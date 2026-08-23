<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getChatMessages, getChatSessions, sendChatMessage } from '../api/secondhand'

const route = useRoute()
const router = useRouter()
const sessions = ref([])
const currentId = ref(route.query.sessionId || '')
const messages = ref([])
const draft = ref('')
const loading = ref(false)
const error = ref('')
let timer
const rows = (value) => Array.isArray(value) ? value : value?.content || value?.records || value?.list || []

async function loadSessions() {
  try {
    sessions.value = rows(await getChatSessions({ page:0,size:50 }))
    if (!currentId.value && sessions.value[0]) currentId.value = sessions.value[0].sessionId
  } catch (cause) { error.value = cause.message }
}
async function loadMessages() {
  if (!currentId.value) return
  try { messages.value = rows(await getChatMessages(currentId.value, { page:0,size:100 })) } catch (cause) { error.value = cause.message }
}
async function select(session) { currentId.value = session.sessionId; await loadMessages() }
async function send() {
  if (!draft.value.trim() || !currentId.value || loading.value) return
  loading.value = true
  try {
    await sendChatMessage({ sessionId:Number(currentId.value), content:draft.value.trim(), messageType:1 })
    draft.value = ''
    await loadMessages()
  } catch (cause) { error.value = cause.message } finally { loading.value = false }
}
onMounted(async () => { await loadSessions(); await loadMessages(); timer = setInterval(loadMessages, 5000) })
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="feature-page"><AppTabBar /><main class="chat-shell feature-card">
    <aside class="session-list"><header><button @click="router.push('/marketplace')">‹</button><h1>市集消息</h1></header><div v-if="!sessions.length" class="feature-empty">暂无会话</div><button v-for="session in sessions" :key="session.sessionId" :class="{active:String(currentId)===String(session.sessionId)}" @click="select(session)"><span class="avatar">{{ (session.otherUsername||'用').slice(0,1) }}</span><span><strong>{{ session.otherUsername || '校园用户' }}</strong><small>{{ session.itemTitle }} · {{ session.lastMessage || '暂无消息' }}</small></span></button></aside>
    <section class="conversation"><div v-if="error" class="feature-error">{{ error }}</div><div class="message-list"><div v-if="!messages.length" class="feature-empty">选择会话后开始沟通</div><div v-for="message in messages" :key="message.id" class="message" :class="{mine:message.isMine}"><span>{{ message.senderName }}</span><p>{{ message.content }}</p><time>{{ message.createTime }}</time></div></div><form @submit.prevent="send"><textarea v-model="draft" class="feature-textarea" placeholder="输入消息，确认商品情况和线下交易安排"></textarea><button class="feature-button feature-button--primary" :disabled="loading">发送</button></form></section>
  </main></div>
</template>

<style scoped>
.chat-shell{display:grid;grid-template-columns:320px 1fr;width:min(1100px,calc(100% - 40px));height:calc(100vh - 100px);margin:20px auto;overflow:hidden}.session-list{border-right:1px solid #e3e9ef;overflow:auto}.session-list header{display:flex;align-items:center;gap:10px;padding:18px;border-bottom:1px solid #e7ecf1}.session-list header button{width:32px;height:32px;border-radius:50%;background:#eef2f5;font-size:23px}.session-list h1{margin:0;font-size:19px}.session-list>button{display:flex;gap:12px;width:100%;padding:14px 16px;border-bottom:1px solid #edf1f4;color:#344a60;background:#fff;text-align:left}.session-list>button.active{background:#eef4f8}.avatar{display:grid;flex:0 0 38px;place-items:center;width:38px;height:38px;border-radius:50%;color:#315f8c;background:#dfeaf3;font-weight:800}.session-list strong,.session-list small{display:block}.session-list small{max-width:220px;margin-top:6px;overflow:hidden;color:#7a8997;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.conversation{display:grid;grid-template-rows:1fr auto;min-width:0;background:#f7f9fb}.message-list{padding:24px;overflow:auto}.message{max-width:70%;margin-bottom:17px}.message>span,.message time{display:block;color:#8a97a4;font-size:11px}.message p{display:inline-block;margin:5px 0;padding:11px 14px;border:1px solid #dce3e9;border-radius:4px 12px 12px;color:#344a60;background:#fff;line-height:1.6}.message.mine{margin-left:auto;text-align:right}.message.mine p{border-color:#527797;color:#fff;background:#527797;border-radius:12px 4px 12px 12px}.conversation form{display:flex;align-items:end;gap:10px;padding:16px;border-top:1px solid #dce3e9;background:#fff}.conversation form textarea{min-height:68px}.conversation form button{height:42px}@media(max-width:760px){.chat-shell{grid-template-columns:1fr;height:auto}.session-list{max-height:300px;border-right:0;border-bottom:1px solid #e3e9ef}.conversation{min-height:560px}}
</style>
