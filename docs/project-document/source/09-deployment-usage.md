<!-- SECTION_BREAK chapter="第九章 安装部署与使用" -->
# 第九章 安装部署与使用

## 9.1 部署范围与当前边界

系统由 uni-app 移动端、React Web 管理端、Spring Boot Java 后端和 FastAPI Python AI 服务四个工程边界组成。提交拓扑 `deploy/compose.submission.yml` 包含 MySQL、Redis、Java backend、Python `ai-server` 与 Web 五个服务，并在 CI 中执行六阶段离线门禁、Compose 静态解析和 SHA 标记镜像构建步骤。当前本地证据只确认质量门禁和 `docker compose ... config --quiet` 通过，未确认镜像实际构建、`up -d` 或健康冒烟成功。

部署目标环境使用 Java 21、MySQL 8、Redis 7、React/Vite/Nginx 和 FastAPI；移动端仍使用 HBuilderX 独立构建。FastAPI 既可由启动脚本单独运行，也可作为 Compose `ai-server` 运行。MaxKB、讯飞和模型提供方不随镜像内置，必须分别配置、连通和审计。

<!-- CALLOUT type="risk" title="Compose 证据边界" -->
五服务清单、Dockerfile、健康检查、内部 DNS 和令牌/地址注入已经存在，但静态配置通过不等于镜像能在干净机成功构建，更不等于外部 MaxKB、讯飞和模型已经可用。只有完成 SHA 镜像构建、`up -d`、`ps`、`deploy/verify.sh` 和业务冒烟后，才能宣称一键部署实测通过。
<!-- END_CALLOUT -->

## 9.2 环境清单

| 类别 | 组件与版本基线 | 用途 | 上线前检查 |
|---|---|---|---|
| 操作系统 | 能运行 Java、Node、Docker、Python 与 Nginx 的受支持环境 | 承载服务或构建工具 | 时间同步、磁盘、文件权限、日志目录和字符集 |
| 数据库 | MySQL 8 | 持久化用户、角色、画像、会议、题库、试卷、考试与校园业务 | 独立数据库、最小权限账号、字符集、备份和恢复演练 |
| 缓存 | Redis 7 | Python 记忆等短期状态 | 访问控制、内网限制、持久化策略和不可用降级 |
| Java | Java 21、Spring Boot、Maven | 业务 API、权限、持久化与治理 | JDK 版本、配置注入、数据库迁移和健康状态 |
| Python | 受项目依赖约束的 Python、FastAPI | 模型调用、Leader 与专业智能体执行 | 虚拟环境、依赖安装、内部端口和外部服务配置 |
| Web | Node、React/Vite/Nginx | 管理端构建、静态托管和 API 代理 | Node 版本、构建产物、路由回退和代理超时 |
| 移动端 | HBuilderX、uni-app | App 调试、构建与发行 | 平台证书、接口地址、麦克风和文件权限 |
| 外部服务 | MaxKB、讯飞、模型提供方 | 知识检索、实时 ASR 与模型推理 | 凭据已配置、网络白名单、配额、超时和降级 |

硬件资源需要根据并发、模型调用模式、试卷转换和文件保留策略进行容量评估。当前没有可复现的 QPS、P95、并发上限或可用性 SLA，因此本文不提供虚构的 CPU、内存或带宽承诺；部署前应以目标数据量和业务峰值执行专项容量测试。

## 9.3 配置与敏感信息管理

公开部署文档只列配置项名称和用途，不复制当前配置文件中的任何凭据值。所有密码、令牌和密钥应由环境变量、密钥管理服务或受控挂载注入；日志、构建产物、前端环境文件和截图不得包含真实值。

