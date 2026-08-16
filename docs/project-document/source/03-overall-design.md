<!-- SECTION_BREAK chapter="第三章 总体设计" -->
# 第三章 总体设计

## 3.1 设计原则

总体设计以“业务治理与模型执行分离、状态事实与生成内容分离、内部服务与公网入口分离”为基本原则。移动端和 Web 端提供角色化交互；Spring Boot 维护身份、领域状态、业务规则和外部服务治理；FastAPI 执行模型与专业智能体任务；MySQL 和 Redis 承担不同生命周期的数据；MaxKB 与讯飞（Xfyun）作为受控外部依赖接入。

架构描述同时保留实现状态。四个工程边界、角色实体、七维画像、动态路径、考试反馈、专业智能体 Catalog、六资源 typed DAG 和主要业务模型按当前实现陈述；依赖真实外部环境的会议转写、MaxKB 和模型调用按部分实现陈述。五服务 Compose、健康依赖与 CI 质量门禁已有静态契约，但镜像实构建、`up -d`、真机和生产可观测性仍需独立验收。

## 3.2 系统上下文

系统面向学生、教师、管理员和商户四类用户。当前 App 登录入口允许 STUDENT 与 TEACHER；当前 Web 登录入口允许 ADMIN 与 MERCHANT。STUDENT 与 TEACHER 通过 uni-app 使用已授权交互；ADMIN 与 MERCHANT 均可进入 React/Vite Web。MaxKB 和 AI 题目生成控制器会拒绝非 ADMIN，但 Web 本身没有统一角色路由保护，不能据此推断商户只会进入商户业务。

当前 Web 路由与导航未按角色过滤；试卷创建、列表、详情与预览只校验登录，只有发布和取消发布执行 ADMIN 校验。

Spring Boot 是业务系统的主要服务边界。它接收移动端和 Web 端请求，执行身份与角色判断，维护 MySQL 中的业务事实，使用 Redis 承载缓存或短生命周期协调状态，并根据场景调用 FastAPI、MaxKB 或讯飞链路。Java 服务负责决定“谁可以发起任务、业务状态如何变化、结果如何持久化”；Python 服务负责决定“模型任务如何执行、由哪个专业智能体处理、生成结果如何按契约返回”。

FastAPI 通过内部 REST 边界接受 Java 服务编排。其 `/internal/*` 路由属于内网服务契约，不作为独立公网 API。模型提供商、MaxKB 与讯飞（Xfyun）均被视为外部依赖；系统只公开所需配置项名称，不在文档、客户端或日志中保存真实凭据值。

## 3.3 四部分总体架构

| 工程边界 | 技术基线 | 主要职责 | 不承担的职责 | 当前状态 |
|---|---|---|---|---|
| 移动端 | uni-app | 当前面向 STUDENT 与 TEACHER，承载已授权的校园服务、AI 助手、画像、会议、资源和在线考试交互 | 不接受 ADMIN 或 MERCHANT 通过 App 登录，不直接修改画像分数或保存外部密钥 | 已实现工程边界；关键页面状态测试为部分覆盖 |
| Web 管理端 | React/Vite | 当前允许 ADMIN 与 MERCHANT 登录，路由与导航未统一按角色过滤 | MaxKB 与题目生成有 ADMIN 校验，但试卷创建、列表、详情和预览仅校验登录 | 当前存在 MERCHANT 试卷授权缺口；教师 Web 治理属于后续目标 |
| Java 业务后端 | Spring Boot 与 JPA | 提供 REST 业务接口、角色治理、持久化、画像规则、会议、知识库、题库、组卷、考试和外部服务编排 | 不在 Java 业务层复制专业智能体实现，不把模型解释直接当作画像写入规则 | 已实现主要业务边界 |
| Python AI 服务 | FastAPI | 保留校园 Leader 单目标路由，执行 Python 课程六资源 typed DAG、统一复审、真实导出和模型调用 | 不作为业务主库，不取代 Java 的角色、事务、画像、掌握度和路径治理 | 已实现服务边界；支持独立启动并已纳入五服务 Compose/CI 静态契约 |

