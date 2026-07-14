# APP 助手资源推送与证据链设计

## 结论

项目当前已经具备可复用的主链路：uni-app APP 通过 Spring Boot 的登录态和会话边界调用 FastAPI Leader，Python 返回文本、附件和调用轨迹，Java 保存消息，画像证据再回注后续对话。问题不在于缺少 AI 助手，而在于资源和来源没有统一契约，导致同一条消息在实时、历史、完整会话页和悬浮助手之间表现不一致。

本次在现有机制上增量补齐，不新建平行资源中心：

- Python 负责把智能体、业务工具、附件和检索结果归一成 `resources + evidenceChain`。
- Java 继续作为认证、会话所有权和长期持久化的权威边界。
- APP 的完整会话页和悬浮助手共享同一套纯函数归一化规则，并都能展示资源和来源。
- 资源点击、打开、下载形成资源级画像证据，闭合“推荐—消费—偏好”的反馈环。
- 原有 `answer / outputTypes / outputMeta / attachments / trace / matchedResults` 全部保留；新生成资源在旧客户端仍以 `attachments` 展示。历史中已经保存的无鉴权 Python 静态链接不承诺继续可用，它们必须标为 `legacy_unavailable`，不能为了兼容重新开放不安全的静态目录。

## 当前框架与权威边界

```text
uni-app APP / React 管理台
            │ JWT、REST、SSE
            ▼
Spring Boot 业务中台
  ├─ 用户、会话、校园业务、画像证据
  ├─ MySQL 长期持久化
  ├─ 业务权限和资源下载授权
  └─ Python AI 网关
            │ 内部 HTTP / SSE
            ▼
FastAPI AI 服务
  ├─ Leader 路由和专业智能体
  ├─ Java 业务工具回调
  ├─ 第三方模型与生成能力
  ├─ Redis 短期上下文
  └─ 生成文件落盘
```

边界约束：Python 可以整理证据，但不能决定 APP 用户是否有权读取历史或下载文件；Java 可以持久化证据快照，但不重新猜测资源语义；APP 只负责解释和交互，不从答案文本重建权威来源。

## 审计结果

### 已经做好

- Leader 同步和 SSE 对话、会话历史、JWT 隔离。
- 文本、图片和通用附件的基础返回。
- `retrievalMeta`、`trace`、`attachments` 的消息级持久化。
- 画像候选证据、定时聚合和后续回注。
- Java 业务工具可返回课程、活动、会议、餐饮、设施和二手数据。

### 确定缺口

- `matchedResults` 只在实时响应存在，未保存到消息，刷新历史后来源永久丢失。
- 悬浮助手只展示文本和图片，文件、视频、后续操作全部丢失。
- 业务工具的结构化结果被压成文本，不能形成可操作的校园资源卡。
- 前端把 `javaBackendCount + documentCount` 相加，同一结果在历史中会重复计数。
- 生成文件默认写系统临时目录并公开静态访问，默认 URL 端口还与 AI 服务端口不一致；历史链接不可依赖，也不经过会话所有权校验。
- 资源点击、下载和忽略没有形成画像证据，当前偏好主要依靠自然语言猜测。
- 当前 trace 只能说明“调用了什么”，不能说明“该资源依据什么来源生成”，也不能区分有来源、仅用画像上下文和纯模型生成。

## 资源种类

资源契约把“语义种类”和“交付形式”分开。一个思维导图可以用图片交付，也可以用 Mermaid 文件交付；课程属于校园语义资源，但以业务卡片交付。

### 学习资源种类

| `kind` | 中文含义 | 当前生产能力 | 本次契约/展示 |
| --- | --- | --- | --- |
| `explanation` | 知识讲解 | 已有文本/文档 | 支持 |
| `mind_map` | 思维导图 | 已有图片/MMD | 支持 |
| `diagram` | 流程图、架构图、活动图 | 已有图片/MMD | 支持 |
| `exercise` | 练习题、题库 | 已有文本/JSON | 支持通用卡，专用答题交互后续扩展 |
| `code_example` | 代码案例、代码包 | 已有文本，部分可 ZIP | 支持 |
| `extended_reading` | 延伸阅读 | 当前无专门智能体 | 契约支持；没有真实来源时不得伪造链接 |
| `image` | 图片、海报、PPT 配图 | 已有 | 支持 |
| `video` | 视频或短视频 | 仅消费端基础能力 | 支持消费；不宣称已具备稳定生成器 |
| `audio` | 音频、播报 | 当前主要是脚本 | 契约支持；真实音频生成后续接入 |
| `document` | MD、PDF、Word | 已有 MD/DOCX，外部 PDF 可消费 | 支持 |
| `presentation` | PPT/PPTX | 当前主要是大纲、配图 | 契约支持 |
| `spreadsheet` | XLS/XLSX/CSV | 已有 XLSX 导出 | 支持 |
| `bundle` | 组合资料包 | 已有 ZIP 基础 | 支持 |

