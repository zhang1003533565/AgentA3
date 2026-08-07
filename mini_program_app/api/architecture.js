import { request } from '../utils/request.js'
import { BASE_URL } from '../utils/config.js'
import { getToken } from '../utils/storage.js'

/**
 * AI 架构图相关接口封装。
 * 对应后端 /api/ai/architecture/* 四个端点（generate/upload/history/detail）。
 */

export const AI_ARCHITECTURE_ENDPOINTS = {
  generate: '/api/ai/architecture/generate',
  upload: '/api/ai/architecture/upload',
  history: '/api/ai/architecture/history',
  detail: id => `/api/ai/architecture/${encodeURIComponent(id)}`
}

// POST /api/ai/architecture/generate
// 请求体字段与后端 ArchitectureDTO.GenerateRequest 对齐（camelCase）
export function buildArchitecturePayload({
  description = '',
  systemType = '',
  architectureStyle = 'AUTO',
  layers = [],
  displayContent = [],
  relationType = 'AUTO',
  sourceText = '',
  fileId = '',
  sourceFile = ''
} = {}) {
  return {
    description: String(description || '').trim(),
    systemType,
    architectureStyle,
    layers,
    displayContent,
    relationType,
    sourceText: String(sourceText || '').trim(),
    fileId: String(fileId || '').trim(),
    sourceFile: String(sourceFile || '').trim()
  }
}

/**
 * 上传文档并解析为文本（供 AI 生成架构图使用）。
 * 对应后端 POST /api/ai/architecture/upload，返回 { fileId, fileName, sourceFile, text }。
 */
