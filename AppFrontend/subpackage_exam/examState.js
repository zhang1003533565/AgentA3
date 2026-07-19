const RETRY_DELAYS = [1000, 2000, 5000]

export function remainingSeconds(deadlineAt, serverNow) {
  const deadline = new Date(deadlineAt).getTime()
  const now = new Date(serverNow).getTime()
  if (!Number.isFinite(deadline) || !Number.isFinite(now)) return 0
  return Math.max(0, Math.ceil((deadline - now) / 1000))
}

export function formatRemainingTime(seconds) {
  const safe = Math.max(0, Math.floor(Number(seconds) || 0))
  const minutes = Math.floor(safe / 60)
  const remainder = safe % 60
  return `${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
}

export function parseAnswer(type, answerJson, fallbackBlanks = []) {
  let answer = {}
  try {
    answer = answerJson ? JSON.parse(answerJson) : {}
  } catch (_) {
    answer = {}
  }
  switch (type) {
    case 'single_choice':
      return cleanText(answer.selectedOption)
    case 'multiple_choice':
      return Array.isArray(answer.selectedOptions) ? answer.selectedOptions.map(cleanText).filter(Boolean) : []
    case 'true_false':
      return typeof answer.value === 'boolean' ? answer.value : null
    case 'fill_blank': {
      const blanks = Array.isArray(answer.blanks) && answer.blanks.length ? answer.blanks : fallbackBlanks
      return blanks.map((blank) => ({ id: cleanText(blank && blank.id), value: cleanText(blank && blank.value) }))
        .filter((blank) => blank.id)
    }
    case 'short_answer':
    case 'calculation':
    case 'programming':
      return cleanText(answer.text)
    default:
      return ''
  }
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
    case 'calculation':
    case 'programming':
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
