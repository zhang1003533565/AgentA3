export const AI_MODEL_CONFIG_PATTERN = /^ai\.service\.(text|vision|image|video|audio)(?:\.([A-Za-z0-9_-]+))?\.(provider|base-url|api-key|model)$/
export const AGENT_MODEL_BINDING_PATTERN = /^ai\.agent-bindings\.([A-Za-z0-9_-]+)\.model$/
/**
 * 智能体子用途模型绑定：ai.agent-bindings.{agent}.{purpose}-model
 * 例如 meeting_summary_agent.minutes-model 只作用于"会后会议纪要"，
 * 与通用 .model（会中实时总结等默认链路）相互独立，互不影响。
 */
export const AGENT_SUB_MODEL_BINDING_PATTERN = /^ai\.agent-bindings\.([A-Za-z0-9_-]+)\.([a-z]+-model)$/
export const AGENT_SUB_MODEL_BINDING_LABELS = {
  'minutes-model': '会后会议纪要模型',
}
export const AGENT_SUB_MODEL_BINDING_HINTS = {
  'minutes-model': '仅作用于会后纪要（Agent 2），不会修改默认模型（会中实时总结）',
}
/**
 * 声明各智能体暴露哪些子用途模型绑定，与"是否已配置"无关。
 * 这样管理员取消专属绑定（配置行被删除）后，后台仍显示该行并标注"跟随系统默认"，
 * 而不是整行消失导致无法重新绑定。
 */
export const AGENT_SUB_MODEL_BINDINGS = {
  meeting_summary_agent: ['minutes-model'],
}
export const AGENT_SUB_MODEL_UNBOUND_TEXT = '跟随系统默认'
export const AGENT_ENABLED_CONFIG_PREFIX = 'ai.agent-enabled.'
export const TOOL_ENABLED_CONFIG_PREFIX = 'ai.tool-enabled.'
export const TOOL_BOUND_CONFIG_PREFIX = 'ai.tool-bound.'
export const TOOL_RETRIEVAL_CONFIG_PREFIX = 'ai.tool-retrieval.'
export const TOOL_BOUND_UNBOUND_MARKER = '-'
export const QUESTION_GENERATION_AGENT_PREFIX = 'ai.question-generation.agent.'
export const AI_TESTED_MODEL_PREFIXES_KEY = 'ai_tested_model_prefixes_v1'
export const AI_TESTED_MODEL_IDS_KEY = 'ai_tested_model_ids_v1'

export const MODEL_MODALITY_LABELS = {
  text: '文本',
  image: '图片',
  video: '视频',
  audio: '语音',
  vision: '视觉理解',
}

export const QUESTION_TYPE_OPTIONS = [
  { value: 'single_choice', label: '单选题' },
  { value: 'multiple_choice', label: '多选题' },
  { value: 'true_false', label: '判断题' },
  { value: 'fill_blank', label: '填空题' },
  { value: 'short_answer', label: '简答题' },
  { value: 'calculation', label: '计算题' },
  { value: 'programming', label: '编程题' },
]

export const buildQuestionGenerationAgentMappings = (configRows = []) => {
  const mappings = {}
  configRows.forEach((item) => {
    const key = String(item.configKey || '')
    if (!key.startsWith(QUESTION_GENERATION_AGENT_PREFIX) || Number(item.status) === 0) return
    const type = key.slice(QUESTION_GENERATION_AGENT_PREFIX.length).trim()
    const agentName = String(item.configValue || '').trim()
    if (type && agentName) {
      mappings[type] = agentName
    }
  })
  return mappings
}

export const resolveQuestionGenerationAgentStatus = (agentName, agents = [], bindings = {}) => {
  const agent = agents.find((item) => item.name === agentName)
  if (!agent) {
    return { exists: false, enabled: null, boundModel: '' }
  }
  return {
    exists: true,
    enabled: isAgentEnabled(agent),
    boundModel: agentName ? bindings[agentName] || '' : '',
  }
}

