import { createRouter, createWebHistory } from 'vue-router'

import AiAssistantView from '../views/AiAssistantView.vue'
import AiToolsView from '../views/AiToolsView.vue'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import MapView from '../views/MapView.vue'
import MeetingsView from '../views/MeetingsView.vue'
import MineView from '../views/MineView.vue'
import ExamPapersView from '../views/ExamPapersView.vue'
import ExamTakingView from '../views/ExamTakingView.vue'
import ExamResultView from '../views/ExamResultView.vue'
import ExamDetailView from '../views/ExamDetailView.vue'
import ExamHistoryView from '../views/ExamHistoryView.vue'
import ResumeView from '../views/ResumeView.vue'
import ResumeDesigner from '../views/ResumeDesigner.vue'
import ResumeWizard from '../views/ResumeWizard.vue'
import { getToken } from '../utils/auth'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/home', name: 'home', component: HomeView },
  { path: '/map', name: 'map', component: MapView },
  { path: '/meetings', name: 'meetings', component: MeetingsView },
  { path: '/ai', name: 'ai', component: AiAssistantView },
  { path: '/ai-tools', name: 'ai-tools', component: AiToolsView },
  { path: '/mine', name: 'mine', component: MineView },
  { path: '/mine/papers', name: 'exam-papers', component: ExamPapersView },
  { path: '/mine/papers/:paperId/history', name: 'exam-history', component: ExamHistoryView },
  { path: '/mine/papers/attempts/:attemptId', name: 'exam-taking', component: ExamTakingView },
  { path: '/mine/papers/results/:attemptId', name: 'exam-result', component: ExamResultView },
  { path: '/mine/papers/results/:attemptId/details', name: 'exam-detail', component: ExamDetailView },
  { path: '/resume', name: 'resume', component: ResumeView, meta: { public: true } },
  { path: '/resume/designer', name: 'resume-designer', component: ResumeDesigner, meta: { public: true } },
  { path: '/resume/wizard', name: 'resume-wizard', component: ResumeWizard, meta: { public: true } },
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
