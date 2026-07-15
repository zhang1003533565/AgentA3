import { useState, useEffect } from 'react'
import { message, Drawer, Button, Table, Tag, Space, Popconfirm, Input, Select, Form, Row, Col, Card } from 'antd'
import { EyeOutlined, DeleteOutlined, SearchOutlined, PinOutlined, StarOutlined, HideOutlined, UnorderedListOutlined, ThunderboltOutlined, MessageOutlined } from '@ant-design/icons'
import { getPostList, deletePost, batchDeletePosts, togglePostPin, togglePostHighlight, togglePostHidden } from '../../../api/forum'
import './PostManage.css'

const { Option } = Select

// 帖子状态映射
const statusMap = {
  'DRAFT': { text: '草稿', color: 'default' },
  'PUBLISHED': { text: '已发布', color: 'green' },
  'HIDDEN': { text: '已隐藏', color: 'orange' },
  'DELETED': { text: '已删除', color: 'red' }
}

function PostManage() {
  const [posts, setPosts] = useState([])
  const [loading, setLoading] = useState(false)
  const [searchForm] = Form.useForm()
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [currentPost, setCurrentPost] = useState(null)
  const [stats, setStats] = useState({ total: 0, published: 0, hidden: 0, today: 0 })

  // 获取帖子列表
  const fetchPosts = async (params = {}) => {
    setLoading(true)
    try {
      const res = await getPostList({
        page: pagination.current,
        size: pagination.pageSize,
        ...params
      })
      if (res.code === 200) {
        const records = res.data?.records || res.data?.list || res.data || []
        setPosts(Array.isArray(records) ? records : [])
        setPagination({
          ...pagination,
          total: res.data?.total || 0
        })
      }
    } catch (error) {
      console.error('获取帖子列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  // 获取统计
  const fetchStats = async () => {
    try {
      const res = await getPostList({ page: 1, size: 1 })
      if (res.code === 200) {
        const total = res.data?.total || 0
        setStats(prev => ({ ...prev, total }))
      }
    } catch (e) { /* ignore */ }
  }

  useEffect(() => {
    fetchPosts()
    fetchStats()
  }, [])

  // 搜索
  const handleSearch = (values) => {
    setPagination({ ...pagination, current: 1 })
    fetchPosts(values)
  }

  // 重置搜索
  const handleReset = () => {
    searchForm.resetFields()
    setPagination({ ...pagination, current: 1 })
    fetchPosts()
  }

  // 查看详情
  const handleView = (record) => {
    setCurrentPost(record)
    setDrawerOpen(true)
  }

  // 删除帖子
  const handleDelete = async (id) => {
    try {
      const res = await deletePost(id)
      if (res.code === 200) {
        message.success('删除成功')
        fetchPosts()
        fetchStats()
      }
    } catch (error) {
      console.error('删除失败:', error)
    }
  }

  // 批量删除
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
    } catch (error) {
      console.error('批量删除失败:', error)
    }
  }

  // 置顶/取消置顶
  const handleTogglePin = async (id) => {
    try {
      const res = await togglePostPin(id)
      if (res.code === 200) {
        message.success('操作成功')
        fetchPosts()
      }
    } catch (error) {
      console.error('置顶操作失败:', error)
    }
  }

  // 加精/取消加精
  const handleToggleHighlight = async (id) => {
    try {
      const res = await togglePostHighlight(id)
      if (res.code === 200) {
        message.success('操作成功')
        fetchPosts()
      }
    } catch (error) {
      console.error('加精操作失败:', error)
    }
  }

  // 隐藏/恢复
  const handleToggleHidden = async (id) => {
    try {
      const res = await togglePostHidden(id)
      if (res.code === 200) {
        message.success('操作成功')
        fetchPosts()
        fetchStats()
      }
    } catch (error) {
      console.error('隐藏操作失败:', error)
    }
  }

  // 表格列定义
  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60
    },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      width: 250,
      render: (text, record) => (
        <Space>
          {record.pinOrder > 0 && <PinOutlined style={{ color: '#faad14' }} />}
          {record.highlighted && <StarOutlined style={{ color: '#f5222d' }} />}
          {text}
        </Space>
      )
    },
    {
      title: '作者',
      dataIndex: 'username',
      width: 100,
      render: (username, record) => record.isAnonymous ? '匿名用户' : (username || '未知')
    },
    {
      title: '话题',
      dataIndex: 'topicName',
      width: 100,
      render: (topicName, record) => record.topic?.topicName || record.topicName || '无'
    },
    {
      title: '浏览/点赞/评论',
      key: 'stats',
      width: 140,
      render: (_, record) => (
        <Space size={4}>
          <UnorderedListOutlined />{record.viewCount}
          <ThunderboltOutlined />{record.likeCount}
          <MessageOutlined />{record.commentCount}
        </Space>
      )
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (status) => (
        <Tag color={statusMap[status]?.color}>
          {statusMap[status]?.text}
        </Tag>
      )
    },
    {
      title: '发布时间',
      dataIndex: 'createTime',
      width: 160
    },
    {
      title: '操作',
      key: 'action',
      width: 320,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small" direction="vertical" size={0}>
          <Space size="small">
            <Button type="text" icon={<EyeOutlined />} onClick={() => handleView(record)}>查看</Button>
            <Button type="text" icon={<PinOutlined />} onClick={() => handleTogglePin(record.id)} title={record.pinOrder > 0 ? '取消置顶' : '置顶'}>
              {record.pinOrder > 0 ? '取消置顶' : '置顶'}
            </Button>
          </Space>
          <Space size="small">
            <Button type="text" icon={<StarOutlined />} onClick={() => handleToggleHighlight(record.id)} title={record.highlighted ? '取消加精' : '加精'}>
              {record.highlighted ? '取消加精' : '加精'}
            </Button>
            <Button type="text" icon={<HideOutlined />} onClick={() => handleToggleHidden(record.id)} title={record.status === 'HIDDEN' ? '恢复' : '隐藏'}>
              {record.status === 'HIDDEN' ? '恢复' : '隐藏'}
            </Button>
            <Popconfirm
              title="确定删除该帖子吗？"
              onConfirm={() => handleDelete(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button type="text" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Space>
        </Space>
      )
    }
  ]

  const rowSelection = {
    selectedRowKeys,
    onChange: (keys) => setSelectedRowKeys(keys)
  }

  return (
    <div className="post-manage-container">
      {/* 统计卡片 */}
      <div className="stat-cards">
        <Card size="small" className="stat-card">
          <div className="stat-value">{stats.total}</div>
          <div className="stat-label">帖子总数</div>
        </Card>
        <Card size="small" className="stat-card stat-green">
          <div className="stat-value">--</div>
          <div className="stat-label">已发布</div>
        </Card>
        <Card size="small" className="stat-card stat-orange">
          <div className="stat-value">--</div>
          <div className="stat-label">已隐藏</div>
        </Card>
        <Card size="small" className="stat-card stat-red">
          <div className="stat-value">{selectedRowKeys.length}</div>
          <div className="stat-label">已选中</div>
        </Card>
      </div>

      {/* 主内容 */}
      <main className="manage-main">
        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="keyword">
              <Input placeholder="搜索帖子标题" prefix={<SearchOutlined />} allowClear style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="status">
              <Select placeholder="选择状态" allowClear style={{ width: 120 }}>
                {Object.entries(statusMap).map(([key, value]) => (
                  <Option key={key} value={key}>{value.text}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit">搜索</Button>
              <Button onClick={handleReset} style={{ marginLeft: 8 }}>重置</Button>
            </Form.Item>
          </Form>
        </div>

        {/* 批量操作栏 */}
        {selectedRowKeys.length > 0 && (
          <div className="batch-bar">
            <span>已选择 {selectedRowKeys.length} 条</span>
            <Popconfirm
              title={`确定删除选中的 ${selectedRowKeys.length} 条帖子吗？`}
              onConfirm={handleBatchDelete}
              okText="确定"
              cancelText="取消"
            >
              <Button type="primary" danger size="small">批量删除</Button>
            </Popconfirm>
          </div>
        )}

        {/* 帖子列表 */}
        <Table
          rowSelection={rowSelection}
          columns={columns}
          dataSource={posts}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            pageSizeOptions: ['10', '20', '50']
          }}
          onChange={(pag) => {
            setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize })
            fetchPosts({ page: pag.current, size: pag.pageSize })
          }}
          scroll={{ x: 1400 }}
        />
      </main>

      {/* 详情抽屉 */}
      <Drawer
        title="帖子详情"
        placement="right"
        width={600}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        {currentPost && (
          <div className="post-detail-drawer">
            <p><strong>标题：</strong>{currentPost.title}</p>
            <p><strong>作者：</strong>{currentPost.username || '未知'}</p>
            <p><strong>话题：</strong>{currentPost.topicName || '无'}</p>
            <p><strong>浏览量：</strong>{currentPost.viewCount}</p>
            <p><strong>点赞数：</strong>{currentPost.likeCount}</p>
            <p><strong>评论数：</strong>{currentPost.commentCount}</p>
            <p><strong>状态：</strong>
              <Tag color={statusMap[currentPost.status]?.color}>
                {statusMap[currentPost.status]?.text}
              </Tag>
            </p>
            {currentPost.pinOrder > 0 && <p><strong>置顶：</strong><Tag color="gold">是</Tag></p>}
            {currentPost.highlighted && <p><strong>加精：</strong><Tag color="red">是</Tag></p>}
            <p><strong>发布时间：</strong>{currentPost.createTime}</p>
            <p><strong>内容：</strong></p>
            <div className="post-content">{currentPost.content}</div>
          </div>
        )}
      </Drawer>
    </div>
  )
}

export default PostManage
