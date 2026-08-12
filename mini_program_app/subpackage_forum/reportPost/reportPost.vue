<template>
  <view class="report-container">
    <nav-bar title="举报帖子" :showBack="true" fixed placeholder />

    <view class="report-content">
      <!-- 被举报内容摘要 -->
      <view class="report-target">
        <text class="target-label">举报内容</text>
        <view class="target-card">
          <text class="target-text">{{ postBrief }}</text>
        </view>
      </view>

      <!-- 举报原因 -->
      <view class="reason-section">
        <text class="section-label">举报原因</text>
        <view class="reason-list">
          <view
            v-for="(reason, index) in reasons"
            :key="index"
            class="reason-item"
            :class="{ active: selectedReason === index }"
            @click="selectedReason = index"
          >
            <text class="reason-text">{{ reason.label }}</text>
            <view class="reason-check" :class="{ checked: selectedReason === index }">
              <text v-if="selectedReason === index">✓</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 补充说明 -->
      <view class="desc-section">
        <text class="section-label">补充说明（选填）</text>
        <textarea
          class="desc-input"
          v-model="description"
          placeholder="请简单描述举报原因，帮助我们更快处理..."
          :maxlength="200"
        />
        <text class="char-count">{{ description.length }}/200</text>
      </view>

      <!-- 提交按钮 -->
      <view class="submit-btn" :class="{ disabled: selectedReason === null }" @click="submitReport">
        <text>提交举报</text>
      </view>
      <text class="submit-tip">提交后我们将尽快核实处理，感谢你的反馈</text>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { createReport, getPostDetail, parseImageList } from '@/api/forum.js'

export default {
  components: { NavBar },
  data() {
    return {
      postId: null,
      postBrief: '',
      reasons: [
        { label: '垃圾广告', reasonType: 1, reasonText: '垃圾广告' },
        { label: '虚假信息', reasonType: 2, reasonText: '虚假信息' },
        { label: '人身攻击', reasonType: 3, reasonText: '人身攻击' },
        { label: '低俗违规', reasonType: 4, reasonText: '低俗违规' },
        { label: '其他', reasonType: 5, reasonText: '其他' }
      ],
      selectedReason: null,
      description: ''
    }
  },
  onLoad(options) {
    this.postId = options.postId
    this.loadPostBrief()
  },
  methods: {
    async loadPostBrief() {
      if (!this.postId) return
      try {
        const res = await getPostDetail(this.postId)
        const data = res?.data || {}
        this.postBrief = (data.content || data.title || '').slice(0, 50)
      } catch (error) {
        this.postBrief = ''
      }
    },
    async submitReport() {
      if (this.selectedReason === null) {
        uni.showToast({ title: '请选择举报原因', icon: 'none' })
        return
      }
      const reason = this.reasons[this.selectedReason]
      uni.showLoading({ title: '提交中...' })
      try {
        await createReport({
          targetType: 1,
          targetId: Number(this.postId),
          reasonType: reason.reasonType,
          reasonText: reason.reasonText,
          description: this.description.trim()
        })
        uni.hideLoading()
        uni.showToast({ title: '举报已提交', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 800)
      } catch (error) {
        uni.hideLoading()
        uni.showToast({ title: error?.message || '举报提交失败', icon: 'none' })
      }
    }
  }
}
</script>

<style lang="scss">
.report-container {
  min-height: 100vh;
  background-color: #F7F7F9;
}

.report-content {
  padding: 24rpx;
}

.report-target {
  background-color: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;

  .target-label {
    font-size: 26rpx;
    font-weight: 600;
    color: #1D1D1F;
    display: block;
    margin-bottom: 16rpx;
  }

  .target-card {
    background-color: #F5F5F7;
    border-radius: 12rpx;
    padding: 20rpx;

    .target-text {
      font-size: 26rpx;
      color: #666666;
      line-height: 1.6;
    }
  }
}

.reason-section {
  background-color: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;

  .section-label {
    font-size: 28rpx;
    font-weight: 600;
    color: #1D1D1F;
    display: block;
    margin-bottom: 16rpx;
  }

  .reason-list {
    .reason-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 24rpx 0;
      border-bottom: 1rpx solid #F5F5F7;

      &:last-child {
        border-bottom: none;
      }

      .reason-text {
        font-size: 28rpx;
        color: #4A4A4A;
      }

      .reason-check {
        width: 36rpx;
        height: 36rpx;
        border-radius: 50%;
        border: 2rpx solid #D1D1D6;
        display: flex;
        align-items: center;
        justify-content: center;

        text {
          font-size: 24rpx;
          color: #FFFFFF;
        }

        &.checked {
          background-color: #5C7A99;
          border-color: #5C7A99;
        }
      }
    }
  }
}

.desc-section {
  background-color: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 32rpx;

  .section-label {
    font-size: 28rpx;
    font-weight: 600;
    color: #1D1D1F;
    display: block;
    margin-bottom: 16rpx;
  }

  .desc-input {
    width: 100%;
    height: 160rpx;
    background-color: #F5F5F7;
    border-radius: 12rpx;
    padding: 20rpx;
    font-size: 26rpx;
    color: #1D1D1F;
    box-sizing: border-box;
  }

  .char-count {
    display: block;
    text-align: right;
    font-size: 22rpx;
    color: #8E8E93;
    margin-top: 8rpx;
  }
}

.submit-btn {
  height: 88rpx;
  border-radius: 44rpx;
  background-color: #5C7A99;
  display: flex;
  align-items: center;
  justify-content: center;

  text {
    font-size: 30rpx;
    color: #FFFFFF;
    font-weight: 600;
  }

  &.disabled {
    opacity: 0.5;
  }
}

.submit-tip {
  display: block;
  text-align: center;
  font-size: 22rpx;
  color: #8E8E93;
  margin-top: 16rpx;
}
</style>
