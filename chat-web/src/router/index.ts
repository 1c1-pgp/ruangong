import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { guest: true } },
    { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { guest: true } },
    {
      path: '/',
      component: () => import('@/views/ChatView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to) => {
  const session = useSessionStore()
  if (to.meta.requiresAuth && !session.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guest && session.token) {
    return { path: '/' }
  }
  return true
})

export default router
