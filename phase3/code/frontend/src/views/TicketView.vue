<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { get } from '../api/client';
import type { Ticket } from '../types';
import { dateTime, money, saveFile } from '../utils/format';
import { useTask } from '../composables/useTask';
import AppIcon from '../components/AppIcon.vue';
import StatusBadge from '../components/StatusBadge.vue';
import StatePanel from '../components/StatePanel.vue';
const route = useRoute();
const ticket = ref<Ticket>();
const loading = ref(true);
const error = ref('');
const task = useTask();
async function load() {
  loading.value = true;
  error.value = '';
  try {
    ticket.value = await get<Ticket>(`/me/tickets/${route.params.ticketId}`);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法读取电子票';
  } finally {
    loading.value = false;
  }
}
async function download() {
  await task.run(async () => {
    const blob = await get<Blob>(`/me/tickets/${ticket.value!.id}/download`);
    saveFile(blob, `电子票_${ticket.value!.id}.txt`);
  }, '文本票据已下载');
}
onMounted(load);
</script>
<template>
  <RouterLink to="/orders" class="back-link"
    ><AppIcon name="ArrowLeft" :size="14" />返回我的订单</RouterLink
  ><StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" /><template
    v-else-if="ticket"
    ><div class="success-heading">
      <span class="state-icon"
        ><AppIcon :name="ticket.usable ? 'CheckCircle2' : 'Ticket'" :size="31"
      /></span>
      <h1>{{ ticket.usable ? '期待已就位，现场见。' : '每一场相聚，都有记录。' }}</h1>
      <p>
        {{ ticket.usable ? '报名成功，你的专属电子票已准备好。' : '这里保留着这张票的最新状态。' }}
      </p>
    </div>
    <article class="ticket-card">
      <header class="ticket-top">
        <p class="eyebrow">YOUR ADMISSION TO A GOOD MOMENT</p>
        <h2>{{ ticket.event.eventName }}</h2>
        <div class="button-row">
          <span class="tag">{{ ticket.event.categoryNameSnapshot }}</span
          ><StatusBadge :status="ticket.status" />
        </div>
        <span class="ticket-decoration" style="display: block; margin-top: 20px"
          >— GOOD MOMENTS —</span
        >
      </header>
      <div class="ticket-perforation" />
      <div class="ticket-body">
        <div class="info-list">
          <div class="info-row">
            <span>持票人</span><strong>{{ ticket.attendeeName }} · 本人</strong>
          </div>
          <div class="info-row">
            <span>场次 / 票档</span
            ><strong>{{ ticket.event.sessionName }} / {{ ticket.ticketTypeName }}</strong>
          </div>
          <div class="info-row">
            <span>活动时间</span><strong>{{ dateTime(ticket.event.startsAt) }}</strong>
          </div>
          <div class="info-row">
            <span>活动地点</span><strong>{{ ticket.event.location }}</strong>
          </div>
          <div class="info-row">
            <span>实付金额</span><strong>¥{{ money(ticket.paidAmountJiao) }}</strong>
          </div>
          <div v-if="ticket.checkedAt" class="info-row">
            <span>首次签到</span><strong>{{ dateTime(ticket.checkedAt) }}</strong>
          </div>
        </div>
        <div class="ticket-id">
          <small>TICKET ID · 专属票号</small><strong>{{ ticket.id }}</strong>
          <p>
            {{
              ticket.usable
                ? '现场出示或提供票号，由授权检票员核对入场。'
                : ticket.invalidReason || '此票不可再次验票。'
            }}
          </p>
        </div>
        <div class="button-row">
          <button class="btn primary" :disabled="task.busy.value" @click="download">
            <AppIcon name="Download" :size="16" />下载文本电子票</button
          ><RouterLink :to="`/orders/${ticket.orderId}`" class="btn secondary">查看订单</RouterLink>
        </div>
        <p class="field-help" style="margin-top: 18px">
          电子票仅限本人使用，不支持转赠。系统采用票 id 核验，不生成二维码。
        </p>
      </div>
    </article></template
  >
</template>
