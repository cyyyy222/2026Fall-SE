import { createRouter, createWebHashHistory } from 'vue-router';
import { useAppStore } from '../stores/app';
import type { Role } from '../types';

export const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: (_to, _from, saved) => saved || { top: 0 },
  routes: [
    { path: '/', redirect: '/events' },
    {
      path: '/events',
      component: () => import('../views/DiscoverView.vue'),
      meta: { title: '发现活动' },
    },
    {
      path: '/events/:eventId',
      component: () => import('../views/EventView.vue'),
      meta: { title: '活动详情' },
    },
    ...['login', 'register', 'recover'].map((name) => ({
      path: `/${name}`,
      component: () => import('../views/AuthView.vue'),
      meta: {
        title: { login: '登录', register: '注册', recover: '找回密码' }[name],
        authPage: true,
      },
    })),
    {
      path: '/force-password',
      component: () => import('../views/AccountView.vue'),
      meta: { title: '首次修改密码', auth: true },
    },
    {
      path: '/account',
      component: () => import('../views/AccountView.vue'),
      meta: { title: '账号与安全', auth: true },
    },
    {
      path: '/messages/:messageId?',
      component: () => import('../views/MessagesView.vue'),
      meta: { title: '站内消息', auth: true },
    },
    {
      path: '/orders',
      component: () => import('../views/OrdersView.vue'),
      meta: { title: '我的订单', roles: ['PARTICIPANT'] },
    },
    {
      path: '/orders/:orderId/:step?',
      component: () => import('../views/OrderView.vue'),
      meta: { title: '订单详情', roles: ['PARTICIPANT'] },
    },
    {
      path: '/tickets/:ticketId',
      component: () => import('../views/TicketView.vue'),
      meta: { title: '我的电子票', roles: ['PARTICIPANT'] },
    },
    {
      path: '/appeals',
      component: () => import('../views/AppealsView.vue'),
      meta: { title: '我的申诉', roles: ['PARTICIPANT'] },
    },
    {
      path: '/reports/:action?',
      component: () => import('../views/ReportsView.vue'),
      meta: { title: '活动举报', roles: ['PARTICIPANT'] },
    },
    {
      path: '/organizer/events',
      component: () => import('../views/OrganizerView.vue'),
      meta: { title: '活动工作台', roles: ['ORGANIZER'] },
    },
    {
      path: '/organizer/events/new',
      component: () => import('../views/EventEditorView.vue'),
      meta: { title: '新建活动', roles: ['ORGANIZER'] },
    },
    {
      path: '/organizer/events/:eventId/edit',
      component: () => import('../views/EventEditorView.vue'),
      meta: { title: '编辑草稿', roles: ['ORGANIZER'] },
    },
    {
      path: '/organizer/events/:eventId',
      component: () => import('../views/ManageEventView.vue'),
      meta: { title: '活动管理', roles: ['ORGANIZER'] },
    },
    ...['organizer', 'admin'].flatMap((role) =>
      ['attendees', 'statistics'].map((section) => ({
        path: `/${role}/events/:eventId/${section}`,
        component: () => import('../views/ReportingView.vue'),
        meta: {
          title: section === 'attendees' ? '活动名单' : '统计与计数',
          roles: [role === 'organizer' ? 'ORGANIZER' : 'ADMIN'],
          section,
        },
      })),
    ),
    {
      path: '/checker',
      component: () => import('../views/CheckerView.vue'),
      meta: { title: '现场验票', roles: ['CHECKER'] },
    },
    ...['attendees', 'statistics'].map((section) => ({
      path: `/checker/${section}`,
      component: () => import('../views/ReportingView.vue'),
      meta: {
        title: section === 'attendees' ? '授权场次名单' : '签到统计',
        roles: ['CHECKER'],
        section,
      },
    })),
    {
      path: '/admin/events/:eventId',
      component: () => import('../views/ManageEventView.vue'),
      meta: { title: '活动审核详情', roles: ['ADMIN'] },
    },
    {
      path: '/admin/appeals',
      component: () => import('../views/AppealsAdminView.vue'),
      meta: { title: '处罚申诉', roles: ['ADMIN'] },
    },
    {
      path: '/admin/:section',
      component: () => import('../views/AdminView.vue'),
      meta: { title: '平台管理', roles: ['ADMIN'] },
    },
    {
      path: '/forbidden',
      component: () => import('../views/NotFoundView.vue'),
      meta: { title: '暂无权限', forbidden: true },
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('../views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
});
router.beforeEach(async (to) => {
  const app = useAppStore();
  await app.initialize();
  const protectedPage = to.meta.auth || to.meta.roles;
  if (protectedPage && !app.user) return { path: '/login', query: { redirect: to.fullPath } };
  if (app.user?.mustChangePassword && to.path !== '/force-password' && !to.meta.authPage)
    return '/force-password';
  if (to.meta.roles && app.user && !(to.meta.roles as Role[]).includes(app.user.role))
    return '/forbidden';
  if (to.path === '/login' && app.user) return app.home;
  document.title = `${to.meta.title || '活动票务'} · 活动票务`;
});
