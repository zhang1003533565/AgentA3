<script setup>
import { ref, onMounted } from 'vue'
import AppTabBar from '../components/AppTabBar.vue'
import { getDiscountActivityList, getDiscountActivityDetail, favoriteActivity, unfavoriteActivity } from '../api/discount'

const loading = ref(true)
const items = ref([])
const keyword = ref('')
const selected = ref(null)
const page = ref(1)
const pageSize = 9
const total = ref(0)

const STATUS_MAP = {
  0: { text: '未开始', cls: 'badge-default' },
  1: { text: '进行中', cls: 'badge-active' },
  2: { text: '已领完', cls: 'badge-warning' },
  3: { text: '已结束', cls: 'badge-ended' },
  4: { text: '已下架', cls: 'badge-offline' },
}

const STATUS_ORDER = { 1: 0, 0: 1, 2: 2, 3: 3, 4: 4 }

function resolveList(raw) {
  let records
  if (raw?.data?.records) {
    total.value = raw.data.total || 0
    records = raw.data.records
  } else if (raw?.records) {
    total.value = raw.total || 0
    records = raw.records
  } else if (Array.isArray(raw)) {
    total.value = raw.length
    records = raw
  } else {
    total.value = 0
    return []
  }
  records.sort((a, b) => (STATUS_ORDER[a.status] ?? 9) - (STATUS_ORDER[b.status] ?? 9))
  return records
}

async function load() {
  loading.value = true
  try {
    items.value = resolveList(await getDiscountActivityList({
      current: page.value, size: pageSize,
      keyword: keyword.value || undefined,
    }))
  } catch { items.value = [] }
  loading.value = false
}

function search() {
  page.value = 1
  load()
}

