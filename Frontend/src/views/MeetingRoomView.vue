<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useMeetingAsr } from '../composables/useMeetingAsr'
import { endMeeting, getMeetingDetail, getMeetingComments, leaveMeeting, sendMeetingComment, transferHost } from '../api/meetings'
import { getUserInfo } from '../utils/auth'

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const loading = ref(true)
const error = ref('')
const myName = ref('我')
const myMicOn = ref(true)
const sidePanelTab = ref('comments')
const showMembers = ref(false)
const showInvite = ref(false)
const showSettings = ref(false)
const leaveActionVisible = ref(false)
const transferHostVisible = ref(false)
const selectedTransferMember = ref('')
const transferring = ref(false)
const copyTip = ref('')
const now = ref(Date.now())
const ending = ref(false)
const comments = ref([])
const commentDraft = ref('')
const danmakuDraft = ref('')
const commentListRef = ref(null)
const intelligenceListRef = ref(null)

let enteredAt = Date.now()
let timer = null
let pollTimer = null

const session = computed(() => detail.value?.session || null)
const sessionId = computed(() => route.params.sessionId)
const meetingTitle = computed(() => session.value?.title || '未命名会议')
const asrEnabled = computed(() => session.value?.status === 'active')

const isCreator = computed(() => {
  const user = getUserInfo() || {}
  const userId = user.userId ?? user.id
  const creatorId = session.value?.creatorId
  if (creatorId == null) return true
  return userId != null && String(creatorId) === String(userId)
})

const {
  asrItems,
  aiSummaryItems,
  subtitleRecords,
  agentEnabled,
  panelMode,
  asrStatusText,
  aiSummaryStatusText,
  asrReconnectVisible,
  livePanelTitle,
  livePanelStatus,
  latestSubtitleLine,
  initAsr,
  closeAsr,
  reconnectAsr,
  setAgentSummary,
  sendDanmaku,
  syncRecordsFromDetail,
} = useMeetingAsr({
  sessionId,
  meetingTitle,
  isHost: isCreator,
  micEnabled: myMicOn,
  enabled: asrEnabled,
})

const canEndMeeting = computed(() => isCreator.value && session.value?.status === 'active')

const transferableMembers = computed(() => tiles.value.filter((tile) => !tile.isSelf))

const elapsedLabel = computed(() => {
  const startSource = session.value?.startTime
  const startAt = startSource ? new Date(startSource).getTime() : enteredAt
  if (!startAt || Number.isNaN(startAt)) return '00:00'
  const isEnded = session.value?.status === 'ended' && session.value?.endTime
  const endAt = isEnded ? new Date(session.value.endTime).getTime() : now.value
  return formatElapsed((endAt - startAt) / 1000)
})

const tiles = computed(() => {
  const names = [...(detail.value?.participants || [])]
  if (myName.value && !names.includes(myName.value)) names.unshift(myName.value)
  return names.map((name) => ({
    name,
    isSelf: name === myName.value,
    micOn: name === myName.value ? myMicOn.value : true,
  }))
})

const meetingLink = computed(() => {
  const code = session.value?.roomCode
  return code ? `${window.location.origin}/meetings?roomCode=${code}` : ''
})

