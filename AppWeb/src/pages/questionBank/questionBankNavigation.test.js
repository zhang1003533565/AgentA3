import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { portalGroups } from '../../data/portalData.js'
import { QUESTION_BANK_ROUTES } from './questionBankRoutes.js'

test('题库管理独立分组包含四个固定顺序入口', () => {
  const group = portalGroups.find((item) => item.label === '题库管理')
  assert.deepEqual(group.items.map(({ label, path }) => ({ label, path })), [
    { label: '题库', path: QUESTION_BANK_ROUTES.questions },
    { label: '题库生成', path: QUESTION_BANK_ROUTES.generate },
    { label: '试卷生成', path: QUESTION_BANK_ROUTES.createPaper },
    { label: '生成的试卷', path: QUESTION_BANK_ROUTES.paperHistory },
  ])
  const ai = portalGroups.find((item) => item.label === 'AI 模块')
  assert.equal(ai.items.some((item) => ['题库管理', '试卷生成'].includes(item.label)), false)
})

test('应用接入题库独立页面并兼容旧路由', async () => {
  const appSource = await readFile(new URL('../../App.jsx', import.meta.url), 'utf8')

  assert.match(appSource, /QUESTION_BANK_ROUTES\.questions/)
  assert.match(appSource, /QUESTION_BANK_ROUTES\.generate/)
  assert.match(appSource, /QUESTION_BANK_ROUTES\.createPaper/)
  assert.match(appSource, /QUESTION_BANK_ROUTES\.paperHistory/)
  assert.match(
    appSource,
    /path="\/ai\/question-bank" element={<Navigate to={QUESTION_BANK_ROUTES\.questions} replace \/>}/,
  )
  assert.match(
    appSource,
    /path="\/ai\/exam-papers" element={<Navigate to={QUESTION_BANK_ROUTES\.createPaper} replace \/>}/,
  )
})

test('试卷创建与历史页面不再使用页内标签页', async () => {
  const [createSource, historySource] = await Promise.all([
    readFile(new URL('./ExamPaperCreatePage.jsx', import.meta.url), 'utf8'),
    readFile(new URL('./ExamPaperHistoryPage.jsx', import.meta.url), 'utf8'),
  ])

  assert.doesNotMatch(createSource, /\bTabs\b/)
  assert.doesNotMatch(historySource, /\bTabs\b/)
})
