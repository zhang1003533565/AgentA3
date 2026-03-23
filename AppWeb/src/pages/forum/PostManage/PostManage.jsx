import { useState, useEffect } from 'react'
import { message, Modal, Button, Table, Tag, Space, Popconfirm, Input, Select, Form } from 'antd'
import { EyeOutlined, DeleteOutlined, SearchOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons'
import { getPostList, deletePost, updatePostStatus } from '../../../api/forum'
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
        // 适配分页响应结构 { records: [...], total: ... }
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

  useEffect(() => {
    fetchPosts()
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
    Modal.info({
      title: '帖子详情',
      width: 600,
      content: (
        <div className="post-detail">
          <p><strong>标题：</strong>{record.title}</p>
          <p><strong>作者：</strong>{record.user?.realName || (record.isAnonymous ? '匿名用户' : '未知')}</p>
          <p><strong>话题：</strong>{record.topic?.topicName || '无'}</p>
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

  // 下架帖子
  const handleOffline = async (id) => {
    try {
      const res = await updatePostStatus(id, 'HIDDEN')
      if (res.code === 200) {
        message.success('已下架')
        fetchPosts()
      }
    } catch (error) {
      console.error('下架失败:', error)
    }
  }

  // 删除帖子
  const handleDelete = async (id) => {
    try {
      const res = await deletePost(id)
      if (res.code === 200) {
        message.success('删除成功')
        fetchPosts()
      }
    } catch (error) {
      console.error('删除失败:', error)
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
      width: 200
    },
    {
      title: '作者',
      dataIndex: 'user',
      width: 100,
      render: (user, record) => record.isAnonymous ? '匿名用户' : (user?.realName || '未知')
    },
    {
      title: '话题',
      dataIndex: 'topic',
      width: 100,
      render: (topic) => topic?.topicName || '无'
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
          {record.status === 'PUBLISHED' && (
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

  return (
    <div className="post-manage-container">
      {/* 主内容 */}
      <main className="manage-main">
        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="keyword">
              <Input placeholder="搜索帖子标题" prefix={<SearchOutlined />} allowClear />
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
