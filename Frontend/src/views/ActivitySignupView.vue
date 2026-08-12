<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getActivityDetail } from '../api/activity'
import { getUserInfo } from '../utils/auth'
import { mockActivities } from '../mock/activityData'

const route = useRoute()
const router = useRouter()
const activity = ref(null)
const loading = ref(true)
const submitting = ref(false)
const showSuccess = ref(false)

const userInfo = computed(() => getUserInfo() || {})

const form = ref({
  name: '',
  studentId: '',
  college: '',
  agreed: false,
})

async function load() {
  loading.value = true
  try {
    const id = route.params.activityId
    const res = await getActivityDetail(id)
    activity.value = res.data
  } catch {
    const id = Number(route.params.activityId)
    activity.value = mockActivities.find(a => a.id === id) || mockActivities[0]
  } finally {
    loading.value = false
    fillUserInfo()
  }
}

function fillUserInfo() {
  const u = userInfo.value
  form.value.name = u.realName || u.username || ''
  form.value.studentId = u.studentId || u.personalNumber || ''
  form.value.college = u.college || u.collegeName || ''
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(String(dateStr).replace(' ', 'T'))
  if (isNaN(d.getTime())) return dateStr
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

function closeModal() {
  router.push({
    name: 'activity-detail',
    params: { activityId: route.params.activityId },
  })
}

function handleSubmit() {
  if (!form.value.agreed) return
  submitting.value = true
  setTimeout(() => {
    submitting.value = false
    showSuccess.value = true
  }, 800)
}

function confirmSuccess() {
  router.push({
    name: 'activity-detail',
    params: { activityId: route.params.activityId },
  })
}

onMounted(load)
</script>

<template>
  <div class="signup-page">
    <AppTabBar />

    <main class="signup-main">
      <div class="bg-layer" aria-hidden="true">
        <div class="bg-mask"></div>
      </div>

      <div v-if="loading" class="loading-state">
        <div class="loader"></div>
        <p>加载中...</p>
      </div>

      <template v-else>
        <div class="modal-overlay">
          <div class="modal-card" role="dialog" aria-modal="true">
            <button class="close-btn" @click="closeModal" aria-label="关闭">关闭</button>

            <div class="modal-header">
              <h2>确认报名</h2>
              <p class="modal-subtitle">请核对您的个人信息，确认无误后提交</p>
            </div>

            <template v-if="activity">
            <div class="activity-card">
              <div class="activity-thumb">
                <img v-if="activity.coverImage" :src="activity.coverImage" :alt="activity.title" />
                <div v-else class="thumb-placeholder">📅</div>
              </div>
              <div class="activity-info">
                <h3 class="activity-title">{{ activity.title }}</h3>
                <p class="activity-meta">
                  <span class="meta-item">📍 {{ activity.location || '线上活动' }}</span>
                </p>
                <p class="activity-meta">
                  <span class="meta-item">🕐 {{ formatDate(activity.startTime) }}</span>
                </p>
              </div>
            </div>

            <form class="signup-form" @submit.prevent="handleSubmit">
              <div class="form-group">
                <label for="name">姓名</label>
                <input
                  id="name"
                  v-model="form.name"
                  type="text"
                  placeholder="请输入姓名"
                  required
                />
              </div>

              <div class="form-group">
                <label for="studentId">学号</label>
                <input
                  id="studentId"
                  v-model="form.studentId"
                  type="text"
                  placeholder="请输入学号"
                  required
                />
              </div>

              <div class="form-group">
                <label for="college">所属学院</label>
                <input
                  id="college"
                  v-model="form.college"
                  type="text"
                  placeholder="请输入所属学院"
                  required
                />
              </div>

              <label class="agreement">
                <input
                  v-model="form.agreed"
                  type="checkbox"
                />
                <span>我已阅读并同意<a href="#">《校园活动参与规范》</a>及相关隐私条款</span>
              </label>

              <button
                type="submit"
                :class="['submit-btn', { loading: submitting, disabled: !form.agreed }]"
                :disabled="submitting || !form.agreed"
              >
                <span v-if="submitting" class="btn-loading">提交中...</span>
                <span v-else>确认提交</span>
              </button>
            </form>
          </template>
        </div>
      </div>

      <div v-if="showSuccess" class="success-dialog-overlay">
        <div class="success-dialog">
          <div class="success-icon-wrap">
            <div class="success-check-icon">✓</div>
          </div>
          <h3 class="success-title">报名成功</h3>
          <p class="success-desc">您已成功报名，可在"我的活动"中查看</p>
          <button class="success-confirm-btn" @click="confirmSuccess">好的</button>
        </div>
      </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.signup-page {
  min-height: 100vh;
  background: #f8fafc;
}

.signup-main {
  position: relative;
  min-height: 100vh;
  padding-top: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.bg-layer {
  position: absolute;
  inset: 0;
  background: #f8fafc;
  z-index: 0;
}

.bg-mask {
  display: none;
}

.loading-state {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px;
  color: #64748b;
}

.loader {
  width: 36px;
  height: 36px;
  border: 3px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 14px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.modal-overlay {
  position: relative;
  z-index: 1;
  width: min(480px, calc(100% - 32px));
  animation: modalIn 0.35s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes modalIn {
  from {
    opacity: 0;
    transform: translateY(24px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal-card {
  position: relative;
  background: #ffffff;
  border-radius: 16px;
  padding: 32px 28px 28px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.12);
  max-height: calc(100vh - 100px);
  overflow-y: auto;
}

.close-btn {
  position: absolute;
  top: 16px;
  right: 18px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.2s;
}

.close-btn:hover {
  color: #334155;
  background: #f1f5f9;
}

.modal-header {
  margin-bottom: 20px;
}

.modal-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 6px;
}

.modal-subtitle {
  font-size: 13px;
  color: #64748b;
  margin: 0;
}

.success-dialog-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(4px);
  animation: overlayFadeIn 0.25s ease-out;
}

@keyframes overlayFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.success-dialog {
  width: min(360px, calc(100% - 48px));
  background: #ffffff;
  border-radius: 16px;
  padding: 36px 28px 28px;
  text-align: center;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
  animation: dialogPop 0.35s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes dialogPop {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.success-icon-wrap {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #22c55e, #16a34a);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  box-shadow: 0 8px 24px rgba(34, 197, 94, 0.3);
}

.success-check-icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #ffffff;
  color: #16a34a;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: checkPop 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes checkPop {
  0% { transform: scale(0); }
  60% { transform: scale(1.15); }
  100% { transform: scale(1); }
}

.success-title {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 8px;
}

.success-desc {
  font-size: 14px;
  color: #64748b;
  margin: 0 0 24px;
  line-height: 1.6;
}

.success-confirm-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.3);
}

.success-confirm-btn:hover {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(59, 130, 246, 0.4);
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.activity-card {
  display: flex;
  gap: 14px;
  padding: 14px;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  margin-bottom: 24px;
}

.activity-thumb {
  width: 72px;
  height: 72px;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
  background: #e2e8f0;
}

.activity-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.activity-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.activity-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-meta {
  font-size: 12px;
  color: #64748b;
  margin: 0;
  display: flex;
  gap: 12px;
}

.meta-item {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.signup-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
}

.form-group input {
  padding: 10px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #0f172a;
  background: #ffffff;
  transition: border-color 0.2s, box-shadow 0.2s;
  outline: none;
}

.form-group input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.form-group input::placeholder {
  color: #94a3b8;
}

.agreement {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  line-height: 1.5;
}

.agreement input[type="checkbox"] {
  width: 16px;
  height: 16px;
  margin-top: 2px;
  accent-color: #2563eb;
  cursor: pointer;
  flex-shrink: 0;
}

.agreement a {
  color: #2563eb;
  text-decoration: none;
}

.agreement a:hover {
  text-decoration: underline;
}

.submit-btn {
  width: 100%;
  padding: 13px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.3);
  margin-top: 4px;
}

.submit-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(59, 130, 246, 0.4);
}

.submit-btn:disabled {
  background: #94a3b8;
  cursor: not-allowed;
  box-shadow: none;
}

.submit-btn.loading {
  pointer-events: none;
}

.btn-loading {
  display: inline-block;
}

@media (max-width: 520px) {
  .modal-card {
    padding: 24px 20px 20px;
    border-radius: 14px;
  }

  .modal-header h2 {
    font-size: 20px;
  }

  .activity-thumb {
    width: 60px;
    height: 60px;
  }

  .activity-title {
    font-size: 14px;
  }
}
</style>
