<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { API_BASE_URL } from '../api/request'
import { getToken } from '../utils/auth'

const props = defineProps({
  templates: { type: Array, default: () => [] },
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'select'])

const thumbMap = ref({})
const objectUrls = ref([])

const resolveThumbnailPath = (thumbnailUrl) => {
  const raw = String(thumbnailUrl || '').trim()
  if (!raw) return ''
  if (raw.startsWith('http://') || raw.startsWith('https://')) return raw
  return raw.startsWith('/') ? `${API_BASE_URL}${raw}` : `${API_BASE_URL}/${raw}`
}

const revokeObjectUrls = () => {
  objectUrls.value.forEach((url) => URL.revokeObjectURL(url))
  objectUrls.value = []
  thumbMap.value = {}
}

watch(() => props.templates, async (templates) => {
  revokeObjectUrls()
  if (!templates?.length) return
  const token = getToken()
  const entries = await Promise.all(templates.map(async (template) => {
    const url = resolveThumbnailPath(template.thumbnailUrl)
    if (!url) return [template.id, '']
    try {
      const response = await fetch(url, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (!response.ok) return [template.id, '']
      const blob = await response.blob()
      const objectUrl = URL.createObjectURL(blob)
      objectUrls.value.push(objectUrl)
      return [template.id, objectUrl]
    } catch {
      return [template.id, '']
    }
  }))
  thumbMap.value = Object.fromEntries(entries.filter(([, objectUrl]) => objectUrl))
}, { immediate: true })

onBeforeUnmount(revokeObjectUrls)

const items = computed(() => props.templates || [])

function choose(templateId) {
  if (props.disabled) return
  emit('update:modelValue', templateId)
  emit('select', templateId)
}
</script>

<template>
  <div v-if="items.length" class="ppt-template-picker">
    <button
      v-for="template in items"
      :key="template.id"
      type="button"
      class="ppt-template-picker__card"
      :class="{ 'is-selected': modelValue === template.id }"
      :disabled="disabled"
      @click="choose(template.id)"
    >
      <img
        v-if="thumbMap[template.id]"
        class="ppt-template-picker__thumb"
        :src="thumbMap[template.id]"
        :alt="template.name"
      >
      <div v-else class="ppt-template-picker__thumb" />
      <div class="ppt-template-picker__meta">
        <strong>{{ template.name }}</strong>
        <span v-if="template.default" class="ppt-template-picker__tag">默认</span>
      </div>
      <p>{{ template.description }}</p>
    </button>
  </div>
</template>

<style scoped>
.ppt-template-picker {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.ppt-template-picker__card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px solid #e3e8ee;
  border-radius: 12px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.ppt-template-picker__card.is-selected,
.ppt-template-picker__card:hover {
  border-color: #7a96ad;
  box-shadow: 0 8px 20px rgba(44, 62, 80, 0.08);
}

.ppt-template-picker__thumb {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border-radius: 8px;
  background: #f2f4f7;
}

.ppt-template-picker__meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ppt-template-picker__tag {
  font-size: 11px;
  color: #667788;
}

.ppt-template-picker__card p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #667788;
}
</style>
