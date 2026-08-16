<script>
// 模块级状态 —— 离开页面再回来也不会丢
const now = new Date()
function ago(m) { const d = new Date(now); d.setMinutes(d.getMinutes() - m); return d.toISOString().replace('T', ' ').slice(0, 19) }

const state = {
  messages: [
    { id: 6, moduleType: 'FORUM', eventType: 'REPLY', title: '张三 回复了你的帖子', content: '这个活动在二食堂门口报名，带学生证就行', createTime: ago(15), isRead: false, targetPage: '' },
    { id: 7, moduleType: 'FORUM', eventType: 'REPLY', title: '赵六 回复了你的帖子', content: '同问，我也想知道选课系统什么时候开', createTime: ago(45), isRead: true, targetPage: '' },
    { id: 8, moduleType: 'FORUM', eventType: 'LIKE', title: '你的帖子收到 5 个赞', content: '"关于期末复习资料分享"的帖子获得 5 个赞', createTime: ago(90), isRead: true, targetPage: '' },
    { id: 9, moduleType: 'EXAM', eventType: 'COMPLETE', title: 'AI 组卷任务完成', content: '《数据结构》期末试卷已生成完毕，共 50 道题，点击查看', createTime: ago(20), isRead: false, targetPage: '' },
    { id: 10, moduleType: 'EXAM', eventType: 'SCORE', title: '试卷批改完成', content: '你的《线性代数》试卷已批改，得分 92 分，排名第 3', createTime: ago(180), isRead: false, targetPage: '' },
    { id: 11, moduleType: 'MEETING', eventType: 'SUMMARY', title: '会议总结已生成', content: '2026-07-24 项目周会的 AI 总结已生成，含 12 条待办事项', createTime: ago(60), isRead: true, targetPage: '' },
    { id: 12, moduleType: 'MEETING', eventType: 'INVITE', title: '会议邀请', content: '张老师邀请你参加 7月26日 的班会讨论', createTime: ago(240), isRead: false, targetPage: '' },
    { id: 13, moduleType: 'LEARNING', eventType: 'PATH_UPDATE', title: '学习路径已更新', content: '根据你的答题情况，Python 学习路径已调整，新增 3 个资源推荐', createTime: ago(10), isRead: false, targetPage: '' },
    { id: 14, moduleType: 'LEARNING', eventType: 'PROFILE', title: '学习画像更新', content: '你的编程能力维度已更新，当前等级：中级', createTime: ago(200), isRead: true, targetPage: '' },
  ],
}

// 响应式所需的强制刷新计数
let tick = 0
</script>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import AppTabBar from '../components/AppTabBar.vue'

const router = useRouter()
const activeModule = ref('FORUM')

const modules = [
  { type: 'FORUM', label: '论坛' },
  { type: 'EXAM', label: '题库' },
  { type: 'MEETING', label: '会议' },
  { type: 'LEARNING', label: '学习' },
]

// 用来强制视图更新的 ref
const updateKey = ref(0)
function notify() { updateKey.value++ }

const activeModuleLabel = computed(() => {
  const m = modules.find(m => m.type === activeModule.value)
  return m ? m.label : '消息'
})

// 每次计算都从 state.messages 读最新 isRead
const moduleMessages = computed(() => {
  void updateKey.value // 依赖这个 key
  return state.messages.filter(m => m.moduleType === activeModule.value)
})

function unreadFor(moduleType) {
  void updateKey.value
  return state.messages.filter(m => m.moduleType === moduleType && !m.isRead).length
}

const moduleUnread = computed(() => {
  void updateKey.value
  return unreadFor(activeModule.value)
})

function groupMessages(list) {
  const order = ['CHAT_MESSAGE', 'TRADE_BUY', 'SYSTEM', 'REPLY', 'LIKE', 'COMPLETE', 'SCORE', 'SUMMARY', 'INVITE', 'PATH_UPDATE', 'PROFILE']
  const groups = new Map()
  for (const m of list) {
    const key = m.eventType || 'OTHER'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(m)
  }
  const entries = [...groups.entries()]
  entries.sort((a, b) => order.indexOf(a[0]) - order.indexOf(b[0]))
  return entries
}

function handleClick(msg) {
  msg.isRead = true
  notify()
  if (msg.targetPage) router.push(msg.targetPage)
}

function switchModule(type) {
  activeModule.value = type
}

function handleMarkAll() {
  state.messages.forEach(m => { m.isRead = true })
  notify()
}

function eventLabel(eventType) {
  const map = {
    CHAT_MESSAGE: '聊天消息',
    TRADE_BUY: '交易消息',
    SYSTEM: '系统消息',
    REPLY: '回复我的',
    LIKE: '点赞通知',
    COMPLETE: '组卷任务',
    SCORE: '批改进度',
    SUMMARY: 'AI 总结',
    INVITE: '会议邀请',
    PATH_UPDATE: '学习路径',
    PROFILE: '学习画像',
  }
  return map[eventType] || '系统消息'
}

