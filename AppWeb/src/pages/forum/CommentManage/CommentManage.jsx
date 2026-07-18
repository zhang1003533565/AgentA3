import { useCallback, useState, useEffect } from 'react'
import { message, Modal, Button, Table, Tag, Space, Popconfirm, Input, Select, Form } from 'antd'
import { EyeOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons'
import { getCommentList, deleteComment } from '../../../api/forum'
import './CommentManage.css'

const { Option } = Select

// 评论状态映射
const statusMap = {
  'NORMAL': { text: '正常', color: 'green' },
  'HIDDEN': { text: '已隐藏', color: 'orange' },
  'DELETED': { text: '已删除', color: 'default' }
}

function CommentManage() {
  const [comments, setComments] = useState([])
  const [loading, setLoading] = useState(false)
  const [searchForm] = Form.useForm()
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })
  const currentPage = pagination.current
  const currentPageSize = pagination.pageSize

  // 获取评论列表
  const fetchComments = useCallback(async (params = {}) => {
    setLoading(true)
    try {
      const res = await getCommentList({
        admin: true,
        page: currentPage,
        size: currentPageSize,
        ...params
      })
      if (res.code === 200) {
        setComments(res.data?.list || res.data || [])
        setPagination((prev) => ({
          ...prev,
          total: res.data?.total || 0
        }))
      }
    } catch (error) {
      console.error('获取评论列表失败:', error)
    } finally {
      setLoading(false)
    }
  }, [currentPage, currentPageSize])

  useEffect(() => {
    fetchComments()
  }, [fetchComments])

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
          <p><strong>所属帖子：</strong>{record.post?.title || '未知'}</p>
          <p><strong>评论者：</strong>{record.user?.realName || '未知'}</p>
          {record.replyToUser && (
            <p><strong>回复对象：</strong>{record.replyToUser.realName}</p>
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
    try {
      const res = await deleteComment(id)
      if (res.code === 200) {
        message.success('删除成功')
        fetchComments()
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
      title: '所属帖子',
      dataIndex: 'post',
      ellipsis: true,
      width: 180,
      render: (post) => post?.title || '未知'
    },
    {
      title: '评论者',
      dataIndex: 'user',
      width: 100,
      render: (user) => user?.realName || '未知'
    },
    {
      title: '评论内容',
      dataIndex: 'content',
      ellipsis: true,
      width: 250
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

  return (
    <div className="comment-manage-container">
      {/* 主内容 */}
      <main className="manage-main">
        {/* 页面标题 */}
        <div className="page-header">
          <h2>评论管理</h2>
        </div>
        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="keyword">
              <Input placeholder="搜索评论内容" prefix={<SearchOutlined />} allowClear />
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
