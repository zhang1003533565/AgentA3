<template>
  <view class="nav-shell">
    <view
      class="nav-bar"
      :class="navClassList"
      :style="navStyle"
    >
      <view class="nav-status" :style="{ height: statusBarHeight + 'px' }"></view>
      <view class="nav-inner" :style="{ height: heightRpx + 'rpx', gridTemplateColumns: navGridColumns }">
        <view class="nav-side nav-side--left">
          <slot name="left">
            <view
              v-if="showBack"
              class="market-back-button"
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
                {{ displayTitle }}
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
              v-if="showDefaultCapsule"
              class="nav-capsule"
            >
              <view class="nav-capsule__dots">
                <text class="nav-capsule__dot"></text>
                <text class="nav-capsule__dot"></text>
                <text class="nav-capsule__dot"></text>
              </view>
              <view class="nav-capsule__divider"></view>
              <view class="nav-capsule__circle">
                <text class="nav-capsule__circle-inner"></text>
              </view>
            </view>
            <view
              v-else-if="rightText || rightIcon"
              class="nav-action nav-right"
              :style="rightActionStyle"
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
    background: { type: String, default: '' },
    titleColor: { type: String, default: '#1D1D1F' },
    subtitleColor: { type: String, default: '#8E8E93' },
    iconColor: { type: String, default: '#1D1D1F' },
    autoBack: { type: Boolean, default: true },
    theme: { type: String, default: 'default' },
    showWechatCapsule: { type: Boolean, default: false }
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
        'nav-bar--with-subtitle': !!this.subtitle,
        'nav-bar--wechat': this.theme === 'wechat'
      }
    },
    navStyle() {
      if (this.glass) {
        return {
          background: 'rgba(255,255,255,0.78)'
        }
      }
      if (this.background) {
        return {
          background: this.background
        }
      }
      return {}
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
    displayTitle() {
      if (!this.title) return ''
      const chars = Array.from(this.title)
      if (chars.length <= 10) {
        return this.title
      }
      return chars.slice(0, 10).join('') + '...'
    },
    showDefaultCapsule() {
      return this.showWechatCapsule
    },
    totalHeightPx() {
      return this.statusBarHeight + this.rpxToPx(this.heightRpx)
    },
    navGridColumns() {
      return this.showDefaultCapsule ? '92rpx 1fr 188rpx' : '112rpx 1fr 112rpx'
    },
    centerInset() {
      if (this.showDefaultCapsule) {
        return '156rpx'
      }
      return '112rpx'
    },
    rightActionStyle() {
      if (this.showDefaultCapsule) {
        return {
          minWidth: '72rpx'
        }
      }
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
      if (!this.autoBack) return
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
  padding: 0 28rpx;
  background: linear-gradient(180deg, #dff0ff 0%, #eaf5ff 100%);
  border-bottom-color: rgba(177, 208, 235, 0.55);
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
  font-size: 32rpx;
  line-height: 1.2;
  font-weight: 700;
  color: #2c2f36;
  letter-spacing: 0;
  white-space: nowrap;
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
  width: 64rpx;
  height: 64rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  margin: 0;
  border-radius: 999rpx;
  transition: background-color 0.18s ease, transform 0.12s ease;
}

.nav-action:active {
  background: rgba(15, 23, 42, 0.06);
  transform: scale(0.96);
}

.nav-action--wechat {
  width: 64rpx;
  height: 64rpx;
  padding: 0;
  border-radius: 50%;
}

.nav-back {
  position: relative;
  color: #1D1D1F;
}

.nav-back::before {
  content: '';
  width: 20rpx;
  height: 20rpx;
  border-left: 4rpx solid currentColor;
  border-bottom: 4rpx solid currentColor;
  transform: rotate(45deg);
  border-radius: 2rpx;
  box-sizing: border-box;
}

.nav-back-icon {
  font-size: 0;
  line-height: 0;
  color: transparent;
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

.nav-capsule {
  height: 74rpx;
  min-width: 168rpx;
  padding: 0 10rpx 0 22rpx;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(193, 213, 230, 0.85);
  box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.65);
}

.nav-capsule__dots {
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
}

.nav-capsule__dot {
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: #222831;
}

.nav-capsule__divider {
  width: 1px;
  height: 30rpx;
  margin: 0 12rpx;
  background: rgba(34, 40, 49, 0.12);
}

.nav-capsule__circle {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  border: 4rpx solid #111827;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-capsule__circle-inner {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #111827;
}
</style>

<style lang="scss">
.market-back-button {
  position: relative;
  width: 64rpx;
  height: 64rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  margin: 0;
  border-radius: 999rpx;
  color: #1D1D1F;
  font-size: 0;
  line-height: 0;
  box-sizing: border-box;
  transition: background-color 0.18s ease, transform 0.12s ease;
}

.market-back-button::before {
  content: '';
  width: 20rpx;
  height: 20rpx;
  border-left: 4rpx solid currentColor;
  border-bottom: 4rpx solid currentColor;
  transform: rotate(45deg);
  border-radius: 2rpx;
  box-sizing: border-box;
}

.market-back-button:active {
  background: rgba(15, 23, 42, 0.06);
  transform: scale(0.96);
}
</style>
