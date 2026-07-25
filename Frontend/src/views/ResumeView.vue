<script setup>
import { ref, computed } from 'vue'
import AppTabBar from '../components/AppTabBar.vue'

// ===== Mock 数据（零使用状态）=====
const processingStatus = ref('pending')

const stats = ref({
  resumeCount: 0,
  aiUsageCount: 0,
  lastEditedDate: null,
  hasMasterResume: false,
})

const masterResume = ref(null)
const tailoredResumes = ref([])

// ===== 工具函数 =====
const formatDate = (value) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('zh-CN', { month: 'short', day: '2-digit', year: 'numeric' })
}

const getRelativeTime = (value) => {
  if (!value) return '暂无'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无'
  const diff = Date.now() - date.getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return formatDate(value)
}

const getMonogram = (title) => {
  const words = title.split(/\s+/).filter((w) => /^[a-zA-Z一-鿿]/.test(w))
  if (words.length === 0 && title.length > 0) return title.charAt(0).toUpperCase()
  return words.slice(0, 2).map((w) => w.charAt(0).toUpperCase()).join('')
}

const cardColors = [
  { bg: '#2563eb', fg: '#ffffff' },
  { bg: '#10b981', fg: '#ffffff' },
  { bg: '#0f172a', fg: '#ffffff' },
  { bg: '#8b5cf6', fg: '#ffffff' },
  { bg: '#0ea5e9', fg: '#ffffff' },
  { bg: '#f59e0b', fg: '#ffffff' },
  { bg: '#ef4444', fg: '#ffffff' },
  { bg: '#6366f1', fg: '#ffffff' },
]

const hashTitle = (title) => {
  let hash = 0
  for (let i = 0; i < title.length; i++) {
    hash = (hash << 5) - hash + title.charCodeAt(i)
    hash |= 0
  }
  return Math.abs(hash)
}

const statusDisplay = computed(() => {
  switch (processingStatus.value) {
    case 'loading': return { text: '检查中...', color: '#64748b' }
    case 'processing': return { text: 'AI 解析中', color: '#2563eb' }
    case 'ready': return { text: '已就绪', color: '#10b981' }
    case 'failed': return { text: '处理失败', color: '#dc2626' }
    default: return { text: '等待上传', color: '#64748b' }
  }
})

const lastAiGenTime = ref(localStorage.getItem('last_ai_gen_time') || null)

const lastAiGenDisplay = computed(() => {
  if (!lastAiGenTime.value) return '从未'
  const diff = Date.now() - Number(lastAiGenTime.value)
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
})
</script>

