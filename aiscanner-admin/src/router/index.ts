import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/',
      component: () => import('../components/AppLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'Dashboard', component: () => import('../views/DashboardView.vue') },
        { path: 'detections', name: 'Detections', component: () => import('../views/DetectionsView.vue') },
        { path: 'blacklist', name: 'Blacklist', component: () => import('../views/BlacklistView.vue') },
        { path: 'keys', name: 'Keys', component: () => import('../views/KeysView.vue') },
        { path: 'health', name: 'Health', component: () => import('../views/HealthView.vue') },
        { path: 'dlq', name: 'Dlq', component: () => import('../views/DlqView.vue') },
      ],
    },
  ],
})

router.beforeEach((to, _from) => {
  const auth = useAuthStore()
  if (to.name !== 'Login' && !auth.isAuthenticated) {
    return { name: 'Login' }
  }
})

export default router
