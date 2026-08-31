export function parseQuestionOptions(value) {
  if (!value) return []
  if (Array.isArray(value)) return value
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(parsed) ? parsed : Object.values(parsed || {})
  } catch {
    return String(value).split(/\r?\n/).filter(Boolean)
  }
}

export function paperQuestionId(item) {
  return item?.questionId || item?.question?.id || item?.id
}
