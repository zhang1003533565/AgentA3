import { useCallback, useEffect, useMemo, useState } from 'react'
import { Breadcrumb, Button, Card, Descriptions, Drawer, Empty, Form, Input, Select, Space, Table, Tag, Typography, message } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
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

// 评分方式展示映射（面向老师/管理员的可读文案）
const scoringModeLabels = {
  exact: '按标准答案判分',
  blank: '按空给分',
  rubric: '按评分点给分',
  step: '按解题步骤给分',
  manual: '人工评分',
}

// 兼容对象 / JSON 字符串两种形式的字段
const asObject = (value) => {
  if (!value) return null
  if (typeof value === 'string') {
    try {
      return JSON.parse(value)
    } catch {
      return null
    }
  }
  return typeof value === 'object' ? value : null
}

const listText = (value) => {
  if (!Array.isArray(value) || !value.length) return '-'
  return value.join('、')
}

// 选择题选项列表：高亮正确选项
const renderOptions = (detail) => {
  const body = asObject(detail.body)
  const answer = asObject(detail.answer)
  const options = body?.options
  if (!Array.isArray(options) || !options.length) return null
  const correctKeys = detail.type === 'multiple_choice'
    ? (answer?.correctOptions || [])
    : [answer?.correctOption].filter(Boolean)
  return (
    <div>
      <Text strong>选项</Text>
      <div className="qb-option-list">
        {options.map((opt) => {
          const isCorrect = correctKeys.includes(opt.key)
          return (
            <div key={opt.key} className={`qb-option-item${isCorrect ? ' qb-option-correct' : ''}`}>
              <span className="qb-option-key">{opt.key}</span>
              <span className="qb-option-text">{opt.text}</span>
              {isCorrect ? <span className="qb-option-badge">正确答案</span> : null}
            </div>
          )
        })}
      </div>
    </div>
  )
}

// 作答要求（简答/计算等题型的补充信息）
const renderRequirements = (detail) => {
  const body = asObject(detail.body)
  const groups = []
  if (Array.isArray(body?.given) && body.given.length) groups.push(['已知条件', body.given])
  if (Array.isArray(body?.requirements) && body.requirements.length) groups.push(['作答要求', body.requirements])
  if (body?.answerLengthHint) groups.push(['篇幅建议', [body.answerLengthHint]])
  if (!groups.length) return null
  return (
    <div>
      <Text strong>作答说明</Text>
      <div className="qb-detail-box">
        {groups.map(([label, items]) => (
          <div key={label} className="qb-require-group">
            <span className="qb-require-label">{label}：</span>
            <ul className="qb-point-list">
              {items.map((item) => <li key={item}>{item}</li>)}
            </ul>
          </div>
        ))}
      </div>
    </div>
  )
}

// 参考答案：按题型转成老师可直接阅读的文字
const renderAnswer = (detail) => {
  const answer = asObject(detail.answer)
  if (!answer) return <div className="qb-detail-box">-</div>
  const { type } = detail
  if (type === 'single_choice') {
    return <div className="qb-detail-box"><span className="qb-answer-main">正确答案：{answer.correctOption || '-'}</span></div>
  }
  if (type === 'multiple_choice') {
    return <div className="qb-detail-box"><span className="qb-answer-main">正确答案：{listText(answer.correctOptions)}</span></div>
  }
  if (type === 'true_false') {
    const val = answer.correct ?? answer.correctAnswer
    const text = val === true ? '正确' : val === false ? '错误' : '-'
    return <div className="qb-detail-box"><span className="qb-answer-main">正确答案：{text}</span></div>
  }
  if (type === 'fill_blank') {
    const blanks = Array.isArray(answer.blanks) ? answer.blanks : []
    if (!blanks.length) return <div className="qb-detail-box">-</div>
    return (
      <div className="qb-detail-box">
        {blanks.map((blank, index) => (
          <div key={blank.id || index} className="qb-answer-line">
            第 {blank.index ?? index + 1} 空：{listText(blank.answers)}
            {Array.isArray(blank.answers) && blank.answers.length > 1 ? '（任写其一即可）' : ''}
          </div>
        ))}
        {answer.caseSensitive === false ? <div className="qb-answer-note">英文答案不区分大小写</div> : null}
      </div>
    )
  }
  if (type === 'calculation') {
    const steps = Array.isArray(answer.steps) ? answer.steps : []
    return (
      <div className="qb-detail-box">
        <div className="qb-answer-main">最终答案：{answer.finalAnswer ?? '-'}</div>
        {steps.length ? (
          <>
            <div className="qb-answer-sub">解题步骤</div>
            <ol className="qb-point-list">
              {steps.map((step) => <li key={step}>{step}</li>)}
            </ol>
          </>
        ) : null}
      </div>
    )
  }
  // 简答/论述：参考答案 + 答题要点
  const points = answer.answerPoints || answer.keyPoints
  return (
    <div className="qb-detail-box">
      <div className="qb-answer-text">{answer.referenceAnswer || '-'}</div>
      {Array.isArray(points) && points.length ? (
        <>
          <div className="qb-answer-sub">答题要点</div>
          <ul className="qb-point-list">
            {points.map((point) => <li key={point}>{point}</li>)}
          </ul>
        </>
      ) : null}
    </div>
  )
}

// 评分规则：评分方式 + 评分点列表
const renderScoring = (detail) => {
  const scoring = asObject(detail.scoring)
  if (!scoring) return <div className="qb-detail-box">按标准答案判分</div>
  const rubrics = Array.isArray(scoring.rubrics) ? scoring.rubrics : []
  return (
    <div className="qb-detail-box">
      <div className="qb-answer-main">评分方式：{scoringModeLabels[scoring.mode] || '按标准答案判分'}</div>
      {rubrics.length ? (
        <div className="qb-rubric-list">
          {rubrics.map((rubric, index) => (
            <div key={rubric.criterion || index} className="qb-rubric-item">
              <span className="qb-rubric-name">{rubric.criterion}</span>
              <span className="qb-rubric-score">{rubric.score} 分</span>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}

function QuestionBank() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [rows, setRows] = useState([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [detailOpen, setDetailOpen] = useState(false)
  const [detail, setDetail] = useState(null)
  const [selectedRowKeys, setSelectedRowKeys] = useState([])

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
      {/* 面包屑 */}
      <div className="question-bank-header">
        <Breadcrumb>
          <Breadcrumb.Item>题库管理</Breadcrumb.Item>
          <Breadcrumb.Item><span className="qb-breadcrumb-active">题库</span></Breadcrumb.Item>
        </Breadcrumb>
      </div>

      {/* 筛选区域 */}
      <Card className="question-bank-card question-bank-filter-card" bordered={false}>
        <Form
          form={form}
          className="question-bank-filter"
          layout="vertical"
          onFinish={() => fetchList({ current: 1 })}
        >
          {/* 搜索框 - 无 label */}
          <Form.Item name="keyword" label=" " colon={false}>
            <Input allowClear suffix={<SearchOutlined />} placeholder="搜索题目内容" />
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
          <Form.Item label=" " colon={false} className="question-bank-filter-actions">
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
            size: 'default',
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

            {/* 选项（仅选择题），正确选项高亮 */}
            {renderOptions(detail)}

            {/* 作答说明（简答/计算等题型） */}
            {renderRequirements(detail)}

            <div>
              <Text strong>参考答案</Text>
              {renderAnswer(detail)}
            </div>

            <div>
              <Text strong>解析</Text>
              <div className="question-bank-analysis">{detail.analysis || '-'}</div>
            </div>

            <div>
              <Text strong>评分规则</Text>
              {renderScoring(detail)}
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
