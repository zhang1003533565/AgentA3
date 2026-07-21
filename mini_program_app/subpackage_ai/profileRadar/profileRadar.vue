<template>
  <view class="page-wrap">
    <nav-bar title="个人画像" />
    <scroll-view class="page-scroll" scroll-y>
      <view class="profile-page">
        <view v-if="loading" class="state-card">
          <view class="state-loading"></view>
          <text class="state-title">正在读取画像</text>
          <text class="state-desc">正在整理你的学习记录</text>
        </view>

        <template v-else-if="hasProfileData">
          <view class="summary-card">
            <text class="summary-title">我的学习画像</text>
            <view class="profile-tags" v-if="visibleProfileTags.length">
              <text class="profile-tag" v-for="tag in visibleProfileTags" :key="tag">{{ tag }}</text>
            </view>
            <text class="summary-meta">{{ profileMetaText }}</text>
          </view>

          <view class="radar-card">
            <view class="section-head">
              <text class="section-title">能力分布</text>
              <text class="confidence-chip">{{ confidenceLabel }}</text>
            </view>
            <view class="radar-shell">
              <canvas
                class="radar-canvas"
                canvas-id="profileRadarCanvas"
                :width="canvasSize"
                :height="canvasSize"
                :style="{ width: canvasSize + 'px', height: canvasSize + 'px' }"
              ></canvas>
            </view>
            <view class="score-grid">
              <view class="score-item" v-for="item in dimensions" :key="item.key">
                <text class="score-name">{{ item.name }}</text>
                <text class="score-number">{{ item.score }}</text>
              </view>
            </view>
          </view>

          <view class="insight-card">
            <text class="section-title">画像解读</text>
            <view class="insight-block insight-block--first">
              <view class="insight-heading">
                <view class="insight-mark insight-mark--strong"></view>
                <text class="insight-label">优势</text>
              </view>
              <text class="insight-text">{{ strengthInsight }}</text>
              <view class="insight-tags" v-if="advantageDimensions.length">
                <text class="insight-tag insight-tag--strong" v-for="item in advantageDimensions" :key="'advantage-' + item">{{ item }}</text>
              </view>
            </view>
            <view class="insight-block">
              <view class="insight-heading">
                <view class="insight-mark insight-mark--attention"></view>
                <text class="insight-label">需要关注</text>
              </view>
              <text class="insight-text">{{ weaknessInsight }}</text>
              <view class="insight-tags" v-if="gapDimensions.length">
                <text class="insight-tag insight-tag--weak" v-for="item in gapDimensions" :key="'gap-' + item">{{ item }}</text>
              </view>
            </view>
            <view class="suggestion-section" v-if="visibleSuggestions.length">
              <text class="suggestion-title">下一步建议</text>
              <view class="suggestion-item" v-for="(item, index) in visibleSuggestions" :key="item">
                <text class="suggestion-index">{{ index + 1 }}</text>
                <text class="suggestion-text">{{ item }}</text>
              </view>
            </view>
          </view>
        </template>

        <view v-else class="state-card">
          <view class="state-icon">
            <view class="state-icon-line state-icon-line--one"></view>
            <view class="state-icon-line state-icon-line--two"></view>
            <view class="state-icon-line state-icon-line--three"></view>
          </view>
          <text class="state-title">{{ emptyStateTitle }}</text>
          <text class="state-desc">{{ emptyStateDescription }}</text>
          <button class="state-action" @tap="handleEmptyAction">{{ loadFailed ? '重新加载' : '去学习' }}</button>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getProfileRadarSnapshot } from '@/api/ai.js'
import { radarLabelLayout } from './profileRadarLayout.js'

const normalizeProfileDimension = (item) => {
  return {
    key: item.key,
    name: item.name,
    shortName: item.shortName || item.name,
    score: Math.max(0, Math.min(100, Number(item.score || 0)))
  }
}

