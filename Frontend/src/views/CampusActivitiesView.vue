<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getActivityList, getCategoryList, addFavorite, removeFavorite } from '../api/activity'
import { mockCategories, mockActivities } from '../mock/activityData'

const router = useRouter()

const loading = ref(true)
const error = ref(null)
const activities = ref([])
const categories = ref([])
const total = ref(0)

const searchText = ref('')
const selectedCategory = ref(null)
const selectedTime = ref('all')
const viewMode = ref('card')
const showCalendar = ref(false)
const currentPage = ref(1)
const pageSize = 12
const favorites = ref(new Set())

const categoryList = computed(() => {
  const allOption = { id: null, categoryName: '全部' }
  if (!categories.value || categories.value.length === 0) {
    return [allOption]
  }
  return [allOption, ...categories.value]
})

const TIME_FILTERS = [
  { key: 'all', label: '全部时间' },
  { key: 'upcoming', label: '即将开始' },
  { key: 'ongoing', label: '进行中' },
  { key: 'ended', label: '已结束' },
]

const STATUS_MAP = {
  upcoming: { text: '可报名', class: 'status-upcoming' },
  ongoing: { text: '进行中', class: 'status-ongoing' },
  ended: { text: '已结束', class: 'status-ended' },
  full: { text: '报名已满', class: 'status-full' },
}

onMounted(() => {
  console.log('CampusActivitiesView mounted')
  loadCategories()
  loadActivities()
})

async function loadCategories() {
  try {
    const res = await getCategoryList()
    if (res?.data) {
      const fetched = Array.isArray(res.data) ? res.data : res.data.records || []
      if (fetched.length > 0) {
        categories.value = fetched
        return
      }
    }
    categories.value = mockCategories
  } catch (err) {
    console.error('加载分类失败，使用Mock数据:', err)
    categories.value = mockCategories
  }
}

async function loadActivities() {
  loading.value = true
  error.value = null
  
  try {
    const params = {
      page: currentPage.value,
      size: pageSize,
      keyword: searchText.value || undefined,
      categoryId: selectedCategory.value !== null ? selectedCategory.value : undefined,
    }
    
    const res = await getActivityList(params)
    const data = res?.data || {}
    let records = Array.isArray(data) ? data : data.records || []
    
    if (records.length === 0) {
      records = filterMockActivities()
    }
    
    activities.value = filterByTime(records)
    total.value = data.total || records.length
  } catch (err) {
    console.error('加载活动失败，使用Mock数据:', err)
    const mockRecords = filterMockActivities()
    activities.value = filterByTime(mockRecords)
    total.value = mockRecords.length
  } finally {
    loading.value = false
  }
}

function filterMockActivities() {
  let result = [...mockActivities]
  
  if (selectedCategory.value !== null) {
    result = result.filter(item => item.categoryId === selectedCategory.value)
  }
  
  if (searchText.value) {
    const keyword = searchText.value.toLowerCase()
    result = result.filter(item => 
      item.title?.toLowerCase().includes(keyword) ||
      item.organizerName?.toLowerCase().includes(keyword) ||
      item.content?.toLowerCase().includes(keyword)
    )
  }
  
  return result
}

function filterByTime(list) {
  if (selectedTime.value === 'all') return list
  const now = new Date()
  return list.filter(item => {
    const phase = getActivityPhase(item)
    if (selectedTime.value === 'upcoming') return phase === 'signup'
    if (selectedTime.value === 'ongoing') return phase === 'ongoing'
    if (selectedTime.value === 'ended') return phase === 'ended'
    return true
  })
}

function getActivityPhase(item) {
  const now = new Date()
  const startTime = new Date(item.startTime?.replace(' ', 'T'))
  const endTime = new Date(item.endTime?.replace(' ', 'T'))
  
  if (now < startTime) return 'signup'
  if (now > endTime) return 'ended'
  return 'ongoing'
}

function getActivityStatus(item) {
  const phase = getActivityPhase(item)
  const isFull = (item.currentPeople || 0) >= (item.maxPeople || 0)
  
  if (phase === 'ended') return STATUS_MAP.ended
  if (phase === 'ongoing') return STATUS_MAP.ongoing
  if (isFull) return STATUS_MAP.full
  return STATUS_MAP.upcoming
}

