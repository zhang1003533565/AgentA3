const RETRY_DELAYS = [1000, 2000, 5000]

export function remainingSeconds(deadlineAt, serverNow) {
  const deadline = new Date(deadlineAt).getTime()
  const now = new Date(serverNow).getTime()
  if (!Number.isFinite(deadline) || !Number.isFinite(now)) return 0
  return Math.max(0, Math.ceil((deadline - now) / 1000))
}

export function normalizeAnswer(type, value) {
  switch (type) {
    case 'single_choice':
      return { selectedOption: cleanText(value) }
    case 'multiple_choice':
      return {
        selectedOptions: [...new Set((Array.isArray(value) ? value : [])
          .map(cleanText)
          .filter(Boolean))].sort()
      }
    case 'true_false':
      return { value: value === true }
    case 'fill_blank':
      return {
        blanks: (Array.isArray(value) ? value : [])
          .map((blank) => ({ id: cleanText(blank && blank.id), value: cleanText(blank && blank.value) }))
          .filter((blank) => blank.id)
          .sort((left, right) => left.id.localeCompare(right.id))
      }
    case 'short_answer':
      return { text: cleanText(value) }
    default:
      throw new TypeError(`不支持的题型: ${type}`)
  }
}

export function mergeSavedAnswer(local, response) {
  const responseVersion = Number(response && response.version)
  if (!Number.isFinite(responseVersion) || responseVersion < Number(local.version)) return local
  return { ...local, ...response, saved: true }
}

export function retryDelay(retryIndex) {
  return RETRY_DELAYS[retryIndex] ?? null
}

function cleanText(value) {
  return value == null ? '' : String(value).trim()
}
