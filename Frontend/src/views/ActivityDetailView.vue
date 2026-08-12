<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getActivityDetail } from '../api/activity'

const route = useRoute()
const router = useRouter()
const activity = ref(null)
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = route.params.activityId
    const res = await getActivityDetail(id)
    activity.value = res.data
  } catch (e) {
    error.value = e?.message || '活动不存在或加载失败'
  } finally {
    loading.value = false
  }
}

const signupProgress = computed(() => {
  if (!activity.value) return 0
  const cur = activity.value.currentPeople || 0
  const max = activity.value.maxPeople || 1
  return Math.round((cur / max) * 100)
})

const remainingSeats = computed(() => {
  if (!activity.value) return 0
  const max = activity.value.maxPeople || 0
  const cur = activity.value.currentPeople || 0
  return Math.max(0, max - cur)
})

function getStatusInfo(item) {
  if (!item) return { text: '', class: '' }
  const now = new Date()
  const start = new Date(String(item.startTime).replace(' ', 'T'))
  const end = new Date(String(item.endTime).replace(' ', 'T'))
  if (now < start) return { text: '即将开始', class: 'upcoming' }
  if (now > end) return { text: '已结束', class: 'ended' }
  return { text: '进行中', class: 'ongoing' }
}

const statusInfo = computed(() => getStatusInfo(activity.value))

const canSignup = computed(() => {
  if (!activity.value) return false
  if (statusInfo.value.class !== 'upcoming') return false
  if (remainingSeats.value <= 0) return false
  return true
})

const signupButtonText = computed(() => {
  if (!activity.value) return '立即报名'
  if (remainingSeats.value <= 0) return '已满'
  if (statusInfo.value.class === 'ongoing') return '活动进行中'
  if (statusInfo.value.class === 'ended') return '活动已结束'
  return '立即报名'
})

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(String(dateStr).replace(' ', 'T'))
  if (isNaN(d.getTime())) return dateStr
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

function formatTimeRange(startStr, endStr) {
  const start = formatDate(startStr)
  const end = formatDate(endStr)
  if (!start) return end
  if (!end) return start
  const sd = new Date(String(startStr).replace(' ', 'T'))
  const ed = new Date(String(endStr).replace(' ', 'T'))
  const sameDay = sd.getFullYear() === ed.getFullYear() && sd.getMonth() === ed.getMonth() && sd.getDate() === ed.getDate()
  const sameTime = sameDay && sd.getHours() === ed.getHours() && sd.getMinutes() === ed.getMinutes()
  if (sameTime) return start
  if (sameDay) {
    const hm = `${String(ed.getHours()).padStart(2,'0')}:${String(ed.getMinutes()).padStart(2,'0')}`
    return `${start} - ${hm}`
  }
  return `${start} - ${end}`
}

function goBack() {
  router.push('/activities')
}

function goSignup() {
  if (!canSignup.value) return
  router.push({
    name: 'activity-signup',
    params: { activityId: route.params.activityId },
  })
}

onMounted(load)
</script>

