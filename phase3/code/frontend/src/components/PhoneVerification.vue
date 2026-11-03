<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { post } from '../api/client';
import { useTask } from '../composables/useTask';
import type { PhoneCodePurpose, PhoneProof, Role } from '../types';
const props = defineProps<{ phone: string; role: Role; purpose: PhoneCodePurpose }>();
const proof = defineModel<PhoneProof | null>({ default: null });
const task = useTask();
const id = ref('');
const code = ref('');
const resendAt = ref(0);
const now = ref(Date.now());
const scope = computed(() => `${props.phone}:${props.role}:${props.purpose}`);
const remaining = computed(() => Math.max(0, Math.ceil((resendAt.value - now.value) / 1000)));
const timer = setInterval(() => (now.value = Date.now()), 1000);
onUnmounted(() => clearInterval(timer));
watch(scope, () => {
  id.value = '';
  code.value = '';
  proof.value = null;
  resendAt.value = 0;
});
watch([id, code], () => {
  proof.value =
    id.value && /^\d{6}$/.test(code.value)
      ? { verificationId: id.value, verificationCode: code.value }
      : null;
});
async function send() {
  await task.run(async () => {
    if (!/^1\d{10}$/.test(props.phone)) throw new Error('请先填写1开头的11位手机号');
    const sentScope = scope.value;
    const receipt = await post<{ verificationId: string; resendAfterSeconds: number }>(
      '/auth/phone-codes',
      {
        phone: props.phone,
        role: props.role,
        purpose: props.purpose,
      },
    );
    if (sentScope !== scope.value) throw new Error('手机号或角色已变化，请重新获取验证码');
    id.value = receipt.verificationId;
    code.value = '';
    now.value = Date.now();
    resendAt.value = now.value + receipt.resendAfterSeconds * 1000;
  });
}
</script>
<template>
  <div class="form-field">
    <label
      >手机验证码
      <span class="verification-input"
        ><input
          v-model="code"
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
          :disabled="task.busy.value || remaining > 0"
          @click="send"
        >
          {{ remaining ? `${remaining}秒后重发` : '获取验证码' }}
        </button></span
      >
    </label>
    <small
      >本地短信模拟：在Java后端终端或backend-local.log查看对应手机号和用途的验证码，5分钟内有效。</small
    >
    <p v-if="task.error.value" class="error-inline" role="alert">{{ task.error.value }}</p>
  </div>
</template>
