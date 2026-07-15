<!-- SECTION_BREAK chapter="第二章 需求分析" -->
# 第二章 需求分析

## 2.1 需求分析原则

本章把赛题目标转化为可追踪、可验收且带当前状态的系统需求。需求中的“系统应”描述目标行为；“验收证据”说明如何判断目标是否满足；“当前状态”才是当前仓库能力结论。实现状态依据 `docs/project-document/evidence-index.md`，不得由需求语气反推完成度。

需求按四类事实口径管理：已实现（implemented）表示当前生产代码或可复现测试已提供能力；部分实现（partial）表示已有代码但集成或覆盖不完整；后续规划（planned）表示尚未形成交付能力；当前限制（known-limit）表示已有证据确认存在边界。验收时应同时核对功能结果、异常路径、数据后置状态和相对路径证据。

## 2.2 用户角色

当前角色基线由 Java 角色实体和初始化数据支撑。当前 App 登录入口允许 STUDENT 与 TEACHER；当前 Web 登录入口允许 ADMIN 与 MERCHANT。产品入口权限不等同于具体接口权限：MaxKB 和 AI 题目生成控制器执行 ADMIN 校验，但 Web 路由与导航没有统一的角色过滤，试卷创建、列表、详情与预览也只校验登录。教师使用 Web 教学治理、学生或教师直接使用 MaxKB 问答属于设计目标；商户可进入试卷链路属于当前授权缺口，不能写成已隔离能力。

| 角色代码 | 中文角色 | 当前产品入口 | 当前已授权范围 | 设计目标 |
|---|---|---|---|---|
| ADMIN | 管理员 | Web | 使用后台治理入口；可调用 ADMIN-only 的 MaxKB、AI 题目生成、试卷发布与取消发布 | 继续承担系统治理，并为后续细分教学权限提供配置基础 |
| TEACHER | 教师 | App | 使用已授权的学习、会议与资源相关交互；当前不能通过 Web 登录 | 后续在独立权限与审计完成后开放知识库、题库和试卷治理 |
| STUDENT | 学生 | App | 使用本人获授权的学习助手、会议、资源和在线考试交互 | 后续可通过受控学习入口使用面向学生的知识问答，不开放后台治理 |
| MERCHANT | 商户 | Web | MaxKB 与题目生成会被 ADMIN 校验拒绝，但当前可见未按角色过滤的路由并可进入只校验登录的试卷链路，构成当前授权缺口 | 未来加固需过滤导航与路由，并为试卷创建、列表、详情、预览和下载增加服务端角色校验 |

## 2.3 业务痛点

| ID | 痛点 | 影响 | 需求响应 |
|---|---|---|---|
| BP-001 | 校园服务与学习入口分散 | 用户在多个页面和流程间切换，学习上下文难以连续 | 通过 App 学习入口和 Web 业务后台形成与当前角色白名单一致的访问边界 |
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

当前 Web 路由与导航未按角色过滤；试卷创建、列表、详情与预览只校验登录，只有发布和取消发布执行 ADMIN 校验。

MaxKB 仅执行 hit-test 检索；Java 提取引用并组装 grounded context；系统 LLM/agent 生成最终回答，Java 再组合 citations 响应。

服务端取消接口属于设计要求；当前客户端 AbortController 仅中止本地 fetch，不能证明存在 REST 取消端点。

