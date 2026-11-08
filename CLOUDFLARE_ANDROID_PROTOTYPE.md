# Cloudflare SSH on Android: implementation notes

## Correct transport

Cloudflare's documented SSH client pattern is an SSH ProxyCommand:

```ssh-config
Host azure-vm
    HostName ssh.dinceryuk.com
    User azureuser
    ProxyCommand cloudflared access ssh --hostname %h
```

The `cloudflared access ssh` process uses stdin/stdout for the SSH transport stream. Keep stderr separate for diagnostic text and the browser-login URL.

Do not use `cloudflared access ssh --url ssh://localhost:<port>`. The `--url` local TCP forwarding syntax is for Access TCP, not the documented SSH ProxyCommand.

## Android implementation

- Build Cloudflare's client with `GOOS=android GOARCH=arm64 CGO_ENABLED=1` and Android NDK clang.
- Extract the ARM64 executable into app-private storage, set owner-executable permission and check whether Android permits launching it.
- Set `HOME` and `XDG_CONFIG_HOME` to a private app directory so Access login state is not stored in shared storage.
- Parse the login URL only from stderr and hand it to Android with an HTTPS `ACTION_VIEW` intent.
- Keep stdout untouched for the SSH protocol stream.
- Never log the login URL, Access token, cookies or private key.

## Device validation still required

A successful cross-compile does not prove Android's runtime will permit process execution from app-private storage or that the user's Access policy will complete browser authentication. The GitHub Actions workflow builds and packages the native ARM64 binaries; an actual Android ARM64 phone must be used for end-to-end validation.
