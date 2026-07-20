<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { login } from '../api/user'
import { setToken, setUserInfo } from '../utils/auth'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({
  username: '',
  password: '',
})

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
  <main class="page page--center login-page">
    <section class="login-card card">
      <div class="brand-mark">A3</div>
      <h1>校园助手登录</h1>
      <p class="muted">登录后查看校园服务、通知与个人中心</p>

      <form class="login-form" @submit.prevent="handleLogin">
        <label>
          <span>用户名</span>
          <input v-model="form.username" autocomplete="username" placeholder="请输入用户名" />
        </label>

        <label>
          <span>密码</span>
          <input
            v-model="form.password"
            autocomplete="current-password"
            placeholder="请输入密码"
            type="password"
          />
        </label>

        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

        <button class="primary-button" :disabled="loading" type="submit">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  background: linear-gradient(135deg, #eff6ff, #ffffff 48%, #e0f2fe);
}

.login-card {
  width: min(100%, 390px);
  padding: 28px 22px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 8px;
  color: #ffffff;
  background: #2563eb;
  font-weight: 900;
  letter-spacing: 0;
}

h1 {
  margin: 18px 0 8px;
  color: #111827;
  font-size: 26px;
}

.login-form {
  display: grid;
  gap: 16px;
  margin-top: 24px;
}

label {
  display: grid;
  gap: 8px;
  color: #374151;
  font-size: 14px;
  font-weight: 700;
}

input {
  width: 100%;
  min-height: 46px;
  padding: 0 12px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  outline: none;
  background: #f9fbff;
}

input:focus {
  border-color: #2563eb;
  background: #ffffff;
}

.form-error {
  margin: 0;
  color: #dc2626;
  font-size: 14px;
}
</style>

