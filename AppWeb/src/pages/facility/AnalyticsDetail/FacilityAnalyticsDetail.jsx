import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Button, Empty, Form, Spin, message } from 'antd'
import { InfoCircleOutlined, ReadOutlined } from '@ant-design/icons'
import { getFacilityDetail, getFacilityTypes, updateFacility } from '../../../api/facility'
import { MAP_BUILDING_UPLOAD_FOLDER, uploadImage } from '../../../api/upload'
import { toVisibleFacilityTypeOptions } from '../../../config/facilityType'
import { parseFacilityMeaningItems, serializeFacilityMeaningItems } from '../../../utils/facilityMeaning'
import { parseFacilityStory, serializeFacilityStory } from '../../../utils/facilityStory'
import SidePanel from '../../../components/SidePanel/SidePanel'
import FacilityAnalyticsForm from '../../../components/FacilityAnalyticsForm/FacilityAnalyticsForm'
import './FacilityAnalyticsDetail.css'

const parseFacilityImages = (images) => {
  const normalizeItem = (item) => {
    if (!item) return null
    if (typeof item === 'string') return { url: item, position: '50% 50%' }
    if (item.url) return { url: item.url, position: item.position || '50% 50%' }
    return null
  }
  if (Array.isArray(images)) return images.map(normalizeItem).filter(Boolean)
  if (!images) return []
  if (typeof images === 'string') {
    try {
      const parsed = JSON.parse(images)
      return Array.isArray(parsed) ? parsed.map(normalizeItem).filter(Boolean) : []
    } catch {
      return []
    }
  }
  return []
}

const formatDetailValue = (value, unit = '') => {
  if (value === undefined || value === null || value === '') return '-'
  return `${value}${unit}`
}

function FacilityDetailHeroCarousel({ images, alt }) {
  const [activeIndex, setActiveIndex] = useState(0)
  const [isResetting, setIsResetting] = useState(false)
  const slides = images.length > 1 ? [...images, images[0]] : images

  useEffect(() => {
    if (images.length <= 1) {
      setActiveIndex(0)
      setIsResetting(false)
      return undefined
    }
    const timer = window.setInterval(() => {
      setActiveIndex((prev) => prev + 1)
    }, 3000)
    return () => window.clearInterval(timer)
  }, [images.length])

  useEffect(() => {
    if (images.length <= 1 || activeIndex !== images.length) return undefined
    const resetTimer = window.setTimeout(() => {
      setIsResetting(true)
      setActiveIndex(0)
      window.requestAnimationFrame(() => {
        window.requestAnimationFrame(() => setIsResetting(false))
      })
    }, 460)
    return () => window.clearTimeout(resetTimer)
  }, [activeIndex, images.length])

  return (
    <div className={`facility-detail-hero__carousel${images.length === 1 ? ' is-single' : ''}`}>
      <div
        className={`facility-detail-hero__carousel-track${isResetting ? ' is-resetting' : ''}`}
        style={{ transform: `translateX(-${activeIndex * 100}%)` }}
      >
        {slides.map((item, index) => (
          <div key={`${item.url}-${index}`} className="facility-detail-hero__slide">
            <img
              className="facility-detail-hero__img"
              src={item.url}
              alt={alt}
              style={{ objectPosition: item.position || '50% 50%' }}
            />
          </div>
        ))}
      </div>
    </div>
  )
}

