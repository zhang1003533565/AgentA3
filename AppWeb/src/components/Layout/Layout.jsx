import { useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Button } from 'antd'
import { ArrowLeftOutlined, EditOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons'
import NavBar from '../NavBar/NavBar'
import PageHeader from '../PageHeader/PageHeader'
import { getNavMetaByPath, getBreadcrumbByPath } from '../../data/portalData'
import { getUserInfo } from '../../utils/storage'
import './Layout.css'

function Layout() {
  const location = useLocation()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)
  const pageMeta = useMemo(() => getNavMetaByPath(location.pathname), [location.pathname])
  const breadcrumb = useMemo(() => getBreadcrumbByPath(location.pathname), [location.pathname])
  const hidePageHeading = location.pathname !== '/home'
  const facilityDetailMatch = /^\/facility\/analytics\/[^/]+$/.test(location.pathname)

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
            {/* 面包屑页题：由布局顶栏统一渲染，位置固定，不受页面内容布局影响 */}
            {hidePageHeading && breadcrumb ? (
              <PageHeader items={breadcrumb} />
            ) : null}
          </div>
          {facilityDetailMatch ? (
            <div className="layout-topbar-right">
              <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/facility/analytics')}>
                返回列表
              </Button>
              <Button type="primary" icon={<EditOutlined />} onClick={() => window.dispatchEvent(new Event('facility-detail-edit'))}>
                编辑信息
              </Button>
            </div>
          ) : null}

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
