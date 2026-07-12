# Task 7 实施报告

## 结果

- 新增题库智能生成三阶段页面：来源与参数、逐题编辑复审、导入完成。
- 文本、DOCX、TXT 三种输入互斥；文件仅本地保留一个且不自动上传。
- 题型选项遵循后端 `available` 状态，不可用项禁用并展示原因。
- 五种题型可编辑题干、选项/答案、难度、分值、知识点、标签、解析、评分规则与来源依据。
- 编辑或删除后 500ms debounce 复审；用请求序号和组件挂载门禁防止旧响应覆盖新草稿或卸载后更新。
- 只有复审 `valid=true` 且题目非空时可导入；`issues` 阻断，`warnings` 不阻断。
- `sourceAgent` 只读并始终取自后端草稿。导入成功后可进入题库或重置后继续生成。
- 未接入菜单或路由，留给 Task 8。

## TDD 证据

1. 先扩展 `questionGenerationState.test.js`，添加删除重编号、无效复审、警告放行、零题禁止四类断言。
2. 首次运行因 `canImportQuestions` 尚未导出而失败，确认 RED。
3. 实现最小纯函数后目标测试 14/14 通过。

## 验证

- `node --test src/pages/questionBank/questionGenerationState.test.js`：14/14 通过。
- `node --test`：29/29 通过。
- `npx eslint src/pages/questionBank/QuestionBankGeneratePage.jsx src/pages/questionBank/questionGenerationState.js src/api/questionGeneration.js`：通过。
- `npm run build`：通过；仅有既有的 chunk size 警告。

## 审查修复

- 填空题答案改为按 `body.blanks[].id` 查找和更新 `answer.blanks`，缺失记录自动补建，不再依赖数组下标一致。
- 导入门禁显式要求 `review.valid=true`、`issues` 为数组且为空、题目非空；`warnings` 不阻断，并增加 `importing/completed` 状态门禁。
- 重新生成开始与成功均推进复审代数，旧复审响应无法覆盖新生成草稿。
- 导入期间冻结逐题编辑和删除；导入完成后隐藏第二阶段，只保留查看题库和继续生成动作。
- options 加载失败改为持久错误 Alert 和重试按钮；加载成功前禁止生成。
- 单选题答案使用受控 `Radio.Group`；题目分值最小值严格大于 0，题目难度不可清空。
- `scoring` 与 `sourceBasis` 使用受控 JSON 草稿和字段级错误；任何文本变化立即使旧复审失效，只有解析成功（且 `sourceBasis` 为数组）才更新待导入题目并触发复审。

## 审查修复 TDD 与验证

- RED：新增纯函数测试后，因 `canEditQuestions` 等导出不存在而按预期失败。
- GREEN：定向状态测试 19/19 通过。
- 全量 Node 测试 34/34 通过。
- scoped ESLint 通过。
- Vite production build 通过；仅有既有 chunk size 警告。
