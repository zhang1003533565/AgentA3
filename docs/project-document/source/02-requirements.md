<!-- SECTION_BREAK chapter="第二章 需求分析" -->
# 第二章 需求分析

## 2.1 需求分析原则

本章把赛题目标转化为可追踪、可验收且带当前状态的系统需求。需求中的“系统应”描述目标行为；“验收证据”说明如何判断目标是否满足；“当前状态”才是当前仓库能力结论。实现状态依据 `docs/project-document/evidence-index.md`，不得由需求语气反推完成度。

需求按四类事实口径管理：已实现（implemented）表示当前生产代码或可复现测试已提供能力；部分实现（partial）表示已有代码但集成或覆盖不完整；后续规划（planned）表示尚未形成交付能力；当前限制（known-limit）表示已有证据确认存在边界。验收时应同时核对功能结果、异常路径、数据后置状态和相对路径证据。

## 2.2 用户角色

当前角色基线由 Java 角色实体和初始化数据支撑。角色矩阵描述主要使用边界，不等于每个接口的完整授权清单；接口级权限仍应以实际控制器和安全配置为准。

| 角色代码 | 中文角色 | 核心诉求 | 主要权限边界 | 当前依据 |
|---|---|---|---|---|
| ADMIN | 管理员 | 管理用户、角色、模型、智能体、知识库与系统配置 | 负责治理和配置，不替代教师确认教学内容 | `AppBackend/01_roles.sql`；`AppBackend/src/main/java/com/example/appbackend/entity/Role.java` |
| TEACHER | 教师 | 管理知识、题目、试卷、会议和教学资源 | 可生产与审核教学内容，不直接改写画像规则 | `AppBackend/01_roles.sql`；Web 管理端与 Java 教学接口 |
| STUDENT | 学生 | 使用学习助手、会议、资源和在线考试 | 访问本人获授权的数据与学习交互 | `AppFrontend/pages.json`；移动端学习分包 |
| MERCHANT | 商户 | 使用与经营相关的校园服务能力 | 不默认获得教学管理、画像治理或 AI 配置权限 | `AppBackend/01_roles.sql`；角色持久化基线 |

## 2.3 业务痛点

| ID | 痛点 | 影响 | 需求响应 |
|---|---|---|---|
| BP-001 | 校园服务与学习入口分散 | 用户在多个页面和流程间切换，学习上下文难以连续 | 通过移动端统一入口和 Web 教学管理形成角色化访问 |
| BP-002 | 通用 AI 缺少持续学习背景 | 相同问题容易得到同质化回答，难以解释个性化依据 | 使用七维画像、证据候选池和 Leader 路由提供受控上下文 |
| BP-003 | 对话、研讨与资源互动信号分散 | 教师难以观察学习过程，画像更新容易依赖主观判断 | 将来源、置信度、状态和应用结果保存为可审计证据 |
| BP-004 | 生成内容来源与完整性不清 | 使用者难以判断内容依据、可下载性和适用范围 | 资源信封记录来源、grounding、integrity 与导出登记 |
| BP-005 | 教学资源和测评生产链较长 | 教师在知识整理、出题、组卷、预览和发布间重复操作 | 连接知识库、题目生成、结构校验、组卷和文档输出 |
| BP-006 | 会议与考试反馈尚未充分闭环 | 实时过程、会后分析和测评结果难以稳定进入下一轮支持 | 保留会议与考试数据，已接通能力按现状使用，画像回写列入规划 |

## 2.4 能力比较

下表比较的是典型产品机制与架构能力，不是统一模型、统一数据集和统一环境下的效果实验。不同类别产品可通过定制获得额外能力，表中“不固定”或“通常依赖扩展”不构成对具体厂商的性能结论。AgentA3 列同时标注当前边界，避免把设计目标写成实测优势。

| 能力维度 | 通用聊天机器人 | 传统教学平台 | 基础知识库问答 | AgentA3 |
|---|---|---|---|---|
| 学习者上下文 | 主要依赖当前会话或产品自带记忆 | 以课程、成绩和操作记录为主 | 主要依赖检索问题与知识库 | 已实现七维画像与证据慢更新，独立学习路径仍在规划 |
| 专业任务编排 | 通常由单一助手直接响应 | 以固定业务流程为主 | 围绕检索与回答 | 已实现 Leader 按需路由 Catalog 中的专业智能体 |
| 来源与可信边界 | 取决于模型和产品配置 | 平台内资源来源相对明确 | 可展示知识片段或引用 | 知识问答处理引用；生成资源保留来源、grounding 和 integrity |
| 教学内容生产 | 可生成文本，教学结构需额外约束 | 依赖人工录入或专用插件 | 侧重已有知识问答 | 已接入五类题目生成、结构校验、组卷、预览和文档输出 |
| 研讨过程处理 | 需外接会议或语音能力 | 常以签到、讨论区为主 | 通常不处理实时语音 | 已有会议数据模型；实时转写与会后智能体链为部分实现 |
| 反馈闭环 | 通常不维护项目特定画像规则 | 依赖平台既有统计 | 主要优化检索问答 | 资源互动可记录；考试自动回写画像和独立路径闭环尚未接通 |
| 多端与治理 | 取决于具体产品 | 通常具备学生端和管理端 | 常提供独立问答入口 | 仓库包含移动端、Web、Java 与独立 Python 服务四个工程边界 |

