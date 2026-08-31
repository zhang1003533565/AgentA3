<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  createPaper,
  createPaperDictionary,
  deletePaperDictionary,
  getPaper,
  listPaperDictionaries,
  updatePaper,
} from '../../api/paper'
import PaperPageShell from './PaperPageShell.vue'

const route = useRoute()
const router = useRouter()

const paperId = ref(route.query.paperId || null)
const source = ref(route.query.source || 'public')
const saving = ref(false)
const manageType = ref('')
const selectorType = ref('')
const dictionariesLoading = ref(false)
const dictionariesError = ref('')
const subjects = ref([])
const categories = ref([])

const form = ref({
  name: '',
  subjectId: null,
  subject: '',
  category: '',
  duration: '',
  remark: '',
})

const managedItems = computed(() => (manageType.value === 'subject' ? subjects.value : categories.value))
const selectorItems = computed(() => (selectorType.value === 'subject' ? subjects.value : categories.value))

async function loadDictionaries() {
  dictionariesLoading.value = true
  dictionariesError.value = ''
  try {
    const [subjectList, categoryList] = await Promise.all([
      listPaperDictionaries('subject'),
      listPaperDictionaries('paper_category'),
    ])
    subjects.value = subjectList || []
    categories.value = categoryList || []
  } catch (cause) {
    dictionariesError.value = cause.message || '科目与分类加载失败'
  } finally {
    dictionariesLoading.value = false
  }
}

async function loadPaper() {
  if (!paperId.value) return
  const data = await getPaper(paperId.value)
  form.value = {
    name: data.name,
    subjectId: data.subjectId,
    subject: data.subject,
    category: data.category,
    duration: data.duration || '',
    remark: data.remark || '',
  }
}

function openSelector(type) {
  if (dictionariesLoading.value) return
  if (dictionariesError.value) {
    if (window.confirm(`${dictionariesError.value}\n是否重新加载？`)) loadDictionaries()
    return
  }
  selectorType.value = type
}

function isSelected(item) {
  return selectorType.value === 'subject'
    ? form.value.subjectId === item.id
    : form.value.category === item.name
}

function selectDictionary(item) {
  if (selectorType.value === 'subject') {
    form.value.subjectId = item.id
    form.value.subject = item.name
  } else {
    form.value.category = item.name
  }
  selectorType.value = ''
}

function createDictionary(type) {
  const label = type === 'subject' ? '科目' : '分类'
  const name = window.prompt(`请输入${label}名称`)
  if (!name?.trim()) return
  createPaperDictionary({ type, name: name.trim() })
    .then(async (result) => {
      const created = result.data || result
      await loadDictionaries()
      if (type === 'subject') {
        form.value.subjectId = created.id
        form.value.subject = created.name
      } else {
        form.value.category = created.name
      }
    })
    .catch((cause) => window.alert(cause.message || `新增${label}失败`))
}

function removeDictionary(item) {
  if (!window.confirm(`确定删除“${item.name}”吗？`)) return
  deletePaperDictionary(item.id)
    .then(async () => {
      if (manageType.value === 'subject' && form.value.subjectId === item.id) {
        form.value.subjectId = null
        form.value.subject = ''
      }
      if (manageType.value === 'paper_category' && form.value.category === item.name) {
        form.value.category = ''
      }
      await loadDictionaries()
    })
    .catch((cause) => window.alert(cause.message || '删除失败'))
}

