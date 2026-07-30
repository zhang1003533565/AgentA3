import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
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
  Tag,
  Upload,
  message,
} from 'antd'
import {
  ApartmentOutlined,
  DeleteOutlined,
  EditOutlined,
  EnvironmentOutlined,
  FileImageOutlined,
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
    rootType: 'CANTEEN',
  },
  SPORTS: {
    title: '运动场管理',
    description: '统一管理运动场及跑道、球场等下级场地。',
    rootType: 'SPORTS_GROUND',
  },
  TEACHING: {
    title: '教学楼管理',
    description: '统一管理教学楼、楼层、教室、实验室和办公室。',
    rootType: 'TEACHING_BUILDING',
  },
  DORMITORY: {
    title: '宿舍管理',
    description: '统一管理宿舍楼、楼层和宿舍房间。',
    rootType: 'DORMITORY_BUILDING',
  },
}

const TYPE_LABELS = {
  CANTEEN: '食堂',
  SPORTS_GROUND: '运动场',
  TEACHING_BUILDING: '教学楼',
  DORMITORY_BUILDING: '宿舍楼',
  FLOOR: '楼层',
  CANTEEN_STALL: '食堂档口',
  DINING_AREA: '就餐区域',
  CLASSROOM: '教室',
  LABORATORY: '实验室',
  OFFICE: '办公室',
  DORMITORY_ROOM: '宿舍房间',
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
  DORMITORY: ['DORMITORY_ROOM'],
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
  if (!parent) return [SCENE_CONFIG[sceneType].rootType]
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

function CanteenCarousel({ images = [] }) {
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
          alt="食堂"
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

export default function FacilityPlaceManage({ sceneType, rootPlaceId = null }) {
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
  const [planFloor, setPlanFloor] = useState(null)
  const [plan, setPlan] = useState(null)
  const [positionOpen, setPositionOpen] = useState(false)
  const [positionPlace, setPositionPlace] = useState(null)
  const [positionPlan, setPositionPlan] = useState(null)
  const [keyword, setKeyword] = useState('')
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
      if (rootPlaceId) {
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
          getMapPlaceDetail(rootPlaceId),
          loadChildren(rootPlaceId),
        ])
        setRootPlace(rootResponse.data || null)
        setTree(children)
        return
      }
      if (sceneType === 'CANTEEN') {
        const response = await getMapPlaceList({ sceneType, placeType: config.rootType })
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
  }, [config.rootType, rootPlaceId, sceneType])

  useEffect(() => {
    loadTree()
  }, [loadTree])

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
    setSaving(true)
    try {
      const payload = {
        parentId: editing ? (editing.parentId ?? null) : (parent?.id ?? null),
        sceneType,
        placeType: values.placeType || editing?.placeType || getAllowedChildTypes(sceneType, parent)[0],
        name: values.name,
        description: values.description || '',
        status: values.status,
        longitude: usesSeparateLocationPanel ? (editing?.longitude ?? null) : (values.longitude ?? null),
        latitude: usesSeparateLocationPanel ? (editing?.latitude ?? null) : (values.latitude ?? null),
        locationDesc: usesSeparateLocationPanel ? (editing?.locationDesc || '') : (values.locationDesc || ''),
        mapVisible: usesSeparateLocationPanel
          ? (editing ? Boolean(editing.mapVisible) : true)
          : Boolean(values.mapVisible),
        sortOrder: values.sortOrder || 0,
      }
      const response = editing
        ? await updateMapPlace(editing.id, payload)
        : await createMapPlace(payload)
      const placeId = response.data.id
      await syncImages(placeId)
      if (!usesSeparateLocationPanel && values.geometryData?.trim()) {
        await saveMapPlaceFence(placeId, {
          geometryType: values.geometryType,
          geometryData: values.geometryData.trim(),
        })
      } else if (!usesSeparateLocationPanel && editing?.fence) {
        await deleteMapPlaceFence(placeId)
      }
      message.success(editing ? '点位已更新' : '点位已创建')
      setEditorOpen(false)
      await loadTree()
    } finally {
      setSaving(false)
    }
  }

  const removePlace = async (record) => {
    await deleteMapPlace(record.id)
    message.success('点位已删除')
    await loadTree()
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
    setPlanFloor(floor)
    setSaving(true)
    try {
      const response = await getFloorPlan(floor.id)
      setPlan(response.data || null)
      planForm.setFieldsValue({ imageUrl: response.data?.imageUrl || '' })
      setPlanOpen(true)
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
    } finally {
      setSaving(false)
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

  const pageTitle = rootPlace ? `${rootPlace.name} · 档口管理` : config.title
  const pageDescription = rootPlace
    ? '进入食堂后单独加载并管理楼层、档口和就餐区域。'
    : sceneType === 'CANTEEN'
      ? '这里只展示顶级食堂；点击“进入档口”后再加载下级点位。'
      : config.description
  const createParent = rootPlace || null
  const createLabel = rootPlace
    ? '新增楼层'
    : sceneType === 'CANTEEN'
      ? '新增食堂'
      : '新增顶级设施'
  const isCanteenOverview = sceneType === 'CANTEEN' && !rootPlaceId
  const canteenCounts = {
    ALL: tree.length,
    ENABLED: tree.filter((item) => item.status === 'ENABLED').length,
    DISABLED: tree.filter((item) => item.status === 'DISABLED').length,
  }

  return (
    <div className="facility-place-page">
      <div className="facility-place-toolbar">
        <div>
          {rootPlace ? (
            <Button
              type="link"
              className="facility-place-back"
              onClick={() => navigate('/facility/canteen')}
            >
              ← 返回食堂列表
            </Button>
          ) : null}
          <h1>{pageTitle}</h1>
          <p>{pageDescription}</p>
        </div>
        <Space>
          <Input.Search
            allowClear
            placeholder="搜索点位"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate(createParent)}>
            {createLabel}
          </Button>
        </Space>
      </div>

      {isCanteenOverview ? (
        <>
          <div className="facility-canteen-stats">
            {[
              ['ALL', '全部食堂'],
              ['ENABLED', '启用'],
              ['DISABLED', '停用'],
            ].map(([value, label]) => (
              <button
                key={value}
                type="button"
                className={statusFilter === value ? 'active' : ''}
                onClick={() => setStatusFilter(value)}
              >
                <span>{canteenCounts[value]}</span>
                {label}
              </button>
            ))}
          </div>
          {filteredTree.length ? (
            <div className="facility-canteen-grid">
              {filteredTree.map((canteen) => {
                const hasLocation = canteen.longitude != null && canteen.latitude != null
                return (
                  <Card
                    key={canteen.id}
                    className="facility-canteen-card"
                    styles={{ body: { padding: 0 } }}
                  >
                    <div className="facility-canteen-card-image">
                      <CanteenCarousel images={canteen.images} />
                    </div>
                    <div className="facility-canteen-card-info">
                      <h2>{canteen.name}</h2>
                      <p>{canteen.description || '暂无食堂介绍'}</p>
                      <div className="facility-canteen-tags">
                        <Tag color={canteen.status === 'ENABLED' ? 'success' : 'default'}>
                          {canteen.status === 'ENABLED' ? '启用' : '停用'}
                        </Tag>
                      </div>
                      <div className={`facility-canteen-location${hasLocation ? ' is-set' : ' is-unset'}`}>
                        <EnvironmentOutlined />
                        <div>
                          <strong>{hasLocation ? '位置已设置' : '位置尚未设置'}</strong>
                          <span>
                            {hasLocation
                              ? (canteen.locationDesc || '已在标点管理中设置')
                              : '请点击“位置管理”补充食堂所在位置'}
                          </span>
                        </div>
                      </div>
                    </div>
                    <div className="facility-canteen-actions">
                      <Button
                        type="primary"
                        icon={<ShopOutlined />}
                        onClick={() => navigate(`/facility/canteen/${canteen.id}/stalls`)}
                      >
                        进入档口
                      </Button>
                      <Button
                        icon={<EnvironmentOutlined />}
                        onClick={() => navigate(`/facility/marker?mapPlaceId=${canteen.id}`)}
                      >
                        位置管理
                      </Button>
                      <Button icon={<EditOutlined />} onClick={() => openEdit(canteen)}>
                        编辑
                      </Button>
                      <Popconfirm
                        title={`确定删除“${canteen.name}”吗？`}
                        description="存在下级点位时不能删除。"
                        onConfirm={() => removePlace(canteen)}
                      >
                        <Button danger icon={<DeleteOutlined />} aria-label={`删除${canteen.name}`} />
                      </Popconfirm>
                    </div>
                  </Card>
                )
              })}
            </div>
          ) : (
            <Card className="facility-place-card">
              <Empty description={loading ? '正在加载食堂' : '暂无符合条件的食堂'} />
            </Card>
          )}
        </>
      ) : (
        <Card className="facility-place-card">
          <Table
            rowKey="id"
            columns={columns}
            dataSource={filteredTree}
            loading={loading}
            pagination={false}
            expandable={rootPlaceId ? { defaultExpandAllRows: true } : undefined}
            locale={{ emptyText: rootPlaceId ? '该食堂暂无楼层或档口' : '暂无设施点位' }}
            scroll={{ x: 1080 }}
          />
        </Card>
      )}

      <SidePanel
        title={editing ? '编辑点位' : parent ? `在“${parent.name}”下新增` : '新增顶级设施'}
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
          {isCanteenOverview ? (
            <Form.Item name="name" label="点位名称" rules={[{ required: true, message: '请输入点位名称' }]}>
              <Input />
            </Form.Item>
          ) : (
            <div className="place-form-grid">
              <Form.Item name="name" label="点位名称" rules={[{ required: true, message: '请输入点位名称' }]}>
                <Input />
              </Form.Item>
              <Form.Item name="placeType" label="点位类型" rules={[{ required: true }]}>
                <Select
                  disabled={Boolean(editing)}
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
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          {!isCanteenOverview ? (
            <>
              <Form.Item name="locationDesc" label="位置说明">
                <Input placeholder="例如：东区体育馆北侧" />
              </Form.Item>
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
            {isCanteenOverview ? (
              <div className="facility-image-preview-tip">
                点击已上传图片，可调整它在食堂卡片中的展示位置。
              </div>
            ) : null}
          </Form.Item>
          {isCanteenOverview && previewImage ? (
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
          {!isCanteenOverview ? (
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

      <Modal
        title={`楼层平面图 · ${planFloor?.name || ''}`}
        open={planOpen}
        confirmLoading={saving}
        onCancel={() => setPlanOpen(false)}
        onOk={submitPlan}
        forceRender
      >
        <Form form={planForm} layout="vertical">
          <Form.Item name="imageUrl" label="平面图" rules={[{ required: true, message: '请上传平面图' }]}>
            <Input placeholder="上传后自动填写图片地址" />
          </Form.Item>
          <Upload showUploadList={false} beforeUpload={uploadPlan}>
            <Button icon={<UploadOutlined />}>上传平面图</Button>
          </Upload>
          {planImageUrl ? (
            <Image className="floor-plan-preview" src={planImageUrl} />
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
