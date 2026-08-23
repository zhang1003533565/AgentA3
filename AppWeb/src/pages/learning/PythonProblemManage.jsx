import { useCallback, useEffect, useState } from 'react'
import { PlusOutlined, ReloadOutlined, RobotOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons'
import {
  Button,
  Card,
  Descriptions,
  Divider,
  Drawer,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import {
  aiGeneratePythonProblems,
  createPythonProblem,
  deletePythonProblem,
  getPythonProblemAdminList,
  updatePythonProblem,
} from '../../api/pythonProblem'

const { TextArea } = Input
const { Title, Text } = Typography

const MODE_OPTIONS = [
  { value: undefined, label: '精确匹配' },
  { value: 'set', label: '无序集合 set' },
  { value: 'deepset', label: '深度无序 deepset' },
]

const DIFFICULTY_META = {
  easy: { label: '简单', color: 'green' },
  medium: { label: '中等', color: 'orange' },
  hard: { label: '困难', color: 'red' },
}

/** 从管理列表行数据重建保存请求（上下架切换等场景复用） */
function toRequest(record, overrides = {}) {
  return {
    number: record.number,
    title: record.title,
    difficulty: record.difficulty,
    passRate: record.passRate,
    submissions: record.submissions,
    tags: record.tags || [],
    description: record.description || '',
    examples: record.examples || [],
    defaultCode: record.defaultCode || '',
    funcName: record.funcName || '',
    testcases: record.testcases || [],
    similarIds: record.similarIds || [],
    enabled: record.enabled !== false,
    ...overrides,
  }
}

function PythonProblemManage() {
  const [list, setList] = useState([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [difficulty, setDifficulty] = useState()
  const [enabledFilter, setEnabledFilter] = useState()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm()
  // AI 生成题目
  const [aiModalOpen, setAiModalOpen] = useState(false)
  const [aiGenerating, setAiGenerating] = useState(false)
  const [draftItems, setDraftItems] = useState([]) // 草稿箱：所有未入库题目（累积）
  const [draftRound, setDraftRound] = useState(null) // 当前生成轮次 {ids, prompt, spec}
  const [aiSpec, setAiSpec] = useState(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewItem, setPreviewItem] = useState(null)
  const [lastPrompt, setLastPrompt] = useState('')
  const [reviseInput, setReviseInput] = useState('')
  const [revising, setRevising] = useState(false)
  const [editingDraftId, setEditingDraftId] = useState(null) // 正在编辑入库的草稿题 draftId
  const [aiForm] = Form.useForm()

  // ---- 草稿箱存取（累积存放未入库题目）----
  const DRAFTBOX_KEY = 'py_problem_ai_draftbox'
  const OLD_DRAFT_KEY = 'py_problem_ai_draft'
  const REVISE_KEY = 'py_problem_ai_revise_input'

  const genDraftId = () => `d${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

  // “继续调整”输入框文字持久化（退出/刷新不丢失）
  const saveReviseInput = (value) => {
    try {
      if (value) {
        localStorage.setItem(REVISE_KEY, value)
      } else {
        localStorage.removeItem(REVISE_KEY)
      }
    } catch {
      // ignore
    }
  }

  const loadReviseInput = () => {
    try {
      return localStorage.getItem(REVISE_KEY) || ''
    } catch {
      return ''
    }
  }

  const loadDraftBox = () => {
    try {
      const raw = localStorage.getItem(DRAFTBOX_KEY)
      if (!raw) return { items: [], lastRound: null }
      const d = JSON.parse(raw)
      return {
        items: Array.isArray(d.items) ? d.items : [],
        lastRound: d.lastRound || null,
      }
    } catch {
      return { items: [], lastRound: null }
    }
  }

  const saveDraftBox = (items, lastRound) => {
    try {
      localStorage.setItem(DRAFTBOX_KEY, JSON.stringify({ items, lastRound, updatedAt: Date.now() }))
    } catch {
      // 存储失败不阻断
    }
    setDraftItems(items)
    setDraftRound(lastRound)
  }

  const removeFromDraftBox = (draftId) => {
    const box = loadDraftBox()
    const remaining = box.items.filter((i) => i.draftId !== draftId)
    // 若移除的是当前轮次的题，同步更新轮次 ids
    let lastRound = box.lastRound
    if (lastRound && Array.isArray(lastRound.ids)) {
      lastRound = { ...lastRound, ids: lastRound.ids.filter((id) => id !== draftId) }
    }
    saveDraftBox(remaining, lastRound)
  }

  const clearDraftBox = () => {
    try {
      localStorage.removeItem(DRAFTBOX_KEY)
    } catch {
      // ignore
    }
    setDraftItems([])
    setDraftRound(null)
    setAiSpec(null)
  }

  const fetchList = useCallback(async () => {
    setLoading(true)
    try {
      const params = {}
      if (keyword.trim()) params.keyword = keyword.trim()
      if (difficulty) params.difficulty = difficulty
      if (enabledFilter !== undefined && enabledFilter !== null) params.enabled = enabledFilter
      const res = await getPythonProblemAdminList(params)
      setList(Array.isArray(res.data) ? res.data : [])
    } catch {
      // 请求层已统一提示
    } finally {
      setLoading(false)
    }
  }, [keyword, difficulty, enabledFilter])

  useEffect(() => {
    fetchList()
    // 载入草稿箱，并把旧的单份草稿缓存迁移进来
    const box = loadDraftBox()
    let items = box.items
    let lastRound = box.lastRound
    try {
      const oldRaw = localStorage.getItem(OLD_DRAFT_KEY)
      if (oldRaw && !items.length) {
        const old = JSON.parse(oldRaw)
        if (old && Array.isArray(old.problems) && old.problems.length) {
          items = old.problems.map((p) => ({ ...p, draftId: genDraftId() }))
          lastRound = { ids: items.map((i) => i.draftId), prompt: old.prompt || '', spec: old.spec || null }
          localStorage.removeItem(OLD_DRAFT_KEY)
        }
      }
    } catch {
      // ignore
    }
    setDraftItems(items)
    setDraftRound(lastRound)
    if (lastRound) {
      setLastPrompt(lastRound.prompt || '')
      setAiSpec(lastRound.spec || null)
    }
    // 载入"继续调整"输入框的缓存文字
    setReviseInput(loadReviseInput())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditing(null)
    setEditingDraftId(null)
    form.resetFields()
    form.setFieldsValue({ difficulty: 'easy', enabled: true })
    setModalOpen(true)
  }

  // ---- AI 生成题目（对话式）----
  const openAiGenerate = () => {
    aiForm.resetFields()
    aiForm.setFieldsValue({ prompt: '', referenceTitle: undefined, count: 1 })
    setAiModalOpen(true)
  }

  const handleAiGenerate = async () => {
    let values
    try {
      values = await aiForm.validateFields()
    } catch {
      return
    }
    setAiGenerating(true)
    try {
      const res = await aiGeneratePythonProblems({
        prompt: values.prompt,
        referenceTitle: values.referenceTitle || '',
        difficulty: values.difficulty || '',
        count: values.count || 1,
      })
      const data = res.data || {}
      const generated = Array.isArray(data.problems) ? data.problems : []
      if (!generated.length) {
        message.warning('AI 未生成题目，请调整描述后重试')
        return
      }
      // 生成的题目追加进草稿箱（不覆盖之前未入库的题），并记录本轮
      const newItems = generated.map((p) => ({ ...p, draftId: genDraftId() }))
      const box = loadDraftBox()
      const items = [...box.items, ...newItems]
      const round = { ids: newItems.map((i) => i.draftId), prompt: values.prompt, spec: data.spec || null }
      saveDraftBox(items, round)
      setAiSpec(data.spec || null)
      setLastPrompt(values.prompt)
      setAiModalOpen(false)
      setPreviewOpen(true)
    } catch {
      // 请求层已统一提示
    } finally {
      setAiGenerating(false)
    }
  }

  // 基于上一轮结果与用户调整意见修订生成
  const handleRevise = async () => {
    const feedback = (reviseInput || '').trim()
    if (!feedback) {
      message.warning('请先描述想怎么调整')
      return
    }
    setRevising(true)
    try {
      // 修订对象=当前轮次的题目（从草稿箱按 draftRound.ids 取出，去 solution 省 token）
      const roundIds = (draftRound && Array.isArray(draftRound.ids) && draftRound.ids.length)
        ? draftRound.ids
        : draftItems.map((i) => i.draftId)
      const box = loadDraftBox()
      const roundProblems = box.items.filter((i) => roundIds.includes(i.draftId))
      if (!roundProblems.length) {
        message.warning('没有可调整的上一轮题目，请重新生成')
        return
      }
      const previousProblems = roundProblems.map((p) => ({
        title: p.title,
        difficulty: p.difficulty,
        description: p.description,
        funcName: p.funcName,
        tags: p.tags,
        examples: p.examples,
        testcases: p.testcases,
      }))
      const res = await aiGeneratePythonProblems({
        prompt: lastPrompt,
        difficulty: '',
        count: roundProblems.length,
        previousFeedback: feedback,
        previousProblems,
      })
      const data = res.data || {}
      const generated = Array.isArray(data.problems) ? data.problems : []
      if (!generated.length) {
        message.warning('AI 未生成调整结果，请换个说法重试')
        return
      }
      // 用新结果替换本轮的题（不与其他轮次堆积），更新草稿箱与轮次
      const newItems = generated.map((p) => ({ ...p, draftId: genDraftId() }))
      const remaining = box.items.filter((i) => !roundIds.includes(i.draftId))
      const items = [...remaining, ...newItems]
      const round = { ids: newItems.map((i) => i.draftId), prompt: lastPrompt, spec: data.spec || null }
      saveDraftBox(items, round)
      setAiSpec(data.spec || null)
      setReviseInput('')
      saveReviseInput('')
      message.success('已按你的意见重新生成')
    } catch {
      // 请求层已统一提示
    } finally {
      setRevising(false)
    }
  }

  // 规格翻译（供预览顶部展示 AI 理解到的需求）
  const specText = (spec) => {
    if (!spec) return null
    const tagModeMap = { subset: '只包含', include: '至少包含', unset: '不限制' }
    const levelMap = { beginner: '初学者', normal: '普通', advanced: '进阶' }
    const refModeMap = { variation: '变式', similar: '相似' }
    const parts = []
    if (Array.isArray(spec.tags) && spec.tags.length) {
      const mode = tagModeMap[spec.tagMode] || ''
      parts.push(`${mode ? mode + ' ' : ''}${spec.tags.join('、')}`)
    } else {
      parts.push('主题不限')
    }
    if (Array.isArray(spec.excludeTags) && spec.excludeTags.length) {
      parts.push(`排除：${spec.excludeTags.join('、')}`)
    }
    if (spec.level) parts.push(levelMap[spec.level] || spec.level)
    if (spec.referenceTitle) {
      parts.push(`参考《${spec.referenceTitle}》（${refModeMap[spec.refMode] || spec.refMode || '变式'}）`)
    }
    if (spec.count) parts.push(`${spec.count} 道`)
    if (spec.difficulty) parts.push(DIFFICULTY_META[spec.difficulty]?.label || spec.difficulty)
    return parts.join(' · ')
  }

  // 生成题目的"编辑入库"：预填到新增表单，人工确认后走创建接口
  const openEditGenerated = (g) => {
    setEditing(null)
    setEditingDraftId(g.draftId || null)
    form.resetFields()
    form.setFieldsValue({
      number: g.number,
      title: g.title,
      difficulty: g.difficulty,
      passRate: null,
      submissions: '',
      tags: g.tags || [],
      enabled: true,
      description: g.description || '',
      defaultCode: g.defaultCode || '',
      funcName: g.funcName || '',
      examples: g.examples || [],
      testcases: g.testcases || [],
      solution: g.solution || [],
      similarText: '',
    })
    setPreviewOpen(false)
    setPreviewItem(null)
    setModalOpen(true)
  }

  // 生成结果详情预览（抽屉）：打开抽屉时先隐藏草稿箱弹窗，避免弹窗遮罩挡住抽屉的操作按钮
  const openPreviewItem = (g) => {
    setPreviewItem(g)
    setPreviewOpen(false)
  }

  // 预览中"直接入库"：按生成结果直接创建（人工已确认内容无误）
  const handleDirectCreate = async () => {
    const g = previewItem
    if (!g) return
    setSubmitting(true)
    try {
      await createPythonProblem({
        number: g.number,
        title: g.title,
        difficulty: g.difficulty,
        tags: g.tags || [],
        description: g.description || '',
        examples: g.examples || [],
        defaultCode: g.defaultCode || '',
        funcName: g.funcName || '',
        testcases: g.testcases || [],
        solution: g.solution || [],
        similarIds: [],
        enabled: true,
      })
      message.success(`题目 #${g.number} 已入库`)
      if (g.draftId) removeFromDraftBox(g.draftId)
      setPreviewItem(null)
      // 若草稿箱还有剩余题目，重新打开草稿箱方便继续操作
      const box = loadDraftBox()
      setPreviewOpen((box.items || []).length > 0)
      fetchList()
    } catch {
      // 请求层已统一提示
    } finally {
      setSubmitting(false)
    }
  }

  const openEdit = (record) => {
    setEditing(record)
    form.resetFields()
    form.setFieldsValue({
      number: record.number,
      title: record.title,
      difficulty: record.difficulty,
      passRate: record.passRate,
      submissions: record.submissions,
      tags: record.tags || [],
      enabled: record.enabled !== false,
      description: record.description || '',
      defaultCode: record.defaultCode || '',
      funcName: record.funcName || '',
      examples: record.examples || [],
      testcases: record.testcases || [],
      solution: record.solution || [],
      similarText: (record.similarIds || []).join(','),
    })
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    let values
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    // 可视化表单数据规范化：examples 的 input/output 由多行文本转字符串数组；accepts 逗号分隔转数组
    const normalizeLines = (arr) =>
      Array.isArray(arr) ? arr.filter((line) => String(line || '').trim() !== '') : []
    const examples = (values.examples || []).map((ex) => ({
      input: normalizeLines(ex.input),
      output: normalizeLines(ex.output),
      ...(ex.explain ? { explain: ex.explain } : {}),
    }))
    const testcases = (values.testcases || []).map((tc) => {
      const item = { input: tc.input, expected: tc.expected }
      if (tc.mode) item.mode = tc.mode
      if (tc.accepts && String(tc.accepts).trim()) {
        item.accepts = String(tc.accepts)
          .split(/[,，\s]+/)
          .filter((v) => v)
      }
      return item
    })
    const solution = (values.solution || []).map((s) => ({
      name: s.name || '',
      idea: s.idea || '',
      code: s.code || '',
      complexity: s.complexity || '',
    }))
    const funcName = (values.funcName || '').trim()
    if (funcName) {
      if (testcases.length === 0) {
        message.error('填写了判题函数名时，必须至少提供一个测试用例')
        return
      }
      const invalid = testcases.find(
        (tc) => typeof tc?.input !== 'string' || !tc.input.trim() || typeof tc?.expected !== 'string'
      )
      if (invalid) {
        message.error('每个测试用例需包含 input 与 expected 字符串字段')
        return
      }
    }
    const similarIds = (values.similarText || '')
      .split(/[,，\s]+/)
      .map((item) => Number(item))
      .filter((item) => Number.isFinite(item))

    const payload = {
      number: values.number,
      title: (values.title || '').trim(),
      difficulty: values.difficulty,
      passRate: values.passRate ?? null,
      submissions: values.submissions || '',
      tags: values.tags || [],
      description: values.description || '',
      examples,
      defaultCode: values.defaultCode || '',
      funcName,
      testcases,
      solution,
      similarIds,
      enabled: values.enabled !== false,
    }

    setSubmitting(true)
    try {
      if (editing) {
        await updatePythonProblem(editing.id, payload)
        message.success('更新成功')
      } else {
        await createPythonProblem(payload)
        message.success('创建成功')
        // 若入库的是草稿箱中的题，入库成功后将其移出草稿箱
        if (editingDraftId) {
          removeFromDraftBox(editingDraftId)
          setEditingDraftId(null)
        }
      }
      setModalOpen(false)
      fetchList()
    } catch {
      // 请求层已统一提示
    } finally {
      setSubmitting(false)
    }
  }

  const handleToggleEnabled = async (record, checked) => {
    try {
      await updatePythonProblem(record.id, toRequest(record, { enabled: checked }))
      message.success(checked ? '已上架' : '已下架')
      fetchList()
    } catch {
      // 请求层已统一提示
    }
  }

  const handleDelete = async (record) => {
    try {
      await deletePythonProblem(record.id)
      message.success('删除成功')
      fetchList()
    } catch {
      // 请求层已统一提示
    }
  }

  const columns = [
    { title: '题号', dataIndex: 'number', width: 70, sorter: (a, b) => (a.number || 0) - (b.number || 0) },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (text, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{text}</Text>
          {(record.tags || []).length > 0 && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {(record.tags || []).join(' · ')}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: '难度',
      dataIndex: 'difficulty',
      width: 90,
      render: (value) => {
        const meta = DIFFICULTY_META[value] || { label: value, color: 'default' }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '判题',
      dataIndex: 'judgeable',
      width: 100,
      render: (value) =>
        value ? <Tag color="blue">可判题</Tag> : <Tag>暂不支持</Tag>,
    },
    {
      title: '上架',
      dataIndex: 'enabled',
      width: 80,
      render: (value, record) => (
        <Switch checked={value !== false} onChange={(checked) => handleToggleEnabled(record, checked)} />
      ),
    },
    { title: '更新时间', dataIndex: 'updateTime', width: 170 },
    {
      title: '操作',
      width: 130,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确定删除该题目？"
            description="删除后小程序题库将不再展示此题"
            onConfirm={() => handleDelete(record)}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Space wrap>
          <Title level={4} style={{ margin: 0 }}>
            Python 题库管理
          </Title>
          <Input.Search
            placeholder="搜索标题 / 题号"
            allowClear
            style={{ width: 220 }}
            onSearch={(value) => {
              setKeyword(value)
            }}
          />
          <Select
            placeholder="难度"
            allowClear
            style={{ width: 120 }}
            value={difficulty}
            onChange={setDifficulty}
            options={[
              { value: 'easy', label: '简单' },
              { value: 'medium', label: '中等' },
              { value: 'hard', label: '困难' },
            ]}
          />
          <Select
            placeholder="上架状态"
            allowClear
            style={{ width: 130 }}
            value={enabledFilter}
            onChange={setEnabledFilter}
            options={[
              { value: true, label: '已上架' },
              { value: false, label: '已下架' },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={fetchList}>
            查询
          </Button>
        </Space>
        <Space>
          <Button onClick={() => setPreviewOpen(true)}>
            草稿箱（{draftItems.length}）
          </Button>
          <Button icon={<RobotOutlined />} onClick={openAiGenerate}>
            AI 生成题目
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增题目
          </Button>
        </Space>
      </Space>

      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={list}
        pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 道题` }}
      />

      {/* AI 生成题目弹窗（对话式） */}
      <Modal
        title="AI 生成题目"
        open={aiModalOpen}
        onCancel={() => setAiModalOpen(false)}
        onOk={handleAiGenerate}
        confirmLoading={aiGenerating}
        okText="生成"
        cancelText="取消"
        width={620}
        destroyOnClose
        maskClosable={false}
      >
        <Form form={aiForm} layout="vertical">
          <Form.Item
            name="prompt"
            label="描述你的出题需求"
            tooltip="说得越细越好：考点边界（只准用/别用）、参考题目、难度、数量、学习水平"
            rules={[{ required: true, message: '请描述你的出题需求' }]}
          >
            <TextArea
              rows={4}
              placeholder={'例如：生成 1 道简单的题，只准用数组，类似两数之和但换成字符串场景；我还没学过哈希表和双指针，别出这两类'}
              maxLength={500}
            />
          </Form.Item>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item
              name="referenceTitle"
              label="参考题目（可选）"
              tooltip="从题库选一道题，AI 会基于它生成变式题；不选则按描述自由命题"
              style={{ flex: 1 }}
            >
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="从题库选择参考题，如：两数之和"
                options={(list || []).map((p) => ({
                  value: p.title,
                  label: `#${p.number} ${p.title}`,
                }))}
              />
            </Form.Item>
            <Form.Item name="difficulty" label="难度（可选）" style={{ width: 150 }}>
              <Select
                allowClear
                placeholder="不限"
                options={[
                  { value: 'easy', label: '简单' },
                  { value: 'medium', label: '中等' },
                  { value: 'hard', label: '困难' },
                ]}
              />
            </Form.Item>
            <Form.Item name="count" label="生成数量" style={{ width: 130 }}>
              <InputNumber min={1} max={5} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            AI 会先解析你描述中的约束（只含哪些考点、排除哪些、参考哪道题等），生成前展示给你确认；生成的是草稿，预览审核后再入库。
          </Text>
        </Form>
      </Modal>

      {/* 草稿箱弹窗：累积存放所有未入库题目 */}
      <Modal
        title={`草稿箱（${draftItems.length} 道未入库）`}
        open={previewOpen}
        onCancel={() => {
          setPreviewOpen(false)
          setPreviewItem(null)
        }}
        footer={null}
        width={780}
        destroyOnClose
        maskClosable={false}
      >
        {aiSpec && (
          <div
            style={{
              background: '#eef4ff',
              border: '1px solid #d6e4ff',
              borderRadius: 8,
              padding: '8px 14px',
              marginBottom: 14,
              fontSize: 13,
            }}
          >
            <Text type="secondary" style={{ marginRight: 8 }}>
              最近一次生成的需求：
            </Text>
            <Text strong>{specText(aiSpec)}</Text>
            <Text type="secondary" style={{ display: 'block', marginTop: 2, fontSize: 12 }}>
              理解有偏差？用下方"继续调整"针对最近一轮重新生成。
            </Text>
          </div>
        )}
        <Space direction="vertical" style={{ width: '100%' }}>
          {draftItems.length === 0 && (
            <Text type="secondary">草稿箱为空，点"AI 生成题目"生成新题。</Text>
          )}
          {draftItems.map((g) => (
            <div
              key={g.draftId}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '10px 14px',
                border: '1px solid #e5e7eb',
                borderRadius: 8,
                background: '#fafafa',
              }}
            >
              <Text strong style={{ minWidth: 36 }}>#{g.number}</Text>
              <Text style={{ flex: 1 }} ellipsis={{ tooltip: g.description || g.title }}>
                {g.title}
              </Text>
              <Tag color={DIFFICULTY_META[g.difficulty]?.color || 'default'}>
                {DIFFICULTY_META[g.difficulty]?.label || g.difficulty}
              </Tag>
              <Text type="secondary" style={{ fontSize: 12 }}>
                用例 {(g.testcases || []).length} 个
              </Text>
              {g.selfCheck === 'pass' && <Tag color="green">自校验通过</Tag>}
              {g.selfCheck === 'fail' && (
                <Tag color="orange" title={g.selfCheckDetail || ''}>
                  用例存疑
                </Tag>
              )}
              {g.selfCheck === 'skip' && <Tag>未校验</Tag>}
              <Button size="small" icon={<EyeOutlined />} onClick={() => openPreviewItem(g)}>
                预览
              </Button>
              <Button type="primary" size="small" onClick={() => openEditGenerated(g)}>
                编辑入库
              </Button>
              <Button
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={() => removeFromDraftBox(g.draftId)}
              >
                删除
              </Button>
            </div>
          ))}
        </Space>
        <Divider style={{ margin: '14px 0 10px' }} />
        <div style={{ width: '100%' }}>
          <Input.TextArea
            rows={3}
            value={reviseInput}
            onChange={(e) => {
              setReviseInput(e.target.value)
              saveReviseInput(e.target.value)
            }}
            placeholder="对最近一轮不满意？说说怎么调整，如：太难了换更基础的思路 / 不要哈希表 / 改成字符串场景"
            maxLength={300}
            style={{ width: '100%', marginBottom: 10 }}
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" ghost loading={revising} onClick={handleRevise}>
              继续调整
            </Button>
          </div>
        </div>
        <Text type="secondary" style={{ display: 'block', marginTop: 12, fontSize: 12 }}>
          草稿箱会累积保存所有未入库题目，关闭或刷新页面不丢失；入库后自动移出。点"预览"看题面/用例/标准答案，"编辑入库"核对后保存。
        </Text>
        {draftItems.length > 0 && (
          <Popconfirm
            title="清空草稿箱？"
            description="将删除所有未入库的草稿题目，此操作不可恢复。"
            onConfirm={() => {
              clearDraftBox()
              setPreviewOpen(false)
            }}
          >
            <Button type="link" danger size="small" style={{ marginTop: 4, padding: 0 }}>
              清空草稿箱
            </Button>
          </Popconfirm>
        )}
      </Modal>

      {/* 生成结果详情抽屉 */}
      <Drawer
        title={previewItem ? `#${previewItem.number} ${previewItem.title}` : '题目详情'}
        open={!!previewItem}
        onClose={() => {
          // 关闭详情后回到草稿箱
          setPreviewItem(null)
          setPreviewOpen(true)
        }}
        width={560}
        extra={
          <Space>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => previewItem && openEditGenerated(previewItem)}
            >
              编辑入库
            </Button>
            <Button onClick={handleDirectCreate} loading={submitting}>
              直接入库
            </Button>
          </Space>
        }
      >
        {previewItem && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Descriptions size="small" column={3}>
              <Descriptions.Item label="难度">
                <Tag color={DIFFICULTY_META[previewItem.difficulty]?.color || 'default'}>
                  {DIFFICULTY_META[previewItem.difficulty]?.label || previewItem.difficulty}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="函数名">{previewItem.funcName || '-'}</Descriptions.Item>
              <Descriptions.Item label="用例数">{(previewItem.testcases || []).length}</Descriptions.Item>
            </Descriptions>
            <Text strong>标签：</Text>
            <Space wrap>
              {(previewItem.tags || []).map((t) => (
                <Tag key={t}>{t}</Tag>
              ))}
            </Space>
            <Divider style={{ margin: '8px 0' }} />
            <Text strong>题目描述</Text>
            <pre style={{ whiteSpace: 'pre-wrap', margin: 0, fontSize: 13, lineHeight: 1.7 }}>{previewItem.description || '-'}</pre>
            {(previewItem.examples || []).length > 0 && (
              <>
                <Divider style={{ margin: '8px 0' }} />
                <Text strong>示例</Text>
                {previewItem.examples.map((ex, i) => (
                  <div key={i} style={{ background: '#fafafa', padding: 10, borderRadius: 6 }}>
                    <Text type="secondary">输入：</Text>
                    {(ex.input || []).map((line, j) => (
                      <div key={j} style={{ fontFamily: 'monospace' }}>{line}</div>
                    ))}
                    <Text type="secondary">输出：</Text>
                    {(ex.output || []).map((line, j) => (
                      <div key={j} style={{ fontFamily: 'monospace' }}>{line}</div>
                    ))}
                    {ex.explain && <Text type="secondary">解释：{ex.explain}</Text>}
                  </div>
                ))}
              </>
            )}
            {(previewItem.testcases || []).length > 0 && (
              <>
                <Divider style={{ margin: '8px 0' }} />
                <Text strong>测试用例（{(previewItem.testcases || []).length} 个）</Text>
                {previewItem.testcases.map((tc, i) => (
                  <div key={i} style={{ background: '#fafafa', padding: 10, borderRadius: 6 }}>
                    <Text type="secondary">#{i + 1} 输入：</Text>
                    <div style={{ fontFamily: 'monospace' }}>{tc.input}</div>
                    <Text type="secondary">期望：</Text>
                    <div style={{ fontFamily: 'monospace' }}>{tc.expected}</div>
                    {tc.mode && <Tag style={{ marginTop: 4 }}>{tc.mode}</Tag>}
                  </div>
                ))}
              </>
            )}
            {(previewItem.solution || []).length > 0 && (
              <>
                <Divider style={{ margin: '8px 0' }} />
                <Text strong>标准答案（{(previewItem.solution || []).length} 解）</Text>
                {previewItem.solution.map((s, i) => (
                  <div key={i} style={{ background: '#fafafa', padding: 10, borderRadius: 6 }}>
                    <Text strong>{s.name}</Text>
                    <Text type="secondary"> · {s.complexity}</Text>
                    <div style={{ fontSize: 13 }}>{s.idea}</div>
                    {s.code && (
                      <pre
                        style={{
                          whiteSpace: 'pre-wrap',
                          background: '#1e1e1e',
                          color: '#d4d4d4',
                          padding: 10,
                          borderRadius: 6,
                          fontFamily: 'Menlo, Consolas, monospace',
                          fontSize: 12,
                        }}
                      >
                        {s.code}
                      </pre>
                    )}
                  </div>
                ))}
              </>
            )}
          </Space>
        )}
      </Drawer>

      <Modal
        title={editing ? `编辑题目 #${editing.number}` : '新增题目'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          setEditingDraftId(null)
        }}
        onOk={handleSubmit}
        confirmLoading={submitting}
        width={760}
        destroyOnClose
        maskClosable={false}
      >
        <Form form={form} layout="vertical" style={{ maxHeight: '65vh', overflowY: 'auto', paddingRight: 8 }}>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item
              name="number"
              label="题号"
              rules={[{ required: true, message: '请输入题号' }]}
              style={{ width: 140 }}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="title"
              label="标题"
              rules={[{ required: true, message: '请输入标题' }]}
              style={{ flex: 1, minWidth: 320 }}
            >
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item
              name="difficulty"
              label="难度"
              rules={[{ required: true, message: '请选择难度' }]}
              style={{ width: 130 }}
            >
              <Select
                options={[
                  { value: 'easy', label: '简单' },
                  { value: 'medium', label: '中等' },
                  { value: 'hard', label: '困难' },
                ]}
              />
            </Form.Item>
          </Space>

          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="passRate" label="通过率(%)" style={{ width: 140 }}>
              <InputNumber min={0} max={100} step={0.1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="submissions" label="提交数(展示)" style={{ width: 160 }}>
              <Input placeholder="如 12.1M" maxLength={32} />
            </Form.Item>
            <Form.Item name="tags" label="标签" style={{ flex: 1, minWidth: 280 }}>
              <Select mode="tags" placeholder="输入后回车添加" />
            </Form.Item>
            <Form.Item name="enabled" label="上架" valuePropName="checked" style={{ width: 90 }}>
              <Switch />
            </Form.Item>
          </Space>

          <Form.Item name="description" label="题目描述">
            <TextArea rows={4} placeholder="支持换行分段" />
          </Form.Item>

          <Form.Item name="defaultCode" label="默认模板代码">
            <TextArea rows={5} style={{ fontFamily: 'Menlo, Consolas, monospace' }} placeholder="def solve():\n    pass" />
          </Form.Item>

          <Space style={{ display: 'flex' }} align="start">
            <Form.Item
              name="funcName"
              label="判题入口函数名"
              tooltip="留空表示该题暂不支持在线判题（如链表 / 类设计题）"
              style={{ width: 240 }}
            >
              <Input placeholder="如 twoSum" maxLength={64} />
            </Form.Item>
            <Form.Item name="similarText" label="相似题目ID" tooltip="多个 ID 用英文逗号分隔" style={{ flex: 1 }}>
              <Input placeholder="如 15,3,53" />
            </Form.Item>
          </Space>

          <Divider orientation="left" plain style={{ margin: '8px 0' }}>
            示例 Examples
          </Divider>
          <Form.List name="examples">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name }) => (
                  <Card
                    key={key}
                    size="small"
                    style={{ marginBottom: 12, background: '#fafafa' }}
                    title={`示例 ${name + 1}`}
                    extra={
                      <Button type="text" danger size="small" icon={<DeleteOutlined />} onClick={() => remove(name)}>
                        删除
                      </Button>
                    }
                  >
                    <Space style={{ display: 'flex' }} align="start">
                      <Form.Item
                        name={[name, 'input']}
                        label="输入（每行一条）"
                        style={{ flex: 1 }}
                        getValueProps={(v) => ({ value: Array.isArray(v) ? v.join('\n') : (v || '') })}
                      >
                        <TextArea rows={2} placeholder={'nums = [2,7,11,15], target = 9'} />
                      </Form.Item>
                      <Form.Item
                        name={[name, 'output']}
                        label="输出（每行一条）"
                        style={{ flex: 1 }}
                        getValueProps={(v) => ({ value: Array.isArray(v) ? v.join('\n') : (v || '') })}
                      >
                        <TextArea rows={2} placeholder={'[0,1]'} />
                      </Form.Item>
                    </Space>
                    <Form.Item name={[name, 'explain']} label="解释" style={{ marginBottom: 0 }}>
                      <Input placeholder="样例解释（可留空）" />
                    </Form.Item>
                  </Card>
                ))}
                <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ input: [''], output: [''], explain: '' })}>
                  添加示例
                </Button>
              </>
            )}
          </Form.List>

          <Divider orientation="left" plain style={{ margin: '16px 0 8px' }}>
            测试用例 Testcases
          </Divider>
          <Form.List name="testcases">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name }) => (
                  <Card
                    key={key}
                    size="small"
                    style={{ marginBottom: 12, background: '#fafafa' }}
                    title={`用例 ${name + 1}`}
                    extra={
                      <Button type="text" danger size="small" icon={<DeleteOutlined />} onClick={() => remove(name)}>
                        删除
                      </Button>
                    }
                  >
                    <Form.Item name={[name, 'input']} label="输入" style={{ marginBottom: 8 }}>
                      <TextArea rows={2} placeholder={'nums = [2,7,11,15], target = 9'} />
                    </Form.Item>
                    <Form.Item name={[name, 'expected']} label="期望输出（判题 JSON 表示）" style={{ marginBottom: 8 }}>
                      <TextArea rows={2} placeholder={'[0, 1]'} />
                    </Form.Item>
                    <Space style={{ display: 'flex' }} align="start">
                      <Form.Item name={[name, 'mode']} label="匹配模式" style={{ width: 180, marginBottom: 0 }}>
                        <Select allowClear options={MODE_OPTIONS} placeholder="精确匹配" />
                      </Form.Item>
                      <Form.Item
                        name={[name, 'accepts']}
                        label="可接受答案（逗号分隔）"
                        tooltip="多解题目可填写多个可接受输出"
                        style={{ flex: 1, marginBottom: 0 }}
                        getValueProps={(v) => ({ value: Array.isArray(v) ? v.join(', ') : (v || '') })}
                      >
                        <Input placeholder={'如 [0,1], [1,0]'} />
                      </Form.Item>
                    </Space>
                  </Card>
                ))}
                <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ input: '', expected: '' })}>
                  添加测试用例
                </Button>
              </>
            )}
          </Form.List>

          <Divider orientation="left" plain style={{ margin: '16px 0 8px' }}>
            标准答案 Solution（可多解，供 AI 辅导参照）
          </Divider>
          <Form.List name="solution">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name }) => (
                  <Card
                    key={key}
                    size="small"
                    style={{ marginBottom: 12, background: '#fafafa' }}
                    title={`解法 ${name + 1}`}
                    extra={
                      <Button type="text" danger size="small" icon={<DeleteOutlined />} onClick={() => remove(name)}>
                        删除
                      </Button>
                    }
                  >
                    <Space style={{ display: 'flex' }} align="start">
                      <Form.Item name={[name, 'name']} label="解法名" style={{ width: 200, marginBottom: 8 }}>
                        <Input placeholder="如 哈希表" />
                      </Form.Item>
                      <Form.Item name={[name, 'complexity']} label="复杂度" style={{ flex: 1, marginBottom: 8 }}>
                        <Input placeholder="如 时间 O(n)，空间 O(n)" />
                      </Form.Item>
                    </Space>
                    <Form.Item name={[name, 'idea']} label="思路" style={{ marginBottom: 8 }}>
                      <TextArea rows={2} placeholder="解法思路说明" />
                    </Form.Item>
                    <Form.Item name={[name, 'code']} label="参考代码" style={{ marginBottom: 0 }}>
                      <TextArea rows={4} style={{ fontFamily: 'Menlo, Consolas, monospace' }} placeholder="完整可运行的参考实现" />
                    </Form.Item>
                  </Card>
                ))}
                <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ name: '', idea: '', code: '', complexity: '' })}>
                  添加解法
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
    </div>
  )
}

export default PythonProblemManage
