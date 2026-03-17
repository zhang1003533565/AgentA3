import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
<<<<<<< HEAD
import Login from './pages/Login'
import Home from './pages/Home'
import ActivityManage from './pages/ActivityManage'
import CategoryManage from './pages/CategoryManage'
import AuditManage from './pages/AuditManage'
import SignInManage from './pages/SignInManage'
import NoticeManage from './pages/NoticeManage'
import Statistics from './pages/Statistics'
=======
import Login from './pages/Login/Login'
import Home from './pages/Home/Home'
import ActivityManage from './pages/activity/ActivityManage/ActivityManage'
import CategoryManage from './pages/activity/CategoryManage/CategoryManage'
import AuditManage from './pages/activity/AuditManage/AuditManage'
import SignInManage from './pages/activity/SignInManage/SignInManage'
import NoticeManage from './pages/activity/NoticeManage/NoticeManage'
import PostManage from './pages/forum/PostManage/PostManage'
import CommentManage from './pages/forum/CommentManage/CommentManage'
import TopicManage from './pages/forum/TopicManage/TopicManage'
import ReportManage from './pages/forum/ReportManage/ReportManage'
>>>>>>> 0a7809a35f16c02fe1e50c18fe41fa7da5adcc0a
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
<<<<<<< HEAD
        <Route path="/statistics" element={<Statistics />} />
=======
        <Route path="/forum/post" element={<PostManage />} />
        <Route path="/forum/comment" element={<CommentManage />} />
        <Route path="/forum/topic" element={<TopicManage />} />
        <Route path="/forum/report" element={<ReportManage />} />
>>>>>>> 0a7809a35f16c02fe1e50c18fe41fa7da5adcc0a
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  )
}

export default App
