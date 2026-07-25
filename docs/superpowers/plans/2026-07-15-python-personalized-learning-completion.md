# Python Personalized Learning Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve every existing campus feature while delivering a grounded Python-course learning loop from dialogue profile through multi-agent resources, persistent path, assessment feedback, replanning, deployment evidence, and competition submission artifacts.

**Architecture:** Java remains the authenticated control plane for MaxKB, profile evidence, mastery, paths, exams, resources, and persistence. Python adds a typed deterministic DAG that consumes Java-supplied profile/mastery/references, runs six resource agents, reviews and packages at least five valid resources, and emits real progress events. The uni-app student client adds a Python learning center and reuses the existing trusted resource/evidence contract.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, MySQL, Redis, Python 3.11, FastAPI, Pydantic, existing model-provider adapters, python-docx, python-pptx, uni-app/Vue 3, React/Vite admin, Docker Compose.

## Global Constraints

- Submission deadline is 48 hours from plan approval.
- Keep all existing campus routes, pages, APIs, and regression journeys reachable.
- Course key is exactly `python`; MaxKB account ID, knowledge ID, and API key remain server-side.
- Reuse `UserProfileEvidence`, existing exam attempts, Leader sessions/messages, assistant resources, evidence chains, and controlled exports.
- Do not introduce LangGraph, another vector database, another profile system, another exam system, or a second resource envelope.
- Core package types are `knowledge_note`, `mind_map`, `practice_set`, `code_lab`, `presentation`, and `extended_reading`.
- A completed package contains at least five reviewed types; `knowledge_note`, `practice_set`, and `code_lab` are mandatory.
- Python course facts require MaxKB evidence; course resources with `model_only` grounding cannot pass review.
- In-flight workflow state uses Redis key `learning:workflow:{workflowId}` with a 24-hour TTL; final resources and learning state use existing messages plus the three new learning tables.
- Video generation remains optional and cannot block the core package.
- Every commit follows the repository Lore protocol and includes `Co-authored-by: OmX <omx@oh-my-codex.dev>`.

## Execution Graph

| Gate | Tasks | Dependency |
| --- | --- | --- |
| G0 | Task 1 | none |
| G1 | Tasks 2, 3, 4, 7 | Task 1; run in parallel |
| G2 | Tasks 5, 6 | Tasks 2–4 |
| G3 | Task 8 | Tasks 3, 5, 6 |
| G4 | Task 9 | Tasks 2–8 |
| G5 | Task 10 | Task 9 |

## Shared Interface Contract

The Java-to-Python request metadata uses these exact keys:

```json
{
  "courseKey": "python",
  "workflowId": "uuid",
  "profileSnapshot": {},
  "masterySnapshot": [],
  "pathSnapshot": {},
  "references": [],
  "requestedResourceTypes": [
    "knowledge_note",
    "mind_map",
    "practice_set",
    "code_lab",
    "presentation",
    "extended_reading"
  ]
}
```

Every learning resource adds these metadata keys to the existing assistant resource envelope:

```json
{
  "courseKey": "python",
  "knowledgePoint": "python.lists.slicing",
  "learningPathId": 12,
  "learningPathItemKey": "python-lists-slicing",
  "resourceKind": "code_lab",
  "reviewStatus": "passed"
}
```

The learning SSE event payload is stable across Java and App:

```json
{
  "workflowId": "uuid",
  "stage": "generating",
  "progress": 49,
  "agentName": "python_code_lab_agent",
  "resourceType": "code_lab",
  "message": "正在生成列表切片代码实验",
  "retryable": false
}
```

---

### Task 1: Remove submitted secrets and establish a reproducible baseline

**Files:**
- Modify: `AppBackend/src/main/resources/application.yml:1-83`
- Modify: `AppBackend/src/main/resources/data.sql:198-260,1413-1422`
- Modify: `AppFrontend/utils/config.js:1-15`
- Create: `AppBackend/src/test/java/com/example/appbackend/config/SubmissionConfigurationTest.java`
- Create: `docs/compliance/credential-rotation-record.md`

**Interfaces:**
- Consumes: current Spring property names and uni-app `BASE_URL` imports.
- Produces: environment-only secrets, synthetic demo identities, and `getApiBaseUrl()` for all App HTTP/SSE clients.

- [ ] **Step 1: Write the failing configuration test**

```java
package com.example.appbackend.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SubmissionConfigurationTest {
    @Test
    void applicationYamlContainsNoLiteralSecrets() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertFalse(yaml.matches("(?s).*secret-id:\\s*(?!\\$\\{).+"));
        assertFalse(yaml.matches("(?s).*secret-key:\\s*(?!\\$\\{).+"));
        assertFalse(yaml.matches("(?s).*access-key-secret:\\s*(?!\\$\\{).+"));
        assertFalse(yaml.matches("(?s).*jwt:\\s*\\n\\s+secret:\\s*(?!\\$\\{).+"));
    }
}
```

- [ ] **Step 2: Run the test and record the red state**

Run: `cd AppBackend && mvn -q -Dtest=SubmissionConfigurationTest test`

Expected: FAIL because tracked YAML still contains literal credential values.

- [ ] **Step 3: Replace all secret-bearing values with required environment variables**

Use these exact values in `application.yml`:

```yaml
jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION_MS:86400000}

tencent:
  map:
    key: ${TENCENT_MAP_KEY:}
  cos:
    secret-id: ${TENCENT_COS_SECRET_ID:}
    secret-key: ${TENCENT_COS_SECRET_KEY:}

amap:
  app:
    key: ${AMAP_APP_KEY:}
  web:
    key: ${AMAP_WEB_KEY:}

aliyun:
  oss:
    access-key-id: ${ALIYUN_OSS_ACCESS_KEY_ID:}
    access-key-secret: ${ALIYUN_OSS_ACCESS_KEY_SECRET:}
```

Replace any seed identity resembling a real student with this synthetic record:

```sql
('A3演示学生', 'A3DEMO001', '13800000000', 'a3-demo@example.invalid')
```

- [ ] **Step 4: Make the App base URL environment-aware**

```js
const trimTrailingSlash = value => String(value || '').replace(/\/+$/, '')

export function getApiBaseUrl() {
  const injected = import.meta.env?.VITE_API_BASE_URL || ''
  // #ifdef H5
  return trimTrailingSlash(injected || '')
  // #endif
  // #ifndef H5
  return trimTrailingSlash(injected || 'http://127.0.0.1:8080')
  // #endif
}

export const BASE_URL = getApiBaseUrl()
```

- [ ] **Step 5: Verify the baseline**

Run:

```bash
cd AppBackend && mvn -q -Dtest=SubmissionConfigurationTest test
cd ../AppFrontend && node --test subpackage_ai/*.test.js subpackage_exam/*.test.js
cd ../AppWeb && npm run build
```

Expected: configuration test PASS, App Node tests PASS, AppWeb build exits 0. Record existing unrelated lint/test failures without suppressing them.

- [ ] **Step 6: Commit only the security baseline**