四部分之间以稳定契约协作。前端只依赖 Java 对外业务接口；Java 可以在保持业务响应契约的前提下替换或降级 AI 执行；Python 只接收完成必要授权和参数整理后的内部任务。这样的边界使教学业务状态能够在模型失败时保持可审计，也使专业智能体迭代不必直接修改前端与数据库结构。

## 3.4 Java 与 Python 职责分界

### 3.4.1 Java 业务治理

Spring Boot 负责以下职责：

- 维护用户、角色和业务权限上下文，在登录层执行 App 的 STUDENT 或 TEACHER 白名单与 Web 的 ADMIN 或 MERCHANT 白名单。
- 提供移动端与 Web 端使用的 REST 接口，并组织 SSE 或 WebSocket 相关业务状态。
- 通过 JPA 保存画像、画像证据、会议、资源互动、题库、试卷和考试等领域事实。
- 控制画像证据从 candidate 到 applied 的受控变化，不允许单次模型总结直接修改分数。
- 对 MaxKB、讯飞和 Python 服务调用执行参数校验、异常转换、超时治理和结果持久化。
- 在组卷、预览确认、在线作答与交卷等链路中维护版本、状态和一致性边界。

### 3.4.2 Python 模型与智能体执行

FastAPI 负责以下职责：

- 统一承接 Java 发起的内部 AI 任务，调用已配置模型并返回契约化结果。
- 由 Leader 识别意图，从 Catalog 中选择目标专业智能体并交给运行器按需执行。
- 对 Python 课程资源请求执行六个类型化生成节点、统一复审、交付和单项重试；该 DAG 与普通校园 Leader 路由隔离。
- 维护专业智能体的角色说明、输入输出、别名和模型模态元数据。
- Python 会反向调用 Java，并转发原始 Authorization；Java 仍重新执行数据权限判断。
- 通过 `REDIS_URL` 使用 Redis 保存对话历史与上下文；未配置时保留本地默认，Redis 不可用时退化为进程内存，但只在当前 Python 进程有效。
- 构建带类型、来源、grounding 和 integrity 信息的资源信封。
- 执行已接入的资源生成、导出或专业任务，不把未接入媒介包装为固定产物。
- 对失败返回明确错误或降级结果，由 Java 决定业务状态和对用户的最终反馈。

### 3.4.3 分界约束

模型输出属于候选结果，不天然等同于业务事实。画像分数、知识点掌握度、路径版本、考试状态、试卷确认、资源登记和角色授权均由 Java 规则或持久化状态决定。Java 通过 `AI_PYTHON_BASE_URL` 选择 Python 服务；Python 通过 `JAVA_BACKEND_BASE_URL` 和 `REDIS_URL` 选择 Java 与 Redis，未配置时保留本地开发默认。提交 Compose 为 Java 与 Python 注入同一 `AI_INTERNAL_TOKEN`，Java 在内部请求附带 `X-AI-Internal-Token`，Python 在该令牌已配置时进行常量时间比较；网络隔离、轮换和令牌必填仍需部署治理。

## 3.5 数据与外部依赖

| 依赖 | 连接方 | 数据职责 | 连接方式与边界 | 失败处理方向 |
|---|---|---|---|---|
| MySQL | Spring Boot | 保存角色、画像、会议、资源互动、题库、试卷、考试等需要持久化的业务事实 | JPA 与数据库连接配置；客户端和 Python 不直接持有业务库访问权 | Java 事务失败应阻止业务成功确认并记录可诊断错误 |
| Redis | Spring Boot、Python MemoryStore | Java 使用受控缓存与 24 小时学习工作流状态；Python 保存会话历史与上下文 | Java 由 Redis host/port 配置连接，Python 由 `REDIS_URL` 连接；失败后分别使用受限内存 fallback | 进程重启或多实例时内存 fallback 不具备共享与持久保证 |
| MaxKB | Spring Boot | 当前由 ADMIN 使用账号、知识库管理和 hit-test 检索 | Java 调用 hit-test 提取片段与引用，不由 MaxKB 生成本系统最终回答 | 检索失败或资料不足时明确状态；最终回答由系统 LLM/agent 生成 |
| 讯飞（Xfyun） | Spring Boot 会议链路 | 提供会议实时语音识别 | 服务端通过 WebSocket 传输授权握手、音频帧和转写事件 | 外部连接失败不得伪造文本；保存已确认记录并向会议端反馈状态 |
| 模型提供商 | FastAPI | 为 Leader 和专业智能体提供模型推理 | Python 按模型配置调用；凭据只在受控服务端注入 | 模型异常由 Python 契约化返回，Java 决定重试、降级或失败 |

