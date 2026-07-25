import { useEffect, useMemo, useRef, useState, useCallback } from 'react'
import { Badge, Button, Card, Drawer, Form, Image, Input, message, Modal, Select, Skeleton, Space, Table, Tag, Upload } from 'antd'
import {
  AimOutlined, CheckCircleOutlined, CloseOutlined, CopyOutlined,
  DeleteOutlined, EnvironmentOutlined, GlobalOutlined, MenuOutlined,
  PictureOutlined, PlusOutlined, PushpinOutlined, SearchOutlined,
  ThunderboltOutlined, UploadOutlined, WarningOutlined,
} from '@ant-design/icons'
import { getMarkerList, deleteMarker } from '../../../api/map'
import { getFacilityList, createFacility as apiCreateFacility, updateFacility as apiUpdateFacility, getFacilityTypes } from '../../../api/facility'
import { getUploadUrl } from '../../../api/upload'
import { toFacilityTypeOptions, createFacilityTypeLabelGetter } from '../../../config/facilityType'
import './MarkerManage.css'

/* ============================================================
   常量
   ============================================================ */
const AMAP_WEB_KEY = '64bc139adb6a611277fb8f6821b371ac'
const DEFAULT_CENTER = { lng: 114.897014, lat: 40.755502 }
const DEFAULT_ZOOM = 16
const MAP_BUILDING_UPLOAD_FOLDER = 'map-buildings'
const MAX_UPLOAD_BYTES = 4.5 * 1024 * 1024
const MAX_IMG_EDGE = 1600

const STATUS_MAP = {
  1: { label: '正常开放', color: 'green' },
  2: { label: '维护中', color: 'orange' },
  3: { label: '已关闭', color: 'red' },
}

const TYPE_META = {
  1: { color: '#FF6B6B', label: '食堂', short: '食' },
  2: { color: '#1DD1A1', label: '运动场', short: '运' },
  3: { color: '#3B82F6', label: '教学楼', short: '教' },
  4: { color: '#A55EEA', label: '综合服务', short: '服' },
  5: { color: '#FECA57', label: '校内商铺', short: '商' },
  99: { color: '#9CA3AF', label: '其他', short: '?' },
}
const tmeta = (t) => TYPE_META[t] || TYPE_META[99]

/* ============================================================
   工具
   ============================================================ */
const toNum = (v) => {
  if (v == null) return null
  if (typeof v === 'string' && v.trim() === '') return null
  const n = Number(v); return Number.isFinite(n) ? n : null
}
const roundCoord = (v) => { const n = toNum(v); return n == null ? '' : String(Number(n.toFixed(7))) }
const parseImgs = (v) => {
  if (Array.isArray(v)) return v.filter(Boolean)
  if (!v) return []
  if (typeof v === 'string') { try { const p = JSON.parse(v); return Array.isArray(p) ? p.filter(Boolean) : [] } catch { return [] } }
  return []
}
const buildImgsJson = (url) => JSON.stringify((url || '').trim() ? [(url || '').trim()] : [])
const isChinaCoord = (l, a) => Number.isFinite(l) && Number.isFinite(a) && l >= 73 && l <= 136 && a >= 3 && a <= 54

/* ---- 高德 ---- */
let amapPromise = null
const loadAmap = () => {
  if (typeof window === 'undefined') return Promise.reject(new Error('浏览器不可用'))
  if (window.AMap) return Promise.resolve(window.AMap)
  if (amapPromise) return amapPromise
  amapPromise = new Promise((rs, rj) => {
    const old = document.querySelector('script[data-amap-sdk]')
    if (old) { old.addEventListener('load', () => rs(window.AMap)); old.addEventListener('error', () => rj(new Error('加载失败'))); return }
    const s = document.createElement('script')
    s.src = `https://webapi.amap.com/maps?v=2.0&key=${AMAP_WEB_KEY}`
    s.async = true; s.defer = true; s.dataset.amapSdk = 'true'
    s.onload = () => window.AMap ? rs(window.AMap) : rj(new Error('未初始化'))
    s.onerror = () => rj(new Error('加载失败'))
    document.body.appendChild(s)
  })
  return amapPromise
}
const amapPlugin = (name) => new Promise((rs, rj) => {
  if (!window.AMap?.plugin) return rj(new Error('AMap 不可用'))
  window.AMap.plugin(name, () => rs(window.AMap))
})

