import type {
  AuditLog,
  CampusEvent,
  Category,
  Job,
  Message,
  Order,
  PaymentAttempt,
  RefundAttempt,
  RefundRequest,
  Report,
  Role,
  User,
} from '../types';

export interface DemoAccount extends User {
  password: string;
  enabled: boolean;
  deletedAt: string | null;
  version: number;
  assignedSessionId: string | null;
}
export interface StoredOrder extends Order {
  userId: string;
  checkedAt: string | null;
  enteredWaitlist: boolean;
}
export interface Database {
  schema: number;
  accounts: DemoAccount[];
  events: CampusEvent[];
  orders: StoredOrder[];
  categories: Category[];
  messages: (Message & { userId: string })[];
  reports: Report[];
  payments: PaymentAttempt[];
  refunds: RefundRequest[];
  refundAttempts: RefundAttempt[];
  jobs: Job[];
  logs: AuditLog[];
}
export const fixedId = (n: number) => `00000000-0000-4000-8000-${n.toString().padStart(12, '0')}`;
export function makeSeed(): Database {
  const now = new Date();
  const at = (days: number, hour = 19) => {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Shanghai',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(now);
    const d = new Date(`${parts}T${hour.toString().padStart(2, '0')}:00:00+08:00`);
    d.setTime(d.getTime() + days * 86400000);
    return d.toISOString();
  };
  const categories = ['音乐演出', '艺术展览', '戏剧现场', '讲座沙龙', '户外生活', '工作坊'].map(
    (name, i) => ({ id: fixedId(100 + i), name, enabled: true, version: 1 }),
  );
  const roleList: Role[] = ['PARTICIPANT', 'ORGANIZER', 'CHECKER', 'ADMIN'];
  const accounts: DemoAccount[] = roleList.map((role, i) => ({
    id: fixedId(i + 1),
    username: ['participant', 'organizer', 'checker', 'admin'][i]!,
    password: 'Demo2026',
    role,
    displayName: ['林同学', '陆老师', '陈同学', '平台管理员'][i]!,
    email: i < 2 ? `demo${i}@example.com` : null,
    maskedPhone: i < 2 ? '138****5678' : null,
    mustChangePassword: false,
    sessionExpiresAt: at(7),
    restrictions: [],
    checkerSessionId: role === 'CHECKER' ? fixedId(210) : null,
    enabled: true,
    deletedAt: null,
    assignedSessionId: role === 'CHECKER' ? fixedId(210) : null,
    version: 1,
  }));
  const briefs = [
    [
      '晚风来信 · 秋日室内音乐会',
      0,
      '鼓楼文化中心 · 小剧场',
      '把秋天，唱成一封信。\n当熟悉的旋律遇见木吉他的温度，我们在城市的一隅，留一段时间给音乐，也留给自己。这是一场属于秋天的室内音乐会，民谣、轻爵士与原创作品将在此相遇。',
    ],
    [
      '慢慢看见 · 生活里的艺术',
      1,
      '校园美术馆 · 一层展厅',
      '在日常的缝隙里，重新发现美。\n从一片落叶的纹理到一束光的形状，六位青年创作者用影像与装置记录平凡生活。欢迎带着好奇心，走进这场缓慢的观看。',
    ],
    [
      '一场好戏 · 星期六的约定',
      2,
      '大学生活动中心 · 黑匣子',
      '让故事在眼前发生。\n青年剧团带来小剧场原创作品，用舞台上的一次相遇，讲述我们共同经历过的告别与重逢。',
    ],
    [
      '城市漫谈 · 在南京生活',
      3,
      '鼓楼书屋 · 二楼',
      '从一条街巷，读懂一座城市。\n与建筑师、城市观察者坐下来，聊聊街道、树木和我们的生活。无需专业背景，每一种生活经验都值得分享。',
    ],
    [
      '山野回声 · 秋日轻徒步',
      4,
      '紫金山 · 白马公园集合点',
      '把脚步，交还给山野。\n沿着熟悉的林间小径慢行，在松针与风声之间认识新朋友。请自备饮水与舒适的鞋，不追求速度，只享受同行。',
    ],
    [
      '指尖的温度 · 陶艺体验',
      5,
      '仙林创意工坊 · 302',
      '从一团泥开始，留住生活的温度。\n在老师的引导下完成一件手捏陶器。从塑形到纹理，让想象在指尖慢慢生长。作品烧制安排现场说明。',
    ],
  ] as const;
  const events: CampusEvent[] = briefs.map(([name, category, location, description], i) => {
    const id = fixedId(200 + i);
    const sessionId = fixedId(210 + i);
    const startsAt = at(i + 1);
    const endsAt = at(i + 1, 21);
    return {
      id,
      name,
      categoryId: categories[category]!.id,
      categoryNameSnapshot: categories[category]!.name,
      description,
      posterUrl: null,
      status: 'PUBLISHED',
      organizerId: accounts[1]!.id,
      version: 1,
      submittedAt: at(-2),
      publishedAt: at(-1),
      settlementStatus: 'NOT_DUE',
      expiresAt: null,
      sessions: [
        {
          id: sessionId,
          eventId: id,
          eventName: name,
          categoryId: categories[category]!.id,
          categoryNameSnapshot: categories[category]!.name,
          name: '秋日专场',
          startsAt,
          endsAt,
          location,
          capacity: 100,
          salesOpensAt: at(-1, 9),
          registrationClosesAt: at(i + 1, 18),
          remaining: 100,
          sessionSalesStatus: 'ON_SALE',
          posterUrl: null,
          ticketTypes: [
            {
              id: fixedId(300 + i * 2),
              name: i === 1 || i === 3 ? '免费预约' : '早鸟票',
              capacity: 24,
              priceJiao: i === 1 || i === 3 ? 0 : [680, 0, 580, 0, 350, 880][i]!,
              saleStopped: false,
              occupied: 0,
              remaining: 24,
              salesStatus: 'ON_SALE',
            },
            {
              id: fixedId(301 + i * 2),
              name: '普通票',
              capacity: 76,
              priceJiao: i === 1 || i === 3 ? 0 : [980, 0, 880, 0, 500, 1280][i]!,
              saleStopped: false,
              occupied: 0,
              remaining: 76,
              salesStatus: 'ON_SALE',
            },
          ],
        },
      ],
    };
  });
  const orders: StoredOrder[] = [];
  function order(
    eventIndex: number,
    typeIndex: number,
    userId: string,
    status: Order['status'],
    n: number,
    attendeeName: string,
  ): StoredOrder {
    const event = events[eventIndex]!;
    const session = event.sessions[0]!;
    const ticketType = session.ticketTypes[typeIndex]!;
    return {
      id: fixedId(1000 + n),
      requestId: fixedId(2000 + n),
      userId,
      status,
      statusReason: null,
      createdAt: at(-1, 12),
      attendeeNameSnapshot: attendeeName,
      amountJiao: ticketType.priceJiao,
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
      ticketTypeId: ticketType.id,
      ticketTypeName: ticketType.name,
      payDeadline: null,
      confirmDeadline: null,
      queuePosition: status === 'WAITLISTED' ? 2 : null,
      consecutivePaymentFailures: 0,
      ticketId: ['REGISTERED', 'CHECKED_IN', 'COMPLETED', 'ABSENT', 'REFUNDED'].includes(status)
        ? fixedId(3000 + n)
        : null,
      activeRefundRequestId: null,
      allowedActions: [],
      checkedAt: status === 'CHECKED_IN' ? at(-1) : null,
      enteredWaitlist: status === 'WAITLISTED',
    };
  }
  let n = 1;
  for (let e = 0; e < events.length; e++) {
    const early = e === 2 ? 24 : [16, 9, 24, 11, 18, 14][e]!;
    for (let k = 0; k < early; k++)
      orders.push(order(e, 0, fixedId(5000 + n), 'REGISTERED', n++, `同学${n}`));
    for (let k = 0; k < 8; k++)
      orders.push(order(e, 1, fixedId(5000 + n), 'REGISTERED', n++, `同学${n}`));
  }
  orders.push(order(1, 0, accounts[0]!.id, 'REGISTERED', n++, '林同学'));
  orders.push(order(2, 0, accounts[0]!.id, 'WAITLISTED', n++, '林同学'));
  // A separate live fixture makes the check-in screens usable immediately without
  // exposing a clock bypass or failure switch to ordinary participants.
  const live = structuredClone(events[3]!);
  live.id = fixedId(230);
  live.name = '现场核验 · 校园午后专场';
  const liveStart = new Date(Math.floor(Date.now() / 3600000) * 3600000);
  const liveSession = live.sessions[0]!;
  liveSession.id = fixedId(231);
  liveSession.eventId = live.id;
  liveSession.eventName = live.name;
  liveSession.name = '现场演示场';
  liveSession.startsAt = liveStart.toISOString();
  liveSession.endsAt = new Date(liveStart.getTime() + 2 * 3600000).toISOString();
  liveSession.registrationClosesAt = liveStart.toISOString();
  liveSession.salesOpensAt = at(-1, 8);
  liveSession.ticketTypes.forEach((t, i) => {
    t.id = fixedId(450 + i);
  });
  const liveIndex = events.length;
  events.push(live);
  accounts[2]!.assignedSessionId = liveSession.id;
  accounts[2]!.checkerSessionId = liveSession.id;
  orders.push(order(liveIndex, 0, accounts[0]!.id, 'REGISTERED', n++, '林同学'));
  const draft = structuredClone(events[5]!);
  draft.id = fixedId(220);
  draft.name = '冬日序曲 · 校园分享会';
  draft.status = 'DRAFT';
  draft.description = '从一个想法开始，筹备下一次相聚。';
  draft.sessions = [];
  draft.categoryId = categories[3]!.id;
  draft.categoryNameSnapshot = categories[3]!.name;
  events.push(draft);
  const pending = structuredClone(events[4]!);
  pending.id = fixedId(221);
  pending.name = '午后读诗 · 文字与远方';
  pending.status = 'PENDING_REVIEW';
  pending.submittedAt = at(-2);
  pending.sessions.forEach((s) => {
    s.id = fixedId(222);
    s.eventId = pending.id;
    s.eventName = pending.name;
    s.ticketTypes.forEach((t, i) => {
      t.id = fixedId(400 + i);
    });
  });
  events.push(pending);
  const messages: Database['messages'] = accounts.map((account, i) => ({
    id: fixedId(4000 + i),
    userId: account.id,
    type: 'WELCOME',
    title: '欢迎来到活动票务',
    body: '在这里发现喜欢的现场，让每一次相聚，都从容一点。当前是前端演示环境，所有业务均为本地模拟。',
    createdAt: now.toISOString(),
    expiresAt: at(180),
    readAt: null,
    target: null,
    actionDeadline: null,
  }));
  const payments = orders
    .filter((o) => o.ticketId)
    .map((o, i) => ({
      id: fixedId(8000 + i),
      orderId: o.id,
      attemptId: fixedId(9000 + i),
      result: 'SUCCESS',
      amountJiao: o.amountJiao,
      createdAt: o.createdAt,
      failureReason: null,
    }));
  return {
    schema: 1,
    accounts,
    events,
    orders,
    categories,
    messages,
    payments,
    refunds: [],
    refundAttempts: [],
    reports: [
      {
        id: fixedId(6000),
        sessionId: events[2]!.sessions[0]!.id,
        eventId: events[2]!.id,
        reporterId: accounts[0]!.id,
        reason: '活动信息需要核实',
        description: '希望核实场地安排是否已经确认。',
        evidenceText: '活动页显示的地点与场地方公告有差异（演示记录）。',
        status: 'PENDING',
        createdAt: now.toISOString(),
        conclusion: null,
        resolvedAt: null,
      },
    ],
    jobs: [],
    logs: [],
  };
}
