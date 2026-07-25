import { useState, useEffect } from 'react'
import { message, Drawer, Button, Table, Tag, Space, Popconfirm, Input, Select, Form, Card } from 'antd'
import {
  EyeOutlined, DeleteOutlined, SearchOutlined, PushpinOutlined, StarOutlined,
  CloseCircleOutlined, LikeOutlined, MessageOutlined,
  FolderOpenOutlined, InboxOutlined, CloudUploadOutlined, CheckCircleOutlined,
} from '@ant-design/icons'
import { getPostList, deletePost, batchDeletePosts, togglePostPin, togglePostHighlight, togglePostHidden, getForumStatistics } from '../../../api/forum'
import './PostManage.css'

const { Option } = Select

const statusMap = {
  'DRAFT': { text: '草稿', color: 'default' },
  'PUBLISHED': { text: '已发布', color: 'green' },
  'HIDDEN': { text: '已隐藏', color: 'orange' },
  'DELETED': { text: '已删除', color: 'red' },
}

const STAT_COLORS = ['#1677ff', '#52c41a', '#fa8c16', '#f5222d']

function PostManage() {
  const [posts, setPosts] = useState([])
  const [loading, setLoading] = useState(false)
  const [searchForm] = Form.useForm()
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [currentPost, setCurrentPost] = useState(null)
  const [stats, setStats] = useState({ totalPosts: 0, publishedPosts: 0, hiddenPosts: 0, deletedPosts: 0 })

  const fetchPosts = async (params = {}) => {
    setLoading(true)
    try {
      const res = await getPostList({
        page: pagination.current,
        size: pagination.pageSize,
        ...params,
      })
      if (res.code === 200) {
        const records = res.data?.records || res.data?.list || res.data || []
        setPosts(Array.isArray(records) ? records : [])
        setPagination({ ...pagination, total: res.data?.total || 0 })
      }
    } catch (error) {
      console.error('获取帖子列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const fetchStats = async () => {
    try {
      const res = await getForumStatistics()
      if (res.code === 200 && res.data) {
        setStats({
          totalPosts: res.data.totalPosts || 0,
          publishedPosts: res.data.publishedPosts || 0,
          hiddenPosts: res.data.hiddenPosts || 0,
          deletedPosts: res.data.deletedPosts || 0,
        })
      }
    } catch (e) { /* ignore */ }
  }

  useEffect(() => {
    fetchPosts()
    fetchStats()
  }, [])

  const handleSearch = (values) => {
    setPagination({ ...pagination, current: 1 })
    fetchPosts(values)
  }

  const handleReset = () => {
    searchForm.resetFields()
    setPagination({ ...pagination, current: 1 })
    fetchPosts()
  }

  const handleView = (record) => { setCurrentPost(record); setDrawerOpen(true) }

  const handleDelete = async (id) => {
    try {
      const res = await deletePost(id)
      if (res.code === 200) { message.success('删除成功'); fetchPosts(); fetchStats() }
    } catch (error) { console.error('删除失败:', error) }
  }

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) return
    try {
      const res = await batchDeletePosts(selectedRowKeys)
      if (res.code === 200) {
        message.success(`已批量删除 ${selectedRowKeys.length} 条帖子`)
        setSelectedRowKeys([])
        fetchPosts()
        fetchStats()
      }
    } catch (error) { console.error('批量删除失败:', error) }
  }

  const handleTogglePin = async (id) => {
    try {
      const res = await togglePostPin(id)
      if (res.code === 200) { message.success('操作成功'); fetchPosts() }
    } catch (error) { console.error('置顶操作失败:', error) }
  }

  const handleToggleHighlight = async (id) => {
    try {
      const res = await togglePostHighlight(id)
      if (res.code === 200) { message.success('操作成功'); fetchPosts() }
    } catch (error) { console.error('加精操作失败:', error) }
  }

  const handleToggleHidden = async (id) => {
    try {
      const res = await togglePostHidden(id)
      if (res.code === 200) { message.success('操作成功'); fetchPosts(); fetchStats() }
    } catch (error) { console.error('隐藏操作失败:', error) }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: '标题', dataIndex: 'title', ellipsis: true, width: 300,
      render: (text, record) => (
        <Space>
          {record.pinOrder > 0 && <PushpinOutlined style={{ color: '#faad14', fontSize: 16 }} title="置顶" />}
          {record.highlighted && <StarOutlined style={{ color: '#f5222d', fontSize: 16 }} title="加精" />}
          <span>{text}</span>
        </Space>
      ),
    },
    { title: '作者', dataIndex: 'username', width: 100, render: (u, r) => r.isAnonymous ? '匿名' : (u || '未知') },
    {
      title: '话题', dataIndex: 'topicName', width: 110,
      render: (_, r) => r.topic?.topicName || r.topicName || '-',
    },
    {
      title: '浏览/赞/评', key: 'stats', width: 140,
      render: (_, r) => (
        <Space size={6}>
          <span><FolderOpenOutlined /> {r.viewCount || 0}</span>
          <span><LikeOutlined style={{color:'#f5222d'}} /> {r.likeCount || 0}</span>
          <span><MessageOutlined /> {r.commentCount || 0}</span>
        </Space>
      ),
    },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: (s) => <Tag color={statusMap[s]?.color}>{statusMap[s]?.text}</Tag>,
    },
    { title: '发布时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作', key: 'action', width: 360, fixed: 'right',
      render: (_, record) => (
        <Space size={4} direction="vertical" style={{ minWidth: 140 }}>
          <Space size={4}>
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleView(record)}>查看</Button>
            <Button type="link" size="small" icon={<PushpinOutlined />} onClick={() => handleTogglePin(record.id)} title={record.pinOrder > 0 ? '取消置顶' : '置顶'}>
              {record.pinOrder > 0 ? '取消置顶' : '置顶'}
            </Button>
          </Space>
          <Space size={4}>
            <Button type="link" size="small" icon={<StarOutlined />} onClick={() => handleToggleHighlight(record.id)} title={record.highlighted ? '取消加精' : '加精'}>
              {record.highlighted ? '取消加精' : '加精'}
            </Button>
            <Button type="link" size="small" icon={<CloseCircleOutlined />} onClick={() => handleToggleHidden(record.id)} title={record.status === 'HIDDEN' ? '恢复' : '隐藏'}>
              {record.status === 'HIDDEN' ? '恢复' : '隐藏'}
            </Button>
            <Popconfirm title="确定删除该帖子吗？" onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Space>
        </Space>
      ),
    },
  ]

  const rowSelection = { selectedRowKeys, onChange: (keys) => setSelectedRowKeys(keys) }

  const statItems = [
    { icon: <CloudUploadOutlined />, label: '帖子总数', value: stats.totalPosts, color: STAT_COLORS[0], bg: '#e6f7ff' },
    { icon: <CheckCircleOutlined />, label: '已发布', value: stats.publishedPosts, color: STAT_COLORS[1], bg: '#f6ffed' },
    { icon: <InboxOutlined />, label: '已隐藏', value: stats.hiddenPosts, color: STAT_COLORS[2], bg: '#fff7e6' },
    { icon: <DeleteOutlined />, label: '已删除', value: stats.deletedPosts, color: STAT_COLORS[3], bg: '#fff1f0' },
  ]

  return (
    <div className="pm-container">
      {/* 背景装饰 */}
      <div className="pm-bg-shapes" />

      {/* 顶部标题区 */}
      <div className="pm-header">
        <div className="pm-header-left">
          <h1 className="pm-header-title">📋 帖子管理</h1>
          <p className="pm-header-desc">管理论坛所有帖子的发布、置顶、加精及隐藏操作</p>
        </div>
        <div className="pm-header-stats">
          <span className="pm-stat-badge pm-badge-blue">共 {stats.totalPosts} 篇</span>
          <span className="pm-stat-badge pm-badge-green">发布 {stats.publishedPosts}</span>
          <span className="pm-stat-badge pm-badge-orange">隐藏 {stats.hiddenPosts}</span>
        </div>
      </div>

      {/* 统计卡片行 */}
      <div className="pm-stat-row">
        {statItems.map((s, i) => (
          <Card key={i} className="pm-stat-card" style={{ borderTop: `3px solid ${s.color}`, background: s.bg }}>
            <div className="pm-stat-card-icon" style={{ color: s.color }}>{s.icon}</div>
            <div className="pm-stat-card-value">{s.value}</div>
            <div className="pm-stat-card-label">{s.label}</div>
          </Card>
        ))}
      </div>

      {/* 搜索栏 */}
      <div className="pm-search-card">
        <Form form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="keyword">
            <Input placeholder="搜索帖子标题" prefix={<SearchOutlined />} allowClear style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="选择状态" allowClear style={{ width: 130 }}>
              {Object.entries(statusMap).map(([key, val]) => (<Option key={key} value={key}>{val.text}</Option>))}
            </Select>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>搜索</Button>
            <Button onClick={handleReset} style={{ marginLeft: 8 }}>重置</Button>
          </Form.Item>
        </Form>
      </div>

      {/* 批量操作栏 */}
      {selectedRowKeys.length > 0 && (
        <div className="pm-batch-bar">
          <span className="pm-batch-count">已选择 <strong>{selectedRowKeys.length}</strong> 条</span>
          <Popconfirm title={`确定删除选中的 ${selectedRowKeys.length} 条帖子吗？`} onConfirm={handleBatchDelete} okText="确定" cancelText="取消">
            <Button type="primary" danger size="small" icon={<DeleteOutlined />}>批量删除</Button>
          </Popconfirm>
        </div>
      )}

      {/* 帖子表格 */}
      <Card className="pm-table-card" bodyStyle={{ padding: 0 }}>
        <Table
          rowSelection={rowSelection}
          columns={columns}
          dataSource={posts}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination, showSizeChanger: true, showTotal: (t) => `共 ${t} 条`,
            pageSizeOptions: ['10', '20', '50'],
          }}
          onChange={(pag) => { setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize }); fetchPosts({ page: pag.current, size: pag.pageSize }) }}
          scroll={{ x: 1500 }}
          size="middle"
          rowClassName={(record) => record.pinOrder > 0 ? 'pm-row-pin' : record.highlighted ? 'pm-row-highlight' : ''}
        />
      </Card>

      {/* 详情抽屉 */}
      <Drawer title={
        <Space>
          <PushpinOutlined style={{ color: '#faad14' }} />
          <span>帖子详情</span>
        </Space>
      } placement="right" width={640} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        {currentPost && (
          <div className="pm-drawer">
            <div className="pm-drawer-section">
              <h3 className="pm-drawer-title">{currentPost.title}</h3>
              <Space wrap className="pm-drawer-tags">
                <Tag color={statusMap[currentPost.status]?.color}>{statusMap[currentPost.status]?.text}</Tag>
                {currentPost.pinOrder > 0 && <Tag color="gold">📌 置顶</Tag>}
                {currentPost.highlighted && <Tag color="red">⭐ 加精</Tag>}
              </Space>
            </div>
            <div className="pm-drawer-info">
              <div className="pm-info-row"><span className="pm-info-label">作者</span><span>{currentPost.username || '未知'}</span></div>
              <div className="pm-info-row"><span className="pm-info-label">话题</span><span>{currentPost.topicName || '无'}</span></div>
              <div className="pm-info-row"><span className="pm-info-label">浏览量</span><span><FolderOpenOutlined /> {currentPost.viewCount}</span></div>
              <div className="pm-info-row"><span className="pm-info-label">点赞数</span><span><LikeOutlined style={{color:'#f5222d'}} /> {currentPost.likeCount}</span></div>
              <div className="pm-info-row"><span className="pm-info-label">评论数</span><span><MessageOutlined /> {currentPost.commentCount}</span></div>
              <div className="pm-info-row"><span className="pm-info-label">发布时间</span><span>{currentPost.createTime}</span></div>
            </div>
            <div className="pm-drawer-content">
              <h4 className="pm-drawer-subtitle">📝 正文内容</h4>
              <div className="pm-content-box">{currentPost.content}</div>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  )
}

export default PostManage
