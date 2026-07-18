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
      :subtitle="subtitle"
      :subtitleColor="subtitleColor"
      :iconColor="iconColor"
      :shadow="shadow"
      @back="$emit('back')"
      @right-click="$emit('right-click')"
    >
      <template v-if="$slots.left" #left>
        <slot name="left"></slot>
      </template>
      <template v-if="$slots.right" #right>
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
    subtitle: { type: String, default: '' },
    fixed: { type: Boolean, default: true },
    placeholder: { type: Boolean, default: true },
    showBack: { type: Boolean, default: false },
    border: { type: Boolean, default: false },
    background: { type: String, default: '#E8F0F8' },
    heightRpx: { type: Number, default: 88 },
    titleColor: { type: String, default: '#1D1D1F' },
    subtitleColor: { type: String, default: '#8E8E93' },
    iconColor: { type: String, default: '#1D1D1F' },
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
