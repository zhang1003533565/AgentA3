import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Alert, Button, Card, Checkbox, Divider, Empty, Form, Input, InputNumber,
  List, message, Radio, Segmented, Select, Space, Spin, Statistic, Tag,
  Typography, Upload,
} from 'antd'
import { DeleteOutlined, InboxOutlined } from '@ant-design/icons'
import { getQuestionGenerationOptions, generateQuestions } from '../../api/questionGeneration'
import { importExamQuestions, reviewExamQuestions } from '../../api/examQuestion'
import { QUESTION_BANK_ROUTES } from './questionBankRoutes'
import {
  buildGenerationFormData,
  buildImportPayload,
  canEditQuestions,
  canImportQuestions,
  clearJsonEditorErrorsForQuestion,
  invalidateReviewGeneration,
  isQuestionTypeAvailable,
  normalizeQuestionForEditor,
  removeQuestionAndRenumber,
  serializeEditedQuestion,
  updateFillBlankAnswers,
  updateJsonEditorErrors,
} from './questionGenerationState'
import './QuestionBankGeneratePage.css'

const { Title, Paragraph, Text } = Typography
const { TextArea } = Input

const TYPE_LABELS = {
  single_choice: '单选题', multiple_choice: '多选题', true_false: '判断题',
  fill_blank: '填空题', short_answer: '简答题',
}
const DIFFICULTIES = [
  { value: 'easy', label: '简单' }, { value: 'medium', label: '中等' }, { value: 'hard', label: '困难' },
]

const listValue = (value) => Array.isArray(value) ? value.join('\n') : ''
const splitLines = (value) => value.split('\n').map((item) => item.trim()).filter(Boolean)

function JsonDraftField({ label, value, errorKey, requireArray = false, disabled, onChange, onInvalidate, onErrorChange }) {
  const serializedValue = JSON.stringify(value, null, 2)
  const [draftValue, setDraftValue] = useState(serializedValue)
  const [error, setError] = useState('')

  useEffect(() => {
    setDraftValue(serializedValue)
    setError('')
    onErrorChange(errorKey, false)
  }, [errorKey, onErrorChange, serializedValue])

  useEffect(() => () => onErrorChange(errorKey, false), [errorKey, onErrorChange])

  const updateDraft = (nextValue) => {
    setDraftValue(nextValue)
    onInvalidate()
    try {
      const parsed = JSON.parse(nextValue)
      if (requireArray && !Array.isArray(parsed)) throw new Error('必须是 JSON 数组')
      setError('')
      onErrorChange(errorKey, false)
      onChange(parsed)
    } catch (parseError) {
      setError(parseError.message === '必须是 JSON 数组' ? parseError.message : '必须是有效 JSON')
      onErrorChange(errorKey, true)
    }
  }

  return <label className={`qbg-field${error ? ' qbg-field--error' : ''}`}><span>{label}</span><TextArea disabled={disabled} status={error ? 'error' : undefined} value={draftValue} autoSize={{ minRows: 3 }} onChange={(event) => updateDraft(event.target.value)} />{error && <Text type="danger">{error}</Text>}</label>
}