export function uploadArchitectureFile(filePath, fileName = '') {
  const token = getToken()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}${AI_ARCHITECTURE_ENDPOINTS.upload}`,
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

/**
 * 调用后端生成架构图。
 * @returns {Promise<{id,title,style,nodes,edges,createTime}>}
 */
export async function generateArchitecture(payload = {}) {
  const response = await request({
    url: '/api/ai/architecture/generate',
    method: 'POST',
    data: payload,
    showError: false
  })
  return normalizeArchitectureResult(response?.data || response)
}

/**
 * 分页查询历史记录。
 * @returns {Promise<{records,total,page,size}>}
 */
export async function getArchitectureHistory({ page = 1, size = 20 } = {}) {
  const response = await request({
    url: '/api/ai/architecture/history',
    method: 'GET',
    data: { page, size },
    showError: false
  })
  const data = response?.data || {}
  return {
    records: Array.isArray(data.records) ? data.records : [],
    total: Number(data.total) || 0,
    page: Number(data.page) || page,
    size: Number(data.size) || size
  }
}

/**
 * 查询单条架构图详情。
 * @param {number|string} id 记录ID
 * @returns {Promise<{id,title,style,nodes,edges,createTime}>}
 */
export async function getArchitectureDetail(id) {
  const response = await request({
    url: `/api/ai/architecture/${encodeURIComponent(id)}`,
    method: 'GET',
    showError: false
  })
  return normalizeArchitectureResult(response?.data || response)
}

/**
 * 归一化后端返回的架构数据，保证前端字段稳定可用。
 * 支持三种数据形态：
 *   1) 分层结构：{ layers: [...], thirdParty: [...], features: [...] } —— AI 按新规范返回
 *   2) 扁平结构：{ nodes: [...], edges: [...] } —— 自动按 type 归类到对应层
 *   3) 单层结构：仅 nodes 数组 —— 自动按 type 归类
 */
export function normalizeArchitectureResult(result = {}) {
  const base = {
    id: result.id != null ? result.id : `arch_${Date.now()}`,
    title: result.title || 'AI 架构图',
    style: result.style || '',
    subtitle: result.subtitle || '分层解耦 · 高可用 · 易扩展',
    createTime: result.createTime || '',
  }
  // 形态 1：分层结构（AI 新规范）
  if (result.layers && Array.isArray(result.layers) && result.layers.length) {
    return {
      ...base,
      layers: result.layers,
      thirdParty: Array.isArray(result.thirdParty) ? result.thirdParty : [],
      features: Array.isArray(result.features) ? result.features : [],
    }
  }
  // 形态 2/3：扁平 nodes + edges，自动归类
  const grouped = groupNodesByLayer(
    Array.isArray(result.nodes) ? result.nodes : [],
    result.title,
    result.style,
    result.subtitle
  )
  return {
    ...base,
    ...grouped,
  }
}

// 标准层定义（按从上到下）
const LAYER_DEFS = [
  { key: 'client',  name: '客户端层',   color: '#4D6BFE', bg: '#EEF0FF', border: '#C7D2FE', iconKey: 'monitor', types: ['frontend', 'client', 'web', 'app', 'mini_program'] },
  { key: 'gateway', name: '接入层',     color: '#8B5CF6', bg: '#F5F3FF', border: '#DDD6FE', iconKey: 'nginx',   types: ['gateway', 'nginx', 'lb', 'load_balancer'] },
  { key: 'service', name: '服务层',     color: '#10B981', bg: '#ECFDF5', border: '#A7F3D0', iconKey: 'shop',    types: ['service', 'business'] },
  { key: 'dao',     name: '数据访问层', color: '#3B82F6', bg: '#EFF6FF', border: '#BFDBFE', iconKey: 'database',types: ['orm', 'dao'] },
  { key: 'storage', name: '数据存储层', color: '#EC4899', bg: '#FDF2F8', border: '#FBCFE8', iconKey: 'database',types: ['database', 'cache', 'search'] },
  { key: 'infra',   name: '基础设施层', color: '#F59E0B', bg: '#FFFBEB', border: '#FDE68A', iconKey: 'server',  types: ['infrastructure', 'monitor', 'log', 'queue', 'message_queue', 'third_party', 'devops'] },
]

const DEFAULT_THIRD_PARTY = [
  { name: '短信服务', description: '验证码、通知', iconKey: 'sms' },
  { name: '对象存储', description: '图片、文件存储', iconKey: 'oss' },
  { name: '支付服务', description: '微信支付、支付宝', iconKey: 'payment' },
  { name: '邮件服务', description: '邮件通知', iconKey: 'mail' },
]

const DEFAULT_FEATURES = ['高可用', '易扩展', '高性能', '安全可靠', '可维护']

function groupNodesByLayer(nodes = [], title = '', style = '', subtitle = '') {
  const used = new Set()
  const layers = LAYER_DEFS.map(cfg => {
    const matched = nodes.filter(n => {
      if (!n || used.has(n.id)) return false
      const type = (n.type || '').toLowerCase()
      return cfg.types.includes(type)
    })
    matched.forEach(n => used.add(n.id))
    return {
      ...cfg,
      nodes: matched.map(n => ({
        name: n.name,
        description: n.description || '',
        tech: Array.isArray(n.technologies) ? n.technologies : (Array.isArray(n.tech) ? n.tech : []),
        iconKey: n.iconKey || cfg.iconKey,
      }))
    }
  })
  return {
    title: title || 'AI 架构图',
    style,
    subtitle: subtitle || '分层解耦 · 高可用 · 易扩展',
    layers,
    thirdParty: DEFAULT_THIRD_PARTY,
    features: DEFAULT_FEATURES,
  }
}

/**
 * 本地 mock 兜底：后端不可用时返回一份示例架构数据，保证前端流程可走通。
 */
export function mockGenerateArchitecture(payload = {}) {
  const title = payload.description ? `${payload.description.slice(0, 12)}架构` : '示例架构图'
  return Promise.resolve(normalizeArchitectureResult({
    id: `mock_arch_${Date.now()}`,
    title,
    style: '前后端分离',
    nodes: [
      { id: 'frontend', name: '前端应用', type: 'frontend', children: [{ name: 'Vue3' }, { name: 'UniApp' }] },
      { id: 'gateway', name: 'API 网关', type: 'gateway', children: [] },
      { id: 'user_service', name: '用户服务', type: 'service', children: [] },
      { id: 'product_service', name: '商品服务', type: 'service', children: [] },
      { id: 'order_service', name: '订单服务', type: 'service', children: [] },
      { id: 'mysql', name: 'MySQL', type: 'database', children: [] },
      { id: 'redis', name: 'Redis', type: 'cache', children: [] }
    ],
    edges: [
      { source: 'frontend', target: 'gateway', label: 'HTTP 请求' },
      { source: 'gateway', target: 'user_service', label: '路由' },
      { source: 'gateway', target: 'product_service', label: '路由' },
      { source: 'gateway', target: 'order_service', label: '路由' },
      { source: 'user_service', target: 'mysql', label: '读写' },
      { source: 'product_service', target: 'mysql', label: '读写' },
      { source: 'order_service', target: 'mysql', label: '读写' },
      { source: 'user_service', target: 'redis', label: '缓存' }
    ],
    createTime: new Date().toISOString()
  }))
}
