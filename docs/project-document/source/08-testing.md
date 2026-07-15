<!-- SECTION_BREAK chapter="第八章 测试与验证" -->
# 第八章 测试与验证

## 8.1 验证原则与结论口径

测试采用“需求可追踪、核心边界优先、结果可复现、缺口明确披露”的原则。功能存在、测试文件存在和测试成功是三个不同结论：源码能够证明实现入口，自动化测试能够证明特定样例下的行为，真实设备、外部服务和生产环境仍需独立验证。任何一项测试结果都必须同时记录命令、环境、执行时间、通过、失败、错误、跳过和未覆盖范围，不能只截取成功用例形成全量结论。

本章状态使用以下口径：implemented 表示当前实现和可复现证据能够支持；partial 表示存在实现或重点测试但集成覆盖不完整；known-limit 表示当前已确认的验证或能力缺口；planned 表示后续测试计划。错误 error 与断言失败 failure 分开统计，环境错误不能改写成业务用例通过，也不能据此推断全部业务失败。

<!-- CALLOUT type="risk" title="当前 Java 结论" -->
当前 Java 结果为 324 tests / 0 failures / 1 environment error / 1 skipped。环境错误与本地测试数据库不存在有关，因此本报告不能写成 Java 全量测试全部成功；应先修复或提供隔离数据库，再以同一命令获取新的完整结果。
<!-- END_CALLOUT -->

## 8.2 测试层次与证据来源

| 层次 | 验证对象 | 当前证据 | 可支持结论 | 不能外推的结论 |
|---|---|---|---|---|
| Java 单元与切片测试 | 画像规则、MaxKB 问答、题目生成、试卷预览、考试状态等 | `AppBackend/src/test/java/` | 关键服务和控制器边界具备自动化用例 | 数据库环境错误存在，不能称全量全绿 |
| Python 重点测试 | RAG 路由、资源信封、导出下载、专业智能体 | `ai-servers/tests/` | 若按给定命令新鲜执行，可报告对应用例结果 | 不从缓存计算 Python 测试通过率，也不以缓存代表全套结果 |
| 移动端状态测试 | AI 消息、考试页面和考试状态 | `AppFrontend/subpackage_ai/assistantMessage.test.js` 等 | 关键纯状态和页面逻辑有自动化覆盖 | 不等同于 HBuilderX 真机、弱网和系统权限验证 |
| Web 状态测试 | AI 题目生成与试卷预览交互 | `AppWeb/src/pages/questionBank/questionGenerationState.test.js` 等 | 候选生成与预览状态边界有用例 | 不等同于真实浏览器、登录角色和后端端到端验证 |
| 视觉试卷验证 | DOCX 生成、真实预览与像素比较 | 试卷生成测试及视觉验证脚本 | 已覆盖的样例可检查预览与最终内容一致性 | 不代表所有模板、字体、平台和打印设备 |
| 文档源契约 | 章节、指令、事实披露、追踪 ID 和敏感信息 | `scripts/tests/test_agent_a3_project_document.py` | 本说明书底稿可被解析并满足静态契约 | 不替代最终 DOCX 与 PDF 逐页渲染检查 |

## 8.3 需求—设计—接口—测试追踪矩阵

追踪矩阵选择赛题主线和风险最高的边界。每项测试都应保存执行环境、角色、输入、预期、实际和证据路径；若当前只能验证边界而不能完成端到端运行，状态列必须明确写为部分验证或待执行。

