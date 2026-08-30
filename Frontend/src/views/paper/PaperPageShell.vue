<script setup>
import { useRouter } from 'vue-router'

import PaperSteps from './PaperSteps.vue'

defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  backTo: { type: [String, Object], default: '/paper' },
  step: { type: Number, default: 0 },
  showSteps: { type: Boolean, default: false },
  fullBleed: { type: Boolean, default: false },
})

const router = useRouter()

function goBack(target) {
  if (typeof target === 'string') router.push(target)
  else router.push(target)
}
</script>

<template>
  <div class="paper-shell" :class="{ 'paper-shell--full': fullBleed }">
    <header class="paper-header">
      <button class="paper-header__back" type="button" aria-label="返回" @click="goBack(backTo)">‹</button>
      <div class="paper-header__title">
        <h1>{{ title }}</h1>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.extra" class="paper-header__extra">
        <slot name="extra" />
      </div>
    </header>
    <main class="paper-main">
      <PaperSteps v-if="showSteps" :active="step" />
      <slot />
    </main>
  </div>
</template>

<style scoped>
@import './paper.css';
</style>
