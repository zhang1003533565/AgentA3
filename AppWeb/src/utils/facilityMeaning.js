const MAX_MEANING_ITEMS = 3

const normalizeMeaningItem = (item) => ({
  title: String(item?.title || '').trim(),
  content: String(item?.content || '').trim(),
})

export const parseFacilityMeaningItems = (value) => {
  if (Array.isArray(value)) {
    return value.map(normalizeMeaningItem).filter((item) => item.title || item.content).slice(0, MAX_MEANING_ITEMS)
  }
  if (!value || !String(value).trim()) return []

  const text = String(value).trim()
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed)) {
      return parsed.map(normalizeMeaningItem).filter((item) => item.title || item.content).slice(0, MAX_MEANING_ITEMS)
    }
  } catch {
    return [{ title: '', content: text }]
  }

  return [{ title: '', content: text }]
}

export const serializeFacilityMeaningItems = (items) => {
  const nextItems = parseFacilityMeaningItems(items)
  return nextItems.length ? JSON.stringify(nextItems) : ''
}

