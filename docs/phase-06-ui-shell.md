# Phase 6 — UI Shell: Theme, Navigation, Scaffolding

## Goal

Build the visual + navigational chassis that the Home / Logs / Settings / Onboarding screens slot into. Material 3 theme, dynamic color, bottom navigation, and stub destinations for each tab.

## Deliverables

### Theme (`ui/theme/`)
- `Color.kt` — fallback `lightColorScheme` and `darkColorScheme` palettes. Primary = a calm blue/teal. Defined via Material 3 tokens.
- `Type.kt` — Material 3 `Typography` using system default font; only overrides where necessary.
- `Shape.kt` — Material 3 `Shapes` with default values (subtle override of `extraLarge` for status cards if desired).
- `Theme.kt` — `FileBridgeTheme(themeMode, dynamicColor, content)`:
  - On Android 12+ and `dynamicColor == true`, use `dynamicLightColorScheme` / `dynamicDarkColorScheme`.
  - Else fallback palette.
  - Reads `themeMode` from `AppSettings` (passed in by `MainActivity`).
  - Updates `WindowCompat.setDecorFitsSystemWindows = false`; applies status/navigation bar colors via `Modifier.systemBarsPadding` in `Scaffold`.

### Navigation (`ui/navigation/`)
- `Destination.kt` — sealed class:
  - `Onboarding` (route `"onboarding"`, no bottom bar).
  - `Home` (route `"home"`, label, icon).
  - `Logs` (route `"logs"`, label, icon).
  - `Settings` (route `"settings"`, label, icon).
- `NavGraph.kt` — `NavHost` with the four routes. Each tab destination wired to a placeholder composable for now (`Box { Text(label) }`); real screens land in phases 7–9.
- `FileBridgeBottomBar` — `NavigationBar` with three items, hidden when route == `Onboarding`.
- Start destination decided by `IsOnboardingCompleteUseCase`:
  - `false` → `Onboarding`.
  - `true` → `Home`.

### Root scaffold
- `ui/MainScreen.kt` — `Scaffold` with `bottomBar = { FileBridgeBottomBar(...) }`, `snackbarHost`, content area hosting `NavHost`.
- `MainActivity` (updated from Phase 1):
  - `setContent { FileBridgeTheme(themeMode = …) { MainScreen() } }`.
  - Collects `AppSettings` via Hilt-injected `ObserveAppSettingsUseCase` to pick `themeMode`.
  - `enableEdgeToEdge()`.
  - Handles "keep screen on" by toggling `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` based on server state + setting (small effect inside `MainScreen`).

### Common UI components (`ui/components/`)
- `SectionHeader(title)` — used by Settings.
- `StatusCard(title, subtitle, icon, accentColor)` — used by Home.
- `LoadingIndicator()` — wrapper around `CircularProgressIndicator`.
- `EmptyState(icon, title, body)` — used by Logs.
- `WarningBanner(message, actionLabel?, onAction?)` — used for anonymous-mode warning.
- `LabeledSwitch(title, subtitle?, checked, onCheckedChange)` — used heavily in Settings.
- `PortInputField(value, onValueChange, isError, errorText)` — numeric, IME `Done`.

Each gets a `@Preview` with both light and dark themes.

### Resources
- Vector drawables for: tab icons (home/wifi, list, settings), QR icon, copy icon, eye/eye-off, refresh, sd-card, folder, alert-triangle.
- App launcher icon — vector adaptive icon with simple monogram (real artwork can come later but must look intentional, not the default green Android).

### Tests
- `NavGraphTest` (instrumented) — starting with `onboardingComplete = false` lands on Onboarding; setting it to true lands on Home; tab clicks switch destinations.
- Component previews compile.

## Acceptance criteria

1. App launches into either Onboarding or Home depending on the flag.
2. Bottom nav is hidden during Onboarding and visible on the three tabs.
3. Dynamic color works on Android 12+ (manually verified by changing wallpaper).
4. Switching `themeMode` in `AppSettings` (via repository directly in a debug screen or test) immediately re-themes the UI.
5. Edge-to-edge: status bar and navigation bar are transparent with appropriate scrim handling.
6. All component previews render in both light and dark.

## Out of scope

- Real Home/Logs/Settings/Onboarding content (later phases).
- Snackbar event plumbing from ViewModels (introduced as needed in phases 7–9).

## Risks / decisions

- **Dynamic color fallback** — must look polished on Android 8–11 too; pick the fallback palette carefully.
- **System bar contrast** — on light theme with light wallpaper-derived dynamic colors, ensure status bar icons remain legible; use `WindowInsetsControllerCompat.isAppearanceLightStatusBars`.
- **No `androidx.navigation.compose` lock-in concerns** — acceptable for v1.
