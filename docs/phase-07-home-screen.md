# Phase 7 — Home Screen: Status, QR, Connection Info

## Goal

The headline screen: clear server status, prominent start/stop, connection details when running, QR code, copy-to-clipboard, live client count, and the anonymous-mode warning banner. Animated transitions between OFF and ON states.

## Deliverables

### ViewModel
- `ui/home/HomeViewModel : ViewModel`, `@HiltViewModel`.
- Injected use cases: `ObserveServerStateUseCase`, `ObserveConnectionInfoUseCase`, `ObserveConnectedClientsUseCase`, `GetAppSettingsUseCase`, `StartServerViaServiceUseCase`, `StopServerViaServiceUseCase`.
- Combined `StateFlow<HomeUiState>`:
  ```
  data class HomeUiState(
      val serverState: ServerState,
      val connectionInfo: ConnectionInfo?,
      val connectedClients: Int,
      val isAnonymous: Boolean,
      val isPasswordRevealed: Boolean,
      val transientError: String? // consumed via Snackbar
  )
  ```
- Events: `onStartClicked`, `onStopClicked`, `onCopyAddress`, `onTogglePasswordVisibility`, `onQrTapped`, `onErrorShown`.
- Start/stop transitions reflected in `HomeUiState` via `serverState` (`Starting`/`Stopping` show progress overlay).

### Composable structure
- `HomeScreen(viewModel)` — collects state with `collectAsStateWithLifecycle`, hosts a `Crossfade` between `ServerOffContent` and `ServerOnContent`. `Starting`/`Stopping` show a centered `LoadingIndicator` overlay with a subtle scrim.

#### `ServerOffContent`
- `StatusCard(title = "Server is stopped", subtitle = "Tap Start to begin", icon = idle illustration, accentColor = secondary)`.
- Below: a config summary card (port · auth mode · root dir) populated from `AppSettings`.
- Bottom: large `Button(onClick = onStartClicked)` labeled "Start server" (FAB-style — `ExtendedFloatingActionButton` anchored to the Scaffold's FAB slot, OR a filled button centered — pick one and stay consistent; recommend FAB).

#### `ServerOnContent`
- `StatusCard(title = "Server is running", subtitle = "<n> connected", icon = pulsing dot, accentColor = primary)`.
  - Pulsing dot: `infiniteTransition.animateFloat(alpha 0.4f → 1f)` on a small filled circle.
- Connection details card:
  - URL line: large monospace text + trailing `IconButton(Icons.ContentCopy, onClick = onCopyAddress)`.
  - Username row (only if not anonymous).
  - Password row (only if not anonymous): masked by default (`••••••••`), trailing eye icon toggles reveal.
  - QR card: `QrCodeImage(url)` 200dp square — tapping opens `QrFullscreenDialog`.
- Connected clients chip: small badge with count + label.
- Anonymous warning banner: `WarningBanner("Anonymous mode is enabled — anyone on this network can access your files.")` when `isAnonymous`.
- Bottom: large `Button(onClick = onStopClicked)` "Stop server" — destructive tone (`ButtonDefaults.filledTonalButtonColors` in error container) — confirm with a brief `AlertDialog` only if a client is currently connected.

### QR generation
- `ui/components/QrCodeImage.kt` — composable wrapping ZXing `QRCodeWriter`:
  - Generates a `Bitmap` off the main thread via `produceState` + `Dispatchers.Default`, cached by input string.
  - Margin = 1 module; ECC level Q.
  - Background = `MaterialTheme.colorScheme.surface`, foreground = `onSurface`.
- `QrFullscreenDialog(url)` — `Dialog` with a centered enlarged QR (min 80% of screen width) + the URL underneath + a close button.

### Clipboard + Snackbar
- Copying the address triggers a Snackbar "Address copied" via the host in `MainScreen`.
- Errors from start (e.g., no Wi-Fi) trigger a Snackbar "No Wi-Fi connection" and leave server stopped.

### Permissions guard
- If the user tries to start the server but `MANAGE_EXTERNAL_STORAGE` is not granted, show an inline card explaining the permission with a "Grant permission" button that opens the system settings intent. (The onboarding flow in Phase 9 normally handles this, but the runtime check is here too because the user may revoke it.)

### Tests
- `HomeViewModelTest` — verifies state combinations: stopped → started → connection info populated → client count updates → stopped.
- Compose UI test: with a fake VM emitting `Stopped`, asserts "Start server" button visible. With `Running` + non-anonymous, asserts URL, masked password, and copy button visible. Tapping eye reveals password.
- QR generator unit test — input string round-trips through a ZXing reader.

## Acceptance criteria

1. Cold launch → Home shows correct OFF state in < 1 s after splash.
2. Tap Start with Wi-Fi connected: state transitions OFF → Starting overlay → ON with URL populated within 3 s typical.
3. URL shown matches `ipconfig` / `ip addr` on the device.
4. QR scanned by a standard QR reader app yields the same URL string.
5. Copy button places the URL on the clipboard (verified via `ClipboardManager`).
6. Stop with active clients shows a confirm dialog; stop with none does not.
7. Anonymous mode shows the warning banner whenever the server is on or off (the banner reflects the *setting*, not the live state).

## Out of scope

- Settings UI (Phase 8).
- Logs screen (Phase 8).
- Widget (Phase 10).

## Risks / decisions

- **Pulsing animation cost** — keep on a single small Composable; do not animate the whole card.
- **Password reveal accessibility** — content description must change between "Show password" and "Hide password".
- **QR rendering on tiny screens** — minimum size 160dp; if screen smaller, scroll the card.
