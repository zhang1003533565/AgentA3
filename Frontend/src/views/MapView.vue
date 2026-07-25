<script setup>
import { onMounted, ref } from 'vue'

import { getMarkerList } from '../api/map'
import AppTabBar from '../components/AppTabBar.vue'

const loading = ref(false)
const errorMessage = ref('')
const markers = ref([])

onMounted(async () => {
  loading.value = true
  try {
    const result = await getMarkerList()
    markers.value = Array.isArray(result.data) ? result.data : result.data?.records || []
  } catch (error) {
    errorMessage.value = error.message || '校园地图暂时无法加载'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="phone-shell">
    <AppTabBar />

    <main class="page">
      <header class="topbar">
        <h1>校园地图</h1>
      </header>

      <section class="map-panel card">
        <div class="map-canvas">
          <span>{{ loading ? '地图加载中...' : '校园地图' }}</span>
        </div>
        <p v-if="errorMessage" class="map-error">{{ errorMessage }}</p>
      </section>

      <section class="marker-list card">
        <h2>地点列表</h2>
        <p v-if="!loading && !errorMessage && markers.length === 0" class="muted">暂无地图地点</p>
        <article v-for="marker in markers.slice(0, 8)" :key="marker.id || marker.name" class="marker-row">
          <strong>{{ marker.name || marker.title || '未命名地点' }}</strong>
          <span>{{ marker.typeName || marker.type || marker.category || '校园地点' }}</span>
        </article>
      </section>
    </main>
  </div>
</template>

<style scoped>
.map-panel {
  overflow: hidden;
}

.map-canvas {
  display: grid;
  place-items: center;
  min-height: 260px;
  color: #ffffff;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.16) 1px, transparent 1px),
    linear-gradient(rgba(255, 255, 255, 0.16) 1px, transparent 1px),
    linear-gradient(135deg, #0f766e, #2563eb);
  background-size: 36px 36px, 36px 36px, auto;
  font-size: 24px;
  font-weight: 900;
}

.map-error {
  margin: 0;
  padding: 14px 16px;
  color: #dc2626;
  background: #fff7f7;
}

.marker-list {
  display: grid;
  margin-top: 16px;
  padding: 16px;
}

.marker-list h2 {
  margin: 0 0 12px;
  color: #111827;
  font-size: 18px;
}

.marker-row {
  display: grid;
  gap: 4px;
  padding: 12px 0;
  border-top: 1px solid #eef2f7;
}

.marker-row strong {
  color: #1f2937;
}

.marker-row span {
  color: #64748b;
  font-size: 14px;
}
</style>
