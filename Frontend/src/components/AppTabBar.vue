<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { clearAuth, getUserInfo } from '../utils/auth'

const router = useRouter()
const showProfilePanel = ref(false)
const userInfo = computed(() => getUserInfo() || {})
const avatarUrl = computed(() => userInfo.value.avatar || '')
const displayName = computed(() => userInfo.value.realName || userInfo.value.username || '未登录')
const studentId = computed(() => userInfo.value.studentId || userInfo.value.personalNumber || '—')
const avatarText = computed(() => {
  return displayName.value.slice(0, 1).toUpperCase()
})

function toggleProfilePanel() {
  showProfilePanel.value = !showProfilePanel.value
}

function openProfileRoute(path) {
  showProfilePanel.value = false
  router.push(path)
}

function handleLogout() {
  showProfilePanel.value = false
  clearAuth()
  router.replace('/login')
}

const shortcutItems = [
  { label: '我的消息', to: '/mine/messages' },
  { label: '我的课表', to: '/mine/schedule' },
  { label: '会议日程', to: '/mine/meeting-schedule' },
  { label: '我的活动', to: '/mine/activities' },
  { label: 'AI 会话历史', to: '/mine/ai-history' },
  { label: '我的试卷', to: '/mine/papers' },
]
</script>

<template>
  <nav class="app-tab-nav" aria-label="主导航">
    <div class="app-tab-nav__inner">
      <RouterLink to="/home">首页</RouterLink>
      <RouterLink to="/map">校园地图</RouterLink>
      <RouterLink to="/meetings">会议</RouterLink>
      <RouterLink to="/ai">AI 助手</RouterLink>
      <RouterLink to="/ai-tools">AI 工具</RouterLink>
      <RouterLink to="/mine">我的页面</RouterLink>
      <RouterLink to="/resume">我的简历</RouterLink>
    </div>
    <div class="app-tab-nav__profile">
      <button class="app-tab-nav__avatar" type="button" aria-label="个人头像" @click="toggleProfilePanel">
        <img v-if="avatarUrl" :src="avatarUrl" alt="" />
        <span v-else>{{ avatarText }}</span>
      </button>
      <transition name="profile-panel">
        <div v-if="showProfilePanel" class="app-tab-nav__panel">
          <section class="app-tab-nav__panel-section">
            <p class="app-tab-nav__name">{{ displayName }}</p>
            <p class="app-tab-nav__student">学号 {{ studentId }}</p>
          </section>

          <section class="app-tab-nav__panel-section app-tab-nav__panel-section--list">
            <button
              v-for="item in shortcutItems"
              :key="item.to"
              class="app-tab-nav__panel-row"
              type="button"
              @click="openProfileRoute(item.to)"
            >
              <span>{{ item.label }}</span>
              <span class="app-tab-nav__panel-arrow">›</span>
            </button>
          </section>

          <section class="app-tab-nav__panel-section">
            <button class="app-tab-nav__logout" type="button" @click="handleLogout">
              退出登录
            </button>
          </section>
        </div>
      </transition>
    </div>
  </nav>
</template>
