import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  BookOutlined,
  CloseOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  FileOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  RobotOutlined,
  SendOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import {
  Button,
  Checkbox,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  message,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
} from 'antd'
import {
  createCampusCourse,
  createCampusCourseChapter,
  createCampusCourseType,
  deleteCampusCourseType,
  deleteCampusCourse,
  deleteCampusCourseChapter,
  getCampusCourse,
  getCampusCourses,
  getCampusCourseTypes,
  linkCampusCourseExam,
  offlineCampusCourse,
  publishCampusCourse,
  unlinkCampusCourseExam,
  updateCampusCourse,
  updateCampusCourseChapter,
} from '../../api/campusCourse'
import { getExamPaperList } from '../../api/examPaper'
import { uploadImage } from '../../api/upload'
import {
  bindChapterAdditionalMaterials,
  bindChapterMaterials,
  bindChapterWordMaterials,
  checkMaterialReference,
  deleteMaterial,
  getChapterAdditionalMaterials,
  getChapterMaterials,
  getChapterWordMaterials,
  getCourseMaterials,
  uploadMaterialBatch,
} from '../../api/campusMaterial'
import { API_BASE_URL } from '../../config/apiBase'
import SidePanel from '../../components/SidePanel/SidePanel'
import request from '../../utils/request'
import './CampusCourseManage.css'

const { TextArea } = Input
const statusMeta = {
  DRAFT: { label: '草稿', color: 'default' },
  PUBLISHED: { label: '已发布', color: 'green' },
  OFFLINE: { label: '已下架', color: 'orange' },
}

const COURSE_TYPE_OPTIONS = [
  { value: '', label: '未设置' },
  { value: 'REQUIRED', label: '必修课' },
  { value: 'ELECTIVE', label: '选修课' },
  { value: 'PUBLIC', label: '公共课' },
  { value: 'LAB', label: '实验课' },
]

/* 内置类型标签名与颜色（仅用于列渲染，课程列表已通过 customCourseTypeNames 返回自定义类型名称） */
const COURSE_TYPE_LABELS = {
  REQUIRED: '必修课',
  ELECTIVE: '选修课',
  PUBLIC: '公共课',
  LAB: '实验课',
}

// Fixed major categories shared with the mini-program course list.
const MAJOR_CATEGORY_OPTIONS = [
  '哲学类', '经济学类', '法学类', '教育学类', '文学类', '历史学类',
  '理学类', '工学类', '农学类', '医学类', '管理学类', '艺术学类', '军事学类', '交叉学科类',
].map(value => ({ value, label: value }))
const BUILTIN_TYPE_COLORS = {
  REQUIRED: 'blue',
  ELECTIVE: 'green',
  PUBLIC: 'purple',
  LAB: 'orange',
}

// 与后端 course-material 白名单保持一致
const MATERIAL_WHITELIST = ['mp4', 'avi', 'pdf', 'ppt', 'pptx', 'doc', 'docx', 'xls', 'xlsx', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'mp3', 'txt']
const VIDEO_EXTENSIONS = new Set(['mp4', 'mov', 'webm', 'avi', 'mkv', 'flv', 'm3u8'])
const WORD_EXTENSIONS = new Set(['doc', 'docx'])
const MAX_FOLDER_BYTES = 2 * 1024 * 1024 * 1024
const UPLOAD_BATCH_SIZE = 8

const materialTypeMeta = {
  VIDEO: { label: '视频', color: 'blue' },
  AUDIO: { label: '音频', color: 'gold' },
  IMAGE: { label: '图片', color: 'green' },
  PDF: { label: 'PDF', color: 'red' },
  PPT: { label: '课件', color: 'purple' },
  DOC: { label: '文档', color: 'geekblue' },
  SHEET: { label: '表格', color: 'cyan' },
  TEXT: { label: '文本', color: 'default' },
  OTHER: { label: '其他', color: 'default' },
}
const materialTypeOptions = [
  { value: 'ALL', label: '全部类型' },
  { value: 'VIDEO', label: '视频' },
  { value: 'AUDIO', label: '音频' },
  { value: 'IMAGE', label: '图片' },
  { value: 'PDF', label: 'PDF' },
  { value: 'PPT', label: '课件' },
  { value: 'DOC', label: '文档' },
  { value: 'SHEET', label: '表格' },
  { value: 'TEXT', label: '文本' },
  { value: 'OTHER', label: '其他' },
]

