<template>
  <view
    class="market-search-entry"
    :class="{
      'market-search-entry--transitioning': transitioning,
      'market-search-entry--active': surfaceActive
    }"
  >
    <view
      class="market-search-entry__pill"
      @click="startTransition"
    >
      <image class="market-search-entry__icon" src="/static/icons/search.svg" mode="aspectFit" />
      <input class="market-search-entry__text" :value="text" disabled />
      <view class="market-search-entry__right">
        <slot name="right"></slot>
      </view>
    </view>

    <root-portal>
      <view class="market-search-entry__mask" :class="{ 'market-search-entry__mask--active': surfaceActive }">
        <view
          class="market-search-entry__surface"
          :style="surfaceStyle"
          @transitionend="onTransitionEnd"
        ></view>
        <view
          class="search-transition-bar"
          :style="transitionBarStyle"
        >
          <image class="market-search-entry__icon" src="/static/icons/search.svg" mode="aspectFit" />
          <input class="market-search-entry__text" :value="text" disabled />
          <view class="market-search-entry__right">
            <slot name="right-transition"></slot>
          </view>
        </view>
      </view>
    </root-portal>
  </view>
</template>

<script>
export default {
  name: 'MarketSearchEntry',
  props: {
    text: { type: String, default: '搜索' },
    source: { type: String, default: '' },
    targetUrl: { type: String, default: '/subpackage_lostfound/marketSearch/marketSearch' },
    barMotion: { type: String, default: 'expand' },
    barTargetOffsetRpx: { type: Number, default: 100 }
  },
  data() {
    return {
      transitioning: false,
      surfaceActive: false,
      navigating: false,
      transitionFallbackTimer: null,
      surfaceRect: {
        left: 0,
        top: 0,
        width: 0,
        height: 0
      },
      windowSize: {
        width: 375,
        height: 667
      }
    }
  },
  computed: {
    surfaceStyle() {
      const rect = this.surfaceRect
      const width = rect.width || 1
      const height = rect.height || 1
      const scaleX = Math.max((this.windowSize.width + 24) / width, 1)
      const scaleY = Math.max((this.windowSize.height + 24) / height, 1)
      const transform = this.surfaceActive
        ? `translate3d(${-rect.left}px, ${-rect.top}px, 0) scale3d(${scaleX}, ${scaleY}, 1)`
        : 'translate3d(0, 0, 0) scale3d(1, 1, 1)'

      return {
        left: `${rect.left}px`,
        top: `${rect.top}px`,
        width: `${width}px`,
        height: `${height}px`,
        transform
      }
    },
    transitionBarStyle() {
      const rect = this.surfaceRect
      const width = rect.width || 1
      const height = rect.height || 1
      const transform = this.barMotion === 'rise'
        ? `translate3d(0, ${this.getBarTargetTop() - (rect.top || 0)}px, 0)`
        : `translate3d(${-rect.left}px, ${-rect.top}px, 0)`
      return {
        left: `${rect.left}px`,
        top: `${rect.top}px`,
        width: `${width}px`,
        height: `${height}px`,
        transform: this.surfaceActive ? transform : 'translate3d(0, 0, 0)'
      }
    }
  },
  methods: {
    getCurrentRoute() {
      const pages = getCurrentPages()
      const current = pages[pages.length - 1]
      if (!current || !current.route) return ''
      const options = current.options || {}
      const query = Object.keys(options)
        .filter((key) => options[key] !== undefined && options[key] !== null && options[key] !== '')
        .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(options[key])}`)
        .join('&')
      return `/${current.route}${query ? `?${query}` : ''}`
    },
    appendQuery(url, params) {
      const query = Object.keys(params)
        .filter((key) => params[key])
        .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
        .join('&')
      if (!query) return url
      return `${url}${url.includes('?') ? '&' : '?'}${query}`
    },
    reset() {
      if (this.transitionFallbackTimer) {
        clearTimeout(this.transitionFallbackTimer)
        this.transitionFallbackTimer = null
      }
      this.transitioning = false
      this.surfaceActive = false
      this.navigating = false
      this.$emit('overlay-change', { active: false })
      this.$emit('transition-change', false)
    },
    rpxToPx(value) {
      return (this.windowSize.width * value) / 750
    },
    getBarTargetTop() {
      let statusBarHeight = 20
      try {
        const sys = uni.getSystemInfoSync()
        statusBarHeight = sys.statusBarHeight || statusBarHeight
      } catch (e) {}
      return statusBarHeight + this.rpxToPx(this.barTargetOffsetRpx)
    },
    startTransition() {
      if (this.transitioning || this.navigating) return
      this.transitioning = true
      this.$emit('transition-change', true)

      try {
        const sys = uni.getSystemInfoSync()
        this.windowSize = {
          width: sys.windowWidth || this.windowSize.width,
          height: sys.windowHeight || this.windowSize.height
        }
      } catch (e) {}

      uni.createSelectorQuery()
        .in(this)
        .select('.market-search-entry__pill')
        .boundingClientRect()
        .exec((res) => {
          const rect = res && res[0]
          if (!rect) {
            this.reset()
            return
          }
          this.surfaceRect = {
            left: rect.left || 0,
            top: rect.top || 0,
            width: rect.width || 1,
            height: rect.height || 1
          }
          this.$emit('overlay-change', {
            active: true,
            rect: { ...this.surfaceRect },
            windowSize: { ...this.windowSize },
            barMotion: this.barMotion,
            barTargetTop: this.getBarTargetTop()
          })
          this.$nextTick(() => {
            this.surfaceActive = true
            this.transitionFallbackTimer = setTimeout(() => {
              this.onTransitionEnd({ propertyName: 'transform' })
            }, 380)
          })
        })
    },
    onTransitionEnd(e) {
      const propertyName = e?.propertyName || e?.detail?.propertyName || ''
      if (propertyName && !String(propertyName).includes('transform')) return
      if (!this.surfaceActive || this.navigating) return
      if (this.transitionFallbackTimer) {
        clearTimeout(this.transitionFallbackTimer)
        this.transitionFallbackTimer = null
      }
      this.navigating = true
      const targetUrl = this.appendQuery(this.targetUrl, {
        source: this.source,
        fromRoute: this.getCurrentRoute()
      })
      console.log('[SEARCH BEFORE NAV]', {
        pages: getCurrentPages().map(p => p.route),
        currentRoute: this.getCurrentRoute(),
        targetUrl
      })
      uni.navigateTo({
        url: targetUrl,
        animationType: 'none',
        animationDuration: 0,
        complete: () => {
          this.reset()
        }
      })
    }
  }
}
</script>

<style scoped>
.market-search-entry {
  min-width: 0;
}

.market-search-entry--transitioning {
  position: relative;
  z-index: 10000;
}

.market-search-entry--active .market-search-entry__pill {
  opacity: 0;
}

.market-search-entry__pill {
  position: relative;
  z-index: 10000;
  width: 100%;
  height: 76rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 28rpx;
  border-radius: 38rpx;
  background: #F5F5F5;
  box-sizing: border-box;
  opacity: 1;
}

.market-search-entry__icon {
  width: 36rpx;
  height: 36rpx;
  flex-shrink: 0;
  opacity: 0.7;
}

.market-search-entry__text {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  font-size: 26rpx;
  line-height: 76rpx;
  color: #888888;
  font-weight: 500;
  padding: 0;
  margin: 0;
  border: none;
  box-sizing: border-box;
  background: transparent;
  opacity: 1;
  -webkit-text-fill-color: #888888;
  pointer-events: none;
}

.market-search-entry__right {
  flex-shrink: 0;
  transition: opacity 160ms ease-out;
}

.market-search-entry--transitioning .market-search-entry__right {
  opacity: 0;
}

.market-search-entry__mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 99999;
  pointer-events: none;
  overflow: hidden;
}

.market-search-entry__mask--active {
  background: rgba(247, 247, 249, 0.01);
}

.market-search-entry__surface {
  position: fixed;
  border-radius: 38rpx;
  background: #F7F7F9;
  overflow: hidden;
  box-shadow: 0 16rpx 48rpx rgba(29, 29, 31, 0.10);
  transform-origin: left top;
  opacity: 0;
  transition: transform 320ms ease-out, opacity 320ms ease-out, border-radius 320ms ease-out;
  will-change: transform, opacity;
}

.market-search-entry__mask--active .market-search-entry__surface {
  opacity: 1;
  border-radius: 0;
}

.search-transition-bar {
  position: fixed;
  z-index: 100000;
  height: 76rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 28rpx;
  border-radius: 38rpx;
  background: #F5F5F5;
  box-sizing: border-box;
  opacity: 0;
  transform-origin: left top;
  transition: transform 320ms ease-out, opacity 160ms ease-out;
  will-change: transform, opacity;
}

.market-search-entry__mask--active .search-transition-bar {
  opacity: 1;
}
</style>