function goToPage(p) {
  page.value = p
  load()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function open(item) {
  try {
    const res = await getDiscountActivityDetail(item.id)
    selected.value = res?.data || res
  } catch { selected.value = item }
}

async function toggleFav(item) {
  try {
    if (item.isFavorited) {
      await unfavoriteActivity(item.id)
      item.isFavorited = false
    } else {
      await favoriteActivity(item.id)
      item.isFavorited = true
    }
    if (selected.value) selected.value.isFavorited = item.isFavorited
  } catch (e) { alert(e.message) }
}

function fmt(t) {
  if (!t) return ''
  const d = t.replace('T', ' ')
  return d.length >= 16 ? d.slice(0, 16) : d
}

onMounted(load)
</script>

<template>
  <div class="discount-page">
    <AppTabBar />

    <main class="discount-container">
      <header class="discount-heading">
        <div class="discount-heading__left">
          <h1>校园优惠</h1>
          <p>精选校园周边商家优惠券，线下领取享折扣</p>
        </div>
        <div class="discount-search">
          <input
            v-model="keyword"
            type="text"
            placeholder="搜索商家名称..."
            @keyup.enter="search"
          />
          <button class="discount-search__btn" @click="search">搜索</button>
        </div>
      </header>

      <div v-if="loading" class="discount-empty">正在加载优惠活动…</div>

      <div v-else-if="!items.length" class="discount-empty">
        <div class="discount-empty__icon">🎫</div>
        <p>暂无优惠活动</p>
      </div>

      <div v-else class="discount-grid">
        <button
          v-for="item in items"
          :key="item.id"
          class="discount-card"
          type="button"
          @click="open(item)"
        >
          <img
            v-if="item.coverImage"
            :src="item.coverImage"
            alt=""
            class="discount-card__img"
          />
          <div v-else class="discount-card__placeholder">🎫</div>

          <span
            v-if="item.status !== undefined"
            :class="['discount-badge', STATUS_MAP[item.status]?.cls]"
          >
            {{ STATUS_MAP[item.status]?.text || '未知' }}
          </span>

          <span class="discount-card__merchant">{{ item.merchantName || '校园商家' }}</span>
          <h3 class="discount-card__title">{{ item.title }}</h3>
        </button>
      </div>

      <div v-if="total > 0" class="discount-pagination">
        <button
          class="discount-pagination__btn"
          :disabled="page === 1"
          @click="goToPage(page - 1)"
        >
          ← 上一页
        </button>
        <span class="discount-pagination__info">
          第 {{ page }} / {{ Math.ceil(total / pageSize) }} 页（共 {{ total }} 个活动）
        </span>
        <button
          class="discount-pagination__btn"
          :disabled="page >= Math.ceil(total / pageSize)"
          @click="goToPage(page + 1)"
        >
          下一页 →
        </button>
      </div>
    </main>

    <!-- Detail Modal -->
    <div
      v-if="selected"
      class="discount-overlay"
      @click.self="selected = null"
    >
      <div class="discount-detail">
        <button class="discount-detail__back" @click="selected = null">
          ← 返回
        </button>

        <img
          v-if="selected.coverImage"
          :src="selected.coverImage"
          alt=""
          class="discount-detail__img"
        />
        <div v-else class="discount-detail__img discount-detail__img--empty">🎫</div>

        <span
          v-if="selected.status !== undefined"
          :class="['discount-badge', STATUS_MAP[selected.status]?.cls]"
          style="margin-top: 12px; display: inline-flex;"
        >
          {{ STATUS_MAP[selected.status]?.text || '未知' }}
        </span>

        <h2>{{ selected.title }}</h2>

        <div class="discount-detail__merchant">
          <span class="discount-detail__merchant-icon">🏪</span>
          <span>{{ selected.merchantName || '校园商家' }}</span>
        </div>

        <p v-if="selected.description" class="discount-detail__desc">
          {{ selected.description }}
        </p>

        <dl v-if="selected.startTime || selected.endTime || selected.merchantAddress || selected.useRules">
          <div v-if="selected.startTime || selected.endTime">
            <dt>活动时间</dt>
            <dd>
              <template v-if="selected.startTime">{{ fmt(selected.startTime) }}</template>
              <template v-if="selected.startTime && selected.endTime"> ~ </template>
              <template v-if="selected.endTime">{{ fmt(selected.endTime) }}</template>
              <template v-if="!selected.startTime && !selected.endTime">时间待定</template>
            </dd>
          </div>
          <div v-if="selected.merchantAddress">
            <dt>领取地点</dt>
            <dd>{{ selected.merchantAddress }}</dd>
          </div>
          <div v-if="selected.useRules">
            <dt>使用规则</dt>
            <dd class="discount-detail__rules">{{ selected.useRules }}</dd>
          </div>
        </dl>

        <div class="discount-detail__actions">
          <button
            :class="['discount-detail__fav', { 'discount-detail__fav--active': selected.isFavorited }]"
            @click="toggleFav(selected)"
          >
            {{ selected.isFavorited ? '❤️ 已收藏' : '🤍 收藏' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== Page Background ===== */
.discount-page {
  min-height: 100vh;
  background: linear-gradient(175deg, #f0f4fa 0%, #f5f8fc 35%, #fafbfd 100%);
  position: relative;
  overflow: hidden;
}

/* Subtle decorative blobs */
.discount-page::before {
  content: '';
  position: fixed;
  top: -180px;
  right: -120px;
  width: 520px;
  height: 520px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.025) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

.discount-page::after {
  content: '';
  position: fixed;
  bottom: -120px;
  left: -80px;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.02) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

.discount-container {
  position: relative;
  z-index: 1;
  max-width: 1100px;
  margin: 0 auto;
  padding: 88px 24px 64px;
}

/* ===== Heading ===== */
.discount-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 32px;
  flex-wrap: wrap;
}

.discount-heading__left h1 {
  font-size: 30px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 8px;
  letter-spacing: -0.3px;
}

.discount-heading__left p {
  font-size: 14.5px;
  color: #7a8ca5;
  margin: 0;
}

/* ===== Search ===== */
.discount-search {
  display: flex;
  gap: 10px;
}

.discount-search input {
  width: 260px;
  height: 44px;
  padding: 0 16px;
  border: 1.5px solid rgba(100, 140, 180, 0.12);
  border-radius: 10px;
  font-size: 14px;
  background: #ffffff;
  outline: none;
  transition: all 0.25s;
  box-shadow: 0 2px 10px rgba(30, 80, 130, 0.06);
  box-sizing: border-box;
  color: #334155;
}

.discount-search input::placeholder {
  color: #b0bec5;
  font-weight: 400;
}

.discount-search input:focus {
  border-color: #3b82f6;
  box-shadow: 0 2px 16px rgba(59, 130, 246, 0.1);
}

.discount-search__btn {
  height: 44px;
  padding: 0 24px;
  border: none;
  border-radius: 10px;
  background: #2563eb;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 2px 10px rgba(37, 99, 235, 0.18);
  box-sizing: border-box;
  white-space: nowrap;
}

.discount-search__btn:hover {
  background: #3b82f6;
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.3);
}

.discount-search__btn:active {
  transform: scale(0.97);
}

/* ===== Empty State ===== */
.discount-empty {
  text-align: center;
  padding: 72px 20px;
  background: #ffffff;
  border-radius: 16px;
  color: #7a8ca5;
  font-size: 14.5px;
  box-shadow: 0 2px 12px rgba(30, 70, 110, 0.04);
}

.discount-empty__icon {
  font-size: 52px;
  margin-bottom: 14px;
}

/* ===== Grid ===== */
.discount-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

@media (max-width: 800px) {
  .discount-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }
}

@media (max-width: 520px) {
  .discount-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

/* ===== Card ===== */
.discount-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 0 0 18px;
  border: 1px solid rgba(100, 140, 180, 0.08);
  border-radius: 16px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  font: inherit;
  color: inherit;
  box-shadow: 0 4px 18px rgba(30, 70, 110, 0.07);
}

.discount-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 14px 36px rgba(30, 70, 110, 0.13);
}

