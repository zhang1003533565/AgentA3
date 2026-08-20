import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button, Card, Empty, Segmented, Select, Space, Switch, Table, Tabs, Tag, Typography, message } from 'antd'
import { CheckCircleOutlined, ExclamationCircleOutlined, ReloadOutlined, SaveOutlined, SettingOutlined } from '@ant-design/icons'
import { getRagAgents } from '../../../api/rag'
import { getSystemConfigList, upsertSystemConfig } from '../../../api/systemConfig'
import {
  AGENT_ENABLED_CONFIG_PREFIX,
  QUESTION_GENERATION_AGENT_PREFIX,
  QUESTION_TYPE_OPTIONS,
  TOOL_BOUND_CONFIG_PREFIX,
  TOOL_BOUND_UNBOUND_MARKER,
  TOOL_ENABLED_CONFIG_PREFIX,
  buildAgentModelBindings,
  buildQuestionGenerationAgentMappings,
  buildToolBindings,
  buildToolToggles,
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

function AgentSettings() {
  const [loading, setLoading] = useState(false)
  const [savingKey, setSavingKey] = useState('')
  const [agents, setAgents] = useState([])
  const [leaderTools, setLeaderTools] = useState([])
  const [tools, setTools] = useState([])
  const [llmModelOptions, setLlmModelOptions] = useState([])
  const [agentModelBindings, setAgentModelBindings] = useState({})
  const [draftBindings, setDraftBindings] = useState({})
  const [questionAgentMappings, setQuestionAgentMappings] = useState({})
  const [draftQuestionAgentMappings, setDraftQuestionAgentMappings] = useState({})
  const [toolBindings, setToolBindings] = useState({})
  const [draftToolBindings, setDraftToolBindings] = useState({})
  const [activeTab, setActiveTab] = useState('overview')
  const [leaderObjectType, setLeaderObjectType] = useState('agents')
  const [leaderAgentFilter, setLeaderAgentFilter] = useState('all')
  const [leaderToolFilter, setLeaderToolFilter] = useState('all')
  const [runtimeAgentFilter, setRuntimeAgentFilter] = useState('all')
  const [contentToolFilter, setContentToolFilter] = useState('all')

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const [agentRes, configRes] = await Promise.all([
        getRagAgents(),
        getSystemConfigList({
          current: 1,
          size: 500,
          prefixes: 'ai.service.,ai.agent-bindings.,ai.agent-enabled.,ai.tool-enabled.,ai.tool-bound.,ai.question-generation.agent.',
        }),
      ])
      const configRows = configRes.data?.records || []
      const nextBindings = buildAgentModelBindings(configRows)
      const nextToolToggles = buildToolToggles(configRows)
      const nextToolBindings = buildToolBindings(configRows)
      const nextQuestionAgentMappings = buildQuestionGenerationAgentMappings(configRows)
      setAgents(agentRes.data?.agents || [])
      setTools((agentRes.data?.generatedTools || []).map((tool) => {
        const hasConfiguredValue = Object.prototype.hasOwnProperty.call(nextToolToggles, tool.name)
        const hasBoundConfig = Object.prototype.hasOwnProperty.call(nextToolBindings, tool.name)
        return {
          ...tool,
          enabled: hasConfiguredValue ? nextToolToggles[tool.name] : tool.enabled !== false,
          boundAgent: hasBoundConfig ? nextToolBindings[tool.name] : (tool.boundAgent || ''),
        }
      }))
      setLeaderTools((agentRes.data?.leaderTools || []).map((tool) => {
        const hasConfiguredValue = Object.prototype.hasOwnProperty.call(nextToolToggles, tool.name)
        const hasBoundConfig = Object.prototype.hasOwnProperty.call(nextToolBindings, tool.name)
        return {
          ...tool,
          enabled: tool.configurable === false ? true : hasConfiguredValue ? nextToolToggles[tool.name] : tool.enabled !== false,
          boundAgent: hasBoundConfig ? nextToolBindings[tool.name] : (tool.boundAgent || ''),
        }
      }))
      setLlmModelOptions(buildLlmModelOptions(configRows))
      setAgentModelBindings(nextBindings)
      setDraftBindings(nextBindings)
      setQuestionAgentMappings(nextQuestionAgentMappings)
      setDraftQuestionAgentMappings(nextQuestionAgentMappings)
      setToolBindings(nextToolBindings)
      setDraftToolBindings(nextToolBindings)
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

  const leaderCallableAgents = useMemo(() => (
    configuredAgents.filter((agent) => agent.name !== 'leader_agent')
  ), [configuredAgents])

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
  ], [saveToolEnabled, saveToolBinding, savingKey, agents, toolBindingOptions, draftToolBindings, toolBindings])

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
  ], [saveToolEnabled, saveToolBinding, savingKey, agents, toolBindingOptions, draftToolBindings, toolBindings])

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
  const disabledLeaderAgentCount = leaderCallableAgents.filter((item) => item.enabled === false).length
  const disabledToolCount = allConfiguredTools.filter((item) => item.enabled === false).length
  const boundCount = configuredAgents.filter((item) => item.boundModel).length
  const unboundAgentCount = configuredAgents.filter((item) => !item.boundModel).length
  const callableAgentCount = leaderCallableAgents.filter((item) => item.enabled !== false).length
  const callableToolCount = configuredLeaderTools.filter((item) => item.enabled !== false).length
  const campusServiceTools = configuredLeaderTools.filter((item) => item.category === 'campus_service')
  const enabledCampusServiceCount = campusServiceTools.filter((item) => item.enabled !== false).length
  const leaderInterfaceTools = configuredLeaderTools.filter((item) => item.category !== 'campus_service')
  const enabledLeaderInterfaceToolCount = leaderInterfaceTools.filter((item) => item.enabled !== false).length
  const mappedQuestionAgentCount = questionAgentRows.filter((item) => item.agentName && item.exists).length
  const validQuestionAgentCount = questionAgentRows.filter((item) => (
    item.agentName && item.exists && item.enabled !== false && item.boundModel
  )).length
  const enabledContentToolCount = configuredTools.filter((item) => item.enabled !== false).length
  const disabledContentToolCount = configuredTools.filter((item) => item.enabled === false).length
  const leaderToolSource = leaderObjectType === 'campus' ? campusServiceTools : leaderInterfaceTools

  const overviewIssues = [
    unboundAgentCount ? {
      key: 'unbound-model',
      title: `${unboundAgentCount} 个智能体未绑定默认模型`,
      action: '去模型绑定',
      tab: 'models',
    } : null,
    disabledLeaderAgentCount ? {
      key: 'disabled-agent',
      title: `${disabledLeaderAgentCount} 个 Leader 专业智能体已关闭`,
      action: '去 Leader 调用',
      tab: 'leader',
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

  const filteredLeaderCallableAgents = useMemo(() => {
    if (leaderAgentFilter === 'enabled') {
      return leaderCallableAgents.filter((item) => item.enabled !== false)
    }
    if (leaderAgentFilter === 'disabled') {
      return leaderCallableAgents.filter((item) => item.enabled === false)
    }
    if (leaderAgentFilter === 'unbound') {
      return leaderCallableAgents.filter((item) => !item.boundModel)
    }
    return leaderCallableAgents
  }, [leaderAgentFilter, leaderCallableAgents])

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

  const filteredContentTools = useMemo(() => {
    if (contentToolFilter === 'enabled') {
      return configuredTools.filter((item) => item.enabled !== false)
    }
    if (contentToolFilter === 'disabled') {
      return configuredTools.filter((item) => item.enabled === false)
    }
    return configuredTools
  }, [configuredTools, contentToolFilter])

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
                      <Text type="secondary">可调用智能体</Text>
                      <strong>{callableAgentCount}/{leaderCallableAgents.length}</strong>
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
                    <Text>Leader 固定开启。关闭智能体或工具后，识别到对应意图也不会进入后续路由。</Text>
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
              label: 'Leader 调用',
              children: (
                <div className="agent-settings-tab-panel">
                  <div className="agent-settings-table-tools">
                    <Segmented
                      className="agent-settings-segmented"
                      value={leaderObjectType}
                      options={[
                        { label: `专业智能体 ${callableAgentCount}/${leaderCallableAgents.length}`, value: 'agents' },
                        { label: `系统能力 ${enabledCampusServiceCount}/${campusServiceTools.length}`, value: 'campus' },
                        { label: `接口工具 ${enabledLeaderInterfaceToolCount}/${leaderInterfaceTools.length}`, value: 'tools' },
                      ]}
                      onChange={(value) => {
                        setLeaderObjectType(value)
                        setLeaderAgentFilter('all')
                        setLeaderToolFilter('all')
                      }}
                    />
                    <Segmented
                      className="agent-settings-segmented"
                      size="small"
                      value={leaderObjectType === 'agents' ? leaderAgentFilter : leaderToolFilter}
                      options={leaderObjectType === 'agents' ? [
                        { label: '全部', value: 'all' },
                        { label: '可调用', value: 'enabled' },
                        { label: '已关闭', value: 'disabled' },
                        { label: '未绑定', value: 'unbound' },
                      ] : [
                        { label: '全部', value: 'all' },
                        { label: '可调用', value: 'enabled' },
                        { label: '已关闭', value: 'disabled' },
                      ]}
                      onChange={leaderObjectType === 'agents' ? setLeaderAgentFilter : setLeaderToolFilter}
                    />
                  </div>
                  <Table
                    className="agent-settings-clean-table"
                    rowKey="name"
                    loading={loading}
                    columns={leaderObjectType === 'agents' ? leaderAgentColumns : leaderToolColumns}
                    dataSource={leaderObjectType === 'agents' ? filteredLeaderCallableAgents : filteredLeaderTools}
                    pagination={leaderObjectType === 'agents' ? { pageSize: 8 } : false}
                    size="middle"
                    scroll={{ x: 1080 }}
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
            {
              key: 'tools',
              label: '工具开关',
              children: (
                <div className="agent-settings-tab-panel">
                  <div className="agent-settings-table-tools">
                    <Space className="agent-settings-title-tags" size={6} wrap>
                      <Tag color="blue">工具 {configuredTools.length}</Tag>
                      <Tag color={enabledContentToolCount === configuredTools.length ? 'green' : 'orange'}>开启 {enabledContentToolCount}</Tag>
                      <Tag color={disabledContentToolCount ? 'orange' : 'green'}>关闭 {disabledContentToolCount}</Tag>
                    </Space>
                    <Segmented
                      className="agent-settings-segmented"
                      size="small"
                      value={contentToolFilter}
                      options={[
                        { label: '全部', value: 'all' },
                        { label: '已开启', value: 'enabled' },
                        { label: '已关闭', value: 'disabled' },
                      ]}
                      onChange={setContentToolFilter}
                    />
                  </div>
                  {configuredTools.length ? (
                    <Table
                      className="agent-settings-clean-table"
                      rowKey="name"
                      loading={loading}
                      columns={toolColumns}
                      dataSource={filteredContentTools}
                      pagination={false}
                      scroll={{ x: 1080 }}
                    />
                  ) : (
                    <Empty description="暂无工具配置" />
                  )}
                </div>
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}

export default AgentSettings
