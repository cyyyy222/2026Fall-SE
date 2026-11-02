import { ref } from 'vue';
import { useAppStore } from '../stores/app';
export function useTask() {
  const busy = ref(false);
  const error = ref('');
  const app = useAppStore();
  async function run<T>(task: () => Promise<T>, success?: string): Promise<T | undefined> {
    if (busy.value) return;
    busy.value = true;
    error.value = '';
    try {
      const result = await task();
      if (success) app.toast(success);
      return result;
    } catch (e) {
      error.value = e instanceof Error ? e.message : '请求未完成';
      app.error(e);
    } finally {
      busy.value = false;
    }
  }
  return { busy, error, run };
}
