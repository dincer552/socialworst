#!/usr/bin/env python3
"""Central Android APK signing service. GitHub Actions authenticate with OIDC."""
from __future__ import annotations

import base64
import json
import os
import re
import secrets
import shutil
import subprocess
import tempfile
from pathlib import Path
from urllib.parse import urlsplit

import jwt
from cryptography.fernet import Fernet, InvalidToken
from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import Response

APP = FastAPI(title="My App Store APK Signer", docs_url=None, redoc_url=None)
DATA_DIR = Path(os.environ.get("SIGNING_DATA_DIR", "/var/lib/apk-signing"))
KEY_DIR = DATA_DIR / "keys"
REPOSITORY_FILE = DATA_DIR / "repositories.enc"
MAX_APK_BYTES = 150 * 1024 * 1024
KEY_ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{1,63}$")
PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")
ALLOWED_REPOSITORIES = {
    item.strip() for item in os.environ.get("ALLOWED_GITHUB_REPOSITORIES", "").split(",") if item.strip()
}
# Explicit repository-to-key bindings are code-reviewed deployment policy.
# DYSSH uses the original existing key; this grants only this repository access
# to key_id "dyssh" and never creates or replaces a signing certificate.
STATIC_REPOSITORY_KEY_BINDINGS = {
    "dincer552/dyssh": {
        "key_id": "dyssh",
        "package_name": "com.dincer552.dyssh",
    },
}
OIDC_AUDIENCE = os.environ.get("APK_SIGNER_OIDC_AUDIENCE", "socialworst-apk-signer")
OIDC_ISSUER = "https://token.actions.githubusercontent.com"
JWKS_URL = OIDC_ISSUER + "/.well-known/jwks"
APKSIGNER = os.environ.get("APKSIGNER_PATH", "/opt/android-sdk/build-tools/36.0.0/apksigner")
AAPT = os.environ.get("AAPT_PATH", "/opt/android-sdk/build-tools/36.0.0/aapt")
ZIPALIGN = os.environ.get("ZIPALIGN_PATH", "/opt/android-sdk/build-tools/36.0.0/zipalign")
_jwks = None


def normalize_repository(value: str) -> str:
    raw = (value or "").strip()
    if raw.startswith(("https://", "http://")):
        parsed = urlsplit(raw)
        if parsed.scheme != "https" or parsed.netloc.lower() != "github.com" or parsed.query or parsed.fragment:
            raise HTTPException(status_code=400, detail="invalid_github_repository_url")
        parts = parsed.path.strip("/").split("/")
        if len(parts) != 2:
            raise HTTPException(status_code=400, detail="repository_url_must_point_to_repository")
        owner, name = parts
        if name.endswith(".git"):
            name = name[:-4]
    else:
        parts = raw.split("/")
        if len(parts) != 2:
            raise HTTPException(status_code=400, detail="repository_must_be_owner_slash_name")
        owner, name = parts
    if (not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})?", owner)
            or not re.fullmatch(r"[A-Za-z0-9._-]{1,100}", name)
            or name in (".", "..") or name.endswith(".")):
        raise HTTPException(status_code=400, detail="invalid_github_repository")
    return f"{owner}/{name}"


def allowed_repositories() -> set[str]:
    repositories = set(ALLOWED_REPOSITORIES) | set(STATIC_REPOSITORY_KEY_BINDINGS)
    if REPOSITORY_FILE.is_file():
        try:
            stored = json.loads(get_cipher().decrypt(REPOSITORY_FILE.read_bytes()))
            if isinstance(stored, list):
                repositories.update(item for item in stored if isinstance(item, str))
        except (InvalidToken, ValueError, OSError) as exc:
            raise HTTPException(status_code=500, detail="repository_registry_unreadable") from exc
    return repositories


def save_allowed_repositories(repositories: set[str]) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    encrypted = get_cipher().encrypt(json.dumps(sorted(repositories), separators=(",", ":")).encode())
    temporary = REPOSITORY_FILE.with_suffix(".tmp")
    temporary.write_bytes(encrypted)
    os.chmod(temporary, 0o600)
    os.replace(temporary, REPOSITORY_FILE)
    os.chmod(REPOSITORY_FILE, 0o600)


def key_repositories(config: dict) -> set[str]:
    values = config.get("repositories")
    if isinstance(values, list):
        return {item for item in values if isinstance(item, str)}
    legacy = config.get("repository")
    return {legacy} if isinstance(legacy, str) and legacy else set()


