import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Form, Input, InputNumber, Select, Space, Upload, message } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { createActivity, getActivityDetail, updateActivity } from '../../../api/activity'
import { getUploadUrl } from '../../../api/upload'
import { getCategoryList } from '../../../api/category'
import '../ActivityManage/ActivityManage.css'
import { toBackendDateTime, toDateTimeInput } from '../activityHelpers'
import ActivityAiDrawer from './ActivityAiDrawer'

const { TextArea } = Input
const MAX_UPLOAD_BYTES = 4.5 * 1024 * 1024
const MAX_IMAGE_EDGE = 1600

const parseUploadResponse = (response) => {
  if (!response) return null
  if (typeof response === 'string') {
    try {
      return JSON.parse(response)
    } catch {
      return null
    }
  }
  return response
}

const readFileAsDataUrl = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })

const loadImageElement = (src) =>
  new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = src
  })

const canvasToBlob = (canvas, type, quality) =>
  new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob)
        return
      }
      reject(new Error('图片压缩失败'))
    }, type, quality)
  })

const compressImageFile = async (file) => {
  if (!(file instanceof File)) return file
  if (file.size <= MAX_UPLOAD_BYTES) return file

  const lowerName = (file.name || '').toLowerCase()
  const isGif = lowerName.endsWith('.gif') || file.type === 'image/gif'
  if (isGif) {
    throw new Error('GIF 图片过大，请先压缩后再上传')
  }

  const dataUrl = await readFileAsDataUrl(file)
  const image = await loadImageElement(dataUrl)
  const ratio = Math.min(1, MAX_IMAGE_EDGE / Math.max(image.width, image.height))
  const width = Math.max(1, Math.round(image.width * ratio))
  const height = Math.max(1, Math.round(image.height * ratio))
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  context.drawImage(image, 0, 0, width, height)

  const outputType = file.type === 'image/png' ? 'image/png' : 'image/jpeg'
  const qualitySteps = outputType === 'image/png' ? [0.92] : [0.9, 0.82, 0.74, 0.66, 0.58, 0.5]

  let compressedBlob = null
  for (const quality of qualitySteps) {
    const blob = await canvasToBlob(canvas, outputType, quality)
    compressedBlob = blob
    if (blob.size <= MAX_UPLOAD_BYTES) break
  }

  if (!compressedBlob) {
    throw new Error('图片压缩失败')
  }

  const extension = outputType === 'image/png' ? '.png' : '.jpg'
  const filename = lowerName.replace(/\.[^.]+$/, '') || 'upload-image'
  return new File([compressedBlob], `${filename}${extension}`, { type: outputType })
}

const uploadImageFile = async ({ file, onSuccess, onError }) => {
  try {
    const compressedFile = await compressImageFile(file)
    const formData = new FormData()
    formData.append('file', compressedFile)
    const response = await fetch(getUploadUrl(), {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
      },
      body: formData,
    })
    const result = await response.json()
    if (!response.ok || result?.code !== 200) {
      throw new Error(result?.msg || '上传失败')
    }
    onSuccess(result)
  } catch (error) {
    onError(error)
  }
}