<template>
  <div class="dashboard">
    <AppTabBar />
    <div class="container">
      <header class="page-header">
        <h1>我的简历</h1>
      </header>

      <!-- 统计卡片 -->
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon" style="background: #eff6ff; color: #2563eb;">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          </div>
          <div class="stat-label">简历数量</div>
          <div class="stat-value">{{ stats.resumeCount }} 份</div>
          <div class="stat-sub">最近更新：{{ getRelativeTime(stats.lastEditedDate) }}</div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: #fef3c7; color: #d97706;">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
          </div>
          <div class="stat-label">AI 使用</div>
          <div class="stat-value">{{ stats.aiUsageCount }} 次</div>
          <div class="stat-sub">累计优化建议：{{ stats.aiUsageCount * 3 }} 条</div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: #fce7f3; color: #db2777;">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          </div>
          <div class="stat-label">最近编辑</div>
          <div class="stat-value" style="font-size: 16px;">{{ formatDate(stats.lastEditedDate) }}</div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: #d1fae5; color: #059669;">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>
          </div>
          <div class="stat-label">主简历状态</div>
          <div class="stat-value" :style="{ color: statusDisplay.color }">
            <span v-if="processingStatus === 'ready'" class="status-dot" style="background: #10b981;"></span>
            <span v-else-if="processingStatus === 'processing'" class="status-dot spinning"></span>
            <span v-else-if="processingStatus === 'failed'" class="status-dot" style="background: #dc2626;"></span>
            {{ statusDisplay.text }}
          </div>
        </div>
      </div>

      <!-- AI 助手卡片 -->
      <router-link to="/resume/wizard" class="ai-card" @click="lastAiGenTime = String(Date.now()); localStorage.setItem('last_ai_gen_time', lastAiGenTime)">
        <div class="ai-card-inner">
          <div class="ai-icon-wrap">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
          </div>
          <div class="ai-content">
            <h3>AI 简历向导</h3>
            <p>通过 AI 对话智能生成简历内容，只需回答几个问题</p>
            <div class="ai-stats">
              <span class="ai-stat-item">
                <span class="ai-stat-dot" style="background: #10b981;"></span>
                累计生成 <strong>{{ stats.aiUsageCount }}</strong> 次
              </span>
              <span class="ai-stat-divider">|</span>
              <span class="ai-stat-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                上次生成：{{ lastAiGenDisplay }}
              </span>
            </div>
          </div>
          <div class="ai-arrow">
            继续生成
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
          </div>
        </div>
      </router-link>

      <!-- 操作卡片 -->
      <div class="action-grid">
        <router-link to="/resume/wizard" class="action-card">
          <div class="action-icon" style="background: #eff6ff; color: #2563eb;">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
          </div>
          <h3>上传简历</h3>
          <p>上传已有简历文件，AI 自动解析并优化格式</p>
        </router-link>

        <router-link to="/resume/designer" class="action-card">
          <div class="action-icon" style="background: #d1fae5; color: #059669;">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          </div>
          <h3>编辑简历</h3>
          <p>{{ masterResume ? '查看和编辑主简历内容' : '先创建一份简历开始' }}</p>
        </router-link>

        <router-link to="/resume/wizard" class="action-card">
          <div class="action-icon" style="background: #fef3c7; color: #d97706;">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>
          </div>
          <h3>岗位匹配</h3>
          <p>粘贴 JD 分析匹配度，获取针对性优化建议</p>
        </router-link>
      </div>

      <!-- 主简历 -->
      <div v-if="masterResume" class="section">
        <h2 class="section-title">主简历</h2>
        <router-link to="/resume/designer" class="master-card">
          <div class="master-left">
            <div class="master-icon">M</div>
            <div>
              <h3>{{ masterResume.title }}</h3>
              <p :style="{ color: statusDisplay.color, fontSize: '13px', margin: 0 }">{{ statusDisplay.text }}</p>
            </div>
          </div>
          <div class="master-right">
            <button v-if="processingStatus === 'failed'" class="btn-ghost">重试</button>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </router-link>
      </div>

      <!-- 定制简历 -->
      <div v-if="tailoredResumes.length" class="section">
        <h2 class="section-title">定制简历</h2>
        <div class="project-grid">
          <div v-for="resume in tailoredResumes" :key="resume.id" class="project-card">
            <div class="project-header">
              <div class="project-avatar" :style="{ backgroundColor: cardColors[hashTitle(resume.title) % cardColors.length].bg, color: cardColors[hashTitle(resume.title) % cardColors.length].fg }">
                {{ getMonogram(resume.title) }}
              </div>
              <span class="project-badge" :class="resume.processingStatus === 'ready' ? 'badge-ready' : 'badge-processing'">
                {{ resume.processingStatus === 'ready' ? '已就绪' : '处理中' }}
              </span>
            </div>
            <h3 class="project-title">{{ resume.title }}</h3>
            <p class="project-snippet">{{ resume.jobSnippet?.slice(0, 50) }}{{ (resume.jobSnippet?.length || 0) > 50 ? '...' : '' }}</p>
            <p class="project-date">编辑于 {{ getRelativeTime(resume.updatedAt) }}</p>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!masterResume && !tailoredResumes.length" class="empty-state">
        <div class="empty-icon">
          <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/></svg>
        </div>
        <h3>还没有简历</h3>
        <p>上传你的第一份简历，或使用 AI 向导创建一份专业简历</p>
        <div class="empty-actions">
          <router-link to="/resume/wizard" class="btn-primary">AI 向导创建</router-link>
          <router-link to="/resume/wizard" class="btn-ghost">上传已有简历</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: #f8fafc;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 90px 40px 32px;
}

