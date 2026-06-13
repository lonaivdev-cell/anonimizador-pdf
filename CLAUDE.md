# CLAUDE.md — anonimizador-pdf

Project guidance for Claude Code (and humans) working in this repository.

## What this is

A **fully-offline** native Android app for anonymizing patient PDFs (clinical chat records and lab
results) before they are sent to external LLMs for research. It is a personal clinical tool. It
extracts PDF text on-device and suggests LGPD-sensitive terms **instantly with a deterministic
offline detector** (`PiiDetector`: Brazilian name dictionaries, chat-sender patterns,
CPF/RG/CRM/CRBM/CNS/phone/e-mail/address/date/prontuário regexes) — an **optional** on-device LLM
(MediaPipe or llama.cpp) can refine the list. The user confirms (tap-to-redact), terms are replaced
whole-word by `[ANONIMIZADO]`, and the result exports as `.txt` — optionally re-organized for LLM
reading via a toggle (`OutputFormatter`; OFF = exact redacted text). All user-visible text is
**Brazilian Portuguese (pt-BR)**.

## Security constraints (NON-NEGOTIABLE)

This app handles sensitive patient data. Do not weaken any of these:

- **No `android.permission.INTERNET`** — ever. The app makes zero network calls at runtime.
- `android:usesCleartextTraffic="false"`, `android:allowBackup="false"`,
  `android:networkSecurityConfig="@xml/network_security_config"` on `<application>`.
- Every `<activity>` / `<provider>` sets `android:exported` explicitly.
- No Firebase / Analytics / Crashlytics / remote logging. No `com.google.android.gms:*`
  (excluded from the MediaPipe dependency).
- Extracted text and anonymized output live **only** in Room (app-internal storage). No temp files
  on shared external storage. The **learned-terms** list (names/instituições the user has confirmed,
  used to seed future detection) lives in app-internal DataStore — same offline guarantee.
- **Never log extracted/clinical text content.** Log sizes only (e.g. `"extraction complete, N chars"`).
  This includes learned terms — never log their contents.

## Architecture

MVVM + Clean Architecture, single Activity, Jetpack Compose.

```
data/        Room (db), DataStore (preferences), repository impls (pdfbox + MediaPipe/llama.cpp)
domain/      models, repository interfaces, use cases
presentation/ navigation (adaptive shell + NavGraph), theme (color/type/shape + ThemeMode + dynamic color), ui/{home,library,viewer,anonymize,settings,onboarding}
di/          Hilt modules (Database, Repository, UseCase)
```

The Room database is at **version 2**. Schema changes ship as explicit `Migration`s (currently
`MIGRATION_1_2` in `data/db/Migrations.kt`, which adds the `folders` table plus `customName` /
`folderId` / `isFavorite` columns) — **never** `fallbackToDestructiveMigration`, because the DB holds
real patient documents. Keep migration DDL byte-aligned with what Room derives from the entities
(column types, nullability, the `@ColumnInfo(defaultValue = "0")` on `isFavorite`) so the post-migration
schema validation passes.

Adaptive UI via `WindowSizeClass` (`calculateWindowSizeClass`): phone = `NavigationBar` (Início /
Biblioteca / Configurações) + single pane; tablet (Expanded) = `PermanentNavigationDrawer` + two-pane
(`NavigableListDetailPaneScaffold`). The viewer (`DocumentViewer`) is reused both as the Library
detail pane and as a standalone route opened from Home. **The Library list pane** is the organization
hub: documents live in optional folders (`folders` table + nullable `folderId`), can be renamed inline
(`customName`, falling back to `originalFilename` via `PdfDocument.displayName`) and favorited (favorites
pin to the top), the list sorts by date/name/pages (persisted in `AppPreferences`), and long-press starts
multi-select for bulk move/favorite/delete. A **PDF combiner** merges the selected documents — in a
user-arranged order — into one new PDF via pdfbox `PDFMergerUtility` (`combinePdfs`, fully offline,
temp files in app cache only); the result is imported back as a fresh document and offered for
share/export. The anonymize screen is a single flow:
offline `PiiDetector` suggestions (grouped by `RedactionCategory`, pre-selected by `Confidence` —
LOW starts unchecked), tap-to-redact (`RedactableText`), a manual term field, and — only when a
model is loaded — optional LLM review/deep-scan. On save, the confirmed `NAME`/`ORGANIZATION` terms
are persisted to the **learned-terms** list (`AppPreferences`); `PiiDetector.detect(text, learnedTerms)`
folds them back in as HIGH-confidence hits on later documents, so the offline detector learns recurring
people/instituições with no model. The list is viewable/clearable in **Settings → Aprendizado offline**.
The preview has a persisted toggle that swaps between the raw redacted text and the `OutputFormatter`
layout; every output surface (anonymize preview, viewer version cards/preview) offers **Copiar**
(clipboard) and **Compartilhar** side by side. Saved `AnonymizedVersion`s surface in the viewer's
"Versões" tab; anonymized exports always use neutral timestamped filenames (the original PDF name may
identify the patient).

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

The app does not bundle a model. The user downloads a model (e.g. Gemma 3) and picks it in
**Settings → Modelo LLM**. See `README.md`.

Two inference engines are supported, selected by the imported file's **extension** in
`LlmRepositoryImpl`:

- **`.task` / `.litertlm`** → MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`).
- **`.gguf`** → llama.cpp via `io.github.ljcamargo:llamacpp-kotlin` (Apache-2.0). This AAR ships
  **prebuilt** native libraries (`arm64-v8a`, `x86_64`) — no NDK is needed in CI. It transitively
  pulls a newer Kotlin/AndroidX than the era-matched stack, so `app/build.gradle.kts` pins
  `kotlin-stdlib`/`core-ktx` back, excludes its unused `appcompat`/`material` UI transitives, and
  adds `-Xskip-metadata-version-check` (the AAR's Kotlin metadata is `mv=2.3`; the project compiler
  is 2.0.21). Keep these guards if bumping the library — they protect the version coupling above.

The offline guarantee is unchanged: no `INTERNET` permission, so neither engine can make network
calls regardless of the model format.
