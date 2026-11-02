<script setup lang="ts">
import { computed, onMounted, ref, toRaw } from 'vue';
import { useRoute } from 'vue-router';
import { get, patch, post } from '../api/client';
import type { CampusEvent, LiveCounts, Mutation } from '../types';
import { useAppStore } from '../stores/app';
import { useTask } from '../composables/useTask';
import { dateTime, money } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import StatePanel from '../components/StatePanel.vue';
import PosterArt from '../components/PosterArt.vue';
import StatusBadge from '../components/StatusBadge.vue';
import AppIcon from '../components/AppIcon.vue';
import AppModal from '../components/AppModal.vue';
const route = useRoute();
const app = useAppStore();
const task = useTask();
const event = ref<CampusEvent>();
const counts = ref<LiveCounts>();
const loading = ref(true);
const error = ref('');
const modal = ref<'settings' | 'cancel' | 'reject' | null>(null);
const editable = ref<CampusEvent>();
const reason = ref('');
const admin = computed(() => app.user?.role === 'ADMIN');
const prefix = computed(() => (admin.value ? '/admin' : '/organizer'));
async function load() {
  loading.value = true;
  error.value = '';
  try {
    event.value = await get<CampusEvent>(`${prefix.value}/events/${route.params.eventId}`);
    if (event.value.status === 'PUBLISHED') {
      try {
        counts.value = await get<LiveCounts>(`/management/events/${event.value.id}/live-counts`);
      } catch {
        counts.value = undefined;
      }
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法读取活动';
  } finally {
    loading.value = false;
  }
}
function settings() {
  editable.value = structuredClone(toRaw(event.value!));
  modal.value = 'settings';
}
async function saveSettings() {
  await task.run(async () => {
    const e = editable.value!;
    const result = await patch<Mutation<CampusEvent>>(
      `/organizer/events/${e.id}/published-settings`,
      {
        expectedVersion: event.value!.version,
        description: e.description,
        sessions: e.sessions.map((s) => ({
          id: s.id,
          location: s.location,
          capacity: s.capacity,
          ticketTypes: s.ticketTypes.map((t) => ({
            id: t.id,
            capacity: t.capacity,
            saleStopped: t.saleStopped,
          })),
        })),
      },
    );
    event.value = result.current;
    modal.value = null;
    await load();
  }, '活动设置已更新');
}
async function submitAction(action: string) {
  await task.run(async () => {
    const e = event.value!;
    const result = await post<{ current: CampusEvent; outcome?: string; reason?: string }>(
      `${prefix.value}/events/${e.id}/${action}`,
      { expectedVersion: e.version, ...(reason.value ? { reason: reason.value } : {}) },
    );
    event.value = result.current;
    modal.value = null;
    app.toast(
      result.outcome === 'RETURNED_TO_DRAFT' ? `已退回草稿：${result.reason}` : '操作已完成',
    );
    await load();
  });
}
onMounted(load);
</script>
<template>
  <RouterLink :to="`${prefix}/events`" class="back-link"
    ><AppIcon name="ArrowLeft" :size="14" />返回活动列表</RouterLink
  ><StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" /><template
    v-else-if="event"
    ><PageHeading
      eyebrow="EVERY DETAIL MAKES A DIFFERENCE"
      :title="admin ? '活动审核与管理' : '活动管理'"
      description="活动生命周期、最新配置和业务入口。"
      ><StatusBadge :status="event.status" /><button class="btn secondary small" @click="load">
        <AppIcon name="RefreshCw" :size="14" />刷新
      </button></PageHeading
    >
    <section class="panel">
      <div class="managed-event-summary">
        <PosterArt
          :title="event.name"
          :src="event.posterUrl"
          :category="event.categoryNameSnapshot"
          :variant="
            ['音乐演出', '艺术展览', '戏剧现场', '讲座沙龙', '户外生活', '工作坊'].indexOf(
              event.categoryNameSnapshot,
            )
          "
        />
        <div>
          <p class="eyebrow">YOUR EVENT</p>
          <h2>{{ event.name || '未命名草稿' }}</h2>
          <div class="detail-tags">
            <span class="tag">{{ event.categoryNameSnapshot || '类别待完善' }}</span
            ><span class="tag">{{ event.sessions.length }}个场次</span>
          </div>
          <p class="field-help">
            活动编号 <span class="mono">{{ event.id }}</span>
          </p>
          <p v-if="event.lastReviewReason" class="field-help rose-text">
            上次审核：{{ event.lastReviewReason }}
          </p>
        </div>
      </div>
      <div class="divider" />
      <p class="content-prose">{{ event.description || '尚未填写活动简介。' }}</p>
    </section>
    <div v-if="event.status === 'PUBLISHED' && counts" class="stat-grid" style="margin-top: 23px">
      <div
        v-for="item in [
          ['当前占位', counts.occupied],
          ['可分配余票', counts.remaining],
          ['当前候补', counts.currentWaitlist],
          ['已签到', counts.checkedIn],
        ]"
        :key="String(item[0])"
        class="stat-card"
      >
        <div class="stat-label">{{ item[0] }}</div>
        <div class="stat-number">{{ item[1] }}</div>
        <div class="stat-foot">已提交业务数据</div>
      </div>
    </div>
    <div v-if="event.status !== 'DRAFT'" class="manage-nav">
      <RouterLink :to="`${prefix}/events/${event.id}/attendees`"
        ><span><AppIcon name="UsersRound" :size="17" /> 活动名单与导出</span
        ><AppIcon name="ArrowUpRight" :size="16" /></RouterLink
      ><RouterLink :to="`${prefix}/events/${event.id}/statistics`"
        ><span><AppIcon name="ChartNoAxesCombined" :size="17" /> 统计与实时计数</span
        ><AppIcon name="ArrowUpRight" :size="16" /></RouterLink
      ><RouterLink v-if="admin" :to="`/admin/accounts?eventId=${event.id}`"
        ><span><AppIcon name="ShieldCheck" :size="17" /> 配置场次检票账号</span
        ><AppIcon name="ArrowUpRight" :size="16" /></RouterLink
      ><button
        v-else
        class="btn secondary"
        @click="settings"
        :disabled="event.status !== 'PUBLISHED'"
      >
        <AppIcon name="Settings2" />发布设置
      </button>
    </div>
    <section class="panel">
      <div class="panel-heading">
        <h2>场次与票种</h2>
        <span class="tag">名称、时间与价格发布后固定</span>
      </div>
      <StatePanel
        v-if="!event.sessions.length"
        title="尚未添加场次"
        description="返回草稿编辑，补充活动结构。"
      />
      <div v-for="s in event.sessions" :key="s.id" style="margin-bottom: 24px">
        <h3 style="font-size: 15px; margin-bottom: 10px">{{ s.name }}</h3>
        <p class="field-help">
          <AppIcon name="MapPin" :size="13" /> {{ s.location }} · {{ dateTime(s.startsAt) }} —
          {{ dateTime(s.endsAt) }}
        </p>
        <p class="field-help" style="margin-bottom: 15px">
          放票 {{ dateTime(s.salesOpensAt) }} / 截止 {{ dateTime(s.registrationClosesAt) }} /
          场次容量 {{ s.capacity }}
        </p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>票种</th>
                <th>价格</th>
                <th>配置容量</th>
                <th>停售设置</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in s.ticketTypes" :key="t.id">
                <td>{{ t.name }}</td>
                <td>¥{{ money(t.priceJiao) }}</td>
                <td>{{ t.capacity }}</td>
                <td>
                  <StatusBadge
                    :status="t.saleStopped ? 'STOPPED' : 'ON_SALE'"
                    :text="t.saleStopped ? '人工停售' : '未设置停售'"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
    <div v-if="admin && event.status === 'PENDING_REVIEW'" class="panel" style="margin-top: 22px">
      <h2 style="font-size: 17px; margin-bottom: 13px">审核结论</h2>
      <div class="alert info">
        <AppIcon name="ShieldCheck" />
        <p>
          通过即发布；发布时将重新检查类别、时间、排期、额度和每场检票账号。条件失效会说明原因退回草稿。
        </p>
      </div>
      <div class="button-row" style="margin-top: 20px">
        <button
          class="btn primary"
          :disabled="task.busy.value"
          @click="
            reason = '';
            submitAction('approval');
          "
        >
          <AppIcon name="Check" />审核通过并发布</button
        ><button
          class="btn secondary"
          @click="
            modal = 'reject';
            reason = '';
          "
        >
          驳回并说明原因</button
        ><RouterLink :to="`/admin/accounts?eventId=${event.id}`" class="text-link"
          >配置检票员<AppIcon name="ArrowUpRight" :size="14"
        /></RouterLink>
      </div>
    </div>
    <div v-else-if="!admin" class="button-row" style="margin-top: 23px">
      <RouterLink
        v-if="event.status === 'DRAFT'"
        :to="`/organizer/events/${event.id}/edit`"
        class="btn primary"
        >继续编辑草稿<AppIcon name="ArrowRight" :size="15" /></RouterLink
      ><button
        v-if="event.status === 'PENDING_REVIEW'"
        class="btn secondary"
        :disabled="task.busy.value"
        @click="submitAction('withdrawal')"
      >
        撤回审核</button
      ><template v-if="event.status === 'PUBLISHED'"
        ><button class="btn primary" @click="settings">
          <AppIcon name="Settings2" />编辑允许的设置</button
        ><button
          class="btn danger"
          @click="
            modal = 'cancel';
            reason = '';
          "
        >
          取消整个活动
        </button></template
      >
    </div>
    <div v-if="event.terminationReason" class="alert warning" style="margin-top: 22px">
      <AppIcon name="CircleAlert" />
      <p>
        终止原因：{{ event.terminationReason }}。当前结算：{{
          event.settlementStatus === 'COMPLETED' ? '已完成（本地演示同步处理）' : '处理中'
        }}；活动终态不可恢复。
      </p>
    </div>
    <p v-if="task.error.value" class="error-inline">{{ task.error.value }}</p></template
  ><AppModal :open="modal === 'settings'" title="发布后允许的设置" wide @close="modal = null"
    ><form v-if="editable" @submit.prevent="saveSettings">
      <div class="form-field">
        <label for="settings-description">活动简介</label
        ><textarea id="settings-description" v-model="editable.description" maxlength="500" />
      </div>
      <div v-for="s in editable.sessions" :key="s.id">
        <h3 style="font-size: 14px; margin: 20px 0 13px">{{ s.name }}</h3>
        <div class="form-grid">
          <div class="form-field">
            <label>地点</label
            ><input v-model="s.location" aria-label="场次地点" maxlength="60" required />
          </div>
          <div class="form-field">
            <label>场次容量</label
            ><input
              v-model.number="s.capacity"
              aria-label="场次容量"
              type="number"
              min="1"
              max="9999"
            />
          </div>
        </div>
        <div v-for="t in s.ticketTypes" :key="t.id" class="ticket-type-row">
          <span>{{ t.name }}</span
          ><input
            v-model.number="t.capacity"
            type="number"
            min="1"
            max="9999"
            :aria-label="`${t.name}容量`"
          /><label class="checkbox-label"
            ><input v-model="t.saleStopped" type="checkbox" />停售</label
          ><span />
        </div>
      </div>
      <p class="field-help">
        缩容不能低于已占名额；扩容时优先递补合格候补。地点、容量变更通知受影响用户。
      </p>
      <div class="button-row">
        <button class="btn secondary" type="button" @click="modal = null">取消</button
        ><button class="btn primary" :disabled="task.busy.value">保存设置</button>
      </div>
    </form></AppModal
  ><AppModal
    :open="modal === 'cancel' || modal === 'reject'"
    :title="modal === 'cancel' ? '确认取消整个活动' : '驳回审核'"
    @close="modal = null"
    ><form @submit.prevent="submitAction(modal === 'cancel' ? 'cancellation' : 'rejection')">
      <p class="field-help">
        {{
          modal === 'cancel'
            ? '仅首场开始前且全活动没有签到时允许取消。生效后立即拒绝新交易与验票，并处理存量订单。终态不可恢复。'
            : '请填写组织者可见的明确理由，活动将返回草稿。'
        }}
      </p>
      <div class="form-field" style="margin-top: 20px">
        <label for="review-reason">操作原因</label
        ><textarea id="review-reason" v-model="reason" required maxlength="200" />
      </div>
      <div class="button-row">
        <button class="btn secondary" type="button" @click="modal = null">暂不操作</button
        ><button class="btn primary" :disabled="task.busy.value">
          确认{{ modal === 'cancel' ? '取消活动' : '驳回' }}
        </button>
      </div>
    </form></AppModal
  >
</template>
