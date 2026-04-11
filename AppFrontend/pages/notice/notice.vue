<template>
  <view class="page">
    <nav-bar title="通知公告" :showBack="true" />
    <view class="notice-list">
      <view v-for="item in notices" :key="item.id" class="notice-item">
        <view class="notice-main" @click="goToDetail(item.id)">
          <view class="notice-top">
            <view class="notice-tag-wrap">
              <text class="notice-tag">{{ item.tag }}</text>
              <text v-if="item.isTop" class="notice-top-badge">置顶</text>
            </view>
            <text class="notice-time">{{ item.time }}</text>
          </view>
          <text class="notice-title">{{ item.title }}</text>
          <text class="notice-content">{{ item.content }}</text>
        </view>
        <!-- 管理按钮（仅管理员/教师显示） -->
        <view v-if="canPublish" class="notice-actions">
          <text v-if="item.isTop" class="action-btn cancel-top" @click="cancelTop(item)">取消置顶</text>
          <text v-else class="action-btn set-top" @click="setTop(item)">置顶</text>
          <text class="action-btn delete" @click="confirmDelete(item)">删除</text>
        </view>
      </view>
      <view v-if="notices.length === 0" class="empty-state">
        <text class="empty-text">暂无公告</text>
      </view>
    </view>
    
    <!-- 发布按钮（仅管理员/教师显示） -->
    <view v-if="canPublish" class="publish-btn" @click="showPublishForm">
      <text class="publish-icon">+</text>
    </view>
    
    <!-- 发布表单弹窗 -->
    <view v-if="showForm" class="form-overlay" @click="closeForm">
      <view class="form-container" @click.stop>
        <view class="form-header">
          <text class="form-title">发布公告</text>
          <text class="form-close" @click="closeForm">×</text>
        </view>
        <view class="form-body">
          <input 
            class="form-input" 
            v-model="form.title" 
            placeholder="请输入公告标题" 
            maxlength="200"
          />
          <textarea 
            class="form-textarea" 
            v-model="form.content" 
            placeholder="请输入公告内容"
            maxlength="2000"
          />
        </view>
        <view class="form-footer">
          <button class="form-btn cancel" @click="closeForm">取消</button>
          <button class="form-btn confirm" @click="submitPublish">发布</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getEnabledAnnouncements, createAnnouncement, updateAnnouncement, deleteAnnouncement } from '@/api/notice.js'

