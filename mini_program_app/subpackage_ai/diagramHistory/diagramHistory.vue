<template>
  <view class="page">
    <nav-bar title="历史记录" :showBack="true" :border="false">
      <template #right>
        <view class="nav-right">
          <view class="nav-ico" @tap="toggleSearch"><svg class="ni" viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg></view>
        </view>
      </template>
    </nav-bar>

    <view v-if="searchOn" class="search-bar">
      <input class="search-input" v-model="keyword" placeholder="搜索标题" focus @input="applyFilter" />
    </view>

    <!-- 标签 -->
    <view class="tabs">
      <view class="tab" :class="{ on: tab === 'mindmap' }" @tap="setTab('mindmap')">
        <svg class="tab-ic" viewBox="0 0 24 24" fill="none" :stroke="tab==='mindmap'?'#fff':'#4D6BFE'" stroke-width="2"><circle cx="6" cy="6" r="2.5"/><circle cx="18" cy="6" r="2.5"/><circle cx="12" cy="18" r="2.5"/><path d="M7.5 7.5L11 16M16.5 7.5L13 16"/></svg>
        <text>思维导图</text>
      </view>
      <view class="tab" :class="{ on: tab === 'flow' }" @tap="setTab('flow')">
        <svg class="tab-ic" viewBox="0 0 24 24" fill="none" :stroke="tab==='flow'?'#fff':'#10B981'" stroke-width="2"><rect x="4" y="4" width="6" height="5" rx="1"/><rect x="14" y="15" width="6" height="5" rx="1"/><path d="M7 9v6h7"/></svg>
        <text>流程图</text>
      </view>
      <view class="tab" :class="{ on: tab === 'arch' }" @tap="setTab('arch')">
        <svg class="tab-ic" viewBox="0 0 24 24" fill="none" :stroke="tab==='arch'?'#fff':'#8B5CF6'" stroke-width="2"><rect x="4" y="4" width="16" height="5" rx="1"/><rect x="7" y="14" width="10" height="5" rx="1"/></svg>
        <text>架构图</text>
      </view>
    </view>

    <view class="count-row"><text>全部记录（{{ list.length }}）</text><text class="sort">按修改时间 ▾</text></view>

    <!-- 列表 -->
    <scroll-view class="list" scroll-y>
      <view v-for="item in list" :key="item.id" class="card" @tap="openSheet(item)">
        <view class="thumb">
          <svg v-if="item.type==='mindmap'" viewBox="0 0 118 88"><g stroke="#4D6BFE" fill="none" stroke-width="1"><path d="M59 44 L30 24M59 44 L30 64M59 44 L88 24M59 44 L88 64"/></g><rect x="50" y="37" width="18" height="14" rx="4" fill="#4D6BFE"/><rect x="20" y="18" width="16" height="10" rx="3" fill="#F59E0B" opacity=".7"/><rect x="20" y="60" width="16" height="10" rx="3" fill="#EC4899" opacity=".7"/><rect x="82" y="18" width="16" height="10" rx="3" fill="#10B981" opacity=".7"/><rect x="82" y="60" width="16" height="10" rx="3" fill="#8B5CF6" opacity=".7"/></svg>
          <svg v-else-if="item.type==='flow'" viewBox="0 0 118 88"><g stroke="#10B981" fill="none" stroke-width="1"><path d="M59 16v10M59 40v10M40 62h38"/></g><rect x="47" y="8" width="24" height="9" rx="4" fill="#10B981"/><rect x="47" y="27" width="24" height="12" rx="2" fill="#fff" stroke="#10B981"/><rect x="47" y="50" width="24" height="12" rx="2" fill="#fff" stroke="#10B981"/><rect x="24" y="66" width="22" height="10" rx="2" fill="#fff" stroke="#10B981"/><rect x="72" y="66" width="22" height="10" rx="2" fill="#fff" stroke="#10B981"/></svg>
          <svg v-else viewBox="0 0 118 88"><g fill="#fff" stroke="#8B5CF6"><rect x="20" y="8" width="78" height="12" rx="2"/><rect x="26" y="28" width="30" height="12" rx="2"/><rect x="62" y="28" width="30" height="12" rx="2"/><rect x="26" y="48" width="30" height="12" rx="2"/><rect x="62" y="48" width="30" height="12" rx="2"/><rect x="20" y="68" width="78" height="12" rx="2" fill="#ECFDF5" stroke="#10B981"/></g></svg>
          <view class="type-badge" :style="{ background: typeMeta[item.type].bg }">
            <svg v-if="item.type==='mindmap'" viewBox="0 0 24 24" fill="none" :stroke="typeMeta[item.type].color" stroke-width="2"><circle cx="6" cy="6" r="2.5"/><circle cx="18" cy="6" r="2.5"/><circle cx="12" cy="18" r="2.5"/><path d="M7.5 7.5L11 16M16.5 7.5L13 16"/></svg>
            <svg v-else-if="item.type==='flow'" viewBox="0 0 24 24" fill="none" :stroke="typeMeta[item.type].color" stroke-width="2"><rect x="4" y="4" width="6" height="5" rx="1"/><rect x="14" y="15" width="6" height="5" rx="1"/><path d="M7 9v6h7"/></svg>
            <svg v-else viewBox="0 0 24 24" fill="none" :stroke="typeMeta[item.type].color" stroke-width="2"><rect x="4" y="4" width="16" height="5" rx="1"/><rect x="7" y="14" width="10" height="5" rx="1"/></svg>
          </view>
        </view>
        <view class="card-main">
          <text class="card-title">{{ item.title }}</text>
          <text class="card-desc">{{ item.desc }}</text>
          <view class="card-time"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/></svg><text>{{ item.time }}</text></view>
        </view>
        <view class="card-more" @tap.stop="openSheet(item)">⋮</view>
      </view>
      <view class="nomore">没有更多了</view>
    </scroll-view>

    <view class="fab" @tap="goCreate">＋</view>

    <!-- 详情弹层 -->
    <view v-if="sheet" class="mask" @tap="closeSheet">
      <view class="sheet" @tap.stop>
        <view class="drag"></view>
        <view class="sheet-head">
          <view class="sheet-ico" :style="{ background: typeMeta[sheet.type].bg }">
            <svg v-if="sheet.type==='mindmap'" viewBox="0 0 24 24" fill="none" :stroke="typeMeta[sheet.type].color" stroke-width="2"><circle cx="6" cy="6" r="2.5"/><circle cx="18" cy="6" r="2.5"/><circle cx="12" cy="18" r="2.5"/><path d="M7.5 7.5L11 16M16.5 7.5L13 16"/></svg>
            <svg v-else-if="sheet.type==='flow'" viewBox="0 0 24 24" fill="none" :stroke="typeMeta[sheet.type].color" stroke-width="2"><rect x="4" y="4" width="6" height="5" rx="1"/><rect x="14" y="15" width="6" height="5" rx="1"/><path d="M7 9v6h7"/></svg>
            <svg v-else viewBox="0 0 24 24" fill="none" :stroke="typeMeta[sheet.type].color" stroke-width="2"><rect x="4" y="4" width="16" height="5" rx="1"/><rect x="7" y="14" width="10" height="5" rx="1"/></svg>
          </view>
          <text class="sheet-title">{{ sheet.title }}</text>
          <text class="sheet-more">⋮</text>
        </view>
        <text class="sheet-sub">{{ typeMeta[sheet.type].label }} · {{ sheet.time }}</text>
        <view class="sheet-preview">
          <svg v-if="sheet.type==='mindmap'" viewBox="0 0 118 88"><g stroke="#4D6BFE" fill="none" stroke-width="1"><path d="M59 44 L30 24M59 44 L30 64M59 44 L88 24M59 44 L88 64"/></g><rect x="50" y="37" width="18" height="14" rx="4" fill="#4D6BFE"/><rect x="20" y="18" width="16" height="10" rx="3" fill="#F59E0B" opacity=".7"/><rect x="20" y="60" width="16" height="10" rx="3" fill="#EC4899" opacity=".7"/><rect x="82" y="18" width="16" height="10" rx="3" fill="#10B981" opacity=".7"/><rect x="82" y="60" width="16" height="10" rx="3" fill="#8B5CF6" opacity=".7"/></svg>
          <svg v-else-if="sheet.type==='flow'" viewBox="0 0 118 88"><g stroke="#10B981" fill="none" stroke-width="1"><path d="M59 16v10M59 40v10M40 62h38"/></g><rect x="47" y="8" width="24" height="9" rx="4" fill="#10B981"/><rect x="47" y="27" width="24" height="12" rx="2" fill="#fff" stroke="#10B981"/><rect x="47" y="50" width="24" height="12" rx="2" fill="#fff" stroke="#10B981"/><rect x="24" y="66" width="22" height="10" rx="2" fill="#fff" stroke="#10B981"/><rect x="72" y="66" width="22" height="10" rx="2" fill="#fff" stroke="#10B981"/></svg>
          <svg v-else viewBox="0 0 118 88"><g fill="#fff" stroke="#8B5CF6"><rect x="20" y="8" width="78" height="12" rx="2"/><rect x="26" y="28" width="30" height="12" rx="2"/><rect x="62" y="28" width="30" height="12" rx="2"/><rect x="26" y="48" width="30" height="12" rx="2"/><rect x="62" y="48" width="30" height="12" rx="2"/><rect x="20" y="68" width="78" height="12" rx="2" fill="#ECFDF5" stroke="#10B981"/></g></svg>
        </view>
        <view class="sheet-desc-wrap">
          <text class="sheet-desc">{{ sheetDescText }}</text>
          <view v-if="sheetCanExpandDesc" class="sheet-expand" @tap="toggleSheetDesc">
            <text>{{ sheetExpanded ? '收起' : '查看更多' }}</text>
          </view>
        </view>
        <view class="sheet-actions">
          <view class="sa" @tap="preview"><view class="sa-ico"><svg viewBox="0 0 24 24"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg></view><text>预览</text></view>
          <view class="sa" @tap="toast('分享')"><view class="sa-ico"><svg viewBox="0 0 24 24"><circle cx="6" cy="12" r="2.5"/><circle cx="17" cy="6" r="2.5"/><circle cx="17" cy="18" r="2.5"/><path d="M8 11l7-4M8 13l7 4"/></svg></view><text>分享</text></view>
          <view class="sa" @tap="exportImage"><view class="sa-ico"><svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="M3 17l5-4 4 3 4-3 5 4"/></svg></view><text>导出图片</text></view>
          <view class="sa" :class="{ 'sa--busy': exporting }" @tap="exportFile"><view class="sa-ico"><svg viewBox="0 0 24 24"><path d="M6 2h9l5 5v15H6z"/><path d="M14 2v6h6"/></svg></view><text>{{ exporting ? '导出中' : '导出文件' }}</text></view>
          <view class="sa sa--del" :class="{ 'sa--busy': deleting }" @tap="deleteRecord"><view class="sa-ico"><svg viewBox="0 0 24 24"><path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13"/></svg></view><text>{{ deleting ? '删除中' : '删除' }}</text></view>
        </view>
        <view class="regen" @tap="regenerate"><svg viewBox="0 0 24 24"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z"/></svg><text>重新生成</text></view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  deleteFlowchartHistory,
  deleteMindmapHistory,
  getErrorMessage,
  getFlowchartDetail,
  getFlowchartHistory,
  getMindmapDetail,
  getMindmapHistory
} from '@/api/aiDiagram.js'
import {
  deleteArchitectureHistory,
  getArchitectureDetail,
  getArchitectureHistory
} from '@/api/architecture.js'

