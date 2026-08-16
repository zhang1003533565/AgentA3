<template>
  <view class="page">
    <view class="print-hidden"><nav-bar title="纸质试卷预览" :showBack="true" placeholder /></view>

    <view class="toolbar print-hidden">
      <button class="answer-button" @click="showAnswers = !showAnswers">{{ showAnswers ? '隐藏答案' : '显示答案' }}</button>
      <view class="zoom-tools">
        <text @click="changeZoom(-0.1)">－</text>
        <text>{{ Math.round(zoom * 100) }}%</text>
        <text @click="changeZoom(0.1)">＋</text>
      </view>
    </view>

    <view class="export-toolbar print-hidden">
      <button :disabled="exporting" @click="exportPdf(false)">导出学生版PDF</button>
      <button :disabled="exporting" @click="exportPdf(true)">导出答案版PDF</button>
      <button :disabled="exporting" @click="exportWord(false)">导出Word</button>
      <button :disabled="exporting" @click="exportWord(true)">导出Word答案版</button>
      <button :disabled="exporting" class="print-button" @click="printPaper">打印试卷</button>
    </view>

    <view v-if="loading" class="state">正在读取试卷和版式...</view>
    <view v-else-if="error" class="state error-state">
      <text>{{ error }}</text>
      <button @click="load">重新加载</button>
    </view>

    <scroll-view v-else scroll-x scroll-y class="preview-scroll">
      <view class="paper-stage" :style="paperStageStyle">
        <view class="paper-sheet" :style="paperStyle">
          <view v-if="layout.bindingLine" :class="['binding-line', layout.bindingPosition === 'right' ? 'binding-right' : 'binding-left']">
            <text>装</text><text>订</text><text>线</text>
          </view>

          <view class="exam-header">
            <text v-if="layout.showSchool" class="school-name">学校：____________________________</text>
            <text class="paper-title" :style="{ fontSize: `${Number(layout.titleFontSize || 24)}pt` }">{{ paper.name }}</text>
            <text class="paper-subtitle" :style="{ fontSize: `${Number(layout.subtitleFontSize || 18)}pt` }">科目：{{ paper.subject }}　考试时间：{{ paper.duration || 0 }}分钟　总分：{{ totalScore }}分</text>
          </view>

          <view v-if="hasStudentInfo" class="student-info">
            <text v-if="layout.showSchool">学校 ____________</text>
            <text v-if="layout.showGrade">年级 ____________</text>
            <text v-if="layout.showClass">班级 ____________</text>
            <text v-if="layout.showName">姓名 ____________</text>
            <text v-if="layout.showStudentNo">学号 ____________</text>
          </view>

          <view class="question-area" :style="questionAreaStyle">
            <view v-for="(group, groupIndex) in questionGroups" :key="group.type" class="question-group">
              <view class="group-title">{{ chineseNumber(groupIndex + 1) }}、{{ group.title }}（共{{ group.items.length }}题，共{{ group.score }}分）</view>
              <view v-for="item in group.items" :key="item.questionId" class="question-item">
                <view class="question-title">
                  <text>{{ item.questionOrder }}. {{ item.question.content }}</text>
                  <text class="score">（{{ item.score }}分）</text>
                </view>
                <view v-if="options(item.question.options).length" class="options">
                  <text v-for="(option, index) in options(item.question.options)" :key="index">{{ optionLabel(index) }}. {{ option }}</text>
                </view>
                <view v-if="showAnswers" class="answer-block">
                  <text><text class="answer-label">正确答案：</text>{{ item.question.answer || '暂无' }}</text>
                  <text><text class="answer-label">答案解析：</text>{{ item.question.analysis || '暂无' }}</text>
                </view>
              </view>
            </view>
          </view>

          <view class="paper-footer">第 1 页 / 共 1 页</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getPaper, getPaperLayout } from '@/api/paper.js'
import { BASE_URL } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'

const TYPE_ORDER = ['单选题', '多选题', '判断题', '填空题', '简答题', '编程题']
const TYPE_TITLES = {
  单选题: '单项选择题',
  多选题: '多项选择题',
  判断题: '判断题',
  填空题: '填空题',
  简答题: '简答题',
  编程题: '编程题'
}

