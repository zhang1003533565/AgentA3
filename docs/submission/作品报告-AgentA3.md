# 中国计算机学会青少年人工智能挑战赛 · 作品报告

| 项目 | 内容 |
|---|---|
| 作品编号 | （填写） |
| 作品名称 | AgentA3——智慧校园个性化学习多智能体系统 |
| 编写日期 | 2026-09-04 |

> **编写说明（提交时删除本框）**  
> 1. 本文按《05-3 作品报告（人工智能挑战赛，2023版）》六章结构撰写。  
> 2. 凡标注 **【图 X-X 截图位】** 处，请按括号内提示自行截图后替换灰色占位框。  
> 3. 文献部分按模板要求省略；核心代码仅提炼算法/编排关键逻辑。  
> 4. 事实基线：`mini_program_app`、`ai-servers`、`AppBackend`、`AppWeb`；不引用 `Frontend/`。

---

## 第1章 作品概述

### 1.1 建设背景与目标

高等教育场景中，学生的课程学习、研讨会议、练习考试与校园日常服务分散在多个入口。通用大模型虽能生成内容，但往往缺少可追溯的学习背景，也难以把过程信号沉淀为可治理的教学证据。教师看到的多是结果性数据，难以及时还原学生在理解、表达、协作与资源使用上的差异。

**AgentA3** 面向上述问题，构建以“证据驱动的个性化学习闭环”为主线的多端系统：把对话补问、资源互动、会议转写与客观考试结果转化为具备来源、置信度与状态的证据；由 Java 业务规则维护七维画像与学习路径；由 Python Leader 按意图路由专业智能体，或进入 Python 课程受控资源生成工作流；最终向学生交付可复审、可导出、可反馈的多模态学习资源。

作品目标可概括为三点：

1. **可追踪**：画像变化、资源交付、考试反馈均可回溯到来源与业务状态，而不是把单次模型输出直接当作事实。  
2. **可协作**：校园通用任务走 Leader 单目标路由；Python 课程资源包走类型化 DAG，两类拓扑隔离，互不硬编码耦合。  
3. **可落地**：保留原有校园服务入口，增量接入 Python 个性化学习、会议、题库与在线考试，形成可演示的完整闭环。

### 1.2 作品定位与创新主线

> 系统将学生的对话、研讨和资源互动转化为可追溯证据，再由 Leader 编排专业智能体，为不同学生生成并推送不同的学习资源。

创新不放在“再做一个聊天机器人”，而放在工程边界与可信机制：

| 方向 | 做法 |
|---|---|
| 画像 | 证据先进 `candidate`，经置信度加权与正负冲突门禁后才可能 `applied` |
| 智能体 | Catalog 登记专业能力；Leader 规则快路径 + LLM 兜底；课程 DAG 并行生成 + 统一复审 |
| 知识问答 | MaxKB 只做 hit-test 检索，最终回答由本系统 Agent 生成并附 citations |
| 考试闭环 | 客观题交卷 → 掌握度更新 → 画像候选 → 路径重排，幂等抑制重复反馈 |
| 多端分工 | 小程序承载学习交互；AppWeb 承载 AI/题库/知识库治理；Java 管事实；Python 管模型执行 |

### 1.3 系统组成一览

| 工程边界 | 技术基线 | 主要职责 |
|---|---|---|
| 移动端小程序 | uni-app（`mini_program_app`） | 学生/教师：校园服务、AI 助手、Python 学习、会议、考试 |
| Web 管理端 | React/Vite（`AppWeb`） | 管理员：MaxKB、智能体、题库组卷、画像规则、课程与会议配置 |
| Java 业务后端 | Spring Boot（`AppBackend`） | 身份权限、画像规则、学习工作流编排、题库考试、会议 ASR 桥接 |
| Python AI 服务 | FastAPI（`ai-servers`） | Leader/Catalog、课程 typed DAG、模型调用、资源复审与导出 |

**【图 1-1 截图位】**  
> 请放置：系统四端关系总览图（可用架构草图或部署拓扑截图）。  
> 建议内容：小程序 / AppWeb → Java(8080) → Python AI(8081)；旁挂 MySQL、Redis、MaxKB、讯飞 ASR。  
> 文件建议保存为：`docs/submission/figures/fig-1-1-architecture.png`

**【图 1-2 截图位】**  
> 请放置：小程序 Python 学习首页 + 管理端知识库/题库入口各一张，体现“学生用 / 管理员治”。  
> 文件建议：`fig-1-2-app-home.png`、`fig-1-2-admin-portal.png`

