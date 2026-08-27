import { API_BASE_URL, request } from './request'
import { getToken } from '../utils/auth'

const endpoints = {
  flowchart: '/api/ai/flowchart',
  architecture: '/api/ai/architecture',
  mind_map: '/api/ai/mindmap',
}

const unwrap = (response) => response?.data ?? response ?? {}

export function diagramEndpoint(type, action = '') {
  const base = endpoints[type]
  if (!base) throw new Error('不支持的图谱类型')
  return `${base}${action}`
}

export async function generateDiagram(type, payload) {
  const response = await request({
    url: diagramEndpoint(type, '/generate'),
    method: 'POST',
    data: payload,
  })
  return normalizeDiagram(type, unwrap(response))
}

export async function getDiagramHistory(type) {
  const response = await request({
    url: diagramEndpoint(type, '/history'),
    params: type === 'architecture' ? { page: 1, size: 24 } : undefined,
  })
  const data = unwrap(response)
  const records = Array.isArray(data) ? data : (data.records || [])
  return records.map((record) => normalizeDiagram(type, record))
}

export async function deleteDiagram(type, id) {
  await request({
    url: diagramEndpoint(type, `/${encodeURIComponent(id)}`),
    method: 'DELETE',
  })
}

export async function optimizeMindMap(payload) {
  const response = await request({
    url: diagramEndpoint('mind_map', '/optimize'),
    method: 'POST',
    data: payload,
  })
  return normalizeDiagram('mind_map', unwrap(response))
}

export async function getDiagramDetail(type, id) {
  const response = await request({ url: diagramEndpoint(type, `/${encodeURIComponent(id)}`) })
  return normalizeDiagram(type, unwrap(response))
}

export async function uploadDiagramFile(type, file) {
  if (!file) throw new Error('请选择要导入的文件')
  const body = new FormData()
  body.append('file', file, file.name)
  const token = getToken()
  const response = await fetch(`${API_BASE_URL}${diagramEndpoint(type, '/upload')}`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body,
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok || payload?.code !== 200) {
    throw new Error(payload?.msg || payload?.message || `文件上传失败：${response.status}`)
  }
  return unwrap(payload)
}

export function buildDiagramPayload(type, { content, file, settings }) {
  const common = {
    sourceText: file?.text || file?.sourceText || '',
    sourceFile: file?.sourceFile || file?.url || '',
    fileId: file?.fileId || file?.id || '',
    files: file ? [file] : [],
  }
  if (type === 'flowchart') {
    return {
      description: content.trim(),
      content: content.trim(),
      sceneType: settings.scene,
      processType: settings.scene,
      nodeGranularity: settings.granularity,
      nodeLevel: settings.granularity,
      layoutDirection: settings.direction,
      requestedLayoutDirection: settings.direction,
      decisionMode: settings.decision,
      requestedDecisionMode: settings.decision,
      swimlaneMode: settings.lane,
      requestedSwimlaneMode: settings.lane,
      diagramType: 'FLOWCHART',
      displayItems: ['NODES', 'EDGES', 'LABELS'],
      ...common,
    }
  }
  if (type === 'architecture') {
    return {
      content: content.trim(),
      description: content.trim(),
      systemType: settings.systemType,
      architectureStyle: 'AUTO',
      autoArchitectureLayers: settings.autoLayers,
      architectureLayers: settings.autoLayers ? [] : settings.layers,
      layers: settings.autoLayers ? [] : settings.layers,
      focusContents: settings.focus,
      displayContent: settings.focus,
      relationMode: settings.relation,
      relationType: settings.relation,
      requestedRelationMode: settings.relation,
      hierarchyMode: 'STRUCTURED',
      ...common,
    }
  }
  return {
    topic: content.trim() || settings.centerTopic.trim(),
    centerTopic: settings.centerTopic.trim(),
    centerTopicMode: settings.centerTopic.trim() ? 'USER_DEFINED' : 'AUTO',
    depth: settings.depth,
    structure: settings.structure,
    detail: settings.detail,
    ...common,
  }
}

