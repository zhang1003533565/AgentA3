<script setup>
import { computed, ref } from 'vue'
import MindMapBranch from './MindMapBranch.vue'

const props = defineProps({ type: { type: String, required: true }, result: { type: Object, required: true } })
const emit = defineEmits(['export', 'fullscreen'])
const scale = ref(1)
const selectedNode = ref(null)
const collapseAllMindNodes = ref(false)
const flowSvg = ref(null)
const lanePalette = [
  { fill: '#f4f8fc', stroke: '#c9dbea', label: '#466b89' },
  { fill: '#f5faf7', stroke: '#c9e4d4', label: '#42775a' },
  { fill: '#faf8f4', stroke: '#eadbbc', label: '#82663a' },
  { fill: '#f8f6fc', stroke: '#dcd2ef', label: '#67568e' },
]

const title = computed(() => props.result.title || 'AI 图谱')
const summary = computed(() => props.result.fileSummary || props.result.subtitle || props.result.description || '')
const flowNodes = computed(() => props.result.nodes?.length ? props.result.nodes : [])
const lanes = computed(() => props.result.lanes || [])
const isHorizontal = computed(() => String(props.result.resolvedLayoutDirection || props.result.layoutDirection || '').toUpperCase().includes('HORIZONTAL'))
const hasLanes = computed(() => lanes.value.length && !String(props.result.resolvedSwimlaneMode || '').includes('NONE'))
const flowPositions = computed(() => flowNodes.value.map((node, index) => {
  const laneIndex = flowLaneIndex(node, index)
  if (isHorizontal.value) {
    const column = hasLanes.value ? Math.floor(index / lanes.value.length) : index
    return { ...node, x: 120 + column * 190, y: hasLanes.value ? 74 + laneIndex * 78 : 190 }
  }
  return { ...node, x: hasLanes.value ? 101 + laneIndex * 150 : 330, y: 92 + index * 116 }
}))
const flowEdges = computed(() => {
  const positions = new Map(flowPositions.value.map((node) => [String(node.id), node]))
  const generated = props.result.edges?.map((edge) => ({ ...edge, from: positions.get(String(edge.source)), to: positions.get(String(edge.target)) })).filter((edge) => edge.from && edge.to) || []
  return generated.length ? generated : flowPositions.value.slice(1).map((node, index) => ({ from: flowPositions.value[index], to: node, label: '' }))
})
const flowViewBox = computed(() => {
  if (isHorizontal.value) {
    const columns = hasLanes.value ? Math.ceil(flowNodes.value.length / lanes.value.length) : flowNodes.value.length
    return `0 0 ${Math.max(760, 220 + columns * 190)} ${Math.max(430, 108 + lanes.value.length * 78)}`
  }
  return `0 0 ${Math.max(720, 70 + lanes.value.length * 150)} ${Math.max(760, 150 + flowNodes.value.length * 116)}`
})
const architectureLayers = computed(() => props.result.layers || [])
const mindTree = computed(() => toMindTree(props.result.nodes || [], title.value))
const mindBranches = computed(() => mindTree.value.children || [])