function ActivityEditor() {
  const navigate = useNavigate()
  const { id } = useParams()
  const isEdit = Boolean(id)
  const [form] = Form.useForm()
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [aiDrawerOpen, setAiDrawerOpen] = useState(false)
  const coverImageValue = Form.useWatch('coverImage', form)
  const originalRef = useRef(null)

  useEffect(() => {
    const run = async () => {
      setLoading(true)
      try {
        const categoryRes = await getCategoryList()
        setCategories(categoryRes?.data || [])
        if (isEdit) {
          const detailRes = await getActivityDetail(id)
          const record = detailRes?.data
          originalRef.current = record
          form.setFieldsValue({
            title: record?.title,
            organizerName: record?.organizerName,
            categoryId: record?.categoryId,
            coverImage: record?.coverImage,
            location: record?.location,
            maxPeople: record?.maxPeople,
            content: record?.content,
            startTime: toDateTimeInput(record?.startTime),
            endTime: toDateTimeInput(record?.endTime),
            signupEndTime: toDateTimeInput(record?.signupEndTime),
          })
        }
      } finally {
        setLoading(false)
      }
    }

    run()
  }, [form, id, isEdit])

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      const payload = {
        title: values.title,
        organizerName: values.organizerName,
        categoryId: values.categoryId,
        coverImage: values.coverImage,
        location: values.location,
        maxPeople: values.maxPeople,
        content: values.content,
        startTime: toBackendDateTime(values.startTime),
        endTime: toBackendDateTime(values.endTime),
        signupEndTime: toBackendDateTime(values.signupEndTime),
        ...(isEdit
          ? {
              images: originalRef.current?.images,
              requiresAudit: originalRef.current?.requiresAudit,
              cancelRequiresAudit: originalRef.current?.cancelRequiresAudit,
              score: originalRef.current?.score,
              creditConfig: originalRef.current?.creditConfig,
              signupStartTime: originalRef.current?.signupStartTime,
              signInStartTime: originalRef.current?.signInStartTime,
              signInEndTime: originalRef.current?.signInEndTime,
              contactName: originalRef.current?.contactName,
              contactPhone: originalRef.current?.contactPhone,
            }
          : {}),
      }

      if (isEdit) {
        await updateActivity(id, payload)
        message.success('活动已更新')
      } else {
        await createActivity(payload)
        message.success('活动已创建')
      }
      navigate('/activity/manage')
    } catch (error) {
      // 后端接口错误已由全局请求拦截器统一提示，避免重复弹窗；
      // 表单校验错误（errorFields）会由 antd 自动在表单项上内联展示
      console.warn('活动保存失败:', error)
    } finally {
      setSubmitting(false)
    }
  }

  const handleCoverUploadChange = ({ file }) => {
    if (file.status === 'uploading') {
      setUploading(true)
      return
    }
    if (file.status === 'done') {
      setUploading(false)
      const response = parseUploadResponse(file.response)
      const url = response?.data?.url
      if (response?.code === 200 && url) {
        form.setFieldsValue({ coverImage: url })
        message.success('图片上传成功')
      } else {
        message.error(response?.msg || '上传返回内容异常')
      }
      return
    }
    if (file.status === 'error') {
      setUploading(false)
      message.error(file.error?.message || '图片上传失败')
    }
  }

  const handleAiFill = (activity) => {
    if (!activity || typeof activity !== 'object') return
    const timeFields = ['startTime', 'endTime', 'signupEndTime']
    const values = {}
    Object.entries(activity).forEach(([key, value]) => {
      if (value === null || value === undefined || value === '') return
      values[key] = timeFields.includes(key) ? toDateTimeInput(value) : value
    })
    if (Object.keys(values).length > 0) {
      form.setFieldsValue(values)
      message.success('AI 数据已填入，请确认后保存')
    } else {
      message.warning('暂无可填入的活动字段')
    }
  }

  return (
    <div className="activity-manage-container">
      <main className="manage-main">
        <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{isEdit ? '编辑活动' : '创建活动'}</h2>
          {!isEdit && (
            <Button type="primary" onClick={() => setAiDrawerOpen(true)}>
              🤖 AI 辅助创建活动
            </Button>
          )}
        </div>

        <div className="search-bar" style={{ display: 'block' }}>
          <Form form={form} layout="vertical" disabled={loading}>
            <Form.Item name="title" label="活动标题" rules={[{ required: true, message: '请输入活动标题' }]}>
              <Input />
            </Form.Item>

            <Form.Item name="organizerName" label="主办方">
              <Input placeholder="留空则默认使用当前账号姓名" maxLength={50} />
            </Form.Item>

            <Form.Item name="coverImage" label="封面图片">
              <Input placeholder="上传后会自动填入图片地址" readOnly />
            </Form.Item>

            <Upload
              name="file"
              showUploadList={false}
              customRequest={uploadImageFile}
              onChange={handleCoverUploadChange}
            >
              <Button icon={<UploadOutlined />} loading={uploading} style={{ marginBottom: 16 }}>
                上传封面图片
              </Button>
            </Upload>

            <Form.Item shouldUpdate noStyle>
              {() =>
                coverImageValue ? (
                  <div style={{ marginBottom: 24 }}>
                    <img
                      src={coverImageValue}
                      alt="活动封面"
                      style={{ width: 240, height: 140, objectFit: 'cover', borderRadius: 12, border: '1px solid #e5e7eb' }}
                    />
                  </div>
                ) : null
              }
            </Form.Item>

            <div className="form-row">
              <Form.Item name="categoryId" label="活动分类" rules={[{ required: true, message: '请选择活动分类' }]} style={{ flex: 1 }}>
                <Select
                  options={categories.map((item) => ({ value: item.id, label: item.name }))}
                  placeholder="请选择分类"
                />
              </Form.Item>
              <Form.Item name="maxPeople" label="人数上限" rules={[{ required: true, message: '请输入人数上限' }]} style={{ flex: 1, marginLeft: 16 }}>
                <InputNumber min={1} max={9999} style={{ width: '100%' }} />
              </Form.Item>
            </div>

            <Form.Item name="location" label="活动地点" rules={[{ required: true, message: '请输入活动地点' }]}>
              <Input />
            </Form.Item>

            <div className="form-row">
              <Form.Item name="startTime" label="活动开始时间" rules={[{ required: true, message: '请输入开始时间' }]} style={{ flex: 1 }}>
                <Input type="datetime-local" />
              </Form.Item>
              <Form.Item name="endTime" label="活动结束时间" rules={[{ required: true, message: '请输入结束时间' }]} style={{ flex: 1, marginLeft: 16 }}>
                <Input type="datetime-local" />
              </Form.Item>
            </div>

            <Form.Item name="signupEndTime" label="报名截止时间" rules={[{ required: true, message: '请输入报名截止时间' }]}>
              <Input type="datetime-local" />
            </Form.Item>

            <Form.Item name="content" label="活动详情" rules={[{ required: true, message: '请输入活动详情' }]}>
              <TextArea rows={6} />
            </Form.Item>

            <Space>
              <Button type="primary" loading={submitting} onClick={handleSubmit}>
                保存
              </Button>
              <Button onClick={() => navigate('/activity/manage')}>返回列表</Button>
            </Space>
          </Form>
        </div>
      </main>
      <ActivityAiDrawer
        open={aiDrawerOpen}
        onClose={() => setAiDrawerOpen(false)}
        onFill={handleAiFill}
      />
    </div>
  )
}

export default ActivityEditor


