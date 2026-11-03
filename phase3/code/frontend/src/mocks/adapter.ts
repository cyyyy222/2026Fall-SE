import { ApiError } from '../api/errors';
import type { CampusEvent, Order, OrderStatus, Result, Role, Session, Ticket } from '../types';
import { fixedId, makeSeed } from './seed';
import type { Database, DemoAccount, StoredOrder } from './seed';
import { uid } from '../utils/format';

const STORAGE_KEY = 'campus-tickets-demo-v1';
const SESSION_KEY = 'campus-ticket-session';
const phoneChallenges = new Map<
  string,
  { id: string; code: string; expires: number; attempts: number }
>();
const phoneCooldowns = new Map<string, number>();
interface Recovery {
  id: string;
  account: string;
  phone: string;
  targetAccountId: string | null;
  status: string;
  createdAt: string;
  verificationNote: string | null;
  resolvedAt: string | null;
  currentPhone?: string;
  role?: string;
  statement?: string;
  pastBehavior?: string;
  contactVerifiedAt?: string;
  historicalHints?: string[];
}
interface ExportRecord {
  id: string;
  eventId: string;
  status: string;
  rowCount: number;
  createdAt: string;
  expiresAt: string | null;
  dataAsOf: string;
  downloadUrl: string;
  content: string;
}
interface Investigation {
  id: string;
  factId: string | null;
  eventId: string;
  conclusion: string;
  mergedReportIds: string[];
  violationRecorded: boolean;
  eventStatus: string;
  terminationJobId: string | null;
  reason: string;
  evidenceText: string;
  factKey: string;
  createdAt: string;
}
interface LocalDatabase extends Database {
  recoveries: Recovery[];
  exports: ExportRecord[];
  investigations: Investigation[];
  mutations: Record<string, { fingerprint: string; result: unknown }>;
  accountSessions: Record<string, string>;
}
const active: OrderStatus[] = ['WAITLISTED', 'OFFERED', 'UNPAID', 'REGISTERED', 'CHECKED_IN'];
const occupies: OrderStatus[] = [
  'OFFERED',
  'UNPAID',
  'REGISTERED',
  'CHECKED_IN',
  'COMPLETED',
  'ABSENT',
];
const now = () => new Date().toISOString();
const plus = (ms: number) => new Date(Date.now() + ms).toISOString();
function fail(code: string, message: string, status = 409): never {
  throw new ApiError(code, message, status);
}
function load(): LocalDatabase {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const data = JSON.parse(raw) as LocalDatabase;
      if (data.schema === 1) return data;
    }
  } catch {
    /* A damaged local preview is replaced with the documented demo seed. */
  }
  return {
    ...makeSeed(),
    recoveries: [],
    exports: [],
    investigations: [],
    mutations: {},
    accountSessions: {},
  };
}
let db = load();
const save = () => localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
save();
export function resetDemo() {
  phoneChallenges.clear();
  phoneCooldowns.clear();
  db = {
    ...makeSeed(),
    recoveries: [],
    exports: [],
    investigations: [],
    mutations: {},
    accountSessions: {},
  };
  sessionStorage.removeItem(SESSION_KEY);
  save();
}
function account(required = true): DemoAccount | null {
  const stored = sessionStorage.getItem(SESSION_KEY);
  const session = stored ? (JSON.parse(stored) as { id: string; token: string }) : null;
  const user = session && db.accounts.find((a) => a.id === session.id);
  if (!user || !session) {
    if (required) fail('AUTH_REQUIRED', '请先登录后继续', 401);
    return null;
  }
  if (
    db.accountSessions[user.id] !== session.token ||
    Date.now() >= Date.parse(user.sessionExpiresAt)
  )
    fail('SESSION_REVOKED', '登录已失效，请重新登录', 401);
  if (!user.enabled || user.deletedAt) fail('ACCOUNT_UNAVAILABLE', '账号已停用或注销', 403);
  if (user.role === 'CHECKER') {
    const event = db.events.find((e) => e.sessions.some((s) => s.id === user.assignedSessionId));
    if (
      !event ||
      terminated(event) ||
      event.sessions.every((s) => Date.parse(s.endsAt) <= Date.now())
    )
      fail('ACCOUNT_UNAVAILABLE', '活动已结束或终止，检票账号已失效', 403);
  }
  return user;
}
function requireRole(...roles: Role[]) {
  const user = account()!;
  if (!roles.includes(user.role)) fail('FORBIDDEN', '当前账号没有此操作权限', 403);
  return user;
}
function userView(user: DemoAccount) {
  const {
    password: _password,
    enabled: _enabled,
    deletedAt: _deleted,
    version: _version,
    assignedSessionId: _assignment,
    phone: _phone,
    ...view
  } = user;
  return view;
}
function eventById(id: string) {
  return db.events.find((e) => e.id === id) || fail('RESOURCE_NOT_FOUND', '活动不存在', 404);
}
function sessionById(id: string): { event: CampusEvent; session: Session } {
  const event =
    db.events.find((e) => e.sessions.some((s) => s.id === id)) ||
    fail('RESOURCE_NOT_FOUND', '场次不存在', 404);
  return { event, session: event.sessions.find((s) => s.id === id)! };
}
function terminated(e: CampusEvent) {
  return (
    ['CANCELLED', 'TAKEN_DOWN', 'ENDED'].includes(e.status || '') ||
    (e.status === 'PUBLISHED' &&
      e.sessions.length > 0 &&
      e.sessions.every((s) => Date.parse(s.endsAt) <= Date.now()))
  );
}
function ownEvent(id: string) {
  const u = requireRole('ORGANIZER');
  const event = eventById(id);
  if (event.organizerId !== u.id) fail('OBJECT_FORBIDDEN', '不能管理其他组织者的活动', 403);
  return event;
}
function managedEvent(id: string, sessionId?: string | null) {
  const u = requireRole('ORGANIZER', 'ADMIN', 'CHECKER');
  const event = eventById(id);
  if (u.role === 'ORGANIZER' && event.organizerId !== u.id)
    fail('OBJECT_FORBIDDEN', '无权读取该活动', 403);
  if (
    u.role === 'CHECKER' &&
    (!sessionId ||
      sessionId !== u.assignedSessionId ||
      !event.sessions.some((s) => s.id === sessionId))
  )
    fail('OBJECT_FORBIDDEN', '仅能查看当前授权场次', 403);
  const end = event.expiresAt
    ? Date.parse(event.expiresAt)
    : event.sessions.length
      ? Math.max(...event.sessions.map((s) => Date.parse(s.endsAt))) + 7 * 86400000
      : Infinity;
  if (Date.now() >= end) fail('DATA_EXPIRED', '数据已超过活动终结后7天的保留期限', 410);
  return event;
}
function ownOrder(id: string) {
  const u = requireRole('PARTICIPANT');
  const order = db.orders.find((o) => o.id === id) || fail('RESOURCE_NOT_FOUND', '订单不存在', 404);
  if (order.userId !== u.id) fail('OBJECT_FORBIDDEN', '只能访问本人的订单', 403);
  return order;
}
function checkinCloseAt(session: Session) {
  const start = Date.parse(session.startsAt);
  return start + Math.max(0, Date.parse(session.endsAt) - start) / 2;
}
function orderView(order: StoredOrder): Order {
  const event = eventById(order.event.eventId);
  const session = event.sessions.find((s) => s.id === order.event.sessionId);
  const open =
    !!session && Date.now() < Date.parse(session.registrationClosesAt) && !terminated(event);
  const pendingOpen = !!session && Date.now() < checkinCloseAt(session) && !terminated(event);
  const actions: string[] = [];
  if (
    pendingOpen &&
    order.status === 'UNPAID' &&
    (!order.payDeadline || Date.now() < Date.parse(order.payDeadline))
  )
    actions.push('PAY');
  if (pendingOpen && order.status === 'OFFERED') actions.push('CONFIRM_PROMOTION');
  if (pendingOpen && ['WAITLISTED', 'OFFERED', 'UNPAID'].includes(order.status))
    actions.push('CANCEL');
  if (open && order.status === 'REGISTERED') actions.push('REQUEST_REFUND');
  if (order.ticketId) actions.push('VIEW_TICKET', 'DOWNLOAD_TICKET');
  actions.push('REPORT');
  const { userId: _userId, checkedAt: _checked, enteredWaitlist: _wait, ...view } = order;
  return {
    ...view,
    event: {
      ...view.event,
      eventStatus: event.status!,
      location: session?.location || view.event.location,
      terminationReason: event.terminationReason || null,
    },
    allowedActions: actions,
  };
}
function decorate(event: CampusEvent): CampusEvent {
  return {
    ...event,
    sessions: event.sessions.map((s) => {
      const ticketTypes = s.ticketTypes.map((t) => {
        const occupied = db.orders.filter(
          (o) => o.ticketTypeId === t.id && occupies.includes(o.status),
        ).length;
        const remaining = Math.max(0, t.capacity - occupied);
        const salesStatus = terminated(event)
          ? 'TERMINATED'
          : Date.now() >= Date.parse(s.endsAt)
            ? 'ENDED'
            : Date.now() >= Date.parse(s.registrationClosesAt)
              ? 'CLOSED'
              : Date.now() < Date.parse(s.salesOpensAt)
                ? 'NOT_OPEN'
                : t.saleStopped
                  ? 'STOPPED'
                  : remaining === 0
                    ? 'SOLD_OUT'
                    : 'ON_SALE';
        return { ...t, occupied, remaining, salesStatus };
      });
      const remaining = ticketTypes.reduce((n, t) => n + t.remaining, 0);
      return {
        ...s,
        eventName: event.name,
        categoryNameSnapshot: event.categoryNameSnapshot,
        posterUrl: event.posterUrl,
        ticketTypes,
        remaining,
        sessionSalesStatus: ticketTypes.some((t) => t.salesStatus === 'ON_SALE')
          ? 'ON_SALE'
          : ticketTypes[0]?.salesStatus || 'NOT_OPEN',
      };
    }),
  };
}
function metadata(event: CampusEvent) {
  const decorated = decorate(event);
  return {
    ...decorated,
    sessions: decorated.sessions.map(({ remaining: _remaining, ...s }) => ({
      ...s,
      ticketTypes: s.ticketTypes.map(
        ({ remaining: _r, occupied: _o, salesStatus: _ss, ...t }) => t,
      ),
      checkerCount: db.accounts.filter(
        (a) => a.assignedSessionId === s.id && a.enabled && !a.deletedAt,
      ).length,
    })),
  };
}
function page<T>(items: T[], query: URLSearchParams) {
  const pageNumber = Number(query.get('page') || 1),
    size = Number(query.get('pageSize') || 20);
  if (
    !Number.isInteger(pageNumber) ||
    pageNumber < 1 ||
    !Number.isInteger(size) ||
    size < 1 ||
    size > 100
  )
    fail('VALIDATION_FAILED', '分页参数无效', 422);
  return {
    items: items.slice((pageNumber - 1) * size, pageNumber * size),
    page: pageNumber,
    pageSize: size,
    total: items.length,
  };
}
function log(action: string, objectType: string, objectId: string) {
  const u = account(false);
  db.logs.unshift({
    id: uid(),
    actorId: u?.id || null,
    actorRole: u?.role || 'SYSTEM',
    action,
    objectType,
    objectId,
    result: 'SUCCESS',
    failureReason: null,
    createdAt: now(),
    traceId: uid(),
  });
}
function message(
  userId: string,
  title: string,
  body: string,
  resourceId?: string,
  resourceType = 'ORDER',
) {
  db.messages.unshift({
    id: uid(),
    userId,
    type: 'BUSINESS_RESULT',
    title,
    body,
    createdAt: now(),
    expiresAt: plus(180 * 86400000),
    readAt: null,
    target: resourceId ? { resourceId, resourceType } : null,
    actionDeadline: null,
  });
}
function mutate<T>(current: T) {
  return { operationId: uid(), operationResult: 'SUCCESS', replayed: false, current };
}
function ticket(order: StoredOrder): Ticket {
  const status = ['REFUNDED', 'ABSENT'].includes(order.status)
    ? 'INVALID'
    : ['CHECKED_IN', 'COMPLETED'].includes(order.status)
      ? 'CHECKED'
      : 'VALID';
  return {
    id: order.ticketId!,
    orderId: order.id,
    status,
    attendeeName: order.attendeeNameSnapshot,
    event: orderView(order).event,
    ticketTypeName: order.ticketTypeName,
    paidAmountJiao: order.amountJiao,
    checkedAt: order.checkedAt,
    usable: status === 'VALID' && !terminated(eventById(order.event.eventId)),
    invalidReason:
      status === 'INVALID'
        ? '票据已失效，不可再次验票'
        : status === 'CHECKED'
          ? '已验票，不可重复入场'
          : null,
  };
}
function promote() {
  for (const event of db.events.filter((e) => e.status === 'PUBLISHED' && !terminated(e)))
    for (const s of event.sessions) {
      if (Date.now() < Date.parse(s.salesOpensAt) || Date.now() >= checkinCloseAt(s)) continue;
      for (const type of decorate(event).sessions.find((x) => x.id === s.id)!.ticketTypes) {
        if (type.saleStopped) continue;
        let left = type.remaining;
        const waiting = db.orders
          .filter((o) => o.ticketTypeId === type.id && o.status === 'WAITLISTED')
          .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id));
        for (const o of waiting) {
          if (!left) break;
          const user = db.accounts.find((a) => a.id === o.userId);
          if (
            user?.restrictions.some(
              (r) => r.kind === 'REGISTER' && Date.parse(r.endsAt) > Date.now(),
            )
          )
            continue;
          o.status = 'OFFERED';
          o.confirmDeadline = new Date(
            Math.min(Date.now() + 12 * 3600000, checkinCloseAt(s)),
          ).toISOString();
          o.queuePosition = null;
          message(
            o.userId,
            '候补成功，请确认名额',
            `${event.name} 已为你保留名额，请在截止前确认。`,
            o.id,
          );
          left--;
        }
      }
    }
}
function tick() {
  for (const o of db.orders) {
    if (
      (o.status === 'UNPAID' && o.payDeadline && Date.now() >= Date.parse(o.payDeadline)) ||
      (o.status === 'OFFERED' &&
        o.confirmDeadline &&
        Date.now() >= Date.parse(o.confirmDeadline)) ||
      (['WAITLISTED', 'OFFERED', 'UNPAID'].includes(o.status) &&
        Date.now() >=
          checkinCloseAt(
            db.events
              .find((e) => e.id === o.event.eventId)!
              .sessions.find((s) => s.id === o.event.sessionId)!,
          ))
    ) {
      o.status = 'CANCELLED';
      o.statusReason = '超过操作截止时间';
    }
  }
  promote();
}
function validateVersion(event: CampusEvent, value: unknown) {
  if (Number(value) !== event.version) fail('VERSION_CONFLICT', '活动已被修改，请刷新后重试');
}
function validateDraft(event: CampusEvent) {
  if (
    !event.name.trim() ||
    (!event.categoryId && !event.proposedCategoryName?.trim()) ||
    !event.sessions.length ||
    event.sessions.length > 20
  )
    fail('VALIDATION_FAILED', '请填写活动名称、有效类别或新类别，并添加1～20个场次', 422);
  const category = event.categoryId
    ? db.categories.find((c) => c.id === event.categoryId && c.enabled)
    : null;
  if (event.categoryId && !category) fail('VALIDATION_FAILED', '请选择有效类别', 422);
  for (const s of event.sessions) {
    if (
      !s.name ||
      !s.location ||
      !s.startsAt ||
      !s.endsAt ||
      Date.parse(s.endsAt) <= Date.parse(s.startsAt)
    )
      fail('VALIDATION_FAILED', '请完整填写场次名称、地点和起止时间', 422);
    if (
      Date.parse(s.salesOpensAt) >= Date.parse(s.registrationClosesAt) ||
      Date.parse(s.registrationClosesAt) > Date.parse(s.startsAt)
    )
      fail('VALIDATION_FAILED', '请满足放票开始 < 报名截止 ≤ 场次开始', 422);
    if (
      s.ticketTypes.length < 1 ||
      s.ticketTypes.length > 10 ||
      s.ticketTypes.reduce((n, t) => n + t.capacity, 0) > s.capacity
    )
      fail('VALIDATION_FAILED', '票种容量总和不得超过场次容量，每场1～10个票种', 422);
  }
}
function terminate(event: CampusEvent, status: 'CANCELLED' | 'TAKEN_DOWN', reason: string) {
  event.status = status;
  event.version = (event.version || 1) + 1;
  event.terminationReason = reason;
  event.expiresAt = plus(7 * 86400000);
  event.settlementStatus = 'COMPLETED';
  for (const o of db.orders.filter((o) => o.event.eventId === event.id)) {
    if (['WAITLISTED', 'OFFERED', 'UNPAID'].includes(o.status)) o.status = 'CANCELLED';
    else if (o.status === 'REGISTERED') {
      o.status = 'REFUNDED';
      recordRefund(o, 'PLATFORM_TERMINATION', reason);
    } else if (o.status === 'CHECKED_IN') o.status = 'COMPLETED';
    message(o.userId, '活动已终止', `${event.name}：${reason}。请查看订单最新状态。`, o.id);
  }
  for (const a of db.accounts.filter(
    (a) => a.role === 'CHECKER' && event.sessions.some((s) => s.id === a.assignedSessionId),
  )) {
    a.enabled = false;
    message(a.id, '活动已终止', reason);
  }
  message(event.organizerId!, '活动已终止', reason, event.id, 'EVENT');
  const job = {
    id: uid(),
    type: 'TERMINATION_SETTLEMENT',
    eventId: event.id,
    status: 'SUCCEEDED',
    dueAt: now(),
    deadlineAt: plus(300000),
    completedAt: now(),
    total: db.orders.filter((o) => o.event.eventId === event.id).length,
    processed: db.orders.filter((o) => o.event.eventId === event.id).length,
    retryCount: 0,
    overdue: false,
    lastError: null,
  };
  db.jobs.unshift(job);
  log(status, 'EVENT', event.id);
  return {
    operationId: uid(),
    replayed: false,
    current: metadata(event),
    effectiveAt: now(),
    jobId: job.id,
    settlementStatus: 'COMPLETED',
  };
}
function recordRefund(o: StoredOrder, source: string, reason: string, attemptId: string = uid()) {
  const request = {
    id: uid(),
    orderId: o.id,
    source,
    status: 'SUCCEEDED',
    reason,
    acceptedAt: now(),
    initialAttemptId: attemptId,
  };
  db.refunds.push(request);
  const attempt = {
    id: uid(),
    requestId: request.id,
    orderId: o.id,
    result: 'SUCCESS',
    amountJiao: o.amountJiao,
    createdAt: now(),
  };
  db.refundAttempts.push(attempt);
  return { operationId: uid(), request, attempt, replayed: false, current: orderView(o) };
}

