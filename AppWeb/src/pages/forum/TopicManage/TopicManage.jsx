import { useState, useEffect } from 'react'
import { message, Modal, Form, Input, Button, Table, Space, Popconfirm, Tag, Select, Card } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, FireFilled, TagOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { getTopicList, createTopic, updateTopic, deleteTopic, getForumStatistics } from '../../../api/forum'
import './TopicManage.css'

function TopicManage() {
  const [topics, setTopics] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [modalTitle, setModalTitle] = useState('创建话题')
  const [editingId, setEditingId] = useState(null)
  const [form] = Form.useForm()
  const [searchForm] = Form.useForm()
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [stats, setStats] = useState({ totalTopics: 0, activeTopics: 0, hotTopics: 0 })

  const fetchTopics = async (params = {}) => {
    setLoading(true)
    try {
      const values = searchForm.getFieldsValue()
      const res = await getTopicList({ page: pagination.current, size: pagination.pageSize, ...values, ...params })
      if (res.code === 200) {
        const records = res.data?.records || res.data?.list || res.data || []
        setTopics(Array.isArray(records) ? records : [])
        setPagination({ ...pagination, total: res.data?.total || records.length })
      }
    } catch (error) { console.error('获取话题列表失败:', error) }
    finally { setLoading(false) }
  }

  const fetchStats = async () => {
    try {
      const res = await getForumStatistics()
      if (res.code === 200 && res.data) {
        setStats({
          totalTopics: res.data.totalTopics || 0,
          activeTopics: res.data.activeTopics || 0,
          hotTopics: res.data.hotTopics || 0,
        })
      }
    } catch { /* ignore */ }
  }

  useEffect(() => { fetchTopics(); fetchStats() }, [])

  const handleSearch = (values) => { setPagination({ ...pagination, current: 1 }); fetchTopics(values) }
  const handleReset = () => { searchForm.resetFields(); setPagination({ ...pagination, current: 1 }); fetchTopics() }

  const handleCreate = () => {
    setModalTitle('创建话题')
    setEditingId(null)
    form.resetFields()
    form.setFieldsValue({ isHot: 0, status: 'ACTIVE' })
    setModalVisible(true)
  }

  const handleEdit = (record) => {
    setModalTitle('编辑话题')
    setEditingId(record.id)
    form.setFieldsValue({
      topicName: record.topicName,
      topicIcon: record.topicIcon,
      description: record.description,
      isHot: record.isHot === 1 ? 1 : 0,
      status: record.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE',
    })
    setModalVisible(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const data = { ...values, isHot: values.isHot === 1 ? 1 : 0, status: values.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE' }
      let res
      if (editingId) { res = await updateTopic(editingId, data) } else { res = await createTopic(data) }
      if (res.code === 200) {
        message.success(editingId ? '更新成功' : '创建成功')
        setModalVisible(false)
        fetchTopics()
        fetchStats()
      }
    } catch (error) { console.error('提交失败:', error) }
  }

  const handleDelete = async (id) => {
    try {
      const res = await deleteTopic(id)
      if (res.code === 200) { message.success('删除成功'); fetchTopics(); fetchStats() }
    } catch (error) { console.error('删除失败:', error) }
  }

  const columns = [
    { title: '图标', dataIndex: 'topicIcon', width: 60, render: (icon) => <span style={{ fontSize: 22 }}>{icon || '📌'}</span> },
    {
      title: '话题名称', dataIndex: 'topicName', width: 180,
      render: (text, record) => (
        <Space>
          {text}
          {record.isHot === 1 && <Tag color="red" icon={<FireFilled />}>热门</Tag>}
        </Space>
      ),
    },
    { title: '描述', dataIndex: 'description', ellipsis: true, width: 220, render: (text) => text || '-' },
    { title: '帖子数', dataIndex: 'postCount', width: 100, sorter: (a, b) => (a.postCount || 0) - (b.postCount || 0) },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (s) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s === 'ACTIVE' ? '启用' : '禁用'}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
    {
      title: '操作', key: 'action', width: 160, fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除该话题吗？" onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
            <Button type="text" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const statItems = [
    { icon: <TagOutlined />, label: '话题总数', value: stats.totalTopics, color: '#7c3aed', bg: '#f5f3ff' },
    { icon: <CheckCircleOutlined />, label: '启用中', value: stats.activeTopics, color: '#059669', bg: '#ecfdf5' },
    { icon: <FireFilled />, label: '热门话题', value: stats.hotTopics, color: '#dc2626', bg: '#fef2f2' },
  ]

  return (
    <div className="tm-container">
      <div className="tm-bg-orbs" />

      <div className="tm-header">
        <div className="tm-header-left">
          <h1 className="tm-header-title">🏷️ 话题管理</h1>
          <p className="tm-header-desc">管理论坛话题分类，支持创建、编辑、设为热门</p>
        </div>
        <div className="tm-header-stats">
          <span className="tm-stat-badge tm-badge-purple">共 {stats.totalTopics} 个</span>
          <span className="tm-stat-badge tm-badge-active">启用 {stats.activeTopics}</span>
          <span className="tm-stat-badge tm-badge-hot">热门 {stats.hotTopics}</span>
        </div>
      </div>

      <div className="tm-stat-row">
        {statItems.map((s, i) => (
          <Card key={i} className="tm-stat-card" style={{ borderTop: `3px solid ${s.color}`, background: s.bg }}>
            <div className="tm-stat-card-icon" style={{ color: s.color }}>{s.icon}</div>
            <div className="tm-stat-card-value">{s.value}</div>
            <div className="tm-stat-card-label">{s.label}</div>
          </Card>
        ))}
      </div>

      <div className="tm-search-card">
        <Form form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="keyword">
            <Input placeholder="搜索话题名称" prefix={<SearchOutlined />} allowClear style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="状态" allowClear style={{ width: 100 }}>
              <Option value="ACTIVE">启用</Option>
              <Option value="INACTIVE">禁用</Option>
            </Select>
          </Form.Item>
          <Form.Item name="isHot">
            <Select placeholder="热门" allowClear style={{ width: 80 }}>
              <Option value={1}>是</Option>
              <Option value={0}>否</Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>搜索</Button>
            <Button onClick={handleReset} style={{ marginLeft: 8 }}>重置</Button>
          </Form.Item>
        </Form>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate} className="tm-add-btn">
          创建话题
        </Button>
      </div>

      <Card className="tm-table-card" bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={topics}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1400 }}
          pagination={{ ...pagination, showSizeChanger: true, showTotal: (t) => `共 ${t} 条`, pageSizeOptions: ['10', '20', '50'] }}
          onChange={(pag) => { setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize }); fetchTopics({ page: pag.current, size: pag.pageSize }) }}
          size="middle"
        />
      </Card>

      <Modal title={modalTitle} open={modalVisible} onOk={handleSubmit} onCancel={() => setModalVisible(false)} width={520} okText="确定" cancelText="取消">
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="topicName" label="话题名称" rules={[{ required: true, message: '请输入话题名称' }]}>
            <Input placeholder="请输入话题名称" maxLength={20} showCount />
          </Form.Item>
          <Form.Item name="topicIcon" label="话题图标（Emoji）" rules={[{ required: true, message: '请输入话题图标' }]}>
            <Input placeholder="如：📚" maxLength={4} />
          </Form.Item>
          <Form.Item name="description" label="话题描述" rules={[{ required: true, message: '请输入话题描述' }]}>
            <Input.TextArea rows={3} placeholder="请输入话题描述" maxLength={100} showCount />
          </Form.Item>
          <Form.Item name="isHot" label="热门话题" initialValue={0} rules={[{ required: true, message: '请选择' }]}>
            <Select placeholder="请选择">
              <Option value={1}>是</Option>
              <Option value={0}>否</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="ACTIVE" rules={[{ required: true, message: '请选择' }]}>
            <Select placeholder="请选择">
              <Option value="ACTIVE">启用</Option>
              <Option value="INACTIVE">禁用</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default TopicManage
