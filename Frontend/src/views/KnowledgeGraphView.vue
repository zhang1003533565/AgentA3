<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getPythonKnowledgeGraph } from '../api/learning'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const graph = ref({ nodes: [], edges: [], summary: {} })
const selected = ref(null)
const keyword = ref('')
const status = ref('all')
const filters = [
  ['all', '全部'], ['weak', '需巩固'], ['learning', '学习中'],
  ['mastered', '已掌握'], ['available', '可学习'], ['locked', '待解锁'],
]
const labels = { weak:'需巩固', learning:'学习中', mastered:'已掌握', available:'可学习', locked:'待解锁' }

const nodes = computed(() => graph.value.nodes.filter((node) => {
  const hitStatus = status.value === 'all' || node.status === status.value
  const query = keyword.value.trim().toLowerCase()
  return hitStatus && (!query || `${node.title} ${node.group}`.toLowerCase().includes(query))
}))
const nodeById = computed(() => new Map(graph.value.nodes.map((node) => [node.id, node])))
const visibleIds = computed(() => new Set(nodes.value.map((node) => node.id)))
const edges = computed(() => graph.value.edges.filter((edge) => visibleIds.value.has(edge.source) && visibleIds.value.has(edge.target)))
const point = (node) => ({ x: 42 + Number(node.level || 0) * 190, y: 92 + Number(node.order || 0) * 150 })
const prerequisites = computed(() => (selected.value?.prerequisiteIds || []).map((id) => nodeById.value.get(id)).filter(Boolean))

async function load() {
  loading.value = true
  try {
    graph.value = await getPythonKnowledgeGraph() || { nodes: [], edges: [], summary: {} }
    selected.value = graph.value.nodes.find((node) => node.status === 'weak') || graph.value.nodes[0] || null
  } catch (cause) { error.value = cause.message } finally { loading.value = false }
}
function generate() {
  router.push({ path: '/learning/resources', query: { topic: selected.value?.title || '' } })
}
onMounted(load)
</script>

<template>
  <div class="feature-page">
    <AppTabBar />
    <main class="graph-page">
      <header class="graph-header">
        <div><h1>Python 知识图谱</h1><p>基于真实答题与学习路径动态更新</p></div>
        <input v-model="keyword" class="feature-input" placeholder="搜索知识点" />
        <div class="feature-chip-row">
          <button v-for="[value,label] in filters" :key="value" class="feature-chip" :class="{ 'feature-chip--active':status===value }" @click="status=value">{{ label }}</button>
        </div>
      </header>
      <div v-if="error" class="feature-error">{{ error }}</div>
      <div v-if="loading" class="feature-empty">正在加载知识图谱…</div>
      <div v-else class="graph-workspace">
        <section class="feature-card graph-canvas">
          <div class="stage-heads"><span v-for="title in ['基础语法','数据结构','函数与模块','异常与调试','算法与性能']" :key="title">{{ title }}</span></div>
          <div class="graph-plane">
            <svg viewBox="0 0 960 540" preserveAspectRatio="none" aria-hidden="true">
              <line v-for="edge in edges" :key="`${edge.source}-${edge.target}`"
                :x1="point(nodeById.get(edge.source)).x+140" :y1="point(nodeById.get(edge.source)).y+28"
                :x2="point(nodeById.get(edge.target)).x" :y2="point(nodeById.get(edge.target)).y+28" />
            </svg>
            <button v-for="node in nodes" :key="node.id" class="graph-node" :class="[`graph-node--${node.status}`,{'selected':selected?.id===node.id}]"
              :style="{left:`${point(node).x}px`,top:`${point(node).y}px`}" @click="selected=node">
              <span></span><strong>{{ node.title }}</strong><small>{{ labels[node.status] || node.status }}</small>
            </button>
            <div v-if="!nodes.length" class="feature-empty graph-no-result">没有符合条件的知识点</div>
          </div>
          <footer class="graph-legend"><span v-for="[value,label] in filters.slice(1)" :key="value"><i :class="`legend-${value}`"></i>{{ label }}</span></footer>
        </section>
        <aside class="feature-card graph-detail">
          <template v-if="selected">
            <div class="feature-section__head"><h2>知识点详情</h2><span :class="`feature-status feature-status--${selected.status}`">{{ labels[selected.status] || selected.status }}</span></div>
            <h3>{{ selected.title }}</h3><p class="graph-description">{{ selected.description || '该知识点暂无补充说明' }}</p>
            <div class="evidence"><div><span>掌握度</span><strong>{{ selected.attemptCount ? `${Math.round(selected.score || 0)}%` : '—' }}</strong></div><div><span>答题次数</span><strong>{{ selected.attemptCount || '—' }}</strong></div><div><span>错误次数</span><strong>{{ selected.attemptCount ? selected.wrongCount : '—' }}</strong></div></div>
            <section class="detail-block"><h4>前置知识</h4><div v-if="prerequisites.length" class="feature-list"><div v-for="item in prerequisites" :key="item.id" class="feature-row"><span>{{ item.title }}</span><span :class="`feature-status feature-status--${item.status}`">{{ labels[item.status] }}</span></div></div><p v-else>无需前置知识</p></section>
            <section class="detail-block"><h4>学习建议</h4><p>{{ selected.pathObjective || (selected.status === 'weak' ? '建议生成专项资源后完成针对性练习。' : '按当前学习路径继续学习。') }}</p></section>
            <div class="detail-actions"><button class="feature-button" @click="generate">生成专项资源</button><button class="feature-button feature-button--primary" @click="router.push('/mine/papers')">开始练习</button></div>
          </template>
        </aside>
      </div>
    </main>
  </div>
