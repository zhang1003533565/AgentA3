<template>
  <view class="page-wrap">
    <nav-bar title="个人画像" />
    <scroll-view class="page-scroll" scroll-y>
      <view class="profile-page">
        <view class="summary-card">
          <view class="summary-top">
            <view>
              <text class="summary-kicker">LEARNING PROFILE</text>
              <text class="summary-title">个人画像雷达图</text>
              <text class="summary-desc">{{ updateMode }}，Leader 回答会参考画像但不会直接改分。</text>
            </view>
            <view class="score-badge">
              <text class="score-value">{{ overallScore }}</text>
              <text class="score-label">综合分</text>
            </view>
          </view>
          <view class="profile-tags">
            <text class="profile-tag" v-for="tag in profileTags" :key="tag">{{ tag }}</text>
          </view>
        </view>

        <view class="radar-card">
          <view class="section-head">
            <text class="section-title">画像能力分布</text>
            <text class="section-subtitle">{{ confidenceText }}</text>
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
          <view class="legend-row">
            <view class="legend-item">
              <view class="legend-dot legend-dot--fill"></view>
              <text>当前画像</text>
            </view>
            <view class="legend-item">
              <view class="legend-dot legend-dot--grid"></view>
              <text>维度参考线</text>
            </view>
          </view>
        </view>

        <view class="dimension-card">
          <view class="section-head">
            <text class="section-title">画像维度</text>
            <text class="section-subtitle">来源与更新策略</text>
          </view>
          <view class="dimension-list">
            <view class="dimension-item" v-for="item in dimensions" :key="item.key">
              <view class="dimension-main">
                <view class="dimension-title-row">
                  <text class="dimension-name">{{ item.name }}</text>
                  <text class="dimension-score">{{ item.score }}</text>
                </view>
                <view class="progress-track">
                  <view class="progress-fill" :style="{ width: item.score + '%' }"></view>
                </view>
                <text class="dimension-desc">{{ item.desc }}</text>
                <view class="dimension-meta">
                  <text class="dimension-meta-tag" v-for="source in item.sourceSummary" :key="item.key + source">{{ source }}</text>
                </view>
                <text class="dimension-policy">更新策略：{{ item.updatePolicyText }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="note-card">
          <text class="note-title">Leader 使用规则</text>
          <text class="note-text" v-for="rule in leaderUsageRules" :key="rule">{{ rule }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getProfileRadarSnapshot } from '@/api/ai.js'

const DEFAULT_DIMENSIONS = [
  {
    key: 'campus_behavior',
    name: '校园行为',
    shortName: '校园行为',
    score: 76,
    desc: '导航、餐饮、优惠、论坛、活动报名、内容浏览和互动行为。',
    sourceSummary: ['资源点击', '活动报名', '论坛互动'],
    updatePolicy: 'slow'
  },
  {
    key: 'course_background',
    name: '专业课程',
    shortName: '专业课程',
    score: 82,
    desc: '来自初始对话、课程选择和专业背景信息。',
    sourceSummary: ['用户资料', '课表', '课程记录'],
    updatePolicy: 'stable'
  },
  {
    key: 'learning_goal',
    name: '学习目标',
    shortName: '学习目标',
    score: 78,
    desc: '通过对话采集，用于确定资源生成和路径规划方向。',
    sourceSummary: ['AI 对话', '会议任务', '资料生成'],
    updatePolicy: 'medium'
  },
  {
    key: 'resource_preference',
    name: '资源偏好',
    shortName: '资源偏好',
    score: 72,
    desc: '根据图解、视频、代码案例等资源点击与反馈持续构建。',
    sourceSummary: ['资源点击', '收藏下载', '反馈行为'],
    updatePolicy: 'slow'
  },
  {
    key: 'weak_points',
    name: '薄弱知识',
    shortName: '薄弱知识',
    score: 64,
    desc: '来自提问记录、讨论分析和会议后的个人总结。',
    sourceSummary: ['错题记录', 'AI 对话', '会议总结'],
    updatePolicy: 'faster'
  },
  {
    key: 'learning_progress',
    name: '学习进度',
    shortName: '学习进度',
    score: 68,
    desc: '基于会议任务安排、阶段汇报和任务完成状态更新。',
    sourceSummary: ['会议待办', '练习完成', '章节记录'],
    updatePolicy: 'medium'
  },
  {
    key: 'ability_performance',
    name: '能力表现',
    shortName: '能力表现',
    score: 74,
    desc: '结合会议发言强度、任务推进速度和完成质量评估。',
    sourceSummary: ['答题结果', '会议参与', '任务质量'],
    updatePolicy: 'slow'
  }
]

const POLICY_LABELS = {
  stable: '稳定字段，事实变化才更新',
  slow: '慢更新，多次证据后小幅变化',
  medium: '中频更新，按任务或会话沉淀',
  faster: '较快更新，但单次变化受限'
}

const normalizeProfileDimension = (item) => {
  const updatePolicy = item.updatePolicy || 'slow'
  return {
    ...item,
    score: Number(item.score || 0),
    shortName: item.shortName || item.name,
    desc: item.desc || item.description || '',
    sourceSummary: Array.isArray(item.sourceSummary) && item.sourceSummary.length ? item.sourceSummary : ['证据沉淀中'],
    updatePolicy,
    updatePolicyText: POLICY_LABELS[updatePolicy] || updatePolicy
  }
}

export default {
  components: { NavBar },
  data() {
    return {
      canvasSize: 320,
      loading: false,
      confidenceLevel: 'medium',
      updateMode: '证据先沉淀，画像慢更新',
      profileTags: ['学习投入型', '专业提升型', '资源偏好稳定'],
      leaderUsageRules: [
        'Leader 每次回答前读取画像，但不能直接修改画像分数。',
        '高置信度画像用于推荐顺序和解释深度；中低置信度只作为倾向。',
        '当前问题优先于历史画像，冲突时优先相信当前表达。'
      ],
      dimensions: DEFAULT_DIMENSIONS.map(normalizeProfileDimension)
    }
  },
  computed: {
    overallScore() {
      const total = this.dimensions.reduce((sum, item) => sum + item.score, 0)
      return Math.round(total / this.dimensions.length)
    },
    confidenceText() {
      const labels = { high: '高置信画像', medium: '中置信画像', low: '持续观察中' }
      return `${labels[this.confidenceLevel] || '中置信画像'} · 7 个维度`
    }
  },
  onLoad() {
    this.loadProfileSnapshot()
  },
  onReady() {
    this.prepareCanvas()
  },
  methods: {
    async loadProfileSnapshot() {
      this.loading = true
      try {
        const res = await getProfileRadarSnapshot()
        const data = res.data || {}
        if (Array.isArray(data.dimensions) && data.dimensions.length) {
          this.dimensions = data.dimensions.map(item => normalizeProfileDimension({
            key: item.key,
            name: item.name,
            shortName: item.shortName || item.name,
            score: item.score,
            desc: item.description,
            sourceSummary: item.sourceSummary,
            updatePolicy: item.updatePolicy
          }))
        }
        this.profileTags = Array.isArray(data.profileTags) && data.profileTags.length ? data.profileTags : this.profileTags
        this.leaderUsageRules = Array.isArray(data.leaderUsageRules) && data.leaderUsageRules.length ? data.leaderUsageRules : this.leaderUsageRules
        this.confidenceLevel = data.confidenceLevel || this.confidenceLevel
        this.updateMode = data.updateMode || this.updateMode
        this.$nextTick(() => this.drawRadar())
      } catch (error) {
        console.warn('profile radar snapshot load failed', error)
      } finally {
        this.loading = false
      }
    },
    prepareCanvas() {
      const systemInfo = uni.getSystemInfoSync()
      const maxSize = Math.min(systemInfo.windowWidth - 48, 340)
      this.canvasSize = Math.max(280, maxSize)
      this.$nextTick(() => {
        this.drawRadar()
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
        ctx.beginPath()
        ctx.moveTo(center, center)
        ctx.lineTo(end.x, end.y)
        ctx.setStrokeStyle('#E5ECE8')
        ctx.setLineWidth(1)
        ctx.stroke()

        ctx.setFontSize(11)
        ctx.setFillStyle('#536357')
        ctx.setTextAlign(label.x < center - 6 ? 'right' : label.x > center + 6 ? 'left' : 'center')
        ctx.setTextBaseline('middle')
        ctx.fillText(item.shortName, label.x, label.y)
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
    }
  }
}
</script>

<style lang="scss" scoped>
.page-wrap {
  min-height: 100vh;
  background: #F7F7F9;
}

.page-scroll {
  height: calc(100vh - 88rpx);
}

.profile-page {
  padding: 24rpx 24rpx 48rpx;
}

.summary-card,
.radar-card,
.dimension-card,
.note-card {
  background: #FFFFFF;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 32rpx rgba(29, 43, 37, 0.06);
}

.summary-card {
  padding: 28rpx;
  background: linear-gradient(135deg, #EAF8F5 0%, #F7F3E8 100%);
}

.summary-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
}

.summary-kicker {
  display: block;
  margin-bottom: 10rpx;
  font-size: 20rpx;
  letter-spacing: 4rpx;
  color: #6E7F75;
}

.summary-title {
  display: block;
  font-size: 44rpx;
  font-weight: 800;
  color: #1D2F29;
  line-height: 1.25;
}

.summary-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #5D6B64;
}

.score-badge {
  width: 128rpx;
  height: 128rpx;
  border-radius: 32rpx;
  background: #16A394;
  color: #FFFFFF;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 14rpx 32rpx rgba(22, 163, 148, 0.26);
}

.score-value {
  font-size: 44rpx;
  font-weight: 800;
  line-height: 1;
}

.score-label {
  margin-top: 8rpx;
  font-size: 20rpx;
  opacity: 0.92;
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 24rpx;
}

.profile-tag {
  padding: 8rpx 16rpx;
  font-size: 22rpx;
  color: #126E65;
  background: rgba(22, 163, 148, 0.12);
  border-radius: 999rpx;
}

.radar-card,
.dimension-card,
.note-card {
  margin-top: 24rpx;
  padding: 28rpx;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 22rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 800;
  color: #1D2F29;
}

.section-subtitle {
  font-size: 22rpx;
  color: #8E8E93;
}

.radar-shell {
  display: flex;
  justify-content: center;
  padding: 8rpx 0 4rpx;
}

.radar-canvas {
  background: #FFFFFF;
}

.legend-row {
  display: flex;
  justify-content: center;
  gap: 28rpx;
  margin-top: 12rpx;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
  color: #6A756F;
  font-size: 22rpx;
}

.legend-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
}

.legend-dot--fill {
  background: #16A394;
}

.legend-dot--grid {
  background: #DDE8E2;
}

.dimension-list {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
}

.dimension-item {
  padding-bottom: 22rpx;
  border-bottom: 1rpx solid #EFEFF4;
}

.dimension-item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.dimension-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.dimension-name {
  font-size: 28rpx;
  font-weight: 700;
  color: #1D1D1F;
}

.dimension-score {
  font-size: 28rpx;
  font-weight: 800;
  color: #16A394;
}

.progress-track {
  height: 12rpx;
  border-radius: 999rpx;
  background: #EEF3F1;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #16A394 0%, #7CC7B6 100%);
}

.dimension-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #6B756F;
}

.dimension-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 12rpx;
}

.dimension-meta-tag {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: #EEF7F4;
  color: #13776C;
  font-size: 20rpx;
}

.dimension-policy {
  display: block;
  margin-top: 10rpx;
  color: #8A7660;
  font-size: 22rpx;
  line-height: 1.5;
}

.note-card {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.note-title {
  font-size: 28rpx;
  font-weight: 800;
  color: #1D2F29;
}

.note-text {
  font-size: 24rpx;
  line-height: 1.7;
  color: #5F6C65;
}
</style>
