import { computed, ref, watch } from 'vue'

import { streamSse } from '../api/sse'
import { API_BASE_URL } from '../api/request'
import { getToken, getUserInfo } from '../utils/auth'
import { getCurrentDisplayName } from '../utils/meetingUser'

const MEANINGLESS_PATTERNS = [
  /^嗯.*$/i,
  /^啊.*$/i,
  /^哦.*$/i,
  /^好$/i,
  /^行$/i,
  /^我同意$/i,
  /^没问题$/i,
  /^明白了$/i,
  /^对$/i,
  /^是的$/i,
  /^是$/i,
  /^不是$/i,
]

function computeTextOverlap(text1, text2) {
  if (!text1 || !text2) return 0
  const chars1 = new Set(text1.split(''))
  const chars2 = new Set(text2.split(''))
  let overlap = 0
  chars1.forEach((char) => {
    if (chars2.has(char)) overlap += 1
  })
  return overlap / Math.max(chars1.size, chars2.size)
}

function hasMeaningfulTranscript(line) {
  if (!line || typeof line !== 'string') return false
  const text = line.trim()
  if (!text) return false
  if (MEANINGLESS_PATTERNS.some((pattern) => pattern.test(text))) return false
  if (text.length > 3) {
    return /[0-9]/.test(text) || /[年月日时分秒]/.test(text)
  }
  return false
}

