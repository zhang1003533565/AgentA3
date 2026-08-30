<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  createPaperBank,
  getPaperBank,
  listPaperDictionaries,
  updatePaperBank,
} from '../../api/paper'
import PaperPageShell from './PaperPageShell.vue'

const route = useRoute()
const router = useRouter()

const id = ref(route.query.id || null)
const saving = ref(false)
const subjects = ref([])
const types = ref([])
const permissions = [
  { label: '仅自己可见', value: 'private' },
  { label: '题库成员可见', value: 'shared' },
]
const form = ref({
  name: '',
  subjectId: null,
  bankType: '',
  description: '',
  visibility: 'private',
})

const subjectNames = computed(() => subjects.value.map((subject) => subject.name))
const permissionLabel = computed(() => (
  permissions.find((item) => item.value === form.value.visibility)?.label || permissions[0].label
))

async function loadDictionaries() {
  const [subjectList, bankTypes] = await Promise.all([
    listPaperDictionaries('subject'),
    listPaperDictionaries('bank_type'),
  ])
  subjects.value = subjectList || []
  types.value = (bankTypes || []).map((item) => item.name)
}

async function loadBank() {
  if (!id.value) return
  form.value = { ...form.value, ...(await getPaperBank(id.value) || {}) }
}

async function save() {
  if (!form.value.name.trim()) {
    window.alert('请输入名称')
    return
  }
  saving.value = true
  try {
    if (id.value) await updatePaperBank(id.value, form.value)
    else await createPaperBank(form.value)
    router.back()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadDictionaries()
  await loadBank()
})
</script>

<template>
  <PaperPageShell :title="id ? '编辑题库组' : '新建题库组'" back-to="/paper/banks">
    <div class="paper-field">
      <label>名称</label>
      <input v-model="form.name" placeholder="请输入题库组名称" />
    </div>
    <div class="paper-field">
      <label>所属科目</label>
      <select v-model="form.subjectId">
        <option :value="null">请选择所属科目</option>
        <option v-for="(name, index) in subjectNames" :key="name" :value="subjects[index].dictCode">
          {{ name }}
        </option>
      </select>
    </div>
    <div class="paper-field">
      <label>类型</label>
      <select v-model="form.bankType">
        <option value="">请选择类型</option>
        <option v-for="type in types" :key="type" :value="type">{{ type }}</option>
      </select>
    </div>
    <div class="paper-field">
      <label>说明</label>
      <textarea v-model="form.description" placeholder="请输入题库组说明" />
    </div>
    <div class="paper-field">
      <label>权限（{{ permissionLabel }}）</label>
      <select v-model="form.visibility">
        <option v-for="item in permissions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
    </div>
    <button class="paper-btn paper-btn--primary save" type="button" :disabled="saving" @click="save">
      {{ saving ? '保存中…' : '保存' }}
    </button>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.save {
  width: 100%;
  margin-top: 8px;
}
</style>