function FacilityAnalyticsDetail() {
  const { id } = useParams()
  const [loading, setLoading] = useState(false)
  const [record, setRecord] = useState(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailSaving, setDetailSaving] = useState(false)
  const [landscapeCategoryOptions, setLandscapeCategoryOptions] = useState([])
  const [detailImageList, setDetailImageList] = useState([])
  const [detailImageUploading, setDetailImageUploading] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => {
    let cancelled = false

    const loadDetail = async () => {
      setLoading(true)
      try {
        const detailRes = await getFacilityDetail(id)
        if (cancelled) return
        const nextRecord = detailRes?.data || null
        const nextImages = parseFacilityImages(nextRecord?.images)
        const nextStory = parseFacilityStory(nextRecord?.campusStory)
        setRecord(nextRecord)
        setDetailImageList(nextImages)
        form.setFieldsValue({
          facilityName: nextRecord?.facilityName,
          material: nextRecord?.material,
          height: nextRecord?.height,
          weight: nextRecord?.weight,
          baseType: nextRecord?.baseType,
          landscapeCategory: nextRecord?.facilityType,
          description: nextRecord?.description,
          cultureBackground: nextRecord?.cultureBackground,
          cultureHighlightText: nextRecord?.cultureHighlightText,
          meaningItems: parseFacilityMeaningItems(nextRecord?.meaningInterpretation),
          campusStoryContent: nextStory.content,
          campusStoryImage: nextStory.image,
          location: nextRecord?.location,
          images: JSON.stringify(nextImages),
        })
      } catch (error) {
        if (!cancelled) {
          message.error(error?.message || '详情加载失败')
          setRecord(null)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadDetail()
    return () => {
      cancelled = true
    }
  }, [form, id])

  useEffect(() => {
    let cancelled = false

    const loadFacilityTypes = async () => {
      try {
        const typeRes = await getFacilityTypes()
        if (cancelled) return
        const list = Array.isArray(typeRes?.data) ? typeRes.data : typeRes?.data?.records || []
        setLandscapeCategoryOptions(toVisibleFacilityTypeOptions(list))
      } catch {
        if (!cancelled) setLandscapeCategoryOptions([])
      }
    }

    loadFacilityTypes()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const handleOpenEdit = () => setDetailOpen(true)
    window.addEventListener('facility-detail-edit', handleOpenEdit)
    return () => window.removeEventListener('facility-detail-edit', handleOpenEdit)
  }, [])

  const handleSaveDetail = async () => {
    setDetailSaving(true)
    try {
      const values = await form.validateFields()
      const payload = {
        facilityName: values.facilityName,
        facilityType: Number(values.landscapeCategory),
        material: values.material,
        height: values.height,
        weight: values.weight,
        baseType: values.baseType,
        description: values.description,
        cultureBackground: values.cultureBackground,
        cultureHighlightText: values.cultureHighlightText,
        meaningInterpretation: serializeFacilityMeaningItems(values.meaningItems),
        campusStory: serializeFacilityStory({
          content: values.campusStoryContent,
          image: values.campusStoryImage,
        }),
        location: values.location,
        images: values.images || record?.images,
        status: record?.status ?? 1,
      }
      const res = await updateFacility(id, payload)
      setRecord((prev) => ({
        ...prev,
        ...payload,
        ...(res?.data || {}),
      }))
      setDetailOpen(false)
      message.success('保存成功')
    } catch (error) {
      if (!error?.errorFields) {
        message.error(error?.message || '保存失败')
      }
    } finally {
      setDetailSaving(false)
    }
  }

  const handleDetailImageUpload = async (file) => {
    setDetailImageUploading(true)
    try {
      const imageUrl = await uploadImage(file, MAP_BUILDING_UPLOAD_FOLDER)
      setDetailImageList((prev) => {
        const nextList = [...prev, { url: imageUrl, position: '50% 50%' }]
        form.setFieldsValue({ images: JSON.stringify(nextList) })
        return nextList
      })
      return false
    } catch (error) {
      message.error(error?.message || '图片上传失败')
      return false
    } finally {
      setDetailImageUploading(false)
    }
  }

  const handleDetailImageRemove = (index) => {
    const nextList = detailImageList.filter((_, itemIndex) => itemIndex !== index)
    setDetailImageList(nextList)
    form.setFieldsValue({ images: JSON.stringify(nextList) })
  }

  const renderCultureBackground = () => {
    const content = record?.cultureBackground || '-'
    const highlight = record?.cultureHighlightText?.trim()
    if (!highlight) return content

    const highlightIndex = content.indexOf(highlight)
    if (highlightIndex === -1) {
      return (
        <>
          <span className="facility-detail-culture__lead">{highlight}</span>
          {content}
        </>
      )
    }

    return (
      <>
        {content.slice(0, highlightIndex)}
        <span className="facility-detail-culture__lead">{highlight}</span>
        {content.slice(highlightIndex + highlight.length)}
      </>
    )
  }

  const images = parseFacilityImages(record?.images)
  const meaningItems = parseFacilityMeaningItems(record?.meaningInterpretation)
  const campusStory = parseFacilityStory(record?.campusStory)
  const paramItems = [
    { label: '材质', value: formatDetailValue(record?.material) },
    { label: '总高度', value: formatDetailValue(record?.height, ' 米') },
    { label: '重量', value: formatDetailValue(record?.weight, ' 吨') },
    { label: '基座形制', value: formatDetailValue(record?.baseType) },
  ]

  return (
    <div className="facility-detail-page">
      <Spin spinning={loading}>
        {record ? (
          <>
            <div className="facility-detail-layout">
              <section className="facility-detail-hero">
                {images.length ? (
                  <FacilityDetailHeroCarousel images={images} alt={record.facilityName || '设施图片'} />
                ) : (
                  <div className="facility-detail-cover-empty" />
                )}
                <div className="facility-detail-hero__shade" />
                <div className="facility-detail-hero__content">
                  <h1>{record.facilityName || ''}</h1>
                  <div className="facility-detail-hero__summary">
                    <span className="facility-detail-hero__summary-space" />
                    <span className="facility-detail-hero__summary-divider">|</span>
                    <span className="facility-detail-hero__summary-text">
                      {formatDetailValue(record.description)}
                    </span>
                  </div>
                </div>
              </section>

              <aside className="facility-detail-side">
                <section className="facility-detail-panel facility-detail-panel--params">
                  <div className="facility-detail-params__title">
                    <InfoCircleOutlined />
                    <span>基础参数</span>
                  </div>
                  <div className="facility-detail-params__grid">
                    {paramItems.map((item) => (
                      <div key={item.label} className="facility-detail-params__item">
                        <span>{item.label}</span>
                        <strong>{item.value}</strong>
                      </div>
                    ))}
                  </div>
                </section>
                <section className="facility-detail-panel facility-detail-panel--map" />
              </aside>

              <section className="facility-detail-panel facility-detail-panel--culture">
                <div className="facility-detail-culture__title">
                  <span className="facility-detail-culture__icon">
                    <ReadOutlined />
                  </span>
                  <strong>文化背景</strong>
                </div>
                <div className="facility-detail-culture__body">
                  {renderCultureBackground()}
                </div>
              </section>
              <section className="facility-detail-panel facility-detail-panel--meaning">
                <h2>寓意解读</h2>
                {meaningItems.length ? (
                  <div className="facility-detail-meaning__list">
                    {meaningItems.map((item, index) => (
                      <div key={`${item.title}-${index}`} className="facility-detail-meaning__item">
                        <div className="facility-detail-meaning__mark" />
                        <div className="facility-detail-meaning__content">
                          {item.title ? <h3>{item.title}</h3> : null}
                          <p>{item.content || '-'}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="facility-detail-meaning__body">-</div>
                )}
              </section>
              <section className="facility-detail-panel facility-detail-panel--wide">
                <div className="facility-detail-story__media">
                  {campusStory.image ? (
                    <img src={campusStory.image} alt="校园故事图片" />
                  ) : (
                    <div className="facility-detail-story__media-empty">暂无图片</div>
                  )}
                </div>
                <div className="facility-detail-story__content">
                  <div className="facility-detail-story__title">
                    <ReadOutlined />
                    <strong>校园故事</strong>
                  </div>
                  <div className="facility-detail-story__body">
                    {formatDetailValue(campusStory.content)}
                  </div>
                </div>
              </section>
            </div>

            <SidePanel
              title="编辑信息"
              open={detailOpen}
              onClose={() => setDetailOpen(false)}
              width={720}
              destroyOnHidden
              footer={(
                <>
                  <Button onClick={() => setDetailOpen(false)}>取消</Button>
                  <Button type="primary" loading={detailSaving} onClick={handleSaveDetail}>保存</Button>
                </>
              )}
            >
              <Form form={form} layout="vertical">
                <FacilityAnalyticsForm
                  imageList={detailImageList}
                  imageUploading={detailImageUploading}
                  onImageUpload={handleDetailImageUpload}
                  onImageRemove={handleDetailImageRemove}
                  landscapeCategoryOptions={landscapeCategoryOptions}
                  showCultureEditor
                />
              </Form>
            </SidePanel>
          </>
        ) : (
          <div className="facility-detail-layout">
            <section className="facility-detail-hero">
              <div className="facility-detail-cover-empty" />
            </section>
            <aside className="facility-detail-side">
              <section className="facility-detail-panel facility-detail-panel--params" />
              <section className="facility-detail-panel facility-detail-panel--map" />
            </aside>
            <section className="facility-detail-panel facility-detail-panel--culture">
              <div className="facility-detail-empty">
                <Empty description={loading ? '加载中...' : '暂无设施详情'} />
              </div>
            </section>
            <section className="facility-detail-panel facility-detail-panel--meaning" />
            <section className="facility-detail-panel facility-detail-panel--wide" />
          </div>
        )}
      </Spin>
    </div>
  )
}

export default FacilityAnalyticsDetail
