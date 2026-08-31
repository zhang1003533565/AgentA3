<script setup>
import { ref } from 'vue'
import {
  formatActivityDate,
  getActivityDateBlock,
  getActivityRemainingSeats,
  getActivitySeatEmClass,
  getActivityStatus,
} from '../../utils/activityCard'

const props = defineProps({
  item: {
    type: Object,
    required: true,
  },
  favorited: {
    type: Boolean,
    default: false,
  },
  showFavorite: {
    type: Boolean,
    default: true,
  },
  animationDelay: {
    type: Number,
    default: 0,
  },
})

const emit = defineEmits(['click', 'toggle-favorite'])

const coverFailed = ref(false)

function onCoverError() {
  coverFailed.value = true
}

function handleClick() {
  emit('click', props.item)
}

function handleToggleFavorite(event) {
  event.stopPropagation()
  emit('toggle-favorite', props.item, event)
}
</script>

<template>
  <article
    class="ca-card"
    :style="{ animationDelay: `${animationDelay}ms` }"
    @click="handleClick"
  >
    <div class="ca-card__cover">
      <img
        v-if="item.coverImage && !coverFailed"
        :src="item.coverImage"
        :alt="item.title"
        loading="lazy"
        @error="onCoverError"
      />
      <div v-else class="ca-card__placeholder">
        <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
      </div>
      <div v-if="item.coverImage && !coverFailed" class="ca-card__shade"></div>
      <div v-if="getActivityDateBlock(item)" class="ca-card__date">
        <svg class="ca-card__date-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
        <span class="ca-card__date-main">{{ getActivityDateBlock(item).month }}{{ getActivityDateBlock(item).day }}日</span>
        <span class="ca-card__date-dot">·</span>
        <span class="ca-card__date-week">{{ getActivityDateBlock(item).weekday }}</span>
      </div>
      <span :class="['ca-badge', getActivityStatus(item).class]">
        {{ getActivityStatus(item).text }}
      </span>
      <button
        v-if="showFavorite"
        :class="['ca-fav', { active: favorited }]"
        :aria-label="favorited ? '取消收藏' : '收藏'"
        @click="handleToggleFavorite"
      >
        <svg v-if="favorited" class="ca-icon ca-fav__icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
        <svg v-else class="ca-icon ca-fav__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
      </button>
    </div>

    <div class="ca-card__body">
      <div class="ca-card__tags">
        <span v-if="item.category?.categoryName" class="ca-chip">{{ item.category.categoryName }}</span>
        <span class="ca-card__org">{{ item.organizerName || item.organizer?.realName || '未知主办方' }}</span>
      </div>

      <h3 class="ca-card__title">{{ item.title }}</h3>

      <ul class="ca-card__meta">
        <li>
          <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          <span>{{ formatActivityDate(item.startTime) }}</span>
        </li>
        <li>
          <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
          <span>{{ item.location || '线上活动' }}</span>
        </li>
        <li v-if="item.maxPeople">
          <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
          <span>{{ item.currentPeople || 0 }}/{{ item.maxPeople || 0 }} 人</span>
          <em v-if="getActivityRemainingSeats(item) > 0" :class="getActivitySeatEmClass(item)">剩 {{ getActivityRemainingSeats(item) }} 位</em>
        </li>
      </ul>

      <div class="ca-card__foot">
        <span v-if="item.signupEndTime" class="ca-card__deadline">报名截止 {{ formatActivityDate(item.signupEndTime) }}</span>
        <span v-else class="ca-card__deadline"></span>
        <span class="ca-link">
          查看详情
          <svg class="ca-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
        </span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.ca-card {
  display: flex;
  flex-direction: column;
  border: 1px solid #eef2f7;
  border-radius: 16px;
  background: #ffffff;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(23, 35, 58, 0.05);
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  animation: caCardIn 0.45s ease-out backwards;
}

