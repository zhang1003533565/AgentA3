# App 在线答题与试卷 PDF 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让管理员发布现有试卷，并让所有已登录 App 用户在“我的试卷”中下载空白 PDF、限时在线答题、退出续答、自动保存、交卷判分和查看每次历史结果。

**Architecture:** 后端继续以 `exam_paper_question` 快照作为唯一试题事实源，新增发布状态、attempt 与 answer 持久化以及 App 专用 DTO/API。App 使用独立分包页面和统一请求封装；倒计时以服务端 `deadlineAt` 为准，答案使用版本号 upsert，客观题只在后端交卷事务中判分，PDF 复用现有 DOCX 生成和 LibreOffice 转换能力。

**Tech Stack:** Java 21、Spring Boot 4、Spring Data JPA、MySQL、Apache POI、LibreOffice、JUnit 5、Mockito、uni-app/Vue、`uni.request`、平台文件 API。

## Global Constraints

- 试卷发布后对所有已登录 App 用户可见，不支持班级或指定用户分发。
- 每套试卷允许重复答题，每次提交保存独立记录；同一用户同一试卷同时只能有一条进行中记录。
- 使用试卷 `durationMinutes` 倒计时，到期由服务端判定并自动交卷；退出后继续原截止时间。
- 单选、多选、判断、填空自动判分；简答不自动计分且不计入客观题总分。
- 交卷前接口不得返回标准答案、解析或评分规则；交卷后才能查看结果。
- App 只下载空白试卷 PDF，不提供答案版 PDF。
- 所有答题、判分和 PDF 内容使用不可变 `exam_paper_question` 快照，不重读当前题库。
- 直接在当前 `master` 分支实施，不创建分支或 worktree。

---

## 文件结构

### 后端新增

- `entity/ExamPaperAttempt.java`：一次答题状态和客观题汇总。
- `entity/ExamPaperAttemptAnswer.java`：每题当前答案、版本和判分结果。
- `repository/ExamPaperAttemptRepository.java`、`ExamPaperAttemptAnswerRepository.java`：所有权、进行中记录、历史和答案查询。
- `dto/AppExamDTO.java`：发布列表、答题安全视图、保存答案、结果和历史 DTO。
- `service/AppExamService.java`、`service/impl/AppExamServiceImpl.java`：发布访问、attempt 状态机、答案保存、判分和结果。
- `service/exampaper/AppExamPdfService.java`：空白试卷 PDF 生成、缓存和权限后的文件结果。
- `controller/AppExamController.java`：App 试卷与答题 API。
- 对应 `AppExamServiceImplTest.java`、`AppExamControllerTest.java`、`AppExamPdfServiceTest.java`。

### 后端修改

- `entity/ExamPaper.java`：发布状态和时间。
- `dto/ExamPaperDTO.java`：管理后台发布状态字段。
- `repository/ExamPaperRepository.java`：已发布列表和发布锁定查询。
- `service/ExamPaperService.java`、`service/impl/ExamPaperServiceImpl.java`：管理员发布/取消发布。
- `controller/ExamPaperController.java`：管理员发布接口。
- 相关管理后台测试。

### 管理后台修改

- `AppWeb/src/api/examPaper.js`：发布和取消发布 API。
- `AppWeb/src/pages/ai/ExamPaper/ExamPaperHistory.jsx`：发布状态、发布时间和操作。
- 相关前端状态测试。

### App 新增

- `AppFrontend/api/exam.js`：App 试卷、attempt、答案、结果、历史和 PDF API。
- `AppFrontend/subpackage_exam/paperList/paperList.vue`
- `AppFrontend/subpackage_exam/paperDetail/paperDetail.vue`
- `AppFrontend/subpackage_exam/attempt/attempt.vue`
- `AppFrontend/subpackage_exam/attemptHistory/attemptHistory.vue`
- `AppFrontend/subpackage_exam/attemptResult/attemptResult.vue`
- `AppFrontend/subpackage_exam/examState.js`：答案规范化、倒计时、版本和合并纯函数。
- `AppFrontend/subpackage_exam/examState.test.js`：纯函数测试。

### App 修改

