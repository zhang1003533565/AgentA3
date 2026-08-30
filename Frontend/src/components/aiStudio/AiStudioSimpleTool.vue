<script setup>
import { useAiStudioTool } from '../../composables/useAiStudioTool'

const props = defineProps({
  tool: { type: String, required: true },
  showWritingOptions: { type: Boolean, default: false },
})

const {
  meta,
  prompt,
  tone,
  wordCount,
  loading,
  error,
  result,
  models,
  selectedModel,
  canGenerate,
  resources,
  answer,
  images,
  generate,
} = useAiStudioTool(props.tool)
</script>

<template>
  <div class="simple-studio">
    <section class="feature-card simple-studio__config">
      <header>
        <h2>生成配置</h2>
        <p>填写需求后开始生成</p>
      </header>

      <label class="studio-field">
        创作需求
        <textarea v-model="prompt" class="feature-textarea" placeholder="描述需要生成的内容、用途和约束" />
      </label>

      <div v-if="showWritingOptions" class="form-grid">
        <label>
          选择模型
          <select v-model="selectedModel" class="feature-select">
            <option v-if="!models.length" value="">暂无可用模型</option>
            <option v-for="model in models" :key="model.value" :value="model.value">{{ model.label }}</option>
          </select>
        </label>
        <label>
          表达语气
          <select v-model="tone" class="feature-select">
            <option>专业</option>
            <option>简洁</option>
            <option>正式</option>
            <option>亲切</option>
          </select>
        </label>
        <label>
          目标字数
          <input v-model="wordCount" class="feature-input" />
        </label>
      </div>

      <button class="feature-button feature-button--primary generate-button" :disabled="!canGenerate" @click="generate">
        {{ loading ? '生成中…' : `生成${meta.title}` }}
      </button>

      <div v-if="error" class="feature-error">
        <strong>生成失败</strong>
        <span>{{ error }}</span>
      </div>
    </section>

    <section class="feature-card simple-studio__result">
      <div class="feature-section__head">
        <div>
          <h2>生成结果</h2>
          <p>生成内容会显示在这里</p>
        </div>
        <span v-if="result" class="feature-status feature-status--completed">已返回</span>
      </div>

      <div v-if="!result && !loading" class="feature-empty result-empty">
        <i />
        <strong>等待生成</strong>
        <span>填写需求后点击生成</span>
      </div>
      <div v-else-if="loading" class="feature-empty">AI 正在生成，请稍候…</div>
      <article v-else>
        <div v-if="answer" class="answer">{{ answer }}</div>
        <div v-if="images.length" class="image-results">
          <img v-for="image in images" :key="image.url || image" :src="image.url || image" alt="AI 生成图片" />
        </div>
        <div v-if="resources.length" class="feature-list">
          <a v-for="resource in resources" :key="resource.id" class="feature-row" :href="resource.previewUrl || resource.url" target="_blank" rel="noreferrer">
            <div class="feature-row__copy">
              <strong>{{ resource.title || resource.kind }}</strong>
              <span>{{ resource.summary }}</span>
            </div>
            <b>打开资源</b>
          </a>
        </div>
        <div v-if="!answer && !images.length && !resources.length" class="feature-empty">
          {{ result.message || '任务已受理，请稍后在 AI 历史中查看结果' }}
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.simple-studio {
  display: grid;
  grid-template-columns: minmax(360px, 480px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.simple-studio__config,
.simple-studio__result {
  padding: 22px;
}

.simple-studio__config header h2 {
  margin: 0 0 4px;
}

.simple-studio__config header p {
  margin: 0 0 16px;
  color: #718096;
  font-size: 13px;
}

.studio-field {
  display: grid;
  gap: 8px;
  margin-bottom: 14px;
  color: #42566b;
  font-size: 13px;
  font-weight: 700;
}

.feature-textarea {
  min-height: 160px;
  resize: vertical;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 14px;
}

.form-grid label {
  display: grid;
  gap: 8px;
  color: #42566b;
  font-size: 13px;
  font-weight: 700;
}

.generate-button {
  width: 100%;
  min-height: 48px;
}

.feature-error {
  display: grid;
  gap: 6px;
  margin-top: 10px;
  padding: 12px;
  border: 1px solid #edc9c3;
  border-radius: 8px;
  color: #a23f34;
  background: #fff8f7;
  font-size: 12px;
}

.simple-studio__result {
  min-height: calc(100vh - 180px);
}

.simple-studio__result article {
  display: grid;
  gap: 18px;
}

.answer {
  padding: 22px;
  border: 1px solid #e0e7ed;
  border-radius: 8px;
  white-space: pre-wrap;
  line-height: 1.85;
}

.image-results {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.image-results img {
  width: 100%;
  border-radius: 8px;
}

.result-empty {
  display: grid;
  place-items: center;
  gap: 9px;
  min-height: 420px;
}

.result-empty i {
  width: 48px;
  height: 40px;
  border: 2px solid #cad7e2;
  border-radius: 8px;
}

@media (max-width: 960px) {
  .simple-studio {
    grid-template-columns: 1fr;
  }
}
</style>
