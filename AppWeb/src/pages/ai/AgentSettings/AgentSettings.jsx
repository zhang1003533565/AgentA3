import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Drawer, Empty, Input, Segmented, Select, Space, Switch, Table, Tabs, Tag, Typography, message } from 'antd'
import { CheckCircleOutlined, ExclamationCircleOutlined, ReloadOutlined, RobotOutlined, SaveOutlined, SettingOutlined } from '@ant-design/icons'
import { getRagAgents, runRagQuery } from '../../../api/rag'
import { getSystemConfigList, upsertSystemConfig } from '../../../api/systemConfig'
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
  const [leaderToolFilter, setLeaderToolFilter] = useState('all')
  const [runtimeAgentFilter, setRuntimeAgentFilter] = useState('all')

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
    } catch (error) {
      message.error(error.message || '工具开关保存失败')
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
      render: (value, record) => {
        return (
          <Button icon={<SettingOutlined />} onClick={() => openRetrievalDrawer(record)}>
            配置检索说明
          </Button>
        )
      },
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
      width: 100,
      render: (value, record) => (
        <Switch
          checked={record.configurable === false || value !== false}
          disabled={record.configurable === false}
          loading={savingKey === `tool:${record.name}`}
          checkedChildren="开"
          unCheckedChildren="关"
          onChange={(checked) => saveToolEnabled(record.name, checked)}
        />
      ),
    },
    {
      title: '工具',
      dataIndex: 'name',
      width: 300,
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Tag color={record.category === 'campus_service' ? 'green' : 'cyan'}>{getToolDisplayName(record)}</Tag>
          <Text type="secondary">{getToolCategoryLabel(record.category)}</Text>
        </Space>
      ),
    },
    {
      title: '输出',
      dataIndex: 'outputs',
      width: 190,
      render: renderOutputs,
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
      render: (value, record) => {
        return (
          <Button icon={<SettingOutlined />} onClick={() => openRetrievalDrawer(record)}>
            配置检索说明
          </Button>
        )
      },
    },
  ], [openRetrievalDrawer, saveToolEnabled, saveToolBinding, savingKey, agents, toolBindingOptions, draftToolBindings, toolBindings])

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
  const structuredTools = allConfiguredTools.filter((item) => item.category === 'structured_query')
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
    if (leaderToolFilter === 'enabled') {
      return leaderToolSource.filter((item) => item.enabled !== false)
    }
    if (leaderToolFilter === 'disabled') {
      return leaderToolSource.filter((item) => item.enabled === false)
    }
    return leaderToolSource
  }, [leaderToolFilter, leaderToolSource])

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
        <Button icon={<ReloadOutlined />} onClick={fetchData} loading={loading}>
          刷新状态
        </Button>
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
                        { label: `结构化查询 ${structuredTools.filter((item) => item.enabled !== false).length}/${structuredTools.length}`, value: 'structured' },
                      ]}
                      onChange={(value) => {
                        setLeaderObjectType(value)
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
                  <Table
                    className="agent-settings-clean-table"
                    rowKey="name"
                    loading={loading}
                    columns={leaderToolColumns}
                    dataSource={filteredLeaderTools}
                    pagination={false}
                    size="middle"
                    scroll={{ x: 1080 }}
                  />
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
                      { title: '说明', dataIndex: 'description' },
                    ]}
                    scroll={{ x: 1100 }}
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
    </div>
  )
}

export default AgentSettings