- `AppFrontend/pages/mine/mine.vue`：我的试卷入口和数量。
- `AppFrontend/pages.json`：`subpackage_exam` 注册。
- `AppFrontend/utils/request.js`：仅在需要时增加二进制/文件下载选项，不改变现有 JSON 接口行为。

---

### Task 1: 发布字段与管理员发布状态机

**Files:**
- Modify: `AppBackend/src/main/java/com/example/appbackend/entity/ExamPaper.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/dto/ExamPaperDTO.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/repository/ExamPaperRepository.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/ExamPaperService.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/ExamPaperServiceImpl.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/controller/ExamPaperController.java`
- Test: `AppBackend/src/test/java/com/example/appbackend/service/impl/ExamPaperServiceImplTest.java`
- Test: `AppBackend/src/test/java/com/example/appbackend/controller/ExamPaperControllerTest.java`

**Interfaces:**
- Produces: `publish(Long paperId, Long adminUserId)`、`unpublish(Long paperId, Long adminUserId)`。
- Produces: `POST /api/exam/papers/{id}/publish|unpublish`。

- [ ] **Step 1: 写发布失败测试**

覆盖非 ADMIN 403、非创建者 403、空试卷/无时长拒绝、发布写时间、取消发布清时间、重复发布/取消幂等。

- [ ] **Step 2: 运行测试确认 RED**

Run: `cd AppBackend && mvn -Dtest=ExamPaperServiceImplTest,ExamPaperControllerTest test`

Expected: FAIL，发布接口和字段不存在。

- [ ] **Step 3: 实现字段与发布事务**

`ExamPaper` 增加 `published=false`、`publishTime`；发布事务按 ID 查询有效试卷、校验 `createdBy`、题量和正时长后更新。控制器先校验 `role=ADMIN`。

- [ ] **Step 4: 运行发布测试**

Expected: 所有发布测试 PASS。

- [ ] **Step 5: Lore 提交**

```bash
git add AppBackend/src/main/java/com/example/appbackend/{entity/ExamPaper.java,dto/ExamPaperDTO.java,repository/ExamPaperRepository.java,service/ExamPaperService.java,service/impl/ExamPaperServiceImpl.java,controller/ExamPaperController.java} AppBackend/src/test/java/com/example/appbackend/{service/impl/ExamPaperServiceImplTest.java,controller/ExamPaperControllerTest.java}
git commit -m "feat: 让管理员显式发布 App 试卷"
```

---

### Task 2: 答题与答案持久化模型

**Files:**
- Create: `AppBackend/src/main/java/com/example/appbackend/entity/ExamPaperAttempt.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/entity/ExamPaperAttemptAnswer.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/repository/ExamPaperAttemptRepository.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/repository/ExamPaperAttemptAnswerRepository.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/dto/AppExamDTO.java`
- Create: `AppBackend/src/test/java/com/example/appbackend/entity/AppExamPersistenceContractTest.java`

**Interfaces:**
- Produces: attempt 状态 `IN_PROGRESS|SUBMITTED|AUTO_SUBMITTED`。
- Produces: 每题答案 `answerJson`、`version`、`answered`、`correct`、`score`。

- [ ] **Step 1: 写 JPA 契约测试**

断言表名、列名、唯一约束、索引、精度、默认值和快照外键字段；特别锁定 `(paper_id,user_id,attempt_no)` 与 `(attempt_id,paper_question_id)` 唯一约束。

- [ ] **Step 2: 运行确认 RED**

Run: `cd AppBackend && mvn -Dtest=AppExamPersistenceContractTest test`

Expected: FAIL，答题实体和表契约尚不存在。

- [ ] **Step 3: 实现实体、仓储和安全 DTO**

答题 DTO 分离 `QuestionForAttempt` 与 `QuestionResult`；前者不得包含 `answerJson/analysis/scoringJson`。

- [ ] **Step 4: 运行持久化契约测试**

Run: `cd AppBackend && mvn -Dtest=AppExamPersistenceContractTest test`

Expected: PASS，0 failures/errors。

- [ ] **Step 5: Lore 提交**

---

### Task 3: App 试卷列表、安全详情与开始/续答

**Files:**
- Create: `AppBackend/src/main/java/com/example/appbackend/service/AppExamService.java`
- Create: `AppBackend/src/main/java/com/example/appbackend/service/impl/AppExamServiceImpl.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/repository/ExamPaperRepository.java`
- Test: `AppBackend/src/test/java/com/example/appbackend/service/impl/AppExamServiceImplTest.java`

