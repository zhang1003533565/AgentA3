<template>
  <view class="post-editor">
    <view class="title-section">
      <view class="title-with-required">
        <input
          class="title-input"
          v-model="form.title"
          placeholder="添加标题"
          :maxlength="50"
        />
        <view class="char-count-wrapper">
          <text class="char-count-required">*</text>
          <text class="char-count">{{ (form.title || '').length }}/50</text>
        </view>
      </view>
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
            <text class="topic-icon">{{ item.icon }}</text> # {{ item.name }}
          </view>
        </view>
      </scroll-view>
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

  .title-with-required {
    position: relative;
    display: flex;
    align-items: flex-start;
  }

  .title-input {
    font-size: 36rpx;
    font-weight: 600;
    color: #1D1D1F;
    width: 100%;
    display: block;
  }

  .char-count-wrapper {
    position: absolute;
    right: 30rpx;
    top: 24rpx;
    display: flex;
    align-items: center;
  }

  .char-count-required {
    color: #FF2E26;
    font-size: 36rpx;
    font-weight: 600;
    margin-right: 8rpx;
  }

  .char-count {
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
    display: block;
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
    display: inline-flex;
    align-items: center;
    padding: 12rpx 28rpx;
    margin-right: 16rpx;
    font-size: 26rpx;
    color: #666;
    background-color: #F5F5F7;
    border-radius: 32rpx;

    .topic-icon {
      margin-right: 8rpx;
    }

    &.active {
      color: #FFFFFF;
      background-color: #5C7A99;
    }
  }
}
</style>