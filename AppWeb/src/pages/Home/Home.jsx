import { useNavigate } from 'react-router-dom'
import { Button, Card } from 'antd'
import { ArrowRightOutlined } from '@ant-design/icons'
import { moduleCards, navigationSections } from '../../data/portalData'
import { getUserInfo } from '../../utils/storage'
import './Home.css'

function Home() {
  const navigate = useNavigate()
  const userInfo = getUserInfo()

  return (
    <div className="home-container">
      <section className="home-hero">
        <div className="home-hero-copy">
          <span className="home-kicker">校园管理工作台</span>
          <h1>统一进入各业务后台，所有数据以真实接口为准，不再展示演示统计。</h1>
          <p>从左侧导航进入具体业务模块。已接入后端的页面会直接请求真实接口，未接入的模块只保留空态入口。</p>
          <div className="home-hero-actions">
            <Button type="primary" size="large" onClick={() => navigate('/activity/manage')}>
              进入活动管理
            </Button>
            <Button size="large" onClick={() => navigate('/user/manage')}>
              进入用户管理
            </Button>
          </div>
        </div>

        <div className="home-hero-panel">
          <strong>{userInfo?.username || '-'}</strong>
          <span>当前身份：{userInfo?.role || '-'}</span>
          <div className="home-panel-list">
            {navigationSections.map((section) => (
              <div key={section.label} className="home-panel-item">
                <strong>{section.label}</strong>
                <span>{section.items.length} 个入口</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="home-phase-grid">
        {moduleCards.map((item) => (
          <Card
            key={item.title}
            className="home-phase-card"
            styles={{ body: { padding: 24 } }}
            onClick={() => navigate(item.route)}
          >
            <span className="phase-tag">业务模块</span>
            <h3>{item.title}</h3>
            <p className="home-card-desc">{item.description}</p>
            <button type="button">
              进入模块
              <ArrowRightOutlined />
            </button>
          </Card>
        ))}
      </section>
    </div>
  )
}

export default Home
