<script setup>
defineProps({
  active: { type: Number, default: 1 },
})

const steps = [
  { no: 1, label: '试卷信息与选题' },
  { no: 2, label: '页面格式' },
  { no: 3, label: '预览与确认' },
]
</script>

<template>
  <div class="paper-steps">
    <div
      v-for="step in steps"
      :key="step.no"
      class="paper-step"
      :class="{
        'paper-step--active': active === step.no,
        'paper-step--done': active > step.no,
      }"
    >
      <span class="paper-step__no">{{ step.no }}</span>
      <span>{{ step.label }}</span>
    </div>
  </div>
</template>

<style scoped>
@import './paper.css';
</style>
