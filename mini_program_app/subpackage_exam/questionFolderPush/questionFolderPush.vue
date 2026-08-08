<template>
  <view class="page">
    <nav-bar title="推送到收藏夹" :showBack="true" fixed placeholder />

    <view class="summary card">
      <text class="summary-title">已选 {{ questionIds.length }} 道题</text>
      <text class="summary-desc">可推送到公共或私有收藏夹；推到公共夹时可选同步公开私有题。</text>
    </view>

    <view class="tabs">
      <view class="tab" :class="{ 'tab--active': targetVisibility === 'PUBLIC' }" @tap="switchTab('PUBLIC')">公共收藏夹</view>
      <view class="tab" :class="{ 'tab--active': targetVisibility === 'PRIVATE' }" @tap="switchTab('PRIVATE')">私有收藏夹</view>
    </view>

    <view class="options card">
      <view class="option" @tap="removeFromSource = !removeFromSource">
        <text class="check" :class="{ on: removeFromSource }">{{ removeFromSource ? '✓' : '' }}</text>
        <text>推送后从当前收藏夹移除</text>
      </view>
      <view v-if="targetVisibility === 'PUBLIC'" class="option" @tap="publishQuestions = !publishQuestions">
        <text class="check" :class="{ on: publishQuestions }">{{ publishQuestions ? '✓' : '' }}</text>
        <text>私有题同步改为公开（公私交互必需）</text>
      </view>
    </view>

    <scroll-view class="list" scroll-y>
      <view v-if="loading" class="state-block"><text>加载中...</text></view>
      <view v-else-if="errorMessage" class="state-block">
        <text>加载失败</text>
        <text class="state-desc">{{ errorMessage }}</text>
        <view class="retry" @tap="loadTargets">重新加载</view>
      </view>
      <view v-else-if="!targets.length" class="state-block">
        <text>暂无可用目标收藏夹</text>
        <text class="state-desc">请先在对应 Tab 下新建收藏夹</text>
      </view>

      <view
        v-for="item in targets"
        :key="item.id"
        class="card target"
        :class="{ 'target--active': selectedId === item.id }"
        @tap="selectedId = item.id"
      >
        <view class="target-head">
          <text class="title">{{ item.name }}</text>
          <text class="badge">{{ item.visibilityLabel || (item.visibility === 'PUBLIC' ? '公共' : '私有') }}</text>
        </view>
        <text class="meta">{{ item.questionCount || 0 }} 题</text>
      </view>
    </scroll-view>

    <view class="footer">
      <view class="primary" :class="{ disabled: !selectedId || pushing }" @tap="confirmPush">
        {{ pushing ? '推送中...' : '确认推送' }}
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getUserInfo } from '@/utils/storage.js'
import { listQuestionFolders, pushQuestionsToFolder } from '@/api/questionFolder.js'

export default {
  components: { NavBar },
  data() {
    return {
      sourceFolderId: '',
      questionIds: [],
      targetVisibility: 'PRIVATE',
      targets: [],
      selectedId: null,
      removeFromSource: false,
      publishQuestions: true,
      loading: false,
      pushing: false,
      errorMessage: '',
      isAdmin: false
    }
  },
  onLoad(query) {
    const info = getUserInfo() || {}
    this.isAdmin = String(info.role || '').toUpperCase() === 'ADMIN'
    this.sourceFolderId = query?.folderId || ''
    const rawIds = query?.questionIds ? decodeURIComponent(query.questionIds) : ''
    this.questionIds = rawIds
      .split(',')
      .map((id) => Number(id))
      .filter((id) => Number.isFinite(id) && id > 0)
    if (query?.targetVisibility === 'PUBLIC' || query?.targetVisibility === 'PRIVATE') {
      this.targetVisibility = query.targetVisibility
    }
    this.loadTargets()
  },
  methods: {
    switchTab(visibility) {
      if (this.targetVisibility === visibility) return
      this.targetVisibility = visibility
      this.selectedId = null
      this.loadTargets()
    },
    async loadTargets() {
      this.loading = true
      this.errorMessage = ''
      try {
        const res = await listQuestionFolders({ visibility: this.targetVisibility })
        const list = res?.data || []
        this.targets = list.filter((item) => {
          if (String(item.id) === String(this.sourceFolderId)) return false
          return this.isAdmin || item.ownedByCurrentUser
        })
      } catch (error) {
        this.targets = []
        this.errorMessage = error?.msg || error?.message || '请检查网络后重试'
      } finally {
        this.loading = false
      }
    },
    async confirmPush() {
      if (!this.selectedId || this.pushing || !this.questionIds.length) return
      this.pushing = true
      try {
        const res = await pushQuestionsToFolder(this.sourceFolderId, {
          targetFolderId: this.selectedId,
          questionIds: this.questionIds,
          removeFromSource: this.removeFromSource,
          publishQuestions: this.targetVisibility === 'PUBLIC' ? this.publishQuestions : false
        })
        const data = res?.data || {}
        uni.showToast({
          title: `已推送 ${data.pushedCount || this.questionIds.length} 题`,
          icon: 'success'
        })
        setTimeout(() => {
          uni.navigateBack()
        }, 500)
      } catch (error) {
        // request toast
      } finally {
        this.pushing = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f6f8fb;
  padding-bottom: 160rpx;
  box-sizing: border-box;
}

.card {
  margin: 16rpx 24rpx 0;
  padding: 24rpx;
  background: #ffffff;
  border: 1px solid #e4ebf1;
  border-radius: 18rpx;
}

.summary-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #18222f;
}

.summary-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #7b8794;
  line-height: 1.5;
}

.tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12rpx;
  margin: 16rpx 24rpx 0;
  padding: 8rpx;
  background: #ffffff;
  border-radius: 18rpx;
  border: 1px solid #e4ebf1;
}

.tab {
  height: 68rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
  color: #667085;
}

.tab--active {
  background: #e8eef3;
  color: #304152;
}

.option {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 10rpx 0;
  font-size: 24rpx;
  color: #314253;
}

.check {
  width: 36rpx;
  height: 36rpx;
  border-radius: 8rpx;
  border: 1px solid #c9d4de;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: #ffffff;
}

.check.on {
  background: #5e7387;
  border-color: #5e7387;
}

.list {
  height: calc(100vh - 520rpx);
  padding-bottom: 24rpx;
  box-sizing: border-box;
}

.target-head {
  display: flex;
  gap: 12rpx;
  align-items: flex-start;
}

.title {
  flex: 1;
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2937;
}

.badge {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #e8eef3;
  color: #516274;
  font-size: 20rpx;
  font-weight: 700;
}

.meta {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #7b8794;
}

.target--active {
  border-color: #5e7387;
  background: #f3f6f9;
}

.state-block {
  padding: 80rpx 40rpx;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  color: #314253;
  font-size: 28rpx;
  font-weight: 700;
}

.state-desc {
  font-size: 24rpx;
  font-weight: 400;
  color: #7b8794;
}

.retry {
  padding: 14rpx 28rpx;
  border-radius: 14rpx;
  background: #e8eef3;
  color: #304152;
  font-size: 24rpx;
  font-weight: 700;
}

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  background: rgba(246, 248, 251, 0.96);
}

.primary {
  height: 84rpx;
  border-radius: 18rpx;
  background: #5e7387;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
}

.primary.disabled {
  opacity: 0.5;
}
</style>
