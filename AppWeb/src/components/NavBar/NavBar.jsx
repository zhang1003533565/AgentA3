import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  AlertOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
  CalendarOutlined,
  CommentOutlined,
  CompassOutlined,
  DashboardOutlined,
  EnvironmentOutlined,
  FileSearchOutlined,
  FundOutlined,
  GiftOutlined,
  HomeOutlined,
  LayoutOutlined,
  LineChartOutlined,
  LogoutOutlined,
  MessageOutlined,
  MinusOutlined,
  NotificationOutlined,
  AudioOutlined,
  PieChartOutlined,
  PlusOutlined,
  PushpinOutlined,
  RobotOutlined,
  RocketOutlined,
  SafetyOutlined,
  SettingOutlined,
  ShopOutlined,
  ShoppingOutlined,
  TagsOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  VideoCameraOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { navigationSections } from '../../data/portalData'
import { clearAuth, getUserInfo } from '../../utils/storage'
import './NavBar.css'

const iconMap = {
  dashboard: <DashboardOutlined />,
  team: <TeamOutlined />,
  calendar: <CalendarOutlined />,
  tags: <TagsOutlined />,
  audit: <AuditOutlined />,
  environment: <EnvironmentOutlined />,
  notification: <NotificationOutlined />,
  message: <MessageOutlined />,
  comment: <CommentOutlined />,
  tag: <TagsOutlined />,
  alert: <AlertOutlined />,
  safety: <SafetyOutlined />,
  shop: <ShopOutlined />,
  thunder: <ThunderboltOutlined />,
  bank: <BankOutlined />,
  home: <HomeOutlined />,
  'bar-chart': <BarChartOutlined />,
  compass: <CompassOutlined />,
  pushpin: <PushpinOutlined />,
  'line-chart': <LineChartOutlined />,
  shopping: <ShoppingOutlined />,
  'file-search': <FileSearchOutlined />,
  appstore: <AppstoreOutlined />,
  warning: <WarningOutlined />,
  gift: <GiftOutlined />,
  coupon: <TagsOutlined />,
  fund: <FundOutlined />,
  robot: <RobotOutlined />,
  'video-camera': <VideoCameraOutlined />,
  audio: <AudioOutlined />,
  layout: <LayoutOutlined />,
  'pie-chart': <PieChartOutlined />,
  rocket: <RocketOutlined />,
  tool: <ToolOutlined />,
  shield: <SafetyOutlined />,
  setting: <SettingOutlined />,
}

function NavBar({ mobileOpen, onClose }) {
  const navigate = useNavigate()
  const location = useLocation()
  const userInfo = getUserInfo()
  const defaultOpen = useMemo(
    () =>
      navigationSections.reduce((acc, section) => {
        const hasActiveChild = section.items.some((item) => (
          location.pathname === item.path ||
          (!item.exact && location.pathname.startsWith(`${item.path}/`)) ||
          (item.path === '/activity/manage' && location.pathname.startsWith('/activity/'))
        ))
        acc[section.label] = hasActiveChild || section.label === '总览'
        return acc
      }, {}),
    [location.pathname]
  )
  const [openSections, setOpenSections] = useState(defaultOpen)

  useEffect(() => {
    setOpenSections((prev) => ({
      ...prev,
      ...defaultOpen,
    }))
  }, [defaultOpen])

  const toggleSection = (label) => {
    setOpenSections((prev) => ({
      ...prev,
      [label]: !prev[label],
    }))
  }

  const handleLogout = () => {
    clearAuth()
    navigate('/')
  }

  return (
    <aside className={`navbar ${mobileOpen ? 'open' : ''}`}>
      <div
        className="navbar-brand"
        onClick={() => {
          navigate('/home')
          onClose?.()
        }}
        role="button"
        tabIndex={0}
      >
        <div className="navbar-brand-mark">SC</div>
        <div>
          <strong>智慧校园</strong>
          <span>Smart Campus Console</span>
        </div>
      </div>

      <div className="navbar-user-card">
        <div className="navbar-user-card-top">
          <div className="navbar-user-avatar">{(userInfo?.username || 'A').slice(0, 1).toUpperCase()}</div>
          <div>
            <strong>{userInfo?.username || '管理员'}</strong>
            <span>{userInfo?.role || 'System Operator'}</span>
          </div>
        </div>
        <button type="button" className="navbar-logout" onClick={handleLogout}>
          <LogoutOutlined />
          <span>退出登录</span>
        </button>
      </div>

      <nav className="navbar-nav">
        {navigationSections.map((section) => (
          <section key={section.label} className="navbar-section">
            <button
              type="button"
              className="navbar-section-toggle"
              onClick={() => toggleSection(section.label)}
            >
              <h4>{section.label}</h4>
              <span>{openSections[section.label] ? <MinusOutlined /> : <PlusOutlined />}</span>
            </button>
            <div className={`navbar-links ${openSections[section.label] ? 'expanded' : 'collapsed'}`}>
              {section.items.filter((item) => !item.hidden).map((item) => {
                const active = location.pathname === item.path ||
                  (!item.exact && location.pathname.startsWith(`${item.path}/`)) ||
                  (item.path === '/activity/manage' && location.pathname.startsWith('/activity/'))
                return (
                  <button
                    key={item.path}
                    type="button"
                    className={`navbar-link ${active ? 'active' : ''}`}
                    onClick={() => {
                      navigate(item.path)
                      onClose?.()
                    }}
                  >
                    <span className="navbar-link-icon">{iconMap[item.icon] || <SettingOutlined />}</span>
                    <span>{item.label}</span>
                  </button>
                )
              })}
            </div>
          </section>
        ))}
      </nav>
    </aside>
  )
}

export default NavBar