async function nextStep() {
  if (!form.value.name.trim() || !form.value.subject.trim() || !form.value.category) {
    window.alert('请填写试卷名称、科目和分类')
    return
  }
  saving.value = true
  try {
    const result = paperId.value
      ? await updatePaper(paperId.value, form.value)
      : await createPaper(form.value)
    paperId.value = result.id
    router.push({ path: '/paper/select', query: { paperId: paperId.value, source: source.value } })
  } catch (cause) {
    window.alert(cause.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadDictionaries()
  if (paperId.value) await loadPaper()
})
</script>

<template>
  <PaperPageShell title="试卷基本信息" back-to="/paper" :show-steps="true" :step="1">
    <div class="paper-field">
      <label>试卷名称</label>
      <input v-model="form.name" maxlength="60" placeholder="例如：Python期末试卷" />
    </div>
    <div class="paper-field">
      <label>所属科目</label>
      <button class="paper-picker" :class="{ 'paper-picker--placeholder': !form.subject }" type="button" @click="openSelector('subject')">
        <span>{{ form.subject || '请选择科目' }}</span>
        <span>›</span>
      </button>
      <button class="paper-link" type="button" @click="createDictionary('subject')">＋ 新建科目</button>
      <button class="paper-link" type="button" @click="manageType = 'subject'">管理</button>
    </div>
    <div class="paper-field">
      <label>试卷分类</label>
      <button class="paper-picker" :class="{ 'paper-picker--placeholder': !form.category }" type="button" @click="openSelector('paper_category')">
        <span>{{ form.category || '请选择分类' }}</span>
        <span>›</span>
      </button>
      <button class="paper-link" type="button" @click="createDictionary('paper_category')">＋ 新建分类</button>
      <button class="paper-link" type="button" @click="manageType = 'paper_category'">管理</button>
    </div>
    <div class="paper-field">
      <label>考试时长（分钟）</label>
      <input v-model.number="form.duration" type="number" placeholder="可选" />
    </div>
    <div class="paper-field">
      <label>试卷备注</label>
      <textarea v-model="form.remark" placeholder="填写试卷说明（可选）" />
    </div>
    <button class="paper-btn paper-btn--primary submit" type="button" :disabled="saving" @click="nextStep">
      {{ saving ? '保存中…' : '下一步：选择试题' }}
    </button>

    <div v-if="selectorType" class="paper-mask" @click.self="selectorType = ''">
      <div class="paper-modal">
        <div class="paper-modal__head">
          <h2>选择{{ selectorType === 'subject' ? '科目' : '试卷分类' }}</h2>
          <button class="paper-modal__close" type="button" @click="selectorType = ''">×</button>
        </div>
        <p v-if="!selectorItems.length" class="paper-empty">暂无可选项，请先新建</p>
        <button
          v-for="item in selectorItems"
          :key="item.id"
          class="selector-row"
          :class="{ selected: isSelected(item) }"
          type="button"
          @click="selectDictionary(item)"
        >
          <span>{{ item.name }}</span>
          <span v-if="isSelected(item)">✓</span>
        </button>
        <button class="paper-btn paper-btn--ghost" type="button" @click="createDictionary(selectorType)">
          ＋ 新建{{ selectorType === 'subject' ? '科目' : '分类' }}
        </button>
      </div>
    </div>

    <div v-if="manageType" class="paper-mask" @click.self="manageType = ''">
      <div class="paper-modal">
        <div class="paper-modal__head">
          <h2>{{ manageType === 'subject' ? '管理科目' : '管理试卷分类' }}</h2>
          <button class="paper-modal__close" type="button" @click="manageType = ''">×</button>
        </div>
        <div v-for="item in managedItems" :key="item.id" class="manager-row">
          <span>{{ item.name }}</span>
          <span v-if="item.creatorId == null" class="system-tag">系统</span>
          <button v-else class="paper-link" type="button" @click="removeDictionary(item)">删除</button>
        </div>
      </div>
    </div>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.submit {
  width: 100%;
  min-height: 46px;
  margin-top: 8px;
}

.selector-row,
.manager-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 14px 4px;
  border-bottom: 1px solid #edf0f5;
  background: transparent;
  text-align: left;
}

.selector-row.selected {
  color: #1e6bb8;
  font-weight: 600;
}

.system-tag {
  color: #9aa5b8;
  font-size: 12px;
}
</style>
