# CLAUDE.md — anonimizador-pdf

Project guidance for Claude Code (and humans) working in this repository.

## What this is

A **fully-offline** native Android app for anonymizing patient PDFs (clinical chat records and lab
results) before they are sent to external LLMs for research. It is a personal clinical tool. It
extracts PDF text on-device, lets an **on-device** LLM (MediaPipe LLM Inference) suggest
LGPD-sensitive terms — or the user marks them manually — redacts them, and exports an anonymized
`.txt`. All user-visible text is **Brazilian Portuguese (pt-BR)**.

## Security constraints (NON-NEGOTIABLE)

This app handles sensitive patient data. Do not weaken any of these:

- **No `android.permission.INTERNET`** — ever. The app makes zero network calls at runtime.
- `android:usesCleartextTraffic="false"`, `android:allowBackup="false"`,
  `android:networkSecurityConfig="@xml/network_security_config"` on `<application>`.
- Every `<activity>` / `<provider>` sets `android:exported` explicitly.
- No Firebase / Analytics / Crashlytics / remote logging. No `com.google.android.gms:*`
  (excluded from the MediaPipe dependency).
- Extracted text and anonymized output live **only** in Room (app-internal storage). No temp files
  on shared external storage.
- **Never log extracted/clinical text content.** Log sizes only (e.g. `"extraction complete, N chars"`).

## Architecture

MVVM + Clean Architecture, single Activity, Jetpack Compose.

```
data/        Room (db), DataStore (preferences), repository impls (pdfbox + MediaPipe)
domain/      models, repository interfaces, use cases
presentation/ navigation (adaptive shell + NavGraph), theme, ui/{library,viewer,anonymize,settings,onboarding}
di/          Hilt modules (Database, Repository, UseCase)
```

Adaptive UI via `WindowSizeClass` (`calculateWindowSizeClass`): phone = `NavigationBar` + single
pane; tablet (Expanded) = `PermanentNavigationDrawer` + two-pane (`NavigableListDetailPaneScaffold`).

## Commands

```bash
./gradlew assembleDebug        # build the debug APK -> app/build/outputs/apk/debug/
./gradlew installDebug         # install on a connected device/emulator
./gradlew testDebugUnitTest    # run JVM unit tests (redaction + JSON parsing)
./gradlew lint                 # Android lint
```

## Build / version coupling (read before bumping anything)

Versions are pinned in `gradle/libs.versions.toml` and are **deliberately era-matched to Kotlin
2.0.21**. The coupling rules that break the build if violated:

- **Compose compiler plugin version == Kotlin version** (`org.jetbrains.kotlin.plugin.compose` =
  2.0.21). Kotlin 2.0+ uses this plugin instead of `kotlinCompilerExtensionVersion`.
- **KSP version's Kotlin prefix == Kotlin version** (`2.0.21-1.0.27`).
- **AGP ≤ 8.7.x** — AGP 9.x force-upgrades the Kotlin Gradle Plugin to 2.2.x.
- **Gradle wrapper** 8.10.2 (≥ AGP 8.7 floor 8.9, ≤ Kotlin 2.0.21 tested ceiling 8.10).
- **Compose BOM is era-matched** (2024.12.01) — do NOT bump to the latest BOM without also bumping
  Kotlin/Compose-compiler, because the Compose runtime is coupled to the compiler.
- AGP 8.x requires **JDK 17+**; `compileSdk`/`targetSdk` = 35, `minSdk` = 31.

Hilt and Room both use **KSP** (not KAPT).

## Build verification

The build is verified by **GitHub Actions** (`.github/workflows/android.yml`) on `ubuntu-latest`
runners, which have the Android SDK and full network access. CI runs `assembleDebug` +
`testDebugUnitTest` and uploads the debug APK as an artifact.

## Loading an LLM model

The app does not bundle a model. The user downloads a MediaPipe `.task` model (e.g. Gemma 3) and
picks it in **Settings → Modelo LLM**. See `README.md`.
