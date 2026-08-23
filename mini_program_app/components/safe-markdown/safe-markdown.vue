<template>
  <view class="safe-markdown">
    <template v-for="(segment, index) in segments" :key="index">
      <rich-text v-if="segment.type === 'text'" :nodes="segment.nodes" selectable />
      <view v-else class="md-code-block">
        <view
          class="md-code-copy"
          :class="{ 'md-code-copy--done': copiedIndex === index }"
          @tap="copyCode(segment.node, index)"
        >
          <text>{{ copiedIndex === index ? '已复制' : '复制' }}</text>
        </view>
        <rich-text :nodes="[segment.node]" selectable />
      </view>
    </template>
  </view>
</template>

<script>
import { markdownToNodes } from '@/utils/markdownNodes.js'

function collectText(node) {
  if (!node) return ''
  if (node.type === 'text') return node.text
  if (Array.isArray(node.children)) return node.children.map(collectText).join('')
  return ''
}

export default {
  name: 'SafeMarkdown',
  props: {
    content: { type: [String, Number], default: '' },
    showCodeCopy: { type: Boolean, default: false }
  },
  data() {
    return { copiedIndex: -1 }
  },
  computed: {
    nodes() {
      return markdownToNodes(String(this.content || ''))
    },
    // 开启 showCodeCopy 时，把代码块节点按原顺序拆出来单独渲染（外层包复制按钮）
    segments() {
      const nodes = this.nodes
      if (!this.showCodeCopy) return [{ type: 'text', nodes }]
      const segments = []
      let buffer = []
      const flush = () => {
        if (buffer.length) {
          segments.push({ type: 'text', nodes: buffer })
          buffer = []
        }
      }
      for (const node of nodes) {
        if (node.name === 'pre') {
          flush()
          segments.push({ type: 'code', node })
        } else {
          buffer.push(node)
        }
      }
      flush()
      return segments
    }
  },
  methods: {
    copyCode(node, index) {
      const text = collectText(node)
      uni.setClipboardData({
        data: text,
        success: () => {
          this.copiedIndex = index
          clearTimeout(this._copyTimer)
          this._copyTimer = setTimeout(() => {
            this.copiedIndex = -1
          }, 1600)
        }
      })
    }
  },
  beforeUnmount() {
    clearTimeout(this._copyTimer)
  }
}
</script>

<style scoped>
.safe-markdown {
  max-width: 100%;
  font-size: 28rpx;
  line-height: 1.72;
  color: inherit;
  overflow-wrap: anywhere;
  word-break: break-word;
  -webkit-user-select: text;
  user-select: text;
}
.safe-markdown :deep(h1),
.safe-markdown :deep(h2),
.safe-markdown :deep(h3) {
  display: block;
  margin: 18rpx 0 10rpx;
  font-weight: 750;
  line-height: 1.4;
}
.safe-markdown :deep(h1) { font-size: 36rpx; }
.safe-markdown :deep(h2) { font-size: 32rpx; }
.safe-markdown :deep(h3) { font-size: 29rpx; }
.safe-markdown :deep(p) { display: block; margin: 8rpx 0; }
.safe-markdown :deep(blockquote) {
  display: block;
  margin: 12rpx 0;
  padding: 10rpx 18rpx;
  border-left: 6rpx solid #93c5fd;
  border-radius: 0 10rpx 10rpx 0;
  background: #f8fafc;
}
.safe-markdown :deep(blockquote p:first-child) { margin-top: 0; }
.safe-markdown :deep(blockquote p:last-child) { margin-bottom: 0; }
.safe-markdown :deep(hr) {
  display: block;
  height: 1px;
  margin: 20rpx 0;
  border: 0;
  background: #dbe2ea;
}
.safe-markdown :deep(ul),
.safe-markdown :deep(ol) {
  display: block;
  margin: 10rpx 0;
  padding-left: 38rpx;
}
.safe-markdown :deep(li) { display: list-item; margin: 6rpx 0; }
.safe-markdown :deep(li ul),
.safe-markdown :deep(li ol) { margin: 4rpx 0; }
.safe-markdown :deep(.task-list-item) { list-style: none; }
.safe-markdown :deep(s) { text-decoration: line-through; text-decoration-thickness: 1px; }
.safe-markdown :deep(code) {
  padding: 2rpx 8rpx;
  border-radius: 7rpx;
  background: #eef2ff;
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  -webkit-user-select: text;
  user-select: text;
}
.safe-markdown :deep(pre) {
  box-sizing: border-box;
  display: block;
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  margin: 12rpx 0;
  padding: 20rpx;
  border-radius: 14rpx;
  background: #111827;
  color: #e5e7eb;
  white-space: pre;
  overflow-wrap: normal;
  word-break: normal;
  -webkit-overflow-scrolling: touch;
  -webkit-user-select: text;
  user-select: text;
}
.safe-markdown :deep(pre code) {
  display: block;
  min-width: max-content;
  padding: 0;
  background: transparent;
  color: inherit;
}
.safe-markdown :deep(a) { color: #2563eb; text-decoration: underline; }
.safe-markdown :deep(.markdown-table-scroll) {
  display: block;
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  margin: 12rpx 0;
  -webkit-overflow-scrolling: touch;
  -webkit-user-select: text;
  user-select: text;
}
.safe-markdown :deep(.markdown-table-scroll table) {
  width: max-content;
  min-width: 100%;
  margin: 0;
  border-collapse: collapse;
  table-layout: auto;
}
.safe-markdown :deep(th),
.safe-markdown :deep(td) {
  min-width: 140rpx;
  max-width: 520rpx;
  padding: 10rpx;
  border: 1px solid #dbe2ea;
  text-align: left;
  vertical-align: top;
  white-space: normal;
  word-break: break-word;
  -webkit-user-select: text;
  user-select: text;
}

/* 代码块 + 复制按钮 */
.md-code-block {
  position: relative;
}
.md-code-copy {
  position: absolute;
  top: 26rpx;
  right: 18rpx;
  z-index: 2;
  padding: 6rpx 18rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.14);
  color: #cbd5e1;
  font-size: 22rpx;
  line-height: 1.4;
}
.md-code-copy--done {
  color: #86efac;
  background: rgba(134, 239, 172, 0.18);
}
</style>
