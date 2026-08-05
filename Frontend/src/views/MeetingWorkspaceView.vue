<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  createQuickMeeting,
  deleteMeeting,
  endMeeting,
  getMeetingDetail,
  getMeetings,
  joinMeeting,
  organizeMeeting,
  reserveMeeting,
  startMeeting,
} from '../api/meetings'
import AppTabBar from '../components/AppTabBar.vue'
import { getUserInfo } from '../utils/auth'

const router = useRouter()

const mode = ref('quick')
const reserveTab = ref('reserve')

const subMode = computed(() => (mode.value === 'reserve' ? reserveTab.value : mode.value))
const meetings = ref([])
const loading = ref(false)
const submitting = ref(false)
const busyId = ref('')
const error = ref('')
const notice = ref('')
const form = ref({ title: '', roomCode: '', displayName: '', scheduledStartTime: '' })
const duration = ref(60)
const micOn = ref(true)
const participants = ref([])
const participantQuery = ref('')

const durationOptions = [
  { value: 30, label: '30 分钟' },
  { value: 60, label: '1 小时' },
  { value: 90, label: '1.5 小时' },
  { value: 120, label: '2 小时' },
]

const menuItems = [
  { key: 'quick', label: '快速开始', desc: '立即发起一场会议' },
  { key: 'reserve', label: '预约会议', desc: '提前安排会议时间' },
  { key: 'join', label: '加入会议', desc: '通过会议号加入' },
]

const createHeading = computed(
  () => ({ quick: '发起会议', reserve: '预约会议', join: '加入会议' })[subMode.value],
)
const createDesc = computed(
  () =>
    ({
      quick: '填写会议主题，一键发起视频会议并生成会议号。',
      reserve: '设定主题、时间与参会人，提前安排一场会议。',
      join: '输入他人分享的会议号，快速加入进行中的会议。',
    })[subMode.value],
)

const statusMeta = {
  active: { label: '进行中', tone: 'live' },
  idle: { label: '待开始', tone: 'pending' },
  reserved: { label: '已预约', tone: 'pending' },
  ended: { label: '已结束', tone: 'ended' },
}

function statusOf(meeting) {
  return statusMeta[meeting.status] || { label: meeting.status || '会议', tone: 'pending' }
}

function meetingId(meeting) {
  return meeting.sessionId || meeting.id
}

function formatRoomCode(roomCode) {
  return roomCode ? String(roomCode).replace(/(.{3})/g, '$1 ').trim() : '—'
}

