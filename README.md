# My App Store for Android

My App Store is a lightweight private Android app catalog. It does not contain SSH connectivity, terminal, Cloudflare tunnel, or NES emulator functionality. Those features live in separate applications.

## Package identity and updates

- Application ID remains `com.dincer552.hattrickssh` to preserve the existing installation and update path.
- Existing production signing identity is the original JKS and alias `hattrickssh`; do not replace it.
- The Store Release contains `my-app-store.apk` and the temporary compatibility alias `ssh-client.apk`.
- Version codes increase with GitHub Actions run numbers.
- Android accepts an in-place update only when the package ID and signing certificate remain compatible.

## Catalog

The store reads [app-catalog.json](./app-catalog.json):

- **My App Store** — this app, from this repository's Releases.
- **DYSSH** — [dincer552/dyssh](https://github.com/dincer552/dyssh), asset `dyssh.apk`.
- **InstaViewer** — [dincer552/instaviewer](https://github.com/dincer552/instaviewer), asset `instaviewer.apk`.

The app checks each latest GitHub Release, downloads the configured APK asset, verifies its SHA-256 digest when available, and asks Android's package installer to install it.

## Central signing

The repository now contains a central signing service in [signing-service](./signing-service/README.md).

- JKS files and passwords are uploaded once from **My App Store → İMZALAMA**.
- The service encrypts the key material at rest. The app encrypts its admin token with the Android Keystore.
- App build workflows use GitHub's short-lived OIDC identity. They do not need JKS files, signing passwords, or signing-related GitHub secrets.
- Each signing key is bound to one exact GitHub repository and Android package name.
- The same JKS must be retained for every future update of an installed package.

### Initial setup

1. Deploy the service on a private VM and expose it only through an HTTPS endpoint (for example, Cloudflare Tunnel). Do not expose its local port publicly.
2. Generate `SIGNING_ENCRYPTION_KEY` and `SIGNING_ADMIN_TOKEN` on the VM and configure the service as described in [signing-service/README.md](./signing-service/README.md).
3. In **My App Store → İMZALAMA**, enter the HTTPS service URL and admin token. Select the existing JKS, use alias `hattrickssh`, package `com.dincer552.hattrickssh`, repository `dincer552/socialworst`, and upload it.
4. In this repository's Settings → Secrets and variables → Actions → Variables, add `APK_SIGNING_SERVICE_URL` with the service HTTPS URL. This is a URL, not a secret.
5. The service's `ALLOWED_GITHUB_REPOSITORIES` must include this repository.

If the currently installed Store version does not yet contain the İMZALAMA screen, install the debug APK artifact from the Actions run for this commit as a separate bootstrap app, or provision the initial key using the service API. The debug build has the `.debug` package suffix and does not replace the production app.

### Add another app key

In the same İMZALAMA screen, set a unique key ID, exact Android package name, and exact GitHub repository (owner/repo), then select that app's existing JKS. Add the repository to the service allowlist and add the same `APK_SIGNING_SERVICE_URL` Actions variable to that repository. Its workflow must build an unsigned release APK, request a GitHub OIDC token with `id-token: write`, and POST the APK to `/v1/sign` with the registered key ID. See the DYSSH integration example in the signing-service guide.

**Important:** Never upload a replacement JKS for an already published app unless you intentionally plan a new Android package identity. Losing the old key prevents normal in-place updates.
