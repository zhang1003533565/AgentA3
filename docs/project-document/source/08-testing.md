<!-- SECTION_BREAK chapter="第八章 测试与验证" -->
# 第八章 测试与验证

## 8.1 验证原则与结论口径

测试采用“需求可追踪、核心边界优先、结果可复现、缺口明确披露”的原则。功能存在、测试文件存在和测试成功是三个不同结论：源码能够证明实现入口，自动化测试能够证明特定样例下的行为，真实设备、外部服务和生产环境仍需独立验证。任何一项测试结果都必须同时记录命令、环境、执行时间、通过、失败、错误、跳过和未覆盖范围，不能只截取成功用例形成全量结论。

本章状态使用以下口径：implemented 表示当前实现和可复现证据能够支持；partial 表示存在实现或重点测试但集成覆盖不完整；known-limit 表示当前已确认的验证或能力缺口；planned 表示后续测试计划。错误 error 与断言失败 failure 分开统计，环境错误不能改写成业务用例通过，也不能据此推断全部业务失败。

<!-- CALLOUT type="evidence" title="最近一次已记录的完整离线质量门禁" -->
2026-07-15 19:28 在 `docs/verification/test-matrix.md` 记录的 `bash scripts/ci/quality-gate.sh` 六阶段结果为退出码 0：Java 389 tests / 0 failures / 0 errors / 1 skipped，Python 264 passed / 5 warnings，AppWeb Node 46/46、ESLint 0/0、Vite build PASS，AppFrontend 93/93，发布契约 9/9，知识/事实/压测 `validate-only` 与 Compose 静态配置均通过。该记录未访问外网、MaxKB 或模型供应商；其后整合分支继续加入 Java 一致性变更与测试，因此这些数字是最近一次可复现的完整门禁记录，不代表最终整合 SHA，提交前必须重跑并更新。
<!-- END_CALLOUT -->

## 8.2 测试层次与证据来源

| 层次 | 验证对象 | 当前证据 | 可支持结论 | 不能外推的结论 |
|---|---|---|---|---|
| Java 单元与切片测试 | 画像、掌握度、路径、考试反馈、工作流恢复、MaxKB、题库与试卷 | `AppBackend/src/test/java/` | 最近完整门禁记录为 389 项、0 failure/0 error，唯一 skip 有明确 opt-in 条件 | 后续整合已增加测试，须在最终 SHA 重跑；也不等同于真实外部端到端 |
| Python 测试 | Leader、typed DAG、复审、真实导出、SSE、内部令牌与地址 | `ai-servers/tests/` | 264 项新鲜通过，5 条 warning 有记录 | 未调用真实模型、多模态供应商或 MaxKB |
| 移动端状态测试 | 校园入口、四个学习页、五轮补问、六状态、考试反馈 | AppFrontend 全部 `*.test.js` | 93/93 通过，既有校园入口与学习主线均有回归 | 不等同于 HBuilderX 真机、弱网和系统权限验证 |
| Web 状态与构建 | 题库、试卷预览、菜单、地图实例和 React 19 兼容 | AppWeb tests、lint、build | 46/46，ESLint 0/0，production build 通过 | 不等同于真实浏览器登录、视觉回归；主 chunk 仍有体积警告 |
| 提交契约 | 知识清单、30 题金标、5×50 负载计划、五服务 Compose | `scripts/knowledge/`、`scripts/eval/`、`deploy/` | 结构校验与静态配置通过 | manifest=`needs_export`，在线评测/压测=`not_run`，Compose 未实启 |
| 视觉试卷验证 | DOCX 生成、真实预览与像素比较 | 试卷生成测试及视觉验证脚本 | 已覆盖的样例可检查预览与最终内容一致性 | 不代表所有模板、字体、平台和打印设备 |
| 文档源契约 | 章节、指令、事实披露、追踪 ID 和敏感信息 | `scripts/tests/test_agent_a3_project_document.py` | 本说明书底稿可被解析并满足静态契约 | 不替代最终 DOCX 与 PDF 逐页渲染检查 |

## 8.3 需求—设计—接口—测试追踪矩阵

追踪矩阵选择赛题主线和风险最高的边界。每项测试都应保存执行环境、角色、输入、预期、实际和证据路径；若当前只能验证边界而不能完成端到端运行，状态列必须明确写为部分验证或待执行。

