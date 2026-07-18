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
.safe-markdown{font-size:28rpx;line-height:1.72;color:inherit;word-break:break-word}
.safe-markdown :deep(h1),.safe-markdown :deep(h2),.safe-markdown :deep(h3){display:block;margin:18rpx 0 10rpx;font-weight:750;line-height:1.4}
.safe-markdown :deep(h1){font-size:36rpx}.safe-markdown :deep(h2){font-size:32rpx}.safe-markdown :deep(h3){font-size:29rpx}
.safe-markdown :deep(p){display:block;margin:8rpx 0}.safe-markdown :deep(ul),.safe-markdown :deep(ol){display:block;padding-left:34rpx;margin:10rpx 0}
.safe-markdown :deep(li){display:list-item;margin:6rpx 0}.safe-markdown :deep(code){padding:2rpx 8rpx;border-radius:7rpx;background:#eef2ff;color:#334155;font-family:monospace}
.safe-markdown :deep(pre){display:block;overflow-x:auto;margin:12rpx 0;padding:20rpx;border-radius:14rpx;background:#111827;color:#e5e7eb;white-space:pre-wrap}
.safe-markdown :deep(pre code){padding:0;background:transparent;color:inherit}.safe-markdown :deep(a){color:#2563eb;text-decoration:underline}
.safe-markdown :deep(table){width:100%;margin:12rpx 0;border-collapse:collapse}.safe-markdown :deep(th),.safe-markdown :deep(td){padding:10rpx;border:1px solid #dbe2ea;text-align:left}
</style>
