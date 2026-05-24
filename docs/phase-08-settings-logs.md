# Phase 8 — Settings & Logs Screens

## Goal

The two configuration/observability tabs. Settings exposes every persisted preference with inline validation. Logs renders the in-memory activity stream with color-coded entries and a clear-with-confirm action.

## Deliverables

### Settings ViewModel
- `ui/settings/SettingsViewModel : ViewModel`, `@HiltViewModel`.
- Injected: `GetAppSettingsUseCase`, `UpdateAppSettingUseCase`, `SetCredentialsUseCase`, `ListStorageRootsUseCase`, `IsSdCardPresentUseCase`, `GetCertificateInfoUseCase`, `RegenerateCertificateUseCase`, `ObserveServerStateUseCase`.
- State:
  ```
  data class SettingsUiState(
      val settings: AppSettings,
      val passwordPlaintext: String, // held in VM only while editing
      val isSdCardAvailable: Boolean,
      val certInfo: CertificateInfo?,
      val serverRunning: Boolean,
      val validationErrors: Map<Field, Int /* strRes */>
  )
  ```
- Validation runs on every edit via `SettingsValidator`; invalid edits don't persist (the field stays "pending" until valid).
- Some settings are **disabled while the server is running** (port, pasv range, protocol, root dir, access mode, auth mode, username, password). Show an inline hint "Stop the server to change this setting."

### Settings screen layout (`ui/settings/SettingsScreen.kt`)
LazyColumn with sectioned items per the spec:

**Server**
- Protocol — `SegmentedButtonRow` (FTP / FTPS). Selecting FTPS but cert not yet generated triggers a small "Generating certificate…" state (no blocking dialog; the regen / first-create call is suspended in VM).
- Port — `PortInputField`, validated 1024–65535.
- Passive port range — two `PortInputField`s on the same row labeled "Min" / "Max"; validate non-overlap with FTP port and min < max.

**Authentication**
- Mode — `SegmentedButtonRow` (Anonymous / Single User).
- Username — `OutlinedTextField` (visible only when Single User), non-empty validation.
- Password — `OutlinedTextField` with show/hide trailing icon, non-empty validation. Saved via `SetCredentialsUseCase` when valid; never logged.

**Storage**
- Root directory — list item showing current path; tap → opens `ACTION_OPEN_DOCUMENT_TREE`. On selection, persist tree URI permission and save via setting.
- "Use internal storage root" — secondary action.
- "Use SD card" — secondary action, visible only when `isSdCardAvailable`.
- Access mode — `LabeledSwitch` "Read only" (off = Read & Write).

**Behavior**
- `LabeledSwitch` "Start server on app launch".
- `LabeledSwitch` "Auto-start on device boot" — toggling on shows a brief explanation that this still requires the device to be unlocked once on Android 10+ for storage access.
- `LabeledSwitch` "Keep screen on while server is running".
- Informational row (non-interactive): "Persistent notification is shown whenever the server is running."

**Security**
- "FTPS certificate" — list item showing fingerprint + expiry from `certInfo`; tap → dialog with full subject/issuer/expiry/fingerprint.
- "Regenerate certificate" — list item; tap → confirm dialog; on confirm, call `RegenerateCertificateUseCase`. If server is currently running with FTPS, refuse and prompt to stop first.

**Appearance**
- Theme — `SegmentedButtonRow` (System / Light / Dark).

**About**
- App version (from `BuildConfig.VERSION_NAME`).
- "Open-source licenses" — opens a screen rendering Gradle-generated license JSON (`com.mikepenz:aboutlibraries-compose` is acceptable; otherwise hand-roll a small list). The license screen lives under `ui/about/`.
- "GitHub repository" — opens browser to URL stored in a `BuildConfig` field.
- "Help translate" — opens browser to the translation project URL (Crowdin/Weblate — decided in Phase 10; placeholder URL acceptable here).

### Logs ViewModel
- `ui/logs/LogsViewModel : ViewModel`, `@HiltViewModel`.
- Injected: `ObserveLogsUseCase`, `ClearLogsUseCase`.
- State: `StateFlow<List<LogEntry>>` + a derived "is empty" boolean.

### Logs screen (`ui/logs/LogsScreen.kt`)
- `TopAppBar` with title "Activity" and a trailing `IconButton` (trash) — tap shows confirm dialog → `ClearLogsUseCase`.
- Empty state: `EmptyState(icon = list, title = "No activity yet", body = "Logs appear here once the server starts.")`.
- Non-empty: reverse-chronological `LazyColumn` of `LogRow(entry)`:
  - Leading dot or chip colored per `LogType`:
    - Connect / Server started → `tertiary` (greenish from theme).
    - Disconnect / Server stopped → `outline` (muted).
    - File uploaded / downloaded → `primary`.
    - Auth failure → `error`.
  - Timestamp (HH:mm:ss) + entry text + optional IP suffix.
- Pull-to-refresh not required (live updates flow in).

### Tests
- `SettingsViewModelTest` — every section's setters propagate, invalid inputs don't persist, settings are locked when server is running.
- `LogsViewModelTest` — empty state, append shows up, clear empties.
- Compose tests for both screens covering the major branches (server running locks, anonymous hides username/password, SD card visibility).

## Acceptance criteria

1. Editing any setting persists across app restart.
2. Entering an invalid port shows inline error and does not save.
3. Switching protocol to FTPS while cert is not yet generated does not block the UI for more than ~1.5 s (cert generation runs in a background dispatcher).
4. Choosing a directory via the system picker grants persistent access; the FTP server (Phase 4) can then read/write within that tree URI on next start.
5. Tapping "Regenerate certificate" twice changes the displayed fingerprint each time.
6. Logs screen receives a new entry within 200 ms of a client connecting (live observation).
7. Clearing logs empties the list and updates immediately.

## Out of scope

- Onboarding-driven first-time setup (Phase 9).
- Widget settings (Phase 10).
- Translation files for any locale beyond English (Phase 10).

## Risks / decisions

- **DocumentFile vs java.io.File for tree URIs** — the FTP engine in Phase 4 currently uses `java.io.File`. If the user selects a tree URI not backed by a path resolvable to a `File` (rare on Android 11+ for primary storage but possible for SD card), we must either:
  - (a) Restrict the picker to internal storage (loses SD card feature), or
  - (b) Add a `DocumentFile` filesystem view to Apache FTPServer.
  Decision deferred to implementation: prefer (b) using a custom `FileSystemView`. Document in code if (a) is taken as a temporary workaround.
- **AboutLibraries vs custom** — AboutLibraries is well-maintained; acceptable dependency.
- **Snackbar reach** — Settings screen needs the same Snackbar host as Home; route through `MainScreen`'s host via a shared event channel or use compose-local.
