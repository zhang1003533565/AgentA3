import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  BookOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
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
import SidePanel from '../../components/SidePanel/SidePanel'
import './CampusCourseManage.css'

const { TextArea } = Input
const statusMeta = {
  DRAFT: { label: '草稿', color: 'default' },
  PUBLISHED: { label: '已发布', color: 'green' },
  OFFLINE: { label: '已下架', color: 'orange' },
}
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

  const loadDetail = async (courseId, open = true) => {
    setDetailLoading(true)
    if (open) setDetailOpen(true)
    try {
      const response = await getCampusCourse(courseId)
      setDetail(response.data)
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

  const openChapterForm = (chapter = null) => {
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
    setChapterModalOpen(true)
  }

  const saveChapter = async () => {
    const values = await chapterForm.validateFields()
    setSubmitting(true)
    try {
      if (editingChapter) {
        await updateCampusCourseChapter(detail.id, editingChapter.id, values)
      } else {
        await createCampusCourseChapter(detail.id, values)
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
