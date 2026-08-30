<script setup>
import DiagramWorkspace from '../DiagramWorkspace.vue'
import DiagramGenerationStatus from '../DiagramGenerationStatus.vue'
import DiagramSettingsPanel from '../DiagramSettingsPanel.vue'
import { useAiStudioTool } from '../../composables/useAiStudioTool'

const props = defineProps({
  tool: { type: String, required: true },
})

const {
  meta,
  prompt,
  loading,
  uploading,
  error,
  result,
  uploadedFile,
  historyItems,
  showHistory,
  fileInput,
  optimizing,
  optimizeInstruction,
  showOptimize,
  historyLoading,
  historyQuery,
  localFileUrl,
  settings,
  currentDiagramConfig,
  canGenerate,
  filteredHistory,
  uploadedFileMeta,
  generate,
  chooseFile,
  handleFile,
  removeUploadedFile,
  previewUploadedFile,
  updateDiagramSettings,
  openHistoryItem,
  removeHistoryItem,
  openOptimizeDialog,
  optimizeCurrentMindMap,
  openFullscreen,
  exportResult,
} = useAiStudioTool(props.tool)
</script>

<template>
  <div class="diagram-studio">
    <section class="feature-card diagram-studio__config">
      <header class="diagram-studio__config-head">
        <div>
          <h2>生成配置</h2>
          <p>{{ currentDiagramConfig.subtitle }}</p>
        </div>
        <button type="button" class="history-button" @click="showHistory = true">历史记录</button>
      </header>

      <label class="studio-field">
        {{ currentDiagramConfig.inputLabel }}
        <textarea v-model="prompt" class="feature-textarea" maxlength="2000" :placeholder="currentDiagramConfig.placeholder" />
        <small class="count">{{ prompt.length }}/2000</small>
      </label>

      <input ref="fileInput" class="file-input" type="file" accept=".pdf,.doc,.docx,.ppt,.pptx" @change="handleFile" />
      <div class="file-import">
        <button type="button" class="feature-button" :disabled="uploading" @click="chooseFile">{{ uploading ? '正在导入…' : '导入文件' }}</button>
        <span>支持 PDF / Word / PPT（≤20MB）</span>
      </div>

      <div v-if="uploadedFile" class="uploaded-file">
        <div>
          <strong>{{ uploadedFile.fileName || '已导入文件' }}</strong>
          <small>{{ uploadedFileMeta || '文件已解析，可作为生成依据' }}</small>
        </div>
        <div>
          <button v-if="localFileUrl" type="button" @click="previewUploadedFile">预览</button>
          <button type="button" class="danger" @click="removeUploadedFile">移除</button>
        </div>
      </div>

      <DiagramSettingsPanel :type="tool" :model-value="settings[tool]" @update:model-value="updateDiagramSettings" />

      <section class="recent-history">
        <div>
          <h3>最近生成</h3>
          <button type="button" @click="showHistory = true">查看全部</button>
        </div>
        <button v-for="item in historyItems.slice(0, 3)" :key="item.id" type="button" class="recent-history__item" @click="openHistoryItem(item)">
          <strong>{{ item.title }}</strong>
          <span>{{ item.createTime || '查看结果与恢复配置' }}</span>
        </button>
        <p v-if="historyLoading">正在加载历史记录…</p>
        <p v-else-if="!historyItems.length">暂无生成记录，完成一次生成后会显示在这里。</p>
      </section>

      <button class="feature-button feature-button--primary generate-button" :disabled="!canGenerate" @click="generate">
        {{ loading ? '生成中…' : `生成${meta.title}` }}
      </button>

      <div v-if="error" class="feature-error">
        <strong>生成失败</strong>
        <span>{{ error }}</span>
        <button v-if="canGenerate" type="button" @click="generate">重新生成</button>
      </div>
    </section>

    <section class="feature-card studio-result diagram-studio__result">
      <div class="feature-section__head">
        <div>
          <h2>生成结果</h2>
          <p>结果支持缩放、节点查看、全屏预览和数据导出</p>
        </div>
        <div v-if="result" class="result-actions">
          <button v-if="tool === 'mind_map'" type="button" :disabled="optimizing" @click="openOptimizeDialog">{{ optimizing ? '优化中…' : '优化导图' }}</button>
          <span class="feature-status feature-status--completed">已完成</span>
        </div>
      </div>

      <div v-if="!result && !loading" class="feature-empty result-empty">
        <i />
        <strong>等待生成</strong>
        <span>填写需求或导入文件，生成结果会显示在这里</span>
      </div>
      <DiagramGenerationStatus v-else-if="loading" :type="tool" :active="loading" />
      <DiagramWorkspace v-else :type="tool" :result="result" @export="exportResult" @fullscreen="openFullscreen" />
    </section>

    <div v-if="showHistory" class="feature-modal-mask" @click.self="showHistory = false">
      <section class="feature-modal history-modal">
        <header class="feature-modal__head">
          <div>
            <h2>{{ meta.title }}历史记录</h2>
            <p>查看结果时会同步恢复当时的生成配置</p>
          </div>
          <button class="feature-modal__close" type="button" @click="showHistory = false">×</button>
        </header>
        <input v-model="historyQuery" class="history-search" placeholder="搜索标题或生成内容" />
        <div v-if="filteredHistory.length" class="history-list">
          <div v-for="item in filteredHistory" :key="item.id" class="history-row">
            <button class="history-row__main" type="button" @click="openHistoryItem(item)">
              <strong>{{ item.title }}</strong>
              <span>{{ item.preview || item.description || item.content || '点击查看生成结果' }}</span>
              <small>{{ item.createTime }}</small>
            </button>
            <button class="history-row__delete" type="button" title="删除本条历史记录" @click="removeHistoryItem(item)">删除</button>
          </div>
        </div>
        <div v-else class="feature-empty">{{ historyLoading ? '正在加载历史记录…' : '暂无匹配记录' }}</div>
      </section>
    </div>

    <div v-if="showOptimize" class="feature-modal-mask" @click.self="showOptimize = false">
      <section class="feature-modal optimize-modal">
        <header class="feature-modal__head">
          <div>
            <h2>优化思维导图</h2>
            <p>保留当前内容并按你的要求重新整理结构</p>
          </div>
          <button class="feature-modal__close" type="button" @click="showOptimize = false">×</button>
        </header>
        <div class="quick-tags">
          <button v-for="tag in ['补充关键知识点', '精简重复内容', '调整层级结构', '突出复习重点']" :key="tag" type="button" @click="optimizeInstruction = tag">{{ tag }}</button>
        </div>
        <textarea v-model="optimizeInstruction" maxlength="500" placeholder="例如：补充复习重点，并将相近主题合并到同一分支" />
        <footer>
          <button type="button" @click="showOptimize = false">取消</button>
          <button type="button" class="primary" :disabled="!optimizeInstruction.trim() || optimizing" @click="optimizeCurrentMindMap">{{ optimizing ? '正在优化…' : '开始优化' }}</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.diagram-studio{display:grid;grid-template-columns:minmax(390px,520px) minmax(600px,1fr);gap:16px;align-items:start}.diagram-studio__config,.diagram-studio__result{padding:22px}.diagram-studio__config{max-height:calc(100vh - 180px);overflow:auto;scrollbar-width:thin}.diagram-studio__config-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-bottom:16px}.diagram-studio__config-head h2{margin:0 0 4px}.diagram-studio__config-head p{margin:0;color:#718096;font-size:13px}.history-button{padding:6px 9px;border:1px solid #d8e3ec;border-radius:6px;color:#41617f;background:#f8fbfd;font-size:12px;font-weight:700;cursor:pointer}.studio-field{position:relative;display:grid;gap:8px;margin-bottom:14px;color:#42566b;font-size:13px;font-weight:700}.feature-textarea{min-height:132px;resize:vertical}.count{position:absolute;right:9px;bottom:10px;color:#77899d;font-size:11px;font-weight:500}.file-input{display:none}.file-import{display:flex;align-items:center;gap:10px;margin:0 0 10px}.file-import span{color:#718096;font-size:12px}.uploaded-file{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:14px;padding:10px 11px;border:1px solid #bfe2d3;border-radius:7px;color:#35725d;background:#f0faf5;font-size:12px}.uploaded-file>div:first-child{display:grid;gap:3px;min-width:0}.uploaded-file strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.uploaded-file small{color:#6e887e;font-weight:500}.uploaded-file>div:last-child{display:flex;gap:8px}.uploaded-file button{color:#416d87;background:transparent;cursor:pointer}.uploaded-file button.danger{color:#a54239}.recent-history{display:grid;gap:6px;margin-top:20px;padding:13px;border:1px solid #e2e9ef;border-radius:8px;background:#fafcfd}.recent-history>div{display:flex;align-items:center;justify-content:space-between}.recent-history h3{margin:0;color:#425970;font-size:13px}.recent-history>div button{color:#416e93;background:transparent;font-size:11px;cursor:pointer}.recent-history__item{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:7px 9px;border-radius:5px;color:#486076;background:#fff;text-align:left;cursor:pointer}.recent-history__item:hover{background:#f0f5f8}.recent-history__item strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.recent-history__item span,.recent-history p{color:#8493a2;font-size:10px}.recent-history p{margin:4px 0}.generate-button{width:100%;margin-top:18px;min-height:48px}.feature-error{display:grid;gap:6px;margin-top:10px;padding:12px;border:1px solid #edc9c3;border-radius:8px;color:#a23f34;background:#fff8f7;font-size:12px}.feature-error button{justify-self:start;color:#8a392f;background:transparent;font-weight:700;cursor:pointer}.diagram-studio__result{min-height:calc(100vh - 180px)}.result-actions{display:flex;align-items:center;gap:8px}.result-actions button{padding:7px 11px;border:1px solid #d7e1e9;border-radius:6px;color:#3e6180;background:#fff;font-size:12px;font-weight:700;cursor:pointer}.result-empty{display:grid;place-items:center;align-content:center;gap:9px;min-height:520px}.result-empty i{width:48px;height:40px;border:2px solid #cad7e2;border-radius:8px;background:linear-gradient(#fff 0 12px,#edf3f7 12px 15px,#fff 15px 23px,#edf3f7 23px 26px,#fff 26px)}.history-modal{width:min(680px,calc(100% - 32px));max-height:min(720px,calc(100vh - 48px));overflow:auto}.history-search{width:100%;height:40px;margin:4px 0 14px;padding:0 12px;border:1px solid #d9e3eb;border-radius:7px;outline:none}.history-list{display:grid;gap:8px}.history-row{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:8px;padding:8px;border:1px solid #e3e9ef;border-radius:8px}.history-row__main{display:grid;gap:4px;min-width:0;color:#344f6a;background:transparent;text-align:left;cursor:pointer}.history-row__delete{padding:5px 8px;border-radius:5px;color:#a24a41;background:#fff5f4;font-size:11px;cursor:pointer}.optimize-modal{width:min(560px,calc(100% - 32px))}.quick-tags{display:flex;flex-wrap:wrap;gap:7px;margin:14px 0}.quick-tags button{padding:7px 9px;border:1px solid #d7e2ea;border-radius:999px;color:#536d84;background:#f8fbfd;font-size:11px;cursor:pointer}.optimize-modal textarea{width:100%;min-height:130px;padding:12px;border:1px solid #d8e2ea;border-radius:8px;resize:vertical;outline:none}.optimize-modal footer{display:flex;justify-content:flex-end;gap:9px;margin-top:14px}.optimize-modal footer button{min-width:88px;height:38px;border:1px solid #d6e0e8;border-radius:7px;color:#53697d;background:#fff;cursor:pointer}.optimize-modal footer .primary{border-color:#326994;color:#fff;background:#326994}@media(max-width:1120px){.diagram-studio{grid-template-columns:1fr}.diagram-studio__config{max-height:none}}
</style>
