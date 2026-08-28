<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'

const route = useRoute()
const workspaceUrl = computed(() => (
  route.query.tab === 'templates'
    ? '/airesume/index.html?embedded=1#/template'
    : '/airesume/index.html?embedded=1#/'
))
</script>

<template>
  <div class="resume-source-page">
    <AppTabBar />
    <iframe
      :key="workspaceUrl"
      class="resume-source-frame"
      :src="workspaceUrl"
      title="AI 简历制作工作台"
    />
  </div>
</template>

<style scoped>
.resume-source-page {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #f5f6f8;
}

.resume-source-frame {
  position: absolute;
  inset: 60px 0 0;
  display: block;
  width: 100vw;
  height: calc(100vh - 60px);
  border: 0;
  background: #fff;
}
</style>
