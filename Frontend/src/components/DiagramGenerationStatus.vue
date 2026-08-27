<script setup>
import { computed, onBeforeUnmount, watch } from 'vue'
import { ref } from 'vue'
import { diagramConfig } from '../config/diagramTools'

const props = defineProps({ type: { type: String, required: true }, active: Boolean })
const step = ref(0)
let timer = null
const stages = computed(() => diagramConfig(props.type).progress)
const progress = computed(() => Math.min(92, 12 + step.value * 20))

function stop() { if (timer) window.clearInterval(timer); timer = null }

watch(() => props.active, (active) => {
  stop(); step.value = 0
  if (active) timer = window.setInterval(() => { if (step.value < stages.value.length - 1) step.value += 1 }, 1400)
}, { immediate: true })
onBeforeUnmount(stop)
</script>

<template>
  <section v-if="active" class="generation-status" aria-live="polite">
    <div class="generation-status__top"><span>AI 正在生成{{ diagramConfig(type).title }}</span><strong>{{ progress }}%</strong></div>
    <div class="generation-status__bar"><i :style="{ width: `${progress}%` }"></i></div>
    <ol><li v-for="(stage,index) in stages" :key="stage" :class="{ active:index===step,done:index<step }"><i>{{ index < step ? '✓' : index + 1 }}</i><span>{{ stage }}</span></li></ol>
    <p>{{ stages[step] }}，请稍候…</p>
  </section>
</template>

<style scoped>
.generation-status{display:grid;align-content:center;min-height:360px;padding:38px;color:#405870}.generation-status__top{display:flex;justify-content:space-between;gap:20px;margin-bottom:10px}.generation-status__top span{font-weight:700}.generation-status__top strong{color:#315f88}.generation-status__bar{height:6px;overflow:hidden;border-radius:999px;background:#e8eef3}.generation-status__bar i{display:block;height:100%;border-radius:inherit;background:#547a9e;transition:width .45s ease}.generation-status ol{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;margin:36px 0 24px;padding:0;list-style:none}.generation-status li{display:grid;justify-items:center;gap:8px;color:#97a5b2;font-size:11px;text-align:center}.generation-status li i{display:grid;place-items:center;width:28px;height:28px;border:1px solid #d4dee7;border-radius:50%;background:#fff;font-style:normal}.generation-status li.active{color:#315f88;font-weight:700}.generation-status li.active i{border-color:#547a9e;color:#fff;background:#547a9e}.generation-status li.done{color:#557f6d}.generation-status li.done i{border-color:#86b39d;color:#fff;background:#6b9b84}.generation-status p{margin:0;color:#728497;text-align:center;font-size:13px}@media(max-width:700px){.generation-status{padding:26px 18px}.generation-status ol{grid-template-columns:1fr}.generation-status li{grid-template-columns:28px 1fr;justify-items:start;align-items:center;text-align:left}}
</style>
