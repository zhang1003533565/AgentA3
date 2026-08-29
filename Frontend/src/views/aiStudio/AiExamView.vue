<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  router.replace('/paper')
})
</script>

<template>
  <div class="paper-state">正在进入试卷生成…</div>
</template>

<style scoped>
.paper-state {
  display: grid;
  place-items: center;
  min-height: 100vh;
  color: #667085;
}
</style>
