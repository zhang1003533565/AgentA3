import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Card, Empty, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Tag, message } from 'antd'
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useParams } from 'react-router-dom'
import { canteenData } from '../CanteenManage/canteenData'
import './StallManage.css'

/* ========== 模拟数据 ========== */
const STALL_MOCK = {
  1: [ // 学一食堂 - 18个档口
    { id: 101, name: '川味小炒', number: '01号档口', type: '特色小吃', status: '营业中', manager: '李强', phone: '138****1001' },
    { id: 102, name: '兰州牛肉面', number: '02号档口', type: '面食', status: '营业中', manager: '王伟', phone: '138****1002' },
    { id: 103, name: '奶茶饮品', number: '03号档口', type: '饮品', status: '暂停营业', manager: '赵敏', phone: '138****1003' },
    { id: 104, name: '麻辣烫', number: '04号档口', type: '特色小吃', status: '营业中', manager: '刘洋', phone: '138****1004' },
    { id: 105, name: '烧腊饭', number: '05号档口', type: '快餐', status: '营业中', manager: '陈刚', phone: '138****1005' },
    { id: 106, name: '东北饺子', number: '06号档口', type: '面食', status: '营业中', manager: '张华', phone: '138****1006' },
    { id: 107, name: '广式肠粉', number: '07号档口', type: '点心', status: '营业中', manager: '林芳', phone: '138****1007' },
    { id: 108, name: '黄焖鸡米饭', number: '08号档口', type: '快餐', status: '营业中', manager: '马超', phone: '138****1008' },
    { id: 109, name: '酸辣粉', number: '09号档口', type: '特色小吃', status: '营业中', manager: '杨丽', phone: '138****1009' },
    { id: 110, name: '蛋挞烘焙', number: '10号档口', type: '点心', status: '营业中', manager: '何静', phone: '138****1010' },
    { id: 111, name: '卤肉饭', number: '11号档口', type: '快餐', status: '营业中', manager: '徐明', phone: '138****1011' },
    { id: 112, name: '石锅拌饭', number: '12号档口', type: '快餐', status: '营业中', manager: '宋佳', phone: '138****1012' },
    { id: 113, name: '凉皮米线', number: '13号档口', type: '特色小吃', status: '营业中', manager: '唐鹏', phone: '138****1013' },
    { id: 114, name: '水煎包', number: '14号档口', type: '面食', status: '营业中', manager: '曹颖', phone: '138****1014' },
    { id: 115, name: '铁板炒饭', number: '15号档口', type: '快餐', status: '营业中', manager: '邓凯', phone: '138****1015' },
    { id: 116, name: '豆浆油条', number: '16号档口', type: '早餐', status: '营业中', manager: '田甜', phone: '138****1016' },
    { id: 117, name: '烤冷面', number: '17号档口', type: '特色小吃', status: '暂停营业', manager: '潘磊', phone: '138****1017' },
    { id: 118, name: '沙拉轻食', number: '18号档口', type: '快餐', status: '营业中', manager: '蒋丽', phone: '138****1018' },
  ],
  2: [ // 学二食堂 - 15个档口
    { id: 201, name: '粤式点心', number: '01号档口', type: '点心', status: '营业中', manager: '黄丽', phone: '138****2001' },
    { id: 202, name: '砂锅粥', number: '02号档口', type: '粥品', status: '营业中', manager: '林志', phone: '138****2002' },
    { id: 203, name: '炸鸡排', number: '03号档口', type: '快餐', status: '暂停营业', manager: '周杰', phone: '138****2003' },
    { id: 204, name: '重庆小面', number: '04号档口', type: '面食', status: '营业中', manager: '吴芳', phone: '138****2004' },
    { id: 205, name: '煲仔饭', number: '05号档口', type: '快餐', status: '营业中', manager: '郑强', phone: '138****2005' },
    { id: 206, name: '肉夹馍', number: '06号档口', type: '特色小吃', status: '营业中', manager: '孙丽', phone: '138****2006' },
    { id: 207, name: '韩式料理', number: '07号档口', type: '特色菜', status: '营业中', manager: '钱明', phone: '138****2007' },
    { id: 208, name: '煎饼果子', number: '08号档口', type: '早餐', status: '营业中', manager: '冯佳', phone: '138****2008' },
    { id: 209, name: '猪脚饭', number: '09号档口', type: '快餐', status: '营业中', manager: '何伟', phone: '138****2009' },
    { id: 210, name: '云吞面', number: '10号档口', type: '面食', status: '营业中', manager: '许婷', phone: '138****2010' },
    { id: 211, name: '烧烤串吧', number: '11号档口', type: '特色小吃', status: '营业中', manager: '蔡斌', phone: '138****2011' },
    { id: 212, name: '豆花甜品', number: '12号档口', type: '饮品', status: '营业中', manager: '邓琳', phone: '138****2012' },
    { id: 213, name: '米粉专窗', number: '13号档口', type: '面食', status: '营业中', manager: '吕刚', phone: '138****2013' },
    { id: 214, name: '蒸菜窗口', number: '14号档口', type: '快餐', status: '营业中', manager: '朱红', phone: '138****2014' },
    { id: 215, name: '水果捞', number: '15号档口', type: '饮品', status: '暂停营业', manager: '秦浩', phone: '138****2015' },
  ],
  3: [ // 学三食堂 - 12个档口
    { id: 301, name: '新疆烤串', number: '01号档口', type: '特色小吃', status: '营业中', manager: '艾力', phone: '138****3001' },
    { id: 302, name: '酸菜鱼', number: '02号档口', type: '特色菜', status: '营业中', manager: '孙磊', phone: '138****3002' },
    { id: 303, name: '云南过桥米线', number: '03号档口', type: '面食', status: '营业中', manager: '杨雪', phone: '138****3003' },
    { id: 304, name: '湘味小炒', number: '04号档口', type: '特色菜', status: '营业中', manager: '李文', phone: '138****3004' },
    { id: 305, name: '日式拉面', number: '05号档口', type: '面食', status: '营业中', manager: '赵敏', phone: '138****3005' },
    { id: 306, name: '火锅冒菜', number: '06号档口', type: '特色菜', status: '营业中', manager: '周涛', phone: '138****3006' },
    { id: 307, name: '三明治轻食', number: '07号档口', type: '快餐', status: '营业中', manager: '郑琳', phone: '138****3007' },
    { id: 308, name: '葱油拌面', number: '08号档口', type: '面食', status: '营业中', manager: '王强', phone: '138****3008' },
    { id: 309, name: '烤鸡翅专窗', number: '09号档口', type: '快餐', status: '营业中', manager: '陈晨', phone: '138****3009' },
    { id: 310, name: '糖水铺', number: '10号档口', type: '饮品', status: '营业中', manager: '黄艳', phone: '138****3010' },
    { id: 311, name: '铁板鱿鱼', number: '11号档口', type: '特色小吃', status: '暂停营业', manager: '吴刚', phone: '138****3011' },
    { id: 312, name: '披萨意面', number: '12号档口', type: '快餐', status: '营业中', manager: '刘芳', phone: '138****3012' },
  ],
}

