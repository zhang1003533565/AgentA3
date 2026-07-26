import { useEffect, useRef, useState } from 'react'
import { Button, Card, Col, Descriptions, Empty, Form, Input, InputNumber, Popconfirm, Row, Select, Space, Tag, message } from 'antd'
import {
  DeleteOutlined,
  EnvironmentOutlined,
  MoreOutlined,
  SearchOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import SidePanel from '../../components/SidePanel/SidePanel'
import { canteenData } from './canteenData'
import './CanteenManage.css'

const STATUS_MAP = {
  '营业中': { color: 'success', text: '营业中' },
  '维护中': { color: 'warning', text: '维护中' },
  '已停用': { color: 'default', text: '已停用' },
}

/* ========== 图片轮播组件 ========== */
function Carousel({ images }) {
  const [currentIndex, setCurrentIndex] = useState(0)
  const timerRef = useRef(null)

  if (!images || images.length === 0) return null

  // 单张图片直接展示，不轮播
  if (images.length === 1) {
    return (
      <img
        src={images[0]}
        alt="食堂图片"
        className="canteen-carousel-image canteen-carousel-image-single"
      />
    )
  }

  // 多张图片自动轮播
  useEffect(() => {
    timerRef.current = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % images.length)
    }, 3000)

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current)
      }
    }
  }, [images.length])

  return (
    <div className="canteen-carousel">
      {images.map((img, idx) => (
        <img
          key={idx}
          src={img}
          alt={`食堂图片${idx + 1}`}
          className={`canteen-carousel-image ${idx === currentIndex ? 'active' : ''}`}
        />
      ))}
      <div className="canteen-carousel-dots">
        {images.map((_, idx) => (
          <span
            key={idx}
            className={`dot ${idx === currentIndex ? 'active' : ''}`}
          ></span>
        ))}
      </div>
    </div>
  )
}

/* ========== 编辑弹窗 ========== */
function EditCanteenModal({ open, onClose, canteen, onSave }) {
  const [form] = Form.useForm()

  useEffect(() => {
    if (open && canteen) {
      form.setFieldsValue({
        name: canteen.name,
        subName: canteen.subName,
        area: canteen.area,
        status: canteen.status,
        type: canteen.type,
        businessHours: canteen.businessHours,
        seats: canteen.seats,
        manager: canteen.manager,
        phone: canteen.phone,
        stallCount: canteen.stallCount,
      })
    }
  }, [open, canteen, form])

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      onSave(canteen.id, values)
    } catch {
      message.warning('请检查表单填写')
    }
  }

  return (
    <SidePanel
      title="编辑食堂"
      open={open}
      onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={handleSubmit}>保存</Button>
        </>
      )}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          label="食堂名称"
          name="name"
          rules={[{ required: true, message: '请输入食堂名称' }]}
        >
          <Input placeholder="例：学四食堂" />
        </Form.Item>

        <Form.Item
          label="副名称"
          name="subName"
        >
          <Input placeholder="例：沁园食堂" />
        </Form.Item>

        <Form.Item
          label="所属区域"
          name="area"
          rules={[{ required: true, message: '请选择区域' }]}
        >
          <Select placeholder="选择区域" options={[
            { value: '主校区', label: '主校区' },
            { value: '东校区', label: '东校区' },
            { value: '西校区', label: '西校区' },
            { value: '南校区', label: '南校区' },
            { value: '北校区', label: '北校区' },
          ]} />
        </Form.Item>

        <Form.Item
          label="营业状态"
          name="status"
          rules={[{ required: true, message: '请选择状态' }]}
        >
          <Select placeholder="选择状态" options={[
            { value: '营业中', label: '营业中' },
            { value: '维护中', label: '维护中' },
            { value: '已停用', label: '已停用' },
          ]} />
        </Form.Item>

        <Form.Item
          label="食堂类型"
          name="type"
          rules={[{ required: true, message: '请选择类型' }]}
        >
          <Select placeholder="选择类型" options={[
            { value: '综合餐厅', label: '综合餐厅' },
            { value: '学生餐厅', label: '学生餐厅' },
            { value: '特色餐厅', label: '特色餐厅' },
            { value: '快餐', label: '快餐' },
            { value: '自助餐', label: '自助餐' },
          ]} />
        </Form.Item>

        <Form.Item
          label="营业时间"
          name="businessHours"
          rules={[{ required: true, message: '请输入营业时间' }]}
        >
          <Input placeholder="例：06:30 - 21:00" />
        </Form.Item>

        <Form.Item
          label="座位数量"
          name="seats"
          rules={[{ required: true, message: '请输入座位数量' }]}
        >
          <InputNumber min={0} style={{ width: '100%' }} placeholder="个" />
        </Form.Item>

        <Form.Item
          label="负责人"
          name="manager"
          rules={[{ required: true, message: '请输入负责人姓名' }]}
        >
          <Input placeholder="负责人姓名" />
        </Form.Item>

        <Form.Item
          label="联系电话"
          name="phone"
          rules={[{ required: true, message: '请输入联系电话' }]}
        >
          <Input placeholder="手机号码" maxLength={11} />
        </Form.Item>

        <Form.Item
          label="档口数量"
          name="stallCount"
          rules={[{ required: true, message: '请输入档口数量' }]}
        >
          <InputNumber min={0} style={{ width: '100%' }} placeholder="个" />
        </Form.Item>
      </Form>
    </SidePanel>
  )
}

