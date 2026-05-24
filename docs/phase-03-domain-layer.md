# Phase 3 — Domain Layer: Models & Use Cases

## Goal

Define every domain model and use case the UI and Service layers will invoke. After this phase, ViewModels in later phases only ever call `UseCase.invoke(...)` — never repositories directly.

## Deliverables

### Domain models (`domain/model/`)
- `AppSettings` (moved here from Phase 2 if not already).
- `ServerConfig` — derived view used by the FTP engine: protocol, port, pasvRange, authMode, username, password (nullable), rootDir, accessMode.
- `ServerState` — sealed class: `Stopped`, `Starting`, `Running(address, port, protocol, connectedClients)`, `Stopping`, `Error(message)`.
- `ConnectionInfo` — `url: String`, `username: String?`, `password: String?`, `protocol: Protocol`.
- `Protocol` — enum `FTP`, `FTPS`.
- `AuthMode` — enum `ANONYMOUS`, `SINGLE_USER`.
- `AccessMode` — enum `READ_WRITE`, `READ_ONLY`.
- `CertificateInfo` — `subject`, `issuer`, `expiresAt`, `sha256Fingerprint`.

### Server abstraction (`domain/server/`)
- `FtpServerController` — interface used by use cases. Defined here, implemented in Phase 4.
  - `val state: StateFlow<ServerState>`
  - `val events: SharedFlow<ServerEvent>`
  - `suspend fun start(config: ServerConfig)`
  - `suspend fun stop()`
  - `val connectedClientCount: StateFlow<Int>`
- `ServerEvent` — sealed: `ClientConnected(ip)`, `ClientDisconnected(ip)`, `FileUploaded(ip, path)`, `FileDownloaded(ip, path)`, `AuthFailure(ip, username)`.

### Network info (`domain/network/`)
- `NetworkInfoProvider` — interface: `fun currentWifiIpAddress(): String?`, `val connectivity: Flow<ConnectivityStatus>`.
- `ConnectivityStatus` — enum `WIFI_CONNECTED`, `NOT_WIFI`, `DISCONNECTED`.
- Implementation lives in `data/network/` and is provided by `DataModule` (small addendum to Phase 2 module).

### Use cases (`domain/usecase/`)

All use cases are `class … @Inject constructor(...)`, expose a single `operator fun invoke(...)`.

- `StartServerUseCase` — builds `ServerConfig` from `SettingsRepository` + `CredentialsRepository`, validates (port range, pasv range non-overlapping with ftp port, username/password non-empty if SINGLE_USER), calls `FtpServerController.start`. Returns `Result<Unit>`.
- `StopServerUseCase` — calls `controller.stop()`. Returns `Result<Unit>`.
- `ObserveServerStateUseCase` — returns `StateFlow<ServerState>`.
- `ObserveConnectionInfoUseCase` — combines server state + settings + credentials + network IP into `Flow<ConnectionInfo?>` (null when stopped).
- `ObserveConnectedClientsUseCase` — `StateFlow<Int>`.
- `GetAppSettingsUseCase` — `Flow<AppSettings>`.
- `UpdateAppSettingUseCase` — typed wrapper around the setting setters; one `invoke` overload per field, OR a `SettingPatch` sealed class. Pick the sealed class approach for testability.
- `SetCredentialsUseCase` — `(username, password) -> Result<Unit>`; clears when anonymous.
- `ObserveLogsUseCase` / `ClearLogsUseCase` — wrap `LogRepository`.
- `RegenerateCertificateUseCase` — calls into the cert manager (Phase 4) to regenerate the self-signed cert; deletes old key.
- `GetCertificateInfoUseCase` — returns `CertificateInfo?` from the cert manager.
- `ListStorageRootsUseCase` / `IsSdCardPresentUseCase` — wrap `StorageRepository`.
- `CompleteOnboardingUseCase` — flips `onboardingComplete` to true; takes initial credentials + root dir.
- `IsOnboardingCompleteUseCase` — `Flow<Boolean>`.

### Validation
- `domain/validation/SettingsValidator.kt` — pure functions:
  - `validatePort(value: Int): ValidationResult`
  - `validatePasvRange(min: Int, max: Int, ftpPort: Int): ValidationResult`
  - `validateUsername(value: String): ValidationResult`
  - `validatePassword(value: String): ValidationResult`
- `ValidationResult` — sealed: `Valid`, `Invalid(reasonStringRes: Int)`.

### Hilt wiring
- `di/DomainModule.kt` — provides any non-`@Inject` constructable items; mostly empty since use cases are `@Inject constructor`.

### Tests
- One test class per use case verifying:
  - Happy path delegates correctly to repos / controller (with MockK).
  - Validation rejects bad inputs (e.g., port 80, password empty in SINGLE_USER).
  - `ObserveConnectionInfoUseCase` correctly returns null when stopped, populated when running.
- `SettingsValidatorTest` covering all branches.
- Turbine-based tests for any flow combinators.

## Acceptance criteria

1. Every use case has a unit test (≥ 90 % line coverage on `domain/`).
2. Validator rejects: port < 1024 or > 65535, pasv max ≤ pasv min, ftpPort within pasv range, empty username/password when SINGLE_USER, whitespace-only username.
3. No domain class imports from `android.*` (except `androidx.annotation.StringRes` for validation reasons).
4. All flows survive configuration change semantics — use cases return `StateFlow` where state is read repeatedly.

## Out of scope

- Real `FtpServerController` implementation (Phase 4).
- ViewModels (Phase 7/8).
- Notification text formatting (Phase 5).

## Risks / decisions

- **Use case granularity** — one-per-action is preferred over a god-`ServerInteractor`, even if it inflates file count. Better testability, easier code review.
- **Result vs exceptions** — use `Result<Unit>` from use cases that can fail at the boundary; sealed `ValidationResult` for input checks (no exceptions for control flow).
- **Cert use cases couple domain to a Phase 4 interface** — define the interface (`CertificateManager`) here in domain, implement in data/server later.