### 1.4 预期效果

对学生：在保留校园生活入口的同时，获得画像补问 → 资源包生成 → 学习路径/推荐 → 练习考试 → 掌握度反馈的完整链路。  
对教师/管理员：在 Web 端配置知识库、模型与智能体，生成并治理题库试卷，审计会议与画像规则。  
对评审：每项关键结论均可回到相对路径中的生产代码与自动化测试，不以未实测指标替代工程事实。

---

## 第2章 任务分析

> 本章对应赛题“基于大模型的个性化资源生成与学习多智能体系统”关键任务：差异识别、多智能体协作、个性化资源、多端使用与教学管理。

### 2.1 任务拆解

| 任务编号 | 赛题关注点 | 本作品对应实现 | 当前状态 |
|---|---|---|---|
| T1 | 对话式学习画像（≥6 维） | Java 七维画像 + 证据协议 `campus-profile-evidence-v1`；小程序五轮补问与雷达页 | 已实现（效果指标未做固定集评测） |
| T2 | 多智能体协同资源生成（≥5 类） | Catalog 专业智能体 + Python 课程 DAG（讲解/导图/练习/代码/拓展）；PPT 独立管线补齐第六类 | 已实现离线契约；真实模型产物需演示留证 |
| T3 | 个性化路径与精准推送 | `learning_path_agent` 规划 + Java 路径项持久化与推荐排序 | 已实现 |
| T4 | 智能辅导（加分） | Leader 校园问答、课程知识检索、编程辅导智能体 | 部分实现（依赖外部模型/知识库） |
| T5 | 学习效果评估（加分） | 客观题评分 → 掌握度/画像候选/路径版本反馈 | 已实现客观题闭环；主观题不自动评分 |

### 2.2 目标用户与角色

| 角色 | 入口 | 核心诉求 |
|---|---|---|
| STUDENT | 小程序 `applogin` | 个性化学习、会议参与、在线考试、校园服务 |
| TEACHER | 小程序 `applogin` | 学习与会议相关已授权能力 |
| ADMIN | Web `weblogin` | MaxKB、题库生成、试卷发布、智能体与画像规则治理 |
| MERCHANT | Web `weblogin` | 商户业务；教学治理接口仍受 ADMIN 校验约束 |

**【图 2-1 截图位】**  
> 请放置：四类角色用例图（可手绘/用工具绘制后截图）。  
> 建议分三块：学生小程序用例、管理员 Web 用例、系统内部 Java↔Python 协作。

### 2.3 主要功能需求

#### 2.3.1 小程序端（学生/教师）

1. 统一登录与校园服务入口（活动、论坛、课表、地图等既有能力保留）。  
2. AI Leader 对话助手（SSE 流式输出）。  
3. Python 个性化学习：画像补问、资源包生成、学习路径、精准推荐、知识图谱。  
4. 七维画像雷达与证据可解释展示。  
5. 会议预约/现场、实时 ASR 转写、会后分析入口。  
6. 题库收藏、组卷相关能力、在线考试作答与结果反馈。  
7. Python 在线编程练习与 AI 辅导。

**【图 2-2 截图位】**  
> 请放置：小程序功能模块图或 `pages.json` 分包结构示意图。  
> 重点标注：`subpackage_learning` / `subpackage_ai` / `subpackage_exam` / `subpackage_meeting`。

#### 2.3.2 Web 管理端

1. MaxKB 账号/知识库管理与引用式问答调试。  
2. 智能体 Catalog 查看、开关、模型绑定、工具与可观测性。  
3. 七维画像规则可视化。  
4. AI 题库生成、题库管理、手工/随机组卷、预览与历史。  
5. 校园课程与 Python 编程题管理；会议历史与 ASR 配置。

**【图 2-3 截图位】**  
> 请放置：AppWeb 门户导航截图（AI / 题库 / 学习 / 会议相关菜单）。

#### 2.3.3 功能交互逻辑

```
小程序/管理端
    │  REST / SSE / WebSocket
    ▼
Spring Boot（鉴权 · 业务事实 · 编排）
    │  内部令牌 + REST
    ▼
FastAPI AI（Leader / DAG / 模型）
    │  必要时反向查询业务数据
    ▼
MySQL / Redis / MaxKB / 讯飞 ASR / 模型提供商
```