/* ---- 图片 ---- */
const readDataUrl = (f) => new Promise((rs, rj) => { const r = new FileReader(); r.onload = () => rs(r.result); r.onerror = rj; r.readAsDataURL(f) })
const loadImg = (src) => new Promise((rs, rj) => { const i = new Image(); i.onload = () => rs(i); i.onerror = rj; i.src = src })
const canvasBlob = (c, t, q) => new Promise((rs, rj) => c.toBlob((b) => b ? rs(b) : rj(new Error('转换失败')), t, q))
const compressImg = async (file) => {
  if (!(file instanceof File)) return file
  if (file.size <= MAX_UPLOAD_BYTES) return file
  if ((file.name || '').toLowerCase().endsWith('.gif') || file.type === 'image/gif') throw new Error('GIF 过大')
  const du = await readDataUrl(file)
  const img = await loadImg(du)
  const r = Math.min(1, MAX_IMG_EDGE / Math.max(img.width, img.height))
  const c = document.createElement('canvas')
  c.width = Math.max(1, Math.round(img.width * r))
  c.height = Math.max(1, Math.round(img.height * r))
  c.getContext('2d').drawImage(img, 0, 0, c.width, c.height)
  const mime = file.type === 'image/png' ? 'image/png' : 'image/jpeg'
  const steps = mime === 'image/png' ? [0.92] : [0.9, 0.82, 0.74, 0.66, 0.58, 0.5]
  let blob = null
  for (const q of steps) { blob = await canvasBlob(c, mime, q); if (blob.size <= MAX_UPLOAD_BYTES) break }
  if (!blob) throw new Error('压缩失败')
  const ext = mime === 'image/png' ? '.png' : '.jpg'
  return new File([blob], (file.name || 'b').replace(/\.[^.]+$/, '') + ext, { type: mime })
}
const uploadImg = async (file) => {
  const cf = await compressImg(file)
  const fd = new FormData(); fd.append('file', cf)
  const resp = await fetch(getUploadUrl(MAP_BUILDING_UPLOAD_FOLDER), {
    method: 'POST', headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` }, body: fd,
  })
  const json = await resp.json()
  if (!resp.ok || json?.code !== 200) throw new Error(json?.msg || '上传失败')
  return json?.data?.url || ''
}

/* ============================================================
   组件
   ============================================================ */
export default function MarkerManage() {
  /* ---- 数据 ---- */
  const [markers, setMarkers] = useState([])
  const [allFacilities, setAllFacilities] = useState([])
  const [loading, setLoading] = useState(true)
  const [typeOptions, setTypeOptions] = useState([])
  const typeLabel = useMemo(() => createFacilityTypeLabelGetter(typeOptions), [typeOptions])

  /* ---- 地图 ---- */
  const [amapOk, setAmapOk] = useState(false)
  const [amapErr, setAmapErr] = useState('')
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const hostRef = useRef(null)
  const ovsRef = useRef([])

  /* ---- 面板开关 ---- */
  const [listVisible, setListVisible] = useState(false)
  const [listClosing, setListClosing] = useState(false)
  const closeList = () => {
    setListClosing(true)
    setTimeout(() => { setListVisible(false); setListClosing(false) }, 200)
  }

  /* ---- 选择 & 编辑 ---- */
  const [selId, setSelId] = useState(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editorMode, setEditorMode] = useState('create')
  const [saving, setSaving] = useState(false)
  const [draft, setDraft] = useState(emptyDraft())
  const [thumbUp, setThumbUp] = useState(false)

  /* ---- 搜索 ---- */
  const [searchKw, setSearchKw] = useState('')
  const [searchBusy, setSearchBusy] = useState(false)
  const [searchHits, setSearchHits] = useState([])
  const [activePoi, setActivePoi] = useState(null)
  const [tableKw, setTableKw] = useState('')
  const [typeFilter, setTypeFilter] = useState(null)

  /* ---- 图例 ---- */
  const [hiddenTypes, setHiddenTypes] = useState(new Set())

  /* ---- 批量操作 ---- */
  const [selKeys, setSelKeys] = useState([])

  /* ---- 快速添加模式 ---- */
  const [quickAdd, setQuickAdd] = useState(false)

  /* ---- 地图图层 ---- */
  const [satellite, setSatellite] = useState(false)

  /* ---- 选中的 marker 信息（用于地图上的详情弹窗） ---- */
  const [popupMarker, setPopupMarker] = useState(null)

  /* ---- 分页 ---- */
  const [pgn, setPgn] = useState({ cur: 1, size: 15 })

  /* ---- 派生 ---- */
  const selMarker = useMemo(() => markers.find((m) => m.id === selId) || null, [markers, selId])
  const visMarkers = useMemo(() => hiddenTypes.size ? markers.filter((m) => !hiddenTypes.has(m.facilityType)) : markers, [markers, hiddenTypes])
  const filtered = useMemo(() => {
    let r = visMarkers
    if (typeFilter) r = r.filter((m) => m.facilityType === typeFilter)
    if (tableKw) { const k = tableKw.toLowerCase(); r = r.filter((m) => (m.markerName || '').toLowerCase().includes(k)) }
    return r
  }, [visMarkers, typeFilter, tableKw])
  const unmarked = useMemo(() => {
    const ids = new Set(markers.map((m) => m.facilityId))
    const list = allFacilities.filter((f) => !ids.has(f.id))
    const bt = {}; list.forEach((f) => { const t = f.facilityType || 99; bt[t] = (bt[t] || 0) + 1 })
    return { total: list.length, byType: bt }
  }, [allFacilities, markers])
  const typeCounts = useMemo(() => { const c = {}; markers.forEach((m) => { c[m.facilityType] = (c[m.facilityType] || 0) + 1 }); return c }, [markers])

  function emptyDraft() { return { facilityName: '', facilityType: 1, location: '', description: '', status: 1, longitude: '', latitude: '', imageX: '', imageY: '', thumbnailUrl: '' } }

  /* ---- 加载 ---- */
  const refreshMarkers = useCallback(async () => {
    try {
      const { data } = await getMarkerList({ page: 1, size: 500 })
      const rows = (data?.records || []).map((m) => {
        const imgs = parseImgs(m.images)
        return { ...m, thumbnailUrl: m.thumbnailUrl || imgs[0] || '', position: m.longitude && m.latitude ? `${m.longitude}, ${m.latitude}` : '-' }
      })
      setMarkers(rows)
    } catch (e) { message.error(e?.message || '加载失败') }
  }, [])

  const refreshFacilities = useCallback(async () => {
    try {
      const rs = await Promise.allSettled([1, 2, 3, 4, 5].map((t) => getFacilityList({ type: t, page: 1, size: 500 })))
      setAllFacilities(rs.filter((r) => r.status === 'fulfilled').flatMap((r) => r.value.data?.records || []))
    } catch { /* noop */ }
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      try { const tr = await getFacilityTypes().catch(() => ({ data: null })); if (!cancelled && tr.data?.length) setTypeOptions(toFacilityTypeOptions(tr.data)) } catch {}
      await Promise.all([refreshMarkers(), refreshFacilities()])
      if (!cancelled) setLoading(false)
    })()
    return () => { cancelled = true }
  }, [refreshMarkers, refreshFacilities])

  /* ---- 地图生命周期 ---- */
  useEffect(() => { let c = false; loadAmap().then(() => { if (!c) { setAmapOk(true); setAmapErr('') } }).catch((e) => { if (!c) setAmapErr(e?.message || '加载失败') }); return () => { c = true } }, [])
  useEffect(() => () => { if (mapRef.current) { try { mapRef.current.destroy() } catch {} mapRef.current = null } }, [])

  const clearOvs = () => { ovsRef.current.forEach((o) => { try { o.setMap(null) } catch {} }); ovsRef.current = [] }

  const buildMap = (ct) => {
    if (!ct || !window.AMap) return null
    if (mapRef.current && hostRef.current === ct) return mapRef.current
    if (mapRef.current) { clearOvs(); try { mapRef.current.destroy() } catch {} mapRef.current = null }
    mapRef.current = new window.AMap.Map(ct, { zoom: DEFAULT_ZOOM, center: [DEFAULT_CENTER.lng, DEFAULT_CENTER.lat], resizeEnable: true, mapStyle: 'amap://styles/normal' })
    hostRef.current = ct
    return mapRef.current
  }
  const resizeMap = (m) => { if (m?.resize) requestAnimationFrame(() => { m.resize(); requestAnimationFrame(() => m.resize()) }) }

  const getCenter = () => {
    const [pl, pa] = [toNum(activePoi?.longitude), toNum(activePoi?.latitude)]; if (pl != null && pa != null) return [pl, pa]
    const [dl, da] = [toNum(draft.longitude), toNum(draft.latitude)]; if (editorOpen && dl != null && da != null) return [dl, da]
    const [sl, sa] = [toNum(selMarker?.longitude), toNum(selMarker?.latitude)]; if (sl != null && sa != null) return [sl, sa]
    return [DEFAULT_CENTER.lng, DEFAULT_CENTER.lat]
  }

  // 快速添加：点击地图直接生成设施 + 标点
  const quickCreateFacility = async (lng, lat, name) => {
    try {
      // 1. 创建设施
      await apiCreateFacility({
        facilityName: name,
        facilityType: 99,
        location: '',
        description: '',
        status: 1,
        longitude: lng,
        latitude: lat,
        imageX: null,
        imageY: null,
        images: '[]',
      })
      // 2. 刷新设施列表，拿到新设施 ID
      const facRes = await getFacilityList({ type: 99, page: 1, size: 500 })
      const newFac = (facRes.data?.records || []).find((f) => f.facilityName === name)
      if (!newFac) throw new Error('设施创建后未找到')
      // 3. 创建标点
      const tok = localStorage.getItem('token') || ''
      await fetch('/api/v1/map/marker', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tok}` },
        body: JSON.stringify({ facilityId: newFac.id, iconUrl: '', sort: 0 }),
      })
      // 4. 刷新标点列表，并选中新标点
      const markerRes = await getMarkerList({ page: 1, size: 500 })
      const newMarker = (markerRes.data?.records || []).find((m) => m.markerName === name)
      setMarkers((markerRes.data?.records || []).map((m) => {
        const imgs = parseImgs(m.images)
        return { ...m, thumbnailUrl: m.thumbnailUrl || imgs[0] || '', position: m.longitude && m.latitude ? `${m.longitude}, ${m.latitude}` : '-' }
      }))
      if (newMarker) { setSelId(newMarker.id); setPopupMarker(newMarker) }
      await refreshFacilities()
      message.success(`已添加: ${name}`)
    } catch (e) { message.error('快速添加失败: ' + (e?.message || '')) }
  }

  /* ---- 渲染 overlays ---- */
  useEffect(() => {
    if (!amapOk) return
    const map = buildMap(containerRef.current); if (!map) return
    resizeMap(map)
    map.setZoomAndCenter(DEFAULT_ZOOM, getCenter())

    const clickH = (e) => {
      // 点击空白处关闭详情弹窗
      setPopupMarker(null)
      setSelId(null)

      const pix = e?.pixel
      const cv = pix && typeof map.containerToLngLat === 'function' ? map.containerToLngLat(pix) : null
      const [rl, rp] = [Number(e.lnglat?.getLng?.() ?? e.lnglat?.lng), Number(e.lnglat?.getLat?.() ?? e.lnglat?.lat)]
      const [cl, cp] = [Number(cv?.getLng?.() ?? cv?.lng), Number(cv?.getLat?.() ?? cv?.lat)]
      const uR = isChinaCoord(rl, rp), uC = isChinaCoord(cl, cp)
      if (!uR && !uC) { return }
      const lng = uR ? rl : cl, lat = uR ? rp : cp

      // 快速添加模式：直接创建标点
      if (quickAdd) {
        setActivePoi(null)
        const name = `新标点 ${new Date().toLocaleTimeString()}`
        quickCreateFacility(lng, lat, name)
        return
      }

      // 编辑器模式：回填到表单
      if (editorOpen && editorMode !== 'image') {
        setActivePoi(null)
        setDraft((p) => ({ ...p, longitude: roundCoord(lng), latitude: roundCoord(lat), imageX: '', imageY: '' }))
      }
    }
    map.on('click', clickH)
    clearOvs()

    const ovs = []
    visMarkers.forEach((m) => {
      const [l, a] = [toNum(m.longitude), toNum(m.latitude)]; if (l == null || a == null) return
      const meta = tmeta(m.facilityType), sel = m.id === selId
      const el = document.createElement('div')
      el.className = `marker-custom-pin${sel ? ' marker-custom-pin--selected' : ''}`
      el.style.background = meta.color; el.textContent = meta.short; el.title = m.markerName || ''
      const mk = new window.AMap.Marker({ map, position: [l, a], content: el, offset: new window.AMap.Pixel(-13, -13), zIndex: sel ? 140 : 100 })
      mk.on('click', () => { setSelId(m.id); setPopupMarker(m); map.setZoomAndCenter(Math.max(map.getZoom() || 16, 17), [l, a]) })
      ovs.push(mk)
    })

    if (editorOpen && draft.longitude && draft.latitude) {
      const [dl, da] = [toNum(draft.longitude), toNum(draft.latitude)]
      if (dl != null && da != null) {
        const pin = document.createElement('div'); pin.className = 'marker-edit-pin'
        ovs.push(new window.AMap.Marker({ map, position: [dl, da], content: pin, offset: new window.AMap.Pixel(-11, -11), zIndex: 150, label: { content: draft.facilityName || '待保存', direction: 'top' } }))
      }
    }
    if (activePoi?.longitude && activePoi?.latitude) {
      const pin = document.createElement('div'); pin.className = 'marker-search-pin'
      ovs.push(new window.AMap.Marker({ map, position: [Number(activePoi.longitude), Number(activePoi.latitude)], content: pin, offset: new window.AMap.Pixel(-10, -10), zIndex: 160, label: { content: activePoi.name || '搜索', direction: 'top' } }))
    }

    ovsRef.current = ovs
    return () => { map.off('click', clickH); clearOvs() }
  }, [amapOk, visMarkers, selId, editorOpen, draft, activePoi, editorMode, quickAdd])

  useEffect(() => {
    if (!containerRef.current || typeof ResizeObserver === 'undefined') return
    const o = new ResizeObserver(() => { if (mapRef.current) resizeMap(mapRef.current) })
    o.observe(containerRef.current); return () => o.disconnect()
  }, [amapOk])

  useEffect(() => {
    if (!visMarkers.length) { setSelId(null); return }
    if (!visMarkers.some((r) => r.id === selId)) setSelId(visMarkers[0]?.id ?? null)
  }, [visMarkers])

  /* ---- POI 搜索 ---- */
  const doSearch = async (kw) => {
    const q = (kw ?? searchKw).trim()
    if (!q) { message.warning('请输入关键词'); return }
    if (!amapOk) { message.warning('地图未就绪'); return }
    setSearchBusy(true)
    try {
      await amapPlugin('AMap.PlaceSearch')
      const ps = new window.AMap.PlaceSearch({ pageSize: 10, pageIndex: 1, citylimit: false })
      const pois = await new Promise((rs, rj) => {
        ps.search(q, (s, r) => { if (s !== 'complete') { rj(new Error(r?.info || '搜索失败')); return } rs(Array.isArray(r?.poiList?.pois) ? r.poiList.pois : []) })
      })
      const items = pois.map((p, i) => {
        const [ln, la] = [toNum(p.location?.lng ?? p.location?.getLng?.()), toNum(p.location?.lat ?? p.location?.getLat?.())]
        if (ln == null || la == null) return null
        return { id: p.id || `p-${i}`, name: p.name || `地点${i + 1}`, address: [p.pname, p.cityname, p.adname, p.address].filter(Boolean).join(' '), longitude: ln, latitude: la }
      }).filter(Boolean)
      setSearchHits(items)
      if (items.length) pickPoi(items[0]); else message.info('未找到')
    } catch (e) { message.error(e?.message || '搜索失败'); setSearchHits([]) } finally { setSearchBusy(false) }
  }

  const pickPoi = (poi) => {
    setActivePoi(poi)
    const [ln, la] = [roundCoord(poi.longitude), roundCoord(poi.latitude)]
    setDraft((p) => ({ ...p, facilityName: p.facilityName || poi.name || '', location: p.location || poi.address || '', longitude: ln, latitude: la, imageX: '', imageY: '' }))
    if (mapRef.current && ln && la) mapRef.current.setZoomAndCenter(17, [Number(ln), Number(la)])
  }

  /* ---- 编辑器 ---- */
  const openCreate = () => { setEditorMode('create'); setSearchHits([]); setActivePoi(null); setDraft(emptyDraft()); setEditorOpen(true) }
  const openEdit = (m) => {
    const marker = m || selMarker
    if (!marker) { message.warning('请先选中标点'); return }
    setSelId(marker.id)
    setEditorMode('reposition'); setSearchHits([]); setActivePoi(null)
    const imgs = parseImgs(marker.images)
    setDraft({ facilityName: marker.markerName || '', facilityType: marker.facilityType || 1, location: marker.location || '', description: marker.description || '', status: marker.status || 1, longitude: marker.longitude ? String(marker.longitude) : '', latitude: marker.latitude ? String(marker.latitude) : '', imageX: marker.imageX ? String(marker.imageX) : '', imageY: marker.imageY ? String(marker.imageY) : '', thumbnailUrl: marker.thumbnailUrl || imgs[0] || '' })
    setEditorOpen(true)
  }
  const openImgEdit = (m) => {
    const marker = m || selMarker
    if (!marker) { message.warning('请先选中标点'); return }
    setSelId(marker.id)
    setEditorMode('image'); setSearchHits([]); setActivePoi(null)
    const imgs = parseImgs(marker.images)
    setDraft({ facilityName: marker.markerName || '', facilityType: marker.facilityType || 1, location: marker.location || '', description: marker.description || '', status: marker.status || 1, longitude: marker.longitude ? String(marker.longitude) : '', latitude: marker.latitude ? String(marker.latitude) : '', imageX: marker.imageX ? String(marker.imageX) : '', imageY: marker.imageY ? String(marker.imageY) : '', thumbnailUrl: marker.thumbnailUrl || imgs[0] || '' })
    setEditorOpen(true)
  }

  const saveDraft = async () => {
    const [l, a] = [toNum(draft.longitude), toNum(draft.latitude)]
    if (!draft.facilityName.trim()) { message.warning('请填写名称'); return }
    if (editorMode !== 'image' && (l == null || a == null)) { message.warning('请填写经纬度'); return }
    setSaving(true)
    try {
      const payload = { facilityName: draft.facilityName.trim(), facilityType: draft.facilityType, location: draft.location, description: draft.description, status: draft.status, longitude: l ?? selMarker?.longitude, latitude: a ?? selMarker?.latitude, imageX: null, imageY: null, images: buildImgsJson(draft.thumbnailUrl) }
      if (editorMode === 'create') await apiCreateFacility(payload)
      else await apiUpdateFacility(selMarker.facilityId, payload)
      await Promise.all([refreshMarkers(), refreshFacilities()])
      setEditorOpen(false)
      message.success(editorMode === 'create' ? '新增成功' : editorMode === 'image' ? '缩略图已更新' : '已更新')
    } catch (e) { message.error(e?.message || '保存失败') } finally { setSaving(false) }
  }

  const doDelete = async (r) => {
    console.log('🗑 doDelete called with:', r?.id, r?.markerName)
    if (!r || !r.id) { console.error('❌ Invalid record:', r); message.error('标点数据异常，无法删除'); return }
    const prevMarkers = markers
    const prevSelId = selId
    console.log('🗑 Removing from list, id=', r.id)
    setMarkers((prev) => prev.filter((m) => m.id !== r.id))
    if (selId === r.id) setSelId(null)
    setSelKeys((prev) => prev.filter((k) => k !== r.id))
    try {
      console.log('🗑 Calling deleteMarker API with id=', r.id)
      await deleteMarker(r.id)
      console.log('🗑 API success')
      await refreshFacilities()
      message.success('已删除')
    } catch (e) {
      console.error('🗑 API failed, restoring:', e)
      setMarkers(prevMarkers)
      if (prevSelId) setSelId(prevSelId)
      message.error(e?.message || '删除失败，请重试')
    }
  }

  const doBatch = async () => {
    const ids = new Set(markers.map((m) => m.facilityId))
    const uids = allFacilities.filter((f) => !ids.has(f.id)).map((f) => f.id)
    if (!uids.length) { message.info('全部已标点'); return }
    try {
      const tok = localStorage.getItem('token') || ''
      await Promise.all(uids.map((fid) => fetch('/api/v1/map/marker', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tok}` }, body: JSON.stringify({ facilityId: fid, iconUrl: '', sort: 0 }) })))
      message.success(`已生成 ${uids.length} 个标点`)
      await Promise.all([refreshMarkers(), refreshFacilities()])
    } catch { message.error('批量生成失败') }
  }

  const doBatchDelete = async () => {
    if (!selKeys.length) { message.warning('请先勾选标点'); return }
    const prevMarkers = markers
    const prevSelId = selId
    const toDelete = new Set(selKeys)
    setMarkers((prev) => prev.filter((m) => !toDelete.has(m.id)))
    setSelKeys([])
    if (toDelete.has(selId)) setSelId(null)
    try {
      await Promise.all([...toDelete].map((id) => deleteMarker(id)))
      await refreshFacilities()
      message.success(`已删除 ${toDelete.size} 个标点`)
    } catch (e) {
      setMarkers(prevMarkers)
      if (prevSelId) setSelId(prevSelId)
      message.error('批量删除失败，已恢复')
    }
  }

  // 复制坐标
  const copyCoord = (lng, lat) => {
    const text = `${lng}, ${lat}`
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => message.success('坐标已复制')).catch(() => message.info(text))
    } else {
      const ta = document.createElement('textarea'); ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0'
      document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
      message.success('坐标已复制')
    }
  }

  // 切换卫星图层
  const toggleSatellite = () => {
    const next = !satellite
    setSatellite(next)
    if (mapRef.current) {
      try {
        if (next) {
          // AMap v2 卫星图层
          const TileLayer = window.AMap.TileLayer
          if (TileLayer && typeof TileLayer.Satellite === 'function') {
            const satLayer = new TileLayer.Satellite()
            mapRef.current.setLayers([satLayer])
          } else {
            // 降级：切换为深色样式
            mapRef.current.setMapStyle('amap://styles/dark')
          }
        } else {
          mapRef.current.setLayers([])
          mapRef.current.setMapStyle('amap://styles/normal')
        }
      } catch {
        // 如果 setLayers 不可用，仅切换样式
        try { mapRef.current.setMapStyle(next ? 'amap://styles/dark' : 'amap://styles/normal') } catch {}
      }
    }
  }

  const doLocate = (r) => {
    setSelId(r.id)
    setPopupMarker(r)
    const [l, a] = [toNum(r.longitude), toNum(r.latitude)]
    if (mapRef.current && l != null && a != null) mapRef.current.setZoomAndCenter(Math.max(mapRef.current.getZoom() || 16, 17), [l, a])
  }

  const toggleType = (t) => setHiddenTypes((p) => { const n = new Set(p); n.has(t) ? n.delete(t) : n.add(t); return n })

  /* ---- 表格列 ---- */
  const cols = [
    { title: '缩略图', dataIndex: 'thumbnailUrl', width: 60, render: (v) => v ? <Image src={v} width={38} height={38} style={{ objectFit: 'cover', borderRadius: 8 }} /> : <span style={{ color: '#cbd5e1', fontSize: 10 }}>—</span> },
    { title: '名称', dataIndex: 'markerName', ellipsis: true, render: (v) => <span style={{ fontWeight: 600, color: '#0f172a' }}>{v || '-'}</span> },
    { title: '类型', dataIndex: 'facilityType', width: 75, render: (v, r) => { const m = tmeta(v); return <Tag style={{ border: 'none', background: m.color, color: '#fff', borderRadius: 8, fontWeight: 600, padding: '1px 8px', fontSize: 11 }}>{typeLabel(v, r.facilityTypeName)}</Tag> } },
    { title: '状态', dataIndex: 'status', width: 65, render: (v) => { const s = STATUS_MAP[v] || { label: v, color: 'default' }; return <Tag color={s.color} style={{ borderRadius: 8, fontSize: 11 }}>{s.label}</Tag> } },
    { title: '', key: 'actions', width: 52, render: (_, r) => (
      <span style={{ color: '#94a3b8', fontSize: 11 }}>选中查看</span>
    ) },
  ]

  /* ============================================================
     RENDER
     ============================================================ */
  return (
    <div className="marker-page">

      {/* ---- Hero ---- */}
      <section className="marker-hero">
        <div className="marker-hero__info">
          <span className="workspace-badge" style={{ display: 'inline-flex', marginBottom: 4 }}>校园设施</span>
          <h2>标点管理</h2>
          <p>管理校园地图设施标点 — 搜索地点、新增标点、编辑位置与缩略图</p>
        </div>
        <Space wrap>
          <Input.Search allowClear placeholder="搜索高德地点..." value={searchKw} onChange={(e) => setSearchKw(e.target.value)} onSearch={doSearch} loading={searchBusy} style={{ width: 220 }} />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate} style={{ borderRadius: 12, fontWeight: 600 }}>新增标点</Button>
        </Space>
      </section>

      {/* ---- 统计卡片 ---- */}
      <div className="marker-stats-row">
        <Card className="marker-stat-card"><div className="marker-stat-card__inner"><div className="marker-stat-card__icon" style={{ background: 'linear-gradient(135deg,#2563eb,#1d4ed8)' }}><PushpinOutlined /></div><div className="marker-stat-card__text"><strong>{markers.length}</strong><span>已标点设施</span></div></div></Card>
        <Card className={`marker-stat-card${unmarked.total > 0 ? '' : ''}`} style={unmarked.total > 0 ? { background: 'linear-gradient(180deg, rgba(255,247,237,0.9), rgba(255,255,255,0.98))' } : {}}><div className="marker-stat-card__inner"><div className="marker-stat-card__icon" style={{ background: unmarked.total > 0 ? 'linear-gradient(135deg,#f97316,#ea580c)' : 'linear-gradient(135deg,#1DD1A1,#10b981)' }}>{unmarked.total > 0 ? <WarningOutlined /> : <CheckCircleOutlined />}</div><div className="marker-stat-card__text"><strong>{unmarked.total > 0 ? unmarked.total : markers.length}</strong><span>{unmarked.total > 0 ? '未标点设施' : '全部已标点 ✓'}</span></div></div></Card>
        {typeOptions.slice(0, 4).map(({ value }) => { const m = tmeta(value); const c = typeCounts[value] || 0; return <Card key={value} className="marker-stat-card"><div className="marker-stat-card__inner"><div className="marker-stat-card__icon" style={{ background: m.color }}><span style={{ fontSize: 13, fontWeight: 700 }}>{m.short}</span></div><div className="marker-stat-card__text"><strong>{c}</strong><span>{typeLabel(value)}</span></div></div></Card> })}
      </div>

      {/* 未标点提示 */}
      {unmarked.total > 0 ? (
        <div style={{ padding: '10px 16px', borderRadius: 14, background: 'linear-gradient(135deg, rgba(255,247,237,0.8), rgba(254,243,199,0.5))', border: '1px solid rgba(251,191,36,0.22)', display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <WarningOutlined style={{ color: '#d97706' }} />
          <span style={{ fontSize: 12, color: '#92400e', flex: 1 }}>还有 <strong>{unmarked.total}</strong> 个设施未标点 {Object.entries(unmarked.byType).map(([t, c]) => { const m = tmeta(Number(t)); return <Tag key={t} style={{ marginLeft: 4, borderRadius: 6, fontSize: 11, background: m.color, border: 'none', color: '#fff' }}>{m.label}×{c}</Tag> })}</span>
          <Button type="primary" size="small" icon={<ThunderboltOutlined />} onClick={doBatch} ghost style={{ borderRadius: 8 }}>一键生成</Button>
        </div>
      ) : null}

      {/* POI 结果 */}
      {searchHits.length > 0 ? (
        <div className="marker-search-results">
          {searchHits.map((p) => (
            <div key={p.id} className={`marker-search-result${activePoi?.id === p.id ? ' active' : ''}`} onClick={() => pickPoi(p)}>
              <strong>{p.name}</strong><span>{p.address}</span><em>{p.longitude}, {p.latitude}</em>
            </div>
          ))}
        </div>
      ) : null}

      {/* ---- 地图全屏 ---- */}
      <div className="marker-body">
        <Card className="marker-map-card">
          <div className="marker-map-shell">
            <div ref={containerRef} className="marker-map-canvas" />
            {amapErr ? <div className="marker-map-error">{amapErr}</div> : null}
            {!amapOk && !amapErr ? <div className="marker-map-loading"><Skeleton active paragraph={{ rows: 1 }} title={false} /></div> : null}

            {/* 图例 */}
            {typeOptions.length > 0 ? (
              <div className="marker-legend">
                {typeOptions.map(({ value }) => { const m = tmeta(value); const hid = hiddenTypes.has(value); return (
                  <div key={value} className={`marker-legend__item${hid ? ' marker-legend__item--hidden' : ''}`} onClick={() => toggleType(value)}>
                    <span className="marker-legend__dot" style={{ background: m.color }} />
                    <span>{typeLabel(value)}</span>
                    <span style={{ color: '#94a3b8', fontSize: 10, marginLeft: 'auto' }}>{typeCounts[value] || 0}</span>
                  </div>
                )})}
              </div>
            ) : null}

            {/* 地图右上角浮动按钮组 */}
            {!listVisible ? (
              <div style={{ position: 'absolute', top: 14, right: 14, zIndex: 15, display: 'flex', flexDirection: 'column', gap: 8 }}>
                <Badge count={markers.length} size="small" offset={[-2, 2]}>
                  <Button className="marker-float-btn" icon={<MenuOutlined />} onClick={() => setListVisible(true)} title="展开标点列表" />
                </Badge>
                <Button className="marker-float-btn" icon={<GlobalOutlined />} onClick={toggleSatellite} title={satellite ? '切换普通地图' : '切换卫星地图'} style={satellite ? { color: '#2563eb', borderColor: '#2563eb' } : {}} />
                <Button className="marker-float-btn" icon={<ThunderboltOutlined />} onClick={() => { setQuickAdd(!quickAdd); if (!quickAdd) message.info('快速添加模式：点击地图即可新建标点') }} title="快速添加模式" style={quickAdd ? { color: '#f97316', borderColor: '#f97316', background: 'rgba(255,247,237,0.94)' } : {}} />
              </div>
            ) : null}

            {/* 选中标点详情弹窗 */}
            {(popupMarker || selMarker) ? (() => {
              const p = popupMarker || selMarker
              const meta = tmeta(p.facilityType)
              return (
                <div className="marker-popup-card">
                  <button className="marker-popup-card__close" onClick={() => { setPopupMarker(null); setSelId(null) }} title="关闭详情"><CloseOutlined /></button>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                    {p.thumbnailUrl ? <Image src={p.thumbnailUrl} width={72} height={54} style={{ objectFit: 'cover', borderRadius: 10, flexShrink: 0 }} /> : <div style={{ width: 72, height: 54, borderRadius: 10, background: '#e8ecf1', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, fontSize: 20 }}>{meta.short}</div>}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4, paddingRight: 24 }}>
                        <strong style={{ fontSize: 15 }}>{p.markerName || '—'}</strong>
                        <Tag style={{ border: 'none', background: meta.color, color: '#fff', borderRadius: 8, fontSize: 11, padding: '0 8px' }}>{typeLabel(p.facilityType, p.facilityTypeName)}</Tag>
                        {p.status != null ? <Tag color={STATUS_MAP[p.status]?.color || 'default'} style={{ borderRadius: 8, fontSize: 11 }}>{STATUS_MAP[p.status]?.label || p.status}</Tag> : null}
                      </div>
                      <div style={{ fontSize: 12, color: '#64748b', marginBottom: 4 }}>{p.location || '未设置位置'}</div>
                      <div style={{ fontSize: 11, color: '#94a3b8', fontFamily: 'monospace', marginBottom: 6 }}>{p.longitude}, {p.latitude}</div>
                      <Space size={6} wrap style={{ marginTop: 2 }}>
                        <Button size="small" onClick={() => copyCoord(p.longitude, p.latitude)} style={{ borderRadius: 8 }}>复制坐标</Button>
                        <Button size="small" icon={<EnvironmentOutlined />} onClick={() => openEdit(p)} style={{ borderRadius: 8 }}>编辑</Button>
                        <Button size="small" danger type="primary" icon={<DeleteOutlined />}
                          onClick={() => {
                            if (window.confirm('确定删除标点「' + (p.markerName || p.id) + '」吗？删除后不可恢复。')) {
                              doDelete(p)
                            }
                          }}
                          style={{ borderRadius: 8, fontWeight: 600 }}
                        >删除标点</Button>
                      </Space>
                    </div>
                  </div>
                </div>
              )
            })() : null}
          </div>
        </Card>

        {/* 右侧悬浮面板 */}
        {listVisible ? (
          <div className={`marker-list-overlay${listClosing ? ' marker-list-overlay--closing' : ''}`}>
            {/* 头部 */}
            <div className="marker-list-overlay__head">
              <h3><PushpinOutlined style={{ marginRight: 6 }} />标点列表<small>共 {markers.length} 个</small></h3>
              <Space size={4}>
                <Select allowClear placeholder="类型" size="small" style={{ width: 100 }} value={typeFilter} options={typeOptions} onChange={(v) => { setTypeFilter(v); setPgn((p) => ({ ...p, cur: 1 })) }} />
                <Button type="text" size="small" icon={<CloseOutlined />} onClick={closeList} />
              </Space>
            </div>

            {/* 搜索栏 */}
            <div className="marker-list-overlay__tools">
              <Input allowClear placeholder="搜索名称..." prefix={<SearchOutlined />} size="small" value={tableKw} onChange={(e) => { setTableKw(e.target.value); setPgn((p) => ({ ...p, cur: 1 })) }} style={{ flex: 1 }} />
              <Button size="small" type={quickAdd ? 'primary' : 'default'} icon={<ThunderboltOutlined />} onClick={() => setQuickAdd(!quickAdd)} style={{ borderRadius: 8, background: quickAdd ? '#f97316' : undefined, borderColor: quickAdd ? '#f97316' : undefined }} title="快速添加模式"></Button>
              <Button size="small" type="primary" icon={<PlusOutlined />} onClick={openCreate} style={{ borderRadius: 8 }}>新增</Button>
              {selKeys.length > 0 ? (
                <Button size="small" danger icon={<DeleteOutlined />} style={{ borderRadius: 8 }}
                  onClick={() => { if (window.confirm(`确定删除选中的 ${selKeys.length} 个标点吗？`)) doBatchDelete() }}>删除({selKeys.length})</Button>
              ) : null}
            </div>

            {/* 表格 */}
            <div className="marker-list-overlay__table">
              <Table
                columns={cols}
                dataSource={filtered}
                loading={loading}
                rowKey="id"
                size="small"
                showHeader={true}
                scroll={{ y: 'calc(100vh - 440px)' }}
                rowSelection={{
                  selectedRowKeys: selKeys,
                  onChange: (keys) => setSelKeys(keys),
                  columnWidth: 32,
                }}
                onRow={(r) => ({ onClick: () => { setSelId(r.id); setPopupMarker(r) }, className: r.id === selId ? 'active-row' : '' })}
                locale={{ emptyText: '暂无标点' }}
                pagination={{
                  current: pgn.cur, pageSize: pgn.size, total: filtered.length,
                  showSizeChanger: true, pageSizeOptions: ['10', '15', '20', '50'],
                  showTotal: (t) => `共 ${t} 个`, size: 'small',
                  onChange: (pg, sz) => setPgn({ cur: pg, size: sz }),
                }}
              />
            </div>
          </div>
        ) : null}
      </div>

      {/* ---- Drawer 编辑器 ---- */}
      <Drawer
        title={editorMode === 'create' ? '新增标点' : editorMode === 'image' ? '上传缩略图' : '编辑标点'}
        placement="right" width={430}
        open={editorOpen} onClose={() => setEditorOpen(false)}
        rootClassName="marker-editor-drawer"
        footer={<div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
          <Button onClick={() => setEditorOpen(false)} style={{ borderRadius: 12 }}>取消</Button>
          <Button type="primary" loading={saving} onClick={saveDraft} style={{ borderRadius: 12, fontWeight: 600 }}>确定</Button>
        </div>}
      >
        <div className="marker-editor-hint">
          {editorMode === 'image' ? '上传的建筑图片将在 App 端展示。' : '填写信息后在地图上点击取经纬度，也可先搜索高德地点。'}
        </div>
        <Form layout="vertical" size="middle">
          <Form.Item label="标记名称" required>
            <Input value={draft.facilityName} onChange={(e) => setDraft((p) => ({ ...p, facilityName: e.target.value }))} placeholder="例如 图书馆" style={{ borderRadius: 12 }} />
          </Form.Item>
          <Form.Item label="设施类型" required>
            <Select value={draft.facilityType} options={typeOptions} onChange={(v) => setDraft((p) => ({ ...p, facilityType: v }))} />
          </Form.Item>
          <Form.Item label="位置说明">
            <Input value={draft.location} onChange={(e) => setDraft((p) => ({ ...p, location: e.target.value }))} placeholder="校园东区南门旁" style={{ borderRadius: 12 }} />
          </Form.Item>
          <Form.Item label="状态">
            <Select value={draft.status} options={Object.entries(STATUS_MAP).map(([k, v]) => ({ value: Number(k), label: v.label }))} onChange={(v) => setDraft((p) => ({ ...p, status: v }))} />
          </Form.Item>
          <Form.Item label="描述">
            <Input.TextArea rows={2} value={draft.description} onChange={(e) => setDraft((p) => ({ ...p, description: e.target.value }))} placeholder="可选" style={{ borderRadius: 12 }} />
          </Form.Item>
          <Form.Item label="建筑缩略图">
            <div className="marker-thumbnail">
              {draft.thumbnailUrl ? <Image src={draft.thumbnailUrl} alt="缩略图" width="100%" style={{ maxHeight: 150, objectFit: 'cover', borderRadius: 14 }} /> : <span className="marker-thumbnail__empty">暂未上传</span>}
              <Space wrap>
                <Upload accept="image/*" showUploadList={false} disabled={thumbUp}
                  customRequest={async ({ file, onSuccess, onError }) => {
                    setThumbUp(true)
                    try { const url = await uploadImg(file); setDraft((p) => ({ ...p, thumbnailUrl: url })); onSuccess?.({ url }); message.success('上传成功') }
                    catch (e) { onError?.(e); message.error(e?.message || '上传失败') }
                    finally { setThumbUp(false) }
                  }}><Button icon={<UploadOutlined />} loading={thumbUp} style={{ borderRadius: 12 }}>上传</Button></Upload>
                {draft.thumbnailUrl ? <Button danger onClick={() => setDraft((p) => ({ ...p, thumbnailUrl: '' }))} style={{ borderRadius: 12 }}>移除</Button> : null}
              </Space>
            </div>
          </Form.Item>
          {editorMode !== 'image' ? (
            <>
              <div className="marker-coord-grid">
                <div><label>经度</label><Input value={draft.longitude} onChange={(e) => setDraft((p) => ({ ...p, longitude: e.target.value }))} placeholder="点击地图取点" style={{ borderRadius: 12 }} /></div>
                <div><label>纬度</label><Input value={draft.latitude} onChange={(e) => setDraft((p) => ({ ...p, latitude: e.target.value }))} placeholder="点击地图取点" style={{ borderRadius: 12 }} /></div>
              </div>
              <div className="marker-editor-hint" style={{ marginTop: 14, marginBottom: 0 }}>在地图上点击取经纬度，支持搜索高德地点后微调。</div>
            </>
          ) : null}
        </Form>
      </Drawer>
    </div>
  )
}
