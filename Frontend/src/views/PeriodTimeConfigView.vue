<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const PERIODS = ref([
  { label: '1', start: '08:00', end: '08:45' },
  { label: '2', start: '08:55', end: '09:40' },
  { label: '3', start: '10:00', end: '10:45' },
  { label: '4', start: '10:55', end: '11:40' },
  { label: '5', start: '14:00', end: '14:45' },
  { label: '6', start: '14:55', end: '15:40' },
  { label: '7', start: '16:00', end: '16:45' },
  { label: '8', start: '16:55', end: '17:40' },
])

// TODO: 调用后端接口保存节次时间
// async function savePeriodTimesAPI(periods) {
//   await periodTimeApi.save(periods)
// }

function savePeriods() {
  // TODO: 调用后端接口
  alert('节次时间已保存')
}

function goBack() {
  router.back()
}
</script>

<template>
  <div class="page-shell">

    <header class="navbar">
      <button class="nav-back" type="button" @click="goBack">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <h1 class="nav-title">节次时间设置</h1>
      <div class="nav-placeholder"></div>
    </header>

    <main class="page-body">
      <section class="card">
        <div class="section-label">节次时间</div>

        <div class="period-list">
          <div v-for="p in PERIODS" :key="p.label" class="period-row">
            <span class="period-badge">第 {{ p.label }} 节</span>
            <div class="time-inputs">
              <input v-model="p.start" type="time" class="time-input" />
              <span class="time-sep">–</span>
              <input v-model="p.end" type="time" class="time-input" />
            </div>
          </div>
        </div>
      </section>
    </main>

    <footer class="bottom-bar">
      <button class="save-btn" type="button" @click="savePeriods">保存时间设置</button>
    </footer>

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

.page-body {
  flex: 1;
  padding: 14px 16px 100px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

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
  margin-bottom: 14px;
}

.period-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.period-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
}

.period-row:last-child {
  border-bottom: none;
}

.period-badge {
  font-size: 14px;
  font-weight: 700;
  color: #374151;
  min-width: 60px;
}

.time-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.time-input {
  width: 100px;
  height: 36px;
  padding: 0 8px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #1f2937;
  font-size: 14px;
  outline: none;
  text-align: center;
  transition: border-color 0.15s;
}

.time-input:focus {
  border-color: #2563eb;
  background: #fff;
}

.time-sep {
  color: #94a3b8;
  font-size: 13px;
}

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
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.25);
  transition: opacity 0.15s;
}

.save-btn:hover {
  opacity: 0.92;
}
</style>
