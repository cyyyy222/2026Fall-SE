<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, patch, post, isMock } from '../api/client';
import { useAppStore } from '../stores/app';
import { useTask } from '../composables/useTask';
import type { Mutation, User, PhoneProof, Role } from '../types';
import { dateTime, roleNames } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import AppIcon from '../components/AppIcon.vue';
import AppModal from '../components/AppModal.vue';
import PhoneVerification from '../components/PhoneVerification.vue';
const app = useAppStore();
const route = useRoute();
const router = useRouter();
const task = useTask();
const forced = computed(() => route.path === '/force-password');
const normal = computed(() => ['PARTICIPANT', 'ORGANIZER'].includes(app.user?.role || ''));
const displayName = ref(app.user?.displayName || '');
const phoneModal = ref(false);
const newPhone = ref('');
const phoneProof = ref<PhoneProof | null>(null);
const passwords = reactive({ oldPassword: '', newPassword: '', confirm: '' });
const closeModal = ref(false);
const resetModal = ref(false);
const closure = ref<{
  eligible: boolean;
  blockers: { message: string }[];
  cancellationCount: number;
  nextEligibleAt: string | null;
}>();
const closePassword = ref('');
async function saveProfile() {
  await task.run(async () => {
    const r = await patch<Mutation<User>>('/me/profile', { displayName: displayName.value });
    app.user = r.current;
  }, '本人资料已更新，历史订单姓名保持原快照');
}
async function changePhone() {
  await task.run(async () => {
    if (!phoneProof.value) throw new Error('请先获取并填写新手机号验证码');
    await patch('/me/phone', { newPhone: newPhone.value, ...phoneProof.value });
    phoneModal.value = false;
    app.user = null;
    app.toast('手机号已换绑，请使用新手机号验证码或原账号密码重新登录');
    await router.push('/login');
  });
}
function openPhone() {
  phoneModal.value = true;
  newPhone.value = '';
  phoneProof.value = null;
}
async function changePassword() {
  await task.run(async () => {
    if (passwords.newPassword !== passwords.confirm) throw new Error('两次新密码输入不一致');
    await post('/me/password', {
      oldPassword: passwords.oldPassword,
      newPassword: passwords.newPassword,
    });
    app.user = null;
    await router.push('/login');
  }, '密码已修改，请重新登录');
}
async function loadClosure() {
  if (normal.value && !forced.value) {
    try {
      closure.value = await get('/me/closure-eligibility');
    } catch (e) {
      app.error(e);
    }
  }
}
async function closeAccount() {
  await task.run(async () => {
    await post('/me/closure', { password: closePassword.value, confirmation: 'CLOSE' });
    app.user = null;
    await router.push('/events');
  }, '账号已注销');
}
async function reset() {
  const { resetDemo } = await import('../mocks/adapter');
  resetDemo();
  app.user = null;
  window.location.hash = '/events';
  window.location.reload();
}
onMounted(loadClosure);
</script>
<template>
  <PageHeading
    eyebrow="YOUR SPACE, YOUR PEACE OF MIND"
    :title="forced ? '请先修改初始密码' : '账号与安全'"
    :description="
      forced
        ? '修改密码前，仅开放账号必要操作。完成后需重新登录。'
        : '管理本人资料，保护每一份相聚的期待。'
    "
  />
  <div class="account-layout">
    <aside v-if="!forced" class="panel profile-card">
      <span class="avatar">{{
        (app.user?.displayName || app.user?.username || '').slice(0, 1)
      }}</span>
      <h2>{{ app.user?.displayName || app.user?.username }}</h2>
      <span class="badge sage">{{ app.user ? roleNames[app.user.role] : '' }} · 固定身份</span>
      <div class="info-list">
        <div class="info-row">
          <span>用户名</span><strong>{{ app.user?.username }}</strong>
        </div>
        <div v-if="app.user?.maskedPhone" class="info-row">
          <span>手机号</span><strong>{{ app.user.maskedPhone }}</strong>
        </div>
        <div class="info-row">
          <span>登录到期</span><strong>{{ dateTime(app.user?.sessionExpiresAt) }}</strong>
        </div>
      </div>
      <div class="divider" />
      <p class="field-help">
        账号角色创建后固定，不能切换；参与者和组织者可以在本人资料中换绑登录手机号。
      </p>
    </aside>
    <div>
      <section v-if="normal && !forced" class="panel account-section">
        <div class="panel-heading">
          <h2><span class="section-index">01</span>本人资料</h2>
        </div>
        <form @submit.prevent="saveProfile">
          <div class="form-grid">
            <div class="form-field">
              <label for="profile-name">本人姓名</label
              ><input id="profile-name" v-model="displayName" required maxlength="60" />
            </div>
            <div class="form-field">
              <label>账号手机号 · 只读</label
              ><input :value="app.user?.maskedPhone || '—'" readonly aria-label="账号手机号" />
              <button type="button" class="text-link" style="margin-top: 8px" @click="openPhone">
                换绑手机号</button
              ><small>仅验证新手机号，同角色内不能重复绑定。</small>
            </div>
          </div>
          <p class="field-help">更新资料不会更改历史订单的姓名快照。</p>
          <button class="btn primary small" :disabled="task.busy.value" style="margin-top: 18px">
            保存资料
          </button>
        </form>
      </section>
      <section class="panel account-section">
        <div class="panel-heading">
          <h2>
            <span class="section-index">{{ forced ? '01' : '02' }}</span
            >修改密码
          </h2>
        </div>
        <form @submit.prevent="changePassword">
          <div class="form-field">
            <label for="old-password">原密码 / 管理员重置的临时密码</label
            ><input
              id="old-password"
              v-model="passwords.oldPassword"
              type="password"
              required
              autocomplete="current-password"
            />
          </div>
          <div class="form-grid">
            <div class="form-field">
              <label for="new-password">新密码</label
              ><input
                id="new-password"
                v-model="passwords.newPassword"
                type="password"
                required
                minlength="8"
                maxlength="72"
                autocomplete="new-password"
                pattern="(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}"
                placeholder="8～72位字母数字组合"
              />
            </div>
            <div class="form-field">
              <label for="confirm-password">确认新密码</label
              ><input
                id="confirm-password"
                v-model="passwords.confirm"
                type="password"
                required
                autocomplete="new-password"
              />
            </div>
          </div>
          <p class="field-help">
            修改后所有已登录设备会退出，需要用新密码重新登录。密码只能使用英文字母和数字，两者都要有，长度
            8–72 位。
          </p>
          <p v-if="task.error.value" class="error-inline">{{ task.error.value }}</p>
          <button class="btn primary small" :disabled="task.busy.value" style="margin-top: 18px">
            修改密码并重新登录
          </button>
        </form>
      </section>
      <section v-if="normal && !forced" class="panel account-section danger-zone">
        <div class="panel-heading">
          <h2><span class="section-index">03</span>账号注销</h2>
        </div>
        <p class="field-help">
          注销后不能恢复，历史订单、票据和操作记录仍会按规则保留。只要还有进行中的订单、候补、电子票、待处理退款，或你还有未结束的活动，就不能注销。
        </p>
        <ul
          v-if="closure?.blockers.length"
          class="closure-blockers"
          style="padding-left: 18px; margin-top: 15px"
        >
          <li v-for="b in closure.blockers" :key="b.message">{{ b.message }}</li>
        </ul>
        <button class="btn danger small" style="margin-top: 18px" @click="closeModal = true">
          查看注销确认
        </button>
      </section>
      <section v-if="isMock && !forced" class="panel account-section">
        <div class="panel-heading">
          <h2><span class="section-index">DEMO</span>演示数据</h2>
        </div>
        <p class="field-help">
          当前操作仅保存在本机浏览器。重置会清除本前端演示的账号和业务变更，恢复初始示例，并退出登录。
        </p>
        <button
          class="btn secondary small"
          style="margin-top: 16px"
          @click="
            closeModal = false;
            resetModal = true;
          "
        >
          <AppIcon name="RefreshCw" :size="14" />重置本地演示
        </button>
      </section>
    </div>
  </div>
  <AppModal :open="closeModal" title="确认注销当前账号" @close="closeModal = false"
    ><div v-if="closure && !closure.eligible" class="alert warning">
      <AppIcon name="CircleAlert" />
      <p>当前尚有未结业务，请先处理后再注销。{{ closure.blockers[0]?.message }}</p>
    </div>
    <form v-else @submit.prevent="closeAccount">
      <p class="field-help">注销后立即失去登录权限，不能恢复。请确认已处理全部业务。</p>
      <div class="form-field" style="margin-top: 20px">
        <label for="close-password">输入当前密码确认</label
        ><input
          id="close-password"
          v-model="closePassword"
          type="password"
          required
          autocomplete="current-password"
        />
      </div>
      <button class="btn danger full" :disabled="task.busy.value">确认注销</button>
    </form></AppModal
  ><AppModal :open="resetModal" title="重置本地演示？" @close="resetModal = false"
    ><p>将清除本浏览器中的演示变更，并恢复四个演示账号和初始活动。该操作不涉及后端或其他文件。</p>
    <div class="button-row">
      <button class="btn secondary" @click="resetModal = false">取消</button
      ><button class="btn primary" @click="reset">确认重置演示</button>
    </div></AppModal
  >
  <AppModal :open="phoneModal" title="换绑手机号" @close="phoneModal = false">
    <form @submit.prevent="changePhone">
      <p class="field-help">
        当前绑定：{{
          app.user?.maskedPhone
        }}。换绑只需要验证新手机号，不需要原手机号验证码；换绑后请用新手机号重新登录。
      </p>
      <div class="form-field">
        <label for="new-bound-phone">新手机号</label
        ><input
          id="new-bound-phone"
          v-model="newPhone"
          required
          inputmode="tel"
          autocomplete="tel"
          maxlength="11"
          pattern="1[0-9]{10}"
        />
      </div>
      <PhoneVerification
        v-if="phoneModal"
        v-model="phoneProof"
        :phone="newPhone"
        :role="(app.user?.role || 'PARTICIPANT') as Role"
        purpose="CHANGE_PHONE"
      />
      <p v-if="task.error.value" class="error-inline" role="alert">{{ task.error.value }}</p>
      <div class="button-row">
        <button type="button" class="btn secondary" @click="phoneModal = false">取消</button
        ><button class="btn primary" :disabled="task.busy.value">确认换绑</button>
      </div>
    </form>
  </AppModal>
</template>
