import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Card, Empty, Form, Image, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Upload, message } from 'antd'
import { SearchOutlined, UploadOutlined } from '@ant-design/icons'
import * as echarts from 'echarts'
import { createActivity, deleteActivity, getActivityList, publishActivity, updateActivity } from '../../api/activity'
import { createCategory, getCategoryList, updateCategory } from '../../api/category'
import { getDiscountActivityList } from '../../api/discount'
import { createFacility, deleteFacility, getFacilityList, updateFacility } from '../../api/facility'
import { createDish, createStall, deleteDish, deleteStall, getCanteenStallList, getDishList, updateDish, updateStall } from '../../api/dish'
import { adminDeleteComment, createTopic, deleteTopic, getCommentList, getPostList, getTopicList, updateTopic } from '../../api/forum'
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
import { getSystemConfigList, testSystemConfig, updateSystemConfig } from '../../api/systemConfig'
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

const estimateGeoByImagePoint = (imageX, imageY, mapConfigForm) => {
  const x = toFiniteNumber(imageX)
  const y = toFiniteNumber(imageY)
  if (x === null || y === null) return null

  const controlPoints = parseJsonText(mapConfigForm.controlPoints, [])
    .map((point) => ({
      imageX: toFiniteNumber(point.imageX),
      imageY: toFiniteNumber(point.imageY),
      longitude: toFiniteNumber(point.longitude),
      latitude: toFiniteNumber(point.latitude),
    }))
    .filter((point) => (
      point.imageX !== null &&
      point.imageY !== null &&
      point.longitude !== null &&
      point.latitude !== null
    ))

  if (controlPoints.length < 3) return null

  const withDistance = controlPoints
    .map((point) => {
      const dx = x - point.imageX
      const dy = y - point.imageY
      return {
        ...point,
        distance: Math.hypot(dx, dy),
      }
    })
    .sort((a, b) => a.distance - b.distance)

  if (withDistance[0]?.distance < 1e-9) {
    return {
      longitude: roundCoordinate(withDistance[0].longitude),
      latitude: roundCoordinate(withDistance[0].latitude),
    }
  }

  const nearestPoints = withDistance.slice(0, Math.min(6, withDistance.length))
  const weighted = nearestPoints.reduce((acc, point) => {
    const weight = 1 / Math.max(point.distance ** 2, 1e-12)
    acc.total += weight
    acc.longitude += point.longitude * weight
    acc.latitude += point.latitude * weight
    return acc
  }, { total: 0, longitude: 0, latitude: 0 })

  if (!weighted.total) return null

  return {
    longitude: roundCoordinate(weighted.longitude / weighted.total),
    latitude: roundCoordinate(weighted.latitude / weighted.total),
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

const parseSummaryList = (value) => {
  const list = parseJsonText(value, [])
  return Array.isArray(list) ? list : []
}

const toDateTimeLocal = (value) => {
  if (!value) return undefined
  return String(value).replace(' ', 'T').slice(0, 16)
}

const toBackendDateTime = (value) => {
  if (!value) return null
  return `${value.replace('T', ' ')}:00`
}

function EChart({ option, height = 320 }) {
  const chartRef = useRef(null)

  useEffect(() => {
    if (!chartRef.current) return undefined

    const chart = echarts.init(chartRef.current)
    chart.setOption(option)

    const handleResize = () => chart.resize()
    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('resize', handleResize)
      chart.dispose()
    }
  }, [option])

  return <div ref={chartRef} style={{ width: '100%', height }} />
}

const loadWorkspaceData = async (pageKey, { current, pageSize, keyword, contextId, urlStallId, currentPostTitle }) => {
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
      const records = Array.isArray(res.data?.records) ? res.data.records : []
      const flattenedRows = records.flatMap((item) => {
        const parentRow = {
          ...item,
          postTitle: currentPostTitle,
          authorName: item.username,
        }
        const childRows = Array.isArray(item.children)
          ? item.children.map((child) => ({
              ...child,
              postTitle: currentPostTitle,
              authorName: child.username,
            }))
          : []
        return [parentRow, ...childRows]
      })
      const filteredRows = keyword
        ? flattenedRows.filter((item) => {
            const text = `${item.content || ''} ${item.username || ''} ${item.authorName || ''}`.toLowerCase()
            return text.includes(String(keyword).toLowerCase())
          })
        : flattenedRows
      return { rows: filteredRows, total: filteredRows.length || res.data?.total || 0 }
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
    case 'facility-analytics': {
      const [restaurantRes, sportsRes, teachingRes, dormitoryRes, heatRes] = await Promise.all([
        getFacilityList({ type: 1, page: 1, size: 100 }),
        getFacilityList({ type: 2, page: 1, size: 100 }),
        getFacilityList({ type: 3, page: 1, size: 100 }),
        getFacilityList({ type: 4, page: 1, size: 100 }),
        getFacilityHeat({ limit: 5 }),
      ])

      const allFacilities = [
        ...(restaurantRes.data?.records || []),
        ...(sportsRes.data?.records || []),
        ...(teachingRes.data?.records || []),
        ...(dormitoryRes.data?.records || []),
      ]

      const countByStatus = (status) => allFacilities.filter((item) => item.status === status).length

      const rows = [
        { id: 'facility-total', label: '设施总数', value: String(allFacilities.length) },
        { id: 'facility-restaurant', label: '餐厅数量', value: String(restaurantRes.data?.total || restaurantRes.data?.records?.length || 0) },
        { id: 'facility-sports', label: '运动场数量', value: String(sportsRes.data?.total || sportsRes.data?.records?.length || 0) },
        { id: 'facility-teaching', label: '教学楼数量', value: String(teachingRes.data?.total || teachingRes.data?.records?.length || 0) },
        { id: 'facility-dormitory', label: '宿舍数量', value: String(dormitoryRes.data?.total || dormitoryRes.data?.records?.length || 0) },
        { id: 'facility-status-normal', label: '正常开放设施', value: String(countByStatus(1)) },
        { id: 'facility-status-maintenance', label: '维护中设施', value: String(countByStatus(2)) },
        { id: 'facility-status-closed', label: '关闭设施', value: String(countByStatus(3)) },
        ...((Array.isArray(heatRes.data) ? heatRes.data : []).map((item, index) => ({
          id: `facility-heat-${index}`,
          label: `热度榜 ${index + 1} · ${item.markerName || item.facilityName || `设施 ${index + 1}`}`,
          value: `访问 ${item.visitCount ?? item.viewCount ?? 0} / 导航 ${item.navigationCount ?? 0}`,
        }))),
      ]

      return { rows, total: rows.length }
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
    case 'system-config': {
      const res = await getSystemConfigList({
        page: current,
        size: pageSize,
        keyword,
        prefixes: 'ai.service.',
      })
      const records = Array.isArray(res.data?.records) ? res.data.records : []
      const baseUrlConfig = records.find((item) => item.configKey === 'ai.service.base-url')
      const apiKeyConfig = records.find((item) => item.configKey === 'ai.service.api-key')
      const modelConfig = records.find((item) => item.configKey === 'ai.service.model')
      const status = [baseUrlConfig?.status, apiKeyConfig?.status, modelConfig?.status].some((item) => Number(item) === 0) ? 0 : 1
      const updateTime = [baseUrlConfig?.updateTime, apiKeyConfig?.updateTime, modelConfig?.updateTime]
        .filter(Boolean)
        .sort()
        .slice(-1)[0] || null
      const rows = (baseUrlConfig || apiKeyConfig || modelConfig)
        ? [{
            id: 'deepseek-config',
            provider: 'DeepSeek',
            apiKeyMasked: (() => {
              const text = String(apiKeyConfig?.configValue || '')
              if (!text) return '-'
              if (text.length <= 10) return text
              return `${text.slice(0, 6)}****${text.slice(-4)}`
            })(),
            status,
            statusText: status === 1 ? '启用' : '禁用',
            updateTime,
            apiKeyConfigId: apiKeyConfig?.id,
            rawApiKey: apiKeyConfig?.configValue || '',
            description: 'DeepSeek API Key 配置',
          }]
        : []
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
  const [forumPostOptions, setForumPostOptions] = useState([])
  const [contextInput, setContextInput] = useState('')
  const [contextId, setContextId] = useState('')
  const [urlStallId, setUrlStallId] = useState('')
  const [urlStallName, setUrlStallName] = useState('')
  const [mapConfigForm, setMapConfigForm] = useState({
    mapImageUrl: '',
    centerLongitude: '',
    centerLatitude: '',
    zoomLevel: 16,
    calibrationMode: 'controlPoints',
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
          currentPostTitle: searchParams.get('postTitle') || forumPostOptions.find((item) => item.value === String(contextId))?.label || '',
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
  }, [contextId, forumPostOptions, page, pageKey, pagination.current, pagination.pageSize, keyword, searchParams, status, urlStallId])

  // 解析 URL 参数（仅 facility-stall-dish 页面）
  useEffect(() => {
    if (pageKey !== 'forum-comment') return

    let cancelled = false
    const loadPostOptions = async () => {
      try {
        const res = await getPostList({ page: 1, size: 100 })
        if (cancelled) return
        const records = res.data?.records || []
        const options = records.map((item) => ({
          value: String(item.id),
          label: `${item.title}（#${item.id}）`,
        }))
        setForumPostOptions(options)
        if (!searchParams.get('postId') && !contextId && options.length) {
          setContextId(options[0].value)
          setContextInput(options[0].value)
          setPagination((prev) => ({ ...prev, current: 1 }))
        }
      } catch {
        if (!cancelled) {
          setForumPostOptions([])
        }
      }
    }

    loadPostOptions()
    return () => {
      cancelled = true
    }
  }, [contextId, pageKey, searchParams])

  useEffect(() => {
    if (pageKey !== 'forum-comment') return

    const postId = searchParams.get('postId')
    if (postId) {
      setContextId(postId)
      setContextInput(postId)
      setPagination((prev) => ({ ...prev, current: 1 }))
    }
  }, [pageKey, searchParams])

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
          calibrationMode: 'controlPoints',
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
      currentPostTitle: searchParams.get('postTitle') || forumPostOptions.find((item) => item.value === String(contextId))?.label || '',
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
            name: record.name,
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
      ...(pageKey === 'system-config'
        ? {
            provider: record.provider,
            apiKey: record.rawApiKey,
            description: record.description,
            status: record.status,
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
        'system-config': {
          edit: async () => {
            await updateSystemConfig(editingRecord.apiKeyConfigId, {
              configValue: values.apiKey,
              description: 'AI 服务密钥',
              status: values.status,
            })
          },
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
      case 'system-config':
        return (
          <>
            <Form.Item name="provider" label="服务商">
              <Input disabled />
            </Form.Item>
            <Form.Item name="apiKey" label="API Key" rules={[{ required: true }]}>
              <Input.Password />
            </Form.Item>
            <Form.Item name="status" label="状态" rules={[{ required: true }]}>
              <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
            </Form.Item>
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
          <Space size="small">
            <Button size="small" onClick={() => navigate(`/forum/comment?postId=${record.id}&postTitle=${encodeURIComponent(record.title || '')}`)}>
              查看评论
            </Button>
          </Space>
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
      case 'system-config':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Button
              size="small"
              loading={actionLoading}
              onClick={() => runAction(async () => {
                const res = await testSystemConfig(record.apiKeyConfigId)
                Modal.info({
                  title: res.data?.success ? '连通测试成功' : '连通测试失败',
                  content: (
                    <div>
                      <p>服务商：DeepSeek</p>
                      <p>目标地址：{res.data?.target || '-'}</p>
                      <p>结果：{res.data?.detail || '-'}</p>
                    </div>
                  ),
                })
              }, '测试完成')}
            >
              测试
            </Button>
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
      'system-config',
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
                <p>需要时再维护中心点和控制点。点击左侧底图可录入控制点图坐标。</p>
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
                  <label>标定方式</label>
                  <Input value="控制点标定" readOnly />
                </div>
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
                <div>当前仅使用控制点标定。</div>
                <div>控制点至少维护 3 个，推荐 4 个以上，点位越多越利于提高映射精度。</div>
              </div>
              <div className="workspace-map-config__actions">
                <Button
                  loading={mapConfigSaving}
                  onClick={async () => {
                    try {
                      const controlPointsParsed = mapConfigForm.controlPoints?.trim() ? JSON.parse(mapConfigForm.controlPoints) : []
                      setMapConfigSaving(true)
                      await updateMapConfig({
                        centerLongitude: mapConfigForm.centerLongitude === '' ? null : Number(mapConfigForm.centerLongitude),
                        centerLatitude: mapConfigForm.centerLatitude === '' ? null : Number(mapConfigForm.centerLatitude),
                        zoomLevel: mapConfigForm.zoomLevel ?? 16,
                        calibrationMode: 'controlPoints',
                        controlPoints: JSON.stringify(controlPointsParsed),
                      })
                      message.success('高级标定保存成功')
                    } catch (error) {
                      message.error(error instanceof SyntaxError ? '控制点 JSON 格式不正确' : (error?.message || '高级标定保存失败'))
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

  const renderFacilityAnalyticsPanel = () => {
    const metricMap = Object.fromEntries(rows.map((item) => [item.label, item.value]))
    const categoryOption = {
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['42%', '70%'],
        center: ['50%', '48%'],
        label: { formatter: '{b}\n{c}' },
        data: [
          { name: '餐厅', value: Number(metricMap['餐厅数量'] || 0) },
          { name: '运动场', value: Number(metricMap['运动场数量'] || 0) },
          { name: '教学楼', value: Number(metricMap['教学楼数量'] || 0) },
          { name: '宿舍', value: Number(metricMap['宿舍数量'] || 0) },
        ],
      }],
    }

    const statusOption = {
      grid: { left: 16, right: 16, top: 16, bottom: 12, containLabel: true },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: {
        type: 'category',
        data: ['正常开放', '维护中', '关闭'],
        axisLine: { lineStyle: { color: '#cbd5e1' } },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#e2e8f0' } },
      },
      series: [{
        type: 'bar',
        barWidth: 38,
        data: [
          Number(metricMap['正常开放设施'] || 0),
          Number(metricMap['维护中设施'] || 0),
          Number(metricMap['关闭设施'] || 0),
        ],
        itemStyle: {
          borderRadius: [8, 8, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#2563eb' },
            { offset: 1, color: '#14b8a6' },
          ]),
        },
      }],
    }

    const heatRows = rows.filter((item) => item.label.startsWith('热度榜'))
    const heatOption = {
      grid: { left: 16, right: 16, top: 12, bottom: 12, containLabel: true },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#e2e8f0' } },
      },
      yAxis: {
        type: 'category',
        data: heatRows.map((item) => item.label.replace(/^热度榜 \d+ · /, '')),
        axisTick: { show: false },
        axisLine: { show: false },
      },
      series: [{
        type: 'bar',
        barWidth: 18,
        data: heatRows.map((item) => {
          const match = String(item.value).match(/访问\s+(\d+)/)
          return Number(match?.[1] || 0)
        }),
        itemStyle: {
          borderRadius: [0, 8, 8, 0],
          color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
            { offset: 0, color: '#0f766e' },
            { offset: 1, color: '#38bdf8' },
          ]),
        },
      }],
    }

    const statCards = [
      { label: '设施总数', value: metricMap['设施总数'] || '0' },
      { label: '正常开放', value: metricMap['正常开放设施'] || '0' },
      { label: '维护中', value: metricMap['维护中设施'] || '0' },
      { label: '关闭设施', value: metricMap['关闭设施'] || '0' },
    ]

    return (
      <div className="workspace-analytics">
        <div className="workspace-analytics__stats">
          {statCards.map((item) => (
            <Card key={item.label} className="workspace-analytics__stat-card">
              <span>{item.label}</span>
              <strong>{item.value}</strong>
            </Card>
          ))}
        </div>

        <div className="workspace-analytics__charts">
          <Card className="workspace-table-card" title="设施类型分布">
            <EChart option={categoryOption} height={320} />
          </Card>
          <Card className="workspace-table-card" title="设施状态分布">
            <EChart option={statusOption} height={320} />
          </Card>
          <Card className="workspace-table-card workspace-analytics__wide" title="设施热度榜">
            {heatRows.length ? <EChart option={heatOption} height={340} /> : <Empty description="暂无热度数据" />}
          </Card>
        </div>
      </div>
    )
  }

  const renderMapAnalyticsPanel = () => {
    const metricMap = Object.fromEntries(rows.map((item) => [item.label, item.value]))
    const totalNavigations = Number(metricMap['navigation.totalNavigations'] || 0)
    const todayNavigations = Number(metricMap['navigation.todayNavigations'] || 0)
    const completedNavigations = Number(metricMap['navigation.completedNavigations'] || 0)
    const cancelledNavigations = Number(metricMap['navigation.cancelledNavigations'] || 0)
    const averageDuration = Number(metricMap['navigation.averageDuration'] || 0)

    const destinationRows = rows.filter((item) => !String(item.label).startsWith('navigation.'))
    const destinationOption = {
      grid: { left: 16, right: 16, top: 12, bottom: 12, containLabel: true },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#e2e8f0' } },
      },
      yAxis: {
        type: 'category',
        data: destinationRows.map((item) => item.label),
        axisTick: { show: false },
        axisLine: { show: false },
      },
      series: [{
        type: 'bar',
        barWidth: 18,
        data: destinationRows.map((item) => Number(item.value || 0)),
        itemStyle: {
          borderRadius: [0, 8, 8, 0],
          color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
            { offset: 0, color: '#0f766e' },
            { offset: 1, color: '#3b82f6' },
          ]),
        },
      }],
    }

    const statusOption = {
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['44%', '72%'],
        center: ['50%', '48%'],
        label: { formatter: '{b}\n{c}' },
        data: [
          { name: '已完成', value: completedNavigations },
          { name: '已取消', value: cancelledNavigations },
          { name: '其他', value: Math.max(totalNavigations - completedNavigations - cancelledNavigations, 0) },
        ],
      }],
    }

    const statCards = [
      { label: '累计导航', value: totalNavigations },
      { label: '今日导航', value: todayNavigations },
      { label: '已完成导航', value: completedNavigations },
      { label: '平均时长(秒)', value: averageDuration },
    ]

    return (
      <div className="workspace-analytics">
        <div className="workspace-analytics__stats">
          {statCards.map((item) => (
            <Card key={item.label} className="workspace-analytics__stat-card">
              <span>{item.label}</span>
              <strong>{item.value}</strong>
            </Card>
          ))}
        </div>

        <div className="workspace-analytics__charts">
          <Card className="workspace-table-card" title="导航状态分布">
            <EChart option={statusOption} height={320} />
          </Card>
          <Card className="workspace-table-card" title="热门目的地">
            {destinationRows.length ? <EChart option={destinationOption} height={320} /> : <Empty description="暂无热门目的地数据" />}
          </Card>
        </div>
      </div>
    )
  }

  const renderDiscountAnalyticsPanel = () => {
    const metricMap = Object.fromEntries(rows.map((item) => [item.label, item.value]))
    const totalMerchants = Number(metricMap['discount.totalMerchants'] || 0)
    const totalActivities = Number(metricMap['discount.totalActivities'] || 0)
    const activeActivities = Number(metricMap['discount.activeActivities'] || 0)
    const totalReviews = Number(metricMap['discount.totalReviews'] || 0)
    const avgScore = Number(metricMap['discount.avgScore'] || 0)
    const totalItems = Number(metricMap['secondhand.totalItems'] || 0)
    const onSaleItems = Number(metricMap['secondhand.onSaleItems'] || 0)
    const soldItems = Number(metricMap['secondhand.soldItems'] || 0)
    const offlineItems = Number(metricMap['secondhand.offlineItems'] || 0)

    const topMerchants = parseSummaryList(metricMap['discount.topMerchants'])
    const activityTrend = parseSummaryList(metricMap['discount.activityTrend'])
    const categoryDistribution = parseSummaryList(metricMap['secondhand.categoryDistribution'])

    const trendOption = {
      grid: { left: 16, right: 16, top: 18, bottom: 18, containLabel: true },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: activityTrend.map((item, index) => item.name || item.label || `统计点${index + 1}`),
        axisLine: { lineStyle: { color: '#cbd5e1' } },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#e2e8f0' } },
      },
      series: [{
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: activityTrend.map((item) => Number(item.count ?? item.value ?? 0)),
        lineStyle: { width: 3, color: '#2563eb' },
        itemStyle: { color: '#0f766e' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(37, 99, 235, 0.3)' },
            { offset: 1, color: 'rgba(20, 184, 166, 0.04)' },
          ]),
        },
      }],
    }

    const merchantOption = {
      grid: { left: 16, right: 16, top: 12, bottom: 12, containLabel: true },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#e2e8f0' } },
      },
      yAxis: {
        type: 'category',
        data: topMerchants.map((item, index) => item.name || `商家 ${index + 1}`),
        axisTick: { show: false },
        axisLine: { show: false },
      },
      series: [{
        type: 'bar',
        barWidth: 18,
        data: topMerchants.map((item) => Number(item.count ?? item.value ?? 0)),
        itemStyle: {
          borderRadius: [0, 8, 8, 0],
          color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
            { offset: 0, color: '#0f766e' },
            { offset: 1, color: '#38bdf8' },
          ]),
        },
      }],
    }

    const categoryOption = {
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['44%', '72%'],
        center: ['50%', '48%'],
        label: { formatter: '{b}\n{c}' },
        data: categoryDistribution.map((item, index) => ({
          name: item.name || `分类 ${index + 1}`,
          value: Number(item.count ?? item.value ?? 0),
        })),
      }],
    }

    const statusOption = {
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['42%', '70%'],
        center: ['50%', '48%'],
        label: { formatter: '{b}\n{c}' },
        data: [
          { name: '在售旧物', value: onSaleItems },
          { name: '已售旧物', value: soldItems },
          { name: '已下架旧物', value: offlineItems },
        ],
      }],
    }

    const statCards = [
      { label: '商家总数', value: totalMerchants },
      { label: '优惠活动', value: totalActivities },
      { label: '进行中活动', value: activeActivities },
      { label: '平均评分', value: avgScore ? avgScore.toFixed(1) : '0.0' },
      { label: '评价总数', value: totalReviews },
      { label: '旧物总量', value: totalItems },
      { label: '在售旧物', value: onSaleItems },
      { label: '已售旧物', value: soldItems },
    ]

    return (
      <div className="workspace-analytics">
        <div className="workspace-analytics__stats">
          {statCards.map((item) => (
            <Card key={item.label} className="workspace-analytics__stat-card">
              <span>{item.label}</span>
              <strong>{item.value}</strong>
            </Card>
          ))}
        </div>

        <div className="workspace-analytics__charts">
          <Card className="workspace-table-card" title="优惠活动趋势">
            {activityTrend.length ? <EChart option={trendOption} height={320} /> : <Empty description="暂无活动趋势数据" />}
          </Card>
          <Card className="workspace-table-card" title="热门商家">
            {topMerchants.length ? <EChart option={merchantOption} height={320} /> : <Empty description="暂无商家排行数据" />}
          </Card>
          <Card className="workspace-table-card" title="旧物分类分布">
            {categoryDistribution.length ? <EChart option={categoryOption} height={320} /> : <Empty description="暂无旧物分类数据" />}
          </Card>
          <Card className="workspace-table-card" title="旧物状态分布">
            <EChart option={statusOption} height={320} />
          </Card>
        </div>
      </div>
    )
  }

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
      {page.title && (
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
      )}

      <section className="workspace-main workspace-main-single">
        {pageKey === 'map-config' ? renderMapConfigPanel() : pageKey === 'map-marker' ? renderMarkerManagePanel() : pageKey === 'facility-analytics' ? renderFacilityAnalyticsPanel() : pageKey === 'map-analytics' ? renderMapAnalyticsPanel() : pageKey === 'discount-analytics' ? renderDiscountAnalyticsPanel() : (
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
                {pageKey === 'forum-comment' ? (
                  <Select
                    allowClear
                    placeholder="选择帖子"
                    value={contextId || undefined}
                    options={forumPostOptions}
                    style={{ minWidth: 280 }}
                    onChange={(value) => {
                      const nextValue = value ? String(value) : ''
                      setPagination((prev) => ({ ...prev, current: 1 }))
                      setContextId(nextValue)
                      setContextInput(nextValue)
                    }}
                  />
                ) : null}
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
