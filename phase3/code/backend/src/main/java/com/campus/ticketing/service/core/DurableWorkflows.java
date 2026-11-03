package com.campus.ticketing.service.core;

import static com.campus.ticketing.common.EntityRecords.*;
import static com.campus.ticketing.dao.RecordStore.Filter.*;
import static com.campus.ticketing.service.core.BusinessSupport.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dao.RecordStore;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.entity.*;
import com.campus.ticketing.enums.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DurableWorkflows {
  private final BusinessSupport b;
  private final EventUseCases events;
  private final OrderUseCases orders;
  private final ObjectProvider<ReportingUseCases> reporting;
  private final ObjectProvider<GovernanceUseCases> governance;
  private final ControlledFiles files;

  public DurableWorkflows(
      BusinessSupport b,
      EventUseCases events,
      OrderUseCases orders,
      ObjectProvider<ReportingUseCases> reporting,
      ObjectProvider<GovernanceUseCases> governance,
      ControlledFiles files) {
    this.b = b;
    this.events = events;
    this.orders = orders;
    this.reporting = reporting;
    this.governance = governance;
    this.files = files;
  }

  public Views.JobView job(JobEntity j) {
    return new Views.JobView(
        j.id(),
        j.type(),
        j.eventId(),
        JobStatus.valueOf(j.status()),
        b.time(j.dueAtMs()),
        b.time(j.deadlineAtMs()),
        b.time(j.completedAtMs()),
        j.total(),
        j.processed(),
        j.retryCount(),
        (j.completedAtMs() == null ? b.now() : j.completedAtMs()) > j.deadlineAtMs(),
        j.lastError());
  }

  public UseCaseResult<PageResult<Views.JobView>> listJobs(RequestContext c, Queries.Jobs q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          var rows =
              b
                  .ordered(
                      JobEntity.class,
                      and(
                          q.eventId() == null ? null : eq("eventId", q.eventId()),
                          q.type() == null ? null : eq("type", q.type()),
                          q.status() == null ? null : eq("status", q.status().name())),
                      RecordStore.Sort.asc("dueAtMs"),
                      RecordStore.Sort.asc("id"))
                  .stream()
                  .map(this::job)
                  .filter(j -> q.overdue() == null || j.overdue() == q.overdue())
                  .toList();
          return ok(page(rows, q.page(), q.pageSize()));
        });
  }

  public UseCaseResult<Views.JobView> jobDetail(RequestContext c, UUID jobId) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          return ok(job(b.need(JobEntity.class, jobId, false)));
        });
  }

  public UseCaseResult<Views.SettlementProgress> settlementProgress(
      RequestContext c, UUID eventId) {
    return b.read(
        () -> {
          var e = b.eventAccess(c, eventId, false, false);
          var j =
              b.db()
                  .one(JobEntity.class, and(eq("eventId", eventId), eq("type", "JOB-04")), false)
                  .orElse(null);
          return ok(
              new Views.SettlementProgress(
                  e.id(),
                  EventStatus.valueOf(e.status()),
                  b.time(b.effectiveEnd(e)),
                  SettlementStatus.valueOf(e.settlementStatus()),
                  j == null ? null : job(j)));
        });
  }

  public UseCaseResult<Views.TerminationResult> cancelEvent(
      RequestContext c, UUID eventId, Requests.VersionReason r) {
    return terminate(c, eventId, r.expectedVersion(), r, null);
  }

  public UseCaseResult<Views.TerminationResult> takedown(
      RequestContext c, UUID eventId, Requests.Takedown r) {
    return terminate(c, eventId, r.expectedVersion(), r, r.investigationId());
  }

  private UseCaseResult<Views.TerminationResult> terminate(
      RequestContext c, UUID eventId, long version, Object request, UUID investigation) {
    return b.write(
        () -> {
          b.actor(c, investigation == null ? Role.ORGANIZER : Role.ADMIN);
          b.eventAccess(c, eventId, true, false);
          String api = investigation == null ? "ORG-10" : "ADM-15";
          var x =
              b.change(
                  c,
                  api,
                  eventId,
                  request,
                  "JOB",
                  i -> b.need(JobEntity.class, i, false),
                  () -> {
                    var e = b.need(EventEntity.class, eventId, true);
                    EventUseCases.version(e.version(), version);
                    String reason;
                    require(
                        e.status().equals("PUBLISHED") && b.effectiveEnd(e) == null,
                        "STATE_CONFLICT",
                        "只能终止仍在发布且未整体结束的活动");
                    if (investigation == null) {
                      reason = trim(((Requests.VersionReason) request).reason());
                      long first =
                          b.sessions(eventId).stream()
                              .mapToLong(EventSessionEntity::startsAtMs)
                              .min()
                              .orElseThrow();
                      require(b.now() < first, "STATE_CONFLICT", "组织者只能首场开始前取消");
                      for (var s : b.sessions(eventId))
                        require(
                            b.db().count(CheckInEntity.class, eq("sessionId", s.id())) == 0,
                            "STATE_CONFLICT",
                            "已有签到不可主动取消");
                    } else {
                      var fact = b.need(InvestigationEntity.class, investigation, false);
                      valid(
                          fact.eventId().equals(eventId)
                              && fact.conclusion().equals("SUBSTANTIATED"),
                          "下架必须有本活动的属实核查依据");
                      reason = fact.reason();
                    }
                    return beginTermination(
                            c, e, investigation == null ? "CANCELLED" : "TAKEN_DOWN", reason)
                        .id();
                  });
          var e = b.need(EventEntity.class, eventId, false);
          return new UseCaseResult<>(
              202,
              new Views.TerminationResult(
                  x.operationId(),
                  x.replayed(),
                  events.manage(e),
                  b.time(e.terminatedAtMs()),
                  x.current().id(),
                  SettlementStatus.valueOf(e.settlementStatus())),
              List.of());
        });
  }

  /**
   * Must run inside the same transaction as a substantiated investigation / explicit termination.
   */
  public JobEntity beginTermination(
      RequestContext c, EventEntity e, String terminal, String reason) {
    require(e.status().equals("PUBLISHED") && b.effectiveEnd(e) == null, "STATE_CONFLICT", "活动已终结");
    long effective = b.now();
    var orderRows = b.ordered(OrderEntity.class, eq("eventId", e.id()), RecordStore.Sort.asc("id"));
    Set<UUID> recipients = new TreeSet<>(Comparator.comparing(UUID::toString));
    recipients.add(e.organizerId());
    for (var o : orderRows) recipients.add(o.userId());
    for (var s : b.sessions(e.id()))
      for (var a :
          b.list(CheckerAssignmentEntity.class, and(eq("sessionId", s.id()), nil("revokedAtMs"))))
        recipients.add(a.checkerId());
    // Acquire affected account locks in stable order before touching order/ticket state.
    for (UUID user : recipients) b.need(UserEntity.class, user, true);
    b.db()
        .save(
            copy(
                e,
                "status",
                terminal,
                "terminatedAtMs",
                effective,
                "terminationReason",
                reason,
                "settlementStatus",
                "PROCESSING",
                "updatedAtMs",
                effective,
                "version",
                e.version() + 1));
    List<JobItemEntity> items = new ArrayList<>();
    for (var o : orderRows)
      items.add(b.item("ORDER:" + o.id(), "ORDER_SETTLEMENT", o.id(), null, Map.of(), effective));
    for (UUID recipient : recipients)
      items.add(
          b.item(
              "NOTICE:" + recipient,
              "RECIPIENT_NOTICE",
              null,
              recipient,
              Map.of("reason", reason),
              effective));
    var job =
        b.enqueue("JOB-04", "TERMINATE:" + e.id(), e.id(), effective, effective + 300_000, items);
    for (var o : orderRows) {
      var s = b.need(EventSessionEntity.class, o.sessionId(), false);
      String action =
          List.of("COMPLETED", "ABSENT", "REFUNDED", "CANCELLED").contains(o.status())
              ? "KEEP"
              : s.endsAtMs() <= effective
                  ? "NATURAL_SETTLE"
                  : o.status().equals("REGISTERED")
                      ? "REFUND"
                      : o.status().equals("CHECKED_IN") ? "COMPLETE" : "CANCEL";
      b.db()
          .insert(
              new TerminationFactEntity(
                  id(),
                  e.id(),
                  job.id(),
                  o.id(),
                  effective,
                  o.status(),
                  s.endsAtMs(),
                  action,
                  reason));
      if (o.status().equals("REGISTERED")) orders.closeRefunds(o.id(), null, "平台终止，原USER依据关闭");
    }
    for (var s : b.sessions(e.id()))
      for (var a :
          b.list(CheckerAssignmentEntity.class, and(eq("sessionId", s.id()), nil("revokedAtMs")))) {
        var checker = b.need(UserEntity.class, a.checkerId(), true);
        events.disableChecker(checker, "EVENT_TERMINATED");
        b.db().save(copy(a, "revokedAtMs", effective, "revocationReason", "EVENT_TERMINATED"));
      }
    b.audit(c, "TERMINATION_ACCEPTED", "EVENT", e.id(), "SUCCESS", null, "JOB-04");
    return job;
  }

  public void naturalOrder(OrderEntity o, long effective) {
    if (o.status().equals("REGISTERED")) {
      orders.status(o, "ABSENT", "正常结束未签到");
      var ticket = b.db().one(TicketEntity.class, eq("orderId", o.id()), true).orElseThrow();
      b.db()
          .save(
              copy(
                  ticket,
                  "status",
                  "INVALID",
                  "invalidatedAtMs",
                  effective,
                  "invalidReason",
                  "正常结束未签到"));
      orders.closeRefunds(o.id(), null, "正常结束，退款依据失效");
      if (b.db()
              .count(
                  BehaviorFactEntity.class,
                  and(eq("orderId", o.id()), eq("type", "NATURAL_ABSENCE")))
          == 0)
        b.db()
            .insert(
                new BehaviorFactEntity(
                    id(),
                    o.phoneIdentityId(),
                    "NATURAL_ABSENCE",
                    o.id(),
                    null,
                    effective,
                    "正常结束未签到"));
    } else if (o.status().equals("CHECKED_IN")) {
      orders.status(o, "COMPLETED", "正常结束");
      orders.closeRefunds(o.id(), null, "已完成");
    } else if (List.of("WAITLISTED", "OFFERED", "UNPAID").contains(o.status())) {
      orders.status(o, "CANCELLED", "报名已截止/场次结束");
      b.notify(
          o.userId(),
          "CANCEL:" + o.id(),
          "ORDER_CANCELLED",
          "订单已取消",
          "报名已截止/场次结束",
          "ORDER",
          o.id(),
          null);
    }
  }

  private void locks(OrderEntity o) {
    b.need(EventEntity.class, o.eventId(), true);
    b.need(UserEntity.class, o.userId(), true);
    b.need(EventSessionEntity.class, o.sessionId(), true);
    b.need(TicketTypeEntity.class, o.ticketTypeId(), true);
    b.need(OrderEntity.class, o.id(), true);
  }

  public void reconcileSession(UUID sessionId) {
    var session = b.read(() -> b.need(EventSessionEntity.class, sessionId, false));
    require(b.now() >= session.endsAtMs(), "STATE_CONFLICT", "场次尚未结束");
    var event = b.read(() -> b.need(EventEntity.class, session.eventId(), false));
    if (Set.of("CANCELLED", "TAKEN_DOWN").contains(event.status())
        && session.endsAtMs() > event.terminatedAtMs())
      throw error(409, "EVENT_TERMINATED", "提前终止场次不能按自然缺席处理");
    for (var initial : b.read(() -> b.list(OrderEntity.class, eq("sessionId", sessionId))))
      b.write(
          () -> {
            locks(initial);
            naturalOrder(b.need(OrderEntity.class, initial.id(), true), session.endsAtMs());
            return null;
          });
    b.write(
        () -> {
          var e = b.need(EventEntity.class, session.eventId(), true);
          var s = b.need(EventSessionEntity.class, sessionId, true);
          snapshot(e, s, "NATURAL");
          b.db().save(copy(s, "settlementStatus", "COMPLETED", "version", s.version() + 1));
          finishNatural(e);
          return null;
        });
  }

  private void finishNatural(EventEntity e) {
    if (!e.status().equals("PUBLISHED") || b.effectiveEnd(e) == null) return;
    if (b.sessions(e.id()).stream().anyMatch(s -> !s.settlementStatus().equals("COMPLETED")))
      return;
    long end =
        b.sessions(e.id()).stream().mapToLong(EventSessionEntity::endsAtMs).max().orElseThrow();
    for (var s : b.sessions(e.id()))
      for (var a :
          b.list(CheckerAssignmentEntity.class, and(eq("sessionId", s.id()), nil("revokedAtMs")))) {
        events.disableChecker(b.need(UserEntity.class, a.checkerId(), true), "EVENT_ENDED");
        b.db().save(copy(a, "revokedAtMs", end, "revocationReason", "EVENT_ENDED"));
      }
    b.db()
        .save(
            copy(
                e,
                "status",
                "ENDED",
                "terminatedAtMs",
                end,
                "settlementStatus",
                "COMPLETED",
                "updatedAtMs",
                b.now(),
                "version",
                e.version() + 1));
    snapshot(e, null, "NATURAL");
  }

  public void snapshot(EventEntity e, EventSessionEntity session, String reason) {
    UUID sessionId = session == null ? null : session.id();
    if (b.db()
        .one(
            StatisticsSnapshotEntity.class,
            and(eq("eventId", e.id()), eq("sessionId", sessionId)),
            false)
        .isPresent()) return;
    var all =
        b.list(
            OrderEntity.class,
            and(eq("eventId", e.id()), session == null ? null : eq("sessionId", session.id())));
    long issued = 0, refunded = 0, checked = 0, absent = 0, waiting = 0, cumulative = 0;
    for (var o : all) {
      if (b.db().count(TicketEntity.class, eq("orderId", o.id())) > 0) issued++;
      if (o.status().equals("REFUNDED")) refunded++;
      if (List.of("CHECKED_IN", "COMPLETED").contains(o.status())) checked++;
      if (o.status().equals("ABSENT")) absent++;
      if (o.status().equals("WAITLISTED")) waiting++;
      if (o.waitlistedAtMs() != null) cumulative++;
    }
    long capacity =
        session == null
            ? b.sessions(e.id()).stream().mapToLong(EventSessionEntity::capacity).sum()
            : session.capacity();
    b.db()
        .insert(
            new StatisticsSnapshotEntity(
                id(),
                e.id(),
                sessionId,
                reason,
                b.now(),
                issued,
                refunded,
                issued - refunded,
                checked,
                absent,
                capacity,
                waiting,
                cumulative));
  }

  public UseCaseResult<Views.Reconciliation> reconcileAbsence(
      RequestContext c, UUID sessionId, Requests.Empty r) {
    var before =
        b.read(
            () -> {
              var u = b.actor(c, Role.CHECKER);
              var a =
                  b.db()
                      .one(
                          CheckerAssignmentEntity.class,
                          and(eq("checkerId", u.id()), nil("revokedAtMs")),
                          false)
                      .orElseThrow(() -> error(403, "ACCOUNT_UNAVAILABLE", "当前无授权"));
              if (!a.sessionId().equals(sessionId)) throw error(403, "OBJECT_FORBIDDEN", "只允许当前场次");
              var e =
                  b.need(
                      EventEntity.class,
                      b.need(EventSessionEntity.class, sessionId, false).eventId(),
                      false);
              b.retention(e);
              return b.list(OrderEntity.class, eq("sessionId", sessionId));
            });
    long pending =
        before.stream()
            .filter(
                o ->
                    List.of("REGISTERED", "CHECKED_IN", "UNPAID", "OFFERED", "WAITLISTED")
                        .contains(o.status()))
            .count();
    reconcileSession(sessionId);
    return b.read(
        () ->
            ok(
                new Views.Reconciliation(
                    sessionId,
                    (int) pending,
                    before.size() - (int) pending,
                    SettlementStatus.COMPLETED)));
  }

  public void promoteType(UUID typeId) {
    b.write(
        () -> {
          var type = b.need(TicketTypeEntity.class, typeId, false);
          var s = b.need(EventSessionEntity.class, type.sessionId(), false);
          var e = b.need(EventEntity.class, s.eventId(), true);
          if (!e.status().equals("PUBLISHED")
              || b.effectiveEnd(e) != null
              || b.now() < s.salesOpensAtMs()
              || b.now() >= DomainRules.checkinClose(s.startsAtMs(), s.endsAtMs())
              || type.saleStopped()) return null;
          var waiters = orders.waiting(typeId);
          var users = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
          for (var o : waiters) users.add(o.userId());
          for (UUID user : users) b.need(UserEntity.class, user, true);
          b.need(EventSessionEntity.class, s.id(), true);
          type = b.need(TicketTypeEntity.class, typeId, true);
          long slots = type.capacity() - b.occupied(typeId);
          for (var initial : waiters) {
            if (slots <= 0) break;
            var o = b.need(OrderEntity.class, initial.id(), true);
            if (!o.status().equals("WAITLISTED") || !orders.eligibleWaiter(o)) continue;
            long deadline =
                DomainRules.promotionDeadline(
                    b.now(), DomainRules.checkinClose(s.startsAtMs(), s.endsAtMs()));
            var offered =
                copy(
                    o,
                    "status",
                    "OFFERED",
                    "offeredAtMs",
                    b.now(),
                    "confirmDeadlineMs",
                    deadline,
                    "updatedAtMs",
                    b.now(),
                    "version",
                    o.version() + 1);
            b.db().save(offered);
            b.notify(
                o.userId(),
                "PROMOTION:" + o.id(),
                "PROMOTION_OFFERED",
                "候补已递补",
                "请在截止前确认名额",
                "ORDER",
                o.id(),
                deadline);
            orders.expiry(offered, deadline);
            b.audit(null, "PROMOTION", "ORDER", o.id(), "SUCCESS", null, "JOB-02");
            slots--;
          }
          return null;
        });
  }

  public void expireOrder(UUID orderId) {
    b.write(
        () -> {
          var initial = b.need(OrderEntity.class, orderId, false);
          locks(initial);
          var o = b.need(OrderEntity.class, orderId, true);
          var s = b.need(EventSessionEntity.class, o.sessionId(), false);
          boolean expire =
              List.of("WAITLISTED", "OFFERED", "UNPAID").contains(o.status())
                  && (b.now() >= DomainRules.checkinClose(s.startsAtMs(), s.endsAtMs())
                      || o.status().equals("OFFERED") && b.now() >= o.confirmDeadlineMs()
                      || o.status().equals("UNPAID") && b.now() >= o.payDeadlineMs());
          if (expire) {
            orders.status(o, "CANCELLED", "候补/确认/支付期限已到或验票已结束");
            b.notify(
                o.userId(),
                "CANCEL:" + o.id(),
                "ORDER_CANCELLED",
                "订单已过期",
                "候补/确认/支付期限已到，或验票已结束",
                "ORDER",
                o.id(),
                null);
            b.audit(null, "EXPIRE_ORDER", "ORDER", o.id(), "SUCCESS", null, "JOB-01");
          }
          return null;
        });
  }

  public void settleTerminationItem(UUID jobId, UUID orderId) {
    settleTerminationItem(jobId, orderId, null);
  }

  private void settleTerminationItem(UUID jobId, UUID orderId, JobItemEntity item) {
    boolean succeeded =
        b.write(
            () -> {
              var initial = b.need(OrderEntity.class, orderId, false);
              locks(initial);
              var o = b.need(OrderEntity.class, orderId, true);
              var f =
                  b.db()
                      .one(
                          TerminationFactEntity.class,
                          and(eq("jobId", jobId), eq("orderId", orderId)),
                          false)
                      .orElseThrow();
              switch (f.settlementAction()) {
                case "NATURAL_SETTLE" -> naturalOrder(o, f.sessionEndsAtMs());
                case "CANCEL" -> {
                  if (List.of("WAITLISTED", "OFFERED", "UNPAID").contains(o.status()))
                    orders.status(o, "CANCELLED", f.reason());
                }
                case "COMPLETE" -> {
                  if (o.status().equals("CHECKED_IN")) orders.status(o, "COMPLETED", f.reason());
                }
                case "REFUND" -> {
                  if (o.status().equals("REFUNDED")) break;
                  require(o.status().equals("REGISTERED"), "STATE_CONFLICT", "终止退款订单状态不一致");
                  var grant =
                      b.db()
                          .one(RefundRequestEntity.class, eq("terminationFactId", f.id()), true)
                          .orElse(null);
                  if (grant == null) {
                    var pay =
                        b.db()
                            .one(
                                PaymentAttemptEntity.class,
                                and(eq("orderId", o.id()), eq("result", "SUCCESS")),
                                false)
                            .orElseThrow();
                    grant =
                        new RefundRequestEntity(
                            id(),
                            o.id(),
                            pay.id(),
                            "PLATFORM_TERMINATION",
                            "ACCEPTED",
                            id(),
                            null,
                            f.effectiveAtMs(),
                            f.reason(),
                            b.need(EventSessionEntity.class, o.sessionId(), false)
                                .registrationClosesAtMs(),
                            f.statusAtEffective(),
                            b.encode(f),
                            b.fingerprint(f),
                            null,
                            id(),
                            f.id(),
                            null,
                            null);
                    b.db().insert(grant);
                  }
                  UUID attempt = grant.status().equals("FAILED") ? id() : grant.initialAttemptId();
                  var result = orders.executeRefund(null, grant, attempt, false);
                  if (!result.result().equals("SUCCESS")) return false;
                }
                default -> {}
              }
              if (item != null) completeItem(jobId, item.id());
              return true;
            });
    require(succeeded, "STATE_CONFLICT", "模拟退款失败，已保存失败尝试，任务稍后重试");
  }

  private void finishTermination(UUID jobId) {
    b.write(
        () -> {
          var j = b.need(JobEntity.class, jobId, false);
          var e = b.need(EventEntity.class, j.eventId(), true);
          String reason = e.status().equals("CANCELLED") ? "CANCEL" : "TAKEDOWN";
          for (var s : b.sessions(e.id())) {
            if (!s.settlementStatus().equals("COMPLETED")) {
              snapshot(e, s, s.endsAtMs() <= e.terminatedAtMs() ? "NATURAL" : reason);
              b.db().save(copy(s, "settlementStatus", "COMPLETED", "version", s.version() + 1));
            }
          }
          snapshot(e, null, reason);
          b.db().save(copy(e, "settlementStatus", "COMPLETED"));
          return null;
        });
  }

  public void recoverDue() {
    // Recreate missing durable triggers from authoritative rows after a crash or deployment.
    for (var candidate :
        b.read(
            () ->
                b.list(
                    OrderEntity.class, in("status", List.of("WAITLISTED", "OFFERED", "UNPAID"))))) {
      var session = b.read(() -> b.need(EventSessionEntity.class, candidate.sessionId(), false));
      long due = expiryDeadline(candidate, session);
      if (due > b.now()) continue;
      b.write(
          () -> {
            b.need(EventEntity.class, candidate.eventId(), true);
            var current = b.need(OrderEntity.class, candidate.id(), false);
            if (List.of("WAITLISTED", "OFFERED", "UNPAID").contains(current.status())) {
              var currentSession = b.need(EventSessionEntity.class, current.sessionId(), false);
              long deadline = expiryDeadline(current, currentSession);
              if (deadline <= b.now()) orders.expiry(current, deadline);
            }
            return null;
          });
    }
    for (var e : b.read(() -> b.list(EventEntity.class, eq("status", "PUBLISHED")))) {
      for (var s : b.read(() -> b.sessions(e.id()))) {
        if (s.endsAtMs() <= b.now() && !s.settlementStatus().equals("COMPLETED"))
          b.write(
              () -> {
                b.need(EventEntity.class, e.id(), true);
                b.enqueue(
                    "JOB-03",
                    "NATURAL:" + s.id(),
                    e.id(),
                    s.endsAtMs(),
                    s.endsAtMs() + 60_000,
                    List.of(
                        b.item(
                            "SESSION:" + s.id(),
                            "NATURAL",
                            null,
                            null,
                            Map.of("sessionId", s.id()),
                            s.endsAtMs())));
                return null;
              });
        if (b.now() < DomainRules.checkinClose(s.startsAtMs(), s.endsAtMs()))
          for (var t : b.read(() -> b.types(s.id())))
            if (!t.saleStopped())
              b.write(
                  () -> {
                    b.need(EventEntity.class, e.id(), true);
                    long minute = b.now() / 60_000;
                    b.enqueue(
                        "JOB-02",
                        "PROMOTE:" + t.id() + ":" + minute,
                        e.id(),
                        b.now(),
                        b.now() + 60_000,
                        List.of(
                            b.item(
                                "TYPE:" + t.id(),
                                "PROMOTE",
                                null,
                                null,
                                Map.of("typeId", t.id()),
                                b.now())));
                    return null;
                  });
      }
    }
    long bucket = b.now() / 60_000;
    for (String type : List.of("JOB-05", "JOB-06"))
      b.write(
          () -> {
            b.enqueue(
                type,
                type + ":" + bucket,
                null,
                b.now(),
                b.now() + 60_000,
                List.of(b.item(type, type, null, null, Map.of(), b.now())));
            return null;
          });
    var local = b.time(b.now());
    if (local.getDayOfWeek() == DayOfWeek.MONDAY) {
      long weekStart =
          local
              .toLocalDate()
              .minusWeeks(1)
              .atStartOfDay()
              .atOffset(ZoneOffset.ofHours(8))
              .toInstant()
              .toEpochMilli();
      long weekEnd = weekStart + 7 * DAY;
      long dueAt = local.getHour() == 0 && local.getMinute() < 5 ? b.now() + 5 * 60_000L : b.now();
      b.write(
          () -> {
            b.enqueue(
                "JOB-07",
                "WEEKLY_ABSENCE:" + weekStart,
                null,
                dueAt,
                dueAt + 3_600_000,
                List.of(
                    b.item(
                        "ABSENCE_SCAN",
                        "WEEKLY_ABSENCE",
                        null,
                        null,
                        Map.of("weekStart", weekStart, "weekEnd", weekEnd),
                        dueAt)));
            return null;
          });
    }
  }

  private long expiryDeadline(OrderEntity order, EventSessionEntity session) {
    Long ownDeadline =
        switch (order.status()) {
          case "UNPAID" -> order.payDeadlineMs();
          case "OFFERED" -> order.confirmDeadlineMs();
          default -> null;
        };
    return ownDeadline == null
        ? DomainRules.checkinClose(session.startsAtMs(), session.endsAtMs())
        : Math.min(
            ownDeadline, DomainRules.checkinClose(session.startsAtMs(), session.endsAtMs()));
  }

  private final ThreadLocal<String> activeLease = new ThreadLocal<>();

  private void completeItem(UUID jobId, UUID itemId) {
    var parent = b.need(JobEntity.class, jobId, true);
    if (activeLease.get() != null && !activeLease.get().equals(parent.leaseOwner()))
      throw error(503, "DATABASE_BUSY", "任务租约已被其他执行器接管");
    var item = b.need(JobItemEntity.class, itemId, true);
    if (!item.status().equals("SUCCEEDED"))
      b.db().save(copy(item, "status", "SUCCEEDED", "completedAtMs", b.now(), "lastError", null));
    b.db()
        .save(
            copy(
                parent,
                "processed",
                (int)
                    b.db()
                        .count(
                            JobItemEntity.class,
                            and(eq("jobId", jobId), eq("status", "SUCCEEDED"))),
                "leaseUntilMs",
                b.now() + 60_000));
  }

  public void runDue() {
    recoverDue();
    var jobs =
        b.read(
            () ->
                b.db()
                    .select(
                        JobEntity.class,
                        and(
                            in("status", List.of("PENDING", "RUNNING", "FAILED_RETRYABLE")),
                            le("nextAttemptAtMs", b.now()),
                            le("dueAtMs", b.now())),
                        List.of(RecordStore.Sort.asc("dueAtMs"), RecordStore.Sort.asc("id")),
                        50,
                        0,
                        false));
    for (var job : jobs) process(job.id());
  }

  public void process(UUID jobId) {
    String leaseOwner = UUID.randomUUID().toString();
    JobItemEntity[] failedItem = {null};
    boolean claimed =
        b.write(
            () -> {
              var j = b.need(JobEntity.class, jobId, true);
              if (j.status().equals("SUCCEEDED")
                  || j.dueAtMs() > b.now()
                  || j.nextAttemptAtMs() > b.now()
                  || j.leaseUntilMs() != null && j.leaseUntilMs() > b.now()) return false;
              b.db()
                  .save(
                      copy(
                          j,
                          "status",
                          "RUNNING",
                          "leaseOwner",
                          leaseOwner,
                          "leaseUntilMs",
                          b.now() + 60_000));
              return true;
            });
    if (!claimed) return;
    activeLease.set(leaseOwner);
    try {
      var items =
          b.read(
              () ->
                  b.ordered(
                      JobItemEntity.class,
                      and(
                          eq("jobId", jobId),
                          ne("status", "SUCCEEDED"),
                          le("nextAttemptAtMs", b.now())),
                      RecordStore.Sort.asc("id")));
      for (var item : items) {
        failedItem[0] = item;
        var j = b.read(() -> b.need(JobEntity.class, jobId, false));
        switch (item.kind()) {
          case "EXPIRY" -> expireOrder(item.orderId());
          case "PROMOTE" -> promoteType(UUID.fromString(payload(item, "typeId")));
          case "NATURAL" -> reconcileSession(UUID.fromString(payload(item, "sessionId")));
          case "ORDER_SETTLEMENT" -> settleTerminationItem(jobId, item.orderId(), item);
          case "RECIPIENT_NOTICE" ->
              b.write(
                  () -> {
                    var e = b.need(EventEntity.class, j.eventId(), false);
                    b.notify(
                        item.recipientId(),
                        "TERMINATE:" + e.id(),
                        "EVENT_TERMINATED",
                        "活动已终止",
                        e.terminationReason(),
                        "EVENT",
                        e.id(),
                        null);
                    completeItem(jobId, item.id());
                    return null;
                  });
          case "EXPORT" -> reporting.getObject().generatePending(itemPayloadId(item));
          case "JOB-05" -> cleanup();
          case "JOB-06" -> governance.getObject().reconcileDue(jobId);
          case "WEEKLY_ABSENCE" ->
              governance
                  .getObject()
                  .weeklyAbsenceScan(
                      Long.parseLong(payload(item, "weekStart")),
                      Long.parseLong(payload(item, "weekEnd")));
          default -> throw new IllegalStateException("Unknown durable item kind");
        }
        b.write(
            () -> {
              completeItem(jobId, item.id());
              return null;
            });
      }
      var complete =
          b.read(
              () -> {
                var j = b.need(JobEntity.class, jobId, false);
                return j.processed().equals(j.total());
              });
      if (complete) {
        var j = b.read(() -> b.need(JobEntity.class, jobId, false));
        if (j.type().equals("JOB-04")) finishTermination(jobId);
        b.write(
            () -> {
              var fresh = b.need(JobEntity.class, jobId, true);
              b.db()
                  .save(
                      copy(
                          fresh,
                          "status",
                          "SUCCEEDED",
                          "completedAtMs",
                          b.now(),
                          "leaseOwner",
                          null,
                          "leaseUntilMs",
                          null,
                          "lastError",
                          null));
              return null;
            });
      }
    } catch (Exception failure) {
      b.write(
          () -> {
            var j = b.need(JobEntity.class, jobId, true);
            if (!leaseOwner.equals(j.leaseOwner())) return null;
            int n = j.retryCount() + 1;
            String code =
                failure instanceof ApiException a ? a.code() : failure.getClass().getSimpleName();
            if (failedItem[0] != null) {
              var item = b.need(JobItemEntity.class, failedItem[0].id(), true);
              if (!item.status().equals("SUCCEEDED"))
                b.db()
                    .save(
                        copy(
                            item,
                            "status",
                            "FAILED_RETRYABLE",
                            "retryCount",
                            item.retryCount() + 1,
                            "nextAttemptAtMs",
                            b.now() + Math.min(30_000, 1_000L << Math.min(n, 5)),
                            "lastError",
                            code));
            }
            b.db()
                .save(
                    copy(
                        j,
                        "status",
                        "FAILED_RETRYABLE",
                        "retryCount",
                        n,
                        "nextAttemptAtMs",
                        b.now() + Math.min(30_000, 1_000L << Math.min(n, 5)),
                        "leaseOwner",
                        null,
                        "leaseUntilMs",
                        null,
                        "lastError",
                        failure instanceof ApiException a
                            ? a.code()
                            : failure.getClass().getSimpleName()));
            return null;
          });
    } finally {
      activeLease.remove();
    }
  }

  private String payload(JobItemEntity i, String name) {
    try {
      return b.json.readTree(i.payloadJson()).path(name).asText();
    } catch (Exception e) {
      throw new IllegalStateException("Invalid durable payload", e);
    }
  }

  private UUID itemPayloadId(JobItemEntity i) {
    return UUID.fromString(payload(i, "exportId"));
  }

  public void cleanup() {
    b.write(
        () -> {
          b.db().delete(AuditLogEntity.class, le("createdAtMs", b.now() - LONG_RETENTION));
          b.db().delete(OperationRecordEntity.class, le("expiresAtMs", b.now()));
          for (var export : b.list(ExportEntity.class, null)) {
            var e = b.need(EventEntity.class, export.eventId(), false);
            Long end = b.effectiveEnd(e);
            if (end != null && b.now() >= end + SHORT_RETENTION) {
              b.db().save(copy(export, "status", "EXPIRED"));
              if (export.fileId() != null) {
                var file = b.need(StoredFileEntity.class, export.fileId(), false);
                if (file.deletedAtMs() == null) {
                  files.delete(file);
                  b.db().save(copy(file, "deletedAtMs", b.now()));
                }
              }
            }
          }
          return null;
        });
  }

  public void reconcileRestrictionMessages() {
    b.write(
        () -> {
          for (var r :
              b.list(
                  RestrictionEntity.class, and(le("endsAtMs", b.now()), nil("endedMessageAtMs")))) {
            for (var u :
                b.list(
                    UserEntity.class,
                    and(
                        or(
                            eq("phoneIdentityId", r.phoneIdentityId()),
                            eq("loginPhoneIdentityId", r.phoneIdentityId())),
                        eq("role", r.kind().equals("PUBLISH") ? "ORGANIZER" : "PARTICIPANT"),
                        nil("deletedAtMs"))))
              b.notify(
                  u.id(),
                  "RESTRICTION_END:" + r.id(),
                  "RESTRICTION_ENDED",
                  "限制已结束",
                  r.reason(),
                  "ACCOUNT",
                  u.id(),
                  null);
            b.db().save(copy(r, "endedMessageAtMs", b.now()));
          }
          return null;
        });
  }
}
