import assert from 'node:assert/strict';
import { before, beforeEach, test } from 'node:test';
import { randomUUID } from 'node:crypto';
import type {
  CampusEvent,
  Order,
  Page,
  PaymentAudit,
  Ticket,
  Role,
  PhoneCodePurpose,
} from '../src/types/index';

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

async function phoneCode(phone: string, role: Role, purpose: PhoneCodePurpose) {
  const original = console.info;
  let code = '';
  console.info = (message: string) => {
    code = /code=(\d{6})/.exec(message)?.[1] || '';
  };
  try {
    const receipt = await call<{ verificationId: string }>('POST', '/auth/phone-codes', {
      phone,
      role,
      purpose,
    });
    assert.match(code, /^\d{6}$/);
    assert.equal('code' in receipt, false);
    return { verificationId: receipt.verificationId, verificationCode: code };
  } finally {
    console.info = original;
  }
}

test('registration allows omitted emails and common QQ suffixes', async () => {
  for (const [i, email] of [undefined, '', '123456@qq.com', 'student@nju.edu.cn'].entries()) {
    const phone = `1392222000${i}`;
    const proof = await phoneCode(phone, 'PARTICIPANT', 'REGISTER');
    await call('POST', '/auth/register', {
      username: `mail_${i}`,
      password: 'Testpass123',
      phone,
      role: 'PARTICIPANT',
      ...(email === undefined ? {} : { email }),
      ...proof,
    });
    const user = await call<{ email: string | null }>('POST', '/auth/login', {
      username: `mail_${i}`,
      password: 'Testpass123',
    });
    assert.equal(user.email, email || null);
  }
});

test('phone code resets a forgotten password for only the selected role', async () => {
  const proof = await phoneCode('13812345678', 'PARTICIPANT', 'RESET_PASSWORD');
  await call('POST', '/auth/password-reset', {
    phone: '13812345678',
    role: 'PARTICIPANT',
    newPassword: 'Changed123',
    ...proof,
  });
  await assert.rejects(login('participant'), hasCode('INVALID_CREDENTIALS'));
  assert.equal(
    (
      await call<{ role: string }>('POST', '/auth/login', {
        username: 'participant',
        password: 'Changed123',
      })
    ).role,
    'PARTICIPANT',
  );
  assert.ok(await login('organizer'));
});

test('personal information rebinds with only the new phone code and rejects occupied same-role numbers', async () => {
  await login('participant');
  const proof = await phoneCode('13933330001', 'PARTICIPANT', 'CHANGE_PHONE');
  const result = await call<{ maskedPhone: string; requiresLogin: boolean }>('PATCH', '/me/phone', {
    newPhone: '13933330001',
    ...proof,
  });
  assert.equal(result.maskedPhone, '139****0001');
  assert.equal(result.requiresLogin, true);
  await assert.rejects(call('GET', '/me'), hasCode('AUTH_REQUIRED'));
  await login('participant');
  const other = await phoneCode('13933330002', 'PARTICIPANT', 'REGISTER');
  await call('POST', '/auth/register', {
    username: 'otherphone',
    password: 'Testpass123',
    phone: '13933330002',
    role: 'PARTICIPANT',
    ...other,
  });
  await assert.rejects(
    call('POST', '/auth/phone-codes', {
      phone: '13933330002',
      role: 'PARTICIPANT',
      purpose: 'CHANGE_PHONE',
    }),
    hasCode('PHONE_ALREADY_BOUND'),
  );
});

