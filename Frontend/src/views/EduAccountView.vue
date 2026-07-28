<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// ─── 表单状态 ──────────────────────────────────────────────────────────
const studentId = ref('')
const password = ref('')
const showPwd = ref(false)
const saving = ref(false)
const showSuccess = ref(false)

// ─── 保存账号 ──────────────────────────────────────────────────────────
// TODO: 调用后端接口保存教务账号
// async function saveEduAccountAPI(payload) {
//   const res = await eduAccountApi.save({
//     studentId: payload.studentId,
//     password: payload.password,   // 建议前端加密后传输
//   })
//   return res.data
// }

async function saveAccount() {
  if (!studentId.value.trim()) {
    alert('请输入教务系统学号')
    return
  }
  if (!password.value) {
    alert('请输入教务系统密码')
    return
  }

  saving.value = true
  try {
    // TODO: 调用后端接口
    // await saveEduAccountAPI({ studentId: studentId.value, password: password.value })

    // 模拟请求延迟
    await new Promise(r => setTimeout(r, 600))
    showSuccess.value = true
  } catch {
    alert('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

function closeSuccess() {
  showSuccess.value = false
  router.back()
}

// ─── 导航 ──────────────────────────────────────────────────────────────
function goBack() {
  router.back()
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
      <h1 class="nav-title">教务账号设置</h1>
      <div class="nav-placeholder"></div>
    </header>

    <main class="page-body">

      <!-- ① 教务账号区域 -->
      <section class="card account-card">
        <div class="card-head-row">
          <span class="section-label">教务账号</span>
          <span class="status-badge">未设置</span>
        </div>
        <p class="hint-text">
          设置教务账号后，可从教务系统自动导入课表，不影响已手动创建的学期列表。
        </p>

        <!-- 学号 -->
        <div class="form-group">
          <label class="form-label">教务系统学号</label>
          <input
            v-model="studentId"
            type="text"
            class="form-input"
            placeholder="请输入学号"
            autocomplete="off"
          />
        </div>

        <!-- 密码 -->
        <div class="form-group">
          <label class="form-label">教务系统密码</label>
          <div class="pwd-row">
            <input
              v-model="password"
              :type="showPwd ? 'text' : 'password'"
              class="form-input pwd-input"
              placeholder="请输入密码"
              autocomplete="off"
            />
            <button
              class="toggle-pwd-btn"
              type="button"
              @click="showPwd = !showPwd"
            >{{ showPwd ? '隐藏' : '显示' }}</button>
          </div>
        </div>
      </section>

      <!-- ② 说明板块 -->
      <section class="card tips-card">
        <div class="section-label">说明</div>
        <ul class="tips-list">
          <li>教务账号仅用于对接学校教务系统，自动拉取课程表数据，不会用于其他任何用途。</li>
          <li>密码将在提交时进行加密处理，并以密文形式存储于服务器，保障账号安全。</li>
          <li>若修改了教务系统密码，请及时在此页面更新，以免导入失败。</li>
        </ul>
      </section>

    </main>

    <!-- 底部保存按钮 -->
    <footer class="bottom-bar">
      <button
        class="save-btn"
        type="button"
        :disabled="saving"
        @click="saveAccount"
      >{{ saving ? '保存中…' : '保存账号' }}</button>
    </footer>

    <!-- 成功提示弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showSuccess" class="modal-overlay" @click.self="closeSuccess">
          <div class="modal-panel">
            <div class="modal-icon">
              <svg viewBox="0 0 48 48" width="44" height="44" fill="none">
                <circle cx="24" cy="24" r="22" fill="#dcfce7" stroke="#16a34a" stroke-width="2" />
                <polyline points="14,25 21,32 34,18" stroke="#16a34a" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" fill="none" />
              </svg>
            </div>
            <div class="modal-title">保存成功</div>
            <div class="modal-desc">教务账号已加密保存，可前往学期管理页面导入课表。</div>
            <button class="modal-btn" type="button" @click="closeSuccess">知道了</button>
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
  padding: 14px 16px 110px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ─── 通用卡片 ─── */
.card {
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.section-label {
  font-size: 13px;
  font-weight: 700;
  color: #94a3b8;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

/* ─── ① 教务账号 ─── */
.card-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.status-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 20px;
  background: #fef2f2;
  color: #dc2626;
}

.hint-text {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.6;
  margin: 0 0 18px;
}

.form-group {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  height: 42px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #1f2937;
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s, background 0.15s;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: #2563eb;
  background: #fff;
}

.pwd-row {
  display: flex;
  gap: 8px;
}

.pwd-input {
  flex: 1;
}

.toggle-pwd-btn {
  flex-shrink: 0;
  padding: 0 12px;
  height: 42px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}

.toggle-pwd-btn:hover {
  background: #fff;
  border-color: #cbd5e1;
}

/* ─── ② 说明板块 ─── */
.tips-card {
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.tips-card .section-label {
  color: #b45309;
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

/* ─── 底部保存按钮 ─── */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 14px 16px 22px;
  background: rgba(244, 247, 251, 0.92);
  backdrop-filter: blur(8px);
  border-top: 1px solid #eef2f7;
  z-index: 50;
}

.save-btn {
  width: 100%;
  height: 48px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.15s;
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.25);
}

.save-btn:hover:not(:disabled) {
  opacity: 0.92;
}

.save-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* ─── 成功弹窗 ─── */
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
  padding: 32px 28px 24px;
  width: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}

.modal-icon {
  margin-bottom: 4px;
}

.modal-title {
  font-size: 17px;
  font-weight: 800;
  color: #111827;
}

.modal-desc {
  font-size: 13px;
  color: #6b7280;
  text-align: center;
  line-height: 1.6;
}

.modal-btn {
  width: 100%;
  height: 42px;
  margin-top: 8px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.15s;
}

.modal-btn:hover {
  opacity: 0.92;
}

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
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-panel,
.modal-leave-to .modal-panel {
  transform: scale(0.92);
  opacity: 0;
}
</style>
