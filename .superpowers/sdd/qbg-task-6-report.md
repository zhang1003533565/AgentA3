# Task 6 实施报告：题库生成状态模型和 API

## 结果

- 新增题库生成 options 与 multipart generate API，生成请求超时为 120 秒。
- 新增生成 FormData 构造函数，文本、DOCX、TXT 三类来源互斥，并兼容后端接受的通用 `file` 来源类型。
- `maxQuestions` 为空时不提交，有值时作为上限提交。
- 新增题目编辑态无损转换，五种标准题型的 `body`、`answer`、标准字段及未知字段均可完整往返。
- 导入载荷的 `sourceAgent`、`sourceTitle` 只取后端草稿，`sourceScene` 固定为 `question_generation`。
- 未实现 UI。

## TDD 证据

1. 先创建 `questionGenerationState.test.js`。
2. 首次运行定向测试因 `questionGenerationState.js` 不存在，以 `ERR_MODULE_NOT_FOUND` 失败。
3. 加入最小实现后运行定向测试：10/10 通过。

## 验证

- `cd AppWeb && node --test src/pages/questionBank/questionGenerationState.test.js`：10/10 通过。
- `cd AppWeb && node --test $(rg --files src -g '*.test.js')`：25/25 通过。
- `cd AppWeb && npx eslint src/pages/questionBank/questionGenerationState.test.js src/pages/questionBank/questionGenerationState.js src/api/questionGeneration.js`：通过，0 error。

## 范围说明

- 本任务仅新增 API、纯状态函数及其测试。
- 未暂存或修改既有 `.superpowers/sdd/qbg-task-4-report.md` 与 `.superpowers/sdd/qbg-task-5-report.md` 工作区改动。