const fileExt = (name = '') => {
  const dot = name.lastIndexOf('.')
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : ''
}
const extCategory = (ext = '') => {
  const value = String(ext).toLowerCase()
  if (['mp4', 'mov', 'webm', 'avi'].includes(value)) return 'VIDEO'
  if (['mp3', 'wav', 'm4a', 'ogg'].includes(value)) return 'AUDIO'
  if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(value)) return 'IMAGE'
  if (value === 'pdf') return 'PDF'
  if (['ppt', 'pptx'].includes(value)) return 'PPT'
  if (['doc', 'docx'].includes(value)) return 'DOC'
  if (['xls', 'xlsx'].includes(value)) return 'SHEET'
  if (value === 'txt') return 'TEXT'
  return 'OTHER'
}
const formatBytes = (bytes) => {
  const n = Number(bytes) || 0
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}
const resolveFileUrl = (url = '') => (/^https?:\/\//.test(url) ? url : `${API_BASE_URL}${url}`)

function CampusCourseManage() {
  console.log('[DEBUG] CampusCourseManage 组件已挂载')
  const [courses, setCourses] = useState([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [editingCourse, setEditingCourse] = useState(null)
  const [courseModalOpen, setCourseModalOpen] = useState(false)
  const [courseForm] = Form.useForm()
  const [detail, setDetail] = useState(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [editingChapter, setEditingChapter] = useState(null)
  const [chapterModalOpen, setChapterModalOpen] = useState(false)
  const [chapterForm] = Form.useForm()
  const [examModalOpen, setExamModalOpen] = useState(false)
  const [examForm] = Form.useForm()
  const [paperOptions, setPaperOptions] = useState([])
  const [submitting, setSubmitting] = useState(false)
  const [courseTypes, setCourseTypes] = useState([])
  const [typeModalOpen, setTypeModalOpen] = useState(false)
  const [typeForm] = Form.useForm()
  const [typeSubmitting, setTypeSubmitting] = useState(false)
  const [coverUploading, setCoverUploading] = useState(false)
  const [displayImageUploading, setDisplayImageUploading] = useState(false)
  const coverUrl = Form.useWatch('coverUrl', courseForm)
  const displayImageUrl = Form.useWatch('displayImageUrl', courseForm)
  const [materials, setMaterials] = useState([])
  const [materialsLoading, setMaterialsLoading] = useState(false)
  const [materialTypeFilter, setMaterialTypeFilter] = useState('ALL')
  const [uploading, setUploading] = useState(false)
  const [uploadInfo, setUploadInfo] = useState(null)
  const [chapterMaterialIds, setChapterMaterialIds] = useState([])
  const [chapterAdditionalMaterialIds, setChapterAdditionalMaterialIds] = useState([])
  const [chapterWordMaterialIds, setChapterWordMaterialIds] = useState([])
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [aiDrawerVisible, setAiDrawerVisible] = useState(false)
  const [aiInput, setAiInput] = useState('')
  const [aiHistory, setAiHistory] = useState([])
  const [aiSending, setAiSending] = useState(false)
  const [aiGeneratedChapter, setAiGeneratedChapter] = useState(null)
  const [aiGeneratedChapters, setAiGeneratedChapters] = useState([])
  const [aiPreviewActiveKey, setAiPreviewActiveKey] = useState('')
  const [aiGenerateProgress, setAiGenerateProgress] = useState({ current: 0, total: 0, generating: false })
  const [aiSelectedChapterKeys, setAiSelectedChapterKeys] = useState([])
  const folderInputRef = useRef(null)

  const chineseNumberMap = {
    零: 0,
    一: 1,
    二: 2,
    两: 2,
    三: 3,
    四: 4,
    五: 5,
    六: 6,
    七: 7,
    八: 8,
    九: 9,
    十: 10,
  }

  const parseChineseNumber = (value = '') => {
    const normalized = String(value || '').trim()
    if (!normalized) return null
    if (/^\d+$/.test(normalized)) return Number(normalized)

    const clean = normalized.replace(/前|第|章|章节|生成|和|到|至|、|，|\s+/g, '')
    if (!clean) return null

    if (clean === '十') return 10
    if (clean.length === 1 && chineseNumberMap[clean] !== undefined) return chineseNumberMap[clean]

    if (/^十[一二三四五六七八九]$/.test(clean)) {
      return 10 + chineseNumberMap[clean[1]]
    }

    if (/^[一二三四五六七八九]十$/.test(clean)) {
      return chineseNumberMap[clean[0]] * 10
    }

    if (/^[一二三四五六七八九]十[一二三四五六七八九]$/.test(clean)) {
      return chineseNumberMap[clean[0]] * 10 + chineseNumberMap[clean[2]]
    }

    return null
  }

  const extractEstimatedMinutes = (text = '') => {
    if (!text) return 0

    const normalizedText = String(text).replace(/[，、]/g, ' ')
    const patterns = [
      /(?:预习|学习|学习时长|时长|预计|预计学习|时间|时长为|都为|为|等于|是)?\s*([零一二两三四五六七八九十\d]+)\s*分钟/i,
      /(?:分钟)\s*([零一二两三四五六七八九十\d]+)/i,
      /(?:\b|\D)(\d+)\s*分钟/i,
    ]

    for (const pattern of patterns) {
      const match = normalizedText.match(pattern)
      if (!match) continue
      const value = match[1]
      const parsed = parseChineseNumber(value)
      if (Number.isInteger(parsed) && parsed >= 0) {
        return parsed
      }
    }

    return 0
  }

  const extractRequestedChapterRange = (text = '') => {
    if (!text) return [1]

    const normalizedText = String(text).replace(/[，、]/g, ' ')

    const rangePattern = /(?:生成|请)?(?:前|第)?\s*([零一二两三四五六七八九十\d]+)\s*章\s*(?:到|至|-|~)\s*([零一二两三四五六七八九十\d]+)\s*章/i
    const rangeMatch = normalizedText.match(rangePattern)
    if (rangeMatch) {
      const start = parseChineseNumber(rangeMatch[1])
      const end = parseChineseNumber(rangeMatch[2])
      if (Number.isInteger(start) && Number.isInteger(end) && start <= end) {
        return Array.from({ length: end - start + 1 }, (_, index) => start + index)
      }
    }

    const multiPattern = /(?:生成|请)?(?:第)?\s*([零一二两三四五六七八九十\d]+)\s*章\s*(?:和|及|、|,|\+)\s*(?:第)?\s*([零一二两三四五六七八九十\d]+)\s*章/i
    const multiMatch = normalizedText.match(multiPattern)
    if (multiMatch) {
      const numbers = [parseChineseNumber(multiMatch[1]), parseChineseNumber(multiMatch[2])]
      if (numbers.every((value) => Number.isInteger(value))) {
        return [...new Set(numbers.sort((a, b) => a - b))]
      }
    }

    const prefixPattern = /(?:生成|请)?\s*(前|前面)\s*([零一二两三四五六七八九十\d]+)\s*章/i
    const prefixMatch = normalizedText.match(prefixPattern)
    if (prefixMatch) {
      const count = parseChineseNumber(prefixMatch[2])
      if (Number.isInteger(count) && count > 0) {
        return Array.from({ length: count }, (_, index) => index + 1)
      }
    }

    const sectionPattern = /(?:生成|请)?\s*(?:第)?\s*([零一二两三四五六七八九十\d]+)\s*章/i
    const sectionMatch = normalizedText.match(sectionPattern)
    if (sectionMatch) {
      const chapter = parseChineseNumber(sectionMatch[1])
      if (Number.isInteger(chapter) && chapter > 0) {
        return [chapter]
      }
    }

    const firstNumber = normalizedText.match(/\d+|[零一二两三四五六七八九十]+/)
    if (firstNumber) {
      const chapter = parseChineseNumber(firstNumber[0])
      if (Number.isInteger(chapter) && chapter > 0) {
        return [chapter]
      }
    }

    return [1]
  }

  const buildAiGeneratedChapter = (payload = {}, chapterIndex = 1, estimatedMinutes = 0) => {
    const rawSections = Array.isArray(payload?.sections)
      ? payload.sections
      : Array.isArray(payload?.data?.sections)
        ? payload.data.sections
        : []

    const chapterLabel = `第 ${chapterIndex} 章`
    const sections = rawSections.map((section, index) => ({
      title: section?.title || `小节${index + 1}`,
      content: section?.content || '',
    }))
    const content = sections.map((section, index) => `${index + 1}. ${section.title}\n${section.content}`).join('\n\n')

    return {
      id: chapterIndex,
      chapterTitle: chapterLabel,
      title: chapterLabel,
      sections,
      content,
      estimatedMinutes: Number(estimatedMinutes) || 0,
    }
  }

  const handleAiSend = async () => {
    const trimmedValue = aiInput.trim()
    if (!trimmedValue) {
      message.warning('请输入想要生成的章节内容要求')
      return
    }

    const courseId = detail?.id
    if (!courseId) {
      message.warning('请先选择并打开要配置的课程')
      return
    }

    const chapterNumbers = extractRequestedChapterRange(trimmedValue)
    const resolvedChapterNumbers = Array.isArray(chapterNumbers) && chapterNumbers.length
      ? chapterNumbers
      : [1]
    const requestedEstimatedMinutes = extractEstimatedMinutes(trimmedValue)

    setAiSending(true)
    setAiGeneratedChapter(null)
    setAiGeneratedChapters([])
    setAiSelectedChapterKeys([])
    setAiPreviewActiveKey('')
    setAiGenerateProgress({ current: 0, total: resolvedChapterNumbers.length, generating: false })
    setAiHistory((prev) => [...prev, { role: 'user', content: trimmedValue }])
    setAiInput('')

    try {
      for (let i = 0; i < resolvedChapterNumbers.length; i += 1) {
        const targetChapter = resolvedChapterNumbers[i]
        const chapterPrompt = `请生成第 ${targetChapter} 章的内容`

        setAiGenerateProgress({ current: i, total: resolvedChapterNumbers.length, generating: true })
        message.loading({
          content: `正在生成第 ${i + 1}/${resolvedChapterNumbers.length} 章...`,
          key: 'ai-generate-progress',
          duration: 0,
        })

        const response = await request.post(`/api/admin/campus-courses/${courseId}/ai/generate`, {
          prompt: chapterPrompt,
          estimatedMinutes: requestedEstimatedMinutes,
        }, {
          timeout: 300000,
        })

        const chapterData = buildAiGeneratedChapter(response?.data || response || {}, targetChapter, requestedEstimatedMinutes)
        setAiGeneratedChapters((prev) => [...prev, chapterData])
        setAiGeneratedChapter(chapterData)
        setAiSelectedChapterKeys((prev) => (prev.includes(chapterData.id) ? prev : [...prev, chapterData.id]))
        setAiPreviewActiveKey(String(chapterData.id))
        setAiGenerateProgress({ current: i + 1, total: resolvedChapterNumbers.length, generating: i + 1 < resolvedChapterNumbers.length })

        message.success({
          content: `第 ${i + 1}/${resolvedChapterNumbers.length} 章已生成`,
          key: 'ai-generate-progress',
          duration: 1.2,
        })
      }

      setAiGenerateProgress({ current: resolvedChapterNumbers.length, total: resolvedChapterNumbers.length, generating: false })
      setAiHistory((prev) => [...prev, { role: 'assistant', content: `已生成 ${resolvedChapterNumbers.length} 章内容，等待确认导入。` }])
      message.success(`AI 已完成 ${resolvedChapterNumbers.length} 章生成，已在预览中展示`)
    } catch (error) {
      const errorMessage = error?.message || 'AI 生成失败，请稍后重试'
      setAiHistory((prev) => [...prev, { role: 'assistant', content: '生成失败，请稍后重试。' }])
      message.error(errorMessage)
      setAiGenerateProgress((prev) => ({ ...prev, generating: false }))
    } finally {
      setAiSending(false)
    }
  }

  const handleConfirmImportAiChapter = async () => {
    if (!detail?.id) {
      message.warning('请先打开要配置的课程')
      return
    }

    if (!aiGeneratedChapters.length) {
      message.warning('当前没有可导入的章节内容')
      return
    }

    const selectedChapters = aiGeneratedChapters.filter((chapter) => aiSelectedChapterKeys.includes(chapter.id))
    if (!selectedChapters.length) {
      message.warning('请至少选择一个章节进行导入')
      return
    }

    try {
      setSubmitting(true)

      for (const [index, chapter] of selectedChapters.entries()) {
        await createCampusCourseChapter(detail.id, {
          title: chapter.chapterTitle || chapter.title || `AI 生成章节 ${index + 1}`,
          summary: 'AI 自动生成章节',
          content: chapter.content || '',
          required: true,
          estimatedMinutes: Number(chapter.estimatedMinutes || 0),
          sortOrder: (detail?.chapters?.length || 0) + index + 1,
        })
      }

      message.success(`已导入 ${selectedChapters.length} 个 AI 生成章节`)
      setAiGeneratedChapter(null)
      setAiGeneratedChapters([])
      setAiSelectedChapterKeys([])
      setAiPreviewActiveKey('')
      setAiGenerateProgress({ current: 0, total: 0, generating: false })
      await loadDetail(detail.id, false)
      await loadCourses()
    } catch (error) {
      message.error(error?.message || '章节导入失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleConfirmImportAichapter = handleConfirmImportAiChapter

  const loadCourses = useCallback(async () => {
    setLoading(true)
    try {
      const response = await getCampusCourses()
      setCourses(response.data || [])
    } finally {
      setLoading(false)
    }
  }, [])

  const loadCourseTypes = useCallback(async () => {
    try {
      const response = await getCampusCourseTypes()
      const data = response.data || []
      console.log('[DEBUG] loadCourseTypes 返回:', data.length, '条', data.map(t => `${t.typeCode}(${t.typeName})[${t.category}]`))
      setCourseTypes(data)
    } catch (error) {
      message.error(error?.message || '加载课程类型失败')
    }
  }, [])

  useEffect(() => {
    loadCourses()
    loadCourseTypes()
  }, [loadCourses, loadCourseTypes])

  const loadMaterials = useCallback(async (courseId) => {
    if (!courseId) return
    setMaterialsLoading(true)
    try {
      const res = await getCourseMaterials(courseId)
      setMaterials(res.data || [])
    } catch (error) {
      message.error(error?.message || '加载资料池失败')
    } finally {
      setMaterialsLoading(false)
    }
  }, [])

  const loadDetail = async (courseId, open = true) => {
    setDetailLoading(true)
    if (open) setDetailOpen(true)
    try {
      const response = await getCampusCourse(courseId)
      setDetail(response.data)
      await loadMaterials(courseId)
    } finally {
      setDetailLoading(false)
    }
  }

  const filteredCourses = useMemo(() => {
    const text = keyword.trim().toLowerCase()
    if (!text) return courses
    return courses.filter((item) =>
      `${item.name || ''} ${item.bookTitle || ''}`.toLowerCase().includes(text))
  }, [courses, keyword])

  const selectedRows = useMemo(() =>
    courses.filter((c) => selectedRowKeys.includes(c.id)),
  [courses, selectedRowKeys])

  const selectionCount = selectedRowKeys.length

  const selectedStatusUniform = useMemo(() => {
    if (!selectionCount) return null
    const first = selectedRows[0]?.publishStatus
    return first && selectedRows.every((r) => r.publishStatus === first) ? first : null
  }, [selectedRows, selectionCount])

  // 必选类型：单选必填；自定义类型：多选可选
  const builtinTypeOptions = useMemo(() =>
    courseTypes
      .filter((item) => item.category === 'BUILTIN')
      .map((item) => ({ value: item.typeCode, label: item.typeName })),
  [courseTypes])

  const removeCourseType = async (type) => {
    if (MAJOR_CATEGORY_OPTIONS.some(item => item.value === type.typeName)) {
      message.warning('固定专业大类不可删除')
      return
    }
    try {
      await deleteCampusCourseType(type.typeCode)
      message.success('分类已删除')
      await loadCourseTypes()
    } catch (error) {
      message.error(error?.message || '分类删除失败')
    }
  }

  const customTypeOptions = useMemo(() => {
    const fixed = MAJOR_CATEGORY_OPTIONS
    const custom = courseTypes
      .filter((item) => item.category === 'CUSTOM'
        && !MAJOR_CATEGORY_OPTIONS.some(fixed => fixed.value === item.typeName))
      .map((item) => ({
        value: item.typeCode,
        label: (
          <span className="course-type-option">
            <span>{item.typeName}</span>
            {!MAJOR_CATEGORY_OPTIONS.some(fixed => fixed.value === item.typeName) && (
              <button type="button" className="course-type-option-delete"
                onMouseDown={(event) => { event.preventDefault(); event.stopPropagation() }}
                onClick={(event) => { event.stopPropagation(); removeCourseType(item) }}>
                ×
              </button>
            )}
          </span>
        ),
      }))
    return [...fixed, ...custom.filter((item) => !fixed.some((fixedItem) => fixedItem.value === item.value))]
  }, [courseTypes])

  const materialMap = useMemo(() => {
    const map = new Map()
    materials.forEach((item) => map.set(item.id, item))
    return map
  }, [materials])

  const filteredMaterials = useMemo(() => {
    if (materialTypeFilter === 'ALL') return materials
    return materials.filter((item) => extCategory(item.fileType) === materialTypeFilter)
  }, [materials, materialTypeFilter])

  const videoMaterials = useMemo(() =>
    materials.filter((item) => VIDEO_EXTENSIONS.has((item.fileType || '').toLowerCase())),
  [materials])

  const nonVideoMaterials = useMemo(() =>
    materials.filter((item) => !VIDEO_EXTENSIONS.has((item.fileType || '').toLowerCase())),
  [materials])

  const wordMaterials = useMemo(() =>
    materials.filter((item) => WORD_EXTENSIONS.has((item.fileType || '').toLowerCase())),
  [materials])

  const openCourseForm = (course = null) => {
    setEditingCourse(course)
    courseForm.resetFields()
    courseForm.setFieldsValue(course ? {
      ...course,
    } : {
      sortOrder: 0,
      courseType: 'PUBLIC',
      customCourseTypes: [],
    })
    setCourseModalOpen(true)
  }

  const saveCourse = async () => {
    const values = await courseForm.validateFields()
    console.log('[DEBUG] saveCourse values:', JSON.stringify(values, null, 2))
    console.log('[DEBUG] customCourseTypes:', values.customCourseTypes)
    setSubmitting(true)
    try {
      if (editingCourse) {
        await updateCampusCourse(editingCourse.id, values)
        message.success('课程已保存')
      } else {
        await createCampusCourse(values)
        message.success('课程已创建，请继续配置章节')
      }
      setCourseModalOpen(false)
      await loadCourses()
      if (detail?.id === editingCourse?.id) await loadDetail(detail.id, false)
    } catch (error) {
      message.error(error?.message || '课程保存失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const saveCourseType = async () => {
    const values = await typeForm.validateFields()
    setTypeSubmitting(true)
    try {
      await createCampusCourseType(values)
      message.success('课程类型已创建')
      setTypeModalOpen(false)
      await loadCourseTypes()
    } catch (error) {
      message.error(error?.message || '创建课程类型失败')
    } finally {
      setTypeSubmitting(false)
    }
  }


  const uploadCourseCover = async (file) => {
    if (!file.type?.startsWith('image/')) {
      message.warning('请选择图片文件')
      return false
    }
    if (file.size > 10 * 1024 * 1024) {
      message.warning('课程封面不能超过 10MB')
      return false
    }
    setCoverUploading(true)
    try {
      const url = await uploadImage(file)
      courseForm.setFieldValue('coverUrl', url)
      message.success('课程封面上传成功')
    } catch (error) {
      message.error(error?.message || '课程封面上传失败')
    } finally {
      setCoverUploading(false)
    }
    return false
  }

  const uploadCourseDisplayImage = async (file) => {
    if (!file.type?.startsWith('image/')) {
      message.warning('请选择图片文件')
      return false
    }
    if (file.size > 10 * 1024 * 1024) {
      message.warning('App 展示图不能超过 10MB')
      return false
    }
    setDisplayImageUploading(true)
    try {
      const url = await uploadImage(file)
      courseForm.setFieldValue('displayImageUrl', url)
      message.success('App 展示图上传成功')
    } catch (error) {
      message.error(error?.message || 'App 展示图上传失败')
    } finally {
      setDisplayImageUploading(false)
    }
    return false
  }

  const changeStatus = async (course, action) => {
    if (action === 'publish') await publishCampusCourse(course.id)
    else await offlineCampusCourse(course.id)
    message.success(action === 'publish' ? '课程已发布，学生端现在可见' : '课程已下架')
    await loadCourses()
    if (detail?.id === course.id) await loadDetail(course.id, false)
  }

  const batchChangeStatus = async (action) => {
    setSubmitting(true)
    try {
      for (const course of selectedRows) {
        if (action === 'publish') await publishCampusCourse(course.id)
        else await offlineCampusCourse(course.id)
      }
      message.success(action === 'publish'
        ? `已批量发布 ${selectionCount} 门课程`
        : `已批量下架 ${selectionCount} 门课程`)
      setSelectedRowKeys([])
      await loadCourses()
    } finally {
      setSubmitting(false)
    }
  }

  const handleBatchDelete = async () => {
    setSubmitting(true)
    try {
      for (const course of selectedRows) {
        await deleteCampusCourse(course.id)
      }
      message.success(`已删除 ${selectionCount} 门课程`)
      if (detail && selectedRows.some((r) => r.id === detail.id)) {
        setDetailOpen(false)
        setDetail(null)
      }
      setSelectedRowKeys([])
      await loadCourses()
    } finally {
      setSubmitting(false)
    }
  }

  const runBatchUpload = async (files) => {
    setUploading(true)
    setUploadInfo({ done: 0, total: files.length })
    let batchId
    let done = 0
    try {
      for (let i = 0; i < files.length; i += UPLOAD_BATCH_SIZE) {
        const slice = files.slice(i, i + UPLOAD_BATCH_SIZE)
        const result = await uploadMaterialBatch(detail.id, slice, batchId)
        batchId = result?.uploadBatchId || batchId
        done += slice.length
        setUploadInfo({ done, total: files.length })
      }
      message.success(`成功上传 ${done} 个资料`)
    } catch (error) {
      message.error(error?.message || '资料上传失败')
    } finally {
      setUploading(false)
      setUploadInfo(null)
      if (detail?.id) await loadMaterials(detail.id)
    }
  }

  const handleFolderChange = (event) => {
    const picked = Array.from(event.target.files || [])
    event.target.value = ''
    if (!detail?.id || !picked.length) return

    const valid = []
    let skipped = 0
    picked.forEach((file) => {
      const ext = fileExt(file.name)
      if (ext && MATERIAL_WHITELIST.includes(ext)) valid.push(file)
      else skipped += 1
    })
    if (!valid.length) {
      message.warning('所选文件夹内没有支持的文件类型')
      return
    }
    const totalBytes = valid.reduce((sum, file) => sum + (file.size || 0), 0)
    if (totalBytes > MAX_FOLDER_BYTES) {
      message.error(`所选文件合计 ${formatBytes(totalBytes)}，超过 ${formatBytes(MAX_FOLDER_BYTES)} 上限`)
      return
    }
    Modal.confirm({
      title: '确认上传文件夹资料',
      content: (
        <div className="material-upload-confirm">
          <p>共 <strong>{valid.length}</strong> 个有效文件，合计 <strong>{formatBytes(totalBytes)}</strong>。</p>
          {skipped > 0 ? <p className="material-upload-confirm__warn">已自动过滤 {skipped} 个不支持的文件。</p> : null}
          <p>将分批上传（每批 {UPLOAD_BATCH_SIZE} 个），上传期间请勿关闭页面。</p>
        </div>
      ),
      okText: '开始上传',
      cancelText: '取消',
      onOk: () => runBatchUpload(valid),
    })
  }

  const handleDeleteMaterial = async (material) => {
    let check
    try {
      const res = await checkMaterialReference(material.id)
      check = res.data
    } catch (error) {
      message.error(error?.message || '引用检查失败')
      return
    }
    const titles = check?.chapterTitles || []
    Modal.confirm({
      title: `删除资料「${material.fileName}」`,
      content: check?.referenced
        ? (
          <div>
            <p>该资料被以下章节引用，删除后将自动从这些章节移除：</p>
            <p className="material-upload-confirm__warn">{titles.join('、')}</p>
          </div>
        )
        : '该资料未被任何章节引用，确认下架吗？',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await deleteMaterial(material.id, false)
        message.success('资料已删除')
        await loadMaterials(detail.id)
      },
    })
  }

  const openChapterForm = async (chapter = null) => {
    setEditingChapter(chapter)
    chapterForm.resetFields()
    let qaQuestion = ''
    let qaAnswer = ''
    if (chapter?.qaJson) {
      try {
        const parsed = JSON.parse(chapter.qaJson)
        const first = Array.isArray(parsed) ? parsed[0] : parsed
        qaQuestion = first?.question || ''
        qaAnswer = first?.answer || ''
      } catch {
        // 忽略旧格式问答，允许重新填写
      }
    }
    chapterForm.setFieldsValue(chapter ? {
      ...chapter,
      qaQuestion,
      qaAnswer,
      required: chapter.required !== false,
    } : {
      required: true,
      sortOrder: (detail?.chapters?.length || 0) + 1,
      estimatedMinutes: 30,
    })
    if (chapter) {
      try {
        const [videoRes, additionalRes, wordRes] = await Promise.all([
          getChapterMaterials(detail.id, chapter.id),
          getChapterAdditionalMaterials(detail.id, chapter.id),
          getChapterWordMaterials(detail.id, chapter.id),
        ])
        setChapterMaterialIds((videoRes.data || []).map((item) => item.id))
        setChapterAdditionalMaterialIds((additionalRes.data || []).map((item) => item.id))
        setChapterWordMaterialIds((wordRes.data || []).map((item) => item.id))
      } catch {
        setChapterMaterialIds([])
        setChapterAdditionalMaterialIds([])
        setChapterWordMaterialIds([])
      }
    } else {
      setChapterMaterialIds([])
      setChapterAdditionalMaterialIds([])
      setChapterWordMaterialIds([])
    }
    setChapterModalOpen(true)
  }

  const saveChapter = async () => {
    const values = await chapterForm.validateFields()
    const chapterValues = {
      ...values,
      qaJson: values.qaQuestion || values.qaAnswer
        ? JSON.stringify([{ question: values.qaQuestion || '', answer: values.qaAnswer || '' }])
        : '',
    }
    delete chapterValues.qaQuestion
    delete chapterValues.qaAnswer
    setSubmitting(true)
    try {
      let chapterId
      if (editingChapter) {
        await updateCampusCourseChapter(detail.id, editingChapter.id, chapterValues)
        chapterId = editingChapter.id
      } else {
        const res = await createCampusCourseChapter(detail.id, chapterValues)
        chapterId = res.data?.id
      }
      if (chapterId) {
        await Promise.all([
          bindChapterMaterials(detail.id, chapterId, chapterMaterialIds),
          bindChapterAdditionalMaterials(detail.id, chapterId, chapterAdditionalMaterialIds),
          bindChapterWordMaterials(detail.id, chapterId, chapterWordMaterialIds),
        ])
      }
      message.success(editingChapter ? '章节已保存' : '章节已添加')
      setChapterModalOpen(false)
      await loadDetail(detail.id, false)
      await loadCourses()
    } catch (error) {
      message.error(error?.message || error?.msg || '章节保存失败，请检查问答内容后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const removeChapter = async (chapterId) => {
    await deleteCampusCourseChapter(detail.id, chapterId)
    message.success('章节已删除')
    await loadDetail(detail.id, false)
    await loadCourses()
  }

  const openExamForm = async () => {
    const response = await getExamPaperList({ current: 1, size: 100 })
    const records = response.data?.records || response.data?.content || []
    setPaperOptions(records.map((paper) => ({
      value: paper.id,
      label: `${paper.title}${paper.published ? '（已发布）' : '（未发布）'}`,
    })))
    examForm.resetFields()
    examForm.setFieldsValue({ sortOrder: (detail?.exams?.length || 0) + 1 })
    setExamModalOpen(true)
  }

  const saveExam = async () => {
    const values = await examForm.validateFields()
    setSubmitting(true)
    try {
      await linkCampusCourseExam(detail.id, values)
      message.success('考试已关联；试卷发布后学生即可参加')
      setExamModalOpen(false)
      await loadDetail(detail.id, false)
      await loadCourses()
    } finally {
      setSubmitting(false)
    }
  }

  const removeExam = async (linkId) => {
    await unlinkCampusCourseExam(detail.id, linkId)
    message.success('考试关联已移除')
    await loadDetail(detail.id, false)
    await loadCourses()
  }

  const columns = [
    {
      title: '课程与课程书',
      key: 'course',
      width: 360,
      render: (_, record) => (
        <div className="course-name-cell">
          <div className="course-cover-mini">
            {record.coverUrl
              ? <img src={record.coverUrl} alt={`${record.name}课程封面`} />
              : <BookOutlined />}
          </div>
          <div><strong>{record.name}</strong><span>{record.bookTitle}</span></div>
        </div>
      ),
    },
    {
      title: '内容',
      width: 150,
      render: (_, record) => `${record.chapterCount || 0} 章 · ${record.examCount || 0} 场考试`,
    },
    {
      title: '类型',
      dataIndex: 'courseType',
      width: 220,
      render: (type, record) => {
        const codes = record.customCourseTypes || []
        const names = record.customCourseTypeNames || []
        // 自定义类型名称由后端 CourseSummary.customCourseTypeNames 直接返回，不依赖类型字典接口
        const customs = codes.map((code, idx) => (
          <Tag key={code} color="default">{idx < names.length ? names[idx] : code}</Tag>
        ))
        if (!customs.length) return <span>-</span>
        return (
          <Space size={4} wrap>
            {customs}
          </Space>
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'publishStatus',
      width: 90,
      render: (status) => {
        const meta = statusMeta[status] || statusMeta.DRAFT
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
  ]

  const chapterTab = (
    <div>
      <div className="course-tab-toolbar">
        <Typography.Text type="secondary">按顺序组织学生需要阅读的课程内容和资料。</Typography.Text>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openChapterForm()}>添加章节</Button>
          <Button icon={<RobotOutlined />} onClick={() => setAiDrawerVisible(true)}>AI智能体生成内容</Button>
        </Space>
      </div>

      {aiGeneratedChapter && (
        <div style={{ marginBottom: 16, border: '1px solid #f0f0f0', borderRadius: 8, padding: 16, background: '#fafafa' }}>
          <Typography.Title level={5} style={{ marginBottom: 12 }}>{aiGeneratedChapter.chapterTitle}</Typography.Title>
          <List
            size="small"
            dataSource={aiGeneratedChapter.sections || []}
            renderItem={(section, index) => (
              <List.Item>
                <List.Item.Meta
                  avatar={<span className="chapter-index">{index + 1}</span>}
                  title={section.title}
                  description={section.content}
                />
              </List.Item>
            )}
          />
        </div>
      )}

      {detail?.chapters?.length ? (
        <List
          className="course-config-list"
          dataSource={detail.chapters}
          renderItem={(chapter, index) => (
            <List.Item actions={[
              <Button key="edit" type="link" onClick={() => openChapterForm(chapter)}>编辑</Button>,
              <Popconfirm key="delete" title="删除后学生的本章进度也会清除，确定吗？" onConfirm={() => removeChapter(chapter.id)}>
                <Button type="link" danger>删除</Button>
              </Popconfirm>,
            ]}>
              <List.Item.Meta
                avatar={<span className="chapter-index">{index + 1}</span>}
                title={<Space><span>{chapter.title}</span>{chapter.required ? <Tag color="blue">必修</Tag> : <Tag>选修</Tag>}</Space>}
                description={`${chapter.summary || '暂无章节说明'} · ${chapter.estimatedMinutes || 0} 分钟`}
              />
            </List.Item>
          )}
        />
      ) : <Empty description="暂无章节，添加至少一个章节后才能发布课程" />}
    </div>
  )

  const examTab = (
    <div>
      <div className="course-tab-toolbar">
        <Typography.Text type="secondary">复用题库管理中已经生成的试卷，考试范围在这里与课程关联。</Typography.Text>
        <Button type="primary" icon={<PlusOutlined />} onClick={openExamForm}>关联试卷</Button>
      </div>
      {detail?.exams?.length ? (
        <List
          className="course-config-list"
          dataSource={detail.exams}
          renderItem={(exam) => (
            <List.Item actions={[
              <Popconfirm key="delete" title="确定移除该考试关联吗？" onConfirm={() => removeExam(exam.id)}>
                <Button type="link" danger>移除</Button>
              </Popconfirm>,
            ]}>
              <List.Item.Meta
                avatar={<span className="exam-mark">考</span>}
                title={<Space><span>{exam.title}</span><Tag color={exam.published ? 'green' : 'default'}>{exam.published ? '试卷已发布' : '试卷未发布'}</Tag></Space>}
                description={`${exam.chapterScope || '全部章节'} · ${exam.questionCount || 0} 题 · ${exam.durationMinutes || 0} 分钟`}
              />
            </List.Item>
          )}
        />
      ) : <Empty description="暂无关联考试" />}
    </div>
  )

  const materialTab = (
    <div>
      <div className="course-tab-toolbar">
        <Typography.Text type="secondary">上传课程资料到资料池，再在各章节中选择关联。</Typography.Text>
        <Space>
          <Select
            value={materialTypeFilter}
            onChange={setMaterialTypeFilter}
            style={{ width: 120 }}
            options={materialTypeOptions}
          />
          <Button
            type="primary"
            icon={<FolderOpenOutlined />}
            loading={uploading}
            onClick={() => folderInputRef.current?.click()}
          >
            {uploading
              ? (uploadInfo ? `上传中 ${uploadInfo.done}/${uploadInfo.total}` : '上传中')
              : '上传文件夹'}
          </Button>
        </Space>
      </div>
      <input
        ref={folderInputRef}
        type="file"
        multiple
        hidden
        webkitdirectory=""
        directory=""
        onChange={handleFolderChange}
      />
      <Spin spinning={materialsLoading}>
        {filteredMaterials.length ? (
          <List
            className="course-material-list"
            dataSource={filteredMaterials}
            renderItem={(item) => {
              const meta = materialTypeMeta[extCategory(item.fileType)] || materialTypeMeta.OTHER
              return (
                <List.Item actions={[
                  <a key="view" href={resolveFileUrl(item.fileUrl)} target="_blank" rel="noreferrer">查看</a>,
                  <Button key="del" type="link" danger onClick={() => handleDeleteMaterial(item)}>删除</Button>,
                ]}>
                  <List.Item.Meta
                    avatar={<span className="material-type-badge"><FileOutlined /></span>}
                    title={<Space><span>{item.fileName}</span><Tag color={meta.color}>{meta.label}</Tag></Space>}
                    description={`${formatBytes(item.fileSize)} · ${(item.fileType || '').toUpperCase()}`}
                  />
                </List.Item>
              )
            }}
          />
        ) : <Empty description="资料池暂无资料，点击“上传文件夹”添加" />}
      </Spin>
    </div>
  )

  return (
    <div className="campus-course-manage">
      <div className="course-page-heading">
        <div className="course-page-heading__text">
          <span className="course-page-heading__kicker">课程学习</span>
          <h1>校园课程管理</h1>
          <p>当前由管理员担任课程负责人，完成课程书、章节、学习范围和考试配置。</p>
        </div>
        <Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => openCourseForm()}>创建课程</Button>
      </div>

      <div className="course-table-card">
        <div className="course-list-toolbar">
          <div className="course-list-toolbar__left">
            <Input.Search placeholder="搜索课程或课程书" allowClear value={keyword} onChange={(event) => setKeyword(event.target.value)} />
            <Space>
              <Button icon={<EyeOutlined />} disabled={selectionCount !== 1} onClick={() => loadDetail(selectedRows[0].id)}>内容配置</Button>
              <Button icon={<EditOutlined />} disabled={selectionCount !== 1} onClick={() => openCourseForm(selectedRows[0])}>编辑</Button>
              {selectedStatusUniform === 'PUBLISHED' ? (
                <Popconfirm title={`确定下架选中的 ${selectionCount} 门课程吗？`} onConfirm={() => batchChangeStatus('offline')}>
                  <Button disabled={!selectedStatusUniform}>下架</Button>
                </Popconfirm>
              ) : (
                <Button icon={<SendOutlined />} disabled={!selectedStatusUniform} onClick={() => batchChangeStatus('publish')}>发布</Button>
              )}
              <Popconfirm title={`确定删除选中的 ${selectionCount} 门课程及其章节配置吗？`} onConfirm={handleBatchDelete}>
                <Button danger icon={<DeleteOutlined />} disabled={!selectionCount}>删除</Button>
              </Popconfirm>
            </Space>
          </div>
          <span>共 {filteredCourses.length} 门课程</span>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={filteredCourses}
          loading={loading}
          tableLayout="fixed"
          pagination={{ pageSize: 10 }}
          rowClassName={(record) => selectedRowKeys.includes(record.id) ? 'course-row-selected' : ''}
          onRow={(record) => ({
            onClick: () => {
              setSelectedRowKeys((prev) =>
                prev.includes(record.id)
                  ? prev.filter((k) => k !== record.id)
                  : [...prev, record.id],
              )
            },
          })}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
            columnWidth: 28,
            renderCell: (checked) => (
              <span className={`course-row-dot${checked ? ' course-row-dot--active' : ''}`} />
            ),
          }}
        />
      </div>

      <SidePanel
        title={editingCourse ? '编辑课程' : '创建课程'}
        open={courseModalOpen}
        onClose={() => setCourseModalOpen(false)}
        width={720}
        footer={(
          <>
            <Button onClick={() => setCourseModalOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting || coverUploading || displayImageUploading} onClick={() => { console.log('[DEBUG] 保存按钮被点击'); saveCourse(); }}>保存</Button>
          </>
        )}
      >
        <Form form={courseForm} layout="vertical" className="course-form">
          <div className="course-form-grid">
            <Form.Item name="name" label="课程名称" rules={[{ required: true, message: '请输入课程名称' }]}>
              <Input placeholder="例如：Python程序设计" maxLength={120} />
            </Form.Item>
            <Form.Item name="bookTitle" label="课程书名称" rules={[{ required: true, message: '请输入课程书名称' }]}>
              <Input placeholder="例如：《Python程序设计基础》" maxLength={160} />
            </Form.Item>
            <Form.Item name="teacherName" label="课程老师" rules={[{ required: true, message: '请输入课程老师' }]}>
              <Input placeholder="请输入课程老师姓名" maxLength={80} />
            </Form.Item>
            <Form.Item hidden name="level">
              <Select
                placeholder="请选择课程等级"
                options={[
                  { value: '初级', label: '初级' },
                  { value: '中级', label: '中级' },
                  { value: '高级', label: '高级' },
                ]}
              />
            </Form.Item>
            <Form.Item name="sortOrder" label="展示顺序"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
            <Form.Item hidden name="courseType">
              <Select placeholder="请选择必选类型" options={builtinTypeOptions} />
            </Form.Item>
            <Form.Item name="customCourseTypes" label="专业大类" extra="可选，可多选">
              <Select mode="multiple" allowClear placeholder={customTypeOptions.length ? "选择自定义类型（可选）" : "暂无自定义类型，请点+创建"} options={customTypeOptions} disabled={!customTypeOptions.length} />
            </Form.Item>
            <div className="course-type-create-row">
              <Button icon={<PlusOutlined />} onClick={() => {
                typeForm.resetFields()
                setTypeModalOpen(true)
              }}>创建类型</Button>
            </div>
          </div>
          <Form.Item name="coverUrl" hidden><Input /></Form.Item>
          <Form.Item name="displayImageUrl" hidden><Input /></Form.Item>
          <div className="course-image-fields">
            <Form.Item label="课程封面">
              <Upload
                listType="picture-card"
                accept="image/jpeg,image/png,image/webp,image/gif"
                fileList={coverUrl ? [{
                  uid: 'course-cover',
                  name: '课程封面',
                  status: 'done',
                  url: coverUrl,
                }] : []}
                beforeUpload={uploadCourseCover}
                onRemove={() => {
                  courseForm.setFieldValue('coverUrl', '')
                  return true
                }}
                showUploadList={{ showPreviewIcon: true, showRemoveIcon: true }}
                disabled={coverUploading}
                maxCount={1}
              >
                {!coverUrl ? (
                  <div className="course-cover-upload__trigger">
                    <UploadOutlined />
                    <span>{coverUploading ? '上传中' : '上传封面'}</span>
                  </div>
                ) : null}
              </Upload>
              <div className="course-cover-upload__tip">建议使用竖版书封，图片不超过 10MB。</div>
            </Form.Item>
            <Form.Item label="App 展示图">
              <Upload
                className="course-display-upload"
                listType="picture-card"
                accept="image/jpeg,image/png,image/webp,image/gif"
                fileList={displayImageUrl ? [{
                  uid: 'course-display-image',
                  name: 'App 展示图',
                  status: 'done',
                  url: displayImageUrl,
                }] : []}
                beforeUpload={uploadCourseDisplayImage}
                onRemove={() => {
                  courseForm.setFieldValue('displayImageUrl', '')
                  return true
                }}
                showUploadList={{ showPreviewIcon: true, showRemoveIcon: true }}
                disabled={displayImageUploading}
                maxCount={1}
              >
                {!displayImageUrl ? (
                  <div className="course-cover-upload__trigger">
                    <UploadOutlined />
                    <span>{displayImageUploading ? '上传中' : '上传展示图'}</span>
                  </div>
                ) : null}
              </Upload>
              <div className="course-cover-upload__tip">建议使用 16:9 横图，App 课程页优先展示。</div>
            </Form.Item>
          </div>
          <Form.Item name="description" label="课程简介"><TextArea rows={4} maxLength={2000} showCount /></Form.Item>
        </Form>
      </SidePanel>

      <SidePanel
        title={detail ? `${detail.name} · 内容配置` : '课程内容配置'}
        width={820}
        open={detailOpen}
        loading={detailLoading}
        onClose={() => setDetailOpen(false)}
        extra={detail && (
          <Space>
            <Button onClick={() => openCourseForm(detail)}>编辑基本信息</Button>
            {detail.publishStatus === 'PUBLISHED'
              ? <Button onClick={() => changeStatus(detail, 'offline')}>下架</Button>
              : <Button type="primary" onClick={() => changeStatus(detail, 'publish')}>发布课程</Button>}
          </Space>
        )}
      >
        {detail && (
          <>
            <div className="course-detail-summary">
              <Descriptions column={2} size="small">
                <Descriptions.Item label="课程书">{detail.bookTitle}</Descriptions.Item>
                <Descriptions.Item label="负责人">{detail.ownerName}</Descriptions.Item>
              </Descriptions>
              <Progress percent={detail.chapterCount ? 100 : 0} showInfo={false} />
              <Typography.Text type="secondary">已配置 {detail.chapterCount} 个章节、{detail.examCount} 场考试</Typography.Text>
            </div>
            <Tabs items={[
              { key: 'chapters', label: `课程章节（${detail.chapterCount}）`, children: chapterTab },
              { key: 'materials', label: `课程资料（${materials.length}）`, children: materialTab },
              { key: 'exams', label: `课程考试（${detail.examCount}）`, children: examTab },
            ]} />
          </>
        )}
      </SidePanel>

      <Modal
        title={editingChapter ? '编辑章节' : '添加章节'}
        open={chapterModalOpen}
        onOk={saveChapter}
        confirmLoading={submitting}
        onCancel={() => setChapterModalOpen(false)}
        width={760}
        okText="保存章节"
      >
        <Form form={chapterForm} layout="vertical">
          <div className="course-form-grid">
            <Form.Item name="title" label="章节标题" rules={[{ required: true, message: '请输入章节标题' }]}><Input /></Form.Item>
            <Form.Item name="estimatedMinutes" label="预计学习时间（分钟）"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="sortOrder" label="章节顺序"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="required" label="必修章节" valuePropName="checked"><Switch /></Form.Item>
          </div>
          <Form.Item name="summary" label="章节说明"><Input maxLength={1000} /></Form.Item>
          <Form.Item name="content" label="课程正文"><TextArea rows={8} placeholder="录入学生需要阅读的课程内容" /></Form.Item>
          <Form.Item name="qaQuestion" label="填写问题">
            <Input placeholder="请输入本章节问题" maxLength={300} />
          </Form.Item>
          <Form.Item name="qaAnswer" label="填写答案">
            <TextArea rows={4} placeholder="请输入问题答案" maxLength={1000} />
          </Form.Item>
          <div className="chapter-material-block">
            <div className="chapter-material-block__head">
              <Typography.Text strong>附加下载资料</Typography.Text>
              <Typography.Text type="secondary">从资料池选择非视频类型资料（文本/PDF/文档等），可多选</Typography.Text>
            </div>
            <Select
              mode="multiple"
              allowClear
              style={{ width: '100%' }}
              placeholder={nonVideoMaterials.length ? '选择附加资料（非视频）' : '资料池中没有非视频类型资料，请先在"课程资料"中上传'}
              value={chapterAdditionalMaterialIds}
              onChange={setChapterAdditionalMaterialIds}
              optionFilterProp="label"
              options={nonVideoMaterials.map((item) => ({ value: item.id, label: item.fileName }))}
            />
            {chapterAdditionalMaterialIds.length ? (
              <ul className="chapter-material-order">
                {chapterAdditionalMaterialIds.map((id, index) => {
                  const item = materialMap.get(id)
                  return (
                    <li key={id}>
                      <span className="chapter-material-order__idx">{index + 1}</span>
                      <span className="chapter-material-order__name">{item ? item.fileName : `资料#${id}`}</span>
                      <Space size={4}>
                        <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => {
                          setChapterAdditionalMaterialIds((prev) => {
                            const next = [...prev]
                            ;[next[index], next[index - 1]] = [next[index - 1], next[index]]
                            return next
                          })
                        }} />
                        <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={index === chapterAdditionalMaterialIds.length - 1} onClick={() => {
                          setChapterAdditionalMaterialIds((prev) => {
                            const next = [...prev]
                            ;[next[index], next[index + 1]] = [next[index + 1], next[index]]
                            return next
                          })
                        }} />
                        <Button size="small" type="text" danger icon={<CloseOutlined />} onClick={() =>
                          setChapterAdditionalMaterialIds((prev) => prev.filter((item) => item !== id))
                        } />
                      </Space>
                    </li>
                  )
                })}
              </ul>
            ) : null}
          </div>
          <div className="chapter-material-block">
            <div className="chapter-material-block__head">
              <Typography.Text strong>关联文本资料</Typography.Text>
              <Typography.Text type="secondary">从资料池选择 Word 类型资料（doc/docx），可多选</Typography.Text>
            </div>
            <Select
              mode="multiple"
              allowClear
              style={{ width: '100%' }}
              placeholder={wordMaterials.length ? '选择 Word 文本资料' : '资料池中没有 Word 类型资料，请先在"课程资料"中上传'}
              value={chapterWordMaterialIds}
              onChange={setChapterWordMaterialIds}
              optionFilterProp="label"
              options={wordMaterials.map((item) => ({ value: item.id, label: item.fileName }))}
            />
            {chapterWordMaterialIds.length ? (
              <ul className="chapter-material-order">
                {chapterWordMaterialIds.map((id, index) => {
                  const item = materialMap.get(id)
                  return (
                    <li key={id}>
                      <span className="chapter-material-order__idx">{index + 1}</span>
                      <span className="chapter-material-order__name">{item ? item.fileName : `资料#${id}`}</span>
                      <Space size={4}>
                        <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => {
                          setChapterWordMaterialIds((prev) => {
                            const next = [...prev]
                            ;[next[index], next[index - 1]] = [next[index - 1], next[index]]
                            return next
                          })
                        }} />
                        <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={index === chapterWordMaterialIds.length - 1} onClick={() => {
                          setChapterWordMaterialIds((prev) => {
                            const next = [...prev]
                            ;[next[index], next[index + 1]] = [next[index + 1], next[index]]
                            return next
                          })
                        }} />
                        <Button size="small" type="text" danger icon={<CloseOutlined />} onClick={() =>
                          setChapterWordMaterialIds((prev) => prev.filter((item) => item !== id))
                        } />
                      </Space>
                    </li>
                  )
                })}
              </ul>
            ) : null}
          </div>
          <div className="chapter-material-block">
            <div className="chapter-material-block__head">
              <Typography.Text strong>关联视频资料</Typography.Text>
              <Typography.Text type="secondary">从资料池选择一个视频资料（仅允许一个）</Typography.Text>
            </div>
            <Select
              allowClear
              style={{ width: '100%' }}
              placeholder={videoMaterials.length ? '选择视频资料' : '资料池中没有视频类型资料，请先在"课程资料"中上传'}
              value={chapterMaterialIds.length ? chapterMaterialIds[0] : undefined}
              onChange={(value) => setChapterMaterialIds(value ? [value] : [])}
              optionFilterProp="label"
              options={videoMaterials.map((item) => ({ value: item.id, label: item.fileName }))}
            />
          </div>
        </Form>
      </Modal>

      <Modal
        title="关联课程考试"
        open={examModalOpen}
        onOk={saveExam}
        confirmLoading={submitting}
        onCancel={() => setExamModalOpen(false)}
        okText="确认关联"
      >
        <Form form={examForm} layout="vertical">
          <Form.Item name="paperId" label="选择试卷" rules={[{ required: true, message: '请选择试卷' }]}>
            <Select showSearch optionFilterProp="label" options={paperOptions} placeholder="从当前管理员创建的试卷中选择" />
          </Form.Item>
          <Form.Item name="chapterScope" label="考试范围"><Input placeholder="例如：第1—4章" maxLength={300} /></Form.Item>
          <Form.Item name="sortOrder" label="展示顺序"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
        </Form>
      </Modal>

      <Modal
        title="创建自定义课程类型"
        open={typeModalOpen}
        onOk={saveCourseType}
        confirmLoading={typeSubmitting}
        onCancel={() => setTypeModalOpen(false)}
        width={460}
        okText="创建"
      >
        <Form form={typeForm} layout="vertical">
          <Form.Item
            name="typeName"
            label="类型名称"
            extra="创建的是自定义类型，名称不能与已有类型重复，存储代码由系统自动生成"
            rules={[
              { required: true, message: '请输入类型名称' },
              { max: 20, message: '类型名称不能超过 20 个字符' },
            ]}
          >
            <Input placeholder="例如：竞赛培训" maxLength={20} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="AI 智能章节助手"
        placement="right"
        width={450}
        open={aiDrawerVisible}
        onClose={() => setAiDrawerVisible(false)}
        destroyOnClose
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, height: '100%' }}>
          <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 12, paddingRight: 4 }}>
            {aiHistory.length ? (
              aiHistory.map((item, index) => (
                <div
                  key={`${item.role}-${index}`}
                  style={{
                    display: 'flex',
                    justifyContent: item.role === 'user' ? 'flex-end' : 'flex-start',
                  }}
                >
                  <div
                    style={{
                      maxWidth: '80%',
                      padding: '10px 12px',
                      borderRadius: item.role === 'user' ? '12px 12px 0 12px' : '12px 12px 12px 0',
                      background: item.role === 'user' ? '#1677ff' : '#f0f0f0',
                      color: item.role === 'user' ? '#fff' : '#1f1f1f',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                    }}
                  >
                    {item.content}
                  </div>
                </div>
              ))
            ) : (
              <Empty description="描述你的课程目标，我来生成章节提纲和内容" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}

            {(aiGenerateProgress.generating || aiGeneratedChapters.length) && (
              <div
                style={{
                  border: '1px solid #f0f0f0',
                  borderRadius: 12,
                  background: '#fafafa',
                  padding: 12,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 12,
                  position: 'relative',
                }}
              >
                <div>
                  <Typography.Text type="secondary">
                    {aiGenerateProgress.generating
                      ? `正在生成第 ${aiGenerateProgress.current + 1}/${aiGenerateProgress.total} 章...`
                      : `已生成 ${aiGeneratedChapters.length}/${aiGenerateProgress.total || aiGeneratedChapters.length} 章`}
                  </Typography.Text>
                </div>

                {aiGeneratedChapters.length ? (
                  <>
                    <Tabs
                      activeKey={String(aiPreviewActiveKey || aiGeneratedChapters[0]?.id || '')}
                      onChange={(value) => setAiPreviewActiveKey(value)}
                      items={aiGeneratedChapters.map((chapter, index) => ({
                        key: String(chapter.id),
                        label: `${chapter.chapterTitle || `第 ${index + 1} 章`}`,
                        children: (
                          <div>
                            <Typography.Title level={5} style={{ marginBottom: 12 }}>{chapter.chapterTitle || `第 ${index + 1} 章`}</Typography.Title>
                            <List
                              size="small"
                              dataSource={chapter.sections || []}
                              renderItem={(section, sectionIndex) => (
                                <List.Item>
                                  <List.Item.Meta
                                    avatar={<span className="chapter-index">{sectionIndex + 1}</span>}
                                    title={section.title}
                                    description={section.content}
                                  />
                                </List.Item>
                              )}
                            />
                          </div>
                        ),
                      }))}
                    />

                    <div style={{ marginTop: 4 }}>
                      <Typography.Text strong>请选择要导入的章节：</Typography.Text>
                      <Checkbox.Group
                        value={aiSelectedChapterKeys.map((value) => String(value))}
                        onChange={(value) => setAiSelectedChapterKeys(value.map((item) => Number(item) || item))}
                        style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 12 }}
                      >
                        {aiGeneratedChapters.map((chapter, index) => (
                          <Checkbox key={chapter.id} value={String(chapter.id)}>
                            {`${chapter.chapterTitle || `第 ${index + 1} 章`}`}
                          </Checkbox>
                        ))}
                      </Checkbox.Group>
                    </div>
                  </>
                ) : (
                  <div style={{ padding: '24px 0', textAlign: 'center' }}>
                    <Spin tip={aiGenerateProgress.generating ? `正在生成第 ${aiGenerateProgress.current + 1}/${aiGenerateProgress.total} 章...` : '正在生成章节中...'} />
                  </div>
                )}

                {!aiGenerateProgress.generating && aiGeneratedChapters.length > 0 && (
                  <div
                    style={{
                      position: 'sticky',
                      bottom: 0,
                      zIndex: 1,
                      background: '#fafafa',
                      paddingTop: 12,
                      borderTop: '1px solid #f0f0f0',
                      display: 'flex',
                      justifyContent: 'flex-end',
                    }}
                  >
                    <Button
                      type="primary"
                      onClick={handleConfirmImportAiChapter}
                      loading={submitting}
                      disabled={aiGenerateProgress.generating || !aiGeneratedChapters.length || !aiSelectedChapterKeys.length}
                    >
                      确认导入
                    </Button>
                  </div>
                )}
              </div>
            )}
          </div>

          <div style={{ borderTop: '1px solid #f0f0f0', paddingTop: 12, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Input.TextArea
              value={aiInput}
              onChange={(event) => setAiInput(event.target.value)}
              rows={4}
              placeholder="例如：为计算机导论课程生成 3 个章节，分别介绍基础概念、算法与实践案例"
              maxLength={500}
            />
            <Button type="primary" icon={<SendOutlined />} onClick={handleAiSend} block loading={aiSending} disabled={aiSending}>
              {aiSending ? '生成中...' : '发送'}
            </Button>
          </div>
        </div>
      </Drawer>
    </div>
  )
}

export default CampusCourseManage
