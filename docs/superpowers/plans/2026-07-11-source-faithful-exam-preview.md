# Source-Faithful Exam Template and Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-built default DOCX layout with a Java port of the reference source/template engine, migrate every source-supported format option, and add exact PDF preview with format adjustment before final creation.

**Architecture:** Template mode copies the retained source DOCX package and replaces only named OOXML slots, headers, settings, and page configuration; simple mode retains the existing POI generator. Preview and final download share the same dispatcher, while a user-scoped preview service converts transient DOCX bytes to PDF through a configurable LibreOffice process.

**Tech Stack:** Java 21, Spring Boot 4, ZIP/JAXP/StAX OOXML processing, Apache POI 5.5.1 for simple mode and verification, LibreOffice headless, React 19, Ant Design 5, Axios, native browser PDF embedding.

## Global Constraints

- Work directly on `master`; do not create a branch or worktree.
- Treat `/Users/zzs/Desktop/zzs/github/wordpapergenerate` and `动态试卷.docx` as the sole layout authority.
- Do not call Node.js at runtime and do not reproduce the reference approximately with HTML/CSS.
- Migrate template and simple modes, A3/A4/B4, portrait/landscape, normal/narrow/wide/binding/custom margins, one/two columns, column spacing, binding toggle, header information, and title/subtitle/body font sizes.
- Preview and final download must use the same Java document engine and complete layout configuration.
- Preserve immutable question and layout snapshots for historical downloads.
- Template resources are immutable; generated DOCX/PDF files are transient or historical outputs, never edits to the retained reference.
- Every DOCX-affecting task ends with structural verification; the final task renders and visually inspects every page.

---

## File Map

### Source-faithful engine

- Create `AppBackend/src/main/resources/exam-paper-template/static/document.docx` by copying the source static template.
- Create `AppBackend/src/main/resources/exam-paper-template/document/document.xml` and `document.xml.rels` from the source.
- Create `AppBackend/src/main/resources/exam-paper-template/head/header1.xml` and `header2.xml` from the source.
- Create `AppBackend/src/main/java/com/example/appbackend/service/exampaper/SourcePaperLayoutResolver.java` for exact source constants and validation.
- Create `AppBackend/src/main/java/com/example/appbackend/service/exampaper/SourcePaperXmlRenderer.java` for XML helpers and question conversion.
- Create `AppBackend/src/main/java/com/example/appbackend/service/exampaper/SourcePaperTemplateEngine.java` for ZIP/package replacement.
- Create `AppBackend/src/main/java/com/example/appbackend/service/exampaper/ExamPaperDocumentDispatcher.java` to select template or simple mode.
- Modify `AppBackend/src/main/java/com/example/appbackend/service/impl/ExamPaperServiceImpl.java` to use the dispatcher.

### Layout persistence and preview

- Modify `ExamPaperDTO.java`, `ExamPaper.java`, and `ExamPaperService*` to persist the complete source layout snapshot.
- Create `ExamPaperPreviewService.java`, `ExamPaperPreviewServiceImpl.java`, `ExamPaperPreviewController.java`, and preview DTOs.
- Create `LibreOfficePreviewConverter.java` for bounded process execution.
- Modify `application.yml` with preview path, timeout, TTL, and directory configuration.

### Frontend

- Modify `AppWeb/src/api/examPaper.js` with preview lifecycle APIs.
- Create `AppWeb/src/pages/ai/ExamPaper/ExamPaperFormatPanel.jsx`.
- Create `AppWeb/src/pages/ai/ExamPaper/ExamPaperPreview.jsx`.
- Modify `ExamPaperCreate.jsx` and `ExamPaper.css` for the three-step flow.

### Tests

- Create focused tests under `AppBackend/src/test/java/com/example/appbackend/service/exampaper/`.
- Extend existing `ExamPaper*Test` controller, service, generator, and persistence tests.
- Add a repository script `scripts/verify_exam_paper_visual.py` that generates reference fixtures, renders DOCX, and writes a visual QA report under a temporary directory.

---

### Task 1: Import and lock the source template package

