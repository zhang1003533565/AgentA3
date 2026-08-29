<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { usePaperPrintPreview } from '../../composables/usePaperPrintPreview'
import PaperPageShell from './PaperPageShell.vue'

const route = useRoute()
const router = useRouter()
const paperId = computed(() => route.query.paperId)

const {
  paper,
  layout,
  loading,
  error,
  showAnswers,
  zoom,
  exporting,
  previewGeneratedAt,
  previewPages,
  questionGroups,
  totalScore,
  previewTitle,
  sealedA3,
  titleFontSize,
  subtitleFontSize,
  hasStudentInfo,
  selectedStudentFields,
  paperDimensions,
  pagesStageStyle,
  pageWrapperStyle,
  paperStyle,
  contentStyle,
  questionAreaStyle,
  scoreSummaryStyle,
  load,
  changeZoom,
  exportPdf,
  groupScoreText,
  questionText,
  answerSpaceLines,
  solutionLabel,
  chineseNumber,
  optionLabel,
  options,
  longOptions,
} = usePaperPrintPreview(paperId)

onMounted(() => {
  if (!paperId.value) error.value = '缺少试卷 ID'
  else load()
})
</script>

<template>
  <PaperPageShell title="真实 Word PDF 预览" back-to="/paper/preview" :full-bleed="true">
    <template #extra>
      <button class="paper-link" type="button" :disabled="loading" @click="load">↻ 重新生成</button>
    </template>

    <div class="toolbar print-hidden">
      <span class="badge">{{ previewPages.length }} 页</span>
      <span>有效期至 {{ previewGeneratedAt }}</span>
      <div class="zoom">
        <button type="button" @click="changeZoom(-0.1)">−</button>
        <span>{{ Math.round(zoom * 100) }}%</span>
        <button type="button" @click="changeZoom(0.1)">＋</button>
      </div>
      <button type="button" :disabled="exporting" @click="exportPdf(false)">导出试卷 PDF</button>
      <button type="button" :disabled="exporting" @click="exportPdf(true)">导出答案 PDF</button>
      <button type="button" @click="showAnswers = !showAnswers">{{ showAnswers ? '隐藏答案' : '显示答案' }}</button>
    </div>

    <div v-if="loading" class="paper-state">正在读取试卷和版式…</div>
    <div v-else-if="error" class="paper-empty paper-empty--error">
      <p>{{ error }}</p>
      <button class="paper-btn paper-btn--primary" type="button" @click="load">重新加载</button>
    </div>

    <div v-else class="preview-scroll print-hidden">
      <div class="pages-stage" :style="pagesStageStyle">
        <div
          v-for="(examPage, pageIndex) in previewPages"
          :key="pageIndex"
          class="exam-page-wrapper"
          :style="pageWrapperStyle"
        >
          <div class="exam-page" :style="paperStyle">
            <div
              v-if="(sealedA3 && pageIndex === 0) || (!sealedA3 && layout.bindingLine)"
              :class="['binding-zone', layout.bindingPosition === 'right' ? 'binding-zone-right' : 'binding-zone-left']"
            >
              <div class="sealing-band"><span>请 不 要 在 装 订 线 内 答 题</span></div>
              <div class="staple-line"></div>
            </div>
            <div v-if="sealedA3" class="center-divider"></div>
            <div class="paper-content" :style="contentStyle">
              <div class="question-area" :style="questionAreaStyle">
                <template v-if="pageIndex === 0">
                  <div class="exam-header">
                    <div class="paper-title" :style="{ fontSize: titleFontSize }">{{ previewTitle }}</div>
                    <div class="paper-subtitle" :style="{ fontSize: subtitleFontSize }">
                      考试时间：{{ paper.duration || 0 }}分钟　　满分：{{ totalScore }}分
                    </div>
                  </div>
                  <div v-if="hasStudentInfo && !sealedA3" class="student-info">
                    <span v-if="selectedStudentFields.includes('school')">学校 ____________</span>
                    <span v-if="selectedStudentFields.includes('grade')">年级 ____________</span>
                    <span v-if="selectedStudentFields.includes('class')">班级 ____________</span>
                    <span v-if="selectedStudentFields.includes('name')">姓名 ____________</span>
                    <span v-if="selectedStudentFields.includes('studentNo')">学号 ____________</span>
                  </div>
                  <div class="score-summary" :style="scoreSummaryStyle">
                    <div class="score-row">
                      <div class="score-label">大题</div>
                      <div v-for="(group, index) in questionGroups" :key="`title-${group.type}`">{{ chineseNumber(index + 1) }}</div>
                      <div class="score-total">总分</div>
                    </div>
                    <div class="score-row">
                      <div class="score-label">得分</div>
                      <div v-for="group in questionGroups" :key="`score-${group.type}`"></div>
                      <div class="score-total"></div>
                    </div>
                  </div>
                </template>
                <div v-for="segment in examPage.segments" :key="`${segment.group.type}-${segment.part}`" class="question-group">
                  <div v-if="segment.showHeading" class="group-heading">
                    <div class="group-score-box"><div>阅卷人</div><div></div><div>得分</div><div></div></div>
                    <div class="group-heading-text">{{ chineseNumber(segment.groupIndex + 1) }}、{{ segment.group.title }}{{ groupScoreText(segment.group) }}</div>
                  </div>
                  <div v-for="item in segment.items" :key="item.questionId" class="question-item">
                    <div class="question-title">
                      <span class="question-number">{{ item.displayNumber }}.</span>
                      <div class="question-content">
                        {{ questionText(item) }}<span class="score">（{{ item.score || 0 }}分）</span>
                      </div>
                    </div>
                    <div v-if="options(item.question.options).length" :class="['options', { 'options-single': longOptions(item) }]">
                      <span v-for="(option, index) in options(item.question.options)" :key="index">{{ optionLabel(index) }}. {{ option }}</span>
                    </div>
                    <div v-if="showAnswers" class="answer-block">
                      <div><span class="answer-label">{{ solutionLabel(item) }}</span>{{ item.question.answer || '暂无' }}</div>
                      <div v-if="item.question.analysis"><span class="answer-label">解析：</span>{{ item.question.analysis }}</div>
                    </div>
                    <div v-else-if="answerSpaceLines(item)" class="answer-space" :style="{ height: `${answerSpaceLines(item) * 28}px` }"></div>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="sealedA3" class="paper-footer paper-footer-double">
              <span>第 {{ pageIndex * 2 + 1 }} 页 共 {{ previewPages.length * 2 }} 页</span>
              <span>第 {{ pageIndex * 2 + 2 }} 页 共 {{ previewPages.length * 2 }} 页</span>
            </div>
            <div v-else class="paper-footer">第 {{ pageIndex + 1 }} 页 共 {{ previewPages.length }} 页</div>
          </div>
        </div>
      </div>
    </div>

    <footer class="paper-bottom print-hidden">
      <button class="paper-btn paper-btn--secondary" type="button" @click="router.push({ path: '/paper/layout', query: { paperId } })">调整版式</button>
      <button class="paper-btn paper-btn--primary" type="button" @click="router.push({ path: '/paper/preview', query: { paperId } })">返回预览</button>
    </footer>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid #dce3ec;
  border-radius: 10px;
  background: #fff;
}

