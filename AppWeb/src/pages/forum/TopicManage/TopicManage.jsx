import { useState, useEffect } from 'react'
import dayjs from 'dayjs'
import { message, Modal, Form, Input, Button, Table, Space, Popconfirm, Tag, Select, Card, Popover, Tooltip } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, FireFilled } from '@ant-design/icons'
import { getTopicList, createTopic, updateTopic, deleteTopic, getForumStatistics } from '../../../api/forum'
import './TopicManage.css'

const { Option } = Select

const formatTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')

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
    } catch (e) { /* ignore */ }
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

  const renderRowPopover = (record) => (
    <div className="tm-row-pop">
      <div className="tm-row-pop-title">{record.topicIcon || '📌'} {record.topicName}</div>
      <div className="tm-row-pop-desc">{record.description || '-'}</div>
      <div className="tm-row-pop-meta">
        <div><span className="tm-row-pop-label">帖子数</span>{record.postCount || 0}</div>
        <div><span className="tm-row-pop-label">状态</span><Tag color={record.status === 'ACTIVE' ? 'green' : 'default'}>{record.status === 'ACTIVE' ? '启用' : '禁用'}</Tag></div>
        <div><span className="tm-row-pop-label">创建时间</span>{formatTime(record.createTime)}</div>
      </div>
    </div>
  )

  const columns = [
    { title: '图标', dataIndex: 'topicIcon', width: 60, render: (icon) => <span style={{ fontSize: 22 }}>{icon || '📌'}</span> },
    {
      title: '话题名称', dataIndex: 'topicName', width: 150,
      render: (text, record) => (
        <Space>
          {text}
          {record.isHot === 1 && <Tag color="red" icon={<FireFilled />}>热门</Tag>}
        </Space>
      ),
    },
    {
      title: '描述', dataIndex: 'description', ellipsis: true, width: 180,
      render: (text, record) => (
        <Popover content={renderRowPopover(record)} title="话题完整信息" trigger="hover" placement="bottomLeft" mouseEnterDelay={0.3}>
          <span>{text || '-'}</span>
        </Popover>
      ),
    },
    { title: '帖子数', dataIndex: 'postCount', width: 80, sorter: (a, b) => (a.postCount || 0) - (b.postCount || 0) },
    {
      title: '状态', dataIndex: 'status', width: 80,
      render: (s) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s === 'ACTIVE' ? '启用' : '禁用'}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', width: 150, render: (t) => formatTime(t) },
    {
      title: '操作', key: 'action', width: 100,
      render: (_, record) => (
        <Space size={0}>
          <Tooltip title="编辑">
            <Button type="text" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)} />
          </Tooltip>
          <Popconfirm title="确定删除该话题吗？" onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
            <Tooltip title="删除">
              <Button type="text" size="small" danger icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const statItems = [
    { label: '话题总数', value: stats.totalTopics, className: 'tm-header-stat-blue' },
    { label: '启用中', value: stats.activeTopics, className: 'tm-header-stat-green' },
    { label: '热门话题', value: stats.hotTopics, className: 'tm-header-stat-red' },
  ]

  return (
    <div className="tm-container">

      {/* 顶部统计区（白色矩形，仅保留统计） */}
      <div className="tm-header">
        <div className="tm-header-stats">
          {statItems.map((s) => (
            <div key={s.label} className={`tm-header-stat ${s.className}`}>
              <span className="tm-header-stat-value">{s.value}</span>
              <span className="tm-header-stat-label">{s.label}</span>
            </div>
          ))}
        </div>
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
