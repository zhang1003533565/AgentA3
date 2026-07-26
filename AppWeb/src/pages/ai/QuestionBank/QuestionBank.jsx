import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button, Card, Descriptions, Empty, Form, Input, InputNumber, Modal, Select, Space, Spin, Table, Tag, Typography, message } from 'antd'
import { MinusCircleOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import {
  createExamQuestion,
  deleteExamQuestion,
  getExamBanks,
  getExamQuestionDetail,
  getExamQuestionList,
  updateExamQuestion,
} from '../../../api/examQuestion'
import PageHeader from '../../../components/PageHeader/PageHeader'
import SidePanel from '../../../components/SidePanel/SidePanel'
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

// 把结构化答案 JSON 还原为可编辑的纯文本（与后端保存规则对称）
const answerToPlainText = (type, rawAnswer) => {
  if (rawAnswer == null) return ''
  const answer = asObject(rawAnswer)
  if (!answer) return typeof rawAnswer === 'string' ? rawAnswer : String(rawAnswer)
  if (type === 'single_choice') return answer.correctOption || ''
  if (type === 'multiple_choice') {
    return Array.isArray(answer.correctOptions) ? answer.correctOptions.join('、') : ''
  }
  if (type === 'true_false') {
    const val = answer.correct ?? answer.correctAnswer
    return val === true ? '正确' : val === false ? '错误' : ''
  }
  if (type === 'fill_blank') {
    const blanks = Array.isArray(answer.blanks) ? answer.blanks : []
    return blanks
      .map((blank) => (Array.isArray(blank.answers) ? blank.answers.join('、') : ''))
      .filter(Boolean)
      .join('；')
  }
  if (type === 'calculation' && answer.finalAnswer != null) return String(answer.finalAnswer)
  if (answer.referenceAnswer != null) return String(answer.referenceAnswer)
  // 其他结构（如 AI 生成的要点式答案）取可读字段拼接
  if (Array.isArray(answer.keyPoints)) {
    return answer.keyPoints
      .map((p) => (typeof p === 'string' ? p : p?.point || ''))
      .filter(Boolean)
      .join('；')
  }
  return Object.values(answer).filter((v) => typeof v === 'string').join('；')
}

// 选项序号（选项编辑/展示统一用字母 key）
const OPTION_KEYS = 'ABCDEFGHIJKLMNOP'.split('')

// 来源场景展示映射（缺失时默认人工录入）
const sourceSceneLabels = {
  manual: '人工录入',
  test: 'AI 生成',
  import: '批量导入',
  question_generation: '智能生成',
}

// 统一题目数据模型：MySQL 导入题与手工新增题字段不完全一致，
// 查看详情与编辑弹窗统一走该转换层，缺失字段用默认值填充
const normalizeQuestion = (raw) => {
  const data = raw || {}
  const body = asObject(data.body) || {}
  const answer = asObject(data.answer)
  const rawPoints = Array.isArray(data.knowledgePoints) ? data.knowledgePoints : asObject(data.knowledgePoints)
  const knowledgePoints = Array.isArray(rawPoints) ? rawPoints.filter(Boolean).map(String) : []
  const rawOptions = Array.isArray(body.options) ? body.options : []
  const options = rawOptions.map((opt, index) => (
    typeof opt === 'string'
      ? { key: OPTION_KEYS[index] || String(index + 1), text: opt }
      : { key: opt?.key || OPTION_KEYS[index] || String(index + 1), text: opt?.text ?? opt?.content ?? '' }
  ))
  return {
    id: data.id,
    type: data.type || '',
    bank: data.sourceTitle || '',
    content: data.stem || '',
    score: data.score == null || Number.isNaN(Number(data.score)) ? 5 : Number(data.score),
    difficulty: data.difficulty || '',
    knowledgePoints,
    options,
    answer,
    answerText: answerToPlainText(data.type, data.answer),
    analysis: data.analysis || '',
    source: sourceSceneLabels[data.sourceScene] || '人工录入',
    sourceTitle: data.sourceTitle || '',
    // 评分规则 / 作答说明等附加信息仍按原始结构渲染
    body: data.body,
    scoring: data.scoring,
    sourceAgent: data.sourceAgent || '',
  }
}

// 选择题选项列表：高亮正确选项（基于统一模型的 options 字段）
const renderOptions = (detail) => {
  const isChoice = detail.type === 'single_choice' || detail.type === 'multiple_choice'
  if (!isChoice) return null
  const { answer, options } = detail
  if (!options.length) {
    return (
      <div>
        <Text strong>选项</Text>
        <div className="qb-detail-box">暂无</div>
      </div>
    )
  }
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
  // 无结构化答案时降级展示纯文本，不显示空白
  if (!answer) return <div className="qb-detail-box">{detail.answerText || '暂无'}</div>
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
  const [editorForm] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [rows, setRows] = useState([])
  const rowsRef = useRef([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const paginationRef = useRef({ current: 1, pageSize: 10, total: 0 })
  const [detailOpen, setDetailOpen] = useState(false)
  const [detail, setDetail] = useState(null)
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  // 题库下拉选项（接口优先，失败时从列表 sourceTitle 兼容兼并）
  const [bankOptions, setBankOptions] = useState([])
  // 新增/编辑弹窗状态
  const [editorOpen, setEditorOpen] = useState(false)
  const [editorMode, setEditorMode] = useState('create')
  const [editingId, setEditingId] = useState(null)
  const [editorLoading, setEditorLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  // 监听编辑弹窗题型：仅选择题展示选项编辑
  const editorType = Form.useWatch('type', editorForm)
  const editorIsChoice = editorType === 'single_choice' || editorType === 'multiple_choice'

  const fetchList = useCallback(async (params = {}) => {
    const values = form.getFieldsValue()
    const current = params.current ?? paginationRef.current.current
    const size = params.pageSize ?? paginationRef.current.pageSize
    setLoading(true)
    try {
      const res = await getExamQuestionList({
        current,
        size,
        type: values.type || undefined,
        difficulty: values.difficulty || undefined,
        keyword: values.keyword || undefined,
        bankId: values.bank || undefined,
      })
      const data = res.data || {}
      setRows(data.records || [])
      rowsRef.current = data.records || []
      const nextPagination = {
        current: data.page || current,
        pageSize: data.size || size,
        total: data.total || 0,
      }
      paginationRef.current = nextPagination
      setPagination(nextPagination)
    } catch (error) {
      message.error(error.message || '题库列表加载失败')
    } finally {
      setLoading(false)
    }
  }, [form])

  useEffect(() => {
    fetchList({ current: 1 })
  }, [fetchList])

  // 题库选项：优先请求 /api/exam/banks，接口未就绪时静默降级
  useEffect(() => {
    getExamBanks()
      .then((res) => {
        const list = Array.isArray(res.data) ? res.data : []
        const options = list.map((item) => (
          typeof item === 'string'
            ? { value: item, label: item }
            : { value: item.id ?? item.name, label: item.name ?? item.title ?? String(item.id) }
        ))
        if (options.length) setBankOptions(options)
      })
      .catch(() => {})
  }, [])

  // 降级兼容：从已加载列表的 sourceTitle 去重补充题库选项
  useEffect(() => {
    setBankOptions((prev) => {
      const known = new Set(prev.map((opt) => opt.value))
      const additions = []
      rows.forEach((row) => {
        if (row.sourceTitle && !known.has(row.sourceTitle)) {
          known.add(row.sourceTitle)
          additions.push({ value: row.sourceTitle, label: row.sourceTitle })
        }
      })
      return additions.length ? [...prev, ...additions] : prev
    })
  }, [rows])

  const openDetail = async (id) => {
    setDetailOpen(true)
    setDetailLoading(true)
    try {
      const res = await getExamQuestionDetail(id)
      // 统一数据模型：兼容 MySQL 导入题与手工新增题
      setDetail(res.data ? normalizeQuestion(res.data) : null)
    } catch (error) {
      message.error(error.message || '题目详情加载失败')
    } finally {
      setDetailLoading(false)
    }
  }

  // 打开新增弹窗
  const openCreate = () => {
    setEditorMode('create')
    setEditingId(null)
    editorForm.resetFields()
    setEditorOpen(true)
  }

  // 打开编辑弹窗并回填详情（经统一模型转换，与查看详情同源）
  const openEdit = async (id) => {
    setEditorMode('edit')
    setEditingId(id)
    editorForm.resetFields()
    setEditorOpen(true)
    setEditorLoading(true)
    try {
      const res = await getExamQuestionDetail(id)
      const data = normalizeQuestion(res.data)
      editorForm.setFieldsValue({
        type: data.type,
        bankId: data.bank || undefined,
        content: data.content,
        options: data.options.length
          ? data.options.map((opt) => ({ text: opt.text }))
          : [{ text: '' }, { text: '' }, { text: '' }, { text: '' }],
        score: data.score,
        difficulty: data.difficulty,
        knowledgePoint: data.knowledgePoints.join('、'),
        answer: data.answerText,
        analysis: data.analysis,
      })
    } catch (error) {
      message.error(error.message || '题目详情加载失败')
      setEditorOpen(false)
    } finally {
      setEditorLoading(false)
    }
  }

  // 保存新增/编辑
  const handleEditorSave = async () => {
    let values
    try {
      values = await editorForm.validateFields()
    } catch {
      return
    }
    const isChoice = values.type === 'single_choice' || values.type === 'multiple_choice'
    // 选项按顺序自动编号 A/B/C…，过滤空行
    const options = isChoice
      ? (values.options || [])
          .map((opt) => (opt?.text || '').trim())
          .filter(Boolean)
          .map((text, index) => ({ key: OPTION_KEYS[index] || String(index + 1), text }))
      : []
    if (isChoice && options.length < 2) {
      message.warning('选择题至少填写两个选项')
      return
    }
    const payload = {
      type: values.type,
      content: values.content,
      bankId: values.bankId || '',
      difficulty: values.difficulty,
      answer: values.answer,
      analysis: values.analysis || '',
      // 统一模型补充字段（接口地址/已有参数不变，后端未声明的字段会被忽略）
      score: values.score ?? 5,
      knowledgePoint: (values.knowledgePoint || '').trim(),
      options,
    }
    setSaving(true)
    try {
      if (editorMode === 'edit') {
        await updateExamQuestion(editingId, payload)
        message.success('编辑成功')
        fetchList()
      } else {
        await createExamQuestion(payload)
        message.success('新增成功')
        fetchList({ current: 1 })
      }
      setEditorOpen(false)
    } catch (error) {
      message.error(error.message || (editorMode === 'edit' ? '编辑失败' : '新增失败'))
    } finally {
      setSaving(false)
    }
  }

  // 删除（二次确认）
  const handleDelete = (record) => {
    Modal.confirm({
      title: '删除题目',
      content: '确定删除该题目吗？',
      okText: '确定',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteExamQuestion(record.id)
          message.success('删除成功')
          // 当页只剩一条时回退一页，避免空页
          const { current } = paginationRef.current
          const nextCurrent = rowsRef.current.length === 1 && current > 1 ? current - 1 : current
          fetchList({ current: nextCurrent })
        } catch (error) {
          message.error(error.message || '删除失败')
        }
      },
    })
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
          <a className="qb-action-link qb-action-edit" onClick={() => openEdit(record.id)}>编辑</a>
          <a className="qb-action-link qb-action-delete" onClick={() => handleDelete(record)}>删除</a>
        </Space>
      ),
    },
  ], [])

  // 筛选区域：通过 Table title 渲染在表格容器内、表头上方
  const renderFilter = () => (
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
        <Select allowClear showSearch optionFilterProp="label" placeholder="请选择所属题库" options={bankOptions} />
      </Form.Item>

      {/* 题型（默认选择题，不可清空，始终只筛一种题型） */}
      <Form.Item name="type" label="题型" initialValue="single_choice">
        <Select showSearch optionFilterProp="label" placeholder="请选择题型" options={questionTypeOptions} />
      </Form.Item>

      {/* 难度 */}
      <Form.Item name="difficulty" label="难度">
        <Select allowClear showSearch optionFilterProp="label" placeholder="请选择难度" options={[
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
          <Button type="primary" icon={<PlusOutlined />} className="qb-add-btn" onClick={openCreate}>
            新增题目
          </Button>
        </Space>
      </Form.Item>
    </Form>
  )

  return (
    <div className="question-bank-page">
      {/* 页面标题（通用面包屑组件） */}
      <PageHeader items={['题库管理', '题库']} />

      {/* 列表卡片（筛选区作为表格 title 渲染在表头上方） */}
      <Card className="question-bank-card question-bank-list-card" bordered={false}>
        <Table
          className="question-bank-table"
          rowKey="id"
          title={renderFilter}
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

      {/* 新增/编辑题目面板（与详情同尺寸的通用右侧面板） */}
      <SidePanel
        title={editorMode === 'edit' ? '编辑题目' : '新增题目'}
        open={editorOpen}
        onClose={() => setEditorOpen(false)}
        forceRender
        footer={(
          <>
            <Button onClick={() => setEditorOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={handleEditorSave}>保存</Button>
          </>
        )}
      >
        <Spin spinning={editorLoading}>
          <Form form={editorForm} layout="vertical" className="question-bank-editor-form">
            <Form.Item name="type" label="题型" rules={[{ required: true, message: '请选择题型' }]}>
              <Select showSearch optionFilterProp="label" placeholder="请选择题型" options={questionTypeOptions} />
            </Form.Item>
            <Form.Item name="bankId" label="所属题库" rules={[{ required: true, message: '请选择所属题库' }]}>
              <Select showSearch optionFilterProp="label" placeholder="请选择所属题库" options={bankOptions} />
            </Form.Item>
            <Form.Item name="content" label="题目内容" rules={[{ required: true, message: '请输入题目内容' }]}>
              <Input.TextArea rows={3} placeholder="请输入题目内容" />
            </Form.Item>
            {/* 选项编辑：仅选择题展示，序号按顺序自动编为 A/B/C… */}
            {editorIsChoice ? (
              <Form.Item label="选项" required className="qb-editor-options">
                <Form.List name="options" initialValue={[{ text: '' }, { text: '' }, { text: '' }, { text: '' }]}>
                  {(fields, { add, remove }) => (
                    <>
                      {fields.map((field, index) => (
                        <div key={field.key} className="qb-editor-option-row">
                          <span className="qb-editor-option-key">{OPTION_KEYS[index] || index + 1}</span>
                          <Form.Item name={[field.name, 'text']} noStyle>
                            <Input placeholder={`请输入选项 ${OPTION_KEYS[index] || index + 1} 内容`} />
                          </Form.Item>
                          {fields.length > 2 ? (
                            <MinusCircleOutlined className="qb-editor-option-remove" onClick={() => remove(field.name)} />
                          ) : null}
                        </div>
                      ))}
                      {fields.length < OPTION_KEYS.length ? (
                        <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ text: '' })}>
                          添加选项
                        </Button>
                      ) : null}
                    </>
                  )}
                </Form.List>
              </Form.Item>
            ) : null}
            <Form.Item name="score" label="分值" initialValue={5} rules={[{ required: true, message: '请输入分值' }]}>
              <InputNumber min={0.5} max={100} step={0.5} placeholder="请输入分值" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="difficulty" label="难度" rules={[{ required: true, message: '请选择难度' }]}>
              <Select showSearch optionFilterProp="label" placeholder="请选择难度" options={[
                { value: 'easy', label: '简单' },
                { value: 'medium', label: '中等' },
                { value: 'hard', label: '困难' },
              ]} />
            </Form.Item>
            <Form.Item name="knowledgePoint" label="知识点">
              <Input placeholder="请输入知识点，多个用、分隔（可选）" />
            </Form.Item>
            <Form.Item name="answer" label="答案" rules={[{ required: true, message: '请输入答案' }]}>
              <Input.TextArea rows={3} placeholder="请输入答案" />
            </Form.Item>
            <Form.Item name="analysis" label="解析">
              <Input.TextArea rows={3} placeholder="请输入解析（可选）" />
            </Form.Item>
          </Form>
        </Spin>
      </SidePanel>

      {/* 详情面板 */}
      <SidePanel
        title="题目详情"
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
                <Tag color="blue">{questionTypeLabels[detail.type] || detail.type || '暂无'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="所属题库">{detail.bank || '暂无'}</Descriptions.Item>
              <Descriptions.Item label="题目内容">{detail.content || '暂无'}</Descriptions.Item>
              <Descriptions.Item label="分值">{detail.score} 分</Descriptions.Item>
              <Descriptions.Item label="难度">
                <Tag>{difficultyLabels[detail.difficulty] || detail.difficulty || '暂无'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="知识点">
                {detail.knowledgePoints.length ? detail.knowledgePoints.join('、') : '暂无'}
              </Descriptions.Item>
              <Descriptions.Item label="来源">
                <Tag color={detail.source === '人工录入' ? 'default' : 'purple'}>{detail.source}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="来源标题">{detail.sourceTitle || '暂无'}</Descriptions.Item>
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
              <div className="question-bank-analysis">{detail.analysis || '暂无'}</div>
            </div>

            <div>
              <Text strong>评分规则</Text>
              {renderScoring(detail)}
            </div>
          </Space>
        ) : (
          <Empty description="暂无题目详情" />
        )}
      </SidePanel>
    </div>
  )
}

export default QuestionBank
