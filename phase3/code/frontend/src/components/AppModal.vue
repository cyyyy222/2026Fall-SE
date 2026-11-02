<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import AppIcon from './AppIcon.vue';
const props = defineProps<{ open: boolean; title: string; wide?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const panel = ref<HTMLElement>();
let previousFocus: HTMLElement | null = null;
function key(event: KeyboardEvent) {
  if (!props.open) return;
  if (event.key === 'Escape') emit('close');
  if (event.key !== 'Tab') return;
  const items = [
    ...(panel.value?.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), select, textarea, a[href]',
    ) || []),
  ];
  if (event.shiftKey && document.activeElement === items[0]) {
    event.preventDefault();
    items.at(-1)?.focus();
  } else if (!event.shiftKey && document.activeElement === items.at(-1)) {
    event.preventDefault();
    items[0]?.focus();
  }
}
watch(
  () => props.open,
  async (open) => {
    if (open) {
      previousFocus = document.activeElement as HTMLElement;
      document.body.style.overflow = 'hidden';
      document.addEventListener('keydown', key);
      await nextTick();
      panel.value?.querySelector<HTMLElement>('input, textarea, select, button')?.focus();
    } else {
      document.body.style.overflow = '';
      document.removeEventListener('keydown', key);
      previousFocus?.focus();
    }
  },
);
onBeforeUnmount(() => {
  document.body.style.overflow = '';
  document.removeEventListener('keydown', key);
});
</script>
<template>
  <Teleport to="body"
    ><div v-if="open" class="modal-backdrop" @mousedown.self="emit('close')">
      <section
        ref="panel"
        class="modal"
        :class="{ wide }"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <div class="modal-heading">
          <h2>{{ title }}</h2>
          <button class="icon-btn" aria-label="关闭弹窗" @click="emit('close')">
            <AppIcon name="X" />
          </button>
        </div>
        <slot />
      </section></div
  ></Teleport>
</template>
