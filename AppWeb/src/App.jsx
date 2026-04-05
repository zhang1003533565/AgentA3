import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout/Layout'
import { allNavItems } from './data/portalData'
import ActivityDetail from './pages/activity/ActivityDetail/ActivityDetail'
import ActivityEditor from './pages/activity/ActivityEditor/ActivityEditor'
import ActivityManage from './pages/activity/ActivityManage/ActivityManage'
import Home from './pages/Home/Home'
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
          {workspaceRoutes}
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  )
}

export default App
