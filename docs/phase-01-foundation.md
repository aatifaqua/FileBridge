# Phase 1 — Project Foundation & Build Setup

## Goal

Stand up a buildable, lint-clean, Hilt-wired Android project skeleton with the full dependency graph declared. After this phase, every later phase plugs into an existing structure rather than scaffolding its own.

## Deliverables

### Build configuration
- `settings.gradle.kts` with `pluginManagement` + `dependencyResolutionManagement`.
- `gradle/libs.versions.toml` version catalog covering: AGP, Kotlin, Compose BOM, Hilt, Coroutines, DataStore, Apache FTPServer, BouncyCastle (for cert generation), ZXing (QR), Glance, JUnit, Turbine, MockK.
- Root `build.gradle.kts` applying plugin aliases.
- `app/build.gradle.kts`:
  - `compileSdk` = latest stable, `minSdk` = 26, `targetSdk` = latest stable.
  - `applicationId` = `app.filebridge`.
  - Kotlin 2.x with Compose Compiler plugin.
  - Build types: `debug`, `release` (R8 + resource shrinking on release; debug suffix `.debug`).
  - `buildFeatures.compose = true`, `viewBinding = false`.
  - Hilt plugin applied.
  - Java 17 toolchain.
- `gradle.properties` with `org.gradle.jvmargs`, `kotlin.code.style=official`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`.

### Manifest & application class
- `AndroidManifest.xml` declaring `INTERNET`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `MANAGE_EXTERNAL_STORAGE` (with the special-access intent properly handled in Phase 9).
- `<application>` with `name=".FileBridgeApp"`, `theme=@style/Theme.FileBridge`, `allowBackup=false`, `dataExtractionRules`, `localeConfig`.
- `FileBridgeApp : Application` annotated `@HiltAndroidApp`.

### Hilt DI scaffolding
- `di/AppModule.kt` — provides `@ApplicationContext`, app-level coroutine scope (`SupervisorJob + Dispatchers.Default`), default `CoroutineDispatcher` bindings (`@IoDispatcher`, `@MainDispatcher`, `@DefaultDispatcher`).
- Empty placeholder modules: `DataModule`, `DomainModule`, `ServerModule` — to be filled by later phases.

### Theme placeholder
- `ui/theme/Theme.kt` with a minimal Material 3 `FileBridgeTheme` composable (real palette/typography arrives in Phase 6).
- `res/values/themes.xml` with `Theme.FileBridge` parented to `Theme.Material3.DynamicColors.DayNight.NoActionBar`.

### Resources
- `res/values/strings.xml` with just `app_name` = `FileBridge`.
- `res/values/colors.xml` empty placeholder.
- `res/mipmap/*` adaptive launcher icons (placeholder vector OK).
- `res/xml/data_extraction_rules.xml` and `backup_rules.xml` stubs (no backup).

### Entry activity
- `MainActivity : ComponentActivity` annotated `@AndroidEntryPoint`, setting an empty `FileBridgeTheme { Surface { Text("FileBridge") } }`. Real content arrives in Phase 6.

### Tooling
- `.editorconfig` enforcing 4-space indent for Kotlin, LF line endings.
- `detekt` or `ktlint` config (pick one — ktlint preferred via Gradle plugin) wired into `check`.
- `.gitignore` for Android (Studio, Gradle, build outputs, `local.properties`).

### Repo-level files
- `LICENSE` — Apache 2.0 (full text).
- `README.md` — stub with project name, one-paragraph description, "build instructions" referencing `./gradlew assembleDebug`. Screenshots placeholder section.
- `CONTRIBUTING.md` — stub (filled in Phase 10).
- `.github/workflows/ci.yml` — minimal: checkout, JDK 17, `./gradlew assembleDebug lint` on push & PR. Tests added in Phase 3.

## Acceptance criteria

1. `./gradlew clean assembleDebug` succeeds on a fresh checkout.
2. `./gradlew lint` succeeds with zero errors.
3. Installing the debug APK on an emulator launches to a blank "FileBridge" screen without crashing.
4. CI workflow runs green on a sample PR.
5. Hilt graph compiles (intentional `@Inject constructor` on a dummy class in `MainActivity` to prove DI works, then remove the dummy at end of phase).

## Out of scope

- Any FTP code, settings UI, screens beyond the placeholder, notifications, widget, or onboarding.
- Real Material 3 palette/typography (Phase 6).
- Permission request flow (Phase 9).

## Risks / decisions

- **Version catalog drift** — pin Compose via BOM; pin Hilt and Kotlin together to avoid Compose-Compiler mismatch.
- **AGP / Gradle wrapper** — wrapper distribution URL must match AGP requirement. Use Gradle 8.x.
- **R8 on release** — keep rules added incrementally per phase; in Phase 1 add `proguard-rules.pro` with comments only.
