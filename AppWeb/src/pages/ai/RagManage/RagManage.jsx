import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Col, Collapse, Empty, Form, Input, Row, Select, Space, Statistic, Table, Tabs, Tag, Typography, Upload, message } from 'antd'
import { ApiOutlined, BranchesOutlined, DatabaseOutlined, DownloadOutlined, ExperimentOutlined, FileTextOutlined, PlayCircleOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import ReactMarkdown from 'react-markdown'
import {
  convertPdf,
  evaluateRag,
  getRagAgents,
  executeTextToSql,
  getRagCapabilities,
  getRagDocuments,
  getRagEmbeddingHealth,
  getRagFramework,
  getRagGraphStoreHealth,
  getRagStrategies,
  getRagVectorStoreHealth,
  getTextToSqlSchema,
  ingestRagDocuments,
  runRagQuery,
} from '../../../api/rag'
import { getSystemConfigList } from '../../../api/systemConfig'
import './RagManage.css'

const { TextArea } = Input
const { Text, Title } = Typography
const TEXT_MODEL_CONFIG_PATTERN = /^ai\.service\.text(?:\.([A-Za-z0-9_-]+))?\.(provider|base-url|api-key|model)$/

const strategyColumns = [
  { title: '执行策略', dataIndex: 'name', render: (value, record) => <Tag color="blue">{record.label || value}</Tag> },
  { title: '策略标识', dataIndex: 'name', render: (value) => <Text code>{value}</Text> },
  { title: '分类', dataIndex: 'categoryLabel', render: (value, record) => <Tag>{value || record.category}</Tag> },
  { title: '用途', dataIndex: 'purpose' },
  { title: '状态', dataIndex: 'status', render: (value) => <Tag color={value === 'implemented' ? 'green' : 'orange'}>{value}</Tag> },
]

const documentColumns = [
  { title: '来源', dataIndex: 'source', ellipsis: true },
  { title: '大小', dataIndex: 'size', width: 120 },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    width: 180,
    render: (value) => (value ? new Date(Number(value) * 1000).toLocaleString() : '-'),
  },
]

const evidenceColumns = [
  { title: '来源', dataIndex: 'source', ellipsis: true },
  { title: '分数', dataIndex: 'score', width: 120, render: (value) => (value === null || value === undefined ? '-' : Number(value).toFixed(4)) },
  { title: '内容', dataIndex: 'content', ellipsis: true },
]

const metricColumns = [
  { title: '指标', dataIndex: 'name' },
  { title: '得分', dataIndex: 'value', render: (value) => Number(value).toFixed(4) },
]

const agentColumns = [
  { title: '智能体', dataIndex: 'name', render: (value) => <Tag color="geekblue">{value}</Tag> },
  { title: '角色', dataIndex: 'role' },
  { title: '职责', dataIndex: 'purpose' },
  {
    title: '执行方式',
    dataIndex: 'executionModeLabel',
    render: (value, record) => <Tag color={record.needRetrieval ? 'cyan' : 'gold'}>{value || (record.needRetrieval ? 'RAG + 智能体' : '不使用 RAG')}</Tag>,
  },
  {
    title: '默认策略',
    dataIndex: 'defaultRagStrategy',
    render: (value, record) => record.needRetrieval ? <Tag color="cyan">{value}</Tag> : <Tag>不使用 RAG</Tag>,
  },
  {
    title: '技能',
    dataIndex: 'skills',
    render: (value = []) => (
      <Space wrap>
        {value.map((item) => <Tag key={item}>{item}</Tag>)}
      </Space>
    ),
  },
]

const coverageColumns = [
  { title: '功能', dataIndex: 'name', render: (value) => <Tag color="blue">{value}</Tag> },
  { title: '分类', dataIndex: 'category' },
  { title: '用途', dataIndex: 'purpose' },
  { title: '状态', dataIndex: 'status', render: (value) => <Tag color="green">{value}</Tag> },
]

const providerColumns = [
  { title: '名称', dataIndex: 'name', width: 150, render: (value) => <Tag>{value}</Tag> },
  { title: '状态', dataIndex: 'status', width: 150, render: (value) => <Tag color={value === 'implemented' ? 'green' : 'orange'}>{value}</Tag> },
  {
    title: '依赖环境变量',
    dataIndex: 'requiredEnv',
    render: (value = []) => value.length ? (
      <Space size={[4, 4]} wrap>
        {value.map((item) => <Text code key={item}>{item}</Text>)}
      </Space>
    ) : '-',
  },
]

const envColumns = [
  { title: '环境变量', dataIndex: 'name' },
  { title: '默认值', dataIndex: 'default' },
  { title: '已配置', dataIndex: 'configured', render: (value) => <Tag color={value ? 'green' : 'default'}>{String(value)}</Tag> },
]

const toList = (value) => String(value || '')
  .split(/[,，\n]/)
  .map((item) => item.trim())
  .filter(Boolean)

const safeJsonParse = (value, fallback) => {
  if (!String(value || '').trim()) return fallback
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

const buildLlmModelOptions = (configRows = []) => {
  const groups = new Map()
  configRows.forEach((item) => {
    const match = String(item.configKey || '').match(TEXT_MODEL_CONFIG_PATTERN)
    if (!match) return
    const [, configName = 'default', field] = match
    const configPrefix = configName === 'default' ? 'ai.service.text' : `ai.service.text.${configName}`
    const group = groups.get(configPrefix) || { configPrefix, configs: {} }
    group.configs[field] = item
    groups.set(configPrefix, group)
  })

  return Array.from(groups.values())
    .filter((group) => ['provider', 'base-url', 'api-key', 'model'].every((field) => {
      const config = group.configs[field]
      return config && Number(config.status) === 1 && String(config.configValue || '').trim()
    }))
    .map((group) => ({
      value: group.configPrefix,
      label: group.configs.model.configValue,
    }))
}

const executionModeLabels = {
  leader_direct_answer: 'Leader 直接回答',
  leader_call_tool: 'Leader 调用接口',
  leader_routed_direct_agent: 'Leader 调用非检索智能体',
  leader_routed_rag: 'Leader 调用 RAG 智能体',
  direct_agent: '直接处理',
  rag_then_agent: 'RAG + 智能体',
}

const getStrategyOptionLabel = (strategy) => {
  if (!strategy) return ''
  return `${strategy.label || strategy.name} · ${strategy.categoryLabel || strategy.category}`
}

const getAgentNeedsRetrieval = (agent) => Boolean(agent && agent.needRetrieval !== false && agent.name !== 'leader_agent')

const getExecutionLabel = (metadata = {}) => (
  metadata.executionModeLabel ||
  executionModeLabels[metadata.executionMode] ||
  metadata.executionMode ||
  '未知执行方式'
)

const readFileAsBase64 = (file) => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => {
    const result = String(reader.result || '')
    resolve(result.includes(',') ? result.split(',').pop() : result)
  }
  reader.onerror = reject
  reader.readAsDataURL(file)
})