/* ========== 详情侧面板 ========== */
function DetailModal({ open, onClose, canteen }) {
  if (!canteen) return null

  return (
    <SidePanel
      title="食堂详情"
      open={open}
      onClose={onClose}
    >
      <Descriptions bordered column={1} size="small">
        <Descriptions.Item label="食堂名称">{canteen.name}</Descriptions.Item>
        <Descriptions.Item label="副名称">{canteen.subName || '-'}</Descriptions.Item>
        <Descriptions.Item label="所属区域">{canteen.area}</Descriptions.Item>
        <Descriptions.Item label="营业状态">
          <Tag color={STATUS_MAP[canteen.status]?.color || 'default'}>
            {canteen.status}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="营业时间">{canteen.businessHours}</Descriptions.Item>
        <Descriptions.Item label="食堂类型">{canteen.type}</Descriptions.Item>
        <Descriptions.Item label="座位数量">{canteen.seats} 个</Descriptions.Item>
        <Descriptions.Item label="负责人">{canteen.manager}</Descriptions.Item>
        <Descriptions.Item label="联系电话">{canteen.phone}</Descriptions.Item>
        <Descriptions.Item label="档口数量">{canteen.stallCount} 个</Descriptions.Item>
        <Descriptions.Item label="图片信息">
          {canteen.images && canteen.images.length > 0 ? (
            <Row gutter={[8, 8]}>
              {canteen.images.map((img, idx) => (
                <Col key={idx} span={8}>
                  <img
                    src={img}
                    alt={`食堂图片${idx + 1}`}
                    style={{ width: '100%', height: 80, objectFit: 'cover', borderRadius: 4 }}
                  />
                </Col>
              ))}
            </Row>
          ) : (
            '-'
          )}
        </Descriptions.Item>
        <Descriptions.Item label="地图位置">
          <Button icon={<EnvironmentOutlined />} size="small">
            查看地图定位
          </Button>
        </Descriptions.Item>
      </Descriptions>
    </SidePanel>
  )
}

