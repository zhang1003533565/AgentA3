<script setup>
import AppTabBar from '../components/AppTabBar.vue'
</script>

<template>
  <div class="campus-activities-view">
    <AppTabBar />
    <main class="page" aria-label="校园活动"></main>
  </div>
</template>

<style scoped>
.campus-activities-view {
  min-height: 100vh;
  background: #f4f7fb;
}
</style>
