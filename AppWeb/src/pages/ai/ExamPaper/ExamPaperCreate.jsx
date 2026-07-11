import { useMemo, useState } from 'react'
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import {
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Radio,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import { createExamPaper, randomPreviewExamPaper } from '../../../api/examPaper'
import { getExamQuestionList } from '../../../api/examQuestion'

const { Text, Title } = Typography
const { TextArea } = Input

const questionTypeOptions = [
  ['single_choice', '单选题'], ['multiple_choice', '多选题'], ['true_false', '判断题'],
  ['fill_blank', '填空题'], ['short_answer', '简答题'], ['essay', '论述题'],
  ['material_analysis', '材料分析题'], ['calculation', '计算题'], ['proof', '证明题'],
  ['programming', '编程题'], ['operation', '操作题'], ['matching', '匹配题'],
  ['ordering', '排序题'], ['cloze', '完形填空'],
].map(([value, label]) => ({ value, label }))

const typeLabels = Object.fromEntries(questionTypeOptions.map(({ value, label }) => [value, label]))
const difficultyOptions = [
  { value: 'easy', label: '简单' },
  { value: 'medium', label: '中等' },
  { value: 'hard', label: '困难' },
]

const initialValues = {
  pageSize: 'A4',
  orientation: 'portrait',
  columnsCount: 1,
  durationMinutes: 60,
  selectionMode: 'manual',
  rules: [{ type: 'single_choice', quantity: 5 }],
}

const normalizeQuestion = (question) => ({
  ...question,
  questionId: Number(question.questionId ?? question.id),
  score: Number(question.score ?? 0),
})

function ExamPaperCreate({ onCreated }) {
  const [form] = Form.useForm()
  const [questionForm] = Form.useForm()
  const [selectedQuestions, setSelectedQuestions] = useState([])
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [questionRows, setQuestionRows] = useState([])
  const [questionLoading, setQuestionLoading] = useState(false)
  const [randomLoading, setRandomLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [manualSelection, setManualSelection] = useState(() => new Map())
  const selectionMode = Form.useWatch('selectionMode', form) || initialValues.selectionMode

  const selectedIds = useMemo(
    () => new Set(selectedQuestions.map((question) => question.questionId)),
    [selectedQuestions],
  )
  const totalScore = useMemo(
    () => selectedQuestions.reduce((sum, question) => sum + Number(question.score || 0), 0),
    [selectedQuestions],
  )

  const fetchQuestions = async (overrides = {}) => {
    const filters = questionForm.getFieldsValue()
    const current = overrides.current ?? pagination.current
    const size = overrides.pageSize ?? pagination.pageSize
    setQuestionLoading(true)
    try {
      const response = await getExamQuestionList({
        current,
        size,
        keyword: filters.keyword || undefined,
        type: filters.type || undefined,
        difficulty: filters.difficulty || undefined,
      })
      const data = response.data || {}
      setQuestionRows(data.records || [])
      setPagination({
        current: data.page ?? data.current ?? current,
        pageSize: data.size ?? size,
        total: data.total ?? 0,
      })
    } catch (error) {
      message.error(error.message || '题库列表加载失败')
    } finally {
      setQuestionLoading(false)
    }
  }

  const openQuestionDrawer = () => {
    setManualSelection(new Map())
    setDrawerOpen(true)
    fetchQuestions({ current: 1 })
  }

  const mergeQuestions = (questions) => {
    setSelectedQuestions((current) => {
      const merged = new Map(current.map((question) => [question.questionId, question]))
      questions.map(normalizeQuestion).forEach((question) => {
        if (!merged.has(question.questionId)) merged.set(question.questionId, question)
      })
      return [...merged.values()]
    })
  }

  const addManualQuestions = () => {
    mergeQuestions([...manualSelection.values()])
    setDrawerOpen(false)
    setManualSelection(new Map())
  }

  const handleRandomPreview = async () => {
    let rules
    try {
      rules = await form.validateFields(['rules'])
    } catch {
      return
    }

    setRandomLoading(true)
    try {
      const response = await randomPreviewExamPaper({
        rules: rules.rules.map((rule) => ({
          type: rule.type,
          difficulty: rule.difficulty || null,
          quantity: rule.quantity,
        })),
      })
      mergeQuestions(response.data?.questions || response.data || [])
      message.success('随机选题结果已合并')
    } catch (error) {
      message.error(error.message || '随机选题失败')
    } finally {
      setRandomLoading(false)
    }
  }

  const updateScore = (questionId, score) => {
    setSelectedQuestions((current) => current.map((question) => (
      question.questionId === questionId ? { ...question, score } : question
    )))
  }

  const moveQuestion = (index, offset) => {
    setSelectedQuestions((current) => {
      const target = index + offset
      if (target < 0 || target >= current.length) return current
      const next = [...current]
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  const handleSubmit = async () => {
    let values
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    if (!selectedQuestions.length) {
      message.error('请至少选择一道题目')
      return
    }
    if (selectedQuestions.some((question) => !Number.isFinite(Number(question.score)) || Number(question.score) <= 0)) {
      message.error('每道题的分值必须大于 0')
      return
    }

    setSubmitting(true)
    try {
      const response = await createExamPaper({
        title: values.title.trim(),
        subtitle: values.subtitle?.trim() || null,
        durationMinutes: values.durationMinutes,
        precautions: values.precautions?.trim() || null,
        headerInfo: values.headerInfo?.trim() || null,
        pageSize: values.pageSize,
        orientation: values.orientation.toUpperCase(),
        columnsCount: values.columnsCount,
        selectionMode: values.selectionMode.toUpperCase(),
        questions: selectedQuestions.map((question, index) => ({
          questionId: question.questionId,
          score: question.score,
          sortOrder: index + 1,
        })),
      })
      message.success('试卷创建成功')
      onCreated?.(response.data)
    } catch (error) {
      message.error(error.message || '试卷创建失败')
    } finally {
      setSubmitting(false)
    }
  }

  const selectedColumns = [
    { title: '顺序', width: 70, render: (_, __, index) => index + 1 },
    { title: '题型', dataIndex: 'type', width: 120, render: (value) => <Tag color="blue">{typeLabels[value] || value}</Tag> },
    { title: '题干', dataIndex: 'stem', ellipsis: true },
    {
      title: '分值', dataIndex: 'score', width: 130,
      render: (value, record) => (
        <InputNumber min={0.01} precision={2} value={value} onChange={(score) => updateScore(record.questionId, score)} />
      ),
    },
    {
      title: '操作', width: 150,
      render: (_, record, index) => (
        <Space size={2}>
          <Button type="text" icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => moveQuestion(index, -1)} />
          <Button type="text" icon={<ArrowDownOutlined />} disabled={index === selectedQuestions.length - 1} onClick={() => moveQuestion(index, 1)} />
          <Button type="text" danger icon={<DeleteOutlined />} onClick={() => setSelectedQuestions((current) => current.filter((item) => item.questionId !== record.questionId))} />
        </Space>
      ),
    },
  ]

  const bankColumns = [
    { title: '题型', dataIndex: 'type', width: 120, render: (value) => <Tag color="blue">{typeLabels[value] || value}</Tag> },
    { title: '题干', dataIndex: 'stem', ellipsis: true },
    { title: '分值', dataIndex: 'score', width: 90, render: (value) => `${value ?? 0} 分` },
    { title: '难度', dataIndex: 'difficulty', width: 90 },
  ]

  return (
    <Form form={form} layout="vertical" initialValues={initialValues} className="exam-paper-create">
      <Card title="试卷信息" className="exam-paper-card">
        <Row gutter={16}>
          <Col xs={24} lg={12}><Form.Item name="title" label="标题" rules={[{ required: true, whitespace: true, message: '请输入试卷标题' }]}><Input maxLength={160} showCount /></Form.Item></Col>
          <Col xs={24} lg={12}><Form.Item name="subtitle" label="副标题"><Input maxLength={200} showCount /></Form.Item></Col>
          <Col xs={24} sm={12} lg={6}><Form.Item name="durationMinutes" label="考试时长（分钟）" rules={[{ required: true, message: '请输入考试时长' }]}><InputNumber min={1} max={1440} precision={0} className="exam-paper-number" /></Form.Item></Col>
          <Col xs={24} sm={12} lg={6}><Form.Item name="pageSize" label="纸张"><Select options={['A3', 'A4', 'B4'].map((value) => ({ value, label: value }))} /></Form.Item></Col>
          <Col xs={24} sm={12} lg={6}><Form.Item name="orientation" label="方向"><Select options={[{ value: 'portrait', label: '纵向' }, { value: 'landscape', label: '横向' }]} /></Form.Item></Col>
          <Col xs={24} sm={12} lg={6}><Form.Item name="columnsCount" label="栏数"><Select options={[{ value: 1, label: '单栏' }, { value: 2, label: '双栏' }]} /></Form.Item></Col>
          <Col span={24}><Form.Item name="headerInfo" label="页眉信息"><Input maxLength={300} showCount /></Form.Item></Col>
          <Col span={24}><Form.Item name="precautions" label="注意事项"><TextArea rows={3} maxLength={2000} showCount /></Form.Item></Col>
        </Row>
      </Card>

      <Card title="选题方式" className="exam-paper-card">
        <Form.Item name="selectionMode"><Radio.Group options={[{ value: 'manual', label: '手工选题' }, { value: 'random', label: '随机选题' }]} /></Form.Item>
        {selectionMode === 'manual' ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={openQuestionDrawer}>从题库选择</Button>
        ) : (
          <Form.List name="rules">
            {(fields, { add, remove }) => (
              <Space direction="vertical" className="exam-paper-rules">
                {fields.map(({ key, name, ...restField }) => (
                  <Space key={key} wrap align="baseline">
                    <Form.Item {...restField} name={[name, 'type']} rules={[{ required: true, message: '请选择题型' }]}><Select placeholder="题型" options={questionTypeOptions} className="exam-paper-rule-type" /></Form.Item>
                    <Form.Item {...restField} name={[name, 'difficulty']}><Select allowClear placeholder="不限难度" options={difficultyOptions} className="exam-paper-rule-difficulty" /></Form.Item>
                    <Form.Item {...restField} name={[name, 'quantity']} rules={[{ required: true, message: '请输入数量' }]}><InputNumber min={1} precision={0} placeholder="数量" /></Form.Item>
                    <Button danger type="text" icon={<DeleteOutlined />} disabled={fields.length === 1} onClick={() => remove(name)}>删除</Button>
                  </Space>
                ))}
                <Space>
                  <Button icon={<PlusOutlined />} onClick={() => add({ type: 'single_choice', quantity: 1 })}>添加规则</Button>
                  <Button type="primary" icon={<ReloadOutlined />} loading={randomLoading} onClick={handleRandomPreview}>随机选题</Button>
                </Space>
              </Space>
            )}
          </Form.List>
        )}
      </Card>

      <Card
        title={<Space><span>已选题目</span><Tag color="green">{selectedQuestions.length} 题</Tag><Tag color="gold">{totalScore} 分</Tag></Space>}
        className="exam-paper-card"
      >
        <Table rowKey="questionId" columns={selectedColumns} dataSource={selectedQuestions} pagination={false} locale={{ emptyText: <Empty description="尚未选择题目" /> }} scroll={{ x: 760 }} />
      </Card>

      <div className="exam-paper-submit"><Button type="primary" size="large" loading={submitting} onClick={handleSubmit}>创建试卷</Button></div>

      <Drawer title="从题库选择" width={920} open={drawerOpen} onClose={() => setDrawerOpen(false)} extra={<Button type="primary" disabled={!manualSelection.size} onClick={addManualQuestions}>加入试卷（{manualSelection.size}）</Button>}>
        <Form form={questionForm} layout="inline" className="exam-paper-filter" onFinish={() => fetchQuestions({ current: 1 })}>
          <Form.Item name="keyword"><Input allowClear prefix={<SearchOutlined />} placeholder="搜索题干" /></Form.Item>
          <Form.Item name="type"><Select allowClear placeholder="题型" options={questionTypeOptions} className="exam-paper-filter-select" /></Form.Item>
          <Form.Item name="difficulty"><Select allowClear placeholder="难度" options={difficultyOptions} className="exam-paper-filter-select" /></Form.Item>
          <Button htmlType="submit" type="primary" icon={<SearchOutlined />}>查询</Button>
        </Form>
        <Table
          rowKey="id"
          columns={bankColumns}
          dataSource={questionRows}
          loading={questionLoading}
          rowSelection={{
            preserveSelectedRowKeys: true,
            selectedRowKeys: [...manualSelection.keys()],
            getCheckboxProps: (record) => ({ disabled: selectedIds.has(Number(record.id)) }),
            onSelect: (record, selected) => setManualSelection((current) => {
              const next = new Map(current)
              if (selected) next.set(Number(record.id), record)
              else next.delete(Number(record.id))
              return next
            }),
            onSelectAll: (selected, selectedRows, changedRows) => setManualSelection((current) => {
              const next = new Map(current)
              changedRows.forEach((record) => {
                if (selected) next.set(Number(record.id), record)
                else next.delete(Number(record.id))
              })
              selectedRows.forEach((record) => next.set(Number(record.id), record))
              return next
            }),
          }}
          pagination={{ ...pagination, showSizeChanger: true, showTotal: (total) => `共 ${total} 道题` }}
          onChange={(next) => fetchQuestions({ current: next.current, pageSize: next.pageSize })}
          scroll={{ x: 720 }}
        />
        <Text type="secondary">跨页选择会保留；已加入试卷的题目不可重复选择。</Text>
      </Drawer>
    </Form>
  )
}

export default ExamPaperCreate
