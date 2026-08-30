<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ActivityCard from '../campus/ActivityCard.vue'
import SecondhandProductCard from '../marketplace/SecondhandProductCard.vue'
import { getActivityDetail } from '../../api/activity'
import { getSecondhandItemDetail } from '../../api/secondhand'
import {
  businessCardKindLabel,
  businessCardResources,
} from '../../utils/assistantBusinessResources'
import { payloadToActivityItem } from '../../utils/activityCard'

const props = defineProps({
  resources: {
    type: Array,
    default: () => [],
  },
})

const router = useRouter()
const loading = ref(false)
const activityItems = ref([])
const secondhandItems = ref([])

const groupedLabel = ref('查询结果')

function unwrapRecord(response) {
  return response?.data ?? response ?? null
}

async function hydrateResources(resources) {
  const cards = businessCardResources(resources)
  if (!cards.length) {
    activityItems.value = []
    secondhandItems.value = []
    return
  }

  loading.value = true
  const kinds = [...new Set(cards.map((item) => item.kind).filter(Boolean))]
  groupedLabel.value = kinds.length === 1 ? businessCardKindLabel(kinds[0]) : '查询结果'

  const nextActivities = []
  const nextSecondhand = []

  await Promise.all(cards.map(async (resource) => {
    const payload = resource?.payload && typeof resource.payload === 'object' ? resource.payload : {}
    const businessId = payload.businessId || payload.id
    if (!businessId) return

    try {
      if (resource.kind === 'activity') {
        const detail = unwrapRecord(await getActivityDetail(businessId))
        nextActivities.push(detail || payloadToActivityItem(payload))
        return
      }
      if (resource.kind === 'secondhand') {
        const detail = unwrapRecord(await getSecondhandItemDetail(businessId))
        nextSecondhand.push(detail || {
          id: businessId,
          title: resource.title || payload.title || payload.name,
          price: payload.price,
          condition: payload.condition,
          categoryName: payload.category,
          images: payload.imageUrl ? [payload.imageUrl] : [],
          description: resource.summary || '',
          location: payload.location || '',
        })
      }
    } catch {
      if (resource.kind === 'activity') {
        const fallback = payloadToActivityItem(payload)
        if (fallback) nextActivities.push(fallback)
      }
      if (resource.kind === 'secondhand') {
        nextSecondhand.push({
          id: businessId,
          title: resource.title || payload.title || payload.name || '二手物品',
          price: payload.price,
          condition: payload.condition,
          categoryName: payload.category,
          images: payload.imageUrl ? [payload.imageUrl] : [],
          description: resource.summary || '',
          location: payload.location || '',
        })
      }
    }
  }))

  activityItems.value = nextActivities
  secondhandItems.value = nextSecondhand
  loading.value = false
}

watch(() => props.resources, (value) => {
  void hydrateResources(value)
}, { immediate: true, deep: true })

function openActivity(item) {
  if (!item?.id) return
  router.push({ name: 'activity-detail', params: { activityId: String(item.id) } })
}

function openSecondhand(item) {
  if (!item?.id) return
  router.push({ path: '/marketplace', query: { itemId: String(item.id) } })
}
</script>

<template>
  <section v-if="activityItems.length || secondhandItems.length || loading" class="assistant-native-results">
    <header v-if="activityItems.length || secondhandItems.length" class="assistant-native-results__head">
      <strong>{{ groupedLabel }}</strong>
      <span>共 {{ activityItems.length + secondhandItems.length }} 条 · 与业务页面同款卡片</span>
    </header>

    <p v-if="loading" class="assistant-native-results__loading">正在加载卡片详情…</p>

    <div v-if="activityItems.length" class="ca-grid assistant-native-results__grid">
      <ActivityCard
        v-for="(item, index) in activityItems"
        :key="item.id || `activity-${index}`"
        :item="item"
        :show-favorite="false"
        :animation-delay="index * 50"
        @click="openActivity(item)"
      />
    </div>

    <div v-if="secondhandItems.length" class="market-grid assistant-native-results__grid">
      <SecondhandProductCard
        v-for="(item, index) in secondhandItems"
        :key="item.id || `secondhand-${index}`"
        :item="item"
        @click="openSecondhand(item)"
      />
    </div>
  </section>
</template>

<style scoped>
.assistant-native-results {
  margin-top: 10px;
}

.assistant-native-results__head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  padding: 0 2px;
}

.assistant-native-results__head strong {
  font-size: 12px;
}

.assistant-native-results__head span {
  color: var(--muted);
  font-size: 10px;
}

.assistant-native-results__loading {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 11px;
}

.ca-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.market-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.assistant-native-results__grid + .assistant-native-results__grid {
  margin-top: 16px;
}

@media (max-width: 720px) {
  .ca-grid,
  .market-grid {
    grid-template-columns: 1fr;
  }
}
</style>
