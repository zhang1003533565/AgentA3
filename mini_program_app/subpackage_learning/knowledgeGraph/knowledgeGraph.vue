<template>
  <view class="page">
    <nav-bar title="Python 知识图谱" :showBack="true" fixed placeholder />

    <view class="overview">
      <text class="overview__eyebrow">PERSONAL KNOWLEDGE MAP</text>
      <text class="overview__title">从掌握证据看下一步</text>
      <text class="overview__desc">节点状态由真实答题与学习路径更新，未产生证据的知识点不会显示虚构分数。</text>
      <view class="overview__metrics">
        <view><text>{{ summary.mastered || 0 }}</text><text>已掌握</text></view>
        <view><text>{{ summary.weak || 0 }}</text><text>需巩固</text></view>
        <view><text>{{ summary.dueForReview || 0 }}</text><text>待复习</text></view>
      </view>
    </view>

    <view v-if="pageState !== 'ready'" class="state">
      <text class="state__title">{{ currentStateCopy.title }}</text>
      <text class="state__desc">{{ currentStateCopy.description }}</text>
      <button v-if="pageState !== 'loading'" @tap="loadGraph">{{ currentStateCopy.action }}</button>
    </view>

    <template v-else>
      <view class="toolbar">
        <view class="search">
          <view class="search__mark"></view>
          <input v-model="keyword" placeholder="搜索知识点或分组" confirm-type="search" />
        </view>
        <scroll-view scroll-x :show-scrollbar="false" class="filters">
          <view class="filters__inner">
            <view v-for="item in statusOptions" :key="item.value" class="filter"
              :class="{ 'filter--active': activeStatus === item.value }" @tap="activeStatus = item.value">
              {{ item.label }}
            </view>
          </view>
        </scroll-view>
      </view>

      <view v-if="levels.length" class="graph">
        <view v-for="(level, levelIndex) in levels" :key="level.level" class="graph-level">
          <view class="graph-level__head">
            <text>{{ level.label }}</text>
            <text>{{ level.nodes.length }} 个知识点</text>
          </view>
          <scroll-view scroll-x :show-scrollbar="false">
            <view class="node-row">
              <view v-for="node in level.nodes" :key="node.id" class="node"
                :class="[`node--${statusMeta(node.status).tone}`, { 'node--path': node.onActivePath }]"
                @tap="selectNode(node)">
                <view class="node__top">
                  <text>{{ node.group || 'Python' }}</text>
                  <text class="node__status">{{ statusMeta(node.status).label }}</text>
                </view>
                <text class="node__title">{{ node.title }}</text>
                <view class="node__score">
                  <view><view :style="{ width: `${score(node.score)}%` }"></view></view>
                  <text>{{ node.attemptCount ? `${score(node.score)}%` : '暂无证据' }}</text>
                </view>
                <text v-if="node.onActivePath" class="node__path">当前路径节点</text>
              </view>
            </view>
          </scroll-view>
          <view v-if="levelIndex < levels.length - 1" class="connector">
            <view></view><text>依赖关系</text><view></view>
          </view>
        </view>
      </view>
      <view v-else class="empty">没有符合当前条件的知识点</view>
    </template>

    <view v-if="selectedNode" class="mask" @tap="selectedNode = null"></view>
    <view v-if="selectedNode" class="detail">
      <view class="detail__handle"></view>
      <view class="detail__head">
        <view>
          <text class="detail__group">{{ selectedNode.group }}</text>
          <text class="detail__title">{{ selectedNode.title }}</text>
        </view>
        <text class="detail__close" @tap="selectedNode = null">×</text>
      </view>
      <text v-if="selectedNode.description" class="detail__desc">{{ selectedNode.description }}</text>
      <view class="detail__evidence">
        <view><text>{{ score(selectedNode.score) }}%</text><text>掌握度</text></view>
        <view><text>{{ selectedNode.attemptCount || 0 }}</text><text>答题次数</text></view>
        <view><text>{{ selectedNode.wrongCount || 0 }}</text><text>错误次数</text></view>
      </view>
      <view v-if="prerequisiteNames.length" class="detail__prerequisite">
        <text>前置知识</text><text>{{ prerequisiteNames.join('、') }}</text>
      </view>
      <text v-if="selectedNode.pathObjective" class="detail__objective">{{ selectedNode.pathObjective }}</text>
      <view class="detail__actions">
        <button class="detail__primary" @tap="generateForNode">生成专项资源</button>
        <button @tap="openExam">开始练习</button>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getPythonKnowledgeGraph } from '@/api/learning.js'
