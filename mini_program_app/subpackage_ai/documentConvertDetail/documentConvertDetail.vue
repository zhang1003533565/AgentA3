<template>
  <view class="page">
    <nav-bar :title="pageTitle" />

    <view class="content">
      <convert-panel
        v-if="meta"
        :convert-type="convertType"
        :type-label="meta.label"
        :accept-extensions="meta.accept"
        :convert-mode="meta.convertMode || 'image'"
        :modes="meta.modes || []"
      />
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import ConvertPanel from '@/subpackage_ai/documentConvert/convertPanel.vue'

const TYPE_META = {
  pdf_to_docx: {
    label: 'PDF → Word',
    accept: ['.pdf'],
    convertMode: 'reflow',
    modes: [
      { value: 'reflow', label: '智能编辑模式', description: '提取PDF文字生成可编辑Word，适合内容修改。' },
      { value: 'image', label: '高清还原模式', description: '完整保留PDF页面效果，适合展示和打印，但文字不可单独编辑。' }
    ]
  },
  ppt_to_docx: {
    label: 'PPT → Word',
    accept: ['.pptx'],
    convertMode: 'reflow',
    modes: [
      { value: 'reflow', label: '智能编辑模式', description: '保留文字、图片、表格可编辑，适合内容修改。' },
      { value: 'image', label: '高清还原模式', description: '通过PDF中转保留PPT页面效果，适合展示。' }
    ]
  },
  pdf_to_ppt: {
    label: 'PDF → PPT',
    accept: ['.pdf'],
    modes: [
      { value: 'image', label: '高清还原模式', description: '页面完整还原为PPT图片，适合展示，但文字不可单独编辑。' },
      { value: 'editable', label: '智能编辑模式', description: 'PDF文字和图片会解析成PPT元素，支持文字修改和图片移动，复杂排版可能存在差异。' }
    ]
  },
  ppt_to_pdf: { label: 'PPT → PDF', accept: ['.ppt', '.pptx'] },
  docx_to_pdf: { label: 'Word → PDF', accept: ['.docx'] },
  docx_to_ppt: {
    label: 'Word → PPT',
    accept: ['.docx'],
    convertMode: 'smart',
    modes: [
      { value: 'smart', label: '智能生成模式', badge: '推荐', description: '解析标题、段落、图片和表格，生成可编辑的汇报型 PPT，适合做演示。' },
      { value: 'image', label: '高清还原模式', description: '保持 Word 原页面效果，每页一张图片，文字不可单独编辑，适合不改内容直接展示。' }
    ]
  }
}

export default {
  components: { NavBar, ConvertPanel },
  data() {
    return {
      convertType: '',
      meta: null,
      pageTitle: '格式转换'
    }
  },
  onLoad(options) {
    const type = String((options && options.convertType) || '').trim()
    const target = TYPE_META[type]
    if (!target) {
      uni.showToast({ title: '无效的转换类型', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 800)
      return
    }
    this.convertType = type
    this.meta = target
    this.pageTitle = target.label
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: #F6F8FB;
  box-sizing: border-box;
}

.content {
  padding: 24rpx 24rpx 48rpx;
}
</style>
