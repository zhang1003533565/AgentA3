import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  Popconfirm,
  Select,
  Spin,
  Tag,
  message,
} from 'antd'
import {
  DeleteOutlined,
  EditOutlined,
  EnvironmentOutlined,
  PlusOutlined,
  SearchOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import SidePanel from '../../components/SidePanel/SidePanel'
import {
  createFacility,
  deleteFacility,
  getFacilityList,
  updateFacility,
} from '../../api/facility'
import './CanteenManage.css'

const STATUS_MAP = {
  1: { color: 'success', text: '营业中' },
  2: { color: 'warning', text: '维护中' },
  3: { color: 'default', text: '已停用' },
}

const STATUS_OPTIONS = [
  { value: 1, label: '营业中' },
  { value: 2, label: '维护中' },
  { value: 3, label: '已停用' },
]

const parseImages = (raw) => {
  if (!raw) return []
  if (Array.isArray(raw)) return raw.filter(Boolean)
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter(Boolean) : []
  } catch {
    return []
  }
}

const toCanteen = (facility) => ({
  ...facility,
  name: facility.facilityName,
  images: parseImages(facility.images),
})

function Carousel({ images }) {
  const [currentIndex, setCurrentIndex] = useState(0)

  useEffect(() => {
    setCurrentIndex(0)
    if (images.length <= 1) return undefined
    const timer = window.setInterval(() => {
      setCurrentIndex((previous) => (previous + 1) % images.length)
    }, 3000)
    return () => window.clearInterval(timer)
  }, [images])

  if (!images.length) return null
  return (
    <div className="canteen-carousel">
      {images.map((image, index) => (
        <img
          key={image}
          src={image}
          alt="食堂"
          className={`canteen-carousel-image${index === currentIndex ? ' active' : ''}`}
        />
      ))}
      {images.length > 1 ? (
        <div className="canteen-carousel-dots">
          {images.map((image, index) => (
            <span key={image} className={`dot${index === currentIndex ? ' active' : ''}`} />
          ))}
        </div>
      ) : null}
    </div>
  )
}

function CanteenEditor({ open, canteen, saving, onClose, onSave }) {
  const [form] = Form.useForm()
  const isEdit = Boolean(canteen)

  useEffect(() => {
    if (!open) return
    form.setFieldsValue({
      facilityName: canteen?.facilityName || '',
      location: canteen?.location || '',
      status: canteen?.status ?? 1,
      description: canteen?.description || '',
    })
  }, [canteen, form, open])

  const submit = async () => {
    try {
      onSave(await form.validateFields())
    } catch {
      // Ant Design 会在字段旁显示校验结果。
    }
  }

  return (
    <SidePanel
      title={isEdit ? '编辑食堂' : '新增食堂'}
      open={open}
      onClose={onClose}
      destroyOnHidden
      footer={(
        <>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={saving} onClick={submit}>保存</Button>
        </>
      )}
    >
      <Form form={form} layout="vertical">
        <Form.Item label="食堂名称" name="facilityName" rules={[{ required: true, message: '请输入食堂名称' }]}>
          <Input placeholder="请输入数据库中的正式名称" />
        </Form.Item>
        <Form.Item label="位置说明" name="location">
          <Input placeholder="例如：朝阳校区东区" />
        </Form.Item>
        <Form.Item label="营业状态" name="status" rules={[{ required: true, message: '请选择状态' }]}>
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item label="设施说明" name="description">
          <Input.TextArea rows={4} placeholder="介绍开放范围、服务内容等" />
        </Form.Item>
        <div className="canteen-location-tip">
          <EnvironmentOutlined />
          这里只维护食堂资料。保存后请在食堂列表点击“地图位置”，前往标点管理进行取点或绘制区域围栏。
        </div>
      </Form>
    </SidePanel>
  )
}

