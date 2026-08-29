<script setup>
import { useRouter } from 'vue-router'
import AppTabBar from '../AppTabBar.vue'

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  backTo: { type: String, default: '/ai-tools' },
  backLabel: { type: String, default: '返回 AI 工具' },
  fullWidth: { type: Boolean, default: false },
})

const router = useRouter()

function goBack() {
  router.push(props.backTo)
}
</script>

<template>
  <div class="feature-page">
    <AppTabBar />
    <main class="ai-studio-page" :class="{ 'ai-studio-page--full': fullWidth }">
      <header class="ai-studio-page__head">
        <button type="button" class="ai-studio-page__back" @click="goBack">← {{ backLabel }}</button>
        <div class="ai-studio-page__intro">
          <span>AI STUDIO</span>
          <h1>{{ title }}</h1>
          <p v-if="subtitle">{{ subtitle }}</p>
        </div>
        <div class="ai-studio-page__actions">
          <slot name="actions" />
        </div>
      </header>
      <div class="ai-studio-page__body">
        <slot />
      </div>
    </main>
  </div>
</template>

<style scoped>
.ai-studio-page {
  width: min(1680px, calc(100% - 40px));
  margin: 0 auto;
  padding: 20px 0 40px;
}

.ai-studio-page--full {
  width: min(1760px, calc(100% - 32px));
}

.ai-studio-page__head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 16px;
  margin-bottom: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e3e9ef;
}

.ai-studio-page__back {
  padding: 8px 12px;
  border: 1px solid #d8e3ec;
  border-radius: 8px;
  color: #41617f;
  background: #f8fbfd;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

.ai-studio-page__back:hover {
  background: #edf4fa;
}

.ai-studio-page__intro > span {
  display: block;
  color: #6f8398;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 1.2px;
}

.ai-studio-page__intro h1 {
  margin: 6px 0 4px;
  color: #1f3852;
  font-size: 26px;
}

.ai-studio-page__intro p {
  margin: 0;
  color: #718096;
  font-size: 14px;
}

.ai-studio-page__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.ai-studio-page__body {
  min-height: calc(100vh - 180px);
}

@media (max-width: 760px) {
  .ai-studio-page {
    width: min(100% - 24px, 680px);
  }

  .ai-studio-page__head {
    grid-template-columns: 1fr;
  }
}
</style>
