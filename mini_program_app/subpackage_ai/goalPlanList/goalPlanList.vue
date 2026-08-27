<template>
  <view class="page study-plan-list-page">
    <nav-bar
      title="我的学习计划"
      :showBack="true"
      :border="false"
    />

    <scroll-view class="content" scroll-y :show-scrollbar="false">
      <view class="content-inner">
        <view v-if="!loading && summaries.length === 0" class="empty-state">
          <text class="empty-title">还没有学习计划</text>
          <text class="empty-sub">上传一份数据表或粘贴计划文本，AI 帮你拆成可勾选的任务清单</text>
          <view class="primary-btn empty-btn" @tap="goCreate"><text>去创建一份计划</text></view>
        </view>

        <template v-if="summaries.length > 0">
          <view class="plan-section-heading">
            <text class="section-title">当前计划</text>
          </view>

          <view v-if="currentPlan" class="section-card current-plan-card" @tap="openDetail(currentPlan)">
            <view class="plan-head current-plan-head">
              <text class="plan-title">{{ currentPlan.title }}</text>
              <view class="status-tag" :class="`status-tag--${currentPlan.status}`">
                <text>{{ statusLabel(currentPlan.status) }}</text>
              </view>
            </view>
            <text v-if="currentPlan.description" class="plan-desc current-plan-desc">{{ currentPlan.description }}</text>
            <view class="progress-line current-progress-line">
              <text class="progress-label">进度</text>
              <view class="progress-track">
                <view class="progress-fill" :style="{ width: `${currentPlan.progress}%` }"></view>
              </view>
              <text class="progress-num">{{ currentPlan.progress }}%</text>
            </view>
            <view class="current-next">
              <view class="current-next-copy">
                <text class="current-next-label">下一步</text>
                <text class="current-next-title">{{ currentPlanNextAction(currentPlan) }}</text>
                <text class="current-next-meta">每天学习 {{ currentPlan.dailyStudyMinutes || 60 }} 分钟</text>
              </view>
            </view>
            <view class="current-plan-schedule">
              <text>开始 {{ formatPlanDate(currentPlan.startDate) }}</text>
              <text v-if="currentPlan.targetDate">目标 {{ formatPlanDate(currentPlan.targetDate) }}</text>
            </view>
            <view class="current-plan-actions">
              <view class="primary-btn current-plan-btn" @tap.stop="openDetail(currentPlan)">
                <text>{{ currentPlanActionLabel(currentPlan) }}</text>
              </view>
              <view class="plan-action plan-action--delete current-plan-delete" @tap.stop="confirmDelete(currentPlan)">
                <text>删除计划</text>
              </view>
            </view>
          </view>

          <view v-if="historyPlans.length > 0" class="history-section">
            <view class="history-heading">
              <text class="section-title">历史计划</text>
              <view class="history-sort">
                <text>按开始时间</text>
                <text class="history-sort-chevron">⌄</text>
                <text class="history-count">{{ historyCount }} 项</text>
              </view>
            </view>
            <view class="section-card history-list">
              <view
                class="history-plan"
                v-for="item in historyPlans"
                :key="item.id"
                @tap="openDetail(item)"
              >
                <view class="plan-head history-plan-head">
                  <text class="plan-title">{{ item.title }}</text>
                  <view class="status-tag" :class="`status-tag--${item.status}`">
                    <text>{{ statusLabel(item.status) }}</text>
                  </view>
                </view>
                <text v-if="item.description" class="plan-desc">{{ item.description }}</text>
                <view class="progress-line history-progress-line">
                  <view class="progress-track">
                    <view class="progress-fill" :style="{ width: `${item.progress}%` }"></view>
                  </view>
                  <text class="progress-num">{{ item.progress }}%</text>
                </view>
                <view class="history-plan-meta">
                  <text>开始 {{ formatPlanDate(item.startDate) }}</text>
                  <text>每天 {{ item.dailyStudyMinutes || 60 }} 分钟</text>
                </view>
                <view class="plan-actions history-plan-actions">
                  <view class="plan-action plan-action--view" @tap.stop="openDetail(item)">
                    <text>查看计划</text>
                  </view>
                  <view class="plan-action plan-action--delete" @tap.stop="confirmDelete(item)">
                    <text>删除</text>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </template>

        <view v-if="loading" class="state-text"><text>正在加载...</text></view>
        <view v-else-if="hasMore && summaries.length > 0" class="load-more" @tap="loadMore">
          <text>{{ loadingMore ? '加载中...' : '加载更多' }}</text>
        </view>
        <view v-else-if="summaries.length > 0" class="state-text"><text>没有更多了</text></view>
      </view>
    </scroll-view>

    <view class="fab-create" @tap="goCreate">
      <view class="fab-icon"></view>
      <text class="fab-label">新建拆解</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { deleteStudyGoal, listMyGoals } from '@/api/studyGoal.js'
