<script setup>
import { computed, nextTick, onMounted, onUpdated, ref, watch } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  content: { type: String, default: '' },
  streaming: { type: Boolean, default: false },
})

const rootRef = ref(null)

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

const renderer = new marked.Renderer()

renderer.code = function renderCode({ text, lang }) {
  const language = String(lang || 'text').trim() || 'text'
  const encoded = encodeURIComponent(text || '')
  return `<div class="code-canvas">
    <div class="code-canvas-header">
      <span class="code-canvas-lang">${escapeHtml(language)}</span>
      <button type="button" class="code-canvas-copy" data-copy="${encoded}" aria-label="复制代码">复制</button>
    </div>
    <pre class="code-canvas-body"><code>${escapeHtml(text || '')}</code></pre>
  </div>`
}

renderer.codespan = function renderCodespan({ text }) {
  return `<code class="inline-code">${escapeHtml(text || '')}</code>`
}

marked.setOptions({
  renderer,
  breaks: true,
  gfm: true,
})

const html = computed(() => {
  const text = String(props.content || '').trim()
  if (!text) return ''
  return marked.parse(text)
})

async function copyCode(encoded) {
  const code = decodeURIComponent(encoded || '')
  await navigator.clipboard.writeText(code)
}

function bindCopyButtons() {
  const root = rootRef.value
  if (!root) return
  root.querySelectorAll('.code-canvas-copy:not([data-bound])').forEach((button) => {
    button.setAttribute('data-bound', 'true')
    button.addEventListener('click', async () => {
      const encoded = button.getAttribute('data-copy') || ''
      const original = button.textContent
      try {
        await copyCode(encoded)
        button.textContent = '已复制'
        window.setTimeout(() => {
          button.textContent = original
        }, 1500)
      } catch {
        button.textContent = '复制失败'
        window.setTimeout(() => {
          button.textContent = original
        }, 1500)
      }
    })
  })
}

function refreshBindings() {
  nextTick(() => bindCopyButtons())
}

onMounted(refreshBindings)
onUpdated(refreshBindings)
watch(() => props.content, refreshBindings)
</script>

<template>
  <div
    ref="rootRef"
    class="chat-markdown"
    :class="{ 'is-streaming': streaming }"
    v-html="html"
  />
</template>

<style scoped>
.chat-markdown {
  color: var(--text);
  font-size: 14px;
  line-height: 1.75;
  overflow-wrap: anywhere;
}

.chat-markdown :deep(> *:first-child) {
  margin-top: 0;
}

.chat-markdown :deep(> *:last-child) {
  margin-bottom: 0;
}

.chat-markdown :deep(h1),
.chat-markdown :deep(h2),
.chat-markdown :deep(h3),
.chat-markdown :deep(h4) {
  margin: 1.15em 0 0.45em;
  color: var(--text);
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: -0.02em;
}

.chat-markdown :deep(h1) { font-size: 1.35em; }
.chat-markdown :deep(h2) { font-size: 1.15em; color: var(--primary); }
.chat-markdown :deep(h3) { font-size: 1.02em; }
.chat-markdown :deep(h4) { font-size: 0.96em; color: var(--muted); }

.chat-markdown :deep(p) {
  margin: 0.55em 0;
  color: color-mix(in srgb, var(--text) 92%, var(--muted));
}

.chat-markdown :deep(strong) {
  color: var(--text);
  font-weight: 650;
}

.chat-markdown :deep(ul),
.chat-markdown :deep(ol) {
  margin: 0.55em 0;
  padding-left: 1.35em;
  color: color-mix(in srgb, var(--text) 90%, var(--muted));
}

.chat-markdown :deep(li + li) {
  margin-top: 0.28em;
}

.chat-markdown :deep(li > p) {
  margin: 0.2em 0;
}

.chat-markdown :deep(blockquote) {
  margin: 0.8em 0;
  padding: 0.65em 0.9em;
  border-left: 3px solid var(--accent);
  border-radius: 0 8px 8px 0;
  color: var(--muted);
  background: var(--primary-soft);
}

.chat-markdown :deep(hr) {
  margin: 1em 0;
  border: 0;
  border-top: 1px solid var(--line);
}

.chat-markdown :deep(table) {
  width: 100%;
  margin: 0.8em 0;
  border-collapse: collapse;
  font-size: 13px;
}

.chat-markdown :deep(th),
.chat-markdown :deep(td) {
  padding: 8px 10px;
  border: 1px solid var(--line);
  text-align: left;
}

.chat-markdown :deep(th) {
  color: var(--primary);
  background: var(--primary-soft);
}

.chat-markdown :deep(.inline-code) {
  padding: 0.12em 0.38em;
  border: 1px solid var(--line);
  border-radius: 5px;
  color: var(--primary);
  background: var(--surface-soft);
  font-family: Consolas, 'SFMono-Regular', Menlo, monospace;
  font-size: 0.9em;
}

.chat-markdown :deep(.code-canvas) {
  margin: 0.9em 0;
  border: 1px solid color-mix(in srgb, var(--line) 80%, #1f3348);
  border-radius: 12px;
  overflow: hidden;
  background: #0f1c2b;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

.chat-markdown :deep(.code-canvas-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: #15283e;
}

.chat-markdown :deep(.code-canvas-lang) {
  color: #9eb4c8;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.chat-markdown :deep(.code-canvas-copy) {
  min-height: 26px;
  padding: 0 10px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  color: #d7e6f4;
  background: rgba(255, 255, 255, 0.04);
  font-size: 11px;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.chat-markdown :deep(.code-canvas-copy:hover) {
  border-color: rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.08);
}

.chat-markdown :deep(.code-canvas-body) {
  overflow-x: auto;
  margin: 0;
  padding: 14px 16px;
  color: #dce8f4;
  background: transparent;
  font: 12px/1.65 Consolas, 'SFMono-Regular', Menlo, monospace;
  white-space: pre;
}

.chat-markdown :deep(.code-canvas-body code) {
  font: inherit;
  color: inherit;
  background: transparent;
}

.chat-markdown.is-streaming :deep(> *:last-child)::after {
  display: inline-block;
  width: 0.45em;
  height: 1em;
  margin-left: 2px;
  vertical-align: -0.12em;
  border-radius: 1px;
  background: var(--accent);
  opacity: 0.75;
  animation: markdown-cursor 1s step-end infinite;
  content: '';
}

@keyframes markdown-cursor {
  50% { opacity: 0; }
}
</style>