**Files:**
- Create: `AppBackend/src/main/resources/exam-paper-template/**`
- Create: `AppBackend/src/test/java/com/example/appbackend/service/exampaper/SourcePaperTemplateResourcesTest.java`

**Interfaces:**
- Produces classpath resource root `exam-paper-template/` with the exact source template and XML parts.

- [ ] **Step 1: Write a failing resource/package contract test**

The test must require the four source resources, open `static/document.docx` as `ZipInputStream`, and assert entries `word/document.xml`, both headers, both footers, styles, numbering, settings, and relationships. It must also parse the imported document template and assert every required placeholder occurs exactly once.

- [ ] **Step 2: Run RED**

Run: `/opt/homebrew/bin/mvn -Dtest=SourcePaperTemplateResourcesTest test`

Expected: FAIL because the resource root is absent.

- [ ] **Step 3: Copy source assets without editing them**

Copy the binary and XML assets from the reference project. Record SHA-256 constants in the test for every copied resource. The test must fail if a later edit silently changes an imported source asset.

- [ ] **Step 4: Run GREEN and commit**

Run the focused test and `git diff --check`; expect PASS.

Commit intent: `锁定试卷源码模板作为版式权威` with Lore trailers documenting the external source path and hashes.

### Task 2: Port all source layout calculations

**Files:**
- Create: `SourcePaperLayoutResolver.java`
- Create: `SourcePaperLayoutResolverTest.java`
- Modify: `ExamPaperDTO.java`

**Interfaces:**
- Produces `PaperRenderMode { TEMPLATE, SIMPLE }`, `MarginPreset { NORMAL, NARROW, WIDE, BINDING, CUSTOM }`, `PaperLayoutConfig`, `ResolvedPageLayout`, and `BindingLayoutTokens`.
- Produces `resolve(PaperLayoutConfig)` and `bindingTokens(PaperLayoutConfig)`.

- [ ] **Step 1: Write table-driven parity tests from JS constants**

Assert exact twip values: A3 `23814x16840`, A4 `16840x11907`, B4 `20639x14572`; margins normal `1134`, narrow `720`, wide horizontal `1440`, binding left/right `2500/998`; default column space `425`; document grid `312`. Test all orientations, presets, custom margins, columns 1/2, binding on/off, and the page-height-dependent header token formulas.

- [ ] **Step 2: Run RED**

Expected: compilation failure because resolver types do not exist.

- [ ] **Step 3: Implement exact resolver and validation**

Validation limits: columns 1..2; column space 0..2880; each custom margin 0..7200; title size 10..120 half-points; subtitle/body 10..72. Template A3 landscape source defaults must resolve byte-for-byte to the source `<w:pgSz>`, `<w:pgMar>`, `<w:cols>`, and `<w:docGrid>` fragments.

- [ ] **Step 4: Run GREEN and commit**

Run resolver tests plus the existing persistence contract tests.

### Task 3: Port score tables, section headers, questions, and answers to XML

**Files:**
- Create: `SourcePaperXmlRenderer.java`
- Create: `SourcePaperXmlRendererTest.java`

**Interfaces:**
- Consumes `PaperVO`, `QuestionSnapshotVO`, and resolved layout.
- Produces escaped OOXML fragments: `renderSubtitle`, `renderScoreTable`, `renderSectionHeader`, `renderQuestions`, `renderAnswers`, and `renderPageSettings`.

- [ ] **Step 1: Write failing source-parity tests**

Parse fragments as namespace-wrapped XML and assert the source values: overall score cells `1000`; grader table cell `969`; outer cells `2164/3040`; score row `520`; grader row `549`; option indent `420`; correct border styles; inline options only when max cleaned length `<15` and count `<=4`; essay blank-line count; Chinese section numbering; answer page break.

- [ ] **Step 2: Run RED**

Expected: missing renderer compilation failure.

- [ ] **Step 3: Implement minimal equivalent renderer**

Use XML-safe escaping and a deterministic HTML cleaner equivalent to the source. Convert application choice option objects to the source key/text model. Unsupported question types use the source section furniture with readable body/answer fallback; they never bypass XML escaping.

- [ ] **Step 4: Run GREEN and commit**