test('manual recovery keeps detailed evidence, lets admin approve-reset and emits local notification', async () => {
  const proof = await phoneCode('13944440001', 'PARTICIPANT', 'RECOVERY_CONTACT');
  const receipt = await call<{ requestId: string }>('POST', '/auth/recoveries', {
    account: 'participant',
    phone: '13812345678',
    currentPhone: '13944440001',
    role: 'PARTICIPANT',
    statement: '原手机号码已经无法使用，希望凭过去的资料恢复本人一直使用的账号。',
    pastBehavior: '我曾报名晚风来信音乐会，可以说明报名时间和票种以及订单状态等信息。',
    ...proof,
  });
  await login('admin');
  const list = await call<Page<{ id: string; statement: string }>>('GET', '/admin/recoveries');
  assert.ok(list.items.find((r) => r.id === receipt.requestId)?.statement.includes('原手机号码'));
  const info = console.info;
  let notification = '';
  console.info = (message: string) => {
    notification = message;
  };
  try {
    const result = await call<{ status: string }>(
      'POST',
      `/admin/recoveries/${receipt.requestId}/resolution`,
      {
        decision: 'VERIFIED',
        verificationNote: '已经核对账户原手机号与历史订单陈述，确认申请人为本人。',
        initialPassword: 'Temporary123',
        reason: '资料核实通过',
      },
    );
    assert.equal(result.status, 'RESOLVED');
  } finally {
    console.info = info;
  }
  assert.match(notification, /LOCAL_RECOVERY_SMS.*13944440001.*account=participant/);
  const user = await call<{ mustChangePassword: boolean; maskedPhone: string }>(
    'POST',
    '/auth/login',
    { username: 'participant', password: 'Temporary123' },
  );
  assert.equal(user.mustChangePassword, true);
  assert.equal(user.maskedPhone, '139****0001');
  await assert.rejects(call('GET', '/me/orders'), hasCode('PASSWORD_CHANGE_REQUIRED'));
});

test('verified phone can register each ordinary role once and duplicate consent logs into the existing account', async () => {
  const phone = '13911112222';
  let firstId = '';
  for (const role of ['PARTICIPANT', 'ORGANIZER'] as const) {
    const proof = await phoneCode(phone, role, 'REGISTER');
    const r = await call<{ id: string }>('POST', '/auth/register', {
      username: `phone_${role}`,
      password: 'Testpass123',
      email: `${role}@example.test`,
      phone,
      role,
      ...proof,
    });
    if (role === 'PARTICIPANT') firstId = r.id;
    assert.ok(r.id);
  }
  // A different fixture phone already has a participant account; verification precedes the prompt.
  const proof = await phoneCode('13812345678', 'PARTICIPANT', 'REGISTER');
  await assert.rejects(
    call('POST', '/auth/register', {
      username: 'duplicate_phone',
      password: 'Testpass123',
      email: 'duplicate@example.test',
      phone: '13812345678',
      role: 'PARTICIPANT',
      ...proof,
    }),
    hasCode('PHONE_ACCOUNT_EXISTS'),
  );
  const user = await call<{ role: string; username: string }>('POST', '/auth/phone-login', {
    phone: '13812345678',
    role: 'PARTICIPANT',
    purpose: 'REGISTER',
    ...proof,
  });
  assert.equal(user.username, 'participant');
  assert.equal(user.role, 'PARTICIPANT');
  await assert.rejects(
    call('POST', '/auth/phone-login', {
      phone: '13812345678',
      role: 'PARTICIPANT',
      purpose: 'REGISTER',
      ...proof,
    }),
    hasCode('PHONE_CODE_EXPIRED'),
  );
  assert.ok(firstId);
});

test('phone login binds role and purpose, rejects wrong codes and admin account pages filter on the server', async () => {
  const proof = await phoneCode('13812345678', 'PARTICIPANT', 'LOGIN');
  await assert.rejects(
    call('POST', '/auth/phone-login', {
      phone: '13812345678',
      role: 'ORGANIZER',
      purpose: 'LOGIN',
      ...proof,
    }),
    hasCode('PHONE_CODE_EXPIRED'),
  );
  await assert.rejects(
    call('POST', '/auth/phone-login', {
      phone: '13812345678',
      role: 'PARTICIPANT',
      purpose: 'REGISTER',
      ...proof,
    }),
    hasCode('PHONE_CODE_EXPIRED'),
  );
  await assert.rejects(
    call('POST', '/auth/phone-login', {
      phone: '13812345678',
      role: 'PARTICIPANT',
      purpose: 'LOGIN',
      ...proof,
      verificationCode: proof.verificationCode === '000000' ? '000001' : '000000',
    }),
    hasCode('PHONE_CODE_INVALID'),
  );
  assert.equal(
    (
      await call<{ role: string }>('POST', '/auth/phone-login', {
        phone: '13812345678',
        role: 'PARTICIPANT',
        purpose: 'LOGIN',
        ...proof,
      })
    ).role,
    'PARTICIPANT',
  );
  await login('admin');
  const page = await call<Page<{ role: string }>>('GET', '/admin/accounts?role=ORGANIZER');
  assert.equal(page.total, 1);
  assert.ok(page.items.every((x) => x.role === 'ORGANIZER'));
});
