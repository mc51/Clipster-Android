# Clipster - Android Client

[![CI](https://github.com/mc51/Clipster-Android/actions/workflows/ci.yml/badge.svg)](https://github.com/mc51/Clipster-Android/actions/workflows/ci.yml)
[![Release](https://github.com/mc51/Clipster-Android/actions/workflows/build.yml/badge.svg)](https://github.com/mc51/Clipster-Android/actions/workflows/build.yml)

Clipster is a multi platform cloud clipboard:
Copy a text or image on your smartphone and paste it on your desktop, or vice versa.
Easy, secure, open source.
Supports Android, Linux, MacOS, Windows and all browsers.

This is the Android Client.
There also is a [Clipster-Desktop](https://github.com/mc51/Clipster-Desktop) client for Linux, MacOS and Windows.
You can run your own [Clipster-Server](https://github.com/mc51/Clipster-Server) on a Linux machine.
Or you can use the public server at [https://clipster.cc](https://clipster.cc).

![Clipster demo](resources/demo_01.gif)

## Setup

Download the latest [clipster.apk](https://github.com/mc51/Clipster-Android/releases/latest/download/clipster.apk) to your Android device (Android 8 or newer) and install it. You may have to allow installing apps from unknown sources in your settings.

## Usage

- On the first run, register a new account or log in. Leave the server empty to use the public server.
- **Share:** long-press a text or image, share it to `Clipster`. Or copy a text and tap `Share Clipboard`.
- **Get:** tap `Get last Clip` to copy the newest clip to your clipboard, or `Get all Clips` to pick one. Long-press the app icon for a `Get last Clip` shortcut.

### Own server with a self-signed certificate

Enable `Allow self-signed certificate` on the login screen. On the first connection Clipster shows the certificate's SHA-256 fingerprint. Only trust it if it matches your server's:

```sh
openssl x509 -noout -fingerprint -sha256 -in cert.pem
```

Clipster then only accepts exactly this certificate and warns you if it ever changes. On Android 17 and newer, Clipster asks for the "Nearby devices" permission if your server is on your local network.

## Security

Clips are encrypted on your device before they are sent, the server only stores encrypted data. Your password never leaves the device. See [SECURITY.md](SECURITY.md) for how this works, its limits, and how to report vulnerabilities, and [PRIVACY.md](PRIVACY.md) for the data the server sees.

## Development

Needs JDK 17+ and the Android SDK. Build a debug APK with `./gradlew assembleDebug`. See [CONTRIBUTING.md](CONTRIBUTING.md) for tests, formatting and releases.

## Roadmap

- [x] Encrypt / Decrypt clipboard locally and only transmit encrypted data to server
- [x] Add clipboard history: share multiple Clips
- [x] Support image sharing
- [ ] F-Droid release
- [ ] Google Play Store release

## Contributions

Contributions are very welcome. If you come across a bug, please open an issue. The same goes for feature requests.
