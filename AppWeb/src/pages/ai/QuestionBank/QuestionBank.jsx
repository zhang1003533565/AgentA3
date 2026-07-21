import { useEffect, useMemo, useState } from 'react'
import { Breadcrumb, Button, Card, Descriptions, Drawer, Empty, Form, Input, Select, Space, Table, Tag, Typography, message } from 'antd'
import {
  PlusOutlined,
  DownloadOutlined,
  ImportOutlined,
  DeleteOutlined,
  UnorderedListOutlined,
  AppstoreOutlined,
  ReloadOutlined,
  SearchOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import { getExamQuestionDetail, getExamQuestionList } from '../../../api/examQuestion'
import './QuestionBank.css'

const { Text } = Typography

// 题型选项（严格按截图：选择/判断/填空/简答/计算）
const questionTypeOptions = [
  { value: 'single_choice', label: '选择题' },
  { value: 'true_false', label: '判断题' },
  { value: 'fill_blank', label: '填空题' },
  { value: 'short_answer', label: '简答题' },
  { value: 'calculation', label: '计算题' },
]

// 题型展示映射
const questionTypeLabels = {
  single_choice: '选择题',
  multiple_choice: '选择题',
  true_false: '判断题',
  fill_blank: '填空题',
  short_answer: '简答题',
  essay: '简答题',
  calculation: '计算题',
}

// 题型标签样式类
const questionTypeTagClass = {
  选择题: 'qb-tag-type-choice',
  判断题: 'qb-tag-type-judge',
  填空题: 'qb-tag-type-blank',
  简答题: 'qb-tag-type-answer',
  计算题: 'qb-tag-type-calc',
}

const difficultyLabels = {
  easy: '简单',
  medium: '中等',
  hard: '困难',
}

const difficultyTagClass = {
  easy: 'qb-tag-diff-easy',
  medium: 'qb-tag-diff-medium',
  hard: 'qb-tag-diff-hard',
}

const formatJson = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

const listText = (value) => {
  if (!Array.isArray(value) || !value.length) return '-'
  return value.join('、')
}

function QuestionBank() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [rows, setRows] = useState([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [detailOpen, setDetailOpen] = useState(false)
  const [detail, setDetail] = useState(null)
  const [viewMode, setViewMode] = useState('list')
  const [selectedRowKeys, setSelectedRowKeys] = useState([])

  const fetchList = async (params = {}) => {
    const values = form.getFieldsValue()
    const current = params.current ?? pagination.current
    const size = params.pageSize ?? pagination.pageSize
    setLoading(true)
    try {
      const res = await getExamQuestionList({
        current,
        size,
        type: values.type || undefined,
        difficulty: values.difficulty || undefined,
        keyword: values.keyword || undefined,
      })
      const data = res.data || {}
      setRows(data.records || [])
      setPagination({
        current: data.page || current,
        pageSize: data.size || size,
        total: data.total || 0,
      })
    } catch (error) {
      message.error(error.message || '题库列表加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchList({ current: 1 })
  }, [])

  const openDetail = async (id) => {
    setDetailOpen(true)
    setDetailLoading(true)
    try {
      const res = await getExamQuestionDetail(id)
      setDetail(res.data || null)
    } catch (error) {
      message.error(error.message || '题目详情加载失败')
    } finally {
      setDetailLoading(false)
    }
  }

  const columns = useMemo(() => [
    {
      title: '题型',
      dataIndex: 'type',
      width: 110,
      render: (value) => {
        const label = questionTypeLabels[value] || value
        return <span className={`qb-type-tag ${questionTypeTagClass[label] || ''}`}>{label}</span>
      },
    },
    {
      title: '题目内容',
      dataIndex: 'stem',
      ellipsis: true,
      render: (value) => <span className="qb-stem-text">{value}</span>,
    },
    {
      title: '所属题库',
      dataIndex: 'sourceTitle',
      width: 160,
      render: (value) => <span className="qb-bank-text">{value || '-'}</span>,
    },
    {
      title: '难度',
      dataIndex: 'difficulty',
      width: 100,
      render: (value) => {
        const diffLabels = { easy: '简单', medium: '中等', hard: '困难' }
        return <span className={`qb-diff-tag ${difficultyTagClass[value] || ''}`}>{diffLabels[value] || value || '-'}</span>
      },
    },
    {
      title: '操作',
      width: 160,
      render: (_, record) => (
        <Space size={16} className="qb-actions">
          <a className="qb-action-link qb-action-view" onClick={() => openDetail(record.id)}>查看</a>
          <a className="qb-action-link qb-action-edit">编辑</a>
          <a className="qb-action-link qb-action-delete">删除</a>
        </Space>
      ),
    },
  ], [])

  return (
    <div className="question-bank-page">
      {/* 面包屑 + 刷新 */}
      <div className="question-bank-header">
        <Breadcrumb>
          <Breadcrumb.Item>题库管理</Breadcrumb.Item>
          <Breadcrumb.Item><span className="qb-breadcrumb-active">题库</span></Breadcrumb.Item>
        </Breadcrumb>
        <a className="qb-refresh-top" onClick={() => fetchList()}>
          <ReloadOutlined /> 刷新
        </a>
      </div>

      {/* 筛选区域 */}
      <Card className="question-bank-card question-bank-filter-card" bordered={false}>
        <Form
          form={form}
          className="question-bank-filter"
          layout="horizontal"
          onFinish={() => fetchList({ current: 1 })}
        >
          {/* 搜索框 - 无 label */}
          <Form.Item name="keyword" colon={false}>
            <Input allowClear suffix={<SearchOutlined />} placeholder="搜索问题内容" />
          </Form.Item>

          {/* 所属题库 */}
          <Form.Item name="bank" label="所属题库">
            <Select allowClear placeholder="请选择所属题库" options={[]} />
          </Form.Item>

          {/* 题型 */}
          <Form.Item name="type" label="题型">
            <Select allowClear placeholder="请选择题型" options={questionTypeOptions} />
          </Form.Item>

          {/* 难度 */}
          <Form.Item name="difficulty" label="难度">
            <Select allowClear placeholder="请选择难度" options={[
              { value: 'easy', label: '简单' },
              { value: 'medium', label: '中等' },
              { value: 'hard', label: '困难' },
            ]} />
          </Form.Item>

          {/* 按钮组 */}
          <Form.Item className="question-bank-filter-actions">
            <Space size={12}>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />} className="qb-search-btn">
                查询
              </Button>
              <Button
                className="qb-reset-btn"
                onClick={() => {
                  form.resetFields()
                  fetchList({ current: 1 })
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      {/* 操作栏 - 仅保留视图切换 */}
      <div className="question-bank-toolbar">
        <div className="qb-toolbar-right">
          <div className="qb-view-toggle">
            <button
              type="button"
              className={`qb-view-btn ${viewMode === 'list' ? 'is-active' : ''}`}
              onClick={() => setViewMode('list')}
              aria-label="列表视图"
            >
              <UnorderedListOutlined />
            </button>
            <button
              type="button"
              className={`qb-view-btn ${viewMode === 'grid' ? 'is-active' : ''}`}
              onClick={() => setViewMode('grid')}
              aria-label="卡片视图"
            >
              <AppstoreOutlined />
            </button>
          </div>
        </div>
      </div>

      {/* 列表卡片 */}
      <Card className="question-bank-card question-bank-list-card" bordered={false}>
        <Table
          className="question-bank-table"
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
          }}
          locale={{ emptyText: <Empty description="暂无题库数据" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 道题`,
          }}
          onChange={(nextPagination) => {
            fetchList({
              current: nextPagination.current,
              pageSize: nextPagination.pageSize,
            })
          }}
        />
      </Card>

      {/* 详情抽屉 */}
      <Drawer
        title="题目详情"
        width={720}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false)
          setDetail(null)
        }}
        loading={detailLoading}
      >
        {detail ? (
          <Space direction="vertical" size="large" className="question-bank-detail">
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="题型">
                <Tag color="blue">{questionTypeLabels[detail.type] || detail.type}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="题目内容">{detail.stem}</Descriptions.Item>
              <Descriptions.Item label="分值">{detail.score} 分</Descriptions.Item>
              <Descriptions.Item label="难度">
                <Tag>{difficultyLabels[detail.difficulty] || detail.difficulty}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="知识点">{listText(detail.knowledgePoints)}</Descriptions.Item>
              <Descriptions.Item label="来源场景">
                <Tag color={detail.sourceScene === 'test' ? 'purple' : 'default'}>
                  {detail.sourceScene || '未标记'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="来源智能体">{detail.sourceAgent || '-'}</Descriptions.Item>
              <Descriptions.Item label="来源标题">{detail.sourceTitle || '-'}</Descriptions.Item>
            </Descriptions>

            <div>
              <Text strong>解析</Text>
              <div className="question-bank-analysis">{detail.analysis || '-'}</div>
            </div>

            <div>
              <Text strong>题型内容</Text>
              <pre className="question-bank-json">{formatJson(detail.body)}</pre>
            </div>
            <div>
              <Text strong>标准答案</Text>
              <pre className="question-bank-json">{formatJson(detail.answer)}</pre>
            </div>
            <div>
              <Text strong>评分规则</Text>
              <pre className="question-bank-json">{formatJson(detail.scoring)}</pre>
            </div>
            <div>
              <Text strong>原始题目 JSON</Text>
              <pre className="question-bank-json">{formatJson(detail.rawQuestion)}</pre>
            </div>
          </Space>
        ) : (
          <Empty description="暂无题目详情" />
        )}
      </Drawer>
    </div>
  )
}

export default QuestionBank