```bash
git add AppBackend/src/main/resources/application.yml AppBackend/src/main/resources/data.sql AppBackend/src/test/java/com/example/appbackend/config/SubmissionConfigurationTest.java AppFrontend/utils/config.js docs/compliance/credential-rotation-record.md
git commit -m "fix: 阻止提交包继续携带真实凭据" -m "将云服务与 JWT 凭据改为环境注入，并以合成演示身份替换种子个人信息。" -m "Constraint: 现有校园功能配置键必须保持兼容" -m "Rejected: 仅在文档声明密钥无效 | 历史与当前文件仍会泄露值" -m "Confidence: high" -m "Scope-risk: moderate" -m "Directive: 已曝光密钥必须在供应商侧轮换，仓库只记录别名和时间" -m "Tested: SubmissionConfigurationTest；App Node tests；AppWeb build" -m "Not-tested: 供应商侧吊销状态需由密钥所有者确认" -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 2: Expose a student-safe Python-course MaxKB retrieval facade

**Files:**
- Modify: `AppBackend/src/main/java/com/example/appbackend/dto/KnowledgeChatDTO.java:16-135`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/KnowledgeChatService.java:5-12`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/KnowledgeChatServiceImpl.java:69-123`
- Create: `AppBackend/src/main/java/com/example/appbackend/dto/LearningKnowledgeDTO.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/CourseKnowledgeService.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/impl/CourseKnowledgeServiceImpl.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/controller/AppLearningKnowledgeController.java`
- Create: `AppBackend/src/test/java/com/example/appbackend/service/impl/CourseKnowledgeServiceImplTest.java`
- Create: `AppBackend/src/test/java/com/example/appbackend/controller/AppLearningKnowledgeControllerTest.java`

**Interfaces:**
- Consumes: `MaxKbKnowledgeService.hitTest`, retrieval cache, `system_config` keys `ai.learning.python.maxkb.account-id`, `ai.learning.python.maxkb.knowledge-id`, `ai.learning.python.enabled`.
- Produces: `KnowledgeChatDTO.RetrievalResult retrieve(RetrievalRequest request)` and `POST /api/app/learning/knowledge/retrieve` with sanitized references.

- [ ] **Step 1: Write the failing service test**

```java
@Test
void retrievePythonCourseHidesInternalBindingAndRawPayload() {
    LearningKnowledgeDTO.RetrieveRequest request = new LearningKnowledgeDTO.RetrieveRequest();
    request.setCourseKey("python");
    request.setQuery("列表切片如何工作");
    request.setTopNumber(6);

    LearningKnowledgeDTO.RetrieveResponse response = service.retrieve(request);

    assertEquals("python", response.getCourseKey());
    assertFalse(response.getReferences().isEmpty());
    assertNull(response.getAccountId());
    assertNull(response.getKnowledgeId());
    assertNull(response.getRaw());
}
```

- [ ] **Step 2: Run the targeted tests**

Run: `cd AppBackend && mvn -q -Dtest=CourseKnowledgeServiceImplTest,AppLearningKnowledgeControllerTest test`

Expected: FAIL because the classes and endpoint do not exist.

- [ ] **Step 3: Add retrieval-only DTOs and service signature**

```java
public interface KnowledgeChatService {
    KnowledgeChatDTO.RetrievalResult retrieve(KnowledgeChatDTO.RetrievalRequest request);
    KnowledgeChatDTO.ChatResponse chat(KnowledgeChatDTO.ChatRequest request, String authorization);
    KnowledgeChatDTO.CacheStats getCacheStats();
    void clearCache();
}
```

```java
@Data
public static class RetrievalRequest {
    private Long accountId;
    private String knowledgeId;
    private String query;
    private Integer topNumber;
    private Double similarity;
    private String searchMode;
}

@Data
public static class RetrievalResult {
    private List<Reference> references;
    private CacheInfo retrievalCache;
}
```

Refactor `chat` to call `retrieve` and retain its existing LLM synthesis behavior.

- [ ] **Step 4: Implement the course binding facade**

```java
public interface CourseKnowledgeService {
    LearningKnowledgeDTO.RetrieveResponse retrieve(LearningKnowledgeDTO.RetrieveRequest request);
}
```

```java
@PostMapping("/retrieve")
public Result<LearningKnowledgeDTO.RetrieveResponse> retrieve(
        @Valid @RequestBody LearningKnowledgeDTO.RetrieveRequest body,
        HttpServletRequest request) {
    requireAuthenticatedUser(request);
    return Result.success(courseKnowledgeService.retrieve(body));
}
```

`CourseKnowledgeServiceImpl` must reject any course key except `python`, resolve account/knowledge IDs from system configuration, call `KnowledgeChatService.retrieve`, copy only `id/title/documentName/content/similarity/source`, and truncate each content field to 1,200 characters.

- [ ] **Step 5: Verify authorization and secret filtering**

Run: `cd AppBackend && mvn -q -Dtest=KnowledgeChatServiceImplTest,CourseKnowledgeServiceImplTest,AppLearningKnowledgeControllerTest test`

Expected: PASS; controller test proves unauthenticated requests return 401 and the JSON contains neither `accountId`, `knowledgeId`, `apiKey`, nor `retrievalRaw`.

- [ ] **Step 6: Commit the retrieval facade**

```bash
git add AppBackend/src/main/java/com/example/appbackend/dto/KnowledgeChatDTO.java AppBackend/src/main/java/com/example/appbackend/dto/LearningKnowledgeDTO.java AppBackend/src/main/java/com/example/appbackend/service/KnowledgeChatService.java AppBackend/src/main/java/com/example/appbackend/service/CourseKnowledgeService.java AppBackend/src/main/java/com/example/appbackend/service/impl/KnowledgeChatServiceImpl.java AppBackend/src/main/java/com/example/appbackend/service/impl/CourseKnowledgeServiceImpl.java AppBackend/src/main/java/com/example/appbackend/controller/AppLearningKnowledgeController.java AppBackend/src/test/java/com/example/appbackend/service/impl/CourseKnowledgeServiceImplTest.java AppBackend/src/test/java/com/example/appbackend/controller/AppLearningKnowledgeControllerTest.java
git commit -m "feat: 让学生安全检索 Python 课程知识" -m "通过服务端固定绑定 MaxKB 账号和知识库，只向学生端返回净化后的引用。" -m "Constraint: MaxKB 管理接口仍仅管理员可访问" -m "Rejected: 由 App 传 accountId 和 knowledgeId | 会泄露内部边界并允许越权" -m "Confidence: high" -m "Scope-risk: moderate" -m "Directive: 新课程必须新增服务端白名单绑定，不得放开任意知识库 ID" -m "Tested: KnowledgeChatServiceImplTest；CourseKnowledgeServiceImplTest；AppLearningKnowledgeControllerTest" -m "Not-tested: 真实 MaxKB 响应将在集成任务验证" -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 3: Persist Python mastery, paths, and path items

**Files:**
- Create: `AppBackend/src/main/java/com/example/appbackend/entity/LearningKnowledgeMastery.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/entity/LearningPath.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/entity/LearningPathItem.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/domain/LearningStatuses.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/repository/LearningKnowledgeMasteryRepository.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/repository/LearningPathRepository.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/repository/LearningPathItemRepository.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/dto/LearningPathDTO.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/LearningPathService.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/impl/LearningPathServiceImpl.java`
- Create: `AppBackend/src/test/java/com/example/appbackend/service/impl/LearningPathServiceImplTest.java`

**Interfaces:**
- Consumes: authenticated `userId`, course key `python`, profile snapshot, mastery observations, and reviewed workflow path draft.
- Produces: `getHome`, `getActivePath`, `replaceActivePath`, `recordResourceInteraction`, and `applyAssessment`.

- [ ] **Step 1: Write failing persistence and versioning tests**

```java
@Test
void replaceActivePathArchivesPreviousVersion() {
    LearningPathDTO.PathView first = service.replaceActivePath(7L, draft("python", "期末复习"));
    LearningPathDTO.PathView second = service.replaceActivePath(7L, draft("python", "补强列表切片"));

    assertEquals(1, first.getVersion());
    assertEquals(2, second.getVersion());
    assertEquals("active", second.getStatus());
    assertEquals(1, repository.countByUserIdAndCourseKeyAndStatus(7L, "python", "active"));
}
```

```java
@Test
void repeatedAssessmentIsIdempotentByAttemptId() {
    service.applyAssessment(observation(7L, 99L, "python.lists.slicing", false, "medium"));
    service.applyAssessment(observation(7L, 99L, "python.lists.slicing", false, "medium"));
    assertEquals(1, masteryRepository.findByUserIdAndCourseKeyAndKnowledgePointKey(
            7L, "python", "python.lists.slicing").orElseThrow().getAttemptCount());
}
```

- [ ] **Step 2: Run the red tests**

Run: `cd AppBackend && mvn -q -Dtest=LearningPathServiceImplTest test`

Expected: FAIL because learning persistence classes do not exist.

- [ ] **Step 3: Implement the entity invariants**

Use these exact status values:

```java
public final class LearningStatuses {
    public static final Set<String> PATH = Set.of("active", "completed", "archived");
    public static final Set<String> ITEM = Set.of("locked", "ready", "in_progress", "completed", "needs_review");
    public static final Set<String> MASTERY = Set.of("new", "weak", "learning", "mastered");
    private LearningStatuses() {}
}
```

`LearningKnowledgeMastery` uses a unique table constraint on `(user_id, course_key, knowledge_point_key)`, stores `lastAttemptId`, `attemptCount`, `correctCount`, `wrongCount`, `score`, `confidence`, `status`, `nextReviewAt`, and has `@Version Long version`.

