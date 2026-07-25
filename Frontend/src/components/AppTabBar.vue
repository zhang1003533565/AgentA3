<script setup>
defineProps({
  embedded: {
    type: Boolean,
    default: false,
  },
})
</script>

<template>
  <header class="app-site-header" :class="{ 'app-site-header--embedded': embedded }">
    <div class="app-site-header__inner">
      <RouterLink class="app-site-header__brand" to="/home">
        数智<span>诊断</span>港
      </RouterLink>

      <nav class="app-site-header__nav" aria-label="主导航">
        <RouterLink to="/home">首页</RouterLink>
        <RouterLink to="/map">校园地图</RouterLink>
        <RouterLink to="/activities">校园活动</RouterLink>
        <RouterLink to="/meetings">会议</RouterLink>
        <RouterLink to="/ai">AI 助手</RouterLink>
        <RouterLink to="/ai-tools">AI 工具</RouterLink>
        <RouterLink to="/mine">我的页面</RouterLink>
        <RouterLink to="/resume">我的简历</RouterLink>
      </nav>

      <RouterLink class="app-site-header__login" to="/login">登录 / 注册</RouterLink>
    </div>
  </header>
</template>

<style scoped>
.app-site-header {
  position: fixed;
  inset: 0 0 auto;
  z-index: 20;
  height: 60px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: #1e2b4c;
  color: #ffffff;
}

.app-site-header--embedded {
  position: static;
}

.app-site-header__inner {
  display: flex;
  align-items: center;
  width: min(1200px, calc(100% - 40px));
  height: 100%;
  margin: 0 auto;
  gap: 18px;
}

.app-site-header__brand {
  flex: 0 0 auto;
  color: #ffffff;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 1px;
  text-decoration: none;
  white-space: nowrap;
}

.app-site-header__brand span {
  color: #00b4ff;
}

.app-site-header__nav {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;
}

.app-site-header__nav::-webkit-scrollbar {
  display: none;
}

.app-site-header__nav a {
  display: grid;
  place-items: center;
  min-height: 36px;
  padding: 0 10px;
  border-radius: 8px;
  color: #ccd5e4;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.app-site-header__nav a:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
}

.app-site-header__nav a.router-link-active {
  color: #ffffff;
  background: rgba(0, 180, 255, 0.2);
}

.app-site-header__login {
  flex: 0 0 auto;
  padding: 8px 18px;
  border-radius: 4px;
  background: #00b4ff;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.app-site-header__login:hover {
  background: #009ee0;
}

@media (max-width: 680px) {
  .app-site-header__inner {
    width: min(100%, calc(100% - 24px));
    gap: 10px;
  }

  .app-site-header__brand {
    font-size: 16px;
  }

  .app-site-header__login {
    padding-inline: 12px;
  }
}
</style>
