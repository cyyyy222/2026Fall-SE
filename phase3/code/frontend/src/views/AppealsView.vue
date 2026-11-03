<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { get, post } from '../api/client';
import type { Restriction, RestrictionAppeal } from '../types';
import { dateTime } from '../utils/format';
import { useTask } from '../composables/useTask';
import PageHeading from '../components/PageHeading.vue';
import AppIcon from '../components/AppIcon.vue';
import AppModal from '../components/AppModal.vue';
import StatePanel from '../components/StatePanel.vue';
import StatusBadge from '../components/StatusBadge.vue';

const task = useTask();
const restrictions = ref<Restriction[]>([]);
const appeals = ref<RestrictionAppeal[]>([]);
const loading = ref(true);
const error = ref('');
const open = ref(false);
const selected = ref<Restriction>();
const form = reactive({ reason: '', evidenceText: '' });

const active = computed(() => restrictions.value.filter((item) => item.active));
const selectedAppeals = computed(() =>
  selected.value ? appeals.value.filter((item) => item.restrictionId === selected.value!.id) : [],
);
const hasPending = computed(() => selectedAppeals.value.some((item) => item.status === 'PENDING'));

async function load() {
  loading.value = true;
  error.value = '';
  try {
    [restrictions.value, appeals.value] = await Promise.all([
      get<Restriction[]>('/me/restrictions'),
      get<RestrictionAppeal[]>('/me/restriction-appeals'),
    ]);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法读取申诉信息';
  } finally {
    loading.value = false;
  }
}
function begin(restriction: Restriction) {
  selected.value = restriction;
  form.reason = '';
  form.evidenceText = '';
  open.value = true;
}
async function submit() {
  await task.run(async () => {
    await post('/me/restriction-appeals', {
      restrictionId: selected.value!.id,
      reason: form.reason,
      evidenceText: form.evidenceText || null,
    });
    open.value = false;
    await load();
  }, '申诉已提交，请等待管理员审核');
}
const sourceName = (source: string | null) =>
  source === 'WEEKLY_ABSENCE' ? '每周缺席自动限制' : '管理员核实限制';
onMounted(load);
</script>
<template>
  <PageHeading
    eyebrow="A FAIR CHANCE TO EXPLAIN"
    title="我的申诉"
    description="对 15 天报名限制有异议时，可以提交理由和证据，由管理员人工审核。"
  />
  <div class="alert info" style="margin-bottom: 24px">
    <AppIcon name="ShieldCheck" />
    <p>
      一周内缺席达到 3 次会触发 15 天报名限制；第 2
      次缺席时会收到预警。限制期间仍可登录、查看订单和电子票，也可以申诉；审核通过后限制立即解除。同一限制可以补充证据再次申诉。
    </p>
  </div>
  <StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" />
  <template v-else>
    <StatePanel
      v-if="!active.length"
      title="当前没有需要申诉的限制"
      description="如果之后触发缺席限制，可以在这里提交申诉。"
      icon="ShieldCheck"
    />
    <section v-for="item in active" :key="item.id" class="panel restriction-card">
      <div class="restriction-card-head">
        <div>
          <p class="eyebrow">ACTIVE RESTRICTION</p>
          <h2>{{ sourceName(item.source) }}</h2>
        </div>
        <span class="badge rose">生效中</span>
      </div>
      <div class="info-list">
        <div class="info-row">
          <span>限制原因</span><strong>{{ item.reason }}</strong>
        </div>
        <div class="info-row">
          <span>生效时间</span><strong>{{ dateTime(item.startsAt) }}</strong>
        </div>
        <div class="info-row">
          <span>结束时间</span><strong>{{ dateTime(item.endsAt) }}</strong>
        </div>
      </div>
      <div class="button-row" style="margin-top: 20px">
        <button class="btn primary" :disabled="hasPending" @click="begin(item)">
          {{ hasPending ? '申诉审核中' : '提交申诉' }}
        </button>
        <span v-if="hasPending" class="field-help">已有待审核申诉，请等待管理员处理</span>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <h2>申诉记录<span>可补充证据再次提交</span></h2>
      </div>
      <StatePanel
        v-if="!appeals.length"
        title="还没有申诉记录"
        description="限制生效期间可以提交理由和证据。"
        icon="FileText"
        compact
      />
      <div v-else class="appeal-list">
        <article v-for="item in appeals" :key="item.id" class="appeal-item">
          <div class="appeal-item-top">
            <strong>{{ dateTime(item.createdAt) }}</strong>
            <StatusBadge :status="item.status" />
          </div>
          <p class="content-prose">{{ item.reason }}</p>
          <p v-if="item.evidenceText" class="field-help">证据：{{ item.evidenceText }}</p>
          <p v-if="item.reviewedAt" class="field-help">
            审核时间：{{ dateTime(item.reviewedAt) }}
            <template v-if="item.decisionNote"> · 结论：{{ item.decisionNote }}</template>
          </p>
        </article>
      </div>
    </section>
  </template>

  <AppModal :open="open" title="提交账号申诉" wide @close="open = false">
    <form @submit.prevent="submit">
      <div class="alert info">
        <AppIcon name="CircleAlert" />
        <p>
          请说明正当理由，例如就医、考试、突发情况等，并尽量提供可核对的文字信息。管理员会结合缺席记录人工判断。
        </p>
      </div>
      <div class="form-field" style="margin-top: 18px">
        <label for="appeal-reason">申诉理由（10～1000字）</label>
        <textarea
          id="appeal-reason"
          v-model="form.reason"
          required
          minlength="10"
          maxlength="1000"
          rows="4"
          placeholder="请具体说明缺席原因和希望管理员核对的情况"
        />
      </div>
      <div class="form-field">
        <label for="appeal-evidence">补充证据（选填，最多2000字）</label>
        <textarea
          id="appeal-evidence"
          v-model="form.evidenceText"
          maxlength="2000"
          rows="3"
          placeholder="可填写证明人、联系渠道、时间线或其他可核对的文字信息；暂不支持上传附件"
        />
      </div>
      <p v-if="task.error.value" class="error-inline" role="alert">{{ task.error.value }}</p>
      <div class="button-row">
        <button type="button" class="btn secondary" @click="open = false">取消</button>
        <button class="btn primary" :disabled="task.busy.value">提交申诉</button>
      </div>
    </form>
  </AppModal>
</template>
