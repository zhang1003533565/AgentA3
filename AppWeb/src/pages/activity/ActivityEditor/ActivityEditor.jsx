import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Form, Input, InputNumber, Select, Space, Switch, Upload, message } from 'antd'
import { MinusCircleOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons'
import { createActivity, getActivityDetail, updateActivity } from '../../../api/activity'
import { getUploadUrl } from '../../../api/upload'
import { getCategoryList } from '../../../api/category'
import '../ActivityManage/ActivityManage.css'
import { parseImageList, toBackendDateTime, toDateTimeInput } from '../activityHelpers'

const { TextArea } = Input
const MAX_UPLOAD_BYTES = 4.5 * 1024 * 1024
const MAX_IMAGE_EDGE = 1600
const DEFAULT_CREDIT_RULES = [
  { role: '主持人', score: 1.5 },
  { role: '工作人员', score: 1.0 },
  { role: '参赛人', score: 1.0 },
  { role: '观众', score: 0.2 },
]

const parseUploadResponse = (response) => {
  if (!response) return null
  if (typeof response === 'string') {
    try {
      return JSON.parse(response)
    } catch (error) {
      return null
    }
  }
  return response
}

const normalizeCreditRules = (record) => {
  let source = record?.creditConfig || record?.creditRules || []
  if (typeof source === 'string') {
    try {
      source = JSON.parse(source)
    } catch (error) {
      source = []
    }
  }
  if (!Array.isArray(source)) source = []
  const normalized = source
    .map((item) => ({
      role: String(item?.role || '').trim(),
      score: Number(item?.score),
    }))
    .filter((item) => item.role && Number.isFinite(item.score))
  return normalized.length ? normalized : DEFAULT_CREDIT_RULES
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
  const [imageUploading, setImageUploading] = useState(false)
  const [galleryImages, setGalleryImages] = useState([])
  const coverImageValue = Form.useWatch('coverImage', form)

  useEffect(() => {
    const run = async () => {
      setLoading(true)
      try {
        const categoryRes = await getCategoryList()
        setCategories(categoryRes?.data || [])
        if (isEdit) {
          const detailRes = await getActivityDetail(id)
          const record = detailRes?.data
          form.setFieldsValue({
            title: record?.title,
            categoryId: record?.categoryId,
            coverImage: record?.coverImage,
            images: parseImageList(record?.images),
            location: record?.location,
            maxPeople: record?.maxPeople,
            content: record?.content,
            contactName: record?.contactName,
            contactPhone: record?.contactPhone,
            requiresAudit: Boolean(record?.requiresAudit),
            cancelRequiresAudit: Boolean(record?.cancelRequiresAudit),
            score: record?.score,
            creditRules: normalizeCreditRules(record),
            startTime: toDateTimeInput(record?.startTime),
            endTime: toDateTimeInput(record?.endTime),
            signupStartTime: toDateTimeInput(record?.signupStartTime),
            signupEndTime: toDateTimeInput(record?.signupEndTime),
            signInStartTime: toDateTimeInput(record?.signInStartTime),
            signInEndTime: toDateTimeInput(record?.signInEndTime),
          })
          setGalleryImages(parseImageList(record?.images))
        } else {
          form.setFieldsValue({
            requiresAudit: true,
            cancelRequiresAudit: false,
            score: 0.2,
            creditRules: DEFAULT_CREDIT_RULES,
          })
          setGalleryImages([])
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
      const creditRules = (values.creditRules || [])
        .map((item) => ({
          role: String(item?.role || '').trim(),
          score: Number(item?.score),
        }))
        .filter((item) => item.role && Number.isFinite(item.score))
      const payload = {
        title: values.title,
        categoryId: values.categoryId,
        coverImage: values.coverImage,
        images: JSON.stringify(galleryImages),
        location: values.location,
        maxPeople: values.maxPeople,
        content: values.content,
        contactName: values.contactName,
        contactPhone: values.contactPhone,
        requiresAudit: Boolean(values.requiresAudit),
        cancelRequiresAudit: Boolean(values.cancelRequiresAudit),
        score: Number(values.score),
        creditConfig: JSON.stringify(creditRules),
        startTime: toBackendDateTime(values.startTime),
        endTime: toBackendDateTime(values.endTime),
        signupStartTime: toBackendDateTime(values.signupStartTime),
        signupEndTime: toBackendDateTime(values.signupEndTime),
        signInStartTime: toBackendDateTime(values.signInStartTime),
        signInEndTime: toBackendDateTime(values.signInEndTime),
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
      if (!error?.errorFields) {
        message.error(error?.message || '提交失败')
      }
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

  const handleGalleryUploadChange = ({ file }) => {
    if (file.status === 'uploading') {
      setImageUploading(true)
      return
    }
    if (file.status === 'done') {
      setImageUploading(false)
      const response = parseUploadResponse(file.response)
      const url = response?.data?.url
      if (response?.code === 200 && url) {
        setGalleryImages((prev) => {
          const next = [...prev, url]
          form.setFieldValue('images', next)
          if (!form.getFieldValue('coverImage')) {
            form.setFieldValue('coverImage', url)
          }
          return next
        })
        message.success('活动图片上传成功')
      } else {
        message.error(response?.msg || '上传返回内容异常')
      }
      return
    }
    if (file.status === 'error') {
      setImageUploading(false)
      message.error(file.error?.message || '活动图片上传失败')
    }
  }

  return (
    <div className="activity-manage-container">
      <main className="manage-main">
        <div className="page-header">
          <h2>{isEdit ? '编辑活动' : '创建活动'}</h2>
        </div>

        <div className="search-bar" style={{ display: 'block' }}>
          <Form form={form} layout="vertical" disabled={loading}>
            <Form.Item name="title" label="活动标题" rules={[{ required: true, message: '请输入活动标题' }]}>
              <Input />
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

            <Form.Item label="活动图片">
              <Upload
                name="file"
                showUploadList={false}
                customRequest={uploadImageFile}
                onChange={handleGalleryUploadChange}
              >
                <Button icon={<UploadOutlined />} loading={imageUploading} style={{ marginBottom: 16 }}>
                  上传活动图片
                </Button>
              </Upload>

              {galleryImages.length ? (
                <div className="activity-image-grid">
                  {galleryImages.map((url, index) => {
                    const isCover = form.getFieldValue('coverImage') === url
                    return (
                      <div key={`${url}-${index}`} className={`activity-image-card ${isCover ? 'cover' : ''}`}>
                        <img src={url} alt={`活动图片${index + 1}`} />
                        <div className="activity-image-actions">
                          <Button size="small" type={isCover ? 'primary' : 'default'} onClick={() => form.setFieldValue('coverImage', url)}>
                            {isCover ? '当前封面' : '设为封面'}
                          </Button>
                          <Button
                            size="small"
                            danger
                            onClick={() => {
                              setGalleryImages((prev) => {
                                const next = prev.filter((item) => item !== url)
                                form.setFieldValue('images', next)
                                if (form.getFieldValue('coverImage') === url) {
                                  form.setFieldValue('coverImage', next[0] || '')
                                }
                                return next
                              })
                            }}
                          >
                            删除
                          </Button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              ) : (
                <div className="activity-image-empty">可上传多张活动图片，并从中选择封面图。</div>
              )}
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

            <Form.Item
              name="requiresAudit"
              label="报名审核"
              valuePropName="checked"
              tooltip="开启后：学生报名先进入等待审核；关闭后：报名直接通过。"
            >
              <Switch checkedChildren="需要审核" unCheckedChildren="无需审核" />
            </Form.Item>
            <Form.Item
              name="cancelRequiresAudit"
              label="取消报名审核"
              valuePropName="checked"
              tooltip="开启后：学生点击取消报名先进入等待审核；关闭后：确认后立即取消。"
            >
              <Switch checkedChildren="需要审核" unCheckedChildren="无需审核" />
            </Form.Item>

            <Form.Item
              name="score"
              label="活动学分"
              rules={[{ required: true, message: '请填写活动学分' }]}
            >
              <InputNumber min={0} max={99} precision={1} step={0.1} style={{ width: '100%' }} placeholder="例如 0.2" />
            </Form.Item>

            <Form.Item label="角色学分分配（申报人自定义）" required>
              <Form.List
                name="creditRules"
                rules={[
                  {
                    validator: async (_, value) => {
                      if (!Array.isArray(value) || value.length === 0) {
                        throw new Error('请至少添加一个角色学分分配')
                      }
                      const hasInvalid = value.some(
                        (item) =>
                          !String(item?.role || '').trim() ||
                          item?.score === undefined ||
                          item?.score === null ||
                          Number.isNaN(Number(item?.score))
                      )
                      if (hasInvalid) {
                        throw new Error('请完整填写每个角色及对应学分')
                      }
                    },
                  },
                ]}
              >
                {(fields, { add, remove }, { errors }) => (
                  <>
                    {fields.map(({ key, name, ...restField }) => (
                      <Space key={key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                        <Form.Item
                          {...restField}
                          name={[name, 'role']}
                          rules={[{ required: true, message: '填写角色' }]}
                          style={{ marginBottom: 0 }}
                        >
                          <Input placeholder="角色，如：主持人/工作人员/观众/参赛人" style={{ width: 360 }} />
                        </Form.Item>
                        <Form.Item
                          {...restField}
                          name={[name, 'score']}
                          rules={[{ required: true, message: '填写学分' }]}
                          style={{ marginBottom: 0 }}
                        >
                          <InputNumber min={0} max={99} precision={1} step={0.1} placeholder="score" />
                        </Form.Item>
                        <MinusCircleOutlined onClick={() => remove(name)} />
                      </Space>
                    ))}
                    <Form.Item style={{ marginTop: 8, marginBottom: 0 }}>
                      <Button type="dashed" onClick={() => add({ role: '', score: 0 })} icon={<PlusOutlined />}>
                        添加角色学分
                      </Button>
                    </Form.Item>
                    <Form.ErrorList errors={errors} />
                  </>
                )}
              </Form.List>
            </Form.Item>

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

            <div className="form-row">
              <Form.Item name="signupStartTime" label="报名开始时间" rules={[{ required: true, message: '请输入报名开始时间' }]} style={{ flex: 1 }}>
                <Input type="datetime-local" />
              </Form.Item>
              <Form.Item name="signupEndTime" label="报名截止时间" rules={[{ required: true, message: '请输入报名截止时间' }]} style={{ flex: 1, marginLeft: 16 }}>
                <Input type="datetime-local" />
              </Form.Item>
            </div>

            <div className="form-row">
              <Form.Item name="signInStartTime" label="签到开始时间" rules={[{ required: true, message: '请输入签到开始时间' }]} style={{ flex: 1 }}>
                <Input type="datetime-local" />
              </Form.Item>
              <Form.Item name="signInEndTime" label="签到结束时间" rules={[{ required: true, message: '请输入签到结束时间' }]} style={{ flex: 1, marginLeft: 16 }}>
                <Input type="datetime-local" />
              </Form.Item>
            </div>

            <div className="form-row">
              <Form.Item name="contactName" label="联系人" rules={[{ required: true, message: '请输入联系人' }]} style={{ flex: 1 }}>
                <Input />
              </Form.Item>
              <Form.Item name="contactPhone" label="联系电话" rules={[{ required: true, message: '请输入联系电话' }]} style={{ flex: 1, marginLeft: 16 }}>
                <Input />
              </Form.Item>
            </div>

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
    </div>
  )
}

export default ActivityEditor



