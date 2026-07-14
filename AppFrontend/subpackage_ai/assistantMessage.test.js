const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const helperPath = join(__dirname, 'assistantMessage.js')
const helperSource = readFileSync(helperPath, 'utf8')
const helper = import(`data:text/javascript;base64,${Buffer.from(helperSource).toString('base64')}`)
const apiPath = join(__dirname, '../api/ai.js')
const apiSource = readFileSync(apiPath, 'utf8')
let apiModuleNonce = 0

async function loadAiApi({ buildAssistantDownloadOptions, token = 'test-token', baseUrl = 'http://localhost:8080' }) {
  globalThis.__assistantApiTestMocks = {
    request: () => Promise.resolve(),
    baseUrl,
    getToken: () => token,
    buildAssistantDownloadOptions
  }
  const importableSource = apiSource
    .replace("import { request } from '@/utils/request.js'", 'const request = (...args) => globalThis.__assistantApiTestMocks.request(...args)')
    .replace("import { BASE_URL } from '@/utils/config.js'", 'const BASE_URL = globalThis.__assistantApiTestMocks.baseUrl')
    .replace("import { getToken } from '@/utils/storage.js'", 'const getToken = () => globalThis.__assistantApiTestMocks.getToken()')
    .replace("import { buildAssistantDownloadOptions } from '../subpackage_ai/assistantMessage.js'",
      'const buildAssistantDownloadOptions = (...args) => globalThis.__assistantApiTestMocks.buildAssistantDownloadOptions(...args)')
  apiModuleNonce += 1
  return import(`data:text/javascript;base64,${Buffer.from(`${importableSource}\n// test-module-${apiModuleNonce}`).toString('base64')}`)
}

test('legacy attachments fall back to resources while new resources win without duplicates', async () => {
  const { normalizeAssistantResources } = await helper
  const message = {
    id: 41,
    resources: [{
      schemaVersion: 'assistant-resource-v1',
      id: 'res_doc',
      kind: 'document',
      deliveryType: 'document',
      title: '新版讲义',
      storageKey: 'same.docx',
      url: '/api/ai/leader/sessions/s/messages/41/exports/same.docx',
      authScope: 'session_owner',
      payload: { type: 'file', format: 'docx' }
    }],
    attachments: [
      { name: '旧重复项', type: 'docx', storageKey: 'same.docx', url: '/old/same.docx' },
      { name: '补充视频', type: 'video', url: 'https://cdn.example.edu/demo.mp4' }
    ]
  }

  const resources = normalizeAssistantResources(message)
  assert.equal(resources.length, 2)
  assert.equal(resources[0].id, 'res_doc')
  assert.equal(resources[0].messageId, 41)
  assert.equal(resources[0].renderer, 'document')
  assert.equal(resources[1].renderer, 'video')
  assert.match(resources[1].key, /^legacy:/)
  assert.doesNotMatch(resources[1].key, /:1$/)
  assert.doesNotMatch(resources[1].key, /cdn\.example\.edu|demo\.mp4/)
})

test('legacy unavailable records stay disabled and cannot be restored from old attachments', async () => {
  const { normalizeAssistantResources } = await helper
  const oldUrl = 'http://localhost:8081/generated-exports/old.docx'
  const resources = normalizeAssistantResources({
    messageId: 9,
    resources: [{
      id: 'res_old',
      kind: 'document',
      deliveryType: 'document',
      title: '旧文档',
      url: oldUrl,
      metadata: { status: 'legacy_unavailable' },
      actions: [{ type: 'download' }]
    }],
    attachments: [{ name: '旧文档', url: oldUrl, status: 'legacy_unavailable' }]
  })

  assert.equal(resources.length, 1)
  assert.equal(resources[0].unavailable, true)
  assert.equal(resources[0].url, '')
  assert.deepEqual(resources[0].actions, [])
})

test('cleared legacy URLs deduplicate canonical resources and weak attachments by unavailable title and delivery', async () => {
  const { normalizeAssistantResources } = await helper
  const resources = normalizeAssistantResources({
    resources: [{
      id: 'res_old',
      kind: 'document',
      deliveryType: 'document',
      title: 'x.docx',
      metadata: { status: 'legacy_unavailable' }
    }],
    attachments: [{ name: 'x.docx', status: 'legacy_unavailable' }]
  })

  assert.equal(resources.length, 1)
  assert.equal(resources[0].id, 'res_old')
})

test('legacy fallback preserves structured aliases plus JSON, Markdown and bare file URLs', async () => {
  const { normalizeAssistantResources } = await helper
  const structured = normalizeAssistantResources({
    attachments: [
      { name: 'a.docx', fileUrl: '/uploads/a.docx' },
      { name: 'b.pdf', path: '/uploads/b.pdf' },
      { name: 'c.zip', href: '/uploads/c.zip' }
    ]
  })
  assert.deepEqual(structured.map((item) => item.url), [
    '/uploads/a.docx', '/uploads/b.pdf', '/uploads/c.zip'
  ])

  const json = normalizeAssistantResources({
    content: JSON.stringify({
      images: ['/uploads/cover.png'],
      documents: [{ fileUrl: '/uploads/notes.pdf', name: 'notes.pdf' }],
      files: [{ path: 'https://cdn.example.edu/slides.pptx' }],
      attachments: [{ href: '/uploads/archive.zip' }],
      documentUrl: '/uploads/single.docx'
    })
  })
  assert.deepEqual(new Set(json.map((item) => item.url)), new Set([
    '/uploads/cover.png', '/uploads/notes.pdf', 'https://cdn.example.edu/slides.pptx',
    '/uploads/archive.zip', '/uploads/single.docx'
  ]))

  const markdown = normalizeAssistantResources({ content: '请查看 [旧讲义](/uploads/old.docx)。' })
  assert.equal(markdown.length, 1)
  assert.equal(markdown[0].url, '/uploads/old.docx')
  assert.equal(markdown[0].legacy, true)

  const bare = normalizeAssistantResources({ content: '视频：https://cdn.example.edu/demo.mp4' })
  assert.equal(bare.length, 1)
  assert.equal(bare[0].url, 'https://cdn.example.edu/demo.mp4')

  const canonicalWins = normalizeAssistantResources({
    resources: [{
      id: 'res_doc', kind: 'document', deliveryType: 'document', title: '新版讲义',
      url: '/uploads/old.docx'
    }],
    content: '[旧讲义](/uploads/old.docx) 和 [补充](/uploads/supplement.pdf)'
  })
  assert.equal(canonicalWins.length, 1)
  assert.equal(canonicalWins[0].id, 'res_doc')
  assert.ok(json.every((item) => item.legacy))
})

test('normal legacy attachments without strong identities keep same-name records and use local collision keys', async () => {
  const { normalizeAssistantResources } = await helper
  const resources = normalizeAssistantResources({
    attachments: [
      { name: '同名说明', type: 'file' },
      { name: '同名说明', type: 'file' },
      { name: '另一份说明', type: 'file' }
    ]
  })
  assert.equal(resources.length, 3)
  assert.equal(new Set(resources.map((item) => item.key)).size, 3)
  assert.ok(resources.every((item) => item.id === null && /^legacy:[0-9a-f]{8}(?::duplicate:\d+)?$/.test(item.key)))
})

test('resource renderers use typed cards and unknown types safely become generic', async () => {
  const { normalizeAssistantResource } = await helper
  assert.equal(normalizeAssistantResource({ deliveryType: 'image', kind: 'image' }).renderer, 'image')
  assert.equal(normalizeAssistantResource({ deliveryType: 'business_card', kind: 'course' }).renderer, 'business_card')
  assert.equal(normalizeAssistantResource({ deliveryType: 'content', kind: 'explanation' }).renderer, 'content')
  assert.equal(normalizeAssistantResource({ deliveryType: 'future_widget', kind: 'future_kind' }).renderer, 'generic')
})

test('business routes are local allowlists and ambiguous kinds never guess a route', async () => {
  const { resolveBusinessResourceRoute } = await helper
  assert.equal(resolveBusinessResourceRoute({ kind: 'course', payload: { businessId: 'CS/1', route: 'javascript:bad' } }),
    '/subpackage_schedule/scheduleDetail/scheduleDetail?id=CS%2F1')
  assert.equal(resolveBusinessResourceRoute({ kind: 'activity', payload: { businessId: 7 } }),
    '/subpackage_community/communityDetail/communityDetail?id=7')
  assert.equal(resolveBusinessResourceRoute({ kind: 'secondhand', payload: { businessId: '二手 8' } }),
    '/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=%E4%BA%8C%E6%89%8B%208')
  for (const kind of ['dining', 'facility', 'meeting', 'unknown']) {
    assert.equal(resolveBusinessResourceRoute({
      kind,
      payload: { businessId: '1', route: '/pages/unsafe' },
      metadata: { route: 'javascript:alert(1)' }
    }), null)
  }
})

test('resource URLs accept Java relative paths and only approved HTTPS hosts', async () => {
  const { resolveAssistantResourceUrl } = await helper
  const baseUrl = 'http://localhost:8080'
  assert.equal(resolveAssistantResourceUrl('/api/ai/leader/file', { baseUrl }),
    'http://localhost:8080/api/ai/leader/file')
  assert.equal(resolveAssistantResourceUrl('/uploads/public.png', { baseUrl }),
    'http://localhost:8080/uploads/public.png')
  assert.equal(resolveAssistantResourceUrl('https://cdn.example.edu/a.pdf', {
    baseUrl,
    approvedHosts: ['cdn.example.edu']
  }), 'https://cdn.example.edu/a.pdf')
  for (const unsafe of [
    '//evil.example/a', '/api/../secret', '/api\\secret', 'javascript:alert(1)',
    'data:text/plain,x', 'file:///tmp/a', 'http://cdn.example.edu/a',
    'https://user@cdn.example.edu/a', 'https://evil.example/a'
  ]) {
    assert.equal(resolveAssistantResourceUrl(unsafe, { baseUrl, approvedHosts: ['cdn.example.edu'] }), '')
  }
})

test('resource URL safety stays available when the platform has no WHATWG URL global', async () => {
  const { resolveAssistantResourceUrl } = await helper
  const originalUrl = globalThis.URL
  try {
    globalThis.URL = undefined
    assert.equal(resolveAssistantResourceUrl('/api/ai/leader/file', {
      baseUrl: 'http://localhost:8080'
    }), 'http://localhost:8080/api/ai/leader/file')
    assert.equal(resolveAssistantResourceUrl('/uploads/public.png', {
      baseUrl: 'https://app.example.edu'
    }), 'https://app.example.edu/uploads/public.png')
    assert.equal(resolveAssistantResourceUrl('https://cdn.example.edu/a.pdf', {
      approvedHosts: ['cdn.example.edu']
    }), 'https://cdn.example.edu/a.pdf')
    for (const unsafe of [
      '//evil.example/a.pdf', 'https://user@cdn.example.edu/a.pdf',
      'https://cdn.example.edu:444/a.pdf', 'https://evil.example/a.pdf',
      '/api/%2e%2e/secret', 'https://cdn.example.edu/%0asecret.pdf'
    ]) {
      assert.equal(resolveAssistantResourceUrl(unsafe, {
        baseUrl: 'http://localhost:8080', approvedHosts: ['cdn.example.edu']
      }), '')
    }
  } finally {
    globalThis.URL = originalUrl
  }
})

test('download options attach local Bearer tokens only to exact assistant export routes', async () => {
  const { buildAssistantDownloadOptions } = await helper
  const exportUrl = '/api/ai/leader/sessions/session%2Fa/messages/42/exports/report%20final.pdf'
  assert.deepEqual(buildAssistantDownloadOptions({
    url: exportUrl, authScope: 'session_owner'
  }, { baseUrl: 'http://localhost:8080', token: 'secret-token' }), {
    url: `http://localhost:8080${exportUrl}`,
    header: { Authorization: 'Bearer secret-token' }
  })
  assert.equal(buildAssistantDownloadOptions({
    url: exportUrl, authScope: 'session_owner'
  }, { baseUrl: 'http://localhost:8080', token: '' }), null)
  for (const unexpectedApiUrl of [
    '/api/ai/leader/file',
    '/api/profile/radar/my',
    '/api/ai/leader/sessions/s/messages/42/exports/file.pdf?redirect=1'
  ]) {
    assert.equal(buildAssistantDownloadOptions({
      url: unexpectedApiUrl, authScope: 'session_owner'
    }, { baseUrl: 'http://localhost:8080', token: 'must-not-send' }), null)
  }
  assert.deepEqual(buildAssistantDownloadOptions({
    url: 'https://cdn.example.edu/public.pdf', authScope: 'public'
  }, {
    baseUrl: 'http://localhost:8080', token: 'must-not-leak', approvedHosts: ['cdn.example.edu']
  }), {
    url: 'https://cdn.example.edu/public.pdf',
    header: {}
  })
  assert.equal(buildAssistantDownloadOptions({
    url: 'https://cdn.example.edu/private.pdf', authScope: 'session_owner'
  }, {
    baseUrl: 'http://localhost:8080', token: 'must-not-leak', approvedHosts: ['cdn.example.edu']
  }), null)
})

test('evidence summaries expose formal state without claiming signatures', async () => {
  const { summarizeEvidenceChain } = await helper
  const available = summarizeEvidenceChain({
    status: 'grounded', evidenceState: 'available', generatedAt: '2026-07-14T12:00:00Z',
    sources: [{ evidenceId: 'ev_1' }, { evidenceId: 'ev_2' }],
    generation: { agent: 'leader_agent' }, integrity: { signed: false }
  })
  assert.equal(available.sourceCount, 2)
  assert.equal(available.agent, 'leader_agent')
  assert.equal(available.trusted, true)
  assert.doesNotMatch(available.label, /签名|防伪/)

  for (const state of ['legacy_missing', 'malformed', 'integrity_failed', 'generation_failed']) {
    const summary = summarizeEvidenceChain({ status: 'model_only', evidenceState: state, sources: [] })
    assert.equal(summary.state, state)
    assert.equal(summary.sourceCount, 0)
    assert.equal(summary.trusted, false)
  }
})

test('malformed or contradictory evidence never becomes trusted', async () => {
  const { summarizeEvidenceChain } = await helper
  assert.equal(summarizeEvidenceChain(null).state, 'legacy_missing')
  assert.equal(summarizeEvidenceChain(undefined).state, 'legacy_missing')

  const malformed = [
    {}, [], 'broken',
    { evidenceState: 'available', status: 'grounded', sources: {} },
    { evidenceState: 'available', status: 'grounded', sources: [] },
    { evidenceState: 'available', status: 'grounded', sources: [{}] },
    { evidenceState: 'available', status: 'model_only', sources: [{}] },
    { evidenceState: 'available', status: 'context_only', sources: [{}] },
    { evidenceState: 'available', status: 'grounded', sources: [null] }
  ]
  assert.deepEqual(malformed.map((value) => {
    const summary = summarizeEvidenceChain(value)
    return [summary.state, summary.trusted]
  }), malformed.map(() => ['malformed', false]))

  const grounded = summarizeEvidenceChain({
    evidenceState: 'available', status: 'grounded', sources: [{ evidenceId: 'ev_1' }]
  })
  assert.equal(grounded.state, 'available')
  assert.equal(grounded.trusted, true)

  const modelOnly = summarizeEvidenceChain({
    evidenceState: 'available', status: 'model_only', sources: []
  })
  assert.equal(modelOnly.state, 'available')
  assert.equal(modelOnly.trusted, true)
})

test('interaction requests whitelist actions, encode identities and expose a stable dedupe key', async () => {
  const { buildResourceInteractionRequest } = await helper
  const request = buildResourceInteractionRequest('session/a', 42, 'res #1', 'download')
  assert.deepEqual(request, {
    url: '/api/ai/leader/sessions/session%2Fa/messages/42/resources/res%20%231/interactions',
    method: 'POST',
    data: { action: 'download' },
    dedupeKey: 'session/a:42:res #1:download'
  })
  for (const action of ['view', 'open', 'download', 'preview', 'follow_up', 'dismiss']) {
    assert.equal(buildResourceInteractionRequest('s', 1, 'r', action).data.action, action)
  }
  assert.equal(buildResourceInteractionRequest('s', 1, 'r', 'delete'), null)
  assert.equal(buildResourceInteractionRequest('', 1, 'r', 'open'), null)
})

test('hit counts never double count Java and document totals', async () => {
  const { countAssistantHits } = await helper
  assert.equal(countAssistantHits({ matchedResults: [{}, {}], retrievalMeta: { javaBackendCount: 9, documentCount: 8 } }), 2)
  assert.equal(countAssistantHits({ matchedResults: [], retrievalMeta: { javaBackendCount: 9, documentCount: 8 } }), 0)
  assert.equal(countAssistantHits({ retrievalMeta: { javaBackendCount: 9, documentCount: 8 } }), 9)
  assert.equal(countAssistantHits({ retrievalMeta: { javaBackendCount: -1, documentCount: '4' } }), 4)
})

test('message merge accepts authoritative empty collections and stable server identity on completion', async () => {
  const { mergeAssistantMessage } = await helper
  const previous = {
    id: 'local-1', content: '流式', matchedResults: [{ id: 1 }],
    retrievalMeta: { documentCount: 1 }, trace: [{ stage: 'search' }],
    attachments: [{ name: 'stale.docx' }], resources: [{ id: 'stale' }],
    evidenceChain: { evidenceState: 'available', status: 'grounded', sources: [{}] }
  }
  const merged = mergeAssistantMessage(previous, {
    messageId: 88, answer: '最终回答', matchedResults: [], retrievalMeta: {}, trace: [],
    attachments: [], resources: [], evidenceChain: {}
  })
  assert.equal(merged.messageId, 88)
  assert.equal(merged.id, 'local-1')
  assert.equal(merged.content, '最终回答')
  assert.deepEqual(merged.matchedResults, [])
  assert.deepEqual(merged.retrievalMeta, {})
  assert.deepEqual(merged.trace, [])
  assert.deepEqual(merged.attachments, [])
  assert.deepEqual(merged.resources, [])
  assert.deepEqual(merged.evidenceChain, {})

  const invalidIncoming = mergeAssistantMessage(previous, {
    matchedResults: {}, retrievalMeta: [], trace: {}, attachments: {}, resources: 'bad', evidenceChain: []
  })
  assert.deepEqual(invalidIncoming.matchedResults, previous.matchedResults)
  assert.deepEqual(invalidIncoming.retrievalMeta, previous.retrievalMeta)
  assert.deepEqual(invalidIncoming.trace, previous.trace)
  assert.deepEqual(invalidIncoming.attachments, previous.attachments)
  assert.deepEqual(invalidIncoming.resources, [{ id: 'stale', messageId: 'local-1' }])
  assert.deepEqual(invalidIncoming.evidenceChain, previous.evidenceChain)

  const unchanged = mergeAssistantMessage({ content: '  保留格式\n' }, { answer: '' })
  assert.equal(unchanged.content, '  保留格式\n')
})

test('downloadAssistantResource infers auth only for API strings and rejects non-2xx responses', async () => {
  const { buildAssistantDownloadOptions } = await helper
  const calls = []
  let nextResponse = { statusCode: 200, tempFilePath: '/tmp/downloaded' }
  globalThis.uni = {
    downloadFile(options) {
      calls.push(options)
      options.success(nextResponse)
    }
  }
  try {
    const api = await loadAiApi({ buildAssistantDownloadOptions, token: 'secret-token' })
    await api.downloadAssistantResource('/api/ai/leader/sessions/s/messages/42/exports/file.pdf')
    assert.deepEqual(calls.at(-1).header, { Authorization: 'Bearer secret-token' })

    await assert.rejects(
      api.downloadAssistantResource('/api/profile/radar/my'),
      /资源地址无效/
    )

    await api.downloadAssistantResource('/uploads/public.png')
    assert.deepEqual(calls.at(-1).header, {})

    await api.downloadAssistantResource('https://cdn.example.edu/public.pdf', {
      approvedHosts: ['cdn.example.edu']
    })
    assert.deepEqual(calls.at(-1).header, {})

    nextResponse = { statusCode: 403, tempFilePath: '/tmp/must-not-resolve' }
    await assert.rejects(
      api.downloadAssistantResource('/uploads/rejected.pdf'),
      /资源下载失败: 403/
    )
  } finally {
    delete globalThis.uni
    delete globalThis.__assistantApiTestMocks
  }
})

test('ai api exposes strict interaction and authenticated download helpers', () => {
  const source = apiSource
  assert.match(source, /export function submitAssistantResourceInteraction\s*\(/)
  assert.match(source, /sessions\/\$\{encodeURIComponent\(sessionId\)\}\/messages\/\$\{encodeURIComponent\(messageId\)\}\/resources\/\$\{encodeURIComponent\(resourceId\)\}\/interactions/)
  assert.match(source, /data:\s*\{\s*action\s*\}/)
  assert.match(source, /export function downloadAssistantResource\s*\(/)
  assert.match(source, /uni\.downloadFile\s*\(/)
  assert.match(source, /Authorization:\s*`Bearer \$\{token\}`/)
})
