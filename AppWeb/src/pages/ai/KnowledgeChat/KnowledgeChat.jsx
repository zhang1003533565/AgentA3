import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Alert, Button, Card, Empty, Form, Input, InputNumber, Select, Space, Spin, Tag, Typography, message } from 'antd'
import { DatabaseOutlined, FileTextOutlined, ReloadOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons'
import { getRagAgents } from '../../../api/rag'
import { getSystemConfigList } from '../../../api/systemConfig'
import {
  clearKnowledgeChatCache,
  getKnowledgeChatCacheStats,
  getMaxKbAccounts,
  getMaxKbKnowledges,
  runKnowledgeAgentChat,
} from '../../../api/maxkbKnowledge'
import {
  buildAgentModelBindings,
  buildLlmModelOptions,
  getAgentModelRequirementText,
  getAgentRequiredModelModalities,
  isAgentEnabled,
} from '../agentConfig'
import './KnowledgeChat.css'

const { Text, Title } = Typography
const { TextArea } = Input

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
    return { records: payload, total: payload.length }
  }
  const records = payload?.records || payload?.list || payload?.items || payload?.rows || []
  return {
    records: Array.isArray(records) ? records : [],
    total: Number(payload?.total ?? payload?.count ?? records.length ?? 0),
  }
}

const textValue = (...values) => {
  const value = values.find((item) => item !== undefined && item !== null && item !== '')
  return value === undefined ? '-' : String(value)
}

const searchModeOptions = [
  { value: 'blend', label: '混合检索' },
  { value: 'embedding', label: '向量检索' },
  { value: 'keywords', label: '关键词检索' },
]

const formatScore = (value) => {
  if (value === undefined || value === null || value === '') return null
  const number = Number(value)
  if (Number.isNaN(number)) return String(value)
  return number >= 1 ? number.toFixed(2) : number.toFixed(3)
}

const formatPercent = (value) => {
  const number = Number(value || 0)
  if (Number.isNaN(number)) return '0.0%'
  return `${(number * 100).toFixed(1)}%`
}

const formatCount = (value) => Number(value || 0).toLocaleString()

const formatDuration = (value) => {
  const number = Number(value || 0)
  if (!number) return '-'
  return `${number.toLocaleString()} ms`
}

