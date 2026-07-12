import { useEffect, useMemo, useRef, useState } from 'react'
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
  Steps,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  createExamPaper,
  createExamPaperPreview,
  deleteExamPaperPreview,
  getExamPaperPreviewPdf,
  randomPreviewExamPaper,
} from '../../../api/examPaper'
import { getExamQuestionList } from '../../../api/examQuestion'
import ExamPaperFormatPanel from './ExamPaperFormatPanel'
import ExamPaperPreview from './ExamPaperPreview'
import {
  SOURCE_LAYOUT_DEFAULTS,
  buildExamPaperRequest,
  createPreviewSignature,
  createPreviewProof,
  shouldAcceptPreviewGeneration,
} from './examPaperPreviewState'

const { Text } = Typography
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
  durationMinutes: 60,
  selectionMode: 'manual',
  rules: [{ type: 'single_choice', quantity: 5 }],
  layout: { ...SOURCE_LAYOUT_DEFAULTS },
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
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewError, setPreviewError] = useState(null)
  const [preview, setPreview] = useState(null)
  const [previewDirty, setPreviewDirty] = useState(false)
  const [currentStep, setCurrentStep] = useState(0)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [manualSelection, setManualSelection] = useState(() => new Map())
  const questionRequestId = useRef(0)
  const previewRef = useRef(null)
  const selectedQuestionsRef = useRef([])
  const mountedRef = useRef(true)
  const previewGenerationRef = useRef(0)
  const previewAbortRef = useRef(null)
  const selectionMode = Form.useWatch('selectionMode', form) || initialValues.selectionMode

  const selectedIds = useMemo(
    () => new Set(selectedQuestions.map((question) => question.questionId)),
    [selectedQuestions],
  )
  const totalScore = useMemo(
    () => selectedQuestions.reduce((sum, question) => sum + Number(question.score || 0), 0),
    [selectedQuestions],
  )

  useEffect(() => {
    previewRef.current = preview
  }, [preview])

  useEffect(() => {
    selectedQuestionsRef.current = selectedQuestions
  }, [selectedQuestions])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      previewGenerationRef.current += 1
      previewAbortRef.current?.abort()
      const current = previewRef.current
      if (current?.blobUrl) URL.revokeObjectURL(current.blobUrl)
      if (current?.token) deleteExamPaperPreview(current.token).catch(() => {})
    }
  }, [])

  const cancelPendingPreview = () => {
    previewGenerationRef.current += 1
    previewAbortRef.current?.abort()
    previewAbortRef.current = null
  }

  const invalidatePreview = () => {
    cancelPendingPreview()
    setPreviewLoading(false)
    setPreviewDirty(true)
  }

  const fetchQuestions = async (overrides = {}) => {
    const requestId = ++questionRequestId.current
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
      }, {
        skipGlobalErrorMessage: true,
      })
      const data = response.data || {}
      if (requestId === questionRequestId.current) {
        setQuestionRows(data.records || [])
        setPagination({
          current: data.page ?? data.current ?? current,
          pageSize: data.size ?? size,
          total: data.total ?? 0,
        })
      }
    } catch (error) {
      if (requestId === questionRequestId.current) {
        message.error(error.message || '题库列表加载失败')
      }
    } finally {
      if (requestId === questionRequestId.current) setQuestionLoading(false)
    }
  }

  const openQuestionDrawer = () => {
    setManualSelection(new Map())
    setDrawerOpen(true)
    fetchQuestions({ current: 1 })
  }

  const mergeQuestions = (questions) => {
    invalidatePreview()
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
    invalidatePreview()
    setSelectedQuestions((current) => current.map((question) => (
      question.questionId === questionId ? { ...question, score } : question
    )))
  }

  const moveQuestion = (index, offset) => {
    invalidatePreview()
    setSelectedQuestions((current) => {
      const target = index + offset
      if (target < 0 || target >= current.length) return current
      const next = [...current]
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  const validatePaper = async () => {
    let values
    try {
      values = await form.validateFields()
    } catch {
      return null
    }
    if (!selectedQuestions.length) {
      message.error('请至少选择一道题目')
      return null
    }
    if (selectedQuestions.some((question) => !Number.isFinite(Number(question.score)) || Number(question.score) <= 0)) {
      message.error('每道题的分值必须大于 0')
      return null
    }
    return { values, request: buildExamPaperRequest(values, selectedQuestions) }
  }

  const clearCurrentPreview = async () => {
    const current = previewRef.current
    previewRef.current = null
    setPreview(null)
    if (current?.blobUrl) URL.revokeObjectURL(current.blobUrl)
    if (current?.token) {
      try {
        await deleteExamPaperPreview(current.token)
      } catch {
        // Best-effort cleanup: the server may already have expired the token.
      }
    }
  }

  const handleGeneratePreview = async () => {
    const paper = await validatePaper()
    if (!paper) return
    const signature = createPreviewSignature(paper.values, selectedQuestions)
    let pendingToken = null
    let pendingBlobUrl = null
    cancelPendingPreview()
    const generation = previewGenerationRef.current
    let controller = null
    const isCurrent = () => shouldAcceptPreviewGeneration({
      generation,
      currentGeneration: previewGenerationRef.current,
      mounted: mountedRef.current,
      requestedSignature: signature,
      currentSignature: createPreviewSignature(form.getFieldsValue(true), selectedQuestionsRef.current),
    })

    setPreviewLoading(true)
    setPreviewError(null)
    try {
      await clearCurrentPreview()
      if (!isCurrent()) return
      // Do not abort POST: once the server starts conversion we must receive its token
      // so a stale generation can explicitly DELETE the temporary preview.
      const response = await createExamPaperPreview(paper.request)
      const session = response.data
      pendingToken = session.token
      if (!isCurrent()) {
        await deleteExamPaperPreview(pendingToken).catch(() => {})
        pendingToken = null
        return
      }
      controller = new AbortController()
      previewAbortRef.current = controller
      const pdfBlob = await getExamPaperPreviewPdf(session.token, { signal: controller.signal })
      pendingBlobUrl = URL.createObjectURL(pdfBlob)
      if (!isCurrent()) {
        URL.revokeObjectURL(pendingBlobUrl)
        pendingBlobUrl = null
        await deleteExamPaperPreview(pendingToken).catch(() => {})
        pendingToken = null
        return
      }
      const nextPreview = {
        ...session,
        blobUrl: pendingBlobUrl,
        signature,
      }
      previewRef.current = nextPreview
      pendingToken = null
      pendingBlobUrl = null
      setPreview(nextPreview)
      setPreviewDirty(false)
      setCurrentStep(2)
    } catch (error) {
      if (pendingToken) deleteExamPaperPreview(pendingToken).catch(() => {})
      if (pendingBlobUrl) URL.revokeObjectURL(pendingBlobUrl)
      if (generation === previewGenerationRef.current && mountedRef.current) {
        setPreviewError(error)
        setPreviewDirty(true)
      }
    } finally {
      if (generation === previewGenerationRef.current && mountedRef.current) {
        previewAbortRef.current = null
        setPreviewLoading(false)
      }
    }
  }

  const handleSubmit = async () => {
    const paper = await validatePaper()
    if (!paper) return
    const currentSignature = createPreviewSignature(paper.values, selectedQuestions)
    if (!preview || previewDirty || preview.signature !== currentSignature) {
      message.warning('页面或题目已变化，请重新生成预览')
      return
    }

    setSubmitting(true)
    try {
      const response = await createExamPaper({
        ...paper.request,
        previewProof: createPreviewProof(preview),
      })
      message.success('试卷创建成功')
      await clearCurrentPreview()
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
          <Button type="text" danger icon={<DeleteOutlined />} onClick={() => {
            invalidatePreview()
            setSelectedQuestions((current) => current.filter((item) => item.questionId !== record.questionId))
          }} />
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

  const goToFormat = async () => {
    const paper = await validatePaper()
    if (paper) setCurrentStep(1)
  }

  const restoreSourceDefaults = () => {
    form.setFieldValue('layout', { ...SOURCE_LAYOUT_DEFAULTS })
    invalidatePreview()
  }

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={initialValues}
      className="exam-paper-create"
      onValuesChange={invalidatePreview}
    >
      <Card className="exam-paper-card exam-paper-steps-card">
        <Steps current={currentStep} items={[{ title: '试卷信息与选题' }, { title: '页面格式' }, { title: '预览与确认' }]} />
      </Card>

      <div className={currentStep === 0 ? '' : 'exam-paper-step-hidden'} aria-hidden={currentStep !== 0}>
      <Card title="试卷信息" className="exam-paper-card">
        <Row gutter={16}>
          <Col xs={24} lg={12}><Form.Item name="title" label="标题" rules={[{ required: true, whitespace: true, message: '请输入试卷标题' }]}><Input maxLength={160} showCount /></Form.Item></Col>
          <Col xs={24} lg={12}><Form.Item name="subtitle" label="副标题"><Input maxLength={200} showCount /></Form.Item></Col>
          <Col xs={24} sm={12} lg={6}><Form.Item name="durationMinutes" label="考试时长（分钟）" rules={[{ required: true, message: '请输入考试时长' }]}><InputNumber min={1} max={1440} precision={0} className="exam-paper-number" /></Form.Item></Col>
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

      <div className="exam-paper-submit"><Button type="primary" size="large" onClick={goToFormat}>下一步：页面格式</Button></div>
      </div>

      <div className={currentStep === 1 ? '' : 'exam-paper-step-hidden'} aria-hidden={currentStep !== 1}>
        {currentStep === 1 && <>
        <ExamPaperFormatPanel form={form} onRestoreDefaults={restoreSourceDefaults} />
        <div className="exam-paper-submit exam-paper-submit-between">
          <Button size="large" onClick={() => setCurrentStep(0)}>上一步</Button>
          <Button type="primary" size="large" loading={previewLoading} onClick={handleGeneratePreview}>生成真实预览</Button>
        </div>
        </>}
      </div>

      <div className={currentStep === 2 ? '' : 'exam-paper-step-hidden'} aria-hidden={currentStep !== 2}>
        {currentStep === 2 && <>
        <Row gutter={[18, 18]} align="top">
          <Col xs={24} xl={9}><ExamPaperFormatPanel form={form} onRestoreDefaults={restoreSourceDefaults} /></Col>
          <Col xs={24} xl={15}>
            <ExamPaperPreview preview={preview} loading={previewLoading} error={previewError} dirty={previewDirty} onRefresh={handleGeneratePreview} />
          </Col>
        </Row>
        <div className="exam-paper-submit exam-paper-submit-between">
          <Space wrap>
            <Button size="large" onClick={() => {
              invalidatePreview()
              setCurrentStep(0)
            }}>返回修改题目</Button>
            <Button size="large" onClick={() => setCurrentStep(1)}>返回调整格式</Button>
          </Space>
          <Button type="primary" size="large" loading={submitting} disabled={!preview || previewDirty} onClick={handleSubmit}>确认生成并保存</Button>
        </div>
        </>}
      </div>

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
