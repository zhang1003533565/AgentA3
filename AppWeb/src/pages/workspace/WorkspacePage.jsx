import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Card, Empty, Form, Image, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { createActivity, deleteActivity, getActivityList, publishActivity, updateActivity } from '../../api/activity'
import { createCategory, getCategoryList, updateCategory } from '../../api/category'
import { getDiscountActivityList } from '../../api/discount'
import { createFacility, deleteFacility, getFacilityList, updateFacility } from '../../api/facility'
import { createDish, createStall, deleteDish, deleteStall, getCanteenStallList, getDishList, updateDish, updateStall } from '../../api/dish'
import { adminDeleteComment, adminDeletePost, createTopic, deleteTopic, getCommentList, getPostList, getTopicList, updateTopic } from '../../api/forum'
import { deleteMarker, getFacilityHeat, getMapConfig, getMarkerList, getNavigationStatistics } from '../../api/map'
import {
  createMerchant,
  createMerchantCategory,
  deleteMerchant,
  deleteMerchantCategory,
  getMerchantList,
  getMerchantCategoryList,
  getMerchantStatistics,
  updateMerchant,
  updateMerchantCategory,
} from '../../api/merchant'
import { auditRegistration, getRegistrationList } from '../../api/registration'
import {
  createSecondhandCategory,
  deleteSecondhandCategory,
  deleteSecondhandItem,
  getSecondhandAdminList,
  getSecondhandCategoryList,
  getSecondhandStatistics,
  markSecondhandItemSold,
  offlineSecondhandItem,
  onlineSecondhandItem,
  updateSecondhandCategory,
} from '../../api/secondhand'
import { closeSignIn, getSignInList, openSignIn } from '../../api/signin'
import { disableUser, enableUser, getUserList } from '../../api/user'
import { getWorkspacePage } from '../../data/portalData'
import './WorkspacePage.css'

const colorMap = {
  true: 'green',
  false: 'default',
  0: 'red',
  1: 'green',
  正常: 'green',
  启用: 'green',
  PUBLISHED: 'green',
  APPROVED: 'green',
  COMPLETED: 'green',
  DRAFT: 'default',
  PENDING: 'orange',
  REJECTED: 'red',
  CANCELLED: 'red',
  ADMIN: 'red',
  TEACHER: 'blue',
  STUDENT: 'default',
}

const toSummaryRows = (obj, prefix = '') =>
  Object.entries(obj || {}).map(([label, value], index) => ({
    id: `${prefix}${label}-${index}`,
    label: prefix ? `${prefix}${label}` : label,
    value: Array.isArray(value) ? JSON.stringify(value) : String(value ?? '-'),
  }))

const toDateTimeLocal = (value) => {
  if (!value) return undefined
  return String(value).replace(' ', 'T').slice(0, 16)
}

const toBackendDateTime = (value) => {
  if (!value) return null
  return `${value.replace('T', ' ')}:00`
}

