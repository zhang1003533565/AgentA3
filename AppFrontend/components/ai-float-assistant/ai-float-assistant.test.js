const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'ai-float-assistant.vue'), 'utf8')
const helperSource = readFileSync(join(__dirname, '../../subpackage_ai/assistantMessage.js'), 'utf8')
const helperModule = import(`data:text/javascript;base64,${Buffer.from(helperSource).toString('base64')}`)

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

async function loadComponent(overrides = {}) {
  const helper = await helperModule
  const dependencies = {
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
  const script = source.slice(source.indexOf('<script>') + '<script>'.length, source.indexOf('</script>'))
  const executable = stripImports(script).replace(/export default\s+\{/, 'return {')
  const factory = new Function(...Object.keys(dependencies), executable)
  return factory(...Object.values(dependencies))
}

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

function installUni(initialStorage = {}) {
  const storage = { ...initialStorage }
  const calls = {
    navigateTo: [],
    previewImage: [],
    setStorageSync: [],
    showToast: []
  }
  globalThis.uni = {
    getStorageSync: (key) => storage[key] || '',
    setStorageSync: (key, value) => {
      storage[key] = value
      calls.setStorageSync.push([key, value])
    },
    removeStorageSync: (key) => delete storage[key],
    showLoading: () => {},
    hideLoading: () => {},
    showToast: (options) => calls.showToast.push(options),
    navigateTo: (options) => {
      calls.navigateTo.push(options)
      options.success?.()
    },
    previewImage: (options) => calls.previewImage.push(options),
    openDocument: () => {},
    saveFile: () => {},
    createInnerAudioContext: () => ({
      play() {}, stop() {}, destroy() {}, onEnded() {}, onError() {}
    })
  }
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

test('floating assistant source wires the shared canonical message contract on history, SSE, and fallback paths', () => {
  for (const helper of [
    'mergeAssistantMessage',
    'normalizeAssistantResources',
    'normalizeResourceActions',
    'summarizeEvidenceChain',
    'countAssistantHits'
  ]) {
    assert.match(source, new RegExp(`\\b${helper}\\b`))
  }

  assert.match(source, /records\.map\(\(item\)\s*=>\s*\{[\s\S]{0,260}mergeAssistantMessage\(\{\},\s*item\)/)
  assert.match(source, /onEvent:\s*\(eventName,\s*payload\)[\s\S]{0,760}mergeLiveAssistantEnvelope/)
  assert.match(source, /eventName\s*===\s*'generation_start'[\s\S]{0,520}type:\s*''/)
  assert.match(source, /onSearch:\s*\(payload\)[\s\S]{0,420}matchedResults[\s\S]{0,180}retrievalMeta/)
  assert.match(source, /onDone:\s*\(payload\)[\s\S]{0,520}mergeAssistantMessage/)
  assert.match(source, /sendMessageFallback[\s\S]{0,900}mergeAssistantMessage/)
  assert.match(source, /messageId/)
  assert.match(source, /\btrace\b/)
  assert.match(source, /sessionChanged[\s\S]{0,220}clearSessionView\(\)/)
  assert.match(source, /if\s*\(!records\.length\)[\s\S]{0,180}clearSessionView\(\)/)
  assert.match(source, /resetSession\(\)\s*\{\s*if\s*\(this\.sending\)\s*return/)
  assert.match(source, /await\s+getLeaderSessionDetail\(saved\)[\s\S]{0,180}this\.sessionId\s*!==\s*saved/)
  assert.doesNotMatch(source, /matchedResults:\s*payload\?\.matchedResults\s*\|\|/)
})

test('floating assistant source wires canonical learning and campus resource cards with evidence state', () => {
  assert.match(source, /class="ai-resource-list"/)
  assert.match(source, /class="ai-resource-card"/)
  assert.match(source, /getMessageResources\(message\)/)
  assert.match(source, /getResourceActions\(resource\)/)
  assert.match(source, /handleResourceAction\(resource,\s*action,\s*message\)/)
  assert.match(source, /resource\.renderer === 'business_card'/)

  for (const kind of [
    'explanation', 'mind_map', 'diagram', 'exercise', 'code_example', 'extended_reading',
    'image', 'video', 'audio', 'document', 'presentation', 'spreadsheet', 'bundle',
    'course', 'activity', 'meeting', 'dining', 'facility', 'secondhand'
  ]) {
    assert.match(source, new RegExp(`\\b${kind}:`))
  }

  assert.match(source, /class="ai-evidence-panel"/)
  assert.match(source, /getEvidenceSummary\(message\)\.label/)
  assert.match(source, /getEvidenceSources\(message\)/)
  assert.match(source, /source\.excerpt/)
})

test('floating assistant source wires owned exports through authenticated temporary downloads', () => {
  assert.match(source, /ASSISTANT_PUBLIC_RESOURCE_HOSTS/)
  assert.match(source, /resolveAssistantResourceUrl\(/)
  assert.match(source, /resource\?\.authScope\s*!==\s*'public'/)
  assert.match(source, /downloadAssistantResource\(resource,\s*\{[\s\S]{0,140}approvedHosts:\s*ASSISTANT_PUBLIC_RESOURCE_HOSTS/)
  assert.match(source, /rememberResourceLocalPath\(/)
  assert.match(source, /resolveBusinessResourceRoute\(resource\)/)
  assert.match(source, /action\.type === 'follow_up'/)
  assert.doesNotMatch(source, /<image[\s\S]{0,180}:src="(?:resource|image)\.url"/)
  assert.doesNotMatch(source, /<video[\s\S]{0,180}:src="resource\.url"/)
  assert.doesNotMatch(source, /uni\.downloadFile\s*\(/)
})

test('floating assistant source wires non-blocking interaction evidence reporting', () => {
  assert.match(source, /buildResourceInteractionRequest\(/)
  assert.match(source, /void\s+submitAssistantResourceInteraction\(/)
  assert.match(source, /console\.warn\('\[assistant-resource-interaction\]'/)
  assert.match(source, /reportedInteractions/)
  assert.match(source, /delete next\[request\.dedupeKey\]/)
})

test('floating assistant source wires safe audio playback and native context disposal', () => {
  assert.match(source, /resource\.renderer === 'audio'/)
  assert.match(source, /toggleResourceAudio\(resource,\s*message\)/)
  assert.match(source, /uni\.createInnerAudioContext\(\)/)
  assert.match(source, /getResourceDisplayPath\(resource\)/)
  assert.match(source, /uni\.saveFile\(/)
  assert.match(source, /disposeAudio\(\)/)
  assert.match(source, /context\.destroy\?\.\(\)/)
  assert.match(source, /beforeUnmount\(\)[\s\S]{0,180}disposeAudio\(\)/)
})

test('floating assistant source has no second attachment parser', () => {
  assert.doesNotMatch(source, /\bgetMessageAttachments\s*\(/)
  assert.doesNotMatch(source, /\bnormalizeMessageAttachment\s*\(/)
  assert.doesNotMatch(source, /\bextractAttachmentsFromText\s*\(/)
})

test('floating assistant keeps the authoritative terminal error envelope instead of replacing its answer', async () => {
  const evidenceChain = {
    schemaVersion: 'assistant-evidence-v1',
    evidenceState: 'generation_failed',
    status: 'model_only',
    sources: []
  }
  const terminalEnvelope = {
    sessionId: 'session-a',
    messageId: 91,
    answer: '资源生成失败，请稍后再试。',
    answerType: 'text',
    resources: [],
    attachments: [],
    evidenceChain
  }
  const component = await loadComponent({
    streamLeaderAgent: async (payload, handlers) => {
      handlers.onError(terminalEnvelope)
      throw new Error('reader failed after authoritative terminal error')
    }
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  vm.inputValue = '生成一张图'

  await vm.sendMessage()

  const assistant = vm.messages.at(-1)
  assert.equal(assistant.content, terminalEnvelope.answer)
  assert.equal(assistant.messageId, 91)
  assert.deepEqual(assistant.resources, [])
  assert.deepEqual(assistant.evidenceChain, evidenceChain)
  assert.equal(assistant.type, '')
})

test('floating assistant refreshes the same session even when cached messages already exist', async () => {
  let historyCalls = 0
  const component = await loadComponent({
    getLeaderSessionDetail: async () => {
      historyCalls += 1
      return { data: { messages: [{ id: 7, role: 'assistant', content: '服务端新消息' }] } }
    }
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  vm.messages = [
    { id: 1, role: 'user', content: '旧问题' },
    { id: 2, role: 'assistant', content: '旧回答' }
  ]

  await vm.syncCurrentSession()

  assert.equal(historyCalls, 1)
  assert.deepEqual(vm.messages.map((item) => item.content), ['服务端新消息'])
})

test('history started before a local send cannot erase the new user and assistant messages', async () => {
  const history = deferred()
  const component = await loadComponent({
    getLeaderSessionDetail: async () => history.promise,
    streamLeaderAgent: async () => {}
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'

  const historyLoad = vm.syncCurrentSession()
  vm.inputValue = '不要被旧历史删除'
  await vm.sendMessage()
  history.resolve({ data: { messages: [] } })
  await historyLoad

  assert.ok(vm.messages.some((item) => item.role === 'user' && item.content === '不要被旧历史删除'))
  assert.ok(vm.messages.some((item) => item.role === 'assistant' && item.type === 'thinking'))
})

test('only the newest same-session history request may replace the floating view', async () => {
  const first = deferred()
  const second = deferred()
  const pending = [first, second]
  const component = await loadComponent({
    getLeaderSessionDetail: async () => pending.shift().promise
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'

  const firstLoad = vm.syncCurrentSession()
  const secondLoad = vm.syncCurrentSession()
  second.resolve({ data: { messages: [{ id: 2, role: 'assistant', content: '较新的快照' }] } })
  await secondLoad
  first.resolve({ data: { messages: [{ id: 1, role: 'assistant', content: '较旧的快照' }] } })
  await firstLoad

  assert.deepEqual(vm.messages.map((item) => item.content), ['较新的快照'])
})

test('late SSE callbacks from an old session cannot restore its UI or storage', async () => {
  const stream = deferred()
  let handlers
  const component = await loadComponent({
    streamLeaderAgent: async (payload, nextHandlers) => {
      handlers = nextHandlers
      return stream.promise
    }
  })
  const { storage } = installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  vm.inputValue = '旧会话问题'
  const sending = vm.sendMessage()

  storage.aiAssistantSessionId = 'session-b'
  await vm.syncCurrentSession()
  handlers.onSession({ sessionId: 'session-a' })
  handlers.onDone({
    sessionId: 'session-a',
    messageId: 101,
    answer: '不应出现的旧回答',
    resources: [],
    attachments: []
  })
  stream.resolve()
  await sending

  assert.equal(vm.sessionId, 'session-b')
  assert.equal(storage.aiAssistantSessionId, 'session-b')
  assert.ok(!vm.messages.some((item) => item.content === '不应出现的旧回答'))
})

test('an invalidated streaming request never falls back into the replacement session', async () => {
  const stream = deferred()
  const fallbackCalls = []
  const component = await loadComponent({
    streamLeaderAgent: async () => stream.promise,
    queryLeaderAgent: async (payload) => {
      fallbackCalls.push(payload)
      return { data: { sessionId: payload.sessionId, answer: '错误串入的新会话' } }
    }
  })
  const { storage } = installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  vm.inputValue = '需要 fallback 的旧问题'
  const sending = vm.sendMessage()

  storage.aiAssistantSessionId = 'session-b'
  await vm.syncCurrentSession()
  const unsupported = new Error('unsupported')
  unsupported.fallbackToNormalRequest = true
  stream.reject(unsupported)
  await sending

  assert.equal(fallbackCalls.length, 0)
  assert.equal(vm.sessionId, 'session-b')
  assert.equal(storage.aiAssistantSessionId, 'session-b')
})

test('a resource download completed after reset cannot open or report against the new session', async () => {
  const download = deferred()
  const interactions = []
  const component = await loadComponent({
    downloadAssistantResource: async () => download.promise,
    submitAssistantResourceInteraction: async (...args) => {
      interactions.push(args)
      return {}
    }
  })
  const { calls, storage } = installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  const resource = {
    key: 'resource:res-image',
    id: 'res-image',
    renderer: 'image',
    url: '/api/ai/leader/sessions/session-a/messages/51/exports/image.png'
  }
  const action = { type: 'preview', label: '预览' }
  const opening = vm.handleResourceAction(resource, action, { id: 51, messageId: 51 })

  vm.createSessionId = () => 'session-b'
  vm.resetSession()
  download.resolve('/tmp/old-image.png')
  await opening

  assert.equal(vm.sessionId, 'session-b')
  assert.equal(storage.aiAssistantSessionId, 'session-b')
  assert.equal(calls.previewImage.length, 0)
  assert.equal(interactions.length, 0)
  assert.deepEqual(vm.resourceLocalPaths, {})
})

test('a business action without a safe route stays visibly disabled and is not interactive', async () => {
  const interactions = []
  const component = await loadComponent({
    submitAssistantResourceInteraction: async (...args) => interactions.push(args)
  })
  const { calls } = installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  const resource = {
    key: 'resource:dining-1',
    id: 'dining-1',
    kind: 'dining',
    renderer: 'business_card',
    payload: { businessId: 'restaurant-or-stall-1' }
  }
  const disabledAction = {
    type: 'open_resource',
    label: '打开',
    disabled: true,
    unavailable: true,
    disabledReason: '暂无安全可用的详情页'
  }

  await vm.handleResourceAction(resource, disabledAction, { id: 52, messageId: 52 })

  assert.equal(calls.navigateTo.length, 0)
  assert.equal(calls.showToast.length, 0)
  assert.equal(interactions.length, 0)
  assert.match(source, /ai-resource-card__action--disabled/)
  assert.match(source, /action\.disabled\s*\?\s*`\$\{action\.label\}（暂不可用）`/)
})

test('a prompt-less dining follow-up action sends the title-derived prompt from the floating entry', async () => {
  const streamCalls = []
  const component = await loadComponent({
    streamLeaderAgent: async (payload) => {
      streamCalls.push(payload)
    }
  })
  installUni({ aiAssistantSessionId: 'session-a' })
  const vm = instantiate(component)
  vm.sessionId = 'session-a'
  const message = {
    messageId: 53,
    resources: [{
      id: 'dining-1',
      kind: 'dining',
      deliveryType: 'business_card',
      title: '东区一楼食堂',
      payload: { businessId: 'dining-1' },
      actions: [{ type: 'follow_up', label: '继续了解' }]
    }]
  }
  const resource = vm.getMessageResources(message)[0]
  const action = vm.getResourceActions(resource)[0]

  await vm.handleResourceAction(resource, action, message)
  await Promise.resolve()

  assert.equal(action.prompt, '请继续介绍「东区一楼食堂」')
  assert.equal(streamCalls.length, 1)
  assert.equal(streamCalls[0].input, action.prompt)
  assert.ok(vm.messages.some((item) => item.role === 'user' && item.content === action.prompt))
})
