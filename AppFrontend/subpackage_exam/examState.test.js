const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const stateSource = readFileSync(join(__dirname, 'examState.js'), 'utf8')
const state = import(`data:text/javascript;base64,${Buffer.from(stateSource).toString('base64')}`)

test('remainingSeconds uses server time and clamps expired attempts to zero', async () => {
  const { remainingSeconds } = await state
  assert.equal(remainingSeconds('2026-07-12T12:00:05Z', '2026-07-12T12:00:00Z'), 5)
  assert.equal(remainingSeconds('2026-07-12T12:00:00.100Z', '2026-07-12T12:00:00Z'), 1)
  assert.equal(remainingSeconds('2026-07-12T11:59:59Z', '2026-07-12T12:00:00Z'), 0)
})

test('normalizeAnswer creates stable payloads for all five question types', async () => {
  const { normalizeAnswer } = await state
  assert.deepEqual(normalizeAnswer('single_choice', 'B'), { selectedOption: 'B' })
  assert.deepEqual(normalizeAnswer('multiple_choice', ['C', 'A', 'C', '']), {
    selectedOptions: ['A', 'C']
  })
  assert.deepEqual(normalizeAnswer('true_false', true), { value: true })
  assert.deepEqual(normalizeAnswer('fill_blank', [
    { id: 'blank_2', value: '  栈底 ' },
    { id: 'blank_1', value: '栈顶' }
  ]), {
    blanks: [
      { id: 'blank_1', value: '栈顶' },
      { id: 'blank_2', value: '栈底' }
    ]
  })
  assert.deepEqual(normalizeAnswer('short_answer', '  说明过程  '), { text: '说明过程' })
})

test('mergeSavedAnswer never lets an older response replace newer local input', async () => {
  const { mergeSavedAnswer } = await state
  const local = { answerJson: '{"selectedOption":"C"}', version: 4, saved: false }
  const stale = { answerJson: '{"selectedOption":"A"}', version: 3, answered: true }
  assert.deepEqual(mergeSavedAnswer(local, stale), local)
  assert.deepEqual(mergeSavedAnswer(local, { answerJson: '{}', answered: true }), local)

  assert.deepEqual(mergeSavedAnswer(local, {
    answerJson: '{"selectedOption":"C"}', version: 4, answered: true
  }), {
    answerJson: '{"selectedOption":"C"}', version: 4, answered: true, saved: true
  })
})

test('retryDelay follows the bounded 1, 2 and 5 second schedule', async () => {
  const { retryDelay } = await state
  assert.equal(retryDelay(0), 1000)
  assert.equal(retryDelay(1), 2000)
  assert.equal(retryDelay(2), 5000)
  assert.equal(retryDelay(3), null)
})

test('pages.json registers the exam subpackage and mine exposes the entry', () => {
  const pages = JSON.parse(readFileSync(join(__dirname, '../pages.json'), 'utf8'))
  const examPackage = pages.subPackages.find((item) => item.root === 'subpackage_exam')
  assert.ok(examPackage)
  assert.deepEqual(examPackage.pages.map((item) => item.path), [
    'paperList/paperList',
    'paperDetail/paperDetail',
    'attempt/attempt',
    'attemptHistory/attemptHistory',
    'attemptResult/attemptResult'
  ])

  const mine = readFileSync(join(__dirname, '../pages/mine/mine.vue'), 'utf8')
  assert.match(mine, />我的试卷</)
  assert.match(mine, /goToExamPapers/)
  assert.match(mine, /\/subpackage_exam\/paperList\/paperList/)
})

test('exam API exposes every route and keeps PDF outside the JSON request wrapper', () => {
  const api = readFileSync(join(__dirname, '../api/exam.js'), 'utf8')
  for (const name of [
    'getExamPapers',
    'getExamPaperDetail',
    'downloadExamPaperPdf',
    'startExamAttempt',
    'getExamAttempt',
    'saveExamAnswer',
    'submitExamAttempt',
    'getExamAttemptHistory',
    'getExamAttemptResult'
  ]) {
    assert.match(api, new RegExp(`export function ${name}\\b`))
  }
  assert.match(api, /uni\.downloadFile/)
  assert.match(api, /uni\.openDocument/)
  for (const route of [
    '/api/app/exam-papers',
    '/api/app/exam-attempts/',
    '/attempts',
    '/answers/',
    '/submit',
    '/result',
    '/pdf'
  ]) {
    assert.ok(api.includes(route), `missing API route fragment: ${route}`)
  }
  assert.match(api, /method: 'GET'/)
  assert.match(api, /method: 'POST'/)
  assert.match(api, /method: 'PUT'/)
  const pdfFunction = api.slice(api.indexOf('export function downloadExamPaperPdf'))
  assert.doesNotMatch(pdfFunction, /\brequest\s*\(/)
})
