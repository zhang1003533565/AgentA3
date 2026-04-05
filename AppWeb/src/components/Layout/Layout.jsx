import { useMemo, useState } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Button } from 'antd'
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons'
import NavBar from '../NavBar/NavBar'
import { getNavMetaByPath } from '../../data/portalData'
import { getUserInfo } from '../../utils/storage'
import './Layout.css'

function Layout() {
  const userInfo = getUserInfo()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)
  const pageMeta = useMemo(() => getNavMetaByPath(location.pathname), [location.pathname])
  const hidePageHeading = location.pathname !== '/home'

  if (!userInfo) {
    return <Navigate to="/" replace />
  }

  return (
    <div className={`layout ${mobileOpen ? 'sidebar-open' : ''}`}>
      <NavBar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} />
      <div className="layout-body">
        <header className="layout-topbar">
          <div className="layout-topbar-left">
            <Button
              className="layout-menu-trigger"
              type="text"
              icon={mobileOpen ? <MenuFoldOutlined /> : <MenuUnfoldOutlined />}
              onClick={() => setMobileOpen((open) => !open)}
            />
            {!hidePageHeading ? (
              <div>
                <span className="layout-kicker">{pageMeta?.badge || '智慧校园后台'}</span>
                <h2>{pageMeta?.title || '管理驾驶舱'}</h2>
              </div>
            ) : null}
          </div>

        </header>

        <main className="layout-content">
          <Outlet />
        </main>
      </div>

      <button
        className="layout-mask"
        type="button"
        onClick={() => setMobileOpen(false)}
        aria-label="关闭侧栏"
      />
    </div>
  )
}

export default Layout
