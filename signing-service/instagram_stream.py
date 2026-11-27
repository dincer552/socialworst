"""Anonymous public Instagram Reel resolver and byte-range streaming proxy.

This service never downloads media to disk, never reads Instagram cookies, and
only resolves public Reel URLs. Range requests are passed through unchanged so
Android Media3 can seek without buffering the entire video.
"""
from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import json
import os
import re
import threading
import time
from collections import defaultdict, deque
from urllib.parse import urlsplit

import httpx
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import JSONResponse, Response, StreamingResponse
from yt_dlp import YoutubeDL
from yt_dlp.utils import DownloadError

router = APIRouter()
SHORTCODE_RE = re.compile(r"^[A-Za-z0-9_-]{5,32}$")
PATH_RE = re.compile(r"^/(?:reel|reels)/([A-Za-z0-9_-]+)/?$", re.IGNORECASE)
CACHE_TTL_SECONDS = 7 * 60
TOKEN_TTL_SECONDS = 15 * 60
RESOLVE_RATE_LIMIT = 18
RESOLVE_RATE_WINDOW = 60

_CACHE: dict[str, tuple[dict, float]] = {}
_CACHE_LOCK = threading.Lock()
_RATE: dict[str, deque] = defaultdict(deque)
_RATE_LOCK = threading.Lock()
_EXTRACT_SEMAPHORE = threading.BoundedSemaphore(2)

_CDN_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/151.0.0.0 Safari/537.36"
    ),
    "Referer": "https://www.instagram.com/",
    "Accept": "video/mp4,video/*;q=0.9,*/*;q=0.8",
    "Accept-Encoding": "identity",
}
_FORWARD_HEADERS = {
    "content-type", "content-length", "content-range", "accept-ranges",
    "etag", "last-modified", "content-encoding",
}
_HTTP = httpx.AsyncClient(
    follow_redirects=True,
    timeout=httpx.Timeout(connect=12.0, read=None, write=12.0, pool=12.0),
    limits=httpx.Limits(max_connections=16, max_keepalive_connections=8),
)


def parse_instagram_shortcode(raw_url: str) -> str:
    """Accept only public Instagram Reel permalinks; reject arbitrary URLs."""
    if not isinstance(raw_url, str) or len(raw_url) > 2048:
        raise ValueError("Geçerli bir Instagram Reel bağlantısı gerekli.")
    parsed = urlsplit(raw_url.strip())
    if parsed.scheme.lower() != "https" or parsed.hostname not in {
        "instagram.com", "www.instagram.com", "m.instagram.com"
    }:
        raise ValueError("Yalnızca https://www.instagram.com/reel/... bağlantıları destekleniyor.")
    match = PATH_RE.fullmatch(parsed.path or "")
    if not match:
        raise ValueError("Bu bağlantı bir Instagram Reel bağlantısı değil.")
    shortcode = match.group(1)
    if not SHORTCODE_RE.fullmatch(shortcode):
        raise ValueError("Instagram Reel kodu geçersiz.")
    return shortcode


def _token_secret() -> bytes:
    # Derive a dedicated HMAC key from the install's existing Fernet secret.
    configured = os.environ.get("SIGNING_ENCRYPTION_KEY", "")
    if not configured:
        raise RuntimeError("SIGNING_ENCRYPTION_KEY is not configured")
    return hashlib.sha256(("instagram-stream:" + configured).encode("utf-8")).digest()


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def issue_stream_token(shortcode: str, *, now: int | None = None) -> str:
    if not SHORTCODE_RE.fullmatch(shortcode):
        raise ValueError("invalid shortcode")
    issued = int(time.time() if now is None else now)
    payload = json.dumps(
        {"c": shortcode, "e": issued + TOKEN_TTL_SECONDS},
        separators=(",", ":"),
    ).encode("utf-8")
    encoded = _b64url(payload)
    signature = hmac.new(_token_secret(), encoded.encode("ascii"), hashlib.sha256).hexdigest()
    return encoded + "." + signature