function zoom(delta) { scale.value = Math.min(1.5, Math.max(0.65, Number((scale.value + delta).toFixed(2)))) }
function reset() { scale.value = 1; selectedNode.value = null }
function toggleAllMindNodes() { collapseAllMindNodes.value = !collapseAllMindNodes.value }
function flowShape(node) {
  const type = String(node.type || '').toLowerCase()
  return type.includes('decision') || type.includes('condition') ? 'decision' : (type.includes('start') || type.includes('end') ? 'terminal' : 'process')
}
function flowLaneFor(node) {
  const candidates = [node.laneId, node.lane, node.role, node.department].filter(Boolean).map(String)
  return lanes.value.find((lane) => candidates.includes(String(lane.id)) || candidates.includes(String(lane.label)) || lane.nodes?.map(String).includes(String(node.id)))
}
function flowLaneIndex(node, fallbackIndex) {
  if (!hasLanes.value) return 0
  const lane = flowLaneFor(node)
  return lane ? Math.max(0, lanes.value.indexOf(lane)) : fallbackIndex % lanes.value.length
}
function flowLaneStyle(index) { return lanePalette[index % lanePalette.length] }
function flowPath(edge) {
  if (isHorizontal.value) {
    const startX = edge.from.x + 74
    const endX = edge.to.x - 74
    const middleX = (startX + endX) / 2
    return `M ${startX} ${edge.from.y} H ${middleX} V ${edge.to.y} H ${endX}`
  }
  const startY = edge.from.y + 28
  const endY = edge.to.y - 28
  const middleY = (startY + endY) / 2
  return `M ${edge.from.x} ${startY} V ${middleY} H ${edge.to.x} V ${endY}`
}
function flowEdgeLabelPosition(edge) {
  return isHorizontal.value
    ? { x: (edge.from.x + edge.to.x) / 2, y: (edge.from.y + edge.to.y) / 2 - 8 }
    : { x: (edge.from.x + edge.to.x) / 2 + 9, y: (edge.from.y + edge.to.y) / 2 - 5 }
}
function flowLabelLines(node) {
  const label = nodeLabel(node)
  const limit = flowShape(node) === 'decision' ? 7 : 11
  const lines = []
  for (let index = 0; index < label.length; index += limit) lines.push(label.slice(index, index + limit))
  return lines.slice(0, 2)
}
function architectureNodeLabel(node) { return node.name || node.label || node.title || '服务模块' }
function nodeLabel(node) { return node.label || node.name || node.title || node.content || '主题' }
function selectNode(node, kind) { selectedNode.value = { ...node, label: nodeLabel(node), kind } }
function selectMindNode(node, depth) { selectNode(node, depth === 1 ? '一级主题' : `${depth} 级主题`) }
function toMindTree(nodes, fallbackTitle) {
  if (!nodes.length) return { label: fallbackTitle, children: [] }
  const first = nodes[0]
  if (Array.isArray(first.children) && first.children.length) return { label: nodeLabel(first), children: first.children }
  const items = nodes.map((node, index) => ({ ...node, id: String(node.id || index), children: [] }))
  const map = new Map(items.map((node) => [node.id, node]))
  const root = items.find((node) => !node.parentId && !node.parent && !node.pid) || items[0]
  items.forEach((node) => {
    const parentId = node.parentId || node.parent || node.pid
    if (parentId && map.has(String(parentId))) map.get(String(parentId)).children.push(node)
  })
  return { label: nodeLabel(root), children: root.children?.length ? root.children : items.filter((node) => node !== root && !node.parentId) }
}

