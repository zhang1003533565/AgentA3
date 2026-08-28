# 学习计划拆解质量优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复细分任务传输丢失问题，并将学习计划拆解优化为可执行、可验收、可补全的任务树。

**Architecture:** 保留现有 Goal、StudyTask、StudySubtask 三层数据模型。先在 Python 内部接口修复嵌套响应契约并增加质量门禁，再由 Java 提供旧计划补全能力，最后调整 UniApp 预览和执行页表达阶段与叶子任务的区别。

**Tech Stack:** FastAPI/Pydantic、Python LLM provider、Spring Boot/JPA、UniApp/Vue3、JUnit/Mockito、pytest。

**Spec:** `docs/superpowers/specs/2026-08-27-study-plan-quality-design.md`

## Global Constraints

- 保留现有 `Goal -> StudyTask -> StudySubtask` 数据模型，不新增第三层。
- 多日父任务必须具备 2~6 个可执行细分任务；一天原子任务允许保持叶子兼容形态。
- AI 输出不合格时最多自动修正一次，仍不合格不得保存。
- 历史计划补全只补缺失细分任务，不覆盖已有进度。
- 不修改工作区中既有 PPT 改动，不引入 eval/new Function/vm。
- 每个独立功能阶段完成并验证后单独使用中文 subject 提交，不 push。

### Task 1: 修复 Python 嵌套响应契约

**Files:**
- Modify: `ai-servers/app/api/routes/goal.py`
- Test: `ai-servers/tests/test_goal_decomposition_agent.py`

**Interfaces:**
- Produces: `GoalDecomposeResponse` whose `TaskPlan` includes `subtasks: List[SubtaskPlan]` and preserves snake_case fields through FastAPI response validation.

- [ ] **Step 1: Add a failing response-contract test**

  Build a `GoalDecomposeResponse` from a nested task and assert the nested item remains available through `response.tasks[0].subtasks[0].task_name`.

- [ ] **Step 2: Run the focused pytest and confirm the contract fails before the model change**

  Run: `uv run pytest ai-servers/tests/test_goal_decomposition_agent.py -q`

- [ ] **Step 3: Define `SubtaskPlan` and add it to `TaskPlan` with a default empty list**

  Keep `task_name`, `description`, `estimated_days`, and `order_num` in the internal snake_case contract.

- [ ] **Step 4: Run the focused pytest and confirm nested response preservation**

  Run: `uv run pytest ai-servers/tests/test_goal_decomposition_agent.py -q`

- [ ] **Step 5: Commit**

  `git add ai-servers/app/api/routes/goal.py ai-servers/tests/test_goal_decomposition_agent.py && git commit -m "fix:修复学习计划细分任务响应契约"`

### Task 2: 强化 AI 任务拆解规则与质量门禁

**Files:**
- Modify: `ai-servers/app/multi_agents/goal_decomposition_agent/agent.py`
- Modify: `ai-servers/app/multi_agents/goal_decomposition_agent/prompt.md`
- Modify: `ai-servers/app/services/goal_ai_service.py`
- Test: `ai-servers/tests/test_goal_decomposition_agent.py`

**Interfaces:**
- Produces: `validate_plan_quality(payload, source_type) -> str | None` and a single repair attempt inside `GoalDecompositionAIService.decompose`.

- [ ] **Step 1: Add failing quality tests**

  Cover a multi-day parent without subtasks, duplicate child names, missing completion descriptions, and mismatched child-day totals. Also cover a one-day atomic task as valid.

- [ ] **Step 2: Run focused pytest and confirm the new tests fail**

  Run: `uv run pytest ai-servers/tests/test_goal_decomposition_agent.py -q`

- [ ] **Step 3: Implement deterministic plan validation and strengthen the system prompt**

  Validate only normalized output; preserve compatibility for direct parser tests while enforcing the gate in the AI service.

- [ ] **Step 4: Implement one model repair attempt with explicit validation feedback**

  Reuse the active provider/config, include the exact quality failure in the repair prompt, and reject the result if the second parse remains invalid.

- [ ] **Step 5: Run all goal-agent tests**

  Run: `uv run pytest ai-servers/tests/test_goal_decomposition_agent.py ai-servers/tests/test_goal_decomposition_route.py -q`

- [ ] **Step 6: Commit**

  `git add ai-servers/app/multi_agents/goal_decomposition_agent/agent.py ai-servers/app/multi_agents/goal_decomposition_agent/prompt.md ai-servers/app/services/goal_ai_service.py ai-servers/tests/test_goal_decomposition_agent.py && git commit -m "feat:增加学习计划拆解质量门禁"`

### Task 3: 增加旧计划细分任务补全接口

