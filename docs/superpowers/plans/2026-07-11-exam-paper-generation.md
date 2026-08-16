# Exam Paper Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a management-console module that creates papers from the existing question bank by random or manual selection, stores immutable history snapshots, and downloads separate DOCX paper and answer files.

**Architecture:** Spring Boot owns selection, persistence, authorization, and on-demand document streaming. Two JPA entities preserve the paper header and question snapshots, while a focused Apache POI generator translates those snapshots into DOCX. React exposes a two-tab workflow for creation and history without introducing a second question bank.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, Apache POI XWPF, Jackson, JUnit 5/Mockito, React 19, Ant Design 5, Axios, Vite.

## Global Constraints

- Read questions only from the existing `exam_question` table; do not add decryption or Excel import.
- Support random and manual selection, score overrides, stable ordering, and duplicate prevention.
- Support A3, A4, and B4 in portrait or landscape, with one or two columns.
- Generate separate no-answer paper and answer DOCX downloads.
- Preserve historical output through immutable question snapshots; do not depend on current question-bank values during download.
- Require login and restrict history/detail/download to the creating user.
- Do not implement reusable rule templates, online Word preview, PDF export, or persistent generated files.

---

## File Map

### Backend files to create

- `AppBackend/src/main/java/com/example/appbackend/entity/ExamPaper.java`: paper metadata and audit fields.
- `AppBackend/src/main/java/com/example/appbackend/entity/ExamPaperQuestion.java`: ordered immutable question snapshot.
- `AppBackend/src/main/java/com/example/appbackend/repository/ExamPaperRepository.java`: creator-scoped history search.
- `AppBackend/src/main/java/com/example/appbackend/repository/ExamPaperQuestionRepository.java`: ordered snapshot lookup.
- `AppBackend/src/main/java/com/example/appbackend/dto/ExamPaperDTO.java`: request/response contract and validated enums.
- `AppBackend/src/main/java/com/example/appbackend/service/ExamPaperService.java`: paper use-case boundary.
- `AppBackend/src/main/java/com/example/appbackend/service/impl/ExamPaperServiceImpl.java`: selection, validation, snapshots, history and ownership.
- `AppBackend/src/main/java/com/example/appbackend/service/ExamPaperDocumentGenerator.java`: DOCX rendering only.
- `AppBackend/src/main/java/com/example/appbackend/controller/ExamPaperController.java`: authenticated JSON and download endpoints.
- `AppBackend/src/test/java/com/example/appbackend/service/impl/ExamPaperServiceImplTest.java`: selection/persistence/authorization tests.
- `AppBackend/src/test/java/com/example/appbackend/service/ExamPaperDocumentGeneratorTest.java`: DOCX package and content tests.
- `AppBackend/src/test/java/com/example/appbackend/controller/ExamPaperControllerTest.java`: endpoint and download header tests.

### Backend files to modify

- `AppBackend/pom.xml`: add `poi-ooxml` and test-visible OOXML support.
- `AppBackend/src/main/java/com/example/appbackend/repository/ExamQuestionRepository.java`: add active-candidate lookup.

### Frontend files to create

- `AppWeb/src/api/examPaper.js`: paper API and authenticated Blob download helpers.
- `AppWeb/src/pages/ai/ExamPaper/ExamPaper.jsx`: orchestration and tab shell.
- `AppWeb/src/pages/ai/ExamPaper/ExamPaperCreate.jsx`: metadata, selection and selected-question editing.
- `AppWeb/src/pages/ai/ExamPaper/ExamPaperHistory.jsx`: history, detail and downloads.
- `AppWeb/src/pages/ai/ExamPaper/ExamPaper.css`: responsive module styling.

### Frontend files to modify

- `AppWeb/src/App.jsx`: register `/ai/exam-papers`.
- `AppWeb/src/data/portalData.js`: add menu and module-card entries.

---

### Task 1: Lock the persistence and API contract

**Files:**
- Create: `AppBackend/src/main/java/com/example/appbackend/entity/ExamPaper.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/entity/ExamPaperQuestion.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/dto/ExamPaperDTO.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/repository/ExamPaperRepository.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/repository/ExamPaperQuestionRepository.java`
- Test: `AppBackend/src/test/java/com/example/appbackend/entity/ExamPaperPersistenceContractTest.java`

**Interfaces:**
- Produces: `ExamPaperDTO.CreateRequest`, `RandomPreviewRequest`, `PaperVO`, `QuestionSnapshotVO`, `PaperPageVO`, and enums `PageSize`, `Orientation`, `SelectionMode`, `DownloadContent`.
- Produces: `ExamPaperRepository.findByCreatedByAndStatusOrderByCreateTimeDesc(Long, Integer, Pageable)` and `ExamPaperQuestionRepository.findByPaperIdOrderBySortOrderAsc(Long)`.