const typeMeta = {
  mindmap: { label: '思维导图', color: '#4D6BFE', bg: '#EEF0FF' },
  flow: { label: '流程图', color: '#10B981', bg: '#ECFDF5' },
  arch: { label: '架构图', color: '#8B5CF6', bg: '#F5F3FF' }
}
const GEN_PATH = {
  mindmap: '/subpackage_ai/mindmapGenerate/mindmapGenerate',
  flow: '/subpackage_ai/flowchartGenerate/flowchartGenerate',
  arch: '/subpackage_ai/architectureGenerate/architectureGenerate'
}
const VIEW_PATH = {
  mindmap: '/subpackage_ai/mindmapViewer/mindmapViewer',
  flow: '/subpackage_ai/flowchartViewer/flowchartViewer',
  arch: '/subpackage_ai/architecturePreview/architecturePreview'
}

const tab = ref('mindmap')
const keyword = ref('')
const searchOn = ref(false)
const sheet = ref(null)
const sheetExpanded = ref(false)
const all = ref({ mindmap: [], flow: [], arch: [] })
const exporting = ref(false)
const deleting = ref(false)
const CARD_DESC_LIMIT = 34
const SHEET_DESC_LIMIT = 108
const RESTORE_KEYS = {
  mindmap: 'aiMindmapRestoreDraft',
  flow: 'aiFlowchartRestoreDraft',
  arch: 'aiArchitectureRestoreDraft'
}
const DETAIL_HANDLERS = {
  mindmap: getMindmapDetail,
  flow: getFlowchartDetail,
  arch: getArchitectureDetail
}
const DELETE_HANDLERS = {
  mindmap: deleteMindmapHistory,
  flow: deleteFlowchartHistory,
  arch: deleteArchitectureHistory
}

