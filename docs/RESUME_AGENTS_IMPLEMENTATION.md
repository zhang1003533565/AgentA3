# AI 简历生成与优化智能体 - 完整实现方案

## 📋 概述

本文档描述了在智慧校园项目中新增的两个 AI 简历相关智能体的实现细节：

1. **resume_create_agent**：AI 一键改简历智能体
2. **resume_edit_agent**：AI 生成简历智能体

这两个智能体都使用本地 DeepSeek 大模型，通过对话方式帮助用户生成或优化简历内容。

---

## 🎯 核心功能

### resume_create_agent（AI 生成简历）
- 通过多轮对话收集用户个人信息、教育背景、工作经历等
- 分步骤引导用户补充简历内容
- 最终输出结构化 JSON 数据供 Java 后端转换为 DOCX/PDF

### resume_edit_agent（AI 一键改简历）
- 接收用户上传的已解析简历内容
- 分析现有简历问题并提供优化建议
- 逐段修改完善，使简历更专业、更有竞争力

---

## 📁 文件结构

```
ai-servers/app/multi_agents/
├── resume_create_agent/
│   ├── __init__.py           # 模块初始化文件
│   ├── README.md             # 接口定义和输入输出格式说明
│   └── prompt.md            # 系统提示词（核心配置）
│
└── resume_edit_agent/
    ├── __init__.py           # 模块初始化文件
    ├── README.md             # 接口定义和输入输出格式说明
    └── prompt.md            # 系统提示词（核心配置）
```

---

## 🧠 System Prompt 设计要点

### resume_create_agent 提示词要点
1. **信息收集流程**：按顺序询问个人信息→教育背景→工作经历→技能→项目经验
2. **量化原则**：始终推动用户提供量化成果数据
3. **输出规范**：必须输出符合特定 JSON 格式的简历数据
4. **对话策略**：友好专业、分步引导、主动建议

### resume_edit_agent 提示词要点
1. **问题诊断**：识别简历中常见问题（表述笼统、缺少量化等）
2. **改写技巧**：动词专业化升级、STAR 法则应用、行业关键词植入
3. **针对性优化**：根据目标职位调整简历内容和关键词
4. **输出格式**：提供原始内容 + 优化建议 + 最终版本

---

## 🔧 数据库部署步骤

### Step 1: 运行绑定 SQL

```bash
mysql -u root -p < e:/项目/github/AgentA3/AppBackend/src/main/resources/bind-resume-agents.sql
```

或在 MySQL 客户端中执行：

```sql
USE smart-campus;
source e:/项目/github/AgentA3/AppBackend/src/main/resources/bind-resume-agents.sql;
```

### Step 2: 验证绑定结果

```sql
-- 查看已绑定的智能体
SELECT * FROM agent_model_bind WHERE agent_id IN ('resume_create_agent', 'resume_edit_agent');

-- 查看详细信息（包括模型配置）
SELECT 
    amb.agent_id,
    amb.model_config_id,
    amc.provider,
    amc.model_name,
    amc.status,
    amb.create_time
FROM agent_model_bind amb
LEFT JOIN ai_model_config amc ON amb.model_config_id = amc.id
WHERE amb.agent_id IN ('resume_create_agent', 'resume_edit_agent');
```

预期结果应该显示两条记录：
| agent_id | model_config_id | provider | model_name | status |
|----------|----------------|----------|------------|--------|
| resume_create_agent | 1 | deepseek | deepseek-chat | 1 |
| resume_edit_agent | 1 | deepseek | deepseek-chat | 1 |

---

## 🔗 Java 后端对接要点

### 1. Agent ID 注册
两个智能体的代码分别是：
- `resume_create_agent` （AI 生成简历）
- `resume_edit_agent` （AI 一键改简历）

### 2. 调用流程

