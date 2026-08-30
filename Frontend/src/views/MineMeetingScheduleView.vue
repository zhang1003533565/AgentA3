<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getMeetings } from '../api/meetings'
import AppTabBar from '../components/AppTabBar.vue'

const router = useRouter()
const loading = ref(false)
const meetings = ref([])
const selectedDate = ref(formatDateKey(new Date()))
const viewMonth = ref(new Date())

function formatDateKey(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatTime(value) {
  if (!value) return '时间待定'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatRoomCode(roomCode) {
  return roomCode ? String(roomCode).replace(/(.{3})/g, '$1 ').trim() : '—'
}

function statusLabel(status) {
  return ({ active: '进行中', idle: '待开始', reserved: '已预约', ended: '已结束' })[status] || status || '会议'
}

const monthLabel = computed(() => `${viewMonth.value.getFullYear()}年${viewMonth.value.getMonth() + 1}月`)

const monthDays = computed(() => {
  const year = viewMonth.value.getFullYear()
  const month = viewMonth.value.getMonth()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const weekdays = ['日', '一', '二', '三', '四', '五', '六']
  return Array.from({ length: daysInMonth }, (_, index) => {
    const day = index + 1
    const date = new Date(year, month, day)
    const key = formatDateKey(date)
    const count = meetings.value.filter((item) => meetingDateKey(item) === key).length
    return {
      key,
      day,
      weekday: weekdays[date.getDay()],
      count,
      isToday: key === formatDateKey(new Date()),
      isSelected: key === selectedDate.value,
    }
  })
})

const dayMeetings = computed(() =>
  meetings.value
    .filter((item) => meetingDateKey(item) === selectedDate.value)
    .sort((a, b) => new Date(a.scheduledStartTime || a.startTime || a.createTime) - new Date(b.scheduledStartTime || b.startTime || b.createTime)),
)

function meetingDateKey(meeting) {
  const raw = meeting.scheduledStartTime || meeting.startTime || meeting.createTime
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return String(raw).slice(0, 10)
  return formatDateKey(date)
}

function rowsOf(response) {
  return response?.data?.records || response?.data?.content || response?.data || []
}

async function loadMeetings() {
  loading.value = true
  try {
    const result = await getMeetings({ pageNum: 1, pageSize: 100 })
    meetings.value = rowsOf(result)
  } catch {
    meetings.value = []
  } finally {
    loading.value = false
  }
}

function shiftMonth(step) {
  const next = new Date(viewMonth.value)
  next.setMonth(next.getMonth() + step)
  viewMonth.value = next
}

function selectDay(key) {
  selectedDate.value = key
}

function openMeeting(meeting) {
  const id = meeting.sessionId || meeting.id
  if (!id) {
    router.push('/meetings')
    return
  }
  if (meeting.status === 'active') {
    router.push(`/meetings/room/${id}`)
    return
  }
  router.push('/meetings')
}

onMounted(loadMeetings)
</script>

<template>
  <div class="feature-page">
    <AppTabBar />

    <main class="feature-container schedule-page">
      <header class="schedule-page__head">
        <div>
          <h1>会议日程</h1>
          <p>按日期查看已预约与历史会议，与 APP 日程页使用同一套会议数据。</p>
        </div>
        <button class="meet-btn meet-btn--primary" type="button" @click="router.push('/meetings?mode=reserve')">
          预约会议
        </button>
      </header>

      <section class="feature-card schedule-layout">
        <aside class="schedule-calendar">
          <div class="schedule-calendar__head">
            <button type="button" class="schedule-calendar__nav" @click="shiftMonth(-1)">‹</button>
            <strong>{{ monthLabel }}</strong>
            <button type="button" class="schedule-calendar__nav" @click="shiftMonth(1)">›</button>
          </div>

          <div class="schedule-calendar__grid">
            <button
              v-for="item in monthDays"
              :key="item.key"
              type="button"
              class="schedule-day"
              :class="{
                'schedule-day--selected': item.isSelected,
                'schedule-day--today': item.isToday,
              }"
              @click="selectDay(item.key)"
            >
              <span class="schedule-day__week">周{{ item.weekday }}</span>
              <strong>{{ item.day }}</strong>
              <em v-if="item.count">{{ item.count }} 场</em>
            </button>
          </div>
        </aside>

        <section class="schedule-list">
          <div class="schedule-list__head">
            <h2>{{ selectedDate }} 的会议</h2>
            <span>{{ dayMeetings.length }} 场</span>
          </div>

          <p v-if="loading" class="feature-empty">正在加载日程…</p>
          <p v-else-if="!dayMeetings.length" class="feature-empty">当日暂无会议日程</p>

          <div v-else class="schedule-cards">
            <article v-for="meeting in dayMeetings" :key="meeting.sessionId || meeting.id" class="schedule-card">
              <div class="schedule-card__time">{{ formatTime(meeting.scheduledStartTime || meeting.startTime) }}</div>
              <div class="schedule-card__body">
                <strong>{{ meeting.title || '未命名会议' }}</strong>
                <p>
                  <span>{{ statusLabel(meeting.status) }}</span>
                  · 会议号 {{ formatRoomCode(meeting.roomCode) }}
                </p>
              </div>
              <button type="button" class="meet-btn" @click="openMeeting(meeting)">详情</button>
            </article>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<style scoped>
.schedule-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.schedule-page__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.schedule-page__head h1 {
  margin: 0;
  color: #17233a;
  font-size: 28px;
}

.schedule-page__head p {
  margin: 8px 0 0;
  color: #718096;
  font-size: 14px;
}

.schedule-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 20px;
  padding: 20px;
}

.schedule-calendar__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.schedule-calendar__head strong {
  color: #17233a;
  font-size: 16px;
}

.schedule-calendar__nav {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #475569;
  background: #f1f5f9;
  font-size: 18px;
}

.schedule-calendar__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.schedule-day {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px;
  border: 1px solid #e5eaf0;
  border-radius: 12px;
  background: #fff;
  text-align: left;
}

.schedule-day--selected {
  border-color: #3b82f6;
  background: #eff6ff;
}

.schedule-day--today strong {
  color: #2563eb;
}

.schedule-day__week {
  color: #94a3b8;
  font-size: 11px;
}

.schedule-day strong {
  color: #17233a;
  font-size: 20px;
}

.schedule-day em {
  color: #2563eb;
  font-size: 11px;
  font-style: normal;
}

.schedule-list__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.schedule-list__head h2 {
  margin: 0;
  color: #17233a;
  font-size: 18px;
}

.schedule-list__head span {
  color: #64748b;
  font-size: 13px;
}

.schedule-cards {
  display: grid;
  gap: 12px;
}

.schedule-card {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #e5eaf0;
  border-radius: 12px;
  background: #fafbfd;
}

.schedule-card__time {
  color: #2563eb;
  font-size: 18px;
  font-weight: 700;
}

.schedule-card__body strong {
  display: block;
  color: #17233a;
  font-size: 15px;
}

.schedule-card__body p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.meet-btn {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #34506c;
  background: #ffffff;
  font-size: 13px;
  font-weight: 700;
}

.meet-btn--primary {
  border-color: #3b82f6;
  color: #ffffff;
  background: #3b82f6;
}

@media (max-width: 960px) {
  .schedule-layout {
    grid-template-columns: 1fr;
  }
}
</style>
