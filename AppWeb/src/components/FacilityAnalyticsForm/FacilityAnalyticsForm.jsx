import { useEffect, useState } from 'react'
import { Form, Input, Select, Upload } from 'antd'
import { EnvironmentOutlined, UploadOutlined } from '@ant-design/icons'
import { getFacilityTypes } from '../../api/facility'
import { toVisibleFacilityTypeOptions } from '../../config/facilityType'
import './FacilityAnalyticsForm.css'

const BANNER_STYLE = {
  minHeight: 112,
  borderRadius: 16,
  background: '#f4f0ff',
  border: '1px solid #e5ddff',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  color: '#5b6acb',
  fontWeight: 600,
  cursor: 'pointer',
}

const getImageUrl = (item) => (typeof item === 'string' ? item : item?.url)

function FacilityAnalyticsForm({
  imageList = [],
  imageUploading = false,
  onImageUpload,
  onImageRemove,
  onLocationPick,
  showMapPicker = true,
  showImageUpload = true,
  landscapeCategoryOptions = [],
}) {
  const [resolvedLandscapeCategoryOptions, setResolvedLandscapeCategoryOptions] = useState(landscapeCategoryOptions)

  useEffect(() => {
    let cancelled = false

    if (landscapeCategoryOptions.length) {
      setResolvedLandscapeCategoryOptions(landscapeCategoryOptions)
      return () => {
        cancelled = true
      }
    }

    const loadLandscapeOptions = async () => {
      try {
        const res = await getFacilityTypes()
        if (cancelled) return
        const types = Array.isArray(res?.data) ? res.data : []
        setResolvedLandscapeCategoryOptions(toVisibleFacilityTypeOptions(types))
      } catch {
        if (!cancelled) setResolvedLandscapeCategoryOptions([])
      }
    }

    loadLandscapeOptions()
    return () => {
      cancelled = true
    }
  }, [landscapeCategoryOptions])

  return (
    <>
      <Form.Item
        name="facilityName"
        label="设施名称"
        rules={[{ required: true, message: '请输入设施名称' }]}
      >
        <Input placeholder="请输入设施名称" />
      </Form.Item>

      <Form.Item
        name="landscapeCategory"
        label="设施分类"
        rules={[{ required: true, message: '请选择设施分类' }]}
      >
        <Select placeholder="请选择分类" options={resolvedLandscapeCategoryOptions} />
      </Form.Item>

      <Form.Item
        name="description"
        label="简介描述"
        rules={[{ required: true, message: '请输入简介描述' }]}
      >
        <Input.TextArea rows={4} placeholder="请输入设施的详细描述..." />
      </Form.Item>

      {showMapPicker ? (
        <Form.Item name="location" label="地图选点">
          <div style={BANNER_STYLE} onClick={onLocationPick}>
            <EnvironmentOutlined style={{ fontSize: 26, marginRight: 12 }} />
            点击在地图上标注位置
          </div>
        </Form.Item>
      ) : null}

      <Form.Item name="images" label="图片上传" hidden>
        <Input />
      </Form.Item>

      {showImageUpload ? (
        <Form.Item label="图片上传" className="facility-analytics-form__upload-item">
          <Upload
            listType="picture-card"
            fileList={imageList.map((item, index) => ({
              uid: String(index),
              name: `图片${index + 1}`,
              status: 'done',
              url: getImageUrl(item),
            }))}
            beforeUpload={(file) => (onImageUpload ? onImageUpload(file) : false)}
            onRemove={(file) => {
              const index = imageList.findIndex((item) => getImageUrl(item) === file.url)
              if (index !== -1) {
                onImageRemove?.(index)
              }
            }}
            showUploadList={{
              showPreviewIcon: true,
              showRemoveIcon: true,
            }}
            disabled={imageUploading || imageList.length >= 3}
          >
            {imageList.length < 3 ? (
              <div style={{ padding: 16, textAlign: 'center' }}>
                <UploadOutlined style={{ fontSize: 24, color: '#94a3b8' }} />
                <p style={{ marginTop: 8, color: '#94a3b8', fontSize: 12 }}>
                  点击或拖拽图片至此上传
                </p>
              </div>
            ) : null}
          </Upload>
        </Form.Item>
      ) : null}
    </>
  )
}

export default FacilityAnalyticsForm
