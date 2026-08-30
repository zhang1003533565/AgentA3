# AI 通用对话接口使用文档

## 概述

实现了基于 DeepSeek 大模型的通用对话接口，自动从数据库 `smart-campus.ai_model_config`表读取启用的配置，提供HTTP接口供Java后端转发调用。

**重要约束：**
- ✅ 仅实现通用 AI 对话底层调用，不涉及简历业务逻辑
- ✅ 不修改任何现有 Java 后端代码和前端代码
- ✅ 提供 RESTful HTTP 接口，方便转发调用

---

## 文件列表

### 后端新增文件
1. **Service**: `AppBackend/src/main/java/com/example/appbackend/service/GenericAiChatService.java`
2. **Controller**: `AppBackend/src/main/java/com/example/appbackend/controller/GenericAiChatController.java`

---

## API 接口说明

### 基础 URL
```
http://localhost:8080/api/ai/chat
```

### 1. 单轮对话接口（最简单）
```
POST /api/ai/chat/chat
```

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userMessage | String | 是 | 用户输入的消息 |

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "content": "你好！有什么我可以帮助你的吗？",
    "success": true,
    "message": "AI 回复生成成功",
    "responseTimeMs": 2345,
    "estimatedTokens": 156
  }
}
```

---

### 2. 带 System Prompt 对话
```
POST /api/ai/chat/chat-with-prompt
```

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userMessage | String | 是 | 用户消息 |
| systemPrompt | String | 否 | System 角色提示词（默认："你是一个有帮助的助手。"）|

**请求示例：**
```bash
curl -X POST "http://localhost:8080/api/ai/chat/chat-with-prompt?userMessage=如何用 Python 读取 Excel 文件&systemPrompt=你是一位资深的数据分析师，擅长用通俗易懂的语言解释技术问题。"
```

---

### 3. 多轮对话（带上下文）
```
POST /api/ai/chat/chat-with-context
```

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userMessage | String | 是 | 当前用户消息 |
| contextMessages | String | 否 | JSON 格式的历史消息数组（可选）|

---

### 4. 健康检查接口
```
GET /api/ai/chat/health
```

**用途：** 验证 AI 服务是否正常、配置是否可用

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": "AI 对话服务正常，已启用 DeepSeek 配置"
}
```

---

### 5. 快速测试接口
```
GET /api/ai/chat/test?message=你好
```

**参数：** message（默认值："你好"）

---

## 数据库配置要求

### 步骤 1: 创建配置表
```sql
USE smart-campus;
source e:/项目/github/AgentA3/AppBackend/src/main/resources/ai-model-config.sql;
source e:/项目/github/AgentA3/AppBackend/src/main/resources/init-ai-model-config.sql;
```

### 步骤 2: 在管理后台配置 DeepSeek 信息
访问 AppWeb 管理员后台 → AI 模块 → DeepSeek 模型配置

**必需填写：**
- API 接口地址：`https://api.deepseek.com`
- API Key：您的 DeepSeek API 密钥
- 模型标识：`deepseek-chat`
- 启用状态：**必须设为启用（开关打开）**

### 步骤 3: 重启后端服务
服务启动后会自动扫描实体类并创建表结构。

---

## 异常处理机制

### 支持的错误场景
1. **数据库连接失败**
   - 错误码：500
   - 错误信息：`数据库连接异常：xxx`

2. **配置未启用**
   - 错误码：500
   - 错误信息：`AI 模型配置未启用，请先在管理后台启用`

3. **API Key 无效**
   - 错误码：403
   - 错误信息：`API Key 无效或已过期，请在管理后台重新配置`

4. **调用超时**
   - 错误码：503
   - 错误信息：`AI 接口调用超时（请检查网络连接或稍后再试）`

5. **配额不足/权限错误**
   - 错误码：403
   - 错误信息：`API Key 权限不足或配额已用完`

6. **服务商内部错误**
   - 错误码：500
   - 错误信息：`AI 服务商内部错误，请稍后重试`

---

## 超时控制

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 全局超时限制 | 30 秒 | 可配置 `timeoutSeconds` 参数覆盖 |
| DeepSeek API 超时 | 10 秒 | 独立配置 |
| 响应体获取 | 30 秒 | 完整响应接收时间 |

**自定义超时：** 可通过 `GenericAiChatService.ChatRequest` 的 `timeoutSeconds` 属性设置。

---

## 集成方式

### 方式 1：直接 HTTP 调用（推荐）
其他 Java 服务通过 HTTP 接口调用即可：

```java
// 使用 RestTemplate 或 WebClient
String response = webClient.post()
    .uri("http://localhost:8080/api/ai/chat/chat?userMessage=如何写一个 Spring Boot 项目")
    .retrieve()
    .bodyToMono(String.class)
    .block();
```

### 方式 2：Spring Cloud Feign 客户端
```java
@FeignClient(name = "ai-service", url = "http://localhost:8080")
public interface AiChatClient {
    @PostMapping("/api/ai/chat/chat")
    Result<ChatResponse> chat(@RequestParam String userMessage);
}
```

---

## 技术栈依赖

无需额外依赖，使用项目已有的：
- Spring WebFlux (`WebClient`)
- Spring Data JPA (`AiModelConfigRepository`)
- EncryptedStringConverter（加密解密）
- Lombok
- Jackson (JSON 解析)

---

## 性能优化建议

1. **启用 Redis 缓存配置**：避免频繁查询数据库
2. **添加连接池**：针对 `WebClient` 配置连接池
3. **异步响应**：考虑添加流式响应支持（stream=true）
4. **批量处理**：对于大量请求，可添加队列缓冲

---

## 后续扩展方向

1. **支持更多模型供应商**：OpenAI、Azure、阿里通义千问等
2. **支持流式响应**：添加 SSE Server-Sent Events 支持
3. **添加 Token 统计**：精确计算输入/输出 Token 数
4. **对话历史记录**：持久化到数据库以便追溯
5. **敏感词过滤**：内置内容安全审核
6. **价格计费**：按 Token 数量计费功能

---

## 常见问题

### Q: 为什么调用时提示"未找到 AI 模型配置"？
A: 请确保：
1. 已在数据库中创建 `ai_model_config` 表
2. 已插入初始记录（执行 `init-ai-model-config.sql`）
3. 已在管理后台启用该配置（status=1）

### Q: API Key 一直报错？
A: 检查：
1. API Key 是否正确复制
2. 是否包含前缀（如 `sk-`）
3. 是否在该账户中购买过额度

### Q: 响应很慢怎么办？
A: 可能的原因：
1. 网络延迟（DeepSeek 在美国）
2. 数据库查询慢（考虑加索引）
3. AI 服务繁忙（建议高峰期限流）

---

## 联系方式

如有问题，请联系开发者或查看：
- [AI_MODEL_CONFIG_INTEGRATION.md](./AI_MODEL_CONFIG_INTEGRATION.md)
- [GenericAiChatService.java](../src/main/java/com/example/appbackend/service/GenericAiChatService.java)
- [GenericAiChatController.java](../src/main/java/com/example/appbackend/controller/GenericAiChatController.java)

---

**最后更新**: 2026-08-21  
**版本**: v1.0  
**作者**: Qoder AI Assistant
