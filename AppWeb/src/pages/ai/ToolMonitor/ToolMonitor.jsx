import { useCallback, useEffect, useState } from 'react'
import { Badge, Card, Empty, Space, Spin, Table, Tag, Timeline, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { getToolMonitorTools, getToolMonitorRecords } from '../../../api/rag'
import './ToolMonitor.css'

const { Text, Title } = Typography

const CATEGORY_COLORS = {
  campus_service: 'blue',
  structured_query: 'cyan',
  content_export: 'green',
  diagram_export: 'purple',
  presentation_generation: 'orange',
  vision_understanding: 'magenta',
  internal_routing: 'default',
}

const formatTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function ToolMonitor() {
  const [tools, setTools] = useState([])
  const [records, setRecords] = useState([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [loadingTools, setLoadingTools] = useState(false)
  const [loadingRecords, setLoadingRecords] = useState(false)

  const fetchTools = useCallback(async () => {
    setLoadingTools(true)
    try {
      const res = await getToolMonitorTools()
      if (res?.code === 200) {
        setTools(Array.isArray(res.data) ? res.data : [])
      }
    } finally {
      setLoadingTools(false)
    }
  }, [])

  const fetchRecords = useCallback(async (page = 1) => {
    setLoadingRecords(true)
    try {
      const res = await getToolMonitorRecords({ pageNum: page, pageSize: 20 })
      if (res?.code === 200 && res.data) {
        setRecords(Array.isArray(res.data.records) ? res.data.records : [])
        setTotal(res.data.total || 0)
        setPageNum(page)
      }
    } finally {
      setLoadingRecords(false)
    }
  }, [])

  useEffect(() => {
    fetchTools()
    fetchRecords(1)
  }, [fetchTools, fetchRecords])

  const toolColumns = [
    {
      title: '工具名称',
      dataIndex: 'displayName',
      key: 'displayName',
      width: 280,
    },
    {
      title: '分类',
      dataIndex: 'categoryLabel',
      key: 'categoryLabel',
      width: 120,
      render: (text, record) => (
        <Tag color={CATEGORY_COLORS[record.category] || 'default'}>{text || '-'}</Tag>
      ),
    },
    {
      title: '用途说明',
      dataIndex: 'purpose',
      key: 'purpose',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 100,
      render: (enabled) => (
        <Badge status={enabled ? 'success' : 'default'} text={enabled ? '已启用' : '已禁用'} />
      ),
    },
  ]

  const recordColumns = [
    {
      title: '对话时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: formatTime,
    },
    {
      title: '是否调用工具',
      dataIndex: 'toolCalled',
      key: 'toolCalled',
      width: 120,
      render: (called) => (
        <Tag color={called ? 'success' : 'default'}>{called ? '已调用' : '未调用'}</Tag>
      ),
    },
    {
      title: '调用工具',
      dataIndex: 'toolDisplayName',
      key: 'toolDisplayName',
      width: 220,
      render: (text, record) => record.toolCalled ? (text || record.toolName || '-') : '-',
    },
    {
      title: '用户输入',
      dataIndex: 'userInput',
      key: 'userInput',
      ellipsis: true,
    },
    {
      title: '意图',
      dataIndex: 'intent',
      key: 'intent',
      width: 140,
      render: (text) => text ? <Tag>{text}</Tag> : '-',
    },
  ]

  const expandedRowRender = (record) => {
    const candidateTools = record.candidateTools || []
    if (candidateTools.length === 0) {
      return <Empty description="暂无候选工具打分数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
    }
    const columns = [
      {
        title: '工具名称',
        dataIndex: 'displayName',
        key: 'displayName',
        width: 240,
        render: (text, row) => text || row.zhName || row.name || '-',
      },
      {
        title: '匹配分数',
        dataIndex: 'matchScore',
        key: 'matchScore',
        width: 120,
        render: (score) => {
          const num = Number(score) || 0
          const color = num >= 0.1 ? 'green' : num > 0 ? 'orange' : 'default'
          return <Text strong style={{ color }}>{num.toFixed(3)}</Text>
        },
        sorter: (a, b) => (Number(a.matchScore) || 0) - (Number(b.matchScore) || 0),
      },
      {
        title: '命中关键词',
        dataIndex: 'matchedKeywords',
        key: 'matchedKeywords',
        render: (keywords) => {
          if (!Array.isArray(keywords) || keywords.length === 0) return '-'
          return (
            <Space size={[4, 4]} wrap>
              {keywords.map((kw, idx) => <Tag key={idx} color="blue">{kw}</Tag>)}
            </Space>
          )
        },
      },
    ]
    return (
      <Table
        columns={columns}
        dataSource={candidateTools}
        rowKey={(row) => row.name || row.displayName || Math.random()}
        pagination={false}
        size="small"
        className="tool-monitor-candidate-table"
      />
    )
  }

  const enabledCount = tools.filter((t) => t.enabled).length
  const disabledCount = tools.length - enabledCount

  return (
    <div className="tool-monitor-page">
      <section className="tool-monitor-toolbar">
        <div className="tool-monitor-heading">
          <Title level={4} style={{ margin: 0 }}>工具调用监控</Title>
          <Text type="secondary">查看工具启用状态与每次工具调用时的意图识别打分情况</Text>
        </div>
        <Space>
          <Badge count={enabledCount} style={{ backgroundColor: '#52c41a' }} overflowCount={999}>
            <Tag color="success">已启用</Tag>
          </Badge>
          <Badge count={disabledCount} style={{ backgroundColor: '#d9d9d9' }} overflowCount={999}>
            <Tag>已禁用</Tag>
          </Badge>
        </Space>
      </section>

      <Card
        title="AI 对话打分记录"
        className="tool-monitor-card"
        extra={
          <a onClick={() => fetchRecords(1)} style={{ cursor: 'pointer' }}>
            <ReloadOutlined /> 刷新
          </a>
        }
      >
        <Table
          columns={recordColumns}
          dataSource={records}
          rowKey="id"
          loading={loadingRecords}
          expandable={{
            expandedRowRender,
            rowExpandable: () => true,
          }}
          pagination={{
            current: pageNum,
            pageSize: 20,
            total,
            onChange: (page) => fetchRecords(page),
            showTotal: (t) => `共 ${t} 条记录`,
            showSizeChanger: false,
          }}
          locale={{ emptyText: <Empty description="暂无 AI 对话记录" /> }}
        />
      </Card>

      <Card
        title="工具列表"
        className="tool-monitor-card"
        extra={
          <a onClick={fetchTools} style={{ cursor: 'pointer' }}>
            <ReloadOutlined /> 刷新
          </a>
        }
      >
        <Table
          columns={toolColumns}
          dataSource={tools}
          rowKey="name"
          loading={loadingTools}
          pagination={false}
          size="small"
          locale={{ emptyText: <Empty description="暂无工具数据" /> }}
        />
      </Card>
    </div>
  )
}

export default ToolMonitor
