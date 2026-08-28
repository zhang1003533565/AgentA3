import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Card, Col, Collapse, Empty, Form, Image, Input, Row, Select, Space, Table, Tabs, Tag, Typography, Upload, message } from 'antd'
import { DatabaseOutlined, PlayCircleOutlined, PlusOutlined, ReloadOutlined, SaveOutlined } from '@ant-design/icons'
import { importExamQuestions } from '../../../api/examQuestion'
import {
  getRagAgents,
  executeTextToSql,
  getRagFramework,
  getTextToSqlSchema,
  runRagQuery,
  saveRagAgentExampleInput,
} from '../../../api/rag'
import { getSystemConfigList } from '../../../api/systemConfig'
import {
  buildAgentModelBindings,
  buildLlmModelOptions,
  getAgentModelRequirementText,
  getAgentRequiredModelModalities,
  isAgentEnabled,
} from '../agentConfig'
import './RagManage.css'

const { TextArea } = Input
const { Text, Title } = Typography
const MAX_AGENT_TEST_IMAGES = 8
const MAX_AGENT_TEST_IMAGE_BYTES = 10 * 1024 * 1024
const MAX_AGENT_TEST_IMAGE_EDGE = 1800

const readFileAsDataUrl = (file) => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => resolve(String(reader.result || ''))
  reader.onerror = () => reject(new Error('图片读取失败'))
  reader.readAsDataURL(file)
})

const loadImageElement = (src) => new Promise((resolve, reject) => {
  const image = new window.Image()
  image.onload = () => resolve(image)
  image.onerror = () => reject(new Error('图片解析失败'))
  image.src = src
})

const canvasToDataUrl = (canvas, type, quality) => canvas.toDataURL(type, quality)

const normalizeAgentTestImage = async (file) => {
  const source = await readFileAsDataUrl(file)
  const image = await loadImageElement(source)
  const ratio = Math.min(1, MAX_AGENT_TEST_IMAGE_EDGE / Math.max(image.width, image.height))
  if (ratio === 1 && file.size <= 2 * 1024 * 1024) return source

  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(image.width * ratio))
  canvas.height = Math.max(1, Math.round(image.height * ratio))
  const context = canvas.getContext('2d')
  if (!context) throw new Error('当前浏览器不支持图片压缩')
  context.drawImage(image, 0, 0, canvas.width, canvas.height)
  const outputType = file.type === 'image/png' ? 'image/png' : 'image/jpeg'
  return canvasToDataUrl(canvas, outputType, outputType === 'image/png' ? undefined : 0.82)
}

const QUESTION_AGENT_TYPES = {
  textbook_question_single_choice_agent: 'single_choice',
  textbook_question_multiple_choice_agent: 'multiple_choice',
  textbook_question_true_false_agent: 'true_false',
  textbook_question_fill_blank_agent: 'fill_blank',
  textbook_question_short_answer_agent: 'short_answer',
  textbook_question_calculation_agent: 'calculation',
  textbook_question_programming_agent: 'programming',
}

const evidenceColumns = [
  { title: '来源', dataIndex: 'source', ellipsis: true },
  { title: '分数', dataIndex: 'score', width: 120, render: (value) => (value === null || value === undefined ? '-' : Number(value).toFixed(4)) },
  { title: '内容', dataIndex: 'content', ellipsis: true },
]

const buildAgentColumns = () => [
  {
    title: '状态',
    dataIndex: 'enabled',
    width: 90,
    render: (value, record) => (
      <Tag color={record.name === 'leader_agent' || value !== false ? 'green' : 'default'}>
        {record.name === 'leader_agent' || value !== false ? '启用' : '关闭'}
      </Tag>
    ),
  },
  {
    title: '智能体',
    dataIndex: 'name',
    width: 280,
    render: (value, record) => (
      <Space direction="vertical" size={4}>
        <Tag color="geekblue">{value}</Tag>
        <Text type="secondary">{record.role}</Text>
      </Space>
    ),
  },
  {
    title: '执行方式',
    dataIndex: 'executionModeLabel',
    width: 150,
    render: (value) => <Tag color="gold">{value || '直接处理'}</Tag>,
  },
  {
    title: '职责',
    dataIndex: 'purpose',
    ellipsis: true,
  },
]

const coverageColumns = [
  { title: '功能', dataIndex: 'name', render: (value) => <Tag color="blue">{value}</Tag> },
  { title: '分类', dataIndex: 'category' },
  { title: '用途', dataIndex: 'purpose' },
  { title: '状态', dataIndex: 'status', render: (value) => <Tag color="green">{value}</Tag> },
]

const toolColumns = [
  { title: '工具', dataIndex: 'name', width: 220, render: (value) => <Tag color="geekblue">{value}</Tag> },
  { title: '分类', dataIndex: 'category', width: 150 },
  { title: '触发场景', dataIndex: 'trigger', ellipsis: true },
  { title: '用途', dataIndex: 'purpose', ellipsis: true },
  {
    title: '产物',
    dataIndex: 'outputs',
    width: 180,
    render: (value = []) => (
      <Space size={[4, 4]} wrap>
        {value.map((item) => <Tag key={item}>{String(item).toUpperCase()}</Tag>)}
      </Space>
    ),
  },
  { title: '状态', dataIndex: 'status', width: 110, render: (value) => <Tag color="green">{value}</Tag> },
]

const providerColumns = [
  { title: '名称', dataIndex: 'name', width: 150, render: (value) => <Tag>{value}</Tag> },
  { title: '状态', dataIndex: 'status', width: 150, render: (value) => <Tag color={value === 'implemented' ? 'green' : 'orange'}>{value}</Tag> },
  {
    title: '依赖环境变量',
    dataIndex: 'requiredEnv',
    render: (value = []) => value.length ? (
      <Space size={[4, 4]} wrap>
        {value.map((item) => <Text code key={item}>{item}</Text>)}
      </Space>
    ) : '-',
  },
]

const envColumns = [
  { title: '环境变量', dataIndex: 'name' },
  { title: '默认值', dataIndex: 'default' },
  { title: '已配置', dataIndex: 'configured', render: (value) => <Tag color={value ? 'green' : 'default'}>{String(value)}</Tag> },
]

