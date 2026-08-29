<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { deletePaperBank, listPaperBanks, listPaperDictionaries } from '../../api/paper'
import PaperPageShell from './PaperPageShell.vue'

const router = useRouter()
const banks = ref([])
const subjects = ref([])
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [bankList, subjectList] = await Promise.all([
      listPaperBanks({ visibility: 'private', keyword: keyword.value }),
      listPaperDictionaries('subject'),
    ])
    banks.value = bankList || []
    subjects.value = subjectList || []
  } finally {
    loading.value = false
  }
}

function subjectName(id) {
  const item = subjects.value.find((subject) => Number(subject.dictCode) === Number(id))
  return item ? item.name : '未设置科目'
}

function permissionText(visibility) {
  return visibility === 'shared' ? '题库成员可见' : '仅自己可见'
}

function edit(id) {
  router.push({ path: '/paper/banks/edit', query: id ? { id } : undefined })
}

function detail(id) {
  router.push(`/paper/banks/${id}`)
}

async function remove(bank) {
  if (!window.confirm('删除后题库组无法恢复，确认删除？')) return
  await deletePaperBank(bank.id)
  banks.value = banks.value.filter((item) => item.id !== bank.id)
}

onMounted(load)
</script>

<template>
  <PaperPageShell title="我的题库组" back-to="/paper/select">
    <div class="toolbar">
      <input v-model="keyword" placeholder="搜索题库组" @keyup.enter="load" />
      <button class="paper-btn paper-btn--primary" type="button" @click="edit()">新建</button>
    </div>

    <div v-if="loading" class="paper-state">加载中…</div>
    <div v-else-if="!banks.length" class="paper-empty">暂无题库组</div>

    <article v-for="bank in banks" :key="bank.id" class="paper-card">
      <div class="body" @click="detail(bank.id)">
        <strong>{{ bank.name }}</strong>
        <span>{{ subjectName(bank.subjectId) }} · {{ bank.bankType || '自定义' }}</span>
        <span>{{ bank.questionCount || 0 }} 道题 · {{ permissionText(bank.visibility) }}</span>
        <small>更新：{{ bank.updateTime || '—' }}</small>
      </div>
      <div class="actions">
        <button type="button" @click="edit(bank.id)">编辑</button>
        <button class="danger" type="button" @click="remove(bank)">删除</button>
      </div>
    </article>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.toolbar input {
  flex: 1;
  padding: 12px 14px;
  border: 1px solid #e0e6ed;
  border-radius: 999px;
  background: #fff;
}

.body {
  display: grid;
  gap: 6px;
  cursor: pointer;
}

.body span,
.body small {
  color: #8c98aa;
  font-size: 13px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #edf0f5;
}

.actions button {
  color: #4d78e8;
}

.danger {
  color: #df6565 !important;
}
</style>
