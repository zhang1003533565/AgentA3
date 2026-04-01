<template>
  <view class="nav-shell">
    <view
      class="nav-bar"
      :class="navClassList"
      :style="navStyle"
    >
      <view class="nav-status" :style="{ height: statusBarHeight + 'px' }"></view>
      <view class="nav-inner" :style="{ height: heightRpx + 'rpx' }">
        <view class="nav-side nav-side--left">
          <slot name="left">
            <view
              v-if="showBack"
              class="nav-action nav-back"
              :style="actionStyle"
              @click="onBack"
            >
              <text class="nav-back-icon" :style="{ color: iconColorValue }">{{ backIcon }}</text>
            </view>
          </slot>
        </view>

        <view class="nav-center" :style="{ paddingLeft: centerInset, paddingRight: centerInset }">
          <slot name="center">
            <view class="nav-title-wrap">
              <text
                v-if="title"
                class="nav-title"
                :style="{ color: titleColorValue, textAlign: titleAlign }"
              >
                {{ title }}
              </text>
              <text
                v-if="subtitle"
                class="nav-subtitle"
                :style="{ color: subtitleColorValue, textAlign: titleAlign }"
              >
                {{ subtitle }}
              </text>
            </view>
          </slot>
        </view>

        <view class="nav-side nav-side--right">
          <slot name="right">
            <view
              v-if="rightText || rightIcon"
              class="nav-action nav-right"
              :style="actionStyle"
              @click="$emit('right-click')"
            >
              <text v-if="rightIcon" class="nav-right-icon" :style="{ color: iconColorValue }">{{ rightIcon }}</text>
              <text v-if="rightText" class="nav-right-text" :style="{ color: titleColorValue }">{{ rightText }}</text>
            </view>
          </slot>
        </view>
      </view>
    </view>

    <view
      v-if="placeholder"
      class="nav-placeholder"
      :style="{ height: totalHeightPx + 'px' }"
    ></view>
  </view>
</template>

<script>
export default {
  name: 'NavBar',
  props: {
    title: { type: String, default: '' },
    subtitle: { type: String, default: '' },
    showBack: { type: Boolean, default: true },
    glass: { type: Boolean, default: false },
    backIcon: { type: String, default: '‹' },
    rightText: { type: String, default: '' },
    rightIcon: { type: String, default: '' },
    fixed: { type: Boolean, default: false },
    placeholder: { type: Boolean, default: false },
    border: { type: Boolean, default: true },
    shadow: { type: Boolean, default: false },
    titleAlign: { type: String, default: 'center' },
    heightRpx: { type: Number, default: 88 },
    background: { type: String, default: '#FFFFFF' },
    titleColor: { type: String, default: '#1D1D1F' },
    subtitleColor: { type: String, default: '#8E8E93' },
    iconColor: { type: String, default: '#1D1D1F' }
  },
  data() {
    return {
      statusBarHeight: 20,
      windowWidth: 375
    }
  },
  computed: {
    navClassList() {
      return {
        'nav-bar--glass': this.glass,
        'nav-bar--fixed': this.fixed,
        'nav-bar--borderless': !this.border,
        'nav-bar--shadow': this.shadow,
        'nav-bar--with-subtitle': !!this.subtitle
      }
    },
    navStyle() {
      return {
        background: this.glass ? 'rgba(255,255,255,0.78)' : this.background
      }
    },
    titleColorValue() {
      return this.titleColor
    },
    subtitleColorValue() {
      return this.subtitleColor
    },
    iconColorValue() {
      return this.iconColor
    },
    totalHeightPx() {
      return this.statusBarHeight + this.rpxToPx(this.heightRpx)
    },
    centerInset() {
      return '112rpx'
    },
    actionStyle() {
      return {
        minWidth: '88rpx'
      }
    }
  },
  mounted() {
    try {
      const sys = uni.getSystemInfoSync()
      this.statusBarHeight = sys.statusBarHeight || 20
      this.windowWidth = sys.windowWidth || 375
    } catch (e) {}
  },
  methods: {
    rpxToPx(value) {
      return (this.windowWidth * value) / 750
    },
    onBack() {
      this.$emit('back')
      const pages = getCurrentPages()
      if (pages.length <= 1) {
        uni.reLaunch({ url: '/pages/index/index' })
        return
      }
      uni.navigateBack({ delta: 1 })
    }
  }
}
</script>

<style lang="scss" scoped>
.nav-shell {
  position: relative;
  z-index: 1000;
}

.nav-bar {
  position: relative;
  padding: 0 24rpx;
  background: #ffffff;
  border-bottom: 1px solid #e8ebef;
}

.nav-bar--fixed {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
}

.nav-bar--glass {
  backdrop-filter: blur(24rpx) saturate(140%);
  -webkit-backdrop-filter: blur(24rpx) saturate(140%);
}

.nav-bar--borderless {
  border-bottom-color: transparent;
}

.nav-bar--shadow {
  box-shadow: 0 12rpx 32rpx rgba(15, 23, 42, 0.06);
}

.nav-inner {
  display: grid;
  grid-template-columns: 112rpx 1fr 112rpx;
  align-items: center;
}

.nav-side {
  display: flex;
  align-items: center;
  min-width: 0;
}

.nav-side--left {
  justify-content: flex-start;
}

.nav-side--right {
  justify-content: flex-end;
}

.nav-center {
  min-width: 0;
}

.nav-title-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
}

.nav-title {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 36rpx;
  line-height: 1.2;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.nav-subtitle {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 6rpx;
  font-size: 22rpx;
  line-height: 1.2;
  font-weight: 500;
}

.nav-action {
  height: 72rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 12rpx;
  border-radius: 999rpx;
  transition: background-color 0.18s ease, transform 0.12s ease;
}

.nav-action:active {
  background: rgba(15, 23, 42, 0.06);
  transform: scale(0.96);
}

.nav-back-icon {
  font-size: 50rpx;
  line-height: 1;
  font-weight: 400;
}

.nav-right-icon {
  font-size: 32rpx;
  line-height: 1;
}

.nav-right-text {
  font-size: 26rpx;
  line-height: 1;
  font-weight: 600;
}

.nav-placeholder {
  width: 100%;
}
</style>