function CanteenDetail({ open, canteen, onClose, onEditLocation }) {
  if (!canteen) return null
  const status = STATUS_MAP[canteen.status] || STATUS_MAP[1]
  return (
    <SidePanel title="食堂详情" open={open} onClose={onClose}>
      <Descriptions bordered column={1} size="small">
        <Descriptions.Item label="食堂名称">{canteen.facilityName}</Descriptions.Item>
        <Descriptions.Item label="位置说明">{canteen.location || '—'}</Descriptions.Item>
        <Descriptions.Item label="营业状态"><Tag color={status.color}>{status.text}</Tag></Descriptions.Item>
        <Descriptions.Item label="设施说明">{canteen.description || '—'}</Descriptions.Item>
        <Descriptions.Item label="空间形态">{canteen.geometryType === 'AREA' ? '区域围栏' : '单点位置'}</Descriptions.Item>
        <Descriptions.Item label="地图坐标">
          {canteen.longitude != null && canteen.latitude != null
            ? `${canteen.longitude}, ${canteen.latitude}`
            : '未设置'}
        </Descriptions.Item>
        <Descriptions.Item label="地图维护">
          <Button icon={<EnvironmentOutlined />} size="small" onClick={onEditLocation}>编辑地图位置</Button>
        </Descriptions.Item>
      </Descriptions>
    </SidePanel>
  )
}

