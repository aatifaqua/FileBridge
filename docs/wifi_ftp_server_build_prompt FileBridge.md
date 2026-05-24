# WiFi FTP Server — Android App Build Prompt

## Project Overview

Build an open-source Android application that turns a device into a Wi-Fi FTP/FTPS server, allowing other devices on the same network to browse and transfer files. The app targets Google Play distribution and is designed to be clean, modern, and accessible to non-technical users.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material Design 3) |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | Latest stable |
| FTP Engine | Apache FTPServer (embedded) |
| Dependency Injection | Hilt |
| Async | Kotlin Coroutines + Flow |
| Storage | DataStore (Preferences) |
| Build System | Gradle with version catalogs |
| License | Apache 2.0 |

---

## Architecture

Follow **MVVM + Clean Architecture**:

- `data/` — FTP server wrapper, DataStore repository, storage access
- `domain/` — Use cases (StartServer, StopServer, GetServerConfig, etc.)
- `ui/` — Compose screens, ViewModels
- `service/` — Foreground Service for server lifecycle
- `widget/` — Glance-based home screen widget
- `receiver/` — Boot receiver for auto-start

Keep all FTP/server logic out of ViewModels and UI layer entirely.

---

## Feature Specification

### 1. FTP Server Core

- Embedded Apache FTPServer running inside a **Foreground Service**
- Server binds to the device's current Wi-Fi IP address
- Support both **plain FTP** and **FTPS (Explicit TLS)** — user can choose in settings
- For FTPS: generate a self-signed certificate on first launch and store it in the app's private directory; display a notice to users that the certificate is self-signed
- Support **Passive Mode (PASV)** — configure a passive port range (default: 50000–51000), allow user to customize
- Configurable FTP port (default: 2121)
- The server must handle graceful shutdown (close all sessions before stopping)
- Emit server events (client connect, disconnect, file upload, download) via a Kotlin Flow consumed by the UI

### 2. Authentication

- **Mode 1 — Anonymous**: no credentials required (clearly warn user this is insecure)
- **Mode 2 — Single User**: username + password set by user
- Passwords stored using Android Keystore or EncryptedSharedPreferences — never plaintext
- Show a warning badge in the UI when anonymous mode is active

### 3. Storage & File Access

- Use `MANAGE_EXTERNAL_STORAGE` permission for broad filesystem access
- Clearly explain the permission on a dedicated onboarding/permission screen before requesting it
- Allow user to select the **root directory** exposed via FTP:
  - Internal Storage root
  - Any directory via system file picker (`ACTION_OPEN_DOCUMENT_TREE`)
  - SD Card (if present, detected via `StorageManager`)
- Detect SD card availability and show it as a selectable option
- **Read/Write toggle**: user can restrict the FTP server to read-only mode

### 4. Home Screen (Main UI)

The main screen has two visual states:

**Server OFF state:**
- Large status card: "Server is stopped" with a subtle idle illustration or icon
- Prominent Start button (FAB or large filled button)
- Summary of current config (port, auth mode, root dir) shown in a card below
- No connection details shown

**Server ON state:**
- Large status card: "Server is running" with a pulsing active indicator
- Connection details displayed prominently:
  - `ftp://192.168.x.x:2121` (plain) or `ftps://...` (secure)
  - Username (if not anonymous)
  - Password (hidden by default, tap to reveal)
- **QR Code** generated from the server URL — tap to enlarge in a dialog
- Copy-to-clipboard button next to the address
- Stop button
- Live connected client count badge

### 5. Navigation & Screens

Use a **bottom navigation bar** with three destinations:

| Tab | Icon | Purpose |
|---|---|---|
| Home | Server/Wifi icon | Start/stop + connection info |
| Logs | List icon | Activity log |
| Settings | Gear icon | All configuration |

### 6. Activity Log Screen

- Scrollable list of timestamped log entries
- Entry types: server started, server stopped, client connected (show IP), client disconnected, file downloaded, file uploaded, authentication failure
- Color-coded entry types (e.g. red for auth failures, green for connections)
- **Clear log** button (with confirmation dialog)
- Log is in-memory only (not persisted across app restarts) — keep last 500 entries maximum

### 7. Settings Screen

Use a sectioned list layout (Compose `LazyColumn` with section headers):

**Server**
- Protocol: FTP / FTPS (radio group)
- Port number (number input, validated 1024–65535)
- Passive mode port range (two number inputs: min and max)

**Authentication**
- Mode: Anonymous / Single User (segmented control or radio)
- Username (text input, visible only when Single User is selected)
- Password (text input with show/hide toggle, visible only when Single User)

**Storage**
- Root directory selector (shows current path, tap to change via picker)
- SD card quick-select button (visible only if SD card detected)
- Access mode: Read & Write / Read Only (toggle)