| 测试 ID | 需求 | 功能与技术设计 | 接口或数据 | 验证重点 | 当前证据与状态 |
|---|---|---|---|---|---|
| TC-01 | FR-001、FR-002、FR-024、NFR-004 | FUNC-01 | API-01、Role | App 与 Web 入口角色分离、ADMIN 拒绝和商户试卷缺口 | Java 登录与控制器测试；授权缺口为 known-limit |
| TC-02 | FR-003、FR-004、NFR-001、NFR-002 | FUNC-02、TECH-02 | API-03、AI 会话和任务 | Leader 单次路由、SSE 终态、工具失败和本地中止边界 | Python 智能体重点测试与移动端消息状态测试；端到端为 partial |
| TC-03 | FR-005 至 FR-008、NFR-003 | FUNC-03、TECH-01 | API-04、API-05、UserProfileEvidence | 七维快照、candidate 到 applied、重复冲突和只读总结 | Java 画像服务测试；考试未自动回写为 known-limit |
| TC-04 | FR-015 至 FR-017、NFR-003 | FUNC-04、TECH-06 | 资源信封、AiLeaderResourceInteraction | 来源、grounding、integrity、互动、受控导出和越权下载 | Python 资源与导出测试、Java 互动实体；媒介组合不作固定承诺 |
| TC-05 | FR-009 至 FR-011、NFR-006 | FUNC-05、TECH-03、TECH-04 | API-06、MeetingRecord | partial 与 final、会后顺序处理、ASR 失败保留确认记录 | Java 与 Python 重点用例；真实讯飞稳定性为 partial |
| TC-06 | FR-012 至 FR-014、NFR-005 | FUNC-06、TECH-05 | API-07、KnowledgeChatServiceImpl | ADMIN 校验、hit-test、grounded context、系统回答与 citations | `KnowledgeChatServiceImplTest.java`；目标学生教师入口尚未开放 |
| TC-07 | FR-018、FR-019、NFR-007 | FUNC-07、TECH-07 | API-08、题型 JSON | 五类接口题型、非法 JSON 拒绝、人工确认后导入 | 控制器、材料解析和服务测试；教师 Web 授权为 partial |
| TC-08 | FR-020、FR-021、NFR-003、NFR-004 | FUNC-08、TECH-07 | API-09、预览会话和一次性证明 | 手工随机组卷、容量不足、证明过期消费和预览最终一致 | 试卷服务、预览控制器与视觉验证；商户授权缺口为 known-limit |
| TC-09 | FR-022、FR-023、NFR-003 | FUNC-09、TECH-08 | API-10、ExamPaperAttemptAnswer | 快照、自动保存版本、重复交卷、客观评分和主观题边界 | Java 考试服务与移动端状态测试；画像回写未实现 |
| TC-10 | NFR-002、NFR-004、NFR-008、NFR-009 | FUNC-10、TECH-03 | 配置、WebSocket 和认证上下文 | 密码、凭据、Origin、日志脱敏与最小权限 | 静态检查与已知限制；渗透和生产安全验证未执行 |
| TC-11 | NFR-005、NFR-006、NFR-010 | FUNC-11、总体部署设计 | MySQL、Redis、Java、Python、Web 与 App | 服务分别启动、健康检查、故障降级和端侧兼容 | 部署说明与重点自动化测试；生产端到端和真机待执行 |

追踪关系不把 planned 项伪装为通过。尤其是学生和教师的 MaxKB 入口、教师 Web 题库治理、考试到画像证据、独立学习路径以及服务端任务取消，目前只能验证已存在边界或定义未来验收方法。

## 8.4 Java 自动化验证

### 8.4.1 当前执行结果

| 指标 | 当前记录 | 解释 |
|---|---:|---|
| tests | 324 | 当前报告收集到的测试数量 |
| failures | 0 | 没有断言失败，但不等于整个套件成功 |
| environment errors | 1 | 本地测试数据库不存在导致的环境错误 |
| skipped | 1 | 一项未在当前环境执行 |

`324 tests / 0 failures / 1 environment error / 1 skipped` 是本次文档使用的唯一 Java 汇总表述。修复环境后应重新执行 Maven 测试并保存新鲜报告；在新结果产生前，不删除 error，也不把 0 failures 换算成百分之百通过率。

### 8.4.2 重点测试面

| 领域 | 代表性证据 | 主要断言 |
|---|---|---|
| MaxKB 问答 | `AppBackend/src/test/java/com/example/appbackend/service/impl/KnowledgeChatServiceImplTest.java` | hit-test 结果处理、输入构造、模型失败、answer 与 citations 组合 |
| AI 题目生成 | `QuestionGenerationControllerTest.java`、`QuestionGenerationMaterialParserTest.java`、`QuestionGenerationServiceImplTest.java` | ADMIN 边界、材料解析、题型 JSON 和非法候选拒绝 |
| 试卷生成 | `ExamPaperServiceImplTest.java`、`ExamPaperDocumentGeneratorTest.java` | 手工随机组卷、分值规则与文档输出 |
| 真实预览 | `ExamPaperPreviewControllerTest.java`、`ExamPaperPreviewServiceImplTest.java` | 预览会话、签名、过期、一次性证明和失败状态 |
| 在线考试 | `AppExamPersistenceContractTest.java`、`AppExamServiceImplTest.java` | 尝试与答案持久化、版本冲突、自动保存、交卷和客观评分 |
| 其他领域 | `AppBackend/src/test/java/` 下相应控制器与服务测试 | 身份、会议、画像、资源和校园服务的局部契约 |

Java 测试应在可隔离的 MySQL 测试库或等价受控环境运行，数据库名称、账号和凭据由环境配置提供。测试报告不复制真实连接值。涉及外部 MaxKB、讯飞或模型服务的用例应明确使用替身、受控沙箱还是真实服务，避免把单元测试的替身响应当作生产连通性。

## 8.5 Python 与前端重点验证

Python 侧已经包含 RAG 路由、资源构建、生成资源导出下载和专业智能体的测试文件。正式报告只接受在当前提交和明确环境中重新执行得到的结果；不从 pytest 缓存、IDE 历史面板或旧日志计算通过率，也不从缓存计算 Python 测试通过率。以下命令用于按能力缩小验证面，执行者仍需记录实际收集数和结果。

