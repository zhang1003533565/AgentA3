<template>
  <view class="icon-line" :class="[sizeClass]" :style="{ width: sizeRpx + 'rpx', height: sizeRpx + 'rpx' }">
    <image class="icon-img" :src="iconSrc" mode="aspectFit" />
  </view>
</template>

<script>
// 简约线性图标，统一 1.5px 线条、#48484A，全站规范。金刚区 24px / 服务区 22px
export default {
  name: 'IconLine',
  props: {
    name: { type: String, required: true },
    size: { type: String, default: 'diamond' }
  },
  computed: {
    sizeRpx() {
      if (this.size === 'service') return 44
      if (this.size === 'cell') return 40
      return 48
    },
    sizeClass() {
      return 'icon-line--' + (this.size || 'diamond')
    },
    iconSrc() {
      return `/static/icons/line/${this.name}.svg`
    }
  }
}
</script>

<style lang="scss" scoped>
.icon-line {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.icon-img {
  width: 100%;
  height: 100%;
}
</style>
