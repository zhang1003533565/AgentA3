import { useState } from 'react'
import {
  Alert,
  Button,
  Divider,
  Drawer,
  Empty,
  Input,
  Space,
  Tag,
  Typography,
} from 'antd'
import { SendOutlined } from '@ant-design/icons'
import { generateActivityDraft } from '../../../api/aiActivity'

const { TextArea } = Input
const { Text, Title } = Typography

const FIELD_LABELS = {
  title: '活动标题',
  organizerName: '主办方',
  coverImage: '封面图片',
  categoryId: '活动分类',
  maxPeople: '人数上限',
  location: '活动地点',
  startTime: '活动开始时间',
  endTime: '活动结束时间',
  signupEndTime: '报名截止时间',
  content: '活动详情',
}

const FIELD_ORDER = [
  'title',
  'categoryId',
  'organizerName',
  'maxPeople',
  'location',
  'startTime',
  'endTime',
  'signupEndTime',
  'content',
  'coverImage',
]

const EMPTY_DRAFT = { activity: null, generatedFields: [] }

function ActivityAiDrawer({ open, onClose, onFill }) {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [draft, setDraft] = useState(EMPTY_DRAFT)

  const handleSend = async () => {
    const text = input.trim()
    if (!text || loading) return
    setMessages((prev) => [...prev, { role: 'user', content: text }])
    setInput('')
    setLoading(true)
    try {
      const res = await generateActivityDraft({
        input: text,
        activityDraft: draft.activity || {},
        generatedFields: draft.generatedFields || [],
      })
      const data = res?.data
      setResult(data)
      setDraft({
        activity: data?.activity || null,
        generatedFields: data?.generatedFields || [],
      })
      setMessages((prev) => [...prev, { role: 'assistant', content: data?.reply || 'AI 没有返回回复' }])
    } catch (error) {
      const errorMessage = error?.message || 'AI 生成失败，请稍后重试'
      setMessages((prev) => [...prev, { role: 'assistant', content: errorMessage }])
    } finally {
      setLoading(false)
    }
  }

  const recognizedFields = result?.activity
    ? Object.entries(result.activity).filter(
        ([, value]) => value !== null && value !== undefined && value !== '',
      )
    : []
  const canFill = recognizedFields.length > 0
  const generatedSet = new Set(result?.generatedFields || [])

  const getFieldValue = (field) => {
    if (field === 'coverImage') return null
    const value = result?.activity?.[field]
    return value !== null && value !== undefined && value !== '' ? value : null
  }

  const getFieldStatus = (field) => {
    if (field === 'coverImage') {
      return { label: '暂未支持', color: 'default', generated: false }
    }
    if (getFieldValue(field) === null) {
      return { label: '待补全', color: 'default', generated: false }
    }
    if (generatedSet.has(field)) {
      return { label: 'AI生成待确认', color: 'orange', generated: true }
    }
    return { label: '已识别', color: 'green', generated: false }
  }

  return (
    <Drawer
      title="AI活动发布助手"
      width={480}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      <Title level={5}>AI 辅助创建活动</Title>
      <Text type="secondary">
        请用自然语言描述活动，例如：我要办一个校园歌手大赛，9月10日下午2点到6点，在大学生活动中心，500人，分类文艺活动。信息不足时我会继续追问，可分多次补充。
      </Text>

      <Divider plain>对话</Divider>

      <div
        style={{
          maxHeight: 260,
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          marginBottom: 12,
        }}
      >
        {messages.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="等待你的活动描述" />
        ) : (
          messages.map((message, index) => (
            <div
              key={index}
              style={{
                alignSelf: message.role === 'user' ? 'flex-end' : 'flex-start',
                maxWidth: '86%',
                padding: '8px 12px',
                borderRadius: 8,
                background: message.role === 'user' ? '#e6f4ff' : '#f5f5f5',
                whiteSpace: 'pre-wrap',
              }}
            >
              <Text>{message.content}</Text>
            </div>
          ))
        )}
        {loading && (
          <Text type="secondary" style={{ alignSelf: 'flex-start' }}>
            AI 正在生成…
          </Text>
        )}
      </div>

      <Space.Compact style={{ width: '100%' }}>
        <TextArea
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="输入活动描述，例如：我要办一个读书分享会，下周六下午3点，在图书馆"
          autoSize={{ minRows: 2, maxRows: 5 }}
          disabled={loading}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          loading={loading}
          disabled={!input.trim()}
          onClick={handleSend}
        >
          发送
        </Button>
      </Space.Compact>

      <Divider plain>活动信息</Divider>

      <div
        style={{
          border: '1px solid #f0f0f0',
          borderRadius: 8,
          padding: '4px 12px',
          marginBottom: 12,
        }}
      >
        {FIELD_ORDER.map((field) => {
          const value = getFieldValue(field)
          const status = getFieldStatus(field)
          return (
            <div
              key={field}
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: 8,
                padding: '8px 0',
                borderBottom: '1px solid #f5f5f5',
              }}
            >
              <Text style={{ width: 88, flexShrink: 0 }}>{FIELD_LABELS[field] || field}</Text>
              <div style={{ flex: 1, minWidth: 0 }}>
                {field === 'coverImage' ? (
                  <Text type="secondary">暂未支持</Text>
                ) : value === null ? (
                  <Text type="secondary">—</Text>
                ) : (
                  <Text ellipsis={{ tooltip: String(value) }} style={{ width: '100%' }}>
                    {String(value)}
                  </Text>
                )}
              </div>
              <div style={{ flexShrink: 0, display: 'flex', gap: 4 }}>
                {status.generated && <Tag color="green">已识别</Tag>}
                <Tag color={status.color}>{status.label}</Tag>
              </div>
            </div>
          )
        })}
      </div>

      {result?.warnings && result.warnings.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={result.warnings.map((warning, index) => (
            <div key={index}>{warning}</div>
          ))}
        />
      )}

      <Button
        type="primary"
        block
        disabled={!canFill}
        onClick={() => onFill && onFill(result?.activity)}
      >
        填入活动表单
      </Button>
    </Drawer>
  )
}

export default ActivityAiDrawer