**Behavior**
- Start server on app launch (toggle)
- Auto-start on device boot (toggle)
- Keep screen on while server is running (toggle)
- Show persistent notification (always on when server is running — not toggleable, required for foreground service)

**Security**
- FTPS certificate info (tap to view certificate details: expiry, fingerprint)
- Regenerate certificate (with confirmation)

**About**
- App version
- Open source licenses
- GitHub repository link
- Translate / contribute link (links to a Crowdin or Weblate project)

### 8. Persistent Notification

While the server is running, show a persistent notification (tied to the Foreground Service):

- **Title**: "FTP Server Running"
- **Body**: `ftp://192.168.x.x:2121` (or ftps://)
- **Actions**: Stop button
- Notification channel: "Server Status" (importance: LOW, no sound)
- Tapping the notification opens the app to the Home tab

When the server is stopped, dismiss the notification.

### 9. Home Screen Widget

Built with **Jetpack Glance**:

- Widget size: fits a 2×1 cell minimum
- Displays:
  - App icon + "FTP Server" label
  - Toggle switch (Start / Stop)
  - When running: `IP:PORT` text below the toggle
  - When stopped: "Tap to start" hint text
- Tapping the toggle starts or stops the server via the Foreground Service
- Widget state updates automatically when server state changes (observe via broadcast or WorkManager)
- Widget background respects system dark/light mode

### 10. Onboarding Flow

First-launch only, shown as a **HorizontalPager** (3 steps):

1. **Welcome** — App name, tagline, brief description
2. **Permission** — Explain `MANAGE_EXTERNAL_STORAGE` and why it's needed; "Grant Permission" button
3. **Quick Setup** — Set username/password or choose anonymous, pick root directory

After completion, mark onboarding as done in DataStore and never show again.

---

## UI / UX Guidelines

- **Material Design 3** throughout — use `MaterialTheme` tokens for all colors, typography, shapes
- **Dynamic Color (Material You)**: enabled on Android 12+; graceful fallback palette for older versions
- **Dark / Light / System** theme — user selectable in Settings
- All interactive elements have minimum touch target of 48×48dp
- Empty states: meaningful illustrations or icons + helper text (e.g., empty log screen)
- Error states: inline validation on settings inputs, Snackbar for runtime errors
- Loading states: use `CircularProgressIndicator` during server start/stop transitions
- Transitions: use Compose animated visibility / crossfade for server state changes
- No custom fonts — use the system default (Roboto) via MaterialTheme

---

## Permissions

| Permission | Reason |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Full filesystem access for FTP root |
| `INTERNET` | FTP socket binding |
| `ACCESS_WIFI_STATE` | Detect Wi-Fi IP address |
| `ACCESS_NETWORK_STATE` | Detect connectivity |
| `FOREGROUND_SERVICE` | Keep server alive in background |
| `FOREGROUND_SERVICE_DATA_SYNC` | Foreground service type for file transfer |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on boot |
| `WAKE_LOCK` | Keep CPU awake during file transfers |
| `REQUEST_INSTALL_PACKAGES` | Not needed — omit |

---

## Internationalisation (i18n)

- All user-facing strings in `res/values/strings.xml` — zero hardcoded strings in Kotlin or Compose files
- Use string formatting placeholders (`%1$s`, `%2$d`) for dynamic values
- RTL layout support via standard Compose `Modifier` semantics and `LocalLayoutDirection`
- Default locale: English (`en`)
- Additional translations via community (provide Crowdin / Weblate config in repo)
- Include `translationHelp.md` in the repo explaining how to contribute translations

---

## Open Source Setup

**Repository structure:**
```
/app
/gradle
/fastlane            ← optional, for Play Store metadata
README.md
LICENSE              ← Apache 2.0
CONTRIBUTING.md
.github/
  ISSUE_TEMPLATE/
  workflows/          ← CI: build + lint on every PR
```

**README should include:**
- What the app does (1 paragraph)
- Screenshots
- Build instructions
- How to contribute
- Translation link
- License badge

**GitHub Actions CI:**
- On every PR: run `./gradlew lint` and `./gradlew testDebugUnitTest`
- On push to `main`: run debug build to verify compilation

---

## Security Considerations

- Never log passwords anywhere (logcat, file, or UI)
- Warn prominently (banner/badge) when anonymous mode is active
- FTPS certificate regeneration clears old certificate immediately
- Validate all user inputs in Settings before saving (port ranges, empty username/password)
- Server only binds to local Wi-Fi IP — never `0.0.0.0` unless explicitly documented

---

## Out of Scope (v1)

- SFTP
- IPv6
- Multiple simultaneous user accounts
- Web browser-based file manager UI
- Cloud storage integration
- PC desktop companion app