function QuestionEditor({ question, index, editorKey, disabled, onChange, onDelete, onInvalidate, onJsonErrorChange }) {
  const patch = (changes) => onChange(index, { ...question, ...changes })
  const patchBody = (changes) => patch({ body: { ...question.body, ...changes } })
  const patchAnswer = (changes) => patch({ answer: { ...question.answer, ...changes } })
  const options = question.body?.options ?? []

  const updateOption = (optionIndex, text) => patchBody({
    options: options.map((option, current) => current === optionIndex ? { ...option, text } : option),
  })

  return (
    <Card
      className="qbg-question-card"
      title={`第 ${question.displayNumber ?? index + 1} 题 · ${TYPE_LABELS[question.type] ?? question.type}`}
      extra={<Button danger disabled={disabled} type="text" icon={<DeleteOutlined />} onClick={() => onDelete(index)}>删除</Button>}
    >
      <fieldset className="qbg-editor-fieldset" disabled={disabled}>
      <div className="qbg-editor-grid">
        <label className="qbg-field qbg-field--wide"><span>题干</span><TextArea value={question.stem} autoSize={{ minRows: 2 }} onChange={(event) => patch({ stem: event.target.value })} /></label>
        <label className="qbg-field"><span>分值</span><InputNumber disabled={disabled} min={0.01} value={question.score} onChange={(score) => patch({ score })} /></label>
        <label className="qbg-field"><span>难度</span><Select disabled={disabled} value={question.difficulty} options={DIFFICULTIES} onChange={(difficulty) => patch({ difficulty })} /></label>
        <label className="qbg-field"><span>知识点（每行一个）</span><TextArea value={listValue(question.knowledgePoints)} onChange={(event) => patch({ knowledgePoints: splitLines(event.target.value) })} /></label>
        <label className="qbg-field"><span>标签（每行一个）</span><TextArea value={listValue(question.tags)} onChange={(event) => patch({ tags: splitLines(event.target.value) })} /></label>
      </div>

      {(question.type === 'single_choice' || question.type === 'multiple_choice') && (
        <div className="qbg-options-editor">
          <Text strong>选项与正确答案</Text>
          {question.type === 'single_choice' ? <Radio.Group disabled={disabled} value={question.answer?.correctOption} onChange={(event) => patchAnswer({ correctOption: event.target.value })}>{options.map((option, optionIndex) => (
            <Space key={option.key ?? optionIndex} className="qbg-option-row" align="start"><Radio value={option.key}>{option.key}</Radio><Input value={option.text} onChange={(event) => updateOption(optionIndex, event.target.value)} /></Space>
          ))}</Radio.Group> : options.map((option, optionIndex) => (
            <Space key={option.key ?? optionIndex} className="qbg-option-row" align="start">
              <Checkbox
                  disabled={disabled}
                  checked={(question.answer?.correctOptions ?? []).includes(option.key)}
                  onChange={(event) => {
                    const selected = new Set(question.answer?.correctOptions ?? [])
                    event.target.checked ? selected.add(option.key) : selected.delete(option.key)
                    patchAnswer({ correctOptions: [...selected] })
                  }}
                >{option.key}</Checkbox>
              <Input value={option.text} onChange={(event) => updateOption(optionIndex, event.target.value)} />
            </Space>
          ))}
        </div>
      )}

      {question.type === 'true_false' && (
        <div className="qbg-editor-grid">
          <label className="qbg-field qbg-field--wide"><span>判断陈述</span><TextArea value={question.body?.statement} onChange={(event) => patchBody({ statement: event.target.value })} /></label>
          <label className="qbg-field"><span>正确答案</span><Radio.Group value={question.answer?.correct} onChange={(event) => patchAnswer({ correct: event.target.value })}><Radio value>正确</Radio><Radio value={false}>错误</Radio></Radio.Group></label>
        </div>
      )}

      {question.type === 'fill_blank' && (
        <div className="qbg-options-editor">
          <label className="qbg-field"><span>带空格的题文</span><TextArea value={question.body?.text} onChange={(event) => patchBody({ text: event.target.value })} /></label>
          <Text strong>空格与可接受答案</Text>
          {(question.body?.blanks ?? []).map((blank, blankIndex) => {
            const answerBlank = (question.answer?.blanks ?? []).find((item) => item.id === blank.id) ?? { id: blank.id, answers: [] }
            return <Space key={blank.id ?? blankIndex} className="qbg-option-row" align="start"><Input value={blank.placeholder} placeholder="空格提示" onChange={(event) => patchBody({ blanks: question.body.blanks.map((item, current) => current === blankIndex ? { ...item, placeholder: event.target.value } : item) })} /><Input value={(answerBlank.answers ?? []).join(' / ')} placeholder="多个答案用 / 分隔" onChange={(event) => patchAnswer({ blanks: updateFillBlankAnswers(question.answer?.blanks, blank.id, event.target.value.split('/').map((value) => value.trim()).filter(Boolean)) })} /></Space>
          })}
        </div>
      )}

      {question.type === 'short_answer' && (
        <div className="qbg-editor-grid">
          <label className="qbg-field qbg-field--wide"><span>参考答案</span><TextArea value={question.answer?.referenceAnswer} autoSize={{ minRows: 3 }} onChange={(event) => patchAnswer({ referenceAnswer: event.target.value })} /></label>
          <label className="qbg-field qbg-field--wide"><span>得分要点（每行一个）</span><TextArea value={listValue(question.answer?.answerPoints)} onChange={(event) => patchAnswer({ answerPoints: splitLines(event.target.value) })} /></label>
        </div>
      )}

      <label className="qbg-field qbg-field--wide"><span>解析</span><TextArea value={question.analysis} autoSize={{ minRows: 2 }} onChange={(event) => patch({ analysis: event.target.value })} /></label>
      <div className="qbg-editor-grid">
        <JsonDraftField label="评分规则（JSON）" value={question.scoring} errorKey={`${editorKey}:scoring`} disabled={disabled} onInvalidate={onInvalidate} onErrorChange={onJsonErrorChange} onChange={(scoring) => patch({ scoring })} />
        <JsonDraftField label="来源依据（JSON 数组）" value={question.sourceBasis} errorKey={`${editorKey}:sourceBasis`} requireArray disabled={disabled} onInvalidate={onInvalidate} onErrorChange={onJsonErrorChange} onChange={(sourceBasis) => patch({ sourceBasis })} />
      </div>
      <Text type="secondary">来源 ID：{question.id ?? question.sourceQuestionId ?? '-'}
      </Text>
      </fieldset>
    </Card>
  )
}