export function normalizeDiagram(type, result = {}) {
  if (type === 'flowchart') {
    return {
      ...result,
      id: String(result.id || ''),
      title: result.title || 'AI 流程图',
      type: result.type || 'FLOWCHART',
      sceneType: result.sceneType || result.processType || 'AUTO',
      nodeGranularity: result.nodeGranularity || result.nodeLevel || 'AUTO',
      requestedLayoutDirection: normalizeFlowDirection(result.requestedLayoutDirection || result.layoutDirection || result.direction),
      resolvedLayoutDirection: normalizeFlowDirection(result.resolvedLayoutDirection || result.requestedLayoutDirection || result.layoutDirection || result.direction),
      requestedDecisionMode: result.requestedDecisionMode || result.decisionMode || 'AUTO',
      resolvedDecisionMode: result.resolvedDecisionMode || (Array.isArray(result.nodes) && result.nodes.some((node) => String(node.type || '').toLowerCase().includes('decision')) ? 'ENABLED' : 'DISABLED'),
      requestedSwimlaneMode: result.requestedSwimlaneMode || result.swimlaneMode || result.swimlane || 'AUTO',
      resolvedSwimlaneMode: result.resolvedSwimlaneMode || (Array.isArray(result.lanes) && result.lanes.length ? 'ROLE' : 'NONE'),
      content: result.content || result.description || '',
      files: Array.isArray(result.files) ? result.files : [],
      sourceText: result.sourceText || '',
      sourceFile: result.sourceFile || '',
      fileId: result.fileId || '',
      fileSummary: result.fileSummary || result.summary || '',
      nodes: Array.isArray(result.nodes) ? result.nodes.map((node, index) => ({
        ...node,
        id: String(node.id || node.nodeId || index + 1),
        label: node.label || node.name || node.description || '流程步骤',
        type: String(node.type || 'process').toLowerCase(),
      })) : [],
      edges: Array.isArray(result.edges) ? result.edges.map((edge) => ({
        ...edge,
        source: String(edge.source || edge.from || ''),
        target: String(edge.target || edge.to || ''),
        label: edge.label || edge.condition || '',
      })) : [],
      lanes: Array.isArray(result.lanes) ? result.lanes.map((lane, index) => ({
        ...lane,
        id: String(lane.id || lane.laneId || index + 1),
        label: lane.label || lane.name || `泳道 ${index + 1}`,
        nodes: Array.isArray(lane.nodes) ? lane.nodes.map((node) => String(node?.id || node?.nodeId || node)) : [],
      })) : [],
      createTime: result.createTime || result.createdAt || '',
    }
  }
  if (type === 'architecture') {
    const rawLayers = Array.isArray(result.layers) ? result.layers : []
    const nodes = Array.isArray(result.nodes) ? result.nodes : []
    const layers = rawLayers.length ? rawLayers : groupArchitectureNodes(nodes)
    return {
      ...result,
      id: String(result.id || ''),
      title: result.title || 'AI 架构图',
      content: result.content || result.description || '',
      files: Array.isArray(result.files) ? result.files : [],
      sourceText: result.sourceText || '',
      sourceFile: result.sourceFile || '',
      fileId: result.fileId || '',
      fileSummary: result.fileSummary || result.summary || '',
      systemType: result.systemType || 'WEB',
      autoArchitectureLayers: result.autoArchitectureLayers !== false,
      architectureLayers: Array.isArray(result.architectureLayers) ? result.architectureLayers : [],
      focusContents: Array.isArray(result.focusContents) ? result.focusContents : [],
      requestedRelationMode: normalizeRelationMode(result.requestedRelationMode || result.relationMode || result.relationType),
      resolvedRelationMode: normalizeRelationMode(result.resolvedRelationMode || result.relationMode || result.relationType || 'MODULE'),
      layers: layers.map((layer, index) => ({
        ...layer,
        key: layer.key || `layer_${index}`,
        name: layer.name || layer.label || `架构层 ${index + 1}`,
        groups: Array.isArray(layer.groups) ? layer.groups.map((group, groupIndex) => ({
          ...group,
          id: String(group.id || group.key || groupIndex),
          name: group.name || group.label || `模块组 ${groupIndex + 1}`,
          nodes: Array.isArray(group.nodes) ? group.nodes : (group.children || []),
        })) : [],
        nodes: Array.isArray(layer.nodes)
          ? layer.nodes
          : (Array.isArray(layer.groups) ? layer.groups.flatMap((group) => group.nodes || group.children || []) : []),
      })),
      thirdParty: Array.isArray(result.thirdParty) ? result.thirdParty : [],
      features: Array.isArray(result.features) ? result.features : [],
      edges: Array.isArray(result.edges) ? result.edges : [],
      createTime: result.createTime || result.createdAt || '',
    }
  }
  return {
    ...result,
    id: String(result.id || ''),
    title: result.title || result.resolvedCenterTopic || 'AI 思维导图',
    requestedCenterTopicMode: result.requestedCenterTopicMode || result.centerTopicMode || 'AUTO',
    resolvedCenterTopic: result.resolvedCenterTopic || result.centerTopic || result.title || '',
    requestedDepth: result.requestedDepth || result.depth || 'auto',
    resolvedDepth: result.resolvedDepth || '',
    requestedStructure: result.requestedStructure || result.structure || 'auto',
    resolvedStructure: result.resolvedStructure || result.structureType || '',
    detailLevel: result.detailLevel || result.detail || 'standard',
    content: result.content || result.topic || result.description || '',
    sourceText: result.sourceText || '',
    sourceFile: result.sourceFile || '',
    fileId: result.fileId || '',
    fileSummary: result.fileSummary || result.summary || '',
    nodes: Array.isArray(result.nodes) ? result.nodes : [],
    createTime: result.createTime || result.createdAt || '',
  }
}

function normalizeFlowDirection(value = 'VERTICAL') {
  const text = String(value || 'VERTICAL').toUpperCase()
  return text.includes('HORIZONTAL') || text.includes('LANDSCAPE') || text.includes('横') ? 'HORIZONTAL' : 'VERTICAL'
}

function normalizeRelationMode(value = 'AUTO') {
  const text = String(value || 'AUTO').trim().toUpperCase()
  if (text === 'DATA' || text === 'DATAFLOW') return 'DATA_FLOW'
  if (['CALL_CHAIN', 'CALLING', 'DEPENDENCY'].includes(text)) return 'CALL'
  return ['AUTO', 'MODULE', 'DATA_FLOW', 'CALL'].includes(text) ? text : 'AUTO'
}

function groupArchitectureNodes(nodes) {
  const definitions = [
    ['client', '用户层', ['client', 'user', 'frontend', 'web', 'mobile']],
    ['application', '应用层', ['application', 'controller', 'gateway', 'business']],
    ['service', '服务层', ['service', 'core', 'backend', 'domain']],
    ['data', '数据层', ['data', 'database', 'storage', 'cache', 'redis', 'mysql']],
  ]
  return definitions.map(([key, name, aliases]) => ({
    key,
    name,
    nodes: nodes.filter((node) => aliases.some((alias) => String(node.layer || node.type || '').toLowerCase().includes(alias))),
  })).filter((layer) => layer.nodes.length)
}
