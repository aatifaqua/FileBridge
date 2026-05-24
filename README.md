# FileBridge

[![CI](https://github.com/aionyxe/FileBridge/actions/workflows/ci.yml/badge.svg)](https://github.com/aionyxe/FileBridge/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

FileBridge turns your Android phone or tablet into a **Wi-Fi FTP / FTPS file server**. Any device on the same network — Windows, macOS, Linux, another Android — can browse and transfer files using any standard FTP client, no cables required.

> **No telemetry, no cloud, no accounts.** Everything stays on your local network.

## Features

- 📡 **FTP and FTPS** — plain FTP or TLS-encrypted FTPS with a self-signed certificate (generated automatically)
- 🔐 **Anonymous or single-user auth** — choose password-protected or open access
- 📁 **Custom root directory** — point the server at any folder you choose, including SD card
- 🔒 **Read-only mode** — allow browsing but block uploads and deletes
- 🔔 **Persistent notification** with one-tap Stop — server runs in the foreground, survives screen-off
- 🔄 **Auto-start on boot** and **auto-start on app launch** options
- 📊 **Activity log** — connection, transfer, and auth events in real time
- 🏠 **Home-screen widget** — toggle the server without opening the app
- 🎨 **Material You** — dynamic color, light/dark/system theme
- 🌍 **Internationalization-ready** — all strings in `strings.xml`; community translations welcome

## Screenshots

_Coming soon._

## Build

**Requirements:** JDK 17, Android SDK platform 35.

```bash
# Debug build
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Lint check
./gradlew lint
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Release build

1. Create `keystore.properties` in the project root (gitignored):
   ```properties
   storeFile=/path/to/keystore.jks
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```
2. Run `./gradlew assembleRelease bundleRelease`.

## Architecture

```
app/
├── data/          # Repositories, DataStore, FTP engine, BouncyCastle cert
├── domain/        # Use cases, models, validators
├── service/       # FtpForegroundService, ServiceLauncher
├── ui/            # Compose screens (Home, Settings, Logs, Onboarding)
│   ├── home/
│   ├── settings/
│   ├── logs/
│   └── onboarding/
├── widget/        # Glance home-screen widget
└── receiver/      # Boot receiver
```

Tech stack: Kotlin · Jetpack Compose (Material 3) · Hilt · Coroutines/Flow · Apache FTPServer · BouncyCastle · ZXing · Glance

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [translationHelp.md](translationHelp.md).

Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) before participating.

## Privacy

FileBridge collects **no data**. It does not include analytics, crash reporting, or any network requests beyond the FTP server it creates on your local network. All settings are stored locally on-device.

## License

Licensed under the [Apache License 2.0](LICENSE).