| 测试 ID | 需求 | 功能与技术设计 | 接口或数据 | 验证重点 | 当前证据与状态 |
|---|---|---|---|---|---|
| TC-01 | FR-001、FR-002、FR-024、NFR-004 | FUNC-01 | API-01、Role | App 与 Web 入口角色分离、ADMIN 拒绝和商户试卷缺口 | Java 登录与控制器测试；授权缺口为 known-limit |
| TC-02 | FR-003、FR-004、NFR-001、NFR-002 | FUNC-02、TECH-02 | API-03、API-11 至 API-13 | 校园 Leader 单目标路由、课程六节点 DAG、复审、SSE、`partial` 与单项重试 | Python workflow/SSE 测试、Java 工作流恢复测试与 App 六状态测试通过；真实模型端到端为 partial |
| TC-03 | FR-005 至 FR-008、NFR-003 | FUNC-03、TECH-01 | API-04、API-05、API-14、API-15 | 五轮补问、七维快照、candidate/applied、掌握度、路径版本和推荐互动 | Java 画像/路径测试与 App 学习页测试通过；真实课程效果未评估 |
| TC-04 | FR-015 至 FR-017、NFR-003 | FUNC-04、TECH-06 | API-11 至 API-13、资源信封 | 六类映射、审核拒绝排除、真实导出、空附件失败、越权下载和恢复合并 | Python 资源/导出测试与 Java 持久化重建通过；真实至少五类产物待演示留证 |
| TC-05 | FR-009 至 FR-011、NFR-006 | FUNC-05、TECH-03、TECH-04 | API-06、MeetingRecord | partial 与 final、会后顺序处理、ASR 失败保留确认记录 | Java 与 Python 重点用例；真实讯飞稳定性为 partial |
| TC-06 | FR-012 至 FR-014、NFR-005 | FUNC-06、TECH-05 | API-07、KnowledgeChatServiceImpl | ADMIN 校验、hit-test、grounded context、系统回答与 citations | `KnowledgeChatServiceImplTest.java`；目标学生教师入口尚未开放 |
| TC-07 | FR-018、FR-019、NFR-007 | FUNC-07、TECH-07 | API-08、题型 JSON | 五类接口题型、非法 JSON 拒绝、人工确认后导入 | 控制器、材料解析和服务测试；教师 Web 授权为 partial |
| TC-08 | FR-020、FR-021、NFR-003、NFR-004 | FUNC-08、TECH-07 | API-09、预览会话和一次性证明 | 手工随机组卷、容量不足、证明过期消费和预览最终一致 | 试卷服务、预览控制器与视觉验证；商户授权缺口为 known-limit |
| TC-09 | FR-022、FR-023、NFR-003 | FUNC-09、TECH-08 | API-10、ExamPaperAttemptAnswer、learningUpdate | 快照、自动保存、重复交卷、客观评分、反馈幂等、掌握度/candidate/路径一致性 | `AppExamLearningFeedback*Test` 与结果页测试通过；主观题和真实课程端到端为 partial |
| TC-10 | NFR-002、NFR-004、NFR-008、NFR-009 | FUNC-10、TECH-02/03 | 配置、内部令牌、WebSocket 和认证上下文 | 环境变量、Java→Python 令牌、日志脱敏、密码、Origin 与最小权限 | 令牌/配置测试通过；密码哈希、Origin、secret 历史扫描和渗透未完成 |
| TC-11 | NFR-005、NFR-006、NFR-010 | FUNC-11、总体部署设计 | 五服务 Compose、健康检查、评测与负载契约 | 静态 Compose、六阶段门禁、30 题与 5×50 计划 | 静态/离线门禁通过；容器实启、在线评测/压测和真机均待执行 |

追踪关系不把 `needs_export` 或 `not_run` 伪装为通过。学生和教师 MaxKB 入口、教师 Web 题库治理、普通助手服务端取消、真实 Python 知识库、真实模型、多模态、容器实启、在线指标和真机仍只能验证既有边界或定义未来验收方法。

## 8.4 Java 自动化验证

### 8.4.1 最近完整门禁记录

