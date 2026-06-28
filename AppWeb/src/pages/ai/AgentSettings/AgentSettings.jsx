import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, Select, Space, Switch, Table, Tag, Typography, message } from 'antd'
import { ReloadOutlined, SaveOutlined, SettingOutlined } from '@ant-design/icons'
import { getRagAgents } from '../../../api/rag'
import { getSystemConfigList, upsertSystemConfig } from '../../../api/systemConfig'
import {
  AGENT_ENABLED_CONFIG_PREFIX,
  buildAgentModelBindings,
  buildLlmModelOptions,
  getAgentModelRequirementText,
  getAgentRequiredModelModalities,
  isAgentEnabled,
  MODEL_MODALITY_LABELS,
} from '../agentConfig'
import './AgentSettings.css'

const { Text, Title } = Typography

function AgentSettings() {
  const [loading, setLoading] = useState(false)
  const [savingKey, setSavingKey] = useState('')
  const [agents, setAgents] = useState([])
  const [llmModelOptions, setLlmModelOptions] = useState([])
  const [agentModelBindings, setAgentModelBindings] = useState({})
  const [draftBindings, setDraftBindings] = useState({})

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const [agentRes, configRes] = await Promise.all([
        getRagAgents(),
        getSystemConfigList({
          current: 1,
          size: 500,
          prefixes: 'ai.service.,ai.agent-bindings.,ai.agent-enabled.',
        }),
      ])
      const configRows = configRes.data?.records || []
      const nextBindings = buildAgentModelBindings(configRows)
      setAgents(agentRes.data?.agents || [])
      setLlmModelOptions(buildLlmModelOptions(configRows))
      setAgentModelBindings(nextBindings)
      setDraftBindings(nextBindings)
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

  const disabledCount = configuredAgents.filter((item) => item.enabled === false).length
  const boundCount = configuredAgents.filter((item) => item.boundModel).length

  return (
    <div className="agent-settings-page">
      <section className="agent-settings-hero">
        <div>
          <span className="agent-settings-kicker">AGENT SETTINGS</span>
          <Title level={1}>智能体设置</Title>
          <p>集中维护智能体开关和默认模型。测试页只负责试跑；这里的配置会影响 Leader 后续路由与专业智能体调用。</p>
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
          <strong>{disabledCount}</strong>
        </Card>
      </div>

      <Alert
        className="agent-settings-alert"
        type="info"
        showIcon
        message="开关规则"
        description="Leader 固定开启。其他智能体关闭后，Leader 识别到需要调用它时会直接跳过并返回提示，不会绕过后台配置继续执行。"
      />

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
    </div>
  )
}

export default AgentSettings
