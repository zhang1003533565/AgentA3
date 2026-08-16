import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  buildGenerationFormData,
  buildKnowledgeMaterial,
  buildImportPayload,
  canEditQuestions,
  canImportQuestions,
  clearJsonEditorErrorsForQuestion,
  isQuestionTypeAvailable,
  invalidateReviewGeneration,
  normalizeQuestionForEditor,
  normalizeKnowledgePage,
  removeQuestionAndRenumber,
  serializeEditedQuestion,
  updateJsonEditorErrors,
  updateFillBlankAnswers,
} from './questionGenerationState.js'

const entries = (formData) => Object.fromEntries(formData.entries())

test('文本来源只提交文本而不提交文件', () => {
  const file = new Blob(['ignored'], { type: 'text/plain' })
  const data = entries(buildGenerationFormData({
    sourceType: 'text',
    text: '课程内容',
    questionType: 'single_choice',
  }, file))

  assert.equal(data.sourceType, 'text')
  assert.equal(data.text, '课程内容')
  assert.equal('file' in data, false)
})

test('DOCX 和 TXT 文件来源只提交单个文件而不提交文本', () => {
  for (const sourceType of ['docx', 'txt']) {
    const file = new Blob([sourceType], { type: 'application/octet-stream' })
    const data = entries(buildGenerationFormData({
      sourceType,
      text: 'ignored',
      questionType: 'short_answer',
    }, file))

    assert.equal(data.sourceType, sourceType)
    assert.equal(data.file instanceof Blob, true)
    assert.equal(data.file.size, file.size)
    assert.equal(data.file.type, file.type)
    assert.equal('text' in data, false)
  }
})

test('通用文件来源与具体文件来源遵守同一互斥契约', () => {
  const file = new Blob(['lesson'])
  const data = entries(buildGenerationFormData({
    sourceType: 'file',
    text: 'ignored',
    questionType: 'fill_blank',
  }, file))

  assert.equal(data.sourceType, 'file')
  assert.equal(data.file instanceof Blob, true)
  assert.equal(data.file.size, file.size)
  assert.equal('text' in data, false)
})

test('知识库分页响应兼容 records 和嵌套 data 结构', () => {
  assert.deepEqual(normalizeKnowledgePage({ data: { code: 200, data: { records: [{ id: 'kb-1' }], total: 1 } } }), {
    records: [{ id: 'kb-1' }],
    total: 1,
  })
})

test('只把用户选中的知识库文档分段合并为题目材料', () => {
  const material = buildKnowledgeMaterial(
    { id: 'kb-1', name: 'Python 知识库' },
    [
      { id: 'doc-1', name: '基础语法' },
      { id: 'doc-2', name: '数据结构' },
    ],
    {
      'doc-1': [{ content: '变量与数据类型' }, { text: '条件语句' }],
      'doc-2': [{ content: '列表与元组' }],
      'doc-3': [{ content: '未选择内容' }],
    },
  )

  assert.equal(material.sourceTitle, 'Python 知识库')
  assert.equal(material.documentCount, 2)
  assert.match(material.text, /基础语法[\s\S]*变量与数据类型[\s\S]*条件语句/)
  assert.match(material.text, /数据结构[\s\S]*列表与元组/)
  assert.doesNotMatch(material.text, /未选择内容/)
})

test('空最大题量不写入 FormData，有值时作为生成上限写入', () => {
  const empty = entries(buildGenerationFormData({
    sourceType: 'text', text: '材料', questionType: 'true_false', maxQuestions: '',
  }))
  const limited = entries(buildGenerationFormData({
    sourceType: 'text', text: '材料', questionType: 'true_false', maxQuestions: 12,
  }))

  assert.equal('maxQuestions' in empty, false)
  assert.equal(limited.maxQuestions, '12')
})

