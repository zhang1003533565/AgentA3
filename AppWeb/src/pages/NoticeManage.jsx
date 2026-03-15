import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { message, Modal, Button, List, Badge, Space, Popconfirm, Tabs, Empty } from 'antd'
import { CheckOutlined, CheckCircleOutlined, DeleteOutlined, EyeOutlined, BellOutlined } from '@ant-design/icons'
import { getNoticeList, markAsRead, markAllAsRead } from '../api/notice'
import { getUserInfo, clearAuth } from '../utils/storage'
import './NoticeManage.css'

const { TabPane } = Tabs

// 通知类型映射
const typeMap = {
  1: { text: '系统', color: 'blue' },
  2: { text: '活动', color: 'green' },
  3: { text: '审核', color: 'orange' },
  4: { text: '签到', color: 'purple' }
}

function NoticeManage() {
  const navigate = useNavigate()
  const [userInfo, setUserInfo] = useState(null)
  const [notices, setNotices] = useState([])
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('all')
  const [unreadCount, setUnreadCount] = useState(0)

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

  // 获取通知列表
  const fetchNotices = async (status = null) => {
    setLoading(true)
    try {
      const params = status !== null ? { status } : {}
      const res = await getNoticeList(params)
      if (res.code === 200) {
        setNotices(res.data || [])
        // 计算未读数量
        const unread = (res.data || []).filter(n => n.status === 0).length
        setUnreadCount(unread)
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (userInfo) {
      fetchNotices()
    }
  }, [userInfo])

  // 标记已读
  const handleMarkAsRead = async (id) => {
    try {
      const res = await markAsRead(id)
      if (res.code === 200) {
        message.success('已标记为已读')
        fetchNotices()
      }
    } catch (error) {
      console.error('标记已读失败:', error)
    }
  }

  // 全部已读
  const handleMarkAllAsRead = async () => {
    try {
      const res = await markAllAsRead()
      if (res.code === 200) {
        message.success('全部标记为已读')
        fetchNotices()
      }
    } catch (error) {
      console.error('标记全部已读失败:', error)
    }
  }

  // 查看详情
  const handleView = (notice) => {
    // 如果未读，先标记为已读
    if (notice.status === 0) {
      handleMarkAsRead(notice.id)
    }
    
    Modal.info({
      title: '通知详情',
      width: 500,
      content: (
        <div className="notice-detail">
          <p><strong>标题：</strong>{notice.title}</p>
          <p><strong>类型：</strong>{typeMap[notice.type]?.text}</p>
          <p><strong>时间：</strong>{notice.createTime}</p>
          <p><strong>内容：</strong></p>
          <div className="notice-content">{notice.content}</div>
        </div>
      ),
      okText: '知道了'
    })
  }

  // 退出登录
  const handleLogout = () => {
    clearAuth()
    message.success('已退出登录')
    navigate('/')
  }

  // Tab切换
  const handleTabChange = (key) => {
    setActiveTab(key)
    if (key === 'all') {
      fetchNotices()
    } else if (key === 'unread') {
      fetchNotices(0)
    } else if (key === 'read') {
      fetchNotices(1)
    }
  }

  // 过滤后的通知列表
  const filteredNotices = notices

  if (!userInfo) {
    return null
  }

  return (
    <div className="notice-manage-container">
      {/* 顶部导航 */}
      <header className="manage-header">
        <div className="header-left">
          <h1>智慧校园 - 通知管理</h1>
        </div>
        <div className="header-right">
          <span className="user-name">{userInfo.username}</span>
          <Button onClick={handleLogout}>退出</Button>
        </div>
      </header>

      {/* 主内容 */}
      <main className="manage-main">
        {/* 操作栏 */}
        <div className="notice-actions">
          <div className="notice-stats">
            <BellOutlined className="notice-icon" />
            <span>您有 <strong>{unreadCount}</strong> 条未读通知</span>
          </div>
          <Button 
            type="primary" 
            icon={<CheckCircleOutlined />}
            onClick={handleMarkAllAsRead}
            disabled={unreadCount === 0}
          >
            全部已读
          </Button>
        </div>

        {/* 通知列表 */}
        <div className="notice-list-container">
          <Tabs activeKey={activeTab} onChange={handleTabChange}>
            <TabPane tab="全部通知" key="all">
              <NoticeList 
                notices={filteredNotices} 
                loading={loading}
                onView={handleView}
                onMarkAsRead={handleMarkAsRead}
              />
            </TabPane>
            <TabPane tab={`未读通知 (${unreadCount})`} key="unread">
              <NoticeList 
                notices={filteredNotices} 
                loading={loading}
                onView={handleView}
                onMarkAsRead={handleMarkAsRead}
              />
            </TabPane>
            <TabPane tab="已读通知" key="read">
              <NoticeList 
                notices={filteredNotices} 
                loading={loading}
                onView={handleView}
                onMarkAsRead={handleMarkAsRead}
              />
            </TabPane>
          </Tabs>
        </div>
      </main>
    </div>
  )
}

// 通知列表组件
function NoticeList({ notices, loading, onView, onMarkAsRead }) {
  if (notices.length === 0 && !loading) {
    return (
      <Empty 
        description="暂无通知" 
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        style={{ marginTop: 40 }}
      />
    )
  }

  return (
    <List
      className="notice-list"
      loading={loading}
      dataSource={notices}
      renderItem={item => (
        <List.Item
          className={`notice-item ${item.status === 0 ? 'unread' : ''}`}
          actions={[
            <Button 
              type="text" 
              icon={<EyeOutlined />}
              onClick={() => onView(item)}
            >
              查看
            </Button>,
            item.status === 0 && (
              <Button 
                type="text" 
                icon={<CheckOutlined />}
                onClick={() => onMarkAsRead(item.id)}
              >
                标记已读
              </Button>
            )
          ]}
        >
          <List.Item.Meta
            avatar={
              <Badge dot={item.status === 0}>
                <div className={`notice-type-icon type-${item.type}`}>
                  {item.type === 1 ? '🔔' : item.type === 2 ? '🎉' : item.type === 3 ? '✅' : '📍'}
                </div>
              </Badge>
            }
            title={
              <Space>
                <span className={item.status === 0 ? 'unread-title' : ''}>
                  {item.title}
                </span>
              </Space>
            }
            description={
              <Space direction="vertical" size={0}>
                <span className="notice-time">{item.createTime}</span>
                <span className="notice-preview">{item.content?.substring(0, 50)}...</span>
              </Space>
            }
          />
        </List.Item>
      )}
    />
  )
}

export default NoticeManage
