import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { downloadPaperExport, getPaper, getPaperLayout } from '../api/paper'
import { parseQuestionOptions } from '../utils/paperQuestion'

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
  编程题: '编程题',
}

export function usePaperPrintPreview(paperIdRef) {
  const paper = ref({ questions: [] })
  const layout = ref({})
  const loading = ref(true)
  const error = ref('')
  const showAnswers = ref(false)
  const zoom = ref(1)
  const exporting = ref(false)
  const previewGeneratedAt = ref('')

  function typeRank(type) {
    const index = TYPE_ORDER.indexOf(type)
    return index === -1 ? TYPE_ORDER.length : index
  }

  function chineseNumber(index) {
    return ['一', '二', '三', '四', '五', '六', '七', '八'][index - 1] || index
  }

  function optionLabel(index) {
    return String.fromCharCode(65 + index)
  }

  function options(value) {
    return parseQuestionOptions(value)
  }

  const sortedQuestions = computed(() => (
    [...(paper.value.questions || [])]
      .sort((a, b) => Number(a.questionOrder || 0) - Number(b.questionOrder || 0))
      .map((item, index) => ({ ...item, displayNumber: index + 1 }))
  ))

  const questionGroups = computed(() => {
    const groups = new Map()
    sortedQuestions.value.forEach((item) => {
      const type = item.question?.questionType || '其他题型'
      if (!groups.has(type)) groups.set(type, [])
      groups.get(type).push(item)
    })
    return [...groups.entries()]
      .sort(([a], [b]) => typeRank(a) - typeRank(b))
      .map(([type, items]) => ({
        type,
        title: TYPE_TITLES[type] || type,
        items: items.map((item, index) => ({ ...item, displayNumber: index + 1 })),
        score: items.reduce((sum, item) => sum + Number(item.score || 0), 0),
      }))
  })

  const totalScore = computed(() => {
    const calculated = sortedQuestions.value.reduce((sum, item) => sum + Number(item.score || 0), 0)
    return Number(paper.value.totalScore) || calculated
  })

  const sealedA3 = computed(() => (
    (layout.value.paperSize || 'A3') === 'A3'
    && (layout.value.orientation || 'landscape') === 'landscape'
    && Number(layout.value.columnsCount || 2) === 2
  ))

  const formalTitle = computed(() => {
    const subject = String(paper.value.subject || paper.value.name || '试卷').trim()
    const category = String(paper.value.category || '试题').trim()
    return `《${subject}》${category}（A卷）`
  })

  const previewTitle = computed(() => {
    if (!sealedA3.value) return formalTitle.value
    const subject = String(paper.value.subject || paper.value.name || '试卷').trim()
    const category = String(paper.value.category || '试卷').trim()
    return `${subject}${category}`
  })

  const titleFontSize = computed(() => (
    sealedA3.value ? '17pt' : `${Math.min(50, Math.max(22, Number(layout.value.titleFontSize || 50)))}pt`
  ))

  const subtitleFontSize = computed(() => (
    sealedA3.value ? '7.5pt' : `${Math.min(30, Math.max(13, Number(layout.value.subtitleFontSize || 24)))}pt`
  ))

  const selectedStudentFields = computed(() => {
    if (layout.value.studentFields) return String(layout.value.studentFields).split(',').filter(Boolean)
    return ['school', 'grade', 'class', 'name', 'studentNo'].filter((key) => (
      layout.value[`show${key === 'studentNo' ? 'StudentNo' : key.charAt(0).toUpperCase() + key.slice(1)}`]
    ))
  })

  const hasStudentInfo = computed(() => (
    layout.value.showStudentInfo !== false && selectedStudentFields.value.length > 0
  ))

  const paperDimensions = computed(() => {
    const portrait = (layout.value.orientation || 'landscape') !== 'landscape'
    const a3 = (layout.value.paperSize || 'A3') === 'A3'
    const base = a3 ? { width: 1123, height: 1588 } : { width: 794, height: 1123 }
    return portrait ? base : { width: base.height, height: base.width }
  })

  const verticalMargins = computed(() => (
    (Number(layout.value.marginTop || 1.8) + Number(layout.value.marginBottom || 1.8)) * 37.8
  ))

  function answerSpaceLines(item) {
    if (showAnswers.value) return 0
    const type = item.question?.questionType || ''
    if (type.includes('证明')) return 14
    if (type.includes('解答')) return 11
    if (type.includes('计算') || type.includes('编程')) return 9
    if (type.includes('简答')) return 3
    return 0
  }

  function longOptions(item) {
    return options(item.question?.options).some((option) => String(option).length > 24)
  }

  function estimatedQuestionHeight(item) {
    const contentLength = String(item.question?.content || '').length
    const optionValues = options(item.question?.options)
    const base = 54 + Math.ceil(contentLength / 42) * 26
    const optionHeight = optionValues.length
      ? (longOptions(item) ? optionValues.length : Math.ceil(optionValues.length / 2)) * 30 + 12
      : 0
    if (showAnswers.value) {
      const answerLength = String(item.question?.answer || '').length + String(item.question?.analysis || '').length
      return base + optionHeight + 58 + Math.ceil(answerLength / 45) * 24
    }
    return base + optionHeight + answerSpaceLines(item) * 28
  }

  const previewPages = computed(() => {
    const columnFactor = Math.max(1, Number(layout.value.columnsCount || 1))
    const estimateScale = sealedA3.value ? 0.62 : 1
    const pageCapacity = Math.max(520, paperDimensions.value.height - verticalMargins.value - 90) * columnFactor / estimateScale
    const firstCapacity = Math.max(300, (paperDimensions.value.height - verticalMargins.value - 190)) * columnFactor / estimateScale
    const pages = [{ segments: [], used: 0, capacity: firstCapacity }]
    questionGroups.value.forEach((group, groupIndex) => {
      let segment = null
      group.items.forEach((item, itemIndex) => {
        let page = pages[pages.length - 1]
        const itemHeight = estimatedQuestionHeight(item)
        const headingHeight = itemIndex === 0 ? 66 : 0
        if (page.used > 0 && page.used + headingHeight + itemHeight > page.capacity) {
          pages.push({ segments: [], used: 0, capacity: pageCapacity })
          page = pages[pages.length - 1]
          segment = null
        }
        if (!segment || segment.page !== pages.length - 1) {
          segment = {
            group,
            groupIndex,
            part: itemIndex,
            showHeading: itemIndex === 0,
            items: [],
            page: pages.length - 1,
          }
          page.segments.push(segment)
          page.used += segment.showHeading ? 66 : 0
        }
        segment.items.push(item)
        page.used += itemHeight
      })
    })
    return pages
  })

  const pagesStageStyle = computed(() => ({
    width: `${paperDimensions.value.width * zoom.value}px`,
    minHeight: `${paperDimensions.value.height * zoom.value}px`,
  }))

  const pageWrapperStyle = computed(() => ({
    width: `${paperDimensions.value.width}px`,
    height: `${paperDimensions.value.height}px`,
    transform: `scale(${zoom.value})`,
    marginBottom: `${30 + Math.max(0, paperDimensions.value.height * (zoom.value - 1))}px`,
  }))

  const paperStyle = computed(() => ({
    width: `${paperDimensions.value.width}px`,
    height: `${paperDimensions.value.height}px`,
    fontSize: sealedA3.value ? '8.2pt' : `${Math.min(28, Math.max(12, Number(layout.value.bodyFontSize || 21)))}pt`,
  }))

  const contentStyle = computed(() => {
    const cm = 37.8
    if (sealedA3.value) {
      return { top: '32px', bottom: '38px', left: '147px', right: '29px' }
    }
    const binding = layout.value.bindingLine ? 34 : 0
    return {
      top: `${Number(layout.value.marginTop || 1.8) * cm}px`,
      bottom: `${Number(layout.value.marginBottom || 1.8) * cm + 30}px`,
      left: `${Number(layout.value.marginLeft || 1.8) * cm + (layout.value.bindingPosition === 'right' ? 0 : binding)}px`,
      right: `${Number(layout.value.marginRight || 1.8) * cm + (layout.value.bindingPosition === 'right' ? binding : 0)}px`,
    }
  })

  const questionAreaStyle = computed(() => ({
    columnCount: Number(layout.value.columnsCount || 2),
    columnGap: `${Number(layout.value.columnGap || 0.75) * 37.8}px`,
    columnFill: 'auto',
    height: '100%',
  }))

  const scoreSummaryStyle = computed(() => ({
    gridTemplateColumns: `repeat(${questionGroups.value.length + 2}, minmax(0, 1fr))`,
  }))

  function formatPreviewTime(date) {
    const pad = (value) => String(value).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  }

  function groupScoreText(group) {
    const scores = [...new Set(group.items.map((item) => Number(item.score || 0)))]
    if (scores.length === 1 && group.items.length > 1) {
      return `（每小题${scores[0]}分，共${group.score}分）`
    }
    if (group.items.length === 1) return `（${group.score}分）`
    return `（共${group.items.length}题，共${group.score}分）`
  }

  function questionText(item) {
    const type = item.question?.questionType || ''
    let content = String(item.question?.content || '')
    if (type.includes('选择') && !/[（(]\s*[）)]/.test(content)) content += '（    ）'
    if (type.includes('填空') && !content.includes('___')) content += '____________________。'
    return content
  }

  function solutionLabel(item) {
    const type = item.question?.questionType || ''
    return type.includes('解答') || type.includes('计算') || type.includes('证明') || type.includes('简答') || type.includes('编程')
      ? '解：'
      : '答案：'
  }

  function pdfFileName(answers) {
    const name = String(paper.value.name || '试卷').replace(/[\\/:*?"<>|\x00-\x1F]/g, '_')
    return `${name}${answers ? '-答案版' : ''}.pdf`
  }

  function applyPrintStyle() {
    removePrintStyle()
    const style = document.createElement('style')
    style.id = 'paper-print-page-style'
    style.textContent = `@media print { @page { size: ${layout.value.paperSize || 'A4'} ${layout.value.orientation || 'portrait'}; margin: 0; } }`
    document.head.appendChild(style)
  }

  function removePrintStyle() {
    const style = document.getElementById('paper-print-page-style')
    if (style) style.remove()
  }

  function fitToScreen() {
    const available = Math.max(280, Number(window.innerWidth || 375) - 32)
    zoom.value = Math.min(1, Math.max(0.3, available / paperDimensions.value.width))
  }

  function changeZoom(step) {
    zoom.value = Math.min(1.5, Math.max(0.3, Number((zoom.value + step).toFixed(2))))
  }

  async function load() {
    if (!paperIdRef.value) {
      error.value = '缺少试卷ID'
      loading.value = false
      return
    }
    loading.value = true
    error.value = ''
    try {
      const [paperResult, layoutResult] = await Promise.all([
        getPaper(paperIdRef.value),
        getPaperLayout(paperIdRef.value),
      ])
      paper.value = paperResult || { questions: [] }
      layout.value = layoutResult || {}
      previewGeneratedAt.value = formatPreviewTime(new Date())
      fitToScreen()
      applyPrintStyle()
    } catch (cause) {
      error.value = cause?.data?.msg || cause?.msg || cause?.message || '试卷预览加载失败'
    } finally {
      loading.value = false
    }
  }

  async function exportPdf(answers = false) {
    if (exporting.value || !paperIdRef.value) return
    exporting.value = true
    try {
      await downloadPaperExport(paperIdRef.value, 'pdf', answers, pdfFileName(answers))
    } catch (cause) {
      error.value = cause?.message || 'PDF下载失败'
    } finally {
      exporting.value = false
    }
  }

  watch(paperIdRef, () => {
    if (paperIdRef.value) load()
  }, { immediate: true })

  onBeforeUnmount(removePrintStyle)

  return {
    paper,
    layout,
    loading,
    error,
    showAnswers,
    zoom,
    exporting,
    previewGeneratedAt,
    sortedQuestions,
    questionGroups,
    totalScore,
    formalTitle,
    previewTitle,
    sealedA3,
    titleFontSize,
    subtitleFontSize,
    hasStudentInfo,
    selectedStudentFields,
    paperDimensions,
    previewPages,
    verticalMargins,
    pagesStageStyle,
    pageWrapperStyle,
    paperStyle,
    contentStyle,
    questionAreaStyle,
    scoreSummaryStyle,
    load,
    fitToScreen,
    changeZoom,
    formatPreviewTime,
    groupScoreText,
    questionText,
    answerSpaceLines,
    estimatedQuestionHeight,
    longOptions,
    solutionLabel,
    exportPdf,
    typeRank,
    chineseNumber,
    optionLabel,
    options,
  }
}
