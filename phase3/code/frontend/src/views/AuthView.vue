<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAppStore } from '../stores/app';
import { post, isMock } from '../api/client';
import { useTask } from '../composables/useTask';
import type { Role } from '../types';
import AppIcon from '../components/AppIcon.vue';
import PosterArt from '../components/PosterArt.vue';
const route = useRoute();
const router = useRouter();
const app = useAppStore();
const task = useTask();
const mode = computed(() => route.path.slice(1));
const complete = ref(false);
const fields = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: '',
  role: 'PARTICIPANT' as Role,
});
const showHttpDemos = !isMock && import.meta.env.VITE_DEMO_ACCOUNTS === 'true';
const demos = isMock
  ? [
      ['participant', '参与者', 'Ticket'],
      ['organizer', '组织者', 'CalendarDays'],
      ['checker', '检票员', 'ScanLine'],
      ['admin', '管理员', 'ShieldCheck'],
    ]
  : [
      ['participant_demo', '参与者', 'Ticket'],
      ['organizer_demo', '组织者', 'CalendarDays'],
      ['checker_live_demo', '检票员', 'ScanLine'],
      ['admin_demo', '管理员', 'ShieldCheck'],
    ];
function selectDemo(username: string) {
  fields.username = username;
  fields.password = isMock
    ? 'Demo2026'
    : username === 'admin_demo'
      ? 'AdminDemo123'
      : 'DemoPass123';
}
const title = computed(() =>
  mode.value === 'register'
    ? '开启下一场相遇'
    : mode.value === 'recover'
      ? '找回你的账号'
      : '好久不见，欢迎回来',
);
async function submit() {
  await task.run(async () => {
    if (mode.value === 'login') {
      const home = await app.login(fields.username, fields.password);
      const redirect =
        typeof route.query.redirect === 'string' &&
        route.query.redirect.startsWith('/') &&
        !route.query.redirect.startsWith('//')
          ? route.query.redirect
          : home;
      await router.push(app.user?.mustChangePassword ? '/force-password' : redirect);
    } else if (mode.value === 'register') {
      if (fields.password !== fields.confirmPassword) throw new Error('两次密码输入不一致');
      await post('/auth/register', {
        username: fields.username,
        password: fields.password,
        email: fields.email,
        phone: fields.phone,
        role: fields.role,
      });
      app.toast('注册成功，请使用新账号登录');
      await router.push('/login');
    } else {
      await post('/auth/recoveries', { account: fields.username, phone: fields.phone });
      complete.value = true;
    }
  });
}
</script>
<template>
  <div class="auth-layout">
    <section class="auth-visual">
      <p class="eyebrow">LIFE HAPPENS IN GOOD COMPANY</p>
      <h1>让喜欢的事，<br />成为<em>生活的一部分。</em></h1>
      <p>不必等一个特别的日子，<br />去听一首歌，看一场展，遇见一些同频的人。</p>
      <div class="auth-art"><PosterArt :variant="4" /></div>
    </section>
    <div>
      <section class="auth-card">
        <p class="eyebrow">WELCOME TO GOOD MOMENTS</p>
        <h2>{{ title }}</h2>
        <p class="auth-subtitle">
          {{
            mode === 'login'
              ? '登录账号，继续探索值得期待的现场。'
              : mode === 'register'
                ? '一个账号，一种身份，一份属于你的期待。'
                : '提交登记账号与手机号，由管理员线下核验。'
          }}
        </p>
        <template v-if="complete"
          ><div class="alert">
            <AppIcon name="CheckCircle2" />
            <p>申请已登记，请等待管理员线下核验。提交手机号不会直接重置密码。</p>
          </div>
          <RouterLink class="btn primary full" to="/login" style="margin-top: 24px"
            >返回登录</RouterLink
          ></template
        >
        <form v-else class="auth-form" @submit.prevent="submit">
          <div v-if="mode === 'register'" class="registration-role">
            <button
              type="button"
              class="choice"
              :class="{ active: fields.role === 'PARTICIPANT' }"
              @click="fields.role = 'PARTICIPANT'"
            >
              我是参与者<small>发现活动，报名参加</small></button
            ><button
              type="button"
              class="choice"
              :class="{ active: fields.role === 'ORGANIZER' }"
              @click="fields.role = 'ORGANIZER'"
            >
              我是组织者<small>创建活动，发起相聚</small>
            </button>
          </div>
          <div class="form-field">
            <label for="username">{{ mode === 'recover' ? '登记账号' : '用户名' }}</label
            ><input
              id="username"
              v-model="fields.username"
              required
              autocomplete="username"
              placeholder="请输入用户名"
              maxlength="60"
            />
          </div>
          <div v-if="mode !== 'login'" class="form-field">
            <label for="phone">登记手机号</label
            ><input
              id="phone"
              v-model="fields.phone"
              required
              inputmode="tel"
              autocomplete="tel"
              pattern="1[0-9]{10}"
              placeholder="请输入11位手机号"
            />
          </div>
          <div v-if="mode === 'register'" class="form-field">
            <label for="email">邮箱</label
            ><input
              id="email"
              v-model="fields.email"
              type="email"
              required
              autocomplete="email"
              placeholder="请输入邮箱"
            />
          </div>
          <div v-if="mode !== 'recover'" class="form-field">
            <label for="password">密码</label
            ><input
              id="password"
              v-model="fields.password"
              type="password"
              required
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              :minlength="mode === 'register' ? 8 : undefined"
              maxlength="72"
              :placeholder="mode === 'register' ? '至少8位，同时包含字母和数字' : '请输入密码'"
            />
          </div>
          <div v-if="mode === 'register'" class="form-field">
            <label for="password-confirm">确认密码</label
            ><input
              id="password-confirm"
              v-model="fields.confirmPassword"
              type="password"
              required
              autocomplete="new-password"
              placeholder="再次输入密码"
            />
          </div>
          <div v-if="mode === 'login'" class="auth-links">
            <span>登录后固定7天有效</span><RouterLink to="/recover">忘记密码？</RouterLink>
          </div>
          <p v-if="task.error.value" class="error-inline" role="alert">{{ task.error.value }}</p>
          <button class="btn primary full" :disabled="task.busy.value">
            <AppIcon v-if="task.busy.value" name="LoaderCircle" class="spin" />{{
              mode === 'login' ? '登录' : mode === 'register' ? '创建账号' : '提交人工找回申请'
            }}<AppIcon name="ArrowRight" :size="16" />
          </button>
          <p class="auth-bottom">
            <template v-if="mode === 'login'"
              >还没有账号？<RouterLink to="/register" class="text-link"
                >立即注册</RouterLink
              ></template
            ><template v-else
              >已经有账号？<RouterLink to="/login" class="text-link">返回登录</RouterLink></template
            >
          </p>
        </form>
        <div v-if="mode === 'login' && (isMock || showHttpDemos)" class="auth-demo">
          <div class="auth-demo-title">
            <span>选择演示账号，填入登录信息</span
            ><span>{{ isMock ? '本地模拟' : '真实接口 · 本机演示' }}</span>
          </div>
          <div class="demo-accounts">
            <button
              v-for="demo in demos"
              :key="demo[0]"
              class="demo-account"
              :class="{ active: fields.username === demo[0] }"
              @click="selectDemo(demo[0]!)"
            >
              <AppIcon :name="demo[2]!" /><span>{{ demo[1] }}</span>
            </button>
          </div>
          <p class="demo-password">
            {{
              isMock
                ? '统一密码：Demo2026 · 每个账号身份固定'
                : '参与者/组织者/检票员：DemoPass123 · 管理员初始口令：AdminDemo123，首次登录须修改'
            }}
          </p>
        </div>
      </section>
      <p v-if="isMock" class="auth-safety">
        当前为前端演示，信息仅保存在本机浏览器。请使用演示资料，不要输入真实密码或个人信息。
      </p>
      <p v-else-if="showHttpDemos" class="auth-safety">
        当前通过 Java 后端读写
        MySQL。支付为本地模拟，不会实际扣款；管理员修改口令后请手动填写新口令。
      </p>
    </div>
  </div>
</template>
