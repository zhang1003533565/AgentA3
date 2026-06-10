import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Breadcrumb,
  Button,
  Card,
  Col,
  Collapse,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Segmented,
  Select,
  Slider,
  Space,
  Statistic,
  Switch,
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
  ClockCircleOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  ExperimentOutlined,
  EyeOutlined,
  FileTextOutlined,
  FolderOutlined,
  HealthMonitorOutlined,
  HomeOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  SettingOutlined,
  SwapOutlined,
  SyncOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import {
  getDatasets,
  getDataset,
  createDataset,
  updateDataset,
  deleteDataset,
  getDocuments,
  getDocument,
  createDocument,
  deleteDocument,
  toggleDocument,
  getSegments,
  getSegment,
  updateSegment,
  deleteSegment,
  toggleSegment,
  getChildChunks,
  convertPdf,
  convertPpt,
  runRagRecallTest,
  getRagVectorStoreHealth,
  getRagEmbeddingHealth,
  getRagGraphStoreHealth,
  getSystemConfigList,
} from '../../../api/dataset'
import '../RagManage/RagManage.css'
import './KnowledgeBaseManage.css'

const { TextArea } = Input
const { Text, Title } = Typography

// ===================== Constants =====================

const AI_MODEL_CONFIG_PATTERN = /^ai\.service\.embedding(?:\.([A-Za-z0-9_-]+))?\.(provider|base-url|api-key|model)$/
const AI_TESTED_MODEL_PREFIXES_KEY = 'ai_tested_model_prefixes_v1'

const indexingStatusMap = {
  waiting:   { color: 'orange',     label: '等待中' },
  parsing:   { color: 'blue',       label: '解析中' },
  cleaning:  { color: 'blue',       label: '清洗中' },
  splitting: { color: 'blue',       label: '切分中' },
  indexing:  { color: 'processing', label: '索引中' },
  completed: { color: 'green',      label: '已完成' },
  error:     { color: 'red',        label: '错误' },
  paused:    { color: 'default',    label: '已暂停' },
}

const pipelineSteps = [
  { key: 'upload',  label: '上传' },
  { key: 'parse',   label: '解析' },
  { key: 'split',   label: '切分' },
  { key: 'embed',   label: '向量化' },
  { key: 'done',    label: '完成' },
]

const recallStrategyOptions = [
  { value: 'hybrid_search',            label: '混合检索 hybrid_search' },
  { value: 'metadata_filter_rag',      label: '元数据过滤 metadata_filter_rag' },
  { value: 'knowledge_base_router_rag', label: '知识库路由 knowledge_base_router_rag' },
  { value: 'contextual_compression_rag', label: '上下文压缩 contextual_compression_rag' },
  { value: 'time_weighted_rag',        label: '时间加权 time_weighted_rag' },
  { value: 'parent_child',             label: '父子切片 parent_child' },
  { value: 'reranking',                label: '重排序 reranking' },
  { value: 'naive_rag',               label: '基础检索 naive_rag' },
  { value: 'multi_query_rag',         label: '多查询 multi_query_rag' },
]

// ===================== Helpers =====================

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

const getStatusInfo = (status) => {
  const info = indexingStatusMap[status] || { color: 'default', label: status || '-' }
  return info
}

const getPipelineStepState = (indexingStatus, stepIndex) => {
  const statusOrder = ['waiting', 'parsing', 'cleaning', 'splitting', 'indexing', 'completed']
  const currentIndex = statusOrder.indexOf(indexingStatus)
  if (indexingStatus === 'error') {
    return stepIndex <= 2 ? 'completed' : 'error'
  }
  if (currentIndex < 0) return 'pending'
  if (stepIndex < currentIndex) return 'completed'
  if (stepIndex === currentIndex) return indexingStatus === 'completed' ? 'completed' : 'active'
  return 'pending'
}

const formatNumber = (val) => {
  const n = Number(val)
  if (!Number.isFinite(n)) return '-'
  if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return String(n)
}

const formatDateTime = (val) => {
  if (!val) return '-'
  try {
    return new Date(val).toLocaleString()
  } catch {
    return String(val)
  }
}

const truncateText = (text, maxLen = 120) => {
  const str = String(text || '')
  return str.length > maxLen ? str.slice(0, maxLen) + '...' : str
}

const extractRecords = (res) => {
  if (!res) return { records: [], total: 0 }
  const d = res.data
  if (Array.isArray(d)) return { records: d, total: d.length }
  if (d && Array.isArray(d.records)) return { records: d.records, total: d.total ?? d.records.length }
  if (d && Array.isArray(d.list)) return { records: d.list, total: d.total ?? d.list.length }
  return { records: [], total: 0 }
}

// ===================== Component =====================