业务事实（画像分数、路径版本、试卷状态、考试尝试）只由 Java 落库；模型输出是候选结果，必须经契约与规则后才能影响用户可见状态。

### 2.4 非功能与边界

| 维度 | 要求 | 本作品做法 |
|---|---|---|
| 交互 | 流式输出、卡片化资源 | SSE 进度事件；资源信封含类型/来源/完整性 |
| 防幻觉 | 资料不足不编造 | MaxKB 检索不足明确提示；资源复审拒绝可重写一次 |
| 安全 | 凭据不进客户端 | JWT、内部令牌、第三方 Key 仅服务端；文档不写真实密钥 |
| 性能 | 合理响应、进度可追踪 | 资源并行度上限 3；工作流可查询与单项重试 |
| 已知限制 | 须诚实披露 | 真实 MaxKB 导出、在线评测/压测、讯飞生产稳定性、Web 细粒度 RBAC 仍有缺口 |

**【表 2-1】** 竞品/方案能力对照（架构能力比较，非统一数据集实验）

| 对比项 | 通用聊天机器人 | 传统教学平台 | 基础知识库问答 | AgentA3 |
|---|---|---|---|---|
| 持续学习画像 | 弱 | 多为静态档案 | 弱 | 七维证据慢更新 |
| 多智能体资源包 | 少 | 少 | 无 | Catalog + 课程 DAG |
| 引用可追溯 | 弱 | 弱 | 中 | hit-test + citations |
| 考试→路径反馈 | 无 | 部分 | 无 | 掌握度/候选/重排 |
| 校园服务一体 | 无 | 部分 | 无 | 小程序增量接入 |

---

## 第3章 原理介绍

> 从原理层面说明系统采用的关键技术机制、算法思路与模型协作方式（不展开硬件选型）。

### 3.1 总体技术路线

系统采用 **“业务治理与模型执行分离”** 的双服务架构：

1. **Java（治理面）**：身份角色、领域状态、画像规则、工作流快照、题库试卷、考试一致性、外部服务编排。  
2. **Python（执行面）**：意图路由、专业智能体运行、课程 typed DAG、模型调用、结构化输出与资源导出。  
3. **双拓扑**：  
   - **校园 Leader 拓扑**：一次请求路由到一个目标智能体或工具；  
   - **课程 Workflow 拓扑**：路径规划 → 多资源并行生成 → 统一复审 → 打包交付。

**【图 3-1 截图位】**  
> 请放置：双拓扑对比示意图（Leader 单目标 vs 课程 DAG）。  
> 可用白板/draw.io 绘制后截图。

### 3.2 七维画像证据协议（慢更新）

**问题**：单次对话或模型判断噪声大，若直接改分会导致画像抖动。  
**原理**：将信号规范化为证据，先入候选池；定时/批量汇总时，按来源可靠性、表达清晰度、重复度、新鲜度、历史一致性加权置信度；正负证据接近则继续等待；融合步长受单次更新上限与边界 clamp 约束。

七维键（封闭基线）：

| Key | 含义 |
|---|---|
| `campus_behavior` | 校园行为 |
| `course_background` | 课程背景 |
| `learning_goal` | 学习目标 |
| `resource_preference` | 资源偏好 |
| `weak_points` | 薄弱点 |
| `learning_progress` | 学习进度 |
| `ability_performance` | 能力表现 |

协议版本：`campus-profile-evidence-v1`。画像总结智能体只读快照解释，**无权直接写分**。

**【图 3-2 截图位】**  
> 请放置：证据状态机图（candidate → applied / 留池 / 拒绝）+ 小程序画像雷达页截图。  
> 文件建议：`fig-3-2-profile-state.png`、`fig-3-2-radar-ui.png`

### 3.3 Leader 路由与 Catalog

**问题**：专业任务输入输出、模型模态、工具白名单各异，不宜在业务代码中硬编码分支。  
**原理**：Catalog 维护智能体元数据与实现包顺序；Leader 按优先级选择路径——指定智能体 → 显式导出 → 能力查询 → 视觉生成规则 → 高置信校园服务快路径 → LLM 规划兜底；Python 课程特定 intent 则切入 `run_learning_workflow`。

**【图 3-3 截图位】**  
> 请放置：Leader 路由决策树 / 时序图；可选再附 AppWeb「智能体 Catalog」页截图。

### 3.4 课程资源 typed DAG

**问题**：个性化资源包需要多角色协作，且单项失败不应毁掉整包。  
**原理**：

