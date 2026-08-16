<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getActivityList, getCategoryList, getMyFavorites, getMyRegistrations, addFavorite, removeFavorite } from '../api/activity'

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
const myActivitiesOnly = ref(false)
const myActivityIds = ref(new Set())
const currentPage = ref(1)
const pageSize = 9
const pageRef = ref(null)

const totalPages = computed(() => Math.max(1, Math.ceil((total.value || 0) / pageSize)))

const pageItems = computed(() => {
  const total = totalPages.value
  const current = currentPage.value
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }
  const core = [1, total, current - 1, current, current + 1]
  const pages = [...new Set(core)].filter((p) => p >= 1 && p <= total).sort((a, b) => a - b)
  const items = []
  let prev = 0
  pages.forEach((p) => {
    if (p - prev > 1) items.push('...')
    items.push(p)
    prev = p
  })
  return items
})

function goToPage(page) {
  if (page === currentPage.value) return
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  loadActivities()
  pageRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
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

async function loadFavoritesFromBackend() {
  try {
    const res = await getMyFavorites({ page: 1, size: 999 })
    const data = res?.data || {}
    const records = Array.isArray(data) ? data : data.records || []
    const backendIds = records.map((item) => item.id)
    // 与本地标记合并，保证收藏状态与后端一致
    favorites.value = new Set([...favorites.value, ...backendIds])
  } catch (err) {
    console.error('加载收藏状态失败:', err)
  }
}

const failedCovers = ref(new Set())

function onCoverError(id) {
  if (failedCovers.value.has(id)) return
  failedCovers.value.add(id)
  failedCovers.value = new Set(failedCovers.value)
}

const now = new Date()
const calendarYear = ref(now.getFullYear())
const calendarMonth = ref(now.getMonth())
const selectedDate = ref(now.getDate())

const categoryList = computed(() => {
  const allOption = { id: null, categoryName: '全部' }
  const source = categories.value?.length > 0 ? categories.value : []
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

const visibleActivities = computed(() => {
  if (!myActivitiesOnly.value) return activities.value
  return activities.value.filter((item) => myActivityIds.value.has(Number(item.id)))
})

async function loadMyActivityIds() {
  try {
    const res = await getMyRegistrations({ page: 1, size: 999 })
    const data = res?.data || {}
    const records = Array.isArray(data) ? data : data.records || []
    myActivityIds.value = new Set(records.map((r) => Number(r.activityId ?? r.activity?.id)))
  } catch (err) {
    console.error('加载我的活动失败:', err)
    myActivityIds.value = new Set()
  }
}

async function toggleMyActivities() {
  if (!myActivitiesOnly.value) {
    await loadMyActivityIds()
  }
  myActivitiesOnly.value = !myActivitiesOnly.value
}

const activitiesForSelectedDate = computed(() => {
  return visibleActivities.value.filter(item => {
    const sd = parseDate(item.startTime)
    const ed = parseDate(item.endTime)
    if (isNaN(sd.getTime())) return false
    const sel = new Date(calendarYear.value, calendarMonth.value, selectedDate.value)
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

const hasActiveFilters = computed(() => {
  return selectedCategory.value !== null || selectedTime.value !== 'all' || !!searchText.value
})

function clearFilters() {
  selectedCategory.value = null
  selectedTime.value = 'all'
  searchText.value = ''
  currentPage.value = 1
  loadActivities()
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
  loadFavoritesFromStorage()
  loadFavoritesFromBackend()
  loadCategories()
  loadActivities()
  loadMyActivityIds()
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
    categories.value = []
  } catch (err) {
    console.error('加载分类失败:', err)
    categories.value = []
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
      timePhase: selectedTime.value !== 'all' ? selectedTime.value : undefined,
    }

    const res = await getActivityList(params)
    const data = res?.data || {}
    const records = Array.isArray(data) ? data : data.records || []

    activities.value = records
    total.value = Number(data.total) || records.length
  } catch (err) {
    console.error('加载活动失败:', err)
    error.value = err?.message || '加载活动失败，请稍后重试'
  } finally {
    loading.value = false
  }
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

function getDateBlock(item) {
  const d = parseDate(item.startTime)
  if (isNaN(d.getTime())) return null
  const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return {
    month: `${d.getMonth() + 1}月`,
    day: String(d.getDate()).padStart(2, '0'),
    weekday: days[d.getDay()],
  }
}

function getActivityStatus(item) {
  const phase = getActivityPhase(item)
  const isFull = (item.currentPeople || 0) >= (item.maxPeople || 0)
  
  if (phase === 'ended') return STATUS_MAP.ended
  if (phase === 'ongoing') return STATUS_MAP.ongoing
  if (isFull) return STATUS_MAP.full
  return STATUS_MAP.upcoming
}

function getRemainingSeats(item) {
  return Math.max(0, (item.maxPeople || 0) - (item.currentPeople || 0))
}

function getSeatEmClass(item) {
  const left = getRemainingSeats(item)
  return { 'seat-low': left > 0 && left <= 10 }
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

function goToday() {
  const now = new Date()
  calendarYear.value = now.getFullYear()
  calendarMonth.value = now.getMonth()
  selectedDate.value = now.getDate()
}

function getCalendarDayMeta(item) {
  const sd = parseDate(item.startTime)
  const ed = parseDate(item.endTime)
  if (isNaN(sd.getTime())) return ''
  const sel = new Date(calendarYear.value, calendarMonth.value, selectedDate.value)
  const selDay = new Date(sel.getFullYear(), sel.getMonth(), sel.getDate())
  const startDay = new Date(sd.getFullYear(), sd.getMonth(), sd.getDate())
  const endDay = ed && !isNaN(ed.getTime()) ? new Date(ed.getFullYear(), ed.getMonth(), ed.getDate()) : startDay
  const hm = (d) => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  const sameDay = startDay.getTime() === endDay.getTime()
  if (sameDay && selDay.getTime() === startDay.getTime()) return `${hm(sd)} - ${hm(ed)}`
  if (selDay.getTime() === startDay.getTime()) return `${hm(sd)} 开始`
  if (selDay.getTime() === endDay.getTime()) return `${hm(ed)} 结束`
  return '全天进行'
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

  // 先更新本地状态，保证界面即时反馈
  if (isFav) {
    favorites.value.delete(id)
  } else {
    favorites.value.add(id)
  }
  favorites.value = new Set(favorites.value)
  saveFavoritesToStorage()

  // 同步到后端，失败时回滚本地状态
  try {
    if (isFav) {
      await removeFavorite(id)
    } else {
      await addFavorite(id)
    }
  } catch (err) {
    console.error('收藏同步失败:', err)
    if (isFav) {
      favorites.value.add(id)
    } else {
      favorites.value.delete(id)
    }
    favorites.value = new Set(favorites.value)
    saveFavoritesToStorage()
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
  const targetDay = new Date(year, month, day)
  return visibleActivities.value.filter(item => {
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

    <main ref="pageRef" class="ca-page">
      <div class="ca-container">
        <!-- Page header -->
        <header class="ca-header">
          <div class="ca-header__copy">
            <h2>校园活动</h2>
          </div>
          <div class="ca-header__actions">
            <div class="ca-seg ca-seg--view">
              <button
                :class="['ca-seg__item', { active: !showCalendar }]"
                @click="showCalendar = false"
                title="卡片视图"
                aria-label="卡片视图"
              >
                <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/></svg>
              </button>
              <button
                :class="['ca-seg__item', { active: showCalendar }]"
                @click="showCalendar = true"
                title="日历视图"
                aria-label="日历视图"
              >
                <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
              </button>
            </div>
            <span v-if="!loading && !error" class="ca-header__pill">
              <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
              <span>共 {{ total }} 场活动</span>
            </span>
          </div>
        </header>

        <!-- Filter toolbar -->
        <section class="ca-toolbar">
          <div class="ca-toolbar__row">
            <span class="ca-toolbar__label">活动分类</span>
            <div class="ca-chips">
              <button
                v-for="cat in categoryList"
                :key="cat.id"
                :class="['ca-chip', { active: selectedCategory === cat.id }]"
                @click="selectedCategory = cat.id; currentPage = 1; loadActivities()"
              >
                {{ cat.categoryName }}
              </button>
            </div>
          </div>

          <div class="ca-toolbar__row ca-toolbar__main">
            <div class="ca-search">
              <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              <input
                v-model="searchText"
                type="text"
                placeholder="搜索活动名称、主办方、关键词…"
                @keyup.enter="handleSearch"
              />
              <button class="ca-search__btn" @click="handleSearch">搜索</button>
            </div>

            <div class="ca-seg ca-seg--time">
              <button
                v-for="tf in TIME_FILTERS"
                :key="tf.key"
                :class="['ca-seg__item', { active: selectedTime === tf.key }]"
                @click="selectedTime = tf.key; currentPage = 1; loadActivities()"
              >
                {{ tf.label }}
              </button>
            </div>

          </div>
        </section>

        <!-- Loading / error / empty -->
        <div v-if="loading" class="ca-state">
          <div class="ca-spinner"></div>
          <p>正在加载活动…</p>
        </div>

        <div v-else-if="error" class="ca-state ca-state--error">
          <svg class="ca-state__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <p>{{ error }}</p>
          <button class="ca-btn" @click="loadActivities">重新加载</button>
        </div>

        <div v-else-if="activities.length === 0" class="ca-state">
          <svg class="ca-state__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/></svg>
          <p class="ca-state__title">{{ hasActiveFilters ? '没有找到匹配的活动' : '暂无校园活动，敬请期待' }}</p>
          <p class="ca-state__hint">{{ hasActiveFilters ? '试试调整分类、时间或搜索关键词' : '新活动正在路上，可以先逛逛其它页面' }}</p>
          <button v-if="hasActiveFilters" class="ca-btn" @click="clearFilters">清除筛选条件</button>
        </div>
        <template v-else>
          <!-- Calendar view -->
          <div v-if="showCalendar" class="ca-calendar">
            <div class="ca-calendar__panel">
              <div class="ca-calendar__head">
                <button class="ca-calendar__nav" @click="prevMonth" aria-label="上个月">
                  <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="15 18 9 12 15 6"/></svg>
                </button>
                <h3>{{ calendarYear }}年{{ calendarMonth + 1 }}月</h3>
                <button class="ca-calendar__nav" @click="nextMonth" aria-label="下个月">
                  <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="9 18 15 12 9 6"/></svg>
                </button>
                <button class="ca-calendar__today" @click="goToday">回到今天</button>
              </div>

              <div class="ca-calendar__week">
                <span v-for="w in ['日', '一', '二', '三', '四', '五', '六']" :key="w">{{ w }}</span>
              </div>

              <div class="ca-calendar__grid">
                <template v-for="(day, idx) in getCalendarDays()" :key="idx">
                  <div
                    v-if="day"
                    :class="[
                      'ca-cal-day',
                      {
                        today: day === now.getDate() && calendarMonth === now.getMonth() && calendarYear === now.getFullYear(),
                        selected: day === selectedDate
                      }
                    ]"
                    @click="selectedDate = day"
                  >
                    <span class="ca-cal-day__num">{{ day }}</span>
                    <div v-if="getActivitiesForDay(day).length > 0" class="ca-cal-day__dots">
                      <span
                        v-for="act in getActivitiesForDay(day).slice(0, 3)"
                        :key="act.id"
                        :class="['ca-cal-dot', getActivityStatus(act).class]"
                        :title="act.title"
                      ></span>
                      <span v-if="getActivitiesForDay(day).length > 3" class="ca-cal-day__more">+{{ getActivitiesForDay(day).length - 3 }}</span>
                    </div>
                  </div>
                  <div v-else class="ca-cal-day ca-cal-day--empty"></div>
                </template>
              </div>

              <div class="ca-calendar__legend">
                <span class="ca-cal-legend-item"><i class="ca-cal-dot status-upcoming"></i>即将开始</span>
                <span class="ca-cal-legend-item"><i class="ca-cal-dot status-ongoing"></i>进行中</span>
                <span class="ca-cal-legend-item"><i class="ca-cal-dot status-ended"></i>已结束</span>
                <span class="ca-cal-legend-item"><i class="ca-cal-dot status-full"></i>已满</span>
                <button
                  type="button"
                  :class="['ca-cal-btn ca-cal-btn--right', { active: myActivitiesOnly }]"
                  @click="toggleMyActivities"
                >
                  <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                  我的活动
                </button>
              </div>
            </div>

            <div class="ca-calendar__side">
              <div class="ca-calendar__side-head">
                <div>
                  <span class="ca-calendar__date">{{ selectedDateKey.replace(/-/g, '/') }}</span>
                  <span class="ca-calendar__weekday">{{ getWeekday() }}</span>
                </div>
                <span class="ca-calendar__count">{{ activitiesForSelectedDate.length }} 个活动</span>
              </div>

              <div class="ca-calendar__list">
                <template v-if="activitiesForSelectedDate.length > 0">
                  <div
                    v-for="(item, index) in activitiesForSelectedDate"
                    :key="item.id"
                    class="ca-day-card"
                    :style="{ animationDelay: `${index * 60}ms` }"
                    @click="handleCardClick(item.id)"
                  >
                    <div class="ca-day-card__top">
                      <span v-if="item.category?.categoryName" class="ca-chip">{{ item.category.categoryName }}</span>
                      <span :class="['ca-badge ca-badge--sm', getActivityStatus(item).class]">{{ getActivityStatus(item).text }}</span>
                    </div>
                    <h4 class="ca-day-card__title">{{ item.title }}</h4>
                    <div class="ca-day-card__meta">
                      <span>
                        <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
                        {{ item.location || '线上活动' }}
                      </span>
                      <span>
                        <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                        {{ getCalendarDayMeta(item) }}
                      </span>
                    </div>
                    <div class="ca-day-card__foot">
                      <span class="ca-link">
                        活动详情
                        <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
                      </span>
                    </div>
                  </div>
                </template>
                <div v-else class="ca-calendar__empty">
                  <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                  <p>当天暂无活动</p>
                  <span>点击左侧日历日期，查看其它安排</span>
                </div>
              </div>

              <div v-if="activitiesForSelectedDate.length > 0" class="ca-calendar__side-foot">
                <span class="ca-link" @click="showCalendar = false">
                  切换为列表视图
                  <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
                </span>
              </div>
            </div>
          </div>

          <!-- Card grid view -->
          <div v-else class="ca-grid">
            <article
              v-for="(item, index) in activities"
              :key="item.id"
              class="ca-card"
              :style="{ animationDelay: `${index * 50}ms` }"
              @click="handleCardClick(item.id)"
            >
              <div class="ca-card__cover">
                <img v-if="item.coverImage && !failedCovers.has(item.id)" :src="item.coverImage" :alt="item.title" loading="lazy" @error="onCoverError(item.id)" />
                <div v-else class="ca-card__placeholder">
                  <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                </div>
                <div v-if="item.coverImage && !failedCovers.has(item.id)" class="ca-card__shade"></div>
                <div v-if="getDateBlock(item)" class="ca-card__date">
                  <svg class="ca-card__date-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                  <span class="ca-card__date-main">{{ getDateBlock(item).month }}{{ getDateBlock(item).day }}日</span>
                  <span class="ca-card__date-dot">·</span>
                  <span class="ca-card__date-week">{{ getDateBlock(item).weekday }}</span>
                </div>
                <span :class="['ca-badge', getActivityStatus(item).class]">
                  {{ getActivityStatus(item).text }}
                </span>
                <button
                  :class="['ca-fav', { active: favorites.has(item.id) }]"
                  :aria-label="favorites.has(item.id) ? '取消收藏' : '收藏'"
                  @click="handleToggleFav(item.id, $event)"
                >
                  <svg v-if="favorites.has(item.id)" class="ca-icon ca-fav__icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
                  <svg v-else class="ca-icon ca-fav__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                </button>
              </div>

              <div class="ca-card__body">
                <div class="ca-card__tags">
                  <span v-if="item.category?.categoryName" class="ca-chip">{{ item.category.categoryName }}</span>
                  <span class="ca-card__org">{{ item.organizerName || item.organizer?.realName || '未知主办方' }}</span>
                </div>

                <h3 class="ca-card__title">{{ item.title }}</h3>

                <ul class="ca-card__meta">
                  <li>
                    <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                    <span>{{ formatDate(item.startTime) }}</span>
                  </li>
                  <li>
                    <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
                    <span>{{ item.location || '线上活动' }}</span>
                  </li>
                  <li>
                    <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                    <span>{{ item.currentPeople || 0 }}/{{ item.maxPeople || 0 }} 人</span>
                    <em v-if="getRemainingSeats(item) > 0" :class="getSeatEmClass(item)">剩 {{ getRemainingSeats(item) }} 位</em>
                  </li>
                </ul>

                <div class="ca-card__foot">
                  <span class="ca-card__deadline">报名截止 {{ formatDate(item.signupEndTime) }}</span>
                  <span class="ca-link">
                    查看详情
                    <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
                  </span>
                </div>
              </div>
            </article>
          </div>

          <!-- 列表分页：每页 9 个活动（3 行 x 3 列），页数按活动总数计算 -->
          <div v-if="!showCalendar" class="ca-pagination">
            <span class="ca-pagination__info">共 <strong>{{ total }}</strong> 场 · <strong>{{ totalPages }}</strong> 页</span>
            <div class="ca-pagination__controls">
              <button
                class="ca-pagination__btn"
                :disabled="currentPage === 1"
                aria-label="上一页"
                @click="goToPage(currentPage - 1)"
              >
                <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="15 18 9 12 15 6"/></svg>
              </button>

              <template v-for="(item, index) in pageItems" :key="`${item}-${index}`">
                <span v-if="item === '...'" class="ca-pagination__ellipsis">…</span>
                <button
                  v-else
                  class="ca-pagination__num"
                  :class="{ active: item === currentPage }"
                  @click="goToPage(item)"
                >{{ item }}</button>
              </template>

              <button
                class="ca-pagination__btn"
                :disabled="currentPage === totalPages"
                aria-label="下一页"
                @click="goToPage(currentPage + 1)"
              >
                <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="9 18 15 12 9 6"/></svg>
              </button>
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
  color: #1f2937;
  animation: caPageIn 0.4s ease-out;
}

.ca-page {
  padding: 88px 20px 56px;
}

.ca-container {
  max-width: 1200px;
  margin: 0 auto;
}

.ca-icon {
  display: inline-block;
  flex: 0 0 auto;
  vertical-align: middle;
}

/* ---------- Header ---------- */
.ca-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin: 4px 0 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid transparent;
  border-image: linear-gradient(90deg, rgba(59, 130, 246, 0.25) 0%, rgba(148, 163, 184, 0.15) 50%, transparent 100%) 1;
  animation: caFadeDown 0.45s ease-out;
}

.ca-header__copy h2 {
  margin: 0;
  color: #17233a;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.4px;
  line-height: 1.25;
}


.ca-header__pill {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  padding: 6px 14px;
  border: 1px solid #dbe4ee;
  border-radius: 999px;
  background: #ffffff;
  color: #5b6b7d;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.ca-header__pill .ca-icon {
  width: 15px;
  height: 15px;
  color: #3b82f6;
}

.ca-header__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 2px;
}

/* ---------- Toolbar ---------- */
.ca-toolbar {
  padding: 18px 20px;
  margin-bottom: 24px;
  border: 1px solid #e6ecf3;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  animation: caFadeDown 0.45s ease-out 0.06s backwards;
}

.ca-toolbar__row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.ca-toolbar__row + .ca-toolbar__row {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f4f8;
}

.ca-toolbar__label {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
}

.ca-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.ca-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 14px;
  border: 1px solid #dbe4ee;
  border-radius: 999px;
  background: #ffffff;
  color: #5b6b7d;
  font-size: 13px;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.ca-chip:hover {
  border-color: #3b82f6;
  background: #eff6ff;
  color: #2563eb;
}

.ca-chip.active {
  border-color: #2563eb;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 4px 10px rgba(59, 130, 246, 0.25);
}

.ca-toolbar__main {
  align-items: center;
  gap: 14px;
}

.ca-search {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 260px;
  height: 42px;
  padding: 0 6px 0 14px;
  border: 1.5px solid #d7e0e8;
  border-radius: 10px;
  background: #ffffff;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.ca-search:focus-within {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.ca-search > .ca-icon {
  width: 17px;
  height: 17px;
  color: #8a99a9;
}

.ca-search input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 10px;
  border: 0;
  outline: 0;
  background: transparent;
  color: #26384d;
  font-size: 14px;
}

.ca-search input::placeholder {
  color: #9aa7b4;
}

.ca-search__btn {
  flex: 0 0 auto;
  height: 32px;
  padding: 0 16px;
  border-radius: 8px;
  background: #2563eb;
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.15s ease;
}

.ca-search__btn:hover {
  background: #1d4ed8;
}

.ca-search__btn:active {
  transform: translateY(1px);
}

.ca-seg {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid #e4eaf1;
  border-radius: 10px;
  background: #f3f6fa;
}

.ca-seg__item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 32px;
  padding: 0 13px;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.ca-seg__item:hover {
  color: #2563eb;
}

.ca-seg__item.active {
  background: #ffffff;
  color: #2563eb;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.08);
}

.ca-seg__item .ca-icon {
  width: 16px;
  height: 16px;
}

.ca-seg--view .ca-seg__item {
  width: 34px;
  padding: 0;
}
/* ---------- States ---------- */
.ca-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 280px;
  padding: 48px 20px;
  border: 1px solid #eef2f7;
  border-radius: 14px;
  background: #ffffff;
  color: #718096;
  text-align: center;
  animation: caFadeUp 0.35s ease-out;
}

.ca-state__icon {
  width: 44px;
  height: 44px;
  color: #a7b4c2;
  stroke-width: 1.5;
}

.ca-state p {
  margin: 0;
  font-size: 14px;
}

.ca-state p.ca-state__title {
  color: #475569;
  font-size: 15px;
  font-weight: 600;
}

.ca-state p.ca-state__hint {
  color: #8a99a9;
  font-size: 13px;
}

.ca-state--error {
  border-style: solid;
  border-color: #ebc4bf;
  background: #fffafa;
  color: #a54239;
}

.ca-spinner {
  width: 34px;
  height: 34px;
  border: 3px solid #e4eaf1;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: caSpin 0.8s linear infinite;
}

.ca-btn {
  min-height: 38px;
  padding: 0 18px;
  border: 1px solid #3b82f6;
  border-radius: 9px;
  background: #ffffff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.ca-btn:hover {
  background: #eff6ff;
  color: #1d4ed8;
}

/* ---------- Card grid ---------- */
.ca-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 20px;
}

.ca-card {
  display: flex;
  flex-direction: column;
  border: 1px solid #eef2f7;
  border-radius: 16px;
  background: #ffffff;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(23, 35, 58, 0.05);
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  animation: caCardIn 0.45s ease-out backwards;
}

.ca-card:hover {
  border-color: #bfdbfe;
  box-shadow: 0 20px 40px rgba(30, 43, 76, 0.12), 0 0 0 1px rgba(59, 130, 246, 0.08), 0 8px 24px rgba(59, 130, 246, 0.08);
  transform: translateY(-4px);
}

.ca-card__cover {
  position: relative;
  height: 176px;
  overflow: hidden;
  background: linear-gradient(160deg, #eef4ff 0%, #e3edf9 60%, #dbe8f6 100%);
}

.ca-card__shade {
  position: absolute;
  inset: auto 0 0 0;
  height: 64px;
  background: linear-gradient(180deg, rgba(10, 18, 30, 0) 0%, rgba(10, 18, 30, 0.32) 100%);
  pointer-events: none;
}

.ca-card__date {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 10px rgba(16, 24, 40, 0.14);
  backdrop-filter: blur(6px);
}

.ca-card__date-icon {
  width: 15px;
  height: 15px;
  color: #2563eb;
}

.ca-card__date-main {
  color: #17233a;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.ca-card__date-dot {
  color: #cbd5e1;
  font-size: 12px;
  line-height: 1;
}

.ca-card__date-week {
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}

.ca-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.ca-card:hover .ca-card__cover img {
  transform: scale(1.05);
}

.ca-card__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: linear-gradient(160deg, #eef4ff 0%, #e3edf9 60%, #dbe8f6 100%);
}

.ca-card__placeholder .ca-icon {
  width: 42px;
  height: 42px;
  color: #9db8d8;
  stroke-width: 1.4;
}

.ca-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
  backdrop-filter: blur(4px);
}

.ca-badge::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.ca-badge.status-upcoming {
  background: rgba(255, 247, 230, 0.94);
  color: #a06b12;
}

.ca-badge.status-ongoing {
  background: rgba(236, 248, 242, 0.94);
  color: #2e7d5b;
}

.ca-badge.status-ended {
  background: rgba(240, 243, 247, 0.94);
  color: #64748b;
}

.ca-badge.status-full {
  background: rgba(254, 240, 240, 0.94);
  color: #b4534a;
}

.ca-fav {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(228, 234, 241, 0.9);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  color: #7b8b9c;
  cursor: pointer;
  transition: color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.ca-fav:hover {
  color: #c0655f;
  box-shadow: 0 4px 10px rgba(16, 24, 40, 0.12);
  transform: scale(1.08);
}

.ca-fav.active {
  color: #c0655f;
}

.ca-fav__icon {
  width: 17px;
  height: 17px;
}

.ca-card__body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 18px 20px 20px;
}

.ca-card__tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.ca-card__tags .ca-chip {
  min-height: 24px;
  padding: 0 9px;
  font-size: 12px;
}

.ca-card__org {
  overflow: hidden;
  color: #8a99a9;
  font-size: 12px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ca-card__title {
  margin: 0 0 12px;
  color: #17233a;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ca-card__meta {
  display: grid;
  gap: 7px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ca-card__meta li {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.4;
}

.ca-card__meta li > span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ca-card__meta .ca-icon {
  width: 15px;
  height: 15px;
  color: #8a99a9;
}

.ca-card__meta em {
  margin-left: 2px;
  color: #2e7d5b;
  font-size: 12px;
  font-style: normal;
  font-weight: 600;
  white-space: nowrap;
}

.ca-card__meta em.seat-low {
  color: #dc2626;
}

.ca-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f4f8;
}

.ca-card__deadline {
  color: #64748b;
  font-size: 12px;
}

.ca-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s ease;
}

.ca-link:hover {
  color: #1d4ed8;
}

.ca-link .ca-icon {
  width: 15px;
  height: 15px;
  transition: transform 0.2s ease;
}

.ca-card:hover .ca-link .ca-icon,
.ca-day-card:hover .ca-link .ca-icon {
  transform: translateX(3px);
}
/* ---------- Pagination ---------- */
.ca-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 28px;
  padding: 14px 20px;
  border: 1px solid #e6ecf3;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  animation: caFadeUp 0.35s ease-out 0.1s backwards;
}

.ca-pagination__info {
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.ca-pagination__info strong {
  color: #17233a;
  font-weight: 700;
}

.ca-pagination__controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ca-pagination__btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid #dbe4ee;
  border-radius: 9px;
  background: #ffffff;
  color: #5b6b7d;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.ca-pagination__btn:hover:not(:disabled) {
  border-color: #3b82f6;
  background: #eff6ff;
  color: #2563eb;
}

.ca-pagination__btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.ca-pagination__btn .ca-icon {
  width: 16px;
  height: 16px;
}

.ca-pagination__num {
  min-width: 34px;
  height: 34px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  color: #5b6b7d;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.ca-pagination__num:hover {
  border-color: #dbe4ee;
  background: #f8fafc;
  color: #2563eb;
}

.ca-pagination__num.active {
  border-color: #2563eb;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
  font-weight: 700;
  box-shadow: 0 4px 10px rgba(59, 130, 246, 0.25);
  cursor: default;
}

.ca-pagination__ellipsis {
  min-width: 22px;
  text-align: center;
  color: #8a99a9;
  font-size: 13px;
  user-select: none;
}
/* ---------- Calendar ---------- */
.ca-calendar {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.85fr);
  gap: 20px;
  align-items: start;
  animation: caFadeUp 0.35s ease-out;
}

.ca-calendar__panel,
.ca-calendar__side {
  border: 1px solid #e6ecf3;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.ca-calendar__panel {
  padding: 20px;
}

.ca-calendar__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.ca-calendar__head h3 {
  flex: 1;
  margin: 0;
  color: #17233a;
  font-size: 18px;
  font-weight: 700;
  text-align: center;
}

.ca-calendar__today {
  flex: 0 0 auto;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.ca-calendar__today:hover {
  border-color: #3b82f6;
  background: #eff6ff;
}

.ca-calendar__nav {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
  color: #64748b;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.ca-calendar__nav:hover {
  border-color: #3b82f6;
  background: #eff6ff;
  color: #2563eb;
}

.ca-calendar__nav .ca-icon {
  width: 16px;
  height: 16px;
}

.ca-calendar__week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
  margin-bottom: 8px;
}

.ca-calendar__week span {
  padding: 4px 0;
  color: #8a99a9;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}

.ca-calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
}

.ca-cal-day {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 64px;
  padding: 8px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #ffffff;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.ca-cal-day:hover {
  border-color: #dbe4ee;
  background: #f1f5f9;
  transform: translateY(-1px);
}

.ca-cal-day--empty {
  background: transparent;
  cursor: default;
}

.ca-cal-day--empty:hover {
  background: transparent;
  transform: none;
}

.ca-cal-day.today {
  border-color: #3b82f6;
}

.ca-cal-day.selected {
  border-color: #2563eb;
  background: #eff6ff;
}

.ca-cal-day__num {
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.ca-cal-day.today .ca-cal-day__num,
.ca-cal-day.selected .ca-cal-day__num {
  color: #2563eb;
}

.ca-cal-day__dots {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: auto;
  padding-top: 8px;
  min-height: 10px;
}

.ca-cal-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c3d0dd;
}

.ca-cal-dot.status-upcoming {
  background: #d8a24a;
}

.ca-cal-dot.status-ongoing {
  background: #4e9d76;
}

.ca-cal-dot.status-ended {
  background: #a7b4c2;
}

.ca-cal-dot.status-full {
  background: #c0655f;
}

.ca-cal-day__more {
  margin-left: 2px;
  color: #8a99a9;
  font-size: 10px;
  line-height: 1;
}

.ca-calendar__legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f0f4f8;
}

.ca-cal-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #475569;
  font-size: 12px;
  line-height: 1;
}

