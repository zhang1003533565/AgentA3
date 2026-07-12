# 题库智能生成实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在管理后台提供 DOCX、TXT、粘贴文本到标准题库草稿的智能生成流程，并通过 AI 模块的动态题型—智能体映射选择执行智能体，预览校验后导入当前题库。

**Architecture:** React 页面只提交生成意图和材料；Spring Boot 负责文件解析、读取系统配置、校验智能体/模型、调用 Python AI 服务并复用现有题库审查服务。题型映射保存在现有 `system_config` 表，生成结果不自动落库，最终继续通过现有题库导入接口写入 `exam_question`。

**Tech Stack:** Java 17、Spring Boot、Apache POI、Jackson、JUnit 5、Mockito、React 19、Ant Design 5、Axios、Node test runner、Vite。

## Global Constraints

- 一次任务只生成一种题型，支持 `single_choice`、`multiple_choice`、`true_false`、`fill_blank`、`short_answer`。
- 最大题量可空；有值时仅表示上限，不允许静默截断超量结果。
- DOCX 只提取标题、正文和表格文本，暂不识别图片；TXT 必须是 UTF-8。
- 题型—智能体映射只能来自 AI 模块的系统配置，不得写死或回退到 Leader。
- 生成结果必须先预览、编辑和校验，再显式导入当前题库。
- 不支持 `.doc`、PDF、PPTX、多文件、自动导入或生成历史。
- 直接在当前 `master` 分支实施，不创建分支或工作树。

---

## 文件结构

### 后端新增

- `AppBackend/src/main/java/com/example/appbackend/dto/QuestionGenerationDTO.java`：生成选项、生成请求内部模型和生成响应契约。
- `AppBackend/src/main/java/com/example/appbackend/service/QuestionGenerationMaterialParser.java`：TXT/DOCX 材料解析与限制校验。
- `AppBackend/src/main/java/com/example/appbackend/service/QuestionGenerationService.java`：题型映射解析与生成编排接口。
- `AppBackend/src/main/java/com/example/appbackend/service/impl/QuestionGenerationServiceImpl.java`：配置、智能体调用、JSON 解析和题库审查编排。
- `AppBackend/src/main/java/com/example/appbackend/controller/QuestionGenerationController.java`：管理员 options/generate API。
- `AppBackend/src/test/java/com/example/appbackend/service/QuestionGenerationMaterialParserTest.java`：文件解析回归测试。
- `AppBackend/src/test/java/com/example/appbackend/service/impl/QuestionGenerationServiceImplTest.java`：配置与编排测试。
- `AppBackend/src/test/java/com/example/appbackend/controller/QuestionGenerationControllerTest.java`：权限和 multipart 控制器测试。

### 后端修改

- `AppBackend/src/main/java/com/example/appbackend/service/impl/PythonAiProxyService.java`：暴露经过现有模型/开关处理的题库生成调用入口及只读智能体目录快照。
- `AppBackend/src/main/resources/application.yml`：文件大小和解析文本上限。

### 前端新增

- `AppWeb/src/api/questionGeneration.js`：options/generate API。
- `AppWeb/src/pages/questionBank/QuestionBankGeneratePage.jsx`：生成设置、草稿编辑、复审和导入页面。
- `AppWeb/src/pages/questionBank/QuestionBankGeneratePage.css`：页面样式。
- `AppWeb/src/pages/questionBank/questionGenerationState.js`：题型编辑器数据转换、请求构造和校验辅助函数。
- `AppWeb/src/pages/questionBank/questionGenerationState.test.js`：纯函数测试。

### 前端修改

- `AppWeb/src/pages/ai/AgentSettings/AgentSettings.jsx`：题型—智能体映射配置卡片。
- `AppWeb/src/pages/ai/AgentSettings/AgentSettings.css`：映射状态样式。
- `AppWeb/src/pages/questionBank/questionBankRoutes.js`：新增生成路由常量。
- `AppWeb/src/data/portalData.js`：新增“题库生成”菜单。
- `AppWeb/src/App.jsx`：注册生成页面路由。

---

### Task 1: 锁定题库生成 DTO 与材料解析行为

**Files:**
- Create: `AppBackend/src/main/java/com/example/appbackend/dto/QuestionGenerationDTO.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/QuestionGenerationMaterialParser.java`
- Create: `AppBackend/src/test/java/com/example/appbackend/service/QuestionGenerationMaterialParserTest.java`
- Modify: `AppBackend/src/main/resources/application.yml`

