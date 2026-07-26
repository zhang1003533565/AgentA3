export const STUDENT_HEADER_FIELDS = Object.freeze([
  { value: 'school', label: '学校' },
  { value: 'grade', label: '年级' },
  { value: 'className', label: '班级' },
  { value: 'name', label: '姓名' },
  { value: 'studentNo', label: '学号' },
])

export const DEFAULT_STUDENT_HEADER_FIELDS = Object.freeze(['school', 'className', 'name', 'studentNo'])

const studentHeaderFieldLabels = Object.freeze(Object.fromEntries(
  STUDENT_HEADER_FIELDS.map(({ value, label }) => [value, label]),
))

export const buildStudentHeaderInfo = (layout = {}) => {
  if (layout.studentInfoVisible === false) return ''
  const fields = Array.isArray(layout.studentInfoFields)
    ? layout.studentInfoFields
    : DEFAULT_STUDENT_HEADER_FIELDS
  return fields
    .map((field) => studentHeaderFieldLabels[field])
    .filter(Boolean)
    .map((label) => `${label}________`)
    .join('  ')
}

export const normalizeLayoutForRequest = (layout = {}) => {
  const {
    studentInfoVisible,
    studentInfoFields,
    ...requestLayout
  } = layout
  const hasStudentInfoConfig = 'studentInfoVisible' in layout || 'studentInfoFields' in layout
  return {
    ...requestLayout,
    headerInfo: hasStudentInfoConfig
      ? buildStudentHeaderInfo({ studentInfoVisible, studentInfoFields })
      : requestLayout.headerInfo || '',
  }
}

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
  studentInfoVisible: true,
  studentInfoFields: DEFAULT_STUDENT_HEADER_FIELDS,
  headerInfo: '学校________  班级________  姓名________  学号________',
  titleFontSize: 50,
  subtitleFontSize: 24,
  bodyFontSize: 21,
})

export const DEFAULT_RANDOM_RULES = Object.freeze([{ type: 'single_choice', quantity: 1 }])

export const shouldAcceptPreviewGeneration = ({
  generation,
  currentGeneration,
  mounted,
}) => mounted && generation === currentGeneration

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

export const createPreviewSignature = (values, questions, typeScoreRules = {}) => JSON.stringify(stableValue({
  form: {
    title: values.title?.trim() || '',
    subtitle: values.subtitle?.trim() || null,
    durationMinutes: values.durationMinutes,
    precautions: values.precautions?.trim() || null,
    layout: normalizeLayoutForRequest(values.layout),
    selectionMode: values.selectionMode?.toUpperCase() || null,
  },
  typeScoreRules,
  questions: questions.map(({ questionId, score }, index) => ({
    questionId: Number(questionId),
    score: Number(score),
    sortOrder: index + 1,
  })),
}))

export const createPreviewProof = (preview) => preview ? ({
  token: preview.token,
  configurationHash: preview.configurationHash,
  questionHash: preview.questionHash,
}) : undefined

export const getValidationErrorMessage = (error) => (
  error?.errorFields?.flatMap((field) => field.errors || []).find(Boolean)
  || '请检查试卷信息和页面格式'
)

export const buildExamPaperRequest = (values, questions, previewProof, typeScoreRules = {}) => ({
  title: values.title.trim(),
  subtitle: values.subtitle?.trim() || null,
  durationMinutes: values.durationMinutes,
  precautions: values.precautions?.trim() || null,
  layout: normalizeLayoutForRequest(values.layout),
  selectionMode: values.selectionMode.toUpperCase(),
  typeScoreRules,
  questions: questions.map((question, index) => ({
    questionId: Number(question.questionId),
    score: Number(question.score),
    sortOrder: index + 1,
  })),
  ...(previewProof ? { previewProof } : {}),
})
