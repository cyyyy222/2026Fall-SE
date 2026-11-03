<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAppStore } from './stores/app';
import { isMock } from './api/client';
import { roleNames } from './utils/format';
import AppIcon from './components/AppIcon.vue';
const app = useAppStore();
const route = useRoute();
const router = useRouter();
const menuOpen = ref(false);
const workspace = computed(() => /^\/(organizer|admin|checker)(\/|$)/.test(route.path));
let unreadTimer: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  void app.refreshUnreadMessages();
  unreadTimer = setInterval(() => void app.refreshUnreadMessages(), 60_000);
});
onUnmounted(() => {
  if (unreadTimer) clearInterval(unreadTimer);
});
watch(
  () => [route.fullPath, app.user?.id],
  () => void app.refreshUnreadMessages(),
);
const menus = computed(() =>
  app.user?.role === 'ORGANIZER'
    ? [
        ['/organizer/events', 'LayoutDashboard', '活动工作台'],
        ['/organizer/events?panel=attendees', 'UsersRound', '活动名单'],
        ['/organizer/events?panel=statistics', 'ChartNoAxesCombined', '统计与计数'],
        ['/messages', 'Bell', '站内消息'],
        ['/account', 'Settings2', '账号与安全'],
      ]
    : app.user?.role === 'CHECKER'
      ? [
          ['/checker', 'ScanLine', '现场验票'],
          ['/checker/attendees', 'UsersRound', '授权场次名单'],
          ['/checker/statistics', 'ChartNoAxesCombined', '签到统计'],
          ['/messages', 'Bell', '站内消息'],
          ['/account', 'Settings2', '账号与安全'],
        ]
      : [
          ['/admin/reviews', 'ClipboardCheck', '活动审核'],
          ['/admin/events', 'CalendarDays', '活动管理'],
          ['/admin/accounts', 'UsersRound', '账号分类管理'],
          ['/admin/categories', 'List', '活动类别'],
          ['/admin/reports', 'ShieldCheck', '举报与核查'],
          ['/admin/appeals', 'HelpCircle', '处罚申诉'],
          ['/admin/restrictions', 'LockKeyhole', '报名与发布限制'],
          ['/admin/payments', 'Ticket', '支付与退款'],
          ['/admin/audit', 'FileText', '操作记录'],
          ['/admin/jobs', 'RefreshCw', '后台处理进度'],
          ['/admin/recoveries', 'HelpCircle', '账号找回申请'],
        ],
);
const activeMenu = (path: string) =>
  path.includes('?')
    ? route.fullPath === path
    : (route.path === path || (path !== '/checker' && route.path.startsWith(`${path}/`))) &&
      (!route.query.panel || path !== '/organizer/events');
