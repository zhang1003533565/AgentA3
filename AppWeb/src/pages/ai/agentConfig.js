export const AI_MODEL_CONFIG_PATTERN = /^ai\.service\.(text|image|video|audio)(?:\.([A-Za-z0-9_-]+))?\.(provider|base-url|api-key|model)$/
export const AGENT_MODEL_BINDING_PATTERN = /^ai\.agent-bindings\.([A-Za-z0-9_-]+)\.model$/
export const AGENT_ENABLED_CONFIG_PREFIX = 'ai.agent-enabled.'
export const AI_TESTED_MODEL_PREFIXES_KEY = 'ai_tested_model_prefixes_v1'
export const AI_TESTED_MODEL_IDS_KEY = 'ai_tested_model_ids_v1'

export const MODEL_MODALITY_LABELS = {
  text: '文本',
  image: '图片',
  video: '视频',
  audio: '语音',
  vision: '视觉理解',
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

export const getAgentRequiredModelModalities = (agent) => {
  const modalities = Array.isArray(agent?.requiredModelModalities) ? agent.requiredModelModalities : []
  return modalities.length ? modalities : ['text']
}

export const getAgentModelRequirementText = (agent) => (
  getAgentRequiredModelModalities(agent)
    .map((item) => MODEL_MODALITY_LABELS[item] || item)
    .join(' / ')
)

export const isAgentEnabled = (agent) => !agent || agent.name === 'leader_agent' || agent.enabled !== false