function KnowledgeBaseManage() {
  // ---------- Navigation ----------
  const [view, setView] = useState('datasets') // 'datasets' | 'documents' | 'segments'
  const [activeTab, setActiveTab] = useState('manage') // 'manage' | 'recall' | 'convert'

  // ---------- Dataset state ----------
  const [datasets, setDatasets] = useState([])
  const [datasetLoading, setDatasetLoading] = useState(false)
  const [currentDataset, setCurrentDataset] = useState(null)
  const [currentDocument, setCurrentDocument] = useState(null)

  // ---------- Document state ----------
  const [documents, setDocuments] = useState([])
  const [documentLoading, setDocumentLoading] = useState(false)
  const [documentTotal, setDocumentTotal] = useState(0)
  const [documentPage, setDocumentPage] = useState(1)

  // ---------- Segment state ----------
  const [segments, setSegments] = useState([])
  const [segmentLoading, setSegmentLoading] = useState(false)
  const [segmentTotal, setSegmentTotal] = useState(0)
  const [segmentPage, setSegmentPage] = useState(1)
  const [segmentSearch, setSegmentSearch] = useState('')

  // ---------- Health state ----------
  const [health, setHealth] = useState({ vector: null, embedding: null, graph: null })
  const [healthLoading, setHealthLoading] = useState(false)

  // ---------- Modal state ----------
  const [datasetModalVisible, setDatasetModalVisible] = useState(false)
  const [datasetModalMode, setDatasetModalMode] = useState('create') // 'create' | 'edit'
  const [datasetModalLoading, setDatasetModalLoading] = useState(false)
  const [editingDataset, setEditingDataset] = useState(null)

  const [documentModalVisible, setDocumentModalVisible] = useState(false)
  const [documentModalLoading, setDocumentModalLoading] = useState(false)

  // ---------- Drawer state ----------
  const [segmentDrawerVisible, setSegmentDrawerVisible] = useState(false)
  const [segmentDrawerLoading, setSegmentDrawerLoading] = useState(false)
  const [activeSegment, setActiveSegment] = useState(null)
  const [childChunks, setChildChunks] = useState([])
  const [childChunkLoading, setChildChunkLoading] = useState(false)

  const [segmentEditDrawerVisible, setSegmentEditDrawerVisible] = useState(false)
  const [segmentEditLoading, setSegmentEditLoading] = useState(false)

  // ---------- System config / embedding ----------
  const [systemConfigs, setSystemConfigs] = useState([])
  const [embeddingModelOptions, setEmbeddingModelOptions] = useState([])

  // ---------- Recall test ----------
  const [recallLoading, setRecallLoading] = useState(false)
  const [recallResult, setRecallResult] = useState(null)
  const [recallError, setRecallError] = useState('')

  // ---------- File conversion ----------
  const [convertLoading, setConvertLoading] = useState(false)
  const [convertResult, setConvertResult] = useState(null)

  // ---------- Upload ----------
  const [uploadFileList, setUploadFileList] = useState([])

  // ---------- Forms ----------
  const [datasetForm] = Form.useForm()
  const [documentForm] = Form.useForm()
  const [segmentEditForm] = Form.useForm()
  const [recallForm] = Form.useForm()

  // ---------- Form watches (must be at component top level) ----------
  const watchedDataSourceType = Form.useWatch('dataSourceType', documentForm) || 'text_input'
  const watchedProcessMode = Form.useWatch('processMode', documentForm) || 'automatic'

  // ---------- Convert file list ----------
  const [convertFileList, setConvertFileList] = useState([])

  // ===================== Data Loading =====================

  const loadDatasets = async () => {
    setDatasetLoading(true)
    try {
      const res = await getDatasets({ current: 1, size: 100 })
      const { records } = extractRecords(res)
      setDatasets(records)
    } catch (err) {
      message.error(err?.message || '加载知识库列表失败')
    } finally {
      setDatasetLoading(false)
    }
  }

  const loadDocuments = async (datasetId, page = 1, keyword = '') => {
    if (!datasetId) return
    setDocumentLoading(true)
    try {
      const res = await getDocuments(datasetId, { current: page, size: 20, keyword })
      const { records, total } = extractRecords(res)
      setDocuments(records)
      setDocumentTotal(total)
      setDocumentPage(page)
    } catch (err) {
      message.error(err?.message || '加载文档列表失败')
    } finally {
      setDocumentLoading(false)
    }
  }

  const loadSegments = async (documentId, page = 1, keyword = '') => {
    if (!documentId) return
    setSegmentLoading(true)
    try {
      const res = await getSegments(documentId, { current: page, size: 20, keyword })
      const { records, total } = extractRecords(res)
      setSegments(records)
      setSegmentTotal(total)
      setSegmentPage(page)
    } catch (err) {
      message.error(err?.message || '加载分段列表失败')
    } finally {
      setSegmentLoading(false)
    }
  }

  const loadHealth = async () => {
    setHealthLoading(true)
    try {
      const [vectorRes, embeddingRes, graphRes] = await Promise.allSettled([
        getRagVectorStoreHealth(),
        getRagEmbeddingHealth(),
        getRagGraphStoreHealth(),
      ])
      setHealth({
        vector: vectorRes.status === 'fulfilled' ? vectorRes.value?.data : null,
        embedding: embeddingRes.status === 'fulfilled' ? embeddingRes.value?.data : null,
        graph: graphRes.status === 'fulfilled' ? graphRes.value?.data : null,
      })
    } catch {
      // errors handled by global interceptor
    } finally {
      setHealthLoading(false)
    }
  }

  const loadSystemConfigs = async () => {
    try {
      const res = await getSystemConfigList({ current: 1, size: 200, prefixes: 'ai.service.embedding' })
      const { records } = extractRecords(res)
      setSystemConfigs(records)
      setEmbeddingModelOptions(buildEmbeddingModelOptions(records))
    } catch {
      // silent
    }
  }

  const loadChildChunks = async (segmentId) => {
    if (!segmentId) return
    setChildChunkLoading(true)
    try {
      const res = await getChildChunks(segmentId)
      const data = res?.data
      setChildChunks(Array.isArray(data) ? data : data?.records || data?.list || [])
    } catch {
      setChildChunks([])
    } finally {
      setChildChunkLoading(false)
    }
  }

  // ===================== Initial Load =====================

  useEffect(() => {
    loadDatasets()
    loadHealth()
    loadSystemConfigs()
  }, [])

  // ===================== Computed Values =====================

  const totalDocuments = useMemo(
    () => datasets.reduce((sum, ds) => sum + Number(ds.documentCount || 0), 0),
    [datasets],
  )

  const totalSegments = useMemo(
    () => datasets.reduce((sum, ds) => sum + Number(ds.segmentCount || ds.chunkCount || 0), 0),
    [datasets],
  )

  const healthScore = useMemo(() => {
    const checks = [health.vector, health.embedding, health.graph]
    const healthy = checks.filter((h) => h && (h.status === 'UP' || h.status === 'healthy' || h.healthy === true)).length
    return Math.round((healthy / 3) * 100)
  }, [health])

  const healthStatusLabel = (h) => {
    if (!h) return { text: '未知', color: 'default' }
    if (h.status === 'UP' || h.status === 'healthy' || h.healthy === true) return { text: '正常', color: 'green' }
    if (h.status === 'DOWN' || h.status === 'unhealthy' || h.healthy === false) return { text: '异常', color: 'red' }
    return { text: h.status || '未知', color: 'default' }
  }

  // ===================== Navigation Handlers =====================

  const enterDataset = async (dataset) => {
    setCurrentDataset(dataset)
    setCurrentDocument(null)
    setDocuments([])
    setSegments([])
    setView('documents')
    loadDocuments(dataset.id)
    // Load full dataset details in background
    try {
      const res = await getDataset(dataset.id)
      if (res?.data) {
        setCurrentDataset((prev) => ({ ...prev, ...res.data }))
      }
    } catch {
      // use list data as fallback
    }
  }

  const enterDocument = async (document) => {
    setCurrentDocument(document)
    setSegments([])
    setView('segments')
    loadSegments(document.id)
    // Load full document details in background
    try {
      const res = await getDocument(document.id)
      if (res?.data) {
        setCurrentDocument((prev) => ({ ...prev, ...res.data }))
      }
    } catch {
      // use list data as fallback
    }
  }

  const backToDatasets = () => {
    setView('datasets')
    setCurrentDataset(null)
    setCurrentDocument(null)
    setDocuments([])
    setSegments([])
    loadDatasets()
  }

  const backToDocuments = () => {
    setView('documents')
    setCurrentDocument(null)
    setSegments([])
    if (currentDataset) {
      loadDocuments(currentDataset.id)
    }
  }

  // ===================== Dataset CRUD =====================

  const openCreateDatasetModal = () => {
    setDatasetModalMode('create')
    setEditingDataset(null)
    datasetForm.resetFields()
    datasetForm.setFieldsValue({
      indexingTechnique: 'high_quality',
      chunkStructure: 'text_model',
      permission: 'only_me',
      embeddingModel: '',
    })
    setDatasetModalVisible(true)
  }

  const openEditDatasetModal = (dataset, e) => {
    if (e) {
      e.stopPropagation()
      e.preventDefault()
    }
    setDatasetModalMode('edit')
    setEditingDataset(dataset)
    datasetForm.setFieldsValue({
      name: dataset.name,
      description: dataset.description,
      indexingTechnique: dataset.indexingTechnique || 'high_quality',
      embeddingModel: dataset.embeddingModel || '',
      chunkStructure: dataset.chunkStructure || 'text_model',
      permission: dataset.permission || 'only_me',
    })
    setDatasetModalVisible(true)
  }

  const handleDatasetModalOk = async () => {
    try {
      const values = await datasetForm.validateFields()
      setDatasetModalLoading(true)
      if (datasetModalMode === 'create') {
        await createDataset(values)
        message.success('知识库创建成功')
      } else {
        await updateDataset(editingDataset.id, values)
        message.success('知识库更新成功')
      }
      setDatasetModalVisible(false)
      loadDatasets()
    } catch (err) {
      if (err?.message && !err?.errorFields) {
        message.error(err.message)
      }
    } finally {
      setDatasetModalLoading(false)
    }
  }

  const handleDeleteDataset = async (dataset, e) => {
    if (e) {
      e.stopPropagation()
      e.preventDefault()
    }
    try {
      await deleteDataset(dataset.id)
      message.success('知识库已删除')
      loadDatasets()
    } catch (err) {
      message.error(err?.message || '删除失败')
    }
  }

  // ===================== Document CRUD =====================

  const openCreateDocumentModal = () => {
    documentForm.resetFields()
    documentForm.setFieldsValue({
      dataSourceType: 'text_input',
      docForm: 'text_model',
      processMode: 'automatic',
    })
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
        docForm: values.docForm,
        processMode: values.processMode || 'automatic',
        embeddingModel: values.embeddingModel,
        docMetadata: values.docMetadata,
      }

      if (values.dataSourceType === 'text_input') {
        payload.content = values.textContent
      } else if (values.dataSourceType === 'upload_file') {
        const fileList = uploadFileList
        let fileObj = null
        if (fileList.length > 0 && fileList[0].originFileObj) {
          fileObj = fileList[0].originFileObj
        } else if (convertResult?.contentBase64) {
          payload.contentBase64 = convertResult.contentBase64
        }
        if (fileObj) {
          payload.contentBase64 = await new Promise((resolve, reject) => {
            const reader = new FileReader()
            reader.onload = () => {
              const result = String(reader.result || '')
              resolve(result.includes(',') ? result.split(',').pop() : result)
            }
            reader.onerror = reject
            reader.readAsDataURL(fileObj)
          })
        }
      }

      // Serialize process rules into JSON string
      if (values.processMode === 'custom') {
        payload.processRules = JSON.stringify({
          chunkSize: values.chunkSize,
          chunkOverlap: values.chunkOverlap,
        })
      } else if (values.processMode === 'hierarchical') {
        payload.processRules = JSON.stringify({
          parentChunkSize: values.parentChunkSize,
          parentChunkOverlap: values.parentChunkOverlap,
          childChunkSize: values.childChunkSize,
          childChunkOverlap: values.childChunkOverlap,
        })
      }

      await createDocument(currentDataset.id, payload)
      message.success('文档创建成功')
      setDocumentModalVisible(false)
      loadDocuments(currentDataset.id)
    } catch (err) {
      if (err?.message && !err?.errorFields) {
        message.error(err.message)
      }
    } finally {
      setDocumentModalLoading(false)
    }
  }

  const handleDeleteDocument = async (document) => {
    try {
      await deleteDocument(document.id)
      message.success('文档已删除')
      loadDocuments(currentDataset.id, documentPage)
    } catch (err) {
      message.error(err?.message || '删除失败')
    }
  }

  const handleToggleDocument = async (document, enabled) => {
    try {
      await toggleDocument(document.id, enabled)
      message.success(enabled ? '文档已启用' : '文档已禁用')
      loadDocuments(currentDataset.id, documentPage)
    } catch (err) {
      message.error(err?.message || '操作失败')
    }
  }

  // ===================== Segment CRUD =====================

  const openSegmentDetailDrawer = async (segment) => {
    setActiveSegment(segment)
    setChildChunks([])
    setSegmentDrawerVisible(true)
    loadChildChunks(segment.id)
    // Load full segment details in background
    try {
      const res = await getSegment(segment.id)
      if (res?.data) {
        setActiveSegment((prev) => ({ ...prev, ...res.data }))
      }
    } catch {
      // use list data as fallback
    }
  }

  const openSegmentEditDrawer = (segment) => {
    setActiveSegment(segment)
    segmentEditForm.setFieldsValue({
      content: segment.content,
      answer: segment.answer,
      keywords: segment.keywords,
      enabled: segment.enabled !== false,
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
      if (err?.message && !err?.errorFields) {
        message.error(err.message)
      }
    } finally {
      setSegmentEditLoading(false)
    }
  }

  const handleDeleteSegment = async (segment) => {
    try {
      await deleteSegment(segment.id)
      message.success('分段已删除')
      loadSegments(currentDocument.id, segmentPage, segmentSearch)
    } catch (err) {
      message.error(err?.message || '删除失败')
    }
  }

  const handleToggleSegment = async (segment, enabled) => {
    try {
      await toggleSegment(segment.id, enabled)
      message.success(enabled ? '分段已启用' : '分段已禁用')
      loadSegments(currentDocument.id, segmentPage, segmentSearch)
    } catch (err) {
      message.error(err?.message || '操作失败')
    }
  }

  // ===================== Recall Test =====================

  const handleRecallTest = async () => {
    try {
      const values = await recallForm.validateFields()
      setRecallLoading(true)
      setRecallError('')
      setRecallResult(null)
      const payload = {
        query: values.query,
        ragStrategy: values.ragStrategy,
        knowledgeBaseIds: values.knowledgeBaseIds,
        embeddingModel: values.embeddingModel,
        topK: values.topK,
        similarityThreshold: values.similarityThreshold,
      }
      const res = await runRagRecallTest(payload)
      setRecallResult(res?.data || res)
    } catch (err) {
      setRecallError(err?.message || '召回测试失败')
    } finally {
      setRecallLoading(false)
    }
  }

  // ===================== File Conversion =====================

  const handleConvertPdf = async (file) => {
    setConvertLoading(true)
    setConvertResult(null)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await convertPdf(formData)
      setConvertResult({ type: 'pdf', data: res?.data, fileName: file.name.replace(/\.pdf$/i, '.docx') })
      message.success('PDF 转换成功')
    } catch (err) {
      message.error(err?.message || 'PDF 转换失败')
    } finally {
      setConvertLoading(false)
    }
  }

  const handleConvertPpt = async (file) => {
    setConvertLoading(true)
    setConvertResult(null)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await convertPpt(formData)
      setConvertResult({ type: 'ppt', data: res?.data, fileName: file.name.replace(/\.pptx$/i, '.docx') })
      message.success('PPT 转换成功')
    } catch (err) {
      message.error(err?.message || 'PPT 转换失败')
    } finally {
      setConvertLoading(false)
    }
  }

  // ===================== Dataset List View (Level 1) =====================

  const renderDatasetList = () => (
    <div>
      {/* Hero Banner */}
      <div className="kb-hero">
        <div className="kb-hero__content">
          <h1>知识库管理</h1>
          <p>
            管理智能校园平台的全部知识库资源。支持创建知识库、上传文档、自动分段与向量化索引，
            并通过召回测试验证检索质量。
          </p>
          <div className="kb-hero__tags">
            <Tag color="teal">RAG 增强检索</Tag>
            <Tag color="blue">自动分段</Tag>
            <Tag color="green">多模型嵌入</Tag>
            <Tag color="purple">父子切片</Tag>
          </div>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => { loadDatasets(); loadHealth() }} loading={datasetLoading}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateDatasetModal}>
            创建知识库
          </Button>
        </Space>
      </div>

      {/* Stats Row */}
      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        <Col xs={12} sm={6}>
          <Card className="kb-metric-card" size="small">
            <Statistic title="知识库总数" value={datasets.length} prefix={<DatabaseOutlined />} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card className="kb-metric-card" size="small">
            <Statistic title="文档总数" value={totalDocuments} prefix={<FileTextOutlined />} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card className="kb-metric-card" size="small">
            <Statistic title="分段总数" value={totalSegments} prefix={<BranchesOutlined />} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card className="kb-metric-card" size="small">
            <Statistic
              title="健康评分"
              value={healthScore}
              suffix="%"
              prefix={<HealthMonitorOutlined />}
              valueStyle={{ color: healthScore >= 66 ? '#0f766e' : healthScore >= 33 ? '#d97706' : '#dc2626' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Health Row */}
      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        <Col xs={24} sm={8}>
          <Card className="kb-health-card" size="small" loading={healthLoading}>
            <div className="kb-health-card__top">
              <span className="kb-health-card__icon"><ApiOutlined /></span>
              <Tag color={healthStatusLabel(health.vector).color}>{healthStatusLabel(health.vector).text}</Tag>
            </div>
            <Statistic title="向量存储服务" value={health?.vector?.version || health?.vector?.storeType || '-'} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card className="kb-health-card" size="small" loading={healthLoading}>
            <div className="kb-health-card__top">
              <span className="kb-health-card__icon"><SettingOutlined /></span>
              <Tag color={healthStatusLabel(health.embedding).color}>{healthStatusLabel(health.embedding).text}</Tag>
            </div>
            <Statistic title="Embedding 服务" value={health?.embedding?.model || health?.embedding?.provider || '-'} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card className="kb-health-card" size="small" loading={healthLoading}>
            <div className="kb-health-card__top">
              <span className="kb-health-card__icon"><BranchesOutlined /></span>
              <Tag color={healthStatusLabel(health.graph).color}>{healthStatusLabel(health.graph).text}</Tag>
            </div>
            <Statistic title="图存储服务" value={health?.graph?.storeType || health?.graph?.version || '-'} />
          </Card>
        </Col>
      </Row>

      {/* Dataset Grid */}
      <div className="kb-section-head">
        <div>
          <Title level={4} style={{ margin: 0 }}>知识库列表</Title>
          <Text type="secondary">共 {datasets.length} 个知识库，点击卡片进入文档管理</Text>
        </div>
      </div>

      <div className="kb-dataset-grid">
        {/* Create card */}
        <div className="kb-create-card" onClick={openCreateDatasetModal}>
          <PlusOutlined className="kb-create-card__icon" />
          <span className="kb-create-card__text">创建知识库</span>
        </div>

        {/* Dataset cards */}
        {datasets.map((ds) => (
          <div
            key={ds.id}
            className="kb-dataset-card"
            onClick={() => enterDataset(ds)}
          >
            <div className="kb-dataset-card__header">
              <span className="kb-dataset-card__title">{ds.name || ds.id}</span>
              <div className="kb-dataset-card__actions">
                <Tooltip title="编辑">
                  <Button
                    type="text"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={(e) => openEditDatasetModal(ds, e)}
                  />
                </Tooltip>
                <Popconfirm
                  title="确定删除该知识库？"
                  description="删除后所有文档和分段数据将无法恢复"
                  onConfirm={(e) => handleDeleteDataset(ds, e)}
                  onCancel={(e) => { if (e) e.stopPropagation() }}
                  okText="确定"
                  cancelText="取消"
                >
                  <Tooltip title="删除">
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={(e) => { e.stopPropagation(); e.preventDefault() }}
                    />
                  </Tooltip>
                </Popconfirm>
              </div>
            </div>
            <div className="kb-dataset-card__desc">{ds.description || '暂无描述'}</div>
            <div className="kb-dataset-card__meta">
              <span className="kb-dataset-card__meta-item">
                <FileTextOutlined /> {formatNumber(ds.documentCount)} 文档
              </span>
              <span className="kb-dataset-card__meta-item">
                <BranchesOutlined /> {formatNumber(ds.segmentCount || ds.chunkCount)} 分段
              </span>
              {ds.indexingTechnique && (
                <Tag style={{ marginRight: 0 }} color={ds.indexingTechnique === 'high_quality' ? 'teal' : 'default'}>
                  {ds.indexingTechnique === 'high_quality' ? '高质量' : '经济'}
                </Tag>
              )}
              {ds.chunkStructure && (
                <Tag style={{ marginRight: 0 }} color="blue">{ds.chunkStructure}</Tag>
              )}
              {ds.embeddingModel && (
                <Tag style={{ marginRight: 0 }}>{ds.embeddingModel}</Tag>
              )}
            </div>
          </div>
        ))}
      </div>

      {datasets.length === 0 && !datasetLoading && (
        <Empty description="暂无知识库，点击上方按钮创建" style={{ marginTop: 40 }} />
      )}

      {/* Create/Edit Dataset Modal */}
      <Modal
        title={datasetModalMode === 'create' ? '创建知识库' : '编辑知识库'}
        open={datasetModalVisible}
        onOk={handleDatasetModalOk}
        onCancel={() => setDatasetModalVisible(false)}
        confirmLoading={datasetModalLoading}
        okText={datasetModalMode === 'create' ? '创建' : '保存'}
        cancelText="取消"
        destroyOnClose
        width={560}
      >
        <Form form={datasetForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="知识库名称" rules={[{ required: true, message: '请输入知识库名称' }]}>
            <Input placeholder="例如：校园规章制度、课程资料库" maxLength={100} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea placeholder="描述该知识库的用途和内容范围" rows={3} maxLength={500} showCount />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="indexingTechnique" label="索引质量" rules={[{ required: true }]}>
                <Select>
                  <Select.Option value="high_quality">高质量 high_quality</Select.Option>
                  <Select.Option value="economy">经济 economy</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="embeddingModel" label="嵌入模型">
                <Input placeholder="例如：text-embedding-3-small" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="chunkStructure" label="分段结构" rules={[{ required: true }]}>
                <Select>
                  <Select.Option value="text_model">文本模型 text_model</Select.Option>
                  <Select.Option value="qa_model">问答模型 qa_model</Select.Option>
                  <Select.Option value="hierarchical_model">层级模型 hierarchical_model</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="permission" label="权限" rules={[{ required: true }]}>
                <Select>
                  <Select.Option value="only_me">仅自己 only_me</Select.Option>
                  <Select.Option value="all_team">全团队 all_team</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  )

  // ===================== Document Management View (Level 2) =====================

  const documentColumns = [
    {
      title: '文档名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (text, record) => (
        <a onClick={() => enterDocument(record)}>{text || record.id}</a>
      ),
    },
    {
      title: '数据来源',
      dataIndex: 'dataSourceType',
      key: 'dataSourceType',
      width: 110,
      render: (val) => {
        const map = { text_input: '文本输入', upload_file: '文件上传', import: '导入' }
        return <Tag>{map[val] || val || '-'}</Tag>
      },
    },
    {
      title: '索引状态',
      dataIndex: 'indexingStatus',
      key: 'indexingStatus',
      width: 110,
      render: (status) => {
        const info = getStatusInfo(status)
        return <Tag color={info.color}>{info.label}</Tag>
      },
    },
    {
      title: '文档形式',
      dataIndex: 'docForm',
      key: 'docForm',
      width: 110,
      render: (val) => val || '-',
    },
    {
      title: '字数',
      dataIndex: 'wordCount',
      key: 'wordCount',
      width: 90,
      render: (val) => formatNumber(val),
    },
    {
      title: '分段数',
      dataIndex: 'segmentCount',
      key: 'segmentCount',
      width: 90,
      render: (val) => formatNumber(val),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (val, record) => (
        <Switch
          checked={val !== false}
          size="small"
          onChange={(checked) => handleToggleDocument(record, checked)}
        />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="查看分段">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => enterDocument(record)}>
              查看
            </Button>
          </Tooltip>
          <Popconfirm
            title="确定删除该文档？"
            onConfirm={() => handleDeleteDocument(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const renderDocumentManagement = () => {
    if (!currentDataset) return null

    return (
      <div>
        {/* Breadcrumb */}
        <Breadcrumb className="kb-breadcrumb" items={[
          { title: <a onClick={backToDatasets}><HomeOutlined /> 知识库管理</a> },
          { title: currentDataset.name || currentDataset.id },
        ]} />

        {/* Dataset Info Card */}
        <div className="kb-dataset-info">
          <div className="kb-dataset-info__title">{currentDataset.name || currentDataset.id}</div>
          <div className="kb-dataset-info__desc">{currentDataset.description || '暂无描述'}</div>
          <div className="kb-dataset-info__tags">
            <Tag color={currentDataset.indexingTechnique === 'high_quality' ? 'teal' : 'default'}>
              索引: {currentDataset.indexingTechnique || '-'}
            </Tag>
            <Tag color="blue">嵌入模型: {currentDataset.embeddingModel || '-'}</Tag>
            <Tag color="purple">分段结构: {currentDataset.chunkStructure || '-'}</Tag>
            <Tag>文档数: {formatNumber(currentDataset.documentCount)}</Tag>
            <Tag>字数: {formatNumber(currentDataset.wordCount)}</Tag>
          </div>
        </div>

        {/* Process Rule */}
        {currentDataset.processRule && (
          <div className="kb-process-rule">
            <div className="kb-process-rule__title">
              <SettingOutlined /> 当前处理规则
            </div>
            <div className="kb-process-rule__meta">
              <span>模式: {currentDataset.processRule.mode || '-'}</span>
              <span>块大小: {currentDataset.processRule.chunkSize || '-'}</span>
              <span>重叠: {currentDataset.processRule.chunkOverlap || '-'}</span>
            </div>
          </div>
        )}

        {/* Actions */}
        <div className="kb-section-head">
          <div>
            <Title level={4} style={{ margin: 0 }}>文档列表</Title>
            <Text type="secondary">共 {documentTotal} 个文档</Text>
          </div>
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => loadDocuments(currentDataset.id, documentPage)}
              loading={documentLoading}
            >
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateDocumentModal}>
              添加文档
            </Button>
          </Space>
        </div>

        {/* Document Table */}
        <Card className="kb-panel-card" bodyStyle={{ padding: 0 }}>
          <Table
            className="kb-document-table"
            columns={documentColumns}
            dataSource={documents}
            rowKey="id"
            loading={documentLoading}
            pagination={{
              current: documentPage,
              total: documentTotal,
              pageSize: 20,
              showSizeChanger: false,
              onChange: (page) => loadDocuments(currentDataset.id, page),
            }}
            locale={{ emptyText: <Empty description="暂无文档，点击「添加文档」上传" /> }}
          />
        </Card>

        {/* Create Document Modal */}
        <Modal
          title="添加文档"
          open={documentModalVisible}
          onOk={handleDocumentModalOk}
          onCancel={() => setDocumentModalVisible(false)}
          confirmLoading={documentModalLoading}
          okText="创建"
          cancelText="取消"
          destroyOnClose
          width={640}
        >
          <Form form={documentForm} layout="vertical" style={{ marginTop: 16 }}>
            <Form.Item name="name" label="文档名称" rules={[{ required: true, message: '请输入文档名称' }]}>
              <Input placeholder="例如：校园管理制度2024" maxLength={200} />
            </Form.Item>

            <Form.Item name="dataSourceType" label="数据来源" rules={[{ required: true }]}>
              <Segmented
                block
                options={[
                  { label: '文本输入', value: 'text_input', icon: <FileTextOutlined /> },
                  { label: '文件上传', value: 'upload_file', icon: <UploadOutlined /> },
                ]}
              />
            </Form.Item>

            {watchedDataSourceType === 'text_input' && (
              <Form.Item name="textContent" label="文本内容" rules={[{ required: true, message: '请输入文本内容' }]}>
                <TextArea placeholder="粘贴或输入文档内容..." rows={8} showCount maxLength={100000} />
              </Form.Item>
            )}

            {watchedDataSourceType === 'upload_file' && (
              <Form.Item label="上传文件">
                <Upload
                  fileList={uploadFileList}
                  maxCount={1}
                  accept=".docx,.txt,.pdf,.pptx"
                  beforeUpload={(file) => {
                    setUploadFileList([{ ...file, uid: file.uid || '-1', name: file.name }])
                    return false
                  }}
                  onRemove={() => {
                    setUploadFileList([])
                    return true
                  }}
                >
                  <Button icon={<UploadOutlined />}>选择文件（.docx, .txt, .pdf, .pptx）</Button>
                </Upload>
                {uploadFileList.length > 0 && /\.(pdf|pptx)$/i.test(uploadFileList[0]?.name || '') && (
                  <Alert
                    type="info"
                    showIcon
                    style={{ marginTop: 8 }}
                    message="检测到 PDF/PPTX 文件，建议先转换再上传"
                    description={
                      <Space style={{ marginTop: 8 }}>
                        <Button
                          size="small"
                          type="primary"
                          loading={convertLoading}
                          onClick={() => {
                            const file = uploadFileList[0]?.originFileObj || uploadFileList[0]
                            if (/\.pdf$/i.test(uploadFileList[0]?.name || '')) {
                              handleConvertPdf(file)
                            } else {
                              handleConvertPpt(file)
                            }
                          }}
                        >
                          <SwapOutlined /> 转换为 DOCX
                        </Button>
                        {convertResult && (
                          <Tag color="green">转换成功: {convertResult.fileName}</Tag>
                        )}
                      </Space>
                    }
                  />
                )}
              </Form.Item>
            )}

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="docForm" label="文档形式" rules={[{ required: true }]}>
                  <Select>
                    <Select.Option value="text_model">文本模型 text_model</Select.Option>
                    <Select.Option value="qa_model">问答模型 qa_model</Select.Option>
                  </Select>
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="processMode" label="处理模式">
                  <Select>
                    <Select.Option value="automatic">自动 automatic</Select.Option>
                    <Select.Option value="custom">自定义 custom</Select.Option>
                    <Select.Option value="hierarchical">层级 hierarchical</Select.Option>
                  </Select>
                </Form.Item>
              </Col>
            </Row>

            {watchedProcessMode === 'custom' && (
              <div className="kb-chunk-settings">
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item name="chunkSize" label="分块大小">
                      <InputNumber min={100} max={4000} style={{ width: '100%' }} placeholder="800" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="chunkOverlap" label="重叠大小">
                      <InputNumber min={0} max={1000} style={{ width: '100%' }} placeholder="120" />
                    </Form.Item>
                  </Col>
                </Row>
              </div>
            )}

            {watchedProcessMode === 'hierarchical' && (
              <div className="kb-chunk-settings">
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item name="parentChunkSize" label="父块大小">
                      <InputNumber min={200} max={8000} style={{ width: '100%' }} placeholder="1600" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="childChunkSize" label="子块大小">
                      <InputNumber min={50} max={2000} style={{ width: '100%' }} placeholder="420" />
                    </Form.Item>
                  </Col>
                </Row>
              </div>
            )}

            <Form.Item name="embeddingModel" label="嵌入模型">
              {embeddingModelOptions.length > 0 ? (
                <Select placeholder="选择嵌入模型" allowClear options={embeddingModelOptions} />
              ) : (
                <Input placeholder="输入嵌入模型名称，如 text-embedding-3-small" />
              )}
            </Form.Item>

            <Collapse ghost className="kb-advanced-collapse" items={[{
              key: 'metadata',
              label: '文档元数据（可选）',
              children: (
                <Form.Item name="docMetadata" noStyle>
                  <TextArea
                    placeholder='JSON 格式，例如：{"tags": ["制度"], "scene": "campus"}'
                    rows={3}
                  />
                </Form.Item>
              ),
            }]} />
          </Form>
        </Modal>
      </div>
    )
  }

  // ===================== Segment Management View (Level 3) =====================

  const segmentColumns = [
    {
      title: '#',
      key: 'position',
      width: 60,
      render: (_, __, index) => (segmentPage - 1) * 20 + index + 1,
    },
    {
      title: '内容',
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
      render: (text) => (
        <div className="kb-segment-content-cell">{truncateText(text, 200)}</div>
      ),
    },
    {
      title: '字数',
      dataIndex: 'wordCount',
      key: 'wordCount',
      width: 90,
      render: (val, record) => formatNumber(val || record.size || record.content?.length),
    },
    {
      title: '命中',
      dataIndex: 'hitCount',
      key: 'hitCount',
      width: 80,
      render: (val) => formatNumber(val),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (val, record) => (
        <Switch
          checked={val !== false}
          size="small"
          onChange={(checked) => handleToggleSegment(record, checked)}
        />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="查看详情">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openSegmentDetailDrawer(record)}>
              查看
            </Button>
          </Tooltip>
          <Tooltip title="编辑">
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openSegmentEditDrawer(record)}>
              编辑
            </Button>
          </Tooltip>
          <Popconfirm
            title="确定删除该分段？"
            onConfirm={() => handleDeleteSegment(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const renderSegmentManagement = () => {
    if (!currentDocument || !currentDataset) return null

    const indexingStatus = currentDocument.indexingStatus || 'completed'

    return (
      <div>
        {/* Breadcrumb */}
        <Breadcrumb className="kb-breadcrumb" items={[
          { title: <a onClick={backToDatasets}><HomeOutlined /> 知识库管理</a> },
          { title: <a onClick={backToDocuments}>{currentDataset.name || currentDataset.id}</a> },
          { title: currentDocument.name || currentDocument.id },
        ]} />

        {/* Document Detail Card */}
        <div className="kb-document-detail">
          <div className="kb-document-detail__header">
            <FileTextOutlined className="kb-document-detail__icon" />
            <div className="kb-document-detail__info">
              <div className="kb-document-detail__title">{currentDocument.name || currentDocument.id}</div>
              <div className="kb-document-detail__meta">
                <span>字数: {formatNumber(currentDocument.wordCount)}</span>
                <span>分段数: {formatNumber(currentDocument.segmentCount)}</span>
                <span>Tokens: {formatNumber(currentDocument.tokens)}</span>
                {currentDocument.batch && <span>批次: {currentDocument.batch}</span>}
                {currentDocument.completedAt && <span>完成时间: {formatDateTime(currentDocument.completedAt)}</span>}
              </div>
            </div>
            <Tag color={getStatusInfo(indexingStatus).color} style={{ fontSize: 14, padding: '4px 12px' }}>
              {getStatusInfo(indexingStatus).label}
            </Tag>
          </div>

          {/* Pipeline */}
          <div className="kb-pipeline">
            {pipelineSteps.map((step, idx) => {
              const state = getPipelineStepState(indexingStatus, idx)
              return (
                <div key={step.key} className={`kb-pipeline__step kb-pipeline__step--${state}`}>
                  {state === 'completed' ? (
                    <CheckCircleOutlined />
                  ) : state === 'active' ? (
                    <SyncOutlined spin />
                  ) : state === 'error' ? (
                    <InfoCircleOutlined />
                  ) : (
                    <ClockCircleOutlined />
                  )}
                  <span>{step.label}</span>
                </div>
              )
            })}
          </div>
        </div>

        {/* Actions */}
        <div className="kb-section-head">
          <div>
            <Title level={4} style={{ margin: 0 }}>分段列表</Title>
            <Text type="secondary">共 {segmentTotal} 个分段</Text>
          </div>
          <Space>
            <Input
              className="kb-segment-search"
              placeholder="搜索分段内容..."
              prefix={<SearchOutlined />}
              allowClear
              value={segmentSearch}
              onChange={(e) => setSegmentSearch(e.target.value)}
              onPressEnter={() => loadSegments(currentDocument.id, 1, segmentSearch)}
            />
            <Button
              icon={<ReloadOutlined />}
              onClick={() => loadSegments(currentDocument.id, segmentPage, segmentSearch)}
              loading={segmentLoading}
            >
              刷新
            </Button>
          </Space>
        </div>

        {/* Segment Table */}
        <Card className="kb-panel-card" bodyStyle={{ padding: 0 }}>
          <Table
            columns={segmentColumns}
            dataSource={segments}
            rowKey="id"
            loading={segmentLoading}
            pagination={{
              current: segmentPage,
              total: segmentTotal,
              pageSize: 20,
              showSizeChanger: false,
              onChange: (page) => loadSegments(currentDocument.id, page, segmentSearch),
            }}
            locale={{ emptyText: <Empty description="暂无分段数据" /> }}
          />
        </Card>

        {/* Segment Detail Drawer */}
        <Drawer
          title="分段详情"
          open={segmentDrawerVisible}
          onClose={() => setSegmentDrawerVisible(false)}
          width={640}
          destroyOnClose
        >
          {activeSegment && (
            <div>
              {/* Position and status */}
              <div className="kb-drawer-section">
                <div className="kb-drawer-meta-grid">
                  <div className="kb-drawer-meta-item">
                    <span className="kb-drawer-meta-item__label">分段 ID</span>
                    <span className="kb-drawer-meta-item__value">{activeSegment.id || '-'}</span>
                  </div>
                  <div className="kb-drawer-meta-item">
                    <span className="kb-drawer-meta-item__label">位置</span>
                    <span className="kb-drawer-meta-item__value">#{activeSegment.position || activeSegment.chunkIndex || '-'}</span>
                  </div>
                  <div className="kb-drawer-meta-item">
                    <span className="kb-drawer-meta-item__label">字数</span>
                    <span className="kb-drawer-meta-item__value">{formatNumber(activeSegment.wordCount || activeSegment.content?.length)}</span>
                  </div>
                  <div className="kb-drawer-meta-item">
                    <span className="kb-drawer-meta-item__label">命中次数</span>
                    <span className="kb-drawer-meta-item__value">{formatNumber(activeSegment.hitCount)}</span>
                  </div>
                  <div className="kb-drawer-meta-item">
                    <span className="kb-drawer-meta-item__label">状态</span>
                    <span className="kb-drawer-meta-item__value">
                      <Tag color={activeSegment.enabled !== false ? 'green' : 'default'}>
                        {activeSegment.enabled !== false ? '启用' : '禁用'}
                      </Tag>
                    </span>
                  </div>
                  <div className="kb-drawer-meta-item">
                    <span className="kb-drawer-meta-item__label">创建时间</span>
                    <span className="kb-drawer-meta-item__value">{formatDateTime(activeSegment.createdAt)}</span>
                  </div>
                </div>
              </div>

              {/* Content */}
              <div className="kb-drawer-section">
                <div className="kb-drawer-section__title">内容</div>
                <div className="kb-drawer-content">{activeSegment.content || '-'}</div>
              </div>

              {/* Answer */}
              {activeSegment.answer && (
                <div className="kb-drawer-section">
                  <div className="kb-drawer-section__title">回答</div>
                  <div className="kb-drawer-content">{activeSegment.answer}</div>
                </div>
              )}

              {/* Keywords */}
              {activeSegment.keywords && (
                <div className="kb-drawer-section">
                  <div className="kb-drawer-section__title">关键词</div>
                  <div className="kb-drawer-tags">
                    {(Array.isArray(activeSegment.keywords) ? activeSegment.keywords : String(activeSegment.keywords).split(/[,，]/))
                      .map((kw, i) => (
                        <Tag key={i} color="teal">{String(kw).trim()}</Tag>
                      ))}
                  </div>
                </div>
              )}

              {/* Metadata */}
              {activeSegment.metadata && Object.keys(activeSegment.metadata).length > 0 && (
                <div className="kb-drawer-section">
                  <div className="kb-drawer-section__title">元数据</div>
                  <div className="kb-drawer-content" style={{ fontSize: 12 }}>
                    {JSON.stringify(activeSegment.metadata, null, 2)}
                  </div>
                </div>
              )}

              {/* Child Chunks */}
              <div className="kb-drawer-section">
                <div className="kb-drawer-section__title">
                  子片段 {childChunks.length > 0 && <Tag>{childChunks.length}</Tag>}
                  {childChunkLoading && <SyncOutlined spin style={{ marginLeft: 8 }} />}
                </div>
                {childChunks.length === 0 && !childChunkLoading && (
                  <Text type="secondary">暂无子片段</Text>
                )}
                {childChunks.map((chunk, idx) => (
                  <div key={chunk.id || idx} className="kb-child-chunk-item">
                    <div className="kb-child-chunk-item__header">
                      <span>子片段 #{idx + 1}</span>
                      <span>{formatNumber(chunk.content?.length || chunk.wordCount)} 字</span>
                    </div>
                    <div className="kb-child-chunk-item__content">{chunk.content || '-'}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </Drawer>

        {/* Segment Edit Drawer */}
        <Drawer
          title="编辑分段"
          open={segmentEditDrawerVisible}
          onClose={() => setSegmentEditDrawerVisible(false)}
          width={560}
          destroyOnClose
          extra={
            <Space>
              <Button onClick={() => setSegmentEditDrawerVisible(false)}>取消</Button>
              <Button type="primary" loading={segmentEditLoading} onClick={handleSegmentEditOk}>
                保存
              </Button>
            </Space>
          }
        >
          <Form form={segmentEditForm} layout="vertical">
            <Form.Item name="content" label="内容" rules={[{ required: true, message: '请输入内容' }]}>
              <TextArea rows={10} placeholder="分段内容" />
            </Form.Item>
            <Form.Item name="answer" label="回答（QA 模型）">
              <TextArea rows={4} placeholder="对应回答内容（可选）" />
            </Form.Item>
            <Form.Item name="keywords" label="关键词">
              <Input placeholder="逗号分隔的关键词列表" />
            </Form.Item>
            <Form.Item name="enabled" label="启用状态" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Form>
        </Drawer>
      </div>
    )
  }

  // ===================== Recall Test Tab =====================

  const recallResultChunks = useMemo(() => {
    if (!recallResult) return []
    if (Array.isArray(recallResult)) return recallResult
    if (Array.isArray(recallResult.records)) return recallResult.records
    if (Array.isArray(recallResult.chunks)) return recallResult.chunks
    if (Array.isArray(recallResult.hits)) return recallResult.hits
    return []
  }, [recallResult])

  const recallColumns = [
    {
      title: '来源',
      key: 'source',
      width: 160,
      render: (_, record) => record.metadata?.sourceName || record.source || record.documentName || '-',
    },
    {
      title: '相似度',
      dataIndex: 'score',
      key: 'score',
      width: 100,
      render: (val) => val != null ? (
        <Tag color={val >= 0.8 ? 'green' : val >= 0.5 ? 'blue' : 'orange'}>
          {(val * 100).toFixed(1)}%
        </Tag>
      ) : '-',
      sorter: (a, b) => (a.score || 0) - (b.score || 0),
      defaultSortOrder: 'descend',
    },
    {
      title: '内容',
      dataIndex: 'content',
      key: 'content',
      render: (text) => (
        <div className="kb-chunk-cell__text">{text || '-'}</div>
      ),
    },
  ]

  const renderRecallTest = () => (
    <div>
      <Card className="kb-panel-card" title={<><ExperimentOutlined /> 召回测试</>} style={{ marginBottom: 20 }}>
        <Form form={recallForm} layout="vertical" initialValues={{
          ragStrategy: 'hybrid_search',
          topK: 5,
          similarityThreshold: 0.5,
          knowledgeBaseIds: datasets.length > 0 ? [datasets[0].id] : [],
        }}>
          <Form.Item name="query" label="查询内容" rules={[{ required: true, message: '请输入查询内容' }]}>
            <TextArea rows={3} placeholder="输入要检索的问题或关键词..." />
          </Form.Item>

          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="ragStrategy" label="检索策略">
                <Select options={recallStrategyOptions} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="knowledgeBaseIds" label="知识库" rules={[{ required: true, message: '请选择知识库' }]}>
                <Select
                  mode="multiple"
                  placeholder="选择要检索的知识库"
                  options={datasets.map((ds) => ({ value: ds.id, label: ds.name || ds.id }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col xs={24} sm={8}>
              <Form.Item name="embeddingModel" label="嵌入模型">
                {embeddingModelOptions.length > 0 ? (
                  <Select placeholder="选择嵌入模型" allowClear options={embeddingModelOptions} />
                ) : (
                  <Input placeholder="嵌入模型名称" />
                )}
              </Form.Item>
            </Col>
            <Col xs={12} sm={8}>
              <Form.Item name="topK" label="Top K">
                <Slider min={1} max={20} marks={{ 1: '1', 20: '20' }} />
              </Form.Item>
            </Col>
            <Col xs={12} sm={8}>
              <Form.Item name="similarityThreshold" label="相似度阈值">
                <Slider min={0} max={1} step={0.05} marks={{ 0: '0', 0.5: '0.5', 1: '1' }} />
              </Form.Item>
            </Col>
          </Row>

          <Button
            type="primary"
            icon={<SearchOutlined />}
            loading={recallLoading}
            onClick={handleRecallTest}
            size="large"
          >
            开始检索
          </Button>
        </Form>
      </Card>

      {recallError && (
        <Alert type="error" message="召回测试失败" description={recallError} showIcon closable style={{ marginBottom: 16 }} />
      )}

      {recallResult && (
        <Card className="kb-panel-card" title={`检索结果（${recallResultChunks.length} 条命中）`}>
          <Table
            columns={recallColumns}
            dataSource={recallResultChunks}
            rowKey={(record, idx) => record.id || `recall-${idx}`}
            pagination={false}
            size="small"
            locale={{ emptyText: <Empty description="未检索到相关内容" /> }}
          />
        </Card>
      )}
    </div>
  )

  // ===================== File Conversion Tab =====================

  const renderFileConversion = () => (
    <div>
      <Card className="kb-panel-card" title={<><SwapOutlined /> 文件转换</>} style={{ marginBottom: 20 }}>
        <Alert
          type="info"
          showIcon
          message="支持将 PDF 和 PPTX 文件转换为 DOCX 格式，转换后可直接上传为文档。"
          style={{ marginBottom: 16 }}
        />

        <Row gutter={24}>
          <Col xs={24} sm={12}>
            <Card title="PDF -> DOCX" size="small" className="kb-panel-card">
              <Upload
                fileList={convertFileList.filter((f) => /\.pdf$/i.test(f.name))}
                maxCount={1}
                accept=".pdf"
                beforeUpload={(file) => {
                  setConvertFileList([file])
                  handleConvertPdf(file)
                  return false
                }}
                onRemove={() => setConvertFileList([])}
              >
                <Button icon={<UploadOutlined />} loading={convertLoading}>选择 PDF 文件</Button>
              </Upload>
            </Card>
          </Col>
          <Col xs={24} sm={12}>
            <Card title="PPTX -> DOCX" size="small" className="kb-panel-card">
              <Upload
                fileList={convertFileList.filter((f) => /\.pptx$/i.test(f.name))}
                maxCount={1}
                accept=".pptx"
                beforeUpload={(file) => {
                  setConvertFileList([file])
                  handleConvertPpt(file)
                  return false
                }}
                onRemove={() => setConvertFileList([])}
              >
                <Button icon={<UploadOutlined />} loading={convertLoading}>选择 PPTX 文件</Button>
              </Upload>
            </Card>
          </Col>
        </Row>

        {convertResult && (
          <div className="kb-convert-result">
            <CheckCircleOutlined style={{ color: '#0f766e', fontSize: 18 }} />
            <Text strong>转换成功</Text>
            <Tag color="green">{convertResult.fileName}</Tag>
            <Text type="secondary">转换后的文件可在「文档管理」中上传</Text>
          </div>
        )}
      </Card>
    </div>
  )

  // ===================== Main Render =====================

  const renderCurrentView = () => {
    switch (view) {
      case 'documents':
        return renderDocumentManagement()
      case 'segments':
        return renderSegmentManagement()
      default:
        return renderDatasetList()
    }
  }

  const tabItems = [
    {
      key: 'manage',
      label: <span><FolderOutlined /> 知识库管理</span>,
      children: renderCurrentView(),
    },
    {
      key: 'recall',
      label: <span><ExperimentOutlined /> 召回测试</span>,
      children: renderRecallTest(),
    },
    {
      key: 'convert',
      label: <span><SwapOutlined /> 文件转换</span>,
      children: renderFileConversion(),
    },
  ]

  return (
    <div className="knowledge-base-manage">
      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key)}
        items={tabItems}
        className="rag-tabs"
      />
    </div>
  )
}

export default KnowledgeBaseManage
