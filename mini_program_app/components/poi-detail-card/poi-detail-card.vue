<template>
  <view
    class="poi-detail-sheet"
    :class="{
      'poi-detail-sheet--show': visible,
      'poi-detail-sheet--half': visible && expandLevel === 'half',
      'poi-detail-sheet--full': visible && expandLevel === 'full'
    }"
    @click.stop
  >
    <view v-if="visible" class="poi-detail-sheet__card">
      <view class="poi-detail-sheet__handle-wrap" @click.stop="$emit('toggle-expand')">
        <view class="poi-detail-sheet__handle"></view>
      </view>

      <view class="poi-detail-sheet__head">
        <view class="poi-detail-sheet__head-main">
          <text class="poi-detail-sheet__name">{{ name || '地点' }}</text>
          <text class="poi-detail-sheet__sub">{{ headerSubText }}</text>
        </view>
        <view class="poi-detail-sheet__head-side">
          <text v-if="category" class="poi-detail-sheet__pill">{{ category }}</text>
          <view class="poi-detail-sheet__nav" @click.stop="$emit('map-click')">
            <view class="poi-detail-sheet__nav-pin"></view>
            <text>去这里</text>
          </view>
        </view>
      </view>

      <view v-if="showFloorPanel" class="poi-detail-sheet__tabs">
        <view
          class="poi-detail-sheet__tab"
          :class="{ active: activePanel === 'info' }"
          @click.stop="$emit('panel-change', 'info')"
        >基础信息</view>
        <view
          class="poi-detail-sheet__tab"
          :class="{ active: activePanel === 'floor' }"
          @click.stop="$emit('panel-change', 'floor')"
        >楼层平面图</view>
      </view>

      <scroll-view
        class="poi-detail-sheet__scroll"
        scroll-y
        :show-scrollbar="false"
      >
        <view v-if="activePanel === 'info'" class="poi-detail-sheet__section">
          <scroll-view
            v-if="displayImages.length"
            class="poi-detail-sheet__gallery"
            scroll-x
            :show-scrollbar="false"
          >
            <view class="poi-detail-sheet__gallery-row">
              <view
                v-for="(url, index) in displayImages"
                :key="`img-${index}`"
                class="poi-detail-sheet__photo"
              >
                <image
                  class="poi-detail-sheet__photo-img"
                  :src="url"
                  mode="aspectFill"
                  @error="onImageError(index)"
                />
              </view>
            </view>
          </scroll-view>

          <view class="poi-detail-sheet__block">
            <text class="poi-detail-sheet__block-label">位置说明</text>
            <text class="poi-detail-sheet__block-text">{{ address || '暂无位置说明' }}</text>
          </view>
          <view class="poi-detail-sheet__block">
            <text class="poi-detail-sheet__block-label">描述</text>
            <text class="poi-detail-sheet__block-text">{{ description || '暂无描述' }}</text>
          </view>
          <view v-if="businessHours" class="poi-detail-sheet__block">
            <text class="poi-detail-sheet__block-label">开放时间</text>
            <text class="poi-detail-sheet__block-text">{{ businessHours }}</text>
          </view>
          <view v-if="avgPriceText" class="poi-detail-sheet__block">
            <text class="poi-detail-sheet__block-label">平均价格</text>
            <text class="poi-detail-sheet__block-text">{{ avgPriceText }}</text>
          </view>
          <text class="poi-detail-sheet__walk">{{ walkText || '步行距离待定位' }}</text>
        </view>

        <view v-else class="poi-detail-sheet__section">
          <scroll-view
            v-if="floors.length"
            class="poi-detail-sheet__floors"
            scroll-x
            :show-scrollbar="false"
          >
            <view class="poi-detail-sheet__floor-row">
              <view
                v-for="floor in floors"
                :key="floor.id"
                class="poi-detail-sheet__floor"
                :class="{ active: floor.id === activeFloorId }"
                @click.stop="$emit('floor-change', floor.id)"
              >{{ floor.label || floor.name }}</view>
            </view>
          </scroll-view>

          <view v-if="floorPlanLoading" class="poi-detail-sheet__empty">
            <text>正在加载平面图…</text>
          </view>
          <view v-else-if="floorPlanError" class="poi-detail-sheet__empty">
            <text>{{ floorPlanError }}</text>
          </view>
          <view v-else-if="!floors.length" class="poi-detail-sheet__empty">
            <text>该点位暂未配置楼层</text>
          </view>
          <view v-else-if="!floorPlanUrl" class="poi-detail-sheet__empty">
            <text>该楼层暂未上传平面图</text>
          </view>
          <view v-else class="poi-detail-sheet__plan-wrap">
            <view class="poi-detail-sheet__plan-stage">
              <image
                class="poi-detail-sheet__plan-image"
                :src="floorPlanUrl"
                mode="aspectFit"
                @error="onPlanError"
              />
              <view
                v-for="point in indoorPoints"
                :key="point.id || point.placeId"
                class="poi-detail-sheet__marker"
                :class="{
                  'poi-detail-sheet__marker--stall': isStallLike(point.placeType),
                  'poi-detail-sheet__marker--active': isSelectedIndoor(point)
                }"
                :style="dotStyle(point)"
                @click.stop="$emit('indoor-select', point)"
              >
                <view class="poi-detail-sheet__marker-hit">
                  <text class="poi-detail-sheet__marker-name">{{ shortIndoorName(point.name) }}</text>
                  <view class="poi-detail-sheet__marker-pin"></view>
                </view>
              </view>
            </view>
            <view v-if="!indoorLoading && !indoorPoints.length" class="poi-detail-sheet__empty poi-detail-sheet__empty--soft">
              <text>该楼层暂无室内点位</text>
            </view>
            <view v-else-if="indoorPoints.length" class="poi-detail-sheet__point-list">
              <view
                v-for="point in indoorPoints"
                :key="`list-${point.id || point.placeId}`"
                class="poi-detail-sheet__point-item"
                :class="{ active: isSelectedIndoor(point) }"
                @click.stop="$emit('indoor-select', point)"
              >
                <view
                  class="poi-detail-sheet__point-dot"
                  :class="{ 'poi-detail-sheet__point-dot--stall': isStallLike(point.placeType) }"
                ></view>
                <view class="poi-detail-sheet__point-body">
                  <text class="poi-detail-sheet__point-name">{{ point.name || '室内点位' }}</text>
                  <text class="poi-detail-sheet__point-meta">{{ point.placeTypeName || point.placeType || '室内点位' }}</text>
                </view>
              </view>
            </view>
          </view>

          <view v-if="selectedIndoor" class="poi-detail-sheet__indoor">
            <view class="poi-detail-sheet__indoor-head">
              <text class="poi-detail-sheet__indoor-name">{{ selectedIndoor.name || '室内点位' }}</text>
              <text class="poi-detail-sheet__indoor-close" @click.stop="$emit('indoor-close')">关闭</text>
            </view>
            <text class="poi-detail-sheet__indoor-meta">{{ indoorMetaText }}</text>
            <image
              v-if="selectedIndoor.imageUrl"
              class="poi-detail-sheet__indoor-image"
              :src="selectedIndoor.imageUrl"
              mode="aspectFill"
            />
            <view v-for="row in indoorDetailRows" :key="row.label" class="poi-detail-sheet__indoor-row">
              <text class="poi-detail-sheet__indoor-label">{{ row.label }}</text>
              <text class="poi-detail-sheet__indoor-value">{{ row.value }}</text>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'PoiDetailCard',
  props: {
    visible: { type: Boolean, default: false },
    expandLevel: { type: String, default: 'half' },
    name: { type: String, default: '' },
    category: { type: String, default: '' },
    walkText: { type: String, default: '' },
    address: { type: String, default: '' },
    description: { type: String, default: '' },
    statusText: { type: String, default: '' },
    businessHours: { type: String, default: '' },
    avgPriceText: { type: String, default: '' },
    floorCount: { type: Number, default: 0 },
    images: { type: Array, default: () => [] },
    imageUrl: { type: String, default: '' },
    activePanel: { type: String, default: 'info' },
    showFloorPanel: { type: Boolean, default: false },
    floors: { type: Array, default: () => [] },
    activeFloorId: { type: [Number, String], default: null },
    floorPlanUrl: { type: String, default: '' },
    floorPlanLoading: { type: Boolean, default: false },
    floorPlanError: { type: String, default: '' },
    indoorPoints: { type: Array, default: () => [] },
    indoorLoading: { type: Boolean, default: false },
    selectedIndoor: { type: Object, default: null }
  },
  emits: [
    'map-click',
    'category-click',
    'toggle-expand',
    'panel-change',
    'floor-change',
    'indoor-select',
    'indoor-close'
  ],
  data() {
    return {
      failedImageIndexes: {},
      planFailed: false
    }
  },
  computed: {
    displayImages() {
      const source = []
      const primary = (this.imageUrl || '').trim()
      if (primary) source.push(primary)
      ;(this.images || []).forEach((item) => {
        const url = typeof item === 'string' ? item : item?.imageUrl
        if (url && !source.includes(url)) source.push(url)
      })
      return source.filter((_, index) => !this.failedImageIndexes[index])
    },
    headerSubText() {
      const parts = [this.walkText, this.address].filter(Boolean)
      return parts.join(' · ') || '校园地点'
    },
    indoorMetaText() {
      const item = this.selectedIndoor
      if (!item) return ''
      return [this.stallStatusText(item.stallStatus), item.placeTypeName || item.placeType, item.floorName]
        .filter(Boolean)
        .join(' · ') || '室内点位'
    },
    indoorDetailRows() {
      const item = this.selectedIndoor
      if (!item) return []
      const rows = []
      if (item.locationDesc) rows.push({ label: '档口位置', value: item.locationDesc })
      if (item.floorName) rows.push({ label: '所在楼层', value: item.floorName })
      if (item.businessHours) rows.push({ label: '营业时间', value: item.businessHours })
      const price = this.formatIndoorPrice(item.avgPrice)
      if (price) rows.push({ label: '人均价格', value: price })
      rows.push({ label: '档口介绍', value: item.description || '暂无说明' })
      return rows
    }
  },
  watch: {
    visible(value) {
      if (value) {
        this.failedImageIndexes = {}
        this.planFailed = false
      }
    },
    imageUrl() {
      this.failedImageIndexes = {}
    },
    images() {
      this.failedImageIndexes = {}
    },
    floorPlanUrl() {
      this.planFailed = false
    }
  },
  methods: {
    onImageError(index) {
      this.failedImageIndexes = { ...this.failedImageIndexes, [index]: true }
    },
    onPlanError() {
      this.planFailed = true
      this.$emit('plan-error')
    },
    isStallLike(placeType) {
      return ['CANTEEN_STALL', 'DINING_AREA'].includes(placeType)
    },
    stallStatusText(status) {
      if (Number(status) === 1) return '营业中'
      if (Number(status) === 2) return '休息中'
      if (Number(status) === 3) return '已关闭'
      return ''
    },
    formatIndoorPrice(value) {
      if (value == null || value === '') return ''
      const amount = Number(value)
      if (!Number.isFinite(amount)) return ''
      return `¥ ${amount.toFixed(2)}`
    },
    isSelectedIndoor(point) {
      return !!(this.selectedIndoor && this.selectedIndoor.placeId === point.placeId)
    },
    shortIndoorName(name) {
      const text = `${name || ''}`.trim()
      if (!text) return '点位'
      return text.length > 6 ? `${text.slice(0, 6)}…` : text
    },
    dotStyle(point) {
      const x = Number(point.xRatio)
      const y = Number(point.yRatio)
      if (!Number.isFinite(x) || !Number.isFinite(y)) return { display: 'none' }
      return {
        left: `${Math.max(0, Math.min(100, x))}%`,
        top: `${Math.max(0, Math.min(100, y))}%`
      }
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/theme.scss';

.poi-detail-sheet {
  position: absolute;
  left: 16rpx;
  right: 16rpx;
  bottom: 16rpx;
  z-index: 28;
  background: transparent;
  transform: translateY(110%);
  opacity: 0;
  pointer-events: none;
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s ease;
}

.poi-detail-sheet--show {
  transform: translateY(0);
  opacity: 1;
  pointer-events: auto;
}

.poi-detail-sheet__card {
  overflow: hidden;
  background: $color-bg-block;
  border-radius: 28rpx;
  border: 1rpx solid #e6edf2;
}

.poi-detail-sheet--half .poi-detail-sheet__scroll {
  max-height: 420rpx;
}

.poi-detail-sheet--full .poi-detail-sheet__scroll {
  max-height: 980rpx;
}

.poi-detail-sheet__handle-wrap {
  padding: 16rpx 0 8rpx;
}

.poi-detail-sheet__handle {
  width: 68rpx;
  height: 8rpx;
  margin: 0 auto;
  border-radius: 999rpx;
  background: #d7dfe6;
}

.poi-detail-sheet__head {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  padding: 0 24rpx 16rpx;
}

.poi-detail-sheet__head-main {
  flex: 1;
  min-width: 0;
}

.poi-detail-sheet__name {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: $color-text-title;
  line-height: 1.3;
}

.poi-detail-sheet__sub {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: $color-text-secondary;
}

.poi-detail-sheet__head-side {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10rpx;
}

.poi-detail-sheet__pill {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: #eef3f7;
  color: #506172;
  font-size: 20rpx;
  font-weight: 700;
}

.poi-detail-sheet__nav {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: #eef3f7;
  color: #314454;
  font-size: 20rpx;
  font-weight: 700;
}

.poi-detail-sheet__nav-pin {
  width: 14rpx;
  height: 14rpx;
  border: 3rpx solid #5c7a99;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
}

.poi-detail-sheet__tabs {
  display: flex;
  gap: 12rpx;
  padding: 0 24rpx 16rpx;
}

.poi-detail-sheet__tab {
  flex: 1;
  height: 64rpx;
  border-radius: 16rpx;
  border: 1rpx solid #dbe3ea;
  background: #f8fafc;
  color: #687889;
  font-size: 24rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.poi-detail-sheet__tab.active {
  background: #e8eef3;
  border-color: #98adbe;
  color: #314454;
}

.poi-detail-sheet__scroll {
  padding: 0 0 calc(12rpx + env(safe-area-inset-bottom));
}

.poi-detail-sheet__section {
  padding: 0 24rpx 12rpx;
}

.poi-detail-sheet__gallery {
  margin-bottom: 16rpx;
}

.poi-detail-sheet__gallery-row {
  display: inline-flex;
  gap: 12rpx;
}

.poi-detail-sheet__photo {
  width: 220rpx;
  height: 140rpx;
  border-radius: 16rpx;
  overflow: hidden;
  background: #f3f5f7;
}

.poi-detail-sheet__photo-img {
  width: 100%;
  height: 100%;
}

.poi-detail-sheet__block {
  padding: 16rpx 0;
  border-top: 1rpx solid #eef1f4;
}

.poi-detail-sheet__block-label {
  display: block;
  font-size: 22rpx;
  font-weight: 700;
  color: #243342;
}

.poi-detail-sheet__block-text {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #708091;
}

.poi-detail-sheet__walk {
  display: block;
  padding: 8rpx 0 4rpx;
  font-size: 22rpx;
  color: #7a8797;
}

.poi-detail-sheet__floors {
  margin-bottom: 16rpx;
}

.poi-detail-sheet__floor-row {
  display: inline-flex;
  gap: 10rpx;
}

.poi-detail-sheet__floor {
  min-width: 88rpx;
  height: 60rpx;
  padding: 0 18rpx;
  border-radius: 16rpx;
  border: 1rpx solid #dbe3ea;
  background: #f8fafc;
  color: #687889;
  font-size: 22rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.poi-detail-sheet__floor.active {
  background: #e8eef3;
  border-color: #98adbe;
  color: #314454;
}

.poi-detail-sheet__plan-wrap {
  padding: 16rpx;
  border-radius: 20rpx;
  background: #f8fafc;
  border: 1rpx solid #e4ebf1;
}

.poi-detail-sheet__plan-stage {
  position: relative;
  width: 100%;
  height: 420rpx;
  border-radius: 16rpx;
  overflow: hidden;
  background: #eef3f6;
  border: 1rpx solid #dee7ee;
}

.poi-detail-sheet__plan-image {
  width: 100%;
  height: 100%;
}

.poi-detail-sheet__marker {
  position: absolute;
  z-index: 2;
  transform: translate(-50%, -100%);
}

.poi-detail-sheet__marker-hit {
  min-width: 72rpx;
  padding-bottom: 4rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.poi-detail-sheet__marker-pin {
  position: relative;
  width: 32rpx;
  height: 32rpx;
  border: 5rpx solid #fff;
  border-radius: 50% 50% 50% 0;
  background: #4d6f8f;
  transform: rotate(-45deg);
  box-sizing: border-box;
}

.poi-detail-sheet__marker-pin::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: #fff;
  transform: translate(-50%, -50%);
}

.poi-detail-sheet__marker--stall .poi-detail-sheet__marker-pin {
  background: #c9864d;
}

.poi-detail-sheet__marker-name {
  margin-bottom: 8rpx;
  max-width: 140rpx;
  padding: 2rpx 8rpx;
  border-radius: 8rpx;
  background: rgba(255, 255, 255, 0.94);
  color: #243342;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 1.3;
  text-align: center;
}

.poi-detail-sheet__marker--active {
  z-index: 3;
}

.poi-detail-sheet__marker--active .poi-detail-sheet__marker-pin {
  width: 34rpx;
  height: 34rpx;
  box-shadow: 0 0 0 8rpx rgba(77, 111, 143, 0.16);
}

.poi-detail-sheet__marker--active .poi-detail-sheet__marker-name {
  background: #314454;
  color: #fff;
}

.poi-detail-sheet__point-list {
  margin-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.poi-detail-sheet__point-item {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 16rpx 14rpx;
  border-radius: 16rpx;
  background: #fff;
  border: 1rpx solid #e4ebf1;
}

.poi-detail-sheet__point-item.active {
  border-color: #98adbe;
  background: #eef3f7;
}

.poi-detail-sheet__point-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #4d6f8f;
  flex-shrink: 0;
}

.poi-detail-sheet__point-dot--stall {
  background: #c9864d;
}

.poi-detail-sheet__point-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.poi-detail-sheet__point-name {
  font-size: 26rpx;
  font-weight: 700;
  color: #243342;
}

.poi-detail-sheet__point-meta {
  font-size: 20rpx;
  color: #7a8898;
}

.poi-detail-sheet__empty {
  padding: 48rpx 16rpx;
  text-align: center;
  color: #7a8898;
  font-size: 24rpx;
}

.poi-detail-sheet__empty--soft {
  padding: 20rpx 0 4rpx;
}

.poi-detail-sheet__indoor {
  margin-top: 16rpx;
  padding: 18rpx;
  border-radius: 18rpx;
  background: #f8fafc;
  border: 1rpx solid #e4ebf1;
}

.poi-detail-sheet__indoor-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12rpx;
}

.poi-detail-sheet__indoor-name {
  font-size: 28rpx;
  font-weight: 800;
  color: #243342;
}

.poi-detail-sheet__indoor-close {
  font-size: 22rpx;
  color: #7a8898;
}

.poi-detail-sheet__indoor-meta {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #708091;
}

.poi-detail-sheet__indoor-image {
  display: block;
  width: 100%;
  height: 180rpx;
  margin-top: 14rpx;
  border-radius: 12rpx;
  background: #eef2f6;
}

.poi-detail-sheet__indoor-row {
  display: flex;
  gap: 16rpx;
  margin-top: 12rpx;
}

.poi-detail-sheet__indoor-label {
  flex-shrink: 0;
  width: 128rpx;
  font-size: 22rpx;
  color: #8a94a0;
}

.poi-detail-sheet__indoor-value {
  flex: 1;
  font-size: 24rpx;
  line-height: 1.5;
  color: #3d4a57;
}
</style>