export default {
  components: { NavBar },
  data() {
    return {
      canvasSize: 320,
      loading: false,
      loadFailed: false,
      confidenceLevel: 'medium',
      dataStatus: 'fallback',
      totalEvidenceCount: 0,
      appliedEvidenceCount: 0,
      strengthSummary: '',
      weaknessSummary: '',
      advantageDimensions: [],
      gapDimensions: [],
      improvementSuggestions: [],
      summaryUpdatedAt: '',
      profileTags: [],
      dimensions: []
    }
  },
  computed: {
    hasProfileData() {
      return this.dataStatus === 'evidence_ready' && this.appliedEvidenceCount > 0 && this.dimensions.length > 0
    },
    visibleProfileTags() {
      return this.profileTags.slice(0, 3)
    },
    visibleSuggestions() {
      return this.improvementSuggestions.slice(0, 3)
    },
    profileMetaText() {
      const evidenceText = `基于 ${this.appliedEvidenceCount} 条有效记录`
      return this.summaryUpdatedAt ? `更新于 ${this.formatDateTime(this.summaryUpdatedAt)} · ${evidenceText}` : evidenceText
    },
    confidenceLabel() {
      const labels = { high: '画像稳定', medium: '画像较稳定', low: '持续更新中' }
      return labels[this.confidenceLevel] || labels.medium
    },
    strengthInsight() {
      if (this.strengthSummary) return this.strengthSummary
      const names = [...this.dimensions].sort((a, b) => b.score - a.score).slice(0, 2).map(item => item.name)
      return names.length ? `${names.join('、')}表现相对突出。` : '优势正在形成。'
    },
    weaknessInsight() {
      if (this.weaknessSummary) return this.weaknessSummary
      const names = [...this.dimensions].sort((a, b) => a.score - b.score).slice(0, 2).map(item => item.name)
      return names.length ? `可以优先关注${names.join('和')}。` : '暂未发现需要重点关注的方向。'
    },
    emptyStateTitle() {
      if (this.loadFailed) return '画像暂时不可用'
      return this.dataStatus === 'evidence_collecting' ? '画像正在形成' : '开始建立你的画像'
    },
    emptyStateDescription() {
      if (this.loadFailed) return '暂时无法读取画像，请稍后重试。'
      if (this.dataStatus === 'evidence_collecting' && this.totalEvidenceCount > 0) {
        return `已经记录 ${this.totalEvidenceCount} 条学习行为，积累更多有效记录后会生成画像。`
      }
      return '完成课程学习、练习和资源浏览后，这里会生成你的个性化学习画像。'
    }
  },
  onLoad() {
    this.loadProfileSnapshot()
  },
  onReady() {
    this.prepareCanvas()
  },
  methods: {
    radarLabelLayout,
    async loadProfileSnapshot() {
      this.loading = true
      this.loadFailed = false
      try {
        const res = await getProfileRadarSnapshot()
        const data = res.data || {}
        if (Array.isArray(data.dimensions) && data.dimensions.length) {
          this.dimensions = data.dimensions.map(normalizeProfileDimension)
        } else {
          this.dimensions = []
        }
        this.profileTags = Array.isArray(data.profileTags) ? data.profileTags : []
        this.confidenceLevel = data.confidenceLevel || this.confidenceLevel
        this.dataStatus = data.dataStatus || 'baseline'
        this.totalEvidenceCount = Number(data.totalEvidenceCount || 0)
        this.appliedEvidenceCount = Number(data.appliedEvidenceCount || 0)
        this.strengthSummary = data.strengthSummary || ''
        this.weaknessSummary = data.weaknessSummary || ''
        this.advantageDimensions = Array.isArray(data.advantageDimensions) ? data.advantageDimensions.slice(0, 3) : []
        this.gapDimensions = Array.isArray(data.gapDimensions) ? data.gapDimensions.slice(0, 3) : []
        this.improvementSuggestions = Array.isArray(data.improvementSuggestions) ? data.improvementSuggestions : []
        this.summaryUpdatedAt = data.summaryUpdatedAt || data.lastUpdatedAt || ''
      } catch (error) {
        console.warn('profile radar snapshot load failed', error)
        this.loadFailed = true
        this.dataStatus = 'fallback'
        this.dimensions = []
        this.profileTags = []
      } finally {
        this.loading = false
        this.$nextTick(() => {
          if (this.hasProfileData) this.drawRadar()
        })
      }
    },
    handleEmptyAction() {
      if (this.loadFailed) {
        this.loadProfileSnapshot()
        return
      }
      uni.navigateTo({ url: '/subpackage_learning/pythonHome/pythonHome' })
    },
    prepareCanvas() {
      const systemInfo = uni.getSystemInfoSync()
      const maxSize = Math.min(systemInfo.windowWidth - 64, 316)
      this.canvasSize = Math.max(260, maxSize)
      this.$nextTick(() => {
        if (this.hasProfileData) this.drawRadar()
      })
    },
    drawRadar() {
      const ctx = uni.createCanvasContext('profileRadarCanvas', this)
      const size = this.canvasSize
      const center = size / 2
      const radius = size * 0.31
      const labelRadius = size * 0.405
      const count = this.dimensions.length
      const startAngle = -Math.PI / 2

      ctx.clearRect(0, 0, size, size)
      ctx.setFillStyle('#ffffff')
      ctx.fillRect(0, 0, size, size)

      for (let level = 5; level >= 1; level -= 1) {
        const r = radius * level / 5
        const points = this.dimensions.map((_, index) => this.point(center, center, r, startAngle + Math.PI * 2 * index / count))
        ctx.beginPath()
        points.forEach((point, index) => {
          if (index === 0) {
            ctx.moveTo(point.x, point.y)
          } else {
            ctx.lineTo(point.x, point.y)
          }
        })
        ctx.closePath()
        ctx.setStrokeStyle(level === 5 ? '#C9D8CF' : '#E5ECE8')
        ctx.setLineWidth(level === 5 ? 1.2 : 1)
        ctx.stroke()
      }

      this.dimensions.forEach((item, index) => {
        const angle = startAngle + Math.PI * 2 * index / count
        const end = this.point(center, center, radius, angle)
        const label = this.point(center, center, labelRadius, angle)
        const labelLayout = this.radarLabelLayout(item.shortName, label, center, size)
        ctx.beginPath()
        ctx.moveTo(center, center)
        ctx.lineTo(end.x, end.y)
        ctx.setStrokeStyle('#E5ECE8')
        ctx.setLineWidth(1)
        ctx.stroke()

        ctx.setFontSize(labelLayout.fontSize)
        ctx.setFillStyle('#40534B')
        ctx.setTextAlign(labelLayout.align)
        ctx.setTextBaseline('middle')
        ctx.fillText(item.shortName, labelLayout.x, labelLayout.y)
      })

      const valuePoints = this.dimensions.map((item, index) => {
        const valueRadius = radius * item.score / 100
        return this.point(center, center, valueRadius, startAngle + Math.PI * 2 * index / count)
      })

      ctx.beginPath()
      valuePoints.forEach((point, index) => {
        if (index === 0) {
          ctx.moveTo(point.x, point.y)
        } else {
          ctx.lineTo(point.x, point.y)
        }
      })
      ctx.closePath()
      ctx.setFillStyle('rgba(38, 166, 154, 0.18)')
      ctx.fill()
      ctx.setStrokeStyle('#16A394')
      ctx.setLineWidth(2)
      ctx.stroke()

      valuePoints.forEach((point) => {
        ctx.beginPath()
        ctx.arc(point.x, point.y, 3.5, 0, Math.PI * 2)
        ctx.setFillStyle('#16A394')
        ctx.fill()
        ctx.setStrokeStyle('#ffffff')
        ctx.setLineWidth(1.5)
        ctx.stroke()
      })

      ctx.beginPath()
      ctx.arc(center, center, 3, 0, Math.PI * 2)
      ctx.setFillStyle('#9EB6AA')
      ctx.fill()

      ctx.draw()
    },
    point(cx, cy, r, angle) {
      return {
        x: cx + Math.cos(angle) * r,
        y: cy + Math.sin(angle) * r
      }
    },
    formatDateTime(value) {
      if (!value) return ''
      const text = String(value).replace('T', ' ')
      return text.length > 16 ? text.slice(0, 16) : text
    }
  }
}
</script>