| 配置类别 | 配置项名称或语义 | 使用方 | 保护要求 |
|---|---|---|---|
| 数据库连接 | `spring.datasource.url` | Java | 地址可按环境配置，不在公开文档写真实主机和库名 |
| 数据库账号 | `spring.datasource.username` | Java | 使用最小权限服务账号，不使用个人账号 |
| 数据库密码 | `spring.datasource.password` | Java | 通过密钥注入并定期轮换，禁止提交仓库 |
| Redis 地址 | `REDIS_URL` 或 Java host、port、database 配置名 | Java 或 Python | 限制在内网，按环境提供，不在客户端暴露 |
| Java 内部地址 | `JAVA_BACKEND_BASE_URL` | Python | Compose 使用 `http://backend:8080`；本地缺省仍为 localhost |
| Python AI 地址 | `AI_PYTHON_BASE_URL` | Java | Compose 使用 `http://ai-server:8081`；仅在受控服务网络可达 |
| MaxKB | base URL、username、password 或 token 配置名 | Java | 只列名称，真实值进入密钥管理并轮换 |
| 模型服务 | model base URL、model name、API key 配置名 | Python | API key 不进入前端、日志、异常正文或测试快照 |
| 讯飞 ASR | APP ID、API key、API secret 配置名 | Java | 三类凭据分别注入；签名材料不得写入日志 |
| 内部认证 | `AI_INTERNAL_TOKEN`、`X-AI-Internal-Token` 与 Authorization 转发 | Java、Python | Compose 注入同一令牌；原始值不持久化、不打印，并须轮换 |

跟踪配置中的 JWT 与腾讯/高德/阿里云等第三方活动凭据已改为环境变量或空值，`data.sql` 不再注入活动密钥；但数据库开发默认口令仍存在，Git 历史 secret/PII 扫描和轮换未执行。业务密码比较仍需迁移到 BCrypt 或 Argon2，会议 WebSocket 仍需收紧 Origin 白名单并增加限流和审计。

## 9.4 部署拓扑与启动顺序

<!-- FIGURE src="images/deployment-boundary.png" caption="图9-1 五服务提交拓扑、内部令牌与外部依赖边界" width_cm="15.5" -->

Compose 通过健康依赖表达顺序：MySQL/Redis 就绪后启动 Java，Java 与 Redis 健康后启动 Python，Java/Python 健康后启动 Web。移动端与外部 MaxKB、讯飞、模型仍独立配置。下游不得在上游不可用时用无休止重试掩盖故障。

1. 备份目标数据库，确认 MySQL 8 和 Redis 7 的网络与访问控制。
2. 从 `deploy/.env.example` 创建不入 Git 的真实 `.env`，至少注入数据库口令、JWT secret 与非空 `AI_INTERNAL_TOKEN`。
3. 先执行 `docker compose ... config --quiet`，再构建带最终 SHA 的五服务镜像。
4. 启动 Compose 并检查 backend `/actuator/health`、ai-server `/healthz` 和 Web 健康状态。
5. 从 Java 所在网络验证带内部令牌的 Python 接口，从 Python 所在网络验证 Java 受控回调和 Redis。
6. 构建 React/Vite 产物，配置 Nginx 静态托管、前端路由回退和 API 代理。
7. 使用 HBuilderX 导入 `AppFrontend`，按目标环境设置 API 地址并执行真机权限测试。
8. 依次验证身份、AI、画像、会议、知识库、题库试卷和考试，不用单一首页可访问代替全链路验证。

## 9.5 MySQL 8 与 Redis 7

提交 Compose 位于仓库根目录 `deploy/compose.submission.yml`。执行输出应保存为脱敏记录，不能把环境变量展开后的凭据复制到文档或工单。

```bash
docker compose --env-file deploy/.env -f deploy/compose.submission.yml config --services
docker compose --env-file deploy/.env -f deploy/compose.submission.yml config --quiet
docker compose --env-file deploy/.env -f deploy/compose.submission.yml build
docker compose --env-file deploy/.env -f deploy/compose.submission.yml up -d
docker compose --env-file deploy/.env -f deploy/compose.submission.yml ps
bash deploy/verify.sh
```

