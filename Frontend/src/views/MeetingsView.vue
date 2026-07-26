<script setup>
import { computed, onMounted, ref } from 'vue'

import { createQuickMeeting, getMeetings } from '../api/meetings'
import AppTabBar from '../components/AppTabBar.vue'
import { getUserInfo } from '../utils/auth'

const loading = ref(false)
const creating = ref(false)
const errorMessage = ref('')
const meetings = ref([])
const meetingTitle = ref('项目进度同步会')

const countdownMinutes = computed(() => {
  const upcoming = meetings.value
    .filter((m) => m.status !== 'ended' && (m.scheduledStartTime || m.startTime))
    .map((m) => new Date(m.scheduledStartTime || m.startTime))
    .filter((d) => !Number.isNaN(d.getTime()) && d.getTime() > Date.now())
    .sort((a, b) => a - b)
  if (upcoming.length === 0) return null
  return Math.ceil((upcoming[0].getTime() - Date.now()) / 60000)
})

const upcomingCount = computed(() => {
  return meetings.value.filter((m) => m.status !== 'ended').length
})

const scheduleMeetings = computed(() => {
  return meetings.value
    .filter((m) => m.status !== 'ended')
    .slice(0, 2)
    .map((m) => {
      const source = m.scheduledStartTime || m.startTime || m.createTime
      const date = source ? new Date(source) : null
      const hh = date && !Number.isNaN(date.getTime()) ? String(date.getHours()).padStart(2, '0') : '--'
      const mm = date && !Number.isNaN(date.getTime()) ? String(date.getMinutes()).padStart(2, '0') : '--'
      const endHh = date && !Number.isNaN(date.getTime()) ? String(date.getHours() + 1).padStart(2, '0') : '--'
      return {
        time: `${hh}:${mm} - ${endHh}:${mm}`,
        organizer: m.creatorName || m.title || '未知',
      }
    })
})

function formatRoomCode(roomCode) {
  return (roomCode || '未生成').replace(/(.{3})/g, '$1 ').trim()
}

async function loadMeetings() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await getMeetings({ pageNum: 1, pageSize: 20 })
    meetings.value = result.data?.records || []
  } catch (error) {
    meetings.value = []
    errorMessage.value = error.message || '会议列表暂时无法加载'
  } finally {
    loading.value = false
  }
}

async function startQuickMeeting() {
  creating.value = true
  errorMessage.value = ''
  const user = getUserInfo() || {}

  try {
    await createQuickMeeting({
      title: meetingTitle.value || '快速会议',
      participants: [
        {
          displayName: user.realName || user.username || '我',
          userId: user.userId || user.id,
        },
      ],
    })
    meetingTitle.value = '项目进度同步会'
    await loadMeetings()
  } catch (error) {
    errorMessage.value = error.message || '会议创建失败'
  } finally {
    creating.value = false
  }
}

onMounted(loadMeetings)
</script>

<template>
  <div class="phone-shell">
    <AppTabBar />

    <main class="page">
      <div class="meetings-layout">
        <!-- 左列：Meeting 标题 + 四个按钮 -->
        <div class="left-col">
          <div class="meeting-header">
            <p class="eyebrow">Meeting</p>
            <h1>会议</h1>
          </div>

          <button class="action-btn action-btn--join" type="button">加入会议</button>
          <button class="action-btn action-btn--start" type="button" @click="startQuickMeeting">
            {{ creating ? '创建中...' : '召开会议' }}
          </button>
          <button class="action-btn action-btn--reserve" type="button">预定会议</button>
          <button class="action-btn action-btn--history" type="button">历史会议</button>
        </div>

        <!-- 右列：倒计时 + 日程卡片 -->
        <div class="right-col">
          <p class="countdown-text">
            {{ countdownMinutes != null ? `距离下一场会议还有${countdownMinutes}分钟` : '暂无待参加会议' }}
          </p>

          <div class="schedule-card card">
            <div class="schedule-card__head">
              <span class="schedule-card__title">日程</span>
              <button class="schedule-card__add" type="button">+添加</button>
            </div>

            <div class="schedule-card__body">
              <div v-if="loading" class="schedule-loading">加载中...</div>
              <template v-else>
                <div v-for="(item, idx) in scheduleMeetings" :key="idx" class="schedule-item">
                  <p class="schedule-item__time">{{ item.time }}</p>
                  <p class="schedule-item__organizer">{{ item.organizer }}</p>
                </div>
                <p v-if="scheduleMeetings.length === 0" class="schedule-empty">暂无日程</p>
              </template>

              <button class="copy-btn" type="button">选择会议以复制会议号</button>

              <p class="upcoming-count">未来还有{{ upcomingCount }}个待参加会议</p>
            </div>
          </div>
        </div>
      </div>

      <p v-if="errorMessage" class="meeting-error">{{ errorMessage }}</p>
    </main>
  </div>
</template>

<style scoped>
.meetings-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* ── 左列 ─────────────────────────────── */
.left-col {
  flex: 0 0 35%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

.meeting-header h1 {
  margin: 0;
  color: #111827;
  font-size: 24px;
  line-height: 1.2;
}

.action-btn {
  width: 72%;
  min-height: 42px;
  border-radius: 8px;
  color: #ffffff;
  font-weight: 700;
  font-size: 14px;
}

.action-btn--join   { background: #2563eb; }
.action-btn--start  { background: #0f766e; }
.action-btn--reserve { background: #4f46e5; }
.action-btn--history { background: #64748b; }

/* ── 右列 ─────────────────────────────── */
.right-col {
  flex: 1;
  min-width: 0;
}

.countdown-text {
  margin: 0 0 12px;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

/* ── 日程卡片 ─────────────────────────── */
.schedule-card {
  overflow: hidden;
}

.schedule-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #eef2f7;
}

.schedule-card__title {
  color: #111827;
  font-size: 15px;
  font-weight: 800;
}

.schedule-card__add {
  min-height: 28px;
  padding: 0 10px;
  border-radius: 6px;
  color: #ffffff;
  background: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.schedule-card__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px 16px;
}

.schedule-loading,
.schedule-empty {
  margin: 0;
  padding: 16px 0;
  color: #64748b;
  text-align: center;
  font-size: 13px;
}

.schedule-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.schedule-item__time {
  margin: 0;
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.schedule-item__organizer {
  margin: 0;
  color: #374151;
  font-size: 13px;
}

.copy-btn {
  width: 100%;
  min-height: 38px;
  margin-top: 8px;
  border-radius: 8px;
  color: #ffffff;
  background: #16a34a;
  font-size: 13px;
  font-weight: 700;
}

.upcoming-count {
  margin: 10px 0 0;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  text-align: center;
}

/* ── 错误提示 ─────────────────────────── */
.meeting-error {
  margin: 12px 0 0;
  padding: 12px 14px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #dc2626;
  background: #fff7f7;
}
</style>