function skipToMain() {
  document.getElementById('main-content')?.focus();
  document.getElementById('main-content')?.scrollIntoView();
}
async function logout() {
  await app.logout();
  menuOpen.value = false;
  await router.push('/events');
}
</script>
<template>
  <a class="skip-link" href="#main-content" @click.prevent="skipToMain">跳到主要内容</a>
  <div
    class="app-shell"
    :class="{ 'workspace-shell': workspace, 'auth-shell': route.meta.authPage }"
  >
    <header class="site-header">
      <RouterLink to="/events" class="brand"
        ><span class="brand-mark"><AppIcon name="Ticket" :size="23" /></span
        ><span>活动票务<span class="brand-sub">GOOD MOMENTS</span></span></RouterLink
      >
      <nav v-if="!workspace && !route.meta.authPage" class="public-nav" aria-label="主导航">
        <RouterLink to="/events" :class="{ active: route.path.startsWith('/events') }"
          >发现活动</RouterLink
        >
        <RouterLink
          v-if="!app.user || app.user.role === 'PARTICIPANT'"
          to="/orders"
          :class="{ active: route.path.startsWith('/orders') || route.path.startsWith('/tickets') }"
          >我的订单</RouterLink
        >
        <RouterLink v-if="app.user && app.user.role !== 'PARTICIPANT'" :to="app.home"
          >{{ roleNames[app.user.role] }}工作台</RouterLink
        >
        <RouterLink
          v-if="app.user?.role === 'PARTICIPANT'"
          to="/reports"
          :class="{ active: route.path.startsWith('/reports') }"
          >我的举报</RouterLink
        >
        <RouterLink
          v-if="app.user?.role === 'PARTICIPANT'"
          to="/appeals"
          :class="{ active: route.path.startsWith('/appeals') }"
          >我的申诉</RouterLink
        >
      </nav>
      <div v-else-if="workspace" class="workspace-label">
        <span class="small-dot" />{{ app.user ? roleNames[app.user.role] : '' }}工作空间
      </div>
      <div class="header-right">
        <span v-if="isMock" class="demo-pill">前端演示</span
        ><span v-if="!workspace" class="location-label"
          ><AppIcon name="MapPin" :size="15" />南京</span
        >
        <template v-if="app.user"
          ><RouterLink to="/messages" class="icon-btn notification-link" aria-label="站内消息"
            ><AppIcon name="Bell" :size="20" /><span
              v-if="app.unreadMessages > 0"
              class="notification-dot"
          /></RouterLink>
          <div class="account-menu">
            <button class="avatar-button" @click="menuOpen = !menuOpen" :aria-expanded="menuOpen">
              <span class="avatar">{{
                (app.user.displayName || app.user.username).slice(0, 1)
              }}</span
              ><span class="user-name">{{ app.user.displayName || app.user.username }}</span
              ><AppIcon name="ChevronDown" :size="13" />
            </button>
            <div v-if="menuOpen" class="dropdown">
              <p>{{ roleNames[app.user.role] }} · 固定身份</p>
              <RouterLink to="/account" @click="menuOpen = false"
                ><AppIcon name="UserRound" />账号与安全</RouterLink
              ><RouterLink :to="app.home" @click="menuOpen = false"
                ><AppIcon name="LayoutDashboard" />我的工作空间</RouterLink
              ><button @click="logout"><AppIcon name="LogOut" />退出登录</button>
            </div>
          </div></template
        >
        <RouterLink v-else to="/login" class="btn primary small"
          >登录 / 注册<AppIcon name="ArrowUpRight" :size="15"
        /></RouterLink>
      </div>
    </header>
    <aside v-if="workspace" class="sidebar">
      <p class="nav-caption">工作空间</p>
      <nav aria-label="工作空间导航">
        <RouterLink
          v-for="item in menus"
          :key="item[0]"
          :to="item[0]!"
          :class="{ selected: activeMenu(item[0]!) }"
          ><AppIcon :name="item[1]!" />{{ item[2] }}</RouterLink
        >
      </nav>
      <div class="sidebar-note">
        <span class="sidebar-note-icon"><AppIcon name="Leaf" /></span>
        <p>让每一次相聚，<br />都从容一点。</p>
        <RouterLink to="/events"
          >前往发现活动 <AppIcon name="ArrowUpRight" :size="14"
        /></RouterLink>
      </div>
      <div class="sidebar-bottom">
        <AppIcon name="ShieldCheck" :size="15" />固定角色 · 按权限访问
      </div>
    </aside>
    <main id="main-content" tabindex="-1" :class="workspace ? 'workspace-main' : 'public-main'">
      <RouterView :key="route.fullPath" />
    </main>
    <footer
      v-if="!route.meta.authPage"
      class="site-footer"
      :class="{ 'workspace-footer': workspace }"
    >
      <span class="footer-brand">活动票务<span> / </span>让每一次相聚，都从容一点。</span
      ><span
        >{{ isMock ? '本地模拟数据 · 尚未连接 Java 后端' : '活动票务与报名系统'
        }}<span class="footer-year">© {{ new Date().getFullYear() }}</span></span
      >
    </footer>
  </div>
  <Transition name="toast"
    ><div
      v-if="app.notification"
      class="toast-message"
      :class="app.notification.type"
      role="status"
    >
      <AppIcon
        :name="app.notification.type === 'success' ? 'CheckCircle2' : 'CircleAlert'"
      /><span>{{ app.notification.message }}</span
      ><button class="icon-btn" aria-label="关闭提示" @click="app.notification = null">
        <AppIcon name="X" :size="16" />
      </button></div
  ></Transition>
</template>
