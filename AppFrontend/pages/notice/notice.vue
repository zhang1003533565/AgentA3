<template>
  <view class="page">
    <nav-bar title="通知公告" :showBack="true" />
    <view class="notice-list">
      <view v-for="item in notices" :key="item.title" class="notice-item">
        <view class="notice-top">
          <text class="notice-tag">{{ item.tag }}</text>
          <text class="notice-time">{{ item.time }}</text>
        </view>
        <text class="notice-title">{{ item.title }}</text>
        <text class="notice-content">{{ item.content }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { NavBar },
  data() {
    return {
      notices: [
        { tag: '教务', time: '2026-04-01 10:30', title: '关于 2026 年清明节放假安排的通知', content: '4 月 4 日至 4 月 6 日放假调休，请各位同学合理安排学习与出行。' },
        { tag: '图书馆', time: '2026-03-31 16:20', title: '图书馆电子资源使用培训讲座', content: '本周五晚 19:00 在图书馆学术报告厅举行，欢迎同学报名参加。' },
        { tag: '学工', time: '2026-03-30 14:00', title: '2026 年春季学期奖学金评选开始', content: '请符合条件的同学于本周内完成系统申请与材料提交。' }
      ]
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f9;
}

.notice-list {
  padding: 24rpx;
}

.notice-item {
  padding: 28rpx;
  border-radius: 24rpx;
  background: #fff;
}

.notice-item + .notice-item {
  margin-top: 24rpx;
}

.notice-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.notice-tag,
.notice-time,
.notice-content {
  font-size: 24rpx;
  color: #6b7280;
}

.notice-title {
  display: block;
  margin-top: 14rpx;
  font-size: 30rpx;
  line-height: 1.5;
  color: #111827;
  font-weight: 700;
}

.notice-content {
  display: block;
  margin-top: 12rpx;
  line-height: 1.7;
}
</style>