export default {
  components: { NavBar },
  data() {
    return {
      notices: [],
      showForm: false,
      canPublish: false,
      form: {
        title: '',
        content: '',
        isTop: false
      }
    }
  },
  onLoad() {
    this.fetchNotices()
    this.checkUserRole()
  },
  onShow() {
    this.fetchNotices()
  },
  methods: {
    async fetchNotices() {
      try {
        const res = await getEnabledAnnouncements()
        if (res.code === 200 && res.data) {
          this.notices = res.data.map(item => ({
            id: item.id,
            tag: '公告',
            time: item.createTime,
            title: item.title,
            content: item.content,
            isTop: item.isTop
          }))
        }
      } catch (err) {
        console.error('获取公告列表失败:', err)
        uni.showToast({ title: '获取公告失败', icon: 'none' })
      }
    },
    goToDetail(id) {
      uni.navigateTo({
        url: `/subpackage_notice/noticeDetail/noticeDetail?id=${id}`
      })
    },
    checkUserRole() {
      const userInfo = uni.getStorageSync('userInfo')
      if (userInfo) {
        const user = JSON.parse(userInfo)
        this.canPublish = user.role === 'ADMIN' || user.role === 'TEACHER'
      }
    },
    showPublishForm() {
      this.showForm = true
    },
    closeForm() {
      this.showForm = false
      this.form.title = ''
      this.form.content = ''
      this.form.isTop = false
    },
    async submitPublish() {
      if (!this.form.title.trim()) {
        uni.showToast({ title: '请输入标题', icon: 'none' })
        return
      }
      if (!this.form.content.trim()) {
        uni.showToast({ title: '请输入内容', icon: 'none' })
        return
      }
      try {
        const res = await createAnnouncement({
          title: this.form.title,
          content: this.form.content,
          isTop: this.form.isTop
        })
        if (res.code === 200) {
          uni.showToast({ title: '发布成功', icon: 'success' })
          this.closeForm()
          this.fetchNotices()
        }
      } catch (err) {
        console.error('发布失败:', err)
        uni.showToast({ title: '发布失败', icon: 'none' })
      }
    },
    async setTop(item) {
      try {
        const res = await updateAnnouncement(item.id, { isTop: true })
        if (res.code === 200) {
          uni.showToast({ title: '置顶成功', icon: 'success' })
          this.fetchNotices()
        }
      } catch (err) {
        console.error('置顶失败:', err)
        uni.showToast({ title: '置顶失败', icon: 'none' })
      }
    },
    async cancelTop(item) {
      try {
        const res = await updateAnnouncement(item.id, { isTop: false })
        if (res.code === 200) {
          uni.showToast({ title: '已取消置顶', icon: 'success' })
          this.fetchNotices()
        }
      } catch (err) {
        console.error('取消置顶失败:', err)
        uni.showToast({ title: '取消置顶失败', icon: 'none' })
      }
    },
    confirmDelete(item) {
      uni.showModal({
        title: '确认删除',
        content: `确定要删除公告"${item.title}"吗？`,
        confirmColor: '#ff4d4f',
        success: (res) => {
          if (res.confirm) {
            this.doDelete(item.id)
          }
        }
      })
    },
    async doDelete(id) {
      try {
        const res = await deleteAnnouncement(id)
        if (res.code === 200) {
          uni.showToast({ title: '删除成功', icon: 'success' })
          this.fetchNotices()
        }
      } catch (err) {
        console.error('删除失败:', err)
        uni.showToast({ title: '删除失败', icon: 'none' })
      }
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

.notice-main {
  margin-bottom: 20rpx;
}

.notice-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.notice-tag-wrap {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.notice-tag,
.notice-time,
.notice-content {
  font-size: 24rpx;
  color: #6b7280;
}

.notice-top-badge {
  font-size: 20rpx;
  color: #ff6b6b;
  background: rgba(255, 107, 107, 0.1);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.notice-actions {
  display: flex;
  gap: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #f0f0f0;
}

.action-btn {
  font-size: 26rpx;
  padding: 12rpx 24rpx;
  border-radius: 12rpx;
}

.action-btn.set-top {
  color: #62b6b3;
  background: rgba(98, 182, 179, 0.1);
}

.action-btn.cancel-top {
  color: #999;
  background: #f5f5f5;
}

.action-btn.delete {
  color: #ff4d4f;
  background: rgba(255, 77, 79, 0.1);
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

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #999;
}

/* 发布按钮 */
.publish-btn {
  position: fixed;
  right: 40rpx;
  bottom: 100rpx;
  width: 100rpx;
  height: 100rpx;
  background: linear-gradient(135deg, #62b6b3, #4f8f90);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(98, 182, 179, 0.4);
  z-index: 100;
}

.publish-icon {
  font-size: 48rpx;
  color: #fff;
  font-weight: 300;
}

/* 表单弹窗 */
.form-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.form-container {
  width: 80%;
  max-width: 600rpx;
  background: #fff;
  border-radius: 24rpx;
  overflow: hidden;
}

.form-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30rpx;
  border-bottom: 1rpx solid #eee;
}

.form-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
}

.form-close {
  font-size: 40rpx;
  color: #999;
  padding: 0 10rpx;
}

.form-body {
  padding: 30rpx;
}

.form-input {
  width: 100%;
  height: 80rpx;
  border: 1rpx solid #e5e5e5;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  margin-bottom: 20rpx;
  box-sizing: border-box;
}

.form-textarea {
  width: 100%;
  height: 200rpx;
  border: 1rpx solid #e5e5e5;
  border-radius: 12rpx;
  padding: 20rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}

.form-footer {
  display: flex;
  padding: 20rpx 30rpx 40rpx;
  gap: 20rpx;
}

.form-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 12rpx;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
}

.form-btn.cancel {
  background: #f5f5f5;
  color: #666;
}

.form-btn.confirm {
  background: linear-gradient(135deg, #62b6b3, #4f8f90);
  color: #fff;
}
</style>
