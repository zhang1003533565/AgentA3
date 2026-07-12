import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { portalGroups } from '../../data/portalData.js'
import { QUESTION_BANK_ROUTES } from './questionBankRoutes.js'

test('题库管理菜单按固定顺序提供唯一的题库生成入口', () => {
  const group = portalGroups.find((item) => item.label === '题库管理')

  assert.deepEqual(group.items.map(({ label, path }) => ({ label, path })), [
    { label: '题库', path: QUESTION_BANK_ROUTES.questions },
    { label: '题库生成', path: QUESTION_BANK_ROUTES.generate },
    { label: '试卷生成', path: QUESTION_BANK_ROUTES.createPaper },
    { label: '生成的试卷', path: QUESTION_BANK_ROUTES.paperHistory },
  ])
  assert.equal(QUESTION_BANK_ROUTES.generate, '/question-bank/generate')
  assert.equal(
    portalGroups.flatMap((item) => item.items).filter((item) => item.path === QUESTION_BANK_ROUTES.generate).length,
    1,
  )
})

test('应用将题库生成页面注册到生成路由', async () => {
  const appSource = await readFile(new URL('../../App.jsx', import.meta.url), 'utf8')

  assert.match(
    appSource,
    /import QuestionBankGeneratePage from ['"]\.\/pages\/questionBank\/QuestionBankGeneratePage['"]/,
  )
  assert.match(
    appSource,
    /<Route path={QUESTION_BANK_ROUTES\.generate} element={<QuestionBankGeneratePage \/>} \/>/,
  )
})