### 校园业务资源种类

| `kind` | 数据来源 | 交付形式 |
| --- | --- | --- |
| `course` | 课表/课程工具 | `business_card` |
| `activity` | 活动工具 | `business_card` |
| `meeting` | 会议工具 | `business_card` |
| `dining` | 食堂、店铺、菜品工具 | `business_card` |
| `facility` | 校园设施/地图工具 | `business_card` |
| `secondhand` | 二手交易工具 | `business_card` |

资源动作 `ActionType` 与行为证据 `InteractionType` 分开。V1 动作只允许 `open_resource / download / preview / follow_up`；行为证据允许 `view / open / download / preview / follow_up / dismiss`。业务卡页面地址由 APP 根据 `kind + businessId` 本地白名单映射，不执行模型给出的任意 scheme/route。外部链接仅允许 HTTPS 和配置化批准域名。注册、加入、认领、联系、收藏等改变业务状态的动作必须继续走各业务模块自己的授权接口，不在资源卡里直接绕过确认。

## 方案比较

### 方案 A：只补 APP 展示

优点是改动小；缺点是历史来源仍然丢失、Python/Java/两个 APP 入口继续各自猜类型，安全下载也没有解决。不能形成证据闭环，拒绝。

### 方案 B：新建独立资源中心和投递队列

可以承载站内信、异步通知和成员级投递，但会新增资源表、收件人表、投递状态和任务系统。当前用户目标是 APP 助手对话内推送和证据链，直接引入该系统会偏离现有机制，留作真正通知/站内信需求的后续阶段。

### 方案 C：兼容信封（采用）

在现有响应上新增统一 `resources` 和 `evidenceChain`，Java做类型、体积和安全白名单校验后持久化并施加权限，APP共用渲染和反馈规则。它能一次修复实时/历史差异、悬浮入口缺失、来源不可解释和附件权限问题，并保留旧字段作为兼容层。

## `AssistantResource` 契约

```json
{
  "schemaVersion": "assistant-resource-v1",
  "id": "res_...",
  "messageId": 123,
  "kind": "document",
  "deliveryType": "document",
  "groundingStatus": "model_only",
  "title": "循环队列复习资料.docx",
  "summary": "根据本轮回答生成的 Word 资料",
  "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "storageKey": "3d8f...-资料.docx",
  "url": "/api/ai/leader/sessions/{sessionId}/messages/{messageId}/exports/{storageKey}",
  "previewUrl": "",
  "sourceType": "generated_export",
  "sourceId": "agent_generated_file",
  "evidenceIds": ["ev_..."],
  "actions": [
    {"type": "download", "label": "下载", "target": "resource", "requiresAuth": true}
  ],
  "authScope": "session_owner",
  "createdAt": "2026-07-14T12:00:00Z",
  "expiresAt": null,
  "integrity": {"algorithm": "SHA-256", "digest": "...", "size": 12345},
  "payload": {"type": "document", "format": "docx"},
  "metadata": {}
}
```

规则：

