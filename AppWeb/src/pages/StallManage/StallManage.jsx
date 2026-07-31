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
  Table,
  Tag,
  Tabs,
  Upload,
  message,
} from 'antd'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SettingOutlined,
  ShopOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import {
  createDishCuisine,
  createDish,
  deleteDishCuisine,
  deleteDish,
  getDishCuisines,
  getDishList,
  updateDishCuisine,
  updateDish,
} from '../../api/dish'
import {
  createMapPlace,
  deleteMapPlace,
  getMapPlaceDetail,
  getMapPlaceList,
  updateMapPlace,
} from '../../api/mapPlace'
import SidePanel from '../../components/SidePanel/SidePanel'
import {
  CANTEEN_STALL_UPLOAD_FOLDER,
  DISH_UPLOAD_FOLDER,
  uploadImage,
} from '../../api/upload'
import './StallManage.css'

const STALL_STATUS_OPTIONS = [
  { value: 1, label: '营业中' },
  { value: 2, label: '休息中' },
  { value: 3, label: '已关闭' },
]

const DISH_STATUS_OPTIONS = [
  { value: true, label: '上架' },
  { value: false, label: '下架' },
]

const getRows = (response) => {
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.data?.records)) return response.data.records
  if (Array.isArray(response?.data?.list)) return response.data.list
  return []
}

const renderStallStatus = (status) => {
  const option = STALL_STATUS_OPTIONS.find((item) => item.value === Number(status))
  const color = Number(status) === 1 ? 'success' : Number(status) === 2 ? 'warning' : 'default'
  return <Tag color={color}>{option?.label || '未知'}</Tag>
}