| ID | 参与者（actor） | 触发条件（trigger） | 系统行为（system behavior） | 验收证据（acceptance evidence） | 当前状态（current status） |
|---|---|---|---|---|---|
| FR-001 | 管理员、教师、学生、商户 | 用户进入 App、Web 或受保护业务 | 系统应识别四类持久化角色，并在登录时执行 App 仅 STUDENT 或 TEACHER、Web 仅 ADMIN 或 MERCHANT 的当前入口白名单 | 核对 `AppBackend/01_roles.sql`、`Role.java` 与 `AppBackend/src/main/java/com/example/appbackend/service/impl/UserServiceImpl.java` 的 `applogin`、`weblogin` 分支 | 已实现（implemented） |
| FR-002 | 学生、教师 | STUDENT 或 TEACHER 从 App 登录并进入校园或学习服务 | 系统应通过 uni-app 页面与分包提供校园服务、AI、会议和考试等已授权入口，并拒绝 ADMIN 或 MERCHANT 通过 App 登录 | 核对 `UserServiceImpl.java` 的 App 角色校验、`AppFrontend/pages.json` 与 `AppFrontend/package.json` | 已实现（implemented） |
| FR-003 | 学生、教师 | 用户向 AI 助手提交具有明确意图的任务 | Leader 应识别任务意图、选择目标专业智能体，并由运行器按契约执行 | 核对 `ai-servers/app/multi_agents/leader_agent/agent.py`、`ai-servers/app/multi_agents/runner.py` 和路由测试记录 | 已实现（implemented） |
| FR-004 | 学生、教师 | 用户发起需要增量反馈的 AI 请求 | 系统应以 SSE 传递增量、完成和错误事件；服务端任务取消端点是设计要求，客户端本地 abort 不得作为服务端取消证据 | 留存 Java 与移动端流式接口测试、`AppFrontend/api/ai.js` 的 AbortController、断线场景与未来取消端点测试 | 部分实现（partial），当前只有客户端本地中止，尚无服务端 REST 取消端点证据 |
| FR-005 | 学生、教师 | 用户查看个性化学习状态或业务规则读取画像 | 系统应读取七维画像的分数、置信度、趋势和证据计数，并以可解释结构展示 | 核对 `AppBackend/src/main/java/com/example/appbackend/service/impl/UserProfileServiceImpl.java` 与 `AppBackend/src/main/java/com/example/appbackend/entity/UserProfileDimension.java` | 已实现（implemented） |
| FR-006 | Java 画像规则、授权业务流程 | 对话、会议或资源互动形成可用画像信号 | 系统应保存证据来源、方向、置信度和状态，先进入候选池，汇总应用后再改变画像 | 核对 `AppBackend/src/main/java/com/example/appbackend/entity/UserProfileEvidence.java`、`docs/user-profile-evidence-protocol.md` 和汇总服务测试 | 已实现（implemented） |
| FR-007 | 学生、教师 | 用户请求画像总结或补证建议 | 画像总结智能体应解释当前快照、强项、欠缺和补证方向，不直接修改画像分数 | 核对 `ai-servers/app/multi_agents/profile_summary_agent/agent.py` 与 Java 画像更新职责分离 | 已实现（implemented） |
| FR-008 | 学生、教师 | 画像或学习结果发生受控变化 | 系统应生成可维护、可调整并能记录完成状态的独立学习路径 | 以路径实体、路径调整规则、接口测试和跨轮次状态记录作为验收材料 | 后续规划（planned），当前只提供个性化回答与资源建议 |
| FR-009 | 教师、学生 | 教师创建会议并管理参与过程 | 系统应保存会议会话、参与者、转写记录和智能体分析结果，并保持记录之间的业务关联 | 核对 `MeetingSession.java`、`MeetingParticipant.java`、`MeetingRecord.java` 与 `MeetingAgentResult.java` | 已实现（implemented） |
| FR-010 | 教师、学生 | 会议开始并上传可识别音频 | 系统应通过 WebSocket 接入讯飞 ASR，转发实时文本事件并保存可用转写 | 核对 `MeetingAsrWebSocketConfig.java`、`MeetingAsrWebSocketHandler.java`，并留存真实外部服务联调记录 | 部分实现（partial），外部服务稳定性仍需部署验证 |
| FR-011 | 教师 | 会议结束且存在可处理转写 | 系统应按需执行转写整理、总结、成员分析和资源推荐，并保存会后结果 | 核对 `AppBackend/src/main/java/com/example/appbackend/service/impl/MeetingServiceImpl.java` 与 `ai-servers/app/multi_agents/catalog.py`，补充完整场景测试 | 部分实现（partial），端到端覆盖有限 |
| FR-012 | 管理员 | ADMIN 在 Web 后台维护 MaxKB 账号或知识库 | Java 服务应在每个 MaxKB 管理入口执行 ADMIN 校验，并对外部异常作出可诊断反馈 | 核对 `AppBackend/src/main/java/com/example/appbackend/controller/MaxKbKnowledgeController.java` 的 `requireAdmin` 与 `MaxKbKnowledgeServiceImpl.java` | 已实现（implemented） |
| FR-013 | 管理员（当前）；学生、教师（目标） | 当前由 ADMIN 在 Web 端选择知识库并发起问答；目标角色在后续获得明确授权 | 当前控制器只接受 ADMIN；MaxKB hit-test 返回检索片段，Java 构建 grounded context 后调用系统 LLM/agent 生成最终回答 | 核对 `MaxKbKnowledgeController.java` 的 `/chat` 与 `requireAdmin`、`KnowledgeChatServiceImpl.java` 的 `hitTest`、`buildAgentInput` 和 `llmService.chat` | 部分实现（partial）：ADMIN 问答链路已实现，STUDENT 与 TEACHER 当前未获控制器授权 |
| FR-014 | 管理员（当前）；学生、教师（目标） | 当前 ADMIN 的问题进入知识库问答链路 | Java 应从检索片段提取 references，组合系统 LLM 的 answer 与 citations 响应，并在资料不足时拒绝编造；MaxKB 不被描述为最终回答生成方 | 核对 `KnowledgeChatServiceImpl.java` 的 `setAnswer`、`setReferences`、检索原文和引用处理测试 | 部分实现（partial）：grounded 回答机制已实现，目标角色覆盖尚未实现 |
| FR-015 | 学生、教师 | 智能体生成可交付学习资源 | 系统应构建包含资源类型、来源、grounding 和 integrity 信息的结构化资源信封 | 核对 `ai-servers/app/services/assistant_resource_builder.py` 与 `ai-servers/tests/test_assistant_resource_builder.py` | 已实现（implemented） |
| FR-016 | 学生 | 用户点击、收藏或下载 AI 资源 | 系统应记录资源标识、互动类型和必要上下文，形成可持久化的互动记录 | 核对 `AppAiLeaderResourceController.java` 与 `AiLeaderResourceInteraction.java`，执行互动持久化验证 | 已实现（implemented） |
| FR-017 | 学生、教师 | 用户对已生成且允许导出的资源发起下载 | 系统应执行资源导出、登记和下载校验，拒绝未登记或不符合约束的请求 | 核对 `generated_exporter.py`、`test_generated_exporter.py` 与 `test_generated_export_download.py` | 已实现（implemented），资源媒介取决于可用生成链路 |
| FR-018 | 管理员（当前）；教师（目标） | 当前由 ADMIN 选择题型、数量和生成材料；目标教师在获得 Web 与接口授权后发起出题 | 当前 Java 接口应先执行 ADMIN 校验，再调用 AI 生成五类题型并进入结构化校验；教师出题属于待授权设计 | 核对 `AppBackend/src/main/java/com/example/appbackend/controller/QuestionGenerationController.java` 的 `requireAdmin` 与 `QuestionGenerationControllerTest.java` | 部分实现（partial）：ADMIN 生成链路已实现，TEACHER 当前不能登录 Web 且未获该控制器授权 |
| FR-019 | 管理员（当前）；教师（目标） | AI 向已授权的题目生成调用方返回候选结果 | 当前 ADMIN 可按题型 JSON 规范解析、校验并导入题干、选项或空位、答案与解析；教师导入需先补齐授权 | 核对 `QuestionGenerationController.java` 的 `/import` 与 ADMIN 校验、`docs/exam-question-json-spec.md` 和服务测试 | 部分实现（partial）：结构校验与 ADMIN 导入已实现，TEACHER 覆盖尚未实现 |
| FR-020 | 管理员、商户（当前可进入）；教师（目标） | 任一已登录 Web 用户进入试卷创建、列表或详情路由 | 当前后端只取 userId 即允许创建、列表、详情和下载，发布或取消发布才校验 ADMIN；设计要求为教学操作补齐角色白名单 | 核对 `AppWeb/src/App.jsx` 的无角色路由、`ExamPaperController.java` 的 `getUserId` 与 `getAdminUserId` | 当前限制（known-limit）：MERCHANT 可进入并调用部分试卷接口，未来需前后端同时加固 |
| FR-021 | 管理员、商户（当前可进入）；教师（目标） | 任一已登录 Web 用户进入真实预览路由 | 当前预览创建、读取和删除只校验 userId；设计要求为预览链路增加角色校验，并继续保留一次性确认边界 | 核对 `ExamPaperPreviewController.java` 的 `userId`、预览服务测试与 Web 路由 | 当前限制（known-limit）：预览内容一致性已实现，但 MERCHANT 访问边界未隔离 |
| FR-022 | 学生 | 学生开始在线考试、持续作答或提交 | 系统应保存考试尝试与答案快照，支持自动保存、交卷和客观题评分，并处理版本与到期状态 | 核对 `ExamPaperAttempt.java`、`ExamPaperAttemptAnswer.java`、`AppExamServiceImplTest.java` 与 `AppFrontend/subpackage_exam/examState.test.js` | 已实现（implemented） |
| FR-023 | 教师、学生 | 试卷包含主观题或需要将考试结果用于画像 | 系统应将客观题自动评分与主观题人工处理分开，并在引入画像前经过证据协议和可审计映射 | 验收需包含主观题人工状态、画像证据候选记录、冲突处理和回归测试 | 当前限制（known-limit），主观题可靠自动评分与考试画像回写尚未接通 |
| FR-024 | 管理员、商户（当前可登录）；教师（目标） | ADMIN 或 MERCHANT 登录 Web；目标教师获得细分治理权限 | React/Vite 当前没有统一角色路由保护；MaxKB 和题目生成由控制器拒绝非 ADMIN，但试卷创建与预览缺少同等校验，未来需建立导航、路由和接口一致授权 | 核对 `UserServiceImpl.java` 的 Web 白名单、`AppWeb/src/App.jsx`、MaxKB、题目生成与试卷控制器 | 当前限制（known-limit）：权限覆盖不一致，需先加固 MERCHANT 边界再扩展 TEACHER 治理 |

