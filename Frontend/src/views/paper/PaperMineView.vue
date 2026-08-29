<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  copyPaper,
  deletePaper,
  listPapers,
} from '../../api/paper'
import PaperPageShell from './PaperPageShell.vue'

const route = useRoute()
const router = useRouter()

const papers = ref([])
const status = ref('')
const keyword = ref('')
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    papers.value = await listPapers({ status: status.value, keyword: keyword.value }) || []
  } catch (cause) {
    loadError.value = cause.message || '试卷数据加载失败'
  } finally {
    loading.value = false
  }
}

function openPaper(paper) {
  if (paper.status === 'draft') {
    router.push({ path: '/paper/select', query: { paperId: paper.id, source: 'public' } })
  } else {
    router.push({ path: '/paper/preview', query: { paperId: paper.id } })
  }
}

async function copyDraft(paper) {
  try {
    const copied = await copyPaper(paper.id)
    openPaper(copied)
  } catch (cause) {
    window.alert(cause.message || '复制失败')
  }
}

async function removePaper(paper) {
  if (!window.confirm(`确认删除“${paper.name}”？`)) return
  try {
    await deletePaper(paper.id)
    await load()
  } catch (cause) {
    window.alert(cause.message || '删除失败')
  }
}

const emptyText = computed(() => (loadError.value ? loadError.value : '没有符合条件的试卷'))

onMounted(load)
</script>

<template>
  <PaperPageShell title="我的试卷" subtitle="草稿与已完成试卷" back-to="/paper">
    <div class="toolbar">
      <input v-model="keyword" placeholder="搜索试卷" @keyup.enter="load" />
      <div class="tabs">
        <button type="button" :class="{ active: !status }" @click="status = ''; load()">全部</button>
        <button type="button" :class="{ active: status === 'draft' }" @click="status = 'draft'; load()">草稿</button>
        <button type="button" :class="{ active: status === 'completed' }" @click="status = 'completed'; load()">已完成</button>
      </div>
    </div>

    <div v-if="loading" class="paper-state">正在加载…</div>
    <div v-else-if="loadError" class="paper-empty paper-empty--error" @click="load">{{ emptyText }}，点击重新加载</div>
    <div v-else-if="!papers.length" class="paper-empty">{{ emptyText }}</div>

    <article v-for="paper in papers" :key="paper.id" class="paper-card list-card">
      <div class="list-card__body" @click="openPaper(paper)">
        <div class="list-card__top">
          <strong>{{ paper.name }}</strong>
          <span :class="['badge', paper.status]">{{ paper.status === 'draft' ? '草稿' : '已完成' }}</span>
        </div>
        <span>{{ paper.subject }} · {{ paper.category }} · {{ paper.questionCount }}题 · {{ paper.totalScore }}分</span>
        <small>更新于 {{ paper.updateTime || '—' }}</small>
      </div>
      <div class="list-card__actions">
        <button type="button" @click="openPaper(paper)">{{ paper.status === 'draft' ? '继续编辑' : '预览' }}</button>
        <button type="button" @click="copyDraft(paper)">复制</button>
        <button class="danger" type="button" @click="removePaper(paper)">删除</button>
      </div>
    </article>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.toolbar {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar input {
  padding: 12px 14px;
  border: 1px solid #e0e6ed;
  border-radius: 999px;
  background: #fff;
}

.tabs {
  display: flex;
  gap: 18px;
}

.tabs button {
  padding-bottom: 8px;
  color: #818da0;
  background: transparent;
  border-bottom: 2px solid transparent;
}

.tabs button.active {
  color: #4d78e8;
  border-bottom-color: #4d78e8;
  font-weight: 700;
}

.list-card__body {
  display: grid;
  gap: 8px;
  cursor: pointer;
}

.list-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.badge.draft {
  color: #c48920;
  background: #fff4d9;
}

.badge.completed {
  color: #31905a;
  background: #e7f7ed;
}

.list-card__body span,
.list-card__body small {
  color: #8f9bad;
  font-size: 13px;
}

.list-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #edf0f5;
}

.list-card__actions button {
  color: #4d78e8;
  font-size: 13px;
}

.list-card__actions .danger {
  color: #df6565;
}
</style>
