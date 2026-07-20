<script setup>
import { computed } from 'vue'

import AppTabBar from '../components/AppTabBar.vue'
import { getUserInfo } from '../utils/auth'

const userInfo = computed(() => getUserInfo() || {})
const displayName = computed(() => userInfo.value.realName || userInfo.value.username || '我的简历')

const resumeSections = [
  { title: '基本信息', text: '姓名、学号、学院、专业等信息' },
  { title: '教育经历', text: '学习经历与在校成果' },
  { title: '项目经历', text: '课程项目、实习项目或校园实践' },
  { title: '技能特长', text: '专业技能、证书与个人优势' },
]
</script>

<template>
  <div class="phone-shell">
    <AppTabBar />

    <main class="page">
      <header class="topbar">
        <h1>我的简历</h1>
      </header>

      <section class="resume-head card">
        <div class="resume-avatar">{{ displayName.slice(0, 1).toUpperCase() }}</div>
        <div>
          <h2>{{ displayName }}</h2>
          <p class="muted">{{ userInfo.college || '学院待完善' }} · {{ userInfo.major || '专业待完善' }}</p>
        </div>
      </section>

      <section class="resume-list">
        <article v-for="section in resumeSections" :key="section.title" class="resume-card card">
          <h3>{{ section.title }}</h3>
          <p>{{ section.text }}</p>
        </article>
      </section>
    </main>
  </div>
</template>

<style scoped>
.resume-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
}

.resume-avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 8px;
  color: #ffffff;
  background: #0f766e;
  font-size: 24px;
  font-weight: 900;
}

h2 {
  margin: 0 0 6px;
  color: #111827;
  font-size: 20px;
}

.resume-head p {
  margin: 0;
}

.resume-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.resume-card {
  padding: 16px;
}

.resume-card h3 {
  margin: 0 0 8px;
  color: #111827;
  font-size: 17px;
}

.resume-card p {
  margin: 0;
  color: #64748b;
  line-height: 1.6;
}
</style>