- `id/evidenceId/chainId/requestId` 不能使用数组下标；消息落库后即成为持久标识，实时和历史不得重新编号。
- `kind` 表达语义，`deliveryType` 只决定渲染器。
- 每个资源有自己的 `groundingStatus`；混合响应不能用响应级状态掩盖某个资源没有事实来源。
- `messageId` 由 Java 完成消息落库后写入；Python 不伪造数据库 ID。
- `payload` 是按 `kind` 白名单生成的可展示字段。业务工具原始 `item` 不直接透传，避免 PII、内部字段和超大 JSON 进入历史。
- 所有下载 URL 返回 Java 相对 API 地址；APP不得收到 Python 的 `localhost` 或内部地址。
- `metadata` 只保存安全白名单字段，模型 API Key、Authorization、完整画像原文不得进入资源。
- Python 生成文件会携带仅供 Java 使用的随机 capability；Java 将它保存于内部附件快照，返回客户端和历史 DTO 前必须剥离。公开契约永远不含 capability。
- 旧 `attachments` 继续返回；它们由同一个 builder 派生或被 builder 吸收，避免两套含义。
- 新类型默认退化为通用资源卡，不导致页面报错。
- 内容资源使用 `payload={type:"content", content, language}`；业务卡使用 `payload={type:"business", businessId, title, subtitle, fields}`；文件资源使用 `payload={type:"file", format, size, digest}`。三类 payload 均有长度和字段白名单。

六类校园业务卡只允许以下规范字段，Python 可以从来源别名映射但不能附带原始 item：

| `kind` | 允许的 `payload` 字段 |
| --- | --- |
| `course` | `businessId, courseName, teacherName, weekday, startSection, endSection, classroom, weekText` |
| `activity` | `businessId, title, category, startTime, endTime, location, status` |
| `meeting` | `businessId, title, startTime, endTime, location, status` |
| `dining` | `businessId, name, category, location, openingHours, rating, priceRange, imageUrl` |
| `facility` | `businessId, name, category, location, openingHours, status, longitude, latitude` |
| `secondhand` | `businessId, title, category, price, condition, status, createdAt, imageUrl` |

所有卡片都禁止 `userId/sellerId/phone/contact/memberList/participants/transcript/token/raw` 及其大小写/下划线变体；未知字段直接丢弃。时间和文本分别受统一格式与长度限制，图片 URL 只允许 HTTP(S) 安全解析。

## `EvidenceChain` 契约

```json
{
  "schemaVersion": "assistant-evidence-v1",
  "chainId": "chain_...",
  "requestId": "req_...",
  "status": "grounded",
  "generatedAt": "2026-07-14T12:00:00Z",
  "evidenceState": "available",
  "queryDigest": "sha256:...",
  "answerDigest": "sha256:...",
  "sources": [
    {
      "evidenceId": "ev_...",
      "sourceType": "java_backend",
      "sourceId": "course:42",
      "title": "数据结构课程",
      "excerpt": "...",
      "sourceVersion": "2026-07-14T10:30:00",
      "retrievedAt": "2026-07-14T12:00:00Z",
      "contentDigest": "sha256:...",
      "accessScope": "request_user"
    }
  ],
  "steps": [{"stage": "tool_result", "detail": {}}],
  "resourceLinks": [{"resourceId": "res_...", "evidenceIds": ["ev_..."]}],
  "generation": {
    "agent": "leader_agent",
    "model": "configured-model",
    "answerType": "tool_result",
    "profileContextUsed": false
  },
  "integrity": {
    "algorithm": "SHA-256",
    "digest": "...",
    "scope": "canonical-json-without-integrity",
    "signed": false
  }
}
```

`status` 必须真实反映证据强度：

- `grounded`：至少有业务记录或知识文档等事实来源。生成文件只是产物，不能把纯模型答案升级为有事实依据。
- `context_only`：没有事实来源，但明确使用了历史会话或画像上下文；当前提问本身不单独触发该状态。
- `model_only`：只使用当前提问和模型能力，没有业务/知识来源，也没有历史会话或画像上下文；前端必须明确展示“未检索外部来源”。

V1 是响应级和资源级证据链，不冒充逐句事实引用。句子/结论级 `claims[] -> evidenceIds[]` 需要模型结构化输出和服务端引用校验，留作 V2；在此之前不得把模型自述当作来源。

完整性摘要用于发现历史序列化损坏和非预期改写，不是数字签名，也不代表第三方来源真实性。前端文案不得将 `signed:false` 描述为“防伪认证”。

`evidenceState` 的正式枚举为 `available / legacy_missing / malformed / integrity_failed / generation_failed`。状态不是自由文本；Java历史读取会按同一规范重算摘要，摘要不一致时保留消息正文，但不能把来源展示为可信。

