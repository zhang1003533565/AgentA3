<script setup>
import AiStudioPageShell from '../../components/aiStudio/AiStudioPageShell.vue'
import AiStudioDiagramTool from '../../components/aiStudio/AiStudioDiagramTool.vue'
import { AI_STUDIO_TOOLS } from '../../config/aiStudioTools'

const meta = AI_STUDIO_TOOLS.architecture
</script>

<template>
  <AiStudioPageShell :title="meta.title" :subtitle="meta.subtitle" :back-to="meta.backTo" full-width>
    <AiStudioDiagramTool tool="architecture" />
  </AiStudioPageShell>
</template>
