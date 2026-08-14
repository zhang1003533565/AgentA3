import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { message, Drawer, Button, Table, Tag, Space, Popconfirm, Input, Form, Card, Popover, Tooltip } from 'antd'
import { EyeOutlined, DeleteOutlined, SearchOutlined, MessageOutlined, LikeOutlined, CommentOutlined, UserOutlined, CloseCircleOutlined } from '@ant-design/icons'
import { getCommentList, adminDeleteComment, batchDeleteComments, getForumStatistics } from '../../../api/forum'
import './CommentManage.css'

const statusMap = {
  'NORMAL': { text: '正常', color: 'green' },
  'HIDDEN': { text: '已隐藏', color: 'orange' },
  'DELETED': { text: '已删除', color: 'default' },
}

const formatTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')

function CommentManage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filterPostId = searchParams.get('postId') ? Number(searchParams.get('postId')) : null
  const filterPostTitle = searchParams.get('postTitle') || ''
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
      const pageNum = params.pageNum ?? pagination.current
      const pageSize = params.pageSize ?? pagination.pageSize
      const res = await getCommentList({ admin: true, pageNum, pageSize, postId: filterPostId ?? undefined, ...params })
      if (res.code === 200) {
        const records = res.data?.records || res.data?.list || res.data || []
        setComments(Array.isArray(records) ? records : [])
        setPagination((prev) => ({ ...prev, current: pageNum, pageSize, total: res.data?.total || 0 }))
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

  useEffect(() => { fetchComments(); fetchStats() }, [searchParams])

  const clearFilter = () => {
    setSearchParams({}, { replace: true })
    setPagination((prev) => ({ ...prev, current: 1 }))
  }

  const handleSearch = (values) => { fetchComments({ pageNum: 1, ...values }) }
  const handleReset = () => { searchForm.resetFields(); fetchComments({ pageNum: 1 }) }
  const handleView = (record) => { setCurrentComment(record); setDrawerOpen(true) }

  const handleDeleteComment = async (id) => {
    try {
      const res = await adminDeleteComment(id)
      if (res.code === 200) { message.success('删除成功'); fetchComments(); fetchStats() }
    } catch (error) { console.error('删除失败:', error) }
  }

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) return
    try {
      const res = await batchDeleteComments(selectedRowKeys)
      if (res.code === 200) { message.success(`已批量删除 ${selectedRowKeys.length} 条评论`); setSelectedRowKeys([]); fetchComments(); fetchStats() }
    } catch (error) { console.error('批量删除失败:', error) }
  }

  const renderRowPopover = (record) => (
    <div className="cm-row-pop">
      <div className="cm-row-pop-post">{record.postTitle || record.post?.title || '未知帖子'}</div>
      <div className="cm-row-pop-content">{record.content || '(空)'}</div>
      <div className="cm-row-pop-meta">
        <div><span className="cm-row-pop-label">评论者</span>{record.username || '未知'}</div>
        <div><span className="cm-row-pop-label">点赞</span>{record.likeCount || 0}</div>
        <div><span className="cm-row-pop-label">状态</span><Tag color={statusMap[record.status]?.color}>{statusMap[record.status]?.text}</Tag></div>
        <div><span className="cm-row-pop-label">评论时间</span>{formatTime(record.createTime)}</div>
      </div>
    </div>
  )

  const columns = [
    {
      title: '所属帖子', dataIndex: 'postTitle', width: 110, ellipsis: true,
      render: (text, record) => <span title={record.postTitle || record.post?.title || '未知帖子'}>{record.postTitle || record.post?.title || '未知帖子'}</span>,
    },
    {
      title: '评论者', dataIndex: 'username', width: 130,
      render: (u) => <Space><UserOutlined />{u || '未知'}</Space>,
    },
    {
      title: '评论内容', dataIndex: 'content', ellipsis: { showTitle: false }, width: 220,
      render: (text, record) => (
        <Popover content={renderRowPopover(record)} title="评论完整信息" trigger="hover" placement="bottomLeft" mouseEnterDelay={0.3}>
          <div className="cm-comment-text">{text || '(空)'}</div>
        </Popover>
      ),
    },
    {
      title: '点赞', dataIndex: 'likeCount', width: 60,
      render: (c) => <Space><LikeOutlined style={{ color: '#f5222d' }} />{c || 0}</Space>,
    },
    { title: '评论时间', dataIndex: 'createTime', width: 130, render: (t) => formatTime(t) },
    {
      title: '操作', key: 'action', width: 90,
      render: (_, record) => (
        <Space size={0}>
          <Tooltip title="查看">
            <Button type="text" size="small" icon={<EyeOutlined />} onClick={() => handleView(record)} />
          </Tooltip>
          <Popconfirm title="确定删除该评论吗？" onConfirm={() => handleDeleteComment(record.id)} okText="确定" cancelText="取消">
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
    { label: '评论总数', value: stats.totalComments, className: 'cm-header-stat-green' },
    { label: '正常', value: stats.normalComments, className: 'cm-header-stat-emerald' },
  ]

  return (
    <div className="cm-container">

      {/* 顶部统计区（白色矩形，仅保留统计） */}
      <div className="cm-header">
        <div className="cm-header-stats">
          {statItems.map((s) => (
            <div key={s.label} className={`cm-header-stat ${s.className}`}>
              <span className="cm-header-stat-value">{s.value}</span>
              <span className="cm-header-stat-label">{s.label}</span>
            </div>
          ))}
        </div>
      </div>

      {/* 按帖子过滤提示条 */}
      {filterPostId && (
        <div className="cm-filter-bar">
          <span className="cm-filter-text">当前查看：帖子「{filterPostTitle || `#${filterPostId}`}」下的评论</span>
          <Button type="link" size="small" icon={<CloseCircleOutlined />} onClick={clearFilter}>查看全部评论</Button>
        </div>
      )}

      <div className="cm-search-card">
        <Form form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="keyword">
            <Input placeholder="搜索评论内容" prefix={<SearchOutlined />} allowClear style={{ width: 200 }} />
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
          expandable={{ childrenColumnName: '__no_children__' }}
          rowSelection={rowSelection}
          columns={columns}
          dataSource={comments}
          rowKey="id"
          loading={loading}
          pagination={{ ...pagination, showSizeChanger: true, showTotal: (t) => `共 ${t} 条`, pageSizeOptions: ['10', '20', '50'] }}
          onChange={(pag) => fetchComments({ pageNum: pag.current, pageSize: pag.pageSize })}
          size="middle"
        />
      </Card>

      <Drawer title={<Space><CommentOutlined /> <span>评论详情</span></Space>} placement="right" width={460} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        {currentComment && (
          <div className="cm-drawer">
            <div className="cm-drawer-section">
              <h3 className="cm-drawer-title">{currentComment.postTitle || currentComment.post?.title || '未知帖子'}</h3>
              <Space wrap className="cm-drawer-tags">
                <Tag color={statusMap[currentComment.status]?.color}>{statusMap[currentComment.status]?.text}</Tag>
              </Space>
            </div>
            <div className="cm-drawer-info">
              <div className="cm-info-row"><span className="cm-info-label">评论者</span><span><UserOutlined /> {currentComment.username || '未知'}</span></div>
              <div className="cm-info-row"><span className="cm-info-label">回复给</span><span>{currentComment.replyToUsername || '-'}</span></div>
              <div className="cm-info-row"><span className="cm-info-label">点赞数</span><span><LikeOutlined style={{color:'#f5222d'}} /> {currentComment.likeCount || 0}</span></div>
              <div className="cm-info-row"><span className="cm-info-label">评论时间</span><span>{formatTime(currentComment.createTime)}</span></div>
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
