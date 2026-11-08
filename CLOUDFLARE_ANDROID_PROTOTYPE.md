# Cloudflare SSH on Android: implementation notes

## Correct transport

Cloudflare's documented SSH client pattern is an SSH ProxyCommand:

```ssh-config
Host my-server
    HostName <configured-host>
    User <configured-user>
    ProxyCommand cloudflared access ssh --hostname %h
```

The `cloudflared access ssh` process uses stdin/stdout for the SSH transport stream. Keep stderr separate for diagnostic text and the browser-login URL.

Do not use `cloudflared access ssh --url ssh://localhost:<port>`. The `--url` local TCP forwarding syntax is for Access TCP, not the documented SSH ProxyCommand.

## Android implementation

- Build Cloudflare's client with `GOOS=android GOARCH=arm64 CGO_ENABLED=1` and Android NDK clang.
- Package the ARM64 executable as `src/main/jniLibs/arm64-v8a/libcloudflared.so` and set `jniLibs.useLegacyPackaging = true`, so Android's Package Manager extracts it into `ApplicationInfo.nativeLibraryDir`.
- Start it from `nativeLibraryDir`; do not copy it into `filesDir` or another writable app-home directory. Android 10+ blocks executing files from the writable app home for apps targeting API 29+.
- Set `HOME` and `XDG_CONFIG_HOME` to a private app directory so Access login state is not stored in shared storage.
- Parse the login URL only from stderr and hand it to Android with an HTTPS `ACTION_VIEW` intent.
- Keep stdout untouched for the SSH protocol stream.
- Never log the login URL, Access token, cookies or private key.

## Device validation still required

A successful cross-compile and APK packaging do not prove that Android can execute the packaged PIE binaries from `nativeLibraryDir`, or that the user's Access policy will complete browser authentication. An Android ARM64 phone must verify `ProcessBuilder` launch of cloudflared and `TerminalSession` launch of sshbridge, then complete the browser and SSH flows.
