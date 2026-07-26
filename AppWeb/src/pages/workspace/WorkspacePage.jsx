import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Card, Drawer, Empty, Form, Image, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tabs, Tag, Upload, message } from 'antd'
import { SearchOutlined, UploadOutlined } from '@ant-design/icons'
import SidePanel from '../../components/SidePanel/SidePanel'
import * as echarts from 'echarts'
import { createActivity, deleteActivity, getActivityList, publishActivity, updateActivity } from '../../api/activity'
import { createCategory, getCategoryList, updateCategory } from '../../api/category'
import { getDiscountActivityList } from '../../api/discount'
import { createFacility, deleteFacility, getFacilityList, getFacilityTypes, updateFacility } from '../../api/facility'
import { createDish, createStall, deleteDish, deleteStall, getCanteenStallList, getDishList, updateDish, updateStall } from '../../api/dish'
import { adminDeleteComment, createTopic, deleteTopic, getCommentList, getPostList, getTopicList, updateTopic } from '../../api/forum'
import { deleteMarker, getFacilityHeat, getMarkerList, getNavigationStatistics } from '../../api/map'
import { getMeetingDetail, getMeetingList } from '../../api/meeting'
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
import { deleteSystemConfig, getSystemConfigList, getSystemConfigTestLogs, testAiModel, testSystemConfig, upsertSystemConfig } from '../../api/systemConfig'
import { getAiModelProviders } from '../../api/rag'
import { MAP_BUILDING_UPLOAD_FOLDER, getUploadUrl } from '../../api/upload'
import { disableUser, enableUser, getUserList } from '../../api/user'
import {
  FACILITY_TYPE_OPTIONS as DEFAULT_FACILITY_TYPE_OPTIONS,
  createFacilityTypeLabelGetter,
  toFacilityTypeOptions,
} from '../../config/facilityType'
import { getWorkspacePage } from '../../data/portalData'
import './WorkspacePage.css'

const AMAP_WEB_KEY = '64bc139adb6a611277fb8f6821b371ac'
const DEFAULT_MAP_CENTER = {
  longitude: 104.0736,
  latitude: 30.6667,
}
const DEFAULT_MAP_ZOOM = 16
const AI_MODALITIES = [
  { key: 'text', label: '语言模型' },
  { key: 'vision', label: '视觉/视频理解' },
  { key: 'image', label: '图片生成/编辑' },
  { key: 'video', label: '视频生成/编辑' },
  { key: 'audio', label: '语音/音频' },
]
const AI_CONFIG_FIELDS = ['provider', 'base-url', 'api-key', 'model']
const AI_MODEL_CONFIG_PATTERN = /^ai\.service\.([A-Za-z0-9_-]+)(?:\.([A-Za-z0-9_-]+))?\.(provider|base-url|api-key|model)$/
const AI_TESTED_MODEL_PREFIXES_KEY = 'ai_tested_model_prefixes_v1'
const AI_TESTED_MODEL_IDS_KEY = 'ai_tested_model_ids_v1'
const AI_PROVIDER_CONFIG_PATTERN = /^ai\.provider\.([A-Za-z0-9_-]+)\.(base-url|api-key)$/
const AI_MODEL_STATUS_LABELS = {
  implemented: '已接入',
  openai_compatible: '兼容接入',
  planned: '待接入',
}
const AI_CATALOG_MODEL_STATUS_LABELS = {
  active: '官方支持',
  deprecated: '官方弃用',
  preview: '预览',
}
const XFYUN_ASR_PREFIX = 'ai.asr.xfyun'
const XFYUN_ASR_FIELDS = ['websocket-url', 'app-id', 'access-key-id', 'access-key-secret', 'lang', 'audio-encode', 'samplerate']
const XFYUN_ASR_REQUIRED_FIELDS = XFYUN_ASR_FIELDS
const XFYUN_ASR_DEFAULTS = {
  'websocket-url': 'wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1',
  lang: 'autodialect',
  'audio-encode': 'pcm_s16le',
  samplerate: '16000',
}
const MEETING_STATUS_LABELS = {
  idle: '未开始',
  active: '会议中',
  paused: '已暂停',
  ended: '已结束',
}
const MEETING_TYPE_LABELS = {
  quick: '快速会议',
  reserved: '预约会议',
}
let amapLoaderPromise = null