function escapeXml(value) {
  return String(value || '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' }[char]))
}
function downloadSvg(svg, filename) {
  const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' })
  const href = URL.createObjectURL(blob); const link = document.createElement('a')
  link.href = href; link.download = filename; link.click(); URL.revokeObjectURL(href)
}
function imageFilename(fallback) { return `${String(title.value || fallback).replace(/[\\/:*?"<>|]/g, '_').slice(0, 48)}.svg` }
function exportImage() {
  if (props.type === 'flowchart' && flowSvg.value) {
    const clone = flowSvg.value.cloneNode(true)
    clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
    clone.insertAdjacentHTML('afterbegin', '<style>.flow-lane-label{font:700 12px sans-serif}.flow-node{stroke-width:2}.flow-node--process{fill:#f7fbff;stroke:#5181ad}.flow-node--terminal{fill:#edf8f3;stroke:#5eaa7c}.flow-node--decision{fill:#fff8e8;stroke:#e0a841}.flow-node-label{fill:#28425e;font:700 13px sans-serif}.flow-edge-label{fill:#8b671f;font:700 11px sans-serif;paint-order:stroke;stroke:#fff;stroke-width:4px}</style><rect width="100%" height="100%" fill="#ffffff"/>')
    downloadSvg(new XMLSerializer().serializeToString(clone), imageFilename('流程图'))
    return
  }
  const width = 1200
  if (props.type === 'architecture') {
    const rowHeight = 150; const height = 100 + Math.max(1, architectureLayers.value.length) * rowHeight
    const rows = architectureLayers.value.map((layer, index) => {
      const nodes = (layer.nodes?.length ? layer.nodes : (layer.groups || []).flatMap((group) => group.nodes || [])).slice(0, 7)
      const cards = nodes.map((node, nodeIndex) => `<rect x="${230 + nodeIndex * 130}" y="${72 + index * rowHeight}" width="116" height="58" rx="8" fill="#f4faf8" stroke="#9fc8ba"/><text x="${288 + nodeIndex * 130}" y="${105 + index * rowHeight}" text-anchor="middle" fill="#2f5365" font-size="13" font-weight="700">${escapeXml(architectureNodeLabel(node).slice(0, 12))}</text>`).join('')
      return `<rect x="40" y="${62 + index * rowHeight}" width="150" height="78" rx="9" fill="#eaf3fa" stroke="#9dbbd3"/><text x="115" y="${108 + index * rowHeight}" text-anchor="middle" fill="#355d7f" font-size="15" font-weight="700">${escapeXml(layer.name)}</text>${cards}`
    }).join('')
    downloadSvg(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><rect width="100%" height="100%" fill="#ffffff"/><text x="40" y="36" fill="#253d58" font-size="20" font-weight="700">${escapeXml(title.value)}</text>${rows}</svg>`, imageFilename('架构图'))
    return
  }
  const height = Math.max(560, 150 + mindBranches.value.length * 96)
  const branches = mindBranches.value.map((branch, index) => {
    const y = 100 + index * 96
    const children = (branch.children || []).slice(0, 5).map((child, childIndex) => `<path d="M 710 ${y + 28} H 770 V ${y - 18 + childIndex * 24} H 800" fill="none" stroke="#a9b8c8"/><rect x="800" y="${y - 29 + childIndex * 24}" width="210" height="23" rx="11" fill="#f8fafc" stroke="#d3dee8"/><text x="812" y="${y - 13 + childIndex * 24}" fill="#526b82" font-size="11">${escapeXml(nodeLabel(child).slice(0, 24))}</text>`).join('')
    return `<path d="M 410 ${height / 2} C 500 ${height / 2}, 500 ${y + 28}, 560 ${y + 28}" fill="none" stroke="#8f79d7" stroke-width="2"/><rect x="560" y="${y}" width="150" height="56" rx="12" fill="#f4f1ff" stroke="#9c87dc"/><text x="635" y="${y + 33}" text-anchor="middle" fill="#4d4083" font-size="13" font-weight="700">${escapeXml(nodeLabel(branch).slice(0, 16))}</text>${children}`
  }).join('')
  downloadSvg(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><rect width="100%" height="100%" fill="#ffffff"/><rect x="190" y="${height / 2 - 34}" width="220" height="68" rx="34" fill="#f1edff" stroke="#7c5ce0" stroke-width="2"/><text x="300" y="${height / 2 + 6}" text-anchor="middle" fill="#4c348f" font-size="17" font-weight="700">${escapeXml(mindTree.value.label.slice(0, 20))}</text>${branches}</svg>`, imageFilename('思维导图'))
}
</script>

<template>
  <section class="diagram-workspace" :class="`diagram-workspace--${type}`">
    <header class="diagram-workspace__head">
      <div><strong>{{ title }}</strong><span>{{ summary || '已生成 · 可缩放、查看节点详情' }}</span></div>
      <div class="diagram-workspace__actions">
        <button type="button" title="缩小" @click="zoom(-0.1)">−</button><button type="button" title="重置缩放" @click="reset">{{ Math.round(scale * 100) }}%</button><button type="button" title="放大" @click="zoom(0.1)">＋</button><button v-if="type==='mind_map'" type="button" class="diagram-workspace__secondary" @click="toggleAllMindNodes">{{ collapseAllMindNodes ? '展开全部' : '收起全部' }}</button><button type="button" class="diagram-workspace__secondary" @click="emit('fullscreen')">全屏预览</button><button type="button" class="diagram-workspace__secondary" @click="exportImage">导出图片</button><button type="button" class="diagram-workspace__export" @click="emit('export')">导出数据</button>
      </div>
    </header>
    <div class="diagram-workspace__viewport"><div class="diagram-workspace__stage" :style="{ transform: `scale(${scale})` }">
      <svg v-if="type === 'flowchart'" ref="flowSvg" class="flow-canvas" :viewBox="flowViewBox" role="img" aria-label="生成的流程图">
        <defs><marker id="flow-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="#50708e" /></marker></defs>
        <g v-if="hasLanes" class="flow-lanes"><g v-for="(lane, index) in lanes" :key="lane.id || lane.label" :transform="isHorizontal ? `translate(0, ${42 + index * 78})` : `translate(${35 + index * 150}, 0)`">
          <rect v-if="isHorizontal" x="35" y="0" :width="Math.max(680, 160 + Math.ceil(flowNodes.length / lanes.length) * 190)" height="64" rx="10" :fill="flowLaneStyle(index).fill" :stroke="flowLaneStyle(index).stroke" stroke-width="1.5" />
          <rect v-else x="0" y="38" width="132" :height="Math.max(700, 110 + flowNodes.length * 116)" rx="10" :fill="flowLaneStyle(index).fill" :stroke="flowLaneStyle(index).stroke" stroke-width="1.5" />
          <text v-if="isHorizontal" x="48" y="24" :fill="flowLaneStyle(index).label" class="flow-lane-label">{{ lane.label || `泳道 ${index + 1}` }}</text><text v-else x="14" y="62" :fill="flowLaneStyle(index).label" class="flow-lane-label">{{ lane.label || `泳道 ${index + 1}` }}</text>
        </g></g>
        <g v-for="edge in flowEdges" :key="`${edge.from.id}-${edge.to.id}-${edge.label}`"><path :d="flowPath(edge)" fill="none" stroke="#50708e" stroke-width="2" marker-end="url(#flow-arrow)" /><text v-if="edge.label" v-bind="flowEdgeLabelPosition(edge)" text-anchor="middle" class="flow-edge-label">{{ edge.label }}</text></g>
        <g v-for="node in flowPositions" :key="node.id" :transform="`translate(${node.x}, ${node.y})`" class="flow-node-group" @click="selectNode({ ...node, laneLabel: flowLaneFor(node)?.label }, '流程节点')"><rect v-if="flowShape(node) === 'process'" x="-74" y="-27" width="148" height="54" rx="10" class="flow-node flow-node--process" /><rect v-else-if="flowShape(node) === 'terminal'" x="-64" y="-23" width="128" height="46" rx="23" class="flow-node flow-node--terminal" /><rect v-else x="-33" y="-33" width="66" height="66" transform="rotate(45)" class="flow-node flow-node--decision" /><text text-anchor="middle" class="flow-node-label"><tspan v-for="(line, lineIndex) in flowLabelLines(node)" :key="lineIndex" x="0" :dy="lineIndex === 0 ? -(flowLabelLines(node).length - 1) * 8 : 16">{{ line }}</tspan></text></g>
      </svg>
      <div v-else-if="type === 'architecture'" class="architecture-canvas"><div class="architecture-canvas__caption">{{ result.systemType || 'WEB' }} · {{ result.resolvedRelationMode || result.requestedRelationMode || '自动分析关系' }}</div><div v-for="layer in architectureLayers" :key="layer.key" class="architecture-layer"><div class="architecture-layer__name">{{ layer.name }}</div><div class="architecture-layer__nodes"><template v-if="layer.groups?.length"><div v-for="group in layer.groups" :key="group.id" class="architecture-group"><b>{{ group.name }}</b><article v-for="node in group.nodes" :key="node.id || node.name" class="architecture-node" @click="selectNode(node, `${layer.name} / ${group.name}`)"><strong>{{ architectureNodeLabel(node) }}</strong><span v-if="node.description">{{ node.description }}</span></article></div></template><template v-else><article v-for="node in layer.nodes" :key="node.id || node.name" class="architecture-node" @click="selectNode(node, layer.name)"><strong>{{ architectureNodeLabel(node) }}</strong><span v-if="node.description">{{ node.description }}</span></article></template><span v-if="!layer.nodes?.length && !layer.groups?.length" class="architecture-empty">由 AI 自动补全</span></div></div><aside v-if="result.thirdParty?.length" class="architecture-third-party"><strong>第三方服务</strong><span v-for="item in result.thirdParty" :key="item.name || item">{{ item.name || item }}</span></aside><footer v-if="result.features?.length" class="architecture-features"><span v-for="feature in result.features" :key="feature">{{ feature }}</span></footer></div>
      <div v-else class="mind-canvas"><button type="button" class="mind-root" @click="selectNode(mindTree,'中心主题')">{{ mindTree.label }}</button><div class="mind-branches"><MindMapBranch v-for="(branch,index) in mindBranches" :key="branch.id || index" :node="branch" :branch-index="index" :collapse-all="collapseAllMindNodes" @select="selectMindNode" /></div><p v-if="!mindBranches.length" class="mind-empty">本次导图暂无可展示节点</p></div>
    </div></div>
    <aside v-if="selectedNode" class="diagram-inspector"><div><span>{{ selectedNode.kind }}</span><strong>{{ selectedNode.label }}</strong><p v-if="selectedNode.description || selectedNode.content">{{ selectedNode.description || selectedNode.content }}</p><small v-if="selectedNode.laneLabel">所属泳道：{{ selectedNode.laneLabel }}</small></div><button type="button" @click="selectedNode = null">×</button></aside>
  </section>
</template>

<style scoped>
.diagram-workspace{position:relative;overflow:hidden;border:1px solid #dce5ee;border-radius:12px;background:#fff}.diagram-workspace__head{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 14px;border-bottom:1px solid #e6edf3}.diagram-workspace__head strong,.diagram-workspace__head span{display:block}.diagram-workspace__head strong{color:#253d58}.diagram-workspace__head span{max-width:450px;margin-top:3px;overflow:hidden;color:#7b8ca0;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.diagram-workspace__actions{display:flex;align-items:center;gap:5px}.diagram-workspace__actions button{min-width:30px;height:30px;border:1px solid #dbe4ec;border-radius:6px;color:#48647f;background:#fff;font-size:12px;font-weight:700}.diagram-workspace__actions .diagram-workspace__secondary,.diagram-workspace__actions .diagram-workspace__export{padding:0 10px}.diagram-workspace__actions .diagram-workspace__secondary{background:#f7fafc}.diagram-workspace__actions .diagram-workspace__export{border-color:#326994;color:#fff;background:#326994}.diagram-workspace__viewport{min-height:490px;overflow:auto;background-color:#f9fbfd;background-image:radial-gradient(#dbe5ef 1px,transparent 1px);background-size:14px 14px}.diagram-workspace__stage{min-width:650px;min-height:490px;padding:30px;transform-origin:top left}.flow-canvas{display:block;width:100%;min-height:700px}.flow-lane-label{font-size:12px;font-weight:800}.flow-node-group{cursor:pointer}.flow-node{stroke-width:2}.flow-node--process{fill:#f7fbff;stroke:#5181ad}.flow-node--terminal{fill:#edf8f3;stroke:#5eaa7c}.flow-node--decision{fill:#fff8e8;stroke:#e0a841}.flow-node-label{fill:#28425e;font-size:13px;font-weight:700;pointer-events:none}.flow-edge-label{fill:#8b671f;font-size:11px;font-weight:700;paint-order:stroke;stroke:#fff;stroke-width:4px}.architecture-canvas{display:grid;min-height:490px;gap:14px;padding:20px;border:1px solid #dce6ee;border-radius:10px;background:#f8fbfd}.architecture-canvas__caption{color:#668098;font-size:11px;font-weight:700;text-align:right}.architecture-layer{display:grid;grid-template-columns:105px minmax(0,1fr);gap:12px;align-items:stretch}.architecture-layer__name{display:grid;place-items:center;padding:12px;border:1px solid #bdd3e5;border-radius:8px;color:#355d7f;background:#eaf3fa;font-weight:800}.architecture-layer__nodes{display:flex;flex-wrap:wrap;align-items:stretch;gap:10px;padding:10px;border:1px solid #d9e4ed;border-radius:8px;background:#fff}.architecture-group{display:flex;flex:1;flex-wrap:wrap;gap:7px;min-width:200px;padding:8px;border:1px dashed #b9cede;border-radius:7px;background:#fbfdff}.architecture-group>b{width:100%;color:#56738d;font-size:11px}.architecture-node{min-width:115px;flex:1;padding:10px;border:1px solid #a9cad7;border-radius:7px;color:#2f5365;background:#f3fbfa;cursor:pointer}.architecture-node:hover{border-color:#6caa9f;background:#eaf8f4}.architecture-node strong,.architecture-node span{display:block}.architecture-node span{margin-top:4px;color:#6e8796;font-size:11px}.architecture-empty{color:#7e94a5;font-size:12px}.architecture-third-party{display:flex;flex-wrap:wrap;gap:8px;margin-left:117px;padding:10px;color:#556f85;font-size:12px}.architecture-third-party span,.architecture-features span{padding:4px 7px;border:1px solid #d5e2eb;border-radius:5px;background:#fff}.architecture-features{display:flex;flex-wrap:wrap;gap:6px;margin-left:117px}.architecture-features span{color:#3f786f;background:#eef9f6}.mind-canvas{display:grid;place-items:center;min-height:490px;gap:28px;padding:45px 20px;background:#fff}.mind-root{padding:14px 22px;border:2px solid #7c5ce0;border-radius:999px;color:#4c348f;background:#f1edff;font-weight:800;box-shadow:0 8px 16px rgba(102,75,187,.14)}.mind-branches{display:flex;flex-wrap:wrap;justify-content:center;gap:18px;max-width:800px}.mind-branch{position:relative;width:210px;padding:14px;border-left:4px solid hsl(calc(var(--branch-index)*56 + 202),68%,58%);border-radius:8px;color:#334660;background:#faf9ff;box-shadow:0 3px 10px rgba(42,48,75,.06);cursor:pointer}.mind-branch--collapsed{background:#f5f3fb}.mind-branch strong{display:block;padding-right:34px}.mind-branch__toggle{position:absolute;top:10px;right:10px;color:#6c5ab0;background:transparent;font-size:11px}.mind-leaves{display:grid;gap:6px;margin-top:10px;padding-top:9px;border-top:1px solid #e3dff5}.mind-leaves button{padding:4px 0;color:#60738a;background:transparent;text-align:left;font-size:12px}.mind-leaves button:hover{color:#4c348f}.mind-empty{color:#7c8da1}.diagram-inspector{position:absolute;right:12px;bottom:12px;display:flex;gap:12px;max-width:min(340px,calc(100% - 24px));padding:12px;border:1px solid #cbdce9;border-radius:9px;color:#344f6a;background:rgba(255,255,255,.97);box-shadow:0 8px 22px rgba(41,68,92,.16)}.diagram-inspector span,.diagram-inspector strong,.diagram-inspector p,.diagram-inspector small{display:block}.diagram-inspector span{color:#6b8499;font-size:11px}.diagram-inspector strong{margin-top:3px}.diagram-inspector p{margin:5px 0;color:#60778e;font-size:12px;line-height:1.5}.diagram-inspector small{color:#557a95}.diagram-inspector>button{align-self:flex-start;color:#71869b;background:transparent;font-size:18px}@media(max-width:800px){.diagram-workspace__head{align-items:flex-start;flex-direction:column}.diagram-workspace__actions{flex-wrap:wrap}.diagram-workspace__viewport{min-height:360px}.diagram-workspace__stage{padding:15px}.architecture-layer{grid-template-columns:85px minmax(0,1fr)}.architecture-third-party,.architecture-features{margin-left:97px}}
</style>
