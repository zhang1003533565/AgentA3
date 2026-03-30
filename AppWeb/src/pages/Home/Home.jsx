import { useNavigate } from 'react-router-dom'
import { Button, Card, Progress } from 'antd'
import { ArrowRightOutlined } from '@ant-design/icons'
import { dashboardBoards, dashboardMetrics, phaseCards } from '../../data/portalData'
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
          <h1>把 `AppWeb` 作为统一后台入口，覆盖活动、论坛、设施、地图、旧物、特惠、AI 与系统治理。</h1>
          <p>
            当前工作台按业务模块组织，不再暴露开发阶段概念。页面先以完整前端体验交付，后续再按模块逐步接后端接口。
          </p>
          <div className="home-hero-actions">
            <Button type="primary" size="large" onClick={() => navigate('/activity/manage')}>
              进入活动模块
            </Button>
            <Button size="large" onClick={() => navigate('/system/optimize')}>
              查看优化中心
            </Button>
          </div>
        </div>

        <div className="home-hero-panel">
          <strong>{userInfo?.username || '管理员'}</strong>
          <span>当前身份：{userInfo?.role || 'System Operator'}</span>
          <div className="home-grid-mini">
            {dashboardMetrics.map((item) => (
              <div key={item.label} className={`home-mini-card ${item.tone}`}>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
                <em>{item.detail}</em>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="home-phase-grid">
        {phaseCards.map((item) => (
          <Card
            key={item.title}
            className="home-phase-card"
            styles={{ body: { padding: 24 } }}
            onClick={() => navigate(item.route)}
          >
            <span className="phase-tag">{item.phase}</span>
            <h3>{item.title}</h3>
            <div className="phase-meta">
              <span>已完成 {item.done} 项</span>
              <span>待推进 {item.todo} 项</span>
            </div>
            <Progress percent={item.progress} strokeColor={item.accent} trailColor="rgba(148,163,184,0.15)" />
            <button type="button">
              进入模块
              <ArrowRightOutlined />
            </button>
          </Card>
        ))}
      </section>

      <section className="home-board-grid">
        <Card title="本轮待办" className="home-board-card">
          <ul className="home-list">
            {dashboardBoards.todo.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </Card>

        <Card title="本次交付内容" className="home-board-card">
          <div className="home-release-list">
            {dashboardBoards.releases.map((item) => (
              <article key={item.title}>
                <strong>{item.title}</strong>
                <p>{item.detail}</p>
                <span>{item.date}</span>
              </article>
            ))}
          </div>
        </Card>

        <Card title="系统提醒" className="home-board-card">
          <div className="home-alert-list">
            {dashboardBoards.alerts.map((item) => (
              <div key={item.content} className="home-alert-item">
                <strong>{item.level}</strong>
                <p>{item.content}</p>
              </div>
            ))}
          </div>
        </Card>
      </section>
    </div>
  )
}

export default Home