**Interfaces:**
- Produces: `listPublished(userId,page,size,keyword)`、`paperDetail(paperId,userId)`、`startOrResume(paperId,userId,now)`、`attemptDetail(attemptId,userId,now)`。

- [ ] **Step 1: 写列表和开始答题测试**

覆盖只返回已发布、用户次数/进行中状态、未发布拒绝新建、取消发布允许续答、截止时间计算、已有进行中直接返回、超时先自动交卷、并发创建唯一性冲突恢复为已存在记录。

- [ ] **Step 2: 运行确认 RED**

Run: `cd AppBackend && mvn -Dtest=AppExamServiceImplTest test`

Expected: FAIL，AppExamServiceImpl 尚未实现。

- [ ] **Step 3: 实现最小服务**

使用事务和数据库唯一性约束封闭并发；答题详情只投影题干、body 和分值，绝不返回标准答案。

- [ ] **Step 4: 运行服务测试**

Run: `cd AppBackend && mvn -Dtest=AppExamServiceImplTest test`

Expected: PASS，列表、开始和续答场景全部通过。

- [ ] **Step 5: Lore 提交**

---

### Task 4: 答案版本保存、自动交卷与客观题判分

**Files:**
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/AppExamService.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/service/impl/AppExamServiceImpl.java`
- Modify: `AppBackend/src/test/java/com/example/appbackend/service/impl/AppExamServiceImplTest.java`

**Interfaces:**
- Produces: `saveAnswer(attemptId,paperQuestionId,userId,SaveAnswerRequest,now)`。
- Produces: `submit(attemptId,userId,now)`、`history(paperId,userId)`、`result(attemptId,userId)`。

- [ ] **Step 1: 写五题型和状态机失败测试**

覆盖答案结构、版本旧写拒绝、非本人拒绝、题目不属于试卷、过期自动交卷、重复提交幂等；单选、多选集合、判断、填空 ID/trim、简答不计分。

- [ ] **Step 2: 运行确认 RED**

Run: `cd AppBackend && mvn -Dtest=AppExamServiceImplTest test`

Expected: FAIL，保存、提交或判分行为缺失。

- [ ] **Step 3: 实现保存与判分**

单题请求限制 64 KiB、文本 20,000 字；交卷事务重新读取全部答案和快照计算结果，客户端不得提交分数/正确性。

- [ ] **Step 4: 运行状态和判分测试**

Run: `cd AppBackend && mvn -Dtest=AppExamServiceImplTest test`

Expected: PASS，状态机和五题型判分测试全部通过。

- [ ] **Step 5: Lore 提交**

---

### Task 5: App 控制器和权限边界

**Files:**
- Create: `AppBackend/src/main/java/com/example/appbackend/controller/AppExamController.java`
- Create: `AppBackend/src/test/java/com/example/appbackend/controller/AppExamControllerTest.java`

**Interfaces:**
- Produces: `/api/app/exam-papers` 与 `/api/app/exam-attempts` 全部设计接口。

- [ ] **Step 1: 写 MockMvc 失败测试**

覆盖未登录 401、当前用户从 request attribute 获取、不能传 userId 越权、交卷前 DTO 无答案字段、历史/结果仅本人、参数和请求体边界。

- [ ] **Step 2: 运行确认 RED**

Run: `cd AppBackend && mvn -Dtest=AppExamControllerTest test`

Expected: FAIL，App 控制器路由尚不存在。

- [ ] **Step 3: 实现控制器**

控制器仅做身份、参数和 body 限制，所有状态机交给服务；统一 Result 和下载响应格式。

- [ ] **Step 4: 运行控制器与服务组合测试**

Run: `cd AppBackend && mvn -Dtest=AppExamControllerTest,AppExamServiceImplTest test`

Expected: PASS，0 failures/errors。

- [ ] **Step 5: Lore 提交**

---

### Task 6: 空白试卷 PDF 服务

**Files:**
- Create: `AppBackend/src/main/java/com/example/appbackend/service/exampaper/AppExamPdfService.java`
- Create: `AppBackend/src/test/java/com/example/appbackend/service/exampaper/AppExamPdfServiceTest.java`
- Modify: `AppBackend/src/main/java/com/example/appbackend/controller/AppExamController.java`
- Modify: `AppBackend/src/test/java/com/example/appbackend/controller/AppExamControllerTest.java`

**Interfaces:**
- Produces: `PdfFile generate(Long paperId, Long userId)`。

- [ ] **Step 1: 写 PDF 权限和内容测试**

覆盖已发布允许、下架但有历史允许、无权限拒绝、只调用 `DownloadContent.PAPER`、LibreOffice 失败可见、Content-Type/文件名正确、10 秒缓存不重复转换。

- [ ] **Step 2: 运行确认 RED**

Run: `cd AppBackend && mvn -Dtest=AppExamPdfServiceTest,AppExamControllerTest test`

Expected: FAIL，PDF 服务和下载路由尚不存在。

- [ ] **Step 3: 实现 DOCX→PDF**

复用 `ExamPaperDocumentDispatcher` 与现有 LibreOffice 转换器的隔离 profile/超时/清理能力；缓存键使用试卷布局和题目快照指纹，不含用户答案。

- [ ] **Step 4: 运行 PDF 测试及现有预览回归**

Run: `cd AppBackend && mvn -Dtest=AppExamPdfServiceTest,AppExamControllerTest,LibreOfficePreviewConverterTest test`

Expected: PDF/控制器测试 PASS；仅允许 LibreOffice 环境探测产生显式 skip。

- [ ] **Step 5: Lore 提交**

---

### Task 7: 管理后台发布操作

**Files:**
- Modify: `AppWeb/src/api/examPaper.js`
- Modify: `AppWeb/src/pages/ai/ExamPaper/ExamPaperHistory.jsx`
- Create: `AppWeb/src/pages/ai/ExamPaper/examPaperPublishState.js`
- Create: `AppWeb/src/pages/ai/ExamPaper/examPaperPublishState.test.js`

**Interfaces:**
- Produces: `publishExamPaper(id)`、`unpublishExamPaper(id)`。

- [ ] **Step 1: 写发布状态纯函数测试并确认 RED**

覆盖状态标签、按钮文案、取消发布确认、操作后刷新当前页。

- [ ] **Step 2: 实现 API 和历史页操作**

列表显示发布状态/时间；按钮有独立 loading，发布和取消发布成功后刷新。

- [ ] **Step 3: 运行 Node、scoped ESLint、build**

Run: `cd AppWeb && node --test src/pages/ai/ExamPaper/examPaperPublishState.test.js`

Run: `cd AppWeb && npx eslint src/api/examPaper.js src/pages/ai/ExamPaper/ExamPaperHistory.jsx src/pages/ai/ExamPaper/examPaperPublishState.js src/pages/ai/ExamPaper/examPaperPublishState.test.js`

Run: `cd AppWeb && npm run build`

- [ ] **Step 4: Lore 提交**

---

### Task 8: App API、状态纯函数和页面注册

**Files:**
- Create: `AppFrontend/api/exam.js`
- Create: `AppFrontend/subpackage_exam/examState.js`
- Create: `AppFrontend/subpackage_exam/examState.test.js`
- Modify: `AppFrontend/pages.json`
- Modify: `AppFrontend/pages/mine/mine.vue`

**Interfaces:**
- Produces: 全部 App Exam API、`remainingSeconds(deadlineAt,serverNow)`、答案规范化、版本合并、重试计划。

- [ ] **Step 1: 写状态和契约测试并确认 RED**

覆盖倒计时、过期、五题型答案 payload、旧保存响应不覆盖新版本、1/2/5 秒重试、页面注册和“我的试卷”入口。

- [ ] **Step 2: 实现 API、纯函数、分包和入口**

PDF 使用 `uni.downloadFile`/`uni.openDocument`，不走 JSON request 封装解析。

- [ ] **Step 3: 运行 App 可用测试/静态检查**

Run: `node --test AppFrontend/subpackage_exam/examState.test.js`

Run: `node -e "JSON.parse(require('fs').readFileSync('AppFrontend/pages.json','utf8')); console.log('pages.json ok')"`

- [ ] **Step 4: Lore 提交**

---

### Task 9: App 试卷列表、详情和历史结果

**Files:**
- Create: `AppFrontend/subpackage_exam/paperList/paperList.vue`
- Create: `AppFrontend/subpackage_exam/paperDetail/paperDetail.vue`
- Create: `AppFrontend/subpackage_exam/attemptHistory/attemptHistory.vue`
- Create: `AppFrontend/subpackage_exam/attemptResult/attemptResult.vue`

- [ ] **Step 1: 建立页面静态契约测试或源契约测试并确认 RED**

断言加载/空/错误状态、开始/继续、PDF、历史、结果字段和下架历史入口。

- [ ] **Step 2: 实现四个页面**

所有页面使用 custom nav-bar 和当前 App 视觉模式；结果页仅消费已交卷结果 DTO。

- [ ] **Step 3: 运行 App 检查并 Lore 提交**

Run: `node --test AppFrontend/subpackage_exam/examState.test.js`

Expected: PASS，并通过页面源契约断言。

---

### Task 10: App 在线答题页与自动保存

**Files:**
- Create: `AppFrontend/subpackage_exam/attempt/attempt.vue`
- Modify: `AppFrontend/subpackage_exam/examState.js`
- Modify: `AppFrontend/subpackage_exam/examState.test.js`

- [ ] **Step 1: 写倒计时和并发保存测试并确认 RED**

覆盖 500ms debounce、每 30 秒服务端校时、后台 flush、旧版本响应淘汰、失败 1/2/5 秒重试、新答案取消旧重试、到时单次自动提交、卸载清理 timer。

- [ ] **Step 2: 实现五题型答题组件**

保存状态按题显示；交卷前确认未答数量；超时跳结果；页面只渲染安全 DTO。

- [ ] **Step 3: 运行 App 测试和静态检查**

Run: `node --test AppFrontend/subpackage_exam/examState.test.js`

Expected: PASS，计时器、保存并发和重试测试无泄漏 timer。

- [ ] **Step 4: Lore 提交**

---

### Task 11: 完整验证与跨端审查

**Files:**
- Modify only files required by verified defects discovered in this task.

- [ ] **Step 1: 后端新鲜验证**

Run: `cd AppBackend && mvn -Dtest=ExamPaperControllerTest,ExamPaperServiceImplTest,AppExamPersistenceContractTest,AppExamServiceImplTest,AppExamControllerTest,AppExamPdfServiceTest,LibreOfficePreviewConverterTest test`

Expected: 0 failures/errors；环境性 LibreOffice skip 必须明确。

- [ ] **Step 2: 管理后台验证**

Run: `cd AppWeb && node --test`

Run: scoped ESLint for changed AppWeb files.

Run: `cd AppWeb && npm run build`

- [ ] **Step 3: App 验证**

Run: `node --test AppFrontend/subpackage_exam/examState.test.js`

Run: `node -e "JSON.parse(require('fs').readFileSync('AppFrontend/pages.json','utf8')); console.log('pages.json ok')"`

随后检查 `AppFrontend/package.json` 是否存在可运行的 H5/小程序构建脚本；存在则执行对应脚本，不存在则在验证报告中记录“仓库未提供 App CLI 构建脚本”，不得伪报构建通过。

- [ ] **Step 4: 真实链路烟雾**

管理员发布含五题型试卷；普通用户开始、保存、退出续答、主动/超时交卷、查看结果、重复答题；下载并打开空白 PDF，确认无答案。

- [ ] **Step 5: 最终只读代码审查**

跨层核查答案泄露、所有权、并发 attempt、版本保存、服务端时间、判分、PDF 内容和发布状态。

- [ ] **Step 6: 修复 Important/Critical 后重跑覆盖测试并提交**

---

## 最终完成标准

- 管理员可发布/取消发布自己创建的有效试卷。
- App“我的试卷”展示所有已发布试卷和用户状态。
- 用户可重复答题，但同一时刻只有一条进行中记录。
- 自动保存、退出续答、倒计时和超时自动交卷以服务端为权威。
- 四种客观题判分正确，简答不计客观分。
- 交卷前无答案泄露，交卷后结果完整。
- 空白试卷 PDF 可下载打开且不含答案。
- 后端、管理后台和 App 相关测试/构建通过；真实五题型链路完成烟雾验证或明确记录不可控环境阻塞。