- [ ] **Step 1: Write a failing reflection contract test**

```java
@Test
void paperContractContainsStableSnapshotAndLayoutFields() throws Exception {
    assertNotNull(ExamPaper.class.getDeclaredField("pageSize"));
    assertNotNull(ExamPaper.class.getDeclaredField("orientation"));
    assertNotNull(ExamPaper.class.getDeclaredField("columnsCount"));
    assertNotNull(ExamPaperQuestion.class.getDeclaredField("bodyJson"));
    assertNotNull(ExamPaperQuestion.class.getDeclaredField("answerJson"));
    assertNotNull(ExamPaperQuestion.class.getDeclaredField("sortOrder"));
}
```

- [ ] **Step 2: Run the contract test and verify it fails**

Run: `cd AppBackend && ./mvnw -Dtest=ExamPaperPersistenceContractTest test`

Expected: compilation fails because `ExamPaper` and `ExamPaperQuestion` do not exist.

- [ ] **Step 3: Implement entities, validated DTOs, and repositories**

Use `@Entity`, `@Table`, Lombok `@Data`, `@PrePersist`, and the same `LocalDateTime` audit pattern as `ExamQuestion`. Validate request fields with these exact limits:

```java
@NotBlank @Size(max = 160) private String title;
@Size(max = 200) private String subtitle;
@Min(1) @Max(1440) private Integer durationMinutes;
@Size(max = 2000) private String precautions;
@Size(max = 300) private String headerInfo;
@NotNull private PageSize pageSize;
@NotNull private Orientation orientation;
@Min(1) @Max(2) private Integer columnsCount;
@NotNull private SelectionMode selectionMode;
@NotEmpty private List<SelectedQuestion> questions;
```

`SelectedQuestion` contains `@NotNull Long questionId`, `@NotNull @DecimalMin("0.01") BigDecimal score`, and `@NotNull @Min(1) Integer sortOrder`. Persist snapshot JSON as `LONGTEXT`. Add unique constraint `(paper_id, question_id)`.

- [ ] **Step 4: Run the contract test**

Run: `cd AppBackend && ./mvnw -Dtest=ExamPaperPersistenceContractTest test`

Expected: PASS.

- [ ] **Step 5: Commit the contract slice**

```bash
git add AppBackend/src/main/java/com/example/appbackend/entity/ExamPaper.java AppBackend/src/main/java/com/example/appbackend/entity/ExamPaperQuestion.java AppBackend/src/main/java/com/example/appbackend/dto/ExamPaperDTO.java AppBackend/src/main/java/com/example/appbackend/repository/ExamPaperRepository.java AppBackend/src/main/java/com/example/appbackend/repository/ExamPaperQuestionRepository.java AppBackend/src/test/java/com/example/appbackend/entity/ExamPaperPersistenceContractTest.java
git commit -m "为稳定复现历史试卷建立快照契约"
```

### Task 2: Implement random selection and paper creation

**Files:**
- Modify: `AppBackend/src/main/java/com/example/appbackend/repository/ExamQuestionRepository.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/ExamPaperService.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/impl/ExamPaperServiceImpl.java`
- Test: `AppBackend/src/test/java/com/example/appbackend/service/impl/ExamPaperServiceImplTest.java`

**Interfaces:**
- Consumes: Task 1 DTOs and repositories.
- Produces: `PaperVO randomPreview(RandomPreviewRequest request)`, `PaperVO create(CreateRequest request, Long userId)`, `PageResponse<PaperVO> list(Integer current, Integer size, Long userId)`, and `PaperVO detail(Long id, Long userId)`.

- [ ] **Step 1: Write failing random-preview tests**

```java
@Test
void randomPreviewSelectsWithoutReplacement() {
    when(questionRepository.findActiveCandidates("single_choice", "easy"))
        .thenReturn(List.of(question(1L), question(2L), question(3L)));
    var result = service.randomPreview(preview("single_choice", "easy", 2));
    assertEquals(2, result.getQuestions().size());
    assertEquals(2, result.getQuestions().stream().map(QuestionSnapshotVO::getQuestionId).distinct().count());
}

@Test
void randomPreviewRejectsInsufficientCandidates() {
    when(questionRepository.findActiveCandidates("single_choice", "easy"))
        .thenReturn(List.of(question(1L)));
    assertThrows(BusinessException.class,
        () -> service.randomPreview(preview("single_choice", "easy", 2)));
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd AppBackend && ./mvnw -Dtest=ExamPaperServiceImplTest test`

