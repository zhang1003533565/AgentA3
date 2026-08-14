<template>
  <view class="page">
    <nav-bar :title="pageTitle" />

    <view class="content">
      <convert-panel
        v-if="meta"
        :convert-type="convertType"
        :type-label="meta.label"
        :accept-extensions="meta.accept"
      />
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import ConvertPanel from '@/subpackage_ai/documentConvert/convertPanel.vue'

const TYPE_META = {
  pdf_to_docx: { label: 'PDF → DOCX', accept: ['.pdf'] },
  ppt_to_docx: { label: 'PPT → DOCX', accept: ['.pptx'] }
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
