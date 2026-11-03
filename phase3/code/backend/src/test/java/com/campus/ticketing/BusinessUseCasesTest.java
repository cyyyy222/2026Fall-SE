package com.campus.ticketing;

import static com.campus.ticketing.common.EntityRecords.*;
import static com.campus.ticketing.dao.RecordStore.Filter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.entity.*;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.security.SessionPrincipal;
import com.campus.ticketing.service.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Application-rule tests using a clearly test-only record fixture, NOT database integration tests.
 */
class BusinessUseCasesTest {
  static class TestClock extends Clock {
    Instant instant = Instant.parse("2026-11-05T02:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return instant;
    }

    void advance(Duration d) {
      instant = instant.plus(d);
    }
  }

  TestClock clock;
  TestRecordStore db;
  BusinessSupport b;
  IdentityUseCases identity;
  EventUseCases events;
  OrderUseCases orders;
  DurableWorkflows workflows;
  GovernanceUseCases governance;
  UUID owner, alice, bob, admin, checker, event, session, type, category;
  PlatformTransactionManager tx;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setup() {
    clock = new TestClock();
    db = new TestRecordStore();
    var store = mock(ObjectProvider.class);
    when(store.getIfAvailable()).thenReturn(db);
    var managers = mock(ObjectProvider.class);
    tx = mock(PlatformTransactionManager.class);
    when(tx.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
    when(managers.getIfAvailable()).thenReturn(tx);
    b =
        new BusinessSupport(
            store, managers, clock, new ObjectMapper().registerModule(new JavaTimeModule()));
    identity = new IdentityUseCases(b, new MockEnvironment());
    events = new EventUseCases(b, identity);
    orders = new OrderUseCases(b, new PaymentSimulator());
    var reports = mock(ObjectProvider.class);
    var gov = mock(ObjectProvider.class);
    workflows =
        new DurableWorkflows(
            b, events, orders, reports, gov, new ControlledFiles(b, new MockEnvironment()));
    governance = new GovernanceUseCases(b, identity, workflows);
    when(gov.getObject()).thenReturn(governance);
    owner = user("owner", "ORGANIZER");
    alice = user("alice", "PARTICIPANT");
    bob = user("bob", "PARTICIPANT");
    admin = user("admin", "ADMIN");
    checker = user("checker", "CHECKER");
    category = UUID.randomUUID();
    db.insert(new CategoryEntity(category, "学习", "学习", true, 0L, b.now(), b.now()));
    event = event("PUBLISHED");
    session = session(event, 18, 20);
    type = type(session, 1);
    db.insert(
        new CheckerAssignmentEntity(
            UUID.randomUUID(), checker, session, admin, b.now(), null, null));
  }

  UUID user(String name, String role) {
    UUID id = UUID.randomUUID(), phone = null;
    if (role.equals("PARTICIPANT") || role.equals("ORGANIZER")) {
      phone = UUID.randomUUID();
      db.insert(
          new PhoneIdentityEntity(
              phone,
              "138" + String.format("%08d", db.count(PhoneIdentityEntity.class, null)),
              0,
              null,
              null,
              b.now(),
              0L));
    }
    db.insert(
        new UserEntity(
            id,
            name,
            name,
            phone == null ? null : name + "@example.test",
            phone == null ? null : name + "@example.test",
            phone,
            "$2a$04$o6w6yDAh8TB0UoVGEthO5ubMrG7KYsSL0tQtKjIgtMDNYzH7O/7qi",
            role,
            null,
            true,
            false,
            null,
            null,
            b.now(),
            b.now(),
            0L));
    return id;
  }

  UUID event(String status) {
    UUID id = UUID.randomUUID();
    db.insert(
        create(
            EventEntity.class,
            "id",
            id,
            "organizerId",
            owner,
            "status",
            status,
            "name",
            "活动",
            "categoryId",
            category,
            "categoryNameSnapshot",
            "学习",
            "description",
            null,
            "settlementStatus",
            "NOT_DUE",
            "createdAtMs",
            b.now(),
            "updatedAtMs",
            b.now(),
            "version",
            0L));
    return id;
  }

  UUID session(UUID event, int start, int end) {
    UUID id = UUID.randomUUID();
    long day =
        b.time(b.now())
            .toLocalDate()
            .atStartOfDay()
            .toInstant(ZoneOffset.ofHours(8))
            .toEpochMilli();
    db.insert(
        new EventSessionEntity(
            id,
            event,
            "场次" + id,
            day + start * 3_600_000L,
            day + end * 3_600_000L,
            "教室",
            10,
            day + 9 * 3_600_000L,
            day + 17 * 3_600_000L,
            "NOT_DUE",
            null,
            0L));
    return id;
  }

  UUID type(UUID session, int capacity) {
    UUID id = UUID.randomUUID();
    db.insert(new TicketTypeEntity(id, session, "普通票", capacity, 100L, false, null, 0L));
    return id;
  }

  RequestContext actor(UUID user) {
    var u = b.need(UserEntity.class, user, false);
    return new RequestContext(
        new SessionPrincipal(
            user,
            Role.valueOf(u.role()),
            u.mustChangePassword(),
            clock.instant().plus(Duration.ofDays(7))),
        UUID.randomUUID(),
        "test-trace",
        "127.0.0.1");
  }

  Requests.Registration registration(UUID request) {
    return new Requests.Registration(request, session, type, "张三");
  }

  UUID paid(UUID user) {
    var c = actor(user);
    UUID order = orders.register(c, registration(UUID.randomUUID())).data().current().id();
    orders.pay(c, order, new Requests.Attempt(UUID.randomUUID()));
    return order;
  }

  @Test
  void registrationAliasesAndPaymentReplayPreserveOneOccupancyAndOneTicket() {
    var c = actor(alice);
    var request = registration(UUID.randomUUID());
    var first = orders.register(c, request);
    assertThat(first.status()).isEqualTo(201);
    assertThat(first.data().current().status()).isEqualTo(OrderStatus.UNPAID);
    var replay = orders.register(c, request);
    assertThat(replay.data().current().id()).isEqualTo(first.data().current().id());
    assertThat(replay.data().replayed()).isTrue();
    var alias = orders.register(c, registration(UUID.randomUUID()));
    assertThat(alias.data().current().id()).isEqualTo(first.data().current().id());
    assertThat(db.count(RegistrationRequestEntity.class, null)).isEqualTo(2);
    assertThat(
            orders.register(actor(bob), registration(UUID.randomUUID())).data().current().status())
        .isEqualTo(OrderStatus.WAITLISTED);
    UUID attempt = UUID.randomUUID();
    var paid = orders.pay(c, first.data().current().id(), new Requests.Attempt(attempt));
    assertThat(paid.data().ticket()).isNotNull();
    orders.pay(c, first.data().current().id(), new Requests.Attempt(attempt));
    orders.pay(c, first.data().current().id(), new Requests.Attempt(UUID.randomUUID()));
    assertThat(db.count(PaymentAttemptEntity.class, null)).isEqualTo(1);
    assertThat(db.count(TicketEntity.class, null)).isEqualTo(1);
    assertThat(b.occupied(type)).isEqualTo(1);
  }

  @Test
  void cancellationKeepsOldRequestMappingAndOffersWaitingUserBeforeNewcomer() {
    var aliceCtx = actor(alice);
    var original = registration(UUID.randomUUID());
    UUID first = orders.register(aliceCtx, original).data().current().id();
    UUID queued =
        orders.register(actor(bob), registration(UUID.randomUUID())).data().current().id();
    orders.cancel(aliceCtx, first, new Requests.Cancel("不参加"));
    clock.advance(Duration.ofMillis(1));
    UUID charlie = user("charlie", "PARTICIPANT");
    assertThat(
            orders
                .register(actor(charlie), registration(UUID.randomUUID()))
                .data()
                .current()
                .status())
        .isEqualTo(OrderStatus.WAITLISTED);
    workflows.promoteType(type);
    assertThat(b.need(OrderEntity.class, queued, false).status()).isEqualTo("OFFERED");
    assertThat(b.occupied(type)).isEqualTo(1);
    orders.confirmPromotion(actor(bob), queued, new Requests.Empty());
    assertThat(
            db.count(
                JobEntity.class,
                eq(
                    "dedupKey",
                    "EXPIRY:"
                        + queued
                        + ":"
                        + b.need(OrderEntity.class, queued, false).payDeadlineMs())))
        .isEqualTo(1);
    assertThat(b.occupied(type)).isEqualTo(1);
    assertThat(b.need(OrderEntity.class, queued, false).payDeadlineMs())
        .isEqualTo(b.now() + 15 * 60_000);
    assertThat(orders.register(aliceCtx, original).data().current().id()).isEqualTo(first);
    assertThat(orders.register(aliceCtx, original).data().current().status())
        .isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void distinctThreePaymentFailuresCancelButReplayDoesNotIncrement() {
    var failing =
        new OrderUseCases(
            b,
            new PaymentSimulator() {
              @Override
              public Outcome pay(long amount) {
                return new Outcome(false, "测试模拟失败");
              }
            });
    var c = actor(alice);
    UUID order = failing.register(c, registration(UUID.randomUUID())).data().current().id();
    UUID first = UUID.randomUUID();
    failing.pay(c, order, new Requests.Attempt(first));
    failing.pay(c, order, new Requests.Attempt(first));
    assertThat(b.need(OrderEntity.class, order, false).consecutivePaymentFailures()).isEqualTo(1);
    failing.pay(c, order, new Requests.Attempt(UUID.randomUUID()));
    var third = failing.pay(c, order, new Requests.Attempt(UUID.randomUUID()));
    assertThat(third.data().current().status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(db.count(PaymentAttemptEntity.class, null)).isEqualTo(3);
    assertThat(db.count(TicketEntity.class, null)).isZero();
  }

  @Test
  void refundCommitsGrantBeforeAttemptAndFailedGrantCanRetryAfterClose() {
    UUID order = paid(alice);
    var c = actor(alice);
    var failing =
        new OrderUseCases(
            b,
            new PaymentSimulator() {
              @Override
              public Outcome refund(long amount) {
                return new Outcome(false, "测试退款失败");
              }
            });
    clearInvocations(tx);
    var first = failing.requestRefund(c, order, new Requests.Refund(UUID.randomUUID(), "计划调整"));
    assertThat(first.data().request().status()).isEqualTo(RefundRequestStatus.FAILED);
    assertThat(first.data().current().status()).isEqualTo(OrderStatus.REGISTERED);
    assertThat(b.occupied(type)).isEqualTo(1);
    verify(tx, times(2)).commit(any());
    clock.advance(Duration.ofHours(7).plusSeconds(1));
    var retried =
        orders.retryRefund(
            actor(alice),
            order,
            first.data().request().id(),
            new Requests.Attempt(UUID.randomUUID()));
    assertThat(retried.data().current().status()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(b.occupied(type)).isZero();
    assertThat(db.count(BehaviorFactEntity.class, eq("type", "VOLUNTARY_REFUND"))).isEqualTo(1);
  }

  @Test
  void refundCrashBetweenT1AndT2RecoversInitialAttemptWithoutNewDeadline() {
    UUID order = paid(alice);
    var c = actor(alice);
    var r = new Requests.Refund(UUID.randomUUID(), "退票");
    var crashing =
        new OrderUseCases(
            b,
            new PaymentSimulator() {
              @Override
              public Outcome refund(long amount) {
                throw new IllegalStateException("test outage");
              }
            });
    assertThatThrownBy(() -> crashing.requestRefund(c, order, r))
        .isInstanceOf(IllegalStateException.class);
    var grant = b.list(RefundRequestEntity.class, null).get(0);
    assertThat(grant.status()).isEqualTo("ACCEPTED");
    assertThat(grant.initialAttemptId()).isEqualTo(r.attemptId());
    clock.advance(Duration.ofHours(7).plusSeconds(1));
    var recovered = orders.requestRefund(c, order, r);
    assertThat(recovered.data().request().id()).isEqualTo(grant.id());
    assertThat(recovered.data().replayed()).isTrue();
    assertThat(db.count(RefundRequestEntity.class, null)).isEqualTo(1);
    assertThat(recovered.data().current().status()).isEqualTo(OrderStatus.REFUNDED);
  }

  @Test
  void checkedTicketReplaysFirstTimeAfterWindowWhileLaterSessionKeepsAccountValid() {
    UUID later = session(event, 21, 23);
    type(later, 2);
    UUID order = paid(alice);
    UUID ticket = b.list(TicketEntity.class, eq("orderId", order)).get(0).id();
    var checks = new CheckInUseCases(b, identity, events, orders);
    clock.advance(Duration.ofHours(7).plusMinutes(30));
    var c = actor(checker);
    var first = checks.confirmCheckIn(c, new Requests.Ticket(ticket));
    assertThat(first.status()).isEqualTo(201);
    clock.advance(Duration.ofHours(3));
    var replay = checks.confirmCheckIn(c, new Requests.Ticket(ticket));
    assertThat(replay.status()).isEqualTo(200);
    assertThat(replay.data().checkedAt()).isEqualTo(first.data().checkedAt());
    assertThat(db.count(CheckInEntity.class, null)).isEqualTo(1);
  }

  @Test
  void checkinWindowOpensTwoHoursBeforeAndClosesAtHalfOfTheSession() {
    UUID order = paid(alice);
    UUID ticket = b.list(TicketEntity.class, eq("orderId", order)).get(0).id();
    var checks = new CheckInUseCases(b, identity, events, orders);
    var c = actor(checker);
    clock.advance(Duration.ofHours(5).plusMinutes(59));
    assertThatThrownBy(() -> checks.previewTicket(c, new Requests.Ticket(ticket)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("CHECKIN_NOT_OPEN"));
    clock.advance(Duration.ofMinutes(1));
    var preview = checks.previewTicket(c, new Requests.Ticket(ticket)).data();
    assertThat(preview.userId()).isEqualTo(alice);
    assertThat(preview.username()).isEqualTo("alice");
    assertThat(preview.attendeeName()).isEqualTo("张三");
    assertThat(preview.phone()).isNotBlank();
    clock.advance(Duration.ofHours(3));
    assertThatThrownBy(() -> checks.previewTicket(c, new Requests.Ticket(ticket)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("CHECKIN_ENDED"));
  }

  @Test
  void waitlistPromotionAndPaymentMayFinishAfterRegistrationCloseBeforeCheckinEnds() {
    UUID paidOrder = paid(alice);
    var queued = orders.register(actor(bob), registration(UUID.randomUUID())).data().current().id();
    assertThat(b.need(OrderEntity.class, queued, false).status()).isEqualTo("WAITLISTED");
    orders.requestRefund(
        actor(alice), paidOrder, new Requests.Refund(UUID.randomUUID(), "测试释放名额"));
    clock.advance(Duration.ofHours(7).plusMinutes(30)); // after registration close, before check-in close
    workflows.promoteType(type);
    var pending = b.need(EventSessionEntity.class, session, false);
    long close = DomainRules.checkinClose(pending.startsAtMs(), pending.endsAtMs());
    var offered = b.need(OrderEntity.class, queued, false);
    assertThat(offered.status()).isEqualTo("OFFERED");
    assertThat(offered.confirmDeadlineMs()).isEqualTo(close);
    orders.confirmPromotion(actor(bob), queued, new Requests.Empty());
    var unpaid = b.need(OrderEntity.class, queued, false);
    assertThat(unpaid.status()).isEqualTo("UNPAID");
    assertThat(unpaid.payDeadlineMs()).isEqualTo(Math.min(b.now() + 15 * 60_000, close));
    orders.pay(actor(bob), queued, new Requests.Attempt(UUID.randomUUID()));
    assertThat(b.need(OrderEntity.class, queued, false).status()).isEqualTo("REGISTERED");
  }

  @Test
  void organizerProposedCategoryIsCreatedByAdminApproval() {
    var created = events.createDraft(actor(owner), new Requests.Empty());
    UUID draftId = created.data().id();
    var now = b.time(b.now());
    UUID sessionId = UUID.randomUUID(), typeId = UUID.randomUUID();
    var saved =
        events.saveDraft(
            actor(owner),
            draftId,
            new Requests.Draft(
                created.data().version(),
                "校园市集",
                null,
                "由组织者提出新类别的测试活动",
                List.of(
                    new Requests.SessionDraft(
                        sessionId,
                        "市集场",
                        now.plusHours(3),
                        now.plusHours(5),
                        "学校操场",
                        10,
                        now.plusHours(1),
                        now.plusHours(2),
                        List.of(
                            new Requests.TicketTypeDraft(typeId, "免费票", 10, 0L, false)))),
                "校园市集"));
    var submitted =
        events.submitReview(
            actor(owner), draftId, new Requests.Version(saved.data().current().version()));
    assertThat(submitted.data().current().status()).isEqualTo(EventStatus.PENDING_REVIEW);
    UUID checkerId = user("marketchecker", "CHECKER");
    db.insert(
        new CheckerAssignmentEntity(
            UUID.randomUUID(), checkerId, sessionId, admin, b.now(), null, null));
    var approved =
        events.approve(
            actor(admin), draftId, new Requests.Version(submitted.data().current().version()));
    assertThat(approved.data().current().status()).isEqualTo(EventStatus.PUBLISHED);
    var category =
        b.db()
            .one(CategoryEntity.class, eq("nameNormalized", BusinessSupport.normalize("校园市集")), false)
            .orElseThrow();
    assertThat(b.need(EventEntity.class, draftId, false).categoryId()).isEqualTo(category.id());
    assertThat(b.need(EventEntity.class, draftId, false).categoryNameSnapshot())
        .isEqualTo("校园市集");
  }

  @Test
  void waitlistedOrderExpiresWhenCheckinCloses() {
    paid(alice);
    UUID queued = orders.register(actor(bob), registration(UUID.randomUUID())).data().current().id();
    assertThat(b.need(OrderEntity.class, queued, false).status()).isEqualTo("WAITLISTED");
    clock.advance(Duration.ofHours(9));
    workflows.expireOrder(queued);
    assertThat(b.need(OrderEntity.class, queued, false).status()).isEqualTo("CANCELLED");
  }

  @Test
  void weeklyAbsenceScanWarnsTwiceThenBansAndAppealCanLiftIt() {
    var phone = b.need(UserEntity.class, alice, false).phoneIdentityId();
    var start = b.now() - 86_400_000L;
    var end = b.now() + 86_400_000L;
    for (int i = 0; i < 2; i++)
      db.insert(
          new BehaviorFactEntity(
              UUID.randomUUID(),
              phone,
              "NATURAL_ABSENCE",
              UUID.randomUUID(),
              null,
              b.now() - 1000 + i,
              "测试缺席"));
    governance.weeklyAbsenceScan(start, end);
    assertThat(b.list(MessageEntity.class, and(eq("recipientId", alice), eq("type", "ABSENCE_WARNING"))))
        .hasSize(1);
    db.insert(
        new BehaviorFactEntity(
            UUID.randomUUID(),
            phone,
            "NATURAL_ABSENCE",
            UUID.randomUUID(),
            null,
            b.now(),
            "测试缺席"));
    governance.weeklyAbsenceScan(start, end);
    var restriction =
        b.db()
            .one(RestrictionEntity.class, and(eq("phoneIdentityId", phone), eq("source", "WEEKLY_ABSENCE")), false)
            .orElseThrow();
    assertThat(b.restricted(phone, "REGISTER")).isTrue();

    var appeal =
        governance.createAppeal(
            actor(alice),
            new Requests.RestrictionAppeal(restriction.id(), "本周因住院无法到场，附医院证明说明。", "住院证明"));
    assertThat(appeal.data().status()).isEqualTo(AppealStatus.PENDING);
    assertThat(
            governance
                .listAppeals(
                    actor(admin),
                    new com.campus.ticketing.dto.queries.Queries.RestrictionAppeals(
                        AppealStatus.PENDING, 1, 20))
                .data()
                .items())
        .hasSize(1);
    governance.resolveAppeal(
        actor(admin),
        appeal.data().id(),
        new Requests.ResolveAppeal(AppealStatus.APPROVED, "已核对住院证明，同意解除"));
    assertThat(b.restricted(phone, "REGISTER")).isFalse();
    assertThat(b.need(RestrictionEntity.class, restriction.id(), false).revokedAtMs()).isNotNull();
  }

  @Test
  void reportDeduplicatesByEventAndStillAllowsOtherEvents() {
    governance.createReport(
        actor(alice),
        new Requests.Report(session, "活动信息不符", "宣传与实际不一致", "截图链接"));
    UUID secondSession = session(event, 19, 21);
    assertThatThrownBy(
            () ->
                governance.createReport(
                    actor(alice),
                    new Requests.Report(secondSession, "活动信息不符", "同一活动再次举报", "截图链接")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("DUPLICATE_REPORT"));
  }

  @Test
  void terminalEventStopsCheckinImmediatelyAndFreePlatformRefundCreatesNoVoluntaryFact() {
    var typeRow = b.need(TicketTypeEntity.class, type, false);
    db.save(copy(typeRow, "priceJiao", 0L));
    UUID order = paid(alice);
    var e = b.need(EventEntity.class, event, false);
    var job = b.write(() -> workflows.beginTermination(actor(admin), e, "TAKEN_DOWN", "核查属实"));
    assertThat(b.need(EventEntity.class, event, false).status()).isEqualTo("TAKEN_DOWN");
    assertThat(b.need(UserEntity.class, checker, false).enabled()).isFalse();
    workflows.settleTerminationItem(job.id(), order);
    assertThat(b.need(OrderEntity.class, order, false).status()).isEqualTo("REFUNDED");
    assertThat(db.count(BehaviorFactEntity.class, eq("type", "VOLUNTARY_REFUND"))).isZero();
  }

  @Test
  void normalEndSnapshotsIncludeBothCumulativeAndCurrentWaitlist() {
    UUID successful = paid(alice);
    orders.register(actor(bob), registration(UUID.randomUUID()));
    clock.advance(Duration.ofHours(11));
    workflows.reconcileSession(session);
    var snapshot = b.list(StatisticsSnapshotEntity.class, eq("sessionId", session)).get(0);
    assertThat(b.need(OrderEntity.class, successful, false).status()).isEqualTo("ABSENT");
    assertThat(snapshot.issuedCount()).isEqualTo(1);
    assertThat(snapshot.absentCount()).isEqualTo(1);
    assertThat(snapshot.currentWaitlistCount()).isZero();
    assertThat(snapshot.cumulativeWaitlistApplications()).isEqualTo(1);
    assertThat(b.need(EventEntity.class, event, false).terminatedAtMs())
        .isEqualTo(b.need(EventSessionEntity.class, session, false).endsAtMs());
  }

  @Test
  void participantRestrictionConsumesServerFactsAndFollowsPhoneIdentity() {
    UUID phone = b.need(UserEntity.class, alice, false).phoneIdentityId();
    List<UUID> facts = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      UUID fact = UUID.randomUUID();
      facts.add(fact);
      db.insert(
          new BehaviorFactEntity(
              fact, phone, "NATURAL_ABSENCE", UUID.randomUUID(), null, b.now() - 1000 + i, "正常缺席"));
    }
    var c = actor(admin);
    var r = new Requests.Restriction(phone, facts, "已核实三次缺席");
    var restricted = governance.restrictRegistration(c, r);
    assertThat(restricted.status()).isEqualTo(201);
    assertThat(db.count(RestrictionFactEntity.class, null)).isEqualTo(3);
    assertThat(b.restricted(phone, "REGISTER")).isTrue();
    assertThat(governance.restrictRegistration(c, r).data().id()).isEqualTo(restricted.data().id());
    assertThatThrownBy(() -> orders.register(actor(alice), registration(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("REGISTRATION_RESTRICTED"));
  }

  @Test
  void thirdOrganizerViolationRestrictsAtomicallyWithoutExtendingForFourth() {
    for (int i = 0; i < 3; i++) {
      UUID id = event("ENDED");
      governance.investigate(
          actor(admin),
          new Requests.Investigation(
              id, List.of(), "fact" + i, Conclusion.SUBSTANTIATED, "核查属实", "已人工核实证据"));
      clock.advance(Duration.ofSeconds(1));
    }
    UUID phone = b.need(UserEntity.class, owner, false).phoneIdentityId();
    var restriction = b.list(RestrictionEntity.class, eq("phoneIdentityId", phone)).get(0);
    assertThat(b.restricted(phone, "PUBLISH")).isTrue();
    governance.investigate(
        actor(admin),
        new Requests.Investigation(
            event("ENDED"), List.of(), "fourth", Conclusion.SUBSTANTIATED, "核查属实", "已人工核实证据"));
    assertThat(b.list(RestrictionEntity.class, eq("phoneIdentityId", phone))).hasSize(1);
    assertThat(b.need(RestrictionEntity.class, restriction.id(), false).endsAtMs())
        .isEqualTo(restriction.endsAtMs());
  }

  @Test
  void reportsAreLimitedPerEventButNotByMonthlyQuota() {
    UUID firstEvent = UUID.randomUUID(), firstSession = UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      UUID e = event("PUBLISHED");
      UUID sid = session(e, 18, 20);
      if (i == 0) {
        firstEvent = e;
        firstSession = sid;
      }
      governance.createReport(actor(alice), new Requests.Report(sid, "理由", "说明", "证据"));
    }
    UUID sameEventOtherSession = session(firstEvent, 19, 21);
    assertThatThrownBy(
            () ->
                governance.createReport(
                    actor(alice),
                    new Requests.Report(sameEventOtherSession, "理由", "说明", "证据")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("DUPLICATE_REPORT"));
    b.notify(alice, "example", "EVENT_CHANGED", "标题", "内容", "EVENT", firstEvent, b.now() + 1000);
    var message = b.list(MessageEntity.class, null).get(0);
    governance.messageDetail(actor(alice), message.id());
    assertThat(b.need(MessageEntity.class, message.id(), false).readAtMs()).isNull();
    clock.advance(Duration.ofSeconds(2));
    governance.markRead(actor(alice), message.id(), new Requests.Empty());
    assertThat(b.need(MessageEntity.class, message.id(), false).actionDeadlineMs())
        .isEqualTo(message.actionDeadlineMs());
  }

  @Test
  void registrationAfterClosureIsAllowedButNextClosureStillWaits365Days() {
    var old = b.need(UserEntity.class, alice, false);
    var phone = b.need(PhoneIdentityEntity.class, old.phoneIdentityId(), false);
    db.save(copy(old, "enabled", false, "deletedAtMs", b.now()));
    db.save(copy(phone, "cancellationCount", 1, "lastCancelledAtMs", b.now()));
    var registered =
        identity.register(
            new RequestContext(null, null, "test"),
            new Requests.Register(
                "newalice",
                "Newpass123",
                "newalice@example.test",
                phone.phone(),
                RegisterRole.PARTICIPANT));
    assertThat(registered.status()).isEqualTo(201);
    assertThat(
            identity
                .eligibility(b.need(UserEntity.class, registered.data().id(), false))
                .eligible())
        .isFalse();
  }

  @Test
  void phoneCanBeSharedAcrossRolesButNotByTwoActiveAccountsOfTheSameRole() {
    var participant = b.need(UserEntity.class, alice, false);
    String phone = b.need(PhoneIdentityEntity.class, participant.phoneIdentityId(), false).phone();
    var c = new RequestContext(null, null, "phone-role-test");
    var organizer =
        identity.register(
            c,
            new Requests.Register(
                "samephoneorganizer",
                "Testpass123",
                "samephoneorganizer@example.test",
                phone,
                RegisterRole.ORGANIZER));
    assertThat(organizer.status()).isEqualTo(201);
    assertThat(b.need(UserEntity.class, organizer.data().id(), false).phoneIdentityId())
        .isEqualTo(participant.phoneIdentityId());
    for (var role : RegisterRole.values()) {
      assertThatThrownBy(
              () ->
                  identity.register(
                      c,
                      new Requests.Register(
                          "duplicate" + role.name(),
                          "Testpass123",
                          role.name() + "@example.test",
                          phone,
                          role)))
          .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(422));
    }
    // The phone exception does not relax username/email uniqueness across roles.
    assertThatThrownBy(
            () ->
                identity.register(
                    c,
                    new Requests.Register(
                        participant.username(),
                        "Testpass123",
                        "another@example.test",
                        "13999999001",
                        RegisterRole.ORGANIZER)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                identity.register(
                    c,
                    new Requests.Register(
                        "anotherorganizer",
                        "Testpass123",
                        participant.email(),
                        "13999999002",
                        RegisterRole.ORGANIZER)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void accountModulesFilterByRoleAndExposeOnlyMaskedPhones() {
    var accounts =
        new AdminAccountUseCases(
            b, identity, events, new LocalSmsDelivery(b, new MockEnvironment()));
    for (Role role : Role.values()) {
      var page =
          accounts
              .listAccounts(
                  actor(admin),
                  new com.campus.ticketing.dto.queries.Queries.Accounts(
                      role, null, null, null, null, 1, 100))
              .data();
      assertThat(page.items()).isNotEmpty().allMatch(a -> a.role() == role);
      for (var account : page.items()) {
        if (role == Role.ADMIN || role == Role.CHECKER) assertThat(account.maskedPhone()).isNull();
        else assertThat(account.maskedPhone()).contains("****").hasSize(11);
      }
    }
  }

  @Test
  void checkerCanBeReassignedToAnotherEventWithOneActiveAssignment() {
    var accounts =
        new AdminAccountUseCases(
            b, identity, events, new LocalSmsDelivery(b, new MockEnvironment()));
    var otherEvent = event("PUBLISHED");
    var otherSession = session(otherEvent, 18, 20);
    // The original session must keep at least one available checker.
    var secondChecker = user("checker2", "CHECKER");
    db.insert(
        new CheckerAssignmentEntity(
            UUID.randomUUID(), secondChecker, session, admin, b.now(), null, null));
    var version = b.need(UserEntity.class, checker, false).version();
    var updated =
        accounts.updateAccount(
            actor(admin),
            checker,
            new Requests.UpdateAccount(version, PatchField.absent(), PatchField.of(otherSession)));
    assertThat(updated.data().current().assignedSessionId()).isEqualTo(otherSession);
    assertThat(updated.data().current().eventId()).isEqualTo(otherEvent);
    var assignments = b.list(CheckerAssignmentEntity.class, eq("checkerId", checker));
    assertThat(assignments).hasSize(2);
    assertThat(assignments.stream().filter(a -> a.revokedAtMs() == null).toList())
        .singleElement()
        .satisfies(a -> assertThat(a.sessionId()).isEqualTo(otherSession));
    assertThat(assignments.stream().filter(a -> a.revokedAtMs() != null).toList())
        .singleElement()
        .satisfies(a -> assertThat(a.revocationReason()).isEqualTo("REASSIGNED"));
  }

  @Test
  void privilegedAccountUsernamesStayUniqueIgnoringCaseAndOuterSpaces() {
    var accounts =
        new AdminAccountUseCases(
            b, identity, events, new LocalSmsDelivery(b, new MockEnvironment()));
    var first =
        accounts.createAccount(
            actor(admin),
            new Requests.CreateAccount("checkerNew", "Password123", ManagedRole.CHECKER, session));
    assertThat(first.data().username()).isEqualTo("checkerNew");
    assertThatThrownBy(
            () ->
                accounts.createAccount(
                    actor(admin),
                    new Requests.CreateAccount(
                        "  CHECKERNEW ", "Password123", ManagedRole.CHECKER, session)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void restrictionsAndNotificationsRemainScopedToRoleWhenPhoneIsShared() {
    var participant = b.need(UserEntity.class, alice, false);
    String phone = b.need(PhoneIdentityEntity.class, participant.phoneIdentityId(), false).phone();
    var registered =
        identity.register(
            new RequestContext(null, null, "role-test"),
            new Requests.Register(
                "sharedorganizer",
                "Testpass123",
                "shared@example.test",
                phone,
                RegisterRole.ORGANIZER));
    var organizer = b.need(UserEntity.class, registered.data().id(), false);
    db.insert(
        new RestrictionEntity(
            UUID.randomUUID(),
            participant.phoneIdentityId(),
            "REGISTER",
            b.now() - 1000,
            b.now() + 1000,
            "test",
            admin,
            null));
    var userView =
        (com.campus.ticketing.dto.views.Views.UserView)
            identity.identity(organizer, b.now() + 10000);
    assertThat(userView.restrictions()).isEmpty();
    assertThat(b.restricted(organizer.phoneIdentityId(), "PUBLISH")).isFalse();
    assertThat(b.restricted(participant.phoneIdentityId(), "REGISTER")).isTrue();
    clock.advance(Duration.ofSeconds(2));
    workflows.reconcileRestrictionMessages();
    assertThat(db.count(MessageEntity.class, eq("recipientId", alice))).isEqualTo(1);
    assertThat(db.count(MessageEntity.class, eq("recipientId", organizer.id()))).isZero();
  }

  @Test
  void loginHashesTokensAndSecondLoginRevokesWithoutSlidingExpiry() {
    var user = b.need(UserEntity.class, alice, false);
    db.save(copy(user, "passwordHash", identity.passwordEncoder().encode("Password123")));
    var anonymous = new RequestContext(null, null, "login-test");
    var first = identity.login(anonymous, new Requests.Login(" ALICE ", "Password123"));
    String token =
        first.cookies().stream()
            .filter(x -> x.getName().equals("TICKET_SESSION"))
            .findFirst()
            .orElseThrow()
            .getValue();
    var principal = identity.authenticate(token);
    assertThat(b.list(LoginSessionEntity.class, null).get(0).tokenHash()).isNotEqualTo(token);
    assertThat(principal.expiresAt().toEpochMilli()).isEqualTo(b.now() + 7 * BusinessSupport.DAY);
    clock.advance(Duration.ofMinutes(1));
    assertThat(identity.authenticate(token).expiresAt()).isEqualTo(principal.expiresAt());
    var second = identity.login(anonymous, new Requests.Login("alice", "Password123"));
    String secondToken =
        second.cookies().stream()
            .filter(x -> x.getName().equals("TICKET_SESSION"))
            .findFirst()
            .orElseThrow()
            .getValue();
    var staleContext =
        new RequestContext(identity.authenticate(secondToken), UUID.randomUUID(), "stale-request");
    assertThat(second.cookies()).isNotEmpty();
    assertThatThrownBy(() -> identity.authenticate(token))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("SESSION_REVOKED"));
    identity.changePassword(actor(alice), new Requests.Password("Password123", "Changed123"));
    assertThat(b.list(LoginSessionEntity.class, nil("revokedAtMs"))).isEmpty();
    assertThatThrownBy(() -> b.actor(staleContext))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("SESSION_REVOKED"));
  }

  @Test
  void checkerCannotReadAnotherSessionsTicketAndSevenDayBoundaryRejectsAdmin() {
    UUID order = paid(alice);
    UUID other = session(event, 21, 23);
    UUID otherType = type(other, 2);
    UUID ticket = b.list(TicketEntity.class, eq("orderId", order)).get(0).id();
    var assignment = b.list(CheckerAssignmentEntity.class, eq("checkerId", checker)).get(0);
    db.save(copy(assignment, "sessionId", other));
    var checks = new CheckInUseCases(b, identity, events, orders);
    assertThatThrownBy(() -> checks.previewTicket(actor(checker), new Requests.Ticket(ticket)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("OBJECT_FORBIDDEN"));
    var e = b.need(EventEntity.class, event, false);
    db.save(copy(e, "status", "ENDED", "terminatedAtMs", b.now() - 7 * BusinessSupport.DAY));
    assertThatThrownBy(() -> b.retention(b.need(EventEntity.class, event, false)))
        .isInstanceOfSatisfying(ApiException.class, x -> assertThat(x.status()).isEqualTo(410));
  }

  @SuppressWarnings("unchecked")
  @Test
  void durableTerminationRetriesFailedAttemptWithoutLosingOriginalDeadlineOrDuplicatingNotice() {
    UUID order = paid(alice);
    var first = new java.util.concurrent.atomic.AtomicBoolean(true);
    var failingOrders =
        new OrderUseCases(
            b,
            new PaymentSimulator() {
              @Override
              public Outcome refund(long amount) {
                return first.getAndSet(false)
                    ? new Outcome(false, "首次模拟失败")
                    : new Outcome(true, null);
              }
            });
    var runner =
        new DurableWorkflows(
            b,
            events,
            failingOrders,
            mock(ObjectProvider.class),
            mock(ObjectProvider.class),
            new ControlledFiles(b, new MockEnvironment()));
    var e = b.need(EventEntity.class, event, false);
    var job = b.write(() -> runner.beginTermination(actor(admin), e, "TAKEN_DOWN", "核查属实"));
    long deadline = job.deadlineAtMs();
    runner.process(job.id());
    assertThat(b.need(JobEntity.class, job.id(), false).status()).isEqualTo("FAILED_RETRYABLE");
    assertThat(db.count(RefundAttemptEntity.class, eq("result", "FAILURE"))).isEqualTo(1);
    assertThat(b.need(OrderEntity.class, order, false).status()).isEqualTo("REGISTERED");
    clock.advance(Duration.ofSeconds(3));
    runner.process(job.id());
    var done = b.need(JobEntity.class, job.id(), false);
    assertThat(done.status()).isEqualTo("SUCCEEDED");
    assertThat(done.processed()).isEqualTo(done.total());
    assertThat(done.deadlineAtMs()).isEqualTo(deadline);
    assertThat(runner.job(copy(done, "completedAtMs", deadline + 1)).overdue()).isTrue();
    assertThat(b.need(EventEntity.class, event, false).settlementStatus()).isEqualTo("COMPLETED");
    long messages = db.count(MessageEntity.class, eq("type", "EVENT_TERMINATED"));
    runner.process(job.id());
    assertThat(db.count(MessageEntity.class, eq("type", "EVENT_TERMINATED"))).isEqualTo(messages);
    assertThat(db.count(RefundAttemptEntity.class, eq("result", "SUCCESS"))).isEqualTo(1);
  }

  @Test
  void aClosedUserRefundCannotBorrowThePlatformRefundSuccess() {
    UUID order = paid(alice);
    var failing =
        new OrderUseCases(
            b,
            new PaymentSimulator() {
              @Override
              public Outcome refund(long amount) {
                return new Outcome(false, "测试失败");
              }
            });
    var old =
        failing.requestRefund(actor(alice), order, new Requests.Refund(UUID.randomUUID(), "退票"));
    var eventRow = b.need(EventEntity.class, event, false);
    var job =
        b.write(() -> workflows.beginTermination(actor(admin), eventRow, "TAKEN_DOWN", "属实下架"));
    workflows.settleTerminationItem(job.id(), order);
    assertThatThrownBy(
            () ->
                orders.retryRefund(
                    actor(alice),
                    order,
                    old.data().request().id(),
                    new Requests.Attempt(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("REFUND_BASIS_INVALID"));
    assertThat(db.count(RefundAttemptEntity.class, eq("result", "SUCCESS"))).isEqualTo(1);
  }

  @Test
  void credentialIdempotencyFingerprintsDoNotBecomeFastPasswordHashes() {
    var request = new Requests.ResetPassword("Initial123", "人工核验", null);
    String fingerprint = b.fingerprint(request);
    assertThat(fingerprint).startsWith("$2").hasSizeLessThanOrEqualTo(64);
    assertThat(b.matchesFingerprint(fingerprint, request)).isTrue();
    assertThat(
            b.matchesFingerprint(
                fingerprint, new Requests.ResetPassword("Changed123", "人工核验", null)))
        .isFalse();
  }

  @Test
  void demoImportIsIdempotentAndPreservesExistingUsersAndOrders() {
    var prior = b.need(UserEntity.class, alice, false);
    long priorUsers = db.count(UserEntity.class, null),
        priorOrders = db.count(OrderEntity.class, null);
    var importer = new DemoDatasetService(b, identity, workflows);
    var first = importer.seed();
    assertThat(first.inserted()).isTrue();
    assertThat(first.users()).isEqualTo(priorUsers + 16);
    assertThat(first.orders()).isEqualTo(priorOrders + 7);
    assertThat(b.need(UserEntity.class, alice, false)).isEqualTo(prior);
    var admin = b.need(UserEntity.class, DemoDatasetService.key("user:admin_demo"), false);
    assertThat(admin.mustChangePassword()).isTrue();
    assertThat(admin.passwordHash()).isNotEqualTo(DemoDatasetService.ADMIN_PASSWORD);
    assertThat(
            identity
                .passwordEncoder()
                .matches(DemoDatasetService.ADMIN_PASSWORD, admin.passwordHash()))
        .isTrue();
    assertThatCode(
            () ->
                events.publicationRules(
                    b.need(EventEntity.class, DemoDatasetService.key("event:review"), false)))
        .doesNotThrowAnyException();
    var second = importer.seed();
    assertThat(second.inserted()).isFalse();
    assertThat(second.users()).isEqualTo(first.users());
    assertThat(second.orders()).isEqualTo(first.orders());
  }

  @Test
  void backgroundRecoveryExpiresOrderWithMissingTriggerOnlyOnce() {
    var id = orders.register(actor(alice), registration(UUID.randomUUID())).data().current().id();
    // Reproduce imported/restored business rows without their original durable trigger.
    db.delete(JobItemEntity.class, null);
    db.delete(JobEntity.class, null);
    clock.advance(Duration.ofMinutes(16));
    workflows.runDue();
    assertThat(b.need(OrderEntity.class, id, false).status()).isEqualTo("CANCELLED");
    assertThat(db.count(JobEntity.class, eq("type", "JOB-01"))).isEqualTo(1);
    long notifications = db.count(MessageEntity.class, eq("recipientId", alice));
    workflows.runDue();
    assertThat(db.count(JobEntity.class, eq("type", "JOB-01"))).isEqualTo(1);
    assertThat(db.count(MessageEntity.class, eq("recipientId", alice))).isEqualTo(notifications);
  }

  @Test
  void emailCanBeOmittedByMultipleUsersAndQqAddressesAreAccepted() {
    var c = new RequestContext(null, null, "email-test");
    for (int i = 1; i <= 2; i++) {
      var created =
          identity.register(
              c,
              new Requests.Register(
                  "optionalmail" + i,
                  "Password123",
                  i == 1 ? null : "  ",
                  "1399000000" + i,
                  RegisterRole.PARTICIPANT));
      assertThat(b.need(UserEntity.class, created.data().id(), false).email()).isNull();
    }
    var qq =
        identity.register(
            c,
            new Requests.Register(
                "qqmail",
                "Password123",
                " 123456@qq.com ",
                "13990000003",
                RegisterRole.PARTICIPANT));
    assertThat(b.need(UserEntity.class, qq.data().id(), false).email()).isEqualTo("123456@qq.com");
  }

  @Test
  void phoneRebindingUsesOnlyNewPhoneProofAndKeepsBusinessIdentityAndOrders() {
    var u = b.need(UserEntity.class, alice, false);
    var oldPhone = b.need(PhoneIdentityEntity.class, u.phoneIdentityId(), false).phone();
    var c = actor(alice);
    UUID orderId = orders.register(c, registration(UUID.randomUUID())).data().current().id();
    db.insert(
        new RestrictionEntity(
            UUID.randomUUID(),
            u.phoneIdentityId(),
            "REGISTER",
            b.now() - 1000,
            b.now() + 60000,
            "keep prior facts",
            admin,
            null));
    int[] verifies = {0};
    var request = new Requests.ChangePhone("13990000004", UUID.randomUUID(), "123456");
    var changed = identity.changePhone(c, request, () -> verifies[0]++);
    assertThat(changed.data().maskedPhone()).isEqualTo("139****0004");
    assertThat(changed.data().requiresLogin()).isTrue();
    assertThat(verifies[0]).isEqualTo(1);
    var current = b.need(UserEntity.class, alice, false);
    assertThat(current.phoneIdentityId()).isEqualTo(u.phoneIdentityId());
    assertThat(current.loginPhoneIdentityId()).isNotEqualTo(u.phoneIdentityId());
    assertThat(b.need(OrderEntity.class, orderId, false).phoneIdentityId())
        .isEqualTo(u.phoneIdentityId());
    assertThat(b.restricted(current.phoneIdentityId(), "REGISTER")).isTrue();
    assertThat(identity.hasPhoneAccount(oldPhone, RegisterRole.PARTICIPANT)).isFalse();
    assertThat(identity.hasPhoneAccount(request.newPhone(), RegisterRole.PARTICIPANT)).isTrue();
    identity.changePhone(c, request, () -> verifies[0]++);
    assertThat(verifies[0]).isEqualTo(1);
    var bobPhone =
        b.need(
                PhoneIdentityEntity.class,
                b.need(UserEntity.class, bob, false).phoneIdentityId(),
                false)
            .phone();
    assertThatThrownBy(
            () ->
                identity.changePhone(
                    actor(alice),
                    new Requests.ChangePhone(bobPhone, UUID.randomUUID(), "123456"),
                    () -> {}))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("PHONE_ALREADY_BOUND"));
  }

  @Test
  void phoneCodePasswordResetRevokesSessionsAndDoesNotRequireTheOldPassword() {
    var u = b.need(UserEntity.class, alice, false);
    db.save(copy(u, "passwordHash", identity.passwordEncoder().encode("Before123")));
    var c = new RequestContext(null, null, "reset-test");
    var session = identity.login(c, new Requests.Login("alice", "Before123"));
    String token =
        session.cookies().stream()
            .filter(x -> x.getName().equals("TICKET_SESSION"))
            .findFirst()
            .orElseThrow()
            .getValue();
    String phone = b.need(PhoneIdentityEntity.class, u.phoneIdentityId(), false).phone();
    identity.resetPasswordByPhone(
        c,
        new Requests.PhonePasswordReset(
            phone, RegisterRole.PARTICIPANT, UUID.randomUUID(), "123456", "After123"));
    assertThatThrownBy(() -> identity.authenticate(token)).isInstanceOf(ApiException.class);
    assertThat(identity.login(c, new Requests.Login("alice", "After123")).status()).isEqualTo(200);
    assertThat(b.need(UserEntity.class, alice, false).mustChangePassword()).isFalse();
  }

  @Test
  void detailedRecoveryApprovalRebindsResetsAndNotifiesOnlyOnce() {
    var u = b.need(UserEntity.class, alice, false);
    String phone = b.need(PhoneIdentityEntity.class, u.phoneIdentityId(), false).phone();
    var appeal =
        identity.requestRecovery(
            new RequestContext(null, null, "appeal-test"),
            new Requests.Recovery(
                "alice",
                phone,
                RegisterRole.PARTICIPANT,
                "13990000005",
                "原手机号已经停止使用，需要提供历史资料申请恢复本人账号。",
                "过去报名过校园音乐会，可以提供日期以及票种等信息请管理员核对。",
                UUID.randomUUID(),
                "123456"));
    var delivery = mock(LocalSmsDelivery.class);
    var accounts = new AdminAccountUseCases(b, identity, events, delivery);
    var c = actor(admin);
    var approval =
        new Requests.ResolveRecovery(
            RecoveryDecision.VERIFIED, "已核对原手机号、本人陈述及历史订单，确认申请人为账号本人。", "Temporary123", "本人资料核验通过");
    var response = accounts.resolveRecovery(c, appeal.data().requestId(), approval);
    assertThat(response.data().status()).isEqualTo(RecoveryStatus.RESOLVED);
    var changed = b.need(UserEntity.class, alice, false);
    assertThat(changed.mustChangePassword()).isTrue();
    assertThat(identity.passwordEncoder().matches("Temporary123", changed.passwordHash())).isTrue();
    assertThat(changed.phoneIdentityId()).isEqualTo(u.phoneIdentityId());
    assertThat(identity.hasPhoneAccount("13990000005", RegisterRole.PARTICIPANT)).isTrue();
    verify(delivery).recovery("13990000005", "alice", "Temporary123", appeal.data().requestId());
    accounts.resolveRecovery(c, appeal.data().requestId(), approval);
    verify(delivery, times(1)).recovery(anyString(), anyString(), anyString(), any());
    assertThat(response.data().statement()).contains("原手机号");
  }
}
