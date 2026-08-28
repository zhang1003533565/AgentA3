<script setup>
import { useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'

const router = useRouter()
</script>

<template>
  <div class="watermark-page">
    <AppTabBar />

    <main class="watermark-content">
      <header class="watermark-heading">
        <button class="back-button" type="button" aria-label="返回 AI 工具" @click="router.push('/ai-tools')">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="m15 5-7 7 7 7" />
          </svg>
        </button>
        <h1>水印工具</h1>
        <span class="heading-spacer" aria-hidden="true"></span>
      </header>

      <section class="watermark-main-grid" aria-label="水印功能">
        <article
          class="watermark-card watermark-card--interactive"
          role="button"
          tabindex="0"
          @click="router.push('/ai-original/add')"
          @keydown.enter="router.push('/ai-original/add')"
        >
          <div class="watermark-icon-box" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <rect x="3.5" y="4" width="17" height="16" rx="2" />
              <circle cx="8.5" cy="9" r="1.5" />
              <path d="m5.5 17 4.5-4 3 2.5 2-2 3.5 3.5" />
            </svg>
          </div>
          <h2>图片加水印</h2>
          <p>上传图片，添加多类型水印并导出</p>
          <span class="watermark-action">立即制作</span>
        </article>

        <article class="watermark-card">
          <div class="watermark-icon-box" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path d="m14.5 5.5 4 4" />
              <path d="m13.2 6.8-6.8 6.8-1.2 4.8 4.8-1.2 6.8-6.8" />
              <path d="m5 20 14-14" />
            </svg>
          </div>
          <h2>图片去水印</h2>
          <p>标记水印区域，智能修复并导出</p>
          <span class="watermark-action">立即制作</span>
        </article>
      </section>

      <section class="common-actions" aria-labelledby="common-actions-title">
        <h2 id="common-actions-title">常用操作</h2>
        <div class="common-actions-grid">
          <article
            class="common-action-card common-action-card--interactive"
            role="button"
            tabindex="0"
            @click="router.push('/ai-original/history')"
            @keydown.enter="router.push('/ai-original/history')"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="8" />
              <path d="M12 7v5l3 2" />
            </svg>
            <span>历史记录</span>
          </article>
          <article
            class="common-action-card common-action-card--interactive"
            role="button"
            tabindex="0"
            @click="router.push('/ai-original/batch')"
            @keydown.enter="router.push('/ai-original/batch')"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <rect x="4" y="5" width="11" height="14" rx="1.5" />
              <path d="M8 3h11a1 1 0 0 1 1 1v14M8 9h4M8 13h4" />
            </svg>
            <span>批量处理</span>
          </article>
          <article
            class="common-action-card common-action-card--interactive"
            role="button"
            tabindex="0"
            @click="router.push('/ai-original/help')"
            @keydown.enter="router.push('/ai-original/help')"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="9" />
              <path d="M9.7 9a2.4 2.4 0 1 1 4.1 1.7c-1.1 1-1.8 1.2-1.8 2.8M12 17h.01" />
            </svg>
            <span>使用帮助</span>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.watermark-page {
  min-height: 100vh;
  padding-top: 60px;
  color: #1f2937;
  background: #f4f7fb;
}

.watermark-content {
  width: min(1280px, calc(100% - 40px));
  margin: 0 auto;
  padding: 24px 0 48px;
}

.watermark-heading {
  display: grid;
  grid-template-columns: 40px 1fr 40px;
  align-items: center;
  min-height: 48px;
  margin-bottom: 20px;
}

.watermark-heading h1 {
  margin: 0;
  color: #17233a;
  font-size: 24px;
  text-align: center;
}

.back-button {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border: 0;
  border-radius: 8px;
  color: #334155;
  background: transparent;
}

.back-button:hover {
  background: #e8eef5;
}

.back-button svg {
  width: 22px;
  height: 22px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.watermark-main-grid,
.common-actions-grid {
  display: grid;
  gap: 20px;
}

.watermark-main-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.watermark-card {
  display: flex;
  min-height: 300px;
  flex-direction: column;
  padding: 24px;
  border: 1px solid #e1e7ed;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(30, 43, 76, .04);
}

.watermark-card--interactive {
  cursor: pointer;
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.watermark-card--interactive:hover,
.watermark-card--interactive:focus-visible {
  border-color: #aebed0;
  box-shadow: 0 12px 28px rgba(30, 43, 76, .1);
  outline: none;
  transform: translateY(-2px);
}

.watermark-icon-box {
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
  border-radius: 12px;
  color: #1f2937;
  background: #f4f6f8;
}

.watermark-icon-box svg,
.common-action-card svg {
  width: 28px;
  height: 28px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.watermark-card h2 {
  margin: 24px 0 0;
  color: #17233a;
  font-size: 20px;
}

.watermark-card p {
  margin: 10px 0 0;
  color: #718096;
  font-size: 14px;
  line-height: 1.6;
}

.watermark-action {
  display: grid;
  min-height: 44px;
  margin-top: auto;
  place-items: center;
  border-radius: 9px;
  color: #fff;
  background: #1a202c;
  font-size: 14px;
  font-weight: 700;
}

.common-actions {
  margin-top: 42px;
}

.common-actions > h2 {
  margin: 0 0 18px;
  color: #17233a;
  font-size: 20px;
}

.common-actions-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.common-action-card {
  display: flex;
  min-height: 180px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  border: 1px solid #e1e7ed;
  border-radius: 10px;
  color: #334155;
  background: #fff;
  box-shadow: 0 8px 24px rgba(30, 43, 76, .04);
}

.common-action-card--interactive {
  cursor: pointer;
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.common-action-card--interactive:hover,
.common-action-card--interactive:focus-visible {
  border-color: #aebed0;
  box-shadow: 0 12px 28px rgba(30, 43, 76, .1);
  outline: none;
  transform: translateY(-2px);
}

.common-action-card svg {
  width: 30px;
  height: 30px;
  color: #718096;
}

.common-action-card span {
  font-size: 14px;
  font-weight: 700;
}

@media (max-width: 720px) {
  .watermark-content {
    width: min(100% - 24px, 560px);
    padding-top: 16px;
  }

  .watermark-main-grid,
  .common-actions-grid {
    grid-template-columns: 1fr;
  }

  .watermark-card {
    min-height: 260px;
  }

  .common-action-card {
    min-height: 120px;
  }
}
</style>