export default function StallManage() {
  const { canteenId, stallId } = useParams()
  const navigate = useNavigate()
  const dishMode = Boolean(stallId)
  const [stallForm] = Form.useForm()
  const [dishForm] = Form.useForm()
  const [categoryForm] = Form.useForm()
  const [canteen, setCanteen] = useState(null)
  const [stalls, setStalls] = useState([])
  const [dishes, setDishes] = useState([])
  const [selectedStallId, setSelectedStallId] = useState(stallId || null)
  const [stallKeyword, setStallKeyword] = useState('')
  const [dishKeyword, setDishKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [dishLoading, setDishLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [stallImageUploading, setStallImageUploading] = useState(false)
  const [dishImageUploading, setDishImageUploading] = useState(false)
  const [stallEditorOpen, setStallEditorOpen] = useState(false)
  const [dishEditorOpen, setDishEditorOpen] = useState(false)
  const [editingStall, setEditingStall] = useState(null)
  const [editingDish, setEditingDish] = useState(null)
  const [floors, setFloors] = useState([])
  const [cuisines, setCuisines] = useState([])
  const [categoryManagerOpen, setCategoryManagerOpen] = useState(false)
  const [categoryEditorOpen, setCategoryEditorOpen] = useState(false)
  const [categoryKind, setCategoryKind] = useState('floor')
  const [editingCategory, setEditingCategory] = useState(null)
  const stallImage = Form.useWatch('imageUrl', stallForm)
  const dishImage = Form.useWatch('imageUrl', dishForm)

  const selectedStall = useMemo(
    () => stalls.find((item) => String(item.id) === String(selectedStallId)) || null,
    [selectedStallId, stalls],
  )

  const filteredStalls = useMemo(() => {
    const keyword = stallKeyword.trim().toLowerCase()
    if (!keyword) return stalls
    return stalls.filter((item) =>
      [item.name, item.floorName, item.locationDesc]
        .some((value) => String(value || '').toLowerCase().includes(keyword)),
    )
  }, [stallKeyword, stalls])

  const filteredDishes = useMemo(() => {
    const keyword = dishKeyword.trim().toLowerCase()
    if (!keyword) return dishes
    return dishes.filter((item) =>
      [item.name, item.category, item.taste]
        .some((value) => String(value || '').toLowerCase().includes(keyword)),
    )
  }, [dishKeyword, dishes])

  const cuisineOptions = useMemo(
    () => cuisines.map((item) => ({
      value: item.id,
      label: item.cuisineName,
      disabled: Number(item.status) !== 1,
    })),
    [cuisines],
  )

  const floorOptions = useMemo(
    () => floors.map((item) => ({
      value: item.id,
      label: item.name,
      disabled: item.status !== 'ENABLED',
    })),
    [floors],
  )

  const loadCategories = useCallback(async () => {
    const [floorResponse, cuisineResponse] = await Promise.all([
      getMapPlaceList({ sceneType: 'CANTEEN', parentId: canteenId }),
      getDishCuisines(canteenId),
    ])
    setFloors(getRows(floorResponse).filter((item) => item.placeType === 'FLOOR'))
    setCuisines(getRows(cuisineResponse))
  }, [canteenId])

  const loadStalls = useCallback(async (preferredStallId = null) => {
    setLoading(true)
    try {
      const mapResponse = await getMapPlaceDetail(canteenId)
      const mapPlace = mapResponse.data || null
      setCanteen(mapPlace)
      const floorResponse = await getMapPlaceList({ sceneType: 'CANTEEN', parentId: canteenId })
      const floorRows = getRows(floorResponse).filter((item) => item.placeType === 'FLOOR')
      setFloors(floorRows)
      const childResponses = await Promise.all(
        floorRows.map((floor) => getMapPlaceList({ sceneType: 'CANTEEN', parentId: floor.id })),
      )
      const directResponse = await getMapPlaceList({ sceneType: 'CANTEEN', parentId: canteenId })
      const rows = [
        ...getRows(directResponse),
        ...childResponses.flatMap(getRows),
      ].filter((item) => item.placeType === 'CANTEEN_STALL')
        .map((item) => ({
          ...item,
          floorName: floorRows.find((floor) => String(floor.id) === String(item.parentId))?.name || '-',
          stallStatus: item.stallStatus ?? (item.status === 'ENABLED' ? 1 : 3),
        }))
        .sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0))
      const cuisineResponse = await getDishCuisines(canteenId)
      setCuisines(getRows(cuisineResponse))
      setStalls(rows)
      setSelectedStallId((current) => {
        if (!dishMode) return null
        const expected = preferredStallId ?? stallId ?? current
        if (rows.some((item) => String(item.id) === String(expected))) return expected
        return null
      })
    } catch (error) {
      if (!error?.showMessage) {
        message.error(error?.message || '档口数据加载失败')
      }
      setStalls([])
      setSelectedStallId(null)
    } finally {
      setLoading(false)
    }
  }, [canteenId, dishMode, stallId])

  const loadDishes = useCallback(async () => {
    if (!dishMode || !selectedStallId) {
      setDishes([])
      return
    }
    setDishLoading(true)
    try {
      const response = await getDishList({ stallPlaceId: selectedStallId })
      setDishes(getRows(response))
    } finally {
      setDishLoading(false)
    }
  }, [dishMode, selectedStallId])

  useEffect(() => {
    loadStalls()
  }, [loadStalls])

  useEffect(() => {
    loadDishes()
  }, [loadDishes])

  const uploadManagedImage = async (file, form, setUploading, folder, label) => {
    if (!file.type?.startsWith('image/')) {
      message.warning('请选择图片文件')
      return false
    }
    if (file.size > 10 * 1024 * 1024) {
      message.warning('单张图片不能超过 10MB')
      return false
    }

    setUploading(true)
    try {
      const url = await uploadImage(file, folder)
      form.setFieldValue('imageUrl', url)
      message.success(`${label}上传成功`)
    } catch (error) {
      message.error(error?.message || `${label}上传失败`)
    } finally {
      setUploading(false)
    }
    return false
  }

  const uploadStallImage = (file) =>
    uploadManagedImage(
      file,
      stallForm,
      setStallImageUploading,
      CANTEEN_STALL_UPLOAD_FOLDER,
      '档口图片',
    )

  const uploadDishImage = (file) =>
    uploadManagedImage(
      file,
      dishForm,
      setDishImageUploading,
      DISH_UPLOAD_FOLDER,
      '菜品图片',
    )

  const openCreateStall = () => {
    setEditingStall(null)
    stallForm.resetFields()
    stallForm.setFieldsValue({
      stallStatus: 1,
      sortOrder: stalls.length,
    })
    setStallEditorOpen(true)
  }

  const openEditStall = (record) => {
    setEditingStall(record)
    stallForm.resetFields()
    stallForm.setFieldsValue({
      ...record,
      floorId: record.parentId,
      location: record.locationDesc,
    })
    setStallEditorOpen(true)
  }

  const saveStall = async () => {
    const values = await stallForm.validateFields()
    setSaving(true)
    try {
      const payload = {
        parentId: values.floorId,
        sceneType: 'CANTEEN',
        placeType: 'CANTEEN_STALL',
        name: values.name,
        description: values.description || '',
        status: Number(values.stallStatus) === 3 ? 'DISABLED' : 'ENABLED',
        locationDesc: values.location || '',
        mapVisible: false,
        sortOrder: values.sortOrder || 0,
        stallStatus: values.stallStatus,
        businessHours: values.businessHours || '',
        avgPrice: values.avgPrice,
        imageUrl: values.imageUrl || '',
      }
      const response = editingStall
        ? await updateMapPlace(editingStall.id, payload)
        : await createMapPlace(payload)
      const savedId = response.data?.id || editingStall?.id
      message.success(editingStall ? '档口已更新' : '档口已新增')
      setStallEditorOpen(false)
      await loadStalls(savedId)
    } finally {
      setSaving(false)
    }
  }

  const removeStall = async (record) => {
    await deleteMapPlace(record.id)
    message.success('档口已删除')
    await loadStalls()
  }

  const openCreateDish = () => {
    if (!selectedStall) return
    setEditingDish(null)
    dishForm.resetFields()
    dishForm.setFieldsValue({
      stallPlaceId: selectedStall.id,
      isAvailable: true,
    })
    setDishEditorOpen(true)
  }

  const openEditDish = (record) => {
    setEditingDish(record)
    dishForm.resetFields()
    dishForm.setFieldsValue({
      ...record,
      cuisineId: record.cuisineId || cuisines.find((item) => item.cuisineName === record.category)?.id,
    })
    setDishEditorOpen(true)
  }

  const saveDish = async () => {
    const values = await dishForm.validateFields()
    setSaving(true)
    try {
      const payload = { ...editingDish, ...values, stallId: null, stallPlaceId: selectedStall.id }
      if (editingDish) await updateDish(editingDish.id, payload)
      else await createDish(payload)
      message.success(editingDish ? '菜品已更新' : '菜品已新增')
      setDishEditorOpen(false)
      await loadDishes()
    } finally {
      setSaving(false)
    }
  }

  const removeDish = async (record) => {
    await deleteDish(record.id)
    message.success('菜品已删除')
    await loadDishes()
  }

  const openCategoryEditor = (kind, record = null) => {
    setCategoryKind(kind)
    setEditingCategory(record)
    categoryForm.resetFields()
    categoryForm.setFieldsValue({
      name: kind === 'floor' ? record?.name : record?.cuisineName,
      status: kind === 'floor' ? (record?.status === 'DISABLED' ? 0 : 1) : (record?.status ?? 1),
      sortOrder: record?.sortOrder ?? 0,
    })
    setCategoryEditorOpen(true)
  }

  const saveCategory = async () => {
    const values = await categoryForm.validateFields()
    const payload = categoryKind === 'floor'
      ? {
          parentId: Number(canteenId),
          sceneType: 'CANTEEN',
          placeType: 'FLOOR',
          name: values.name,
          status: Number(values.status) === 1 ? 'ENABLED' : 'DISABLED',
          mapVisible: false,
          sortOrder: values.sortOrder || 0,
        }
      : { ...values, canteenPlaceId: Number(canteenId) }
    setSaving(true)
    try {
      const response = categoryKind === 'floor'
        ? editingCategory
          ? await updateMapPlace(editingCategory.id, payload)
          : await createMapPlace(payload)
        : editingCategory
          ? await updateDishCuisine(editingCategory.id, payload)
          : await createDishCuisine(payload)
      message.success(editingCategory ? '分类已更新' : '分类已新增')
      setCategoryEditorOpen(false)
      await loadCategories()
      if (!editingCategory) {
        if (categoryKind === 'floor' && stallEditorOpen) stallForm.setFieldValue('floorId', response.data?.id)
        if (categoryKind === 'cuisine' && dishEditorOpen) dishForm.setFieldValue('cuisineId', response.data?.id)
      }
    } finally {
      setSaving(false)
    }
  }

  const removeCategory = async (kind, record) => {
    if (kind === 'floor') await deleteMapPlace(record.id)
    else await deleteDishCuisine(record.id)
    message.success('分类已删除')
    await loadCategories()
  }

  /* The former table columns are kept here temporarily for reference while the
     card layout settles.
  const stallColumns = [
    {
      title: '档口名称',
      dataIndex: 'name',
      width: 210,
      render: (value, record) => (
        <button
          type="button"
          className="stall-name-button"
          onClick={() => setSelectedStallId(record.id)}
        >
          <ShopOutlined />
          <span>{value}</span>
        </button>
      ),
    },
    {
      title: '档口位置',
      key: 'location',
      render: (_, record) => [record.floorName, record.locationDesc].filter(Boolean).join(' · ') || '-',
    },
    {
      title: '营业时间',
      dataIndex: 'businessHours',
      width: 150,
      render: (value) => value || '-',
    },
    {
      title: '人均',
      dataIndex: 'avgPrice',
      width: 90,
      render: (value) => value == null ? '-' : `¥${Number(value).toFixed(2)}`,
    },
    {
      title: '状态',
      dataIndex: 'stallStatus',
      width: 100,
      render: renderStallStatus,
    },
    {
      title: '操作',
      key: 'actions',
      width: 250,
      render: (_, record) => (
        <Space size="small">
          <Button
            type={String(record.id) === String(selectedStallId) ? 'primary' : 'default'}
            ghost={String(record.id) === String(selectedStallId)}
            size="small"
            onClick={() => setSelectedStallId(record.id)}
          >
            管理菜品
          </Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEditStall(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确定删除该档口吗？"
            description="请先确认该档口下没有需要保留的菜品。"
            onConfirm={() => removeStall(record)}
          >
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  */
  const dishColumns = [
    {
      title: '菜品',
      dataIndex: 'name',
      width: 220,
      render: (value, record) => (
        <Space>
          {record.imageUrl ? (
            <Image
              src={record.imageUrl}
              width={42}
              height={42}
              preview={false}
              className="dish-table-image"
            />
          ) : <span className="dish-table-image-placeholder" />}
          <span className="dish-name">{value}</span>
        </Space>
      ),
    },
    {
      title: '菜系',
      dataIndex: 'category',
      width: 130,
      render: (value) => value ? <Tag>{value}</Tag> : '-',
    },
    {
      title: '口味',
      dataIndex: 'taste',
      width: 110,
      render: (value) => value || '-',
    },
    {
      title: '价格',
      dataIndex: 'price',
      width: 100,
      render: (value) => `¥${Number(value || 0).toFixed(2)}`,
    },
    {
      title: '状态',
      dataIndex: 'isAvailable',
      width: 100,
      render: (value) => <Tag color={value ? 'success' : 'default'}>{value ? '上架' : '下架'}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 170,
      render: (_, record) => (
        <Space size="small">
          <Button size="small" icon={<EditOutlined />} onClick={() => openEditDish(record)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该菜品吗？" onConfirm={() => removeDish(record)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const renderCategoryTable = (kind) => {
    const isFloor = kind === 'floor'
    const rows = isFloor ? floors : cuisines
    return (
      <div className="category-manager-section">
        <div className="category-manager-toolbar">
          <p>{isFloor ? '楼层是食堂的下级点位，档口绑定到具体楼层。' : '菜系由菜品引用，同一档口可拥有多种菜系。'}</p>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openCategoryEditor(kind)}>
            {isFloor ? '新增楼层' : '新增菜系'}
          </Button>
        </div>
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={rows}
          locale={{ emptyText: isFloor ? '暂未配置楼层' : '暂未配置菜系' }}
          columns={[
            {
              title: isFloor ? '楼层名称' : '菜系名称',
              dataIndex: isFloor ? 'name' : 'cuisineName',
            },
            {
              title: '排序',
              dataIndex: 'sortOrder',
              width: 80,
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 90,
              render: (value) => {
                const enabled = isFloor ? value === 'ENABLED' : Number(value) === 1
                return (
                <Tag color={enabled ? 'success' : 'default'}>
                  {enabled ? '启用' : '停用'}
                </Tag>
                )
              },
            },
            {
              title: '操作',
              key: 'actions',
              width: 150,
              render: (_, record) => (
                <Space size="small">
                  <Button size="small" onClick={() => openCategoryEditor(kind, record)}>编辑</Button>
                  <Popconfirm
                    title={`确定删除该${isFloor ? '楼层' : '菜系'}吗？`}
                    description={isFloor
                      ? '楼层下存在档口时不能删除，可以改为停用。'
                      : '已被菜品使用时不能删除，可以改为停用。'}
                    onConfirm={() => removeCategory(kind, record)}
                  >
                    <Button size="small" danger>删除</Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </div>
    )
  }

  return (
    <div className="stall-page">
      <div className="stall-page-header">
        <div>
          <Button
            type="link"
            icon={<ArrowLeftOutlined />}
            className="stall-back-button"
            onClick={() => navigate(
              dishMode
                ? `/facility/canteen/${canteenId}/stalls`
                : '/facility/canteen',
            )}
          >
            {dishMode ? '返回档口列表' : '返回食堂列表'}
          </Button>
          <h1>
            {canteen?.name || '食堂'} · {dishMode ? `${selectedStall?.name || '档口'}菜品管理` : '档口管理'}
          </h1>
          <p>
            {dishMode
              ? '菜品归属当前档口，可在这里维护菜系、价格、口味和上下架状态。'
              : '档口以卡片列表展示，进入指定档口后再管理其菜品。'}
          </p>
        </div>
        <Space>
          <Button
            icon={<SettingOutlined />}
            onClick={() => {
              setCategoryManagerOpen(true)
              loadCategories()
            }}
          >
            {dishMode ? '菜系管理' : '楼层与菜系'}
          </Button>
          {dishMode ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateDish} disabled={!selectedStall}>
              新增菜品
            </Button>
          ) : (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateStall}>
              新增档口
            </Button>
          )}
        </Space>
      </div>

      {!dishMode && (
      <Card className="stall-section-card" loading={loading}>
        <div className="stall-section-heading">
          <div>
            <h2>档口列表</h2>
            <p>档口本身就是食堂下的点位，维护其楼层、位置和营业信息。</p>
          </div>
          <Input.Search
            allowClear
            placeholder="搜索档口、楼层或位置"
            value={stallKeyword}
            onChange={(event) => setStallKeyword(event.target.value)}
            className="stall-search"
          />
        </div>
        <div className="stall-card-grid">
          {filteredStalls.map((record) => (
            <Card
              key={record.id}
              className="stall-list-card"
              cover={record.imageUrl ? (
                <Image
                  src={record.imageUrl}
                  height={150}
                  preview={false}
                  className="stall-card-image"
                />
              ) : (
                <div className="stall-card-placeholder">
                  <ShopOutlined />
                </div>
              )}
            >
              <div className="stall-card-title-row">
                <h3>{record.name}</h3>
                {renderStallStatus(record.stallStatus)}
              </div>
              <div className="stall-card-meta">
                <span>位置</span>
                <strong>{[record.floorName, record.locationDesc].filter(Boolean).join(' · ') || '-'}</strong>
              </div>
              <div className="stall-card-meta">
                <span>营业时间</span>
                <strong>{record.businessHours || '-'}</strong>
              </div>
              <div className="stall-card-meta">
                <span>人均</span>
                <strong>{record.avgPrice == null ? '-' : `¥${Number(record.avgPrice).toFixed(2)}`}</strong>
              </div>
              <div className="stall-card-actions">
                <Button
                  type="primary"
                  onClick={() => navigate(`/facility/canteen/${canteenId}/stalls/${record.id}/dishes`)}
                >
                  管理菜品
                </Button>
                <Button icon={<EditOutlined />} onClick={() => openEditStall(record)}>
                  编辑
                </Button>
                <Popconfirm
                  title="确定删除该档口吗？"
                  description="请先确认该档口下没有需要保留的菜品。"
                  onConfirm={() => removeStall(record)}
                >
                  <Button danger icon={<DeleteOutlined />}>删除</Button>
                </Popconfirm>
              </div>
            </Card>
          ))}
        </div>
        {!loading && filteredStalls.length === 0 ? (
          <Empty description="该食堂暂无档口" className="stall-empty" />
        ) : null}
        {/* The stall table was replaced by the card grid.
        <Table
          rowKey="id"
          columns={stallColumns}
          dataSource={filteredStalls}
          loading={loading}
          pagination={false}
          rowClassName={(record) =>
            String(record.id) === String(selectedStallId) ? 'stall-selected-row' : ''
          }
          onRow={(record) => ({ onClick: () => setSelectedStallId(record.id) })}
          locale={{ emptyText: '该食堂暂无档口' }}
          scroll={{ x: 1080 }}
        /> */}
      </Card>
      )}

      {dishMode && (
      <Card className="stall-section-card dish-section-card">
        <div className="stall-section-heading">
          <div>
            <h2>菜品管理</h2>
            <p>
              {selectedStall
                ? `当前档口：${selectedStall.name} · ${[selectedStall.floorName, selectedStall.locationDesc].filter(Boolean).join(' / ') || '未设置位置'}`
                : '请先在上方选择一个档口'}
            </p>
          </div>
          <Space>
            <Input.Search
              allowClear
              placeholder="搜索菜品、分类或口味"
              value={dishKeyword}
              onChange={(event) => setDishKeyword(event.target.value)}
              className="dish-search"
              disabled={!selectedStall}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateDish} disabled={!selectedStall}>
              新增菜品
            </Button>
          </Space>
        </div>
        {selectedStall ? (
          <Table
            rowKey="id"
            columns={dishColumns}
            dataSource={filteredDishes}
            loading={dishLoading}
            pagination={false}
            locale={{ emptyText: '该档口暂无菜品' }}
            scroll={{ x: 860 }}
          />
        ) : (
          <Empty description="选择档口后在这里管理菜品" className="stall-empty" />
        )}
      </Card>
      )}

      <SidePanel
        title={`${canteen?.name || '校园设施'} · 楼层与菜系`}
        open={categoryManagerOpen}
        onClose={() => setCategoryManagerOpen(false)}
        width={760}
        destroyOnHidden
      >
        <div className="category-manager-intro">
          楼层和档口均复用设施点位层级；菜系属于菜品，同一档口的不同菜品可以选择不同菜系。
        </div>
        <Tabs
          items={[
            {
              key: 'floor',
              label: `楼层点位（${floors.length}）`,
              children: renderCategoryTable('floor'),
            },
            {
              key: 'cuisine',
              label: `菜品菜系（${cuisines.length}）`,
              children: renderCategoryTable('cuisine'),
            },
          ]}
        />
      </SidePanel>

      <SidePanel
        title={editingStall ? '编辑档口' : '新增档口'}
        open={stallEditorOpen}
        onClose={() => setStallEditorOpen(false)}
        destroyOnHidden
        footer={(
          <>
            <Button onClick={() => setStallEditorOpen(false)}>取消</Button>
            <Button
              type="primary"
              loading={saving || stallImageUploading}
              disabled={stallImageUploading}
              onClick={saveStall}
            >
              保存
            </Button>
          </>
        )}
      >
        <Form form={stallForm} layout="vertical">
          <Form.Item name="name" label="档口名称" rules={[{ required: true, message: '请输入档口名称' }]}>
            <Input placeholder="例如：兰州拉面" />
          </Form.Item>
          <div className="stall-form-grid">
            <Form.Item name="stallStatus" label="营业状态" rules={[{ required: true }]}>
              <Select options={STALL_STATUS_OPTIONS} />
            </Form.Item>
            <Form.Item
              name="floorId"
              label="所在楼层"
              rules={[{ required: true, message: '请选择所在楼层' }]}
              extra={<Button type="link" size="small" onClick={() => setCategoryManagerOpen(true)}>管理设施楼层</Button>}
            >
              <Select
                allowClear
                options={floorOptions}
                placeholder="请选择已配置的楼层"
                notFoundContent="请先在“楼层与菜系”中新增"
              />
            </Form.Item>
            <Form.Item name="location" label="档口位置">
              <Input placeholder="例如：一层东侧 06 号" />
            </Form.Item>
          </div>
          <div className="stall-form-grid">
            <Form.Item name="businessHours" label="营业时间">
              <Input placeholder="例如：06:30-20:30" />
            </Form.Item>
            <Form.Item name="avgPrice" label="人均价格">
              <InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="description" label="档口介绍">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="imageUrl" label="档口图片地址">
            <Input placeholder="请输入图片 URL" />
          </Form.Item>
          <div className="stall-image-upload-actions">
            <Upload
              accept="image/jpeg,image/png,image/webp,image/gif"
              showUploadList={false}
              beforeUpload={uploadStallImage}
              disabled={stallImageUploading}
            >
              <Button icon={<UploadOutlined />} loading={stallImageUploading}>
                {stallImage ? '替换档口图片' : '上传档口图片'}
              </Button>
            </Upload>
            {stallImage ? (
              <Button danger onClick={() => stallForm.setFieldValue('imageUrl', '')}>
                移除图片
              </Button>
            ) : null}
            <span className="stall-image-upload-tip">支持 JPG、PNG、WebP、GIF，最大 10MB</span>
          </div>
          {stallImage ? <Image src={stallImage} className="stall-form-image" /> : null}
          <Form.Item name="sortOrder" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </SidePanel>

      <SidePanel
        title={editingDish ? '编辑菜品' : `新增菜品 · ${selectedStall?.name || ''}`}
        open={dishEditorOpen}
        onClose={() => setDishEditorOpen(false)}
        destroyOnHidden
        footer={(
          <>
            <Button onClick={() => setDishEditorOpen(false)}>取消</Button>
            <Button
              type="primary"
              loading={saving || dishImageUploading}
              disabled={dishImageUploading}
              onClick={saveDish}
            >
              保存
            </Button>
          </>
        )}
      >
        <Form form={dishForm} layout="vertical">
          <Form.Item label="所属档口">
            <Input value={selectedStall?.name} disabled />
          </Form.Item>
          <Form.Item name="name" label="菜品名称" rules={[{ required: true, message: '请输入菜品名称' }]}>
            <Input />
          </Form.Item>
          <div className="stall-form-grid">
            <Form.Item name="price" label="价格" rules={[{ required: true, message: '请输入价格' }]}>
              <InputNumber min={0} precision={2} prefix="¥" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="isAvailable" label="状态" rules={[{ required: true }]}>
              <Select options={DISH_STATUS_OPTIONS} />
            </Form.Item>
          </div>
          <div className="stall-form-grid">
            <Form.Item
              name="cuisineId"
              label="菜系"
              rules={[{ required: true, message: '请选择菜系' }]}
              extra={<Button type="link" size="small" onClick={() => setCategoryManagerOpen(true)}>管理菜系</Button>}
            >
              <Select
                options={cuisineOptions}
                placeholder="请选择菜品所属菜系"
                notFoundContent="请先新增菜系"
              />
            </Form.Item>
            <Form.Item name="taste" label="口味">
              <Input placeholder="例如：清淡、麻辣" />
            </Form.Item>
          </div>
          <Form.Item name="description" label="菜品介绍">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="imageUrl" label="菜品图片地址">
            <Input placeholder="请输入图片 URL" />
          </Form.Item>
          <div className="stall-image-upload-actions">
            <Upload
              accept="image/jpeg,image/png,image/webp,image/gif"
              showUploadList={false}
              beforeUpload={uploadDishImage}
              disabled={dishImageUploading}
            >
              <Button icon={<UploadOutlined />} loading={dishImageUploading}>
                {dishImage ? '替换菜品图片' : '上传菜品图片'}
              </Button>
            </Upload>
            {dishImage ? (
              <Button danger onClick={() => dishForm.setFieldValue('imageUrl', '')}>
                移除图片
              </Button>
            ) : null}
            <span className="stall-image-upload-tip">支持 JPG、PNG、WebP、GIF，最大 10MB</span>
          </div>
          {dishImage ? <Image src={dishImage} className="stall-form-image" /> : null}
          <Form.Item name="stallPlaceId" hidden><Input /></Form.Item>
        </Form>
      </SidePanel>

      <Modal
        title={`${editingCategory ? '编辑' : '新增'}${categoryKind === 'floor' ? '楼层点位' : '菜品菜系'}`}
        open={categoryEditorOpen}
        confirmLoading={saving}
        onCancel={() => setCategoryEditorOpen(false)}
        onOk={saveCategory}
        destroyOnHidden
      >
        <Form form={categoryForm} layout="vertical">
          <Form.Item
            name="name"
            label={categoryKind === 'floor' ? '楼层名称' : '菜系名称'}
            rules={[{ required: true, message: '请输入分类名称' }]}
          >
            <Input placeholder={categoryKind === 'floor' ? '例如：1F、地下1层' : '例如：面食、川湘菜'} />
          </Form.Item>
          <div className="stall-form-grid">
            <Form.Item name="status" label="状态" rules={[{ required: true }]}>
              <Select options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]} />
            </Form.Item>
            <Form.Item name="sortOrder" label="排序">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </div>
  )
}