提交 Compose 将 MySQL、Redis、Java、Python AI 与 Web 放入同一内部网络，并分别注入 `AI_PYTHON_BASE_URL=http://ai-server:8081`、`JAVA_BACKEND_BASE_URL=http://backend:8080` 与 `REDIS_URL=redis://redis:6379/0`。本地默认仍用于非容器开发；进程内存 fallback 只提供单进程降级，不等同于持久化或多实例共享。部署验收必须检查内部令牌、Authorization 转发、超时、局部重试、日志脱敏和依赖不可用场景。

## 3.6 通信协议与主要数据流

### 3.6.1 REST 业务流

移动端和 Web 端在通过各自角色白名单后使用 REST 调用 Spring Boot。App 当前接受 STUDENT 与 TEACHER；Web 当前接受 ADMIN 与 MERCHANT。Java 还在具体控制器执行更细权限校验，例如 MaxKB 和 AI 题目生成仅接受 ADMIN，因此“能够登录 Web”不等于“能够调用全部治理接口”。通过校验后，Java 再以 JPA 更新 MySQL，必要时使用 Redis 协调短生命周期状态，并按场景调用 FastAPI、MaxKB 或其他外部服务。

典型数据顺序为：前端提交带业务上下文的请求；Java 验证角色与前置状态；Java 选择本地业务处理或内部 AI 调用并附加内部令牌；Python 在执行工具时可携带原始 Authorization 反向查询 Java；Python 返回结构化候选结果；Java 校验、登记或持久化。Java、Python 与 Redis 地址均可由环境覆盖，Compose 使用内部 DNS 名称。

### 3.6.2 SSE 流式交互

对于生成时间较长的 AI 助手与课程资源任务，系统使用 SSE 承载服务器到客户端的增量事件。Python 课程事件区分生成节点、复审、导出、持久化、`partial`、单项失败和完成；Java 保存工作流快照并提供查询与单资源重试，客户端断开后可按工作流标识恢复。SSE 仍是单向下行通道；普通助手的服务端取消端点没有现有证据，客户端 AbortController 只中止本地 fetch。

当前前端关键流式状态已有测试基础，但弱网重连、代理超时和跨服务完整链路仍需在部署环境验证。因此总体设计保留事件标识、幂等和恢复要求，不给出未经实测的延迟或稳定性承诺。

### 3.6.3 WebSocket 会议流

会议实时语音采用 WebSocket 维持双向连接。客户端将会议音频送入 Java 会议处理边界，Java 服务按讯飞协议建立外部连接并处理 partial 或 final 转写事件，再向会议参与端广播可用文本并保存确认记录。会后智能体使用持久化转写作为输入，不以尚未确认的临时文本直接生成不可追踪结论。

该链路当前为部分实现。真实讯飞服务稳定性、音频设备差异、断线恢复、Origin 白名单、连接限流与安全审计仍需部署验证和加固。失败时应保留已确认记录、关闭无效连接并向客户端返回明确状态，不能用空白或虚构文本替代识别结果。

### 3.6.4 知识问答流

当前只有 ADMIN 可以通过 `MaxKbKnowledgeController` 选择知识库并提交问题。MaxKB 仅执行 hit-test 检索；Java 提取引用并组装 grounded context；系统 LLM/agent 生成最终回答，Java 再组合 citations 响应。面向 STUDENT、TEACHER 的受控知识问答是设计目标，必须新增独立授权、入口与审计后再计入现状。该 RAG 流只适用于明确的知识库问答。

### 3.6.5 画像证据流

对话、画像补问、会议或资源互动形成信号后，Java 将满足协议的数据保存为候选画像证据。汇总流程根据维度、方向、置信度、冲突和既有状态决定是否应用，并记录实际变化。Python 学习首页同时读取知识点掌握度、活动路径和推荐；路径项可开始、完成或重规划。受支持的客观题交卷结果按题目知识点幂等更新掌握度、生成画像 candidate、重排路径并返回版本变化，画像总结智能体仍只读解释快照。

