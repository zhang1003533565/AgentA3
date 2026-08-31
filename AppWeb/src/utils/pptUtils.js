export function extractPptTaskFromResponse(response) {
  if (!response || typeof response !== 'object') return null
  const metadata = response.metadata || response.retrievalMeta || {}
  const pptTask = metadata.pptTask
  if (!pptTask || typeof pptTask !== 'object' || !pptTask.taskId) return null
  return pptTask
}

export function extractPptTemplateSelectionFromResponse(response) {
  if (!response || typeof response !== 'object') return null
  const metadata = response.metadata || response.retrievalMeta || {}
  const answerType = String(response.answerType || metadata.answerType || '').trim()
  if (answerType !== 'ppt_template_selection') return null
  const templates = Array.isArray(metadata.pptTemplateCatalog) ? metadata.pptTemplateCatalog : []
  const draft = metadata.pptGenerationDraft && typeof metadata.pptGenerationDraft === 'object'
    ? metadata.pptGenerationDraft
    : null
  if (!templates.length) return null
  return { templates, draft }
}

export const PPT_TERMINAL_STATUSES = new Set(['completed', 'failed', 'cancelled', 'timed_out'])

export function pptStageLabel(stage) {
  const value = String(stage || '').trim().toLowerCase()
  const labels = {
    queued: '排队中',
    outline: '生成大纲',
    slides: '生成逐页内容',
    writing: '生成逐页内容',
    structuring: '匹配版式',
    preparing: '整理内容',
    rendering: '渲染幻灯片',
    exporting: '导出 PPTX',
    visuals: '生成配图',
    completed: '已完成',
    failed: '失败',
    cancelled: '已取消',
  }
  return labels[value] || stage || '处理中'
}
