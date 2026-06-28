/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
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
  Typography,
  Upload,
  message,
} from 'antd'
import {
  ApiOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  EditOutlined,
  FileSearchOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import {
  createMaxKbAccount,
  deleteMaxKbAccount,
  getMaxKbAccounts,
  getMaxKbDocuments,
  getMaxKbEnvironments,
  getMaxKbKnowledges,
  getMaxKbParagraphs,
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

const statusTag = (value) => {
  const normalized = String(value ?? '').toLowerCase()
  if (!normalized || normalized === 'null') return <Tag>未知</Tag>
  if (['success', 'ready', 'available', '1', 'true', 'finish', 'completed'].includes(normalized)) {
    return <Tag color="green">{String(value)}</Tag>
  }
  if (['failed', 'error', '0', 'false'].includes(normalized)) {
    return <Tag color="red">{String(value)}</Tag>
  }
  if (['running', 'embedding', 'pending', 'processing', 'queue'].includes(normalized)) {
    return <Tag color="gold">{String(value)}</Tag>
  }
  return <Tag color="blue">{String(value)}</Tag>
}

function KnowledgeManage() {
  const [accountSearchForm] = Form.useForm()
  const [accountForm] = Form.useForm()
  const [knowledgeForm] = Form.useForm()
  const [uploadForm] = Form.useForm()
  const [hitForm] = Form.useForm()

  const [environmentOptions, setEnvironmentOptions] = useState([])
  const [accounts, setAccounts] = useState([])
  const [accountLoading, setAccountLoading] = useState(false)
  const [accountPagination, setAccountPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedAccountId, setSelectedAccountId] = useState(null)
  const [accountModalOpen, setAccountModalOpen] = useState(false)
  const [editingAccount, setEditingAccount] = useState(null)
  const [savingAccount, setSavingAccount] = useState(false)
  const [testingAccountId, setTestingAccountId] = useState(null)

  const [knowledgeRows, setKnowledgeRows] = useState([])
  const [knowledgeLoading, setKnowledgeLoading] = useState(false)
  const [knowledgePagination, setKnowledgePagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedKnowledge, setSelectedKnowledge] = useState(null)

  const [documentsOpen, setDocumentsOpen] = useState(false)
  const [documentRows, setDocumentRows] = useState([])
  const [documentLoading, setDocumentLoading] = useState(false)
  const [documentPagination, setDocumentPagination] = useState({ current: 1, pageSize: 10, total: 0 })

  const [paragraphOpen, setParagraphOpen] = useState(false)
  const [selectedDocument, setSelectedDocument] = useState(null)
  const [paragraphRows, setParagraphRows] = useState([])
  const [paragraphLoading, setParagraphLoading] = useState(false)

  const [uploadOpen, setUploadOpen] = useState(false)
  const [uploadFileList, setUploadFileList] = useState([])
  const [uploading, setUploading] = useState(false)

  const [hitOpen, setHitOpen] = useState(false)
  const [hitLoading, setHitLoading] = useState(false)
  const [hitRows, setHitRows] = useState([])

  const selectedAccount = useMemo(
    () => accounts.find((item) => item.id === selectedAccountId) || null,
    [accounts, selectedAccountId]
  )

  const environmentSelectOptions = useMemo(() => (
    environmentOptions.map((item) => ({ value: item.value, label: item.label }))
  ), [environmentOptions])

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

  const fetchKnowledges = useCallback(async (params = {}) => {
    if (!selectedAccountId) {
      setKnowledgeRows([])
      setKnowledgePagination({ current: 1, pageSize: 10, total: 0 })
      return
    }
    const values = knowledgeForm.getFieldsValue()
    const current = params.current ?? knowledgePagination.current
    const size = params.pageSize ?? knowledgePagination.pageSize
    setKnowledgeLoading(true)
    try {
      const res = await getMaxKbKnowledges(selectedAccountId, {
        page: current,
        page_size: size,
        name: values.name || undefined,
      })
      const data = normalizePage(res.data)
      setKnowledgeRows(data.records)
      setKnowledgePagination({
        current: data.page || current,
        pageSize: data.size || size,
        total: data.total || 0,
      })
    } catch (error) {
      message.error(error.message || '知识库加载失败')
    } finally {
      setKnowledgeLoading(false)
    }
  }, [knowledgeForm, knowledgePagination.current, knowledgePagination.pageSize, selectedAccountId])

  const fetchDocuments = useCallback(async (knowledge, params = {}) => {
    const targetKnowledge = knowledge || selectedKnowledge
    if (!selectedAccountId || !targetKnowledge?.id) return
    const current = params.current ?? documentPagination.current
    const size = params.pageSize ?? documentPagination.pageSize
    setDocumentLoading(true)
    try {
      const res = await getMaxKbDocuments(selectedAccountId, targetKnowledge.id, {
        page: current,
        page_size: size,
        name: params.name,
      })
      const data = normalizePage(res.data)
      setDocumentRows(data.records)
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
  }, [documentPagination.current, documentPagination.pageSize, selectedAccountId, selectedKnowledge])

  const fetchParagraphs = async (document) => {
    if (!selectedAccountId || !selectedKnowledge?.id || !document?.id) return
    setSelectedDocument(document)
    setParagraphOpen(true)
    setParagraphLoading(true)
    try {
      const res = await getMaxKbParagraphs(selectedAccountId, selectedKnowledge.id, document.id, {
        page: 1,
        page_size: 50,
      })
      setParagraphRows(normalizePage(res.data).records)
    } catch (error) {
      message.error(error.message || '分段加载失败')
    } finally {
      setParagraphLoading(false)
    }
  }

  useEffect(() => {
    fetchEnvironments()
    fetchAccounts({ current: 1 })
  }, [])

  useEffect(() => {
    knowledgeForm.resetFields()
    setSelectedKnowledge(null)
    setDocumentsOpen(false)
    fetchKnowledges({ current: 1 })
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

  const openDocuments = (record) => {
    setSelectedKnowledge(record)
    setDocumentsOpen(true)
    setDocumentRows([])
    setDocumentPagination({ current: 1, pageSize: 10, total: 0 })
    fetchDocuments(record, { current: 1, pageSize: 10 })
  }

  const openHitTest = (knowledge) => {
    setHitOpen(true)
    setHitRows([])
    hitForm.setFieldsValue({
      knowledgeId: knowledge?.id,
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
      message.warning('请先打开一个知识库')
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

  const accountColumns = useMemo(() => [
    {
      title: '账号',
      dataIndex: 'accountName',
      width: 190,
      render: (value, record) => (
        <Space direction="vertical" size={2}>
          <Button type="link" className="knowledge-link-button" onClick={() => setSelectedAccountId(record.id)}>
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

  const knowledgeColumns = useMemo(() => [
    {
      title: '知识库',
      dataIndex: 'name',
      render: (value, record) => (
        <Space direction="vertical" size={2}>
          <Text strong>{textValue(value, record.knowledge_name, record.title, record.id)}</Text>
          <Text type="secondary">{record.id}</Text>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 110,
      render: (value) => <Tag color="blue">{textValue(value)}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: statusTag,
    },
    {
      title: '文档',
      width: 100,
      render: (_, record) => textValue(record.document_count, record.documentCount, record.document_num, 0),
    },
    {
      title: '创建人',
      width: 140,
      render: (_, record) => textValue(record.create_user, record.createUser, record.username),
    },
    {
      title: '更新时间',
      width: 170,
      render: (_, record) => textValue(record.update_time, record.updateTime, record.create_time, record.createTime),
    },
    {
      title: '操作',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size={6}>
          <Button size="small" icon={<FileSearchOutlined />} onClick={() => openDocuments(record)}>
            文档
          </Button>
          <Button size="small" icon={<ThunderboltOutlined />} onClick={() => openHitTest(record)}>
            召回
          </Button>
        </Space>
      ),
    },
  ], [selectedAccountId])

  const documentColumns = useMemo(() => [
    {
      title: '文档',
      dataIndex: 'name',
      render: (value, record) => (
        <Space direction="vertical" size={2}>
          <Text strong>{textValue(value, record.title, record.file_name, record.id)}</Text>
          <Text type="secondary">{record.id}</Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: statusTag,
    },
    {
      title: '字符数',
      width: 100,
      render: (_, record) => textValue(record.char_length, record.charLength, record.character_count, 0),
    },
    {
      title: '分段数',
      width: 100,
      render: (_, record) => textValue(record.paragraph_count, record.paragraphCount, record.paragraph_num, 0),
    },
    {
      title: '更新时间',
      width: 170,
      render: (_, record) => textValue(record.update_time, record.updateTime, record.create_time, record.createTime),
    },
    {
      title: '操作',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Button size="small" icon={<FileSearchOutlined />} onClick={() => fetchParagraphs(record)}>
          分段
        </Button>
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

  return (
    <div className="knowledge-manage-page">
      <section className="knowledge-manage-header">
        <div>
          <span className="knowledge-manage-kicker">MAXKB</span>
          <Title level={1}>知识库管理</Title>
          <p>维护不同环境和账号的 MaxKB 接入信息，并按账号权限管理知识库内容。</p>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => {
            fetchAccounts()
            fetchKnowledges()
          }}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateAccount}>
            新增账号
          </Button>
        </Space>
      </section>

      <Card className="knowledge-manage-card" title="MaxKB 账号">
        <Form
          form={accountSearchForm}
          className="knowledge-manage-filter"
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
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
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
          rowClassName={(record) => record.id === selectedAccountId ? 'knowledge-selected-row' : ''}
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
      </Card>

      <Card
        className="knowledge-manage-card"
        title="知识库"
        extra={selectedAccount ? (
          <Space wrap>
            <Tag color={environmentColors[selectedAccount.environment] || 'default'}>
              {selectedAccount.environmentText || selectedAccount.environment}
            </Tag>
            <Text type="secondary">{selectedAccount.accountName}</Text>
          </Space>
        ) : null}
      >
        {!selectedAccount ? (
          <Empty description="请先新增或选择一个 MaxKB 账号" />
        ) : (
          <>
            <Alert
              className="knowledge-manage-alert"
              type="info"
              showIcon
              message={`当前使用 ${selectedAccount.accountName} 的 ${selectedAccount.baseUrl}`}
            />
            <Form
              form={knowledgeForm}
              className="knowledge-manage-filter"
              layout="inline"
              onFinish={() => fetchKnowledges({ current: 1 })}
            >
              <Form.Item name="name">
                <Input allowClear prefix={<SearchOutlined />} placeholder="搜索知识库名称" />
              </Form.Item>
              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                    查询
                  </Button>
                  <Button onClick={() => {
                    knowledgeForm.resetFields()
                    fetchKnowledges({ current: 1 })
                  }}>
                    重置
                  </Button>
                  <Button icon={<ThunderboltOutlined />} onClick={() => openHitTest()}>
                    召回测试
                  </Button>
                </Space>
              </Form.Item>
            </Form>

            <Table
              rowKey="id"
              columns={knowledgeColumns}
              dataSource={knowledgeRows}
              loading={knowledgeLoading}
              locale={{ emptyText: <Empty description="该账号暂无可访问知识库" /> }}
              pagination={{
                current: knowledgePagination.current,
                pageSize: knowledgePagination.pageSize,
                total: knowledgePagination.total,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 个知识库`,
              }}
              scroll={{ x: 980 }}
              onChange={(nextPagination) => {
                fetchKnowledges({
                  current: nextPagination.current,
                  pageSize: nextPagination.pageSize,
                })
              }}
            />
          </>
        )}
      </Card>

      <Modal
        title={editingAccount ? '编辑 MaxKB 账号' : '新增 MaxKB 账号'}
        open={accountModalOpen}
        onCancel={() => setAccountModalOpen(false)}
        onOk={saveAccount}
        confirmLoading={savingAccount}
        width={680}
        destroyOnHidden
      >
        <Form form={accountForm} layout="vertical" className="knowledge-account-form">
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

      <Drawer
        title={selectedKnowledge ? `文档：${textValue(selectedKnowledge.name, selectedKnowledge.title, selectedKnowledge.id)}` : '知识库文档'}
        width={980}
        open={documentsOpen}
        onClose={() => setDocumentsOpen(false)}
        extra={(
          <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => {
            uploadForm.setFieldsValue({ limit: 4096, splitStrategy: '', withFilter: false })
            setUploadFileList([])
            setUploadOpen(true)
          }}>
            上传文档
          </Button>
        )}
      >
        <Table
          rowKey="id"
          columns={documentColumns}
          dataSource={documentRows}
          loading={documentLoading}
          locale={{ emptyText: <Empty description="暂无文档" /> }}
          pagination={{
            current: documentPagination.current,
            pageSize: documentPagination.pageSize,
            total: documentPagination.total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 个文档`,
          }}
          scroll={{ x: 860 }}
          onChange={(nextPagination) => {
            fetchDocuments(selectedKnowledge, {
              current: nextPagination.current,
              pageSize: nextPagination.pageSize,
            })
          }}
        />
      </Drawer>

      <Drawer
        title={selectedDocument ? `分段：${textValue(selectedDocument.name, selectedDocument.title, selectedDocument.id)}` : '文档分段'}
        width={860}
        open={paragraphOpen}
        onClose={() => setParagraphOpen(false)}
      >
        <Table
          rowKey={(record) => record.id || record.paragraph_id || JSON.stringify(record).slice(0, 80)}
          loading={paragraphLoading}
          dataSource={paragraphRows}
          locale={{ emptyText: <Empty description="暂无分段" /> }}
          pagination={{ pageSize: 10 }}
          columns={[
            {
              title: '标题',
              width: 180,
              render: (_, record) => textValue(record.title, record.name, record.id),
            },
            {
              title: '内容',
              render: (_, record) => (
                <div className="knowledge-paragraph-content">
                  {textValue(record.content, record.text)}
                </div>
              ),
            },
            {
              title: '状态',
              width: 110,
              render: (_, record) => statusTag(record.status),
            },
          ]}
        />
      </Drawer>

      <Modal
        title="上传文档到 MaxKB"
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
          <div className="knowledge-upload-grid">
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
          <div className="knowledge-hit-grid">
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
          rowKey={(record) => record.id || record.paragraph_id || JSON.stringify(record).slice(0, 80)}
          className="knowledge-hit-table"
          columns={hitColumns}
          dataSource={hitRows}
          loading={hitLoading}
          locale={{ emptyText: <Empty description="运行后显示召回结果" /> }}
          pagination={{ pageSize: 5 }}
        />

        {hitRows.length ? (
          <Descriptions className="knowledge-hit-json" column={1} size="small" bordered>
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