/* ========== 主页面 ========== */
export default function CanteenManage() {
  const navigate = useNavigate()
  const [canteens, setCanteens] = useState([...canteenData])
  const [filtered, setFiltered] = useState([...canteenData])
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState('全部')
  const [deletedId, setDeletedId] = useState(null)
  const [editOpen, setEditOpen] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [editingCanteen, setEditingCanteen] = useState(null)
  const [detailCanteen, setDetailCanteen] = useState(null)

  // 搜索和状态筛选
  useEffect(() => {
    let result = [...canteens]

    // 状态筛选
    if (statusFilter !== '全部') {
      result = result.filter(c => c.status === statusFilter)
    }

    // 关键词搜索
    if (searchTerm.trim()) {
      const term = searchTerm.trim().toLowerCase()
      result = result.filter(c =>
        c.name.toLowerCase().includes(term) ||
        (c.subName && c.subName.toLowerCase().includes(term))
      )
    }

    setFiltered(result)
  }, [canteens, searchTerm, statusFilter])

  // 处理删除
  const handleDelete = (id, name) => {
    setDeletedId(id)
    setTimeout(() => {
      setCanteens(prev => prev.filter(c => c.id !== id))
      setDeletedId(null)
      message.success(`${name} 已删除`)
    }, 300)
  }

  // 处理编辑保存
  const handleEditSave = (id, values) => {
    setCanteens(prev =>
      prev.map(c =>
        c.id === id
          ? { ...c, ...values }
          : c
      )
    )
    setEditOpen(false)
    setEditingCanteen(null)
    message.success('保存成功')
  }

  // 统计
  const total = canteens.length
  const active = canteens.filter(c => c.status === '营业中').length
  const maintenance = canteens.filter(c => c.status === '维护中').length
  const inactive = canteens.filter(c => c.status === '已停用').length

  return (
    <div className="canteen-manage-page">
      {/* 页面头部（页题由布局顶栏面包屑统一渲染） */}
      <header className="canteen-header">
        <div className="canteen-header-right">
          <div className="canteen-search">
            <Input
              placeholder="搜索食堂名称"
              prefix={<SearchOutlined />}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              allowClear
              style={{ width: 240 }}
            />
          </div>
          <Button type="primary" onClick={() => message.info('新增食堂功能开发中')}>
            新增食堂
          </Button>
          <Button icon={<EnvironmentOutlined />} shape="circle" />
        </div>
      </header>

      {/* 统计卡片 */}
      <div className="canteen-stats">
        <div className={`stat-item ${statusFilter === '全部' ? 'active' : ''}`} onClick={() => setStatusFilter('全部')}>
          <div className="stat-number">{total}</div>
          <div className="stat-label">全部食堂</div>
        </div>
        <div className={`stat-item ${statusFilter === '营业中' ? 'active' : ''}`} onClick={() => setStatusFilter('营业中')}>
          <div className="stat-number">{active}</div>
          <div className="stat-label">营业中</div>
        </div>
        <div className={`stat-item ${statusFilter === '维护中' ? 'active' : ''}`} onClick={() => setStatusFilter('维护中')}>
          <div className="stat-number">{maintenance}</div>
          <div className="stat-label">维护中</div>
        </div>
        <div className={`stat-item ${statusFilter === '已停用' ? 'active' : ''}`} onClick={() => setStatusFilter('已停用')}>
          <div className="stat-number">{inactive}</div>
          <div className="stat-label">已停用</div>
        </div>
      </div>

      {/* 食堂卡片列表 */}
      <div className="canteen-grid">
        {filtered.length === 0 ? (
          <div className="canteen-empty">
            <Empty description={canteens.length === 0 ? '暂无食堂数据，请点击"新增食堂"添加' : '没有找到匹配的食堂'} />
          </div>
        ) : (
          filtered.map((canteen) => (
            <Card
              key={canteen.id}
              className={`canteen-card ${deletedId === canteen.id ? 'deleting' : ''}`}
            >
              {/* 图片区域 */}
              <div className="canteen-card-image">
                {canteen.images && canteen.images.length > 0 ? (
                  <Carousel images={canteen.images} />
                ) : (
                  <div className="canteen-card-image-placeholder">
                    <ShopOutlined style={{ fontSize: 48, color: '#ccc' }} />
                  </div>
                )}
              </div>

              {/* 基本信息 */}
              <div className="canteen-card-info">
                <div className="canteen-card-title">
                  <span className="canteen-main-name">{canteen.name}</span>
                  {canteen.subName && (
                    <span className="canteen-sub-name">{canteen.subName}</span>
                  )}
                </div>

                <div className="canteen-card-tags">
                  <Tag className="area-tag">{canteen.area}</Tag>
                  <Tag color={STATUS_MAP[canteen.status]?.color || 'default'} className="status-tag">
                    <span className="status-dot"></span>
                    {canteen.status}
                  </Tag>
                </div>

                <div className="canteen-card-details">
                  <div className="detail-row">
                    <span className="detail-label">营业时间</span>
                    <span className="detail-value">{canteen.businessHours}</span>
                  </div>
                  <div className="detail-row">
                    <span className="detail-label">负责人</span>
                    <span className="detail-value">{canteen.manager}</span>
                  </div>
                  <div className="detail-row">
                    <span className="detail-label">档口数量</span>
                    <span className="detail-value">{canteen.stallCount}个档口</span>
                  </div>
                </div>
              </div>

              {/* 操作按钮 */}
              <div className="canteen-card-actions">
                <Button
                  type="primary"
                  icon={<ShopOutlined />}
                  onClick={() => navigate(`/facility/canteen/${canteen.id}/stalls`)}
                >
                  查看档口
                </Button>
                <Button onClick={() => {
                  setDetailCanteen(canteen)
                  setDetailOpen(true)
                }}>
                  查看更多
                </Button>
                <Popconfirm
                  title={`确认删除 ${canteen.name}？`}
                  description="删除后将无法恢复"
                  onConfirm={() => handleDelete(canteen.id, canteen.name)}
                  okText="确认"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                >
                  <Button danger icon={<DeleteOutlined />}>
                    删除
                  </Button>
                </Popconfirm>
              </div>
            </Card>
          ))
        )}
      </div>

      {/* 编辑弹窗 */}
      <EditCanteenModal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        canteen={editingCanteen}
        onSave={handleEditSave}
      />

      {/* 详情弹窗 */}
      <DetailModal
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        canteen={detailCanteen}
      />
    </div>
  )
}