### 3.6.6 题库、试卷与考试流

当前由 ADMIN 在 Web 端发起 AI 题目生成，`QuestionGenerationController` 的选项、生成和导入入口均执行 `requireAdmin`。试卷控制器的权限边界不同：创建、列表、详情、下载和预览只校验登录用户，发布与取消发布才校验 ADMIN。由于 Web 路由未做统一角色过滤，MERCHANT 当前可能进入并调用前一组试卷能力，这是授权限制而非设计目标。教师出题、组卷和预览仍属于待授权目标。

## 3.7 逻辑模块划分

| 模块 | 入口 | Java 业务职责 | Python 或外部职责 | 关键状态 |
|---|---|---|---|---|
| 统一身份与校园服务 | App：STUDENT、TEACHER；Web：ADMIN、MERCHANT | 角色实体、产品入口白名单、业务权限和校园领域接口 | 无必需 AI 依赖 | 用户、角色、登录入口、领域业务记录 |
| AI Leader 与 Python 学习 | 移动端 | 会话、五轮补问、工作流恢复、路径、推荐和结果登记 | 校园 Leader 单目标路由；课程六资源 DAG、复审、导出 | 会话、工作流、资源、掌握度、路径和推荐 |
| 七维画像与掌握度 | 移动端、Java 服务 | 维度规则、证据候选、汇总应用、知识点测评与趋势 | 画像总结只读解释快照 | 维度、证据状态、掌握度、置信度、应用结果 |
| 会议与研讨 | App：STUDENT、TEACHER | 会话、参与者、转写记录、会后结果编排 | 讯飞 ASR；会议类专业智能体 | 会议状态、partial 与 final 文本、分析结果 |
| 知识库问答 | Web：ADMIN 当前；App 目标角色待授权 | 当前 ADMIN 负责 MaxKB 治理和问答结果处理 | MaxKB hit-test 检索；系统 LLM/agent 生成最终回答 | 知识库、会话、引用、异常状态、授权范围 |
| 个性化资源与路径 | 移动端 | 工作流登记、局部重试、资源下载、路径版本与互动 | 六节点 typed DAG、统一复审、真实导出 | completed/partial、失败类型、六资源、路径项和推荐 |
| 题库与试卷 | Web：ADMIN、MERCHANT 当前可见；TEACHER 目标 | 题目生成只允许 ADMIN；试卷创建、查询、下载和预览只校验登录；发布状态只允许 ADMIN | AI 生成题目候选 | 题目 JSON、试卷、预览证明与待加固授权范围 |
| 在线考试 | 移动端 | 尝试、答案快照、版本、自动保存、客观评分和学习反馈 | 当前不依赖 Python 直接评分主观题 | 尝试、答案版本、分数、掌握度变化、画像 candidate 与路径版本 |
| AI 与系统治理 | Web：ADMIN、MERCHANT 可登录 | 控制器权限不一致；MaxKB 与题目生成仅 ADMIN，试卷部分接口缺少角色校验 | Catalog 与模型执行配置 | 模型、智能体、知识库、系统配置和授权缺口 |

## 3.8 当前实现

当前源码形成 uni-app、React/Vite、Spring Boot 和 FastAPI 四个工程边界。登录服务限制 App 为 STUDENT 或 TEACHER、Web 为 ADMIN 或 MERCHANT；MaxKB 与 AI 题目生成控制器限制为 ADMIN，但试卷创建、列表、详情、下载和预览只校验登录。Python Catalog 维护普通专业智能体，课程资源 DAG 独立编排六类交付；Python 工具可转发 Authorization 反向调用 Java，Java↔Python 内部请求可使用共享令牌，工作流与会话可使用 Redis 并保留受限内存降级。

系统同时存在两种明确的多智能体拓扑：普通校园任务由 Leader 选择一个目标能力按需执行；Python 课程资源由六个类型化生成节点与复审节点形成受控 DAG。二者共享 Catalog/Runner 基础但不相互替代。动态路径和考试反馈由 Java 持有写权，模型解释不会直接转换为不可回溯的分数。

