<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { get, post } from '../api/client';
import type { Page, Report, Session } from '../types';
import { dateTime } from '../utils/format';
import { useTask } from '../composables/useTask';
import PageHeading from '../components/PageHeading.vue';
import AppModal from '../components/AppModal.vue';
import StatusBadge from '../components/StatusBadge.vue';
import StatePanel from '../components/StatePanel.vue';
import AppIcon from '../components/AppIcon.vue';
const route = useRoute();
const task = useTask();
const reports = ref<Report[]>([]);
const sessions = ref<Session[]>([]);
const loading = ref(true);
const error = ref('');
const show = ref(route.params.action === 'new');
const form = reactive({
  sessionId: String(route.query.sessionId || ''),
  reason: '',
  description: '',
  evidenceText: '',
});
async function load() {
  loading.value = true;
  error.value = '';
  try {
    reports.value = (await get<Page<Report>>('/me/reports?pageSize=100')).items;
    sessions.value = (await get<Page<Session>>('/public/sessions?pageSize=100')).items;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法读取举报';
  } finally {
    loading.value = false;
  }
}
async function submit() {
  await task.run(async () => {
    await post('/me/reports', form);
    show.value = false;
    await load();
  }, '举报已受理，可在此查看后续处理进度');
}
onMounted(load);
</script>
<template>
  <PageHeading
    eyebrow="A BETTER EXPERIENCE, TOGETHER"
    title="活动举报与处理进度"
    description="认真对待每一条反馈，也保护每一位反馈者的信息。"
    ><button class="btn primary" @click="show = true">
      <AppIcon name="Plus" :size="16" />提交举报
    </button></PageHeading
  >
  <div class="alert info" style="margin-bottom: 24px">
    <AppIcon name="ShieldCheck" />
    <p>
      同人同场最多一次，连续30天最多受理5次。驳回不返次数；支持文字证据或链接，不上传或自动抓取附件。
    </p>
  </div>
  <StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" /><StatePanel
    v-else-if="!reports.length"
    title="暂时没有举报记录"
    description="对活动信息有疑问，可以提交具体事实与文字依据。"
    icon="ShieldCheck"
  />
  <article v-for="report in reports" :key="report.id" class="report-card">
    <div class="report-card-top">
      <h3>{{ report.reason }}</h3>
      <StatusBadge :status="report.status" />
    </div>
    <p>{{ report.description }}</p>
    <div class="info-row">
      <span>证据说明</span><strong>{{ report.evidenceText }}</strong>
    </div>
    <div v-if="report.conclusion" class="alert" style="margin-top: 17px">
      <AppIcon name="CheckCircle2" />
      <p>处理结论：{{ report.conclusion }}</p>
    </div>
    <div class="divider" />
    <div class="info-row">
      <span>{{ dateTime(report.createdAt) }}</span
      ><span class="mono">{{ report.id }}</span>
    </div>
  </article>
  <AppModal :open="show" title="提交活动举报" @close="show = false"
    ><form @submit.prevent="submit">
      <div class="form-field">
        <label for="report-session">目标活动与场次</label
        ><select id="report-session" v-model="form.sessionId" required>
          <option disabled value="">请选择场次</option>
          <option v-for="s in sessions" :key="s.id" :value="s.id">
            {{ s.eventName }} · {{ s.name }}
          </option>
        </select>
      </div>
      <div class="form-field">
        <label for="report-reason">举报原因</label
        ><input
          id="report-reason"
          v-model="form.reason"
          required
          maxlength="200"
          placeholder="例如：活动信息与实际不符"
        />
      </div>
      <div class="form-field">
        <label for="report-description">具体事实</label
        ><textarea
          id="report-description"
          v-model="form.description"
          required
          maxlength="2000"
          placeholder="请清楚描述需要核实的事实"
        />
      </div>
      <div class="form-field">
        <label for="report-evidence">文字证据或链接</label
        ><textarea
          id="report-evidence"
          v-model="form.evidenceText"
          required
          maxlength="2000"
          placeholder="提供可追溯的信息依据，不上传附件"
        />
      </div>
      <p v-if="task.error.value" class="error-inline">{{ task.error.value }}</p>
      <div class="button-row">
        <button class="btn secondary" type="button" @click="show = false">取消</button
        ><button class="btn primary" :disabled="task.busy.value">提交举报</button>
      </div>
    </form></AppModal
  >
</template>
