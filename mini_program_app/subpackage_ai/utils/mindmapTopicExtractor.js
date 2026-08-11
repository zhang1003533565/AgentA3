const MAX_CENTER_TOPIC_LENGTH = 10

const NOISE_WORDS = [
  '思维导图', '导图', '中心主题', '主题名称', '输入内容', '补充要求',
  '生成', '制作', '创建', '输出', '帮我', '请帮我', '请根据', '根据',
  '一份', '一个', '这个', '那个', '相关', '内容', '要求', '例如',
  '文件', '导入文件', '支持', '自动提取'
]

const GENERIC_CANDIDATES = new Set([
  '思维导图', '输入内容', '补充要求', '文件内容', '自动提取', '知识梳理',
  '课程体系', '复习提纲', '项目拆解', '核心知识', '主要内容'
])

const DOMAIN_HINTS = [
  '课程', '体系', '学习', '路线', '知识', '项目', '复习', '提纲',
  '计算机', '数据结构', '操作系统', '网络', 'Linux', 'Python', '算法',
  '架构', '流程', '管理', '分析', '设计', '实践'
]

export function extractMindmapCenterTopic({
  userText = '',
  fileName = '',
  text = '',
  maxLength = MAX_CENTER_TOPIC_LENGTH
} = {}) {
  const limit = Math.max(4, Number(maxLength || MAX_CENTER_TOPIC_LENGTH))
  const candidates = []
  const fullText = [userText, fileName, text].filter(Boolean).join('\n')

  addCandidate(candidates, cleanFileName(fileName), 16, 'filename')
  addTextCandidates(candidates, userText, 14, 'user')
  addTextCandidates(candidates, text, 10, 'body')
  addFrequencyCandidates(candidates, fullText, 8)

  const ranked = candidates
    .map(item => {
      const value = trimTopic(item.value, limit)
      if (!isUsefulTopic(value)) return null
      return {
        value,
        score: item.weight + scoreTopic(value, fullText, item.source)
      }
    })
    .filter(Boolean)
    .sort((a, b) => b.score - a.score || b.value.length - a.value.length)

  return ranked[0]?.value || ''
}

function addCandidate(candidates, value, weight, source) {
  const cleaned = cleanCandidate(value)
  if (cleaned) candidates.push({ value: cleaned, weight, source })
}

function addTextCandidates(candidates, text, weight, source) {
  const normalized = String(text || '').replace(/\r/g, '\n')
  if (!normalized.trim()) return
  const lines = normalized
    .split('\n')
    .map(line => line.trim())
    .filter(Boolean)
    .slice(0, 16)

  lines.forEach((line, index) => {
    const lineWeight = weight + Math.max(0, 5 - index)
    splitPhrases(line).slice(0, 6).forEach(phrase => {
      addCandidate(candidates, phrase, lineWeight, source)
    })
  })
}

function addFrequencyCandidates(candidates, text, weight) {
  const phrases = splitPhrases(text)
  const counts = new Map()
  phrases.forEach(phrase => {
    const value = trimTopic(cleanCandidate(phrase), MAX_CENTER_TOPIC_LENGTH)
    if (isUsefulTopic(value)) counts.set(value, (counts.get(value) || 0) + 1)
  })
  Array.from(counts.entries())
    .filter(([, count]) => count > 1)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8)
    .forEach(([value, count]) => {
      candidates.push({ value, weight: weight + count * 2, source: 'frequency' })
    })
}

function splitPhrases(text) {
  return String(text || '')
    .replace(/[【】「」『』《》]/g, ' ')
    .split(/[\n\r\t，。！？；：、,.!?;:|/\\()[\]{}<>]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function cleanFileName(name) {
  return String(name || '')
    .replace(/\.(pdf|docx?|pptx?)$/i, '')
    .replace(/\s+/g, ' ')
}

function cleanCandidate(value) {
  let text = String(value || '')
    .replace(/\.(pdf|docx?|pptx?)$/i, '')
    .replace(/https?:\/\/\S+/ig, ' ')
    .replace(/[（(][^）)]{0,40}[）)]/g, ' ')
    .replace(/\b20\d{6,}\b/g, ' ')
    .replace(/\b\d{6,}\b/g, ' ')
    .replace(/[A-Z]{2,}\d{2,}/ig, ' ')
    .replace(/[_\-—–]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()

  text = text.replace(/^(请)?(帮我|根据|围绕|关于|生成|制作|创建|输出|整理|梳理|设计)+/g, '')
  text = text.replace(/^(一份|一个|一下|有关|关于)+/g, '')
  NOISE_WORDS.forEach(word => {
    text = text.replace(new RegExp(escapeRegExp(word), 'g'), '')
  })
  return text.replace(/\s+/g, '').trim()
}

function trimTopic(value, maxLength) {
  let text = String(value || '').trim()
  if (!text) return ''
  const meaningfulMatch = text.match(/[A-Za-z0-9]*[\u4e00-\u9fa5A-Za-z0-9]{2,}(课程体系|学习路线|知识体系|复习提纲|项目拆解|课程|体系|路线|项目|知识|算法|架构|流程)/)
  if (meaningfulMatch) {
    text = meaningfulMatch[0]
  }
  if (text.length <= maxLength) return text
  return text.slice(0, maxLength)
}

function isUsefulTopic(value) {
  const text = String(value || '').trim()
  if (text.length < 2) return false
  if (GENERIC_CANDIDATES.has(text)) return false
  if (/^\d+$/.test(text)) return false
  if (/^[A-Za-z]{1,2}$/.test(text)) return false
  if (!/[\u4e00-\u9fa5A-Za-z]/.test(text)) return false
  return true
}

function scoreTopic(value, fullText, source) {
  const text = String(value || '')
  let score = Math.min(text.length, 10)
  const occurrences = countOccurrences(fullText, text)
  score += Math.min(occurrences, 8) * 2
  DOMAIN_HINTS.forEach(hint => {
    if (text.includes(hint)) score += 3
  })
  if (source === 'filename' && /[\d]{2,}|[\u4e00-\u9fa5]{2,4}(同学|老师|教授)/.test(text)) {
    score -= 4
  }
  if (/^(请|帮|生成|制作|根据)/.test(text)) score -= 8
  return score
}

function countOccurrences(source, target) {
  const text = String(source || '')
  const word = String(target || '')
  if (!word) return 0
  let count = 0
  let index = text.indexOf(word)
  while (index >= 0) {
    count += 1
    index = text.indexOf(word, index + word.length)
  }
  return count
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