.page-header {
  margin-bottom: 28px;
}

.page-header h1 {
  margin: 0;
  color: #0f172a;
  font-size: 28px;
  font-weight: 700;
}

/* ===== 统计卡片 ===== */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  margin-bottom: 12px;
}

.stat-label {
  color: #64748b;
  font-size: 13px;
  margin-bottom: 4px;
}

.stat-value {
  color: #0f172a;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-sub {
  color: #94a3b8;
  font-size: 12px;
  margin-top: 6px;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.status-dot.spinning {
  width: 16px;
  height: 16px;
  border: 2px solid #e2e8f0;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ===== AI 卡片 ===== */
.ai-card {
  display: flex;
  margin-bottom: 24px;
  padding: 28px 32px;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  background: linear-gradient(135deg, #ffffff 0%, #eff6ff 100%);
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.ai-card:hover {
  box-shadow: 0 4px 24px rgba(37, 99, 235, 0.1);
}

.ai-card-inner {
  display: flex;
  align-items: center;
  gap: 20px;
  width: 100%;
}

.ai-icon-wrap {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: #2563eb;
  color: #ffffff;
  flex-shrink: 0;
  box-shadow: 0 0 28px rgba(37, 99, 235, 0.3);
}

.ai-content {
  flex: 1;
  min-width: 0;
}

.ai-content h3 {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 20px;
  font-weight: 700;
}

.ai-content p {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 14px;
}

.ai-stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  color: #94a3b8;
  font-size: 13px;
}

.ai-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.ai-stat-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.ai-stat-item strong {
  color: #0f172a;
}

.ai-stat-divider {
  color: #cbd5e1;
}

.ai-arrow {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border-radius: 10px;
  color: #ffffff;
  background: #2563eb;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

/* ===== 操作卡片 ===== */
.action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.action-card {
  display: block;
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.action-card:hover {
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  transform: translateY(-2px);
}

.action-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  margin-bottom: 14px;
}

.action-card h3 {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 16px;
  font-weight: 600;
}

.action-card p {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.5;
}

/* ===== 区域标题 ===== */
.section {
  margin-bottom: 24px;
}

.section-title {
  margin: 0 0 14px;
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
}

/* ===== 主简历卡片 ===== */
.master-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: box-shadow 0.2s;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.master-card:hover {
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}

.master-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.master-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  color: #ffffff;
  background: #2563eb;
  font-size: 18px;
  font-weight: 700;
}

.master-left h3 {
  margin: 0 0 4px;
  color: #0f172a;
  font-size: 16px;
}

.master-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ===== 定制简历网格 ===== */
.project-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.project-card {
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.project-card:hover {
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  transform: translateY(-2px);
}

.project-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.project-avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 700;
}

.project-badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.badge-ready {
  color: #059669;
  background: #d1fae5;
}

.badge-processing {
  color: #2563eb;
  background: #dbeafe;
}

.project-title {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.project-snippet {
  margin: 0 0 8px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.5;
}

.project-date {
  margin: 0;
  color: #cbd5e1;
  font-size: 12px;
}

/* ===== 空状态 ===== */
.empty-state {
  padding: 80px 40px;
  text-align: center;
  border: 2px dashed #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
}

.empty-icon {
  margin-bottom: 20px;
}

.empty-state h3 {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 20px;
}

.empty-state p {
  margin: 0 0 28px;
  color: #94a3b8;
  font-size: 15px;
}

.empty-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

/* ===== 按钮 ===== */
.btn-primary {
  display: inline-flex;
  align-items: center;
  padding: 10px 24px;
  border-radius: 10px;
  color: #ffffff;
  background: #2563eb;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: background 0.2s;
}

.btn-primary:hover {
  background: #1d4ed8;
}

.btn-ghost {
  display: inline-flex;
  align-items: center;
  padding: 10px 24px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  color: #334155;
  background: #ffffff;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-ghost:hover {
  background: #f8fafc;
}
</style>
