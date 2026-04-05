import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Form, Input, InputNumber, Select, Space, Upload, message } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { createActivity, getActivityDetail, updateActivity } from '../../../api/activity'
import { getUploadUrl } from '../../../api/upload'
import { getCategoryList } from '../../../api/category'
import '../ActivityManage/ActivityManage.css'
import { parseImageList, toBackendDateTime, toDateTimeInput } from '../activityHelpers'

const { TextArea } = Input

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
            startTime: toDateTimeInput(record?.startTime),
            endTime: toDateTimeInput(record?.endTime),
            signupStartTime: toDateTimeInput(record?.signupStartTime),
            signupEndTime: toDateTimeInput(record?.signupEndTime),
          })
          setGalleryImages(parseImageList(record?.images))
        } else {
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
        startTime: toBackendDateTime(values.startTime),
        endTime: toBackendDateTime(values.endTime),
        signupStartTime: toBackendDateTime(values.signupStartTime),
        signupEndTime: toBackendDateTime(values.signupEndTime),
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
              action={getUploadUrl()}
              headers={{
                Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
              }}
              showUploadList={false}
              onChange={({ file }) => {
                if (file.status === 'uploading') {
                  setUploading(true)
                  return
                }
                if (file.status === 'done') {
                  setUploading(false)
                  const url = file.response?.data?.url
                  if (url) {
                    form.setFieldValue('coverImage', url)
                    message.success('图片上传成功')
                  } else {
                    message.error('上传返回内容异常')
                  }
                }
                if (file.status === 'error') {
                  setUploading(false)
                  message.error('图片上传失败')
                }
              }}
            >
              <Button icon={<UploadOutlined />} loading={uploading} style={{ marginBottom: 16 }}>
                上传封面图片
              </Button>
            </Upload>

            <Form.Item shouldUpdate noStyle>
              {() =>
                form.getFieldValue('coverImage') ? (
                  <div style={{ marginBottom: 24 }}>
                    <img
                      src={form.getFieldValue('coverImage')}
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
                action={getUploadUrl()}
                headers={{
                  Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
                }}
                showUploadList={false}
                onChange={({ file }) => {
                  if (file.status === 'uploading') {
                    setImageUploading(true)
                    return
                  }
                  if (file.status === 'done') {
                    setImageUploading(false)
                    const url = file.response?.data?.url
                    if (url) {
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
                      message.error('上传返回内容异常')
                    }
                  }
                  if (file.status === 'error') {
                    setImageUploading(false)
                    message.error('活动图片上传失败')
                  }
                }}
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
