import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Drawer, Empty, Input, Modal, Segmented, Select, Space, Switch, Table, Tabs, Tag, Typography, Upload, message } from 'antd'
import { ApiOutlined, CheckCircleOutlined, DownloadOutlined, ExclamationCircleOutlined, PlusOutlined, ReloadOutlined, RobotOutlined, SaveOutlined, SettingOutlined } from '@ant-design/icons'
import { getRagAgents, runRagQuery, testFileContentTool } from '../../../api/rag'
import { getSystemConfigList, upsertSystemConfig } from '../../../api/systemConfig'
import axios from 'axios'
import { API_BASE_URL } from '../../../config/apiBase'
import {
  AGENT_ENABLED_CONFIG_PREFIX,
  QUESTION_GENERATION_AGENT_PREFIX,
  QUESTION_TYPE_OPTIONS,
  TOOL_BOUND_CONFIG_PREFIX,
  TOOL_BOUND_UNBOUND_MARKER,
  TOOL_ENABLED_CONFIG_PREFIX,
  TOOL_RETRIEVAL_CONFIG_PREFIX,
  buildAgentModelBindings,
  buildQuestionGenerationAgentMappings,
  buildToolBindings,
  buildToolToggles,
  buildToolRetrievalProfiles,
  buildLlmModelOptions,
  getAgentModelRequirementText,
  getAgentRequiredModelModalities,
  isAgentEnabled,
  isToolEnabled,
  MODEL_MODALITY_LABELS,
  resolveQuestionGenerationAgentStatus,
} from '../agentConfig'
import './AgentSettings.css'

const { Text, Title } = Typography
const TOOL_TEST_IMAGE_MAX_BYTES = 10 * 1024 * 1024
const TOOL_TEST_IMAGE_MAX_EDGE = 1920
const TOOL_TEST_IMAGE_JPEG_QUALITY = 0.82
const TOOL_TEST_STITCH_MAX_IMAGES = 9
const TOOL_TEST_FILE_MAX_BYTES = 25 * 1024 * 1024
const FILE_CONTENT_TOOL_NAMES = new Set([
  'markdown_to_text_tool',
  'txt_to_text_tool',
  'word_to_text_tool',
  'ppt_to_text_tool',
  'pdf_to_text_tool',
])
const FILE_CONTENT_TOOL_ACCEPT = {
  markdown_to_text_tool: '.md,.markdown',
  txt_to_text_tool: '.txt',
  word_to_text_tool: '.docx',
  ppt_to_text_tool: '.pptx',
  pdf_to_text_tool: '.pdf',
}
const isFileContentTool = (tool) => FILE_CONTENT_TOOL_NAMES.has(tool?.name)

const TOOL_TRIGGER_TYPE_VIEW = {
  system: { label: '系统主动触发', color: 'blue' },
  leader: { label: 'Leader 协调', color: 'purple' },
  rule_direct: { label: '规则直调', color: 'cyan' },
  workflow_dependency: { label: '工作流依赖', color: 'gold' },
}

const renderToolTriggerType = (value) => {
  const view = TOOL_TRIGGER_TYPE_VIEW[value] || { label: value || 'Leader 协调', color: 'default' }
  return <Tag color={view.color}>{view.label}</Tag>
}

const TOOL_TEST_PROMPTS = {
  recognize_image_tool: '请识别我上传的图片，概括主要内容并读取其中清晰可见的文字。',
  generate_image_tool: '请生成一张简洁的智慧校园首页插图，浅色背景，蓝灰色调，不包含文字。',
  generate_mind_map_image_tool: '请生成一张“校园二手交易流程”思维导图图片，包含发布、沟通、线下交易三个分支。',
  generate_flowchart_image_tool: '请生成校园二手商品发布审核流程图图片。',
  generate_architecture_image_tool: '请生成校园二手交易平台的前端、后端、MySQL、Redis系统架构图图片。',
  generate_knowledge_graph_image_tool: '请生成学生、商品、分类、订单之间关系的知识图谱图片。',
  generate_ppt_image_tool: '请生成一张智慧校园主题的 PPT 封面配图，16:9，蓝灰色，留出标题区域。',
  image_stitching_tool: '请将我上传的图片按照上传顺序拼接成一张图片。',
  text_to_sql: '请统计当前系统中的二手商品数量，并返回查询结果。',
  java_schedule_api: '请查询我本周的课程安排。',
  java_activity_api: '请查询当前可报名的校园活动。',
  java_meeting_api: '请查询我的会议列表和会议状态。',
  java_canteen_api: '请查询食堂档口和菜品信息。',
  java_facility_api: '请查询校园设施及其位置信息。',
  java_secondhand_api: '请查询当前在售的二手商品。',
  tool_capability_query: '请列出当前系统已经启用并且可以调用的工具能力。',
  generated_export_tools: '请把以下内容整理为 Markdown 和 Word 文件并提供下载：校园二手交易应当当面验货、确认商品状态后再完成交易。',
  text_to_markdown_tool: '请把以下内容按原文转成Markdown文件：校园二手交易应当当面验货、确认商品状态后再完成交易。',
  text_to_txt_tool: '请把以下内容按原文转成纯文本文件：校园二手交易应当当面验货、确认商品状态后再完成交易。',
  text_to_docx_tool: '请把以下内容按原文转成Word文件：校园二手交易应当当面验货、确认商品状态后再完成交易。',
  markdown_export_tool: '请把以下内容导出为 Markdown 文件：校园二手交易测试内容。',
  docx_export_tool: '请把以下内容导出为 Word 文档：校园二手交易测试内容。',
  excel_export_tool: '请把以下清单导出为 Excel：商品A，分类教材；商品B，分类数码。',
  pptx_export_tool: '请把以下内容生成并导出为 PPTX：校园二手交易平台介绍，包括发布、沟通、线下交易。',
  content_archive_tool: '请把以下内容分别导出为 Markdown 和 Word，并将所有附件打包成 ZIP：校园二手交易测试内容。',
  diagram_source_export_tool: '请生成校园二手交易流程的 Mermaid 流程图，并导出图表源码文件。',
}

const getToolTestPrompt = (tool) => TOOL_TEST_PROMPTS[tool?.name]
  || `请执行${tool?.zhName || tool?.name || '当前工具'}测试，并返回可验证的输出。${tool?.trigger ? `触发要求：${tool.trigger}` : ''}`

const readToolTestImage = (file) => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => resolve(String(reader.result || ''))
  reader.onerror = () => reject(new Error(`图片读取失败：${file.name}`))
  reader.readAsDataURL(file)
})

const compressToolTestImage = (file) => new Promise((resolve, reject) => {
  if (String(file?.type || '').toLowerCase() === 'image/gif') {
    readToolTestImage(file).then(resolve).catch(reject)
    return
  }
  const objectUrl = URL.createObjectURL(file)
  const image = new Image()
  image.onload = () => {
    URL.revokeObjectURL(objectUrl)
    const { width, height } = image
    const longest = Math.max(width, height)
    const scale = longest > TOOL_TEST_IMAGE_MAX_EDGE ? TOOL_TEST_IMAGE_MAX_EDGE / longest : 1
    const targetWidth = Math.max(1, Math.round(width * scale))
    const targetHeight = Math.max(1, Math.round(height * scale))
    const canvas = document.createElement('canvas')
    canvas.width = targetWidth
    canvas.height = targetHeight
    const context = canvas.getContext('2d')
    if (!context) {
      readToolTestImage(file).then(resolve).catch(reject)
      return
    }
    context.drawImage(image, 0, 0, targetWidth, targetHeight)
    resolve(canvas.toDataURL('image/jpeg', TOOL_TEST_IMAGE_JPEG_QUALITY))
  }
  image.onerror = () => {
    URL.revokeObjectURL(objectUrl)
    reject(new Error(`图片读取失败：${file.name}`))
  }
  image.src = objectUrl
})

const getToolTestFileExtension = (file) => {
  const name = String(file?.name || '').toLowerCase()
  return name.includes('.') ? name.split('.').pop() : ''
}

const isToolTestImageFile = (file) => (
  String(file?.type || '').toLowerCase().startsWith('image/')
  || ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'tif', 'tiff'].includes(getToolTestFileExtension(file))
)

const responseContainsTool = (response, toolName) => {
  if (!response || !toolName) return false
  const values = []
  const visit = (value, depth = 0) => {
    if (depth > 6 || value === null || value === undefined) return
    if (typeof value === 'string') {
      values.push(value)
      return
    }
    if (Array.isArray(value)) {
      value.forEach((item) => visit(item, depth + 1))
      return
    }
    if (typeof value === 'object') {
      Object.entries(value).forEach(([key, item]) => {
        if (['toolName', 'tool', 'executedTool', 'executedAgent', 'targetAgent'].includes(key)) values.push(String(item || ''))
        visit(item, depth + 1)
      })
    }
  }
  visit({ trace: response.trace, metadata: response.metadata, attachments: response.attachments })
  return values.some((value) => value === toolName)
}
const getToolDisplayName = (tool) => {
  if (!tool) return ''
  if (tool.displayName) return tool.displayName
  if (tool.zhName && tool.name) return `${tool.zhName}（${tool.name}）`
  return tool.name || ''
}

const getToolCategoryLabel = (category) => {
  const labels = {
    campus_service: '系统能力',
    structured_query: '结构化查询',
    content_export: '内容整理',
    file_content_extraction: '文件内容识别',
    diagram_export: '图表导出',
    presentation_generation: 'PPT 生成',
    vision_understanding: '图片理解',
  }
  return labels[category] || category || '-'
}

const getShortModelName = (modelValue) => {
  if (!modelValue) return ''
  return String(modelValue)
    .replace(/^ai\.service\./, '')
    .replace(/^(text|vision)\./, '')
}

const renderOutputs = (outputs) => (
  <Space size={[6, 6]} wrap>
    {(Array.isArray(outputs) ? outputs : []).map((item) => (
      <Tag color="blue" key={item}>{String(item).toUpperCase()}</Tag>
    ))}
  </Space>
)

const defaultRetrievalProfile = (tool) => ({
  description: tool?.purpose || '',
  keywords: [],
  aliases: [],
  entities: [],
  constraints: [],
  negativeCases: [],
  examples: [],
})

const retrievalProfileText = (tool, profile) => {
  const value = { ...defaultRetrievalProfile(tool), ...(profile || {}) }
  return [
    `说明：${value.description || ''}`,
    `关键词：${(value.keywords || []).join('、')}`,
    `用户说法：${(value.aliases || []).join('、')}`,
    `实体：${(value.entities || []).join('、')}`,
    `限制条件：${(value.constraints || []).join('、')}`,
    `不适用：${(value.negativeCases || []).join('、')}`,
    `示例：${(value.examples || []).join('；')}`,
  ].join('\n')
}

