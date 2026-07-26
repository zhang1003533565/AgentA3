import { useEffect, useMemo, useRef, useState, useCallback } from 'react'
import { Button, Drawer, Form, Input, message, Segmented, Select, Skeleton, Table, Tag } from 'antd'
import {
  AimOutlined, BorderOutlined, CheckCircleOutlined, CopyOutlined,
  EnvironmentOutlined, GlobalOutlined, PushpinOutlined, SearchOutlined,
  UndoOutlined, WarningOutlined,
} from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import { getMarkerList } from '../../../api/map'
import { updateFacility as apiUpdateFacility, getFacilityTypes } from '../../../api/facility'
import { toFacilityTypeOptions, createFacilityTypeLabelGetter } from '../../../config/facilityType'
import './MarkerManage.css'

/* ============================================================
   常量
   ============================================================ */
const AMAP_WEB_KEY = '64bc139adb6a611277fb8f6821b371ac'
const DEFAULT_CENTER = { lng: 114.897014, lat: 40.755502 }
const DEFAULT_ZOOM = 16

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
const isChinaCoord = (l, a) => Number.isFinite(l) && Number.isFinite(a) && l >= 73 && l <= 136 && a >= 3 && a <= 54
const parseBoundaryPoints = (value) => {
  let source = value
  if (typeof value === 'string') {
    try { source = JSON.parse(value) } catch { return [] }
  }
  if (!Array.isArray(source)) return []
  return source.map((point) => {
    if (Array.isArray(point)) return [toNum(point[0]), toNum(point[1])]
    return [toNum(point?.longitude ?? point?.lng), toNum(point?.latitude ?? point?.lat)]
  }).filter(([lng, lat]) => lng != null && lat != null)
}
const getBoundaryCenter = (points) => {
  if (!points.length) return null
  const totals = points.reduce((sum, [lng, lat]) => [sum[0] + lng, sum[1] + lat], [0, 0])
  return [totals[0] / points.length, totals[1] / points.length]
}
const segmentOrientation = (a, b, c) => {
  const value = (b[1] - a[1]) * (c[0] - b[0]) - (b[0] - a[0]) * (c[1] - b[1])
  if (Math.abs(value) < 1e-12) return 0
  return value > 0 ? 1 : 2
}
const pointOnSegment = (a, b, c) => (
  b[0] <= Math.max(a[0], c[0]) && b[0] >= Math.min(a[0], c[0])
  && b[1] <= Math.max(a[1], c[1]) && b[1] >= Math.min(a[1], c[1])
)
const segmentsIntersect = (a, b, c, d) => {
  const o1 = segmentOrientation(a, b, c)
  const o2 = segmentOrientation(a, b, d)
  const o3 = segmentOrientation(c, d, a)
  const o4 = segmentOrientation(c, d, b)
  if (o1 !== o2 && o3 !== o4) return true
  return (o1 === 0 && pointOnSegment(a, c, b))
    || (o2 === 0 && pointOnSegment(a, d, b))
    || (o3 === 0 && pointOnSegment(c, a, d))
    || (o4 === 0 && pointOnSegment(c, b, d))
}
const hasSelfIntersection = (points) => {
  const size = points.length
  for (let first = 0; first < size; first += 1) {
    const firstNext = (first + 1) % size
    for (let second = first + 1; second < size; second += 1) {
      const secondNext = (second + 1) % size
      if (first === second || firstNext === second || secondNext === first) continue
      if (segmentsIntersect(points[first], points[firstNext], points[second], points[secondNext])) return true
    }
  }
  return false
}
const eventPosition = (event, overlay) => {
  const value = event?.lnglat || overlay?.getPosition?.()
  const lng = toNum(value?.getLng?.() ?? value?.lng)
  const lat = toNum(value?.getLat?.() ?? value?.lat)
  return lng == null || lat == null ? null : [lng, lat]
}
const isMarkerUnlocated = (marker) => (
  marker.geometryType === 'AREA'
    ? parseBoundaryPoints(marker.boundaryPoints).length < 3
    : toNum(marker.longitude) == null || toNum(marker.latitude) == null
)

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

/* ============================================================
   组件
   ============================================================ */
