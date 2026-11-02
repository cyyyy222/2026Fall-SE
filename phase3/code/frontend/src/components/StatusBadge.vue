<script setup lang="ts">
import { computed } from 'vue';
import { label } from '../utils/format';
const props = defineProps<{ status?: string | null; text?: string }>();
const tone = computed(() =>
  [
    'PUBLISHED',
    'REGISTERED',
    'VALID',
    'SUCCEEDED',
    'SUCCESS',
    'ON_SALE',
    'COMPLETED',
    'CHECKED_IN',
    'CHECKED',
  ].includes(props.status || '')
    ? 'sage'
    : [
          'UNPAID',
          'OFFERED',
          'PENDING_REVIEW',
          'PENDING',
          'WAITLISTED',
          'RUNNING',
          'PROCESSING',
          'NOT_OPEN',
          'ACCEPTED',
        ].includes(props.status || '')
      ? 'warm'
      : ['TAKEN_DOWN', 'FAILURE', 'FAILED', 'ABSENT', 'INVALID'].includes(props.status || '')
        ? 'rose'
        : 'neutral',
);
</script>
<template>
  <span class="badge" :class="tone"><span class="badge-dot" />{{ text || label(status) }}</span>
</template>
