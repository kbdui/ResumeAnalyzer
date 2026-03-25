import { createRouter, createWebHistory } from 'vue-router'
import UploadAndScreenPage from '../views/UploadAndScreenPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 默认重定向到筛选页面
    {
      path: '/',
      redirect: '/screen',
    },
    {
      path: '/screen',
      name: 'screen',
      component: UploadAndScreenPage,
    },
    {
      path: '/analyze',
      name: 'analyze',
      component: () => import('../views/AnalyzePage.vue'),
    },
  ],
})

export default router
