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

const todayLabel = computed(() => {
  const now = new Date()
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return `今天 · ${now.getMonth() + 1}月${now.getDate()}日 ${weekdays[now.getDay()]}`
})

function formatRoomCode(roomCode) {
  return (roomCode || '未生成').replace(/(.{3})/g, '$1 ').trim()
}

function meetingTime(meeting) {
  const source = meeting.scheduledStartTime || meeting.startTime || meeting.createTime
  if (!source) return meeting.meetingType === 'reserved' ? '待开始' : '会议中'
  const date = new Date(source)
  if (Number.isNaN(date.getTime())) return meeting.meetingType === 'reserved' ? '待开始' : '会议中'
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return meeting.meetingType === 'reserved' && meeting.status !== 'active'
    ? `${hh}:${mm} 预约`
    : `${hh}:${mm} 开始`
}

function meetingStatus(meeting) {
  const statusMap = {
    active: '进行中',
    idle: '待开始',
    paused: '已暂停',
    ended: '已结束',
  }
  return statusMap[meeting.status] || '会议'
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
      <header class="topbar">
        <div>
          <p class="eyebrow">Meeting</p>
          <h1>会议</h1>
        </div>
      </header>

      <section class="meeting-hero card">
        <div>
          <p>高效会议</p>
          <h2>从这里开始</h2>
        </div>
        <div class="meeting-visual">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </section>

      <section class="quick-start card">
        <label>
          <span>会议主题</span>
          <input v-model="meetingTitle" placeholder="请输入会议主题" />
        </label>
        <button class="primary-button" :disabled="creating" type="button" @click="startQuickMeeting">
          {{ creating ? '正在创建...' : '发起会议' }}
        </button>
      </section>

      <p v-if="errorMessage" class="meeting-error">{{ errorMessage }}</p>

      <section class="meeting-list card">
        <div class="meeting-list__head">
          <h2>会议列表</h2>
          <span>{{ todayLabel }}</span>
        </div>

        <p v-if="loading" class="empty-state">正在加载会议...</p>
        <p v-else-if="meetings.length === 0" class="empty-state">暂无会议，先发起一场会议吧。</p>

        <article
          v-for="meeting in meetings"
          v-else
          :key="meeting.sessionId || meeting.id"
          class="meeting-item"
          :class="{ 'meeting-item--active': meeting.status === 'active' }"
        >
          <div>
            <strong>{{ meeting.title || '未命名会议' }}</strong>
            <span>{{ meetingTime(meeting) }}</span>
            <span>会议号：{{ formatRoomCode(meeting.roomCode) }}</span>
          </div>
          <em>{{ meetingStatus(meeting) }}</em>
        </article>
      </section>
    </main>
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

.meeting-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px;
  color: #ffffff;
  background: linear-gradient(135deg, #0f766e, #2563eb);
}

.meeting-hero p,
.meeting-hero h2 {
  margin: 0;
}

.meeting-hero p {
  opacity: 0.88;
}

.meeting-hero h2 {
  margin-top: 8px;
  font-size: 26px;
}

.meeting-visual {
  position: relative;
  display: grid;
  place-items: center;
  width: 96px;
  height: 78px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.18);
}

.meeting-visual span {
  position: absolute;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ffffff;
}

.meeting-visual span:nth-child(1) {
  top: 16px;
  left: 18px;
}

.meeting-visual span:nth-child(2) {
  top: 16px;
  right: 18px;
}

.meeting-visual span:nth-child(3) {
  bottom: 14px;
}

.quick-start {
  display: grid;
  gap: 14px;
  margin-top: 16px;
  padding: 16px;
}

label {
  display: grid;
  gap: 8px;
  color: #374151;
  font-size: 14px;
  font-weight: 700;
}

input {
  width: 100%;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  outline: none;
  background: #f9fbff;
}

.meeting-error {
  margin: 12px 0 0;
  padding: 12px 14px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #dc2626;
  background: #fff7f7;
}

.meeting-list {
  margin-top: 16px;
  overflow: hidden;
}

.meeting-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px;
  border-bottom: 1px solid #eef2f7;
}

.meeting-list__head h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
}

.meeting-list__head span {
  color: #64748b;
  font-size: 13px;
}

.empty-state {
  margin: 0;
  padding: 22px 16px;
  color: #64748b;
  text-align: center;
}

.meeting-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 16px;
  border-bottom: 1px solid #eef2f7;
  background: #ffffff;
}

.meeting-item:last-child {
  border-bottom: 0;
}

.meeting-item--active {
  background: #f0fdf4;
}

.meeting-item div {
  display: grid;
  gap: 5px;
}

.meeting-item strong {
  color: #111827;
}

.meeting-item span {
  color: #64748b;
  font-size: 13px;
}

.meeting-item em {
  flex: 0 0 auto;
  padding: 5px 9px;
  border-radius: 999px;
  color: #0f766e;
  background: #ccfbf1;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}
</style>