function formatTime(value) {
  if (!value) return '时间待定'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function meetingTime(meeting) {
  return formatTime(meeting.scheduledStartTime || meeting.startTime || meeting.createTime)
}

function isCreator(meeting) {
  const user = getUserInfo() || {}
  const userId = user.userId ?? user.id
  if (meeting?.creatorId == null || userId == null) return true
  return String(meeting.creatorId) === String(userId)
}

function actionsFor(meeting) {
  if (meeting.status === 'active') {
    const actions = [{ key: 'enter', label: '进入', cls: 'meet-btn--primary' }]
    if (isCreator(meeting)) actions.push({ key: 'end', label: '结束', cls: 'meet-btn--danger' })
    return actions
  }
  if (meeting.status === 'ended') {
    return [{ key: 'records', label: '查看记录' }]
  }
  return [{ key: 'start', label: '开始', cls: 'meet-btn--primary' }]
}

function rowsOf(response) {
  return response?.data?.records || response?.data?.content || response?.data || []
}

async function loadMeetings() {
  loading.value = true
  error.value = ''
  try {
    const result = await getMeetings({ pageNum: 1, pageSize: 50 })
    meetings.value = rowsOf(result)
  } catch (cause) {
    meetings.value = []
    error.value = cause.message || '会议列表暂时无法加载'
  } finally {
    loading.value = false
  }
}

function currentDisplayName() {
  const user = getUserInfo() || {}
  return user.realName || user.username || '我'
}

function resetForm() {
  form.value = { title: '', roomCode: '', displayName: '', scheduledStartTime: '' }
  duration.value = 60
  micOn.value = true
  participants.value = []
  participantQuery.value = ''
}

function selectMode(key) {
  mode.value = key
  if (key === 'reserve') reserveTab.value = 'reserve'
  error.value = ''
  notice.value = ''
}

function selectReserveTab(tab) {
  reserveTab.value = tab
  error.value = ''
  notice.value = ''
}

function addParticipant() {
  const name = participantQuery.value.trim()
  if (!name) return
  if (!participants.value.includes(name)) participants.value.push(name)
  participantQuery.value = ''
}

function removeParticipant(name) {
  participants.value = participants.value.filter((item) => item !== name)
}

function cancelReserve() {
  resetForm()
  error.value = ''
  notice.value = ''
}

async function submit() {
  submitting.value = true
  error.value = ''
  notice.value = ''
  try {
    if (subMode.value === 'join') {
      if (!form.value.roomCode.trim()) throw new Error('请输入会议号')
      const result = await joinMeeting({
        roomCode: form.value.roomCode.trim(),
        displayName: form.value.displayName.trim() || currentDisplayName(),
      })
      resetForm()
      if (enterRoom(result, true)) return
      notice.value = '已加入会议'
      await loadMeetings()
      return
    } else if (subMode.value === 'reserve') {
      if (!form.value.title.trim()) throw new Error('请输入会议主题')
      if (!form.value.scheduledStartTime) throw new Error('请选择开始时间')
      await reserveMeeting({
        title: form.value.title.trim(),
        scheduledStartTime: form.value.scheduledStartTime,
        participants: participants.value,
      })
      notice.value = '会议预约成功'
    } else {
      await createQuickMeeting({
        title: form.value.title.trim() || '快速会议',
        participants: [currentDisplayName()],
      })
      notice.value = '会议已发起'
    }
    resetForm()
    await loadMeetings()
  } catch (cause) {
    error.value = cause.message || '操作失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function enterRoom(result, useMicPreference) {
  const session = result?.data?.session
  if (session?.sessionId) {
    const mic = useMicPreference && !micOn.value ? 0 : 1
    router.push(`/meetings/room/${session.sessionId}?mic=${mic}`)
    return true
  }
  return false
}

async function handleAction(meeting, key) {
  if (key === 'records') {
    await openDetail(meeting)
    showRecords.value = true
    return
  }
  if (key === 'enter') {
    busyId.value = meetingId(meeting)
    error.value = ''
    try {
      const result = await joinMeeting({ roomCode: meeting.roomCode, displayName: currentDisplayName() })
      enterRoom(result, true)
    } catch (cause) {
      error.value = cause.message || '加入会议失败'
    } finally {
      busyId.value = ''
    }
    return
  }
  const id = meetingId(meeting)
  if (!id) return
  busyId.value = id
  error.value = ''
  notice.value = ''
  try {
    if (key === 'start') await startMeeting(id)
    else if (key === 'end') await endMeeting(id)
    else if (key === 'organize') await organizeMeeting(id)
    else if (key === 'delete') await deleteMeeting(id)
    await loadMeetings()
  } catch (cause) {
    error.value = cause.message || '操作失败，请稍后重试'
  } finally {
    busyId.value = ''
  }
}

/* ---------- 会议详情弹窗 ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const detailBusy = ref('')
const showRecords = ref(false)
const showMinutes = ref(false)
const copyTip = ref('')

const agentLabels = {
  meeting_transcription_agent: '转写整理',
  meeting_summary_agent: '会议纪要',
  meeting_controller_agent: '流程调度',
  meeting_member_analysis_agent: '成员分析',
  meeting_resource_recommendation_agent: '资源推荐',
}

const currentSession = computed(() => detail.value?.session || null)
const hostName = computed(() => currentDisplayName())

const participantList = computed(() => {
  const list = [...(detail.value?.participants || [])]
  const host = hostName.value
  if (host && !list.includes(host)) list.unshift(host)
  return list
})

const meetingLink = computed(() => {
  const code = currentSession.value?.roomCode
  return code ? `${window.location.origin}/meetings?roomCode=${code}` : ''
})

function agentLabel(name) {
  return agentLabels[name] || name
}

function detailTime(session) {
  return formatTime(session?.scheduledStartTime || session?.startTime || session?.createTime)
}

async function openDetail(meeting) {
  detailVisible.value = true
  detailLoading.value = true
  showRecords.value = false
  showMinutes.value = false
  detail.value = { session: meeting, participants: [], records: [], results: [] }
  try {
    const result = await getMeetingDetail(meetingId(meeting))
    if (result?.data) {
      detail.value = result.data
      showMinutes.value = (result.data.results || []).length > 0
    }
  } catch (cause) {
    error.value = cause.message || '会议详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailVisible.value = false
  detail.value = null
  copyTip.value = ''
}

async function runMinutes() {
  if (!currentSession.value) return
  detailBusy.value = 'organize'
  error.value = ''
  try {
    const result = await organizeMeeting(meetingId(currentSession.value))
    if (result?.data) detail.value = result.data
    showMinutes.value = true
    await loadMeetings()
  } catch (cause) {
    error.value = cause.message || 'AI 纪要生成失败'
  } finally {
    detailBusy.value = ''
  }
}

async function endCurrentMeeting() {
  if (!currentSession.value) return
  detailBusy.value = 'end'
  error.value = ''
  try {
    const result = await endMeeting(meetingId(currentSession.value))
    if (result?.data) detail.value = result.data
    await loadMeetings()
  } catch (cause) {
    error.value = cause.message || '结束会议失败'
  } finally {
    detailBusy.value = ''
  }
}

async function copyText(text, label) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
  }
  copyTip.value = `${label}已复制`
  setTimeout(() => {
    copyTip.value = ''
  }, 2000)
}

onMounted(() => {
  loadMeetings()
  const roomCode = new URLSearchParams(window.location.search).get('roomCode')
  if (roomCode) {
    mode.value = 'join'
    form.value.roomCode = roomCode
  }
})
</script>

<template>
  <div class="feature-page">
    <AppTabBar />

    <main class="feature-container meeting-layout">
      <aside class="meeting-sidebar feature-card">
        <div class="meeting-sidebar__brand">
          <strong>会议中心</strong>
          <span>在线会议 · 智能整理</span>
        </div>

        <nav class="meeting-sidebar__menu" aria-label="会议菜单">
          <button
            v-for="item in menuItems"
            :key="item.key"
            type="button"
            class="meeting-menu-item"
            :class="{ 'meeting-menu-item--active': mode === item.key }"
            @click="selectMode(item.key)"
          >
            <span class="meeting-menu-item__icon" aria-hidden="true">
              <svg v-if="item.key === 'quick'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
              </svg>
              <svg v-else-if="item.key === 'reserve'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="4.5" width="18" height="17" rx="2.5" />
                <path d="M8 2.5v4M16 2.5v4M3 9.5h18" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
                <path d="M10 17l5-5-5-5M15 12H3" />
              </svg>
            </span>
            <span class="meeting-menu-item__copy">
              <strong>{{ item.label }}</strong>
              <small>{{ item.desc }}</small>
            </span>
          </button>
        </nav>

        <div class="meeting-sidebar__tip">
          <p>提示</p>
          <span>发起会议后，将会议号分享给同学即可邀请他们加入。</span>
        </div>
      </aside>

      <div class="meeting-content">
        <header class="meeting-heading">
          <h1>会议</h1>
          <p>发起、预约或加入校园会议，AI 帮你整理会后纪要。</p>
        </header>

        <p v-if="error" class="meeting-alert meeting-alert--error">{{ error }}</p>
        <p v-else-if="notice" class="meeting-alert meeting-alert--success">{{ notice }}</p>

        <section class="feature-card meeting-create" :class="{ 'meeting-create--join': subMode === 'join' }">
          <div class="meeting-create__head">
            <h2>{{ createHeading }}</h2>
            <p>{{ createDesc }}</p>
          </div>

          <div v-if="mode === 'reserve'" class="meeting-tabs">
            <button
              type="button"
              class="meeting-tab"
              :class="{ 'meeting-tab--active': reserveTab === 'reserve' }"
              @click="selectReserveTab('reserve')"
            >
              预约会议
            </button>
            <button
              type="button"
              class="meeting-tab"
              :class="{ 'meeting-tab--active': reserveTab === 'quick' }"
              @click="selectReserveTab('quick')"
            >
              快速会议
            </button>
          </div>

          <form class="meeting-create__form" @submit.prevent="submit">
            <!-- 加入会议 -->
            <template v-if="subMode === 'join'">
              <label>
                <span>会议号</span>
                <input v-model="form.roomCode" class="feature-input" placeholder="请输入 9 位会议号" />
              </label>
              <label>
                <span>姓名</span>
                <input v-model="form.displayName" class="feature-input" placeholder="请输入您的姓名" />
              </label>
              <div class="reserve-toggle-row">
                <div>
                  <p class="reserve-toggle-row__title">入会开启麦克风</p>
                  <p class="reserve-toggle-row__desc">进入会议时自动开启麦克风</p>
                </div>
                <button
                  type="button"
                  class="switch"
                  :class="{ 'switch--on': micOn }"
                  role="switch"
                  :aria-checked="micOn"
                  @click="micOn = !micOn"
                >
                  <span class="switch__thumb"></span>
                </button>
              </div>
              <button class="meet-btn meet-btn--primary meet-btn--block" type="submit" :disabled="submitting">
                {{ submitting ? '处理中…' : '加入会议' }}
              </button>
            </template>

            <!-- 快速会议 -->
            <template v-else-if="subMode === 'quick'">
              <label>
                <span>会议主题</span>
                <input v-model="form.title" class="feature-input" placeholder="请输入会议主题，例如：项目进度同步会" />
              </label>
              <button class="meet-btn meet-btn--primary meet-btn--block" type="submit" :disabled="submitting">
                {{ submitting ? '处理中…' : '发起会议' }}
              </button>
            </template>

            <!-- 预约会议 -->
            <template v-else>
              <label>
                <span>会议主题</span>
                <input v-model="form.title" class="feature-input" placeholder="请输入会议主题，例如：项目进度同步会" />
              </label>

              <div class="reserve-grid">
                <label>
                  <span>开始时间</span>
                  <input v-model="form.scheduledStartTime" class="feature-input" type="datetime-local" />
                </label>
                <label>
                  <span>会议时长</span>
                  <select v-model.number="duration" class="feature-select">
                    <option v-for="opt in durationOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                  </select>
                </label>
              </div>

              <div class="reserve-field">
                <span class="reserve-field__label">参会人员</span>
                <div class="participant-add">
                  <input
                    v-model="participantQuery"
                    class="feature-input"
                    placeholder="搜索或输入姓名，回车添加"
                    @keydown.enter.prevent="addParticipant"
                  />
                  <button type="button" class="meet-btn" @click="addParticipant">添加</button>
                </div>
                <div v-if="participants.length" class="participant-chips">
                  <span v-for="name in participants" :key="name" class="participant-chip">
                    {{ name }}
                    <button type="button" aria-label="移除参会人" @click="removeParticipant(name)">×</button>
                  </span>
                </div>
                <p v-else class="participant-hint">尚未添加参会人</p>
              </div>

              <div class="reserve-toggle-row">
                <div>
                  <p class="reserve-toggle-row__title">入会开启麦克风</p>
                  <p class="reserve-toggle-row__desc">进入会议时自动开启麦克风</p>
                </div>
                <button
                  type="button"
                  class="switch"
                  :class="{ 'switch--on': micOn }"
                  role="switch"
                  :aria-checked="micOn"
                  @click="micOn = !micOn"
                >
                  <span class="switch__thumb"></span>
                </button>
              </div>

              <div class="reserve-footer">
                <button type="button" class="meet-btn" @click="cancelReserve">取消</button>
                <button class="meet-btn meet-btn--primary" type="submit" :disabled="submitting">
                  {{ submitting ? '处理中…' : '预约会议' }}
                </button>
              </div>
            </template>
          </form>
        </section>

        <section v-if="mode === 'quick'" class="feature-card meeting-panel">
          <div class="feature-section__head">
            <h2>我的会议</h2>
            <span>共 {{ meetings.length }} 场</span>
          </div>

          <p v-if="loading" class="feature-empty meeting-empty">正在加载会议…</p>
          <p v-else-if="meetings.length === 0" class="feature-empty meeting-empty">
            暂无会议，先从左侧发起一场会议吧。
          </p>

          <div v-else class="meeting-table-wrap">
            <table class="meeting-table">
              <thead>
                <tr>
                  <th>会议名称</th>
                  <th>开始时间</th>
                  <th>会议号</th>
                  <th>会议状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="meeting in meetings"
                  :key="meetingId(meeting)"
                  @click="openDetail(meeting)"
                >
                  <td class="meeting-table__title">{{ meeting.title || '未命名会议' }}</td>
                  <td>{{ meetingTime(meeting) }}</td>
                  <td>{{ formatRoomCode(meeting.roomCode) }}</td>
                  <td>
                    <span class="meeting-status" :class="`meeting-status--${statusOf(meeting).tone}`">
                      <i v-if="statusOf(meeting).tone === 'live'" class="meeting-status__dot" aria-hidden="true"></i>
                      {{ statusOf(meeting).label }}
                    </span>
                  </td>
                  <td class="meeting-table__actions" @click.stop>
                    <button
                      v-for="action in actionsFor(meeting)"
                      :key="action.key"
                      type="button"
                      class="meet-btn"
                      :class="action.cls"
                      :disabled="busyId === meetingId(meeting)"
                      @click="handleAction(meeting, action.key)"
                    >
                      {{ action.label }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </main>

    <!-- 会议详情弹窗 -->
    <div v-if="detailVisible" class="detail-mask" @click.self="closeDetail">
      <div class="detail-modal" role="dialog" aria-modal="true" aria-label="会议详情">
        <button class="detail-modal__close" type="button" aria-label="关闭" @click="closeDetail">×</button>

        <header class="detail-modal__head">
          <h2>{{ currentSession?.title || '未命名会议' }}</h2>
          <span
            v-if="currentSession"
            class="meeting-status"
            :class="`meeting-status--${statusOf(currentSession).tone}`"
          >
            <i v-if="statusOf(currentSession).tone === 'live'" class="meeting-status__dot" aria-hidden="true"></i>
            {{ statusOf(currentSession).label }}
          </span>
        </header>

        <p v-if="detailLoading" class="detail-loading">正在加载会议详情…</p>

        <template v-else-if="currentSession">
          <dl class="detail-info">
            <div>
              <dt>会议时间</dt>
              <dd>{{ detailTime(currentSession) }}</dd>
            </div>
            <div>
              <dt>主持人</dt>
              <dd>{{ hostName }}</dd>
            </div>
            <div>
              <dt>会议号</dt>
              <dd class="detail-copy">
                <code>{{ formatRoomCode(currentSession.roomCode) }}</code>
                <button type="button" @click="copyText(currentSession.roomCode, '会议号')">复制</button>
              </dd>
            </div>
            <div>
              <dt>会议链接</dt>
              <dd class="detail-copy">
                <code>{{ meetingLink }}</code>
                <button type="button" @click="copyText(meetingLink, '会议链接')">复制</button>
              </dd>
            </div>
          </dl>

          <section class="detail-section">
            <h3>参会人员（{{ participantList.length }}）</h3>
            <div v-if="participantList.length" class="detail-participants">
              <span v-for="name in participantList" :key="name" class="detail-participant">
                {{ name }}
                <em v-if="name === hostName">主持人</em>
              </span>
            </div>
            <p v-else class="detail-empty">暂无参会人</p>
          </section>

          <section v-if="showMinutes" class="detail-section">
            <h3>AI 会议纪要</h3>
            <p v-if="detailBusy === 'organize'" class="detail-empty">AI 正在整理会议内容…</p>
            <template v-else-if="detail?.results?.length">
              <div v-for="item in detail.results" :key="item.id" class="detail-result">
                <h4>{{ agentLabel(item.agentName) }}</h4>
                <p>{{ item.answer || '暂无内容' }}</p>
              </div>
            </template>
            <p v-else class="detail-empty">暂无纪要，点击下方「AI 会议纪要」生成</p>
          </section>

          <section v-if="showRecords" class="detail-section">
            <h3>会议记录（{{ detail?.records?.length || 0 }}）</h3>
            <template v-if="detail?.records?.length">
              <div v-for="record in detail.records" :key="record.id" class="detail-record">
                <p>{{ record.content }}</p>
                <span>{{ record.source || '记录' }} · {{ formatTime(record.createTime) }}</span>
              </div>
            </template>
            <p v-else class="detail-empty">暂无会议记录</p>
          </section>

          <p v-if="copyTip" class="detail-copy-tip">{{ copyTip }}</p>

          <footer class="detail-footer">
            <button
              class="meet-btn meet-btn--primary"
              type="button"
              :disabled="detailBusy === 'organize'"
              @click="runMinutes"
            >
              {{ detailBusy === 'organize' ? 'AI 生成中…' : 'AI 会议纪要' }}
            </button>
            <button class="meet-btn" type="button" @click="showRecords = !showRecords">
              {{ showRecords ? '收起会议记录' : '会议记录' }}
            </button>
            <button
              v-if="currentSession?.status === 'active' && isCreator(currentSession)"
              class="meet-btn meet-btn--danger"
              type="button"
              :disabled="detailBusy === 'end'"
              @click="endCurrentMeeting"
            >
              {{ detailBusy === 'end' ? '结束中…' : '结束会议' }}
            </button>
          </footer>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.meeting-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

/* ---------- Sidebar ---------- */
.meeting-sidebar {
  position: sticky;
  top: 84px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px 16px;
}

.meeting-sidebar__brand {
  padding: 2px 4px;
}

.meeting-sidebar__brand strong {
  display: block;
  color: #17233a;
  font-size: 18px;
}

.meeting-sidebar__brand span {
  display: block;
  margin-top: 3px;
  color: #8494a7;
  font-size: 12px;
  letter-spacing: 0.4px;
}

.meeting-sidebar__menu {
  display: grid;
  gap: 8px;
}

.meeting-menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.meeting-menu-item:hover {
  background: #f4f7fb;
}

.meeting-menu-item--active {
  border-color: rgba(37, 99, 235, 0.18);
  background: #eef4ff;
}

.meeting-menu-item__icon {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  color: #52627a;
  background: #f1f4f8;
}

.meeting-menu-item__icon svg {
  width: 18px;
  height: 18px;
}

.meeting-menu-item--active .meeting-menu-item__icon {
  color: #ffffff;
  background: #2563eb;
}

.meeting-menu-item__copy {
  display: grid;
  min-width: 0;
}

.meeting-menu-item__copy strong {
  color: #23344a;
  font-size: 14px;
}

.meeting-menu-item--active .meeting-menu-item__copy strong {
  color: #1d4ed8;
}

.meeting-menu-item__copy small {
  margin-top: 2px;
  color: #8494a7;
  font-size: 12px;
}

.meeting-sidebar__tip {
  margin-top: auto;
  padding: 14px;
  border-radius: 10px;
  background: #f4f7fb;
}

.meeting-sidebar__tip p {
  margin: 0 0 6px;
  color: #23344a;
  font-size: 13px;
  font-weight: 700;
}

.meeting-sidebar__tip span {
  color: #718096;
  font-size: 12px;
  line-height: 1.6;
}

/* ---------- Content ---------- */
.meeting-heading h1 {
  margin: 0;
  color: #17233a;
  font-size: 28px;
}

.meeting-heading p {
  margin: 7px 0 0;
  color: #718096;
  font-size: 14px;
}

.meeting-alert {
  margin: 16px 0 0;
  padding: 11px 14px;
  border-radius: 9px;
  font-size: 14px;
}

.meeting-alert--error {
  border: 1px solid #ebc4bf;
  color: #a54239;
  background: #fff8f7;
}

.meeting-alert--success {
  border: 1px solid #bfe3cd;
  color: #2f6b4f;
  background: #f0faf4;
}

.meeting-create {
  margin-top: 18px;
  padding: 22px;
}

.meeting-create--join {
  max-width: 430px;
  margin-right: auto;
  margin-left: auto;
}

.meeting-create__head h2 {
  margin: 0;
  color: #23344a;
  font-size: 19px;
}

.meeting-create__head p {
  margin: 7px 0 0;
  color: #718096;
  font-size: 13px;
}

.meeting-tabs {
  display: inline-flex;
  gap: 4px;
  margin-top: 18px;
  padding: 4px;
  border: 1px solid #e0e6ec;
  border-radius: 9px;
  background: #f4f7fa;
}

.meeting-tab {
  min-height: 34px;
  padding: 0 18px;
  border-radius: 7px;
  color: #65758a;
  background: transparent;
  font-size: 13px;
  font-weight: 700;
  transition: background 0.15s ease, color 0.15s ease;
}

.meeting-tab:hover {
  color: #34506c;
}

.meeting-tab--active {
  color: #1d4ed8;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(30, 43, 76, 0.08);
}

.meeting-create__form {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.meeting-create__form label {
  display: grid;
  gap: 7px;
  color: #41536a;
  font-size: 13px;
  font-weight: 700;
}

/* ---------- Reserve form ---------- */
.reserve-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.reserve-field {
  display: grid;
  gap: 8px;
}

.reserve-field__label {
  color: #41536a;
  font-size: 13px;
  font-weight: 700;
}

.participant-add {
  display: flex;
  gap: 8px;
}

.participant-add .feature-input {
  flex: 1;
}

.participant-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.participant-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 7px 5px 12px;
  border-radius: 999px;
  color: #2956c2;
  background: #edf3ff;
  font-size: 13px;
  font-weight: 600;
}

.participant-chip button {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  color: #2956c2;
  background: rgba(37, 99, 235, 0.14);
  font-size: 13px;
  line-height: 1;
}

.participant-chip button:hover {
  background: rgba(37, 99, 235, 0.24);
}

.participant-hint {
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
}

.reserve-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 14px;
  border: 1px solid #e5eaf0;
  border-radius: 10px;
  background: #fafbfd;
}

.reserve-toggle-row__title {
  margin: 0;
  color: #26384d;
  font-size: 14px;
  font-weight: 700;
}

.reserve-toggle-row__desc {
  margin: 3px 0 0;
  color: #8494a7;
  font-size: 12px;
}

.switch {
  position: relative;
  flex: 0 0 auto;
  width: 44px;
  height: 24px;
  border-radius: 999px;
  background: #cbd5e1;
  transition: background 0.18s ease;
}

.switch__thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.2);
  transition: left 0.18s ease;
}

