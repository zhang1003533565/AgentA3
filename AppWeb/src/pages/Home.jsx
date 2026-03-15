import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { message } from 'antd'
import { getUserInfo, clearAuth } from '../utils/storage'
import './Home.css'

function Home() {
  const navigate = useNavigate()
  const [currentTime, setCurrentTime] = useState(new Date())
  const [userInfo, setUserInfo] = useState(null)

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

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date())
    }, 1000)
    return () => clearInterval(timer)
  }, [])

  const formatTime = (date) => {
    return date.toLocaleTimeString('zh-CN', { 
      hour: '2-digit', 
      minute: '2-digit',
      second: '2-digit'
    })
  }

  const formatDate = (date) => {
    const options = { 
      year: 'numeric', 
      month: 'long', 
      day: 'numeric', 
      weekday: 'long' 
    }
    return date.toLocaleDateString('zh-CN', options)
  }

  const menuItems = [
    { icon: '📅', title: '课程表', desc: '查看今日课程', color: '#4A90D9', path: null },
    { icon: '📊', title: '成绩查询', desc: '学期成绩一览', color: '#67C23A', path: null },
    { icon: '📝', title: '考试安排', desc: '考试时间与地点', color: '#E6A23C', path: null },
    { icon: '📖', title: '图书馆', desc: '借阅与预约', color: '#909399', path: null },
    { icon: '💳', title: '校园卡', desc: '充值与消费', color: '#F56C6C', path: null },
    { icon: '📋', title: '请假申请', desc: '在线提交请假', color: '#8E44AD', path: null },
    { icon: '🎉', title: '活动管理', desc: '活动发布与管理', color: '#FF6B6B', path: '/activity/manage' },
    { icon: '🏷️', title: '分类管理', desc: '活动分类管理', color: '#52C41A', path: '/category/manage' },
    { icon: '✅', title: '审核管理', desc: '报名审核管理', color: '#FAAD14', path: '/audit/manage' },
    { icon: '📍', title: '签到管理', desc: '活动签到管理', color: '#13C2C2', path: '/signin/manage' },
    { icon: '🔍', title: '失物招领', desc: '寻物与招领', color: '#4ECDC4', path: null }
  ]

  const notices = [
    { id: 1, title: '关于2026年清明节放假安排的通知', time: '2026-03-12', type: '教务' },
    { id: 2, title: '图书馆电子资源使用培训讲座', time: '2026-03-11', type: '图书馆' },
    { id: 3, title: '2026年春季学期奖学金评选开始', time: '2026-03-10', type: '学工' }
  ]

  const todayCourses = [
    { name: '高等数学', location: '教学楼A301', time: '08:00-09:40', status: 'finished' },
    { name: '大学英语', location: '教学楼B205', time: '10:00-11:40', status: 'ongoing' },
    { name: '计算机基础', location: '实验楼C102', time: '14:00-15:40', status: 'upcoming' }
  ]

  const handleLogout = () => {
    clearAuth()
    message.success('已退出登录')
    navigate('/')
  }

  const handleMenuClick = (item) => {
    if (item.path) {
      navigate(item.path)
    } else {
      message.info('功能开发中，敬请期待')
    }
  }

  if (!userInfo) {
    return null // 未登录时不渲染内容
  }

  return (
    <div className="home-container">
      {/* 顶部导航栏 */}
      <header className="home-header">
        <div className="header-left">
          <h1>智慧校园</h1>
        </div>
        <div className="header-right">
          <div className="time-display">
            <span className="time">{formatTime(currentTime)}</span>
            <span className="date">{formatDate(currentTime)}</span>
          </div>
          <div className="user-info">
            <img 
              src={`https://api.dicebear.com/7.x/avataaars/svg?seed=${userInfo.username}`} 
              alt="avatar" 
              className="avatar" 
            />
            <div className="user-details">
              <span className="user-name">{userInfo.username}</span>
              <span className="user-role">{userInfo.role === 'USER' ? '学生' : userInfo.role}</span>
            </div>
            <button className="logout-btn" onClick={handleLogout}>退出</button>
          </div>
        </div>
      </header>

      {/* 主内容区 */}
      <main className="home-main">
        {/* 欢迎横幅 */}
        <section className="welcome-banner">
          <div className="welcome-content">
            <h2>欢迎回来，{userInfo.username}！</h2>
            <p>今天是你在智慧校园的第 <strong>128</strong> 天</p>
          </div>
          <div className="quick-stats">
            <div className="stat-item">
              <span className="stat-number">3</span>
              <span className="stat-label">今日课程</span>
            </div>
            <div className="stat-item">
              <span className="stat-number">5</span>
              <span className="stat-label">待办事项</span>
            </div>
            <div className="stat-item">
              <span className="stat-number">2</span>
              <span className="stat-label">新消息</span>
            </div>
          </div>
        </section>

        {/* 功能菜单 */}
        <section className="menu-section">
          <h3 className="section-title">快捷功能</h3>
          <div className="menu-grid">
            {menuItems.map((item, index) => (
              <div 
                key={index} 
                className="menu-card" 
                style={{ '--card-color': item.color }}
                onClick={() => handleMenuClick(item)}
              >
                <div className="menu-icon">{item.icon}</div>
                <div className="menu-info">
                  <h4>{item.title}</h4>
                  <p>{item.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        <div className="content-row">
          {/* 今日课程 */}
          <section className="courses-section">
            <h3 className="section-title">
              📚 今日课程
              <a href="#" className="view-more">查看课表 &gt;</a>
            </h3>
            <div className="course-list">
              {todayCourses.map((course, index) => (
                <div key={index} className={`course-item ${course.status}`}>
                  <div className="course-time">{course.time}</div>
                  <div className="course-info">
                    <h4>{course.name}</h4>
                    <p>📍 {course.location}</p>
                  </div>
                  <div className={`course-status-badge ${course.status}`}>
                    {course.status === 'finished' ? '已结束' : 
                     course.status === 'ongoing' ? '进行中' : '待上课'}
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* 通知公告 */}
          <section className="notices-section">
            <h3 className="section-title">
              📢 通知公告
              <a href="#" className="view-more">更多 &gt;</a>
            </h3>
            <div className="notice-list">
              {notices.map((notice) => (
                <div key={notice.id} className="notice-item">
                  <span className="notice-type">{notice.type}</span>
                  <div className="notice-content">
                    <h4>{notice.title}</h4>
                    <span className="notice-time">{notice.time}</span>
                  </div>
                </div>
              ))}
            </div>
          </section>
        </div>
      </main>

      {/* 页脚 */}
      <footer className="home-footer">
        <p>&copy; 2026 智慧校园管理系统 - All Rights Reserved</p>
      </footer>
    </div>
  )
}

export default Home
