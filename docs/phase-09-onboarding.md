# Phase 9 — Onboarding, Permissions & First-Run UX

## Goal

A three-step onboarding pager shown on first launch. Explains the app, requests `MANAGE_EXTERNAL_STORAGE` with rationale, and collects initial auth + root directory choices. Sets `onboardingComplete = true` only when the user has either granted the permission and chosen credentials/root, or explicitly continued in a degraded state.

## Deliverables

### Onboarding ViewModel
- `ui/onboarding/OnboardingViewModel : ViewModel`, `@HiltViewModel`.
- Injected: `GetAppSettingsUseCase`, `SetCredentialsUseCase`, `UpdateAppSettingUseCase`, `CompleteOnboardingUseCase`, `ListStorageRootsUseCase`.
- State:
  ```
  data class OnboardingUiState(
      val currentStep: Int,            // 0..2
      val storagePermissionGranted: Boolean,
      val notificationPermissionGranted: Boolean, // Android 13+
      val authMode: AuthMode,
      val username: String,
      val password: String,
      val rootDirUri: String?,
      val canFinish: Boolean
  )
  ```
- Events: `onNext`, `onBack`, `onAuthModeChanged`, `onUsernameChanged`, `onPasswordChanged`, `onRootDirSelected(uri)`, `onPermissionResult(granted)`, `onFinish`.

### Pager screen (`ui/onboarding/OnboardingScreen.kt`)
- `HorizontalPager` with 3 pages, page indicator dots at the bottom, "Back" + "Next" / "Finish" buttons.
- Swiping forward past an incomplete step is disabled by setting `userScrollEnabled = false`; navigation is via buttons only.

#### Step 1 — Welcome
- App icon + "FileBridge".
- Tagline: "Turn your phone into a Wi-Fi file server."
- Three bullet "what you can do" lines: browse files, transfer to PC, secure with FTPS.
- "Next" advances.

#### Step 2 — Permission
- Title: "We need filesystem access".
- Body text explaining that `MANAGE_EXTERNAL_STORAGE` is required to share files outside the app's sandbox, and that no data leaves the device.
- Primary button "Grant permission":
  - On Android 11+: launches `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` for this package. Result observed via lifecycle resume + re-check `Environment.isExternalStorageManager()`.
  - On Android 10 and below: requests `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE` via the standard runtime permission API (still declared in manifest behind appropriate `maxSdkVersion`).
- Secondary text link "Why this permission?" → expands inline explanation.
- (Android 13+) After storage is granted (or on any path forward), request `POST_NOTIFICATIONS` via standard runtime permission. If denied, show a tooltip explaining the server will still run but no notification will appear.
- "Next" enabled only after the storage permission step has resolved (granted or explicitly skipped via a "Continue without granting" link — the app then enters a degraded state where the server cannot be started until the user grants the permission from Settings).

#### Step 3 — Quick setup
- Auth mode segmented control (Anonymous / Single User), default Single User.
- Username field (prefilled "ftpuser"), password field (with show/hide).
- "Choose root directory" button:
  - Default: "Internal storage" (set automatically).
  - User may tap and pick a directory via `ACTION_OPEN_DOCUMENT_TREE`.
- "Finish" calls `CompleteOnboardingUseCase` which:
  - Saves auth mode + credentials.
  - Saves root dir URI.
  - Flips `onboardingComplete = true`.
- Navigation pops onboarding off the back stack; lands on `Home`.

### Navigation integration
- Removed-only-once: once `onboardingComplete == true`, the route is unreachable. Direct deep links into `onboarding` are ignored.

### Permissions plumbing
- `ui/onboarding/PermissionHelpers.kt`:
  - `rememberStoragePermissionState()` Composable hook returning current grant status, a `request()` lambda, and reacts to lifecycle resume.
  - `rememberNotificationPermissionState()` similar.
- These are also reused by Phase 7's Home runtime guard.

### Tests
- `OnboardingViewModelTest` — flow through all three steps, finish enabled only when valid.
- Instrumented test: starting with `onboardingComplete = false`, walk pages, mock permission grant, finish, verify `onboardingComplete = true` and navigation lands on Home.

## Acceptance criteria

1. First launch lands on Onboarding step 1.
2. Step 2 "Grant permission" opens the correct system screen on Android 11+ and the runtime dialog on Android 10−.
3. Returning from the system permission screen reflects the new grant status without manually refreshing.
4. Finishing onboarding never crashes if the user skipped storage permission — they see a banner on Home explaining the server cannot start until permission is granted.
5. Subsequent launches skip onboarding entirely.
6. Onboarding strings are all in `strings.xml`.

## Out of scope

- Localization beyond English (Phase 10).
- Re-running onboarding from Settings (not a v1 feature).
- Battery optimization prompts.

## Risks / decisions

- **MANAGE_EXTERNAL_STORAGE & Play policy** — Google requires a Play Console justification. Document this in the release checklist (Phase 10); ensure the in-app rationale matches what's submitted to Play.
- **Skipped permission UX** — must not leave the app in a confusing state. Home should show a clear actionable banner whenever permission is missing.
- **POST_NOTIFICATIONS** — denial is recoverable (user can grant from system settings); we don't insist.
