import { useState, useEffect } from 'react'
import dayjs from 'dayjs'
import { message, Modal, Form, Input, Button, Table, Space, Popconfirm, Tag, Select, Card, Popover, Tooltip, Checkbox } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, FireFilled } from '@ant-design/icons'
import { getTopicList, createTopic, updateTopic, deleteTopic, batchDeleteTopics, getForumStatistics, getForumRules } from '../../../api/forum'
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
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [stats, setStats] = useState({ totalTopics: 0, activeTopics: 0, hotTopics: 0 })
  const [rules, setRules] = useState(null)

  const fetchTopics = async (params = {}) => {
    setLoading(true)
    try {
      const values = searchForm.getFieldsValue()
      const pageNum = params.pageNum ?? pagination.current
      const pageSize = params.pageSize ?? pagination.pageSize
      const res = await getTopicList({ pageNum, pageSize, ...values, ...params })
      if (res.code === 200) {
        const records = res.data?.records || res.data?.list || res.data || []
        const all = Array.isArray(records) ? records : []
        // 过滤掉系统内置的「热门」(id=1)、「最新」(id=2)话题——由系统自动收录管理，无需管理员操作
        const filtered = all.filter((t) => t.id !== 1 && t.id !== 2)
        setTopics(filtered)
        setPagination((prev) => ({ ...prev, current: pageNum, pageSize, total: Math.max((res.data?.total || all.length) - 2, 0) }))
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

  const fetchRules = async () => {
    try {
      const res = await getForumRules()
      if (res.code === 200) setRules(res.data)
    } catch (e) { /* ignore */ }
  }

  useEffect(() => { fetchTopics(); fetchStats(); fetchRules() }, [])

  const handleSearch = (values) => { fetchTopics({ pageNum: 1, ...values }) }
  const handleReset = () => { searchForm.resetFields(); fetchTopics({ pageNum: 1 }) }

  const handleCreate = () => {
    setModalTitle('创建话题')
    setEditingId(null)
    form.resetFields()
    form.setFieldsValue({ isHot: false, status: 'ACTIVE' })
    setModalVisible(true)
  }

  const handleEdit = (record) => {
    setModalTitle('编辑话题')
    setEditingId(record.id)
    form.setFieldsValue({
      topicName: record.topicName,
      isHot: record.isHot === 1,
      status: record.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE',
    })
    setModalVisible(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const data = { ...values, isHot: values.isHot ? 1 : 0, status: values.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE' }
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

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) return
    try {
      const res = await batchDeleteTopics(selectedRowKeys)
      if (res.code === 200) { message.success(`已批量删除 ${selectedRowKeys.length} 个话题`); setSelectedRowKeys([]); fetchTopics(); fetchStats() }
    } catch (error) { console.error('批量删除失败:', error) }
  }

  const renderRowPopover = (record) => (
    <div className="tm-row-pop">
      <div className="tm-row-pop-title">{record.topicName}</div>
      <div className="tm-row-pop-meta">
        <div><span className="tm-row-pop-label">帖子数</span>{record.postCount || 0}</div>
        <div><span className="tm-row-pop-label">状态</span><Tag color={record.status === 'ACTIVE' ? 'green' : 'default'}>{record.status === 'ACTIVE' ? '启用' : '禁用'}</Tag></div>
        <div><span className="tm-row-pop-label">创建时间</span>{formatTime(record.createTime)}</div>
      </div>
    </div>
  )

  const columns = [
    {
      title: '话题名称', dataIndex: 'topicName', width: 180,
      render: (text, record) => (
        <Popover content={renderRowPopover(record)} title="话题完整信息" trigger="hover" placement="bottomLeft" mouseEnterDelay={0.3}>
          <Space>
            {text}
            {record.isHot === 1 && <Tag color="red" icon={<FireFilled />}>热门</Tag>}
          </Space>
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

  const rowSelection = { selectedRowKeys, onChange: (keys) => setSelectedRowKeys(keys) }

  const statItems = [
    { label: '话题总数', value: stats.totalTopics, className: 'tm-header-stat-blue' },
    { label: '启用中', value: stats.activeTopics, className: 'tm-header-stat-green' },
    { label: '热门话题', value: stats.hotTopics, className: 'tm-header-stat-red' },
  ]

  return (
    <div className="tm-container">

      {/* 热门/最新话题收录标准说明 */}
      {rules && (
        <div className="tm-rules-bar">
          <span className="tm-rules-label">收录标准</span>
          <span className="tm-rules-item">{rules.hot}</span>
          <span className="tm-rules-item">{rules.latest}</span>
          <span className="tm-rules-tip">「热门」「最新」为系统内置话题，由系统自动收录管理，无需人工维护。</span>
        </div>
      )}

      {/* 顶部统计区（白色矩形） */}
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

      {selectedRowKeys.length > 0 && (
        <div className="tm-batch-bar">
          <span className="tm-batch-count">已选择 <strong>{selectedRowKeys.length}</strong> 个</span>
          <Popconfirm title={`确定删除选中的 ${selectedRowKeys.length} 个话题吗？`} onConfirm={handleBatchDelete} okText="确定" cancelText="取消">
            <Button type="primary" danger size="small" icon={<DeleteOutlined />}>批量删除</Button>
          </Popconfirm>
        </div>
      )}

      <Card className="tm-table-card" bodyStyle={{ padding: 0 }}>
        <Table
          rowSelection={rowSelection}
          columns={columns}
          dataSource={topics}
          rowKey="id"
          loading={loading}
          pagination={{ ...pagination, showSizeChanger: true, showTotal: (t) => `共 ${t} 条`, pageSizeOptions: ['10', '20', '50'] }}
          onChange={(pag) => fetchTopics({ pageNum: pag.current, pageSize: pag.pageSize })}
          size="middle"
        />
      </Card>

      <Modal title={modalTitle} open={modalVisible} onOk={handleSubmit} onCancel={() => setModalVisible(false)} width={520} okText="确定" cancelText="取消">
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="topicName" label="话题名称" rules={[{ required: true, message: '请输入话题名称' }]}>
            <Input placeholder="请输入话题名称" maxLength={20} showCount />
          </Form.Item>
          <Form.Item name="isHot" valuePropName="checked" style={{ marginBottom: 24 }}>
            <div className="tm-hot-row">
              <span className="tm-hot-label">热门话题</span>
              <Checkbox>设为热门话题</Checkbox>
            </div>
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
