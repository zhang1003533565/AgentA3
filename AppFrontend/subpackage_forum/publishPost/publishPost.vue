<template>
  <view class="publish-container">
    <!-- 标题输入 -->
    <view class="title-section">
      <input 
        class="title-input"
        v-model="postForm.title"
        placeholder="添加标题（选填）"
        :maxlength="50"
      />
      <text class="char-count">{{ postForm.title.length }}/50</text>
    </view>

    <!-- 内容输入 -->
    <view class="content-section">
      <textarea 
        class="content-input"
        v-model="postForm.content"
        placeholder="分享你的想法、经验或问题..."
        :maxlength="2000"
      />
      <text class="char-count">{{ postForm.content.length }}/2000</text>
    </view>

    <!-- 图片上传 -->
    <view class="image-section">
      <view class="image-list">
        <view 
          v-for="(img, index) in postForm.images" 
          :key="index"
          class="image-item"
        >
          <image class="preview-image" :src="img" mode="aspectFill" />
          <view class="delete-btn" @click="deleteImage(index)">
            <text>×</text>
          </view>
        </view>
        <view 
          class="add-image-btn" 
          v-if="postForm.images.length < 9"
          @click="chooseImage"
        >
          <text class="add-icon">+</text>
          <text class="add-text">添加图片</text>
        </view>
      </view>
    </view>

    <!-- 话题选择 -->
    <view class="topic-section">
      <text class="section-label">选择话题</text>
      <scroll-view class="topic-scroll" scroll-x>
        <view class="topic-list">
          <view 
            v-for="(item, index) in topics" 
            :key="index"
            class="topic-item"
            :class="{ active: postForm.topicId === item.id }"
            @click="selectTopic(item.id)"
          >
            # {{ item.name }}
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 发布设置 -->
    <view class="setting-section">
      <view class="setting-item" @click="toggleAnonymous">
        <text class="setting-label">匿名发布</text>
        <view class="setting-switch" :class="{ active: postForm.isAnonymous }">
          <text>{{ postForm.isAnonymous ? '开' : '关' }}</text>
        </view>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="draft-btn" @click="saveDraft">
        <text>存草稿</text>
      </view>
      <view class="publish-btn" :class="{ disabled: !canPublish }" @click="publishPost">
        <text>发布</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      postForm: {
        title: '',
        content: '',
        images: [],
        topicId: null,
        isAnonymous: false
      },
      topics: [
        { id: 1, name: '校园生活' },
        { id: 2, name: '学习交流' },
        { id: 3, name: '求职招聘' },
        { id: 4, name: '二手交易' },
        { id: 5, name: '情感树洞' },
        { id: 6, name: '美食探店' },
        { id: 7, name: '求助问答' },
        { id: 8, name: '失物招领' }
      ]
    }
  },
  computed: {
    canPublish() {
      return this.postForm.content.trim().length >= 10
    }
  },
  methods: {
    // 选择图片
    chooseImage() {
      const remaining = 9 - this.postForm.images.length
      uni.chooseImage({
        count: remaining,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: (res) => {
          this.postForm.images = [...this.postForm.images, ...res.tempFilePaths]
        }
      })
    },

    // 删除图片
    deleteImage(index) {
      this.postForm.images.splice(index, 1)
    },

    // 选择话题
    selectTopic(topicId) {
      this.postForm.topicId = this.postForm.topicId === topicId ? null : topicId
    },

    // 切换匿名
    toggleAnonymous() {
      this.postForm.isAnonymous = !this.postForm.isAnonymous
    },

    // 存草稿
    saveDraft() {
      if (!this.postForm.content.trim()) {
        uni.showToast({ title: '请输入内容', icon: 'none' })
        return
      }
      // TODO: 保存到本地存储
      uni.showToast({ title: '已保存草稿', icon: 'success' })
    },

    // 发布帖子
    publishPost() {
      if (!this.canPublish) {
        uni.showToast({ title: '内容至少10个字', icon: 'none' })
        return
      }

      // TODO: 调用后端接口
      uni.showLoading({ title: '发布中...' })
      setTimeout(() => {
        uni.hideLoading()
        uni.showToast({ title: '发布成功', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 1500)
      }, 1000)
    }
  }
}
</script>

