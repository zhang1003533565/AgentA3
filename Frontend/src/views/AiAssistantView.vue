<script setup>
import AppTabBar from '../components/AppTabBar.vue'
</script>

<template>
  <div class="phone-shell">
    <AppTabBar />

    <main class="page">
      <header class="topbar">
        <h1>AI助手</h1>
      </header>

      <section class="assistant-card card">
        <h2>校园 AI 助手</h2>
        <p class="muted">这里先放一个简单入口，后续可以接小程序里的 AI 对话、创作、会议纪要等接口。</p>
        <button class="primary-button" type="button">开始对话</button>
      </section>

      <section class="feature-list card">
        <div>
          <strong>智能问答</strong>
          <span>咨询校园服务与学习问题</span>
        </div>
        <div>
          <strong>内容创作</strong>
          <span>生成文案、活动方案与总结</span>
        </div>
        <div>
          <strong>会议助手</strong>
          <span>整理会议纪要与待办事项</span>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.assistant-card {
  display: grid;
  gap: 14px;
  padding: 20px;
}

.assistant-card h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
}

.assistant-card p {
  margin: 0;
  line-height: 1.7;
}

.feature-list {
  display: grid;
  margin-top: 16px;
  overflow: hidden;
}

.feature-list div {
  display: grid;
  gap: 4px;
  padding: 15px 16px;
  border-bottom: 1px solid #eef2f7;
}

.feature-list div:last-child {
  border-bottom: 0;
}

.feature-list strong {
  color: #1f2937;
}

.feature-list span {
  color: #64748b;
  font-size: 14px;
}
</style>