const loadAmapScript = () => {
  if (typeof window === 'undefined') return Promise.reject(new Error('浏览器环境不可用'))
  if (window.AMap) return Promise.resolve(window.AMap)
  if (amapLoaderPromise) return amapLoaderPromise

  amapLoaderPromise = new Promise((resolve, reject) => {
    const existing = document.querySelector('script[data-amap-sdk="true"]')
    if (existing) {
      existing.addEventListener('load', () => resolve(window.AMap))
      existing.addEventListener('error', () => reject(new Error('高德地图脚本加载失败')))
      return
    }

    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${AMAP_WEB_KEY}`
    script.async = true
    script.defer = true
    script.dataset.amapSdk = 'true'
    script.onload = () => {
      if (window.AMap) {
        resolve(window.AMap)
      } else {
        reject(new Error('高德地图对象未初始化'))
      }
    }
    script.onerror = () => reject(new Error('高德地图脚本加载失败'))
    document.body.appendChild(script)
  })

  return amapLoaderPromise
}

const ensureAmapPlugin = (pluginName) => new Promise((resolve, reject) => {
  if (typeof window === 'undefined' || !window.AMap?.plugin) {
    reject(new Error('高德地图插件不可用'))
    return
  }
  window.AMap.plugin(pluginName, () => resolve(window.AMap))
})

const toFiniteNumber = (value) => {
  if (value === null || value === undefined) return null
  if (typeof value === 'string' && value.trim() === '') return null
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : null
}

const formatDateTimeText = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ')
}

const roundCoordinate = (value) => {
  const numeric = toFiniteNumber(value)
  return numeric === null ? '' : String(Number(numeric.toFixed(7)))
}

const parseFacilityImages = (images) => {
  if (Array.isArray(images)) return images.filter(Boolean)
  if (!images) return []
  if (typeof images === 'string') {
    try {
      const parsed = JSON.parse(images)
      return Array.isArray(parsed) ? parsed.filter(Boolean) : []
    } catch {
      return []
    }
  }
  return []
}

const buildFacilityImagesJson = (thumbnailUrl) => {
  const url = (thumbnailUrl || '').trim()
  return JSON.stringify(url ? [url] : [])
}

const MAP_BUILDING_MAX_UPLOAD_BYTES = 4.5 * 1024 * 1024
const MAP_BUILDING_MAX_IMAGE_EDGE = 1600

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

const compressMapBuildingImage = async (file) => {
  if (!(file instanceof File)) return file
  if (file.size <= MAP_BUILDING_MAX_UPLOAD_BYTES) return file

  const lowerName = (file.name || '').toLowerCase()
  if (lowerName.endsWith('.gif') || file.type === 'image/gif') {
    throw new Error('GIF 图片过大，请先压缩后再上传')
  }

  const dataUrl = await readFileAsDataUrl(file)
  const image = await loadImageElement(dataUrl)
  const ratio = Math.min(1, MAP_BUILDING_MAX_IMAGE_EDGE / Math.max(image.width, image.height))
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
    if (blob.size <= MAP_BUILDING_MAX_UPLOAD_BYTES) break
  }
  if (!compressedBlob) {
    throw new Error('图片压缩失败')
  }
  const extension = outputType === 'image/png' ? '.png' : '.jpg'
  const filename = lowerName.replace(/\.[^.]+$/, '') || 'map-building'
  return new File([compressedBlob], `${filename}${extension}`, { type: outputType })
}

const uploadMapBuildingImage = async (file) => {
  const compressedFile = await compressMapBuildingImage(file)
  const formData = new FormData()
  formData.append('file', compressedFile)
  const response = await fetch(getUploadUrl(MAP_BUILDING_UPLOAD_FOLDER), {
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
  return result?.data?.url || ''
}

const isLikelyChinaCoordinate = (longitude, latitude) => (
  Number.isFinite(longitude)
  && Number.isFinite(latitude)
  && longitude >= 73
  && longitude <= 136
  && latitude >= 3
  && latitude <= 54
)

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
  try {
    const list = !value || !String(value).trim() ? [] : JSON.parse(value)
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
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

const loadWorkspaceData = async (pageKey, { current, pageSize, keyword, status, contextId, urlStallId, currentPostTitle }) => {
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
    case 'facility-canteen': {
      const res = await getFacilityList({ type: 1, page: current, size: pageSize, name: keyword, status: status === '全部' ? undefined : parseInt(status) })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-restaurant': {
      const res = await getCanteenStallList({ page: current, size: pageSize, restaurantId: contextId || undefined })
      const rows = Array.isArray(res.data) ? res.data : []
      return { rows, total: rows.length }
    }
    case 'facility-sports': {
      const typeMap = {
        '全部': [2, 3, 4, 5],
        '球类场地': [2],
        '水上及特殊场地': [3],
        '田径及综合场地': [4],
        '其他': [5],
      }
      const selectedTypes = typeMap[status] || [2, 3, 4, 5]
      const results = await Promise.all(selectedTypes.map(type => 
        getFacilityList({ type, page: 1, size: 100, name: keyword })
      ))
      const allRecords = results.flatMap(res => res.data?.records || [])
      const rows = allRecords.slice((current - 1) * pageSize, current * pageSize)
      return { rows, total: allRecords.length }
    }
    case 'facility-teaching': {
      const res = await getFacilityList({ type: 6, page: current, size: pageSize, name: keyword, status: status === '全部' ? undefined : parseInt(status) })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-dormitory': {
      const res = await getFacilityList({ type: 7, page: current, size: pageSize, name: keyword, status: status === '全部' ? undefined : parseInt(status) })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-stall-dish': {
      if (!urlStallId) return { rows: [], total: 0 }
      const res = await getDishList({ stallId: parseInt(urlStallId), name: keyword })
      return { rows: res.data || [], total: res.data?.length || 0 }
    }
    case 'facility-analytics': {
      const [restaurantRes, sportsRes, teachingRes, dormitoryRes, heatRes] = await Promise.allSettled([
        getFacilityList({ type: 1, page: 1, size: 100 }),
        getFacilityList({ type: 2, page: 1, size: 100 }),
        getFacilityList({ type: 3, page: 1, size: 100 }),
        getFacilityList({ type: 4, page: 1, size: 100 }),
        getFacilityHeat({ limit: 5 }),
      ])

      const getPageRecords = (result) => (
        result.status === 'fulfilled'
          ? (result.value.data?.records || [])
          : []
      )

      const getPageTotal = (result) => (
        result.status === 'fulfilled'
          ? (result.value.data?.total || result.value.data?.records?.length || 0)
          : 0
      )

      const getListData = (result) => (
        result.status === 'fulfilled' && Array.isArray(result.value.data)
          ? result.value.data
          : []
      )

      const allFacilities = [
        ...getPageRecords(restaurantRes),
        ...getPageRecords(sportsRes),
        ...getPageRecords(teachingRes),
        ...getPageRecords(dormitoryRes),
      ]

      const countByStatus = (status) => allFacilities.filter((item) => item.status === status).length

      const rows = [
        { id: 'facility-total', label: '设施总数', value: String(allFacilities.length) },
        { id: 'facility-restaurant', label: '餐厅数量', value: String(getPageTotal(restaurantRes)) },
        { id: 'facility-sports', label: '运动场数量', value: String(getPageTotal(sportsRes)) },
        { id: 'facility-teaching', label: '教学楼数量', value: String(getPageTotal(teachingRes)) },
        { id: 'facility-dormitory', label: '宿舍数量', value: String(getPageTotal(dormitoryRes)) },
        { id: 'facility-status-normal', label: '正常开放设施', value: String(countByStatus(1)) },
        { id: 'facility-status-maintenance', label: '维护中设施', value: String(countByStatus(2)) },
        { id: 'facility-status-closed', label: '关闭设施', value: String(countByStatus(3)) },
        ...(getListData(heatRes).map((item, index) => ({
          id: `facility-heat-${index}`,
          label: `热度榜 ${index + 1} · ${item.markerName || item.facilityName || `设施 ${index + 1}`}`,
          value: `访问 ${item.visitCount ?? item.viewCount ?? 0} / 导航 ${item.navigationCount ?? 0}`,
        }))),
      ]

      return { rows, total: rows.length }
    }
    case 'facility-marker': {
      const res = await getMarkerList({ page: 1, size: 500, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'map-marker': {
      const res = await getMarkerList({ page: 1, size: 500, keyword })
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
      const [res, providerRes] = await Promise.all([
        getSystemConfigList({
          page: current,
          size: 500,
          keyword,
          prefixes: 'ai.service.,ai.provider.',
        }),
        getAiModelProviders(),
      ])
      const records = Array.isArray(res.data?.records) ? res.data.records : []
      const rows = buildAiCapabilityRows(records, providerRes.data)
      return { rows, total: rows.length }
    }
    case 'voice-model-config': {
      const [res, logRes] = await Promise.all([
        getSystemConfigList({
          page: current,
          size: 100,
          keyword,
          prefixes: 'ai.asr.xfyun.',
        }),
        getSystemConfigTestLogs({ configKeyPrefix: XFYUN_ASR_PREFIX, size: 5 }),
      ])
      const records = Array.isArray(res.data?.records) ? res.data.records : []
      const logs = Array.isArray(logRes.data?.records) ? logRes.data.records : []
      const rows = [{ ...buildXfyunAsrRow(records), testLogs: logs, lastTestLog: logs[0] }]
      return { rows, total: rows.length }
    }
    case 'meeting-history': {
      const res = await getMeetingList({ pageNum: current, pageSize, keyword })
      const rows = (res.data?.records || []).map(normalizeMeetingSessionRow)
      return { rows, total: res.data?.total || rows.length }
    }
    default:
      return { rows: [], total: 0 }
  }
}

const statusMap = {
  1: '正常开放',
  2: '维护中',
  3: '关闭',
}

const facilityTypeMap = {
  1: '食堂',
  2: '球类场地',
  3: '水上及特殊场地',
  4: '田径及综合场地',
  5: '其他',
  6: '教学楼',
  7: '宿舍',
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

  if (type === 'text') {
    if (!value) {
      return <span style={{ color: '#94a3b8' }}>-</span>
    }
    const text = String(value).length > 15 ? String(value).slice(0, 15) + '...' : value
    return (
      <span style={{ 
        fontSize: '13px',
        lineHeight: '1.5',
      }}>
        {text}
      </span>
    )
  }

  if (type === 'images') {
    let imageList = []
    try {
      if (typeof value === 'string') {
        imageList = JSON.parse(value)
      } else if (Array.isArray(value)) {
        imageList = value
      }
    } catch (e) {
      imageList = []
    }
    if (!imageList || imageList.length === 0) {
      return <span style={{ color: '#94a3b8' }}>暂无图片</span>
    }
    const displayImages = imageList.slice(0, 3)
    return (
      <Space size={4}>
        {displayImages.map((url, index) => (
          <Image
            key={index}
            src={url}
            alt={`图片${index + 1}`}
            width={56}
            height={56}
            style={{ objectFit: 'cover', borderRadius: 8 }}
            fallback="data:image/svg+xml;base64,PHN2ZyB4bWxucz0naHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmcnIHdpZHRoPSc1NicgaGVpZ2h0PSc1Nic+PHJlY3Qgd2lkdGg9JzU2JyBoZWlnaHQ9JzU2JyByeD0nMTInIGZpbGw9JyNlNWU3ZWInLz48dGV4dCB4PSc1MCUnIHk9JzUwJScgZm9udC1zaXplPScxMicgZmlsbD0nIzY0NzQ4YicgdGV4dC1hbmNob3I9J21pZGRsZScgZHk9Jy4zNWVtJz7ml6Dlm748L3RleHQ+PC9zdmc+"
          />
        ))}
        {imageList.length > 3 && (
          <span style={{ color: '#94a3b8', fontSize: 12, alignSelf: 'center' }}>+{imageList.length - 3}</span>
        )}
      </Space>
    )
  }

  if (type === 'tag') {
    const text = facilityTypeMap[value] || (value === undefined || value === null || value === '' ? '-' : String(value))
    return <Tag color={colorMap[text] || 'blue'}>{text}</Tag>
  }

  if (type === 'status') {
    const text = statusMap[value] || (value === undefined || value === null || value === '' ? '-' : String(value))
    const color = value === 1 ? 'green' : value === 2 ? 'orange' : value === 3 ? 'red' : colorMap[text] || 'default'
    return <Tag color={color}>{text}</Tag>
  }

  if (row && value === undefined) {
    return '-'
  }

  return value ?? '-'
}

function maskSecret(value) {
  const text = String(value || '')
  if (!text) return '-'
  if (text.length <= 10) return text
  return `${text.slice(0, 6)}****${text.slice(-4)}`
}

function groupAiConfigRecords(records) {
  const groups = new Map()
  records.forEach((item) => {
    const match = String(item.configKey || '').match(AI_MODEL_CONFIG_PATTERN)
    if (!match) return
    const [, modality, configName = 'default', field] = match
    const configPrefix = configName === 'default' ? `ai.service.${modality}` : `ai.service.${modality}.${configName}`
    const group = groups.get(configPrefix) || { modality, configName, configPrefix, configs: {} }
    group.configs[field] = item
    groups.set(configPrefix, group)
  })
  return groups
}

function groupAiProviderRecords(records) {
  const groups = new Map()
  records.forEach((item) => {
    const match = String(item.configKey || '').match(AI_PROVIDER_CONFIG_PATTERN)
    if (!match) return
    const [, providerId, field] = match
    const group = groups.get(providerId) || { providerId, configs: {} }
    group.configs[field] = item
    groups.set(providerId, group)
  })
  return groups
}

function normalizeProviderId(value) {
  return String(value || '').trim().toLowerCase().replace(/\s+/g, '-')
}

function toAiConfigName(value, fallback = 'model') {
  const normalized = String(value || '')
    .trim()
    .replace(/[^A-Za-z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return normalized || fallback
}

function buildProviderLookup(providerCatalog) {
  const providers = Array.isArray(providerCatalog?.providers) ? providerCatalog.providers : []
  const lookup = new Map()
  providers.forEach((provider) => {
    const aliases = [provider.id, provider.name, ...(provider.aliases || [])]
    aliases.forEach((alias) => {
      const key = normalizeProviderId(alias)
      if (key) lookup.set(key, provider)
    })
  })
  return lookup
}

function getCapabilityStatusText(status) {
  return AI_MODEL_STATUS_LABELS[status] || status || '可配置'
}

function getCatalogModelStatusText(status) {
  return AI_CATALOG_MODEL_STATUS_LABELS[status] || status || '官方支持'
}

function getProviderCatalogModalities(providerCatalog) {
  const catalogModalities = Array.isArray(providerCatalog?.modalities) ? providerCatalog.modalities : []
  const providerCapabilities = Array.isArray(providerCatalog?.providers)
    ? providerCatalog.providers.flatMap((provider) => Object.keys(provider?.capabilities || {}))
    : []
  const seen = new Set()
  const options = []

  catalogModalities.forEach((item) => {
    if (!item?.key || seen.has(item.key)) return
    seen.add(item.key)
    options.push({ key: item.key, label: item.label || item.key })
  })
  providerCapabilities.forEach((key) => {
    if (!key || seen.has(key)) return
    seen.add(key)
    const fallback = AI_MODALITIES.find((item) => item.key === key)
    options.push({ key, label: fallback?.label || key })
  })
  AI_MODALITIES.forEach((item) => {
    if (seen.has(item.key)) return
    seen.add(item.key)
    options.push(item)
  })
  return options
}

function getModalityLabel(key, providerCatalog = null) {
  const catalogMatch = getProviderCatalogModalities(providerCatalog).find((item) => item.key === key)
  return catalogMatch?.label || key
}

function getModelOptions(record) {
  const models = Array.isArray(record?.capabilityCatalog?.models) ? record.capabilityCatalog.models : []
  return models.map((model) => ({
    value: model.id,
    label: model.name && model.name !== model.id ? `${model.name}（${model.id}）` : model.id,
  }))
}

function markModelTestSuccess(configPrefix, modelId = '') {
  try {
    if (configPrefix) {
      const rawPrefixes = localStorage.getItem(AI_TESTED_MODEL_PREFIXES_KEY)
      const parsedPrefixes = rawPrefixes ? JSON.parse(rawPrefixes) : {}
      parsedPrefixes[configPrefix] = Date.now()
      localStorage.setItem(AI_TESTED_MODEL_PREFIXES_KEY, JSON.stringify(parsedPrefixes))
    }
    if (modelId) {
      const rawModelIds = localStorage.getItem(AI_TESTED_MODEL_IDS_KEY)
      const parsedModelIds = rawModelIds ? JSON.parse(rawModelIds) : {}
      parsedModelIds[modelId] = Date.now()
      localStorage.setItem(AI_TESTED_MODEL_IDS_KEY, JSON.stringify(parsedModelIds))
    }
  } catch {
    // ignore storage errors
  }
}

function getTestedModelPrefixSet() {
  try {
    const raw = localStorage.getItem(AI_TESTED_MODEL_PREFIXES_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return new Set(Object.keys(parsed || {}))
  } catch {
    return new Set()
  }
}

function pickProviderSharedConfig(providerMeta, providerRecords, capabilityGroups) {
  const providerId = providerMeta?.id
  const shared = providerId ? providerRecords.get(providerId)?.configs || {} : {}
  const relatedGroups = capabilityGroups.filter((group) => {
    const configuredProvider = group.configs.provider?.configValue
    return providerMeta && (
      normalizeProviderId(configuredProvider) === normalizeProviderId(providerMeta.id)
      || normalizeProviderId(group.configName) === normalizeProviderId(providerMeta.id)
    )
  })
  const firstWith = (field) => relatedGroups.find((group) => String(group.configs[field]?.configValue || '').trim())?.configs[field]
  return {
    baseUrl: shared['base-url']?.configValue || firstWith('base-url')?.configValue || providerMeta?.baseUrl || '',
    apiKey: shared['api-key']?.configValue || firstWith('api-key')?.configValue || '',
    baseUrlConfigId: shared['base-url']?.id,
    apiKeyConfigId: shared['api-key']?.id,
    apiKeyMasked: maskSecret(shared['api-key']?.configValue || firstWith('api-key')?.configValue),
  }
}

function buildCatalogProviderRows(providerCatalog, providerRecords, capabilityGroups) {
  const providers = Array.isArray(providerCatalog?.providers) ? providerCatalog.providers : []
  const rows = []
  providers.forEach((provider) => {
    const sharedConfig = pickProviderSharedConfig(provider, providerRecords, capabilityGroups)
    rows.push({
      id: `ai.provider.${provider.id}`,
      provider: provider.id,
      providerName: provider.name || provider.id,
      providerDisplay: `${provider.name || provider.id}（${provider.id}）`,
      baseUrl: sharedConfig.baseUrl,
      model: '',
      apiKeyMasked: sharedConfig.apiKeyMasked,
      rawApiKey: sharedConfig.apiKey,
      providerBaseUrl: sharedConfig.baseUrl,
      providerApiKey: sharedConfig.apiKey,
      status: 1,
      statusText: '未配置',
      runtimeStatus: '可配置',
      isDefault: 0,
      updateTime: null,
      configured: false,
      providerOnly: true,
      providerCatalog: provider,
      configIds: {},
      testConfigId: null,
    })
  })
  return rows
}

function buildAiCapabilityRows(records, providerCatalog) {
  const groups = groupAiConfigRecords(records)
  const providerRecords = groupAiProviderRecords(records)
  const providerLookup = buildProviderLookup(providerCatalog)
  const capabilityGroups = Array.from(groups.values())
  const configuredRows = capabilityGroups.map((group) => {
    const providerConfig = group.configs.provider
    const baseUrlConfig = group.configs['base-url']
    const apiKeyConfig = group.configs['api-key']
    const modelConfig = group.configs.model
    const configs = [providerConfig, baseUrlConfig, apiKeyConfig, modelConfig].filter(Boolean)
    const configured = AI_CONFIG_FIELDS.every((field) => String(group.configs[field]?.configValue || '').trim())
    const disabled = configured && configs.some((item) => Number(item.status) === 0)
    const updateTime = configs.map((item) => item.updateTime).filter(Boolean).sort().slice(-1)[0] || null
    const providerMeta = providerLookup.get(normalizeProviderId(providerConfig?.configValue)) || providerLookup.get(normalizeProviderId(group.configName))
    const capabilityMeta = providerMeta?.capabilities?.[group.modality]
    const sharedConfig = pickProviderSharedConfig(providerMeta, providerRecords, capabilityGroups)
    
    return {
      id: group.configPrefix,
      modality: group.modality,
      modalityLabel: getModalityLabel(group.modality, providerCatalog),
      configName: group.configName === 'default' ? '默认' : group.configName,
      configPrefix: group.configPrefix,
      provider: providerConfig?.configValue || '',
      providerName: providerMeta?.name || providerConfig?.configValue || '',
      providerDisplay: providerMeta?.name
        ? `${providerMeta.name}（${providerConfig?.configValue || providerMeta.id}）`
        : providerConfig?.configValue || '',
      baseUrl: sharedConfig.baseUrl || baseUrlConfig?.configValue || '',
      model: modelConfig?.configValue || '',
      apiKeyMasked: maskSecret(sharedConfig.apiKey || apiKeyConfig?.configValue),
      rawApiKey: sharedConfig.apiKey || apiKeyConfig?.configValue || '',
      providerBaseUrl: sharedConfig.baseUrl,
      providerApiKey: sharedConfig.apiKey,
      status: disabled ? 0 : 1,
      statusText: configured ? (disabled ? '禁用' : '启用') : '未配置',
      runtimeStatus: getCapabilityStatusText(capabilityMeta?.status),
      isDefault: 0,
      updateTime,
      configured,
      providerCatalog: providerMeta,
      capabilityCatalog: capabilityMeta,
      configIds: {
        provider: providerConfig?.id,
        baseUrl: baseUrlConfig?.id,
        apiKey: apiKeyConfig?.id,
        model: modelConfig?.id,
      },
      testConfigId: baseUrlConfig?.id || apiKeyConfig?.id || modelConfig?.id || providerConfig?.id,
    }
  })
  return [...configuredRows, ...buildCatalogProviderRows(providerCatalog, providerRecords, capabilityGroups)]
    .sort((a, b) => {
      const modalities = getProviderCatalogModalities(providerCatalog)
      const getOrder = (modality) => {
        const index = modalities.findIndex((item) => item.key === modality)
        return index === -1 ? modalities.length : index
      }
      const modalityOrder = getOrder(a.modality) - getOrder(b.modality)
      if (modalityOrder !== 0) return modalityOrder
      return String(a.providerName || a.provider).localeCompare(String(b.providerName || b.provider), 'zh-Hans-CN')
    })
}

function groupRowsByProvider(rows) {
  const providers = new Map()
  rows.filter((row) => row.configKind !== 'asr').forEach((row) => {
    const providerId = row.providerCatalog?.id || row.provider || row.configName
    const key = normalizeProviderId(providerId)
    if (!key) return
    const existing = providers.get(key) || {
      id: key,
      provider: row.provider || providerId,
      providerName: row.providerName || row.providerCatalog?.name || providerId,
      providerDisplay: row.providerDisplay || row.providerName || providerId,
      baseUrl: row.providerBaseUrl || row.baseUrl || row.providerCatalog?.baseUrl || '',
      rawApiKey: row.providerApiKey || row.rawApiKey || '',
      apiKeyMasked: maskSecret(row.providerApiKey || row.rawApiKey),
      providerCatalog: row.providerCatalog,
      models: [],
    }
    if (!existing.rawApiKey && row.rawApiKey) {
      existing.rawApiKey = row.rawApiKey
      existing.apiKeyMasked = maskSecret(row.rawApiKey)
    }
    if (!existing.baseUrl && row.baseUrl) {
      existing.baseUrl = row.baseUrl
    }
    if (!row.providerOnly) {
      existing.models.push(row)
    }
    providers.set(key, existing)
  })
  return Array.from(providers.values()).sort((a, b) => a.providerName.localeCompare(b.providerName, 'zh-Hans-CN'))
}

function buildXfyunAsrRow(records) {
  const configs = {}
  records.forEach((item) => {
    const key = String(item.configKey || '')
    if (!key.startsWith(`${XFYUN_ASR_PREFIX}.`)) return
    const field = key.slice(XFYUN_ASR_PREFIX.length + 1)
    if (XFYUN_ASR_FIELDS.includes(field)) {
      configs[field] = item
    }
  })
  const getValue = (field) => configs[field]?.configValue || XFYUN_ASR_DEFAULTS[field] || ''
  const configured = XFYUN_ASR_REQUIRED_FIELDS.every((field) => String(getValue(field) || '').trim())
  const savedConfigs = Object.values(configs)
  const disabled = savedConfigs.length > 0 && savedConfigs.some((item) => Number(item.status) === 0)
  const updateTime = savedConfigs.map((item) => item.updateTime).filter(Boolean).sort().slice(-1)[0] || null
  return {
    id: XFYUN_ASR_PREFIX,
    configKind: 'asr',
    modality: 'asr',
    modalityLabel: '语音转写',
    configName: '讯飞实时转写',
    configPrefix: XFYUN_ASR_PREFIX,
    provider: '讯飞 RTASR 大模型',
    baseUrl: getValue('websocket-url'),
    model: 'rtasr-llm',
    appId: getValue('app-id'),
    accessKeyId: getValue('access-key-id'),
    rawAccessKeySecret: getValue('access-key-secret'),
    rawApiKey: getValue('access-key-secret'),
    apiKeyMasked: maskSecret(getValue('access-key-secret')),
    lang: getValue('lang'),
    audioEncode: getValue('audio-encode'),
    sampleRate: getValue('samplerate'),
    status: disabled ? 0 : 1,
    statusText: configured ? (disabled ? '禁用' : '启用') : '未配置',
    configured,
    updateTime,
    configIds: Object.fromEntries(XFYUN_ASR_FIELDS.map((field) => [field, configs[field]?.id])),
    testConfigId: configs['websocket-url']?.id || configs['app-id']?.id || configs['access-key-id']?.id || configs['access-key-secret']?.id,
  }
}

function normalizeMeetingSessionRow(item) {
  const status = String(item.status || '').toLowerCase()
  const meetingType = String(item.meetingType || '').toLowerCase()
  return {
    ...item,
    id: item.sessionId,
    status,
    statusText: MEETING_STATUS_LABELS[status] || item.status || '-',
    meetingTypeText: MEETING_TYPE_LABELS[meetingType] || item.meetingType || '-',
    updateTime: formatDateTimeText(item.updateTime),
    startTime: formatDateTimeText(item.startTime),
    endTime: formatDateTimeText(item.endTime),
    scheduledStartTime: formatDateTimeText(item.scheduledStartTime),
  }
}

function WorkspacePage({ pageKey }) {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const page = getWorkspacePage(pageKey)
  const [form] = Form.useForm()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState('全部')
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [modalMode, setModalMode] = useState('create')
  const [editingRecord, setEditingRecord] = useState(null)
  const [aiModelTestOpen, setAiModelTestOpen] = useState(false)
  const [aiModelTestRecord, setAiModelTestRecord] = useState(null)
  const [aiModelTestPrompt, setAiModelTestPrompt] = useState('')
  const [aiVisionMediaType, setAiVisionMediaType] = useState('image')
  const [aiVisionMediaUrl, setAiVisionMediaUrl] = useState('')
  const [aiVisionMediaBase64, setAiVisionMediaBase64] = useState('')
  const [aiVisionMediaMimeType, setAiVisionMediaMimeType] = useState('')
  const [aiVisionMediaFilename, setAiVisionMediaFilename] = useState('')
  const [aiVisionUploadLoading, setAiVisionUploadLoading] = useState(false)
  const [aiModelTestResult, setAiModelTestResult] = useState(null)
  const [aiModelTestLoading, setAiModelTestLoading] = useState(false)
  const [meetingDetailOpen, setMeetingDetailOpen] = useState(false)
  const [meetingDetailLoading, setMeetingDetailLoading] = useState(false)
  const [meetingDetail, setMeetingDetail] = useState(null)
  const [merchantCategoryOptions, setMerchantCategoryOptions] = useState([])
  const [activityCategoryOptions, setActivityCategoryOptions] = useState([])
  const [forumPostOptions, setForumPostOptions] = useState([])
  const [, setContextInput] = useState('')
  const [contextId, setContextId] = useState('')
  const [urlStallId, setUrlStallId] = useState('')
  const [urlStallName, setUrlStallName] = useState('')
  const [mapConfigForm] = useState({
    centerLongitude: DEFAULT_MAP_CENTER.longitude,
    centerLatitude: DEFAULT_MAP_CENTER.latitude,
    zoomLevel: DEFAULT_MAP_ZOOM,
    provider: 'amap',
  })
  const [amapReady, setAmapReady] = useState(false)
  const [amapLoadError, setAmapLoadError] = useState('')
  const [selectedMarkerId, setSelectedMarkerId] = useState(null)
  const [markerEditorOpen, setMarkerEditorOpen] = useState(false)
  const [markerEditorMode, setMarkerEditorMode] = useState('create')
  const [markerEditorSaving, setMarkerEditorSaving] = useState(false)
  const [markerSearchKeyword, setMarkerSearchKeyword] = useState('')
  const [markerSearchLoading, setMarkerSearchLoading] = useState(false)
  const [markerSearchResults, setMarkerSearchResults] = useState([])
  const [activeSearchPoi, setActiveSearchPoi] = useState(null)
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
    thumbnailUrl: '',
  })
  const [markerThumbnailUploading, setMarkerThumbnailUploading] = useState(false)
  // 设施地图选点 Drawer（运动场/教学楼/宿舍/食堂编辑时使用）
  const [mapPickerOpen, setMapPickerOpen] = useState(false)
  const [mapPickerRecord, setMapPickerRecord] = useState(null)
  const [mapPickerAmapReady, setMapPickerAmapReady] = useState(false)
  const [mapPickerAmapError, setMapPickerAmapError] = useState('')
  const [mapPickerLng, setMapPickerLng] = useState('')
  const [mapPickerLat, setMapPickerLat] = useState('')
  const [mapPickerSaving, setMapPickerSaving] = useState(false)
  const mapPickerContainerRef = useRef(null)
  const mapPickerAmapRef = useRef(null)
  const mapPickerOverlaysRef = useRef([])
  const mapPickerInitialPositionRef = useRef(null)
  const markerAmapContainerRef = useRef(null)
  const markerAmapHostRef = useRef(null)
  const markerAmapRef = useRef(null)
  const markerAmapOverlaysRef = useRef([])
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  })
  const workspacePage = pagination.current
  const workspacePageSize = pagination.pageSize
  const stallImagePreview = Form.useWatch('image', form)
  const dishImagePreview = Form.useWatch('imageUrl', form)
  const [facilityImageList, setFacilityImageList] = useState([])
  const [facilityImageUploading, setFacilityImageUploading] = useState(false)
  const [facilityTypeOptions, setFacilityTypeOptions] = useState(DEFAULT_FACILITY_TYPE_OPTIONS)
  const getFacilityTypeLabel = useMemo(
    () => createFacilityTypeLabelGetter(facilityTypeOptions),
    [facilityTypeOptions],
  )
  const markerRows = useMemo(() => (Array.isArray(rows) ? rows.map((item) => {
    const imageList = parseFacilityImages(item.images)
    return {
      ...item,
      thumbnailUrl: item.thumbnailUrl || imageList[0] || '',
      position: item.longitude && item.latitude ? `${item.longitude}, ${item.latitude}` : '-',
    }
  }) : []), [rows])
  const selectedMarker = useMemo(
    () => markerRows.find((item) => item.id === selectedMarkerId) || null,
    [markerRows, selectedMarkerId],
  )

  useEffect(() => {
    let cancelled = false
    getFacilityTypes()
      .then((res) => {
        if (cancelled) return
        const types = Array.isArray(res.data) ? res.data : []
        if (types.length) {
          setFacilityTypeOptions(toFacilityTypeOptions(types))
        }
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    setKeyword('')
    setRows([])
    setContextInput('')
    setContextId('')
    setUrlStallId('')
    setUrlStallName('')
    setSelectedMarkerId(null)
    setActiveSearchPoi(null)
    setMarkerSearchKeyword('')
    setMarkerSearchResults([])
    setPagination((prev) => ({
      ...prev,
      current: 1,
      total: 0,
    }))
  }, [pageKey])

  useEffect(() => {
    let cancelled = false

    const run = async () => {
      if (!page) return
      setLoading(true)
      try {
        const result = await loadWorkspaceData(pageKey, {
          current: workspacePage,
          pageSize: workspacePageSize,
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
  }, [contextId, forumPostOptions, page, pageKey, workspacePage, workspacePageSize, keyword, searchParams, status, urlStallId])

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

  // facility-restaurant 页面：从 URL 读取 restaurantId 作为 contextId 过滤
  useEffect(() => {
    if (pageKey !== 'facility-restaurant') return

    const restaurantId = searchParams.get('restaurantId')
    const restaurantName = searchParams.get('restaurantName')
    if (restaurantId) {
      setContextId(restaurantId)
      setUrlStallName(restaurantName || '')
    } else {
      setContextId('')
      setUrlStallName('')
    }
    setPagination((prev) => ({ ...prev, current: 1 }))
  }, [pageKey, searchParams])

  useEffect(() => {
    if (pageKey !== 'map-marker' && pageKey !== 'facility-marker') return undefined

    let cancelled = false
    loadAmapScript()
      .then(() => {
        if (!cancelled) {
          setAmapReady(true)
          setAmapLoadError('')
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setAmapReady(false)
          setAmapLoadError(error?.message || '高德地图加载失败')
        }
      })

    return () => {
      cancelled = true
    }
  }, [pageKey])

  useEffect(() => {
    if (pageKey !== 'map-marker' && pageKey !== 'facility-marker') return
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

  const handleFacilityImageUpload = async (file) => {
    setFacilityImageUploading(true)
    try {
      const compressedFile = await compressMapBuildingImage(file)
      const formData = new FormData()
      formData.append('file', compressedFile)
      const response = await fetch(getUploadUrl(MAP_BUILDING_UPLOAD_FOLDER), {
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
      const imageUrl = result?.data?.url || ''
      if (imageUrl) {
        setFacilityImageList((prev) => {
          const newList = [...prev, imageUrl]
          form.setFieldsValue({ images: JSON.stringify(newList) })
          return newList
        })
      }
      return false
    } catch (error) {
      message.error(error?.message || '图片上传失败')
      return false
    } finally {
      setFacilityImageUploading(false)
    }
  }

  const handleFacilityImageRemove = (index) => {
    const newList = facilityImageList.filter((_, i) => i !== index)
    setFacilityImageList(newList)
    form.setFieldsValue({ images: JSON.stringify(newList) })
  }

  const openMeetingDetail = async (record) => {
    if (!record?.sessionId) return
    setMeetingDetailOpen(true)
    setMeetingDetailLoading(true)
    setMeetingDetail(null)
    try {
      const res = await getMeetingDetail(record.sessionId)
      setMeetingDetail(res.data || null)
    } catch (error) {
      message.error(error?.message || '会议详情加载失败')
    } finally {
      setMeetingDetailLoading(false)
    }
  }

  const runSystemConfigTest = async (record) => {
    if (!record.testConfigId) {
      message.warning(record.configKind === 'asr' ? '请先保存讯飞实时转写配置后再测试' : '请先保存该能力的模型配置后再测试')
      return
    }
    setActionLoading(true)
    try {
      const res = await testSystemConfig(record.testConfigId)
      const result = res.data || {}
      const success = !!result.success
      if (success) {
        message.success('测试成功，记录已保存')
      } else {
        message.error('测试失败，记录已保存')
      }
      Modal.info({
        title: success ? '连通测试成功' : '连通测试失败',
        content: (
          <div>
            <p>能力类型：{record.modalityLabel}</p>
            <p>服务商：{record.providerDisplay || record.provider || '-'}</p>
            <p>目标地址：{result.target || '-'}</p>
            <p>结果：{result.detail || '-'}</p>
            <p>测试记录：已保存到历史记录</p>
          </div>
        ),
      })
      await refreshPageData()
    } catch (error) {
      message.error(error?.message || '测试失败')
    } finally {
      setActionLoading(false)
    }
  }

  const saveProviderCredential = async (providerGroup, values) => {
    const providerId = providerGroup.providerCatalog?.id || providerGroup.provider
    const baseUrl = String(values.baseUrl || '').trim()
    const apiKey = String(values.apiKey || '').trim()
    if (!providerId || !baseUrl || !apiKey) {
      message.warning('请填写服务地址和 API Key')
      return
    }
    setActionLoading(true)
    try {
      await Promise.all([
        upsertSystemConfig({
          configKey: `ai.provider.${providerId}.base-url`,
          configValue: baseUrl,
          configGroup: 'ai',
          description: `${providerGroup.providerName} 统一模型服务地址`,
          status: 1,
        }),
        upsertSystemConfig({
          configKey: `ai.provider.${providerId}.api-key`,
          configValue: apiKey,
          configGroup: 'ai',
          description: `${providerGroup.providerName} 统一模型服务密钥`,
          status: 1,
        }),
        ...providerGroup.models
          .filter((model) => model.configured)
          .flatMap((model) => ([
            upsertSystemConfig({
              configKey: `${model.configPrefix}.base-url`,
              configValue: baseUrl,
              configGroup: 'ai',
              description: `${model.modalityLabel}模型服务地址`,
              status: model.status,
              isDefault: 0,
            }),
            upsertSystemConfig({
              configKey: `${model.configPrefix}.api-key`,
              configValue: apiKey,
              configGroup: 'ai',
              description: `${model.modalityLabel}模型服务密钥`,
              status: model.status,
              isDefault: 0,
            }),
          ])),
      ])
      message.success('服务商 Key 已保存')
      await refreshPageData()
    } catch (error) {
      message.error(error?.message || '保存失败')
    } finally {
      setActionLoading(false)
    }
  }

  const formEnabledPages = [
    'activity-center',
    'activity-category',
    'forum-topic',
    'facility-canteen',
    'facility-restaurant',
    'facility-sports',
    'facility-teaching',
    'facility-dormitory',
    'facility-stall-dish',
    'market-category',
    'discount-category',
    'discount-merchant',
    'system-config',
    'voice-model-config',
  ]

  const openCreateModal = async () => {
    setModalMode('create')
    setEditingRecord(null)
    form.resetFields()
    setFacilityImageList([])
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
    if (pageKey === 'facility-restaurant' && contextId) {
      form.setFieldsValue({ restaurantId: parseInt(contextId) })
    }
    if (pageKey === 'system-config') {
      form.setFieldsValue({ modality: 'text', status: 1 })
    }
    setModalOpen(true)
  }

  const openEditModal = async (record) => {
    setModalMode('edit')
    setEditingRecord(record)
    if (['facility-sports', 'facility-teaching', 'facility-dormitory', 'facility-canteen'].includes(pageKey)) {
      setFacilityImageList(parseFacilityImages(record.images))
    } else {
      setFacilityImageList([])
    }
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
      ...(['facility-restaurant', 'facility-sports', 'facility-teaching', 'facility-dormitory', 'facility-canteen'].includes(pageKey)
        ? {
            facilityName: record.facilityName,
            facilityType: record.facilityType,
            description: record.description,
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
      ...(['system-config', 'voice-model-config'].includes(pageKey)
        ? record.configKind === 'asr'
          ? {
              modalityLabel: record.modalityLabel,
              configName: record.configName,
              websocketUrl: record.baseUrl,
              appId: record.appId,
              accessKeyId: record.accessKeyId,
              accessKeySecret: record.rawAccessKeySecret,
              lang: record.lang,
              audioEncode: record.audioEncode,
              sampleRate: record.sampleRate,
              status: record.status,
            }
          : {
              modality: record.modality,
              modalityLabel: record.modalityLabel,
              configName: record.configName,
              provider: record.provider,
              baseUrl: record.baseUrl,
              model: record.model,
              apiKey: record.rawApiKey,
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
          create: () => createFacility({ ...values, facilityType: 6 }),
          edit: () => updateFacility(editingRecord.id, { ...values, facilityType: 6 }),
        },
        'facility-dormitory': {
          create: () => createFacility({ ...values, facilityType: 7 }),
          edit: () => updateFacility(editingRecord.id, { ...values, facilityType: 7 }),
        },
        'facility-canteen': {
          create: () => createFacility({ ...values, facilityType: 1 }),
          edit: () => updateFacility(editingRecord.id, { ...values, facilityType: 1 }),
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
          create: async () => {
            const modality = values.modality
            const configName = String(values.configName || '').trim()
            const configPrefix = configName ? `ai.service.${modality}.${configName}` : `ai.service.${modality}`
            const modalityLabel = getModalityLabel(modality)
            const statusValue = values.status
            const configs = [
              { field: 'provider', value: values.provider, description: `${modalityLabel}模型服务商` },
              { field: 'base-url', value: values.baseUrl, description: `${modalityLabel}模型服务地址` },
              { field: 'api-key', value: values.apiKey, description: `${modalityLabel}模型服务密钥` },
              { field: 'model', value: values.model, description: `${modalityLabel}模型 ID` },
            ]
            await Promise.all(configs.map((item) => upsertSystemConfig({
              configKey: `${configPrefix}.${item.field}`,
              configValue: item.value,
              configGroup: 'ai',
              description: item.description,
              status: statusValue,
              isDefault: 0,
            })))
          },
          edit: async () => {
            if (editingRecord.configKind === 'asr') {
              const statusValue = values.status
              const configs = [
                { field: 'websocket-url', value: values.websocketUrl, description: '讯飞实时转写大模型 WebSocket 地址' },
                { field: 'app-id', value: values.appId, description: '讯飞实时转写大模型 App ID' },
                { field: 'access-key-id', value: values.accessKeyId, description: '讯飞实时转写大模型 AccessKeyId' },
                { field: 'access-key-secret', value: values.accessKeySecret, description: '讯飞实时转写大模型 AccessKeySecret' },
                { field: 'lang', value: values.lang, description: '讯飞实时转写大模型语种' },
                { field: 'audio-encode', value: values.audioEncode, description: '讯飞实时转写大模型音频编码' },
                { field: 'samplerate', value: values.sampleRate, description: '讯飞实时转写大模型采样率' },
              ]
              await Promise.all(configs.map((item) => upsertSystemConfig({
                configKey: `${XFYUN_ASR_PREFIX}.${item.field}`,
                configValue: item.value,
                configGroup: 'asr',
                description: item.description,
                status: statusValue,
              })))
              return
            }
            const configPrefix = editingRecord.configPrefix
            const statusValue = values.status
            const configs = [
              { field: 'provider', value: values.provider, description: `${editingRecord.modalityLabel}模型服务商` },
              { field: 'base-url', value: values.baseUrl, description: `${editingRecord.modalityLabel}模型服务地址` },
              { field: 'api-key', value: values.apiKey, description: `${editingRecord.modalityLabel}模型服务密钥` },
              { field: 'model', value: values.model, description: `${editingRecord.modalityLabel}模型 ID` },
            ]
            await Promise.all(configs.map((item) => upsertSystemConfig({
              configKey: `${configPrefix}.${item.field}`,
              configValue: item.value,
              configGroup: 'ai',
              description: item.description,
              status: statusValue,
              isDefault: 0,
            })))
          },
        },
        'voice-model-config': {
          edit: async () => {
            const statusValue = values.status
            const configs = [
              { field: 'websocket-url', value: values.websocketUrl, description: '讯飞实时转写大模型 WebSocket 地址' },
              { field: 'app-id', value: values.appId, description: '讯飞实时转写大模型 App ID' },
              { field: 'access-key-id', value: values.accessKeyId, description: '讯飞实时转写大模型 AccessKeyId/APIKey' },
              { field: 'access-key-secret', value: values.accessKeySecret, description: '讯飞实时转写大模型 AccessKeySecret/APISecret' },
              { field: 'lang', value: values.lang, description: '讯飞实时转写大模型语种' },
              { field: 'audio-encode', value: values.audioEncode, description: '讯飞实时转写大模型音频编码' },
              { field: 'samplerate', value: values.sampleRate, description: '讯飞实时转写大模型采样率' },
            ]
            await Promise.all(configs.map((item) => upsertSystemConfig({
              configKey: `${XFYUN_ASR_PREFIX}.${item.field}`,
              configValue: item.value,
              configGroup: 'asr',
              description: item.description,
              status: statusValue,
            })))
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

  const renderVoiceModelConfigFields = () => (
    <>
      <Form.Item name="modalityLabel" label="能力类型">
        <Input disabled />
      </Form.Item>
      <Form.Item name="configName" label="配置名称">
        <Input disabled />
      </Form.Item>
      <Form.Item name="websocketUrl" label="WebSocket URL" rules={[{ required: true, message: '请输入讯飞实时转写大模型 WebSocket 地址' }]}>
        <Input placeholder="wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1" />
      </Form.Item>
      <Form.Item name="appId" label="App ID" rules={[{ required: true, message: '请输入讯飞 App ID' }]}>
        <Input placeholder="请输入讯飞控制台 App ID" />
      </Form.Item>
      <Form.Item name="accessKeyId" label="APIKey / AccessKeyId" rules={[{ required: true, message: '请输入讯飞 APIKey' }]}>
        <Input placeholder="请输入讯飞控制台 APIKey" />
      </Form.Item>
      <Form.Item name="accessKeySecret" label="APISecret / AccessKeySecret" rules={[{ required: true, message: '请输入讯飞 APISecret' }]}>
        <Input.Password placeholder="请输入讯飞控制台 APISecret" />
      </Form.Item>
      <Form.Item name="lang" label="语种" rules={[{ required: true, message: '请输入语种参数' }]}>
        <Input placeholder="autodialect" />
      </Form.Item>
      <Form.Item name="audioEncode" label="音频编码" rules={[{ required: true, message: '请输入音频编码' }]}>
        <Input placeholder="pcm_s16le" />
      </Form.Item>
      <Form.Item name="sampleRate" label="采样率" rules={[{ required: true, message: '请输入采样率' }]}>
        <Input placeholder="16000" />
      </Form.Item>
      <Form.Item name="status" label="状态" rules={[{ required: true }]}>
        <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
      </Form.Item>
    </>
  )

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
      case 'facility-canteen':
        return (
          <>
            <Form.Item name="facilityName" label="食堂名称" rules={[{ required: true }]}>
              <Input placeholder="例如 字一食堂" />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="location" label="位置说明">
              <Input placeholder="例如 校园东区" />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select options={[
                { value: 1, label: '正常开放' },
                { value: 2, label: '维护中' },
                { value: 3, label: '关闭' },
              ]} />
            </Form.Item>
          </>
        )
      case 'facility-sports':
        return (
          <>
            <Form.Item name="facilityName" label="设施名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="facilityType" label="设施类型" rules={[{ required: true }]}>
              <Select options={facilityTypeOptions.filter(opt => opt.value >= 2 && opt.value <= 5)} />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select options={[
                { value: 1, label: '正常开放' },
                { value: 2, label: '维护中' },
                { value: 3, label: '关闭' },
              ]} />
            </Form.Item>
            <Form.Item name="images" label="设施图片" hidden>
              <Input />
            </Form.Item>
            <div className="workspace-image-editor">
              <span className="workspace-image-editor__label">设施图片</span>
              <Upload
                listType="picture-card"
                fileList={facilityImageList.map((url, index) => ({
                  uid: String(index),
                  name: `图片${index + 1}`,
                  status: 'done',
                  url,
                }))}
                beforeUpload={handleFacilityImageUpload}
                onRemove={(file) => {
                  const index = facilityImageList.indexOf(file.url)
                  if (index !== -1) {
                    handleFacilityImageRemove(index)
                  }
                }}
                showUploadList={{
                  showPreviewIcon: true,
                  showRemoveIcon: true,
                  removeIcon: (
                    <span style={{ fontSize: 16, color: '#ff4d4f' }}>✕</span>
                  ),
                }}
                disabled={facilityImageUploading || facilityImageList.length >= 3}
              >
                {facilityImageList.length < 3 && (
                  <div style={{ padding: 16, textAlign: 'center' }}>
                    <UploadOutlined style={{ fontSize: 24, color: '#94a3b8' }} />
                    <p style={{ marginTop: 8, color: '#94a3b8', fontSize: 12 }}>点击上传图片（最多3张）</p>
                  </div>
                )}
              </Upload>
            </div>
          </>
        )
      case 'facility-teaching':
        return (
          <>
            <Form.Item name="facilityName" label="设施名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select options={[
                { value: 1, label: '正常开放' },
                { value: 2, label: '维护中' },
                { value: 3, label: '关闭' },
              ]} />
            </Form.Item>
            <Form.Item name="images" label="设施图片" hidden>
              <Input />
            </Form.Item>
            <div className="workspace-image-editor">
              <span className="workspace-image-editor__label">设施图片</span>
              <Upload
                listType="picture-card"
                fileList={facilityImageList.map((url, index) => ({
                  uid: String(index),
                  name: `图片${index + 1}`,
                  status: 'done',
                  url,
                }))}
                beforeUpload={handleFacilityImageUpload}
                onRemove={(file) => {
                  const index = facilityImageList.indexOf(file.url)
                  if (index !== -1) {
                    handleFacilityImageRemove(index)
                  }
                }}
                showUploadList={{
                  showPreviewIcon: true,
                  showRemoveIcon: true,
                  removeIcon: (
                    <span style={{ fontSize: 16, color: '#ff4d4f' }}>✕</span>
                  ),
                }}
                disabled={facilityImageUploading || facilityImageList.length >= 3}
              >
                {facilityImageList.length < 3 && (
                  <div style={{ padding: 16, textAlign: 'center' }}>
                    <UploadOutlined style={{ fontSize: 24, color: '#94a3b8' }} />
                    <p style={{ marginTop: 8, color: '#94a3b8', fontSize: 12 }}>点击上传图片（最多3张）</p>
                  </div>
                )}
              </Upload>
            </div>
          </>
        )
      case 'facility-dormitory':
        return (
          <>
            <Form.Item name="facilityName" label="设施名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select options={[
                { value: 1, label: '正常开放' },
                { value: 2, label: '维护中' },
                { value: 3, label: '关闭' },
              ]} />
            </Form.Item>
            <Form.Item name="images" label="设施图片" hidden>
              <Input />
            </Form.Item>
            <div className="workspace-image-editor">
              <span className="workspace-image-editor__label">设施图片</span>
              <Upload
                listType="picture-card"
                fileList={facilityImageList.map((url, index) => ({
                  uid: String(index),
                  name: `图片${index + 1}`,
                  status: 'done',
                  url,
                }))}
                beforeUpload={handleFacilityImageUpload}
                onRemove={(file) => {
                  const index = facilityImageList.indexOf(file.url)
                  if (index !== -1) {
                    handleFacilityImageRemove(index)
                  }
                }}
                showUploadList={{
                  showPreviewIcon: true,
                  showRemoveIcon: true,
                  removeIcon: (
                    <span style={{ fontSize: 16, color: '#ff4d4f' }}>✕</span>
                  ),
                }}
                disabled={facilityImageUploading || facilityImageList.length >= 3}
              >
                {facilityImageList.length < 3 && (
                  <div style={{ padding: 16, textAlign: 'center' }}>
                    <UploadOutlined style={{ fontSize: 24, color: '#94a3b8' }} />
                    <p style={{ marginTop: 8, color: '#94a3b8', fontSize: 12 }}>点击上传图片（最多3张）</p>
                  </div>
                )}
              </Upload>
            </div>
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
        if (modalMode === 'edit' && editingRecord?.configKind === 'asr') {
          return renderVoiceModelConfigFields()
        }
        return (
          <>
            {modalMode === 'create' || !editingRecord?.configured ? (
              <>
                <Form.Item name="modality" label="能力类型" rules={[{ required: true, message: '请选择能力类型' }]}>
                  <Select
                    disabled={modalMode === 'edit'}
                    options={(editingRecord?.providerCatalog
                      ? getProviderCatalogModalities({ providers: [editingRecord.providerCatalog], modalities: [] })
                        .filter((item) => editingRecord.providerCatalog.capabilities?.[item.key])
                      : AI_MODALITIES
                    ).map((item) => ({ value: item.key, label: item.label }))}
                    onChange={(value) => {
                      const capability = editingRecord?.providerCatalog?.capabilities?.[value]
                      const modalityLabel = getModalityLabel(value)
                      form.setFieldsValue({
                        model: capability?.defaultModel || capability?.models?.[0]?.id || '',
                        configName: editingRecord?.providerCatalog?.id || form.getFieldValue('configName'),
                        modalityLabel,
                      })
                      if (editingRecord?.providerCatalog) {
                        setEditingRecord((prev) => ({
                          ...prev,
                          modality: value,
                          modalityLabel,
                          capabilityCatalog: capability,
                          configPrefix: `ai.service.${value}.${prev.providerCatalog.id}`,
                        }))
                      }
                    }}
                  />
                </Form.Item>
                <Form.Item
                  name="configName"
                  label="配置标识"
                  rules={[
                    { required: true, message: '请输入配置标识' },
                    { pattern: /^[A-Za-z0-9_-]+$/, message: '只能包含英文、数字、下划线或横线' },
                  ]}
                >
                  <Input disabled={modalMode === 'edit'} placeholder="例如 qwen / image-main" />
                </Form.Item>
              </>
            ) : (
              <>
                <Form.Item name="modalityLabel" label="能力类型">
                  <Input disabled />
                </Form.Item>
                <Form.Item name="configName" label="配置标识">
                  <Input disabled />
                </Form.Item>
              </>
            )}
            <Form.Item name="provider" label="服务商" rules={[{ required: true, message: '请输入服务商' }]}>
              <Input placeholder="请输入服务商标识，例如 qwen / deepseek" />
            </Form.Item>
            <Form.Item name="baseUrl" label="Base URL" rules={[{ required: true, message: '请输入服务地址' }]}>
              <Input disabled={Boolean(editingRecord?.providerCatalog)} placeholder="服务商统一 Base URL" />
            </Form.Item>
            <Form.Item name="apiKey" label="API Key" rules={[{ required: true }]}>
              <Input.Password disabled={Boolean(editingRecord?.providerCatalog)} placeholder="服务商统一 Key，回到服务商卡片修改" />
            </Form.Item>
            <Form.Item name="model" label="模型 ID" rules={[{ required: true, message: '请输入模型 ID' }]}>
              {getModelOptions(editingRecord).length ? (
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={getModelOptions(editingRecord)}
                  placeholder="请选择或搜索模型 ID"
                />
              ) : (
                <Input placeholder="请输入模型 ID" />
              )}
            </Form.Item>
            <Form.Item name="status" label="状态" rules={[{ required: true }]}>
              <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
            </Form.Item>
          </>
        )
      case 'voice-model-config':
        return renderVoiceModelConfigFields()
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
      case 'facility-canteen':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Button
              size="small"
              onClick={() => openMapPicker(record)}
            >
              地图标点
            </Button>
            <Button
              size="small"
              onClick={() => {
                navigate(`/facility/restaurant?restaurantId=${record.id}&restaurantName=${encodeURIComponent(record.facilityName || '食堂')}`)
              }}
            >
              查看档口
            </Button>
            <Popconfirm title="确定删除该食堂吗？" onConfirm={() => runAction(() => deleteFacility(record.id), '食堂已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'facility-sports':
      case 'facility-teaching':
      case 'facility-dormitory':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Button
              size="small"
              onClick={() => openMapPicker(record)}
            >
              地图标点
            </Button>
            <Popconfirm title="确定删除该设施吗？" onConfirm={() => runAction(() => deleteFacility(record.id), '设施已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
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
      case 'facility-marker':
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
      case 'voice-model-config':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              {record.configKind !== 'asr' && !record.configured ? '配置' : '编辑'}
            </Button>
            {record.configured || record.configKind === 'asr' ? (
              <Button
                size="small"
                loading={actionLoading}
                onClick={() => runSystemConfigTest(record)}
              >
                测试
              </Button>
            ) : null}
            {record.configKind !== 'asr' && record.configured ? (
              <Popconfirm
                title="确定删除该模型配置吗？"
                description="会删除该配置组下的 provider/base-url/api-key/model 四个配置项。"
                onConfirm={() => {
                  const ids = Object.values(record.configIds || {}).filter(Boolean)
                  if (!ids.length) {
                    message.warning('该配置没有可删除的数据库记录')
                    return
                  }
                  runAction(
                    () => Promise.all(ids.map((id) => deleteSystemConfig(id))),
                    '模型配置已删除',
                  )
                }}
              >
                <Button size="small" danger loading={actionLoading}>
                  删除
                </Button>
              </Popconfirm>
            ) : null}
          </Space>
        )
      case 'meeting-history':
        return (
          <Button size="small" onClick={() => openMeetingDetail(record)}>
            查看详情
          </Button>
        )
      default:
        return null
    }
  }

  const openProviderModelModal = (providerGroup, modelRecord = null) => {
    const fallbackModality = providerGroup.activeModality || 'text'
    const fallbackCapability = providerGroup.models.find((item) => item.modality === fallbackModality)
      || providerGroup.models.find((item) => item.capabilityCatalog)
      || {
        modality: fallbackModality,
        modalityLabel: getModalityLabel(fallbackModality),
        capabilityCatalog: providerGroup.providerCatalog?.capabilities?.[fallbackModality],
      }
    const record = modelRecord || {
      id: `ai.service.${fallbackModality}.${providerGroup.provider}`,
      modality: fallbackModality,
      modalityLabel: getModalityLabel(fallbackModality),
      configName: providerGroup.provider,
      configPrefix: `ai.service.${fallbackModality}.${providerGroup.provider}`,
      provider: providerGroup.provider,
      providerName: providerGroup.providerName,
      providerDisplay: providerGroup.providerDisplay,
      baseUrl: providerGroup.baseUrl,
      model: fallbackCapability?.capabilityCatalog?.defaultModel || '',
      rawApiKey: providerGroup.rawApiKey,
      apiKeyMasked: providerGroup.apiKeyMasked,
      status: 1,
      statusText: '未配置',
      runtimeStatus: getCapabilityStatusText(fallbackCapability?.capabilityCatalog?.status),
      isDefault: 0,
      configured: false,
      providerCatalog: providerGroup.providerCatalog,
      capabilityCatalog: fallbackCapability?.capabilityCatalog,
      configIds: {},
      testConfigId: null,
    }
    const capabilityCatalog = record.capabilityCatalog || record.providerCatalog?.capabilities?.[record.modality]
    const modelValue = record.model || capabilityCatalog?.defaultModel || capabilityCatalog?.models?.[0]?.id || ''
    setModalMode(modelRecord?.configured ? 'edit' : 'create')
    setEditingRecord({ ...record, capabilityCatalog })
    form.resetFields()
    form.setFieldsValue({
      modality: record.modality,
      modalityLabel: record.modalityLabel,
      configName: record.configName === '默认' ? record.provider : record.configName,
      provider: record.provider,
      baseUrl: providerGroup.baseUrl || record.baseUrl,
      model: modelValue,
      apiKey: providerGroup.rawApiKey || record.rawApiKey,
      status: record.status,
      
    })
    setModalOpen(true)
  }

  const buildCatalogModelRecord = (providerGroup, section, providerCapability, catalogModel, configuredModel = null) => {
    if (configuredModel) {
      return {
        ...configuredModel,
        catalogModel,
        capabilityCatalog: configuredModel.capabilityCatalog || providerCapability,
        catalogConfigured: true,
      }
    }
    const modelId = catalogModel?.id || ''
    const configName = toAiConfigName(modelId, providerGroup.provider)
    return {
      id: `ai.catalog.${section.key}.${providerGroup.provider}.${modelId}`,
      modality: section.key,
      modalityLabel: section.label,
      configName,
      configPrefix: `ai.service.${section.key}.${configName}`,
      provider: providerGroup.provider,
      providerName: providerGroup.providerName,
      providerDisplay: providerGroup.providerDisplay,
      baseUrl: providerGroup.baseUrl,
      model: modelId,
      rawApiKey: providerGroup.rawApiKey,
      apiKeyMasked: providerGroup.apiKeyMasked,
      status: 1,
      statusText: '未配置',
      runtimeStatus: getCapabilityStatusText(providerCapability?.status),
      isDefault: 0,
      configured: false,
      providerCatalog: providerGroup.providerCatalog,
      capabilityCatalog: providerCapability,
      catalogModel,
      catalogConfigured: false,
      configIds: {},
      testConfigId: null,
    }
  }

  const buildSectionModelRows = (providerGroup, section, providerCapability) => {
    const configuredModels = providerGroup.models.filter((model) => model.modality === section.key)
    const configuredByModelId = new Map(configuredModels.map((model) => [model.model, model]))
    const catalogModels = Array.isArray(providerCapability?.models) ? providerCapability.models : []
    const catalogRows = catalogModels.map((catalogModel) => (
      buildCatalogModelRecord(providerGroup, section, providerCapability, catalogModel, configuredByModelId.get(catalogModel.id))
    ))
    const extraRows = configuredModels
      .filter((model) => !configuredByModelId.has(model.model) || !catalogModels.some((catalogModel) => catalogModel.id === model.model))
      .map((model) => ({ ...model, catalogConfigured: true }))
    return [...catalogRows, ...extraRows]
  }

  const getAiModelTestDefaultPrompt = (modality) => {
    switch (modality) {
      case 'image':
        return '生成一张简洁的智慧校园图标，蓝绿色科技风，干净背景。'
      case 'video':
        return '生成一个 5 秒的智慧校园欢迎动画，镜头缓慢推进，现代科技感。'
      case 'audio':
        return '欢迎使用智慧校园模型测试。'
      case 'vision':
        return '请用一句中文回复：视觉模型连接测试成功。'
      default:
        return '请用一句中文回复：模型连接测试成功。'
    }
  }

  const extractAiTestUrls = (data) => {
    const raw = data?.raw || {}
    const imageUrl = Array.isArray(raw.images) ? raw.images.find((item) => item?.url)?.url : ''
    const videoUrl = Array.isArray(raw.videos) ? raw.videos.find((item) => item?.url)?.url : ''
    return { imageUrl, videoUrl }
  }

  const renderAiModelTestOutput = (data) => {
    const { imageUrl, videoUrl } = extractAiTestUrls(data)
    if (data.modality === 'image' && imageUrl) {
      return (
        <div className="workspace-ai-test-media">
          <Image src={imageUrl} alt="模型测试返回图片" />
          <a href={imageUrl} target="_blank" rel="noreferrer">打开图片原图</a>
        </div>
      )
    }
    if (data.modality === 'video' && videoUrl) {
      return (
        <div className="workspace-ai-test-media">
          <video src={videoUrl} controls playsInline />
          <a href={videoUrl} target="_blank" rel="noreferrer">打开视频地址</a>
        </div>
      )
    }
    const answer = String(data.detail || '').replace(/^模型返回：/, '')
    return <pre>{answer || '-'}</pre>
  }

  const executeAiModelTest = async (model, prompt) => {
    const baseUrl = String(model.providerBaseUrl || model.baseUrl || '').trim()
    const apiKey = String(model.providerApiKey || model.rawApiKey || '').trim()
    const provider = String(model.providerCatalog?.id || model.provider || '').trim()
    const modelId = String(model.model || '').trim()
    if (!provider || !baseUrl || !apiKey || !modelId) {
      setAiModelTestResult({
        success: false,
        detail: '请先在服务商卡片里保存 Base URL 和 API Key',
        provider,
        model: modelId,
        modality: model.modality,
        prompt,
      })
      return
    }
    setAiModelTestLoading(true)
    setAiModelTestResult(null)
    try {
      const payload = {
        modality: model.modality,
        provider,
        baseUrl,
        apiKey,
        model: modelId,
        configPrefix: model.configPrefix,
        prompt,
      }
      if (model.modality === 'vision') {
        payload.mediaType = aiVisionMediaType
        payload.mediaUrl = aiVisionMediaUrl
        payload.mediaBase64 = aiVisionMediaBase64
        payload.mediaMimeType = aiVisionMediaMimeType
        payload.mediaFilename = aiVisionMediaFilename
      }
      const result = await testAiModel(payload)
      setAiModelTestResult(result?.data || {})
      if (result?.data?.success) {
        markModelTestSuccess(model?.configPrefix, modelId)
      }
    } catch (error) {
      setAiModelTestResult({
        success: false,
        detail: error?.message || '模型测试失败',
        provider,
        model: modelId,
        modality: model.modality,
        prompt,
      })
    } finally {
      setAiModelTestLoading(false)
    }
  }

  const runAiModelTest = async (model) => {
    const defaultPrompt = getAiModelTestDefaultPrompt(model.modality)
    const features = Array.isArray(model?.catalogModel?.features) ? model.catalogModel.features : []
    const supportsImage = features.length === 0 || features.includes('image_understanding')
    const supportsVideo = features.length === 0 || features.includes('video_understanding')
    const defaultMediaType = supportsImage ? 'image' : (supportsVideo ? 'video' : 'image')
    setAiModelTestRecord(model)
    setAiModelTestPrompt(defaultPrompt)
    setAiVisionMediaType(defaultMediaType)
    setAiVisionMediaUrl('')
    setAiVisionMediaBase64('')
    setAiVisionMediaMimeType('')
    setAiVisionMediaFilename('')
    setAiModelTestResult(null)
    setAiModelTestOpen(true)
  }

  const compressAiTestImage = async (file) => {
    if (!(file instanceof File)) return file
    if (!file.type.startsWith('image/')) return file
    const maxBytes = 5 * 1024 * 1024
    if (file.size <= maxBytes) return file
    const dataUrl = await readFileAsDataUrl(file)
    const image = await loadImageElement(dataUrl)
    const ratio = Math.min(1, 1800 / Math.max(image.width, image.height))
    const width = Math.max(1, Math.round(image.width * ratio))
    const height = Math.max(1, Math.round(image.height * ratio))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    context.drawImage(image, 0, 0, width, height)
    const outputType = file.type === 'image/png' ? 'image/png' : 'image/jpeg'
    const qualitySteps = outputType === 'image/png' ? [0.92] : [0.9, 0.82, 0.74, 0.66, 0.58]
    let compressedBlob = null
    for (const quality of qualitySteps) {
      const blob = await canvasToBlob(canvas, outputType, quality)
      compressedBlob = blob
      if (blob.size <= maxBytes) break
    }
    const extension = outputType === 'image/png' ? '.png' : '.jpg'
    const filename = (file.name || 'vision-image').replace(/\.[^.]+$/, '')
    return new File([compressedBlob], `${filename}${extension}`, { type: outputType })
  }

  const beforeVisionUpload = async (file) => {
    setAiVisionUploadLoading(true)
    try {
      const normalized = aiVisionMediaType === 'image' ? await compressAiTestImage(file) : file
      const dataUrl = await readFileAsDataUrl(normalized)
      const [prefix, base64 = ''] = String(dataUrl).split(',')
      const mimeMatch = /data:(.*?);base64/.exec(prefix || '')
      const mimeType = mimeMatch?.[1] || normalized.type || (aiVisionMediaType === 'image' ? 'image/jpeg' : 'video/mp4')
      setAiVisionMediaBase64(base64)
      setAiVisionMediaMimeType(mimeType)
      setAiVisionMediaFilename(normalized.name || '')
      setAiVisionMediaUrl('')
      message.success('上传成功，已写入本次测试请求')
    } catch (error) {
      message.error(error?.message || '上传失败')
    } finally {
      setAiVisionUploadLoading(false)
    }
    return false
  }

  const renderAiModelProviderGrid = (section, providerGroups) => (
    <div className="workspace-ai-provider-grid">
      {providerGroups.filter((providerGroup) => providerGroup.providerCatalog?.capabilities?.[section.key]).map((providerGroup) => {
        const testedModelPrefixes = getTestedModelPrefixSet()
        const providerCapability = providerGroup.providerCatalog?.capabilities?.[section.key]
        const sectionModels = buildSectionModelRows(providerGroup, section, providerCapability)
        const scopedProvider = { ...providerGroup, activeModality: section.key }
        return (
          <Card
            key={`${section.key}-${providerGroup.id}`}
            className="workspace-ai-provider-card"
            title={providerGroup.providerDisplay}
            extra={
              providerCapability ? (
                <Button type="primary" size="small" onClick={() => openProviderModelModal(scopedProvider)}>
                  新增模型
                </Button>
              ) : null
            }
          >
            <Form
              layout="vertical"
              className="workspace-ai-provider-form"
              initialValues={{
                baseUrl: providerGroup.baseUrl,
                apiKey: providerGroup.rawApiKey,
              }}
              onFinish={(values) => saveProviderCredential(providerGroup, values)}
            >
              <Form.Item name="baseUrl" label="Base URL" rules={[{ required: true, message: '请输入服务地址' }]}>
                <Input placeholder="请输入服务商统一 Base URL" />
              </Form.Item>
              <Form.Item name="apiKey" label="API Key" rules={[{ required: true, message: '请输入 API Key' }]}>
                <Input.Password placeholder="这个服务商只需要填写一次" />
              </Form.Item>
              <Button htmlType="submit" loading={actionLoading}>
                保存 Key
              </Button>
            </Form>

            <div className="workspace-ai-model-list">
              {sectionModels.length ? sectionModels.map((model) => (
                <div key={model.id} className="workspace-ai-model-row">
                  <div>
                    <Space size="small" wrap>
                      <Tag color={colorMap[model.modalityLabel] || 'blue'}>{model.modalityLabel}</Tag>
                      <Tag color={colorMap[model.runtimeStatus] || 'default'}>{model.runtimeStatus}</Tag>
                      {Array.isArray(model.catalogModel?.features) && model.catalogModel.features.includes('image_understanding') ? (
                        <Tag color="geekblue">图片理解</Tag>
                      ) : null}
                      {Array.isArray(model.catalogModel?.features) && model.catalogModel.features.includes('video_understanding') ? (
                        <Tag color="purple">视频理解</Tag>
                      ) : null}
                      {model.catalogModel ? (
                        <Tag color={model.catalogModel.status === 'deprecated' ? 'orange' : 'cyan'}>
                          {getCatalogModelStatusText(model.catalogModel.status)}
                        </Tag>
                      ) : null}
                      <Tag color={testedModelPrefixes.has(model.configPrefix) ? 'green' : 'default'}>
                        {testedModelPrefixes.has(model.configPrefix) ? '已测试' : '未测试'}
                      </Tag>
                      <Tag color={colorMap[model.statusText] || 'default'}>{model.statusText}</Tag>
                    </Space>
                    <strong>{model.model || '-'}</strong>
                    <span>
                      {model.catalogModel?.name && model.catalogModel.name !== model.model
                        ? `${model.catalogModel.name} · `
                        : ''}
                      {model.configName} · {model.configPrefix}
                    </span>
                  </div>
                  <Space size="small" wrap>
                    <Button size="small" onClick={() => openProviderModelModal(scopedProvider, model)}>
                      {model.configured ? '编辑' : '配置'}
                    </Button>
                    <Button
                      size="small"
                      loading={aiModelTestLoading && aiModelTestRecord?.id === model.id}
                      onClick={() => runAiModelTest(model)}
                    >
                      测试
                    </Button>
                    {model.configured ? (
                      <Popconfirm
                        title="确定删除该模型配置吗？"
                        description="会删除该配置组下的 provider/base-url/api-key/model 四个配置项。"
                        onConfirm={() => {
                          const ids = Object.values(model.configIds || {}).filter(Boolean)
                          if (!ids.length) {
                            message.warning('该配置没有可删除的数据库记录')
                            return
                          }
                          runAction(
                            () => Promise.all(ids.map((id) => deleteSystemConfig(id))),
                            '模型配置已删除',
                          )
                        }}
                      >
                        <Button size="small" danger loading={actionLoading}>
                          删除
                        </Button>
                      </Popconfirm>
                    ) : null}
                  </Space>
                </div>
              )) : (
                <div className="workspace-ai-model-empty">暂无官方模型</div>
              )}
            </div>
          </Card>
        )
      })}
    </div>
  )

  const renderAiModelProviderCards = () => {
    const providerGroups = groupRowsByProvider(rows)
    const providerCatalog = providerGroups.find((providerGroup) => providerGroup.providerCatalog)?.providerCatalog
    const sections = getProviderCatalogModalities({
      providers: providerGroups.map((providerGroup) => providerGroup.providerCatalog).filter(Boolean),
      modalities: providerCatalog?.modalities || [],
    }).filter((section) => providerGroups.some((providerGroup) => providerGroup.providerCatalog?.capabilities?.[section.key]))
    if (!providerGroups.length) {
      return <Empty description={page.emptyText} />
    }
    const tabItems = sections.map((section) => {
      const providerCount = providerGroups.filter((providerGroup) => providerGroup.providerCatalog?.capabilities?.[section.key]).length
      return {
        key: section.key,
        label: `${section.label}（${providerCount}）`,
        children: renderAiModelProviderGrid(section, providerGroups),
      }
    })
    return (
      <div className="workspace-ai-section-list">
        <Tabs className="workspace-ai-model-tabs" items={tabItems} />
      </div>
    )
  }

  const renderVoiceModelConfigCards = () => {
    const voiceRows = rows.filter((row) => row.configKind === 'asr')
    if (!voiceRows.length) {
      return <Empty description={page.emptyText} />
    }
    return (
      <div className="workspace-ai-section-list">
        {voiceRows.map((record) => (
          <Card
            key={record.id}
            className="workspace-ai-provider-card"
            title={record.provider}
            extra={<Tag color={record.configured ? 'green' : 'orange'}>{record.statusText}</Tag>}
          >
            <div className="workspace-ai-model-row">
              <div>
                <Space size="small" wrap>
                  <Tag color="blue">{record.modalityLabel}</Tag>
                  <Tag color="cyan">Java 后端独立配置</Tag>
                  <Tag color={record.configured ? 'green' : 'default'}>{record.configured ? '可测试' : '待填写凭据'}</Tag>
                  {record.lastTestLog ? (
                    <Tag color={record.lastTestLog.success ? 'green' : 'red'}>
                      最近测试{record.lastTestLog.success ? '成功' : '失败'}
                    </Tag>
                  ) : null}
                </Space>
                <strong>{record.model}</strong>
                <span>{record.configName} · {record.baseUrl || '默认讯飞实时转写地址'}</span>
                {record.lastTestLog ? (
                  <span>
                    最近测试：{formatDateTimeText(record.lastTestLog.createTime)} · {record.lastTestLog.detail || '-'}
                  </span>
                ) : (
                  <span>尚无测试记录，保存配置后点击“测试”即可生成记录。</span>
                )}
              </div>
              <Space size="small" wrap>{renderRowActions(record)}</Space>
            </div>
            {record.testLogs?.length ? (
              <div className="workspace-test-log-list">
                {record.testLogs.map((item) => (
                  <div key={item.id} className="workspace-test-log-list__item">
                    <Tag color={item.success ? 'green' : 'red'}>{item.success ? '成功' : '失败'}</Tag>
                    <span>{formatDateTimeText(item.createTime)}</span>
                    <span>{item.detail || '-'}</span>
                  </div>
                ))}
              </div>
            ) : null}
          </Card>
        ))}
      </div>
    )
  }

  const columns = (() => {
    if (!page?.columns?.length) return []
    const baseColumns = page.columns.map((column) => ({
      title: column.title,
      dataIndex: column.dataIndex,
      key: column.dataIndex,
      render: (value, record) => {
        if (column.dataIndex === 'facilityType') {
          return <Tag color="blue">{getFacilityTypeLabel(value, record.facilityTypeName)}</Tag>
        }
        return renderCell(value, column.type, record)
      },
    }))
    const hasActions = [
      'user-manage',
      'activity-center',
      'activity-category',
      'activity-audit',
      'forum-post',
      'forum-comment',
      'forum-topic',
      'facility-canteen',
      'facility-restaurant',
      'facility-sports',
      'facility-teaching',
      'facility-dormitory',
      'facility-stall-dish',
      'facility-marker',
      'map-marker',
      'market-item',
      'market-audit',
      'market-category',
      'discount-category',
      'discount-merchant',
      'system-config',
    ].includes(pageKey)

    if (!hasActions) return baseColumns

    const showAddInHeader = ['facility-sports', 'facility-teaching', 'facility-dormitory'].includes(pageKey)

    return [
      ...baseColumns,
      {
        title: (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>操作</span>
            {showAddInHeader && (
              <Button size="small" type="primary" onClick={openCreateModal} style={{ marginLeft: 'auto' }}>
                新增
              </Button>
            )}
          </div>
        ),
        key: 'actions',
        render: (_, record) => renderRowActions(record),
      },
    ]
  })()

  const clearAmapOverlays = useCallback((overlaysRef) => {
    if (!overlaysRef.current?.length) return
    overlaysRef.current.forEach((overlay) => {
      try {
        overlay.setMap(null)
      } catch {
        // 高德地图容器先于覆盖物销毁时，清理接口可能抛错。
      }
    })
    overlaysRef.current = []
  }, [])

  const destroyAmapMap = useCallback((mapRef) => {
    clearAmapOverlays(markerAmapOverlaysRef)
    if (!mapRef.current) return
    try {
      mapRef.current.destroy()
    } catch {
      // 地图实例可能已被高德 SDK 内部销毁。
    }
    mapRef.current = null
    markerAmapHostRef.current = null
  }, [clearAmapOverlays])

  const buildAmapMap = useCallback((container, mapRef) => {
    if (!container || !window.AMap) return null
    if (mapRef.current && markerAmapHostRef.current === container) return mapRef.current
    if (mapRef.current) {
      destroyAmapMap(mapRef)
    }
    const lng = Number(mapConfigForm.centerLongitude) || DEFAULT_MAP_CENTER.longitude
    const lat = Number(mapConfigForm.centerLatitude) || DEFAULT_MAP_CENTER.latitude
    mapRef.current = new window.AMap.Map(container, {
      zoom: Number(mapConfigForm.zoomLevel) || DEFAULT_MAP_ZOOM,
      center: [lng, lat],
      resizeEnable: true,
      mapStyle: 'amap://styles/normal'
    })
    markerAmapHostRef.current = container
    return mapRef.current
  }, [destroyAmapMap, mapConfigForm.centerLatitude, mapConfigForm.centerLongitude, mapConfigForm.zoomLevel])

  const resizeAmapMap = useCallback((map) => {
    if (!map?.resize) return
    requestAnimationFrame(() => {
      map.resize()
      requestAnimationFrame(() => map.resize())
    })
  }, [])

  const pickMarkerSearchResult = useCallback((poi) => {
    setActiveSearchPoi(poi)
    const longitude = roundCoordinate(poi.longitude)
    const latitude = roundCoordinate(poi.latitude)
    setMarkerDraft((prev) => ({
      ...prev,
      facilityName: prev.facilityName || poi.name || '',
      location: prev.location || poi.address || '',
      longitude,
      latitude,
      imageX: '',
      imageY: '',
    }))

    const map = markerAmapRef.current
    if (map && longitude && latitude) {
      map.setZoomAndCenter(Math.max(Number(mapConfigForm.zoomLevel) || DEFAULT_MAP_ZOOM, 17), [Number(longitude), Number(latitude)])
    }
  }, [mapConfigForm.zoomLevel])

  const handleMarkerSearch = async (rawKeyword) => {
    const searchKeyword = String(rawKeyword ?? markerSearchKeyword).trim()
    if (!searchKeyword) {
      message.warning('请输入地点关键词')
      return
    }
    if (mapConfigForm.provider !== 'amap') {
      message.warning('仅高德地图模式支持地点搜索')
      return
    }
    if (!amapReady || !window.AMap) {
      message.warning('高德地图尚未加载完成')
      return
    }

    setMarkerSearchLoading(true)
    try {
      await ensureAmapPlugin('AMap.PlaceSearch')
      const placeSearch = new window.AMap.PlaceSearch({
        pageSize: 10,
        pageIndex: 1,
        citylimit: false,
      })
      const pois = await new Promise((resolve, reject) => {
        placeSearch.search(searchKeyword, (status, result) => {
          if (status !== 'complete') {
            reject(new Error(result?.info || '地点搜索失败'))
            return
          }
          resolve(Array.isArray(result?.poiList?.pois) ? result.poiList.pois : [])
        })
      })

      const normalized = pois
        .map((item, index) => {
          const location = item.location
          const longitude = toFiniteNumber(location?.lng ?? location?.getLng?.())
          const latitude = toFiniteNumber(location?.lat ?? location?.getLat?.())
          if (longitude === null || latitude === null) return null
          return {
            id: item.id || `poi-${index}`,
            name: item.name || `地点${index + 1}`,
            address: [item.pname, item.cityname, item.adname, item.address].filter(Boolean).join(' '),
            longitude,
            latitude,
          }
        })
        .filter(Boolean)

      setMarkerSearchResults(normalized)
      if (normalized.length) {
        pickMarkerSearchResult(normalized[0])
      } else {
        message.info('没有找到匹配地点，请换个关键词')
      }
    } catch (error) {
      setMarkerSearchResults([])
      message.error(error?.message || '地点搜索失败')
    } finally {
      setMarkerSearchLoading(false)
    }
  }

  useEffect(() => {
    if (pageKey !== 'map-marker' && pageKey !== 'facility-marker' || !amapReady) return undefined
    const map = buildAmapMap(markerAmapContainerRef.current, markerAmapRef)
    if (!map) return undefined
    resizeAmapMap(map)

    const zoom = Number(mapConfigForm.zoomLevel) || DEFAULT_MAP_ZOOM
    const defaultLng = Number(mapConfigForm.centerLongitude) || DEFAULT_MAP_CENTER.longitude
    const defaultLat = Number(mapConfigForm.centerLatitude) || DEFAULT_MAP_CENTER.latitude
    const searchLng = toFiniteNumber(activeSearchPoi?.longitude)
    const searchLat = toFiniteNumber(activeSearchPoi?.latitude)
    const draftLng = toFiniteNumber(markerDraft.longitude)
    const draftLat = toFiniteNumber(markerDraft.latitude)
    const selectedLng = toFiniteNumber(selectedMarker?.longitude)
    const selectedLat = toFiniteNumber(selectedMarker?.latitude)
    const mapCenter = searchLng !== null && searchLat !== null
      ? [searchLng, searchLat]
      : markerEditorOpen && draftLng !== null && draftLat !== null
      ? [draftLng, draftLat]
      : selectedLng !== null && selectedLat !== null
        ? [selectedLng, selectedLat]
        : [defaultLng, defaultLat]
    map.setZoomAndCenter(zoom, mapCenter)

    const clickHandler = (event) => {
      if (!markerEditorOpen) return
      const pixel = event?.pixel
      const convertedLngLat = pixel && typeof map.containerToLngLat === 'function'
        ? map.containerToLngLat(pixel)
        : null
      const rawLongitude = Number(event.lnglat?.getLng?.() ?? event.lnglat?.lng)
      const rawLatitude = Number(event.lnglat?.getLat?.() ?? event.lnglat?.lat)
      const convertedLongitude = Number(convertedLngLat?.getLng?.() ?? convertedLngLat?.lng)
      const convertedLatitude = Number(convertedLngLat?.getLat?.() ?? convertedLngLat?.lat)
      const useRawCoordinate = isLikelyChinaCoordinate(rawLongitude, rawLatitude)
      const useConvertedCoordinate = isLikelyChinaCoordinate(convertedLongitude, convertedLatitude)
      const longitude = useRawCoordinate ? rawLongitude : convertedLongitude
      const latitude = useRawCoordinate ? rawLatitude : convertedLatitude
      if (!useRawCoordinate && !useConvertedCoordinate) {
        message.warning('这次取点坐标异常，请在底图加载完成后重试或先搜索地点再微调。')
        return
      }
      setActiveSearchPoi(null)
      setMarkerDraft((prev) => ({
        ...prev,
        longitude: roundCoordinate(longitude),
        latitude: roundCoordinate(latitude),
        imageX: '',
        imageY: '',
      }))
    }
    map.on('click', clickHandler)

    clearAmapOverlays(markerAmapOverlaysRef)
    const overlays = markerRows
      .map((marker) => {
        const markerLng = toFiniteNumber(marker.longitude)
        const markerLat = toFiniteNumber(marker.latitude)
        if (markerLng === null || markerLat === null) return null
        const overlay = new window.AMap.Marker({
          map,
          position: [markerLng, markerLat],
          label: {
            content: marker.markerName || `标记${marker.id}`,
            direction: 'top'
          }
        })
        overlay.on('click', () => setSelectedMarkerId(marker.id))
        return overlay
      })
      .filter(Boolean)

    if (markerEditorOpen && markerDraft.longitude && markerDraft.latitude) {
      overlays.push(new window.AMap.Marker({
        map,
        position: [Number(markerDraft.longitude), Number(markerDraft.latitude)],
        label: {
          content: markerDraft.facilityName || '待保存标点',
          direction: 'top'
        }
      }))
    }

    if (activeSearchPoi?.longitude && activeSearchPoi?.latitude) {
      overlays.push(new window.AMap.Marker({
        map,
        position: [Number(activeSearchPoi.longitude), Number(activeSearchPoi.latitude)],
        zIndex: 160,
        anchor: 'bottom-center',
        content: '<div class="workspace-amap-search-pin"></div>',
        label: {
          content: activeSearchPoi.name || '搜索结果',
          direction: 'top'
        }
      }))
    }

    markerAmapOverlaysRef.current = overlays

    return () => {
      map.off('click', clickHandler)
      clearAmapOverlays(markerAmapOverlaysRef)
    }
  }, [activeSearchPoi, amapReady, buildAmapMap, clearAmapOverlays, mapConfigForm.centerLatitude, mapConfigForm.centerLongitude, mapConfigForm.provider, mapConfigForm.zoomLevel, markerDraft, markerEditorOpen, markerRows, pageKey, resizeAmapMap, selectedMarker])

  useEffect(() => {
    if (pageKey !== 'map-marker' && pageKey !== 'facility-marker' || !markerAmapRef.current) return
    resizeAmapMap(markerAmapRef.current)
  }, [markerEditorOpen, pageKey, resizeAmapMap])

  useEffect(() => {
    if ((pageKey !== 'map-marker' && pageKey !== 'facility-marker') || typeof ResizeObserver === 'undefined' || !markerAmapContainerRef.current) return undefined
    const observer = new ResizeObserver(() => {
      if (markerAmapRef.current) {
        resizeAmapMap(markerAmapRef.current)
      }
    })
    observer.observe(markerAmapContainerRef.current)
    return () => observer.disconnect()
  }, [markerEditorOpen, pageKey, resizeAmapMap])

  useEffect(() => {
    if (pageKey === 'map-marker' || pageKey === 'facility-marker') return undefined
    destroyAmapMap(markerAmapRef)
    return undefined
  }, [destroyAmapMap, pageKey])

  useEffect(() => () => {
    destroyAmapMap(markerAmapRef)
  }, [destroyAmapMap])

  const openMapPicker = (record) => {
    const initialLongitude = toFiniteNumber(record.longitude)
    const initialLatitude = toFiniteNumber(record.latitude)
    mapPickerInitialPositionRef.current = initialLongitude !== null && initialLatitude !== null
      ? [initialLongitude, initialLatitude]
      : [DEFAULT_MAP_CENTER.longitude, DEFAULT_MAP_CENTER.latitude]
    setMapPickerRecord(record)
    setMapPickerLng(record.longitude ? String(record.longitude) : '')
    setMapPickerLat(record.latitude ? String(record.latitude) : '')
    setMapPickerOpen(true)
    // 加载高德地图
    loadAmapScript()
      .then(() => {
        setMapPickerAmapReady(true)
        setMapPickerAmapError('')
      })
      .catch((err) => {
        setMapPickerAmapReady(false)
        setMapPickerAmapError(err?.message || '高德地图加载失败')
      })
  }

  const saveMapPicker = async () => {
    const lng = toFiniteNumber(mapPickerLng)
    const lat = toFiniteNumber(mapPickerLat)
    if (lng === null || lat === null) {
      message.warning('请在地图上点击选取位置')
      return
    }
    setMapPickerSaving(true)
    try {
      await updateFacility(mapPickerRecord.id, {
        facilityName: mapPickerRecord.facilityName,
        facilityType: mapPickerRecord.facilityType,
        longitude: lng,
        latitude: lat,
      })
      message.success('标点位置已更新')
      setMapPickerOpen(false)
      await refreshPageData()
    } catch (err) {
      message.error(err?.message || '保存失败')
    } finally {
      setMapPickerSaving(false)
    }
  }

  const mapPickerFacilityName = mapPickerRecord?.facilityName || ''

  // 地图选点 Drawer 内地图初始化
  useEffect(() => {
    if (!mapPickerOpen || !mapPickerAmapReady || !mapPickerContainerRef.current) return undefined
    if (!window.AMap) return undefined

    clearAmapOverlays(mapPickerOverlaysRef)
    if (mapPickerAmapRef.current) {
      try {
        mapPickerAmapRef.current.destroy()
      } catch {
        // 地图实例可能已被高德 SDK 内部销毁。
      }
      mapPickerAmapRef.current = null
    }

    let [lng, lat] = mapPickerInitialPositionRef.current || [
      DEFAULT_MAP_CENTER.longitude,
      DEFAULT_MAP_CENTER.latitude,
    ]

    if (!isLikelyChinaCoordinate(lng, lat)) {
      if (isLikelyChinaCoordinate(lat, lng)) {
        const temp = lng
        lng = lat
        lat = temp
      } else {
        lng = DEFAULT_MAP_CENTER.longitude
        lat = DEFAULT_MAP_CENTER.latitude
      }
    }

    const map = new window.AMap.Map(mapPickerContainerRef.current, {
      zoom: 17,
      center: [lng, lat],
      resizeEnable: true,
      mapStyle: 'amap://styles/normal',
    })
    mapPickerAmapRef.current = map

    // 点击地图取点
    const clickHandler = (event) => {
      let rawLng = Number(event.lnglat?.getLng?.() ?? event.lnglat?.lng)
      let rawLat = Number(event.lnglat?.getLat?.() ?? event.lnglat?.lat)
      
      if (!Number.isFinite(rawLng) || !Number.isFinite(rawLat)) {
        message.warning('无法获取坐标，请等底图加载完成后重试')
        return
      }
      
      if (!isLikelyChinaCoordinate(rawLng, rawLat)) {
        if (isLikelyChinaCoordinate(rawLat, rawLng)) {
          const temp = rawLng
          rawLng = rawLat
          rawLat = temp
        } else {
          message.warning('坐标异常，请选择中国境内的位置')
          return
        }
      }
      
      setMapPickerLng(roundCoordinate(rawLng))
      setMapPickerLat(roundCoordinate(rawLat))
    }
    map.on('click', clickHandler)

    return () => {
      map.off('click', clickHandler)
      clearAmapOverlays(mapPickerOverlaysRef)
      try {
        map.destroy()
      } catch {
        // Drawer 卸载时地图容器可能已经销毁。
      }
      mapPickerAmapRef.current = null
    }
  }, [clearAmapOverlays, mapPickerAmapReady, mapPickerOpen])

  // 坐标变化只同步选点标记，不重建地图实例
  useEffect(() => {
    if (!mapPickerOpen || !mapPickerAmapReady || !mapPickerAmapRef.current || !window.AMap) return
    const lng = toFiniteNumber(mapPickerLng)
    const lat = toFiniteNumber(mapPickerLat)
    if (lng === null || lat === null) {
      clearAmapOverlays(mapPickerOverlaysRef)
      return
    }

    const map = mapPickerAmapRef.current
    clearAmapOverlays(mapPickerOverlaysRef)
    const marker = new window.AMap.Marker({
      map,
      position: [lng, lat],
      label: { content: mapPickerFacilityName || '当前位置', direction: 'top' },
    })
    mapPickerOverlaysRef.current = [marker]
    map.setCenter([lng, lat])
  }, [clearAmapOverlays, mapPickerAmapReady, mapPickerFacilityName, mapPickerLat, mapPickerLng, mapPickerOpen])

  const renderMapPickerDrawer = () => (
    <Drawer
      title={`地图标点 — ${mapPickerRecord?.facilityName || ''}`}
      placement="right"
      width={680}
      open={mapPickerOpen}
      onClose={() => setMapPickerOpen(false)}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
          <Button onClick={() => setMapPickerOpen(false)}>取消</Button>
          <Button type="primary" loading={mapPickerSaving} onClick={saveMapPicker}>
            保存位置
          </Button>
        </div>
      }
    >
      <div className="map-picker-drawer">
        <p className="map-picker-drawer__tip">
          点击地图选取位置，也可以先搜索地点再微调。当前坐标将保存到该设施记录。
        </p>
        <div className="map-picker-drawer__coords">
          <div>
            <label>经度</label>
            <Input
              value={mapPickerLng}
              onChange={(e) => setMapPickerLng(e.target.value)}
              placeholder="点击地图自动填入"
              disabled={true}
            />
          </div>
          <div>
            <label>纬度</label>
            <Input
              value={mapPickerLat}
              onChange={(e) => setMapPickerLat(e.target.value)}
              placeholder="点击地图自动填入"
              disabled={true}
            />
          </div>
        </div>
        <div className="map-picker-drawer__map-shell">
          {mapPickerAmapError ? (
            <div className="map-picker-drawer__error">{mapPickerAmapError}</div>
          ) : null}
          <div ref={mapPickerContainerRef} className="map-picker-drawer__canvas" />
        </div>
      </div>
    </Drawer>
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

    const heatRows = rows.filter((item) => String(item?.label || '').startsWith('热度榜'))
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

  const openMarkerCreate = () => {
    setMarkerEditorMode('create')
    setMarkerSearchResults([])
    setActiveSearchPoi(null)
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
      thumbnailUrl: '',
    })
    setMarkerEditorOpen(true)
  }

  const openMarkerImageEditor = () => {
    if (!selectedMarker) {
      message.warning('请先在右侧表格中选中一个标记')
      return
    }
    setMarkerEditorMode('image')
    setMarkerSearchResults([])
    setActiveSearchPoi(null)
    const imageList = parseFacilityImages(selectedMarker.images)
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
      thumbnailUrl: selectedMarker.thumbnailUrl || imageList[0] || '',
    })
    setMarkerEditorOpen(true)
  }

  const openMarkerReposition = () => {
    if (!selectedMarker) {
      message.warning('请先在右侧表格中选中一个标记')
      return
    }
    setMarkerEditorMode('reposition')
    setMarkerSearchResults([])
    setActiveSearchPoi(null)
    const imageList = parseFacilityImages(selectedMarker.images)
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
      thumbnailUrl: selectedMarker.thumbnailUrl || imageList[0] || '',
    })
    setMarkerEditorOpen(true)
  }

  const saveMarkerDraft = async () => {
    const longitude = toFiniteNumber(markerDraft.longitude)
    const latitude = toFiniteNumber(markerDraft.latitude)
    if (!markerDraft.facilityName.trim()) {
      message.warning('请填写标记名称')
      return
    }
    if (markerEditorMode !== 'image' && (longitude === null || latitude === null)) {
      message.warning('请填写有效的经纬度')
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
        longitude: longitude ?? selectedMarker?.longitude,
        latitude: latitude ?? selectedMarker?.latitude,
        imageX: null,
        imageY: null,
        images: buildFacilityImagesJson(markerDraft.thumbnailUrl),
      }
      if (markerEditorMode === 'create') {
        await createFacility(payload)
      } else {
        await updateFacility(selectedMarker.facilityId, payload)
      }
      await refreshPageData()
      setPagination((prev) => ({ ...prev, current: 1 }))
      setMarkerEditorOpen(false)
      message.success(
        markerEditorMode === 'create'
          ? '标点新增成功'
          : markerEditorMode === 'image'
            ? '建筑缩略图已更新'
            : '标点位置已更新',
      )
    } catch (error) {
      message.error(error?.message || (markerEditorMode === 'create' ? '标点新增失败' : '位置更新失败'))
    } finally {
      setMarkerEditorSaving(false)
    }
  }

  const renderMarkerManagePanel = () => (
    <div className="workspace-marker-fullwidth">
      {/* 顶部工具栏 */}
      <div className="workspace-marker-toolbar">
        <div className="workspace-marker-toolbar__search">
          <Input
            allowClear
            value={markerSearchKeyword}
            onChange={(event) => setMarkerSearchKeyword(event.target.value)}
            onPressEnter={() => handleMarkerSearch(markerSearchKeyword)}
            placeholder="搜索教学楼、食堂、宿舍、道路等高德地点"
            prefix={<SearchOutlined />}
            style={{ width: 320 }}
          />
          <Button loading={markerSearchLoading} onClick={() => handleMarkerSearch(markerSearchKeyword)}>
            搜索地点
          </Button>
        </div>
        <div className="workspace-marker-toolbar__actions">
          <span className="workspace-marker-toolbar__meta">已加载 {markerRows.length} 个标点</span>
          <Button type="primary" onClick={openMarkerCreate}>新增标点</Button>
          <Button onClick={openMarkerReposition} disabled={!selectedMarker}>设置位置</Button>
          <Button onClick={openMarkerImageEditor} disabled={!selectedMarker}>上传缩略图</Button>
        </div>
      </div>

      {/* 搜索结果 */}
      {markerSearchResults.length ? (
        <div className="workspace-marker-search-results">
          {markerSearchResults.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`workspace-marker-search__result${activeSearchPoi?.id === item.id ? ' active' : ''}`}
              onClick={() => pickMarkerSearchResult(item)}
            >
              <strong>{item.name}</strong>
              <span>{item.address || '无详细地址'}</span>
              <em>{item.longitude}, {item.latitude}</em>
            </button>
          ))}
        </div>
      ) : null}

      {/* 全宽地图 */}
      <div className="workspace-marker-map-shell">
        <div
          key={`marker-amap-${markerEditorOpen ? 'editing' : 'preview'}`}
          ref={markerAmapContainerRef}
          className="workspace-marker-map-canvas"
        />
        {amapLoadError ? <div className="workspace-map-config__map-error">{amapLoadError}</div> : null}
      </div>

      {/* 标点列表 */}
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
              value={status}
              options={page.filters.status.map((item) => ({ value: item, label: item }))}
              onChange={(value) => {
                setPagination((prev) => ({ ...prev, current: 1 }))
                setStatus(value)
              }}
            />
          </div>
        }
      >
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

      {/* 编辑 Drawer — mask 关闭，挂载在当前容器内，不遮挡地图点击 */}
      <Drawer
        title={
          markerEditorMode === 'create'
            ? '新增标点'
            : markerEditorMode === 'image'
              ? '上传建筑缩略图'
              : '设置标点位置'
        }
        placement="right"
        width={420}
        open={markerEditorOpen}
        onClose={() => setMarkerEditorOpen(false)}
        mask={false}
        getContainer={false}
        style={{ position: 'absolute' }}
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
            <Button onClick={() => setMarkerEditorOpen(false)}>取消</Button>
            <Button type="primary" loading={markerEditorSaving} onClick={saveMarkerDraft}>确定</Button>
          </div>
        }
      >
        <p style={{ color: 'var(--text-muted)', fontSize: 13, marginBottom: 20 }}>
          {markerEditorMode === 'image'
            ? '图片将上传到腾讯云 COS 的 map-buildings 目录，App 端仅展示已上传图片。'
            : '填写信息后，在左侧地图点击取点，也可以先用高德搜索地点。'}
        </p>
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
              options={facilityTypeOptions}
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
          <Form.Item label="建筑缩略图">
            <div className="workspace-marker-thumbnail">
              {markerDraft.thumbnailUrl ? (
                <Image
                  src={markerDraft.thumbnailUrl}
                  alt="建筑缩略图"
                  width={120}
                  height={80}
                  style={{ objectFit: 'cover', borderRadius: 12 }}
                />
              ) : (
                <span className="workspace-marker-thumbnail__empty">暂未上传，App 端不显示图片</span>
              )}
              <Space wrap>
                <Upload
                  accept="image/*"
                  showUploadList={false}
                  disabled={markerThumbnailUploading}
                  customRequest={async ({ file, onSuccess, onError }) => {
                    setMarkerThumbnailUploading(true)
                    try {
                      const url = await uploadMapBuildingImage(file)
                      setMarkerDraft((prev) => ({ ...prev, thumbnailUrl: url }))
                      onSuccess?.({ url })
                      message.success('缩略图上传成功')
                    } catch (error) {
                      onError?.(error)
                      message.error(error?.message || '缩略图上传失败')
                    } finally {
                      setMarkerThumbnailUploading(false)
                    }
                  }}
                >
                  <Button icon={<UploadOutlined />} loading={markerThumbnailUploading}>
                    上传到腾讯云
                  </Button>
                </Upload>
                {markerDraft.thumbnailUrl ? (
                  <Button
                    danger
                    onClick={() => setMarkerDraft((prev) => ({ ...prev, thumbnailUrl: '' }))}
                  >
                    移除图片
                  </Button>
                ) : null}
              </Space>
            </div>
          </Form.Item>
          {markerEditorMode !== 'image' ? (
            <>
              <div className="workspace-map-config__grid">
                <div>
                  <label>经度</label>
                  <Input
                    value={markerDraft.longitude}
                    onChange={(event) => setMarkerDraft((prev) => ({ ...prev, longitude: event.target.value }))}
                    placeholder="点击左侧地图取点"
                  />
                </div>
                <div>
                  <label>纬度</label>
                  <Input
                    value={markerDraft.latitude}
                    onChange={(event) => setMarkerDraft((prev) => ({ ...prev, latitude: event.target.value }))}
                    placeholder="点击左侧地图取点"
                  />
                </div>
              </div>
              <div className="workspace-marker-editor__hint">
                在左侧地图点击取点，系统会自动回填经纬度；也可以先搜索地点，再微调落点。
              </div>
            </>
          ) : null}
        </Form>
      </Drawer>
    </div>
  )

  if (!page) {
    return <Empty description="页面配置不存在" />
  }

  return (
    <div className="workspace-page">
      {/* 页头仅保留带动态上下文的档口页（档口名、返回入口），普通页题由布局顶栏面包屑统一渲染 */}
      {page.title && (pageKey === 'facility-stall-dish' || pageKey === 'facility-restaurant') && (
        <section className={`workspace-hero ${pageKey === 'facility-sports' ? 'workspace-hero-sports' : ''}`}>
          <div className="workspace-hero-content">
            <span className="workspace-badge">{page.badge}</span>
            <h1>
              {pageKey === 'facility-stall-dish' && urlStallName
                ? `${page.title} - ${urlStallName}`
                : pageKey === 'facility-restaurant' && urlStallName
                  ? `${urlStallName} · 档口管理`
                  : page.title}
            </h1>
            <p>{page.description}</p>
            {pageKey === 'facility-stall-dish' ? (
              <p>当前档口 ID：{urlStallId || '未获取到'}</p>
            ) : null}
            {pageKey === 'facility-restaurant' && contextId ? (
              <p>食堂 ID：{contextId} · <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigate('/facility/canteen')}>← 返回食堂列表</Button></p>
            ) : null}
          </div>
        </section>
      )}

      <section className="workspace-main workspace-main-single">
        {pageKey === 'map-marker' || pageKey === 'facility-marker' ? renderMarkerManagePanel() : pageKey === 'facility-analytics' ? renderFacilityAnalyticsPanel() : pageKey === 'map-analytics' ? renderMapAnalyticsPanel() : pageKey === 'discount-analytics' ? renderDiscountAnalyticsPanel() : (
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
                {pageKey === 'facility-sports' ? (
                  <Select
                    value={status}
                    options={page.filters.status.map((item) => ({ value: item, label: item }))}
                    onChange={(value) => {
                      setPagination((prev) => ({ ...prev, current: 1 }))
                      setStatus(value)
                    }}
                  />
                ) : (
                  <Select
                    value="全部"
                    disabled
                    options={page.filters.status.map((item) => ({ value: item, label: item }))}
                  />
                )}
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
                {formEnabledPages.includes(pageKey) && !['system-config', 'voice-model-config', 'facility-sports', 'facility-teaching', 'facility-dormitory'].includes(pageKey) ? (
                  <Button type="primary" onClick={openCreateModal}>
                    新增
                  </Button>
                ) : null}
              </div>
            ) : null
          }
        >
          {pageKey === 'system-config' ? (
            renderAiModelProviderCards()
          ) : pageKey === 'voice-model-config' ? (
            renderVoiceModelConfigCards()
          ) : columns.length ? (
            <Table
              columns={columns}
              dataSource={rows}
              loading={loading}
              rowKey={(record) => record.id || record.key || JSON.stringify(record)}
              locale={{ emptyText: page.emptyText }}
              scroll={{ x: 'max-content' }}
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

      {/* 新增/编辑：统一侧面板组件 */}
      <SidePanel
        open={modalOpen}
        title={modalMode === 'create'
          ? `新增${pageKey === 'system-config' ? '模型配置' : page.title}`
          : `编辑${pageKey === 'system-config' ? (editingRecord?.configKind === 'asr' ? '讯飞实时转写配置' : '模型配置') : pageKey === 'voice-model-config' ? '语音模型配置' : page.title}`}
        onClose={() => setModalOpen(false)}
        destroyOnHidden
        footer={(
          <>
            <Button onClick={() => setModalOpen(false)}>取消</Button>
            <Button type="primary" loading={actionLoading} onClick={submitModal}>保存</Button>
          </>
        )}
      >
        <Form form={form} layout="vertical">
          {renderModalFields()}
        </Form>
      </SidePanel>

      <Drawer
        open={meetingDetailOpen}
        title={meetingDetail?.session?.title || '会议详情'}
        width={720}
        onClose={() => setMeetingDetailOpen(false)}
        destroyOnHidden
      >
        {meetingDetailLoading ? (
          <Empty description="会议详情加载中..." />
        ) : meetingDetail ? (
          <div className="workspace-meeting-detail">
            <Card size="small" title="会议信息">
              <p>会议号：{meetingDetail.session?.roomCode || '-'}</p>
              <p>状态：{MEETING_STATUS_LABELS[String(meetingDetail.session?.status || '').toLowerCase()] || meetingDetail.session?.status || '-'}</p>
              <p>类型：{MEETING_TYPE_LABELS[String(meetingDetail.session?.meetingType || '').toLowerCase()] || meetingDetail.session?.meetingType || '-'}</p>
              <p>开始时间：{formatDateTimeText(meetingDetail.session?.startTime)}</p>
              <p>结束时间：{formatDateTimeText(meetingDetail.session?.endTime)}</p>
              <p>参会成员：{Array.isArray(meetingDetail.participants) && meetingDetail.participants.length ? meetingDetail.participants.join('、') : '-'}</p>
            </Card>
            <Card size="small" title={`会议记录（${meetingDetail.records?.length || 0}）`}>
              {meetingDetail.records?.length ? meetingDetail.records.map((item) => (
                <div key={item.id} className="workspace-meeting-detail__block">
                  <Space size="small" wrap>
                    <Tag color={item.source === 'transcription' ? 'blue' : 'default'}>{item.source || 'manual'}</Tag>
                    <span>{formatDateTimeText(item.createTime)}</span>
                  </Space>
                  <p>{item.content}</p>
                </div>
              )) : <Empty description="暂无会议记录" />}
            </Card>
            <Card size="small" title={`智能体结果（${meetingDetail.results?.length || 0}）`}>
              {meetingDetail.results?.length ? meetingDetail.results.map((item) => (
                <div key={item.id} className="workspace-meeting-detail__block">
                  <Space size="small" wrap>
                    <Tag color="green">{item.agentName}</Tag>
                    <span>{formatDateTimeText(item.createTime)}</span>
                  </Space>
                  <p>{item.answer}</p>
                </div>
              )) : <Empty description="暂无智能体结果" />}
            </Card>
          </div>
        ) : (
          <Empty description="暂无会议详情" />
        )}
      </Drawer>

      <Modal
        open={aiModelTestOpen}
        title={`测试模型：${aiModelTestRecord?.model || '-'}`}
        okText="开始测试"
        cancelText="取消"
        confirmLoading={aiModelTestLoading}
        destroyOnHidden
        onCancel={() => {
          if (!aiModelTestLoading) {
            setAiModelTestOpen(false)
          }
        }}
        onOk={() => executeAiModelTest(aiModelTestRecord, aiModelTestPrompt)}
      >
        <div className="workspace-ai-test-prompt">
          <p>{aiModelTestRecord?.modality === 'image' || aiModelTestRecord?.modality === 'video' ? '请输入生成提示词' : '请输入测试文本'}</p>
          <Input.TextArea
            value={aiModelTestPrompt}
            rows={4}
            disabled={aiModelTestLoading}
            onChange={(event) => setAiModelTestPrompt(event.target.value)}
          />
          {aiModelTestRecord?.modality === 'vision' ? (
            <div style={{ marginTop: 12, display: 'grid', gap: 10 }}>
              {(() => {
                const features = Array.isArray(aiModelTestRecord?.catalogModel?.features) ? aiModelTestRecord.catalogModel.features : []
                const supportsImage = features.length === 0 || features.includes('image_understanding')
                const supportsVideo = features.length === 0 || features.includes('video_understanding')
                const options = []
                if (supportsImage) options.push({ value: 'image', label: '图片理解' })
                if (supportsVideo) options.push({ value: 'video', label: '视频理解' })
                return (
              <Select
                value={aiVisionMediaType}
                options={options}
                onChange={(value) => {
                  setAiVisionMediaType(value)
                  setAiVisionMediaBase64('')
                  setAiVisionMediaMimeType('')
                  setAiVisionMediaFilename('')
                }}
              />
                )
              })()}
              <Input
                value={aiVisionMediaUrl}
                placeholder={aiVisionMediaType === 'image' ? '可选：图片 URL' : '可选：视频 URL'}
                onChange={(event) => setAiVisionMediaUrl(event.target.value)}
              />
              <Upload
                showUploadList={false}
                accept={aiVisionMediaType === 'image' ? 'image/*' : 'video/*'}
                beforeUpload={beforeVisionUpload}
              >
                <Button icon={<UploadOutlined />} loading={aiVisionUploadLoading}>
                  上传{aiVisionMediaType === 'image' ? '图片' : '视频'}（前端先压缩，后端二次处理）
                </Button>
              </Upload>
              {aiVisionMediaFilename ? (
                <div style={{ color: '#475569', fontSize: 12 }}>
                  已选择文件：{aiVisionMediaFilename}
                </div>
              ) : null}
            </div>
          ) : null}
          {aiModelTestLoading ? (
            <div className="workspace-ai-test-status">测试中，正在等待模型返回结果...</div>
          ) : null}
          {aiModelTestResult ? (
            <div className={`workspace-ai-test-result ${aiModelTestResult.success ? 'is-success' : 'is-error'}`}>
              <p>状态：{aiModelTestResult.success ? '成功' : '失败'}</p>
              <p>能力类型：{aiModelTestRecord?.modalityLabel || aiModelTestResult.modality || '-'}</p>
              <p>服务商：{aiModelTestRecord?.providerDisplay || aiModelTestResult.provider || '-'}</p>
              <p>模型：{aiModelTestResult.model || aiModelTestRecord?.model || '-'}</p>
              <p>目标地址：{aiModelTestResult.target || '-'}</p>
              <p>测试输入：{aiModelTestResult.prompt || aiModelTestPrompt || '-'}</p>
              {renderAiModelTestOutput(aiModelTestResult)}
              {aiModelTestResult.raw ? (
                <details>
                  <summary>查看原始返回</summary>
                  <pre>{typeof aiModelTestResult.raw === 'string' ? aiModelTestResult.raw : JSON.stringify(aiModelTestResult.raw, null, 2)}</pre>
                </details>
              ) : null}
            </div>
          ) : null}
        </div>
      </Modal>

      {renderMapPickerDrawer()}
    </div>
  )
}

export default WorkspacePage
