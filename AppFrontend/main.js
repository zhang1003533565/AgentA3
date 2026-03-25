import App from './App'

// #ifndef VUE3
import Vue from 'vue'
import './uni.promisify.adaptor'
Vue.config.productionTip = false
App.mpType = 'app'
const app = new Vue({
  ...App
})
app.$mount()
// #endif

// #ifdef VUE3
import { createSSRApp } from 'vue'
import NavFixed from '@/components/nav-fixed/nav-fixed.vue'
export function createApp() {
  const app = createSSRApp(App)
  // 全局注册固定导航组件
  app.component('NavFixed', NavFixed)
  return {
    app
  }
}
// #endif