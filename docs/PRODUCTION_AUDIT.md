# Auditoria de produção e roteiro do "Privacy Suite"

*Audit date: 2026-07-13 · Scope: full codebase at commit `987a97a` + external research (F-Droid policy, PDF redaction techniques). Findings verified against source with file:line references.*

This document has four parts:

1. **Production-readiness audit** — verified findings, ranked by severity.
2. **F-Droid distribution plan** — what the store actually requires, per-dependency verdicts, checklist.
3. **Privacy-suite expansion** — true in-PDF redaction, more document formats, text→PDF, with a technical design.
4. **Additional ideas & milestone roadmap.**

---

## Part 1 — Production-readiness audit

### What is already good

- Offline guarantee is real and layered: no `INTERNET` permission plus `tools:node="remove"` defense, `usesCleartextTraffic=false`, empty trust anchors, no GMS/Firebase, `allowBackup=false`.
- Logging discipline holds: every `Log.*` call in `data/` logs ids/sizes only — no clinical content anywhere.
- Anonymized exports use neutral timestamped filenames; FileProvider is not exported; MainActivity is the only exported component.
- String-resource discipline in the UI is excellent (~180 strings + 7 plurals; only a handful of hardcoded exceptions).
- Solid unit tests for the core logic (PiiDetector, redaction, formatter, chunker, LLM parsing).

### P0 — Critical (fix before any public distribution)

