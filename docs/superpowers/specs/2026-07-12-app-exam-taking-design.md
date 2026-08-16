# App 在线答题与试卷 PDF 设计

## 目标

在 App 端“我的”页面增加“我的试卷”入口。管理员发布试卷后，所有已登录 App 用户都可以查看试卷、下载空白试卷 PDF、在线限时答题、自动保存进度、交卷后查看客观题成绩和答案解析，并保留每一次独立答题记录。

## 已确认范围

- 管理员发布的试卷对所有 App 用户可见。
- 每套试卷允许重复答题，每次提交保存为独立记录。
- 使用试卷配置的 `durationMinutes` 倒计时；到时自动交卷。
- 中途退出自动保存，再进入时继续原截止时间，不重新计时。
- 单选、多选、判断、填空自动判分；简答题保存答案但不自动计分。
- 交卷后展示标准答案、解析和简答题参考答案。
- App 只提供空白试卷 PDF，不提供答案版 PDF。
- 直接在当前 `master` 分支实施，不创建功能分支或 worktree。

## 方案选择

采用现有试卷快照扩展方案。在线答题、判分和 PDF 都以 `exam_paper_question` 的题目快照为准，不重新读取当前题库。这样题库后续修改或停用不会改变已经发布的试卷、进行中的答题和历史成绩。

不采用以下方案：

- 答题时重新读取 `exam_question`：题库修改会导致试卷和成绩漂移。
- 复制成 App 专用试卷表：重复保存现有完整试卷快照，维护成本高且没有必要。

## 管理后台发布流程

“生成的试卷”列表和详情增加发布状态与操作：

- 状态：未发布、已发布。
- 未发布试卷可执行“发布到 App”。
- 已发布试卷可执行“取消发布”。
- 发布前要求试卷状态有效、至少包含一道题、考试时长为正整数。
- 发布记录 `published=true`、`publishTime`。
- 取消发布只阻止新用户开始答题；已有进行中答题可以继续并交卷，历史记录始终可查看。
- 再次发布保留历史答题记录，并更新发布时间。

`exam_paper` 新增：

```text
published       BIT NOT NULL DEFAULT 0
publish_time    DATETIME NULL
```

发布和取消发布接口仅允许管理员调用。现有创建者仍只能管理自己创建的试卷；不能发布其他管理员的试卷。

## App 信息架构

### “我的”页面

在现有“我的课表”“我的活动”等入口中增加“我的试卷”：

- 显示已发布试卷数量。
- 点击进入试卷列表页。

### 试卷列表页

每张试卷卡片展示：

- 标题、副标题；
- 题量、总分；
- 考试时长；
- 发布时间；
- 当前用户已完成次数；
- 是否存在一条进行中的答题。

操作：

- 无进行中记录：“开始答题”；
- 有进行中记录：“继续答题”；
- “下载 PDF”；
- “答题记录”。

列表只返回已发布且状态有效的试卷。取消发布后不再出现在公开列表，但用户仍可从自己的历史记录进入已完成结果或继续已开始记录。

### 在线答题页

页面顶部固定显示：

- 试卷标题；
- 已答数量 / 总题量；
- 服务端截止时间计算出的剩余时间；
- 提交按钮。

题型交互：

- 单选：单选控件；
- 多选：多选控件；
- 判断：正确 / 错误；
- 填空：按 `blank.id` 展示并保存各空答案；
- 简答：多行文本输入。

交卷前不返回标准答案、解析、正确选项或参考答案。试题 DTO 必须使用专门的答题视图，不能直接复用包含 `answerJson` 的管理后台 DTO。

### 答题记录和结果页

答题记录按提交时间倒序展示：

- 第几次答题；
- 开始、交卷时间；
- 客观题得分 / 客观题总分；
- 状态：已交卷、超时自动交卷。

结果页展示：

- 客观题总成绩；
- 每题用户答案；
- 标准答案；
- 是否正确和客观题得分；
- 题目解析；
- 简答题参考答案及“未自动计分”标记。

不显示一个包含简答题零分的误导性总分，统一显示“客观题得分 / 客观题总分”。

## 答题数据模型

### `exam_paper_attempt`