1. `learning_path_agent` 生成路径草稿与资源 brief；  
2. 按请求类型并行调度资源智能体（最大并行 3）；  
3. `resource_review_agent` 统一复审；拒绝项允许重写一次；  
4. `resource_package_agent` 打包；终态可为 `completed` 或 `partial`，失败类型可单项重试。

DAG 内资源类型映射：

| 资源类型 | 智能体 |
|---|---|
| `knowledge_note` | `textbook_knowledge_agent` |
| `mind_map` | `diagram_mind_map_agent` |
| `practice_set` | `python_practice_set_agent` |
| `code_lab` | `python_code_lab_agent` |
| `extended_reading` | `extension_reading_agent` |

产品侧“六类资源”= 上述五类 + **独立 PPT 管线**（Presenton / `AppAiPptController`），报告中按此边界表述。

**【图 3-4 截图位】**  
> 请放置：资源生成 SSE 进度界面截图（含 planning / generation / review / package 阶段）+ DAG 流程图。

### 3.5 MaxKB 检索与引用式回答

**问题**：若把“检索”和“回答”混成黑盒，无法审计引用来源。  
**原理**：MaxKB 仅执行 hit-test；Java 抽取 references、裁剪 grounded context；再调用本系统 LLM/Agent 生成 answer，并在响应中带回 citations；资料不足时明确说明，不编造。

**【图 3-5 截图位】**  
> 请放置：管理端知识库问答页——左侧问题、右侧回答 + 引用片段列表。

### 3.6 会议实时 ASR 与会后链

**问题**：会议音频连续、低延迟，REST 不足以承载帧流。  
**原理**：客户端经 Java WebSocket 接入；服务端桥接讯飞实时 ASR；区分 `partial` 与 `final`，仅确认文本进入稳定会议记录；会后顺序调用转写整理、总结、成员分析、资源推荐等会议类智能体。

**【图 3-6 截图位】**  
> 请放置：会议现场页（转写滚动）+ 会后总结结果页。

### 3.7 考试反馈闭环

**问题**：考试结果若不能回流到掌握度与路径，个性化无法闭环。  
**原理**：交卷后对客观题按知识点幂等更新掌握度，生成画像 candidate，触发路径重排并返回版本变化；主观题保持人工边界，不宣称可靠自动评分。

**【图 3-7 截图位】**  
> 请放置：考试结果页 + 交卷前后学习路径版本对比（或掌握度变化）。

---

## 第4章 系统实现

> 从工程实现角度说明四端如何落地第 3 章原理；仅给出核心代码，一般 CRUD/配置省略。

### 4.1 工程结构与协作契约

| 模块 | 关键目录 |
|---|---|
| 小程序 | `mini_program_app/subpackage_learning|ai|exam|meeting`，`api/learning.js` 等 |
| AI 服务 | `ai-servers/app/multi_agents/`，`learning_workflow/` |
| Java | `AppBackend/.../controller|service|websocket` |
| 管理端 | `AppWeb/src/pages/ai|questionBank|learning` |

内部安全：Compose/部署注入同一 `AI_INTERNAL_TOKEN`；Java 请求附带 `X-AI-Internal-Token`；Python 反向调用转发原始 `Authorization`，Java 重新鉴权。

**【图 4-1 截图位】**  
> 请放置：仓库根目录 / 四模块目录树截图，或 IDE 工程结构。

### 4.2 小程序实现要点

#### 4.2.1 学习主线页面

| 页面（分包） | 作用 |
|---|---|
| `subpackage_learning/pythonHome` | Python 学习首页、五轮补问入口 |
| `resourceGenerate` | 资源包 SSE 生成与展示 |
| `learningPath` | 路径开始/完成/重规划 |
| `recommendations` | 精准推荐列表 |
| `knowledgeGraph` | 课程知识图谱 |
| `subpackage_ai/aiConversation` | Leader 对话 |
| `subpackage_ai/profileRadar` | 七维雷达 |
| `subpackage_exam/attempt*` | 在线考试 |
| `subpackage_meeting/meetingLive` | 会议现场与 ASR |

**【图 4-2 截图位】**  
> 请按操作路径连续截图：补问 → 生成资源包 → 路径页 → 推荐页（4 张拼图亦可）。

#### 4.2.2 与后端的关键 API（摘录）

