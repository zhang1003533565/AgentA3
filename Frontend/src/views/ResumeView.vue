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

// ===== 新建分组弹窗状态 =====
const showGroupModal = ref(false)
const newGroupName = ref('')
const groupNameError = ref('')
const MAX_GROUP_NAME_LENGTH = 20

function openGroupModal() {
  newGroupName.value = ''
  groupNameError.value = ''
  showGroupModal.value = true
}

function closeGroupModal() {
  showGroupModal.value = false
  newGroupName.value = ''
  groupNameError.value = ''
}

function handleCreateGroup() {
  const name = newGroupName.value.trim()

  if (!name) {
    groupNameError.value = '分组名称不能为空'
    return
  }

  if (name.length > MAX_GROUP_NAME_LENGTH) {
    groupNameError.value = `分组名称不得超过${MAX_GROUP_NAME_LENGTH}个字符`
    return
  }

  const newGroup = {
    groupId: `group-${Date.now()}`,
    groupName: name,
    count: 0,
    resumes: [],
  }

  resumeGroups.value.push(newGroup)
  closeGroupModal()
}

function handleGroupInput() {
  if (groupNameError.value) {
    groupNameError.value = ''
  }
}

// ===== 简历分组 Mock 数据（仅用于 UI 展示）=====
const resumeGroups = ref([
  {
    groupId: 'group-java',
    groupName: 'Java工程师简历',
    count: 2,
    resumes: [
      {
        id: 'java-1',
        title: 'Java高级开发_张三',
        snippet: '8年Java后端开发经验，熟悉Spring Cloud、微服务架构与中间件优化',
        initial: 'J',
        time: '2023-10-24',
        score: 85,
      },
      {
        id: 'java-2',
        title: '互联网大厂专用版',
        snippet: '针对互联网大厂JD定制，突出高并发与分布式系统设计能力',
        initial: 'I',
        time: '2023-10-20',
        score: 92,
      },
    ],
  },
  {
    groupId: 'group-cpp',
    groupName: 'C++工程师简历',
    count: 1,
    resumes: [
      {
        id: 'cpp-1',
        title: 'C++系统开发_李四',
        snippet: '5年C++系统开发经验，擅长高性能计算、网络编程与跨平台开发',
        initial: 'C',
        time: '2023-10-18',
        score: 88,
      },
    ],
  },
])

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

// ===== 简历分组计算属性 =====
const groupedResumes = computed(() => {
  // 优先使用 Mock 分组数据展示，原有数据逻辑保留
  if (resumeGroups.value && resumeGroups.value.length > 0) {
    return resumeGroups.value.map((group) => ({
      ...group,
      resumes: group.resumes.map((resume) => ({
        ...resume,
        time: resume.time || formatDate(stats.lastEditedDate),
      })),
    }))
  }

  const allResumes = []

  // 主简历放入第一组
  if (masterResume) {
    allResumes.push({
      id: 'master',
      title: masterResume.title || '主简历',
      snippet: masterResume.contentSnippet?.slice(0, 80) || '暂无内容',
      initial: 'M',
      time: formatDate(stats.lastEditedDate),
      groupId: 'group-master',
      groupName: '我的简历',
      score: 0,
    })
  }

  // 定制简历
  tailoredResumes.forEach((resume, index) => {
    allResumes.push({
      id: resume.id || `custom-${index}`,
      title: resume.title || '未命名简历',
      snippet: resume.jobSnippet?.slice(0, 80) || resume.contentSnippet?.slice(0, 80) || '暂无内容',
      initial: getMonogram(resume.title || '简历'),
      time: getRelativeTime(resume.updatedAt || stats.lastEditedDate),
      groupId: 'group-custom',
      groupName: '定制简历',
      score: resume.score || 0,
    })
  })

  // 按分组返回
  const result = []

  if (allResumes.filter((r) => r.groupId === 'group-master').length > 0) {
    result.push({
      groupId: 'group-master',
      groupName: '我的简历',
      count: allResumes.filter((r) => r.groupId === 'group-master').length,
      resumes: allResumes.filter((r) => r.groupId === 'group-master'),
    })
  }

  if (allResumes.filter((r) => r.groupId === 'group-custom').length > 0) {
    result.push({
      groupId: 'group-custom',
      groupName: '定制简历',
      count: allResumes.filter((r) => r.groupId === 'group-custom').length,
      resumes: allResumes.filter((r) => r.groupId === 'group-custom'),
    })
  }

  return result
})
</script>

