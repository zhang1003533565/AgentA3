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
const showCalendar = ref(false)
const currentPage = ref(1)
const pageSize = 12
const favorites = ref(new Set())
const FAV_KEY = 'activity_favorites'

function loadFavoritesFromStorage() {
  try {
    const raw = localStorage.getItem(FAV_KEY)
    if (raw) {
      const ids = JSON.parse(raw)
      favorites.value = new Set(ids)
    }
  } catch {}
}

function saveFavoritesToStorage() {
  localStorage.setItem(FAV_KEY, JSON.stringify([...favorites.value]))
}

const now = new Date()
const calendarYear = ref(now.getFullYear())
const calendarMonth = ref(now.getMonth())
const selectedDate = ref(now.getDate())

const categoryList = computed(() => {
  const allOption = { id: null, categoryName: '全部' }
  const source = categories.value?.length > 0 ? categories.value : mockCategories
  const list = source.map(c => ({
    ...c,
    categoryName: c.categoryName || c.name || '未分类'
  }))
  return [allOption, ...list]
})

const selectedDateKey = computed(() => {
  const y = calendarYear.value
  const m = String(calendarMonth.value + 1).padStart(2, '0')
  const d = String(selectedDate.value).padStart(2, '0')
  return `${y}-${m}-${d}`
})

const activitiesForSelectedDate = computed(() => {
  return activities.value.filter(item => {
    const sd = parseDate(item.startTime)
    const ed = parseDate(item.endTime)
    if (isNaN(sd.getTime())) return false
    const sel = new Date(selectedDateKey.value)
    const startDay = new Date(sd.getFullYear(), sd.getMonth(), sd.getDate())
    const endDay = ed && !isNaN(ed.getTime()) ? new Date(ed.getFullYear(), ed.getMonth(), ed.getDate()) : startDay
    return sel >= startDay && sel <= endDay
  })
})

function prevMonth() {
  if (calendarMonth.value === 0) {
    calendarMonth.value = 11
    calendarYear.value--
  } else {
    calendarMonth.value--
  }
  clampSelectedDate()
}

function nextMonth() {
  if (calendarMonth.value === 11) {
    calendarMonth.value = 0
    calendarYear.value++
  } else {
    calendarMonth.value++
  }
  clampSelectedDate()
}

function clampSelectedDate() {
  const lastDay = new Date(calendarYear.value, calendarMonth.value + 1, 0).getDate()
  if (selectedDate.value > lastDay) {
    selectedDate.value = lastDay
  }
}

const TIME_FILTERS = [
  { key: 'all', label: '全部时间' },
  { key: 'upcoming', label: '即将开始' },
  { key: 'ongoing', label: '进行中' },
  { key: 'ended', label: '已结束' },
]

const STATUS_MAP = {
  upcoming: { text: '即将开始', class: 'status-upcoming' },
  ongoing: { text: '进行中', class: 'status-ongoing' },
  ended: { text: '已结束', class: 'status-ended' },
  full: { text: '报名已满', class: 'status-full' },
}

onMounted(() => {
  console.log('CampusActivitiesView mounted')
  loadFavoritesFromStorage()
  loadCategories()
  loadActivities()
})

