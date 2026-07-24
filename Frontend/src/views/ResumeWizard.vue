<script setup>
import { ref, nextTick, onMounted, watch } from 'vue'
import AppTabBar from '../components/AppTabBar.vue'

const messages = ref([
  {
    role: 'ai',
    text: '你好！我是 AI 简历向导。我会通过几个问题帮你创建一份专业的简历。首先，请告诉我你想要应聘的职位是什么？',
    time: '刚刚',
  },
])

const inputText = ref('')
const isThinking = ref(false)
const questionCount = ref(0)
const totalQuestions = ref(10)
const progress = ref(0)
const chatContainer = ref(null)

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || isThinking.value) return

  messages.value.push({ role: 'user', text, time: '刚刚' })
  inputText.value = ''
  isThinking.value = true
  questionCount.value++

  await new Promise((resolve) => setTimeout(resolve, 1500 + Math.random() * 1000))
  isThinking.value = false

  const aiReplies = [
    '感谢你的分享！接下来我们聊聊工作经历。请描述你最自豪的一个项目——你在其中扮演了什么角色，用了哪些技术，取得了什么成果？',
    '很好！现在来补充一些细节。在这个项目中，你有没有量化的工作成果？比如性能提升了多少，用户量增长了多少？',
    '接下来我们看看技能部分。除了你提到的技术栈，你还有哪些证书、语言能力或者其他特长？',
    '最后一步了！请告诉我你的教育经历——学校、专业、学位，以及在校期间有什么突出的成就或奖项？',
  ]

  const replyIndex = Math.min(questionCount.value - 1, aiReplies.length - 1)
  messages.value.push({ role: 'ai', text: aiReplies[replyIndex], time: '刚刚' })
  progress.value = Math.round((questionCount.value / totalQuestions.value) * 100)

  await nextTick()
  scrollToBottom()
}

const scrollToBottom = () => {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

onMounted(() => scrollToBottom())

watch(messages, () => nextTick(() => scrollToBottom()), { deep: true })
</script>

<template>
  <div class="wizard">
    <AppTabBar />
    <div class="wizard-layout">
      <!-- 左侧聊天 -->
      <div class="chat-panel">
        <header class="chat-header">
          <router-link to="/resume" class="back-link">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
            返回
          </router-link>
          <h1>AI 简历向导</h1>
          <div class="progress-badge">{{ progress }}%</div>
        </header>

        <div class="progress-bar-wrap">
          <div class="progress-bar-fill" :style="{ width: progress + '%' }"></div>
        </div>

        <div class="chat-area" ref="chatContainer">
          <div v-for="(msg, i) in messages" :key="i" :class="['chat-bubble', msg.role === 'user' ? 'chat-user' : 'chat-ai']">
            <div v-if="msg.role === 'ai'" class="chat-avatar">AI</div>
            <div class="chat-content">
              <p class="chat-text">{{ msg.text }}</p>
              <span class="chat-time">{{ msg.time }}</span>
            </div>
            <div v-if="msg.role === 'user'" class="chat-avatar user-avatar">我</div>
          </div>

          <div v-if="isThinking" class="chat-bubble chat-ai">
            <div class="chat-avatar">AI</div>
            <div class="chat-content">
              <div class="thinking-dots"><span></span><span></span><span></span></div>
            </div>
          </div>
        </div>

        <div class="input-bar">
          <div class="input-row">
            <textarea
              v-model="inputText"
              placeholder="输入你的回答..."
              :disabled="isThinking"
              @keydown.enter.exact.prevent="sendMessage"
              rows="2"
            ></textarea>
            <button class="send-btn" :disabled="!inputText.trim() || isThinking" @click="sendMessage">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/></svg>
            </button>
          </div>
          <div class="input-actions">
            <span class="input-hint">{{ questionCount }}/{{ totalQuestions }} 个问题</span>
            <div class="input-btns">
              <button class="btn-ghost-sm">跳过</button>
              <button class="btn-ghost-sm">返回</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧预览 -->
      <div class="preview-panel">
        <div class="preview-header">
          <h2>实时预览</h2>
        </div>
        <div class="preview-content">
          <div class="preview-empty">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#cbd5e1" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            <p>回答几个问题后，简历将在这里实时展示</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wizard {
  min-height: 100vh;
  padding-top: 62px;
  background: #f8fafc;
}

