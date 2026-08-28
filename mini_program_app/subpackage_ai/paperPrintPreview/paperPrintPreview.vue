<template>
  <view class="page">
    <view class="preview-header print-hidden">
      <view class="preview-heading"><view class="eye-mark"><view class="eye-pupil"></view></view><text>真实 Word PDF 预览</text></view>
      <button class="regenerate-button" :disabled="loading" @click="load"><text class="refresh-mark">↻</text>重新生成预览</button>
    </view>

    <view class="document-meta print-hidden">
      <text class="page-count-badge">{{ previewPages.length }} 页</text>
      <text class="validity">有效期至 {{ previewGeneratedAt }}</text>
      <text class="open-hint">新窗口打开（使用浏览器缩放/翻页）</text>
    </view>

    <view class="viewer-toolbar print-hidden">
      <view class="viewer-tool-group"><text class="viewer-icon">☷</text><text class="viewer-icon">•••</text></view>
      <view class="viewer-tool-group viewer-zoom"><text @click="changeZoom(-0.1)">−</text><text @click="changeZoom(0.1)">＋</text><text class="zoom-value">{{ Math.round(zoom * 100) }}%</text></view>
      <view class="viewer-page-number"><input :value="1" disabled /><text>/ {{ previewPages.length }}</text></view>
      <view class="viewer-tool-group viewer-right-tools"><text class="viewer-icon">⌕</text><text class="viewer-icon">•••</text></view>
    </view>

    <view class="export-toolbar print-hidden">
      <button :disabled="exporting" @click="exportPdf(false)">导出纸质试卷 PDF</button>
      <button :disabled="exporting" class="answer-export-button" @click="exportPdf(true)">导出答案 PDF</button>
      <button class="answer-button" @click="showAnswers = !showAnswers">{{ showAnswers ? '隐藏答案' : '显示答案' }}</button>
    </view>

    <view v-if="loading" class="state">正在读取试卷和版式...</view>
    <view v-else-if="error" class="state error-state">
      <text>{{ error }}</text>
      <button @click="load">重新加载</button>
    </view>

    <scroll-view v-else scroll-x scroll-y class="preview-scroll">
      <view class="pages-stage" :style="pagesStageStyle">
        <view v-for="(examPage, pageIndex) in previewPages" :key="pageIndex" class="exam-page-wrapper" :style="pageWrapperStyle">
          <view class="exam-page" :style="paperStyle">
            <view v-if="(sealedA3 && pageIndex === 0) || (!sealedA3 && layout.bindingLine)" :class="['binding-zone', layout.bindingPosition === 'right' ? 'binding-zone-right' : 'binding-zone-left']">
              <view class="sealing-band"><text>请 不 要 在 装 订 线 内 答 题</text></view>
              <view class="staple-line"></view>
              <view v-if="pageIndex === 0" class="candidate-fields"><text v-for="field in ['学校','班级','姓名','学号']" :key="field">{{ field }}：________</text></view>
            </view>
            <view v-if="sealedA3" class="center-divider"></view>
            <view class="paper-content" :style="contentStyle">
              <view class="question-area" :style="questionAreaStyle">
                <template v-if="pageIndex === 0">
                  <view class="exam-header logical-left-header">
                    <view class="paper-title" :style="{fontSize: titleFontSize}">{{ previewTitle }}</view>
                    <view class="paper-subtitle" :style="{fontSize: subtitleFontSize}">考试时间：{{ paper.duration || 0 }}分钟　　满分：{{ totalScore }}分</view>
                  </view>
                  <view v-if="hasStudentInfo && !sealedA3" class="student-info">
                    <text v-if="selectedStudentFields.includes('school')">学校 ____________</text><text v-if="selectedStudentFields.includes('grade')">年级 ____________</text><text v-if="selectedStudentFields.includes('class')">班级 ____________</text><text v-if="selectedStudentFields.includes('name')">姓名 ____________</text><text v-if="selectedStudentFields.includes('studentNo')">学号 ____________</text>
                  </view>
                  <view class="score-summary logical-left-header" :style="scoreSummaryStyle"><view class="score-row"><view class="score-label">大题</view><view v-for="(group,index) in questionGroups" :key="`summary-title-${group.type}`">{{ chineseNumber(index+1) }}</view><view class="score-total">总分</view></view><view class="score-row"><view class="score-label">得分</view><view v-for="group in questionGroups" :key="`summary-score-${group.type}`"></view><view class="score-total"></view></view></view>
                </template>
                <view v-for="segment in examPage.segments" :key="`${segment.group.type}-${segment.part}`" class="question-group">
                  <view v-if="segment.showHeading" class="group-heading">
                    <view class="group-score-box"><view>阅卷人</view><view></view><view>得分</view><view></view></view>
                    <view class="group-heading-text">{{ chineseNumber(segment.groupIndex + 1) }}、{{ segment.group.title }}{{ groupScoreText(segment.group) }}</view>
                  </view>
                  <view v-for="item in segment.items" :key="item.questionId" class="question-item">
                    <view class="question-title"><text class="question-number">{{ item.displayNumber }}.</text><view class="question-content">{{ questionText(item) }}<text class="score">（{{ item.score || 0 }}分）</text></view></view>
                    <view v-if="options(item.question.options).length" :class="['options', {'options-single': longOptions(item)}]"><text v-for="(option,index) in options(item.question.options)" :key="index">{{ optionLabel(index) }}. {{ option }}</text></view>
                    <view v-if="showAnswers" class="answer-block"><view><text class="answer-label">{{ solutionLabel(item) }}</text>{{ item.question.answer || '暂无' }}</view><view v-if="item.question.analysis"><text class="answer-label">解析：</text>{{ item.question.analysis }}</view></view>
                    <view v-else-if="answerSpaceLines(item)" class="answer-space" :style="{height: `${answerSpaceLines(item) * 28}px`}"></view>
                  </view>
                </view>
              </view>
            </view>
            <view v-if="sealedA3" class="paper-footer paper-footer-double"><text>第 {{ pageIndex * 2 + 1 }} 页 共 {{ previewPages.length * 2 }} 页</text><text>第 {{ pageIndex * 2 + 2 }} 页 共 {{ previewPages.length * 2 }} 页</text></view>
            <view v-else class="paper-footer">第 {{ pageIndex + 1 }} 页 共 {{ previewPages.length }} 页</view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { getPaper, getPaperLayout } from '@/api/paper.js'