```text
id                       BIGINT PK
paper_id                 BIGINT NOT NULL
user_id                  BIGINT NOT NULL
attempt_no               INT NOT NULL
status                   VARCHAR(20) NOT NULL  -- IN_PROGRESS / SUBMITTED / AUTO_SUBMITTED
started_at               DATETIME NOT NULL
deadline_at              DATETIME NOT NULL
submitted_at             DATETIME NULL
objective_score          DECIMAL(10,2) NOT NULL DEFAULT 0
objective_total_score    DECIMAL(10,2) NOT NULL DEFAULT 0
answered_count           INT NOT NULL DEFAULT 0
question_count           INT NOT NULL
create_time              DATETIME NOT NULL
update_time              DATETIME NOT NULL
```

约束和索引：

- `(paper_id, user_id, attempt_no)` 唯一。
- 查询用户答题记录索引 `(user_id, paper_id, status, started_at)`。
- 同一用户同一试卷只能有一条 `IN_PROGRESS`。数据库使用可空活动标记或事务级锁实现，不只依赖应用层先查后插，避免并发创建两条进行中记录。

### `exam_paper_attempt_answer`

```text
id                 BIGINT PK
attempt_id         BIGINT NOT NULL
paper_question_id  BIGINT NOT NULL
answer_json        LONGTEXT NOT NULL
answered           BIT NOT NULL DEFAULT 0
correct            BIT NULL
score              DECIMAL(10,2) NULL
create_time        DATETIME NOT NULL
update_time        DATETIME NOT NULL
```

约束和索引：

- `(attempt_id, paper_question_id)` 唯一。
- 保存答案使用 upsert 语义，同一题只保留当前答案。
- 交卷时以数据库中的最终答案重新判分，不信任客户端提交的正确性或分数。

答题记录直接关联 `ExamPaperQuestion` 快照。试卷题目快照在试卷创建后不可修改；发布状态变化不影响快照。

## 后端接口

### 管理员接口

在现有试卷管理接口增加：

```text
POST /api/exam/papers/{id}/publish
POST /api/exam/papers/{id}/unpublish
```

要求角色为 `ADMIN`，并校验 `createdBy` 所有权。

### App 试卷接口

新增：

```text
GET  /api/app/exam-papers
GET  /api/app/exam-papers/{paperId}
GET  /api/app/exam-papers/{paperId}/pdf
POST /api/app/exam-papers/{paperId}/attempts
GET  /api/app/exam-attempts/{attemptId}
PUT  /api/app/exam-attempts/{attemptId}/answers/{paperQuestionId}
POST /api/app/exam-attempts/{attemptId}/submit
GET  /api/app/exam-papers/{paperId}/attempts
GET  /api/app/exam-attempts/{attemptId}/result
```

接口均从 JWT 请求属性读取当前用户，不接受客户端传入 `userId`。

开始答题接口：

- 若已有未超时的 `IN_PROGRESS`，返回该记录，不创建新记录。
- 若已有记录已超时，先按当前答案自动交卷，再创建新记录。
- 若试卷已取消发布且没有进行中记录，拒绝开始。
- `deadlineAt = startedAt + durationMinutes`，由服务端生成。

保存答案接口：

- 校验答题记录归当前用户所有；
- 校验状态仍为 `IN_PROGRESS`；
- 校验题目属于该试卷；
- 校验尚未到截止时间；若已到期，先自动交卷并返回“答题已结束”；
- 校验答案结构与题型匹配；
- 保存后返回服务端确认时间和已答数量。

交卷接口：

- 使用事务和状态条件更新实现幂等；
- 重复提交返回同一结果，不重复判分或新增记录；
- 截止时间已过时状态记为 `AUTO_SUBMITTED`；
- 未超时主动交卷记为 `SUBMITTED`。

## 客观题判分

判分只使用 `exam_paper_question.answer_json` 和快照分值。

- 单选：规范化选项 key 后完全一致得满分。
- 多选：去重并排序后集合完全一致得满分；不提供部分分。
- 判断：布尔值完全一致得满分。
- 填空：按照 `blank.id` 对应；每个答案去除首尾空格后完全一致，全部空正确才得满分；不提供部分分。
- 简答：`correct=null`、`score=null`，不加入客观题总分。

空答案计为未答和错误。后端忽略客户端提交的分数、正确性和标准答案字段。

## 自动保存和倒计时

- App 每次答案变化后使用 500ms debounce 保存到后端。
- 页面切后台或退出前尽力执行一次保存，但可靠性以每次变化后的服务端保存为准。
- 页面重新进入时读取服务端答案和 `deadlineAt`。
- 倒计时使用 `deadlineAt - 当前时间`，App 本地计时只负责显示。
- 答题页每 30 秒向服务端读取答题状态，校正手机时间漂移。
- 剩余时间为零时自动提交。
- 即使 App 没有运行，后端在读取、保存或提交该记录时也会检测截止时间并完成超时交卷。