Expected: FAIL because the service and repository candidate method are absent.

- [ ] **Step 3: Add active candidate query and no-replacement selection**

Add:

```java
@Query("SELECT q FROM ExamQuestion q WHERE q.status = 1 AND q.type = :type AND (:difficulty IS NULL OR q.difficulty = :difficulty)")
List<ExamQuestion> findActiveCandidates(@Param("type") String type,
                                        @Param("difficulty") String difficulty);
```

Shuffle a defensive copy using an injected/package-visible `RandomGenerator`, reject shortages before taking `subList`, and return lightweight candidate VOs without saving.

- [ ] **Step 4: Write failing creation tests**

Cover duplicate IDs, missing/inactive IDs, score override, server-calculated total, stable sort order, and snapshot copying. The principal assertion is:

```java
assertEquals(new BigDecimal("12.50"), savedPaper.getTotalScore());
assertEquals("original body", savedSnapshots.get(0).getBodyJson());
assertEquals(1, savedSnapshots.get(0).getSortOrder());
```

- [ ] **Step 5: Implement transactional creation and creator-scoped reads**

Use `@Transactional` for `create`, `findAllById` plus cardinality/status checks, a `HashSet<Long>` for duplicates, server-side `BigDecimal` summation, and `rawQuestionJson`/structured columns as immutable snapshots. `detail` must throw `BusinessException(FORBIDDEN_CODE, "无权访问该试卷")` when `createdBy` differs.

- [ ] **Step 6: Run service tests**

Run: `cd AppBackend && ./mvnw -Dtest=ExamPaperServiceImplTest test`

Expected: PASS.

- [ ] **Step 7: Commit service behavior**

```bash
git add AppBackend/src/main/java/com/example/appbackend/repository/ExamQuestionRepository.java AppBackend/src/main/java/com/example/appbackend/service/ExamPaperService.java AppBackend/src/main/java/com/example/appbackend/service/impl/ExamPaperServiceImpl.java AppBackend/src/test/java/com/example/appbackend/service/impl/ExamPaperServiceImplTest.java
git commit -m "让组卷结果可校验且可稳定追溯"
```

### Task 3: Generate paper and answer DOCX in pure Java

