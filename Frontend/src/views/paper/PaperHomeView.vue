<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { deletePaper, listPapers } from '../../api/paper'
import PaperPageShell from './PaperPageShell.vue'

const router = useRouter()
const papers = ref([])
const loading = ref(false)
const loadError = ref('')
const deletingPaperId = ref(null)

const sources = [
  { key: 'public', icon: '🌐', name: '共有题库', desc: '共同维护' },
  { key: 'private', icon: '📚', name: '私有题库', desc: '我的题库' },
  { key: 'favorite', icon: '★', name: '收藏夹', desc: '快速选题' },
]

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    papers.value = await listPapers({ status: 'draft' }) || []
  } catch (cause) {
    loadError.value = cause.message || '试卷数据加载失败，请检查后端服务'
  } finally {
    loading.value = false
  }
}

function createPaper() {
  router.push('/paper/info')
}

function goMine() {
  router.push('/paper/mine')
}

function chooseSource(source) {
  const paper = papers.value[0]
  router.push({
    path: '/paper/select',
    query: {
      ...(paper ? { paperId: paper.id } : {}),
      source,
    },
  })
}

function openPaper(paper) {
  router.push({ path: '/paper/select', query: { paperId: paper.id, source: 'public' } })
}

async function confirmDelete(paper) {
  if (deletingPaperId.value !== null) return
  if (!window.confirm('删除后该草稿无法恢复，确认删除？')) return
  deletingPaperId.value = paper.id
  try {
    await deletePaper(paper.id)
    papers.value = papers.value.filter((item) => item.id !== paper.id)
  } catch (cause) {
    window.alert(cause.message || '删除失败')
  } finally {
    deletingPaperId.value = null
  }
}

onMounted(load)
</script>

<template>
  <PaperPageShell title="试卷生成" subtitle="创建、选题并导出试卷" back-to="/ai-tools">
    <section class="paper-grid-2">
      <button class="feature-card feature-card--primary" type="button" @click="createPaper">
        <span class="feature-icon">＋</span>
        <strong>创建新试卷</strong>
        <span>填写基本信息后选择题目</span>
      </button>
      <button class="feature-card" type="button" @click="goMine">
        <span class="feature-icon">📄</span>
        <strong>我的试卷</strong>
        <span>查看自己创建的试卷</span>
      </button>
    </section>

    <h2 class="section-title">选题来源</h2>
    <section class="paper-grid-3">
      <button v-for="item in sources" :key="item.key" class="source-card" type="button" @click="chooseSource(item.key)">
        <span class="source-icon">{{ item.icon }}</span>
        <strong>{{ item.name }}</strong>
        <span>{{ item.desc }}</span>
      </button>
    </section>

    <div class="section-head">
      <h2 class="section-title">最近编辑</h2>
      <button class="paper-link" type="button" @click="goMine">我的试卷 ›</button>
    </div>

    <div v-if="loading" class="paper-state">正在加载…</div>
    <div v-else-if="loadError" class="paper-empty paper-empty--error" @click="load">{{ loadError }}，点击重新加载</div>
    <div v-else-if="!papers.length" class="paper-empty">
      <strong>还没有试卷</strong>
      <span>点击上方创建你的第一份试卷</span>
    </div>
    <article v-for="paper in papers" :key="paper.id" class="paper-card draft-card">
      <div class="draft-card__main" @click="openPaper(paper)">
        <strong>{{ paper.name }}</strong>
        <span>{{ paper.subject }} · {{ paper.questionCount || 0 }}题 · {{ paper.totalScore || 0 }}分</span>
      </div>
      <div class="draft-card__actions">
        <span class="status">草稿</span>
        <button type="button" :disabled="deletingPaperId === paper.id" @click.stop="confirmDelete(paper)">
          {{ deletingPaperId === paper.id ? '删除中' : '删除' }}
        </button>
      </div>
    </article>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.feature-card {
  display: grid;
  gap: 8px;
  padding: 22px;
  border: 1px solid #e4e8ed;
  border-radius: 12px;
  background: #fff;
  text-align: left;
}

.feature-card--primary {
  color: #fff;
  background: linear-gradient(135deg, #6395f1, #4d72df);
  border-color: transparent;
}

.feature-card strong {
  font-size: 17px;
}

.feature-card span:last-child {
  color: #8f9bad;
  font-size: 13px;
}

.feature-card--primary span:last-child {
  color: rgba(255, 255, 255, 0.85);
}

.feature-icon {
  font-size: 24px;
}

.section-title {
  margin: 24px 0 12px;
  font-size: 17px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.source-card {
  display: grid;
  gap: 8px;
  padding: 18px 14px;
  border: 1px solid #e4e8ed;
  border-radius: 12px;
  background: #fff;
  text-align: center;
}

.source-icon {
  font-size: 28px;
}

.source-card strong {
  font-size: 15px;
}

.source-card span:last-child {
  color: #9aa6b8;
  font-size: 12px;
}

.draft-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
}

.draft-card__main {
  display: grid;
  gap: 6px;
}

.draft-card__main span {
  color: #98a4b6;
  font-size: 13px;
}

.draft-card__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status {
  color: #5c82ee;
  font-size: 13px;
}

.draft-card__actions button {
  color: #df6565;
  font-size: 13px;
}

.paper-empty {
  display: grid;
  gap: 8px;
}
</style>