def read_stream_token(token: str, *, now: int | None = None) -> str:
    if not isinstance(token, str) or len(token) > 512 or token.count(".") != 1:
        raise ValueError("invalid stream token")
    encoded, supplied_signature = token.split(".", 1)
    expected = hmac.new(_token_secret(), encoded.encode("ascii"), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(supplied_signature, expected):
        raise ValueError("invalid stream token")
    try:
        padded = encoded + "=" * (-len(encoded) % 4)
        payload = json.loads(base64.urlsafe_b64decode(padded.encode("ascii")))
        shortcode = payload["c"]
        expiry = int(payload["e"])
    except Exception as exc:
        raise ValueError("invalid stream token") from exc
    current = int(time.time() if now is None else now)
    if not SHORTCODE_RE.fullmatch(shortcode) or expiry < current:
        raise ValueError("expired stream token")
    return shortcode


def _friendly_extractor_error(error: Exception) -> str:
    raw = str(error).lower()
    if any(term in raw for term in ("login", "logged-in", "log in", "empty media", "private", "rate-limit", "rate limit")):
        return "Instagram bu Reel için anonim video akışına izin vermedi. Gönderi giriş, yaş veya erişim kısıtlamasına tabi olabilir."
    if any(term in raw for term in ("not exist", "not available", "404", "removed")):
        return "Reel bulunamadı; silinmiş, gizli veya bağlantı hatalı olabilir."
    return "Instagram video akışı çözümlenemedi. Instagram geçici olarak anonim erişimi sınırlıyor olabilir."


def _extract_video(shortcode: str, *, force: bool = False) -> dict:
    now = time.monotonic()
    with _CACHE_LOCK:
        cached = _CACHE.get(shortcode)
        if not force and cached and cached[1] > now:
            return cached[0]

    canonical_url = "https://www.instagram.com/reel/" + shortcode + "/"
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "format": "best[ext=mp4]/best",
        "noplaylist": True,
        "socket_timeout": 18,
        "extractor_retries": 1,
        "retries": 1,
        # curl-cffi is installed; match a recent browser TLS fingerprint.
        "impersonate": "chrome",
    }
    try:
        if not _EXTRACT_SEMAPHORE.acquire(timeout=3):
            raise RuntimeError("resolver busy")
        try:
            with YoutubeDL(options) as ydl:
                info = ydl.extract_info(canonical_url, download=False)
        finally:
            _EXTRACT_SEMAPHORE.release()
    except DownloadError as exc:
        raise ValueError(_friendly_extractor_error(exc)) from exc
    except Exception as exc:
        raise ValueError(_friendly_extractor_error(exc)) from exc

    video_url = info.get("url")
    if not isinstance(video_url, str):
        raise ValueError("Instagram bu Reel için oynatılabilir video adresi döndürmedi.")
    parsed = urlsplit(video_url)
    host = (parsed.hostname or "").lower()
    if parsed.scheme != "https" or not (
        host.endswith(".cdninstagram.com") or host == "cdninstagram.com"
        or host.endswith(".fbcdn.net") or host == "fbcdn.net"
    ):
        raise ValueError("Instagram beklenmeyen bir medya adresi döndürdü.")

    item = {
        "url": video_url,
        "duration": float(info.get("duration") or 0),
        "mime_type": "video/mp4" if (info.get("ext") == "mp4") else "video/*",
    }
    with _CACHE_LOCK:
        _CACHE[shortcode] = (item, now + CACHE_TTL_SECONDS)
    return item


def _allow_resolve_request(request: Request) -> bool:
    # Cloudflare Tunnel connects to localhost and supplies CF-Connecting-IP.
    address = request.headers.get("cf-connecting-ip") or (
        request.client.host if request.client else "unknown"
    )
    now = time.monotonic()
    with _RATE_LOCK:
        entries = _RATE[address]
        while entries and entries[0] <= now - RESOLVE_RATE_WINDOW:
            entries.popleft()
        if len(entries) >= RESOLVE_RATE_LIMIT:
            return False
        entries.append(now)
    return True


