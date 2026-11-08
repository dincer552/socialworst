#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${CLOUDFLARED_VERSION:-2026.10.7}"
SOURCE="${ROOT}/build/cloudflared-${VERSION}"
NDK_VERSION="${ANDROID_NDK_VERSION:-26.1.10909125}"
ANDROID_API="${ANDROID_API_LEVEL:-26}"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

if [[ -z "${ANDROID_HOME}" || ! -d "${ANDROID_HOME}/ndk/${NDK_VERSION}" ]]; then
  echo "Android SDK/NDK missing. Set ANDROID_HOME and install NDK ${NDK_VERSION}." >&2
  exit 1
fi
if ! command -v go >/dev/null 2>&1; then
  echo "Go is required to build cloudflared." >&2
  exit 1
fi

GO_VERSION="$(go env GOVERSION | sed 's/^go//')"
if [[ "$(printf '%s\n%s\n' "1.27.3" "${GO_VERSION}" | sort -V | head -n1)" != "1.27.3" ]]; then
  echo "Go 1.27.3 or newer required; found ${GO_VERSION}." >&2
  exit 1
fi

TOOLCHAIN="${ANDROID_HOME}/ndk/${NDK_VERSION}/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="${TOOLCHAIN}/aarch64-linux-android${ANDROID_API}-clang"
CXX="${TOOLCHAIN}/aarch64-linux-android${ANDROID_API}-clang++"
if [[ ! -x "${CC}" ]]; then
  echo "Android NDK compiler not found: ${CC}" >&2
  exit 1
fi

mkdir -p "${ROOT}/build"
if [[ ! -d "${SOURCE}/.git" ]]; then
  git clone --depth 1 --branch "${VERSION}" https://github.com/cloudflare/cloudflared.git "${SOURCE}"
fi

apply_patch_once() {
  local patch_file="$1"
  if patch --dry-run --forward -p1 < "$patch_file" >/dev/null 2>&1; then
    patch --forward -p1 < "$patch_file"
  elif patch --dry-run --reverse -p1 < "$patch_file" >/dev/null 2>&1; then
    echo "Already applied: $(basename "$patch_file")"
  else
    echo "Patch does not apply cleanly: $patch_file" >&2
    exit 1
  fi
}

(
  cd "${SOURCE}"
  apply_patch_once "${ROOT}/patches/cloudflared-android.patch"
  apply_patch_once "${ROOT}/patches/cloudflared-security-dependencies.patch"
  go mod download golang.org/x/net@v0.62.0
  go mod vendor

  export GOOS=android
  export GOARCH=arm64
  export CGO_ENABLED=1
  export CC
  export CXX
  export CGO_CFLAGS="-O2 -fPIE"
  export CGO_LDFLAGS="-pie -Wl,-z,max-page-size=16384"

  OUTPUT="${ROOT}/src/main/assets/bin/cloudflared"
  mkdir -p "$(dirname "${OUTPUT}")"
  go build -mod=vendor -trimpath \
    -ldflags="-s -w -buildid= -extldflags=-Wl,-z,max-page-size=16384" \
    -o "${OUTPUT}.tmp" ./cmd/cloudflared
  mv "${OUTPUT}.tmp" "${OUTPUT}"
  chmod 700 "${OUTPUT}"
)

file "${ROOT}/src/main/assets/bin/cloudflared"
ls -lh "${ROOT}/src/main/assets/bin/cloudflared"
echo "Built cloudflared ${VERSION} for Android arm64."