## 2.5 功能需求

| ID | 参与者（actor） | 触发条件（trigger） | 系统行为（system behavior） | 验收证据（acceptance evidence） | 当前状态（current status） |
|---|---|---|---|---|---|
| FR-001 | 管理员、教师、学生、商户 | 用户进入受保护业务或管理员维护角色 | 系统应以持久化角色基线识别四类用户，并把角色作为业务访问控制依据 | 核对 `AppBackend/01_roles.sql` 与 `AppBackend/src/main/java/com/example/appbackend/entity/Role.java` 中的角色代码和 `sys_role` 映射 | 已实现（implemented） |
| FR-002 | 学生、商户 | 用户从移动端进入校园或学习服务 | 系统应通过 uni-app 页面与分包提供校园服务、AI、会议和考试等角色化入口 | 核对 `AppFrontend/pages.json`、`AppFrontend/package.json` 及对应页面路由可达性 | 已实现（implemented） |
| FR-003 | 学生、教师 | 用户向 AI 助手提交具有明确意图的任务 | Leader 应识别任务意图、选择目标专业智能体，并由运行器按契约执行 | 核对 `ai-servers/app/multi_agents/leader_agent/agent.py`、`ai-servers/app/multi_agents/runner.py` 和路由测试记录 | 已实现（implemented） |
| FR-004 | 学生、教师 | 用户发起需要增量反馈的 AI 请求 | 系统应以 SSE 向客户端传递可识别的增量事件、完成事件和错误事件，并允许客户端处理连接中断 | 留存 Java 与移动端流式接口测试、`AppFrontend/subpackage_ai/assistantMessage.test.js` 及断线场景记录 | 部分实现（partial），尚缺生产网络端到端验证 |
| FR-005 | 学生、教师 | 用户查看个性化学习状态或业务规则读取画像 | 系统应读取七维画像的分数、置信度、趋势和证据计数，并以可解释结构展示 | 核对 `AppBackend/src/main/java/com/example/appbackend/service/impl/UserProfileServiceImpl.java` 与 `AppBackend/src/main/java/com/example/appbackend/entity/UserProfileDimension.java` | 已实现（implemented） |
| FR-006 | Java 画像规则、授权业务流程 | 对话、会议或资源互动形成可用画像信号 | 系统应保存证据来源、方向、置信度和状态，先进入候选池，汇总应用后再改变画像 | 核对 `AppBackend/src/main/java/com/example/appbackend/entity/UserProfileEvidence.java`、`docs/user-profile-evidence-protocol.md` 和汇总服务测试 | 已实现（implemented） |
| FR-007 | 学生、教师 | 用户请求画像总结或补证建议 | 画像总结智能体应解释当前快照、强项、欠缺和补证方向，不直接修改画像分数 | 核对 `ai-servers/app/multi_agents/profile_summary_agent/agent.py` 与 Java 画像更新职责分离 | 已实现（implemented） |
| FR-008 | 学生、教师 | 画像或学习结果发生受控变化 | 系统应生成可维护、可调整并能记录完成状态的独立学习路径 | 以路径实体、路径调整规则、接口测试和跨轮次状态记录作为验收材料 | 后续规划（planned），当前只提供个性化回答与资源建议 |
| FR-009 | 教师、学生 | 教师创建会议并管理参与过程 | 系统应保存会议会话、参与者、转写记录和智能体分析结果，并保持记录之间的业务关联 | 核对 `MeetingSession.java`、`MeetingParticipant.java`、`MeetingRecord.java` 与 `MeetingAgentResult.java` | 已实现（implemented） |
| FR-010 | 教师、学生 | 会议开始并上传可识别音频 | 系统应通过 WebSocket 接入讯飞 ASR，转发实时文本事件并保存可用转写 | 核对 `MeetingAsrWebSocketConfig.java`、`MeetingAsrWebSocketHandler.java`，并留存真实外部服务联调记录 | 部分实现（partial），外部服务稳定性仍需部署验证 |
| FR-011 | 教师 | 会议结束且存在可处理转写 | 系统应按需执行转写整理、总结、成员分析和资源推荐，并保存会后结果 | 核对 `AppBackend/src/main/java/com/example/appbackend/service/impl/MeetingServiceImpl.java` 与 `ai-servers/app/multi_agents/catalog.py`，补充完整场景测试 | 部分实现（partial），端到端覆盖有限 |
| FR-012 | 管理员、教师 | 用户维护 MaxKB 账号或知识库 | Java 服务应提供受权限控制的账号与知识库管理能力，并对外部异常作出可诊断反馈 | 核对 `MaxKbKnowledgeController.java`、`MaxKbKnowledgeServiceImpl.java` 及异常场景记录 | 已实现（implemented） |
| FR-013 | 学生、教师 | 用户选择知识库并发起资料问题 | Java 服务应调用 MaxKB 完成知识问答，处理回答与引用结果，并向调用方返回明确异常 | 核对 `KnowledgeChatServiceImpl.java` 与 `KnowledgeChatServiceImplTest.java` 的正常和异常用例 | 已实现（implemented） |
| FR-014 | 学生、教师 | 问题进入知识库问答链路 | 系统应展示可用引用或资料不足提示，并把 RAG 的适用范围限定在明确知识库链路 | 核对知识问答结果结构、引用处理测试和 `docs/project-document/evidence-index.md` 中的范围说明 | 已实现（implemented），不外推至其他智能体回答 |
| FR-015 | 学生、教师 | 智能体生成可交付学习资源 | 系统应构建包含资源类型、来源、grounding 和 integrity 信息的结构化资源信封 | 核对 `ai-servers/app/services/assistant_resource_builder.py` 与 `ai-servers/tests/test_assistant_resource_builder.py` | 已实现（implemented） |
| FR-016 | 学生 | 用户点击、收藏或下载 AI 资源 | 系统应记录资源标识、互动类型和必要上下文，形成可持久化的互动记录 | 核对 `AppAiLeaderResourceController.java` 与 `AiLeaderResourceInteraction.java`，执行互动持久化验证 | 已实现（implemented） |
| FR-017 | 学生、教师 | 用户对已生成且允许导出的资源发起下载 | 系统应执行资源导出、登记和下载校验，拒绝未登记或不符合约束的请求 | 核对 `generated_exporter.py`、`test_generated_exporter.py` 与 `test_generated_export_download.py` | 已实现（implemented），资源媒介取决于可用生成链路 |
| FR-018 | 教师、管理员 | 用户选择受支持题型、数量和生成材料后发起出题 | Java 接口应调用 AI 生成当前接入的五类题型，并将结果交给结构化校验流程 | 核对 `QuestionGenerationController.java` 与 `QuestionGenerationControllerTest.java` 的题型和请求边界 | 已实现（implemented） |
| FR-019 | 教师、管理员 | AI 返回题目候选结果 | 系统应按题型 JSON 规范解析、校验并持久化题干、选项或空位、答案与解析，异常数据不得静默入库 | 核对 `docs/exam-question-json-spec.md`、`QuestionGenerationMaterialParserTest.java` 与 `QuestionGenerationServiceImplTest.java` | 已实现（implemented） |
| FR-020 | 教师、管理员 | 用户从题库选择手工或随机规则组卷 | 系统应按请求组装试卷，校验题目与版式输入，并生成可下载文档 | 核对 `ExamPaperController.java`、`ExamPaperServiceImplTest.java` 与 `ExamPaperDocumentGeneratorTest.java` | 已实现（implemented） |
| FR-021 | 教师、管理员 | 用户使用模板模式创建最终试卷 | 系统应先生成真实预览，再校验一次性预览确认结果，防止未经确认的内容直接创建最终试卷 | 核对 `ExamPaperPreviewControllerTest.java` 与 `ExamPaperPreviewServiceImplTest.java` 的预览证明和失效场景 | 已实现（implemented） |
| FR-022 | 学生 | 学生开始在线考试、持续作答或提交 | 系统应保存考试尝试与答案快照，支持自动保存、交卷和客观题评分，并处理版本与到期状态 | 核对 `ExamPaperAttempt.java`、`ExamPaperAttemptAnswer.java`、`AppExamServiceImplTest.java` 与 `AppFrontend/subpackage_exam/examState.test.js` | 已实现（implemented） |
| FR-023 | 教师、学生 | 试卷包含主观题或需要将考试结果用于画像 | 系统应将客观题自动评分与主观题人工处理分开，并在引入画像前经过证据协议和可审计映射 | 验收需包含主观题人工状态、画像证据候选记录、冲突处理和回归测试 | 当前限制（known-limit），主观题可靠自动评分与考试画像回写尚未接通 |
| FR-024 | 管理员、教师 | 用户通过 Web 管理教学与 AI 相关配置 | React/Vite 管理端应提供知识库、题库、试卷、模型与智能体等治理入口，并通过 Java 接口持久化受支持配置 | 核对 `AppWeb/package.json`、`AppWeb/src/main.jsx`、题库与试卷页面状态测试，并执行角色权限验收 | 部分实现（partial），关键页面有测试但缺少完整生产端到端验证 |

