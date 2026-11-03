import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { get, post } from '../api/client';
import type { User } from '../types';
import { ApiError } from '../api/errors';

export const useAppStore = defineStore('app', () => {
  const user = ref<User | null>(null);
  const initialized = ref(false);
  const notification = ref<{ message: string; type: 'success' | 'error' } | null>(null);
  const unreadMessages = ref(0);
  let notificationTimer: ReturnType<typeof setTimeout>;
  const home = computed(() =>
    user.value?.role === 'ORGANIZER'
      ? '/organizer/events'
      : user.value?.role === 'CHECKER'
        ? '/checker'
        : user.value?.role === 'ADMIN'
          ? '/admin/reviews'
          : '/events',
  );
  function toast(message: string, type: 'success' | 'error' = 'success') {
    clearTimeout(notificationTimer);
    notification.value = { message, type };
    notificationTimer = setTimeout(() => {
      notification.value = null;
    }, 5000);
  }
  function error(error: unknown) {
    if (error instanceof ApiError && (error.status === 401 || error.code === 'ACCOUNT_UNAVAILABLE'))
      user.value = null;
    toast(error instanceof Error ? error.message : '请求未完成，请稍后重试', 'error');
  }
  async function initialize() {
    if (initialized.value) return;
    try {
      user.value = await get<User>('/me');
      await refreshUnreadMessages();
    } catch {
      user.value = null;
      unreadMessages.value = 0;
    }
    initialized.value = true;
  }
  async function refreshUnreadMessages() {
    if (!user.value) {
      unreadMessages.value = 0;
      return;
    }
    try {
      const result = await get<{ count: number }>('/me/messages/unread-count');
      unreadMessages.value = Number(result.count) || 0;
    } catch {
      // A failed count must not hide existing navigation or invent unread state.
    }
  }
  async function login(username: string, password: string) {
    user.value = await post<User>('/auth/login', { username, password });
    await refreshUnreadMessages();
    return home.value;
  }
  async function phoneLogin(input: {
    phone: string;
    role: string;
    verificationId: string;
    verificationCode: string;
    purpose: 'LOGIN' | 'REGISTER';
  }) {
    user.value = await post<User>('/auth/phone-login', input);
    await refreshUnreadMessages();
    return home.value;
  }
  async function logout() {
    try {
      await post('/auth/logout');
    } finally {
      user.value = null;
      unreadMessages.value = 0;
    }
  }
  return {
    user,
    initialized,
    notification,
    unreadMessages,
    home,
    toast,
    error,
    initialize,
    refreshUnreadMessages,
    login,
    phoneLogin,
    logout,
  };
});
