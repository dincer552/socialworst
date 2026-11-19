#!/usr/bin/env python3
"""Central Android APK signing service. GitHub Actions authenticate with OIDC."""
from __future__ import annotations

import base64
import json
import os
import re
import secrets
import subprocess
import tempfile
from pathlib import Path

import jwt
from cryptography.fernet import Fernet, InvalidToken
from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import Response

APP = FastAPI(title="My App Store APK Signer", docs_url=None, redoc_url=None)
DATA_DIR = Path(os.environ.get("SIGNING_DATA_DIR", "/var/lib/apk-signing"))
KEY_DIR = DATA_DIR / "keys"
MAX_APK_BYTES = 150 * 1024 * 1024
KEY_ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{1,63}$")
PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")
ALLOWED_REPOSITORIES = {
    item.strip() for item in os.environ.get("ALLOWED_GITHUB_REPOSITORIES", "").split(",") if item.strip()
}
OIDC_AUDIENCE = os.environ.get("APK_SIGNER_OIDC_AUDIENCE", "socialworst-apk-signer")
OIDC_ISSUER = "https://token.actions.githubusercontent.com"
JWKS_URL = OIDC_ISSUER + "/.well-known/jwks"
APKSIGNER = os.environ.get("APKSIGNER_PATH", "/opt/android-sdk/build-tools/36.0.0/apksigner")
AAPT = os.environ.get("AAPT_PATH", "/opt/android-sdk/build-tools/36.0.0/aapt")
_jwks = None


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


def verify_github_oidc(token: str) -> dict:
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
    if not ALLOWED_REPOSITORIES or repository not in ALLOWED_REPOSITORIES:
        raise HTTPException(status_code=403, detail="repository_not_allowed")
    if claims.get("ref") != "refs/heads/main":
        raise HTTPException(status_code=403, detail="only_main_branch_can_sign")
    if claims.get("event_name") not in ("push", "workflow_dispatch"):
        raise HTTPException(status_code=403, detail="workflow_event_not_allowed")
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
    if repository not in ALLOWED_REPOSITORIES:
        raise HTTPException(status_code=403, detail="repository_not_allowed")
    if not key_alias.strip() or len(key_alias) > 128 or not key_password or len(key_password) > 512:
        raise HTTPException(status_code=400, detail="invalid_key_metadata")
    raw = await keystore.read(MAX_APK_BYTES + 1)
    if not raw or len(raw) > MAX_APK_BYTES:
        raise HTTPException(status_code=413, detail="keystore_size_invalid")
    payload = {
        "package_name": package_name,
        "repository": repository,
        "key_alias": key_alias.strip(),
        "key_password": key_password,
        "keystore_b64": base64.b64encode(raw).decode("ascii"),
    }
    save_key(key_id, payload)
    return {"ok": True, "key_id": key_id, "package_name": package_name}


@APP.post("/v1/sign")
async def sign_apk(
    key_id: str = Form(...),
    apk: UploadFile = File(...),
    authorization: str | None = Header(default=None),
):
    token = authorization.removeprefix("Bearer ").strip() if authorization else ""
    claims = verify_github_oidc(token)
    config = read_key(key_id)
    if claims.get("repository") != config.get("repository"):
        raise HTTPException(status_code=403, detail="repository_not_authorized_for_key")
    raw = await apk.read(MAX_APK_BYTES + 1)
    if not raw or len(raw) > MAX_APK_BYTES:
        raise HTTPException(status_code=413, detail="apk_size_invalid")

    with tempfile.TemporaryDirectory(prefix="apk-sign-") as temp:
        folder = Path(temp)
        unsigned = folder / "input-unsigned.apk"
        keystore_path = folder / "signing-key.jks"
        signed = folder / "output-signed.apk"
        unsigned.write_bytes(raw)
        keystore_bytes = base64.b64decode(config["keystore_b64"], validate=True)
        keystore_path.write_bytes(keystore_bytes)
        os.chmod(keystore_path, 0o600)

        package_name, version_code = apk_identity(unsigned)
        if package_name != config["package_name"]:
            raise HTTPException(status_code=403, detail="package_name_does_not_match_signing_key")

        env = os.environ.copy()
        env["APK_SIGNING_PASSWORD"] = config["key_password"]
        run_checked([
            APKSIGNER, "sign", "--ks", str(keystore_path),
            "--ks-key-alias", config["key_alias"],
            "--ks-pass", "env:APK_SIGNING_PASSWORD",
            "--key-pass", "env:APK_SIGNING_PASSWORD",
            "--out", str(signed), str(unsigned),
        ], env=env, timeout=120)
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
