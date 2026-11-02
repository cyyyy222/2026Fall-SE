<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, post } from '../api/client';
import type { Message, Mutation, Order, Page } from '../types';
import { dateTime } from '../utils/format';
import { useTask } from '../composables/useTask';
import { useAppStore } from '../stores/app';
import PageHeading from '../components/PageHeading.vue';
import AppIcon from '../components/AppIcon.vue';
import StatePanel from '../components/StatePanel.vue';
const route = useRoute();
const router = useRouter();
const app = useAppStore();
const task = useTask();
const messages = ref<Message[]>([]);
const selected = ref<Message>();
const loading = ref(true);
const error = ref('');
const unreadOnly = ref(false);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    messages.value = (
      await get<Page<Message>>(`/me/messages?pageSize=100&unreadOnly=${unreadOnly.value}`)
    ).items;
    if (route.params.messageId)
      selected.value = await get<Message>(`/me/messages/${route.params.messageId}`);
    else selected.value = messages.value[0];
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法加载消息';
  } finally {
    loading.value = false;
  }
}
async function markRead() {
  if (!selected.value) return;
  await task.run(async () => {
    const result = await post<Mutation<Message>>(`/me/messages/${selected.value!.id}/read`);
    selected.value = result.current;
    const index = messages.value.findIndex((message) => message.id === result.current.id);
    if (index >= 0) messages.value[index] = result.current;
  }, '已标记为已读');
}
async function openTarget() {
  if (!selected.value?.target) return;
  const target = selected.value.target;
  await task.run(async () => {
    if (target.resourceType === 'ACCOUNT') {
      await router.push('/account');
    } else if (target.resourceType === 'ORDER') {
      await router.push(`/orders/${target.resourceId}`);
    } else if (target.resourceType === 'EVENT') {
      if (app.user?.role === 'ORGANIZER') {
        await router.push(`/organizer/events/${target.resourceId}`);
      } else if (app.user?.role === 'ADMIN') {
        await router.push(`/admin/events/${target.resourceId}`);
      } else if (app.user?.role === 'CHECKER') {
        await router.push('/checker');
      } else {
        const orders = await get<Page<Order>>(`/me/orders?eventId=${target.resourceId}&pageSize=1`);
        await router.push(orders.items.length ? `/orders/${orders.items[0]!.id}` : '/orders');
      }
    } else {
      app.toast('请从对应业务页面查看相关记录');
    }
  });
}
onMounted(load);
</script>
<template>
  <PageHeading
    eyebrow="NOTES FOR YOU"
    title="站内消息"
    description="不错过每一次重要提醒。阅读消息不会延长业务操作期限。"
    ><button class="btn secondary small" @click="load">
      <AppIcon name="RefreshCw" :size="14" />刷新
    </button></PageHeading
  ><StatePanel v-if="loading || error" :loading="loading" :error="error" @retry="load" />
  <div v-else class="message-layout">
    <section class="message-list">
      <div class="message-list-header">
        <strong style="font-size: 12px">我的消息 · {{ messages.length }}</strong
        ><label class="checkbox-label"
          ><input v-model="unreadOnly" type="checkbox" @change="load" />仅未读</label
        >
      </div>
      <button
        v-for="message in messages"
        :key="message.id"
        class="message-item"
        :class="{ selected: selected?.id === message.id }"
        @click="selected = message"
      >
        <h3><i v-if="!message.readAt" class="unread-dot" />{{ message.title }}</h3>
        <p>{{ message.body }}</p>
        <small>{{ dateTime(message.createdAt) }}</small></button
      ><StatePanel
        v-if="!messages.length"
        title="暂无消息"
        description="重要业务结果将在这里保留。"
        icon="Bell"
      />
    </section>
    <section v-if="selected" class="message-detail">
      <p class="eyebrow">IN YOUR INBOX</p>
      <h2>{{ selected.title }}</h2>
      <p class="message-time">
        {{ dateTime(selected.createdAt) }} · {{ selected.readAt ? '已读' : '未读' }}
      </p>
      <p class="content-prose">{{ selected.body }}</p>
      <div v-if="selected.actionDeadline" class="alert warning" style="margin-top: 25px">
        <AppIcon name="Clock3" />操作截止：{{ dateTime(selected.actionDeadline) }}
      </div>
      <div class="button-row" style="margin-top: 35px">
        <button v-if="selected.target" class="btn primary" @click="openTarget">
          查看相关记录<AppIcon name="ArrowUpRight" :size="15" /></button
        ><button
          v-if="!selected.readAt"
          class="btn secondary"
          :disabled="task.busy.value"
          @click="markRead"
        >
          <AppIcon name="Check" :size="15" />标记已读
        </button>
      </div>
      <p class="field-help" style="margin-top: 30px">
        消息生成180天后到期。点击业务入口后仍会重新检查最新状态与权限。
      </p>
    </section>
    <StatePanel
      v-else
      title="选择一条消息"
      description="你的重要提醒，会在这里展开。"
      icon="Mail"
    />
  </div>
</template>
