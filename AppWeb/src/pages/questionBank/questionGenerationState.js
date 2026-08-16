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

export const unwrapKnowledgePayload = (response) => {
  let payload = response?.data ?? response
  if (payload && typeof payload === 'object' && 'data' in payload && ('code' in payload || 'msg' in payload || 'message' in payload)) {
    payload = payload.data
  }
  return payload
}

export const normalizeKnowledgePage = (response) => {
  const payload = unwrapKnowledgePayload(response)
  if (Array.isArray(payload)) {
    return { records: payload, total: payload.length }
  }
  const records = payload?.records || payload?.list || payload?.items || payload?.rows || []
  return {
    records: Array.isArray(records) ? records : [],
    total: Number(payload?.total ?? payload?.count ?? records.length ?? 0),
  }
}

const firstText = (...values) => {
  const value = values.find((item) => item !== undefined && item !== null && String(item).trim())
  return value === undefined ? '' : String(value).trim()
}

export const buildKnowledgeMaterial = (knowledge, selectedDocuments, paragraphGroups) => {
  const knowledgeName = firstText(knowledge?.name, knowledge?.knowledge_name, knowledge?.title, knowledge?.id, '知识库')
  const sections = (selectedDocuments || []).map((document, index) => {
    const documentId = String(document?.id ?? document?.document_id ?? '')
    const documentName = firstText(document?.name, document?.title, document?.file_name, documentId, `文档 ${index + 1}`)
    const paragraphs = paragraphGroups?.[documentId] || []
    const content = paragraphs
      .map((item) => firstText(item?.content, item?.text))
      .filter(Boolean)
      .join('\n\n')
    return content ? `## ${documentName}\n\n${content}` : ''
  }).filter(Boolean)
  const text = [`# ${knowledgeName}`, ...sections].join('\n\n').trim()
  return {
    text,
    sourceTitle: knowledgeName,
    documentCount: sections.length,
  }
}

export const buildImportPayload = (draft, questions) => ({
  proof: draft.proof,
  questions,
  missingInfo: draft.missingInfo ?? [],
})

export const normalizeQuestionForEditor = (question) => structuredClone(question)

export const serializeEditedQuestion = (question) => structuredClone(question)

export const removeQuestionAndRenumber = (questions, removedIndex) => questions
  .filter((_, index) => index !== removedIndex)
  .map((question, index) => ({ ...question, displayNumber: index + 1 }))

export const updateFillBlankAnswers = (answerBlanks, blankId, answers) => {
  const current = Array.isArray(answerBlanks) ? answerBlanks : []
  const found = current.some((blank) => blank.id === blankId)
  if (!found) return [...current, { id: blankId, answers }]
  return current.map((blank) => blank.id === blankId ? { ...blank, answers } : blank)
}

export const invalidateReviewGeneration = (generation) => generation + 1

export const canEditQuestions = ({ importing = false, completed = false } = {}) => (
  !importing && !completed
)

export const isQuestionTypeAvailable = (options, questionType) => (
  Array.isArray(options)
  && options.some((option) => option.type === questionType && option.available === true)
)

export const updateJsonEditorErrors = (errors, errorKey, hasError) => {
  const next = new Set(errors)
  if (hasError) next.add(errorKey)
  else next.delete(errorKey)
  return next
}

export const clearJsonEditorErrorsForQuestion = (errors, questionKey) => (
  new Set([...errors].filter((errorKey) => !errorKey.startsWith(`${questionKey}:`)))
)

export const canImportQuestions = (review, questions, status, jsonInvalidCount = 0) => (
  canEditQuestions(status)
  && jsonInvalidCount === 0
  && Boolean(review?.valid)
  && Array.isArray(review?.issues)
  && review.issues.length === 0
  && Array.isArray(questions)
  && questions.length > 0
)