export default function MarkerManage() {
  const [searchParams] = useSearchParams()
  const requestedFacilityIdRef = useRef(searchParams.get('facilityId'))

  /* ---- 数据 ---- */
  const [markers, setMarkers] = useState([])
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

  /* ---- 选择 & 编辑 ---- */
  const [selId, setSelId] = useState(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [draft, setDraft] = useState(emptyDraft())
  const [fenceDrawing, setFenceDrawing] = useState(false)

  /* ---- 搜索 ---- */
  const [searchKw, setSearchKw] = useState('')
  const [searchBusy, setSearchBusy] = useState(false)
  const [searchHits, setSearchHits] = useState([])
  const [activePoi, setActivePoi] = useState(null)
  const [tableKw, setTableKw] = useState('')
  const [typeFilter, setTypeFilter] = useState(null)

  /* ---- 图例 ---- */
  const [hiddenTypes, setHiddenTypes] = useState(new Set())

  /* ---- 地图图层 ---- */
  const [satellite, setSatellite] = useState(false)

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
  const unlocated = useMemo(() => {
    const list = markers.filter(isMarkerUnlocated)
    const bt = {}; list.forEach((f) => { const t = f.facilityType || 99; bt[t] = (bt[t] || 0) + 1 })
    return { total: list.length, byType: bt }
  }, [markers])
  const typeCounts = useMemo(() => { const c = {}; markers.forEach((m) => { c[m.facilityType] = (c[m.facilityType] || 0) + 1 }); return c }, [markers])

  function emptyDraft() {
    return {
      facilityName: '',
      facilityType: 1,
      location: '',
      description: '',
      status: 1,
      longitude: '',
      latitude: '',
      imageX: '',
      imageY: '',
      geometryType: 'POINT',
      boundaryPoints: [],
    }
  }

  /* ---- 加载 ---- */
  const refreshMarkers = useCallback(async () => {
    try {
      const { data } = await getMarkerList({ page: 1, size: 500 })
      const rows = (data?.records || []).map((m) => {
        return {
          ...m,
          geometryType: m.geometryType === 'AREA' ? 'AREA' : 'POINT',
          boundaryPoints: parseBoundaryPoints(m.boundaryPoints),
          position: m.longitude && m.latitude ? `${m.longitude}, ${m.latitude}` : '-',
        }
      })
      setMarkers(rows)
    } catch (e) { message.error(e?.message || '加载失败') }
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      try { const tr = await getFacilityTypes().catch(() => ({ data: null })); if (!cancelled && tr.data?.length) setTypeOptions(toFacilityTypeOptions(tr.data)) } catch {}
      await refreshMarkers()
      if (!cancelled) setLoading(false)
    })()
    return () => { cancelled = true }
  }, [refreshMarkers])

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

  /* ---- 渲染 overlays ---- */
  useEffect(() => {
    if (!amapOk) return
    const map = buildMap(containerRef.current); if (!map) return
    resizeMap(map)

    const clickH = (e) => {
      // 非编辑状态下点击空白处才取消列表选择；编辑时必须保留当前设施。
      if (!editorOpen) setSelId(null)

      const pix = e?.pixel
      const cv = pix && typeof map.containerToLngLat === 'function' ? map.containerToLngLat(pix) : null
      const [rl, rp] = [Number(e.lnglat?.getLng?.() ?? e.lnglat?.lng), Number(e.lnglat?.getLat?.() ?? e.lnglat?.lat)]
      const [cl, cp] = [Number(cv?.getLng?.() ?? cv?.lng), Number(cv?.getLat?.() ?? cv?.lat)]
      const uR = isChinaCoord(rl, rp), uC = isChinaCoord(cl, cp)
      if (!uR && !uC) { return }
      const lng = uR ? rl : cl, lat = uR ? rp : cp

      // 编辑器模式：回填到表单
      if (editorOpen) {
        setActivePoi(null)
        setDraft((previous) => {
          if (previous.geometryType === 'AREA') {
            if (!fenceDrawing) return previous
            const boundaryPoints = [...previous.boundaryPoints, [Number(roundCoord(lng)), Number(roundCoord(lat))]]
            const center = getBoundaryCenter(boundaryPoints)
            return {
              ...previous,
              boundaryPoints,
              longitude: center ? roundCoord(center[0]) : previous.longitude,
              latitude: center ? roundCoord(center[1]) : previous.latitude,
              imageX: '',
              imageY: '',
            }
          }
          return { ...previous, longitude: roundCoord(lng), latitude: roundCoord(lat), imageX: '', imageY: '' }
        })
        return
      }

    }
    map.on('click', clickH)
    clearOvs()

    const ovs = []
    let cancelled = false
    const pointRows = []
    visMarkers.forEach((m) => {
      const meta = tmeta(m.facilityType), sel = m.id === selId
      const boundaryPoints = parseBoundaryPoints(m.boundaryPoints)
      if (m.geometryType === 'AREA' && boundaryPoints.length >= 3) {
        const polygon = new window.AMap.Polygon({
          map,
          path: boundaryPoints,
          strokeColor: meta.color,
          strokeWeight: sel ? 4 : 2,
          strokeOpacity: sel ? 1 : 0.85,
          fillColor: meta.color,
          fillOpacity: sel ? 0.28 : 0.16,
          zIndex: sel ? 130 : 90,
          bubble: false,
        })
        polygon.on('click', () => {
          setSelId(m.id)
          if (typeof map.setFitView === 'function') map.setFitView([polygon], false, [80, 80, 80, 80], 18)
        })
        ovs.push(polygon)
        return
      }
      const [l, a] = [toNum(m.longitude), toNum(m.latitude)]; if (l == null || a == null) return
      pointRows.push({ marker: m, lng: l, lat: a, meta, selected: sel })
    })

    const addPointMarker = ({ marker: m, lng: l, lat: a, meta, selected: sel }) => {
      const el = document.createElement('div')
      el.className = `marker-custom-pin${sel ? ' marker-custom-pin--selected' : ''}`
      el.style.background = meta.color; el.textContent = meta.short; el.title = m.markerName || ''
      const mk = new window.AMap.Marker({ map, position: [l, a], content: el, offset: new window.AMap.Pixel(-13, -13), zIndex: sel ? 140 : 100 })
      mk.on('click', () => { setSelId(m.id); map.setZoomAndCenter(Math.max(map.getZoom() || 16, 17), [l, a]) })
      ovs.push(mk)
    }
    if (pointRows.length < 30) {
      pointRows.forEach(addPointMarker)
    } else {
      const pointByPosition = new Map(pointRows.map((item) => [`${item.lng},${item.lat}`, item]))
      amapPlugin('AMap.MarkerCluster').then(() => {
        if (cancelled || !window.AMap?.MarkerCluster) return
        const cluster = new window.AMap.MarkerCluster(
          map,
          pointRows.map((item) => ({ lnglat: [item.lng, item.lat], weight: item.selected ? 10 : 1 })),
          {
            gridSize: 60,
            maxZoom: 16,
            averageCenter: true,
            renderClusterMarker: (context) => {
              const node = document.createElement('div')
              node.className = 'marker-cluster-pin'
              node.textContent = String(context.count)
              context.marker.setContent(node)
              context.marker.setOffset(new window.AMap.Pixel(-19, -19))
            },
            renderMarker: (context) => {
              const position = context.marker.getPosition?.()
              const key = `${toNum(position?.getLng?.() ?? position?.lng)},${toNum(position?.getLat?.() ?? position?.lat)}`
              const item = pointByPosition.get(key)
              const node = document.createElement('div')
              node.className = `marker-custom-pin${item?.selected ? ' marker-custom-pin--selected' : ''}`
              node.style.background = item?.meta?.color || TYPE_META[99].color
              node.textContent = item?.meta?.short || TYPE_META[99].short
              node.title = item?.marker?.markerName || ''
              context.marker.setContent(node)
              context.marker.setOffset(new window.AMap.Pixel(-13, -13))
              if (item && !context.marker.__markerManageBound) {
                context.marker.__markerManageBound = true
                context.marker.setExtData(item)
                context.marker.on('click', () => {
                  const current = context.marker.getExtData()
                  if (!current) return
                  setSelId(current.marker.id)
                  map.setZoomAndCenter(Math.max(map.getZoom() || 16, 17), [current.lng, current.lat])
                })
              } else if (item) {
                context.marker.setExtData(item)
              }
            },
          },
        )
        ovs.push(cluster)
      }).catch(() => {
        if (!cancelled) pointRows.forEach(addPointMarker)
      })
    }

    if (editorOpen && draft.geometryType === 'AREA' && draft.boundaryPoints.length) {
      const path = parseBoundaryPoints(draft.boundaryPoints)
      if (path.length >= 3) {
        ovs.push(new window.AMap.Polygon({
          map,
          path,
          strokeColor: '#2563eb',
          strokeWeight: 3,
          strokeStyle: 'dashed',
          fillColor: '#2563eb',
          fillOpacity: 0.16,
          zIndex: 150,
          bubble: fenceDrawing,
        }))
      } else if (path.length >= 2) {
        ovs.push(new window.AMap.Polyline({
          map,
          path,
          strokeColor: '#2563eb',
          strokeWeight: 3,
          strokeStyle: 'dashed',
          zIndex: 150,
          bubble: fenceDrawing,
        }))
      }
      path.forEach((point, index) => {
        const node = document.createElement('button')
        node.type = 'button'
        node.className = 'marker-fence-node'
        node.title = fenceDrawing ? `边界点 ${index + 1}` : `拖动调整边界点 ${index + 1}，右键删除`
        const marker = new window.AMap.Marker({
          map,
          position: point,
          content: node,
          offset: new window.AMap.Pixel(-7, -7),
          zIndex: 160 + index,
          draggable: !fenceDrawing,
          bubble: false,
        })
        if (!fenceDrawing) {
          marker.on('dragend', (event) => {
            const position = eventPosition(event, marker)
            if (!position) return
            setDraft((previous) => {
              const boundaryPoints = previous.boundaryPoints.map((item, pointIndex) => (
                pointIndex === index ? [Number(roundCoord(position[0])), Number(roundCoord(position[1]))] : item
              ))
              const center = getBoundaryCenter(boundaryPoints)
              return {
                ...previous,
                boundaryPoints,
                longitude: center ? roundCoord(center[0]) : '',
                latitude: center ? roundCoord(center[1]) : '',
              }
            })
          })
          marker.on('rightclick', () => {
            setDraft((previous) => {
              if (previous.boundaryPoints.length <= 3) {
                message.warning('区域围栏至少保留3个边界点')
                return previous
              }
              const boundaryPoints = previous.boundaryPoints.filter((_, pointIndex) => pointIndex !== index)
              const center = getBoundaryCenter(boundaryPoints)
              return {
                ...previous,
                boundaryPoints,
                longitude: center ? roundCoord(center[0]) : '',
                latitude: center ? roundCoord(center[1]) : '',
              }
            })
          })
        }
        ovs.push(marker)
      })
      if (!fenceDrawing && path.length >= 3) {
        path.forEach((point, index) => {
          const nextIndex = (index + 1) % path.length
          const nextPoint = path[nextIndex]
          const midpoint = [(point[0] + nextPoint[0]) / 2, (point[1] + nextPoint[1]) / 2]
          const node = document.createElement('button')
          node.type = 'button'
          node.className = 'marker-fence-mid-node'
          node.title = '点击新增边界点'
          const marker = new window.AMap.Marker({
            map,
            position: midpoint,
            content: node,
            offset: new window.AMap.Pixel(-5, -5),
            zIndex: 155,
            bubble: false,
          })
          marker.on('click', () => {
            setDraft((previous) => {
              const boundaryPoints = [...previous.boundaryPoints]
              boundaryPoints.splice(nextIndex, 0, [Number(roundCoord(midpoint[0])), Number(roundCoord(midpoint[1]))])
              const center = getBoundaryCenter(boundaryPoints)
              return {
                ...previous,
                boundaryPoints,
                longitude: center ? roundCoord(center[0]) : '',
                latitude: center ? roundCoord(center[1]) : '',
              }
            })
          })
          ovs.push(marker)
        })
      }
    } else if (editorOpen && draft.longitude && draft.latitude) {
      const [dl, da] = [toNum(draft.longitude), toNum(draft.latitude)]
      if (dl != null && da != null) {
        const pin = document.createElement('div'); pin.className = 'marker-edit-pin'
        const marker = new window.AMap.Marker({
          map,
          position: [dl, da],
          content: pin,
          offset: new window.AMap.Pixel(-11, -11),
          zIndex: 150,
          draggable: true,
          label: { content: draft.facilityName || '待保存', direction: 'top' },
        })
        marker.on('dragend', (event) => {
          const position = eventPosition(event, marker)
          if (!position) return
          setDraft((previous) => ({
            ...previous,
            longitude: roundCoord(position[0]),
            latitude: roundCoord(position[1]),
            imageX: '',
            imageY: '',
          }))
        })
        ovs.push(marker)
      }
    }
    if (activePoi?.longitude && activePoi?.latitude) {
      const pin = document.createElement('div'); pin.className = 'marker-search-pin'
      ovs.push(new window.AMap.Marker({ map, position: [Number(activePoi.longitude), Number(activePoi.latitude)], content: pin, offset: new window.AMap.Pixel(-10, -10), zIndex: 160, label: { content: activePoi.name || '搜索', direction: 'top' } }))
    }

    ovsRef.current = ovs
    return () => { cancelled = true; map.off('click', clickH); clearOvs() }
  }, [amapOk, visMarkers, selId, editorOpen, draft, activePoi, fenceDrawing])

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
    if (!editorOpen || !selMarker) { message.info('请先从设施列表选择一个设施并进入位置编辑'); return }
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
    setDraft((p) => ({ ...p, longitude: ln, latitude: la, imageX: '', imageY: '' }))
    if (mapRef.current && ln && la) mapRef.current.setZoomAndCenter(17, [Number(ln), Number(la)])
  }

  /* ---- 编辑器 ---- */
  const closeEditor = () => {
    setFenceDrawing(false)
    setEditorOpen(false)
  }
  const openEdit = (m) => {
    const marker = m || selMarker
    if (!marker) { message.warning('请先选中标点'); return }
    setSelId(marker.id)
    setSearchHits([]); setActivePoi(null)
    setFenceDrawing(false)
    setDraft({
      facilityName: marker.markerName || '',
      facilityType: marker.facilityType || 1,
      location: marker.location || '',
      description: marker.description || '',
      status: marker.status || 1,
      longitude: marker.longitude ? String(marker.longitude) : '',
      latitude: marker.latitude ? String(marker.latitude) : '',
      imageX: marker.imageX ? String(marker.imageX) : '',
      imageY: marker.imageY ? String(marker.imageY) : '',
      geometryType: marker.geometryType === 'AREA' ? 'AREA' : 'POINT',
      boundaryPoints: parseBoundaryPoints(marker.boundaryPoints),
    })
    setEditorOpen(true)
  }

  useEffect(() => {
    const requestedFacilityId = requestedFacilityIdRef.current
    if (!requestedFacilityId || !markers.length) return
    const marker = markers.find((item) => String(item.facilityId) === String(requestedFacilityId))
    requestedFacilityIdRef.current = null
    if (marker) {
      openEdit(marker)
    } else {
      message.warning('未找到该设施的地图记录')
    }
  }, [markers])

  const finishFenceDrawing = () => {
    const boundaryPoints = parseBoundaryPoints(draft.boundaryPoints)
    if (boundaryPoints.length < 3) {
      message.warning('区域围栏至少需要3个边界点')
      return
    }
    if (hasSelfIntersection(boundaryPoints)) {
      message.warning('围栏边线不能交叉，请撤销或调整边界点')
      return
    }
    setFenceDrawing(false)
    message.success('围栏已闭合，可拖动顶点继续微调')
  }

  const saveDraft = async () => {
    const boundaryPoints = parseBoundaryPoints(draft.boundaryPoints)
    const boundaryCenter = getBoundaryCenter(boundaryPoints)
    const [l, a] = draft.geometryType === 'AREA' && boundaryCenter
      ? boundaryCenter
      : [toNum(draft.longitude), toNum(draft.latitude)]
    if (draft.geometryType === 'AREA' && boundaryPoints.length < 3) {
      message.warning('区域围栏至少需要3个边界点')
      return
    }
    if (draft.geometryType === 'AREA' && fenceDrawing) {
      message.warning('请先完成围栏绘制')
      return
    }
    if (draft.geometryType === 'AREA' && hasSelfIntersection(boundaryPoints)) {
      message.warning('围栏边线不能交叉，请调整边界点')
      return
    }
    if (draft.geometryType === 'POINT' && (l == null || a == null)) {
      message.warning('请填写经纬度')
      return
    }
    setSaving(true)
    try {
      const payload = {
        longitude: l ?? selMarker?.longitude,
        latitude: a ?? selMarker?.latitude,
        imageX: null,
        imageY: null,
        geometryType: draft.geometryType,
        boundaryPoints: draft.geometryType === 'AREA' ? JSON.stringify(boundaryPoints) : '[]',
      }
      if (!selMarker?.facilityId) throw new Error('设施数据不存在，无法保存位置')
      await apiUpdateFacility(selMarker.facilityId, payload)
      await refreshMarkers()
      closeEditor()
      message.success('位置已更新')
    } catch (e) { message.error(e?.message || '保存失败') } finally { setSaving(false) }
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
    const [l, a] = [toNum(r.longitude), toNum(r.latitude)]
    if (mapRef.current && l != null && a != null) mapRef.current.setZoomAndCenter(Math.max(mapRef.current.getZoom() || 16, 17), [l, a])
  }

  const toggleType = (t) => setHiddenTypes((p) => { const n = new Set(p); n.has(t) ? n.delete(t) : n.add(t); return n })

  const fitVisibleMarkers = () => {
    if (!mapRef.current) return
    try {
      const markerOverlays = ovsRef.current.filter((overlay) => (
        typeof overlay?.getPosition === 'function' || typeof overlay?.getPath === 'function'
      ))
      if (markerOverlays.length && typeof mapRef.current.setFitView === 'function') {
        mapRef.current.setFitView(markerOverlays, false, [64, 64, 64, 64], 18)
      } else {
        mapRef.current.setZoomAndCenter(DEFAULT_ZOOM, [DEFAULT_CENTER.lng, DEFAULT_CENTER.lat])
      }
    } catch {
      mapRef.current.setZoomAndCenter(DEFAULT_ZOOM, [DEFAULT_CENTER.lng, DEFAULT_CENTER.lat])
    }
  }

  /* ---- 右侧设施列表 ---- */
  const cols = [
    {
      title: '标点',
      key: 'marker',
      render: (_, r, index) => {
        const meta = tmeta(r.facilityType)
        const statusMeta = STATUS_MAP[r.status] || { label: r.status, color: 'default' }
        const active = r.id === selId
        const pendingLocation = isMarkerUnlocated(r)
        const sequence = String((pgn.cur - 1) * pgn.size + index + 1).padStart(2, '0')
        return (
          <div className={`marker-list-item${active ? ' marker-list-item--active' : ''}`}>
            <div className="marker-list-item__summary">
              <span className="marker-list-item__index">#{sequence}</span>
              <div className="marker-list-item__main">
                <strong title={r.markerName || ''}>{r.markerName || `标点 #${sequence}`}</strong>
                <div className="marker-list-item__tags">
                  <Tag style={{ '--marker-tag-color': meta.color }}>{typeLabel(r.facilityType, r.facilityTypeName)}</Tag>
                  {pendingLocation ? <Tag color="orange">待定位</Tag> : null}
                  {r.geometryType === 'AREA' ? <Tag icon={<BorderOutlined />}>区域围栏</Tag> : null}
                  {r.status != null ? <Tag color={statusMeta.color}>{statusMeta.label}</Tag> : null}
                </div>
              </div>
              <AimOutlined className="marker-list-item__locate" />
            </div>
            {active ? (
              <div className="marker-list-item__detail">
                <span><EnvironmentOutlined />{r.location || '未设置位置'}</span>
                {r.geometryType === 'AREA' ? <span><BorderOutlined />围栏边界 {parseBoundaryPoints(r.boundaryPoints).length} 个点</span> : null}
                <code>{r.longitude || '—'}, {r.latitude || '—'}</code>
                <div className="marker-list-item__actions">
                  <Button type="text" size="small" disabled={pendingLocation} icon={<CopyOutlined />} onClick={(event) => { event.stopPropagation(); copyCoord(r.longitude, r.latitude) }}>复制坐标</Button>
                  <Button type="text" size="small" icon={<EnvironmentOutlined />} onClick={(event) => { event.stopPropagation(); openEdit(r) }}>编辑位置</Button>
                </div>
              </div>
            ) : null}
          </div>
        )
      },
    },
  ]

  /* ============================================================
     RENDER
     ============================================================ */
  return (
    <div className="marker-page">
      <section className="marker-toolbar">
        <div className="marker-toolbar__copy">
          <h2>标点管理</h2>
          <p>编辑既有设施的点位与区域围栏</p>
        </div>
      </section>

      <section className="marker-summary" aria-label="设施位置统计">
        <div className="marker-summary__primary"><span>设施总数</span><strong>{markers.length}</strong></div>
        <div className="marker-summary__primary">
          <span>{unlocated.total > 0 ? '待定位' : '已定位'}</span>
          <strong>{unlocated.total > 0 ? unlocated.total : markers.length}</strong>
          {unlocated.total > 0 ? <WarningOutlined /> : <CheckCircleOutlined />}
        </div>
        <div className="marker-summary__types">
          {typeOptions.map(({ value }) => {
            const meta = tmeta(value)
            return (
              <button key={value} type="button" className={hiddenTypes.has(value) ? 'is-hidden' : ''} onClick={() => toggleType(value)}>
                <span style={{ background: meta.color }} />
                {typeLabel(value)}
                <strong>{typeCounts[value] || 0}</strong>
              </button>
            )
          })}
        </div>
      </section>

      {unlocated.total > 0 ? (
        <section className="marker-unmarked-alert">
          <WarningOutlined />
          <span>还有 <strong>{unlocated.total}</strong> 个设施未设置地图位置，请从右侧列表选择后编辑</span>
          <div>{Object.entries(unlocated.byType).map(([t, count]) => {
            const meta = tmeta(Number(t))
            return <Tag key={t} style={{ '--marker-tag-color': meta.color }}>{meta.label} × {count}</Tag>
          })}</div>
        </section>
      ) : null}

      <section className="marker-workspace">
        <div className="marker-map-pane">
          <div className={`marker-map-shell${editorOpen ? ' is-editing' : ''}`}>
            <div ref={containerRef} className="marker-map-canvas" />
            {amapErr ? <div className="marker-map-error">{amapErr}</div> : null}
            {!amapOk && !amapErr ? <div className="marker-map-loading"><Skeleton active paragraph={{ rows: 1 }} title={false} /></div> : null}
            {editorOpen ? (
              <div className="marker-map-edit-status">
                <EnvironmentOutlined />
                <span>
                  正在编辑 <strong>{draft.facilityName || '当前设施'}</strong>
                  · {draft.geometryType === 'AREA' ? '在地图上依次点击围栏顶点' : '点击地图选择位置'}
                </span>
              </div>
            ) : null}

            {typeOptions.length > 0 ? (
              <div className="marker-legend">
                <button type="button" className={hiddenTypes.size === 0 ? 'is-active' : ''} onClick={() => setHiddenTypes(new Set())}>
                  <span className="marker-legend__all"><PushpinOutlined /></span>全部
                </button>
                {typeOptions.map(({ value }) => {
                  const meta = tmeta(value)
                  const hidden = hiddenTypes.has(value)
                  return (
                    <button key={value} type="button" className={hidden ? 'is-hidden' : ''} onClick={() => toggleType(value)}>
                      <span className="marker-legend__dot" style={{ background: meta.color }} />
                      {typeLabel(value)}
                    </button>
                  )
                })}
              </div>
            ) : null}

            <div className="marker-map-controls">
              <Button icon={<AimOutlined />} onClick={fitVisibleMarkers} title="适配全部标点" />
              <Button className={satellite ? 'is-active' : ''} icon={<GlobalOutlined />} onClick={toggleSatellite} title={satellite ? '切换普通地图' : '切换卫星地图'} />
            </div>

            {searchHits.length > 0 ? (
              <div className="marker-search-results">
                <div className="marker-search-results__head">高德地点搜索结果 <button type="button" onClick={() => setSearchHits([])}>收起</button></div>
                {searchHits.map((point) => (
                  <button key={point.id} type="button" className={`marker-search-result${activePoi?.id === point.id ? ' active' : ''}`} onClick={() => pickPoi(point)}>
                    <strong>{point.name}</strong><span>{point.address}</span><em>{point.longitude}, {point.latitude}</em>
                  </button>
                ))}
              </div>
            ) : null}
          </div>
        </div>

        <aside className="marker-list-panel">
          <header className="marker-list-panel__head">
            <div><h3>设施列表</h3><span>{filtered.length} / {markers.length}</span></div>
          </header>
          <div className="marker-list-panel__filters">
            <Input allowClear placeholder="搜索设施名称" prefix={<SearchOutlined />} value={tableKw} onChange={(e) => { setTableKw(e.target.value); setPgn((prev) => ({ ...prev, cur: 1 })) }} />
            <Select allowClear placeholder="全部类型" value={typeFilter} options={typeOptions} onChange={(value) => { setTypeFilter(value); setPgn((prev) => ({ ...prev, cur: 1 })) }} />
          </div>
          <div className="marker-list-panel__table">
            <Table
              columns={cols}
              dataSource={filtered}
              loading={loading}
              rowKey="id"
              size="small"
              showHeader={false}
              onRow={(record) => ({ onClick: () => doLocate(record), className: record.id === selId ? 'active-row' : '' })}
              locale={{ emptyText: '暂无标点' }}
              pagination={{
                current: pgn.cur,
                pageSize: pgn.size,
                total: filtered.length,
                showSizeChanger: false,
                hideOnSinglePage: true,
                size: 'small',
                onChange: (page) => setPgn((prev) => ({ ...prev, cur: page })),
              }}
            />
          </div>
          <footer className="marker-list-panel__footer">
            <span><EnvironmentOutlined />设施在对应业务页面创建，此处仅维护地图位置</span>
          </footer>
        </aside>
      </section>

      {/* ---- Drawer 编辑器 ---- */}
      <Drawer
        title="编辑设施位置"
        placement="right" width={430}
        open={editorOpen} onClose={closeEditor}
        mask={false}
        push={false}
        rootStyle={{ pointerEvents: 'none' }}
        styles={{ wrapper: { pointerEvents: 'auto' } }}
        rootClassName="marker-editor-drawer"
        footer={<div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
          <Button onClick={closeEditor} style={{ borderRadius: 12 }}>取消</Button>
          <Button type="primary" loading={saving} onClick={saveDraft} style={{ borderRadius: 12, fontWeight: 600 }}>保存位置</Button>
        </div>}
      >
        <div className="marker-editor-hint marker-editor-hint--interactive">
          <EnvironmentOutlined />
          <div>
            <strong>{draft.geometryType === 'AREA' ? '保持面板打开，在左侧地图绘制围栏' : '保持面板打开，在左侧地图点击落点'}</strong>
            <span>地图仍可拖动和缩放；名称、类型等业务信息请在对应设施页面维护。</span>
          </div>
        </div>
        <div className="marker-editor-facility">
          <span className="marker-editor-facility__icon" style={{ background: tmeta(draft.facilityType).color }}>
            {tmeta(draft.facilityType).short}
          </span>
          <div>
            <strong>{draft.facilityName || '未命名设施'}</strong>
            <span>{typeLabel(draft.facilityType)} · {draft.location || '暂无位置说明'}</span>
          </div>
        </div>
        <Form layout="vertical" size="middle">
          <>
              <Form.Item label="地点搜索">
                <Input.Search
                  allowClear
                  placeholder="搜索高德地点辅助定位"
                  value={searchKw}
                  onChange={(e) => setSearchKw(e.target.value)}
                  onSearch={doSearch}
                  loading={searchBusy}
                />
              </Form.Item>
              <Form.Item label="位置形态" required>
                <Segmented
                  block
                  className="marker-geometry-switch"
                  value={draft.geometryType}
                  options={[
                    { label: '单点位置', value: 'POINT', icon: <EnvironmentOutlined /> },
                    { label: '区域围栏', value: 'AREA', icon: <BorderOutlined /> },
                  ]}
                  onChange={(geometryType) => {
                    setFenceDrawing(geometryType === 'AREA')
                    setDraft((prev) => ({
                      ...prev,
                      geometryType,
                      boundaryPoints: geometryType === 'POINT' ? [] : prev.boundaryPoints,
                      longitude: geometryType === 'AREA' && prev.boundaryPoints.length === 0 ? '' : prev.longitude,
                      latitude: geometryType === 'AREA' && prev.boundaryPoints.length === 0 ? '' : prev.latitude,
                    }))
                  }}
                />
              </Form.Item>
              {draft.geometryType === 'AREA' ? (
                <div className="marker-fence-editor">
                  <div className="marker-fence-editor__head">
                    <div>
                      <strong>绘制围栏边界</strong>
                      <span>
                        {fenceDrawing
                          ? '在地图上依次点击边界顶点，完成后进入微调'
                          : '拖动实心顶点调整，点击空心中点新增，右键顶点删除'}
                      </span>
                    </div>
                    <em className={!fenceDrawing && draft.boundaryPoints.length >= 3 ? 'is-ready' : ''}>
                      {fenceDrawing ? '绘制中' : draft.boundaryPoints.length >= 3 ? '已完成' : '未绘制'}
                    </em>
                  </div>
                  <div className="marker-fence-editor__count">
                    <BorderOutlined />
                    已标记 <strong>{draft.boundaryPoints.length}</strong> 个边界点
                  </div>
                  <div className="marker-fence-editor__actions">
                    {fenceDrawing ? (
                      <>
                        <Button type="primary" disabled={draft.boundaryPoints.length < 3} onClick={finishFenceDrawing}>完成绘制</Button>
                        <Button
                          icon={<UndoOutlined />}
                          disabled={!draft.boundaryPoints.length}
                          onClick={() => setDraft((prev) => {
                            const boundaryPoints = prev.boundaryPoints.slice(0, -1)
                            const center = getBoundaryCenter(boundaryPoints)
                            return {
                              ...prev,
                              boundaryPoints,
                              longitude: center ? roundCoord(center[0]) : '',
                              latitude: center ? roundCoord(center[1]) : '',
                            }
                          })}
                        >
                          撤销
                        </Button>
                      </>
                    ) : (
                      <Button type="primary" onClick={() => setFenceDrawing(true)}>
                        {draft.boundaryPoints.length ? '继续添加边界点' : '开始绘制'}
                      </Button>
                    )}
                    <Button
                      disabled={!draft.boundaryPoints.length}
                      onClick={() => {
                        setFenceDrawing(true)
                        setDraft((prev) => ({ ...prev, boundaryPoints: [], longitude: '', latitude: '' }))
                      }}
                    >
                      重新绘制
                    </Button>
                  </div>
                  <div className="marker-fence-editor__center">
                    <span>区域中心点</span>
                    <code>{draft.longitude && draft.latitude ? `${draft.longitude}, ${draft.latitude}` : '绘制后自动计算'}</code>
                  </div>
                </div>
              ) : (
                <>
                  <div className="marker-coord-grid">
                    <div><label>经度</label><Input value={draft.longitude} onChange={(e) => setDraft((p) => ({ ...p, longitude: e.target.value }))} placeholder="点击地图取点" style={{ borderRadius: 12 }} /></div>
                    <div><label>纬度</label><Input value={draft.latitude} onChange={(e) => setDraft((p) => ({ ...p, latitude: e.target.value }))} placeholder="点击地图取点" style={{ borderRadius: 12 }} /></div>
                  </div>
                  <div className="marker-editor-hint marker-editor-hint--compact">在地图上点击落点，也可以直接拖动蓝色标记微调位置。</div>
                </>
              )}
          </>
        </Form>
      </Drawer>
    </div>
  )
}