export default function QuestionBankGeneratePage() {
  const [form] = Form.useForm()
  const navigate = useNavigate()
  const [sourceType, setSourceType] = useState('text')
  const [fileList, setFileList] = useState([])
  const [options, setOptions] = useState([])
  const [optionsLoading, setOptionsLoading] = useState(true)
  const [optionsError, setOptionsError] = useState('')
  const [generating, setGenerating] = useState(false)
  const [draft, setDraft] = useState(null)
  const [questions, setQuestions] = useState([])
  const [review, setReview] = useState(null)
  const [reviewing, setReviewing] = useState(false)
  const [jsonEditorErrors, setJsonEditorErrors] = useState(() => new Set())
  const [revision, setRevision] = useState(0)
  const [importing, setImporting] = useState(false)
  const [importResult, setImportResult] = useState(null)
  const selectedQuestionType = Form.useWatch('questionType', form)
  const generationLock = useRef(false)
  const importLock = useRef(false)
  const reviewSequence = useRef(0)
  const mounted = useRef(true)

  useEffect(() => () => {
    mounted.current = false
    reviewSequence.current += 1
  }, [])

  const invalidateReview = useCallback(() => {
    reviewSequence.current = invalidateReviewGeneration(reviewSequence.current)
    setReview(null)
    setReviewing(false)
  }, [])

  const changeJsonEditorError = useCallback((errorKey, hasError) => {
    setJsonEditorErrors((current) => {
      const next = updateJsonEditorErrors(current, errorKey, hasError)
      return next.size === current.size && [...next].every((key) => current.has(key)) ? current : next
    })
  }, [])

  const loadOptions = useCallback(async () => {
    setOptionsLoading(true)
    setOptionsError('')
    try {
      const response = await getQuestionGenerationOptions()
      if (!mounted.current) return
      const nextOptions = response.data?.questionTypes ?? []
      setOptions(nextOptions)
      const currentQuestionType = form.getFieldValue('questionType')
      if (currentQuestionType && !isQuestionTypeAvailable(nextOptions, currentQuestionType)) {
        form.setFieldValue('questionType', undefined)
      }
    } catch {
      if (mounted.current) {
        setOptions([])
        form.setFieldValue('questionType', undefined)
        setOptionsError('题型选项加载失败，当前无法安全生成题目。')
      }
    } finally {
      if (mounted.current) setOptionsLoading(false)
    }
  }, [form])

  useEffect(() => {
    loadOptions()
  }, [loadOptions])

  useEffect(() => {
    if (!draft || revision === 0) return undefined
    const sequence = ++reviewSequence.current
    setReviewing(true)
    const timer = window.setTimeout(async () => {
      try {
        const response = await reviewExamQuestions(buildImportPayload(draft, questions), draft.questionType)
        if (mounted.current && reviewSequence.current === sequence) setReview(response.data)
      } catch {
        if (mounted.current && reviewSequence.current === sequence) setReview({ valid: false, issues: ['复审请求失败'], warnings: [] })
      } finally {
        if (mounted.current && reviewSequence.current === sequence) setReviewing(false)
      }
    }, 500)
    return () => window.clearTimeout(timer)
  }, [draft, questions, revision])

  const resetGeneration = useCallback(() => {
    reviewSequence.current += 1
    setDraft(null)
    setQuestions([])
    setReview(null)
    setRevision(0)
    setJsonEditorErrors(new Set())
    setImportResult(null)
  }, [])

  const changeSourceType = (nextType) => {
    setSourceType(nextType)
    setFileList([])
    form.setFieldsValue({ sourceType: nextType, text: undefined })
  }

  const changeFile = ({ fileList: nextFiles }) => {
    const nextFile = nextFiles.at(-1)
    if (!nextFile) {
      setFileList([])
      return
    }
    if (!nextFile.name.toLowerCase().endsWith(`.${sourceType}`)) {
      message.warning(`请选择 .${sourceType} 文件`)
      setFileList([])
      return
    }
    setFileList([nextFile])
  }

  const handleGenerate = async (values) => {
    if (generationLock.current || importing || importResult || optionsError || optionsLoading) return
    if (!isQuestionTypeAvailable(options, values.questionType)) {
      form.setFieldValue('questionType', undefined)
      message.error('所选题型当前不可用，请重新选择')
      return
    }
    generationLock.current = true
    reviewSequence.current = invalidateReviewGeneration(reviewSequence.current)
    setReview(null)
    setReviewing(false)
    setJsonEditorErrors(new Set())
    setGenerating(true)
    try {
      const response = await generateQuestions(buildGenerationFormData(values, fileList[0]?.originFileObj))
      const nextDraft = response.data
      reviewSequence.current = invalidateReviewGeneration(reviewSequence.current)
      setDraft(nextDraft)
      setQuestions((nextDraft.questions ?? []).map((question, index) => ({ ...normalizeQuestionForEditor(question), displayNumber: index + 1 })))
      setReview({ valid: nextDraft.valid, issues: nextDraft.issues ?? [], warnings: nextDraft.warnings ?? [] })
      setRevision(0)
      setJsonEditorErrors(new Set())
      setImportResult(null)
      message.success(`已生成 ${nextDraft.generatedCount ?? nextDraft.questions?.length ?? 0} 道题`)
    } finally {
      generationLock.current = false
      setGenerating(false)
    }
  }

  const updateQuestion = (index, question) => {
    if (!canEditQuestions({ importing, completed: Boolean(importResult) })) return
    setQuestions((current) => current.map((item, currentIndex) => currentIndex === index ? serializeEditedQuestion(question) : item))
    setReview(null)
    setRevision((value) => value + 1)
  }

  const deleteQuestion = (index) => {
    if (!canEditQuestions({ importing, completed: Boolean(importResult) })) return
    const editorKey = questions[index]?.id ?? questions[index]?.sourceQuestionId ?? `question-${index}`
    setJsonEditorErrors((current) => clearJsonEditorErrorsForQuestion(current, editorKey))
    setQuestions((current) => removeQuestionAndRenumber(current, index))
    setReview(null)
    setRevision((value) => value + 1)
  }

  const handleImport = async () => {
    const status = { importing, completed: Boolean(importResult) }
    if (importLock.current || !canImportQuestions(review, questions, status, jsonEditorErrors.size)) return
    importLock.current = true
    setImporting(true)
    try {
      const response = await importExamQuestions(buildImportPayload(draft, questions), draft.questionType)
      setImportResult(response.data)
      message.success(`已导入 ${response.data?.importedCount ?? questions.length} 道题`)
    } finally {
      importLock.current = false
      setImporting(false)
    }
  }

  const selectedOption = options.find((option) => option.type === selectedQuestionType)
  const displayedReview = review ?? { valid: false, issues: [], warnings: [] }
  const editStatus = { importing, completed: Boolean(importResult) }
  const editingEnabled = canEditQuestions(editStatus)
  const selectedQuestionTypeAvailable = isQuestionTypeAvailable(options, selectedQuestionType)

  return (
    <div className="qbg-page">
      <section className="qbg-hero"><span>QUESTION GENERATOR</span><Title level={1}>题库智能生成</Title><Paragraph>从文本或课程文件生成题目，逐题编辑并复审后导入题库。</Paragraph></section>
      {!importResult && <Card title="1. 选择来源与生成参数" className="qbg-panel">
        {optionsError && <Alert className="qbg-options-error" type="error" showIcon message={optionsError} action={<Button size="small" onClick={loadOptions} loading={optionsLoading}>重试</Button>} />}
        <Spin spinning={optionsLoading}>
          <Form form={form} layout="vertical" initialValues={{ sourceType: 'text' }} onFinish={handleGenerate}>
            <Form.Item name="sourceType" label="来源类型"><Segmented block value={sourceType} options={[{ value: 'text', label: '粘贴文本' }, { value: 'docx', label: 'DOCX 文件' }, { value: 'txt', label: 'TXT 文件' }]} onChange={changeSourceType} /></Form.Item>
            {sourceType === 'text' ? <Form.Item name="text" label="课程材料" rules={[{ required: true, whitespace: true, message: '请输入课程材料' }]}><TextArea rows={8} maxLength={200000} showCount /></Form.Item> : <Form.Item label="课程文件" required><Upload.Dragger accept={`.${sourceType}`} maxCount={1} fileList={fileList} beforeUpload={() => false} onChange={changeFile} onRemove={() => { setFileList([]); return true }}><InboxOutlined className="qbg-upload-icon" /><p>点击或拖拽一个 {sourceType.toUpperCase()} 文件，选择后不会自动上传</p></Upload.Dragger></Form.Item>}
            <div className="qbg-form-grid">
              <Form.Item name="questionType" label="题型" rules={[{ required: true, message: '请选择题型' }]}><Select placeholder="选择可用题型" options={options.map((option) => ({ value: option.type, disabled: !option.available, label: option.available ? (TYPE_LABELS[option.type] ?? option.type) : `${TYPE_LABELS[option.type] ?? option.type}（${option.unavailableReason || '当前不可用'}）` }))} /></Form.Item>
              <Form.Item name="maxQuestions" label="最大题量"><InputNumber min={1} precision={0} placeholder="留空由智能体决定" /></Form.Item>
              <Form.Item name="difficulty" label="难度"><Select allowClear placeholder="不限" options={DIFFICULTIES} /></Form.Item>
              <Form.Item name="sourceTitle" label="来源标题"><Input maxLength={160} placeholder="可选" /></Form.Item>
            </div>
            {selectedOption && <Alert type={selectedOption.available ? 'info' : 'warning'} showIcon message={selectedOption.available ? `执行智能体：${selectedOption.agentRole || selectedOption.agentName}` : selectedOption.unavailableReason} />}
            <Button className="qbg-primary" type="primary" htmlType="submit" loading={generating} disabled={optionsLoading || Boolean(optionsError) || !selectedQuestionTypeAvailable || generating || importing || (sourceType !== 'text' && fileList.length !== 1)}>生成题目</Button>
          </Form>
        </Spin>
      </Card>}

      {draft && !importResult && <Card title="2. 预览、编辑与复审" className="qbg-panel" extra={<Tag color="blue">来源智能体：{draft.agentName}</Tag>}>
        <div className="qbg-stats"><Statistic title="生成题数" value={questions.length} /><Statistic title="题型" value={TYPE_LABELS[draft.questionType] ?? draft.questionType} /><Statistic title="来源" value={draft.sourceTitle || draft.originalFilename || '-'} /></div>
        {!!draft.missingInfo?.length && <Alert showIcon type="info" message="缺失信息" description={<List size="small" dataSource={draft.missingInfo} renderItem={(item) => <List.Item>{item}</List.Item>} />} />}
        {!!displayedReview.issues?.length && <Alert showIcon type="error" message="必须修复的问题" description={displayedReview.issues.join('；')} />}
        {!!displayedReview.warnings?.length && <Alert showIcon type="warning" message="建议检查" description={displayedReview.warnings.join('；')} />}
        <Divider />
        {questions.length ? questions.map((question, index) => {
          const editorKey = question.id ?? question.sourceQuestionId ?? `question-${index}`
          return <QuestionEditor key={`${editorKey}-${index}`} question={question} index={index} editorKey={editorKey} disabled={!editingEnabled} onChange={updateQuestion} onDelete={deleteQuestion} onInvalidate={invalidateReview} onJsonErrorChange={changeJsonEditorError} />
        }) : <Empty description="当前没有可导入的题目" />}
        <div className="qbg-review-actions"><Text type="secondary">{reviewing ? '正在复审编辑结果…' : displayedReview.valid ? '已通过复审' : '未通过复审'}</Text><Button type="primary" loading={importing} disabled={reviewing || !canImportQuestions(review, questions, editStatus, jsonEditorErrors.size)} onClick={handleImport}>导入题库</Button></div>
      </Card>}

      {importResult && <Card title="3. 导入完成" className="qbg-panel qbg-success"><Alert showIcon type="success" message={`成功导入 ${importResult.importedCount ?? questions.length} 道题`} /><Space><Button type="primary" onClick={() => navigate(QUESTION_BANK_ROUTES.questions)}>查看题库</Button><Button onClick={resetGeneration}>继续生成</Button></Space></Card>}
    </div>
  )
}
