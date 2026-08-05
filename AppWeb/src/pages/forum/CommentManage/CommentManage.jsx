import { useState, useEffect } from 'react'
import { message, Drawer, Button, Table, Tag, Space, Popconfirm, Input, Select, Form, Card } from 'antd'
import { EyeOutlined, DeleteOutlined, SearchOutlined, MessageOutlined, LikeOutlined, CommentOutlined, RiseOutlined, UserOutlined, CheckCircleOutlined, InboxOutlined } from '@ant-design/icons'
import { getCommentList, batchDeleteComments, getForumStatistics } from '../../../api/forum'
import './CommentManage.css'

const { Option } = Select

const statusMap = {
  'NORMAL': { text: '正常', color: 'green' },
  'HIDDEN': { text: '已隐藏', color: 'orange' },
  'DELETED': { text: '已删除', color: 'default' },
}

const STAT_COLORS = ['#059669', '#10b981', '#f59e0b']

function CommentManage() {
  const [comments, setComments] = useState([])
  const [loading, setLoading] = useState(false)
  const [searchForm] = Form.useForm()
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [currentComment, setCurrentComment] = useState(null)
  const [stats, setStats] = useState({ totalComments: 0, normalComments: 0, hiddenComments: 0 })

  const fetchComments = async (params = {}) => {
    setLoading(true)
    try {
      const res = await getCommentList({ admin: true, page: pagination.current, size: pagination.pageSize, ...params })
      if (res.code === 200) {
        const records = res.data?.records || res.data?.list || res.data || []
        setComments(Array.isArray(records) ? records : [])
        setPagination({ ...pagination, total: res.data?.total || 0 })
      }
    } catch (error) { console.error('获取评论列表失败:', error) }
    finally { setLoading(false) }
  }

  const fetchStats = async () => {
    try {
      const res = await getForumStatistics()
      if (res.code === 200 && res.data) {
        setStats({
          totalComments: res.data.totalComments || 0,
          normalComments: res.data.normalComments || 0,
          hiddenComments: res.data.hiddenComments || 0,
        })
      }
    } catch (e) { /* ignore */ }
  }

  useEffect(() => { fetchComments(); fetchStats() }, [])

  const handleSearch = (values) => { setPagination({ ...pagination, current: 1 }); fetchComments(values) }
  const handleReset = () => { searchForm.resetFields(); setPagination({ ...pagination, current: 1 }); fetchComments() }
  const handleView = (record) => { setCurrentComment(record); setDrawerOpen(true) }

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) return
    try {
      const res = await batchDeleteComments(selectedRowKeys)
      if (res.code === 200) { message.success(`已批量删除 ${selectedRowKeys.length} 条评论`); setSelectedRowKeys([]); fetchComments(); fetchStats() }
    } catch (error) { console.error('批量删除失败:', error) }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: '所属帖子', dataIndex: 'postTitle', width: 220, ellipsis: true,
      render: (text, record) => <span title={record.postTitle || record.post?.title || '未知帖子'}>{record.postTitle || record.post?.title || '未知帖子'}</span>,
    },
    {
      title: '评论者', dataIndex: 'username', width: 100,
      render: (u) => <Space><UserOutlined />{u || '未知'}</Space>,
    },
    {
      title: '评论内容', dataIndex: 'content', ellipsis: { showTitle: false }, width: 320,
      render: (text) => <div className="cm-comment-text" title={text}>{text || '(空)'}</div>,
    },
    {
      title: '点赞', dataIndex: 'likeCount', width: 80,
      render: (c) => <Space><LikeOutlined style={{ color: '#f5222d' }} />{c || 0}</Space>,
    },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: (s) => <Tag color={statusMap[s]?.color}>{statusMap[s]?.text}</Tag>,
    },
    { title: '评论时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作', key: 'action', width: 80, fixed: 'right',
      render: (_, record) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleView(record)}>查看</Button>
      ),
    },
  ]

  const rowSelection = { selectedRowKeys, onChange: (keys) => setSelectedRowKeys(keys) }

  const statItems = [
    { icon: <CommentOutlined />, label: '评论总数', value: stats.totalComments, color: STAT_COLORS[0], bg: '#ecfdf5' },
    { icon: <CheckCircleOutlined />, label: '正常', value: stats.normalComments, color: STAT_COLORS[1], bg: '#f0fdf4' },
    { icon: <InboxOutlined />, label: '已隐藏', value: stats.hiddenComments, color: STAT_COLORS[2], bg: '#fffbeb' },
  ]

  return (
    <div className="cm-container">

      {/* 顶部统计区（页题由布局顶栏面包屑统一渲染） */}
      <div className="cm-header">
        <div className="cm-header-stats">
          <span className="cm-stat-badge cm-badge-green">共 {stats.totalComments} 条</span>
          <span className="cm-stat-badge cm-badge-normal">正常 {stats.normalComments}</span>
          <span className="cm-stat-badge cm-badge-hidden">隐藏 {stats.hiddenComments}</span>
        </div>
      </div>

      <div className="cm-stat-row">
        {statItems.map((s, i) => (
          <Card key={i} className="cm-stat-card" style={{ borderTop: `3px solid ${s.color}`, background: s.bg }}>
            <div className="cm-stat-card-icon" style={{ color: s.color }}>{s.icon}</div>
            <div className="cm-stat-card-value">{s.value}</div>
            <div className="cm-stat-card-label">{s.label}</div>
          </Card>
        ))}
      </div>

      <div className="cm-search-card">
        <Form form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="keyword">
            <Input placeholder="搜索评论内容" prefix={<SearchOutlined />} allowClear style={{ width: 200 }} />
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

      {selectedRowKeys.length > 0 && (
        <div className="cm-batch-bar">
          <span className="cm-batch-count">已选择 <strong>{selectedRowKeys.length}</strong> 条</span>
          <Popconfirm title={`确定删除选中的 ${selectedRowKeys.length} 条评论吗？`} onConfirm={handleBatchDelete} okText="确定" cancelText="取消">
            <Button type="primary" danger size="small" icon={<DeleteOutlined />}>批量删除</Button>
          </Popconfirm>
        </div>
      )}

      <Card className="cm-table-card" bodyStyle={{ padding: 0 }}>
        <Table
          rowSelection={rowSelection}
          columns={columns}
          dataSource={comments}
          rowKey="id"
          loading={loading}
          pagination={{ ...pagination, showSizeChanger: true, showTotal: (t) => `共 ${t} 条`, pageSizeOptions: ['10', '20', '50'] }}
          onChange={(pag) => { setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize }); fetchComments({ page: pag.current, size: pag.pageSize }) }}
          scroll={{ x: 1400 }}
          size="middle"
        />
      </Card>

      <Drawer title={<Space><CommentOutlined /> <span>评论详情</span></Space>} placement="right" width={580} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        {currentComment && (
          <div className="cm-drawer">
            <div className="cm-drawer-section">
              <h3 className="cm-drawer-title">所属帖子</h3>
              <p className="cm-post-link">{currentComment.postTitle || currentComment.post?.title || '未知帖子'}</p>
            </div>
            <div className="cm-drawer-info">
              <div className="cm-info-row"><span className="cm-info-label">评论者</span><Space><UserOutlined />{currentComment.username || '未知'}</Space></div>
              {currentComment.replyToUsername && <div className="cm-info-row"><span className="cm-info-label">回复给</span><Space><RiseOutlined />{currentComment.replyToUsername}</Space></div>}
              <div className="cm-info-row"><span className="cm-info-label">点赞数</span><Space><LikeOutlined style={{color:'#f5222d'}} />{currentComment.likeCount || 0}</Space></div>
              <div className="cm-info-row"><span className="cm-info-label">状态</span><Tag color={statusMap[currentComment.status]?.color}>{statusMap[currentComment.status]?.text}</Tag></div>
              <div className="cm-info-row"><span className="cm-info-label">评论时间</span><span>{currentComment.createTime}</span></div>
            </div>
            <div className="cm-drawer-content">
              <h4 className="cm-drawer-subtitle">💬 评论内容</h4>
              <div className="cm-content-box">{currentComment.content}</div>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  )
}

export default CommentManage
