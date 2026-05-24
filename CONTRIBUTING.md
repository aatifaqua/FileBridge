# Contributing to FileBridge

Thank you for your interest in contributing! Every bug fix, feature, and translation improves the app for everyone.

## Getting started

1. **Fork** the repository and create a new branch from `main`:
   ```bash
   git checkout -b feature/my-improvement
   ```

2. **Build** the project with Android Studio (Hedgehog / 2023.1+) or via the command line:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Run tests** before submitting:
   ```bash
   ./gradlew testDebugUnitTest lint
   ```

## Code style

This project uses **ktlint** (configured via the Gradle plugin). It runs automatically in CI.
To format locally:
```bash
./gradlew ktlintFormat
```

Key conventions:
- Kotlin only — no Java source files
- All strings in `res/values/strings.xml` — no hardcoded literals in Kotlin/Compose
- `@HiltViewModel` for all ViewModels; `@Singleton` for repositories/controllers
- Unit-test every ViewModel and use-case
- `contentDescription` on all interactive icons

## Branching model

| Branch | Purpose |
|--------|---------|
| `main` | Stable, always buildable |
| `feature/*` | New features — PRs target `main` |
| `fix/*` | Bug fixes — PRs target `main` |

## Commit messages

We recommend [Conventional Commits](https://www.conventionalcommits.org/) (not enforced):
```
feat: add passive port range validation
fix: prevent crash on empty root directory
docs: update CONTRIBUTING.md
```

## Pull requests

1. Fill in the PR template checklist.
2. Ensure CI is green (lint + tests + assembleDebug).
3. Maintainers aim to review within a week.

## Translations

See [translationHelp.md](translationHelp.md) for how to add or improve a locale.

## License

By contributing you agree that your contributions will be licensed under the [Apache 2.0 License](LICENSE).
