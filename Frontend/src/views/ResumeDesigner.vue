<script setup>
import { ref, computed } from 'vue'
import AppTabBar from '../components/AppTabBar.vue'

const hasContent = computed(() => {
  const d = resumeData.value
  return d.personalInfo.name || d.summary || d.education.length || d.experience.length || d.skills.length || d.projects.length
})

const resumeData = ref({
  personalInfo: {
    name: '',
    title: '',
    email: '',
    phone: '',
    location: '',
  },
  summary: '',
  education: [],
  experience: [],
  skills: [],
  projects: [],
})

const isSaving = ref(false)

const handleSave = () => {
  isSaving.value = true
  setTimeout(() => { isSaving.value = false }, 800)
}
</script>

<template>
  <div class="designer">
    <AppTabBar />
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="toolbar-inner">
        <router-link to="/resume" class="toolbar-back">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
          返回
        </router-link>
        <h1>简历编辑器</h1>
        <div class="toolbar-actions">
          <button class="tool-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
            AI 优化
          </button>
          <button class="tool-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
            撤销
          </button>
          <button class="tool-btn save-btn" :class="{ saving: isSaving }" @click="handleSave">
            <svg v-if="!isSaving" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="spin-icon"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
            {{ isSaving ? '保存中' : '保存' }}
          </button>
          <button class="tool-btn primary">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
            下载 PDF
          </button>
        </div>
      </div>
    </div>

    <!-- 主体 -->
    <div class="designer-body">
      <div class="container">
        <!-- 空状态 -->
        <div v-if="!hasContent" class="empty-state">
          <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/></svg>
          <h3>还没有简历内容</h3>
          <p>上传简历或使用 AI 向导创建后，这里将显示编辑界面</p>
        </div>

        <!-- 简历预览 -->
        <div v-else class="resume-preview">
          <!-- 基本信息 -->
          <div class="preview-section">
            <div class="preview-name">{{ resumeData.personalInfo.name }}</div>
            <div class="preview-title">{{ resumeData.personalInfo.title }}</div>
            <div class="preview-contact">
              <span>{{ resumeData.personalInfo.email }}</span>
              <span class="sep">|</span>
              <span>{{ resumeData.personalInfo.phone }}</span>
              <span class="sep">|</span>
              <span>{{ resumeData.personalInfo.location }}</span>
            </div>
          </div>

          <!-- 个人总结 -->
          <div class="preview-section">
            <div class="section-head">
              <div class="section-bar"></div>
              个人总结
            </div>
            <p class="section-text">{{ resumeData.summary }}</p>
          </div>

          <!-- 教育经历 -->
          <div class="preview-section">
            <div class="section-head">
              <div class="section-bar"></div>
              教育经历
            </div>
            <div v-for="edu in resumeData.education" :key="edu.school" class="preview-item">
              <div class="item-dot"></div>
              <div>
                <div class="item-title">{{ edu.school }}</div>
                <div class="item-sub">{{ edu.major }} · {{ edu.degree }} · {{ edu.years }}</div>
              </div>
            </div>
          </div>

          <!-- 工作经历 -->
          <div class="preview-section">
            <div class="section-head">
              <div class="section-bar"></div>
              工作经历
            </div>
            <div v-for="exp in resumeData.experience" :key="exp.company" class="preview-item">
              <div class="item-dot"></div>
              <div>
                <div class="item-title">{{ exp.company }}</div>
                <div class="item-sub">{{ exp.title }} · {{ exp.years }}</div>
                <ul class="item-list">
                  <li v-for="h in exp.highlights" :key="h">{{ h }}</li>
                </ul>
              </div>
            </div>
          </div>

          <!-- 技能 -->
          <div class="preview-section">
            <div class="section-head">
              <div class="section-bar"></div>
              技能
            </div>
            <div class="tag-list">
              <span v-for="skill in resumeData.skills" :key="skill" class="tag">{{ skill }}</span>
            </div>
          </div>

          <!-- 项目经历 -->
          <div class="preview-section">
            <div class="section-head">
              <div class="section-bar"></div>
              项目经历
            </div>
            <div v-for="proj in resumeData.projects" :key="proj.name" class="preview-item">
              <div class="item-dot"></div>
              <div>
                <div class="item-title">{{ proj.name }}</div>
                <div class="item-sub">{{ proj.role }} · {{ proj.years }}</div>
                <p class="item-desc">{{ proj.description }}</p>
              </div>
            </div>
          </div>

          <!-- AI 标识 -->
          <div class="ai-row">
            <div class="ai-line"></div>
            <div class="ai-badge">
              <span class="ai-dot"></span>
              AI 已优化
            </div>
            <div class="ai-line"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 100px 40px;
  border: 2px dashed #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
  text-align: center;
}