.ca-calendar__legend .ca-cal-dot.status-ended {
  background: #8a99a9;
}

.ca-calendar__legend .ca-cal-dot.status-full {
  background: #dc2626;
}

.ca-cal-legend-item .ca-cal-dot {
  width: 7px;
  height: 7px;
}

.ca-calendar__side {
  padding: 20px;
}

.ca-calendar__side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.ca-calendar__date {
  display: block;
  color: #17233a;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.ca-calendar__weekday {
  display: block;
  margin-top: 2px;
  color: #8a99a9;
  font-size: 12px;
}

.ca-calendar__count {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.ca-calendar__count::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.ca-calendar__list {
  display: grid;
  gap: 10px;
}

.ca-day-card {
  padding: 14px 16px;
  border: 1px solid #e6ecf3;
  border-radius: 12px;
  background: #ffffff;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
  animation: caCardIn 0.4s ease-out backwards;
}

.ca-day-card:hover {
  border-color: #bfdbfe;
  box-shadow: 0 8px 20px rgba(30, 43, 76, 0.08);
  transform: translateY(-2px);
}

.ca-day-card__top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.ca-day-card__top .ca-chip {
  min-height: 22px;
  padding: 0 8px;
  font-size: 11px;
}

.ca-badge--sm {
  position: static;
  padding: 3px 9px;
  font-size: 11px;
}

.ca-badge--sm::before {
  width: 4px;
  height: 4px;
}

.ca-day-card__title {
  margin: 0 0 8px;
  color: #17233a;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ca-day-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  color: #64748b;
  font-size: 12px;
}

.ca-day-card__meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.ca-day-card__meta .ca-icon {
  width: 14px;
  height: 14px;
  color: #8a99a9;
}

.ca-day-card__foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.ca-calendar__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 200px;
  color: #8a99a9;
  font-size: 13px;
  text-align: center;
}

