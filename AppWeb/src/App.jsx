import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import Login from './pages/Login/Login'
import Home from './pages/Home/Home'
import ActivityManage from './pages/activity/ActivityManage/ActivityManage'
import CategoryManage from './pages/activity/CategoryManage/CategoryManage'
import AuditManage from './pages/activity/AuditManage/AuditManage'
import SignInManage from './pages/activity/SignInManage/SignInManage'
import NoticeManage from './pages/activity/NoticeManage/NoticeManage'
import './App.css'

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/home" element={<Home />} />
        <Route path="/activity/manage" element={<ActivityManage />} />
        <Route path="/category/manage" element={<CategoryManage />} />
        <Route path="/audit/manage" element={<AuditManage />} />
        <Route path="/signin/manage" element={<SignInManage />} />
        <Route path="/notice/manage" element={<NoticeManage />} />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  )
}

export default App