`LearningPath` stores `userId`, `courseKey`, `goal`, `versionNo`, `status`, `profileDigest`, `masteryDigest`, `sourceMessageId`, `generatedAt`, and `nextReplanAt`. `LearningPathItem` stores `pathId`, `itemKey`, `knowledgePoint`, `objective`, `targetMastery`, `priority`, `sequenceNo`, `resourceKindsJson`, `resourceIdsJson`, `status`, `deliveryStatus`, `sourceMessageId`, `scheduledAt`, `deliveredAt`, `completedAt`, and `rationale`.

- [ ] **Step 4: Implement deterministic mastery update**

```java
private BigDecimal nextScore(BigDecimal previous, boolean correct, String difficulty) {
    BigDecimal weight = switch (difficulty) {
        case "hard" -> new BigDecimal("0.40");
        case "medium" -> new BigDecimal("0.30");
        default -> new BigDecimal("0.20");
    };
    BigDecimal signal = correct ? new BigDecimal("100") : BigDecimal.ZERO;
    return previous.multiply(BigDecimal.ONE.subtract(weight))
            .add(signal.multiply(weight))
            .setScale(2, RoundingMode.HALF_UP);
}
```

Status mapping is `<60 weak`, `60–79.99 learning`, and `>=80 with at least 3 attempts mastered`; otherwise `new`. Review intervals are 1, 3, and 7 days for weak, learning, and mastered.

- [ ] **Step 5: Implement active-path replacement and interactions**

`replaceActivePath` archives the current active row in the same transaction, saves `version + 1`, validates unique `itemKey`, and stores resource IDs as canonical JSON. `recordResourceInteraction` accepts only `view/open/complete/dismiss`; `complete` updates the item to `completed`, `dismiss` updates delivery status only.

- [ ] **Step 6: Verify and commit**

Run: `cd AppBackend && mvn -q -Dtest=LearningPathServiceImplTest test`

Expected: PASS with versioning, idempotency, status transitions, and mastery thresholds covered.

```bash
git add AppBackend/src/main/java/com/example/appbackend/entity/LearningKnowledgeMastery.java AppBackend/src/main/java/com/example/appbackend/entity/LearningPath.java AppBackend/src/main/java/com/example/appbackend/entity/LearningPathItem.java AppBackend/src/main/java/com/example/appbackend/domain/LearningStatuses.java AppBackend/src/main/java/com/example/appbackend/repository/LearningKnowledgeMasteryRepository.java AppBackend/src/main/java/com/example/appbackend/repository/LearningPathRepository.java AppBackend/src/main/java/com/example/appbackend/repository/LearningPathItemRepository.java AppBackend/src/main/java/com/example/appbackend/dto/LearningPathDTO.java AppBackend/src/main/java/com/example/appbackend/service/LearningPathService.java AppBackend/src/main/java/com/example/appbackend/service/impl/LearningPathServiceImpl.java AppBackend/src/test/java/com/example/appbackend/service/impl/LearningPathServiceImplTest.java
git commit -m "feat: 持久化 Python 掌握度与动态路径" -m "以三张学习表记录知识点掌握度、路径版本和资源节点状态，并保证重复答题证据幂等。" -m "Constraint: 画像证据继续复用 UserProfileEvidence" -m "Rejected: 新建独立画像和考试体系 | 会产生冲突事实源" -m "Confidence: high" -m "Scope-risk: moderate" -m "Directive: 路径状态只能经 LearningPathService 事务更新" -m "Tested: LearningPathServiceImplTest" -m "Not-tested: 真实考试反馈由后续任务覆盖" -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 4: Build the typed Python multi-agent DAG

**Files:**
- Create: `ai-servers/app/learning_workflow/__init__.py`
- Create: `ai-servers/app/learning_workflow/models.py`
- Create: `ai-servers/app/learning_workflow/workflow.py`
- Create complete agent package: `ai-servers/app/multi_agents/python_code_lab_agent/{__init__.py,README.md,agent.py,contract.md,example_input.md,prompt.md,skill.md,tools.yaml}`
- Create complete agent package: `ai-servers/app/multi_agents/python_practice_set_agent/{__init__.py,README.md,agent.py,contract.md,example_input.md,prompt.md,skill.md,tools.yaml}`
- Create complete agent package: `ai-servers/app/multi_agents/extension_reading_agent/{__init__.py,README.md,agent.py,contract.md,example_input.md,prompt.md,skill.md,tools.yaml}`
- Create complete agent package: `ai-servers/app/multi_agents/resource_review_agent/{__init__.py,README.md,agent.py,contract.md,example_input.md,prompt.md,skill.md,tools.yaml}`
- Create complete agent package: `ai-servers/app/multi_agents/resource_package_agent/{__init__.py,README.md,agent.py,contract.md,example_input.md,prompt.md,skill.md,tools.yaml}`
- Create complete agent package: `ai-servers/app/multi_agents/learning_path_agent/{__init__.py,README.md,agent.py,contract.md,example_input.md,prompt.md,skill.md,tools.yaml}`
- Modify: `ai-servers/app/multi_agents/catalog.py:12-190`
- Modify: `ai-servers/app/multi_agents/runner.py:9-48`
- Modify: `ai-servers/app/services/assistant_resource_builder.py:68-73,503-521`
- Modify: `ai-servers/tests/test_assistant_resource_builder.py`
- Create: `ai-servers/tests/test_learning_workflow.py`

**Interfaces:**
- Consumes: `LearningWorkflowRequest` with profile, mastery, MaxKB references, topic, and six requested resource types.
- Produces: ordered `WorkflowEvent` values and `LearningWorkflowResult` with reviewed resources, package metadata, and path draft.

- [ ] **Step 1: Write the failing DAG contract test**

```python
def test_workflow_runs_shared_plan_resources_review_and_package():
    runner = FakeRunner()
    result = run_learning_workflow(build_request(), runner=runner)

    assert runner.calls[0] == "learning_path_agent"
    assert set(runner.parallel_calls) == {
        "textbook_knowledge_agent",
        "diagram_mind_map_agent",
        "python_practice_set_agent",
        "python_code_lab_agent",
        "ppt_outline_agent",
        "extension_reading_agent",
    }
    assert runner.calls[-2:] == ["resource_review_agent", "resource_package_agent"]
    assert len(result.resources) >= 5
    assert all(item.reviewStatus == "passed" for item in result.resources)
```

- [ ] **Step 2: Run the red test**

Run: `cd ai-servers && .venv/bin/python -m pytest tests/test_learning_workflow.py -q`

Expected: FAIL because `app.learning_workflow` does not exist.

- [ ] **Step 3: Add strict Pydantic models**

```python
class WorkflowResource(BaseModel):
    resourceType: Literal[
        "knowledge_note", "mind_map", "practice_set",
        "code_lab", "presentation", "extended_reading"
    ]
    agentName: str
    content: str
    evidenceIds: list[str] = Field(min_length=1)
    reviewStatus: Literal["pending", "passed", "rejected"] = "pending"
    reviewIssues: list[str] = Field(default_factory=list)


class LearningWorkflowRequest(BaseModel):
    workflowId: str
    userId: int
    courseKey: Literal["python"]
    topic: str = Field(min_length=1, max_length=500)
    profileSnapshot: dict[str, Any]
    masterySnapshot: list[dict[str, Any]]
    pathSnapshot: dict[str, Any]
    references: list[dict[str, Any]] = Field(min_length=1)
    requestedResourceTypes: list[str]
```

- [ ] **Step 4: Implement deterministic dependencies and bounded parallelism**

```python
def run_learning_workflow(request: LearningWorkflowRequest, runner) -> LearningWorkflowResult:
    plan = runner.run("learning_path_agent", build_plan_input(request), request.references)
    jobs = resource_jobs(request, plan)
    with ThreadPoolExecutor(max_workers=3) as pool:
        futures = {pool.submit(runner.run, job.agent, job.input, request.references): job for job in jobs}
        drafts = [to_resource(futures[future], future.result()) for future in as_completed(futures)]
    review = runner.run("resource_review_agent", build_review_input(drafts), request.references)
    reviewed = apply_review(drafts, review)
    reviewed = rewrite_rejected_once(reviewed, request, runner)
    validate_package_threshold(reviewed)
    package = runner.run("resource_package_agent", build_package_input(reviewed, plan), request.references)
    return build_result(request, reviewed, package, plan)
