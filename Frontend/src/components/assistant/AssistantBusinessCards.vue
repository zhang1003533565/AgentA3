<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  businessCardDetailRows,
  businessCardKindLabel,
  businessCardResources,
} from '../../utils/assistantBusinessResources'

const props = defineProps({
  resources: {
    type: Array,
    default: () => [],
  },
})

const router = useRouter()
const cards = computed(() => businessCardResources(props.resources))

const groupedLabel = computed(() => {
  const kinds = [...new Set(cards.value.map((item) => item.kind).filter(Boolean))]
  if (kinds.length === 1) return businessCardKindLabel(kinds[0])
  return '查询结果'
})

function imageUrl(resource) {
  const url = resource?.payload?.imageUrl
  return typeof url === 'string' && url.trim() ? url.trim() : ''
}

function openResource(resource) {
  const payload = resource?.payload && typeof resource.payload === 'object' ? resource.payload : {}
  const businessId = payload.businessId || payload.id
  if (!businessId) return
  const id = String(businessId)
  switch (String(resource?.kind || '').trim()) {
    case 'activity':
      router.push({ name: 'activity-detail', params: { activityId: id } })
      return
    case 'secondhand':
      router.push({ path: '/marketplace', query: { itemId: id } })
      return
    case 'course':
      router.push({ name: 'campus-course', params: { courseId: id } })
      return
    case 'meeting':
      router.push({ path: '/meetings', query: { meetingId: id } })
      return
    case 'dining':
      router.push({ path: '/discount' })
      return
    case 'facility':
      router.push({ path: '/map' })
      return
    default:
      break
  }
}
</script>

<template>
  <section v-if="cards.length" class="assistant-result-panel">
    <header class="assistant-result-head">
      <strong>{{ groupedLabel }}</strong>
      <span>共 {{ cards.length }} 条 · 点击卡片查看详情</span>
    </header>
    <div class="assistant-result-grid">
      <button
        v-for="(resource, index) in cards"
        :key="resource.id || `${resource.kind}-${resource.title}`"
        type="button"
        class="assistant-result-card"
        @click="openResource(resource)"
      >
        <div v-if="imageUrl(resource)" class="assistant-result-card-cover">
          <img :src="imageUrl(resource)" :alt="resource.title" />
        </div>
        <div class="assistant-result-card-body">
          <span class="assistant-result-card-index">#{{ index + 1 }}</span>
          <strong class="assistant-result-card-title">{{ resource.title }}</strong>
          <p v-if="resource.summary" class="assistant-result-card-summary">{{ resource.summary }}</p>
          <dl class="assistant-result-card-fields">
            <div
              v-for="row in businessCardDetailRows(resource)"
              :key="`${resource.id}-${row.key}`"
              class="assistant-result-card-field"
            >
              <dt>{{ row.label }}</dt>
              <dd>{{ row.value }}</dd>
            </div>
          </dl>
        </div>
        <span class="assistant-result-card-action">查看详情</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.assistant-result-panel {
  margin-top: 10px;
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  overflow: hidden;
  background: var(--surface);
}

.assistant-result-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line-strong);
  background: var(--surface-soft);
}

.assistant-result-head strong {
  font-size: 12px;
}

.assistant-result-head span {
  color: var(--muted);
  font-size: 10px;
}

.assistant-result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
  padding: 10px;
}

.assistant-result-card {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  background: var(--surface);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.assistant-result-card:hover {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--line-strong));
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
  transform: translateY(-1px);
}

.assistant-result-card-cover {
  aspect-ratio: 16 / 9;
  background: var(--surface-soft);
}

.assistant-result-card-cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.assistant-result-card-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  padding: 10px 10px 8px;
}

.assistant-result-card-index {
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.04em;
}

.assistant-result-card-title {
  font-size: 12px;
  line-height: 1.4;
}

.assistant-result-card-summary {
  margin: 0;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.45;
}

.assistant-result-card-fields {
  display: grid;
  gap: 4px;
  margin: 2px 0 0;
}

.assistant-result-card-field {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px;
  align-items: start;
}

.assistant-result-card-field dt {
  color: var(--muted);
  font-size: 9px;
  white-space: nowrap;
}

.assistant-result-card-field dd {
  margin: 0;
  color: var(--text);
  font-size: 10px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.assistant-result-card-action {
  padding: 8px 10px;
  border-top: 1px solid var(--line-strong);
  color: var(--accent);
  background: var(--surface-soft);
  font-size: 10px;
  text-align: center;
}

@media (max-width: 720px) {
  .assistant-result-grid {
    grid-template-columns: 1fr;
  }
}
</style>
