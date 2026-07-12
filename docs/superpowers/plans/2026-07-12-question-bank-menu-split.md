# 题库管理独立菜单实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将题库、试卷生成和生成的试卷拆成三个独立页面，并从 AI 模块移入独立的“题库管理”菜单分组。

**Architecture:** 使用集中路由常量定义三个新入口；侧边栏数据引用这些入口。试卷创建和历史分别使用独立页面壳复用现有业务组件，旧 `/ai/*` 地址通过 React Router 重定向保持兼容。

**Tech Stack:** React 19、React Router 7、Ant Design 5、Node.js test runner、ESLint、Vite

## Global Constraints

- 一级菜单名称必须为“题库管理”。
- 子菜单顺序必须为“题库”“试卷生成”“生成的试卷”。
- 三个入口必须是独立页面，不能继续使用页内 Tabs 切换。
- 保留现有题库、组卷、详情、试卷下载和答案下载行为。
- 旧 `/ai/question-bank` 和 `/ai/exam-papers` 路由必须继续可用并重定向。
- 不创建分支，直接在当前 `master` 修改。

---

### Task 1: 锁定独立菜单与路由契约

**Files:**
- Create: `AppWeb/src/pages/questionBank/questionBankRoutes.js`
- Create: `AppWeb/src/pages/questionBank/questionBankNavigation.test.js`
- Modify: `AppWeb/src/data/portalData.js`

**Interfaces:**
- Produces: `QUESTION_BANK_ROUTES = { questions, createPaper, paperHistory }`
- Produces: `portalGroups` 中 label 为 `题库管理` 的分组及三个菜单项。

- [ ] **Step 1: 写失败的导航契约测试**

```js
import assert from 'node:assert/strict'
import test from 'node:test'
import { portalGroups } from '../../data/portalData.js'
import { QUESTION_BANK_ROUTES } from './questionBankRoutes.js'

test('题库管理独立分组包含三个固定顺序入口', () => {
  const group = portalGroups.find((item) => item.label === '题库管理')
  assert.deepEqual(group.items.map(({ label, path }) => ({ label, path })), [
    { label: '题库', path: QUESTION_BANK_ROUTES.questions },
    { label: '试卷生成', path: QUESTION_BANK_ROUTES.createPaper },
    { label: '生成的试卷', path: QUESTION_BANK_ROUTES.paperHistory },
  ])
  const ai = portalGroups.find((item) => item.label === 'AI 模块')
  assert.equal(ai.items.some((item) => ['题库管理', '试卷生成'].includes(item.label)), false)
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd AppWeb && node --test src/pages/questionBank/questionBankNavigation.test.js`

Expected: FAIL，原因是 `questionBankRoutes.js` 不存在或独立分组不存在。

- [ ] **Step 3: 定义路由常量并调整菜单数据**

```js
export const QUESTION_BANK_ROUTES = Object.freeze({
  questions: '/question-bank/questions',
  createPaper: '/question-bank/papers/create',
  paperHistory: '/question-bank/papers/history',
})
```

在 `portalData.js` 中从 AI 分组删除两个旧菜单项，并在 AI 分组之前加入：

```js
{
  label: '题库管理',
  items: [
    { path: QUESTION_BANK_ROUTES.questions, label: '题库', icon: 'appstore' },
    { path: QUESTION_BANK_ROUTES.createPaper, label: '试卷生成', icon: 'file-text' },
    { path: QUESTION_BANK_ROUTES.paperHistory, label: '生成的试卷', icon: 'file-search' },
  ],
},
```

同时将 `moduleCards` 中题库和试卷卡片路由更新为新常量。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd AppWeb && node --test src/pages/questionBank/questionBankNavigation.test.js`

Expected: 1 test passed。

- [ ] **Step 5: 提交**

```bash
git add AppWeb/src/data/portalData.js AppWeb/src/pages/questionBank/questionBankRoutes.js AppWeb/src/pages/questionBank/questionBankNavigation.test.js
git commit -m "feat: 独立题库管理导航分组"
```

### Task 2: 拆分试卷创建与历史页面

**Files:**
- Create: `AppWeb/src/pages/questionBank/ExamPaperCreatePage.jsx`
- Create: `AppWeb/src/pages/questionBank/ExamPaperHistoryPage.jsx`
- Modify: `AppWeb/src/App.jsx`
- Test: `AppWeb/src/pages/questionBank/questionBankNavigation.test.js`

**Interfaces:**
- Consumes: `QUESTION_BANK_ROUTES`。
- Consumes: `ExamPaperCreate` 与 `ExamPaperHistory` 现有业务组件。
- Produces: 三个新路由及两个旧路由重定向。

- [ ] **Step 1: 扩展失败测试锁定页面与兼容路由**

测试读取 `App.jsx`，断言包含三个新路径、两个 `<Navigate replace>` 重定向，并且新页面源码不包含 `Tabs`：

```js
const appSource = await readFile(new URL('../../App.jsx', import.meta.url), 'utf8')
assert.match(appSource, /QUESTION_BANK_ROUTES\.questions/)
assert.match(appSource, /QUESTION_BANK_ROUTES\.createPaper/)
assert.match(appSource, /QUESTION_BANK_ROUTES\.paperHistory/)
assert.match(appSource, /path="\/ai\/question-bank"/)
assert.match(appSource, /path="\/ai\/exam-papers"/)
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd AppWeb && node --test src/pages/questionBank/questionBankNavigation.test.js`

Expected: FAIL，新页面和路由尚未接入。

- [ ] **Step 3: 创建独立页面壳**

`ExamPaperCreatePage.jsx`：

```jsx
import { Typography } from 'antd'
import ExamPaperCreate from '../ai/ExamPaper/ExamPaperCreate'
import '../ai/ExamPaper/ExamPaper.css'

