# SSH Client for Android

A native Android SSH client with saved server profiles, an interactive terminal and optional Cloudflare Access transport. The app uses SSH/JSch and the Termux terminal emulator; it does not simulate shell commands.

## Current implementation

- Application ID: `com.dincer552.hattrickssh` (kept stable for existing installations).
- Minimum Android: API 26; Play release target: API 36.
- ARM64 native payloads: Cloudflare `cloudflared` and the PTY socket bridge.
- Authentication: per-profile SSH private key or password. Private keys and profile data are encrypted with AES-GCM using a non-exportable Android Keystore key.
- Cloudflare Access: optional per profile. Direct SSH is supported without `cloudflared`.
- Backups: app backup is disabled; sensitive app files are excluded from extraction and device transfer.
- Updates: GitHub-installed APK builds use signed GitHub Release APK updates. The Play flavor uses Google Play in-app updates and does not request `REQUEST_INSTALL_PACKAGES`.
- Existing installations migrate the previous single-server profile and encrypted key on first launch after upgrade; fresh installs start with an empty server list.

Cloudflare/PTY execution has only been cross-compiled and packaged in CI so far. It must still be verified on a real ARM64 Android device before describing that path as operational.

## Build layout

The active Android application is the repository-root Gradle project and `src/main`. The historical `app/` directory is not included in `settings.gradle.kts` and is not part of the build.

Build the GitHub debug APK:

```sh
scripts/build-cloudflared-android.sh
mkdir -p src/main/jniLibs/arm64-v8a
NDK="$ANDROID_HOME/ndk/26.1.10909125"
CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
"$CC" -O2 -Wall -Wextra -fPIE -pie -Wl,-z,max-page-size=16384 \
  src/main/cpp/sshbridge.c -o src/main/jniLibs/arm64-v8a/libsshbridge.so
gradle assembleGithubDebug
```

The GitHub Actions workflow builds the native ARM64 payloads, an unsigned debug APK, and—when the signing secrets are configured—a signed GitHub APK plus a signed Play AAB.

## Signing and distribution

GitHub repository secrets:

- `ANDROID_KEYSTORE_BASE64`: base64 encoding of the existing persistent JKS.
- `ANDROID_KEYSTORE_PASSWORD`: password for the JKS and `hattrickssh` alias.

Do not generate a replacement keystore, commit key material, or change the application ID. The GitHub APK update path requires the same signing certificate used for previously distributed APKs. Before publishing to Play, verify the JKS certificate against the currently distributed APK and configure Play App Signing so existing installations remain updatable. Play upload is not automated yet: the workflow produces the AAB for review/upload in Play Console.

## Privacy

See [PRIVACY_POLICY.md](./PRIVACY_POLICY.md). No advertising or analytics SDK is currently configured. Network traffic is used for the user's SSH connection, optional Cloudflare Access, and—only in the GitHub APK flavor—GitHub Release update checks/downloads.

## Store submission work still required

- Verify the signing certificate and upload the Play AAB to an internal test track.
- Test profile CRUD, password/key authentication, direct SSH, terminal lifecycle, and optional Cloudflare Access on supported Android devices.
- Complete Play Console identity, app content, Data safety and target-audience declarations.
- Provide a public privacy policy URL, store listing text, screenshots, feature graphic, and support contact.
- Review open-source notices in [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).