const questionCases = [
  {
    type: 'single_choice',
    body: { options: [{ key: 'A', text: '甲', extension: 'keep' }], layout: 'vertical' },
    answer: { correct: 'A', evidence: ['第一段'] },
  },
  {
    type: 'multiple_choice',
    body: { options: [{ key: 'A', text: '甲' }, { key: 'B', text: '乙' }], shuffle: true },
    answer: { correct: ['A', 'B'], partialCredit: false },
  },
  {
    type: 'true_false',
    body: { statement: '天空是蓝色', display: 'buttons' },
    answer: { correct: true, accepted: [true] },
  },
  {
    type: 'fill_blank',
    body: { blanks: [{ id: 'blank-1', placeholder: '概念', extra: 1 }], caseSensitive: false },
    answer: { blanks: [{ id: 'blank-1', answers: ['人工智能'], score: 2 }], orderMatters: true },
  },
  {
    type: 'short_answer',
    body: { prompt: '说明原因', responseLines: 4 },
    answer: { reference: '参考答案', keyPoints: ['要点一'], rubricVersion: 2 },
  },
]

for (const sample of questionCases) {
  test(`${sample.type} 编辑态往返保留 body、answer、标准字段和未知字段`, () => {
    const question = {
      sourceQuestionId: `${sample.type}-1`,
      stem: '题干',
      score: 5,
      difficulty: 'medium',
      knowledgePoints: ['知识点'],
      tags: ['标签'],
      analysis: '解析',
      scoring: { mode: 'exact' },
      sourceBasis: { pages: [1] },
      customField: { nested: 'keep' },
      ...sample,
    }

    assert.deepEqual(serializeEditedQuestion(normalizeQuestionForEditor(question)), question)
  })
}

test('生成导入只发送一次性 proof 与编辑后题目，不发送可伪造来源元数据', () => {
  const draft = {
    proof: 'server-proof',
    agentName: 'backend-agent',
    sourceTitle: '后端解析标题',
    missingInfo: ['缺少章节'],
    sourceAgent: 'untrusted-alias',
    sourceScene: 'manual',
  }
  const questions = [{
    type: 'single_choice',
    stem: '编辑后的题目',
    agentName: 'edited-agent',
    sourceAgent: 'edited-agent',
    sourceTitle: '编辑标题',
    sourceScene: 'edited-scene',
  }]

  assert.deepEqual(buildImportPayload(draft, questions), {
    proof: 'server-proof',
    questions,
    missingInfo: ['缺少章节'],
  })
})

test('删除题目后重新编号但不改来源 ID', () => {
  const questions = [
    { id: 'source-1', sourceQuestionId: 'legacy-1', displayNumber: 1, stem: '第一题' },
    { id: 'source-2', sourceQuestionId: 'legacy-2', displayNumber: 2, stem: '第二题' },
    { id: 'source-3', sourceQuestionId: 'legacy-3', displayNumber: 3, stem: '第三题' },
  ]

  assert.deepEqual(removeQuestionAndRenumber(questions, 1), [
    { id: 'source-1', sourceQuestionId: 'legacy-1', displayNumber: 1, stem: '第一题' },
    { id: 'source-3', sourceQuestionId: 'legacy-3', displayNumber: 2, stem: '第三题' },
  ])
  assert.equal(questions[2].displayNumber, 3)
})

test('review valid=false 时禁止导入', () => {
  assert.equal(canImportQuestions({ valid: false, issues: ['题干缺失'] }, [{ id: 'q-1' }]), false)
})

test('警告不阻断已通过复审的导入', () => {
  assert.equal(canImportQuestions({ valid: true, issues: [], warnings: ['建议补充解析'] }, [{ id: 'q-1' }]), true)
})

test('review 有效且 issues 为空时编辑器 JSON 错误仍禁止导入', () => {
  const review = { valid: true, issues: [], warnings: [] }
  const questions = [{ id: 'q-1' }]

  assert.equal(canImportQuestions(review, questions, undefined, 1), false)
  assert.equal(canImportQuestions(review, questions, undefined, 0), true)
})

test('零题时即使 review valid=true 也禁止导入', () => {
  assert.equal(canImportQuestions({ valid: true, issues: [], warnings: [] }, []), false)
})

test('填空答案按 blank id 更新而不是按数组下标更新', () => {
  const answers = [
    { id: 'blank-b', answers: ['乙'] },
    { id: 'blank-a', answers: ['甲'] },
  ]

  assert.deepEqual(updateFillBlankAnswers(answers, 'blank-a', ['新答案']), [
    { id: 'blank-b', answers: ['乙'] },
    { id: 'blank-a', answers: ['新答案'] },
  ])
})

