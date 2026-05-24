# Phase 4 — FTP Server Engine (FTP + FTPS)

## Goal

Implement the real `FtpServerController` using embedded Apache FTPServer. Support both plain FTP and FTPS (Explicit TLS) with a self-signed certificate generated on first launch. Bridge FTPServer's internal events into the `ServerEvent` SharedFlow and maintain a live connected-client count.

## Deliverables

### Apache FTPServer integration (`data/server/`)
- Dependency: `org.apache.ftpserver:ftpserver-core` (latest 1.x).
- `FtpServerControllerImpl` — implements `FtpServerController`.
  - Lifecycle owns a single `FtpServer` instance.
  - `start(config)` builds a `FtpServerFactory`:
    - `ListenerFactory` with port from config.
    - `DataConnectionConfigurationFactory` for PASV port range + passive external address = current Wi-Fi IP.
    - SSL configuration if `protocol == FTPS` (Explicit TLS via `AUTH TLS`).
    - Listener bound to the device's current Wi-Fi IPv4 (never `0.0.0.0`).
    - `UserManager` populated with the appropriate user (see below).
    - Custom `Ftplet` registered to bridge events.
  - `stop()` performs graceful suspend + stop: `server.suspend()` → wait for in-flight transfers up to a 5 s deadline → `server.stop()`.
  - State machine: emit `Starting` before bind, `Running(...)` after listener active, `Stopping` during shutdown, `Stopped` after, `Error(msg)` on failure (with retained config for retry).
  - `connectedClientCount` is a `MutableStateFlow<Int>` updated on Ftplet connect/disconnect callbacks.

### User management
- `data/server/AppUserManager` — implements `org.apache.ftpserver.ftplet.UserManager`.
  - When `AuthMode.ANONYMOUS`: returns an "anonymous" user with no password, home = root dir, write permission per `AccessMode`.
  - When `AuthMode.SINGLE_USER`: returns the configured username; authentication via `UsernamePasswordAuthentication` comparing against `CredentialsRepository` (constant-time string compare).
  - Emits `AuthFailure(ip, username)` on failed auth.

### Permissions per access mode
- `READ_WRITE` → `WritePermission` + read.
- `READ_ONLY` → no `WritePermission`; STOR/DELE/RNFR/RNTO/MKD/RMD rejected with 550.

### Event bridging
- `data/server/EventBridgeFtplet : DefaultFtplet` — overrides:
  - `onConnect` → emit `ClientConnected`; increment count; append `LogEntry`.
  - `onDisconnect` → emit `ClientDisconnected`; decrement count; append log.
  - `onUploadEnd` → emit `FileUploaded`.
  - `onDownloadEnd` → emit `FileDownloaded`.
  - Auth failures emitted from `AppUserManager`.
- Events go through an injected `ServerEventBus` (`MutableSharedFlow<ServerEvent>` with `extraBufferCapacity = 64`, `BufferOverflow.DROP_OLDEST`).
- Same bus feeds `LogRepository` via a small collector started inside `FtpServerControllerImpl.start` and cancelled on stop.

### Certificate management (`data/server/cert/`)
- `CertificateManager` — interface defined in domain (Phase 3); implemented here.
  - `suspend fun getOrCreate(): KeyStore` — returns existing keystore or generates a self-signed cert (BouncyCastle) valid 10 years, CN = `FileBridge`, key = RSA 2048 or EC P-256, stored at `filesDir/ftps_keystore.bks` (BKS via BouncyCastle).
  - `suspend fun regenerate()` — deletes existing keystore + key alias, then `getOrCreate()`.
  - `suspend fun info(): CertificateInfo?` — parses subject/issuer/expiry/SHA-256 fingerprint.
- Keystore password — generated once on first run, stored in EncryptedSharedPreferences (`cert_keystore_password`), 32 random bytes hex-encoded.
- `SslConfigurationFactory` consumes this keystore for FTPS.

### Wi-Fi IP resolution
- `data/network/WifiNetworkInfoProvider` implements `NetworkInfoProvider` from Phase 3.
  - Uses `ConnectivityManager.getLinkProperties(activeNetwork)` filtered to IPv4 non-loopback.
  - Emits `ConnectivityStatus` via `ConnectivityManager.NetworkCallback` wrapped in `callbackFlow`.

### Wake lock
- `FtpServerControllerImpl` acquires a partial `WakeLock` tagged `FileBridge::FtpServer` while running; releases on stop. (Screen-on lock is handled by the Activity in Phase 7 when the setting is enabled.)

### Hilt wiring
- `di/ServerModule.kt`:
  - Binds `FtpServerController` → `FtpServerControllerImpl` as `@Singleton`.
  - Binds `CertificateManager` → `CertificateManagerImpl`.
  - Binds `NetworkInfoProvider` → `WifiNetworkInfoProvider`.
  - Provides `ServerEventBus`.

### Tests
- `FtpServerControllerImplTest` (instrumented):
  - Start in plain FTP, connect with Apache Commons Net `FTPClient` over loopback (using port 0 / dynamic port + override `passiveExternalAddress = 127.0.0.1`), list root, upload, download, disconnect — assert `events` and `connectedClientCount` reflect each step.
  - Start in FTPS, connect with `FTPSClient` (explicit), trust the self-signed cert via custom `TrustManager` reading our keystore.
  - Auth failure path emits `AuthFailure`.
  - Read-only mode rejects STOR.
- `CertificateManagerImplTest`:
  - `getOrCreate` is idempotent.
  - `regenerate` changes the fingerprint.
  - Cert validity period = 10 years.

## Acceptance criteria

1. From another machine on the LAN, `ftp 192.168.x.x 2121` connects and lists the configured root in both anonymous and single-user modes.
2. FTPS explicit (e.g., FileZilla "Require explicit FTP over TLS") connects after accepting the self-signed cert.
3. Stopping the server while a transfer is in progress completes the transfer (up to 5 s) before unbinding.
4. `events` flow emits within 100 ms of the underlying FTPServer callback.
5. `connectedClientCount` matches `netstat` observation.
6. No leaks: starting and stopping 100 times in a row leaves heap stable (manual profiler check; not gated in CI).
7. Server never binds to `0.0.0.0`; binding to a non-Wi-Fi network is rejected with a clear `ServerState.Error`.

## Out of scope

- Foreground Service (Phase 5).
- Any UI invoking start/stop (Phase 7).
- Multi-user accounts (out of v1).

## Risks / decisions

- **Apache FTPServer maturity** — last release is old; acceptable for v1. Vendor patches only if a blocking bug surfaces.
- **BouncyCastle on Android** — use `bcprov-jdk18on` and register a provider lazily; avoid replacing the platform provider.
- **Keystore password persistence** — stored in EncryptedSharedPreferences keyed by Android Keystore master key. Acceptable for v1.
- **PASV external address** — when device IP changes mid-session, existing sessions break. Document; do not attempt seamless rebind in v1.
- **Foreground service type** — Phase 5 declares `dataSync`; engine doesn't care, but cert + listener startup must complete within the FGS startup window (10 s) — measured in tests.