function timeLabel(t) {
  if (!t) return ''
  const d = new Date(t.replace(/-/g, '/'))
  const now = Date.now()
  const diff = now - d
  if (diff < 6e4) return '刚刚'
  if (diff < 36e5) return Math.floor(diff / 6e4) + ' 分钟前'
  if (diff < 864e5) return Math.floor(diff / 36e5) + ' 小时前'
  return t.slice(0, 10)
}

function totalUnread() {
  void updateKey.value
  return state.messages.filter(m => !m.isRead).length
}
</script>

<template>
  <div class="phone-shell">
    <main class="page">
      <header class="topbar">
        <button class="back-btn" type="button" @click="router.back()">‹</button>
        <h1>消息中心</h1>
        <button v-if="totalUnread() > 0" class="mark-all-btn" type="button" @click="handleMarkAll">全部已读</button>
        <span v-else class="mark-all-placeholder"></span>
      </header>

      <nav class="module-tabs">
        <button
          v-for="m in modules"
          :key="m.type"
          class="tab-item"
          :class="{ active: activeModule === m.type }"
          type="button"
          @click="switchModule(m.type)"
        >
          <span>{{ m.label }}</span>
          <span v-if="unreadFor(m.type) > 0" class="badge">{{ unreadFor(m.type) }}</span>
        </button>
      </nav>

      <div class="section-header">
        <h2 class="section-title">{{ activeModuleLabel }}</h2>
        <span v-if="moduleUnread > 0" class="section-unread">{{ moduleUnread }} 条未读</span>
      </div>

      <p v-if="moduleMessages.length === 0" class="muted empty">暂无{{ activeModuleLabel }}消息</p>

      <template v-else>
        <div v-for="[eventType, groupMsgs] in groupMessages(moduleMessages)" :key="eventType" class="msg-group">
          <h3 class="group-title">{{ eventLabel(eventType) }}</h3>
          <ul class="msg-list">
            <li
              v-for="m in groupMsgs"
              :key="m.id"
              class="msg-card"
              :class="{ unread: !m.isRead }"
              @click="handleClick(m)"
            >
              <div class="msg-header">
                <span class="msg-title">{{ m.title }}</span>
                <span class="msg-time">{{ timeLabel(m.createTime) }}</span>
              </div>
              <p class="msg-body">{{ m.content }}</p>
              <span v-if="!m.isRead" class="dot"></span>
            </li>
          </ul>
        </div>
      </template>
    </main>

    <AppTabBar />
  </div>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  min-height: 52px;
  border-bottom: 1px solid #eef2f7;
  background: #fff;
}
.topbar h1 { flex: 1; margin: 0; font-size: 18px; color: #111827; }
.back-btn {
  border: none; background: none; font-size: 28px; color: #2563eb;
  cursor: pointer; padding: 0 4px; line-height: 1;
}
.mark-all-btn { border: none; background: none; font-size: 14px; color: #2563eb; cursor: pointer; }
.mark-all-placeholder { width: 56px; }

.module-tabs {
  display: flex;
  border-bottom: 1px solid #eef2f7;
  background: #fff;
  overflow-x: auto;
}
.tab-item {
  position: relative;
  flex: 1;
  min-width: 0;
  padding: 12px 4px;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  font-size: 14px;
  color: #6b7280;
  cursor: pointer;
  white-space: nowrap;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.tab-item.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
  font-weight: 600;
}
.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
}

.section-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 14px 16px 8px;
}
.section-title { margin: 0; font-size: 16px; color: #111827; }
.section-unread { font-size: 12px; color: #9ca3af; }

.msg-group { margin-bottom: 4px; }
.group-title {
  margin: 0;
  padding: 8px 16px 4px;
  font-size: 12px;
  font-weight: 500;
  color: #9ca3af;
}

.msg-list { list-style: none; padding: 0; margin: 0; }
.msg-card {
  position: relative;
  padding: 14px 16px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
  background: #fff;
  transition: background .15s;
}
.msg-card:active { background: #f9fafb; }
.msg-card.unread { background: #eff6ff; }

.msg-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.msg-title { font-size: 15px; font-weight: 600; color: #111827; }
.msg-time { font-size: 12px; color: #9ca3af; white-space: nowrap; margin-left: 8px; }
.msg-body {
  margin: 6px 0 0;
  font-size: 13px; color: #6b7280; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}

.dot {
  position: absolute; top: 16px; right: 6px;
  width: 7px; height: 7px; border-radius: 50%; background: #ef4444;
}

.muted { font-size: 14px; color: #9ca3af; }
.empty { text-align: center; margin-top: 60px; }
</style>
