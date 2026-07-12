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
  canImportQuestions,
  normalizeQuestionForEditor,
  removeQuestionAndRenumber,
  serializeEditedQuestion,
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

function QuestionEditor({ question, index, onChange, onDelete }) {
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
      extra={<Button danger type="text" icon={<DeleteOutlined />} onClick={() => onDelete(index)}>删除</Button>}
    >
      <div className="qbg-editor-grid">
        <label className="qbg-field qbg-field--wide"><span>题干</span><TextArea value={question.stem} autoSize={{ minRows: 2 }} onChange={(event) => patch({ stem: event.target.value })} /></label>
        <label className="qbg-field"><span>分值</span><InputNumber min={0} value={question.score} onChange={(score) => patch({ score })} /></label>
        <label className="qbg-field"><span>难度</span><Select allowClear value={question.difficulty} options={DIFFICULTIES} onChange={(difficulty) => patch({ difficulty })} /></label>
        <label className="qbg-field"><span>知识点（每行一个）</span><TextArea value={listValue(question.knowledgePoints)} onChange={(event) => patch({ knowledgePoints: splitLines(event.target.value) })} /></label>
        <label className="qbg-field"><span>标签（每行一个）</span><TextArea value={listValue(question.tags)} onChange={(event) => patch({ tags: splitLines(event.target.value) })} /></label>
      </div>

      {(question.type === 'single_choice' || question.type === 'multiple_choice') && (
        <div className="qbg-options-editor">
          <Text strong>选项与正确答案</Text>
          {options.map((option, optionIndex) => (
            <Space key={option.key ?? optionIndex} className="qbg-option-row" align="start">
              {question.type === 'single_choice' ? (
                <Radio checked={question.answer?.correctOption === option.key} onChange={() => patchAnswer({ correctOption: option.key })}>{option.key}</Radio>
              ) : (
                <Checkbox
                  checked={(question.answer?.correctOptions ?? []).includes(option.key)}
                  onChange={(event) => {
                    const selected = new Set(question.answer?.correctOptions ?? [])
                    event.target.checked ? selected.add(option.key) : selected.delete(option.key)
                    patchAnswer({ correctOptions: [...selected] })
                  }}
                >{option.key}</Checkbox>
              )}
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
            const answerBlank = (question.answer?.blanks ?? [])[blankIndex] ?? { id: blank.id, answers: [] }
            return <Space key={blank.id ?? blankIndex} className="qbg-option-row" align="start"><Input value={blank.placeholder} placeholder="空格提示" onChange={(event) => patchBody({ blanks: question.body.blanks.map((item, current) => current === blankIndex ? { ...item, placeholder: event.target.value } : item) })} /><Input value={(answerBlank.answers ?? []).join(' / ')} placeholder="多个答案用 / 分隔" onChange={(event) => patchAnswer({ blanks: (question.answer?.blanks ?? []).map((item, current) => current === blankIndex ? { ...item, answers: event.target.value.split('/').map((value) => value.trim()).filter(Boolean) } : item) })} /></Space>
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
        <label className="qbg-field"><span>评分规则（JSON）</span><TextArea defaultValue={JSON.stringify(question.scoring, null, 2)} autoSize={{ minRows: 3 }} onBlur={(event) => { try { patch({ scoring: JSON.parse(event.target.value) }) } catch { message.warning('评分规则必须是有效 JSON') } }} /></label>
        <label className="qbg-field"><span>来源依据（JSON 数组）</span><TextArea defaultValue={JSON.stringify(question.sourceBasis, null, 2)} autoSize={{ minRows: 3 }} onBlur={(event) => { try { const value = JSON.parse(event.target.value); if (!Array.isArray(value)) throw new Error(); patch({ sourceBasis: value }) } catch { message.warning('来源依据必须是有效 JSON 数组') } }} /></label>
      </div>
      <Text type="secondary">来源 ID：{question.id ?? question.sourceQuestionId ?? '-'}
      </Text>
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
  const [generating, setGenerating] = useState(false)
  const [draft, setDraft] = useState(null)
  const [questions, setQuestions] = useState([])
  const [review, setReview] = useState(null)
  const [reviewing, setReviewing] = useState(false)
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

  useEffect(() => {
    let active = true
    getQuestionGenerationOptions().then((response) => {
      if (active) setOptions(response.data?.questionTypes ?? [])
    }).catch(() => {}).finally(() => { if (active) setOptionsLoading(false) })
    return () => { active = false }
  }, [])

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
    if (generationLock.current) return
    generationLock.current = true
    setGenerating(true)
    try {
      const response = await generateQuestions(buildGenerationFormData(values, fileList[0]?.originFileObj))
      const nextDraft = response.data
      setDraft(nextDraft)
      setQuestions((nextDraft.questions ?? []).map((question, index) => ({ ...normalizeQuestionForEditor(question), displayNumber: index + 1 })))
      setReview({ valid: nextDraft.valid, issues: nextDraft.issues ?? [], warnings: nextDraft.warnings ?? [] })
      setRevision(0)
      setImportResult(null)
      message.success(`已生成 ${nextDraft.generatedCount ?? nextDraft.questions?.length ?? 0} 道题`)
    } finally {
      generationLock.current = false
      setGenerating(false)
    }
  }

  const updateQuestion = (index, question) => {
    setQuestions((current) => current.map((item, currentIndex) => currentIndex === index ? serializeEditedQuestion(question) : item))
    setReview(null)
    setRevision((value) => value + 1)
  }

  const deleteQuestion = (index) => {
    setQuestions((current) => removeQuestionAndRenumber(current, index))
    setReview(null)
    setRevision((value) => value + 1)
  }

  const handleImport = async () => {
    if (importLock.current || !canImportQuestions(review, questions)) return
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

  return (
    <div className="qbg-page">
      <section className="qbg-hero"><span>QUESTION GENERATOR</span><Title level={1}>题库智能生成</Title><Paragraph>从文本或课程文件生成题目，逐题编辑并复审后导入题库。</Paragraph></section>
      <Card title="1. 选择来源与生成参数" className="qbg-panel">
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
            <Button className="qbg-primary" type="primary" htmlType="submit" loading={generating} disabled={generating || (sourceType !== 'text' && fileList.length !== 1)}>生成题目</Button>
          </Form>
        </Spin>
      </Card>

      {draft && <Card title="2. 预览、编辑与复审" className="qbg-panel" extra={<Tag color="blue">来源智能体：{draft.agentName}</Tag>}>
        <div className="qbg-stats"><Statistic title="生成题数" value={questions.length} /><Statistic title="题型" value={TYPE_LABELS[draft.questionType] ?? draft.questionType} /><Statistic title="来源" value={draft.sourceTitle || draft.originalFilename || '-'} /></div>
        {!!draft.missingInfo?.length && <Alert showIcon type="info" message="缺失信息" description={<List size="small" dataSource={draft.missingInfo} renderItem={(item) => <List.Item>{item}</List.Item>} />} />}
        {!!displayedReview.issues?.length && <Alert showIcon type="error" message="必须修复的问题" description={displayedReview.issues.join('；')} />}
        {!!displayedReview.warnings?.length && <Alert showIcon type="warning" message="建议检查" description={displayedReview.warnings.join('；')} />}
        <Divider />
        {questions.length ? questions.map((question, index) => <QuestionEditor key={`${question.id ?? question.sourceQuestionId ?? 'question'}-${index}`} question={question} index={index} onChange={updateQuestion} onDelete={deleteQuestion} />) : <Empty description="当前没有可导入的题目" />}
        <div className="qbg-review-actions"><Text type="secondary">{reviewing ? '正在复审编辑结果…' : displayedReview.valid ? '已通过复审' : '未通过复审'}</Text><Button type="primary" loading={importing} disabled={reviewing || importing || !canImportQuestions(review, questions)} onClick={handleImport}>导入题库</Button></div>
      </Card>}

      {importResult && <Card title="3. 导入完成" className="qbg-panel qbg-success"><Alert showIcon type="success" message={`成功导入 ${importResult.importedCount ?? questions.length} 道题`} /><Space><Button type="primary" onClick={() => navigate(QUESTION_BANK_ROUTES.questions)}>查看题库</Button><Button onClick={resetGeneration}>继续生成</Button></Space></Card>}
    </div>
  )
}