#### AI 生成简历流程：
```javascript
// Java 后端调用 Python AI 服务
POST /api/v1/chat/completions
Content-Type: application/json
Authorization: Bearer {apiKey}

{
  "agent_id": "resume_create_agent",
  "user_message": "帮我写一份简历",
  "system_prompt": "你是智慧校园的【AI 生成简历】智能体...", // prompt.md 中的内容
  "model_name": "deepseek-chat",
  "context": [...], // 历史对话记录
  "temperature": 0.7
}
```

#### AI 改简历流程：
```javascript
POST /api/v1/chat/completions
Content-Type: application/json
Authorization: Bearer {apiKey}

{
  "agent_id": "resume_edit_agent",
  "user_message": "帮我看一下这份简历哪里需要优化",
  "system_prompt": "你是智慧校园的【AI 一键改简历】智能体...", // prompt.md 中的内容
  "uploaded_resume": { // Java 前端上传并解析后的 JSON
    "personal_info": {...},
    "work_experience": [...]
  },
  "target_position": "Java 高级开发工程师", // 可选
  "model_name": "deepseek-chat"
}
```

### 3. 响应处理

Java 后端应能够解析并存储 AI 返回的以下字段：

#### resume_create_agent 响应：
```json
{
  "intent": "gather_info|confirm|generate_resume",
  "answer": "你好！请先告诉我你的姓名是什么？",
  "collected_data": {...}, // 如果已生成完整简历
  "next_step": "请提供你的求职意向职位",
  "action": "continue_gathering|finalizing|exporting"
}
```

#### resume_edit_agent 响应：
```json
{
  "intent": "analyze|optimize_section|export_optimized",
  "answer": "我发现了几个可以优化的地方...",
  "optimized_data": {...}, // 如果已优化完成
  "optimization_suggestions": [
    {
      "section": "work_experience",
      "current": "负责公司后台开发",
      "suggested": "主导公司 ERP 系统后端架构设计，使用 Spring Boot 技术栈",
      "reason": "原表述过于笼统...",
      "priority": "high"
    }
  ],
  "action": "suggesting|editing|exporting"
}
```

---

## 🖥️ 前端集成要点

### 管理后台展示

在 AI 智能体设置页面，应该能下拉选择这两个新增的智能体：

1. **智能体列表**：
   - resume_create_agent - AI 生成简历
   - resume_edit_agent - AI 一键改简历
   - （其他已有的智能体...）

2. **绑定操作**：
   - 为每个智能体选择绑定的 AI 模型配置
   - 当前默认都已绑定到 DeepSeek 模型（ID=1）

### 用户界面

#### AI 生成简历页面：
1. 聊天对话框
2. 进度指示器（已完成的信息部分）
3. 导出按钮（完成后显示）
4. 支持断点续传（保存当前收集的 resume_snapshot）

#### AI 改简历页面：
1. 简历上传入口（PDF/Word）
2. 解析后的简历预览区域
3. 优化建议展示区（高亮需要修改的部分）
4. 逐段确认/跳过控制
5. 导出优化后简历按钮

---

## ✅ 验收标准

### 功能完整性
- ✅ 两个智能体目录已创建，包含必要的配置文件
- ✅ prompt.md 中的提示词详细且可执行
- ✅ 数据库绑定记录正确插入
- ✅ Java 后端能通过 API 调用 Python AI 服务
- ✅ 管理后台能加载和配置这两个智能体
- ✅ 前端页面能正常展示和使用

### 质量要求
- ✅ System Prompt 覆盖了各种使用场景
- ✅ 输出格式符合 JSON Schema 规范
- ✅ 对话流程自然流畅
- ✅ 简历内容专业且有竞争力

---

## 🚀 后续扩展

### 短期优化
1. 添加更多示例问答对优化对话效果
2. 支持更多语言（英语简历生成）
3. 增加简历评分功能（HR 视角评估）

### 长期规划
1. 基于真实招聘 JD 进行针对性优化
2. 支持不同行业的简历模板（技术/市场/销售等）
3. 结合用户画像数据智能推荐简历侧重点
4. 集成面试模拟功能（根据简历生成面试题）

---

**最后更新时间**: 2026-08-24  
**作者**: Campus AI Team