<style lang="scss">
.publish-container {
  min-height: 100vh;
  background-color: #FFFFFF;
  padding-bottom: 120rpx;
}

.title-section {
  padding: 24rpx 30rpx;
  border-bottom: 1rpx solid #F0F0F0;
  position: relative;

  .title-input {
    font-size: 36rpx;
    font-weight: 600;
    color: #1D1D1F;
    width: 100%;
  }

  .char-count {
    position: absolute;
    right: 30rpx;
    bottom: 24rpx;
    font-size: 22rpx;
    color: #8E8E93;
  }
}

.content-section {
  padding: 24rpx 30rpx;
  min-height: 300rpx;
  position: relative;

  .content-input {
    width: 100%;
    min-height: 300rpx;
    font-size: 30rpx;
    color: #1D1D1F;
    line-height: 1.8;
  }

  .char-count {
    position: absolute;
    right: 30rpx;
    bottom: 24rpx;
    font-size: 22rpx;
    color: #8E8E93;
  }
}

.image-section {
  padding: 0 30rpx 24rpx;
  border-bottom: 1rpx solid #F0F0F0;

  .image-list {
    display: flex;
    flex-wrap: wrap;
    gap: 16rpx;

    .image-item {
      position: relative;
      width: 200rpx;
      height: 200rpx;

      .preview-image {
        width: 100%;
        height: 100%;
        border-radius: 12rpx;
      }

      .delete-btn {
        position: absolute;
        top: -12rpx;
        right: -12rpx;
        width: 40rpx;
        height: 40rpx;
        background-color: rgba(0, 0, 0, 0.6);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;

        text {
          color: #FFFFFF;
          font-size: 28rpx;
        }
      }
    }

    .add-image-btn {
      width: 200rpx;
      height: 200rpx;
      background-color: #F7F7F9;
      border-radius: 12rpx;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;

      .add-icon {
        font-size: 48rpx;
        color: #8E8E93;
      }

      .add-text {
        font-size: 24rpx;
        color: #8E8E93;
        margin-top: 8rpx;
      }
    }
  }
}

.topic-section {
  padding: 24rpx 30rpx;
  border-bottom: 1rpx solid #F0F0F0;

  .section-label {
    font-size: 28rpx;
    color: #1D1D1F;
    font-weight: 600;
    margin-bottom: 16rpx;
    display: block;
  }

  .topic-scroll {
    white-space: nowrap;
    margin: 0 -30rpx;
    padding: 0 30rpx;
  }

  .topic-list {
    display: flex;
  }

  .topic-item {
    display: inline-block;
    padding: 12rpx 28rpx;
    margin-right: 16rpx;
    font-size: 26rpx;
    color: #666;
    background-color: #F5F5F7;
    border-radius: 32rpx;

    &.active {
      color: #FFFFFF;
      background-color: #5C7A99;
    }
  }
}

.setting-section {
  padding: 0 30rpx;

  .setting-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 28rpx 0;
    border-bottom: 1rpx solid #F0F0F0;

    .setting-label {
      font-size: 28rpx;
      color: #1D1D1F;
    }

    .setting-switch {
      width: 80rpx;
      height: 48rpx;
      background-color: #E5E5EA;
      border-radius: 24rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.3s;

      text {
        font-size: 22rpx;
        color: #FFFFFF;
      }

      &.active {
        background-color: #5C7A99;
      }
    }
  }
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 16rpx 30rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background-color: #FFFFFF;
  border-top: 1rpx solid #F0F0F0;

  .draft-btn {
    padding: 20rpx 40rpx;
    margin-right: 20rpx;

    text {
      font-size: 28rpx;
      color: #8E8E93;
    }
  }

  .publish-btn {
    padding: 20rpx 60rpx;
    background-color: #5C7A99;
    border-radius: 40rpx;

    text {
      font-size: 28rpx;
      color: #FFFFFF;
      font-weight: 500;
    }

    &.disabled {
      background-color: #E5E5EA;

      text {
        color: #8E8E93;
      }
    }
  }
}
</style>
