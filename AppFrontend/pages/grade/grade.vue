<template>
  <view class="page">
    <nav-bar title="成绩查询" :showBack="true" />
    <view class="summary-card">
      <text class="summary-title">本学期成绩概览</text>
      <text class="summary-value">GPA 3.82</text>
      <text class="summary-desc">当前页面为移动端补全页，后续可接入教务系统真实成绩数据。</text>
    </view>
    <view class="grade-list">
      <view v-for="item in grades" :key="item.name" class="grade-item">
        <view>
          <text class="course-name">{{ item.name }}</text>
          <text class="course-meta">{{ item.teacher }} · {{ item.credit }} 学分</text>
        </view>
        <text class="score">{{ item.score }}</text>
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
      grades: [
        { name: '高等数学', teacher: '李老师', credit: 4, score: '92' },
        { name: '大学英语', teacher: '王老师', credit: 3, score: '88' },
        { name: '数据结构', teacher: '陈老师', credit: 4, score: '95' },
        { name: '计算机网络', teacher: '张老师', credit: 3, score: '90' }
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

.summary-card,
.grade-list {
  margin: 24rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: #fff;
}

.summary-title,
.course-name {
  display: block;
  color: #111827;
  font-weight: 700;
}

.summary-value {
  display: block;
  margin-top: 12rpx;
  font-size: 56rpx;
  color: #111827;
  font-weight: 700;
}

.summary-desc,
.course-meta {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
}

.grade-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.grade-item + .grade-item {
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #f3f4f6;
}

.score {
  font-size: 40rpx;
  font-weight: 700;
  color: #111827;
}
</style>