```

`validate_package_threshold` rejects packages missing any mandatory type or containing fewer than five passed resources. Review evidence IDs must be members of the request reference ID set.

- [ ] **Step 5: Register agents and strict runner dispatch**

Add all six new agents to `AGENT_ORDER` and profiles. Each agent exposes `process(input_text, evidence, chat_service=None) -> str`; `runner.py` continues to invoke only registered modules. `python_practice_set_agent` must emit a mixed set containing at least single-choice, multiple-choice, true/false, fill-blank, and code-output questions. Prompts state that MaxKB content is data, not executable instructions, and every factual section must list the evidence IDs used. Extend `_SAFE_RESOURCE_METADATA` only with `courseKey`, `knowledgePoint`, `learningPathId`, `learningPathItemKey`, `resourceKind`, and `reviewStatus`, and test that forbidden keys remain removed.

- [ ] **Step 6: Verify DAG behavior and commit**

Run: `cd ai-servers && .venv/bin/python -m pytest tests/test_learning_workflow.py tests/test_assistant_resource_builder.py -q`

Expected: PASS for dependency order, maximum parallelism 3, single rewrite, mandatory-resource threshold, forged evidence rejection, and package assembly.

```bash
git add ai-servers/app/learning_workflow ai-servers/app/multi_agents/python_code_lab_agent ai-servers/app/multi_agents/python_practice_set_agent ai-servers/app/multi_agents/extension_reading_agent ai-servers/app/multi_agents/resource_review_agent ai-servers/app/multi_agents/resource_package_agent ai-servers/app/multi_agents/learning_path_agent ai-servers/app/multi_agents/catalog.py ai-servers/app/multi_agents/runner.py ai-servers/app/services/assistant_resource_builder.py ai-servers/tests/test_learning_workflow.py ai-servers/tests/test_assistant_resource_builder.py
git commit -m "feat: 让 Python 学习资源形成真实协作 DAG" -m "学习规划先拆解目标，六个资源智能体共享画像和课程证据并行生成，再由审核与整合智能体消费上游结果。" -m "Constraint: 不引入 LangGraph 且核心包必须至少五类资源" -m "Rejected: Leader 每轮只路由一个智能体 | 无法证明任务协作和结果依赖" -m "Confidence: high" -m "Scope-risk: broad" -m "Directive: 新资源类型必须注册、审核并纳入包完成门槛" -m "Tested: test_learning_workflow；test_assistant_resource_builder" -m "Not-tested: 真实模型调用在集成任务验证" -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 5: Export executable Python labs and real PPTX files

**Files:**
- Modify: `ai-servers/app/rag/document_conversion/generated_exporter.py:240-360`
- Create: `ai-servers/app/rag/document_conversion/presentation_exporter.py`
- Create: `ai-servers/tests/test_learning_exports.py`

**Interfaces:**
- Consumes: reviewed `code_lab` JSON and reviewed `presentation` outline JSON.
- Produces: `.py`, `.md`, `.zip`, and `.pptx` attachments through the existing controlled export root.

- [ ] **Step 1: Write failing export tests**

```python
from pptx import Presentation


def test_code_lab_export_contains_runnable_source_and_tests(tmp_path, monkeypatch):
    monkeypatch.setenv("AI_EXPORT_ROOT", str(tmp_path))
    result = export_python_code_lab(valid_code_lab(), {"title": "列表切片实验"})
    assert {item["ext"] for item in result.attachments} == {"py", "md", "zip"}
    compile(next(tmp_path.rglob("*.py")).read_text("utf-8"), "lab.py", "exec")


def test_presentation_export_creates_openable_pptx(tmp_path, monkeypatch):
    monkeypatch.setenv("AI_EXPORT_ROOT", str(tmp_path))
    attachment = export_presentation(valid_outline(), {"title": "Python列表"})
    presentation = Presentation(next(tmp_path.rglob("*.pptx")))
    assert len(presentation.slides) >= 5
```

- [ ] **Step 2: Run the red tests**

Run: `cd ai-servers && .venv/bin/python -m pytest tests/test_learning_exports.py -q`

Expected: FAIL because both export functions are missing.

- [ ] **Step 3: Implement safe Python source export**

```python
class GeneratedExportError(ValueError):
    pass


def export_python_code_lab(payload: Mapping[str, Any], metadata: Mapping[str, Any]) -> GeneratedExportResult:
    source = str(payload["sourceCode"])
    compile(source, "lab.py", "exec")
    forbidden = ("subprocess", "os.system", "eval(", "exec(", "socket", "requests")
    if any(token in source for token in forbidden):
        raise GeneratedExportError("代码实验包含不允许的系统或网络操作")
    slug = _slugify(str(metadata.get("title") or "python-code-lab"))
    source_path = _write_text_file(slug, "py", source.rstrip() + "\n")
    guide_path = _write_text_file(f"{slug}-guide", "md", render_code_lab_guide(payload))
    archive_path = _write_archive(slug, [source_path, guide_path])
    return GeneratedExportResult(attachments=[
        _attachment_for_file(source_path, "python_code_export", "Python 源码"),
        _attachment_for_file(guide_path, "markdown_export", "实验说明"),
        _attachment_for_file(archive_path, "archive_export", "代码实验包"),
    ])
```

- [ ] **Step 4: Implement deterministic PPTX export**

`export_presentation` uses `pptx.Presentation`, widescreen dimensions, one title slide, one agenda slide, one content slide per outline item, and one references slide. It must place source IDs in speaker notes metadata or a visible 10pt footer and must not fetch remote images during export.

- [ ] **Step 5: Verify artifacts and commit**

Run: `cd ai-servers && .venv/bin/python -m pytest tests/test_learning_exports.py tests/test_ppt_to_docx_conversion.py -q`

Expected: PASS and generated archives remain below the configured export maximum.

```bash
git add ai-servers/app/rag/document_conversion/generated_exporter.py ai-servers/app/rag/document_conversion/presentation_exporter.py ai-servers/tests/test_learning_exports.py
git commit -m "feat: 输出可运行代码实验与真实课件" -m "将审核后的 Python 实操生成源码和实验包，并把课件大纲确定性导出为可打开的 PPTX。" -m "Constraint: 导出只能写入受控目录且不得执行模型代码" -m "Rejected: 用硬编码预览和 Toast 冒充文件生成 | 无法形成可交付资源" -m "Confidence: high" -m "Scope-risk: moderate" -m "Directive: 新文件格式必须通过所有权、大小和完整性校验" -m "Tested: test_learning_exports；test_ppt_to_docx_conversion" -m "Not-tested: 不同 Office 软件的视觉一致性在发布任务抽检" -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 6: Connect the DAG to Java, real SSE progress, and persistent results

**Files:**
- Modify: `ai-servers/app/models/schemas.py`
- Modify: `ai-servers/app/api/routes/rag.py:575-668`
- Modify: `ai-servers/app/multi_agents/leader_agent/agent.py:48-80`
- Modify: `ai-servers/app/services/java_backend.py`
- Modify: `ai-servers/app/services/data_store.py`
- Modify: `ai-servers/app/main.py`
- Create: `ai-servers/app/security/internal_auth.py`
- Create: `ai-servers/app/safety/learning_content_guard.py`
- Create: `ai-servers/tests/test_learning_workflow_routes.py`
- Create: `ai-servers/tests/test_internal_auth.py`
- Create: `ai-servers/tests/test_learning_content_guard.py`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/PythonAiProxyService.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/AssistantEnvelopeService.java:80-115`
- Modify: `AppBackend/src/main/resources/application.yml`
- Modify: `AppBackend/pom.xml`
- Create: `AppBackend/src/main/java/com/example/appbackend/controller/AppLearningController.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/LearningWorkflowService.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/LearningWorkflowStateStore.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/impl/LearningWorkflowServiceImpl.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/impl/RedisLearningWorkflowStateStore.java`
- Create: `AppBackend/src/test/java/com/example/appbackend/controller/AppLearningControllerTest.java`
- Modify: `AppBackend/src/test/java/com/example/appbackend/service/impl/AssistantEnvelopeServiceTest.java`