**Interfaces:**
- Produces: `QuestionGenerationMaterialParser.parse(String sourceType, MultipartFile file, String text): ParsedMaterial`
- Produces: `QuestionGenerationDTO.ParsedMaterial(String text, String originalFilename, String sourceTitle)`
- Produces: `QuestionGenerationDTO.GenerationResponse` consumed by Tasks 3、4、7。

- [ ] **Step 1: 编写 TXT 和 DOCX 解析失败测试**

覆盖 UTF-8 BOM、非法 UTF-8、空内容、10 MiB 限制、DOCX 段落与表格、损坏 DOCX、`.doc` 拒绝。核心断言形态：

```java
@Test
void parsesDocxParagraphsAndTablesInDocumentOrder() throws Exception {
    MockMultipartFile file = docx("课程.docx", document -> {
        document.createParagraph().createRun().setText("第一章");
        XWPFTable table = document.createTable(1, 2);
        table.getRow(0).getCell(0).setText("概念");
        table.getRow(0).getCell(1).setText("定义");
    });

    ParsedMaterial result = parser.parse("docx", file, null);

    assertThat(result.text()).containsSubsequence("第一章", "概念", "定义");
    assertThat(result.originalFilename()).isEqualTo("课程.docx");
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd AppBackend && mvn -Dtest=QuestionGenerationMaterialParserTest test`

Expected: FAIL，类或方法尚不存在。

- [ ] **Step 3: 定义 DTO 与解析器**

DTO 至少包含：

```java
public record ParsedMaterial(String text, String originalFilename, String sourceTitle) {}

@Data
public static class GenerationResponse {
    private String questionType;
    private String agentName;
    private String agentRole;
    private String sourceTitle;
    private String originalFilename;
    private Integer maxQuestions;
    private Integer generatedCount;
    private List<Map<String, Object>> questions = new ArrayList<>();
    private List<String> missingInfo = new ArrayList<>();
    private Boolean valid;
    private List<String> issues = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
```

解析器使用严格 `CharsetDecoder` 和 `XWPFDocument`，配置项为：

```yaml
exam:
  question-generation:
    max-file-bytes: 10485760
    max-text-characters: 200000
```

- [ ] **Step 4: 运行解析测试并确认通过**

Run: `cd AppBackend && mvn -Dtest=QuestionGenerationMaterialParserTest test`

Expected: PASS，所有 TXT/DOCX 边界测试通过。

- [ ] **Step 5: 提交任务 1**

```bash
git add AppBackend/src/main/java/com/example/appbackend/dto/QuestionGenerationDTO.java AppBackend/src/main/java/com/example/appbackend/service/QuestionGenerationMaterialParser.java AppBackend/src/test/java/com/example/appbackend/service/QuestionGenerationMaterialParserTest.java AppBackend/src/main/resources/application.yml
git commit -m "feat: 建立题库生成材料解析边界"
```

---

### Task 2: 建立动态题型—智能体配置解析

**Files:**
- Create: `AppBackend/src/main/java/com/example/appbackend/service/QuestionGenerationService.java`
- Create: `AppBackend/src/test/java/com/example/appbackend/service/impl/QuestionGenerationServiceImplTest.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/impl/QuestionGenerationServiceImpl.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/PythonAiProxyService.java`

**Interfaces:**
- Produces: `QuestionGenerationService.getOptions(String authorization): OptionsResponse`
- Produces: `PythonAiProxyService.getQuestionGenerationAgentCatalog(String authorization): Map<String, AgentDescriptor>`
- Consumes: system config keys `ai.question-generation.agent.<type>` and existing `ai.agent-enabled.*` / `ai.agent-bindings.*.model`.

- [ ] **Step 1: 编写五种映射状态测试**

测试有效配置、缺失配置、未知智能体、停用智能体、无模型绑定；断言 options 始终返回五行且包含明确 `available` 与 `unavailableReason`。

```java
assertThat(response.getQuestionTypes()).extracting(QuestionTypeOption::getType)
        .containsExactly("single_choice", "multiple_choice", "true_false", "fill_blank", "short_answer");
assertThat(singleChoice.getAvailable()).isFalse();
assertThat(singleChoice.getUnavailableReason()).contains("未绑定已测试模型");
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd AppBackend && mvn -Dtest=QuestionGenerationServiceImplTest test`