const extractMermaidCodeBlock = (text) => {
  const match = String(text || '').match(/```mermaid\s*([\s\S]*?)```/i)
  return match ? String(match[1] || '').trim() : ''
}

const normalizeMindMapLabel = (line) => {
  let text = String(line || '').trim().replace(/^[-*]\s+/, '')
  const rootMatch = text.match(/^root\s*\(\((.*)\)\)$/i)
  if (rootMatch) return rootMatch[1].trim()
  text = text.replace(/^\(\((.*)\)\)$/, '$1')
    .replace(/^\((.*)\)$/, '$1')
    .replace(/^\[(.*)\]$/, '$1')
    .replace(/^\{\{(.*)\}\}$/, '$1')
  return text.trim()
}

const parseMermaidMindMap = (source) => {
  const lines = String(source || '')
    .split('\n')
    .filter((line) => line.trim() && !/^\s*mindmap\s*$/i.test(line))
  const root = { label: '思维导图', children: [] }
  const stack = [{ indent: -1, node: root }]

  lines.forEach((line) => {
    const indent = line.match(/^\s*/)?.[0].length || 0
    const label = normalizeMindMapLabel(line)
    if (!label) return
    while (stack.length > 1 && indent <= stack[stack.length - 1].indent) {
      stack.pop()
    }
    const node = { label, children: [] }
    stack[stack.length - 1].node.children.push(node)
    stack.push({ indent, node })
  })

  return root.children[0] || root
}

const renderMindMapNode = (node, path = '0') => (
  <div className="rag-mindmap-node" key={path}>
    <div className="rag-mindmap-label">{node.label}</div>
    {node.children?.length ? (
      <div className="rag-mindmap-children">
        {node.children.map((child, index) => renderMindMapNode(child, `${path}-${index}`))}
      </div>
    ) : null}
  </div>
)

const MarkdownPreview = ({ source, assets = [] }) => {
  const assetPreviewUrls = new Map(
    assets
      .filter((asset) => asset.path && asset.previewDataUrl)
      .map((asset) => [asset.path, asset.previewDataUrl]),
  )

  return (
    <article className="rag-markdown-preview">
      <ReactMarkdown
        components={{
          img: ({ src = '', alt = '' }) => {
            const previewSrc = assetPreviewUrls.get(src)
            return previewSrc ? (
              <span className="rag-markdown-image">
                <img src={previewSrc} alt={alt || 'PDF 提取图片'} />
                <span className="rag-markdown-image__caption">{alt || src}</span>
              </span>
            ) : (
              <span className="rag-markdown-missing-image">图片资源：{alt || src}</span>
            )
          },
        }}
      >
        {source || '暂无预览'}
      </ReactMarkdown>
    </article>
  )
}

const agentExampleInputs = {
  leader_agent: '请自动判断：帮我把数据结构的栈与队列整理成复习资料',
  mind_map_agent: '把操作系统进程调度整理成思维导图',
  md_knowledge_agent: '# 数据结构\n- 栈遵循后进先出\n- 队列遵循先进先出\n- 图可以用邻接矩阵或邻接表表示',
  textbook_knowledge_agent: '查询并整理数据结构中栈与队列的教材知识点',
  textbook_question_bank_agent: '根据数据结构中栈与队列的知识点生成 5 道练习题',
  ppt_agent: '根据数据结构中栈与队列的知识点生成 6 页课件大纲',
  image_agent: '为操作系统进程调度知识点生成一张课堂教学配图提示词',
}

