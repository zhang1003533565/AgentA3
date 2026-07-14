const RESOURCE_ACTIONS = new Set(['open_resource', 'download', 'preview', 'follow_up'])
const INTERACTION_ACTIONS = new Set(['view', 'open', 'download', 'preview', 'follow_up', 'dismiss'])
const EVIDENCE_STATES = new Set([
  'available', 'legacy_missing', 'malformed', 'integrity_failed', 'generation_failed'
])
const GROUNDING_STATES = new Set(['grounded', 'context_only', 'model_only'])
const FILE_EXTENSIONS = {
  image: new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg']),
  video: new Set(['mp4', 'mov', 'webm', 'm4v']),
  audio: new Set(['mp3', 'wav', 'm4a', 'ogg']),
  presentation: new Set(['ppt', 'pptx']),
  spreadsheet: new Set(['xls', 'xlsx', 'csv']),
  bundle: new Set(['zip', 'tar', 'gz'])
}
const RENDERERS = new Set([
  'image', 'video', 'audio', 'document', 'presentation', 'spreadsheet', 'bundle',
  'content', 'business_card'
])

function objectValue(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function textValue(value) {
  return value === undefined || value === null ? '' : String(value).trim()
}

function own(object, key) {
  return Object.prototype.hasOwnProperty.call(objectValue(object), key)
}

function firstText(...values) {
  for (const value of values) {
    const text = textValue(value)
    if (text) return text
  }
  return ''
}

function safeNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number : 0
}

function messageIdentity(message) {
  const source = objectValue(message)
  return source.messageId ?? source.id ?? null
}

