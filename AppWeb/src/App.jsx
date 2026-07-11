import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout/Layout'
import { allNavItems } from './data/portalData'
import ActivityDetail from './pages/activity/ActivityDetail/ActivityDetail'
import ActivityEditor from './pages/activity/ActivityEditor/ActivityEditor'
import ActivityManage from './pages/activity/ActivityManage/ActivityManage'
import Home from './pages/Home/Home'
import QuestionBank from './pages/ai/QuestionBank/QuestionBank'
import ExamPaper from './pages/ai/ExamPaper/ExamPaper'
import KnowledgeChat from './pages/ai/KnowledgeChat/KnowledgeChat'
import KnowledgeManage from './pages/ai/KnowledgeManage/KnowledgeManage'
import ParagraphManage from './pages/ai/KnowledgeManage/ParagraphManage'
import ProfileRules from './pages/ai/ProfileRules/ProfileRules'
import RagManage from './pages/ai/RagManage/RagManage'
import AgentSettings from './pages/ai/AgentSettings/AgentSettings'
import AgentCache from './pages/ai/AgentCache/AgentCache'
import Login from './pages/Login/Login'
import WorkspacePage from './pages/workspace/WorkspacePage'
import './App.css'

function App() {
  const workspaceRoutes = allNavItems
    .filter((item) => item.pageKey && item.path !== '/activity/manage')
    .map((item) => (
      <Route
        key={item.path}
        path={item.path}
        element={<WorkspacePage pageKey={item.pageKey} />}
      />
    ))

  return (
    <Router>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route element={<Layout />}>
          <Route path="/home" element={<Home />} />
          <Route path="/activity/manage" element={<ActivityManage />} />
          <Route path="/activity/create" element={<ActivityEditor />} />
          <Route path="/activity/:id/edit" element={<ActivityEditor />} />
          <Route path="/activity/:id" element={<ActivityDetail />} />
          <Route path="/ai/rag" element={<Navigate to="/ai/rag/agents" replace />} />
          <Route path="/ai/rag/strategy" element={<Navigate to="/ai/rag/agents" replace />} />
          <Route path="/ai/rag/agents" element={<RagManage page="agents" />} />
          <Route path="/ai/agent-settings" element={<AgentSettings />} />
          <Route path="/ai/agent-cache" element={<AgentCache />} />
          <Route path="/ai/question-bank" element={<QuestionBank />} />
          <Route path="/ai/exam-papers" element={<ExamPaper />} />
          <Route path="/ai/knowledge" element={<KnowledgeManage />} />
          <Route path="/admin/knowledge-chat" element={<KnowledgeChat />} />
          <Route path="/ai/knowledge/paragraph/:knowledgeId/:documentId" element={<ParagraphManage />} />
          <Route path="/admin/paragraph/:knowledgeId/:documentId" element={<ParagraphManage />} />
          <Route path="/ai/profile-rules" element={<ProfileRules />} />
          {workspaceRoutes}
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  )
}

export default App