规范 JSON 规则固定为：UTF-8、对象键递归字典序、数组顺序保留、无多余空白、Unicode 原文不 ASCII 转义、禁止 NaN/Infinity、时间统一 RFC 3339 UTC（`Z`）。摘要格式统一为小写 `sha256:<64 hex>`。`resource.evidenceIds` 是资源侧便捷副本，`evidenceChain.resourceLinks` 是权威映射；构建和读取时必须验证二者集合相等。

## 生成和持久化流程

```text
Python agent/tool result
  → 统一 resource/evidence builder
  → 同步响应或 SSE done
  → Java 映射 resources/evidenceChain/matchedResults
  → 消息表 LONGTEXT 快照
  → 历史 DTO 原样恢复
  → APP 共用 normalize/render helper
```

Python builder：

1. 从 `documents` 建立来源证据并计算内容摘要；最多保留 20 条，每条摘要最多 800 字，metadata 只允许来源标识、标题、类型、时间、位置和安全路由字段。
2. 从 `attachments` 建立文件/图片/视频资源。
3. 从 Java 工具 `documents.metadata` 建立校园业务卡。
4. 根据执行智能体和 `answerType` 为无附件的学习内容建立非链接内容资源。
5. 将资源映射到直接来源；没有来源时保持空 `evidenceIds` 并降低链状态。
6. 过滤敏感元数据后计算规范 JSON 摘要。

Java消息持久化新增三列：`matched_results_json`、`resources_json`、`evidence_chain_json`。`matched_results_json` 保存经白名单和数量/长度限制的公共快照，不保存 Python 工具原始 item。Java还会用类型化 DTO递归校验 `schemaVersion/id/kind/deliveryType/groundingStatus/payload/action/storageKey/url` 和总字节数，不能因为 Python 已用 Pydantic 就信任任意 Map。

生成文件另建服务端权威 `AiLeaderGeneratedExport` manifest，不从模型可影响的消息 JSON 扫描授权：

```text
id, userId, leaderSessionId, messageId, resourceId,
storageKey, fileName, mimeType, size, sha256,
pythonCapability, createdAt, expiresAt, status
```

`storageKey` 唯一；manifest 只接受 Java从 Python exporter 标记为 `serverGenerated=true` 的内部附件创建。消息 JSON只用于展示，manifest 才是下载授权和完整性校验依据。使用现有 Hibernate `ddl-auto:update` 增量建表/列，不改已有消息主键或会话模型。

## 受控导出下载

AI 生成文件改为：

1. 默认落在 `ai-servers/data/ai-exports`，文件名使用完整 UUID，附件记录 SHA-256、大小、创建时间和过期时间；本地默认目录只保证单机重启后仍在，生产必须挂载持久共享卷或替换为对象存储。
2. 每个文件生成独立的 256 位随机 capability；Python以临时文件 + 原子 rename 同时落盘文件和 `<storageKey>.meta.json` sidecar，sidecar只保存 capability 摘要、文件摘要、大小、MIME、创建/过期时间。重启后从 sidecar 恢复校验，清理时文件与 sidecar 必须成对删除。原始 capability 只随内部响应交给 Java并写入服务端 export manifest。
3. FastAPI 不再用无鉴权 `StaticFiles` 暴露该目录，只提供按 `storageKey` 读取的内部接口，同时要求 `X-AI-Export-Capability` 常量时间校验并阻止路径穿越。普通用户 Bearer 或伪造非空 Authorization 单独存在时一律不能下载。
4. Java 先保存消息和类型化 export manifest，再把客户端可见资源和 legacy attachment 都重写为当前 `sessionId + messageId + storageKey` 的相对下载地址，并剥离 capability。
5. APP 请求 Java 下载地址并携带 Bearer Token。
6. Java 按类型化 manifest 校验当前用户、会话、消息、resourceId、`storageKey` 和 active 状态；再取 capability 代理 Python 文件流。收到字节后重算 SHA-256 和 size，不一致返回完整性错误且不把文件交给 APP。
7. 过期文件返回 410；导出和读取时执行配置化 TTL/容量清理。历史消息保留资源元数据并展示已过期，不返回失效的内部地址。

