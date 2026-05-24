# FileBridge — Implementation Status

## ✅ ALL PHASES COMPLETE

**74/74 unit tests passing · `assembleDebug` BUILD SUCCESSFUL**

---

## Phases

### Phase 1 — Foundation ✅
AGP 8.7.3, Kotlin 2.0.21, Hilt 2.52, Compose BOM 2024.12.01, single `:app` module.

### Phase 2 — Data Layer ✅
`SettingsRepositoryImpl` (DataStore), `CredentialsRepositoryImpl` (EncryptedSharedPreferences),
`StorageRepositoryImpl`, `LogRepositoryImpl`, `DataModule` Hilt bindings.

### Phase 3 — Domain Layer ✅
All models (`AppSettings`, `ServerState`, `Protocol`, `AuthMode`, `AccessMode`, `ThemeMode`,
`ConnectionInfo`, `CertificateInfo`), use-cases, `SettingsValidator`, `ValidationResult`.

### Phase 4 — FTP Server Engine ✅
- `ServerEventBus` — singleton `MutableSharedFlow` with `DROP_OLDEST`
- `SessionContext` — `ThreadLocal<String>` IP propagation (Ftplet → UserManager)
- `EventBridgeFtplet` — client connect/disconnect/upload/download events
- `AppUserManager` — constant-time credential check, anonymous auth
- `CertificateManagerImpl` — RSA 2048 BKS keystore, BouncyCastle, 10-year cert
- `WifiNetworkInfoProvider` — `callbackFlow` wrapping `ConnectivityManager`
- `FtpServerControllerImpl` — full state machine, PASV, FTPS (explicit TLS), wake lock
- Packaging: BouncyCastle OSGI manifest conflict excluded

### Phase 5 — Foreground Service & Notification ✅
- `NotificationFactory` — starting / running / error notifications, Stop action
- `FtpForegroundService` — `LifecycleService`, `FOREGROUND_SERVICE_TYPE_DATA_SYNC`
- `ServiceLauncher` — `ContextCompat.startForegroundService` / plain `startService`
- `FileBridgeApp` — `@HiltAndroidApp`, registers notification channel

### Phase 6 — UI Shell ✅
- Material 3 theme: teal seed (`#006874`), light/dark palettes, dynamic color on S+
- `Destination` sealed class: Onboarding / Home / Logs / Settings
- `FileBridgeNavHost` + `FileBridgeBottomBar` (hidden on Onboarding)
- `MainScreen` + `MainViewModel`; mid-session onboarding completion triggers auto-nav to Home
- `MainActivity` — `enableEdgeToEdge`, `FLAG_KEEP_SCREEN_ON`, auto-start on launch
- Components: `StatusCard`, `SectionHeader`, `LoadingIndicator`, `EmptyState`,
  `WarningBanner`, `LabeledSwitch`, `PortInputField`

### Phase 7 — Home Screen ✅
- `HomeUiState`, `HomeViewModel` — nested `combine` for 6 flows
- `HomeScreen` — `Crossfade`, animated pulsing status card, QR dialog, stop-with-clients dialog
- `QrCodeImage` — `produceState` + ZXing on `Dispatchers.Default`
- **Tests**: `HomeViewModelTest` (8), `QrCodeRoundTripTest` (2)

### Phase 8 — Settings & Logs Screens ✅
- `LogsViewModel` + `LogsScreen` — reverse-chronological, color-coded by `LogType`, `EmptyState`
- `SettingsField` enum, `SettingsUiState`, `SettingsViewModel` — nested combines, inline validation,
  server-lock guard, async cert regen
- `SettingsScreen` — 7 sections, segmented buttons, `ACTION_OPEN_DOCUMENT_TREE` picker,
  cert detail/regen dialogs, About with `BuildConfig` links
- **Tests**: `LogsViewModelTest` (5), `SettingsViewModelTest` (18)

### Phase 9 — Onboarding, Permissions & First-Run UX ✅
- `OnboardingUiState`, `OnboardingViewModel` — 3-step state machine, `canFinish` guards
- `PermissionHelpers` — `rememberStoragePermissionState` (API 30+ Settings redirect / API 29−
  runtime dialog), `rememberNotificationPermissionState` (POST_NOTIFICATIONS on API 33+)
- `OnboardingScreen` — `HorizontalPager` with `userScrollEnabled = false`, animated dot indicators
  - Step 0: Welcome with bullet list
  - Step 1: Storage + notification permissions with skip / "why?" inline expansion
  - Step 2: Auth mode, credentials, root directory picker
- `CompleteOnboardingUseCase` integration; `MainScreen` navigates to Home on flag flip
- **Tests**: `OnboardingViewModelTest` (15)

### Phase 10 — Widget, Boot Auto-Start, CI & Release ✅
- `FtpServerWidget` (Glance 1.1.1) — 2×1 home-screen widget, `PreferencesGlanceStateDefinition`,
  `StartServerWidgetAction` / `StopServerWidgetAction`
- `FtpServerWidgetReceiver` — `GlanceAppWidgetReceiver`
- `FtpForegroundService` — pushes widget state on every `ServerState` transition
- `BootCompletedReceiver` — `@AndroidEntryPoint`, reads `startOnBoot` via `runBlocking`,
  starts `FtpForegroundService` on `BOOT_COMPLETED`
- `AndroidManifest.xml` — boot receiver, widget receiver, `localeConfig`
- `res/xml/ftp_server_widget_info.xml` — 2×1 appwidget-provider
- `res/xml/locale_config.xml` — Android 13+ locale picker (`en`)
- `app/build.gradle.kts` — signing config (env vars or `keystore.properties`), `Properties` import
- `app/proguard-rules.pro` — comprehensive keep rules (FTPServer, BC, Hilt, Glance, ZXing)
- `.github/workflows/ci.yml` — PR / main: lint + tests + debug APK; tag `v*`: signed release APK + AAB
- `.github/ISSUE_TEMPLATE/` — bug report, feature request
- `.github/PULL_REQUEST_TEMPLATE.md`
- `CODE_OF_CONDUCT.md` (Contributor Covenant)
- `CONTRIBUTING.md` — full code-style, branching, test, translation guide
- `translationHelp.md` — how to add a locale, Weblate link placeholder
- `README.md` — description, features, build instructions, architecture, privacy statement
- `fastlane/metadata/android/en-US/` — title, short/full description, changelog v1
- `.gitignore` — `keystore.properties`, `*.jks`, `*.keystore` added

---

## Test summary

| Test file | Count |
|-----------|-------|
| `HomeViewModelTest` | 8 |
| `QrCodeRoundTripTest` | 2 |
| `LogsViewModelTest` | 5 |
| `SettingsViewModelTest` | 18 |
| `OnboardingViewModelTest` | 15 |
| Domain / data tests (Phases 1–4) | 26 |
| **Total** | **74** |

All 74 pass. `assembleDebug` is green with 0 errors, 0 warnings (deprecated icon uses updated to AutoMirrored).