def get_cipher() -> Fernet:
    value = os.environ.get("SIGNING_ENCRYPTION_KEY", "")
    if not value:
        raise RuntimeError("SIGNING_ENCRYPTION_KEY is not configured")
    try:
        return Fernet(value.encode("ascii"))
    except Exception as exc:
        raise RuntimeError("SIGNING_ENCRYPTION_KEY must be a Fernet key") from exc


def require_admin(authorization: str | None) -> None:
    expected = os.environ.get("SIGNING_ADMIN_TOKEN", "")
    supplied = authorization.removeprefix("Bearer ").strip() if authorization else ""
    if not expected or not secrets.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail="unauthorized")


def safe_key_id(key_id: str) -> str:
    if not KEY_ID_RE.fullmatch(key_id):
        raise HTTPException(status_code=400, detail="invalid_key_id")
    return key_id


def save_key(key_id: str, payload: dict) -> None:
    KEY_DIR.mkdir(parents=True, exist_ok=True)
    encrypted = get_cipher().encrypt(json.dumps(payload, separators=(",", ":")).encode())
    destination = KEY_DIR / f"{safe_key_id(key_id)}.enc"
    temporary = destination.with_suffix(".tmp")
    temporary.write_bytes(encrypted)
    os.chmod(temporary, 0o600)
    os.replace(temporary, destination)
    os.chmod(destination, 0o600)


def read_key(key_id: str) -> dict:
    path = KEY_DIR / f"{safe_key_id(key_id)}.enc"
    if not path.is_file():
        raise HTTPException(status_code=404, detail="signing_key_not_configured")
    try:
        return json.loads(get_cipher().decrypt(path.read_bytes()))
    except (InvalidToken, ValueError, OSError) as exc:
        raise HTTPException(status_code=500, detail="signing_key_store_unreadable") from exc


def decode_github_oidc(token: str) -> dict:
    """Validate a GitHub Actions identity without applying the repository allowlist yet."""
    global _jwks
    if not token:
        raise HTTPException(status_code=401, detail="missing_github_oidc_token")
    try:
        if _jwks is None:
            _jwks = jwt.PyJWKClient(JWKS_URL, cache_jwk_set=True, lifespan=300)
        signing_key = _jwks.get_signing_key_from_jwt(token)
        claims = jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            audience=OIDC_AUDIENCE,
            issuer=OIDC_ISSUER,
            options={"require": ["exp", "iat", "iss", "aud", "sub"]},
        )
    except Exception as exc:
        raise HTTPException(status_code=401, detail="invalid_github_oidc_token") from exc

    repository = claims.get("repository", "")
    if not isinstance(repository, str) or not repository:
        raise HTTPException(status_code=401, detail="github_repository_claim_missing")
    if claims.get("ref") != "refs/heads/main":
        raise HTTPException(status_code=403, detail="only_main_branch_can_sign")
    if claims.get("event_name") not in ("push", "workflow_dispatch"):
        raise HTTPException(status_code=403, detail="workflow_event_not_allowed")
    return claims


def verify_github_oidc(token: str) -> dict:
    claims = decode_github_oidc(token)
    if claims.get("repository") not in allowed_repositories():
        raise HTTPException(status_code=403, detail="repository_not_allowed")
    return claims