.empty-state h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.empty-state p {
  margin: 0;
  color: #94a3b8;
  font-size: 14px;
}

.designer {
  min-height: 100vh;
  padding-top: 62px;
  background: #f8fafc;
}

/* ===== 工具栏 ===== */
.toolbar {
  position: sticky;
  top: 62px;
  z-index: 10;
  background: rgba(255, 255, 255, 0.95);
  border-bottom: 1px solid #e2e8f0;
  backdrop-filter: blur(12px);
}

.toolbar-inner {
  display: flex;
  align-items: center;
  gap: 16px;
  max-width: 900px;
  margin: 0 auto;
  padding: 12px 24px;
}

.toolbar-back {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #64748b;
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.toolbar-back:hover { color: #0f172a; }

.toolbar-inner h1 {
  flex: 1;
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.tool-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #334155;
  background: #ffffff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
}

.tool-btn:hover { background: #f8fafc; }

.tool-btn.primary {
  color: #ffffff;
  background: #2563eb;
  border-color: #2563eb;
}

.tool-btn.primary:hover { background: #1d4ed8; }

.tool-btn.saving {
  color: #2563eb;
  border-color: #bfdbfe;
  background: #eff6ff;
}

@keyframes spin { to { transform: rotate(360deg); } }

.spin-icon { animation: spin 1s linear infinite; }

/* ===== 主体 ===== */
.designer-body {
  padding: 24px;
}

.container {
  max-width: 820px;
  margin: 0 auto;
}

/* ===== 简历预览 ===== */
.resume-preview {
  padding: 48px 56px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
  font-size: 14px;
  line-height: 1.8;
}

.preview-section {
  margin-bottom: 28px;
}

.preview-name {
  color: #0f172a;
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 2px;
}

.preview-title {
  color: #64748b;
  font-size: 15px;
  margin-bottom: 8px;
}

.preview-contact {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #94a3b8;
  font-size: 13px;
}

.sep { color: #cbd5e1; }

.section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.section-bar {
  width: 3px;
  height: 18px;
  border-radius: 2px;
  background: #2563eb;
}

.section-text {
  margin: 0;
  padding-left: 13px;
  color: #475569;
  line-height: 1.8;
}

.preview-item {
  display: flex;
  gap: 10px;
  padding-left: 3px;
  margin-bottom: 12px;
}

.item-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
  margin-top: 9px;
  flex-shrink: 0;
  opacity: 0.5;
}

.item-title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
}

.item-sub {
  color: #64748b;
  font-size: 13px;
  margin-top: 2px;
}

.item-list {
  margin: 6px 0 0;
  padding-left: 18px;
  color: #475569;
  font-size: 13px;
  line-height: 1.8;
}

.item-list li { margin-bottom: 3px; }

.item-desc {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.8;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-left: 13px;
}

.tag {
  padding: 5px 14px;
  border-radius: 999px;
  color: #2563eb;
  background: #eff6ff;
  font-size: 13px;
  font-weight: 600;
}

.ai-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 32px;
  padding-top: 20px;
}

.ai-line {
  flex: 1;
  height: 1px;
  background: #e2e8f0;
}

.ai-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border-radius: 999px;
  color: #059669;
  background: #d1fae5;
  border: 1px solid #a7f3d0;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.ai-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #059669;
}
</style>
