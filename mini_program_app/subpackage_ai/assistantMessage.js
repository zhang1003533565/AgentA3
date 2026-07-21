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
const MESSAGE_ARRAY_FIELDS = new Set(['matchedResults', 'trace', 'attachments', 'resources'])
const MESSAGE_OBJECT_FIELDS = new Set(['retrievalMeta', 'evidenceChain'])
const DEFAULT_FOLLOW_UP_TITLE_MAX_LENGTH = 80

function legacyMarkdownAttachmentPattern() {
  return /!?\[([^\]]+)\]\(((?:https?:\/\/|\/uploads\/)[^\s"'<>，。！？；、)]+?\.(?:png|jpe?g|gif|webp|bmp|mp4|mov|m4v|webm|ogg|pdf|docx?|pptx?|xlsx?|csv|md|mmd|zip)(?:\?[^\s"'<>，。！？；、)]*)?)\)/gi
}

function legacyAttachmentUrlPattern() {
  return /(?:https?:\/\/|\/uploads\/)[^\s"'<>，。！？；、]+?\.(?:png|jpe?g|gif|webp|bmp|mp4|mov|m4v|webm|ogg|pdf|docx?|pptx?|xlsx?|csv|md|mmd|zip)(?:\?[^\s"'<>，。！？；、]*)?/gi
}

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

function firstUrl(...values) {
  for (const value of values) {
    if (typeof value !== 'string') continue
    const url = value.trim()
    if (url) return url
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

function comparableContent(value, withoutAttachmentReferences = false) {
  if (typeof value !== 'string') return ''
  let content = value
  if (withoutAttachmentReferences) {
    content = content
      .replace(legacyMarkdownAttachmentPattern(), ' ')
      .replace(legacyAttachmentUrlPattern(), ' ')
  }
  return content.replace(/\r\n?/g, '\n').trim().replace(/\s+/g, ' ')
}

function equivalentMessageContent(value, messageContent) {
  const normalizedValue = comparableContent(value)
  const normalizedMessage = comparableContent(messageContent)
  if (!normalizedValue || !normalizedMessage) return false
  if (normalizedValue === normalizedMessage) return true

  const valueWithoutAttachments = comparableContent(value, true)
  const messageWithoutAttachments = comparableContent(messageContent, true)
  return Boolean(valueWithoutAttachments && messageWithoutAttachments
    && valueWithoutAttachments === messageWithoutAttachments)
}

function redundantMessageContentResource(resource, messageContent) {
  const source = objectValue(resource)
  if (inferDeliveryType(source) !== 'content') return false
  const payload = objectValue(source.payload)
  const body = typeof payload.content === 'string' ? payload.content : ''
  if (body.trim()) return equivalentMessageContent(body, messageContent)
  return equivalentMessageContent(typeof source.summary === 'string' ? source.summary : '', messageContent)
}

function resourceUnavailable(resource) {
  if (resource.unavailable === true) return true
  const metadata = objectValue(resource.metadata)
  return [resource.status, resource.availability, metadata.status, metadata.availability]
    .some((value) => textValue(value).toLowerCase() === 'legacy_unavailable')
}

function stableLegacyIdentity(resource) {
  return firstText(resource.storageKey, resource.url, resource.previewUrl, resource.fileName, resource.name, resource.title)
}

function unavailableLegacyFingerprint(resource) {
  if (!resourceUnavailable(resource)) return ''
  const title = firstText(resource.title, resource.fileName, resource.name)
    .replace(/\s+/g, ' ')
    .toLowerCase()
  const deliveryType = inferDeliveryType(resource) || 'document'
  return title ? `${deliveryType}:${title}` : ''
}

function fileNameFromUrl(url) {
  const clean = textValue(url).split(/[?#]/)[0]
  const encodedName = clean.slice(clean.lastIndexOf('/') + 1) || '文件'
  try {
    return decodeURIComponent(encodedName) || '文件'
  } catch {
    return encodedName || '文件'
  }
}

function legacyAttachmentCandidate(value, forcedType = '') {
  if (typeof value === 'string') {
    return { url: value.trim(), name: fileNameFromUrl(value), type: forcedType }
  }
  const source = objectValue(value)
  const url = firstUrl(source.url, source.fileUrl, source.file_url, source.path, source.href)
  if (!url && !firstText(source.storageKey, source.fileName, source.name, source.title)) return null
  return {
    ...source,
    url,
    name: firstText(source.name, source.fileName, fileNameFromUrl(url)),
    type: firstText(source.type, source.fileType, forcedType),
    mimeType: textValue(source.mimeType)
  }
}

function extractLegacyAttachmentsFromText(value) {
  const content = typeof value === 'string' ? value : ''
  if (!content) return []
  const result = []
  try {
    const parsed = JSON.parse(content)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      if (Array.isArray(parsed.images)) {
        for (const item of parsed.images) result.push(legacyAttachmentCandidate(item, 'image'))
      }
      for (const field of ['documents', 'files', 'attachments']) {
        if (!Array.isArray(parsed[field])) continue
        for (const item of parsed[field]) result.push(legacyAttachmentCandidate(item))
      }
      for (const [field, type] of [
        ['imageUrl', 'image'], ['image_url', 'image'],
        ['documentUrl', 'document'], ['document_url', 'document'],
        ['fileUrl', 'file'], ['file_url', 'file'], ['url', textValue(parsed.type)]
      ]) {
        if (typeof parsed[field] !== 'string' || !parsed[field].trim()) continue
        result.push(legacyAttachmentCandidate({
          url: parsed[field],
          name: firstText(parsed.name, parsed.fileName, parsed.title),
          type,
          mimeType: parsed.mimeType
        }))
      }
    }
  } catch {
    // Old plain-text responses are inspected by the conservative patterns below.
  }

  const markdownPattern = legacyMarkdownAttachmentPattern()
  let match
  while ((match = markdownPattern.exec(content)) !== null) {
    result.push(legacyAttachmentCandidate({ url: match[2], name: match[1] }))
  }
  const plainText = content.replace(legacyMarkdownAttachmentPattern(), '')
  for (const url of plainText.match(legacyAttachmentUrlPattern()) || []) {
    result.push(legacyAttachmentCandidate(url))
  }
  return result.filter(Boolean)
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

function defaultFollowUpPrompt(resource) {
  const title = textValue(objectValue(resource).title)
    .replace(/[\u0000-\u001f\u007f]/g, ' ')
    .replace(/[「」]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, DEFAULT_FOLLOW_UP_TITLE_MAX_LENGTH)
  return title ? `请继续介绍「${title}」` : ''
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
    meeting: `/subpackage_meeting/meetingDetail/meetingDetail?sessionId=${encodedId}`,
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
      if (route) {
        normalized.route = route
      } else {
        normalized.disabled = true
        normalized.unavailable = true
        normalized.disabledReason = '暂无安全可用的详情页'
      }
    }
    if (type === 'follow_up') {
      const prompt = firstText(action.prompt, action.value, defaultFollowUpPrompt(source))
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
  const url = firstUrl(source.url, source.fileUrl, source.file_url, source.path, source.href)
  const canonicalSource = { ...source, url }
  const deliveryType = inferDeliveryType(canonicalSource) || 'document'
  const unavailable = resourceUnavailable(canonicalSource)
  const fallbackActions = unavailable || !firstText(url, source.previewUrl)
    ? []
    : [{
        type: ['image', 'video', 'audio'].includes(deliveryType) ? 'preview' : 'download',
        label: ['image', 'video', 'audio'].includes(deliveryType) ? '预览' : '下载',
        target: 'resource',
        requiresAuth: url.startsWith('/api/')
      }]
  return normalizeAssistantResource({
    ...canonicalSource,
    kind: firstText(canonicalSource.kind, deliveryType),
    deliveryType,
    title: firstText(canonicalSource.title, canonicalSource.fileName, canonicalSource.name, fileNameFromUrl(url), '附件'),
    payload: Object.keys(objectValue(canonicalSource.payload)).length
      ? canonicalSource.payload
      : { type: 'file', format: extensionOf(canonicalSource) || 'file' },
    actions: Array.isArray(canonicalSource.actions) ? canonicalSource.actions : fallbackActions,
    status: unavailable ? 'legacy_unavailable' : canonicalSource.status
  }, { legacy: true, messageId })
}

function legacyAttachments(message, includeTextFallback) {
  const source = objectValue(message)
  const values = []
  for (const field of ['attachments', 'files', 'fileList']) {
    if (Array.isArray(source[field])) {
      for (const item of source[field]) values.push(legacyAttachmentCandidate(item))
    }
  }
  if (includeTextFallback) {
    values.push(...extractLegacyAttachmentsFromText(source.content))
  }
  return values.filter(Boolean)
}

export function normalizeAssistantResources(message) {
  const source = objectValue(message)
  const messageId = messageIdentity(source)
  const result = []
  const seen = new Set()
  const weakLegacyKeyCounts = new Map()
  for (const rawResource of Array.isArray(source.resources) ? source.resources : []) {
    if (!rawResource || typeof rawResource !== 'object' || Array.isArray(rawResource)) continue
    if (redundantMessageContentResource(rawResource, source.content)) continue
    const identities = resourceIdentities(rawResource)
    if (identities.some((identity) => seen.has(identity))) continue
    const normalized = normalizeAssistantResource(rawResource, { messageId })
    const keyIdentity = `key:${normalized.key}`
    if (seen.has(keyIdentity)) continue
    result.push(normalized)
    seen.add(keyIdentity)
    for (const identity of identities) seen.add(identity)
  }
  for (const attachment of legacyAttachments(source, result.length === 0)) {
    const identities = resourceIdentities(attachment)
    if (identities.some((identity) => seen.has(identity))) continue
    const normalized = legacyAttachmentResource(attachment, messageId)
    if (!stableLegacyIdentity(attachment)) continue
    if (!identities.length && normalized.unavailable) {
      const fingerprint = unavailableLegacyFingerprint(normalized)
      if (fingerprint && result.some((item) => unavailableLegacyFingerprint(item) === fingerprint)) continue
    }
    let keyIdentity = `key:${normalized.key}`
    if (!identities.length) {
      const duplicateCount = weakLegacyKeyCounts.get(normalized.key) || 0
      weakLegacyKeyCounts.set(normalized.key, duplicateCount + 1)
      if (duplicateCount > 0) {
        normalized.key = `${normalized.key}:duplicate:${duplicateCount + 1}`
        keyIdentity = `key:${normalized.key}`
      }
    } else if (seen.has(keyIdentity)) {
      continue
    }
    result.push(normalized)
    seen.add(keyIdentity)
    for (const identity of identities) seen.add(identity)
  }
  return result
}

function hasTraversal(value) {
  try {
    const decoded = decodeURIComponent(value)
    return /[\u0000-\u001f\u007f\\]/.test(decoded)
      || decoded.split(/[/?#]/).some((segment) => segment === '.' || segment === '..')
  } catch {
    return true
  }
}

function validHostname(value) {
  const hostname = textValue(value).toLowerCase()
  if (!hostname || hostname.length > 253) return false
  if (/^\d{1,3}(?:\.\d{1,3}){3}$/.test(hostname)) {
    return hostname.split('.').every((part) => Number(part) <= 255)
  }
  return hostname.split('.').every((label) => label.length > 0 && label.length <= 63
    && /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(label))
}

function parseAbsoluteHttpUrl(value) {
  const raw = textValue(value)
  const match = raw.match(/^(https?):\/\/([^/?#]+)([^?#]*)(\?[^#]*)?(#.*)?$/i)
  if (!match) return null
  const protocol = match[1].toLowerCase()
  const authority = match[2]
  if (!authority || authority.includes('@')) return null

  let hostname = authority
  let port = ''
  if (authority.startsWith('[')) {
    const end = authority.indexOf(']')
    if (end < 2) return null
    hostname = authority.slice(1, end).toLowerCase()
    const rest = authority.slice(end + 1)
    if (rest) {
      if (!/^:\d+$/.test(rest)) return null
      port = rest.slice(1)
    }
    if (!hostname.includes(':') || !/^[0-9a-f:.]+$/i.test(hostname)) return null
  } else {
    const colon = authority.lastIndexOf(':')
    if (colon >= 0) {
      if (authority.indexOf(':') !== colon) return null
      hostname = authority.slice(0, colon).toLowerCase()
      port = authority.slice(colon + 1)
    } else {
      hostname = hostname.toLowerCase()
    }
    if (!validHostname(hostname)) return null
  }
  if (port && (!/^\d+$/.test(port) || Number(port) < 1 || Number(port) > 65535)) return null
  return {
    protocol,
    hostname,
    port,
    path: match[3] || '',
    query: match[4] || '',
    fragment: match[5] || '',
    href: raw
  }
}

export function resolveAssistantResourceUrl(value, options = {}) {
  const raw = textValue(value)
  if (!raw || /[\s\u0000-\u001f\u007f\\]/.test(raw) || raw.startsWith('//') || hasTraversal(raw)) return ''
  if (raw.startsWith('/')) {
    if (!raw.startsWith('/api/') && !raw.startsWith('/uploads/')) return ''
    const baseUrl = textValue(options.baseUrl).replace(/\/+$/, '')
    if (!baseUrl) return ''
    const base = parseAbsoluteHttpUrl(baseUrl)
    if (!base || base.path || base.query || base.fragment) return ''
    return `${baseUrl}${raw}`
  }
  const parsed = parseAbsoluteHttpUrl(raw)
  if (!parsed || parsed.protocol !== 'https' || (parsed.port && parsed.port !== '443')) return ''
  const approvedHosts = Array.isArray(options.approvedHosts)
    ? options.approvedHosts.map((host) => textValue(host).toLowerCase()).filter(Boolean)
    : []
  return approvedHosts.includes(parsed.hostname) ? parsed.href : ''
}

export function buildAssistantDownloadOptions(resource, options = {}) {
  const source = objectValue(resource)
  if (resourceUnavailable(source)) return null
  const rawUrl = firstText(source.url, source.previewUrl)
  const url = resolveAssistantResourceUrl(rawUrl, options)
  if (!url) return null
  const localApi = rawUrl.startsWith('/api/')
  const authenticatedExport = /^\/api\/ai\/leader\/sessions\/[^/?#]+\/messages\/[^/?#]+\/exports\/[^/?#]+$/.test(rawUrl)
  if (localApi && !authenticatedExport) return null
  const protectedResource = authenticatedExport || textValue(source.authScope) === 'session_owner'
  const token = textValue(options.token)
  if (protectedResource && !token) return null
  if (protectedResource && !authenticatedExport) return null
  return {
    url,
    header: authenticatedExport ? { Authorization: `Bearer ${token}` } : {}
  }
}

export function summarizeEvidenceChain(evidenceChain) {
  if (evidenceChain === null || evidenceChain === undefined) {
    return {
      state: 'legacy_missing',
      status: 'model_only',
      label: '旧消息未记录来源',
      sourceCount: 0,
      agent: '',
      generatedAt: '',
      trusted: false
    }
  }
  const rootValid = evidenceChain && typeof evidenceChain === 'object' && !Array.isArray(evidenceChain)
  const chain = rootValid ? evidenceChain : {}
  const stateCandidate = textValue(chain.evidenceState)
  const statusCandidate = textValue(chain.status)
  const status = GROUNDING_STATES.has(statusCandidate) ? statusCandidate : 'model_only'
  const sourcesValid = Array.isArray(chain.sources)
    && chain.sources.every((source) => source && typeof source === 'object' && !Array.isArray(source)
      && textValue(source.evidenceId))
  const sourceCount = sourcesValid ? chain.sources.length : 0
  const groundingConsistent = GROUNDING_STATES.has(statusCandidate)
    && (statusCandidate === 'grounded' ? sourceCount > 0 : sourceCount === 0)
  const structureValid = rootValid
    && Object.keys(chain).length > 0
    && EVIDENCE_STATES.has(stateCandidate)
    && sourcesValid
    && groundingConsistent
  const state = structureValid ? stateCandidate : 'malformed'
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
    trusted: structureValid && state === 'available'
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
  if (MESSAGE_ARRAY_FIELDS.has(key) && Array.isArray(next)) return next
  if (MESSAGE_OBJECT_FIELDS.has(key) && next && typeof next === 'object' && !Array.isArray(next)) return next
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