export const getTestedModelPrefixes = () => {
  try {
    const raw = localStorage.getItem(AI_TESTED_MODEL_PREFIXES_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return new Set(Object.keys(parsed || {}))
  } catch {
    return new Set()
  }
}

export const getTestedModelIds = () => {
  try {
    const raw = localStorage.getItem(AI_TESTED_MODEL_IDS_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return new Set(Object.keys(parsed || {}))
  } catch {
    return new Set()
  }
}

export const buildLlmModelOptions = (configRows = []) => {
  const groups = new Map()
  configRows.forEach((item) => {
    const match = String(item.configKey || '').match(AI_MODEL_CONFIG_PATTERN)
    if (!match) return
    const [, modality, configName = 'default', field] = match
    const configPrefix = configName === 'default' ? `ai.service.${modality}` : `ai.service.${modality}.${configName}`
    const group = groups.get(configPrefix) || { configPrefix, modality, configs: {} }
    group.configs[field] = item
    groups.set(configPrefix, group)
  })

  const testedPrefixes = getTestedModelPrefixes()
  const testedModelIds = getTestedModelIds()
  return Array.from(groups.values())
    .filter((group) => ['provider', 'base-url', 'api-key', 'model'].every((field) => {
      const config = group.configs[field]
      return config && String(config.configValue || '').trim()
    }))
    .filter((group) => testedPrefixes.has(group.configPrefix) || testedModelIds.has(String(group.configs.model?.configValue || '').trim()))
    .map((group) => {
      const modalityLabel = MODEL_MODALITY_LABELS[group.modality] || group.modality
      const isDefault = Object.values(group.configs).some((config) => Number(config?.isDefault) === 1)
      return {
        value: group.configPrefix,
        label: `[${modalityLabel}] ${group.configs.model.configValue}`,
        modality: group.modality,
        isDefault,
      }
    })
}

export const buildAgentModelBindings = (configRows = []) => {
  const bindings = {}
  configRows.forEach((item) => {
    const match = String(item.configKey || '').match(AGENT_MODEL_BINDING_PATTERN)
    if (!match) return
    const model = String(item.configValue || '').trim()
    if (model) {
      bindings[match[1]] = model
    }
  })
  return bindings
}

/**
 * 解析智能体子用途模型绑定，返回 { [agentName]: { [purposeModel]: configPrefix } }。
 * 例如 meeting_summary_agent 的 minutes-model 指向 ai.service.text.qwen3-8-max。
 */
export const buildAgentSubModelBindings = (configRows = []) => {
  const bindings = {}
  configRows.forEach((item) => {
    const match = String(item.configKey || '').match(AGENT_SUB_MODEL_BINDING_PATTERN)
    if (!match) return
    if (Number(item.status) === 0) return
    const [, agentName, purposeModel] = match
    const configPrefix = String(item.configValue || '').trim()
    if (!configPrefix) return
    bindings[agentName] = { ...(bindings[agentName] || {}), [purposeModel]: configPrefix }
  })
  return bindings
}

/**
 * 解析子用途模型绑定对应的系统配置行 id：{ [agentName]: { [purposeModel]: id } }。
 * 「取消专属绑定」需要按 id 调用现有 DELETE /api/system-config/{id} 真删除该行，
 * 而不是写入空值（空值会让后端 upsert 出一条语义不明的配置）。
 */
export const buildAgentSubModelBindingIds = (configRows = []) => {
  const ids = {}
  configRows.forEach((item) => {
    const match = String(item.configKey || '').match(AGENT_SUB_MODEL_BINDING_PATTERN)
    if (!match) return
    const [, agentName, purposeModel] = match
    const id = item.id ?? item.configId
    if (id === undefined || id === null) return
    ids[agentName] = { ...(ids[agentName] || {}), [purposeModel]: id }
  })
  return ids
}

/**
 * 解析「模型配置前缀 → 模型 ID」映射，用于把绑定的配置前缀还原成可读模型名。
 * 前端不硬编码任何模型名，一律取后端真实配置值。
 */
export const buildAiModelLabelByPrefix = (configRows = []) => {
  const labels = {}
  configRows.forEach((item) => {
    const match = String(item.configKey || '').match(AI_MODEL_CONFIG_PATTERN)
    if (!match) return
    const [, modality, configName, field] = match
    if (field !== 'model') return
    const value = String(item.configValue || '').trim()
    if (!value) return
    labels[configName === undefined || !configName
      ? `ai.service.${modality}`
      : `ai.service.${modality}.${configName}`] = value
  })
  return labels
}

const parseEnabledConfigValue = (value) => {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  const normalized = String(value ?? '').trim().toLowerCase()
  return !['0', 'false', 'off', 'disabled', 'no'].includes(normalized)
}

export const buildToolToggles = (configRows = []) => {
  const toggles = {}
  configRows.forEach((item) => {
    const key = String(item.configKey || '')
    if (!key.startsWith(TOOL_ENABLED_CONFIG_PREFIX)) return
    const toolName = key.slice(TOOL_ENABLED_CONFIG_PREFIX.length).trim()
    if (toolName) {
      toggles[toolName] = parseEnabledConfigValue(item.configValue)
    }
  })
  return toggles
}

export const buildToolBindings = (configRows = []) => {
  const bindings = {}
  configRows.forEach((item) => {
    const key = String(item.configKey || '')
    if (!key.startsWith(TOOL_BOUND_CONFIG_PREFIX) || Number(item.status) === 0) return
    const toolName = key.slice(TOOL_BOUND_CONFIG_PREFIX.length).trim()
    if (!toolName) return
    const value = String(item.configValue ?? '').trim()
    bindings[toolName] = value === TOOL_BOUND_UNBOUND_MARKER ? '' : value
  })
  return bindings
}

export const buildToolRetrievalProfiles = (configRows = []) => {
  const profiles = {}
  configRows.forEach((item) => {
    const key = String(item.configKey || '')
    if (!key.startsWith(TOOL_RETRIEVAL_CONFIG_PREFIX) || Number(item.status) === 0) return
    const toolName = key.slice(TOOL_RETRIEVAL_CONFIG_PREFIX.length).trim()
    if (!toolName) return
    try {
      const parsed = JSON.parse(String(item.configValue || '{}'))
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) profiles[toolName] = parsed
    } catch {
      // Ignore malformed legacy values; the UI will show the generated defaults.
    }
  })
  return profiles
}

export const getAgentRequiredModelModalities = (agent) => {
  const modalities = Array.isArray(agent?.requiredModelModalities) ? agent.requiredModelModalities : []
  return modalities.length ? modalities : ['text']
}

export const getAgentModelRequirementText = (agent) => (
  getAgentRequiredModelModalities(agent)
    .map((item) => MODEL_MODALITY_LABELS[item] || item)
    .join(' / ')
)

export const isAgentEnabled = (agent) => (
  !agent || agent.name === 'leader_agent' || agent.internalOnly || agent.enabled !== false
)

export const isToolEnabled = (tool) => !tool || tool.enabled !== false
