<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// ─── 背景设置 ───────────────────────────────────────────────────────────
const BG_STORAGE_KEY = 'schedule_bg_image'
const showBgModal = ref(false)
const bgImage = ref(null) // data URL or null (default)
const fileInputRef = ref(null)
const saving = ref(false)
const saved = ref(false)

onMounted(() => {
  bgImage.value = localStorage.getItem(BG_STORAGE_KEY) || null
})

function openBgModal() {
  saved.value = false
  showBgModal.value = true
}

function closeBgModal() {
  showBgModal.value = false
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    bgImage.value = ev.target.result
  }
  reader.readAsDataURL(file)
  e.target.value = '' // reset so same file can be re-selected
}

function resetToDefault() {
  bgImage.value = null
}

// TODO: 调用后端接口保存背景配置
// async function saveBgConfigAPI(imageBase64) {
//   await scheduleApi.saveBgConfig({ image: imageBase64 })
// }

async function saveBgConfig() {
  saving.value = true
  try {
    // TODO: 调用后端接口
    await new Promise(r => setTimeout(r, 600))
    if (bgImage.value) {
      localStorage.setItem(BG_STORAGE_KEY, bgImage.value)
    } else {
      localStorage.removeItem(BG_STORAGE_KEY)
    }
    saved.value = true
    setTimeout(() => { showBgModal.value = false }, 1000)
  } catch {
    alert('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

// ─── 导航 ───────────────────────────────────────────────────────────────
function goBack() {
  router.back()
}

function goEduAccount() {
  router.push('/mine/edu-account')
}

function goPeriodTimeConfig() {
  router.push('/mine/period-time')
}
</script>

<template>
  <div class="page-shell">

    <!-- 顶部导航 -->
    <header class="navbar">
      <button class="nav-back" type="button" @click="goBack">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <h1 class="nav-title">课表设置</h1>
      <div class="nav-placeholder"></div>
    </header>

    <main class="page-body">

      <!-- 功能列表 -->
      <section class="card menu-card">
        <!-- 教务账号设置 -->
        <div class="menu-row">
          <div class="menu-info">
            <span class="menu-label">教务账号设置</span>
            <span class="menu-desc">绑定教务系统账号，自动导入课表</span>
          </div>
          <button class="menu-action link-btn" type="button" @click="goEduAccount">修改</button>
        </div>

        <div class="menu-divider"></div>

        <!-- 节次时间设置 -->
        <div class="menu-row">
          <div class="menu-info">
            <span class="menu-label">节次时间设置</span>
            <span class="menu-desc">自定义每节课的起止时间</span>
          </div>
          <button class="menu-action dark-btn" type="button" @click="goPeriodTimeConfig">设置</button>
        </div>

        <div class="menu-divider"></div>

        <!-- 自定义课表背景 -->
        <div class="menu-row">
          <div class="menu-info">
            <span class="menu-label">自定义课表背景</span>
            <span class="menu-desc">上传喜欢的图片作为课表背景</span>
          </div>
          <button class="menu-action dark-btn" type="button" @click="openBgModal">设置</button>
        </div>
      </section>

      <!-- 说明区域 -->
      <section class="card tips-card">
        <div class="tips-label">说明</div>
        <ul class="tips-list">
          <li>节次时间会影响课表左侧时间和课程详情里的时间范围。</li>
          <li>课程位置仍按"第几节"显示，不会改变导入的课程节次。</li>
        </ul>
      </section>

    </main>

    <!-- 背景设置弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showBgModal" class="modal-overlay" @click.self="closeBgModal">
          <div class="modal-panel">
            <div class="modal-title">自定义课表背景</div>

            <!-- 预览区 -->
            <div class="preview-box" :class="{ 'has-image': !!bgImage }">
              <img v-if="bgImage" :src="bgImage" class="preview-img" />
              <span v-else class="preview-empty">暂无背景图片</span>
            </div>

            <!-- 操作按钮组 -->
            <div class="bg-actions">
              <input
                ref="fileInputRef"
                type="file"
                accept="image/*"
                style="display:none"
                @change="handleFileChange"
              />
              <button class="bg-upload-btn" type="button" @click="triggerFileInput">
                <svg viewBox="0 0 20 20" width="15" height="15" fill="currentColor">
                  <path d="M3 17a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm3.293-7.707a1 1 0 011.414 0L9 10.586V3a1 1 0 112 0v7.586l1.293-1.293a1 1 0 111.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z"/>
                </svg>
                上传图片
              </button>
              <button class="bg-reset-btn" type="button" @click="resetToDefault">恢复默认背景</button>
            </div>

            <div v-if="saved" class="saved-msg">背景已保存，返回课表即可看到效果</div>

            <button
              class="modal-btn"
              type="button"
              :disabled="saving"
              @click="saveBgConfig"
            >{{ saving ? '保存中…' : '保存背景' }}</button>
            <button class="modal-cancel-btn" type="button" @click="closeBgModal">取消</button>
          </div>
        </div>
      </Transition>
    </Teleport>

  </div>
</template>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f4f7fb;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  color: #1f2937;
}

/* ─── 顶部导航 ─── */
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 52px;
  background: #fff;
  border-bottom: 1px solid #eef2f7;
  flex-shrink: 0;
}

.nav-back {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: #f1f5f9;
  color: #374151;
  cursor: pointer;
}

.nav-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.nav-placeholder {
  width: 36px;
}

/* ─── 主体 ─── */
.page-body {
  flex: 1;
  padding: 14px 16px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ─── 通用卡片 ─── */
.card {
  background: #fff;
  border-radius: 12px;
  padding: 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

/* ─── 功能列表 ─── */
.menu-card {
  padding: 0;
}

.menu-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  gap: 12px;
}

.menu-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1;
  min-width: 0;
}

.menu-label {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.menu-desc {
  font-size: 12px;
  color: #9ca3af;
}

.menu-divider {
  height: 1px;
  background: #f1f5f9;
  margin: 0 18px;
}

/* 蓝色修改按钮 */
.link-btn {
  flex-shrink: 0;
  padding: 5px 14px;
  border: 1px solid #dbeafe;
  border-radius: 6px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}

.link-btn:hover {
  background: #dbeafe;
}

/* 黑色设置按钮 */
.dark-btn {
  flex-shrink: 0;
  padding: 5px 14px;
  border: none;
  border-radius: 6px;
  background: #111827;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.12s;
}

.dark-btn:hover {
  opacity: 0.88;
}

/* ─── 说明板块 ─── */
.tips-card {
  padding: 18px;
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.tips-label {
  font-size: 13px;
  font-weight: 700;
  color: #b45309;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  margin-bottom: 12px;
}

.tips-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tips-list li {
  position: relative;
  padding-left: 14px;
  font-size: 12px;
  color: #92400e;
  line-height: 1.6;
}

.tips-list li::before {
  content: '·';
  position: absolute;
  left: 0;
  color: #d97706;
  font-weight: 900;
}

/* ─── 背景设置弹窗 ─── */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-panel {
  background: #fff;
  border-radius: 16px;
  padding: 28px 24px 22px;
  width: 340px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}

.modal-title {
  font-size: 16px;
  font-weight: 800;
  color: #111827;
  text-align: center;
  margin-bottom: 4px;
}

.preview-box {
  width: 100%;
  height: 160px;
  border-radius: 10px;
  border: 2px dashed #e2e8f0;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.preview-box.has-image {
  border-style: solid;
  border-color: #dbeafe;
  background: #fff;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-empty {
  font-size: 13px;
  color: #9ca3af;
}

.bg-actions {
  display: flex;
  gap: 8px;
  width: 100%;
}

.bg-upload-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 38px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}
.bg-upload-btn:hover { background: #dbeafe; }

.bg-reset-btn {
  flex: 1;
  height: 38px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}
.bg-reset-btn:hover { background: #f8fafc; }

.saved-msg {
  font-size: 12px;
  color: #16a34a;
  font-weight: 600;
  text-align: center;
  background: #f0fdf4;
  width: 100%;
  padding: 6px;
  border-radius: 6px;
}

.modal-btn {
  width: 100%;
  height: 42px;
  margin-top: 4px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.15s;
}
.modal-btn:hover:not(:disabled) { opacity: 0.92; }
.modal-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.modal-cancel-btn {
  width: 100%;
  height: 40px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}
.modal-cancel-btn:hover { background: #f8fafc; }

/* 弹窗动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-active .modal-panel,
.modal-leave-active .modal-panel {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.modal-enter-from,
.modal-leave-to { opacity: 0; }
.modal-enter-from .modal-panel,
.modal-leave-to .modal-panel {
  transform: scale(0.92);
  opacity: 0;
}
</style>
