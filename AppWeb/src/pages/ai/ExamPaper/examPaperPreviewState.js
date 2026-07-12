export const SOURCE_LAYOUT_DEFAULTS = Object.freeze({
  renderMode: 'TEMPLATE',
  pageSize: 'A3',
  orientation: 'LANDSCAPE',
  marginPreset: 'BINDING',
  customMarginTop: null,
  customMarginRight: null,
  customMarginBottom: null,
  customMarginLeft: null,
  columnsCount: 2,
  columnSpace: 425,
  hasBindingLine: true,
  headerInfo: '煤矿___________    部门___________   岗位___________    姓名___________',
  titleFontSize: 50,
  subtitleFontSize: 24,
  bodyFontSize: 21,
})

export const shouldAcceptPreviewGeneration = ({
  generation,
  currentGeneration,
  mounted,
  requestedSignature,
  currentSignature,
}) => mounted && generation === currentGeneration && requestedSignature === currentSignature

const stableValue = (value) => {
  if (Array.isArray(value)) return value.map(stableValue)
  if (value && typeof value === 'object') {
    return Object.keys(value).sort().reduce((result, key) => {
      if (value[key] !== undefined) result[key] = stableValue(value[key])
      return result
    }, {})
  }
  return value
}

export const createPreviewSignature = (values, questions) => JSON.stringify(stableValue({
  form: values,
  questions: questions.map(({ questionId, score }, index) => ({
    questionId: Number(questionId),
    score: Number(score),
    sortOrder: index + 1,
  })),
}))

export const buildExamPaperRequest = (values, questions) => ({
  title: values.title.trim(),
  subtitle: values.subtitle?.trim() || null,
  durationMinutes: values.durationMinutes,
  precautions: values.precautions?.trim() || null,
  layout: { ...values.layout },
  selectionMode: values.selectionMode.toUpperCase(),
  questions: questions.map((question, index) => ({
    questionId: Number(question.questionId),
    score: Number(question.score),
    sortOrder: index + 1,
  })),
})
