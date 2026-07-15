import { useState, useEffect } from 'react'
import { message, Drawer, Button, Table, Tag, Space, Popconfirm, Input, Select, Form, Card } from 'antd'
import { EyeOutlined, DeleteOutlined, SearchOutlined, MessageOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { getCommentList, batchDeleteComments } from '../../../api/forum'
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
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [currentComment, setCurrentComment] = useState(null)

  // 获取评论列表
  const fetchComments = async (params = {}) => {
    setLoading(true)
    try {
      const res = await getCommentList({
        admin: true,
        page: pagination.current,
        size: pagination.pageSize,
        ...params
      })
      if (res.code === 200) {
        const records = res.data?.records || res.data?.list || res.data || []
        setComments(Array.isArray(records) ? records : [])
        setPagination({
          ...pagination,
          total: res.data?.total || 0
        })
      }
    } catch (error) {
      console.error('获取评论列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchComments()
  }, [])

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
    setCurrentComment(record)
    setDrawerOpen(true)
  }

  // 批量删除
  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) return
    try {
      const res = await batchDeleteComments(selectedRowKeys)
      if (res.code === 200) {
        message.success(`已批量删除 ${selectedRowKeys.length} 条评论`)
        setSelectedRowKeys([])
        fetchComments()
      }
    } catch (error) {
      console.error('批量删除失败:', error)
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
      dataIndex: 'postTitle',
      ellipsis: true,
      width: 200,
      render: (text, record) => {
        const title = record.postTitle || record.post?.title || '未知帖子'
        return <span title={title}>{title}</span>
      }
    },
    {
      title: '评论者',
      dataIndex: 'username',
      width: 100,
      render: (username) => username || '未知'
    },
    {
      title: '评论内容',
      dataIndex: 'content',
      ellipsis: { showTitle: false },
      width: 300,
      render: (text) => (
        <div className="comment-text-preview" title={text}>
          {text || '(空)'}
        </div>
      )
    },
    {
      title: '点赞',
      dataIndex: 'likeCount',
      width: 80,
      render: (count) => (
        <Space>
          <ThunderboltOutlined style={{ fontSize: 12, color: '#faad14' }} />
          {count || 0}
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
      title: '评论时间',
      dataIndex: 'createTime',
      width: 160
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Button
          type="text"
          icon={<EyeOutlined />}
          onClick={() => handleView(record)}
        >
          查看
        </Button>
      )
    }
  ]

  const rowSelection = {
    selectedRowKeys,
    onChange: (keys) => setSelectedRowKeys(keys)
  }

  return (
    <div className="comment-manage-container">
      {/* 统计卡片 */}
      <div className="stat-cards">
        <Card size="small" className="stat-card">
          <div className="stat-value">--</div>
          <div className="stat-label">评论总数</div>
        </Card>
        <Card size="small" className="stat-card stat-green">
          <div className="stat-value">--</div>
          <div className="stat-label">正常</div>
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
              <Input placeholder="搜索评论内容" prefix={<SearchOutlined />} allowClear style={{ width: 180 }} />
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
              title={`确定删除选中的 ${selectedRowKeys.length} 条评论吗？`}
              onConfirm={handleBatchDelete}
              okText="确定"
              cancelText="取消"
            >
              <Button type="primary" danger size="small">批量删除</Button>
            </Popconfirm>
          </div>
        )}

        {/* 评论列表 */}
        <Table
          rowSelection={rowSelection}
          columns={columns}
          dataSource={comments}
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
            fetchComments({ page: pag.current, size: pag.pageSize })
          }}
          scroll={{ x: 1200 }}
        />
      </main>

      {/* 详情抽屉 */}
      <Drawer
        title="评论详情"
        placement="right"
        width={500}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        {currentComment && (
          <div className="comment-detail-drawer">
            <p><strong>评论ID：</strong>{currentComment.id}</p>
            <p><strong>所属帖子：</strong>{currentComment.postTitle || currentComment.post?.title || '未知'}</p>
            <p><strong>评论者：</strong>{currentComment.username || '未知'}</p>
            {currentComment.replyToUsername && (
              <p><strong>回复对象：</strong>{currentComment.replyToUsername}</p>
            )}
            <p><strong>点赞数：</strong>{currentComment.likeCount || 0}</p>
            <p><strong>状态：</strong>
              <Tag color={statusMap[currentComment.status]?.color}>
                {statusMap[currentComment.status]?.text}
              </Tag>
            </p>
            <p><strong>评论时间：</strong>{currentComment.createTime}</p>
            <p><strong>内容：</strong></p>
            <div className="comment-content">{currentComment.content}</div>
          </div>
        )}
      </Drawer>
    </div>
  )
}

export default CommentManage
