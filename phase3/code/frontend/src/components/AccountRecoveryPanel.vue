<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { post } from '../api/client';
import { useAppStore } from '../stores/app';
import { useTask } from '../composables/useTask';
import type { PhoneProof, Role } from '../types';
import PhoneVerification from './PhoneVerification.vue';
const router = useRouter();
const app = useAppStore();
const task = useTask();
const manual = ref(false);
const role = ref<Role>('PARTICIPANT');
const phone = ref('');
const proof = ref<PhoneProof | null>(null);
const passwords = reactive({ value: '', confirm: '' });
const appeal = reactive({ account: '', oldPhone: '', statement: '', pastBehavior: '' });
const receipt = ref('');
const purpose = computed(() => (manual.value ? 'RECOVERY_CONTACT' : 'RESET_PASSWORD'));
async function submit() {
  await task.run(async () => {
    if (!proof.value) throw new Error('请先获取并填写正确格式的六位验证码');
    if (!manual.value) {
      if (passwords.value !== passwords.confirm) throw new Error('两次新密码输入不一致');
      await post('/auth/password-reset', {
        phone: phone.value,
        role: role.value,
        ...proof.value,
        newPassword: passwords.value,
      });
      app.user = null;
      app.toast('密码已重置，请重新登录');
      await router.push('/login');
    } else {
      const result = await post<{ requestId: string }>('/auth/recoveries', {
        account: appeal.account,
        phone: appeal.oldPhone,
        role: role.value,
        currentPhone: phone.value,
        statement: appeal.statement,
        pastBehavior: appeal.pastBehavior,
        ...proof.value,
      });
      receipt.value = result.requestId;
    }
  });
}
</script>
<template>
  <div v-if="receipt" class="alert info">
    <div>
      <strong>申诉已提交，等待管理员审核</strong>
      <p>申请编号：{{ receipt }}</p>
      <p>
        请保留申请编号。审核通过后，账号会换绑到你验证过的当前手机号，并生成临时密码；本地演示不会真的发短信，通知会记录在后端日志中。用临时密码首次登录后需要改密。
      </p>
      <RouterLink to="/login" class="text-link">返回登录</RouterLink>
    </div>
  </div>
  <form v-else class="auth-form" @submit.prevent="submit">
    <div class="tabs" aria-label="密码找回方式">
      <button
        type="button"
        :class="{ active: !manual }"
        @click="
          manual = false;
          proof = null;
        "
      >
        手机号验证码找回</button
      ><button
        type="button"
        :class="{ active: manual }"
        @click="
          manual = true;
          proof = null;
        "
      >
        原号不可用，人工申诉
      </button>
    </div>
    <p class="field-help">
      优先验证已绑定手机号并设置新密码。管理员、检票员没有绑定手机号，请联系平台管理员。
    </p>
    <div class="form-field">
      <label for="recovery-role">账号身份</label
      ><select id="recovery-role" v-model="role">
        <option value="PARTICIPANT">参与者</option>
        <option value="ORGANIZER">组织者</option>
      </select>
    </div>
    <template v-if="manual">
      <div class="alert info">
        <p>
          请尽量准确地提供原账号资料和以往行为。你的陈述会提交给管理员人工审核，不会仅凭手机号和用户名自动改密。当前手机号须先验证，用于接收审核结果和临时凭据。
        </p>
      </div>
      <div class="form-field">
        <label for="appeal-account">原账号用户名</label
        ><input
          id="appeal-account"
          v-model="appeal.account"
          required
          maxlength="60"
          autocomplete="username"
        />
      </div>
      <div class="form-field">
        <label for="appeal-old-phone">账户原手机号</label
        ><input
          id="appeal-old-phone"
          v-model="appeal.oldPhone"
          required
          inputmode="tel"
          pattern="1[0-9]{10}"
          maxlength="11"
        />
      </div>
    </template>
    <div class="form-field">
      <label for="reset-phone">{{
        manual ? '当前可用手机号（接收审核通知）' : '账号已绑定的手机号'
      }}</label
      ><input
        id="reset-phone"
        v-model="phone"
        required
        inputmode="tel"
        pattern="1[0-9]{10}"
        maxlength="11"
        autocomplete="tel"
      />
    </div>
    <PhoneVerification
      :key="purpose"
      v-model="proof"
      :phone="phone"
      :role="role"
      :purpose="purpose"
    />
    <template v-if="manual">
      <div class="form-field">
        <label for="appeal-statement">申诉陈述（20～2000字）</label
        ><textarea
          id="appeal-statement"
          v-model="appeal.statement"
          required
          minlength="20"
          maxlength="2000"
          rows="3"
          placeholder="说明原手机号为何无法使用、账号大致注册时间，以及其他能够帮助核对身份的信息。"
        />
      </div>
      <div class="form-field">
        <label for="appeal-history">以往行为信息（20～2000字）</label
        ><textarea
          id="appeal-history"
          v-model="appeal.pastBehavior"
          required
          minlength="20"
          maxlength="2000"
          rows="3"
          placeholder="例如曾报名或创建的活动、时间、票种、订单状态或其他仅本人应当了解的信息。请勿填写密码。"
        />
      </div>
    </template>
    <template v-else>
      <div class="form-field">
        <label for="reset-new-password">设置新密码</label
        ><input
          id="reset-new-password"
          v-model="passwords.value"
          type="password"
          required
          minlength="8"
          maxlength="72"
          pattern="(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}"
          autocomplete="new-password"
          placeholder="8～72位字母和数字组合"
        />
      </div>
      <div class="form-field">
        <label for="reset-confirm-password">确认新密码</label
        ><input
          id="reset-confirm-password"
          v-model="passwords.confirm"
          type="password"
          required
          autocomplete="new-password"
        />
      </div>
    </template>
    <p v-if="task.error.value" class="error-inline" role="alert">{{ task.error.value }}</p>
    <button class="btn primary full" :disabled="task.busy.value">
      {{ manual ? '提交详细申诉，等待审核' : '验证并重置密码' }}
    </button>
    <p class="auth-bottom"><RouterLink to="/login" class="text-link">返回登录</RouterLink></p>
  </form>
</template>