**Interfaces:**
- Consumes: Task 2 retrieval, Task 3 paths, Task 4 DAG, Task 5 exports, existing profile snapshot and resource envelope.
- Produces: `POST /api/app/learning/resources/generate/stream`, `GET /api/app/learning/workflows/{workflowId}`, and final persisted resources/path.

- [ ] **Step 1: Write failing SSE contract tests**

```python
def test_learning_stream_emits_real_monotonic_stages(client, configured_models):
    events = read_sse(client.post("/internal/rag/query/stream", json=learning_request()))
    assert events[0].received_after_ms < 500
    names = [event.name for event in events]
    assert names[:4] == ["accepted", "profile", "retrieval", "planning"]
    assert names.count("agent_start") == 6
    assert names.count("agent_done") == 6
    assert names.index("review_start") > max(i for i, name in enumerate(names) if name == "agent_done")
    assert names[-5:] == ["review_result", "exporting", "pathing", "persisting", "done"]
    progress = [event.data["progress"] for event in events]
    assert progress == sorted(progress)
    assert len([event for event in events if event.name == "done"]) == 1
```

- [ ] **Step 2: Run red route tests**

Run: `cd ai-servers && .venv/bin/python -m pytest tests/test_learning_workflow_routes.py -q`

Expected: FAIL because learning workflow routing and stages are missing.

- [ ] **Step 3: Add the workflow branch without changing campus routing**

The Leader plan action set becomes `direct_answer | delegate_agent | call_tool | run_learning_workflow`. Trigger only when `courseKey == "python"` and the intent is resource package, learning plan, weakness review, or path replanning. All existing campus service tools retain their current branch.

`event_stream` emits a stage immediately before each real operation and after its result. Remove post-hoc `chunk_answer + sleep` only for the learning branch; ordinary chat compatibility remains unchanged in this task.

- [ ] **Step 4: Make Java backend failure recoverable**

Replace permanent `enabled = False` behavior with a timestamped circuit:

```python
def _can_call(self) -> bool:
    return self.disabled_until is None or time.monotonic() >= self.disabled_until

def _record_failure(self) -> None:
    self.disabled_until = time.monotonic() + 10.0

def _record_success(self) -> None:
    self.disabled_until = None
```

- [ ] **Step 5: Require authenticated Java-to-Python calls and guard untrusted course content**

```python
def require_internal_token(x_ai_internal_token: str | None = Header(default=None)) -> None:
    expected = os.environ.get("AI_INTERNAL_TOKEN", "")
    if not expected or not x_ai_internal_token or not hmac.compare_digest(expected, x_ai_internal_token):
        raise HTTPException(status_code=401, detail="内部调用认证失败")
```

Apply this dependency to internal chat, RAG, image, video, and document-conversion routes; keep `/healthz` public. Java sends `X-AI-Internal-Token` from `AI_INTERNAL_TOKEN`. `learning_content_guard` treats retrieved text as untrusted data, rejects forged evidence IDs, strips prompt-injection directives from planning context, and rejects generated Python code containing filesystem destruction, subprocess, network, dynamic evaluation, or credential access.

- [ ] **Step 6: Add Java facade and Redis recovery**

Add `spring-boot-starter-data-redis`, configure `spring.data.redis.host/port/password` from `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD`, and store canonical JSON through `StringRedisTemplate`. `LearningWorkflowService.start` generates UUID, stores accepted state in Redis for 24 hours, injects the authenticated user's profile/mastery/path, calls Python SSE, persists the final assistant message/resources, and calls `LearningPathService.replaceActivePath`. `getWorkflow` returns the Redis state or reconstructs a completed state from the persisted message. Extend Java resource metadata and SSE field allowlists only with the Shared Interface Contract keys; keep the existing forbidden-key filter intact.

- [ ] **Step 7: Expose the complete student learning facade**

`AppLearningController` owns these exact authenticated methods:

```java
@PostMapping(value = "/resources/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
SseEmitter generateResources(
        @Valid @RequestBody LearningPathDTO.GenerateRequest body,
        HttpServletRequest request);

@GetMapping("/workflows/{workflowId}")
Result<LearningPathDTO.WorkflowView> workflow(
        @PathVariable String workflowId,
        HttpServletRequest request);

@GetMapping("/courses/python/home")
Result<LearningPathDTO.HomeView> home(HttpServletRequest request);

@PostMapping("/courses/python/profile-answers")
Result<LearningPathDTO.ProfileAnswerResult> profileAnswer(
        @Valid @RequestBody LearningPathDTO.ProfileAnswerRequest body,
        HttpServletRequest request);

@GetMapping("/courses/python/path")
Result<LearningPathDTO.PathView> path(HttpServletRequest request);

@GetMapping("/courses/python/recommendations")
Result<List<LearningPathDTO.Recommendation>> recommendations(HttpServletRequest request);

@PostMapping("/recommendations/{itemId}/interactions")
Result<Void> recommendationInteraction(
        @PathVariable Long itemId,
        @Valid @RequestBody LearningPathDTO.InteractionRequest body,
        HttpServletRequest request);

@PostMapping("/path-items/{itemId}/start")
Result<LearningPathDTO.PathItemView> startPathItem(
        @PathVariable Long itemId,
        HttpServletRequest request);

@PostMapping("/path-items/{itemId}/complete")
Result<LearningPathDTO.PathItemView> completePathItem(
        @PathVariable Long itemId,
        HttpServletRequest request);

@PostMapping("/courses/python/path/replan")
Result<LearningPathDTO.PathView> replan(HttpServletRequest request);

@PostMapping("/workflows/{workflowId}/resources/{resourceType}/retry")
Result<LearningPathDTO.WorkflowView> retryResource(
        @PathVariable String workflowId,
        @PathVariable String resourceType,
        HttpServletRequest request);
```

Profile question IDs are fixed to `python_goal`, `python_level`, `python_weak_topic`, `python_resource_preference`, and `python_weekly_time`; Java maps them to existing profile dimensions and never accepts a client-provided score.

- [ ] **Step 8: Verify both sides and commit**

Run:

```bash
cd ai-servers && .venv/bin/python -m pytest tests/test_learning_workflow_routes.py tests/test_internal_auth.py tests/test_learning_content_guard.py tests/test_rag_api_routes.py -q
cd ../AppBackend && mvn -q -Dtest=PythonAiProxyServiceTest,AppLearningControllerTest test
```

Expected: PASS, first SSE event arrives before model completion, progress is monotonic, campus route tests remain unchanged, and reconnect returns the same workflow ID/results.

