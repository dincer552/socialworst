# SSH Client development and release checklist

## General SSH client

- [x] Preserve application ID `com.dincer552.hattrickssh` and the existing Android Keystore alias.
- [x] Use multiple server profiles; remove personal host/user defaults from the active UI.
- [x] Support key or password authentication per profile.
- [x] Encrypt profile records, passwords, private keys, and known-host data using Android Keystore AES-GCM.
- [x] Keep Cloudflare Access optional; allow direct SSH.
- [x] Add GitHub Release update check, in-app check button, signed APK download, and Android installer handoff.
- [ ] Configure GitHub signing secrets and verify existing APK certificate continuity.
- [ ] Run Actions unit tests and debug/release APK builds; inspect ARM64 payloads and release assets.

## Device acceptance

- [ ] Test profile create/edit/delete and persistence after restart.
- [ ] Test per-profile key isolation and SSH password authentication.
- [ ] Connect to a controlled SSH server; verify host-key prompt and changed-key rejection.
- [ ] Test PTY shell, ANSI, input, resize, scrolling, disconnect and reconnect on an ARM64 Android device.
- [ ] Test Cloudflare browser login and SSH tunnel separately from direct SSH.
- [ ] Install an older signed APK and test its in-app GitHub update path.
- [ ] Confirm credentials and key material do not appear in logs.
- [ ] Verify API 26+ and native 16 KB page-size compatibility.

## Release setup

- [ ] Set `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD` in GitHub Actions secrets; do not commit or share signing material.
- [ ] Publish the privacy policy at a public HTTPS URL and add support contact details.
- [ ] Review third-party license notices and release notes.

## Known limitations

No real SSH server, Cloudflare Access flow, Android-device terminal, or update-install test is claimed until it has actually been performed. A successful compile/package alone does not verify these runtime features.
