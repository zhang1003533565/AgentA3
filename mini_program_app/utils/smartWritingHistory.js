export const SMART_WRITING_HISTORY_KEY = 'smartWritingHistory'
export const SMART_WRITING_SAVED_WORKS_KEY = 'smartWritingSavedWorks'
export const SMART_WRITING_DRAFT_KEY = 'smartWritingDraft'

export function getSmartWritingHistory() {
  const records = uni.getStorageSync(SMART_WRITING_HISTORY_KEY)
  return Array.isArray(records) ? records : []
}

export function buildSmartWritingTitle(sceneLabel, prompt) {
  const cleanPrompt = String(prompt || '').replace(/\s+/g, ' ').trim()
  const shortPrompt = cleanPrompt.length > 18 ? `${cleanPrompt.slice(0, 18)}...` : cleanPrompt
  return [sceneLabel, shortPrompt || '智能写作'].filter(Boolean).join(' · ')
}

export function saveSmartWritingHistory(record) {
  const records = getSmartWritingHistory()
  const nextRecord = {
    ...record,
    id: record?.id || `smart-writing-${Date.now()}`,
    createdAt: record?.createdAt || new Date().toISOString()
  }
  uni.setStorageSync(SMART_WRITING_HISTORY_KEY, [nextRecord, ...records.filter(item => item.id !== nextRecord.id)].slice(0, 50))
  return nextRecord
}

export function getSmartWritingSavedWorks() {
  const records = uni.getStorageSync(SMART_WRITING_SAVED_WORKS_KEY)
  return Array.isArray(records) ? records : []
}

export function saveSmartWritingSavedWork(record) {
  const records = getSmartWritingSavedWorks()
  const nextRecord = {
    ...record,
    id: record?.id || `smart-writing-saved-${Date.now()}`,
    savedAt: new Date().toISOString(),
    createdAt: record?.createdAt || new Date().toISOString()
  }
  uni.setStorageSync(SMART_WRITING_SAVED_WORKS_KEY, [nextRecord, ...records.filter(item => item.id !== nextRecord.id)].slice(0, 50))
  return nextRecord
}

export function deleteSmartWritingSavedWork(id) {
  const records = getSmartWritingSavedWorks().filter(item => item.id !== id)
  uni.setStorageSync(SMART_WRITING_SAVED_WORKS_KEY, records)
  return records
}

export function getSmartWritingDraft() {
  const record = uni.getStorageSync(SMART_WRITING_DRAFT_KEY)
  return record && typeof record === 'object' ? record : null
}

export function saveSmartWritingDraft(record) {
  const nextRecord = {
    ...record,
    id: record?.id || `smart-writing-draft-${Date.now()}`,
    createdAt: record?.createdAt || new Date().toISOString()
  }
  uni.setStorageSync(SMART_WRITING_DRAFT_KEY, nextRecord)
  return nextRecord
}

export function clearSmartWritingDraft(id) {
  const record = getSmartWritingDraft()
  if (!record || !id || record.id === id) {
    uni.removeStorageSync(SMART_WRITING_DRAFT_KEY)
  }
}

export function deleteSmartWritingHistory(id) {
  const records = getSmartWritingHistory().filter(item => item.id !== id)
  uni.setStorageSync(SMART_WRITING_HISTORY_KEY, records)
  return records
}