function RagManage() {
  const [bootLoading, setBootLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [strategies, setStrategies] = useState([])
  const [capabilities, setCapabilities] = useState(null)
  const [framework, setFramework] = useState(null)
  const [agents, setAgents] = useState([])
  const [agentWorkflow, setAgentWorkflow] = useState({})
  const [documents, setDocuments] = useState([])
  const [health, setHealth] = useState({})
  const [queryResult, setQueryResult] = useState(null)
  const [queryError, setQueryError] = useState('')
  const [evaluationResult, setEvaluationResult] = useState(null)
  const [sqlSchema, setSqlSchema] = useState(null)
  const [sqlResult, setSqlResult] = useState(null)
  const [agentTestResult, setAgentTestResult] = useState(null)
  const [agentTestLoading, setAgentTestLoading] = useState(false)
  const [uploadFileList, setUploadFileList] = useState([])
  const [convertFileList, setConvertFileList] = useState([])
  const [convertResult, setConvertResult] = useState(null)
  const [convertLoading, setConvertLoading] = useState(false)
  const [llmModelOptions, setLlmModelOptions] = useState([])
  const [queryForm] = Form.useForm()
  const [ingestForm] = Form.useForm()
  const [convertForm] = Form.useForm()
  const [evaluateForm] = Form.useForm()
  const [sqlForm] = Form.useForm()
  const [agentTestForm] = Form.useForm()

  const strategyOptions = useMemo(
    () => [
      { value: '', label: '按智能体默认策略' },
      ...strategies.map((item) => ({ value: item.name, label: getStrategyOptionLabel(item) })),
    ],
    [strategies]
  )

  const getStrategyOptionsForAgent = (agent) => {
    if (!getAgentNeedsRetrieval(agent)) return []
    const supported = agent?.supportedRagStrategies || []
    if (!supported.length) return strategyOptions
    return [
      strategyOptions[0],
      ...strategies
        .filter((item) => supported.includes(item.name))
        .map((item) => ({ value: item.name, label: getStrategyOptionLabel(item) })),
    ]
  }

  const agentOptions = useMemo(
    () => [
      { value: 'leader_agent', label: 'Leader 自动路由 · 意图识别/分发' },
      ...agents.filter((item) => item.name !== 'leader_agent').map((item) => ({
        value: item.name,
        label: `${item.role} · ${item.name}`,
      })),
    ],
    [agents]
  )

  const agentTestOptions = useMemo(
    () => agents.map((item) => ({
      value: item.name,
      label: `${item.role} · ${item.name}`,
    })),
    [agents]
  )

  const getAgentExampleInput = useCallback((agent) => (
    agent?.invokeExample?.input ||
    agentExampleInputs[agent?.name] ||
    `请使用${agent?.role || '智能体'}处理这段课程内容`
  ), [])

  const fillAgentTestForm = useCallback((agent) => {
    if (!agent) return
    agentTestForm.setFieldsValue({
      agentName: agent.name,
      ragStrategy: getAgentNeedsRetrieval(agent)
        ? agent.invokeExample?.ragStrategy || agent.defaultRagStrategy || ''
        : '',
      input: getAgentExampleInput(agent),
    })
  }, [agentTestForm, getAgentExampleInput])

  const refresh = async () => {
    setBootLoading(true)
    try {
      const [
        strategyRes,
        capabilityRes,
        frameworkRes,
        agentRes,
        documentRes,
        vectorHealthRes,
        embeddingHealthRes,
        graphHealthRes,
        schemaRes,
        aiConfigRes,
      ] = await Promise.all([
        getRagStrategies(),
        getRagCapabilities(),
        getRagFramework(),
        getRagAgents(),
        getRagDocuments(),
        getRagVectorStoreHealth(),
        getRagEmbeddingHealth(),
        getRagGraphStoreHealth(),
        getTextToSqlSchema(),
        getSystemConfigList({ current: 1, size: 500, prefixes: 'ai.service.' }),
      ])
      setStrategies(strategyRes.data?.strategies || [])
      setCapabilities(capabilityRes.data || null)
      setFramework(frameworkRes.data || null)
      setAgents(agentRes.data?.agents || [])
      setAgentWorkflow(agentRes.data?.workflow || {})
      setDocuments(documentRes.data?.documents || [])
      setHealth({
        vector: vectorHealthRes.data,
        embedding: embeddingHealthRes.data,
        graph: graphHealthRes.data,
      })
      setSqlSchema(schemaRes.data?.schema || null)

      setLlmModelOptions(buildLlmModelOptions(aiConfigRes.data?.records || []))
    } catch (error) {
      message.error(error.message || '加载 RAG 管理数据失败')
    } finally {
      setBootLoading(false)
    }
  }

  useEffect(() => {
    refresh()
  }, [])

  useEffect(() => {
    if (!agents.length || agentTestForm.getFieldValue('agentName')) return
    const leader = agents.find((item) => item.name === 'leader_agent') || agents[0]
    fillAgentTestForm(leader)
  }, [agents, agentTestForm, fillAgentTestForm])

  useEffect(() => {
    if (!llmModelOptions.length) return
    const defaultModel = llmModelOptions[0].value
    if (!queryForm.getFieldValue('llmModel')) {
      queryForm.setFieldsValue({ llmModel: defaultModel })
    }
    if (!agentTestForm.getFieldValue('llmModel')) {
      agentTestForm.setFieldsValue({ llmModel: defaultModel })
    }
  }, [llmModelOptions, queryForm, agentTestForm])

  const handleQuery = async (values) => {
    setActionLoading(true)
    setQueryError('')
    try {
      const selectedAgentName = values.agentName || 'leader_agent'
      const selectedAgent = agents.find((item) => item.name === selectedAgentName)
      const canUseRagStrategy = getAgentNeedsRetrieval(selectedAgent)
      const res = await runRagQuery({
        input: values.input,
        keyword: values.keyword || undefined,
        intent: values.intent || 'campus_search',
        ragStrategy: canUseRagStrategy ? values.ragStrategy || undefined : undefined,
        agentName: selectedAgentName,
        llmModel: values.llmModel || undefined,
        metadata: {},
      })
      setQueryResult(res.data)
      message.success('智能体执行完成')
    } catch (error) {
      const errorMessage = error.message || '智能体执行失败'
      setQueryResult(null)
      setQueryError(errorMessage)
      message.error(errorMessage)
    } finally {
      setActionLoading(false)
    }
  }

  const handleIngest = async (values) => {
    setActionLoading(true)
    try {
      const selectedFile = uploadFileList[0]?.originFileObj || uploadFileList[0]
      const textContent = values.content || ''
      if (!selectedFile && !textContent.trim()) {
        message.warning('请粘贴文档内容，或选择一个本地文件')
        return
      }
      const contentBase64 = selectedFile ? await readFileAsBase64(selectedFile) : undefined
      const res = await ingestRagDocuments({
        documents: [{
          source: values.source || selectedFile?.name || '后台录入.md',
          content: textContent,
          contentBase64,
          metadata: {
            origin: 'admin_console',
            uploadMode: selectedFile ? 'file_base64' : 'text',
          },
        }],
      })
      message.success(`已入库 ${res.data?.storedCount || 0} 个文档`)
      ingestForm.resetFields()
      setUploadFileList([])
      await refresh()
    } catch (error) {
      message.error(error.message || '知识入库失败')
    } finally {
      setActionLoading(false)
    }
  }

  const handlePdfConvert = async (values) => {
    const selectedFile = convertFileList[0]?.originFileObj || convertFileList[0]
    if (!selectedFile) {
      message.warning('请先选择一个 PDF 文件')
      return
    }
    setConvertLoading(true)
    setConvertResult(null)
    try {
      const formData = new FormData()
      formData.append('file', selectedFile)
      formData.append('targetFormat', values.targetFormat)
      const res = await convertPdf(formData)
      setConvertResult(res.data)
      message.success('PDF 转换完成')
    } catch (error) {
      message.error(error.message || 'PDF 转换失败')
    } finally {
      setConvertLoading(false)
    }
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
    link.download = result.fileName || 'pdf-convert-result'
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  }

  const handleEvaluate = async (values) => {
    setActionLoading(true)
    try {
      const documentsPayload = safeJsonParse(values.documentsJson, queryResult?.documents || [])
      const res = await evaluateRag({
        query: values.query,
        answer: values.answer,
        documents: Array.isArray(documentsPayload) ? documentsPayload : [],
        expectedSources: toList(values.expectedSources),
        expectedAnswerTerms: toList(values.expectedAnswerTerms),
      })
      setEvaluationResult(res.data)
      message.success('评估完成')
    } catch (error) {
      message.error(error.message || '评估失败')
    } finally {
      setActionLoading(false)
    }
  }

  const handleUseLastQueryForEvaluation = () => {
    if (!queryResult) {
      message.warning('请先执行一次 RAG 查询')
      return
    }
    evaluateForm.setFieldsValue({
      query: queryForm.getFieldValue('input'),
      answer: queryResult.answer,
      documentsJson: JSON.stringify(queryResult.documents || [], null, 2),
    })
    message.success('已填入最近一次查询结果')
  }

  const handleTextToSql = async (values) => {
    setActionLoading(true)
    try {
      const res = await executeTextToSql({
        input: values.input,
        ragStrategy: 'text_to_sql',
      })
      setSqlResult(res.data)
      message.success('Text-to-SQL 执行完成')
    } catch (error) {
      message.error(error.message || 'Text-to-SQL 执行失败')
    } finally {
      setActionLoading(false)
    }
  }

  const handleAgentTest = async (values) => {
    const agent = agents.find((item) => item.name === values.agentName)
    if (!agent) {
      message.warning('请先选择一个智能体')
      return
    }
    const payload = {
      input: values.input,
      intent: agent.intent === 'auto' ? 'campus_search' : agent.intent,
      ragStrategy: getAgentNeedsRetrieval(agent)
        ? values.ragStrategy || agent.defaultRagStrategy || undefined
        : undefined,
      agentName: agent.name,
      llmModel: values.llmModel || undefined,
      metadata: {
        testFrom: 'admin_agent_console',
        agentRole: agent.role,
        needRetrieval: agent.needRetrieval,
      },
    }

    setAgentTestLoading(true)
    try {
      const res = await runRagQuery(payload)
      setAgentTestResult({
        agent,
        request: payload,
        response: res.data,
      })
      message.success(`${agent.role} 调用成功`)
    } catch (error) {
      setAgentTestResult({
        agent,
        request: payload,
        error: error.message || '智能体调用失败',
      })
      message.error(error.message || '智能体调用失败')
    } finally {
      setAgentTestLoading(false)
    }
  }

  const renderHealthCard = (key, title, icon) => {
    const data = health[key] || {}
    const healthy = data.status === 'implemented' || data.configured
    return (
      <Card className="rag-health-card">
        <div className="rag-health-card__top">
          <span className="rag-health-card__icon">{icon}</span>
          <Tag color={healthy ? 'green' : 'orange'}>{data.status || '-'}</Tag>
        </div>
        <Statistic title={title} value={data.backend || data.provider || '-'} />
        <Text type="secondary">configured: {String(data.configured ?? true)}</Text>
      </Card>
    )
  }

  const renderProviderCard = (title, dataSource) => (
    <Card title={title} className="rag-panel-card rag-provider-card">
      <Table
        rowKey="name"
        columns={providerColumns}
        dataSource={dataSource || []}
        pagination={false}
        size="small"
        scroll={{ x: 560 }}
      />
    </Card>
  )

  const renderAgentAnswer = (answer, response = {}) => {
    const text = String(answer || '').trim()
    const metadata = response?.metadata || response || {}
    const answerType = response?.answerType || metadata?.answerType || 'text'
    if (!text) {
      return <div className="rag-answer-box">暂无回答</div>
    }
    if (answerType !== 'mermaid_mindmap') {
      return <div className={`rag-answer-box rag-answer-box--${answerType}`}>{text}</div>
    }
    const mermaidBody = extractMermaidCodeBlock(text) || text
    const markdownSource = text.startsWith('```mermaid') ? text : `\`\`\`mermaid\n${mermaidBody}\n\`\`\``
    const mindMapTree = parseMermaidMindMap(mermaidBody)
    return (
      <div className="rag-answer-box rag-answer-box--mindmap">
        <div className="rag-mindmap-canvas">
          {renderMindMapNode(mindMapTree)}
        </div>
        <Collapse
          className="rag-mindmap-source"
          size="small"
          ghost
          items={[
            {
              key: 'source',
              label: 'Markdown 源码',
              children: <pre className="rag-mermaid-code">{markdownSource}</pre>,
            },
          ]}
        />
      </div>
    )
  }

  const tabs = [
    {
      key: 'playground',
      label: '智能体执行',
      children: (
        <Row gutter={[20, 20]}>
          <Col xs={24} lg={9}>
            <Card title="Leader / RAG 查询" className="rag-panel-card">
              <Form
                form={queryForm}
                layout="vertical"
                initialValues={{ ragStrategy: '', intent: 'campus_search', agentName: 'leader_agent' }}
                onFinish={handleQuery}
              >
                <Alert
                  className="rag-inline-alert"
                  type="warning"
                  showIcon
                  message="默认走 Java 的 ai.service.text.* 模型配置；你也可以在下方选择已配置的文本模型作为本次请求覆盖值。配置缺失或模型调用失败会直接报错。"
                />
                <Form.Item name="agentName" label="执行智能体">
                  <Select
                    options={agentOptions}
                    placeholder="Leader 自动路由"
                    onChange={() => queryForm.setFieldsValue({ ragStrategy: '' })}
                  />
                </Form.Item>
                <Form.Item name="llmModel" label="LLM 模型">
                  <Select
                    options={llmModelOptions}
                    placeholder="默认使用系统配置模型"
                    showSearch
                    optionFilterProp="label"
                    allowClear
                  />
                </Form.Item>
                <Form.Item
                  noStyle
                  shouldUpdate={(prev, next) => prev.agentName !== next.agentName}
                >
                  {({ getFieldValue }) => {
                    const selectedAgent = agents.find((item) => item.name === getFieldValue('agentName'))
                    if (!getAgentNeedsRetrieval(selectedAgent)) {
                      return (
                        <Alert
                          className="rag-inline-alert"
                          type="info"
                          showIcon
                          message={selectedAgent?.name === 'leader_agent' ? 'Leader 会先做意图识别，再决定直接回答、调用智能体或接口。' : '当前智能体不需要 RAG 策略，会直接处理输入。'}
                        />
                      )
                    }
                    return (
                      <Form.Item name="ragStrategy" label="RAG 执行策略" extra="只有需要检索的专业智能体才会使用该参数。">
                        <Select options={getStrategyOptionsForAgent(selectedAgent)} showSearch optionFilterProp="label" />
                      </Form.Item>
                    )
                  }}
                </Form.Item>
                <Form.Item name="intent" label="意图">
                  <Input placeholder="campus_search" />
                </Form.Item>
                <Form.Item name="keyword" label="检索关键词">
                  <Input allowClear placeholder="可空，默认由问题推断" />
                </Form.Item>
                <Form.Item name="input" label="用户问题" rules={[{ required: true, message: '请输入用户问题' }]}>
                  <TextArea rows={5} placeholder="例如：统计食堂优惠券数量 / 校园卡补办在哪里？" />
                </Form.Item>
                <Button type="primary" htmlType="submit" icon={<PlayCircleOutlined />} loading={actionLoading} block>
                  执行智能体
                </Button>
              </Form>
            </Card>
          </Col>
          <Col xs={24} lg={15}>
            <Card title="查询结果" className="rag-panel-card">
              {queryError ? (
                <Alert
                  type="error"
                  showIcon
                  message="智能体执行失败"
                  description={queryError}
                />
              ) : queryResult ? (
                <Space direction="vertical" size="large" className="rag-full">
                  <div className="rag-agent-test-status">
                    <Tag color="green">{getExecutionLabel(queryResult.metadata)}</Tag>
                    <Tag color="blue">入口：{queryResult.metadata?.agentName || 'leader_agent'}</Tag>
                    {queryResult.metadata?.targetAgent && <Tag>目标：{queryResult.metadata.targetAgent}</Tag>}
                    {queryResult.metadata?.executedAgent && <Tag color="geekblue">执行：{queryResult.metadata.executedAgent}</Tag>}
                    <Tag color="volcano">类型：{queryResult.answerType || queryResult.metadata?.answerType || 'text'}</Tag>
                    <Tag color={queryResult.metadata?.needRetrieval ? 'cyan' : 'gold'}>{queryResult.metadata?.strategyLabel || queryResult.strategy}</Tag>
                  </div>
                  {renderAgentAnswer(queryResult.answer, queryResult)}
                  <Table
                    rowKey={(record) => record.id || record.source}
                    columns={evidenceColumns}
                    dataSource={queryResult.documents || []}
                    pagination={{ pageSize: 5 }}
                  />
                  <Collapse
                    items={[
                      {
                        key: 'trace',
                        label: 'Trace / Metadata',
                        children: <pre className="rag-code-block">{JSON.stringify({ trace: queryResult.trace, metadata: queryResult.metadata }, null, 2)}</pre>,
                      },
                    ]}
                  />
                </Space>
              ) : (
                <Empty description="执行一次后查看答案、证据和 trace" />
              )}
            </Card>
          </Col>
        </Row>
      ),
    },
    {
      key: 'knowledge',
      label: '知识库',
      children: (
        <Row gutter={[20, 20]}>
          <Col xs={24} lg={9}>
            <Card title="新增知识文档" className="rag-panel-card">
              <Form form={ingestForm} layout="vertical" onFinish={handleIngest}>
                <Form.Item name="source" label="来源文件名">
                  <Input placeholder="例如：校园卡服务.md" />
                </Form.Item>
                <Form.Item label="本地文件">
                  <Upload
                    beforeUpload={(file) => {
                      setUploadFileList([file])
                      ingestForm.setFieldsValue({ source: file.name })
                      return false
                    }}
                    fileList={uploadFileList}
                    maxCount={1}
                    onRemove={() => {
                      setUploadFileList([])
                      return true
                    }}
                  >
                    <Button icon={<UploadOutlined />}>选择文件</Button>
                  </Upload>
                  <Text type="secondary">支持 Markdown、TXT、CSV、TSV、JSON、HTML、PDF 和图片；文件会以 Base64 传给 Python 服务入库。</Text>
                </Form.Item>
                <Form.Item name="content" label="文档内容">
                  <TextArea rows={10} placeholder="粘贴 Markdown、文本、表格摘要等知识内容" />
                </Form.Item>
                <Button type="primary" htmlType="submit" icon={<FileTextOutlined />} loading={actionLoading} block>
                  入库并索引
                </Button>
              </Form>
            </Card>
          </Col>
          <Col xs={24} lg={15}>
            <Card title="已入库文档" extra={<Button icon={<ReloadOutlined />} onClick={refresh} loading={bootLoading}>刷新</Button>} className="rag-panel-card">
              <Table
                rowKey={(record) => record.source}
                columns={documentColumns}
                dataSource={documents}
                pagination={{ pageSize: 8 }}
              />
            </Card>
          </Col>
        </Row>
      ),
    },
    {
      key: 'convert',
      label: '文档转换',
      children: (
        <Row gutter={[20, 20]}>
          <Col xs={24} lg={9}>
            <Card title="PDF 转换" className="rag-panel-card">
              <Form
                form={convertForm}
                layout="vertical"
                initialValues={{ targetFormat: 'md' }}
                onFinish={handlePdfConvert}
              >
                <Alert
                  className="rag-inline-alert"
                  type="info"
                  showIcon
                  message="支持 PDF 转 Markdown 或 DOCX；当前不做 OCR，扫描件无法提取文字时会直接报错。"
                />
                <Form.Item label="PDF 文件" required>
                  <Upload
                    accept="application/pdf,.pdf"
                    beforeUpload={(file) => {
                      if (!file.name.toLowerCase().endsWith('.pdf')) {
                        message.warning('请选择 PDF 文件')
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
                    <Button icon={<UploadOutlined />}>选择 PDF</Button>
                  </Upload>
                </Form.Item>
                <Form.Item name="targetFormat" label="输出格式" rules={[{ required: true, message: '请选择输出格式' }]}>
                  <Select
                    options={[
                      { value: 'md', label: 'Markdown（zip，含图片 assets）' },
                      { value: 'docx', label: 'DOCX（尽量保留图片与基础排版）' },
                    ]}
                  />
                </Form.Item>
                <Button type="primary" htmlType="submit" icon={<FileTextOutlined />} loading={convertLoading} block>
                  开始转换
                </Button>
              </Form>
            </Card>
          </Col>
          <Col xs={24} lg={15}>
            <Card title="转换结果" className="rag-panel-card">
              {convertResult ? (
                <Space direction="vertical" size="large" className="rag-full">
                  <div className="rag-agent-test-status">
                    <Tag color="green">转换完成</Tag>
                    <Tag color="blue">{convertResult.format}</Tag>
                    <Tag color="cyan">{convertResult.downloadType}</Tag>
                    {Number.isFinite(convertResult.imageCount) ? <Tag color="geekblue">图片：{convertResult.imageCount}</Tag> : null}
                  </div>
                  <Space wrap>
                    <Text strong>{convertResult.fileName}</Text>
                    <Text type="secondary">{convertResult.contentLength ? `${Math.ceil(convertResult.contentLength / 1024)} KB` : ''}</Text>
                    <Button type="primary" icon={<DownloadOutlined />} onClick={() => downloadConvertedFile(convertResult)}>
                      下载结果
                    </Button>
                  </Space>
                  {convertResult.outputType === 'markdown' ? (
                    <Collapse
                      defaultActiveKey={['preview']}
                      items={[
                        {
                          key: 'preview',
                          label: 'Markdown 预览',
                          children: <MarkdownPreview source={convertResult.preview} assets={convertResult.assets} />,
                        },
                        {
                          key: 'assets',
                          label: '图片资源',
                          children: (
                            <Table
                              rowKey="path"
                              columns={[
                                { title: '文件', dataIndex: 'name' },
                                { title: '页码', dataIndex: 'page', width: 100 },
                                { title: '路径', dataIndex: 'path' },
                                { title: '大小', dataIndex: 'size', width: 120, render: (value) => `${Math.ceil(Number(value || 0) / 1024)} KB` },
                              ]}
                              dataSource={convertResult.assets || []}
                              pagination={{ pageSize: 5 }}
                              size="small"
                            />
                          ),
                        },
                      ]}
                    />
                  ) : (
                    <Alert type="success" showIcon message="DOCX 已生成，点击上方按钮下载。" />
                  )}
                </Space>
              ) : (
                <Empty description="上传 PDF 并选择格式后，转换结果会显示在这里" />
              )}
            </Card>
          </Col>
        </Row>
      ),
    },
    {
      key: 'strategy',
      label: '策略与健康',
      children: (
        <Space direction="vertical" size="large" className="rag-full">
          <Row gutter={[20, 20]}>
            <Col xs={24} md={8}>{renderHealthCard('vector', '向量库', <DatabaseOutlined />)}</Col>
            <Col xs={24} md={8}>{renderHealthCard('embedding', 'Embedding', <ApiOutlined />)}</Col>
            <Col xs={24} md={8}>{renderHealthCard('graph', '图谱存储', <BranchesOutlined />)}</Col>
          </Row>
          <Card title="16 种 RAG 执行策略" className="rag-panel-card">
            <Table
              rowKey="name"
              columns={strategyColumns}
              dataSource={strategies}
              pagination={{ pageSize: 8 }}
            />
          </Card>
          <Card title="能力目录" className="rag-panel-card">
            <pre className="rag-code-block">{JSON.stringify(capabilities || {}, null, 2)}</pre>
          </Card>
        </Space>
      ),
    },
    {
      key: 'agents',
      label: '多智能体',
      children: (
        <Space direction="vertical" size="large" className="rag-full">
          <Card
            title="智能体调用测试"
            extra={<Tag color="geekblue">参数：agentName</Tag>}
            className="rag-panel-card"
          >
            <Row gutter={[20, 20]}>
              <Col xs={24} lg={9}>
                <Form
                  form={agentTestForm}
                  layout="vertical"
                  onFinish={handleAgentTest}
                >
                  <Form.Item name="agentName" label="选择要测试的智能体" rules={[{ required: true, message: '请选择智能体' }]}>
                    <Select
                      options={agentTestOptions}
                      showSearch
                      optionFilterProp="label"
                      placeholder="选择智能体"
                      onChange={(value) => fillAgentTestForm(agents.find((item) => item.name === value))}
                    />
                  </Form.Item>
                  <Form.Item name="llmModel" label="LLM 模型">
                    <Select
                      options={llmModelOptions}
                      placeholder="默认使用系统配置模型"
                      showSearch
                      optionFilterProp="label"
                      allowClear
                    />
                  </Form.Item>
                  <Form.Item
                    noStyle
                    shouldUpdate={(prev, next) => prev.agentName !== next.agentName}
                  >
                    {({ getFieldValue }) => {
                      const selectedAgent = agents.find((item) => item.name === getFieldValue('agentName'))
                      const canUseRagStrategy = getAgentNeedsRetrieval(selectedAgent)
                      if (!canUseRagStrategy) {
                        return (
                          <Alert
                            className="rag-inline-alert"
                            type="info"
                            showIcon
                            message={selectedAgent?.name === 'leader_agent'
                              ? 'Leader 不手动选择 RAG 策略；它会自动判断直接回答、调用专业智能体或调用接口。'
                              : '该智能体不跑 RAG，会直接处理输入内容。'}
                          />
                        )
                      }
                      return (
                        <Form.Item name="ragStrategy" label="RAG 执行策略" extra="该智能体会先检索证据，再生成结构化结果。">
                          <Select options={getStrategyOptionsForAgent(selectedAgent)} showSearch optionFilterProp="label" />
                        </Form.Item>
                      )
                    }}
                  </Form.Item>
                  <Form.Item name="input" label="测试输入" rules={[{ required: true, message: '请输入测试内容' }]}>
                    <TextArea rows={6} placeholder="输入一段课程内容或任务要求" />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" icon={<PlayCircleOutlined />} loading={agentTestLoading} block>
                    调用当前智能体
                  </Button>
                  <Text type="secondary" className="rag-agent-test-tip">
                    Leader 用于意图识别、自动分发和工具调用；MD 知识点提取等非检索智能体会直接处理输入；教材、PPT、题库、图片等需要证据的智能体才会显示 RAG 执行策略。
                  </Text>
                </Form>
              </Col>
              <Col xs={24} lg={15}>
                {agentTestResult ? (
                  <Space direction="vertical" size="large" className="rag-full">
                    <div className="rag-agent-test-status">
                      <Tag color={agentTestResult.error ? 'red' : 'green'}>
                        {agentTestResult.error ? '调用失败' : '调用成功'}
                      </Tag>
                      <Tag color="blue">{agentTestResult.agent?.name}</Tag>
                      <Tag>{agentTestResult.response?.metadata?.agentName || agentTestResult.request?.agentName}</Tag>
                      <Tag color="purple">{getExecutionLabel(agentTestResult.response?.metadata)}</Tag>
                      {agentTestResult.response?.metadata?.executedAgent && <Tag color="geekblue">执行：{agentTestResult.response.metadata.executedAgent}</Tag>}
                      <Tag color="volcano">类型：{agentTestResult.response?.answerType || agentTestResult.response?.metadata?.answerType || 'text'}</Tag>
                    </div>
                    {agentTestResult.error ? (
                      <div className="rag-answer-box">{agentTestResult.error}</div>
                    ) : (
                      <>
                        {renderAgentAnswer(agentTestResult.response?.answer, agentTestResult.response)}
                        <Table
                          rowKey={(record) => record.id || record.source}
                          columns={evidenceColumns}
                          dataSource={agentTestResult.response?.documents || []}
                          pagination={{ pageSize: 4 }}
                          size="small"
                        />
                      </>
                    )}
                    <Collapse
                      items={[
                        {
                          key: 'request',
                          label: '请求参数',
                          children: <pre className="rag-code-block">{JSON.stringify(agentTestResult.request || {}, null, 2)}</pre>,
                        },
                        {
                          key: 'trace',
                          label: 'Trace / Metadata',
                          children: <pre className="rag-code-block">{JSON.stringify({
                            trace: agentTestResult.response?.trace || [],
                            metadata: agentTestResult.response?.metadata || {},
                          }, null, 2)}</pre>,
                        },
                      ]}
                    />
                  </Space>
                ) : (
                  <Empty description="选择一个智能体并点击调用，就能在这里查看回答、证据和 trace" />
                )}
              </Col>
            </Row>
          </Card>
          <Row gutter={[20, 20]}>
            <Col xs={24} lg={10}>
              <Card title="协作流程" className="rag-panel-card">
                <pre className="rag-code-block">{JSON.stringify(agentWorkflow, null, 2)}</pre>
              </Card>
            </Col>
            <Col xs={24} lg={14}>
              <Card title="Agent / Skill 目录" className="rag-panel-card">
                <Table
                  rowKey="name"
                  columns={agentColumns}
                  dataSource={agents}
                  pagination={{ pageSize: 6 }}
                />
              </Card>
            </Col>
          </Row>
          <Card title="每个智能体的职责、输入输出和技能文件" className="rag-panel-card">
            <Collapse
              items={agents.map((agent) => ({
                key: agent.name,
                label: `${agent.name} · ${agent.role}`,
                children: (
                  <Space direction="vertical" className="rag-full">
                    <Text>{agent.purpose}</Text>
                    <Card size="small" title="后台调用方式">
                      <Space direction="vertical" className="rag-full">
                        <pre className="rag-code-block">{JSON.stringify({
                          agentName: agent.name,
                          ...(agent.name === 'leader_agent'
                            ? { executionMode: 'leader_orchestration' }
                            : getAgentNeedsRetrieval(agent)
                              ? { ragStrategy: agent.defaultRagStrategy }
                              : { executionMode: 'direct_agent' }),
                          input: getAgentExampleInput(agent),
                        }, null, 2)}</pre>
                        <Button
                          icon={<PlayCircleOutlined />}
                          onClick={() => fillAgentTestForm(agent)}
                        >
                          填入上方测试台
                        </Button>
                      </Space>
                    </Card>
                    <Row gutter={[16, 16]}>
                      <Col xs={24} md={12}>
                        <Card size="small" title="输入">
                          {(agent.inputs || []).map((item) => <Tag key={item}>{item}</Tag>)}
                        </Card>
                      </Col>
                      <Col xs={24} md={12}>
                        <Card size="small" title="输出">
                          {(agent.outputs || []).map((item) => <Tag key={item}>{item}</Tag>)}
                        </Card>
                      </Col>
                    </Row>
                    <Collapse
                      items={[
                        {
                          key: 'skill',
                          label: 'skill.md',
                          children: <pre className="rag-code-block">{agent.documents?.skill || '暂无 skill 文档'}</pre>,
                        },
                        {
                          key: 'prompt',
                          label: 'prompt.md',
                          children: <pre className="rag-code-block">{agent.documents?.prompt || '暂无 prompt 文档'}</pre>,
                        },
                        {
                          key: 'contract',
                          label: 'contract.md / tools.yaml',
                          children: <pre className="rag-code-block">{`${agent.documents?.contract || ''}\n\n${agent.documents?.tools || ''}`}</pre>,
                        },
                        {
                          key: 'files',
                          label: '文件路径',
                          children: <pre className="rag-code-block">{JSON.stringify(agent.files || {}, null, 2)}</pre>,
                        },
                      ]}
                    />
                  </Space>
                ),
              }))}
            />
          </Card>
        </Space>
      ),
    },
    {
      key: 'framework',
      label: '框架配置',
      children: (
        <Space direction="vertical" size="large" className="rag-full">
          <Card title="文档功能覆盖" className="rag-panel-card">
            <Table
              rowKey="name"
              columns={coverageColumns}
              dataSource={framework?.coverage || []}
              pagination={{ pageSize: 8 }}
            />
          </Card>
          <div className="rag-provider-grid">
            {renderProviderCard('Model Provider', framework?.modelProviders)}
            {renderProviderCard('Embedding Provider', framework?.embeddingProviders)}
            {renderProviderCard('Vector Store', framework?.vectorStores)}
            {renderProviderCard('Graph Store', framework?.graphStores)}
          </div>
          <Row gutter={[20, 20]}>
            <Col xs={24} lg={10}>
              <Card title="运行环境" className="rag-panel-card">
                <Table
                  rowKey="name"
                  columns={envColumns}
                  dataSource={framework?.runtimeEnv || []}
                  pagination={false}
                  size="small"
                />
              </Card>
            </Col>
            <Col xs={24} lg={14}>
              <Card title="目录与 API" className="rag-panel-card">
                <Collapse
                  items={[
                    {
                      key: 'folders',
                      label: '运行目录',
                      children: <pre className="rag-code-block">{JSON.stringify(framework?.runtimeFolders || {}, null, 2)}</pre>,
                    },
                    {
                      key: 'indexing',
                      label: '索引配置',
                      children: <pre className="rag-code-block">{JSON.stringify(framework?.indexing || {}, null, 2)}</pre>,
                    },
                    {
                      key: 'apis',
                      label: '接口清单',
                      children: <pre className="rag-code-block">{JSON.stringify(framework?.apis || [], null, 2)}</pre>,
                    },
                  ]}
                />
              </Card>
            </Col>
          </Row>
        </Space>
      ),
    },
    {
      key: 'evaluate',
      label: '评估',
      children: (
        <Row gutter={[20, 20]}>
          <Col xs={24} lg={10}>
            <Card
              title="RAG 评估"
              extra={<Button size="small" onClick={handleUseLastQueryForEvaluation}>使用最近查询</Button>}
              className="rag-panel-card"
            >
              <Form form={evaluateForm} layout="vertical" onFinish={handleEvaluate}>
                <Form.Item name="query" label="问题" rules={[{ required: true, message: '请输入问题' }]}>
                  <Input />
                </Form.Item>
                <Form.Item name="answer" label="答案">
                  <TextArea rows={4} />
                </Form.Item>
                <Form.Item name="expectedSources" label="期望来源">
                  <Input placeholder="逗号或换行分隔，例如 card.md" />
                </Form.Item>
                <Form.Item name="expectedAnswerTerms" label="期望答案词">
                  <Input placeholder="逗号或换行分隔，例如 行政楼" />
                </Form.Item>
                <Form.Item name="documentsJson" label="证据 JSON">
                  <TextArea rows={7} placeholder="为空时使用最近一次 RAG 查询的 documents" />
                </Form.Item>
                <Button type="primary" htmlType="submit" icon={<ExperimentOutlined />} loading={actionLoading} block>
                  开始评估
                </Button>
              </Form>
            </Card>
          </Col>
          <Col xs={24} lg={14}>
            <Card title="评估结果" className="rag-panel-card">
              {evaluationResult ? (
                <Space direction="vertical" className="rag-full">
                  <Tag color={evaluationResult.passed ? 'green' : 'red'}>{evaluationResult.passed ? '通过' : '未通过'}</Tag>
                  <Table
                    rowKey="name"
                    columns={metricColumns}
                    dataSource={Object.entries(evaluationResult.metrics || {}).map(([name, value]) => ({ name, value }))}
                    pagination={false}
                  />
                  <pre className="rag-code-block">{JSON.stringify(evaluationResult.detail || {}, null, 2)}</pre>
                </Space>
              ) : (
                <Empty description="提交评估后查看指标" />
              )}
            </Card>
          </Col>
        </Row>
      ),
    },
    {
      key: 'sql',
      label: 'Text-to-SQL',
      children: (
        <Row gutter={[20, 20]}>
          <Col xs={24} lg={9}>
            <Card title="自然语言查结构化数据" className="rag-panel-card">
              <Form form={sqlForm} layout="vertical" onFinish={handleTextToSql}>
                <Form.Item name="input" label="查询问题" rules={[{ required: true, message: '请输入查询问题' }]}>
                  <TextArea rows={5} placeholder="例如：查询黄焖鸡 / 统计优惠券列表" />
                </Form.Item>
                <Button type="primary" htmlType="submit" loading={actionLoading} block>
                  生成并执行 SQL
                </Button>
              </Form>
            </Card>
          </Col>
          <Col xs={24} lg={15}>
            <Card title="Schema 与执行结果" className="rag-panel-card">
              <Collapse
                defaultActiveKey={['result']}
                items={[
                  {
                    key: 'result',
                    label: '执行结果',
                    children: <pre className="rag-code-block">{JSON.stringify(sqlResult || {}, null, 2)}</pre>,
                  },
                  {
                    key: 'schema',
                    label: '当前 Schema',
                    children: <pre className="rag-code-block">{JSON.stringify(sqlSchema || {}, null, 2)}</pre>,
                  },
                ]}
              />
            </Card>
          </Col>
        </Row>
      ),
    },
  ]

  return (
    <div className="rag-manage">
      <section className="rag-hero">
        <div>
          <span className="rag-kicker">AI RAG Console</span>
          <Title level={1}>RAG 管理</Title>
          <p>统一管理知识库、Leader 自动路由、多智能体测试、Text-to-SQL、GraphRAG 健康状态和评估指标。</p>
        </div>
        <Button icon={<ReloadOutlined />} onClick={refresh} loading={bootLoading}>
          刷新状态
        </Button>
      </section>

      <Tabs className="rag-tabs" items={tabs} />
    </div>
  )
}

export default RagManage
