import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout/Layout'
import { allNavItems } from './data/portalData'
import ActivityDetail from './pages/activity/ActivityDetail/ActivityDetail'
import ActivityEditor from './pages/activity/ActivityEditor/ActivityEditor'
import ActivityManage from './pages/activity/ActivityManage/ActivityManage'
import Home from './pages/Home/Home'
import KnowledgeBaseManage from './pages/ai/KnowledgeBaseManage/KnowledgeBaseManage'
import RagManage from './pages/ai/RagManage/RagManage'
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
          <Route path="/ai/rag" element={<RagManage />} />
          <Route path="/ai/rag/strategy" element={<RagManage page="strategy" />} />
          <Route path="/ai/rag/agents" element={<RagManage page="agents" />} />
          <Route path="/ai/rag/framework" element={<RagManage page="framework" />} />
          <Route path="/ai/rag/evaluate" element={<RagManage page="evaluate" />} />
          <Route path="/ai/rag/text-to-sql" element={<RagManage page="sql" />} />
          <Route path="/ai/knowledge-base" element={<KnowledgeBaseManage />} />
          {workspaceRoutes}
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  )
}

export default App