.switch--on {
  background: #2563eb;
}

.switch--on .switch__thumb {
  left: 23px;
}

.reserve-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

.reserve-footer .meet-btn {
  min-width: 96px;
}

/* ---------- Meetings list ---------- */
.meeting-panel {
  margin-top: 18px;
  padding: 22px;
}

.meeting-empty {
  margin: 0;
}

.meeting-table-wrap {
  overflow-x: auto;
}

.meeting-table {
  width: 100%;
  border-collapse: collapse;
}

.meeting-table th {
  padding: 10px 12px;
  border-bottom: 1px solid #e5eaf0;
  color: #64748b;
  background: #f6f8fb;
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.meeting-table td {
  padding: 13px 12px;
  border-bottom: 1px solid #eef2f7;
  color: #34506c;
  font-size: 14px;
  white-space: nowrap;
}

.meeting-table tbody tr {
  cursor: pointer;
  transition: background 0.15s ease;
}

.meeting-table tbody tr:hover {
  background: #f6f9ff;
}

.meeting-table tbody tr:last-child td {
  border-bottom: 0;
}

.meeting-table__title {
  max-width: 220px;
  overflow: hidden;
  color: #26384d;
  font-weight: 700;
  text-overflow: ellipsis;
}

.meeting-table__actions .meet-btn {
  min-height: 32px;
  padding: 0 14px;
  font-size: 12px;
}

/* ---------- Detail modal ---------- */
.detail-mask {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.45);
}

