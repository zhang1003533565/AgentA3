<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getMeetingDetail } from '../api/meetings'
import { getUserInfo } from '../utils/auth'

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const loading = ref(true)
const error = ref('')
const myName = ref('我')
const myMicOn = ref(true)
const subtitlesOn = ref(false)
const showMembers = ref(false)
const showInvite = ref(false)
const showSettings = ref(false)
const copyTip = ref('')
const now = ref(Date.now())

let enteredAt = Date.now()
let timer = null

const session = computed(() => detail.value?.session || null)

const elapsedLabel = computed(() => {
  const startSource = session.value?.startTime
  const startAt = startSource ? new Date(startSource).getTime() : enteredAt
  if (!startAt || Number.isNaN(startAt)) return '00:00'
  return formatElapsed((now.value - startAt) / 1000)
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

async function loadRoom() {
  loading.value = true
  error.value = ''
  try {
    const result = await getMeetingDetail(route.params.sessionId)
    detail.value = result?.data || null
  } catch (cause) {
    error.value = cause.message || '会议信息加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const user = getUserInfo() || {}
  myName.value = route.query.name || user.realName || user.username || '我'
  myMicOn.value = route.query.mic !== '0'
  enteredAt = Date.now()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
  loadRoom()
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
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
            <i class="room-topbar__duration-dot" aria-hidden="true"></i>
            已进行 {{ elapsedLabel }}
          </span>
        </div>
        <div class="room-topbar__actions">
          <button class="room-icon-btn" type="button" aria-label="设置" title="设置" @click="showSettings = true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="3.2" />
              <path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.2 5.2l1.7 1.7M17.1 17.1l1.7 1.7M5.2 18.8l1.7-1.7M17.1 6.9l1.7-1.7" />
            </svg>
          </button>
          <button class="room-btn room-btn--leave" type="button" @click="leaveRoom">离开会议</button>
        </div>
      </header>

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

      <!-- 字幕条 -->
      <div v-if="subtitlesOn" class="room-subtitles">实时字幕已开启，正在识别会议发言…</div>

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

        <button class="room-control" :class="{ 'room-control--active': subtitlesOn }" type="button" @click="subtitlesOn = !subtitlesOn">
          <span class="room-control__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <rect x="2.5" y="5" width="19" height="14" rx="2.5" />
              <path d="M6 12h5M13.5 12H18M6 15.5h3.5M12 15.5H18" />
            </svg>
          </span>
          <span class="room-control__label">字幕</span>
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
              <p>实时字幕</p>
              <span>在画面下方显示会议实时字幕</span>
            </div>
            <button class="room-switch" :class="{ 'room-switch--on': subtitlesOn }" type="button" role="switch" :aria-checked="subtitlesOn" @click="subtitlesOn = !subtitlesOn">
              <i></i>
            </button>
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

/* ---------- 人员网格 ---------- */
.room-grid {
  display: grid;
  flex: 1;
  align-content: center;
  gap: 14px;
  padding: 20px 22px;
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
@media (max-width: 640px) {
  .room-topbar {
    padding: 12px 14px;
  }

  .room-topbar__info h1 {
    font-size: 15px;
  }

  .room-grid {
    padding: 14px;
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