Expected: FAIL，服务实现尚不存在。

- [ ] **Step 3: 实现只读配置解析**

服务内部只固定“题型枚举”和“配置键前缀”，不固定任何智能体名称：

```java
private static final String MAPPING_PREFIX = "ai.question-generation.agent.";
private static final List<String> QUESTION_TYPES = List.of(
        "single_choice", "multiple_choice", "true_false", "fill_blank", "short_answer");
```

`PythonAiProxyService` 提供结构化智能体目录读取能力，复用现有开关合并和模型绑定解析，不让生成服务解析 Python 原始 `Object` 的内部细节。

- [ ] **Step 4: 运行配置解析测试并确认通过**

Run: `cd AppBackend && mvn -Dtest=QuestionGenerationServiceImplTest test`

Expected: PASS，五种映射状态全部稳定返回。

- [ ] **Step 5: 提交任务 2**

```bash
git add AppBackend/src/main/java/com/example/appbackend/service/QuestionGenerationService.java AppBackend/src/main/java/com/example/appbackend/service/impl/QuestionGenerationServiceImpl.java AppBackend/src/main/java/com/example/appbackend/service/impl/PythonAiProxyService.java AppBackend/src/test/java/com/example/appbackend/service/impl/QuestionGenerationServiceImplTest.java
git commit -m "feat: 从 AI 配置解析题型智能体映射"
```

---

### Task 3: 实现 Java 题库生成编排与结果审查

**Files:**
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/QuestionGenerationService.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/QuestionGenerationServiceImpl.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/PythonAiProxyService.java`
- Modify: `AppBackend/src/test/java/com/example/appbackend/service/impl/QuestionGenerationServiceImplTest.java`

**Interfaces:**
- Produces: `QuestionGenerationService.generate(GenerationCommand command, String authorization): GenerationResponse`
- Consumes: `QuestionGenerationMaterialParser`, `ExamQuestionService.review`, `PythonAiProxyService.queryQuestionGeneration`。

- [ ] **Step 1: 编写生成编排失败测试**

覆盖：未配置映射时不调用 AI；可选最大题量不出现强制凑数措辞；指定难度透传；合法 JSON 返回 review；非法 JSON、空题目、混合题型、超出最大题量均不可导入。

```java
verify(pythonAiProxyService).queryQuestionGeneration(argThat(payload ->
        payload.agentName().equals("configured_agent")
        && payload.maxQuestions() == null
        && payload.input().contains("由有效知识点决定题量")), eq(authorization));
```

- [ ] **Step 2: 运行编排测试并确认失败**

Run: `cd AppBackend && mvn -Dtest=QuestionGenerationServiceImplTest test`

Expected: FAIL，generate 尚未实现。

- [ ] **Step 3: 实现生成编排**

生成命令包含：

```java
public record GenerationCommand(
        String sourceType,
        MultipartFile file,
        String text,
        String questionType,
        Integer maxQuestions,
        String difficulty,
        String sourceTitle) {}
```

调用 payload 的 `agentName` 必须来自配置解析结果。智能体答案通过 Jackson 解析成 `ExamQuestionDTO.ImportRequest`，设置 `sourceAgent/sourceTitle/sourceScene` 后调用：

```java
ExamQuestionDTO.ReviewResponse review = examQuestionService.review(importRequest, command.questionType());
```

超量时追加 issue，不截断；零题时追加 issue 并保留 `missingInfo`。

- [ ] **Step 4: 运行编排与代理回归测试**

Run: `cd AppBackend && mvn -Dtest=QuestionGenerationServiceImplTest,PythonAiProxyServiceTest test`

Expected: PASS，且现有 Python 代理行为无回归。

- [ ] **Step 5: 提交任务 3**

```bash
git add AppBackend/src/main/java/com/example/appbackend/service/QuestionGenerationService.java AppBackend/src/main/java/com/example/appbackend/service/impl/QuestionGenerationServiceImpl.java AppBackend/src/main/java/com/example/appbackend/service/impl/PythonAiProxyService.java AppBackend/src/test/java/com/example/appbackend/service/impl/QuestionGenerationServiceImplTest.java
git commit -m "feat: 编排题库智能生成与标准审查"
```

---

### Task 4: 暴露管理员 options 与 multipart generate API

**Files:**
- Create: `AppBackend/src/main/java/com/example/appbackend/controller/QuestionGenerationController.java`
- Create: `AppBackend/src/test/java/com/example/appbackend/controller/QuestionGenerationControllerTest.java`

**Interfaces:**
- Produces: `GET /api/exam/question-generation/options`
- Produces: `POST /api/exam/question-generation/generate` (`multipart/form-data`)
- Consumes: Task 3 的 `QuestionGenerationService`。

- [ ] **Step 1: 编写权限与参数测试**

覆盖非管理员 403、管理员 options 200、文本来源成功、DOCX 文件来源成功、来源/题型/最大题量非法时 400。

```java
mockMvc.perform(multipart("/api/exam/question-generation/generate")
        .param("sourceType", "text")
        .param("text", "课程材料")
        .param("questionType", "single_choice")
        .requestAttr("role", "ADMIN")
        .header("Authorization", "Bearer token"))
    .andExpect(status().isOk());