.discount-card:active {
  transform: translateY(-2px);
}

/* ===== Card Cover ===== */
.discount-card__img {
  display: block;
  width: 100%;
  height: 170px;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.discount-card:hover .discount-card__img {
  transform: scale(1.03);
}

.discount-card__placeholder {
  width: 100%;
  height: 170px;
  background: linear-gradient(145deg, #e8f0fe 0%, #dce8fa 40%, #eef2ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44px;
  position: relative;
  transition: transform 0.3s ease;
}

/* Subtle decorative circles on placeholder */
.discount-card__placeholder::before {
  content: '';
  position: absolute;
  top: -28px;
  right: -18px;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  pointer-events: none;
}

.discount-card__placeholder::after {
  content: '';
  position: absolute;
  bottom: -22px;
  left: -14px;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.4);
  pointer-events: none;
}

.discount-card:hover .discount-card__placeholder {
  transform: scale(1.03);
}

/* ===== Status Badge (capsule) ===== */
.discount-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 11.5px;
  font-weight: 600;
  margin: 14px 16px 0;
  letter-spacing: 0.2px;
  width: fit-content;
}

.badge-active {
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
}

.badge-warning {
  background: rgba(249, 115, 22, 0.12);
  color: #c2410c;
}

.badge-ended,
.badge-default {
  background: rgba(100, 116, 139, 0.1);
  color: #64748b;
}

.badge-offline {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}

/* ===== Card Info ===== */
.discount-card__merchant {
  display: block;
  margin: 8px 16px 0;
  font-size: 13px;
  color: #8a9aaf;
  font-weight: 500;
}

.discount-card__title {
  margin: 6px 16px 0;
  font-size: 18px;
  font-weight: 700;
  color: #17233c;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ===== Detail Overlay ===== */
.discount-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 40px 16px;
  overflow-y: auto;
}

.discount-detail {
  width: 100%;
  max-width: 440px;
  background: #ffffff;
  border-radius: 18px;
  padding: 28px;
  animation: detailIn 0.25s ease-out;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.18);
}

@keyframes detailIn {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.discount-detail__back {
  border: none;
  background: rgba(59, 130, 246, 0.06);
  color: #3b82f6;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  padding: 6px 14px;
  border-radius: 8px;
  margin-bottom: 18px;
  transition: background 0.2s;
}

.discount-detail__back:hover {
  background: rgba(59, 130, 246, 0.12);
}

.discount-detail__img {
  width: 100%;
  height: 210px;
  object-fit: cover;
  border-radius: 14px;
}

.discount-detail__img--empty {
  background: linear-gradient(145deg, #e8f0fe 0%, #dce8fa 40%, #eef2ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52px;
}

.discount-detail h2 {
  font-size: 21px;
  font-weight: 700;
  color: #0f172a;
  margin: 16px 0 8px;
  letter-spacing: -0.2px;
}

.discount-detail__merchant {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #64748b;
  margin-bottom: 14px;
}

.discount-detail__merchant-icon {
  font-size: 16px;
}

.discount-detail__desc {
  font-size: 14px;
  color: #64748b;
  line-height: 1.7;
  white-space: pre-wrap;
  margin: 0 0 18px;
}

.discount-detail dl {
  border-top: 1px solid #eef1f5;
  padding-top: 14px;
  margin: 0;
}

.discount-detail dl div {
  display: flex;
  padding: 10px 0;
  font-size: 14px;
}

.discount-detail dt {
  width: 80px;
  flex-shrink: 0;
  color: #94a3b8;
  font-weight: 500;
}

.discount-detail dd {
  margin: 0;
  color: #334155;
}

.discount-detail__rules {
  white-space: pre-wrap;
}

.discount-detail__actions {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid #eef1f5;
}

.discount-detail__fav {
  width: 100%;
  padding: 13px;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  color: #64748b;
}

.discount-detail__fav--active {
  border-color: #f43f5e;
  color: #f43f5e;
  background: rgba(244, 63, 94, 0.04);
}

.discount-detail__fav:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.discount-detail__fav--active:hover {
  border-color: #e11d48;
  color: #e11d48;
}

/* ===== Pagination ===== */
.discount-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-top: 36px;
  padding: 20px;
}

.discount-pagination__btn {
  padding: 10px 22px;
  border: 1.5px solid rgba(100, 140, 180, 0.15);
  border-radius: 10px;
  background: #ffffff;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(30, 70, 110, 0.05);
}

.discount-pagination__btn:hover:not(:disabled) {
  border-color: #3b82f6;
  color: #2563eb;
  background: #f0f6ff;
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.12);
}

.discount-pagination__btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.discount-pagination__info {
  font-size: 13.5px;
  color: #7a8ca5;
  font-weight: 500;
  white-space: nowrap;
}
</style>
