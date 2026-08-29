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
