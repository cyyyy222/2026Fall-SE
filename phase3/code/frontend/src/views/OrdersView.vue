<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { get } from '../api/client';
import type { Order, Page } from '../types';
import { dateTime, money } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import PosterArt from '../components/PosterArt.vue';
import StatusBadge from '../components/StatusBadge.vue';
import StatePanel from '../components/StatePanel.vue';
import AppIcon from '../components/AppIcon.vue';
const orders = ref<Order[]>([]);
const loading = ref(true);
const error = ref('');
const tab = ref('');
const tabs = [
  ['', '全部订单'],
  ['UNPAID', '待支付'],
  ['WAITLIST', '候补进度'],
  ['REGISTERED', '待参加'],
  ['HISTORY', '历史订单'],
];
const filtered = computed(() =>
  orders.value.filter(
    (o) =>
      !tab.value ||
      (tab.value === 'WAITLIST'
        ? ['WAITLISTED', 'OFFERED'].includes(o.status)
        : tab.value === 'HISTORY'
          ? ['COMPLETED', 'CANCELLED', 'REFUNDED', 'ABSENT'].includes(o.status)
          : o.status === tab.value),
  ),
);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    orders.value = (await get<Page<Order>>('/me/orders?pageSize=100')).items;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法加载订单';
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <PageHeading
    eyebrow="YOUR NEXT GOOD MOMENT"
    title="我的订单"
    description="每一场期待，都有迹可循。"
    ><RouterLink to="/events" class="btn secondary small"
      ><AppIcon name="ArrowUpRight" :size="15" />发现更多活动</RouterLink
    ></PageHeading
  >
  <div class="tabs">
    <button
      v-for="item in tabs"
      :key="item[0]"
      :class="{ active: tab === item[0] }"
      @click="tab = item[0]!"
    >
      {{ item[1] }}<span v-if="!item[0]">{{ orders.length }}</span>
    </button>
  </div>
  <StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" /><StatePanel
    v-else-if="!filtered.length"
    title="这里还没有订单"
    description="喜欢的活动，值得为它留一点时间。"
    ><RouterLink to="/events" class="btn primary"
      >去发现活动<AppIcon name="ArrowRight" :size="15" /></RouterLink></StatePanel
  ><template v-else
    ><article v-for="(order, i) in filtered" :key="order.id" class="order-item">
      <div class="order-item-top">
        <span>订单编号 {{ order.id }}</span
        ><span>{{ dateTime(order.createdAt) }}</span>
      </div>
      <div class="order-item-body">
        <div class="order-thumb">
          <RouterLink :to="`/events/${order.event.eventId}`">
            <PosterArt
              :variant="i + 1"
              :title="order.event.eventName"
              :category="order.event.categoryNameSnapshot"
            />
          </RouterLink>
        </div>
        <div class="order-item-info">
          <h3>
            <RouterLink :to="`/events/${order.event.eventId}`">{{
              order.event.eventName
            }}</RouterLink>
          </h3>
          <p><AppIcon name="CalendarDays" :size="13" />{{ dateTime(order.event.startsAt) }}</p>
          <p><AppIcon name="MapPin" :size="13" />{{ order.event.location }}</p>
          <p>{{ order.ticketTypeName }} · {{ order.attendeeNameSnapshot }} · 1张</p>
        </div>
        <div class="order-item-actions">
          <StatusBadge :status="order.status" /><span class="price"
            >¥{{ money(order.amountJiao) }}</span
          >
          <div class="button-row">
            <RouterLink
              v-if="order.ticketId"
              :to="`/tickets/${order.ticketId}`"
              class="btn primary small"
              >查看电子票</RouterLink
            ><RouterLink
              v-else-if="['UNPAID', 'WAITLISTED', 'OFFERED'].includes(order.status)"
              :to="`/orders/${order.id}/${order.status === 'UNPAID' ? 'pay' : 'waitlist'}`"
              class="btn primary small"
              >{{ order.status === 'UNPAID' ? '继续支付' : '查看候补' }}</RouterLink
            ><RouterLink :to="`/orders/${order.id}`" class="text-link">订单详情</RouterLink>
          </div>
        </div>
      </div>
    </article>
    <p class="field-help">
      活动结束或终止后，你的订单和电子票仍可查看；活动名单与统计只保留 7 天，不影响你的历史票据。
    </p></template
  >
</template>
