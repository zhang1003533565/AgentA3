<script setup>
import AiStudioPageShell from '../../components/aiStudio/AiStudioPageShell.vue'
import PresentationFlow from '../../components/ppt/PresentationFlow.vue'
import { AI_STUDIO_TOOLS } from '../../config/aiStudioTools'

const meta = AI_STUDIO_TOOLS.presentation
</script>

<template>
  <AiStudioPageShell :title="meta.title" :subtitle="meta.subtitle" :back-to="meta.backTo" full-width>
    <PresentationFlow initial-entry-mode="templateFirst" />
  </AiStudioPageShell>
</template>