| 能力 | 方法与路径 |
|---|---|
| Leader 对话/流式 | `/api/ai/leader/query`（及 stream） |
| 资源生成 SSE | `/api/app/learning/resources/generate/stream` |
| 路径/推荐 | `/api/app/learning/path`、`/recommendations`、`/path/replan` |
| 画像雷达 | `/api/profile/radar/my` |
| 在线考试 | `/api/app/exam-papers`、`/api/app/exam-attempts` |
| 会议 ASR | WebSocket `/api/meetings/{sessionId}/asr/stream` |

### 4.3 Java：画像证据汇总（核心）

实现类：`UserProfileServiceImpl`。证据达最低置信度后计算正负加权；若相对差小于 0.2 则继续留在候选池；否则融合改分并标记 `applied`。

```java
// AppBackend/.../UserProfileServiceImpl.java（核心逻辑摘录）
private void applyEvidenceBatch(Long userId, String dimensionKey,
                                List<UserProfileEvidence> evidenceList) {
    List<UserProfileEvidence> validEvidence = evidenceList.stream()
            .filter(e -> e.getConfidence() != null
                    && e.getConfidence() >= rule.getMinConfidence())
            .filter(e -> e.getSuggestedDelta() != null && e.getSuggestedDelta() != 0)
            .toList();

    double positiveWeight = validEvidence.stream()
            .filter(e -> e.getSuggestedDelta() > 0)
            .mapToDouble(e -> Math.abs(e.getSuggestedDelta()) * e.getConfidence())
            .sum();
    double negativeWeight = validEvidence.stream()
            .filter(e -> e.getSuggestedDelta() < 0)
            .mapToDouble(e -> Math.abs(e.getSuggestedDelta()) * e.getConfidence())
            .sum();
    double totalWeight = positiveWeight + negativeWeight;
    // 正负接近：不改分，继续候选
    if (totalWeight <= 0
            || Math.abs(positiveWeight - negativeWeight) / totalWeight < 0.2) {
        validEvidence.forEach(e -> e.setReason(
                "本轮画像汇总发现正负证据接近，继续留在候选池等待更多行为"));
        evidenceRepository.saveAll(validEvidence);
        return;
    }
    int direction = positiveWeight >= negativeWeight ? 1 : -1;
    int appliedDelta = calculateFusionDelta(dimension, rule, averageConfidence, requestedDelta);
    // 更新 score / confidence / trend，证据 status -> applied
}
```

置信度五因子加权：来源 0.35 + 表达 0.25 + 重复 0.20 + 新鲜度 0.10 + 历史一致 0.10。

**【图 4-3 截图位】**  
> 请放置：AppWeb「画像规则」页，或数据库中某用户七维分数/证据列表截图（注意脱敏）。

### 4.4 Python：Leader 路由（核心）

```python
# ai-servers/app/multi_agents/leader_agent/agent.py（plan 主路径摘录）
def plan(self, input_text, rag_strategy="", requested_agent=None, ...):
    route_text = str(routing_input_text or input_text or "")
    forced_plan = self._plan_for_requested_agent(requested_agent, rag_strategy)
    if forced_plan:
        return self._finalize_campus_tool_plan(forced_plan, route_text)

    file_export_plan = self._plan_explicit_file_export_request(route_text, callable_catalog)
    if file_export_plan:
        return self._finalize_campus_tool_plan(file_export_plan, route_text)

    # 能力查询 / 视觉生成规则 / 高置信校园服务快路径 ...
    fast_plan = self._plan_high_confidence_service_query(...)
    if fast_plan:
        return self._finalize_campus_tool_plan(fast_plan, route_text)

    # LLM 兜底规划
    return self._finalize_campus_tool_plan(
        self._plan_with_llm(input_text, rag_strategy, chat_service, ...),
        route_text,
    )

def _plan_learning_workflow(self, learning_context):
    if str(context.get("courseKey") or "").strip().lower() != "python":
        return None
    if intent not in {"resource_package", "learning_plan",
                      "weakness_review", "path_replanning"}:
        return None
    return LeaderPlan(
        intent=intent,
        action="run_learning_workflow",
        route_mode="workflow",
        route_reason="Python 课程个性化学习请求进入受控多智能体工作流。",
    )
```

Catalog 由 `ai-servers/app/multi_agents/catalog.py` 的 `AGENT_ORDER` 动态展开（含 Leader、题型、会议、PPT、学习工作流内部智能体、编程辅导等）；报告表述建议为“Catalog 动态维护的专业智能体集合”，避免与历史材料中的固定数量表述冲突。

