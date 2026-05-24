# FileBridge — Product Requirements Document (Index)

FileBridge is an open-source Android app that turns a device into a Wi-Fi FTP/FTPS server. This PRD decomposes the full spec from `wifi_ftp_server_build_prompt FileBridge.md` into 10 phases, ordered by architectural layer so each phase can be implemented, reviewed, and merged independently.

---

## Source of truth

The canonical feature spec lives in `wifi_ftp_server_build_prompt FileBridge.md` in the same folder. This PRD does **not** redefine features — it sequences them, defines deliverables, and sets acceptance criteria per phase. If the spec and PRD ever disagree, the spec wins; update the PRD.

---

## Tech stack (recap)

Kotlin · Jetpack Compose (Material 3) · Hilt · Coroutines + Flow · DataStore · Apache FTPServer · Gradle version catalogs · min SDK 26 · Apache 2.0.

---

## Architecture (recap)

MVVM + Clean Architecture. Module layout inside `:app` (single-module to start, can be split later):

```
data/      — FTP wrapper, DataStore, storage, secure prefs
domain/    — use cases, domain models
ui/        — Compose screens, ViewModels, theme, navigation
service/   — Foreground Service
widget/    — Glance widget
receiver/  — Boot receiver
di/        — Hilt modules
```

No FTP or server logic in `ui/` or ViewModels.

---

## Phase map

| # | Phase | File | Depends on |
|---|---|---|---|
| 1 | Project Foundation & Build Setup | [phase-01-foundation.md](phase-01-foundation.md) | — |
| 2 | Data Layer — Persistence & Secure Storage | [phase-02-data-layer.md](phase-02-data-layer.md) | 1 |
| 3 | Domain Layer — Models & Use Cases | [phase-03-domain-layer.md](phase-03-domain-layer.md) | 2 |
| 4 | FTP Server Engine (FTP + FTPS) | [phase-04-ftp-engine.md](phase-04-ftp-engine.md) | 2, 3 |
| 5 | Foreground Service & Notification | [phase-05-service.md](phase-05-service.md) | 4 |
| 6 | UI Shell — Theme, Navigation, Scaffolding | [phase-06-ui-shell.md](phase-06-ui-shell.md) | 1 |
| 7 | Home Screen — Status, QR, Connection Info | [phase-07-home-screen.md](phase-07-home-screen.md) | 5, 6 |
| 8 | Settings & Logs Screens | [phase-08-settings-logs.md](phase-08-settings-logs.md) | 5, 6 |
| 9 | Onboarding, Permissions & First-Run UX | [phase-09-onboarding.md](phase-09-onboarding.md) | 6, 8 |
| 10 | Widget, Boot Auto-Start, i18n, CI & Release | [phase-10-widget-i18n-release.md](phase-10-widget-i18n-release.md) | 5, 7, 8 |

Phases 6 can begin in parallel with phases 2–5 (UI shell only depends on the foundation). Phase 7 needs the service to be callable; phase 8 the same. Phase 10 wraps everything for distribution.

---

## Cross-cutting requirements (apply to every phase)

- **No hardcoded user-facing strings.** Every string visible to the user goes in `res/values/strings.xml`.
- **Material 3 tokens only** for colors, typography, shapes. No hex literals in Composables.
- **Hilt-injected**, no manual singletons or `object` services holding state.
- **No plaintext secrets** — passwords and cert keys go through Android Keystore / EncryptedSharedPreferences.
- **Tests:** every use case and ViewModel gets unit tests. FTP engine gets at least one integration test against a loopback client.
- **Lint clean** — `./gradlew lint` must pass with zero new warnings per phase.
- **Accessibility** — all interactive elements ≥ 48dp, content descriptions on icon-only buttons.

---

## Definition of "phase complete"

A phase is done when:

1. All deliverables in its phase doc are implemented.
2. All acceptance criteria pass (manual or automated).
3. `./gradlew assembleDebug lint testDebugUnitTest` is green.
4. No TODOs introduced without a tracked follow-up.
5. The phase doc's "Out of scope" section was respected — leakage from later phases is rejected in review.

---

## Open questions / decisions deferred

These are noted in individual phase docs but listed centrally so they're not forgotten:

- Exact Apache FTPServer artifact coordinates and whether to vendor any patches (Phase 4).
- Whether DataStore Proto vs Preferences (Phase 2 — currently Preferences per spec).
- Whether to ship a custom keystore password or derive one per-install (Phase 4).
- Crowdin vs Weblate choice for translation hosting (Phase 10).
