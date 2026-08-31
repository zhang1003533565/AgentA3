<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getPaperLayout, updatePaperLayout } from '../../api/paper'
import PaperPageShell from './PaperPageShell.vue'

const route = useRoute()
const router = useRouter()

const paperId = ref(route.query.paperId)
const loading = ref(true)
const saving = ref(false)
const templates = ['标准模板', '简洁模板']
const marginItems = [
  { key: 'marginTop', label: '上' },
  { key: 'marginBottom', label: '下' },
  { key: 'marginLeft', label: '左' },
  { key: 'marginRight', label: '右' },
]
const studentFieldOptions = [
  { key: 'school', label: '学校' },
  { key: 'grade', label: '年级' },
  { key: 'class', label: '班级' },
  { key: 'name', label: '姓名' },
  { key: 'studentNo', label: '学号' },
]
const form = ref({})

function normalize(value) {
  const fields = value.studentFields
    ? String(value.studentFields).split(',').filter(Boolean)
    : ['school', 'grade', 'class', 'name', 'studentNo'].filter((key) => value[`show${key === 'studentNo' ? 'StudentNo' : key.charAt(0).toUpperCase() + key.slice(1)}`] !== false)
  return {
    templateName: value.templateName || '标准模板',
    paperSize: value.paperSize,
    orientation: value.orientation,
    columnsCount: Number(value.columnsCount),
    columnGap: Number(value.columnGap),
    bindingLine: Boolean(value.bindingLine),
    bindingPosition: value.bindingPosition,
    marginTop: Number(value.marginTop),
    marginBottom: Number(value.marginBottom),
    marginLeft: Number(value.marginLeft),
    marginRight: Number(value.marginRight),
    marginPreset: value.marginPreset || '标准装订线',
    showStudentInfo: value.showStudentInfo !== false,
    studentFields: fields,
    titleFontSize: Number(value.titleFontSize),
    subtitleFontSize: Number(value.subtitleFontSize),
    bodyFontSize: Number(value.bodyFontSize),
  }
}

async function load(defaults = false, templateName = '') {
  loading.value = true
  try {
    form.value = normalize(await getPaperLayout(paperId.value, defaults, templateName) || {})
  } finally {
    loading.value = false
  }
}

function chooseTemplate(name) {
  if (name !== form.value.templateName) load(true, name)
}

function applyMarginPreset(name) {
  form.value.marginPreset = name
  if (name === '标准装订线') {
    Object.assign(form.value, { marginTop: 2.54, marginBottom: 2.54, marginLeft: 2.8, marginRight: 2 })
  }
}

function toggleStudentField(key) {
  const fields = [...form.value.studentFields]
  const index = fields.indexOf(key)
  if (index >= 0) fields.splice(index, 1)
  else fields.push(key)
  form.value.studentFields = fields
}

