<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()

const links = [
  { to: '/learning', label: '题库', match: (path) => path === '/learning' || path.startsWith('/learning/practice') },
  { to: '/learning/plan', label: '学习规划', match: (path) => path === '/learning/plan' || path === '/learning/resources' },
  { to: '/learning/knowledge-graph', label: '知识图谱', match: (path) => path === '/learning/knowledge-graph' },
]

function isActive(link) {
  return link.match(route.path)
}
</script>

<template>
  <nav class="py-learning-nav" aria-label="Python 学习导航">
    <RouterLink
      v-for="link in links"
      :key="link.to"
      :to="link.to"
      class="py-learning-nav__link"
      :class="{ 'py-learning-nav__link--active': isActive(link) }"
    >
      {{ link.label }}
    </RouterLink>
  </nav>
</template>

<style scoped>
.py-learning-nav {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid #e1e7ed;
  border-radius: 8px;
  background: #fff;
}

.py-learning-nav__link {
  padding: 8px 16px;
  border-radius: 6px;
  color: #667085;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}

.py-learning-nav__link:hover {
  color: #344054;
  background: #f4f7fb;
}

.py-learning-nav__link--active {
  color: #2f76bd;
  background: #eaf4fd;
}
</style>
