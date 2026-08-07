import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Upload,
  message,
} from 'antd'
import {
  DeleteOutlined,
  EditOutlined,
  PhoneOutlined,
  PlusOutlined,
  SearchOutlined,
  ShopOutlined,
  SwapOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import SidePanel from '../../components/SidePanel/SidePanel'
import {
  getMerchantList,
  createMerchant,
  updateMerchant,
  deleteMerchant,
  getMerchantCategoryList,
  updateMerchantStatus,
} from '../../api/merchant'
import './MerchantManage.css'

const STATUS_OPTIONS = [
  { value: 1, label: '营业中' },
  { value: 0, label: '休息中' },
]

const CATEGORY_COLORS = ['blue', 'cyan', 'geekblue', 'orange', 'purple']

export default function MerchantManage() {
  const navigate = useNavigate()
  const [data, setData] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 })
  const [keyword, setKeyword] = useState('')
  const [categoryOptions, setCategoryOptions] = useState([])
  const [categoryFilter, setCategoryFilter] = useState(undefined)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState(null)

  const fetchCategories = useCallback(async () => {
    try {
      const res = await getMerchantCategoryList()
      setCategoryOptions((Array.isArray(res.data) ? res.data : []).map((c) => ({
        value: c.id,
        label: c.categoryName,
      })))
    } catch { /* non-critical */ }
  }, [])

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getMerchantList({
        page: pagination.current,
        size: pagination.pageSize,
        keyword: keyword || undefined,
        categoryId: categoryFilter,
      })
      setData(res.data?.records || [])
      setTotal(res.data?.total || 0)
    } catch {
      message.error('商家数据加载失败')
    } finally {
      setLoading(false)
    }
  }, [pagination, keyword, categoryFilter])

  useEffect(() => { fetchCategories() }, [fetchCategories])
  useEffect(() => { fetchData() }, [fetchData])

  const openCreate = () => {
    setEditingRecord(null)
    setEditorOpen(true)
  }

  const openEdit = (record) => {
    setEditingRecord(record)
    setEditorOpen(true)
  }

  const handleDelete = async (id) => {
    try {
      await deleteMerchant(id)
      message.success('商家已删除')
      fetchData()
    } catch {
      message.error('删除失败')
    }
  }

  const handleStatusToggle = async (record) => {
    const nextStatus = record.status === 1 ? 0 : 1
    const label = nextStatus === 1 ? '营业中' : '休息中'
    Modal.confirm({
      title: `确定将状态改为「${label}」吗？`,
      onOk: async () => {
        try {
          await updateMerchantStatus(record.id, { status: nextStatus })
          message.success(`状态已更新为「${label}」`)
          fetchData()
        } catch {
          message.error('状态更新失败')
        }
      },
    })
  }

  const openCount = data.filter((r) => r.status === 1).length
  const closedCount = data.filter((r) => r.status === 0).length

  const columns = [
    {
      title: '商家名称',
      dataIndex: 'merchantName',
      width: 260,
      render: (text, record) => (
        <div className="merchant-name-cell">
          <div className="merchant-icon"><ShopOutlined /></div>
          <div>
            <a
              className="merchant-name merchant-name--link"
              onClick={() => navigate(`/discount/activity?merchantId=${record.id}&merchantName=${encodeURIComponent(text)}`)}
            >
              {text}
            </a>
            {record.address && (
              <div className="merchant-address">{record.address}</div>
            )}
          </div>
        </div>
      ),
    },
    {
      title: '分类',
      dataIndex: 'categoryName',
      width: 120,
      render: (text, _, idx) =>
        text ? (
          <Tag color={CATEGORY_COLORS[idx % CATEGORY_COLORS.length]}>{text}</Tag>
        ) : '-',
    },
    {
      title: '联系电话',
      dataIndex: 'contactPhone',
      width: 140,
      render: (text) => text || '-',
    },
    {
      title: '地址',
      dataIndex: 'address',
      ellipsis: true,
      render: (text) => text || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status) =>
        status === 1 ? (
          <Tag color="success">营业中</Tag>
        ) : (
          <Tag>休息中</Tag>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="text"
            size="small"
            icon={<SwapOutlined />}
            onClick={() => handleStatusToggle(record)}
          >
            状态
          </Button>
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定删除此商家吗？"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button type="text" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div className="discount-manage-page">
      <header className="discount-header">
        <div className="discount-header-left">
          <h2>商家管理</h2>
          <p>管理参与校园特惠活动的商家信息</p>
        </div>
        <div className="discount-header-right">
          <Input
            placeholder="搜索商家名称"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPagination((p) => ({ ...p, current: 1 }))
            }}
            onPressEnter={fetchData}
            allowClear
            style={{ width: 240 }}
          />
          <Select
            placeholder="全部分类"
            value={categoryFilter}
            onChange={(v) => {
              setCategoryFilter(v)
              setPagination((p) => ({ ...p, current: 1 }))
            }}
            allowClear
            style={{ width: 160 }}
            options={categoryOptions}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增商家
          </Button>
        </div>
      </header>

      <div className="discount-stats">
        <button type="button" className="stat-item active">
          <span className="stat-label">商家总数</span>
          <span className="stat-number">{total}</span>
        </button>
        <button type="button" className="stat-item">
          <span className="stat-label">营业中</span>
          <span className="stat-number">{openCount}</span>
        </button>
        <button type="button" className="stat-item">
          <span className="stat-label">休息中</span>
          <span className="stat-number">{closedCount}</span>
        </button>
      </div>

      <div className="discount-table-card">
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={data}
            rowKey="id"
            scroll={{ x: 900 }}
            locale={{ emptyText: <Empty description="暂无商家数据" /> }}
            pagination={{
              current: pagination.current,
              pageSize: pagination.pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 条`,
              onChange: (page, pageSize) =>
                setPagination({ current: page, pageSize }),
            }}
          />
        </Spin>
      </div>

      <MerchantEditor
        open={editorOpen}
        record={editingRecord}
        categoryOptions={categoryOptions}
        saving={saving}
        onClose={() => setEditorOpen(false)}
        onSave={async (values) => {
          setSaving(true)
          try {
            if (editingRecord) {
              await updateMerchant(editingRecord.id, values)
              message.success('商家信息已更新')
            } else {
              await createMerchant(values)
              message.success('商家已创建')
            }
            setEditorOpen(false)
            fetchData()
          } catch (e) {
            message.error(e?.message || '保存失败')
          } finally {
            setSaving(false)
          }
        }}
      />
    </div>
  )
}

function MerchantEditor({ open, record, categoryOptions, saving, onClose, onSave }) {
  const [form] = Form.useForm()
  const isEdit = Boolean(record)

  useEffect(() => {
    if (!open) return
    form.setFieldsValue({
      merchantName: record?.merchantName || '',
      categoryId: record?.categoryId || undefined,
      description: record?.description || '',
      address: record?.address || '',
      contactName: record?.contactName || '',
      contactPhone: record?.contactPhone || '',
      businessHours: record?.businessHours || '',
      status: record?.status ?? 1,
    })
  }, [record, form, open])

  const submit = async () => {
    try {
      const values = await form.validateFields()
      onSave({ ...values, categoryId: Number(values.categoryId) })
    } catch {
      // Ant Design shows validation errors on fields
    }
  }

  return (
    <SidePanel
      title={isEdit ? '编辑商家' : '新增商家'}
      open={open}
      onClose={onClose}
      destroyOnHidden
      footer={
        <>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={saving} onClick={submit}>
            保存
          </Button>
        </>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item
          label="商家名称"
          name="merchantName"
          rules={[{ required: true, message: '请输入商家名称' }]}
        >
          <Input placeholder="请输入商家全称" />
        </Form.Item>
        <Form.Item
          label="商家分类"
          name="categoryId"
          rules={[{ required: true, message: '请选择分类' }]}
        >
          <Select placeholder="选择经营类目" options={categoryOptions} />
        </Form.Item>
        <Form.Item label="联系电话" name="contactPhone">
          <Input placeholder="请输入有效联系方式" />
        </Form.Item>
        <Form.Item label="营业时间" name="businessHours">
          <Input placeholder="例如：09:00-21:00" />
        </Form.Item>
        <Form.Item label="联系人" name="contactName">
          <Input placeholder="联系人姓名" />
        </Form.Item>
        <Form.Item label="详细地址" name="address">
          <Input placeholder="请输入商家具体地址" />
        </Form.Item>
        <Form.Item label="营业状态" name="status">
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item label="商家介绍" name="description">
          <Input.TextArea rows={4} placeholder="简要介绍商家特色、经营范围" />
        </Form.Item>
      </Form>
    </SidePanel>
  )
}