<style lang="scss" scoped>
.page-wrap {
  min-height: 100vh;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #F5F7F8;
}

.page-scroll {
  flex: 1;
  min-height: 0;
  height: auto;
}

.profile-page {
  padding: 20rpx 24rpx calc(72rpx + env(safe-area-inset-bottom));
}

.summary-card,
.radar-card,
.insight-card,
.state-card {
  background: #FFFFFF;
  border: 1rpx solid #E9EEEB;
  border-radius: 22rpx;
}

.summary-card {
  padding: 30rpx;
  background: #F0F8F6;
  border-color: #E2F0EC;
}

.summary-title {
  display: block;
  font-size: 38rpx;
  font-weight: 800;
  color: #172B25;
  line-height: 1.3;
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 20rpx;
}

.profile-tag {
  padding: 8rpx 15rpx;
  font-size: 22rpx;
  color: #176E64;
  background: #DFF1ED;
  border-radius: 999rpx;
}

.summary-meta {
  display: block;
  margin-top: 20rpx;
  color: #77847E;
  font-size: 22rpx;
  line-height: 1.5;
}

.radar-card,
.insight-card {
  margin-top: 20rpx;
  padding: 28rpx 28rpx 30rpx;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.section-title {
  display: block;
  font-size: 31rpx;
  font-weight: 800;
  color: #172B25;
}

.confidence-chip {
  padding: 7rpx 14rpx;
  border-radius: 999rpx;
  background: #EDF6F3;
  color: #4E756B;
  font-size: 21rpx;
}

.radar-shell {
  display: flex;
  justify-content: center;
  padding: 2rpx 0;
}

.radar-canvas {
  background: #FFFFFF;
}

.score-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rpx;
  overflow: hidden;
  margin-top: 2rpx;
  border-radius: 16rpx;
  background: #E9EEEB;
  border: 1rpx solid #E9EEEB;
}

