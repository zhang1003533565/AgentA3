# AI 模型配置与智能体绑定功能说明

## 概述

本文档描述了基于 DeepSeek 大模型的 AI 对话转发功能的实现细节，包括模型配置管理、智能体绑定和对话转发接口。

---

## 一、数据库表结构

### 1. ai_model_config（AI 模型配置表）

存储 DeepSeek 等大模型的基本配置信息。

| 字段名 | 类型 | 说明 | 示例值 |
|--------|------|------|--------|
| id | bigint(20) | 主键 ID | 1 |
| provider | varchar(100) | 供应商名称 | deepseek |
| base_url | varchar(500) | API 接口地址 | http://127.0.0.1:8081 |
| api_key | text | API 密钥（加密存储） | sk-xxxxx |
| model_name | varchar(100) | 模型标识 | deepseek-chat |
| status | int(11) | 启用状态：1-启用 0-禁用 | 1 |
| create_time | datetime | 创建时间 | 2026-08-24 10:00:00 |
| update_time | datetime | 更新时间 | 2026-08-24 10:30:00 |

### 2. agent_model_bind（智能体 - 模型绑定表）

将智能体与模型配置进行关联绑定。

| 字段名 | 类型 | 说明 | 示例值 |
|--------|------|------|--------|
| id | bigint(20) | 主键 ID | 1 |
| agent_id | varchar(50) | 智能体 ID | resume-editor |
| model_config_id | bigint(20) | AI 模型配置 ID | 1 |
| create_time | datetime | 创建时间 | 2026-08-24 10:00:00 |
| update_time | datetime | 更新时间 | 2026-08-24 10:00:00 |

**重要约束**: `agent_id` 唯一索引，确保一个智能体只能绑定一套模型配置。

---

## 二、后端 API 接口

### A. 管理后台接口（需要管理员权限）

#### 1. 新增模型配置
```http
POST /api/admin/ai/model-config
Content-Type: application/json
Authorization: Bearer <admin_token>

{
  "provider": "deepseek",
  "baseUrl": "http://127.0.0.1:8081",
  "apiKey": "sk-your-deepseek-api-key-here",
  "modelName": "deepseek-chat",
  "status": 1
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "provider": "deepseek",
    "baseUrl": "http://127.0.0.1:8081",
    "apiKeyMasked": "sk-****xx****98",  // 脱敏显示
    "modelName": "deepseek-chat",
    "status": 1,
    "createTime": "2026-08-24T10:00:00",
    "updateTime": "2026-08-24T10:00:00"
  }
}
```

#### 2. 更新模型配置
```http
PUT /api/admin/ai/model-config/{id}
Content-Type: application/json
Authorization: Bearer <admin_token>

{
  "provider": "deepseek",
  "baseUrl": "http://new-url:8081",
  "apiKey": "sk-new-api-key",
  "modelName": "deepseek-chat",
  "status": 1
}
```

#### 3. 获取所有模型配置列表
```http
GET /api/admin/ai/model-config/list
Authorization: Bearer <admin_token>
```

#### 4. 获取单个配置详情
```http
GET /api/admin/ai/model-config/{id}
Authorization: Bearer <admin_token>
```

#### 5. 获取启用的配置列表（用于下拉选择）
```http
GET /api/admin/ai/model-config/enabled
Authorization: Bearer <admin_token>
```

---

### B. 智能体绑定管理接口

#### 1. 绑定智能体到模型配置
```http
POST /api/admin/ai/bind/bind
Content-Type: application/json
Authorization: Bearer <admin_token>

{
  "agentId": "resume-editor",
  "modelConfigId": 1
}
```

**说明**: 
- 一个智能体只能绑定一套模型（由唯一约束保证）
- 如果已存在绑定，会返回错误提示

#### 2. 根据 agentId 获取绑定信息和完整模型配置
```http
GET /api/admin/ai/bind/agent/{agentId}
Authorization: Bearer <admin_token>

示例：
GET /api/admin/ai/bind/agent/resume-editor
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "bindInfo": {
      "id": 1,
      "agentId": "resume-editor",
      "modelConfigId": 1,
      "createTime": "2026-08-24T10:00:00"
    },
    "modelConfig": {
      "id": 1,
      "provider": "deepseek",
      "baseUrl": "http://127.0.0.1:8081",
      "apiKey": "sk-xxxxx",  // 管理后台可查看所有密钥
      "modelName": "deepseek-chat",
      "status": 1
    }
  }
}
```

#### 3. 解除智能体绑定
```http
DELETE /api/admin/ai/bind/unbind/{agentId}
Authorization: Bearer <admin_token>

示例：
DELETE /api/admin/ai/bind/unbind/resume-editor
```

---

### C. 公共对话转发接口（前端调用）

#### 1. 非流式对话接口
```http
POST /api/ai/chat/agent/{agentId}
Content-Type: application/json

{
  "agentId": "resume-editor",
  "message": "帮我优化这份简历"
}
```

**响应示例**:
```json
{
  "success": true,
  "code": 200,
  "message": "AI 回复生成成功",
  "data": {
    "success": true,
    "content": "您好！您的简历整体结构清晰，建议从以下几个方面进行优化...",
    "source": "python-llm"
  }
}
```

#### 2. 流式对话接口（SSE）
```http
POST /api/ai/chat/agent/{agentId}/stream
Content-Type: application/json
Accept: text/event-stream

{
  "agentId": "resume-generator",
  "message": "生成一份 Java 工程师的简历模板"
}
```

**SSE 流式响应格式**:
```
event: start
data: {"status":"starting"}

event: token
data: {"content":"您好！这是为您生成的 Java 工程师简历模板..."}

event: token
data: {"content":"...继续生成的内容..."}

event: end
data: {"status":"completed"}
```

