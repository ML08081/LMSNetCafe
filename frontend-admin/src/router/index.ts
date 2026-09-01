import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppLayout from '../layouts/AppLayout.vue'
import DashboardView from '../views/dashboard/DashboardView.vue'
import WorkbenchView from '../views/workbench/WorkbenchView.vue'

export const moduleRoutes = [
  { path: '', name: 'dashboard', component: DashboardView, meta: { title: '经营看板', permission: 'dashboard:view', icon: 'Monitor' } },
  { path: 'workbench', name: 'workbench', component: WorkbenchView, meta: { title: '前台工作台', permission: 'workbench:view', icon: 'Tickets' } },
  { path: 'members', name: 'members', component: () => import('../views/member/MemberView.vue'), meta: { title: '会员管理', permission: 'member:manage', icon: 'User' } },
  { path: 'devices', name: 'devices', component: () => import('../views/device/DeviceView.vue'), meta: { title: '设备监控', permission: 'device:view', icon: 'Cpu' } },
  { path: 'billing/rules', name: 'billing-rules', component: () => import('../views/billing/BillingRuleView.vue'), meta: { title: '计费规则', permission: 'billing:manage', icon: 'Coin' } },
  { path: 'sessions', name: 'sessions', component: () => import('../views/session/SessionView.vue'), meta: { title: '上机记录', permission: 'session:view', icon: 'Clock' } },
  { path: 'faces', name: 'faces', component: () => import('../views/face/FaceView.vue'), meta: { title: '人脸认证', permission: 'face:manage', icon: 'Camera' } },
  { path: 'service-desk', name: 'service-desk', component: () => import('../views/service/ServiceDeskView.vue'), meta: { title: '服务与订单', permission: 'service:manage', icon: 'ShoppingCart' } },
  { path: 'statistics', name: 'statistics', component: () => import('../views/statistics/StatisticsView.vue'), meta: { title: '数据统计', permission: 'statistics:view', icon: 'DataAnalysis' } },
  { path: 'system/users', name: 'system-users', component: () => import('../views/system/SystemUserView.vue'), meta: { title: '系统用户', permission: 'system:user', icon: 'Setting' } },
  { path: 'maintenance', name: 'maintenance', component: () => import('../views/maintenance/MaintenanceView.vue'), meta: { title: '维修维护', permission: 'maintenance:manage', icon: 'Tools' } },
  { path: 'portal', name: 'portal-home', component: () => import('../views/portal/PortalHomeView.vue'), meta: { title: '我的首页', permission: 'portal:home', icon: 'House' } },
  { path: 'portal/account', name: 'portal-account', component: () => import('../views/portal/PortalAccountView.vue'), meta: { title: '我的账户', permission: 'portal:account', icon: 'Wallet' } },
  { path: 'portal/sessions', name: 'portal-sessions', component: () => import('../views/portal/PortalSessionsView.vue'), meta: { title: '上机记录', permission: 'portal:sessions', icon: 'TrendCharts' } },
  { path: 'portal/devices', name: 'portal-devices', component: () => import('../views/portal/PortalDevicesView.vue'), meta: { title: '机位与计费', permission: 'portal:devices', icon: 'MapLocation' } },
  { path: 'portal/services', name: 'portal-services', component: () => import('../views/portal/PortalServicesView.vue'), meta: { title: '呼叫与点餐', permission: 'portal:services', icon: 'ShoppingCart' } },
  { path: 'portal/support', name: 'portal-support', component: () => import('../views/portal/PortalSupportView.vue'), meta: { title: '故障反馈', permission: 'portal:support', icon: 'Service' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/login/LoginView.vue'), meta: { public: true } },
    { path: '/', component: AppLayout, children: moduleRoutes },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return auth.token ? auth.homePath : true
  }

  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!auth.user) {
    try {
      await auth.fetchProfile()
    } catch {
      auth.logout()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  const permission = to.meta.permission as string | undefined
  if (permission && !auth.hasPermission(permission)) {
    const firstAllowed = moduleRoutes.find((route) => auth.hasPermission(route.meta.permission as string))
    return firstAllowed ? `/${firstAllowed.path}`.replace(/\/$/, '/') : '/login'
  }

  return true
})

export default router
