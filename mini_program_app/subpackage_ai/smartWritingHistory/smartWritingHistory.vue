<template>
  <view class="history-page">
    <nav-bar title="智能写作历史" :showBack="true" fixed placeholder />

    <view class="history-panel">
      <view class="search-box">
        <image class="search-icon" src="/static/icons/line/search.svg" mode="aspectFit" />
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索创作记录"
          confirm-type="search"
        />
        <text v-if="keyword" class="search-clear" @tap="keyword = ''">×</text>
      </view>

      <view class="record-list">
        <view v-for="item in filteredRecords" :key="item.id" class="record-item" @tap="openRecord(item.id)">
          <view class="record-icon" :class="`record-icon--${item.sceneKey || 'default'}`">
            <text>{{ sceneIcon(item.sceneKey) }}</text>
          </view>
          <view class="record-body">
            <text class="record-title">{{ item.title }}</text>
            <text class="record-time">{{ formatTime(item.createdAt) }}</text>
          </view>
        </view>
      </view>

      <view v-if="!filteredRecords.length" class="empty-state">
        <text class="empty-title">{{ keyword ? '没有匹配的记录' : '暂无智能写作记录' }}</text>
        <text class="empty-desc">完成一次智能写作后，记录会显示在这里</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getSmartWritingHistory } from '@/utils/smartWritingHistory.js'

const keyword = ref('')
const records = ref([])

const loadRecords = () => {
  records.value = getSmartWritingHistory()
}

onShow(loadRecords)

const filteredRecords = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return records.value
  return records.value.filter(item => `${item.title || ''} ${item.prompt || ''}`.toLowerCase().includes(value))
})

const sceneIcon = (sceneKey) => ({
  weekly: '周',
  summary: '总',
  holiday: '节',
  review: '评'
}[sceneKey] || '文')

const formatTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const openRecord = (id) => {
  uni.navigateTo({
    url: `/subpackage_ai/smartWritingDetail/smartWritingDetail?id=${encodeURIComponent(id)}&source=history`
  })
}
</script>

<style lang="scss" scoped>
.history-page {
  min-height: 100vh;
  background: #f5f6fa;
}

.history-panel {
  min-height: calc(100vh - 88rpx);
  padding: 26rpx 30rpx 60rpx;
  box-sizing: border-box;
}

.search-box {
  display: flex;
  align-items: center;
  height: 76rpx;
  padding: 0 22rpx;
  border-radius: 18rpx;
  background: #ffffff;
  box-sizing: border-box;
}

.search-icon {
  width: 32rpx;
  height: 32rpx;
  margin-right: 14rpx;
  opacity: 0.52;
}

.search-input {
  flex: 1;
  min-width: 0;
  color: #20232b;
  font-size: 25rpx;
}

.search-clear {
  display: flex;
  width: 38rpx;
  height: 38rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #eef0f5;
  color: #8991a0;
  font-size: 27rpx;
}

.record-list {
  margin-top: 20rpx;
  padding: 18rpx 22rpx;
  border-radius: 22rpx;
  background: #ffffff;
}

.record-item {
  display: flex;
  align-items: center;
  min-height: 92rpx;
  gap: 20rpx;
}

.record-item + .record-item {
  border-top: 1rpx solid #f0f1f5;
}

.record-icon {
  display: flex;
  width: 48rpx;
  height: 48rpx;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  color: #ffffff;
  font-size: 23rpx;
  font-weight: 700;
}

.record-icon--weekly {
  background: #4f72df;
}

.record-icon--summary {
  background: #7b55db;
}

.record-icon--holiday {
  background: #a765d8;
}

.record-icon--review {
  background: #7655d7;
}

.record-icon--default {
  background: #718096;
}

.record-body {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.record-title {
  min-width: 0;
  overflow: hidden;
  color: #242731;
  font-size: 27rpx;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-time {
  flex-shrink: 0;
  color: #a0a6b3;
  font-size: 21rpx;
}

.empty-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 150rpx 30rpx;
  text-align: center;
}

.empty-title {
  color: #4d5360;
  font-size: 28rpx;
  font-weight: 700;
}

.empty-desc {
  margin-top: 12rpx;
  color: #9aa1ae;
  font-size: 23rpx;
}
</style>
