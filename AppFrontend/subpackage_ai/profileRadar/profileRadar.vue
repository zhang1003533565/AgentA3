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
              <view class="source-row">
                <text class="source-pill" :class="'source-pill--' + dataStatus">{{ dataStatusText }}</text>
                <text class="source-text">{{ dataSourceText }}</text>
              </view>
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

        <view class="insight-card">
          <view class="section-head section-head--wrap">
            <text class="section-title">智能画像总结</text>
            <text class="section-subtitle">{{ summaryMetaText }}</text>
          </view>
          <text class="insight-summary">{{ aiSummary }}</text>
          <view class="insight-block">
            <text class="insight-label">优势</text>
            <text class="insight-text">{{ strengthSummary }}</text>
            <view class="insight-tags" v-if="advantageDimensions.length">
              <text class="insight-tag insight-tag--strong" v-for="item in advantageDimensions" :key="'advantage-' + item">{{ item }}</text>
            </view>
          </view>
          <view class="insight-block">
            <text class="insight-label">欠缺</text>
            <text class="insight-text">{{ weaknessSummary }}</text>
            <view class="insight-tags" v-if="gapDimensions.length">
              <text class="insight-tag insight-tag--weak" v-for="item in gapDimensions" :key="'gap-' + item">{{ item }}</text>
            </view>
          </view>
          <view class="suggestion-list" v-if="improvementSuggestions.length">
            <text class="suggestion-item" v-for="item in improvementSuggestions" :key="item">{{ item }}</text>
          </view>
          <view class="confidence-list" v-if="confidenceNotes.length">
            <text class="confidence-title">置信依据</text>
            <text class="confidence-item" v-for="item in confidenceNotes" :key="item">{{ item }}</text>
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
      dataStatus: 'fallback',
      dataStatusText: '本地兜底',
      dataSourceText: '接口未返回前先显示默认示例，不能作为正式画像结论。',
      totalEvidenceCount: 0,
      appliedEvidenceCount: 0,
      candidateEvidenceCount: 0,
      updateMode: '证据先沉淀，画像慢更新',
      aiSummary: '当前显示的是默认画像示例，真实画像需要登录后读取后端证据快照。',
      strengthSummary: '优势尚未稳定，需要更多聊天、会议、做题和资源使用证据来确认。',
      weaknessSummary: '欠缺点尚未稳定，Leader 只能把这份画像作为倾向参考。',
      advantageDimensions: [],
      gapDimensions: [],
      improvementSuggestions: ['完成更多对话、会议总结、练习和资源选择后，系统会按证据汇总更新画像。'],
      confidenceNotes: ['当前为本地兜底示例，真实置信度需要后端证据快照返回。'],
      summaryEngine: 'local_fallback',
      summaryUpdatedAt: '',
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
    },
    summaryMetaText() {
      if (this.summaryUpdatedAt) {
        return `证据 ${this.totalEvidenceCount} 条 · ${this.formatDateTime(this.summaryUpdatedAt)}`
      }
      return `证据 ${this.totalEvidenceCount} 条`
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
        this.dataStatus = data.dataStatus || 'evidence_ready'
        this.dataStatusText = data.dataStatusText || '真实画像'
        this.dataSourceText = data.dataSourceText || '已从后端画像接口读取。'
        this.totalEvidenceCount = Number(data.totalEvidenceCount || 0)
        this.appliedEvidenceCount = Number(data.appliedEvidenceCount || 0)
        this.candidateEvidenceCount = Number(data.candidateEvidenceCount || 0)
        this.aiSummary = data.aiSummary || this.aiSummary
        this.strengthSummary = data.strengthSummary || this.strengthSummary
        this.weaknessSummary = data.weaknessSummary || this.weaknessSummary
        this.advantageDimensions = Array.isArray(data.advantageDimensions) ? data.advantageDimensions : this.advantageDimensions
        this.gapDimensions = Array.isArray(data.gapDimensions) ? data.gapDimensions : this.gapDimensions
        this.improvementSuggestions = Array.isArray(data.improvementSuggestions) && data.improvementSuggestions.length
          ? data.improvementSuggestions
          : this.improvementSuggestions
        this.confidenceNotes = Array.isArray(data.confidenceNotes) && data.confidenceNotes.length
          ? data.confidenceNotes
          : this.confidenceNotes
        this.summaryEngine = data.summaryEngine || this.summaryEngine
        this.summaryUpdatedAt = data.summaryUpdatedAt || data.lastUpdatedAt || this.summaryUpdatedAt
        this.$nextTick(() => this.drawRadar())
      } catch (error) {
        console.warn('profile radar snapshot load failed', error)
        this.dataStatus = 'fallback'
        this.dataStatusText = '本地兜底'
        this.dataSourceText = '后端画像接口暂不可用，当前只展示默认示例。'
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
  background: #F7F7F9;
}

.page-scroll {
  flex: 1;
  min-height: 0;
  height: auto;
}

.profile-page {
  padding: 24rpx 24rpx calc(112rpx + env(safe-area-inset-bottom));
}

.summary-card,
.radar-card,
.insight-card,
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

.source-row {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 16rpx;
}

.source-pill {
  flex-shrink: 0;
  padding: 7rpx 14rpx;
  border-radius: 999rpx;
  font-size: 21rpx;
  font-weight: 700;
  color: #126E65;
  background: rgba(22, 163, 148, 0.12);
}

.source-pill--baseline,
.source-pill--fallback {
  color: #8A642C;
  background: rgba(195, 142, 62, 0.16);
}

.source-pill--evidence_collecting {
  color: #2F6A92;
  background: rgba(63, 145, 196, 0.16);
}

.source-text {
  flex: 1;
  min-width: 0;
  font-size: 22rpx;
  line-height: 1.55;
  color: #69766F;
  word-break: break-word;
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
.insight-card,
.dimension-card,
.note-card {
  margin-top: 24rpx;
  padding: 28rpx;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 22rpx;
}

.section-head--wrap {
  align-items: flex-start;
  flex-wrap: wrap;
}

.section-title {
  font-size: 32rpx;
  font-weight: 800;
  color: #1D2F29;
}

.section-subtitle {
  font-size: 22rpx;
  color: #8E8E93;
  line-height: 1.5;
  text-align: right;
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

.insight-summary {
  display: block;
  font-size: 26rpx;
  line-height: 1.75;
  color: #3D4C45;
  word-break: break-word;
}

.insight-block {
  margin-top: 22rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #EFEFF4;
}

.insight-label {
  display: block;
  font-size: 24rpx;
  font-weight: 800;
  color: #1D2F29;
  margin-bottom: 8rpx;
}

.insight-text {
  display: block;
  font-size: 24rpx;
  line-height: 1.7;
  color: #5F6C65;
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
  color: #8A5C2D;
  background: #FFF2DF;
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  margin-top: 22rpx;
}

.suggestion-item {
  display: block;
  padding: 16rpx 18rpx;
  border-radius: 18rpx;
  background: #F7FAF8;
  color: #5F6C65;
  font-size: 23rpx;
  line-height: 1.6;
  word-break: break-word;
}

.confidence-list {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  margin-top: 22rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid #EFEFF4;
}

.confidence-title {
  font-size: 23rpx;
  font-weight: 800;
  color: #3D4C45;
}

.confidence-item {
  display: block;
  color: #7A8580;
  font-size: 22rpx;
  line-height: 1.6;
  word-break: break-word;
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
  white-space: normal;
  word-break: break-word;
  overflow: visible;
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
  white-space: normal;
  word-break: break-word;
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
  white-space: normal;
  word-break: break-word;
}
</style>