async function save() {
  saving.value = true
  try {
    const payload = normalize(form.value)
    payload.studentFields = form.value.studentFields.join(',')
    payload.showSchool = form.value.studentFields.includes('school')
    payload.showGrade = form.value.studentFields.includes('grade')
    payload.showClass = form.value.studentFields.includes('class')
    payload.showName = form.value.studentFields.includes('name')
    payload.showStudentNo = form.value.studentFields.includes('studentNo')
    await updatePaperLayout(paperId.value, payload)
    await load()
    router.replace({ path: '/paper/print', query: { paperId: paperId.value } })
  } catch (cause) {
    window.alert(cause.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (!paperId.value) {
    window.alert('缺少试卷 ID')
    return
  }
  load()
})
</script>

<template>
  <PaperPageShell title="页面格式" back-to="/paper/preview" :show-steps="true" :step="2">
    <div v-if="loading" class="paper-state">正在读取版式配置…</div>
    <template v-else>
      <section class="paper-card">
        <h3>版式模板</h3>
        <div class="template-list">
          <button
            v-for="name in templates"
            :key="name"
            type="button"
            class="template"
            :class="{ active: form.templateName === name }"
            @click="chooseTemplate(name)"
          >
            <strong>{{ name }}</strong>
            <span>{{ name === '标准模板' ? 'A3 · 横向 · 双栏' : 'A4 · 纵向 · 单栏' }}</span>
          </button>
        </div>
      </section>

      <section class="paper-card">
        <h3>纸张与排版</h3>
        <div v-for="row in [
          { label: '纸张', key: 'paperSize', options: ['A3', 'A4'] },
          { label: '方向', key: 'orientation', options: [{ v: 'landscape', l: '横向' }, { v: 'portrait', l: '纵向' }] },
          { label: '栏数', key: 'columnsCount', options: [{ v: 1, l: '单栏' }, { v: 2, l: '双栏' }] },
        ]" :key="row.label" class="setting-row">
          <span>{{ row.label }}</span>
          <div class="choices">
            <button
              v-for="option in row.options"
              :key="typeof option === 'string' ? option : option.v"
              type="button"
              class="choice"
              :class="{ active: form[row.key] === (typeof option === 'string' ? option : option.v) }"
              @click="form[row.key] = typeof option === 'string' ? option : option.v"
            >
              {{ typeof option === 'string' ? option : option.l }}
            </button>
          </div>
        </div>
        <div class="setting-row">
          <span>栏距</span>
          <label class="number-field"><input v-model.number="form.columnGap" type="number" step="0.1" /><span>cm</span></label>
        </div>
        <div class="setting-row">
          <span>页边距</span>
          <div class="choices">
            <button type="button" class="choice" :class="{ active: form.marginPreset === '标准装订线' }" @click="applyMarginPreset('标准装订线')">标准装订线</button>
            <button type="button" class="choice" :class="{ active: form.marginPreset === '自定义' }" @click="form.marginPreset = '自定义'">自定义</button>
          </div>
        </div>
        <div v-if="form.marginPreset === '自定义'" class="margin-grid">
          <label v-for="item in marginItems" :key="item.key" class="margin-item">
            <span>{{ item.label }}</span>
            <input v-model.number="form[item.key]" type="number" step="0.1" />
            <span>cm</span>
          </label>
        </div>
      </section>

      <section class="paper-card">
        <h3>装订线</h3>
        <label class="setting-row"><span>显示装订线</span><input v-model="form.bindingLine" type="checkbox" /></label>
        <div v-if="form.bindingLine" class="setting-row">
          <span>位置</span>
          <div class="choices">
            <button type="button" class="choice" :class="{ active: form.bindingPosition === 'left' }" @click="form.bindingPosition = 'left'">左</button>
            <button type="button" class="choice" :class="{ active: form.bindingPosition === 'right' }" @click="form.bindingPosition = 'right'">右</button>
          </div>
        </div>
      </section>

      <section class="paper-card">
        <h3>学生信息栏</h3>
        <label class="setting-row"><span>显示学生信息栏</span><input v-model="form.showStudentInfo" type="checkbox" /></label>
        <div v-if="form.showStudentInfo" class="field-list">
          <label v-for="field in studentFieldOptions" :key="field.key" class="field-item">
            <input type="checkbox" :checked="form.studentFields.includes(field.key)" @change="toggleStudentField(field.key)" />
            <span>{{ field.label }}</span>
          </label>
        </div>
      </section>

      <section class="paper-card">
        <h3>字号</h3>
        <label v-for="item in [
          { key: 'titleFontSize', label: '标题字号' },
          { key: 'subtitleFontSize', label: '副标题字号' },
          { key: 'bodyFontSize', label: '正文字号' },
        ]" :key="item.key" class="setting-row">
          <span>{{ item.label }}</span>
          <label class="number-field"><input v-model.number="form[item.key]" type="number" /><span>pt</span></label>
        </label>
      </section>
    </template>

    <footer class="paper-bottom">
      <button class="paper-btn paper-btn--secondary" type="button" @click="router.back()">上一步</button>
      <button class="paper-btn paper-btn--ghost" type="button" :disabled="loading || saving" @click="load(true, form.templateName)">恢复默认</button>
      <button class="paper-btn paper-btn--primary" type="button" :disabled="loading || saving" @click="save">
        {{ saving ? '保存中…' : '生成真实预览' }}
      </button>
    </footer>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.template-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.template {
  padding: 14px;
  border: 2px solid #d8e3f2;
  border-radius: 10px;
  background: #fff;
  text-align: left;
}

.template.active {
  border-color: #1e6bb8;
  background: #f3f8fc;
}

.template span {
  display: block;
  margin-top: 6px;
  color: #8493a8;
  font-size: 12px;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  border-bottom: 1px solid #edf1f6;
}

.choices {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.choice {
  padding: 8px 14px;
  border: 1px solid #c9d8ef;
  border-radius: 8px;
  color: #5f6e84;
  background: #fff;
}

.choice.active {
  color: #fff;
  background: #1e6bb8;
  border-color: #1e6bb8;
}

.number-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.number-field input {
  width: 72px;
  padding: 8px;
  border: 1px solid #c9d8ef;
  border-radius: 8px;
  text-align: center;
}

.margin-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 12px 0;
}

.field-list {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  padding: 8px 0 12px;
}

.field-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
