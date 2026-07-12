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
