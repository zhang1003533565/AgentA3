import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Row, Col, Statistic, DatePicker, Button, message } from 'antd'
import { ArrowLeftOutlined, UserOutlined, CalendarOutlined, CheckCircleOutlined, FileTextOutlined } from '@ant-design/icons'
import { getUserInfo } from '../utils/storage'
import './Statistics.css'

const { RangePicker } = DatePicker

function Statistics() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [dateRange, setDateRange] = useState([null, null])
  
  // 统计数据
  const [stats, setStats] = useState({
    totalUsers: 1256,
    totalActivities: 48,
    totalSignIns: 3256,
    totalNotices: 128,
    todayActiveUsers: 89,
    weekNewActivities: 5,
    monthSignInRate: 87.5,
    pendingAudits: 12
  })

  useEffect(() => {
    const info = getUserInfo()
    if (!info) {
      message.error('请先登录')
      navigate('/')
      return
    }
  }, [navigate])

  const handleBack = () => {
    navigate('/home')
  }

  const handleDateChange = (dates) => {
    setDateRange(dates)
    if (dates && dates[0] && dates[1]) {
      message.info(`已选择时间范围：${dates[0].format('YYYY-MM-DD')} 至 ${dates[1].format('YYYY-MM-DD')}`)
      // 这里可以调用API获取指定时间范围的统计数据
    }
  }

  const handleRefresh = () => {
    setLoading(true)
    setTimeout(() => {
      setLoading(false)
      message.success('数据已刷新')
    }, 1000)
  }

  return (
    <div className="statistics-container">
      {/* 顶部导航 */}
      <header className="statistics-header">
        <div className="header-left">
          <Button 
            icon={<ArrowLeftOutlined />} 
            onClick={handleBack}
            className="back-btn"
          >
            返回首页
          </Button>
          <h1>数据统计</h1>
        </div>
        <div className="header-right">
          <RangePicker 
            onChange={handleDateChange}
            placeholder={['开始日期', '结束日期']}
            style={{ marginRight: 16 }}
          />
          <Button type="primary" onClick={handleRefresh} loading={loading}>
            刷新数据
          </Button>
        </div>
      </header>

      {/* 主要内容 */}
      <main className="statistics-main">
        {/* 核心指标 */}
        <Row gutter={[24, 24]} className="stats-row">
          <Col xs={24} sm={12} lg={6}>
            <Card className="stat-card stat-card-blue">
              <Statistic
                title="总用户数"
                value={stats.totalUsers}
                prefix={<UserOutlined />}
                valueStyle={{ color: '#1890FF' }}
              />
              <div className="stat-trend">+12% 较上月</div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card className="stat-card stat-card-green">
              <Statistic
                title="活动总数"
                value={stats.totalActivities}
                prefix={<CalendarOutlined />}
                valueStyle={{ color: '#52C41A' }}
              />
              <div className="stat-trend">+3 本周新增</div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card className="stat-card stat-card-orange">
              <Statistic
                title="签到总次数"
                value={stats.totalSignIns}
                prefix={<CheckCircleOutlined />}
                valueStyle={{ color: '#FA8C16' }}
              />
              <div className="stat-trend">月均 87.5%</div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card className="stat-card stat-card-purple">
              <Statistic
                title="通知公告数"
                value={stats.totalNotices}
                prefix={<FileTextOutlined />}
                valueStyle={{ color: '#722ED1' }}
              />
              <div className="stat-trend">本月发布 15 条</div>
            </Card>
          </Col>
        </Row>

        {/* 详细统计 */}
        <Row gutter={[24, 24]} className="detail-row">
          <Col xs={24} lg={12}>
            <Card title="今日活跃用户" className="detail-card">
              <div className="detail-content">
                <div className="detail-number">{stats.todayActiveUsers}</div>
                <div className="detail-desc">较昨日 +15 人</div>
              </div>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="本周新增活动" className="detail-card">
              <div className="detail-content">
                <div className="detail-number">{stats.weekNewActivities}</div>
                <div className="detail-desc">待审核 2 个</div>
              </div>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="月度签到率" className="detail-card">
              <div className="detail-content">
                <div className="detail-number">{stats.monthSignInRate}%</div>
                <div className="detail-desc">较上月 +2.3%</div>
              </div>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="待审核报名" className="detail-card">
              <div className="detail-content">
                <div className="detail-number">{stats.pendingAudits}</div>
                <div className="detail-desc">请及时处理</div>
              </div>
            </Card>
          </Col>
        </Row>
      </main>
    </div>
  )
}

export default Statistics