export default function CanteenManage() {
  const navigate = useNavigate()
  const [canteens, setCanteens] = useState([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState(null)
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingCanteen, setEditingCanteen] = useState(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailCanteen, setDetailCanteen] = useState(null)

  const loadCanteens = useCallback(async () => {
    setLoading(true)
    try {
      const response = await getFacilityList({ type: 1, page: 1, size: 500 })
      setCanteens((response.data?.records || []).map(toCanteen))
    } catch (error) {
      message.error(error?.message || '食堂数据加载失败')
      setCanteens([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadCanteens()
  }, [loadCanteens])

  const filtered = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase()
    return canteens.filter((canteen) => {
      const matchesStatus = statusFilter === 'ALL' || canteen.status === Number(statusFilter)
      const matchesKeyword = !keyword
        || canteen.facilityName?.toLowerCase().includes(keyword)
        || canteen.location?.toLowerCase().includes(keyword)
      return matchesStatus && matchesKeyword
    })
  }, [canteens, searchTerm, statusFilter])

  const openCreate = () => {
    setEditingCanteen(null)
    setEditorOpen(true)
  }

  const openEdit = (canteen) => {
    setEditingCanteen(canteen)
    setEditorOpen(true)
  }

  const saveCanteen = async (values) => {
    setSaving(true)
    try {
      const payload = {
        facilityName: values.facilityName.trim(),
        facilityType: 1,
        location: values.location?.trim() || '',
        description: values.description?.trim() || '',
        status: values.status,
      }
      if (editingCanteen) {
        await updateFacility(editingCanteen.id, payload)
      } else {
        await createFacility(payload)
      }
      message.success(editingCanteen ? '食堂信息已更新' : '食堂已创建，请继续设置地图位置')
      setEditorOpen(false)
      setEditingCanteen(null)
      await loadCanteens()
    } catch (error) {
      message.error(error?.message || '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const removeCanteen = async (canteen) => {
    setDeletingId(canteen.id)
    try {
      await deleteFacility(canteen.id)
      message.success(`${canteen.facilityName} 已删除`)
      await loadCanteens()
    } catch (error) {
      message.error(error?.message || '删除失败')
    } finally {
      setDeletingId(null)
    }
  }

  const editLocation = (canteen) => {
    navigate(`/facility/marker?facilityId=${canteen.id}`)
  }

  const counts = {
    ALL: canteens.length,
    1: canteens.filter((item) => item.status === 1).length,
    2: canteens.filter((item) => item.status === 2).length,
    3: canteens.filter((item) => item.status === 3).length,
  }

  return (
    <div className="canteen-manage-page">
      <header className="canteen-header">
        <div className="canteen-header-right">
          <div className="canteen-search">
            <Input
              placeholder="搜索食堂名称或位置"
              prefix={<SearchOutlined />}
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              allowClear
              style={{ width: 260 }}
            />
          </div>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增食堂</Button>
        </div>
      </header>

      <div className="canteen-stats">
        {[
          ['ALL', '全部食堂'],
          ['1', '营业中'],
          ['2', '维护中'],
          ['3', '已停用'],
        ].map(([value, label]) => (
          <button
            key={value}
            type="button"
            className={`stat-item${statusFilter === value ? ' active' : ''}`}
            onClick={() => setStatusFilter(value)}
          >
            <span className="stat-number">{counts[value]}</span>
            <span className="stat-label">{label}</span>
          </button>
        ))}
      </div>

      <Spin spinning={loading}>
        <div className="canteen-grid">
          {!filtered.length ? (
            <div className="canteen-empty">
              <Empty description={loading ? '正在读取数据库' : '数据库中暂无食堂数据'} />
            </div>
          ) : filtered.map((canteen) => {
            const status = STATUS_MAP[canteen.status] || STATUS_MAP[1]
            const hasLocation = canteen.longitude != null && canteen.latitude != null
            return (
              <Card key={canteen.id} className="canteen-card">
                <div className="canteen-card-image">
                  {canteen.images.length ? (
                    <Carousel images={canteen.images} />
                  ) : (
                    <div className="canteen-card-image-placeholder"><ShopOutlined /></div>
                  )}
                </div>
                <div className="canteen-card-info">
                  <div className="canteen-card-title">
                    <span className="canteen-main-name">{canteen.facilityName}</span>
                    <span className="canteen-sub-name">设施编号 #{canteen.id}</span>
                  </div>
                  <div className="canteen-card-tags">
                    <Tag className="area-tag">{canteen.location || '未填写位置说明'}</Tag>
                    <Tag color={status.color} className="status-tag">{status.text}</Tag>
                    <Tag color={hasLocation ? 'blue' : 'orange'}>{hasLocation ? '已定位' : '待定位'}</Tag>
                  </div>
                  <div className="canteen-card-details">
                    <div className="detail-row">
                      <span className="detail-label">空间形态</span>
                      <span className="detail-value">{canteen.geometryType === 'AREA' ? '区域围栏' : '单点位置'}</span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">地图坐标</span>
                      <span className="detail-value">{hasLocation ? `${canteen.longitude}, ${canteen.latitude}` : '未设置'}</span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">更新时间</span>
                      <span className="detail-value">{canteen.updateTime || '—'}</span>
                    </div>
                  </div>
                </div>
                <div className="canteen-card-actions">
                  <Button icon={<EditOutlined />} onClick={() => openEdit(canteen)}>编辑</Button>
                  <Button icon={<EnvironmentOutlined />} onClick={() => editLocation(canteen)}>地图位置</Button>
                  <Button onClick={() => {
                    setDetailCanteen(canteen)
                    setDetailOpen(true)
                  }}>详情</Button>
                  <Popconfirm
                    title={`确认删除 ${canteen.facilityName}？`}
                    description="将同时删除关联地图标记，操作不可恢复"
                    onConfirm={() => removeCanteen(canteen)}
                    okText="确认"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                  >
                    <Button danger loading={deletingId === canteen.id} icon={<DeleteOutlined />} />
                  </Popconfirm>
                </div>
              </Card>
            )
          })}
        </div>
      </Spin>

      <CanteenEditor
        open={editorOpen}
        canteen={editingCanteen}
        saving={saving}
        onClose={() => setEditorOpen(false)}
        onSave={saveCanteen}
      />
      <CanteenDetail
        open={detailOpen}
        canteen={detailCanteen}
        onClose={() => setDetailOpen(false)}
        onEditLocation={() => editLocation(detailCanteen)}
      />
    </div>
  )
}
