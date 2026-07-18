const RESOURCE_GENERATE = '/subpackage_learning/resourceGenerate/resourceGenerate'
const CONVERSATION = '/subpackage_ai/aiConversation/aiConversation'

function resourceRoute(resourceType, title) {
  const topic = title || 'Python 个性化学习'
  return `${RESOURCE_GENERATE}?resourceType=${encodeURIComponent(resourceType)}&topic=${encodeURIComponent(topic)}`
}

function conversationRoute(tool) {
  const name = String(tool?.name || 'AI 工具').trim() || 'AI 工具'
  const description = String(tool?.desc || '').trim()
  const intent = `请使用真实可用的能力帮助我完成「${name}」${description ? `：${description}` : ''}。请先确认必要信息，再给出可验证的结果。`
  return `${CONVERSATION}?prefill=${encodeURIComponent(intent)}`
}

export function resolveAiToolDestination(tool = {}) {
  const name = String(tool?.name || '').trim()
  if (name === '智能写作') return '/subpackage_ai/smartWriting/smartWriting'
  if (name === 'AI对话') return CONVERSATION
  if (name === 'AI玩图' || name === '文生图') return '/subpackage_ai/imageGenerate/imageGenerate'
  if (['PPT生成', 'AIPPT', 'PPT大纲'].includes(name)) return resourceRoute('presentation', name)
  if (name === '思维导图') return resourceRoute('mind_map', name)
  if (['试卷生成', '学科出题', '考研题目'].includes(name)) return resourceRoute('practice_set', name)
  if (name === 'Python代码实验') return resourceRoute('code_lab', name)
  if (name === '学习计划') return '/subpackage_learning/learningPath/learningPath'
  if (['活动图', '架构图', '流程图'].includes(name)) return conversationRoute(tool)
  return conversationRoute(tool)
}
