<template>
  <view class="publish-container">
    <nav-bar title="发布活动" :showBack="true" />
    
    <scroll-view class="publish-content" scroll-y>
      <!-- 活动封面 -->
      <view class="form-item">
        <text class="form-label">活动封面</text>
        <view class="cover-upload" @click="chooseCover">
          <image v-if="form.cover" class="cover-preview" :src="form.cover" mode="aspectFill" />
          <view v-else class="upload-placeholder">
            <text class="upload-text">点击上传封面</text>
          </view>
        </view>
      </view>
      
      <!-- 活动标题 -->
      <view class="form-item">
        <text class="form-label">活动标题 <text class="required">*</text></text>
        <input 
          class="form-input"
          v-model="form.title"
          placeholder="请输入活动标题"
          maxlength="50"
        />
      </view>
      
      <!-- 活动类型 -->
      <view class="form-item">
        <text class="form-label">活动类型 <text class="required">*</text></text>
        <view class="type-list">
          <view 
            v-for="(item, index) in typeList" 
            :key="index"
            class="type-item"
            :class="{ active: form.type === item.id }"
            @click="form.type = item.id"
          >
            {{ item.name }}
          </view>
        </view>
      </view>
      
      <!-- 活动时间 -->
      <view class="form-item">
        <text class="form-label">活动时间 <text class="required">*</text></text>
        <view class="time-picker" @click="showTimePicker = true">
          <text class="picker-text">{{ form.startTime || '请选择活动时间' }}</text>
          <text class="picker-arrow">›</text>
        </view>
      </view>
      
      <!-- 活动地点 -->
      <view class="form-item">
        <text class="form-label">活动地点 <text class="required">*</text></text>
        <input 
          class="form-input"
          v-model="form.location"
          placeholder="请输入活动地点"
        />
      </view>
      
      <!-- 人数限制 -->
      <view class="form-item">
        <text class="form-label">人数限制 <text class="required">*</text></text>
        <input 
          class="form-input"
          v-model.number="form.maxPeople"
          type="number"
          placeholder="请输入最大报名人数"
        />
      </view>
      
      <!-- 活动详情 -->
      <view class="form-item">
        <text class="form-label">活动详情</text>
        <textarea 
          class="form-textarea"
          v-model="form.description"
          placeholder="请输入活动详情，包括活动内容、注意事项等"
          maxlength="500"
        />
        <text class="word-count">{{ form.description.length }}/500</text>
      </view>
      
      <!-- 底部安全区域 -->
      <view class="safe-area"></view>
    </scroll-view>
    
    <!-- 底部提交按钮 -->
    <view class="bottom-bar">
      <view class="submit-btn" @click="handleSubmit">发布活动</view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { NavBar },
  data() {
    return {
      form: {
        cover: '',
        title: '',
        type: 1,
        startTime: '',
        location: '',
        maxPeople: '',
        description: ''
      },
      typeList: [
        { id: 1, name: '邻里互助' },
        { id: 2, name: '志愿服务' },
        { id: 3, name: '文体活动' },
        { id: 4, name: '公益活动' },
        { id: 5, name: '技能培训' }
      ],
      showTimePicker: false
    }
  },
  methods: {
    // 选择封面
    chooseCover() {
      uni.chooseImage({
        count: 1,
        success: (res) => {
          this.form.cover = res.tempFilePaths[0]
        }
      })
    },
    
    // 提交表单
    handleSubmit() {
      // 表单验证
      if (!this.form.title.trim()) {
        uni.showToast({ title: '请输入活动标题', icon: 'none' })
        return
      }
      if (!this.form.startTime) {
        uni.showToast({ title: '请选择活动时间', icon: 'none' })
        return
      }
      if (!this.form.location.trim()) {
        uni.showToast({ title: '请输入活动地点', icon: 'none' })
        return
      }
      if (!this.form.maxPeople) {
        uni.showToast({ title: '请输入人数限制', icon: 'none' })
        return
      }
      
      // 提交数据
      uni.showLoading({ title: '发布中...' })
      
      setTimeout(() => {
        uni.hideLoading()
        uni.showToast({
          title: '发布成功',
          icon: 'success',
          success: () => {
            setTimeout(() => {
              uni.navigateBack()
            }, 1500)
          }
        })
      }, 1500)
    }
  }
}
</script>

<style lang="scss">
.publish-container {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 140rpx;
}

.publish-content {
  height: 100vh;
}

.form-item {
  background-color: #FFFFFF;
  padding: 32rpx;
  margin-bottom: 24rpx;
}

.form-label {
  font-size: 30rpx;
  color: #333;
  font-weight: 500;
  margin-bottom: 20rpx;
  display: block;
}

.required {
  color: #FF4D4F;
}

.cover-upload {
  width: 100%;
  height: 360rpx;
  background-color: #F7F7F9;
  border-radius: 16rpx;
  overflow: hidden;
}

.cover-preview {
  width: 100%;
  height: 100%;
}

.upload-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.upload-text {
  font-size: 28rpx;
  color: #999;
}

.form-input {
  height: 80rpx;
  background-color: #F7F7F9;
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #333;
}

.type-list {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}

.type-item {
  padding: 16rpx 32rpx;
  background-color: #F7F7F9;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #666;
}

.type-item.active {
  background-color: #E6F7FF;
  color: #007AFF;
}

.time-picker {
  height: 80rpx;
  background-color: #F7F7F9;
  border-radius: 12rpx;
  padding: 0 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.picker-text {
  font-size: 28rpx;
  color: #333;
}

.picker-text:empty,
.picker-text[data-placeholder] {
  color: #999;
}

.picker-arrow {
  width: 32rpx;
  height: 32rpx;
  color: #999;
  font-size: 36rpx;
  line-height: 1;
  text-align: center;
}

.form-textarea {
  height: 240rpx;
  background-color: #F7F7F9;
  border-radius: 12rpx;
  padding: 24rpx;
  font-size: 28rpx;
  color: #333;
  line-height: 1.6;
}

.word-count {
  text-align: right;
  font-size: 24rpx;
  color: #999;
  margin-top: 12rpx;
}

.safe-area {
  height: 40rpx;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 24rpx 32rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  background-color: #FFFFFF;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.submit-btn {
  height: 88rpx;
  background: linear-gradient(135deg, #007AFF, #00C6FF);
  border-radius: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #FFFFFF;
  font-weight: 500;
}
</style>