| 指标 | 当前记录 | 解释 |
|---|---:|---|
| tests | 389 | 2026-07-15 19:28 完整门禁收集到的测试数量；最终整合 SHA 待重跑 |
| failures | 0 | 没有断言失败 |
| errors | 0 | H2 隔离配置下没有环境错误 |
| skipped | 1 | 仅 opt-in 视觉矩阵生成测试未启用 |

`389 tests / 0 failures / 0 errors / 1 skipped` 是本次文档可引用的最近完整 Java 门禁记录，不是最终整合 SHA 的预测值。唯一 skipped 为 `SourcePaperVisualFixtureTest.writesSourceFaithfulVisualMatrix`，只在提供 `-Dexam.visual.output=...` 时生成视觉 QA 文件，不是功能失败；真实 MySQL、MaxKB 与外部服务仍未由该套件验证。

### 8.4.2 重点测试面

| 领域 | 代表性证据 | 主要断言 |
|---|---|---|
| MaxKB 问答 | `AppBackend/src/test/java/com/example/appbackend/service/impl/KnowledgeChatServiceImplTest.java` | hit-test 结果处理、输入构造、模型失败、answer 与 citations 组合 |
| AI 题目生成 | `QuestionGenerationControllerTest.java`、`QuestionGenerationMaterialParserTest.java`、`QuestionGenerationServiceImplTest.java` | ADMIN 边界、材料解析、题型 JSON 和非法候选拒绝 |
| 试卷生成 | `ExamPaperServiceImplTest.java`、`ExamPaperDocumentGeneratorTest.java` | 手工随机组卷、分值规则与文档输出 |
| 真实预览 | `ExamPaperPreviewControllerTest.java`、`ExamPaperPreviewServiceImplTest.java` | 预览会话、签名、过期、一次性证明和失败状态 |
| 在线考试 | `AppExamPersistenceContractTest.java`、`AppExamServiceImplTest.java` | 尝试与答案持久化、版本冲突、自动保存、交卷和客观评分 |
| 学习路径与考试反馈 | `LearningPathServiceImplTest.java`、`LearningWorkflowServiceImplTest.java`、`AppExamLearningFeedbackTest.java`、`AppExamLearningFeedbackPersistenceTest.java` | 路径版本、工作流恢复、`partial` 合并、掌握度、candidate、重规划和幂等 |
| 其他领域 | `AppBackend/src/test/java/` 下相应控制器与服务测试 | 身份、会议、画像、资源和校园服务的局部契约 |

当前门禁通过 `application-test.yml` 使用隔离 H2 测试环境，不依赖开发机 MySQL。涉及外部 MaxKB、讯飞、模型和 Python HTTP 的用例使用替身或本地临时服务；测试报告不复制真实连接值，也不把替身响应当作生产连通性。

## 8.5 Python、前端与提交契约验证

Python 侧在当前质量门禁中执行完整 `ai-servers/tests`，得到 264 passed、5 warnings。覆盖普通 Leader、RAG 路由、六资源 typed DAG、统一复审、代码/导图/题库/PPT 等真实导出、SSE、Java 工具复用、内部令牌与环境地址。警告保留在原始输出中；结果不包含真实外部模型、多模态供应商或 MaxKB 调用。

```bash
python -m pytest ai-servers/tests/test_rag_api_routes.py
python -m pytest ai-servers/tests/test_assistant_resource_builder.py
python -m pytest ai-servers/tests/test_learning_workflow.py ai-servers/tests/test_learning_workflow_routes.py
python -m pytest ai-servers/tests/test_learning_exports.py ai-servers/tests/test_generated_export_download.py
python -m pytest ai-servers/tests/test_internal_auth.py ai-servers/tests/test_memory_store.py
```

移动端全部 Node 测试为 93/93。除原 AI 与考试状态外，`subpackage_learning` 测试锁定四个学习页面、五轮画像问题、六个诚实页面态、SSE 恢复、单项重试、后端拥有的路径状态、推荐动作、安全 Markdown 和校园入口保留；它们仍不证明麦克风权限、真实文件保存、弱网或不同手机系统行为。

Web 端 Node 测试为 46/46，ESLint 为 0 error/0 warning，Vite production build 通过；构建仍报告主 JS chunk 大于 500 kB。测试覆盖生成候选、预览并发、React 19 兼容、菜单与地图实例生命周期，但权限结论仍应由真实角色调用确认，特别是 MERCHANT 试卷缺口。