**【图 4-4 截图位】**  
> 请放置：管理端 RagManage / AgentSettings 列表页，展示已登记智能体。

### 4.5 Python：课程资源 DAG（核心）

```python
# ai-servers/app/learning_workflow/workflow.py（摘录）
RESOURCE_AGENT_BY_TYPE = {
    "knowledge_note": "textbook_knowledge_agent",
    "mind_map": "diagram_mind_map_agent",
    "practice_set": "python_practice_set_agent",
    "code_lab": "python_code_lab_agent",
    "extended_reading": "extension_reading_agent",
}
MANDATORY_RESOURCE_TYPES = frozenset({"knowledge_note", "practice_set", "code_lab"})
MIN_PASSED_RESOURCES = 4
MAX_PARALLELISM = 3

def run_learning_workflow(request, runner=None, event_callback=None):
    plan_payload = runner.run("learning_path_agent", build_plan_input(request), ...)
    plan = _validate_model(LearningPlan, plan_payload, "learning_path_agent")

    jobs = resource_jobs(request, plan)
    drafts_by_type = {}
    with ThreadPoolExecutor(max_workers=MAX_PARALLELISM) as pool:
        future_jobs = {pool.submit(_run_resource_job, ...): job for job in jobs}
        for future in as_completed(future_jobs):
            drafts_by_type[future_jobs[future].resource_type] = future.result()

    # resource_review_agent 复审 → 拒绝项重写一次 → resource_package_agent 打包
    ...
```

Java 侧 `LearningWorkflowServiceImpl` 负责任务登记、SSE 转发给小程序、`partial` 合并与单项重试，保证客户端断线后仍可按工作流标识恢复。

**【图 4-5 截图位】**  
> 请放置：一次真实（或演示环境）资源包结果页——至少展示 3～5 类资源卡片。

### 4.6 Java：MaxKB 引用式问答（核心）

```java
// AppBackend/.../KnowledgeChatServiceImpl.java（摘录）
public KnowledgeChatDTO.ChatResponse chat(ChatRequest request, String authorization) {
    KnowledgeChatDTO.RetrievalResult retrievalResult = retrieve(retrievalRequest);
    List<Reference> references = retrievalResult.getReferences() == null
            ? List.of() : retrievalResult.getReferences();

    LlmChatRequest chatRequest = new LlmChatRequest();
    chatRequest.setAgentName(... DEFAULT_AGENT_NAME ...);
    chatRequest.setPrompt(
        "你是智慧校园知识库问答助手。回答时优先依据提供的知识库片段；"
      + "资料不足时明确说明，不要编造。");
    chatRequest.setInput(buildAgentInput(request.getQuestion(), references));

    LlmChatResponse llmResponse = llmService.chat(chatRequest, authorization);
    response.setAnswer(llmResponse.getAnswer());
    response.setReferences(references); // citations 与回答一并返回
    return response;
}
```

控制器层对 MaxKB 管理与问答执行 ADMIN 校验；学生课程侧另有 `AppLearningKnowledgeController` 的受控检索入口。

### 4.7 会议 ASR 桥接（核心）

实现：`MeetingAsrWebSocketHandler`。讯飞回包 `action=result` 时区分 final/partial，向会议端广播 `asr_result`，并维护可展示的 transcript。

```java
// MeetingAsrWebSocketHandler 内部桥接逻辑（摘录）
if ("result".equals(action)) {
    boolean isFinal = isFinalResult(dataNode);
    if (isFinal) {
        finalSegments.put(resolveSegmentId(dataNode), text);
        partialText.setLength(0);
    } else {
        partialText.setLength(0);
        partialText.append(text);
    }
    sendClient(Map.of(
            "type", "asr_result",
            "text", text,
            "isFinal", isFinal,
            "transcript", isFinal ? buildTranscript()
                                 : buildTranscriptWithPartial()
    ));
}
```

**【图 4-6 截图位】**  
> 请放置：会议 ASR 实时转写界面；若无真实讯飞环境，可截配置页并在图注标明“依赖外部 ASR，演示环境需注入凭据”。

### 4.8 题库、组卷与在线考试