```bash
python -m pytest ai-servers/tests/test_rag_api_routes.py
python -m pytest ai-servers/tests/test_assistant_resource_builder.py
python -m pytest ai-servers/tests/test_generated_exporter.py ai-servers/tests/test_generated_export_download.py
python -m pytest ai-servers/tests/test_programming_agent.py
```

移动端重点状态测试包括 `AppFrontend/subpackage_ai/assistantMessage.test.js`、`AppFrontend/subpackage_exam/examState.test.js` 和 `AppFrontend/subpackage_exam/examPages.test.js`。它们验证消息与考试页面的纯状态，但不证明麦克风权限、文件下载、弱网恢复或不同手机系统行为。

Web 端重点状态测试包括 `AppWeb/src/pages/questionBank/questionGenerationState.test.js` 和 `AppWeb/src/pages/ai/ExamPaper/examPaperPreviewState.test.js`。它们可验证生成候选、预览失效和并发状态逻辑，但权限结论仍应由真实角色调用后端接口确认，特别是 MERCHANT 当前可进入部分试卷链路的缺口。

## 8.6 异常与恢复验证

| 异常场景 | 注入方式 | 预期结果 | 数据检查 |
|---|---|---|---|
| 模型调用失败 | 让模型适配返回超时或结构错误 | AI 任务进入失败终态，SSE 发出错误而非完成 | 半截回答不标记为最终，业务事实不被模型直接改写 |
| MaxKB 超时或无命中 | hit-test 超时或返回空 references | 返回检索失败、资料不足或受限回答 | 不生成伪造 citations，不改知识库状态 |
| Redis 不可用 | 关闭 Redis 后启动记忆服务 | Python 记忆退化为进程内存并显式记录运行边界 | 不宣称跨进程、重启后记忆仍持久化 |
| SSE 中断 | complete 前断开客户端连接 | 页面将回答标为不完整并提供安全重试 | 不把本地 AbortController 记为服务端取消成功 |
| 会议 ASR 断开 | 中断外部 WebSocket 或发送错误帧 | 保留已确认 final，停止虚假的识别中状态 | 不用模型内容补齐缺失转写 |
| 答案版本冲突 | 以旧版本提交自动保存 | 返回冲突和当前权威版本 | 服务端新答案不被旧请求覆盖 |
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

安全验证当前以边界检查和整改清单为主，不能称为渗透测试或合规认证。重点检查非 ADMIN 调用治理接口、MERCHANT 进入试卷链路、资源与考试数据归属、原始 Authorization 日志、业务密码比较、配置凭据、WebSocket Origin 和下载路径泄露。当前业务密码仍存在明文比较风险，配置中仍存在需要环境变量化与轮换的凭据值，会议 WebSocket 仍需 Origin 白名单、限流和审计加固。

文档静态检查覆盖以下项目：源文件精确数量、章节标题、支持的指令、FR、NFR、FUNC、TECH、API 与测试追踪 ID、禁止结论、占位符、密钥特征和本地绝对路径。静态扫描成功只能证明底稿文本满足规则，最终 DOCX 和 PDF 仍需逐页检查目录、标题、表格、图注、字体、裁切和分页。

## 8.9 验证缺口与退出条件

| 缺口 | 当前状态 | 完成条件 | 结论限制 |
|---|---|---|---|
| Java 环境错误 | known-limit | 提供隔离测试数据库并重新取得 0 error 报告 | 当前不能称 Java 全量通过 |
| Python 全量结果 | partial | 在当前提交上执行明确命令并保存新鲜结果 | 不报告缓存推导通过率 |
| 性能测试 | 未执行 | 定义负载模型、数据量、环境并采集稳定结果 | 不编造 QPS、P95 或可用性 SLA |
| 真机测试 | 未执行 | 覆盖代表性 Android 或目标设备、权限、弱网和下载 | 前端状态测试不等同于真机 |
| 渗透测试 | 未执行 | 在授权环境完成认证、授权、输入和依赖边界测试 | 不宣称已通过安全认证 |
| 生产端到端测试 | 未执行 | 在生产等价拓扑运行 Java、Python、外部服务和多端流程 | 不宣称一条命令部署后全部可用 |
| 讯飞稳定性 | partial | 真实凭据环境下覆盖连接、断线、限流和长会话 | 单元测试不证明外部稳定性 |
| 开源许可证复核 | 未完成 | 对直接与传递依赖完成许可证、NOTICE 和分发义务复核 | 不宣称开源合规审计完成 |

发布退出条件至少包括：关键目标测试新鲜执行；Java 环境 error 处理或被明确接受；角色与数据归属回归；文档与下载链路无敏感信息；最终 DOCX 和 PDF 逐页视觉检查；所有未测项保留责任人和后续验证入口。任何未满足条件都应以缺口形式保留，不用模糊的“基本通过”替代证据。
