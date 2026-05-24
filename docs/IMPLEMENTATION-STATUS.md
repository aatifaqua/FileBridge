# FileBridge — Implementation Status

This document tracks what has actually been implemented against the phase docs. It is updated as
phases land. Source of truth for *requirements* remains the phase docs; this file records *progress*.

_Last updated: 2026-05-24_

## Summary

| Phase | Status | Verified |
|---|---|---|
| 1 — Foundation & Build Setup | ✅ Implemented | `assembleDebug` + `testDebugUnitTest` green |
| 2 — Data Layer | ✅ Implemented | builds; 6 unit tests pass |
| 3 — Domain Layer | ✅ Implemented | builds; 21 unit tests pass |
| 4 — FTP Server Engine | ✅ Implemented | `assembleDebug` green; 27 unit tests pass; instrumented tests written |
| 5 — Foreground Service & Notification | ✅ Implemented | `assembleDebug` green; 27 unit tests pass; instrumented tests written |
| 6–10 | ⬜ Not started | — |

## Toolchain decisions (locked in)

The original scaffold used bleeding-edge AGP 9.1.1 / Gradle 9.3.1 / compileSdk 36. To get a reliable
Compose + Hilt build, the toolchain was **pinned to a known-good stable set**:

- AGP **8.7.3**, Gradle **8.11.1**
- Kotlin **2.0.21** + Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`)
- KSP **2.0.21-1.0.28** (KSP1 — `ksp.useKSP2` must stay **off**; Hilt 2.52 is incompatible with KSP2)
- Hilt **2.52**
- compileSdk / targetSdk **35**, minSdk **26**
- Java **17** bytecode target

Local build note: the system default JDK is 24 (unsupported by AGP 8.7), so the Gradle daemon is
pinned to Android Studio's bundled **JBR 21** via `org.gradle.java.home` in the *user-level*
`~/.gradle/gradle.properties` (kept out of the repo so CI stays portable). CI uses Temurin JDK 17.

Package/namespace: **`com.aionyxe.filebridge`** (kept from the scaffold; the PRD's `app.filebridge`
was intentionally not adopted).

FTPS certificate key algorithm (for Phase 4): **RSA 2048**.

## Phase 1 — Foundation & Build Setup ✅

- Version catalog (`gradle/libs.versions.toml`) covering Compose BOM, Hilt, Coroutines, DataStore,
  security-crypto, Apache FTPServer, BouncyCastle (prov + pkix), ZXing, Glance, Material, plus test
  libs (JUnit, Turbine, MockK, Robolectric, commons-net, androidx.test).
- Root + `app/build.gradle.kts`: Compose, Hilt, KSP, ktlint plugins; debug `.debug` suffix; release
  R8 + resource shrinking; `BuildConfig` fields `GITHUB_URL` / `TRANSLATION_URL`.
- `AndroidManifest.xml`: all required permissions (INTERNET, ACCESS_WIFI_STATE,
  ACCESS_NETWORK_STATE, FOREGROUND_SERVICE[_DATA_SYNC], WAKE_LOCK, RECEIVE_BOOT_COMPLETED,
  POST_NOTIFICATIONS, MANAGE_EXTERNAL_STORAGE + legacy R/W with maxSdk 29), `FileBridgeApp`,
  `MainActivity`.
- `FileBridgeApp : Application` (`@HiltAndroidApp`), `MainActivity` (`@AndroidEntryPoint`) showing a
  placeholder `FileBridgeTheme { Surface { Text("FileBridge") } }`.
- Hilt scaffolding: `di/AppModule` (dispatcher qualifiers `@IoDispatcher`/`@MainDispatcher`/
  `@DefaultDispatcher`, `@ApplicationScope`), placeholder `DataModule`/`DomainModule`/`ServerModule`.
- Theme placeholder (`ui/theme/Theme.kt`), Material 3 DayNight XML theme, `colors.xml` placeholder.
- Tooling: `.editorconfig`, ktlint (non-blocking via `ignoreFailures`), `proguard-rules.pro` with
  FTPServer/BouncyCastle keep rules staged.
- Repo files: `LICENSE` (Apache 2.0), `README.md`, `CONTRIBUTING.md`, `.github/workflows/ci.yml`
  (checkout → JDK 17 → assembleDebug → lint).

## Phase 2 — Data Layer ✅

- **Settings (DataStore Preferences):** `SettingsKeys`, `SettingsRepository` (interface) +
  `SettingsRepositoryImpl` exposing `Flow<AppSettings>` and per-field suspend setters. `AppSettings`
  domain model with spec defaults (port 2121, pasv 50000–51000, username `ftpuser`, etc.).
- **Credentials (encrypted):** `CredentialsRepository` + impl over `EncryptedSharedPreferences`
  (AES256-GCM master key). Only the password is encrypted; username lives in settings.
- **Storage discovery:** `StorageLocation`, `StorageRepository` + impl using `StorageManager`
  (internal + removable volumes, SD-card detection, tree-URI → `File` best-effort resolution and
  persistable-permission helper).
- **Logs (in-memory):** `LogEntry`, `LogType`, `LogRepository` + impl backed by `MutableStateFlow`
  with a 500-entry FIFO ring buffer (`@Singleton`).
- **Hilt:** `DataModule` provides `DataStore<Preferences>` + `EncryptedSharedPreferences` and binds
  all four repositories.
- **Tests:** `LogRepositoryImplTest` and `SettingsRepositoryImplTest` (JVM unit);
  `CredentialsRepositoryImplTest` (instrumented `androidTest` — needs Android Keystore).

## Phase 3 — Domain Layer ✅

- **Models:** `ServerConfig`, `ServerState` (sealed), `ConnectionInfo`, `CertificateInfo`, enums
  `Protocol` / `AuthMode` / `AccessMode` / `ThemeMode`.
- **Abstractions (interfaces, implemented in Phase 4):** `FtpServerController`, `ServerEvent`
  (sealed), `CertificateManager`, `NetworkInfoProvider` + `ConnectivityStatus`.
- **Use cases:** Start/Stop server, ObserveServerState, ObserveConnectionInfo,
  ObserveConnectedClients, GetAppSettings, UpdateAppSetting (sealed `SettingPatch`), SetCredentials,
  Observe/Clear logs, Regenerate/GetCertificateInfo, ListStorageRoots, IsSdCardPresent,
  CompleteOnboarding, IsOnboardingComplete.
- **Validation:** `SettingsValidator` (port range, pasv range/overlap, username, password) returning
  sealed `ValidationResult`; `ValidationException` carries a `@StringRes` reason. Error strings added
  to `strings.xml`.
- **Tests:** `SettingsValidatorTest`, `StartServerUseCaseTest`, `ObserveConnectionInfoUseCaseTest`,
  `SetCredentialsUseCaseTest` (MockK + Turbine).

## Phase 4 — FTP Server Engine ✅

- **`data/server/ServerEventBus`** — `@Singleton` `MutableSharedFlow<ServerEvent>` (capacity 64,
  DROP_OLDEST).
- **`data/server/SessionContext`** — `ThreadLocal<String>` propagates client IP into
  `AppUserManager.authenticate`.
- **`data/server/EventBridgeFtplet`** — `DefaultFtplet` override bridging connect/disconnect/upload/
  download callbacks into the event bus; maintains `connectedClientCount`.
- **`data/server/AppUserManager`** — `UserManager` impl; anonymous + single-user modes; constant-time
  credential comparison; `WritePermission` gated on `AccessMode`.
- **`data/server/cert/CertificateManagerImpl`** — BouncyCastle RSA-2048 self-signed cert, BKS
  keystore persisted to `filesDir/ftps_keystore.bks`, password in `EncryptedSharedPreferences`.
- **`data/network/WifiNetworkInfoProvider`** — resolves IPv4 Wi-Fi address; `callbackFlow`
  connectivity stream.
- **`data/server/FtpServerControllerImpl`** — state machine, PASV config, Explicit TLS for FTPS,
  partial `WakeLock`, 5 s graceful drain on stop, bus → log collector.
- **`di/ServerModule`** — binds all three interfaces.
- **`app/build.gradle.kts`** — `META-INF/versions/9/OSGI-INF/MANIFEST.MF` excluded (BouncyCastle
  conflict).
- **Tests (instrumented):** `CertificateManagerImplTest`, `FtpServerControllerImplTest`,
  `TestNetworkInfoProvider`.

## Phase 5 — Foreground Service & Notification ✅

- **`service/notification/NotificationFactory`** — `@Singleton`; creates/updates three notification
  variants:
  - `starting()` — indeterminate progress, ongoing, title "Starting FTP server…"
  - `running(info)` — ongoing, non-dismissible, body = server URL, Stop `PendingIntent` action,
    content intent opens `MainActivity`.
  - `error(msg)` — dismissible, auto-cancel.
  - `registerChannel()` — creates channel `server_status` (`IMPORTANCE_LOW`, no sound/vibration);
    called from `FileBridgeApp.onCreate` (idempotent).
- **`service/FtpForegroundService`** — `LifecycleService` + `@AndroidEntryPoint`:
  - `onCreate` starts a `lifecycleScope` state observer that keeps the notification in sync with
    `ObserveServerStateUseCase` / `ObserveConnectionInfoUseCase`.
  - `ACTION_START` → `ServiceCompat.startForeground` (type `dataSync`) immediately + launches
    `StartServerUseCase` coroutine.
  - `ACTION_STOP` → launches `StopServerUseCase`, waits for `Stopped`, then `stopForeground(true)`
    + `stopSelf()`.
  - `onDestroy` → calls `StopServerUseCase` if stop was not already initiated (system kill path).
  - Returns `START_NOT_STICKY` — no zombie revival.
- **`service/ServiceLauncher`** — `object`; `start(context)` uses `ContextCompat.startForegroundService`;
  `stop(context)` uses plain `startService`.
- **`FileBridgeApp`** updated — injects `NotificationFactory`, calls `registerChannel()` in
  `onCreate`.
- **`AndroidManifest.xml`** updated — `<service .FtpForegroundService exported="false"
  foregroundServiceType="dataSync" />`.
- **`res/values/strings.xml`** updated — notification channel name/description + notification
  title/action strings.
- **Tests (instrumented):** `FtpForegroundServiceTest` — `ACTION_START` state transition,
  `ACTION_STOP` reaches `Stopped`, `onTaskRemoved` does not crash service.

## Verification status

- `./gradlew :app:assembleDebug` — **green** (phases 1–5 compile, Hilt graph builds).
- `./gradlew :app:testDebugUnitTest` — **green**: 27 tests, all passing.
- Instrumented tests (`androidTest`) require emulator/device; not run in this session.

## Next up

- **Phase 6 — UI Shell:** Theme tokens, NavHost + bottom navigation (Home / Settings / Logs tabs),
  `FileBridgeTheme` with dynamic color opt-in, `Scaffold` shell, `MainActivity` wired to nav graph.
