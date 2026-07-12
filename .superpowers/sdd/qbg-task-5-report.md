# Task 5 实施报告：在智能体设置中管理五种题型映射

## 结果

- 新增 `QUESTION_GENERATION_AGENT_PREFIX`、固定五题型 `QUESTION_TYPE_OPTIONS` 和配置解析函数 `buildQuestionGenerationAgentMappings(configRows)`。
- 智能体设置查询范围加入 `ai.question-generation.agent.`。
- 新增题库生成智能体映射卡片，固定展示单选题、多选题、判断题、填空题、简答题。
- 智能体下拉选项完全由 `getRagAgents()` 返回的 `agents` 生成，没有写死智能体名称。
- 每行展示所选智能体的启用状态和模型绑定状态，并通过独立按钮保存对应 `system_config`。

## TDD 证据

1. 先创建 `agentConfig.test.js`，执行 `node --test src/pages/ai/agentConfig.test.js`。
2. 测试因 `agentConfig.js` 尚未导出 `QUESTION_GENERATION_AGENT_PREFIX` 而失败，确认 RED。
3. 实现前缀、五题型选项与解析函数后重跑，2/2 通过，确认 GREEN。

## 验证

- `node --test`：14/14 通过。
- `npx eslint src/pages/ai/agentConfig.js src/pages/ai/AgentSettings/AgentSettings.jsx`：通过，无错误。
- `npm run build`：通过；Vite 保留既有的大 chunk 警告。
- `git diff --check`：通过。

## 范围说明

- 未修改题目生成页面。
- 未新增依赖。
- 保留工作区中 Task 4 报告的既有未提交修改，不纳入本任务提交。