def _upstream_headers(range_header: str | None) -> dict[str, str]:
    headers = dict(_CDN_HEADERS)
    if range_header:
        # Range is a byte interval, not a URL or host. Forward it verbatim.
        if not re.fullmatch(r"bytes=(?:\d+-\d*|-\d+)", range_header.strip()):
            raise HTTPException(status_code=416, detail="invalid_range_header")
        headers["Range"] = range_header.strip()
    return headers


async def _open_upstream(video_url: str, request: Request) -> httpx.Response:
    headers = _upstream_headers(request.headers.get("range"))
    method = "HEAD" if request.method == "HEAD" else "GET"
    outbound = _HTTP.build_request(method, video_url, headers=headers)
    return await _HTTP.send(outbound, stream=True)


@router.post("/v1/instagram/resolve")
async def resolve_instagram_reel(request: Request):
    if not _allow_resolve_request(request):
        return JSONResponse(
            {"ok": False, "error": "Çok fazla bağlantı denendi. Biraz bekleyip tekrar dene."},
            status_code=429,
        )
    try:
        body = await request.json()
        shortcode = parse_instagram_shortcode(body.get("url", "") if isinstance(body, dict) else "")
        item = await asyncio.to_thread(_extract_video, shortcode)
        token = issue_stream_token(shortcode)
    except ValueError as exc:
        return JSONResponse({"ok": False, "error": str(exc)}, status_code=422)
    except Exception:
        return JSONResponse(
            {"ok": False, "error": "Video akışı şu anda hazırlanamadı. Biraz sonra tekrar dene."},
            status_code=503,
        )

    return JSONResponse({
        "ok": True,
        "stream_url": "/v1/instagram/stream/" + token,
        "duration": item["duration"],
        "mime_type": item["mime_type"],
        "seek_supported": True,
        "expires_in": TOKEN_TTL_SECONDS,
    })


@router.api_route("/v1/instagram/stream/{token}", methods=["GET", "HEAD"])
async def stream_instagram_reel(token: str, request: Request):
    try:
        shortcode = read_stream_token(token)
        item = await asyncio.to_thread(_extract_video, shortcode)
    except ValueError as exc:
        return JSONResponse({"ok": False, "error": str(exc)}, status_code=410)
    except Exception:
        return JSONResponse({"ok": False, "error": "Video akışı yenilenemedi."}, status_code=503)

    upstream = await _open_upstream(item["url"], request)

    # CDN media URLs are signed and expire. Resolve once more if a seek receives 403.
    if upstream.status_code == 403:
        await upstream.aclose()
        with _CACHE_LOCK:
            _CACHE.pop(shortcode, None)
        try:
            item = await asyncio.to_thread(_extract_video, shortcode, force=True)
            upstream = await _open_upstream(item["url"], request)
        except Exception:
            return JSONResponse({"ok": False, "error": "Instagram video bağlantısının süresi doldu; tekrar aç."}, status_code=502)

    headers = {
        key: value for key, value in upstream.headers.items()
        if key.lower() in _FORWARD_HEADERS
    }
    headers["Cache-Control"] = "private, no-store"
    headers["X-Content-Type-Options"] = "nosniff"
    status_code = upstream.status_code

    if request.method == "HEAD" or status_code not in (200, 206):
        await upstream.aclose()
        return Response(
            content=b"",
            status_code=status_code,
            headers=headers,
            media_type=upstream.headers.get("content-type"),
        )

    async def stream_body():
        try:
            async for chunk in upstream.aiter_raw():
                yield chunk
        finally:
            await upstream.aclose()

    return StreamingResponse(
        stream_body(),
        status_code=status_code,
        headers=headers,
        media_type=upstream.headers.get("content-type", "video/mp4"),
    )
