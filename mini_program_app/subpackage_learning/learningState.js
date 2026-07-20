const TERMINAL_READY_EVENTS = new Set(['done', 'completed'])
const FAILURE_EVENTS = new Set(['error', 'agent_failed', 'generation_failed'])

function objectValue(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function boundedProgress(value, fallback = 0) {
  const number = Number(value)
  if (!Number.isFinite(number)) return fallback
  return Math.max(0, Math.min(100, number))
}

function resourceMap(value) {
  if (!value) return {}
  if (!Array.isArray(value)) return { ...objectValue(value) }
  const result = {}
  for (const resource of value) {
    const source = objectValue(resource)
    const type = String(
      source.metadata?.resourceType || source.metadata?.resourceKind
      || source.resourceType || source.resourceKind
      || source.payload?.resourceType || source.payload?.resourceKind
      || source.kind
      || ''
    ).trim()
    if (type) result[type] = source
  }
  return result
}

export function createLearningState(workflowId = '') {
  return {
    workflowId: String(workflowId || '').trim(),
    status: 'idle',
    stage: 'idle',
    progress: 0,
    agentName: '',
    resourceType: '',
    message: '',
    resources: {},
    errors: {},
    updatedAt: 0
  }
}

export function reduceLearningEvent(current, eventName, payload = {}) {
  const state = { ...createLearningState(), ...objectValue(current) }
  const event = objectValue(payload)
  const incomingWorkflowId = String(event.workflowId || '').trim()
  if (state.workflowId && incomingWorkflowId && incomingWorkflowId !== state.workflowId) {
    return state
  }

  const name = String(eventName || 'message').trim() || 'message'
  const workflowId = state.workflowId || incomingWorkflowId
  const progress = Math.max(state.progress, boundedProgress(event.progress, state.progress))
  const resourceType = String(event.resourceType || '').trim()
  const resources = { ...objectValue(state.resources), ...resourceMap(event.resources) }
  if (resourceType && event.resource && typeof event.resource === 'object') {
    resources[resourceType] = event.resource
  }
  const errors = { ...objectValue(state.errors), ...objectValue(event.errors) }
  let status = state.status === 'idle' ? 'generating' : state.status

  if (resourceType && ['agent_start', 'retrying'].includes(name)) {
    delete errors[resourceType]
    status = 'generating'
  } else if (FAILURE_EVENTS.has(name)) {
    const key = resourceType || 'workflow'
    errors[key] = {
      message: String(event.message || '资源生成失败'),
      retryable: event.retryable === true
    }
    status = 'generation_failed'
  } else if (name === 'dependency_unavailable') {
    status = 'dependency_unavailable'
  } else if (TERMINAL_READY_EVENTS.has(name)) {
    status = Object.keys(errors).length ? 'generation_failed' : 'ready'
  } else if (!['network_error', 'empty'].includes(status)) {
    status = 'generating'
  }

  return {
    ...state,
    workflowId,
    status,
    stage: name,
    progress: TERMINAL_READY_EVENTS.has(name) ? Math.max(progress, 100) : progress,
    agentName: String(event.agentName || state.agentName || ''),
    resourceType: resourceType || state.resourceType || '',
    message: String(event.message || state.message || ''),
    resources,
    errors,
    updatedAt: Date.now()
  }
}

export function restoreLearningState(current, snapshot = {}) {
  const state = { ...createLearningState(), ...objectValue(current) }
  const server = objectValue(snapshot)
  const serverWorkflowId = String(server.workflowId || '').trim()
  if (state.workflowId && serverWorkflowId && state.workflowId !== serverWorkflowId) return state
  return {
    ...state,
    ...server,
    workflowId: state.workflowId || serverWorkflowId,
    progress: Math.max(state.progress, boundedProgress(server.progress, 0)),
    resources: { ...objectValue(state.resources), ...resourceMap(server.resources) },
    errors: { ...objectValue(state.errors), ...objectValue(server.errors) },
    updatedAt: Date.now()
  }
}