const safeJsonParse = (value, fallback) => {
  if (!String(value || '').trim()) return fallback
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

const parseMediaAnswer = (answer) => {
  const value = typeof answer === 'string' ? safeJsonParse(answer, null) : answer
  if (!value || typeof value !== 'object') return null
  const images = Array.isArray(value.images) ? value.images : []
  const videos = Array.isArray(value.videos) ? value.videos : []
  if (!images.length && !videos.length) return null
  return { ...value, images, videos }
}

const executionModeLabels = {
  leader_direct_answer: 'Leader 直接回答',
  leader_call_tool: 'Leader 调用接口',
  leader_routed_direct_agent: 'Leader 调用非检索智能体',
  direct_agent: '直接处理',
  leader_skipped_disabled_agent: 'Leader 跳过已关闭智能体',
  direct_disabled_agent: '已关闭智能体未执行',
}

const updateAgentInList = (agents, updatedAgent) => (
  updatedAgent?.name
    ? agents.map((item) => (item.name === updatedAgent.name ? { ...item, ...updatedAgent } : item))
    : agents
)

const getExecutionLabel = (metadata = {}) => (
  metadata.executionModeLabel ||
  executionModeLabels[metadata.executionMode] ||
  metadata.executionMode ||
  '未知执行方式'
)

const extractMermaidCodeBlock = (text) => {
  const match = String(text || '').match(/```mermaid\s*([\s\S]*?)```/i)
  return match ? String(match[1] || '').trim() : ''
}

const normalizeMindMapLabel = (line) => {
  let text = String(line || '').trim().replace(/^[-*]\s+/, '')
  const rootMatch = text.match(/^root\s*\(\((.*)\)\)$/i)
  if (rootMatch) return rootMatch[1].trim()
  text = text.replace(/^\(\((.*)\)\)$/, '$1')
    .replace(/^\((.*)\)$/, '$1')
    .replace(/^\[(.*)\]$/, '$1')
    .replace(/^\{\{(.*)\}\}$/, '$1')
  return text.trim()
}

const parseMermaidMindMap = (source) => {
  const lines = String(source || '')
    .split('\n')
    .filter((line) => line.trim() && !/^\s*mindmap\s*$/i.test(line))
  const root = { label: '思维导图', children: [] }
  const stack = [{ indent: -1, node: root }]

  lines.forEach((line) => {
    const indent = line.match(/^\s*/)?.[0].length || 0
    const label = normalizeMindMapLabel(line)
    if (!label) return
    while (stack.length > 1 && indent <= stack[stack.length - 1].indent) {
      stack.pop()
    }
    const node = { label, children: [] }
    stack[stack.length - 1].node.children.push(node)
    stack.push({ indent, node })
  })

  return root.children[0] || root
}

const renderMindMapNode = (node, path = '0') => (
  <div className="rag-mindmap-node" key={path}>
    <div className="rag-mindmap-label">{node.label}</div>
    {node.children?.length ? (
      <div className="rag-mindmap-children">
        {node.children.map((child, index) => renderMindMapNode(child, `${path}-${index}`))}
      </div>
    ) : null}
  </div>
)

const agentExampleInputs = {
  leader_agent: '数据结构：栈与队列\n\n栈遵循后进先出原则，只能在栈顶进行插入和删除。队列遵循先进先出原则，只能在队尾插入、队头删除。循环队列通过取模运算复用数组空间。',
  diagram_mind_map_agent: '操作系统：进程调度\n\n进程调度是操作系统按照一定策略从就绪队列中选择进程分配 CPU 的过程。常见算法包括先来先服务、短作业优先、优先级调度、时间片轮转和多级反馈队列。',
  diagram_flowchart_agent: '括号匹配算法流程\n\n从左到右扫描字符串。遇到左括号时入栈；遇到右括号时，如果栈为空则匹配失败，否则弹出栈顶左括号并判断类型是否对应。扫描结束后，如果栈为空则括号匹配成功，否则匹配失败。',
  diagram_architecture_agent: '智慧校园 AI 智能体架构材料\n\n前端 AppWeb 负责展示智能体列表、输入材料、模型选择和执行结果。Java 后端 AppBackend 负责鉴权、读取模型配置、接入第三方知识库并代理调用 Python AI 服务。Python ai-servers 负责智能体路由、工具编排和专业智能体执行。',
  mind_map_agent: '操作系统：进程调度\n\n进程调度是操作系统按照一定策略从就绪队列中选择进程分配 CPU 的过程。常见算法包括先来先服务、短作业优先、优先级调度、时间片轮转和多级反馈队列。',
  textbook_knowledge_agent: '数据结构：栈与队列\n\n栈是后进先出的受限线性表。队列是先进先出的受限线性表。循环队列用于解决顺序队列假溢出问题。',
  textbook_question_single_choice_agent: '数据结构：栈与队列\n\n栈只允许在栈顶进行插入和删除。队列只允许在队尾插入、队头删除。栈常用于括号匹配，队列常用于任务排队。',
  textbook_question_fill_blank_agent: '数据结构：栈与队列\n\n栈顶、栈底、入栈、出栈、队头、队尾、入队、出队、LIFO、FIFO、循环队列、front、rear。',
  textbook_question_true_false_agent: '数据结构：栈与队列\n\n栈是后进先出，队列是先进先出。读取栈顶或队头元素不会删除元素。循环队列可复用数组空间。',
  textbook_question_multiple_choice_agent: '数据结构：栈与队列\n\n栈和队列都是操作受限的线性表。顺序栈需要关注容量限制，循环队列需要关注队空和队满条件。',
  textbook_question_short_answer_agent: '数据结构：栈与队列\n\n栈的基本操作包括入栈、出栈、取栈顶元素。队列的基本操作包括入队、出队、取队头元素。两者应用场景不同。',
  textbook_question_calculation_agent: '数据结构：栈与队列计算材料\n\n顺序栈空栈 top = -1，入栈 top 加 1，出栈 top 减 1。循环队列容量为 6 时，队满条件可为 (rear + 1) % 6 == front。',
  textbook_question_programming_agent: '数据结构：栈\n\n栈遵循后进先出。括号匹配算法从左到右扫描字符串，左括号入栈，右括号与栈顶左括号匹配，最终栈空表示匹配成功。',
  meeting_controller_agent: '张老师：本周要完成数据结构复习资料初稿。\n\n李明：我负责整理栈与队列知识点，周三前提交。\n\n王芳：我负责生成练习题，周四前提交。',
  meeting_transcription_agent: '张老师：我们今天主要讨论数据结构复习资料。\n\n李明：我负责栈的定义和操作。\n\n王芳：我负责练习题，括号匹配还需要再确认。',
  meeting_summary_agent: '张老师：本周要完成数据结构复习资料初稿。\n\n李明：我周三前提交知识点。\n\n王芳：我周四前提交题目。\n\n陈强：我周五前提交代码案例。',
  meeting_member_analysis_agent: '张老师：循环队列的队满条件是什么？\n\n李明：应该是 rear 追上 front，但具体公式我有点不确定。\n\n王芳：括号匹配步骤我还需要再复习。',
  meeting_resource_recommendation_agent: '李明：循环队列的 front、rear 变化和判满条件还不够熟。\n\n王芳：括号匹配算法步骤我需要再复习。\n\n陈强：我想找一个循环队列图文示例。',
  meeting_voice_broadcast_agent: '张老师：本次会议确定，本周完成数据结构复习资料初稿。\n\n李明：我负责栈与队列知识点。\n\n王芳：我负责练习题。\n\n陈强：我负责代码案例。',
  ppt_outline_agent: '数据结构：栈与队列\n\n课程重点包括栈的后进先出、队列的先进先出、循环队列的队空队满条件，以及括号匹配和任务调度等应用。',
  ppt_structure_agent: '数据结构栈与队列 PPT 结构选择\n\n根据 Presenton 模板组件 Schema 为每页选择合适的 layoutId。',
  ppt_review_agent: 'PPT 大纲：数据结构栈与队列\n\n第 1 页课程导入；第 2 页栈；第 3 页队列；第 4 页循环队列；第 5 页应用案例；第 6 页课堂练习。',
  ppt_image_agent: 'PPT 插图素材：数据结构栈与队列\n\n封面包含栈容器和队列队伍。栈图体现入栈出栈，队列图体现入队出队，循环队列图体现 front 和 rear。',
  ppt_to_docx_agent: 'PPTX 转 DOCX 转换需求\n\n请上传一个 .pptx 文件，将每页幻灯片按顺序整理成 Word 文档。要求保留可提取的文字、表格和图片；内容可以根据 Word 文档重新排版。',
  image_agent: '操作系统：进程调度配图素材\n\n画面元素包括就绪队列、CPU、调度器、进程卡片、时间片和优先级标记。',
}

const legacyQuestionExampleInputs = new Set([
  '请自动判断：帮我把数据结构的栈与队列整理成复习资料',
  '把操作系统进程调度整理成思维导图',
  '查询并整理数据结构中栈与队列的教材知识点，输出 Markdown',
  '根据数据结构中栈与队列的知识点生成 5 道选择题',
  '根据数据结构中栈与队列的知识点生成 5 道填空题',
  '根据数据结构中栈与队列的知识点生成 5 道判断题',
  '根据数据结构中栈与队列的知识点生成 5 道多选题',
  '根据数据结构中栈与队列的知识点生成 5 道简答题',
  '根据数据结构中栈与队列的知识点生成 5 道计算题',
  '根据数据结构中栈与队列的知识点生成 3 道编程题',
  '根据会议记录梳理会议状态、任务分发和下一步流程',
  '整理这段会议转写文本，区分说话人并修正发言格式',
  '总结这段会议的核心观点、结论、任务分工和后续计划',
  '分析会议中各成员的理解偏差、薄弱点和参与特征',
  '根据会议内容为每位成员推荐学习资源和推送策略',
  '把这段会议总结改写成适合语音播报的脚本',
  '根据数据结构中栈与队列的知识点生成 6 页课件大纲',
  '根据这份 6 页课件大纲生成逐页布局、版式和视觉层级',
  '审查这份 PPT 大纲和布局，输出问题清单、修改建议和置信度评分',
  '根据这份 PPT 大纲生成封面图和关键页面插图提示词',
  '为操作系统进程调度知识点生成一张课堂教学配图提示词',
])

const ragPageConfig = {
  playground: {
    sectionKey: 'playground',
    kicker: 'AI Agent Console',
    title: 'AI 智能体',
    description: '测试 Leader 自动路由、专业智能体和工具执行结果。',
  },
  agents: {
    sectionKey: 'agents',
    kicker: 'Agent Console',
    title: '智能体测试',
    description: '测试专业智能体调用、导入题库、维护示例输入和查看技能文件；开关和默认模型请到智能体设置页维护。',
  },
  framework: {
    sectionKey: 'framework',
    kicker: 'AI Framework',
    title: '框架配置',
    description: '查看智能体、模型服务、运行目录和 API 配置。',
  },
  sql: {
    sectionKey: 'sql',
    kicker: 'Text-to-SQL',
    title: 'Text-to-SQL',
    description: '把自然语言查询转换为结构化数据查询，并查看 Schema 与执行结果。',
  },
}

function RagManage({ page = 'playground' }) {
  const [bootLoading, setBootLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [framework, setFramework] = useState(null)
  const [agents, setAgents] = useState([])
  const [agentWorkflow, setAgentWorkflow] = useState({})
  const [queryResult, setQueryResult] = useState(null)
  const [queryError, setQueryError] = useState('')
  const [sqlSchema, setSqlSchema] = useState(null)
  const [sqlResult, setSqlResult] = useState(null)
  const [agentTestResult, setAgentTestResult] = useState(null)
  const [agentTestLoading, setAgentTestLoading] = useState(false)
  const [questionImportLoading, setQuestionImportLoading] = useState(false)
  const [llmModelOptions, setLlmModelOptions] = useState([])
  const [agentModelBindings, setAgentModelBindings] = useState({})
  const [agentTestImages, setAgentTestImages] = useState([])
  const [queryForm] = Form.useForm()
  const [sqlForm] = Form.useForm()
  const [agentTestForm] = Form.useForm()
  const refreshPromiseRef = useRef(null)
  const autoFilledQueryInputRef = useRef('')

  const getModelOptionsForAgent = useCallback((agent) => {
    const required = getAgentRequiredModelModalities(agent)
    return llmModelOptions.filter((option) => required.includes(option.modality))
  }, [llmModelOptions])

  const getAgentBoundModel = useCallback((agentName) => {
    const name = agentName || 'leader_agent'
    return agentModelBindings[name] || ''
  }, [agentModelBindings])

  const getDefaultModelForAgent = useCallback((agent) => {
    const modelOptions = getModelOptionsForAgent(agent)
    const boundModel = getAgentBoundModel(agent?.name)
    if (boundModel && modelOptions.some((option) => option.value === boundModel)) {
      return boundModel
    }
    return ''
  }, [getAgentBoundModel, getModelOptionsForAgent])

  const getAgentExampleInput = useCallback((agent) => (
    agent?.invokeExample?.input ||
    agentExampleInputs[agent?.name] ||
    `请使用${agent?.role || '智能体'}处理这段课程内容`
  ), [])

  const applyAgentDefaultModel = useCallback((form, agentName) => {
    const selectedAgent = agents.find((item) => item.name === agentName) || (agentName === 'leader_agent' ? { name: 'leader_agent' } : null)
    const exampleInput = selectedAgent ? getAgentExampleInput(selectedAgent) : undefined
    form.setFieldsValue({
      llmModel: getDefaultModelForAgent(selectedAgent) || undefined,
      input: exampleInput,
    })
    if (form === queryForm && exampleInput) {
      autoFilledQueryInputRef.current = exampleInput
    }
  }, [agents, getAgentExampleInput, getDefaultModelForAgent, queryForm])

  const tableAgentColumns = useMemo(
    () => buildAgentColumns(),
    []
  )

  const agentOptions = useMemo(
    () => [
      { value: 'leader_agent', label: 'Leader 自动路由 · 意图识别/分发' },
      ...agents.filter((item) => item.name !== 'leader_agent').map((item) => ({
        value: item.name,
        label: `${item.role} · ${item.name}${item.internalOnly ? ' · 系统内部必用' : (isAgentEnabled(item) ? '' : ' · 已关闭')}`,
        disabled: !isAgentEnabled(item),
      })),
    ],
    [agents]
  )

  const agentTestOptions = useMemo(
    () => agents.map((item) => ({
      value: item.name,
      label: `${item.role} · ${item.name}${item.internalOnly ? ' · 系统内部必用' : (isAgentEnabled(item) ? '' : ' · 已关闭')}`,
      disabled: !isAgentEnabled(item),
    })),
    [agents]
  )

  const fillAgentTestForm = useCallback((agent) => {
    if (!agent) return
    setAgentTestImages([])
    agentTestForm.setFieldsValue({
      agentName: agent.name,
      llmModel: getDefaultModelForAgent(agent) || undefined,
      input: getAgentExampleInput(agent),
    })
  }, [agentTestForm, getAgentExampleInput, getDefaultModelForAgent])

  const beforeAgentTestImageUpload = async (file) => {
    if (!file.type?.startsWith('image/')) {
      message.error('只能上传图片文件')
      return Upload.LIST_IGNORE
    }
    if (file.size > MAX_AGENT_TEST_IMAGE_BYTES) {
      message.error('单张图片不能超过 10MB')
      return Upload.LIST_IGNORE
    }
    try {
      const dataUrl = await normalizeAgentTestImage(file)
      setAgentTestImages((current) => {
        if (current.length >= MAX_AGENT_TEST_IMAGES) return current
        return [...current, {
          uid: file.uid,
          name: file.name,
          status: 'done',
          type: file.type,
          url: dataUrl,
          thumbUrl: dataUrl,
        }]
      })
    } catch (error) {
      message.error(error.message || '图片处理失败')
    }
    return Upload.LIST_IGNORE
  }

  const saveAgentExampleInput = async (agentName, inputValue, options = {}) => {
    const selectedAgentName = agentName || 'leader_agent'
    const selectedInput = String(inputValue || '').trim()
    if (!selectedInput) {
      message.warning('示例输入不能为空')
      return
    }
    try {
      const res = await saveRagAgentExampleInput(selectedAgentName, selectedInput)
      const updatedAgent = res.data
      setAgents((prev) => updateAgentInList(prev, updatedAgent))
      if (options.updateTestForm !== false) {
        agentTestForm.setFieldsValue({ agentName: selectedAgentName, input: selectedInput })
      }
      message.success('示例输入已保存')
    } catch (error) {
      message.error(error.message || '示例输入保存失败')
    }
  }

  const refresh = async () => {
    if (refreshPromiseRef.current) {
      return refreshPromiseRef.current
    }
    const task = (async () => {
      setBootLoading(true)
      try {
        const [
          frameworkRes,
          agentRes,
          schemaRes,
          aiConfigRes,
        ] = await Promise.all([
          getRagFramework(),
          getRagAgents(),
          getTextToSqlSchema(),
          getSystemConfigList({ current: 1, size: 500, prefixes: 'ai.service.,ai.agent-bindings.' }),
        ])
        setFramework(frameworkRes.data || null)
        setAgents(agentRes.data?.agents || [])
        setAgentWorkflow(agentRes.data?.workflow || {})
        setSqlSchema(schemaRes.data?.schema || null)
        const configRows = aiConfigRes.data?.records || []
        setLlmModelOptions(buildLlmModelOptions(configRows))
        setAgentModelBindings(buildAgentModelBindings(configRows))
      } catch (error) {
        message.error(error.message || '加载 AI 智能体数据失败')
      } finally {
        setBootLoading(false)
        refreshPromiseRef.current = null
      }
    })()
    refreshPromiseRef.current = task
    return task
  }

  useEffect(() => {
    refresh()
  }, [])

  useEffect(() => {
    if (!agents.length || agentTestForm.getFieldValue('agentName')) return
    const leader = agents.find((item) => item.name === 'leader_agent') || agents[0]
    fillAgentTestForm(leader)
  }, [agents, agentTestForm, fillAgentTestForm])

  useEffect(() => {
    const agentName = queryForm.getFieldValue('agentName') || 'leader_agent'
    const selectedAgent = agents.find((item) => item.name === agentName) || (agentName === 'leader_agent' ? { name: 'leader_agent' } : null)
    const nextValues = {}
    if (!queryForm.getFieldValue('llmModel')) {
      const defaultModel = getDefaultModelForAgent(selectedAgent)
      if (defaultModel) {
        nextValues.llmModel = defaultModel
      }
    }
    const currentInput = queryForm.getFieldValue('input')
    const shouldRefreshInput = (
      !currentInput ||
      currentInput === autoFilledQueryInputRef.current ||
      legacyQuestionExampleInputs.has(currentInput)
    )
    if (shouldRefreshInput && selectedAgent) {
      const exampleInput = getAgentExampleInput(selectedAgent)
      nextValues.input = exampleInput
      autoFilledQueryInputRef.current = exampleInput
    }
    if (Object.keys(nextValues).length) {
      queryForm.setFieldsValue(nextValues)
    }
  }, [agents, agentModelBindings, llmModelOptions, getAgentExampleInput, getDefaultModelForAgent, queryForm])

  const handleQuery = async (values) => {
    setActionLoading(true)
    setQueryError('')
    try {
      const selectedAgentName = values.agentName || 'leader_agent'
      const selectedAgent = agents.find((item) => item.name === selectedAgentName)
      if (selectedAgent && !isAgentEnabled(selectedAgent)) {
        message.warning('该智能体当前未开启，请先打开开关')
        return
      }
      const res = await runRagQuery({
        input: values.input,
        keyword: values.keyword || undefined,
        intent: values.intent || 'campus_search',
        agentName: selectedAgentName,
        llmModel: values.llmModel || getAgentBoundModel(selectedAgentName) || undefined,
        metadata: {},
      })
      setQueryResult(res.data)
      message.success('智能体执行完成')
    } catch (error) {
      const errorMessage = error.message || '智能体执行失败'
      setQueryResult(null)
      setQueryError(errorMessage)
      message.error(errorMessage)
    } finally {
      setActionLoading(false)
    }
  }

  const handleTextToSql = async (values) => {
    setActionLoading(true)
    try {
      const res = await executeTextToSql({
        input: values.input,
      })
      setSqlResult(res.data)
      message.success('Text-to-SQL 执行完成')
    } catch (error) {
      message.error(error.message || 'Text-to-SQL 执行失败')
    } finally {
      setActionLoading(false)
    }
  }

  const handleAgentTest = async (values) => {
    const agent = agents.find((item) => item.name === values.agentName)
    if (!agent) {
      message.warning('请先选择一个智能体')
      return
    }
    if (!isAgentEnabled(agent)) {
      message.warning('该智能体当前未开启，请先打开开关')
      return
    }
    const payload = {
      input: values.input,
      intent: agent.intent === 'auto' ? 'campus_search' : agent.intent,
      agentName: agent.name,
      llmModel: values.llmModel || getAgentBoundModel(agent.name) || undefined,
      metadata: {
        testFrom: 'admin_agent_console',
        agentRole: agent.role,
        needRetrieval: agent.needRetrieval,
      },
    }
    if (agentTestImages.length) {
      payload.imageDataUrls = agentTestImages.map((item) => item.url)
    }

    setAgentTestLoading(true)
    try {
      const res = await runRagQuery(payload)
      setAgentTestResult({
        agent,
        request: payload,
        response: res.data,
      })
      message.success(`${agent.role} 调用成功`)
    } catch (error) {
      setAgentTestResult({
        agent,
        request: payload,
        error: error.message || '智能体调用失败',
      })
      message.error(error.message || '智能体调用失败')
    } finally {
      setAgentTestLoading(false)
    }
  }

  const getQuestionBankPayloadFromResult = (result) => {
    const answerType = result?.response?.answerType || result?.response?.metadata?.answerType
    const agentName = result?.agent?.name || result?.request?.agentName
    if (answerType !== 'question_bank' && !QUESTION_AGENT_TYPES[agentName]) {
      return null
    }
    const payload = safeJsonParse(result?.response?.answer, null)
    if (!payload || !Array.isArray(payload.questions)) {
      return null
    }
    return payload
  }

  const handleImportQuestionBank = async () => {
    const payload = getQuestionBankPayloadFromResult(agentTestResult)
    if (!payload) {
      message.warning('当前结果不是可导入的题库 JSON')
      return
    }
    if (!payload.questions.length) {
      message.warning('当前题库 JSON 没有可导入的题目')
      return
    }
    const agentName = agentTestResult?.agent?.name || agentTestResult?.request?.agentName
    const expectedType = QUESTION_AGENT_TYPES[agentName] || agentTestResult?.agent?.intent
    setQuestionImportLoading(true)
    try {
      const res = await importExamQuestions({
        questions: payload.questions,
        missingInfo: payload.missingInfo || [],
        sourceAgent: agentName,
        sourceTitle: `智能体测试：${agentTestResult?.agent?.role || agentName}`,
        sourceScene: 'test',
      }, expectedType)
      const importedCount = res.data?.importedCount || 0
      message.success(`已导入题库 ${importedCount} 道题`)
    } catch (error) {
      message.error(error.message || '题库导入失败')
    } finally {
      setQuestionImportLoading(false)
    }
  }

  const renderProviderCard = (title, dataSource) => (
    <Card title={title} className="rag-panel-card rag-provider-card">
      <Table
        rowKey="name"
        columns={providerColumns}
        dataSource={dataSource || []}
        pagination={false}
        size="small"
        scroll={{ x: 560 }}
      />
    </Card>
  )

  const renderMediaAnswer = (media, rawText) => {
    const imageItems = media.images || []
    const videoItems = media.videos || []
    const items = imageItems.length
      ? imageItems.map((item) => ({ ...item, mediaType: 'image' }))
      : videoItems.map((item) => ({ ...item, mediaType: 'video' }))
    const successItems = items.filter((item) => item.url || item.base64)
    const failedItems = items.filter((item) => !item.url && !item.base64)
    return (
      <div className="rag-answer-box rag-answer-box--media">
        <div className="rag-media-summary">
          <Space size={[8, 8]} wrap>
            <Tag color={media.status === 'success' ? 'green' : media.status === 'running' ? 'blue' : 'orange'}>
              状态：{media.status || '-'}
            </Tag>
            {media.taskId && <Tag>任务：{media.taskId}</Tag>}
            {media.providerTaskId && <Tag>服务商任务：{media.providerTaskId}</Tag>}
          </Space>
          {media.message && <Text type="secondary">{media.message}</Text>}
        </div>
        {successItems.length ? (
          <div className="rag-media-grid">
            {successItems.map((item, index) => {
              const source = item.base64 ? `data:image/png;base64,${item.base64}` : item.url
              return (
                <div className="rag-media-card" key={`${item.mediaType}-${item.index ?? index}`}>
                  {item.mediaType === 'video' ? (
                    <video className="rag-media-video" src={source} controls playsInline />
                  ) : (
                    <Image className="rag-media-image" src={source} alt={`生成图片 ${index + 1}`} />
                  )}
                  <Space className="rag-media-actions" size={[8, 8]} wrap>
                    <Tag color="green">成功</Tag>
                    {item.seed !== null && item.seed !== undefined && <Tag>seed：{item.seed}</Tag>}
                    {item.url && <a href={item.url} target="_blank" rel="noreferrer">打开原始地址</a>}
                  </Space>
                </div>
              )
            })}
          </div>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有返回可展示的媒体地址" />
        )}
        {failedItems.length ? (
          <Alert
            type="warning"
            showIcon
            message="部分媒体生成失败"
            description={failedItems.map((item, index) => (
              <div key={index}>{item.errorMessage || media.message || '未返回媒体地址'}</div>
            ))}
          />
        ) : null}
        <Collapse
          size="small"
          ghost
          items={[
            {
              key: 'raw',
              label: '查看原始返回',
              children: <pre className="rag-media-raw">{rawText}</pre>,
            },
          ]}
        />
      </div>
    )
  }

  const renderAgentAnswer = (answer, response = {}) => {
    const text = String(answer || '').trim()
    const metadata = response?.metadata || response || {}
    const answerType = response?.answerType || metadata?.answerType || 'text'
    if (!text) {
      return <div className="rag-answer-box">暂无回答</div>
    }
    const media = parseMediaAnswer(text)
    if (media || ['image_generation', 'video_generation'].includes(answerType)) {
      return media
        ? renderMediaAnswer(media, text)
        : <div className={`rag-answer-box rag-answer-box--${answerType}`}>{text}</div>
    }
    if (answerType !== 'mermaid_mindmap') {
      return <div className={`rag-answer-box rag-answer-box--${answerType}`}>{text}</div>
    }
    const mermaidBody = extractMermaidCodeBlock(text) || text
    const markdownSource = text.startsWith('```mermaid') ? text : `\`\`\`mermaid\n${mermaidBody}\n\`\`\``
    const mindMapTree = parseMermaidMindMap(mermaidBody)
    return (
      <div className="rag-answer-box rag-answer-box--mindmap">
        <div className="rag-mindmap-canvas">
          {renderMindMapNode(mindMapTree)}
        </div>
        <Collapse
          className="rag-mindmap-source"
          size="small"
          ghost
          items={[
            {
              key: 'source',
              label: 'Markdown 源码',
              children: <pre className="rag-mermaid-code">{markdownSource}</pre>,
            },
          ]}
        />
      </div>
    )
  }

  const enabledAgentCount = agents.filter((item) => isAgentEnabled(item)).length
  const boundAgentCount = agents.filter((item) => agentModelBindings[item.name]).length
  const agentExampleCount = agents.filter((item) => item.documents?.exampleInput || item.invokeExample?.input).length

  const sections = [
    {
      key: 'playground',
      label: '智能体执行',
      children: (
        <Row gutter={[20, 20]}>
          <Col xs={24} lg={9}>
            <Card title="Leader / 智能体查询" className="rag-panel-card">
              <Form
                form={queryForm}
                layout="vertical"
                initialValues={{ intent: 'campus_search', agentName: 'leader_agent' }}
                onFinish={handleQuery}
              >
                <Form.Item name="agentName" label="执行智能体">
                  <Select
                    options={agentOptions}
                    placeholder="Leader 自动路由"
                    onChange={(value) => applyAgentDefaultModel(queryForm, value)}
                  />
                </Form.Item>
                <Form.Item
                  noStyle
                  shouldUpdate={(prev, next) => prev.agentName !== next.agentName}
                >
                  {({ getFieldValue }) => {
                    const selectedAgent = agents.find((item) => item.name === getFieldValue('agentName'))
                    const modelOptions = getModelOptionsForAgent(selectedAgent)
                    return (
                      <>
                        <Alert
                          className="rag-inline-alert"
                          type="info"
                          showIcon
                          message={`当前智能体需要模型：${getAgentModelRequirementText(selectedAgent)}`}
                        />
                        {!modelOptions.length ? (
                          <Alert
                            className="rag-inline-alert"
                            type="warning"
                            showIcon
                            message={`没有可选模型：请到模型配置页测试成功至少一个${getAgentModelRequirementText(selectedAgent)}模型。`}
                            action={<Button size="small" type="primary" onClick={() => { window.location.href = '/ai/model' }}>去配置模型</Button>}
                          />
                        ) : null}
                        <Form.Item
                          label="模型"
                          extra={getAgentBoundModel(getFieldValue('agentName'))
                            ? '已自动带入该智能体的默认模型；这里只影响本次测试。'
                            : `只显示已测试成功的${getAgentModelRequirementText(selectedAgent)}模型。`}
                        >
                          <Form.Item name="llmModel" noStyle rules={[{ required: true, message: '请选择模型' }]}>
                            <Select
                              options={modelOptions}
                              placeholder={modelOptions.length ? '请选择模型' : '没有匹配的已测试模型'}
                              showSearch
                              optionFilterProp="label"
                              disabled={!modelOptions.length}
                            />
                          </Form.Item>
                        </Form.Item>
                      </>
                    )
                  }}
                </Form.Item>
                <Form.Item
                  noStyle
                  shouldUpdate={(prev, next) => prev.agentName !== next.agentName}
                >
                  {({ getFieldValue }) => {
                    const selectedAgent = agents.find((item) => item.name === getFieldValue('agentName'))
                    return (
                      <Alert
                        className="rag-inline-alert"
                        type="info"
                        showIcon
                        message={selectedAgent?.name === 'leader_agent'
                          ? 'Leader 会先做意图识别，再决定直接回答、调用智能体或接口。'
                          : '当前智能体会直接处理输入；第三方知识库由 Java 后端接入后作为上下文提供。'}
                      />
                    )
                  }}
                </Form.Item>
                <Form.Item name="intent" label="意图">
                  <Input placeholder="campus_search" />
                </Form.Item>
                <Form.Item name="keyword" label="检索关键词">
                  <Input allowClear placeholder="可空，默认由问题推断" />
                </Form.Item>
                <Form.Item
                  name="input"
                  label="输入材料"
                  extra="这里会自动读取当前智能体的 example_input.md，编辑后可保存为下次默认示例。"
                  rules={[{ required: true, message: '请输入输入材料' }]}
                >
                  <TextArea rows={5} placeholder="例如：粘贴知识点、会议对话、PPT 大纲或其他原始材料" />
                </Form.Item>
                <Form.Item
                  noStyle
                  shouldUpdate={(prev, next) => prev.agentName !== next.agentName || prev.input !== next.input}
                >
                  {({ getFieldValue }) => (
                    <Button
                      className="rag-agent-example-save"
                      icon={<SaveOutlined />}
                      onClick={() => saveAgentExampleInput(
                        getFieldValue('agentName') || 'leader_agent',
                        getFieldValue('input'),
                        { updateTestForm: false },
                      )}
                      block
                    >
                      保存为当前智能体示例输入
                    </Button>
                  )}
                </Form.Item>
                <Button type="primary" htmlType="submit" icon={<PlayCircleOutlined />} loading={actionLoading} block>
                  执行智能体
                </Button>
              </Form>
            </Card>
          </Col>
          <Col xs={24} lg={15}>
            <Card title="查询结果" className="rag-panel-card">
              {queryError ? (
                <Alert
                  type="error"
                  showIcon
                  message="智能体执行失败"
                  description={queryError}
                />
              ) : queryResult ? (
                <Space direction="vertical" size="large" className="rag-full">
                  <div className="rag-agent-test-status">
                    <Tag color="green">{getExecutionLabel(queryResult.metadata)}</Tag>
                    <Tag color="blue">入口：{queryResult.metadata?.agentName || 'leader_agent'}</Tag>
                    {queryResult.metadata?.targetAgent && <Tag>目标：{queryResult.metadata.targetAgent}</Tag>}
                    {queryResult.metadata?.executedAgent && <Tag color="geekblue">执行：{queryResult.metadata.executedAgent}</Tag>}
                    <Tag color="volcano">类型：{queryResult.answerType || queryResult.metadata?.answerType || 'text'}</Tag>
                    <Tag color="gold">{queryResult.metadata?.strategyLabel || queryResult.strategy}</Tag>
                  </div>
                  {renderAgentAnswer(queryResult.answer, queryResult)}
                  <Table
                    rowKey={(record) => record.id || record.source}
                    columns={evidenceColumns}
                    dataSource={queryResult.documents || []}
                    pagination={{ pageSize: 5 }}
                  />
                  <Collapse
                    items={[
                      {
                        key: 'trace',
                        label: 'Trace / Metadata',
                        children: <pre className="rag-code-block">{JSON.stringify({ trace: queryResult.trace, metadata: queryResult.metadata }, null, 2)}</pre>,
                      },
                    ]}
                  />
                </Space>
              ) : (
                <Empty description="执行一次后查看答案、证据和 trace" />
              )}
            </Card>
          </Col>
        </Row>
      ),
    },
    {
      key: 'agents',
      label: '多智能体',
      children: (
        <Space direction="vertical" size={16} className="rag-full">
          <Card
            className="rag-workbench-card"
            title="智能体调用测试"
            extra={(
              <Space size={6} wrap>
                <Tag color="blue">智能体 {agents.length}</Tag>
                <Tag color={enabledAgentCount === agents.length ? 'green' : 'orange'}>启用 {enabledAgentCount}</Tag>
                <Tag color={boundAgentCount === agents.length ? 'green' : 'orange'}>已绑定模型 {boundAgentCount}</Tag>
                <Tag color="geekblue">参数：agentName</Tag>
              </Space>
            )}
          >
            <div className="rag-agent-workbench">
              <section className="rag-agent-input-pane">
                <div className="rag-pane-head">
                  <div>
                    <strong>测试输入</strong>
                    <Text type="secondary">选择智能体、确认模型并提交材料。</Text>
                  </div>
                </div>
                <Form
                  form={agentTestForm}
                  layout="vertical"
                  onFinish={handleAgentTest}
                >
                  <Form.Item name="agentName" label="智能体" rules={[{ required: true, message: '请选择智能体' }]}>
                    <Select
                      options={agentTestOptions}
                      showSearch
                      optionFilterProp="label"
                      placeholder="选择智能体"
                      onChange={(value) => fillAgentTestForm(agents.find((item) => item.name === value))}
                    />
                  </Form.Item>
                  <Form.Item
                    noStyle
                    shouldUpdate={(prev, next) => prev.agentName !== next.agentName}
                  >
                    {({ getFieldValue }) => {
                      const selectedAgent = agents.find((item) => item.name === getFieldValue('agentName'))
                      const modelOptions = getModelOptionsForAgent(selectedAgent)
                      const boundModel = getAgentBoundModel(getFieldValue('agentName'))
                      return (
                        <>
                          <div className="rag-agent-context">
                            <Space size={[6, 6]} wrap>
                              <Tag color={selectedAgent?.name === 'leader_agent' ? 'purple' : 'geekblue'}>
                                {selectedAgent?.role || selectedAgent?.name || '未选择'}
                              </Tag>
                              <Tag color="blue">模型：{getAgentModelRequirementText(selectedAgent)}</Tag>
                              {boundModel ? <Tag color="green">默认模型已带入</Tag> : <Tag color="orange">未绑定默认模型</Tag>}
                            </Space>
                            <Text type="secondary">
                              {selectedAgent?.name === 'leader_agent'
                                ? 'Leader 会自动判断直接回答、调用专业智能体或调用接口。'
                                : selectedAgent?.purpose || '该智能体会直接处理输入内容。'}
                            </Text>
                          </div>
                          {!modelOptions.length ? (
                            <Alert
                              className="rag-compact-alert"
                              type="warning"
                              showIcon
                              message={`没有可选模型，请先测试成功至少一个${getAgentModelRequirementText(selectedAgent)}模型。`}
                              action={<Button size="small" type="primary" onClick={() => { window.location.href = '/ai/model' }}>去配置模型</Button>}
                            />
                          ) : null}
                          <Form.Item
                            label="本次模型"
                            extra={boundModel ? '已自动带入默认模型；修改后只影响本次测试。' : `只显示已测试成功的${getAgentModelRequirementText(selectedAgent)}模型。`}
                          >
                            <Form.Item name="llmModel" noStyle rules={[{ required: true, message: '请选择模型' }]}>
                              <Select
                                options={modelOptions}
                                placeholder={modelOptions.length ? `请选择${getAgentModelRequirementText(selectedAgent)}模型` : '没有匹配的已测试模型'}
                                showSearch
                                optionFilterProp="label"
                                disabled={!modelOptions.length}
                              />
                            </Form.Item>
                          </Form.Item>
                        </>
                      )
                    }}
                  </Form.Item>
                  <Form.Item
                    name="input"
                    label="测试输入"
                    extra="默认读取智能体目录下的 example_input.md，可编辑后保存为下次示例。"
                    rules={[{ required: true, message: '请输入测试内容' }]}
                  >
                    <TextArea rows={8} placeholder="输入一段课程内容、会议记录、PPT 大纲或任务要求" />
                  </Form.Item>
                  <Form.Item
                    noStyle
                    shouldUpdate={(prev, next) => prev.agentName !== next.agentName}
                  >
                    {({ getFieldValue }) => {
                      const selectedAgent = agents.find((item) => item.name === getFieldValue('agentName'))
                      const supportsVision = getAgentRequiredModelModalities(selectedAgent).includes('vision')
                      if (!supportsVision) return null
                      return (
                        <Form.Item
                          label="测试图片"
                          extra={`支持 JPG、PNG、WebP 等常见图片，最多 ${MAX_AGENT_TEST_IMAGES} 张，单张不超过 10MB。`}
                        >
                          <Upload
                            accept="image/*"
                            listType="picture-card"
                            fileList={agentTestImages}
                            beforeUpload={beforeAgentTestImageUpload}
                            onRemove={(file) => {
                              setAgentTestImages((current) => current.filter((item) => item.uid !== file.uid))
                              return true
                            }}
                            multiple
                          >
                            {agentTestImages.length < MAX_AGENT_TEST_IMAGES ? (
                              <div>
                                <PlusOutlined />
                                <div className="rag-agent-image-upload-label">上传图片</div>
                              </div>
                            ) : null}
                          </Upload>
                        </Form.Item>
                      )
                    }}
                  </Form.Item>
                  <Form.Item
                    noStyle
                    shouldUpdate={(prev, next) => prev.agentName !== next.agentName || prev.input !== next.input}
                  >
                    {({ getFieldValue }) => (
                      <Button
                        className="rag-agent-example-save"
                        icon={<SaveOutlined />}
                        onClick={() => saveAgentExampleInput(
                          getFieldValue('agentName'),
                          getFieldValue('input'),
                        )}
                        block
                      >
                        保存为该智能体示例输入
                      </Button>
                    )}
                  </Form.Item>
                  <Button type="primary" htmlType="submit" icon={<PlayCircleOutlined />} loading={agentTestLoading} block>
                    调用当前智能体
                  </Button>
                </Form>
              </section>

              <section className="rag-agent-result-pane">
                <div className="rag-pane-head">
                  <div>
                    <strong>执行结果</strong>
                    <Text type="secondary">查看回答、证据、请求参数和 trace。</Text>
                  </div>
                  {!agentTestResult?.error && agentTestResult && getQuestionBankPayloadFromResult(agentTestResult) ? (
                    <Button
                      size="small"
                      type="primary"
                      icon={<DatabaseOutlined />}
                      loading={questionImportLoading}
                      onClick={handleImportQuestionBank}
                    >
                      导入题库
                    </Button>
                  ) : null}
                </div>
                {agentTestResult ? (
                  <Space direction="vertical" size={14} className="rag-full">
                    <div className="rag-agent-test-status">
                      <Tag color={agentTestResult.error ? 'red' : 'green'}>
                        {agentTestResult.error ? '调用失败' : '调用成功'}
                      </Tag>
                      <Tag color="blue">{agentTestResult.agent?.name}</Tag>
                      <Tag color="purple">{getExecutionLabel(agentTestResult.response?.metadata)}</Tag>
                      {agentTestResult.response?.metadata?.executedAgent && <Tag color="geekblue">执行：{agentTestResult.response.metadata.executedAgent}</Tag>}
                      <Tag color="volcano">类型：{agentTestResult.response?.answerType || agentTestResult.response?.metadata?.answerType || 'text'}</Tag>
                    </div>
                    {agentTestResult.error ? (
                      <div className="rag-answer-box">{agentTestResult.error}</div>
                    ) : (
                      <>
                        {renderAgentAnswer(agentTestResult.response?.answer, agentTestResult.response)}
                        <Table
                          rowKey={(record) => record.id || record.source}
                          columns={evidenceColumns}
                          dataSource={agentTestResult.response?.documents || []}
                          pagination={{ pageSize: 4 }}
                          size="small"
                        />
                      </>
                    )}
                    <Collapse
                      size="small"
                      items={[
                        {
                          key: 'request',
                          label: '请求参数',
                          children: <pre className="rag-code-block">{JSON.stringify(agentTestResult.request || {}, null, 2)}</pre>,
                        },
                        {
                          key: 'trace',
                          label: 'Trace / Metadata',
                          children: <pre className="rag-code-block">{JSON.stringify({
                            trace: agentTestResult.response?.trace || [],
                            metadata: agentTestResult.response?.metadata || {},
                          }, null, 2)}</pre>,
                        },
                      ]}
                    />
                  </Space>
                ) : (
                  <Empty className="rag-result-empty" description="选择智能体并调用后，这里显示回答、证据和 trace" />
                )}
              </section>
            </div>
          </Card>

          <Card className="rag-agent-info-card">
            <Tabs
              className="rag-agent-info-tabs"
              items={[
                {
                  key: 'directory',
                  label: '智能体目录',
                  children: (
                    <Table
                      rowKey="name"
                      columns={tableAgentColumns}
                      dataSource={agents}
                      pagination={{ pageSize: 8 }}
                      scroll={{ x: 880 }}
                    />
                  ),
                },
                {
                  key: 'workflow',
                  label: '协作流程',
                  children: <pre className="rag-code-block">{JSON.stringify(agentWorkflow, null, 2)}</pre>,
                },
                {
                  key: 'documents',
                  label: `技能文档 ${agentExampleCount}/${agents.length}`,
                  children: (
                    <Collapse
                      items={agents.map((agent) => ({
                        key: agent.name,
                        label: `${agent.name} · ${agent.role}`,
                        children: (
                          <div className="rag-agent-doc-panel">
                            <Text>{agent.purpose}</Text>
                            <Space size={[6, 6]} wrap>
                              {(agent.inputs || []).map((item) => <Tag key={`input-${item}`}>输入：{item}</Tag>)}
                              {(agent.outputs || []).map((item) => <Tag color="blue" key={`output-${item}`}>输出：{item}</Tag>)}
                            </Space>
                            <TextArea
                              rows={5}
                              value={agent.documents?.exampleInput || getAgentExampleInput(agent)}
                              onChange={(event) => {
                                const value = event.target.value
                                setAgents((prev) => prev.map((item) => (
                                  item.name === agent.name
                                    ? {
                                        ...item,
                                        documents: { ...(item.documents || {}), exampleInput: value },
                                        invokeExample: { ...(item.invokeExample || {}), input: value },
                                      }
                                    : item
                                )))
                              }}
                            />
                            <Space wrap>
                              <Button
                                icon={<SaveOutlined />}
                                onClick={() => saveAgentExampleInput(
                                  agent.name,
                                  agent.documents?.exampleInput || getAgentExampleInput(agent),
                                  { updateTestForm: false },
                                )}
                              >
                                保存 example_input.md
                              </Button>
                              <Button
                                icon={<PlayCircleOutlined />}
                                onClick={() => fillAgentTestForm(agent)}
                              >
                                填入上方测试台
                              </Button>
                            </Space>
                            <Collapse
                              size="small"
                              items={[
                                {
                                  key: 'skill',
                                  label: 'skill.md',
                                  children: <pre className="rag-code-block">{agent.documents?.skill || '暂无 skill 文档'}</pre>,
                                },
                                {
                                  key: 'prompt',
                                  label: 'prompt.md',
                                  children: <pre className="rag-code-block">{agent.documents?.prompt || '暂无 prompt 文档'}</pre>,
                                },
                                {
                                  key: 'contract',
                                  label: 'contract.md / tools.yaml',
                                  children: <pre className="rag-code-block">{`${agent.documents?.contract || ''}\n\n${agent.documents?.tools || ''}`}</pre>,
                                },
                                {
                                  key: 'files',
                                  label: '文件路径',
                                  children: <pre className="rag-code-block">{JSON.stringify(agent.files || {}, null, 2)}</pre>,
                                },
                              ]}
                            />
                          </div>
                        ),
                      }))}
                    />
                  ),
                },
              ]}
            />
          </Card>
        </Space>
      ),
    },
    {
      key: 'framework',
      label: '框架配置',
      children: (
        <Space direction="vertical" size="large" className="rag-full">
          <Card title="能力覆盖" className="rag-panel-card">
            <Table
              rowKey="name"
              columns={coverageColumns}
              dataSource={framework?.coverage || []}
              pagination={{ pageSize: 8 }}
            />
          </Card>
          <Card title="工具能力" className="rag-panel-card">
            <Table
              rowKey="name"
              columns={toolColumns}
              dataSource={framework?.generatedTools || []}
              pagination={false}
              scroll={{ x: 980 }}
            />
          </Card>
          <div className="rag-provider-grid">
            {renderProviderCard('Model Provider', framework?.modelProviders)}
          </div>
          <Row gutter={[20, 20]}>
            <Col xs={24} lg={10}>
              <Card title="运行环境" className="rag-panel-card">
                <Table
                  rowKey="name"
                  columns={envColumns}
                  dataSource={framework?.runtimeEnv || []}
                  pagination={false}
                  size="small"
                />
              </Card>
            </Col>
            <Col xs={24} lg={14}>
              <Card title="目录与 API" className="rag-panel-card">
                <Collapse
                  items={[
                    {
                      key: 'folders',
                      label: '运行目录',
                      children: <pre className="rag-code-block">{JSON.stringify(framework?.runtimeFolders || {}, null, 2)}</pre>,
                    },
                    {
                      key: 'apis',
                      label: '接口清单',
                      children: <pre className="rag-code-block">{JSON.stringify(framework?.apis || [], null, 2)}</pre>,
                    },
                  ]}
                />
              </Card>
            </Col>
          </Row>
        </Space>
      ),
    },
    {
      key: 'sql',
      label: 'Text-to-SQL',
      children: (
        <Row gutter={[20, 20]}>
          <Col xs={24} lg={9}>
            <Card title="自然语言查结构化数据" className="rag-panel-card">
              <Form form={sqlForm} layout="vertical" onFinish={handleTextToSql}>
                <Form.Item name="input" label="查询问题" rules={[{ required: true, message: '请输入查询问题' }]}>
                  <TextArea rows={5} placeholder="例如：查询黄焖鸡 / 统计优惠券列表" />
                </Form.Item>
                <Button type="primary" htmlType="submit" loading={actionLoading} block>
                  生成并执行 SQL
                </Button>
              </Form>
            </Card>
          </Col>
          <Col xs={24} lg={15}>
            <Card title="Schema 与执行结果" className="rag-panel-card">
              <Collapse
                defaultActiveKey={['result']}
                items={[
                  {
                    key: 'result',
                    label: '执行结果',
                    children: <pre className="rag-code-block">{JSON.stringify(sqlResult || {}, null, 2)}</pre>,
                  },
                  {
                    key: 'schema',
                    label: '当前 Schema',
                    children: <pre className="rag-code-block">{JSON.stringify(sqlSchema || {}, null, 2)}</pre>,
                  },
                ]}
              />
            </Card>
          </Col>
        </Row>
      ),
    },
  ]

  const pageConfig = ragPageConfig[page] || ragPageConfig.playground
  const activeSection = sections.find((item) => item.key === pageConfig.sectionKey) || sections[0]

  return (
    <div className="rag-manage">
      <section className="rag-toolbar">
        <div className="rag-heading">
          <Title level={2}>{pageConfig.title}</Title>
          <Text type="secondary">{pageConfig.description}</Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={refresh} loading={bootLoading}>
          刷新状态
        </Button>
      </section>

      <div className="rag-tabs">
        {activeSection.children}
      </div>
    </div>
  )
}

export default RagManage