import { buildQueryString, classifyLearningError, learningErrorMessage, responseData, stateCopy } from '@/subpackage_learning/learningView.js'
import { displayGraphScore, filterGraphNodes, graphLevels, graphStatus } from '@/subpackage_learning/knowledgeGraphView.js'

export default {
  components: { NavBar },
  data() {
    return {
      pageState: 'loading',
      errorMessage: '',
      graph: { nodes: [], edges: [], summary: {} },
      keyword: '',
      activeStatus: 'all',
      selectedNode: null,
      statusOptions: [
        { value: 'all', label: '全部' },
        { value: 'weak', label: '需巩固' },
        { value: 'learning', label: '学习中' },
        { value: 'mastered', label: '已掌握' },
        { value: 'available', label: '可学习' },
        { value: 'locked', label: '待解锁' }
      ]
    }
  },
  computed: {
    currentStateCopy() { return stateCopy(this.pageState, this.errorMessage) },
    summary() { return this.graph.summary || {} },
    filteredNodes() { return filterGraphNodes(this.graph.nodes, this.keyword, this.activeStatus) },
    levels() { return graphLevels(this.filteredNodes) },
    prerequisiteNames() {
      const ids = Array.isArray(this.selectedNode?.prerequisiteIds) ? this.selectedNode.prerequisiteIds : []
      const byId = new Map((this.graph.nodes || []).map(node => [node.id, node.title]))
      return ids.map(id => byId.get(id) || id)
    }
  },
  onLoad() { this.loadGraph() },
  onPullDownRefresh() { this.loadGraph(true) },
  methods: {
    statusMeta: graphStatus,
    score: displayGraphScore,
    async loadGraph(fromRefresh = false) {
      this.pageState = 'loading'
      this.errorMessage = ''
      try {
        const data = responseData(await getPythonKnowledgeGraph()) || {}
        this.graph = {
          nodes: Array.isArray(data.nodes) ? data.nodes : [],
          edges: Array.isArray(data.edges) ? data.edges : [],
          summary: data.summary || {}
        }
        this.pageState = this.graph.nodes.length ? 'ready' : 'empty'
      } catch (error) {
        this.pageState = classifyLearningError(error)
        this.errorMessage = learningErrorMessage(error)
      } finally {
        if (fromRefresh) uni.stopPullDownRefresh?.()
      }
    },
    selectNode(node) { this.selectedNode = node },
    generateForNode() {
      const query = buildQueryString({ topic: `围绕“${this.selectedNode.title}”生成专项讲解、练习与代码实验` })
      this.selectedNode = null
      uni.navigateTo({ url: `/subpackage_learning/resourceGenerate/resourceGenerate?${query}` })
    },
    openExam() {
      this.selectedNode = null
      uni.navigateTo({ url: '/subpackage_exam/paperList/paperList' })
    }
  }
}
</script>

