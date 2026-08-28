<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'

import { API_BASE_URL } from '../api/request'
import { getToken } from '../utils/auth'

const props = defineProps({
  item: { type: Object, required: true },
  message: { type: Object, default: null },
  sessionId: { type: String, default: '' },
})

const emit = defineEmits(['open', 'download'])

const previewSrc = ref('')
const status = ref('idle')
let objectUrl = ''

function attachmentName(item) {
  return item?.name || item?.fileName || item?.title || '生成图片'
}

function buildExportUrl(item, message, sessionId) {
  const direct = item?.previewUrl || item?.url || item?.sourceUrl || item?.downloadUrl || ''
  if (direct) {
    if (direct.startsWith('http://') || direct.startsWith('https://')) return direct
    if (direct.startsWith('/')) return `${API_BASE_URL}${direct}`
    return `${API_BASE_URL}/${direct}`
  }
  const storageKey = String(item?.storageKey || '').trim()
  const resolvedSessionId = String(
    sessionId || message?.exportSessionId || message?.sessionId || '',
  ).trim()
  const messageId = message?.exportMessageId || message?.messageId
  if (!storageKey || !resolvedSessionId || !messageId) return ''
  return `${API_BASE_URL}/api/ai/leader/sessions/${encodeURIComponent(resolvedSessionId)}/messages/${messageId}/exports/${encodeURIComponent(storageKey)}`
}

function revokeObjectUrl() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
}

async function loadPreview() {
  revokeObjectUrl()
  previewSrc.value = ''
  const source = buildExportUrl(props.item, props.message, props.sessionId)
  if (!source) {
    status.value = 'pending'
    return
  }
  status.value = 'loading'
  try {
    const token = getToken()
    const response = await fetch(source, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) throw new Error(`读取图片失败（${response.status}）`)
    const blob = await response.blob()
    if (!blob.type.startsWith('image/')) {
      throw new Error('附件不是有效图片')
    }
    objectUrl = URL.createObjectURL(blob)
    previewSrc.value = objectUrl
    status.value = 'ready'
  } catch {
    status.value = 'error'
  }
}

watch(
  () => [
    props.item?.storageKey,
    props.item?.url,
    props.message?.exportMessageId,
    props.message?.messageId,
    props.sessionId,
  ],
  () => { void loadPreview() },
  { immediate: true },
)

onBeforeUnmount(revokeObjectUrl)
</script>

<template>
  <div class="chat-image-attachment">
    <div v-if="status === 'loading' || status === 'pending'" class="chat-image-attachment__placeholder">
      <span>{{ status === 'pending' ? '图片准备中…' : '图片加载中…' }}</span>
    </div>
    <div v-else-if="status === 'error'" class="chat-image-attachment__placeholder is-error">
      <span>图片预览失败，可尝试打开或下载</span>
    </div>
    <img
      v-else
      class="chat-image-attachment__image"
      :src="previewSrc"
      :alt="attachmentName(item)"
      loading="lazy"
      @click="emit('open')"
    />
    <div class="chat-image-attachment__actions">
      <button type="button" @click="emit('open')">打开</button>
      <button type="button" @click="emit('download')">下载</button>
    </div>
  </div>
</template>

<style scoped>
.chat-image-attachment {
  width: min(360px, 100%);
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
}

.chat-image-attachment__placeholder {
  display: grid;
  min-height: 180px;
  place-items: center;
  padding: 20px;
  color: var(--muted);
  background: linear-gradient(180deg, var(--surface-soft), var(--surface));
  font-size: 12px;
  text-align: center;
}

.chat-image-attachment__placeholder.is-error {
  color: #b45353;
}

.chat-image-attachment__image {
  display: block;
  width: 100%;
  max-height: 420px;
  object-fit: contain;
  background: #f4f6f8;
  cursor: zoom-in;
}

.chat-image-attachment__actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  padding: 6px 8px;
  border-top: 1px solid var(--line);
}

.chat-image-attachment__actions button {
  min-height: 28px;
  padding: 0 10px;
  border-radius: 6px;
  color: var(--primary);
  background: transparent;
  font-size: 11px;
}

.chat-image-attachment__actions button:hover {
  background: var(--primary-soft);
}
</style>
