import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildGenerationFormData,
  buildImportPayload,
  canEditQuestions,
  canImportQuestions,
  invalidateReviewGeneration,
  normalizeQuestionForEditor,
  removeQuestionAndRenumber,
  serializeEditedQuestion,
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

test('导入来源只信任后端草稿且来源场景固定', () => {
  const draft = {
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
    questions,
    missingInfo: ['缺少章节'],
    sourceAgent: 'backend-agent',
    sourceTitle: '后端解析标题',
    sourceScene: 'question_generation',
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