```

- [ ] **Step 2: 运行控制器测试并确认失败**

Run: `cd AppBackend && mvn -Dtest=QuestionGenerationControllerTest test`

Expected: FAIL，路由尚不存在。

- [ ] **Step 3: 实现控制器**

控制器使用 `@RequestPart(required=false) MultipartFile file` 与 `@RequestParam` 接收字段，显式校验 `role=ADMIN`，将 Authorization 原样交给服务，不写入日志。

- [ ] **Step 4: 运行后端相关测试**

Run: `cd AppBackend && mvn -Dtest=QuestionGenerationControllerTest,QuestionGenerationMaterialParserTest,QuestionGenerationServiceImplTest,PythonAiProxyServiceTest test`

Expected: PASS，无失败测试。

- [ ] **Step 5: 提交任务 4**

```bash
git add AppBackend/src/main/java/com/example/appbackend/controller/QuestionGenerationController.java AppBackend/src/test/java/com/example/appbackend/controller/QuestionGenerationControllerTest.java
git commit -m "feat: 提供管理员题库生成接口"
```

---

### Task 5: 在智能体设置中管理五种题型映射

**Files:**
- Modify: `AppWeb/src/pages/ai/agentConfig.js`
- Modify: `AppWeb/src/pages/ai/AgentSettings/AgentSettings.jsx`
- Modify: `AppWeb/src/pages/ai/AgentSettings/AgentSettings.css`
- Create: `AppWeb/src/pages/ai/agentConfig.test.js`

**Interfaces:**
- Produces: `QUESTION_GENERATION_AGENT_PREFIX`、`QUESTION_TYPE_OPTIONS`、`buildQuestionGenerationAgentMappings(configRows)`。
- Persists: `ai.question-generation.agent.<type>` through existing `upsertSystemConfig`。

- [ ] **Step 1: 编写映射解析纯函数测试**

```javascript
assert.deepEqual(buildQuestionGenerationAgentMappings([
  { configKey: 'ai.question-generation.agent.single_choice', configValue: 'agent_a', status: 1 },
]), { single_choice: 'agent_a' })
```

- [ ] **Step 2: 运行前端测试并确认失败**

Run: `cd AppWeb && node --test src/pages/ai/agentConfig.test.js`

Expected: FAIL，导出尚不存在。

- [ ] **Step 3: 实现配置卡片**

`AgentSettings` 的配置查询 prefixes 增加 `ai.question-generation.agent.`。卡片固定展示五种题型，但智能体选项完全来自 `getRagAgents()`；每行显示映射、启用状态、模型绑定状态和独立保存按钮。

保存 payload：

```javascript
await upsertSystemConfig({
  configKey: `${QUESTION_GENERATION_AGENT_PREFIX}${type}`,
  configValue: agentName,
  configGroup: 'ai',
  description: `${label}题库生成智能体`,
  status: 1,
  isDefault: 0,
})
```

- [ ] **Step 4: 运行纯函数测试与 ESLint**

Run: `cd AppWeb && node --test src/pages/ai/agentConfig.test.js`

Run: `cd AppWeb && npx eslint src/pages/ai/agentConfig.js src/pages/ai/AgentSettings/AgentSettings.jsx`

Expected: 全部通过，无 ESLint 错误。

- [ ] **Step 5: 提交任务 5**

```bash
git add AppWeb/src/pages/ai/agentConfig.js AppWeb/src/pages/ai/agentConfig.test.js AppWeb/src/pages/ai/AgentSettings/AgentSettings.jsx AppWeb/src/pages/ai/AgentSettings/AgentSettings.css
git commit -m "feat: 在 AI 模块配置题型生成智能体"
```

---

### Task 6: 建立生成页状态模型和 API

**Files:**
- Create: `AppWeb/src/api/questionGeneration.js`
- Create: `AppWeb/src/pages/questionBank/questionGenerationState.js`
- Create: `AppWeb/src/pages/questionBank/questionGenerationState.test.js`

**Interfaces:**
- Produces: `getQuestionGenerationOptions()`、`generateQuestions(formData)`。
- Produces: `buildGenerationFormData(values, file)`、`buildImportPayload(draft, questions)`、`normalizeQuestionForEditor(question)`、`serializeEditedQuestion(question)`。

- [ ] **Step 1: 编写请求和题型转换测试**

覆盖三种来源互斥、空最大题量不写入 FormData、最大题量写入上限、五种题型 body/answer 往返不丢字段、导入来源不可被编辑值覆盖。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd AppWeb && node --test src/pages/questionBank/questionGenerationState.test.js`