**Files:**
- Modify: `AppBackend/pom.xml`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/ExamPaperDocumentGenerator.java`
- Test: `AppBackend/src/test/java/com/example/appbackend/service/ExamPaperDocumentGeneratorTest.java`

**Interfaces:**
- Consumes: `ExamPaperDTO.PaperVO` and ordered `QuestionSnapshotVO`.
- Produces: `byte[] generate(PaperVO paper, DownloadContent content)`.

- [ ] **Step 1: Add Apache POI dependency and a failing smoke test**

Add `org.apache.poi:poi-ooxml:5.5.1` (the current stable release published by Apache POI). Write a test that calls `generate`, opens the bytes with `XWPFDocument`, and asserts the title and first stem exist.

```java
try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
    String text = document.getParagraphs().stream().map(XWPFParagraph::getText).collect(joining("\n"));
    assertTrue(text.contains("期末考试"));
    assertTrue(text.contains("第一道题"));
}
```

- [ ] **Step 2: Run the generator test and verify failure**

Run: `cd AppBackend && ./mvnw -Dtest=ExamPaperDocumentGeneratorTest test`

Expected: FAIL because the generator is absent.

- [ ] **Step 3: Implement layout configuration**

Map page sizes in twips: A3 `16838 x 23811`, A4 `11906 x 16838`, B4 `14173 x 20013`. Swap width/height for landscape, set `STPageOrientation.LANDSCAPE`, and configure `CTColumns` with `num=1|2`, `space=425`, and `sep=true` for two columns.

- [ ] **Step 4: Implement content renderers**

Create focused private methods: `writeHeader`, `groupQuestions`, `writeSectionHeading`, `writeQuestion`, `writeOptions`, `writeAnswer`, `readJson`, and `safeFileText`. Render choice options from `body.options`; render true/false answers; render unknown structured answers as readable Jackson pretty JSON. `PAPER` never calls `writeAnswer`; `ANSWER` keeps identical sequential numbers and includes analysis when present.

- [ ] **Step 5: Add parameterized layout and separation tests**

Test all `PageSize x Orientation` combinations, columns `1` and `2`, and assert paper bytes exclude `标准答案` while answer bytes include it. Inspect `CTSectPr` values after reopening the DOCX.

- [ ] **Step 6: Run generator tests**

Run: `cd AppBackend && ./mvnw -Dtest=ExamPaperDocumentGeneratorTest test`

Expected: PASS for all parameterized cases.

- [ ] **Step 7: Commit DOCX generation**

```bash
git add AppBackend/pom.xml AppBackend/src/main/java/com/example/appbackend/service/ExamPaperDocumentGenerator.java AppBackend/src/test/java/com/example/appbackend/service/ExamPaperDocumentGeneratorTest.java
git commit -m "用纯 Java 提供可配置的试卷文档输出"
```

### Task 4: Expose authenticated paper APIs and downloads

**Files:**
- Create: `AppBackend/src/main/java/com/example/appbackend/controller/ExamPaperController.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/ExamPaperService.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/ExamPaperServiceImpl.java`
- Test: `AppBackend/src/test/java/com/example/appbackend/controller/ExamPaperControllerTest.java`

**Interfaces:**
- Consumes: Tasks 2 and 3.
- Produces: `/api/exam/papers/random-preview`, create/list/detail, and `/download?content=paper|answer`.

- [ ] **Step 1: Write failing MockMvc tests**

Test missing `userId` returns the project-standard unauthorized result, create delegates with the request attribute, and download returns:

```java
.andExpect(status().isOk())
.andExpect(header().string(HttpHeaders.CONTENT_TYPE,
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
```

- [ ] **Step 2: Run controller tests and verify failure**

Run: `cd AppBackend && ./mvnw -Dtest=ExamPaperControllerTest test`

Expected: FAIL because routes are not registered.

- [ ] **Step 3: Implement controller and download response**

Use `getUserId` identical to `ExamQuestionController`. Sanitize filename to `[\\/:*?\"<>|\\r\\n]`, encode with `ContentDisposition.attachment().filename(name, UTF_8)`, and return `ResponseEntity<byte[]>`. Accept only enum values `paper` and `answer` case-insensitively; invalid content returns 400.

- [ ] **Step 4: Run controller and service tests**

Run: `cd AppBackend && ./mvnw -Dtest=ExamPaperControllerTest,ExamPaperServiceImplTest test`

Expected: PASS.

- [ ] **Step 5: Commit the HTTP boundary**

```bash
git add AppBackend/src/main/java/com/example/appbackend/controller/ExamPaperController.java AppBackend/src/main/java/com/example/appbackend/service/ExamPaperService.java AppBackend/src/main/java/com/example/appbackend/service/impl/ExamPaperServiceImpl.java AppBackend/src/test/java/com/example/appbackend/controller/ExamPaperControllerTest.java
git commit -m "让登录用户安全创建并下载历史试卷"
```

### Task 5: Add the frontend API and creation workflow

**Files:**
- Create: `AppWeb/src/api/examPaper.js`
- Create: `AppWeb/src/pages/ai/ExamPaper/ExamPaper.jsx`
- Create: `AppWeb/src/pages/ai/ExamPaper/ExamPaperCreate.jsx`
- Create: `AppWeb/src/pages/ai/ExamPaper/ExamPaper.css`

**Interfaces:**
- Consumes: existing `getExamQuestionList`; Task 4 REST routes.
- Produces: creation tab with `onCreated(paper)` callback.

- [ ] **Step 1: Implement the API adapter**

Export `randomPreviewExamPaper(data)`, `createExamPaper(data)`, `getExamPaperList(params)`, `getExamPaperDetail(id)`, and `downloadExamPaper(id, content)`. The download helper must request `responseType: 'blob'`, create an object URL, click a temporary anchor, and revoke the URL in `finally`.

- [ ] **Step 2: Build metadata and layout form**

Use exact initial values: `pageSize: 'A4'`, `orientation: 'portrait'`, `columnsCount: 1`, `durationMinutes: 60`, `selectionMode: 'manual'`. Validate title, duration, and at least one selected question before calling `createExamPaper`.

- [ ] **Step 3: Build random selection rows**

Use `Form.List name="rules"`; each row has type, optional difficulty, and quantity. Map the response into selected rows keyed by `questionId`; reject duplicates locally and display the backend shortage message unchanged.

- [ ] **Step 4: Build manual selection**

Render the existing paginated question list in a modal/drawer. Preserve selections in a `Map<number, Question>` across pagination, disable already-selected rows, and initialize selected score from `question.score`.

- [ ] **Step 5: Build selected-question editing**

Render order, type, stem, editable `InputNumber` score, up/down actions and remove. Derive totals with `useMemo` from selected rows; when submitting, renumber `sortOrder` from array position rather than trusting stale UI values.

- [ ] **Step 6: Run frontend lint and build**

Run: `cd AppWeb && npm run lint && npm run build`

Expected: both commands exit 0; Vite emits `dist` assets.

- [ ] **Step 7: Commit creation UI**

```bash
git add AppWeb/src/api/examPaper.js AppWeb/src/pages/ai/ExamPaper/ExamPaper.jsx AppWeb/src/pages/ai/ExamPaper/ExamPaperCreate.jsx AppWeb/src/pages/ai/ExamPaper/ExamPaper.css
git commit -m "让管理员从现有题库完成可编辑组卷"
```

### Task 6: Add history, routing, and menu entry

**Files:**
- Create: `AppWeb/src/pages/ai/ExamPaper/ExamPaperHistory.jsx`
- Modify: `AppWeb/src/pages/ai/ExamPaper/ExamPaper.jsx`
- Modify: `AppWeb/src/App.jsx`
- Modify: `AppWeb/src/data/portalData.js`
- Modify: `AppWeb/src/pages/ai/ExamPaper/ExamPaper.css`

**Interfaces:**
- Consumes: `getExamPaperList`, `getExamPaperDetail`, and `downloadExamPaper`.
- Produces: reachable `/ai/exam-papers` module and creator-scoped history controls.

- [ ] **Step 1: Build history pagination and detail drawer**

Render title, layout, selection mode, question count, total score, creator time, and actions. The detail drawer lists stable snapshot order and per-paper score; it must not refetch current question-bank details.

- [ ] **Step 2: Add separate download actions**

Add “下载试卷” with `content='paper'` and “下载答案” with `content='answer'`; maintain independent loading keys `${id}:paper` and `${id}:answer` to prevent double clicks without blocking other rows.

- [ ] **Step 3: Register route and navigation**

Import `ExamPaper` in `App.jsx`, add `<Route path="/ai/exam-papers" element={<ExamPaper />} />`, insert `{ path: '/ai/exam-papers', label: '试卷生成', icon: 'file-text' }` immediately after题库管理, and add a module card with description “从现有题库随机或手工组卷并下载 Word 试卷”。

- [ ] **Step 4: Add responsive styling**

At widths below `768px`, stack form rows, make tables horizontally scrollable, and make primary creation/download controls full width. Reuse existing page hero/card visual language.

- [ ] **Step 5: Run frontend checks**

Run: `cd AppWeb && npm run lint && npm run build`

Expected: both commands exit 0 and the route is present in the built bundle.

- [ ] **Step 6: Commit history and navigation**

```bash
git add AppWeb/src/pages/ai/ExamPaper/ExamPaperHistory.jsx AppWeb/src/pages/ai/ExamPaper/ExamPaper.jsx AppWeb/src/pages/ai/ExamPaper/ExamPaper.css AppWeb/src/App.jsx AppWeb/src/data/portalData.js
git commit -m "让已生成试卷可追溯并可重复下载"
```

### Task 7: End-to-end verification and documentation alignment

**Files:**
- Modify if evidence requires: files introduced in Tasks 1-6.
- Verify: `docs/superpowers/specs/2026-07-11-exam-paper-generation-design.md`

**Interfaces:**
- Consumes: complete backend and frontend.
- Produces: fresh completion evidence with no known test, lint, or build failures.

- [ ] **Step 1: Run targeted backend tests**

Run: `cd AppBackend && ./mvnw -Dtest=ExamPaperPersistenceContractTest,ExamPaperServiceImplTest,ExamPaperDocumentGeneratorTest,ExamPaperControllerTest test`

Expected: all paper-module tests PASS.

- [ ] **Step 2: Run full backend test suite and package**

Run: `cd AppBackend && ./mvnw test`

Run: `cd AppBackend && ./mvnw -DskipTests package`

Expected: `BUILD SUCCESS` for both commands.

- [ ] **Step 3: Run frontend checks**

Run: `cd AppWeb && npm run lint`

Run: `cd AppWeb && npm run build`

Expected: both exit 0.

- [ ] **Step 4: Verify representative DOCX packages**

Generate A3 landscape/two-column paper and A4 portrait/one-column answer bytes in the generator test, save only under the test temp directory, and reopen them with `XWPFDocument`. Assert `word/document.xml` exists, page width/orientation/column count match, paper excludes answers, and answer includes answers.

- [ ] **Step 5: Check the final diff**

Run: `git diff --check`

Run: `git status --short`

Expected: no whitespace errors; only intentional implementation files are modified.

- [ ] **Step 6: Commit any verification-only fixes**

```bash
git add AppBackend AppWeb
git commit -m "确保试卷模块通过完整构建与文档校验"
```

Skip this commit when verification requires no changes.
