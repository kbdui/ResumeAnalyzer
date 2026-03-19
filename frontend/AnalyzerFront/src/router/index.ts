import { createRouter, createWebHistory } from 'vue-router'
import UploadPage from '../views/UploadPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/upload',
    },
    {
      path: '/upload',
      name: 'upload',
      component: UploadPage,
    },
    {
      path: '/task',
      name: 'task',
      component: () => import('../views/TaskPage.vue'),
    },
    {
      path: '/analyze',
      name: 'analyze',
      component: () => import('../views/AnalyzePage.vue'),
    },
  ],
})

export default router
