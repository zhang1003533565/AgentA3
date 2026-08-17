import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout/Layout'
import { allNavItems } from './data/portalData'
import ActivityDetail from './pages/activity/ActivityDetail/ActivityDetail'
import ActivityEditor from './pages/activity/ActivityEditor/ActivityEditor'
import ActivityManage from './pages/activity/ActivityManage/ActivityManage'
import Home from './pages/Home/Home'
import QuestionBank from './pages/ai/QuestionBank/QuestionBank'
import KnowledgeChat from './pages/ai/KnowledgeChat/KnowledgeChat'
import KnowledgeManage from './pages/ai/KnowledgeManage/KnowledgeManage'
import ParagraphManage from './pages/ai/KnowledgeManage/ParagraphManage'
import ProfileRules from './pages/ai/ProfileRules/ProfileRules'
import RagManage from './pages/ai/RagManage/RagManage'
import AgentSettings from './pages/ai/AgentSettings/AgentSettings'
import AgentCache from './pages/ai/AgentCache/AgentCache'
import Login from './pages/Login/Login'
import ReportManage from './pages/forum/ReportManage/ReportManage'
import PostManage from './pages/forum/PostManage/PostManage'
import CommentManage from './pages/forum/CommentManage/CommentManage'
import TopicManage from './pages/forum/TopicManage/TopicManage'
import SecondhandReportManage from './pages/market/SecondhandReportManage/SecondhandReportManage'
import ExamPaperCreatePage from './pages/questionBank/ExamPaperCreatePage'
import ExamPaperHistoryPage from './pages/questionBank/ExamPaperHistoryPage'
import QuestionBankGeneratePage from './pages/questionBank/QuestionBankGeneratePage'
import { QUESTION_BANK_ROUTES } from './pages/questionBank/questionBankRoutes'
import WorkspacePage from './pages/workspace/WorkspacePage'
import StallManage from './pages/StallManage/StallManage'
import StallIndoorManage from './pages/StallManage/StallIndoorManage'
import MarkerManage from './pages/facility/MarkerManage/MarkerManage'
import FacilityPlaceManage from './pages/facility/FacilityPlaceManage/FacilityPlaceManage'
import TeachingBuildingManage from './pages/facility/TeachingBuildingManage/TeachingBuildingManage'
import CampusCourseManage from './pages/learning/CampusCourseManage'
import DiscountActivityManage from './pages/discount/ActivityManage'
import DiscountMerchantManage from './pages/discount/MerchantManage'
import DiscountCategoryManage from './pages/discount/CategoryManage'
import './App.css'

// 论坛独立页面路径集合（不走 WorkspacePage）
const FORUM_INDEPENDENT_PATHS = new Set(['/forum/post', '/forum/comment', '/forum/topic', '/forum/report'])
const FACILITY_PLACE_PATHS = new Set(['/facility/sports', '/facility/teaching', '/facility/dormitory'])
const DISCOUNT_PATHS = new Set(['/discount/merchant', '/discount/activity', '/discount/category'])
const SECONDHAND_INDEPENDENT_PATHS = new Set(['/market/report'])

function App() {
  // 过滤掉论坛相关路由，避免与独立页面冲突
  const workspaceRoutes = allNavItems
    .filter((item) => item.pageKey && item.path !== '/activity/manage' && item.path !== '/facility/canteen')
    .filter((item) => item.pageKey && item.path !== '/activity/manage' && !FORUM_INDEPENDENT_PATHS.has(item.path) && !DISCOUNT_PATHS.has(item.path))
    .filter((item) => !FACILITY_PLACE_PATHS.has(item.path))
    .filter((item) => !SECONDHAND_INDEPENDENT_PATHS.has(item.path))
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
          <Route path="/facility/canteen" element={<FacilityPlaceManage sceneType="CANTEEN" />} />
          <Route path="/facility/sports" element={<FacilityPlaceManage sceneType="SPORTS" />} />
          <Route path="/facility/teaching" element={<TeachingBuildingManage />} />
          <Route path="/facility/teaching/:buildingId" element={<TeachingBuildingManage />} />
          <Route path="/facility/teaching/:buildingId/floors/:floorId" element={<TeachingBuildingManage />} />
          <Route path="/facility/dormitory" element={<FacilityPlaceManage sceneType="DORMITORY" />} />
          <Route path="/facility/canteen/:canteenId/stalls" element={<StallManage />} />
          <Route path="/facility/canteen/:canteenId/stalls/indoor" element={<StallIndoorManage />} />
          <Route path="/facility/canteen/:canteenId/stalls/:stallId/dishes" element={<StallManage />} />
          <Route path="/activity/manage" element={<ActivityManage />} />
          <Route path="/activity/create" element={<ActivityEditor />} />
          <Route path="/activity/:id/edit" element={<ActivityEditor />} />
          <Route path="/activity/:id" element={<ActivityDetail />} />
          {/* 论坛独立美化页面 */}
          <Route path="/forum/post" element={<PostManage />} />
          <Route path="/forum/comment" element={<CommentManage />} />
          <Route path="/forum/topic" element={<TopicManage />} />
          <Route path="/forum/report" element={<ReportManage />} />
          <Route path="/market/report" element={<SecondhandReportManage />} />
          <Route path="/ai/rag" element={<Navigate to="/ai/rag/agents" replace />} />
          <Route path="/ai/rag/strategy" element={<Navigate to="/ai/rag/agents" replace />} />
          <Route path="/ai/rag/agents" element={<RagManage page="agents" />} />
          <Route path="/ai/agent-settings" element={<AgentSettings />} />
          <Route path="/ai/agent-cache" element={<AgentCache />} />
          <Route path={QUESTION_BANK_ROUTES.questions} element={<QuestionBank />} />
          <Route path={QUESTION_BANK_ROUTES.generate} element={<QuestionBankGeneratePage />} />
          <Route path={QUESTION_BANK_ROUTES.createPaper} element={<ExamPaperCreatePage />} />
          <Route path={QUESTION_BANK_ROUTES.paperHistory} element={<ExamPaperHistoryPage />} />
          <Route path="/ai/question-bank" element={<Navigate to={QUESTION_BANK_ROUTES.questions} replace />} />
          <Route path="/ai/exam-papers" element={<Navigate to={QUESTION_BANK_ROUTES.createPaper} replace />} />
          <Route path="/ai/knowledge" element={<KnowledgeManage />} />
          <Route path="/admin/knowledge-chat" element={<KnowledgeChat />} />
          <Route path="/ai/knowledge/paragraph/:knowledgeId/:documentId" element={<ParagraphManage />} />
          <Route path="/admin/paragraph/:knowledgeId/:documentId" element={<ParagraphManage />} />
          <Route path="/ai/profile-rules" element={<ProfileRules />} />
          <Route path="/facility/marker" element={<MarkerManage />} />
          <Route path="/learning/courses" element={<CampusCourseManage />} />
          <Route path="/discount/merchant" element={<DiscountMerchantManage />} />
          <Route path="/discount/activity" element={<DiscountActivityManage />} />
          <Route path="/discount/category" element={<DiscountCategoryManage />} />
          {workspaceRoutes}
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  )
}

export default App
