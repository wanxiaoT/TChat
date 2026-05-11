# Repository Guidelines

## Source Boundary

- **App source**: `C:\Users\Administrator\AndroidStudioProjects\TChat`
  - Open source Android client.
  - This repository may contain client UI, public API paths, public HTTPS
    endpoints, and app-side integration logic.

- **Server backend source**: `C:\1Git\tchat对接naapi方案\naapi-tchat-backend`
  - Closed source.
  - Contains backend code, billing logic, license logic, payment handling,
    NewAPI integration, admin tooling, deployment files, and server-only
    configuration.
  - Do not copy backend source, backend deployment logic, backend secrets,
    NewAPI admin credentials, payment secrets, database files, private keys, or
    production environment configuration into this Android open-source
    repository.

- TChat official NAAPI service requests must go to `https://t.naapi.cc`; the
  Android app must not directly connect to `naapi.cc`.

## Project Structure & Module Organization

- `app/`: Android application (Jetpack Compose UI, navigation, settings).
- `feature-chat/`: Chat feature UI and related Compose components (includes `src/main/assets/`).
- `data/`: Room database, repositories, models, and skill/matching logic.
- `network/`: Networking layer (OkHttp, API clients).
- `core/`: Shared utilities and small cross-module helpers.
- Sources live in `*/src/main/java/…`; resources in `app/src/main/res/…`.
- Tests (currently minimal) live in `app/src/test/…` and `app/src/androidTest/…`.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repo root (Windows: `gradlew.bat`, macOS/Linux: `./gradlew`):

- `./gradlew :app:assembleDebug` — build a debug APK.
- `./gradlew :app:installDebug` — install on a connected device/emulator.
- `./gradlew testDebugUnitTest` — run JVM unit tests.
- `./gradlew connectedDebugAndroidTest` — run instrumentation + Compose UI tests.
- `./gradlew lint` — run Android Lint checks.

Dependencies are managed via the version catalog: `gradle/libs.versions.toml`.

## Coding Style & Naming Conventions

- Kotlin + Compose, 4-space indentation, no tabs. Prefer Android Studio’s default Kotlin formatter.
- `@Composable` functions use `PascalCase` (e.g., `SkillScreen`); non-UI functions use `camelCase`.
- Types use `PascalCase`; constants use `UPPER_SNAKE_CASE`.
- Keep module namespaces consistent (e.g., `com.tchat.wanxiaot` in `app`, `com.tchat.data` in `data`).

## Testing Guidelines

- Unit tests: JUnit4 in `app/src/test`.
- Instrumentation/UI: AndroidX test runner + Espresso/Compose UI tests in `app/src/androidTest`.
- Name tests `*Test.kt` and mirror production package paths (e.g., `app/src/test/java/com/tchat/…`).

## Commit & Pull Request Guidelines

- Commit messages in history are short and action-focused (often starting with `修复…`, `更新…`, `添加…`); follow the same style and keep commits scoped.
- PRs should include: what/why, steps to verify, and screenshots for UI changes (phone + tablet where applicable). Link related issues when available.

## Configuration & Security

- Never commit API keys or personal tokens. Use in-app provider settings or local-only files (e.g., `local.properties`, `~/.gradle/gradle.properties`).
- Ensure `local.properties` points to a valid Android SDK installation before building.