Python 服务仍应部署在仅 Java 可访问的内网。随机 capability 是每个文件独立的服务间读取凭据，Java 的用户/会话/消息/资源四级校验是 APP 的权威边界；两者都不能省略。生产环境若未显式配置 `AI_EXPORT_ROOT` 为持久挂载目录则启动失败，开发环境才允许仓库内默认目录。

## APP 展示与反馈

完整会话页和悬浮助手都必须支持：

- 图片预览、视频播放、文件下载、业务卡、通用未知资源卡。
- 最多三条白名单动作。
- “依据与来源”摘要：状态、来源数量、生成智能体、生成时间；完整页可以展开来源标题、摘要和时间。
- 历史刷新后与实时消息一致。
- `authScope=session_owner` 的图片/视频不能直接把 Java URL 填进 `<image>/<video>`；先用带 Bearer Header 的 `uni.downloadFile` 下载到临时本地路径，再预览/播放。公开外部媒体仍可直接展示。

资源发生 `view/open/download/preview/follow_up/dismiss` 时，APP 不直接调用通用 `/api/profile/evidence`，而调用资源专用接口：

`POST /api/ai/leader/sessions/{sessionId}/messages/{messageId}/resources/{resourceId}/interactions`

客户端只提交白名单 `action`。Java先验证会话、消息和资源归属，再用现有 `UserProfileService.addEvidence` 构造完整证据：

```text
sourceType=assistant_resource
sourceId=服务端生成的 session/message/resource/action 幂等键
action=<interaction>
objectType=assistant_resource
objectId=resourceId
dimensionKey=resource_preference
evidence=用户对该资源执行了 <action>
metadata={sessionId,messageId,resourceKind,deliveryType,chainId}
```

同一用户对同一消息资源执行同一动作只记录一次；重复提交返回已有/忽略状态。提交失败不阻断资源打开，但不能静默改变本地成功状态；控制台记录安全摘要，不记录 token 或 capability。

## 错误处理与兼容

- 新字段缺失时，APP 继续从旧 `attachments` 展示；不得因为老历史没有证据链而崩溃。指向旧 Python 静态目录的历史生成链接只展示“旧资源不可用”，不直接打开。
- 新客户端优先使用 `resources`，只用旧附件补齐未被资源覆盖的 URL。
- JSON 反序列化失败时 Java 日志记录消息 ID 和字段名；客户端得到空结构和正式 `evidenceState=malformed`，不能伪装成“无来源”。
- 单条公共资源/证据 envelope 有总字节上限；超限时截断来源并标记 `truncated=true`，不能无界写入 LONGTEXT。
- 未知资源类型使用通用卡；未知动作不执行。
- `matchedResults` 命中数量优先取数组长度，否则取 `max(javaBackendCount, documentCount)`，禁止相加。
- 纯模型回答保留，但证据状态明确为 `model_only`。

## 验收标准

- Python 同步和 SSE 均返回同构 `resources` 与 `evidenceChain`。
- 文档、图片、视频、业务工具结果至少分别有契约测试。
- Java 查询响应、消息落库和历史详情不丢经裁剪的 `matchedResults/resources/evidenceChain`，并为实时 SSE 返回真实 `messageId`。
- 生成附件不能通过原 Python 静态 URL公开读取；即使直接访问 Python，缺少 capability 也不能下载；Java仅允许会话和消息拥有者下载已绑定文件。
- 纯函数测试验证两端共用的归一化、URL、动作和证据摘要；源码契约与人工代码审查验证两个 Vue 页面已接线。当前仓库没有 uni-app 组件测试运行时，最终报告必须将“未做真机视觉/交互 E2E”列为验证边界，不能由源码文本测试冒充运行时证明。
- 资源交互形成画像候选证据，并包含资源、会话和证据链标识。
- 历史命中数量不再翻倍。
- 老消息和旧字段继续可用。

## 非目标与后续阶段

- 本轮不新建 APP Push、站内信、收件人和投递状态系统。
- 本轮不宣称已有稳定视频/音频/PPTX生成器；只建立一致的接入和消费契约。
- 本轮不实现逐句 claim-citation 验证、来源删除事件驱动的历史 stale 标记或数字签名。
- 本轮不处理会议“每位成员独立投递”的资源实体；若要做会后成员级推送，应基于本契约再增加 recipient/delivery/feedback 数据模型。
