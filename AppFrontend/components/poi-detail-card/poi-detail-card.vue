<template>
  <view class="poi-detail-sheet" :class="{ 'poi-detail-sheet--show': visible }" @click.stop>
    <view v-if="visible" class="poi-detail-sheet__card">
      <!-- 方案 4：有图 — 全景大图 + 底部渐变叠字 -->
      <view v-if="showCoverImage" class="poi-detail-sheet__hero">
        <image
          class="poi-detail-sheet__hero-img"
          :src="imageUrl"
          mode="aspectFill"
          @error="onImageError"
        />
        <view class="poi-detail-sheet__hero-gradient" />
        <view class="poi-detail-sheet__hero-caption">
          <text class="poi-detail-sheet__hero-name">{{ name }}</text>
          <text v-if="distance" class="poi-detail-sheet__hero-distance">{{ distance }}</text>
        </view>
      </view>

      <!-- 无图退化：标题与距离回到下方常规区域 -->
      <view v-else class="poi-detail-sheet__fallback-head">
        <view class="poi-detail-sheet__fallback-row">
          <text class="poi-detail-sheet__fallback-name">{{ name }}</text>
          <text v-if="distance" class="poi-detail-sheet__fallback-distance">{{ distance }}</text>
        </view>
      </view>

      <view class="poi-detail-sheet__body">
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
            <text v-if="zone" class="poi-detail-sheet__bento-zone">{{ zone }}</text>
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

/* —— 全景大图区 —— */
.poi-detail-sheet__hero {
  position: relative;
  width: 100%;
  height: 320rpx;
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

.poi-detail-sheet__hero-caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx $spacing-md-rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  z-index: 1;
}

.poi-detail-sheet__hero-name {
  font-size: $font-size-h1-rpx;
  font-weight: $font-weight-h1;
  color: #ffffff;
  line-height: 1.3;
}

.poi-detail-sheet__hero-distance {
  font-size: $font-size-body-rpx;
  font-weight: $font-weight-body;
  color: rgba(255, 255, 255, 0.88);
  line-height: 1.35;
}

/* —— 无图退化头部 —— */
.poi-detail-sheet__fallback-head {
  padding: $spacing-md-rpx $spacing-md-rpx 8rpx;
  background: $color-bg-block;
}

.poi-detail-sheet__fallback-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $spacing-sm-rpx;
}

.poi-detail-sheet__fallback-name {
  flex: 1;
  min-width: 0;
  font-size: $font-size-h1-rpx;
  font-weight: $font-weight-h1;
  color: $color-text-title;
  line-height: 1.35;
}

.poi-detail-sheet__fallback-distance {
  flex-shrink: 0;
  font-size: $font-size-body-rpx;
  color: $color-text-secondary;
}

/* —— Bento 区 + 主按钮 —— */
.poi-detail-sheet__body {
  padding: $spacing-md-rpx;
  padding-bottom: calc(#{$spacing-md-rpx} + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: $spacing-md-rpx;
}

.poi-detail-sheet__bento {
  display: flex;
  gap: $spacing-sm-rpx;
  align-items: stretch;
}

.poi-detail-sheet__bento-box {
  flex: 1;
  min-width: 0;
  padding: 20rpx;
  border-radius: $radius-base-rpx;
  background: $color-bg-canvas;
  border: 1rpx solid $color-divider;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.poi-detail-sheet__bento-box--clickable:active {
  background: $tag-primary-bg;
}

.poi-detail-sheet__bento-label {
  font-size: $font-size-secondary-rpx;
  font-weight: $font-weight-h2;
  color: $color-text-secondary;
  line-height: 1.3;
}

.poi-detail-sheet__bento-desc {
  font-size: $font-size-body-rpx;
  font-weight: $font-weight-body;
  color: $color-text-body;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-all;
}

.poi-detail-sheet__bento-zone {
  font-size: $font-size-body-rpx;
  color: $color-text-title;
  font-weight: $font-weight-h2;
  line-height: 1.45;
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
  font-size: $font-size-secondary-rpx;
  color: $color-primary;
  font-weight: $font-weight-h2;
  line-height: 1.4;
}

.poi-detail-sheet__btn-primary {
  width: 100%;
  height: 92rpx;
  line-height: 92rpx;
  margin: 0;
  padding: 0;
  border-radius: $radius-base-rpx;
  font-size: 30rpx;
  font-weight: $font-weight-h2;
  border: 2rpx solid $tag-primary-text;
  background: linear-gradient(90deg, #6b8ba4, $color-primary);
  color: $color-bg-header;
}

.poi-detail-sheet__btn-primary::after {
  border: none;
}
</style>