function formatDate(dateStr) {
  if (!dateStr) return '待定'
  const date = new Date(String(dateStr).replace(' ', 'T'))
  if (isNaN(date.getTime())) return dateStr
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}月${day}日 ${hour}:${minute}`
}

function handleSearch() {
  currentPage.value = 1
  loadActivities()
}

function handleCardClick(id) {
  router.push(`/activities/${id}`)
}

async function handleToggleFav(id, event) {
  event.stopPropagation()
  const isFav = favorites.value.has(id)
  
  try {
    if (isFav) {
      await removeFavorite(id)
      favorites.value.delete(id)
    } else {
      await addFavorite(id)
      favorites.value.add(id)
    }
    favorites.value = new Set(favorites.value)
  } catch (err) {
    console.error('收藏操作失败:', err)
  }
}

function handleShare(activity, event) {
  event.stopPropagation()
  const url = `${window.location.origin}/activities/${activity.id}`
  
  if (navigator.clipboard) {
    navigator.clipboard.writeText(url).then(() => {
      alert('链接已复制到剪贴板')
    }).catch(() => {
      prompt('复制以下链接分享:', url)
    })
  } else {
    prompt('复制以下链接分享:', url)
  }
}

const canRegister = computed(() => true)

function getCalendarDays() {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)
  const daysInMonth = lastDay.getDate()
  const startWeekDay = firstDay.getDay()
  
  const calendarDays = []
  for (let i = 0; i < startWeekDay; i++) {
    calendarDays.push(null)
  }
  for (let d = 1; d <= daysInMonth; d++) {
    calendarDays.push(d)
  }
  return calendarDays
}

function getActivitiesForDay(day) {
  if (!day) return []
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  return activities.value.filter(item => {
    const startDate = item.startTime?.slice(0, 10)
    const endDate = item.endTime?.slice(0, 10)
    if (!startDate) return false
    return dateStr >= startDate && dateStr <= (endDate || startDate)
  })
}
</script>

<template>
  <div class="campus-activities-view">
    <AppTabBar />
    
    <main class="page">
      <div class="container">
        <div class="page-header">
          <div><h2>校园活动</h2>
          <p class="header-desc">探索精彩校园生活，参与有趣活动</p></div>
          <button class="publish-entry" type="button" @click="$router.push('/activities/publish')">发布活动</button>
        </div>
        
        <div class="filter-section">
          <div class="filter-row">
            <span class="filter-label">📅 活动分类</span>
            <div class="filter-tags">
              <button
                v-for="cat in categoryList"
                :key="cat.id"
                :class="['tag-btn', { active: selectedCategory === cat.id }]"
                @click="selectedCategory = cat.id; currentPage = 1; loadActivities()"
              >
                {{ cat.categoryName }}
              </button>
            </div>
          </div>
          
          <div class="filter-row filter-row-main">
            <div class="search-box">
              <input
                v-model="searchText"
                type="text"
                placeholder="搜索活动名称、主办方、关键词..."
                @keyup.enter="handleSearch"
              />
              <button class="search-btn" @click="handleSearch">搜索</button>
            </div>
            
            <div class="time-filter">
              <button
                v-for="tf in TIME_FILTERS"
                :key="tf.key"
                :class="['time-btn', { active: selectedTime === tf.key }]"
                @click="selectedTime = tf.key; currentPage = 1; loadActivities()"
              >
                {{ tf.label }}
              </button>
            </div>
            
            <div class="view-toggle">
              <button
                :class="['view-btn', { active: viewMode === 'card' && !showCalendar }]"
                @click="viewMode = 'card'; showCalendar = false"
                title="卡片视图"
              >
                ▦
              </button>
              <button
                :class="['view-btn', { active: viewMode === 'list' && !showCalendar }]"
                @click="viewMode = 'list'; showCalendar = false"
                title="列表视图"
              >
                ☰
              </button>
              <button
                :class="['view-btn', { active: showCalendar }]"
                @click="showCalendar = !showCalendar"
                title="日历视图"
              >
                📅
              </button>
            </div>
          </div>
        </div>
        
        <div v-if="loading" class="loading-container">
          <div class="loader"></div>
          <p>加载中...</p>
        </div>
        
        <div v-else-if="error" class="error-container">
          <p class="error-msg">❌ {{ error }}</p>
          <button class="retry-btn" @click="loadActivities">重新加载</button>
        </div>
        
        <div v-else-if="activities.length === 0" class="empty-container">
          <p class="empty-icon">📭</p>
          <p class="empty-text">暂无校园活动，敬请期待</p>
        </div>
        
        <template v-else>
          <div v-if="showCalendar" class="calendar-view">
            <div class="calendar-header">
              <h3>{{ new Date().getFullYear() }}年{{ new Date().getMonth() + 1 }}月</h3>
              <span class="calendar-summary">本月共 {{ activities.length }} 个活动</span>
            </div>
            <div class="calendar-weekdays">
              <span v-for="w in ['日', '一', '二', '三', '四', '五', '六']" :key="w" class="weekday">{{ w }}</span>
            </div>
            <div class="calendar-grid">
              <template v-for="(day, idx) in getCalendarDays()" :key="idx">
                <div
                  v-if="day"
                  :class="['calendar-day', { today: day === new Date().getDate() }]"
                >
                  <span class="day-number">{{ day }}</span>
                  <div
                    v-for="act in getActivitiesForDay(day).slice(0, 2)"
                    :key="act.id"
                    :class="['day-activity', getActivityStatus(act).class]"
                    @click="handleCardClick(act.id)"
                    :title="act.title"
                  >
                    {{ act.title.slice(0, 6) }}
                  </div>
                  <div v-if="getActivitiesForDay(day).length > 2" class="day-more">
                    +{{ getActivitiesForDay(day).length - 2 }}
                  </div>
                </div>
                <div v-else class="calendar-day empty"></div>
              </template>
            </div>
          </div>
          
          <div v-else-if="viewMode === 'card'" class="activity-grid">
            <div
              v-for="(item, index) in activities"
              :key="item.id"
              class="activity-card"
              :style="{ animationDelay: `${index * 50}ms` }"
              @click="handleCardClick(item.id)"
            >
              <div class="card-cover">
                <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title" />
                <div v-else class="card-cover-placeholder">📅</div>
                <span :class="['status-badge', getActivityStatus(item).class]">
                  {{ getActivityStatus(item).text }}
                </span>
                <button
                  :class="['fav-btn', { favorited: favorites.has(item.id) }]"
                  @click="handleToggleFav(item.id, $event)"
                >
                  {{ favorites.has(item.id) ? '❤️' : '🤍' }}
                </button>
              </div>
              
              <div class="card-body">
                <div class="card-tags">
                  <span v-if="item.category?.categoryName" class="category-tag">
                    {{ item.category.categoryName }}
                  </span>
                  <span class="organizer">{{ item.organizer || '未知主办方' }}</span>
                </div>
                
                <h3 class="card-title">{{ item.title }}</h3>
                
                <div class="card-info">
                  <div class="info-item">
                    <span class="icon">🕐</span>
                    <span>{{ formatDate(item.startTime) }}</span>
                  </div>
                  <div class="info-item">
                    <span class="icon">📍</span>
                    <span>{{ item.location || '线上活动' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="icon">👥</span>
                    <span>
                      {{ item.currentPeople || 0 }}/{{ item.maxPeople || 0 }} 人
                      <em v-if="(item.maxPeople || 0) - (item.currentPeople || 0) > 0" class="highlight">
                        (剩 {{ (item.maxPeople || 0) - (item.currentPeople || 0) }} 位)
                      </em>
                    </span>
                  </div>
                </div>
                
                <div class="card-footer">
                  <span class="signup-deadline">截止: {{ formatDate(item.signupEndTime) }}</span>
                  <div class="card-actions">
                    <button class="action-btn" @click="handleShare(item, $event)">🔗</button>
                    <button class="action-btn" @click.stop="$router.push(`/activities/${item.id}/sign-in`)">签到</button>
                    <button
                      v-if="getActivityStatus(item).text === '可报名'"
                      class="action-btn primary"
                      @click.stop="handleCardClick(item.id)"
                    >
                      立即报名
                    </button>
                    <button v-else class="action-btn" @click.stop="handleCardClick(item.id)">
                      查看详情
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
          
          <div v-else class="activity-list">
            <div
              v-for="(item, index) in activities"
              :key="item.id"
              class="activity-list-item"
              :style="{ animationDelay: `${index * 40}ms` }"
              @click="handleCardClick(item.id)"
            >
              <div class="list-cover">
                <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title" />
                <div v-else class="list-cover-placeholder">📅</div>
              </div>
              
              <div class="list-content">
                <div class="list-header">
                  <h3>{{ item.title }}</h3>
                  <span :class="['status-badge', getActivityStatus(item).class]">
                    {{ getActivityStatus(item).text }}
                  </span>
                </div>
                
                <div class="list-meta">
                  <span class="meta-tag">{{ item.category?.categoryName || '未分类' }}</span>
                  <span class="meta-text">{{ item.organizer || '未知主办方' }}</span>
                  <span class="meta-sep">|</span>
                  <span class="meta-text">🕐 {{ formatDate(item.startTime) }}</span>
                  <span class="meta-sep">|</span>
                  <span class="meta-text">📍 {{ item.location || '线上' }}</span>
                </div>
                
                <div class="list-footer">
                  <span class="people-count">
                    👥 {{ item.currentPeople || 0 }}/{{ item.maxPeople || 0 }} 人
                  </span>
                  <div class="list-actions">
                    <button
                      :class="['icon-btn', { favorited: favorites.has(item.id) }]"
                      @click="handleToggleFav(item.id, $event)"
                    >
                      {{ favorites.has(item.id) ? '❤️' : '🤍' }}
                    </button>
                    <button class="icon-btn" @click="handleShare(item, $event)">🔗</button>
                    <button class="list-detail-btn" @click.stop="$router.push(`/activities/${item.id}/sign-in`)">签到</button>
                    <button class="list-detail-btn" @click.stop="handleCardClick(item.id)">
                      {{ getActivityStatus(item).text === '可报名' ? '立即报名' : '查看详情' }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
        
        <div v-if="total > pageSize" class="pagination">
          <button :disabled="currentPage === 1" @click="currentPage--; loadActivities()">上一页</button>
          <span>第 {{ currentPage }} 页 / 共 {{ Math.ceil(total / pageSize) }} 页</span>
          <button :disabled="currentPage >= Math.ceil(total / pageSize)" @click="currentPage++; loadActivities()">下一页</button>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.campus-activities-view {
  min-height: 100vh;
  background: #f4f7fb;
}

.page {
  padding: 80px 20px 40px;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 8px;
}

.header-desc {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.filter-section {
  background: #ffffff;
  border-radius: 16px;
  padding: 20px 24px;
  margin-bottom: 24px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  animation: slideDown 0.4s ease-out;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-row:last-child {
  margin-bottom: 0;
}

.filter-label {
  font-size: 14px;
  font-weight: 600;
  color: #475569;
  white-space: nowrap;
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-btn {
  padding: 6px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tag-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.tag-btn.active {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border-color: #2563eb;
  color: #ffffff;
}

.filter-row-main {
  gap: 12px;
}

.search-box {
  display: flex;
  gap: 8px;
  flex: 1;
  min-width: 280px;
}

.search-box input {
  flex: 1;
  padding: 10px 16px;
  border: 1.5px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.search-box input:focus {
  border-color: #3b82f6;
}

.search-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  background: #3b82f6;
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.search-btn:hover {
  background: #2563eb;
}

.time-filter {
  display: flex;
  background: #f1f5f9;
  padding: 4px;
  border-radius: 10px;
}

.time-btn {
  padding: 8px 14px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.time-btn:hover {
  color: #2563eb;
}

.time-btn.active {
  background: #ffffff;
  color: #2563eb;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.1);
}

.view-toggle {
  display: flex;
  background: #f1f5f9;
  padding: 4px;
  border-radius: 10px;
}

.view-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #64748b;
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.2s;
}

.view-btn:hover {
  color: #2563eb;
}

.view-btn.active {
  background: #ffffff;
  color: #2563eb;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.1);
}

.loading-container,
.error-container,
.empty-container {
  text-align: center;
  padding: 60px 20px;
  background: #ffffff;
  border-radius: 16px;
}

.loader {
  width: 40px;
  height: 40px;
  margin: 0 auto 16px;
  border: 4px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loading-container p,
.empty-text {
  color: #64748b;
  font-size: 14px;
}

.error-msg {
  color: #ef4444;
  margin: 0 0 16px;
}

.retry-btn {
  padding: 10px 24px;
  border: none;
  border-radius: 10px;
  background: #3b82f6;
  color: #ffffff;
  cursor: pointer;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.activity-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
}

.activity-card {
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  transition: all 0.3s ease;
  animation: cardFadeIn 0.5s ease-out backwards;
}

.activity-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
}

.card-cover {
  position: relative;
  height: 180px;
  overflow: hidden;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.activity-card:hover .card-cover img {
  transform: scale(1.05);
}

.card-cover-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
}

.status-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
}

.status-upcoming {
  background: rgba(250, 204, 21, 0.9);
  color: #713f12;
}

.status-ongoing {
  background: rgba(34, 197, 94, 0.9);
  color: #14532d;
}

.status-ended {
  background: rgba(100, 116, 139, 0.9);
  color: #ffffff;
}

.status-full {
  background: rgba(239, 68, 68, 0.9);
  color: #ffffff;
}

.fav-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  cursor: pointer;
  transition: transform 0.2s;
}

.fav-btn:hover {
  transform: scale(1.1);
}

.card-body {
  padding: 16px;
}

.card-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.category-tag {
  padding: 2px 8px;
  background: #dbeafe;
  color: #1d4ed8;
  border-radius: 4px;
  font-size: 12px;
}

.organizer {
  font-size: 12px;
  color: #94a3b8;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
  margin: 0 0 12px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #64748b;
}

.info-item .icon {
  font-size: 14px;
}

.info-item em {
  font-style: normal;
}

.info-item em.highlight {
  color: #10b981;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.signup-deadline {
  font-size: 12px;
  color: #94a3b8;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 6px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.action-btn.primary {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  color: #ffffff;
}

.action-btn.primary:hover {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #ffffff;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.activity-list-item {
  display: flex;
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  transition: all 0.3s;
  animation: listSlideIn 0.4s ease-out backwards;
}

.activity-list-item:hover {
  transform: translateX(4px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1);
}

.list-cover {
  width: 160px;
  flex-shrink: 0;
}

.list-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.list-cover-placeholder {
  width: 100%;
  height: 100%;
  min-height: 120px;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
}

.list-content {
  flex: 1;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.list-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.list-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
  flex-wrap: wrap;
}

.meta-tag {
  padding: 2px 8px;
  background: #dbeafe;
  color: #1d4ed8;
  border-radius: 4px;
  font-size: 12px;
}

.meta-sep {
  color: #e2e8f0;
}

.list-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
}

.people-count {
  font-size: 13px;
  color: #94a3b8;
}

.list-actions {
  display: flex;
  gap: 8px;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: #f1f5f9;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.icon-btn:hover {
  background: #e2e8f0;
}

.list-detail-btn {
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.list-detail-btn:hover {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
}

.calendar-view {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  animation: slideDown 0.4s ease-out;
}

.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.calendar-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.calendar-summary {
  font-size: 14px;
  color: #64748b;
}

.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.weekday {
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  padding: 8px;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
}

.calendar-day {
  min-height: 100px;
  padding: 8px;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #f1f5f9;
  transition: all 0.2s;
}

.calendar-day.empty {
  background: transparent;
  border: none;
}

.calendar-day.today {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.05);
}

.day-number {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 6px;
}

.calendar-day.today .day-number {
  color: #2563eb;
}

.day-activity {
  padding: 4px 6px;
  border-radius: 6px;
  font-size: 11px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: transform 0.2s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.day-activity:hover {
  transform: scale(1.02);
}

.day-activity.status-upcoming {
  background: rgba(250, 204, 21, 0.2);
  color: #854d0e;
}

.day-activity.status-ongoing {
  background: rgba(34, 197, 94, 0.2);
  color: #166534;
}

.day-activity.status-ended {
  background: rgba(100, 116, 139, 0.15);
  color: #475569;
}

.day-activity.status-full {
  background: rgba(239, 68, 68, 0.15);
  color: #b91c1c;
}

.day-more {
  font-size: 11px;
  color: #64748b;
  padding: 2px 6px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 32px;
}

.pagination button {
  padding: 8px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  color: #64748b;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.pagination button:hover:not(:disabled) {
  border-color: #3b82f6;
  color: #3b82f6;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pagination span {
  font-size: 14px;
  color: #64748b;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes cardFadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes listSlideIn {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@media (max-width: 768px) {
  .page {
    padding: 70px 16px 30px;
  }
  
  .filter-row-main {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-box {
    min-width: auto;
  }
  
  .activity-grid {
    grid-template-columns: 1fr;
  }
  
  .activity-list-item {
    flex-direction: column;
  }
  
  .list-cover {
    width: 100%;
    height: 160px;
  }
  
  .calendar-day {
    min-height: 80px;
    padding: 4px;
  }
  
  .day-activity {
    font-size: 10px;
  }
}
</style>

<style scoped>
.publish-entry {
  min-height: 40px;
  padding: 0 18px;
  border-radius: 8px;
  color: #fff;
  background: #315f8c;
  font-weight: 700;
}
</style>
