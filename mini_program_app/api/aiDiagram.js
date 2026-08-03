import { request } from '../utils/request.js'

// Frontend contract for AI diagram generation.
// Mind map is wired to backend; other diagram endpoints are reserved.

export const AI_DIAGRAM_ENDPOINTS = {
  mindmap: {
    method: 'POST',
    url: '/api/app/ai/diagrams/mindmap'
  },
  activity: {
    method: 'POST',
    url: '/api/app/ai/diagrams/activity'
  },
  flowchart: {
    method: 'POST',
    url: '/api/app/ai/diagrams/flowchart'
  },
  architecture: {
    method: 'POST',
    url: '/api/app/ai/diagrams/architecture'
  }
}

export function getAiDiagramEndpoint(type) {
  return AI_DIAGRAM_ENDPOINTS[type] || null
}

export function buildMindmapPayload({
  prompt,
  centerTopic = '',
  depth = 'auto',
  structure = 'auto',
  expand = 'standard',
  sourceFileUrl = ''
} = {}) {
  const finalPrompt = String(prompt || centerTopic || '').trim()
  return {
    type: 'mindmap',
    prompt: finalPrompt,
    centerTopic: String(centerTopic || finalPrompt).trim(),
    options: {
      depth,
      structure,
      expand
    },
    sourceFileUrl,
    output: {
      format: 'image',
      imageType: 'svg',
      includeMermaid: true
    }
  }
}

export async function generateMindmap(payload = {}) {
  const endpoint = getAiDiagramEndpoint('mindmap')
  const response = await request({
    url: endpoint.url,
    method: endpoint.method,
    data: payload,
    showError: false
  })
  return normalizeGenerateResult(response?.data || response)
}

export function normalizeGenerateResult(result = {}) {
  return {
    id: String(result.id || `mindmap_${Date.now()}`),
    type: result.type || 'mindmap',
    title: result.title || 'AI 思维导图',
    status: result.status || 'success',
    mermaid: result.mermaid || '',
    imageUrl: result.imageUrl || '',
    outline: Array.isArray(result.outline) ? result.outline : [],
    createdAt: result.createdAt || new Date().toISOString(),
    metadata: result.metadata || {}
  }
}

export function mockGenerateMindmap(payload = {}) {
  const title = payload.centerTopic || payload.prompt || 'AI 思维导图'
  return Promise.resolve(normalizeGenerateResult({
    id: `mock_mindmap_${Date.now()}`,
    type: 'mindmap',
    title,
    status: 'success',
    mermaid: [
      'mindmap',
      `  root((${title}))`,
      '    基础课程',
      '      程序设计',
      '      计算机导论',
      '    核心能力',
      '      数据结构',
      '      操作系统',
      '    应用方向',
      '      Web 开发',
      '      人工智能'
    ].join('\n'),
    imageUrl: '/static/mock/mindmap-generated-sample.svg',
    outline: [
      { title: '基础课程', children: ['程序设计', '计算机导论'] },
      { title: '核心能力', children: ['数据结构', '操作系统'] },
      { title: '应用方向', children: ['Web 开发', '人工智能'] }
    ],
    createdAt: new Date().toISOString()
  }))
}
