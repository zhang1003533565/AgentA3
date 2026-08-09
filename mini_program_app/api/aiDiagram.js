import { request } from '../utils/request.js'
import { BASE_URL } from '../utils/config.js'
import { getToken } from '../utils/storage.js'

export const AI_MINDMAP_ENDPOINTS = {
  generate: '/api/ai/mindmap/generate',
  optimize: '/api/ai/mindmap/optimize',
  upload: '/api/ai/mindmap/upload',
  history: '/api/ai/mindmap/history',
  detail: id => `/api/ai/mindmap/${encodeURIComponent(id)}`
}

export const AI_FLOWCHART_ENDPOINTS = {
  generate: '/api/ai/flowchart/generate',
  upload: '/api/ai/flowchart/upload',
  history: '/api/ai/flowchart/history',
  detail: id => `/api/ai/flowchart/${encodeURIComponent(id)}`
}

export function buildMindmapPayload({
  topic = '',
  centerTopic = '',
  depth = 'auto',
  structure = '知识梳理',
  detail = 'standard',
  sourceText = '',
  sourceFile = '',
  fileId = ''
} = {}) {
  const finalTopic = String(topic || centerTopic || '').trim()
  const finalCenterTopic = String(centerTopic || '').trim()
  return {
    topic: finalTopic,
    centerTopic: finalCenterTopic,
    depth: String(depth || 'auto'),
    structure: String(structure || '知识梳理'),
    detail: String(detail || 'standard'),
    sourceText: String(sourceText || '').trim(),
    sourceFile: String(sourceFile || '').trim(),
    fileId: String(fileId || '').trim()
  }
}

export async function generateMindmap(payload = {}) {
  const response = await request({
    url: AI_MINDMAP_ENDPOINTS.generate,
    method: 'POST',
    data: payload,
    showError: false,
    timeout: 120000
  })
  return normalizeMindmap(response?.data || response)
}

export async function optimizeMindmap(payload = {}) {
  const response = await request({
    url: AI_MINDMAP_ENDPOINTS.optimize,
    method: 'POST',
    data: payload,
    showError: false,
    timeout: 120000
  })
  return normalizeMindmap(response?.data || response)
}

export async function getMindmapHistory() {
  const response = await request({
    url: AI_MINDMAP_ENDPOINTS.history,
    method: 'GET',
    showError: false
  })
  return Array.isArray(response?.data) ? response.data : []
}

export async function getMindmapDetail(id) {
  const response = await request({
    url: AI_MINDMAP_ENDPOINTS.detail(id),
    method: 'GET',
    showError: false
  })
  return normalizeMindmap(response?.data || response)
}

export function uploadMindmapFile(filePath, fileName = '') {
  const token = getToken()
  const uploadUrl = `${BASE_URL}${AI_MINDMAP_ENDPOINTS.upload}`
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: uploadUrl,
      filePath,
      name: 'file',
      fileName,
      header: {
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: (res) => {
        let body = res.data
        try {
          body = typeof body === 'string' ? JSON.parse(body) : body
        } catch (error) {
          reject(new Error('文件上传响应解析失败'))
          return
        }
        if (res.statusCode >= 200 && res.statusCode < 300 && body?.code === 200) {
          resolve(body.data)
          return
        }
        reject({
          code: body?.code,
          statusCode: res.statusCode,
          msg: body?.msg || body?.message || '文件上传失败'
        })
      },
      fail: (error) => {
        reject(error)
      }
    })
  })
}

export function normalizeMindmap(result = {}) {
  return {
    id: String(result.id || ''),
    title: result.title || 'AI 思维导图',
    nodes: Array.isArray(result.nodes) ? result.nodes : [],
    createTime: result.createTime || result.createdAt || ''
  }
}

