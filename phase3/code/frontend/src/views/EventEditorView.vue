<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, post, put, uploadPoster } from '../api/client';
import type { CampusEvent, Category, Mutation, Session, TicketType } from '../types';
import { useTask } from '../composables/useTask';
import { uid } from '../utils/format';
import { useAppStore } from '../stores/app';
import PageHeading from '../components/PageHeading.vue';
import StatePanel from '../components/StatePanel.vue';
import StatusBadge from '../components/StatusBadge.vue';
import AppIcon from '../components/AppIcon.vue';
import AppModal from '../components/AppModal.vue';
const route = useRoute();
const router = useRouter();
const task = useTask();
const app = useAppStore();
const event = ref<CampusEvent>();
const categories = ref<Category[]>([]);
const loading = ref(true);
const error = ref('');
const poster = ref('');
const posterFile = ref<File>();
const rounding = ref<{ session: Session; field: 'startsAt' | 'endsAt'; value: string }>();
const count = computed(() => event.value?.description.length || 0);
const inputDate = (value: string) =>
  value ? new Date(Date.parse(value) + 8 * 3600000).toISOString().slice(0, 16) : '';
function setDate(
  session: Session,
  field: 'startsAt' | 'endsAt' | 'salesOpensAt' | 'registrationClosesAt',
  value: string,
) {
  if (!value) {
    session[field] = '';
    return;
  }
  if (['startsAt', 'endsAt'].includes(field) && value.slice(-2) !== '00') {
    rounding.value = {
      session,
      field: field as 'startsAt' | 'endsAt',
      value: value.slice(0, -2) + '00',
    };
    return;
  }
  session[field] = `${value}:00+08:00`;
}
function addType(session: Session) {
  if (session.ticketTypes.length >= 10) return;
  session.ticketTypes.push({
    id: uid(),
    name: '',
    capacity: 20,
    priceJiao: 0,
    saleStopped: false,
  } as TicketType);
}
function addSession() {
  if (!event.value || event.value.sessions.length >= 20) return;
  const start = new Date(Date.now() + 3 * 86400000);
  start.setUTCHours(11, 0, 0, 0);
  const s: Session = {
    id: uid(),
    eventId: event.value.id,
    eventName: event.value.name,
    categoryId: event.value.categoryId,
    categoryNameSnapshot: '',
    name: `第${event.value.sessions.length + 1}场`,
    startsAt: start.toISOString(),
    endsAt: new Date(start.getTime() + 7200000).toISOString(),
    salesOpensAt: new Date(Date.now() + 3600000).toISOString().slice(0, 16) + ':00Z',
    registrationClosesAt: new Date(start.getTime() - 3600000).toISOString(),
    location: '',
    capacity: 100,
    ticketTypes: [],
    remaining: 0,
    sessionSalesStatus: 'NOT_OPEN',
    posterUrl: null,
  };
  addType(s);
  event.value.sessions.push(s);
}
function upload(file?: File) {
  if (!file) return;
  if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5000000) {
    app.toast('请选择不超过5,000,000字节的JPEG或PNG图片', 'error');
    return;
  }
  posterFile.value = file;
  const reader = new FileReader();
  reader.onload = () => {
    poster.value = String(reader.result);
  };
  reader.readAsDataURL(file);
}
async function load() {
  loading.value = true;
  try {
    categories.value = await get<Category[]>('/public/categories?scope=EDIT');
    if (route.path.endsWith('/new')) {
      const created = await post<CampusEvent>('/organizer/events');
      await router.replace(`/organizer/events/${created.id}/edit`);
      return;
    }
    event.value = await get<CampusEvent>(`/organizer/events/${route.params.eventId}`);
    if (event.value.status !== 'DRAFT') throw new Error('此活动不在草稿状态，请返回工作台管理');
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法加载草稿';
  } finally {
    loading.value = false;
  }
}
async function save(submit = false) {
  await task.run(async () => {
    const e = event.value!;
    const sessions = e.sessions.map((s) => ({
      id: s.id,
      name: s.name || null,
      startsAt: s.startsAt || null,
      endsAt: s.endsAt || null,
      location: s.location || null,
      capacity: s.capacity || null,
      salesOpensAt: s.salesOpensAt || null,
      registrationClosesAt: s.registrationClosesAt || null,
      ticketTypes: s.ticketTypes.map((t) => ({
        id: t.id,
        name: t.name || null,
        capacity: t.capacity || null,
        priceJiao: t.priceJiao,
        saleStopped: t.saleStopped,
      })),
    }));
    const result = await put<Mutation<CampusEvent>>(`/organizer/events/${e.id}/draft`, {
      expectedVersion: e.version,
      name: e.name || null,
      categoryId: e.categoryId || null,
      description: e.description || null,
      sessions,
    });
    event.value = result.current;
    if (posterFile.value) {
      const uploaded = await uploadPoster<Mutation<CampusEvent>>(
        e.id,
        posterFile.value,
        event.value.version!,
      );
      event.value = uploaded.current;
      posterFile.value = undefined;
    }
    if (submit) {
      await post(`/organizer/events/${e.id}/submission`, { expectedVersion: event.value.version });
      await router.push(`/organizer/events/${e.id}`);
      app.toast('活动已提交审核');
    } else app.toast('草稿已保存');
  });
}
onMounted(load);
</script>
<template>
  <RouterLink to="/organizer/events" class="back-link"
    ><AppIcon name="ArrowLeft" :size="14" />返回活动工作台</RouterLink
  ><PageHeading
    eyebrow="FROM AN IDEA TO A GATHERING"
    title="打磨你的下一场相聚"
    description="先保存灵感，再完善细节。草稿允许暂时不完整。"
    ><StatusBadge status="DRAFT" /></PageHeading
  ><StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" /><template
    v-else-if="event"
    ><section class="panel">
      <div class="form-section-head">
        <h2><span class="section-index">01</span>活动基本信息</h2>
        <span class="tag">名称与类别发布后不可修改</span>
      </div>
      <div class="form-grid">
        <div class="form-field">
          <label for="event-name">活动名称</label
          ><input
            id="event-name"
            v-model="event.name"
            maxlength="60"
            placeholder="给这场相聚起个名字"
          />
        </div>
        <div class="form-field">
          <label for="event-category">活动类别</label
          ><select id="event-category" v-model="event.categoryId">
            <option value="">请选择类别</option>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="form-field span-two">
          <label for="event-description"
            >活动简介<span class="push-right">{{ count }} / 500</span></label
          ><textarea
            id="event-description"
            v-model="event.description"
            maxlength="500"
            rows="4"
            placeholder="告诉大家，这是一场怎样的相聚…"
          />
        </div>
      </div>
      <label class="poster-upload"
        ><AppIcon v-if="!poster" name="Plus" :size="23" /><img
          v-else
          :src="poster"
          alt="海报本地预览" /><span style="display: block; margin-top: 8px">活动海报 · 可选</span
        ><small>JPEG / PNG，最多5,000,000字节；选择后与草稿保存；演示模式存入本机浏览器</small
        ><input
          type="file"
          accept="image/jpeg,image/png"
          aria-label="选择海报预览"
          @change="upload(($event.target as HTMLInputElement).files?.[0])" /></label
      ><button
        v-if="poster"
        class="text-link"
        style="margin-top: 8px"
        @click="
          poster = '';
          posterFile = undefined;
        "
      >
        移除本次选择
      </button>
    </section>
    <section class="panel">
      <div class="form-section-head">
        <h2><span class="section-index">02</span>场次与票种</h2>
        <span class="muted" style="font-size: 11px">{{ event.sessions.length }} / 20 场</span>
      </div>
      <div class="alert info">
        <AppIcon name="Clock3" />
        <p>
          场次时间精确到小时，放票时间精确到分钟。每场票种容量总和不得超过场次容量；价格使用整数角保存，支持免费预约。
        </p>
      </div>
      <div v-for="(s, index) in event.sessions" :key="s.id" class="draft-session">
        <div class="draft-session-head">
          <h3>场次 {{ String(index + 1).padStart(2, '0') }}</h3>
          <button
            class="icon-btn"
            :aria-label="`删除场次${index + 1}`"
            @click="event.sessions.splice(index, 1)"
          >
            <AppIcon name="Trash2" :size="15" />
          </button>
        </div>
        <div class="draft-session-body">
          <div class="form-grid">
            <div class="form-field">
              <label :for="`session-name-${index}`">场次名称</label
              ><input :id="`session-name-${index}`" v-model="s.name" maxlength="60" />
            </div>
            <div class="form-field">
              <label :for="`session-location-${index}`">场次地点</label
              ><input
                :id="`session-location-${index}`"
                v-model="s.location"
                maxlength="60"
                placeholder="请输入具体地点"
              />
            </div>
            <div
              v-for="item in [
                ['startsAt', '场次开始'],
                ['endsAt', '场次结束'],
                ['salesOpensAt', '放票开始'],
                ['registrationClosesAt', '报名截止'],
              ] as const"
              :key="item[0]"
              class="form-field"
            >
              <label :for="`${item[0]}-${index}`">{{ item[1] }}</label
              ><input
                :id="`${item[0]}-${index}`"
                type="datetime-local"
                :value="inputDate(s[item[0]])"
                @change="setDate(s, item[0], ($event.target as HTMLInputElement).value)"
              />
            </div>
            <div class="form-field">
              <label :for="`session-capacity-${index}`">场次总容量</label
              ><input
                :id="`session-capacity-${index}`"
                v-model.number="s.capacity"
                type="number"
                min="1"
                max="9999"
                step="1"
              />
            </div>
          </div>
          <div class="divider" />
          <div class="section-title">
            <h3 style="font-size: 13px">票种设置</h3>
            <span class="muted" style="font-size: 10px"
              >总容量 {{ s.ticketTypes.reduce((n, t) => n + Number(t.capacity || 0), 0) }} /
              {{ s.capacity }}</span
            >
          </div>
          <div class="ticket-type-row" style="font-size: 10px; color: #999f88">
            <span>票种名称</span><span>容量</span><span>价格（元）</span><span />
          </div>
          <div v-for="(t, ti) in s.ticketTypes" :key="t.id" class="ticket-type-row">
            <input
              v-model="t.name"
              maxlength="60"
              :aria-label="`场次${index + 1}票种${ti + 1}名称`"
              placeholder="例如：普通票"
            /><input
              v-model.number="t.capacity"
              type="number"
              min="1"
              max="9999"
              step="1"
              aria-label="票种容量"
            /><input
              :value="t.priceJiao / 10"
              type="number"
              min="0"
              max="999999"
              step="0.1"
              aria-label="票种价格（元）"
              @input="
                t.priceJiao = Math.round(Number(($event.target as HTMLInputElement).value) * 10)
              "
            /><button
              class="icon-btn"
              :aria-label="`删除票种${ti + 1}`"
              @click="s.ticketTypes.splice(ti, 1)"
            >
              <AppIcon name="X" :size="14" />
            </button>
          </div>
          <button
            class="btn secondary small"
            :disabled="s.ticketTypes.length >= 10"
            @click="addType(s)"
          >
            <AppIcon name="Plus" :size="14" />添加票种
          </button>
        </div>
      </div>
      <button
        class="btn secondary full"
        style="margin-top: 20px"
        :disabled="event.sessions.length >= 20"
        @click="addSession"
      >
        <AppIcon name="Plus" :size="15" />添加场次
      </button>
    </section>
    <p v-if="task.error.value" class="error-inline" role="alert">{{ task.error.value }}</p>
    <div class="sticky-actions">
      <p>提交审核前将检查所有必填字段、结构与时间关系。</p>
      <div class="button-row">
        <button class="btn secondary" :disabled="task.busy.value" @click="save(false)">
          保存草稿</button
        ><button class="btn primary" :disabled="task.busy.value" @click="save(true)">
          <AppIcon v-if="task.busy.value" name="LoaderCircle" class="spin" />提交审核<AppIcon
            name="ArrowRight"
            :size="15"
          />
        </button>
      </div></div></template
  ><AppModal :open="!!rounding" title="确认调整到整小时" @close="rounding = undefined"
    ><p>
      场次起止精确到小时。所选时间将向下取整为
      {{ rounding?.value.replace('T', ' ') }}，确认后才应用此值。
    </p>
    <div class="button-row">
      <button class="btn secondary" @click="rounding = undefined">取消</button
      ><button
        class="btn primary"
        @click="
          if (rounding) rounding.session[rounding.field] = rounding.value + ':00+08:00';
          rounding = undefined;
        "
      >
        确认使用整点
      </button>
    </div></AppModal
  >
</template>
