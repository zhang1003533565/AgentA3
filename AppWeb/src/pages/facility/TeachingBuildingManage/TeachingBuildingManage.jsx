import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Button,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Spin,
  Table,
  Tag,
  Upload,
  message,
} from 'antd'
import {
  AimOutlined,
  ApartmentOutlined,
  BankOutlined,
  CameraOutlined,
  CheckCircleFilled,
  DeleteOutlined,
  EditOutlined,
  EllipsisOutlined,
  EnvironmentOutlined,
  FileImageOutlined,
  LeftOutlined,
  PlusOutlined,
  SearchOutlined,
  SaveOutlined,
  UnorderedListOutlined,
  UndoOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router-dom'
import {
  addMapPlaceImage,
  createMapPlace,
  deleteMapPlace,
  deleteMapPlaceImage,
  deleteIndoorPosition,
  getFloorPlan,
  getMapPlaceDetail,
  getMapPlaceTree,
  saveFloorPlan,
  saveIndoorPosition,
  updateMapPlace,
  updateMapPlaceImage,
} from '../../../api/mapPlace'
import { MAP_BUILDING_UPLOAD_FOLDER, uploadImage } from '../../../api/upload'
import SidePanel from '../../../components/SidePanel/SidePanel'
import './TeachingBuildingManage.css'

const ROOM_TYPES = new Set(['CLASSROOM', 'LABORATORY', 'OFFICE'])

const ROOM_TYPE_OPTIONS = [
  { value: 'CLASSROOM', label: '教室' },
  { value: 'LABORATORY', label: '实验室' },
  { value: 'OFFICE', label: '办公室' },
]

const ROOM_TYPE_LABELS = Object.fromEntries(ROOM_TYPE_OPTIONS.map((item) => [item.value, item.label]))

const USAGE_STATUS_OPTIONS = [
  { value: 'AVAILABLE', label: '空闲' },
  { value: 'IN_CLASS', label: '上课中' },
  { value: 'IN_USE', label: '使用中' },
]

const USAGE_STATUS_META = {
  AVAILABLE: { label: '空闲', tone: 'available' },
  IN_CLASS: { label: '上课中', tone: 'busy' },
  IN_USE: { label: '使用中', tone: 'busy' },
}

const normalizeImages = (images = []) => images.map((item) => ({
  uid: String(item.id),
  name: item.imageUrl?.split('/').pop() || `图片${item.id}`,
  status: 'done',
  url: item.imageUrl,
  imageId: item.id,
  focusX: item.focusX ?? 50,
  focusY: item.focusY ?? 50,
}))

const getCoverImage = (record) => record?.images?.[0] || null

const getRooms = (floor) => (floor?.children || []).filter((item) => ROOM_TYPES.has(item.placeType))

const getFloors = (building) => (building?.children || []).filter((item) => item.placeType === 'FLOOR')

const getRoomUsage = (room) => room?.usageStatus || 'AVAILABLE'

const getRoomCount = (building) => getFloors(building)
  .reduce((total, floor) => total + getRooms(floor).length, 0)

const normalizePosition = (position) => position ? {
  id: position.id,
  floorPlanId: position.floorPlanId,
  xRatio: Number(position.xRatio),
  yRatio: Number(position.yRatio),
} : null

const positionsMatch = (left, right) => {
  if (!left && !right) return true
  if (!left || !right) return false
  return Number(left.xRatio) === Number(right.xRatio)
    && Number(left.yRatio) === Number(right.yRatio)
}

function UsageTag({ status }) {
  const meta = USAGE_STATUS_META[status] || USAGE_STATUS_META.AVAILABLE
  return (
    <Tag className={`teaching-usage-tag ${meta.tone}`}>
      <span className={`teaching-status-dot ${meta.tone}`} />
      {meta.label}
    </Tag>
  )
}

function CoverImage({ record, icon, alt }) {
  const image = getCoverImage(record)
  if (!image?.imageUrl) {
    return <div className="teaching-cover-placeholder">{icon}</div>
  }
  return (
    <img
      src={image.imageUrl}
      alt={alt}
      style={{ objectPosition: `${image.focusX ?? 50}% ${image.focusY ?? 50}%` }}
    />
  )
}

export default function TeachingBuildingManage() {
  const navigate = useNavigate()
  const { buildingId, floorId } = useParams()
  const [form] = Form.useForm()
  const [batchForm] = Form.useForm()
  const [tree, setTree] = useState([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [buildingKeyword, setBuildingKeyword] = useState('')
  const [campusFilter, setCampusFilter] = useState('ALL')
  const [floorKeyword, setFloorKeyword] = useState('')
  const [roomKeyword, setRoomKeyword] = useState('')
  const [usageFilter, setUsageFilter] = useState('ALL')
  const [viewMode, setViewMode] = useState('PLAN')
  const [selectedRoomId, setSelectedRoomId] = useState(null)
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [placementMode, setPlacementMode] = useState(false)
  const [draftPositions, setDraftPositions] = useState({})
  const [initialPositions, setInitialPositions] = useState({})
  const [placementHistory, setPlacementHistory] = useState([])
  const [placementSaving, setPlacementSaving] = useState(false)
  const [mapZoom, setMapZoom] = useState(1)
  const [planImageSize, setPlanImageSize] = useState(null)
  const [planFitSize, setPlanFitSize] = useState(null)
  const planViewportRef = useRef(null)
  const planZoomStageRef = useRef(null)
  const [roomDetailOpen, setRoomDetailOpen] = useState(false)
  const [batchEditorOpen, setBatchEditorOpen] = useState(false)
  const [batchSaving, setBatchSaving] = useState(false)
  const [floorPlan, setFloorPlan] = useState(null)
  const [roomDetails, setRoomDetails] = useState({})
  const [detailLoading, setDetailLoading] = useState(false)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editorKind, setEditorKind] = useState('building')
  const [editorRecord, setEditorRecord] = useState(null)
  const [editorParent, setEditorParent] = useState(null)
  const [editorOriginalImages, setEditorOriginalImages] = useState([])
  const [editorImages, setEditorImages] = useState([])
  const [editorPlanUrl, setEditorPlanUrl] = useState('')

  const loadTree = useCallback(async () => {
    setLoading(true)
    try {
      const response = await getMapPlaceTree('TEACHING')
      setTree(Array.isArray(response.data) ? response.data : [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadTree()
  }, [loadTree])

  const currentBuilding = useMemo(
    () => tree.find((item) => String(item.id) === String(buildingId)) || null,
    [buildingId, tree],
  )

  const currentFloor = useMemo(
    () => getFloors(currentBuilding).find((item) => String(item.id) === String(floorId)) || null,
    [currentBuilding, floorId],
  )

  const floors = useMemo(() => getFloors(currentBuilding), [currentBuilding])
  const rooms = useMemo(() => getRooms(currentFloor), [currentFloor])

  useEffect(() => {
    if (!currentFloor) {
      setFloorPlan(null)
      setRoomDetails({})
      setSelectedRoomId(null)
      setSelectedRowKeys([])
      setPlacementMode(false)
      setDraftPositions({})
      setInitialPositions({})
      setPlacementHistory([])
      return undefined
    }

    let active = true
    setDetailLoading(true)
    Promise.all([
      getFloorPlan(currentFloor.id),
      Promise.all(rooms.map((room) => getMapPlaceDetail(room.id))),
    ]).then(([planResponse, detailResponses]) => {
      if (!active) return
      const details = Object.fromEntries(
        detailResponses.map((response) => [String(response.data.id), response.data]),
      )
      const positions = Object.fromEntries(
        detailResponses.map((response) => [
          String(response.data.id),
          normalizePosition(response.data.indoorPosition),
        ]),
      )
      setFloorPlan(planResponse.data || null)
      setRoomDetails(details)
      setDraftPositions(positions)
      setInitialPositions(positions)
      setPlacementMode(false)
      setPlacementHistory([])
      setSelectedRowKeys([])
      setSelectedRoomId((previous) => {
        if (previous && details[String(previous)]) return previous
        return rooms[0]?.id || null
      })
    }).catch(() => {
      if (!active) return
      setFloorPlan(null)
      setRoomDetails({})
      setDraftPositions({})
      setInitialPositions({})
      setPlacementMode(false)
      setPlacementHistory([])
      setSelectedRowKeys([])
      setSelectedRoomId(rooms[0]?.id || null)
    }).finally(() => {
      if (active) setDetailLoading(false)
    })

    return () => {
      active = false
    }
  }, [currentFloor, rooms])

  const campusOptions = useMemo(() => {
    const locations = [...new Set(tree.map((item) => item.locationDesc).filter(Boolean))]
    return [
      { value: 'ALL', label: '全部校区' },
      ...locations.map((location) => ({ value: location, label: location })),
    ]
  }, [tree])

  const filteredBuildings = useMemo(() => {
    const query = buildingKeyword.trim().toLowerCase()
    return tree.filter((building) => {
      const matchesCampus = campusFilter === 'ALL' || building.locationDesc === campusFilter
      const matchesKeyword = !query || building.name.toLowerCase().includes(query)
      return matchesCampus && matchesKeyword
    })
  }, [buildingKeyword, campusFilter, tree])

  const filteredFloors = useMemo(() => {
    const query = floorKeyword.trim().toLowerCase()
    return floors.filter((floor) => !query || floor.name.toLowerCase().includes(query))
  }, [floorKeyword, floors])

  const detailedRooms = useMemo(
    () => rooms.map((room) => roomDetails[String(room.id)] || room),
    [roomDetails, rooms],
  )

  const filteredRooms = useMemo(() => {
    const query = roomKeyword.trim().toLowerCase()
    return detailedRooms.filter((room) => {
      const usage = getRoomUsage(room)
      const matchesUsage = usageFilter === 'ALL'
        || (usageFilter === 'AVAILABLE' && usage === 'AVAILABLE')
        || (usageFilter === 'IN_USE' && usage !== 'AVAILABLE')
      const matchesKeyword = !query
        || room.name.toLowerCase().includes(query)
        || (room.usagePurpose || '').toLowerCase().includes(query)
      return matchesUsage && matchesKeyword
    })
  }, [detailedRooms, roomKeyword, usageFilter])

  const selectedRoom = selectedRoomId
    ? roomDetails[String(selectedRoomId)] || rooms.find((room) => String(room.id) === String(selectedRoomId))
    : null

  const positionedRoomCount = detailedRooms.filter((room) => {
    const position = placementMode ? draftPositions[String(room.id)] : room.indoorPosition
    return Boolean(position)
  }).length
  const unpositionedRoomCount = Math.max(0, detailedRooms.length - positionedRoomCount)

  const placementDirty = detailedRooms.some((room) => (
    !positionsMatch(
      draftPositions[String(room.id)],
      initialPositions[String(room.id)],
    )
  ))

  const updatePlanFitSize = useCallback((imageSize = planImageSize) => {
    const viewport = planViewportRef.current
    if (!viewport || !imageSize?.width || !imageSize?.height) return

    const scale = Math.min(
      viewport.clientWidth / imageSize.width,
      viewport.clientHeight / imageSize.height,
    )

    if (!Number.isFinite(scale) || scale <= 0) return
    setPlanFitSize({
      width: Math.max(1, Math.round(imageSize.width * scale)),
      height: Math.max(1, Math.round(imageSize.height * scale)),
    })
  }, [planImageSize])

  useEffect(() => {
    const viewport = planViewportRef.current
    if (!viewport || !planImageSize || typeof ResizeObserver === 'undefined') return undefined

    const observer = new ResizeObserver(() => updatePlanFitSize())
    observer.observe(viewport)
    return () => observer.disconnect()
  }, [planImageSize, updatePlanFitSize])

  const selectRoom = (roomId) => {
    setSelectedRoomId(roomId)
  }

  const focusRoomOnPlan = (room) => {
    selectRoom(room.id)

    const position = placementMode
      ? draftPositions[String(room.id)]
      : normalizePosition(room.indoorPosition)
    const viewport = planViewportRef.current
    const stage = planZoomStageRef.current
    if (!position || !viewport || !stage) return

    const left = stage.offsetLeft + (stage.offsetWidth * Number(position.xRatio)) / 100 - viewport.clientWidth / 2
    const top = stage.offsetTop + (stage.offsetHeight * Number(position.yRatio)) / 100 - viewport.clientHeight / 2
    viewport.scrollTo({
      left: Math.max(0, left),
      top: Math.max(0, top),
      behavior: 'smooth',
    })
  }

  const enterPlacementMode = () => {
    if (!floorPlan?.imageUrl) {
      message.warning('请先在“编辑楼层”中上传楼层平面图')
      return
    }
    if (!detailedRooms.length) {
      message.warning('请先新增房间，再进行点位布置')
      return
    }
    const positions = Object.fromEntries(
      detailedRooms.map((room) => [String(room.id), normalizePosition(room.indoorPosition)]),
    )
    setInitialPositions(positions)
    setDraftPositions(positions)
    setPlacementHistory([])
    setSelectedRoomId((previous) => previous || detailedRooms[0]?.id || null)
    setPlacementMode(true)
  }

  const leavePlacementMode = () => {
    if (!placementDirty) {
      setPlacementMode(false)
      return
    }
    Modal.confirm({
      title: '退出点位布置？',
      content: '尚未保存的点位调整将被丢弃。',
      okText: '退出',
      cancelText: '继续布置',
      onOk: () => {
        setDraftPositions(initialPositions)
        setPlacementHistory([])
        setPlacementMode(false)
      },
    })
  }

  const updateDraftPosition = (roomId, nextPosition) => {
    const key = String(roomId)
    setPlacementHistory((previous) => [
      ...previous,
      { roomId: key, position: draftPositions[key] || null },
    ])
    setDraftPositions((previous) => ({ ...previous, [key]: nextPosition }))
  }

  const placeSelectedRoom = (event) => {
    if (!placementMode || !selectedRoomId) return
    const rect = event.currentTarget.getBoundingClientRect()
    updateDraftPosition(String(selectedRoomId), {
      ...(draftPositions[String(selectedRoomId)] || {}),
      floorPlanId: floorPlan.id,
      xRatio: Number((((event.clientX - rect.left) / rect.width) * 100).toFixed(4)),
      yRatio: Number((((event.clientY - rect.top) / rect.height) * 100).toFixed(4)),
    })
  }

  const undoPlacement = () => {
    const previousAction = placementHistory[placementHistory.length - 1]
    if (!previousAction) return
    setDraftPositions((previous) => ({
      ...previous,
      [previousAction.roomId]: previousAction.position,
    }))
    setPlacementHistory((previous) => previous.slice(0, -1))
  }

  const savePlacements = async () => {
    const changedRooms = detailedRooms.filter((room) => !positionsMatch(
      draftPositions[String(room.id)],
      initialPositions[String(room.id)],
    ))
    if (!changedRooms.length) {
      message.info('点位没有发生变化')
      return
    }
    setPlacementSaving(true)
    try {
      for (const room of changedRooms) {
        const key = String(room.id)
        const nextPosition = draftPositions[key]
        const previousPosition = initialPositions[key]
        if (nextPosition) {
          await saveIndoorPosition(room.id, {
            floorPlanId: floorPlan.id,
            xRatio: nextPosition.xRatio,
            yRatio: nextPosition.yRatio,
          })
        } else if (previousPosition?.id) {
          await deleteIndoorPosition(previousPosition.id)
        }
      }
      message.success('房间点位已保存')
      setPlacementMode(false)
      setPlacementHistory([])
      await loadTree()
    } finally {
      setPlacementSaving(false)
    }
  }

  const openBatchEditor = () => {
    batchForm.resetFields()
    setBatchEditorOpen(true)
  }

  const saveBatchEditor = async () => {
    const values = await batchForm.validateFields()
    if (!values.usagePurpose && !values.usageStatus) {
      message.warning('请至少选择一项需要批量修改的内容')
      return
    }
    const selectedRooms = detailedRooms.filter((room) => selectedRowKeys.includes(room.id))
    setBatchSaving(true)
    try {
      await Promise.all(selectedRooms.map((room) => updateMapPlace(room.id, {
        parentId: room.parentId,
        sceneType: room.sceneType,
        placeType: room.placeType,
        name: room.name,
        description: room.description || '',
        usagePurpose: values.usagePurpose || room.usagePurpose || '',
        usageStatus: values.usageStatus || getRoomUsage(room),
        status: room.status || 'ENABLED',
        longitude: room.longitude ?? null,
        latitude: room.latitude ?? null,
        locationDesc: room.locationDesc || '',
        mapVisible: room.mapVisible ?? false,
        sortOrder: room.sortOrder ?? 0,
      })))
      message.success(`已更新 ${selectedRooms.length} 个房间`)
      setBatchEditorOpen(false)
      setSelectedRowKeys([])
      await loadTree()
    } finally {
      setBatchSaving(false)
    }
  }

  const batchDeleteRooms = () => {
    const selectedRooms = detailedRooms.filter((room) => selectedRowKeys.includes(room.id))
    Modal.confirm({
      title: `确定删除选中的 ${selectedRooms.length} 个房间吗？`,
      content: '删除后无法恢复，相关室内点位也将一并移除。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        for (const room of selectedRooms) await deleteMapPlace(room.id)
        message.success('所选房间已删除')
        setSelectedRowKeys([])
        await loadTree()
      },
    })
  }

  const uploadEditorImage = async (file) => {
    if (!file.type?.startsWith('image/')) {
      message.warning('请选择图片文件')
      return false
    }
    setUploading(true)
    try {
      const url = await uploadImage(file, MAP_BUILDING_UPLOAD_FOLDER)
      setEditorImages((previous) => [
        ...previous,
        {
          uid: `new-${Date.now()}`,
          name: file.name,
          status: 'done',
          url,
          focusX: 50,
          focusY: 50,
        },
      ])
      message.success('图片上传成功')
    } catch (error) {
      message.error(error?.message || '图片上传失败')
    } finally {
      setUploading(false)
    }
    return false
  }

  const uploadFloorPlan = async (file) => {
    if (!file.type?.startsWith('image/')) {
      message.warning('请选择图片文件')
      return false
    }
    setUploading(true)
    try {
      const url = await uploadImage(file, MAP_BUILDING_UPLOAD_FOLDER)
      setEditorPlanUrl(url)
      message.success('平面图上传成功')
    } catch (error) {
      message.error(error?.message || '平面图上传失败')
    } finally {
      setUploading(false)
    }
    return false
  }

  const openEditor = async (kind, record = null, parent = null) => {
    setSaving(true)
    try {
      const detail = record ? (await getMapPlaceDetail(record.id)).data : null
      const actualParent = parent
        || (kind === 'floor' ? currentBuilding : kind === 'room' ? currentFloor : null)
      if (kind === 'floor' && record) {
        const plan = (await getFloorPlan(record.id)).data || null
        setEditorPlanUrl(plan?.imageUrl || '')
      } else {
        setEditorPlanUrl('')
      }

      form.resetFields()
      form.setFieldsValue({
        name: detail?.name || '',
        locationDesc: detail?.locationDesc || '',
        description: detail?.description || '',
        placeType: detail?.placeType || 'CLASSROOM',
        usagePurpose: detail?.usagePurpose || '',
        usageStatus: detail?.usageStatus || 'AVAILABLE',
      })
      setEditorKind(kind)
      setEditorRecord(detail)
      setEditorParent(actualParent)
      setEditorOriginalImages(detail?.images || [])
      setEditorImages(normalizeImages(detail?.images || []))
      setEditorOpen(true)
    } finally {
      setSaving(false)
    }
  }

  const syncImages = async (placeId) => {
    const retainedImageIds = new Set(editorImages.map((item) => item.imageId).filter(Boolean))
    await Promise.all(
      editorOriginalImages
        .filter((item) => !retainedImageIds.has(item.id))
        .map((item) => deleteMapPlaceImage(item.id)),
    )
    await Promise.all(editorImages.map((item, index) => {
      const payload = {
        imageUrl: item.url,
        sortOrder: index,
        focusX: item.focusX ?? 50,
        focusY: item.focusY ?? 50,
      }
      return item.imageId
        ? updateMapPlaceImage(item.imageId, payload)
        : addMapPlaceImage(placeId, payload)
    }))
  }

  const saveEditor = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const placeType = editorKind === 'building'
        ? 'TEACHING_BUILDING'
        : editorKind === 'floor'
          ? 'FLOOR'
          : values.placeType
      const payload = {
        parentId: editorRecord?.parentId ?? editorParent?.id ?? null,
        sceneType: 'TEACHING',
        placeType,
        name: values.name,
        description: values.description || '',
        usagePurpose: editorKind === 'room' ? values.usagePurpose || '' : '',
        usageStatus: editorKind === 'room' ? values.usageStatus || 'AVAILABLE' : null,
        status: editorRecord?.status || 'ENABLED',
        locationDesc: editorKind === 'building' ? values.locationDesc || '' : '',
        longitude: editorRecord?.longitude ?? null,
        latitude: editorRecord?.latitude ?? null,
        mapVisible: editorKind === 'building',
        sortOrder: editorRecord?.sortOrder ?? ((editorParent?.children?.length || tree.length) + 1) * 10,
      }
      const response = editorRecord
        ? await updateMapPlace(editorRecord.id, payload)
        : await createMapPlace(payload)
      const placeId = response.data.id
      await syncImages(placeId)
      if (editorKind === 'floor' && editorPlanUrl) {
        await saveFloorPlan(placeId, { imageUrl: editorPlanUrl })
      }
      message.success(editorRecord ? '保存成功' : '创建成功')
      setEditorOpen(false)
      await loadTree()
    } finally {
      setSaving(false)
    }
  }

  const removePlace = (record, returnPath = null) => {
    Modal.confirm({
      title: `确定删除“${record.name}”吗？`,
      content: '存在下级数据时不能删除，请先处理下级楼层或房间。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        await deleteMapPlace(record.id)
        message.success('删除成功')
        if (returnPath) navigate(returnPath)
        await loadTree()
      },
    })
  }

  const openMoreMenu = (record, kind, parent, returnPath = null) => ({
    items: [
      { key: 'edit', icon: <EditOutlined />, label: '编辑' },
      { type: 'divider' },
      { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true },
    ],
    onClick: ({ key }) => {
      if (key === 'edit') openEditor(kind, record, parent)
      if (key === 'delete') removePlace(record, returnPath)
    },
  })

  const buildingContent = (
    <div className="teaching-page teaching-buildings-page">
      <div className="teaching-page-heading">
        <div>
          <h1>教学楼管理</h1>
          <p>统一管理教学楼、楼层和各类房间。</p>
        </div>
        <div className="teaching-toolbar teaching-building-toolbar">
          <Select
            value={campusFilter}
            options={campusOptions}
            onChange={setCampusFilter}
          />
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索教学楼名称"
            value={buildingKeyword}
            onChange={(event) => setBuildingKeyword(event.target.value)}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('building')}>
            新增教学楼
          </Button>
        </div>
      </div>

      <Spin spinning={loading}>
        {filteredBuildings.length ? (
          <div className="teaching-building-grid">
            {filteredBuildings.map((building) => (
              <article key={building.id} className="teaching-building-card">
                <div className="teaching-card-cover">
                  <CoverImage record={building} icon={<BankOutlined />} alt={building.name} />
                  <Button
                    className="teaching-image-edit"
                    icon={<CameraOutlined />}
                    onClick={() => openEditor('building', building)}
                  >
                    编辑图片
                  </Button>
                </div>
                <div className="teaching-building-info">
                  <h2>{building.name}</h2>
                  <div className="teaching-meta-row">
                    <EnvironmentOutlined />
                    <span>{building.locationDesc || '暂未填写校区位置'}</span>
                  </div>
                  <div className="teaching-meta-row">
                    <ApartmentOutlined />
                    <span>{getFloors(building).length} 层</span>
                  </div>
                  <div className="teaching-meta-row">
                    <BankOutlined />
                    <span>{getRoomCount(building)} 个房间</span>
                  </div>
                </div>
                <div className="teaching-card-actions">
                  <Button type="link" onClick={() => navigate(`/facility/teaching/${building.id}`)}>
                    管理楼层 <span aria-hidden="true">→</span>
                  </Button>
                  <Dropdown menu={openMoreMenu(building, 'building', null)} trigger={['click']}>
                    <Button icon={<EllipsisOutlined />} aria-label={`${building.name}更多操作`} />
                  </Dropdown>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className="teaching-empty-panel">
            <Empty description={loading ? '正在加载教学楼' : '暂无符合条件的教学楼'}>
              {!loading && !buildingKeyword && campusFilter === 'ALL' ? (
                <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('building')}>
                  新增教学楼
                </Button>
              ) : null}
            </Empty>
          </div>
        )}
      </Spin>
    </div>
  )

  const floorContent = currentBuilding ? (
    <div className="teaching-page teaching-floors-page">
      <div className="teaching-detail-heading">
        <div>
          <Button type="link" icon={<LeftOutlined />} onClick={() => navigate('/facility/teaching')}>
            返回教学楼列表
          </Button>
          <div className="teaching-title-line">
            <h1>{currentBuilding.name}</h1>
            <span>{currentBuilding.locationDesc || '校区位置未填写'}</span>
            <i />
            <span>{floors.length} 层</span>
            <i />
            <span>{getRoomCount(currentBuilding)} 个房间</span>
          </div>
        </div>
        <div className="teaching-toolbar">
          <Button icon={<EditOutlined />} onClick={() => openEditor('building', currentBuilding)}>
            编辑教学楼
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('floor', null, currentBuilding)}>
            新增楼层
          </Button>
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索楼层"
            value={floorKeyword}
            onChange={(event) => setFloorKeyword(event.target.value)}
          />
        </div>
      </div>

      <Spin spinning={loading}>
        {filteredFloors.length ? (
          <div className="teaching-floor-grid">
            {filteredFloors.map((floor) => {
              const floorRooms = getRooms(floor)
              const availableCount = floorRooms.filter((room) => getRoomUsage(room) === 'AVAILABLE').length
              return (
                <article key={floor.id} className="teaching-floor-card">
                  <div className="teaching-card-cover">
                    <CoverImage record={floor} icon={<ApartmentOutlined />} alt={floor.name} />
                    <Button
                      className="teaching-image-edit teaching-image-edit--dark"
                      icon={<CameraOutlined />}
                      onClick={() => openEditor('floor', floor, currentBuilding)}
                    >
                      编辑楼层图片
                    </Button>
                  </div>
                  <div className="teaching-floor-info">
                    <h2>{floor.name}</h2>
                    <strong>{floorRooms.length} 个房间</strong>
                    <span>{availableCount} 个空闲 · {Math.max(0, floorRooms.length - availableCount)} 个使用中</span>
                  </div>
                  <div className="teaching-card-actions">
                    <Button
                      type="link"
                      onClick={() => navigate(`/facility/teaching/${currentBuilding.id}/floors/${floor.id}`)}
                    >
                      管理房间 <span aria-hidden="true">→</span>
                    </Button>
                    <Dropdown
                      menu={openMoreMenu(floor, 'floor', currentBuilding, `/facility/teaching/${currentBuilding.id}`)}
                      trigger={['click']}
                    >
                      <Button icon={<EllipsisOutlined />} aria-label={`${floor.name}更多操作`} />
                    </Dropdown>
                  </div>
                </article>
              )
            })}
          </div>
        ) : (
          <div className="teaching-empty-panel teaching-empty-panel--compact">
            <Empty description={loading ? '正在加载楼层' : '该教学楼暂无楼层'}>
              {!loading ? (
                <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('floor', null, currentBuilding)}>
                  新增楼层
                </Button>
              ) : null}
            </Empty>
          </div>
        )}
      </Spin>
    </div>
  ) : null

  const roomColumns = [
    {
      title: '房间编号', dataIndex: 'name', width: 130,
      sorter: (left, right) => left.name.localeCompare(right.name, 'zh-CN', { numeric: true }),
    },
    {
      title: '房间类型', dataIndex: 'placeType', width: 130,
      sorter: (left, right) => (ROOM_TYPE_LABELS[left.placeType] || '').localeCompare(ROOM_TYPE_LABELS[right.placeType] || '', 'zh-CN'),
      render: (value) => ROOM_TYPE_LABELS[value] || value,
    },
    {
      title: '使用用途', dataIndex: 'usagePurpose',
      sorter: (left, right) => (left.usagePurpose || '').localeCompare(right.usagePurpose || '', 'zh-CN'),
      render: (value) => value || '未填写',
    },
    {
      title: '使用状态', dataIndex: 'usageStatus', width: 120,
      sorter: (left, right) => getRoomUsage(left).localeCompare(getRoomUsage(right)),
      render: (value) => <UsageTag status={value || 'AVAILABLE'} />,
    },
    {
      title: '点位状态', key: 'position', width: 130,
      sorter: (left, right) => Number(Boolean(left.indoorPosition)) - Number(Boolean(right.indoorPosition)),
      render: (_, room) => room.indoorPosition ? (
        <span className="teaching-position-state"><CheckCircleFilled /> 已定位</span>
      ) : <span className="teaching-position-state teaching-position-state--missing"><EnvironmentOutlined /> 待定位</span>,
    },
    {
      title: '更新时间', dataIndex: 'updatedAt', width: 170,
      sorter: (left, right) => dayjs(left.updatedAt).valueOf() - dayjs(right.updatedAt).valueOf(),
      render: (value) => value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—',
    },
    {
      title: '操作', key: 'actions', width: 170,
      render: (_, room) => (
        <div className="teaching-table-actions" onClick={(event) => event.stopPropagation()}>
          <Button type="link" onClick={() => { selectRoom(room.id); setViewMode('PLAN') }}>查看</Button>
          <Button type="link" onClick={() => openEditor('room', room, currentFloor)}>编辑</Button>
          <Dropdown menu={openMoreMenu(room, 'room', currentFloor)} trigger={['click']}>
            <Button type="text" icon={<EllipsisOutlined />} aria-label={`${room.name}更多操作`} />
          </Dropdown>
        </div>
      ),
    },
  ]

  const selectedPlanPosition = selectedRoom
    ? (placementMode ? draftPositions[String(selectedRoom.id)] : normalizePosition(selectedRoom.indoorPosition))
    : null

  const roomPlan = (
    <section className="teaching-plan-workbench">
      <div className="teaching-plan-meta">
        <span className="teaching-plan-mode">{placementMode ? '布置点位模式' : '查看模式'}</span>
        {unpositionedRoomCount ? <span className="teaching-unpositioned-count">未定位 {unpositionedRoomCount}</span> : null}
      </div>

      <div className="teaching-room-point-strip" aria-label="房间点位">
        <span className="teaching-point-strip-title">房间点位 {detailedRooms.length}</span>
        <div className="teaching-point-chip-list">
          {detailedRooms.map((room) => {
            const selected = String(room.id) === String(selectedRoomId)
            const position = placementMode ? draftPositions[String(room.id)] : normalizePosition(room.indoorPosition)
            const statusLabel = position ? (USAGE_STATUS_META[getRoomUsage(room)]?.label || '空闲') : '待定位'
            return (
              <button key={room.id} type="button" className={`teaching-point-chip${selected ? ' selected' : ''}`}
                onClick={() => focusRoomOnPlan(room)}>
                <span className={`teaching-status-dot ${position ? (getRoomUsage(room) === 'AVAILABLE' ? 'available' : 'busy') : 'missing'}`} />
                <strong>{room.name}</strong>
                <span>{room.usagePurpose || ROOM_TYPE_LABELS[room.placeType]}</span>
                <i />
                <span>{statusLabel}</span>
              </button>
            )
          })}
        </div>
      </div>

      {placementMode ? (
        <div className="teaching-placement-tip"><AimOutlined /><span>{selectedRoom
          ? `已选择 ${selectedRoom.name}，请点击平面图设置位置；再次点击可调整。`
          : '请先从上方点位条选择房间，再点击平面图设置位置。'}</span></div>
      ) : null}

      <Spin spinning={detailLoading || placementSaving}>
        {floorPlan?.imageUrl ? (
          <div ref={planViewportRef} className="teaching-plan-viewport">
            <div className="teaching-plan-zoom-controls" onClick={(event) => event.stopPropagation()}>
              <button type="button" disabled={mapZoom <= 0.75} onClick={() => setMapZoom((value) => Math.max(0.75, Number((value - 0.15).toFixed(2))))}>−</button>
              <button type="button" onClick={() => {
                setMapZoom(1)
                planViewportRef.current?.scrollTo({ left: 0, top: 0, behavior: 'smooth' })
              }}>适应</button>
              <button type="button" disabled={mapZoom >= 1.75} onClick={() => setMapZoom((value) => Math.min(1.75, Number((value + 0.15).toFixed(2))))}>＋</button>
            </div>
            <div
              ref={planZoomStageRef}
              className="teaching-plan-zoom-stage"
              style={planFitSize ? {
                width: `${Math.round(planFitSize.width * mapZoom)}px`,
                height: `${Math.round(planFitSize.height * mapZoom)}px`,
              } : undefined}
            >
              <div
                className={`teaching-floor-plan${placementMode ? ' is-placing' : ''}`}
                onClick={placeSelectedRoom}
                onDragOver={placementMode ? (event) => event.preventDefault() : undefined}
                onDrop={placementMode ? placeSelectedRoom : undefined}
              >
                <img
                  key={floorPlan.imageUrl}
                  src={floorPlan.imageUrl}
                  alt={`${currentFloor.name}平面图`}
                  onLoad={(event) => {
                    const imageSize = {
                      width: event.currentTarget.naturalWidth,
                      height: event.currentTarget.naturalHeight,
                    }
                    setPlanImageSize(imageSize)
                    setMapZoom(1)
                    window.requestAnimationFrame(() => updatePlanFitSize(imageSize))
                  }}
                />
                {detailedRooms.map((room) => {
                  const position = placementMode ? draftPositions[String(room.id)] : normalizePosition(room.indoorPosition)
                  if (!position) return null
                  const selected = String(room.id) === String(selectedRoomId)
                  const usageTone = getRoomUsage(room) === 'AVAILABLE' ? 'available' : 'busy'
                  return (
                    <button key={room.id} type="button" draggable={placementMode}
                      className={`teaching-room-pin ${usageTone}${selected ? ' selected' : ''}`}
                      style={{ left: `${position.xRatio}%`, top: `${position.yRatio}%` }}
                      onDragStart={() => selectRoom(room.id)}
                      onClick={(event) => { event.stopPropagation(); selectRoom(room.id) }}
                      aria-label={`${room.name}，${USAGE_STATUS_META[getRoomUsage(room)]?.label || '空闲'}`}>
                      <strong>{room.name}</strong>
                      <span className="teaching-room-pin-status" aria-hidden="true" />
                    </button>
                  )
                })}
                {selectedRoom && !placementMode ? (
                  <div className="teaching-room-popover" onClick={(event) => event.stopPropagation()}>
                    <button type="button" className="teaching-room-popover-close" onClick={() => setSelectedRoomId(null)} aria-label="关闭房间信息">×</button>
                    <strong>{selectedRoom.name}</strong>
                    <div><span>{selectedRoom.usagePurpose || ROOM_TYPE_LABELS[selectedRoom.placeType]}</span><UsageTag status={getRoomUsage(selectedRoom)} /></div>
                    <footer>
                      <Button onClick={() => setRoomDetailOpen(true)}>查看详情</Button>
                      <Button type="primary" icon={<EditOutlined />} onClick={() => openEditor('room', selectedRoom, currentFloor)}>编辑房间</Button>
                    </footer>
                  </div>
                ) : null}
                <span className="teaching-plan-position-summary">已定位 {positionedRoomCount} / {detailedRooms.length}</span>
              </div>
            </div>
          </div>
        ) : (
          <div className="teaching-plan-empty">
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂未上传楼层平面图">
              <Button icon={<UploadOutlined />} onClick={() => openEditor('floor', currentFloor, currentBuilding)}>编辑楼层并上传</Button>
            </Empty>
          </div>
        )}
      </Spin>
    </section>
  )

  const roomContent = currentBuilding && currentFloor ? (
    <div className="teaching-page teaching-rooms-page">
      <div className="teaching-room-heading">
        <div>
          <Button type="link" icon={<LeftOutlined />} disabled={placementMode} onClick={() => navigate(`/facility/teaching/${currentBuilding.id}`)}>返回楼层列表</Button>
          <h1>{currentBuilding.name} · {currentFloor.name}</h1>
        </div>
        <div className="teaching-room-heading-actions">
          <Select
            className="teaching-floor-select"
            value={String(currentFloor.id)}
            disabled={placementMode}
            options={floors.map((floor) => ({ value: String(floor.id), label: floor.name }))}
            onChange={(nextFloorId) => navigate(`/facility/teaching/${currentBuilding.id}/floors/${nextFloorId}`)}
          />
          {viewMode === 'PLAN' ? (placementMode ? (
            <>
              <Button icon={<UndoOutlined />} disabled={!placementHistory.length} onClick={undoPlacement}>撤销</Button>
              <Button onClick={leavePlacementMode}>取消</Button>
              <Button type="primary" icon={<SaveOutlined />} loading={placementSaving} disabled={!placementDirty} onClick={savePlacements}>保存点位</Button>
            </>
          ) : (
            <>
              <Button icon={<UnorderedListOutlined />} onClick={() => setViewMode('LIST')}>房间列表</Button>
              <Button type="primary" icon={<AimOutlined />} disabled={!floorPlan?.imageUrl || !detailedRooms.length} onClick={enterPlacementMode}>布置点位</Button>
            </>
          )) : (
            <>
              <Button icon={<ApartmentOutlined />} onClick={() => setViewMode('PLAN')}>平面图</Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('room', null, currentFloor)}>新增房间</Button>
            </>
          )}
        </div>
      </div>

      {viewMode === 'PLAN' ? roomPlan : (
        <>
          <div className="teaching-room-list-toolbar">
            <Input allowClear prefix={<SearchOutlined />} placeholder="搜索房间编号或用途" value={roomKeyword}
              onChange={(event) => setRoomKeyword(event.target.value)} />
            <Select value={usageFilter} onChange={setUsageFilter} options={[
              { label: '全部状态', value: 'ALL' },
              { label: '空闲', value: 'AVAILABLE' },
              { label: '使用中', value: 'IN_USE' },
            ]} />
          </div>
          <section className="teaching-room-table-card">
            <div className="teaching-table-heading"><h2>房间列表</h2><span>共 {filteredRooms.length} 间房</span></div>
            <div className="teaching-batch-toolbar">
              <span>已选择 {selectedRowKeys.length} 项</span>
              <Button icon={<EditOutlined />} disabled={!selectedRowKeys.length} onClick={openBatchEditor}>批量编辑</Button>
              <Button danger icon={<DeleteOutlined />} disabled={!selectedRowKeys.length} onClick={batchDeleteRooms}>批量删除</Button>
            </div>
            <Table rowKey="id" columns={roomColumns} dataSource={filteredRooms} loading={detailLoading}
              rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
              pagination={{ defaultPageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
              locale={{ emptyText: '暂无符合条件的房间' }} scroll={{ x: 1100 }} />
          </section>
        </>
      )}
    </div>
  ) : null

  const editorTitle = editorRecord
    ? `编辑${editorKind === 'building' ? '教学楼' : editorKind === 'floor' ? '楼层' : '房间'}`
    : `新增${editorKind === 'building' ? '教学楼' : editorKind === 'floor' ? '楼层' : '房间'}`

  const routeStateContent = (
    <div className="teaching-page teaching-route-state">
      {loading ? (
        <Spin size="large" tip="正在加载教学楼数据" />
      ) : (
        <Empty description={floorId ? '未找到该楼层' : '未找到该教学楼'}>
          <Button type="primary" onClick={() => navigate('/facility/teaching')}>
            返回教学楼列表
          </Button>
        </Empty>
      )}
    </div>
  )

  const activeContent = !buildingId
    ? buildingContent
    : !currentBuilding
      ? routeStateContent
      : floorId
        ? (currentFloor ? roomContent : routeStateContent)
        : floorContent

  return (
    <>
      {activeContent}

      <SidePanel
        title={editorTitle}
        open={editorOpen}
        onClose={() => setEditorOpen(false)}
        destroyOnHidden
        footer={(
          <>
            <Button onClick={() => setEditorOpen(false)}>取消</Button>
            <Button type="primary" loading={saving || uploading} onClick={saveEditor}>保存</Button>
          </>
        )}
      >
        <Form form={form} layout="vertical" className="teaching-editor-form">
          <Form.Item
            name="name"
            label={editorKind === 'building' ? '教学楼名称' : editorKind === 'floor' ? '楼层编号' : '房间编号'}
            rules={[{ required: true, message: '请填写名称或编号' }]}
          >
            <Input placeholder={editorKind === 'building' ? '例如：明德教学楼' : editorKind === 'floor' ? '例如：2F' : '例如：202'} />
          </Form.Item>

          {editorKind === 'building' ? (
            <Form.Item name="locationDesc" label="所属校区 / 位置说明">
              <Input placeholder="例如：朝阳校区东区" />
            </Form.Item>
          ) : null}

          {editorKind === 'room' ? (
            <>
              <Form.Item name="placeType" label="房间类型" rules={[{ required: true }]}>
                <Select options={ROOM_TYPE_OPTIONS} />
              </Form.Item>
              <Form.Item name="usagePurpose" label="使用用途">
                <Input placeholder="例如：普通教学、多媒体教学、实验教学或行政办公" />
              </Form.Item>
              <Form.Item name="usageStatus" label="使用状态" rules={[{ required: true }]}>
                <Select options={USAGE_STATUS_OPTIONS} />
              </Form.Item>
            </>
          ) : null}

          <Form.Item name="description" label="备注说明">
            <Input.TextArea rows={3} placeholder="填写必要的补充说明" />
          </Form.Item>

          <Form.Item label={editorKind === 'building' ? '教学楼图片' : editorKind === 'floor' ? '楼层实景图片' : '房间实景图片'}>
            <Upload
              listType="picture-card"
              accept="image/jpeg,image/png,image/webp,image/gif"
              fileList={editorImages}
              beforeUpload={uploadEditorImage}
              onRemove={(file) => {
                setEditorImages((previous) => previous.filter((item) => item.uid !== file.uid))
                return true
              }}
              disabled={uploading || editorImages.length >= 3}
            >
              {editorImages.length < 3 ? (
                <div className="teaching-upload-trigger">
                  <UploadOutlined />
                  <span>上传图片</span>
                </div>
              ) : null}
            </Upload>
          </Form.Item>

          {editorKind === 'floor' ? (
            <Form.Item label="楼层平面图">
              {editorPlanUrl ? (
                <div className="teaching-plan-upload-preview">
                  <img src={editorPlanUrl} alt="楼层平面图预览" />
                  <Upload accept="image/*" showUploadList={false} beforeUpload={uploadFloorPlan}>
                    <Button icon={<FileImageOutlined />} loading={uploading}>更换平面图</Button>
                  </Upload>
                </div>
              ) : (
                <Upload accept="image/*" showUploadList={false} beforeUpload={uploadFloorPlan}>
                  <Button icon={<FileImageOutlined />} loading={uploading}>上传平面图</Button>
                </Upload>
              )}
              <p className="teaching-form-tip">平面图用于展示并定位本楼层的房间。</p>
            </Form.Item>
          ) : null}

          {editorKind === 'room' ? (
            <div className="teaching-form-notice">
              <AimOutlined />
              房间资料保存后，可返回平面图模式，通过“布置点位”统一设置房间位置。
            </div>
          ) : null}
        </Form>
      </SidePanel>

      <Modal
        title={selectedRoom ? `房间详情 · ${selectedRoom.name}` : '房间详情'}
        open={roomDetailOpen}
        onCancel={() => setRoomDetailOpen(false)}
        footer={<Button type="primary" onClick={() => setRoomDetailOpen(false)}>关闭</Button>}
        width={560}
        destroyOnHidden
      >
        {selectedRoom ? (
          <div className="teaching-room-detail-modal">
            <div className="teaching-room-detail-summary">
              <strong>{selectedRoom.name}</strong>
              <UsageTag status={getRoomUsage(selectedRoom)} />
            </div>
            <dl className="teaching-room-detail-fields">
              <div><dt>房间类型</dt><dd>{ROOM_TYPE_LABELS[selectedRoom.placeType]}</dd></div>
              <div><dt>使用用途</dt><dd>{selectedRoom.usagePurpose || '未填写'}</dd></div>
              <div><dt>点位状态</dt><dd>{selectedPlanPosition ? '已定位' : '待定位'}</dd></div>
            </dl>
            <div className="teaching-room-detail-photo">
              <span>房间实景</span>
              <div><CoverImage record={selectedRoom} icon={<CameraOutlined />} alt={selectedRoom.name} /></div>
            </div>
          </div>
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择房间查看详情" />}
      </Modal>

      <Modal
        title={`批量编辑房间（${selectedRowKeys.length} 项）`}
        open={batchEditorOpen}
        onCancel={() => setBatchEditorOpen(false)}
        onOk={saveBatchEditor}
        okText="保存修改"
        cancelText="取消"
        confirmLoading={batchSaving}
        destroyOnHidden
      >
        <Form form={batchForm} layout="vertical" className="teaching-batch-form">
          <p>未填写的项目将保持原值不变。</p>
          <Form.Item name="usagePurpose" label="统一使用用途">
            <Input placeholder="例如：普通教学、多媒体教学或行政办公" />
          </Form.Item>
          <Form.Item name="usageStatus" label="统一使用状态">
            <Select allowClear placeholder="保持原状态" options={USAGE_STATUS_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