export function getErrorMessage(error, fallback = '生成失败') {
  const candidates = [
    error?.msg,
    error?.message,
    error?.detail,
    error?.data?.msg,
    error?.data?.message,
    error?.data?.detail
  ]
  const useful = candidates.find(item => {
    const text = String(item || '').trim()
    return text && text !== 'request:ok'
  })
  if (useful) return useful
  if (error?.statusCode === 401 || error?.code === 401) return '请先登录'
  if (error?.statusCode === 404) return '思维导图接口不可用，请确认后端已启动并更新到最新代码'
  if (error?.statusCode >= 500) return '服务器处理失败，请查看后端日志'
  if (error?.errMsg && error.errMsg !== 'request:ok') return error.errMsg
  return fallback
}

export async function generateFlowchart(payload = {}) {
  const response = await request({
    url: AI_FLOWCHART_ENDPOINTS.generate,
    method: 'POST',
    data: payload,
    showError: false,
    timeout: 120000
  })
  return normalizeFlowchart(response?.data || response)
}

export async function getFlowchartHistory() {
  const response = await request({
    url: AI_FLOWCHART_ENDPOINTS.history,
    method: 'GET',
    showError: false
  })
  return Array.isArray(response?.data) ? response.data : []
}

export async function getFlowchartDetail(id) {
  const response = await request({
    url: AI_FLOWCHART_ENDPOINTS.detail(id),
    method: 'GET',
    showError: false
  })
  return normalizeFlowchart(response?.data || response)
}

export function uploadFlowchartFile(filePath, fileName = '') {
  const token = getToken()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}${AI_FLOWCHART_ENDPOINTS.upload}`,
      filePath,
      name: 'file',
      fileName,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        let body = res.data
        try {
          body = typeof body === 'string' ? JSON.parse(body) : body
        } catch (error) {
          reject(new Error('文件上传响应解析失败'))
          return
        }
        if (res.statusCode >= 200 && res.statusCode < 300 && body?.code === 200) {
          resolve(body.data)
          return
        }
        reject({
          code: body?.code,
          statusCode: res.statusCode,
          msg: body?.msg || body?.message || '文件上传失败'
        })
      },
      fail: reject
    })
  })
}

export function normalizeFlowchart(result = {}) {
  return {
    id: String(result.id || ''),
    title: result.title || 'AI 流程图',
    type: result.type || 'FLOWCHART',
    sceneType: result.sceneType || result.processType || 'ADMIN',
    nodeGranularity: result.nodeGranularity || result.nodeLevel || 'AUTO',
    requestedDecisionMode: result.requestedDecisionMode || result.decisionMode || 'AUTO',
    resolvedDecisionMode: result.resolvedDecisionMode || (Array.isArray(result.nodes) && result.nodes.some(node => String(node.type || '').toLowerCase() === 'decision') ? 'ENABLED' : 'DISABLED'),
    requestedSwimlaneMode: result.requestedSwimlaneMode || result.swimlaneMode || result.swimlane || 'AUTO',
    resolvedSwimlaneMode: result.resolvedSwimlaneMode || (Array.isArray(result.lanes) && result.lanes.length ? 'ROLE' : 'NONE'),
    lanes: Array.isArray(result.lanes) ? result.lanes : [],
    nodes: Array.isArray(result.nodes) ? result.nodes.map(normalizeFlowNode) : [],
    edges: Array.isArray(result.edges) ? result.edges.map((edge, index) => normalizeFlowEdge(edge, index)) : [],
    createTime: result.createTime || ''
  }
}

function normalizeFlowNode(node = {}) {
  const rawType = String(node.type || 'process').toLowerCase()
  const type = rawType.includes('start')
    ? 'start'
    : rawType.includes('end')
      ? 'end'
      : rawType.includes('decision') || rawType.includes('judge')
        ? 'decision'
        : 'process'
  const label = node.label || node.name || node.description || '流程步骤'
  return {
    ...node,
    id: String(node.id || label),
    type,
    label,
    name: node.name || label,
    laneId: node.laneId || node.lane || ''
  }
}

function normalizeFlowEdge(edge = {}, index = 0) {
  const label = edge.label || edge.condition || ''
  return {
    ...edge,
    id: edge.id || `e${index + 1}`,
    source: String(edge.source || ''),
    target: String(edge.target || ''),
    label,
    type: edge.type || (label ? 'branch' : 'normal')
  }
}