.ca-calendar__empty .ca-icon {
  width: 34px;
  height: 34px;
  color: #b3bfcc;
  stroke-width: 1.4;
}

.ca-calendar__empty p {
  margin: 0;
}

.ca-calendar__empty span {
  color: #a7b4c2;
  font-size: 12px;
}

.ca-cal-btn--right {
  margin-left: auto;
}

.ca-cal-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 14px;
  border: 1px solid #dbe4ee;
  border-radius: 999px;
  background: #ffffff;
  color: #5b6b7d;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.ca-cal-btn:hover {
  border-color: #3b82f6;
  color: #2563eb;
}

.ca-cal-btn.active {
  border-color: #2563eb;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
  box-shadow: 0 4px 10px rgba(59, 130, 246, 0.25);
}

.ca-cal-btn .ca-icon {
  width: 15px;
  height: 15px;
}

.ca-calendar__side-foot {
  display: flex;
  justify-content: center;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f0f4f8;
}

/* ---------- Keyframes ---------- */
@keyframes caPageIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes caFadeDown {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes caFadeUp {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes caCardIn {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes caSpin {
  to { transform: rotate(360deg); }
}

/* ---------- Responsive ---------- */
@media (max-width: 960px) {
  .ca-calendar {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .ca-page {
    padding: 84px 16px 40px;
  }

  .ca-header__copy h2 {
    font-size: 24px;
  }

  .ca-toolbar {
    padding: 16px;
  }

  .ca-chips {
    flex-wrap: nowrap;
    overflow-x: auto;
    scrollbar-width: none;
    padding-bottom: 4px;
  }

  .ca-chips::-webkit-scrollbar {
    display: none;
  }

  .ca-chip {
    min-height: 36px;
    flex: 0 0 auto;
  }

  .ca-toolbar__main {
    align-items: stretch;
    flex-direction: column;
  }

  .ca-search {
    min-width: 0;
    width: 100%;
  }

  .ca-seg--time {
    max-width: 100%;
    overflow-x: auto;
  }

  .ca-seg--time .ca-seg__item {
    flex: 0 0 auto;
  }

  .ca-seg--view {
    align-self: flex-start;
  }

  .ca-grid {
    grid-template-columns: 1fr;
  }

  .ca-fav {
    width: 40px;
    height: 40px;
  }

  .ca-fav__icon {
    width: 19px;
    height: 19px;
  }

  .ca-cal-day {
    min-height: 60px;
    padding: 6px;
  }

  .ca-pagination {
    flex-direction: column;
    align-items: center;
    gap: 12px;
  }
}
</style>
