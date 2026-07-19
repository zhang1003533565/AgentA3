const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const helperSource = readFileSync(join(__dirname, 'assistantMessage.js'), 'utf8')
const helperModule = import(`data:text/javascript;base64,${Buffer.from(helperSource).toString('base64')}`)

function page(relativePath) {
  return readFileSync(join(__dirname, relativePath), 'utf8')
}

function sourceSlice(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker)
  const end = source.indexOf(endMarker, start + startMarker.length)
  assert.ok(start >= 0 && end > start, `missing source slice: ${startMarker}`)
  return source.slice(start, end)
}

function stripImports(script) {
  const lines = script.split('\n')
  const result = []
  let skipping = false
  for (const line of lines) {
    const trimmed = line.trim()
    if (!skipping && trimmed.startsWith('import ')) {
      skipping = !/\sfrom\s+['"][^'"]+['"]\s*$/.test(trimmed)
      continue
    }
    if (skipping) {
      if (/^}\sfrom\s+['"][^'"]+['"]\s*$/.test(trimmed)) skipping = false
      continue
    }
    result.push(line)
  }
  return result.join('\n')
}

async function loadConversationComponent(overrides = {}) {
  const helper = await helperModule
  const dependencies = {
    NavBar: {},
    SafeMarkdown: {},
    ASSISTANT_PUBLIC_RESOURCE_HOSTS: [],
    BASE_URL: 'http://localhost:8080',
    downloadAssistantResource: async () => '/tmp/resource.bin',
    getLeaderSessionDetail: async () => ({ data: { messages: [] } }),
    queryLeaderAgent: async () => ({ data: {} }),
    streamLeaderAgent: async () => {},
    submitAssistantResourceInteraction: async () => ({}),
    buildResourceInteractionRequest: helper.buildResourceInteractionRequest,
    countAssistantHits: helper.countAssistantHits,
    mergeAssistantMessage: helper.mergeAssistantMessage,
    normalizeAssistantResources: helper.normalizeAssistantResources,
    normalizeResourceActions: helper.normalizeResourceActions,
    resolveAssistantResourceUrl: helper.resolveAssistantResourceUrl,
    resolveBusinessResourceRoute: helper.resolveBusinessResourceRoute,
    summarizeEvidenceChain: helper.summarizeEvidenceChain,
    ...overrides
  }
  const source = page('aiConversation/aiConversation.vue')
  const script = source.slice(source.indexOf('<script>') + '<script>'.length, source.indexOf('</script>'))
  const executable = stripImports(script).replace(/export default\s+\{/, 'return {')
  const factory = new Function(...Object.keys(dependencies), executable)
  return factory(...Object.values(dependencies))
}

test('assistant surfaces reuse safe markdown and full conversation accepts a prefilled intent', async () => {
  const full = page('aiConversation/aiConversation.vue')
  const floating = page('../components/ai-float-assistant/ai-float-assistant.vue')
  for (const source of [full, floating]) {
    assert.match(source, /SafeMarkdown/)
    assert.match(source, /<safe-markdown/)
    assert.doesNotMatch(source, /v-html/)
  }
  assert.doesNotMatch(floating, /formatAnswerLines\(getDisplayContent\(message\)\)/)

  const component = await loadConversationComponent()
  installUni()
  const vm = instantiate(component)
  component.onLoad.call(vm, { prefill: encodeURIComponent('为我生成 Python 列表切片练习') })
  assert.equal(vm.inputValue, '为我生成 Python 列表切片练习')
})

function instantiate(component) {
  const vm = {
    ...component.data(),
    ...component.methods,
    $nextTick(callback) {
      callback()
    }
  }
  Object.defineProperty(vm, 'canSend', {
    configurable: true,
    get: () => component.computed.canSend.call(vm)
  })
  return vm
}

function installUni(initialStorage = {}, overrides = {}) {
  const storage = { ...initialStorage }
  const calls = {
    showLoading: [],
    hideLoading: [],
    showToast: [],
    navigateTo: [],
    previewImage: [],
    openDocument: [],
    saveFile: []
  }
  const defaults = {
    getStorageSync: (key) => storage[key] || '',
    setStorageSync: (key, value) => { storage[key] = value },
    removeStorageSync: (key) => delete storage[key],
    showLoading: (options) => calls.showLoading.push(options),
    hideLoading: () => calls.hideLoading.push(true),
    showToast: (options) => calls.showToast.push(options),
    navigateTo: (options) => calls.navigateTo.push(options),
    redirectTo: () => {},
    previewImage: (options) => calls.previewImage.push(options),
    openDocument: (options) => calls.openDocument.push(options),
    saveFile: (options) => calls.saveFile.push(options),
    createInnerAudioContext: () => ({
      play() {}, stop() {}, destroy() {}, onEnded() {}, onError() {}
    })
  }
  globalThis.uni = { ...defaults, ...overrides }
  return { storage, calls }
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

test('full conversation source wires the shared message envelope and canonical resource cards', () => {
  const source = page('aiConversation/aiConversation.vue')

  for (const helper of [
    'mergeAssistantMessage',
    'normalizeAssistantResources',
    'summarizeEvidenceChain',
    'countAssistantHits',
    'normalizeResourceActions',
    'resolveBusinessResourceRoute',
    'buildResourceInteractionRequest'
  ]) {
    assert.match(source, new RegExp(`\\b${helper}\\b`))
  }
  assert.match(source, /class="resource-list"/)
  assert.match(source, /class="resource-card"/)
  assert.match(source, /getMessageResources\(message\)/)
  assert.match(source, /getResourceActions\(resource\)/)
  assert.match(source, /handleResourceAction\(resource, action, message\)/)
  assert.match(source, /class="resource-file"/)
  assert.match(source, /resource-file__extension/)
  assert.match(source, /getResourceFileExtension\(resource\)/)
  assert.match(source, /getResourceSizeLabel\(resource\)/)
  assert.match(source, /openFileResource\(resource, message\)/)
  assert.match(source, /点击下载/)
  assert.match(source, /resource-card__action--disabled/)
  assert.match(source, /action\.disabled\s*\?\s*`\$\{action\.label\}（暂不可用）`/)
  assert.match(source, /resource\.unavailable\s*\|\|\s*action\.disabled\s*\|\|\s*action\.unavailable/)
  assert.doesNotMatch(source, /\bnormalizeAttachments\s*\(/)
  assert.doesNotMatch(source, /\bextractAttachmentsFromText\s*\(/)
  assert.doesNotMatch(source, /\bbuildAttachment\s*\(/)

  assert.match(sourceSlice(source, '\n    async loadDetail() {', '\n    appendMessage(message, forceScroll = false) {'), /mergeAssistantMessage\(\{\},\s*item\)/)
  assert.match(sourceSlice(source, '\n    async loadDetail() {', '\n    appendMessage(message, forceScroll = false) {'), /this\.sessionId\s*!==\s*requestedSessionId/)
  assert.match(sourceSlice(source, '\n    async sendMessage(options = {}) {', '\n    stopGeneration() {'), /onDone:[\s\S]*mergeAssistantMessage\(current,/)
  assert.match(sourceSlice(source, '\n    applyGenerationStart(localId', '\n    async sendMessageFallback('), /mergeAssistantMessage\(current,/)
  assert.match(sourceSlice(source, '\n    async sendMessageFallback(', '\n    createInitialCallDetail('), /mergeAssistantMessage\(current,/)
})

test('full conversation source wires evidence state and recorded-source expansion', () => {
  const source = page('aiConversation/aiConversation.vue')

  assert.match(source, /class="evidence-panel"/)
  assert.match(source, /shouldShowEvidence\(message\)/)
  assert.match(source, /getEvidenceCompactLabel\(message\)/)
  assert.match(source, /toggleEvidence\(message\)/)
  assert.match(source, /getEvidenceSources\(message\)/)
  assert.match(source, /source\.excerpt/)
  assert.match(source, /summary\.trusted/)
  assert.match(source, /getEvidenceAgentLabel\(message\)/)
  assert.match(source, /getEvidenceGeneratedAtLabel\(message\)/)
})

test('full conversation source keeps protected URLs behind authenticated download and allows configured public media', () => {
  const source = page('aiConversation/aiConversation.vue')

  assert.match(source, /\bdownloadAssistantResource\(resource,\s*\{[\s\S]{0,120}approvedHosts:\s*ASSISTANT_PUBLIC_RESOURCE_HOSTS/)
  assert.match(source, /\bsubmitAssistantResourceInteraction\(/)
  assert.match(source, /\bbuildResourceInteractionRequest\(/)
  assert.match(source, /void\s+submitAssistantResourceInteraction\(/)
  assert.match(source, /\bresolveBusinessResourceRoute\(resource\)/)
  assert.match(source, /uni\.navigateTo\(\{\s*url:\s*route/)
  assert.match(source, /\bresolveAssistantResourceUrl\(/)
  assert.match(source, /resource\?\.authScope\s*!==\s*'public'/)
  assert.match(source, /ASSISTANT_PUBLIC_RESOURCE_HOSTS/)
  assert.match(source, /console\.warn\('\[assistant-resource-interaction\]'/)
  assert.doesNotMatch(source, /<image[\s\S]{0,180}:src="(?:file|resource)\.url"/)
  assert.doesNotMatch(source, /<video[\s\S]{0,180}:src="(?:file|resource)\.url"/)
  assert.doesNotMatch(source, /uni\.downloadFile\s*\(/)
})

test('full conversation source wires authenticated audio playback from safe display paths', () => {
  const source = page('aiConversation/aiConversation.vue')

  assert.match(source, /resource\.renderer === 'audio'/)
  assert.match(source, /toggleResourceAudio\(resource,\s*message\)/)
  assert.match(source, /uni\.createInnerAudioContext\(\)/)
  assert.match(source, /uni\.saveFile\(/)
  assert.match(source, /disposeAudio\(\)/)
})

test('full conversation keeps a growing composer in flex flow so it cannot cover the message tail', () => {
  const source = page('aiConversation/aiConversation.vue')
  const style = source.slice(source.indexOf('<style'), source.indexOf('</style>'))

  assert.match(style, /\.conversation-page\s*\{[\s\S]{0,220}padding-bottom:\s*0/)
  assert.match(style, /\.message-list\s*\{[\s\S]{0,160}min-height:\s*0/)
  assert.match(style, /\.message-list\s*\{[\s\S]{0,220}padding:\s*24rpx/)
  assert.doesNotMatch(style, /\.message-list\s*\{[\s\S]{0,220}padding:\s*24rpx 24rpx 112rpx/)
  assert.match(style, /\.scroll-to-bottom\s*\{[\s\S]{0,180}position:\s*absolute/)
  assert.match(style, /\.scroll-to-bottom\s*\{[\s\S]{0,220}bottom:\s*calc\(112rpx \+ env\(safe-area-inset-bottom\)\)/)
  assert.match(style, /\.composer\s*\{[\s\S]{0,180}position:\s*relative/)
  assert.match(style, /\.composer\s*\{[\s\S]{0,220}flex-shrink:\s*0/)
  assert.doesNotMatch(style, /\.composer\s*\{[\s\S]{0,120}position:\s*fixed/)
})

test('full conversation source preserves absent hit lists and merges authoritative final envelopes', () => {
  const source = page('aiConversation/aiConversation.vue')

  assert.match(source, /mergeAssistantMessage\(current,\s*\{[\s\S]*\.\.\.\(payload\s*\|\|\s*\{\}\)/)
  assert.match(source, /mergeAssistantMessage\(current,\s*\{[\s\S]{0,180}role:\s*'assistant',\s*type:\s*''/)
  assert.match(source, /Array\.isArray\(payload\?\.resources\)[\s\S]{0,220}attachments:\s*\[\]/)
  assert.match(source, /getMatchedCount\(detail\)\s*\{[\s\S]{0,280}countAssistantHits\(/)
  assert.match(source, /getExplicitMatchedResults\(message, raw\)/)
  assert.match(source, /delete normalized\.matchedResults/)
  const initialStart = source.indexOf('createInitialCallDetail(input)')
  const initialEnd = source.indexOf('createLiveTraceStep(', initialStart)
  assert.ok(initialStart >= 0 && initialEnd > initialStart)
  assert.doesNotMatch(source.slice(initialStart, initialEnd), /matchedResults/)
  assert.match(source, /Object\.prototype\.hasOwnProperty\.call\(payload\s*\|\|\s*\{\},\s*'matchedResults'\)/)
  assert.doesNotMatch(source, /javaCount\s*\+\s*documentCount/)
  assert.doesNotMatch(source, /attachments:\s*payload\?\.attachments\s*\|\|\s*\[\]/)
  assert.doesNotMatch(source, /matchedResults:\s*payload\?\.matchedResults\s*\|\|/)
  assert.match(source, /this\.resourceLocalPaths\s*=\s*\{\}/)
  assert.match(source, /this\.resourceLoading\s*=\s*\{\}/)
  assert.match(source, /this\.reportedInteractions\s*=\s*\{\}/)
})

test('assistant public resource hosts have an explicit app configuration boundary', () => {
  const source = page('../utils/config.js')
  assert.match(source, /export const ASSISTANT_PUBLIC_RESOURCE_HOSTS\s*=\s*\[/)
})

test('full conversation keeps Java authoritative terminal error resources and evidence', async () => {
  const evidenceChain = {
    schemaVersion: 'assistant-evidence-v1',
    evidenceState: 'generation_failed',
    status: 'model_only',
    sources: []
  }
  const terminalEnvelope = {
    sessionId: 'session-a',
    messageId: 201,
    answer: '资源生成失败，请稍后再试。',
    answerType: 'text',
    resources: [],
    attachments: [],
    evidenceChain
  }
  const component = await loadConversationComponent({
    streamLeaderAgent: async (payload, handlers) => {
      handlers.onError(terminalEnvelope)
      throw new Error('reader failed after authoritative terminal error')
    }
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  vm.inputValue = '生成失败场景'

  await vm.sendMessage()

  const assistant = vm.messages.at(-1)
  assert.equal(assistant.content, terminalEnvelope.answer)
  assert.equal(assistant.messageId, 201)
  assert.deepEqual(assistant.resources, [])
  assert.deepEqual(assistant.evidenceChain, evidenceChain)
  assert.equal(assistant.type, '')
})

test('full conversation shows the safe backend failure stage instead of a generic request error', async () => {
  const component = await loadConversationComponent()
  const vm = instantiate(component)

  const detail = vm.buildErrorCallDetail({
    payload: {
      retrievalMeta: {
        failureStage: 'leader_plan',
        failureReason: 'Leader 模型规划调用失败，请检查 Leader 智能体的模型配置。',
        failedAgent: 'leader_agent'
      }
    }
  })

  assert.equal(detail.currentStep, 'leader_plan')
  assert.equal(detail.agentName, 'leader_agent')
  assert.match(detail.error, /Leader 模型规划调用失败/)
})

test('a completed answer stays completed when the stream transport rejects during teardown', async () => {
  const component = await loadConversationComponent({
    streamLeaderAgent: async (payload, handlers) => {
      handlers.onDone({
        sessionId: 'session-a',
        messageId: 202,
        answer: '完整答案',
        answerType: 'text',
        resources: [],
        attachments: []
      })
      throw new Error('reader failed after done')
    }
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  vm.inputValue = '测试流收尾'

  await vm.sendMessage()

  const assistant = vm.messages.at(-1)
  assert.equal(assistant.content, '完整答案')
  assert.equal(assistant.responseState, 'completed')
  assert.doesNotMatch(assistant.content, /中断|失败/)
  assert.equal(vm.sending, false)
})

test('full conversation ignores a successful history snapshot that started before a local send', async () => {
  const history = deferred()
  const component = await loadConversationComponent({
    getLeaderSessionDetail: async () => history.promise,
    streamLeaderAgent: async () => {}
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'

  const loading = vm.loadDetail()
  vm.inputValue = '保留成功竞态下的本地消息'
  await vm.sendMessage()
  history.resolve({ data: { messages: [{ id: 1, role: 'assistant', content: '旧历史' }] } })
  await loading

  assert.ok(vm.messages.some((item) => item.role === 'user' && item.content === '保留成功竞态下的本地消息'))
  assert.ok(!vm.messages.some((item) => item.content === '旧历史'))
})

test('full conversation ignores a failed history request that started before a local send', async () => {
  const history = deferred()
  const component = await loadConversationComponent({
    getLeaderSessionDetail: async () => history.promise,
    streamLeaderAgent: async () => {}
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'

  const loading = vm.loadDetail()
  vm.inputValue = '保留失败竞态下的本地消息'
  await vm.sendMessage()
  history.reject(new Error('late history failure'))
  await loading

  assert.ok(vm.messages.some((item) => item.role === 'user' && item.content === '保留失败竞态下的本地消息'))
})

test('full conversation only applies its newest same-session history request', async () => {
  const first = deferred()
  const second = deferred()
  const pending = [first, second]
  const component = await loadConversationComponent({
    getLeaderSessionDetail: async () => pending.shift().promise
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'

  const firstLoad = vm.loadDetail()
  const secondLoad = vm.loadDetail()
  second.resolve({ data: { messages: [{ id: 2, role: 'assistant', content: '新快照' }] } })
  await secondLoad
  first.resolve({ data: { messages: [{ id: 1, role: 'assistant', content: '旧快照' }] } })
  await firstLoad

  assert.deepEqual(vm.messages.map((item) => item.content), ['新快照'])
})

test('full conversation ignores late SSE callbacks after generation releases into a new conversation', async () => {
  const stream = deferred()
  let handlers
  const component = await loadConversationComponent({
    streamLeaderAgent: async (payload, nextHandlers) => {
      handlers = nextHandlers
      return stream.promise
    }
  })
  const { storage } = installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  vm.inputValue = '旧会话生成任务'
  const sending = vm.sendMessage()
  handlers.onEvent('generation_start', {
    sessionId: 'session-a',
    messageId: 301,
    answer: '开始生成',
    resources: [],
    attachments: []
  })
  assert.equal(vm.sending, false)

  vm.startNewConversation()
  handlers.onSession({ sessionId: 'session-a' })
  handlers.onDone({
    sessionId: 'session-a',
    messageId: 301,
    answer: '不应恢复的旧结果',
    resources: [],
    attachments: []
  })
  stream.resolve()
  await sending

  assert.equal(vm.sessionId, '')
  assert.equal(storage.aiAssistantSessionId, undefined)
  assert.deepEqual(vm.messages, [])
})

test('a prompt-less facility follow-up action sends the title-derived prompt from the full conversation', async () => {
  const streamCalls = []
  const component = await loadConversationComponent({
    streamLeaderAgent: async (payload) => {
      streamCalls.push(payload)
    }
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  const message = {
    messageId: 302,
    resources: [{
      id: 'facility-1',
      kind: 'facility',
      deliveryType: 'business_card',
      title: '图书馆研讨室',
      payload: { businessId: 'facility-1' },
      actions: [{ type: 'follow_up', label: '继续了解' }]
    }]
  }
  const resource = vm.getMessageResources(message)[0]
  const action = vm.getResourceActions(resource)[0]

  await vm.handleResourceAction(resource, action, message)
  await Promise.resolve()

  assert.equal(action.prompt, '请继续介绍「图书馆研讨室」')
  assert.equal(streamCalls.length, 1)
  assert.equal(streamCalls[0].input, action.prompt)
  assert.ok(vm.messages.some((item) => item.role === 'user' && item.content === action.prompt))
})

test('answer conversion uses a compact action message and a structured leader request', async () => {
  let requestPayload
  const component = await loadConversationComponent({
    streamLeaderAgent: (payload, handlers) => {
      requestPayload = payload
      handlers.onDone({
        sessionId: 'session-a',
        messageId: 401,
        answer: '文件已生成',
        answerType: 'document',
        outputType: 'document',
        resources: [],
        attachments: []
      })
      return Promise.resolve()
    }
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'

  await vm.handleFollowUpAction({
    label: '生成文件版',
    prompt: '请把刚才的内容整理成可下载文件。',
    outputType: 'document'
  }, { messageId: 77 })

  assert.deepEqual(requestPayload, {
    sessionId: 'session-a',
    agentName: 'leader_agent',
    input: '请把刚才的内容整理成可下载文件。',
    interactionType: 'transform',
    displayInput: '已请求：生成文件版',
    requestedOutputType: 'document',
    sourceMessageId: 77
  })
  const actionMessage = vm.messages.find((item) => item.role === 'action')
  assert.equal(actionMessage.content, '已请求：生成文件版')
  assert.equal(actionMessage.requestContent, '请把刚才的内容整理成可下载文件。')
  assert.ok(!vm.messages.some((item) => item.role === 'user'
    && item.content === '请把刚才的内容整理成可下载文件。'))
  assert.equal(vm.messages.at(-1).content, '文件已生成')
})

test('structured conversion keeps its metadata when SSE falls back to the normal request', async () => {
  let fallbackPayload
  const unsupported = new Error('stream unsupported')
  unsupported.fallbackToNormalRequest = true
  const component = await loadConversationComponent({
    streamLeaderAgent: () => Promise.reject(unsupported),
    queryLeaderAgent: async (payload) => {
      fallbackPayload = payload
      return { data: { answer: '图片已生成', answerType: 'image', resources: [], attachments: [] } }
    }
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'

  await vm.handleFollowUpAction({
    label: '生成图片版',
    prompt: '请把刚才的内容转换成图片。',
    outputType: 'image'
  }, { messageId: 78 })

  assert.equal(fallbackPayload.interactionType, 'transform')
  assert.equal(fallbackPayload.displayInput, '已请求：生成图片版')
  assert.equal(fallbackPayload.requestedOutputType, 'image')
  assert.equal(fallbackPayload.sourceMessageId, 78)
  assert.equal(fallbackPayload.input, '请把刚才的内容转换成图片。')
})

test('stop generation aborts the active stream and preserves the partial answer', async () => {
  const stream = deferred()
  let handlers
  let abortReason = ''
  const abortableTask = Object.assign(stream.promise, {
    abort(reason) {
      abortReason = reason
      const error = new Error('aborted')
      error.name = 'AbortError'
      stream.reject(error)
      return true
    }
  })
  const component = await loadConversationComponent({
    streamLeaderAgent: (payload, nextHandlers) => {
      handlers = nextHandlers
      return abortableTask
    }
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  vm.inputValue = '写一份较长的计划'

  const sending = vm.sendMessage()
  handlers.onDelta('已经生成的部分')
  vm.stopGeneration()
  handlers.onDone({ answer: '停止后到达的完整答案', answerType: 'text' })
  await sending

  assert.equal(abortReason, 'user_cancelled')
  const assistant = vm.messages.at(-1)
  assert.equal(assistant.responseState, 'stopped')
  assert.match(assistant.content, /已经生成的部分/)
  assert.match(assistant.content, /已停止生成/)
  assert.doesNotMatch(assistant.content, /停止后到达的完整答案/)
  assert.equal(assistant.callDetail.status, 'stopped')
  assert.equal(vm.sending, false)
})

test('stop generation cannot be overwritten by a late non-streaming fallback response', async () => {
  const fallback = deferred()
  const unsupported = new Error('stream unsupported')
  unsupported.fallbackToNormalRequest = true
  const component = await loadConversationComponent({
    streamLeaderAgent: () => Promise.reject(unsupported),
    queryLeaderAgent: () => fallback.promise
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  vm.inputValue = '生成一份计划'

  const sending = vm.sendMessage()
  await Promise.resolve()
  await Promise.resolve()
  vm.stopGeneration()
  fallback.resolve({ data: { answer: '不应覆盖停止状态', answerType: 'text' } })
  await sending

  const assistant = vm.messages.at(-1)
  assert.equal(assistant.responseState, 'stopped')
  assert.match(assistant.content, /已停止生成/)
  assert.doesNotMatch(assistant.content, /不应覆盖停止状态/)
})

test('stop generation aborts the native fallback task and releases the composer immediately', async () => {
  const fallback = deferred()
  let abortReason = ''
  const fallbackTask = Object.assign(fallback.promise, {
    abort(reason) {
      abortReason = reason
      const error = new Error('aborted')
      error.name = 'AbortError'
      fallback.reject(error)
      return true
    }
  })
  const unsupported = new Error('stream unsupported')
  unsupported.fallbackToNormalRequest = true
  const component = await loadConversationComponent({
    streamLeaderAgent: () => Promise.reject(unsupported),
    queryLeaderAgent: () => fallbackTask
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  vm.inputValue = '原生端请求'

  const sending = vm.sendMessage()
  await Promise.resolve()
  await Promise.resolve()
  vm.stopGeneration()
  await sending

  assert.equal(abortReason, 'user_cancelled')
  assert.equal(vm.sending, false)
  assert.equal(vm.stopping, false)
  assert.equal(vm.messages.at(-1).responseState, 'stopped')
})

test('history restores structured actions and ordinary text stays visually untagged', async () => {
  const component = await loadConversationComponent({
    getLeaderSessionDetail: async () => ({
      data: {
        messages: [
          {
            id: 1,
            role: 'user',
            content: '已请求：生成视频版',
            answerType: 'action_transform',
            outputMeta: {
              interactionType: 'transform',
              requestContent: '请把刚才的内容生成视频版。',
              requestedOutputType: 'video'
            }
          },
          { id: 2, role: 'assistant', content: '普通回答', answerType: 'text', outputTypes: ['text'] }
        ]
      }
    })
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'

  await vm.loadDetail()

  assert.equal(vm.messages[0].role, 'action')
  assert.equal(vm.messages[0].requestContent, '请把刚才的内容生成视频版。')
  assert.deepEqual(vm.getVisibleOutputTypeTags(vm.messages[1]), [])
})

test('a stale full-page download cannot mutate the replacement view or clear its same-key loading owner', async () => {
  const firstDownload = deferred()
  const secondDownload = deferred()
  const downloads = [firstDownload, secondDownload]
  const interactions = []
  const component = await loadConversationComponent({
    downloadAssistantResource: async () => downloads.shift().promise,
    submitAssistantResourceInteraction: async (...args) => {
      interactions.push(args)
      return {}
    }
  })
  const { calls } = installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  const resource = {
    key: 'resource:shared-image',
    id: 'shared-image',
    renderer: 'image',
    url: '/api/ai/leader/sessions/session-a/messages/401/exports/image.png'
  }
  const action = { type: 'preview', label: '预览' }
  const firstOpening = vm.handleResourceAction(resource, action, { id: 401, messageId: 401 })
  assert.equal(vm.isResourceLoading(resource), true)

  vm.startNewConversation()
  const hidesAfterReset = calls.hideLoading.length
  vm.sessionId = 'session-b'
  const secondOpening = vm.handleResourceAction(resource, action, { id: 402, messageId: 402 })
  assert.equal(vm.isResourceLoading(resource), true)

  firstDownload.resolve('/tmp/session-a.png')
  await firstOpening

  assert.equal(vm.isResourceLoading(resource), true)
  assert.deepEqual(vm.resourceLocalPaths, {})
  assert.equal(calls.previewImage.length, 0)
  assert.equal(calls.showToast.length, 0)
  assert.equal(interactions.length, 0)
  assert.equal(hidesAfterReset, 1)
  assert.equal(calls.hideLoading.length, hidesAfterReset)

  secondDownload.resolve('/tmp/session-b.png')
  await secondOpening
  assert.equal(vm.isResourceLoading(resource), false)
  assert.equal(vm.resourceLocalPaths[resource.key], '/tmp/session-b.png')
  assert.equal(calls.previewImage.length, 1)
  assert.equal(interactions[0][0], 'session-b')
})

test('a stale full-page download rejection is silent in the replacement conversation', async () => {
  const download = deferred()
  const component = await loadConversationComponent({
    downloadAssistantResource: async () => download.promise
  })
  const { calls } = installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  const resource = {
    key: 'resource:old-document',
    id: 'old-document',
    renderer: 'document',
    url: '/api/ai/leader/sessions/session-a/messages/403/exports/document.pdf'
  }
  const opening = vm.handleResourceAction(resource, { type: 'download', label: '下载' }, { messageId: 403 })

  vm.startNewConversation()
  const hidesAfterReset = calls.hideLoading.length
  vm.sessionId = 'session-b'
  download.reject(new Error('旧会话下载失败'))
  await opening

  assert.deepEqual(calls.showToast, [])
  assert.equal(hidesAfterReset, 1)
  assert.equal(calls.hideLoading.length, hidesAfterReset)
})

test('late native open callbacks cannot restore an old audio path or toast in a new conversation', async () => {
  let saveOptions
  let documentOptions
  const component = await loadConversationComponent({
    downloadAssistantResource: async (resource) => resource.renderer === 'audio'
      ? '/tmp/session-a.mp3'
      : '/tmp/session-a.pdf'
  })
  const { calls } = installUni({ aiAssistantSessionId: 'session-a' }, {
    saveFile: (options) => { saveOptions = options },
    openDocument: (options) => { documentOptions = options }
  })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  const audio = {
    key: 'resource:old-audio', id: 'old-audio', renderer: 'audio',
    url: '/api/ai/leader/sessions/session-a/messages/404/exports/audio.mp3'
  }
  const document = {
    key: 'resource:old-pdf', id: 'old-pdf', renderer: 'document',
    url: '/api/ai/leader/sessions/session-a/messages/404/exports/document.pdf'
  }

  await vm.handleResourceAction(audio, { type: 'download', label: '下载' }, { messageId: 404 })
  await vm.handleResourceAction(document, { type: 'preview', label: '预览' }, { messageId: 404 })
  assert.ok(saveOptions)
  assert.ok(documentOptions)
  vm.startNewConversation()
  vm.sessionId = 'session-b'

  saveOptions.success({ savedFilePath: '/saved/session-a.mp3' })
  saveOptions.fail()
  documentOptions.fail()

  assert.deepEqual(vm.resourceLocalPaths, {})
  assert.deepEqual(calls.showToast, [])
})

test('an old audio error callback cannot dispose the replacement conversation audio', async () => {
  const audioInstances = []
  const component = await loadConversationComponent()
  const { calls } = installUni({ aiAssistantSessionId: 'session-a' }, {
    createInnerAudioContext: () => {
      const handlers = {}
      const instance = {
        handlers,
        stopCalls: 0,
        destroyCalls: 0,
        play() {},
        stop() { this.stopCalls += 1 },
        destroy() { this.destroyCalls += 1 },
        onEnded(callback) { handlers.ended = callback },
        onError(callback) { handlers.error = callback }
      }
      audioInstances.push(instance)
      return instance
    }
  })
  const vm = instantiate(component)
  const resource = { key: 'resource:shared-audio', id: 'shared-audio', renderer: 'audio' }
  vm.sessionId = 'session-a'
  vm.resourceLocalPaths = { [resource.key]: '/tmp/session-a.mp3' }
  vm.toggleResourceAudio(resource)
  const oldAudio = audioInstances[0]

  vm.startNewConversation()
  vm.sessionId = 'session-b'
  vm.resourceLocalPaths = { [resource.key]: '/tmp/session-b.mp3' }
  vm.toggleResourceAudio(resource)
  const replacementAudio = audioInstances[1]
  oldAudio.handlers.error()

  assert.equal(vm.audioContext, replacementAudio)
  assert.equal(vm.activeAudioKey, resource.key)
  assert.equal(replacementAudio.stopCalls, 0)
  assert.equal(replacementAudio.destroyCalls, 0)
  assert.deepEqual(calls.showToast, [])
})

test('a delayed business navigation success cannot report an old resource against the new session', async () => {
  const interactions = []
  const component = await loadConversationComponent({
    submitAssistantResourceInteraction: async (...args) => interactions.push(args)
  })
  const { calls } = installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  const resource = {
    key: 'resource:meeting-1',
    id: 'meeting-1',
    kind: 'meeting',
    renderer: 'business_card',
    payload: { businessId: 'meeting-1' }
  }

  await vm.handleResourceAction(resource, { type: 'open_resource', label: '打开' }, { messageId: 405 })
  assert.equal(calls.navigateTo.length, 1)
  vm.startNewConversation()
  vm.sessionId = 'session-b'
  calls.navigateTo[0].success()

  assert.equal(interactions.length, 0)
})

test('a stale interaction failure cannot delete the replacement epoch dedupe owner', async () => {
  const firstInteraction = deferred()
  const submissions = [firstInteraction.promise, Promise.resolve({})]
  const component = await loadConversationComponent({
    submitAssistantResourceInteraction: () => submissions.shift()
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  const resource = { key: 'resource:dedupe', id: 'dedupe', renderer: 'image', messageId: 406 }
  const message = { id: 406, messageId: 406 }
  vm.reportResourceInteraction(resource, message, 'preview')

  vm.startNewConversation()
  vm.sessionId = 'session-a'
  vm.reportResourceInteraction(resource, message, 'preview')
  const dedupeKey = Object.keys(vm.reportedInteractions)[0]
  assert.ok(dedupeKey)

  const originalWarn = console.warn
  console.warn = () => {}
  try {
    firstInteraction.reject(new Error('old interaction failed'))
    await new Promise((resolve) => setImmediate(resolve))
  } finally {
    console.warn = originalWarn
  }

  assert.ok(vm.reportedInteractions[dedupeKey])
})
