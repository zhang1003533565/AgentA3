import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { message, Modal, Button, Table, Tag, Space, Popconfirm, Input, Select, Form } from 'antd'
import { EyeOutlined, DeleteOutlined, SearchOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons'
import { getUserInfo, clearAuth } from '../../../utils/storage'
import './PostManage.css'

const { Option } = Select
const { TextArea } = Input

// 帖子状态映射
const statusMap = {
  0: { text: '待审核', color: 'orange' },
  1: { text: '已发布', color: 'green' },
  2: { text: '已下架', color: 'default' },
  3: { text: '已拒绝', color: 'red' }
}

// 模拟数据
const mockPosts = [
  {
    id: 1,
    title: '关于期末考试复习的一些建议',
    content: '马上就要期末考试了，大家有什么好的复习方法吗？我来分享一些我的经验...',
    authorName: '张三',
    authorId: 1,
    topicName: '学习交流',
    isAnonymous: 0,
    viewCount: 256,
    likeCount: 45,
    commentCount: 12,
    status: 1,
    createTime: '2026-03-15 14:30:00'
  },
  {
    id: 2,
    title: '寻找羽毛球搭子',
    content: '有没有喜欢打羽毛球的同学？周末一起打球呀！',
    authorName: '匿名用户',
    authorId: 2,
    topicName: '运动健身',
    isAnonymous: 1,
    viewCount: 128,
    likeCount: 23,
    commentCount: 8,
    status: 1,
    createTime: '2026-03-15 10:20:00'
  },
  {
    id: 3,
    title: '二手自行车转让',
    content: '毕业了，转让一辆九成新自行车，价格面议...',
    authorName: '李四',
    authorId: 3,
    topicName: '二手交易',
    isAnonymous: 0,
    viewCount: 89,
    likeCount: 15,
    commentCount: 5,
    status: 0,
    createTime: '2026-03-14 16:45:00'
  },
  {
    id: 4,
    title: '食堂新开档口推荐',
    content: '二食堂新开了一家麻辣烫，味道超级棒！推荐大家去尝尝...',
    authorName: '王五',
    authorId: 4,
    topicName: '美食推荐',
    isAnonymous: 0,
    viewCount: 512,
    likeCount: 89,
    commentCount: 34,
    status: 1,
    createTime: '2026-03-14 12:00:00'
  }
]

function PostManage() {
  const navigate = useNavigate()
  const [userInfo, setUserInfo] = useState(null)
  const [posts, setPosts] = useState([])
  const [loading, setLoading] = useState(false)
  const [searchForm] = Form.useForm()
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })

  // 检查登录状态
  useEffect(() => {
    const info = getUserInfo()
    if (!info) {
      message.error('请先登录')
      navigate('/')
      return
    }
    setUserInfo(info)
  }, [navigate])

  // 获取帖子列表
  const fetchPosts = async (params = {}) => {
    setLoading(true)
    try {
      // 模拟API调用
      await new Promise(resolve => setTimeout(resolve, 500))
      setPosts(mockPosts)
      setPagination({
        ...pagination,
        total: mockPosts.length
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (userInfo) {
      fetchPosts()
    }
  }, [userInfo])

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
    Modal.info({
      title: '帖子详情',
      width: 600,
      content: (
        <div className="post-detail">
          <p><strong>标题：</strong>{record.title}</p>
          <p><strong>作者：</strong>{record.authorName}</p>
          <p><strong>话题：</strong>{record.topicName}</p>
          <p><strong>浏览量：</strong>{record.viewCount}</p>
          <p><strong>点赞数：</strong>{record.likeCount}</p>
          <p><strong>评论数：</strong>{record.commentCount}</p>
          <p><strong>状态：</strong>{statusMap[record.status]?.text}</p>
          <p><strong>发布时间：</strong>{record.createTime}</p>
          <p><strong>内容：</strong></p>
          <div className="post-content">{record.content}</div>
        </div>
      )
    })
  }

  // 审核通过
  const handleApprove = async (id) => {
    message.success('审核通过')
    fetchPosts()
  }

  // 审核拒绝
  const handleReject = async (id) => {
    message.success('已拒绝')
    fetchPosts()
  }

  // 删除帖子
  const handleDelete = async (id) => {
    message.success('删除成功')
    fetchPosts()
  }

  // 下架帖子
  const handleOffline = async (id) => {
    message.success('已下架')
    fetchPosts()
  }

  // 退出登录
  const handleLogout = () => {
    clearAuth()
    message.success('已退出登录')
    navigate('/')
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
      width: 200
    },
    {
      title: '作者',
      dataIndex: 'authorName',
      width: 100
    },
    {
      title: '话题',
      dataIndex: 'topicName',
      width: 100
    },
    {
      title: '浏览/点赞/评论',
      key: 'stats',
      width: 140,
      render: (_, record) => `${record.viewCount}/${record.likeCount}/${record.commentCount}`
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
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button 
            type="text" 
            icon={<EyeOutlined />} 
            onClick={() => handleView(record)}
          >
            查看
          </Button>
          {record.status === 0 && (
            <>
              <Button 
                type="text" 
                style={{ color: '#52c41a' }}
                icon={<CheckOutlined />} 
                onClick={() => handleApprove(record.id)}
              >
                通过
              </Button>
              <Button 
                type="text" 
                danger
                icon={<CloseOutlined />} 
                onClick={() => handleReject(record.id)}
              >
                拒绝
              </Button>
            </>
          )}
          {record.status === 1 && (
            <Button 
              type="text" 
              onClick={() => handleOffline(record.id)}
            >
              下架
            </Button>
          )}
          <Popconfirm
            title="确定删除该帖子吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="text" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  if (!userInfo) {
    return null
  }

  return (
    <div className="post-manage-container">
      {/* 顶部导航 */}
      <header className="manage-header">
        <div className="header-left">
          <h1>智慧校园 - 帖子管理</h1>
        </div>
        <div className="header-right">
          <span className="user-name">{userInfo.username}</span>
          <Button onClick={handleLogout}>退出</Button>
        </div>
      </header>

      {/* 主内容 */}
      <main className="manage-main">
        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="keyword">
              <Input placeholder="搜索帖子标题" prefix={<SearchOutlined />} allowClear />
            </Form.Item>
            <Form.Item name="topicId">
              <Select placeholder="选择话题" allowClear style={{ width: 120 }}>
                <Option value={1}>学习交流</Option>
                <Option value={2}>运动健身</Option>
                <Option value={3}>二手交易</Option>
                <Option value={4}>美食推荐</Option>
              </Select>
            </Form.Item>
            <Form.Item name="status">
              <Select placeholder="选择状态" allowClear style={{ width: 120 }}>
                {Object.entries(statusMap).map(([key, value]) => (
                  <Option key={key} value={parseInt(key)}>{value.text}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit">搜索</Button>
              <Button onClick={handleReset} style={{ marginLeft: 8 }}>重置</Button>
            </Form.Item>
          </Form>
        </div>

        {/* 帖子列表 */}
        <Table
          columns={columns}
          dataSource={posts}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={(pag) => {
            setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize })
            fetchPosts({ page: pag.current, size: pag.pageSize })
          }}
          scroll={{ x: 1200 }}
        />
      </main>
    </div>
  )
}

export default PostManage