---

## 三、System Prompt 配置

根据智能体类型，Java 后端会自动组装不同的 System Prompt：

### 1. resume-editor（AI 一键改简历）
```text
你是一位专业的简历修改助手，帮助用户优化简历内容，使其更符合招聘要求。
```

### 2. resume-generator（AI 生成简历）
```text
你是一位简历生成专家，根据用户提供的个人信息和工作经历，生成专业格式的简历内容。
```

### 3. 其他智能体
```text
你是一位有帮助的助手。
```

---

## 四、Python LLM 服务调用

### 请求格式
Java 后端会将以下参数封装后发送到 Python 服务：

```json
{
  "agent_id": "resume-editor",
  "user_message": "帮我优化这份简历",
  "system_prompt": "你是一位专业的简历修改助手，帮助用户优化简历内容，使其更符合招聘要求。",
  "model_name": "deepseek-chat",
  "temperature": 0.7,
  "max_tokens": 2000,
  "context": [...]  // 可选，历史对话上下文
}
```

### Python 服务地址
- URL: `http://127.0.0.1:8081/api/v1/chat/completions`
- Header: `Authorization: Bearer <api_key>`

---

## 五、部署步骤

### Step 1: 创建数据库表

```bash
# 在 MySQL 中执行
mysql -u root -p < e:/项目/github/AgentA3/AppBackend/src/main/resources/ai-agents-setup.sql
```

或者依次执行：
```sql
USE smart-campus;
source e:/项目/github/AgentA3/AppBackend/src/main/resources/ai-model-config.sql;
source e:/项目/github/AgentA3/AppBackend/src/main/resources/agent-model-bind.sql;
```

### Step 2: 重启后端服务

```powershell
cd e:\项目\github\AgentA3\AppBackend
# 停止当前运行的 Spring Boot 服务
# 然后重新启动
./start-smart-campus.ps1
```

### Step 3: 启动 Python AI 服务（如果未启动）

```powershell
cd e:\项目\github\AgentA3\ai-servers
py -3.11 -m uvicorn app.main:app --host 127.0.0.1 --port 8081
```

### Step 4: 通过管理后台配置模型

1. 登录管理后台（账号：`test_admin_20260821` / `admin123`）
2. 进入"AI 模型配置管理"页面
3. 添加 DeepSeek 配置：
   - Provider: deepseek
   - Base URL: http://127.0.0.1:8081
   - API Key: 填入你的 DeepSeek API Key
   - Model Name: deepseek-chat
   - Status: 启用 (1)

### Step 5: 绑定智能体

1. 进入"智能体 - 模型绑定管理"页面
2. 为两个智能体分别绑定模型配置：
   - Agent ID: `resume-editor` → 绑定刚创建的 DeepSeek 配置
   - Agent ID: `resume-generator` → 绑定同一个或不同的配置

### Step 6: 测试对话接口

使用 Postman 或 curl 测试：

```bash
# 测试非流式对话
curl -X POST "http://localhost:8080/api/ai/chat/agent/resume-editor" \
  -H "Content-Type: application/json" \
  -d '{"message":"如何优化简历技能部分？"}' \
  -H "Authorization: Bearer <user_token>"

# 测试流式对话（SSE）
curl -X POST "http://localhost:8080/api/ai/chat/agent/resume-generator/stream" \
  -H "Content-Type: application/json" \
  -d '{"message":"生成一份前端开发的简历"}' \
  -H "Authorization: Bearer <user_token>"
```

---

## 六、安全说明

### 1. API 密钥保护
- **前端不暴露密钥**: 所有 API Key 只存储在 Java 后端数据库中
- **加密存储**: API Key 使用 `EncryptedStringConverter` 进行加密存储
- **脱敏展示**: 前端和管理后台显示时自动脱敏（如：`sk-****xx****98`）
- **Java 内部可解密**: Service 层可以获取完整的明文密钥用于调用 Python 服务

### 2. 权限控制
- 管理后台接口（`/api/admin/*`）需要管理员权限
- 前端调用对话接口（`/api/ai/chat/*`）需要用户登录态

### 3. 业务隔离
- Python 服务只负责调用 DeepSeek 大模型
- 提示词（System Prompt）由 Java 后端统一组装和控制
- 简历相关业务逻辑完全在 Java 后端实现

---

## 七、技术要点

### 1. 使用的 HTTP 客户端
Java 后端使用标准库 `java.net.http.HttpClient` 调用 Python 服务，无需额外依赖。

### 2. 超时控制
- 非流式请求：默认无超时限制
- 流式请求：长时间等待

### 3. 异常处理
- 数据库连接失败：抛出 BusinessException
- 配置为空：检查并返回友好提示
- API 调用超时：HttpClient 默认超时机制
- 密钥失效：HTTP 401/403 错误捕获

### 4. 流式响应（SSE）
- 使用 Spring 的 `SseEmitter` 实现 Server-Sent Events
- 异步线程调用 Python 服务
- 实时推送 Token 到前端

---

## 八、后续扩展

### 1. 支持多个模型提供商
可以通过增加 `ai_model_config` 记录来支持 OpenAI、Google Gemini 等其他大模型。

### 2. 模型配置热加载
可以引入 Redis 缓存配置，避免每次对话都查询数据库。

### 3. 对话日志记录
可以考虑记录所有对话内容到日志表，便于后续分析和优化。

### 4. 负载均衡
如果有多个 Python 服务实例，可以实现简单的负载均衡策略。

---

**最后更新时间**: 2026-08-24
