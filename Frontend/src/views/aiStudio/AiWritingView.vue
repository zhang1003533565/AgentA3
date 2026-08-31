<script setup>
import AiStudioPageShell from '../../components/aiStudio/AiStudioPageShell.vue'
import AiStudioSimpleTool from '../../components/aiStudio/AiStudioSimpleTool.vue'
import { AI_STUDIO_TOOLS } from '../../config/aiStudioTools'

const meta = AI_STUDIO_TOOLS.writing
</script>

<template>
  <AiStudioPageShell :title="meta.title" :subtitle="meta.subtitle" :back-to="meta.backTo">
    <AiStudioSimpleTool tool="writing" show-writing-options />
  </AiStudioPageShell>
</template>
