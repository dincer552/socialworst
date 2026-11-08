# Third-party notices

Hattrick SSH includes or builds the following third-party components.

- **cloudflared**: Cloudflare, Inc., Apache License 2.0. Source: https://github.com/cloudflare/cloudflared
- **cloudflared Android browser-fallback patch**: adapted from the Android cloudflared build work in Tinyhack SSH by Yohanes, MIT License. Source: https://github.com/yohanes/tinyhack-ssh
- **JSch (mwiede fork)**: Revised BSD License. Source: https://github.com/mwiede/jsch
- **Termux terminal-view / terminal-emulator**: GPL-3.0-or-later. Source: https://github.com/termux/termux-app

The native Android build patch is limited to delegating browser launch to the Android host application and excluding the Unix browser launcher for Android. The app opens Cloudflare Access login URLs with an Android ACTION_VIEW intent.

See each upstream project for its complete license text. When distributing builds, include the corresponding license texts and comply with each component's terms.