def run_checked(command: list[str], *, env: dict[str, str] | None = None, timeout: int = 90) -> str:
    try:
        result = subprocess.run(
            command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, timeout=timeout, check=False,
            env=env or os.environ.copy(),
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise HTTPException(status_code=503, detail=f"android_signing_tool_unavailable: {exc}") from exc
    if result.returncode != 0:
        raise HTTPException(status_code=422, detail="apk_signing_or_validation_failed")
    return result.stdout


def apk_identity(apk_path: Path) -> tuple[str, str]:
    output = run_checked([AAPT, "dump", "badging", str(apk_path)], timeout=30)
    match = re.search(r"^package: name='([^']+)' versionCode='([^']+)'", output, re.MULTILINE)
    if not match:
        raise HTTPException(status_code=422, detail="apk_package_metadata_missing")
    return match.group(1), match.group(2)


@APP.get("/health")
def health():
    return {"ok": True, "service": "my-app-store-apk-signer", "oidc": True}


@APP.post("/v1/bootstrap")
def bootstrap_signing_profile(
    key_id: str = Form(...),
    package_name: str = Form(...),
    authorization: str | None = Header(default=None),
):
    """Allow a repo's own verified main-branch workflow to create its first, isolated key."""
    token = authorization.removeprefix("Bearer ").strip() if authorization else ""
    claims = decode_github_oidc(token)
    safe_key_id(key_id)
    if not PACKAGE_RE.fullmatch(package_name):
        raise HTTPException(status_code=400, detail="invalid_package_name")

    repository = normalize_repository(claims["repository"])
    try:
        config = read_key(key_id)
    except HTTPException as exc:
        if exc.status_code != 404:
            raise
        config = generate_signing_profile(key_id, repository, package_name)
        save_key(key_id, config)
        save_allowed_repositories(allowed_repositories() | {repository})
        return {
            "ok": True,
            "created": True,
            "key_id": key_id,
            "repository": repository,
            "package_name": package_name,
        }

    # Self-service bootstrap may only confirm the repository's own existing profile.
    # It cannot attach another repository to an existing app or reuse another app's key.
    if config.get("package_name") != package_name:
        raise HTTPException(status_code=409, detail="signing_profile_package_mismatch")
    if repository not in key_repositories(config):
        raise HTTPException(status_code=403, detail="repository_requires_admin_binding")
    save_allowed_repositories(allowed_repositories() | {repository})
    return {
        "ok": True,
        "created": False,
        "key_id": key_id,
        "repository": repository,
        "package_name": package_name,
    }


@APP.put("/v1/admin/keys/{key_id}")
async def provision_key(
    key_id: str,
    keystore: UploadFile = File(...),
    package_name: str = Form(...),
    repository: str = Form(...),
    key_alias: str = Form(...),
    key_password: str = Form(...),
    authorization: str | None = Header(default=None),
):
    require_admin(authorization)
    safe_key_id(key_id)
    if not PACKAGE_RE.fullmatch(package_name):
        raise HTTPException(status_code=400, detail="invalid_package_name")
    repository = normalize_repository(repository)
    if not key_alias.strip() or len(key_alias) > 128 or not key_password or len(key_password) > 512:
        raise HTTPException(status_code=400, detail="invalid_key_metadata")
    raw = await keystore.read(MAX_APK_BYTES + 1)
    if not raw or len(raw) > MAX_APK_BYTES:
        raise HTTPException(status_code=413, detail="keystore_size_invalid")
    encoded_keystore = base64.b64encode(raw).decode("ascii")
    try:
        existing = read_key(key_id)
    except HTTPException as exc:
        if exc.status_code != 404:
            raise
        existing = None

    if existing:
        # Never silently replace the certificate behind an existing Android package.
        if (existing.get("package_name") != package_name or existing.get("keystore_b64") != encoded_keystore
                or existing.get("key_password") != key_password):
            raise HTTPException(status_code=409, detail="existing_signing_key_is_protected")
        if existing.get("key_alias") != key_alias.strip():
            raise HTTPException(status_code=409, detail="existing_signing_alias_is_protected")
        repositories = key_repositories(existing) | {repository}
    else:
        repositories = {repository}

    payload = {
        "package_name": package_name,
        "repository": sorted(repositories)[0],
        "repositories": sorted(repositories),
        "key_alias": key_alias.strip(),
        "key_password": key_password,
        "keystore_b64": encoded_keystore,
    }
    save_key(key_id, payload)
    save_allowed_repositories(allowed_repositories() | {repository})
    return {"ok": True, "key_id": key_id, "package_name": package_name, "repository": repository}



def generate_signing_profile(key_id: str, repository: str, package_name: str | None = None) -> dict:
    """Create a dedicated Android signing identity for a new repository."""
    owner, repo_name = repository.split("/", 1)
    owner_part = re.sub(r"[^A-Za-z0-9_]", "_", owner)
    repo_part = re.sub(r"[^A-Za-z0-9_]", "", repo_name)
    if not repo_part or not repo_part[0].isalpha():
        repo_part = "app" + repo_part
    package_name = package_name or f"com.{owner_part.lower()}.{repo_part.lower()}"
    if not PACKAGE_RE.fullmatch(package_name):
        raise HTTPException(status_code=400, detail="could_not_derive_android_package_name")

    keytool = shutil.which("keytool")
    if not keytool:
        raise HTTPException(status_code=503, detail="keytool_unavailable_on_signing_server")

    password = secrets.token_urlsafe(36)
    alias = "app-signing"
    with tempfile.TemporaryDirectory(prefix="apk-keygen-") as temp:
        keystore_path = Path(temp) / f"{key_id}.jks"
        run = subprocess.run(
            [
                keytool, "-genkeypair", "-noprompt",
                "-keystore", str(keystore_path),
                "-storetype", "JKS",
                "-storepass", password,
                "-keypass", password,
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "3072",
                "-validity", "10000",
                "-dname", f"CN={repository}, OU=My App Store, O={owner}, C=TR",
            ],
            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, timeout=60, check=False,
        )
        if run.returncode != 0 or not keystore_path.is_file():
            raise HTTPException(status_code=503, detail="automatic_signing_key_generation_failed")
        raw = keystore_path.read_bytes()

    return {
        "package_name": package_name,
        "repository": repository,
        "repositories": [repository],
        "key_alias": alias,
        "key_password": password,
        "keystore_b64": base64.b64encode(raw).decode("ascii"),
    }


@APP.post("/v1/admin/repositories")
def register_repository(
    repository: str = Form(...),
    key_id: str = Form(...),
    authorization: str | None = Header(default=None),
):
    """Attach a repository to an existing key, or create a dedicated key on first use."""
    require_admin(authorization)
    safe_key_id(key_id)
    repository = normalize_repository(repository)
    try:
        config = read_key(key_id)
    except HTTPException as exc:
        if exc.status_code != 404:
            raise
        config = generate_signing_profile(key_id, repository)
        save_key(key_id, config)

    repositories = key_repositories(config)
    repositories.add(repository)
    config["repositories"] = sorted(repositories)
    config["repository"] = sorted(repositories)[0]
    save_key(key_id, config)
    save_allowed_repositories(allowed_repositories() | {repository})
    return {
        "ok": True,
        "key_id": key_id,
        "package_name": config["package_name"],
        "repository": repository,
        "repositories": sorted(repositories),
        "key_created": config.get("key_alias") == "app-signing",
    }


@APP.post("/v1/sign")
async def sign_apk(
    key_id: str = Form(...),
    apk: UploadFile = File(...),
    authorization: str | None = Header(default=None),
):
    token = authorization.removeprefix("Bearer ").strip() if authorization else ""
    claims = verify_github_oidc(token)
    config = read_key(key_id)
    repository = claims.get("repository")
    static_binding = STATIC_REPOSITORY_KEY_BINDINGS.get(repository)
    statically_authorized = (
        static_binding is not None
        and static_binding.get("key_id") == key_id
        and static_binding.get("package_name") == config.get("package_name")
    )
    if repository not in key_repositories(config) and not statically_authorized:
        raise HTTPException(status_code=403, detail="repository_not_authorized_for_key")
    raw = await apk.read(MAX_APK_BYTES + 1)
    if not raw or len(raw) > MAX_APK_BYTES:
        raise HTTPException(status_code=413, detail="apk_size_invalid")

    with tempfile.TemporaryDirectory(prefix="apk-sign-") as temp:
        folder = Path(temp)
        unsigned = folder / "input-unsigned.apk"
        keystore_path = folder / "signing-key.jks"
        aligned = folder / "input-aligned.apk"
        signed = folder / "output-signed.apk"
        unsigned.write_bytes(raw)
        keystore_bytes = base64.b64decode(config["keystore_b64"], validate=True)
        keystore_path.write_bytes(keystore_bytes)
        os.chmod(keystore_path, 0o600)

        package_name, version_code = apk_identity(unsigned)
        if package_name != config["package_name"]:
            raise HTTPException(status_code=403, detail="package_name_does_not_match_signing_key")

        run_checked([ZIPALIGN, "-f", "-p", "4", str(unsigned), str(aligned)], timeout=60)
        env = os.environ.copy()
        env["APK_SIGNING_PASSWORD"] = config["key_password"]
        run_checked([
            APKSIGNER, "sign", "--ks", str(keystore_path),
            "--ks-key-alias", config["key_alias"],
            "--ks-pass", "env:APK_SIGNING_PASSWORD",
            "--key-pass", "env:APK_SIGNING_PASSWORD",
            "--out", str(signed), str(aligned),
        ], env=env, timeout=120)
        run_checked([ZIPALIGN, "-c", "-p", "4", str(signed)], timeout=30)
        run_checked([APKSIGNER, "verify", "--verbose", str(signed)], timeout=60)
        result = signed.read_bytes()

    return Response(
        content=result,
        media_type="application/vnd.android.package-archive",
        headers={
            "Content-Disposition": f'attachment; filename="{key_id}.apk"',
            "X-APK-Package": config["package_name"],
            "X-APK-Version-Code": version_code,
            "X-Signing-Repository": claims["repository"],
            "Cache-Control": "no-store",
        },
    )
