# Hattrick SSH

A minimal Android SSH client for connecting to a personal Azure Linux VM through Cloudflare Access and Cloudflare Tunnel.

## Current server profile

- Host: `ssh.dinceryuksek.com`
- Port: `22`
- User: `azureuser`
- VM label: `hattrick-vm-uks`
- Authentication: user-imported RSA private key
- Transport: Cloudflare Access `cloudflared access ssh --hostname` as an SSH stdio ProxyCommand
- Android package: `com.dincer552.hattrickssh`
- Minimum Android: API 26
- Native ABI: `arm64-v8a`

## Architecture

1. The app imports a PEM/OpenSSH private key from Android's document picker.
2. The key is encrypted with AES-256-GCM using a non-exportable Android Keystore key. StrongBox is used when available and supported; otherwise Android Keystore is used.
3. The app launches its Android ARM64 `cloudflared` binary and uses its stdin/stdout as the SSH proxy byte stream. stderr is kept separate for diagnostics and the Cloudflare login URL.
4. The SSH library (mwiede JSch) connects using public-key authentication and verifies/pins the server's SSH host key in encrypted app-private storage.
5. An Android ARM64 PTY bridge connects a local loopback socket to the Termux VT terminal view. The SSH channel is a real interactive shell with an `xterm-256color` PTY.

The app does not use an HTTP/WebView terminal and does not send shell commands through a fake command field. The initial connection timeout is three minutes to allow interactive Cloudflare Access login.

## First run

1. Install the debug APK from the GitHub Actions artifact.
2. Open **SSH PRIVATE KEY SEÇ** and select the RSA private-key file downloaded from Azure.
3. Tap **BAĞLAN**.
4. If Cloudflare Access asks for browser authentication, complete the sign-in and return to the app.
5. On the first SSH connection, inspect the server host-key fingerprint and accept only after verifying it against a trusted source.
6. Use the terminal and the shortcut row for Esc, Tab, Ctrl+C, Ctrl+D, arrows and Enter.

The private key must never be committed to this repository, a GitHub Actions secret, or an app log. The initial importer targets unencrypted Azure PEM/OpenSSH private keys; encrypted keys that require a passphrase are not yet supported.

## Build

GitHub Actions builds the native components and the Android debug APK on pushes to `main` and on manual workflow dispatch.

The workflow:
- Installs JDK 17, Android SDK/NDK and Go.
- Builds Cloudflare's `cloudflared` source for `GOOS=android GOARCH=arm64 CGO_ENABLED=1`.
- Compiles `src/main/cpp/sshbridge.c` for Android ARM64.
- Runs `gradle assembleDebug`.
- Publishes `build/outputs/apk/debug/*.apk` as the `hattrick-ssh-debug-apk` artifact.

To build locally, install JDK 17, Android SDK (API 35), NDK 26.1.10909125, Go 1.27.3+, Git and Patch. Then run:

```bash
scripts/build-cloudflared-android.sh
mkdir -p src/main/assets/bin
NDK="$ANDROID_HOME/ndk/26.1.10909125"
CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
"$CC" -O2 -Wall -Wextra -fPIE -pie -Wl,-z,max-page-size=16384 \
  src/main/cpp/sshbridge.c -o src/main/assets/bin/sshbridge
gradle assembleDebug
```

## Cloudflare notes

Cloudflare's documented SSH pattern is to use `cloudflared access ssh --hostname <host>` as the SSH client's stdio proxy. It is not a local TCP forwarder and does not use `--url ssh://localhost:<port>`.

The Android patch delegates opening the interactive login URL to the host Android app. Access credentials and the cloudflared config are stored inside the app-private files directory, which is excluded from Android backup.

The app does not modify the Cloudflare Tunnel, DNS, Access application, firewall or VM configuration.

## Important test status

A successful APK build only proves packaging. End-to-end use still requires testing on a real Android ARM64 device with the user's Cloudflare Access policy and Azure public-key account. The GitHub Actions artifact must be checked for a green build before installing.

## Third-party components

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for component sources and licenses.
