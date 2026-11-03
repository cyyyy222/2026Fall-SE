<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, post } from '../api/client';
import type { CampusEvent, Order } from '../types';
import { date, dateTime, money, time, uid } from '../utils/format';
import { useAppStore } from '../stores/app';
import { useTask } from '../composables/useTask';
import PosterArt from '../components/PosterArt.vue';
import AppIcon from '../components/AppIcon.vue';
import StatusBadge from '../components/StatusBadge.vue';
import StatePanel from '../components/StatePanel.vue';
const route = useRoute();
const router = useRouter();
const app = useAppStore();
const task = useTask();
const event = ref<CampusEvent>();
const error = ref('');
const loading = ref(true);
const sessionId = ref('');
const typeId = ref('');
const name = ref(app.user?.displayName || '');
const requestId = ref(uid());
const session = computed(() => event.value?.sessions.find((s) => s.id === sessionId.value));
const ticketType = computed(() => session.value?.ticketTypes.find((t) => t.id === typeId.value));
const variant = computed(() =>
  ['音乐演出', '艺术展览', '戏剧现场', '讲座沙龙', '户外生活', '工作坊'].indexOf(
    event.value?.categoryNameSnapshot || '',
  ),
);
const allowed = computed(
  () => !!ticketType.value && ['ON_SALE', 'SOLD_OUT'].includes(ticketType.value.salesStatus),
);
const sessionRemaining = computed(
  () =>
    session.value?.ticketTypes.reduce(
      (total, item) => total + (item.saleStopped ? 0 : item.remaining),
      0,
    ) ?? 0,
);
const sessionPriceFrom = computed(() => {
  const prices = session.value?.ticketTypes.map((item) => item.priceJiao) ?? [];
  return prices.length ? Math.min(...prices) : 0;
});
function chooseSession(id: string) {
  sessionId.value = id;
  typeId.value = session.value?.ticketTypes[0]?.id || '';
  requestId.value = uid();
}
async function load() {
  loading.value = true;
  error.value = '';
  try {
    event.value = await get<CampusEvent>(`/public/events/${route.params.eventId}`);
    chooseSession(event.value.sessions[0]?.id || '');
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法读取活动';
  } finally {
    loading.value = false;
  }
}
async function register() {
  if (!app.user) {
    router.push({ path: '/login', query: { redirect: route.fullPath } });
    return;
  }
  if (app.user.role !== 'PARTICIPANT') {
    app.toast('仅参与者账号可以报名，请退出后登录参与者账号', 'error');
    return;
  }
  await task.run(async () => {
    const result = await post<{ current: Order }>('/me/registrations', {
      requestId: requestId.value,
      sessionId: sessionId.value,
      ticketTypeId: typeId.value,
      attendeeName: name.value,
    });
    const o = result.current;
    await router.push(
      o.ticketId
        ? `/tickets/${o.ticketId}`
        : `/orders/${o.id}/${o.status === 'UNPAID' ? 'pay' : 'waitlist'}`,
    );
  });
}
onMounted(load);
</script>
<template>
  <div class="event-detail-view">
    <div class="breadcrumb">
      <RouterLink to="/events">发现活动</RouterLink><AppIcon name="ChevronRight" :size="13" /><span
        >活动详情与本人报名</span
      >
    </div>
    <StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" /><template
      v-else-if="event && session"
      ><section class="panel detail-card">
        <PosterArt
          :variant="variant"
          :title="event.name"
          :src="event.posterUrl"
          :category="event.categoryNameSnapshot"
          large
        />
        <div class="detail-content">
          <p class="eyebrow">AN INVITATION TO A GOOD MOMENT</p>
          <h1>{{ event.name }}</h1>
          <div class="detail-tags">
            <span class="tag">{{ event.categoryNameSnapshot }}</span
            ><StatusBadge :status="session.sessionSalesStatus" /><span class="tag"
              >仅限本人 · 一人一票</span
            >
          </div>
          <dl class="detail-facts">
            <div>
              <dt><AppIcon name="CalendarDays" :size="16" />活动时间</dt>
              <dd>
                {{ date(session.startsAt, { year: 'numeric', weekday: 'short' }) }}
                <b>{{ time(session.startsAt) }}–{{ time(session.endsAt) }}</b>
              </dd>
            </div>
            <div>
              <dt><AppIcon name="MapPin" :size="16" />活动地点</dt>
              <dd>{{ session.location }}</dd>
            </div>
            <div class="fact-deadline">
              <dt><AppIcon name="Clock3" :size="16" />报名截止</dt>
              <dd>{{ dateTime(session.registrationClosesAt) }}</dd>
            </div>
            <div>
              <dt><AppIcon name="Ticket" :size="16" />票价与余票</dt>
              <dd>
                {{ sessionPriceFrom ? `¥${money(sessionPriceFrom)} 起` : '免费' }}
                <span class="fact-muted" :class="{ 'is-sold-out': !sessionRemaining }">{{
                  sessionRemaining ? `余 ${sessionRemaining} 个名额` : '已售罄 · 可候补'
                }}</span>
              </dd>
            </div>
          </dl>
          <div class="booking-panel">
            <div class="booking-head">
              <h2>报名信息</h2>
              <span>同一场次仅一条有效订单</span>
            </div>
            <div class="choice-label">
              选择场次<span>共 {{ event.sessions.length }} 个场次可选</span>
            </div>
            <div class="choice-grid sessions">
              <button
                v-for="s in event.sessions"
                :key="s.id"
                class="choice session-choice"
                :class="{ active: s.id === sessionId }"
                @click="chooseSession(s.id)"
              >
                <span class="session-date">{{ date(s.startsAt) }} {{ time(s.startsAt) }}</span>
                <small>{{ s.name }}</small>
              </button>
            </div>
            <div class="choice-label">选择票档<span>每人每场仅限一张</span></div>
            <div class="choice-grid">
              <button
                v-for="t in session.ticketTypes"
                :key="t.id"
                class="choice ticket-choice"
                :class="{ active: t.id === typeId }"
                :disabled="t.saleStopped"
                @click="
                  typeId = t.id;
                  requestId = uid();
                "
              >
                <span class="choice-top">
                  <span class="choice-name">{{ t.name }}</span>
                  <span class="choice-price">{{
                    t.priceJiao ? `¥${money(t.priceJiao)}` : '免费'
                  }}</span>
                </span>
                <small :class="{ 'is-sold-out': !t.saleStopped && !t.remaining }">{{
                  t.saleStopped
                    ? '已停售'
                    : t.remaining
                      ? `余 ${t.remaining} 个名额`
                      : '已售罄 · 可候补'
                }}</small>
              </button>
            </div>
            <form @submit.prevent="register">
              <div class="form-grid">
                <div class="form-field">
                  <label for="booking-phone">账号手机号 · 只读</label
                  ><input
                    id="booking-phone"
                    :value="app.user?.maskedPhone || '登录后显示本人信息'"
                    readonly
                  />
                </div>
                <div class="form-field">
                  <label for="booking-name">本人姓名<span class="required">*</span></label
                  ><input
                    id="booking-name"
                    v-model="name"
                    placeholder="请输入本人姓名"
                    :required="!!app.user"
                    maxlength="60"
                    @input="requestId = uid()"
                  />
                </div>
              </div>
              <p class="field-help">仅限当前账号本人报名；同一场次最多一条有效订单。</p>
              <p v-if="task.error.value" class="error-inline" role="alert">
                {{ task.error.value }}
              </p>
              <div class="purchase-total">
                <span class="total-price"
                  ><small>合计</small>¥{{ money(ticketType?.priceJiao || 0) }}</span
                ><button
                  class="btn primary"
                  :disabled="task.busy.value || (!allowed && !!app.user)"
                  type="submit"
                >
                  <AppIcon v-if="task.busy.value" name="LoaderCircle" class="spin" />{{
                    !app.user
                      ? '登录后报名'
                      : !allowed
                        ? '当前不可报名'
                        : ticketType?.remaining === 0
                          ? '申请候补'
                          : '确认报名'
                  }}<AppIcon name="ArrowRight" :size="17" />
                </button>
              </div>
              <p class="booking-foot">
                {{
                  ticketType?.remaining === 0
                    ? '申请候补不会占用名额；轮到你时，需要先确认名额，再完成支付，并在验票结束前完成，否则候补失效。'
                    : '提交后会生成待支付订单并进入支付页，需要你本人确认；这是课程演示的模拟支付，不会真实扣款。'
                }}
              </p>
            </form>
          </div>
        </div>
      </section>
      <div class="alert detail-notice">
        <AppIcon name="Leaf" :size="18" />
        <p>
          <strong>一人一票：</strong>每个账号在同一场次只能有 1
          张有效票，并且只能给本人报名，不能替他人报名或转赠。入场时出示电子票号（一串字母和数字），由检票员在系统中核对，不需要二维码。满座后可以申请候补，有人退票时按排队顺序递补；报名、支付和候补都以实际截止时间为准。
        </p>
      </div>
      <div class="detail-lower">
        <section class="panel">
          <div class="section-title">
            <h2>关于这场相聚</h2>
            <span class="tag">活动介绍</span>
          </div>
          <p class="content-prose">{{ event.description }}</p>
        </section>
        <section class="panel">
          <div class="section-title"><h2>报名之前，了解这些</h2></div>
          <div class="numbered-note">
            <span>01</span>
            <p>
              报名后请在 15 分钟内完成确认支付；如果离报名截止不足 15
              分钟，则以报名截止时间为准。免费票也要走同样的确认步骤。
            </p>
          </div>
          <div class="numbered-note">
            <span>02</span>
            <p>
              报名截止前，符合条件的订单可以申请全额退款（课程演示的模拟退款）；一旦完成签到，就不能再退。
            </p>
          </div>
          <div class="numbered-note">
            <span>03</span>
            <p>请保存好电子票号，现场向检票员出示，核对无误后完成签到。</p>
          </div>
          <div class="divider" />
          <RouterLink
            v-if="app.user?.role === 'PARTICIPANT'"
            :to="`/reports/new?sessionId=${session.id}`"
            class="text-link"
            ><AppIcon name="ShieldCheck" :size="15" />活动信息有疑问？提交举报</RouterLink
          >
          <p v-else class="muted report-hint">举报功能向登录参与者开放。</p>
        </section>
      </div></template
    >
  </div>
</template>
