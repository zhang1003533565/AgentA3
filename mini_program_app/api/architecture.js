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
  content = '',
  files = [],
  description = '',
  systemType = '',
  architectureStyle = 'AUTO',
  autoArchitectureLayers = true,
  architectureLayers = [],
  layers = [],
  focusContents = [],
  displayContent = [],
  relationMode = 'AUTO',
  relationType = 'AUTO',
  hierarchyMode = 'STRUCTURED',
  sourceText = '',
  fileId = '',
  sourceFile = ''
} = {}) {
  const normalizedArchitectureLayers = Array.isArray(architectureLayers) && architectureLayers.length
    ? architectureLayers
    : (Array.isArray(layers) ? layers : [])
  const normalizedFocusContents = Array.isArray(focusContents) && focusContents.length
    ? focusContents
    : (Array.isArray(displayContent) ? displayContent : [])
  const normalizedRelationMode = normalizeRelationMode(relationMode || relationType || 'AUTO')
  const normalizedSourceFile = typeof sourceFile === 'string'
    ? sourceFile
    : (sourceFile?.url || sourceFile?.sourceFile || sourceFile?.fileUrl || '')

  return {
    content: String(content || '').trim(),
    files: Array.isArray(files) ? files : [],
    description: String(description || '').trim(),
    systemType,
    architectureStyle,
    autoArchitectureLayers: Boolean(autoArchitectureLayers),
    architectureLayers: normalizedArchitectureLayers,
    layers: normalizedArchitectureLayers,
    focusContents: normalizedFocusContents,
    displayContent: normalizedFocusContents,
    relationMode: normalizedRelationMode,
    relationType: normalizedRelationMode,
    hierarchyMode: normalizeHierarchyMode(hierarchyMode),
    sourceText: String(sourceText || '').trim(),
    fileId: String(fileId || '').trim(),
    sourceFile: String(normalizedSourceFile || '').trim()
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
  const requestedRelationMode = normalizeRelationMode(
    result.requestedRelationMode || result.relationMode || result.relationType || result.requestedRelationType || 'AUTO'
  )
  const resolvedRelationMode = normalizeRelationMode(
    result.resolvedRelationMode ||
      result.resolvedRelationType ||
      (requestedRelationMode === 'AUTO' ? result.relationMode : requestedRelationMode) ||
      'MODULE'
  )
  const requestedHierarchyMode = normalizeHierarchyMode(result.requestedHierarchyMode || result.hierarchyMode || 'STRUCTURED')
  const resolvedHierarchyMode = normalizeHierarchyMode(result.resolvedHierarchyMode || requestedHierarchyMode)
  const base = {
    id: result.id != null ? result.id : `arch_${Date.now()}`,
    title: result.title || 'AI 架构图',
    style: result.style || '',
    subtitle: result.subtitle || '分层解耦 · 高可用 · 易扩展',
    createTime: result.createTime || '',
    systemType: result.systemType || 'WEB',
    autoArchitectureLayers: result.autoArchitectureLayers !== false,
    architectureLayers: Array.isArray(result.architectureLayers) ? result.architectureLayers : [],
    focusContents: Array.isArray(result.focusContents) ? result.focusContents : [],
    relationMode: resolvedRelationMode,
    requestedRelationMode,
    resolvedRelationMode,
    requestedHierarchyMode,
    resolvedHierarchyMode,
  }
  // 形态 1：分层结构（AI 新规范）
  if (result.layers && Array.isArray(result.layers) && result.layers.length) {
    const normalizedLayers = normalizeLayers(result.layers)
    const normalizedNodes = flattenLayersToNodes(normalizedLayers)
    return {
      ...base,
      layers: normalizedLayers,
      thirdParty: Array.isArray(result.thirdParty) ? result.thirdParty : [],
      features: Array.isArray(result.features) ? result.features : [],
      nodes: normalizedNodes,
      edges: normalizeEdges(result.edges, resolvedRelationMode),
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
    nodes: flattenLayersToNodes(grouped.layers || []),
    edges: normalizeEdges(result.edges, resolvedRelationMode),
  }
}

export function normalizeRelationMode(value = 'AUTO') {
  const text = String(value || 'AUTO').trim().toUpperCase()
  if (text === 'DATA' || text === 'DATAFLOW') return 'DATA_FLOW'
  if (text === 'CALL_CHAIN' || text === 'CALLING' || text === 'DEPENDENCY') return 'CALL'
  if (['AUTO', 'MODULE', 'DATA_FLOW', 'CALL'].includes(text)) return text
  return 'AUTO'
}

export function normalizeHierarchyMode(value = 'STRUCTURED') {
  const text = String(value || 'STRUCTURED').trim().toUpperCase()
  if (text === 'AUTO' || text === 'STRUCTURED') return text
  return 'STRUCTURED'
}

function normalizeEdges(edges = [], mode = 'MODULE') {
  if (!Array.isArray(edges)) return []
  const defaultType = mode === 'DATA_FLOW' ? 'dataFlow' : mode === 'CALL' ? 'call' : 'structural'
  const defaultDirection = mode === 'MODULE' ? 'none' : 'forward'
  return edges.map(edge => ({
    ...edge,
    source: edge.source || edge.from || '',
    target: edge.target || edge.to || '',
    type: defaultType,
    label: edge.label || '',
    direction: defaultDirection,
  }))
}

function createIdFactory() {
  const used = new Set()
  return (seed, fallback) => {
    const base = slugify(seed || fallback, fallback)
    let id = base
    let index = 2
    while (used.has(id)) {
      id = `${base}_${index}`
      index += 1
    }
    used.add(id)
    return id
  }
}

function slugify(value, fallback = 'node') {
  const text = String(value || '')
    .trim()
    .replace(/[^0-9A-Za-z_\u4e00-\u9fff]+/g, '_')
    .replace(/^_+|_+$/g, '')
  return text || fallback
}

function normalizeTech(value) {
  if (!Array.isArray(value)) return []
  return value.map(item => String(item || '').trim()).filter(Boolean)
}

function normalizeLayerNode(rawNode = {}, layer = {}, index = 0, createId, { groupId = '', parentId = '', level = 1 } = {}) {
  const name = String(rawNode.name || rawNode.label || '').trim()
  if (!name) return null
  const id = createId(rawNode.id || rawNode.nodeId || name, `${layer.key || 'layer'}_${index + 1}`)
  const node = {
    ...rawNode,
    id,
    name,
    type: String(rawNode.type || layer.key || 'node'),
    layer: String(rawNode.layer || layer.key || ''),
    description: String(rawNode.description || rawNode.desc || ''),
    tech: normalizeTech(rawNode.tech || rawNode.technologies || []),
    iconKey: rawNode.iconKey || layer.iconKey || 'settings',
    level,
  }
  if (groupId) node.groupId = groupId
  if (parentId) node.parentId = parentId

  const rawChildren = rawNode.children || rawNode.childNodes || rawNode.components || rawNode.submodules || []
  const children = Array.isArray(rawChildren)
    ? rawChildren
        .map((child, childIndex) => normalizeLayerNode(child, layer, childIndex, createId, {
          groupId,
          parentId: id,
          level: level + 1,
        }))
        .filter(Boolean)
    : []
  if (children.length) node.children = children
  else delete node.children
  return node
}

function normalizeLayerGroup(rawGroup = {}, layer = {}, index = 0, createId) {
  const name = String(rawGroup.name || rawGroup.label || '').trim()
  if (!name) return null
  const id = createId(rawGroup.id || rawGroup.groupId || name, `${layer.key || 'layer'}_group_${index + 1}`)
  const rawNodes = rawGroup.nodes || rawGroup.children || rawGroup.items || []
  const nodes = Array.isArray(rawNodes)
    ? rawNodes
        .map((node, nodeIndex) => normalizeLayerNode(node, layer, nodeIndex, createId, { groupId: id, level: 1 }))
        .filter(Boolean)
    : []
  return {
    ...rawGroup,
    id,
    name,
    description: String(rawGroup.description || rawGroup.desc || ''),
    layer: layer.key || '',
    nodes,
  }
}

function normalizeLayers(layers = []) {
  const createId = createIdFactory()
  return layers.map((rawLayer, layerIndex) => {
    const fallback = LAYER_DEFS.find(item => item.key === rawLayer?.key) || LAYER_DEFS[layerIndex] || {}
    const layer = {
      ...fallback,
      ...rawLayer,
      key: rawLayer?.key || fallback.key || `layer_${layerIndex + 1}`,
      name: rawLayer?.name || fallback.name || `架构层 ${layerIndex + 1}`,
      color: rawLayer?.color || fallback.color || '#4D6BFE',
      bg: rawLayer?.bg || fallback.bg || '#EEF0FF',
      border: rawLayer?.border || fallback.border || '#C7D2FE',
      iconKey: rawLayer?.iconKey || fallback.iconKey || 'settings',
    }
    const groups = Array.isArray(rawLayer?.groups)
      ? rawLayer.groups.map((group, groupIndex) => normalizeLayerGroup(group, layer, groupIndex, createId)).filter(Boolean)
      : []
    const nodes = groups.length
      ? groups.reduce((items, group) => items.concat(group.nodes || []), [])
      : (Array.isArray(rawLayer?.nodes)
          ? rawLayer.nodes.map((node, nodeIndex) => normalizeLayerNode(node, layer, nodeIndex, createId, { level: 1 })).filter(Boolean)
          : [])
    return {
      ...layer,
      groups,
      nodes,
    }
  })
}

function flattenNode(node, target = []) {
  target.push({
    id: node.id,
    name: node.name,
    type: node.type,
    layer: node.layer,
    description: node.description || '',
    tech: normalizeTech(node.tech),
    iconKey: node.iconKey || '',
    parentId: node.parentId || '',
    groupId: node.groupId || '',
    level: Number(node.level) || 1,
  })
  ;(node.children || []).forEach(child => flattenNode(child, target))
  return target
}

function flattenLayersToNodes(layers = []) {
  const seen = new Set()
  const nodes = []
  layers.forEach(layer => {
    const layerNodes = []
    ;(layer.groups || []).forEach(group => {
      ;(group.nodes || []).forEach(node => flattenNode(node, layerNodes))
    })
    ;(layer.nodes || []).forEach(node => flattenNode(node, layerNodes))
    layerNodes.forEach(node => {
      if (!node.id || seen.has(node.id)) return
      seen.add(node.id)
      nodes.push(node)
    })
  })
  return nodes
}

function normalizeFlatNodes(nodes = []) {
  if (!Array.isArray(nodes)) return []
  return nodes.map(node => ({
    ...node,
    id: String(node.id || node.name || ''),
    name: String(node.name || node.label || node.id || ''),
    type: String(node.type || ''),
    layer: String(node.layer || node.type || ''),
    description: String(node.description || ''),
    tech: normalizeTech(node.tech || node.technologies || []),
    iconKey: String(node.iconKey || ''),
    parentId: String(node.parentId || ''),
    groupId: String(node.groupId || ''),
    level: Number(node.level) || 1,
  })).filter(node => node.id && node.name)
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
  const createId = createIdFactory()
  const layers = LAYER_DEFS.map(cfg => {
    const matched = nodes.filter(n => {
      if (!n || used.has(n.id)) return false
      const type = (n.type || '').toLowerCase()
      return cfg.types.includes(type)
    })
    matched.forEach(n => used.add(n.id))
    const layer = { ...cfg }
    return {
      ...cfg,
      groups: [],
      nodes: matched
        .map((n, index) => normalizeLayerNode(n, layer, index, createId, { level: 1 }))
        .filter(Boolean)
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
  const requestedRelationMode = normalizeRelationMode(payload.relationMode || payload.relationType || 'AUTO')
  const resolvedRelationMode = requestedRelationMode === 'AUTO' ? 'MODULE' : requestedRelationMode
  const edgeType = resolvedRelationMode === 'DATA_FLOW' ? 'dataFlow' : resolvedRelationMode === 'CALL' ? 'call' : 'structural'
  return Promise.resolve(normalizeArchitectureResult({
    id: `mock_arch_${Date.now()}`,
    title,
    style: '前后端分离',
    systemType: payload.systemType || 'WEB',
    architectureLayers: Array.isArray(payload.architectureLayers) ? payload.architectureLayers : [],
    focusContents: Array.isArray(payload.focusContents) ? payload.focusContents : [],
    requestedRelationMode,
    resolvedRelationMode,
    requestedHierarchyMode: 'STRUCTURED',
    resolvedHierarchyMode: 'STRUCTURED',
    layers: [
      {
        ...LAYER_DEFS[0],
        groups: [
          {
            id: 'client_entry_group',
            name: '用户入口',
            description: '多端访问入口',
            nodes: [
              {
                id: 'frontend',
                name: '前端应用',
                type: 'frontend',
                description: '统一承载用户交互',
                tech: ['Vue3', 'UniApp'],
                children: [
                  { id: 'mobile_pages', name: '移动端页面', description: '浏览、发布、聊天', tech: ['UniApp'] },
                  { id: 'admin_pages', name: '管理后台页面', description: '运营审核与配置', tech: ['Vue3'] },
                ],
              },
            ],
          },
        ],
        nodes: [],
      },
      {
        ...LAYER_DEFS[1],
        groups: [
          {
            id: 'gateway_access_group',
            name: '访问接入',
            description: '请求入口与安全控制',
            nodes: [
              {
                id: 'gateway',
                name: 'API 网关',
                type: 'gateway',
                description: '路由、鉴权、限流',
                tech: ['Gateway'],
                children: [
                  { id: 'auth_filter', name: '鉴权过滤器', description: 'Token 校验', tech: ['JWT'] },
                  { id: 'route_policy', name: '路由策略', description: '服务转发', tech: ['Gateway'] },
                ],
              },
            ],
          },
        ],
        nodes: [],
      },
      {
        ...LAYER_DEFS[2],
        groups: [
          {
            id: 'service_business_group',
            name: '核心业务服务',
            description: '交易闭环能力',
            nodes: [
              {
                id: 'user_service',
                name: '用户服务',
                type: 'service',
                description: '账号、认证、资料',
                tech: ['Spring Boot'],
                children: [
                  { id: 'user_profile_module', name: '资料模块', description: '个人信息维护', tech: ['MyBatis'] },
                ],
              },
              {
                id: 'product_service',
                name: '商品服务',
                type: 'service',
                description: '发布、分类、浏览',
                tech: ['Spring Boot'],
                children: [
                  { id: 'product_publish_module', name: '发布模块', description: '商品发布校验', tech: ['Spring Boot'] },
                ],
              },
              { id: 'order_service', name: '订单服务', type: 'service', description: '交易状态流转', tech: ['Spring Boot'] },
            ],
          },
        ],
        nodes: [],
      },
      {
        ...LAYER_DEFS[3],
        nodes: [
          { id: 'mybatis', name: 'MyBatis-Plus', type: 'dao', description: 'SQL 映射与事务', tech: ['MyBatis-Plus'] },
        ],
      },
      {
        ...LAYER_DEFS[4],
        groups: [
          {
            id: 'storage_data_group',
            name: '数据存储',
            description: '业务数据与缓存',
            nodes: [
              { id: 'mysql', name: 'MySQL', type: 'database', description: '核心业务数据', tech: ['MySQL'] },
              { id: 'redis', name: 'Redis', type: 'cache', description: '缓存、会话、验证码', tech: ['Redis'] },
            ],
          },
        ],
        nodes: [],
      },
      {
        ...LAYER_DEFS[5],
        nodes: [
          { id: 'docker', name: 'Docker', type: 'infrastructure', description: '容器化部署', tech: ['Docker'] },
          { id: 'monitor', name: '监控告警', type: 'monitor', description: '指标与日志观察', tech: ['Prometheus'] },
        ],
      },
    ],
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
      { source: 'frontend', target: 'gateway', type: edgeType, label: resolvedRelationMode === 'MODULE' ? '结构连接' : '请求' },
      { source: 'gateway', target: 'user_service', type: edgeType, label: resolvedRelationMode === 'CALL' ? '调用' : '路由' },
      { source: 'gateway', target: 'product_service', type: edgeType, label: resolvedRelationMode === 'CALL' ? '调用' : '路由' },
      { source: 'gateway', target: 'order_service', type: edgeType, label: resolvedRelationMode === 'CALL' ? '调用' : '路由' },
      { source: 'user_service', target: 'mysql', type: edgeType, label: resolvedRelationMode === 'DATA_FLOW' ? '用户数据' : '读写' },
      { source: 'product_service', target: 'mysql', type: edgeType, label: resolvedRelationMode === 'DATA_FLOW' ? '商品数据' : '读写' },
      { source: 'order_service', target: 'mysql', type: edgeType, label: resolvedRelationMode === 'DATA_FLOW' ? '订单数据' : '读写' },
      { source: 'user_service', target: 'redis', type: edgeType, label: '缓存' }
    ],
    createTime: new Date().toISOString()
  }))
}
