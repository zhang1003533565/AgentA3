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
  message,
} from 'antd'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SettingOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import {
  createDish,
  createStall,
  deleteDish,
  deleteStall,
  getCanteenStallList,
  getDishList,
  updateDish,
  updateStall,
} from '../../api/dish'
import {
  createFacilityFloor,
  createStallCuisine,
  deleteFacilityFloor,
  deleteStallCuisine,
  getFacilityFloors,
  getFacilityList,
  getStallCuisines,
  updateFacilityFloor,
  updateStallCuisine,
} from '../../api/facility'
import { getMapPlaceDetail } from '../../api/mapPlace'
import SidePanel from '../../components/SidePanel/SidePanel'
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

const normalizeName = (value = '') => value.trim().replace(/\s+/g, '')

const renderStallStatus = (status) => {
  const option = STALL_STATUS_OPTIONS.find((item) => item.value === Number(status))
  const color = Number(status) === 1 ? 'success' : Number(status) === 2 ? 'warning' : 'default'
  return <Tag color={color}>{option?.label || '未知'}</Tag>
}

export default function StallManage() {
  const { canteenId } = useParams()
  const navigate = useNavigate()
  const [stallForm] = Form.useForm()
  const [dishForm] = Form.useForm()
  const [categoryForm] = Form.useForm()
  const [canteen, setCanteen] = useState(null)
  const [restaurantId, setRestaurantId] = useState(null)
  const [stalls, setStalls] = useState([])
  const [dishes, setDishes] = useState([])
  const [selectedStallId, setSelectedStallId] = useState(null)
  const [stallKeyword, setStallKeyword] = useState('')
  const [dishKeyword, setDishKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [dishLoading, setDishLoading] = useState(false)
  const [saving, setSaving] = useState(false)
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
  const stallImage = Form.useWatch('image', stallForm)
  const dishImage = Form.useWatch('imageUrl', dishForm)

  const selectedStall = useMemo(
    () => stalls.find((item) => String(item.id) === String(selectedStallId)) || null,
    [selectedStallId, stalls],
  )

  const filteredStalls = useMemo(() => {
    const keyword = stallKeyword.trim().toLowerCase()
    if (!keyword) return stalls
    return stalls.filter((item) =>
      [item.stallName, item.category, item.floor, item.location]
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
      label: item.floorName,
      disabled: Number(item.status) !== 1,
    })),
    [floors],
  )

  const resolveRestaurant = useCallback(async (mapPlace) => {
    const response = await getFacilityList({ type: 1, pageNum: 1, pageSize: 200 })
    const restaurants = getRows(response)
    const name = normalizeName(mapPlace?.name)
    const matched = restaurants.find((item) => normalizeName(item.facilityName) === name)
    return Number(matched?.id || canteenId)
  }, [canteenId])

  const loadCategories = useCallback(async (targetRestaurantId) => {
    if (!targetRestaurantId) {
      setFloors([])
      setCuisines([])
      return
    }
    const [floorResponse, cuisineResponse] = await Promise.all([
      getFacilityFloors(targetRestaurantId),
      getStallCuisines(targetRestaurantId),
    ])
    setFloors(getRows(floorResponse))
    setCuisines(getRows(cuisineResponse))
  }, [])

  const loadStalls = useCallback(async (preferredStallId = null) => {
    setLoading(true)
    try {
      const mapResponse = await getMapPlaceDetail(canteenId)
      const mapPlace = mapResponse.data || null
      setCanteen(mapPlace)
      const resolvedRestaurantId = await resolveRestaurant(mapPlace)
      setRestaurantId(resolvedRestaurantId)
      await loadCategories(resolvedRestaurantId)

      // 查询完整列表后在前端按餐厅过滤，保证“休息中/已关闭”的档口也能在管理端看到。
      const stallResponse = await getCanteenStallList()
      const rows = getRows(stallResponse)
        .filter((item) => Number(item.restaurantId) === resolvedRestaurantId)
        .sort((left, right) => (left.sort || 0) - (right.sort || 0))
      setStalls(rows)
      setSelectedStallId((current) => {
        const expected = preferredStallId ?? current
        if (rows.some((item) => String(item.id) === String(expected))) return expected
        return rows[0]?.id ?? null
      })
    } finally {
      setLoading(false)
    }
  }, [canteenId, loadCategories, resolveRestaurant])

  const loadDishes = useCallback(async () => {
    if (!selectedStallId) {
      setDishes([])
      return
    }
    setDishLoading(true)
    try {
      // 管理端需要同时看到上架和下架菜品，因此取完整列表后按档口归属过滤。
      const response = await getDishList()
      setDishes(getRows(response).filter(
        (item) => String(item.stallId) === String(selectedStallId),
      ))
    } finally {
      setDishLoading(false)
    }
  }, [selectedStallId])

  useEffect(() => {
    loadStalls()
  }, [loadStalls])

  useEffect(() => {
    loadDishes()
  }, [loadDishes])

  const openCreateStall = () => {
    setEditingStall(null)
    stallForm.resetFields()
    stallForm.setFieldsValue({
      restaurantId,
      status: 1,
      sort: stalls.length,
    })
    setStallEditorOpen(true)
  }

  const openEditStall = (record) => {
    setEditingStall(record)
    stallForm.resetFields()
    stallForm.setFieldsValue({
      ...record,
      floorId: record.floorId || floors.find((item) => item.floorName === record.floor)?.id,
      cuisineId: record.cuisineId || cuisines.find((item) => item.cuisineName === record.category)?.id,
    })
    setStallEditorOpen(true)
  }

  const saveStall = async () => {
    const values = await stallForm.validateFields()
    setSaving(true)
    try {
      const payload = { ...editingStall, ...values, restaurantId }
      const response = editingStall
        ? await updateStall(editingStall.id, payload)
        : await createStall(payload)
      const savedId = response.data?.id || editingStall?.id
      message.success(editingStall ? '档口已更新' : '档口已新增')
      setStallEditorOpen(false)
      await loadStalls(savedId)
    } finally {
      setSaving(false)
    }
  }

  const removeStall = async (record) => {
    await deleteStall(record.id)
    message.success('档口已删除')
    await loadStalls()
  }

  const openCreateDish = () => {
    if (!selectedStall) return
    setEditingDish(null)
    dishForm.resetFields()
    dishForm.setFieldsValue({
      stallId: selectedStall.id,
      isAvailable: true,
    })
    setDishEditorOpen(true)
  }

  const openEditDish = (record) => {
    setEditingDish(record)
    dishForm.resetFields()
    dishForm.setFieldsValue(record)
    setDishEditorOpen(true)
  }

  const saveDish = async () => {
    const values = await dishForm.validateFields()
    setSaving(true)
    try {
      const payload = { ...editingDish, ...values, stallId: selectedStall.id }
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
      name: kind === 'floor' ? record?.floorName : record?.cuisineName,
      status: record?.status ?? 1,
      sortOrder: record?.sortOrder ?? 0,
    })
    setCategoryEditorOpen(true)
  }

  const saveCategory = async () => {
    const values = await categoryForm.validateFields()
    const payload = categoryKind === 'floor'
      ? { ...values, facilityId: restaurantId }
      : { ...values, restaurantId }
    setSaving(true)
    try {
      const response = categoryKind === 'floor'
        ? editingCategory
          ? await updateFacilityFloor(editingCategory.id, payload)
          : await createFacilityFloor(payload)
        : editingCategory
          ? await updateStallCuisine(editingCategory.id, payload)
          : await createStallCuisine(payload)
      message.success(editingCategory ? '分类已更新' : '分类已新增')
      setCategoryEditorOpen(false)
      await loadCategories(restaurantId)
      if (!editingCategory && stallEditorOpen) {
        stallForm.setFieldValue(
          categoryKind === 'floor' ? 'floorId' : 'cuisineId',
          response.data?.id,
        )
      }
    } finally {
      setSaving(false)
    }
  }

  const removeCategory = async (kind, record) => {
    if (kind === 'floor') await deleteFacilityFloor(record.id)
    else await deleteStallCuisine(record.id)
    message.success('分类已删除')
    await loadCategories(restaurantId)
  }

  const stallColumns = [
    {
      title: '档口名称',
      dataIndex: 'stallName',
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
      title: '菜系',
      dataIndex: 'category',
      width: 130,
      render: (value) => value ? <Tag color="blue">{value}</Tag> : '-',
    },
    {
      title: '档口位置',
      key: 'location',
      render: (_, record) => [record.floor, record.location].filter(Boolean).join(' · ') || '-',
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
      dataIndex: 'status',
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
      title: '分类',
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
          <p>{isFloor ? '楼层用于标识档口所在区域。' : '菜系用于统一归类档口经营类型。'}</p>
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
              dataIndex: isFloor ? 'floorName' : 'cuisineName',
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
              render: (value) => (
                <Tag color={Number(value) === 1 ? 'success' : 'default'}>
                  {Number(value) === 1 ? '启用' : '停用'}
                </Tag>
              ),
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
                    description="已被档口使用时不能删除，可以改为停用。"
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
            onClick={() => navigate('/facility/canteen')}
          >
            返回食堂列表
          </Button>
          <h1>{canteen?.name || '食堂'} · 档口与菜品</h1>
          <p>档口按业务列表维护；菜品归属当前档口，不作为地图点位。</p>
        </div>
        <Space>
          <Button
            icon={<SettingOutlined />}
            onClick={() => {
              setCategoryManagerOpen(true)
              loadCategories(restaurantId)
            }}
            disabled={!restaurantId}
          >
            楼层与菜系
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateStall} disabled={!restaurantId}>
            新增档口
          </Button>
        </Space>
      </div>

      <Card className="stall-section-card">
        <div className="stall-section-heading">
          <div>
            <h2>档口列表</h2>
            <p>维护档口的菜系、楼层、具体位置和营业信息。</p>
          </div>
          <Input.Search
            allowClear
            placeholder="搜索档口、菜系或位置"
            value={stallKeyword}
            onChange={(event) => setStallKeyword(event.target.value)}
            className="stall-search"
          />
        </div>
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
        />
      </Card>

      <Card className="stall-section-card dish-section-card">
        <div className="stall-section-heading">
          <div>
            <h2>菜品管理</h2>
            <p>
              {selectedStall
                ? `当前档口：${selectedStall.stallName} · ${selectedStall.category || '未设置菜系'} · ${[selectedStall.floor, selectedStall.location].filter(Boolean).join(' / ') || '未设置位置'}`
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

      <SidePanel
        title={`${canteen?.name || '校园设施'} · 楼层与菜系`}
        open={categoryManagerOpen}
        onClose={() => setCategoryManagerOpen(false)}
        width={760}
        destroyOnHidden
      >
        <div className="category-manager-intro">
          楼层属于校园设施的通用基础数据，可供食堂、教学楼等建筑绑定；菜系用于当前食堂的档口分类。
        </div>
        <Tabs
          items={[
            {
              key: 'floor',
              label: `设施楼层（${floors.length}）`,
              children: renderCategoryTable('floor'),
            },
            {
              key: 'cuisine',
              label: `档口菜系（${cuisines.length}）`,
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
            <Button type="primary" loading={saving} onClick={saveStall}>保存</Button>
          </>
        )}
      >
        <Form form={stallForm} layout="vertical">
          <Form.Item name="stallName" label="档口名称" rules={[{ required: true, message: '请输入档口名称' }]}>
            <Input placeholder="例如：兰州拉面" />
          </Form.Item>
          <div className="stall-form-grid">
            <Form.Item
              name="cuisineId"
              label="菜系"
              rules={[{ required: true, message: '请选择菜系' }]}
              extra={<Button type="link" size="small" onClick={() => setCategoryManagerOpen(true)}>管理菜系</Button>}
            >
              <Select
                allowClear
                options={cuisineOptions}
                placeholder="请选择已配置的菜系"
                notFoundContent="请先在“楼层与菜系”中新增"
              />
            </Form.Item>
            <Form.Item name="status" label="营业状态" rules={[{ required: true }]}>
              <Select options={STALL_STATUS_OPTIONS} />
            </Form.Item>
          </div>
          <div className="stall-form-grid">
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
          <Form.Item name="image" label="档口图片地址">
            <Input placeholder="请输入图片 URL" />
          </Form.Item>
          {stallImage ? <Image src={stallImage} className="stall-form-image" /> : null}
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </SidePanel>

      <SidePanel
        title={editingDish ? '编辑菜品' : `新增菜品 · ${selectedStall?.stallName || ''}`}
        open={dishEditorOpen}
        onClose={() => setDishEditorOpen(false)}
        destroyOnHidden
        footer={(
          <>
            <Button onClick={() => setDishEditorOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={saveDish}>保存</Button>
          </>
        )}
      >
        <Form form={dishForm} layout="vertical">
          <Form.Item label="所属档口">
            <Input value={selectedStall?.stallName} disabled />
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
            <Form.Item name="category" label="菜品分类">
              <Input placeholder="例如：招牌、主食、小吃" />
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
          {dishImage ? <Image src={dishImage} className="stall-form-image" /> : null}
          <Form.Item name="stallId" hidden><Input /></Form.Item>
        </Form>
      </SidePanel>

      <Modal
        title={`${editingCategory ? '编辑' : '新增'}${categoryKind === 'floor' ? '设施楼层' : '档口菜系'}`}
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