```bash
git add ai-servers/app/models/schemas.py ai-servers/app/api/routes/rag.py ai-servers/app/multi_agents/leader_agent/agent.py ai-servers/app/services/java_backend.py ai-servers/app/services/data_store.py ai-servers/app/main.py ai-servers/app/security/internal_auth.py ai-servers/app/safety/learning_content_guard.py ai-servers/tests/test_learning_workflow_routes.py ai-servers/tests/test_internal_auth.py ai-servers/tests/test_learning_content_guard.py AppBackend/pom.xml AppBackend/src/main/resources/application.yml AppBackend/src/main/java/com/example/appbackend/service/impl/PythonAiProxyService.java AppBackend/src/main/java/com/example/appbackend/service/impl/AssistantEnvelopeService.java AppBackend/src/main/java/com/example/appbackend/controller/AppLearningController.java AppBackend/src/main/java/com/example/appbackend/service/LearningWorkflowService.java AppBackend/src/main/java/com/example/appbackend/service/LearningWorkflowStateStore.java AppBackend/src/main/java/com/example/appbackend/service/impl/LearningWorkflowServiceImpl.java AppBackend/src/main/java/com/example/appbackend/service/impl/RedisLearningWorkflowStateStore.java AppBackend/src/test/java/com/example/appbackend/controller/AppLearningControllerTest.java AppBackend/src/test/java/com/example/appbackend/service/impl/AssistantEnvelopeServiceTest.java
git commit -m "feat: 串联 Python 学习工作流与真实进度" -m "Java 注入画像、掌握度和课程证据，Python 按真实阶段生成资源，完成结果回写消息与学习路径。" -m "Constraint: 校园 Leader 路由不得改变" -m "Rejected: 完整生成后再延迟切块 | 不能证明实时进度" -m "Confidence: high" -m "Scope-risk: broad" -m "Directive: 工作流事件名和共享元数据必须保持向后兼容" -m "Tested: learning workflow routes；rag routes；PythonAiProxyServiceTest；AppLearningControllerTest" -m "Not-tested: 真机断网恢复在学生端任务验证" -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 7: Feed exam results into mastery, profile evidence, and replanning

**Files:**
- Modify: `AppBackend/src/main/java/com/example/appbackend/dto/AppExamDTO.java:82-111`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/AppExamServiceImpl.java:350-410`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/AssistantResourceInteractionService.java:66-139`
- Create: `AppBackend/src/test/java/com/example/appbackend/service/impl/AppExamLearningFeedbackTest.java`

**Interfaces:**
- Consumes: `ExamQuestion.knowledgePointsJson`, objective scoring results, Task 3 learning service, and existing profile evidence service.
- Produces: `AttemptResult.learningUpdate`, idempotent mastery observations, versioned replanning, and resource completion interactions.

- [ ] **Step 1: Write the failing feedback test**

```java
@Test
void wrongPythonAnswerUpdatesMasteryAndReplansOnce() {
    AppExamDTO.AttemptResult first = service.submit(attemptId, userId);
    AppExamDTO.AttemptResult repeated = service.getResult(attemptId, userId);

    assertEquals(List.of("python.lists.slicing"), first.getLearningUpdate().getWeakKnowledgePoints());
    assertEquals(2, first.getLearningUpdate().getPathVersionAfter());
    assertEquals(first.getLearningUpdate(), repeated.getLearningUpdate());
    verify(learningPathService, times(1)).applyAssessment(any());
}
```

- [ ] **Step 2: Run the red test**

Run: `cd AppBackend && mvn -q -Dtest=AppExamLearningFeedbackTest test`

Expected: FAIL because `learningUpdate` and the feedback call do not exist.

- [ ] **Step 3: Add response types**

```java
@Data
public static class LearningUpdate {
    private List<String> weakKnowledgePoints;
    private String evidenceStatus;
    private Integer pathVersionBefore;
    private Integer pathVersionAfter;
    private List<PathChange> changedNodes;
    private LearningPathDTO.Recommendation nextRecommendation;
}
```

Add `private LearningUpdate learningUpdate;` to `AttemptResult`.

- [ ] **Step 4: Apply feedback after objective scoring**

Only submitted objective questions with non-empty `knowledgePointsJson` produce observations. Use `attemptId` as the idempotency key. Subjective questions with `correct == null` produce no mastery update. Add candidate evidence to `weak_points`, `learning_progress`, and `ability_performance` through the existing evidence service.

- [ ] **Step 5: Reuse interactions for path completion**

When the existing assistant resource interaction is `complete`, read `learningPathId` and `learningPathItemKey` from the safe resource metadata and call `LearningPathService.recordResourceInteraction`. Do not trust client-supplied path IDs that are absent from the stored resource.

- [ ] **Step 6: Verify and commit**

Run: `cd AppBackend && mvn -q -Dtest=AppExamLearningFeedbackTest,AppExamServiceImplTest,AssistantResourceInteractionServiceTest test`

Expected: PASS with objective feedback, subjective exclusion, repeated-submit idempotency, path version change, and resource ownership checks.

```bash
git add AppBackend/src/main/java/com/example/appbackend/dto/AppExamDTO.java AppBackend/src/main/java/com/example/appbackend/service/impl/AppExamServiceImpl.java AppBackend/src/main/java/com/example/appbackend/service/impl/AssistantResourceInteractionService.java AppBackend/src/test/java/com/example/appbackend/service/impl/AppExamLearningFeedbackTest.java
git commit -m "feat: 让答题结果驱动画像与路径调整" -m "客观题按知识点形成幂等掌握度证据，薄弱点触发路径版本升级并返回可解释的下一推荐。" -m "Constraint: 未评分主观题不得推断掌握度" -m "Rejected: 仅展示考试分数 | 无法形成持续优化闭环" -m "Confidence: high" -m "Scope-risk: moderate" -m "Directive: 所有掌握度更新必须携带唯一证据键" -m "Tested: AppExamLearningFeedbackTest；AppExamServiceImplTest；AssistantResourceInteractionServiceTest" -m "Not-tested: 大规模历史答题回填不在本轮范围" -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 8: Deliver the Python learning center and remove fake learning interactions

**Files:**
- Create: `AppFrontend/api/learning.js`
- Modify: `AppFrontend/api/ai.js:256-344`
- Create: `AppFrontend/subpackage_learning/learningState.js`
- Create: `AppFrontend/subpackage_learning/learningState.test.js`
- Create: `AppFrontend/subpackage_learning/pythonHome/pythonHome.vue`
- Create: `AppFrontend/subpackage_learning/resourceGenerate/resourceGenerate.vue`
- Create: `AppFrontend/subpackage_learning/learningPath/learningPath.vue`
- Create: `AppFrontend/subpackage_learning/recommendations/recommendations.vue`
- Create: `AppFrontend/components/learning-profile-dialog/learning-profile-dialog.vue`
- Create: `AppFrontend/components/learning-resource-viewer/learning-resource-viewer.vue`
- Create: `AppFrontend/components/safe-markdown/safe-markdown.vue`
- Create: `AppFrontend/utils/markdownNodes.js`
- Create: `AppFrontend/utils/markdownNodes.test.js`
- Modify: `AppFrontend/pages.json`
- Modify: `AppFrontend/pages/index/index.vue`
- Modify: `AppFrontend/subpackage_ai/aiCreate/aiCreate.vue:204-250`
- Modify: `AppFrontend/subpackage_ai/examGenerate/examGenerate.vue:259-275`
- Modify: `AppFrontend/subpackage_ai/aiConversation/aiConversation.vue:37-171`
- Modify: `AppFrontend/components/ai-float-assistant/ai-float-assistant.vue`
- Modify: `AppFrontend/subpackage_exam/attemptResult/attemptResult.vue`
- Create: `AppFrontend/subpackage_learning/learningApi.test.js`
- Modify: `AppFrontend/subpackage_ai/assistantPages.test.js`

**Interfaces:**
- Consumes: Task 6 learning APIs/SSE, Task 7 `learningUpdate`, existing resource/evidence/download helpers.
- Produces: Python home, profile answers, real package progress, path/recommendations, and a result-to-path CTA without removing any campus entry.

- [ ] **Step 1: Write failing pure-state tests**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { createLearningState, reduceLearningEvent } from './learningState.js'

