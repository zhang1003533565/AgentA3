# 学习计划拆解升级 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有 AI 学习计划拆解升级为可编辑、可排程、可按今日执行并支持延期/部分完成的学习计划。

**Architecture:** 保留现有 UniApp -> Spring Boot -> FastAPI AI 链路。AI 仍只负责生成目标和任务草稿；Java 服务负责日期排程、状态约束、加权进度和用户归属；前端负责编辑预览、执行视图和轻量交互。旧数据的日期为空时继续按旧清单逻辑工作。

**Tech Stack:** UniApp/Vue3, Spring Boot 4/JPA, MySQL, FastAPI/Python, JUnit/Spring test starter, pytest。

**Spec:** `docs/superpowers/specs/2026-08-27-study-goal-planning-design.md`

## Global Constraints

- 只修改学习计划相关文件，不触碰现有 PPT 未提交改动。
- 每个独立功能完成并验证后单独 `git commit`，只 commit 不 push。
- 数据库查询使用参数绑定；用户归属继续使用 `findByIdAndUserId`。
- 不引入 eval/new Function/vm 等动态执行。
- UI 延续浅灰背景、白色表面、深色文字和低饱和蓝灰强调色。
- 旧目标和旧任务必须可读取；新增日期字段允许为空。
- 静态检查、编译、单测、真实服务冒烟和截图验证分别报告，不混称为同一种验证。

### Task 1: Extend the persisted planning contract

**Files:**
- Modify: `AppBackend/src/main/java/com/example/appbackend/entity/StudyGoal.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/entity/StudyTask.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/dto/StudyGoalDTO.java`
- Modify: `AppBackend/src/main/resources/db/study-goal-schema.sql`
- Test: `AppBackend/src/test/java/com/example/appbackend/entity/StudyGoalPersistenceContractTest.java`

**Interfaces:**
- Goal fields: `LocalDate startDate`, `LocalDate targetDate`.
- Task fields: `LocalDate plannedStartDate`, `LocalDate plannedEndDate`, `Integer progressPercent`.
- Allowed task states: `pending`, `in_progress`, `blocked`, `skipped`, `completed`.
- DTO date fields serialize as ISO `yyyy-MM-dd`; old null values remain valid.

- [ ] Write a failing persistence contract test proving the new fields map to the expected column names and default progress is zero.
- [ ] Run `mvn -o -q -Dtest=StudyGoalPersistenceContractTest test` and verify it fails because the fields/columns are absent.
- [ ] Add nullable date columns and `progress_percent` with a zero default; update entity comments and DTO request/view types.
- [ ] Run the focused test and verify it passes, then run the existing entity persistence tests.
- [ ] Commit with `feat: 扩展学习计划排程数据契约`.

### Task 2: Add scheduling, weighted progress, status, and postpone behavior

**Files:**
- Modify: `AppBackend/src/main/java/com/example/appbackend/controller/StudyGoalController.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/StudyGoalService.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/StudyGoalServiceImpl.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/repository/StudyTaskRepository.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/exception/GlobalExceptionHandler.java` only if a new validation error needs mapping
- Test: `AppBackend/src/test/java/com/example/appbackend/service/impl/StudyGoalServiceImplTest.java`

**Interfaces:**
- `saveGoal` accepts goal dates and schedules tasks sequentially from `startDate` using `estimatedDays`; when no start date is supplied, use `LocalDate.now()`.
- `updateTaskCompletion(taskId, isCompleted, userId)` keeps the existing signature and maps false to `in_progress` when partial progress exists, otherwise `pending`.
- Add `updateTaskProgress(taskId, progressPercent, userId)` returning the updated `GoalView`.
- Add `updateTaskStatus(taskId, status, userId)` returning the updated `GoalView`.
- Add `postponeTask(taskId, days, userId)` returning the updated `GoalView`; shift the selected task and all later unfinished tasks by `days`.
- Progress is `round(sum(estimatedDays * progressPercent) / sum(estimatedDays))`; status is derived from task states/progress.

- [ ] Write failing service tests for sequential dates, weighted progress, status transitions, ownership rejection, and postponing unfinished tasks.
- [ ] Run the focused Java test and verify each failure is caused by the missing behavior.
- [ ] Implement small private helpers for date scheduling, weighted progress, status normalization, and owned-task lookup; preserve existing response shapes plus the new fields.
- [ ] Add controller routes `PUT /tasks/{taskId}/progress`, `PUT /tasks/{taskId}/status`, and `POST /tasks/{taskId}/postpone` with bounded inputs.
- [ ] Run focused tests, then the existing backend test suite relevant to study goals and compile offline.
- [ ] Commit with `feat: 增加学习计划排程与执行状态`.