| 环节 | 关键实现 |
|---|---|
| AI 出题 | `QuestionGenerationController` + 题型智能体（单选/多选/判断/填空/简答等） |
| 题库/组卷 | `ExamQuestionController`、`ExamPaperController`、预览一次性证明 |
| App 考试 | `AppExamController` / `AppExamServiceImpl`：快照、自动保存、交卷、客观评分 |
| 反馈 | 交卷后掌握度 + 画像 candidate + 路径重排（幂等） |

**【图 4-7 截图位】**  
> 请放置：管理端 AI 出题页、组卷预览、小程序作答页、成绩/反馈页（选 2～4 张）。

### 4.9 管理端（AppWeb）实现要点

| 路由/页面 | 用途 |
|---|---|
| `/ai/knowledge`、`/admin/knowledge-chat` | MaxKB 管理与引用问答 |
| `/ai/rag/agents`、`/ai/agent-settings` | Catalog 与智能体开关 |
| `/ai/profile-rules` | 画像规则 |
| `/question-bank/*` | 题库生成、组卷、历史 |
| `/learning/courses`、`/learning/python-problems` | 课程与编程题 |
| `/meeting/*` | 会议历史与语音模型配置 |

**【图 4-8 截图位】**  
> 请放置：管理端 2～3 张治理页拼图（知识库 + 题库 + 智能体）。

---

## 第5章 测试分析

### 5.1 测试方案

采用“需求可追踪、核心边界优先、结果可复现、缺口明确披露”原则。区分三类结论：

1. **源码存在** ≠ 测试通过；  
2. **离线自动化通过** ≠ 真实外部服务通过；  
3. **契约/静态配置通过** ≠ 容器已实启或在线压测完成。

测试层次覆盖：Java 单测/切片、Python 工作流与路由、AppWeb 构建与用例、提交契约（Compose 静态、评测计划结构）。小程序主线以页面与 API 联调及既有回归为准。

### 5.2 测试环境（填写实测值）

| 类别 | 建议配置 | 本队实测（请补） |
|---|---|---|
| OS | Windows 10 / Linux | |
| JDK | 21 | |
| Python | 3.11+（uv） | |
| Node | 20+ | |
| MySQL / Redis | Docker Compose | |
| 浏览器 | Chrome 最新 | |
| 小程序 | 微信开发者工具 / 真机 | |

**【图 5-1 截图位】**  
> 请放置：`quality-gate` 或各模块测试命令通过的终端输出截图。

### 5.3 功能性测试用例（摘要）

| 编号 | 模块 | 步骤要点 | 预期 | 结果栏 |
|---|---|---|---|---|
| FT-01 | 登录分端 | App 用学生账号；Web 用管理员 | 角色白名单生效 | 【 】 |
| FT-02 | Leader 对话 | 发起校园类问题，观察 SSE | 有增量与终态 | 【 】 |
| FT-03 | 画像补问 | 完成五轮问题 | 证据入池，雷达可查 | 【 】 |
| FT-04 | 资源包 | 请求多类资源 | 出现进度事件；允许 partial | 【 】 |
| FT-05 | 路径推荐 | 查看路径并完成一项 | 状态变更，推荐更新 | 【 】 |
| FT-06 | 知识问答 | 管理端提问有资料/无资料 | 有 citations 或资料不足提示 | 【 】 |
| FT-07 | 出题组卷 | 生成题目并组卷预览 | JSON 合法，预览一致 | 【 】 |
| FT-08 | 在线考试 | 作答交卷 | 客观分正确，路径/掌握度变化 | 【 】 |
| FT-09 | 会议 ASR | 接入音频 | partial/final 区分，确认文本可存 | 【 】 |
| FT-10 | 权限 | 非 ADMIN 调 MaxKB | 拒绝 | 【 】 |

**【图 5-2 截图位】**  
> 请放置：上述关键用例的界面证据（每用例 1 张，可附录）。

### 5.4 离线质量门禁参考记录

仓库文档曾记录完整离线门禁（未访问外网模型/MaxKB）示例：Java 数百项用例 0 failure、Python 二百余项通过、AppWeb 测试与生产构建通过。**提交前须在最终 SHA 重跑并更新本节数字与截图**，本节不把历史数字写成最终成绩。

### 5.5 稳定性与边界结论

| 项目 | 结论口径 |
|---|---|
| 画像冲突门禁、工作流 partial/重试、考试反馈幂等 | 有自动化边界测试支撑 |
| 真实模型质量、MaxKB 召回率、ASR 准确率 | 未在固定公开集上给出对外指标，演示时展示样例即可 |
| 5×50 压测 / 30 题在线事实评测 | 计划已冻结；若未跑完，状态保持 `not_run`，不得虚构通过 |
| 真机弱网、WebSocket Origin 加固、密码哈希升级 | 列为已知限制与后续项 |