.ca-card:hover {
  border-color: #bfdbfe;
  box-shadow: 0 20px 40px rgba(30, 43, 76, 0.12), 0 0 0 1px rgba(59, 130, 246, 0.08), 0 8px 24px rgba(59, 130, 246, 0.08);
  transform: translateY(-4px);
}

.ca-card__cover {
  position: relative;
  height: 176px;
  overflow: hidden;
  background: linear-gradient(160deg, #eef4ff 0%, #e3edf9 60%, #dbe8f6 100%);
}

.ca-card__shade {
  position: absolute;
  inset: auto 0 0 0;
  height: 64px;
  background: linear-gradient(180deg, rgba(10, 18, 30, 0) 0%, rgba(10, 18, 30, 0.32) 100%);
  pointer-events: none;
}

.ca-card__date {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 10px rgba(16, 24, 40, 0.14);
  backdrop-filter: blur(6px);
}

.ca-card__date-icon {
  width: 15px;
  height: 15px;
  color: #2563eb;
}

.ca-card__date-main {
  color: #17233a;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.ca-card__date-dot {
  color: #cbd5e1;
  font-size: 12px;
  line-height: 1;
}

.ca-card__date-week {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.ca-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}

.ca-card:hover .ca-card__cover img {
  transform: scale(1.04);
}

.ca-card__placeholder {
  display: grid;
  place-items: center;
  height: 100%;
  color: #9db8d8;
}

.ca-card__placeholder .ca-icon {
  width: 42px;
  height: 42px;
  color: #9db8d8;
  stroke-width: 1.4;
}

.ca-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
  backdrop-filter: blur(4px);
}

.ca-badge::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.ca-badge.status-upcoming {
  background: rgba(255, 247, 230, 0.94);
  color: #a06b12;
}

.ca-badge.status-ongoing {
  background: rgba(236, 248, 242, 0.94);
  color: #2e7d5b;
}

.ca-badge.status-ended {
  background: rgba(240, 243, 247, 0.94);
  color: #64748b;
}

.ca-badge.status-full {
  background: rgba(254, 240, 240, 0.94);
  color: #b4534a;
}

.ca-fav {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(228, 234, 241, 0.9);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  color: #7b8b9c;
  cursor: pointer;
  transition: color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.ca-fav:hover {
  color: #c0655f;
  box-shadow: 0 4px 10px rgba(16, 24, 40, 0.12);
  transform: scale(1.08);
}

.ca-fav.active {
  color: #c0655f;
}

.ca-fav__icon {
  width: 17px;
  height: 17px;
}

.ca-card__body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 18px 20px 20px;
}

.ca-card__tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.ca-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border: 1px solid #dbe4ee;
  border-radius: 999px;
  background: #ffffff;
  color: #5b6b7d;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
}

.ca-card__org {
  overflow: hidden;
  color: #8a99a9;
  font-size: 12px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ca-card__title {
  margin: 0 0 12px;
  color: #17233a;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ca-card__meta {
  display: grid;
  gap: 7px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ca-card__meta li {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.4;
}

.ca-card__meta li > span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ca-card__meta .ca-icon {
  width: 15px;
  height: 15px;
  flex: 0 0 auto;
  color: #94a3b8;
}

.ca-card__meta em {
  margin-left: 4px;
  color: #2e7d5b;
  font-size: 12px;
  font-style: normal;
  font-weight: 600;
}

.ca-card__meta em.seat-low {
  color: #b45309;
}

.ca-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f4f8;
}

.ca-card__deadline {
  color: #64748b;
  font-size: 12px;
}

.ca-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.ca-link .ca-icon {
  width: 15px;
  height: 15px;
  transition: transform 0.2s ease;
}

.ca-card:hover .ca-link .ca-icon {
  transform: translateX(2px);
}

.ca-icon {
  display: inline-block;
  flex: 0 0 auto;
  vertical-align: middle;
}

@keyframes caCardIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