const parseRetrievalProfileText = (text, tool) => {
  const profile = defaultRetrievalProfile(tool)
  String(text || '').split('\n').forEach((line) => {
    const match = line.match(/^([^：:]+)[：:](.*)$/)
    if (!match) return
    const fieldMap = {
      说明: 'description',
      关键词: 'keywords',
      用户说法: 'aliases',
      实体: 'entities',
      限制条件: 'constraints',
      不适用: 'negativeCases',
      示例: 'examples',
    }
    const field = fieldMap[match[1].trim()]
    if (!field) return
    if (field === 'description') profile[field] = match[2].trim()
    else profile[field] = match[2].split(/[、,，;；]/).map((item) => item.trim()).filter(Boolean)
  })
  return profile
}

const parseGeneratedRetrievalProfile = (answer, tool) => {
  const raw = String(answer || '').replace(/^```json\s*/i, '').replace(/^```\s*/i, '').replace(/\s*```$/i, '').trim()
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null
    const profile = defaultRetrievalProfile(tool)
    profile.description = String(parsed.description || parsed.说明 || tool.purpose || '').trim()
    const fields = {
      keywords: parsed.keywords || parsed.关键词,
      aliases: parsed.aliases || parsed.用户说法,
      entities: parsed.entities || parsed.实体,
      constraints: parsed.constraints || parsed.限制条件,
      negativeCases: parsed.negativeCases || parsed.不适用,
      examples: parsed.examples || parsed.示例,
    }
    Object.entries(fields).forEach(([key, value]) => {
      profile[key] = Array.isArray(value)
        ? value.map((item) => String(item).trim()).filter(Boolean)
        : String(value || '').split(/[、,，;；]/).map((item) => item.trim()).filter(Boolean)
    })
    return profile
  } catch {
    return null
  }
}

const retrievalProfileFields = [
  ['description', '说明'],
  ['keywords', '关键词'],
  ['aliases', '用户说法'],
  ['entities', '实体'],
  ['constraints', '限制条件'],
  ['negativeCases', '不适用'],
  ['examples', '示例'],
]

const profileValues = (profile, field) => {
  const value = profile?.[field]
  return Array.isArray(value) ? value.filter(Boolean).map(String) : (value ? [String(value)] : [])
}

const ProfileItems = ({ profile, empty = '未配置' }) => (
  <Space size={[6, 6]} wrap>
    {profile?.length ? profile.map((item) => <Tag key={item}>{item}</Tag>) : <Text type="secondary">{empty}</Text>}
  </Space>
)

function AgentSettings() {
  const [loading, setLoading] = useState(false)
  const [savingKey, setSavingKey] = useState('')
  const [agents, setAgents] = useState([])
  const [leaderTools, setLeaderTools] = useState([])
  const [tools, setTools] = useState([])
  const [internalTools, setInternalTools] = useState([])
  const [fileFormats, setFileFormats] = useState([])
  const [llmModelOptions, setLlmModelOptions] = useState([])
  const [agentModelBindings, setAgentModelBindings] = useState({})
  const [draftBindings, setDraftBindings] = useState({})
  const [questionAgentMappings, setQuestionAgentMappings] = useState({})
  const [draftQuestionAgentMappings, setDraftQuestionAgentMappings] = useState({})
  const [toolBindings, setToolBindings] = useState({})
  const [draftToolBindings, setDraftToolBindings] = useState({})
  const [, setToolRetrievalProfiles] = useState({})
  const [draftToolRetrievalProfiles, setDraftToolRetrievalProfiles] = useState({})
  const [retrievalDrawerTool, setRetrievalDrawerTool] = useState(null)
  const [retrievalDrawerOpen, setRetrievalDrawerOpen] = useState(false)
  const [retrievalDrawerProfile, setRetrievalDrawerProfile] = useState(null)
  const [retrievalGeneratedProfile, setRetrievalGeneratedProfile] = useState(null)
  const [retrievalGenerating, setRetrievalGenerating] = useState('')
  const [activeTab, setActiveTab] = useState('overview')
  const [leaderObjectType, setLeaderObjectType] = useState('all')
  const [toolTriggerType, setToolTriggerType] = useState('all')
  const [leaderToolFilter, setLeaderToolFilter] = useState('all')
  const [selectedToolKeys, setSelectedToolKeys] = useState([])
  const [runtimeAgentFilter, setRuntimeAgentFilter] = useState('all')
  const [testUsername, setTestUsername] = useState('zzs')
  const [testPassword, setTestPassword] = useState('admin123')
  const [endpointDrawerOpen, setEndpointDrawerOpen] = useState(false)
  const [endpointDrawerTool, setEndpointDrawerTool] = useState(null)
  const [endpointTestResults, setEndpointTestResults] = useState({})
  const [toolTestName, setToolTestName] = useState('')
  const [toolTestMode, setToolTestMode] = useState('prompt')
  const [toolTestInput, setToolTestInput] = useState('')
  const [toolTestImages, setToolTestImages] = useState([])
  const [toolTestFileList, setToolTestFileList] = useState([])
  const [toolTestLoading, setToolTestLoading] = useState(false)
  const [toolTestResult, setToolTestResult] = useState(null)
  const [toolTestPreview, setToolTestPreview] = useState('')

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const [agentRes, configRes] = await Promise.all([
        getRagAgents(),
        getSystemConfigList({
          current: 1,
          size: 500,
          prefixes: 'ai.service.,ai.agent-bindings.,ai.agent-enabled.,ai.tool-enabled.,ai.tool-bound.,ai.tool-retrieval.,ai.question-generation.agent.',
        }),
      ])
      const configRows = configRes.data?.records || []
      const nextBindings = buildAgentModelBindings(configRows)
      const nextToolToggles = buildToolToggles(configRows)
      const nextToolBindings = buildToolBindings(configRows)
      const nextToolRetrievalProfiles = buildToolRetrievalProfiles(configRows)
      const nextQuestionAgentMappings = buildQuestionGenerationAgentMappings(configRows)
      setAgents(agentRes.data?.agents || [])
      setInternalTools(agentRes.data?.internalTools || [])
      setFileFormats(agentRes.data?.fileFormats || [])
      setTools((agentRes.data?.generatedTools || []).map((tool) => {
        const hasConfiguredValue = Object.prototype.hasOwnProperty.call(nextToolToggles, tool.name)
        const hasBoundConfig = Object.prototype.hasOwnProperty.call(nextToolBindings, tool.name)
        return {
          ...tool,
          enabled: hasConfiguredValue ? nextToolToggles[tool.name] : tool.enabled !== false,
          boundAgent: hasBoundConfig ? nextToolBindings[tool.name] : (tool.boundAgent || ''),
          retrievalProfile: nextToolRetrievalProfiles[tool.name] || defaultRetrievalProfile(tool),
        }
      }))
      setLeaderTools((agentRes.data?.leaderTools || []).map((tool) => {
        const hasConfiguredValue = Object.prototype.hasOwnProperty.call(nextToolToggles, tool.name)
        const hasBoundConfig = Object.prototype.hasOwnProperty.call(nextToolBindings, tool.name)
        return {
          ...tool,
          enabled: tool.configurable === false ? true : hasConfiguredValue ? nextToolToggles[tool.name] : tool.enabled !== false,
          boundAgent: hasBoundConfig ? nextToolBindings[tool.name] : (tool.boundAgent || ''),
          retrievalProfile: nextToolRetrievalProfiles[tool.name] || defaultRetrievalProfile(tool),
        }
      }))
      setLlmModelOptions(buildLlmModelOptions(configRows))
      setAgentModelBindings(nextBindings)
      setDraftBindings(nextBindings)
      setQuestionAgentMappings(nextQuestionAgentMappings)
      setDraftQuestionAgentMappings(nextQuestionAgentMappings)
      setToolBindings(nextToolBindings)
      setDraftToolBindings(nextToolBindings)
      setToolRetrievalProfiles(nextToolRetrievalProfiles)
      setDraftToolRetrievalProfiles(Object.fromEntries(
        [...(agentRes.data?.generatedTools || []), ...(agentRes.data?.leaderTools || [])].map((tool) => [
          tool.name,
          retrievalProfileText(tool, nextToolRetrievalProfiles[tool.name]),
        ]),
      ))
    } catch (error) {
      message.error(error.message || '加载智能体设置失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const getModelOptionsForAgent = useCallback((agent) => {
    const required = getAgentRequiredModelModalities(agent)
    return llmModelOptions.filter((option) => required.includes(option.modality))
  }, [llmModelOptions])

  const saveAgentEnabled = useCallback(async (agentName, enabled) => {
    if (agentName === 'leader_agent') {
      message.warning('Leader 是总控入口，必须保持开启')
      return
    }
    setSavingKey(`enabled:${agentName}`)
    try {
      await upsertSystemConfig({
        configKey: `${AGENT_ENABLED_CONFIG_PREFIX}${agentName}`,
        configValue: enabled ? '1' : '0',
        configGroup: 'ai',
        description: `智能体 ${agentName} 启用开关`,
        status: 1,
        isDefault: 0,
      })
      setAgents((prev) => prev.map((item) => (
        item.name === agentName ? { ...item, enabled } : item
      )))
      message.success(enabled ? '智能体已开启' : '智能体已关闭，Leader 路由到它时会跳过')
    } catch (error) {
      message.error(error.message || '智能体开关保存失败')
    } finally {
      setSavingKey('')
    }
  }, [])

  const saveAgentModelBinding = useCallback(async (agentName) => {
    const modelValue = String(draftBindings[agentName] || '').trim()
    if (!modelValue) {
      message.warning('请先选择默认模型')
      return
    }
    setSavingKey(`model:${agentName}`)
    try {
      await upsertSystemConfig({
        configKey: `ai.agent-bindings.${agentName}.model`,
        configValue: modelValue,
        configGroup: 'ai',
        description: `智能体 ${agentName} 默认模型绑定`,
        status: 1,
        isDefault: 0,
      })
      setAgentModelBindings((prev) => ({ ...prev, [agentName]: modelValue }))
      message.success('默认模型已保存')
    } catch (error) {
      message.error(error.message || '默认模型保存失败')
    } finally {
      setSavingKey('')
    }
  }, [draftBindings])

  const saveToolEnabled = useCallback(async (toolName, enabled) => {
    setSavingKey(`tool:${toolName}`)
    try {
      await upsertSystemConfig({
        configKey: `${TOOL_ENABLED_CONFIG_PREFIX}${toolName}`,
        configValue: enabled ? '1' : '0',
        configGroup: 'ai',
        description: `工具 ${toolName} 启用开关`,
        status: 1,
        isDefault: 0,
      })
      setTools((prev) => prev.map((item) => (
        item.name === toolName ? { ...item, enabled } : item
      )))
      setLeaderTools((prev) => prev.map((item) => (
        item.name === toolName ? { ...item, enabled } : item
      )))
      message.success(enabled ? '工具已开启，Leader 可调用' : '工具已关闭，Leader 不会调用')
      return true
    } catch (error) {
      message.error(error.message || '工具开关保存失败')
      return false
    } finally {
      setSavingKey('')
    }
  }, [])

  const saveToolBinding = useCallback(async (toolName) => {
    const value = String(draftToolBindings[toolName] ?? '').trim()
    setSavingKey(`tool-binding:${toolName}`)
    try {
      await upsertSystemConfig({
        configKey: `${TOOL_BOUND_CONFIG_PREFIX}${toolName}`,
        configValue: value || TOOL_BOUND_UNBOUND_MARKER,
        configGroup: 'ai',
        description: `工具 ${toolName} 绑定智能体`,
        status: 1,
        isDefault: 0,
      })
      setToolBindings((prev) => ({ ...prev, [toolName]: value }))
      setTools((prev) => prev.map((item) => (
        item.name === toolName ? { ...item, boundAgent: value } : item
      )))
      setLeaderTools((prev) => prev.map((item) => (
        item.name === toolName ? { ...item, boundAgent: value } : item
      )))
      message.success(value ? `已绑定智能体 ${value}` : '已设置为暂不绑定')
    } catch (error) {
      message.error(error.message || '绑定智能体保存失败')
    } finally {
      setSavingKey('')
    }
  }, [draftToolBindings])

  const buildToolImpactText = useCallback((record) => {
    const lines = []
    if (record.name === 'generated_export_tools') {
      lines.push('这是内容整理总开关，关闭后 Leader 不会调用导出整理能力，自动附件整理也会停止。')
    }
    if (record.boundAgent) {
      const boundAgent = agents.find((item) => item.name === record.boundAgent)
      lines.push(`绑定智能体：${boundAgent?.role || record.boundAgent}，其启用状态会影响本工具可用性。`)
    }
    if (record.trigger) {
      lines.push(`触发条件：${record.trigger}`)
    }
    return lines.join('\n')
  }, [agents])

  const handleToolToggleChange = useCallback((record, checked) => {
    if (checked) {
      saveToolEnabled(record.name, true)
      return
    }
    Modal.confirm({
      title: '确认关闭工具',
      content: buildToolImpactText(record) || '关闭后 Leader 将不再调用该工具。',
      okText: '确认关闭',
      cancelText: '取消',
      onOk: () => saveToolEnabled(record.name, false),
    })
  }, [saveToolEnabled, buildToolImpactText])

  const bulkSetToolsEnabled = useCallback(async (enabled) => {
    const names = [...selectedToolKeys]
    if (!names.length) return
    Modal.confirm({
      title: enabled ? '批量开启工具' : '批量关闭工具',
      content: `将对选中的 ${names.length} 个工具执行${enabled ? '开启' : '关闭'}。`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        let failed = 0
        for (const name of names) {
          const ok = await saveToolEnabled(name, enabled)
          if (!ok) failed += 1
        }
        message.success(failed ? `已${enabled ? '开启' : '关闭'} ${names.length - failed}/${names.length} 个工具，${failed} 个失败` : `已${enabled ? '开启' : '关闭'} ${names.length} 个工具`)
        setSelectedToolKeys([])
      },
    })
  }, [selectedToolKeys, saveToolEnabled])

  const saveToolRetrievalProfile = useCallback(async (tool) => {
    const text = draftToolRetrievalProfiles[tool.name] || retrievalProfileText(tool, tool.retrievalProfile)
    const profile = retrievalDrawerProfile || parseRetrievalProfileText(text, tool)
    setSavingKey(`tool-retrieval:${tool.name}`)
    try {
      await upsertSystemConfig({
        configKey: `${TOOL_RETRIEVAL_CONFIG_PREFIX}${tool.name}`,
        configValue: JSON.stringify(profile, null, 0),
        configGroup: 'ai',
        description: `工具 ${tool.name} 检索说明`,
        status: 1,
        isDefault: 0,
      })
      setToolRetrievalProfiles((prev) => ({ ...prev, [tool.name]: profile }))
      setTools((prev) => prev.map((item) => (item.name === tool.name ? { ...item, retrievalProfile: profile } : item)))
      setLeaderTools((prev) => prev.map((item) => (item.name === tool.name ? { ...item, retrievalProfile: profile } : item)))
      setDraftToolRetrievalProfiles((prev) => ({ ...prev, [tool.name]: retrievalProfileText(tool, profile) }))
      setRetrievalDrawerProfile(profile)
      setRetrievalGeneratedProfile(null)
      message.success('工具检索说明已保存')
      return true
    } catch (error) {
      message.error(error.message || '工具检索说明保存失败')
      return false
    } finally {
      setSavingKey('')
    }
  }, [draftToolRetrievalProfiles, retrievalDrawerProfile])

  const openRetrievalDrawer = useCallback((tool) => {
    const current = tool.retrievalProfile || parseRetrievalProfileText(draftToolRetrievalProfiles[tool.name], tool)
    setRetrievalDrawerTool(tool)
    setRetrievalDrawerProfile({ ...defaultRetrievalProfile(tool), ...current })
    setRetrievalGeneratedProfile(null)
    setRetrievalDrawerOpen(true)
  }, [draftToolRetrievalProfiles])

  const openEndpointDrawer = useCallback((tool) => {
    setEndpointDrawerTool(tool)
    setEndpointTestResults({})
    setEndpointDrawerOpen(true)
  }, [])

  const testEndpoint = useCallback(async (endpoint) => {
    if (!endpointDrawerTool) return
    const key = `${endpointDrawerTool.name}:${endpoint.method}:${endpoint.path}:${JSON.stringify(endpoint.params || {})}`
    setEndpointTestResults((prev) => ({ ...prev, [key]: { testing: true } }))
    try {
      const loginRes = await axios.post(`${API_BASE_URL}/api/auth/applogin`, {
        username: testUsername,
        password: testPassword,
      })
      const token = loginRes.data?.data?.token
      if (!token) throw new Error('测试用户登录失败，请检查账号密码')
      const res = await axios.get(`${API_BASE_URL}${endpoint.path}`, {
        headers: { Authorization: `Bearer ${token}` },
        params: endpoint.params || {},
        timeout: 15000,
      })
      const body = res.data
      setEndpointTestResults((prev) => ({
        ...prev,
        [key]: {
          testing: false,
          reachable: true,
          ok: body?.code === 200,
          status: res.status,
          code: body?.code,
          msg: body?.msg || '',
          hasData: body?.data != null,
        },
      }))
    } catch (e) {
      const status = e.response?.status
      const body = e.response?.data
      setEndpointTestResults((prev) => ({
        ...prev,
        [key]: {
          testing: false,
          reachable: e.response != null,
          ok: false,
          status,
          code: body?.code,
          msg: body?.msg || body?.message || e.message,
        },
      }))
    }
  }, [testUsername, testPassword, endpointDrawerTool])

  const generateToolRetrievalProfile = useCallback(async (tool) => {
    setRetrievalGenerating(tool.name)
    try {
      const res = await runRagQuery({
        input: [
          '请根据下面的工具注册信息，生成该工具的检索配置。',
          '只输出 JSON，不要输出 Markdown 或解释文字。',
          '字段必须包含：description、keywords、aliases、entities、constraints、negativeCases、examples。',
          `工具名称：${tool.name}`,
          `工具用途：${tool.purpose || ''}`,
          `触发条件：${tool.trigger || ''}`,
          `输出类型：${(tool.outputs || []).join('、')}`,
        ].join('\n'),
        agentName: 'tool_intent_router_agent',
        intent: 'tool_retrieval_profile_generation',
        metadata: {
          testFrom: 'admin_agent_console',
          generationPurpose: 'tool_retrieval_profile',
          toolName: tool.name,
        },
      })
      const profile = parseGeneratedRetrievalProfile(res.data?.answer, tool)
      if (!profile) {
        throw new Error('模型没有返回合法的检索配置 JSON')
      }
      setRetrievalGeneratedProfile(profile)
      setRetrievalDrawerProfile(profile)
      message.success('AI 已生成检索说明，请确认后保存')
    } catch (error) {
      message.error(error.message || 'AI 生成检索说明失败')
    } finally {
      setRetrievalGenerating('')
    }
  }, [])

  const saveQuestionAgentMapping = useCallback(async (type, label) => {
    const agentName = String(draftQuestionAgentMappings[type] || '').trim()
    if (!agentName) {
      message.warning('请先选择题库生成智能体')
      return
    }
    setSavingKey(`question-agent:${type}`)
    try {
      await upsertSystemConfig({
        configKey: `${QUESTION_GENERATION_AGENT_PREFIX}${type}`,
        configValue: agentName,
        configGroup: 'ai',
        description: `${label}题库生成智能体`,
        status: 1,
        isDefault: 0,
      })
      setQuestionAgentMappings((prev) => ({ ...prev, [type]: agentName }))
      message.success(`${label}生成智能体已保存`)
    } catch (error) {
      message.error(error.message || '题型智能体映射保存失败')
    } finally {
      setSavingKey('')
    }
  }, [draftQuestionAgentMappings])

  const configuredAgents = useMemo(() => agents.map((agent) => {
    const enabled = isAgentEnabled(agent)
    const boundModel = agentModelBindings[agent.name] || ''
    return {
      ...agent,
      enabled,
      boundModel,
      modelChanged: (draftBindings[agent.name] || '') !== boundModel,
    }
  }), [agents, agentModelBindings, draftBindings])

  const configuredTools = useMemo(() => tools.map((tool) => ({
    ...tool,
    enabled: isToolEnabled(tool),
  })), [tools])

  const configuredLeaderTools = useMemo(() => leaderTools.map((tool) => ({
    ...tool,
    enabled: tool.configurable === false ? true : isToolEnabled(tool),
  })), [leaderTools])

  const allConfiguredTools = useMemo(() => {
    const map = new Map()
    configuredLeaderTools.forEach((tool) => map.set(tool.name, tool))
    configuredTools.forEach((tool) => map.set(tool.name, tool))
    return Array.from(map.values())
  }, [configuredLeaderTools, configuredTools])

  const selectedToolTest = useMemo(
    () => allConfiguredTools.find((tool) => tool.name === toolTestName) || null,
    [allConfiguredTools, toolTestName]
  )

  const toolTestOptions = useMemo(() => allConfiguredTools
    .filter((tool) => toolTestMode === 'prompt' || isFileContentTool(tool))
    .map((tool) => ({
      value: tool.name,
      label: `${tool.zhName || tool.name} · ${tool.name}`,
      disabled: tool.enabled === false,
    })), [allConfiguredTools, toolTestMode])

  const selectToolForTest = useCallback((name) => {
    const tool = allConfiguredTools.find((item) => item.name === name)
    setToolTestName(name)
    setToolTestInput(getToolTestPrompt(tool))
    setToolTestImages([])
    setToolTestFileList([])
    setToolTestResult(null)
    setToolTestPreview('')
  }, [allConfiguredTools])

  const changeToolTestMode = useCallback((mode) => {
    setToolTestMode(mode)
    setToolTestImages([])
    setToolTestFileList([])
    setToolTestResult(null)
    if (mode === 'manual' && !isFileContentTool(selectedToolTest)) {
      setToolTestName('')
      setToolTestInput('')
    }
  }, [selectedToolTest])

  const beforeToolTestImageUpload = useCallback(async (file) => {
    if (!isToolTestImageFile(file)) {
      message.error('测试只能上传图片文件')
      return Upload.LIST_IGNORE
    }
    if (
      selectedToolTest?.name === 'image_stitching_tool'
      && toolTestImages.length >= TOOL_TEST_STITCH_MAX_IMAGES
    ) {
      message.warning(`图片拼接测试最多上传 ${TOOL_TEST_STITCH_MAX_IMAGES} 张图片`)
      return Upload.LIST_IGNORE
    }
    if (file.size > TOOL_TEST_IMAGE_MAX_BYTES) {
      message.error('单张图片不能超过 10MB')
      return Upload.LIST_IGNORE
    }
    try {
      const dataUrl = await compressToolTestImage(file)
      setToolTestImages((current) => {
        const withoutCurrent = current.filter((item) => item.uid !== file.uid)
        if (selectedToolTest?.name === 'image_stitching_tool'
          && withoutCurrent.length >= TOOL_TEST_STITCH_MAX_IMAGES) {
          return current
        }
        return [
          ...withoutCurrent,
          {
            uid: file.uid,
            name: file.name,
            status: 'done',
            type: file.type || 'image/*',
            url: dataUrl,
            thumbUrl: dataUrl,
          },
        ]
      })
    } catch (error) {
      message.error(error.message || '图片读取失败')
    }
    return Upload.LIST_IGNORE
  }, [selectedToolTest, toolTestImages.length])

  const beforeToolTestFileUpload = useCallback((file) => {
    if (!selectedToolTest || !isFileContentTool(selectedToolTest)) {
      message.error('请先选择文件内容提取工具')
      return Upload.LIST_IGNORE
    }
    if (file.size > TOOL_TEST_FILE_MAX_BYTES) {
      message.error('测试文件不能超过 25MB')
      return Upload.LIST_IGNORE
    }
    const accepted = String(FILE_CONTENT_TOOL_ACCEPT[selectedToolTest.name] || '')
      .split(',')
      .filter(Boolean)
    const lowerName = String(file.name || '').toLowerCase()
    if (!accepted.some((extension) => lowerName.endsWith(extension))) {
      message.error(`请选择 ${accepted.join('、')} 格式的文件`)
      return Upload.LIST_IGNORE
    }
    setToolTestFileList([file])
    setToolTestResult(null)
    return false
  }, [selectedToolTest])

  const runManualToolTest = useCallback(async () => {
    if (!selectedToolTest || !isFileContentTool(selectedToolTest)) {
      message.warning('请选择支持文件上传测试的工具')
      return
    }
    const file = toolTestFileList[0]?.originFileObj || toolTestFileList[0]
    if (!file) {
      message.warning('请先上传测试文件')
      return
    }
    const startedAt = performance.now()
    setToolTestLoading(true)
    setToolTestResult(null)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await testFileContentTool(selectedToolTest.name, formData)
      const durationMs = Math.round(performance.now() - startedAt)
      setToolTestResult({
        status: 'success',
        mode: 'manual',
        durationMs,
        request: { toolName: selectedToolTest.name, fileName: file.name, size: file.size },
        response: res.data,
      })
      message.success('文件解析测试通过')
    } catch (error) {
      setToolTestResult({
        status: 'error',
        mode: 'manual',
        durationMs: Math.round(performance.now() - startedAt),
        message: error.message || '文件解析测试失败',
      })
    } finally {
      setToolTestLoading(false)
    }
  }, [selectedToolTest, toolTestFileList])

  const runToolTest = useCallback(async () => {
    if (!selectedToolTest) {
      message.warning('请先选择工具')
      return
    }
    const isImageStitchingTest = selectedToolTest.name === 'image_stitching_tool'
    if (!isImageStitchingTest && !toolTestInput.trim()) {
      message.warning('请输入测试内容')
      return
    }
    if (selectedToolTest.name === 'recognize_image_tool' && !toolTestImages.length) {
      message.warning('图片识别工具需要先上传测试图片')
      return
    }
    if (selectedToolTest.name === 'image_stitching_tool' && toolTestImages.length < 2) {
      message.warning(`图片拼接工具至少需要上传两张图片，最多 ${TOOL_TEST_STITCH_MAX_IMAGES} 张`)
      return
    }

    const startedAt = performance.now()
    setToolTestLoading(true)
    setToolTestResult(null)
    setToolTestPreview('')
    try {
      const payload = {
        input: isImageStitchingTest ? getToolTestPrompt(selectedToolTest) : toolTestInput.trim(),
        agentName: 'leader_agent',
        intent: 'campus_search',
        metadata: {
          testFrom: 'admin_tool_console',
          expectedToolName: selectedToolTest.name,
          directToolTest: true,
        },
      }
      const testedTextModel = llmModelOptions.find((option) => option.modality === 'text' && option.isDefault)
        || llmModelOptions.find((option) => option.modality === 'text')
      if (testedTextModel) payload.llmModel = testedTextModel.value
      if (toolTestImages.length) payload.imageDataUrls = toolTestImages.map((item) => item.url)
      const res = await runRagQuery(payload)
      const durationMs = Math.round(performance.now() - startedAt)
      const matched = responseContainsTool(res.data, selectedToolTest.name)
      setToolTestResult({
        status: matched ? 'success' : 'mismatch',
        matched,
        durationMs,
        request: payload,
        response: res.data,
      })
      if (matched) message.success(`${selectedToolTest.zhName || selectedToolTest.name}测试通过`)
      else message.warning('请求执行成功，但 trace 中未确认目标工具被调用')
    } catch (error) {
      setToolTestResult({
        status: 'error',
        durationMs: Math.round(performance.now() - startedAt),
        message: error.message || '工具调用失败',
      })
      message.error(error.message || '工具调用失败')
    } finally {
      setToolTestLoading(false)
    }
  }, [selectedToolTest, toolTestImages, toolTestInput, llmModelOptions])

  const fetchToolTestAttachmentBlob = useCallback(async (item) => {
    if (!item?.storageKey || !item?.internalCapability) {
      throw new Error('附件缺少下载凭据，请重新运行测试')
    }
    const fileName = item.fileName || item.name || item.type || 'ai-export'
    const token = localStorage.getItem('token') || ''
    const url = `${API_BASE_URL}/api/ai/rag/export?storageKey=${encodeURIComponent(item.storageKey)}&capability=${encodeURIComponent(item.internalCapability)}&filename=${encodeURIComponent(fileName)}`
    const response = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) {
      let detail = `下载失败(${response.status})`
      try {
        const text = await response.text()
        const parsed = text ? JSON.parse(text) : null
        detail = parsed?.msg || parsed?.message || parsed?.detail || detail
      } catch (parseError) {
        // keep status fallback
      }
      throw new Error(detail)
    }
    return response.blob()
  }, [])

  const previewToolTestAttachment = useCallback(async (item) => {
    if (item?.previewDataUrl) {
      setToolTestPreview(item.previewDataUrl)
      return
    }
    const hideLoading = message.loading('正在加载预览...', 0)
    try {
      const blob = await fetchToolTestAttachmentBlob(item)
      const objectUrl = window.URL.createObjectURL(blob)
      setToolTestPreview((current) => {
        if (current?.startsWith('blob:')) {
          window.URL.revokeObjectURL(current)
        }
        return objectUrl
      })
    } catch (error) {
      message.error(error.message || '预览加载失败')
    } finally {
      hideLoading()
    }
  }, [fetchToolTestAttachmentBlob])

  const downloadToolTestAttachment = useCallback(async (item) => {
    const hideLoading = message.loading('正在准备下载文件...', 0)
    try {
      const blob = await fetchToolTestAttachmentBlob(item)
      const fileName = item.fileName || item.name || item.type || 'ai-export'
      const objectUrl = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 1000)
      hideLoading()
      message.success(`已开始下载 ${fileName}`)
    } catch (error) {
      hideLoading()
      message.error(error.message || '文件下载失败')
    }
  }, [fetchToolTestAttachmentBlob])

  const modelColumns = useMemo(() => [
    {
      title: '智能体',
      dataIndex: 'name',
      width: 280,
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Tag color={record.name === 'leader_agent' ? 'purple' : 'geekblue'}>{value}</Tag>
          <Text type="secondary">{record.role}</Text>
        </Space>
      ),
    },
    {
      title: '开关',
      dataIndex: 'enabled',
      width: 110,
      render: (value, record) => (
        <Switch
          checked={record.name === 'leader_agent' || value !== false}
          disabled={record.name === 'leader_agent'}
          loading={savingKey === `enabled:${record.name}`}
          checkedChildren="开"
          unCheckedChildren="关"
          onChange={(checked) => saveAgentEnabled(record.name, checked)}
        />
      ),
    },
    {
      title: '模型需求',
      dataIndex: 'requiredModelModalities',
      width: 120,
      render: (_, record) => (
        <Space size={[6, 6]} wrap>
          {getAgentRequiredModelModalities(record).map((item) => (
            <Tag color={item === 'text' ? 'blue' : 'gold'} key={item}>
              {MODEL_MODALITY_LABELS[item] || item}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '默认模型',
      dataIndex: 'boundModel',
      width: 380,
      render: (_, record) => {
        const options = getModelOptionsForAgent(record)
        return (
          <Space.Compact className="agent-settings-model">
            <Select
              value={draftBindings[record.name] || undefined}
              options={options}
              placeholder={options.length ? `选择${getAgentModelRequirementText(record)}模型` : '没有已测试通过的可用模型'}
              showSearch
              optionFilterProp="label"
              disabled={!options.length}
              onChange={(value) => setDraftBindings((prev) => ({ ...prev, [record.name]: value }))}
            />
            <Button
              icon={<SaveOutlined />}
              disabled={!options.length || !record.modelChanged}
              loading={savingKey === `model:${record.name}`}
              onClick={() => saveAgentModelBinding(record.name)}
            >
              保存
            </Button>
          </Space.Compact>
        )
      },
    },
  ], [draftBindings, getModelOptionsForAgent, saveAgentEnabled, saveAgentModelBinding, savingKey])

  const toolBindingOptions = useMemo(() => [
    { value: '', label: '暂不绑定', agentName: '' },
    ...agents.map((agent) => ({
      value: agent.name,
      label: agent.role || agent.name,
      agentName: agent.name,
    })),
  ], [agents])

  const toolColumns = useMemo(() => [
    {
      title: '工具',
      dataIndex: 'name',
      width: 280,
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Tag color={record.name === 'generated_export_tools' ? 'purple' : 'cyan'}>{getToolDisplayName(record)}</Tag>
          <Text type="secondary">{getToolCategoryLabel(record.category)}</Text>
        </Space>
      ),
    },
    {
      title: '开关',
      dataIndex: 'enabled',
      width: 100,
      render: (value, record) => (
        <Switch
          checked={value !== false}
          loading={savingKey === `tool:${record.name}`}
          checkedChildren="开"
          unCheckedChildren="关"
          onChange={(checked) => saveToolEnabled(record.name, checked)}
        />
      ),
    },
    {
      title: '输出',
      dataIndex: 'outputs',
      width: 170,
      render: renderOutputs,
    },
    {
      title: '调度类型',
      dataIndex: 'triggerType',
      width: 130,
      render: renderToolTriggerType,
    },
    {
      title: '绑定智能体',
      dataIndex: 'boundAgent',
      width: 330,
      render: (value, record) => {
        const current = draftToolBindings[record.name] !== undefined ? draftToolBindings[record.name] : (value || '')
        const saved = toolBindings[record.name] !== undefined ? toolBindings[record.name] : (value || '')
        const boundAgentEnabled = current ? agents.find((agent) => agent.name === current)?.enabled !== false : null
        return (
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Space.Compact>
              <Select
                value={current}
                options={toolBindingOptions}
                placeholder="选择智能体"
                showSearch
                filterOption={(input, option) => {
                  const keyword = String(input || '').trim().toLowerCase()
                  if (!keyword) return true
                  return String(option?.label || '').toLowerCase().includes(keyword)
                    || String(option?.agentName || '').toLowerCase().includes(keyword)
                }}
                optionRender={(option) => (
                  <div>
                    <div>{option.label}</div>
                    {option.data.agentName ? (
                      <div className="agent-settings-binding-option-name">{option.data.agentName}</div>
                    ) : null}
                  </div>
                )}
                popupMatchSelectWidth={false}
                style={{ width: 200 }}
                onChange={(selected) => setDraftToolBindings((prev) => ({ ...prev, [record.name]: selected || '' }))}
              />
              <Button
                icon={<SaveOutlined />}
                disabled={current === saved}
                loading={savingKey === `tool-binding:${record.name}`}
                onClick={() => saveToolBinding(record.name)}
              >
                保存
              </Button>
            </Space.Compact>
            {current && boundAgentEnabled === false && (
              <Tag color="red">绑定智能体已关闭，工具实际不可用</Tag>
            )}
          </Space>
        )
      },
    },
    {
      title: '触发条件',
      dataIndex: 'trigger',
      width: 320,
      ellipsis: true,
    },
    {
      title: '说明',
      dataIndex: 'purpose',
      ellipsis: true,
    },
    {
      title: '检索说明（可编辑）',
      dataIndex: 'retrievalProfile',
      width: 180,
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Button icon={<SettingOutlined />} onClick={() => openRetrievalDrawer(record)}>
            配置检索说明
          </Button>
          {Array.isArray(record.endpoints) && record.endpoints.length > 0 && (
            <Button icon={<ApiOutlined />} onClick={() => openEndpointDrawer(record)}>
              接口 ({record.endpoints.length})
            </Button>
          )}
        </Space>
      ),
    },
  ], [openRetrievalDrawer, saveToolEnabled, saveToolBinding, savingKey, agents, toolBindingOptions, draftToolBindings, toolBindings])

  const leaderAgentColumns = useMemo(() => [
    {
      title: '开关',
      dataIndex: 'enabled',
      width: 100,
      render: (value, record) => (
        <Switch
          checked={value !== false}
          loading={savingKey === `enabled:${record.name}`}
          checkedChildren="开"
          unCheckedChildren="关"
          onChange={(checked) => saveAgentEnabled(record.name, checked)}
        />
      ),
    },
    {
      title: '智能体',
      dataIndex: 'name',
      width: 280,
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Tag color="geekblue">{value}</Tag>
          <Text className="agent-settings-muted-line" type="secondary" title={record.purpose || record.role}>
            {record.role || record.purpose || '-'}
          </Text>
        </Space>
      ),
    },
    {
      title: '意图',
      dataIndex: 'intent',
      width: 170,
      render: (value) => <Tag>{value || '-'}</Tag>,
    },
    {
      title: '输出',
      dataIndex: 'outputs',
      width: 180,
      render: renderOutputs,
    },
    {
      title: '模型',
      dataIndex: 'boundModel',
      render: (value) => (
        value
          ? <Tag color="blue" title={value}>{getShortModelName(value)}</Tag>
          : <Tag color="orange">未绑定</Tag>
      ),
    },
  ], [saveAgentEnabled, savingKey])

  const leaderToolColumns = useMemo(() => [
    {
      title: '开关',
      dataIndex: 'enabled',
      width: 80,
      render: (value, record) => (
        <Switch
          checked={record.configurable === false || value !== false}
          disabled={record.configurable === false}
          loading={savingKey === `tool:${record.name}`}
          checkedChildren="开"
          unCheckedChildren="关"
          onChange={(checked) => handleToolToggleChange(record, checked)}
        />
      ),
    },
    {
      title: '工具',
      dataIndex: 'name',
      width: 170,
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Tag color={record.category === 'campus_service' ? 'green' : 'cyan'}>{record.zhName || getToolDisplayName(record)}</Tag>
          <Text type="secondary" style={{ fontSize: 12 }}>{record.name}</Text>
        </Space>
      ),
    },
    {
      title: '输出',
      dataIndex: 'outputs',
      width: 130,
      render: renderOutputs,
    },
    {
      title: '调度类型',
      dataIndex: 'triggerType',
      width: 130,
      render: renderToolTriggerType,
    },
    {
      title: '绑定智能体',
      dataIndex: 'boundAgent',
      width: 270,
      render: (value, record) => {
        const current = draftToolBindings[record.name] !== undefined ? draftToolBindings[record.name] : (value || '')
        const saved = toolBindings[record.name] !== undefined ? toolBindings[record.name] : (value || '')
        const boundAgentEnabled = current ? agents.find((agent) => agent.name === current)?.enabled !== false : null
        return (
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Space.Compact>
              <Select
                value={current}
                options={toolBindingOptions}
                placeholder="选择智能体"
                showSearch
                filterOption={(input, option) => {
                  const keyword = String(input || '').trim().toLowerCase()
                  if (!keyword) return true
                  return String(option?.label || '').toLowerCase().includes(keyword)
                    || String(option?.agentName || '').toLowerCase().includes(keyword)
                }}
                optionRender={(option) => (
                  <div>
                    <div>{option.label}</div>
                    {option.data.agentName ? (
                      <div className="agent-settings-binding-option-name">{option.data.agentName}</div>
                    ) : null}
                  </div>
                )}
                popupMatchSelectWidth={false}
                style={{ width: 175 }}
                onChange={(selected) => setDraftToolBindings((prev) => ({ ...prev, [record.name]: selected || '' }))}
              />
              <Button
                icon={<SaveOutlined />}
                disabled={current === saved}
                loading={savingKey === `tool-binding:${record.name}`}
                onClick={() => saveToolBinding(record.name)}
              >
                保存
              </Button>
            </Space.Compact>
            {current && boundAgentEnabled === false && (
              <Tag color="red">绑定智能体已关闭，工具实际不可用</Tag>
            )}
          </Space>
        )
      },
    },
    {
      title: '说明',
      dataIndex: 'purpose',
      width: 170,
      ellipsis: { showTitle: false },
      render: (value, record) => (
        <span title={`${value || ''}${record.trigger ? `\n触发：${record.trigger}` : ''}`}>
          {value || '-'}
        </span>
      ),
    },
    {
      title: '检索说明（可编辑）',
      dataIndex: 'retrievalProfile',
      width: 180,
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Button icon={<SettingOutlined />} onClick={() => openRetrievalDrawer(record)}>
            配置检索说明
          </Button>
          {Array.isArray(record.endpoints) && record.endpoints.length > 0 && (
            <Button icon={<ApiOutlined />} onClick={() => openEndpointDrawer(record)}>
              接口 ({record.endpoints.length})
            </Button>
          )}
        </Space>
      ),
    },
  ], [openRetrievalDrawer, handleToolToggleChange, saveToolBinding, savingKey, agents, toolBindingOptions, draftToolBindings, toolBindings])

  const questionAgentOptions = useMemo(() => agents.map((agent) => ({
    value: agent.name,
    label: agent.role ? `${agent.role}（${agent.name}）` : agent.name,
  })), [agents])

  const questionAgentRows = useMemo(() => QUESTION_TYPE_OPTIONS.map((questionType) => {
    const agentName = draftQuestionAgentMappings[questionType.value] || ''
    const status = resolveQuestionGenerationAgentStatus(agentName, agents, agentModelBindings)
    return {
      ...questionType,
      agentName,
      ...status,
      changed: agentName !== (questionAgentMappings[questionType.value] || ''),
    }
  }), [agentModelBindings, agents, draftQuestionAgentMappings, questionAgentMappings])

  const questionAgentColumns = useMemo(() => [
    {
      title: '题型',
      dataIndex: 'label',
      width: 120,
      render: (label) => <Tag color="blue">{label}</Tag>,
    },
    {
      title: '生成智能体',
      dataIndex: 'agentName',
      render: (agentName, record) => (
        <Select
          className="agent-settings-question-select"
          value={agentName || undefined}
          options={questionAgentOptions}
          placeholder="选择智能体"
          showSearch
          optionFilterProp="label"
          onChange={(value) => setDraftQuestionAgentMappings((prev) => ({ ...prev, [record.value]: value }))}
        />
      ),
    },
    {
      title: '启用状态',
      dataIndex: 'enabled',
      width: 120,
      render: (enabled, record) => record.agentName ? (
        record.exists
          ? <Tag color={enabled === false ? 'red' : 'green'}>{enabled === false ? '已关闭' : '已启用'}</Tag>
          : <Tag color="red">智能体不存在</Tag>
      ) : <Text type="secondary">未映射</Text>,
    },
    {
      title: '模型绑定',
      dataIndex: 'boundModel',
      width: 220,
      render: (boundModel, record) => record.agentName ? (
        record.exists
          ? boundModel ? <Tag color="geekblue" title={boundModel}>{getShortModelName(boundModel)}</Tag> : <Tag color="orange">未绑定</Tag>
          : <Tag color="orange">智能体不存在，绑定无效</Tag>
      ) : <Text type="secondary">-</Text>,
    },
    {
      title: '操作',
      dataIndex: 'value',
      width: 110,
      render: (type, record) => (
        <Button
          icon={<SaveOutlined />}
          disabled={!record.agentName || !record.changed}
          loading={savingKey === `question-agent:${type}`}
          onClick={() => saveQuestionAgentMapping(type, record.label)}
        >
          保存
        </Button>
      ),
    },
  ], [questionAgentOptions, saveQuestionAgentMapping, savingKey])

  const disabledAgentCount = configuredAgents.filter((item) => item.enabled === false).length
  const disabledToolCount = allConfiguredTools.filter((item) => item.enabled === false).length
  const boundCount = configuredAgents.filter((item) => item.boundModel).length
  const unboundAgentCount = configuredAgents.filter((item) => !item.boundModel).length
  const callableToolCount = allConfiguredTools.filter((item) => item.enabled !== false).length
  const campusServiceTools = allConfiguredTools.filter((item) => item.category === 'campus_service')
  const enabledCampusServiceCount = campusServiceTools.filter((item) => item.enabled !== false).length
  const visualTools = allConfiguredTools.filter((item) => item.category === 'visual_generation')
  const contentCategoryTools = allConfiguredTools.filter((item) => item.category === 'content_export')
  const fileContentTools = allConfiguredTools.filter((item) => item.category === 'file_content_extraction')
  const structuredTools = allConfiguredTools.filter((item) => item.category === 'structured_query')
  const systemTriggeredTools = allConfiguredTools.filter((item) => item.triggerType === 'system')
  const leaderTriggeredTools = allConfiguredTools.filter((item) => item.triggerType === 'leader')
  const ruleDirectTools = allConfiguredTools.filter((item) => item.triggerType === 'rule_direct')
  const workflowDependencyTools = allConfiguredTools.filter((item) => item.triggerType === 'workflow_dependency')
  const mappedQuestionAgentCount = questionAgentRows.filter((item) => item.agentName && item.exists).length
  const validQuestionAgentCount = questionAgentRows.filter((item) => (
    item.agentName && item.exists && item.enabled !== false && item.boundModel
  )).length
  const leaderToolSource = leaderObjectType === 'all'
    ? allConfiguredTools
    : leaderObjectType === 'campus'
      ? campusServiceTools
      : leaderObjectType === 'visual'
        ? visualTools
        : leaderObjectType === 'content'
          ? contentCategoryTools
          : leaderObjectType === 'file_content'
            ? fileContentTools
            : structuredTools

  const overviewIssues = [
    unboundAgentCount ? {
      key: 'unbound-model',
      title: `${unboundAgentCount} 个智能体未绑定默认模型`,
      action: '去模型绑定',
      tab: 'models',
    } : null,
    mappedQuestionAgentCount < questionAgentRows.length ? {
      key: 'question-map',
      title: `${questionAgentRows.length - mappedQuestionAgentCount} 个题型未完成生成智能体映射`,
      action: '去题库映射',
      tab: 'questions',
    } : null,
    validQuestionAgentCount < questionAgentRows.length ? {
      key: 'question-valid',
      title: `${questionAgentRows.length - validQuestionAgentCount} 个题型映射当前不可用`,
      action: '去题库映射',
      tab: 'questions',
    } : null,
    disabledToolCount ? {
      key: 'disabled-tool',
      title: `${disabledToolCount} 个 Leader 可调用工具已关闭`,
      action: '去工具开关',
      tab: 'tools',
    } : null,
  ].filter(Boolean)

  const filteredLeaderTools = useMemo(() => {
    const triggerFiltered = toolTriggerType === 'all'
      ? leaderToolSource
      : leaderToolSource.filter((item) => item.triggerType === toolTriggerType)
    if (leaderToolFilter === 'enabled') {
      return triggerFiltered.filter((item) => item.enabled !== false)
    }
    if (leaderToolFilter === 'disabled') {
      return triggerFiltered.filter((item) => item.enabled === false)
    }
    return triggerFiltered
  }, [leaderToolFilter, leaderToolSource, toolTriggerType])

  const filteredConfiguredAgents = useMemo(() => {
    if (runtimeAgentFilter === 'enabled') {
      return configuredAgents.filter((item) => item.enabled !== false)
    }
    if (runtimeAgentFilter === 'disabled') {
      return configuredAgents.filter((item) => item.enabled === false)
    }
    if (runtimeAgentFilter === 'unbound') {
      return configuredAgents.filter((item) => !item.boundModel)
    }
    return configuredAgents
  }, [configuredAgents, runtimeAgentFilter])

  return (
    <div className="agent-settings-page">
      <section className="agent-settings-toolbar">
        <div className="agent-settings-heading">
          <Title level={2}>智能体设置</Title>
          <Text type="secondary">维护 Leader 路由、默认模型、题库映射和工具开关。</Text>
        </div>
        <Space align="center">
          <Text type="secondary">测试用户</Text>
          <Input size="small" value={testUsername} onChange={(e) => setTestUsername(e.target.value)} placeholder="账号" style={{ width: 110 }} />
          <Input.Password size="small" value={testPassword} onChange={(e) => setTestPassword(e.target.value)} placeholder="密码" style={{ width: 130 }} />
          <Button icon={<ReloadOutlined />} onClick={fetchData} loading={loading}>
            刷新状态
          </Button>
        </Space>
      </section>

      <Card className="agent-settings-shell">
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'overview',
              label: '总览',
              children: (
                <div className="agent-settings-overview">
                  <div className="agent-settings-metrics">
                    <div className="agent-settings-metric">
                      <Text type="secondary">Leader 工具</Text>
                      <strong>{callableToolCount}/{allConfiguredTools.length}</strong>
                    </div>
                    <div className="agent-settings-metric">
                      <Text type="secondary">模型绑定</Text>
                      <strong>{boundCount}/{configuredAgents.length}</strong>
                    </div>
                    <div className="agent-settings-metric">
                      <Text type="secondary">题库映射</Text>
                      <strong>{validQuestionAgentCount}/{questionAgentRows.length}</strong>
                    </div>
                    <div className="agent-settings-metric">
                      <Text type="secondary">工具开启</Text>
                      <strong>{callableToolCount}/{configuredLeaderTools.length}</strong>
                    </div>
                  </div>

                  <div className="agent-settings-rule-note">
                    <SettingOutlined />
                    <Text>Leader 固定开启。关闭工具后，识别到对应能力也不会进入后续路由。</Text>
                  </div>

                  <div className="agent-settings-issues">
                    <div className="agent-settings-issues-head">
                      <span>需要处理</span>
                      <Tag color={overviewIssues.length ? 'orange' : 'green'}>
                        {overviewIssues.length ? `${overviewIssues.length} 项` : '当前完整'}
                      </Tag>
                    </div>
                    {overviewIssues.length ? (
                      overviewIssues.map((issue) => (
                        <div className="agent-settings-issue" key={issue.key}>
                          <Space>
                            <ExclamationCircleOutlined />
                            <span>{issue.title}</span>
                          </Space>
                          <Button type="link" onClick={() => setActiveTab(issue.tab)}>
                            {issue.action}
                          </Button>
                        </div>
                      ))
                    ) : (
                      <div className="agent-settings-empty-state">
                        <CheckCircleOutlined />
                        <span>当前配置完整，可以直接使用。</span>
                      </div>
                    )}
                  </div>
                </div>
              ),
            },
            {
              key: 'leader',
              label: '工具开关',
              children: (
                <div className="agent-settings-tab-panel">
                  <div className="agent-settings-table-tools">
                    <Segmented
                      className="agent-settings-segmented"
                      value={leaderObjectType}
                      options={[
                        { label: `全部工具 ${callableToolCount}/${allConfiguredTools.length}`, value: 'all' },
                        { label: `校园服务 ${enabledCampusServiceCount}/${campusServiceTools.length}`, value: 'campus' },
                        { label: `视觉能力 ${visualTools.filter((item) => item.enabled !== false).length}/${visualTools.length}`, value: 'visual' },
                        { label: `内容处理 ${contentCategoryTools.filter((item) => item.enabled !== false).length}/${contentCategoryTools.length}`, value: 'content' },
                        { label: `文件内容识别 ${fileContentTools.filter((item) => item.enabled !== false).length}/${fileContentTools.length}`, value: 'file_content' },
                        { label: `结构化查询 ${structuredTools.filter((item) => item.enabled !== false).length}/${structuredTools.length}`, value: 'structured' },
                      ]}
                      onChange={(value) => {
                        setLeaderObjectType(value)
                        setToolTriggerType('all')
                        setLeaderToolFilter('all')
                      }}
                    />
                    <Segmented
                      className="agent-settings-segmented"
                      value={toolTriggerType}
                      options={[
                        { label: `全部调度 ${allConfiguredTools.length}`, value: 'all' },
                        { label: `系统主动触发 ${systemTriggeredTools.length}`, value: 'system' },
                        { label: `Leader 调用 ${leaderTriggeredTools.length}`, value: 'leader' },
                        { label: `规则直调 ${ruleDirectTools.length}`, value: 'rule_direct' },
                        { label: `工作流依赖 ${workflowDependencyTools.length}`, value: 'workflow_dependency' },
                      ]}
                      onChange={(value) => {
                        setToolTriggerType(value)
                        setLeaderToolFilter('all')
                      }}
                    />
                    <Segmented
                      className="agent-settings-segmented"
                      size="small"
                      value={leaderToolFilter}
                      options={[
                        { label: '全部', value: 'all' },
                        { label: '可调用', value: 'enabled' },
                        { label: '已关闭', value: 'disabled' },
                      ]}
                      onChange={setLeaderToolFilter}
                    />
                  </div>
                  {selectedToolKeys.length > 0 && (
                    <Space style={{ marginBottom: 8 }} size={8}>
                      <Text type="secondary">已选 {selectedToolKeys.length} 项</Text>
                      <Button size="small" onClick={() => bulkSetToolsEnabled(true)}>批量开启</Button>
                      <Button size="small" danger onClick={() => bulkSetToolsEnabled(false)}>批量关闭</Button>
                      <Button size="small" type="text" onClick={() => setSelectedToolKeys([])}>取消选择</Button>
                    </Space>
                  )}
                  <Table
                    className="agent-settings-clean-table"
                    rowKey="name"
                    loading={loading}
                    columns={leaderToolColumns}
                    dataSource={filteredLeaderTools}
                    rowSelection={{
                      selectedRowKeys: selectedToolKeys,
                      onChange: setSelectedToolKeys,
                    }}
                    pagination={{ pageSize: 8 }}
                    size="middle"
                  />
                </div>
              ),
            },
            {
              key: 'tool-test',
              label: '工具测试',
              children: (
                <div className="agent-settings-tool-test">
                  <section className="agent-settings-tool-test-input">
                    <div className="agent-settings-tool-test-heading">
                      <div>
                        <Title level={4}>工具功能测试</Title>
                        <Text type="secondary">
                          {toolTestMode === 'prompt'
                            ? '直连执行所选工具，不经过 Leader 路由；需要模型的工具仍会使用已绑定模型。'
                            : '上传真实文件直接执行所选提取工具，不经过模型路由。'}
                        </Text>
                      </div>
                    </div>

                    <Segmented
                      block
                      value={toolTestMode}
                      options={[
                        { label: '指令测试', value: 'prompt' },
                        { label: '手动测试（上传文件）', value: 'manual' },
                      ]}
                      onChange={changeToolTestMode}
                    />

                    <label className="agent-settings-field-label">测试工具</label>
                    <Select
                      value={toolTestName || undefined}
                      options={toolTestOptions}
                      placeholder={toolTestMode === 'manual' ? '选择文件内容提取工具' : '选择需要测试的工具'}
                      showSearch
                      optionFilterProp="label"
                      onChange={selectToolForTest}
                    />

                    {selectedToolTest ? (
                      <div className="agent-settings-tool-test-context">
                        <Space size={[6, 6]} wrap>
                          <Tag color="cyan">{getToolDisplayName(selectedToolTest)}</Tag>
                          <Tag>{getToolCategoryLabel(selectedToolTest.category)}</Tag>
                          <Tag color={selectedToolTest.enabled === false ? 'red' : 'green'}>
                            {selectedToolTest.enabled === false ? '已关闭' : '已开启'}
                          </Tag>
                          {selectedToolTest.status === 'registered' ? <Tag color="orange">仅注册</Tag> : null}
                        </Space>
                        <Text type="secondary">{selectedToolTest.purpose}</Text>
                        <Text type="secondary">预期输出：{(selectedToolTest.outputs || []).join('、') || '文本结果'}</Text>
                      </div>
                    ) : null}

                    {toolTestMode === 'prompt'
                      && (selectedToolTest?.name === 'recognize_image_tool' || selectedToolTest?.name === 'image_stitching_tool') ? (
                      <>
                        <label className="agent-settings-field-label">测试图片</label>
                        <Upload
                          accept="image/*"
                          listType="picture-card"
                          maxCount={selectedToolTest.name === 'recognize_image_tool' ? 1 : TOOL_TEST_STITCH_MAX_IMAGES}
                          fileList={toolTestImages}
                          beforeUpload={beforeToolTestImageUpload}
                          onRemove={(file) => {
                            setToolTestImages((current) => current.filter((item) => item.uid !== file.uid))
                            return true
                          }}
                        >
                          {selectedToolTest.name !== 'recognize_image_tool' || !toolTestImages.length ? (
                            <div>
                              <PlusOutlined />
                              <div className="agent-settings-tool-test-upload-label">
                                上传图片
                              </div>
                            </div>
                          ) : null}
                        </Upload>
                        <Text type="secondary">
                          {selectedToolTest.name === 'image_stitching_tool'
                            ? `可上传 2-${TOOL_TEST_STITCH_MAX_IMAGES} 张图片，上传时自动压缩（最长边 ${TOOL_TEST_IMAGE_MAX_EDGE}px），单张原图不超过 10MB。`
                            : '图片识别测试必须上传图片，上传时自动压缩，单张原图不超过 10MB。'}
                        </Text>
                      </>
                    ) : null}

                    {toolTestMode === 'prompt' ? (
                      <>
                        {selectedToolTest?.name !== 'image_stitching_tool' ? (
                          <>
                            <label className="agent-settings-field-label">测试输入</label>
                            <Input.TextArea
                              rows={7}
                              value={toolTestInput}
                              disabled={!selectedToolTest}
                              placeholder="选择工具后自动生成对应测试示例"
                              onChange={(event) => setToolTestInput(event.target.value)}
                            />
                          </>
                        ) : null}
                        <Space.Compact block>
                          {selectedToolTest?.name !== 'image_stitching_tool' ? (
                            <Button
                              disabled={!selectedToolTest}
                              onClick={() => setToolTestInput(getToolTestPrompt(selectedToolTest))}
                            >
                              恢复测试示例
                            </Button>
                          ) : null}
                          <Button
                            type="primary"
                            icon={<RobotOutlined />}
                            loading={toolTestLoading}
                            disabled={!selectedToolTest || selectedToolTest.enabled === false}
                            onClick={runToolTest}
                          >
                            运行指令测试
                          </Button>
                        </Space.Compact>
                      </>
                    ) : (
                      <>
                        <label className="agent-settings-field-label">测试文件</label>
                        <Upload.Dragger
                          accept={selectedToolTest ? FILE_CONTENT_TOOL_ACCEPT[selectedToolTest.name] : undefined}
                          maxCount={1}
                          fileList={toolTestFileList}
                          beforeUpload={beforeToolTestFileUpload}
                          onRemove={() => {
                            setToolTestFileList([])
                            setToolTestResult(null)
                            return true
                          }}
                          disabled={!selectedToolTest || selectedToolTest.enabled === false}
                        >
                          <p className="ant-upload-drag-icon"><PlusOutlined /></p>
                          <p className="ant-upload-text">点击或拖拽文件到这里</p>
                          <p className="ant-upload-hint">
                            {selectedToolTest
                              ? `支持 ${FILE_CONTENT_TOOL_ACCEPT[selectedToolTest.name]}，单个文件不超过 25MB`
                              : '请先选择文件内容提取工具'}
                          </p>
                        </Upload.Dragger>
                        <Button
                          type="primary"
                          icon={<RobotOutlined />}
                          loading={toolTestLoading}
                          disabled={!selectedToolTest || selectedToolTest.enabled === false || !toolTestFileList.length}
                          onClick={runManualToolTest}
                        >
                          运行手动测试
                        </Button>
                      </>
                    )}
                  </section>

                  <section className="agent-settings-tool-test-result">
                    <div className="agent-settings-tool-test-heading">
                      <div>
                        <Title level={4}>测试结果</Title>
                        <Text type="secondary">
                          {toolTestMode === 'prompt'
                            ? '只有 trace 或附件明确记录目标工具时才判定为通过。'
                            : '展示文件实际提取出的文本、图片和解析统计。'}
                        </Text>
                      </div>
                      {toolTestResult?.durationMs !== undefined ? <Tag>{toolTestResult.durationMs} ms</Tag> : null}
                    </div>

                    {!toolTestResult ? (
                      <Empty description="选择工具并运行测试后，在这里查看结果" />
                    ) : (
                      <Space direction="vertical" size={14} className="agent-settings-tool-test-result-body">
                        <Alert
                          showIcon
                          type={toolTestResult.status === 'success' ? 'success' : toolTestResult.status === 'mismatch' || toolTestResult.status === 'unavailable' ? 'warning' : 'error'}
                          message={toolTestResult.status === 'success'
                            ? toolTestResult.mode === 'manual' ? '测试通过：文件解析成功' : '测试通过：目标工具已成功调用'
                            : toolTestResult.status === 'mismatch'
                              ? '请求成功，但未在 trace 或附件中确认目标工具'
                              : toolTestResult.status === 'unavailable'
                                ? '该工具暂不可执行测试'
                                : '工具测试失败'}
                          description={toolTestResult.message}
                        />
                        {toolTestResult.response?.answer ? (
                          <div className="agent-settings-tool-test-output">
                            <Text strong>工具输出</Text>
                            <div>{toolTestResult.response.answer}</div>
                          </div>
                        ) : null}
                        {toolTestResult.mode === 'manual' && toolTestResult.response ? (
                          <>
                            <div className="agent-settings-tool-test-output">
                              <Space size={[6, 6]} wrap>
                                <Tag color="blue">模式：{toolTestResult.response.mode}</Tag>
                                <Tag>文本 {toolTestResult.response.textLength || 0} 字</Tag>
                                <Tag>图片 {toolTestResult.response.imageCount || 0} 张</Tag>
                                <Tag>{toolTestResult.response.inputFormat?.toUpperCase()}</Tag>
                              </Space>
                            </div>
                            {toolTestResult.response.text ? (
                              <div className="agent-settings-tool-test-output">
                                <Text strong>提取文本</Text>
                                <div className="agent-settings-tool-test-extracted-text">{toolTestResult.response.text}</div>
                              </div>
                            ) : null}
                            {toolTestResult.response.images?.length ? (
                              <div className="agent-settings-tool-test-output">
                                <Text strong>提取图片</Text>
                                <div className="agent-settings-tool-test-image-grid">
                                  {toolTestResult.response.images.map((item, index) => (
                                    <figure key={`${item.name || 'image'}-${index}`}>
                                      <img src={item.dataUrl} alt={item.name || `提取图片 ${index + 1}`} />
                                      <figcaption>{item.name || `图片 ${index + 1}`}</figcaption>
                                    </figure>
                                  ))}
                                </div>
                              </div>
                            ) : null}
                          </>
                        ) : null}
                        {toolTestResult.response?.attachments?.length ? (
                          <div className="agent-settings-tool-test-output">
                            <Text strong>生成附件</Text>
                            <Space size={[6, 6]} wrap>
                              {toolTestResult.response.attachments.map((item, index) => {
                                const fileName = item.fileName || item.name || item.type || `附件 ${index + 1}`
                                const downloadable = Boolean(item.storageKey && item.internalCapability)
                                const previewable = Boolean(item.previewDataUrl || downloadable)
                                if (previewable && item.type === 'image') {
                                  return (
                                    <Tag
                                      color="blue"
                                      key={`${item.url || fileName}-${index}`}
                                      title="点击查看拼接结果"
                                      style={{ cursor: 'pointer' }}
                                      onClick={() => previewToolTestAttachment(item)}
                                    >
                                      {fileName}
                                    </Tag>
                                  )
                                }
                                return (
                                  <Tag color="blue" key={`${item.url || fileName}-${index}`}>
                                    {downloadable ? (
                                      <a
                                        href="#"
                                        onClick={(event) => {
                                          event.preventDefault()
                                          event.stopPropagation()
                                          downloadToolTestAttachment(item)
                                        }}
                                        className="agent-settings-tool-test-download"
                                        title="点击下载文件"
                                      >
                                        <DownloadOutlined /> {fileName}
                                      </a>
                                    ) : fileName}
                                  </Tag>
                                )
                              })}
                            </Space>
                          </div>
                        ) : null}
                        {toolTestResult.response ? (
                          <details className="agent-settings-tool-test-details">
                            <summary>查看完整响应、trace 和请求参数</summary>
                            <pre>{JSON.stringify({ request: toolTestResult.request, response: toolTestResult.response }, null, 2)}</pre>
                          </details>
                        ) : null}
                      </Space>
                    )}
                    <Modal
                      open={Boolean(toolTestPreview)}
                      title="图片拼接结果"
                      footer={null}
                      width={900}
                      onCancel={() => {
                        setToolTestPreview((current) => {
                          if (current?.startsWith('blob:')) {
                            window.URL.revokeObjectURL(current)
                          }
                          return ''
                        })
                      }}
                    >
                      {toolTestPreview ? (
                        <img
                          src={toolTestPreview}
                          alt="图片拼接结果"
                          style={{ display: 'block', width: '100%', maxHeight: '70vh', objectFit: 'contain' }}
                        />
                      ) : null}
                    </Modal>
                  </section>
                </div>
              ),
            },
            {
              key: 'intent-router',
              label: '意图识别',
              children: (
                <div className="agent-settings-tab-panel">
                  <div className="agent-settings-rule-note">
                    <SettingOutlined />
                    <Text>
                      意图识别是 Leader 路由前的系统必经步骤，不属于 Leader 可选工具，也不计入 Leader 工具数量。
                      它只提取意图、关键词、实体、约束和查询变体；工具索引层再根据各工具的检索说明筛选候选工具。
                    </Text>
                  </div>
                  <Table
                    className="agent-settings-clean-table"
                    rowKey="name"
                    loading={loading}
                    pagination={false}
                    size="middle"
                    dataSource={internalTools}
                    columns={[
                      {
                        title: '内部工具',
                        dataIndex: 'zhName',
                        width: 280,
                        render: (value, record) => (
                          <Space direction="vertical" size={4}>
                            <Tag color="purple">{value || record.name}</Tag>
                            <Text type="secondary">{record.name}</Text>
                          </Space>
                        ),
                      },
                      {
                        title: '状态',
                        width: 150,
                        render: () => <Tag color="green">系统必用 · 强制启用</Tag>,
                      },
                      {
                        title: '调用范围',
                        width: 220,
                        render: () => <Tag color="blue">Leader 路由前自动调用</Tag>,
                      },
                      {
                        title: '输出',
                        dataIndex: 'outputs',
                        width: 260,
                        render: (outputs) => renderOutputs(outputs),
                      },
                      {
                        title: '说明',
                        dataIndex: 'purpose',
                        render: (value, record) => (
                          <Space direction="vertical" size={2}>
                            <Text>{value}</Text>
                            <Text type="secondary">{record.trigger}</Text>
                          </Space>
                        ),
                      },
                    ]}
                  />
                </div>
              ),
            },
            {
              key: 'file-formats',
              label: '文件格式',
              children: (
                <div className="agent-settings-tab-panel">
                  <div className="agent-settings-rule-note">
                    <SettingOutlined />
                    <Text>
                      文件格式注册表是上传校验、AI 链接识别、文件导出和前端展示的统一来源。后续新增或调整格式时维护这张注册表即可。
                    </Text>
                  </div>
                  <Table
                    className="agent-settings-clean-table"
                    rowKey="key"
                    loading={loading}
                    pagination={false}
                    size="middle"
                    dataSource={fileFormats}
                    columns={[
                      { title: '格式', dataIndex: 'name', width: 180 },
                      { title: '扩展名', dataIndex: 'extensions', width: 180, render: (value) => <Space size={[4, 4]} wrap>{(value || []).map((item) => <Tag key={item} color="blue">.{item}</Tag>)}</Space> },
                      { title: '可上传', dataIndex: 'canUpload', width: 100, render: (value) => <Tag color={value ? 'green' : 'default'}>{value ? '是' : '否'}</Tag> },
                      { title: '可识别链接', dataIndex: 'canDetect', width: 120, render: (value) => <Tag color={value ? 'green' : 'default'}>{value ? '是' : '否'}</Tag> },
                      { title: '可导出', dataIndex: 'canExport', width: 100, render: (value) => <Tag color={value ? 'green' : 'default'}>{value ? '是' : '否'}</Tag> },
                      { title: '对应工具', dataIndex: 'tool', width: 220, render: (value) => <Text code>{value || '-'}</Text> },
                      { title: '说明', dataIndex: 'description', width: 320, ellipsis: true },
                    ]}
                  />
                </div>
              ),
            },
            {
              key: 'models',
              label: '模型绑定',
              children: (
                <div className="agent-settings-tab-panel">
                  <div className="agent-settings-table-tools">
                    <Space className="agent-settings-title-tags" size={6} wrap>
                      <Tag color="blue">智能体 {configuredAgents.length}</Tag>
                      <Tag color={boundCount === configuredAgents.length ? 'green' : 'orange'}>已绑定 {boundCount}</Tag>
                      <Tag color={unboundAgentCount ? 'orange' : 'green'}>未绑定 {unboundAgentCount}</Tag>
                      <Tag color={disabledAgentCount ? 'orange' : 'green'}>关闭 {disabledAgentCount}</Tag>
                    </Space>
                    <Segmented
                      className="agent-settings-segmented"
                      size="small"
                      value={runtimeAgentFilter}
                      options={[
                        { label: '全部', value: 'all' },
                        { label: '已开启', value: 'enabled' },
                        { label: '已关闭', value: 'disabled' },
                        { label: '未绑定', value: 'unbound' },
                      ]}
                      onChange={setRuntimeAgentFilter}
                    />
                  </div>
                  {configuredAgents.length ? (
                    <Table
                      className="agent-settings-clean-table"
                      rowKey="name"
                      loading={loading}
                      columns={modelColumns}
                      dataSource={filteredConfiguredAgents}
                      pagination={{ pageSize: 8 }}
                      scroll={{ x: 880 }}
                    />
                  ) : (
                    <Empty description="暂无智能体配置" />
                  )}
                </div>
              ),
            },
            {
              key: 'questions',
              label: '题库映射',
              children: (
                <div className="agent-settings-tab-panel">
                  <div className="agent-settings-section-summary">
                    <Text type="secondary">为每种题型选择生成智能体。每种题型独立保存。</Text>
                    <Space className="agent-settings-title-tags" size={6} wrap>
                      <Tag color="blue">已映射 {mappedQuestionAgentCount}/{questionAgentRows.length}</Tag>
                      <Tag color={validQuestionAgentCount === questionAgentRows.length ? 'green' : 'orange'}>
                        可用 {validQuestionAgentCount}/{questionAgentRows.length}
                      </Tag>
                    </Space>
                  </div>
                  <Table
                    className="agent-settings-clean-table"
                    rowKey="value"
                    loading={loading}
                    columns={questionAgentColumns}
                    dataSource={questionAgentRows}
                    pagination={false}
                    scroll={{ x: 820 }}
                  />
                </div>
              ),
            },
          ]}
        />
      </Card>
      <Drawer
        title={retrievalDrawerTool ? `检索说明：${getToolDisplayName(retrievalDrawerTool)}` : '工具检索说明'}
        width={620}
        open={retrievalDrawerOpen}
        onClose={() => setRetrievalDrawerOpen(false)}
        destroyOnClose={false}
        extra={retrievalDrawerTool ? <Tag color="blue">{retrievalDrawerTool.name}</Tag> : null}
      >
        {retrievalDrawerTool && retrievalDrawerProfile ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Alert
              type="info"
              showIcon
              message="AI 生成只产生候选方案"
              description="当前配置不会被自动替换。请查看下方差异，确认后再保存。"
            />
            <Card size="small" title="当前已保存配置">
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                {retrievalProfileFields.map(([field, label]) => (
                  <div key={field}>
                    <Text strong>{label}</Text>
                    <div style={{ marginTop: 4 }}>
                      {field === 'description'
                        ? <Text>{retrievalDrawerTool.retrievalProfile?.description || retrievalDrawerTool.purpose || '未配置'}</Text>
                        : <ProfileItems profile={profileValues(retrievalDrawerTool.retrievalProfile || defaultRetrievalProfile(retrievalDrawerTool), field)} />}
                    </div>
                  </div>
                ))}
              </Space>
            </Card>

            {retrievalGeneratedProfile ? (
              <Card size="small" title="AI 生成方案" extra={<Tag color="purple">待确认</Tag>}>
                <Space direction="vertical" size={10} style={{ width: '100%' }}>
                  {retrievalProfileFields.map(([field, label]) => (
                    <div key={field}>
                      <Text strong>{label}</Text>
                      <div style={{ marginTop: 4 }}>
                        {field === 'description'
                          ? <Text>{retrievalGeneratedProfile.description || '未配置'}</Text>
                          : <ProfileItems profile={profileValues(retrievalGeneratedProfile, field)} />}
                      </div>
                    </div>
                  ))}
                </Space>
              </Card>
            ) : null}

            {retrievalGeneratedProfile ? (
              <Card size="small" title="前后对比">
                <Space direction="vertical" size={10} style={{ width: '100%' }}>
                  {retrievalProfileFields.filter(([field]) => field !== 'description').map(([field, label]) => {
                    const before = profileValues(retrievalDrawerTool.retrievalProfile || defaultRetrievalProfile(retrievalDrawerTool), field)
                    const after = profileValues(retrievalGeneratedProfile, field)
                    const kept = after.filter((item) => before.includes(item))
                    const added = after.filter((item) => !before.includes(item))
                    const removed = before.filter((item) => !after.includes(item))
                    return (
                      <div key={field}>
                        <Text strong>{label}</Text>
                        <div style={{ marginTop: 4 }}>
                          <Text type="secondary">保留：</Text><ProfileItems profile={kept} />
                          <Text type="secondary">新增：</Text><ProfileItems profile={added} empty="无" />
                          <Text type="secondary">去掉：</Text><ProfileItems profile={removed} empty="无" />
                        </div>
                      </div>
                    )
                  })}
                </Space>
              </Card>
            ) : null}

            <Card size="small" title="编辑并确认">
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                {retrievalProfileFields.map(([field, label]) => (
                  <div key={field}>
                    <Text strong>{label}</Text>
                    {field === 'description' ? (
                      <Input.TextArea
                        rows={2}
                        value={retrievalDrawerProfile.description || ''}
                        onChange={(event) => setRetrievalDrawerProfile((prev) => ({ ...prev, description: event.target.value }))}
                      />
                    ) : (
                      <Input
                        value={profileValues(retrievalDrawerProfile, field).join('、')}
                        placeholder="多个内容用顿号分隔"
                        onChange={(event) => setRetrievalDrawerProfile((prev) => ({
                          ...prev,
                          [field]: event.target.value.split(/[、,，;；]/).map((item) => item.trim()).filter(Boolean),
                        }))}
                      />
                    )}
                  </div>
                ))}
              </Space>
            </Card>

            <Space>
              <Button
                type="primary"
                icon={<RobotOutlined />}
                loading={retrievalGenerating === retrievalDrawerTool.name}
                onClick={() => generateToolRetrievalProfile(retrievalDrawerTool)}
              >
                AI 重新生成
              </Button>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={savingKey === `tool-retrieval:${retrievalDrawerTool.name}`}
                onClick={async () => {
                  const saved = await saveToolRetrievalProfile(retrievalDrawerTool)
                  if (saved) setRetrievalDrawerOpen(false)
                }}
              >
                保存当前配置
              </Button>
            </Space>
          </Space>
        ) : null}
      </Drawer>
      <Drawer
        title={endpointDrawerTool ? `接口清单：${getToolDisplayName(endpointDrawerTool)}` : '接口清单'}
        width={560}
        open={endpointDrawerOpen}
        onClose={() => setEndpointDrawerOpen(false)}
        extra={endpointDrawerTool ? <Tag color="blue">{endpointDrawerTool.name}</Tag> : null}
      >
        {endpointDrawerTool ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Text type="secondary">点「测试」用测试用户（{testUsername}）身份请求该接口，看连通状态。</Text>
            {(Array.isArray(endpointDrawerTool.endpoints) ? endpointDrawerTool.endpoints : []).map((ep) => {
              const key = `${endpointDrawerTool.name}:${ep.method}:${ep.path}:${JSON.stringify(ep.params || {})}`
              const r = endpointTestResults[key]
              return (
                <Card key={key} size="small">
                  <Space direction="vertical" size={6} style={{ width: '100%' }}>
                    <Space size={6}>
                      <Tag color="blue">{ep.method}</Tag>
                      <Text code>{ep.path}</Text>
                    </Space>
                    <Text type="secondary">{ep.description}</Text>
                    <Space size={8}>
                      <Button size="small" type="primary" loading={r?.testing} onClick={() => testEndpoint(ep)}>
                        测试
                      </Button>
                      {r && !r.testing && (
                        !r.reachable
                          ? <Tag color="red">❌ 连不上（{r.msg || r.code || '无响应'}）</Tag>
                          : r.ok
                            ? <Tag color="green">✅ 连通（HTTP {r.status}，code {r.code}）</Tag>
                            : <Tag color="orange">✅ 连通（HTTP {r.status}：{r.msg || r.code}）</Tag>
                      )}
                    </Space>
                  </Space>
                </Card>
              )
            })}
          </Space>
        ) : <Empty description="该工具没有接口清单" />}
      </Drawer>
    </div>
  )
}

export default AgentSettings