## 2.6 非功能需求

| ID | 参与者（actor） | 触发条件（trigger） | 系统行为（system behavior） | 验收证据（acceptance evidence） | 当前状态（current status） |
|---|---|---|---|---|---|
| NFR-001 | 学生、教师 | AI 任务需要较长处理时间 | 客户端应持续呈现进度、增量内容、完成和失败状态，不把无响应等待作为正常交互 | SSE 事件契约测试、移动端状态测试、断线与重试记录 | 部分实现（partial），尚缺真实弱网与生产链路验证 |
| NFR-002 | 全体用户 | 外部模型、MaxKB、讯飞或缓存发生超时与异常 | 系统应返回可理解错误并保留可诊断日志，已提交业务不得因单个外部依赖失败而静默丢失 | 外部依赖异常测试、Java 服务异常分支和会议联调记录 | 部分实现（partial），重点链路有处理但未形成统一故障演练 |
| NFR-003 | 学生、教师 | 自动保存、交卷、预览确认或资源下载发生重复请求 | 系统应以状态、版本或一次性证明控制重复执行，避免生成不一致结果 | `AppExamServiceImplTest.java`、`ExamPaperPreviewServiceImplTest.java` 与生成资源下载测试 | 已实现（implemented），限于已有状态化链路 |
| NFR-004 | 管理员、全体用户 | 用户认证、授权或保存密码 | 系统应执行最小权限控制并采用适合生产环境的密码哈希，不在日志和文档暴露敏感值 | 角色权限测试、密码存储审查和敏感信息扫描 | 当前限制（known-limit），密码存储仍需升级为 BCrypt 或 Argon2 |
| NFR-005 | 运维人员、管理员 | 部署环境需要配置数据库、模型或外部服务 | 系统应通过环境变量或受控密钥服务注入凭据，支持轮换并避免凭据进入仓库和成品 | 配置扫描、环境变量清单、轮换演练和提交历史审查 | 后续规划（planned），当前需继续完成环境变量化与轮换治理 |
| NFR-006 | 运维人员、会议用户 | 客户端建立会议 ASR WebSocket | 服务应限制允许来源、连接频率和消息规模，并记录安全审计事件 | Origin 白名单、限流、鉴权、异常帧与审计日志测试 | 后续规划（planned），当前 WebSocket 安全边界待强化 |
| NFR-007 | AI 开发成员 | 新增专业智能体或调整模型模态 | 系统应通过 Catalog 维护角色、输入输出、别名与模态契约，使新增能力不破坏现有路由接口 | `ai-servers/app/multi_agents/catalog.py` 的目录校验与路由回归测试 | 已实现（implemented），Catalog 当前登记 30 个实现包 |
| NFR-008 | 学生、教师、管理员 | 用户在移动端或现代桌面浏览器使用系统 | 核心状态和操作应在目标端正确呈现，构建产物不出现阻断性错误 | uni-app 页面状态测试、React/Vite 构建、浏览器检查和真机清单 | 部分实现（partial），关键状态有测试但真机与完整兼容验证未完成 |
| NFR-009 | 开发与运维成员 | 跨 Java、Python 和外部服务排查请求 | 系统应提供相关标识、结构化日志、健康检查和关键链路指标，且日志不得记录凭据与隐私正文 | 日志字段审查、跨服务关联演练、健康检查和脱敏测试 | 后续规划（planned），生产监控与审计体系仍需补齐 |
| NFR-010 | 验收人员、运维人员 | 进行容量、延迟、稳定性或安全验收 | 系统应在固定环境、固定数据集和明确脚本下报告实测结果、样本范围与失败条件，不使用推测值 | 性能脚本、原始报告、环境说明、渗透测试和生产端到端记录 | 当前限制（known-limit），尚无可对外承诺的 QPS、P95、准确率或可用性结论 |

## 2.7 验收与追踪规则

1. 每项需求的验收记录应包含执行环境、输入条件、实际结果、证据位置、执行日期和结论；外部服务相关用例还应记录依赖版本与可用状态。
2. “已实现”条目至少核对生产代码和一个可复现验证入口；只有源码但缺少环境验证的外部链路不得提升状态。
3. “部分实现”条目验收时必须同时报告已覆盖路径和未覆盖路径，不用局部成功替代完整链路结论。
4. “后续规划”和“当前限制”条目可定义验收方法，但在相应代码、测试和部署证据形成前不得计入当前成果。
5. 需求状态变更应先更新 `docs/project-document/evidence-index.md`，再同步正文、追踪矩阵和测试结论，保证表述与证据一致。
