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
import KnowledgeGraphView from '../views/KnowledgeGraphView.vue'
import LearningResourceView from '../views/LearningResourceView.vue'
import MarketplaceView from '../views/MarketplaceView.vue'
import MarketplaceChatView from '../views/MarketplaceChatView.vue'
import ForumView from '../views/ForumView.vue'
import ForumPostView from '../views/ForumPostView.vue'
import ForumProfileView from '../views/ForumProfileView.vue'
import AiStudioView from '../views/AiStudioView.vue'
import CampusServicesView from '../views/CampusServicesView.vue'
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
import ResumeView from '../views/ResumeView.vue'
import ResumeDesigner from '../views/ResumeDesigner.vue'
import ResumeWizard from '../views/ResumeWizard.vue'
import CampusCourseView from '../views/CampusCourseView.vue'
import CampusDiscountView from '../views/CampusDiscountView.vue'
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
  { path: '/ai-studio/:tool?', name: 'ai-studio', component: AiStudioView },
  { path: '/profile-radar', name: 'profile-radar', component: ProfileRadarView },
  { path: '/learning', name: 'learning', component: PythonLearningView },
  { path: '/learning/knowledge-graph', name: 'knowledge-graph', component: KnowledgeGraphView },
  { path: '/learning/resources', name: 'learning-resources', component: LearningResourceView },
  { path: '/marketplace', name: 'marketplace', component: MarketplaceView },
  { path: '/marketplace/chat', name: 'marketplace-chat', component: MarketplaceChatView },
  { path: '/forum', name: 'forum', component: ForumView },
  { path: '/forum/posts/:postId', name: 'forum-post', component: ForumPostView },
  { path: '/forum/users/:userId', name: 'forum-profile', component: ForumProfileView },
  { path: '/campus-services', name: 'campus-services', component: CampusServicesView },
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
  { path: '/resume/designer', name: 'resume-designer', component: ResumeDesigner },
  { path: '/resume/wizard', name: 'resume-wizard', component: ResumeWizard },
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
