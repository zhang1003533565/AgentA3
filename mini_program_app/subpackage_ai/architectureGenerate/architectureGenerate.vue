<template>
  <view class="page page--architecture">
    <nav-bar title="AI 架构图" :showBack="true" :border="false">
      <template #right>
        <view class="nav-history-action" @tap="openHistory">
        <image class="nav-history-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
      </view>
      </template>
    </nav-bar>

    <scroll-view class="content" scroll-y>
      <view class="input-card">
        <text class="input-label">描述您的架构需求</text>
        <textarea
          class="prompt-input"
          v-model="description"
          placeholder="例如：生成一个校园二手交易系统的整体架构图，包含核心业务流程和数据流向..."
          placeholder-class="prompt-placeholder"
          :maxlength="500"
        />
        <view class="input-footer">
          <view class="voice-import" @tap="importVoice">
            <image class="voice-icon" src="/static/icons/diagram/import-file.svg" mode="aspectFit" />
            <text>导入文档/语音</text>
          </view>
          <text class="char-count">{{ description.length }} / 500</text>
        </view>
      </view>

      <view class="section-title">
        <image class="section-icon" src="/static/icons/diagram/settings-blue.svg" mode="aspectFit" />
        <text>架构生成设置</text>
      </view>

      <view class="field-block">
        <text class="field-label">系统类型</text>
        <view class="chip-row">
          <view
            class="pill-chip"
            :class="{ 'pill-chip--active': selectedSystemType === item.key }"
            v-for="item in systemTypes"
            :key="item.key"
            @tap="selectedSystemType = item.key"
          >
            {{ item.label }}
          </view>
        </view>
      </view>

      <view class="field-block">
        <text class="field-label">架构层级（多选）</text>
        <view class="layer-list">
          <view
            class="layer-row"
            :class="{ 'layer-row--active': selectedLayer === item.key }"
            v-for="item in layerOptions"
            :key="item.key"
            @tap="selectedLayer = item.key"
          >
            <image class="layer-icon" :src="item.icon" mode="aspectFit" />
            <view class="layer-copy">
              <view class="layer-title-line">
                <text class="layer-title">{{ item.label }}</text>
                <text class="ai-tag" v-if="item.tag">{{ item.tag }}</text>
              </view>
              <text class="layer-desc" v-if="item.desc">{{ item.desc }}</text>
            </view>
            <view class="choice-circle" :class="{ 'choice-circle--active': selectedLayer === item.key }"></view>
          </view>
        </view>
      </view>

      <view class="field-block">
        <text class="field-label">展示内容</text>
        <view class="checkbox-grid">
          <view
            class="checkbox-item"
            v-for="item in contentOptions"
            :key="item.key"
            @tap="toggleContent(item.key)"
          >
            <view class="checkbox" :class="{ 'checkbox--active': selectedContents.includes(item.key) }"></view>
            <text>{{ item.label }}</text>
          </view>
        </view>
      </view>

      <view class="field-block">
        <text class="field-label">关系表达</text>
        <view class="relation-list">
          <view
            class="relation-row"
            :class="{ 'relation-row--active': selectedRelation === item.key }"
            v-for="item in relationOptions"
            :key="item.key"
            @tap="selectedRelation = item.key"
          >
            <view class="relation-radio" :class="{ 'relation-radio--active': selectedRelation === item.key }"></view>
            <view>
              <view class="relation-title-line">
                <text class="relation-title">{{ item.label }}</text>
                <text class="ai-tag" v-if="item.tag">{{ item.tag }}</text>
              </view>
              <text class="relation-desc">{{ item.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 最近生成（仿 mindmap 列表） -->
      <view class="recent-section" v-if="recentItems.length">
        <text class="recent-title">最近生成</text>
        <view class="recent-list">
          <view
            class="recent-item"
            v-for="item in recentItems"
            :key="item.id"
            @tap="openRecent(item)"
          >
            <view class="recent-icon-wrap">
              <image class="recent-icon" src="/static/icons/diagram/app-grid.svg" mode="aspectFit" />
            </view>
            <view class="recent-info">
              <text class="recent-name">{{ item.title || '未命名架构' }}</text>
              <text class="recent-meta">{{ item.preview || formatTime(item.createTime) }}</text>
            </view>
            <image class="recent-arrow" src="/static/icons/icon-forward.svg" mode="aspectFit" />
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-bar">
      <view class="generate-btn" @tap="generateArchitecture">
        <image class="generate-icon" src="/static/icons/diagram/spark-blue.svg" mode="aspectFit" />
        <text>{{ isGenerating ? 'AI 生成中...' : 'AI 生成架构图' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  buildArchitecturePayload,
  getArchitectureHistory
} from '@/api/architecture.js'

const description = ref('')
const selectedSystemType = ref('web')
const selectedLayer = ref('auto')
const selectedContents = ref(['frontend', 'backend'])
const selectedRelation = ref('auto')
const isGenerating = ref(false)
const recentItems = ref([])

// UI 选项 key → 后端枚举值 映射
const SYSTEM_TYPE_MAP = {
  web: 'WEB',
  app: 'APP',
  mini: 'MINI_PROGRAM',
  admin: 'ADMIN'
}
const LAYER_MAP = {
  auto: [],
  client: ['ACCESS'],
  application: ['APPLICATION'],
  service: ['SERVICE'],
  data: ['DATA']
}
const CONTENT_MAP = {
  frontend: 'FRONTEND',
  backend: 'BACKEND',
  storage: 'DATABASE',
  thirdParty: 'THIRD_PARTY'
}
const RELATION_MAP = {
  auto: 'AUTO',
  module: 'MODULE',
  data: 'DATA_FLOW'
}

const systemTypes = [
  { key: 'web', label: 'Web系统' },
  { key: 'app', label: 'APP系统' },
  { key: 'mini', label: '小程序' },
  { key: 'admin', label: '管理后台' }
]

const layerOptions = [
  { key: 'auto', label: '自动分析', tag: 'AI', desc: '将根据需求智能判断架构层级', icon: '/static/icons/diagram/ai-pen-blue.svg' },
  { key: 'client', label: '用户层 (Client/User)', icon: '/static/icons/diagram/layer.svg' },
  { key: 'application', label: '应用层 (Application)', icon: '/static/icons/diagram/app-grid.svg' },
  { key: 'service', label: '服务层 (Service/Core)', icon: '/static/icons/diagram/server.svg' },
  { key: 'data', label: '数据层 (Data Storage)', icon: '/static/icons/diagram/database.svg' }
]

const contentOptions = [
  { key: 'frontend', label: '前端模块' },
  { key: 'backend', label: '后端服务' },
  { key: 'storage', label: '数据存储' },
  { key: 'thirdParty', label: '第三方服务' }
]

const relationOptions = [
  { key: 'auto', label: '自动分析', tag: 'AUTO', desc: 'AI 将标注并展示合适的表达方式' },
  { key: 'module', label: '模块关系', desc: '展示组件间的层级与连接关系' },
  { key: 'data', label: '数据流向', desc: '着重展示信息的传递与存储路径' }
]

const openHistory = () => { uni.navigateTo({ url: '/subpackage_ai/diagramHistory/diagramHistory' }) }

// 加载最近生成的架构图列表
const loadRecentItems = async () => {
  try {
    const data = await getArchitectureHistory({ page: 1, size: 10 })
    const records = (data && data.records) || []
    recentItems.value = records.map(item => ({
      id: item.id,
      title: item.title || '未命名架构',
      preview: item.preview || item.description || '',
      createTime: item.createTime || item.createdAt || ''
    }))
  } catch (error) {
    recentItems.value = []
  }
}

// 格式化时间（YYYY-MM-DD HH:mm）
const formatTime = (timeStr = '') => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  if (Number.isNaN(date.getTime())) return timeStr
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// 点击最近生成项 → 进入架构图结果页
const openRecent = (item) => {
  if (!item || item.id == null) return
  uni.navigateTo({
    url: `/subpackage_ai/architecturePreview/architecturePreview?recordId=${encodeURIComponent(item.id)}`
  })
}
const importVoice = () => { uni.showToast({ title: '导入接口预留', icon: 'none' }) }

const toggleContent = (key) => {
  if (selectedContents.value.includes(key)) {
    selectedContents.value = selectedContents.value.filter(item => item !== key)
    return
  }
  selectedContents.value = [...selectedContents.value, key]
}

const generateArchitecture = async () => {
  if (!description.value.trim()) {
    uni.showToast({ title: '请输入架构描述', icon: 'none' })
    return
  }
  if (isGenerating.value) return
  // 组装 payload 并跳转生成动画页（由动画页负责调用 API 与播放生长动画）
  const payload = buildArchitecturePayload({
    description: description.value.trim(),
    systemType: SYSTEM_TYPE_MAP[selectedSystemType.value] || 'WEB',
    architectureStyle: 'AUTO',
    layers: LAYER_MAP[selectedLayer.value] || [],
    displayContent: selectedContents.value.map(key => CONTENT_MAP[key]).filter(Boolean),
    relationType: RELATION_MAP[selectedRelation.value] || 'AUTO'
  })
  uni.setStorageSync('aiArchitecturePendingPayload', payload)
  uni.navigateTo({ url: '/subpackage_ai/architectureGenerating/architectureGenerating' })
}

onMounted(() => {
  loadRecentItems()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #FCFAFC;
  color: #15233A;
}

.nav-history-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 999rpx;
  transition: background-color 0.18s ease, transform 0.12s ease;
}

.nav-history-action:active {
  background: rgba(15, 23, 42, 0.06);
  transform: scale(0.96);
}

.nav-history-icon {
  width: 34rpx;
  height: 34rpx;
}

.content {
  height: calc(100vh - 88rpx);
  padding: 30rpx 30rpx 150rpx;
  box-sizing: border-box;
}

.input-card {
  height: 396rpx;
  padding: 34rpx 34rpx 24rpx;
  border: 1rpx solid #DFE3EA;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.input-label {
  display: block;
  margin-bottom: 14rpx;
  color: #3A4657;
  font-size: 22rpx;
  font-weight: 600;
}

.prompt-input {
  width: 100%;
  height: 270rpx;
  color: #28364C;
  font-size: 24rpx;
  line-height: 1.5;
}

.prompt-placeholder {
  color: #1F2E44;
  font-size: 24rpx;
  line-height: 1.5;
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.voice-import {
  display: flex;
  align-items: center;
  color: #2394F2;
  font-size: 22rpx;
  font-weight: 700;
}

.voice-icon {
  width: 24rpx;
  height: 24rpx;
  margin-right: 8rpx;
}

.char-count {
  color: #778397;
  font-size: 20rpx;
}

.section-title {
  display: flex;
  align-items: center;
  margin: 36rpx 0 26rpx;
  color: #1C2E48;
  font-size: 30rpx;
  font-weight: 800;
}

.section-icon {
  width: 34rpx;
  height: 30rpx;
  margin-right: 12rpx;
}

.field-block {
  margin-bottom: 28rpx;
}

.field-label {
  display: block;
  margin-bottom: 18rpx;
  color: #344155;
  font-size: 22rpx;
  font-weight: 600;
}

.chip-row {
  display: flex;
  gap: 14rpx;
}

.pill-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 50rpx;
  min-width: 118rpx;
  padding: 0 22rpx;
  border: 1rpx solid #DDE3EB;
  border-radius: 28rpx;
  background: #FFFFFF;
  color: #1F2C3F;
  font-size: 22rpx;
  box-sizing: border-box;
}

.pill-chip--active {
  border-color: #38A6F4;
  background: #3AA3F5;
  color: #FFFFFF;
  font-weight: 700;
}

.layer-list,
.relation-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.layer-row {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 26rpx;
  border: 1rpx solid #DEE3EB;
  border-radius: 14rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.layer-row--active {
  height: 108rpx;
  border: 2rpx solid #3AA3F5;
  background: #EAF6FF;
}

.layer-icon {
  width: 34rpx;
  height: 34rpx;
  margin-right: 22rpx;
}

.layer-copy {
  flex: 1;
  min-width: 0;
}

.layer-title-line,
.relation-title-line {
  display: flex;
  align-items: center;
}

.layer-title,
.relation-title {
  color: #1F2C3F;
  font-size: 24rpx;
  font-weight: 800;
}

.layer-desc,
.relation-desc {
  display: block;
  margin-top: 6rpx;
  color: #7C8797;
  font-size: 18rpx;
  line-height: 1.25;
}

.ai-tag {
  margin-left: 12rpx;
  color: #3AA3F5;
  font-size: 16rpx;
  font-weight: 800;
}

.choice-circle {
  width: 26rpx;
  height: 26rpx;
  border: 2rpx solid #B8C2D0;
  border-radius: 50%;
  box-sizing: border-box;
}

.choice-circle--active {
  border: 7rpx solid #3AA3F5;
}

.checkbox-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}

.checkbox-item {
  display: flex;
  align-items: center;
  height: 64rpx;
  padding: 0 20rpx;
  border: 1rpx solid #E1E5EC;
  border-radius: 10rpx;
  background: #FFFFFF;
  color: #1F2C3F;
  font-size: 22rpx;
  box-sizing: border-box;
}

.checkbox {
  position: relative;
  width: 24rpx;
  height: 24rpx;
  margin-right: 12rpx;
  border: 1rpx solid #CAD2DE;
  border-radius: 4rpx;
  box-sizing: border-box;
}

.checkbox--active {
  border-color: #3AA3F5;
  background: #3AA3F5;
}

.checkbox--active::after {
  content: "";
  position: absolute;
  left: 6rpx;
  top: 2rpx;
  width: 7rpx;
  height: 13rpx;
  border-right: 2rpx solid #FFFFFF;
  border-bottom: 2rpx solid #FFFFFF;
  transform: rotate(45deg);
}

.relation-row {
  display: flex;
  align-items: center;
  min-height: 100rpx;
  padding: 18rpx 26rpx;
  border: 1rpx solid #DEE3EB;
  border-radius: 14rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.relation-row--active {
  border: 2rpx solid #3AA3F5;
}

.relation-radio {
  width: 26rpx;
  height: 26rpx;
  margin-right: 20rpx;
  border: 2rpx solid #B8C2D0;
  border-radius: 50%;
  box-sizing: border-box;
}

.relation-radio--active {
  border: 8rpx solid #3AA3F5;
}

/* ===== 最近生成 ===== */
.recent-section {
  margin-top: 36rpx;
  margin-bottom: 40rpx;
}

.recent-title {
  display: block;
  margin: 0 0 18rpx 8rpx;
  color: #545B67;
  font-size: 24rpx;
  font-weight: 500;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.recent-item {
  display: flex;
  align-items: center;
  height: 130rpx;
  padding: 0 30rpx;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-sizing: border-box;
  box-shadow: 0 4rpx 12rpx rgba(35, 43, 58, 0.03);
}

.recent-item:active {
  background: #F4F8FC;
}

.recent-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 68rpx;
  height: 68rpx;
  margin-right: 24rpx;
  border-radius: 12rpx;
  background: #E8F4FE;
}

.recent-icon {
  width: 36rpx;
  height: 36rpx;
}

.recent-info {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.recent-name {
  color: #1E2B3D;
  font-size: 26rpx;
  font-weight: 500;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-meta {
  margin-top: 4rpx;
  color: #2D4664;
  font-size: 20rpx;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-arrow {
  width: 32rpx;
  height: 32rpx;
  opacity: 0.3;
  flex-shrink: 0;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  padding: 22rpx 30rpx 24rpx;
  background: #FCFAFC;
  box-sizing: border-box;
}

.generate-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: 16rpx;
  background: #3AA3F5;
  color: #FFFFFF;
  font-size: 28rpx;
  font-weight: 800;
}

.generate-icon {
  width: 34rpx;
  height: 34rpx;
  margin-right: 12rpx;
}
</style>
