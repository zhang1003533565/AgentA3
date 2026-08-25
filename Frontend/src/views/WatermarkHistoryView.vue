<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getWatermarkHistory, removeWatermarkHistory } from '../utils/watermarkHistory'

const router = useRouter()
const isManageMode = ref(false)
const showPopup = ref(false)
const currentRecord = ref(null)
const toastMessage = ref('')
let toastTimer

const records = ref([])

const hasSelectedRecords = computed(() => records.value.some((item) => item.selected))

onMounted(() => {
  records.value = getWatermarkHistory()
})

function showToast(message) {
  toastMessage.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toastMessage.value = '' }, 2200)
}

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function toggleRecord(index) {
  records.value[index].selected = !records.value[index].selected
}

function openRecord(record) {
  if (isManageMode.value) return
  currentRecord.value = record
  showPopup.value = true
}

function closePopup() {
  showPopup.value = false
  currentRecord.value = null
}

function reEdit() {
  if (!currentRecord.value) return
  const path = currentRecord.value.editPath
  closePopup()
  if (path === '/ai-original') showToast('去水印页面暂未迁移')
  else router.push(path)
}

function deleteSingle() {
  if (!currentRecord.value) return
  removeWatermarkHistory(currentRecord.value.id)
  records.value = records.value.filter((item) => item !== currentRecord.value)
  closePopup()
  showToast('已删除')
}

function deleteSelected() {
  removeWatermarkHistory(records.value.filter((item) => item.selected).map((item) => item.id))
  records.value = records.value.filter((item) => !item.selected)
  isManageMode.value = false
  showToast('已删除选中记录')
}
</script>

<template>
  <div class="feature-page history-page">
    <AppTabBar />

    <main class="history-content">
      <header class="history-heading">
        <button class="back-button" type="button" aria-label="返回水印工具" @click="router.push('/ai-original')">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 5-7 7 7 7" /></svg>
        </button>
        <h1>历史记录</h1>
        <button class="manage-button" type="button" @click="toggleManageMode">{{ isManageMode ? '完成' : '管理' }}</button>
      </header>

      <section class="history-list-section">
        <p class="history-label">最近处理的图片</p>
        <div class="record-list">
          <div v-if="!records.length" class="history-empty">暂无处理记录</div>
          <div
            v-for="(record, index) in records"
            :key="`${record.time}-${record.format}`"
            class="record-item"
            role="button"
            tabindex="0"
            @click="openRecord(record)"
            @keydown.enter="openRecord(record)"
          >
            <input v-if="isManageMode" class="record-checkbox" type="checkbox" :checked="record.selected" :aria-label="`选择${record.title} ${record.time}`" @click.stop="toggleRecord(index)" />
            <span class="record-preview" aria-hidden="true">
              <img v-if="record.previewUrl" :src="record.previewUrl" :alt="record.title" />
              <svg v-else viewBox="0 0 24 24"><rect x="3.5" y="4" width="17" height="16" rx="2" /><circle cx="8.5" cy="9" r="1.5" /><path d="m5.5 17 4.5-4 3 2.5 2-2 3.5 3.5" /></svg>
            </span>
            <span class="record-info">
              <strong>{{ record.title }}</strong>
              <span>{{ record.time }} · {{ record.format }}</span>
            </span>
            <span v-if="!isManageMode" class="record-arrow" aria-hidden="true">›</span>
          </div>
        </div>
      </section>
    </main>

    <div v-if="isManageMode" class="delete-bar">
      <button class="delete-button" type="button" :disabled="!hasSelectedRecords" @click="deleteSelected">删除选中记录</button>
    </div>

    <div v-if="showPopup" class="history-mask" @click.self="closePopup">
      <section class="history-popup" role="dialog" aria-modal="true" aria-label="历史记录操作">
        <div class="popup-preview" aria-hidden="true">
          <img v-if="currentRecord?.previewUrl" :src="currentRecord.previewUrl" :alt="currentRecord.title" />
          <svg v-else viewBox="0 0 24 24"><rect x="3.5" y="4" width="17" height="16" rx="2" /><circle cx="8.5" cy="9" r="1.5" /><path d="m5.5 17 4.5-4 3 2.5 2-2 3.5 3.5" /></svg>
        </div>
        <div class="popup-actions">
          <button class="popup-button popup-button--secondary" type="button" @click="reEdit">重新编辑</button>
          <button class="popup-button popup-button--danger" type="button" @click="deleteSingle">删除</button>
        </div>
      </section>
    </div>

    <transition name="toast"><div v-if="toastMessage" class="history-toast" role="status">{{ toastMessage }}</div></transition>
  </div>