MySQL 初始化应核对角色基线、实体所需表和迁移顺序。当前源码扫描为 65 个 JPA 实体和 64 个唯一映射表，但实体数量不是迁移成功判据；需要检查实际表结构、唯一约束、索引和必要物理外键。不得在已有业务库无审查执行可能覆盖数据的历史初始化脚本。

Redis 7 不可用时，Python 记忆服务当前会退化为进程内存。该降级只能用于保持有限运行，不能保证多进程共享、重启后持久化或跨实例一致性。生产部署应监控降级事件，并在 Redis 恢复后按业务规则重建或清理短期状态，而不是把内存模式当成等价高可用方案。

## 9.6 Java 21 服务

Java 服务负责认证、权限、业务状态、JPA 持久化、MaxKB 调用治理、试卷文档和对 Python AI 的编排。启动前确认 Java 21 生效、Maven 依赖可解析、数据库可连接、配置项已通过受控渠道注入。

```bash
cd AppBackend
mvn -version
mvn spring-boot:run
```

若使用构建产物部署，应先执行项目规定的测试和打包命令，再以受控进程或容器运行。启动检查至少包括：应用进入就绪状态；数据库连接与迁移无错误；未在日志输出密码、令牌或原始 Authorization；AI 服务不可用时返回可诊断状态；下载目录不可由公网直接遍历。

当前离线门禁记录 Java 389 tests / 0 failures / 0 errors / 1 opt-in skipped。该结果基于隔离测试环境；部署环境能启动数据库仍不代表真实 MySQL、MaxKB 或模型业务已经通过，应在最终整合 SHA 上重跑并保留原始报告。

## 9.7 Python FastAPI AI 服务

Python AI 服务支持独立启动，也已作为 `ai-server` 纳入提交 Compose 与 CI 构建步骤。独立启动适用于本地开发，比赛验收应优先使用提交拓扑并保留镜像 digest 与健康记录。

```bash
cd ai-servers
./start-ai-server.sh
```

启动后依次检查：FastAPI 进程可用；Java 能访问所需 `/internal/*` 入口；Python 反向调用 Java 时转发原始 Authorization 且日志不记录该值；目标模型与必要工具配置存在；Redis 不可用时能够明确识别内存降级。Python `/internal/*` 是内网服务契约，不应单独发布为终端用户或公网 API。

Python 使用 `JAVA_BACKEND_BASE_URL` 和 `REDIS_URL`，Java 使用 `AI_PYTHON_BASE_URL`；Compose 分别注入 `backend`、`redis` 和 `ai-server` 内部 DNS 地址。缺省 localhost 只服务本地开发。Redis 不可用时进程内存 fallback 不具备多实例共享或重启持久性；`AI_INTERNAL_TOKEN` 为空时 Python 不执行 header 校验，生产 `.env` 必须提供非空值并限制 `/internal/*` 网络可达范围。

## 9.8 React/Vite/Nginx Web 端

Web 端通过 Vite 构建静态资源，再由 Nginx 承载。构建环境不得把服务端密钥写入前端变量；浏览器可见配置只能包含公开 API 地址和非敏感开关。

```bash
cd AppWeb
npm install
npm run build
```

Nginx 部署需要配置静态根目录、单页应用路由回退、Java API 代理、SSE 禁止不当缓冲、合理的上传与读取超时以及安全响应头。生产启用 TLS、域名和代理规则时应使用环境证书管理，不把私钥存入仓库。

当前 Web 登录允许 ADMIN 与 MERCHANT，但路由与导航尚未统一按角色过滤。MaxKB 和 AI 题目生成等控制器要求 ADMIN；随机预览、创建、列表、详情、预览与下载只校验登录，发布和取消发布要求 ADMIN。因此 Nginx 路由可达性、前端菜单隐藏和后端权限是三层不同控制，部署验收必须用至少 ADMIN 和 MERCHANT 两类账号执行授权回归。

