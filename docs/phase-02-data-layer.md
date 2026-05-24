# Phase 2 — Data Layer: Persistence & Secure Storage

## Goal

Implement all persistent storage abstractions: user settings (DataStore Preferences), credentials (EncryptedSharedPreferences / Keystore), and a small abstraction for filesystem/storage queries (SD card detection, document tree URIs). Higher layers consume only the repository interfaces defined here.

## Deliverables

### Settings persistence — DataStore (Preferences)
- `data/settings/SettingsKeys.kt` — typed Preferences keys for:
  - `protocol` (enum: `FTP` / `FTPS`)
  - `ftpPort` (Int, default 2121)
  - `pasvMinPort`, `pasvMaxPort` (Int, defaults 50000 / 51000)
  - `authMode` (enum: `ANONYMOUS` / `SINGLE_USER`)
  - `username` (String, default `"ftpuser"`)
  - `rootDirUri` (String, default empty = internal storage root)
  - `accessMode` (enum: `READ_WRITE` / `READ_ONLY`)
  - `startOnAppLaunch` (Bool, default false)
  - `startOnBoot` (Bool, default false)
  - `keepScreenOn` (Bool, default false)
  - `themeMode` (enum: `SYSTEM` / `LIGHT` / `DARK`)
  - `onboardingComplete` (Bool, default false)
- `data/settings/SettingsRepository.kt` — interface exposing `Flow<AppSettings>` + suspend setters per field.
- `data/settings/SettingsRepositoryImpl.kt` — DataStore-backed implementation; serialization via simple key-by-key reads. Wraps `Context.dataStore` (file `settings.preferences_pb`).
- `domain/model/AppSettings.kt` — immutable data class (lives in domain so UI can consume without depending on data).

### Credentials — Encrypted storage
- `data/credentials/CredentialsRepository.kt` — interface: `suspend fun getPassword(): String?`, `suspend fun setPassword(value: String)`, `suspend fun clear()`.
- `data/credentials/CredentialsRepositoryImpl.kt` — uses `EncryptedSharedPreferences` with `MasterKey` (AES256_GCM). File: `credentials.enc`. Username lives in settings (non-secret); only password is encrypted.
- Passwords MUST never appear in logcat. Add a unit test asserting `toString()` of any wrapper redacts the value.

### Storage discovery
- `data/storage/StorageLocations.kt` — data class `StorageLocation(displayName, path, isRemovable)`.
- `data/storage/StorageRepository.kt` — interface:
  - `fun listStorageRoots(): List<StorageLocation>`
  - `fun isSdCardPresent(): Boolean`
  - `fun resolveRootDir(uriOrPath: String): File?` (handles `content://` tree URIs by persisting permission and resolving via `DocumentFile`).
- `data/storage/StorageRepositoryImpl.kt` — uses `StorageManager.storageVolumes` to enumerate, filter removable.

### Logs (in-memory buffer — referenced by Phase 8)
- `data/logs/LogEntry.kt` — `data class LogEntry(timestamp, type, message, ip?)`.
- `data/logs/LogType.kt` — enum: `SERVER_STARTED`, `SERVER_STOPPED`, `CLIENT_CONNECTED`, `CLIENT_DISCONNECTED`, `FILE_UPLOADED`, `FILE_DOWNLOADED`, `AUTH_FAILURE`.
- `data/logs/LogRepository.kt` — interface: `val entries: StateFlow<List<LogEntry>>`, `fun append(entry: LogEntry)`, `fun clear()`.
- `data/logs/LogRepositoryImpl.kt` — backed by a `MutableStateFlow<List<LogEntry>>` with ring-buffer trimming at 500 entries. `@Singleton`.

### Hilt wiring
- `di/DataModule.kt` — binds all three repositories (`@Binds`), provides `DataStore<Preferences>` and `EncryptedSharedPreferences` singletons.

### Tests
- `SettingsRepositoryImplTest` — round-trip each setting, default values returned when unset.
- `CredentialsRepositoryImplTest` — set/get/clear; no plaintext in `Context.filesDir`.
- `LogRepositoryImplTest` — ring buffer caps at 500; `clear()` empties; `entries` emits on append.
- Uses an in-memory DataStore (test rule) and `Robolectric` for `EncryptedSharedPreferences` if needed; otherwise instrumented test under `androidTest/`.

## Acceptance criteria

1. All repository interfaces are `interface` (not `class`), enabling fake implementations in later tests.
2. Killing and restarting the app preserves all `AppSettings` and the username; passwords decrypt correctly after relaunch.
3. `Flow<AppSettings>` emits the updated value within one frame of any setter being called.
4. Log buffer never exceeds 500 entries under stress (test inserts 10 000 entries, asserts size == 500 and FIFO order).
5. Unit tests pass; coverage of the three repositories ≥ 80 %.

## Out of scope

- Domain use cases (Phase 3).
- Any UI consuming these flows (Phase 6+).
- Cert key storage — that's Phase 4 because it lives with the FTP engine config.

## Risks / decisions

- **DataStore Preferences vs Proto** — Preferences is sufficient; revisit only if schema balloons.
- **EncryptedSharedPreferences deprecation** — Android `androidx.security:security-crypto-ktx:1.1.0-alpha` is the latest. Acceptable risk; document in a code comment that we'll migrate if Google ships a replacement.
- **Tree URI persistence** — must call `contentResolver.takePersistableUriPermission` immediately on selection (called from Phase 8 UI, but the repository must expose the helper).