// This adapter is a local UI demonstrator, not the future Java authorization/database layer.
export async function mockRequest<T>(
  method: string,
  path: string,
  payload?: unknown,
  key?: string,
): Promise<Result<T>> {
  await new Promise((resolve) => setTimeout(resolve, 150));
  db = load();
  tick();
  const before = structuredClone(db);
  const url = new URL(path, 'http://demo.local');
  const p = url.pathname.replace(/^\/api/, '');
  const q = url.searchParams;
  const body = (payload || {}) as Record<string, unknown>;
  const text = (name: string) => String(body[name] ?? '');
  const mutationKey = key && `${account(false)?.id}:${method}:${p}:${key}`;
  if (mutationKey && db.mutations[mutationKey]) {
    const previous = db.mutations[mutationKey]!;
    if (previous.fingerprint !== JSON.stringify(body))
      fail('IDEMPOTENCY_CONFLICT', '同一操作编号不能用于不同参数');
    return {
      code: 'OK',
      message: '已返回原操作结果',
      data: structuredClone(previous.result) as T,
      traceId: uid(),
      serverTime: now(),
    };
  }
  try {
    const data = handle();
    if (mutationKey && !['GET'].includes(method))
      db.mutations[mutationKey] = {
        fingerprint: JSON.stringify(body),
        result: structuredClone(data),
      };
    save();
    return {
      code: 'OK',
      message: '操作成功（前端模拟）',
      data: data as T,
      traceId: uid(),
      serverTime: now(),
    };
  } catch (error) {
    db = before;
    save();
    throw error;
  }

  function handle(): unknown {
    if (p === '/auth/csrf') return { token: 'local-preview', headerName: 'X-CSRF-TOKEN' };
    const codeScope = (purpose: string) => `${text('phone')}:${text('role')}:${purpose}`;
    function verifyCode(
      purpose: string,
      consume: boolean,
      phone = text('phone'),
      role = text('role'),
    ) {
      const key = `${phone}:${role}:${purpose}`,
        c = phoneChallenges.get(key);
      if (!c || c.id !== text('verificationId') || c.expires <= Date.now())
        fail('PHONE_CODE_EXPIRED', '验证码已过期、已使用或与手机号及角色不匹配', 422);
      if (c.code !== text('verificationCode')) {
        if (--c.attempts <= 0) phoneChallenges.delete(key);
        fail('PHONE_CODE_INVALID', '验证码错误', 422);
      }
      if (consume) phoneChallenges.delete(key);
    }
    if (p === '/auth/phone-codes') {
      if (
        !/^1\d{10}$/.test(text('phone')) ||
        !['PARTICIPANT', 'ORGANIZER'].includes(text('role')) ||
        !['LOGIN', 'REGISTER', 'RESET_PASSWORD', 'RECOVERY_CONTACT', 'CHANGE_PHONE'].includes(
          text('purpose'),
        )
      )
        fail('VALIDATION_FAILED', '手机号、角色或用途无效', 422);
      if (text('purpose') === 'CHANGE_PHONE') {
        const current = requireRole('PARTICIPANT', 'ORGANIZER');
        if (current.role !== text('role')) fail('FORBIDDEN', '角色与当前账号不符', 403);
        if (current.phone === text('phone')) fail('PHONE_UNCHANGED', '新手机号与当前相同');
        if (
          db.accounts.some(
            (a) => !a.deletedAt && a.role === current.role && a.phone === text('phone'),
          )
        )
          fail('PHONE_ALREADY_BOUND', '该手机号已被同角色的其他账号绑定');
      }
      const cooldown = `${text('phone')}:${text('role')}`;
      if ((phoneCooldowns.get(cooldown) || 0) > Date.now())
        fail('TOO_MANY_REQUESTS', '请60秒后重试', 429);
      const code = String(crypto.getRandomValues(new Uint32Array(1))[0]! % 1000000).padStart(
        6,
        '0',
      );
      const id = uid(),
        expires = Date.now() + 300000;
      phoneChallenges.set(codeScope(text('purpose')), { id, code, expires, attempts: 5 });
      phoneCooldowns.set(cooldown, Date.now() + 60000);
      console.info(
        `[LOCAL_PHONE_CODE] mock phone=${text('phone')} role=${text('role')} purpose=${text('purpose')} code=${code}`,
      );
      return {
        verificationId: id,
        expiresAt: new Date(expires).toISOString(),
        resendAfterSeconds: 60,
        delivery: 'MOCK_CONSOLE',
      };
    }
    if (p === '/auth/login' || p === '/auth/phone-login') {
      if (p === '/auth/phone-login') {
        if (
          !['PARTICIPANT', 'ORGANIZER'].includes(text('role')) ||
          !['LOGIN', 'REGISTER'].includes(text('purpose'))
        )
          fail('VALIDATION_FAILED', '手机号登录角色或用途无效', 422);
        verifyCode(text('purpose'), true);
      }
      const user = db.accounts.find(
        (a) =>
          !a.deletedAt &&
          (p === '/auth/phone-login'
            ? a.phone === text('phone') && a.role === text('role')
            : a.username.toLowerCase() === text('username').trim().toLowerCase() &&
              a.password === text('password')),
      );
      if (!user) fail('INVALID_CREDENTIALS', '用户名或密码不正确', 401);
      if (!user.enabled || user.deletedAt) fail('ACCOUNT_UNAVAILABLE', '账号不可用', 403);
      const token = uid();
      db.accountSessions[user.id] = token;
      user.sessionExpiresAt = plus(7 * 86400000);
      sessionStorage.setItem(SESSION_KEY, JSON.stringify({ id: user.id, token }));
      account();
      return userView(user);
    }
    if (p === '/auth/logout') {
      const user = account(false);
      if (user) delete db.accountSessions[user.id];
      sessionStorage.removeItem(SESSION_KEY);
      return { loggedOut: true };
    }
    if (p === '/auth/register') {
      if (!['PARTICIPANT', 'ORGANIZER'].includes(text('role')))
        fail('VALIDATION_FAILED', '仅开放参与者和组织者注册', 422);
      if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,72}$/.test(text('password')))
        fail('VALIDATION_FAILED', '密码须为8～72位字母和数字组合', 422);
      if (
        !/^1\d{10}$/.test(text('phone')) ||
        (!!text('email').trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text('email').trim())) ||
        !text('username').trim()
      )
        fail('VALIDATION_FAILED', '请填写有效的用户名、邮箱和手机号', 422);
      verifyCode('REGISTER', false);
      if (
        db.accounts.some(
          (a) => !a.deletedAt && a.role === text('role') && a.phone === text('phone'),
        )
      )
        fail('PHONE_ACCOUNT_EXISTS', '该手机号已有当前角色的账号，这是你的账号吗？', 409);
      if (
        db.accounts.some(
          (a) =>
            !a.deletedAt &&
            (a.username.toLowerCase() === text('username').trim().toLowerCase() ||
              a.email?.toLowerCase() === text('email').trim().toLowerCase()),
        )
      )
        fail('VALIDATION_FAILED', '用户名或邮箱已被使用', 422);
      verifyCode('REGISTER', true);
      const user: DemoAccount = {
        id: uid(),
        username: text('username').trim(),
        password: text('password'),
        role: text('role') as Role,
        displayName: null,
        email: text('email').trim() || null,
        maskedPhone: `${text('phone').slice(0, 3)}****${text('phone').slice(-4)}`,
        phone: text('phone'),
        mustChangePassword: false,
        sessionExpiresAt: plus(7 * 86400000),
        restrictions: [],
        checkerSessionId: null,
        assignedSessionId: null,
        enabled: true,
        deletedAt: null,
        version: 1,
      };
      db.accounts.push(user);
      message(user.id, '欢迎加入', '欢迎发现属于你的现场。');
      return { id: user.id, username: user.username, role: user.role };
    }
    if (p === '/auth/password-reset') {
      if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,72}$/.test(text('newPassword')))
        fail('VALIDATION_FAILED', '新密码须为8～72位字母数字组合', 422);
      verifyCode('RESET_PASSWORD', true);
      const target = db.accounts.find(
        (a) => !a.deletedAt && a.phone === text('phone') && a.role === text('role'),
      );
      if (!target) fail('PHONE_NOT_REGISTERED', '该手机号尚未绑定当前角色的账号', 404);
      if (!target.enabled) fail('ACCOUNT_UNAVAILABLE', '账号不可用', 403);
      target.password = text('newPassword');
      target.mustChangePassword = false;
      delete db.accountSessions[target.id];
      sessionStorage.removeItem(SESSION_KEY);
      return { changed: true, requiresLogin: true };
    }
    if (p === '/auth/recoveries') {
      if (
        !text('account').trim() ||
        !/^1\d{10}$/.test(text('phone')) ||
        !/^1\d{10}$/.test(text('currentPhone')) ||
        !['PARTICIPANT', 'ORGANIZER'].includes(text('role')) ||
        [...text('statement').trim()].length < 20 ||
        [...text('pastBehavior').trim()].length < 20
      )
        fail(
          'VALIDATION_FAILED',
          '请填写原手机号、用户名、当前手机号及至少20字的陈述和以往行为',
          422,
        );
      verifyCode('RECOVERY_CONTACT', true, text('currentPhone'));
      const target = db.accounts.find(
        (a) =>
          !a.deletedAt &&
          a.username.toLowerCase() === text('account').trim().toLowerCase() &&
          a.phone === text('phone') &&
          a.role === text('role'),
      );
      const request: Recovery = {
        id: uid(),
        account: text('account'),
        phone: text('phone'),
        targetAccountId: target?.id || null,
        status: 'PENDING',
        createdAt: now(),
        verificationNote: null,
        resolvedAt: null,
        currentPhone: text('currentPhone'),
        role: text('role'),
        statement: text('statement'),
        pastBehavior: text('pastBehavior'),
        contactVerifiedAt: now(),
        historicalHints: target
          ? db.orders
              .filter((o) => o.userId === target.id)
              .slice(0, 5)
              .map((o) => `本人订单：${o.event.eventName} / ${o.status}`)
          : [],
      };
      db.recoveries.push(request);
      return {
        requestId: request.id,
        status: 'RECEIVED',
        message: '详细申诉已提交，等待管理员审核',
      };
    }
    if (p.startsWith('/public/')) {
      if (p === '/public/categories')
        return db.categories.filter(
          (c) =>
            c.enabled ||
            (q.get('scope') !== 'EDIT' &&
              db.events.some(
                (e) => e.categoryId === c.id && e.status === 'PUBLISHED' && !terminated(e),
              )),
        );
      if (p === '/public/sessions') {
        let sessions = db.events
          .filter((e) => e.status === 'PUBLISHED' && !terminated(e))
          .flatMap((e) => decorate(e).sessions)
          .filter((s) => Date.parse(s.endsAt) > Date.now());
        for (const [param, accessor] of [
          ['eventName', (s: Session) => s.eventName],
          ['location', (s: Session) => s.location],
        ] as const)
          if (q.get(param))
            sessions = sessions.filter((s) =>
              accessor(s).toLowerCase().includes(q.get(param)!.toLowerCase()),
            );
        if (q.get('categoryId'))
          sessions = sessions.filter((s) => s.categoryId === q.get('categoryId'));
        if (q.get('startsFrom'))
          sessions = sessions.filter(
            (s) => Date.parse(s.startsAt) >= Date.parse(q.get('startsFrom')!),
          );
        if (q.get('startsTo'))
          sessions = sessions.filter(
            (s) => Date.parse(s.startsAt) <= Date.parse(q.get('startsTo')!),
          );
        if (q.get('salesStatus'))
          sessions = sessions.filter((s) => s.sessionSalesStatus === q.get('salesStatus'));
        sessions = sessions
          .map((s) => ({
            ...s,
            matchedTicketTypeIds: s.ticketTypes
              .filter(
                (t) =>
                  (!q.get('ticketTypeName') || t.name.includes(q.get('ticketTypeName')!)) &&
                  (!q.has('minPriceJiao') || t.priceJiao >= Number(q.get('minPriceJiao'))) &&
                  (!q.has('maxPriceJiao') || t.priceJiao <= Number(q.get('maxPriceJiao'))),
              )
              .map((t) => t.id),
          }))
          .filter((s) => s.matchedTicketTypeIds.length > 0);
        const direction = q.get('sortDirection') === 'desc' ? -1 : 1;
        sessions.sort(
          (a, b) =>
            (q.get('sortBy') === 'remaining'
              ? a.remaining - b.remaining
              : a.startsAt.localeCompare(b.startsAt)) * direction || a.id.localeCompare(b.id),
        );
        return page(sessions, q);
      }
      const id = p.split('/')[3]!;
      const event = eventById(id);
      if (event.status !== 'PUBLISHED' || terminated(event))
        fail('EVENT_NOT_PUBLIC', '活动尚未公开或已经终止');
      return decorate(event);
    }
    const user = account()!;
    if (user.mustChangePassword && !['/me', '/me/password'].includes(p))
      fail('PASSWORD_CHANGE_REQUIRED', '请先完成密码修改', 403);
    if (p === '/me') return userView(user);
    if (p === '/me/profile') {
      user.displayName =
        text('displayName').trim() || fail('VALIDATION_FAILED', '请输入本人姓名', 422);
      return mutate(userView(user));
    }
    if (p === '/me/phone') {
      requireRole('PARTICIPANT', 'ORGANIZER');
      if (user.phone === text('newPhone')) fail('PHONE_UNCHANGED', '新手机号与当前相同');
      if (
        db.accounts.some(
          (a) =>
            !a.deletedAt &&
            a.id !== user.id &&
            a.role === user.role &&
            a.phone === text('newPhone'),
        )
      )
        fail('PHONE_ALREADY_BOUND', '该手机号已被同角色其他账号绑定');
      verifyCode('CHANGE_PHONE', true, text('newPhone'), user.role);
      user.phone = text('newPhone');
      user.maskedPhone = user.phone.slice(0, 3) + '****' + user.phone.slice(-4);
      user.version++;
      delete db.accountSessions[user.id];
      sessionStorage.removeItem(SESSION_KEY);
      return { maskedPhone: user.maskedPhone, requiresLogin: true };
    }
    if (p === '/me/password') {
      if (text('oldPassword') !== user.password) fail('VALIDATION_FAILED', '原密码不正确', 422);
      if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,72}$/.test(text('newPassword')))
        fail('VALIDATION_FAILED', '新密码须为8～72位字母数字组合', 422);
      user.password = text('newPassword');
      user.mustChangePassword = false;
      delete db.accountSessions[user.id];
      sessionStorage.removeItem(SESSION_KEY);
      return { changed: true, requiresLogin: true };
    }
    if (p.startsWith('/me/closure')) {
      requireRole('PARTICIPANT', 'ORGANIZER');
      const blockers = db.orders
        .filter((o) => o.userId === user.id && active.includes(o.status))
        .map((o) => ({
          code: 'ACTIVE_ORDER',
          message: `尚有${orderView(o).event.eventName}的未结订单`,
          objectType: 'ORDER',
          objectId: o.id,
        }));
      if (user.role === 'ORGANIZER')
        db.events
          .filter(
            (e) =>
              e.organizerId === user.id &&
              ['PUBLISHED', 'PENDING_REVIEW'].includes(e.status!) &&
              !terminated(e),
          )
          .forEach((e) =>
            blockers.push({
              code: 'ACTIVE_EVENT',
              message: `${e.name}尚未结束`,
              objectType: 'EVENT',
              objectId: e.id,
            }),
          );
      if (p === '/me/closure-eligibility')
        return {
          eligible: blockers.length === 0,
          blockers,
          cancellationCount: 0,
          lastCancelledAt: null,
          nextEligibleAt: null,
          willBlacklist: false,
        };
      if (blockers.length) fail('ACCOUNT_CLOSE_BLOCKED', blockers[0]!.message);
      if (text('password') !== user.password) fail('VALIDATION_FAILED', '密码不正确', 422);
      user.deletedAt = now();
      delete db.accountSessions[user.id];
      sessionStorage.removeItem(SESSION_KEY);
      return { closed: true, requiresLogin: true };
    }
    if (p === '/me/registrations') {
      requireRole('PARTICIPANT');
      const existingRequest = db.orders.find(
        (o) => o.userId === user.id && o.requestId === text('requestId'),
      );
      if (existingRequest) {
        if (
          existingRequest.event.sessionId !== text('sessionId') ||
          existingRequest.ticketTypeId !== text('ticketTypeId') ||
          existingRequest.attendeeNameSnapshot !== text('attendeeName')
        )
          fail('IDEMPOTENCY_CONFLICT', '报名编号已用于不同参数');
        return {
          operationId: existingRequest.requestId,
          operationResult: 'CREATED',
          replayed: true,
          current: orderView(existingRequest),
        };
      }
      const { event, session } = sessionById(text('sessionId'));
      const type =
        decorate(event)
          .sessions.find((s) => s.id === session.id)!
          .ticketTypes.find((t) => t.id === text('ticketTypeId')) ||
        fail('VALIDATION_FAILED', '票种不属于当前场次', 422);
      if (event.status !== 'PUBLISHED' || terminated(event))
        fail('EVENT_TERMINATED', '活动不可报名');
      if (Date.now() < Date.parse(session.salesOpensAt)) fail('SALES_NOT_OPEN', '尚未开售');
      if (Date.now() >= Date.parse(session.registrationClosesAt))
        fail('REGISTRATION_CLOSED', '报名已截止');
      if (type.saleStopped) fail('SALE_STOPPED', '该票种已停售');
      if (user.restrictions.some((r) => r.kind === 'REGISTER' && Date.parse(r.endsAt) > Date.now()))
        fail('REGISTRATION_RESTRICTED', '当前账号处于新报名限制期');
      const same = db.orders.find(
        (o) =>
          o.userId === user.id && o.event.sessionId === session.id && active.includes(o.status),
      );
      if (same) {
        if (same.ticketTypeId !== type.id)
          fail('ACTIVE_ORDER_EXISTS', '本场已有有效订单，请先处理原订单');
        return {
          operationId: uid(),
          operationResult: 'EXISTING_ACTIVE_ORDER',
          replayed: false,
          current: orderView(same),
        };
      }
      if (
        db.orders.some(
          (o) =>
            o.userId === user.id &&
            active.includes(o.status) &&
            Date.parse(o.event.startsAt) < Date.parse(session.endsAt) &&
            Date.parse(o.event.endsAt) > Date.parse(session.startsAt),
        )
      )
        fail('SESSION_TIME_CONFLICT', '与已有报名场次时间冲突');
      if (!text('attendeeName').trim()) fail('VALIDATION_FAILED', '请确认本人姓名', 422);
      const status = type.remaining > 0 ? 'UNPAID' : 'WAITLISTED';
      const order: StoredOrder = {
        id: uid(),
        requestId: text('requestId'),
        userId: user.id,
        status,
        statusReason: null,
        createdAt: now(),
        attendeeNameSnapshot: text('attendeeName').trim(),
        amountJiao: type.priceJiao,
        event: {
          eventId: event.id,
          eventName: event.name,
          categoryNameSnapshot: event.categoryNameSnapshot,
          eventStatus: event.status!,
          sessionId: session.id,
          sessionName: session.name,
          startsAt: session.startsAt,
          endsAt: session.endsAt,
          location: session.location,
          terminationReason: null,
          terminatedAt: null,
        },
        ticketTypeId: type.id,
        ticketTypeName: type.name,
        payDeadline:
          status === 'UNPAID'
            ? new Date(
                Math.min(Date.now() + 900000, Date.parse(session.registrationClosesAt)),
              ).toISOString()
            : null,
        confirmDeadline: null,
        queuePosition:
          status === 'WAITLISTED'
            ? db.orders.filter((o) => o.ticketTypeId === type.id && o.status === 'WAITLISTED')
                .length + 1
            : null,
        consecutivePaymentFailures: 0,
        ticketId: null,
        activeRefundRequestId: null,
        allowedActions: [],
        checkedAt: null,
        enteredWaitlist: status === 'WAITLISTED',
      };
      db.orders.unshift(order);
      log('REGISTER', 'ORDER', order.id);
      return {
        operationId: order.requestId,
        operationResult: 'CREATED',
        replayed: false,
        current: orderView(order),
      };
    }
    if (p.startsWith('/me/registration-requests/')) {
      const o =
        db.orders.find((o) => o.userId === user.id && o.requestId === p.split('/').at(-1)) ||
        fail('RESOURCE_NOT_FOUND', '未找到已受理请求', 404);
      return {
        operationId: o.requestId,
        operationResult: 'CREATED',
        replayed: true,
        current: orderView(o),
      };
    }
    if (p === '/me/orders') {
      requireRole('PARTICIPANT');
      return page(
        db.orders
          .filter((o) => o.userId === user.id && (!q.get('status') || o.status === q.get('status')))
          .map(orderView),
        q,
      );
    }
    if (p.startsWith('/me/orders/')) {
      const o = ownOrder(p.split('/')[3]!);
      const view = orderView(o);
      const action = p.split('/')[4];
      if (!action) return view;
      if (action === 'queue')
        return {
          orderId: o.id,
          status: o.status,
          position: o.queuePosition,
          confirmDeadline: o.confirmDeadline,
          payDeadline: o.payDeadline,
          temporarilySkipped: false,
          skipReason: null,
        };
      if (action === 'cancel') {
        if (!view.allowedActions.includes('CANCEL'))
          fail('STATE_CONFLICT', '当前状态或时间不允许取消');
        o.status = 'CANCELLED';
        o.statusReason = '本人主动取消';
        log('CANCEL', 'ORDER', o.id);
        message(user.id, '订单已取消', o.event.eventName, o.id);
        promote();
        return mutate(orderView(o));
      }
      if (action === 'promotion-confirmation') {
        if (!view.allowedActions.includes('CONFIRM_PROMOTION'))
          fail('PROMOTION_EXPIRED', '递补确认不可用');
        o.status = 'UNPAID';
        o.payDeadline = new Date(
          Math.min(
            Date.now() + 900000,
            Date.parse(sessionById(o.event.sessionId).session.registrationClosesAt),
          ),
        ).toISOString();
        return mutate(orderView(o));
      }
      if (action === 'payment-attempts') {
        const old = db.payments.find(
          (a) =>
            a.orderId === o.id && (a.attemptId === text('attemptId') || a.result === 'SUCCESS'),
        );
        if (old)
          return {
            operationId: old.id,
            attempt: old,
            replayed: true,
            current: orderView(o),
            ticket: o.ticketId ? ticket(o) : null,
          };
        if (!view.allowedActions.includes('PAY'))
          fail('PAYMENT_EXPIRED', '订单不可支付，请查看最新状态');
        const attempt = {
          id: uid(),
          orderId: o.id,
          attemptId: text('attemptId'),
          result: 'SUCCESS',
          amountJiao: o.amountJiao,
          createdAt: now(),
          failureReason: null,
        };
        db.payments.push(attempt);
        o.status = 'REGISTERED';
        o.ticketId = uid();
        log('PAY', 'ORDER', o.id);
        message(user.id, '报名成功，电子票已生成', o.event.eventName, o.id);
        return {
          operationId: attempt.id,
          attempt,
          replayed: false,
          current: orderView(o),
          ticket: ticket(o),
        };
      }
      if (action === 'payments')
        return {
          paymentAttempts: db.payments.filter((a) => a.orderId === o.id),
          refundRequests: db.refunds.filter((r) => r.orderId === o.id),
          refundAttempts: db.refundAttempts.filter((r) => r.orderId === o.id),
        };
      if (action === 'refund-requests') {
        if (!view.allowedActions.includes('REQUEST_REFUND'))
          fail('REFUND_NOT_ALLOWED', '当前订单不符合退款条件');
        o.status = 'REFUNDED';
        const result = recordRefund(o, 'USER', text('reason'), text('attemptId'));
        log('REFUND', 'ORDER', o.id);
        message(user.id, '退款成功', `已全额模拟退款 ¥${o.amountJiao / 10}`, o.id);
        promote();
        return result;
      }
    }
    if (p.startsWith('/me/tickets/')) {
      const o =
        db.orders.find((o) => o.ticketId === p.split('/')[3]) ||
        fail('RESOURCE_NOT_FOUND', '电子票不存在', 404);
      ownOrder(o.id);
      const t = ticket(o);
      if (p.endsWith('/download'))
        return new Blob(
          [
            `活动票务 · 电子票\n姓名：${t.attendeeName}\n票 id：${t.id}\n活动：${t.event.eventName}\n场次：${t.event.sessionName}\n时间：${t.event.startsAt} — ${t.event.endsAt}\n地点：${t.event.location}\n票种：${t.ticketTypeName}\n实付：${t.paidAmountJiao / 10}元\n状态：${t.status}${t.invalidReason ? '\n' + t.invalidReason : ''}`,
          ],
          { type: 'text/plain;charset=utf-8' },
        );
      return t;
    }
    if (p.startsWith('/me/messages')) {
      const id = p.split('/')[3];
      if (!id)
        return page(
          db.messages.filter(
            (m) =>
              m.userId === user.id &&
              (!q.get('unreadOnly') || q.get('unreadOnly') === 'false' || !m.readAt),
          ),
          q,
        );
      const m =
        db.messages.find((m) => m.id === id && m.userId === user.id) ||
        fail('RESOURCE_NOT_FOUND', '消息不存在', 404);
      if (p.endsWith('/read')) {
        m.readAt ||= now();
        return mutate(m);
      }
      return m;
    }
    if (p.startsWith('/me/reports')) {
      requireRole('PARTICIPANT');
      if (method === 'GET')
        return p.split('/')[3]
          ? db.reports.find((r) => r.id === p.split('/')[3] && r.reporterId === user.id) ||
              fail('RESOURCE_NOT_FOUND', '举报不存在', 404)
          : page(
              db.reports.filter((r) => r.reporterId === user.id),
              q,
            );
      const { event, session } = sessionById(text('sessionId'));
      if (db.reports.some((r) => r.reporterId === user.id && r.sessionId === session.id))
        fail('DUPLICATE_REPORT', '你已举报过该场次，可在列表中查看进度');
      if (
        db.reports.filter(
          (r) => r.reporterId === user.id && Date.parse(r.createdAt) > Date.now() - 30 * 86400000,
        ).length >= 5
      )
        fail('REPORT_LIMIT_REACHED', '30天内受理举报已达5次');
      if (![text('reason'), text('description'), text('evidenceText')].every((t) => t.trim()))
        fail('VALIDATION_FAILED', '请填写举报原因、事实描述和文字证据', 422);
      const report = {
        id: uid(),
        sessionId: session.id,
        eventId: event.id,
        reporterId: user.id,
        reason: text('reason'),
        description: text('description'),
        evidenceText: text('evidenceText'),
        status: 'PENDING',
        createdAt: now(),
        conclusion: null,
        resolvedAt: null,
      };
      db.reports.unshift(report);
      return report;
    }
    if (p.startsWith('/organizer/events')) {
      requireRole('ORGANIZER');
      const id = p.split('/')[3];
      if (!id) {
        if (method === 'GET')
          return page(
            db.events
              .filter(
                (e) =>
                  e.organizerId === user.id && (!q.get('status') || e.status === q.get('status')),
              )
              .map(metadata),
            q,
          );
        const draft: CampusEvent = {
          id: uid(),
          name: '',
          categoryId: '',
          categoryNameSnapshot: '',
          description: '',
          posterUrl: null,
          sessions: [],
          status: 'DRAFT',
          organizerId: user.id,
          version: 1,
          settlementStatus: 'NOT_DUE',
        };
        db.events.unshift(draft);
        return metadata(draft);
      }
      const event = ownEvent(id);
      const action = p.split('/')[4];
      if (!action) return metadata(event);
      if (action === 'settlement')
        return {
          eventId: event.id,
          eventStatus: event.status,
          effectiveAt: null,
          settlementStatus: event.settlementStatus,
          job: db.jobs.find((j) => j.eventId === event.id) || null,
        };
      validateVersion(event, body.expectedVersion);
      if (action === 'poster') {
        if (!['DRAFT', 'PUBLISHED'].includes(event.status!) || terminated(event))
          fail('STATE_CONFLICT', '当前状态不允许修改海报');
        const value = text('posterData');
        if (!/^data:image\/(png|jpeg);base64,/.test(value))
          fail('VALIDATION_FAILED', '海报格式不支持', 422);
        event.posterUrl = value;
        event.version = (event.version || 1) + 1;
        return mutate(metadata(event));
      }
      if (action === 'draft') {
        if (event.status !== 'DRAFT') fail('STATE_CONFLICT', '只有草稿可以编辑');
        event.name = text('name');
        event.description = text('description');
        event.categoryId = text('categoryId') || '';
        event.proposedCategoryName = text('proposedCategoryName') || null;
        event.categoryNameSnapshot =
          db.categories.find((c) => c.id === event.categoryId)?.name ||
          event.proposedCategoryName ||
          '';
        event.sessions = ((body.sessions as Session[]) || []).map((s) => ({
          ...s,
          eventId: id,
          eventName: event.name,
          categoryId: event.categoryId,
          categoryNameSnapshot: event.categoryNameSnapshot,
          posterUrl: null,
        }));
      } else if (action === 'submission') {
        validateDraft(event);
        if (event.status !== 'DRAFT') fail('STATE_CONFLICT', '活动不在草稿状态');
        event.status = 'PENDING_REVIEW';
        event.submittedAt = now();
      } else if (action === 'withdrawal') {
        if (event.status !== 'PENDING_REVIEW') fail('STATE_CONFLICT', '活动不在待审核状态');
        event.status = 'DRAFT';
      } else if (action === 'published-settings') {
        if (event.status !== 'PUBLISHED' || terminated(event))
          fail('STATE_CONFLICT', '活动不能修改');
        if ('description' in body) event.description = text('description');
        for (const change of (body.sessions as Partial<Session>[]) || []) {
          const s = event.sessions.find((s) => s.id === change.id)!;
          if (!s || Date.parse(s.endsAt) <= Date.now())
            fail('STATE_CONFLICT', '场次已结束或不存在');
          if (change.location) s.location = change.location;
          if (change.capacity) s.capacity = change.capacity;
          for (const tc of change.ticketTypes || []) {
            const t = s.ticketTypes.find((t) => t.id === tc.id)!;
            const occupied = db.orders.filter(
              (o) => o.ticketTypeId === t.id && occupies.includes(o.status),
            ).length;
            if (tc.capacity !== undefined && tc.capacity < occupied)
              fail('CAPACITY_BELOW_OCCUPIED', '票种容量不能低于已占名额');
            Object.assign(t, tc);
          }
          if (s.ticketTypes.reduce((n, t) => n + t.capacity, 0) > s.capacity)
            fail('VALIDATION_FAILED', '票种容量总和超过场次容量', 422);
        }
      } else if (action === 'cancellation') {
        if (
          event.status !== 'PUBLISHED' ||
          event.sessions.some((s) => Date.parse(s.startsAt) <= Date.now()) ||
          db.orders.some((o) => o.event.eventId === id && o.checkedAt)
        )
          fail('STATE_CONFLICT', '仅首场开始前且全活动无签到时可取消');
        return terminate(event, 'CANCELLED', text('reason'));
      } else fail('NOT_IMPLEMENTED', '此操作将在Java后端阶段接入', 501);
      event.version = (event.version || 1) + 1;
      log(action!, 'EVENT', id);
      promote();
      return mutate(metadata(event));
    }
    if (p.startsWith('/checker')) {
      requireRole('CHECKER');
      const { event, session } = sessionById(user.assignedSessionId!);
      if (p === '/checker/context')
        return {
          session: decorate(event).sessions.find((s) => s.id === session.id),
          eventStatus: event.status,
          checkinOpensAt: new Date(Date.parse(session.startsAt) - 2 * 3600000).toISOString(),
          checkinClosesAt: new Date(
            Date.parse(session.startsAt) +
              (Date.parse(session.endsAt) - Date.parse(session.startsAt)) / 2,
          ).toISOString(),
          allowedActions: [],
        };
      if (p.endsWith('/absence-reconciliation')) {
        if (Date.now() < Date.parse(session.endsAt))
          fail('STATE_CONFLICT', '场次尚未结束，不能标记缺席');
        return {
          sessionId: session.id,
          processedCount: 0,
          alreadyProcessedCount: 0,
          settlementStatus: 'COMPLETED',
        };
      }
      const o =
        db.orders.find((o) => o.ticketId === text('ticketId')) ||
        fail('RESOURCE_NOT_FOUND', '票号不存在，请检查输入', 404);
      if (o.event.sessionId !== user.assignedSessionId)
        fail('OBJECT_FORBIDDEN', '此票不属于当前授权场次', 403);
      const attendee = db.accounts.find((a) => a.id === o.userId);
      if (o.checkedAt)
        return p.endsWith('/checkins')
          ? {
              ticketId: o.ticketId,
              orderId: o.id,
              checkedAt: o.checkedAt,
              checkerId: user.id,
              sessionId: session.id,
              replayed: true,
              orderStatus: o.status,
              ticketStatus: 'CHECKED',
            }
          : {
              ticketId: o.ticketId,
              userId: o.userId,
              username: attendee?.username || '—',
              attendeeName: o.attendeeNameSnapshot,
              phone: attendee?.phone || null,
              sessionId: session.id,
              sessionName: session.name,
              ticketTypeName: o.ticketTypeName,
              ticketStatus: 'CHECKED',
              alreadyChecked: true,
              checkedAt: o.checkedAt,
              canConfirm: false,
            };
      if (o.status !== 'REGISTERED') fail('TICKET_INVALID', '电子票已失效');
      const checkinOpen = Date.parse(session.startsAt) - 2 * 3600000;
      const checkinClose =
        Date.parse(session.startsAt) +
        (Date.parse(session.endsAt) - Date.parse(session.startsAt)) / 2;
      if (Date.now() < checkinOpen)
        fail('CHECKIN_NOT_OPEN', '尚未到验票窗口：活动开始前 2 小时开放');
      if (Date.now() >= checkinClose)
        fail('CHECKIN_ENDED', '验票窗口已结束：活动进行到一半时停止验票');
      if (p.endsWith('/ticket-previews'))
        return {
          ticketId: o.ticketId,
          userId: o.userId,
          username: attendee?.username || '—',
          attendeeName: o.attendeeNameSnapshot,
          phone: attendee?.phone || null,
          sessionId: session.id,
          sessionName: session.name,
          ticketTypeName: o.ticketTypeName,
          ticketStatus: 'VALID',
          alreadyChecked: false,
          checkedAt: null,
          canConfirm: true,
        };
      o.status = 'CHECKED_IN';
      o.checkedAt = now();
      log('CHECKIN', 'TICKET', o.ticketId!);
      return {
        ticketId: o.ticketId,
        orderId: o.id,
        checkedAt: o.checkedAt,
        checkerId: user.id,
        sessionId: session.id,
        replayed: false,
        orderStatus: o.status,
        ticketStatus: 'CHECKED',
      };
    }
    if (p.startsWith('/management/exports/')) {
      requireRole('ORGANIZER', 'ADMIN');
      const item =
        db.exports.find((e) => e.id === p.split('/')[3]) ||
        fail('RESOURCE_NOT_FOUND', '导出不存在', 404);
      managedEvent(item.eventId);
      return p.endsWith('/download')
        ? new Blob([item.content], { type: 'text/csv;charset=utf-8' })
        : item;
    }
    if (p.startsWith('/management/events/')) {
      const id = p.split('/')[3]!;
      const event = managedEvent(id, q.get('sessionId'));
      const rows = db.orders.filter(
        (o) =>
          o.event.eventId === id &&
          (!q.get('sessionId') || o.event.sessionId === q.get('sessionId')),
      );
      const action = p.split('/')[4];
      if (action === 'attendees' || action === 'exports') {
        if (action === 'exports') requireRole('ORGANIZER', 'ADMIN');
        const source =
          action === 'exports'
            ? new URLSearchParams(
                Object.entries(body)
                  .filter(([, v]) => v !== '' && v != null)
                  .map(([k, v]) => [k, String(v)]),
              )
            : q;
        const attendees = rows
          .filter(
            (o) =>
              (!source.get('sessionId') || o.event.sessionId === source.get('sessionId')) &&
              (!source.get('ticketTypeId') || o.ticketTypeId === source.get('ticketTypeId')) &&
              (!source.get('orderStatus') || o.status === source.get('orderStatus')) &&
              (!source.get('checkinStatus') ||
                (o.checkedAt ? 'CHECKED' : 'NOT_CHECKED') === source.get('checkinStatus')),
          )
          .map((o) => ({
            attendeeName: o.attendeeNameSnapshot,
            sessionId: o.event.sessionId,
            sessionName: o.event.sessionName,
            ticketTypeId: o.ticketTypeId,
            ticketTypeName: o.ticketTypeName,
            orderStatus: o.status,
            checkinStatus: o.checkedAt ? 'CHECKED' : 'NOT_CHECKED',
            ticketId: o.ticketId,
          }));
        if (action === 'attendees') return page(attendees, q);
        const csv = (value: string | null) =>
          `"${String(value || '')
            .replace(/^[=+\-@]/, "'$&")
            .replaceAll('"', '""')}"`;
        const content =
          '\ufeff姓名,场次,票种,报名状态,签到状态,票码\r\n' +
          attendees
            .map((o) =>
              [
                o.attendeeName,
                o.sessionName,
                o.ticketTypeName,
                o.orderStatus,
                o.checkinStatus,
                o.ticketId,
              ]
                .map(csv)
                .join(','),
            )
            .join('\r\n');
        const idExport = uid();
        const item = {
          id: idExport,
          eventId: event.id,
          status: 'SUCCEEDED',
          rowCount: attendees.length,
          createdAt: now(),
          expiresAt: event.expiresAt || null,
          dataAsOf: now(),
          downloadUrl: `/management/exports/${idExport}/download`,
          content,
        };
        db.exports.push(item);
        log('EXPORT', 'EVENT', event.id);
        return item;
      }
      if (action === 'live-counts') {
        if (terminated(event)) fail('STATE_CONFLICT', '活动已终结，请查看正式报表');
        const checkedIn = rows.filter((o) => o.checkedAt).length;
        if (user.role === 'CHECKER')
          return {
            sessionId: user.assignedSessionId,
            asOf: now(),
            checkedIn,
            settlementStatus: 'NOT_DUE',
          };
        const types = decorate(event).sessions.flatMap((s) =>
          s.ticketTypes.map((t) => ({
            sessionId: s.id,
            ticketTypeId: t.id,
            capacity: t.capacity,
            occupied: t.occupied,
            remaining: t.remaining,
          })),
        );
        return {
          eventId: event.id,
          sessionId: q.get('sessionId'),
          asOf: now(),
          orderCounts: Object.fromEntries(
            [
              'WAITLISTED',
              'OFFERED',
              'UNPAID',
              'REGISTERED',
              'CHECKED_IN',
              'COMPLETED',
              'CANCELLED',
              'REFUNDED',
              'ABSENT',
            ].map((status) => [status, rows.filter((o) => o.status === status).length]),
          ),
          currentWaitlist: rows.filter((o) => o.status === 'WAITLISTED').length,
          cumulativeWaitlistApplications: rows.filter((o) => o.enteredWaitlist).length,
          occupied: types.reduce((n, t) => n + t.occupied, 0),
          remaining: types.reduce((n, t) => n + t.remaining, 0),
          checkedIn,
          settlementStatus: 'NOT_DUE',
          ticketTypeCounts: types,
        };
      }
      if (action === 'statistics') {
        const done = terminated(event);
        const issued = rows.filter((o) => o.ticketId).length,
          refunds = rows.filter((o) => o.status === 'REFUNDED').length,
          checked = rows.filter((o) => o.checkedAt).length,
          absent = rows.filter((o) => o.status === 'ABSENT').length;
        return {
          eventId: event.id,
          settlementStatus: done ? 'COMPLETED' : 'NOT_DUE',
          issuedCount: done ? issued : null,
          refundCount: done ? refunds : null,
          checkedInCount: done ? checked : null,
          absentCount: done ? absent : null,
          attendanceRate: done ? (issued - refunds ? checked / (issued - refunds) : 0) : null,
          absenceRate: done ? (issued - refunds ? absent / (issued - refunds) : 0) : null,
          capacityUtilizationRate: done
            ? checked /
              Math.max(
                1,
                event.sessions.reduce((n, s) => n + s.capacity, 0),
              )
            : null,
          expiresAt: event.expiresAt || null,
          cumulativeWaitlistApplications: done
            ? rows.filter((o) => o.enteredWaitlist).length
            : null,
        };
      }
      if (action === 'payment-summary') {
        requireRole('ORGANIZER', 'ADMIN');
        const ids = rows.map((o) => o.id);
        const pay = db.payments.filter((a) => ids.includes(a.orderId) && a.result === 'SUCCESS'),
          refund = db.refundAttempts.filter((a) => ids.includes(a.orderId));
        return {
          eventId: id,
          successfulPaymentCount: pay.length,
          paidAmountJiao: pay.reduce((n, a) => n + a.amountJiao, 0),
          successfulRefundCount: refund.length,
          refundedAmountJiao: refund.reduce((n, a) => n + a.amountJiao, 0),
          netAmountJiao:
            pay.reduce((n, a) => n + a.amountJiao, 0) -
            refund.reduce((n, a) => n + a.amountJiao, 0),
          asOf: now(),
        };
      }
    }
    if (p.startsWith('/admin/')) {
      requireRole('ADMIN');
      const parts = p.split('/');
      const id = parts[3];
      if (parts[2] === 'events' || parts[2] === 'reviews') {
        if (!id) {
          const result = page(
            db.events
              .filter((e) => parts[2] !== 'reviews' || e.status === 'PENDING_REVIEW')
              .filter((e) => !q.get('status') || e.status === q.get('status'))
              .map(metadata),
            q,
          );
          const today = new Date(Date.now() + 28800000).toISOString().slice(0, 10);
          return {
            ...result,
            dailyPublishedCount: db.events.filter(
              (e) =>
                e.publishedAt &&
                new Date(Date.parse(e.publishedAt) + 28800000).toISOString().startsWith(today),
            ).length,
            dailyLimit: 100,
            day: today,
          };
        }
        const event = eventById(id);
        if (method === 'GET') return metadata(event);
        validateVersion(event, body.expectedVersion);
        if (p.endsWith('/approval')) {
          if (event.status !== 'PENDING_REVIEW') fail('STATE_CONFLICT', '活动已不在待审核状态');
          if (!event.categoryId && event.proposedCategoryName?.trim()) {
            const name = event.proposedCategoryName.trim();
            let category = db.categories.find((c) => c.name === name);
            if (!category) {
              category = { id: uid(), name, enabled: true, version: 1 };
              db.categories.push(category);
            }
            event.categoryId = category.id;
            event.categoryNameSnapshot = name;
          }
          validateDraft(event);
          const noChecker = event.sessions.some(
            (s) =>
              !db.accounts.some((a) => a.assignedSessionId === s.id && a.enabled && !a.deletedAt),
          );
          if (noChecker) {
            event.status = 'DRAFT';
            event.lastReviewReason = '每场须配置至少一个可用检票账号';
            event.version!++;
            message(event.organizerId!, '活动审核退回', event.lastReviewReason, event.id, 'EVENT');
            return {
              operationId: uid(),
              outcome: 'RETURNED_TO_DRAFT',
              reason: event.lastReviewReason,
              replayed: false,
              current: metadata(event),
              dailyPublishedCount: 6,
            };
          }
          event.status = 'PUBLISHED';
          event.version!++;
          event.publishedAt = now();
          message(event.organizerId!, '活动审核通过', event.name, event.id, 'EVENT');
          log('APPROVE', 'EVENT', id);
          return {
            operationId: uid(),
            outcome: 'PUBLISHED',
            reason: null,
            replayed: false,
            current: metadata(event),
            dailyPublishedCount: 7,
          };
        }
        if (p.endsWith('/rejection')) {
          if (!text('reason').trim()) fail('VALIDATION_FAILED', '请填写驳回理由', 422);
          event.status = 'DRAFT';
          event.lastReviewReason = text('reason');
          event.version!++;
          message(event.organizerId!, '活动审核未通过', text('reason'), event.id, 'EVENT');
          return {
            operationId: uid(),
            outcome: 'REJECTED',
            reason: text('reason'),
            replayed: false,
            current: metadata(event),
            dailyPublishedCount: 6,
          };
        }
      }
      if (parts[2] === 'categories') {
        if (method === 'GET') return page(db.categories, q);
        if (method === 'POST') {
          const name = text('name').trim();
          if (!name || db.categories.some((c) => c.name.toLowerCase() === name.toLowerCase()))
            fail('VALIDATION_FAILED', '类别名称为空或已存在', 422);
          const c = { id: uid(), name, enabled: true, version: 1 };
          db.categories.push(c);
          return c;
        }
        const c =
          db.categories.find((c) => c.id === id) || fail('RESOURCE_NOT_FOUND', '类别不存在', 404);
        if (body.name) c.name = text('name');
        if ('enabled' in body) c.enabled = Boolean(body.enabled);
        c.version++;
        return mutate(c);
      }
      if (parts[2] === 'accounts') {
        if (method === 'GET')
          return page(
            db.accounts
              .filter(
                (a) =>
                  (!q.get('role') || a.role === q.get('role')) &&
                  (!q.get('username') ||
                    a.username.toLowerCase().includes(q.get('username')!.toLowerCase())),
              )
              .map((a) => ({
                id: a.id,
                username: a.username,
                role: a.role,
                maskedPhone: a.maskedPhone,
                enabled: a.enabled,
                deletedAt: a.deletedAt,
                mustChangePassword: a.mustChangePassword,
                assignedSessionId: a.assignedSessionId,
                eventId: a.assignedSessionId
                  ? db.events.find((e) => e.sessions.some((s) => s.id === a.assignedSessionId))?.id
                  : null,
                version: a.version,
              })),
            q,
          );
        if (method === 'POST' && !id) {
          if (!['ADMIN', 'CHECKER'].includes(text('role')))
            fail('VALIDATION_FAILED', '仅允许创建管理员或检票员', 422);
          if (
            db.accounts.some(
              (a) => !a.deletedAt && a.username.toLowerCase() === text('username').toLowerCase(),
            )
          )
            fail('VALIDATION_FAILED', '用户名已存在', 422);
          if (text('role') === 'CHECKER') sessionById(text('sessionId'));
          const a: DemoAccount = {
            ...db.accounts[3]!,
            id: uid(),
            username: text('username'),
            password: text('initialPassword'),
            role: text('role') as Role,
            displayName: text('username'),
            mustChangePassword: text('role') === 'ADMIN',
            assignedSessionId: text('sessionId') || null,
            checkerSessionId: text('sessionId') || null,
            enabled: true,
            deletedAt: null,
            version: 1,
          };
          db.accounts.push(a);
          log('CREATE_ACCOUNT', 'USER', a.id);
          return userView(a);
        }
        const a =
          db.accounts.find((a) => a.id === id) || fail('RESOURCE_NOT_FOUND', '账号不存在', 404);
        if (p.endsWith('/password-reset')) {
          a.password = text('initialPassword');
          a.mustChangePassword = true;
          delete db.accountSessions[a.id];
          return { accountId: a.id, reset: true, mustChangePassword: true };
        }
        if (a.deletedAt) fail('ACCOUNT_UNAVAILABLE', '已删除账号不能恢复', 403);
        if (!['ADMIN', 'CHECKER'].includes(a.role))
          fail('FORBIDDEN', '普通用户账号仅支持有依据的密码重置', 403);
        if (
          a.role === 'ADMIN' &&
          db.accounts.filter((a) => a.role === 'ADMIN' && a.enabled && !a.deletedAt).length === 1 &&
          (body.enabled === false || method === 'DELETE')
        )
          fail('LAST_CHECKER_REQUIRED', '不能停用最后一个可用管理员');
        if (
          a.role === 'CHECKER' &&
          (body.enabled === false ||
            method === 'DELETE' ||
            (body.assignedSessionId && body.assignedSessionId !== a.assignedSessionId))
        ) {
          const event = db.events.find((e) => e.sessions.some((s) => s.id === a.assignedSessionId));
          if (
            event?.status === 'PUBLISHED' &&
            !terminated(event) &&
            db.accounts.filter(
              (b) => b.assignedSessionId === a.assignedSessionId && b.enabled && !b.deletedAt,
            ).length === 1
          )
            fail('LAST_CHECKER_REQUIRED', '请先配置替代检票员，不能移走最后一个可用账号');
        }
        if (body.assignedSessionId) {
          const target = sessionById(text('assignedSessionId'));
          if (
            (target.event.status !== 'PENDING_REVIEW' && target.event.status !== 'PUBLISHED') ||
            terminated(target.event) ||
            Date.parse(target.session.endsAt) <= Date.now()
          )
            fail('VALIDATION_FAILED', '只能改绑到待审核或已发布且尚未结束的场次', 422);
        }
        if (method === 'DELETE') {
          a.deletedAt = now();
          a.enabled = false;
        }
        if ('enabled' in body) a.enabled = Boolean(body.enabled);
        if (body.assignedSessionId) {
          a.assignedSessionId = text('assignedSessionId');
          a.checkerSessionId = a.assignedSessionId;
        }
        a.version++;
        delete db.accountSessions[a.id];
        log('UPDATE_ACCOUNT', 'USER', a.id);
        return mutate(userView(a));
      }
      if (parts[2] === 'reports')
        return page(
          db.reports.map((r) => ({
            ...r,
            eventAcceptedReportCount: db.reports.filter((x) => x.eventId === r.eventId).length,
            needsAttention: false,
          })),
          q,
        );
      if (parts[2] === 'investigations') {
        if (method === 'GET')
          return id ? db.investigations.find((i) => i.id === id) : page(db.investigations, q);
        if (!text('reason').trim() || !text('evidenceText').trim())
          fail('VALIDATION_FAILED', '请填写处理理由与可追溯证据', 422);
        const e = eventById(text('eventId'));
        const substantiated = text('conclusion') === 'SUBSTANTIATED';
        const result =
          substantiated && e.status === 'PUBLISHED'
            ? terminate(e, 'TAKEN_DOWN', text('reason'))
            : null;
        const reportIds = (body.reportIds as string[]) || [];
        db.reports
          .filter((r) => reportIds.includes(r.id))
          .forEach((r) => {
            r.status = text('conclusion');
            r.conclusion = text('reason');
            r.resolvedAt = now();
          });
        const investigation: Investigation = {
          id: uid(),
          factId: substantiated ? uid() : null,
          eventId: e.id,
          conclusion: text('conclusion'),
          mergedReportIds: reportIds,
          violationRecorded: substantiated,
          eventStatus: e.status!,
          terminationJobId: result?.jobId || null,
          reason: text('reason'),
          evidenceText: text('evidenceText'),
          factKey: text('factKey'),
          createdAt: now(),
        };
        db.investigations.unshift(investigation);
        return investigation;
      }
      if (parts[2] === 'payments')
        return page(
          db.payments.filter((a) => !q.get('orderId') || a.orderId === q.get('orderId')),
          q,
        );
      if (parts[2] === 'refund-requests')
        return id
          ? {
              request: db.refunds.find((r) => r.id === id),
              attempts: db.refundAttempts.filter((r) => r.requestId === id),
              order: db.orders.find((o) => o.id === db.refunds.find((r) => r.id === id)?.orderId),
            }
          : page(db.refunds, q);
      if (parts[2] === 'audit-logs')
        return page(
          db.logs.filter(
            (l) =>
              (!q.get('action') || l.action.includes(q.get('action')!)) &&
              (!q.get('objectId') || l.objectId === q.get('objectId')),
          ),
          q,
        );
      if (parts[2] === 'audit-objects') {
        const objectId = parts[4];
        const o = db.orders.find((o) => o.id === objectId || o.ticketId === objectId);
        if (!o) fail('RESOURCE_NOT_FOUND', '未找到该单条业务对象', 404);
        return {
          objectType: parts[3],
          id: objectId,
          record: orderView(o),
          relatedIds: {
            orders: [o.id],
            payments: db.payments.filter((a) => a.orderId === o.id).map((a) => a.id),
            refunds: [],
            tickets: [o.ticketId].filter(Boolean),
            checkins: [],
          },
        };
      }
      if (parts[2] === 'jobs')
        return id
          ? db.jobs.find((j) => j.id === id) || fail('RESOURCE_NOT_FOUND', '任务不存在', 404)
          : page(db.jobs, q);
      if (parts[2] === 'recoveries') {
        if (method === 'GET') return page(db.recoveries, q);
        const r =
          db.recoveries.find((r) => r.id === id) ||
          fail('RESOURCE_NOT_FOUND', '找回申请不存在', 404);
        if (!r.targetAccountId && text('decision') === 'VERIFIED')
          fail('VALIDATION_FAILED', '未匹配到账号，不能通过核验', 422);
        if (r.status !== 'PENDING') fail('STATE_CONFLICT', '申请已处理');
        if (text('decision') === 'VERIFIED' && text('initialPassword')) {
          const target = db.accounts.find((a) => a.id === r.targetAccountId);
          if (
            !target ||
            target.deletedAt ||
            target.phone !== r.phone ||
            !r.currentPhone ||
            !r.contactVerifiedAt
          )
            fail('STATE_CONFLICT', '原账号或当前手机号证明失效');
          if (
            db.accounts.some(
              (a) =>
                !a.deletedAt &&
                a.id !== target.id &&
                a.role === target.role &&
                a.phone === r.currentPhone,
            )
          )
            fail('PHONE_ALREADY_BOUND', '当前手机号已被同角色账号绑定');
          if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,72}$/.test(text('initialPassword')))
            fail('VALIDATION_FAILED', '临时密码须为8～72位字母数字组合', 422);
          target.phone = r.currentPhone;
          target.maskedPhone = r.currentPhone.slice(0, 3) + '****' + r.currentPhone.slice(-4);
          target.password = text('initialPassword');
          target.mustChangePassword = true;
          delete db.accountSessions[target.id];
          r.status = 'RESOLVED';
          r.resolvedAt = now();
          r.verificationNote = text('verificationNote');
          console.info(
            `[LOCAL_RECOVERY_SMS] mock phone=${r.currentPhone} account=${target.username} initialPassword=${target.password} mustChangePassword=true`,
          );
          return r;
        }
        r.status = text('decision');
        r.verificationNote = text('verificationNote');
        return r;
      }
      if (parts[2] === 'restriction-candidates') return page([], q);
    }
    fail('NOT_IMPLEMENTED', '此接口尚未加入前端演示，后续由Java后端实现', 501);
  }
}

export const demoIds = { event: fixedId(200), session: fixedId(210) };
