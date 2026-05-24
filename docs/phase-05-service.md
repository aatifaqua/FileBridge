# Phase 5 — Foreground Service & Notification

## Goal

Wrap `FtpServerController` in an Android Foreground Service so the server survives the UI being backgrounded. Surface server state via a sticky notification with a Stop action. After this phase, the FTP engine is reachable from anywhere in the app via Service intents.

## Deliverables

### Service implementation
- `service/FtpForegroundService : LifecycleService`, annotated `@AndroidEntryPoint`.
- `serviceType = FOREGROUND_SERVICE_TYPE_DATA_SYNC` (declared in manifest).
- Injected: `StartServerUseCase`, `StopServerUseCase`, `ObserveServerStateUseCase`, `ObserveConnectionInfoUseCase`, `NotificationFactory`.
- `onStartCommand`:
  - `ACTION_START` → call `startServerUseCase`; `startForeground(NOTIF_ID, notificationFactory.starting())` immediately to satisfy FGS contract; then observe state and update the notification.
  - `ACTION_STOP` → call `stopServerUseCase`; on `Stopped`, `stopSelf()` + remove notification.
  - Re-delivery: `START_NOT_STICKY` (we want explicit user start, not zombie revival).
- `onDestroy`: cancel its `lifecycleScope`, ensure server is stopped, release wake lock.

### Service binding (for UI live data)
- A small `ServiceLauncher` (singleton) in `service/` exposes:
  - `fun start(context: Context)`
  - `fun stop(context: Context)`
  - These send the appropriate `Intent` to the service via `ContextCompat.startForegroundService`.
- UI does **not** bind to the Service. It observes `FtpServerController.state` directly via use cases (the controller is `@Singleton` and shared via Hilt). The Service merely hosts the lifecycle.

### Notification
- `service/notification/NotificationFactory.kt`:
  - Channel `server_status`, importance `IMPORTANCE_LOW`, no sound/vibration.
  - `starting()` notification: indeterminate progress, title "Starting FTP server…".
  - `running(connectionInfo)` notification:
    - Title: string resource "FTP server running".
    - Body: `ftp://192.168.x.x:2121` (or `ftps://…`).
    - Action: "Stop" → `PendingIntent` to service with `ACTION_STOP`.
    - Content intent: opens `MainActivity` on Home tab.
    - Ongoing + non-dismissible while running.
  - `error(message)` notification: dismissible.
- All strings in `strings.xml`.

### Channel registration
- `FileBridgeApp.onCreate` (Phase 1 file, updated here) registers the channel on Android 8+.

### Manifest updates
- `<service android:name=".service.FtpForegroundService" android:foregroundServiceType="dataSync" android:exported="false" />`.
- Intent action constants defined in a companion object on the Service.

### Use case additions
- `domain/usecase/StartServerViaServiceUseCase` (optional thin wrapper) — UI calls this rather than knowing about Intents. Internally calls `ServiceLauncher.start(context)`. Same for stop.

### Tests
- Instrumented test: send `ACTION_START`, assert `FtpServerController.state` transitions to `Running` within 5 s and a notification is posted with the correct content text. Send `ACTION_STOP`, assert state returns to `Stopped` and notification is cancelled.
- Verify the service is killable by the system without crashing: simulate `onTaskRemoved` (swiped from recents) — server should keep running.

## Acceptance criteria

1. Starting the server from a test fixture posts a foreground notification within 1 s.
2. Tapping "Stop" in the notification stops the server and removes the notification.
3. Tapping the notification body opens `MainActivity`.
4. Server keeps running when the app is swiped from recents.
5. Server stops cleanly when the OS kills the service (test by `adb shell am stopservice`).
6. No `ForegroundServiceDidNotStartInTimeException` even on cold cert generation (covered by Phase 4 timing tests; revisit if encountered).

## Out of scope

- Widget triggers (Phase 10).
- Boot auto-start (Phase 10).
- UI screens consuming the connection info (Phase 7).

## Risks / decisions

- **Android 14 FGS type enforcement** — `dataSync` is appropriate for ongoing user-initiated data transfer. Document this rationale in code; if rejected on Play, consider `specialUse` with a justification.
- **Notification permission (Android 13+)** — request `POST_NOTIFICATIONS` is handled in Phase 9 onboarding. Service still starts without it; just the notification UI is suppressed by the OS. Server still runs.
- **Doze / battery optimization** — partial wake lock acquired in Phase 4 keeps CPU; we do NOT prompt the user to disable battery optimization in v1. Document as a known limitation.