.wizard-layout {
  display: grid;
  grid-template-columns: 1fr 420px;
  height: calc(100vh - 62px);
  max-width: 1400px;
  margin: 0 auto;
}

/* ===== 左侧聊天 ===== */
.chat-panel {
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e2e8f0;
  background: #ffffff;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #64748b;
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
}

.back-link:hover {
  color: #0f172a;
}

.chat-header h1 {
  flex: 1;
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.progress-badge {
  padding: 4px 12px;
  border-radius: 999px;
  color: #2563eb;
  background: #eff6ff;
  font-size: 13px;
  font-weight: 700;
}

.progress-bar-wrap {
  height: 3px;
  background: #e2e8f0;
}

.progress-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #2563eb, #0891b2);
  transition: width 0.4s ease;
}

.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-bubble {
  display: flex;
  gap: 10px;
  max-width: 75%;
}

.chat-ai { align-self: flex-start; }

.chat-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.chat-avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #ffffff;
  background: #2563eb;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-avatar { background: #0f766e; }

.chat-text {
  margin: 0;
  padding: 12px 16px;
  border-radius: 12px;
  color: #1f2937;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  font-size: 14px;
  line-height: 1.7;
}

.chat-user .chat-text {
  color: #ffffff;
  background: #2563eb;
  border: 0;
}

.chat-time {
  color: #94a3b8;
  font-size: 11px;
  padding: 0 4px;
  margin-top: 4px;
  display: block;
}

.chat-user .chat-time { text-align: right; }

.thinking-dots {
  display: flex;
  gap: 5px;
  padding: 14px 18px;
}

.thinking-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
  animation: dotBounce 1.4s ease-in-out infinite;
}

.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dotBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* ===== 输入栏 ===== */
.input-bar {
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
  background: #ffffff;
}

.input-row {
  display: flex;
  gap: 10px;
}

textarea {
  flex: 1;
  min-height: 48px;
  max-height: 100px;
  padding: 12px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  outline: none;
  font-family: inherit;
}

textarea:focus {
  border-color: #2563eb;
  background: #ffffff;
}

.send-btn {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  border: 0;
  color: #ffffff;
  background: #2563eb;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.2s;
}

.send-btn:hover { background: #1d4ed8; }
.send-btn:disabled { background: #93c5fd; cursor: not-allowed; }

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.input-hint {
  color: #94a3b8;
  font-size: 13px;
}

.input-btns {
  display: flex;
  gap: 8px;
}

.btn-ghost-sm {
  padding: 5px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #64748b;
  background: #ffffff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-ghost-sm:hover {
  background: #f8fafc;
}

/* ===== 右侧预览 ===== */
.preview-panel {
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  overflow-y: auto;
}

.preview-header {
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.preview-header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.preview-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.preview-card {
  padding: 28px 24px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  font-size: 13px;
  line-height: 1.7;
}

.preview-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 80px 40px;
  text-align: center;
}

.preview-empty p {
  margin: 0;
  color: #94a3b8;
  font-size: 14px;
  line-height: 1.6;
}

.preview-name {
  color: #0f172a;
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 2px;
}

.preview-title {
  color: #64748b;
  font-size: 14px;
  margin-bottom: 8px;
}

.preview-contact {
  color: #94a3b8;
  font-size: 12px;
  margin-bottom: 20px;
}

.preview-section {
  margin-bottom: 18px;
}

.preview-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #0f172a;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.preview-bar {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: #2563eb;
}

.preview-text {
  margin: 0;
  padding-left: 11px;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.preview-item {
  display: flex;
  gap: 8px;
  padding-left: 3px;
}

.preview-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #2563eb;
  margin-top: 7px;
  flex-shrink: 0;
  opacity: 0.5;
}

.preview-item-title {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.preview-item-sub {
  color: #94a3b8;
  font-size: 12px;
}

.preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-left: 11px;
}

.preview-tag {
  padding: 3px 10px;
  border-radius: 999px;
  color: #2563eb;
  background: #eff6ff;
  font-size: 12px;
  font-weight: 600;
}
</style>