export function useMeetingAsr(options) {
  const {
    sessionId,
    meetingTitle,
    isHost,
    micEnabled,
    enabled,
  } = options

  const asrItems = ref([])
  const aiSummaryItems = ref([])
  const subtitleRecords = ref([])
  const agentEnabled = ref(false)
  const panelMode = ref('danmaku')
  const asrStatusText = ref('等待连接')
  const aiSummaryStatusText = ref('等待发言')
  const asrReconnectVisible = ref(false)

  let asrSocket = null
  let asrBrowserStream = null
  let asrAudioContext = null
  let asrAudioSource = null
  let asrAudioProcessor = null
  let asrAudioSilence = null
  let asrAudioWorkletUrl = ''
  let asrPcmBuffer = null
  let asrRecording = false
  let asrSocketReady = false
  let asrServiceReady = false
  let asrManualClosing = false
  let asrReconnectTimer = null
  let asrReconnectAttempts = 0
  let asrLastError = ''
  let asrSeq = 0
  let aiSummarySeq = 0
  let aiSummaryHistoryLoaded = false
  let aiSummaryHistoryRetryTimer = null
  let aiSummaryTimer = null
  let summaryWindowTimer = null
  let aiSummaryRunning = false
  let aiSummaryPending = false
  let lastSummaryInput = ''
  let lastSummaryOutput = ''
  let lastSummaryErrorInput = ''
  let summaryWindowStart = Date.now()
  let lastProcessedTranscriptIndex = 0
  let hasShownEmptyNotice = false
  const meetingTranscriptLines = []

  const livePanelTitle = computed(() => (agentEnabled.value ? 'AI 实时总结' : '语音弹幕'))
  const livePanelStatus = computed(() => (agentEnabled.value ? aiSummaryStatusText.value : asrStatusText.value))
  const latestSubtitleLine = computed(() => {
    const finals = asrItems.value.filter((item) => item.isFinal && item.text)
    const latest = finals[finals.length - 1]
    if (latest) return `${latest.speaker}：${latest.text}`
    const partial = asrItems.value.find((item) => !item.isFinal && item.text)
    return partial ? `${partial.speaker}：${partial.text}` : ''
  })

  function getCurrentUserId() {
    const user = getUserInfo() || {}
    const storedId = user.userId ?? user.id
    if (storedId != null && storedId !== '') return String(storedId)
    return ''
  }

  function isSelfSpeaker(payload) {
    const currentId = getCurrentUserId()
    const currentName = getCurrentDisplayName()
    if (currentId && payload.speakerUserId && String(currentId) === String(payload.speakerUserId)) {
      return true
    }
    return !!currentName && payload.speaker === currentName
  }

  function buildAsrSocketUrl(token) {
    const base = API_BASE_URL.replace(/^http/i, (match) => (match.toLowerCase() === 'https' ? 'wss' : 'ws')).replace(/\/$/, '')
    return `${base}/api/meetings/${encodeURIComponent(sessionId.value)}/asr/stream?token=${encodeURIComponent(token)}`
  }

  function sendSocketJson(payload) {
    if (!asrSocket || asrSocket.readyState !== WebSocket.OPEN) return
    asrSocket.send(JSON.stringify(payload))
  }

  function requestAiSummaryHistory() {
    sendSocketJson({ type: 'ai_summary_history_request' })
  }

  function scheduleAiSummaryHistoryRetry() {
    clearAiSummaryHistoryRetryTimer()
    aiSummaryHistoryRetryTimer = setTimeout(() => {
      if (!aiSummaryHistoryLoaded && asrSocketReady) requestAiSummaryHistory()
    }, 5000)
  }

  function clearAiSummaryHistoryRetryTimer() {
    if (aiSummaryHistoryRetryTimer) {
      clearTimeout(aiSummaryHistoryRetryTimer)
      aiSummaryHistoryRetryTimer = null
    }
  }

  function sendAiSummaryToBackend(id, text, time) {
    if (!id || !text?.trim()) return
    sendSocketJson({ type: 'ai_summary', id, text: text.trim(), time })
  }

  function upsertAsrItem(item) {
    const text = (item.text || '').trim()
    if (!text) return
    const partialId = `partial-${item.speaker}`
    if (!item.isFinal) {
      const existing = asrItems.value.find((entry) => entry.id === partialId)
      if (existing) existing.text = text
      else asrItems.value.push({ ...item, id: partialId })
    } else {
      asrItems.value = asrItems.value.filter((entry) => entry.id !== partialId)
      asrItems.value.push({ ...item, id: `asr-${Date.now()}-${asrSeq += 1}` })
    }
    if (asrItems.value.length > 12) {
      asrItems.value = asrItems.value.slice(asrItems.value.length - 12)
    }
  }

  function appendTranscriptLine(item) {
    const text = (item.text || '').trim()
    if (!text) return
    meetingTranscriptLines.push(`${item.speaker}：${text}`)
    if (meetingTranscriptLines.length > 80) {
      meetingTranscriptLines.splice(0, meetingTranscriptLines.length - 80)
    }
    const now = new Date()
    const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
    subtitleRecords.value.push({
      speaker: item.speaker || '参会成员',
      text,
      time,
      isSelf: item.isSelf,
      timestamp: Date.now(),
    })
    if (subtitleRecords.value.length > 200) {
      subtitleRecords.value = subtitleRecords.value.slice(-200)
    }
    persistSubtitleRecords()
  }

  function persistSubtitleRecords() {
    if (!sessionId.value) return
    try {
      localStorage.setItem(`meeting_danmaku_${sessionId.value}`, JSON.stringify(subtitleRecords.value.slice(-200)))
    } catch {
      /* ignore */
    }
  }

  function restoreSubtitleRecords() {
    if (!sessionId.value) return
    try {
      const raw = localStorage.getItem(`meeting_danmaku_${sessionId.value}`)
      const list = raw ? JSON.parse(raw) : []
      if (Array.isArray(list) && list.length) {
        subtitleRecords.value = list.filter((item) => item && (item.text || item.speaker)).slice(-200)
      }
    } catch {
      subtitleRecords.value = []
    }
  }

  function restoreAiSummaryRecords() {
    if (!sessionId.value) return
    try {
      const raw = localStorage.getItem(`meeting_ai_summary_${sessionId.value}`)
      const list = raw ? JSON.parse(raw) : []
      if (Array.isArray(list) && list.length) {
        aiSummaryItems.value = list.slice(-20)
        const last = aiSummaryItems.value[aiSummaryItems.value.length - 1]
        if (last?.text) lastSummaryOutput = last.text
      }
    } catch {
      aiSummaryItems.value = []
    }
  }

  function persistAiSummaryRecords() {
    if (!sessionId.value) return
    try {
      localStorage.setItem(
        `meeting_ai_summary_${sessionId.value}`,
        JSON.stringify(aiSummaryItems.value.slice(-20).map((item) => ({ id: item.id, text: item.text, time: item.time }))),
      )
    } catch {
      /* ignore */
    }
  }

  function mergeAiSummaryHistory(items) {
    const existingIds = new Set(aiSummaryItems.value.map((item) => item.id))
    const merged = [...aiSummaryItems.value]
    items.forEach((item) => {
      if (item?.id && !existingIds.has(item.id)) {
        merged.push({ id: item.id, text: item.text || '', time: item.time || '' })
        existingIds.add(item.id)
      }
    })
    aiSummaryItems.value = merged.length > 20 ? merged.slice(-20) : merged
    const lastItem = aiSummaryItems.value[aiSummaryItems.value.length - 1]
    if (lastItem?.text) lastSummaryOutput = lastItem.text
    persistAiSummaryRecords()
  }

  function createAiSummaryItem(text) {
    const now = new Date()
    const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
    const item = { id: `ai-summary-${Date.now()}-${aiSummarySeq += 1}`, text, time, _createdAt: Date.now() }
    aiSummaryItems.value.push(item)
    if (aiSummaryItems.value.length > 20) {
      aiSummaryItems.value = aiSummaryItems.value.slice(-20)
    }
    persistAiSummaryRecords()
    return item
  }

  function updateAiSummaryItem(id, text) {
    const item = aiSummaryItems.value.find((entry) => entry.id === id)
    if (item) item.text = text
    persistAiSummaryRecords()
  }

  function handleAsrMessage(raw) {
    let payload = null
    try {
      payload = typeof raw === 'string' ? JSON.parse(raw) : raw
    } catch {
      return
    }
    if (!payload) return

    if (payload.type === 'asr_ready') {
      asrServiceReady = true
      asrStatusText.value = micEnabled.value ? (asrRecording ? '正在识别' : '识别已连接') : '已静音'
      if (micEnabled.value) startAsrRecording()
      return
    }
    if (payload.type === 'asr_error') {
      asrLastError = payload.message || '识别异常'
      asrStatusText.value = asrLastError
      asrServiceReady = false
      return
    }
    if (payload.type === 'asr_result') {
      const item = {
        speakerUserId: payload.speakerUserId || '',
        speaker: payload.speaker || '参会成员',
        text: payload.text || '',
        isFinal: !!payload.isFinal,
        isSelf: isSelfSpeaker(payload),
      }
      upsertAsrItem(item)
      if (item.isFinal) appendTranscriptLine(item)
      return
    }
    if (payload.type === 'danmaku') {
      const currentId = getCurrentUserId()
      if (currentId && payload.speakerUserId && String(currentId) === String(payload.speakerUserId)) return
      const speaker = payload.speaker || '参会成员'
      const text = (payload.text || '').trim()
      if (!text) return
      const now = new Date()
      subtitleRecords.value.push({
        speaker,
        text,
        time: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
        isSelf: false,
        isDanmaku: true,
        timestamp: Date.now(),
      })
      if (subtitleRecords.value.length > 200) subtitleRecords.value = subtitleRecords.value.slice(-200)
      persistSubtitleRecords()
      return
    }
    if (payload.type === 'ai_summary_history') {
      clearAiSummaryHistoryRetryTimer()
      if (Array.isArray(payload.items) && payload.items.length) {
        if (!isHost.value && !agentEnabled.value) agentEnabled.value = true
        mergeAiSummaryHistory(payload.items)
        if (agentEnabled.value) aiSummaryStatusText.value = '历史重点已加载'
      }
      aiSummaryHistoryLoaded = true
      return
    }
    if (payload.type === 'ai_summary') {
      const incomingId = payload.id || ''
      const incomingText = (payload.text || '').trim()
      const incomingTime = payload.time || ''
      if (!incomingId || !incomingText) return
      if (!isHost.value && !agentEnabled.value) {
        agentEnabled.value = true
        aiSummaryStatusText.value = '重点已更新'
      }
      if (aiSummaryItems.value.some((item) => item.id === incomingId)) return
      const recentCutoff = Date.now() - 30000
      let duplicateFound = false
      for (let index = aiSummaryItems.value.length - 1; index >= 0; index -= 1) {
        const local = aiSummaryItems.value[index]
        if (local._createdAt && local._createdAt >= recentCutoff) {
          if (computeTextOverlap(incomingText, local.text) > 0.5) {
            local.id = incomingId
            local.text = incomingText
            local.time = incomingTime
            delete local._createdAt
            duplicateFound = true
            break
          }
        }
      }
      if (!duplicateFound) {
        aiSummaryItems.value.push({ id: incomingId, text: incomingText, time: incomingTime })
        if (aiSummaryItems.value.length > 20) {
          aiSummaryItems.value = aiSummaryItems.value.slice(-20)
        }
      }
      persistAiSummaryRecords()
    }
  }

  function floatTo16kPcm(input, inputSampleRate) {
    const outputSampleRate = 16000
    const ratio = inputSampleRate / outputSampleRate
    const outputLength = Math.floor(input.length / ratio)
    if (outputLength <= 0) return null
    const buffer = new ArrayBuffer(outputLength * 2)
    const view = new DataView(buffer)
    for (let index = 0; index < outputLength; index += 1) {
      const sampleIndex = Math.floor(index * ratio)
      const sample = Math.max(-1, Math.min(1, input[sampleIndex]))
      view.setInt16(index * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
    }
    return buffer
  }

  function sendAlignedPcmFrames(pcm) {
    if (!asrSocketReady || !asrServiceReady || !asrSocket) return
    const frameSize = 1280
    const incoming = new Uint8Array(pcm)
    const pending = asrPcmBuffer || new Uint8Array(0)
    const merged = new Uint8Array(pending.length + incoming.length)
    merged.set(pending)
    merged.set(incoming, pending.length)
    let offset = 0
    while (offset + frameSize <= merged.length) {
      asrSocket.send(merged.slice(offset, offset + frameSize))
      offset += frameSize
    }
    asrPcmBuffer = merged.slice(offset)
  }

  function cleanupAudioWorkletUrl() {
    if (asrAudioWorkletUrl && typeof URL !== 'undefined') {
      try {
        URL.revokeObjectURL(asrAudioWorkletUrl)
      } catch {
        /* ignore */
      }
    }
    asrAudioWorkletUrl = ''
  }

  function stopBrowserAsrRecording() {
    if (asrAudioProcessor) {
      try {
        if (asrAudioProcessor.port) asrAudioProcessor.port.onmessage = null
        asrAudioProcessor.disconnect()
        asrAudioProcessor.onaudioprocess = null
      } catch {
        /* ignore */
      }
    }
    if (asrAudioSilence) {
      try {
        asrAudioSilence.disconnect()
      } catch {
        /* ignore */
      }
    }
    if (asrAudioSource) {
      try {
        asrAudioSource.disconnect()
      } catch {
        /* ignore */
      }
    }
    if (asrAudioContext) {
      try {
        asrAudioContext.close()
      } catch {
        /* ignore */
      }
    }
    if (asrBrowserStream) {
      asrBrowserStream.getTracks().forEach((track) => track.stop())
    }
    cleanupAudioWorkletUrl()
    asrPcmBuffer = null
    asrAudioProcessor = null
    asrAudioSilence = null
    asrAudioSource = null
    asrAudioContext = null
    asrBrowserStream = null
  }

  async function connectBrowserAudioProcessor() {
    if (await connectAudioWorkletProcessor()) return
    asrAudioProcessor = asrAudioContext.createScriptProcessor(2048, 1, 1)
    asrAudioProcessor.onaudioprocess = (event) => {
      sendBrowserPcmFrame(event.inputBuffer.getChannelData(0))
    }
    asrAudioSource.connect(asrAudioProcessor)
    asrAudioProcessor.connect(asrAudioContext.destination)
  }

  async function connectAudioWorkletProcessor() {
    if (
      !asrAudioContext?.audioWorklet
      || typeof AudioWorkletNode === 'undefined'
      || typeof Blob === 'undefined'
    ) {
      return false
    }
    try {
      const workletCode = `
        class AsrPcmProcessor extends AudioWorkletProcessor {
          process(inputs) {
            const input = inputs[0]
            if (input && input[0]) this.port.postMessage(input[0].slice(0))
            return true
          }
        }
        registerProcessor('asr-pcm-processor', AsrPcmProcessor)
      `
      asrAudioWorkletUrl = URL.createObjectURL(new Blob([workletCode], { type: 'application/javascript' }))
      await asrAudioContext.audioWorklet.addModule(asrAudioWorkletUrl)
      asrAudioProcessor = new AudioWorkletNode(asrAudioContext, 'asr-pcm-processor')
      asrAudioProcessor.port.onmessage = (event) => sendBrowserPcmFrame(event.data)
      asrAudioSilence = asrAudioContext.createGain()
      asrAudioSilence.gain.value = 0
      asrAudioSource.connect(asrAudioProcessor)
      asrAudioProcessor.connect(asrAudioSilence)
      asrAudioSilence.connect(asrAudioContext.destination)
      return true
    } catch {
      cleanupAudioWorkletUrl()
      asrAudioProcessor = null
      asrAudioSilence = null
      return false
    }
  }

  function sendBrowserPcmFrame(input) {
    const sampleRate = asrAudioContext?.sampleRate
    if (!input || !sampleRate) return
    const pcm = floatTo16kPcm(input, sampleRate)
    if (pcm) sendAlignedPcmFrames(pcm)
  }

  async function startBrowserAsrRecording() {
    if (asrRecording || !asrSocketReady || !asrServiceReady || !asrSocket) return
    if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
      asrStatusText.value = '当前浏览器不支持录音，仅接收字幕'
      return
    }
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    if (!AudioContextClass) {
      asrStatusText.value = '当前浏览器不支持音频采集'
      return
    }
    try {
      asrBrowserStream = await navigator.mediaDevices.getUserMedia({ audio: true })
      asrAudioContext = new AudioContextClass()
      asrAudioSource = asrAudioContext.createMediaStreamSource(asrBrowserStream)
      await connectBrowserAudioProcessor()
      asrRecording = true
      asrStatusText.value = '正在识别'
    } catch {
      asrRecording = false
      asrStatusText.value = '浏览器录音权限未开启'
      stopBrowserAsrRecording()
    }
  }

  function startAsrRecording() {
    if (asrRecording || !asrSocketReady || !asrServiceReady || !asrSocket || !micEnabled.value) return
    startBrowserAsrRecording()
  }

  function stopAsrRecording() {
    asrManualClosing = false
    clearAsrReconnectTimer()
    if (asrSocketReady && asrSocket) {
      if (asrPcmBuffer?.length) {
        const frameSize = 1280
        const frame = new Uint8Array(frameSize)
        frame.set(asrPcmBuffer)
        asrSocket.send(frame)
      }
      sendSocketJson({ stop: true })
    }
    stopBrowserAsrRecording()
    asrRecording = false
    asrStatusText.value = micEnabled.value ? '识别已连接' : '已静音'
  }

  function clearAsrReconnectTimer() {
    if (asrReconnectTimer) {
      clearTimeout(asrReconnectTimer)
      asrReconnectTimer = null
    }
  }

  function scheduleAsrReconnect() {
    if (asrReconnectAttempts >= 3) {
      asrStatusText.value = asrLastError || '识别已断开，请手动重连'
      asrReconnectVisible.value = true
      return
    }
    clearAsrReconnectTimer()
    asrReconnectAttempts += 1
    const delay = Math.min(1000 * asrReconnectAttempts, 3000)
    asrReconnectTimer = setTimeout(() => {
      asrStatusText.value = `正在重连识别 (${asrReconnectAttempts}/3)`
      openAsrSocket()
    }, delay)
  }

  function openAsrSocket() {
    if (asrSocket || !sessionId.value) return
    clearAsrReconnectTimer()
    const token = getToken()
    if (!token) {
      asrStatusText.value = '请先登录后使用识别'
      return
    }
    asrManualClosing = false
    asrLastError = ''
    asrReconnectVisible.value = false
    asrSocket = new WebSocket(buildAsrSocketUrl(token))
    asrSocket.binaryType = 'arraybuffer'
    asrSocket.onopen = () => {
      asrSocketReady = true
      asrReconnectAttempts = 0
      asrStatusText.value = micEnabled.value ? '识别服务连接中' : '已静音'
      aiSummaryHistoryLoaded = false
      requestAiSummaryHistory()
      scheduleAiSummaryHistoryRetry()
    }
    asrSocket.onmessage = (event) => {
      if (typeof event.data === 'string') handleAsrMessage(event.data)
      else if (event.data instanceof ArrayBuffer) handleAsrMessage(new TextDecoder().decode(event.data))
    }
    asrSocket.onerror = () => {
      asrLastError = '识别连接异常'
      asrStatusText.value = asrLastError
      asrSocketReady = false
    }
    asrSocket.onclose = () => {
      clearAiSummaryHistoryRetryTimer()
      const shouldReconnect = !asrManualClosing && !!sessionId.value && enabled.value
      asrSocketReady = false
      asrServiceReady = false
      asrSocket = null
      asrRecording = false
      stopBrowserAsrRecording()
      if (shouldReconnect) {
        asrStatusText.value = asrLastError || '识别已断开，正在重连'
        scheduleAsrReconnect()
      } else {
        asrStatusText.value = asrLastError || '识别已停止'
      }
    }
  }

  function initAsr() {
    if (!sessionId.value) {
      asrStatusText.value = '会议创建后开始识别'
      return
    }
    restoreSubtitleRecords()
    restoreAiSummaryRecords()
    asrStatusText.value = '正在连接识别'
    openAsrSocket()
  }

  function closeAsr() {
    clearAiSummaryTimer()
    stopSummaryWindowTimer()
    clearAiSummaryHistoryRetryTimer()
    asrManualClosing = true
    clearAsrReconnectTimer()
    if (asrSocket) {
      try {
        sendSocketJson({ stop: true })
        asrSocket.close()
      } catch {
        /* ignore */
      }
    }
    stopBrowserAsrRecording()
    asrRecording = false
    asrSocketReady = false
    asrServiceReady = false
    asrSocket = null
  }

  function reconnectAsr() {
    asrLastError = ''
    asrStatusText.value = '正在重新连接'
    asrManualClosing = false
    clearAsrReconnectTimer()
    if (asrSocket) {
      try {
        asrSocket.close()
      } catch {
        /* ignore */
      }
      asrSocket = null
    }
    openAsrSocket()
  }

  function hasSummarySourceText() {
    return meetingTranscriptLines.length > 0 || asrItems.value.some((item) => item.text?.trim())
  }

  function buildSummaryInput() {
    const transcript = meetingTranscriptLines.slice(-40).join('\n')
    if (!transcript.trim()) return ''
    const lines = [
      `会议主题：${meetingTitle.value || '未命名会议'}`,
      '',
      '你是会中实时增量摘要智能体。只输出上一轮摘要之后新增或变化的内容。',
      '如果本轮没有重要新增内容，直接输出："暂无新的关键进展。"',
    ]
    if (lastSummaryOutput) {
      lines.push('', '## 上一轮已输出的摘要（禁止重复）', lastSummaryOutput, '', '请只输出本轮新增或变化的信息。', '')
    }
    lines.push('## 当前实时转写内容', transcript)
    const input = lines.join('\n')
    return input.length > 3900 ? input.slice(input.length - 3900) : input
  }

  function resetSummaryWindow() {
    summaryWindowStart = Date.now()
    lastProcessedTranscriptIndex = meetingTranscriptLines.length
    hasShownEmptyNotice = false
  }

  function checkIfShouldSummarize() {
    if (!agentEnabled.value || !isHost.value) return
    const windowDuration = Date.now() - summaryWindowStart
    if (windowDuration < 120000) return
    const transcriptFromIndex = meetingTranscriptLines.slice(lastProcessedTranscriptIndex)
    if (!transcriptFromIndex.length) {
      if (!hasShownEmptyNotice) {
        hasShownEmptyNotice = true
        createAiSummaryItem('暂无新的关键进展。')
        aiSummaryStatusText.value = '暂无新的关键进展'
        resetSummaryWindow()
      }
      return
    }
    if (!transcriptFromIndex.some(hasMeaningfulTranscript)) {
      if (!hasShownEmptyNotice) {
        hasShownEmptyNotice = true
        createAiSummaryItem('暂无新的关键进展。')
        aiSummaryStatusText.value = '暂无新的关键进展'
        resetSummaryWindow()
      }
      return
    }
    runAiSummary()
  }

  function startSummaryWindowTimer() {
    stopSummaryWindowTimer()
    summaryWindowTimer = setInterval(checkIfShouldSummarize, 10000)
  }

  function stopSummaryWindowTimer() {
    if (summaryWindowTimer) {
      clearInterval(summaryWindowTimer)
      summaryWindowTimer = null
    }
  }

  function clearAiSummaryTimer() {
    if (aiSummaryTimer) {
      clearTimeout(aiSummaryTimer)
      aiSummaryTimer = null
    }
  }

  async function runAiSummary() {
    if (!isHost.value || !agentEnabled.value || !hasSummarySourceText() || !sessionId.value) return
    const content = buildSummaryInput()
    if (!content || content === lastSummaryInput) {
      aiSummaryStatusText.value = '已是最新'
      return
    }
    if (aiSummaryRunning) {
      aiSummaryPending = true
      return
    }
    aiSummaryRunning = true
    aiSummaryPending = false
    aiSummaryStatusText.value = '正在提炼会议重点'
    lastSummaryInput = content
    lastSummaryErrorInput = ''
    const streamItem = createAiSummaryItem('正在提炼会议重点...')
    let streamText = ''
    let streamError = ''
    try {
      await streamSse('/api/llm/chat/stream', {
        sessionId: `meeting-${sessionId.value}-summary`,
        agentName: 'meeting_summary_agent',
        input: content,
      }, {
        onDelta: (delta) => {
          if (!delta) return
          streamText += delta
          updateAiSummaryItem(streamItem.id, streamText)
        },
        onDone: (payload) => {
          const answer = (payload?.answer || '').trim()
          if (answer && !streamText.trim()) {
            streamText = answer
            updateAiSummaryItem(streamItem.id, streamText)
          }
        },
        onError: (payload) => {
          streamError = payload?.message || '流式总结失败'
        },
      })
      if (streamError) throw new Error(streamError)
      if (streamText.trim()) {
        lastSummaryOutput = streamText.trim()
        aiSummaryStatusText.value = '重点已更新'
        sendAiSummaryToBackend(streamItem.id, streamText.trim(), streamItem.time)
        resetSummaryWindow()
      }
    } catch (error) {
      aiSummaryStatusText.value = '智能体暂不可用'
      if (lastSummaryErrorInput !== content) {
        lastSummaryErrorInput = content
        updateAiSummaryItem(streamItem.id, `会议总结智能体调用失败：${error.message || '请检查后台模型配置'}`)
      }
    } finally {
      aiSummaryRunning = false
      if (aiSummaryPending && agentEnabled.value) {
        aiSummaryTimer = setTimeout(runAiSummary, 0)
      }
    }
  }

  function setAgentSummary(enabled) {
    if (!isHost.value) return
    agentEnabled.value = !!enabled
    panelMode.value = enabled ? 'summary' : 'danmaku'
    if (agentEnabled.value) {
      aiSummaryStatusText.value = hasSummarySourceText() ? '准备提炼重点' : '等待发言'
      resetSummaryWindow()
      if (!aiSummaryHistoryLoaded) {
        requestAiSummaryHistory()
        scheduleAiSummaryHistoryRetry()
      }
      startSummaryWindowTimer()
    } else {
      stopSummaryWindowTimer()
      clearAiSummaryTimer()
      aiSummaryStatusText.value = '已关闭'
    }
  }

  function sendDanmaku(text) {
    const speaker = getCurrentDisplayName() || '参会成员'
    const trimmed = (text || '').trim()
    if (!trimmed) return
    const now = new Date()
    subtitleRecords.value.push({
      speaker,
      text: trimmed,
      time: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
      isSelf: true,
      isDanmaku: true,
      timestamp: Date.now(),
    })
    if (subtitleRecords.value.length > 200) subtitleRecords.value = subtitleRecords.value.slice(-200)
    persistSubtitleRecords()
    sendSocketJson({ type: 'danmaku', speaker, text: trimmed })
  }

  function syncRecordsFromDetail(records = []) {
    if (!Array.isArray(records) || !records.length) return
    records.forEach((record) => {
      const speaker = record.speakerName || record.speaker || '参会成员'
      const text = (record.content || record.text || '').trim()
      if (!text) return
      const exists = subtitleRecords.value.some((item) => item.speaker === speaker && item.text === text)
      if (!exists) {
        subtitleRecords.value.push({
          speaker,
          text,
          time: record.createTime ? String(record.createTime).slice(11, 16) : '',
          isSelf: false,
          timestamp: Date.now(),
        })
      }
    })
    if (subtitleRecords.value.length > 200) subtitleRecords.value = subtitleRecords.value.slice(-200)
  }

  watch(micEnabled, (value) => {
    if (!asrSocketReady) return
    if (value) startAsrRecording()
    else stopAsrRecording()
  })

  watch(enabled, (value) => {
    if (value) initAsr()
    else closeAsr()
  })

  return {
    asrItems,
    aiSummaryItems,
    subtitleRecords,
    agentEnabled,
    panelMode,
    asrStatusText,
    aiSummaryStatusText,
    asrReconnectVisible,
    livePanelTitle,
    livePanelStatus,
    latestSubtitleLine,
    initAsr,
    closeAsr,
    reconnectAsr,
    setAgentSummary,
    sendDanmaku,
    syncRecordsFromDetail,
  }
}
