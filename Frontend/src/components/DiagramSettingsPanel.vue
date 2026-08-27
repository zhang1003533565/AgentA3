<script setup>
import { computed } from 'vue'
import { diagramConfig } from '../config/diagramTools'

const props = defineProps({ type: { type: String, required: true }, modelValue: { type: Object, required: true } })
const emit = defineEmits(['update:modelValue'])
const config = computed(() => diagramConfig(props.type))

function patch(key, value) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

function toggle(key, value) {
  const current = Array.isArray(props.modelValue[key]) ? props.modelValue[key] : []
  patch(key, current.includes(value) ? current.filter((item) => item !== value) : [...current, value])
}
</script>

<template>
  <section class="diagram-settings" aria-label="图谱专属设置">
    <template v-if="type === 'architecture'">
      <div class="setting-section">
        <h3>系统类型</h3>
        <div class="option-grid" :style="{ '--columns': 4 }">
          <button v-for="item in config.systemTypes" :key="item.value" type="button" :class="{ selected: modelValue.systemType === item.value }" @click="patch('systemType', item.value)"><strong>{{ item.label }}</strong></button>
        </div>
      </div>
      <div class="setting-section">
        <h3>架构层级（多选）</h3>
        <button type="button" class="option-row" :class="{ selected: modelValue.autoLayers }" @click="patch('autoLayers', !modelValue.autoLayers)">
          <span class="option-row__mark">{{ modelValue.autoLayers ? '●' : '○' }}</span><span><strong>AI 自动分析</strong><small>根据需求智能判断架构层级</small></span>
        </button>
        <div v-if="!modelValue.autoLayers" class="option-stack">
          <button v-for="item in config.layers" :key="item.value" type="button" class="option-row" :class="{ selected: modelValue.layers.includes(item.value) }" @click="toggle('layers', item.value)">
            <span class="option-row__mark">{{ modelValue.layers.includes(item.value) ? '✓' : '○' }}</span><span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
          </button>
        </div>
      </div>
      <div class="setting-section">
        <h3>重点展示</h3>
        <div class="check-grid">
          <button v-for="item in config.focus" :key="item.value" type="button" :class="{ selected: modelValue.focus.includes(item.value) }" @click="toggle('focus', item.value)"><span>{{ modelValue.focus.includes(item.value) ? '✓' : '○' }}</span>{{ item.label }}</button>
        </div>
      </div>
      <div class="setting-section">
        <h3>关系表达</h3>
        <div class="option-grid" :style="{ '--columns': 2 }">
          <button v-for="item in config.relations" :key="item.value" type="button" :class="{ selected: modelValue.relation === item.value }" @click="patch('relation', item.value)"><strong>{{ item.label }}</strong><small>{{ item.description }}</small></button>
        </div>
      </div>
    </template>

    <template v-else>
      <label v-if="type === 'mind_map'" class="center-topic">
        <span>中心主题（可选）</span>
        <input :value="modelValue.centerTopic" maxlength="10" placeholder="不填写将由 AI 自动提取" @input="patch('centerTopic', $event.target.value)" />
        <small>{{ (modelValue.centerTopic || '').length }}/10</small>
      </label>
      <div v-for="section in config.sections" :key="section.key" class="setting-section">
        <h3>{{ section.label }}</h3>
        <div class="option-grid" :style="{ '--columns': section.columns }">
          <button v-for="item in section.options" :key="item.value" type="button" :class="{ selected: modelValue[section.key] === item.value }" @click="patch(section.key, item.value)"><strong>{{ item.label }}</strong><small v-if="item.description">{{ item.description }}</small></button>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.diagram-settings{display:grid;gap:18px;padding-top:4px}.setting-section{display:grid;gap:8px}.setting-section h3{margin:0;color:#2f465e;font-size:13px}.option-grid{display:grid;grid-template-columns:repeat(var(--columns),minmax(0,1fr));gap:7px}.option-grid button,.check-grid button,.option-row{min-height:42px;padding:8px 9px;border:1px solid #d8e2eb;border-radius:7px;color:#536a80;background:#fff;text-align:center}.option-grid button:hover,.check-grid button:hover,.option-row:hover{border-color:#9eb7cd}.option-grid button.selected,.check-grid button.selected,.option-row.selected{border-color:#547a9e;color:#294f73;background:#edf4fa}.option-grid strong,.option-grid small,.option-row strong,.option-row small{display:block}.option-grid strong,.option-row strong{font-size:12px}.option-grid small,.option-row small{margin-top:3px;color:#8190a1;font-size:10px;font-weight:500;line-height:1.25}.option-row{display:flex;align-items:center;gap:9px;width:100%;text-align:left}.option-row__mark{width:17px;color:#3c6d96;font-weight:800}.option-stack{display:grid;gap:6px}.check-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px}.check-grid button{display:flex;align-items:center;gap:6px;min-height:36px;text-align:left;font-size:12px;font-weight:700}.check-grid span{color:#3c6d96}.center-topic{position:relative;display:grid;gap:7px;color:#2f465e;font-size:13px;font-weight:700}.center-topic input{height:40px;padding:0 46px 0 11px;border:1px solid #d8e2eb;border-radius:7px;color:#30465c;background:#fff;outline:none}.center-topic input:focus{border-color:#547a9e;box-shadow:0 0 0 3px rgba(84,122,158,.08)}.center-topic>small{position:absolute;right:10px;bottom:13px;color:#8292a3;font-size:10px}@media(max-width:720px){.option-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
