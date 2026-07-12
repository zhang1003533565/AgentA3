const FILE_SOURCE_TYPES = new Set(['file', 'docx', 'txt'])

const appendWhenPresent = (formData, name, value) => {
  if (value !== undefined && value !== null && value !== '') {
    formData.append(name, String(value))
  }
}

export const buildGenerationFormData = (values, file) => {
  const formData = new FormData()

  formData.append('sourceType', values.sourceType)
  appendWhenPresent(formData, 'questionType', values.questionType)
  appendWhenPresent(formData, 'maxQuestions', values.maxQuestions)
  appendWhenPresent(formData, 'difficulty', values.difficulty)
  appendWhenPresent(formData, 'sourceTitle', values.sourceTitle)

  if (FILE_SOURCE_TYPES.has(values.sourceType)) {
    if (file) formData.append('file', file)
  } else {
    appendWhenPresent(formData, 'text', values.text)
  }

  return formData
}

export const buildImportPayload = (draft, questions) => ({
  questions,
  missingInfo: draft.missingInfo ?? [],
  sourceAgent: draft.agentName,
  sourceTitle: draft.sourceTitle,
  sourceScene: 'question_generation',
})

export const normalizeQuestionForEditor = (question) => structuredClone(question)

export const serializeEditedQuestion = (question) => structuredClone(question)

export const removeQuestionAndRenumber = (questions, removedIndex) => questions
  .filter((_, index) => index !== removedIndex)
  .map((question, index) => ({ ...question, displayNumber: index + 1 }))

export const canImportQuestions = (review, questions) => (
  Boolean(review?.valid) && Array.isArray(questions) && questions.length > 0
)