</template>

<style scoped>
.history-page { min-height: 100vh; padding-top: 60px; background: #f4f7fb; color: #1f2937; }
.history-content { width: min(1180px, calc(100% - 40px)); margin: 0 auto; padding: 26px 0 48px; }
.history-heading { display: grid; grid-template-columns: 40px 1fr 64px; align-items: center; gap: 12px; margin-bottom: 26px; }
.history-heading h1 { margin: 0; color: #17233a; font-size: 27px; font-weight: 800; text-align: center; }
.back-button { display: grid; width: 40px; height: 40px; place-items: center; border: 1px solid #dbe3eb; border-radius: 8px; color: #334155; background: #fff; }.back-button svg { width: 21px; height: 21px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; }
.manage-button { min-height: 40px; padding: 0 8px; color: #334155; background: transparent; font-size: 14px; font-weight: 700; }
.history-list-section { padding: 0 8px; }.history-label { margin: 0 0 12px; color: #94a3b8; font-size: 14px; }.record-list { display: grid; gap: 8px; }
.history-empty { min-height: 180px; display: grid; place-items: center; border: 1px dashed #ccd6e0; border-radius: 9px; color: #94a3b8; background: #fafbfd; }
.record-item { display: flex; align-items: center; gap: 18px; min-height: 104px; padding: 16px 18px; border: 1px solid transparent; border-radius: 9px; background: transparent; cursor: pointer; }.record-item:hover, .record-item:focus-visible { border-color: #e1e7ed; background: #fff; outline: none; }
.record-checkbox { width: 18px; height: 18px; flex: 0 0 18px; accent-color: #315f8c; }.record-preview { display: grid; width: 56px; height: 56px; flex: 0 0 56px; place-items: center; overflow: hidden; border-radius: 8px; color: #94a3b8; background: #eef2f6; }.record-preview img { width: 100%; height: 100%; object-fit: cover; }.record-preview svg { width: 28px; height: 28px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.6; }
.record-info { min-width: 0; flex: 1; }.record-info strong, .record-info span { display: block; }.record-info strong { color: #1f2937; font-size: 16px; font-weight: 800; }.record-info span { margin-top: 5px; color: #94a3b8; font-size: 13px; }.record-arrow { color: #94a3b8; font-size: 23px; }
.delete-bar { position: fixed; right: 0; bottom: 0; left: 0; z-index: 1050; padding: 14px 24px; border-top: 1px solid #e2e8f0; background: rgba(244,247,251,.96); }.delete-button { display: block; width: min(1180px, 100%); min-height: 44px; margin: 0 auto; border-radius: 8px; color: #fff; background: #dc4d47; font-weight: 800; }.delete-button:disabled { cursor: not-allowed; opacity: .45; }
.history-mask { position: fixed; inset: 0; z-index: 1100; display: flex; align-items: flex-end; background: rgba(30,41,59,.4); }.history-popup { width: min(100%, 620px); margin: 0 auto; padding: 22px; border-radius: 16px 16px 0 0; background: #fff; }.popup-preview { display: grid; height: 300px; place-items: center; overflow: hidden; border-radius: 10px; color: #94a3b8; background: #eef2f6; }.popup-preview img { max-width: 100%; max-height: 100%; object-fit: contain; }.popup-preview svg { width: 72px; height: 72px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.3; }.popup-actions { display: flex; gap: 10px; margin-top: 18px; }.popup-button { flex: 1; min-height: 42px; border-radius: 8px; font-weight: 800; }.popup-button--secondary { border: 1px solid #d7e0e8; color: #34506c; background: #fff; }.popup-button--danger { color: #fff; background: #dc4d47; }
.history-toast { position: fixed; left: 50%; bottom: 28px; z-index: 1200; padding: 10px 16px; border-radius: 8px; color: #fff; background: rgba(15,23,42,.9); font-size: 13px; transform: translateX(-50%); }.toast-enter-active, .toast-leave-active { transition: opacity .18s ease, transform .18s ease; }.toast-enter-from, .toast-leave-to { opacity: 0; transform: translate(-50%, 8px); }
@media (max-width: 620px) { .history-content { width: min(100% - 24px, 560px); padding-top: 18px; }.history-heading { grid-template-columns: 40px 1fr 54px; }.history-list-section { padding: 0; }.record-item { gap: 12px; min-height: 86px; padding: 12px 8px; }.record-preview { width: 48px; height: 48px; flex-basis: 48px; }.popup-preview { height: 240px; } }
</style>
