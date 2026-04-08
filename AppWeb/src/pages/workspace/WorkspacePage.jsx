import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Card, Empty, Form, Image, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Upload, message } from 'antd'
import { SearchOutlined, UploadOutlined } from '@ant-design/icons'
import { createActivity, deleteActivity, getActivityList, publishActivity, updateActivity } from '../../api/activity'
import { createCategory, getCategoryList, updateCategory } from '../../api/category'
import { getDiscountActivityList } from '../../api/discount'
import { createFacility, deleteFacility, getFacilityList, updateFacility } from '../../api/facility'
import { createDish, createStall, deleteDish, deleteStall, getCanteenStallList, getDishList, updateDish, updateStall } from '../../api/dish'
import { adminDeleteComment, adminDeletePost, createTopic, deleteTopic, getCommentList, getPostList, getTopicList, updateTopic } from '../../api/forum'
import { deleteMarker, getFacilityHeat, getMapConfig, getMarkerList, getNavigationStatistics, updateMapConfig } from '../../api/map'
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
import { getUploadUrl } from '../../api/upload'
import { disableUser, enableUser, getUserList } from '../../api/user'
import { getWorkspacePage } from '../../data/portalData'
import './WorkspacePage.css'

const parseJsonText = (value, fallback) => {
  try {
    if (!value || !String(value).trim()) return fallback
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

const normalizePointNumber = (value) => {
  const numeric = Number(value)
  if (Number.isNaN(numeric)) return 0
  return Math.max(0, Math.min(1, Number(numeric.toFixed(6))))
}

const toFiniteNumber = (value) => {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : null
}

const roundCoordinate = (value) => {
  const numeric = toFiniteNumber(value)
  return numeric === null ? '' : String(Number(numeric.toFixed(7)))
}

const solve3x3Linear = (matrix, vector) => {
  const a = matrix.map((row, index) => [...row, vector[index]])
  for (let col = 0; col < 3; col += 1) {
    let pivot = col
    for (let row = col + 1; row < 3; row += 1) {
      if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
        pivot = row
      }
    }
    if (Math.abs(a[pivot][col]) < 1e-10) return null
    if (pivot !== col) {
      const temp = a[pivot]
      a[pivot] = a[col]
      a[col] = temp
    }
    const factor = a[col][col]
    for (let j = col; j < 4; j += 1) {
      a[col][j] /= factor
    }
    for (let row = 0; row < 3; row += 1) {
      if (row === col) continue
      const ratio = a[row][col]
      for (let j = col; j < 4; j += 1) {
        a[row][j] -= ratio * a[col][j]
      }
    }
  }
  return [a[0][3], a[1][3], a[2][3]]
}

const solveAffineCoefficients = (points, targetKey) => {
  if (!Array.isArray(points) || points.length < 3) return null
  const design = points.map((point) => [
    toFiniteNumber(point.longitude),
    toFiniteNumber(point.latitude),
    1,
  ])
  const target = points.map((point) => toFiniteNumber(point[targetKey]))
  if (design.some((row) => row[0] === null || row[1] === null) || target.some((value) => value === null)) {
    return null
  }
  const ata = [
    [0, 0, 0],
    [0, 0, 0],
    [0, 0, 0],
  ]
  const atb = [0, 0, 0]
  for (let i = 0; i < design.length; i += 1) {
    for (let row = 0; row < 3; row += 1) {
      atb[row] += design[i][row] * target[i]
      for (let col = 0; col < 3; col += 1) {
        ata[row][col] += design[i][row] * design[i][col]
      }
    }
  }
  return solve3x3Linear(ata, atb)
}

const estimateGeoByImagePoint = (imageX, imageY, mapConfigForm) => {
  const x = toFiniteNumber(imageX)
  const y = toFiniteNumber(imageY)
  if (x === null || y === null) return null

  if (mapConfigForm.calibrationMode === 'controlPoints') {
    const controlPoints = parseJsonText(mapConfigForm.controlPoints, [])
    const xCoeff = solveAffineCoefficients(controlPoints, 'imageX')
    const yCoeff = solveAffineCoefficients(controlPoints, 'imageY')
    if (xCoeff && yCoeff) {
      const determinant = xCoeff[0] * yCoeff[1] - xCoeff[1] * yCoeff[0]
      if (Math.abs(determinant) > 1e-10) {
        const rhsX = x - xCoeff[2]
        const rhsY = y - yCoeff[2]
        const longitude = (rhsX * yCoeff[1] - xCoeff[1] * rhsY) / determinant
        const latitude = (xCoeff[0] * rhsY - rhsX * yCoeff[0]) / determinant
        return {
          longitude: roundCoordinate(longitude),
          latitude: roundCoordinate(latitude),
        }
      }
    }
  }

  const boundary = parseJsonText(mapConfigForm.boundary, null)
  const northEast = boundary?.northEast
  const southWest = boundary?.southWest
  const minLng = toFiniteNumber(southWest?.longitude)
  const maxLng = toFiniteNumber(northEast?.longitude)
  const minLat = toFiniteNumber(southWest?.latitude)
  const maxLat = toFiniteNumber(northEast?.latitude)
  if ([minLng, maxLng, minLat, maxLat].some((value) => value === null)) return null
  if (maxLng === minLng || maxLat === minLat) return null
  const longitude = minLng + x * (maxLng - minLng)
  const latitude = maxLat - y * (maxLat - minLat)
  return {
    longitude: roundCoordinate(longitude),
    latitude: roundCoordinate(latitude),
  }
}

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
  const [mapConfigForm, setMapConfigForm] = useState({
    mapImageUrl: '',
    centerLongitude: '',
    centerLatitude: '',
    zoomLevel: 16,
    calibrationMode: 'boundary',
    boundary: '',
    controlPoints: '[]',
  })
  const [mapConfigSaving, setMapConfigSaving] = useState(false)
  const [mapImageUploading, setMapImageUploading] = useState(false)
  const [showAdvancedCalibration, setShowAdvancedCalibration] = useState(false)
  const [selectedMarkerId, setSelectedMarkerId] = useState(null)
  const [markerEditorOpen, setMarkerEditorOpen] = useState(false)
  const [markerEditorMode, setMarkerEditorMode] = useState('create')
  const [markerEditorSaving, setMarkerEditorSaving] = useState(false)
  const [markerDraft, setMarkerDraft] = useState({
    facilityName: '',
    facilityType: 1,
    location: '',
    description: '',
    status: 1,
    longitude: '',
    latitude: '',
    imageX: '',
    imageY: '',
  })
  const [controlPointDraft, setControlPointDraft] = useState({
    name: '',
    imageX: '',
    imageY: '',
    longitude: '',
    latitude: '',
  })
  const mapPreviewImageRef = useRef(null)
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

  useEffect(() => {
    if (!['map-config', 'map-marker'].includes(pageKey)) return

    let cancelled = false
    const loadMapConfig = async () => {
      try {
        const res = await getMapConfig()
        if (cancelled) return
        const data = res.data || {}
        setMapConfigForm({
          mapImageUrl: data.mapImageUrl || '',
          centerLongitude: data.centerLongitude ?? '',
          centerLatitude: data.centerLatitude ?? '',
          zoomLevel: data.zoomLevel ?? 16,
          calibrationMode: data.calibrationMode || 'boundary',
          boundary: data.boundary ? JSON.stringify(data.boundary, null, 2) : '',
          controlPoints: JSON.stringify(data.controlPoints || [], null, 2),
        })
      } catch (error) {
        if (!cancelled) {
          message.error(error?.message || '加载地图配置失败')
        }
      }
    }

    loadMapConfig()
    return () => {
      cancelled = true
    }
  }, [pageKey])

  useEffect(() => {
    if (pageKey !== 'map-marker') return
    if (!rows.length) {
      setSelectedMarkerId(null)
      return
    }
    if (!rows.some((item) => item.id === selectedMarkerId)) {
      setSelectedMarkerId(rows[0]?.id ?? null)
    }
  }, [pageKey, rows, selectedMarkerId])

  const refreshPageData = async () => {
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
  }

  const runAction = async (fn, successText) => {
    setActionLoading(true)
    try {
      await fn()
      message.success(successText)
      await refreshPageData()
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

  const controlPoints = parseJsonText(mapConfigForm.controlPoints, [])
  const draftPointReady = controlPointDraft.imageX !== '' && controlPointDraft.imageY !== ''

  const renderMapConfigPanel = () => (
    <Card className="workspace-table-card">
      <div className="workspace-map-config">
        <div className="workspace-map-config__preview">
          <div className="workspace-map-config__preview-head">
            <h3>地图底图</h3>
            <p>上传后作为地图底图使用。</p>
          </div>
          <Input
            value={mapConfigForm.mapImageUrl}
            placeholder="上传后会自动填入图片地址"
            readOnly
            style={{ marginBottom: 12 }}
          />
          <Upload
            name="file"
            action={getUploadUrl()}
            headers={{ Authorization: `Bearer ${localStorage.getItem('token') || ''}` }}
            showUploadList={false}
            onChange={({ file }) => {
              if (file.status === 'uploading') {
                setMapImageUploading(true)
                return
              }
              if (file.status === 'done') {
                setMapImageUploading(false)
                const url = file.response?.data?.url
                if (url) {
                  setMapConfigForm((prev) => ({ ...prev, mapImageUrl: url }))
                  message.success('底图上传成功')
                } else {
                  message.error('上传返回内容异常')
                }
              }
              if (file.status === 'error') {
                setMapImageUploading(false)
                message.error('底图上传失败')
              }
            }}
          >
            <Button icon={<UploadOutlined />} loading={mapImageUploading}>
              上传地图图片
            </Button>
          </Upload>
          <div className="workspace-map-config__image-shell">
            {mapConfigForm.mapImageUrl ? (
              <div
                className="workspace-map-config__image-stage"
                onClick={(event) => {
                  if (!showAdvancedCalibration) return
                  const imageElement = mapPreviewImageRef.current
                  const rect = imageElement?.getBoundingClientRect()
                  if (!rect?.width || !rect?.height) return
                  const offsetX = event.clientX - rect.left
                  const offsetY = event.clientY - rect.top
                  if (offsetX < 0 || offsetY < 0 || offsetX > rect.width || offsetY > rect.height) return
                  const imageX = normalizePointNumber(offsetX / rect.width)
                  const imageY = normalizePointNumber(offsetY / rect.height)
                  setControlPointDraft((prev) => ({
                    ...prev,
                    imageX: imageX.toFixed(6),
                    imageY: imageY.toFixed(6),
                  }))
                }}
              >
                <img
                  ref={mapPreviewImageRef}
                  src={mapConfigForm.mapImageUrl}
                  alt="地图底图预览"
                  className="workspace-map-config__image"
                />
                {showAdvancedCalibration && draftPointReady ? (
                  <div
                    className="workspace-map-config__marker workspace-map-config__marker--draft"
                    style={{
                      left: `${Number(controlPointDraft.imageX || 0) * 100}%`,
                      top: `${Number(controlPointDraft.imageY || 0) * 100}%`,
                    }}
                    title={`当前取点 (${controlPointDraft.imageX}, ${controlPointDraft.imageY})`}
                  >
                    <span>+</span>
                  </div>
                ) : null}
                {showAdvancedCalibration && Array.isArray(controlPoints) && controlPoints.map((point, index) => (
                  <div
                    key={`${point.name || 'point'}-${index}`}
                    className="workspace-map-config__marker"
                    style={{
                      left: `${Number(point.imageX || 0) * 100}%`,
                      top: `${Number(point.imageY || 0) * 100}%`,
                    }}
                    title={`${point.name || `控制点${index + 1}`} (${point.imageX}, ${point.imageY})`}
                  >
                    <span>{index + 1}</span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="workspace-map-config__image-empty">上传后在这里预览地图底图</div>
            )}
          </div>
          <div className="workspace-map-config__actions">
            <Button
              type="primary"
              loading={mapConfigSaving}
              onClick={async () => {
                try {
                  setMapConfigSaving(true)
                  await updateMapConfig({
                    mapImageUrl: mapConfigForm.mapImageUrl || '',
                  })
                  message.success('地图底图保存成功')
                } catch (error) {
                  message.error(error?.message || '地图底图保存失败')
                } finally {
                  setMapConfigSaving(false)
                }
              }}
            >
              保存地图底图
            </Button>
            <Button
              onClick={() => setShowAdvancedCalibration((prev) => !prev)}
            >
              {showAdvancedCalibration ? '收起高级标定' : '进入高级标定'}
            </Button>
          </div>
        </div>
        {showAdvancedCalibration ? (
          <div className="workspace-map-config__form">
            <div className="workspace-map-config__advanced">
              <div className="workspace-map-config__advanced-head">
                <h4>高级标定</h4>
                <p>需要时再维护中心点、边界和控制点。点击左侧底图可录入控制点图坐标。</p>
              </div>
              <div className="workspace-map-config__grid">
                <div>
                  <label>中心经度</label>
                  <Input
                    value={mapConfigForm.centerLongitude}
                    onChange={(event) => setMapConfigForm((prev) => ({ ...prev, centerLongitude: event.target.value }))}
                    placeholder="例如 116.397428"
                  />
                </div>
                <div>
                  <label>中心纬度</label>
                  <Input
                    value={mapConfigForm.centerLatitude}
                    onChange={(event) => setMapConfigForm((prev) => ({ ...prev, centerLatitude: event.target.value }))}
                    placeholder="例如 39.909500"
                  />
                </div>
                <div>
                  <label>默认缩放</label>
                  <InputNumber
                    min={1}
                    max={20}
                    style={{ width: '100%' }}
                    value={mapConfigForm.zoomLevel}
                    onChange={(value) => setMapConfigForm((prev) => ({ ...prev, zoomLevel: value ?? 16 }))}
                  />
                </div>
                <div>
                  <label>标定模式</label>
                  <Select
                    value={mapConfigForm.calibrationMode}
                    options={[
                      { value: 'boundary', label: '边界坐标' },
                      { value: 'controlPoints', label: '控制点标定' },
                    ]}
                    onChange={(value) => setMapConfigForm((prev) => ({ ...prev, calibrationMode: value }))}
                  />
                </div>
              </div>

              <div className="workspace-map-config__field">
                <label>边界坐标 JSON</label>
                <Input.TextArea
                  rows={8}
                  value={mapConfigForm.boundary}
                  onChange={(event) => setMapConfigForm((prev) => ({ ...prev, boundary: event.target.value }))}
                  placeholder={`未配置时这里为空。\n如需边界标定，请手动填写：\n{\n  "northEast": { "longitude": 116.41, "latitude": 39.92 },\n  "southWest": { "longitude": 116.38, "latitude": 39.89 }\n}`}
                />
              </div>

              <div className="workspace-map-config__field">
                <label>控制点录入</label>
                <div className="workspace-map-config__grid">
                  <div>
                    <label>点位名称</label>
                    <Input
                      value={controlPointDraft.name}
                      onChange={(event) => setControlPointDraft((prev) => ({ ...prev, name: event.target.value }))}
                      placeholder="例如 南门"
                    />
                  </div>
                  <div>
                    <label>经度</label>
                    <Input
                      value={controlPointDraft.longitude}
                      onChange={(event) => setControlPointDraft((prev) => ({ ...prev, longitude: event.target.value }))}
                      placeholder="点击地图后再填经度"
                    />
                  </div>
                  <div>
                    <label>纬度</label>
                    <Input
                      value={controlPointDraft.latitude}
                      onChange={(event) => setControlPointDraft((prev) => ({ ...prev, latitude: event.target.value }))}
                      placeholder="点击地图后再填纬度"
                    />
                  </div>
                  <div>
                    <label>图片坐标</label>
                    <Input
                      value={draftPointReady ? `${controlPointDraft.imageX}, ${controlPointDraft.imageY}` : ''}
                      readOnly
                      placeholder="点击左侧地图自动取点"
                    />
                  </div>
                </div>
                <Space style={{ marginBottom: 16 }}>
                  <Button
                    onClick={() => {
                      const nextPoint = {
                        name: controlPointDraft.name || `控制点${controlPoints.length + 1}`,
                        imageX: normalizePointNumber(controlPointDraft.imageX),
                        imageY: normalizePointNumber(controlPointDraft.imageY),
                        longitude: Number(controlPointDraft.longitude),
                        latitude: Number(controlPointDraft.latitude),
                      }
                      if (
                        Number.isNaN(nextPoint.longitude) ||
                        Number.isNaN(nextPoint.latitude) ||
                        controlPointDraft.imageX === '' ||
                        controlPointDraft.imageY === ''
                      ) {
                        message.warning('请先在左侧地图点击取点，并填写该点的经纬度')
                        return
                      }
                      const nextPoints = [...(Array.isArray(controlPoints) ? controlPoints : []), nextPoint]
                      setMapConfigForm((prev) => ({
                        ...prev,
                        controlPoints: JSON.stringify(nextPoints, null, 2),
                      }))
                      setControlPointDraft({
                        name: '',
                        imageX: '',
                        imageY: '',
                        longitude: '',
                        latitude: '',
                      })
                    }}
                  >
                    添加控制点
                  </Button>
                  <Button
                    onClick={() => {
                      setControlPointDraft({
                        name: '',
                        imageX: '',
                        imageY: '',
                        longitude: '',
                        latitude: '',
                      })
                    }}
                  >
                    清空当前取点
                  </Button>
                </Space>
                <div className="workspace-map-config__point-list">
                  {draftPointReady ? (
                    <div className="workspace-map-config__point-tip">
                      已在左侧地图选中一个待保存点：图坐标 {Number(controlPointDraft.imageX || 0).toFixed(6)}, {Number(controlPointDraft.imageY || 0).toFixed(6)}
                    </div>
                  ) : null}
                  {Array.isArray(controlPoints) && controlPoints.length ? controlPoints.map((point, index) => (
                    <div key={`${point.name || 'point'}-${index}`} className="workspace-map-config__point-item">
                      <div>
                        <strong>{point.name || `控制点${index + 1}`}</strong>
                        <span>
                          图坐标 {Number(point.imageX || 0).toFixed(6)}, {Number(point.imageY || 0).toFixed(6)} | 经纬度 {point.longitude}, {point.latitude}
                        </span>
                      </div>
                      <Button
                        size="small"
                        danger
                        onClick={() => {
                          const nextPoints = controlPoints.filter((_, pointIndex) => pointIndex !== index)
                          setMapConfigForm((prev) => ({
                            ...prev,
                            controlPoints: JSON.stringify(nextPoints, null, 2),
                          }))
                        }}
                      >
                        删除
                      </Button>
                    </div>
                  )) : (
                    <div className="workspace-map-config__point-empty">还没有控制点，先点击左侧地图选点。</div>
                  )}
                </div>
                <Input.TextArea
                  rows={8}
                  value={mapConfigForm.controlPoints}
                  onChange={(event) => setMapConfigForm((prev) => ({ ...prev, controlPoints: event.target.value }))}
                  placeholder="控制点 JSON 会随着上面的操作自动生成，也可以手动微调。"
                  style={{ marginTop: 12 }}
                />
              </div>

              <div className="workspace-map-config__tips">
                <div>边界坐标用于简单线性标定，适合规则平面图。</div>
                <div>控制点至少维护 3 个，推荐 4 个，适合后续做更精确的图片坐标映射。</div>
              </div>
              <div className="workspace-map-config__actions">
                <Button
                  loading={mapConfigSaving}
                  onClick={async () => {
                    try {
                      const boundaryParsed = mapConfigForm.boundary?.trim() ? JSON.parse(mapConfigForm.boundary) : null
                      const controlPointsParsed = mapConfigForm.controlPoints?.trim() ? JSON.parse(mapConfigForm.controlPoints) : []
                      setMapConfigSaving(true)
                      await updateMapConfig({
                        centerLongitude: mapConfigForm.centerLongitude === '' ? null : Number(mapConfigForm.centerLongitude),
                        centerLatitude: mapConfigForm.centerLatitude === '' ? null : Number(mapConfigForm.centerLatitude),
                        zoomLevel: mapConfigForm.zoomLevel ?? 16,
                        calibrationMode: mapConfigForm.calibrationMode,
                        boundary: boundaryParsed ? JSON.stringify(boundaryParsed) : '',
                        controlPoints: JSON.stringify(controlPointsParsed),
                      })
                      message.success('高级标定保存成功')
                    } catch (error) {
                      message.error(error instanceof SyntaxError ? '边界坐标或控制点 JSON 格式不正确' : (error?.message || '高级标定保存失败'))
                    } finally {
                      setMapConfigSaving(false)
                    }
                  }}
                >
                  保存高级标定
                </Button>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </Card>
  )

  const markerRows = Array.isArray(rows) ? rows.map((item) => ({
    ...item,
    position: item.longitude && item.latitude ? `${item.longitude}, ${item.latitude}` : '-',
  })) : []
  const markersWithImageCoords = markerRows.filter((item) => item.imageX !== null && item.imageX !== undefined && item.imageY !== null && item.imageY !== undefined)
  const markersWithoutImageCoords = markerRows.filter((item) => item.imageX === null || item.imageX === undefined || item.imageY === null || item.imageY === undefined)
  const selectedMarker = markerRows.find((item) => item.id === selectedMarkerId) || null

  const openMarkerCreate = () => {
    setMarkerEditorMode('create')
    setMarkerDraft({
      facilityName: '',
      facilityType: 1,
      location: '',
      description: '',
      status: 1,
      longitude: '',
      latitude: '',
      imageX: '',
      imageY: '',
    })
    setMarkerEditorOpen(true)
  }

  const openMarkerReposition = () => {
    if (!selectedMarker) {
      message.warning('请先在右侧表格中选中一个标记')
      return
    }
    setMarkerEditorMode('reposition')
    setMarkerDraft({
      facilityName: selectedMarker.markerName || '',
      facilityType: selectedMarker.facilityType || 1,
      location: selectedMarker.location || '',
      description: selectedMarker.description || '',
      status: selectedMarker.status || 1,
      longitude: selectedMarker.longitude ? String(selectedMarker.longitude) : '',
      latitude: selectedMarker.latitude ? String(selectedMarker.latitude) : '',
      imageX: selectedMarker.imageX ? String(selectedMarker.imageX) : '',
      imageY: selectedMarker.imageY ? String(selectedMarker.imageY) : '',
    })
    setMarkerEditorOpen(true)
  }

  const applyMarkerDraftFromMapPoint = (imageX, imageY) => {
    const estimated = estimateGeoByImagePoint(imageX, imageY, mapConfigForm)
    setMarkerDraft((prev) => ({
      ...prev,
      imageX: imageX.toFixed(6),
      imageY: imageY.toFixed(6),
      longitude: estimated?.longitude ?? prev.longitude,
      latitude: estimated?.latitude ?? prev.latitude,
    }))
  }

  const saveMarkerDraft = async () => {
    const longitude = toFiniteNumber(markerDraft.longitude)
    const latitude = toFiniteNumber(markerDraft.latitude)
    const imageX = toFiniteNumber(markerDraft.imageX)
    const imageY = toFiniteNumber(markerDraft.imageY)
    if (!markerDraft.facilityName.trim()) {
      message.warning('请填写标记名称')
      return
    }
    if (longitude === null || latitude === null) {
      message.warning('请填写有效的经纬度')
      return
    }
    if (imageX === null || imageY === null) {
      message.warning('请先在左侧底图上点击取点')
      return
    }
    setMarkerEditorSaving(true)
    try {
      const payload = {
        facilityName: markerDraft.facilityName.trim(),
        facilityType: markerDraft.facilityType,
        location: markerDraft.location,
        description: markerDraft.description,
        status: markerDraft.status,
        longitude,
        latitude,
        imageX,
        imageY,
        images: '[]',
      }
      await (markerEditorMode === 'create'
        ? createFacility(payload)
        : updateFacility(selectedMarker.facilityId, payload))
      await refreshPageData()
      setMarkerEditorOpen(false)
      message.success(markerEditorMode === 'create' ? '标点新增成功' : '标点位置已更新')
    } catch (error) {
      message.error(error?.message || (markerEditorMode === 'create' ? '标点新增失败' : '位置更新失败'))
    } finally {
      setMarkerEditorSaving(false)
    }
  }

  const renderMarkerManagePanel = () => (
    <div className="workspace-marker-layout">
      <Card className="workspace-marker-preview-card">
        <div className="workspace-map-config__preview-head">
          <h3>标点预览</h3>
          <p>左侧展示底图和已有标记点。新增或调整位置时，先点按钮，再点击底图取点。</p>
        </div>
        <div className="workspace-map-config__image-shell">
          {mapConfigForm.mapImageUrl ? (
            <div
              className={`workspace-map-config__image-stage workspace-map-config__image-stage--preview${markerEditorOpen ? ' workspace-map-config__image-stage--editing' : ''}`}
              onClick={(event) => {
                if (!markerEditorOpen) return
                const rect = event.currentTarget.getBoundingClientRect()
                if (!rect.width || !rect.height) return
                const imageX = normalizePointNumber((event.clientX - rect.left) / rect.width)
                const imageY = normalizePointNumber((event.clientY - rect.top) / rect.height)
                applyMarkerDraftFromMapPoint(imageX, imageY)
              }}
            >
              <img
                src={mapConfigForm.mapImageUrl}
                alt="地图标点预览"
                className="workspace-map-config__image"
              />
              {markerEditorOpen && markerDraft.imageX !== '' && markerDraft.imageY !== '' ? (
                <div
                  className="workspace-map-config__marker workspace-map-config__marker--draft"
                  style={{
                    left: `${Number(markerDraft.imageX || 0) * 100}%`,
                    top: `${Number(markerDraft.imageY || 0) * 100}%`,
                  }}
                  title="当前待保存位置"
                >
                  <span>+</span>
                </div>
              ) : null}
              {markersWithImageCoords.map((marker, index) => (
                <button
                  key={marker.id || `marker-${index}`}
                  type="button"
                  className={`workspace-map-preview__marker${marker.id === selectedMarkerId ? ' active' : ''}`}
                  style={{
                    left: `${Number(marker.imageX || 0) * 100}%`,
                    top: `${Number(marker.imageY || 0) * 100}%`,
                  }}
                  title={marker.markerName || `标记${index + 1}`}
                  onClick={() => setSelectedMarkerId(marker.id)}
                >
                  <span>{index + 1}</span>
                </button>
              ))}
            </div>
          ) : (
            <div className="workspace-map-config__image-empty">请先在地图配置中上传底图</div>
          )}
        </div>
        <div className="workspace-marker-preview-card__meta">
          <div>已显示标点 {markersWithImageCoords.length} 个</div>
          <div>未映射到底图 {markersWithoutImageCoords.length} 个</div>
        </div>
        {markersWithoutImageCoords.length ? (
          <div className="workspace-marker-preview-card__notice">
            以下标记还没有底图坐标，暂时不会显示在左侧预览中：
            {` ${markersWithoutImageCoords.map((item) => item.markerName).join('、')}`}
          </div>
        ) : null}
        <div className="workspace-map-config__actions">
          <Button type="primary" onClick={openMarkerCreate}>
            新增标点
          </Button>
          <Button onClick={openMarkerReposition} disabled={!selectedMarker}>
            设置选中位置
          </Button>
        </div>
      </Card>

      <Card
        className="workspace-table-card"
        extra={
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
          </div>
        }
      >
        {markerEditorOpen ? (
          <div className="workspace-marker-editor">
            <div className="workspace-marker-editor__head">
              <div>
                <h3>{markerEditorMode === 'create' ? '新增标点' : '设置标点位置'}</h3>
                <p>右侧填写信息，左侧地图点击取点。</p>
              </div>
              <Button onClick={() => setMarkerEditorOpen(false)}>
                收起
              </Button>
            </div>
            <Form layout="vertical">
              <Form.Item label="标记名称" required>
                <Input
                  value={markerDraft.facilityName}
                  onChange={(event) => setMarkerDraft((prev) => ({ ...prev, facilityName: event.target.value }))}
                  placeholder="例如 图书馆"
                />
              </Form.Item>
              <Form.Item label="设施类型" required>
                <Select
                  value={markerDraft.facilityType}
                  options={[
                    { value: 1, label: '餐厅' },
                    { value: 2, label: '运动场' },
                    { value: 3, label: '教学楼' },
                    { value: 4, label: '宿舍' },
                  ]}
                  onChange={(value) => setMarkerDraft((prev) => ({ ...prev, facilityType: value }))}
                />
              </Form.Item>
              <Form.Item label="位置说明">
                <Input
                  value={markerDraft.location}
                  onChange={(event) => setMarkerDraft((prev) => ({ ...prev, location: event.target.value }))}
                  placeholder="例如 南门东侧"
                />
              </Form.Item>
              <Form.Item label="描述">
                <Input.TextArea
                  rows={3}
                  value={markerDraft.description}
                  onChange={(event) => setMarkerDraft((prev) => ({ ...prev, description: event.target.value }))}
                  placeholder="可选"
                />
              </Form.Item>
              <div className="workspace-map-config__grid">
                <div>
                  <label>经度</label>
                  <Input
                    value={markerDraft.longitude}
                    onChange={(event) => setMarkerDraft((prev) => ({ ...prev, longitude: event.target.value }))}
                    placeholder="点击底图后自动推算，也可手填"
                  />
                </div>
                <div>
                  <label>纬度</label>
                  <Input
                    value={markerDraft.latitude}
                    onChange={(event) => setMarkerDraft((prev) => ({ ...prev, latitude: event.target.value }))}
                    placeholder="点击底图后自动推算，也可手填"
                  />
                </div>
                <div>
                  <label>图片横坐标</label>
                  <Input value={markerDraft.imageX} readOnly placeholder="点击左侧底图取点" />
                </div>
                <div>
                  <label>图片纵坐标</label>
                  <Input value={markerDraft.imageY} readOnly placeholder="点击左侧底图取点" />
                </div>
              </div>
              <div className="workspace-marker-editor__hint">
                先点击左侧地图取点。若高级标定已配置，系统会自动推算经纬度；否则请手动填写。
              </div>
              <div className="workspace-map-config__actions">
                <Button onClick={() => setMarkerEditorOpen(false)}>
                  取消
                </Button>
                <Button type="primary" loading={markerEditorSaving} onClick={saveMarkerDraft}>
                  确定
                </Button>
              </div>
            </Form>
          </div>
        ) : null}
        <Table
          columns={columns}
          dataSource={markerRows}
          loading={loading}
          rowKey={(record) => record.id || record.key || JSON.stringify(record)}
          locale={{ emptyText: page.emptyText }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
          }}
          onRow={(record) => ({
            onClick: () => setSelectedMarkerId(record.id),
            className: record.id === selectedMarkerId ? 'workspace-table-row--active' : '',
          })}
          onChange={(nextPagination) => {
            setPagination((prev) => ({
              ...prev,
              current: nextPagination.current,
              pageSize: nextPagination.pageSize,
            }))
          }}
        />
      </Card>
    </div>
  )

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
        {pageKey === 'map-config' ? renderMapConfigPanel() : pageKey === 'map-marker' ? renderMarkerManagePanel() : (
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
        )}
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
