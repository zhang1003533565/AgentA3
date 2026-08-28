<script setup>
defineProps({
  label: { type: String, default: '正在生成更详细的图片，请稍等。' },
})
</script>

<template>
  <div class="image-generation-canvas" role="status" aria-live="polite">
    <div class="image-generation-canvas__grid" aria-hidden="true"></div>
    <p class="image-generation-canvas__label">{{ label }}</p>
  </div>
</template>

<style scoped>
.image-generation-canvas {
  position: relative;
  width: min(360px, 100%);
  min-height: 220px;
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: linear-gradient(180deg, #f7f8fa 0%, #eef1f4 100%);
}

.image-generation-canvas__grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle at 1px 1px, rgba(120, 132, 148, 0.22) 1px, transparent 0);
  background-size: 18px 18px;
  animation: image-generation-pan 2.8s linear infinite;
}

.image-generation-canvas__label {
  position: relative;
  z-index: 1;
  margin: 0;
  padding: 18px 16px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}

@keyframes image-generation-pan {
  0% { transform: translate3d(0, 0, 0); }
  100% { transform: translate3d(-18px, -18px, 0); }
}
</style>
