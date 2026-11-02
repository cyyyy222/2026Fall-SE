import type { Role } from '../types';
export const money = (jiao: number) =>
  new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 1 }).format(
    jiao / 10,
  );
export function date(value: string | null | undefined, options: Intl.DateTimeFormatOptions = {}) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: '2-digit',
    day: '2-digit',
    ...options,
  }).format(new Date(value));
}
export const time = (value: string) =>
  date(value, { hour: '2-digit', minute: '2-digit' }).split(' ').at(-1);
export const dateTime = (value: string | null | undefined) =>
  date(value, { year: 'numeric', hour: '2-digit', minute: '2-digit' });
export const roleNames: Record<Role, string> = {
  PARTICIPANT: '参与者',
  ORGANIZER: '组织者',
  CHECKER: '检票员',
  ADMIN: '管理员',
};
export const labels: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '待审核',
  PUBLISHED: '已发布',
  CANCELLED: '已取消',
  TAKEN_DOWN: '已下架',
  ENDED: '已结束',
  WAITLISTED: '候补中',
  OFFERED: '递补待确认',
  UNPAID: '待支付',
  REGISTERED: '已报名',
  CHECKED_IN: '已签到',
  COMPLETED: '已完成',
  REFUNDED: '已退款',
  ABSENT: '缺席',
  VALID: '有效',
  CHECKED: '已验票',
  INVALID: '已失效',
  PENDING: '待处理',
  RUNNING: '处理中',
  SUCCEEDED: '已完成',
  FAILED_RETRYABLE: '等待重试',
  SUCCESS: '成功',
  FAILURE: '失败',
  SUBSTANTIATED: '核查属实',
  UNSUBSTANTIATED: '不属实',
  PROCESSING: '结算中',
  NOT_DUE: '尚未结束',
  ACCEPTED: '已受理',
  FAILED: '失败待重试',
  REVOKED: '已撤销',
  USER: '本人申请',
  PLATFORM_TERMINATION: '平台终止',
  ON_SALE: '报名中',
  SOLD_OUT: '可候补',
  STOPPED: '已停售',
  NOT_OPEN: '未开售',
  CLOSED: '报名截止',
};
export const label = (value: string | undefined | null) => (value ? labels[value] || value : '—');
export const uid = () => crypto.randomUUID();
export function saveFile(
  content: Blob | string,
  filename: string,
  type = 'text/plain;charset=utf-8',
) {
  const url = URL.createObjectURL(
    content instanceof Blob ? content : new Blob([content], { type }),
  );
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
