<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import AppTabBar from '../components/AppTabBar.vue'
import { clearAuth, getUserInfo } from '../utils/auth'

const router = useRouter()
const userInfo = computed(() => getUserInfo() || {})
const displayName = computed(() => userInfo.value.realName || userInfo.value.username || '未登录')
const studentId = computed(() => userInfo.value.studentId || userInfo.value.personalNumber || '—')

const menuItems = [
  '我的消息',
  '我的课表',
  '会议日程',
  '我的活动',
  'AI 会话历史',
  '我的试卷',
]

function logout() {
  clearAuth()
  router.replace('/login')
}
</script>

<template>
  <div class="phone-shell">
    <main class="page">
      <header class="topbar">
        <h1>个人中心</h1>
      </header>

      <section class="profile-card card">
        <div class="avatar">{{ displayName.slice(0, 1).toUpperCase() }}</div>
        <div>
          <h2>{{ displayName }}</h2>
          <p class="muted">学号 {{ studentId }}</p>
        </div>
      </section>

      <section class="menu-card card">
        <button v-for="item in menuItems" :key="item" class="menu-row" type="button">
          <span>{{ item }}</span>
          <span class="arrow">›</span>
        </button>
      </section>

      <button class="logout-button" type="button" @click="logout">退出登录</button>
    </main>

    <AppTabBar />
  </div>
</template>

<style scoped>
.profile-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
}

.avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 8px;
  color: #ffffff;
  background: #2563eb;
  font-size: 24px;
  font-weight: 900;
}

h2 {
  margin: 0 0 6px;
  color: #111827;
  font-size: 20px;
}

.profile-card p {
  margin: 0;
}

.menu-card {
  margin-top: 16px;
  overflow: hidden;
}

.menu-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 52px;
  padding: 0 16px;
  border-bottom: 1px solid #eef2f7;
  color: #1f2937;
  background: #ffffff;
  text-align: left;
}

.menu-row:last-child {
  border-bottom: 0;
}

.arrow {
  color: #94a3b8;
  font-size: 24px;
}

.logout-button {
  width: 100%;
  min-height: 48px;
  margin-top: 16px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #dc2626;
  background: #ffffff;
  font-weight: 800;
}
</style>
