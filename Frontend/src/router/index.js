import { createRouter, createWebHistory } from 'vue-router'

import AiAssistantView from '../views/AiAssistantView.vue'
import AiToolsView from '../views/AiToolsView.vue'
import CampusActivitiesView from '../views/CampusActivitiesView.vue'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import MapView from '../views/MapView.vue'
import MeetingRoomView from '../views/MeetingRoomView.vue'
import MeetingWorkspaceView from '../views/MeetingWorkspaceView.vue'
import MessageCenterView from '../views/MessageCenterView.vue'
import PythonLearningView from '../views/PythonLearningView.vue'
import PythonQuestionBankView from '../views/pythonOnline/PythonQuestionBankView.vue'
import PythonPracticeView from '../views/pythonOnline/PythonPracticeView.vue'
import KnowledgeGraphView from '../views/KnowledgeGraphView.vue'
import LearningResourceView from '../views/LearningResourceView.vue'
import MarketplaceView from '../views/MarketplaceView.vue'
import MarketplaceChatView from '../views/MarketplaceChatView.vue'
import ForumView from '../views/ForumView.vue'
import ForumPostView from '../views/ForumPostView.vue'
import ForumProfileView from '../views/ForumProfileView.vue'
import AiWritingView from '../views/aiStudio/AiWritingView.vue'
import AiImageView from '../views/aiStudio/AiImageView.vue'
import AiPresentationView from '../views/aiStudio/AiPresentationView.vue'
import AiMindMapView from '../views/aiStudio/AiMindMapView.vue'
import AiArchitectureView from '../views/aiStudio/AiArchitectureView.vue'
import AiFlowchartView from '../views/aiStudio/AiFlowchartView.vue'
import { AI_STUDIO_TOOL_IDS } from '../config/aiStudioTools'
import AiOriginalView from '../views/AiOriginalView.vue'
import WatermarkAddView from '../views/WatermarkAddView.vue'
import WatermarkBatchView from '../views/WatermarkBatchView.vue'
import WatermarkHistoryView from '../views/WatermarkHistoryView.vue'
import WatermarkHelpView from '../views/WatermarkHelpView.vue'
import ActivityPublishView from '../views/ActivityPublishView.vue'
import ActivitySignInView from '../views/ActivitySignInView.vue'
import ActivityDetailView from '../views/ActivityDetailView.vue'
import ActivitySignupView from '../views/ActivitySignupView.vue'
import AccountSettingsView from '../views/AccountSettingsView.vue'
import ProfileRadarView from '../views/ProfileRadarView.vue'
import MineActivitiesView from '../views/MineActivitiesView.vue'
import MineAiHistoryView from '../views/MineAiHistoryView.vue'
import MineMeetingScheduleView from '../views/MineMeetingScheduleView.vue'
import ScheduleWorkspaceView from '../views/ScheduleWorkspaceView.vue'
import ScheduleSettingsWorkspaceView from '../views/ScheduleSettingsWorkspaceView.vue'
import MineView from '../views/MineView.vue'
import ExamPapersView from '../views/ExamPapersView.vue'
import ExamTakingView from '../views/ExamTakingView.vue'
import ExamResultView from '../views/ExamResultView.vue'
import ExamDetailView from '../views/ExamDetailView.vue'
import ExamHistoryView from '../views/ExamHistoryView.vue'
import PaperHomeView from '../views/paper/PaperHomeView.vue'
import PaperMineView from '../views/paper/PaperMineView.vue'
import PaperInfoView from '../views/paper/PaperInfoView.vue'
import PaperSelectView from '../views/paper/PaperSelectView.vue'
import PaperSelectedView from '../views/paper/PaperSelectedView.vue'
import PaperPreviewView from '../views/paper/PaperPreviewView.vue'
import PaperLayoutView from '../views/paper/PaperLayoutView.vue'
import PaperPrintPreviewView from '../views/paper/PaperPrintPreviewView.vue'
import PaperBankView from '../views/paper/PaperBankView.vue'
import PaperBankEditView from '../views/paper/PaperBankEditView.vue'
import PaperBankDetailView from '../views/paper/PaperBankDetailView.vue'
import PaperQuestionDetailView from '../views/paper/PaperQuestionDetailView.vue'
import ResumeView from '../views/ResumeView.vue'
import ResumeWorkspaceView from '../views/ResumeWorkspaceView.vue'
import ResumeDesigner from '../views/ResumeDesigner.vue'
import ResumeWizard from '../views/ResumeWizard.vue'
import CampusCourseView from '../views/CampusCourseView.vue'
import CampusDiscountView from '../views/CampusDiscountView.vue'
import DocumentConvertView from '../views/DocumentConvertView.vue'
import CareerNebulaView from '../views/CareerNebulaView.vue'
import CareerPlanetView from '../views/CareerPlanetView.vue'
import HotJobsView from '../views/HotJobsView.vue'
import { getToken } from '../utils/auth'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/home', name: 'home', component: HomeView },
  { path: '/map', name: 'map', component: MapView },
  { path: '/activities', name: 'activities', component: CampusActivitiesView },
  { path: '/meetings', name: 'meetings', component: MeetingWorkspaceView },
  { path: '/meetings/room/:sessionId', name: 'meeting-room', component: MeetingRoomView },
  { path: '/ai', name: 'ai', component: AiAssistantView },
  { path: '/ai-tools', name: 'ai-tools', component: AiToolsView },
  { path: '/jobs/hot', name: 'hot-jobs', component: HotJobsView },
  { path: '/ai-original', name: 'ai-original', component: AiOriginalView },
  { path: '/ai-original/add', name: 'ai-original-add', component: WatermarkAddView },
  { path: '/ai-original/batch', name: 'ai-original-batch', component: WatermarkBatchView },
  { path: '/ai-original/history', name: 'ai-original-history', component: WatermarkHistoryView },
  { path: '/ai-original/help', name: 'ai-original-help', component: WatermarkHelpView },
  { path: '/ai-studio', redirect: '/ai-tools' },
  { path: '/ai-studio/writing', name: 'ai-studio-writing', component: AiWritingView },
  { path: '/ai-studio/image', name: 'ai-studio-image', component: AiImageView },
  { path: '/ai-studio/exam', redirect: '/paper' },
  { path: '/paper', name: 'paper-home', component: PaperHomeView },
  { path: '/paper/mine', name: 'paper-mine', component: PaperMineView },
  { path: '/paper/info', name: 'paper-info', component: PaperInfoView },
  { path: '/paper/select', name: 'paper-select', component: PaperSelectView },
  { path: '/paper/selected', name: 'paper-selected', component: PaperSelectedView },
  { path: '/paper/preview', name: 'paper-preview', component: PaperPreviewView },
  { path: '/paper/layout', name: 'paper-layout', component: PaperLayoutView },
  { path: '/paper/print', name: 'paper-print', component: PaperPrintPreviewView },
  { path: '/paper/banks', name: 'paper-banks', component: PaperBankView },
  { path: '/paper/banks/edit', name: 'paper-bank-edit', component: PaperBankEditView },
  { path: '/paper/banks/:id', name: 'paper-bank-detail', component: PaperBankDetailView },
  { path: '/paper/questions/:questionId', name: 'paper-question-detail', component: PaperQuestionDetailView },
  { path: '/ai-studio/presentation', name: 'ai-studio-presentation', component: AiPresentationView },
  { path: '/ai-studio/mind_map', name: 'ai-studio-mind-map', component: AiMindMapView },
  { path: '/ai-studio/architecture', name: 'ai-studio-architecture', component: AiArchitectureView },
  { path: '/ai-studio/flowchart', name: 'ai-studio-flowchart', component: AiFlowchartView },
  {
    path: '/ai-studio/:tool',
    redirect: (to) => (AI_STUDIO_TOOL_IDS.includes(String(to.params.tool || '')) ? `/ai-studio/${to.params.tool}` : '/ai-tools'),
  },
  { path: '/profile-radar', name: 'profile-radar', component: ProfileRadarView },
  { path: '/learning', name: 'learning', component: PythonQuestionBankView },
  { path: '/learning/plan', name: 'learning-plan', component: PythonLearningView },
  { path: '/learning/problems/:id', redirect: (to) => `/learning/practice/${to.params.id}` },
  { path: '/learning/practice/:id', name: 'python-practice', component: PythonPracticeView },
  { path: '/learning/knowledge-graph', name: 'knowledge-graph', component: KnowledgeGraphView },
  { path: '/learning/resources', name: 'learning-resources', component: LearningResourceView },
  { path: '/marketplace', name: 'marketplace', component: MarketplaceView },
  { path: '/marketplace/chat', name: 'marketplace-chat', component: MarketplaceChatView },
  { path: '/forum', name: 'forum', component: ForumView },
  { path: '/forum/posts/:postId', name: 'forum-post', component: ForumPostView },
  { path: '/forum/users/:userId', name: 'forum-profile', component: ForumProfileView },
  { path: '/discount', name: 'discount', component: CampusDiscountView },
  { path: '/activities/publish', name: 'activity-publish', component: ActivityPublishView },
  { path: '/activities/:activityId', name: 'activity-detail', component: ActivityDetailView },
  { path: '/activities/:activityId/sign-in', name: 'activity-sign-in', component: ActivitySignInView },
  { path: '/activities/:activityId/signup', name: 'activity-signup', component: ActivitySignupView },
  { path: '/courses/:courseId', name: 'campus-course', component: CampusCourseView },
  { path: '/mine', name: 'mine', component: MineView },
  { path: '/messages', name: 'messages', component: MessageCenterView },
  { path: '/mine/messages', name: 'mine-messages', component: MessageCenterView },
  { path: '/mine/schedule', name: 'mine-schedule', component: ScheduleWorkspaceView },
  { path: '/mine/schedule-settings', name: 'mine-schedule-settings', component: ScheduleSettingsWorkspaceView },
  { path: '/mine/period-time', redirect: '/mine/schedule-settings?tab=periods' },
  { path: '/mine/semester', redirect: '/mine/schedule-settings?tab=semesters' },
  { path: '/mine/edu-account', redirect: '/mine/schedule-settings?tab=account' },
  { path: '/mine/meeting-schedule', name: 'mine-meeting-schedule', component: MineMeetingScheduleView },
  { path: '/mine/activities', name: 'mine-activities', component: MineActivitiesView },
  { path: '/mine/ai-history', name: 'mine-ai-history', component: MineAiHistoryView },
  { path: '/mine/papers', name: 'mine-papers', component: ExamPapersView },
  { path: '/mine/papers/attempts/:attemptId', name: 'exam-taking', component: ExamTakingView },
  { path: '/mine/papers/:paperId/history', name: 'exam-history', component: ExamHistoryView },
  { path: '/mine/papers/results/:attemptId', name: 'exam-result', component: ExamResultView },
  { path: '/mine/papers/results/:attemptId/details', name: 'exam-detail', component: ExamDetailView },
  { path: '/mine/account-settings', name: 'account-settings', component: AccountSettingsView },
  { path: '/resume', name: 'resume', component: ResumeView },
  { path: '/resume/workspace', name: 'resume-workspace', component: ResumeWorkspaceView },
  { path: '/resume/legacy', redirect: '/resume' },
  { path: '/resume/designer', name: 'resume-designer', component: ResumeDesigner },
  { path: '/resume/wizard', name: 'resume-wizard', component: ResumeWizard },
  { path: '/resume/wizard/edit', name: 'resume-edit', component: ResumeWizard },
  { path: '/career/nebula/:careerId?', name: 'career-nebula', component: CareerNebulaView },
  { path: '/career/nebula/:careerId/planet/:skillId', name: 'career-planet', component: CareerPlanetView },
  { path: '/convert', name: 'convert', component: DocumentConvertView },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (!to.meta.public && !getToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})

export default router
