<script setup>
import { ref, watch } from 'vue'

defineOptions({ name: 'MindMapBranch' })
const props = defineProps({ node: { type: Object, required: true }, depth: { type: Number, default: 1 }, branchIndex: { type: Number, default: 0 }, collapseAll: Boolean })
const emit = defineEmits(['select'])
const collapsed = ref(false)

watch(() => props.collapseAll, (value) => { collapsed.value = value })
function label(node) { return node.label || node.name || node.title || node.content || '主题' }
</script>

<template>
  <article class="mind-node" :class="[`mind-node--depth-${Math.min(depth,4)}`, { 'mind-node--collapsed': collapsed }]" :style="{ '--branch-index': branchIndex }" @click="emit('select', node, depth)">
    <div class="mind-node__body">
      <strong>{{ label(node) }}</strong>
      <button v-if="node.children?.length" type="button" :title="collapsed ? '展开子主题' : '收起子主题'" @click.stop="collapsed=!collapsed">{{ collapsed ? '+' : '−' }}</button>
    </div>
    <div v-if="node.children?.length && !collapsed" class="mind-node__children">
      <MindMapBranch v-for="(child,index) in node.children" :key="child.id || `${depth}-${index}-${label(child)}`" :node="child" :depth="depth+1" :branch-index="branchIndex" :collapse-all="collapseAll" @select="(item,level)=>emit('select',item,level)" />
    </div>
  </article>
</template>

<style scoped>
.mind-node{position:relative;min-width:180px;color:#334960}.mind-node__body{position:relative;display:flex;align-items:center;justify-content:space-between;gap:8px;padding:11px 12px;border:1px solid hsl(calc(var(--branch-index)*49 + 202),40%,74%);border-left:4px solid hsl(calc(var(--branch-index)*49 + 202),58%,57%);border-radius:8px;background:#fff;cursor:pointer;transition:border-color .15s ease,background .15s ease}.mind-node__body:hover{background:#f8fafc}.mind-node__body strong{font-size:12px;line-height:1.4}.mind-node__body button{display:grid;place-items:center;flex:0 0 auto;width:20px;height:20px;border:1px solid #d8e0e8;border-radius:50%;color:#5e7287;background:#f8fafc;font-size:14px}.mind-node__children{position:relative;display:grid;gap:7px;margin:8px 0 0 18px;padding-left:15px;border-left:1px solid #d8e1ea}.mind-node__children:before{content:"";position:absolute;top:-8px;left:-1px;width:14px;height:1px;background:#d8e1ea}.mind-node--depth-2 .mind-node__body{padding:9px 10px;background:#fbfcfe}.mind-node--depth-3 .mind-node__body,.mind-node--depth-4 .mind-node__body{padding:7px 9px;background:#fff}.mind-node--depth-3 .mind-node__body strong,.mind-node--depth-4 .mind-node__body strong{font-size:11px}.mind-node--collapsed>.mind-node__body{background:#f5f7fa}
</style>