**Files:**
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/StudyGoalService.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/StudyGoalServiceImpl.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/controller/StudyGoalController.java`
- Test: `AppBackend/src/test/java/com/example/appbackend/service/impl/StudyGoalServiceImplTest.java`

**Interfaces:**
- Produces: `StudyGoalService.expandMissingSubtasks(Long goalId, Long userId, String authorization) -> StudyGoalDTO.GoalDetail`.
- Produces: `POST /api/study-goal/{goalId}/expand-subtasks` returning the refreshed goal detail.

- [ ] **Step 1: Add failing service tests**

  Cover owned legacy tasks receiving generated subtasks, existing subtasks remaining untouched, and another user receiving 404 before any AI or repository mutation.

- [ ] **Step 2: Run the focused Maven test and confirm the new tests fail**

  Run from `AppBackend`: `mvn -q -Dtest=StudyGoalServiceImplTest -DfailIfNoTests=false test`

- [ ] **Step 3: Build the legacy expansion input and call the existing Python AI proxy**

  Include goal title/description and numbered parent task names/descriptions/days. Ask the model to preserve each parent and return only missing child steps.

- [ ] **Step 4: Save only missing subtasks and reschedule affected parent/task windows**

  Keep existing subtask records and progress. Use the existing `buildSubtaskEntity`, schedule, and ownership helpers.

- [ ] **Step 5: Add the controller endpoint with Authorization forwarding**

  Return `Result<StudyGoalDTO.GoalDetail>` and use `currentUserId` for ownership isolation.

- [ ] **Step 6: Run focused test and backend compile**

  Run: `mvn -q -Dtest=StudyGoalServiceImplTest -DfailIfNoTests=false test` and `mvn -q -DskipTests compile`.

- [ ] **Step 7: Commit**

  `git add AppBackend/src/main/java/com/example/appbackend/service/StudyGoalService.java AppBackend/src/main/java/com/example/appbackend/service/impl/StudyGoalServiceImpl.java AppBackend/src/main/java/com/example/appbackend/controller/StudyGoalController.java AppBackend/src/test/java/com/example/appbackend/service/impl/StudyGoalServiceImplTest.java && git commit -m "feat:支持旧学习计划补全细分任务"`

### Task 4: 优化预览与执行页面

**Files:**
- Modify: `mini_program_app/api/studyGoal.js`
- Modify: `mini_program_app/subpackage_ai/goalDecompose/goalDecompose.vue`
- Modify: `mini_program_app/utils/studyPlan.js`
- Test: `mini_program_app/subpackage_ai/goalDecompose/goalDecompose.structure.test.js` and related study-plan tests

**Interfaces:**
- Consumes: `POST /api/study-goal/{goalId}/expand-subtasks`.
- Produces: visible phase headers, child execution rows, legacy repair banner/action, and leaf-only counters.

- [ ] **Step 1: Add failing structure assertions**

  Assert the page has explicit phase/child labels, the repair action, and the API import; assert the normalization path keeps nested children.

- [ ] **Step 2: Run frontend structure tests and confirm the new assertions fail**

  Run from `mini_program_app`: `node --test utils/studyPlan.test.js subpackage_ai/goalDecompose/goalDecompose.structure.test.js`

- [ ] **Step 3: Add the repair API and legacy-plan state**

  Show the repair action only when at least one parent has no subtasks and confirm before calling the endpoint.

- [ ] **Step 4: Make the hierarchy explicit in preview and execution states**

  Keep the parent as a non-checkbox group, label child rows as executable steps, and retain leaf-only progress/count behavior.

- [ ] **Step 5: Run frontend tests and H5 transformation check**

  Run the node tests and request `http://127.0.0.1:5173/subpackage_ai/goalDecompose/goalDecompose.vue` with curl, expecting HTTP 200.

- [ ] **Step 6: Commit**

  `git add mini_program_app/api/studyGoal.js mini_program_app/subpackage_ai/goalDecompose/goalDecompose.vue mini_program_app/utils/studyPlan.js mini_program_app/subpackage_ai/goalDecompose/goalDecompose.structure.test.js && git commit -m "feat:优化学习计划任务树展示"`

### Task 5: 全链路回归与交付

**Files:**
- Verify only; do not include existing PPT worktree changes.

- [ ] **Step 1: Run Python goal-agent tests and backend focused tests**

- [ ] **Step 2: Run backend compile and frontend H5 request checks**

- [ ] **Step 3: Run the study-goal smoke script against an isolated 8082/8083 pair when available**

  Verify nested output, save, leaf progress aggregation, legacy expansion, delete, ownership isolation, and history list behavior.

- [ ] **Step 4: Record runtime handoff requirements**

  The resident 8080/8081 services must be restarted with the new code, and the UniApp developer tool must recompile changed pages.

- [ ] **Step 5: Commit any final test-only adjustments separately**

  Use a Chinese commit subject and leave unrelated PPT modifications untouched.
