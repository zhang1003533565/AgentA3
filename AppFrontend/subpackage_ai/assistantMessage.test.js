const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const helperPath = join(__dirname, 'assistantMessage.js')
const helperSource = readFileSync(helperPath, 'utf8')
const helper = import(`data:text/javascript;base64,${Buffer.from(helperSource).toString('base64')}`)

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

test('legacy attachments without server identities deduplicate by a stable non-index key', async () => {
  const { normalizeAssistantResources } = await helper
  const resources = normalizeAssistantResources({
    attachments: [
      { name: '同名说明', type: 'file' },
      { name: '同名说明', type: 'file' },
      { name: '另一份说明', type: 'file' }
    ]
  })
  assert.equal(resources.length, 2)
  assert.equal(new Set(resources.map((item) => item.key)).size, 2)
  assert.ok(resources.every((item) => /^legacy:[0-9a-f]{8}$/.test(item.key)))
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

test('download options attach local Bearer tokens only to authenticated Java resources', async () => {
  const { buildAssistantDownloadOptions } = await helper
  assert.deepEqual(buildAssistantDownloadOptions({
    url: '/api/ai/leader/file', authScope: 'session_owner'
  }, { baseUrl: 'http://localhost:8080', token: 'secret-token' }), {
    url: 'http://localhost:8080/api/ai/leader/file',
    header: { Authorization: 'Bearer secret-token' }
  })
  assert.equal(buildAssistantDownloadOptions({
    url: '/api/ai/leader/file', authScope: 'session_owner'
  }, { baseUrl: 'http://localhost:8080', token: '' }), null)
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
    const summary = summarizeEvidenceChain({ status: 'grounded', evidenceState: state, sources: {} })
    assert.equal(summary.state, state)
    assert.equal(summary.sourceCount, 0)
    assert.equal(summary.trusted, false)
  }
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

test('message merge retains search evidence and stable server identity across stream completion', async () => {
  const { mergeAssistantMessage } = await helper
  const previous = {
    id: 'local-1', content: '流式', matchedResults: [{ id: 1 }],
    retrievalMeta: { documentCount: 1 }, trace: [{ stage: 'search' }]
  }
  const merged = mergeAssistantMessage(previous, {
    messageId: 88, answer: '最终回答', matchedResults: [], retrievalMeta: {}, trace: [],
    resources: [{ id: 'res_1', kind: 'document', deliveryType: 'document' }],
    evidenceChain: { evidenceState: 'available', status: 'grounded', sources: [] }
  })
  assert.equal(merged.messageId, 88)
  assert.equal(merged.id, 'local-1')
  assert.equal(merged.content, '最终回答')
  assert.deepEqual(merged.matchedResults, previous.matchedResults)
  assert.deepEqual(merged.retrievalMeta, previous.retrievalMeta)
  assert.deepEqual(merged.trace, previous.trace)
  assert.equal(merged.resources[0].messageId, 88)
  assert.equal(merged.evidenceChain.evidenceState, 'available')

  const unchanged = mergeAssistantMessage({ content: '  保留格式\n' }, { answer: '' })
  assert.equal(unchanged.content, '  保留格式\n')
})

test('ai api exposes strict interaction and authenticated download helpers', () => {
  const source = readFileSync(join(__dirname, '../api/ai.js'), 'utf8')
  assert.match(source, /export function submitAssistantResourceInteraction\s*\(/)
  assert.match(source, /sessions\/\$\{encodeURIComponent\(sessionId\)\}\/messages\/\$\{encodeURIComponent\(messageId\)\}\/resources\/\$\{encodeURIComponent\(resourceId\)\}\/interactions/)
  assert.match(source, /data:\s*\{\s*action\s*\}/)
  assert.match(source, /export function downloadAssistantResource\s*\(/)
  assert.match(source, /uni\.downloadFile\s*\(/)
  assert.match(source, /Authorization:\s*`Bearer \$\{token\}`/)
})