const STATUS_OPTIONS = [
  { value: '全部', label: '全部' },
  { value: '营业中', label: '营业中' },
  { value: '暂停营业', label: '暂停营业' },
]

const TYPE_OPTIONS = ['特色小吃', '面食', '饮品', '快餐', '点心', '粥品', '特色菜']

const STATUS_MAP = {
  '营业中': { color: 'success' },
  '暂停营业': { color: 'default' },
}

/* ========== 档口卡片 ========== */
function StallCard({ stall, onEdit, onDelete }) {
  const s = STATUS_MAP[stall.status] || STATUS_MAP['营业中']

  return (
    <Card className="stall-card" bodyStyle={{ padding: '16px 18px 14px' }}>
      <div className="stall-card-top">
        <span className="stall-number">{stall.number}</span>
        <h3 className="stall-name">{stall.name}</h3>
        <Tag color={s.color} className="stall-status-tag">{stall.status}</Tag>
      </div>

      <div className="stall-card-info">
        <div className="stall-info-row">
          <span className="stall-info-label">类型</span>
          <span className="stall-info-value">{stall.type}</span>
        </div>
        <div className="stall-info-row">
          <span className="stall-info-label">负责人</span>
          <span className="stall-info-value">{stall.manager}</span>
        </div>
      </div>

      <div className="stall-card-actions">
        <Button icon={<EditOutlined />} onClick={() => onEdit(stall)}>
          编辑
        </Button>
        <Popconfirm
          title={`确认删除 ${stall.name}？`}
          description="删除后无法恢复"
          onConfirm={() => onDelete(stall.id)}
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
  )
}

/* ========== 新增/编辑弹窗 ========== */
function StallFormModal({ stall, open, onClose, onSave, canteenId }) {
  const [form] = Form.useForm()
  const isEdit = !!stall

  useEffect(() => {
    if (open && stall) {
      form.resetFields()
      form.setFieldsValue(stall)
    } else if (open && !stall) {
      // 新增模式：预设食堂 ID
      form.resetFields()
      form.setFieldsValue({ canteenId: parseInt(canteenId) })
    }
  }, [open, stall?.id, form, canteenId])

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      onSave(isEdit ? stall.id : null, values)
    } catch { /* noop */ }
  }

  return (
    <Modal
      title={isEdit ? `编辑 ${stall?.name || ''}` : '新增档口'}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      width={520}
      okText="保存"
      cancelText="取消"
    >
      <Form form={form} layout="vertical">
        <Form.Item
          label="档口名称"
          name="name"
          rules={[{ required: true, message: '请输入档口名称' }]}
        >
          <Input placeholder="例：川味小炒" />
        </Form.Item>
        <Form.Item
          label="档口编号"
          name="number"
          rules={[{ required: true, message: '请输入档口编号' }]}
        >
          <Input placeholder="例：19号档口" />
        </Form.Item>
        <Form.Item
          label="类型"
          name="type"
          rules={[{ required: true, message: '请选择类型' }]}
        >
          <Select options={TYPE_OPTIONS.map(v => ({ value: v, label: v }))} placeholder="选择类型" />
        </Form.Item>
        <Form.Item
          label="状态"
          name="status"
          rules={[{ required: true, message: '请选择状态' }]}
        >
          <Select options={STATUS_OPTIONS.slice(1)} placeholder="选择状态" />
        </Form.Item>
        <Form.Item
          label="负责人"
          name="manager"
          rules={[{ required: true, message: '请输入负责人' }]}
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
      </Form>
    </Modal>
  )
}

