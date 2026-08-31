<script setup>
import {
  categoryIconByName,
  conditionLabel,
  statusLabel,
  statusTone,
} from '../../utils/marketplaceCategories'

const props = defineProps({
  item: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['click'])

function parseImages(value) {
  if (Array.isArray(value)) return value
  try {
    return JSON.parse(value || '[]')
  } catch {
    return String(value || '').split(',').filter(Boolean)
  }
}

function categoryNameOf(item) {
  return item?.categoryName || ''
}

function price(value) {
  return Number.isFinite(Number(value)) ? `¥${Number(value).toFixed(2)}` : '价格待确认'
}

function handleClick() {
  emit('click', props.item)
}
</script>

<template>
  <article class="feature-card product-card" @click="handleClick">
    <div class="product-image">
      <img v-if="parseImages(item.images)[0]" :src="parseImages(item.images)[0]" alt="" loading="lazy" />
      <div v-else class="product-image__placeholder">
        <span class="product-image__icon" v-html="categoryIconByName(categoryNameOf(item))" />
        <span>暂无图片</span>
      </div>
      <em :class="'product-status product-status--' + statusTone(item)">{{ statusLabel(item) }}</em>
    </div>
    <div class="product-copy">
      <div class="product-copy__top">
        <strong>{{ item.title }}</strong>
        <span v-if="categoryNameOf(item)" class="product-category">{{ categoryNameOf(item) }}</span>
      </div>
      <p>{{ item.description || '卖家暂未填写描述' }}</p>
      <div class="product-copy__foot">
        <div>
          <b>{{ price(item.price) }}</b>
          <small v-if="item.originalPrice">原价 {{ price(item.originalPrice) }}</small>
          <small v-if="item.condition !== undefined && item.condition !== null">{{ conditionLabel(item.condition) }}</small>
        </div>
        <span>{{ item.campusName || item.tradeLocation || item.location || '校内交易' }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.product-card {
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.16s, box-shadow 0.16s;
}

.product-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(30, 43, 76, 0.08);
}

.product-image {
  position: relative;
  height: 190px;
  background: linear-gradient(180deg, #f3f6f9 0%, #e9eef3 100%);
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-image__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 100%;
  color: #8a9bae;
  font-size: 12px;
}

.product-image__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  color: #6b8299;
  background: rgba(255, 255, 255, 0.72);
}

.product-image__icon :deep(svg) {
  width: 24px;
  height: 24px;
}

.product-status {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
}

.product-status--sale {
  color: #315f8c;
  background: rgba(255, 255, 255, 0.94);
}

.product-status--reserved {
  color: #b45309;
  background: #fff7ed;
}

.product-status--sold,
.product-status--offline {
  color: #667085;
  background: rgba(255, 255, 255, 0.94);
}

.product-copy {
  padding: 16px;
}

.product-copy__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.product-copy__top strong {
  display: block;
  color: #26384d;
  font-size: 16px;
  line-height: 1.35;
}

.product-category {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 999px;
  color: #5b6b7d;
  background: #f3f6f9;
  font-size: 11px;
}

.product-copy > p {
  margin: 0 0 10px;
  color: #667085;
  font-size: 13px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-copy__foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
}

.product-copy__foot b {
  display: block;
  color: #c2410c;
  font-size: 18px;
}

.product-copy__foot small {
  display: block;
  color: #94a3b8;
  font-size: 11px;
}

.product-copy__foot > span {
  color: #64748b;
  font-size: 12px;
  text-align: right;
}
</style>