test('填空答案缺少对应 blank id 时补建记录', () => {
  assert.deepEqual(updateFillBlankAnswers([{ id: 'blank-a', answers: ['甲'] }], 'blank-b', ['乙']), [
    { id: 'blank-a', answers: ['甲'] },
    { id: 'blank-b', answers: ['乙'] },
  ])
})

test('review issues 非空时即使 valid=true 也禁止导入且 warnings 不阻断', () => {
  const questions = [{ id: 'q-1' }]
  assert.equal(canImportQuestions({ valid: true, issues: ['题干缺失'], warnings: [] }, questions), false)
  assert.equal(canImportQuestions({ valid: true, issues: [], warnings: ['建议补充解析'] }, questions), true)
})

test('导入中和导入完成状态冻结编辑并禁止再次导入', () => {
  const review = { valid: true, issues: [], warnings: [] }
  const questions = [{ id: 'q-1' }]

  assert.equal(canEditQuestions({ importing: true, completed: false }), false)
  assert.equal(canEditQuestions({ importing: false, completed: true }), false)
  assert.equal(canEditQuestions({ importing: false, completed: false }), true)
  assert.equal(canImportQuestions(review, questions, { importing: true, completed: false }), false)
  assert.equal(canImportQuestions(review, questions, { importing: false, completed: true }), false)
})

test('重新生成开始和成功都推进复审代数使旧请求失效', () => {
  const started = invalidateReviewGeneration(7)
  const succeeded = invalidateReviewGeneration(started)

  assert.equal(started, 8)
  assert.equal(succeeded, 9)
})

test('只有 options 中存在且 available=true 的题型可生成', () => {
  const options = [
    { type: 'single_choice', available: true },
    { type: 'short_answer', available: false },
  ]

  assert.equal(isQuestionTypeAvailable(options, 'single_choice'), true)
  assert.equal(isQuestionTypeAvailable(options, 'short_answer'), false)
  assert.equal(isQuestionTypeAvailable(options, 'missing'), false)
  assert.equal(isQuestionTypeAvailable(undefined, 'single_choice'), false)
})

test('JSON 编辑错误按题目字段增加、修正后清除且不影响其他字段', () => {
  const initial = new Set()
  const invalid = updateJsonEditorErrors(initial, 'q-1:scoring', true)
  const twoInvalid = updateJsonEditorErrors(invalid, 'q-1:sourceBasis', true)
  const corrected = updateJsonEditorErrors(twoInvalid, 'q-1:scoring', false)

  assert.deepEqual([...invalid], ['q-1:scoring'])
  assert.deepEqual([...twoInvalid], ['q-1:scoring', 'q-1:sourceBasis'])
  assert.deepEqual([...corrected], ['q-1:sourceBasis'])
  assert.equal(initial.size, 0)
})

test('删除题目只清理该题 JSON 编辑错误', () => {
  const errors = new Set(['q-1:scoring', 'q-1:sourceBasis', 'q-2:scoring'])

  assert.deepEqual([...clearJsonEditorErrorsForQuestion(errors, 'q-1')], ['q-2:scoring'])
})

test('页面在解析 JSON 前立即失效复审且生成请求前二次校验题型可用性', async () => {
  const source = await readFile(new URL('./QuestionBankGeneratePage.jsx', import.meta.url), 'utf8')
  const updateDraft = source.slice(source.indexOf('const updateDraft'), source.indexOf('return <label'))
  const handleGenerate = source.slice(source.indexOf('const handleGenerate'), source.indexOf('const updateQuestion'))

  assert.ok(updateDraft.indexOf('onInvalidate()') < updateDraft.indexOf('JSON.parse(nextValue)'))
  assert.ok(handleGenerate.indexOf('isQuestionTypeAvailable(options, values.questionType)') < handleGenerate.indexOf('generateQuestions('))
  assert.match(handleGenerate, /message\.error\('所选题型当前不可用，请重新选择'\)/)
})