function extensionOf(resource) {
  const payload = objectValue(resource.payload)
  const explicit = firstText(payload.format, resource.ext, resource.format, resource.type).toLowerCase().replace(/^\./, '')
  if (explicit) return explicit
  const candidate = firstText(resource.fileName, resource.name, resource.title, resource.url).split(/[?#]/)[0]
  const match = candidate.match(/\.([a-z0-9]{1,16})$/i)
  return match ? match[1].toLowerCase() : ''
}

function inferDeliveryType(resource) {
  const explicit = firstText(resource.deliveryType, resource.kind, resource.type).toLowerCase()
  const aliases = {
    pdf: 'document', doc: 'document', docx: 'document', md: 'document', mmd: 'document',
    txt: 'document', file: 'document', excel: 'spreadsheet', xls: 'spreadsheet', xlsx: 'spreadsheet',
    ppt: 'presentation', pptx: 'presentation'
  }
  if (RENDERERS.has(explicit)) return explicit
  if (aliases[explicit]) return aliases[explicit]
  const mimeType = firstText(resource.mimeType).toLowerCase()
  if (mimeType.startsWith('image/')) return 'image'
  if (mimeType.startsWith('video/')) return 'video'
  if (mimeType.startsWith('audio/')) return 'audio'
  const extension = extensionOf(resource)
  for (const [type, extensions] of Object.entries(FILE_EXTENSIONS)) {
    if (extensions.has(extension)) return type
  }
  if (extension || firstText(resource.url, resource.storageKey)) return 'document'
  return explicit === 'business' ? 'business_card' : explicit
}

function resourceUnavailable(resource) {
  const metadata = objectValue(resource.metadata)
  return [resource.status, resource.availability, metadata.status, metadata.availability]
    .some((value) => textValue(value).toLowerCase() === 'legacy_unavailable')
}

function stableLegacyIdentity(resource) {
  return firstText(resource.storageKey, resource.url, resource.previewUrl, resource.fileName, resource.name, resource.title)
}

function stableKey(value) {
  let hash = 2166136261
  for (const character of String(value || '')) {
    hash ^= character.codePointAt(0)
    hash = Math.imul(hash, 16777619)
  }
  return (hash >>> 0).toString(16).padStart(8, '0')
}

function resourceIdentities(resource) {
  const result = []
  const id = textValue(resource.id)
  const storageKey = textValue(resource.storageKey)
  const url = textValue(resource.url)
  const previewUrl = textValue(resource.previewUrl)
  if (id) result.push(`id:${id}`)
  if (storageKey) result.push(`storage:${storageKey}`)
  if (url) result.push(`url:${url}`)
  if (previewUrl) result.push(`url:${previewUrl}`)
  return result
}

export function resolveBusinessResourceRoute(resource) {
  const source = objectValue(resource)
  const payload = objectValue(source.payload)
  const businessId = firstText(payload.businessId, source.businessId)
  if (!businessId) return null
  const encodedId = encodeURIComponent(businessId)
  const routes = {
    course: `/subpackage_schedule/scheduleDetail/scheduleDetail?id=${encodedId}`,
    activity: `/subpackage_community/communityDetail/communityDetail?id=${encodedId}`,
    secondhand: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${encodedId}`
  }
  return routes[textValue(source.kind).toLowerCase()] || null
}

export function normalizeResourceActions(resource) {
  const source = objectValue(resource)
  if (resourceUnavailable(source)) return []
  const result = []
  for (const rawAction of Array.isArray(source.actions) ? source.actions : []) {
    if (result.length >= 3) break
    const action = objectValue(rawAction)
    const type = textValue(action.type).toLowerCase()
    if (!RESOURCE_ACTIONS.has(type)) continue
    const normalized = {
      type,
      label: firstText(action.label, {
        open_resource: '打开', download: '下载', preview: '预览', follow_up: '继续提问'
      }[type]),
      target: 'resource',
      requiresAuth: action.requiresAuth !== false
    }
    if (type === 'open_resource' && inferDeliveryType(source) === 'business_card') {
      const route = resolveBusinessResourceRoute(source)
      if (!route) continue
      normalized.route = route
    }
    if (type === 'follow_up') {
      const prompt = firstText(action.prompt, action.value)
      if (prompt) normalized.prompt = prompt.slice(0, 1000)
    }
    result.push(normalized)
  }
  return result
}

export function normalizeAssistantResource(resource, options = {}) {
  const source = objectValue(resource)
  const kind = firstText(source.kind, 'unknown').toLowerCase()
  const deliveryType = inferDeliveryType(source)
  const renderer = RENDERERS.has(deliveryType) ? deliveryType : 'generic'
  const unavailable = resourceUnavailable(source)
  const id = textValue(source.id)
  const legacy = options.legacy === true || !id
  const identity = stableLegacyIdentity(source) || `${kind}:${firstText(source.title, 'resource')}`
  const messageId = source.messageId ?? options.messageId ?? null
  return {
    schemaVersion: firstText(source.schemaVersion, legacy ? 'legacy-attachment-v1' : 'assistant-resource-v1'),
    id: id || null,
    key: id ? `resource:${id}` : `legacy:${stableKey(identity)}`,
    messageId,
    kind,
    deliveryType: deliveryType || 'unknown',
    renderer,
    groundingStatus: GROUNDING_STATES.has(textValue(source.groundingStatus))
      ? textValue(source.groundingStatus)
      : 'model_only',
    title: firstText(source.title, source.fileName, source.name, '资源'),
    summary: textValue(source.summary),
    mimeType: textValue(source.mimeType),
    storageKey: textValue(source.storageKey),
    url: unavailable ? '' : textValue(source.url),
    previewUrl: unavailable ? '' : textValue(source.previewUrl),
    sourceType: textValue(source.sourceType),
    sourceId: textValue(source.sourceId),
    evidenceIds: Array.isArray(source.evidenceIds)
      ? source.evidenceIds.map(textValue).filter(Boolean)
      : [],
    authScope: firstText(source.authScope,
      textValue(source.url).startsWith('/api/') ? 'session_owner' : 'public'),
    payload: objectValue(source.payload),
    metadata: objectValue(source.metadata),
    actions: unavailable ? [] : normalizeResourceActions(source),
    unavailable,
    legacy
  }
}

function legacyAttachmentResource(attachment, messageId) {
  const source = objectValue(attachment)
  const deliveryType = inferDeliveryType(source) || 'document'
  const unavailable = resourceUnavailable(source)
  const fallbackActions = unavailable || !firstText(source.url, source.previewUrl)
    ? []
    : [{
        type: ['image', 'video', 'audio'].includes(deliveryType) ? 'preview' : 'download',
        label: ['image', 'video', 'audio'].includes(deliveryType) ? '预览' : '下载',
        target: 'resource',
        requiresAuth: textValue(source.url).startsWith('/api/')
      }]
  return normalizeAssistantResource({
    ...source,
    kind: firstText(source.kind, deliveryType),
    deliveryType,
    title: firstText(source.title, source.fileName, source.name, '附件'),
    payload: Object.keys(objectValue(source.payload)).length
      ? source.payload
      : { type: 'file', format: extensionOf(source) || 'file' },
    actions: Array.isArray(source.actions) ? source.actions : fallbackActions,
    status: unavailable ? 'legacy_unavailable' : source.status
  }, { legacy: true, messageId })
}

function legacyAttachments(message) {
  const source = objectValue(message)
  const values = []
  for (const field of ['attachments', 'files', 'fileList']) {
    if (Array.isArray(source[field])) values.push(...source[field])
  }
  return values.filter((item) => item && typeof item === 'object' && !Array.isArray(item))
}

export function normalizeAssistantResources(message) {
  const source = objectValue(message)
  const messageId = messageIdentity(source)
  const result = []
  const seen = new Set()
  for (const rawResource of Array.isArray(source.resources) ? source.resources : []) {
    if (!rawResource || typeof rawResource !== 'object' || Array.isArray(rawResource)) continue
    const identities = resourceIdentities(rawResource)
    if (identities.some((identity) => seen.has(identity))) continue
    const normalized = normalizeAssistantResource(rawResource, { messageId })
    const keyIdentity = `key:${normalized.key}`
    if (seen.has(keyIdentity)) continue
    result.push(normalized)
    seen.add(keyIdentity)
    for (const identity of identities) seen.add(identity)
  }
  for (const attachment of legacyAttachments(source)) {
    const identities = resourceIdentities(attachment)
    if (identities.some((identity) => seen.has(identity))) continue
    const normalized = legacyAttachmentResource(attachment, messageId)
    if (!stableLegacyIdentity(attachment)) continue
    const keyIdentity = `key:${normalized.key}`
    if (seen.has(keyIdentity)) continue
    result.push(normalized)
    seen.add(keyIdentity)
    for (const identity of identities) seen.add(identity)
  }
  return result
}

function hasTraversal(value) {
  try {
    const decoded = decodeURIComponent(value)
    return decoded.includes('\\') || decoded.split(/[/?#]/).some((segment) => segment === '.' || segment === '..')
  } catch {
    return true
  }
}

export function resolveAssistantResourceUrl(value, options = {}) {
  const raw = textValue(value)
  if (!raw || /[\u0000-\u001f\u007f\\]/.test(raw) || raw.startsWith('//') || hasTraversal(raw)) return ''
  if (raw.startsWith('/')) {
    if (!raw.startsWith('/api/') && !raw.startsWith('/uploads/')) return ''
    const baseUrl = textValue(options.baseUrl).replace(/\/+$/, '')
    if (!baseUrl) return ''
    try {
      const base = new URL(baseUrl)
      if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password) return ''
    } catch {
      return ''
    }
    return `${baseUrl}${raw}`
  }
  let parsed
  try {
    parsed = new URL(raw)
  } catch {
    return ''
  }
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password || (parsed.port && parsed.port !== '443')) return ''
  const approvedHosts = Array.isArray(options.approvedHosts)
    ? options.approvedHosts.map((host) => textValue(host).toLowerCase()).filter(Boolean)
    : []
  return approvedHosts.includes(parsed.hostname.toLowerCase()) ? parsed.href : ''
}

export function buildAssistantDownloadOptions(resource, options = {}) {
  const source = objectValue(resource)
  if (resourceUnavailable(source)) return null
  const rawUrl = firstText(source.url, source.previewUrl)
  const url = resolveAssistantResourceUrl(rawUrl, options)
  if (!url) return null
  const localApi = rawUrl.startsWith('/api/')
  const protectedResource = localApi || textValue(source.authScope) === 'session_owner'
  const token = textValue(options.token)
  if (protectedResource && !token) return null
  if (protectedResource && !localApi) return null
  return {
    url,
    header: localApi ? { Authorization: `Bearer ${token}` } : {}
  }
}

export function summarizeEvidenceChain(evidenceChain) {
  const chain = objectValue(evidenceChain)
  const stateCandidate = textValue(chain.evidenceState)
  const state = EVIDENCE_STATES.has(stateCandidate)
    ? stateCandidate
    : (Object.keys(chain).length ? 'malformed' : 'legacy_missing')
  const statusCandidate = textValue(chain.status)
  const status = GROUNDING_STATES.has(statusCandidate) ? statusCandidate : 'model_only'
  const sourceCount = Array.isArray(chain.sources) ? chain.sources.length : 0
  const labels = {
    legacy_missing: '旧消息未记录来源',
    malformed: '来源数据格式异常',
    integrity_failed: '来源完整性校验失败',
    generation_failed: '来源链生成失败'
  }
  let label = labels[state]
  if (!label) {
    label = status === 'grounded'
      ? `有依据与来源（${sourceCount}）`
      : status === 'context_only'
        ? '使用了会话或画像上下文'
        : '未检索外部来源'
  }
  const generation = objectValue(chain.generation)
  return {
    state,
    status,
    label,
    sourceCount,
    agent: textValue(generation.agent),
    generatedAt: textValue(chain.generatedAt),
    trusted: state === 'available'
  }
}

export function buildResourceInteractionRequest(sessionId, messageId, resourceId, action) {
  const normalizedSessionId = textValue(sessionId)
  const normalizedMessageId = textValue(messageId)
  const normalizedResourceId = textValue(resourceId)
  const normalizedAction = textValue(action).toLowerCase()
  if (!normalizedSessionId || !normalizedMessageId || !normalizedResourceId || !INTERACTION_ACTIONS.has(normalizedAction)) {
    return null
  }
  return {
    url: `/api/ai/leader/sessions/${encodeURIComponent(normalizedSessionId)}`
      + `/messages/${encodeURIComponent(normalizedMessageId)}`
      + `/resources/${encodeURIComponent(normalizedResourceId)}/interactions`,
    method: 'POST',
    data: { action: normalizedAction },
    dedupeKey: `${normalizedSessionId}:${normalizedMessageId}:${normalizedResourceId}:${normalizedAction}`
  }
}

export function countAssistantHits(message) {
  const source = objectValue(message)
  if (own(source, 'matchedResults')) {
    return Array.isArray(source.matchedResults) ? source.matchedResults.length : 0
  }
  const metadata = objectValue(source.retrievalMeta)
  return Math.max(safeNumber(metadata.javaBackendCount), safeNumber(metadata.documentCount))
}

function preferCollection(previous, incoming, key) {
  if (!own(incoming, key)) return previous[key]
  const next = incoming[key]
  if (Array.isArray(next)) {
    return next.length || !Array.isArray(previous[key]) || !previous[key].length ? next : previous[key]
  }
  if (next && typeof next === 'object') {
    return Object.keys(next).length || !objectValue(previous[key]) || !Object.keys(objectValue(previous[key])).length
      ? next
      : previous[key]
  }
  return previous[key]
}

export function mergeAssistantMessage(previousMessage, incomingMessage) {
  const previous = objectValue(previousMessage)
  const incoming = objectValue(incomingMessage)
  const merged = { ...previous, ...incoming }
  const incomingAnswer = own(incoming, 'answer') ? incoming.answer : incoming.content
  merged.content = typeof incomingAnswer === 'string' && incomingAnswer.length
    ? incomingAnswer
    : (typeof previous.content === 'string' ? previous.content : '')
  merged.id = previous.id ?? incoming.id ?? null
  merged.messageId = incoming.messageId ?? incoming.id ?? previous.messageId ?? previous.id ?? null
  for (const key of ['matchedResults', 'retrievalMeta', 'trace', 'attachments', 'resources', 'evidenceChain']) {
    const value = preferCollection(previous, incoming, key)
    if (value !== undefined) merged[key] = value
  }
  if (Array.isArray(merged.resources)) {
    merged.resources = merged.resources.map((resource) => ({
      ...objectValue(resource),
      messageId: objectValue(resource).messageId ?? merged.messageId
    }))
  }
  return merged
}
