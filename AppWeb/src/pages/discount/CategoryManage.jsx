import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Empty,
  Form,
  Input,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tag,
  message,
} from 'antd'
import SidePanel from '../../components/SidePanel/SidePanel'
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import {
  getMerchantCategoryList,
  createMerchantCategory,
  updateMerchantCategory,
  deleteMerchantCategory,
} from '../../api/merchant'
import './CategoryManage.css'

export default function CategoryManage() {
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getMerchantCategoryList()
      let rows = Array.isArray(res.data) ? res.data : []
      if (keyword) {
        rows = rows.filter((r) =>
          r.categoryName && r.categoryName.includes(keyword),
        )
      }
      setData(rows)
    } catch {
      message.error('分类数据加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword])

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
      await deleteMerchantCategory(id)
      message.success('分类已删除')
      fetchData()
    } catch {
      message.error('删除失败')
    }
  }

  const enabledCount = data.filter((r) => r.status === 1).length
  const disabledCount = data.filter((r) => r.status === 0).length

  const columns = [
    {
      title: '编号',
      dataIndex: 'id',
      width: 80,
      render: (id) => <span className="category-id">#{id}</span>,
    },
    {
      title: '分类名称',
      dataIndex: 'categoryName',
      render: (text) => <span className="category-name">{text}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status) =>
        status === 1 ? (
          <Tag color="success">启用</Tag>
        ) : (
          <Tag color="error">停用</Tag>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定删除此分类吗？"
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
          <h2>分类管理</h2>
          <p>维护校园特惠商家分类，用于商家归类与前端筛选展示</p>
        </div>
        <div className="discount-header-right">
          <Input
            placeholder="搜索分类名称"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            allowClear
            style={{ width: 240 }}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增分类
          </Button>
        </div>
      </header>

      <div className="discount-stats">
        <button type="button" className="stat-item active">
          <span className="stat-label">分类总数</span>
          <span className="stat-number">{data.length}</span>
        </button>
        <button type="button" className="stat-item">
          <span className="stat-label">已启用</span>
          <span className="stat-number">{enabledCount}</span>
        </button>
        <button type="button" className="stat-item">
          <span className="stat-label">已停用</span>
          <span className="stat-number">{disabledCount}</span>
        </button>
      </div>

      <div className="discount-table-card">
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={data}
            rowKey="id"
            scroll={{ x: 600 }}
            locale={{ emptyText: <Empty description="暂无分类数据" /> }}
            pagination={false}
          />
        </Spin>
        <div className="table-footer">
          <span>共 {data.length} 条分类</span>
        </div>
      </div>

      <CategoryEditor
        open={editorOpen}
        record={editingRecord}
        saving={saving}
        onClose={() => setEditorOpen(false)}
        onSave={async (values) => {
          setSaving(true)
          try {
            if (editingRecord) {
              await updateMerchantCategory(editingRecord.id, values)
              message.success('分类已更新')
            } else {
              await createMerchantCategory(values)
              message.success('分类已创建')
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

function CategoryEditor({ open, record, saving, onClose, onSave }) {
  const [form] = Form.useForm()
  const isEdit = Boolean(record)

  useEffect(() => {
    if (!open) return
    form.setFieldsValue({
      categoryName: record?.categoryName || '',
      status: record?.status ?? 1,
    })
  }, [record, form, open])

  const submit = async () => {
    try {
      const values = await form.validateFields()
      onSave(values)
    } catch {
      // Ant Design shows validation errors
    }
  }

  return (
    <SidePanel
      title={isEdit ? '编辑分类' : '新增分类'}
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
          label="分类名称"
          name="categoryName"
          rules={[{ required: true, message: '请输入分类名称' }]}
        >
          <Input placeholder="例如：餐饮美食" />
        </Form.Item>
        <Form.Item label="状态" name="status">
          <Space size={12}>
            <Button
              type={form.getFieldValue('status') === 1 ? 'primary' : 'default'}
              onClick={() => form.setFieldValue('status', 1)}
            >
              启用
            </Button>
            <Button
              type={form.getFieldValue('status') === 0 ? 'primary' : 'default'}
              onClick={() => form.setFieldValue('status', 0)}
            >
              停用
            </Button>
          </Space>
        </Form.Item>
      </Form>
    </SidePanel>
  )
}