<template>
  <div class="dashboard">
    <AppTabBar />
    <div class="container">
      <header class="page-header">
        <h1>我的简历工作台</h1>
      </header>

      <!-- AI 助手卡片 -->
      <div class="ai-assist-grid">
        <router-link to="/resume/wizard/edit" class="ai-card">
          <div class="ai-card-inner">
            <div class="ai-icon-wrap blue">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2Zm0 16a1 1 0 1 1 1-1 1 1 0 0 1-1 1Zm0-6a1 1 0 1 1 1-1 1 1 0 0 1-1 1Z"/><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            </div>
            <div class="ai-content">
              <h3>AI 一键改简历</h3>
              <p>上传现有简历，AI 自动优化内容与布局，智能适配目标岗位</p>
            </div>
            <div class="ai-arrow">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
            </div>
          </div>
        </router-link>

        <router-link to="/resume/wizard" class="ai-card">
          <div class="ai-card-inner">
            <div class="ai-icon-wrap green">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/><circle cx="12" cy="12" r="3"/></svg>
            </div>
            <div class="ai-content">
              <h3>AI 生成简历</h3>
              <p>通过 AI 对话智能生成简历内容，包含职业测评与岗位匹配</p>
            </div>
            <div class="ai-arrow">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
            </div>
          </div>
        </router-link>

        <router-link to="/resume/workspace" class="ai-card">
          <div class="ai-card-inner">
            <div class="ai-icon-wrap purple">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16v16H4z"/><path d="M8 8h8M8 12h8M8 16h5"/></svg>
            </div>
            <div class="ai-content">
              <h3>简历制作</h3>
              <p>使用开源 AIResume 编辑器，分区填写、拖拽排序并实时预览简历</p>
            </div>
            <div class="ai-arrow">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
            </div>
          </div>
        </router-link>

        <router-link :to="{ path: '/resume/workspace', query: { tab: 'templates' } }" class="ai-card">
          <div class="ai-card-inner">
            <div class="ai-icon-wrap amber">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/></svg>
            </div>
            <div class="ai-content">
              <h3>模板市场</h3>
              <p>浏览并使用开源项目保留的五套原始简历模板</p>
            </div>
            <div class="ai-arrow">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
            </div>
          </div>
        </router-link>
      </div>

      <!-- 简历分组管理 -->
      <div class="section group-section-wrapper">
        <div class="section-header">
          <h2 class="section-title">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
            简历分组管理
          </h2>
          <button class="btn-new-group" type="button" @click="openGroupModal">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            新建分组
          </button>
        </div>

        <!-- 分组列表 -->
        <div class="group-content">
          <div v-for="item in groupedResumes" :key="item.groupId" class="group-list">
            <h3 class="group-name">
              {{ item.groupName }}
              <span class="group-count">{{ item.count }} 份</span>
            </h3>
            <div class="project-grid">
              <div
                v-for="resume in item.resumes"
                :key="resume.id"
                class="project-card editable"
                @click="$router.push('/resume/designer')"
              >
                <div class="project-header">
                  <div
                    class="project-avatar"
                    :style="{
                      backgroundColor: cardColors[hashTitle(resume.title) % cardColors.length].bg,
                      color: cardColors[hashTitle(resume.title) % cardColors.length].fg,
                    }"
                  >
                    {{ resume.initial }}
                  </div>
                  <span class="project-badge badge-ready">已就绪</span>
                </div>
                <h3 class="project-title">{{ resume.title }}</h3>
                <p class="project-snippet">{{ resume.snippet }}</p>
                <div class="project-meta">
                  <span class="project-date">更新：{{ resume.time }}</span>
                  <div class="resume-score">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                    <span>{{ resume.score }}分</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 新建分组弹窗 -->
      <Teleport to="body">
        <Transition name="modal-fade">
          <div v-if="showGroupModal" class="modal-overlay" @click.self="closeGroupModal">
            <div class="modal-card">
              <div class="modal-header">
                <h3 class="modal-title">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
                  新建分组
                </h3>
                <button class="modal-close" type="button" aria-label="关闭" @click="closeGroupModal">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                </button>
              </div>

              <div class="modal-body">
                <label class="input-label" for="group-name">分组名称</label>
                <div class="input-wrapper" :class="{ 'input-wrapper--error': groupNameError }">
                  <input
                    id="group-name"
                    v-model="newGroupName"
                    type="text"
                    maxlength="20"
                    placeholder="请输入分组名称，例如：前端开发工程师"
                    @input="handleGroupInput"
                    @keydown.enter="handleCreateGroup"
                  />
                </div>
                <p v-if="groupNameError" class="input-error">{{ groupNameError }}</p>
                <p v-else class="input-hint">分组名称不得超过20个字符</p>
              </div>

              <div class="modal-footer">
                <button class="btn-cancel" type="button" @click="closeGroupModal">取消</button>
                <button class="btn-confirm" type="button" @click="handleCreateGroup">确认</button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: #f8fafc;
}