### Task 3: Align AI and input safeguards

**Files:**
- Modify: `ai-servers/app/multi_agents/goal_decomposition_agent/prompt.md`
- Modify: `ai-servers/app/multi_agents/goal_decomposition_agent/agent.py`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/StudyGoalServiceImpl.java`
- Modify: `mini_program_app/api/studyGoal.js`
- Test: `ai-servers/tests/test_goal_decomposition_agent.py`

**Interfaces:**
- Keep the AI response contract `goal + tasks`; do not make the model responsible for persistence dates.
- Parser and prompt both enforce 3-30 tasks and the same title length.
- Oversized serialized file input returns a clear 400 instead of silent truncation.
- Frontend API wrappers expose progress, status, and postpone calls with the same Result<T> convention.

- [ ] Add failing parser tests for the 3-30 task contract and title limit consistency.
- [ ] Add a backend test for rejecting content over `MAX_CONTENT_CHARS` without invoking the AI proxy.
- [ ] Run the focused pytest/Java tests and confirm red failures.
- [ ] Align constants and validation, replacing silent truncation with an actionable error.
- [ ] Run `pytest -q ai-servers/tests/test_goal_decomposition_agent.py` and the focused backend test.
- [ ] Commit with `fix: 收紧学习计划拆解契约与输入限制`.

### Task 4: Build an editable and date-aware preview flow

**Files:**
- Modify: `mini_program_app/subpackage_ai/goalDecompose/goalDecompose.vue`
- Modify: `mini_program_app/api/studyGoal.js`
- Create: `mini_program_app/utils/studyGoalPlan.js`
- Test: `mini_program_app/utils/studyGoalPlan.test.js` if the repository test runner supports the existing UniApp test setup

**Interfaces:**
- Pure helpers: `normalizeDraftTask`, `createEmptyDraftTask`, `moveDraftTask`, `validatePlanDates`, `isTaskDueToday`, `formatPlanDate`.
- Preview supports editing title/description/task fields, add/delete/reorder, and date selection.
- Text/file source selection is mutually exclusive; selecting a file clears text and returning to input clears the previous draft source.
- Save payload includes `startDate` and `targetDate`; task edits are sent unchanged except for final normalization.

- [ ] Add failing pure-helper tests for task ordering, date validation, today matching, and old-data normalization.
- [ ] Run the focused frontend test and verify red failure.
- [ ] Implement helpers and wire the preview template to local draft state.
- [ ] Add clear error/retry states for decompose, detail load, and save; preserve the current draft on failure.
- [ ] Fix the saved goal badge to use the actual goal status and reset stale input after creating a new plan.
- [ ] Run the available frontend tests and a Vite/UniApp static build check.
- [ ] Commit with `feat: 支持学习计划预览编辑与日期排程`.

### Task 5: Add daily execution and plan-list visibility

**Files:**
- Modify: `mini_program_app/subpackage_ai/goalDecompose/goalDecompose.vue`
- Modify: `mini_program_app/subpackage_ai/goalPlanList/goalPlanList.vue`
- Modify: `mini_program_app/api/studyGoal.js`

**Interfaces:**
- Detail filters: `all`, `today`, `pending`, `completed`.
- Task row shows date range, progress, and state; today shows unfinished tasks whose planned date range covers today.
- Task actions expose partial progress, status selection, and postpone-one-day.
- Plan list shows date range and retains pagination/history behavior.

- [ ] Add/extend frontend helper tests for today filtering and status labels.
- [ ] Implement the smallest mobile-friendly controls using the existing light-gray/white card system.
- [ ] Keep “再拆解一份计划” as a clear reset action and keep history navigation intact.
- [ ] Run static checks and manually exercise the authenticated flow if the runtime account is available.
- [ ] Commit with `feat: 增加学习计划今日执行视图`.

### Task 6: Verify, deploy, and report

**Files:**
- No source changes unless verification exposes a regression.
- Inspect: all files changed by Tasks 1-5.

- [ ] Run `git diff --check` scoped to the changed files.
- [ ] Run Python tests for the AI agent.
- [ ] Run offline Java compile and relevant Java tests with the project JDK.
- [ ] Run the frontend build/static checks available in `mini_program_app`.
- [ ] Run the real smoke script against isolated service ports only if the current environment permits, never killing the user's 8080/8081 services.
- [ ] Capture before/after screenshots of the actual authenticated flow when a supported browser is available; otherwise report the limitation explicitly.
- [ ] Review `git diff` and `git status --short`; verify unrelated PPT files remain untouched and unstaged.
- [ ] Deploy the verified frontend artifact to the actual runtime directory only after the user-facing build succeeds, then report the exact path.