function formatElapsed(totalSeconds) {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const pad = (n) => String(n).padStart(2, '0')
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

function initialOf(name) {
  return (name || '?').slice(0, 1).toUpperCase()
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

function leaveRoom() {
  router.replace('/meetings')
}

async function exitRoom() {
  if (session.value?.status === 'active' && !isCreator.value) {
    try {
      await leaveMeeting(session.value.sessionId)
    } catch {
      /* 忽略离开接口异常，仍返回列表 */
    }
  }
  leaveRoom()
}

/* ---------- 评论区 ---------- */
const currentUserId = computed(() => {
  const user = getUserInfo() || {}
  return user.userId ?? user.id
})

function parseDate(value) {
  if (!value) return new Date()
  if (value instanceof Date) return value
  return new Date(value)
}

async function loadComments() {
  try {
    const result = await getMeetingComments(route.params.sessionId)
    const list = result?.data || []
    comments.value = (list || []).map((item) => ({
      id: item.id,
      name: item.senderName || '匿名',
      text: item.content,
      time: formatClock(parseDate(item.createTime)),
      isSelf:
        (item.senderId != null && String(item.senderId) === String(currentUserId.value)) ||
        item.senderName === myName.value,
    }))
  } catch {
    comments.value = []
  }
}

function formatClock(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function sendComment() {
  const text = commentDraft.value.trim()
  if (!text) return
  try {
    await sendMeetingComment(route.params.sessionId, { content: text })
    commentDraft.value = ''
    await loadComments()
    nextTick(() => {
      if (commentListRef.value) commentListRef.value.scrollTop = commentListRef.value.scrollHeight
    })
  } catch (cause) {
    error.value = cause.message || '评论发送失败'
  }
}

function submitDanmaku() {
  const text = danmakuDraft.value.trim()
  if (!text) return
  sendDanmaku(text)
  danmakuDraft.value = ''
  nextTick(() => {
    if (intelligenceListRef.value) intelligenceListRef.value.scrollTop = intelligenceListRef.value.scrollHeight
  })
}

function toggleAgentMode(enabled) {
  setAgentSummary(enabled)
}

function handleHostLeave() {
  if (!isCreator.value) {
    exitRoom()
    return
  }
  leaveActionVisible.value = true
}

function closeLeaveAction() {
  leaveActionVisible.value = false
}

function openTransferHost() {
  leaveActionVisible.value = false
  transferHostVisible.value = true
  selectedTransferMember.value = ''
}

function closeTransferHost() {
  transferHostVisible.value = false
}

function selectTransferMember(name) {
  selectedTransferMember.value = name
}

async function confirmTransferHost() {
  if (!selectedTransferMember.value) {
    error.value = '请选择新主持人'
    return
  }
  if (!session.value) return
  transferring.value = true
  error.value = ''
  try {
    const result = await transferHost(session.value.sessionId, { newHostName: selectedTransferMember.value })
    if (result?.data) detail.value = result.data
    transferHostVisible.value = false
    try {
      await leaveMeeting(session.value.sessionId)
    } catch {
      /* 忽略离开接口异常 */
    }
    leaveRoom()
  } catch (cause) {
    error.value = cause.message || '转交主持人失败'
  } finally {
    transferring.value = false
  }
}

async function endRoom() {
  if (!session.value) return
  ending.value = true
  error.value = ''
  try {
    const result = await endMeeting(session.value.sessionId)
    if (result?.data) detail.value = result.data
  } catch (cause) {
    error.value = cause.message || '结束会议失败'
  } finally {
    ending.value = false
  }
}

async function loadRoom() {
  loading.value = true
  error.value = ''
  try {
    const result = await getMeetingDetail(route.params.sessionId)
    detail.value = result?.data || null
    syncRecordsFromDetail(result?.data?.records || [])
  } catch (cause) {
    error.value = cause.message || '会议信息加载失败'
  } finally {
    loading.value = false
  }
}

async function refreshDetail() {
  if (loading.value || ending.value) return
  try {
    const result = await getMeetingDetail(route.params.sessionId)
    if (result?.data) {
      detail.value = result.data
      if (result.data.session?.status === 'ended' && !isCreator.value) {
        router.replace('/meetings')
      }
    }
  } catch {
    /* 忽略轮询异常 */
  }
  try {
    await loadComments()
  } catch {
    /* 忽略轮询异常 */
  }
}

onMounted(() => {
  const user = getUserInfo() || {}
  myName.value = route.query.name || user.realName || user.username || '我'
  myMicOn.value = route.query.mic !== '0'
  enteredAt = Date.now()
  loadComments()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
  pollTimer = setInterval(refreshDetail, 8000)
  loadRoom().then(() => {
    if (asrEnabled.value) initAsr()
  })
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (pollTimer) clearInterval(pollTimer)
  closeAsr()
})

watch(asrEnabled, (value) => {
  if (value) initAsr()
  else closeAsr()
})

watch([asrItems, aiSummaryItems, subtitleRecords], () => {
  nextTick(() => {
    if (intelligenceListRef.value && sidePanelTab.value === 'intelligence') {
      intelligenceListRef.value.scrollTop = intelligenceListRef.value.scrollHeight
    }
  })
}, { deep: true })
</script>

<template>
  <div class="room-shell">
    <!-- 加载中 / 错误 -->
    <div v-if="loading" class="room-state">正在进入会议…</div>
    <div v-else-if="error || !session" class="room-state">
      <p>{{ error || '未找到会议' }}</p>
      <button class="room-btn room-btn--primary" type="button" @click="router.replace('/meetings')">返回会议列表</button>
    </div>

    <template v-else>
      <!-- 顶部栏 -->
      <header class="room-topbar">
        <div class="room-topbar__info">
          <h1>{{ session.title || '未命名会议' }}</h1>
          <span class="room-topbar__duration">
            <i class="room-topbar__duration-dot" :class="{ 'room-topbar__duration-dot--ended': session.status === 'ended' }" aria-hidden="true"></i>
            {{ session.status === 'ended' ? '已结束' : `已进行 ${elapsedLabel}` }}
          </span>
        </div>
        <div class="room-topbar__actions">
          <button class="room-icon-btn" type="button" aria-label="设置" title="设置" @click="showSettings = true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="3.2" />
              <path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.2 5.2l1.7 1.7M17.1 17.1l1.7 1.7M5.2 18.8l1.7-1.7M17.1 6.9l1.7-1.7" />
            </svg>
          </button>
          <button
            v-if="canEndMeeting"
            class="room-btn room-btn--leave"
            type="button"
            :disabled="ending"
            @click="endRoom"
          >
            {{ ending ? '结束中…' : '结束会议' }}
          </button>
          <button
            v-if="canEndMeeting"
            class="room-btn room-btn--ghost"
            type="button"
            @click="handleHostLeave"
          >
            离开
          </button>
          <button v-else-if="session.status === 'active'" class="room-btn room-btn--leave" type="button" @click="exitRoom">离开会议</button>
        </div>
      </header>

      <!-- 会议已结束提示 -->
      <div v-if="session.status === 'ended'" class="room-ended-tip">会议已结束，会议记录与 AI 纪要可在会议列表中查看。</div>
      <div v-else-if="error" class="room-ended-tip room-ended-tip--error">{{ error }}</div>

      <div class="room-body">
        <!-- 参会人员网格 -->
        <main class="room-grid">
          <div v-for="tile in tiles" :key="tile.name" class="room-tile">
            <span class="room-tile__avatar">{{ initialOf(tile.name) }}</span>
            <span class="room-tile__name">
              {{ tile.name }}
              <em v-if="tile.isSelf">（我）</em>
            </span>
            <span class="room-tile__mic" :class="tile.micOn ? 'room-tile__mic--on' : 'room-tile__mic--off'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3Z" />
                <path d="M18.5 11a6.5 6.5 0 0 1-13 0M12 17.5V21" />
                <path v-if="!tile.micOn" d="M4.5 4.5l15 15" />
              </svg>
              {{ tile.micOn ? '麦克风开启' : '麦克风关闭' }}
            </span>
          </div>
        </main>

        <!-- 右侧：评论 + 智能助手 -->
        <aside class="room-sidepanel">
          <div class="room-sidepanel__tabs">
            <button type="button" :class="{ active: sidePanelTab === 'comments' }" @click="sidePanelTab = 'comments'">评论</button>
            <button type="button" :class="{ active: sidePanelTab === 'intelligence' }" @click="sidePanelTab = 'intelligence'">智能助手</button>
          </div>

          <div v-show="sidePanelTab === 'comments'" class="room-comments">
            <div class="room-comments__head">
              <h2>评论区</h2>
              <span>{{ comments.length }} 条</span>
            </div>
            <div ref="commentListRef" class="room-comments__list">
              <p v-if="!comments.length" class="room-comments__empty">还没有评论，来抢个沙发吧</p>
              <div
                v-for="(comment, index) in comments"
                :key="index"
                class="room-comment"
                :class="{ 'room-comment--self': comment.isSelf }"
              >
                <span class="room-comment__avatar">{{ initialOf(comment.name) }}</span>
                <div class="room-comment__body">
                  <div class="room-comment__meta">
                    <strong>{{ comment.name }}<em v-if="comment.isSelf">（我）</em></strong>
                    <time>{{ comment.time }}</time>
                  </div>
                  <p class="room-comment__text">{{ comment.text }}</p>
                </div>
              </div>
            </div>
            <form class="room-comments__input" @submit.prevent="sendComment">
              <input v-model="commentDraft" type="text" maxlength="300" placeholder="发表你的评论…" />
              <button class="room-btn room-btn--primary" type="submit" :disabled="!commentDraft.trim()">发送</button>
            </form>
          </div>

          <div v-show="sidePanelTab === 'intelligence'" class="room-intelligence">
            <div class="room-intelligence__head">
              <div>
                <h2>{{ livePanelTitle }}</h2>
                <span>{{ livePanelStatus }}</span>
              </div>
              <button v-if="asrReconnectVisible" type="button" class="room-intelligence__reconnect" @click="reconnectAsr">重新连接</button>
            </div>

            <div v-if="isCreator" class="room-intelligence__modes">
              <button type="button" :class="{ active: !agentEnabled }" @click="toggleAgentMode(false)">语音弹幕</button>
              <button type="button" :class="{ active: agentEnabled }" @click="toggleAgentMode(true)">AI 总结</button>
            </div>

            <div ref="intelligenceListRef" class="room-intelligence__list">
              <template v-if="agentEnabled">
                <p v-if="!aiSummaryItems.length" class="room-comments__empty">等待会议发言，AI 将提炼重点…</p>
                <article v-for="item in aiSummaryItems" :key="item.id" class="room-intelligence__card room-intelligence__card--summary">
                  <time>{{ item.time }}</time>
                  <p>{{ item.text }}</p>
                </article>
              </template>
              <template v-else>
                <p v-if="!asrItems.length && !subtitleRecords.length" class="room-comments__empty">开启麦克风后，发言会自动转成字幕</p>
                <article
                  v-for="item in asrItems"
                  :key="item.id"
                  class="room-intelligence__bubble"
                  :class="{ 'room-intelligence__bubble--self': item.isSelf, 'room-intelligence__bubble--partial': !item.isFinal }"
                >
                  <strong>{{ item.speaker }}</strong>
                  <p>{{ item.text }}</p>
                </article>
                <article
                  v-for="(item, index) in subtitleRecords"
                  :key="`record-${index}-${item.timestamp}`"
                  class="room-intelligence__record"
                >
                  <span>{{ item.time }} · {{ item.speaker }}{{ item.isDanmaku ? '（弹幕）' : '' }}</span>
                  <p>{{ item.text }}</p>
                </article>
              </template>
            </div>

            <form class="room-comments__input" @submit.prevent="submitDanmaku">
              <input v-model="danmakuDraft" type="text" maxlength="120" placeholder="发送会议弹幕…" />
              <button class="room-btn room-btn--primary" type="submit" :disabled="!danmakuDraft.trim()">发送</button>
            </form>
          </div>
        </aside>
      </div>

      <!-- 字幕条 -->
      <div v-if="latestSubtitleLine && session.status === 'active'" class="room-subtitles">{{ latestSubtitleLine }}</div>

      <!-- 底部控制栏 -->
      <footer class="room-controls">
        <button class="room-control" :class="{ 'room-control--off': !myMicOn }" type="button" @click="myMicOn = !myMicOn">
          <span class="room-control__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3Z" />
              <path d="M18.5 11a6.5 6.5 0 0 1-13 0M12 17.5V21M8.5 21h7" />
              <path v-if="!myMicOn" d="M4.5 4.5l15 15" />
            </svg>
          </span>
          <span class="room-control__label">{{ myMicOn ? '麦克风' : '已静音' }}</span>
        </button>

        <button class="room-control" type="button" @click="showMembers = true">
          <span class="room-control__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="9" cy="8" r="3.4" />
              <path d="M2.8 20c.8-3.3 3.3-5.2 6.2-5.2s5.4 1.9 6.2 5.2" />
              <circle cx="17.2" cy="9.2" r="2.6" />
              <path d="M16.8 15.4c2.5.4 4.1 2 4.6 4.6" />
            </svg>
          </span>
          <span class="room-control__label">成员</span>
        </button>

        <button class="room-control" :class="{ 'room-control--active': sidePanelTab === 'intelligence' }" type="button" @click="sidePanelTab = 'intelligence'">
          <span class="room-control__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3v3M12 18v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M3 12h3M18 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1" />
              <circle cx="12" cy="12" r="4.2" />
            </svg>
          </span>
          <span class="room-control__label">智能助手</span>
        </button>

        <button class="room-control" type="button" @click="showInvite = true">
          <span class="room-control__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="10" cy="8" r="3.4" />
              <path d="M3.5 20c.8-3.3 3.3-5.2 6.5-5.2 1.6 0 3 .4 4.2 1.2" />
              <path d="M18 13.5v7M14.5 17h7" />
            </svg>
          </span>
          <span class="room-control__label">邀请</span>
        </button>
      </footer>

      <!-- 成员弹窗 -->
      <div v-if="showMembers" class="room-mask" @click.self="showMembers = false">
        <div class="room-modal">
          <div class="room-modal__head">
            <h2>参会成员（{{ tiles.length }}）</h2>
            <button class="room-modal__close" type="button" aria-label="关闭" @click="showMembers = false">×</button>
          </div>
          <ul class="room-members">
            <li v-for="tile in tiles" :key="tile.name" class="room-member">
              <span class="room-member__avatar">{{ initialOf(tile.name) }}</span>
              <span class="room-member__name">{{ tile.name }}<em v-if="tile.isSelf">（我）</em></span>
              <span class="room-member__mic" :class="tile.micOn ? 'room-member__mic--on' : 'room-member__mic--off'">
                {{ tile.micOn ? '麦克风开启' : '麦克风关闭' }}
              </span>
            </li>
          </ul>
        </div>
      </div>

      <!-- 邀请弹窗 -->
      <div v-if="showInvite" class="room-mask" @click.self="showInvite = false">
        <div class="room-modal">
          <div class="room-modal__head">
            <h2>邀请参会</h2>
            <button class="room-modal__close" type="button" aria-label="关闭" @click="showInvite = false">×</button>
          </div>
          <p class="room-modal__tip">将会议号或链接分享给同学，即可邀请他们加入会议。</p>
          <div class="room-copy-row">
            <span>会议号</span>
            <code>{{ session.roomCode || '—' }}</code>
            <button type="button" @click="copyText(session.roomCode, '会议号')">复制</button>
          </div>
          <div class="room-copy-row">
            <span>会议链接</span>
            <code>{{ meetingLink }}</code>
            <button type="button" @click="copyText(meetingLink, '会议链接')">复制</button>
          </div>
          <p v-if="copyTip" class="room-copy-tip">{{ copyTip }}</p>
        </div>
      </div>

      <!-- 主持人离开选项 -->
      <div v-if="leaveActionVisible" class="room-mask" @click.self="closeLeaveAction">
        <div class="room-modal room-modal--actions">
          <div class="room-modal__head">
            <h2>离开会议</h2>
            <button class="room-modal__close" type="button" aria-label="关闭" @click="closeLeaveAction">×</button>
          </div>
          <p class="room-modal__tip">你是主持人，离开前可以选择结束会议或转交主持人。</p>
          <button class="room-action-btn room-action-btn--danger" type="button" :disabled="ending" @click="closeLeaveAction(); endRoom()">
            全员结束会议
          </button>
          <button
            class="room-action-btn"
            type="button"
            :disabled="!transferableMembers.length"
            @click="openTransferHost"
          >
            转交主持人并离开
          </button>
          <button class="room-action-btn room-action-btn--ghost" type="button" @click="closeLeaveAction">暂不离开</button>
        </div>
      </div>

      <!-- 转交主持人 -->
      <div v-if="transferHostVisible" class="room-mask" @click.self="closeTransferHost">
        <div class="room-modal">
          <div class="room-modal__head">
            <h2>选择新主持人</h2>
            <button class="room-modal__close" type="button" aria-label="关闭" @click="closeTransferHost">×</button>
          </div>
          <ul v-if="transferableMembers.length" class="room-members">
            <li
              v-for="member in transferableMembers"
              :key="member.name"
              class="room-member room-member--selectable"
              :class="{ 'room-member--selected': selectedTransferMember === member.name }"
              @click="selectTransferMember(member.name)"
            >
              <span class="room-member__avatar">{{ initialOf(member.name) }}</span>
              <span class="room-member__name">{{ member.name }}</span>
              <span v-if="selectedTransferMember === member.name" class="room-member__check">✓</span>
            </li>
          </ul>
          <p v-else class="room-modal__tip">暂无其他参会成员可转交</p>
          <button
            class="room-action-btn"
            type="button"
            :disabled="!selectedTransferMember || transferring"
            @click="confirmTransferHost"
          >
            {{ transferring ? '转交中…' : '确认转交并离开' }}
          </button>
        </div>
      </div>

      <!-- 设置弹窗 -->
      <div v-if="showSettings" class="room-mask" @click.self="showSettings = false">
        <div class="room-modal">
          <div class="room-modal__head">
            <h2>会议设置</h2>
            <button class="room-modal__close" type="button" aria-label="关闭" @click="showSettings = false">×</button>
          </div>
          <div class="room-setting-row">
            <div>
              <p>我的麦克风</p>
              <span>开启后其他成员可以听到你的声音</span>
            </div>
            <button class="room-switch" :class="{ 'room-switch--on': myMicOn }" type="button" role="switch" :aria-checked="myMicOn" @click="myMicOn = !myMicOn">
              <i></i>
            </button>
          </div>
          <div class="room-setting-row">
            <div>
              <p>AI 实时总结</p>
              <span>{{ isCreator ? '主持人开启后，全会议成员可看到增量重点' : '主持人开启后会自动同步到此处' }}</span>
            </div>
            <button
              class="room-switch"
              :class="{ 'room-switch--on': agentEnabled, 'room-switch--disabled': !isCreator }"
              type="button"
              role="switch"
              :aria-checked="agentEnabled"
              :disabled="!isCreator"
              @click="isCreator && toggleAgentMode(!agentEnabled)"
            >
              <i></i>
            </button>
          </div>
          <div class="room-setting-row">
            <div>
              <p>识别状态</p>
              <span>{{ asrStatusText }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.room-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  color: #1f2937;
  background: #f4f7fb;
}

.room-state {
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 14px;
  min-height: 100vh;
  color: #718096;
  font-size: 14px;
  text-align: center;
}

/* ---------- 顶部栏 ---------- */
.room-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 22px;
  border-bottom: 1px solid #e8edf3;
  background: #ffffff;
}

.room-topbar__info {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.room-topbar__info h1 {
  margin: 0;
  overflow: hidden;
  color: #17233a;
  font-size: 18px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.room-topbar__duration {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  color: #2956c2;
  background: #edf3ff;
  font-size: 12px;
  font-weight: 700;
}

.room-topbar__duration-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22a06b;
  animation: room-pulse 1.4s ease-in-out infinite;
}

.room-topbar__duration-dot--ended {
  background: #94a3b8;
  animation: none;
}

.room-ended-tip {
  margin: 14px 22px 0;
  padding: 10px 16px;
  border: 1px solid #e5eaf0;
  border-radius: 10px;
  color: #64748b;
  background: #ffffff;
  font-size: 13px;
  text-align: center;
}

.room-ended-tip--error {
  border-color: #f3c8c8;
  color: #a54239;
  background: #fff6f5;
}

@keyframes room-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(34, 160, 107, 0.45);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(34, 160, 107, 0);
  }
}

.room-topbar__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.room-icon-btn {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid #dbe3ef;
  border-radius: 10px;
  color: #52627a;
  background: #ffffff;
}

.room-icon-btn svg {
  width: 19px;
  height: 19px;
}

.room-icon-btn:hover {
  border-color: #94a3b8;
  color: #23344a;
}

.room-btn {
  min-height: 38px;
  padding: 0 16px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
}

.room-btn--primary {
  color: #ffffff;
  background: #2563eb;
}

.room-btn--leave {
  color: #ffffff;
  background: #dc2626;
}

.room-btn--leave:hover {
  background: #b91c1c;
}

.room-btn--ghost {
  color: #475569;
  background: #f1f5f9;
}

.room-btn--ghost:hover {
  background: #e2e8f0;
}

.room-action-btn {
  display: block;
  width: 100%;
  min-height: 44px;
  margin-top: 10px;
  padding: 0 16px;
  border-radius: 10px;
  color: #1e293b;
  background: #f8fafc;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
}

.room-action-btn:hover:not(:disabled) {
  background: #eef2f7;
}

.room-action-btn--danger {
  color: #ffffff;
  background: #dc2626;
}

.room-action-btn--danger:hover:not(:disabled) {
  background: #b91c1c;
}

.room-action-btn--ghost {
  color: #64748b;
  background: transparent;
}

.room-modal--actions .room-modal__tip {
  margin-bottom: 8px;
}

/* ---------- 主体布局 ---------- */
.room-body {
  display: grid;
  flex: 1;
  gap: 16px;
  min-height: 0;
  padding: 20px 22px;
  grid-template-columns: minmax(0, 1fr) 330px;
}

/* ---------- 人员网格 ---------- */
.room-grid {
  display: grid;
  align-content: center;
  gap: 14px;
  min-height: 0;
  overflow: auto;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
}

.room-tile {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 16 / 10;
  border: 1px solid #e5eaf0;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(30, 43, 76, 0.04);
}

.room-tile__avatar {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  border-radius: 50%;
  color: #ffffff;
  background: linear-gradient(135deg, #2563eb, #4f8bf5);
  font-size: 24px;
  font-weight: 800;
}

.room-tile__name {
  position: absolute;
  bottom: 10px;
  left: 50%;
  max-width: calc(100% - 150px);
  overflow: hidden;
  transform: translateX(-50%);
  color: #26384d;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.room-tile__name em {
  color: #8494a7;
  font-style: normal;
  font-weight: 600;
}

.room-tile__mic {
  position: absolute;
  bottom: 8px;
  left: 8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.room-tile__mic svg {
  width: 12px;
  height: 12px;
}

.room-tile__mic--on {
  color: #2f6b4f;
  background: #edf8f3;
}

.room-tile__mic--off {
  color: #a54239;
  background: #fff1ef;
}

/* ---------- 右侧智能面板 ---------- */
.room-sidepanel {
  display: flex;
  flex-direction: column;
  max-height: 520px;
  min-height: 0;
  border: 1px solid #e5eaf0;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(30, 43, 76, 0.04);
  overflow: hidden;
}

.room-sidepanel__tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-bottom: 1px solid #eef2f7;
}

.room-sidepanel__tabs button {
  min-height: 42px;
  color: #64748b;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 700;
}

.room-sidepanel__tabs button.active {
  color: #2563eb;
  background: #ffffff;
  box-shadow: inset 0 -2px 0 #2563eb;
}

.room-intelligence {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.room-intelligence__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px 10px;
}

.room-intelligence__head h2 {
  margin: 0;
  color: #17233a;
  font-size: 15px;
}

.room-intelligence__head span {
  display: block;
  margin-top: 4px;
  color: #8494a7;
  font-size: 12px;
}

.room-intelligence__reconnect {
  flex-shrink: 0;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #2563eb;
  background: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

.room-intelligence__modes {
  display: inline-flex;
  gap: 6px;
  margin: 0 16px 10px;
  padding: 4px;
  border: 1px solid #e0e6ec;
  border-radius: 9px;
  background: #f4f7fa;
}

.room-intelligence__modes button {
  flex: 1;
  min-height: 30px;
  border-radius: 7px;
  color: #65758a;
  background: transparent;
  font-size: 12px;
  font-weight: 700;
}

.room-intelligence__modes button.active {
  color: #2563eb;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(30, 43, 76, 0.08);
}

.room-intelligence__list {
  display: grid;
  align-content: start;
  gap: 10px;
  flex: 1;
  min-height: 0;
  padding: 0 16px 12px;
  overflow-y: auto;
}

.room-intelligence__bubble,
.room-intelligence__record,
.room-intelligence__card {
  padding: 10px 12px;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  background: #fafbfd;
}

.room-intelligence__bubble--self {
  border-color: #dbeafe;
  background: #eff6ff;
}

.room-intelligence__bubble--partial {
  opacity: 0.72;
}

.room-intelligence__bubble strong,
.room-intelligence__record span {
  display: block;
  margin-bottom: 4px;
  color: #64748b;
  font-size: 12px;
}

.room-intelligence__bubble p,
.room-intelligence__record p,
.room-intelligence__card p {
  margin: 0;
  color: #26384d;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.room-intelligence__card--summary time {
  display: block;
  margin-bottom: 6px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 700;
}

.room-switch--disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* ---------- 评论区 ---------- */
.room-comments {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.room-comments__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  padding: 14px 16px;
  border-bottom: 1px solid #eef2f7;
}

.room-comments__head h2 {
  margin: 0;
  color: #17233a;
  font-size: 15px;
}

.room-comments__head span {
  color: #8494a7;
  font-size: 12px;
}

.room-comments__list {
  display: grid;
  align-content: start;
  gap: 12px;
  flex: 1;
  min-height: 0;
  padding: 14px 16px;
  overflow-y: auto;
}

.room-comments__empty {
  margin: auto;
  color: #94a3b8;
  font-size: 13px;
  text-align: center;
}

.room-comment {
  display: flex;
  gap: 8px;
}

.room-comment--self {
  flex-direction: row-reverse;
}

.room-comment__avatar {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #ffffff;
  background: linear-gradient(135deg, #2563eb, #4f8bf5);
  font-size: 14px;
  font-weight: 800;
}

.room-comment__body {
  display: grid;
  justify-items: start;
  gap: 4px;
  max-width: calc(100% - 48px);
  min-width: 0;
}

.room-comment--self .room-comment__body {
  justify-items: end;
}

.room-comment__meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.room-comment__meta strong {
  color: #26384d;
  font-size: 12px;
}

.room-comment__meta em {
  color: #8494a7;
  font-style: normal;
}

.room-comment__meta time {
  color: #94a3b8;
  font-size: 11px;
}

.room-comment__text {
  margin: 0;
  padding: 8px 12px;
  border-radius: 4px 12px 12px 12px;
  color: #34506c;
  background: #f4f7fb;
  font-size: 13px;
  line-height: 1.6;
}

.room-comment--self .room-comment__text {
  border-radius: 12px 4px 12px 12px;
  color: #ffffff;
  background: #3b82f6;
  word-break: break-word;
}

.room-comments__input {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
  padding: 12px 14px;
  border-top: 1px solid #eef2f7;
}

.room-comments__input input {
  flex: 1;
  min-width: 0;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #dbe3ef;
  border-radius: 10px;
  color: #26384d;
  background: #ffffff;
  font-size: 13px;
}

.room-comments__input input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.room-comments__input .room-btn {
  min-height: 38px;
  padding: 0 16px;
  font-size: 13px;
}

.room-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.room-btn--primary:hover:not(:disabled) {
  background: #1d4ed8;
}

/* ---------- 字幕条 ---------- */
.room-subtitles {
  margin: 0 22px;
  padding: 10px 16px;
  border-radius: 10px;
  color: #34506c;
  background: #ffffff;
  border: 1px solid #e5eaf0;
  font-size: 13px;
  text-align: center;
}

/* ---------- 底部控制栏 ---------- */
.room-controls {
  display: flex;
  justify-content: center;
  gap: 26px;
  padding: 16px 22px 22px;
}

.room-control {
  display: grid;
  justify-items: center;
  gap: 6px;
  min-width: 66px;
  background: transparent;
}

.room-control__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border: 1px solid #dbe3ef;
  border-radius: 14px;
  color: #34506c;
  background: #ffffff;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.room-control__icon svg {
  width: 22px;
  height: 22px;
}

.room-control:hover .room-control__icon {
  border-color: #93b4f0;
  color: #1d4ed8;
  background: #eef4ff;
}

.room-control--active .room-control__icon {
  border-color: #2563eb;
  color: #ffffff;
  background: #2563eb;
}

.room-control--off .room-control__icon {
  border-color: #f3b8b8;
  color: #ffffff;
  background: #dc2626;
}

.room-control__label {
  color: #52627a;
  font-size: 12px;
  font-weight: 700;
}

/* ---------- 弹窗 ---------- */
.room-mask {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.45);
}

.room-modal {
  width: min(460px, 100%);
  max-height: calc(100vh - 48px);
  padding: 22px;
  border-radius: 14px;
  overflow: auto;
  background: #ffffff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.25);
}

.room-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.room-modal__head h2 {
  margin: 0;
  color: #17233a;
  font-size: 18px;
}

.room-modal__close {
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

.room-modal__close:hover {
  color: #334155;
  background: #e5eaf0;
}

.room-modal__tip {
  margin: 0 0 14px;
  color: #718096;
  font-size: 13px;
}

/* 成员列表 */
.room-members {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.room-member {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  background: #fafbfd;
}

.room-member--selectable {
  cursor: pointer;
}

.room-member--selectable:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.room-member--selected {
  border-color: #93c5fd;
  background: #eff6ff;
}

.room-member__check {
  margin-left: auto;
  color: #2563eb;
  font-weight: 700;
}

.room-member__avatar {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #ffffff;
  background: linear-gradient(135deg, #2563eb, #4f8bf5);
  font-size: 14px;
  font-weight: 800;
}

.room-member__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #26384d;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.room-member__name em {
  color: #8494a7;
  font-style: normal;
  font-weight: 600;
}

.room-member__mic {
  flex: 0 0 auto;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.room-member__mic--on {
  color: #2f6b4f;
  background: #edf8f3;
}

.room-member__mic--off {
  color: #a54239;
  background: #fff1ef;
}

/* 邀请复制行 */
.room-copy-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid #eef2f7;
}

.room-copy-row > span {
  flex: 0 0 56px;
  color: #8494a7;
  font-size: 13px;
}

.room-copy-row code {
  flex: 1;
  min-width: 0;
  padding: 4px 8px;
  border-radius: 6px;
  overflow: hidden;
  color: #2956c2;
  background: #f4f7fb;
  font-size: 12px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.room-copy-row button {
  flex: 0 0 auto;
  min-height: 28px;
  padding: 0 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #2563eb;
  background: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

.room-copy-row button:hover {
  border-color: #2563eb;
  background: #eef4ff;
}

.room-copy-tip {
  margin: 12px 0 0;
  color: #2f6b4f;
  font-size: 13px;
  text-align: center;
}

/* 设置项 */
.room-setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 14px;
  border: 1px solid #e5eaf0;
  border-radius: 10px;
  background: #fafbfd;
}

.room-setting-row + .room-setting-row {
  margin-top: 10px;
}

.room-setting-row p {
  margin: 0;
  color: #26384d;
  font-size: 14px;
  font-weight: 700;
}

.room-setting-row span {
  display: block;
  margin-top: 3px;
  color: #8494a7;
  font-size: 12px;
}

.room-switch {
  position: relative;
  flex: 0 0 auto;
  width: 44px;
  height: 24px;
  border-radius: 999px;
  background: #cbd5e1;
  transition: background 0.18s ease;
}

.room-switch i {
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

.room-switch--on {
  background: #2563eb;
}

.room-switch--on i {
  left: 23px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 960px) {
  .room-body {
    padding: 14px;
    grid-template-columns: 1fr;
  }

  .room-comments {
    flex: 0 0 auto;
    height: 320px;
  }

  .room-sidepanel {
    flex: 0 0 auto;
    height: 360px;
  }
}

@media (max-width: 640px) {
  .room-topbar {
    padding: 12px 14px;
  }

  .room-topbar__info h1 {
    font-size: 15px;
  }

  .room-grid {
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }

  .room-tile__avatar {
    width: 46px;
    height: 46px;
    font-size: 18px;
  }

  .room-controls {
    gap: 14px;
  }
}
</style>
