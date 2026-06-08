import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Progress,
  Row,
  Segmented,
  Select,
  Slider,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  Upload,
  message,
} from 'antd'
import {
  ApiOutlined,
  BranchesOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  CopyOutlined,
  DatabaseOutlined,
  DownloadOutlined,
  EyeOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  FilterOutlined,
  ReloadOutlined,
  SearchOutlined,
  SettingOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import {
  convertPdf,
  convertPpt,
  getRagDocumentChunks,
  getRagDocuments,
  getRagEmbeddingHealth,
  getRagGraphStoreHealth,
  getRagVectorStoreHealth,
  ingestRagDocuments,
  runRagRecallTest,
} from '../../../api/rag'
import { getSystemConfigList } from '../../../api/systemConfig'
import '../RagManage/RagManage.css'
import './KnowledgeBaseManage.css'

const { TextArea } = Input
const { Text, Title } = Typography

const DEFAULT_CHUNK_SIZE = 800
const DEFAULT_CHUNK_OVERLAP = 120
const DEFAULT_KNOWLEDGE_BASE_ID = 'default'
const DEFAULT_KNOWLEDGE_BASE_NAME = '默认知识库'
const LOCAL_KNOWLEDGE_BASES_KEY = 'smart_campus_knowledge_bases_v1'
const PARENT_CHUNK_SIZE = 1600
const PARENT_CHUNK_OVERLAP = 160
const CHILD_CHUNK_SIZE = 420
const CHILD_CHUNK_OVERLAP = 80
const AI_TESTED_MODEL_PREFIXES_KEY = 'ai_tested_model_prefixes_v1'
const AI_MODEL_CONFIG_PATTERN = /^ai\.service\.embedding(?:\.([A-Za-z0-9_-]+))?\.(provider|base-url|api-key|model)$/

const recallStrategyOptions = [
  { value: 'hybrid_search', label: '混合检索 hybrid_search' },
  { value: 'metadata_filter_rag', label: '元数据过滤 metadata_filter_rag' },
  { value: 'knowledge_base_router_rag', label: '知识库路由 knowledge_base_router_rag' },
  { value: 'contextual_compression_rag', label: '上下文压缩 contextual_compression_rag' },
  { value: 'time_weighted_rag', label: '时间加权 time_weighted_rag' },
  { value: 'parent_child', label: '父子切片 parent_child' },
  { value: 'reranking', label: '重排序 reranking' },
  { value: 'naive_rag', label: '基础检索 naive_rag' },
  { value: 'multi_query_rag', label: '多查询 multi_query_rag' },
]

const ingestModeOptions = [
  { label: '语义边界', value: 'semantic' },
  { label: '父子片段', value: 'parentChild' },
]

const sourceTypeOptions = [
  { label: '全部来源', value: 'all' },
  { label: '真实入库', value: 'indexed' },
  { label: '最近入库', value: 'recent' },
  { label: '当前预览', value: 'preview' },
  { label: '召回命中', value: 'recall' },
]

const defaultKnowledgeBase = {
  id: DEFAULT_KNOWLEDGE_BASE_ID,
  name: DEFAULT_KNOWLEDGE_BASE_NAME,
  description: '兼容历史文档和未指定归属的知识内容',
  documentCount: 0,
  chunkCount: 0,
  size: 0,
}

const cleanText = (text) => String(text || '').replace(/\r\n/g, '\n').trim()

const slugifyKnowledgeBaseId = (value) => {
  const normalized = String(value || '').trim()
  if (!normalized) return ''
  return normalized
    .toLowerCase()
    .replace(/[^a-z0-9_\-\u4e00-\u9fff]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 64)
}

const readLocalKnowledgeBases = () => {
  try {
    const parsed = JSON.parse(localStorage.getItem(LOCAL_KNOWLEDGE_BASES_KEY) || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const writeLocalKnowledgeBases = (items) => {
  localStorage.setItem(LOCAL_KNOWLEDGE_BASES_KEY, JSON.stringify(items))
}

const mergeKnowledgeBases = (remoteItems, localItems) => {
  const byId = new Map()
  byId.set(defaultKnowledgeBase.id, { ...defaultKnowledgeBase })
  ;[...(localItems || []), ...(remoteItems || [])].forEach((item) => {
    const id = String(item?.id || '').trim()
    if (!id) return
    const previous = byId.get(id) || {}
    byId.set(id, {
      ...previous,
      ...item,
      id,
      name: item.name || previous.name || id,
      description: item.description ?? previous.description ?? '',
      documentCount: Number(item.documentCount ?? previous.documentCount ?? 0),
      chunkCount: Number(item.chunkCount ?? previous.chunkCount ?? 0),
      size: Number(item.size ?? previous.size ?? 0),
    })
  })
  return Array.from(byId.values()).sort((left, right) => {
    if (left.id === DEFAULT_KNOWLEDGE_BASE_ID) return -1
    if (right.id === DEFAULT_KNOWLEDGE_BASE_ID) return 1
    return String(left.name).localeCompare(String(right.name), 'zh-CN')
  })
}

const deriveKnowledgeBasesFromDocuments = (documents) => {
  const byId = new Map()
  ;(documents || []).forEach((document) => {
    const id = document.knowledgeBaseId || DEFAULT_KNOWLEDGE_BASE_ID
    const item = byId.get(id) || {
      id,
      name: document.knowledgeBaseName || DEFAULT_KNOWLEDGE_BASE_NAME,
      description: '',
      documentCount: 0,
      chunkCount: 0,
      size: 0,
    }
    item.documentCount += 1
    item.chunkCount += Number(document.chunkCount || 0)
    item.size += Number(document.size || 0)
    byId.set(id, item)
  })
  return Array.from(byId.values())
}

const formatBytes = (value) => {
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  if (number < 1024) return `${number} B`
  if (number < 1024 * 1024) return `${Math.ceil(number / 1024)} KB`
  return `${(number / 1024 / 1024).toFixed(1)} MB`
}

const formatTime = (value) => (value ? new Date(Number(value) * 1000).toLocaleString() : '-')

const isSupportedKnowledgeFile = (file) => /\.(docx|txt)$/i.test(file?.name || file?.originFileObj?.name || '')
const isPptxFile = (file) => /\.pptx$/i.test(file?.name || file?.originFileObj?.name || '')
const isPdfFile = (file) => /\.pdf$/i.test(file?.name || file?.originFileObj?.name || '')

const readFileAsBase64 = (file) => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => {
    const result = String(reader.result || '')
    resolve(result.includes(',') ? result.split(',').pop() : result)
  }
  reader.onerror = reject
  reader.readAsDataURL(file)
})

const readFileAsText = (file) => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => resolve(String(reader.result || ''))
  reader.onerror = reject
  reader.readAsText(file)
})

const splitSemanticText = (text, chunkSize = DEFAULT_CHUNK_SIZE, overlap = DEFAULT_CHUNK_OVERLAP) => {
  const normalized = cleanText(text)
  if (!normalized) return []
  if (normalized.length <= chunkSize) return [normalized]

  const chunks = []
  let start = 0
  while (start < normalized.length) {
    const end = Math.min(start + chunkSize, normalized.length)
    const splitAt = findBoundary(normalized, start, end, chunkSize)
    const chunk = normalized.slice(start, splitAt).trim()
    if (chunk) chunks.push(chunk)
    if (splitAt >= normalized.length) break
    start = Math.max(splitAt - overlap, start + 1)
  }
  return chunks
}

const findBoundary = (text, start, end, chunkSize) => {
  const windowText = text.slice(start, end)
  const markers = ['\n\n', '\n', '。', '！', '？', '.', '!', '?']
  for (const marker of markers) {
    const index = windowText.lastIndexOf(marker)
    if (index > chunkSize * 0.45) {
      return start + index + marker.length
    }
  }
  return end
}

