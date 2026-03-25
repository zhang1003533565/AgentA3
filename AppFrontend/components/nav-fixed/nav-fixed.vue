<template>
  <!-- 全局通用 · 永远不报错 · 固定顶部导航 -->
  <view class="nav-fixed">
    <slot />
  </view>
</template>

<style scoped>
.nav-fixed {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  background: #ffffff;
  z-index: 999;
}
</style>