Run renderer and existing question snapshot tests.

### Task 4: Build and verify the template ZIP engine

**Files:**
- Create: `SourcePaperTemplateEngine.java`
- Create: `SourcePaperPackageVerifier.java`
- Create: `ExamPaperDocumentDispatcher.java`
- Modify: `ExamPaperServiceImpl.java`
- Test: `SourcePaperTemplateEngineTest.java`

**Interfaces:**
- Produces `byte[] generate(PaperVO paper, DownloadContent content, PaperLayoutConfig layout)`.
- Dispatcher uses `SourcePaperTemplateEngine` for TEMPLATE and existing `ExamPaperDocumentGenerator` for SIMPLE.

- [ ] **Step 1: Write failing package-generation tests**

Generate A3 landscape template bytes, open as ZIP, parse XML, and assert: all placeholders are removed; both headers and footers remain; header/footer references and relationships remain valid; `w:evenAndOddHeaders` exists; page settings match resolver; `%information%` and geometry tokens are replaced; answer inclusion follows content type; imported preserve-only parts keep their source hashes.

- [ ] **Step 2: Run RED**

Expected: missing engine failure.

- [ ] **Step 3: Implement ZIP replacement**

Read the immutable classpath template into memory, replace only named slots, update `word/document.xml`, both headers, and settings. Reject missing or duplicate placeholders, malformed XML, unresolved `%...%` tokens, missing required entries, and invalid relationships. Do not normalize or reserialize preserve-only XML parts.

- [ ] **Step 4: Integrate dispatcher and run GREEN**

Update download generation to use the persisted render mode/layout. Keep existing simple-mode tests and add template-mode paper/answer tests.

- [ ] **Step 5: Commit**

Commit intent: `用 Java 等价移植源码模板生成器`.

### Task 5: Persist complete layout snapshots

**Files:**
- Modify: `ExamPaper.java`, `ExamPaperDTO.java`, `ExamPaperService.java`, `ExamPaperServiceImpl.java`
- Modify tests: persistence, service, controller.

**Interfaces:**
- Create/preview share one validated `PaperLayoutRequest` nested DTO.
- `PaperVO` returns the saved layout for history detail and repeat download.

- [ ] **Step 1: Write failing contract/service tests**

Assert create copies render mode, margin preset/custom values, column space, binding flag, header info, and three font sizes into the entity and response. Modify a request after create and prove the entity snapshot is unchanged. Download must use saved values rather than defaults.

- [ ] **Step 2: Run RED**

Expected: missing layout fields/assertions fail.

- [ ] **Step 3: Implement entity/DTO mapping**

Use explicit non-null database columns for render mode, margin preset, column spacing, binding flag, and font sizes. Custom margin columns may be nullable only when the preset is not CUSTOM. Validate conditional custom margins in the service and return 400 before persistence.

- [ ] **Step 4: Run GREEN and commit**

Run all backend `ExamPaper*Test` suites.

### Task 6: Implement exact preview conversion and lifecycle

**Files:**
- Create: `LibreOfficePreviewConverter.java`
- Create: `ExamPaperPreviewService.java` and implementation
- Create: `ExamPaperPreviewDTO.java`
- Create: `ExamPaperPreviewController.java`
- Modify: `application.yml`
- Test: converter, service, and controller tests.

**Interfaces:**
- `PreviewResponse createPreview(CreateRequest request, Long userId)`.
- `PreviewFile getPreview(String token, Long userId)`.
- `void deletePreview(String token, Long userId)`.

- [ ] **Step 1: Write failing lifecycle/security tests**

Test unpredictable token format, creator-only access, 30-minute expiry, configuration/question hashes, old preview cleanup, no history persistence, cache-control headers, explicit unavailable/timeout/conversion errors, and process cleanup.

- [ ] **Step 2: Run RED**

Expected: preview types absent.

- [ ] **Step 3: Implement bounded LibreOffice converter**

Use `ProcessBuilder` argument arrays only: configured soffice path, `--headless`, `--convert-to`, `pdf`, `--outdir`. Use UUID-only filenames, a dedicated temporary profile, a configurable timeout, forced process termination on timeout, PDF signature/page-count validation, and recursive cleanup limited to the configured preview root.