/* ========== 主页面 ========== */
export default function StallManage() {
  const { canteenId } = useParams()
  const canteen = canteenData.find((c) => String(c.id) === canteenId)

  // 档口数据（本地可变状态）
  const [stalls, setStalls] = useState(() => {
    const id = parseInt(canteenId)
    const list = STALL_MOCK[id]
    return list ? [...list] : []
  })

  const [searchKeyword, setSearchKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('全部')

  // 弹窗
  const [addOpen, setAddOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editingStall, setEditingStall] = useState(null)

  // 下一个自增 ID（基于当前最大 ID）
  const getNextId = () => {
    const allIds = stalls.map(s => s.id)
    return Math.max(...allIds, 0) + 1
  }

  const filteredStalls = stalls.filter((s) => {
    const matchSearch = s.name.toLowerCase().includes(searchKeyword.toLowerCase())
    const matchStatus = statusFilter === '全部' || s.status === statusFilter
    return matchSearch && matchStatus
  })

  const total = stalls.length
  const openCount = stalls.filter((s) => s.status === '营业中').length
  const closedCount = stalls.filter((s) => s.status === '暂停营业').length

  const handleDelete = (id) => {
    setStalls((prev) => prev.filter((s) => s.id !== id))
    message.success('删除成功')
  }

  const handleEdit = (stall) => {
    setEditingStall(stall)
    setEditOpen(true)
  }

  // 新增
  const handleAdd = () => {
    setEditingStall(null)
    setAddOpen(true)
  }

  // 保存（新增或编辑共用）
  const handleSave = (id, values) => {
    if (id) {
      // 编辑
      setStalls((prev) => prev.map((s) => (s.id === id ? { ...s, ...values } : s)))
      setEditOpen(false)
      setEditingStall(null)
      message.success('保存成功')
    } else {
      // 新增
      const newStall = {
        id: getNextId(),
        ...values,
      }
      setStalls((prev) => [...prev, newStall])
      setAddOpen(false)
      message.success('新增成功')
    }
  }

  // 返回入口已由布局顶栏面包屑提供

  return (
    <div className="stall-page">
      {/* 顶部标题（返回入口由布局顶栏面包屑提供，这里保留食堂名动态上下文） */}
      <div className="stall-header">
        <div className="stall-header-left">
          <div className="stall-header-info">
            <h1 className="stall-title">{canteen?.name || '未知食堂'}</h1>
            <p className="stall-subtitle">管理该食堂下所有档口信息</p>
          </div>
        </div>
        <div className="stall-header-right">
          <Input
            placeholder="搜索档口名称"
            prefix={<SearchOutlined />}
            allowClear
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            style={{ width: 220 }}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增档口
          </Button>
        </div>
      </div>

      {/* 统计信息 */}
      <div className="stall-stats">
        <div className="stall-stat-item">
          <span className="stall-stat-label">档口总数</span>
          <span className="stall-stat-num">{total}</span>
        </div>
        <div className="stall-stat-item">
          <span className="stall-stat-label">营业中</span>
          <span className="stall-stat-num stall-stat-success">{openCount}</span>
        </div>
        <div className="stall-stat-item">
          <span className="stall-stat-label">暂停营业</span>
          <span className="stall-stat-num stall-stat-warning">{closedCount}</span>
        </div>
      </div>

      {/* 状态筛选 */}
      <div className="stall-filter-bar">
        {STATUS_OPTIONS.map((opt) => (
          <Button
            key={opt.value}
            type={statusFilter === opt.value ? 'primary' : 'default'}
            onClick={() => setStatusFilter(opt.value)}
          >
            {opt.label}
          </Button>
        ))}
      </div>

      {/* 档口卡片网格 */}
      {filteredStalls.length > 0 ? (
        <div className="stall-grid">
          {filteredStalls.map((stall) => (
            <StallCard
              key={stall.id}
              stall={stall}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />
          ))}
        </div>
      ) : (
        <Empty
          description={stalls.length === 0 ? '暂无档口数据' : '没有找到匹配的档口'}
          className="stall-empty"
        />
      )}

      {/* 新增/编辑弹窗 */}
      <StallFormModal
        stall={editingStall}
        open={editOpen}
        onClose={() => { setEditOpen(false); setEditingStall(null) }}
        onSave={handleSave}
        canteenId={canteenId}
      />
      <StallFormModal
        stall={null}
        open={addOpen}
        onClose={() => { setAddOpen(false); setEditingStall(null) }}
        onSave={handleSave}
        canteenId={canteenId}
      />
    </div>
  )
}