import { BASE_URL } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'

const TYPE_ORDER = ['填空题', '单选题', '多选题', '判断题', '简答题', '解答题', '计算题', '证明题', '编程题']
const TYPE_TITLES = {
  单选题: '单项选择题',
  多选题: '多项选择题',
  判断题: '判断题',
  填空题: '填空题',
  简答题: '简答题',
  解答题: '解答题',
  计算题: '计算题',
  证明题: '证明题',
  编程题: '编程题'
}

export default {
  components: {},
  data() {
    return {
      paperId: null,
      paper: { questions: [] },
      layout: {},
      loading: true,
      error: '',
      showAnswers: false,
      zoom: 1,
      exporting: false,
      previewGeneratedAt: ''
    }
  },
  computed: {
    sortedQuestions() {
      return [...(this.paper.questions || [])].sort((a, b) => Number(a.questionOrder || 0) - Number(b.questionOrder || 0)).map((item, index) => ({ ...item, displayNumber: index + 1 }))
    },
    questionGroups() {
      const groups = new Map()
      this.sortedQuestions.forEach(item => {
        const type = item.question?.questionType || '其他题型'
        if (!groups.has(type)) groups.set(type, [])
        groups.get(type).push(item)
      })
      return [...groups.entries()]
        .sort(([a], [b]) => this.typeRank(a) - this.typeRank(b))
        .map(([type, items]) => ({
          type,
          title: TYPE_TITLES[type] || type,
          items: items.map((item, index) => ({ ...item, displayNumber: index + 1 })),
          score: items.reduce((sum, item) => sum + Number(item.score || 0), 0)
        }))
    },
    totalScore() {
      const calculated = this.sortedQuestions.reduce((sum, item) => sum + Number(item.score || 0), 0)
      return Number(this.paper.totalScore) || calculated
    },
    formalTitle() {
      const subject = String(this.paper.subject || this.paper.name || '试卷').trim()
      const category = String(this.paper.category || '试题').trim()
      return `《${subject}》${category}（A卷）`
    },
    previewTitle() {
      if (!this.sealedA3) return this.formalTitle
      const subject = String(this.paper.subject || this.paper.name || '试卷').trim()
      const category = String(this.paper.category || '试卷').trim()
      return `${subject}${category}`
    },
    sealedA3() {
      return (this.layout.paperSize || 'A3') === 'A3' && (this.layout.orientation || 'landscape') === 'landscape' && Number(this.layout.columnsCount || 2) === 2
    },
    titleFontSize() { return this.sealedA3 ? '17pt' : `${Math.min(50, Math.max(22, Number(this.layout.titleFontSize || 50)))}pt` },
    subtitleFontSize() { return this.sealedA3 ? '7.5pt' : `${Math.min(30, Math.max(13, Number(this.layout.subtitleFontSize || 24)))}pt` },
    hasStudentInfo() {
      return this.layout.showStudentInfo !== false && this.selectedStudentFields.length > 0
    },
    selectedStudentFields() {
      if (this.layout.studentFields) return String(this.layout.studentFields).split(',').filter(Boolean)
      return ['school', 'grade', 'class', 'name', 'studentNo'].filter(key => this.layout[`show${key === 'studentNo' ? 'StudentNo' : key.charAt(0).toUpperCase() + key.slice(1)}`])
    },
    paperDimensions() {
      const portrait = (this.layout.orientation || 'landscape') !== 'landscape'
      const a3 = (this.layout.paperSize || 'A3') === 'A3'
      const base = a3 ? { width: 1123, height: 1588 } : { width: 794, height: 1123 }
      return portrait ? base : { width: base.height, height: base.width }
    },
    previewPages() {
      const columnFactor = Math.max(1, Number(this.layout.columnsCount || 1))
      const estimateScale = this.sealedA3 ? 0.62 : 1
      const pageCapacity = Math.max(520, this.paperDimensions.height - this.verticalMargins - 90) * columnFactor / estimateScale
      const firstCapacity = Math.max(300, (this.paperDimensions.height - this.verticalMargins - 190)) * columnFactor / estimateScale
      const pages = [{ segments: [], used: 0, capacity: firstCapacity }]
      this.questionGroups.forEach((group, groupIndex) => {
        let segment = null
        group.items.forEach((item, itemIndex) => {
          let page = pages[pages.length - 1]
          const itemHeight = this.estimatedQuestionHeight(item)
          const headingHeight = itemIndex === 0 ? 66 : 0
          if (page.used > 0 && page.used + headingHeight + itemHeight > page.capacity) {
            pages.push({ segments: [], used: 0, capacity: pageCapacity })
            page = pages[pages.length - 1]
            segment = null
          }
          if (!segment || segment.page !== pages.length - 1) {
            segment = { group, groupIndex, part: itemIndex, showHeading: itemIndex === 0, items: [], page: pages.length - 1 }
            page.segments.push(segment)
            page.used += segment.showHeading ? 66 : 0
          }
          segment.items.push(item)
          page.used += itemHeight
        })
      })
      return pages
    },
    verticalMargins() {
      return (Number(this.layout.marginTop || 1.8) + Number(this.layout.marginBottom || 1.8)) * 37.8
    },
    pagesStageStyle() {
      return { width: `${this.paperDimensions.width * this.zoom}px`, minHeight: `${this.paperDimensions.height * this.zoom}px` }
    },
    pageWrapperStyle() {
      return {
        width: `${this.paperDimensions.width}px`,
        height: `${this.paperDimensions.height}px`,
        transform: `scale(${this.zoom})`,
        marginBottom: `${30 + Math.max(0, this.paperDimensions.height * (this.zoom - 1))}px`
      }
    },
    paperStyle() {
      return {
        width: `${this.paperDimensions.width}px`,
        height: `${this.paperDimensions.height}px`,
        fontSize: this.sealedA3 ? '8.2pt' : `${Math.min(28, Math.max(12, Number(this.layout.bodyFontSize || 21)))}pt`
      }
    },
    contentStyle() {
      const cm = 37.8
      if (this.sealedA3) {
        return { top: '32px', bottom: '38px', left: '147px', right: '29px' }
      }
      const binding = this.layout.bindingLine ? 34 : 0
      return {
        top: `${Number(this.layout.marginTop || 1.8) * cm}px`,
        bottom: `${Number(this.layout.marginBottom || 1.8) * cm + 30}px`,
        left: `${Number(this.layout.marginLeft || 1.8) * cm + (this.layout.bindingPosition === 'right' ? 0 : binding)}px`,
        right: `${Number(this.layout.marginRight || 1.8) * cm + (this.layout.bindingPosition === 'right' ? binding : 0)}px`
      }
    },
    questionAreaStyle() {
      return {
        columnCount: Number(this.layout.columnsCount || 2),
        columnGap: `${Number(this.layout.columnGap || 0.75) * 37.8}px`,
        columnFill: 'auto',
        height: '100%'
      }
    },
    scoreSummaryStyle() {
      return { gridTemplateColumns: `repeat(${this.questionGroups.length + 2}, minmax(0, 1fr))` }
    }
  },
  onLoad(query) {
    this.paperId = query.paperId
    if (!this.paperId) {
      this.error = '缺少试卷ID'
      this.loading = false
      return
    }
    this.load()
  },
  onUnload() { this.removePrintStyle() },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const [paperResult, layoutResult] = await Promise.all([
          getPaper(this.paperId),
          getPaperLayout(this.paperId)
        ])
        this.paper = paperResult.data || { questions: [] }
        this.layout = layoutResult.data || {}
        this.previewGeneratedAt = this.formatPreviewTime(new Date())
        this.fitToScreen()
        this.applyPrintStyle()
      } catch (error) {
        this.error = error?.data?.msg || error?.msg || '试卷预览加载失败'
      } finally { this.loading = false }
    },
    fitToScreen() {
      const system = uni.getSystemInfoSync()
      const available = Math.max(280, Number(system.windowWidth || 375) - 32)
      this.zoom = Math.min(1, Math.max(0.3, available / this.paperDimensions.width))
    },
    changeZoom(step) { this.zoom = Math.min(1.5, Math.max(0.3, Number((this.zoom + step).toFixed(2)))) },
    formatPreviewTime(date) {
      const pad = value => String(value).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    },
    groupScoreText(group) {
      const scores = [...new Set(group.items.map(item => Number(item.score || 0)))]
      return scores.length === 1 && group.items.length > 1
        ? `（每小题${scores[0]}分，共${group.score}分）`
        : group.items.length === 1 ? `（${group.score}分）` : `（共${group.items.length}题，共${group.score}分）`
    },
    questionText(item) {
      const type = item.question?.questionType || ''
      let content = String(item.question?.content || '')
      if (type.includes('选择') && !/[（(]\s*[）)]/.test(content)) content += '（    ）'
      if (type.includes('填空') && !content.includes('___')) content += '____________________。'
      return content
    },
    answerSpaceLines(item) {
      if (this.showAnswers) return 0
      const type = item.question?.questionType || ''
      if (type.includes('证明')) return 14
      if (type.includes('解答')) return 11
      if (type.includes('计算') || type.includes('编程')) return 9
      if (type.includes('简答')) return 3
      return 0
    },
    estimatedQuestionHeight(item) {
      const contentLength = String(item.question?.content || '').length
      const optionValues = this.options(item.question?.options)
      const base = 54 + Math.ceil(contentLength / 42) * 26
      const optionHeight = optionValues.length ? (this.longOptions(item) ? optionValues.length : Math.ceil(optionValues.length / 2)) * 30 + 12 : 0
      if (this.showAnswers) {
        const answerLength = String(item.question?.answer || '').length + String(item.question?.analysis || '').length
        return base + optionHeight + 58 + Math.ceil(answerLength / 45) * 24
      }
      return base + optionHeight + this.answerSpaceLines(item) * 28
    },
    longOptions(item) { return this.options(item.question?.options).some(option => String(option).length > 24) },
    solutionLabel(item) {
      const type = item.question?.questionType || ''
      return type.includes('解答') || type.includes('计算') || type.includes('证明') || type.includes('简答') || type.includes('编程') ? '解：' : '答案：'
    },
    exportPdf(answers) {
      if (this.exporting) return
      this.exporting = true
      const query = answers ? '?answers=true' : ''
      const url = `${BASE_URL}/api/papers/${this.paperId}/export/pdf${query}`
      const fileName = this.pdfFileName(answers)
      // #ifdef H5
      this.exportPdfForH5(url, fileName)
      // #endif
      // #ifndef H5
      uni.showLoading({ title: '正在生成PDF...' })
      uni.downloadFile({
        url,
        header: { Authorization: `Bearer ${getToken()}` },
        success: (result) => {
          if (result.statusCode !== 200) {
            uni.showToast({ title: `PDF生成失败：${result.statusCode}`, icon: 'none' })
            return
          }
          uni.openDocument({ filePath: result.tempFilePath, fileType: 'pdf', showMenu: true })
          uni.showToast({ title: 'PDF生成成功', icon: 'success' })
        },
        fail: () => uni.showToast({ title: 'PDF下载失败', icon: 'none' }),
        complete: () => { this.exporting = false; uni.hideLoading() }
      })
      // #endif
    },
    async exportPdfForH5(url, fileName) {
      try {
        if (typeof window !== 'undefined' && typeof window.showSaveFilePicker === 'function') {
          try {
            await this.savePdfWithPicker(url, fileName)
          } catch (error) {
            if (error && error.name === 'AbortError') {
              uni.showToast({ title: '已取消保存', icon: 'none' })
              return
            }
            uni.showLoading({ title: '正在生成PDF...' })
            await this.downloadFileWithLink(url, fileName)
          }
        } else {
          uni.showLoading({ title: '正在生成PDF...' })
          await this.downloadFileWithLink(url, fileName)
        }
        uni.showToast({ title: 'PDF保存成功', icon: 'success' })
      } catch (error) {
        if (error && error.name === 'AbortError') {
          uni.showToast({ title: '已取消保存', icon: 'none' })
          return
        }
        uni.showToast({ title: 'PDF下载失败', icon: 'none' })
      } finally {
        this.exporting = false
        uni.hideLoading()
      }
    },
    async savePdfWithPicker(url, fileName) {
      const fileHandle = await window.showSaveFilePicker({
        suggestedName: fileName,
        types: [{
          description: 'PDF 文件',
          accept: { 'application/pdf': ['.pdf'] }
        }]
      })
      uni.showLoading({ title: '正在生成PDF...' })
      const response = await fetch(url, {
        headers: { Authorization: `Bearer ${getToken()}` }
      })
      if (!response.ok) throw new Error(`PDF生成失败：${response.status}`)
      const blob = await response.blob()
      const writable = await fileHandle.createWritable()
      await writable.write(blob)
      await writable.close()
    },
    downloadFileWithLink(url, fileName) {
      return new Promise((resolve, reject) => {
        uni.downloadFile({
          url,
          header: { Authorization: `Bearer ${getToken()}` },
          success: (result) => {
            if (result.statusCode !== 200) {
              reject(new Error(`PDF生成失败：${result.statusCode}`))
              return
            }
            const link = document.createElement('a')
            link.href = result.tempFilePath
            link.download = fileName
            document.body.appendChild(link)
            link.click()
            document.body.removeChild(link)
            resolve()
          },
          fail: reject
        })
      })
    },
    exportWord(answers) {
      if (this.exporting) return
      this.exporting = true
      const query = answers ? '?answers=true' : ''
      const url = `${BASE_URL}/api/papers/${this.paperId}/export/word${query}`
      const fileName = this.wordFileName(answers)
      // #ifdef H5
      this.exportWordForH5(url, fileName)
      // #endif
      // #ifndef H5
      uni.showLoading({ title: '正在生成Word...' })
      uni.downloadFile({
        url,
        header: { Authorization: `Bearer ${getToken()}` },
        success: (result) => {
          if (result.statusCode !== 200) {
            uni.showToast({ title: `Word生成失败：${result.statusCode}`, icon: 'none' })
            return
          }
          uni.openDocument({ filePath: result.tempFilePath, fileType: 'docx', showMenu: true })
          uni.showToast({ title: 'Word生成成功', icon: 'success' })
        },
        fail: () => uni.showToast({ title: 'Word下载失败', icon: 'none' }),
        complete: () => { this.exporting = false; uni.hideLoading() }
      })
      // #endif
    },
    async exportWordForH5(url, fileName) {
      try {
        if (typeof window !== 'undefined' && typeof window.showSaveFilePicker === 'function') {
          try {
            await this.saveWordWithPicker(url, fileName)
          } catch (error) {
            if (error && error.name === 'AbortError') {
              uni.showToast({ title: '已取消保存', icon: 'none' })
              return
            }
            uni.showLoading({ title: '正在生成Word...' })
            await this.downloadFileWithLink(url, fileName)
          }
        } else {
          uni.showLoading({ title: '正在生成Word...' })
          await this.downloadFileWithLink(url, fileName)
        }
        uni.showToast({ title: 'Word保存成功', icon: 'success' })
      } catch (error) {
        if (error && error.name === 'AbortError') {
          uni.showToast({ title: '已取消保存', icon: 'none' })
          return
        }
        uni.showToast({ title: 'Word下载失败', icon: 'none' })
      } finally {
        this.exporting = false
        uni.hideLoading()
      }
    },
    async saveWordWithPicker(url, fileName) {
      const fileHandle = await window.showSaveFilePicker({
        suggestedName: fileName,
        types: [{
          description: 'Word 文档',
          accept: {
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx']
          }
        }]
      })
      uni.showLoading({ title: '正在生成Word...' })
      const response = await fetch(url, {
        headers: { Authorization: `Bearer ${getToken()}` }
      })
      if (!response.ok) throw new Error(`Word生成失败：${response.status}`)
      const blob = await response.blob()
      const writable = await fileHandle.createWritable()
      await writable.write(blob)
      await writable.close()
    },
    printPaper() {
      // #ifdef H5
      this.applyPrintStyle()
      this.$nextTick(() => window.print())
      // #endif
      // #ifndef H5
      uni.showToast({ title: '请在H5浏览器中使用打印', icon: 'none' })
      // #endif
    },
    pdfFileName(answers) {
      const name = String(this.paper.name || '试卷').replace(/[\\/:*?"<>|\x00-\x1F]/g, '_')
      return `${name}${answers ? '-答案版' : ''}.pdf`
    },
    wordFileName(answers) {
      const name = String(this.paper.name || '试卷').replace(/[\\/:*?"<>|\x00-\x1F]/g, '_')
      return `${name}${answers ? '-答案版' : ''}.docx`
    },
    applyPrintStyle() {
      // #ifdef H5
      this.removePrintStyle()
      const style = document.createElement('style')
      style.id = 'paper-print-page-style'
      style.textContent = `@media print { @page { size: ${this.layout.paperSize || 'A4'} ${this.layout.orientation || 'portrait'}; margin: 0; } }`
      document.head.appendChild(style)
      // #endif
    },
    removePrintStyle() {
      // #ifdef H5
      const style = document.getElementById('paper-print-page-style')
      if (style) style.remove()
      // #endif
    },
    typeRank(type) {
      const index = TYPE_ORDER.indexOf(type)
      return index === -1 ? TYPE_ORDER.length : index
    },
    chineseNumber(index) { return ['一', '二', '三', '四', '五', '六', '七', '八'][index - 1] || index },
    optionLabel(index) { return String.fromCharCode(65 + index) },
    options(value) {
      if (!value) return []
      if (Array.isArray(value)) return value
      try {
        const parsed = typeof value === 'string' ? JSON.parse(value) : value
        return Array.isArray(parsed) ? parsed : Object.values(parsed || {})
      } catch (error) {
        return String(value).split(/\r?\n/).filter(Boolean)
      }
    }
  }
}
</script>

<style scoped lang="scss">
/* Traditional A3 landscape exam sheet: the wrapper owns zoom, the paper keeps its real geometry. */
.exam-page-wrapper { transform-origin: top left; }
.paper-title { line-height: 1.25; }
.binding-zone { width: 32px; }
.page{height:100vh;background:#e8edf3;overflow:hidden}.toolbar{height:84rpx;display:flex;align-items:center;justify-content:space-between;padding:0 24rpx;background:#fff;border-bottom:1rpx solid #dce3ec;box-sizing:border-box}.answer-button{margin:0;padding:0 28rpx;height:56rpx;line-height:56rpx;border-radius:30rpx;background:#4d78e8;color:#fff;font-size:24rpx}.zoom-tools{display:flex;align-items:center;gap:20rpx;color:#3d587b;font-size:24rpx}.zoom-tools text:first-child,.zoom-tools text:last-child{width:48rpx;height:48rpx;line-height:48rpx;text-align:center;border-radius:50%;background:#edf3fc;color:#416fd8}.export-toolbar{display:flex;gap:16rpx;padding:14rpx 24rpx;background:#fff;border-bottom:1rpx solid #dce3ec}.export-toolbar button{flex:1;margin:0;padding:0 8rpx;height:68rpx;line-height:68rpx;border-radius:34rpx;background:#edf3fc;color:#416fd8;font-size:24rpx}.export-toolbar .answer-export-button{background:#4d78e8;color:#fff}.state{padding:160rpx 24rpx;text-align:center;color:#65758d}.error-state text{display:block;margin-bottom:24rpx}.error-state button{width:220rpx;background:#4d78e8;color:#fff;border-radius:32rpx;font-size:25rpx}.preview-scroll{height:calc(100vh - 340rpx);padding:28rpx 20rpx 80rpx;box-sizing:border-box}.pages-stage{position:relative;margin:0 auto}.exam-page-wrapper{position:relative;margin:0 auto 30px}.exam-page{position:relative;box-sizing:border-box;transform-origin:top left;background:#fff;color:#111;box-shadow:0 8px 28px rgba(32,44,60,.18);font-family:'SimSun','宋体',serif;line-height:1.55;overflow:hidden}.paper-content{position:absolute;box-sizing:border-box;overflow:hidden}.exam-header{width:100%;box-sizing:border-box;text-align:center;margin:0 0 14px}.paper-title{display:block;width:100%;box-sizing:border-box;font-weight:700;line-height:1.35;white-space:normal;overflow-wrap:anywhere}.paper-subtitle{margin-top:9px;line-height:1.4}.student-info{display:flex;flex-wrap:wrap;justify-content:center;gap:8px 24px;padding:7px 0 11px;border-bottom:1px solid #222;margin-bottom:12px;font-size:.86em}.student-info text{white-space:nowrap}.score-summary{display:table;width:100%;table-layout:fixed;border-collapse:collapse;margin:0 0 18px;font-size:.86em}.score-row{display:table-row;height:34px}.score-row>view{display:table-cell;text-align:center;vertical-align:middle;border:1px solid #111}.score-label,.score-total{width:13%}.question-area{column-rule:1px solid #777;column-fill:auto}.question-group{break-inside:auto;margin:0 0 14px}.group-heading{display:flex;align-items:stretch;justify-content:space-between;gap:12px;break-inside:avoid;break-after:avoid-column;margin:3px 0 10px}.group-heading-text{flex:1;min-width:0;align-self:center;font-weight:700;font-size:1.05em;line-height:1.45}.group-score-box{display:grid;grid-template-columns:48px 78px;flex:0 0 126px;min-height:38px;font-size:.8em}.group-score-box>view{display:flex;align-items:center;justify-content:center;border:1px solid #111}.group-score-box>view+view{border-left:0}.question-item{break-inside:avoid-column;margin:0 0 13px}.question-title{display:flex;align-items:flex-start;gap:5px;line-height:1.65}.question-number{flex:0 0 auto}.question-content{flex:1;min-width:0;white-space:pre-wrap;overflow-wrap:anywhere}.score{white-space:nowrap;margin-left:4px;font-size:.9em}.options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));column-gap:28px;row-gap:4px;padding:7px 4px 0 24px;line-height:1.5}.options text{min-width:0;overflow-wrap:anywhere}.options-single{grid-template-columns:1fr}.answer-space{width:100%;box-sizing:border-box}.answer-block{margin:7px 0 4px 22px;padding:0;line-height:1.55;color:#111;background:transparent;border:0}.answer-block>view{margin-top:3px}.answer-label{font-weight:700}.binding-zone{position:absolute;z-index:2;top:34px;bottom:34px;width:26px;border-right:1px dashed #444;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;font-size:10pt;color:#333}.binding-zone-left{left:18px}.binding-zone-right{right:18px;border-right:0;border-left:1px dashed #444}.paper-footer{position:absolute;left:0;right:0;bottom:14px;text-align:center;font-size:10pt;color:#222}

/* Standard A3 sealing geometry mirrors the real backend PDF. */
.binding-zone{top:24px;bottom:24px;width:112px;border-right:1px solid #555;display:flex;align-items:center;justify-content:center;font-size:7.2pt;overflow:hidden}
.binding-zone-right{border-right:0;border-left:1px solid #555}
.sealing-band{position:absolute;left:14px;top:0;bottom:0;width:50px;background:#b2b2b2;color:#fff;display:flex;align-items:center;justify-content:center}
.sealing-band text,.candidate-line{white-space:nowrap;transform:rotate(-90deg)}
.staple-line{position:absolute;left:63px;top:0;bottom:0;border-left:1px dashed #555}
.center-divider{position:absolute;z-index:4;top:24px;bottom:24px;left:calc(147px + (100% - 176px) / 2);border-left:1px solid #111;pointer-events:none}
.candidate-fields{position:absolute;left:72px;top:35px;bottom:35px;width:32px;display:flex;flex-direction:column;justify-content:space-between;align-items:center}.candidate-fields text{white-space:nowrap;transform:rotate(-90deg);font-size:7.2pt}
.paper-footer{font-size:7pt}
.paper-footer-double{left:147px;right:29px;display:grid;grid-template-columns:1fr 1fr;column-gap:24px}

/* Reference-style document viewer chrome. The paper itself remains real A3 landscape. */
.preview-header{height:110px;display:flex;align-items:center;justify-content:space-between;padding:0 28px;background:#fff;border-bottom:1px solid #dfe5eb;box-sizing:border-box}.preview-heading{display:flex;align-items:center;gap:16px;font-size:32px;font-weight:700;color:#101828}.eye-mark{width:28px;height:18px;border:3px solid #162338;border-radius:70% 15%;transform:rotate(45deg);display:flex;align-items:center;justify-content:center}.eye-pupil{width:7px;height:7px;border:2px solid #162338;border-radius:50%;transform:rotate(-45deg)}.regenerate-button{margin:0;padding:0 28px;height:68px;line-height:68px;border:1px solid #d8e3ef;border-radius:18px;background:#fff;color:#162338;font-size:24px;font-weight:600;box-shadow:0 2px 5px rgba(16,24,40,.06)}.refresh-mark{font-size:34px;vertical-align:-2px;margin-right:10px}.document-meta{height:88px;display:flex;align-items:center;gap:28px;padding:0 30px;background:#fff;border-bottom:1px solid #d9dee5;box-sizing:border-box;font-size:24px}.page-count-badge{padding:5px 16px;border:2px solid #63a7ff;border-radius:13px;color:#1672e8;background:#eff7ff}.validity{color:#8a8f98}.open-hint{color:#162338}.viewer-toolbar{height:82px;display:flex;align-items:center;justify-content:space-between;padding:0 28px;background:#f8f8f8;border-bottom:1px solid #d6d9de;box-sizing:border-box;color:#222;font-size:28px}.viewer-tool-group{display:flex;align-items:center;gap:30px;min-width:160px}.viewer-icon{letter-spacing:5px}.viewer-zoom{justify-content:center;gap:24px}.viewer-zoom text{cursor:pointer}.zoom-value{font-size:22px;color:#5c6673}.viewer-page-number{display:flex;align-items:center;gap:14px}.viewer-page-number input{width:78px;height:52px;text-align:center;border:1px solid #9ea4ab;border-radius:4px;background:#fff;font-size:22px}.viewer-right-tools{justify-content:flex-end}.page{background:#f3f4f6}.preview-scroll{height:calc(100vh - 430px)}.export-toolbar{padding:12px 24px}.export-toolbar button{border-radius:10px}

.paper-content{width:auto}.score-summary{display:grid;width:100%;box-sizing:border-box;table-layout:auto}.score-row{display:contents}.score-row>view{display:flex;min-width:0;min-height:34px;align-items:center;justify-content:center;box-sizing:border-box;border:1px solid #111;margin:0 -0.5px -0.5px 0}.score-label,.score-total{width:auto}
.question-area>.exam-header,.question-area>.student-info,.question-area>.score-summary{break-inside:avoid-column}
.logical-left-header{max-width:100%;overflow:hidden;box-sizing:border-box}

/* Responsive viewer chrome: keep controls readable on phones while preserving the paper's print scale. */
@media screen and (max-width: 900px) {
  .preview-header { height:auto; min-height:76px; padding:14px 18px; gap:12px; }
  .preview-heading { min-width:0; gap:10px; font-size:clamp(20px, 4vw, 28px); line-height:1.2; }
  .preview-heading > text { white-space:normal; }
  .eye-mark { flex:0 0 auto; transform:scale(.8) rotate(45deg); }
  .regenerate-button { flex:0 0 auto; height:48px; line-height:48px; padding:0 14px; border-radius:12px; font-size:clamp(14px, 2.4vw, 20px); white-space:nowrap; }
  .refresh-mark { font-size:24px; margin-right:4px; }
  .document-meta { min-height:64px; height:auto; flex-wrap:wrap; gap:8px 14px; padding:10px 18px; font-size:clamp(13px, 2.2vw, 18px); line-height:1.35; }
  .page-count-badge { padding:3px 10px; border-width:1px; border-radius:9px; }
  .open-hint { flex-basis:100%; }
  .viewer-toolbar { height:58px; padding:0 16px; font-size:clamp(18px, 3vw, 24px); }
  .viewer-tool-group { min-width:0; gap:16px; }
  .viewer-icon { letter-spacing:2px; }
  .viewer-zoom { gap:12px; }
  .viewer-zoom text { min-width:20px; text-align:center; }
  .zoom-value { font-size:14px; }
  .viewer-page-number { gap:6px; font-size:14px; }
  .viewer-page-number input { width:42px; height:34px; font-size:15px; }
  .export-toolbar { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:8px; padding:10px 16px; }
  .export-toolbar button { min-width:0; height:44px; line-height:44px; padding:0 5px; border-radius:9px; font-size:clamp(12px, 2.2vw, 16px); white-space:nowrap; }
  .preview-scroll { height:calc(100vh - 276px); padding:18px 12px 50px; }
}

@media screen and (max-width: 480px) {
  .preview-header { padding:10px 12px; gap:8px; }
  .preview-heading { font-size:20px; }
  .regenerate-button { height:40px; line-height:40px; padding:0 8px; font-size:13px; }
  .refresh-mark { font-size:20px; }
  .document-meta { padding:8px 12px; gap:6px 10px; font-size:13px; }
  .viewer-toolbar { padding:0 10px; }
  .viewer-tool-group { gap:8px; }
  .viewer-zoom { gap:7px; }
  .viewer-right-tools { display:none; }
  .export-toolbar { gap:6px; padding:8px 10px; }
  .export-toolbar button { height:40px; line-height:40px; font-size:12px; }
  .preview-scroll { height:calc(100vh - 235px); padding:12px 8px 40px; }
}

@media print{
  .print-hidden{display:none!important}.page{height:auto!important;overflow:visible!important;background:#fff!important}.preview-scroll{height:auto!important;overflow:visible!important;padding:0!important}.pages-stage{width:auto!important;margin:0!important}.exam-page-wrapper{width:auto!important;height:auto!important;margin:0!important;transform:none!important;break-after:page}.exam-page{transform:none!important;box-shadow:none!important;margin:0!important}
}
</style>
