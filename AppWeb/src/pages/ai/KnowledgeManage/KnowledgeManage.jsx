/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Avatar,
  Button,
  Descriptions,
  Drawer,
  Dropdown,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
  message,
} from 'antd'
import {
  ApiOutlined,
  AppstoreOutlined,
  ArrowLeftOutlined,
  BookOutlined,
  CheckCircleFilled,
  CloudUploadOutlined,
  ClusterOutlined,
  CommentOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExportOutlined,
  FileTextOutlined,
  FilterOutlined,
  FontSizeOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  SettingOutlined,
  ShareAltOutlined,
  StopOutlined,
  TagsOutlined,
  ThunderboltOutlined,
  UploadOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  createMaxKbAccount,
  deleteMaxKbAccount,
  getMaxKbAccounts,
  getMaxKbDocuments,
  getMaxKbEnvironments,
  getMaxKbKnowledges,
  runMaxKbHitTest,
  testMaxKbAccount,
  updateMaxKbAccount,
  updateMaxKbAccountStatus,
  uploadMaxKbDocuments,
} from '../../../api/maxkbKnowledge'
import './KnowledgeManage.css'

const { Text, Title } = Typography
const { TextArea } = Input

const environmentColors = {
  local: 'green',
  test: 'blue',
  prod: 'red',
  custom: 'purple',
}

const searchModeOptions = [
  { value: 'blend', label: '混合检索' },
  { value: 'embedding', label: '向量检索' },
  { value: 'keywords', label: '关键词检索' },
]

const splitStrategyOptions = [
  { value: '', label: '默认分段' },
  { value: 'llm_text', label: '大模型文本分段' },
  { value: 'llm_vision', label: '大模型视觉分段' },
]

const menuItems = [
  { key: 'document', label: '文档', icon: <FileTextOutlined /> },
  { key: 'problem', label: '问题', icon: <CommentOutlined /> },
  { key: 'termbase', label: '自定义分词', icon: <FontSizeOutlined /> },
  { key: 'hit', label: '召回测试', icon: <ThunderboltOutlined /> },
  { key: 'setting', label: '设置', icon: <SettingOutlined /> },
]

const hitHandlingMethodText = {
  optimization: '模型优化',
  directly_return: '直接返回',
  DIRECTLY_RETURN: '直接返回',
  MODEL_OPTIMIZATION: '模型优化',
}

const unwrapMaxKbPayload = (response) => {
  let payload = response?.data ?? response
  if (payload && typeof payload === 'object' && 'data' in payload && ('code' in payload || 'msg' in payload || 'message' in payload)) {
    payload = payload.data
  }
  return payload
}

const normalizePage = (response) => {
  const payload = unwrapMaxKbPayload(response)
  if (Array.isArray(payload)) {
    return { records: payload, total: payload.length, page: 1, size: payload.length || 10 }
  }
  const records = payload?.records || payload?.list || payload?.items || payload?.rows || []
  return {
    records: Array.isArray(records) ? records : [],
    total: Number(payload?.total ?? payload?.count ?? records.length ?? 0),
    page: Number(payload?.page ?? payload?.current_page ?? payload?.current ?? 1),
    size: Number(payload?.size ?? payload?.page_size ?? payload?.pageSize ?? 10),
  }
}

const normalizeRows = (response) => {
  const payload = unwrapMaxKbPayload(response)
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.records)) return payload.records
  if (Array.isArray(payload?.list)) return payload.list
  if (Array.isArray(payload?.items)) return payload.items
  if (Array.isArray(payload?.documents)) return payload.documents
  return payload ? [payload] : []
}

const textValue = (...values) => {
  const value = values.find((item) => item !== undefined && item !== null && item !== '')
  return value === undefined ? '-' : String(value)
}

const compactNumber = (...values) => {
  const value = values.find((item) => item !== undefined && item !== null && item !== '')
  const number = Number(value)
  if (Number.isNaN(number)) return textValue(value)
  if (number >= 10000) return `${(number / 1000).toFixed(1)}k`
  if (number >= 1000) return `${(number / 1000).toFixed(1)}k`
  return number.toLocaleString()
}

const recordKey = (record) => (
  record?.id || record?.document_id || record?.paragraph_id || record?.uuid || JSON.stringify(record).slice(0, 80)
)

const statusText = (record) => {
  const raw = record?.status
  if (raw && typeof raw === 'object') {
    const values = Object.values(raw).map((item) => String(item?.state ?? item?.status ?? item).toLowerCase())
    if (values.some((item) => ['failure', 'failed', 'error'].includes(item))) return '失败'
    if (values.some((item) => ['started', 'pending', 'running', 'processing'].includes(item))) return '处理中'
    if (values.some((item) => ['success', 'completed', 'finish', 'done'].includes(item))) return '成功'
  }
  const normalized = String(raw ?? '').toLowerCase()
  if (!normalized || normalized === 'null') return '未知'
  if (['success', 'ready', 'available', '1', 'true', 'finish', 'finished', 'completed', 'done'].includes(normalized)) return '成功'
  if (['failed', 'failure', 'error', '0', 'false'].includes(normalized)) return '失败'
  if (['running', 'embedding', 'pending', 'processing', 'queue', 'started'].includes(normalized)) return '处理中'
  return String(raw)
}

