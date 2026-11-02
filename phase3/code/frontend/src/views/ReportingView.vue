<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { get, post, query } from '../api/client';
import type { Attendee, CampusEvent, LiveCounts, Page, Session, Statistics } from '../types';
import { useAppStore } from '../stores/app';
import { useTask } from '../composables/useTask';
import { dateTime, label, saveFile, money } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import AppIcon from '../components/AppIcon.vue';
import StatePanel from '../components/StatePanel.vue';
import StatusBadge from '../components/StatusBadge.vue';
const app = useAppStore();
const route = useRoute();
const task = useTask();
const section = computed(() => String(route.meta.section));
const checker = computed(() => app.user?.role === 'CHECKER');
const event = ref<CampusEvent>();
const eventId = ref(String(route.params.eventId || ''));
const filters = reactive({
  sessionId: '',
  ticketTypeId: '',
  orderStatus: '',
  checkinStatus: '',
  page: 1,
  pageSize: 20,
});
const rows = ref<Attendee[]>([]);
const total = ref(0);
const counts = ref<LiveCounts>();
const stats = ref<Statistics>();
const payment = ref<{
  paidAmountJiao: number;
  refundedAmountJiao: number;
  netAmountJiao: number;
}>();
const loading = ref(true);
const error = ref('');
const chart = computed(() =>
  Object.entries(counts.value?.orderCounts || {}).map(([status, value]) => ({ status, value })),
);
const chartMax = computed(() => Math.max(1, ...chart.value.map((c) => c.value)));
async function load() {
  loading.value = true;
  error.value = '';
  try {
    if (checker.value) {
      const context = await get<{ session: Session }>('/checker/context');
      eventId.value = context.session.eventId;
      filters.sessionId = context.session.id;
      event.value = {
        id: eventId.value,
        name: context.session.eventName,
        sessions: [context.session],
      } as CampusEvent;
    } else
      event.value = await get<CampusEvent>(
        `/${app.user?.role === 'ADMIN' ? 'admin' : 'organizer'}/events/${eventId.value}`,
      );
    if (section.value === 'attendees') {
      const result = await get<Page<Attendee>>(
        `/management/events/${eventId.value}/attendees${query(filters)}`,
      );
      rows.value = result.items;
      total.value = result.total;
    } else {
      const suffix = query({ sessionId: filters.sessionId });
      stats.value = await get<Statistics>(
        `/management/events/${eventId.value}/statistics${suffix}`,
      );
      counts.value = undefined;
      if (stats.value.settlementStatus === 'NOT_DUE')
        counts.value = await get<LiveCounts>(
          `/management/events/${eventId.value}/live-counts${suffix}`,
        );
      if (!checker.value)
        payment.value = await get(`/management/events/${eventId.value}/payment-summary${suffix}`);
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法读取业务数据';
  } finally {
    loading.value = false;
  }
}
async function exportCsv() {
  await task.run(async () => {
    const result = await post<{
      id: string;
      status: string;
      rowCount: number;
      downloadUrl: string | null;
    }>(`/management/events/${eventId.value}/exports`, {
      sessionId: filters.sessionId || undefined,
      ticketTypeId: filters.ticketTypeId || undefined,
      orderStatus: filters.orderStatus || undefined,
      checkinStatus: filters.checkinStatus || undefined,
    });
    if (result.status !== 'SUCCEEDED') {
      app.toast('导出任务已创建，请稍后在任务列表确认完成后下载');
      return;
    }
    const blob = await get<Blob>(`/management/exports/${result.id}/download`);
    saveFile(blob, `活动名单_${event.value?.name || '活动'}.csv`);
    app.toast(`已导出${result.rowCount}条，空结果仍包含表头`);
  });
}
onMounted(load);
</script>
<template>
  <PageHeading
    eyebrow="CLEAR INFORMATION, CALM DECISIONS"
    :title="
      section === 'attendees' ? '活动名单与导出' : checker ? '授权场次签到统计' : '统计与实时计数'
    "
    :description="event?.name || '按角色与活动范围读取业务数据。'"
    ><button class="btn secondary small" @click="load">
      <AppIcon name="RefreshCw" :size="14" />刷新数据</button
    ><button
      v-if="section === 'attendees' && !checker"
      class="btn primary small"
      :disabled="task.busy.value || !!error"
      @click="exportCsv"
    >
      <AppIcon name="Download" :size="15" />导出 CSV
    </button></PageHeading
  >
  <div class="alert info" style="margin-bottom: 24px">
    <AppIcon name="ShieldCheck" />
    <p>
      名单、统计和CSV统一保留至整活动实际终结后7天，所有角色均受限制。{{
        checker
          ? '检票员仅可查看当前授权场次，不具备导出或支付汇总权限。'
          : '下载时再次校验权限和期限，晚导出不会延长期限。'
      }}
    </p>
  </div>
  <StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" /><template
    v-else-if="section === 'attendees'"
    ><section class="panel">
      <form
        class="toolbar"
        @submit.prevent="
          filters.page = 1;
          load();
        "
      >
        <select v-if="!checker" v-model="filters.sessionId" aria-label="筛选场次">
          <option value="">全部场次</option>
          <option v-for="s in event?.sessions" :key="s.id" :value="s.id">
            {{ s.name }}
          </option></select
        ><select v-model="filters.orderStatus" aria-label="筛选订单状态">
          <option value="">全部订单状态</option>
          <option
            v-for="s in [
              'WAITLISTED',
              'OFFERED',
              'UNPAID',
              'REGISTERED',
              'CHECKED_IN',
              'COMPLETED',
              'REFUNDED',
              'CANCELLED',
              'ABSENT',
            ]"
            :key="s"
            :value="s"
          >
            {{ label(s) }}
          </option></select
        ><select v-model="filters.checkinStatus" aria-label="筛选签到状态">
          <option value="">全部签到状态</option>
          <option value="CHECKED">已签到</option>
          <option value="NOT_CHECKED">未签到</option></select
        ><button class="btn secondary small"><AppIcon name="Filter" :size="14" />应用筛选</button>
      </form>
      <div v-if="rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>姓名</th>
              <th>场次</th>
              <th>票种</th>
              <th>报名状态</th>
              <th>签到状态</th>
              <th>票 id</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in rows" :key="`${row.ticketId}-${i}`">
              <td>{{ row.attendeeName }}</td>
              <td>{{ row.sessionName }}</td>
              <td>{{ row.ticketTypeName }}</td>
              <td><StatusBadge :status="row.orderStatus" /></td>
              <td>{{ row.checkinStatus === 'CHECKED' ? '已签到' : '未签到' }}</td>
              <td class="mono">{{ row.ticketId || '尚未出票' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <StatePanel
        v-else
        title="没有匹配的名单记录"
        description="调整筛选条件；空结果导出仍会包含CSV表头。"
        icon="UsersRound"
      />
      <div class="pagination">
        <span>共 {{ total }} 条记录 · 每页 {{ filters.pageSize }} 条</span>
        <div class="page-buttons">
          <button
            :disabled="filters.page === 1"
            aria-label="上一页"
            @click="
              filters.page--;
              load();
            "
          >
            <AppIcon name="ChevronLeft" :size="13" /></button
          ><span class="current">{{ filters.page }}</span
          ><button
            :disabled="filters.page * filters.pageSize >= total"
            aria-label="下一页"
            @click="
              filters.page++;
              load();
            "
          >
            <AppIcon name="ChevronRight" :size="13" />
          </button>
        </div>
      </div></section></template
  ><template v-else
    ><section v-if="counts">
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-label">当前签到数<AppIcon name="CheckCircle2" :size="16" /></div>
          <div class="stat-number">{{ counts.checkedIn }}</div>
          <div class="stat-foot">{{ dateTime(counts.asOf) }}更新</div>
        </div>
        <template v-if="!checker"
          ><div class="stat-card">
            <div class="stat-label">可分配余票</div>
            <div class="stat-number">{{ counts.remaining }}</div>
            <div class="stat-foot">不含未分配给票种的容量</div>
          </div>
          <div class="stat-card rose">
            <div class="stat-label">当前候补</div>
            <div class="stat-number">{{ counts.currentWaitlist }}</div>
            <div class="stat-foot">递补待确认另计</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">累计候补申请</div>
            <div class="stat-number">{{ counts.cumulativeWaitlistApplications }}</div>
            <div class="stat-foot">按申请订单计数，重试不累计</div>
          </div></template
        >
      </div>
      <section v-if="!checker" class="panel">
        <div class="panel-heading">
          <h2>当前订单状态分布</h2>
          <span class="tag">实时读取 · 非历史快照</span>
        </div>
        <div class="chart-bars">
          <div v-for="c in chart" :key="c.status" class="chart-column">
            <b>{{ c.value }}</b>
            <div class="bar" :style="{ height: `${Math.max(2, (c.value / chartMax) * 110)}px` }" />
            <small>{{ label(c.status) }}</small>
          </div>
        </div>
        <p class="field-help">
          操作事务完成后，下一次刷新反映最新结果。未结束场次不提供最终缺席率。
        </p>
      </section>
    </section>
    <section class="panel" style="margin-top: 23px">
      <div class="panel-heading">
        <h2>正式结束统计</h2>
        <StatusBadge :status="stats?.settlementStatus" />
      </div>
      <template v-if="stats?.settlementStatus === 'COMPLETED'"
        ><div class="stat-grid">
          <div
            v-for="item in checker
              ? [['签到数', stats.checkedInCount]]
              : [
                  ['曾成功出票', stats.issuedCount],
                  ['退款数', stats.refundCount],
                  ['签到数', stats.checkedInCount],
                  ['缺席数', stats.absentCount],
                ]"
            :key="String(item[0])"
            class="stat-card"
          >
            <div class="stat-label">{{ item[0] }}</div>
            <div class="stat-number">{{ item[1] }}</div>
          </div>
        </div>
        <div class="info-list">
          <div class="info-row">
            <span>签到率</span
            ><strong>{{ ((stats.attendanceRate || 0) * 100).toFixed(1) }}%</strong>
          </div>
          <template v-if="!checker"
            ><div class="info-row">
              <span>缺席率</span><strong>{{ ((stats.absenceRate || 0) * 100).toFixed(1) }}%</strong>
            </div>
            <div class="info-row">
              <span>容量利用率</span
              ><strong>{{ ((stats.capacityUtilizationRate || 0) * 100).toFixed(1) }}%</strong>
            </div></template
          >
        </div></template
      >
      <div v-else class="alert info">
        <AppIcon name="Clock3" />
        <p>
          {{
            stats?.settlementStatus === 'PROCESSING'
              ? '订单尚在结算处理中，完成后才发布完整快照。'
              : '当前活动尚未结束，正式报表尚未生成。这里不会用0伪装未完成的统计。'
          }}
        </p>
      </div>
    </section>
    <section v-if="payment && !checker" class="panel">
      <div class="panel-heading">
        <h2>支付汇总</h2>
        <span class="tag">仅聚合，不含个人逐笔明细</span>
      </div>
      <div class="info-list">
        <div class="info-row">
          <span>累计成功支付</span><strong>¥{{ money(payment.paidAmountJiao) }}</strong>
        </div>
        <div class="info-row">
          <span>累计成功退款</span><strong>¥{{ money(payment.refundedAmountJiao) }}</strong>
        </div>
        <div class="info-row">
          <span>净额</span><strong class="rose-text">¥{{ money(payment.netAmountJiao) }}</strong>
        </div>
      </div>
    </section></template
  >
</template>