import { formatPlanDate, statusText } from '@/utils/studyPlan.js'

const PAGE_SIZE = 10
const summaries = ref([])
const page = ref(1)
const total = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const initialized = ref(false)
const deletingId = ref(null)

const currentPlan = computed(() => (
  summaries.value.find((item) => item.status === 'in_progress')
  || summaries.value.find((item) => item.status === 'pending')
  || summaries.value[0]
  || null
))

const historyPlans = computed(() => {
  const currentId = currentPlan.value?.id
  return summaries.value.filter((item) => item.id !== currentId)
})

const historyCount = computed(() => Math.max(0, total.value - (currentPlan.value ? 1 : 0)))

function statusLabel(status) {
  return statusText(status)
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function currentPlanNextAction(item) {
  if (item.nextTaskName) return item.nextTaskName
  if (item.status === 'in_progress') return '继续执行计划中的任务'
  if (item.status === 'pending') return '开始第一项执行任务'
  return '查看计划完成情况'
}

function currentPlanActionLabel(item) {
  if (item.status === 'in_progress') return '继续学习'
  if (item.status === 'pending') return '开始学习'
  return '查看计划'
}

async function fetchPage(pageNo, isMore) {
  const response = await listMyGoals(pageNo, PAGE_SIZE)
  const data = response?.data || {}
  const rows = Array.isArray(data.records) ? data.records : []
  total.value = Number(data.total) || 0
  page.value = pageNo
  summaries.value = isMore ? summaries.value.concat(rows) : rows
  const fetched = (pageNo - 1) * PAGE_SIZE + rows.length
  hasMore.value = fetched < total.value
}

function refresh() {
  if (loading.value) return
  loading.value = true
  fetchPage(1, false).catch(() => {}).finally(() => {
    loading.value = false
    initialized.value = true
  })
}

function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  fetchPage(page.value + 1, true).catch(() => {}).finally(() => {
    loadingMore.value = false
  })
}

onLoad(() => {})

onShow(() => {
  // 从详情返回时同步最新勾选进度
  refresh()
})

function openDetail(item) {
  uni.navigateTo({ url: `/subpackage_ai/goalDecompose/goalDecompose?goalId=${item.id}` })
}

function confirmDelete(item) {
  if (deletingId.value != null) return
  uni.showModal({
    title: '删除学习计划',
    content: `确定删除“${item.title}”？计划、任务和细分任务都将被删除，且无法恢复。`,
    confirmText: '删除',
    confirmColor: '#A14B46',
    success: (result) => {
      if (!result.confirm) return
      deletingId.value = item.id
      deleteStudyGoal(item.id).then(() => fetchPage(1, false)).then(() => {
        uni.showToast({ title: '计划已删除', icon: 'success' })
      }).catch(() => {}).finally(() => {
        deletingId.value = null
      })
    }
  })
}

function goCreate() {
  uni.navigateTo({ url: '/subpackage_ai/goalDecompose/goalDecompose' })
}
</script>

<style scoped>
.page {
  height: 100vh;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #F6F7FB;
  color: #172033;
  position: relative;
}

.content {
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
}

.content-inner {
  width: 100%;
  box-sizing: border-box;
  padding: 24rpx 30rpx calc(180rpx + env(safe-area-inset-bottom));
}

.section-card {
  border: 1rpx solid rgba(229, 226, 240, 0.82);
  border-radius: 22rpx;
  background: #FFFFFF;
  box-shadow: 0 12rpx 34rpx rgba(31, 35, 68, 0.045);
  box-sizing: border-box;
}

.section-card + .section-card {
  margin-top: 22rpx;
}

.plan-card {
  padding: 24rpx 28rpx;
}

.plan-head {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.plan-title {
  flex: 1;
  min-width: 0;
  font-size: 29rpx;
  font-weight: 600;
  color: #172033;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-tag {
  flex-shrink: 0;
  padding: 5rpx 14rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  font-weight: 600;
}

.status-tag--pending {
  background: #EEF1F6;
  color: #64748B;
}

.status-tag--in_progress {
  background: #E8F3EC;
  color: #3D7A52;
}

.status-tag--completed {
  background: #EDF2FA;
  color: #2F4468;
}

.status-tag--blocked,
.status-tag--skipped {
  background: #FBEAE9;
  color: #A14B46;
}

.plan-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 23rpx;
  color: #5C667A;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.progress-line {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 18rpx;
}

