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
  <div class="breadcrumb">
    <RouterLink to="/events">发现活动</RouterLink><AppIcon name="ChevronRight" :size="12" /><span
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
        <div class="detail-meta">
          <div><AppIcon name="MapPin" :size="15" />{{ session.location }}</div>
          <div>
            <AppIcon name="CalendarDays" :size="15" />{{
              date(session.startsAt, { year: 'numeric', weekday: 'short' })
            }}
            {{ time(session.startsAt) }}—{{ time(session.endsAt) }}
          </div>
        </div>
        <div class="choice-label">
          选择场次<span>报名截止 {{ dateTime(session.registrationClosesAt) }}</span>
        </div>
        <div class="choice-grid sessions">
          <button
            v-for="s in event.sessions"
            :key="s.id"
            class="choice"
            :class="{ active: s.id === sessionId }"
            @click="chooseSession(s.id)"
          >
            {{ date(s.startsAt) }} {{ time(s.startsAt) }}<small>{{ s.name }}</small>
          </button>
        </div>
        <div class="choice-label">选择票档<span>每人每场仅限一张</span></div>
        <div class="choice-grid">
          <button
            v-for="t in session.ticketTypes"
            :key="t.id"
            class="choice"
            :class="{ active: t.id === typeId }"
            :disabled="t.saleStopped"
            @click="
              typeId = t.id;
              requestId = uid();
            "
          >
            {{ t.name }} · {{ t.priceJiao ? `¥${money(t.priceJiao)}` : '免费'
            }}<small>{{
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
          <p v-if="task.error.value" class="error-inline" role="alert">{{ task.error.value }}</p>
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
              }}<AppIcon name="ArrowRight" :size="16" />
            </button>
          </div>
          <p class="field-help" style="margin-top: 12px">
            {{
              ticketType?.remaining === 0
                ? '候补不占名额，递补成功后需要先确认、再支付。'
                : '创建订单后进入支付页，由本人确认模拟支付；不会自动扣款。'
            }}
          </p>
        </form>
      </div>
    </section>
    <div class="alert" style="margin-top: 22px">
      <AppIcon name="Leaf" :size="17" />
      <p>
        一人一票，不支持代报、转赠。票 id
        验票，无二维码。有合格在先候补时优先递补；所有操作以实际截止时间及最新状态为准。
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
          <p>订单支付期限最长15分钟，临近报名截止时会同步缩短。免费票也需要确认支付。</p>
        </div>
        <div class="numbered-note">
          <span>02</span>
          <p>截止前，符合条件的已报名订单可申请全额模拟退款；已签到后不可退款。</p>
        </div>
        <div class="numbered-note">
          <span>03</span>
          <p>请妥善保存电子票号，现场由授权检票员核对后确认签到。</p>
        </div>
        <div class="divider" />
        <RouterLink
          v-if="app.user?.role === 'PARTICIPANT'"
          :to="`/reports/new?sessionId=${session.id}`"
          class="text-link"
          ><AppIcon name="ShieldCheck" :size="14" />活动信息有疑问？提交举报</RouterLink
        >
        <p v-else class="muted" style="font-size: 11px">举报功能向登录参与者开放。</p>
      </section>
    </div></template
  >
</template>
