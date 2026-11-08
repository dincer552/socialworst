# Hattrick SSH — ordered validation plan

This checklist is the release gate. A source-code implementation is not marked complete until its stated test has passed.

## Repository and build

- [x] Inspect the existing private repository and retain the pre-existing scaffold.
- [x] Confirm app ID `com.dincer552.hattrickssh`, Kotlin/Gradle project, minSdk 26 and ARM64 ABI.
- [x] Build Cloudflare cloudflared for `GOOS=android GOARCH=arm64 CGO_ENABLED=1` in GitHub Actions.
- [x] Compile the native PTY bridge for Android ARM64 in GitHub Actions.
- [x] Fix the Kotlin nullable host-key byte-array compilation error.
- [x] Package native executables as APK `jniLibs/arm64-v8a` payloads and use `nativeLibraryDir` instead of writable `filesDir`.
- [ ] Confirm the current commit's APK build and verify both executable entries are present in the APK.

## Android device prototype — required before real SSH testing

- [ ] Install the debug APK on an ARM64 Android phone (API 26+).
- [ ] Confirm `ProcessBuilder` starts cloudflared from `nativeLibraryDir`.
- [ ] Confirm cloudflared stdin/stdout carry a byte stream and stderr is parsed independently.
- [ ] Confirm the PTY bridge starts through Termux `TerminalSession` from `nativeLibraryDir`.
- [ ] Confirm the localhost socket bridge connects both directions without truncation or deadlock.

## Access and SSH

- [ ] Complete Cloudflare Access browser authentication and return to the waiting SSH connection.
- [ ] Confirm the Access login state is retained in app-private storage without logging tokens or cookies.
- [ ] Import an unencrypted RSA PEM/OpenSSH private key through Android's document picker.
- [ ] Confirm the key is AES-GCM encrypted with Android Keystore and remains after app restart.
- [ ] Verify the first SSH host fingerprint against a trusted source; reject changed fingerprints.
- [ ] Establish a real SSH public-key session to `ssh.dinceryuk.com:22` as `azureuser`.
- [ ] Run `hostname` and `whoami` on the VM.

## Interactive terminal and lifecycle

- [ ] Verify PTY shell, ANSI colors, scrolling, keyboard resizing and long input.
- [ ] Verify Enter, Backspace, Tab, Esc, arrows, Ctrl+C and Ctrl+D.
- [ ] Verify PTY resize events reach the SSH channel.
- [ ] Verify disconnect and reconnect.
- [ ] Verify key persistence after force-closing and reopening the app.
- [ ] Run `df -h`, `free -h` and read-only `sudo systemctl status cloudflared`.
- [ ] Confirm logs contain no private key, Access URL, token or cookie.

## Release

- [ ] Add and verify the Gradle wrapper for Android Studio/local builds.
- [ ] Verify the final GitHub Actions artifact and installation on the target phone.
- [ ] Mark the app ready only after all device and Azure acceptance tests above pass.

## Current boundary

CI cross-compilation is not a substitute for Android runtime validation. No Cloudflare Tunnel, DNS, Access policy, firewall or Azure VM configuration changes are part of these tests.