.progress-track {
  flex: 1;
  height: 12rpx;
  border-radius: 999rpx;
  background: #EEF1F7;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #5E86C7, #3D5789);
  transition: width 0.35s ease;
}

.progress-num {
  width: 80rpx;
  text-align: right;
  font-size: 25rpx;
  font-weight: 600;
  color: #3D5789;
}

.plan-foot {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 16rpx;
}

.meta-text {
  font-size: 21rpx;
  color: #8B93A6;
}

.meta-text--remain {
  color: #B97D24;
}

.meta-split {
  color: #D4DAE5;
  font-size: 21rpx;
}

.plan-time {
  margin-left: 0;
  font-size: 20rpx;
  color: #98A0B0;
}

.delete-plan-link {
  margin-left: auto;
  flex-shrink: 0;
  color: #A14B46;
  font-size: 21rpx;
}

.plan-schedule {
  display: flex;
  gap: 24rpx;
  margin-top: 12rpx;
  color: #8B93A6;
  font-size: 20rpx;
}

.empty-state {
  padding: 120rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  text-align: center;
}

.empty-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #233047;
}

.empty-sub {
  font-size: 23rpx;
  color: #98A0B0;
  line-height: 1.6;
}

.primary-btn {
  margin-top: 26rpx;
  width: 320rpx;
  height: 84rpx;
  border-radius: 16rpx;
  background: #3D5789;
  color: #FFFFFF;
  font-size: 27rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.state-text {
  padding: 34rpx 0;
  text-align: center;
  font-size: 23rpx;
  color: #98A0B0;
}

.load-more {
  margin-top: 22rpx;
  height: 76rpx;
  border-radius: 14rpx;
  border: 1rpx solid #CBD3E0;
  background: #FFFFFF;
  color: #47536A;
  font-size: 25rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fab-create {
  position: fixed;
  right: 30rpx;
  bottom: calc(50rpx + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 18rpx 30rpx;
  border-radius: 999rpx;
  background: #3D5789;
  box-shadow: 0 14rpx 30rpx rgba(61, 87, 137, 0.32);
}

.fab-icon {
  width: 24rpx;
  height: 24rpx;
  position: relative;
}

.fab-icon::before,
.fab-icon::after {
  content: '';
  position: absolute;
  background: #FFFFFF;
  border-radius: 2rpx;
}

.fab-icon::before {
  left: 50%;
  top: 0;
  width: 3rpx;
  height: 100%;
  transform: translateX(-50%);
}

.fab-icon::after {
  top: 50%;
  left: 0;
  height: 3rpx;
  width: 100%;
  transform: translateY(-50%);
}

.fab-label {
  color: #FFFFFF;
  font-size: 25rpx;
  font-weight: 600;
}

/* 学习计划列表与详情保持同一套安静的阅读层级 */
.page {
  background: #F5F7FA;
}

.content-inner {
  padding: 18rpx 28rpx calc(170rpx + env(safe-area-inset-bottom));
}

.section-card {
  border-color: #E6EBF2;
  border-radius: 16rpx;
  box-shadow: none;
}

.section-card + .section-card {
  margin-top: 16rpx;
}

.plan-card--quiet {
  padding: 22rpx 24rpx;
}

.plan-head {
  gap: 12rpx;
}

.plan-title {
  font-size: 28rpx;
}

.plan-desc {
  margin-top: 8rpx;
  color: #657287;
}

.plan-summary .progress-line {
  margin-top: 16rpx;
}

.plan-summary .progress-track {
  height: 10rpx;
}

.plan-foot {
  align-items: center;
  gap: 8rpx;
  margin-top: 14rpx;
}

.plan-actions {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-left: auto;
  flex-shrink: 0;
}

.delete-plan-link--quiet {
  margin-left: 0;
  color: #9A6570;
}

.plan-schedule {
  gap: 14rpx;
  margin-top: 12rpx;
  flex-wrap: wrap;
}

.load-more {
  border-color: #D7DFE9;
  box-shadow: none;
}

.fab-create {
  right: 28rpx;
  bottom: calc(42rpx + env(safe-area-inset-bottom));
  padding: 16rpx 26rpx;
  box-shadow: 0 10rpx 22rpx rgba(61, 87, 137, 0.2);
}

/* 计划列表按“当前计划—历史计划”重新分层，减少信息同时争抢注意力 */
.study-plan-list-page .content-inner {
  padding: 18rpx 28rpx calc(170rpx + env(safe-area-inset-bottom));
}

.study-plan-list-page .section-card {
  border-color: #E6EBE9;
  border-radius: 18rpx;
  box-shadow: none;
}

.study-plan-list-page .plan-section-heading {
  margin: 20rpx 16rpx 14rpx;
}

.study-plan-list-page .section-title {
  color: #263B3D;
  font-size: 27rpx;
  font-weight: 600;
}

.study-plan-list-page .current-plan-card {
  padding: 26rpx 24rpx 24rpx;
}

.study-plan-list-page .current-plan-head {
  align-items: flex-start;
}

.study-plan-list-page .current-plan-head .plan-title {
  font-size: 30rpx;
  line-height: 1.35;
}

.study-plan-list-page .current-plan-desc {
  margin-top: 10rpx;
  color: #687779;
}

.study-plan-list-page .current-progress-line {
  margin-top: 22rpx;
}

.study-plan-list-page .progress-label {
  flex-shrink: 0;
  color: #819091;
  font-size: 21rpx;
}

.study-plan-list-page .progress-fill {
  background: #5B7F80;
}

.study-plan-list-page .progress-num {
  color: #527677;
}

.study-plan-list-page .current-next {
  margin-top: 20rpx;
  padding: 18rpx 20rpx;
  border-radius: 14rpx;
  background: #F0F4F3;
}

.study-plan-list-page .current-next-copy {
  display: flex;
  flex-direction: column;
  gap: 5rpx;
  min-width: 0;
}

.study-plan-list-page .current-next-label {
  color: #5B7F80;
  font-size: 20rpx;
}

.study-plan-list-page .current-next-title {
  color: #263B3D;
  font-size: 24rpx;
  line-height: 1.4;
}

.study-plan-list-page .current-next-meta {
  color: #7E8B8C;
  font-size: 20rpx;
}

.study-plan-list-page .current-plan-schedule,
.study-plan-list-page .history-plan-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18rpx;
  color: #839091;
  font-size: 20rpx;
}

.study-plan-list-page .current-plan-schedule {
  margin-top: 18rpx;
}

.study-plan-list-page .current-plan-btn {
  flex: 1;
  width: auto;
  min-width: 0;
  height: 78rpx;
  margin-top: 0;
  border-radius: 14rpx;
  background: #5B7F80;
  font-size: 26rpx;
}

.study-plan-list-page .current-plan-actions {
  display: flex;
  align-items: stretch;
  gap: 14rpx;
  margin-top: 20rpx;
}

.study-plan-list-page .current-plan-delete {
  flex: 0 0 150rpx;
  height: 78rpx;
  border-radius: 14rpx;
  font-size: 22rpx;
}

.study-plan-list-page .history-section {
  margin-top: 28rpx;
}

.study-plan-list-page .history-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  margin: 0 16rpx 14rpx;
}

.study-plan-list-page .history-sort {
  display: flex;
  align-items: center;
  gap: 8rpx;
  color: #7D8A8B;
  font-size: 20rpx;
}

.study-plan-list-page .history-sort-chevron {
  color: #97A3A3;
  font-size: 22rpx;
  line-height: 1;
}

.study-plan-list-page .history-count {
  padding-left: 10rpx;
  border-left: 1rpx solid #DCE3E1;
  color: #526E70;
}

.study-plan-list-page .history-list {
  padding: 0;
  overflow: hidden;
}

.study-plan-list-page .history-plan {
  padding: 24rpx;
}

.study-plan-list-page .history-plan + .history-plan {
  border-top: 1rpx solid #EDF1F0;
}

.study-plan-list-page .history-plan-head {
  align-items: flex-start;
}

.study-plan-list-page .history-plan .plan-title {
  font-size: 27rpx;
}

.study-plan-list-page .history-plan .plan-desc {
  margin-top: 9rpx;
  color: #718082;
}

.study-plan-list-page .history-progress-line {
  margin-top: 16rpx;
}

.study-plan-list-page .history-plan-meta {
  margin-top: 14rpx;
}

.study-plan-list-page .history-plan-actions {
  display: flex;
  gap: 14rpx;
  margin-top: 18rpx;
}

.plan-action {
  flex: 1;
  min-width: 0;
  height: 62rpx;
  border: 1rpx solid;
  border-radius: 10rpx;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
}

.plan-action--view {
  background: #FFFFFF;
  border: 1rpx solid #5B7F80;
  color: #527677;
}

.plan-action--delete {
  background: #FFFFFF;
  border: 1rpx solid #C77B73;
  color: #B76760;
}

.study-plan-list-page .fab-create {
  background: #5B7F80;
  box-shadow: 0 10rpx 22rpx rgba(91, 127, 128, 0.2);
}
</style>