function fmt(t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
function systemTypeLabel(value) {
  const map = {
    WEB: 'Web系统',
    APP: 'APP系统',
    MINI_PROGRAM: '小程序',
    ADMIN: '管理后台'
  }
  return map[String(value || '').toUpperCase()] || 'Web系统'
}
function relationLabel(value) {
  const map = {
    AUTO: '自动分析',
    MODULE: '模块关系',
    DATA_FLOW: '数据流向',
    CALL: '调用关系'
  }
  return map[String(value || '').toUpperCase()] || '自动分析'
}
function archDesc(record = {}) {
  const relation = record.resolvedRelationMode || record.requestedRelationMode || record.relationMode || record.relationType
  return `${systemTypeLabel(record.systemType)} · ${relationLabel(relation)}`
}

function firstText(...values) {
  return values.find(value => String(value || '').trim()) || ''
}

function compactText(value = '') {
  return String(value || '')
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map(part => part.trim())
    .filter(Boolean)
    .join(' ')
}

function stripFileContent(value = '') {
  return String(value || '').split(/文件解析内容[:：]/)[0].trim()
}

function extractFileContent(value = '') {
  const text = String(value || '')
  const match = text.match(/文件解析内容[:：]\s*([\s\S]*)$/)
  return match ? match[1].trim() : ''
}

function truncateText(value = '', limit = CARD_DESC_LIMIT) {
  const text = compactText(value)
  if (!text) return ''
  return text.length > limit ? `${text.slice(0, limit)}...` : text
}

function fileNameFromPath(value = '') {
  const text = String(value || '').trim()
  if (!text) return ''
  return text.split(/[\\/]/).filter(Boolean).pop() || text
}

function firstFile(record = {}) {
  return Array.isArray(record.files) && record.files.length ? record.files[0] : null
}

function fileName(record = {}) {
  const file = firstFile(record)
  return firstText(file?.name, file?.fileName, record.fileName, fileNameFromPath(record.sourceFile))
}

function fileSummary(record = {}) {
  const file = firstFile(record)
  return firstText(record.fileSummary, record.summary, file?.summary)
}

function sourceText(record = {}) {
  const file = firstFile(record)
  return firstText(record.sourceText, file?.text, extractFileContent(record.content), extractFileContent(record.description))
}

function displayInput(record = {}) {
  const value = firstText(record.content, record.description, record.preview, record.subtitle)
  return stripFileContent(value).trim()
}

function fullDesc(record = {}, type) {
  const input = displayInput(record)
  const summary = fileSummary(record)
  const parsedText = sourceText(record)
  const importedFile = fileName(record)
  const parts = []

  if (type === 'arch') {
    parts.push(archDesc(record))
  }
  if (input) {
    parts.push(type === 'arch' ? `原始需求：${input}` : input)
  }
  if (importedFile) {
    parts.push(`导入文件：${importedFile}`)
  }
  if (summary && !parts.join('\n').includes(summary)) {
    parts.push(`AI 总结：${summary}`)
  }
  if (parsedText && !String(record.description || record.content || '').includes(parsedText)) {
    parts.push(`文件解析内容：${parsedText}`)
  }
  return firstText(parts.join('\n\n'), input, summary, type === 'arch' ? archDesc(record) : typeMeta[type].label)
}

function cardDesc(record = {}, type) {
  return truncateText(firstText(displayInput(record), fileSummary(record), type === 'arch' ? archDesc(record) : typeMeta[type].label))
}

function normalizeRecord(record = {}, type, fallback = {}) {
  const raw = {
    ...(fallback.raw || {}),
    ...record
  }
  const time = fmt(raw.createTime || raw.updateTime || raw.createdAt || fallback.time)
  return {
    id: firstText(raw.id, fallback.id),
    type,
    title: firstText(raw.title, fallback.title, '未命名'),
    desc: cardDesc(raw, type),
    fullDesc: fullDesc(raw, type),
    time,
    raw
  }
}

function norm(list, type) {
  return (list || []).map(r => normalizeRecord(r, type))
}

async function load() {
  try { const d = await getMindmapHistory(); all.value.mindmap = norm(Array.isArray(d) ? d : d?.records, 'mindmap') } catch (e) { all.value.mindmap = [] }
  try { const d = await getFlowchartHistory(); all.value.flow = norm(Array.isArray(d) ? d : d?.records, 'flow') } catch (e) { all.value.flow = [] }
  try { const d = await getArchitectureHistory(); all.value.arch = norm(d?.records, 'arch') } catch (e) { all.value.arch = [] }
}

const list = computed(() => {
  const base = all.value[tab.value] || []
  if (!keyword.value.trim()) return base
  return base.filter(i => i.title.includes(keyword.value.trim()) || String(i.fullDesc || '').includes(keyword.value.trim()))
})
const sheetFullDesc = computed(() => sheet.value?.fullDesc || sheet.value?.desc || '')
const sheetCanExpandDesc = computed(() => compactText(sheetFullDesc.value).length > SHEET_DESC_LIMIT)
const sheetDescText = computed(() => {
  if (!sheetCanExpandDesc.value || sheetExpanded.value) return sheetFullDesc.value
  return truncateText(sheetFullDesc.value, SHEET_DESC_LIMIT)
})

function setTab(t) { tab.value = t }
function initTab(options = {}) {
  const value = String(options.tab || options.type || '').toLowerCase()
  if (value === 'arch' || value === 'architecture') tab.value = 'arch'
  if (value === 'flow' || value === 'flowchart') tab.value = 'flow'
  if (value === 'mindmap' || value === 'mind_map') tab.value = 'mindmap'
}
function applyFilter() { /* computed 自动过滤 */ }
function toggleSearch() { searchOn.value = !searchOn.value; if (!searchOn.value) keyword.value = '' }
async function openSheet(item) {
  sheetExpanded.value = false
  sheet.value = item
  try {
    const detail = await DETAIL_HANDLERS[item.type](item.id)
    if (sheet.value && String(sheet.value.id) === String(item.id) && sheet.value.type === item.type) {
      sheet.value = normalizeRecord(detail || {}, item.type, item)
    }
  } catch (error) {
    // 列表数据足够展示摘要，详情失败时保留当前弹层。
  }
}
function closeSheet() {
  sheet.value = null
  sheetExpanded.value = false
}
function toggleSheetDesc() {
  sheetExpanded.value = !sheetExpanded.value
}
function preview() {
  const s = sheet.value; if (!s) return
  closeSheet()
  uni.navigateTo({ url: `${VIEW_PATH[s.type]}?id=${encodeURIComponent(s.id)}&recordId=${encodeURIComponent(s.id)}` })
}
function exportImage() {
  const s = sheet.value
  if (!s) return
  const id = encodeURIComponent(s.id)
  closeSheet()
  uni.navigateTo({ url: `${VIEW_PATH[s.type]}?id=${id}&recordId=${id}&autoExport=1` })
}
async function exportFile() {
  const s = sheet.value
  if (!s || exporting.value) return
  exporting.value = true
  uni.showLoading({ title: '正在导出', mask: true })
  try {
    const detail = await DETAIL_HANDLERS[s.type](s.id)
    const exportData = {
      schema: 'ai-diagram-history-export/v1',
      type: s.type,
      typeLabel: typeMeta[s.type].label,
      id: s.id,
      title: s.title,
      exportedAt: new Date().toISOString(),
      data: detail
    }
    const filename = `${safeFileName(s.title || typeMeta[s.type].label)}_${s.id}.json`
    await saveTextFile(filename, JSON.stringify(exportData, null, 2))
    closeSheet()
  } catch (error) {
    toast(getErrorMessage(error, '导出失败'))
  } finally {
    uni.hideLoading()
    exporting.value = false
  }
}
function deleteRecord() {
  const s = sheet.value
  if (!s || deleting.value) return
  uni.showModal({
    title: '删除记录',
    content: `确定删除“${s.title}”吗？删除后不可恢复。`,
    confirmText: '删除',
    confirmColor: '#EF4444',
    success: async (res) => {
      if (!res.confirm) return
      deleting.value = true
      try {
        await DELETE_HANDLERS[s.type](s.id)
        all.value[s.type] = (all.value[s.type] || []).filter(item => String(item.id) !== String(s.id))
        closeSheet()
        toast('已删除')
      } catch (error) {
        toast(getErrorMessage(error, '删除失败'))
      } finally {
        deleting.value = false
      }
    }
  })
}
function normalizeRestoreFiles(record = {}) {
  const files = Array.isArray(record.files) ? record.files : []
  const normalized = files.map((file) => {
    if (!file || typeof file !== 'object') return null
    const sourceFile = firstText(file.sourceFile, file.url, record.sourceFile)
    const fileId = firstText(file.fileId, file.id, record.fileId)
    const name = firstText(file.name, file.fileName, fileNameFromPath(sourceFile), '已导入文件')
    return {
      ...file,
      id: fileId,
      fileId,
      name,
      fileName: name,
      sourceFile,
      url: sourceFile,
      text: firstText(file.text, record.sourceText, extractFileContent(record.content), extractFileContent(record.description)),
      summary: firstText(file.summary, record.fileSummary, record.summary),
      size: file.size || 0,
      textLength: file.textLength || 0,
      truncated: Boolean(file.truncated),
      pageCount: file.pageCount || 0,
      slideCount: file.slideCount || 0,
      paragraphCount: file.paragraphCount || 0
    }
  }).filter(Boolean)
  if (normalized.length) return normalized

  const sourceFile = record.sourceFile || ''
  const fileId = record.fileId || ''
  const text = firstText(record.sourceText, extractFileContent(record.content), extractFileContent(record.description))
  const summary = fileSummary(record)
  if (!sourceFile && !fileId && !text && !summary) return []
  const name = firstText(fileName(record), fileNameFromPath(sourceFile), '已导入文件')
  return [{
    id: fileId,
    fileId,
    name,
    fileName: name,
    sourceFile,
    url: sourceFile,
    text,
    summary,
    size: 0,
    textLength: text.length,
    truncated: false,
    pageCount: 0,
    slideCount: 0,
    paragraphCount: 0
  }]
}

function stripMindmapContent(value = '') {
  return stripFileContent(value)
    .replace(/建议中心主题[:：][^\n]*\n?/g, '')
    .replace(/用户输入要求[:：]/g, '')
    .trim()
}

function stripFlowContent(value = '') {
  const text = String(value || '')
  const match = text.match(/原始需求[:：]\s*([\s\S]*?)(?:\n\s*当前泳道|\n\s*当前节点顺序|\n\s*当前连线|\n\s*优化要求|文件解析内容[:：]|$)/)
  if (match) return match[1].trim()
  return stripFileContent(text).trim()
}

function buildRestoreDraft(record = {}, type) {
  const files = normalizeRestoreFiles(record)
  const common = {
    title: record.title || '',
    description: record.description || record.content || record.preview || '',
    files,
    sourceText: firstText(record.sourceText, files[0]?.text, extractFileContent(record.content), extractFileContent(record.description)),
    sourceFile: firstText(record.sourceFile, files[0]?.sourceFile, files[0]?.url),
    fileId: firstText(record.fileId, files[0]?.fileId, files[0]?.id),
    summary: firstText(fileSummary(record), files[0]?.summary),
    fileSummary: firstText(record.fileSummary, record.summary, files[0]?.summary)
  }
  if (type === 'mindmap') {
    const topic = firstText(record.topic, stripMindmapContent(record.content || ''), stripMindmapContent(record.description || record.preview || ''))
    return {
      ...common,
      topic,
      content: topic,
      centerTopic: record.centerTopic || record.resolvedCenterTopic || '',
      centerTopicMode: record.centerTopicMode || record.requestedCenterTopicMode || 'AUTO',
      depth: record.depth || record.requestedDepth || 'AUTO',
      structure: record.structure || record.requestedStructure || 'AUTO',
      detail: record.detail || record.detailLevel || 'STANDARD'
    }
  }
  if (type === 'flow') {
    const content = firstText(record.content, stripFlowContent(record.description || record.preview || ''))
    return {
      ...common,
      content,
      sceneType: record.sceneType || record.processType || 'AUTO',
      nodeGranularity: record.nodeGranularity || record.nodeLevel || 'AUTO',
      layoutDirection: record.requestedLayoutDirection || record.layoutDirection || 'VERTICAL',
      decisionMode: record.requestedDecisionMode || record.decisionMode || 'AUTO',
      swimlaneMode: record.requestedSwimlaneMode || record.swimlaneMode || record.swimlane || 'AUTO'
    }
  }
  return {
    ...common,
    content: firstText(record.content, stripFileContent(record.description || record.preview || '')),
    systemType: record.systemType || 'WEB',
    autoArchitectureLayers: record.autoArchitectureLayers !== false,
    architectureLayers: Array.isArray(record.architectureLayers) ? record.architectureLayers : [],
    focusContents: Array.isArray(record.focusContents) ? record.focusContents : [],
    relationMode: record.requestedRelationMode || record.relationMode || record.relationType || 'AUTO',
    requestedRelationMode: record.requestedRelationMode || record.relationMode || record.relationType || 'AUTO',
    hierarchyMode: record.requestedHierarchyMode || record.hierarchyMode || 'STRUCTURED'
  }
}

async function regenerate() {
  const s = sheet.value; if (!s) return
  let detail = s.raw || s
  try {
    detail = {
      ...detail,
      ...(await DETAIL_HANDLERS[s.type](s.id) || {})
    }
  } catch (error) {
    // 详情接口失败时使用列表里已有的历史字段尽量恢复。
  }
  const restoreKey = RESTORE_KEYS[s.type]
  if (restoreKey) {
    uni.setStorageSync(restoreKey, buildRestoreDraft(detail, s.type))
  }
  closeSheet()
  uni.navigateTo({ url: `${GEN_PATH[s.type]}?restore=1` })
}
function goCreate() { uni.navigateTo({ url: GEN_PATH[tab.value] }) }
function goBack() { uni.navigateBack() }
function toast(t) { uni.showToast({ title: t, icon: 'none' }) }

function safeFileName(value = '') {
  const text = String(value || 'AI图表')
    .trim()
    .replace(/[\\/:*?"<>|]+/g, '_')
    .replace(/\s+/g, '_')
  return (text || 'AI图表').slice(0, 48)
}

async function saveTextFile(filename, content) {
  if (downloadInBrowser(filename, content)) {
    toast('已导出文件')
    return
  }
  try {
    const filePath = await writeLocalFile(filename, content)
    toast('已导出文件')
    if (typeof uni.openDocument === 'function') {
      uni.openDocument({
        filePath,
        showMenu: true,
        fail: () => showExportPath(filePath)
      })
    } else {
      showExportPath(filePath)
    }
  } catch (error) {
    await copyExportContent(content)
    toast('环境不支持保存文件，已复制内容')
  }
}

function downloadInBrowser(filename, content) {
  if (
    typeof document === 'undefined' ||
    typeof Blob === 'undefined' ||
    typeof URL === 'undefined' ||
    typeof URL.createObjectURL !== 'function'
  ) {
    return false
  }
  const blob = new Blob([content], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  return true
}

function writeLocalFile(filename, content) {
  return new Promise((resolve, reject) => {
    const fs = typeof uni.getFileSystemManager === 'function' ? uni.getFileSystemManager() : null
    const basePath = (typeof uni.env !== 'undefined' && uni.env.USER_DATA_PATH) ? uni.env.USER_DATA_PATH : ''
    if (!fs || !basePath || typeof fs.writeFile !== 'function') {
      reject(new Error('当前环境不支持写入文件'))
      return
    }
    const filePath = `${basePath}/${filename}`
    fs.writeFile({
      filePath,
      data: content,
      encoding: 'utf8',
      success: () => resolve(filePath),
      fail: reject
    })
  })
}

function copyExportContent(content) {
  return new Promise((resolve, reject) => {
    if (typeof uni.setClipboardData !== 'function') {
      reject(new Error('当前环境不支持复制导出内容'))
      return
    }
    uni.setClipboardData({
      data: content,
      success: resolve,
      fail: reject
    })
  })
}

function showExportPath(filePath) {
  uni.showModal({
    title: '导出完成',
    content: `文件已保存：${filePath}`,
    showCancel: false
  })
}

onLoad(initTab)
onShow(() => { load() })
</script>

<style lang="scss" scoped>
.page { height: 100vh; background: #F5F6FA; display: flex; flex-direction: column; overflow: hidden; }
.nav-right { display: flex; gap: 16rpx; align-items: center; }
.nav-ico { width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; }
.ni { width: 38rpx; height: 38rpx; stroke: #1D1D1F; fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.search-bar { display: flex; align-items: center; padding: 16rpx 32rpx; background: #F5F6FA; }
.search-input { flex: 1; height: 72rpx; background: #fff; border-radius: 16rpx; padding: 0 24rpx; font-size: 26rpx; box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04); }

.tabs { display: flex; gap: 20rpx; margin: 16rpx 24rpx 8rpx; padding: 12rpx; background: #fff; border-radius: 24rpx; box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.03); }
.tab { flex: 1; display: flex; align-items: center; justify-content: center; gap: 14rpx; height: 88rpx; border-radius: 24rpx; background: #F5F6FA; font-size: 28rpx; font-weight: 600; color: #333; }
.tab-ic { width: 34rpx; height: 34rpx; }
.tab.on { background: #16181D; color: #fff; }

.count-row { display: flex; align-items: center; justify-content: space-between; padding: 24rpx 40rpx 16rpx; font-size: 26rpx; color: #666; }
.sort { color: #888; }

.list { flex: 1; height: 0; padding: 8rpx 0 24rpx; }
.card { height: 232rpx; background: #fff; border-radius: 32rpx; padding: 28rpx; display: flex; align-items: center; gap: 24rpx; margin: 0 32rpx 24rpx; box-sizing: border-box; position: relative; overflow: hidden; box-shadow: 0 4rpx 16rpx rgba(0,0,0,.03); }
.thumb { width: 236rpx; height: 176rpx; border-radius: 20rpx; background: #FAFAFD; border: 2rpx solid #F0F0F4; position: relative; overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.thumb svg { width: 100%; height: 100%; }
.type-badge { position: absolute; left: 12rpx; top: 12rpx; width: 52rpx; height: 52rpx; border-radius: 16rpx; display: flex; align-items: center; justify-content: center; }
.type-badge svg { width: 28rpx; height: 28rpx; }
.card-main { flex: 1; min-width: 0; height: 176rpx; display: flex; flex-direction: column; justify-content: center; gap: 12rpx; overflow: hidden; }
.card-title { display: block; width: 100%; font-size: 30rpx; font-weight: 700; line-height: 38rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-desc { display: block; width: 100%; max-width: 100%; font-size: 24rpx; line-height: 32rpx; color: #8a8fa3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-time { display: flex; align-items: center; gap: 10rpx; font-size: 24rpx; color: #a3a7b8; }
.card-time svg { width: 26rpx; height: 26rpx; stroke: #a3a7b8; fill: none; stroke-width: 2; }
.card-more { position: absolute; right: 24rpx; top: 28rpx; color: #333; font-size: 32rpx; }
.nomore { text-align: center; font-size: 24rpx; color: #a3a7b8; padding: 20rpx 0; }
.fab { position: fixed; right: 40rpx; bottom: 52rpx; width: 108rpx; height: 108rpx; border-radius: 50%; background: #16181D; color: #fff; font-size: 52rpx; display: flex; align-items: center; justify-content: center; box-shadow: 0 16rpx 40rpx rgba(0,0,0,.25); z-index: 60; }

.mask { position: fixed; inset: 0; background: rgba(0,0,0,.35); display: flex; align-items: flex-end; z-index: 100; }
.sheet { width: 100%; background: #fff; border-radius: 48rpx 48rpx 0 0; padding: 24rpx 40rpx calc(40rpx + env(safe-area-inset-bottom)); max-height: 78%; overflow-y: auto; }
.drag { width: 80rpx; height: 8rpx; border-radius: 999rpx; background: #E2E5EA; margin: 0 auto 28rpx; }
.sheet-head { display: flex; align-items: center; gap: 20rpx; }
.sheet-ico { width: 80rpx; height: 80rpx; border-radius: 20rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.sheet-ico svg { width: 36rpx; height: 36rpx; }
.sheet-title { font-size: 32rpx; font-weight: 700; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sheet-more { color: #333; font-size: 32rpx; }
.sheet-sub { font-size: 24rpx; color: #8a8fa3; margin: 12rpx 0 24rpx 100rpx; display: block; }
.sheet-preview { background: #FAFAFD; border: 2rpx solid #F0F0F4; border-radius: 28rpx; height: 400rpx; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.sheet-preview svg { width: 90%; height: 90%; }
.sheet-desc-wrap { margin: 24rpx 4rpx; }
.sheet-desc { font-size: 26rpx; line-height: 1.6; color: #555; display: block; white-space: pre-wrap; word-break: break-word; }
.sheet-expand { display: inline-flex; align-items: center; margin-top: 10rpx; color: #4D6BFE; font-size: 24rpx; font-weight: 700; }
.sheet-actions { display: flex; justify-content: space-between; padding: 12rpx 8rpx 28rpx; }
.sa { display: flex; flex-direction: column; align-items: center; gap: 12rpx; font-size: 24rpx; color: #333; }
.sa--busy { opacity: .55; pointer-events: none; }
.sa-ico { width: 80rpx; height: 80rpx; border-radius: 50%; background: #F5F6FA; display: flex; align-items: center; justify-content: center; }
.sa-ico svg { width: 34rpx; height: 34rpx; stroke: #333; fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.sa--del { color: #EF4444; }
.sa--del .sa-ico { background: #FEF2F2; }
.sa--del .sa-ico svg { stroke: #EF4444; }
.regen { height: 100rpx; border-radius: 28rpx; background: #16181D; color: #fff; display: flex; align-items: center; justify-content: center; gap: 16rpx; font-size: 30rpx; font-weight: 700; }
.regen svg { width: 32rpx; height: 32rpx; stroke: #fff; fill: none; stroke-width: 2; }
</style>
