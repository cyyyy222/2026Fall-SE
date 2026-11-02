<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { get, patch, post, query, request } from '../api/client';
import type { CampusEvent, Page } from '../types';
import { useTask } from '../composables/useTask';
import { useAppStore } from '../stores/app';
import { dateTime, label, money, roleNames, uid } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import StatePanel from '../components/StatePanel.vue';
import StatusBadge from '../components/StatusBadge.vue';
import AppIcon from '../components/AppIcon.vue';
import AppModal from '../components/AppModal.vue';
type Row = Record<string, unknown>;
const route = useRoute();
const app = useAppStore();
const task = useTask();
const section = computed(() => String(route.params.section || 'reviews'));
const config: Record<string, { title: string; description: string; icon: string; path: string }> = {
  reviews: {
    title: '活动审核',
    description: '让每一次相聚，都从认真审核开始。通过即发布，超期不自动通过。',
    icon: 'ClipboardCheck',
    path: '/admin/reviews',
  },
  events: {
    title: '平台活动',
    description: '查找活动、定位业务入口；历史活动元数据与短期名单统计分开。',
    icon: 'CalendarDays',
    path: '/admin/events',
  },
  accounts: {
    title: '账号与检票授权',
    description: '一个账号，一种固定身份。检票账号仅绑定一个场次。',
    icon: 'UsersRound',
    path: '/admin/accounts',
  },
  categories: {
    title: '活动类别',
    description: '维护清晰的分类，让喜欢的活动更容易被发现。',
    icon: 'List',
    path: '/admin/categories',
  },
  reports: {
    title: '举报核查',
    description: '依据可追溯事实作出判断，保护举报人和参与者的信息。',
    icon: 'ShieldCheck',
    path: '/admin/reports',
  },
  investigations: {
    title: '核查记录',
    description: '同活动同一事实仅记一次违规；已终态活动不回改状态。',
    icon: 'ShieldCheck',
    path: '/admin/investigations',
  },
  restrictions: {
    title: '业务限制与事实核实',
    description: '限制新业务，不影响合法既有订单履约。按手机号身份关联事实。',
    icon: 'LockKeyhole',
    path: '/admin/restriction-candidates',
  },
  payments: {
    title: '支付记录与退款排障',
    description: '查看模拟交易记录；仅按合法失败依据重试退款，不提供代支付。',
    icon: 'Ticket',
    path: '/admin/payments',
  },
  audit: {
    title: '操作日志与单条审计',
    description: '关键动作可追踪；日志不提供人工改删或导出。',
    icon: 'FileText',
    path: '/admin/audit-logs',
  },
  jobs: {
    title: '持久任务与结算进度',
    description: '终止立即生效，逐项处理可恢复。超期仍继续，并记录未达标。',
    icon: 'RefreshCw',
    path: '/admin/jobs',
  },
  recoveries: {
    title: '人工找回与密码重置',
    description: '先进行线下身份核验并保存依据，再重置指定账号密码。',
    icon: 'HelpCircle',
    path: '/admin/recoveries',
  },
};
const current = computed(() => config[section.value] || config.events!);
const rows = ref<Row[]>([]);
const loading = ref(true);
const error = ref('');
const search = ref('');
const tab = ref('payments');
const modal = ref('');
const selected = ref<Row>();
const detail = ref<unknown>();
const eventOptions = ref<CampusEvent[]>([]);
const total = ref(0);
const dailyPublished = ref(0);
const pageNumber = ref(1);
const form = reactive({
  username: '',
  initialPassword: 'Demo2026',
  role: 'CHECKER',
  sessionId: '',
  name: '',
  reason: '',
  evidenceText: '',
  conclusion: 'UNSUBSTANTIATED',
  factKey: '',
  verificationNote: '',
  decision: 'VERIFIED',
  objectType: 'ORDER',
  objectId: '',
});
const s = (row: Row, key: string) => (row[key] == null ? '—' : String(row[key]));
const n = (row: Row, key: string) => Number(row[key] || 0);
const id = (row: Row) => String(row.id || '');
const visible = computed(() =>
  !search.value || ['payments', 'audit'].includes(section.value)
    ? rows.value
    : rows.value.filter((row) =>
        JSON.stringify(row).toLowerCase().includes(search.value.toLowerCase()),
      ),
);
const allSessions = computed(() =>
  eventOptions.value.flatMap((e) =>
    e.sessions.map((session) => ({ ...session, eventName: e.name })),
  ),
);
const eventName = (eventId: unknown) =>
  eventOptions.value.find((e) => e.id === eventId)?.name || String(eventId || '—');
