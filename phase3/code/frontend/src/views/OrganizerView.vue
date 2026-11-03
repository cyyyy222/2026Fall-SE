<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, post } from '../api/client';
import type { CampusEvent, Page } from '../types';
import { dateTime } from '../utils/format';
import { useTask } from '../composables/useTask';
import { useAppStore } from '../stores/app';
import PageHeading from '../components/PageHeading.vue';
import StatePanel from '../components/StatePanel.vue';
import StatusBadge from '../components/StatusBadge.vue';
import AppIcon from '../components/AppIcon.vue';
const route = useRoute();
const router = useRouter();
const app = useAppStore();
const task = useTask();
const events = ref<CampusEvent[]>([]);
const loading = ref(true);
const creating = ref(false);
const error = ref('');
const status = ref('');
const panel = computed(() => (typeof route.query.panel === 'string' ? route.query.panel : ''));
const filtered = computed(() =>
  events.value.filter(
    (e) =>
      !status.value ||
      (status.value === 'TERMINAL'
        ? ['CANCELLED', 'TAKEN_DOWN', 'ENDED'].includes(e.status || '')
        : e.status === status.value),
  ),
);
const stat = (s: string) => events.value.filter((e) => e.status === s).length;
async function load() {
  loading.value = true;
  error.value = '';
  try {
    events.value = (await get<Page<CampusEvent>>('/organizer/events?pageSize=100')).items;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法读取活动';
  } finally {
    loading.value = false;
  }
}
async function withdraw(e: CampusEvent) {
  await task.run(async () => {
    await post(`/organizer/events/${e.id}/withdrawal`, { expectedVersion: e.version });
    await load();
  }, '已撤回审核，活动返回草稿');
}
async function createEvent() {
  creating.value = true;
  try {
    const created = await post<CampusEvent>('/organizer/events');
    await router.push(`/organizer/events/${created.id}/edit`);
  } catch (e) {
    app.error(e);
  } finally {
    creating.value = false;
  }
}
onMounted(load);
</script>
<template>
  <PageHeading
    eyebrow="MAKE SOMETHING WORTH GATHERING FOR"
    :title="
      panel === 'attendees'
        ? '选择活动，查看名单'
        : panel === 'statistics'
          ? '选择活动，查看统计'
          : '活动工作台'
    "
    description="从一份草稿，到一次完整的相聚。"
    ><button class="btn primary" :disabled="creating" @click="createEvent">
      <AppIcon name="Plus" :size="16" />{{ creating ? '正在创建…' : '新建活动' }}
    </button></PageHeading
  >
  <div v-if="!panel" class="stat-grid">
    <div class="stat-card">
      <div class="stat-label">已发布<AppIcon name="CalendarDays" :size="16" /></div>
      <div class="stat-number">{{ String(stat('PUBLISHED')).padStart(2, '0') }}</div>
      <div class="stat-foot">正在发生的期待</div>
    </div>
    <div class="stat-card rose">
      <div class="stat-label">待审核<AppIcon name="Clock3" :size="16" /></div>
      <div class="stat-number">{{ String(stat('PENDING_REVIEW')).padStart(2, '0') }}</div>
      <div class="stat-foot">审核期间不可编辑</div>
    </div>
    <div class="stat-card">
      <div class="stat-label">草稿<AppIcon name="FileText" :size="16" /></div>
      <div class="stat-number">{{ String(stat('DRAFT')).padStart(2, '0') }}</div>
      <div class="stat-foot">继续打磨你的想法</div>
    </div>
    <div class="stat-card soft">
      <div class="stat-label">全部活动<AppIcon name="Leaf" :size="16" /></div>
      <div class="stat-number">{{ String(events.length).padStart(2, '0') }}</div>
      <div class="stat-foot">每一场相聚都有记录</div>
    </div>
  </div>
  <section class="panel">
    <div class="panel-heading">
      <h2>我的活动<span>仅展示当前账号创建的活动</span></h2>
      <button class="icon-btn" aria-label="刷新活动" @click="load">
        <AppIcon name="RefreshCw" :size="16" />
      </button>
    </div>
    <div class="tabs">
      <button
        v-for="item in [
          ['', '全部活动'],
          ['DRAFT', '草稿'],
          ['PENDING_REVIEW', '待审核'],
          ['PUBLISHED', '已发布'],
          ['TERMINAL', '已终结'],
        ]"
        :key="item[0]"
        :class="{ active: status === item[0] }"
        @click="status = item[0]!"
      >
        {{ item[1] }}
      </button>
    </div>
    <StatePanel
      v-if="loading || error"
      :loading="loading"
      :error="error"
      @retry="load"
    /><StatePanel
      v-else-if="!filtered.length"
      title="还没有这一类活动"
      description="从一份草稿开始，把想法变成相聚。"
    />
    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>活动名称</th>
            <th>状态</th>
            <th>场次 / 票种</th>
            <th>计划开始</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="e in filtered" :key="e.id">
            <td class="table-title">
              {{ e.name || '未命名草稿'
              }}<small>{{ e.categoryNameSnapshot || '未选择类别' }}</small>
            </td>
            <td><StatusBadge :status="e.status" /></td>
            <td>
              {{ e.sessions.length }}场 /
              {{ e.sessions.reduce((n, s) => n + s.ticketTypes.length, 0) }}票种
            </td>
            <td>{{ dateTime(e.sessions[0]?.startsAt) }}</td>
            <td>
              <div class="action-group">
                <RouterLink
                  v-if="panel"
                  :to="`/organizer/events/${e.id}/${panel}`"
                  class="text-link"
                  >{{ panel === 'attendees' ? '查看名单' : '查看统计'
                  }}<AppIcon name="ArrowUpRight" :size="12" /></RouterLink
                ><template v-else
                  ><RouterLink
                    :to="`/organizer/events/${e.id}${e.status === 'DRAFT' ? '/edit' : ''}`"
                    class="text-link"
                    >{{ e.status === 'DRAFT' ? '继续编辑' : '管理活动' }}</RouterLink
                  ><button
                    v-if="e.status === 'PENDING_REVIEW'"
                    class="text-link"
                    :disabled="task.busy.value"
                    @click="withdraw(e)"
                  >
                    撤回审核
                  </button></template
                >
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pagination">
      <span>共 {{ filtered.length }} 个活动</span><span>固定角色 · 活动归属校验</span>
    </div>
  </section>
  <div class="alert info" style="margin-top: 23px">
    <AppIcon name="HelpCircle" />
    <p>
      审核通过后活动立即发布。发布后只能修改简介、海报，以及未结束场次的地点、容量和停售设置；名称、类别、时间和票价不能再改。
    </p>
  </div>
</template>