## 9.9 uni-app 与 HBuilderX

移动端工程位于 `AppFrontend`，使用 HBuilderX 导入、运行和构建。开发和发行环境应分别配置 Java API 地址、必要的 WebSocket 地址和平台权限，不把数据库、MaxKB、模型或讯飞服务端凭据放入客户端。

1. 在 HBuilderX 中导入 `AppFrontend`，确认依赖和页面分包可解析。
2. 选择目标运行平台，按环境提供公开 API 和 WebSocket 地址。
3. 使用 STUDENT 和 TEACHER 账号验证 App 登录；ADMIN 和 MERCHANT 应被 App 入口拒绝。
4. 验证原 AI 与校园入口仍可用，再验证 Python 五轮补问、六资源工作流、`partial` 单项重试、路径和推荐。
5. 验证 AI SSE 流式状态、工作流恢复、资源卡片和受控下载，记录断网恢复行为。
6. 验证会议麦克风授权、WebSocket 连接、partial 与 final 区分以及外部 ASR 错误。
7. 验证考试开始、自动保存、交卷、`learningUpdate`、路径版本变化与重复提交幂等。
8. 构建发行包前执行真机隐私、权限、日志和包体配置检查。

模拟器或浏览器运行不能代替真机验证。麦克风、文件保存、后台切换、网络变化和系统权限在不同设备上表现不同，应记录设备、系统版本和复现步骤。

## 9.10 外部服务接入

| 外部依赖 | 接入用途 | 连通检查 | 失败与降级 |
|---|---|---|---|
| MaxKB | 账号、知识库管理与 hit-test 检索 | 验证认证、目标知识库和命中测试 | 超时或资料不足时返回失败或受限回答，不伪造 citations |
| 系统模型服务 | Leader、专业智能体和最终回答生成 | 验证模型名、认证、超时和结构化输出 | 任务进入明确失败状态，不直接改写画像或题库 |
| 讯飞 ASR | 会议实时语音识别 | 验证签名、WebSocket、音频格式和 partial 或 final | 保留已确认 final，外部断开不虚构文本 |
| 文件转换工具 | 试卷真实预览和文档转换 | 验证可执行程序、隔离配置、字体和输出目录 | 转换失败不签发有效预览证明 |

外部服务凭据只通过配置项名称被引用。连通性测试应使用专用测试账号和最小权限，结果记录只保留时间、服务类别、状态、请求标识与脱敏错误；不粘贴令牌、签名原文或响应中的个人数据。

## 9.11 首次启动验收

| 顺序 | 检查项 | 预期结果 | 不通过时处理 |
|---:|---|---|---|
| 1 | MySQL 8 与 Redis 7 | 服务可达，账号权限与数据目录符合环境要求 | 停止上层启动，修复网络、权限或初始化 |
| 2 | Java 21 | 应用就绪，数据库无迁移错误，日志无敏感值 | 检查配置名称、数据库和 JDK，不跳过错误继续发布 |
| 3 | FastAPI | `ai-server` 健康，内部接口仅在受控网络可达 | 检查镜像、依赖、环境地址和模型配置 |
| 4 | Java 与 Python 双向调用 | Java header 使用共享内部令牌，必要回调携带用户认证 | 检查 `AI_INTERNAL_TOKEN`、内部 DNS 和网络策略 |
| 5 | Web 与 Nginx | 静态路由、API、SSE 和下载可用 | 检查代理缓冲、超时、路由回退和权限 |
| 6 | App 与 HBuilderX 真机 | 登录、AI、会议、资源和考试主路径可操作 | 记录设备与权限，修复后重新执行 |
| 7 | 角色授权 | ADMIN、MERCHANT、TEACHER、STUDENT 边界符合当前事实 | 对商户试卷缺口建立已知风险与整改项 |
| 8 | 外部依赖 | MaxKB、模型和讯飞分别返回可诊断结果 | 不把外部未配置包装成应用成功 |

