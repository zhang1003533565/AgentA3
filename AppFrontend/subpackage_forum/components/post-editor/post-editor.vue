<template>
  <view class="post-editor">
    <view class="title-section">
      <input
        class="title-input"
        v-model="form.title"
        placeholder="添加标题（选填）"
        :maxlength="50"
      />
      <text class="char-count">{{ (form.title || '').length }}/50</text>
    </view>

    <view class="content-section">
      <textarea
        class="content-input"
        v-model="form.content"
        placeholder="分享你的想法、经验或问题..."
        :maxlength="2000"
      />
      <text class="char-count">{{ (form.content || '').length }}/2000</text>
    </view>

    <view class="image-section">
      <view class="image-list">
        <view
          v-for="(img, index) in form.images"
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
          v-if="(form.images || []).length < 9"
          @click="chooseImage"
        >
          <text class="add-icon">+</text>
          <text class="add-text">添加图片</text>
        </view>
      </view>
    </view>

    <view class="topic-section">
      <text class="section-label">选择话题</text>
      <scroll-view class="topic-scroll" scroll-x :show-scrollbar="false">
        <view class="topic-list">
          <view
            v-for="(item, index) in topics"
            :key="index"
            class="topic-item"
            :class="{ active: form.topicId === item.id }"
            @click="selectTopic(item.id)"
          >
            # {{ item.name }}
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="setting-section">
      <view
        class="setting-item"
        :class="{ 'setting-item--pressed': switchPressed }"
        @click="toggleAnonymous"
        @touchstart="switchPressed = true"
        @touchend="switchPressed = false"
        @touchcancel="switchPressed = false"
      >
        <text class="setting-label">匿名发布</text>
        <view class="setting-switch" :class="{ active: !!form.isAnonymous }">
          <view class="setting-switch-thumb"></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getUploadErrorMessage, uploadImages } from '@/utils/upload'
export default {
  name: 'PostEditor',
  props: {
    form: {
      type: Object,
      required: true
    },
    topics: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      switchPressed: false
    }
  },
  methods: {
    chooseImage() {
      const current = this.form.images || []
      const remaining = 9 - current.length
      uni.chooseImage({
        count: remaining,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: async (res) => {
          const files = res.tempFilePaths
          try {
            const urls = await uploadImages(files)
            const next = [...(this.form.images || []), ...urls]
            this.$set(this.form, 'images', next)
          } catch (e) {
            uni.showToast({ title: getUploadErrorMessage(e), icon: 'none' })
          }
        }
      })
    },
    deleteImage(index) {
      const next = [...(this.form.images || [])]
      next.splice(index, 1)
      this.$set(this.form, 'images', next)
    },
    selectTopic(topicId) {
      this.$set(this.form, 'topicId', this.form.topicId === topicId ? null : topicId)
    },
    toggleAnonymous() {
      this.$set(this.form, 'isAnonymous', !this.form.isAnonymous)
    }
  }
}
</script>

<style lang="scss">
.post-editor {
  width: 100%;
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
    scrollbar-width: none;
    &::-webkit-scrollbar {
      display: none;
    }
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
    transition: opacity 0.15s ease, transform 0.15s ease;

    &.setting-item--pressed {
      opacity: 0.85;
      transform: scale(0.99);
    }

    .setting-label {
      font-size: 28rpx;
      color: #1D1D1F;
    }

    .setting-switch {
      position: relative;
      width: 88rpx;
      height: 48rpx;
      background-color: #E5E5EA;
      border-radius: 24rpx;
      transition: background-color 0.25s cubic-bezier(0.4, 0, 0.2, 1);

      &.active {
        background-color: #5C7A99;
      }
    }

    .setting-switch-thumb {
      position: absolute;
      left: 4rpx;
      top: 4rpx;
      width: 40rpx;
      height: 40rpx;
      border-radius: 50%;
      background-color: #FFFFFF;
      box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.15);
      transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .setting-switch.active .setting-switch-thumb {
      transform: translateX(40rpx);
    }
  }
}
</style>