提交契约 unittest 为 9/9；知识清单、30 题金标和 5×50 负载计划的 `validate-only` 通过，五服务 Compose 可静态解析。这里的 PASS 只表示结构与阈值合法：Python 课程 manifest 仍为 `needs_export`，事实和负载报告仍为 `not_run`，没有任何在线准确率、拒答率、QPS、P95 或失败率数字。

## 8.6 异常与恢复验证

| 异常场景 | 注入方式 | 预期结果 | 数据检查 |
|---|---|---|---|
| 模型调用失败 | 让模型适配返回超时或结构错误 | AI 任务进入失败终态，SSE 发出错误而非完成 | 半截回答不标记为最终，业务事实不被模型直接改写 |
| MaxKB 超时或无命中 | hit-test 超时或返回空 references | 返回检索失败、资料不足或受限回答 | 不生成伪造 citations，不改知识库状态 |
| Redis 不可用 | 关闭 Redis 后启动记忆服务 | Python 记忆退化为进程内存并显式记录运行边界 | 不宣称跨进程、重启后记忆仍持久化 |
| SSE 中断 | complete 前断开客户端连接 | 页面将回答标为不完整并提供安全重试 | 不把本地 AbortController 记为服务端取消成功 |
| 单资源审核或导出失败 | 拒绝一个可选资源、返回空附件或让导出器失败 | 工作流终态为 `partial`，失败类型可单独重试 | 失败项不进入资源集合；重试不删除既有成功项和路径 |
| 工作流短期状态丢失 | 清除 Redis/内存状态后查询已完成工作流 | 从持久化助手消息聚合资源、错误和终态 | 多条消息中的成功资源不丢失，不把 partial 重建成 completed |
| 会议 ASR 断开 | 中断外部 WebSocket 或发送错误帧 | 保留已确认 final，停止虚假的识别中状态 | 不用模型内容补齐缺失转写 |
| 答案版本冲突 | 以旧版本提交自动保存 | 返回冲突和当前权威版本 | 服务端新答案不被旧请求覆盖 |
| 考试反馈重复处理 | 对同一终态提交重复读取或重复执行反馈 | 返回相同学习更新或跳过重复应用 | 掌握度、画像 candidate 与路径版本不重复变化 |
| 内部令牌错误 | 配置 `AI_INTERNAL_TOKEN` 后缺少或提交错误 header | Python `/internal/*` 返回 401，健康检查保持可用 | 不创建 AI 任务；日志不记录令牌值 |
| 预览证明失效 | 预览后修改题目或版式 | 拒绝最终创建并要求重新预览 | 失效证明不被消费为成功 |
| 下载完整性失败 | 使用未登记、缺失或校验失败资源 | 拒绝下载并提示重新生成 | 不泄露服务器绝对路径或其他用户资源 |

异常测试既验证错误码，也验证失败后状态。一次请求收到错误不代表系统已经恢复；必须进一步检查任务终态、事务回滚、旧配置是否保留、文件登记是否孤立、答案是否被覆盖以及用户是否获得可操作反馈。

## 8.7 试卷视觉与内容一致性验证

试卷链路除单元断言外，还需要真实文档生成、LibreOffice 预览、页面渲染和像素或结构比较。当前可引用的工程证据包括 `AppBackend/src/test/java/com/example/appbackend/service/ExamPaperDocumentGeneratorTest.java`、预览控制器与服务测试、`scripts/verify_exam_paper_visual.py` 和 `scripts/exam_paper_manual_verdict.json`。这些证据用于确认受测模板中的中文、填空题答题线、答案卷、页面格式以及预览与最终输出的一致性边界。

视觉结论必须附带样例、字体环境、转换器和比较阈值。已验证样例不能外推为所有模板、操作系统、Office 版本、字体和打印机均一致。若环境缺少指定中文字体，应记录字体替代及其版面影响；下载 DOCX 必须保留源码模板结构，预览副本的字体适配不能反向改写下载文档。

