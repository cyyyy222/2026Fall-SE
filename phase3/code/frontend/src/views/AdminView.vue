<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { get, patch, post, query, request } from '../api/client';
import type { CampusEvent, Page, Role } from '../types';
import { useTask } from '../composables/useTask';
import { useAppStore } from '../stores/app';
import { dateTime, label, money, roleNames, uid } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import StatePanel from '../components/StatePanel.vue';
import StatusBadge from '../components/StatusBadge.vue';
import AppIcon from '../components/AppIcon.vue';
import AppModal from '../components/AppModal.vue';
import RecoveryDetails from '../components/RecoveryDetails.vue';
type Row = Record<string, unknown>;
const route = useRoute();
const app = useAppStore();
const task = useTask();
const section = computed(() => String(route.params.section || 'reviews'));
const accountModules: { role: Role; title: string; description: string }[] = [
  {
    role: 'PARTICIPANT',
    title: '参与者账号',
    description: '参与者自行注册；查看账号状态与脱敏手机号，找回申请另行核验。',
  },
  {
    role: 'ORGANIZER',
    title: '组织者账号',
    description: '组织者自行注册；可以与参与者共用手机号，同角色内不得重复。',
  },
  {
    role: 'CHECKER',
    title: '检票账号与授权',
    description: '创建独立检票账号，选择活动与场次，再把凭据交给对应组织者安排现场人员。',
  },
  {
    role: 'ADMIN',
    title: '管理员账号',
    description: '首个管理员由系统初始化；已有管理员可创建其他管理员，新管理员首次登录必须改密。',
  },
];
const accountRole = computed(
  () => accountModules.find((x) => x.role === route.query.role)?.role || 'CHECKER',
);
const accountModule = computed(() => accountModules.find((x) => x.role === accountRole.value)!);
const canCreateAccount = computed(
  () => section.value === 'accounts' && ['ADMIN', 'CHECKER'].includes(accountRole.value),
);
const credentials = ref<{
  username: string;
  password: string;
  role: string;
  assignment: string;
} | null>(null);
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
    title: '报名与发布限制',
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
    title: '操作记录与追溯',
    description: '关键动作可追踪；日志不提供人工改删或导出。',
    icon: 'FileText',
    path: '/admin/audit-logs',
  },
  jobs: {
    title: '后台处理进度',
    description: '终止立即生效，逐项处理可恢复。超期仍继续，并记录未达标。',
    icon: 'RefreshCw',
    path: '/admin/jobs',
  },
  recoveries: {
    title: '账号找回申请',
    description:
      '审核原手机号、用户名、当前号码及行为陈述；通过后换绑已验证的当前号码、重置临时密码并模拟短信通知。',
    icon: 'HelpCircle',
    path: '/admin/recoveries',
  },
};
const current = computed(() =>
  section.value === 'accounts'
    ? {
        ...config.accounts!,
        title: accountModule.value.title,
        description: accountModule.value.description,
      }
    : config[section.value] || config.events!,
);
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
  eventId: '',
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
    e.sessions.map((session) => ({ ...session, eventId: e.id, eventName: e.name })),
  ),
);
const configurableEvents = computed(() =>
  eventOptions.value.filter(
    (e) =>
      ['PENDING_REVIEW', 'PUBLISHED'].includes(e.status || '') &&
      e.sessions.some((s) => Date.parse(s.endsAt) > Date.now()),
  ),
);
const selectableSessions = computed(() =>
  allSessions.value.filter(
    (s) =>
      configurableEvents.value.some((e) => e.id === s.eventId) &&
      Date.parse(s.endsAt) > Date.now() &&
      (modal.value === 'binding'
        ? s.eventId === selected.value?.eventId
        : s.eventId === form.eventId),
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
          : section.value === 'accounts'
            ? { role: accountRole.value, username: search.value || undefined }
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
  form.eventId = row?.eventId ? String(row.eventId) : '';
  if (kind === 'account') {
    form.role = accountRole.value;
    form.username = '';
    form.initialPassword = '';
  }
  if (['accountReset', 'reset', 'recovery'].includes(kind)) form.initialPassword = '';
  if (kind === 'recovery') form.decision = 'VERIFIED';
}
function showCredentials(username: string, role: string) {
  const session = allSessions.value.find((s) => s.id === form.sessionId);
  credentials.value = {
    username,
    role,
    password: form.initialPassword,
    assignment: session ? `${session.eventName} · ${session.name}` : '',
  };
}
async function copyCredentials() {
  if (!credentials.value) return;
  const c = credentials.value;
  try {
    await navigator.clipboard.writeText(
      `活动票务登录凭据\n账号：${c.username}\n初始密码：${c.password}\n${c.assignment ? `授权：${c.assignment}\n` : ''}登录地址：${location.origin}/#/login\n请使用账号密码登录；组织者需退出自己的账号后使用检票账号登录。`,
    );
    app.toast('凭据已复制，请自行通过可信渠道交接');
  } catch {
    app.toast('无法自动复制，请手动复制下方账号和密码', 'error');
  }
}
async function submit() {
  await task.run(async () => {
    if (modal.value === 'account') {
      await post('/admin/accounts', {
        username: form.username,
        initialPassword: form.initialPassword,
        role: form.role,
        ...(form.role === 'CHECKER' ? { sessionId: form.sessionId } : {}),
      });
      showCredentials(form.username.trim(), form.role);
      form.initialPassword = '';
    } else if (modal.value === 'binding')
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
        ...(form.decision === 'VERIFIED'
          ? {
              initialPassword: form.initialPassword,
              reason: [...form.verificationNote].slice(0, 200).join(''),
            }
          : {}),
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
      v-if="canCreateAccount || section === 'categories'"
      class="btn primary small"
      @click="open(section === 'accounts' ? 'account' : 'category')"
    >
      <AppIcon name="Plus" :size="15" />{{
        section === 'accounts' ? `创建${roleNames[accountRole]}账号` : '新增类别'
      }}</button
    ><RouterLink v-if="section === 'reports'" to="/admin/investigations" class="btn secondary small"
      >核查记录<AppIcon name="ArrowUpRight" :size="14" /></RouterLink
  ></PageHeading>
  <nav v-if="section === 'accounts'" class="account-modules" aria-label="账号分类">
    <RouterLink
      v-for="item in accountModules"
      :key="item.role"
      :to="{ path: '/admin/accounts', query: { role: item.role } }"
      :class="{ active: accountRole === item.role }"
    >
      <AppIcon
        :name="
          item.role === 'CHECKER'
            ? 'ScanLine'
            : item.role === 'ADMIN'
              ? 'ShieldCheck'
              : 'UsersRound'
        "
        :size="18"
      />{{ item.title }}
    </RouterLink>
  </nav>
  <section
    v-if="section === 'accounts' && credentials"
    class="panel credential-receipt"
    aria-label="新账号凭据"
  >
    <h3>账号已创建，请完成凭据交接</h3>
    <p>
      {{
        credentials.role === 'CHECKER'
          ? '请把以下检票凭据交给对应活动组织者，由组织者安排工作人员。组织者需退出自己的账号，再用检票账号登录。'
          : '请把凭据交给新管理员，新管理员首次登录必须修改密码。'
      }}
    </p>
    <p v-if="credentials.assignment">授权活动与场次：{{ credentials.assignment }}</p>
    <div class="credential-values">
      <span
        >账号 <strong>{{ credentials.username }}</strong></span
      ><span
        >初始密码 <strong>{{ credentials.password }}</strong></span
      >
    </div>
    <p class="field-help">
      只在本次创建后展示初始密码，关闭或离开页面后不再显示。系统不保存可回查的明文密码，不自动向任何人发送凭据；遗失后由管理员重置。
    </p>
    <div class="button-row">
      <button class="btn secondary small" @click="copyCredentials">复制交接信息</button
      ><button class="btn secondary small" @click="credentials = null">已交接，关闭</button>
    </div>
  </section>
  <div v-if="section === 'accounts'" class="alert info" style="margin-bottom: 22px">
    <AppIcon name="Info" />
    <p>
      {{
        accountRole === 'CHECKER'
          ? '每场至少一名检票员，也可以配置多名；一个检票账号当前只绑定一场，不需要手机号。账号共用会互相挤下线，请为每名同时值守的工作人员创建独立账号。'
          : accountRole === 'ADMIN'
            ? '首个管理员由系统初始化，已有管理员仍可创建其他管理员。管理员不绑定手机号或检票场次。'
            : '这里仅列出当前类型账号。参与者与组织者分别注册和登录，同一个手机号可以拥有这两种身份；账号角色不切换。'
      }}
    </p>
  </div>
  <div v-if="section === 'recoveries'" class="alert info" style="margin-bottom: 22px">
    <AppIcon name="HelpCircle" />
    <p>
      信息来源：登录页 → 忘记密码 → 填写用户名和登记手机号 →
      提交申请。工作人员通过当面核对或已有联系渠道确认本人身份，在本页记录核验依据，再设置临时密码并自行告知本人。系统不会自动核实陈述。普通用户优先通过手机号验证码重置密码；人工审核通过后可在本页直接换绑并重置，通知只模拟输出在后端LOCAL_RECOVERY_SMS日志，不真正发送短信。
    </p>
  </div>
  <div v-if="section === 'audit'" class="alert info" style="margin-bottom: 22px">
    <AppIcon name="FileText" />
    <p>
      这是“谁在什么时候对哪个对象做了什么”的操作记录，例如审核活动、重置密码和检票，用于排查误操作与核对责任。管理员只能查询，不在这里修改业务数据。
    </p>
  </div>
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
      这里查看系统自动工作的进度，例如取消超时未支付订单、递补候补、处理活动退款和结算。进度保存在数据库，应用重启后可继续，不需要管理员逐单执行。超时释放、递补、自然结算目标1分钟；取消/下架结算目标5分钟，尚未完成故障恢复性能验收。
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
                : section === 'accounts'
                  ? '按当前类型的用户名搜索'
                  : '在当前页搜索名称、状态或编号'
          "
          aria-label="筛选管理记录"
        />
      </div>
      <button
        v-if="['payments', 'audit', 'accounts'].includes(section)"
        class="btn secondary small"
      >
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
            <th>登记手机号</th>
            <th>可用状态</th>
            <th v-if="accountRole === 'CHECKER'">授权活动与场次</th>
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
              {{
                row.maskedPhone || (['ADMIN', 'CHECKER'].includes(accountRole) ? '无需绑定' : '—')
              }}
            </td>
            <td>
              <StatusBadge
                :status="row.enabled && !row.deletedAt ? 'VALID' : 'INVALID'"
                :text="row.deletedAt ? '已注销/删除' : row.enabled ? '可用' : '停用'"
              />
            </td>
            <td v-if="accountRole === 'CHECKER'">
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
              <span v-else class="muted" style="font-size: 10px">{{
                row.deletedAt ? '历史账号只读' : '自行注册；密码问题通过账号找回申请处理'
              }}</span>
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
            <th>原手机号 / 当前手机号</th>
            <th>状态</th>
            <th>受理时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="id(row)">
            <td>{{ s(row, 'account') }}</td>
            <td>
              {{ s(row, 'phone')
              }}<small class="secondary-text">当前：{{ s(row, 'currentPhone') }}</small>
            </td>
            <td>{{ s(row, 'status') }}</td>
            <td>{{ dateTime(s(row, 'createdAt')) }}</td>
            <td>
              <button class="text-link" @click="open('recoveryDetails', row)">查看申诉资料</button
              ><button
                v-if="row.status === 'PENDING'"
                class="text-link"
                @click="open('recovery', row)"
              >
                审核并处理</button
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
    :wide="['detail', 'investigate', 'recovery', 'recoveryDetails'].includes(modal)"
    @close="modal = ''"
  >
    <pre v-if="modal === 'detail'" class="data-inspector">{{
      JSON.stringify(detail, null, 2)
    }}</pre>
    <RecoveryDetails v-else-if="modal === 'recoveryDetails' && selected" :recovery="selected" />
    <form v-else @submit.prevent="submit">
      <template v-if="modal === 'account'"
        ><div class="form-field">
          <label>账号类型</label>
          <p>{{ roleNames[form.role as Role] }} · 无需手机号</p>
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
          <label for="new-account-event">分配活动</label
          ><select
            id="new-account-event"
            v-model="form.eventId"
            required
            @change="form.sessionId = ''"
          >
            <option value="" disabled>选择待审核或未结束的已发布活动</option>
            <option v-for="event in configurableEvents" :key="event.id" :value="event.id">
              {{ event.name }}
            </option>
          </select>
        </div>
        <div v-if="form.role === 'CHECKER'" class="form-field">
          <label for="new-account-session">授权场次</label
          ><select id="new-account-session" v-model="form.sessionId" required>
            <option value="" disabled>选择已存在场次</option>
            <option v-for="session in selectableSessions" :key="session.id" :value="session.id">
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
      ><template v-if="modal === 'recovery'">
        <RecoveryDetails v-if="selected" :recovery="selected" />
        <div class="form-field">
          <label for="recovery-decision">核验结论</label
          ><select id="recovery-decision" v-model="form.decision">
            <option value="VERIFIED">审核通过，换绑并重置密码</option>
            <option value="REJECTED">核验未通过</option>
          </select>
        </div>
        <div class="form-field">
          <label for="recovery-note">核验方式、核对依据与结论</label
          ><textarea
            id="recovery-note"
            v-model="form.verificationNote"
            required
            minlength="10"
            maxlength="2000"
            placeholder="记录核对了哪些资料、为什么能够确认或不能确认申请人为本人。"
          />
        </div>
        <div v-if="form.decision === 'VERIFIED'" class="form-field">
          <label for="approve-temporary-password">新的临时密码</label
          ><input
            id="approve-temporary-password"
            v-model="form.initialPassword"
            required
            minlength="8"
            maxlength="72"
            pattern="(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}"
            autocomplete="new-password"
          /><small
            >通过后换绑申请人已验证的当前手机号，再模拟短信下发账号和临时密码。日志标记LOCAL_RECOVERY_SMS。</small
          >
        </div>
      </template>
      <template v-if="modal === 'reset'">
        <p class="field-help">
          仅使用这条已审核通过的申请；换绑其已验证的当前手机号、重置密码并模拟短信通知，不凭手机号直接改密。
        </p>
        <div class="form-field">
          <label for="reset-password">临时密码</label
          ><input id="reset-password" v-model="form.initialPassword" required minlength="8" />
        </div>
        <div class="form-field">
          <label for="reset-reason">操作理由</label
          ><input id="reset-reason" v-model="form.reason" required />
        </div>
      </template>
      <template v-if="modal === 'binding'"
        ><div class="form-field">
          <label for="assignment">新的授权场次</label
          ><select id="assignment" v-model="form.sessionId" required>
            <option v-for="session in selectableSessions" :key="session.id" :value="session.id">
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
