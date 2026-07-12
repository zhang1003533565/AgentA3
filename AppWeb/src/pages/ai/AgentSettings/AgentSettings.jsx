import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, Select, Space, Switch, Table, Tag, Typography, message } from 'antd'
import { ReloadOutlined, SaveOutlined, SettingOutlined, ToolOutlined } from '@ant-design/icons'
import { getRagAgents } from '../../../api/rag'
import { getSystemConfigList, upsertSystemConfig } from '../../../api/systemConfig'
import {
  AGENT_ENABLED_CONFIG_PREFIX,
  QUESTION_GENERATION_AGENT_PREFIX,
  QUESTION_TYPE_OPTIONS,
  TOOL_ENABLED_CONFIG_PREFIX,
  buildAgentModelBindings,
  buildQuestionGenerationAgentMappings,
  buildToolToggles,
  buildLlmModelOptions,
  getAgentModelRequirementText,
  getAgentRequiredModelModalities,
  isAgentEnabled,
  isToolEnabled,
  MODEL_MODALITY_LABELS,
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
  }
  return labels[category] || category || '-'
}

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

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const [agentRes, configRes] = await Promise.all([
        getRagAgents(),
        getSystemConfigList({
          current: 1,
          size: 500,
          prefixes: 'ai.service.,ai.agent-bindings.,ai.agent-enabled.,ai.tool-enabled.,ai.question-generation.agent.',
        }),
      ])
      const configRows = configRes.data?.records || []
      const nextBindings = buildAgentModelBindings(configRows)
      const nextToolToggles = buildToolToggles(configRows)
      const nextQuestionAgentMappings = buildQuestionGenerationAgentMappings(configRows)
      setAgents(agentRes.data?.agents || [])
      setTools((agentRes.data?.generatedTools || []).map((tool) => {
        const hasConfiguredValue = Object.prototype.hasOwnProperty.call(nextToolToggles, tool.name)
        return {
          ...tool,
          enabled: hasConfiguredValue ? nextToolToggles[tool.name] : tool.enabled !== false,
        }
      }))
      setLeaderTools((agentRes.data?.leaderTools || []).map((tool) => {
        const hasConfiguredValue = Object.prototype.hasOwnProperty.call(nextToolToggles, tool.name)
        return {
          ...tool,
          enabled: tool.configurable === false ? true : hasConfiguredValue ? nextToolToggles[tool.name] : tool.enabled !== false,
        }
      }))
      setLlmModelOptions(buildLlmModelOptions(configRows))
      setAgentModelBindings(nextBindings)
      setDraftBindings(nextBindings)
      setQuestionAgentMappings(nextQuestionAgentMappings)
      setDraftQuestionAgentMappings(nextQuestionAgentMappings)
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

  const columns = useMemo(() => [
    {
      title: '智能体',
      dataIndex: 'name',
      width: 260,
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
      width: 120,
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
      width: 170,
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
    {
      title: '职责',
      dataIndex: 'purpose',
      ellipsis: true,
    },
  ], [draftBindings, getModelOptionsForAgent, saveAgentEnabled, saveAgentModelBinding, savingKey])

  const toolColumns = useMemo(() => [
    {
      title: '工具',
      dataIndex: 'name',
      width: 260,
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
      width: 120,
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
      width: 180,
      render: (outputs) => (
        <Space size={[6, 6]} wrap>
          {(Array.isArray(outputs) ? outputs : []).map((item) => (
            <Tag color="blue" key={item}>{String(item).toUpperCase()}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '触发条件',
      dataIndex: 'trigger',
      ellipsis: true,
    },
    {
      title: '作用',
      dataIndex: 'purpose',
      ellipsis: true,
    },
  ], [saveToolEnabled, savingKey])

  const leaderAgentColumns = useMemo(() => [
    {
      title: '智能体',
      dataIndex: 'name',
      width: 230,
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Space size={6} wrap>
            <Tag color={record.enabled === false ? 'red' : 'green'}>
              {record.enabled === false ? '关闭' : '可调用'}
            </Tag>
            <Tag color="geekblue">{value}</Tag>
          </Space>
          <Text type="secondary">{record.role}</Text>
        </Space>
      ),
    },
    {
      title: '意图',
      dataIndex: 'intent',
      width: 160,
      render: (value) => <Tag>{value || '-'}</Tag>,
    },
    {
      title: '输出',
      dataIndex: 'outputs',
      width: 180,
      render: (outputs) => (
        <Space size={[6, 6]} wrap>
          {(Array.isArray(outputs) ? outputs : []).map((item) => (
            <Tag color="blue" key={item}>{item}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '职责',
      dataIndex: 'purpose',
      ellipsis: true,
    },
  ], [])

  const leaderToolColumns = useMemo(() => [
    {
      title: '工具',
      dataIndex: 'name',
      width: 220,
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Space size={6} wrap>
            <Tag color={record.enabled === false ? 'red' : 'green'}>
              {record.enabled === false ? '关闭' : '可调用'}
            </Tag>
            <Tag color={record.category === 'campus_service' ? 'green' : 'cyan'}>{getToolDisplayName(record)}</Tag>
          </Space>
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
      title: '输出',
      dataIndex: 'outputs',
      width: 150,
      render: (outputs) => (
        <Space size={[6, 6]} wrap>
          {(Array.isArray(outputs) ? outputs : []).map((item) => (
            <Tag color="blue" key={item}>{String(item).toUpperCase()}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '触发条件',
      dataIndex: 'trigger',
      ellipsis: true,
    },
  ], [saveToolEnabled, savingKey])

  const questionAgentOptions = useMemo(() => agents.map((agent) => ({
    value: agent.name,
    label: agent.role ? `${agent.role}（${agent.name}）` : agent.name,
  })), [agents])

  const questionAgentRows = useMemo(() => QUESTION_TYPE_OPTIONS.map((questionType) => {
    const agentName = draftQuestionAgentMappings[questionType.value] || ''
    const agent = agents.find((item) => item.name === agentName)
    return {
      ...questionType,
      agentName,
      enabled: agent ? isAgentEnabled(agent) : null,
      boundModel: agentName ? agentModelBindings[agentName] || '' : '',
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
        <Tag color={enabled === false ? 'red' : 'green'}>{enabled === false ? '已关闭' : '已启用'}</Tag>
      ) : <Text type="secondary">未映射</Text>,
    },
    {
      title: '模型绑定',
      dataIndex: 'boundModel',
      width: 220,
      render: (boundModel, record) => record.agentName ? (
        boundModel ? <Tag color="geekblue">{boundModel}</Tag> : <Tag color="orange">未绑定</Tag>
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
  const callableAgentCount = leaderCallableAgents.filter((item) => item.enabled !== false).length
  const callableToolCount = configuredLeaderTools.filter((item) => item.enabled !== false).length
  const campusServiceTools = configuredLeaderTools.filter((item) => item.category === 'campus_service')
  const enabledCampusServiceCount = campusServiceTools.filter((item) => item.enabled !== false).length

  return (
    <div className="agent-settings-page">
      <section className="agent-settings-hero">
        <div>
          <span className="agent-settings-kicker">AGENT SETTINGS</span>
          <Title level={1}>智能体设置</Title>
          <p>集中维护智能体开关、默认模型和内容整理工具。测试页只负责试跑；这里的配置会影响 Leader 后续路由、专业智能体调用和附件生成。</p>
        </div>
        <Button icon={<ReloadOutlined />} onClick={fetchData} loading={loading}>
          刷新状态
        </Button>
      </section>

      <div className="agent-settings-summary">
        <Card>
          <Text type="secondary">智能体数量</Text>
          <strong>{configuredAgents.length}</strong>
        </Card>
        <Card>
          <Text type="secondary">已绑定模型</Text>
          <strong>{boundCount}</strong>
        </Card>
        <Card>
          <Text type="secondary">已关闭</Text>
          <strong>{disabledAgentCount}</strong>
        </Card>
        <Card>
          <Text type="secondary">关闭工具</Text>
          <strong>{disabledToolCount}</strong>
        </Card>
        <Card>
          <Text type="secondary">系统能力</Text>
          <strong>{enabledCampusServiceCount}</strong>
        </Card>
      </div>

      <Alert
        className="agent-settings-alert"
        type="info"
        showIcon
        message="开关规则"
        description="Leader 固定开启。右侧系统能力会直连 Java 后端接口，例如课表、活动、会议、食堂、设施和旧物；关闭后 Leader 识别到对应意图也不会调用。内容整理工具关闭后，自动整理附件只会生成仍开启的格式。"
      />

      <Card
        className="agent-settings-card"
        title={<Space><SettingOutlined />Leader 可调用清单</Space>}
      >
        <div className="agent-settings-callable-head">
          <Text type="secondary">可调用智能体 {callableAgentCount} 个</Text>
          <Text type="secondary">可直接调用工具 {callableToolCount} 个</Text>
          <Text type="secondary">已开启系统能力 {enabledCampusServiceCount}/{campusServiceTools.length} 个</Text>
        </div>
        <div className="agent-settings-leader-grid">
          <div>
            <div className="agent-settings-section-title">专业智能体</div>
            <Table
              rowKey="name"
              loading={loading}
              columns={leaderAgentColumns}
              dataSource={leaderCallableAgents}
              pagination={{ pageSize: 6 }}
              size="small"
              scroll={{ x: 860 }}
            />
          </div>
          <div>
            <div className="agent-settings-section-title">系统能力 / 接口工具</div>
            <Table
              rowKey="name"
              loading={loading}
              columns={leaderToolColumns}
              dataSource={configuredLeaderTools}
              pagination={false}
              size="small"
              scroll={{ x: 760 }}
            />
          </div>
        </div>
      </Card>

      <Card
        className="agent-settings-card"
        title={<Space><SettingOutlined />智能体运行配置</Space>}
      >
        {configuredAgents.length ? (
          <Table
            rowKey="name"
            loading={loading}
            columns={columns}
            dataSource={configuredAgents}
            pagination={{ pageSize: 8 }}
            scroll={{ x: 1040 }}
          />
        ) : (
          <Empty description="暂无智能体配置" />
        )}
      </Card>

      <Card
        className="agent-settings-card"
        title={<Space><SettingOutlined />题库生成智能体映射</Space>}
      >
        <Text className="agent-settings-card-description" type="secondary">
          为每种题型选择生成智能体。选项来自当前智能体清单，每种题型独立保存。
        </Text>
        <Table
          rowKey="value"
          loading={loading}
          columns={questionAgentColumns}
          dataSource={questionAgentRows}
          pagination={false}
          scroll={{ x: 820 }}
        />
      </Card>

      <Card
        className="agent-settings-card"
        title={<Space><ToolOutlined />内容整理工具</Space>}
      >
        {configuredTools.length ? (
          <Table
            rowKey="name"
            loading={loading}
            columns={toolColumns}
            dataSource={configuredTools}
            pagination={false}
            scroll={{ x: 1040 }}
          />
        ) : (
          <Empty description="暂无工具配置" />
        )}
      </Card>
    </div>
  )
}

export default AgentSettings