Expected: FAIL，模块尚不存在。

- [ ] **Step 3: 实现 API 和纯函数**

```javascript
export const getQuestionGenerationOptions = () => request.get('/api/exam/question-generation/options')
export const generateQuestions = (data) => request.post('/api/exam/question-generation/generate', data, {
  headers: { 'Content-Type': 'multipart/form-data' },
  timeout: 120000,
})
```

`buildImportPayload` 必须从后端 draft 获取 `agentName/sourceTitle`，并固定 `sourceScene: 'question_generation'`。

- [ ] **Step 4: 运行状态测试**

Run: `cd AppWeb && node --test src/pages/questionBank/questionGenerationState.test.js`

Expected: PASS，所有来源和题型往返测试通过。

- [ ] **Step 5: 提交任务 6**

```bash
git add AppWeb/src/api/questionGeneration.js AppWeb/src/pages/questionBank/questionGenerationState.js AppWeb/src/pages/questionBank/questionGenerationState.test.js
git commit -m "feat: 建立题库生成前端状态契约"
```

---

### Task 7: 实现题库生成页面、逐题编辑和导入

**Files:**
- Create: `AppWeb/src/pages/questionBank/QuestionBankGeneratePage.jsx`
- Create: `AppWeb/src/pages/questionBank/QuestionBankGeneratePage.css`
- Modify: `AppWeb/src/pages/questionBank/questionGenerationState.test.js`

**Interfaces:**
- Consumes: Tasks 4、6 API；现有 `reviewExamQuestions`、`importExamQuestions`。
- Produces: 管理员三阶段题库生成页面。

- [ ] **Step 1: 扩展状态测试锁定编辑后复审和导入门禁**

断言删除题目后重新编号不改来源 ID；review `valid=false` 时 `canImport` 为 false；警告不阻断；零题永远不能导入。

- [ ] **Step 2: 运行测试确认新增断言失败**

Run: `cd AppWeb && node --test src/pages/questionBank/questionGenerationState.test.js`

Expected: FAIL，门禁辅助函数尚不存在。

- [ ] **Step 3: 实现三阶段页面**

页面必须包含：

- 来源类型 Segmented；切换时清空非当前输入；
- Upload `beforeUpload={() => false}`，仅保留一个 `.docx` 或 `.txt`；
- 题型 Select，根据 options 的 `available` 禁用无效项并展示原因；
- 最大题量 `InputNumber` 可空，最小 1；
- 难度可空；
- 生成 loading 防重复；
- 结果统计、missingInfo、issues、warnings；
- 五种题型对应编辑控件和删除按钮；
- 每次编辑后 debounce 调用 `/review`；
- review 通过且题目非空时启用导入；
- 导入成功后提供 `navigate(QUESTION_BANK_ROUTES.questions)` 和重置生成。

- [ ] **Step 4: 运行页面相关测试和 ESLint**

Run: `cd AppWeb && node --test src/pages/questionBank/questionGenerationState.test.js`

Run: `cd AppWeb && npx eslint src/pages/questionBank/QuestionBankGeneratePage.jsx src/pages/questionBank/questionGenerationState.js src/api/questionGeneration.js`

Expected: 全部通过，无未使用变量和 hooks 依赖错误。