test('learning progress is monotonic and resources are keyed by type', () => {
  let state = createLearningState('wf-1')
  state = reduceLearningEvent(state, 'agent_done', {
    workflowId: 'wf-1', progress: 49, resourceType: 'code_lab', resource: { id: 'r1' }
  })
  state = reduceLearningEvent(state, 'retrieval', { workflowId: 'wf-1', progress: 15 })
  assert.equal(state.progress, 49)
  assert.equal(state.resources.code_lab.id, 'r1')
})
```

- [ ] **Step 2: Run the red Node tests**

Run: `cd AppFrontend && node --test subpackage_learning/*.test.js utils/markdownNodes.test.js subpackage_ai/assistantPages.test.js`

Expected: FAIL because the learning modules do not exist.

- [ ] **Step 3: Add API calls and state reducer**

```js
export const getPythonHome = () => request({ url: '/api/app/learning/courses/python/home' })
export const submitProfileAnswer = data => request({
  url: '/api/app/learning/courses/python/profile-answers', method: 'POST', data
})
export const getPythonPath = () => request({ url: '/api/app/learning/courses/python/path' })
export const getPythonRecommendations = () => request({
  url: '/api/app/learning/courses/python/recommendations'
})
export const getLearningWorkflow = workflowId => request({
  url: `/api/app/learning/workflows/${encodeURIComponent(workflowId)}`
})
export const retryLearningResource = (workflowId, resourceType) => request({
  url: `/api/app/learning/workflows/${encodeURIComponent(workflowId)}/resources/${encodeURIComponent(resourceType)}/retry`,
  method: 'POST'
})
export const startPathItem = itemId => request({
  url: `/api/app/learning/path-items/${encodeURIComponent(itemId)}/start`, method: 'POST'
})
export const completePathItem = itemId => request({
  url: `/api/app/learning/path-items/${encodeURIComponent(itemId)}/complete`, method: 'POST'
})
export const replanPythonPath = () => request({
  url: '/api/app/learning/courses/python/path/replan', method: 'POST'
})
export const recordRecommendationInteraction = (itemId, data) => request({
  url: `/api/app/learning/recommendations/${encodeURIComponent(itemId)}/interactions`,
  method: 'POST', data
})

// Export the existing helper from api/ai.js; do not duplicate the parser.
export const streamLearningResources = (data, handlers) => streamSse(
  '/api/app/learning/resources/generate/stream', data, handlers,
  '当前运行环境无法读取学习资源生成进度'
)
```

The SSE client must accept the event names in the Shared Interface Contract, retain `workflowId`, persist it in storage, and call `GET /api/app/learning/workflows/{workflowId}` after reconnect. `markdownNodes.js` converts headings, paragraphs, lists, emphasis, links, tables, inline code, and fenced code to a strict uni-app `rich-text` node allowlist; it ignores raw HTML and rejects `javascript:`/`data:` links. The same `safe-markdown` component is used by the resource viewer, AI conversation, and float assistant.

- [ ] **Step 4: Register pages and add a non-destructive home entry**

Add one `subpackage_learning` package with the four pages. Add “Python 个性化学习” to the existing 智感工坊 section without removing or reordering existing campus entries.

- [ ] **Step 5: Implement real page states**

Each page supports `loading`, `ready`, `empty`, `dependency_unavailable`, `network_error`, and `generation_failed`. The resource page renders existing trusted cards and evidence status, plus per-resource retry. The path page uses only backend statuses. The recommendation page sends `view/open/complete/dismiss` interactions.

- [ ] **Step 6: Remove fake learning results without hiding tools**

Fix exam generation with:

```js
const answer = res?.data?.answer || ''
```

Route PPT, mind map, questions, code lab, and learning plan cards to the new pages. Route remaining visible tools to `aiConversation` with an encoded prefilled intent; no visible card may silently return or show a completion Toast without an API result.

- [ ] **Step 7: Add the assessment loop UI**

`attemptResult.vue` renders `learningUpdate.weakKnowledgePoints`, path version before/after, changed nodes, next recommendation, and a button to `/subpackage_learning/learningPath/learningPath`.

- [ ] **Step 8: Reuse one safe Markdown renderer in both assistant surfaces**

`learning-resource-viewer.vue` accepts normalized nodes, never raw HTML, and renders headings, paragraphs, lists, emphasis, links through the existing safe-URL policy, tables, inline code, fenced code, and Mermaid source blocks. Replace direct assistant `<text>` rendering in `aiConversation.vue` and the ad-hoc formatting in `ai-float-assistant.vue` with this component.

- [ ] **Step 9: Verify and commit**

Run:

```bash
cd AppFrontend && node --test subpackage_learning/*.test.js subpackage_ai/*.test.js subpackage_exam/*.test.js
```

Expected: PASS for state, API envelope, all visible AI Create actions, exam response, and assessment CTA.

```bash
git add AppFrontend/api/learning.js AppFrontend/api/ai.js AppFrontend/subpackage_learning AppFrontend/components/learning-profile-dialog AppFrontend/components/learning-resource-viewer AppFrontend/components/safe-markdown AppFrontend/utils/markdownNodes.js AppFrontend/utils/markdownNodes.test.js AppFrontend/components/ai-float-assistant/ai-float-assistant.vue AppFrontend/pages.json AppFrontend/pages/index/index.vue AppFrontend/subpackage_ai/assistantPages.test.js AppFrontend/subpackage_ai/aiCreate/aiCreate.vue AppFrontend/subpackage_ai/aiConversation/aiConversation.vue AppFrontend/subpackage_ai/examGenerate/examGenerate.vue AppFrontend/subpackage_exam/attemptResult/attemptResult.vue
git commit -m "feat: 交付 Python 个性化学习主线" -m "新增画像补问、资源包进度、学习路径、精准推荐和答题后重规划页面，同时保留所有校园入口。" -m "Constraint: 可见工具不得静默或伪造完成" -m "Rejected: 隐藏未接通的校园功能 | 用户明确要求完整保留" -m "Confidence: high" -m "Scope-risk: broad" -m "Directive: 学习状态只消费后端事实，不在 App 本地伪造进度或路径" -m "Tested: learning Node tests；assistant tests；exam tests" -m "Not-tested: 真机视觉和网络恢复由集成任务覆盖" -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 9: Make the full submission reproducible and measurable

**Files:**
- Create: `ai-servers/Dockerfile`
- Create: `deploy/compose.submission.yml`
- Create: `deploy/.env.example`
- Create: `deploy/verify.sh`
- Create: `.github/workflows/quality-gate.yml`
- Create: `scripts/ci/quality-gate.sh`
- Create: `evaluation/python-course/gold.jsonl`
- Create: `evaluation/python-course/rubric.md`
- Create: `scripts/eval/run_factual_eval.py`
- Create: `scripts/eval/run_load.py`
- Create: `docs/deployment/submission-runbook.md`
- Create: `docs/verification/test-matrix.md`
- Create: `NOTICE`
- Create: `THIRD_PARTY_NOTICES.md`
- Create: `SECURITY.md`
- Create: `PRIVACY.md`
- Create: `docs/compliance/open-source-compliance.md`
- Create: `docs/compliance/AI_CODING_DISCLOSURE.md`
- Create: `artifacts/knowledge-base/python-course/README.md`
- Create: `artifacts/knowledge-base/python-course/manifest.json`
- Create: `artifacts/knowledge-base/python-course/sources.csv`
- Create: `artifacts/knowledge-base/python-course/checksums.sha256`
- Create: `scripts/knowledge/validate_python_course.py`
- Create: `artifacts/verification/baseline-failures.txt`
- Create: `artifacts/verification/python-course-factual.json`
- Create: `artifacts/verification/python-course-load.json`

**Interfaces:**
- Consumes: Tasks 1–8 and external MaxKB/LLM values from `.env`.
- Produces: one-command environment, fail-fast quality gate, 30-question factual evaluation, load metrics, and compliance documentation.

- [ ] **Step 1: Restore every pre-existing quality gate before adding release evidence**

Run these commands and preserve their initial output as `artifacts/verification/baseline-failures.txt`:

```bash
cd ai-servers && .venv/bin/python -m pytest -q --tb=no
cd ../AppWeb && npm run lint
cd ../AppBackend && mvn test -q
```

The known baseline is 18 Python failures, 33 Web lint errors/13 warnings, and one Java context error caused by a missing test database. Fix them under these non-negotiable contracts:

- Keep strict question-bank schema validation; update stale fake-provider fixtures to emit the production schema instead of weakening validation.
- Restore `build_diagram(input_text, evidence, chat_service)` on diagram agents and preserve Mermaid type rejection.
- Configure explicit model bindings in route tests instead of bypassing per-agent model requirements.
- Reset Java-backend caches between integration tests so schedule fixtures cannot leak across cases.
- Fix conditional React Hook calls by moving hooks before early returns; do not disable `react-hooks/rules-of-hooks`.
- Fix parsing, unused-variable, empty-block, and irregular-whitespace errors without adding blanket ESLint ignores.
- Add `AppBackend/src/test/resources/application-test.yml`, an H2 test dependency, and `@ActiveProfiles("test")` to the context test so `mvn test` never requires a developer MySQL database.

Completion command:

```bash
cd ai-servers && .venv/bin/python -m pytest -q
cd ../AppWeb && npm run lint && npm run build
cd ../AppBackend && mvn test -q
```

Expected: all three commands exit 0 with zero failures and zero lint errors.

- [ ] **Step 2: Write the deployment smoke script before the compose file**

```bash
#!/usr/bin/env bash
set -euo pipefail
base_url="${BASE_URL:-http://localhost:8080}"
curl --fail --silent "${base_url}/actuator/health" >/dev/null
curl --fail --silent "${AI_BASE_URL:-http://localhost:8081}/healthz" >/dev/null
curl --fail --silent "${WEB_BASE_URL:-http://localhost:3000}" >/dev/null
```

- [ ] **Step 3: Create a pinned five-service compose**

Compose must contain MySQL, Redis, backend, ai-server, and web; use health checks and `depends_on.condition: service_healthy`. External MaxKB and model provider are environment dependencies. Do not use `latest` tags in the final manifest.

- [ ] **Step 4: Add the quality wrapper**

```bash
#!/usr/bin/env bash
set -euo pipefail
(cd AppBackend && mvn test)
(cd ai-servers && .venv/bin/python -m pytest -q)
(cd AppWeb && node --test src/**/*.test.js && npm run lint && npm run build)
(cd AppFrontend && node --test subpackage_ai/*.test.js subpackage_exam/*.test.js subpackage_learning/*.test.js)
docker compose -f deploy/compose.submission.yml config --quiet
```

The GitHub workflow runs this wrapper before image publishing and names images with `${{ github.sha }}`.

- [ ] **Step 5: Create a frozen 30-question evaluation set**

`gold.jsonl` contains 20 deterministic Python concept/output questions, 5 rubric-scored explanation questions, and 5 out-of-knowledge questions. Each record includes `id`, `query`, `expectedEvidence`, `answerType`, `expectedAnswer`, and `shouldRefuse`. The runner saves every raw result and reports Recall@5, citation validity, deterministic accuracy, rubric pass count, refusal accuracy, error rate, and p95 latency.

- [ ] **Step 6: Freeze a portable Python-course knowledge manifest**

`manifest.json` records `packVersion`, MaxKB version, document count, paragraph count, split configuration, course chapters, source IDs, and SHA-256 values without account IDs or credentials. `sources.csv` records `source_id,title,origin,author,license,sha256`. `validate_python_course.py` verifies every manifest source exists, checks all hashes, rejects unknown licenses, and confirms the evaluation set references only declared sources. If MaxKB native export is legally redistributable, store it as `portable-pack.zip`; otherwise store only team-authored/openly licensed sources plus deterministic import instructions.

- [ ] **Step 7: Document compliance and AI Coding use**

Do not relicense the team's own code without all copyright holders' explicit decision. `NOTICE` and `THIRD_PARTY_NOTICES.md` list dependency/component name, version, source URL, license, and usage. `AI_CODING_DISCLOSURE.md` records Codex/OMX use, generated scopes, human decisions, review/testing, sensitive-data prohibition, and known limitations. No license may be asserted for the external `wordpapergenerate` material until its actual source record is present.

- [ ] **Step 8: Run the complete gate**

Run: `bash scripts/ci/quality-gate.sh`

Expected: exit 0. If real MaxKB credentials are available, also run `docker compose -f deploy/compose.submission.yml up -d` followed by `bash deploy/verify.sh` and the factual/load scripts. Record actual metrics without changing thresholds after seeing results.

- [ ] **Step 9: Commit reproducibility assets**

```bash
git add ai-servers/Dockerfile deploy .github/workflows/quality-gate.yml scripts/ci scripts/knowledge evaluation scripts/eval docs/deployment docs/verification NOTICE THIRD_PARTY_NOTICES.md SECURITY.md PRIVACY.md docs/compliance/open-source-compliance.md docs/compliance/AI_CODING_DISCLOSURE.md artifacts/knowledge-base/python-course artifacts/verification AppBackend/src/test/resources/application-test.yml AppBackend/src/test/java/com/example/appbackend/AppBackendApplicationTests.java AppBackend/pom.xml AppWeb/src ai-servers/tests
git commit -m "chore: 让比赛提交可复现且可量化" -m "统一容器、质量门禁、Python 课程事实评测、性能评测和合规说明，确保提交证据来自同一版本。" -m "Constraint: 外部 MaxKB 与模型凭据只通过环境变量注入" -m "Rejected: 仅提供开发机启动说明 | 评委无法复现完整 AI 主线" -m "Confidence: medium" -m "Scope-risk: broad" -m "Directive: 发布前必须保存原始评测结果并执行干净环境复现" -m "Tested: quality-gate；compose config；deployment smoke；factual and load evaluation" -m "Not-tested: 未提供外部凭据时真实供应商调用需在演示环境补跑" -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

### Task 10: Freeze evidence, create the competition deck/video, and run final release audit

**Files:**
- Create: `submission/A3-final.pptx`
- Create: `submission/A3-final.pdf`
- Create: `submission/video/script.md`
- Create: `submission/video/shot-list.md`
- Create: `submission/video/captions.srt`
- Create: `submission/video/7min-demo.mp4`
- Create: `submission/frozen-demo/manifest.json`
- Create: `submission/frozen-demo/checksums.sha256`
- Create: `submission/manifest.json`
- Create: `docs/submission/evidence-index.md`

**Interfaces:**
- Consumes: one frozen commit, Python-course knowledge pack hash, final test/evaluation reports, real screenshots, and real generated resources.
- Produces: submission-ready deck/PDF, a 400–415 second video, frozen replay evidence, and a requirement-to-evidence index.

- [ ] **Step 1: Freeze the demo matrix**

Record exactly five journeys in `manifest.json`: grounded Python explanation, six-resource package, practice submission and path version change, out-of-knowledge refusal, and one campus Leader journey. Each entry stores request, response, evidence IDs, output files, screenshot paths, commit SHA, dataset hash, model, and timestamp.

- [ ] **Step 2: Build the 12-slide deck**

Slides are: title/value, student pain evidence, closed loop, architecture, seven-dimensional profile, Python MaxKB governance, multi-agent package, dynamic path/assessment, retained campus capabilities, factual/performance results, security/open-source/AI Coding/reproducibility, and closing/demo index. Every numeric claim links to an artifact path in speaker notes.

- [ ] **Step 3: Render and inspect every slide**

Use the Presentations skill to generate PPTX and PDF. Render all slides to images; verify 16:9, body text at least 24pt, no clipping, no placeholders, no secrets, and no unreadable architecture labels.

- [ ] **Step 4: Record a 6:40–6:55 video**

Use this exact time budget: 0:00–0:20 value, 0:20–0:50 architecture, 0:50–1:30 profile/knowledge, 1:30–3:50 resource package, 3:50–4:50 assessment/replan, 4:50–5:40 campus capability, 5:40–6:20 metrics/security/reproduction, 6:20–6:50 close. The recording must use the same frozen commit and dataset as the manifest.

- [ ] **Step 5: Run final release audit**

Run:

```bash
bash scripts/ci/quality-gate.sh
bash deploy/verify.sh
sha256sum -c submission/frozen-demo/checksums.sha256
ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 submission/video/7min-demo.mp4
git status --short
```

Expected: quality/deploy/checksum commands exit 0, video duration is 400–415 seconds and below 420 seconds, and the only working-tree entries are deliberately staged submission artifacts.

- [ ] **Step 6: Commit the frozen submission**

```bash
git add submission docs/submission/evidence-index.md
git commit -m "docs: 冻结 A3 比赛演示与评审证据" -m "将答辩课件、七分钟视频、真实样例、测试评测和需求映射绑定到同一代码与知识库版本。" -m "Constraint: 视频必须少于七分钟且不得出现凭据或真实个人信息" -m "Rejected: 使用与最终代码无关的静态演示素材 | 无法复核真实性" -m "Confidence: high" -m "Scope-risk: moderate" -m "Directive: 提交后不得替换单个产物而不更新 manifest 和校验和" -m "Tested: final quality gate；deployment verify；checksums；media duration；slide render inspection" -m "Not-tested: 比赛平台上传后的转码效果需在平台预览确认" -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

## Final Completion Gate

Do not claim completion until every statement below is backed by fresh output:

- Java tests exit 0 with no unexpected skips.
- Python tests exit 0, including the previously failing route/question/diagram contracts.
- App Node tests exit 0 and every visible AI Create card has a real action.
- AppWeb lint and build exit 0.
- A student can retrieve Python MaxKB references without receiving internal IDs or secrets.
- The workflow trace proves planner → six resource agents → reviewer → package → path dependencies.
- At least five real resources are previewable/downloadable, with lecture, practice, and code lab mandatory.
- A submitted objective attempt updates mastery, profile evidence, path version, and next recommendation exactly once.
- All original campus regression journeys remain reachable and pass.
- Compose starts Java, Python, Web, MySQL, and Redis from a clean environment.
- Secret/PII scans have zero unapproved findings.
- The 30-question factual report and load report record raw results and actual thresholds.
- PPT/PDF render correctly and the demo video is below seven minutes.