export default {
  components: { NavBar },
  data() {
    return {
      paperId: null,
      paper: { questions: [] },
      layout: {},
      loading: true,
      error: '',
      showAnswers: false,
      zoom: 1,
      exporting: false
    }
  },
  computed: {
    sortedQuestions() {
      return [...(this.paper.questions || [])].sort((a, b) => Number(a.questionOrder || 0) - Number(b.questionOrder || 0))
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
          items,
          score: items.reduce((sum, item) => sum + Number(item.score || 0), 0)
        }))
    },
    totalScore() {
      const calculated = this.sortedQuestions.reduce((sum, item) => sum + Number(item.score || 0), 0)
      return Number(this.paper.totalScore) || calculated
    },
    hasStudentInfo() {
      return ['showSchool', 'showGrade', 'showClass', 'showName', 'showStudentNo'].some(key => this.layout[key])
    },
    paperDimensions() {
      const portrait = this.layout.orientation !== 'landscape'
      const a3 = this.layout.paperSize === 'A3'
      const base = a3 ? { width: 1123, height: 1588 } : { width: 794, height: 1123 }
      return portrait ? base : { width: base.height, height: base.width }
    },
    paperStageStyle() {
      return { width: `${this.paperDimensions.width * this.zoom}px`, minHeight: `${this.paperDimensions.height * this.zoom}px` }
    },
    paperStyle() {
      const cm = 37.8
      return {
        width: `${this.paperDimensions.width}px`,
        minHeight: `${this.paperDimensions.height}px`,
        paddingTop: `${Number(this.layout.marginTop || 0) * cm}px`,
        paddingBottom: `${Number(this.layout.marginBottom || 0) * cm}px`,
        paddingLeft: `${Number(this.layout.marginLeft || 0) * cm}px`,
        paddingRight: `${Number(this.layout.marginRight || 0) * cm}px`,
        transform: `scale(${this.zoom})`,
        fontSize: `${Number(this.layout.bodyFontSize || 12)}pt`
      }
    },
    questionAreaStyle() {
      return {
        columnCount: Number(this.layout.columnsCount || 1),
        columnGap: `${Number(this.layout.columnGap || 0) * 37.8}px`
      }
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
.page{height:100vh;background:#eaf2fb;overflow:hidden}.toolbar{height:84rpx;display:flex;align-items:center;justify-content:space-between;padding:0 24rpx;background:#fff;border-bottom:1rpx solid #dce6f2;box-sizing:border-box}.answer-button{margin:0;padding:0 28rpx;height:56rpx;line-height:56rpx;border-radius:30rpx;background:#4d78e8;color:#fff;font-size:24rpx}.zoom-tools{display:flex;align-items:center;gap:20rpx;color:#3d587b;font-size:24rpx}.zoom-tools text:first-child,.zoom-tools text:last-child{width:48rpx;height:48rpx;line-height:48rpx;text-align:center;border-radius:50%;background:#edf3fc;color:#416fd8}.export-toolbar{display:flex;flex-wrap:wrap;gap:12rpx;padding:12rpx 24rpx;background:#fff;border-bottom:1rpx solid #dce6f2}.export-toolbar button{flex:1 1 calc(33.333% - 12rpx);margin:0;padding:0 8rpx;height:62rpx;line-height:62rpx;border-radius:30rpx;background:#edf3fc;color:#416fd8;font-size:22rpx}.export-toolbar .print-button{background:#4d78e8;color:#fff}.state{padding:160rpx 24rpx;text-align:center;color:#65758d}.error-state text{display:block;margin-bottom:24rpx}.error-state button{width:220rpx;background:#4d78e8;color:#fff;border-radius:32rpx;font-size:25rpx}.preview-scroll{height:calc(100vh - 340rpx);padding:24rpx;box-sizing:border-box}.paper-stage{position:relative;margin:0 auto 40rpx}.paper-sheet{position:relative;box-sizing:border-box;transform-origin:top left;background:#fff;color:#171717;box-shadow:0 10rpx 36rpx rgba(49,75,113,.18);font-family:'SimSun','宋体',serif;line-height:1.65}.exam-header{text-align:center;border-bottom:2px solid #222;padding-bottom:20px;margin-bottom:18px}.exam-header text{display:block}.school-name{text-align:left;font-size:11pt;margin-bottom:10px}.paper-title{font-weight:700;line-height:1.35}.paper-subtitle{margin-top:10px}.student-info{display:flex;flex-wrap:wrap;gap:12px 28px;padding:12px 0 18px;border-bottom:1px solid #333;margin-bottom:20px}.student-info text{white-space:nowrap}.question-area{column-rule:1px solid #d8d8d8}.question-group{break-inside:auto;margin-bottom:22px}.group-title{break-after:avoid-column;font-weight:700;font-size:1.1em;margin-bottom:12px}.question-item{break-inside:avoid-column;margin-bottom:18px}.question-title{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.question-title>text:first-child{flex:1}.score{white-space:nowrap}.options{padding:8px 0 0 24px}.options text{display:block;margin:3px 0}.answer-block{margin-top:10px;padding:10px 12px;background:#f2f5f8;border-left:3px solid #5577a8}.answer-block>text{display:block}.answer-label{font-weight:700}.binding-line{position:absolute;top:30px;bottom:30px;width:28px;border-left:1px dashed #555;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;font-size:11pt;color:#555}.binding-left{left:18px}.binding-right{right:-10px}.paper-footer{position:absolute;left:0;right:0;bottom:20px;text-align:center;font-size:10pt;color:#555}

@media print{
  .print-hidden{display:none!important}.page{height:auto!important;overflow:visible!important;background:#fff!important}.preview-scroll{height:auto!important;overflow:visible!important;padding:0!important}.paper-stage{width:auto!important;min-height:0!important;margin:0!important}.paper-sheet{transform:none!important;box-shadow:none!important;margin:0!important}
}
</style>
