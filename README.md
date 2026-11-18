# My App Store for Android

This Android app turns the existing SSH Client installation into a private app store for the developer's own Android apps.

## Compatibility and preserved installation

- **Application ID remains:** `com.dincer552.hattrickssh`.
- **Existing signing identity remains:** the original JKS file and `hattrickssh` alias must be used for every update.
- **Existing app data is not cleared.** Previous SSH profiles and encrypted key data remain in app-private storage, but the launcher now opens the store.
- Android still validates the signing certificate and asks the user to approve APK installation/update.

## Store features

- Reads the app list from [app-catalog.json](./app-catalog.json).
- Fetches each app's latest GitHub Release and selected APK asset.
- Shows installed/current/update-available status using package name and version code.
- Downloads APKs with Android DownloadManager.
- Verifies the GitHub Release SHA-256 digest when available.
- Hands the APK to Android Package Installer; signature and package compatibility are enforced by Android.

## Adding an app

Add an entry to `app-catalog.json` with:
- `id`: stable unique identifier.
- `name` and `description`.
- `packageName`: exact Android application ID.
- `repository`: GitHub owner/repository.
- `assetName`: exact APK asset name attached to the latest GitHub Release.

The target repository must publish a signed APK with a monotonically increasing Android `versionCode`. Use the same signing certificate for all releases of that app.

## Build and signing

The app keeps the original package ID and signing key so it can update the installed SSH Client in place. GitHub Actions builds ARM64 payloads, verifies tests, and publishes `ssh-client.apk` when these repository Actions secrets are configured:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded original JKS file.
- `ANDROID_KEYSTORE_PASSWORD`: JKS password.

Do not replace the existing JKS, change the package ID, or expose signing material. The GitHub Release asset remains named `ssh-client.apk` for compatibility with the existing updater and installed versions.

Android requires user approval for sideloaded APK installations. Silent installation is not supported for an ordinary app without device-owner/system privileges.