---

## 第6章 作品总结

### 6.1 作品特色与创新点

1. **证据驱动的七维画像慢更新**：候选池 + 置信度五因子 + 正负冲突门禁，避免模型一次判断直接改写学生画像。  
2. **双拓扑多智能体**：校园场景 Leader 单目标路由；Python 课程 typed DAG 并行生成与统一复审；Catalog 使能力扩展不必改前端业务分支。  
3. **检索与生成职责分离**：MaxKB hit-test 只提供证据片段，本系统 Agent 生成回答并返回 citations，资料不足可拒答。  
4. **资源可信交付**：复审、真实导出、`partial` 与单项重试；资源信封携带类型、来源与完整性信息。  
5. **学—练—测闭环**：客观考试结果回流掌握度与路径版本，形成可演示的个性化闭环。  
6. **校园能力增量接入**：学习与会议以分包形式接入，不拆除既有校园服务入口。  
7. **管理治理一体**：AppWeb 覆盖知识库、智能体、题库组卷与画像规则，便于教学侧运维。

### 6.2 作品展望

1. 完成真实 Python 课程知识库合法导出、空环境恢复与引用映射验收。  
2. 在真实 endpoint 上完成事实评测与负载测试，保留原始报告。  
3. 强化 Web 端统一 RBAC、会议 WebSocket Origin/限流审计，推进密码自适应哈希。  
4. 开放面向学生/教师的受控知识问答入口，补齐教师 Web 治理权限模型。  
5. 在更多课程上复用 DAG 与画像协议，扩展资源类型与辅导形态（仍保持业务事实由 Java 治理）。

### 6.3 结束语

AgentA3 的价值在于把大模型能力放进可审计的业务状态机：谁能调用、证据如何生效、资源如何交付、考试如何反馈，都有明确工程边界。后续工作重点从“功能堆叠”转向“真实知识、真实评测与生产加固”，使作品从可演示系统进一步成为可核验、可运维的校园学习智能体平台。

---

## 附图清单（方便你按序补图）

| 编号 | 建议截取内容 | 建议文件名 |
|---|---|---|
| 图 1-1 | 四端架构总览 | `fig-1-1-architecture.png` |
| 图 1-2 | 小程序首页 + 管理端门户 | `fig-1-2-*.png` |
| 图 2-1 | 角色用例图 | `fig-2-1-usecase.png` |
| 图 2-2 | 小程序功能/分包模块图 | `fig-2-2-modules.png` |
| 图 2-3 | 管理端导航 | `fig-2-3-admin-nav.png` |
| 图 3-1 | 双拓扑原理图 | `fig-3-1-dual-topology.png` |
| 图 3-2 | 画像状态机 + 雷达 UI | `fig-3-2-*.png` |
| 图 3-3 | Leader 路由图 + Catalog 页 | `fig-3-3-*.png` |
| 图 3-4 | DAG + SSE 进度 | `fig-3-4-*.png` |
| 图 3-5 | 知识问答含引用 | `fig-3-5-kb-chat.png` |
| 图 3-6 | 会议转写/会后 | `fig-3-6-*.png` |
| 图 3-7 | 考试反馈闭环 | `fig-3-7-exam-feedback.png` |
| 图 4-1 | 工程目录 | `fig-4-1-repo.png` |
| 图 4-2 | 学习主线四连图 | `fig-4-2-learning-flow.png` |
| 图 4-3 | 画像规则/数据 | `fig-4-3-profile.png` |
| 图 4-4 | 智能体列表 | `fig-4-4-agents.png` |
| 图 4-5 | 资源包结果 | `fig-4-5-resources.png` |
| 图 4-6 | 会议 ASR | `fig-4-6-asr.png` |
| 图 4-7 | 题库/考试 | `fig-4-7-exam.png` |
| 图 4-8 | 管理端治理拼图 | `fig-4-8-admin.png` |
| 图 5-1 | 测试终端通过 | `fig-5-1-tests.png` |
| 图 5-2 | 功能测试界面证据 | `fig-5-2-ft-*.png` |

> 截图请统一放到：`docs/submission/figures/`。你截好后告诉我文件名，我可以帮你嵌进 Word 并微调图注排版。

---

*（参考文献按本次要求不写。）*