const titleOf = (row: Row) => (s(row, 'name') === '—' ? '未命名草稿' : s(row, 'name'));
const badge = (row: Row) => (s(row, 'status') === '—' ? s(row, 'result') : s(row, 'status'));
const endpoint = computed(() =>
  section.value === 'payments' && tab.value === 'refunds'
    ? '/admin/refund-requests'
    : current.value.path,
);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const extra =
      section.value === 'audit'
        ? { objectId: search.value || undefined }
        : section.value === 'payments'
          ? { orderId: search.value || undefined }
          : {};
    const result = await get<Page<Row> & { dailyPublishedCount?: number }>(
      `${endpoint.value}${query({ page: pageNumber.value, pageSize: 20, ...extra })}`,
    );
    rows.value = result.items;
    total.value = result.total;
    dailyPublished.value = result.dailyPublishedCount || 0;
    if (['accounts', 'reports', 'events', 'investigations'].includes(section.value))
      eventOptions.value = (await get<Page<CampusEvent>>('/admin/events?pageSize=100')).items;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法读取管理数据';
  } finally {
    loading.value = false;
  }
}
function open(kind: string, row?: Row) {
  selected.value = row;
  modal.value = kind;
  form.name = row ? s(row, 'name') : '';
  form.reason = '';
  form.evidenceText = '';
  form.factKey = uid();
  form.verificationNote = '';
  form.sessionId = row && row.assignedSessionId ? String(row.assignedSessionId) : '';
}
async function submit() {
  await task.run(async () => {
    if (modal.value === 'account')
      await post('/admin/accounts', {
        username: form.username,
        initialPassword: form.initialPassword,
        role: form.role,
        ...(form.role === 'CHECKER' ? { sessionId: form.sessionId } : {}),
      });
    else if (modal.value === 'binding')
      await patch(`/admin/accounts/${id(selected.value!)}`, {
        expectedVersion: n(selected.value!, 'version'),
        assignedSessionId: form.sessionId,
      });
    else if (modal.value === 'accountReset')
      await post(`/admin/accounts/${id(selected.value!)}/password-reset`, {
        initialPassword: form.initialPassword,
        reason: form.reason,
      });
    else if (modal.value === 'deleteAccount')
      await request(
        'DELETE',
        `/admin/accounts/${id(selected.value!)}?expectedVersion=${n(selected.value!, 'version')}`,
        undefined,
        uid(),
      );
    else if (modal.value === 'refundRetry')
      await post(`/admin/refund-requests/${id(selected.value!)}/attempts`, {
        attemptId: uid(),
        reason: form.reason,
      });
    else if (modal.value === 'restriction')
      await post('/admin/registration-restrictions', {
        phoneIdentityId: s(selected.value!, 'phoneIdentityId'),
        basisFactIds: selected.value!.eligibleFactIds,
        reason: form.reason,
      });
    else if (modal.value === 'category') {
      if (selected.value)
        await patch(`/admin/categories/${id(selected.value)}`, {
          name: form.name,
          expectedVersion: n(selected.value, 'version'),
        });
      else await post('/admin/categories', { name: form.name });
    } else if (modal.value === 'investigate')
      await post('/admin/investigations', {
        eventId: s(selected.value!, 'eventId'),
        reportIds: [id(selected.value!)],
        factKey: form.factKey,
        conclusion: form.conclusion,
        reason: form.reason,
        evidenceText: form.evidenceText,
      });
    else if (modal.value === 'recovery')
      await post(`/admin/recoveries/${id(selected.value!)}/resolution`, {
        decision: form.decision,
        verificationNote: form.verificationNote,
      });
    else if (modal.value === 'reset')
      await post(`/admin/accounts/${s(selected.value!, 'targetAccountId')}/password-reset`, {
        initialPassword: form.initialPassword,
        reason: form.reason,
        recoveryRequestId: id(selected.value!),
      });
    modal.value = '';
    await load();
    app.toast('操作已完成');
  });
}
async function toggle(row: Row) {
  await task.run(async () => {
    if (section.value === 'categories')
      await patch(`/admin/categories/${id(row)}`, {
        expectedVersion: n(row, 'version'),
        enabled: !row.enabled,
      });
    else
      await patch(`/admin/accounts/${id(row)}`, {
        expectedVersion: n(row, 'version'),
        enabled: !row.enabled,
      });
    await load();
  }, '设置已更新');
}
async function inspect(row: Row) {
  await task.run(async () => {
    const orderId = s(row, 'orderId');
    detail.value =
      section.value === 'payments' ? await get(`/admin/audit-objects/ORDER/${orderId}`) : row;
    modal.value = 'detail';
  });
}
async function auditObject() {
  await task.run(async () => {
    detail.value = await get(`/admin/audit-objects/${form.objectType}/${form.objectId}`);
    modal.value = 'detail';
  });
}
onMounted(load);
</script>
<template>
  <PageHeading
    eyebrow="CARE FOR EVERY GOOD MOMENT"
    :title="current.title"
    :description="current.description"
    ><button class="btn secondary small" @click="load">
      <AppIcon name="RefreshCw" :size="14" />刷新</button
    ><button
      v-if="section === 'accounts' || section === 'categories'"
      class="btn primary small"
      @click="open(section === 'accounts' ? 'account' : 'category')"
    >
      <AppIcon name="Plus" :size="15" />{{
        section === 'accounts' ? '创建账号' : '新增类别'
      }}</button
    ><RouterLink v-if="section === 'reports'" to="/admin/investigations" class="btn secondary small"
      >核查记录<AppIcon name="ArrowUpRight" :size="14" /></RouterLink
  ></PageHeading>
  <div v-if="section === 'reviews'" class="stat-grid">
    <div class="stat-card">
      <div class="stat-label">待审核活动<AppIcon name="ClipboardCheck" :size="16" /></div>
      <div class="stat-number">{{ total }}</div>
      <div class="stat-foot">按提交顺序处理</div>
    </div>
    <div class="stat-card rose">
      <div class="stat-label">超过24小时</div>
      <div class="stat-number">
        {{ rows.filter((r) => Date.now() - Date.parse(s(r, 'submittedAt')) > 86400000).length }}
      </div>
      <div class="stat-foot">人工处理，不自动通过</div>
    </div>
    <div class="stat-card">
      <div class="stat-label">今日首次发布</div>
      <div class="stat-number">
        {{ dailyPublished }}<small style="font: 12px Segoe UI; color: #a0a58e"> / 100</small>
      </div>
      <div class="stat-foot">北京时间自然日，取消不返额</div>
    </div>
    <div class="stat-card soft">
      <div class="stat-label">发布检查<AppIcon name="ShieldCheck" :size="16" /></div>
      <div style="font-size: 14px; margin: 17px 0 8px; color: #768467">
        先配置每场检票账号，再进行审核
      </div>
      <div class="stat-foot">复核类别、时间、排期、限制与每日额度</div>
    </div>
  </div>
  <div v-if="section === 'restrictions'" class="stat-grid">
    <div class="stat-card soft" style="grid-column: span 2">
      <div class="stat-label">参与者新报名限制</div>
      <h3 style="font-family: var(--serif); margin: 14px 0 8px">3次缺席 / 5次主动成功退款</h3>
      <p class="field-help">连续30天事实，经管理员核实后限制15天。</p>
    </div>
    <div class="stat-card" style="grid-column: span 2">
      <div class="stat-label">组织者新发布限制</div>
      <h3 style="font-family: var(--serif); margin: 14px 0 8px">3次合格违规事实</h3>
      <p class="field-help">自动限制15天，已有活动不会自动取消。</p>
    </div>
  </div>
  <div v-if="section === 'jobs'" class="alert info" style="margin-bottom: 23px">
    <AppIcon name="Clock3" />
    <p>
      超时释放、递补、自然结算目标1分钟；取消/下架结算和必发通知目标5分钟。前端演示同步处理终止，不能代表真实持久任务已通过验收。
    </p>
  </div>
  <section class="panel">
    <div v-if="section === 'payments'" class="tabs">
      <button
        :class="{ active: tab === 'payments' }"
        @click="
          tab = 'payments';
          pageNumber = 1;
          load();
        "
      >
        支付记录</button
      ><button
        :class="{ active: tab === 'refunds' }"
        @click="
          tab = 'refunds';
          pageNumber = 1;
          load();
        "
      >
        合法退款依据
      </button>
    </div>
    <form
      class="toolbar"
      @submit.prevent="
        pageNumber = 1;
        load();
      "
    >
      <div class="search-input" style="max-width: 360px; flex: 1">
        <AppIcon name="Search" :size="15" /><input
          v-model="search"
          :placeholder="
            section === 'payments'
              ? '按完整订单编号查询'
              : section === 'audit'
                ? '按完整对象编号查询日志'
                : '在当前页搜索名称、状态或编号'
          "
          aria-label="筛选管理记录"
        />
      </div>
      <button v-if="['payments', 'audit'].includes(section)" class="btn secondary small">
        查询</button
      ><span class="push-right result-total">{{ total }} 条记录</span>
    </form>
    <StatePanel
      v-if="loading || error"
      :loading="loading"
      :error="error"
      @retry="load"
    /><StatePanel
      v-else-if="!visible.length"
      :title="
        section === 'restrictions'
          ? '当前没有待核实的限制候选'
          : section === 'jobs'
            ? '当前没有后台任务记录'
            : section === 'audit'
              ? '暂无符合条件的操作日志'
              : section === 'recoveries'
                ? '暂无人工找回申请'
                : '没有匹配的记录'
      "
      :description="
        section === 'jobs'
          ? '取消或下架活动后，会在这里查看本地演示的结算结果。'
          : section === 'restrictions'
            ? '当前演示夹具未构造达到处罚阈值的事实，不显示虚构候选或处罚成功。'
            : '新的业务记录将在操作后显示。'
      "
      :icon="current.icon"
    />
    <div v-else class="table-wrap">
      <table v-if="['reviews', 'events'].includes(section)">
        <thead>
          <tr>
            <th>活动</th>
            <th>状态</th>
            <th>场次</th>
            <th>提交时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="id(row)">
            <td class="table-title">
              {{ titleOf(row) }}<small>{{ s(row, 'categoryNameSnapshot') }}</small>
            </td>
            <td><StatusBadge :status="s(row, 'status')" /></td>
            <td>{{ (row.sessions as unknown[])?.length || 0 }}场</td>
            <td>{{ dateTime(s(row, 'submittedAt') === '—' ? null : s(row, 'submittedAt')) }}</td>
            <td>
              <RouterLink :to="`/admin/events/${id(row)}`" class="text-link"
                >{{ section === 'reviews' ? '查看并审核' : '管理详情'
                }}<AppIcon name="ArrowUpRight" :size="13"
              /></RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="section === 'accounts'">
        <thead>
          <tr>
            <th>账号</th>
            <th>固定角色</th>
            <th>可用状态</th>
            <th>授权场次</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="id(row)">
            <td>
              {{ s(row, 'username')
              }}<span v-if="row.mustChangePassword" class="secondary-text">下次登录须改密</span>
            </td>
            <td>{{ roleNames[s(row, 'role') as keyof typeof roleNames] }}</td>
            <td>
              <StatusBadge
                :status="row.enabled ? 'VALID' : 'INVALID'"
                :text="row.enabled ? '可用' : '停用'"
              />
            </td>
            <td>
              {{ allSessions.find((x) => x.id === row.assignedSessionId)?.name || '—'
              }}<small v-if="row.assignedSessionId" class="secondary-text">{{
                eventName(row.eventId)
              }}</small>
            </td>
            <td>
              <div
                v-if="['ADMIN', 'CHECKER'].includes(s(row, 'role')) && !row.deletedAt"
                class="action-group"
              >
                <button class="text-link" :disabled="task.busy.value" @click="toggle(row)">
                  {{ row.enabled ? '停用' : '启用' }}</button
                ><button
                  v-if="row.role === 'CHECKER'"
                  class="text-link"
                  @click="open('binding', row)"
                >
                  改绑</button
                ><button class="text-link" @click="open('accountReset', row)">重置密码</button
                ><button class="text-link" @click="open('deleteAccount', row)">删除</button>
              </div>
              <span v-else class="muted" style="font-size: 10px">普通账号不可切换角色</span>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="section === 'categories'">
        <thead>
          <tr>
            <th>类别名称</th>
            <th>状态</th>
            <th>版本</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="id(row)">
            <td>{{ s(row, 'name') }}</td>
            <td>
              <StatusBadge
                :status="row.enabled ? 'VALID' : 'STOPPED'"
                :text="row.enabled ? '启用中' : '已停用'"
              />
            </td>
            <td>v{{ row.version }}</td>
            <td>
              <div class="action-group">
                <button class="text-link" @click="open('category', row)">改名</button
                ><button class="text-link" :disabled="task.busy.value" @click="toggle(row)">
                  {{ row.enabled ? '停用' : '启用' }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="section === 'reports'">
        <thead>
          <tr>
            <th>活动 / 原因</th>
            <th>受理时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="id(row)">
            <td>
              {{ eventName(row.eventId)
              }}<small class="secondary-text">{{ s(row, 'reason') }}</small>
            </td>
            <td>{{ dateTime(s(row, 'createdAt')) }}</td>
            <td><StatusBadge :status="s(row, 'status')" /></td>
            <td>
              <button
                v-if="row.status === 'PENDING'"
                class="text-link"
                @click="open('investigate', row)"
              >
                核查处理</button
              ><button
                v-else
                class="text-link"
                @click="
                  detail = row;
                  modal = 'detail';
                "
              >
                查看结论
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="section === 'investigations'">
        <thead>
          <tr>
            <th>核查编号</th>
            <th>活动</th>
            <th>结论</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="id(row)">
            <td class="mono">{{ id(row) }}</td>
            <td>{{ eventName(row.eventId) }}</td>
            <td><StatusBadge :status="s(row, 'conclusion')" /></td>
            <td>
              <button
                class="text-link"
                @click="
                  detail = row;
                  modal = 'detail';
                "
              >
                查看依据
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="section === 'payments'">
        <thead>
          <tr>
            <th>{{ tab === 'refunds' ? '退款依据 / 原因' : '支付记录' }}</th>
            <th>金额 / 来源</th>
            <th>结果</th>
            <th>时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="id(row)">
            <td>
              <span class="mono">{{ id(row) }}</span
              ><span class="secondary-text mono">订单 {{ s(row, 'orderId') }}</span>
            </td>
            <td>
              {{ tab === 'refunds' ? label(s(row, 'source')) : `¥${money(n(row, 'amountJiao'))}` }}
            </td>
            <td><StatusBadge :status="badge(row)" /></td>
            <td>{{ dateTime(s(row, tab === 'refunds' ? 'acceptedAt' : 'createdAt')) }}</td>
            <td>
              <div class="action-group">
                <button class="text-link" :disabled="task.busy.value" @click="inspect(row)">
                  单条审计</button
                ><button
                  v-if="tab === 'refunds' && row.status === 'FAILED'"
                  class="text-link"
                  @click="open('refundRetry', row)"
                >
                  按依据重试
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="section === 'audit'">
        <thead>
          <tr>
            <th>时间</th>
            <th>操作者</th>
            <th>动作</th>
            <th>业务对象</th>
            <th>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="id(row)">
            <td>{{ dateTime(s(row, 'createdAt')) }}</td>
            <td>{{ s(row, 'actorRole') }}</td>
            <td>{{ s(row, 'action') }}</td>
            <td>
              <button
                class="text-link mono"
                @click="
                  form.objectId = s(row, 'objectId');
                  form.objectType = s(row, 'objectType') === 'TICKET' ? 'TICKET' : 'ORDER';
                "
              >
                {{ s(row, 'objectType') }} · {{ s(row, 'objectId').slice(0, 8) }}…
              </button>
            </td>
            <td><StatusBadge :status="s(row, 'result')" /></td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="section === 'jobs'">
        <thead>
          <tr>
            <th>任务</th>
            <th>状态</th>
            <th>进度</th>
            <th>时限</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="id(row)">
            <td>
              {{ s(row, 'type') }}<small class="secondary-text mono">{{ id(row) }}</small>
            </td>
            <td><StatusBadge :status="s(row, 'status')" /></td>
            <td>
              {{ n(row, 'processed') }} / {{ n(row, 'total') }}
              <div class="progress-track">
                <span
                  :style="{
                    width: `${n(row, 'total') ? (n(row, 'processed') / n(row, 'total')) * 100 : 100}%`,
                  }"
                />
              </div>
            </td>
            <td>{{ row.overdue ? '已超时，继续处理' : '目标内' }}</td>
            <td>
              <button
                class="text-link"
                @click="
                  detail = row;
                  modal = 'detail';
                "
              >
                查看进度
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="section === 'restrictions'">
        <thead>
          <tr>
            <th>手机号身份</th>
            <th>限制类型</th>
            <th>正常缺席</th>
            <th>主动退款</th>
            <th>违规</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="s(row, 'phoneIdentityId')">
            <td>{{ s(row, 'maskedPhone') }}</td>
            <td>{{ row.kind === 'REGISTER' ? '新报名限制' : '新发布限制' }}</td>
            <td>{{ row.absenceCount }}</td>
            <td>{{ row.voluntaryRefundCount }}</td>
            <td>{{ row.violationCount }}</td>
            <td>
              <button
                v-if="row.kind === 'REGISTER'"
                class="text-link"
                @click="open('restriction', row)"
              >
                核实并确认限制</button
              ><span v-else class="muted">按违规事实自动生效</span>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="section === 'recoveries'">
        <thead>
          <tr>
            <th>账号</th>
            <th>登记手机号</th>
            <th>状态</th>
            <th>受理时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="id(row)">
            <td>{{ s(row, 'account') }}</td>
            <td>{{ s(row, 'phone') }}</td>
            <td>{{ s(row, 'status') }}</td>
            <td>{{ dateTime(s(row, 'createdAt')) }}</td>
            <td>
              <button
                v-if="row.status === 'PENDING'"
                class="text-link"
                @click="open('recovery', row)"
              >
                记录线下核验</button
              ><button
                v-if="row.status === 'VERIFIED'"
                class="text-link"
                @click="open('reset', row)"
              >
                重置密码</button
              ><span v-if="['REJECTED', 'RESOLVED'].includes(s(row, 'status'))" class="muted"
                >已处理</span
              >
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!loading && total" class="pagination">
      <span>第 {{ pageNumber }} 页 · 每页20条</span>
      <div class="page-buttons">
        <button
          :disabled="pageNumber <= 1"
          aria-label="上一页"
          @click="
            pageNumber--;
            load();
          "
        >
          <AppIcon name="ChevronLeft" :size="13" /></button
        ><span class="current">{{ pageNumber }}</span
        ><button
          :disabled="pageNumber * 20 >= total"
          aria-label="下一页"
          @click="
            pageNumber++;
            load();
          "
        >
          <AppIcon name="ChevronRight" :size="13" />
        </button>
      </div>
    </div>
  </section>
  <section v-if="section === 'audit'" class="panel">
    <div class="panel-heading">
      <h2>单条对象审计</h2>
      <span class="tag">不能重建过期名单或汇总</span>
    </div>
    <form class="toolbar" @submit.prevent="auditObject">
      <select v-model="form.objectType" aria-label="审计对象类型">
        <option v-for="t in ['ORDER', 'PAYMENT', 'REFUND', 'TICKET', 'CHECKIN']" :key="t">
          {{ t }}
        </option></select
      ><input
        v-model="form.objectId"
        required
        placeholder="输入完整业务编号"
        aria-label="单条对象编号"
        style="max-width: 400px; flex: 1"
      /><button class="btn primary small" :disabled="task.busy.value">追踪单条对象</button>
    </form>
  </section>
  <div v-if="section === 'restrictions'" class="alert info" style="margin-top: 23px">
    <AppIcon name="ShieldCheck" />
    <p>
      0元主动退款同样计次；平台退款、候补放弃、未支付取消不计。旧依据不能重复处罚，当前演示不伪造达到阈值的名单。
    </p>
  </div>
  <AppModal
    :open="modal !== ''"
    :title="
      modal === 'account'
        ? '创建特权账号'
        : modal === 'category'
          ? selected
            ? '修改类别名称'
            : '新增活动类别'
          : modal === 'investigate'
            ? '核查举报与记录依据'
            : modal === 'recovery'
              ? '记录人工核验结论'
              : modal === 'reset'
                ? '按核验依据重置密码'
                : modal === 'binding'
                  ? '改绑检票场次'
                  : modal === 'accountReset'
                    ? '重置特权账号密码'
                    : modal === 'deleteAccount'
                      ? '逻辑删除特权账号'
                      : modal === 'restriction'
                        ? '核实报名限制依据'
                        : modal === 'refundRetry'
                          ? '按原合法依据重试退款'
                          : '单条记录详情'
    "
    :wide="modal === 'detail' || modal === 'investigate'"
    @close="modal = ''"
  >
    <pre v-if="modal === 'detail'" class="data-inspector">{{
      JSON.stringify(detail, null, 2)
    }}</pre>
    <form v-else @submit.prevent="submit">
      <template v-if="modal === 'account'"
        ><div class="form-field">
          <label for="new-account-role">账号角色</label
          ><select id="new-account-role" v-model="form.role">
            <option value="CHECKER">检票员</option>
            <option value="ADMIN">管理员</option>
          </select>
        </div>
        <div class="form-field">
          <label for="new-account-name">用户名</label
          ><input id="new-account-name" v-model="form.username" required />
        </div>
        <div class="form-field">
          <label for="new-account-password">初始密码</label
          ><input id="new-account-password" v-model="form.initialPassword" required minlength="8" />
        </div>
        <div v-if="form.role === 'CHECKER'" class="form-field">
          <label for="new-account-session">授权场次</label
          ><select id="new-account-session" v-model="form.sessionId" required>
            <option value="" disabled>选择已存在场次</option>
            <option v-for="session in allSessions" :key="session.id" :value="session.id">
              {{ session.eventName }} · {{ session.name }}
            </option>
          </select>
        </div>
        <p class="field-help">
          管理员首次登录需改密。检票账号一个仅绑定一场，发布前每场至少一个可用。
        </p></template
      >
      <div v-if="modal === 'category'" class="form-field">
        <label for="category-name">类别名称</label
        ><input id="category-name" v-model="form.name" required maxlength="60" /><small
          >名称去首尾空白，英文大小写不区分，停用类别仍占用名称。</small
        >
      </div>
      <template v-if="modal === 'investigate'"
        ><div class="alert info">
          <AppIcon name="FileText" />
          <p>{{ s(selected!, 'description') }}<br />举报证据：{{ s(selected!, 'evidenceText') }}</p>
        </div>
        <div class="form-field" style="margin-top: 20px">
          <label for="conclusion">人工核查结论</label
          ><select id="conclusion" v-model="form.conclusion">
            <option value="UNSUBSTANTIATED">不属实，不处罚</option>
            <option value="SUBSTANTIATED">属实，记录违规并依法下架</option>
          </select>
        </div>
        <div class="form-field">
          <label for="fact-key">同一事实标识</label
          ><input id="fact-key" v-model="form.factKey" required maxlength="120" />
        </div>
        <div class="form-field">
          <label for="investigation-reason">处理理由</label
          ><textarea id="investigation-reason" v-model="form.reason" required maxlength="200" />
        </div>
        <div class="form-field">
          <label for="investigation-evidence">核查证据依据</label
          ><textarea
            id="investigation-evidence"
            v-model="form.evidenceText"
            required
            maxlength="2000"
          />
        </div>
        <p class="field-help">
          属实且仍已发布则下架，终态只记录违规；下架不可恢复。同活动同一事实不重复记违规。
        </p></template
      ><template v-if="modal === 'recovery'"
        ><div class="form-field">
          <label for="recovery-decision">核验结论</label
          ><select id="recovery-decision" v-model="form.decision">
            <option value="VERIFIED">已完成线下核验</option>
            <option value="REJECTED">核验未通过</option>
          </select>
        </div>
        <div class="form-field">
          <label for="recovery-note">线下核验记录</label
          ><textarea
            id="recovery-note"
            v-model="form.verificationNote"
            required
            maxlength="2000"
          /></div></template
      ><template v-if="modal === 'reset'"
        ><p class="field-help" style="margin-bottom: 20px">
          仅使用这条已核验且绑定原账号的申请，不凭手机号直接重置。
        </p>
        <div class="form-field">
          <label for="reset-password">临时密码</label
          ><input id="reset-password" v-model="form.initialPassword" required minlength="8" />
        </div>
        <div class="form-field">
          <label for="reset-reason">操作理由</label
          ><input id="reset-reason" v-model="form.reason" required /></div></template
      ><template v-if="modal === 'binding'"
        ><div class="form-field">
          <label for="assignment">新的授权场次</label
          ><select id="assignment" v-model="form.sessionId" required>
            <option
              v-for="session in allSessions.filter((s) => s.eventId === selected?.eventId)"
              :key="session.id"
              :value="session.id"
            >
              {{ session.eventName }} · {{ session.name }}
            </option>
          </select>
        </div>
        <p class="field-help">
          仅同活动未结束场次，不能移走原场最后一个可用检票员；历史签到归属不改变。
        </p></template
      ><template v-if="['accountReset', 'refundRetry', 'restriction'].includes(modal)"
        ><div v-if="modal === 'accountReset'" class="form-field">
          <label for="privileged-reset">新的临时密码</label
          ><input id="privileged-reset" v-model="form.initialPassword" required minlength="8" />
        </div>
        <div class="form-field">
          <label for="basis-reason">核实依据与操作理由</label
          ><textarea id="basis-reason" v-model="form.reason" required maxlength="200" />
        </div>
        <p class="field-help">
          提交时重新核验原依据、当前状态与权限；不能通过此入口扩大原授权范围。
        </p></template
      >
      <div v-if="modal === 'deleteAccount'" class="alert warning">
        <AppIcon name="CircleAlert" />
        <p>
          账号凭据和旧会话立即失效，保留历史签到与审计关联；不能删除最后一个可用管理员或移走最后一个可用检票员。
        </p>
      </div>
      <p v-if="task.error.value" class="error-inline" role="alert">{{ task.error.value }}</p>
      <div class="button-row">
        <button class="btn secondary" type="button" @click="modal = ''">取消</button
        ><button class="btn primary" :disabled="task.busy.value">确认提交</button>
      </div>
    </form></AppModal
  >
</template>