## 2.6 非功能需求

| ID | 参与者（actor） | 触发条件（trigger） | 系统行为（system behavior） | 验收证据（acceptance evidence） | 当前状态（current status） |
|---|---|---|---|---|---|
| NFR-001 | 学生、教师 | AI 任务需要较长处理时间 | 客户端应持续呈现进度、增量内容、完成和失败状态，不把无响应等待作为正常交互 | SSE 事件契约测试、移动端状态测试、断线与重试记录 | 部分实现（partial），尚缺真实弱网与生产链路验证 |
| NFR-002 | 全体用户 | 外部模型、MaxKB、讯飞或缓存发生超时与异常 | 系统应返回可理解错误并保留可诊断日志，已提交业务不得因单个外部依赖失败而静默丢失 | 外部依赖异常测试、Java 服务异常分支和会议联调记录 | 部分实现（partial），重点链路有处理但未形成统一故障演练 |
| NFR-003 | 学生、教师 | 自动保存、交卷、预览确认或资源下载发生重复请求 | 系统应以状态、版本或一次性证明控制重复执行，避免生成不一致结果 | `AppExamServiceImplTest.java`、`ExamPaperPreviewServiceImplTest.java` 与生成资源下载测试 | 已实现（implemented），限于已有状态化链路 |
| NFR-004 | 管理员、全体用户 | 用户认证、授权或保存密码 | 系统应在登录、导航、路由和接口四层执行最小权限，并采用适合生产环境的密码哈希 | 角色权限测试、MERCHANT 试卷越权回归、密码存储审查和敏感信息扫描 | 当前限制（known-limit），试卷授权覆盖与密码存储均需加固 |
| NFR-005 | 运维人员、管理员 | 部署环境需要配置数据库、模型或外部服务 | 系统应通过环境变量或受控密钥服务注入凭据，支持轮换并避免凭据进入仓库和成品 | 配置扫描、环境变量清单、轮换演练和提交历史审查 | 后续规划（planned），当前需继续完成环境变量化与轮换治理 |
| NFR-006 | 运维人员、会议用户 | 客户端建立会议 ASR WebSocket | 服务应限制允许来源、连接频率和消息规模，并记录安全审计事件 | Origin 白名单、限流、鉴权、异常帧与审计日志测试 | 后续规划（planned），当前 WebSocket 安全边界待强化 |
| NFR-007 | AI 开发成员 | 新增专业智能体或调整模型模态 | 系统应通过 Catalog 维护角色、输入输出、别名与模态契约，使新增能力不破坏现有路由接口 | `ai-servers/app/multi_agents/catalog.py` 的目录校验与路由回归测试 | 已实现（implemented），Catalog 当前登记 30 个实现包 |
| NFR-008 | 学生、教师、管理员、商户 | 用户通过其当前获准的 App 或 Web 产品入口使用系统 | 核心状态和操作应在目标端正确呈现，且角色不得越过 App 与 Web 登录白名单 | uni-app 页面状态测试、React/Vite 构建、`UserServiceImpl.java` 登录分支、浏览器检查和真机清单 | 部分实现（partial），关键状态有测试但真机与完整兼容验证未完成 |
| NFR-009 | 开发与运维成员 | 跨 Java、Python 和外部服务排查请求 | 系统应提供相关标识、结构化日志、健康检查和关键链路指标，且日志不得记录凭据与隐私正文 | 日志字段审查、跨服务关联演练、健康检查和脱敏测试 | 后续规划（planned），生产监控与审计体系仍需补齐 |
| NFR-010 | 验收人员、运维人员 | 进行容量、延迟、稳定性或安全验收 | 系统应在固定环境、固定数据集和明确脚本下报告实测结果、样本范围与失败条件，不使用推测值 | 性能脚本、原始报告、环境说明、渗透测试和生产端到端记录 | 当前限制（known-limit），尚无可对外承诺的 QPS、P95、准确率或可用性结论 |

## 2.7 验收与追踪规则

1. 每项需求的验收记录应包含执行环境、输入条件、实际结果、证据位置、执行日期和结论；外部服务相关用例还应记录依赖版本与可用状态。
2. “已实现”条目至少核对生产代码和一个可复现验证入口；只有源码但缺少环境验证的外部链路不得提升状态。
3. “部分实现”条目验收时必须同时报告已覆盖路径和未覆盖路径，不用局部成功替代完整链路结论。
4. “后续规划”和“当前限制”条目可定义验收方法，但在相应代码、测试和部署证据形成前不得计入当前成果。
5. 需求状态变更应先更新 `docs/project-document/evidence-index.md`，再同步正文、追踪矩阵和测试结论，保证表述与证据一致。
