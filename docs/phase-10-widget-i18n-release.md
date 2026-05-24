# Phase 10 — Widget, Boot Auto-Start, i18n, CI & Release

## Goal

Ship-readiness: home-screen widget, boot receiver for auto-start, internationalization scaffolding, full CI, release build configuration, and Play Store / GitHub artifacts. After this phase, the app is ready to publish.

## Deliverables

### Glance widget (`widget/`)
- `FtpServerWidget : GlanceAppWidget`:
  - Layout: a `Column` with the app icon + "FTP Server" label, a `Switch`-style toggle row, and a status text line.
  - When server running: status line shows `IP:PORT`; toggle is "on" — tapping calls `StopServerViaServiceUseCase`.
  - When server stopped: status line "Tap to start"; toggle "off" — tapping calls `StartServerViaServiceUseCase` (which requires permission; if missing, opens the app to Home with the permission banner).
  - Background uses `GlanceTheme` colors; respects light/dark.
- `FtpServerWidgetReceiver : GlanceAppWidgetReceiver` registered in manifest.
- Widget state refresh:
  - `FtpForegroundService` calls `FtpServerWidget.update(context, glanceId)` on every `ServerState` transition.
  - On boot / app start, a one-shot sync ensures widget matches actual state.
- `res/xml/ftp_server_widget_info.xml` declares 2×1 minimum resize, preview image, configure activity none.

### Boot receiver (`receiver/`)
- `BootCompletedReceiver : BroadcastReceiver` listening for `ACTION_BOOT_COMPLETED` and `ACTION_LOCKED_BOOT_COMPLETED`.
  - Reads `AppSettings.startOnBoot` synchronously via a small `runBlocking` (acceptable for a short-lived receiver) — if true, starts `FtpForegroundService` with `ACTION_START`.
  - No-op if the setting is off.
- Manifest declares the receiver with `<intent-filter>` and `android:exported="true"` (required for boot receivers) + `android:directBootAware="false"` (we need user data unlocked).

### "Start on app launch" wiring
- `MainActivity.onCreate` checks `AppSettings.startOnAppLaunch`; if true and permission granted and server not already running, sends `ACTION_START` to the service. This is a small addition to the Phase 6 `MainActivity`.

### Internationalization
- Audit every Composable and ViewModel for hardcoded strings — none allowed. Move any stragglers to `strings.xml`.
- Add string formatting placeholders where dynamic values appear (`%1$s`, `%1$d`).
- Provide a `locale_config.xml` listing `en` (and any languages contributed during this phase — likely just `en`).
- `translationHelp.md` at repo root explaining:
  - Where strings live.
  - How to add a new locale folder.
  - How to submit translations (PR or Crowdin/Weblate link).
- **Decision finalized this phase**: Crowdin vs Weblate. Recommend Weblate (self-hostable, libre). Place URL placeholder in `BuildConfig` until project is created.
- RTL test: enable "Force RTL layout direction" developer option, verify no layout breaks on each screen.

### CI / GitHub Actions
- `.github/workflows/ci.yml` (extends Phase 1 stub):
  - On PR: `./gradlew lint testDebugUnitTest assembleDebug`.
  - On push to `main`: same + upload debug APK as artifact.
  - On tagged release `v*`: build signed release APK + AAB (signing config via GitHub Secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `SIGNING_STORE_PASSWORD`), attach to GitHub Release.
- `.github/ISSUE_TEMPLATE/bug_report.md`, `feature_request.md`.
- `.github/PULL_REQUEST_TEMPLATE.md` — checklist for tests, lint, strings, accessibility.
- `CODE_OF_CONDUCT.md` — Contributor Covenant.

### Release build configuration
- `app/build.gradle.kts` signing config reading from env / `keystore.properties` (gitignored).
- ProGuard / R8 keep rules:
  - Apache FTPServer (reflection-heavy) — keep `org.apache.ftpserver.**`.
  - BouncyCastle — keep `org.bouncycastle.**`.
  - Hilt generated.
- `release` build type: `minifyEnabled true`, `shrinkResources true`, `proguard-android-optimize.txt` + `proguard-rules.pro`.
- Smoke test: install release APK, start server, transfer a file, stop. Must work without obfuscation crashes.

### Fastlane metadata (optional but recommended)
- `fastlane/metadata/android/en-US/` with:
  - `title.txt`
  - `short_description.txt`
  - `full_description.txt`
  - `images/phoneScreenshots/` placeholders
  - `changelogs/` directory

### README, CONTRIBUTING
- `README.md` filled out:
  - One-paragraph description.
  - Screenshots (placeholders OK if not captured yet).
  - Features bullet list.
  - Build instructions.
  - "How to contribute" link to `CONTRIBUTING.md`.
  - "Help translate" link to `translationHelp.md`.
  - License badge.
- `CONTRIBUTING.md`:
  - Code style (ktlint).
  - Branching (PRs to `main`).
  - Commit messages (Conventional Commits recommended, not enforced).
  - How to run tests locally.

### Tests
- Boot receiver test (instrumented): broadcast `BOOT_COMPLETED` with `startOnBoot = true`, verify service starts.
- Widget Glance test: stub state flow, verify widget updates content on state transition.
- Verify release APK builds in CI.

## Acceptance criteria

1. Widget added to home screen reflects current server state and toggles correctly.
2. With "Auto-start on device boot" enabled, rebooting the device starts the server within ~30 s of unlock.
3. With "Start server on app launch" enabled, opening the app starts the server (when permission is granted).
4. `./gradlew assembleRelease` produces a signed APK + AAB (when secrets are present).
5. Release APK installed manually behaves identically to debug (transfer test passes).
6. CI is green on the final PR.
7. Every user-visible string is in `strings.xml`; no Kotlin/Compose literals.
8. Repo contains LICENSE (Apache 2.0), README, CONTRIBUTING, CODE_OF_CONDUCT, translationHelp, issue/PR templates, CI workflow.

## Out of scope

- Actual Play Store submission (requires manual console work, screenshots, content rating questionnaire).
- Beyond-English translations (community-driven post-launch).
- Crash reporting / analytics — explicitly **not** added in v1; mention in README that the app collects no telemetry.

## Risks / decisions

- **Widget on Android 12+** — `RemoteViews` collection limits don't apply to Glance, but background work to refresh must be quick (< 5 s) or the system kills it. Update from inside the FGS context which is alive.
- **Direct Boot** — receiver is not `directBootAware`, so auto-start waits for user unlock. Acceptable.
- **Play `MANAGE_EXTERNAL_STORAGE`** — submission requires policy declaration. Draft justification text included in `fastlane/metadata`.
- **R8 + Apache FTPServer reflection** — must verify with the smoke test; expect to iterate on keep rules.