<style scoped>
.page{min-height:100vh;padding:24rpx 24rpx 80rpx;background:#f3f5f8;color:#172033;box-sizing:border-box}.overview{padding:34rpx 30rpx;border-radius:28rpx;background:#253449;color:#fff}.overview__eyebrow,.overview__title,.overview__desc{display:block}.overview__eyebrow{font-size:19rpx;letter-spacing:3rpx;color:#a8bacd}.overview__title{margin-top:14rpx;font-size:39rpx;font-weight:780}.overview__desc{margin-top:12rpx;color:#c9d4df;font-size:23rpx;line-height:1.6}.overview__metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:12rpx;margin-top:28rpx}.overview__metrics view{padding:16rpx;border:1px solid rgba(255,255,255,.12);border-radius:16rpx;background:rgba(255,255,255,.05);text-align:center}.overview__metrics text{display:block}.overview__metrics text:first-child{font-size:32rpx;font-weight:760}.overview__metrics text:last-child{margin-top:4rpx;color:#aebccc;font-size:19rpx}.state{margin-top:22rpx;padding:70rpx 30rpx;border-radius:22rpx;background:#fff;text-align:center}.state__title,.state__desc{display:block}.state__title{font-size:30rpx;font-weight:720}.state__desc{margin-top:12rpx;color:#69778a;font-size:23rpx;line-height:1.6}.state button{margin-top:24rpx;background:#48627f;color:#fff}.toolbar{margin-top:20rpx;padding:20rpx;border-radius:22rpx;background:#fff}.search{display:flex;align-items:center;gap:16rpx;height:76rpx;padding:0 22rpx;border-radius:16rpx;background:#f2f5f8}.search__mark{width:22rpx;height:22rpx;border:3rpx solid #718096;border-radius:50%;position:relative}.search__mark:after{content:'';position:absolute;width:10rpx;height:3rpx;right:-8rpx;bottom:-4rpx;background:#718096;transform:rotate(45deg)}.search input{flex:1;font-size:24rpx}.filters{margin-top:16rpx;white-space:nowrap}.filters__inner{display:inline-flex;gap:10rpx}.filter{padding:10rpx 18rpx;border-radius:999rpx;background:#f2f5f8;color:#65758a;font-size:21rpx}.filter--active{background:#dfe8f1;color:#294662;font-weight:650}.graph{margin-top:20rpx}.graph-level{padding:22rpx;border-radius:22rpx;background:#fff}.graph-level+.graph-level{margin-top:14rpx}.graph-level__head{display:flex;justify-content:space-between;color:#718096;font-size:21rpx}.graph-level__head text:first-child{color:#29394e;font-size:25rpx;font-weight:700}.node-row{display:inline-flex;gap:16rpx;padding:18rpx 2rpx 4rpx}.node{width:300rpx;padding:20rpx;border:2rpx solid #d7e0e9;border-radius:19rpx;background:#fff;box-sizing:border-box}.node--path{box-shadow:inset 5rpx 0 #547493}.node--mastered{border-color:#96c5b3;background:#f4faf7}.node--learning{border-color:#9db8d1;background:#f4f8fc}.node--weak{border-color:#d7aaa6;background:#fff7f6}.node--locked{opacity:.58;background:#f2f4f6}.node__top{display:flex;justify-content:space-between;gap:10rpx;color:#708096;font-size:18rpx}.node__status{color:#425d78}.node__title{display:block;margin-top:14rpx;font-size:27rpx;font-weight:720}.node__score{display:flex;align-items:center;gap:12rpx;margin-top:18rpx}.node__score>view{flex:1;height:7rpx;border-radius:999rpx;background:#e1e7ed;overflow:hidden}.node__score>view>view{height:100%;background:#587895}.node__score text{color:#718096;font-size:18rpx}.node__path{display:block;margin-top:12rpx;color:#496984;font-size:18rpx}.connector{display:flex;align-items:center;justify-content:center;gap:12rpx;height:44rpx;color:#91a0af;font-size:17rpx}.connector view{width:34rpx;height:1px;background:#cbd5df}.empty{margin-top:20rpx;padding:60rpx;background:#fff;border-radius:22rpx;color:#8492a3;text-align:center}.mask{position:fixed;inset:0;z-index:100;background:rgba(15,23,42,.38)}.detail{position:fixed;left:0;right:0;bottom:0;z-index:101;padding:16rpx 28rpx calc(32rpx + env(safe-area-inset-bottom));border-radius:30rpx 30rpx 0 0;background:#fff}.detail__handle{width:70rpx;height:7rpx;margin:0 auto 26rpx;border-radius:999rpx;background:#d5dce4}.detail__head{display:flex;justify-content:space-between}.detail__group,.detail__title{display:block}.detail__group{color:#718096;font-size:20rpx}.detail__title{margin-top:6rpx;font-size:35rpx;font-weight:780}.detail__close{padding:4rpx 12rpx;color:#7d8997;font-size:42rpx}.detail__desc{display:block;margin-top:14rpx;color:#5f6f82;font-size:23rpx;line-height:1.6}.detail__evidence{display:grid;grid-template-columns:repeat(3,1fr);gap:12rpx;margin-top:24rpx}.detail__evidence view{padding:18rpx;border-radius:15rpx;background:#f2f5f8;text-align:center}.detail__evidence text{display:block}.detail__evidence text:first-child{font-size:29rpx;font-weight:730}.detail__evidence text:last-child{margin-top:5rpx;color:#718096;font-size:19rpx}.detail__prerequisite,.detail__objective{display:block;margin-top:18rpx;padding:16rpx;border-radius:14rpx;background:#f7f9fb;color:#516174;font-size:21rpx}.detail__prerequisite text{display:block}.detail__prerequisite text:first-child{margin-bottom:5rpx;color:#8492a3}.detail__actions{display:grid;grid-template-columns:1.3fr 1fr;gap:14rpx;margin-top:24rpx}.detail__actions button{margin:0;border:1px solid #ccd6e0;background:#fff;color:#334b64;font-size:24rpx}.detail__actions .detail__primary{border-color:#48627f;background:#48627f;color:#fff}
</style>