| # | Finding | Where |
|---|---------|-------|
| 1 | **Releases ship a debuggable, debug-keyed APK.** `release.yml` publishes `assembleDebug` output. `debuggable=true` means anyone with ADB access can `run-as` the app and pull the entire Room DB (clinical text), DataStore (learned patient names) and imported PDFs — no root needed. F-Droid also only builds `assembleRelease`. | `.github/workflows/release.yml:62-75`, `app/build.gradle.kts:33-36` |
| 2 | **Cleartext residue survives "Apagar tudo".** `shareText`/`sharePdf` stage files (including **raw un-anonymized combined PDFs**) into `filesDir/exports/` and never delete them; `deleteAll()`/`deleteDocument()` never touch that directory. | `ViewerScreen.kt:468-472`, `LibraryScreen.kt:923-926`, `PdfRepositoryImpl.kt:124-134` |
| 3 | **Accent/normalization gap causes silent redaction misses.** `(?iu)` case-folds but does not fold diacritics, and there is no `Normalizer` call anywhere. Learned term "João" won't match "Joao" in the next document (and vice-versa for manual terms); NFD-decomposed extraction won't match NFC learned terms. The KDoc claims accent-insensitivity that the code doesn't deliver. | `ApplyRedactionsUseCase.kt:4,24`, `PiiDetector.kt:192-195` |
| 4 | **Path traversal in model import.** `File(dir, name)` where `name` is the `DISPLAY_NAME` returned by an arbitrary content provider — a malicious provider returning `../../databases/anonimizador.db` overwrites files in the sandbox. `PdfRepositoryImpl.sanitizeFilename()` exists but isn't applied here. | `LlmRepositoryImpl.kt:86-88` |
| 5 | **No `FLAG_SECURE`.** Raw patient text appears in Recents thumbnails, screenshots, and screen-casting on every screen. Should default ON with a Settings opt-out. | `MainActivity.kt` (absent everywhere) |
| 6 | **Clipboard copies without `EXTRA_IS_SENSITIVE`.** Four copy sites use Compose `LocalClipboardManager` (which can't set the flag); one copies the raw un-anonymized text. Clinical text lands in the Android 13+ clipboard preview, clipboard-history keyboards, and OEM cross-device clipboard sync — a *network* egress path outside the app's control. | `ViewerScreen.kt:182,236,253`, `AnonymizeScreen.kt:163` |

### P1 — High

| # | Finding | Where |
|---|---------|-------|
| 7 | **Two OOM/crash paths on large documents.** (a) Extraction uses `PDDocument.load(file)` with main-memory buffering (the merge path already uses temp-file buffering — extraction should too). (b) List queries `SELECT *` including the full `extractedText`/`anonymizedText` for every row; a single row >2 MB throws `SQLiteBlobTooBigException` on many devices. Needs projection DTOs without text columns. | `PdfRepositoryImpl.kt:87`, `PdfDocumentDao.kt:15-16`, `AnonymizedVersionDao.kt:16-17` |
| 8 | **Whole document rendered as one `Text`.** Viewer, anonymize editor and preview each lay out the full extracted text in a single non-lazy `Text` (the editor inside `verticalScroll`); a 500-page document means multi-second jank or ANR. `buildHighlightedText` is O(text×terms) inside composition and re-runs on every tap. | `ViewerScreen.kt:276-287`, `AnonymizeScreen.kt:222-240,471-497`, `RedactableText.kt:36-38,80-127` |
| 9 | **Redaction session lost on process death.** Only `docId` is in `SavedStateHandle`; curated chips/preview state evaporate if Android kills the backgrounded process. No `BackHandler` on the anonymize screen either — system back from preview discards unsaved work without confirmation. | `AnonymizeViewModel.kt:88-90`, `AnonymizeScreen.kt` |
| 10 | **Tap-to-redact is invisible to TalkBack.** The interaction is a raw `pointerInput` on one `Text`; there are zero `semantics` modifiers in the entire module. Selection state is conveyed by color only. | `RedactableText.kt:42-55,120` |
| 11 | **LLM token loss / races.** MediaPipe callback uses `trySend` (drops tokens on backpressure); llama path uses `DROP_OLDEST` (a dropped `Done` event hangs the flow; a dropped `]` silently empties the parse). Engine init has no mutex — concurrent generate calls can leak a second native LLM instance. | `LlmRepositoryImpl.kt:74-77,117-137,148-153,160-210` |
| 12 | **Plaintext at rest.** Room DB and DataStore (learned patient names) are unencrypted; combined with #1 this is trivially extractable. Consider SQLCipher (`SupportFactory`) + Keystore-held key, and `android:dataExtractionRules` alongside `allowBackup=false`. | `DatabaseModule.kt:24-27`, `AppPreferences.kt`, manifest |

### P2 — Medium / low (selected; full list in the PR description of the audit)

- Encrypted PDFs surface as generic `IO_ERROR` — no `ENCRYPTED` variant in `ExtractionError`; no early `isEncrypted` check (`PdfRepositoryImpl.kt:112-117`).
- `importModel` deletes the old model before the new copy is validated; a failed copy leaves no model + possibly a truncated file (`LlmRepositoryImpl.kt:84-92`).
- MediaPipe generation is not cancellable (`cancelGenerateResponseAsync` never called) (`LlmRepositoryImpl.kt:154`).
- Loading overlays don't consume input → concurrent imports possible (`LibraryScreen.kt:888-906`, `HomeScreen.kt:368-394`).
- Undo-delete: if the user navigates away before the snackbar resolves, the document stays hidden until the VM dies (`LibraryViewModel.kt:294-297`, `LibraryScreen.kt:157-164`).
- Overlapping multi-word names can strand a surname: "Maria Clara" + "Clara Souza" over "Maria Clara Souza" leaves "Souza" (`ApplyRedactionsUseCase.kt:16-28`). Interval-based matching fixes it.
- `OutputFormatter` deletes any line that is only 1–4 digits — that's a **lab value**, not just a page number; contradicts its "layout, not content" contract (`OutputFormatter.kt:26,86-89`).
- Deep-scan chunking splits at arbitrary offsets with no overlap — PII straddling a cut is invisible to the LLM (`TextChunker.kt:29-37`).
- FileProvider exposes the whole cache dir via a dead `cache-path` entry (`res/xml/file_paths.xml:7`).
- `exportSchema=false` blocks Room migration tests; turn it on and commit `schemas/`.
- Raw English `e.message` from inference engines shown verbatim in the pt-BR UI (`AnonymizeViewModel.kt:214,258`); pt-BR exception strings hardcoded in the data layer.
- CI never runs `lint` or `assembleRelease`.
- Shared-in PDF lost on rotation before consumption (`MainActivity.kt:27,36-38`); `ACTION_VIEW` silently imports with no confirmation.
- Learned-terms list is clear-only, not viewable/per-term-removable, despite docs claiming otherwise (`SettingsScreen.kt:217-238`).
- Accessibility: 32 dp touch target (`LibraryScreen.kt:656`), switch rows without `toggleable(role=Switch)`, no live-region announcements on progress overlays.

---

## Part 2 — F-Droid distribution plan

### Policy verdicts (verified against current policy sources)

- **Prebuilt Maven AARs are allowed** if the artifact is FOSS-licensed and comes from a trusted repo (Maven Central, Google Maven, etc.). The scanner only flags binaries committed to the *app repo* (only `gradle-wrapper.jar` here — standard exception). No anti-features apply to this app; the no-INTERNET, user-side-loaded-model design is *stronger* than existing precedents (Whisper/whoBIRD ship TFLite and carry only a NonFreeNet flag for model downloads the app performs — ours performs none).

| Dependency | Verdict |
|---|---|
| `com.tom-roush:pdfbox-android` | **OK** — Apache-2.0, pure Java, Maven Central. |
| `io.github.ljcamargo:llamacpp-kotlin` | **OK** — Apache-2.0 POM on Maven Central; native libs are built from vendored llama.cpp sources in its repo, so a from-source recipe is feasible if ever demanded. |
| `com.google.mediapipe:tasks-genai` | **Likely OK, medium risk** — Apache-2.0 on Google Maven, CPU engine source is public, but no F-Droid app ships it yet and the GPU path's source completeness has been questioned upstream. **Plan B: an `fdroid` product flavor that drops MediaPipe and keeps only the GGUF/llama.cpp engine.** |
| AndroidX / Hilt / Room / Kotlin | **OK.** |

### Submission checklist

1. **Real release builds**: keep `signingConfig` out (F-Droid signs), but make CI build `assembleRelease` on tags and stop publishing debug APKs. `isMinifyEnabled=false` actually helps reproducibility; revisit later with the reproducible-builds flow (`Binaries:` + `AllowedAPKSigningKeys`) to keep your own signature.
2. **Static versioning**: F-Droid's `checkupdates` cannot execute Gradle — the current `findProperty(...) ?: fallback` pattern is exactly the unsupported dynamic case. Commit literal `versionCode`/`versionName` bumped in the tagged release commit (CI `-P` override can stay for GitHub releases).
3. **Fastlane metadata**: create `fastlane/metadata/android/{en-US,pt-BR}/` with `title.txt`, `short_description.txt` (≤80 chars), `full_description.txt`, `changelogs/<versionCode>.txt`, icon + phone screenshots. en-US is the required fallback locale.
4. **Donations**: add `.github/FUNDING.yml` (Liberapay / GitHub Sponsors / OpenCollective / custom) *before* the fdroiddata MR — donation links must be verifiable from the repo. `Donate:`/`Liberapay:`/`OpenCollective:` fields go in the recipe. In-app donation screens are allowed by F-Droid (no anti-feature).
5. **Submit** a direct MR to `gitlab.com/fdroid/fdroiddata` (`metadata/dev.lorenzods.anonimizadorpdf.yml`, `subdir: app`, `gradle: [yes]`, `AutoUpdateMode: Version`, `UpdateCheckMode: Tags ^v[0-9.]+$`). Keep the GMS exclude. Expect possible reviewer discussion on MediaPipe (have the flavor ready).
6. **Positioning**: no PDF-redaction/anonymization app exists in the main repo today — this fills a real gap. README should grow an English section; the "sem fins comerciais" line should be dropped or reworded (MIT already permits commercial use; the phrase only creates license ambiguity).

---

## Part 3 — Privacy-suite expansion

### 3.1 The color-change idea: rejected, and what to do instead

Painting text the background color (or drawing a box over it) only changes *rendering*. The `Tj`/`TJ` show-text operators and their string operands remain byte-for-byte in the content stream — copy/paste, `pdftotext`, and every LLM ingestion pipeline recover the "hidden" text verbatim. This is the classic litigated redaction failure (Manafort, Maxwell). Apache PDFBox deliberately never shipped a redaction API for this reason (PDFBOX-1755; the open PDFBOX-5588 explicitly only *simulates* redactions). The stated requirement — "any parser gets gibberish" — is met only by removing/replacing the bytes, or by rebuilding pages from pixels.

Two sound modes, both implementable with the existing stack:

**Mode 1 — Rasterized redaction ("modo seguro", ship first).**
`android.graphics.pdf.PdfRenderer` renders each page to a bitmap (~150–200 dpi) → black boxes (or "ANONIMIZADO" stamps) are drawn over the matched term regions → `android.graphics.pdf.PdfDocument` rebuilds a clean PDF from the bitmaps. Zero new dependencies, ~1–2 days of work, and **gibberish-proof by construction** — nothing from the original file structure survives (this is the Dangerzone model). It is also the *only* correct answer for scanned PDFs. Trade-offs: loses text selectability (recoverable later via a Tesseract OCR layer over the already-redacted pixels) and grows file size (compress page bitmaps to JPEG before drawing).

**Mode 2 — Vector-preserving redaction ("modo avançado", ship second).**
Content-stream surgery with pdfbox-android (all required classes verified present in the port): `PDFStreamParser` → walk tokens → drop/split the `Tj`/`TJ`/`'`/`"` operands whose glyphs (located via `PDFTextStripper`/`TextPosition`) fall inside a matched term → `ContentStreamWriter` → `page.setContents(...)`, recursing into Form XObjects and annotation appearance streams; then stamp black boxes + label over the holes (`PDPageContentStream` in APPEND mode, embedded Noto Sans TTF). The hard 20% is CID/Type0 subset fonts: **delete codes rather than substitute** (never invent codes outside the embedded subset). Reference patterns: Apache's `RemoveAllText` example and mkl's `PdfContentStreamEditor`.

**Non-negotiable invariant for both modes:** after producing the redacted PDF, **re-extract its text and assert zero confirmed terms survive** (whole-word, normalized). If verification fails in Mode 2, automatically fall back to Mode 1. This turns "parsers get gibberish" into a testable gate rather than a hope.

**Metadata scrubbing (required for any "redacted PDF" claim).** All doable with pdfbox-android: reset `/Info` dictionary (Title often carries the patient-identifying filename), null XMP metadata (document, per-page, per-XObject), remove embedded files/attachments, annotations, AcroForm values (or flatten), outlines/bookmarks, and the structure tree (`ActualText`/`/Alt` duplicate content text — often forgotten). Save to a **new** file (full rewrite; never `saveIncremental`, which preserves prior revisions).

### 3.2 More input formats

Recommended abstraction, fitting the existing Clean Architecture (Hilt multibinding keyed by MIME/extension/magic bytes):

```kotlin
interface DocumentSource {
    suspend fun extractText(uri: Uri): ExtractedDocument   // text + span map + metadata inventory
    val redactionBackend: RedactionBackend?                // null => degrade to .txt / generated-PDF export
}
interface RedactionBackend {
    suspend fun redact(uri: Uri, terms: List<ConfirmedTerm>, out: OutputStream): RedactionReport
}
```

Every backend ends with the same verify-by-re-extraction gate; formats without a writable backend degrade explicitly to redacted `.txt` or a generated structured PDF.

| Format | How (offline, FOSS) | Write-back | Priority |
|---|---|---|---|
| TXT / Markdown | stream + charset sniff (UTF-8 / ISO-8859-1 for legacy pt-BR) | trivial | **Now** — near-zero cost, clinical chat exports are often .txt |
| DOCX | ZIP + platform `XmlPullParser` over `word/document.xml` (+ headers/footers/footnotes/comments + `docProps/*.xml` metadata). **Not Apache POI** (broken/heavy on Android). Word splits words across `<w:r>` runs → match on paragraph text, map back to run offsets | yes — rewrite `<w:t>` nodes, scrub core/app props, fold tracked changes, drop comments/thumbnail | **High** |
| ODT | same ZIP+XML story (`content.xml`, `meta.xml`), simpler than DOCX | yes | High |
| HTML / EPUB | jsoup (MIT, ~430 KB, Android-proven); EPUB = ZIP of XHTML via spine order | yes via DOM edit | Later |
| RTF | small hand-rolled tokenizer, read-only (`RTFEditorKit` is Swing — unavailable) | no | Later |
| Scanned PDFs / images | **tesseract4android** (Apache-2.0, F-Droid-proven) with user-imported `por.traineddata` — reuse the existing "import a model file" UX to keep the APK small. ML Kit rejected (closed-source + GMS) | n/a — OCR feeds Mode 1 | Medium |

### 3.3 Text → structured PDF

- **Default engine: `android.graphics.pdf.PdfDocument` + `StaticLayout`** (AOSP, zero deps). Real vector text, full Unicode/pt-BR shaping via any system font, compact output; a ~150-line paginator (title, sections, page numbers) covers the `OutputFormatter` → PDF case.
- Keep **pdfbox-android + one embedded FOSS TTF** (Noto Sans; the built-in Helvetica is WinAnsi-only and throws on em-dashes/smart quotes common in chat logs) for stamping text onto *existing* PDFs — `android.graphics.pdf` can only create documents, not edit them.

---

## Part 4 — Additional ideas & roadmap

### Suggested features beyond the request

1. **Consistent pseudonymization** — replace each distinct entity with a stable token (`[PACIENTE-1]`, `[MEDICO-2]`, `[INSTITUICAO-1]`) instead of a flat `[ANONIMIZADO]`. For the stated research use-case this is a major quality win: the external LLM keeps referential integrity ("who said what") with zero extra privacy cost. Mapping stays on-device only.
2. **Relatório de redação** — after each export, a summary (N terms, by category, verification-pass result) the user can keep; doubles as an LGPD-mindset audit trail without storing the terms themselves.
3. **App lock** (BiometricPrompt/PIN) + optional panic wipe; retention options ("delete original after anonymizing", auto-delete after N days).
4. **Friction on sharing non-anonymized content** — a "you are sharing RAW patient text" confirmation on the viewer's copy/export of originals.
5. **Learned-terms manager** — viewable, per-term removable list (a false learned term currently matches forever, invisibly).
6. **Redaction profiles** — presets of which categories auto-select (e.g. "pesquisa" vs "compartilhamento com colega").
7. **Batch mode** — run the offline detector over a folder of documents with one review queue.

### Milestones

- **v3.1 — Hardening (pre-F-Droid gate).** All P0 items; OOM fixes (temp-file extraction, projection DAOs); release-build CI; accent/NFC normalization + tests; `ExtractionError.ENCRYPTED`; export-dir cleanup in `deleteAll()`.
- **v3.2 — Store readiness.** Static versioning; fastlane metadata (pt-BR + en-US); FUNDING.yml; lint in CI; English README section; optional `fdroid` flavor without MediaPipe; submit fdroiddata MR.
- **v4.0 — Privacy suite I.** Rasterized redacted-PDF export + metadata scrubber + verification gate; TXT input; text→structured PDF; pseudonymization tokens; FLAG_SECURE/app-lock polish.
- **v4.1 — Privacy suite II.** DOCX/ODT read + writable redaction; learned-terms manager; redaction report.
- **v4.2 — Advanced.** Vector-preserving in-PDF redaction (Mode 2) with automatic fallback; OCR via tesseract4android; HTML/EPUB.

### Key references

- Apache `RemoveAllText` example · mkl's `PdfContentStreamEditor` (testarea-pdfbox2) · PDFBOX-1755 / PDFBOX-5588
- Dangerzone (freedomofpress/dangerzone) — rasterize-then-rebuild model
- PDF Association, *High-Security PDF Redactions* · alexwlchan, *Beware of incomplete PDF redactions*
- F-Droid: Inclusion Policy, Reproducible Builds, Build Metadata Reference, fastlane docs; precedents `org.woheller69.whisper`, `org.stypox.dicio`
- adaptech-cz/Tesseract4Android (Apache-2.0)
