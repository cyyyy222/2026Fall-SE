<script setup lang="ts">
import AppIcon from './AppIcon.vue';
withDefaults(
  defineProps<{
    loading?: boolean;
    error?: string;
    title?: string;
    description?: string;
    icon?: string;
  }>(),
  {
    icon: 'Ticket',
    title: '这里暂时还没有内容',
    description: '换个筛选条件，或去发现下一场喜欢的活动。',
  },
);
defineEmits<{ retry: [] }>();
</script>
<template>
  <div class="state-panel" :class="{ compact: loading }" :role="error ? 'alert' : 'status'">
    <span class="state-icon"
      ><AppIcon
        :name="loading ? 'LoaderCircle' : error ? 'CircleAlert' : icon"
        :size="30"
        :class="{ spin: loading }"
    /></span>
    <h3>{{ loading ? '正在为你准备…' : error ? '暂时无法加载' : title }}</h3>
    <p>{{ error || (loading ? '请稍等片刻' : description) }}</p>
    <button v-if="error" class="btn secondary" @click="$emit('retry')">
      <AppIcon name="RefreshCw" />重新加载</button
    ><slot v-else />
  </div>
</template>
