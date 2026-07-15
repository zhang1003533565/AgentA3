<template>
  <view class="common-page-header" :style="headerStyle">
    <nav-bar
      :title="title"
      :fixed="fixed"
      :placeholder="placeholder"
      :showBack="showBack"
      :border="border"
      :background="background"
      :heightRpx="heightRpx"
      :titleColor="titleColor"
      :shadow="shadow"
    >
      <template #left>
        <slot name="left"></slot>
      </template>
      <template #right>
        <slot name="right"></slot>
      </template>
    </nav-bar>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  name: 'CommonPageHeader',
  components: { NavBar },
  props: {
    title: { type: String, default: '' },
    fixed: { type: Boolean, default: true },
    placeholder: { type: Boolean, default: true },
    showBack: { type: Boolean, default: false },
    border: { type: Boolean, default: false },
    background: { type: String, default: '#E8F0F8' },
    heightRpx: { type: Number, default: 88 },
    titleColor: { type: String, default: '#1D1D1F' },
    shadow: { type: Boolean, default: false }
  },
  computed: {
    headerStyle() {
      return {
        background: this.background
      }
    }
  }
}
</script>

<style scoped>
.common-page-header {
  width: 100%;
}
</style>
