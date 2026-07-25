<template>
  <view v-if="visible" class="import-progress-mask">
    <view class="import-progress-card">
      <view class="import-progress-head">
        <view
          class="import-progress-icon"
          :class="{
            'import-progress-icon--done': status === 'done',
            'import-progress-icon--failed': status === 'failed'
          }"
        >
          <view v-if="status === 'running'" class="import-progress-spinner"></view>
          <text v-else-if="status === 'done'" class="import-progress-symbol">✓</text>
          <text v-else class="import-progress-symbol">!</text>
        </view>
        <text class="import-progress-title">{{ title }}</text>
        <text class="import-progress-message">{{ message }}</text>
      </view>

      <view class="import-progress-steps">
        <view
          v-for="step in steps"
          :key="step.key"
          class="import-progress-step"
          :class="`import-progress-step--${step.status || 'waiting'}`"
        >
          <view class="import-progress-step__dot">
            <text v-if="step.status === 'done'">✓</text>
            <text v-else-if="step.status === 'failed'">!</text>
          </view>
          <view class="import-progress-step__main">
            <text class="import-progress-step__title">{{ step.title }}</text>
            <text class="import-progress-step__desc">{{ step.desc }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'ImportProgress',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    title: {
      type: String,
      default: '正在导入课表'
    },
    message: {
      type: String,
      default: '正在准备导入'
    },
    status: {
      type: String,
      default: 'running'
    },
    steps: {
      type: Array,
      default: () => []
    }
  }
}
</script>

<style lang="scss" scoped>
.import-progress-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2200;
  background: rgba(0, 0, 0, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  box-sizing: border-box;
}

.import-progress-card {
  width: 620rpx;
  max-width: 100%;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 24rpx 64rpx rgba(0, 0, 0, 0.22);
  overflow: hidden;
}

.import-progress-head {
  padding: 34rpx 34rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.import-progress-icon {
  width: 60rpx;
  height: 60rpx;
  border-radius: 999rpx;
  background: #eaf2ff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3f7df2;
}

.import-progress-icon--done {
  background: #eaf8f3;
  color: #168b6a;
}

.import-progress-icon--failed {
  background: #fff2eb;
  color: #d95b37;
}

.import-progress-spinner {
  width: 32rpx;
  height: 32rpx;
  border-radius: 999rpx;
  border: 5rpx solid rgba(63, 125, 242, 0.22);
  border-top-color: #3f7df2;
  animation: import-progress-spin 0.8s linear infinite;
}

.import-progress-symbol {
  font-size: 34rpx;
  font-weight: 800;
  line-height: 1;
}

.import-progress-title {
  display: block;
  margin-top: 18rpx;
  font-size: 34rpx;
  font-weight: 800;
  color: #111827;
}

.import-progress-message {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.5;
  color: #7a8796;
}

.import-progress-steps {
  padding: 4rpx 34rpx 34rpx;
}

.import-progress-step {
  display: flex;
  gap: 18rpx;
  padding: 16rpx 0;
  border-top: 1rpx solid #edf1f5;
}

.import-progress-step__dot {
  width: 34rpx;
  height: 34rpx;
  margin-top: 4rpx;
  border-radius: 999rpx;
  background: #eef3f8;
  border: 4rpx solid #eef3f8;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20rpx;
  font-weight: 800;
  flex-shrink: 0;
}

.import-progress-step__main {
  flex: 1;
  min-width: 0;
}

.import-progress-step__title {
  display: block;
  font-size: 27rpx;
  font-weight: 800;
  color: #8a96a6;
}

.import-progress-step__desc {
  display: block;
  margin-top: 6rpx;
  font-size: 23rpx;
  line-height: 1.45;
  color: #a0aaba;
}

.import-progress-step--active .import-progress-step__dot {
  background: #3f7df2;
  border-color: rgba(63, 125, 242, 0.18);
  box-shadow: 0 0 0 8rpx rgba(63, 125, 242, 0.08);
}

.import-progress-step--active .import-progress-step__title {
  color: #21334d;
}

.import-progress-step--active .import-progress-step__desc {
  color: #5d738c;
}

.import-progress-step--done .import-progress-step__dot {
  background: #23b08a;
  border-color: #23b08a;
}

.import-progress-step--done .import-progress-step__title {
  color: #21334d;
}

.import-progress-step--done .import-progress-step__desc {
  color: #6e8198;
}

.import-progress-step--failed .import-progress-step__dot {
  background: #d95b37;
  border-color: #d95b37;
}

.import-progress-step--failed .import-progress-step__title {
  color: #d95b37;
}

@keyframes import-progress-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