function KnowledgeSourcePanel({ references = [] }) {
  const hasReferences = references.length > 0

  return (
    <div className="knowledge-chat-source-panel">
      <div className="knowledge-chat-source-head">
        <div className="knowledge-chat-source-title">
          <DatabaseOutlined />
          <span>知识库来源</span>
          <span className="knowledge-chat-source-count">| 引用分段 {references.length}</span>
        </div>
      </div>

      {hasReferences ? (
        <div className="knowledge-chat-source-list">
          {references.map((reference, index) => {
            const score = formatScore(reference.similarity)
            return (
              <div className="knowledge-chat-source-card" key={`${reference.id || index}-${index}`}>
                <div className="knowledge-chat-source-card-head">
                  <div className="knowledge-chat-source-card-title">
                    <span>{index + 1}.</span>
                    <strong>{textValue(reference.title, reference.documentName, reference.source, '引用分段')}</strong>
                  </div>
                  {score ? <Tag color="blue">{score}</Tag> : null}
                </div>
                <div className="knowledge-chat-source-content">
                  {textValue(reference.content, '该分段暂无文本内容')}
                </div>
                <div className="knowledge-chat-source-footer">
                  <span>
                    <FileTextOutlined />
                    {textValue(reference.documentName, '未知文档')}
                  </span>
                  <span>{textValue(reference.knowledgeName, '未知知识库')}</span>
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="knowledge-chat-source-empty">未检索到知识库片段</div>
      )}
    </div>
  )
}

function KnowledgeChat() {
  const [form] = Form.useForm()
  const [searchParams] = useSearchParams()
  const initialAccountId = searchParams.get('accountId')
  const initialKnowledgeId = searchParams.get('knowledgeId')
  const [accounts, setAccounts] = useState([])
  const [knowledges, setKnowledges] = useState([])
  const [agents, setAgents] = useState([])
  const [agentModelBindings, setAgentModelBindings] = useState({})
  const [llmModelOptions, setLlmModelOptions] = useState([])
  const [selectedAccountId, setSelectedAccountId] = useState(null)
  const [loading, setLoading] = useState(false)
  const [bootstrapLoading, setBootstrapLoading] = useState(false)
  const [messages, setMessages] = useState([])
  const [sessionId, setSessionId] = useState('')
  const [cacheStats, setCacheStats] = useState(null)

  const selectedAgentName = Form.useWatch('agentName', form)
  const selectedAgent = useMemo(
    () => agents.find((item) => item.name === selectedAgentName) || (selectedAgentName === 'leader_agent' ? { name: 'leader_agent', role: 'Leader 自动路由' } : null),
    [agents, selectedAgentName],
  )

  const accountOptions = useMemo(() => (
    accounts.map((item) => ({
      value: item.id,
      label: `${item.accountName} · ${item.environmentText || item.environment}`,
    }))
  ), [accounts])

  const knowledgeOptions = useMemo(() => (
    knowledges.map((item) => ({
      value: item.id,
      label: textValue(item.name, item.knowledge_name, item.title, item.id),
    }))
  ), [knowledges])

  const agentOptions = useMemo(() => ([
    { value: 'leader_agent', label: 'Leader 自动路由' },
    ...agents.filter((item) => item.name !== 'leader_agent').map((item) => ({
      value: item.name,
      label: `${item.role || item.name} · ${item.name}`,
      disabled: !isAgentEnabled(item),
    })),
  ]), [agents])

  const getAgentBoundModel = useCallback((agentName) => (
    agentModelBindings[agentName || 'leader_agent'] || ''
  ), [agentModelBindings])

  const getModelOptionsForAgent = useCallback((agent) => {
    const requiredModalities = getAgentRequiredModelModalities(agent)
    const options = llmModelOptions.filter((item) => requiredModalities.includes(item.modality))
    const boundModel = getAgentBoundModel(agent?.name)
    if (boundModel && !options.some((item) => item.value === boundModel)) {
      return [{ value: boundModel, label: `已绑定模型 · ${boundModel}` }, ...options]
    }
    return options
  }, [getAgentBoundModel, llmModelOptions])

  const modelOptions = useMemo(() => getModelOptionsForAgent(selectedAgent), [getModelOptionsForAgent, selectedAgent])

  const applyAgentDefaultModel = useCallback((agentName) => {
    const nextAgent = agents.find((item) => item.name === agentName) || (agentName === 'leader_agent' ? { name: 'leader_agent' } : null)
    const options = getModelOptionsForAgent(nextAgent)
    const boundModel = getAgentBoundModel(agentName)
    form.setFieldsValue({
      agentName,
      llmModel: boundModel || options[0]?.value,
    })
  }, [agents, form, getAgentBoundModel, getModelOptionsForAgent])

  const loadKnowledges = useCallback(async (accountId, preferredKnowledgeId) => {
    if (!accountId) {
      setKnowledges([])
      form.setFieldsValue({ knowledgeId: undefined })
      return
    }
    try {
      const res = await getMaxKbKnowledges(accountId, { page: 1, page_size: 100 })
      const rows = normalizePage(res.data).records
      setKnowledges(rows)
      const selectedKnowledge = rows.find((item) => String(item.id) === String(preferredKnowledgeId)) || rows[0]
      form.setFieldsValue({ knowledgeId: selectedKnowledge?.id })
    } catch (error) {
      setKnowledges([])
      message.error(error.message || '知识库加载失败')
    }
  }, [form])

  const loadCacheStats = useCallback(async () => {
    try {
      const res = await getKnowledgeChatCacheStats()
      setCacheStats(res.data || null)
    } catch {
      setCacheStats(null)
    }
  }, [])

  const loadBootstrap = useCallback(async () => {
    setBootstrapLoading(true)
    try {
      const [accountRes, agentRes, configRes, cacheStatsRes] = await Promise.all([
        getMaxKbAccounts({ current: 1, size: 100, status: 1 }),
        getRagAgents(),
        getSystemConfigList({ current: 1, size: 500, prefixes: 'ai.service.,ai.agent-bindings.' }),
        getKnowledgeChatCacheStats().catch(() => ({ data: null })),
      ])
      const accountRows = accountRes.data?.records || []
      const agentRows = agentRes.data?.agents || []
      const configRows = configRes.data?.records || []
      setCacheStats(cacheStatsRes.data || null)
      setAccounts(accountRows)
      setAgents(agentRows)
      setAgentModelBindings(buildAgentModelBindings(configRows))
      setLlmModelOptions(buildLlmModelOptions(configRows))

      const firstAccountId = accountRows.find((item) => String(item.id) === String(initialAccountId))?.id
        || accountRows.find((item) => item.status === 1)?.id
        || accountRows[0]?.id
        || null
      setSelectedAccountId(firstAccountId)
      form.setFieldsValue({
        accountId: firstAccountId,
        agentName: 'leader_agent',
        topNumber: 5,
        similarity: 0.6,
        searchMode: 'blend',
      })
      if (firstAccountId) {
        await loadKnowledges(firstAccountId, initialKnowledgeId)
      }
    } catch (error) {
      message.error(error.message || '聊天配置加载失败')
    } finally {
      setBootstrapLoading(false)
    }
  }, [form, initialAccountId, initialKnowledgeId, loadKnowledges])

  useEffect(() => {
    loadBootstrap()
  }, [loadBootstrap])

  useEffect(() => {
    if (!selectedAgentName) return
    const boundModel = getAgentBoundModel(selectedAgentName)
    if (boundModel && !form.getFieldValue('llmModel')) {
      form.setFieldsValue({ llmModel: boundModel })
    }
  }, [form, getAgentBoundModel, selectedAgentName])

  const handleAccountChange = async (value) => {
    setSelectedAccountId(value)
    setMessages([])
    setSessionId('')
    await loadKnowledges(value)
  }

  const handleClearCache = async () => {
    try {
      await clearKnowledgeChatCache()
      message.success('检索缓存已清空')
      await loadCacheStats()
    } catch (error) {
      message.error(error.message || '清空缓存失败')
    }
  }

  const handleSend = async () => {
    const values = await form.validateFields()
    const question = String(values.question || '').trim()
    if (!question) return

    const userMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: question,
    }
    setMessages((prev) => [...prev, userMessage])
    form.setFieldsValue({ question: '' })
    setLoading(true)

    try {
      const res = await runKnowledgeAgentChat({
        accountId: values.accountId,
        knowledgeId: values.knowledgeId,
        question,
        sessionId: sessionId || undefined,
        agentName: values.agentName || 'leader_agent',
        llmModel: values.llmModel || undefined,
        topNumber: values.topNumber,
        similarity: values.similarity,
        searchMode: values.searchMode,
      })
      const data = res.data || {}
      setSessionId(data.sessionId || sessionId)
      setMessages((prev) => [
        ...prev,
        {
          id: `assistant-${Date.now()}`,
          role: 'assistant',
          content: data.answer || '智能体没有返回可用内容。',
          answerType: data.answerType || 'markdown',
          agentName: data.agentName,
          model: data.model,
          references: data.references || [],
          retrievalCache: data.retrievalCache || null,
          metadata: data.metadata || {},
        },
      ])
      await loadCacheStats()
    } catch (error) {
      setMessages((prev) => [
        ...prev,
        {
          id: `assistant-error-${Date.now()}`,
          role: 'assistant',
          isError: true,
          content: error.message || '知识库聊天失败',
        },
      ])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="knowledge-chat-page">
      <div className="knowledge-chat-head">
        <div>
          <Title level={3}>知识库聊天</Title>
          <Text type="secondary">Java 先调用 MaxKB 召回资料，再调用你的系统智能体生成回答。</Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={loadBootstrap} loading={bootstrapLoading}>
          刷新配置
        </Button>
      </div>

      <Spin spinning={bootstrapLoading}>
        <div className="knowledge-chat-grid">
          <Card className="knowledge-chat-settings" title="聊天配置">
            <Form
              form={form}
              layout="vertical"
              initialValues={{
                agentName: 'leader_agent',
                topNumber: 5,
                similarity: 0.6,
                searchMode: 'blend',
              }}
            >
              <Form.Item name="accountId" label="MaxKB 账号" rules={[{ required: true, message: '请选择 MaxKB 账号' }]}>
                <Select
                  options={accountOptions}
                  placeholder="选择账号"
                  onChange={handleAccountChange}
                />
              </Form.Item>
              <Form.Item name="knowledgeId" label="知识库" rules={[{ required: true, message: '请选择知识库' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={knowledgeOptions}
                  placeholder="选择知识库"
                  disabled={!selectedAccountId}
                />
              </Form.Item>
              <Form.Item name="agentName" label="回答智能体">
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={agentOptions}
                  onChange={applyAgentDefaultModel}
                />
              </Form.Item>
              <Alert
                className="knowledge-chat-alert"
                type={isAgentEnabled(selectedAgent) ? 'info' : 'warning'}
                showIcon
                message={isAgentEnabled(selectedAgent)
                  ? `当前智能体需要模型：${getAgentModelRequirementText(selectedAgent)}`
                  : '该智能体当前已关闭，请到智能体设置页开启。'}
              />
              <Form.Item name="llmModel" label="模型">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={modelOptions}
                  placeholder={modelOptions.length ? '默认使用智能体绑定模型' : '没有匹配的已测试模型'}
                />
              </Form.Item>
              <div className="knowledge-chat-options">
                <Form.Item name="searchMode" label="检索模式">
                  <Select options={searchModeOptions} />
                </Form.Item>
                <Form.Item name="topNumber" label="召回数量">
                  <InputNumber min={1} max={20} />
                </Form.Item>
                <Form.Item name="similarity" label="相似度">
                  <InputNumber min={0} max={2} step={0.05} />
                </Form.Item>
              </div>
              <div className="knowledge-chat-cache-card">
                <div className="knowledge-chat-cache-head">
                  <span>检索缓存</span>
                  <Tag color={Number(cacheStats?.hitRate || 0) > 0 ? 'green' : 'default'}>
                    命中率 {formatPercent(cacheStats?.hitRate)}
                  </Tag>
                </div>
                <div className="knowledge-chat-cache-grid">
                  <div>
                    <span>总请求</span>
                    <strong>{formatCount(cacheStats?.requestCount)}</strong>
                  </div>
                  <div>
                    <span>命中</span>
                    <strong>{formatCount(cacheStats?.hitCount)}</strong>
                  </div>
                  <div>
                    <span>条目</span>
                    <strong>{formatCount(cacheStats?.entryCount)}</strong>
                  </div>
                  <div>
                    <span>节省</span>
                    <strong>{formatDuration(cacheStats?.estimatedSavedMillis)}</strong>
                  </div>
                </div>
                <Button size="small" onClick={handleClearCache}>清空缓存</Button>
              </div>
            </Form>
          </Card>

          <Card
            className="knowledge-chat-console"
            title={(
              <Space>
                <RobotOutlined />
                <span>Java 知识库智能体对话</span>
                {sessionId ? <Tag color="blue">{sessionId}</Tag> : null}
              </Space>
            )}
          >
            <div className="knowledge-chat-messages">
              {!messages.length ? (
                <Empty description="选择知识库后输入问题，答案会由你的智能体生成" />
              ) : messages.map((item) => (
                <div className={`knowledge-chat-message is-${item.role} ${item.isError ? 'is-error' : ''}`} key={item.id}>
                  <div className="knowledge-chat-message-meta">
                    <Tag color={item.role === 'user' ? 'blue' : item.isError ? 'red' : 'green'}>
                      {item.role === 'user' ? '你' : '智能体'}
                    </Tag>
                    {item.agentName ? <Tag>{item.agentName}</Tag> : null}
                    {item.model ? <Tag color="geekblue">{item.model}</Tag> : null}
                    {item.retrievalCache ? (
                      <Tag color={item.retrievalCache.cacheHit ? 'green' : 'default'}>
                        检索缓存：{item.retrievalCache.cacheHit ? '命中' : '未命中'}
                      </Tag>
                    ) : null}
                  </div>
                  <div className="knowledge-chat-answer">{item.content}</div>
                  {item.role === 'assistant' && !item.isError ? (
                    <KnowledgeSourcePanel references={item.references || []} />
                  ) : null}
                </div>
              ))}
              {loading ? <Spin className="knowledge-chat-loading" /> : null}
            </div>

            <Form form={form} layout="vertical" className="knowledge-chat-input">
              <Form.Item name="question" rules={[{ required: true, message: '请输入问题' }]}>
                <TextArea
                  rows={3}
                  placeholder="输入要问知识库的问题"
                  onPressEnter={(event) => {
                    if (!event.shiftKey) {
                      event.preventDefault()
                      handleSend()
                    }
                  }}
                />
              </Form.Item>
              <Button type="primary" icon={<SendOutlined />} loading={loading} onClick={handleSend}>
                发送
              </Button>
            </Form>
          </Card>
        </div>
      </Spin>
    </div>
  )
}

export default KnowledgeChat