<template>
  <div class="activity-detail-view">
    <AppTabBar />

    <main class="detail-page">
      <div class="container">
        <button class="back-btn" @click="goBack">
          <span class="back-icon">‹</span>
          <span>返回活动列表</span>
        </button>

        <div v-if="loading" class="loading-state">
          <div class="loader"></div>
          <p>加载中...</p>
        </div>

        <div v-else-if="error" class="error-state">
          <p>{{ error }}</p>
          <button @click="load">重新加载</button>
        </div>

        <div v-else-if="activity" class="detail-content">
          <header class="detail-header">
            <div class="header-tags">
              <span :class="['status-tag', statusInfo.class]">{{ statusInfo.text }}</span>
              <span v-if="activity.category?.categoryName" class="category-tag">{{ activity.category.categoryName }}</span>
            </div>
            <h1 class="detail-title">{{ activity.title }}</h1>
          </header>

          <div class="info-cards">
            <div class="info-card">
              <div class="info-icon"><svg class="info-icon__svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
              <div class="info-content">
                <span class="info-label">活动时间</span>
                <span class="info-value">{{ formatTimeRange(activity.startTime, activity.endTime) }}</span>
              </div>
            </div>
            <div class="info-card">
              <div class="info-icon"><svg class="info-icon__svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg></div>
              <div class="info-content">
                <span class="info-label">活动地点</span>
                <span class="info-value">{{ activity.location || '线上活动' }}</span>
              </div>
            </div>
            <div class="info-card">
              <div class="info-icon"><svg class="info-icon__svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg></div>
              <div class="info-content">
                <span class="info-label">报名情况</span>
                <span class="info-value">{{ activity.currentPeople || 0 }} / {{ activity.maxPeople || 0 }} 人</span>
              </div>
            </div>
          </div>

          <div class="detail-body">
            <div class="main-content">
              <section class="intro-section">
                <h2>活动介绍</h2>
                <p>{{ activity.content || activity.description || '暂无活动介绍信息' }}</p>
              </section>

              <section v-if="activity.tags && activity.tags.length" class="tags-section">
                <span v-for="tag in activity.tags" :key="tag" class="tag-chip">{{ tag }}</span>
              </section>
            </div>

            <aside class="side-panel">
              <div class="signup-card">
                <div class="progress-header">
                  <span>报名进度</span>
                  <span class="progress-percent">{{ signupProgress }}%</span>
                </div>
                <div class="progress-bar">
                  <div class="progress-fill" :style="{ width: signupProgress + '%' }"></div>
                </div>
                <p class="seats-left">剩余 {{ remainingSeats }} 个名额</p>
                <button
                  :class="['signup-btn', { disabled: !canSignup }]"
                  :disabled="!canSignup"
                  @click="goSignup"
                >
                  {{ signupButtonText }}
                </button>
              </div>
            </aside>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.activity-detail-view {
  min-height: 100vh;
  background: #f8fafc;
}

.detail-page {
  padding: 70px 0 40px;
}

.container {
  width: min(960px, calc(100% - 40px));
  margin: 0 auto;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  margin-bottom: 20px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 14px;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.25s;
}

.back-btn:hover {
  color: #2563eb;
  background: #eff6ff;
}

.back-icon {
  font-size: 18px;
  font-weight: 600;
}

.loading-state, .error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  color: #94a3b8;
}

.loader {
  width: 32px;
  height: 32px;
  border: 3px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 12px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.detail-content {
  animation: fadeInUp 0.5s ease-out;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

.detail-header {
  margin-bottom: 28px;
}

.header-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.status-tag {
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-tag.upcoming {
  background: #fef3c7;
  color: #854d0e;
}

.status-tag.ongoing {
  background: #dcfce7;
  color: #166534;
}

.status-tag.ended {
  background: #f1f5f9;
  color: #475569;
}

.category-tag {
  padding: 3px 10px;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.detail-title {
  font-size: 32px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
  line-height: 1.3;
}

.info-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 28px;
}

.info-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
  transition: box-shadow 0.25s ease, border-color 0.25s ease, transform 0.25s ease;
}

.info-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.info-icon {
  font-size: 24px;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border-radius: 12px;
}

.info-icon__svg {
  width: 20px;
  height: 20px;
  color: #2563eb;
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: #94a3b8;
}

.info-value {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
}

.detail-body {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 24px;
  align-items: start;
}

.main-content {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  padding: 28px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.intro-section h2 {
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
  margin: 0 0 16px;
  padding-left: 12px;
  border-left: 3px solid #3b82f6;
}

.intro-section p {
  font-size: 15px;
  line-height: 1.8;
  color: #475569;
  margin: 0;
  white-space: pre-wrap;
}

.tags-section {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}

.tag-chip {
  padding: 5px 14px;
  background: #f1f5f9;
  color: #64748b;
  border-radius: 16px;
  font-size: 13px;
}

.side-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 80px;
}

.signup-card {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  padding: 24px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;
  color: #64748b;
}

.progress-percent {
  font-weight: 700;
  color: #2563eb;
}

.progress-bar {
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 10px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #2563eb);
  border-radius: 4px;
  transition: width 0.4s ease;
}

.seats-left {
  font-size: 13px;
  color: #94a3b8;
  margin: 0 0 16px;
}

.signup-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.signup-btn:hover:not(.disabled) {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.4);
}

.signup-btn.disabled,
.signup-btn:disabled {
  background: #94a3b8;
  cursor: not-allowed;
  box-shadow: none;
}

@media (max-width: 768px) {
  .info-cards {
    grid-template-columns: 1fr;
  }

  .detail-body {
    grid-template-columns: 1fr;
  }

  .side-panel {
    position: static;
  }

  .detail-title {
    font-size: 24px;
  }
}
</style>
