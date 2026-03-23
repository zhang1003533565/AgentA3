import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import Login from './pages/Login/Login'
import Home from './pages/Home/Home'
import ActivityManage from './pages/activity/ActivityManage/ActivityManage'
import CategoryManage from './pages/activity/CategoryManage/CategoryManage'
import AuditManage from './pages/activity/AuditManage/AuditManage'
import SignInManage from './pages/activity/SignInManage/SignInManage'
import NoticeManage from './pages/activity/NoticeManage/NoticeManage'
import Statistics from './pages/Statistics'
import PostManage from './pages/forum/PostManage/PostManage'
import CommentManage from './pages/forum/CommentManage/CommentManage'
import TopicManage from './pages/forum/TopicManage/TopicManage'
import ReportManage from './pages/forum/ReportManage/ReportManage'
import UserManage from './pages/user/UserManage'
import Layout from './components/Layout/Layout'
import './App.css'

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route element={<Layout />}>
          <Route path="/home" element={<Home />} />
          <Route path="/activity/manage" element={<ActivityManage />} />
          <Route path="/category/manage" element={<CategoryManage />} />
          <Route path="/audit/manage" element={<AuditManage />} />
          <Route path="/signin/manage" element={<SignInManage />} />
          <Route path="/notice/manage" element={<NoticeManage />} />
          <Route path="/statistics" element={<Statistics />} />
          <Route path="/forum/post" element={<PostManage />} />
          <Route path="/forum/comment" element={<CommentManage />} />
          <Route path="/forum/topic" element={<TopicManage />} />
          <Route path="/forum/report" element={<ReportManage />} />
          <Route path="/user/manage" element={<UserManage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  )
}

export default App
