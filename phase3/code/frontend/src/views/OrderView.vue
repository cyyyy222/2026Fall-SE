<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, post } from '../api/client';
import type { Order, PaymentAudit, Mutation } from '../types';
import { dateTime, label, money, uid } from '../utils/format';
import { serverNow } from '../utils/clock';
import { useTask } from '../composables/useTask';
import { useAppStore } from '../stores/app';
import PageHeading from '../components/PageHeading.vue';
import AppIcon from '../components/AppIcon.vue';
import AppModal from '../components/AppModal.vue';
import StatePanel from '../components/StatePanel.vue';
import StatusBadge from '../components/StatusBadge.vue';
const route = useRoute();
const router = useRouter();
const task = useTask();
const app = useAppStore();
const order = ref<Order>();
const payments = ref<PaymentAudit>();
const loading = ref(true);
const error = ref('');
const confirm = ref<'cancel' | 'refund' | null>(null);
const reason = ref('本人计划变更');
const clock = ref(serverNow());
const attemptId = ref(uid());
const refundAttempt = ref(uid());
const cancelKey = ref(uid());
const refundKey = ref(uid());
const interval = setInterval(() => {
  clock.value = serverNow();
}, 1000);
onUnmounted(() => clearInterval(interval));
const countdown = computed(() => {
  const deadline =
    order.value?.status === 'OFFERED' ? order.value.confirmDeadline : order.value?.payDeadline;
  if (!deadline) return '—';
  const seconds = Math.max(0, Math.floor((Date.parse(deadline) - clock.value) / 1000));
  return `${String(Math.floor(seconds / 3600)).padStart(2, '0')}:${String(Math.floor(seconds / 60) % 60).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
});
const can = (action: string) => order.value?.allowedActions.includes(action);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    order.value = await get<Order>(`/me/orders/${route.params.orderId}`);
    payments.value = await get<PaymentAudit>(`/me/orders/${route.params.orderId}/payments`);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法加载订单';
  } finally {
    loading.value = false;
  }
}
async function pay() {
  await task.run(async () => {
    const result = await post<{
      current: Order;
      attempt: { result: string; failureReason: string | null };
    }>(
      `/me/orders/${order.value!.id}/payment-attempts`,
      { attemptId: attemptId.value },
      attemptId.value,
    );
    order.value = result.current;
    if (result.current.ticketId) await router.push(`/tickets/${result.current.ticketId}`);
    else if (result.attempt.result === 'FAILURE') {
      attemptId.value = uid();
      task.error.value = result.attempt.failureReason || '本次模拟支付未成功，可按当前期限重试';
      payments.value = await get<PaymentAudit>(`/me/orders/${order.value.id}/payments`);
    }
  });
}
async function promote() {
  await task.run(async () => {
    const result = await post<Mutation<Order>>(
      `/me/orders/${order.value!.id}/promotion-confirmation`,
    );
    order.value = result.current;
    await router.replace(`/orders/${order.value!.id}/pay`);
  }, '名额已确认，请及时支付');
}
async function changeOrder() {
  await task.run(async () => {
    const refund = confirm.value === 'refund';
    const retry = refund && can('RETRY_REFUND') && order.value!.activeRefundRequestId;
    const suffix = retry
      ? `refund-requests/${order.value!.activeRefundRequestId}/attempts`
      : refund
        ? 'refund-requests'
        : 'cancel';
    const path = `/me/orders/${order.value!.id}/${suffix}`;
    const result = await post<
      Mutation<Order> & { attempt?: { result: string; failureReason?: string } }
    >(
      path,
      retry
        ? { attemptId: refundAttempt.value }
        : refund
          ? { reason: reason.value, attemptId: refundAttempt.value }
          : { reason: reason.value },
      refund ? refundKey.value : cancelKey.value,
    );
    order.value = result.current;
    if (result.attempt?.result === 'FAILURE') {
      refundAttempt.value = uid();
      refundKey.value = uid();
      task.error.value =
        result.attempt.failureReason || '退款执行失败，原票及名额仍保留；请按合法原申请重试';
      return;
    }
    confirm.value = null;
    await load();
    app.toast(refund ? '已完成全额模拟退款' : '订单已取消');
  });
}
onMounted(load);
</script>
<template>
  <RouterLink to="/orders" class="back-link"
    ><AppIcon name="ArrowLeft" :size="14" />返回我的订单</RouterLink
  ><StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" /><template
    v-else-if="order"
    ><PageHeading
      eyebrow="YOUR RESERVATION"
      :title="
        order.status === 'UNPAID'
          ? '为期待，留一个位置'
          : ['WAITLISTED', 'OFFERED'].includes(order.status)
            ? '好事，值得耐心等待'
            : '订单详情'
      "
      description="操作结果以当前订单状态为准，遇到网络异常请先查询。"
      ><StatusBadge :status="order.status" /><button class="btn secondary small" @click="load">
        <AppIcon name="RefreshCw" :size="14" />刷新状态
      </button></PageHeading
    >
    <div v-if="order.status === 'UNPAID'" class="steps">
      <span class="step done"
        ><b><AppIcon name="Check" :size="12" /></b>确认报名</span
      ><i class="step-connector" /><span class="step current"><b>2</b>模拟支付</span
      ><i class="step-connector" /><span class="step"><b>3</b>获取电子票</span>
    </div>
    <div class="two-column">
      <section class="panel">
        <div class="panel-heading">
          <h2>{{ order.event.eventName }}</h2>
          <span class="tag">{{ order.event.categoryNameSnapshot }}</span>
        </div>
        <div class="payment-summary">
          <h3>{{ order.event.sessionName }}</h3>
          <div class="info-list">
            <div class="info-row">
              <span>时间</span
              ><strong
                >{{ dateTime(order.event.startsAt) }} — {{ dateTime(order.event.endsAt) }}</strong
              >
            </div>
            <div class="info-row">
              <span>地点</span><strong>{{ order.event.location }}</strong>
            </div>
            <div class="info-row">
              <span>票档 / 数量</span><strong>{{ order.ticketTypeName }} · 1张</strong>
            </div>
            <div class="info-row">
              <span>本人姓名</span><strong>{{ order.attendeeNameSnapshot }}</strong>
            </div>
          </div>
        </div>
        <template v-if="order.status === 'UNPAID'"
          ><div class="payment-method">
            <AppIcon name="Ticket" :size="25" />
            <div>
              <strong>本地模拟支付</strong>
              <p>仅用于课程原型，不涉及真实资金。</p>
            </div>
            <AppIcon name="CheckCircle2" class="push-right" />
          </div>
          <div class="purchase-total">
            <span class="total-price"><small>应付</small>¥{{ money(order.amountJiao) }}</span
            ><button
              class="btn primary"
              :disabled="task.busy.value || !can('PAY') || countdown === '00:00:00'"
              @click="pay"
            >
              <AppIcon
                v-if="task.busy.value"
                name="LoaderCircle"
                class="spin"
              />确认模拟支付<AppIcon name="ArrowRight" :size="15" />
            </button>
          </div>
          <p class="field-help" style="margin-top: 15px">
            免费票同样需要确认支付。支付成功后生成唯一电子票，不重复扣占名额。
          </p></template
        ><template v-else-if="order.status === 'WAITLISTED'"
          ><div class="order-state-head">
            <AppIcon name="Clock3" :size="30" />
            <div>
              <h2>你已加入候补队列</h2>
              <p>当前位次：第 {{ order.queuePosition || '—' }} 位，仅展示本人位次。</p>
            </div>
          </div>
          <p class="content-prose">
            候补暂不占位，也不会扣款。有名额时优先依序递补，并发送站内消息。递补成功后需先确认名额，再完成支付。
          </p></template
        ><template v-else-if="order.status === 'OFFERED'"
          ><div class="order-state-head">
            <AppIcon name="CheckCircle2" :size="30" />
            <div>
              <h2>名额已为你保留</h2>
              <p>请先确认递补，再在支付期限内付款。</p>
            </div>
          </div>
          <button
            class="btn primary full"
            :disabled="task.busy.value || !can('CONFIRM_PROMOTION')"
            @click="promote"
          >
            确认递补，前往支付<AppIcon name="ArrowRight" :size="16" /></button></template
        ><template v-else
          ><div
            class="alert"
            :class="{ warning: ['CANCELLED', 'REFUNDED', 'ABSENT'].includes(order.status) }"
          >
            <AppIcon :name="order.status === 'REGISTERED' ? 'CheckCircle2' : 'CircleAlert'" />
            <div>
              当前订单：{{ label(order.status) }}
              <p v-if="order.statusReason">{{ order.statusReason }}</p>
              <p v-if="order.event.terminationReason">
                活动终止原因：{{ order.event.terminationReason }}
              </p>
            </div>
          </div>
          <RouterLink
            v-if="order.ticketId"
            :to="`/tickets/${order.ticketId}`"
            class="btn primary"
            style="margin-top: 20px"
            ><AppIcon name="Ticket" />查看电子票</RouterLink
          ></template
        >
        <p v-if="task.error.value" class="error-inline" role="alert">
          {{ task.error.value }}。若响应未知，请点击刷新状态确认结果；重试保持原操作编号。
        </p>
        <div
          v-if="payments && (payments.paymentAttempts.length || payments.refundAttempts.length)"
          class="divider"
        />
        <div v-if="payments?.paymentAttempts.length">
          <h3 style="font-size: 13px; margin-bottom: 14px">支付记录</h3>
          <div
            v-for="p in payments.paymentAttempts"
            :key="p.id"
            class="info-row"
            style="margin-top: 9px"
          >
            <span>{{ dateTime(p.createdAt) }}</span
            ><span>{{ label(p.result) }} · ¥{{ money(p.amountJiao) }}</span>
          </div>
        </div>
        <div v-if="payments?.refundAttempts.length" style="margin-top: 22px">
          <h3 style="font-size: 13px">退款记录</h3>
          <div
            v-for="r in payments.refundAttempts"
            :key="r.id"
            class="info-row"
            style="margin-top: 9px"
          >
            <span>{{ dateTime(r.createdAt) }}</span
            ><span>{{ label(r.result) }} · ¥{{ money(r.amountJiao) }}</span>
          </div>
        </div>
      </section>
      <aside>
        <section class="panel">
          <h3 style="font-size: 15px; margin-bottom: 22px">订单信息</h3>
          <div class="info-list">
            <div class="info-row"><span>订单状态</span><StatusBadge :status="order.status" /></div>
            <div class="info-row">
              <span>订单金额</span><strong>¥{{ money(order.amountJiao) }}</strong>
            </div>
            <div class="info-row">
              <span>创建时间</span><strong>{{ dateTime(order.createdAt) }}</strong>
            </div>
          </div>
          <div class="divider" />
          <p class="field-help">订单编号</p>
          <p class="mono" style="margin-top: 8px">{{ order.id }}</p>
          <template v-if="['UNPAID', 'OFFERED'].includes(order.status)"
            ><div class="divider" />
            <p class="field-help">{{ order.status === 'UNPAID' ? '支付' : '递补确认' }}剩余时间</p>
            <div class="countdown" style="margin-top: 8px">{{ countdown }}</div>
            <p class="field-help">
              {{ dateTime(order.status === 'UNPAID' ? order.payDeadline : order.confirmDeadline) }}
              截止
            </p></template
          >
          <div
            v-if="can('CANCEL') || can('REQUEST_REFUND') || can('RETRY_REFUND')"
            class="divider"
          />
          <button v-if="can('CANCEL')" class="btn secondary full" @click="confirm = 'cancel'">
            {{ order.status === 'WAITLISTED' ? '放弃候补' : '取消订单' }}</button
          ><button
            v-if="can('REQUEST_REFUND') || can('RETRY_REFUND')"
            class="btn secondary full"
            @click="confirm = 'refund'"
          >
            申请全额退款
          </button>
        </section>
        <div class="alert info" style="margin-top: 20px">
          <AppIcon name="HelpCircle" :size="16" />
          <p>网络异常不代表支付或退款失败。请先查当前订单，避免产生新的重复意图。</p>
        </div>
      </aside>
    </div></template
  ><AppModal
    :open="!!confirm"
    :title="confirm === 'refund' ? '确认全额模拟退款' : '确认取消订单'"
    @close="confirm = null"
    ><p>
      {{
        confirm === 'refund'
          ? '退款成功后电子票立即失效，名额释放。此操作不可恢复；0元票也会记录退款。'
          : '取消后将释放已占名额或退出候补队列。重新报名需要再次满足当时的时间与资格条件。'
      }}
    </p>
    <form @submit.prevent="changeOrder">
      <div class="form-field">
        <label for="cancel-reason">原因</label
        ><textarea id="cancel-reason" v-model="reason" required maxlength="200" />
      </div>
      <div class="button-row">
        <button class="btn secondary" type="button" @click="confirm = null">暂不操作</button
        ><button class="btn primary" :disabled="task.busy.value">
          {{ confirm === 'refund' ? '确认退款' : '确认取消' }}
        </button>
      </div>
    </form></AppModal
  >
</template>
