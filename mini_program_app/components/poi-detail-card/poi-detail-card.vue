<template>
  <view class="poi-detail-sheet" :class="{ 'poi-detail-sheet--show': visible }" @click.stop>
    <view v-if="visible" class="poi-detail-sheet__card">
      <view v-if="showCoverImage" class="poi-detail-sheet__hero">
        <image
          class="poi-detail-sheet__hero-img"
          :src="imageUrl"
          mode="aspectFill"
          @error="onImageError"
        />
        <view class="poi-detail-sheet__hero-gradient" />
      </view>

      <view class="poi-detail-sheet__body">
        <view class="poi-detail-sheet__title-row">
          <view class="poi-detail-sheet__title-main">
            <text class="poi-detail-sheet__name">{{ name }}</text>
            <text v-if="zone" class="poi-detail-sheet__zone">{{ zone }}</text>
          </view>
          <text v-if="distance" class="poi-detail-sheet__distance">{{ distance }}</text>
        </view>

        <view class="poi-detail-sheet__bento">
          <view class="poi-detail-sheet__bento-box poi-detail-sheet__bento-box--desc">
            <text class="poi-detail-sheet__bento-label">详细介绍</text>
            <text class="poi-detail-sheet__bento-desc">{{ displayDescription }}</text>
          </view>
          <view
            class="poi-detail-sheet__bento-box poi-detail-sheet__bento-box--meta"
            :class="{ 'poi-detail-sheet__bento-box--clickable': !!secondaryLabel }"
            @click.stop="onBentoServiceClick"
          >
            <text class="poi-detail-sheet__bento-label">位置与服务</text>
            <view v-if="serviceLine" class="poi-detail-sheet__bento-service">
              <text v-if="secondaryEmoji" class="poi-detail-sheet__bento-emoji">{{ secondaryEmoji }}</text>
              <text class="poi-detail-sheet__bento-service-text">{{ serviceLine }}</text>
            </view>
          </view>
        </view>

        <button
          v-if="primaryLabel"
          class="poi-detail-sheet__btn-primary"
          @click.stop="$emit('primary-click')"
        >
          {{ primaryLabel }}
        </button>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'PoiDetailCard',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    name: {
      type: String,
      default: ''
    },
    distance: {
      type: String,
      default: ''
    },
    zone: {
      type: String,
      default: ''
    },
    description: {
      type: String,
      default: ''
    },
    imageUrl: {
      type: String,
      default: ''
    },
    secondaryEmoji: {
      type: String,
      default: ''
    },
    secondaryLabel: {
      type: String,
      default: ''
    },
    /** 动态服务补充文案，如「今日菜单」，与 secondaryLabel 组合展示 */
    serviceHint: {
      type: String,
      default: ''
    },
    primaryLabel: {
      type: String,
      default: ''
    }
  },
  emits: ['secondary-click', 'primary-click'],
  data() {
    return {
      imageLoadFailed: false
    }
  },
  computed: {
    normalizedImageUrl() {
      return (this.imageUrl || '').trim()
    },
    showCoverImage() {
      return !!this.normalizedImageUrl && !this.imageLoadFailed
    },
    displayDescription() {
      const text = (this.description || '').trim()
      return text || '暂无介绍'
    },
    serviceLine() {
      const label = (this.secondaryLabel || '').trim()
      const hint = (this.serviceHint || '').trim()
      if (!label && !hint) return ''
      if (label && hint) return `${label} / ${hint}`
      return label || hint
    }
  },
  watch: {
    visible(value) {
      if (value) {
        this.resetCardState()
      }
    },
    imageUrl() {
      this.imageLoadFailed = false
    }
  },
  methods: {
    resetCardState() {
      this.imageLoadFailed = false
    },
    onImageError() {
      this.imageLoadFailed = true
    },
    onBentoServiceClick() {
      if (!this.secondaryLabel && !this.serviceHint) return
      this.$emit('secondary-click')
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/theme.scss';

$poi-radius-sheet: 32rpx;

.poi-detail-sheet {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 28;
  padding: 0;
  padding-bottom: env(safe-area-inset-bottom);
  background: transparent;
  transform: translateY(110%);
  opacity: 0;
  pointer-events: none;
  transition: transform 0.32s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.25s ease;
}

.poi-detail-sheet--show {
  transform: translateY(0);
  opacity: 1;
  pointer-events: auto;
}

.poi-detail-sheet__card {
  width: 100%;
  margin: 0;
  overflow: hidden;
  background: $color-bg-block;
  border-radius: $poi-radius-sheet $poi-radius-sheet 0 0;
  border-top: 1rpx solid $color-divider;
  box-shadow: 0 -8rpx 48rpx rgba(29, 29, 31, 0.1);
}

.poi-detail-sheet__hero {
  position: relative;
  width: 100%;
  height: 220rpx;
  overflow: hidden;
  border-radius: $poi-radius-sheet $poi-radius-sheet 0 0;
}

.poi-detail-sheet__hero-img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.poi-detail-sheet__hero-gradient {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 55%;
  background: linear-gradient(
    180deg,
    rgba(0, 0, 0, 0) 0%,
    rgba(0, 0, 0, 0.45) 55%,
    rgba(0, 0, 0, 0.72) 100%
  );
  pointer-events: none;
}

.poi-detail-sheet__body {
  padding: 28rpx $spacing-md-rpx $spacing-md-rpx;
  padding-bottom: calc(#{$spacing-md-rpx} + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: $spacing-md-rpx;
}

.poi-detail-sheet__title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
}

.poi-detail-sheet__title-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.poi-detail-sheet__name {
  font-size: 36rpx;
  font-weight: 900;
  color: $color-text-title;
  line-height: 1.28;
  word-break: break-word;
}

.poi-detail-sheet__zone {
  font-size: 24rpx;
  font-weight: 500;
  color: $color-text-secondary;
  line-height: 1.4;
}

.poi-detail-sheet__distance {
  flex-shrink: 0;
  margin-top: 4rpx;
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: #eef3f7;
  color: #526b7e;
  font-size: 23rpx;
  font-weight: 700;
  line-height: 1.2;
}

.poi-detail-sheet__bento {
  display: flex;
  gap: 14rpx;
  align-items: stretch;
}

.poi-detail-sheet__bento-box {
  flex: 1;
  min-width: 0;
  padding: 18rpx;
  border-radius: 16rpx;
  background: #f7f9fb;
  border: 1rpx solid $color-divider;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.poi-detail-sheet__bento-box--clickable:active {
  background: $tag-primary-bg;
}

.poi-detail-sheet__bento-label {
  font-size: 22rpx;
  font-weight: $font-weight-h2;
  color: $color-text-secondary;
  line-height: 1.3;
}

.poi-detail-sheet__bento-desc {
  font-size: 25rpx;
  font-weight: $font-weight-body;
  color: $color-text-body;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-all;
}

.poi-detail-sheet__bento-service {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6rpx;
  margin-top: 4rpx;
}

.poi-detail-sheet__bento-emoji {
  font-size: $font-size-body-rpx;
  line-height: 1.2;
}

.poi-detail-sheet__bento-service-text {
  font-size: 24rpx;
  color: #526b7e;
  font-weight: $font-weight-h2;
  line-height: 1.4;
}

.poi-detail-sheet__btn-primary {
  width: 100%;
  height: 84rpx;
  line-height: 84rpx;
  margin: 0;
  padding: 0;
  border-radius: 18rpx;
  font-size: 29rpx;
  font-weight: $font-weight-h2;
  border: 1rpx solid rgba(49, 83, 111, 0.18);
  background: #5f7f99;
  color: #ffffff;
}

.poi-detail-sheet__btn-primary::after {
  border: none;
}
</style>
