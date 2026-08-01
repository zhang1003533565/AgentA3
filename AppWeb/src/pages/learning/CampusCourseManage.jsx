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
  SendOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import {
  Button,
  Descriptions,
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
  deleteCampusCourse,
  deleteCampusCourseChapter,
  getCampusCourse,
  getCampusCourses,
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
  bindChapterMaterials,
  checkMaterialReference,
  deleteMaterial,
  getChapterMaterials,
  getCourseMaterials,
  uploadMaterialBatch,
} from '../../api/campusMaterial'
import { API_BASE_URL } from '../../config/apiBase'
import SidePanel from '../../components/SidePanel/SidePanel'
import './CampusCourseManage.css'

const { TextArea } = Input
const statusMeta = {
  DRAFT: { label: '草稿', color: 'default' },
  PUBLISHED: { label: '已发布', color: 'green' },
  OFFLINE: { label: '已下架', color: 'orange' },
}

// 与后端 course-material 白名单保持一致
const MATERIAL_WHITELIST = ['mp4', 'avi', 'pdf', 'ppt', 'pptx', 'doc', 'docx', 'xls', 'xlsx', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'mp3', 'txt']
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
  if (['mp4', 'avi'].includes(value)) return 'VIDEO'
  if (value === 'mp3') return 'AUDIO'
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
  const folderInputRef = useRef(null)

  const loadCourses = useCallback(async () => {
    setLoading(true)
    try {
      const response = await getCampusCourses()
      setCourses(response.data || [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadCourses()
  }, [loadCourses])

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

  const materialMap = useMemo(() => {
    const map = new Map()
    materials.forEach((item) => map.set(item.id, item))
    return map
  }, [materials])

  const filteredMaterials = useMemo(() => {
    if (materialTypeFilter === 'ALL') return materials
    return materials.filter((item) => extCategory(item.fileType) === materialTypeFilter)
  }, [materials, materialTypeFilter])

  const openCourseForm = (course = null) => {
    setEditingCourse(course)
    courseForm.resetFields()
    courseForm.setFieldsValue(course ? {
      ...course,
    } : {
      sortOrder: 0,
    })
    setCourseModalOpen(true)
  }

  const saveCourse = async () => {
    const values = await courseForm.validateFields()
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
    } finally {
      setSubmitting(false)
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

  const removeCourse = async (id) => {
    await deleteCampusCourse(id)
    message.success('课程已删除')
    if (detail?.id === id) {
      setDetailOpen(false)
      setDetail(null)
    }
    await loadCourses()
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

  const moveChapterMaterial = (index, dir) => {
    setChapterMaterialIds((prev) => {
      const target = index + dir
      if (target < 0 || target >= prev.length) return prev
      const next = [...prev]
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }
  const removeChapterMaterial = (id) =>
    setChapterMaterialIds((prev) => prev.filter((item) => item !== id))

  const openChapterForm = async (chapter = null) => {
    setEditingChapter(chapter)
    chapterForm.resetFields()
    chapterForm.setFieldsValue(chapter ? {
      ...chapter,
      required: chapter.required !== false,
    } : {
      required: true,
      sortOrder: (detail?.chapters?.length || 0) + 1,
      estimatedMinutes: 30,
      resourceType: 'TEXT',
    })
    if (chapter) {
      try {
        const res = await getChapterMaterials(detail.id, chapter.id)
        setChapterMaterialIds((res.data || []).map((item) => item.id))
      } catch {
        setChapterMaterialIds([])
      }
    } else {
      setChapterMaterialIds([])
    }
    setChapterModalOpen(true)
  }

  const saveChapter = async () => {
    const values = await chapterForm.validateFields()
    setSubmitting(true)
    try {
      let chapterId
      if (editingChapter) {
        await updateCampusCourseChapter(detail.id, editingChapter.id, values)
        chapterId = editingChapter.id
      } else {
        const res = await createCampusCourseChapter(detail.id, values)
        chapterId = res.data?.id
      }
      if (chapterId) {
        await bindChapterMaterials(detail.id, chapterId, chapterMaterialIds)
      }
      message.success(editingChapter ? '章节已保存' : '章节已添加')
      setChapterModalOpen(false)
      await loadDetail(detail.id, false)
      await loadCourses()
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
      title: '状态',
      dataIndex: 'publishStatus',
      width: 100,
      render: (status) => {
        const meta = statusMeta[status] || statusMeta.DRAFT
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '操作',
      width: 300,
      render: (_, record) => (
        <Space wrap>
          <Button type="link" icon={<EyeOutlined />} onClick={() => loadDetail(record.id)}>内容配置</Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => openCourseForm(record)}>编辑</Button>
          {record.publishStatus === 'PUBLISHED' ? (
            <Popconfirm title="下架后学生将无法进入课程，确定继续吗？" onConfirm={() => changeStatus(record, 'offline')}>
              <Button type="link">下架</Button>
            </Popconfirm>
          ) : (
            <Button type="link" icon={<SendOutlined />} onClick={() => changeStatus(record, 'publish')}>发布</Button>
          )}
          <Popconfirm title="确定删除该课程及其章节配置吗？" onConfirm={() => removeCourse(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const chapterTab = (
    <div>
      <div className="course-tab-toolbar">
        <Typography.Text type="secondary">按顺序组织学生需要阅读的课程内容和资料。</Typography.Text>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openChapterForm()}>添加章节</Button>
      </div>
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
        <div>
          <span>课程学习</span>
          <h1>校园课程管理</h1>
          <p>当前由管理员担任课程负责人，完成课程书、章节、学习范围和考试配置。</p>
        </div>
        <Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => openCourseForm()}>创建课程</Button>
      </div>

      <div className="course-table-card">
        <div className="course-list-toolbar">
          <Input.Search placeholder="搜索课程或课程书" allowClear value={keyword} onChange={(event) => setKeyword(event.target.value)} />
          <span>共 {filteredCourses.length} 门课程</span>
        </div>
        <Table rowKey="id" columns={columns} dataSource={filteredCourses} loading={loading} pagination={{ pageSize: 10 }} />
      </div>

      <SidePanel
        title={editingCourse ? '编辑课程' : '创建课程'}
        open={courseModalOpen}
        onClose={() => setCourseModalOpen(false)}
        width={720}
        footer={(
          <>
            <Button onClick={() => setCourseModalOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting || coverUploading || displayImageUploading} onClick={saveCourse}>保存</Button>
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
            <Form.Item name="sortOrder" label="展示顺序"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
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
          <div className="course-form-grid">
            <Form.Item name="resourceType" label="附加资料类型">
              <Select allowClear options={[
                { value: 'TEXT', label: '正文' },
                { value: 'PDF', label: 'PDF' },
                { value: 'PPT', label: 'PPT课件' },
                { value: 'VIDEO', label: '视频' },
                { value: 'LINK', label: '外部链接' },
              ]} />
            </Form.Item>
            <Form.Item name="resourceUrl" label="附加资料地址"><Input placeholder="可选，填写资料 URL" /></Form.Item>
          </div>
          <div className="chapter-material-block">
            <div className="chapter-material-block__head">
              <Typography.Text strong>关联资料池资料</Typography.Text>
              <Typography.Text type="secondary">从资料池选择，并调整学习顺序</Typography.Text>
            </div>
            <Select
              mode="multiple"
              allowClear
              style={{ width: '100%' }}
              placeholder={materials.length ? '从资料池选择资料' : '资料池为空，请先在“课程资料”中上传'}
              value={chapterMaterialIds}
              onChange={setChapterMaterialIds}
              optionFilterProp="label"
              options={materials.map((item) => ({ value: item.id, label: item.fileName }))}
            />
            {chapterMaterialIds.length ? (
              <ul className="chapter-material-order">
                {chapterMaterialIds.map((id, index) => {
                  const item = materialMap.get(id)
                  return (
                    <li key={id}>
                      <span className="chapter-material-order__idx">{index + 1}</span>
                      <span className="chapter-material-order__name">{item ? item.fileName : `资料#${id}`}</span>
                      <Space size={4}>
                        <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => moveChapterMaterial(index, -1)} />
                        <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={index === chapterMaterialIds.length - 1} onClick={() => moveChapterMaterial(index, 1)} />
                        <Button size="small" type="text" danger icon={<CloseOutlined />} onClick={() => removeChapterMaterial(id)} />
                      </Space>
                    </li>
                  )
                })}
              </ul>
            ) : null}
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
    </div>
  )
}

export default CampusCourseManage
