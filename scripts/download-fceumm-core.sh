#!/usr/bin/env bash
set -euo pipefail

# Pinned LemuroidCores artifact: FCEUmm NES core for Android ARM64.
# The Git blob hash is verified to prevent silently packaging a changed binary.
readonly CORE_COMMIT="fee2e824525daa22bcf318f96127fe43fa8a15ad"
readonly CORE_BLOB_SHA="b47b4a6e4a4ddd70fd7f22c7baccee1d06b35902"
readonly CORE_PATH="src/main/jniLibs/arm64-v8a/libfceumm_libretro_android.so"
readonly CORE_URL="https://raw.githubusercontent.com/Swordfish90/LemuroidCores/${CORE_COMMIT}/lemuroid_core_fceumm/src/main/jniLibs/arm64-v8a/libfceumm_libretro_android.so"

mkdir -p "$(dirname "$CORE_PATH")"
curl --fail --location --retry 3 --retry-delay 2 "$CORE_URL" --output "$CORE_PATH"

actual_blob_sha="$(git hash-object "$CORE_PATH")"
if [[ "$actual_blob_sha" != "$CORE_BLOB_SHA" ]]; then
  echo "FCEUmm core integrity check failed: expected $CORE_BLOB_SHA, got $actual_blob_sha" >&2
  rm -f "$CORE_PATH"
  exit 1
fi

chmod 755 "$CORE_PATH"
file "$CORE_PATH"
echo "Verified FCEUmm ARM64 core from LemuroidCores commit $CORE_COMMIT"