.detail-modal {
  position: relative;
  width: min(560px, 100%);
  max-height: calc(100vh - 48px);
  padding: 26px;
  border-radius: 14px;
  overflow: auto;
  background: #ffffff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.25);
}

.detail-modal__close {
  position: absolute;
  top: 14px;
  right: 14px;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #64748b;
  background: #f1f4f7;
  font-size: 20px;
  line-height: 1;
}

.detail-modal__close:hover {
  color: #334155;
  background: #e5eaf0;
}

.detail-modal__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-right: 36px;
}

.detail-modal__head h2 {
  margin: 0;
  color: #17233a;
  font-size: 20px;
  word-break: break-all;
}

.detail-loading {
  margin: 18px 0 0;
  padding: 14px;
  border: 1px dashed #d7e0e8;
  border-radius: 8px;
  color: #8494a7;
  font-size: 13px;
  text-align: center;
}

.detail-info {
  margin: 18px 0 0;
  border-block: 1px solid #eef2f7;
}

.detail-info > div {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 11px 0;
}

.detail-info dt {
  flex: 0 0 64px;
  color: #8494a7;
  font-size: 13px;
}

.detail-info dd {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: #26384d;
  font-size: 14px;
}

.detail-copy {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-copy code {
  padding: 3px 8px;
  border-radius: 6px;
  color: #2956c2;
  background: #f4f7fb;
  font-size: 13px;
  word-break: break-all;
}

.detail-copy button {
  min-height: 26px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #2563eb;
  background: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

.detail-copy button:hover {
  border-color: #2563eb;
  background: #eef4ff;
}

.detail-section {
  margin-top: 16px;
}

.detail-section h3 {
  margin: 0 0 10px;
  color: #23344a;
  font-size: 14px;
}

.detail-participants {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-participant {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border-radius: 999px;
  color: #34506c;
  background: #f1f4f8;
  font-size: 13px;
}

.detail-participant em {
  padding: 1px 6px;
  border-radius: 999px;
  color: #ffffff;
  background: #2563eb;
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
}

.detail-empty {
  margin: 0;
  padding: 12px;
  border: 1px dashed #d7e0e8;
  border-radius: 8px;
  color: #8494a7;
  font-size: 13px;
  text-align: center;
}

.detail-result {
  padding: 12px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: #fafbfd;
}

.detail-result + .detail-result,
.detail-record + .detail-record {
  margin-top: 8px;
}

.detail-result h4 {
  margin: 0 0 6px;
  color: #1d4ed8;
  font-size: 13px;
}

.detail-result p {
  margin: 0;
  color: #34506c;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-record {
  padding: 10px 12px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: #fafbfd;
}

.detail-record p {
  margin: 0;
  color: #34506c;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-record span {
  display: block;
  margin-top: 6px;
  color: #94a3b8;
  font-size: 12px;
}

.detail-copy-tip {
  margin: 12px 0 0;
  color: #2f6b4f;
  font-size: 13px;
  text-align: center;
}

.detail-footer {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.detail-footer .meet-btn {
  flex: 1;
  min-height: 42px;
}

/* ---------- Status tag ---------- */
.meeting-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.meeting-status--live {
  color: #2f6b4f;
  background: #edf8f3;
}

.meeting-status--pending {
  color: #2956c2;
  background: #edf3ff;
}

.meeting-status--ended {
  color: #718096;
  background: #eff2f5;
}

.meeting-status__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22a06b;
  animation: meeting-pulse 1.4s ease-in-out infinite;
}

@keyframes meeting-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(34, 160, 107, 0.45);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(34, 160, 107, 0);
  }
}

/* ---------- Buttons ---------- */
.meet-btn {
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #34506c;
  background: #ffffff;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  transition: border-color 0.15s ease, background 0.15s ease, color 0.15s ease;
}

.meet-btn:hover {
  border-color: #94a3b8;
}

.meet-btn--primary {
  border-color: #2563eb;
  color: #ffffff;
  background: #2563eb;
}

.meet-btn--primary:hover {
  background: #1d4ed8;
}

.meet-btn--danger {
  border-color: #e7b6b1;
  color: #a54239;
  background: #fff8f7;
}

.meet-btn--block {
  width: 100%;
  min-height: 44px;
  font-size: 15px;
}

.meet-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

/* ---------- Responsive ---------- */
@media (max-width: 900px) {
  .meeting-layout {
    grid-template-columns: 1fr;
  }

  .meeting-sidebar {
    position: static;
  }

  .reserve-grid {
    grid-template-columns: 1fr;
  }
}
</style>