const { Title, Paragraph } = Typography

export default function ExamPaperCreatePage() {
  return <div className="exam-paper-page">
    <section className="exam-paper-hero">
      <span className="exam-paper-kicker">EXAM PAPER</span>
      <Title level={1}>试卷生成</Title>
      <Paragraph>从现有题库手工选题或按规则随机组卷。</Paragraph>
    </section>
    <ExamPaperCreate />
  </div>
}
```

`ExamPaperHistoryPage.jsx` 使用相同页面壳，标题为“生成的试卷”，正文渲染 `<ExamPaperHistory />`。

- [ ] **Step 4: 接入新路由和旧路由重定向**

在 `App.jsx` 中导入 `Navigate`、两个新页面和路由常量：

```jsx
<Route path={QUESTION_BANK_ROUTES.questions} element={<QuestionBank />} />
<Route path={QUESTION_BANK_ROUTES.createPaper} element={<ExamPaperCreatePage />} />
<Route path={QUESTION_BANK_ROUTES.paperHistory} element={<ExamPaperHistoryPage />} />
<Route path="/ai/question-bank" element={<Navigate to={QUESTION_BANK_ROUTES.questions} replace />} />
<Route path="/ai/exam-papers" element={<Navigate to={QUESTION_BANK_ROUTES.createPaper} replace />} />
```

删除 `App.jsx` 对聚合 `ExamPaper` 页面的导入与直接使用。

- [ ] **Step 5: 运行导航测试、ESLint 和构建**

Run: `cd AppWeb && node --test src/pages/questionBank/questionBankNavigation.test.js`

Expected: all tests passed。

Run: `cd AppWeb && npx eslint src/App.jsx src/data/portalData.js src/pages/questionBank/*.js src/pages/questionBank/*.jsx`

Expected: exit 0。

Run: `cd AppWeb && npm run build`

Expected: `✓ built`。

- [ ] **Step 6: 提交**

```bash
git add AppWeb/src/App.jsx AppWeb/src/pages/questionBank/ExamPaperCreatePage.jsx AppWeb/src/pages/questionBank/ExamPaperHistoryPage.jsx AppWeb/src/pages/questionBank/questionBankNavigation.test.js
git commit -m "feat: 拆分试卷创建与历史页面"
```

### Task 3: 浏览器验证独立导航

**Files:**
- Modify only if verification finds a defect in Task 1 or Task 2 files.

**Interfaces:**
- Consumes: 三个新路由和侧边栏菜单。
- Produces: 用户可见的最终导航验证证据。

- [ ] **Step 1: 启动本地前端并登录管理后台**

Run: `cd AppWeb && npm run dev -- --host localhost`

Expected: Vite 提供本地 URL，页面可登录。

- [ ] **Step 2: 验证菜单结构**

确认“AI 模块”无题库/试卷入口；“题库管理”包含且按顺序显示三个子菜单。

- [ ] **Step 3: 验证三个独立页面**

依次点击“题库”“试卷生成”“生成的试卷”，确认 URL 分别匹配三个新路由，试卷页面不再显示 Tabs。

- [ ] **Step 4: 验证旧路由**

直接访问 `/ai/question-bank` 和 `/ai/exam-papers`，确认浏览器替换为对应新 URL。

- [ ] **Step 5: 最终回归**

Run: `cd AppWeb && node --test src/antdReact19Compatibility.test.js src/pages/ai/ExamPaper/examPaperPreviewState.test.js src/pages/questionBank/questionBankNavigation.test.js`

Expected: all tests passed。

Run: `cd AppWeb && npx eslint src/App.jsx src/data/portalData.js src/pages/questionBank src/pages/ai/ExamPaper`

Expected: exit 0。

Run: `cd AppWeb && npm run build`

Expected: `✓ built`。

- [ ] **Step 6: 提交验证修复（仅有改动时）**

```bash
git add AppWeb/src/App.jsx AppWeb/src/data/portalData.js AppWeb/src/pages/questionBank
git commit -m "fix: 完成题库独立导航验证"
```