## PDF 下载

App PDF 接口只允许：

- 已登录用户；
- 试卷当前已发布，或当前用户拥有该试卷的答题记录；
- 内容固定为空白试卷，不接收 `content=answer`。

生成流程：

1. 使用 `ExamPaperQuestion` 快照和试卷布局生成与管理后台一致的试卷 DOCX；
2. 复用现有 LibreOffice 转换能力生成 PDF；
3. 返回 `application/pdf` 和安全的 UTF-8 文件名；
4. 临时文件隔离并清理；
5. 不返回答案版。

PDF 可以按试卷内容和布局指纹做短期缓存，缓存不得包含用户答案。

## 权限与安全

- 管理员发布接口必须同时校验角色和试卷所有权。
- App 用户只能访问自己的答题记录。
- 交卷前 DTO 不含任何标准答案、解析和评分规则。
- 结果接口只在答题记录已提交后返回答案解析。
- 下载接口不接受答案内容参数。
- 单题答案 JSON 请求体限制为 64 KiB，单个文本答案限制为 20,000 个字符。
- 开始答题和提交依靠事务幂等；答案保存采用每题版本号；PDF 接口对单用户单试卷最多每 10 秒生成一次，命中缓存不重复转换。
- 日志只记录 paperId、attemptId、userId、状态、题量、耗时，不记录完整答案和标准答案。

## App 页面文件与导航

在 `AppFrontend/pages/mine/mine.vue` 增加“我的试卷”入口，新增页面：

```text
AppFrontend/pages/exam/paper-list.vue
AppFrontend/pages/exam/paper-detail.vue
AppFrontend/pages/exam/attempt.vue
AppFrontend/pages/exam/attempt-history.vue
AppFrontend/pages/exam/attempt-result.vue
```

新增 `AppFrontend/api/exam.js`，沿用现有 token、请求和文件下载封装。页面注册到 `pages.json`；如主包体积明显增长，可放入独立分包，但第一优先是遵循当前项目的页面组织模式。

下载 PDF 在 App/小程序环境中使用平台文件 API 保存或打开，不尝试使用 Web DOM 下载方式。

## 错误处理

- 试卷取消发布：新答题提示“试卷已下架”；已有进行中答题仍可继续。
- 答题已超时：自动交卷并跳转结果页。
- 自动保存失败：保留本地输入、显示未保存状态，并按 1 秒、2 秒、5 秒间隔重试；新版本答案到来时取消旧版本重试。
- 并发保存：使用答案更新时间或版本号避免旧请求覆盖新答案。
- 重复交卷：返回已有结果。
- PDF 转换失败：显示明确错误，不回退下载 DOCX。
- 网络恢复后重新读取服务端 attempt 和答案，再合并尚未保存的本地最新输入。

## 测试策略

### 后端

- 发布/取消发布的管理员角色、创建者所有权、空试卷和时长校验。
- App 列表只返回已发布试卷；取消发布后历史和进行中记录仍可访问。
- 并发开始答题只产生一条 `IN_PROGRESS`。
- 截止时间、继续答题、超时自动交卷和重复交卷幂等。
- 用户只能访问自己的 attempt。
- 五种题型答案结构校验和四种客观题判分。
- 填空按 ID 对应、多选去重排序、简答不计客观分。
- 交卷前响应不泄露标准答案，交卷后结果完整。
- PDF 只生成试卷内容、权限正确、Content-Type 正确、转换失败可见。

### App

- “我的”入口、试卷列表状态和导航。
- 开始/继续答题分支。
- 五种题型输入组件与自动保存。
- 倒计时恢复、服务端校时、到时自动交卷。
- 自动保存失败重试和新答案不被旧响应覆盖。
- 交卷确认、未答题统计、结果和历史页面。
- PDF 下载/打开的 App 与小程序平台行为。

### 完整验证

- 后端相关单元和控制器测试；
- App 静态检查与可用构建命令；
- 使用管理员发布一套包含五种题型的试卷；
- 使用普通用户完成开始、退出续答、自动保存、超时/主动交卷、结果查看、重复答题；
- 下载并打开空白试卷 PDF，确认没有答案。

## 非目标

- 不支持按班级或指定用户分发。
- 不支持简答题 AI 判分或人工批改。
- 不提供答案版 PDF。
- 不提供防作弊、摄像头监考、切屏检测或题目乱序。
- 不允许用户修改已提交记录。
- 不在本期实现管理员查看全体用户成绩统计。