首次启动验收之后仍需执行性能、渗透、备份恢复和生产端到端测试。健康入口成功只证明进程或局部依赖可用，不证明 AI 回答正确、会议长时稳定、试卷视觉一致或考试在弱网下可靠。

## 9.12 用户操作概要

### 9.12.1 学生

学生从 App 登录后既可继续使用原校园、AI、会议和考试能力，也可进入 Python 个性化学习：首页逐轮回答五个画像问题，按主题生成六类资源，查看/下载成功项，对 `partial` 的失败类型单独重试，开始或完成路径项并处理精准推荐。交卷后结果页在真实返回 `learningUpdate` 时展示薄弱点、candidate 状态和路径版本变化；主观题和缺失反馈不得伪造完成提示。

### 9.12.2 教师

教师当前从 App 登录，使用已授权的学习和会议能力。Web 题库、试卷和知识库治理中的教师角色是目标状态，并非当前统一授权事实；需要补齐 Web 登录角色、路由和控制器授权后才能按教师流程验收。

### 9.12.3 管理员

管理员从 Web 登录，管理 MaxKB、AI 题目候选、试卷发布与系统治理。题目生成先审查结构化候选再导入，模板试卷先生成真实预览再使用一次性证明创建，外部配置只查看是否已配置和验证状态，不回显完整凭据。

### 9.12.4 商户

商户当前可以从 Web 登录，使用与其业务相关的页面。由于 Web 路由未统一过滤，随机预览、创建、列表、详情、预览与下载只校验登录，商户可调用这些试卷接口；这属于授权缺口，不是产品设计目标。上线前应补齐后端最小权限、前端导航过滤和回归测试。

## 9.13 运维与故障排查

| 现象 | 优先检查 | 安全处理 | 禁止做法 |
|---|---|---|---|
| Java 启动失败 | Java 21、数据库、迁移和配置项是否存在 | 使用脱敏日志定位，恢复上一个可用配置 | 将密码或完整连接串粘贴到公开渠道 |
| AI 请求无终态 | Python 进程、内部地址、模型超时和 SSE 代理 | 依据任务标识核对两端状态，标记失败后安全重试 | 把半截回答改为完成或无限重试 |
| 课程资源为 partial | 失败类型、审核状态、导出附件和 retryable | 恢复工作流，只重试失败类型并保留成功资源 | 清空整个包、把审核拒绝或空附件标成成功 |
| 内部接口 401 | 两端 `AI_INTERNAL_TOKEN` 是否一致、header 是否被代理剥离 | 轮换并重新注入同一值，检查网络策略 | 临时关闭令牌校验或把令牌打印到日志 |
| Redis 降级 | Redis 网络、认证和容量 | 标明内存模式，恢复 Redis 后验证状态 | 宣称内存降级具有跨进程持久性 |
| 会议无转写 | 麦克风权限、Java WebSocket、讯飞配置和音频格式 | 保留已确认 final，重连并记录外部错误 | 伪造转写或输出签名材料 |
| 试卷预览失败 | 转换工具、字体、输出目录和证明状态 | 重新生成预览，不消费失效证明 | 绕过预览直接创建模板试卷 |
| 自动保存冲突 | 尝试状态、服务端版本和本地草稿 | 获取权威版本并按业务规则合并或提示用户 | 用旧答案覆盖新版本 |
| 下载失败 | 登记、权限、文件存在性和完整性 | 重新生成或恢复登记，隐藏服务器位置 | 返回本地绝对路径或其他用户资源 |

运维记录应包含最终 SHA、镜像 digest、环境、时间、工作流或请求标识、脱敏错误和处置结果。当前生产监控、容灾、备份恢复、密钥轮换和统一审计仍需完善；Compose 静态解析或单个健康入口成功均不能单独证明生产级运维体系。