.badge {
  padding: 4px 10px;
  border: 1px solid #63a7ff;
  border-radius: 8px;
  color: #1672e8;
  background: #eff7ff;
}

.zoom {
  display: flex;
  gap: 8px;
  align-items: center;
}

.preview-scroll {
  overflow: auto;
  padding: 20px 12px 80px;
  background: #e8edf3;
}

.pages-stage {
  position: relative;
  margin: 0 auto;
}

.exam-page-wrapper {
  position: relative;
  margin: 0 auto 30px;
  transform-origin: top left;
}

.exam-page {
  position: relative;
  box-sizing: border-box;
  overflow: hidden;
  color: #111;
  background: #fff;
  box-shadow: 0 8px 28px rgba(32, 44, 60, 0.18);
  font-family: 'SimSun', '宋体', serif;
  line-height: 1.55;
}

.paper-content {
  position: absolute;
  box-sizing: border-box;
  overflow: hidden;
}

.exam-header {
  width: 100%;
  margin-bottom: 14px;
  text-align: center;
}

.paper-title {
  font-weight: 700;
  line-height: 1.35;
}

.paper-subtitle {
  margin-top: 9px;
}

.student-info {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px 24px;
  margin-bottom: 12px;
  padding-bottom: 11px;
  border-bottom: 1px solid #222;
}

.score-summary {
  display: grid;
  width: 100%;
  margin-bottom: 18px;
}

.score-row {
  display: contents;
}

.score-row > div {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: center;
  border: 1px solid #111;
  margin: 0 -0.5px -0.5px 0;
}

.question-area {
  column-rule: 1px solid #777;
}

.group-heading {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
}

.group-heading-text {
  flex: 1;
  font-weight: 700;
}

.group-score-box {
  display: grid;
  grid-template-columns: 48px 78px;
  font-size: 0.8em;
}

.group-score-box > div {
  display: grid;
  place-items: center;
  border: 1px solid #111;
}

.question-title {
  display: flex;
  gap: 5px;
  align-items: flex-start;
}

.question-content {
  flex: 1;
  white-space: pre-wrap;
}

.options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 28px;
  padding: 7px 4px 0 24px;
}

.options-single {
  grid-template-columns: 1fr;
}

.answer-space {
  width: 100%;
}

.answer-block {
  margin: 7px 0 4px 22px;
  line-height: 1.55;
}

.answer-label {
  font-weight: 700;
}

.binding-zone {
  position: absolute;
  z-index: 2;
  top: 24px;
  bottom: 24px;
  width: 112px;
  border-right: 1px solid #555;
}

.binding-zone-right {
  right: 18px;
  border-right: 0;
  border-left: 1px solid #555;
}

.binding-zone-left {
  left: 18px;
}

.sealing-band {
  position: absolute;
  left: 14px;
  top: 0;
  bottom: 0;
  width: 50px;
  display: grid;
  place-items: center;
  color: #fff;
  background: #b2b2b2;
}

.sealing-band span {
  transform: rotate(-90deg);
  white-space: nowrap;
}

.staple-line {
  position: absolute;
  left: 63px;
  top: 0;
  bottom: 0;
  border-left: 1px dashed #555;
}

.center-divider {
  position: absolute;
  z-index: 4;
  top: 24px;
  bottom: 24px;
  left: calc(147px + (100% - 176px) / 2);
  border-left: 1px solid #111;
}

.paper-footer {
  position: absolute;
  right: 0;
  bottom: 14px;
  left: 0;
  text-align: center;
  font-size: 10pt;
}

.paper-footer-double {
  left: 147px;
  right: 29px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

@media print {
  .print-hidden {
    display: none !important;
  }
}
</style>
