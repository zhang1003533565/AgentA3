import { useEffect, useState } from 'react'
import {
  Button,
  Checkbox,
  Drawer,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Modal,
  Pagination,
  Popconfirm,
  Radio,
  Select,
  Slider,
  Space,
  Switch,
  Tag,
  Tooltip,
  Upload,
  message,
} from 'antd'
import {
  ApiOutlined,
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CheckCircleFilled,
  CheckCircleOutlined,
  CloseCircleFilled,
  DatabaseOutlined,
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  ExperimentOutlined,
  ExclamationCircleFilled,
  EyeOutlined,
  FileTextOutlined,
  FolderOutlined,
  GlobalOutlined,
  InfoCircleOutlined,
  LoadingOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PictureOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  SettingOutlined,
  StarFilled,
  UploadOutlined,
} from '@ant-design/icons'
import {
  getDatasets, getDataset, createDataset, updateDataset, deleteDataset,
  getDocuments, getDocument, createDocument, deleteDocument, toggleDocument,
  pauseDocument, renameDocument, archiveDocument, unarchiveDocument, retryDocuments,
  getSegments, getSegment, updateSegment, deleteSegment, toggleSegment,
  createSegment, batchToggleSegments, batchDeleteSegments,
  getChildChunks,
  convertPdf, convertPpt,
  runRagRecallTest,
  getRagVectorStoreHealth, getRagEmbeddingHealth, getRagGraphStoreHealth,
  getSystemConfigList,
} from '../../../api/dataset'
import './KnowledgeBaseManage.css'

const { TextArea } = Input

// ======================== Constants ========================

const AI_MODEL_CONFIG_PATTERN = /^ai\.service\.embedding(?:\.([A-Za-z0-9_-]+))?\.(provider|base-url|api-key|model)$/
const AI_TESTED_MODEL_PREFIXES_KEY = 'ai_tested_model_prefixes_v1'

const indexingStatusMap = {
  waiting:    { label: '等待中', cls: 'warning' },
  parsing:    { label: '解析中', cls: 'processing' },
  cleaning:   { label: '清洗中', cls: 'processing' },
  splitting:  { label: '切分中', cls: 'processing' },
  indexing:   { label: '索引中', cls: 'processing' },
  completed:  { label: '可用',   cls: 'success' },
  error:      { label: '错误',   cls: 'error' },
  paused:     { label: '已暂停', cls: 'warning' },
}

const retrievalStrategyOptions = [
  { value: 'hybrid_search', label: '混合检索' },
  { value: 'metadata_filter_rag', label: '元数据过滤' },
  { value: 'knowledge_base_router_rag', label: '知识库路由' },
  { value: 'contextual_compression_rag', label: '上下文压缩' },
  { value: 'time_weighted_rag', label: '时间加权' },
  { value: 'parent_child', label: '父子切片' },
  { value: 'reranking', label: '重排序' },
  { value: 'naive_rag', label: '基础检索' },
  { value: 'multi_query_rag', label: '多查询' },
]

const WIZARD_STEPS = [
  { num: 1, label: '数据来源' },
  { num: 2, label: '索引配置' },
  { num: 3, label: '完成' },
]

// ======================== Helpers ========================

const formatCount = (n) => {
  const num = Number(n) || 0
  if (num >= 1000) return `${(num / 1000).toFixed(1)}k`
  return String(num)
}

const formatTime = (t) => {
  if (!t) return '-'
  try { return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }
  catch { return '-' }
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

const getFileExtension = (name) => {
  if (!name) return ''
  const parts = name.split('.')
  return parts.length > 1 ? parts.pop().toUpperCase() : ''
}

const extractRecords = (res) => {
  if (!res) return []
  if (Array.isArray(res.data)) return res.data
  if (res.data?.records) return res.data.records
  if (res.data?.list) return res.data.list
  if (res.data?.chunks) return res.data.chunks
  if (res.data?.items) return res.data.items
  if (res.data?.content) return res.data.content
  return []
}

const extractTotal = (res) => {
  if (res?.data?.total !== undefined) return res.data.total
  if (res?.data?.totalElements !== undefined) return res.data.totalElements
  if (Array.isArray(res?.data)) return res.data.length
  if (Array.isArray(res?.data?.records)) return res.data.records.length
  if (Array.isArray(res?.data?.list)) return res.data.list.length
  if (Array.isArray(res?.data?.chunks)) return res.data.chunks.length
  if (Array.isArray(res?.data?.items)) return res.data.items.length
  if (Array.isArray(res?.data?.content)) return res.data.content.length
  return 0
}

const readTestedModelPrefixSet = () => {
  try {
    const raw = localStorage.getItem(AI_TESTED_MODEL_PREFIXES_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return new Set(Object.keys(parsed || {}))
  } catch { return new Set() }
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
    .filter((g) => testedPrefixes.has(g.configPrefix))
    .filter((g) => g.configs.provider && g.configs['base-url'] && g.configs['api-key'] && g.configs.model)
    .map((g) => ({ value: g.configPrefix, label: `${g.configs.model}（${g.configs.provider}）` }))
}

const readFileAsBase64 = (file) => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => {
    const result = String(reader.result || '')
    resolve(result.includes(',') ? result.split(',').pop() : result)
  }
  reader.onerror = reject
  reader.readAsDataURL(file)
})

// ======================== Component ========================