</template>

<style scoped>
.graph-page{width:min(1500px,calc(100% - 32px));margin:auto;padding:22px 0 36px}.graph-header{display:grid;grid-template-columns:1fr 240px auto;align-items:center;gap:16px;margin-bottom:18px}.graph-header h1{margin:0;color:#17233a;font-size:27px}.graph-header p{margin:5px 0 0;color:#718096;font-size:13px}.graph-workspace{display:grid;grid-template-columns:minmax(880px,1fr) 340px;gap:16px}.graph-canvas{overflow:auto}.stage-heads{display:grid;grid-template-columns:repeat(5,190px);width:960px;padding:22px 16px 0}.stage-heads span{margin:0 13px;padding:12px;border:1px solid #dce3ea;border-radius:7px;color:#334a62;background:#f7f9fb;text-align:center;font-weight:750}.graph-plane{position:relative;width:960px;height:540px}.graph-plane:before{content:'';position:absolute;inset:18px 0;background:repeating-linear-gradient(90deg,transparent 0,transparent 189px,#e2e8ef 190px)}.graph-plane svg{position:absolute;inset:0;width:960px;height:540px}.graph-plane line{stroke:#8499ad;stroke-width:1.5}.graph-node{position:absolute;width:140px;min-height:57px;padding:8px 10px;border:1px solid #aab8c6;border-radius:8px;color:#344a60;background:#fff;text-align:left}.graph-node>span{display:inline-block;width:9px;height:9px;margin-right:7px;border-radius:50%;background:#758ca2}.graph-node strong{font-size:14px}.graph-node small{display:block;margin:5px 0 0 16px;color:#788899}.graph-node--mastered{border-color:#62a180;background:#f4faf7}.graph-node--mastered>span{background:#4d9270}.graph-node--weak{border-color:#bd7069;background:#fff7f6}.graph-node--weak>span{background:#b85e56}.graph-node--learning,.graph-node--available{border-color:#6f95b8;background:#f5f9fc}.graph-node--learning>span,.graph-node--available>span{background:#527da5}.graph-node--locked{opacity:.58}.graph-node.selected{box-shadow:0 0 0 3px rgba(49,95,140,.15)}.graph-no-result{position:absolute;inset:150px 220px}.graph-legend{display:flex;gap:20px;width:960px;padding:16px 24px;border-top:1px solid #e5eaf0;color:#65758a;font-size:12px}.graph-legend span{display:flex;align-items:center;gap:6px}.graph-legend i{width:9px;height:9px;border-radius:50%;background:#8296aa}.graph-legend .legend-mastered{background:#4d9270}.graph-legend .legend-weak{background:#b85e56}.graph-legend .legend-learning,.graph-legend .legend-available{background:#527da5}.graph-legend .legend-locked{background:#aab4bf}.graph-detail{position:sticky;top:82px;height:fit-content;padding:22px}.graph-detail h3{margin:26px 0 8px;font-size:25px}.graph-description{color:#718096;line-height:1.6}.evidence{display:grid;grid-template-columns:repeat(3,1fr);margin:22px 0;border-block:1px solid #e7ecf1}.evidence div{padding:17px 5px;text-align:center}.evidence div+div{border-left:1px solid #e7ecf1}.evidence span,.evidence strong{display:block}.evidence span{color:#718096;font-size:12px}.evidence strong{margin-top:7px;font-size:20px}.detail-block{margin-top:22px}.detail-block h4{margin:0 0 10px}.detail-block p{color:#65758a;line-height:1.7}.detail-block .feature-row{padding:11px}.detail-actions{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:24px}@media(max-width:1200px){.graph-workspace{grid-template-columns:1fr}.graph-detail{position:static}.graph-header{grid-template-columns:1fr}.graph-header .feature-chip-row{grid-column:1}}
</style>