const buildPreviewChunks = (text, source, mode, settings = {}) => {
  const normalized = cleanText(text)
  if (!normalized) return []
  const chunkSize = Number(settings.chunkSize || DEFAULT_CHUNK_SIZE)
  const chunkOverlap = Number(settings.chunkOverlap || DEFAULT_CHUNK_OVERLAP)
  const parentChunkSize = Number(settings.parentChunkSize || PARENT_CHUNK_SIZE)
  const parentChunkOverlap = Number(settings.parentChunkOverlap || PARENT_CHUNK_OVERLAP)
  const childChunkSize = Number(settings.childChunkSize || CHILD_CHUNK_SIZE)
  const childChunkOverlap = Number(settings.childChunkOverlap || CHILD_CHUNK_OVERLAP)
  if (mode === 'parentChild') {
    return splitSemanticText(normalized, parentChunkSize, parentChunkOverlap).flatMap((parent, parentIndex) => {
      const parentId = `preview-parent-${parentIndex}`
      const children = splitSemanticText(parent, childChunkSize, childChunkOverlap)
      return [
        {
          id: parentId,
          source,
          content: parent,
          chunkIndex: parentIndex,
          chunkRole: 'parent',
          parentIndex,
          size: new Blob([parent]).size,
          origin: 'preview',
          metadata: { chunkRole: 'parent', parentIndex, sourceName: source },
        },
        ...children.map((child, childIndex) => ({
          id: `${parentId}-child-${childIndex}`,
          source,
          content: child,
          chunkIndex: childIndex,
          chunkRole: 'child',
          parentIndex,
          childIndex,
          size: new Blob([child]).size,
          origin: 'preview',
          metadata: { chunkRole: 'child', parentIndex, childIndex, sourceName: source },
        })),
      ]
    })
  }
  return splitSemanticText(normalized, chunkSize, chunkOverlap).map((content, index) => ({
    id: `preview-${index}`,
    source,
    content,
    chunkIndex: index,
    chunkRole: 'chunk',
    size: new Blob([content]).size,
    origin: 'preview',
    metadata: { chunkIndex: index, sourceName: source },
  }))
}

const withChunkKnowledgeBase = (chunk, knowledgeBase) => ({
  ...chunk,
  knowledgeBaseId: knowledgeBase?.id || DEFAULT_KNOWLEDGE_BASE_ID,
  knowledgeBaseName: knowledgeBase?.name || DEFAULT_KNOWLEDGE_BASE_NAME,
  metadata: {
    ...(chunk.metadata || {}),
    knowledgeBaseId: knowledgeBase?.id || DEFAULT_KNOWLEDGE_BASE_ID,
    knowledgeBaseName: knowledgeBase?.name || DEFAULT_KNOWLEDGE_BASE_NAME,
  },
})

