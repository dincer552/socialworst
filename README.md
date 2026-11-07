# Hattrick SSH

A minimal Android SSH client for connecting to a personal Azure Linux VM through Cloudflare Tunnel.

## Current target

- Android app: Kotlin + Jetpack Compose
- Application ID: `com.dincer552.hattrickssh`
- Minimum Android: API 26
- SSH host: `ssh.dinceryuksek.com`
- SSH port: `22`
- SSH user: `azureuser`
- Authentication: user-imported RSA private key
- Transport: Cloudflare Access + cloudflared client-side proxy + real SSH

## Security

Never commit private keys, Cloudflare tokens, certificates, or credentials. The production implementation must use Android Keystore-backed encryption and verify the SSH server host key.

## Milestones

1. [x] Initial Android/Compose project scaffold
2. [x] GitHub Actions debug APK workflow
3. [ ] Verify Android-compatible cloudflared execution on ARM64
4. [ ] Cloudflare Access browser authentication
5. [ ] Real SSH client and RSA key import
6. [ ] Interactive PTY terminal
7. [ ] Host-key pinning and secure credential storage
8. [ ] End-to-end test against the Azure VM

## Build

GitHub Actions builds a debug APK on pushes to `main` and on manual workflow dispatch. Download it from the workflow run's Artifacts section.

**Important:** The current UI is a scaffold. It does not establish an SSH connection yet. Do not treat the Connect button as functional until the networking milestones are complete.
