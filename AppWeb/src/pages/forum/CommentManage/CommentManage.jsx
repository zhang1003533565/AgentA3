import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { message, Modal, Button, Table, Tag, Space, Popconfirm, Input, Select, Form } from 'antd'
import { EyeOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons'
import { getUserInfo, clearAuth } from '../../../utils/storage'
import './CommentManage.css'

const { Option } = Select

// 评论状态映射
const statusMap = {
  0: { text: '待审核', color: 'orange' },
  1: { text: '已发布', color: 'green' },
  2: { text: '已删除', color: 'default' }
}

// 模拟数据
const mockComments = [
  {
    id: 1,
    postId: 1,
    postTitle: '关于期末考试复习的一些建议',
    userId: 5,
    userName: '赵六',
    content: '感谢分享！这些方法很实用，我已经开始尝试了。',
    parentId: null,
    replyToName: null,
    likeCount: 12,
    status: 1,
    createTime: '2026-03-15 15:30:00'
  },
  {
    id: 2,
    postId: 1,
    postTitle: '关于期末考试复习的一些建议',
    userId: 6,
    userName: '钱七',
    content: '请问有没有高数的复习资料可以分享一下？',
    parentId: null,
    replyToName: null,
    likeCount: 5,
    status: 1,
    createTime: '2026-03-15 16:00:00'
  },
  {
    id: 3,
    postId: 1,
    postTitle: '关于期末考试复习的一些建议',
    userId: 1,
    userName: '张三',
    content: '有的，我稍后整理一下发到帖子里。',
    parentId: 2,
    replyToName: '钱七',
    likeCount: 8,
    status: 1,
    createTime: '2026-03-15 16:15:00'
  },
  {
    id: 4,
    postId: 2,
    postTitle: '寻找羽毛球搭子',
    userId: 7,
    userName: '孙八',
    content: '我也想打球！周末下午可以吗？',
    parentId: null,
    replyToName: null,
    likeCount: 3,
    status: 1,
    createTime: '2026-03-15 11:00:00'
  },
  {
    id: 5,
    postId: 4,
    postTitle: '食堂新开档口推荐',
    userId: 8,
    userName: '周九',
    content: '这家确实好吃！强烈推荐他们的牛肉丸！',
    parentId: null,
    replyToName: null,
    likeCount: 15,
    status: 0,
    createTime: '2026-03-14 12:30:00'
  }
]

function CommentManage() {
  const navigate = useNavigate()
  const [userInfo, setUserInfo] = useState(null)
  const [comments, setComments] = useState([])
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

  // 获取评论列表
  const fetchComments = async (params = {}) => {
    setLoading(true)
    try {
      await new Promise(resolve => setTimeout(resolve, 500))
      setComments(mockComments)
      setPagination({
        ...pagination,
        total: mockComments.length
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (userInfo) {
      fetchComments()
    }
  }, [userInfo])

  // 搜索
  const handleSearch = (values) => {
    setPagination({ ...pagination, current: 1 })
    fetchComments(values)
  }

  // 重置搜索
  const handleReset = () => {
    searchForm.resetFields()
    setPagination({ ...pagination, current: 1 })
    fetchComments()
  }

  // 查看详情
  const handleView = (record) => {
    Modal.info({
      title: '评论详情',
      width: 500,
      content: (
        <div className="comment-detail">
          <p><strong>评论ID：</strong>{record.id}</p>
          <p><strong>所属帖子：</strong>{record.postTitle}</p>
          <p><strong>评论者：</strong>{record.userName}</p>
          {record.replyToName && (
            <p><strong>回复对象：</strong>{record.replyToName}</p>
          )}
          <p><strong>点赞数：</strong>{record.likeCount}</p>
          <p><strong>状态：</strong>{statusMap[record.status]?.text}</p>
          <p><strong>评论时间：</strong>{record.createTime}</p>
          <p><strong>内容：</strong></p>
          <div className="comment-content">{record.content}</div>
        </div>
      )
    })
  }

  // 删除评论
  const handleDelete = async (id) => {
    message.success('删除成功')
    fetchComments()
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
      title: '所属帖子',
      dataIndex: 'postTitle',
      ellipsis: true,
      width: 180
    },
    {
      title: '评论者',
      dataIndex: 'userName',
      width: 100
    },
    {
      title: '评论内容',
      dataIndex: 'content',
      ellipsis: true,
      width: 250,
      render: (text, record) => (
        <span>
          {record.replyToName && <span style={{ color: '#1890ff' }}>回复@{record.replyToName}： </span>}
          {text}
        </span>
      )
    },
    {
      title: '点赞',
      dataIndex: 'likeCount',
      width: 80
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
      title: '评论时间',
      dataIndex: 'createTime',
      width: 160
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
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
          <Popconfirm
            title="确定删除该评论吗？"
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
    <div className="comment-manage-container">
      {/* 顶部导航 */}
      <header className="manage-header">
        <div className="header-left">
          <h1>智慧校园 - 评论管理</h1>
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
              <Input placeholder="搜索评论内容" prefix={<SearchOutlined />} allowClear />
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

        {/* 评论列表 */}
        <Table
          columns={columns}
          dataSource={comments}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={(pag) => {
            setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize })
            fetchComments({ page: pag.current, size: pag.pageSize })
          }}
          scroll={{ x: 1100 }}
        />
      </main>
    </div>
  )
}

export default CommentManage