const readTestedModelPrefixSet = () => {
  try {
    const raw = localStorage.getItem(AI_TESTED_MODEL_PREFIXES_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return new Set(Object.keys(parsed || {}))
  } catch {
    return new Set()
  }
}

const buildEmbeddingModelOptions = (configs) => {
  const testedPrefixes = readTestedModelPrefixSet()
  const groups = new Map()
  ;(configs || []).forEach((item) => {
    const match = AI_MODEL_CONFIG_PATTERN.exec(item.configKey || '')
    if (!match) return
    const configName = match[1] || 'default'
    const field = match[2]
    const configPrefix = configName === 'default' ? 'ai.service.embedding' : `ai.service.embedding.${configName}`
    const group = groups.get(configPrefix) || { configPrefix, configName, configs: {} }
    group.configs[field] = item.configValue || ''
    groups.set(configPrefix, group)
  })
  return Array.from(groups.values())
    .filter((group) => testedPrefixes.has(group.configPrefix))
    .filter((group) => group.configs.provider && group.configs['base-url'] && group.configs['api-key'] && group.configs.model)
    .map((group) => ({
      value: group.configPrefix,
      label: `${group.configs.model}（${group.configs.provider || group.configName}）`,
    }))
}

const normalizeIndexedChunks = (items) => (items || []).map((item, index) => ({
  id: item.id || `${item.source || 'chunk'}-${index}`,
  source: item.source || item.metadata?.sourceName || item.storedPath || '-',
  storedPath: item.storedPath,
  content: item.content || '',
  score: item.score,
  chunkIndex: item.chunkIndex ?? item.metadata?.chunkIndex ?? index,
  chunkRole: item.metadata?.chunkRole || 'chunk',
  size: item.size || new Blob([item.content || '']).size,
  origin: 'indexed',
  knowledgeBaseId: item.knowledgeBaseId || item.metadata?.knowledgeBaseId || DEFAULT_KNOWLEDGE_BASE_ID,
  knowledgeBaseName: item.knowledgeBaseName || item.metadata?.knowledgeBaseName || DEFAULT_KNOWLEDGE_BASE_NAME,
  metadata: item.metadata || {},
}))

const normalizeRecallChunks = (items) => (items || []).map((item, index) => ({
  id: `recall-${item.id || index}`,
  source: item.metadata?.sourceName || item.source || '-',
  storedPath: item.source,
  content: item.content || '',
  score: item.score,
  chunkIndex: item.metadata?.chunkIndex ?? index,
  chunkRole: item.metadata?.chunkRole || 'hit',
  size: new Blob([item.content || '']).size,
  origin: 'recall',
  knowledgeBaseId: item.metadata?.knowledgeBaseId || DEFAULT_KNOWLEDGE_BASE_ID,
  knowledgeBaseName: item.metadata?.knowledgeBaseName || DEFAULT_KNOWLEDGE_BASE_NAME,
  metadata: item.metadata || {},
}))

const buildDocumentStatus = (document) => {
  if (!document) return { label: '-', color: 'default' }
  if (Number(document.chunkCount || 0) > 0) return { label: '已索引', color: 'green' }
  return { label: '待处理', color: 'orange' }
}

const valuesKnowledgeBaseMatch = (document, knowledgeBaseIds) => {
  if (!knowledgeBaseIds?.length) return true
  return knowledgeBaseIds.includes(document.knowledgeBaseId || DEFAULT_KNOWLEDGE_BASE_ID)
}

function KnowledgeBaseManage() {
  const [bootLoading, setBootLoading] = useState(false)
  const [chunkLoading, setChunkLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [documents, setDocuments] = useState([])
  const [selectedSource, setSelectedSource] = useState('')
  const [indexedChunks, setIndexedChunks] = useState([])
  const [knowledgeBases, setKnowledgeBases] = useState([defaultKnowledgeBase])
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState(DEFAULT_KNOWLEDGE_BASE_ID)
  const [recallKnowledgeBaseIds, setRecallKnowledgeBaseIds] = useState([DEFAULT_KNOWLEDGE_BASE_ID])
  const [newKnowledgeBaseName, setNewKnowledgeBaseName] = useState('')
  const [newKnowledgeBaseDescription, setNewKnowledgeBaseDescription] = useState('')
  const [health, setHealth] = useState({})
  const [knowledgeBaseMeta, setKnowledgeBaseMeta] = useState({})
  const [uploadFileList, setUploadFileList] = useState([])
  const [previewText, setPreviewText] = useState('')
  const [previewSource, setPreviewSource] = useState('后台录入.txt')
  const [previewMode, setPreviewMode] = useState('semantic')
  const [chunkSettings, setChunkSettings] = useState({
    chunkSize: DEFAULT_CHUNK_SIZE,
    chunkOverlap: DEFAULT_CHUNK_OVERLAP,
    parentChunkSize: PARENT_CHUNK_SIZE,
    parentChunkOverlap: PARENT_CHUNK_OVERLAP,
    childChunkSize: CHILD_CHUNK_SIZE,
    childChunkOverlap: CHILD_CHUNK_OVERLAP,
  })
  const [convertFileList, setConvertFileList] = useState([])
  const [convertResult, setConvertResult] = useState(null)
  const [convertLoading, setConvertLoading] = useState(false)
  const [recallLoading, setRecallLoading] = useState(false)
  const [recallResult, setRecallResult] = useState(null)
  const [recallError, setRecallError] = useState('')
  const [ingestResult, setIngestResult] = useState(null)
  const [recentChunks, setRecentChunks] = useState([])
  const [embeddingModelOptions, setEmbeddingModelOptions] = useState([])
  const [chunkSearch, setChunkSearch] = useState('')
  const [chunkOriginFilter, setChunkOriginFilter] = useState('all')
  const [activeChunk, setActiveChunk] = useState(null)
  const [ingestForm] = Form.useForm()
  const [convertForm] = Form.useForm()
  const [recallForm] = Form.useForm()

  const selectedKnowledgeBase = useMemo(
    () => knowledgeBases.find((item) => item.id === selectedKnowledgeBaseId) || knowledgeBases[0] || defaultKnowledgeBase,
    [knowledgeBases, selectedKnowledgeBaseId],
  )

  const visibleDocuments = useMemo(
    () => documents.filter((item) => (item.knowledgeBaseId || DEFAULT_KNOWLEDGE_BASE_ID) === selectedKnowledgeBaseId),
    [documents, selectedKnowledgeBaseId],
  )

  const selectedDocument = useMemo(
    () => visibleDocuments.find((item) => item.source === selectedSource) || visibleDocuments[0] || null,
    [selectedSource, visibleDocuments],
  )

  const previewChunks = useMemo(
    () => buildPreviewChunks(previewText, previewSource || '后台录入.txt', previewMode, chunkSettings).map((item) => withChunkKnowledgeBase(item, selectedKnowledgeBase)),
    [previewText, previewSource, previewMode, chunkSettings, selectedKnowledgeBase],
  )

  const recallChunks = useMemo(
    () => normalizeRecallChunks(recallResult?.documents || []),
    [recallResult],
  )
  const recallAverageScore = useMemo(() => {
    const scores = recallChunks.map((item) => Number(item.score)).filter(Number.isFinite)
    if (!scores.length) return 0
    return scores.reduce((sum, score) => sum + score, 0) / scores.length
  }, [recallChunks])
  const recallCoverage = useMemo(() => {
    const selectedIds = recallResult?.metadata?.knowledgeBaseIds || recallKnowledgeBaseIds
    if (!selectedIds?.length) return 0
    const hitIds = new Set(recallChunks.map((item) => item.knowledgeBaseId || DEFAULT_KNOWLEDGE_BASE_ID))
    return Math.round((selectedIds.filter((id) => hitIds.has(id)).length / selectedIds.length) * 100)
  }, [recallChunks, recallKnowledgeBaseIds, recallResult])

  const allChunks = useMemo(
    () => [...indexedChunks, ...recentChunks, ...previewChunks, ...recallChunks],
    [indexedChunks, recentChunks, previewChunks, recallChunks],
  )

  const filteredChunks = useMemo(() => {
    const keyword = chunkSearch.trim().toLowerCase()
    return allChunks.filter((item) => {
      const matchOrigin = chunkOriginFilter === 'all' || item.origin === chunkOriginFilter
      const matchSource = !selectedDocument || item.origin !== 'indexed' || item.source === selectedDocument.source
      const matchKnowledgeBase = item.origin !== 'indexed' || (item.knowledgeBaseId || DEFAULT_KNOWLEDGE_BASE_ID) === selectedKnowledgeBaseId
      const matchKeyword = !keyword
        || String(item.content || '').toLowerCase().includes(keyword)
        || String(item.source || '').toLowerCase().includes(keyword)
        || JSON.stringify(item.metadata || {}).toLowerCase().includes(keyword)
      return matchOrigin && matchSource && matchKnowledgeBase && matchKeyword
    })
  }, [allChunks, chunkOriginFilter, chunkSearch, selectedDocument, selectedKnowledgeBaseId])

  const totalChunkCount = visibleDocuments.reduce((sum, item) => sum + Number(item.chunkCount || 0), 0)
  const indexedSize = visibleDocuments.reduce((sum, item) => sum + Number(item.size || 0), 0)
  const indexedHealth = health.vector || {}
  const healthScore = [
    indexedHealth.configured,
    health.embedding?.configured ?? health.embedding?.status === 'implemented',
    health.graph?.configured ?? health.graph?.status === 'implemented',
  ].filter(Boolean).length
  const previewAverageLength = previewChunks.length
    ? Math.round(previewChunks.reduce((sum, item) => sum + String(item.content || '').length, 0) / previewChunks.length)
    : 0

  const refreshChunks = async (source = selectedDocument?.source || '', knowledgeBaseId = selectedKnowledgeBaseId) => {
    setChunkLoading(true)
    try {
      const params = {
        ...(source ? { source } : {}),
        ...(knowledgeBaseId ? { knowledgeBaseIds: knowledgeBaseId } : {}),
      }
      const res = await getRagDocumentChunks(params)
      setIndexedChunks(normalizeIndexedChunks(res.data?.chunks || []))
    } catch (error) {
      setIndexedChunks([])
      message.warning(error.message || '加载文档切片失败')
    } finally {
      setChunkLoading(false)
    }
  }

  const refresh = async () => {
    setBootLoading(true)
    try {
      const [documentRes, vectorHealthRes, embeddingHealthRes, graphHealthRes, configRes] = await Promise.all([
        getRagDocuments(),
        getRagVectorStoreHealth(),
        getRagEmbeddingHealth(),
        getRagGraphStoreHealth(),
        getSystemConfigList({ current: 1, size: 500, prefixes: 'ai.service.embedding' }),
      ])
      const loadedDocuments = documentRes.data?.documents || []
      const mergedKnowledgeBases = mergeKnowledgeBases(
        [...deriveKnowledgeBasesFromDocuments(loadedDocuments), ...(documentRes.data?.knowledgeBases || [])],
        readLocalKnowledgeBases(),
      )
      const options = buildEmbeddingModelOptions(configRes.data?.records || [])
      const nextKnowledgeBaseId = mergedKnowledgeBases.some((item) => item.id === selectedKnowledgeBaseId)
        ? selectedKnowledgeBaseId
        : mergedKnowledgeBases[0]?.id || DEFAULT_KNOWLEDGE_BASE_ID
      const nextVisibleDocuments = loadedDocuments.filter((item) => (item.knowledgeBaseId || DEFAULT_KNOWLEDGE_BASE_ID) === nextKnowledgeBaseId)
      const nextSelectedSource = selectedSource && nextVisibleDocuments.some((item) => item.source === selectedSource)
        ? selectedSource
        : nextVisibleDocuments[0]?.source || ''
      setDocuments(loadedDocuments)
      setKnowledgeBases(mergedKnowledgeBases)
      setSelectedKnowledgeBaseId(nextKnowledgeBaseId)
      setSelectedSource(nextSelectedSource)
      setKnowledgeBaseMeta(documentRes.data?.knowledgeBase || {})
      setEmbeddingModelOptions(options)
      const currentIngestModel = ingestForm.getFieldValue('embeddingModel')
      const currentRecallModel = recallForm.getFieldValue('embeddingModel')
      if (!currentIngestModel && options[0]?.value) {
        ingestForm.setFieldsValue({ embeddingModel: options[0].value })
      }
      if (!currentRecallModel && options[0]?.value) {
        recallForm.setFieldsValue({ embeddingModel: options[0].value })
      }
      setHealth({
        vector: vectorHealthRes.data,
        embedding: embeddingHealthRes.data,
        graph: graphHealthRes.data,
      })
      if (nextSelectedSource) {
        const chunkRes = await getRagDocumentChunks({ source: nextSelectedSource, knowledgeBaseIds: nextKnowledgeBaseId })
        setIndexedChunks(normalizeIndexedChunks(chunkRes.data?.chunks || []))
      } else {
        setIndexedChunks([])
      }
    } catch (error) {
      message.error(error.message || '加载知识库数据失败')
    } finally {
      setBootLoading(false)
    }
  }

  useEffect(() => {
    refresh()
  }, [])

  useEffect(() => {
    ingestForm.setFieldsValue({ knowledgeBaseId: selectedKnowledgeBaseId })
    if (!recallKnowledgeBaseIds.length || !recallKnowledgeBaseIds.every((id) => knowledgeBases.some((item) => item.id === id))) {
      setRecallKnowledgeBaseIds([selectedKnowledgeBaseId])
      recallForm.setFieldsValue({ knowledgeBaseIds: [selectedKnowledgeBaseId] })
    }
  }, [selectedKnowledgeBaseId, knowledgeBases])

  useEffect(() => {
    if (selectedDocument?.source) {
      refreshChunks(selectedDocument.source, selectedKnowledgeBaseId)
    } else {
      setIndexedChunks([])
    }
  }, [selectedDocument?.source, selectedKnowledgeBaseId])

  const handleIngest = async (values) => {
    setActionLoading(true)
    try {
      const selectedFile = uploadFileList[0]?.originFileObj || uploadFileList[0]
      const textContent = values.content || ''
      const sourceName = values.source || selectedFile?.name || '后台录入.txt'
      if (!selectedFile && !textContent.trim()) {
        message.warning('请粘贴文档内容，或选择一个本地文件')
        return
      }
      if (selectedFile && !isSupportedKnowledgeFile(selectedFile)) {
        message.warning('知识库暂时只支持上传 DOCX 或 TXT 文件')
        return
      }
      if (!selectedFile && sourceName && !isSupportedKnowledgeFile({ name: sourceName })) {
        message.warning('来源文件名请使用 .txt 或 .docx 后缀')
        return
      }
      const targetKnowledgeBase = knowledgeBases.find((item) => item.id === values.knowledgeBaseId) || selectedKnowledgeBase
      const contentBase64 = selectedFile ? await readFileAsBase64(selectedFile) : undefined
      const previewForRecent = selectedFile && !textContent
        ? []
        : buildPreviewChunks(textContent, sourceName, values.chunkMode || previewMode, values).map((item) => withChunkKnowledgeBase(item, targetKnowledgeBase))
      const res = await ingestRagDocuments({
        embeddingModel: values.embeddingModel,
        documents: [{
          source: sourceName,
          content: textContent,
          contentBase64,
          metadata: {
            origin: 'knowledge_base_console',
            uploadMode: selectedFile ? 'file_base64' : 'text',
            chunkMode: values.chunkMode || previewMode,
            chunkSize: values.chunkSize || DEFAULT_CHUNK_SIZE,
            chunkOverlap: values.chunkOverlap || DEFAULT_CHUNK_OVERLAP,
            parentChunkSize: values.parentChunkSize || PARENT_CHUNK_SIZE,
            parentChunkOverlap: values.parentChunkOverlap || PARENT_CHUNK_OVERLAP,
            childChunkSize: values.childChunkSize || CHILD_CHUNK_SIZE,
            childChunkOverlap: values.childChunkOverlap || CHILD_CHUNK_OVERLAP,
            scene: values.scene || 'campus_knowledge',
            tags: String(values.tags || '').split(/[,，]/).map((item) => item.trim()).filter(Boolean),
            knowledgeBaseId: targetKnowledgeBase.id,
            knowledgeBaseName: targetKnowledgeBase.name,
            knowledgeBaseDescription: targetKnowledgeBase.description || '',
          },
        }],
      })
      const result = res.data || {}
      setIngestResult(result)
      setRecentChunks(previewForRecent.map((item) => ({ ...item, id: item.id.replace('preview', 'recent'), origin: 'recent' })))
      setSelectedKnowledgeBaseId(targetKnowledgeBase.id)
      setSelectedSource(sourceName)
      message.success(`入库完成：${result.storedCount || 0} 个文档，${result.indexedChunkCount || 0} 个片段`)
      ingestForm.setFieldsValue({
        content: '',
        source: '',
        tags: '',
        scene: 'campus_knowledge',
        chunkMode: 'semantic',
        chunkSize: DEFAULT_CHUNK_SIZE,
        chunkOverlap: DEFAULT_CHUNK_OVERLAP,
        parentChunkSize: PARENT_CHUNK_SIZE,
        parentChunkOverlap: PARENT_CHUNK_OVERLAP,
        childChunkSize: CHILD_CHUNK_SIZE,
        childChunkOverlap: CHILD_CHUNK_OVERLAP,
      })
      setPreviewText('')
      setPreviewSource('后台录入.txt')
      setPreviewMode('semantic')
      setChunkSettings({
        chunkSize: DEFAULT_CHUNK_SIZE,
        chunkOverlap: DEFAULT_CHUNK_OVERLAP,
        parentChunkSize: PARENT_CHUNK_SIZE,
        parentChunkOverlap: PARENT_CHUNK_OVERLAP,
        childChunkSize: CHILD_CHUNK_SIZE,
        childChunkOverlap: CHILD_CHUNK_OVERLAP,
      })
      setUploadFileList([])
      await refresh()
    } catch (error) {
      message.error(error.message || '知识入库失败')
    } finally {
      setActionLoading(false)
    }
  }

  const handleDocumentConvert = async () => {
    const selectedFile = convertFileList[0]?.originFileObj || convertFileList[0]
    if (!selectedFile) {
      message.warning('请先选择一个 PDF 或 PPTX 文件')
      return
    }
    const isPptx = isPptxFile(selectedFile)
    const isPdf = isPdfFile(selectedFile)
    if (!isPptx && !isPdf) {
      message.warning('请选择 PDF 或 PPTX 文件')
      return
    }
    setConvertLoading(true)
    setConvertResult(null)
    try {
      const formData = new FormData()
      formData.append('file', selectedFile)
      if (!isPptx) {
        formData.append('targetFormat', 'docx')
      }
      const res = isPptx ? await convertPpt(formData) : await convertPdf(formData)
      setConvertResult(res.data)
      message.success(isPptx ? 'PPTX 转 DOCX 完成' : 'PDF 转 DOCX 完成')
    } catch (error) {
      message.error(error.message || '文档转换失败')
    } finally {
      setConvertLoading(false)
    }
  }

  const handleRecallTest = async (values) => {
    setRecallLoading(true)
    setRecallError('')
    setRecallResult(null)
    try {
      const res = await runRagRecallTest({
        query: values.query,
        keyword: values.keyword || undefined,
        intent: values.intent || 'campus_search',
        ragStrategy: values.ragStrategy || 'hybrid_search',
        embeddingModel: values.embeddingModel,
        metadata: {
          source: values.source || undefined,
          knowledgeBaseIds: values.knowledgeBaseIds || recallKnowledgeBaseIds,
          topK: values.topK || 5,
          similarityThreshold: values.similarityThreshold,
        },
      })
      setRecallResult(res.data)
      setChunkOriginFilter('recall')
      message.success(`召回完成，命中 ${(res.data?.documents || []).length} 条`)
    } catch (error) {
      const errorMessage = error.message || '召回测试失败'
      setRecallError(errorMessage)
      message.error(errorMessage)
    } finally {
      setRecallLoading(false)
    }
  }

  const handleCreateKnowledgeBase = () => {
    const name = newKnowledgeBaseName.trim()
    if (!name) {
      message.warning('请输入知识库名称')
      return
    }
    const id = slugifyKnowledgeBaseId(name)
    if (!id) {
      message.warning('知识库名称需要包含中文、英文或数字')
      return
    }
    if (knowledgeBases.some((item) => item.id === id)) {
      message.warning('同名知识库已存在')
      return
    }
    const nextItem = {
      id,
      name,
      description: newKnowledgeBaseDescription.trim(),
      documentCount: 0,
      chunkCount: 0,
      size: 0,
    }
    const nextLocalItems = [...readLocalKnowledgeBases(), nextItem]
    writeLocalKnowledgeBases(nextLocalItems)
    const merged = mergeKnowledgeBases(knowledgeBases, nextLocalItems)
    setKnowledgeBases(merged)
    setSelectedKnowledgeBaseId(id)
    setRecallKnowledgeBaseIds([id])
    ingestForm.setFieldsValue({ knowledgeBaseId: id })
    recallForm.setFieldsValue({ knowledgeBaseIds: [id] })
    setNewKnowledgeBaseName('')
    setNewKnowledgeBaseDescription('')
    message.success('知识库已创建，导入文档时会写入该知识库')
  }

  const downloadConvertedFile = (result) => {
    if (!result?.contentBase64) {
      message.warning('转换结果没有可下载内容')
      return
    }
    const binary = window.atob(result.contentBase64)
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index += 1) {
      bytes[index] = binary.charCodeAt(index)
    }
    const blob = new Blob([bytes], { type: result.mimeType || 'application/octet-stream' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = result.fileName || 'knowledge-base-convert-result'
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  }

  const handleCopyChunk = async (chunk) => {
    try {
      await navigator.clipboard.writeText(chunk.content || '')
      message.success('片段内容已复制')
    } catch {
      message.warning('当前浏览器不允许写入剪贴板')
    }
  }

  const documentColumns = [
    {
      title: '文档',
      dataIndex: 'source',
      ellipsis: true,
      render: (value, record) => {
        const status = buildDocumentStatus(record)
        return (
          <button
            type="button"
            className={`kb-document-button ${selectedDocument?.source === record.source ? 'is-active' : ''}`}
            onClick={() => setSelectedSource(record.source)}
          >
            <span className="kb-document-button__title">{value}</span>
            <span className="kb-document-button__meta">
              <Tag color={status.color}>{status.label}</Tag>
              <Tag>{record.knowledgeBaseName || DEFAULT_KNOWLEDGE_BASE_NAME}</Tag>
              <span>{record.chunkCount || 0} chunks</span>
            </span>
          </button>
        )
      },
    },
    { title: '大小', dataIndex: 'size', width: 90, render: formatBytes },
  ]

  const chunkColumns = [
    {
      title: '片段',
      dataIndex: 'chunkIndex',
      width: 92,
      render: (value, record) => (
        <Space direction="vertical" size={2}>
          <Text strong>#{value ?? '-'}</Text>
          <Tag color={record.origin === 'indexed' ? 'green' : record.origin === 'recall' ? 'blue' : record.origin === 'recent' ? 'cyan' : 'orange'}>
            {record.origin === 'indexed' ? '真实' : record.origin === 'recall' ? '命中' : record.origin === 'recent' ? '最近' : '预览'}
          </Tag>
          {record.chunkRole ? <Text type="secondary">{record.chunkRole}</Text> : null}
        </Space>
      ),
    },
    {
      title: '内容',
      dataIndex: 'content',
      render: (value, record) => (
        <div className="kb-chunk-cell">
          <div className="kb-chunk-cell__source">{record.knowledgeBaseName || DEFAULT_KNOWLEDGE_BASE_NAME} / {record.source}</div>
          <div className="kb-chunk-cell__text">{value}</div>
          <div className="kb-chunk-cell__meta">
            <span>{formatBytes(record.size)}</span>
            {Number.isFinite(Number(record.score)) ? <span>score {Number(record.score).toFixed(4)}</span> : null}
            {record.parentIndex !== undefined ? <span>parent {record.parentIndex}</span> : null}
          </div>
        </div>
      ),
    },
    {
      title: '操作',
      width: 108,
      render: (_, record) => (
        <Space>
          <Tooltip title="查看完整片段">
            <Button icon={<EyeOutlined />} onClick={() => setActiveChunk(record)} />
          </Tooltip>
          <Tooltip title="复制片段">
            <Button icon={<CopyOutlined />} onClick={() => handleCopyChunk(record)} />
          </Tooltip>
        </Space>
      ),
    },
  ]

  const recallColumns = [
    { title: '来源', dataIndex: 'source', width: 260, ellipsis: true, render: (value, record) => `${record.metadata?.knowledgeBaseName || DEFAULT_KNOWLEDGE_BASE_NAME} / ${record.metadata?.sourceName || value || '-'}` },
    { title: '分数', dataIndex: 'score', width: 110, render: (value) => (value === null || value === undefined ? '-' : Number(value).toFixed(4)) },
    { title: '内容', dataIndex: 'content', ellipsis: true },
  ]

  const renderHealthCard = (key, title, icon) => {
    const data = health[key] || {}
    const healthy = data.status === 'implemented' || data.configured
    return (
      <Card className="kb-health-card">
        <div className="kb-health-card__top">
          <span className="kb-health-card__icon">{icon}</span>
          <Tag color={healthy ? 'green' : 'orange'}>{data.status || (healthy ? 'configured' : 'pending')}</Tag>
        </div>
        <Statistic title={title} value={data.backend || data.provider || data.collection || '-'} />
        <Text type="secondary">{data.collection || data.model || data.uri || `configured: ${String(data.configured ?? true)}`}</Text>
      </Card>
    )
  }

  const renderIngestPanel = () => (
    <Card title="导入与切分策略" className="kb-panel-card">
      <Form
        form={ingestForm}
        layout="vertical"
        initialValues={{
          chunkMode: 'semantic',
          scene: 'campus_knowledge',
          knowledgeBaseId: DEFAULT_KNOWLEDGE_BASE_ID,
          chunkSize: DEFAULT_CHUNK_SIZE,
          chunkOverlap: DEFAULT_CHUNK_OVERLAP,
          parentChunkSize: PARENT_CHUNK_SIZE,
          parentChunkOverlap: PARENT_CHUNK_OVERLAP,
          childChunkSize: CHILD_CHUNK_SIZE,
          childChunkOverlap: CHILD_CHUNK_OVERLAP,
        }}
        onFinish={handleIngest}
        onValuesChange={(_, values) => {
          setPreviewSource(values.source || uploadFileList[0]?.name || '后台录入.txt')
          setPreviewText(values.content || previewText)
          setPreviewMode(values.chunkMode || 'semantic')
          setChunkSettings({
            chunkSize: values.chunkSize || DEFAULT_CHUNK_SIZE,
            chunkOverlap: values.chunkOverlap || DEFAULT_CHUNK_OVERLAP,
            parentChunkSize: values.parentChunkSize || PARENT_CHUNK_SIZE,
            parentChunkOverlap: values.parentChunkOverlap || PARENT_CHUNK_OVERLAP,
            childChunkSize: values.childChunkSize || CHILD_CHUNK_SIZE,
            childChunkOverlap: values.childChunkOverlap || CHILD_CHUNK_OVERLAP,
          })
        }}
      >
        <Form.Item
          name="embeddingModel"
          label="向量模型"
          rules={[{ required: true, message: '请选择已测试成功的向量模型' }]}
          extra="只显示系统配置中已测试成功的 ai.service.embedding.*。"
        >
          <Select
            options={embeddingModelOptions}
            placeholder="请先在系统配置中测试向量模型"
            notFoundContent="暂无已测试成功的向量模型"
          />
        </Form.Item>
        <Form.Item
          name="knowledgeBaseId"
          label="目标知识库"
          rules={[{ required: true, message: '请选择文档要导入的知识库' }]}
          extra="文档、chunk 和召回过滤都会使用这个知识库归属。"
        >
          <Select
            options={knowledgeBases.map((item) => ({
              value: item.id,
              label: `${item.name}（${item.chunkCount || 0} chunks）`,
            }))}
            onChange={setSelectedKnowledgeBaseId}
          />
        </Form.Item>
        <Row gutter={12}>
          <Col xs={24} md={12}>
            <Form.Item name="source" label="来源文件名">
              <Input placeholder="例如：校园卡服务.txt" onChange={(event) => setPreviewSource(event.target.value || '后台录入.txt')} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item name="scene" label="业务场景">
              <Input placeholder="campus_knowledge" />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="chunkMode" label="切分模式">
          <Segmented options={ingestModeOptions} onChange={setPreviewMode} />
        </Form.Item>
        <div className="kb-chunk-settings">
          <div className="kb-chunk-settings__head">
            <Text strong>索引参数</Text>
            <Text type="secondary">预计 {previewChunks.length} 个预览片段，平均 {previewAverageLength || 0} 字</Text>
          </div>
          <Row gutter={10}>
            <Col xs={12}>
              <Form.Item name="chunkSize" label="Chunk Size">
                <InputNumber min={200} max={3000} step={50} className="kb-full-control" />
              </Form.Item>
            </Col>
            <Col xs={12}>
              <Form.Item name="chunkOverlap" label="Overlap">
                <InputNumber min={0} max={800} step={10} className="kb-full-control" />
              </Form.Item>
            </Col>
            <Col xs={12}>
              <Form.Item name="parentChunkSize" label="Parent Size">
                <InputNumber min={600} max={5000} step={100} className="kb-full-control" />
              </Form.Item>
            </Col>
            <Col xs={12}>
              <Form.Item name="parentChunkOverlap" label="Parent Overlap">
                <InputNumber min={0} max={1200} step={20} className="kb-full-control" />
              </Form.Item>
            </Col>
            <Col xs={12}>
              <Form.Item name="childChunkSize" label="Child Size">
                <InputNumber min={120} max={1600} step={20} className="kb-full-control" />
              </Form.Item>
            </Col>
            <Col xs={12}>
              <Form.Item name="childChunkOverlap" label="Child Overlap">
                <InputNumber min={0} max={500} step={10} className="kb-full-control" />
              </Form.Item>
            </Col>
          </Row>
        </div>
        <Form.Item label="本地文件">
          <Upload
            accept=".docx,.txt,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
            beforeUpload={async (file) => {
              if (!isSupportedKnowledgeFile(file)) {
                message.warning('知识库暂时只支持上传 DOCX 或 TXT 文件')
                return Upload.LIST_IGNORE
              }
              setUploadFileList([file])
              ingestForm.setFieldsValue({ source: file.name })
              setPreviewSource(file.name)
              if (/\.txt$/i.test(file.name)) {
                const text = await readFileAsText(file)
                ingestForm.setFieldsValue({ content: text })
                setPreviewText(text)
              } else {
                setPreviewText('')
              }
              return false
            }}
            fileList={uploadFileList}
            maxCount={1}
            onRemove={() => {
              setUploadFileList([])
              return true
            }}
          >
            <Button icon={<UploadOutlined />}>选择 DOCX / TXT</Button>
          </Upload>
          <Text type="secondary">TXT 会立即生成片段预览；DOCX 入库后可通过真实 chunk 面板查看。</Text>
        </Form.Item>
        <Form.Item name="content" label="文档内容">
          <TextArea
            rows={9}
            placeholder="粘贴 TXT 文本内容，右侧会实时展示切分后的每个片段"
            onChange={(event) => setPreviewText(event.target.value)}
          />
        </Form.Item>
        <Form.Item name="tags" label="元数据标签">
          <Input placeholder="例如：校园卡, 后勤, 服务指南" />
        </Form.Item>
        <Button type="primary" htmlType="submit" icon={<CloudUploadOutlined />} loading={actionLoading} block>
          入库并索引
        </Button>
      </Form>
    </Card>
  )

  const renderRecallPanel = () => (
    <Card title="召回验证" className="kb-panel-card">
      <Form
        form={recallForm}
        layout="vertical"
        initialValues={{ ragStrategy: 'hybrid_search', intent: 'campus_search', topK: 5, similarityThreshold: 0.2, knowledgeBaseIds: [DEFAULT_KNOWLEDGE_BASE_ID] }}
        onFinish={handleRecallTest}
      >
        <Form.Item name="query" label="测试问题" rules={[{ required: true, message: '请输入要测试召回的问题' }]}>
          <TextArea rows={4} placeholder="例如：校园卡丢了怎么挂失？" />
        </Form.Item>
        <Form.Item name="keyword" label="关键词">
          <Input placeholder="可选；用于增强关键词召回" />
        </Form.Item>
        <Row gutter={12}>
          <Col xs={24} md={12}>
            <Form.Item name="ragStrategy" label="召回策略">
              <Select options={recallStrategyOptions} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item name="source" label="目标文档">
              <Select
                allowClear
                showSearch
                placeholder="全部文档"
                options={documents
                  .filter((item) => (valuesKnowledgeBaseMatch(item, recallKnowledgeBaseIds)))
                  .map((item) => ({ value: item.source, label: `${item.source}（${item.knowledgeBaseName || DEFAULT_KNOWLEDGE_BASE_NAME}）` }))}
              />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item
          name="knowledgeBaseIds"
          label="测试知识库"
          rules={[{ required: true, message: '请选择至少一个知识库' }]}
          extra="可以选择一个或多个知识库，召回结果会限定在这些知识库内。"
        >
          <Select
            mode="multiple"
            options={knowledgeBases.map((item) => ({
              value: item.id,
              label: `${item.name}（${item.chunkCount || 0} chunks）`,
            }))}
            onChange={(values) => setRecallKnowledgeBaseIds(values)}
          />
        </Form.Item>
        <Form.Item
          name="embeddingModel"
          label="向量模型"
          rules={[{ required: true, message: '请选择已测试成功的向量模型' }]}
          extra="召回会使用和入库一致的向量模型配置。"
        >
          <Select
            options={embeddingModelOptions}
            placeholder="请先在系统配置中测试向量模型"
            notFoundContent="暂无已测试成功的向量模型"
          />
        </Form.Item>
        <Row gutter={12}>
          <Col xs={24} md={10}>
            <Form.Item name="topK" label="Top K">
              <InputNumber min={1} max={20} className="kb-full-control" />
            </Form.Item>
          </Col>
          <Col xs={24} md={14}>
            <Form.Item name="similarityThreshold" label="相似度阈值">
              <Slider min={0} max={1} step={0.05} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="intent" label="意图标识">
          <Input placeholder="campus_search" />
        </Form.Item>
        <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={recallLoading} block>
          开始召回测试
        </Button>
      </Form>
    </Card>
  )

  const renderConvertPanel = () => (
    <Card title="转换为可入库文档" className="kb-panel-card">
      <Form form={convertForm} layout="vertical" onFinish={handleDocumentConvert}>
        <Alert
          className="rag-inline-alert"
          type="info"
          showIcon
          message="支持 PDF 转 DOCX、PPTX 转 DOCX；转换后的 DOCX 可继续作为知识库文档入库。"
        />
        <Form.Item label="文件" required>
          <Upload
            accept="application/pdf,.pdf,application/vnd.openxmlformats-officedocument.presentationml.presentation,.pptx"
            beforeUpload={(file) => {
              const lowerName = file.name.toLowerCase()
              if (!lowerName.endsWith('.pdf') && !lowerName.endsWith('.pptx')) {
                message.warning('请选择 PDF 或 PPTX 文件')
                return Upload.LIST_IGNORE
              }
              setConvertFileList([file])
              setConvertResult(null)
              return false
            }}
            fileList={convertFileList}
            maxCount={1}
            onRemove={() => {
              setConvertFileList([])
              setConvertResult(null)
              return true
            }}
          >
            <Button icon={<UploadOutlined />}>选择 PDF / PPTX</Button>
          </Upload>
        </Form.Item>
        <Button type="primary" htmlType="submit" icon={<FileTextOutlined />} loading={convertLoading} block>
          转换为 DOCX
        </Button>
      </Form>
      {convertResult ? (
        <div className="kb-convert-result">
          <Tag color="green">转换完成</Tag>
          <Text strong>{convertResult.fileName}</Text>
          <Text type="secondary">{formatBytes(convertResult.contentLength)}</Text>
          <Button type="primary" icon={<DownloadOutlined />} onClick={() => downloadConvertedFile(convertResult)}>
            下载结果
          </Button>
        </div>
      ) : null}
    </Card>
  )

  return (
    <div className="rag-manage knowledge-base-manage">
      <section className="kb-hero">
        <div className="kb-hero__content">
          <span className="rag-kicker">Knowledge Base Console</span>
          <Title level={1}>知识库方案工作台</Title>
          <p>参考成熟 RAG 知识库的导入、解析、切分、索引、召回验证和片段运维流程，直接展示每个切分片段。</p>
          <div className="kb-hero__tags">
            <Tag color="green">Milvus 向量库</Tag>
            <Tag color="blue">Preview Chunks</Tag>
            <Tag color="cyan">Parent-child</Tag>
            <Tag color="purple">Recall Test</Tag>
          </div>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={refresh} loading={bootLoading}>
            刷新知识库
          </Button>
          <Button icon={<SettingOutlined />} onClick={() => { window.location.href = '/ai/rag/framework' }}>
            框架配置
          </Button>
        </Space>
      </section>

      <Space direction="vertical" size="large" className="rag-full">
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} xl={6}>
            <Card className="kb-metric-card">
              <Statistic title="当前库文档" value={visibleDocuments.length} suffix="个" />
              <Text type="secondary">{selectedKnowledgeBase.name}</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card className="kb-metric-card">
              <Statistic title="真实 Chunk" value={totalChunkCount} suffix="段" />
              <Text type="secondary">来自向量库聚合统计</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card className="kb-metric-card">
              <Statistic title="索引内容量" value={formatBytes(indexedSize)} />
              <Text type="secondary">按 chunk 内容估算</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card className="kb-metric-card">
              <Statistic title="运行健康" value={healthScore} suffix="/3" />
              <Progress percent={Math.round((healthScore / 3) * 100)} showInfo={false} size="small" />
            </Card>
          </Col>
        </Row>

        <Card className="kb-panel-card">
          <div className="kb-section-head">
            <div>
              <Text strong>知识库空间</Text>
              <Text type="secondary">导入文档前先选择知识库，召回测试可选择一个或多个知识库</Text>
            </div>
            <Tag color="blue">当前：{selectedKnowledgeBase.name}</Tag>
          </div>
          <div className="kb-space-grid">
            <div className="kb-space-list">
              {knowledgeBases.map((item) => (
                <button
                  type="button"
                  key={item.id}
                  className={`kb-space-card ${item.id === selectedKnowledgeBaseId ? 'is-active' : ''}`}
                  onClick={() => {
                    setSelectedKnowledgeBaseId(item.id)
                    setSelectedSource('')
                  }}
                >
                  <span className="kb-space-card__title">{item.name}</span>
                  <span className="kb-space-card__desc">{item.description || '暂无描述'}</span>
                  <span className="kb-space-card__meta">
                    <Tag>{item.documentCount || 0} 文档</Tag>
                    <Tag color="cyan">{item.chunkCount || 0} chunks</Tag>
                    <span>{formatBytes(item.size)}</span>
                  </span>
                </button>
              ))}
            </div>
            <div className="kb-space-create">
              <Text strong>新建知识库</Text>
              <Input
                placeholder="例如：教学资料库"
                value={newKnowledgeBaseName}
                onChange={(event) => setNewKnowledgeBaseName(event.target.value)}
              />
              <Input
                placeholder="用途描述，例如：课程教材、题库与教学资料"
                value={newKnowledgeBaseDescription}
                onChange={(event) => setNewKnowledgeBaseDescription(event.target.value)}
              />
              <Button type="primary" icon={<DatabaseOutlined />} onClick={handleCreateKnowledgeBase}>
                创建知识库
              </Button>
            </div>
          </div>
        </Card>

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={8}>{renderHealthCard('vector', '向量库', <DatabaseOutlined />)}</Col>
          <Col xs={24} lg={8}>{renderHealthCard('embedding', 'Embedding', <ApiOutlined />)}</Col>
          <Col xs={24} lg={8}>{renderHealthCard('graph', '图谱存储', <BranchesOutlined />)}</Col>
        </Row>

        <Row gutter={[16, 16]} align="stretch">
          <Col xs={24} xl={8}>
            <Space direction="vertical" size="large" className="rag-full">
              {renderIngestPanel()}
              {renderConvertPanel()}
            </Space>
          </Col>
          <Col xs={24} xl={16}>
            <Space direction="vertical" size="large" className="rag-full">
              <Card className="kb-panel-card kb-documents-card">
                <div className="kb-section-head">
                  <div>
                    <Text strong>文档与索引</Text>
                    <Text type="secondary">选择文档后查看真实入库的每个 chunk</Text>
                  </div>
                  <Button icon={<ReloadOutlined />} onClick={() => refreshChunks(selectedDocument?.source)} loading={chunkLoading}>
                    刷新片段
                  </Button>
                </div>
                <Row gutter={[16, 16]}>
                  <Col xs={24} lg={10}>
                    <Table
                      className="kb-document-table"
                      rowKey={(record) => record.source}
                      columns={documentColumns}
                      dataSource={visibleDocuments}
                      pagination={{ pageSize: 6 }}
                      loading={bootLoading}
                      locale={{ emptyText: <Empty description="暂无入库文档" /> }}
                    />
                  </Col>
                  <Col xs={24} lg={14}>
                    <div className="kb-document-detail">
                      {selectedDocument ? (
                        <>
                          <div className="kb-document-detail__top">
                            <FileSearchOutlined />
                            <div>
                              <Text strong>{selectedDocument.source}</Text>
                              <Text type="secondary">{selectedDocument.knowledgeBaseName || DEFAULT_KNOWLEDGE_BASE_NAME} / {selectedDocument.collection || knowledgeBaseMeta.chunkIndexPath || '-'}</Text>
                            </div>
                          </div>
                          <Row gutter={[12, 12]}>
                            <Col span={8}><Statistic title="Chunk" value={selectedDocument.chunkCount || 0} /></Col>
                            <Col span={8}><Statistic title="大小" value={formatBytes(selectedDocument.size)} /></Col>
                            <Col span={8}><Statistic title="更新" value={formatTime(selectedDocument.updatedAt)} /></Col>
                          </Row>
                          <div className="kb-pipeline">
                            {['上传', '解析', '切分', '向量化', '召回'].map((item, index) => (
                              <div className="kb-pipeline__step" key={item}>
                                <CheckCircleOutlined />
                                <span>{index + 1}. {item}</span>
                              </div>
                            ))}
                          </div>
                          <Collapse
                            size="small"
                            items={[{
                              key: 'kb-meta',
                              label: '知识库运行元数据',
                              children: <pre className="rag-code-block">{JSON.stringify(knowledgeBaseMeta, null, 2)}</pre>,
                            }]}
                          />
                        </>
                      ) : (
                        <Empty description="选择或导入一个文档后查看详情" />
                      )}
                    </div>
                  </Col>
                </Row>
              </Card>

              <Card className="kb-panel-card">
                <div className="kb-section-head">
                  <div>
                    <Text strong>切分片段</Text>
                    <Text type="secondary">真实入库、实时预览、最近入库和召回命中都会汇总在这里</Text>
                  </div>
                  <Space wrap>
                    <Input
                      className="kb-chunk-search"
                      prefix={<SearchOutlined />}
                      placeholder="搜索片段内容 / 来源 / metadata"
                      value={chunkSearch}
                      onChange={(event) => setChunkSearch(event.target.value)}
                    />
                    <Select
                      className="kb-origin-select"
                      value={chunkOriginFilter}
                      options={sourceTypeOptions}
                      onChange={setChunkOriginFilter}
                      suffixIcon={<FilterOutlined />}
                    />
                  </Space>
                </div>
                <Table
                  rowKey={(record) => `${record.origin}-${record.id}`}
                  columns={chunkColumns}
                  dataSource={filteredChunks}
                  loading={chunkLoading}
                  pagination={{ pageSize: 8 }}
                  locale={{ emptyText: <Empty description="暂无可展示片段；粘贴文本或选择已入库文档后查看" /> }}
                />
              </Card>

              <Card className="kb-panel-card">
                <Tabs
                  items={[
                    {
                      key: 'recall',
                      label: '召回结果',
                      children: recallError ? (
                        <Alert type="error" showIcon message="召回测试失败" description={recallError} />
                      ) : recallResult ? (
                        <Space direction="vertical" size="large" className="rag-full">
                          <div className="rag-agent-test-status">
                            <Tag color="green">召回完成</Tag>
                            <Tag color="cyan">{recallResult.metadata?.strategyLabel || recallResult.strategy}</Tag>
                            <Tag color="blue">命中：{(recallResult.documents || []).length}</Tag>
                            <Tag color="purple">库覆盖：{recallCoverage}%</Tag>
                            <Tag>平均分：{recallAverageScore ? recallAverageScore.toFixed(4) : '-'}</Tag>
                            {recallResult.metadata?.backend ? <Tag>后端：{recallResult.metadata.backend}</Tag> : null}
                          </div>
                          <div className="kb-recall-quality">
                            <div>
                              <Text strong>召回质量</Text>
                              <Text type="secondary">按当前测试知识库覆盖率和命中分数快速判断配置是否合理</Text>
                            </div>
                            <Progress percent={recallCoverage} size="small" />
                          </div>
                          <Table
                            rowKey={(record) => record.id || `${record.source}-${record.content}`}
                            columns={recallColumns}
                            dataSource={recallResult.documents || []}
                            pagination={{ pageSize: 5 }}
                          />
                          <Collapse
                            items={[{
                              key: 'metadata',
                              label: 'Trace / Metadata',
                              children: <pre className="rag-code-block">{JSON.stringify({ trace: recallResult.trace, metadata: recallResult.metadata }, null, 2)}</pre>,
                            }]}
                          />
                        </Space>
                      ) : (
                        <Empty description="输入测试问题后，召回命中的知识片段会显示在这里" />
                      ),
                    },
                    {
                      key: 'ingest',
                      label: '最近入库',
                      children: ingestResult ? (
                        <Space direction="vertical" size="middle" className="rag-full knowledge-base-ingest-result">
                          <div className="rag-agent-test-status">
                            <Tag color="green">最近入库完成</Tag>
                            <Tag color="blue">文档：{ingestResult.storedCount || 0}</Tag>
                            <Tag color="cyan">Chunk：{ingestResult.indexedChunkCount || 0}</Tag>
                            {ingestResult.indexPath ? <Tag>索引：{ingestResult.indexPath}</Tag> : null}
                          </div>
                          <Collapse
                            items={[{
                              key: 'ingest-detail',
                              label: '存储文件 / Trace',
                              children: (
                                <pre className="rag-code-block">
                                  {JSON.stringify({
                                    documents: ingestResult.documents || [],
                                    storedFiles: ingestResult.storedFiles || [],
                                    trace: ingestResult.trace || [],
                                  }, null, 2)}
                                </pre>
                              ),
                            }]}
                          />
                        </Space>
                      ) : (
                        <Empty description="入库后会展示最近一次处理结果" />
                      ),
                    },
                  ]}
                />
              </Card>
            </Space>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={8}>{renderRecallPanel()}</Col>
          <Col xs={24} xl={16}>
            <Card title="成熟知识库能力清单" className="kb-panel-card">
              <div className="kb-checklist">
                {[
                  ['数据源管理', '按知识库组织 DOCX、TXT、PDF/PPTX 转换文档，并保留来源文件名。'],
                  ['处理规则', '可配置 chunk size、overlap、父子切片参数，并实时预览切分结果。'],
                  ['索引与向量化', '每个 chunk 写入知识库、来源、标签、场景和切分配置 metadata。'],
                  ['检索设置', '支持多知识库召回测试、Top K、阈值、混合检索和父子片段策略。'],
                  ['质量评估', '召回结果展示 score、库覆盖率、trace、metadata，方便调参。'],
                  ['发布治理', '当前以知识库归属和 metadata 隔离；后续可扩展权限、版本和发布状态。'],
                ].map(([title, desc]) => (
                  <div className="kb-checklist__item" key={title}>
                    <CheckCircleOutlined />
                    <div>
                      <Text strong>{title}</Text>
                      <Text type="secondary">{desc}</Text>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          </Col>
        </Row>
      </Space>

      <Drawer
        title={activeChunk ? `片段详情 #${activeChunk.chunkIndex ?? '-'}` : '片段详情'}
        open={Boolean(activeChunk)}
        width={620}
        onClose={() => setActiveChunk(null)}
        extra={activeChunk ? (
          <Button icon={<CopyOutlined />} onClick={() => handleCopyChunk(activeChunk)}>
            复制内容
          </Button>
        ) : null}
      >
        {activeChunk ? (
          <Space direction="vertical" size="large" className="rag-full">
            <div className="kb-drawer-tags">
              <Tag color="blue">{activeChunk.source}</Tag>
              <Tag>{activeChunk.origin}</Tag>
              <Tag>{activeChunk.chunkRole}</Tag>
              <Tag>{formatBytes(activeChunk.size)}</Tag>
              {Number.isFinite(Number(activeChunk.score)) ? <Tag color="purple">score {Number(activeChunk.score).toFixed(4)}</Tag> : null}
            </div>
            <div className="kb-chunk-content">{activeChunk.content}</div>
            <Collapse
              items={[{
                key: 'metadata',
                label: 'Metadata',
                children: <pre className="rag-code-block">{JSON.stringify(activeChunk.metadata || {}, null, 2)}</pre>,
              }]}
            />
          </Space>
        ) : null}
      </Drawer>
    </div>
  )
}

export default KnowledgeBaseManage
