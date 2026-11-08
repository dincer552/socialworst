# SSH Client for Android

A native Android SSH client with saved server profiles, an interactive terminal, and optional Cloudflare Access transport. Commands run in a real SSH shell; the app does not simulate a terminal.

## Features

- Application ID: `com.dincer552.hattrickssh` (preserved for existing installations).
- Android API 26+; target API 36; ARM64 native payloads.
- Multiple server profiles with direct SSH or optional Cloudflare Access.
- Per-profile SSH private key or password authentication.
- Profile data, passwords, private keys, and known-host keys are encrypted in app-private storage using AES-GCM and Android Keystore.
- GitHub Release updates: the app checks for releases on launch and from the in-app control. It downloads `ssh-client.apk`; Android asks the user to approve installation and checks that the APK signature matches the installed app.
- App data backup is disabled.

Cloudflare and PTY native execution still require testing on a real ARM64 Android device. A successful cross-build alone does not prove those runtime paths work.

## GitHub Releases and signing

On a push to `main`, GitHub Actions builds a debug APK and, when signing secrets are configured, a signed release APK. The release includes `ssh-client.apk` for the in-app updater and `hattrick-ssh.apk` for compatibility with older app versions. Before publishing, CI compares the new APK certificate with the currently published APK certificate.

Configure these GitHub repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded existing JKS file.
- `ANDROID_KEYSTORE_PASSWORD`: JKS password (and the configured signing alias password).

Never replace or commit the existing JKS, change the application ID, or send signing material in chat. Android requires user approval to install APK updates from outside Google Play.

## Local build

The active Android module is the repository-root Gradle project and `src/main`.

```sh
scripts/build-cloudflared-android.sh
mkdir -p src/main/jniLibs/arm64-v8a
NDK="$ANDROID_HOME/ndk/26.1.10909125"
CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
"$CC" -O2 -Wall -Wextra -fPIE -pie -Wl,-z,max-page-size=16384 \
  src/main/cpp/sshbridge.c -o src/main/jniLibs/arm64-v8a/libsshbridge.so
gradle assembleDebug
```

See [PRIVACY_POLICY.md](./PRIVACY_POLICY.md) and [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md). No real server, Cloudflare, or Android-device test is claimed until it has actually been run.