- [ ] **Step 5: 提交任务 7**

```bash
git add AppWeb/src/pages/questionBank/QuestionBankGeneratePage.jsx AppWeb/src/pages/questionBank/QuestionBankGeneratePage.css AppWeb/src/pages/questionBank/questionGenerationState.js AppWeb/src/pages/questionBank/questionGenerationState.test.js
git commit -m "feat: 提供题库智能生成预览与导入页面"
```

---

### Task 8: 接入题库菜单、路由并完成全链路验证

**Files:**
- Modify: `AppWeb/src/pages/questionBank/questionBankRoutes.js`
- Modify: `AppWeb/src/data/portalData.js`
- Modify: `AppWeb/src/App.jsx`
- Create: `AppWeb/src/pages/questionBank/questionBankRoutes.test.js`

**Interfaces:**
- Produces: `QUESTION_BANK_ROUTES.generate = '/question-bank/generate'`。
- Consumes: Task 7 页面。

- [ ] **Step 1: 编写导航契约测试**

断言菜单顺序严格为“题库、题库生成、试卷生成、生成的试卷”，生成路由唯一且指向 `/question-bank/generate`。

- [ ] **Step 2: 运行导航测试并确认失败**

Run: `cd AppWeb && node --test src/pages/questionBank/questionBankRoutes.test.js`

Expected: FAIL，generate 尚未加入。

- [ ] **Step 3: 注册菜单和路由**

```javascript
export const QUESTION_BANK_ROUTES = Object.freeze({
  questions: '/question-bank/questions',
  generate: '/question-bank/generate',
  createPaper: '/question-bank/papers/create',
  paperHistory: '/question-bank/papers/history',
})
```

`portalData.js` 在“题库”和“试卷生成”之间增加 `{ path: QUESTION_BANK_ROUTES.generate, label: '题库生成', icon: 'robot' }`；`App.jsx` 注册 `QuestionBankGeneratePage`。

- [ ] **Step 4: 运行后端完整相关测试**

Run: `cd AppBackend && mvn -Dtest=QuestionGenerationControllerTest,QuestionGenerationMaterialParserTest,QuestionGenerationServiceImplTest,PythonAiProxyServiceTest test`

Expected: BUILD SUCCESS，无失败测试。

- [ ] **Step 5: 运行前端完整验证**

Run: `cd AppWeb && node --test src/pages/ai/agentConfig.test.js src/pages/questionBank/questionGenerationState.test.js src/pages/questionBank/questionBankRoutes.test.js src/pages/ai/ExamPaper/examPaperPreviewState.test.js`

Run: `cd AppWeb && npm run lint`

Run: `cd AppWeb && npm run build`

Expected: Node tests 全部 PASS，ESLint 零错误，Vite production build 成功。

- [ ] **Step 6: 执行真实材料烟雾测试**

使用 UTF-8 TXT 和包含标题、正文、表格的 DOCX 分别验证：

1. 未配置映射时页面禁止生成；
2. 在智能体设置保存映射后，页面显示实际智能体；
3. 最大题量为空和有值均可生成，实际数量不超过上限；
4. 编辑或删除题目会重新审查；
5. 导入成功后题目出现在当前题库；
6. 新题可被现有试卷生成模块手工选择。

Expected: 全链路无控制台错误、无 4xx/5xx，题库与试卷模块读取同一条新题记录。

- [ ] **Step 7: 提交任务 8**

```bash
git add AppWeb/src/pages/questionBank/questionBankRoutes.js AppWeb/src/pages/questionBank/questionBankRoutes.test.js AppWeb/src/data/portalData.js AppWeb/src/App.jsx
git commit -m "feat: 将题库智能生成接入独立菜单"
```

---

## 最终完成标准

- “题库管理”下存在独立“题库生成”菜单和路由。
- AI 模块可配置五种题型各自对应的任意现有智能体，代码无智能体名称映射常量。
- DOCX、TXT、粘贴文本都能进入同一 Java 生成编排链路。
- 未配置、停用、无模型映射均在调用前失败且原因明确。
- 最大题量为空时自动决定，有值时只作为上限。
- 生成结果支持编辑、删除、复审，校验失败不能导入。
- 导入题目进入当前题库并可被试卷模块使用。
- 相关后端测试、前端测试、ESLint、生产构建和真实材料烟雾测试全部通过。
