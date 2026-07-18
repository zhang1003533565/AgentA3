import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button, Card, Descriptions, Drawer, Empty, Form, Input, Select, Space, Table, Tag, Typography, message } from 'antd'
import { EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { getExamQuestionDetail, getExamQuestionList } from '../../../api/examQuestion'
import './QuestionBank.css'

const { Text, Title } = Typography

const questionTypeOptions = [
  { value: 'single_choice', label: '单选题' },
  { value: 'multiple_choice', label: '多选题' },
  { value: 'true_false', label: '判断题' },
  { value: 'fill_blank', label: '填空题' },
  { value: 'short_answer', label: '简答题' },
  { value: 'essay', label: '论述题' },
  { value: 'material_analysis', label: '材料分析题' },
  { value: 'calculation', label: '计算题' },
  { value: 'proof', label: '证明题' },
  { value: 'programming', label: '编程题' },
  { value: 'operation', label: '操作题' },
  { value: 'matching', label: '匹配题' },
  { value: 'ordering', label: '排序题' },
  { value: 'cloze', label: '完形填空' },
]

const questionTypeLabels = questionTypeOptions.reduce((acc, item) => {
  acc[item.value] = item.label
  return acc
}, {})

const difficultyLabels = {
  easy: '简单',
  medium: '中等',
  hard: '困难',
}

const difficultyColors = {
  easy: 'green',
  medium: 'gold',
  hard: 'red',
}

const sourceSceneLabels = {
  test: '测试导入',
  import: '批量导入',
  manual: '手动录入',
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
  const currentPage = pagination.current
  const currentPageSize = pagination.pageSize

  const fetchList = useCallback(async (params = {}) => {
    const values = form.getFieldsValue()
    const current = params.current ?? currentPage
    const size = params.pageSize ?? currentPageSize
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
  }, [currentPage, currentPageSize, form])

  useEffect(() => {
    fetchList({ current: 1 })
  }, [fetchList])

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
      width: 120,
      render: (value) => <Tag color="blue">{questionTypeLabels[value] || value}</Tag>,
    },
    {
      title: '题干',
      dataIndex: 'stem',
      ellipsis: true,
      render: (value) => <Text strong>{value}</Text>,
    },
    {
      title: '分值',
      dataIndex: 'score',
      width: 90,
      render: (value) => `${value ?? 0} 分`,
    },
    {
      title: '难度',
      dataIndex: 'difficulty',
      width: 100,
      render: (value) => <Tag color={difficultyColors[value] || 'default'}>{difficultyLabels[value] || value}</Tag>,
    },
    {
      title: '知识点',
      dataIndex: 'knowledgePoints',
      ellipsis: true,
      render: (value) => listText(value),
    },
    {
      title: '来源',
      dataIndex: 'sourceScene',
      width: 130,
      render: (value, record) => (
        <Space size={4} wrap>
          <Tag color={value === 'test' ? 'purple' : 'default'}>{sourceSceneLabels[value] || value || '未标记'}</Tag>
          {record.sourceAgent && <Tag>{record.sourceAgent}</Tag>}
        </Space>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
    },
    {
      title: '操作',
      width: 90,
      fixed: 'right',
      render: (_, record) => (
        <Button size="small" icon={<EyeOutlined />} onClick={() => openDetail(record.id)}>
          查看
        </Button>
      ),
    },
  ], [])

  return (
    <div className="question-bank-page">
      <section className="question-bank-hero">
        <div>
          <span className="question-bank-kicker">QUESTION BANK</span>
          <Title level={1}>题库管理</Title>
          <p>管理智能体生成和导入的标准题库。</p>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => fetchList()} loading={loading}>
          刷新
        </Button>
      </section>

      <Card className="question-bank-card">
        <Form
          form={form}
          className="question-bank-filter"
          layout="inline"
          onFinish={() => fetchList({ current: 1 })}
        >
          <Form.Item name="keyword">
            <Input allowClear prefix={<SearchOutlined />} placeholder="搜索题干" />
          </Form.Item>
          <Form.Item name="type">
            <Select
              allowClear
              placeholder="题型"
              options={questionTypeOptions}
              style={{ width: 150 }}
            />
          </Form.Item>
          <Form.Item name="difficulty">
            <Select
              allowClear
              placeholder="难度"
              style={{ width: 120 }}
              options={[
                { value: 'easy', label: '简单' },
                { value: 'medium', label: '中等' },
                { value: 'hard', label: '困难' },
              ]}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button
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

        <Table
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无题库数据" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 道题`,
          }}
          scroll={{ x: 1100 }}
          onChange={(nextPagination) => {
            fetchList({
              current: nextPagination.current,
              pageSize: nextPagination.pageSize,
            })
          }}
        />
      </Card>

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
              <Descriptions.Item label="题干">{detail.stem}</Descriptions.Item>
              <Descriptions.Item label="分值">{detail.score} 分</Descriptions.Item>
              <Descriptions.Item label="难度">
                <Tag color={difficultyColors[detail.difficulty] || 'default'}>
                  {difficultyLabels[detail.difficulty] || detail.difficulty}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="知识点">{listText(detail.knowledgePoints)}</Descriptions.Item>
              <Descriptions.Item label="来源场景">
                <Tag color={detail.sourceScene === 'test' ? 'purple' : 'default'}>
                  {sourceSceneLabels[detail.sourceScene] || detail.sourceScene || '未标记'}
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
