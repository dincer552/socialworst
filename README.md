# My App Store for Android

My App Store is a lightweight private Android app catalog. It does not contain SSH connectivity, terminal, Cloudflare tunnel, or NES emulator functionality. Those features live in separate applications.

## Package identity and updates

- Application ID remains `com.dincer552.hattrickssh` to preserve the existing installation and update path.
- Existing app signing identity remains the original JKS and alias `hattrickssh`.
- The app's own GitHub Release APK is named `my-app-store.apk`.
- Existing installation data is not cleared by the package update.
- Version codes increase with GitHub Actions run numbers so Android accepts in-place updates.

## Catalog

The store reads [app-catalog.json](./app-catalog.json). Current entries:

- **My App Store** — this app, updated from this repository's Releases.
- **DYSSH** — standalone SSH client from [dincer552/dyssh](https://github.com/dincer552/dyssh), APK asset `dyssh.apk`.
- **InstaViewer** — separate application from [dincer552/instaviewer](https://github.com/dincer552/instaviewer), APK asset `instaviewer.apk`.

For each app, the store fetches the latest published GitHub Release, checks the package's installed version, downloads the APK, verifies its SHA-256 digest when GitHub provides one, and launches Android's package installer. Android requires user confirmation and enforces package/signature compatibility.

## Add an app

Add an entry to `app-catalog.json` with a stable `id`, `name`, `description`, `icon`, exact `packageName`, GitHub `repository`, and exact release `assetName`. The app repository must publish a signed APK with monotonically increasing Android `versionCode` and a stable signing certificate.

## Build and signing

Configure repository Actions secrets:
- `ANDROID_KEYSTORE_BASE64`: base64-encoded original JKS.
- `ANDROID_KEYSTORE_PASSWORD`: JKS password for alias `hattrickssh`.

Do not replace the original JKS, change the Store package ID, or reset its version code. This is required for existing Store installations to accept updates.
