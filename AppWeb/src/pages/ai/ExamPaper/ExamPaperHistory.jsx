import { useEffect, useRef, useState } from 'react'
import { DownloadOutlined, EyeOutlined } from '@ant-design/icons'
import { Button, Card, Descriptions, Drawer, Empty, Space, Table, Tag, Typography, message } from 'antd'
import { downloadExamPaper, getExamPaperDetail, getExamPaperList } from '../../../api/examPaper'

const { Text, Title } = Typography

const selectionModeLabels = { MANUAL: '手工选题', RANDOM: '随机选题' }
const orientationLabels = { PORTRAIT: '纵向', LANDSCAPE: '横向' }

const formatTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function ExamPaperHistory({ refreshKey = 0 }) {
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [detail, setDetail] = useState(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [downloadKeys, setDownloadKeys] = useState(() => new Set())
  const downloadKeysRef = useRef(new Set())
  const listRequestId = useRef(0)
  const detailRequestId = useRef(0)

  const fetchPapers = async (current = 1, pageSize = pagination.pageSize) => {
    const requestId = ++listRequestId.current
    setLoading(true)
    try {
      const response = await getExamPaperList({ current, size: pageSize })
      const data = response.data || {}
      if (requestId !== listRequestId.current) return
      setRows(data.records || [])
      setPagination({
        current: data.current ?? current,
        pageSize: data.size ?? pageSize,
        total: data.total ?? 0,
      })
    } catch (error) {
      if (requestId === listRequestId.current) message.error(error.message || '历史试卷加载失败')
    } finally {
      if (requestId === listRequestId.current) setLoading(false)
    }
  }

  useEffect(() => {
    fetchPapers(1)
    return () => {
      listRequestId.current += 1
      detailRequestId.current += 1
    }
    // refreshKey is the explicit signal to reload after a successful creation.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshKey])

  const openDetail = async (record) => {
    const requestId = ++detailRequestId.current
    setDetail(null)
    setDetailOpen(true)
    setDetailLoading(true)
    try {
      const response = await getExamPaperDetail(record.id)
      if (requestId === detailRequestId.current) setDetail(response.data || null)
    } catch (error) {
      if (requestId === detailRequestId.current) message.error(error.message || '试卷详情加载失败')
    } finally {
      if (requestId === detailRequestId.current) setDetailLoading(false)
    }
  }

  const handleDownload = async (record, content) => {
    const key = `${record.id}:${content}`
    if (downloadKeysRef.current.has(key)) return
    downloadKeysRef.current.add(key)
    setDownloadKeys(new Set(downloadKeysRef.current))
    try {
      await downloadExamPaper(record.id, content)
    } catch (error) {
      message.error(error.message || (content === 'answer' ? '答案下载失败' : '试卷下载失败'))
    } finally {
      downloadKeysRef.current.delete(key)
      setDownloadKeys(new Set(downloadKeysRef.current))
    }
  }

  const columns = [
    { title: '标题', dataIndex: 'title', width: 220, ellipsis: true },
    {
      title: '版式', width: 150,
      render: (_, record) => `${record.pageSize || '-'} · ${orientationLabels[record.orientation] || record.orientation || '-'} · ${record.columnsCount || '-'} 栏`,
    },
    {
      title: '选题方式', dataIndex: 'selectionMode', width: 110,
      render: (value) => <Tag color={value === 'RANDOM' ? 'gold' : 'blue'}>{selectionModeLabels[value] || value || '-'}</Tag>,
    },
    { title: '题目数', dataIndex: 'questionCount', width: 90, render: (value) => `${value ?? 0} 题` },
    { title: '总分', dataIndex: 'totalScore', width: 90, render: (value) => `${value ?? 0} 分` },
    { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatTime },
    {
      title: '操作', fixed: 'right', width: 320,
      render: (_, record) => (
        <Space className="exam-paper-actions" wrap>
          <Button icon={<EyeOutlined />} onClick={() => openDetail(record)}>查看详情</Button>
          <Button type="primary" icon={<DownloadOutlined />} loading={downloadKeys.has(`${record.id}:paper`)} onClick={() => handleDownload(record, 'paper')}>下载试卷</Button>
          <Button icon={<DownloadOutlined />} loading={downloadKeys.has(`${record.id}:answer`)} onClick={() => handleDownload(record, 'answer')}>下载答案</Button>
        </Space>
      ),
    },
  ]

  const snapshotRows = [...(detail?.questions || [])].sort((left, right) => (
    (left.sortOrder ?? 0) - (right.sortOrder ?? 0) || (left.id ?? 0) - (right.id ?? 0)
  ))
  const snapshotColumns = [
    { title: '顺序', dataIndex: 'sortOrder', width: 70 },
    { title: '题型', dataIndex: 'type', width: 120, render: (value) => <Tag color="blue">{value || '-'}</Tag> },
    { title: '题干快照', dataIndex: 'stem', ellipsis: true },
    { title: '分值', dataIndex: 'score', width: 90, render: (value) => `${value ?? 0} 分` },
  ]

  return (
    <Card className="exam-paper-card exam-paper-history">
      <div className="exam-paper-history-heading">
        <div><Title level={3}>生成历史</Title><Text type="secondary">仅展示当前账号创建的试卷，可查看保存时的题目快照并再次下载。</Text></div>
      </div>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        locale={{ emptyText: <Empty description="暂无已生成试卷" /> }}
        pagination={{ ...pagination, showSizeChanger: true, showTotal: (total) => `共 ${total} 份试卷` }}
        onChange={(next) => fetchPapers(next.current, next.pageSize)}
        scroll={{ x: 1180 }}
      />

      <Drawer
        title="试卷详情"
        width="min(920px, 100vw)"
        open={detailOpen}
        loading={detailLoading}
        onClose={() => {
          detailRequestId.current += 1
          setDetailOpen(false)
        }}
      >
        {detail && (
          <Space direction="vertical" size="large" className="exam-paper-detail">
            <Descriptions bordered column={{ xs: 1, sm: 2 }}>
              <Descriptions.Item label="标题" span={2}>{detail.title || '-'}</Descriptions.Item>
              <Descriptions.Item label="版式">{detail.pageSize || '-'} · {orientationLabels[detail.orientation] || detail.orientation || '-'} · {detail.columnsCount || '-'} 栏</Descriptions.Item>
              <Descriptions.Item label="选题方式">{selectionModeLabels[detail.selectionMode] || detail.selectionMode || '-'}</Descriptions.Item>
              <Descriptions.Item label="题目数">{detail.questionCount ?? 0} 题</Descriptions.Item>
              <Descriptions.Item label="总分">{detail.totalScore ?? 0} 分</Descriptions.Item>
              <Descriptions.Item label="创建时间" span={2}>{formatTime(detail.createTime)}</Descriptions.Item>
            </Descriptions>
            <Table rowKey="id" columns={snapshotColumns} dataSource={snapshotRows} pagination={false} scroll={{ x: 640 }} />
          </Space>
        )}
      </Drawer>
    </Card>
  )
}

export default ExamPaperHistory