.container {
  display: flex;
  flex-direction: column;
  max-width: 1200px;
  min-height: calc(100vh - 60px);
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

/* ===== AI 助手区域 ===== */
.ai-assist-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.ai-card {
  display: flex;
  padding: 28px 32px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #ffffff;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s, border-color 0.2s;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}

.ai-card:hover {
  transform: translateY(-2px);
  border-color: #bfdbfe;
  box-shadow: 0 12px 32px rgba(37, 99, 235, 0.1);
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
  width: 60px;
  height: 60px;
  border-radius: 16px;
  color: #ffffff;
  flex-shrink: 0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.ai-icon-wrap.blue {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
}

.ai-icon-wrap.green {
  background: linear-gradient(135deg, #10b981, #34d399);
}

.ai-icon-wrap.purple {
  background: linear-gradient(135deg, #6d5bd0, #8b7de3);
}

.ai-icon-wrap.amber {
  background: linear-gradient(135deg, #d28a32, #e8a94f);
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
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  color: #2563eb;
  background: #eff6ff;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
  transition: background 0.2s, color 0.2s;
}

.ai-card:hover .ai-arrow {
  color: #ffffff;
  background: #2563eb;
}

/* ===== 简历分组管理 ===== */
.group-section-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 28px 32px;
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.06);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  font-weight: 700;
}

.section-title svg {
  color: #2563eb;
}

.btn-new-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  border: none;
  border-radius: 10px;
  color: #ffffff;
  background: #2563eb;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-new-group:hover {
  background: #1d4ed8;
}

.group-list {
  margin-bottom: 28px;
}

.group-list:last-child {
  margin-bottom: 0;
}

.group-name {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 16px;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.group-count {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 500;
}

.group-list + .group-list {
  padding-top: 24px;
  border-top: 1px dashed #e2e8f0;
}

.group-content {
  flex: 1;
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
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.project-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 22px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
  cursor: pointer;
  transition: box-shadow 0.25s, transform 0.25s, border-color 0.25s;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.05);
  overflow: hidden;
  min-height: 180px;
}

.project-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: linear-gradient(90deg, #2563eb, #60a5fa);
  opacity: 0;
  transition: opacity 0.25s;
}

.project-card:hover {
  border-color: #bfdbfe;
  box-shadow: 0 12px 32px rgba(37, 99, 235, 0.12);
  transform: translateY(-3px);
}

.project-card:hover::before {
  opacity: 1;
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
  margin: 0 0 16px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}

.project-date {
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
}

.project-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.resume-score {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: 8px;
  color: #f59e0b;
  background: #fffbeb;
  font-size: 12px;
  font-weight: 700;
}

.resume-score svg {
  fill: #fbbf24;
  stroke: #f59e0b;
}

.project-card.editable:hover {
  border-color: #bfdbfe;
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

/* ===== 新建分组弹窗 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(4px);
}

.modal-card {
  width: min(460px, calc(100% - 32px));
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 24px 56px rgba(15, 23, 42, 0.18);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22px 26px;
  border-bottom: 1px solid #f1f5f9;
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
}

.modal-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  font-weight: 700;
}

.modal-title svg {
  color: #2563eb;
}

.modal-close {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  color: #94a3b8;
  background: transparent;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
}

.modal-close:hover {
  color: #64748b;
  background: #f1f5f9;
}

.modal-body {
  padding: 26px;
}

.input-label {
  display: block;
  margin-bottom: 10px;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.input-wrapper {
  display: flex;
  align-items: center;
  padding: 2px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-wrapper:focus-within {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.08);
}

.input-wrapper--error {
  border-color: #fca5a5;
}

.input-wrapper--error:focus-within {
  border-color: #f87171;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.08);
}

.input-wrapper input {
  flex: 1;
  width: 100%;
  padding: 12px 14px;
  border: none;
  border-radius: 10px;
  color: #0f172a;
  background: transparent;
  font-size: 14px;
  outline: none;
}

.input-wrapper input::placeholder {
  color: #cbd5e1;
}

.input-hint {
  margin: 10px 0 0;
  color: #94a3b8;
  font-size: 12px;
}

.input-error {
  margin: 10px 0 0;
  color: #dc2626;
  font-size: 12px;
  font-weight: 500;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 18px 26px;
  border-top: 1px solid #f1f5f9;
  background: #fafbfc;
}

.btn-cancel {
  padding: 10px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  color: #64748b;
  background: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.btn-cancel:hover {
  color: #334155;
  background: #f8fafc;
}

.btn-confirm {
  padding: 10px 22px;
  border: none;
  border-radius: 10px;
  color: #ffffff;
  background: #2563eb;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, box-shadow 0.2s;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}

.btn-confirm:hover {
  background: #1d4ed8;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.32);
}

/* 弹窗过渡动画 */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.25s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
</style>