function KnowledgeBaseManage() {
  // ===== View state =====
  const [creatingWizard, setCreatingWizard] = useState(false)
  const [wizardStep, setWizardStep] = useState(1)
  const [view, setView] = useState('list')
  const [detailTab, setDetailTab] = useState('documents')
  const [currentDocument, setCurrentDocument] = useState(null)

  // ===== Wizard state =====
  const [wizSourceType, setWizSourceType] = useState('file')
  const [wizFiles, setWizFiles] = useState([])
  const [wizName, setWizName] = useState('')
  const [wizDescription, setWizDescription] = useState('')
  const [wizIndexType, setWizIndexType] = useState('high_quality')
  const [wizChunkStructure, setWizChunkStructure] = useState('text_model')
  const [wizRetrievalMethod, setWizRetrievalMethod] = useState('hybrid_search')
  const [wizChunkSize, setWizChunkSize] = useState(800)
  const [wizChunkOverlap, setWizChunkOverlap] = useState(120)
  const [wizProcessMode, setWizProcessMode] = useState('automatic')
  const [wizCreating, setWizCreating] = useState(false)
  const [wizCreatedDataset, setWizCreatedDataset] = useState(null)
  const [wizDragOver, setWizDragOver] = useState(false)
  const [emptyKbModalVisible, setEmptyKbModalVisible] = useState(false)
  const [emptyKbForm] = Form.useForm()
  const [emptyKbLoading, setEmptyKbLoading] = useState(false)

  // ===== Data =====
  const [datasets, setDatasets] = useState([])
  const [datasetTotal, setDatasetTotal] = useState(0)
  const [datasetPage, setDatasetPage] = useState(1)
  const [datasetSearch, setDatasetSearch] = useState('')
  const [currentDataset, setCurrentDataset] = useState(null)
  const [documents, setDocuments] = useState([])
  const [documentTotal, setDocumentTotal] = useState(0)
  const [documentPage, setDocumentPage] = useState(1)
  const [documentSearch, setDocumentSearch] = useState('')
  const [docStatusFilter, setDocStatusFilter] = useState('all')
  const [segments, setSegments] = useState([])
  const [segmentTotal, setSegmentTotal] = useState(0)
  const [segmentPage, setSegmentPage] = useState(1)
  const [segmentSearch, setSegmentSearch] = useState('')

  // ===== Loading =====
  const [listLoading, setListLoading] = useState(false)
  const [docLoading, setDocLoading] = useState(false)
  const [segLoading, setSegLoading] = useState(false)

  // ===== Modals =====
  const [datasetModalVisible, setDatasetModalVisible] = useState(false)
  const [editingDataset, setEditingDataset] = useState(null)
  const [datasetModalLoading, setDatasetModalLoading] = useState(false)
  const [documentModalVisible, setDocumentModalVisible] = useState(false)
  const [documentModalLoading, setDocumentModalLoading] = useState(false)
  const [segmentDrawerVisible, setSegmentDrawerVisible] = useState(false)
  const [activeSegment, setActiveSegment] = useState(null)
  const [segmentEditDrawerVisible, setSegmentEditDrawerVisible] = useState(false)
  const [segmentEditLoading, setSegmentEditLoading] = useState(false)
  const [uploadFileList, setUploadFileList] = useState([])
  const [convertResult, setConvertResult] = useState(null)
  const [convertLoading, setConvertLoading] = useState(false)

  // ===== Forms =====
  const [datasetForm] = Form.useForm()
  const [documentForm] = Form.useForm()
  const [segmentEditForm] = Form.useForm()
  const [recallForm] = Form.useForm()
  const [settingsForm] = Form.useForm()

  // ===== Health & Recall =====
  const [health, setHealth] = useState({})
  const [recallLoading, setRecallLoading] = useState(false)
  const [recallResult, setRecallResult] = useState(null)

  // ===== Embedding models =====
  const [embeddingModelOptions, setEmbeddingModelOptions] = useState([])

  // ===== NEW: Document batch & sort =====
  const [selectedDocIds, setSelectedDocIds] = useState([])
  const [docSortBy, setDocSortBy] = useState('upload_time')

  // ===== NEW: Segment batch & filter =====
  const [selectedSegmentIds, setSelectedSegmentIds] = useState([])
  const [segmentStatusFilter, setSegmentStatusFilter] = useState('all')
  const [newSegmentVisible, setNewSegmentVisible] = useState(false)
  const [newSegmentForm] = Form.useForm()
  const [newSegmentLoading, setNewSegmentLoading] = useState(false)
  const [segmentImages, setSegmentImages] = useState([])

  // ===== NEW: Settings retrieval params =====
  const [settingsRetrievalMethod, setSettingsRetrievalMethod] = useState('hybrid_search')
  const [settingsTopK, setSettingsTopK] = useState(2)
  const [settingsScoreThreshold, setSettingsScoreThreshold] = useState(0.7)
  const [settingsScoreThresholdEnabled, setSettingsScoreThresholdEnabled] = useState(true)
  const [settingsRerankEnable, setSettingsRerankEnable] = useState(false)
  const [settingsRerankModel, setSettingsRerankModel] = useState('')
  const [settingsRerankingMode, setSettingsRerankingMode] = useState('rerank_model')
  const [settingsVectorWeight, setSettingsVectorWeight] = useState(0.7)
  const [settingsKeywordNumber, setSettingsKeywordNumber] = useState(10)
  const [settingsRetrievalOpen, setSettingsRetrievalOpen] = useState(true)

  // ===== NEW: Hit testing v2 =====
  const [hitQuery, setHitQuery] = useState('')
  const [hitTestingRecords, setHitTestingRecords] = useState([])
  const [hitResults, setHitResults] = useState([])
  const [hitLoading, setHitLoading] = useState(false)
  const [hitRetrievalOpen, setHitRetrievalOpen] = useState(false)
  const [hitTopK, setHitTopK] = useState(5)
  const [hitScoreThreshold, setHitScoreThreshold] = useState(0.2)
  const [hitRagStrategy, setHitRagStrategy] = useState('hybrid_search')

  // ===== Watch form fields =====
  const watchDataSourceType = Form.useWatch('dataSourceType', documentForm) || 'text_input'
  const watchProcessMode = Form.useWatch('processMode', documentForm) || 'automatic'

  // ======================== Loaders ========================

  const loadDatasets = async (page = 1, keyword = '') => {
    setListLoading(true)
    try {
      const res = await getDatasets({ current: page, size: 20, keyword })
      setDatasets(extractRecords(res))
      setDatasetTotal(extractTotal(res))
    } catch (err) { message.error(err.message || '加载知识库失败') }
    finally { setListLoading(false) }
  }

  const loadDatasetDetail = async (id) => {
    try {
      const res = await getDataset(id)
      setCurrentDataset(res.data)
      const d = res.data
      settingsForm.setFieldsValue({
        name: d.name,
        description: d.description,
        indexingTechnique: d.indexingTechnique,
        embeddingModel: d.embeddingModel,
        chunkStructure: d.chunkStructure,
        permission: d.permission,
        retrievalModel: d.retrievalModel,
      })
      // Sync retrieval settings from dataset data
      try {
        const rm = typeof d.retrievalModel === 'string' ? JSON.parse(d.retrievalModel) : d.retrievalModel
        if (rm) {
          if (rm.search_method) setSettingsRetrievalMethod(rm.search_method)
          if (rm.top_k) setSettingsTopK(rm.top_k)
          if (rm.score_threshold !== undefined) setSettingsScoreThreshold(rm.score_threshold)
          if (rm.score_threshold_enabled !== undefined) setSettingsScoreThresholdEnabled(rm.score_threshold_enabled)
          if (rm.rerank_enable !== undefined) setSettingsRerankEnable(rm.rerank_enable)
          if (rm.rerank_model) setSettingsRerankModel(rm.rerank_model)
          if (rm.reranking_mode) setSettingsRerankingMode(rm.reranking_mode)
          if (rm.vector_weight !== undefined) setSettingsVectorWeight(rm.vector_weight)
          if (rm.keyword_number !== undefined) setSettingsKeywordNumber(rm.keyword_number)
        }
      } catch { /* ignore parse errors */ }
    } catch (err) { message.error(err.message) }
  }

  const loadDocuments = async (datasetId, page = 1, keyword = '', sortBy = '') => {
    setDocLoading(true)
    try {
      const res = await getDocuments(datasetId, { current: page, size: 20, keyword, sortBy: sortBy || docSortBy })
      setDocuments(extractRecords(res))
      setDocumentTotal(extractTotal(res))
    } catch (err) { message.error(err.message) }
    finally { setDocLoading(false) }
  }

  const loadSegments = async (documentId, page = 1, keyword = '') => {
    setSegLoading(true)
    try {
      const res = await getSegments(documentId, { current: page, size: 20, keyword })
      setSegments(extractRecords(res))
      setSegmentTotal(extractTotal(res))
    } catch (err) { message.error(err.message) }
    finally { setSegLoading(false) }
  }

  const loadHealth = async () => {
    const results = await Promise.allSettled([
      getRagVectorStoreHealth(), getRagEmbeddingHealth(), getRagGraphStoreHealth(),
    ])
    setHealth({
      vector: results[0].status === 'fulfilled' ? results[0].value?.data : {},
      embedding: results[1].status === 'fulfilled' ? results[1].value?.data : {},
      graph: results[2].status === 'fulfilled' ? results[2].value?.data : {},
    })
  }

  const loadEmbeddingOptions = async () => {
    try {
      const res = await getSystemConfigList({ current: 1, size: 500, prefixes: 'ai.service.embedding' })
      setEmbeddingModelOptions(buildEmbeddingModelOptions(res.data?.records || []))
    } catch { /* ignore */ }
  }

  // ======================== Init ========================

  useEffect(() => {
    loadDatasets()
    loadHealth()
    loadEmbeddingOptions()
  }, [])

  // ======================== Navigation ========================

  const enterDataset = (dataset) => {
    setCurrentDataset(dataset)
    setView('detail')
    setDetailTab('documents')
    setCurrentDocument(null)
    setSelectedDocIds([])
    loadDatasetDetail(dataset.id)
    loadDocuments(dataset.id)
  }

  const backToList = () => {
    setView('list')
    setCurrentDataset(null)
    setCurrentDocument(null)
    setCreatingWizard(false)
    setSelectedDocIds([])
    loadDatasets(datasetPage, datasetSearch)
  }

  const enterDocument = (doc) => {
    setCurrentDocument(doc)
    setSelectedSegmentIds([])
    loadSegments(doc.id)
  }

  const backToDocuments = () => {
    setCurrentDocument(null)
    setSelectedSegmentIds([])
    if (currentDataset) loadDocuments(currentDataset.id, documentPage, documentSearch)
  }

  const switchDetailTab = (tab) => {
    setDetailTab(tab)
    setCurrentDocument(null)
    setSelectedDocIds([])
    if (tab === 'documents' && currentDataset) loadDocuments(currentDataset.id)
  }

  // ======================== Wizard Handlers ========================

  const openCreateWizard = () => {
    setCreatingWizard(true)
    setWizardStep(1)
    setWizFiles([])
    setWizName('')
    setWizDescription('')
    setWizIndexType('high_quality')
    setWizChunkStructure('text_model')
    setWizRetrievalMethod('hybrid_search')
    setWizProcessMode('automatic')
    setWizSourceType('file')
    setWizChunkSize(800)
    setWizChunkOverlap(120)
    setWizCreatedDataset(null)
    setWizCreating(false)
  }

  const closeWizard = () => {
    setCreatingWizard(false)
    setWizardStep(1)
    setWizFiles([])
  }

  const handleWizardFileSelect = (e) => {
    const files = Array.from(e.target.files || [])
    if (files.length > 0) {
      setWizFiles((prev) => [...prev, ...files])
      if (!wizName) setWizName(files[0].name.replace(/\.[^.]+$/, ''))
    }
    e.target.value = ''
  }

  const handleWizardFileDrop = (e) => {
    e.preventDefault()
    setWizDragOver(false)
    const files = Array.from(e.dataTransfer.files || [])
    if (files.length > 0) {
      setWizFiles((prev) => [...prev, ...files])
      if (!wizName) setWizName(files[0].name.replace(/\.[^.]+$/, ''))
    }
  }

  const removeWizFile = (idx) => {
    setWizFiles((prev) => prev.filter((_, i) => i !== idx))
  }

  const wizardNextStep = () => {
    if (wizardStep === 1) {
      if (wizSourceType === 'file' && wizFiles.length === 0) {
        message.warning('请至少选择一个文件')
        return
      }
      setWizardStep(2)
    } else if (wizardStep === 2) {
      if (!wizName.trim()) {
        message.warning('请输入知识库名称')
        return
      }
      handleWizardCreate()
    }
  }

  const wizardPrevStep = () => {
    if (wizardStep > 1) setWizardStep(wizardStep - 1)
  }

  const handleWizardCreate = async () => {
    setWizCreating(true)
    try {
      const datasetPayload = {
        name: wizName.trim(),
        description: wizDescription.trim(),
        indexingTechnique: wizIndexType,
        chunkStructure: wizChunkStructure,
        permission: 'only_me',
      }
      const dsRes = await createDataset(datasetPayload)
      const datasetId = dsRes.data?.id || dsRes.data
      setWizCreatedDataset({ id: datasetId, name: wizName })

      if (wizFiles.length > 0) {
        const failedDocs = []
        for (const file of wizFiles) {
          try {
            const contentBase64 = await readFileAsBase64(file)
            if (!contentBase64) {
              failedDocs.push({ name: file.name, reason: '文件读取失败，请重试' })
              continue
            }
            const docPayload = {
              name: file.name,
              dataSourceType: 'upload_file',
              docForm: wizChunkStructure,
              processMode: wizProcessMode,
              contentBase64,
            }
            if (wizProcessMode === 'custom') {
              docPayload.processRules = JSON.stringify({ chunkSize: wizChunkSize, chunkOverlap: wizChunkOverlap })
            }
            await createDocument(datasetId, docPayload)
          } catch (err) {
            const reason = err?.response?.data?.message || err.message || '未知错误'
            failedDocs.push({ name: file.name, reason })
            console.error('Failed to create document:', file.name, err)
          }
        }
        if (failedDocs.length > 0) {
          const detail = failedDocs.map(d => `${d.name}: ${d.reason}`).join('；')
          message.warning(`${failedDocs.length} 个文档创建失败 — ${detail}`, 8)
        }
      }

      setWizardStep(3)
      message.success('知识库创建成功')
    } catch (err) {
      message.error(err.message || '创建知识库失败')
    } finally {
      setWizCreating(false)
    }
  }

  const handleWizardFinish = () => {
    if (wizCreatedDataset?.id) {
      setCreatingWizard(false)
      loadDatasets(1, '')
      enterDataset({ id: wizCreatedDataset.id, name: wizCreatedDataset.name })
    } else {
      closeWizard()
      loadDatasets(1, '')
    }
  }

  const handleCreateEmptyKb = async () => {
    try {
      const values = await emptyKbForm.validateFields()
      setEmptyKbLoading(true)
      await createDataset({
        name: values.name.trim(),
        description: '',
        indexingTechnique: 'high_quality',
        chunkStructure: 'text_model',
        permission: 'only_me',
      })
      message.success('空知识库已创建')
      setEmptyKbModalVisible(false)
      emptyKbForm.resetFields()
      loadDatasets(1, '')
    } catch (err) {
      if (err?.message && !err?.errorFields) message.error(err.message)
    } finally {
      setEmptyKbLoading(false)
    }
  }

  // ======================== Dataset CRUD ========================

  const openCreateDataset = () => {
    setEditingDataset(null)
    datasetForm.resetFields()
    setDatasetModalVisible(true)
  }

  const openEditDataset = (dataset, e) => {
    e?.stopPropagation()
    setEditingDataset(dataset)
    datasetForm.setFieldsValue({
      name: dataset.name,
      description: dataset.description,
      indexingTechnique: dataset.indexingTechnique,
      embeddingModel: dataset.embeddingModel,
      chunkStructure: dataset.chunkStructure,
      permission: dataset.permission,
    })
    setDatasetModalVisible(true)
  }

  const handleDatasetModalOk = async () => {
    try {
      const values = await datasetForm.validateFields()
      setDatasetModalLoading(true)
      if (editingDataset) {
        await updateDataset(editingDataset.id, values)
        message.success('知识库已更新')
      } else {
        await createDataset(values)
        message.success('知识库已创建')
      }
      setDatasetModalVisible(false)
      loadDatasets(datasetPage, datasetSearch)
    } catch (err) {
      if (err?.message && !err?.errorFields) message.error(err.message)
    } finally { setDatasetModalLoading(false) }
  }

  const handleDeleteDataset = async (dataset, e) => {
    e?.stopPropagation()
    try {
      await deleteDataset(dataset.id)
      message.success('知识库已删除')
      loadDatasets(datasetPage, datasetSearch)
    } catch (err) { message.error(err.message) }
  }

  // ======================== Document CRUD ========================

  const openCreateDocument = () => {
    documentForm.resetFields()
    documentForm.setFieldsValue({ dataSourceType: 'text_input', docForm: 'text_model', processMode: 'automatic' })
    setUploadFileList([])
    setConvertResult(null)
    setDocumentModalVisible(true)
  }

  const handleDocumentModalOk = async () => {
    try {
      const values = await documentForm.validateFields()
      setDocumentModalLoading(true)
      const payload = {
        name: values.name,
        dataSourceType: values.dataSourceType,
        docForm: values.docForm || 'text_model',
        processMode: values.processMode || 'automatic',
        embeddingModel: values.embeddingModel,
        docMetadata: values.docMetadata,
      }
      if (values.dataSourceType === 'text_input') {
        if (!values.textContent?.trim()) {
          message.warning('请输入文本内容')
          return
        }
        payload.content = values.textContent
      } else {
        const fileList = uploadFileList
        let fileObj = null
        if (fileList.length > 0) {
          fileObj = fileList[0].originFileObj || fileList[0]
        }
        if (fileObj) {
          payload.contentBase64 = await readFileAsBase64(fileObj)
        } else if (convertResult?.contentBase64) {
          payload.contentBase64 = convertResult.contentBase64
        }
        if (!payload.contentBase64) {
          message.warning('请选择要上传的文件')
          return
        }
      }
      if (values.processMode === 'custom') {
        payload.processRules = JSON.stringify({ chunkSize: values.chunkSize, chunkOverlap: values.chunkOverlap })
      } else if (values.processMode === 'hierarchical') {
        payload.processRules = JSON.stringify({
          parentChunkSize: values.parentChunkSize, parentChunkOverlap: values.parentChunkOverlap,
          childChunkSize: values.childChunkSize, childChunkOverlap: values.childChunkOverlap,
        })
      }
      await createDocument(currentDataset.id, payload)
      message.success('文档创建成功')
      setDocumentModalVisible(false)
      loadDocuments(currentDataset.id)
      loadDatasetDetail(currentDataset.id)
    } catch (err) {
      if (err?.message && !err?.errorFields) message.error(err.message)
    } finally { setDocumentModalLoading(false) }
  }

  const handleDeleteDocument = async (doc, e) => {
    e?.stopPropagation()
    try {
      await deleteDocument(doc.id)
      message.success('文档已删除')
      loadDocuments(currentDataset.id, documentPage, documentSearch)
      loadDatasetDetail(currentDataset.id)
    } catch (err) { message.error(err.message) }
  }

  const handleToggleDocument = async (doc, enabled) => {
    try {
      await toggleDocument(doc.id, enabled)
      message.success(enabled ? '文档已启用' : '文档已禁用')
      loadDocuments(currentDataset.id, documentPage, documentSearch)
    } catch (err) { message.error(err.message) }
  }

  // ======================== NEW: Document Extra Operations ========================

  const handlePauseDocument = async (doc) => {
    try {
      const action = doc.indexingStatus === 'paused' ? 'resume' : 'pause'
      await pauseDocument(doc.id, action)
      message.success(action === 'pause' ? '文档已暂停' : '文档已恢复')
      loadDocuments(currentDataset.id, documentPage, documentSearch)
    } catch (err) { message.error(err.message) }
  }

  const handleRenameDocument = async (doc) => {
    Modal.confirm({
      title: '重命名文档',
      content: (
        <Input
          defaultValue={doc.name}
          id="rename-doc-input"
          placeholder="输入新名称"
        />
      ),
      onOk: async () => {
        const el = document.getElementById('rename-doc-input')
        const newName = el?.value?.trim()
        if (!newName) { message.warning('名称不能为空'); return }
        try {
          await renameDocument(doc.id, newName)
          message.success('重命名成功')
          loadDocuments(currentDataset.id, documentPage, documentSearch)
        } catch (err) { message.error(err.message) }
      },
    })
  }

  const handleArchiveDocument = async (doc) => {
    try {
      await archiveDocument(doc.id)
      message.success('文档已归档')
      loadDocuments(currentDataset.id, documentPage, documentSearch)
    } catch (err) { message.error(err.message) }
  }

  const handleUnarchiveDocument = async (doc) => {
    try {
      await unarchiveDocument(doc.id)
      message.success('文档已取消归档')
      loadDocuments(currentDataset.id, documentPage, documentSearch)
    } catch (err) { message.error(err.message) }
  }

  // ===== NEW: Document Batch Operations =====

  const handleBatchDocToggle = (docId, checked) => {
    setSelectedDocIds((prev) => checked ? [...prev, docId] : prev.filter((id) => id !== docId))
  }

  const handleSelectAllDocs = (checked) => {
    if (checked) {
      setSelectedDocIds(documents.map((d) => d.id))
    } else {
      setSelectedDocIds([])
    }
  }

  const handleBatchDocEnable = async () => {
    if (!selectedDocIds.length || !currentDataset) return
    try {
      for (const id of selectedDocIds) await toggleDocument(id, true)
      message.success(`已启用 ${selectedDocIds.length} 个文档`)
      setSelectedDocIds([])
      loadDocuments(currentDataset.id, documentPage, documentSearch)
    } catch (err) { message.error(err.message) }
  }

  const handleBatchDocDisable = async () => {
    if (!selectedDocIds.length || !currentDataset) return
    try {
      for (const id of selectedDocIds) await toggleDocument(id, false)
      message.success(`已禁用 ${selectedDocIds.length} 个文档`)
      setSelectedDocIds([])
      loadDocuments(currentDataset.id, documentPage, documentSearch)
    } catch (err) { message.error(err.message) }
  }

  const handleBatchDocArchive = async () => {
    if (!selectedDocIds.length || !currentDataset) return
    try {
      for (const id of selectedDocIds) await archiveDocument(id)
      message.success(`已归档 ${selectedDocIds.length} 个文档`)
      setSelectedDocIds([])
      loadDocuments(currentDataset.id, documentPage, documentSearch)
    } catch (err) { message.error(err.message) }
  }

  const handleBatchDocDelete = async () => {
    if (!selectedDocIds.length || !currentDataset) return
    Modal.confirm({
      title: `确认删除 ${selectedDocIds.length} 个文档？`,
      content: '此操作不可撤销。',
      okType: 'danger',
      onOk: async () => {
        try {
          for (const id of selectedDocIds) await deleteDocument(id)
          message.success(`已删除 ${selectedDocIds.length} 个文档`)
          setSelectedDocIds([])
          loadDocuments(currentDataset.id, documentPage, documentSearch)
          loadDatasetDetail(currentDataset.id)
        } catch (err) { message.error(err.message) }
      },
    })
  }

  // ======================== Segment ========================

  const openSegmentDetail = async (segment) => {
    setActiveSegment(segment)
    try {
      const res = await getSegment(segment.id)
      setActiveSegment(res.data)
    } catch { /* use existing data */ }
    setSegmentDrawerVisible(true)
  }

  const openSegmentEdit = (segment) => {
    setActiveSegment(segment)
    segmentEditForm.setFieldsValue({
      content: segment.content,
      answer: segment.answer,
      keywords: segment.keywords,
      enabled: segment.enabled !== 0,
    })
    setSegmentEditDrawerVisible(true)
  }

  const handleSegmentEditOk = async () => {
    try {
      const values = await segmentEditForm.validateFields()
      setSegmentEditLoading(true)
      await updateSegment(activeSegment.id, {
        content: values.content,
        answer: values.answer,
        keywords: values.keywords,
        enabled: values.enabled ? 1 : 0,
      })
      message.success('分段更新成功')
      setSegmentEditDrawerVisible(false)
      loadSegments(currentDocument.id, segmentPage, segmentSearch)
    } catch (err) {
      if (err?.message && !err?.errorFields) message.error(err.message)
    } finally { setSegmentEditLoading(false) }
  }

  const handleDeleteSegment = async (segment) => {
    try {
      await deleteSegment(segment.id)
      message.success('分段已删除')
      loadSegments(currentDocument.id, segmentPage, segmentSearch)
    } catch (err) { message.error(err.message) }
  }

  const handleToggleSegment = async (segment, enabled) => {
    try {
      await toggleSegment(segment.id, enabled)
      message.success(enabled ? '分段已启用' : '分段已禁用')
      loadSegments(currentDocument.id, segmentPage, segmentSearch)
    } catch (err) { message.error(err.message) }
  }

  // ===== NEW: Segment Batch Operations =====

  const handleBatchSegToggle = (segId, checked) => {
    setSelectedSegmentIds((prev) => checked ? [...prev, segId] : prev.filter((id) => id !== segId))
  }

  const handleSelectAllSegments = (checked) => {
    if (checked) {
      setSelectedSegmentIds(segments.map((s) => s.id))
    } else {
      setSelectedSegmentIds([])
    }
  }

  const handleBatchSegEnable = async () => {
    if (!selectedSegmentIds.length || !currentDocument) return
    try {
      await batchToggleSegments(currentDocument.id, 'enable', selectedSegmentIds)
      message.success(`已启用 ${selectedSegmentIds.length} 个分段`)
      setSelectedSegmentIds([])
      loadSegments(currentDocument.id, segmentPage, segmentSearch)
    } catch (err) { message.error(err.message) }
  }

  const handleBatchSegDisable = async () => {
    if (!selectedSegmentIds.length || !currentDocument) return
    try {
      await batchToggleSegments(currentDocument.id, 'disable', selectedSegmentIds)
      message.success(`已禁用 ${selectedSegmentIds.length} 个分段`)
      setSelectedSegmentIds([])
      loadSegments(currentDocument.id, segmentPage, segmentSearch)
    } catch (err) { message.error(err.message) }
  }

  const handleBatchSegDelete = async () => {
    if (!selectedSegmentIds.length || !currentDocument) return
    Modal.confirm({
      title: `确认删除 ${selectedSegmentIds.length} 个分段？`,
      content: '此操作不可撤销。',
      okType: 'danger',
      onOk: async () => {
        try {
          await batchDeleteSegments(currentDocument.id, selectedSegmentIds)
          message.success(`已删除 ${selectedSegmentIds.length} 个分段`)
          setSelectedSegmentIds([])
          loadSegments(currentDocument.id, segmentPage, segmentSearch)
        } catch (err) { message.error(err.message) }
      },
    })
  }

  // ===== NEW: Create Segment =====

  const handleCreateSegment = async () => {
    try {
      const values = await newSegmentForm.validateFields()
      setNewSegmentLoading(true)
      const attachments = []
      for (const img of segmentImages) {
        const b64 = await readFileAsBase64(img)
        attachments.push({ name: img.name, contentBase64: b64, type: img.type || 'image/png' })
      }
      await createSegment(currentDocument.id, {
        content: values.content,
        answer: values.answer,
        keywords: values.keywords,
        attachments: attachments.length > 0 ? attachments : undefined,
      })
      message.success('分段已添加')
      setNewSegmentVisible(false)
      newSegmentForm.resetFields()
      setSegmentImages([])
      loadSegments(currentDocument.id, segmentPage, segmentSearch)
    } catch (err) {
      if (err?.message && !err?.errorFields) message.error(err.message)
    } finally { setNewSegmentLoading(false) }
  }

  // ======================== Settings ========================

  const handleSaveSettings = async () => {
    try {
      const values = await settingsForm.validateFields()
      const retrievalModel = {
        search_method: settingsRetrievalMethod,
        top_k: settingsTopK,
        score_threshold: settingsScoreThreshold,
        score_threshold_enabled: settingsScoreThresholdEnabled,
        rerank_enable: settingsRerankEnable,
        rerank_model: settingsRerankModel,
        reranking_mode: settingsRerankingMode,
        vector_weight: settingsVectorWeight,
        keyword_number: settingsKeywordNumber,
      }
      await updateDataset(currentDataset.id, {
        ...values,
        retrievalModel: JSON.stringify(retrievalModel),
      })
      message.success('设置已保存')
      loadDatasetDetail(currentDataset.id)
    } catch (err) {
      if (err?.message && !err?.errorFields) message.error(err.message)
    }
  }

  // ======================== Recall (Hit Testing v2) ========================

  const handleHitTest = async () => {
    if (!hitQuery.trim()) { message.warning('请输入测试问题'); return }
    setHitLoading(true)
    setHitResults([])
    try {
      const res = await runRagRecallTest({
        query: hitQuery,
        ragStrategy: hitRagStrategy,
        metadata: {
          knowledgeBaseIds: currentDataset ? [currentDataset.id] : [],
          topK: hitTopK,
          similarityThreshold: hitScoreThreshold,
        },
      })
      const results = res.data?.documents || res.data || []
      setHitResults(Array.isArray(results) ? results : [])
      // Save to records
      setHitTestingRecords((prev) => [
        { query: hitQuery, time: new Date().toLocaleTimeString('zh-CN'), count: (Array.isArray(results) ? results : []).length },
        ...prev.slice(0, 9),
      ])
      message.success(`召回完成，命中 ${(Array.isArray(results) ? results : []).length} 条`)
    } catch (err) { message.error(err.message || '召回测试失败') }
    finally { setHitLoading(false) }
  }

  // Legacy recall test handler (kept for form compatibility)
  const handleRecallTest = async (values) => {
    setRecallLoading(true)
    setRecallResult(null)
    try {
      const res = await runRagRecallTest({
        query: values.query,
        ragStrategy: values.ragStrategy || 'hybrid_search',
        embeddingModel: values.embeddingModel,
        metadata: {
          knowledgeBaseIds: values.knowledgeBaseIds || [currentDataset?.id],
          topK: values.topK || 5,
          similarityThreshold: values.similarityThreshold,
        },
      })
      setRecallResult(res.data)
      message.success(`召回完成，命中 ${(res.data?.documents || []).length} 条`)
    } catch (err) { message.error(err.message || '召回测试失败') }
    finally { setRecallLoading(false) }
  }

  // ======================== File Convert ========================

  const handleConvert = async (file) => {
    setConvertLoading(true)
    setConvertResult(null)
    try {
      const isPptx = file.name.toLowerCase().endsWith('.pptx')
      const formData = new FormData()
      formData.append('file', file)
      if (!isPptx) formData.append('targetFormat', 'docx')
      const res = isPptx ? await convertPpt(formData) : await convertPdf(formData)
      setConvertResult(res.data)
      message.success('转换完成')
    } catch (err) { message.error(err.message || '转换失败') }
    finally { setConvertLoading(false) }
  }

  // ======================== RENDER: Wizard ========================

  const renderWizard = () => (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#fff' }}>
      <div className="kb-wizard-topbar">
        <button className="kb-wizard-back" onClick={closeWizard}>
          <ArrowLeftOutlined />
          <span>知识库</span>
        </button>
        <div className="kb-wizard-stepper-wrap">
          <div className="kb-wizard-stepper">
            {WIZARD_STEPS.map((s, i) => (
              <div key={s.num} style={{ display: 'flex', alignItems: 'center' }}>
                {i > 0 && <div className="kb-wizard-step-connector" />}
                <div className="kb-wizard-step">
                  {s.num < wizardStep ? (
                    <span className="kb-wizard-step-num done">{s.num}</span>
                  ) : s.num === wizardStep ? (
                    <span className="kb-wizard-step-badge active">STEP {s.num}</span>
                  ) : (
                    <span className="kb-wizard-step-num pending">{s.num}</span>
                  )}
                  <span className={`kb-wizard-step-label ${s.num < wizardStep ? 'done' : s.num === wizardStep ? 'active' : 'pending'}`}>
                    {s.label}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {wizardStep === 1 && renderWizardStep1()}
      {wizardStep === 2 && renderWizardStep2()}
      {wizardStep === 3 && renderWizardStep3()}
    </div>
  )

  // ===== Wizard Step 1 =====
  const renderWizardStep1 = () => (
    <div className="kb-wizard-body">
      <div className="kb-wizard-left">
        <div className="kb-wizard-left-inner">
          <div className="kb-wizard-section-title">数据来源</div>
          <div className="kb-data-source-grid">
            <div className={`kb-data-source-card ${wizSourceType === 'file' ? 'selected' : ''}`}
              onClick={() => setWizSourceType('file')}>
              <div className="kb-data-source-icon"><FileTextOutlined /></div>
              <span className="kb-data-source-label">上传文件</span>
            </div>
            <div className={`kb-data-source-card ${wizSourceType === 'notion' ? 'selected' : ''}`}
              onClick={() => setWizSourceType('notion')}>
              <div className="kb-data-source-icon"><ApiOutlined /></div>
              <span className="kb-data-source-label">Notion</span>
            </div>
            <div className={`kb-data-source-card ${wizSourceType === 'web' ? 'selected' : ''}`}
              onClick={() => setWizSourceType('web')}>
              <div className="kb-data-source-icon"><GlobalOutlined /></div>
              <span className="kb-data-source-label">网页</span>
            </div>
          </div>

          {wizSourceType === 'file' && (
            <div className="kb-upload-area">
              <div
                className={`kb-upload-dropzone ${wizDragOver ? 'dragging' : ''}`}
                onClick={() => document.getElementById('wiz-file-input')?.click()}
                onDragOver={(e) => { e.preventDefault(); setWizDragOver(true) }}
                onDragLeave={() => setWizDragOver(false)}
                onDrop={handleWizardFileDrop}
              >
                <UploadOutlined />
                <div className="kb-upload-text">
                  拖放文件到这里，或 <span className="kb-upload-text-accent">浏览</span>
                </div>
                <div className="kb-upload-tip">
                  支持 TXT、MD、PDF、DOCX、XLSX、HTML、CSV、EPUB 等格式，单个文件最大 15MB
                </div>
              </div>
              <input id="wiz-file-input" type="file" multiple
                accept=".txt,.md,.mdx,.markdown,.pdf,.docx,.doc,.xlsx,.xls,.html,.htm,.csv,.pptx,.ppt,.epub,.eml,.xml"
                style={{ display: 'none' }}
                onChange={handleWizardFileSelect}
              />
              {wizFiles.length > 0 && (
                <div style={{ marginTop: 12 }}>
                  {wizFiles.map((file, idx) => (
                    <div key={idx} className="kb-file-item">
                      <div className="kb-file-item-icon"><FileTextOutlined /></div>
                      <div className="kb-file-item-info">
                        <div className="kb-file-item-name">{file.name}</div>
                        <div className="kb-file-item-meta">
                          <span>{getFileExtension(file.name)}</span>
                          <span>·</span>
                          <span>{formatFileSize(file.size)}</span>
                        </div>
                      </div>
                      <button className="kb-file-item-delete" onClick={() => removeWizFile(idx)}>
                        <DeleteOutlined />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="kb-divider-regular" />

          <button className="kb-empty-kb-link" onClick={() => {
            closeWizard()
            setEmptyKbModalVisible(true)
            emptyKbForm.resetFields()
          }}>
            <FolderOutlined />
            <span>创建一个空知识库</span>
          </button>

          <div className="kb-wizard-footer">
            <div />
            <Button type="primary" onClick={wizardNextStep} size="large">
              下一步 <ArrowRightOutlined />
            </Button>
          </div>
        </div>
      </div>

      <div className="kb-wizard-right">
        <div className="kb-wizard-right-inner">
          <div className="kb-wizard-preview-title">文件预览</div>
          {wizFiles.length > 0 ? (
            wizFiles.map((file, idx) => (
              <div key={idx} className="kb-preview-file-card">
                <div className="kb-preview-file-icon"><FileTextOutlined /></div>
                <div className="kb-preview-file-info">
                  <div className="kb-preview-file-name">{file.name}</div>
                  <div className="kb-preview-file-meta">
                    {getFileExtension(file.name)} · {formatFileSize(file.size)}
                  </div>
                </div>
              </div>
            ))
          ) : (
            <div className="kb-wizard-preview-empty">
              <div className="kb-wizard-preview-empty-icon"><FileTextOutlined /></div>
              <div>上传文件后，预览将显示在这里</div>
            </div>
          )}
        </div>
      </div>
    </div>
  )

  // ===== Wizard Step 2 =====
  const renderWizardStep2 = () => (
    <div className="kb-wizard-body">
      <div className="kb-wizard-left">
        <div className="kb-wizard-left-inner">

          <div className="kb-wizard-section-label" style={{ marginBottom: 4 }}>知识库名称</div>
          <Input
            value={wizName}
            onChange={(e) => setWizName(e.target.value)}
            placeholder="例如：教学资料库"
            maxLength={40}
            style={{ marginBottom: 16 }}
          />

          <div className="kb-wizard-section-label" style={{ marginBottom: 4 }}>描述（可选）</div>
          <TextArea
            value={wizDescription}
            onChange={(e) => setWizDescription(e.target.value)}
            placeholder="知识库用途描述..."
            rows={2}
            style={{ marginBottom: 24 }}
          />

          <div className="kb-wizard-section-title">切分方式</div>

          <div className="kb-seg-options-card">
            <div
              className={`kb-seg-options-header ${wizChunkStructure !== 'hierarchical_model' ? 'active' : ''}`}
              onClick={() => { setWizChunkStructure('text_model'); setWizProcessMode('automatic') }}
            >
              <div className="kb-seg-options-icon"><SettingOutlined /></div>
              <span className="kb-seg-options-title">通用切分</span>
              <Radio checked={wizChunkStructure !== 'hierarchical_model'} style={{ marginLeft: 'auto' }} />
            </div>
            {wizChunkStructure !== 'hierarchical_model' && (
              <div className="kb-seg-options-body">
                <div className="kb-seg-fields">
                  <div className="kb-seg-field">
                    <div className="kb-seg-field-label">处理模式</div>
                    <Select
                      value={wizProcessMode}
                      onChange={setWizProcessMode}
                      style={{ width: '100%' }}
                      options={[
                        { value: 'automatic', label: '自动' },
                        { value: 'custom', label: '自定义' },
                      ]}
                    />
                  </div>
                  {wizProcessMode === 'custom' && (
                    <>
                      <div className="kb-seg-field">
                        <div className="kb-seg-field-label">分段大小</div>
                        <InputNumber value={wizChunkSize} min={100} max={4000}
                          onChange={setWizChunkSize} style={{ width: '100%' }} />
                      </div>
                      <div className="kb-seg-field">
                        <div className="kb-seg-field-label">重叠大小</div>
                        <InputNumber value={wizChunkOverlap} min={0} max={1000}
                          onChange={setWizChunkOverlap} style={{ width: '100%' }} />
                      </div>
                    </>
                  )}
                </div>
              </div>
            )}
          </div>

          <div className="kb-seg-options-card">
            <div
              className={`kb-seg-options-header ${wizChunkStructure === 'hierarchical_model' ? 'active' : ''}`}
              onClick={() => setWizChunkStructure('hierarchical_model')}
            >
              <div className="kb-seg-options-icon"><DatabaseOutlined /></div>
              <span className="kb-seg-options-title">父子切分</span>
              <Radio checked={wizChunkStructure === 'hierarchical_model'} style={{ marginLeft: 'auto' }} />
            </div>
            {wizChunkStructure === 'hierarchical_model' && (
              <div className="kb-seg-options-body">
                <div style={{ fontSize: 12, color: '#6b7280', lineHeight: 1.6 }}>
                  父块保留上下文语义，子块用于精确检索。适合长文档和结构化内容。
                </div>
              </div>
            )}
          </div>

          <div style={{ height: 24 }} />

          <div className="kb-wizard-section-title">索引方式</div>

          <div className="kb-index-mode-grid">
            <div
              className={`kb-index-card ${wizIndexType === 'high_quality' ? 'selected' : ''}`}
              onClick={() => setWizIndexType('high_quality')}
            >
              <div className="kb-index-card-header">
                <div className="kb-index-card-icon" style={{ background: 'linear-gradient(135deg, #fef3c7, #fde68a)' }}>
                  <StarFilled style={{ color: '#f59e0b' }} />
                </div>
                <div className="kb-index-card-info">
                  <div className="kb-index-card-title">
                    高质量
                    <span className="kb-recommend-badge">推荐</span>
                  </div>
                  <div className="kb-index-card-desc">
                    使用内嵌向量模型生成嵌入，进行语义检索，准确率更高
                  </div>
                </div>
                <div className="kb-index-card-radio">
                  <Radio checked={wizIndexType === 'high_quality'} />
                </div>
              </div>
            </div>

            <div
              className={`kb-index-card ${wizIndexType === 'economy' ? 'selected' : ''}`}
              onClick={() => setWizIndexType('economy')}
            >
              <div className="kb-index-card-header">
                <div className="kb-index-card-icon" style={{ background: 'linear-gradient(135deg, #e0e7ff, #c7d2fe)' }}>
                  <DatabaseOutlined style={{ color: '#6366f1' }} />
                </div>
                <div className="kb-index-card-info">
                  <div className="kb-index-card-title">经济</div>
                  <div className="kb-index-card-desc">
                    仅使用关键词索引，不需要嵌入模型，成本更低
                  </div>
                </div>
                <div className="kb-index-card-radio">
                  <Radio checked={wizIndexType === 'economy'} />
                </div>
              </div>
            </div>
          </div>

          <div className="kb-index-warning">
            <ExclamationCircleFilled />
            <span>索引方式确认后不可更改，请谨慎选择</span>
          </div>

          {wizIndexType === 'high_quality' ? (
            <div style={{ marginTop: 16, marginBottom: 24 }}>
              <div className="kb-wizard-section-label">嵌入模型</div>
              <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 8 }}>
                选择一个嵌入模型将文本转化为向量，用于语义检索
              </div>
              <Select
                placeholder="选择嵌入模型"
                style={{ width: '100%' }}
                options={embeddingModelOptions}
                allowClear
              />
            </div>
          ) : (
            <div className="kb-economy-embedding-notice">
              <div className="kb-economy-notice-icon">
                <CheckCircleFilled style={{ color: '#10b981', fontSize: 18 }} />
              </div>
              <div className="kb-economy-notice-text">
                <div style={{ fontWeight: 600, color: '#374151', fontSize: 13 }}>不需要嵌入模型</div>
                <div style={{ color: '#6b7280', fontSize: 12, marginTop: 2 }}>
                  经济模式仅使用倒排索引进行关键词检索，无需配置向量模型，节省资源开销
                </div>
              </div>
            </div>
          )}

          <div style={{ height: 8 }} />

          {wizIndexType === 'high_quality' ? (
            <>
              <div className="kb-wizard-section-title">检索方式</div>

              <div
                className={`kb-retrieval-card ${wizRetrievalMethod === 'semantic_search' ? 'selected' : ''}`}
                onClick={() => setWizRetrievalMethod('semantic_search')}
              >
                <div className="kb-retrieval-card-icon"><SearchOutlined /></div>
                <div className="kb-retrieval-card-info">
                  <div className="kb-retrieval-card-title">语义检索</div>
                  <div className="kb-retrieval-card-desc">通过向量模型将查询转化为向量，进行语义相似度匹配</div>
                </div>
                <div className="kb-retrieval-card-radio">
                  <Radio checked={wizRetrievalMethod === 'semantic_search'} />
                </div>
              </div>

              <div
                className={`kb-retrieval-card ${wizRetrievalMethod === 'full_text_search' ? 'selected' : ''}`}
                onClick={() => setWizRetrievalMethod('full_text_search')}
              >
                <div className="kb-retrieval-card-icon"><FileTextOutlined /></div>
                <div className="kb-retrieval-card-info">
                  <div className="kb-retrieval-card-title">全文检索</div>
                  <div className="kb-retrieval-card-desc">基于 BM25 的传统关键词检索，适合精确匹配场景</div>
                </div>
                <div className="kb-retrieval-card-radio">
                  <Radio checked={wizRetrievalMethod === 'full_text_search'} />
                </div>
              </div>

              <div
                className={`kb-retrieval-card ${wizRetrievalMethod === 'hybrid_search' ? 'selected' : ''}`}
                onClick={() => setWizRetrievalMethod('hybrid_search')}
              >
                <div className="kb-retrieval-card-icon"><ExperimentOutlined /></div>
                <div className="kb-retrieval-card-info">
                  <div className="kb-retrieval-card-title">
                    混合检索
                    <span className="kb-recommend-badge">推荐</span>
                  </div>
                  <div className="kb-retrieval-card-desc">同时执行语义检索和全文检索，通过重排序返回最佳结果</div>
                </div>
                <div className="kb-retrieval-card-radio">
                  <Radio checked={wizRetrievalMethod === 'hybrid_search'} />
                </div>
              </div>
            </>
          ) : (
            <>
              <div className="kb-wizard-section-title">检索方式</div>
              <div className="kb-retrieval-card selected" style={{ cursor: 'default' }}>
                <div className="kb-retrieval-card-icon"><SearchOutlined /></div>
                <div className="kb-retrieval-card-info">
                  <div className="kb-retrieval-card-title">关键词检索</div>
                  <div className="kb-retrieval-card-desc">经济模式下仅支持基于倒排索引的关键词检索方式</div>
                </div>
                <div className="kb-retrieval-card-radio">
                  <Radio checked disabled />
                </div>
              </div>
            </>
          )}

          <div className="kb-wizard-footer">
            <Button onClick={wizardPrevStep}>
              <ArrowLeftOutlined /> 上一步
            </Button>
            <Button type="primary" onClick={wizardNextStep} loading={wizCreating}>
              创建知识库
            </Button>
          </div>
        </div>
      </div>

      <div className="kb-wizard-right">
        <div className="kb-wizard-right-inner">
          <div className="kb-wizard-preview-title">配置预览</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ padding: '12px 16px', borderRadius: 8, background: '#f9fafb', border: '0.5px solid #e5e7eb' }}>
              <div style={{ fontSize: 11, color: '#9ca3af', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 4 }}>切分方式</div>
              <div style={{ fontSize: 13, fontWeight: 500, color: '#374151' }}>
                {wizChunkStructure === 'hierarchical_model' ? '父子切分' : '通用切分'}
                {wizProcessMode === 'custom' && wizChunkStructure !== 'hierarchical_model' && ` · 分段 ${wizChunkSize} · 重叠 ${wizChunkOverlap}`}
              </div>
            </div>
            <div style={{ padding: '12px 16px', borderRadius: 8, background: '#f9fafb', border: '0.5px solid #e5e7eb' }}>
              <div style={{ fontSize: 11, color: '#9ca3af', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 4 }}>索引方式</div>
              <div style={{ fontSize: 13, fontWeight: 500, color: '#374151' }}>
                {wizIndexType === 'high_quality' ? '高质量（向量嵌入）' : '经济（关键词）'}
              </div>
            </div>
            <div style={{ padding: '12px 16px', borderRadius: 8, background: wizIndexType === 'economy' ? '#ecfdf5' : '#f9fafb', border: wizIndexType === 'economy' ? '0.5px solid #d1fae5' : '0.5px solid #e5e7eb' }}>
              <div style={{ fontSize: 11, color: '#9ca3af', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 4 }}>嵌入模型</div>
              <div style={{ fontSize: 13, fontWeight: 500, color: wizIndexType === 'economy' ? '#059669' : '#374151' }}>
                {wizIndexType === 'economy' ? '不需要' : '需要配置'}
              </div>
            </div>
            <div style={{ padding: '12px 16px', borderRadius: 8, background: '#f9fafb', border: '0.5px solid #e5e7eb' }}>
              <div style={{ fontSize: 11, color: '#9ca3af', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 4 }}>检索方式</div>
              <div style={{ fontSize: 13, fontWeight: 500, color: '#374151' }}>
                {wizIndexType === 'economy' ? '关键词检索'
                  : wizRetrievalMethod === 'semantic_search' ? '语义检索'
                  : wizRetrievalMethod === 'full_text_search' ? '全文检索'
                  : '混合检索'}
              </div>
            </div>
            {wizFiles.length > 0 && (
              <div style={{ padding: '12px 16px', borderRadius: 8, background: '#f9fafb', border: '0.5px solid #e5e7eb' }}>
                <div style={{ fontSize: 11, color: '#9ca3af', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 4 }}>文件数量</div>
                <div style={{ fontSize: 13, fontWeight: 500, color: '#374151' }}>{wizFiles.length} 个文件</div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )

  // ===== Wizard Step 3 =====
  const renderWizardStep3 = () => (
    <div className="kb-wizard-step3-wrap">
      <div className="kb-wizard-step3-inner">
        <div className="kb-wizard-step3-title">知识库创建完成</div>
        <div className="kb-wizard-step3-desc">
          正在处理文档索引，这可能需要一些时间，你可以先进入知识库查看。
        </div>

        <div className="kb-wizard-step3-info">
          <div className="kb-wizard-step3-icon">
            <FolderOutlined />
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="kb-wizard-step3-name-label">知识库名称</div>
            <div className="kb-wizard-step3-name-box">{wizCreatedDataset?.name || wizName}</div>
          </div>
        </div>

        {wizFiles.length > 0 && (
          <div>
            <div className="kb-progress-label">
              <LoadingOutlined style={{ color: '#6366f1' }} />
              处理中
            </div>
            {wizFiles.map((file, idx) => (
              <div key={idx} className="kb-progress-item">
                <div className="kb-progress-bar-wrap">
                  <div className="kb-progress-bar-fill" style={{ width: wizCreatedDataset ? '100%' : '60%' }} />
                  <div className="kb-progress-bar-content">
                    <FileTextOutlined style={{ fontSize: 14, color: '#6b7280' }} />
                    <span className="kb-progress-bar-name">{file.name}</span>
                    {wizCreatedDataset && <CheckCircleFilled style={{ fontSize: 14, color: '#10b981' }} />}
                    {!wizCreatedDataset && <span className="kb-progress-bar-pct">60%</span>}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        <div style={{
          marginTop: 24, padding: '16px', borderRadius: 12,
          border: '0.5px solid #e5e7eb', background: '#f9fafb'
        }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: '#374151', marginBottom: 12 }}>配置摘要</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, fontSize: 12 }}>
            <div>
              <span style={{ color: '#9ca3af' }}>切分方式：</span>
              <span style={{ color: '#374151', fontWeight: 500 }}>
                {wizChunkStructure === 'hierarchical_model' ? '父子切分' : '通用切分'}
              </span>
            </div>
            <div>
              <span style={{ color: '#9ca3af' }}>索引方式：</span>
              <span style={{ color: '#374151', fontWeight: 500 }}>
                {wizIndexType === 'high_quality' ? '高质量' : '经济'}
              </span>
            </div>
            <div>
              <span style={{ color: '#9ca3af' }}>嵌入模型：</span>
              <span style={{ color: wizIndexType === 'economy' ? '#059669' : '#374151', fontWeight: 500 }}>
                {wizIndexType === 'economy' ? '不需要' : '需要配置'}
              </span>
            </div>
            <div>
              <span style={{ color: '#9ca3af' }}>检索方式：</span>
              <span style={{ color: '#374151', fontWeight: 500 }}>
                {wizIndexType === 'economy' ? '关键词检索'
                  : wizRetrievalMethod === 'semantic_search' ? '语义检索'
                  : wizRetrievalMethod === 'full_text_search' ? '全文检索'
                  : '混合检索'}
              </span>
            </div>
            <div>
              <span style={{ color: '#9ca3af' }}>文件数量：</span>
              <span style={{ color: '#374151', fontWeight: 500 }}>{wizFiles.length}</span>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, marginTop: 32 }}>
          <Button onClick={closeWizard}>返回首页</Button>
          <Button type="primary" onClick={handleWizardFinish}>
            进入知识库 <ArrowRightOutlined />
          </Button>
        </div>
      </div>
    </div>
  )

  // ======================== RENDER: Dataset List ========================

  const renderDatasetList = () => (
    <div className="kb-page">
      <div className="kb-list-toolbar">
        <Input
          className="kb-search-input"
          prefix={<SearchOutlined style={{ color: '#9ca3af' }} />}
          placeholder="搜索知识库..."
          allowClear
          value={datasetSearch}
          onChange={(e) => setDatasetSearch(e.target.value)}
          onPressEnter={() => loadDatasets(1, datasetSearch)}
        />
        <Button icon={<ReloadOutlined />} onClick={() => loadDatasets(datasetPage, datasetSearch)} />
      </div>

      <div className="kb-card-grid">
        <div className="kb-new-dataset-card">
          <div className="kb-new-card-main">
            <div className="kb-new-card-option" onClick={openCreateWizard}>
              <PlusOutlined />
              <span>创建知识库</span>
            </div>
          </div>
          <div className="kb-new-card-divider" />
          <div className="kb-new-card-bottom">
            <div className="kb-new-card-option" onClick={() => {
              setEmptyKbModalVisible(true)
              emptyKbForm.resetFields()
            }}>
              <ApiOutlined />
              <span>创建空知识库</span>
            </div>
          </div>
        </div>

        {datasets.map((ds) => (
          <div key={ds.id} className="kb-dataset-card" onClick={() => enterDataset(ds)}>
            <div className="kb-dataset-card-actions">
              <Dropdown menu={{
                items: [
                  { key: 'edit', label: '重命名', icon: <EditOutlined />, onClick: (e) => openEditDataset(ds, e) },
                  { type: 'divider' },
                  { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true,
                    onClick: (e) => { e.domEvent.stopPropagation(); handleDeleteDataset(ds, e.domEvent) } },
                ],
              }} trigger={['click']}>
                <Button type="text" size="small" icon={<MoreOutlined />}
                  onClick={(e) => e.stopPropagation()} style={{ borderRadius: 10 }} />
              </Dropdown>
            </div>

            <div className="kb-dataset-card-header">
              <div className="kb-dataset-card-icon"><FolderOutlined /></div>
              <div style={{ minWidth: 0, flex: 1 }}>
                <div className="kb-dataset-card-name">{ds.name}</div>
                <div className="kb-dataset-card-author">
                  创建于 {formatTime(ds.createTime)}
                  {ds.indexingTechnique && (
                    <span style={{ marginLeft: 6, fontSize: 10, color: '#d1d5db' }}>
                      · {ds.indexingTechnique === 'high_quality' ? '高质量' : '经济'}
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div className="kb-dataset-card-desc">{ds.description || '暂无描述'}</div>

            <div className="kb-dataset-card-tags">
              {ds.indexingTechnique && (
                <span className="kb-card-tag">
                  {ds.indexingTechnique === 'high_quality' ? 'HIGH QUALITY' : 'ECONOMY'}
                </span>
              )}
              {ds.chunkStructure && (
                <span className="kb-card-tag">
                  {ds.chunkStructure === 'text_model' ? 'TEXT' : ds.chunkStructure === 'qa_model' ? 'QA' : 'HIERARCHICAL'}
                </span>
              )}
            </div>

            <div className="kb-dataset-card-footer">
              <FileTextOutlined />
              <span>{formatCount(ds.documentCount)}</span>
              <span className="kb-footer-sep">/</span>
              <span style={{ fontSize: 12 }}>更新于 {formatTime(ds.updateTime || ds.createTime)}</span>
            </div>
          </div>
        ))}
      </div>

      <Modal
        title={editingDataset ? '编辑知识库' : '创建知识库'}
        open={datasetModalVisible}
        onOk={handleDatasetModalOk}
        onCancel={() => setDatasetModalVisible(false)}
        confirmLoading={datasetModalLoading}
        width={520}
      >
        <Form form={datasetForm} layout="vertical" initialValues={{
          indexingTechnique: 'high_quality', chunkStructure: 'text_model', permission: 'only_me'
        }}>
          <Form.Item name="name" label="知识库名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="例如：教学资料库" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={3} placeholder="知识库用途描述..." />
          </Form.Item>
          <Form.Item name="indexingTechnique" label="索引技术">
            <Radio.Group>
              <Radio.Button value="high_quality">高质量（向量）</Radio.Button>
              <Radio.Button value="economy">经济（关键词）</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="chunkStructure" label="切分形态">
            <Select options={[
              { value: 'text_model', label: '文本模型' },
              { value: 'qa_model', label: 'QA 模型' },
              { value: 'hierarchical_model', label: '层次模型（父子）' },
            ]} />
          </Form.Item>
          <Form.Item name="embeddingModel" label="向量模型">
            <Input placeholder="例如：text-embedding-3-small" />
          </Form.Item>
          <Form.Item name="permission" label="权限">
            <Select options={[
              { value: 'only_me', label: '仅创建者' },
              { value: 'all_team', label: '全部团队' },
            ]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )

  // ======================== RENDER: Sidebar ========================

  const renderSidebar = () => {
    if (!currentDataset) return null
    const links = [
      { key: 'documents', icon: <FileTextOutlined />, label: '文档' },
      { key: 'hitTesting', icon: <ExperimentOutlined />, label: '召回测试' },
      { key: 'settings', icon: <SettingOutlined />, label: '设置' },
    ]
    return (
      <div className="kb-sidebar">
        <div className="kb-sidebar-header">
          <div className="kb-sidebar-icon"><FolderOutlined /></div>
          <div style={{ minWidth: 0 }}>
            <div className="kb-sidebar-name">{currentDataset.name}</div>
            <div className="kb-sidebar-type">Knowledge</div>
          </div>
        </div>
        <div className="kb-sidebar-divider" />
        <div className="kb-sidebar-nav">
          {links.map((l) => (
            <button key={l.key} type="button"
              className={`kb-sidebar-link ${detailTab === l.key ? 'active' : ''}`}
              onClick={() => switchDetailTab(l.key)}>
              <span className="kb-sidebar-link-icon">{l.icon}</span>
              <span>{l.label}</span>
            </button>
          ))}
        </div>
        <div className="kb-sidebar-stats">
          <div className="kb-sidebar-stat">
            <span className="kb-sidebar-stat-value">{formatCount(currentDataset.documentCount)}</span>
            <span className="kb-sidebar-stat-label">文档</span>
          </div>
          <div className="kb-sidebar-stat-divider" />
          <div className="kb-sidebar-stat">
            <span className="kb-sidebar-stat-value">{formatCount(currentDataset.wordCount)}</span>
            <span className="kb-sidebar-stat-label">字数</span>
          </div>
        </div>
      </div>
    )
  }

  // ======================== RENDER: Document List ========================

  const renderDocumentList = () => {
    const allSelected = documents.length > 0 && selectedDocIds.length === documents.length
    const someSelected = selectedDocIds.length > 0

    return (
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
        <div className="kb-doc-header">
          <h1>文档</h1>
          <p>管理知识库中的所有文档，包括上传、索引和状态追踪。</p>
        </div>
        <div className="kb-doc-toolbar">
          <div className="kb-doc-toolbar-left">
            <Select value={docStatusFilter} style={{ width: 140 }} onChange={(v) => setDocStatusFilter(v)}
              options={[
                { value: 'all', label: '全部' },
                { value: 'completed', label: '可用' },
                { value: 'indexing', label: '索引中' },
                { value: 'error', label: '错误' },
                { value: 'paused', label: '已暂停' },
              ]} />
            <Input prefix={<SearchOutlined style={{ color: '#9ca3af' }} />} placeholder="搜索文档..."
              style={{ width: 200 }} allowClear value={documentSearch}
              onChange={(e) => setDocumentSearch(e.target.value)}
              onPressEnter={() => loadDocuments(currentDataset.id, 1, documentSearch)} />
            <Select value={docSortBy} style={{ width: 130 }} onChange={(v) => { setDocSortBy(v); if (currentDataset) loadDocuments(currentDataset.id, documentPage, documentSearch, v) }}
              options={[
                { value: 'upload_time', label: '按上传时间' },
                { value: 'hit_count', label: '按命中次数' },
              ]} />
          </div>
          <div className="kb-doc-toolbar-right">
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateDocument}>添加文档</Button>
          </div>
        </div>
        <div className="kb-doc-table-wrap">
          <table className="kb-doc-table">
            <thead>
              <tr>
                <th className="kb-doc-checkbox-col" style={{ width: 36 }}>
                  <Checkbox
                    checked={allSelected}
                    indeterminate={!allSelected && someSelected}
                    onChange={(e) => handleSelectAllDocs(e.target.checked)}
                  />
                </th>
                <th style={{ width: 36 }}>#</th>
                <th>文件名称</th>
                <th style={{ width: 130 }}>切分模式</th>
                <th style={{ width: 96 }}>字数</th>
                <th style={{ width: 80 }}>分段</th>
                <th style={{ width: 120 }}>上传时间</th>
                <th style={{ width: 100 }}>状态</th>
                <th style={{ width: 140 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {documents.length === 0 && !docLoading ? (
                <tr><td colSpan={9}>
                  <div className="kb-empty-state">
                    <div className="kb-empty-icon"><FileTextOutlined /></div>
                    <div style={{ fontWeight: 600, color: '#374151', marginBottom: 4 }}>暂无文档</div>
                    <div style={{ fontSize: 13, color: '#9ca3af', marginBottom: 16 }}>点击"添加文档"开始导入知识内容</div>
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreateDocument}>添加文档</Button>
                  </div>
                </td></tr>
              ) : documents.map((doc, idx) => {
                const st = indexingStatusMap[doc.indexingStatus] || { label: doc.indexingStatus, cls: 'disabled' }
                const isPaused = doc.indexingStatus === 'paused'
                const isProcessing = ['parsing', 'cleaning', 'splitting', 'indexing'].includes(doc.indexingStatus)
                const isArchived = doc.archived === 1 || doc.archived === true
                return (
                  <tr key={doc.id}>
                    <td className="kb-doc-checkbox-col">
                      <Checkbox
                        checked={selectedDocIds.includes(doc.id)}
                        onChange={(e) => handleBatchDocToggle(doc.id, e.target.checked)}
                      />
                    </td>
                    <td style={{ color: '#d1d5db', fontSize: 12 }}>{(documentPage - 1) * 20 + idx + 1}</td>
                    <td>
                      <div className="kb-doc-name-cell">
                        <div className="kb-doc-type-icon"><FileTextOutlined /></div>
                        <span className="kb-doc-name-text" onClick={() => enterDocument(doc)}>{doc.name}</span>
                      </div>
                    </td>
                    <td><Tag style={{ fontSize: 11 }}>{doc.docForm === 'text_model' ? '文本' : doc.docForm === 'qa_model' ? 'QA' : doc.docForm || '-'}</Tag></td>
                    <td>{formatCount(doc.wordCount)}</td>
                    <td>{formatCount(doc.segmentCount)}</td>
                    <td style={{ fontSize: 12, color: '#9ca3af' }}>{formatTime(doc.createTime)}</td>
                    <td><span className={`kb-status-dot ${st.cls}`}>{st.label}</span></td>
                    <td>
                      <Space size={4}>
                        <Switch size="small" checked={doc.enabled !== 0}
                          onChange={(v) => handleToggleDocument(doc, v)} />
                        <Dropdown menu={{
                          items: [
                            ...(isProcessing ? [{ key: 'pause', label: '暂停', icon: <PauseCircleOutlined /> }] : []),
                            ...(isPaused ? [{ key: 'resume', label: '恢复处理', icon: <ReloadOutlined /> }] : []),
                            { key: 'rename', label: '重命名', icon: <EditOutlined /> },
                            ...(isArchived
                              ? [{ key: 'unarchive', label: '取消归档', icon: <FolderOutlined /> }]
                              : [{ key: 'archive', label: '归档', icon: <FolderOutlined /> }]),
                            { type: 'divider' },
                            { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true },
                          ],
                          onClick: ({ key }) => {
                            if (key === 'pause' || key === 'resume') handlePauseDocument(doc)
                            else if (key === 'rename') handleRenameDocument(doc)
                            else if (key === 'archive') handleArchiveDocument(doc)
                            else if (key === 'unarchive') handleUnarchiveDocument(doc)
                            else if (key === 'delete') {
                              Modal.confirm({
                                title: '确认删除此文档？',
                                okType: 'danger',
                                onOk: () => handleDeleteDocument(doc),
                              })
                            }
                          },
                        }} trigger={['click']}>
                          <Button type="text" size="small" icon={<MoreOutlined />} onClick={(e) => e.stopPropagation()} />
                        </Dropdown>
                      </Space>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {documentTotal > 20 && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', padding: '16px 0' }}>
              <Pagination current={documentPage} pageSize={20} total={documentTotal} showSizeChanger={false}
                onChange={(p) => { setDocumentPage(p); loadDocuments(currentDataset.id, p, documentSearch) }} />
            </div>
          )}
        </div>

        {/* Batch action bar */}
        {someSelected && (
          <div className="kb-batch-action-bar">
            <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>
              已选择 <Tag color="blue">{selectedDocIds.length}</Tag> 个文档
            </span>
            <Space>
              <Button size="small" onClick={handleBatchDocEnable}>启用</Button>
              <Button size="small" onClick={handleBatchDocDisable}>禁用</Button>
              <Button size="small" onClick={handleBatchDocArchive}>归档</Button>
              <Button size="small" danger onClick={handleBatchDocDelete}>删除</Button>
              <Button size="small" type="text" onClick={() => setSelectedDocIds([])}>取消</Button>
            </Space>
          </div>
        )}
      </div>
    )
  }

  // ======================== RENDER: Segment List ========================

  const renderSegmentList = () => {
    if (!currentDocument) return null
    const st = indexingStatusMap[currentDocument.indexingStatus] || { label: '-', cls: 'disabled' }
    const pipelineSteps = ['上传', '解析', '切分', '向量化', '完成']
    const completedIdx = currentDocument.indexingStatus === 'completed' ? 5
      : currentDocument.indexingStatus === 'error' ? -1
      : ['waiting', 'parsing', 'splitting', 'indexing'].indexOf(currentDocument.indexingStatus) + 1

    const allSegSelected = segments.length > 0 && selectedSegmentIds.length === segments.length
    const someSegSelected = selectedSegmentIds.length > 0

    // Filter segments by status
    const filteredSegments = segments.filter((seg) => {
      if (segmentStatusFilter === 'enabled') return seg.enabled !== 0
      if (segmentStatusFilter === 'disabled') return seg.enabled === 0
      return true
    })

    return (
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
        <div className="kb-segment-header">
          <div className="kb-segment-header-left">
            <button className="kb-back-btn" onClick={backToDocuments}><ArrowLeftOutlined /></button>
            <div>
              <div style={{ fontWeight: 600, fontSize: 14, color: '#111827' }}>{currentDocument.name}</div>
              <div style={{ fontSize: 12, color: '#9ca3af' }}>
                <span className={`kb-status-dot ${st.cls}`}>{st.label}</span>
                <span style={{ margin: '0 8px', color: '#e5e7eb' }}>·</span>
                {formatCount(currentDocument.wordCount)} 字
                <span style={{ margin: '0 8px', color: '#e5e7eb' }}>·</span>
                {formatCount(currentDocument.segmentCount)} 分段
              </div>
            </div>
          </div>
          <Space>
            <div style={{ display: 'flex', gap: 4 }}>
              {pipelineSteps.map((step, i) => (
                <Tag key={step} color={i < completedIdx ? 'success' : i === completedIdx ? 'processing' : 'default'}
                  style={{ fontSize: 11, borderRadius: 6, margin: 0 }}>
                  {i < completedIdx && <CheckCircleOutlined style={{ marginRight: 4 }} />}
                  {step}
                </Tag>
              ))}
            </div>
          </Space>
        </div>

        <div className="kb-segment-toolbar">
          <Checkbox
            checked={allSegSelected}
            indeterminate={!allSegSelected && someSegSelected}
            onChange={(e) => handleSelectAllSegments(e.target.checked)}
            style={{ marginRight: 4 }}
          />
          <Input prefix={<SearchOutlined style={{ color: '#9ca3af' }} />} placeholder="搜索分段内容..."
            style={{ width: 240 }} allowClear value={segmentSearch}
            onChange={(e) => setSegmentSearch(e.target.value)}
            onPressEnter={() => loadSegments(currentDocument.id, 1, segmentSearch)} />
          <Select value={segmentStatusFilter} style={{ width: 120 }} onChange={(v) => setSegmentStatusFilter(v)}
            options={[
              { value: 'all', label: '全部' },
              { value: 'enabled', label: '已启用' },
              { value: 'disabled', label: '已禁用' },
            ]} />
          <div style={{ flex: 1 }} />
          <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => {
            newSegmentForm.resetFields()
            setSegmentImages([])
            setNewSegmentVisible(true)
          }}>
            添加分段
          </Button>
          <span style={{ fontSize: 12, color: '#9ca3af', fontWeight: 600 }}>共 {segmentTotal} 个分段</span>
        </div>

        <div className="kb-segment-list">
          {filteredSegments.length === 0 && !segLoading ? (
            <div className="kb-empty-state">
              <div className="kb-empty-icon"><DatabaseOutlined /></div>
              <div style={{ fontWeight: 600, color: '#374151', marginBottom: 4 }}>暂无分段</div>
              <div style={{ fontSize: 13, color: '#9ca3af' }}>文档索引完成后，分段会自动出现在这里</div>
            </div>
          ) : filteredSegments.map((seg, idx) => (
            <div key={seg.id}>
              {idx > 0 && <div className="kb-segment-divider" />}
              <div className="kb-segment-card">
                <div className="kb-segment-checkbox">
                  <Checkbox
                    checked={selectedSegmentIds.includes(seg.id)}
                    onChange={(e) => handleBatchSegToggle(seg.id, e.target.checked)}
                  />
                </div>
                <div className="kb-segment-actions-float">
                  <Button type="text" size="small" icon={<EyeOutlined />} onClick={() => openSegmentDetail(seg)} />
                  <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openSegmentEdit(seg)} />
                  <Popconfirm title="删除此分段？" onConfirm={() => handleDeleteSegment(seg)}>
                    <Button type="text" size="small" icon={<DeleteOutlined />} danger />
                  </Popconfirm>
                  <Switch size="small" checked={seg.enabled !== 0} onChange={(v) => handleToggleSegment(seg, v)} />
                </div>
                <div className="kb-segment-card-top">
                  <div className="kb-segment-card-meta">
                    <span className="kb-segment-index-tag">Chunk-{String(idx + 1).padStart(2, '0')}</span>
                    <span>·</span>
                    <span>{formatCount(seg.wordCount)} 字</span>
                    <span>·</span>
                    <span>{seg.hitCount || 0} 命中</span>
                  </div>
                </div>
                <div className="kb-segment-content">{seg.content}</div>
                {seg.attachments && (() => {
                  try {
                    const atts = typeof seg.attachments === 'string' ? JSON.parse(seg.attachments) : seg.attachments
                    if (Array.isArray(atts) && atts.length > 0) {
                      return (
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
                          {atts.map((att, i) => (
                            <div key={i} style={{ width: 56, height: 56, borderRadius: 6, overflow: 'hidden', border: '1px solid #e5e7eb' }}>
                              {att.contentBase64
                                ? <img src={`data:${att.type || 'image/png'};base64,${att.contentBase64}`} alt={att.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                : <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f3f4f6', color: '#9ca3af' }}><PictureOutlined /></div>
                              }
                            </div>
                          ))}
                        </div>
                      )
                    }
                  } catch { /* ignore */ }
                  return null
                })()}
                {seg.keywords && (() => {
                  try {
                    const kws = typeof seg.keywords === 'string' ? JSON.parse(seg.keywords) : seg.keywords
                    if (Array.isArray(kws) && kws.length > 0) {
                      return (
                        <div className="kb-segment-keywords">
                          {kws.map((kw, i) => <span key={i} className="kb-segment-keyword">{kw}</span>)}
                        </div>
                      )
                    }
                  } catch { /* ignore */ }
                  return null
                })()}
              </div>
            </div>
          ))}
        </div>

        {segmentTotal > 20 && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', padding: '12px 16px', borderTop: '0.5px solid #e5e7eb' }}>
            <Pagination current={segmentPage} pageSize={20} total={segmentTotal} showSizeChanger={false}
              onChange={(p) => { setSegmentPage(p); loadSegments(currentDocument.id, p, segmentSearch) }} />
          </div>
        )}

        {/* Segment batch action bar */}
        {someSegSelected && (
          <div className="kb-batch-action-bar">
            <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>
              已选择 <Tag color="blue">{selectedSegmentIds.length}</Tag> 个分段
            </span>
            <Space>
              <Button size="small" onClick={handleBatchSegEnable}>启用</Button>
              <Button size="small" onClick={handleBatchSegDisable}>禁用</Button>
              <Button size="small" danger onClick={handleBatchSegDelete}>删除</Button>
              <Button size="small" type="text" onClick={() => setSelectedSegmentIds([])}>取消</Button>
            </Space>
          </div>
        )}
      </div>
    )
  }

  // ======================== RENDER: Hit Testing (v2 - Dify Style) ========================

  const renderHitTesting = () => (
    <div className="kb-hit-wrap">
      <div className="kb-hit-title">
        <h1>召回测试</h1>
        <p>测试知识库的检索质量，验证分段是否能被正确召回。</p>
      </div>
      <div className="kb-hit-v2-layout">
        {/* Left panel */}
        <div className="kb-hit-v2-left">
          <div className="kb-hit-v2-input-section">
            <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 8 }}>
              查询文本
            </div>
            <div style={{ position: 'relative' }}>
              <TextArea
                value={hitQuery}
                onChange={(e) => setHitQuery(e.target.value.slice(0, 200))}
                placeholder="输入要测试的查询问题..."
                rows={5}
                maxLength={200}
              />
              <div style={{ position: 'absolute', bottom: 8, right: 12, fontSize: 11, color: '#9ca3af' }}>
                {hitQuery.length}/200
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12 }}>
              <Button
                type="primary"
                icon={<SearchOutlined />}
                loading={hitLoading}
                onClick={handleHitTest}
                block
              >
                开始测试
              </Button>
            </div>
            {/* Modify retrieval config toggle */}
            <div
              style={{ marginTop: 12, cursor: 'pointer', fontSize: 13, color: '#4f46e5', fontWeight: 500 }}
              onClick={() => setHitRetrievalOpen(!hitRetrievalOpen)}
            >
              <DownOutlined style={{ fontSize: 10, marginRight: 4, transform: hitRetrievalOpen ? 'rotate(0)' : 'rotate(-90deg)', transition: 'transform 0.2s' }} />
              修改检索配置
            </div>
            {hitRetrievalOpen && (
              <div style={{ marginTop: 12, padding: 12, borderRadius: 8, border: '0.5px solid #e5e7eb', background: '#f9fafb' }}>
                <div className="kb-param-row">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4, width: 120, flexShrink: 0 }}>
                    <span style={{ fontSize: 12, fontWeight: 600, color: '#374151' }}>检索策略</span>
                  </div>
                  <Select value={hitRagStrategy} onChange={setHitRagStrategy} style={{ width: '100%' }}
                    options={retrievalStrategyOptions} size="small" />
                </div>
                <div className="kb-param-row" style={{ marginTop: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4, width: 120, flexShrink: 0 }}>
                    <span style={{ fontSize: 12, fontWeight: 600, color: '#374151' }}>Top K</span>
                    <Tooltip title="返回的最大结果数量"><InfoCircleOutlined style={{ color: '#9ca3af', fontSize: 11 }} /></Tooltip>
                  </div>
                  <Slider min={1} max={10} step={1} value={hitTopK}
                    onChange={setHitTopK}
                    style={{ flex: 1, margin: '0 12px' }} />
                  <InputNumber min={1} max={10} step={1} value={hitTopK}
                    onChange={setHitTopK} size="small" style={{ width: 64 }} />
                </div>
                <div className="kb-param-row" style={{ marginTop: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4, width: 120, flexShrink: 0 }}>
                    <span style={{ fontSize: 12, fontWeight: 600, color: '#374151' }}>Score 阈值</span>
                    <Tooltip title="最低相似度分数阈值"><InfoCircleOutlined style={{ color: '#9ca3af', fontSize: 11 }} /></Tooltip>
                  </div>
                  <Slider min={0} max={1} step={0.01} value={hitScoreThreshold}
                    onChange={setHitScoreThreshold}
                    style={{ flex: 1, margin: '0 12px' }} />
                  <InputNumber min={0} max={1} step={0.01} value={hitScoreThreshold}
                    onChange={setHitScoreThreshold} size="small" style={{ width: 64 }} />
                </div>
              </div>
            )}
          </div>

          {/* Testing records */}
          {hitTestingRecords.length > 0 && (
            <div style={{ marginTop: 20 }}>
              <div style={{ fontSize: 12, fontWeight: 600, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 8 }}>
                测试记录
              </div>
              {hitTestingRecords.map((rec, idx) => (
                <div key={idx} style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  padding: '8px 10px', borderRadius: 6, marginBottom: 4,
                  background: '#f9fafb', border: '0.5px solid #f3f4f6', cursor: 'pointer',
                }}
                  onClick={() => setHitQuery(rec.query)}
                >
                  <span style={{ fontSize: 12, color: '#374151', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 200 }}>
                    {rec.query}
                  </span>
                  <span style={{ fontSize: 11, color: '#9ca3af', flexShrink: 0, marginLeft: 8 }}>
                    {rec.count} 条 · {rec.time}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right panel - results */}
        <div className="kb-hit-v2-right">
          {hitLoading && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}>
              <LoadingOutlined style={{ fontSize: 24, color: '#6366f1' }} />
              <span style={{ marginLeft: 8, color: '#6b7280' }}>正在检索...</span>
            </div>
          )}
          {!hitLoading && hitResults.length === 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 300, color: '#9ca3af' }}>
              <ExperimentOutlined style={{ fontSize: 32, marginBottom: 12, color: '#d1d5db' }} />
              <div style={{ fontSize: 13 }}>输入查询后点击测试，结果将显示在这里</div>
            </div>
          )}
          {!hitLoading && hitResults.length > 0 && (
            <div>
              <div style={{ fontSize: 12, fontWeight: 600, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 12 }}>
                召回结果 ({hitResults.length})
              </div>
              {hitResults.map((doc, idx) => {
                const score = Number(doc.score || 0)
                const scorePercent = Math.round(score * 100)
                return (
                  <div key={idx} className="kb-hit-result-v2">
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                      <span className="kb-segment-index-tag" style={{ fontSize: 11 }}>
                        Chunk-{String(idx + 1).padStart(2, '0')}
                      </span>
                      <span style={{ fontSize: 11, color: '#9ca3af' }}>
                        {doc.metadata?.sourceName || doc.source || 'unknown'}
                      </span>
                    </div>
                    <div className="kb-hit-v2-content">
                      {doc.content}
                    </div>
                    <div className="kb-score-bar-wrap">
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                        <span style={{ fontSize: 11, fontWeight: 600, color: '#6b7280' }}>Score</span>
                        <span className={`kb-hit-score ${score > 0.7 ? 'high' : score > 0.4 ? 'medium' : 'low'}`}>
                          {score.toFixed(4)}
                        </span>
                      </div>
                      <div className="kb-score-bar">
                        <div
                          className="kb-score-bar-fill"
                          style={{
                            width: `${scorePercent}%`,
                            background: score > 0.7 ? '#3b82f6' : score > 0.4 ? '#f59e0b' : '#ef4444',
                          }}
                        />
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )

  // ======================== RENDER: Settings (Full Dify-style Retrieval Config) ========================

  const renderSettingsRetrievalParams = () => (
    <div style={{ marginTop: 16 }}>
      {/* Retrieval method cards (same style as wizard) */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
        <div
          className={`kb-retrieval-card ${settingsRetrievalMethod === 'semantic_search' ? 'selected' : ''}`}
          onClick={() => setSettingsRetrievalMethod('semantic_search')}
          style={{ flex: 1 }}
        >
          <div className="kb-retrieval-card-icon"><SearchOutlined /></div>
          <div className="kb-retrieval-card-info">
            <div className="kb-retrieval-card-title">语义检索</div>
            <div className="kb-retrieval-card-desc">向量语义相似度匹配</div>
          </div>
          <div className="kb-retrieval-card-radio">
            <Radio checked={settingsRetrievalMethod === 'semantic_search'} />
          </div>
        </div>
        <div
          className={`kb-retrieval-card ${settingsRetrievalMethod === 'full_text_search' ? 'selected' : ''}`}
          onClick={() => setSettingsRetrievalMethod('full_text_search')}
          style={{ flex: 1 }}
        >
          <div className="kb-retrieval-card-icon"><FileTextOutlined /></div>
          <div className="kb-retrieval-card-info">
            <div className="kb-retrieval-card-title">全文检索</div>
            <div className="kb-retrieval-card-desc">BM25 关键词匹配</div>
          </div>
          <div className="kb-retrieval-card-radio">
            <Radio checked={settingsRetrievalMethod === 'full_text_search'} />
          </div>
        </div>
        <div
          className={`kb-retrieval-card ${settingsRetrievalMethod === 'hybrid_search' ? 'selected' : ''}`}
          onClick={() => setSettingsRetrievalMethod('hybrid_search')}
          style={{ flex: 1 }}
        >
          <div className="kb-retrieval-card-icon"><ExperimentOutlined /></div>
          <div className="kb-retrieval-card-info">
            <div className="kb-retrieval-card-title">
              混合检索
              <span className="kb-recommend-badge">推荐</span>
            </div>
            <div className="kb-retrieval-card-desc">语义 + 关键词融合</div>
          </div>
          <div className="kb-retrieval-card-radio">
            <Radio checked={settingsRetrievalMethod === 'hybrid_search'} />
          </div>
        </div>
      </div>

      {/* Top K */}
      <div className="kb-param-row">
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, width: 140, flexShrink: 0 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>Top K</span>
          <Tooltip title="返回的最大检索结果数量，值越大返回越多结果，但可能降低精确度">
            <InfoCircleOutlined style={{ color: '#9ca3af', fontSize: 12 }} />
          </Tooltip>
        </div>
        <Slider min={1} max={10} step={1} value={settingsTopK}
          onChange={setSettingsTopK}
          style={{ flex: 1, margin: '0 16px' }} />
        <InputNumber min={1} max={10} step={1} value={settingsTopK}
          onChange={setSettingsTopK} style={{ width: 72 }} />
      </div>

      {/* Score Threshold */}
      <div className="kb-param-row" style={{ marginTop: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, width: 140, flexShrink: 0 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>Score 阈值</span>
          <Tooltip title="设置最低相似度分数阈值，低于该值的结果将被过滤">
            <InfoCircleOutlined style={{ color: '#9ca3af', fontSize: 12 }} />
          </Tooltip>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', flex: 1, gap: 12 }}>
          <Switch size="small" checked={settingsScoreThresholdEnabled}
            onChange={setSettingsScoreThresholdEnabled} />
          <Slider min={0} max={1} step={0.01} value={settingsScoreThreshold}
            onChange={setSettingsScoreThreshold}
            disabled={!settingsScoreThresholdEnabled}
            style={{ flex: 1 }} />
          <InputNumber min={0} max={1} step={0.01} value={settingsScoreThreshold}
            onChange={setSettingsScoreThreshold}
            disabled={!settingsScoreThresholdEnabled}
            style={{ width: 72 }} />
        </div>
      </div>

      {/* Rerank Model */}
      <div className="kb-param-row" style={{ marginTop: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, width: 140, flexShrink: 0 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>Rerank 模型</span>
          <Tooltip title="启用后使用 Rerank 模型对检索结果进行重排序，提高结果相关性">
            <InfoCircleOutlined style={{ color: '#9ca3af', fontSize: 12 }} />
          </Tooltip>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', flex: 1, gap: 8 }}>
          <Switch size="small" checked={settingsRerankEnable}
            onChange={setSettingsRerankEnable} />
          {settingsRerankEnable && (
            <Input
              value={settingsRerankModel}
              onChange={(e) => setSettingsRerankModel(e.target.value)}
              placeholder="输入 Rerank 模型名称，如 bge-reranker-v2"
              style={{ flex: 1 }}
            />
          )}
        </div>
      </div>

      {/* Hybrid search specific: reranking mode */}
      {settingsRetrievalMethod === 'hybrid_search' && (
        <div style={{ marginTop: 20, padding: 16, borderRadius: 10, border: '0.5px solid #e5e7eb', background: '#fafafa' }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 12 }}>
            重排序模式
          </div>
          <Radio.Group value={settingsRerankingMode} onChange={(e) => setSettingsRerankingMode(e.target.value)}>
            <Radio value="weighted_score">加权分数</Radio>
            <Radio value="rerank_model">Rerank 模型</Radio>
          </Radio.Group>

          {settingsRerankingMode === 'weighted_score' && (
            <div style={{ marginTop: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                <span style={{ fontSize: 12, color: '#6b7280' }}>语义权重</span>
                <span style={{ fontSize: 12, color: '#6b7280' }}>关键词权重</span>
              </div>
              <div className="kb-param-row">
                <span style={{ fontSize: 12, fontWeight: 600, color: '#4f46e5', width: 40 }}>
                  {settingsVectorWeight.toFixed(1)}
                </span>
                <Slider min={0} max={1} step={0.1} value={settingsVectorWeight}
                  onChange={setSettingsVectorWeight}
                  style={{ flex: 1, margin: '0 12px' }} />
                <span style={{ fontSize: 12, fontWeight: 600, color: '#d97706', width: 40, textAlign: 'right' }}>
                  {(1 - settingsVectorWeight).toFixed(1)}
                </span>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )

  const renderSettingsEconomyParams = () => (
    <div style={{ marginTop: 16 }}>
      {/* Keyword Number for economy mode */}
      <div className="kb-param-row">
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, width: 140, flexShrink: 0 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>关键词数量</span>
          <Tooltip title="经济模式下返回的关键词匹配结果数量">
            <InfoCircleOutlined style={{ color: '#9ca3af', fontSize: 12 }} />
          </Tooltip>
        </div>
        <Slider min={0} max={50} step={1} value={settingsKeywordNumber}
          onChange={setSettingsKeywordNumber}
          style={{ flex: 1, margin: '0 16px' }} />
        <InputNumber min={0} max={50} step={1} value={settingsKeywordNumber}
          onChange={setSettingsKeywordNumber} style={{ width: 72 }} />
      </div>

      {/* Score Threshold for economy */}
      <div className="kb-param-row" style={{ marginTop: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, width: 140, flexShrink: 0 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>Score 阈值</span>
          <Tooltip title="设置最低相似度分数阈值">
            <InfoCircleOutlined style={{ color: '#9ca3af', fontSize: 12 }} />
          </Tooltip>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', flex: 1, gap: 12 }}>
          <Switch size="small" checked={settingsScoreThresholdEnabled}
            onChange={setSettingsScoreThresholdEnabled} />
          <Slider min={0} max={1} step={0.01} value={settingsScoreThreshold}
            onChange={setSettingsScoreThreshold}
            disabled={!settingsScoreThresholdEnabled}
            style={{ flex: 1 }} />
          <InputNumber min={0} max={1} step={0.01} value={settingsScoreThreshold}
            onChange={setSettingsScoreThreshold}
            disabled={!settingsScoreThresholdEnabled}
            style={{ width: 72 }} />
        </div>
      </div>
    </div>
  )

  const renderSettings = () => (
    <div className="kb-settings-wrap">
      <div className="kb-settings-title">
        <h1>设置</h1>
        <p>配置知识库的基本信息、索引方式和检索策略。</p>
      </div>
      <Form form={settingsForm} layout="vertical" className="kb-settings-form">
        {/* Basic info */}
        <div className="kb-settings-row">
          <div className="kb-settings-label">名称</div>
          <div className="kb-settings-control">
            <Form.Item name="name" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
          </div>
        </div>
        <div className="kb-settings-row">
          <div className="kb-settings-label">描述</div>
          <div className="kb-settings-control">
            <Form.Item name="description">
              <TextArea rows={3} placeholder="知识库描述..." />
            </Form.Item>
          </div>
        </div>
        <div className="kb-settings-divider" />

        {/* Index config */}
        <div className="kb-settings-row">
          <div className="kb-settings-label">索引技术</div>
          <div className="kb-settings-control">
            <Form.Item name="indexingTechnique">
              <Radio.Group>
                <Radio.Button value="high_quality">高质量（向量）</Radio.Button>
                <Radio.Button value="economy">经济（关键词）</Radio.Button>
              </Radio.Group>
            </Form.Item>
          </div>
        </div>
        <div className="kb-settings-row">
          <div className="kb-settings-label">切分形态</div>
          <div className="kb-settings-control">
            <Form.Item name="chunkStructure">
              <Select options={[
                { value: 'text_model', label: '文本模型' },
                { value: 'qa_model', label: 'QA 模型' },
                { value: 'hierarchical_model', label: '层次模型（父子）' },
              ]} />
            </Form.Item>
          </div>
        </div>
        <div className="kb-settings-row">
          <div className="kb-settings-label">向量模型</div>
          <div className="kb-settings-control">
            <Form.Item name="embeddingModel">
              <Input placeholder="例如：text-embedding-3-small" />
            </Form.Item>
          </div>
        </div>
        <div className="kb-settings-divider" />

        {/* Permission */}
        <div className="kb-settings-row">
          <div className="kb-settings-label">权限</div>
          <div className="kb-settings-control">
            <Form.Item name="permission">
              <Select options={[
                { value: 'only_me', label: '仅创建者' },
                { value: 'all_team', label: '全部团队' },
              ]} />
            </Form.Item>
          </div>
        </div>
        <div className="kb-settings-divider" />

        {/* Retrieval Settings section */}
        <div style={{ marginBottom: 8 }}>
          <div
            style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', userSelect: 'none' }}
            onClick={() => setSettingsRetrievalOpen(!settingsRetrievalOpen)}
          >
            <DownOutlined style={{
              fontSize: 10, color: '#6b7280',
              transform: settingsRetrievalOpen ? 'rotate(0)' : 'rotate(-90deg)',
              transition: 'transform 0.2s',
            }} />
            <span style={{ fontSize: 15, fontWeight: 600, color: '#111827' }}>检索设置</span>
          </div>
          <div style={{ fontSize: 12, color: '#9ca3af', marginTop: 4, marginLeft: 18 }}>
            配置检索方法、参数和重排序策略
          </div>
        </div>

        {settingsRetrievalOpen && (
          <div style={{ padding: '16px 0' }}>
            {/* Show different params based on index technique */}
            {settingsForm.getFieldValue('indexingTechnique') === 'economy'
              ? renderSettingsEconomyParams()
              : renderSettingsRetrievalParams()
            }
          </div>
        )}

        <div style={{ paddingTop: 16 }}>
          <Button type="primary" onClick={handleSaveSettings} style={{ minWidth: 96 }}>保存</Button>
        </div>
      </Form>
    </div>
  )

  // ======================== RENDER: Detail View ========================

  const renderDetail = () => (
    <div className="kb-detail-layout">
      {renderSidebar()}
      <div className="kb-detail-content">
        {currentDocument ? renderSegmentList()
          : detailTab === 'documents' ? renderDocumentList()
          : detailTab === 'hitTesting' ? renderHitTesting()
          : renderSettings()}
      </div>
    </div>
  )

  // ======================== RENDER: Document Creation Modal ========================

  const renderDocumentModal = () => {
    const dataSourceType = watchDataSourceType
    const processMode = watchProcessMode

    return (
      <Modal
        title="添加文档"
        open={documentModalVisible}
        onOk={handleDocumentModalOk}
        onCancel={() => setDocumentModalVisible(false)}
        confirmLoading={documentModalLoading}
        width={640}
        okText="创建并索引"
      >
        <Form form={documentForm} layout="vertical">
          <Form.Item name="name" label="文档名称" rules={[{ required: true, message: '请输入文档名称' }]}>
            <Input placeholder="例如：校园卡服务指南.txt" />
          </Form.Item>

          <Form.Item name="dataSourceType" label="数据来源">
            <Radio.Group>
              <Radio.Button value="text_input">文本输入</Radio.Button>
              <Radio.Button value="upload_file">上传文件</Radio.Button>
            </Radio.Group>
          </Form.Item>

          {dataSourceType === 'text_input' ? (
            <Form.Item name="textContent" label="文本内容">
              <TextArea rows={8} placeholder="粘贴文本内容..." />
            </Form.Item>
          ) : (
            <div>
              <Form.Item label="选择文件">
                <Upload
                  accept=".txt,.md,.mdx,.markdown,.pdf,.docx,.doc,.xlsx,.xls,.html,.htm,.csv,.pptx,.ppt,.epub,.eml,.xml"
                  beforeUpload={(file) => {
                    setUploadFileList([{ ...file, originFileObj: file, uid: file.uid || `rc-upload-${Date.now()}`, status: 'done', name: file.name }])
                    documentForm.setFieldsValue({ name: file.name })
                    return false
                  }}
                  fileList={uploadFileList}
                  maxCount={1}
                  onRemove={() => setUploadFileList([])}
                >
                  <Button icon={<UploadOutlined />}>选择 DOCX / TXT / PDF / PPTX</Button>
                </Upload>
              </Form.Item>
              {uploadFileList.length > 0 && /\.pdf$/i.test(uploadFileList[0]?.name || '') && (
                <div style={{ marginBottom: 12 }}>
                  <Button loading={convertLoading} onClick={() => handleConvert(uploadFileList[0].originFileObj || uploadFileList[0])}>
                    先转换为 DOCX（推荐）
                  </Button>
                  {convertResult && <Tag color="success" style={{ marginLeft: 8 }}>已转换：{convertResult.fileName}</Tag>}
                </div>
              )}
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Form.Item name="docForm" label="文档形态">
              <Select options={[
                { value: 'text_model', label: '文本模型' },
                { value: 'qa_model', label: 'QA 模型' },
              ]} />
            </Form.Item>
            <Form.Item name="processMode" label="处理模式">
              <Select options={[
                { value: 'automatic', label: '自动' },
                { value: 'custom', label: '自定义' },
                { value: 'hierarchical', label: '层次（父子）' },
              ]} />
            </Form.Item>
          </div>

          {processMode === 'custom' && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Form.Item name="chunkSize" label="Chunk Size" initialValue={800}>
                <InputNumber min={200} max={3000} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="chunkOverlap" label="Overlap" initialValue={120}>
                <InputNumber min={0} max={800} style={{ width: '100%' }} />
              </Form.Item>
            </div>
          )}

          {processMode === 'hierarchical' && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Form.Item name="parentChunkSize" label="Parent Size" initialValue={1600}>
                <InputNumber min={600} max={5000} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="childChunkSize" label="Child Size" initialValue={420}>
                <InputNumber min={120} max={1600} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="parentChunkOverlap" label="Parent Overlap" initialValue={160}>
                <InputNumber min={0} max={1200} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="childChunkOverlap" label="Child Overlap" initialValue={80}>
                <InputNumber min={0} max={500} style={{ width: '100%' }} />
              </Form.Item>
            </div>
          )}

          <Form.Item name="embeddingModel" label="向量模型">
            <Select options={embeddingModelOptions} placeholder="选择向量模型" allowClear />
          </Form.Item>

          <Form.Item name="docMetadata" label="元数据 (JSON)">
            <TextArea rows={2} placeholder='{"tags":["校园卡","后勤"],"scene":"campus_knowledge"}' />
          </Form.Item>
        </Form>
      </Modal>
    )
  }

  // ======================== Main Render ========================

  return (
    <div style={{ height: '100%' }}>
      {creatingWizard ? renderWizard()
        : view === 'list' ? renderDatasetList()
        : renderDetail()}

      {renderDocumentModal()}

      {/* Empty KB Modal */}
      <Modal
        title="创建空知识库"
        open={emptyKbModalVisible}
        onOk={handleCreateEmptyKb}
        onCancel={() => setEmptyKbModalVisible(false)}
        confirmLoading={emptyKbLoading}
        width={480}
      >
        <Form form={emptyKbForm} layout="vertical">
          <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 16 }}>
            创建一个空的知识库，稍后再添加文档。
          </div>
          <Form.Item name="name" label="知识库名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="例如：教学资料库" maxLength={40} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Segment Detail Drawer */}
      <Drawer
        title={activeSegment ? `分段详情 #${activeSegment.position ?? '-'}` : '分段详情'}
        open={segmentDrawerVisible}
        onClose={() => setSegmentDrawerVisible(false)}
        width={560}
      >
        {activeSegment && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              <Tag>{activeSegment.documentName || '-'}</Tag>
              <Tag color={activeSegment.enabled !== 0 ? 'success' : 'default'}>
                {activeSegment.enabled !== 0 ? '已启用' : '已禁用'}
              </Tag>
              <span style={{ fontSize: 12, color: '#9ca3af' }}>
                {formatCount(activeSegment.wordCount)} 字 · {activeSegment.hitCount || 0} 命中
              </span>
            </div>
            <div style={{ padding: 16, borderRadius: 8, border: '0.5px solid #e5e7eb', background: '#f9fafb', lineHeight: 1.8, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {activeSegment.content}
            </div>
            {activeSegment.answer && (
              <div>
                <div style={{ fontSize: 12, fontWeight: 600, color: '#374151', marginBottom: 4 }}>回答 (QA)</div>
                <div style={{ padding: 12, borderRadius: 8, border: '0.5px solid #e5e7eb', background: '#fff', lineHeight: 1.7 }}>
                  {activeSegment.answer}
                </div>
              </div>
            )}
            {activeSegment.keywords && (() => {
              try {
                const kws = typeof activeSegment.keywords === 'string' ? JSON.parse(activeSegment.keywords) : activeSegment.keywords
                if (Array.isArray(kws) && kws.length > 0) {
                  return (
                    <div>
                      <div style={{ fontSize: 12, fontWeight: 600, color: '#374151', marginBottom: 4 }}>关键词</div>
                      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                        {kws.map((kw, i) => <Tag key={i}>{kw}</Tag>)}
                      </div>
                    </div>
                  )
                }
              } catch { /* ignore */ }
              return null
            })()}
            {activeSegment.childChunks && activeSegment.childChunks.length > 0 && (
              <div>
                <div style={{ fontSize: 12, fontWeight: 600, color: '#374151', marginBottom: 8 }}>
                  子片段 ({activeSegment.childChunks.length})
                </div>
                {activeSegment.childChunks.map((cc) => (
                  <div key={cc.id} style={{ padding: 10, borderRadius: 8, border: '0.5px solid #e5e7eb', marginBottom: 6, fontSize: 12, lineHeight: 1.7 }}>
                    <div style={{ fontSize: 10, color: '#9ca3af', marginBottom: 4 }}>
                      #{cc.position} · {cc.wordCount} 字 · {cc.type}
                    </div>
                    {cc.content}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </Drawer>

      {/* Segment Edit Drawer */}
      <Drawer
        title="编辑分段"
        open={segmentEditDrawerVisible}
        onClose={() => setSegmentEditDrawerVisible(false)}
        width={560}
        extra={<Button type="primary" onClick={handleSegmentEditOk} loading={segmentEditLoading}>保存</Button>}
      >
        <Form form={segmentEditForm} layout="vertical">
          <Form.Item name="content" label="内容">
            <TextArea rows={10} />
          </Form.Item>
          <Form.Item name="answer" label="回答 (QA 模式)">
            <TextArea rows={4} />
          </Form.Item>
          <Form.Item name="keywords" label="关键词 (JSON 数组)">
            <Input placeholder='["关键词1","关键词2"]' />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>

      {/* New Segment Modal */}
      <Modal
        title="添加分段"
        open={newSegmentVisible}
        onOk={handleCreateSegment}
        onCancel={() => setNewSegmentVisible(false)}
        confirmLoading={newSegmentLoading}
        width={560}
        okText="添加"
      >
        <Form form={newSegmentForm} layout="vertical">
          <Form.Item name="content" label="内容" rules={[{ required: true, message: '请输入分段内容' }]}>
            <TextArea rows={8} placeholder="输入分段内容..." />
          </Form.Item>
          <Form.Item name="answer" label="回答 (QA 模式，可选)">
            <TextArea rows={4} placeholder="如果是 QA 模式，输入对应的回答..." />
          </Form.Item>
          <Form.Item name="keywords" label="关键词 (JSON 数组，可选)">
            <Input placeholder='["关键词1","关键词2"]' />
          </Form.Item>
          <Form.Item label="图片附件（可选）">
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
              {segmentImages.map((img, idx) => (
                <div key={idx} style={{ position: 'relative', width: 72, height: 72, borderRadius: 8, overflow: 'hidden', border: '1px solid #e5e7eb' }}>
                  <img src={URL.createObjectURL(img)} alt={img.name}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  <span onClick={() => setSegmentImages(prev => prev.filter((_, i) => i !== idx))}
                    style={{ position: 'absolute', top: -4, right: -4, cursor: 'pointer', fontSize: 16, color: '#ff4d4f', background: '#fff', borderRadius: '50%' }}>
                    <CloseCircleFilled />
                  </span>
                </div>
              ))}
              <label style={{ width: 72, height: 72, border: '1px dashed #d1d5db', borderRadius: 8, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#9ca3af', fontSize: 12 }}>
                <PictureOutlined style={{ fontSize: 20, marginBottom: 2 }} />
                <span>添加</span>
                <input type="file" accept="image/jpeg,image/png,image/gif" multiple style={{ display: 'none' }}
                  onChange={(e) => {
                    const files = Array.from(e.target.files || [])
                    setSegmentImages(prev => [...prev, ...files])
                    e.target.value = ''
                  }}
                />
              </label>
            </div>
            <div style={{ fontSize: 12, color: '#9ca3af', marginTop: 4 }}>支持 JPG、PNG、GIF 格式</div>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default KnowledgeBaseManage
