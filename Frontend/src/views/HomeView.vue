<script setup>
import { onMounted, ref } from 'vue'

import { getEnabledAnnouncements } from '../api/notice'
import AppTabBar from '../components/AppTabBar.vue'

const headline = ref('欢迎使用校园助手')
const loading = ref(false)

const quickEntries = [
  { title: '活动', desc: '校园活动与报名', icon: '活' },
  { title: '食堂', desc: '餐厅与档口信息', icon: '食' },
  { title: '失物', desc: '失物招领与二手', icon: '失' },
  { title: '论坛', desc: '校园交流社区', icon: '坛' },
]

onMounted(async () => {
  loading.value = true
  try {
    const result = await getEnabledAnnouncements()
    const list = Array.isArray(result.data) ? result.data : result.data?.records || []
    headline.value = list[0]?.title || headline.value
  } catch {
    headline.value = '公告暂时无法加载'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="phone-shell">
    <main class="page">
      <header class="topbar">
        <div>
          <p class="eyebrow">AgentA3 Campus</p>
          <h1>首页</h1>
        </div>
      </header>

      <section class="hero card">
        <p>今天想做什么？</p>
        <h2>校园服务，一屏直达</h2>
      </section>

      <section class="headline card">
        <strong>公告</strong>
        <span>{{ loading ? '加载中...' : headline }}</span>
      </section>

      <section class="quick-grid">
        <article v-for="item in quickEntries" :key="item.title" class="quick-card card">
          <div class="quick-icon">{{ item.icon }}</div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
        </article>
      </section>
    </main>

    <AppTabBar />
  </div>
</template>

<style scoped>
.eyebrow {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

.hero {
  padding: 22px;
  color: #ffffff;
  background: linear-gradient(135deg, #2563eb, #0891b2);
}

.hero p {
  margin: 0 0 10px;
  opacity: 0.9;
}

.hero h2 {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
}

.headline {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 14px;
}

.headline strong {
  flex: 0 0 auto;
  color: #2563eb;
}

.headline span {
  min-width: 0;
  overflow: hidden;
  color: #374151;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.quick-card {
  min-height: 142px;
  padding: 16px;
}

.quick-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  color: #ffffff;
  background: #0f766e;
  font-size: 20px;
  font-weight: 900;
}

.quick-card h3 {
  margin: 16px 0 6px;
  color: #111827;
  font-size: 18px;
}

.quick-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}
</style>

