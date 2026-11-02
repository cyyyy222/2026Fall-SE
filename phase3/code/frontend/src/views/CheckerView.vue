<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { get, post } from '../api/client';
import type { Session } from '../types';
import { useTask } from '../composables/useTask';
import { dateTime } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import AppIcon from '../components/AppIcon.vue';
import StatePanel from '../components/StatePanel.vue';
import StatusBadge from '../components/StatusBadge.vue';
const task = useTask();
const loading = ref(true);
const error = ref('');
const ticketId = ref('');
const context = ref<{ session: Session; checkinOpensAt: string; checkinClosesAt: string }>();
const preview = ref<{
  ticketId: string;
  attendeeName: string;
  sessionName: string;
  ticketTypeName: string;
  alreadyChecked: boolean;
  checkedAt: string | null;
  canConfirm: boolean;
}>();
const result = ref<{ checkedAt: string; replayed: boolean }>();
async function load() {
  loading.value = true;
  error.value = '';
  try {
    context.value = await get('/checker/context');
  } catch (e) {
    error.value = e instanceof Error ? e.message : '当前账号不可用';
  } finally {
    loading.value = false;
  }
}
async function check() {
  preview.value = undefined;
  result.value = undefined;
  await task.run(async () => {
    preview.value = await post('/checker/ticket-previews', { ticketId: ticketId.value.trim() });
  });
}
async function confirm() {
  await task.run(async () => {
    result.value = await post('/checker/checkins', { ticketId: preview.value!.ticketId });
    preview.value!.canConfirm = false;
  }, '签到结果已确认');
}
async function reconcile() {
  await task.run(async () => {
    await post(`/checker/sessions/${context.value!.session.id}/absence-reconciliation`);
  }, '已提交结束补漏处理，结果按当前授权与场次状态核验');
}
onMounted(load);
</script>
<template>
  <PageHeading
    eyebrow="A WARM WELCOME STARTS HERE"
    title="现场验票"
    description="先核对，再确认。只为当前授权场次验票。"
  /><StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" /><template
    v-else-if="context"
    ><div class="alert info" style="margin-bottom: 24px">
      <AppIcon name="ShieldCheck" />
      <p>
        当前授权：{{ context.session.eventName }} · {{ context.session.name }}<br />验票窗口：{{
          dateTime(context.checkinOpensAt)
        }}
        — {{ dateTime(context.checkinClosesAt) }}；窗口含起点，不含终点。
      </p>
    </div>
    <section class="panel scanner-card">
      <div class="scanner-symbol"><AppIcon name="ScanLine" :size="35" /></div>
      <h2>准备迎接下一位参与者</h2>
      <p>输入电子票 id，核对姓名和票档后再确认签到。</p>
      <form class="scanner-form" @submit.prevent="check">
        <input
          v-model="ticketId"
          required
          aria-label="电子票 id"
          placeholder="请输入完整票号（UUID）"
          autocomplete="off"
          @input="
            preview = undefined;
            result = undefined;
          "
        /><button class="btn primary" :disabled="task.busy.value">
          <AppIcon v-if="task.busy.value" name="LoaderCircle" class="spin" />核对票号
        </button>
      </form>
      <p v-if="task.error.value" class="error-inline" role="alert">{{ task.error.value }}</p>
      <div v-if="preview" class="check-result">
        <div class="section-title">
          <h3>
            {{ result ? '签到已确认' : preview.alreadyChecked ? '此票已签到' : '票据信息核对' }}
          </h3>
          <StatusBadge :status="result || preview.alreadyChecked ? 'CHECKED' : 'VALID'" />
        </div>
        <div class="info-list">
          <div class="info-row">
            <span>姓名</span><strong>{{ preview.attendeeName }}</strong>
          </div>
          <div class="info-row">
            <span>场次</span><strong>{{ preview.sessionName }}</strong>
          </div>
          <div class="info-row">
            <span>票档</span><strong>{{ preview.ticketTypeName }}</strong>
          </div>
          <div v-if="result || preview.checkedAt" class="info-row">
            <span>首次签到时间</span
            ><strong>{{ dateTime(result?.checkedAt || preview.checkedAt) }}</strong>
          </div>
        </div>
        <button
          v-if="preview.canConfirm && !result"
          class="btn sage full"
          :disabled="task.busy.value"
          @click="confirm"
        >
          <AppIcon name="CheckCircle2" />确认签到</button
        ><button
          v-else
          class="btn secondary full"
          @click="
            ticketId = '';
            preview = undefined;
            result = undefined;
          "
        >
          继续核对下一张票
        </button>
      </div>
      <div class="divider" />
      <p class="field-help">无权限时不会显示持票人信息。重复验票仅返回首次结果；不支持撤销验票。</p>
    </section>
    <section class="panel" style="margin-top: 24px">
      <div class="panel-heading">
        <h2>场次结束补漏</h2>
        <span class="tag">仅正常结束后</span>
      </div>
      <p class="field-help">
        仅在场次正常结束、账号仍可用且当前授权未撤销时补做缺席漏标，不提前标记、不撤销验票。
      </p>
      <button
        class="btn secondary small"
        style="margin-top: 16px"
        :disabled="task.busy.value || Date.now() < Date.parse(context.session.endsAt)"
        @click="reconcile"
      >
        补做结束缺席标记
      </button>
    </section>
    <div class="manage-nav">
      <RouterLink to="/checker/attendees"
        ><span><AppIcon name="UsersRound" /> 授权场次名单</span
        ><AppIcon name="ArrowUpRight" :size="16" /></RouterLink
      ><RouterLink to="/checker/statistics"
        ><span><AppIcon name="ChartNoAxesCombined" /> 签到统计</span
        ><AppIcon name="ArrowUpRight" :size="16" /></RouterLink
      ><RouterLink to="/messages"
        ><span><AppIcon name="Bell" /> 站内消息</span><AppIcon name="ArrowUpRight" :size="16"
      /></RouterLink></div
  ></template>
</template>
