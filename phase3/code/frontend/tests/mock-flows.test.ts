import assert from 'node:assert/strict';
import { before, beforeEach, test } from 'node:test';
import { randomUUID } from 'node:crypto';
import type { CampusEvent, Order, Page, PaymentAudit, Ticket } from '../src/types/index';

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) {
    return this.values.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.values.set(key, value);
  }
  removeItem(key: string) {
    this.values.delete(key);
  }
  clear() {
    this.values.clear();
  }
}
let adapter: typeof import('../src/mocks/adapter');
before(async () => {
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true,
  });
  Object.defineProperty(globalThis, 'sessionStorage', {
    value: new MemoryStorage(),
    configurable: true,
  });
  adapter = await import('../src/mocks/adapter');
});
beforeEach(() => adapter.resetDemo());
async function call<T>(method: string, path: string, body?: unknown, key?: string): Promise<T> {
  return (await adapter.mockRequest<T>(method, path, body, key)).data;
}
const login = (username = 'participant') =>
  call('POST', '/auth/login', { username, password: 'Demo2026' });
function hasCode(code: string) {
  return (error: unknown) =>
    !!error && typeof error === 'object' && 'code' in error && error.code === code;
}

test('anonymous readers can discover activities but cannot access private orders', async () => {
  const page = await call<Page<unknown>>('GET', '/public/sessions');
  assert.ok(page.items.length >= 6);
  await assert.rejects(call('GET', '/me/orders'), hasCode('AUTH_REQUIRED'));
});

test('one booking and payment intent yields one ticket; refund invalidates it', async () => {
  await login();
  const event = await call<CampusEvent>('GET', `/public/events/${adapter.demoIds.event}`);
  const session = event.sessions[0]!;
  const type = session.ticketTypes[0]!;
  const input = {
    requestId: randomUUID(),
    sessionId: session.id,
    ticketTypeId: type.id,
    attendeeName: '演示同学',
  };
  const registered = await call<{ current: Order }>('POST', '/me/registrations', input);
  assert.equal(registered.current.status, 'UNPAID');
  const replay = await call<{ current: Order }>('POST', '/me/registrations', input);
  assert.equal(replay.current.id, registered.current.id);
  const attemptId = randomUUID();
  const path = `/me/orders/${registered.current.id}`;
  const paid = await call<{ current: Order; ticket: Ticket }>('POST', `${path}/payment-attempts`, {
    attemptId,
  });
  const paidAgain = await call<{ current: Order; ticket: Ticket }>(
    'POST',
    `${path}/payment-attempts`,
    { attemptId },
  );
  assert.equal(paid.current.status, 'REGISTERED');
  assert.equal(paid.ticket.id, paidAgain.ticket.id);
  const audit = await call<PaymentAudit>('GET', `${path}/payments`);
  assert.equal(audit.paymentAttempts.filter((p) => p.result === 'SUCCESS').length, 1);
  const refund = await call<{ current: Order }>(
    'POST',
    `${path}/refund-requests`,
    { attemptId: randomUUID(), reason: '前端测试' },
    randomUUID(),
  );
  assert.equal(refund.current.status, 'REFUNDED');
  assert.equal((await call<Ticket>('GET', `/me/tickets/${paid.ticket.id}`)).status, 'INVALID');
  const updated = await call<CampusEvent>('GET', `/public/events/${event.id}`);
  assert.equal(updated.sessions[0]!.ticketTypes[0]!.remaining, type.remaining);
});

test('idempotency rejects parameter changes and roles cannot perform participant purchases', async () => {
  await login();
  const event = await call<CampusEvent>('GET', `/public/events/${adapter.demoIds.event}`);
  const input = {
    requestId: randomUUID(),
    sessionId: event.sessions[0]!.id,
    ticketTypeId: event.sessions[0]!.ticketTypes[0]!.id,
    attendeeName: '演示同学',
  };
  await call('POST', '/me/registrations', input);
  await assert.rejects(
    call('POST', '/me/registrations', { ...input, attendeeName: '不同姓名' }),
    hasCode('IDEMPOTENCY_CONFLICT'),
  );
  await login('admin');
  await assert.rejects(
    call('POST', '/me/registrations', { ...input, requestId: randomUUID() }),
    hasCode('FORBIDDEN'),
  );
});

test('live checker fixture allows preview then exactly one check-in', async () => {
  await login();
  const orders = await call<Page<Order>>('GET', '/me/orders');
  const live = orders.items.find((o) => o.event.sessionName === '现场演示场')!;
  assert.ok(live.ticketId);
  await login('checker');
  const preview = await call<{ canConfirm: boolean }>('POST', '/checker/ticket-previews', {
    ticketId: live.ticketId,
  });
  assert.equal(preview.canConfirm, true);
  const first = await call<{ checkedAt: string; replayed: boolean }>('POST', '/checker/checkins', {
    ticketId: live.ticketId,
  });
  const second = await call<{ checkedAt: string; replayed: boolean }>('POST', '/checker/checkins', {
    ticketId: live.ticketId,
  });
  assert.equal(first.checkedAt, second.checkedAt);
  assert.equal(second.replayed, true);
  await assert.rejects(
    call('GET', `/management/events/${live.event.eventId}/attendees`),
    hasCode('OBJECT_FORBIDDEN'),
  );
});

test('draft saving, submission, and review rejection preserve editable state', async () => {
  await login('organizer');
  const draft = await call<CampusEvent>('POST', '/organizer/events');
  const saved = await call<{ current: CampusEvent }>('PUT', `/organizer/events/${draft.id}/draft`, {
    expectedVersion: draft.version,
    name: '测试草稿',
    categoryId: null,
    description: '',
    sessions: [],
  });
  assert.equal(saved.current.status, 'DRAFT');
  await assert.rejects(
    call('POST', `/organizer/events/${draft.id}/submission`, {
      expectedVersion: saved.current.version,
    }),
    hasCode('VALIDATION_FAILED'),
  );
  await login('admin');
  const reviews = await call<Page<CampusEvent>>('GET', '/admin/reviews');
  const pending = reviews.items[0]!;
  const rejected = await call<{ current: CampusEvent }>(
    'POST',
    `/admin/events/${pending.id}/rejection`,
    { expectedVersion: pending.version, reason: '请补充场地确认' },
  );
  assert.equal(rejected.current.status, 'DRAFT');
  assert.equal(rejected.current.lastReviewReason, '请补充场地确认');
});

test('organizer CSV has BOM and headers and cannot remove the last checker', async () => {
  await login('organizer');
  const exported = await call<{ id: string; rowCount: number }>(
    'POST',
    `/management/events/${adapter.demoIds.event}/exports`,
  );
  assert.ok(exported.rowCount > 0);
  const file = await call<Blob>('GET', `/management/exports/${exported.id}/download`);
  const bytes = new Uint8Array(await file.arrayBuffer());
  assert.deepEqual([...bytes.slice(0, 3)], [239, 187, 191]);
  assert.match(await file.text(), /姓名,场次,票种,报名状态,签到状态,票码/);
  await login('admin');
  const accounts = await call<Page<{ id: string; role: string; version: number }>>(
    'GET',
    '/admin/accounts',
  );
  const checker = accounts.items.find((a) => a.role === 'CHECKER')!;
  await assert.rejects(
    call('PATCH', `/admin/accounts/${checker.id}`, {
      expectedVersion: checker.version,
      enabled: false,
    }),
    hasCode('LAST_CHECKER_REQUIRED'),
  );
});
