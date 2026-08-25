import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Dropdown,
  Empty,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Upload,
  message,
} from 'antd'
import {
  AimOutlined,
  ApartmentOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  EllipsisOutlined,
  EnvironmentOutlined,
  FileImageOutlined,
  FileTextOutlined,
  PlusOutlined,
  ShopOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import {
  addMapPlaceImage,
  createMapPlace,
  deleteFloorPlan,
  deleteIndoorPosition,
  deleteMapPlace,
  deleteMapPlaceFence,
  deleteMapPlaceImage,
  getFloorPlan,
  getFloorPlanPositions,
  getMapPlaceDetail,
  getMapPlaceList,
  getMapPlaceTree,
  saveFloorPlan,
  saveIndoorPosition,
  saveMapPlaceFence,
  updateMapPlaceImage,
  updateMapPlace,
} from '../../../api/mapPlace'
import { MAP_BUILDING_UPLOAD_FOLDER, uploadImage } from '../../../api/upload'
import SidePanel from '../../../components/SidePanel/SidePanel'
import './FacilityPlaceManage.css'

const SCENE_CONFIG = {
  CANTEEN: {
    title: '食堂管理',
    description: '统一管理食堂、楼层、档口和就餐区域。',
    rootTypes: ['CANTEEN'],
  },
  SPORTS: {
    title: '运动场管理',
    description: '统一管理运动场及跑道、球场等下级场地。',
    rootTypes: ['SPORTS_GROUND'],
  },
  TEACHING: {
    title: '教学楼管理',
    description: '统一管理教学楼、楼层、教室、实验室和办公室。',
    rootTypes: ['TEACHING_BUILDING'],
  },
  DORMITORY: {
    title: '宿舍管理',
    description: '统一管理宿舍楼、楼层和宿舍房间。',
    rootTypes: [
      'MALE_DORMITORY',
      'FEMALE_DORMITORY',
      'STAFF_DORMITORY',
      'GUEST_DORMITORY',
      'RESIDENTIAL_AREA',
    ],
  },
}

const TYPE_LABELS = {
  CANTEEN: '食堂',
  SPORTS_GROUND: '运动场',
  TEACHING_BUILDING: '教学楼',
  DORMITORY_BUILDING: '宿舍楼',
  MALE_DORMITORY: '男生宿舍',
  FEMALE_DORMITORY: '女生宿舍',
  STAFF_DORMITORY: '教职工宿舍',
  GUEST_DORMITORY: '外宾宿舍',
  RESIDENTIAL_AREA: '小区',
  FLOOR: '楼层',
  CANTEEN_STALL: '食堂档口',
  DINING_AREA: '就餐区域',
  CLASSROOM: '教室',
  LABORATORY: '实验室',
  OFFICE: '办公室',
  DORMITORY_ROOM: '宿舍房间',
  UNDERGRADUATE_DORM: '本科生',
  POSTGRADUATE_DORM: '研究生',
  DOCTORAL_DORM: '博士生',
  FACULTY_DORM: '教师',
  LIFE_AREA: '生活类',
  STUDY_AREA: '学习类',
  RUNNING_TRACK: '跑道',
  FOOTBALL_FIELD: '足球场',
  BASKETBALL_COURT: '篮球场',
  VOLLEYBALL_COURT: '排球场',
  BADMINTON_COURT: '羽毛球场',
  LONG_JUMP_AREA: '跳远区',
  SHOT_PUT_AREA: '铅球区',
  PLATFORM: '主席台',
}

const CHILD_TYPES = {
  CANTEEN: ['FLOOR'],
  TEACHING_BUILDING: ['FLOOR'],
  DORMITORY_BUILDING: ['FLOOR'],
  MALE_DORMITORY: ['FLOOR'],
  FEMALE_DORMITORY: ['FLOOR'],
  STAFF_DORMITORY: ['FLOOR'],
  GUEST_DORMITORY: ['FLOOR'],
  RESIDENTIAL_AREA: ['FLOOR'],
  SPORTS_GROUND: [
    'RUNNING_TRACK',
    'FOOTBALL_FIELD',
    'BASKETBALL_COURT',
    'VOLLEYBALL_COURT',
    'BADMINTON_COURT',
    'LONG_JUMP_AREA',
    'SHOT_PUT_AREA',
    'PLATFORM',
  ],
}

const FLOOR_CHILD_TYPES = {
  CANTEEN: ['CANTEEN_STALL', 'DINING_AREA'],
  TEACHING: ['CLASSROOM', 'LABORATORY', 'OFFICE'],
  DORMITORY: [
    'UNDERGRADUATE_DORM',
    'POSTGRADUATE_DORM',
    'DOCTORAL_DORM',
    'FACULTY_DORM',
    'LIFE_AREA',
    'STUDY_AREA',
    'DORMITORY_ROOM',
  ],
}

const STATUS_OPTIONS = [
  { value: 'ENABLED', label: '启用' },
  { value: 'DISABLED', label: '停用' },
]

const flattenTree = (nodes, depth = 0) =>
  (nodes || []).flatMap((node) => [
    { ...node, depth },
    ...flattenTree(node.children, depth + 1),
  ])

const getAllowedChildTypes = (sceneType, parent) => {
  if (!parent) return SCENE_CONFIG[sceneType].rootTypes
  if (parent.placeType === 'FLOOR') return FLOOR_CHILD_TYPES[sceneType] || []
  return CHILD_TYPES[parent.placeType] || []
}

const normalizeFileList = (images = []) =>
  images.map((item) => ({
    uid: String(item.id),
    name: item.imageUrl.split('/').pop() || `图片${item.id}`,
    status: 'done',
    url: item.imageUrl,
    imageId: item.id,
    focusX: item.focusX ?? 50,
    focusY: item.focusY ?? 50,
  }))

function CanteenCarousel({ images = [], alt = '食堂' }) {
  const [currentIndex, setCurrentIndex] = useState(0)

  useEffect(() => {
    if (images.length <= 1) return undefined
    const timer = window.setInterval(() => {
      setCurrentIndex((previous) => (previous + 1) % images.length)
    }, 3000)
    return () => window.clearInterval(timer)
  }, [images])

  if (!images.length) {
    return (
      <div className="facility-canteen-card-placeholder">
        <ShopOutlined />
      </div>
    )
  }

  return (
    <div className="facility-canteen-carousel">
      {images.map((image, index) => (
        <img
          key={image.id || image.imageUrl}
          src={image.imageUrl}
          alt={alt}
          className={index === currentIndex % images.length ? 'active' : ''}
          style={{ objectPosition: `${image.focusX ?? 50}% ${image.focusY ?? 50}%` }}
        />
      ))}
      {images.length > 1 ? (
        <div className="facility-canteen-carousel-dots">
          {images.map((image, index) => (
            <span
              key={image.id || image.imageUrl}
              className={index === currentIndex % images.length ? 'active' : ''}
            />
          ))}
        </div>
      ) : null}
    </div>
  )
}

export default function FacilityPlaceManage({
  sceneType,
  rootPlaceId = null,
  managementRootPlaceId = null,
  floorId = null,
}) {
  const config = SCENE_CONFIG[sceneType]
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [planForm] = Form.useForm()
  const [positionForm] = Form.useForm()
  const [tree, setTree] = useState([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [parent, setParent] = useState(null)
  const [fileList, setFileList] = useState([])
  const [previewImageUid, setPreviewImageUid] = useState(null)
  const [planOpen, setPlanOpen] = useState(false)
  const [returnToRoomManagerAfterPlan, setReturnToRoomManagerAfterPlan] = useState(false)
  const [planFloor, setPlanFloor] = useState(null)
  const [plan, setPlan] = useState(null)
  const [planPlacements, setPlanPlacements] = useState([])
  const [planShowPlacements, setPlanShowPlacements] = useState(false)
  const [placementsLoading, setPlacementsLoading] = useState(false)
  const [positionOpen, setPositionOpen] = useState(false)
  const [positionPlace, setPositionPlace] = useState(null)
  const [positionPlan, setPositionPlan] = useState(null)
  const [keyword, setKeyword] = useState('')
  const [managementFloorFilter, setManagementFloorFilter] = useState('ALL')
  const [roomManagerOpen, setRoomManagerOpen] = useState(false)
  const [rootPlace, setRootPlace] = useState(null)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const planImageUrl = Form.useWatch('imageUrl', planForm)
  const positionX = Form.useWatch('xRatio', positionForm)
  const positionY = Form.useWatch('yRatio', positionForm)
  const positionId = Form.useWatch('positionId', positionForm)

  const flatPlaces = useMemo(
    () => flattenTree(rootPlace ? [{ ...rootPlace, children: tree }] : tree),
    [rootPlace, tree],
  )

  const loadTree = useCallback(async () => {
    setLoading(true)
    try {
      if (floorId) {
        const loadChildren = async (parentId) => {
          const response = await getMapPlaceList({ sceneType, parentId })
          const children = Array.isArray(response.data) ? response.data : []
          return Promise.all(
            children.map(async (child) => ({
              ...child,
              children: getAllowedChildTypes(sceneType, child).length
                ? await loadChildren(child.id)
                : [],
            })),
          )
        }
        const [floorResponse, children] = await Promise.all([
          getMapPlaceDetail(floorId),
          loadChildren(floorId),
        ])
        setRootPlace(floorResponse.data || null)
        setTree(children)
        return
      }
      if (rootPlaceId || managementRootPlaceId) {
        const activeRootPlaceId = rootPlaceId || managementRootPlaceId
        const loadChildren = async (parentId) => {
          const response = await getMapPlaceList({ sceneType, parentId })
          const children = Array.isArray(response.data) ? response.data : []
          return Promise.all(
            children.map(async (child) => ({
              ...child,
              children: getAllowedChildTypes(sceneType, child).length
                ? await loadChildren(child.id)
                : [],
            })),
          )
        }
        const [rootResponse, children] = await Promise.all([
          getMapPlaceDetail(activeRootPlaceId),
          loadChildren(activeRootPlaceId),
        ])
        setRootPlace(rootResponse.data || null)
        setTree(children)
        return
      }
      if (sceneType === 'CANTEEN') {
        const response = await getMapPlaceList({ sceneType, placeType: config.rootTypes[0] })
        setRootPlace(null)
        setTree(Array.isArray(response.data) ? response.data : [])
        return
      }
      const response = await getMapPlaceTree(sceneType)
      setRootPlace(null)
      setTree(Array.isArray(response.data) ? response.data : [])
    } finally {
      setLoading(false)
    }
  }, [config.rootTypes, floorId, managementRootPlaceId, rootPlaceId, sceneType])

  useEffect(() => {
    loadTree()
  }, [loadTree])

  const enterFacilityManagement = async (place) => {
    if (!place || sceneType === 'SPORTS') return
    setLoading(true)
    try {
      const loadChildren = async (parentId) => {
        const response = await getMapPlaceList({ sceneType, parentId })
        const children = Array.isArray(response.data) ? response.data : []
        return Promise.all(children.map(async (child) => ({
          ...child,
          children: getAllowedChildTypes(sceneType, child).length
            ? await loadChildren(child.id)
            : [],
        })))
      }
      const children = await loadChildren(place.id)
      setRootPlace(place)
      setTree(children)
    } catch (error) {
      message.error(error?.message || '下级设施加载失败')
    } finally {
      setLoading(false)
    }
  }

  const filteredTree = useMemo(() => {
    const query = keyword.trim().toLowerCase()
    const filterNodes = (nodes) =>
      nodes.reduce((result, node) => {
        const children = filterNodes(node.children || [])
        const matchesStatus = statusFilter === 'ALL' || node.status === statusFilter
        const matchesKeyword = !query || node.name.toLowerCase().includes(query)
        if ((matchesStatus && matchesKeyword) || children.length) {
          result.push({ ...node, children })
        }
        return result
      }, [])
    return filterNodes(tree)
  }, [keyword, statusFilter, tree])

  const rootFloorCounts = useMemo(() => {
    const counts = new Map()
    const collect = (nodes = []) => {
      nodes.forEach((node) => {
        if (node.children?.length) {
          counts.set(node.id, node.children.filter((child) => child.placeType === 'FLOOR').length)
          collect(node.children)
        }
      })
    }
    collect(tree)
    return counts
  }, [tree])

  const openCreate = (parentPlace = null) => {
    setEditing(null)
    setParent(parentPlace)
    setFileList([])
    setPreviewImageUid(null)
    const allowed = getAllowedChildTypes(sceneType, parentPlace)
    form.resetFields()
    form.setFieldsValue({
      parentId: parentPlace?.id,
      placeType: allowed[0],
      status: 'ENABLED',
      mapVisible: parentPlace?.placeType !== 'FLOOR',
      sortOrder: 0,
      geometryType: 'POLYGON',
    })
    setEditorOpen(true)
  }

  const openEdit = async (record) => {
    setSaving(true)
    try {
      const response = await getMapPlaceDetail(record.id)
      const detail = response.data
      setEditing(detail)
      setParent(flatPlaces.find((item) => item.id === detail.parentId) || null)
      const normalizedImages = normalizeFileList(detail.images)
      setFileList(normalizedImages)
      setPreviewImageUid(normalizedImages[0]?.uid || null)
      form.resetFields()
      form.setFieldsValue({
        ...detail,
        geometryType: detail.fence?.geometryType || 'POLYGON',
        geometryData: detail.fence?.geometryData,
      })
      setEditorOpen(true)
    } finally {
      setSaving(false)
    }
  }

  const syncImages = async (placeId) => {
    const previousIds = new Set((editing?.images || []).map((item) => item.id))
    const retainedIds = new Set(fileList.map((item) => item.imageId).filter(Boolean))
    await Promise.all(
      [...previousIds].filter((id) => !retainedIds.has(id)).map((id) => deleteMapPlaceImage(id)),
    )
    const newFiles = fileList.filter((item) => !item.imageId && item.url)
    const retainedFiles = fileList.filter((item) => item.imageId)
    await Promise.all([
      ...retainedFiles.map((item, index) =>
        updateMapPlaceImage(item.imageId, {
          imageUrl: item.url,
          sortOrder: index,
          focusX: item.focusX ?? 50,
          focusY: item.focusY ?? 50,
        }),
      ),
      ...newFiles.map((item, index) =>
        addMapPlaceImage(placeId, {
          imageUrl: item.url,
          sortOrder: retainedFiles.length + index,
          focusX: item.focusX ?? 50,
          focusY: item.focusY ?? 50,
        }),
      ),
    ])
  }

  const savePlace = async () => {
    const values = await form.validateFields()
    const usesSeparateLocationPanel = sceneType === 'CANTEEN' && !rootPlaceId
    const keepsExistingLocation = usesSeparateLocationPanel || isFloorLevel || isFacilityLevel
    setSaving(true)
    try {
      const payload = {
        parentId: values.parentId ?? (editing ? (editing.parentId ?? null) : (parent?.id ?? null)),
        sceneType,
        placeType: values.placeType || editing?.placeType || getAllowedChildTypes(sceneType, parent)[0],
        name: values.name,
        description: values.description || '',
        status: values.status,
        longitude: keepsExistingLocation ? (editing?.longitude ?? null) : (values.longitude ?? null),
        latitude: keepsExistingLocation ? (editing?.latitude ?? null) : (values.latitude ?? null),
        locationDesc: keepsExistingLocation ? (editing?.locationDesc || '') : (values.locationDesc || ''),
        mapVisible: keepsExistingLocation
          ? (editing ? Boolean(editing.mapVisible) : true)
          : Boolean(values.mapVisible),
        sortOrder: values.sortOrder || 0,
      }
      const response = editing
        ? await updateMapPlace(editing.id, payload)
        : await createMapPlace(payload)
      const placeId = response.data.id
      await syncImages(placeId)
      if (!keepsExistingLocation && !isDormitory && values.geometryData?.trim()) {
        await saveMapPlaceFence(placeId, {
          geometryType: values.geometryType,
          geometryData: values.geometryData.trim(),
        })
      } else if (!keepsExistingLocation && !isDormitory && editing?.fence) {
        await deleteMapPlaceFence(placeId)
      }
      message.success(editing ? '点位已更新' : '点位已创建')
      setEditorOpen(false)
      if (rootPlace && !rootPlaceId) await enterFacilityManagement(rootPlace)
      else await loadTree()
    } finally {
      setSaving(false)
    }
  }

  const removePlace = async (record) => {
    await deleteMapPlace(record.id)
    message.success('点位已删除')
    if (rootPlace && !rootPlaceId) await enterFacilityManagement(rootPlace)
    else await loadTree()
  }

  const confirmRemovePlace = (record) => {
    Modal.confirm({
      title: `确定删除“${record.name}”吗？`,
      content: `仅可删除没有下级点位的设施；如存在楼层、${sceneType === 'TEACHING' ? '教室' : '房间'}或档口，请先处理下级点位。`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => removePlace(record),
    })
  }

  const uploadFile = async (file) => {
    try {
      const url = await uploadImage(file, MAP_BUILDING_UPLOAD_FOLDER)
      const uid = `new-${Date.now()}`
      setFileList((previous) => [
        ...previous,
        {
          uid,
          name: file.name,
          status: 'done',
          url,
          focusX: 50,
          focusY: 50,
        },
      ])
      setPreviewImageUid(uid)
    } catch (error) {
      message.error(error?.message || '图片上传失败')
    }
    return false
  }

  const previewImage = fileList.find((item) => item.uid === previewImageUid) || null

  const updatePreviewFocus = (event) => {
    if (!previewImage || (event.type === 'pointermove' && event.buttons !== 1)) return
    const bounds = event.currentTarget.getBoundingClientRect()
    const focusX = Math.round(Math.max(0, Math.min(100, ((event.clientX - bounds.left) / bounds.width) * 100)))
    const focusY = Math.round(Math.max(0, Math.min(100, ((event.clientY - bounds.top) / bounds.height) * 100)))
    event.currentTarget.setPointerCapture?.(event.pointerId)
    setFileList((previous) => previous.map((item) =>
      item.uid === previewImage.uid ? { ...item, focusX, focusY } : item,
    ))
  }

  const resetPreviewFocus = () => {
    if (!previewImage) return
    setFileList((previous) => previous.map((item) =>
      item.uid === previewImage.uid ? { ...item, focusX: 50, focusY: 50 } : item,
    ))
  }

  const removeImageFile = (file) => {
    setFileList((previous) => previous.filter((item) => item.uid !== file.uid))
    if (previewImageUid === file.uid) setPreviewImageUid(null)
  }

  const openPlan = async (floor) => {
    const shouldReturnToRoomManager = roomManagerOpen
    setReturnToRoomManagerAfterPlan(shouldReturnToRoomManager)
    if (shouldReturnToRoomManager) setRoomManagerOpen(false)
    setPlanFloor(floor)
    setSaving(true)
    try {
      const response = await getFloorPlan(floor.id)
      setPlan(response.data || null)
      setPlanPlacements([])
      setPlanShowPlacements(false)
      planForm.setFieldsValue({ imageUrl: response.data?.imageUrl || '' })
      setPlanOpen(true)
    } catch (error) {
      if (shouldReturnToRoomManager) setRoomManagerOpen(true)
      setReturnToRoomManagerAfterPlan(false)
      if (!error?.showMessage) message.error(error?.message || '平面图加载失败')
    } finally {
      setSaving(false)
    }
  }

  const submitPlan = async () => {
    const values = await planForm.validateFields()
    setSaving(true)
    try {
      const response = await saveFloorPlan(planFloor.id, values)
      setPlan(response.data)
      message.success('楼层平面图已保存')
      setPlanOpen(false)
      setPlanShowPlacements(false)
      setPlanPlacements([])
    } finally {
      setSaving(false)
    }
  }

  const loadPlanPlacements = async () => {
    if (!plan || !planFloor) return
    setPlacementsLoading(true)
    try {
      const [positionResponse, listResponse] = await Promise.all([
        getFloorPlanPositions(plan.id),
        getMapPlaceList({ sceneType, parentId: planFloor.id }),
      ])
      const positions = Array.isArray(positionResponse.data) ? positionResponse.data : []
      const places = Array.isArray(listResponse.data) ? listResponse.data : []
      const nameById = new Map(places.map((item) => [String(item.id), item.name]))
      const placements = positions.map((position) => ({
        placeId: position.placeId,
        name: nameById.get(String(position.placeId)) || `点位 ${position.placeId}`,
        xRatio: position.xRatio,
        yRatio: position.yRatio,
      }))
      if (!placements.length) {
        message.info('该楼层暂无已定位的点位，请先通过「室内定位」为设施设置位置')
        return
      }
      setPlanPlacements(placements)
      setPlanShowPlacements(true)
    } finally {
      setPlacementsLoading(false)
    }
  }

  const uploadPlan = async (file) => {
    try {
      const url = await uploadImage(file, MAP_BUILDING_UPLOAD_FOLDER)
      planForm.setFieldValue('imageUrl', url)
    } catch (error) {
      message.error(error?.message || '平面图上传失败')
    }
    return false
  }

  const openPosition = async (record) => {
    const floor = flatPlaces.find((item) => item.id === record.parentId)
    if (!floor || floor.placeType !== 'FLOOR') return
    setSaving(true)
    try {
      const [planResponse, detailResponse] = await Promise.all([
        getFloorPlan(floor.id),
        getMapPlaceDetail(record.id),
      ])
      if (!planResponse.data) {
        message.warning('请先为所属楼层上传平面图')
        return
      }
      setPositionPlace(record)
      setPositionPlan(planResponse.data)
      positionForm.setFieldsValue({
        xRatio: detailResponse.data?.indoorPosition?.xRatio,
        yRatio: detailResponse.data?.indoorPosition?.yRatio,
        positionId: detailResponse.data?.indoorPosition?.id,
      })
      setPositionOpen(true)
    } finally {
      setSaving(false)
    }
  }

  const submitPosition = async () => {
    const values = await positionForm.validateFields()
    setSaving(true)
    try {
      await saveIndoorPosition(positionPlace.id, {
        floorPlanId: positionPlan.id,
        xRatio: values.xRatio,
        yRatio: values.yRatio,
      })
      message.success('室内位置已保存')
      setPositionOpen(false)
    } finally {
      setSaving(false)
    }
  }

  const columns = [
    {
      title: '点位名称',
      dataIndex: 'name',
      width: 260,
      render: (name, record) => (
        <Space>
          <ApartmentOutlined className="place-tree-icon" />
          <span className="place-name">{name}</span>
          {!record.mapVisible ? <Tag>地图隐藏</Tag> : null}
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'placeType',
      width: 150,
      render: (value) => <Tag color="blue">{TYPE_LABELS[value] || value}</Tag>,
    },
    {
      title: '位置',
      dataIndex: 'locationDesc',
      ellipsis: true,
      render: (value, record) =>
        value || (record.longitude != null ? `${record.longitude}, ${record.latitude}` : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value) => (
        <Tag color={value === 'ENABLED' ? 'success' : 'default'}>
          {value === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 340,
      render: (_, record) => {
        const childTypes = getAllowedChildTypes(sceneType, record)
        const isIndoorChild =
          record.parentId && flatPlaces.find((item) => item.id === record.parentId)?.placeType === 'FLOOR'
        return (
          <Space wrap>
            {!rootPlaceId && sceneType === 'CANTEEN' && record.placeType === 'CANTEEN' ? (
              <Button
                type="primary"
                ghost
                size="small"
                icon={<ShopOutlined />}
                onClick={() => navigate(`/facility/canteen/${record.id}/stalls`)}
              >
                进入档口
              </Button>
            ) : childTypes.length ? (
              <Button size="small" icon={<PlusOutlined />} onClick={() => openCreate(record)}>
                新增下级
              </Button>
            ) : null}
            {record.placeType === 'FLOOR' ? (
              <Button size="small" icon={<FileImageOutlined />} onClick={() => openPlan(record)}>
                平面图
              </Button>
            ) : null}
            {isIndoorChild ? (
              <Button size="small" icon={<EnvironmentOutlined />} onClick={() => openPosition(record)}>
                室内定位
              </Button>
            ) : null}
            <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
              编辑
            </Button>
            <Popconfirm
              title="确定删除该点位吗？"
              description="存在下级点位时不能删除。"
              onConfirm={() => removePlace(record)}
            >
              <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  if (!config) return <Empty description="未知设施场景" />

  const isOverview = !rootPlaceId && !rootPlace && !floorId
  const isSimpleEditor = sceneType === 'CANTEEN' && isOverview
  const isDormitory = sceneType === 'DORMITORY'
  const isFloorLevel = Boolean(rootPlaceId) && !floorId
  const isFacilityLevel = Boolean(floorId)
  const isOutdoorLevel = !isFloorLevel && !isFacilityLevel
  const roomEntityLabel = sceneType === 'TEACHING' ? '教室' : '房间'
  const roomManagerLabel = sceneType === 'TEACHING' ? '楼层与教室' : '楼层与房间'
  const roomLocatorLabel = sceneType === 'TEACHING' ? '楼层教室定位' : '楼层房间定位'
  const pageTitle = rootPlace
    ? `${rootPlace.name} · ${floorId
      ? '设施管理'
      : sceneType === 'CANTEEN'
        ? '档口管理'
        : sceneType === 'SPORTS' ? '平面图管理' : roomManagerLabel}`
    : config.title
  const pageDescription = rootPlace
    ? (floorId
        ? '统一管理该楼层的设施、房间及其定位。'
        : sceneType === 'CANTEEN'
          ? '进入食堂后单独加载并管理楼层、档口和就餐区域。'
          : sceneType === 'TEACHING'
            ? '按楼层管理教室、实验室和办公室。'
            : '按楼层管理宿舍房间。')
    : sceneType === 'CANTEEN'
      ? ''
      : config.description
  const createParent = rootPlace || null
  const createLabel = rootPlace
    ? (floorId ? '新增设施' : '新增楼层')
    : sceneType === 'CANTEEN'
      ? '新增食堂'
      : sceneType === 'SPORTS'
        ? '新增运动场'
        : sceneType === 'TEACHING'
          ? '新增教学楼'
          : sceneType === 'DORMITORY'
            ? '新增宿舍楼'
            : '新增设施'
  const isCanteenOverview = isOverview
  const isRoomManagement = Boolean(rootPlace)
    && !floorId
    && ['TEACHING', 'DORMITORY'].includes(sceneType)
  const managementFloors = isRoomManagement
    ? filteredTree.filter((item) => item.placeType === 'FLOOR')
    : []
  const selectedManagementFloor = managementFloors.find(
    (item) => String(item.id) === String(managementFloorFilter),
  )
  const roomCreateParent = selectedManagementFloor || managementFloors[0]
  const managementRooms = managementFloors.flatMap((floor) =>
    (floor.children || []).map((room) => ({ ...room, floorName: floor.name, floorId: floor.id })))
    .filter((room) => managementFloorFilter === 'ALL'
      || String(room.floorId) === String(managementFloorFilter))
  const roomTypeLabel = sceneType === 'TEACHING' ? '教室' : '宿舍房间'
  const facilityListLabel = {
    CANTEEN: '食堂列表',
    SPORTS: '运动场列表',
    TEACHING: '教学楼列表',
    DORMITORY: '宿舍楼列表',
  }[sceneType] || '设施列表'
  const isRoomEditor = isRoomManagement && (
    parent?.placeType === 'FLOOR'
    || [
      'CLASSROOM',
      'LABORATORY',
      'OFFICE',
      'DORMITORY_ROOM',
      'UNDERGRADUATE_DORM',
      'POSTGRADUATE_DORM',
      'DOCTORAL_DORM',
      'FACULTY_DORM',
      'LIFE_AREA',
      'STUDY_AREA',
    ].includes(editing?.placeType)
  )
  const isBuildingFloorEditor = isRoomManagement && (
    SCENE_CONFIG[sceneType].rootTypes.includes(parent?.placeType)
    || editing?.placeType === 'FLOOR'
  )
  const editorTitle = isRoomEditor
    ? (editing ? `编辑${roomEntityLabel}` : `新增${roomEntityLabel}`)
    : editing
      ? '编辑点位'
      : parent
        ? `在“${parent.name}”下新增`
        : createLabel
  const canteenCounts = {
    ALL: tree.length,
    ENABLED: tree.filter((item) => item.status === 'ENABLED').length,
    DISABLED: tree.filter((item) => item.status === 'DISABLED').length,
  }

  return (
    <div className={`facility-place-page${isOverview ? ' facility-canteen-overview-page' : ''}`}>
      <div className="facility-place-toolbar">
        <div>
          {rootPlace ? (
            <Button
              type="link"
              className="facility-place-back"
              onClick={() => {
                if (floorId) {
                  navigate(`/facility/dormitory/${rootPlaceId}`)
                  return
                }
                if (managementRootPlaceId) {
                  navigate(`/facility/${sceneType === 'TEACHING' ? 'teaching' : 'dormitory'}`)
                  return
                }
                if (!rootPlaceId) {
                  setRootPlace(null)
                  setManagementFloorFilter('ALL')
                  loadTree()
                  return
                }
                navigate({
                  CANTEEN: '/facility/canteen',
                  SPORTS: '/facility/sports',
                  TEACHING: '/facility/teaching',
                  DORMITORY: '/facility/dormitory',
                }[sceneType] || '/facility/canteen')
              }}
            >
              ← 返回{facilityListLabel}
            </Button>
          ) : null}
          {!isCanteenOverview && !isRoomManagement ? <h1>{pageTitle}</h1> : null}
          {pageDescription && !isRoomManagement ? <p>{pageDescription}</p> : null}
        </div>
        <Space wrap>
          {isRoomManagement ? (
            <Button
              icon={<AimOutlined />}
              onClick={() => navigate(`/facility/${sceneType === 'TEACHING' ? 'teaching' : 'dormitory'}/${rootPlace.id}/rooms/indoor`)}
            >
              {roomLocatorLabel}
            </Button>
          ) : null}
          {isRoomManagement ? (
            <Button onClick={() => setRoomManagerOpen(true)}>{roomManagerLabel}</Button>
          ) : null}
          {isRoomManagement ? (
            <Select
              value={managementFloorFilter}
              onChange={setManagementFloorFilter}
              style={{ width: 150 }}
              options={[
                { value: 'ALL', label: '全部楼层' },
                ...managementFloors.map((floor) => ({ value: floor.id, label: floor.name })),
              ]}
            />
          ) : null}
          <Input.Search
            allowClear
            placeholder="搜索点位"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
          {!isRoomManagement ? (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openCreate(createParent)}
            >
              {createLabel}
            </Button>
          ) : null}
        </Space>
      </div>

      {isOverview ? (
        <>
          <div className="facility-canteen-stats">
            {[
              ['ALL', '全部'],
              ['ENABLED', '启用'],
              ['DISABLED', '停用'],
            ].map(([value, label]) => (
              <button
                key={value}
                type="button"
                className={statusFilter === value ? 'active' : ''}
                onClick={() => setStatusFilter(value)}
              >
                {label}
                <span>{canteenCounts[value]}</span>
              </button>
            ))}
          </div>
          {filteredTree.length ? (
            <div className="facility-canteen-grid">
              {filteredTree.map((canteen) => {
                const hasLocation = canteen.longitude != null && canteen.latitude != null
                const childCount = canteen.children?.length ?? 0
                const childLabel = sceneType === 'CANTEEN'
                  ? '个档口'
                  : sceneType === 'SPORTS' ? '个场地' : '个楼层'
                const overviewActionLabel = sceneType === 'CANTEEN'
                  ? '进入档口管理'
                  : sceneType === 'SPORTS' ? '进入平面图管理' : `进入${roomManagerLabel}`
                return (
                  <Card
                    key={canteen.id}
                    className="facility-canteen-card"
                    styles={{ body: { padding: 0 } }}
                  >
                    <div className="facility-canteen-card-image">
                      <CanteenCarousel images={canteen.images} alt={isDormitory ? '宿舍' : '食堂'} />
                      <div className="facility-canteen-image-shade" />
                      <div className="facility-canteen-heading">
                        <h2>{canteen.name}</h2>
                        <Tag color={canteen.status === 'ENABLED' ? 'success' : 'default'}>
                          {canteen.status === 'ENABLED' ? '启用' : '停用'}
                        </Tag>
                      </div>
                    </div>
                    <div className="facility-canteen-card-info">
                      <div className="facility-canteen-summary-row">
                        {isDormitory ? <ApartmentOutlined /> : <ShopOutlined />}
                        <span><strong>{sceneType === 'CANTEEN' ? (canteen.stallCount ?? 0) : childCount}</strong> {childLabel}</span>
                      </div>
                      <div className="facility-canteen-summary-row">
                        <EnvironmentOutlined />
                        <span>{hasLocation ? '户外位置已配置' : '暂未配置户外位置'}</span>
                      </div>
                    </div>
                    <div className="facility-canteen-actions">
                      <Button
                        type="primary"
                        icon={isDormitory ? <ApartmentOutlined /> : <ShopOutlined />}
                        onClick={() => sceneType === 'CANTEEN'
                          ? navigate(`/facility/canteen/${canteen.id}/stalls`)
                          : sceneType === 'SPORTS'
                            ? navigate(`/facility/marker?mapPlaceId=${canteen.id}`)
                            : enterFacilityManagement(canteen)}
                      >
                        {overviewActionLabel}
                      </Button>
                      <Dropdown
                        trigger={['click']}
                        placement="bottomRight"
                        menu={{
                          items: [
                            { key: 'location', icon: <EnvironmentOutlined />, label: '位置管理' },
                            { key: 'edit', icon: <EditOutlined />, label: `编辑${config.title.replace('管理', '')}` },
                            { type: 'divider' },
                            { key: 'delete', icon: <DeleteOutlined />, label: `删除${config.title.replace('管理', '')}`, danger: true },
                          ],
                          onClick: ({ key }) => {
                            if (key === 'location') navigate(`/facility/marker?mapPlaceId=${canteen.id}`)
                            if (key === 'edit') openEdit(canteen)
                            if (key === 'delete') confirmRemovePlace(canteen)
                          },
                        }}
                      >
                        <Button
                          className="facility-canteen-more"
                          icon={<EllipsisOutlined />}
                          aria-label={`${canteen.name}更多操作`}
                        />
                      </Dropdown>
                    </div>
                  </Card>
                )
              })}
            </div>
          ) : (
            <Card className="facility-place-card">
              <Empty description={loading
                ? (isDormitory ? '正在加载宿舍' : '正在加载食堂')
                : (isDormitory ? '暂无符合条件的宿舍' : '暂无符合条件的食堂')} />
            </Card>
          )}
        </>
      ) : floorId ? (
        <>
          {filteredTree.length ? (
            <div className="facility-canteen-grid">
              {filteredTree.map((facility) => {
                return (
                  <Card
                    key={facility.id}
                    className="facility-canteen-card"
                    styles={{ body: { padding: 0 } }}
                  >
                    <div className="facility-canteen-card-image">
                      <CanteenCarousel images={facility.images} alt="设施" />
                      <div className="facility-canteen-image-shade" />
                      <div className="facility-canteen-heading">
                        <h2>{facility.name}</h2>
                        <Tag color={facility.status === 'ENABLED' ? 'success' : 'default'}>
                          {facility.status === 'ENABLED' ? '启用' : '停用'}
                        </Tag>
                      </div>
                    </div>
                    <div className="facility-canteen-card-info">
                      <div className="facility-canteen-summary-row">
                        <ApartmentOutlined />
                        <span>
                          <Tag color="blue">{TYPE_LABELS[facility.placeType] || facility.placeType}</Tag>
                        </span>
                      </div>
                      <div className="facility-canteen-summary-row">
                        <FileTextOutlined />
                        <span className="facility-card-ellipsis">{facility.description || '暂无设施说明'}</span>
                      </div>
                    </div>
                    <div className="facility-canteen-actions facility-floor-actions">
                      <Button
                        type="primary"
                        icon={<EditOutlined />}
                        onClick={() => openEdit(facility)}
                      >
                        编辑设施
                      </Button>
                      <Button
                        icon={<EnvironmentOutlined />}
                        onClick={() => openPosition(facility)}
                      >
                        室内定位
                      </Button>
                      <Dropdown
                        trigger={['click']}
                        placement="bottomRight"
                        menu={{
                          items: [
                            { key: 'delete', icon: <DeleteOutlined />, label: '删除设施', danger: true },
                          ],
                          onClick: ({ key }) => {
                            if (key === 'delete') confirmRemovePlace(facility)
                          },
                        }}
                      >
                        <Button
                          className="facility-canteen-more"
                          icon={<EllipsisOutlined />}
                          aria-label={`${facility.name}更多操作`}
                        />
                      </Dropdown>
                    </div>
                  </Card>
                )
              })}
            </div>
          ) : (
            <Card className="facility-place-card">
              <Empty description={loading ? '正在加载设施' : '暂无符合条件的设施'} />
            </Card>
          )}
        </>
      ) : rootPlaceId ? (
        <>
          {filteredTree.length ? (
            <div className="facility-canteen-grid">
              {filteredTree.map((floor) => {
                const children = (tree.find((item) => String(item.id) === String(floor.id))?.children) || []
                return (
                  <Card
                    key={floor.id}
                    className="facility-canteen-card"
                    styles={{ body: { padding: 0 } }}
                  >
                    <div className="facility-canteen-card-image">
                      <CanteenCarousel images={floor.images} alt="楼层" />
                      <div className="facility-canteen-image-shade" />
                      <div className="facility-canteen-heading">
                        <h2>{floor.name}</h2>
                        <Tag color={floor.status === 'ENABLED' ? 'success' : 'default'}>
                          {floor.status === 'ENABLED' ? '启用' : '停用'}
                        </Tag>
                      </div>
                    </div>
                    <div className="facility-canteen-card-info">
                      <div className="facility-canteen-summary-row">
                        <ApartmentOutlined />
                        <span><strong>{children.length}</strong> 个设施</span>
                      </div>
                      <div className="facility-canteen-summary-row">
                        <FileTextOutlined />
                        <span className="facility-card-ellipsis">{floor.description || '暂无楼层公告'}</span>
                      </div>
                    </div>
                    <div className="facility-canteen-actions facility-floor-actions">
                      <Button
                        type="primary"
                        icon={<ApartmentOutlined />}
                        onClick={() => navigate(`/facility/dormitory/${rootPlaceId}/floors/${floor.id}`)}
                      >
                        管理设施
                      </Button>
                      <Button icon={<EditOutlined />} onClick={() => openEdit(floor)}>
                        编辑楼层
                      </Button>
                      <Button icon={<FileImageOutlined />} onClick={() => openPlan(floor)}>
                        平面图
                      </Button>
                      <Dropdown
                        trigger={['click']}
                        placement="bottomRight"
                        menu={{
                          items: [
                            { key: 'delete', icon: <DeleteOutlined />, label: '删除楼层', danger: true },
                          ],
                          onClick: ({ key }) => {
                            if (key === 'delete') confirmRemovePlace(floor)
                          },
                        }}
                      >
                        <Button
                          className="facility-canteen-more"
                          icon={<EllipsisOutlined />}
                          aria-label={`${floor.name}更多操作`}
                        />
                      </Dropdown>
                    </div>
                  </Card>
                )
              })}
            </div>
          ) : (
            <Card className="facility-place-card">
              <Empty description={loading ? '正在加载楼层' : '暂无符合条件的楼层'} />
            </Card>
          )}
        </>
      ) : isRoomManagement ? (
        <Card className="facility-place-card facility-room-management-card" styles={{ body: { padding: 24 } }}>
          <div className="facility-room-management-summary">
            <span><strong>{managementFloors.length}</strong> 个楼层</span>
            <span><strong>{managementRooms.length}</strong> 个{roomTypeLabel}</span>
          </div>
          {managementRooms.length ? (
            <div className="facility-room-grid">
              {managementRooms.map((room) => (
                <Card key={room.id} className="facility-room-card" styles={{ body: { padding: 0 } }}>
                  <div className="facility-room-card-cover">
                    <CanteenCarousel images={room.images || []} />
                    <div className="facility-canteen-image-shade" />
                    <div className="facility-canteen-heading">
                      <h2>{room.name}</h2>
                      <Tag color={room.status === 'ENABLED' ? 'success' : 'default'}>
                        {room.status === 'ENABLED' ? '启用' : '停用'}
                      </Tag>
                    </div>
                  </div>
                  <div className="facility-room-card-info">
                    <div><ApartmentOutlined /><span>所在楼层：{room.floorName}</span></div>
                    <div>
                      <CheckCircleOutlined />
                      <span>启用状态：{room.status === 'ENABLED' ? '启用' : '停用'}</span>
                    </div>
                  </div>
                  <div className="facility-room-card-actions">
                    <Button type="primary" onClick={() => openEdit(room)}>编辑{roomEntityLabel}</Button>
                    <Popconfirm title={`确定删除“${room.name}”吗？`} onConfirm={() => removePlace(room)}>
                      <Button danger icon={<DeleteOutlined />}>删除</Button>
                    </Popconfirm>
                  </div>
                </Card>
              ))}
            </div>
          ) : (
            <Empty description={managementFloors.length ? `该楼层暂无${roomTypeLabel}` : '请先新增楼层'} />
          )}
        </Card>
      ) : (
        <Card className="facility-place-card">
          <Table
            rowKey="id"
            columns={columns}
            dataSource={filteredTree}
            loading={loading}
            pagination={false}
            expandable={rootPlaceId || rootPlace ? { defaultExpandAllRows: true } : undefined}
            locale={{ emptyText: rootPlace ? `暂无楼层或下级${roomEntityLabel}` : '暂无设施点位' }}
            scroll={{ x: 1080 }}
          />
        </Card>
      )}

      <SidePanel
        title={`${rootPlace?.name || ''} · ${roomManagerLabel}`}
        open={roomManagerOpen}
        onClose={() => setRoomManagerOpen(false)}
        destroyOnHidden
      >
        <div className="facility-room-manager-tip">
          楼层作为建筑的下级层级，{sceneType === 'TEACHING' ? '教室、实验室和办公室' : '宿舍房间'}归属到具体楼层。
        </div>
        <Tabs
          items={[
            {
              key: 'floor',
              label: `楼层点位（${managementFloors.length}）`,
              children: (
                <div className="facility-room-manager-section">
                  <div className="facility-room-manager-toolbar">
                    <p>维护建筑楼层，并为每个楼层配置平面图。</p>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate(rootPlace)}>
                      新增楼层
                    </Button>
                  </div>
                  <Table
                    rowKey="id"
                    size="small"
                    pagination={false}
                    dataSource={managementFloors}
                    locale={{ emptyText: '暂未配置楼层' }}
                    columns={[
                      { title: '楼层名称', dataIndex: 'name' },
                      { title: '排序', dataIndex: 'sortOrder', width: 80 },
                      {
                        title: '状态',
                        dataIndex: 'status',
                        width: 90,
                        render: (value) => <Tag color={value === 'ENABLED' ? 'success' : 'default'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag>,
                      },
                      {
                        title: '操作',
                        key: 'actions',
                        width: 240,
                        render: (_, floor) => (
                          <Space size="small">
                            <Button size="small" onClick={() => openPlan(floor)}>平面图</Button>
                            <Button size="small" onClick={() => openEdit(floor)}>编辑</Button>
                            <Popconfirm title={`确定删除“${floor.name}”吗？`} onConfirm={() => removePlace(floor)}>
                              <Button size="small" danger>删除</Button>
                            </Popconfirm>
                          </Space>
                        ),
                      },
                    ]}
                  />
                </div>
              ),
            },
            {
              key: 'room',
              label: `${roomTypeLabel}（${managementRooms.length}）`,
              children: (
                <div className="facility-room-manager-section">
                  <div className="facility-room-manager-toolbar">
                    <Select
                      value={managementFloorFilter}
                      onChange={setManagementFloorFilter}
                      style={{ width: 160 }}
                      options={[
                        { value: 'ALL', label: '全部楼层' },
                        ...managementFloors.map((floor) => ({ value: floor.id, label: floor.name })),
                      ]}
                    />
                    <Button type="primary" icon={<PlusOutlined />} disabled={!roomCreateParent} onClick={() => openCreate(roomCreateParent)}>
                      新增{roomEntityLabel}
                    </Button>
                  </div>
                  <Table
                    rowKey="id"
                    size="small"
                    pagination={false}
                    dataSource={managementRooms}
                    locale={{ emptyText: managementFloors.length ? `暂无${roomTypeLabel}` : '请先新增楼层' }}
                    columns={[
                      { title: `${roomEntityLabel}名称`, dataIndex: 'name' },
                      { title: '所在楼层', dataIndex: 'floorName', width: 140 },
                      {
                        title: '类型',
                        dataIndex: 'placeType',
                        width: 140,
                        render: (value) => TYPE_LABELS[value] || value,
                      },
                      {
                        title: '状态',
                        dataIndex: 'status',
                        width: 90,
                        render: (value) => <Tag color={value === 'ENABLED' ? 'success' : 'default'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag>,
                      },
                      {
                        title: '操作',
                        key: 'actions',
                        width: 150,
                        render: (_, room) => (
                          <Space size="small">
                            <Button size="small" onClick={() => openEdit(room)}>编辑</Button>
                            <Popconfirm title={`确定删除“${room.name}”吗？`} onConfirm={() => removePlace(room)}>
                              <Button size="small" danger>删除</Button>
                            </Popconfirm>
                          </Space>
                        ),
                      },
                    ]}
                  />
                </div>
              ),
            },
          ]}
        />
      </SidePanel>

      {isBuildingFloorEditor ? (
        <Modal
          className="facility-floor-editor-modal"
          title={editing ? '编辑楼层点位' : '新增楼层点位'}
          open={editorOpen}
          width={720}
          okText="确定"
          cancelText="取消"
          confirmLoading={saving}
          onOk={savePlace}
          onCancel={() => setEditorOpen(false)}
          destroyOnHidden
          forceRender
        >
          <Form form={form} layout="vertical">
            <Form.Item
              name="name"
              label="楼层名称"
              rules={[{ required: true, message: '请输入楼层名称' }]}
            >
              <Input placeholder="例如：1F、地下层" />
            </Form.Item>
            <div className="place-form-grid">
              <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
                <Select options={STATUS_OPTIONS} />
              </Form.Item>
              <Form.Item name="sortOrder" label="排序">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </div>
          </Form>
        </Modal>
      ) : (
        <SidePanel
          title={editorTitle}
          open={editorOpen}
          onClose={() => setEditorOpen(false)}
          destroyOnHidden
          footer={(
            <>
              <Button onClick={() => setEditorOpen(false)}>取消</Button>
              <Button type="primary" loading={saving} onClick={savePlace}>保存</Button>
            </>
          )}
        >
        <Form form={form} layout="vertical">
          {isRoomEditor ? (
            <Form.Item name="name" label="点位名称" rules={[{ required: true, message: '请输入点位名称' }]}>
              <Input />
            </Form.Item>
          ) : null}
          {isSimpleEditor ? (
            <Form.Item name="name" label="点位名称" rules={[{ required: true, message: '请输入点位名称' }]}>
              <Input />
            </Form.Item>
          ) : (
            <div className="place-form-grid">
              {isRoomEditor ? (
                <Form.Item
                  name="parentId"
                  label="所在楼层"
                  rules={[{ required: true, message: '请选择所在楼层' }]}
                >
                  <Select
                    placeholder="请选择已创建的楼层"
                    options={managementFloors.map((floor) => ({
                      value: floor.id,
                      label: floor.name,
                    }))}
                  />
                </Form.Item>
              ) : (
                <Form.Item name="name" label="点位名称" rules={[{ required: true, message: '请输入点位名称' }]}>
                  <Input />
                </Form.Item>
              )}
              <Form.Item name="placeType" label="点位类型" rules={[{ required: true }]}>
                <Select
                  disabled={Boolean(editing) && !isRoomEditor}
                  options={getAllowedChildTypes(sceneType, parent).map((value) => ({
                    value,
                    label: TYPE_LABELS[value] || value,
                  }))}
                />
              </Form.Item>
            </div>
          )}
          <div className="place-form-grid">
            <Form.Item name="status" label="状态">
              <Select options={STATUS_OPTIONS} />
            </Form.Item>
            <Form.Item name="sortOrder" label="排序">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item
            name="description"
            label={isFacilityLevel ? '设施说明' : (isFloorLevel ? '楼层公告' : '描述')}
          >
            <Input.TextArea
              rows={2}
              placeholder={isFacilityLevel
                ? '例如：洗衣机、吹风机等设施的使用说明'
                : (isFloorLevel ? '例如：停水、检修等楼层通知' : undefined)}
            />
          </Form.Item>
          {!isSimpleEditor && isOutdoorLevel ? (
            <>
              <Form.Item name="locationDesc" label="位置说明">
                <Input placeholder="例如：东区体育馆北侧" />
              </Form.Item>
              {!['TEACHING', 'DORMITORY'].includes(sceneType) ? (
                <div className="place-form-grid">
                  <Form.Item name="longitude" label="经度">
                    <InputNumber min={-180} max={180} precision={7} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="latitude" label="纬度">
                    <InputNumber min={-90} max={90} precision={7} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="mapVisible" label="室外地图显示" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </div>
              ) : null}
            </>
          ) : null}
          <Form.Item label="图片">
            <Upload
              listType="picture-card"
              fileList={fileList}
              beforeUpload={uploadFile}
              onPreview={(file) => setPreviewImageUid(file.uid)}
              onRemove={removeImageFile}
            >
              <div><UploadOutlined /><div>上传</div></div>
            </Upload>
            {isSimpleEditor ? (
              <div className="facility-image-preview-tip">
                点击已上传图片，可调整它在食堂卡片中的展示位置。
              </div>
            ) : null}
          </Form.Item>
          {isSimpleEditor && previewImage ? (
            <div className="facility-image-position-editor">
              <div className="facility-image-position-editor__header">
                <div>
                  <strong>卡片展示预览</strong>
                  <span>在图片上点击或拖动，选择希望重点展示的位置</span>
                </div>
                <Button size="small" onClick={resetPreviewFocus}>恢复居中</Button>
              </div>
              <div
                className="facility-image-position-preview"
                role="application"
                aria-label="拖动设置图片展示位置"
                onPointerDown={updatePreviewFocus}
                onPointerMove={updatePreviewFocus}
              >
                <img
                  src={previewImage.url}
                  alt="食堂卡片展示预览"
                  draggable={false}
                  style={{ objectPosition: `${previewImage.focusX ?? 50}% ${previewImage.focusY ?? 50}%` }}
                />
                <span
                  className="facility-image-focus-point"
                  style={{ left: `${previewImage.focusX ?? 50}%`, top: `${previewImage.focusY ?? 50}%` }}
                />
              </div>
              <p>保存后，食堂卡片会按照当前预览位置裁切图片；原图文件不会被修改。</p>
            </div>
          ) : null}
          {!isSimpleEditor && isOutdoorLevel && !['TEACHING', 'DORMITORY'].includes(sceneType) ? (
            <>
              <div className="place-form-grid">
                <Form.Item name="geometryType" label="户外围栏类型">
                  <Select options={[
                    { value: 'POLYGON', label: '区域 Polygon' },
                    { value: 'LINESTRING', label: '路线 LineString' },
                  ]} />
                </Form.Item>
              </div>
              <Form.Item
                name="geometryData"
                label="围栏 GeoJSON"
                extra="可留空；坐标顺序统一为 [经度, 纬度]。"
              >
                <Input.TextArea rows={5} placeholder='{"type":"Polygon","coordinates":[...]}' />
              </Form.Item>
            </>
          ) : null}
        </Form>
        </SidePanel>
      )}

      <Modal
        title={`楼层平面图 · ${planFloor?.name || ''}`}
        open={planOpen}
        centered
        zIndex={1400}
        confirmLoading={saving}
        onCancel={() => {
          setPlanOpen(false)
          setPlanShowPlacements(false)
          setPlanPlacements([])
        }}
        onOk={submitPlan}
        afterClose={() => {
          if (returnToRoomManagerAfterPlan) setRoomManagerOpen(true)
          setReturnToRoomManagerAfterPlan(false)
        }}
        forceRender
      >
        <Form form={planForm} layout="vertical">
          <Form.Item name="imageUrl" label="平面图" rules={[{ required: true, message: '请上传平面图' }]}>
            <Input placeholder="上传后自动填写图片地址" />
          </Form.Item>
          <Space>
            <Upload showUploadList={false} beforeUpload={uploadPlan}>
              <Button icon={<UploadOutlined />}>上传平面图</Button>
            </Upload>
            {isDormitory ? (
              <Button
                icon={<EnvironmentOutlined />}
                loading={placementsLoading}
                disabled={!planImageUrl}
                onClick={() => {
                  if (planShowPlacements) {
                    setPlanShowPlacements(false)
                    return
                  }
                  loadPlanPlacements()
                }}
              >
                {planShowPlacements ? '隐藏点位' : '显示楼层点位'}
              </Button>
            ) : null}
          </Space>
          {planImageUrl ? (
            <div className="floor-plan-markers">
              <Image className="floor-plan-preview" src={planImageUrl} />
              {planShowPlacements && planPlacements.map((placement) => (
                <Tooltip key={placement.placeId} title={placement.name}>
                  <span
                    className="floor-plan-marker"
                    style={{ left: `${placement.xRatio}%`, top: `${placement.yRatio}%` }}
                  />
                </Tooltip>
              ))}
            </div>
          ) : null}
          {plan ? (
            <Popconfirm
              title="确定删除该平面图及其室内定位吗？"
              onConfirm={async () => {
                await deleteFloorPlan(planFloor.id)
                setPlan(null)
                planForm.resetFields()
                message.success('平面图已删除')
              }}
            >
              <Button danger className="plan-delete-button">删除平面图</Button>
            </Popconfirm>
          ) : null}
        </Form>
      </Modal>

      <Modal
        title={`室内定位 · ${positionPlace?.name || ''}`}
        open={positionOpen}
        confirmLoading={saving}
        onCancel={() => setPositionOpen(false)}
        onOk={submitPosition}
        forceRender
      >
        <Form form={positionForm} layout="vertical">
          {positionPlan?.imageUrl ? (
            <div
              className="indoor-position-preview"
              onClick={(event) => {
                const rect = event.currentTarget.getBoundingClientRect()
                positionForm.setFieldsValue({
                  xRatio: Number((((event.clientX - rect.left) / rect.width) * 100).toFixed(4)),
                  yRatio: Number((((event.clientY - rect.top) / rect.height) * 100).toFixed(4)),
                })
              }}
            >
              <Image preview={false} src={positionPlan.imageUrl} />
              {positionX != null && positionY != null ? (
                <span
                  className="indoor-position-dot"
                  style={{
                    left: `${positionX}%`,
                    top: `${positionY}%`,
                  }}
                />
              ) : null}
            </div>
          ) : null}
          <p className="indoor-position-tip">点击平面图选取位置，也可以手动输入百分比坐标。</p>
          <div className="place-form-grid">
            <Form.Item name="xRatio" label="横向位置（%）" rules={[{ required: true }]}>
              <InputNumber min={0} max={100} precision={4} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="yRatio" label="纵向位置（%）" rules={[{ required: true }]}>
              <InputNumber min={0} max={100} precision={4} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="positionId" hidden><Input /></Form.Item>
          {positionId ? (
            <Popconfirm
              title="确定删除室内定位吗？"
              onConfirm={async () => {
                await deleteIndoorPosition(positionId)
                setPositionOpen(false)
                message.success('室内定位已删除')
              }}
            >
              <Button danger>删除室内定位</Button>
            </Popconfirm>
          ) : null}
        </Form>
      </Modal>
    </div>
  )
}
