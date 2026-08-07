<template>
  <view class="page">
    <nav-bar title="格式转换" />

    <view class="content">
      <!-- 顶部说明 -->
      <view class="intro-card">
        <text class="intro-title">文档格式转换</text>
        <text class="intro-desc">支持常用办公文档格式互转，转换进度实时可见</text>
      </view>

      <!-- 转换类型网格 -->
      <view class="types-grid">
        <view
          class="type-card"
          :class="{ 'type-card--disabled': !item.enabled }"
          v-for="item in types"
          :key="item.convertType"
          @tap="openType(item)"
        >
          <view class="type-icon" :style="{ background: item.lightColor }">
            <text class="type-icon-text" :style="{ color: item.color }">{{ item.iconText }}</text>
          </view>
          <view class="type-info">
            <text class="type-name">{{ item.label }}</text>
            <text class="type-desc">{{ item.desc }}</text>
          </view>
          <view class="type-tag" v-if="!item.enabled">
            <text class="type-tag-text">即将上线</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'

const types = ref([
  { convertType: 'pdf_to_docx', label: 'PDF → DOCX', desc: 'PDF 转 Word 文档', iconText: 'DOCX', color: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.10)', enabled: true },
  { convertType: 'ppt_to_docx', label: 'PPT → DOCX', desc: 'PPT 转 Word 文档', iconText: 'DOCX', color: '#6B9B7A', lightColor: 'rgba(107, 155, 122, 0.12)', enabled: true },
  { convertType: 'docx_to_pdf', label: 'DOCX → PDF', desc: 'Word 转 PDF 文档', iconText: 'PDF', color: '#B89B7A', lightColor: 'rgba(184, 155, 122, 0.12)', enabled: false },
  { convertType: 'pdf_to_ppt', label: 'PDF → PPT', desc: 'PDF 转 PPT 演示文稿', iconText: 'PPT', color: '#8B7AB8', lightColor: 'rgba(139, 122, 184, 0.12)', enabled: false },
  { convertType: 'ppt_to_pdf', label: 'PPT → PDF', desc: 'PPT 转 PDF 文档', iconText: 'PDF', color: '#7A9BB8', lightColor: 'rgba(122, 155, 184, 0.12)', enabled: false },
  { convertType: 'docx_to_ppt', label: 'DOCX → PPT', desc: 'Word 转 PPT 演示文稿', iconText: 'PPT', color: '#A67B7B', lightColor: 'rgba(166, 123, 123, 0.12)', enabled: false }
])

const openType = (item) => {
  if (!item.enabled) {
    uni.showToast({ title: '该功能即将上线', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/subpackage_ai/documentConvertDetail/documentConvertDetail?convertType=${encodeURIComponent(item.convertType)}`
  })
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

.intro-card {
  background: #FFFFFF;
  border-radius: 24rpx;
  border: 1rpx solid #EEEEEE;
  padding: 32rpx;
  margin-bottom: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.intro-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #1D1D1F;
}

.intro-desc {
  font-size: 26rpx;
  color: #8E8E93;
  line-height: 1.6;
}

.types-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
}

.type-card {
  position: relative;
  background: #FFFFFF;
  border-radius: 24rpx;
  border: 1rpx solid #EEEEEE;
  padding: 28rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.type-card--disabled {
  opacity: 0.55;
}

.type-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.type-icon-text {
  font-size: 26rpx;
  font-weight: 700;
}

.type-info {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.type-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #1D1D1F;
}

.type-desc {
  font-size: 24rpx;
  color: #8E8E93;
  line-height: 1.5;
}

.type-tag {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  background: rgba(142, 142, 147, 0.10);
  border-radius: 999rpx;
  padding: 6rpx 14rpx;
}

.type-tag-text {
  font-size: 20rpx;
  color: #8E8E93;
}
</style>
