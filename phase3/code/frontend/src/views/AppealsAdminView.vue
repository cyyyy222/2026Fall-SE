<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { get, post, query } from '../api/client';
import type { Page, RestrictionAppeal } from '../types';
import { dateTime } from '../utils/format';
import { useTask } from '../composables/useTask';
import PageHeading from '../components/PageHeading.vue';
import AppIcon from '../components/AppIcon.vue';
import AppModal from '../components/AppModal.vue';
import StatePanel from '../components/StatePanel.vue';
import StatusBadge from '../components/StatusBadge.vue';

const task = useTask();
const rows = ref<RestrictionAppeal[]>([]);
const status = ref('');
const page = ref(1);
const total = ref(0);
const loading = ref(true);
const error = ref('');
const selected = ref<RestrictionAppeal>();
const decision = ref<'APPROVED' | 'REJECTED'>('APPROVED');
const note = ref('');
const open = ref(false);
const pageSize = 20;

const tabs = computed(() => [
  ['', '全部'],
  ['PENDING', '待审核'],
  ['APPROVED', '已通过'],
  ['REJECTED', '未通过'],
]);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const result = await get<Page<RestrictionAppeal>>(
      `/admin/restriction-appeals${query({ status: status.value, page: page.value, pageSize })}`,
    );
    rows.value = result.items;
    total.value = result.total;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法读取申诉';
  } finally {
    loading.value = false;
  }
}
function begin(row: RestrictionAppeal) {
  selected.value = row;
  decision.value = 'APPROVED';
  note.value = '';
  open.value = true;
}
async function resolve() {
  await task.run(
    async () => {
      await post(`/admin/restriction-appeals/${selected.value!.id}/resolution`, {
        decision: decision.value,
        note: note.value,
      });
      open.value = false;
      await load();
    },
    decision.value === 'APPROVED' ? '申诉已通过，限制已解除' : '申诉已驳回',
  );
}
function changeStatus(value: string) {
  status.value = value;
  page.value = 1;
  load();
}
onMounted(load);
</script>
<template>
  <PageHeading
    eyebrow="REVIEW WITH EVIDENCE"
    title="处罚申诉"
    description="核对参与者的缺席记录、申诉理由和补充证据；通过后立即解除 15 天限制。"
  >
    <button class="btn secondary small" @click="load">
      <AppIcon name="RefreshCw" :size="14" />刷新
    </button>
  </PageHeading>
  <div class="alert info" style="margin-bottom: 22px">
    <AppIcon name="ShieldCheck" />
    <p>
      每周一 00:00 自动扫描上一周；3 次正常缺席生成 15 天报名限制，2
      次时发送预警。管理员可结合申诉证据提前解除限制，解除后仍保留事实记录。
    </p>
  </div>
  <section class="panel">
    <div class="tabs">
      <button
        v-for="item in tabs"
        :key="item[0]"
        :class="{ active: status === item[0] }"
        @click="changeStatus(item[0]!)"
      >
        {{ item[1] }}
      </button>
    </div>
    <StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" />
    <StatePanel
      v-else-if="!rows.length"
      title="没有匹配的申诉"
      description="新的处罚申诉会在这里显示。"
      icon="HelpCircle"
    />
    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>账号</th>
            <th>限制原因 / 期限</th>
            <th>申诉理由</th>
            <th>提交时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>
              {{ row.username }}
              <span class="secondary-text">{{
                row.restriction.kind === 'REGISTER' ? '报名限制' : '发布限制'
              }}</span>
            </td>
            <td>
              {{ row.restriction.reason }}
              <span class="secondary-text">
                {{ dateTime(row.restriction.startsAt) }} — {{ dateTime(row.restriction.endsAt) }}
              </span>
            </td>
            <td style="max-width: 320px; white-space: normal">
              {{ row.reason }}
              <span v-if="row.evidenceText" class="secondary-text">{{ row.evidenceText }}</span>
            </td>
            <td>{{ dateTime(row.createdAt) }}</td>
            <td><StatusBadge :status="row.status" /></td>
            <td>
              <button v-if="row.status === 'PENDING'" class="text-link" @click="begin(row)">
                审核处理
              </button>
              <span v-else class="muted">{{ row.decisionNote || '已处理' }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pagination">
      <span>共 {{ total }} 条 · 每页 {{ pageSize }} 条</span>
      <div class="page-buttons">
        <button
          :disabled="page <= 1"
          aria-label="上一页"
          @click="
            page--;
            load();
          "
        >
          <AppIcon name="ChevronLeft" :size="13" />
        </button>
        <span class="current">{{ page }}</span>
        <button
          :disabled="page * pageSize >= total"
          aria-label="下一页"
          @click="
            page++;
            load();
          "
        >
          <AppIcon name="ChevronRight" :size="13" />
        </button>
      </div>
    </div>
  </section>

  <AppModal :open="open" title="审核处罚申诉" wide @close="open = false">
    <div v-if="selected" class="alert info">
      <AppIcon name="FileText" />
      <p>
        账号 {{ selected.username }} · {{ selected.restriction.reason }}<br />
        申诉理由：{{ selected.reason
        }}<template v-if="selected.evidenceText"
          ><br />补充证据：{{ selected.evidenceText }}</template
        >
      </p>
    </div>
    <form @submit.prevent="resolve">
      <div class="form-field" style="margin-top: 18px">
        <label for="appeal-decision">审核结论</label>
        <select id="appeal-decision" v-model="decision">
          <option value="APPROVED">通过，立即解除 15 天限制</option>
          <option value="REJECTED">驳回，维持原限制</option>
        </select>
      </div>
      <div class="form-field">
        <label for="appeal-note">审核说明（2～1000字）</label>
        <textarea
          id="appeal-note"
          v-model="note"
          required
          minlength="2"
          maxlength="1000"
          rows="4"
          placeholder="记录核对了哪些缺席记录、理由和证据，以及作出结论的依据"
        />
      </div>
      <p v-if="task.error.value" class="error-inline" role="alert">{{ task.error.value }}</p>
      <div class="button-row">
        <button type="button" class="btn secondary" @click="open = false">取消</button>
        <button class="btn primary" :disabled="task.busy.value">提交审核结论</button>
      </div>
    </form>
  </AppModal>
</template>