| 验证步骤 | 检查点 | 通过条件 | 保留证据 |
|---|---|---|---|
| 生成 DOCX | 题干、选项、填空线、答案、页眉页脚和页面格式 | 文档结构与请求一致，无原始 JSON 泄露 | 测试报告和生成样例摘要 |
| 转换预览 | LibreOffice 能打开并生成 PDF | 转换成功，中文和受支持题型可读 | 转换日志的脱敏摘要 |
| 页面渲染 | 每页转为图像 | 无裁切、重叠、空白异常和不可读字体 | 页面图像与人工判定记录 |
| 预览最终比较 | 使用同一确认内容生成最终文档 | 受测页面像素或结构在阈值内一致 | 比较结果与预览证明关联 |
| 下载复核 | 从业务下载入口取得 DOCX | 文件可打开且内容与已确认预览相符 | 文件摘要和接口状态，不保存本地绝对路径 |

## 8.8 安全、隐私与文档静态检查

安全验证当前以边界检查和整改清单为主，不能称为渗透测试或合规认证。重点检查非 ADMIN 调用治理接口、MERCHANT 试卷链路、资源与考试数据归属、Authorization/内部令牌日志、业务密码、WebSocket Origin 和下载路径。跟踪配置中的 JWT 与第三方活动凭据已改为环境变量或空值，内部令牌已有自动化测试；但数据库开发默认口令仍存在，Git 历史 secret/PII 扫描与轮换未执行，业务密码比较和会议 WebSocket 仍需加固。

文档静态检查覆盖以下项目：源文件精确数量、章节标题、支持的指令、FR、NFR、FUNC、TECH、API 与测试追踪 ID、禁止结论、占位符、密钥特征和本地绝对路径。静态扫描成功只能证明底稿文本满足规则，最终 DOCX 和 PDF 仍需逐页检查目录、标题、表格、图注、字体、裁切和分页。

## 8.9 验证缺口与退出条件

| 缺口 | 当前状态 | 完成条件 | 结论限制 |
|---|---|---|---|
| Java 离线门禁 | completed（最近完整记录） | 389 tests / 0 failures / 0 errors / 1 opt-in skip | 后续整合新增测试，最终 SHA 必须重跑；不外推为真实外部通过 |
| Python 离线门禁 | completed | 264 passed / 5 warnings | 不外推为真实模型、MaxKB 和多模态通过 |
| Python 课程知识库 | needs_export | 冻结真实 MaxKB 版本、来源、许可、哈希、文档/分段并完成恢复导入 | 当前仓库包不是可恢复生产知识库 |
| 在线事实评测 | not_run | 使用真实 endpoint 运行 20 deterministic + 5 rubric + 5 refusal 并复核 | 不编造准确率、证据覆盖或拒答率 |
| 在线性能测试 | not_run | 使用冻结 5 并发×50 请求计划采集原始报告 | 不编造 QPS、P95 或可用性 SLA |
| 真机测试 | 未执行 | 覆盖代表性 Android 或目标设备、权限、弱网和下载 | 前端状态测试不等同于真机 |
| 渗透测试 | 未执行 | 在授权环境完成认证、授权、输入和依赖边界测试 | 不宣称已通过安全认证 |
| 五服务容器实启 | 未执行 | 构建 SHA 标记镜像，`up -d` 并通过 `deploy/verify.sh` | Compose 静态通过不等于一键部署已运行 |
| 生产端到端测试 | 未执行 | 在生产等价拓扑运行 Java、Python、外部服务和多端流程 | 不宣称全部外部依赖可用 |
| 讯飞稳定性 | partial | 真实凭据环境下覆盖连接、断线、限流和长会话 | 单元测试不证明外部稳定性 |
| 开源许可证复核 | P0 未完成 | 补齐模板来源、PyMuPDF 路径、锁文件、传递依赖 SBOM 和许可证文本 | NOTICE/直接依赖清单不等于合规审计完成 |

发布退出条件至少包括：整合分支后重新执行六阶段门禁；导出真实 Python 课程知识库；真实至少五类资源、30 题评测与 5×50 压测留证；五服务实构建/实启；角色与数据归属回归；secret/PII 与许可证 P0；最终 DOCX/PDF 逐页检查和 7 分钟内视频。任何未满足条件都保持 `needs_export`、`not_run`、partial 或 known-limit，不用“基本通过”替代证据。
