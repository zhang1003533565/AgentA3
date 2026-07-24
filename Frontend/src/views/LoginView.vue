<script setup>
import loginBg from '@/assets/login-bg.jpg'

import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { login } from '../api/user'
import { setToken, setUserInfo } from '../utils/auth'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const showPassword = ref(false)
const form = reactive({
  username: '',
  password: '',
})

function togglePassword() {
  showPassword.value = !showPassword.value
}

function goToRegister() {
  router.push({ name: 'register' })
}

async function handleLogin() {
  errorMessage.value = ''

  if (!form.username.trim()) {
    errorMessage.value = '请输入用户名'
    return
  }

  if (!form.password || form.password.length < 6) {
    errorMessage.value = '密码长度至少6位'
    return
  }

  loading.value = true
  try {
    const result = await login({
      username: form.username.trim(),
      password: form.password,
    })
    const user = result.data || {}

    setToken(user.token)
    setUserInfo({
      id: user.id,
      userId: user.id,
      username: user.username,
      role: user.role,
      phone: user.phone,
      realName: user.realName,
      college: user.college,
      major: user.major,
      className: user.className,
      personalNumber: user.personalNumber,
      studentId: user.personalNumber,
      avatar: user.avatar,
    })

    router.replace(String(route.query.redirect || '/home'))
  } catch (error) {
    errorMessage.value = error.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main
    class="login-page"
    :style="{ backgroundImage: `url(${loginBg})`, backgroundSize: 'cover', backgroundPosition: 'center', backgroundRepeat: 'no-repeat' }"
  >
    <div class="login-card">
      <div class="card-border-glow"></div>

      <div class="card-particles">
        <span class="particle p1"></span>
        <span class="particle p2"></span>
        <span class="particle p3"></span>
        <span class="particle p4"></span>
        <span class="particle p5"></span>
      </div>

      <div class="brand-area">
        <div class="brand-logo">
          <div class="logo-ring"></div>
          <svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
            <text class="logo-text" x="50%" y="50%" dominant-baseline="central" text-anchor="middle"
                  font-family="'Segoe UI', -apple-system, BlinkMacSystemFont, sans-serif"
                  font-size="26" font-weight="900" fill="white"
                  stroke="white" stroke-width="2"
                  style="paint-order: stroke fill;"
                  letter-spacing="1.5">A3</text>
          </svg>
        </div>
        <div class="brand-text-group">
          <h1 class="brand-title">A3 Campus</h1>
        </div>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label class="form-label">账号</label>
          <div class="input-wrapper">
            <input
              v-model="form.username"
              class="form-input"
              autocomplete="username"
              placeholder="请输入账号"
            />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">密码</label>
          <div class="input-wrapper">
            <input
              v-model="form.password"
              class="form-input"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
            <button
              type="button"
              class="toggle-password"
              @click="togglePassword"
            >
              <svg v-if="!showPassword" class="eye-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                <circle cx="12" cy="12" r="3"></circle>
              </svg>
              <svg v-else class="eye-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                <line x1="1" y1="1" x2="23" y2="23"></line>
              </svg>
              <span class="toggle-text">{{ showPassword ? '隐藏' : '显示' }}</span>
            </button>
          </div>
        </div>

        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

        <div class="button-group">
          <button
            class="submit-btn"
            :disabled="loading"
            type="submit"
          >
            <span v-if="loading" class="btn-loading"></span>
            <span class="btn-text">{{ loading ? '登录中...' : '进入校园助手' }}</span>
          </button>

          <button
            class="register-btn"
            type="button"
            @click="goToRegister"
          >
            注册新账号
          </button>
        </div>
      </form>
    </div>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  position: relative;
  overflow: hidden;
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: #ffffff;
  border-radius: 24px;
  padding: 44px 40px 36px;
  box-shadow: 0 25px 60px rgba(37, 99, 235, 0.12), 0 8px 24px rgba(37, 99, 235, 0.06);
  position: relative;
  z-index: 1;
  overflow: hidden;
  animation: card-reveal 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes card-reveal {
  0% {
    opacity: 0;
    transform: translateY(30px) scale(0.96);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.card-border-glow {
  position: absolute;
  inset: 0;
  border-radius: 24px;
  padding: 1.5px;
  background: linear-gradient(
    135deg,
    transparent 0%,
    rgba(59, 130, 246, 0.15) 15%,
    rgba(59, 130, 246, 0.4) 30%,
    rgba(37, 99, 235, 0.2) 50%,
    rgba(59, 130, 246, 0.4) 70%,
    rgba(59, 130, 246, 0.15) 85%,
    transparent 100%
  );
  background-size: 300% 300%;
  -webkit-mask:
    linear-gradient(#fff 0 0) content-box,
    linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  animation: border-flow 8s ease-in-out infinite;
  pointer-events: none;
}

@keyframes border-flow {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

.card-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
  border-radius: 24px;
}

.particle {
  position: absolute;
  width: 3px;
  height: 3px;
  background: #3b82f6;
  border-radius: 50%;
  opacity: 0;
  animation: particle-float 6s ease-in-out infinite;
}

.p1 { top: 12%; left: 15%; animation-delay: 0s; }
.p2 { top: 25%; right: 18%; animation-delay: 1.2s; }
.p3 { bottom: 30%; left: 20%; animation-delay: 2.4s; width: 2px; height: 2px; }
.p4 { top: 50%; right: 12%; animation-delay: 3.6s; width: 4px; height: 4px; }
.p5 { bottom: 15%; right: 25%; animation-delay: 4.8s; }

@keyframes particle-float {
  0% {
    opacity: 0;
    transform: translateY(0) scale(0.5);
  }
  20% {
    opacity: 0.6;
    transform: translateY(-8px) scale(1);
  }
  80% {
    opacity: 0.3;
    transform: translateY(-16px) scale(0.8);
  }
  100% {
    opacity: 0;
    transform: translateY(-24px) scale(0.5);
  }
}

.brand-area {
  text-align: center;
  margin-bottom: 40px;
  animation: fade-slide-up 0.6s ease-out 0.1s both;
}

@keyframes fade-slide-up {
  0% {
    opacity: 0;
    transform: translateY(16px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

.brand-logo {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 50%, #1d4ed8 100%);
  background-size: 200% 200%;
  border-radius: 16px;
  color: #ffffff;
  margin-bottom: 16px;
  box-shadow:
    0 8px 24px rgba(37, 99, 235, 0.3),
    0 0 0 0 rgba(59, 130, 246, 0.4);
  animation:
    logo-appear 0.8s cubic-bezier(0.34, 1.56, 0.64, 1) 0.2s both,
    logo-gradient 4s ease infinite 1s,
    logo-float 3s ease-in-out infinite 1s;
}

.brand-logo .logo-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100%;
  height: 100%;
  border: 2px solid rgba(59, 130, 246, 0.4);
  border-radius: 16px;
  transform: translate(-50%, -50%) scale(1);
  opacity: 0;
  animation: logo-ring 2s ease-out infinite 1s;
}

.brand-logo svg {
  width: 34px;
  height: 34px;
  animation: logo-rotate 8s linear infinite 1s;
}

.brand-logo .logo-text {
  animation: logo-text-pulse 2s ease-in-out infinite 1s;
}

@keyframes logo-appear {
  0% {
    opacity: 0;
    transform: scale(0.3) rotate(-15deg);
  }
  60% {
    transform: scale(1.1) rotate(3deg);
  }
  100% {
    opacity: 1;
    transform: scale(1) rotate(0);
  }
}

@keyframes logo-gradient {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

@keyframes logo-float {
  0%, 100% {
    transform: translateY(0);
    box-shadow:
      0 8px 24px rgba(37, 99, 235, 0.3),
      0 0 0 0 rgba(59, 130, 246, 0.4);
  }
  50% {
    transform: translateY(-3px);
    box-shadow:
      0 12px 30px rgba(37, 99, 235, 0.4),
      0 0 0 0 rgba(59, 130, 246, 0.3);
  }
}

@keyframes logo-ring {
  0% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0.6;
  }
  100% {
    transform: translate(-50%, -50%) scale(1.6);
    opacity: 0;
  }
}

@keyframes logo-rotate {
  0%, 100% {
    transform: rotate(0deg);
  }
  25% {
    transform: rotate(-2deg);
  }
  75% {
    transform: rotate(2deg);
  }
}

@keyframes logo-text-pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.85;
  }
}

.brand-text-group {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.brand-title {
  font-size: 34px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
  letter-spacing: 1px;
  line-height: 1.2;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  animation: field-enter 0.5s ease-out both;
}

.form-group:nth-child(1) { animation-delay: 0.3s; }
.form-group:nth-child(2) { animation-delay: 0.4s; }

@keyframes field-enter {
  0% {
    opacity: 0;
    transform: translateY(12px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  border: 1.5px solid #e5e7eb;
  border-radius: 12px;
  background: #f8fafc;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.input-wrapper:hover {
  border-color: #bfdbfe;
  background: #ffffff;
}

.input-wrapper:focus-within {
  border-color: #3b82f6;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.12);
}

.form-input {
  flex: 1;
  height: 46px;
  padding: 0 16px;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #111827;
  font-family: inherit;
  transition: all 0.2s ease;
}

.form-input::placeholder {
  color: #9ca3af;
}

.toggle-password {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  margin-right: 6px;
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.toggle-password:hover {
  color: #1d4ed8;
  background: rgba(59, 130, 246, 0.1);
}

.toggle-password:active {
  transform: scale(0.95);
}

.eye-icon {
  width: 16px;
  height: 16px;
  transition: transform 0.2s ease;
}

.toggle-password:hover .eye-icon {
  transform: scale(1.1);
}

.form-error {
  margin: 0;
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 10px;
  color: #dc2626;
  font-size: 13px;
  animation: error-shake 0.5s cubic-bezier(0.36, 0.07, 0.19, 0.97);
}

@keyframes error-shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-8px); }
  40% { transform: translateX(8px); }
  60% { transform: translateX(-5px); }
  80% { transform: translateX(5px); }
}

.button-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 6px;
  animation: field-enter 0.5s ease-out 0.5s both;
}

.submit-btn {
  height: 48px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
  cursor: pointer;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.3),
              0 1px 2px rgba(37, 99, 235, 0.2);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.submit-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s ease;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.4),
              0 2px 4px rgba(37, 99, 235, 0.3);
}

.submit-btn:hover:not(:disabled)::before {
  left: 100%;
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0) scale(0.98);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
}

.submit-btn:disabled {
  background: #bfdbfe;
  cursor: not-allowed;
  box-shadow: none;
}

.btn-loading {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.btn-text {
  position: relative;
  z-index: 1;
}

.register-btn {
  height: 44px;
  border: 1.5px solid #3b82f6;
  border-radius: 12px;
  background: transparent;
  color: #2563eb;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.register-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.08) 0%, rgba(37, 99, 235, 0.12) 100%);
  opacity: 0;
  transition: opacity 0.25s ease;
}

.register-btn:hover {
  background: transparent;
  border-color: #2563eb;
  color: #1d4ed8;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.2);
}

.register-btn:hover::before {
  opacity: 1;
}

.register-btn:active {
  transform: translateY(0) scale(0.98);
}
</style>
