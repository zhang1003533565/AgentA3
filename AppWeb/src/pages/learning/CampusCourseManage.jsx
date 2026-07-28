import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  BookOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  PlusOutlined,
  SendOutlined,
} from '@ant-design/icons'
import {
  Button,
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
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
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
import './CampusCourseManage.css'

const { TextArea } = Input
const statusMeta = {
  DRAFT: { label: '草稿', color: 'default' },
  PUBLISHED: { label: '已发布', color: 'green' },
  OFFLINE: { label: '已下架', color: 'orange' },
}
const audienceMeta = {
  ALL: '全部学生',
  CLASS: '指定班级',
  STUDENT: '指定学生',
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
      audienceValues: course.audienceValues || '',
    } : {
      audienceType: 'ALL',
      sortOrder: 0,
      estimatedHours: 32,
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
          <div className="course-cover-mini"><BookOutlined /></div>
          <div><strong>{record.name}</strong><span>{record.bookTitle}</span></div>
        </div>
      ),
    },
    { title: '学期', dataIndex: 'semester', width: 130, render: (value) => value || '未设置' },
    {
      title: '学习范围',
      width: 130,
      render: (_, record) => audienceMeta[record.audienceType] || record.audienceType,
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

      <Modal
        title={editingCourse ? '编辑课程' : '创建课程'}
        open={courseModalOpen}
        onOk={saveCourse}
        confirmLoading={submitting}
        onCancel={() => setCourseModalOpen(false)}
        width={720}
        okText="保存"
      >
        <Form form={courseForm} layout="vertical" className="course-form">
          <div className="course-form-grid">
            <Form.Item name="name" label="课程名称" rules={[{ required: true, message: '请输入课程名称' }]}>
              <Input placeholder="例如：Python程序设计" maxLength={120} />
            </Form.Item>
            <Form.Item name="bookTitle" label="课程书名称" rules={[{ required: true, message: '请输入课程书名称' }]}>
              <Input placeholder="例如：《Python程序设计基础》" maxLength={160} />
            </Form.Item>
            <Form.Item name="semester" label="学期"><Input placeholder="例如：2026-2027-1" /></Form.Item>
            <Form.Item name="estimatedHours" label="预计学时"><InputNumber min={1} max={10000} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="audienceType" label="学习范围" rules={[{ required: true }]}>
              <Select options={[
                { value: 'ALL', label: '全部学生' },
                { value: 'CLASS', label: '指定班级' },
                { value: 'STUDENT', label: '指定学生' },
              ]} />
            </Form.Item>
            <Form.Item name="sortOrder" label="展示顺序"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          </div>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.audienceType !== next.audienceType}>
            {({ getFieldValue }) => getFieldValue('audienceType') === 'ALL' ? null : (
              <Form.Item
                name="audienceValues"
                label={getFieldValue('audienceType') === 'CLASS' ? '班级名称' : '学生账号/学号'}
                rules={[{ required: true, message: '请填写学习范围' }]}
                extra="支持使用逗号或换行分隔多个值"
              >
                <TextArea rows={2} placeholder={getFieldValue('audienceType') === 'CLASS' ? '例如：计231，计232' : '例如：20233090117'} />
              </Form.Item>
            )}
          </Form.Item>
          <Form.Item name="coverUrl" label="封面图片地址"><Input placeholder="可选，填写可访问的图片 URL" /></Form.Item>
          <Form.Item name="description" label="课程简介"><TextArea rows={4} maxLength={2000} showCount /></Form.Item>
        </Form>
      </Modal>

      <Drawer
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
                <Descriptions.Item label="学习范围">{audienceMeta[detail.audienceType]}</Descriptions.Item>
                <Descriptions.Item label="学期">{detail.semester || '未设置'}</Descriptions.Item>
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
      </Drawer>

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