const StatusValue = ({ record }) => {
  const value = statusText(record)
  const isSuccess = value === '成功'
  const isFailed = value === '失败'
  return (
    <span className={`maxkb-status-value ${isSuccess ? 'is-success' : ''} ${isFailed ? 'is-error' : ''}`}>
      <CheckCircleFilled />
      {value}
    </span>
  )
}

function KnowledgeManage() {
  const navigate = useNavigate()
  const [accountSearchForm] = Form.useForm()
  const [accountForm] = Form.useForm()
  const [uploadForm] = Form.useForm()
  const [hitForm] = Form.useForm()

  const [environmentOptions, setEnvironmentOptions] = useState([])
  const [accounts, setAccounts] = useState([])
  const [accountLoading, setAccountLoading] = useState(false)
  const [accountPagination, setAccountPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedAccountId, setSelectedAccountId] = useState(null)
  const [accountDrawerOpen, setAccountDrawerOpen] = useState(false)
  const [accountModalOpen, setAccountModalOpen] = useState(false)
  const [editingAccount, setEditingAccount] = useState(null)
  const [savingAccount, setSavingAccount] = useState(false)
  const [testingAccountId, setTestingAccountId] = useState(null)

  const [knowledgeRows, setKnowledgeRows] = useState([])
  const [knowledgeLoading, setKnowledgeLoading] = useState(false)
  const [knowledgePagination, setKnowledgePagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const [knowledgeKeyword, setKnowledgeKeyword] = useState('')
  const [selectedKnowledge, setSelectedKnowledge] = useState(null)

  const [activeMenu, setActiveMenu] = useState('document')
  const [documentRows, setDocumentRows] = useState([])
  const [documentLoading, setDocumentLoading] = useState(false)
  const [documentPagination, setDocumentPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [documentSearchType, setDocumentSearchType] = useState('name')
  const [documentKeyword, setDocumentKeyword] = useState('')
  const [selectedDocumentKeys, setSelectedDocumentKeys] = useState([])

  const [uploadOpen, setUploadOpen] = useState(false)
  const [uploadFileList, setUploadFileList] = useState([])
  const [uploading, setUploading] = useState(false)

  const [hitOpen, setHitOpen] = useState(false)
  const [hitLoading, setHitLoading] = useState(false)
  const [hitRows, setHitRows] = useState([])

  const selectedAccount = useMemo(
    () => accounts.find((item) => item.id === selectedAccountId) || null,
    [accounts, selectedAccountId],
  )

  const environmentSelectOptions = useMemo(() => (
    environmentOptions.map((item) => ({ value: item.value, label: item.label }))
  ), [environmentOptions])

  const accountSelectOptions = useMemo(() => (
    accounts.map((item) => ({
      value: item.id,
      label: `${item.accountName} · ${item.environmentText || item.environment}`,
    }))
  ), [accounts])

  const knowledgeSelectOptions = useMemo(() => (
    knowledgeRows.map((item) => ({
      value: item.id,
      label: textValue(item.name, item.knowledge_name, item.title, item.id),
    }))
  ), [knowledgeRows])

  const fetchEnvironments = useCallback(async () => {
    try {
      const res = await getMaxKbEnvironments()
      setEnvironmentOptions(res.data || [])
    } catch (error) {
      message.error(error.message || '环境选项加载失败')
    }
  }, [])

  const fetchAccounts = useCallback(async (params = {}) => {
    const values = accountSearchForm.getFieldsValue()
    const current = params.current ?? accountPagination.current
    const size = params.pageSize ?? accountPagination.pageSize
    setAccountLoading(true)
    try {
      const res = await getMaxKbAccounts({
        current,
        size,
        keyword: values.keyword || undefined,
        environment: values.environment || undefined,
        status: values.status,
      })
      const data = res.data || {}
      const nextRows = data.records || []
      setAccounts(nextRows)
      setAccountPagination({
        current: data.page || current,
        pageSize: data.size || size,
        total: data.total || 0,
      })
      setSelectedAccountId((prev) => {
        if (prev && nextRows.some((item) => item.id === prev)) return prev
        return nextRows.find((item) => item.status === 1)?.id || nextRows[0]?.id || null
      })
    } catch (error) {
      message.error(error.message || 'MaxKB 账号加载失败')
    } finally {
      setAccountLoading(false)
    }
  }, [accountPagination.current, accountPagination.pageSize, accountSearchForm])

  const fetchDocuments = useCallback(async (knowledge, params = {}) => {
    const targetKnowledge = knowledge || selectedKnowledge
    if (!selectedAccountId || !targetKnowledge?.id) {
      setDocumentRows([])
      setDocumentPagination({ current: 1, pageSize: documentPagination.pageSize, total: 0 })
      return
    }
    const current = params.current ?? documentPagination.current
    const size = params.pageSize ?? documentPagination.pageSize
    const keyword = params.keyword ?? documentKeyword
    setDocumentLoading(true)
    try {
      const res = await getMaxKbDocuments(selectedAccountId, targetKnowledge.id, {
        page: current,
        page_size: size,
        task_type: 1,
        [documentSearchType]: keyword || undefined,
      })
      const data = normalizePage(res.data)
      setDocumentRows(data.records)
      setSelectedDocumentKeys([])
      setDocumentPagination({
        current: data.page || current,
        pageSize: data.size || size,
        total: data.total || 0,
      })
    } catch (error) {
      message.error(error.message || '文档列表加载失败')
    } finally {
      setDocumentLoading(false)
    }
  }, [documentKeyword, documentPagination.current, documentPagination.pageSize, documentSearchType, selectedAccountId, selectedKnowledge])

  const fetchKnowledges = useCallback(async (params = {}) => {
    if (!selectedAccountId) {
      setKnowledgeRows([])
      setSelectedKnowledge(null)
      setDocumentRows([])
      return
    }
    const current = params.current ?? knowledgePagination.current
    const size = params.pageSize ?? knowledgePagination.pageSize
    setKnowledgeLoading(true)
    try {
      const res = await getMaxKbKnowledges(selectedAccountId, {
        page: current,
        page_size: size,
        name: knowledgeKeyword || undefined,
      })
      const data = normalizePage(res.data)
      const nextRows = data.records
      const nextSelected = params.keepSelection
        ? nextRows.find((item) => item.id === selectedKnowledge?.id) || selectedKnowledge
        : nextRows.find((item) => item.id === selectedKnowledge?.id) || nextRows[0] || null
      setKnowledgeRows(nextRows)
      setKnowledgePagination({
        current: data.page || current,
        pageSize: data.size || size,
        total: data.total || 0,
      })
      setSelectedKnowledge(nextSelected)
      if (nextSelected) {
        fetchDocuments(nextSelected, { current: 1, pageSize: documentPagination.pageSize, keyword: documentKeyword })
      } else {
        setDocumentRows([])
      }
    } catch (error) {
      message.error(error.message || '知识库加载失败')
    } finally {
      setKnowledgeLoading(false)
    }
  }, [documentKeyword, documentPagination.pageSize, fetchDocuments, knowledgeKeyword, knowledgePagination.current, knowledgePagination.pageSize, selectedAccountId, selectedKnowledge])

  const openParagraphPage = (document) => {
    if (!selectedAccountId || !selectedKnowledge?.id || !document?.id) return
    const query = new URLSearchParams({
      from: 'workspace',
      isShared: 'false',
      accountId: String(selectedAccountId),
      documentName: textValue(document.name, document.title, document.file_name, document.id),
      knowledgeName: textValue(selectedKnowledge.name, selectedKnowledge.knowledge_name, selectedKnowledge.title, selectedKnowledge.id),
    })
    navigate(`/admin/paragraph/${selectedKnowledge.id}/${document.id}?${query.toString()}`)
  }

  useEffect(() => {
    fetchEnvironments()
    fetchAccounts({ current: 1 })
  }, [])

  useEffect(() => {
    setSelectedKnowledge(null)
    setDocumentRows([])
    setDocumentKeyword('')
    if (selectedAccountId) {
      fetchKnowledges({ current: 1 })
    }
  }, [selectedAccountId])

  const openCreateAccount = () => {
    setEditingAccount(null)
    accountForm.resetFields()
    accountForm.setFieldsValue({ environment: 'local', status: 1 })
    setAccountModalOpen(true)
  }

  const openEditAccount = (record) => {
    setEditingAccount(record)
    accountForm.setFieldsValue({
      accountName: record.accountName,
      environment: record.environment || 'local',
      baseUrl: record.baseUrl,
      workspaceId: record.workspaceId,
      apiKey: '',
      remark: record.remark,
      status: record.status,
    })
    setAccountModalOpen(true)
  }

  const saveAccount = async () => {
    const values = await accountForm.validateFields()
    setSavingAccount(true)
    try {
      if (editingAccount) {
        await updateMaxKbAccount(editingAccount.id, values)
        message.success('MaxKB 账号已更新')
      } else {
        await createMaxKbAccount(values)
        message.success('MaxKB 账号已创建')
      }
      setAccountModalOpen(false)
      fetchAccounts()
    } catch (error) {
      message.error(error.message || '账号保存失败')
    } finally {
      setSavingAccount(false)
    }
  }

  const handleDeleteAccount = async (record) => {
    try {
      await deleteMaxKbAccount(record.id)
      message.success('账号已删除')
      fetchAccounts({ current: 1 })
    } catch (error) {
      message.error(error.message || '账号删除失败')
    }
  }

  const handleAccountStatus = async (record, checked) => {
    try {
      await updateMaxKbAccountStatus(record.id, checked ? 1 : 0)
      message.success(checked ? '账号已启用' : '账号已禁用')
      fetchAccounts()
    } catch (error) {
      message.error(error.message || '状态更新失败')
    }
  }

  const handleTestAccount = async (record) => {
    setTestingAccountId(record.id)
    try {
      await testMaxKbAccount(record.id)
      message.success('连接成功，已验证该账号可访问 MaxKB')
    } catch (error) {
      message.error(error.message || '连接测试失败')
    } finally {
      setTestingAccountId(null)
    }
  }

  const selectKnowledge = (knowledgeId) => {
    const row = knowledgeRows.find((item) => item.id === knowledgeId)
    setSelectedKnowledge(row || null)
    setActiveMenu('document')
    setDocumentPagination((prev) => ({ ...prev, current: 1 }))
    fetchDocuments(row, { current: 1 })
  }

  const openUpload = () => {
    if (!selectedKnowledge?.id) {
      message.warning('请先选择知识库')
      return
    }
    uploadForm.setFieldsValue({ limit: 4096, splitStrategy: '', withFilter: false })
    setUploadFileList([])
    setUploadOpen(true)
  }

  const openHitTest = (knowledge) => {
    const targetKnowledge = knowledge || selectedKnowledge
    setHitOpen(true)
    setHitRows([])
    hitForm.setFieldsValue({
      knowledgeId: targetKnowledge?.id,
      queryText: '',
      topNumber: 5,
      similarity: 0.6,
      searchMode: 'blend',
    })
  }

  const runHitTest = async () => {
    if (!selectedAccountId) {
      message.warning('请先选择 MaxKB 账号')
      return
    }
    const values = await hitForm.validateFields()
    setHitLoading(true)
    try {
      const res = await runMaxKbHitTest(selectedAccountId, {
        knowledge_id: values.knowledgeId,
        query_text: values.queryText,
        top_number: values.topNumber,
        similarity: values.similarity,
        search_mode: values.searchMode,
      })
      setHitRows(normalizeRows(res.data))
      message.success('召回测试完成')
    } catch (error) {
      message.error(error.message || '召回测试失败')
    } finally {
      setHitLoading(false)
    }
  }

  const submitUpload = async () => {
    if (!selectedAccountId || !selectedKnowledge?.id) {
      message.warning('请先选择知识库')
      return
    }
    if (!uploadFileList.length) {
      message.warning('请选择要上传的文件')
      return
    }
    const values = await uploadForm.validateFields()
    const formData = new FormData()
    uploadFileList.forEach((item) => formData.append('file', item.originFileObj || item))
    formData.append('limit', values.limit || 4096)
    if (values.patterns) {
      values.patterns
        .split(/[\n,，]/)
        .map((item) => item.trim())
        .filter(Boolean)
        .forEach((pattern) => formData.append('patterns', pattern))
    }
    if (values.withFilter !== undefined) {
      formData.append('with_filter', String(values.withFilter))
    }
    if (values.splitStrategy) {
      formData.append('split_strategy', values.splitStrategy)
    }
    if (values.modelId) {
      formData.append('model_id', values.modelId)
    }

    setUploading(true)
    try {
      await uploadMaxKbDocuments(selectedAccountId, selectedKnowledge.id, formData)
      message.success('文档已提交 MaxKB 处理')
      setUploadOpen(false)
      setUploadFileList([])
      uploadForm.resetFields()
      fetchDocuments(selectedKnowledge, { current: 1, pageSize: documentPagination.pageSize })
    } catch (error) {
      message.error(error.message || '上传失败')
    } finally {
      setUploading(false)
    }
  }

  const unsupportedAction = (label) => {
    message.info(`${label} 需要 MaxKB 对应写入接口开放后才能执行`)
  }

  const accountColumns = useMemo(() => [
    {
      title: '账号',
      dataIndex: 'accountName',
      width: 190,
      render: (value, record) => (
        <Space direction="vertical" size={2}>
          <Button type="link" className="maxkb-link-button" onClick={() => setSelectedAccountId(record.id)}>
            {value}
          </Button>
          <Text type="secondary">{record.workspaceId}</Text>
        </Space>
      ),
    },
    {
      title: '环境',
      dataIndex: 'environment',
      width: 100,
      render: (value, record) => (
        <Tag color={environmentColors[value] || 'default'}>{record.environmentText || value}</Tag>
      ),
    },
    {
      title: '服务地址',
      dataIndex: 'baseUrl',
      ellipsis: true,
    },
    {
      title: 'Key',
      dataIndex: 'apiKeyMasked',
      width: 140,
      render: (value) => <Text code>{value || '未配置'}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value, record) => (
        <Switch
          size="small"
          checked={value === 1}
          checkedChildren="启用"
          unCheckedChildren="禁用"
          onChange={(checked) => handleAccountStatus(record, checked)}
        />
      ),
    },
    {
      title: '操作',
      width: 260,
      fixed: 'right',
      render: (_, record) => (
        <Space size={6} wrap>
          <Button size="small" icon={<ThunderboltOutlined />} loading={testingAccountId === record.id} onClick={() => handleTestAccount(record)}>
            测试
          </Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEditAccount(record)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该 MaxKB 账号吗？" onConfirm={() => handleDeleteAccount(record)}>
            <Button size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ], [testingAccountId])

  const documentColumns = useMemo(() => [
    {
      title: '文件名称',
      dataIndex: 'name',
      minWidth: 280,
      ellipsis: true,
      render: (value, record) => (
        <button className="maxkb-file-name" type="button" onClick={() => openParagraphPage(record)}>
          {textValue(value, record.title, record.file_name, record.id)}
        </button>
      ),
    },
    {
      title: <span>文件状态 <FilterOutlined className="maxkb-header-filter" /></span>,
      dataIndex: 'status',
      width: 120,
      render: (_, record) => <StatusValue record={record} />,
    },
    {
      title: '字符数',
      width: 120,
      align: 'right',
      sorter: true,
      render: (_, record) => compactNumber(record.char_length, record.charLength, record.character_count, 0),
    },
    {
      title: '分段',
      width: 120,
      align: 'right',
      sorter: true,
      render: (_, record) => textValue(record.paragraph_count, record.paragraphCount, record.paragraph_num, 0),
    },
    {
      title: <span>启用状态 <FilterOutlined className="maxkb-header-filter" /></span>,
      width: 110,
      render: (_, record) => (
        <span className={`maxkb-active-value ${record.is_active === false ? 'is-disabled' : ''}`}>
          {record.is_active === false ? <StopOutlined /> : <CheckCircleFilled />}
          {record.is_active === false ? '已禁用' : '已启用'}
        </span>
      ),
    },
    {
      title: <span>标签 <FilterOutlined className="maxkb-header-filter" /></span>,
      width: 150,
      render: (_, record) => (
        <Space size={6}>
          {record.tag_count ? (
            <Tag icon={<TagsOutlined />} color="default">{record.tag_count}</Tag>
          ) : null}
          <Button className="maxkb-tag-button" size="small" icon={<PlusOutlined />} onClick={(event) => {
            event.stopPropagation()
            unsupportedAction('标签')
          }}>
            标签
          </Button>
        </Space>
      ),
    },
    {
      title: <span>命中处理方式 <FilterOutlined className="maxkb-header-filter" /></span>,
      width: 165,
      render: (_, record) => {
        const value = textValue(record.hit_handling_method, record.hitHandlingMethod, 'optimization')
        return hitHandlingMethodText[value] || value
      },
    },
    {
      title: '创建者',
      width: 80,
      ellipsis: true,
      render: (_, record) => textValue(record.nick_name, record.creator, record.create_user, record.createUser, '系统'),
    },
    {
      title: '创建时间',
      width: 175,
      sorter: true,
      render: (_, record) => textValue(record.create_time, record.createTime, '-'),
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size={6} onClick={(event) => event.stopPropagation()}>
          <Switch
            size="small"
            checked={record.is_active !== false}
            onChange={() => unsupportedAction('启停文档')}
          />
          <Tooltip title="查看分段">
            <Button type="text" className="maxkb-icon-action" icon={<ClusterOutlined />} onClick={() => openParagraphPage(record)} />
          </Tooltip>
          <Tooltip title="分词索引">
            <Button type="text" className="maxkb-icon-action" icon={<ShareAltOutlined />} onClick={() => unsupportedAction('分词索引')} />
          </Tooltip>
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                { key: 'setting', icon: <SettingOutlined />, label: '设置', onClick: () => unsupportedAction('文档设置') },
                { key: 'generate', icon: <ThunderboltOutlined />, label: '生成问题', onClick: () => unsupportedAction('生成问题') },
                { key: 'export', icon: <ExportOutlined />, label: '导出 Excel', onClick: () => unsupportedAction('导出 Excel') },
                { key: 'download', icon: <DownloadOutlined />, label: '下载', onClick: () => unsupportedAction('下载') },
                { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true, onClick: () => unsupportedAction('删除文档') },
              ],
            }}
          >
            <Button type="text" className="maxkb-icon-action" icon={<MoreOutlined />} />
          </Dropdown>
        </Space>
      ),
    },
  ], [selectedAccountId, selectedKnowledge])

  const hitColumns = useMemo(() => [
    {
      title: '内容',
      dataIndex: 'content',
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Text>{textValue(value, record.text, record.title, record.problem_text)}</Text>
          <Text type="secondary">{textValue(record.document_name, record.documentName, record.knowledge_name, record.knowledgeName)}</Text>
        </Space>
      ),
    },
    {
      title: '相似度',
      width: 120,
      render: (_, record) => textValue(record.similarity, record.score, record.comprehensive_score),
    },
  ], [])

  const renderDocumentWorkspace = () => {
    if (!selectedAccount) {
      return (
        <div className="maxkb-empty-stage">
          <Empty description="请先在系统管理中添加 MaxKB 账号" />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateAccount}>新增账号</Button>
        </div>
      )
    }
    if (!selectedKnowledge) {
      return (
        <div className="maxkb-empty-stage">
          <Empty description="当前账号暂无可访问知识库" />
          <Space>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              value={knowledgeKeyword}
              placeholder="按名称搜索知识库"
              onChange={(event) => setKnowledgeKeyword(event.target.value)}
              onPressEnter={() => fetchKnowledges({ current: 1 })}
            />
            <Button icon={<ReloadOutlined />} onClick={() => fetchKnowledges({ current: 1 })}>刷新</Button>
          </Space>
        </div>
      )
    }

    return (
      <>
        <div className="maxkb-content-title">
          <Title level={2}>文档</Title>
        </div>
        <div className="maxkb-document-card">
          <div className="maxkb-document-toolbar">
            <Space size={12} wrap>
              <Button type="primary" icon={<UploadOutlined />} onClick={openUpload}>上传文档</Button>
              <Button disabled={!selectedDocumentKeys.length} onClick={() => unsupportedAction('向量化')}>向量化</Button>
              <Button disabled={!selectedDocumentKeys.length} onClick={() => unsupportedAction('分词索引')}>分词索引</Button>
              <Button disabled={!selectedDocumentKeys.length} onClick={() => unsupportedAction('生成问题')}>生成问题</Button>
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [
                    { key: 'setting', label: '设置', disabled: !selectedDocumentKeys.length, onClick: () => unsupportedAction('批量设置') },
                    { key: 'move', label: '迁移', disabled: !selectedDocumentKeys.length, onClick: () => unsupportedAction('文档迁移') },
                    { key: 'tag', label: '添加标签', disabled: !selectedDocumentKeys.length, onClick: () => unsupportedAction('添加标签') },
                    { type: 'divider' },
                    { key: 'export-excel', label: '导出 Excel', disabled: !selectedDocumentKeys.length, onClick: () => unsupportedAction('导出 Excel') },
                    { key: 'export-zip', label: '导出 Zip', disabled: !selectedDocumentKeys.length, onClick: () => unsupportedAction('导出 Zip') },
                    { type: 'divider' },
                    { key: 'delete', label: '删除', danger: true, disabled: !selectedDocumentKeys.length, onClick: () => unsupportedAction('批量删除') },
                  ],
                }}
              >
                <Button icon={<MoreOutlined />} />
              </Dropdown>
            </Space>

            <Space className="maxkb-search-cluster" size={12} wrap>
              <Input.Group compact className="maxkb-complex-search">
                <Select
                  value={documentSearchType}
                  onChange={setDocumentSearchType}
                  options={[
                    { value: 'name', label: '名称' },
                    { value: 'create_user', label: '创建者' },
                  ]}
                />
                <Input
                  allowClear
                  value={documentKeyword}
                  placeholder={documentSearchType === 'name' ? '按名称搜索' : '按创建者搜索'}
                  onChange={(event) => setDocumentKeyword(event.target.value)}
                  onPressEnter={() => fetchDocuments(selectedKnowledge, { current: 1, keyword: documentKeyword })}
                />
              </Input.Group>
              <Button onClick={() => fetchDocuments(selectedKnowledge, { current: 1, keyword: documentKeyword })}>搜索</Button>
              <Button onClick={() => unsupportedAction('标签管理')}>标签管理</Button>
            </Space>
          </div>

          <Table
            rowKey={recordKey}
            className="maxkb-document-table"
            columns={documentColumns}
            dataSource={documentRows}
            loading={documentLoading}
            bordered
            rowSelection={{
              selectedRowKeys: selectedDocumentKeys,
              onChange: setSelectedDocumentKeys,
            }}
            locale={{ emptyText: <Empty description="暂无文档" /> }}
            pagination={{
              current: documentPagination.current,
              pageSize: documentPagination.pageSize,
              total: documentPagination.total,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
            }}
            scroll={{ x: 1360 }}
            onRow={(record) => ({ onDoubleClick: () => openParagraphPage(record) })}
            onChange={(nextPagination) => {
              fetchDocuments(selectedKnowledge, {
                current: nextPagination.current,
                pageSize: nextPagination.pageSize,
                keyword: documentKeyword,
              })
            }}
          />
        </div>

        {selectedDocumentKeys.length ? (
          <div className="maxkb-selection-bar">
            <Button onClick={() => unsupportedAction('取消向量化')}>取消向量化</Button>
            <Button onClick={() => unsupportedAction('取消生成')}>取消生成</Button>
            <Text type="secondary">已选择 {selectedDocumentKeys.length} 个文档</Text>
            <Button type="link" onClick={() => setSelectedDocumentKeys([])}>清空</Button>
          </div>
        ) : null}
      </>
    )
  }

  const renderSecondaryPanel = () => {
    if (activeMenu === 'hit') {
      return (
        <>
          <div className="maxkb-content-title">
            <Title level={2}>召回测试</Title>
          </div>
          <div className="maxkb-placeholder-card">
            <Form form={hitForm} layout="vertical">
              <Form.Item name="knowledgeId" label="知识库" rules={[{ required: true, message: '请选择知识库' }]}>
                <Select showSearch optionFilterProp="label" options={knowledgeSelectOptions} placeholder="选择当前账号可访问的知识库" />
              </Form.Item>
              <Form.Item name="queryText" label="测试问题" rules={[{ required: true, message: '请输入测试问题' }]}>
                <TextArea rows={3} placeholder="输入要检索的问题" />
              </Form.Item>
              <div className="maxkb-hit-grid">
                <Form.Item name="topNumber" label="返回数量">
                  <InputNumber min={1} max={20} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item name="similarity" label="相似度阈值">
                  <InputNumber min={0} max={1} step={0.05} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item name="searchMode" label="检索模式">
                  <Select options={searchModeOptions} />
                </Form.Item>
              </div>
              <Button type="primary" loading={hitLoading} icon={<ThunderboltOutlined />} onClick={runHitTest}>运行测试</Button>
            </Form>
            <Table
              rowKey={recordKey}
              className="maxkb-hit-table"
              columns={hitColumns}
              dataSource={hitRows}
              loading={hitLoading}
              locale={{ emptyText: <Empty description="运行后显示召回结果" /> }}
              pagination={{ pageSize: 5 }}
            />
          </div>
        </>
      )
    }

    const item = menuItems.find((menu) => menu.key === activeMenu)
    return (
      <>
        <div className="maxkb-content-title">
          <Title level={2}>{item?.label}</Title>
        </div>
        <div className="maxkb-placeholder-card">
          <Empty description={`${item?.label || '该模块'}展示入口已按 MaxKB 保留，等后续接入对应 OpenAPI 后可继续补齐。`} />
        </div>
      </>
    )
  }

  return (
    <div className="knowledge-maxkb-page">
      <header className="maxkb-topbar">
        <div className="maxkb-brand">
          <div className="maxkb-brand-mark">Z</div>
          <div>
            <strong>流光知识库</strong>
          </div>
        </div>
        <nav className="maxkb-topnav">
          <Button type="primary" icon={<BookOutlined />}>知识库</Button>
          <Button type="text" icon={<CommentOutlined />} onClick={() => openHitTest()}>聊天测试</Button>
          <Button type="text" icon={<AppstoreOutlined />} onClick={() => unsupportedAction('模型')}>模型</Button>
          <Button type="text" icon={<SettingOutlined />} onClick={() => setAccountDrawerOpen(true)}>系统管理</Button>
          <Avatar className="maxkb-avatar" icon={<UserOutlined />} />
        </nav>
      </header>

      <div className="maxkb-workbench">
        <aside className="maxkb-inner-sidebar">
          <div className="maxkb-knowledge-head">
            <div className="maxkb-knowledge-title">
              <Button type="text" icon={<ArrowLeftOutlined />} className="maxkb-back-button" onClick={() => setAccountDrawerOpen(true)} />
              <span className="maxkb-knowledge-icon"><FileTextOutlined /></span>
              <Select
                value={selectedKnowledge?.id}
                placeholder="选择知识库"
                loading={knowledgeLoading}
                options={knowledgeSelectOptions}
                onChange={selectKnowledge}
                className="maxkb-knowledge-select"
                bordered={false}
              />
            </div>
            <Select
              value={selectedAccountId}
              placeholder="选择 MaxKB 账号"
              options={accountSelectOptions}
              onChange={setSelectedAccountId}
              className="maxkb-account-select"
            />
          </div>

          <div className="maxkb-side-search">
            <Input
              allowClear
              prefix={<SearchOutlined />}
              value={knowledgeKeyword}
              placeholder="搜索知识库"
              onChange={(event) => setKnowledgeKeyword(event.target.value)}
              onPressEnter={() => fetchKnowledges({ current: 1 })}
            />
            <Button icon={<ReloadOutlined />} onClick={() => fetchKnowledges({ current: 1 })} />
          </div>

          <nav className="maxkb-menu-list">
            {menuItems.map((item) => (
              <button
                key={item.key}
                type="button"
                className={`maxkb-menu-item ${activeMenu === item.key ? 'is-active' : ''}`}
                onClick={() => {
                  setActiveMenu(item.key)
                  if (item.key === 'hit') {
                    hitForm.setFieldsValue({
                      knowledgeId: selectedKnowledge?.id,
                      queryText: '',
                      topNumber: 5,
                      similarity: 0.6,
                      searchMode: 'blend',
                    })
                  }
                }}
              >
                {item.icon}
                <span>{item.label}</span>
              </button>
            ))}
          </nav>
        </aside>

        <main className="maxkb-main-panel">
          {activeMenu === 'document' ? renderDocumentWorkspace() : renderSecondaryPanel()}
        </main>
      </div>

      <Drawer
        title="系统管理"
        width={980}
        open={accountDrawerOpen}
        onClose={() => setAccountDrawerOpen(false)}
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={openCreateAccount}>新增账号</Button>}
      >
        <Form
          form={accountSearchForm}
          className="maxkb-account-filter"
          layout="inline"
          onFinish={() => fetchAccounts({ current: 1 })}
        >
          <Form.Item name="keyword">
            <Input allowClear prefix={<SearchOutlined />} placeholder="搜索账号、URL、工作空间" />
          </Form.Item>
          <Form.Item name="environment">
            <Select allowClear placeholder="环境" options={environmentSelectOptions} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="status">
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 120 }}
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '禁用' },
              ]}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button>
              <Button onClick={() => {
                accountSearchForm.resetFields()
                fetchAccounts({ current: 1 })
              }}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <Table
          rowKey="id"
          columns={accountColumns}
          dataSource={accounts}
          loading={accountLoading}
          rowClassName={(record) => record.id === selectedAccountId ? 'maxkb-selected-row' : ''}
          locale={{ emptyText: <Empty description="暂无 MaxKB 账号" /> }}
          pagination={{
            current: accountPagination.current,
            pageSize: accountPagination.pageSize,
            total: accountPagination.total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 个账号`,
          }}
          scroll={{ x: 980 }}
          onRow={(record) => ({ onClick: () => setSelectedAccountId(record.id) })}
          onChange={(nextPagination) => {
            fetchAccounts({
              current: nextPagination.current,
              pageSize: nextPagination.pageSize,
            })
          }}
        />
      </Drawer>

      <Modal
        title={editingAccount ? '编辑 MaxKB 账号' : '新增 MaxKB 账号'}
        open={accountModalOpen}
        onCancel={() => setAccountModalOpen(false)}
        onOk={saveAccount}
        confirmLoading={savingAccount}
        width={680}
        destroyOnHidden
      >
        <Form form={accountForm} layout="vertical" className="maxkb-account-form">
          <Form.Item name="accountName" label="账号名称" rules={[{ required: true, message: '请输入账号名称' }]}>
            <Input placeholder="例如：测试环境-教务知识库" />
          </Form.Item>
          <Form.Item name="environment" label="环境" rules={[{ required: true, message: '请选择环境' }]}>
            <Select options={environmentSelectOptions} />
          </Form.Item>
          <Form.Item name="baseUrl" label="MaxKB URL" rules={[{ required: true, message: '请输入 MaxKB 服务地址' }]}>
            <Input addonBefore={<ApiOutlined />} placeholder="例如：http://localhost:8080 或 https://maxkb.example.com" />
          </Form.Item>
          <Form.Item name="workspaceId" label="工作空间 ID" rules={[{ required: true, message: '请输入工作空间 ID' }]}>
            <Input placeholder="MaxKB OpenAPI Key 对应的 workspace_id" />
          </Form.Item>
          <Form.Item
            name="apiKey"
            label="OpenAPI Key"
            rules={editingAccount ? [] : [{ required: true, message: '请输入 OpenAPI Key' }]}
            extra={editingAccount ? '编辑时不填写会保留原 Key。' : null}
          >
            <Input.Password placeholder="mkb_..." />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <TextArea rows={2} placeholder="用途、权限范围或负责人" />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
            <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="上传文档"
        open={uploadOpen}
        onCancel={() => setUploadOpen(false)}
        onOk={submitUpload}
        confirmLoading={uploading}
        width={720}
        destroyOnHidden
      >
        <Form form={uploadForm} layout="vertical">
          <Upload.Dragger
            multiple
            fileList={uploadFileList}
            beforeUpload={() => false}
            onChange={({ fileList }) => setUploadFileList(fileList)}
          >
            <p className="ant-upload-drag-icon"><CloudUploadOutlined /></p>
            <p className="ant-upload-text">选择或拖入要入库的文档</p>
          </Upload.Dragger>
          <div className="maxkb-upload-grid">
            <Form.Item name="limit" label="分段长度">
              <InputNumber min={200} max={20000} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="splitStrategy" label="分段策略">
              <Select options={splitStrategyOptions} />
            </Form.Item>
            <Form.Item name="withFilter" label="过滤处理">
              <Select options={[{ value: false, label: '关闭' }, { value: true, label: '开启' }]} />
            </Form.Item>
            <Form.Item name="modelId" label="模型 ID">
              <Input placeholder="使用大模型分段时填写" />
            </Form.Item>
          </div>
          <Form.Item name="patterns" label="自定义分隔符">
            <TextArea rows={2} placeholder="一行一个，或用逗号分隔" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="知识库召回测试"
        width={860}
        open={hitOpen}
        onClose={() => setHitOpen(false)}
        extra={<Button type="primary" loading={hitLoading} icon={<ThunderboltOutlined />} onClick={runHitTest}>运行测试</Button>}
      >
        <Form form={hitForm} layout="vertical">
          <Form.Item name="knowledgeId" label="知识库" rules={[{ required: true, message: '请选择知识库' }]}>
            <Select showSearch optionFilterProp="label" options={knowledgeSelectOptions} placeholder="选择当前账号可访问的知识库" />
          </Form.Item>
          <Form.Item name="queryText" label="测试问题" rules={[{ required: true, message: '请输入测试问题' }]}>
            <TextArea rows={3} placeholder="输入要检索的问题" />
          </Form.Item>
          <div className="maxkb-hit-grid">
            <Form.Item name="topNumber" label="返回数量">
              <InputNumber min={1} max={20} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="similarity" label="相似度阈值">
              <InputNumber min={0} max={1} step={0.05} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="searchMode" label="检索模式">
              <Select options={searchModeOptions} />
            </Form.Item>
          </div>
        </Form>

        <Table
          rowKey={recordKey}
          className="maxkb-hit-table"
          columns={hitColumns}
          dataSource={hitRows}
          loading={hitLoading}
          locale={{ emptyText: <Empty description="运行后显示召回结果" /> }}
          pagination={{ pageSize: 5 }}
        />

        {hitRows.length ? (
          <Descriptions className="maxkb-hit-json" column={1} size="small" bordered>
            <Descriptions.Item label="原始结果">
              <pre>{JSON.stringify(hitRows, null, 2)}</pre>
            </Descriptions.Item>
          </Descriptions>
        ) : null}
      </Drawer>
    </div>
  )
}

export default KnowledgeManage