const loadWorkspaceData = async (pageKey, { current, pageSize, keyword, contextId, urlStallId }) => {
  switch (pageKey) {
    case 'user-manage': {
      const res = await getUserList({ page: current, size: pageSize, username: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'activity-center': {
      const res = await getActivityList({ page: current, size: pageSize, title: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'activity-category': {
      const res = await getCategoryList()
      const rows = Array.isArray(res.data) ? res.data : []
      return { rows, total: rows.length }
    }
    case 'activity-audit': {
      if (!contextId) return { rows: [], total: 0 }
      const res = await getRegistrationList(contextId, { page: current, size: pageSize })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'activity-signin': {
      if (!contextId) return { rows: [], total: 0 }
      const res = await getSignInList(contextId, current, pageSize)
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'forum-post': {
      const res = await getPostList({ page: current, size: pageSize, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'forum-comment': {
      if (!contextId) return { rows: [], total: 0 }
      const res = await getCommentList({ postId: contextId, page: current, size: pageSize })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'forum-topic': {
      const res = await getTopicList({ page: current, size: pageSize })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-restaurant': {
      const res = await getCanteenStallList({ page: current, size: pageSize })
      const rows = Array.isArray(res.data) ? res.data : []
      return { rows, total: rows.length }
    }
    case 'facility-sports': {
      const res = await getFacilityList({ type: 2, page: current, size: pageSize, name: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-teaching': {
      const res = await getFacilityList({ type: 3, page: current, size: pageSize, name: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-dormitory': {
      const res = await getFacilityList({ type: 4, page: current, size: pageSize, name: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-stall-dish': {
      if (!urlStallId) return { rows: [], total: 0 }
      const res = await getDishList({ stallId: parseInt(urlStallId), name: keyword })
      return { rows: res.data || [], total: res.data?.length || 0 }
    }
    case 'map-config': {
      const res = await getMapConfig()
      const rows = toSummaryRows(res.data)
      return { rows, total: rows.length }
    }
    case 'map-marker': {
      const res = await getMarkerList({ page: current, size: pageSize, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'map-analytics': {
      const [navigationRes, heatRes] = await Promise.all([getNavigationStatistics(), getFacilityHeat({ limit: 10 })])
      const rows = [
        ...toSummaryRows(navigationRes.data, 'navigation.'),
        ...(Array.isArray(heatRes.data)
          ? heatRes.data.map((item, index) => ({
              id: `heat-${index}`,
              label: item.markerName || item.facilityName || `heat-${index + 1}`,
              value: String(item.visitCount ?? item.navigationCount ?? item.count ?? '-'),
            }))
          : []),
      ]
      return { rows, total: rows.length }
    }
    case 'market-item':
    case 'market-audit': {
      const res = await getSecondhandAdminList({ page: current, size: pageSize, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'market-category': {
      const res = await getSecondhandCategoryList()
      const rows = Array.isArray(res.data) ? res.data : []
      return { rows, total: rows.length }
    }
    case 'discount-merchant': {
      const res = await getMerchantList({ page: current, size: pageSize, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'discount-activity': {
      const res = await getDiscountActivityList({ page: current, size: pageSize, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'discount-category': {
      const res = await getMerchantCategoryList()
      const rows = Array.isArray(res.data) ? res.data : []
      return { rows, total: rows.length }
    }
    case 'discount-analytics': {
      const [merchantStatRes, secondhandStatRes] = await Promise.all([getMerchantStatistics(), getSecondhandStatistics()])
      const rows = [
        ...toSummaryRows(merchantStatRes.data, 'discount.'),
        ...toSummaryRows(secondhandStatRes.data, 'secondhand.'),
      ]
      return { rows, total: rows.length }
    }
    default:
      return { rows: [], total: 0 }
  }
}

function renderCell(value, type, row) {
  if (type === 'image') {
    if (!value) {
      return <span style={{ color: '#94a3b8' }}>暂无图片</span>
    }
    return (
      <Image
        src={value}
        alt="preview"
        width={56}
        height={56}
        style={{ objectFit: 'cover', borderRadius: 12 }}
        fallback="data:image/svg+xml;base64,PHN2ZyB4bWxucz0naHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmcnIHdpZHRoPSc1NicgaGVpZ2h0PSc1Nic+PHJlY3Qgd2lkdGg9JzU2JyBoZWlnaHQ9JzU2JyByeD0nMTInIGZpbGw9JyNlNWU3ZWInLz48dGV4dCB4PSc1MCUnIHk9JzUwJScgZm9udC1zaXplPScxMicgZmlsbD0nIzY0NzQ4YicgdGV4dC1hbmNob3I9J21pZGRsZScgZHk9Jy4zNWVtJz7ml6Dlm748L3RleHQ+PC9zdmc+"
      />
    )
  }

  if (type === 'tag' || type === 'status') {
    const text = value === undefined || value === null || value === '' ? '-' : String(value)
    return <Tag color={colorMap[text] || 'default'}>{text}</Tag>
  }

  if (row && value === undefined) {
    return '-'
  }

  return value ?? '-'
}

function WorkspacePage({ pageKey }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const page = getWorkspacePage(pageKey)
  const [form] = Form.useForm()
  const [keyword, setKeyword] = useState('')
  const [status] = useState('全部')
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [modalMode, setModalMode] = useState('create')
  const [editingRecord, setEditingRecord] = useState(null)
  const [merchantCategoryOptions, setMerchantCategoryOptions] = useState([])
  const [activityCategoryOptions, setActivityCategoryOptions] = useState([])
  const [contextInput, setContextInput] = useState('')
  const [contextId, setContextId] = useState('')
  const [urlStallId, setUrlStallId] = useState('')
  const [urlStallName, setUrlStallName] = useState('')
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  })
  const stallImagePreview = Form.useWatch('image', form)
  const dishImagePreview = Form.useWatch('imageUrl', form)

  useEffect(() => {
    let cancelled = false

    const run = async () => {
      if (!page) return
      setLoading(true)
      try {
        const result = await loadWorkspaceData(pageKey, {
          current: pagination.current,
          pageSize: pagination.pageSize,
          keyword,
          status,
          contextId,
          urlStallId,
        })
        if (!cancelled) {
          setRows(result.rows)
          setPagination((prev) => ({
            ...prev,
            total: result.total,
          }))
        }
      } catch (error) {
        if (!cancelled) {
          setRows([])
          setPagination((prev) => ({
            ...prev,
            total: 0,
          }))
        }
        console.error(`加载 ${pageKey} 数据失败:`, error)
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    run()
    return () => {
      cancelled = true
    }
  }, [contextId, page, pageKey, pagination.current, pagination.pageSize, keyword, status, urlStallId])

  // 解析 URL 参数（仅 facility-stall-dish 页面）
  useEffect(() => {
    if (pageKey !== 'facility-stall-dish') return

    const stallId = searchParams.get('stallId')
    const stallName = searchParams.get('stallName')
    if (stallId) {
      setUrlStallId(stallId)
      setUrlStallName(stallName || '')
    } else {
      setUrlStallId('')
      setUrlStallName('')
    }
  }, [pageKey, searchParams])

  const runAction = async (fn, successText) => {
    setActionLoading(true)
    try {
      await fn()
      message.success(successText)
      const result = await loadWorkspaceData(pageKey, {
        current: pagination.current,
        pageSize: pagination.pageSize,
        keyword,
        status,
        contextId,
        urlStallId,
      })
      setRows(result.rows)
      setPagination((prev) => ({ ...prev, total: result.total }))
    } catch (error) {
      message.error(error?.message || '操作失败')
    } finally {
      setActionLoading(false)
    }
  }

  const formEnabledPages = [
    'activity-center',
    'activity-category',
    'forum-topic',
    'facility-restaurant',
    'facility-sports',
    'facility-teaching',
    'facility-dormitory',
    'facility-stall-dish',
    'market-category',
    'discount-category',
    'discount-merchant',
  ]

  const openCreateModal = async () => {
    setModalMode('create')
    setEditingRecord(null)
    form.resetFields()
    if (pageKey === 'activity-center') {
      const res = await getCategoryList()
      const data = Array.isArray(res.data) ? res.data : []
      setActivityCategoryOptions(data.map((item) => ({ value: item.id, label: item.name })))
    }
    if (pageKey === 'discount-merchant') {
      const res = await getMerchantCategoryList()
      setMerchantCategoryOptions(Array.isArray(res.data) ? res.data : [])
    }
    if (pageKey === 'facility-stall-dish') {
      form.setFieldsValue({ stallId: parseInt(urlStallId) })
    }
    setModalOpen(true)
  }

  const openEditModal = async (record) => {
    setModalMode('edit')
    setEditingRecord(record)
    if (pageKey === 'activity-center') {
      const res = await getCategoryList()
      const data = Array.isArray(res.data) ? res.data : []
      setActivityCategoryOptions(data.map((item) => ({ value: item.id, label: item.name })))
    }
    if (pageKey === 'discount-merchant') {
      const res = await getMerchantCategoryList()
      setMerchantCategoryOptions(Array.isArray(res.data) ? res.data : [])
    }
    if (pageKey === 'facility-stall-dish') {
      form.setFieldsValue({ stallId: `${record.stallId} (${record.stallName})` })
    }
    form.setFieldsValue({
      ...(pageKey === 'activity-center'
        ? {
            title: record.title,
            categoryId: record.categoryId,
            location: record.location,
            maxPeople: record.maxPeople,
            content: record.content,
            contactName: record.contactName,
            contactPhone: record.contactPhone,
            startTime: toDateTimeLocal(record.startTime),
            endTime: toDateTimeLocal(record.endTime),
            signupStartTime: toDateTimeLocal(record.signupStartTime),
            signupEndTime: toDateTimeLocal(record.signupEndTime),
          }
        : {}),
      ...(pageKey === 'activity-category'
        ? {
            name: record.categoryName,
            sort: record.sort,
            status: record.status,
          }
        : {}),
      ...(pageKey === 'forum-topic'
        ? {
            topicName: record.topicName,
            topicIcon: record.topicIcon,
            description: record.description,
            isHot: record.isHot,
            status: record.status,
          }
        : {}),
      ...(pageKey === 'facility-restaurant'
        ? {
            stallName: record.stallName,
            restaurantId: record.restaurantId,
            floor: record.floor,
            category: record.category,
            location: record.location,
            score: record.score,
            avgPrice: record.avgPrice,
            businessHours: record.businessHours,
            image: record.image,
            description: record.description,
            status: record.status,
            sort: record.sort,
          }
        : {}),
      ...(['facility-restaurant', 'facility-sports', 'facility-teaching', 'facility-dormitory'].includes(pageKey)
        ? {
            facilityName: record.facilityName,
            facilityType: record.facilityType,
            description: record.description,
            location: record.location,
            longitude: record.longitude,
            latitude: record.latitude,
            images: typeof record.images === 'string' ? record.images : JSON.stringify(record.images || []),
          }
        : {}),
      ...(['market-category', 'discount-category'].includes(pageKey)
        ? {
            categoryName: record.categoryName,
            sort: record.sort,
          }
        : {}),
      ...(pageKey === 'discount-merchant'
        ? {
            merchantName: record.merchantName,
            categoryId: record.categoryId,
            description: record.description,
            address: record.address,
            contactPhone: record.contactPhone,
            contactName: record.contactName,
            businessHours: record.businessHours,
            username: record.merchantUsername,
            password: record.merchantPassword,
          }
        : {}),
      ...(pageKey === 'facility-stall-dish'
        ? {
            name: record.name,
            stallId: record.stallId,
            price: typeof record.price === 'string' ? parseFloat(record.price) : record.price,
            category: record.category,
            taste: record.taste,
            imageUrl: record.imageUrl,
            isAvailable: record.isAvailable,
          }
        : {}),
    })
    setModalOpen(true)
  }

  const submitModal = async () => {
    try {
      const values = await form.validateFields()
      const actionMap = {
        'activity-center': {
          create: () =>
            createActivity({
              title: values.title,
              categoryId: values.categoryId,
              location: values.location,
              maxPeople: values.maxPeople,
              content: values.content,
              contactName: values.contactName,
              contactPhone: values.contactPhone,
              startTime: toBackendDateTime(values.startTime),
              endTime: toBackendDateTime(values.endTime),
              signupStartTime: toBackendDateTime(values.signupStartTime),
              signupEndTime: toBackendDateTime(values.signupEndTime),
            }),
          edit: () =>
            updateActivity(editingRecord.id, {
              title: values.title,
              categoryId: values.categoryId,
              location: values.location,
              maxPeople: values.maxPeople,
              content: values.content,
              contactName: values.contactName,
              contactPhone: values.contactPhone,
              startTime: toBackendDateTime(values.startTime),
              endTime: toBackendDateTime(values.endTime),
              signupStartTime: toBackendDateTime(values.signupStartTime),
              signupEndTime: toBackendDateTime(values.signupEndTime),
            }),
        },
        'activity-category': {
          create: () => createCategory({ name: values.name, sort: values.sort, status: values.status }),
          edit: () => updateCategory(editingRecord.id, { name: values.name, sort: values.sort, status: values.status }),
        },
        'forum-topic': {
          create: () => createTopic(values),
          edit: () => updateTopic(editingRecord.id, values),
        },
        'facility-sports': {
          create: () => createFacility(values),
          edit: () => updateFacility(editingRecord.id, values),
        },
        'facility-teaching': {
          create: () => createFacility(values),
          edit: () => updateFacility(editingRecord.id, values),
        },
        'facility-dormitory': {
          create: () => createFacility(values),
          edit: () => updateFacility(editingRecord.id, values),
        },
        'facility-restaurant': {
          create: () => createStall(values),
          edit: () => updateStall(editingRecord.id, values),
        },
        'market-category': {
          create: () => createSecondhandCategory(values),
          edit: () => updateSecondhandCategory(editingRecord.id, values),
        },
        'discount-category': {
          create: () => createMerchantCategory(values),
          edit: () => updateMerchantCategory(editingRecord.id, values),
        },
        'discount-merchant': {
          create: () => createMerchant(values),
          edit: () => updateMerchant(editingRecord.id, values),
        },
        'facility-stall-dish': {
          create: () => createDish(values),
          edit: () => updateDish(editingRecord.id, values),
        },
      }
      const entry = actionMap[pageKey]
      if (!entry) return
      await runAction(modalMode === 'create' ? entry.create : entry.edit, modalMode === 'create' ? '创建成功' : '更新成功')
      setModalOpen(false)
      form.resetFields()
    } catch (error) {
      if (!error?.errorFields) {
        message.error(error?.message || '提交失败')
      }
    }
  }

  const renderModalFields = () => {
    switch (pageKey) {
      case 'activity-center':
        return (
          <>
            <Form.Item name="title" label="活动标题" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="categoryId" label="活动分类" rules={[{ required: true }]}>
              <Select options={activityCategoryOptions} />
            </Form.Item>
            <Form.Item name="location" label="活动地点" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="maxPeople" label="人数上限" rules={[{ required: true }]}>
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="startTime" label="开始时间" rules={[{ required: true }]}>
              <Input type="datetime-local" />
            </Form.Item>
            <Form.Item name="endTime" label="结束时间" rules={[{ required: true }]}>
              <Input type="datetime-local" />
            </Form.Item>
            <Form.Item name="signupStartTime" label="报名开始时间" rules={[{ required: true }]}>
              <Input type="datetime-local" />
            </Form.Item>
            <Form.Item name="signupEndTime" label="报名截止时间" rules={[{ required: true }]}>
              <Input type="datetime-local" />
            </Form.Item>
            <Form.Item name="contactName" label="联系人" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="contactPhone" label="联系电话" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="content" label="活动详情" rules={[{ required: true }]}>
              <Input.TextArea rows={4} />
            </Form.Item>
          </>
        )
      case 'activity-category':
        return (
          <>
            <Form.Item name="name" label="分类名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="sort" label="排序">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
            </Form.Item>
          </>
        )
      case 'forum-topic':
        return (
          <>
            <Form.Item name="topicName" label="话题名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="topicIcon" label="图标地址">
              <Input />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="isHot" label="是否热门">
              <Select options={[{ value: 1, label: '是' }, { value: 0, label: '否' }]} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select options={[{ value: 'ACTIVE', label: '启用' }, { value: 'INACTIVE', label: '禁用' }]} />
            </Form.Item>
          </>
        )
      case 'facility-restaurant':
        return (
          <>
            <Form.Item name="stallName" label="档口名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="restaurantId" label="所属餐厅" rules={[{ required: true }]}>
              <Select options={[
                { value: 1, label: '第一学生餐厅' },
                { value: 2, label: '第二学生餐厅' },
                { value: 3, label: '清真餐厅' },
              ]} />
            </Form.Item>
            <Form.Item name="floor" label="楼层">
              <Input />
            </Form.Item>
            <Form.Item name="category" label="品类">
              <Input />
            </Form.Item>
            <Form.Item name="location" label="位置">
              <Input />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="image" label="档口照片">
              <Input placeholder="请输入档口图片 URL" />
            </Form.Item>
            <div className="workspace-image-editor">
              <span className="workspace-image-editor__label">照片预览</span>
              {stallImagePreview ? (
                <Image
                  src={stallImagePreview}
                  alt="档口照片预览"
                  className="workspace-image-editor__preview"
                />
              ) : (
                <div className="workspace-image-editor__empty">输入图片地址后可在这里预览</div>
              )}
            </div>
          </>
        )
      case 'facility-sports':
      case 'facility-teaching':
      case 'facility-dormitory':
        return (
          <>
            <Form.Item name="facilityName" label="设施名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="facilityType" label="设施类型" rules={[{ required: true }]}>
              <Select options={[{ value: 1, label: '餐厅' }, { value: 2, label: '运动场' }, { value: 3, label: '教学楼' }, { value: 4, label: '宿舍' }]} />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="location" label="位置">
              <Input />
            </Form.Item>
            <Form.Item name="longitude" label="经度" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="latitude" label="纬度" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="images" label="图片(JSON数组字符串)">
              <Input.TextArea rows={2} />
            </Form.Item>
          </>
        )
      case 'market-category':
      case 'discount-category':
        return (
          <>
            <Form.Item name="categoryName" label="分类名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="sort" label="排序">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </>
        )
      case 'discount-merchant':
        return (
          <>
            <Form.Item name="merchantName" label="商家名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="categoryId" label="分类" rules={[{ required: true }]}>
              <Select options={merchantCategoryOptions.map((item) => ({ value: item.id, label: item.categoryName }))} />
            </Form.Item>
            <Form.Item name="description" label="介绍">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="address" label="地址" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="contactName" label="联系人">
              <Input />
            </Form.Item>
            <Form.Item name="contactPhone" label="联系电话" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="businessHours" label="营业时间">
              <Input />
            </Form.Item>
            <Form.Item name="username" label="账号" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="password" label="密码" rules={[{ required: true }]}>
              <Input.Password />
            </Form.Item>
          </>
        )
      case 'facility-stall-dish':
        return (
          <>
            <Form.Item name="stallId" label="所属档口" rules={[{ required: true }]}>
              <Input disabled />
            </Form.Item>
            <Form.Item name="name" label="菜品名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="price" label="价格" rules={[{ required: true }]}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="category" label="分类" rules={[{ required: true }]}>
              <Select options={[
                { value: '早餐', label: '早餐' },
                { value: '面食', label: '面食' },
                { value: '米饭', label: '米饭' },
                { value: '小吃', label: '小吃' },
                { value: '饮品', label: '饮品' },
                { value: '麻辣烫', label: '麻辣烫' },
                { value: '自选快餐', label: '自选快餐' },
              ]} />
            </Form.Item>
            <Form.Item name="taste" label="口味">
              <Select options={[
                { value: '清淡', label: '清淡' },
                { value: '麻辣', label: '麻辣' },
                { value: '酸辣', label: '酸辣' },
                { value: '甜味', label: '甜味' },
                { value: '咸味', label: '咸味' },
              ]} />
            </Form.Item>
            <Form.Item name="imageUrl" label="图片地址">
              <Input placeholder="请输入菜品图片 URL" />
            </Form.Item>
            <Form.Item name="isAvailable" label="状态" rules={[{ required: true }]}>
              <Select options={[{ value: true, label: '上架' }, { value: false, label: '下架' }]} />
            </Form.Item>
            <div className="workspace-image-editor">
              <span className="workspace-image-editor__label">菜品图片预览</span>
              {dishImagePreview ? (
                <Image
                  src={dishImagePreview}
                  alt="菜品图片预览"
                  className="workspace-image-editor__preview"
                />
              ) : (
                <div className="workspace-image-editor__empty">输入图片地址后可在这里预览</div>
              )}
            </div>
          </>
        )
      default:
        return null
    }
  }

  const renderRowActions = (record) => {
    switch (pageKey) {
      case 'user-manage':
        return (
          <Space size="small">
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => enableUser(record.id), '已启用用户')}>
              启用
            </Button>
            <Button size="small" danger loading={actionLoading} onClick={() => runAction(() => disableUser(record.id), '已禁用用户')}>
              禁用
            </Button>
          </Space>
        )
      case 'activity-center':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => publishActivity(record.id), '活动已发布')}>
              发布
            </Button>
            <Popconfirm title="确定删除该活动吗？" onConfirm={() => runAction(() => deleteActivity(record.id), '活动已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'activity-audit':
        return (
          <Space size="small">
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => auditRegistration(record.id, 'APPROVED'), '审核通过')}>
              通过
            </Button>
            <Button size="small" danger loading={actionLoading} onClick={() => runAction(() => auditRegistration(record.id, 'REJECTED'), '已拒绝')}>
              拒绝
            </Button>
          </Space>
        )
      case 'forum-post':
        return (
          <Popconfirm title="确定删除该帖子吗？" onConfirm={() => runAction(() => adminDeletePost(record.id), '帖子已删除')}>
            <Button size="small" danger loading={actionLoading}>
              删除
            </Button>
          </Popconfirm>
        )
      case 'forum-comment':
        return (
          <Popconfirm title="确定删除该评论吗？" onConfirm={() => runAction(() => adminDeleteComment(record.id), '评论已删除')}>
            <Button size="small" danger loading={actionLoading}>
              删除
            </Button>
          </Popconfirm>
        )
      case 'forum-topic':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Popconfirm title="确定删除该话题吗？" onConfirm={() => runAction(() => deleteTopic(record.id), '话题已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'activity-category':
        return <Button size="small" onClick={() => openEditModal(record)}>编辑</Button>
      case 'facility-restaurant':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Button
              size="small"
              onClick={() => {
                navigate(`/facility/stall-dish?stallId=${record.id}&stallName=${encodeURIComponent(record.stallName || '未知档口')}`)
              }}
            >
              查看菜品
            </Button>
            <Popconfirm title="确定删除该档口吗？" onConfirm={() => runAction(() => deleteStall(record.id), '档口已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'map-marker':
        return (
          <Popconfirm title="确定删除该标记吗？" onConfirm={() => runAction(() => deleteMarker(record.id), '标记已删除')}>
            <Button size="small" danger loading={actionLoading}>
              删除
            </Button>
          </Popconfirm>
        )
      case 'market-item':
      case 'market-audit':
        return (
          <Space size="small">
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => onlineSecondhandItem(record.id), '物品已上架')}>
              上架
            </Button>
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => offlineSecondhandItem(record.id), '物品已下架')}>
              下架
            </Button>
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => markSecondhandItemSold(record.id), '物品已标记售出')}>
              售出
            </Button>
            <Popconfirm title="确定删除该物品吗？" onConfirm={() => runAction(() => deleteSecondhandItem(record.id), '物品已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'market-category':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Popconfirm title="确定删除该分类吗？" onConfirm={() => runAction(() => deleteSecondhandCategory(record.id), '分类已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'discount-category':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Popconfirm title="确定删除该分类吗？" onConfirm={() => runAction(() => deleteMerchantCategory(record.id), '分类已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'discount-merchant':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Popconfirm title="确定删除该商家吗？" onConfirm={() => runAction(() => deleteMerchant(record.id), '商家已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'facility-stall-dish':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Popconfirm title="确定删除该菜品吗？" onConfirm={() => runAction(() => deleteDish(record.id), '菜品已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      default:
        return null
    }
  }

  const columns = useMemo(() => {
    if (!page?.columns?.length) return []
    const baseColumns = page.columns.map((column) => ({
      title: column.title,
      dataIndex: column.dataIndex,
      key: column.dataIndex,
      render: (value, record) => renderCell(value, column.type, record),
    }))
    const hasActions = [
      'user-manage',
      'activity-center',
      'activity-category',
      'activity-audit',
      'forum-post',
      'forum-comment',
      'forum-topic',
      'facility-restaurant',
      'facility-sports',
      'facility-teaching',
      'facility-dormitory',
      'facility-stall-dish',
      'map-marker',
      'market-item',
      'market-audit',
      'market-category',
      'discount-category',
      'discount-merchant',
    ].includes(pageKey)

    if (!hasActions) return baseColumns

    return [
      ...baseColumns,
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => renderRowActions(record),
      },
    ]
  }, [page, pageKey, actionLoading])

  if (!page) {
    return <Empty description="页面配置不存在" />
  }

  return (
    <div className="workspace-page">
      <section className="workspace-hero">
        <div>
          <span className="workspace-badge">{page.badge}</span>
          <h1>{pageKey === 'facility-stall-dish' && urlStallName ? `${page.title} - ${urlStallName}` : page.title}</h1>
          <p>{page.description}</p>
          {pageKey === 'facility-stall-dish' ? (
            <p>当前档口 ID：{urlStallId || '未获取到'}</p>
          ) : null}
        </div>
      </section>

      <section className="workspace-main workspace-main-single">
        <Card
          className="workspace-table-card"
          extra={
            columns.length ? (
              <div className="workspace-filters">
                <Input
                  allowClear
                  placeholder="搜索关键字"
                  prefix={<SearchOutlined />}
                  value={keyword}
                  onChange={(event) => {
                    setPagination((prev) => ({ ...prev, current: 1 }))
                    setKeyword(event.target.value)
                  }}
                />
                <Select
                  value="全部"
                  disabled
                  options={page.filters.status.map((item) => ({ value: item, label: item }))}
                />
                {pageKey === 'activity-signin' && contextId ? (
                  <>
                    <Button size="small" loading={actionLoading} onClick={() => runAction(() => openSignIn(contextId), '签到已开启')}>
                      开启签到
                    </Button>
                    <Button size="small" loading={actionLoading} onClick={() => runAction(() => closeSignIn(contextId), '签到已关闭')}>
                      关闭签到
                    </Button>
                  </>
                ) : null}
                {formEnabledPages.includes(pageKey) ? (
                  <Button type="primary" onClick={openCreateModal}>
                    新增
                  </Button>
                ) : null}
              </div>
            ) : null
          }
        >
          {columns.length ? (
            <Table
              columns={columns}
              dataSource={rows}
              loading={loading}
              rowKey={(record) => record.id || record.key || JSON.stringify(record)}
              locale={{ emptyText: page.emptyText }}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: pagination.total,
                showSizeChanger: true,
              }}
              onChange={(nextPagination) => {
                setPagination((prev) => ({
                  ...prev,
                  current: nextPagination.current,
                  pageSize: nextPagination.pageSize,
                }))
              }}
            />
          ) : (
            <Empty description={page.emptyText} />
          )}
        </Card>
      </section>

      <Modal
        open={modalOpen}
        title={modalMode === 'create' ? `新增${page.title}` : `编辑${page.title}`}
        onCancel={() => setModalOpen(false)}
        onOk={submitModal}
        confirmLoading={actionLoading}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          {renderModalFields()}
        </Form>
      </Modal>
    </div>
  )
}

export default WorkspacePage