## 3.9 部分实现

会议 ASR WebSocket 与会后处理具备代码链路，仍依赖真实外部服务和完整场景验证。教师 Web 治理与目标角色 MaxKB 问答尚未授权；MERCHANT 试卷访问缺少前后端一致限制；普通助手服务端取消端点没有现有证据；真实 Python 课程 MaxKB 包、在线事实评测、压测和五服务实启仍未完成。上述事项分别作为部分实现、当前限制或提交前工作处理。

## 3.10 部署边界

| 部署单元 | 当前启动方式 | 网络暴露建议 | 依赖 | 状态与限制 |
|---|---|---|---|---|
| MySQL | Compose 基础设施服务 | 仅向受控后端网络开放 | 持久卷、初始化与备份策略 | 已有开发部署入口，生产备份与容灾需另行建设 |
| Redis | Compose 基础设施服务 | 仅向受控服务网络开放 | 持久化或缓存策略按场景配置 | 已有开发部署入口，故障降级需按链路验证 |
| Spring Boot | Compose 或 Java 部署说明 | 作为主要业务 API 边界，经网关或反向代理对端提供 | MySQL、Redis、FastAPI、MaxKB、讯飞 | 当前主要业务服务，生产 TLS、限流和监控需环境治理 |
| React/Vite Web | 构建后由 Web 容器或 Nginx 提供 | 公开静态资源与受控业务入口 | Spring Boot API | Compose 覆盖相关部署边界 |
| uni-app 移动端 | HBuilderX 或目标平台构建 | 只连接受控 Java API 与会议入口 | Spring Boot API | 真机兼容和发布流程仍需验收 |
| FastAPI Python AI 服务 | 启动脚本或 `deploy/compose.submission.yml` 中的 `ai-server` | 接收带内部令牌的 Java 调用，也会携带 Authorization 反向查询 Java | 模型提供商、Java、Redis 与进程内存 fallback | 已纳入五服务 Compose/CI 静态契约；镜像实构建、实启和外部模型仍待验证 |

提交拓扑由 Compose 按健康依赖启动 MySQL、Redis、Java、Python AI 和 Web，uni-app 仍独立构建。`docker compose ... config --quiet` 已通过，只证明清单可解析；当前记录没有镜像实构建、`up -d` 或 `deploy/verify.sh` 结果，因此不能把静态通过写成一键部署已运行。外部 MaxKB、讯飞和模型服务仍须单独配置与验证。

## 3.11 安全边界

1. 终端用户只访问受控 Java 业务接口和必要的会议连接，不直接访问数据库、Redis、模型提供商或 Python 内部路由。
2. Java 调用 FastAPI 时附加 `X-AI-Internal-Token`，Python 反向调用 Java 时转发 Authorization；两类值均不得进入日志、资源信封或导出元数据。
3. Python `/internal/*` 应限制在内网；当前令牌只在 `AI_INTERNAL_TOKEN` 已配置时校验，生产环境还须强制非空、轮换并结合网络策略、超时和请求规模控制。
4. WebSocket 需要 Origin 白名单、连接限流、消息规模限制和审计；这些属于待实施的安全强化项。
5. Web 导航、路由和试卷接口需要补齐一致的角色授权，优先阻止 MERCHANT 越过商户业务边界。
6. 被跟踪的 JWT 与第三方活动凭据已改为环境变量或空值，但数据库开发默认口令、Git 历史 secret/PII 扫描、密码哈希、密钥轮换、TLS、备份、容灾和生产监控仍须专项验收。

## 3.12 后续规划

后续架构演进优先完成五项工作：第一，为 Web 导航、路由和试卷接口建立一致角色授权；第二，导出并恢复真实 Python 课程 MaxKB 包；第三，为普通流式任务实现可验证的服务端取消端点；第四，在真实模型环境运行 30 题评测、5×50 压测和至少五类资源演示；第五，实际构建并启动五服务镜像，完成真机、安全和生产等价端到端验证。

这些规划不改变当前系统边界：Java 继续拥有业务事实和治理规则，Python 继续拥有模型与智能体执行，外部服务继续通过受控适配层接入。任何状态提升都应先形成代码、测试和部署证据，再更新工程证据索引与项目说明书。