async function loadCategories() {
  try {
    const res = await getCategoryList()
    if (res?.data) {
      const fetched = Array.isArray(res.data) ? res.data : res.data.records || []
      if (fetched.length > 0) {
        categories.value = fetched.map(c => ({
          ...c,
          categoryName: c.categoryName || c.name || '未分类'
        }))
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
  const startTime = parseDate(item.startTime)
  const endTime = parseDate(item.endTime)
  
  if (isNaN(startTime.getTime()) || isNaN(endTime.getTime())) return 'signup'
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
  const date = parseDate(dateStr)
  if (isNaN(date.getTime())) return dateStr
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}月${day}日 ${hour}:${minute}`
}

function parseDate(str) {
  if (!str) return new Date(NaN)
  const s = String(str).trim()
  const normalized = s.includes('T') ? s : s.replace(' ', 'T')
  let d = new Date(normalized)
  if (isNaN(d.getTime())) {
    d = new Date(s.replace(' ', 'T').replace(/:00$/, ''))
  }
  return d
}

function getHour(dateStr) {
  const d = parseDate(dateStr)
  return isNaN(d.getTime()) ? '--' : String(d.getHours()).padStart(2, '0')
}

function getMinute(dateStr) {
  const d = parseDate(dateStr)
  return isNaN(d.getTime()) ? '--' : String(d.getMinutes()).padStart(2, '0')
}

function getSelectedDay() {
  return String(selectedDate.value).padStart(2, '0')
}

function getSelectedMonth() {
  return calendarMonth.value + 1
}

function getWeekday() {
  const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  const d = new Date(selectedDateKey.value)
  return days[d.getDay()]
}

function prevDay() {
  const d = new Date(selectedDateKey.value)
  d.setDate(d.getDate() - 1)
  calendarYear.value = d.getFullYear()
  calendarMonth.value = d.getMonth()
  selectedDate.value = d.getDate()
}

function nextDay() {
  const d = new Date(selectedDateKey.value)
  d.setDate(d.getDate() + 1)
  calendarYear.value = d.getFullYear()
  calendarMonth.value = d.getMonth()
  selectedDate.value = d.getDate()
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
      favorites.value.delete(id)
    } else {
      favorites.value.add(id)
    }
    favorites.value = new Set(favorites.value)
    saveFavoritesToStorage()
  } catch (err) {
    console.error('收藏操作失败:', err)
  }
}

function getCalendarDays() {
  const year = calendarYear.value
  const month = calendarMonth.value
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
  const year = calendarYear.value
  const month = calendarMonth.value
  const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  const targetDay = new Date(dateStr)
  return activities.value.filter(item => {
    const sd = parseDate(item.startTime)
    const ed = parseDate(item.endTime)
    if (isNaN(sd.getTime())) return false
    const startDay = new Date(sd.getFullYear(), sd.getMonth(), sd.getDate())
    const endDay = ed && !isNaN(ed.getTime()) ? new Date(ed.getFullYear(), ed.getMonth(), ed.getDate()) : startDay
    return targetDay >= startDay && targetDay <= endDay
  })
}
</script>

<template>
  <div class="campus-activities-view">
    <AppTabBar />
    
    <main class="page">
      <div class="container">
        <div class="page-header">
          <h2>校园活动</h2>
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
                :class="['view-btn', { active: !showCalendar }]"
                @click="showCalendar = false"
                title="卡片视图"
              >
                ▦
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
            <div class="calendar-left">
              <div class="calendar-header">
                <button class="nav-btn" @click="prevMonth">‹</button>
                <h3>{{ calendarYear }}年{{ calendarMonth + 1 }}月</h3>
                <button class="nav-btn" @click="nextMonth">›</button>
              </div>
              <div class="calendar-weekdays">
                <span v-for="w in ['日', '一', '二', '三', '四', '五', '六']" :key="w" class="weekday">{{ w }}</span>
              </div>
              <div class="calendar-grid">
                <template v-for="(day, idx) in getCalendarDays()" :key="idx">
                  <div
                    v-if="day"
                    :class="[
                      'calendar-day',
                      {
                        today: day === now.getDate() && calendarMonth === now.getMonth() && calendarYear === now.getFullYear(),
                        selected: day === selectedDate
                      }
                    ]"
                    @click="selectedDate = day"
                  >
                    <span class="day-number">{{ day }}</span>
                    <div
                      v-for="act in getActivitiesForDay(day).slice(0, 2)"
                      :key="act.id"
                      :class="['day-activity', getActivityStatus(act).class]"
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
            
            <div class="calendar-right">
              <div class="right-header">
                <div class="rh-title">
                  <span class="right-date">{{ selectedDateKey.replace(/-/g, '/') }}</span>
                  <span class="right-weekday">{{ getWeekday() }}</span>
                </div>
                <div class="rh-count">
                  <span class="count-num">{{ activitiesForSelectedDate.length }}</span>
                  <span class="count-label">个活动</span>
                </div>
              </div>
              <div class="right-content">
                <template v-if="activitiesForSelectedDate.length > 0">
                  <div
                    v-for="(item, index) in activitiesForSelectedDate"
                    :key="item.id"
                    class="day-activity-card"
                    :style="{ animationDelay: `${index * 80}ms` }"
                    @click="handleCardClick(item.id)"
                  >
                    <div class="dac-body">
                      <div class="dac-tags">
                        <span v-if="item.category?.categoryName" class="dac-category">{{ item.category.categoryName }}</span>
                        <span :class="['dac-status', getActivityStatus(item).class]">{{ getActivityStatus(item).text }}</span>
                      </div>
                      <h4 class="dac-title">{{ item.title }}</h4>
                      <div class="dac-meta">
                        <span>📍 {{ item.location || '线上活动' }}</span>
                        <span>🕐 {{ formatDate(item.startTime) }} - {{ formatDate(item.endTime) }}</span>
                      </div>
                      <div class="dac-footer">
                        <span class="dac-detail">活动详情<span class="dac-arrow">→</span></span>
                      </div>
                    </div>
                  </div>
                </template>
                <div v-else class="right-empty">
                  <p class="empty-icon">📅</p>
                  <p>当天暂无活动</p>
                </div>
              </div>
            </div>
          </div>
          
          <div v-else class="activity-grid">
            <div
              v-for="(item, index) in activities"
              :key="item.id"
              class="activity-card"
              :style="{ animationDelay: `${index * 60}ms` }"
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
                    <span class="detail-link" @click.stop="handleCardClick(item.id)">
                      查看详情 <span class="detail-arrow">→</span>
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </main>
  </div>
</template>

<style scoped>
.campus-activities-view {
  min-height: 100vh;
  background: #f4f7fb;
  animation: pageFadeIn 0.5s ease-out;
}

.page {
  padding: 80px 20px 40px;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 28px;
  animation: headerSlideDown 0.5s ease-out;
}

.page-header h2 {
  font-size: 30px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
  letter-spacing: -0.5px;
}

.filter-section {
  background: #ffffff;
  border-radius: 16px;
  padding: 20px 24px;
  margin-bottom: 28px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  animation: filterSlideDown 0.5s ease-out 0.1s backwards;
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
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.tag-btn:hover {
  border-color: #3b82f6;
  color: #2563eb;
  background: #f0f7ff;
  transform: translateY(-1px);
}

.tag-btn.active {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border-color: #2563eb;
  color: #ffffff;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.3);
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
  transition: all 0.2s;
}

.search-box input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.search-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.25);
  transition: all 0.25s;
}

.search-btn:hover {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 4px 10px rgba(59, 130, 246, 0.35);
  transform: translateY(-1px);
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
  transition: all 0.25s;
}

.time-btn:hover {
  color: #2563eb;
  background: rgba(59, 130, 246, 0.05);
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
  transition: all 0.25s;
}

.view-btn:hover {
  color: #2563eb;
  background: rgba(59, 130, 246, 0.05);
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
  animation: fadeInUp 0.4s ease-out;
}

.loader {
  width: 40px;
  height: 40px;
  margin: 0 auto 16px;
  border: 4px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
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
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.25);
  transition: all 0.25s;
}

.retry-btn:hover {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 4px 10px rgba(59, 130, 246, 0.35);
  transform: translateY(-1px);
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
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  animation: cardFadeIn 0.5s ease-out backwards;
}

.activity-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.14);
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
  transition: transform 0.5s ease;
}

.activity-card:hover .card-cover img {
  transform: scale(1.08);
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
  transition: all 0.25s;
}

.fav-btn:hover {
  transform: scale(1.15);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
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

.action-btn.primary {
  padding: 6px 14px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.25);
  transition: all 0.25s;
}

.action-btn.primary:hover {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #ffffff;
  box-shadow: 0 4px 10px rgba(59, 130, 246, 0.35);
  transform: translateY(-1px);
}

.detail-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 13px;
  font-weight: 500;
  color: #2563eb;
  cursor: pointer;
  transition: color 0.25s;
  user-select: none;
}

.detail-link:hover {
  color: #1d4ed8;
}

.detail-link:hover .detail-arrow {
  transform: translateX(3px);
}

.detail-arrow {
  display: inline-block;
  transition: transform 0.25s;
}

.calendar-view {
  background: #ffffff;
  border-radius: 16px;
  padding: 0;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  animation: fadeInUp 0.5s ease-out;
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  align-items: start;
  overflow: hidden;
}

.calendar-left {
  padding: 20px;
  min-width: 0;
  overflow: hidden;
}

.calendar-right {
  min-width: 0;
  padding: 24px;
  border-left: 1px solid #e2e8f0;
  animation: slideInRight 0.5s ease-out 0.15s backwards;
  display: flex;
  flex-direction: column;
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

.nav-btn {
  width: 36px;
  height: 36px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  color: #64748b;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s;
}

.nav-btn:hover {
  border-color: #3b82f6;
  color: #2563eb;
  background: rgba(59, 130, 246, 0.05);
  transform: translateY(-1px);
}

.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
  margin-bottom: 8px;
}

.weekday {
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  padding: 4px 0;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
}

.calendar-day {
  min-height: 72px;
  padding: 6px;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #f1f5f9;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.calendar-day:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}

.calendar-day.empty {
  background: transparent;
  border: none;
  cursor: default;
}

.calendar-day.empty:hover {
  transform: none;
  box-shadow: none;
  background: transparent;
}

.calendar-day.today {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.05);
}

.calendar-day.selected {
  border-color: #2563eb;
  background: rgba(59, 130, 246, 0.1);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.2);
}

.day-number {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 4px;
}

.calendar-day.today .day-number {
  color: #2563eb;
}

.calendar-day.selected .day-number {
  color: #1d4ed8;
}

.day-activity {
  padding: 2px 4px;
  border-radius: 4px;
  font-size: 10px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: all 0.2s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.day-activity:hover {
  transform: scale(1.03);
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

.right-header {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rh-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rh-title .right-date {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.5px;
}

.rh-title .right-weekday {
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
}

.rh-count {
  display: flex;
  align-items: baseline;
  gap: 4px;
  background: #eff6ff;
  padding: 6px 14px;
  border-radius: 20px;
}

.count-num {
  font-size: 18px;
  font-weight: 700;
  color: #2563eb;
  line-height: 1;
}

.count-label {
  font-size: 12px;
  color: #2563eb;
  font-weight: 500;
}

.right-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.day-activity-card {
  display: block;
  padding: 16px 18px;
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  animation: cardFadeIn 0.4s ease-out backwards;
}

.day-activity-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.15);
  transform: translateY(-3px);
}

.dac-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dac-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.dac-category {
  padding: 2px 8px;
  background: #dbeafe;
  color: #1d4ed8;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.dac-status {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.dac-status.status-upcoming {
  background: rgba(250, 204, 21, 0.2);
  color: #854d0e;
}

.dac-status.status-ongoing {
  background: rgba(34, 197, 94, 0.2);
  color: #166534;
}

.dac-status.status-ended {
  background: rgba(100, 116, 139, 0.15);
  color: #475569;
}

.dac-status.status-full {
  background: rgba(239, 68, 68, 0.15);
  color: #b91c1c;
}

.dac-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.dac-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #64748b;
}

.dac-footer {
  margin-top: auto;
  padding-top: 10px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.dac-detail {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
  color: #2563eb;
  transition: all 0.25s;
}

.day-activity-card:hover .dac-detail {
  color: #1d4ed8;
}

.dac-arrow {
  display: inline-block;
  transition: transform 0.25s;
}

.day-activity-card:hover .dac-arrow {
  transform: translateX(3px);
}

.right-empty {
  text-align: center;
  padding: 40px 20px;
  color: #94a3b8;
}

.right-empty .empty-icon {
  font-size: 36px;
  margin-bottom: 8px;
}

@keyframes pageFadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes headerSlideDown {
  from {
    opacity: 0;
    transform: translateY(-16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes filterSlideDown {
  from {
    opacity: 0;
    transform: translateY(-12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes cardFadeIn {
  from {
    opacity: 0;
    transform: translateY(24px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes spin {
  to { transform: rotate(360deg); }
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
  
  .calendar-view {
    grid-template-columns: 1fr;
  }
  
  .calendar-right {
    border-left: none;
    border-top: 1px solid #e2e8f0;
  }
  
  .calendar-day {
    min-height: 64px;
    padding: 4px;
  }
  
  .day-activity {
    font-size: 9px;
    padding: 2px 3px;
  }
}
</style>