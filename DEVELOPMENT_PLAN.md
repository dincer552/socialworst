# Android SSH Client release checklist

## Repository and product baseline

- [x] Preserve application ID `com.dincer552.hattrickssh` and the existing Android Keystore alias.
- [x] Replace hard-coded personal server UI/defaults with user-defined connection profiles.
- [x] Support multiple profiles with private-key or SSH-password authentication.
- [x] Encrypt profile records/passwords and per-profile private keys in app-private storage using Android Keystore AES-GCM.
- [x] Make Cloudflare Access an optional profile transport; leave normal SSH direct.
- [x] Separate GitHub APK and Google Play AAB distribution flavors.
- [x] Remove APK install permission from the Play manifest; retain signed APK updates for the GitHub flavor.
- [x] Set Play target API to 36 and update the Android Gradle Plugin/Gradle combination.
- [ ] Build debug/release APK and Play AAB; validate the CI workflow and signed artifacts.
- [ ] Compare the configured JKS signing certificate with the currently distributed APK certificate.

## Device and protocol acceptance

- [ ] Exercise profile create/edit/delete and confirm persistence after restart.
- [ ] Import a distinct private key for two profiles and confirm profile isolation.
- [ ] Test key and password SSH authentication against a controlled SSH server.
- [ ] Confirm first host-key acceptance and rejection of changed host keys.
- [ ] Test real PTY shell, ANSI colors, output, resize, scroll, long input, keyboard, and disconnect/reconnect.
- [ ] Test normal direct SSH independently of Cloudflare.
- [ ] On a real ARM64 Android phone, test cloudflared execution, browser Access login, token persistence, and SSH over the Tunnel.
- [ ] Confirm neither credentials nor key material appear in logs.
- [ ] Test API 26+ compatibility and 16 KB page-size native payload alignment.

## Google Play submission

- [ ] Upload the signed Play AAB to an internal test track and validate Play in-app updates.
- [ ] Confirm Play App Signing uses a certificate compatible with the already distributed APKs if migration of those installs is required.
- [ ] Complete store listing, support contact, privacy policy URL, Data safety and app-content declarations.
- [ ] Review third-party license notices and store screenshots/graphics.

## Known boundary

No Android-device SSH, PTY, or Cloudflare Access runtime test has been performed in this checkout. A successful build/package alone does not establish those features as operational. The app does not change any user's SSH server, Cloudflare Tunnel, DNS, firewall, or cloud account configuration.