- [ ] **Step 4: Implement in-memory preview metadata and scheduled cleanup**

Store token metadata in a concurrent map; store files under a user/token directory. Confirmation never trusts preview bytes. Controller returns PDF with `Cache-Control: no-store, private` and requires request `userId`.

- [ ] **Step 5: Run GREEN and commit**

Use a fake executable/process fixture for deterministic unit tests; run a real LibreOffice smoke conversion only when `soffice` is available.

### Task 7: Build the format panel and PDF preview workflow

**Files:**
- Create: `ExamPaperFormatPanel.jsx`, `ExamPaperPreview.jsx`
- Modify: `ExamPaperCreate.jsx`, `ExamPaper.css`, `examPaper.js`

**Interfaces:**
- Frontend layout object maps exactly to `PaperLayoutRequest`.
- Preview state stores token, PDF Blob URL, expiry, configuration hash, and dirty flag.

- [ ] **Step 1: Add preview APIs and safe Blob PDF handling**

Add create/get/delete functions with existing authentication, single error messaging, JSON Blob error recovery, and unconditional object URL revocation.

- [ ] **Step 2: Build the three-step create flow**

Keep existing selection in step 1. Step 2 exposes every source option and a “恢复源码默认值” action using template/A3/landscape/binding/two columns/425 spacing/source font defaults. CUSTOM reveals four margin fields.

- [ ] **Step 3: Build exact preview state invalidation**

Create a deterministic client signature from ordered question IDs/scores plus layout/form values. Any change after preview sets dirty and disables confirm. Preview success records the current signature. Confirm is enabled only when signatures match.

- [ ] **Step 4: Build the PDF viewer**

Use an object/embed element with open-in-new-tab fallback, refresh, zoom/browser controls, expiry display, loading/error/LibreOffice-unavailable states, and mobile layout. Delete the current preview when replaced or component unmounts.

- [ ] **Step 5: Validate frontend**

Run targeted ESLint for all modified files and `npm run build`; expect exit 0 except the known bundle-size warning.

- [ ] **Step 6: Commit**

Commit intent: `让页面格式调整通过真实 Word 预览闭环`.

### Task 8: Perform structural and visual source-fidelity verification

**Files:**
- Create: `scripts/verify_exam_paper_visual.py`
- Modify tests only if verification reveals defects.

**Interfaces:**
- Produces a temporary QA directory containing generated DOCX, PDF, page PNGs, source/final structural reports, and a Markdown verdict; nothing is committed except the script.

- [ ] **Step 1: Run all backend focused tests and package**

Run all `ExamPaper*Test`, `SourcePaper*Test`, preview tests, then Maven package. Expected: exit 0.

- [ ] **Step 2: Run frontend checks**

Run targeted ESLint and Vite production build. Record the existing chunk-size warning separately from failures.

- [ ] **Step 3: Generate the fixed reference fixture**

Use the same title, section counts, scores, header information, page defaults, and representative question lengths as the source example. Generate both paper and answer using TEMPLATE mode.

- [ ] **Step 4: Render every generated page**

Use the documents skill `render_docx.py` with the bundled Python runtime and inspect every page PNG at 100%. Compare against `/Users/zzs/Desktop/zzs/github/wordpapergenerate/动态试卷.docx` patterns and the supplied screenshot.

- [ ] **Step 5: Run layout matrix visual smoke tests**

Render A3/A4/B4, both orientations, one/two columns, four presets, custom margin, binding on/off, and font adjustments. Fail on clipping, overlapping, missing binding furniture, broken logical page fields, missing score/grader tables, or unresolved placeholders.

- [ ] **Step 6: Verify preview/final equivalence**

Generate preview PDF and final DOCX from identical inputs, render the final DOCX to PDF, and compare page count plus raster images. Record any LibreOffice-only rendering difference; unexplained differences fail the task.

- [ ] **Step 7: Final review and commit**

Run `git diff --check`, confirm a clean status after commits, dispatch a whole-feature review, fix all Critical/Important findings, and commit any verification fixes with Lore trailers.
