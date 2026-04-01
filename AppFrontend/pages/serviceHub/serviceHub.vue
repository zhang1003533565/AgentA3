<template>
  <view class="page">
    <nav-bar :title="title" :showBack="true" />
    <view class="hero">
      <text class="hero-title">{{ title }}</text>
      <text class="hero-desc">该服务入口已补齐页面壳层，后续可以继续接具体业务接口。</text>
    </view>
    <view class="card-list">
      <view class="card">
        <text class="card-title">当前状态</text>
        <text class="card-desc">页面已可正常跳转，不再出现路径不存在的问题。</text>
      </view>
      <view class="card">
        <text class="card-title">建议下一步</text>
        <text class="card-desc">如果你确定优先级，我可以继续把这里替换成真实业务页，比如校车、校历、报修或就业信息。</text>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { NavBar },
  data() {
    return {
      title: '校园服务'
    }
  },
  onLoad(options) {
    this.title = options.title || '校园服务'
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef2ff 0%, #f8fafc 35%, #f7f7f9 100%);
}

.hero,
.card {
  margin: 24rpx;
  padding: 32rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 20rpx 50rpx rgba(15, 23, 42, 0.05);
}

.hero-title,
.card-title {
  display: block;
  color: #111827;
  font-weight: 700;
}

.hero-title {
  font-size: 44rpx;
}

.hero-desc,
.card-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #6b7280;
}
</style>
