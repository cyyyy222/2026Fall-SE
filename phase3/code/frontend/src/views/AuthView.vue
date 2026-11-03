<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAppStore } from '../stores/app';
import { post, isMock } from '../api/client';
import { useTask } from '../composables/useTask';
import type { Role } from '../types';
import AppIcon from '../components/AppIcon.vue';
import PosterArt from '../components/PosterArt.vue';
import AppModal from '../components/AppModal.vue';
import { ApiError } from '../api/errors';
import AccountRecoveryPanel from '../components/AccountRecoveryPanel.vue';
const route = useRoute();
const router = useRouter();
const app = useAppStore();
const task = useTask();
const codeTask = useTask();
const loginMethod = ref<'password' | 'phone'>('password');
const verificationId = ref('');
const verificationCode = ref('');
const challengeScope = ref('');
const duplicateAccount = ref(false);
const resendAt = ref(0);
const clockNow = ref(Date.now());
const timer = setInterval(() => {
  clockNow.value = Date.now();
}, 1000);
onUnmounted(() => clearInterval(timer));
const resendSeconds = computed(() =>
  Math.max(0, Math.ceil((resendAt.value - clockNow.value) / 1000)),
);
const mode = computed(() => route.path.slice(1));
const fields = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: '',
  role: 'PARTICIPANT' as Role,
});
const codePurpose = computed(() => (mode.value === 'register' ? 'REGISTER' : 'LOGIN'));
const scope = computed(() => `${fields.phone}:${fields.role}:${codePurpose.value}`);
watch(scope, () => {
  verificationId.value = '';
  verificationCode.value = '';
  challengeScope.value = '';
  resendAt.value = 0;
});
async function sendCode() {
  await codeTask.run(async () => {
    if (!/^1\d{10}$/.test(fields.phone)) throw new Error('请先填写有效的11位手机号');
    const requestedScope = scope.value;
    const receipt = await post<{ verificationId: string; resendAfterSeconds: number }>(
      '/auth/phone-codes',
      {
        phone: fields.phone,
        role: fields.role,
        purpose: codePurpose.value,
      },
    );
    if (scope.value !== requestedScope) {
      app.toast('手机号或角色已变化，请重新获取验证码', 'error');
      return;
    }
    verificationId.value = receipt.verificationId;
    challengeScope.value = requestedScope;
    verificationCode.value = '';
    clockNow.value = Date.now();
    resendAt.value = Date.now() + receipt.resendAfterSeconds * 1000;
    app.toast('验证码已生成，请在后端终端查看（本地演示，不会发送短信），5分钟内有效');
  });
}
function phoneInput(purpose: 'LOGIN' | 'REGISTER') {
  if (!verificationId.value || challengeScope.value !== scope.value)
    throw new Error('手机号或角色已变化，请重新获取验证码');
  return {
    phone: fields.phone,
    role: fields.role,
    verificationId: verificationId.value,
    verificationCode: verificationCode.value,
    purpose,
  };
}
async function finishLogin(home: string) {
  const redirect =
    typeof route.query.redirect === 'string' &&
    route.query.redirect.startsWith('/') &&
    !route.query.redirect.startsWith('//')
      ? route.query.redirect
      : home;
  await router.push(app.user?.mustChangePassword ? '/force-password' : redirect);
}
async function loginExisting() {
  await task.run(async () => {
    const home = await app.phoneLogin(phoneInput('REGISTER'));
    duplicateAccount.value = false;
    await finishLogin(home);
  });
}
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
  loginMethod.value = 'password';
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
      const home =
        loginMethod.value === 'phone'
          ? await app.phoneLogin(phoneInput('LOGIN'))
          : await app.login(fields.username, fields.password);
      await finishLogin(home);
    } else if (mode.value === 'register') {
      if (fields.password !== fields.confirmPassword) throw new Error('两次密码输入不一致');
      try {
        await post('/auth/register', {
          username: fields.username,
          password: fields.password,
          email: fields.email.trim() || null,
          phone: fields.phone,
          role: fields.role,
          verificationId: phoneInput('REGISTER').verificationId,
          verificationCode: verificationCode.value,
        });
      } catch (e) {
        if (e instanceof ApiError && e.code === 'PHONE_ACCOUNT_EXISTS') {
          duplicateAccount.value = true;
          return;
        }
        throw e;
      }
      app.toast('注册成功，请使用新账号登录');
      await router.push('/login');
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
                : '优先通过手机号验证码找回，原号不可用时提交详细申诉。'
          }}
        </p>
        <AccountRecoveryPanel v-if="mode === 'recover'" />
        <form v-else class="auth-form" @submit.prevent="submit">
          <div v-if="mode === 'login'" class="tabs" aria-label="登录方式">
            <button
              type="button"
              :class="{ active: loginMethod === 'password' }"
              @click="loginMethod = 'password'"
            >
              账号密码登录
            </button>
            <button
              type="button"
              :class="{ active: loginMethod === 'phone' }"
              @click="loginMethod = 'phone'"
            >
              手机号验证码登录
            </button>
          </div>
          <p v-if="mode === 'login' && loginMethod === 'phone'" class="field-help">
            请选择要登录的身份；管理员和检票员请使用账号密码登录。
          </p>
          <div
            v-if="mode === 'register' || (mode === 'login' && loginMethod === 'phone')"
            class="registration-role"
          >
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
          <div v-if="mode !== 'login' || loginMethod === 'password'" class="form-field">
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
          <div v-if="mode !== 'login' || loginMethod === 'phone'" class="form-field">
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
          <p v-if="mode === 'register'" class="field-help">
            同一个手机号可以分别注册 1 个参与者账号和 1 个组织者账号，但同一角色只能有 1
            个。用户名在所有账号中不能重复；邮箱可以不填，填了也不能和其他账号重复。
          </p>
          <div
            v-if="mode === 'register' || (mode === 'login' && loginMethod === 'phone')"
            class="form-field"
          >
            <label for="phone-code">手机验证码</label>
            <div class="verification-input">
              <input
                id="phone-code"
                v-model="verificationCode"
                required
                inputmode="numeric"
                autocomplete="one-time-code"
                pattern="[0-9]{6}"
                maxlength="6"
                placeholder="请输入6位验证码"
              />
              <button
                type="button"
                class="btn secondary"
                :disabled="codeTask.busy.value || resendSeconds > 0"
                @click="sendCode"
              >
                {{ resendSeconds ? `${resendSeconds}秒后重发` : '获取验证码' }}
              </button>
            </div>
            <small
              >本地演示：验证码不会真的发短信，会显示在后端终端或 backend-local.log 中，5
              分钟内有效。</small
            >
            <p v-if="codeTask.error.value" class="error-inline" role="alert">
              {{ codeTask.error.value }}
            </p>
          </div>
          <div v-if="mode === 'recover'" class="alert info">
            <p>
              填写注册用户名和当时登记的手机号，提交后会进入管理员的“账号找回申请”。平台工作人员通过当面核对或已有联系渠道确认本人身份，再重置临时密码；这里不会自动发短信或直接修改密码。管理员、检票员请联系平台管理员处理。
            </p>
          </div>
          <div v-if="mode === 'register'" class="form-field">
            <label for="email">邮箱（选填）</label
            ><input
              id="email"
              v-model="fields.email"
              type="email"
              autocomplete="email"
              placeholder="选填，例如12345@qq.com或name@163.com"
            />
          </div>
          <div
            v-if="mode !== 'recover' && (mode !== 'login' || loginMethod === 'password')"
            class="form-field"
          >
            <label for="password">密码</label
            ><input
              id="password"
              v-model="fields.password"
              type="password"
              required
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              :minlength="mode === 'register' ? 8 : undefined"
              :pattern="
                mode === 'register' ? '(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}' : undefined
              "
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
            <span>登录后 7 天内有效</span><RouterLink to="/recover">忘记密码？</RouterLink>
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
            ><span>{{ isMock ? '本地模拟' : '本机联调 · 演示账号' }}</span>
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
        当前为本机联调环境，数据保存在
        MySQL。支付为本地模拟，不会实际扣款；管理员修改口令后请手动填写新口令。
      </p>
    </div>
  </div>
  <AppModal
    :open="duplicateAccount"
    title="这个手机号已有账号，是你的吗？"
    @close="duplicateAccount = false"
  >
    <p>
      这个手机号已经有一个{{
        fields.role === 'ORGANIZER' ? '组织者' : '参与者'
      }}账号，同一角色不能重复注册。如果这是你的账号，可以直接登录。
    </p>
    <p class="field-help">
      如果不是你的账号，请联系平台工作人员核实手机号归属；平台不会覆盖或删除原账号。另一种身份仍可单独注册。
    </p>
    <p v-if="task.error.value" class="error-inline">{{ task.error.value }}</p>
    <div class="button-row">
      <button class="btn secondary" @click="duplicateAccount = false">不是我的，返回核实</button
      ><button class="btn primary" :disabled="task.busy.value" @click="loginExisting">
        这是我的账号，直接登录
      </button>
    </div>
  </AppModal>
</template>
