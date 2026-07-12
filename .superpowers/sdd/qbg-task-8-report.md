# Task 8 实施报告

## 结果

- 新增 `QUESTION_BANK_ROUTES.generate = '/question-bank/generate'`。
- “题库管理”菜单固定顺序为：题库、题库生成、试卷生成、生成的试卷；题库生成入口唯一。
- `App.jsx` 将 Task 7 的 `QuestionBankGeneratePage` 注册到生成路由。
- 未修改题库生成页面业务逻辑。

## TDD 证据

1. 先新增 `questionBankRoutes.test.js`，运行指定测试得到 0/2：菜单缺少题库生成入口，App 缺少页面导入和路由注册。
2. 加入最小路由、菜单和 App 注册，并更新既有导航契约后，新旧导航测试 5/5 通过。

## 验证

- 后端聚焦测试：57 tests，0 failures，0 errors，`BUILD SUCCESS`。首次受限运行因 Mockito 无法 attach JVM、测试临时服务器无法 bind socket 而失败；允许 JVM attach/本地端口后使用相同 Maven 命令重跑通过。
- 前端全部 Node 测试：41/41 通过。
- 本次变更 scoped ESLint：通过，零问题。
- `npm run build`：成功，Vite 转换 3743 modules；仅有既存的大 chunk 警告。
- `npm run lint`：未通过，仓库既有 33 errors、13 warnings；报错均不在本次 Task 8 文件，主要位于 `Home.jsx`、`Statistics.jsx`、活动页面、`WorkspacePage.jsx` 等。
- 本地静态路由烟雾：Vite 监听 `http://127.0.0.1:5174/`，请求 `/question-bank/generate` 返回 `200 text/html`，响应包含 `#root` 和 `/src/main.jsx`。
- `git diff --check`：通过。

## 未验证与环境阻塞

真实 TXT/DOCX、智能体映射、生成、复审、导入、题库和试卷手选的浏览器全链路未执行。当前任务环境没有可复用的已登录管理员会话、真实 AI/Python 服务与模型凭据；因此不将静态路由烟雾或自动化测试表述为真实材料 E2E 通过。相关 Java/Node 自动化测试覆盖了 TXT/DOCX 解析、映射可用性、最大题量、编辑/删除复审和导入门禁等契约。
