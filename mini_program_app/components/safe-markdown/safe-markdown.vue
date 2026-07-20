<template>
  <view class="safe-markdown">
    <rich-text :nodes="nodes" selectable />
  </view>
</template>

<script>
import { markdownToNodes } from '@/utils/markdownNodes.js'

export default {
  name: 'SafeMarkdown',
  props: {
    content: { type: [String, Number], default: '' }
  },
  computed: {
    nodes() {
      return markdownToNodes(String(this.content || ''))
    }
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
</style>
