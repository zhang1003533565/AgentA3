<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import {
  addJobFavorite,
  getJobFavoriteIds,
  getLatestJobRecommendations,
  JOB_SALARY_HINT,
  listJobFavorites,
  refreshJobRecommendations,
  removeJobFavorite,
} from '../api/jobRecommendations'

const router = useRouter()
const route = useRoute()
const jobs = ref([])
const favoriteJobs = ref([])
const favoritedIds = ref(new Set())
const activeTab = ref(route.query.tab === 'favorites' ? 'favorites' : 'weekly')
const loading = ref(true)
const refreshing = ref(false)
const error = ref('')

const displayedJobs = computed(() => (activeTab.value === 'favorites' ? favoriteJobs.value : jobs.value))

const weekLabel = computed(() => {
  const first = jobs.value[0]
  if (!first?.weekStartDate || !first?.weekEndDate) return ''
  return `${String(first.weekStartDate).slice(0, 10)} — ${String(first.weekEndDate).slice(0, 10)}`
})

function parseSkills(skillsText) {
  return String(skillsText || '')
    .split(/[,，、]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function resolveJobSearchLink(job) {
  const keyword = String(job?.jobTitle || '软件工程师').trim() || '软件工程师'
  return `https://www.zhipin.com/web/geek/job?query=${encodeURIComponent(keyword)}`
}

async function loadFavoriteIds() {
  try {
    const result = await getJobFavoriteIds()
    const ids = Array.isArray(result?.data) ? result.data : []
    favoritedIds.value = new Set(ids)
  } catch {
    favoritedIds.value = new Set()
  }
}

async function loadFavorites() {
  try {
    const result = await listJobFavorites()
    favoriteJobs.value = Array.isArray(result?.data) ? result.data : []
  } catch {
    favoriteJobs.value = []
  }
}

async function fetchJobs() {
  loading.value = true
  error.value = ''
  try {
    const result = await getLatestJobRecommendations()
    jobs.value = Array.isArray(result?.data) ? result.data : []
    if (activeTab.value === 'weekly' && !jobs.value.length) {
      error.value = 'empty'
    }
  } catch (cause) {
    if (activeTab.value === 'weekly') {
      error.value = cause.message || '加载失败'
    }
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  refreshing.value = true
  error.value = ''
  try {
    const result = await refreshJobRecommendations()
    jobs.value = Array.isArray(result?.data) ? result.data : []
    activeTab.value = 'weekly'
    error.value = jobs.value.length ? '' : 'empty'
    await loadFavoriteIds()
  } catch (cause) {
    error.value = cause.message || '刷新失败'
  } finally {
    refreshing.value = false
  }
}

async function toggleFavorite(job, event) {
  event?.stopPropagation?.()
  if (!job?.id) return
  const isFav = favoritedIds.value.has(job.id)
  try {
    if (isFav) {
      await removeJobFavorite(job.id)
      favoritedIds.value.delete(job.id)
    } else {
      await addJobFavorite(job.id)
      favoritedIds.value.add(job.id)
    }
    favoritedIds.value = new Set(favoritedIds.value)
    if (activeTab.value === 'favorites') {
      await loadFavorites()
    }
  } catch {
    // request helper already surfaces errors
  }
}

watch(activeTab, async (tab) => {
  error.value = ''
  if (tab === 'favorites') {
    loading.value = true
    await loadFavorites()
    loading.value = false
    if (!favoriteJobs.value.length) {
      error.value = 'empty-favorites'
    }
  } else if (!jobs.value.length) {
    error.value = 'empty'
  }
})

onMounted(async () => {
  await Promise.all([fetchJobs(), loadFavoriteIds()])
  if (activeTab.value === 'favorites') {
    await loadFavorites()
    if (!favoriteJobs.value.length) {
      error.value = 'empty-favorites'
    }
  }
})
</script>

<template>
  <div class="hotjobs-shell">
    <AppTabBar />
    <header class="hotjobs-header">
      <button type="button" aria-label="返回 AI 工具" @click="router.push('/ai-tools')">‹</button>
      <div>
        <span class="hotjobs-kicker">CAREER SIGNAL</span>
        <h1>岗位雷达</h1>
        <p>AI 整理近一周软件工程热门岗位，薪资与要求以 BOSS 直聘为准</p>
      </div>
      <div class="hotjobs-actions">
        <span v-if="weekLabel" class="hotjobs-week">{{ weekLabel }}</span>
        <button type="button" :disabled="refreshing" @click="handleRefresh">
          {{ refreshing ? '刷新中…' : '刷新推荐' }}
        </button>
      </div>
    </header>

    <main class="hotjobs-main">
      <p class="hotjobs-disclaimer">
        以下内容由 AI 生成，仅供参考；点击链接将在 BOSS 直聘搜索对应岗位，请以平台实际信息为准。
      </p>

      <div class="hotjobs-tabs">
        <button type="button" :class="{ active: activeTab === 'weekly' }" @click="activeTab = 'weekly'">
          本周推荐
        </button>
        <button type="button" :class="{ active: activeTab === 'favorites' }" @click="activeTab = 'favorites'">
          我的收藏
        </button>
      </div>

      <div v-if="loading" class="hotjobs-empty">正在加载…</div>

      <div v-else-if="error === 'empty'" class="hotjobs-empty">
        <p>暂无岗位推荐数据</p>
        <p class="hotjobs-hint">系统会在每周一定时刷新，你也可以立即生成最新推荐。</p>
        <button type="button" :disabled="refreshing" @click="handleRefresh">立即生成</button>
      </div>

      <div v-else-if="error === 'empty-favorites'" class="hotjobs-empty">
        <p>还没有收藏岗位</p>
        <p class="hotjobs-hint">在「本周推荐」中点击星标即可收藏感兴趣的岗位。</p>
        <button type="button" @click="activeTab = 'weekly'">去看推荐</button>
      </div>

      <div v-else-if="error" class="hotjobs-empty hotjobs-empty--error">
        <p>{{ error }}</p>
        <button type="button" @click="fetchJobs">重试</button>
      </div>

      <div v-else class="hotjobs-grid">
        <article v-for="(job, index) in displayedJobs" :key="job.id || `${job.jobTitle}-${index}`" class="hotjobs-card">
          <div v-if="activeTab === 'weekly'" class="hotjobs-card__rank">
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
            <small v-if="index === 0">HOT</small>
          </div>
          <div class="hotjobs-card__body" :class="{ 'hotjobs-card__body--full': activeTab === 'favorites' }">
            <div class="hotjobs-card__title-row">
              <h2>{{ job.jobTitle }}</h2>
              <button
                type="button"
                class="hotjobs-fav"
                :class="{ 'hotjobs-fav--active': favoritedIds.has(job.id) }"
                :aria-label="favoritedIds.has(job.id) ? '取消收藏' : '收藏岗位'"
                @click="toggleFavorite(job, $event)"
              >
                {{ favoritedIds.has(job.id) ? '★' : '☆' }}
              </button>
            </div>
            <p class="hotjobs-salary-hint">{{ JOB_SALARY_HINT }}</p>
            <div v-if="parseSkills(job.skills).length" class="hotjobs-skills">
              <span v-for="skill in parseSkills(job.skills)" :key="skill">{{ skill }}</span>
            </div>
            <footer class="hotjobs-card__footer">
              <a :href="resolveJobSearchLink(job)" target="_blank" rel="noreferrer">在 BOSS 直聘查看</a>
              <span>AI 生成</span>
            </footer>
          </div>
        </article>
      </div>
    </main>
  </div>
</template>

<style scoped>
.hotjobs-shell {
  min-height: 100vh;
  padding-top: 60px;
  background: #f4f6f8;
}

.hotjobs-header {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 16px;
  align-items: start;
  padding: 20px max(18px, calc((100% - 960px) / 2));
  border-bottom: 1px solid #e4e7ec;
  background: #fff;
}

.hotjobs-header > button {
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 50%;
  color: #344054;
  background: #f0f2f5;
  font-size: 29px;
  line-height: 1;
}

.hotjobs-kicker {
  display: block;
  margin-bottom: 4px;
  color: #527797;
  font-size: 11px;
  letter-spacing: 0.08em;
}

.hotjobs-header h1 {
  margin: 0;
  color: #1d2939;
  font-size: 24px;
}

.hotjobs-header p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
}

.hotjobs-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.hotjobs-week {
  color: #98a2b3;
  font-size: 12px;
}

.hotjobs-actions button {
  min-width: 96px;
  height: 36px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  color: #344054;
  background: #fff;
  font-size: 13px;
}

.hotjobs-actions button:disabled {
  opacity: 0.6;
}

.hotjobs-main {
  width: min(100%, 960px);
  margin: 0 auto;
  padding: 20px 18px 40px;
}

.hotjobs-disclaimer {
  margin: 0 0 16px;
  padding: 12px 14px;
  border-radius: 8px;
  color: #667085;
  background: #fff;
  border: 1px solid #e4e7ec;
  font-size: 13px;
  line-height: 1.6;
}

.hotjobs-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.hotjobs-tabs button {
  min-width: 96px;
  height: 36px;
  border: 1px solid #d0d5dd;
  border-radius: 999px;
  color: #667085;
  background: #fff;
  font-size: 13px;
}

.hotjobs-tabs button.active {
  border-color: #527797;
  color: #527797;
  background: #eef4f8;
  font-weight: 600;
}

.hotjobs-grid {
  display: grid;
  gap: 14px;
}

.hotjobs-card {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 12px;
  padding: 18px;
  border: 1px solid #e1e5ea;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(16, 24, 40, 0.04);
}

.hotjobs-card__rank {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding-top: 4px;
}

.hotjobs-card__rank span {
  color: #527797;
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.hotjobs-card__rank small {
  padding: 2px 6px;
  border-radius: 999px;
  color: #fff;
  background: #527797;
  font-size: 10px;
}

.hotjobs-card__body h2 {
  margin: 0;
  color: #1d2939;
  font-size: 18px;
}

.hotjobs-card__body--full {
  grid-column: 1 / -1;
}

.hotjobs-card__title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.hotjobs-fav {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  color: #98a2b3;
  background: #f4f6f8;
  font-size: 18px;
  line-height: 1;
}

.hotjobs-fav--active {
  color: #f5a623;
  background: #fff8eb;
}

.hotjobs-salary-hint {
  margin: 0 0 12px;
  color: #667085;
  font-size: 13px;
}

.hotjobs-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.hotjobs-skills span {
  padding: 4px 10px;
  border-radius: 999px;
  color: #527797;
  background: #eef4f8;
  font-size: 12px;
}

.hotjobs-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #eef0f2;
}

.hotjobs-card__footer a {
  color: #2f76bd;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}

.hotjobs-card__footer span {
  color: #98a2b3;
  font-size: 12px;
}

.hotjobs-empty {
  padding: 48px 24px;
  text-align: center;
  color: #667085;
}

.hotjobs-empty--error {
  color: #b42318;
}

.hotjobs-hint {
  margin: 8px 0 16px;
  color: #98a2b3;
  font-size: 13px;
}

.hotjobs-empty button {
  min-width: 120px;
  height: 40px;
  border: 0;
  border-radius: 6px;
  color: #fff;
  background: #527797;
}

@media (max-width: 720px) {
  .hotjobs-header {
    grid-template-columns: auto 1fr;
  }

  .hotjobs-actions {
    grid-column: 1 / -1;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }

  .hotjobs-card {
    grid-template-columns: 1fr;
  }

  .hotjobs-card__rank {
    flex-direction: row;
    justify-content: flex-start;
  }
}
</style>
