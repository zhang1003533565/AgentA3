import { useNavigate, useLocation } from 'react-router-dom'
import { Dropdown, Button, Avatar, Space } from 'antd'
import { DownOutlined, LogoutOutlined, HomeOutlined, CalendarOutlined, MessageOutlined } from '@ant-design/icons'
import { getUserInfo, clearAuth } from '../../utils/storage'
import './NavBar.css'

function NavBar() {
  const navigate = useNavigate()
  const location = useLocation()
  const userInfo = getUserInfo()

  // 判断当前选中的菜单
  const getSelectedKey = () => {
    const path = location.pathname
    if (path === '/home' || path === '/statistics') return 'home'
    if (path.startsWith('/activity') || path.startsWith('/category') || 
        path.startsWith('/audit') || path.startsWith('/signin') || 
        path.startsWith('/notice')) return 'activity'
    if (path.startsWith('/forum')) return 'forum'
    return 'home'
  }

  // 活动子菜单项
  const activityMenuItems = [
    { key: 'activity', label: '🎉 活动管理', onClick: () => navigate('/activity/manage') },
    { key: 'category', label: '🏷️ 分类管理', onClick: () => navigate('/category/manage') },
    { key: 'audit', label: '✅ 报名审核', onClick: () => navigate('/audit/manage') },
    { key: 'signin', label: '📍 签到管理', onClick: () => navigate('/signin/manage') },
    { key: 'notice', label: '🔔 通知管理', onClick: () => navigate('/notice/manage') },
    { type: 'divider' },
    { key: 'statistics', label: '📈 数据统计', onClick: () => navigate('/statistics') },
  ]

  // 论坛子菜单项
  const forumMenuItems = [
    { key: 'post', label: '📝 帖子管理', onClick: () => navigate('/forum/post') },
    { key: 'comment', label: '💬 评论管理', onClick: () => navigate('/forum/comment') },
    { key: 'topic', label: '🏷️ 话题管理', onClick: () => navigate('/forum/topic') },
    { key: 'report', label: '🚨 举报处理', onClick: () => navigate('/forum/report') },
  ]

  // 退出登录
  const handleLogout = () => {
    clearAuth()
    navigate('/')
  }

  return (
    <header className="navbar">
      <div className="navbar-container">
        {/* Logo */}
        <div className="navbar-logo" onClick={() => navigate('/home')}>
          <span className="logo-icon">🏫</span>
          <span className="logo-text">智慧校园</span>
        </div>

        {/* 导航菜单 */}
        <nav className="navbar-menu">
          <div 
            className={`nav-item ${getSelectedKey() === 'home' ? 'active' : ''}`}
            onClick={() => navigate('/home')}
          >
            <HomeOutlined />
            <span>首页</span>
          </div>

          <Dropdown menu={{ items: activityMenuItems }} trigger={['hover']} placement="bottomLeft">
            <div className={`nav-item ${getSelectedKey() === 'activity' ? 'active' : ''}`}>
              <CalendarOutlined />
              <span>校园活动</span>
              <DownOutlined style={{ fontSize: 10, marginLeft: 4 }} />
            </div>
          </Dropdown>

          <Dropdown menu={{ items: forumMenuItems }} trigger={['hover']} placement="bottomLeft">
            <div className={`nav-item ${getSelectedKey() === 'forum' ? 'active' : ''}`}>
              <MessageOutlined />
              <span>校园论坛</span>
              <DownOutlined style={{ fontSize: 10, marginLeft: 4 }} />
            </div>
          </Dropdown>
        </nav>

        {/* 用户信息 */}
        <div className="navbar-user">
          <Space>
            <Avatar 
              size="small"
              src={`https://api.dicebear.com/7.x/avataaars/svg?seed=${userInfo?.username || 'admin'}`}
            />
            <span className="user-name">{userInfo?.username || '管理员'}</span>
            <Button 
              type="text" 
              icon={<LogoutOutlined />}
              onClick={handleLogout}
              className="logout-btn"
            >
              退出
            </Button>
          </Space>
        </div>
      </div>
    </header>
  )
}

export default NavBar