.score-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 18rpx 20rpx;
  background: #FAFBFA;
}

.score-name {
  overflow: hidden;
  color: #58655F;
  font-size: 23rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-number {
  margin-left: 12rpx;
  color: #168F82;
  font-size: 25rpx;
  font-weight: 800;
}

.insight-block {
  margin-top: 24rpx;
  padding-top: 22rpx;
  border-top: 1rpx solid #EDF0EE;
}

.insight-block--first {
  margin-top: 18rpx;
  padding-top: 0;
  border-top: 0;
}

.insight-heading {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 10rpx;
}

.insight-mark {
  width: 8rpx;
  height: 26rpx;
  border-radius: 999rpx;
}

.insight-mark--strong {
  background: #1AA393;
}

.insight-mark--attention {
  background: #D8A15A;
}

.insight-label {
  font-size: 25rpx;
  font-weight: 800;
  color: #263A33;
}

.insight-text {
  display: block;
  font-size: 24rpx;
  line-height: 1.7;
  color: #5E6B65;
  word-break: break-word;
}

.insight-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 14rpx;
}

.insight-tag {
  padding: 7rpx 14rpx;
  border-radius: 999rpx;
  font-size: 21rpx;
  line-height: 1.35;
}

.insight-tag--strong {
  color: #126E65;
  background: #E8F7F3;
}

.insight-tag--weak {
  color: #8A612F;
  background: #FDF2E4;
}

.suggestion-section {
  margin-top: 26rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #EDF0EE;
}

.suggestion-title {
  display: block;
  margin-bottom: 16rpx;
  color: #263A33;
  font-size: 26rpx;
  font-weight: 800;
}

.suggestion-item {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin-top: 12rpx;
  padding: 16rpx 18rpx;
  border-radius: 16rpx;
  background: #F7F9F8;
}

.suggestion-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34rpx;
  height: 34rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: #DFF1ED;
  color: #16766B;
  font-size: 20rpx;
  font-weight: 800;
}

.suggestion-text {
  flex: 1;
  color: #56645E;
  font-size: 23rpx;
  line-height: 1.55;
}

.state-card {
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  min-height: 560rpx;
  padding: 48rpx;
  text-align: center;
}

.state-icon {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 10rpx;
  width: 112rpx;
  height: 112rpx;
  padding: 25rpx;
  border-radius: 32rpx;
  background: #EAF5F2;
  box-sizing: border-box;
}

.state-icon-line {
  width: 12rpx;
  border-radius: 999rpx;
  background: #33A697;
}

.state-icon-line--one { height: 28rpx; opacity: 0.55; }
.state-icon-line--two { height: 48rpx; }
.state-icon-line--three { height: 38rpx; opacity: 0.75; }

.state-title {
  display: block;
  margin-top: 28rpx;
  color: #20342D;
  font-size: 32rpx;
  font-weight: 800;
}

.state-desc {
  display: block;
  max-width: 520rpx;
  margin-top: 14rpx;
  color: #78847F;
  font-size: 24rpx;
  line-height: 1.65;
}

.state-action {
  min-width: 220rpx;
  margin-top: 32rpx;
  padding: 0 40rpx;
  border: 0;
  border-radius: 16rpx;
  background: #178F82;
  color: #FFFFFF;
  font-size: 25rpx;
  line-height: 78rpx;
}

.state-action::after {
  border: 0;
}

.state-loading {
  width: 54rpx;
  height: 54rpx;
  border: 5rpx solid #DCEDE9;
  border-top-color: #178F82;
  border-radius: 50%;
  animation: state-spin 0.8s linear infinite;
}

@keyframes state-spin {
  to { transform: rotate(360deg); }
}
</style>
