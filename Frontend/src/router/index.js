import { createRouter, createWebHistory } from 'vue-router'

import AiAssistantView from '../views/AiAssistantView.vue'
import AiToolsView from '../views/AiToolsView.vue'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import MapView from '../views/MapView.vue'
import MeetingsView from '../views/MeetingsView.vue'
import MineView from '../views/MineView.vue'
import ResumeView from '../views/ResumeView.vue'
import { getToken } from '../utils/auth'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/home', name: 'home', component: HomeView, meta: { public: true } },
  { path: '/map', name: 'map', component: MapView, meta: { public: true } },
  { path: '/meetings', name: 'meetings', component: MeetingsView, meta: { public: true } },
  { path: '/ai', name: 'ai', component: AiAssistantView, meta: { public: true } },
  { path: '/ai-tools', name: 'ai-tools', component: AiToolsView, meta: { public: true } },
  { path: '/mine', name: 'mine', component: MineView, meta: { public: true } },
  { path: '/resume', name: 'resume', component: ResumeView, meta: { public: true } },
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
