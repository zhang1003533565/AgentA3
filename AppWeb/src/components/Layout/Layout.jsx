import { Outlet, Navigate } from 'react-router-dom'
import { getUserInfo } from '../../utils/storage'
import NavBar from '../NavBar/NavBar'
import './Layout.css'

function Layout() {
  const userInfo = getUserInfo()
  
  // 未登录则跳转到登录页
  if (!userInfo) {
    return <Navigate to="/" replace />
  }

  return (
    <div className="layout">
      <NavBar />
      <main className="layout-content">
        <Outlet />
      </main>
    </div>
  )
}

export default Layout
