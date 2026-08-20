import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Empty, Input, List, Spin, Tag, Typography, message } from 'antd'
import { HistoryOutlined, PlusOutlined, ReloadOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons'
import { getLeaderSessionDetail, getLeaderSessions, streamLeaderAgent } from '../../../api/aiLeader'
import './AiConversation.css'

const { Text, Title } = Typography
const { TextArea } = Input

const normalizeMessage = (item, index) => ({
  id: item.id || `${item.role}-${index}`,
  role: item.role === 'user' ? 'user' : 'assistant',
  content: item.content || item.answer || '',
  answerType: item.answerType || 'text',
  agentName: item.agentName || 'leader_agent',
  model: item.model || '',
  retrievalMeta: item.retrievalMeta || {},
  resources: Array.isArray(item.resources) ? item.resources : [],
  status: item.status || 'completed',
  steps: Array.isArray(item.steps) ? item.steps : [],
})

function AiConversation() {
  const [sessionId, setSessionId] = useState('')
  const [sessions, setSessions] = useState([])
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')

  const loadSessions = useCallback(async () => {
    setHistoryLoading(true)
    try {
      const response = await getLeaderSessions({ pageNum: 1, pageSize: 50 })
      setSessions(response.data?.records || [])
    } catch (requestError) {
      setError(requestError?.message || '加载 AI 会话历史失败')
    } finally {
      setHistoryLoading(false)
    }
  }, [])

  const openSession = useCallback(async (nextSessionId) => {
    if (!nextSessionId) return
    setDetailLoading(true)
    setError('')
    try {
      const response = await getLeaderSessionDetail(nextSessionId)
      const data = response.data || {}
      setSessionId(data.session?.sessionId || nextSessionId)
      setMessages((data.messages || []).map(normalizeMessage))
    } catch (requestError) {
      setError(requestError?.message || '加载会话内容失败')
    } finally {
      setDetailLoading(false)
    }
  }, [])

  useEffect(() => {
    // 页面进入时同步 APP 端的会话历史。
    loadSessions()
  }, [loadSessions])

  const currentSessionTitle = useMemo(() => {
    const current = sessions.find((item) => item.sessionId === sessionId)
    return current?.title || '智能助手'
  }, [sessionId, sessions])

  const startNewConversation = () => {
    if (loading) return
    setSessionId('')
    setMessages([])
    setInput('')
    setError('')
  }

  const sendMessage = async () => {
    const content = input.trim()
    if (!content || loading) return
    setInput('')
    setError('')
    const thinkingId = `assistant-${Date.now()}`
    setMessages((current) => [...current,
      { id: `user-${Date.now()}`, role: 'user', content },
      { id: thinkingId, role: 'assistant', content: '', status: 'running', steps: ['已提交给智能助手，正在准备处理'] },
    ])
    setLoading(true)
    try {
      const streamTask = streamLeaderAgent({
        sessionId: sessionId || undefined,
        input: content,
      }, {
        onSession: (payload) => {
          setSessionId(payload?.sessionId || sessionId)
          setMessages((current) => current.map((item) => item.id === thinkingId
            ? { ...item, steps: [...item.steps, `已建立会话，使用 ${payload?.agentName || 'Leader'}${payload?.model ? ` · ${payload.model}` : ''}`] }
            : item))
        },
        onEvent: (eventName, payload) => {
          if (eventName === 'generation_start') {
            setMessages((current) => current.map((item) => item.id === thinkingId
              ? { ...item, content: payload?.answer || '', status: 'running', steps: [...item.steps, `开始生成${payload?.outputType ? ` ${payload.outputType}` : '回答'}`] }
              : item))
          } else if (eventName === 'tool_start') {
            setMessages((current) => current.map((item) => item.id === thinkingId
              ? { ...item, steps: [...item.steps, payload?.message || '正在调用工具'] }
              : item))
          }
        },
        onSearch: (payload) => {
          const count = Array.isArray(payload?.matchedResults) ? payload.matchedResults.length : null
          setMessages((current) => current.map((item) => item.id === thinkingId
            ? { ...item, steps: [...item.steps, `正在检索${payload?.searchKeyword ? `：${payload.searchKeyword}` : ''}${count === null ? '' : `，找到 ${count} 条结果`}`] }
            : item))
        },
        onDelta: (delta) => {
          setMessages((current) => current.map((item) => item.id === thinkingId
            ? { ...item, content: `${item.content || ''}${delta}` }
            : item))
        },
        onDone: (payload) => {
          setSessionId(payload?.sessionId || sessionId)
          setMessages((current) => current.map((item) => item.id === thinkingId
            ? normalizeMessage({ ...payload, id: thinkingId, role: 'assistant', content: payload?.answer || item.content, steps: [...item.steps, '回答完成'] }, current.indexOf(item))
            : item))
        },
        onError: (payload) => { throw new Error(payload?.message || '流式请求失败') },
      })
      await streamTask
      await loadSessions()
    } catch (requestError) {
      if (requestError?.name !== 'AbortError') {
        setMessages((current) => current.map((item) => item.id === thinkingId
          ? { ...item, status: 'error', steps: [...item.steps, requestError?.message || '处理失败'] }
          : item))
        setError(requestError?.message || 'AI 回复失败，请稍后重试')
        message.error(requestError?.message || 'AI 回复失败')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="ai-conversation-page">
      <aside className="ai-conversation-history">
        <div className="ai-conversation-history-head">
          <div><Text className="ai-conversation-kicker">AI ASSISTANT</Text><Title level={3}>AI 会话</Title></div>
          <Button type="text" icon={<ReloadOutlined />} onClick={loadSessions} loading={historyLoading} aria-label="刷新历史" />
        </div>
        <Button block icon={<PlusOutlined />} onClick={startNewConversation} disabled={loading}>新建对话</Button>
        <div className="ai-conversation-history-title"><HistoryOutlined /> 历史会话</div>
        <Spin spinning={historyLoading}>
          {sessions.length ? (
            <List
              className="ai-conversation-session-list"
              dataSource={sessions}
              renderItem={(item) => (
                <List.Item
                  className={item.sessionId === sessionId ? 'is-active' : ''}
                  onClick={() => openSession(item.sessionId)}
                >
                  <div className="ai-conversation-session-item">
                    <strong>{item.title || item.lastMessage || '未命名会话'}</strong>
                    <Text type="secondary">{item.messageCount || 0} 条消息</Text>
                  </div>
                </List.Item>
              )}
            />
          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有 AI 会话" />}
        </Spin>
      </aside>

      <section className="ai-conversation-main">
        <header className="ai-conversation-header">
          <div className="ai-conversation-title"><span className="ai-conversation-avatar"><RobotOutlined /></span><div><Title level={3}>{currentSessionTitle}</Title><Text type="secondary">固定接入 Leader 智能体，支持校园业务问答与多智能体路由</Text></div></div>
          <Tag color="blue">Leader</Tag>
        </header>

        <div className="ai-conversation-messages">
          {detailLoading ? <Spin /> : messages.length ? messages.map((item) => (
            <div key={item.id} className={`ai-conversation-message ai-conversation-message--${item.role}`}>
              <div className="ai-conversation-message-label">{item.role === 'user' ? '我' : '智能助手'}</div>
              <div className="ai-conversation-bubble">
                {item.role === 'assistant' && item.steps?.length ? <div className="ai-conversation-steps">{item.steps.map((step, stepIndex) => <div key={`${item.id}-step-${stepIndex}`} className="ai-conversation-step"><span className="ai-conversation-step-dot" />{step}</div>)}</div> : null}
                {item.content || (item.status === 'running' ? '正在处理…' : '智能助手没有返回可用内容。')}
              </div>
              {item.role === 'assistant' && item.agentName ? <Text type="secondary" className="ai-conversation-meta">{item.agentName}{item.model ? ` · ${item.model}` : ''}</Text> : null}
            </div>
          )) : <div className="ai-conversation-empty"><RobotOutlined /><Title level={4}>和智能助手开始聊吧</Title><Text type="secondary">这里会保存成一条历史会话，下次可以继续打开。</Text></div>}
        </div>

        <div className="ai-conversation-composer">
          {error ? <Alert type="error" showIcon message={error} closable onClose={() => setError('')} /> : null}
          <div className="ai-conversation-input-row">
            <TextArea value={input} onChange={(event) => setInput(event.target.value)} onPressEnter={(event) => { if (!event.shiftKey) { event.preventDefault(); sendMessage() } }} placeholder="输入你想咨询的内容，Enter 发送，Shift + Enter 换行" autoSize={{ minRows: 2, maxRows: 6 }} disabled={loading} />
            <Button type="primary" icon={<SendOutlined />} onClick={sendMessage} loading={loading} disabled={!input.trim()}>发送</Button>
          </div>
        </div>
      </section>
    </div>
  )
}

export default AiConversation
