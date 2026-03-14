import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import Login from './pages/Login'
import Home from './pages/Home'
import ActivityManage from './pages/ActivityManage'
import CategoryManage from './pages/CategoryManage'
import AuditManage from './pages/AuditManage'
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
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  )
}

export default App
