import { createRouter, createWebHistory } from 'vue-router'

import AiAssistantView from '../views/AiAssistantView.vue'
import EduAccountView from '../views/EduAccountView.vue'
import AiToolsView from '../views/AiToolsView.vue'
import CampusActivitiesView from '../views/CampusActivitiesView.vue'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import MapView from '../views/MapView.vue'
import MeetingsView from '../views/MeetingsView.vue'
import MessageView from '../views/MessageView.vue'
import MineActivitiesView from '../views/MineActivitiesView.vue'
import MineAiHistoryView from '../views/MineAiHistoryView.vue'
import MineMeetingScheduleView from '../views/MineMeetingScheduleView.vue'
import MineMessagesView from '../views/MineMessagesView.vue'
import MineScheduleView from '../views/MineScheduleView.vue'
import MineView from '../views/MineView.vue'
import ExamPapersView from '../views/ExamPapersView.vue'
import ExamTakingView from '../views/ExamTakingView.vue'
import ExamResultView from '../views/ExamResultView.vue'
import ExamDetailView from '../views/ExamDetailView.vue'
import ExamHistoryView from '../views/ExamHistoryView.vue'
import PeriodTimeConfigView from '../views/PeriodTimeConfigView.vue'
import ScheduleSettingsView from '../views/ScheduleSettingsView.vue'
import SemesterManageView from '../views/SemesterManageView.vue'
import ResumeView from '../views/ResumeView.vue'
import ResumeDesigner from '../views/ResumeDesigner.vue'
import ResumeWizard from '../views/ResumeWizard.vue'
import { getToken } from '../utils/auth'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/home', name: 'home', component: HomeView },
  { path: '/map', name: 'map', component: MapView },
  { path: '/activities', name: 'activities', component: CampusActivitiesView },
  { path: '/meetings', name: 'meetings', component: MeetingsView },
  { path: '/ai', name: 'ai', component: AiAssistantView },
  { path: '/ai-tools', name: 'ai-tools', component: AiToolsView },
  { path: '/mine', name: 'mine', component: MineView },
  { path: '/messages', name: 'messages', component: MessageView },
  { path: '/mine/messages', name: 'mine-messages', component: MineMessagesView },
  { path: '/mine/schedule', name: 'mine-schedule', component: MineScheduleView },
  { path: '/mine/schedule-settings', name: 'mine-schedule-settings', component: ScheduleSettingsView },
  { path: '/mine/period-time', name: 'mine-period-time', component: PeriodTimeConfigView },
  { path: '/mine/semester', name: 'mine-semester', component: SemesterManageView },
  { path: '/mine/edu-account', name: 'mine-edu-account', component: EduAccountView },
  { path: '/mine/meeting-schedule', name: 'mine-meeting-schedule', component: MineMeetingScheduleView },
  { path: '/mine/activities', name: 'mine-activities', component: MineActivitiesView },
  { path: '/mine/ai-history', name: 'mine-ai-history', component: MineAiHistoryView },
  { path: '/mine/papers', name: 'mine-papers', component: ExamPapersView },
  { path: '/mine/papers/attempts/:attemptId', name: 'exam-taking', component: ExamTakingView },
  { path: '/mine/papers/:paperId/history', name: 'exam-history', component: ExamHistoryView },
  { path: '/mine/papers/results/:attemptId', name: 'exam-result', component: ExamResultView },
  { path: '/mine/papers/results/:attemptId/details', name: 'exam-detail', component: ExamDetailView },
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
