<template>
  <view class="settings-page">
    <nav-bar title="课表设置" :showBack="true" fixed placeholder />

    <view class="settings-shell">
      <view class="settings-card" @click="openAccountSettings">
        <view class="settings-card__main">
          <text class="settings-card__title">教务账号设置</text>
          <text class="settings-card__desc">维护教务系统学号和密码，用于导入课表</text>
        </view>
        <text class="settings-card__action">修改</text>
      </view>

      <view class="settings-card" @click="openPeriodSettings">
        <view class="settings-card__main">
          <text class="settings-card__title">节次时间设置</text>
          <text class="settings-card__desc">修改第 1 节、第 2 节等开始和结束时间</text>
        </view>
        <text class="settings-card__action settings-card__action--primary">设置</text>
      </view>

      <view class="tips-card">
        <text class="tips-card__title">说明</text>
        <text class="tips-card__line">节次时间会影响课表左侧时间和课程详情里的时间范围。</text>
        <text class="tips-card__line">课程位置仍按“第几节”显示，不会改变导入的课程节次。</text>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { NavBar },
  methods: {
    openAccountSettings() {
      uni.navigateTo({
        url: '/subpackage_schedule/scheduleAccountSettings/scheduleAccountSettings'
      })
    },
    openPeriodSettings() {
      uni.navigateTo({
        url: '/subpackage_schedule/schedulePeriodSettings/schedulePeriodSettings'
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.settings-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef7ff 0%, #f8fbff 52%, #f7f9fb 100%);
}

.settings-shell {
  padding: 30rpx 24rpx 44rpx;
  box-sizing: border-box;
}

.settings-card,
.tips-card {
  background: rgba(255, 255, 255, 0.96);
  border-radius: 24rpx;
  box-shadow: 0 14rpx 32rpx rgba(65, 102, 153, 0.08);
}

.settings-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 28rpx;
  margin-bottom: 22rpx;
  border: 2rpx solid rgba(220, 233, 251, 0.78);
  box-sizing: border-box;
}

.settings-card__main {
  flex: 1;
  min-width: 0;
}

.settings-card__title,
.tips-card__title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #21334d;
}

.settings-card__desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7890ad;
  line-height: 1.45;
}

.settings-card__action {
  flex-shrink: 0;
  min-width: 88rpx;
  height: 56rpx;
  padding: 0 18rpx;
  border-radius: 16rpx;
  background: #f2f6fb;
  color: #536b87;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  font-size: 24rpx;
  font-weight: 700;
  white-space: nowrap;
}

.settings-card__action--primary {
  background: #1d1d1f;
  color: #fff;
}

.tips-card {
  padding: 26rpx 28rpx;
  margin-top: 24rpx;
}

.tips-card__line {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #5d738c;
}
</style>
