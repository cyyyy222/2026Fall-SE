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
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class OrderUseCases {
  private final BusinessSupport b;
  private final PaymentSimulator simulator;
  private static final List<String> ACTIVE =
      List.of("WAITLISTED", "OFFERED", "UNPAID", "REGISTERED", "CHECKED_IN");

  public OrderUseCases(BusinessSupport b, PaymentSimulator simulator) {
    this.b = b;
    this.simulator = simulator;
  }

  public OrderEntity owned(RequestContext c, UUID id, boolean lock) {
    var u = b.actor(c, Role.PARTICIPANT);
    var o = b.need(OrderEntity.class, id, false);
    if (!o.userId().equals(u.id())) throw error(403, "OBJECT_FORBIDDEN", "无权访问他人订单");
    if (lock) {
      b.need(EventEntity.class, o.eventId(), true);
      b.need(UserEntity.class, u.id(), true);
      b.actor(c, Role.PARTICIPANT);
      b.need(EventSessionEntity.class, o.sessionId(), true);
      b.need(TicketTypeEntity.class, o.ticketTypeId(), true);
      o = b.need(OrderEntity.class, id, true);
    }
    return o;
  }

  public Views.OrderView auditOrder(OrderEntity o) {
    return copy(order(o), "allowedActions", List.of());
  }

  public Views.EventHistorySummary history(OrderEntity o) {
    var s = b.need(EventSessionEntity.class, o.sessionId(), false);
    var e = b.need(EventEntity.class, o.eventId(), false);
    return new Views.EventHistorySummary(
        e.id(),
        o.eventNameSnapshot(),
        o.categoryNameSnapshot(),
        EventStatus.valueOf(e.status()),
        s.id(),
        o.sessionNameSnapshot(),
        b.time(s.startsAtMs()),
        b.time(s.endsAtMs()),
        s.location(),
        e.terminationReason(),
        b.time(e.terminatedAtMs()));
  }

  public boolean conflict(UUID userId, EventSessionEntity target, UUID excludeOrder) {
    for (var o : b.list(OrderEntity.class, and(eq("userId", userId), in("status", ACTIVE)))) {
      if (Objects.equals(o.id(), excludeOrder)) continue;
      var s = b.need(EventSessionEntity.class, o.sessionId(), false);
      if (DomainRules.overlaps(
          target.startsAtMs(), target.endsAtMs(), s.startsAtMs(), s.endsAtMs())) return true;
    }
    return false;
  }

  public List<OrderEntity> waiting(UUID type) {
    return b.ordered(
        OrderEntity.class,
        and(eq("ticketTypeId", type), eq("status", "WAITLISTED")),
        RecordStore.Sort.asc("waitlistedAtMs"),
        RecordStore.Sort.asc("id"));
  }

  public boolean eligibleWaiter(OrderEntity o) {
    var u = b.need(UserEntity.class, o.userId(), false);
    return u.enabled()
        && u.deletedAtMs() == null
        && !b.restricted(u.phoneIdentityId(), "REGISTER")
        && !conflict(u.id(), b.need(EventSessionEntity.class, o.sessionId(), false), o.id());
  }

  private Integer position(OrderEntity order) {
    if (!order.status().equals("WAITLISTED")) return null;
    var queue = waiting(order.ticketTypeId());
    for (int i = 0; i < queue.size(); i++) if (queue.get(i).id().equals(order.id())) return i + 1;
    return null;
  }

  public Views.OrderView order(OrderEntity o) {
    var ticket = b.db().one(TicketEntity.class, eq("orderId", o.id()), false).orElse(null);
    var active =
        b.db()
            .one(
                RefundRequestEntity.class,
                and(
                    eq("orderId", o.id()),
                    eq("source", "USER"),
                    in("status", List.of("ACCEPTED", "FAILED"))),
                false)
            .orElse(null);
    var e = b.need(EventEntity.class, o.eventId(), false);
    var s = b.need(EventSessionEntity.class, o.sessionId(), false);
    boolean open =
        e.status().equals("PUBLISHED")
            && b.effectiveEnd(e) == null
            && b.now() < s.registrationClosesAtMs();
    List<String> actions = new ArrayList<>();
    if (open && List.of("WAITLISTED", "OFFERED", "UNPAID").contains(o.status()))
      actions.add("CANCEL");
    if (open && o.status().equals("OFFERED") && b.now() < o.confirmDeadlineMs())
      actions.add("CONFIRM_PROMOTION");
    if (open && o.status().equals("UNPAID") && b.now() < o.payDeadlineMs()) actions.add("PAY");
    if (o.status().equals("REGISTERED")) {
      if (active != null) {
        actions.add("REVOKE_REFUND");
        actions.add("RETRY_REFUND");
      } else if (open) actions.add("REQUEST_REFUND");
    }
    if (ticket != null) {
      actions.add("VIEW_TICKET");
      actions.add("DOWNLOAD_TICKET");
    }
    actions.add("REPORT");
    return new Views.OrderView(
        o.id(),
        o.requestId(),
        OrderStatus.valueOf(o.status()),
        o.statusReason(),
        b.time(o.createdAtMs()),
        o.attendeeNameSnapshot(),
        o.amountJiao(),
        history(o),
        o.ticketTypeId(),
        o.ticketTypeNameSnapshot(),
        b.time(o.payDeadlineMs()),
        b.time(o.confirmDeadlineMs()),
        position(o),
        o.consecutivePaymentFailures(),
        ticket == null ? null : ticket.id(),
        active == null ? null : active.id(),
        actions);
  }

  public UseCaseResult<PageResult<Views.OrderView>> listMyOrders(
      RequestContext c, Queries.Orders q) {
    return b.read(
        () -> {
          var u = b.actor(c, Role.PARTICIPANT);
          return ok(
              page(
                  b
                      .ordered(
                          OrderEntity.class,
                          and(
                              eq("userId", u.id()),
                              q.status() == null ? null : eq("status", q.status().name()),
                              q.eventId() == null ? null : eq("eventId", q.eventId())),
                          RecordStore.Sort.desc("createdAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::order)
                      .toList(),
                  q.page(),
                  q.pageSize()));
        });
  }

  public UseCaseResult<Views.OrderView> orderDetail(RequestContext c, UUID orderId) {
    return b.read(() -> ok(order(owned(c, orderId, false))));
  }

  public UseCaseResult<Views.RegistrationResult> register(
      RequestContext c, Requests.Registration r) {
    return b.write(
        () -> {
          var u = b.actor(c, Role.PARTICIPANT);
          var s = b.need(EventSessionEntity.class, r.sessionId(), false);
          var e = b.need(EventEntity.class, s.eventId(), true);
          u = b.need(UserEntity.class, u.id(), true);
          b.actor(c, Role.PARTICIPANT);
          s = b.need(EventSessionEntity.class, s.id(), true);
          var type = b.need(TicketTypeEntity.class, r.ticketTypeId(), true);
          valid(
              type.sessionId().equals(s.id())
                  && type.deletedAtMs() == null
                  && s.deletedAtMs() == null,
              "票种与场次关联不合法");
          String fp = b.fingerprint(r);
          var previous =
              b.db()
                  .one(
                      RegistrationRequestEntity.class,
                      and(eq("userId", u.id()), eq("requestId", r.requestId())),
                      true);
          if (previous.isPresent()) {
            var saved = previous.get();
            require(saved.fingerprint().equals(fp), "IDEMPOTENCY_CONFLICT", "报名请求键参数不同");
            return ok(
                new Views.RegistrationResult(
                    saved.operationId(),
                    saved.operationResult(),
                    true,
                    order(b.need(OrderEntity.class, saved.orderId(), false))));
          }
          var existing =
              b.db()
                  .one(
                      OrderEntity.class,
                      and(eq("userId", u.id()), eq("sessionId", s.id()), in("status", ACTIVE)),
                      true)
                  .orElse(null);
          UUID operation = id();
          String result = "CREATED";
          boolean replayed = false;
          if (existing != null) {
            require(
                existing.ticketTypeId().equals(type.id()), "ACTIVE_ORDER_EXISTS", "本场已有其他票种有效订单");
            result = "EXISTING_ACTIVE_ORDER";
            replayed = true;
          } else {
            open(e, s, type);
            require(
                !b.restricted(u.phoneIdentityId(), "REGISTER"),
                "REGISTRATION_RESTRICTED",
                "当前身份处于报名限制期");
            require(!conflict(u.id(), s, null), "SESSION_TIME_CONFLICT", "与本人其他有效订单时间冲突");
            boolean space =
                b.occupied(type.id()) < type.capacity()
                    && waiting(type.id()).stream().noneMatch(this::eligibleWaiter);
            existing =
                new OrderEntity(
                    id(),
                    r.requestId(),
                    u.id(),
                    u.phoneIdentityId(),
                    e.id(),
                    s.id(),
                    type.id(),
                    space ? "UNPAID" : "WAITLISTED",
                    null,
                    trim(r.attendeeName()),
                    e.name(),
                    s.name(),
                    e.categoryNameSnapshot(),
                    type.name(),
                    type.priceJiao(),
                    b.now(),
                    space ? null : b.now(),
                    null,
                    null,
                    space ? DomainRules.payDeadline(b.now(), s.registrationClosesAtMs()) : null,
                    0,
                    b.now(),
                    0L);
            b.db().insert(existing);
            expiry(existing, space ? existing.payDeadlineMs() : s.registrationClosesAtMs());
          }
          b.db()
              .insert(
                  new RegistrationRequestEntity(
                      id(), u.id(), r.requestId(), fp, existing.id(), operation, result, b.now()));
          b.audit(c, "ME-06", "ORDER", existing.id(), "SUCCESS", null, null);
          return created(
              new Views.RegistrationResult(operation, result, replayed, order(existing)), replayed);
        });
  }

  public void open(EventEntity e, EventSessionEntity s, TicketTypeEntity type) {
    require(
        e.status().equals("PUBLISHED") && b.effectiveEnd(e) == null, "EVENT_TERMINATED", "活动不可交易");
    require(b.now() >= s.salesOpensAtMs(), "SALES_NOT_OPEN", "尚未放票");
    require(b.now() < s.registrationClosesAtMs(), "REGISTRATION_CLOSED", "报名已截止");
    if (type != null) require(!type.saleStopped(), "SALE_STOPPED", "票种已停售");
  }

  public UseCaseResult<Views.RegistrationResult> findByRequest(RequestContext c, UUID requestId) {
    return b.read(
        () -> {
          var u = b.actor(c, Role.PARTICIPANT);
          var r =
              b.db()
                  .one(
                      RegistrationRequestEntity.class,
                      and(eq("userId", u.id()), eq("requestId", requestId)),
                      false)
                  .orElseThrow(() -> error(404, "RESOURCE_NOT_FOUND", "未找到持久受理记录"));
          return ok(
              new Views.RegistrationResult(
                  r.operationId(),
                  r.operationResult(),
                  true,
                  order(b.need(OrderEntity.class, r.orderId(), false))));
        });
  }

  public void status(OrderEntity o, String status, String reason) {
    b.db()
        .save(
            copy(
                o,
                "status",
                status,
                "statusReason",
                reason,
                "updatedAtMs",
                b.now(),
                "version",
                o.version() + 1));
  }

  public void expiry(OrderEntity order, long deadline) {
    b.enqueue(
        "JOB-01",
        "EXPIRY:" + order.id() + ":" + deadline,
        order.eventId(),
        deadline,
        deadline + 60_000,
        List.of(b.item("ORDER:" + order.id(), "EXPIRY", order.id(), null, Map.of(), deadline)));
  }

  public UseCaseResult<Mutation<Views.OrderView>> cancel(
      RequestContext c, UUID orderId, Requests.Cancel r) {
    return b.write(
        () -> {
          var o = owned(c, orderId, true);
          var x =
              b.change(
                  c,
                  "ME-10",
                  orderId,
                  r,
                  "ORDER",
                  i -> order(b.need(OrderEntity.class, i, false)),
                  () -> {
                    var current = b.need(OrderEntity.class, orderId, true);
                    open(
                        b.need(EventEntity.class, current.eventId(), false),
                        b.need(EventSessionEntity.class, current.sessionId(), false),
                        null);
                    require(
                        List.of("WAITLISTED", "OFFERED", "UNPAID").contains(current.status()),
                        "STATE_CONFLICT",
                        "当前订单不可取消");
                    status(current, "CANCELLED", trim(r.reason()));
                    b.notify(
                        current.userId(),
                        "CANCEL:" + current.id(),
                        "ORDER_CANCELLED",
                        "订单已取消",
                        Objects.requireNonNullElse(trim(r.reason()), "本人主动取消"),
                        "ORDER",
                        current.id(),
                        null);
                    return orderId;
                  });
          return ok(x.mutation());
        });
  }

  public UseCaseResult<Views.QueueView> queuePosition(RequestContext c, UUID orderId) {
    return b.read(
        () -> {
          var o = owned(c, orderId, false);
          boolean skipped = o.status().equals("WAITLISTED") && !eligibleWaiter(o);
          return ok(
              new Views.QueueView(
                  o.id(),
                  OrderStatus.valueOf(o.status()),
                  position(o),
                  b.time(o.confirmDeadlineMs()),
                  b.time(o.payDeadlineMs()),
                  skipped,
                  skipped ? "当前资格暂不满足，保留原队列顺序" : null));
        });
  }

  public UseCaseResult<Mutation<Views.OrderView>> confirmPromotion(
      RequestContext c, UUID orderId, Requests.Empty r) {
    return b.write(
        () -> {
          owned(c, orderId, true);
          var x =
              b.change(
                  c,
                  "ME-12",
                  orderId,
                  r,
                  "ORDER",
                  i -> order(b.need(OrderEntity.class, i, false)),
                  () -> {
                    var o = b.need(OrderEntity.class, orderId, true);
                    var s = b.need(EventSessionEntity.class, o.sessionId(), false);
                    open(b.need(EventEntity.class, o.eventId(), false), s, null);
                    require(o.status().equals("OFFERED"), "STATE_CONFLICT", "订单不在待确认状态");
                    require(b.now() < o.confirmDeadlineMs(), "PROMOTION_EXPIRED", "递补确认已过期");
                    var confirmed =
                        copy(
                            o,
                            "status",
                            "UNPAID",
                            "payDeadlineMs",
                            DomainRules.payDeadline(b.now(), s.registrationClosesAtMs()),
                            "updatedAtMs",
                            b.now(),
                            "version",
                            o.version() + 1);
                    b.db().save(confirmed);
                    expiry(confirmed, confirmed.payDeadlineMs());
                    return orderId;
                  });
          return ok(x.mutation());
        });
  }

  public Views.PaymentAttempt payment(PaymentAttemptEntity a) {
    return new Views.PaymentAttempt(
        a.id(),
        a.attemptId(),
        a.orderId(),
        AttemptResult.valueOf(a.result()),
        a.amountJiao(),
        b.time(a.createdAtMs()),
        a.failureReason());
  }

  public Views.RefundRequest refundRequest(RefundRequestEntity r) {
    return new Views.RefundRequest(
        r.id(),
        r.orderId(),
        RefundSource.valueOf(r.source()),
        RefundRequestStatus.valueOf(r.status()),
        b.time(r.acceptedAtMs()),
        r.initialAttemptId(),
        r.reason(),
        new Views.RefundEligibility(
            b.time(r.registrationClosesAtMs()), OrderStatus.valueOf(r.orderStatusAtAcceptance())),
        b.time(r.revokedAtMs()),
        r.closureReason());
  }

  public Views.RefundAttempt refundAttempt(RefundAttemptEntity a) {
    return new Views.RefundAttempt(
        a.id(),
        a.attemptId(),
        a.requestId(),
        a.orderId(),
        a.originalPaymentId(),
        AttemptResult.valueOf(a.result()),
        a.amountJiao(),
        b.time(a.createdAtMs()),
        a.failureReason());
  }

  public Views.TicketView ticket(TicketEntity t) {
    var o = b.need(OrderEntity.class, t.orderId(), false);
    var check = b.db().one(CheckInEntity.class, eq("ticketId", t.id()), false).orElse(null);
    var s = b.need(EventSessionEntity.class, o.sessionId(), false);
    var e = b.need(EventEntity.class, o.eventId(), false);
    boolean usable =
        t.status().equals("VALID")
            && o.status().equals("REGISTERED")
            && e.status().equals("PUBLISHED")
            && b.now() < s.endsAtMs()
            && b.effectiveEnd(e) == null;
    return new Views.TicketView(
        t.id(),
        o.id(),
        TicketStatus.valueOf(t.status()),
        o.attendeeNameSnapshot(),
        history(o),
        o.ticketTypeNameSnapshot(),
        o.amountJiao(),
        check == null ? null : b.time(check.checkedAtMs()),
        usable,
        usable
            ? null
            : Objects.requireNonNullElse(
                t.invalidReason(), t.status().equals("CHECKED") ? "已验票，不可再次使用" : "活动/票已不可验票"));
  }

  public UseCaseResult<Views.PaymentResult> pay(
      RequestContext c, UUID orderId, Requests.Attempt r) {
    return b.write(
        () -> {
          var o = owned(c, orderId, true);
          var previous =
              b.db()
                  .one(
                      PaymentAttemptEntity.class,
                      and(eq("orderId", orderId), eq("attemptId", r.attemptId())),
                      false)
                  .orElse(null);
          if (previous == null)
            previous =
                b.db()
                    .one(
                        PaymentAttemptEntity.class,
                        and(eq("orderId", orderId), eq("result", "SUCCESS")),
                        false)
                    .orElse(null);
          if (previous != null) return ok(paymentResult(previous, true));
          var s = b.need(EventSessionEntity.class, o.sessionId(), false);
          open(b.need(EventEntity.class, o.eventId(), false), s, null);
          require(o.status().equals("UNPAID"), "STATE_CONFLICT", "订单不是待支付状态");
          require(b.now() < o.payDeadlineMs(), "PAYMENT_EXPIRED", "支付已过期");
          var outcome = simulator.pay(o.amountJiao());
          var attempt =
              new PaymentAttemptEntity(
                  id(),
                  orderId,
                  r.attemptId(),
                  id(),
                  outcome.success() ? "SUCCESS" : "FAILURE",
                  o.amountJiao(),
                  outcome.reason(),
                  b.now());
          b.db().insert(attempt);
          if (outcome.success()) {
            status(o, "REGISTERED", null);
            b.db().insert(new TicketEntity(id(), o.id(), "VALID", b.now(), null, null));
            b.notify(
                o.userId(),
                "PAYMENT:" + attempt.id(),
                "PAYMENT_SUCCESS",
                "支付出票成功",
                "电子票已生成",
                "ORDER",
                o.id(),
                null);
          } else {
            int failures = o.consecutivePaymentFailures() + 1;
            b.db()
                .save(
                    copy(
                        o,
                        "consecutivePaymentFailures",
                        failures,
                        "status",
                        failures >= 3 ? "CANCELLED" : "UNPAID",
                        "statusReason",
                        failures >= 3 ? "第三次不同支付尝试失败" : o.statusReason(),
                        "updatedAtMs",
                        b.now(),
                        "version",
                        o.version() + 1));
            if (failures >= 3)
              b.notify(
                  o.userId(),
                  "CANCEL:" + o.id(),
                  "ORDER_CANCELLED",
                  "订单已取消",
                  "第三次不同支付尝试失败",
                  "ORDER",
                  o.id(),
                  null);
          }
          b.audit(
              c, "ME-13", "PAYMENT", attempt.id(), attempt.result(), attempt.failureReason(), null);
          return created(paymentResult(attempt, false), false);
        });
  }

  private Views.PaymentResult paymentResult(PaymentAttemptEntity a, boolean replayed) {
    var o = b.need(OrderEntity.class, a.orderId(), false);
    var t = b.db().one(TicketEntity.class, eq("orderId", o.id()), false).orElse(null);
    return new Views.PaymentResult(
        a.operationId(), payment(a), replayed, order(o), t == null ? null : ticket(t));
  }

  public UseCaseResult<Views.PaymentAudit> paymentHistory(RequestContext c, UUID orderId) {
    return b.read(
        () -> {
          owned(c, orderId, false);
          return ok(
              new Views.PaymentAudit(
                  b
                      .ordered(
                          PaymentAttemptEntity.class,
                          eq("orderId", orderId),
                          RecordStore.Sort.asc("createdAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::payment)
                      .toList(),
                  b
                      .ordered(
                          RefundRequestEntity.class,
                          eq("orderId", orderId),
                          RecordStore.Sort.asc("acceptedAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::refundRequest)
                      .toList(),
                  b
                      .ordered(
                          RefundAttemptEntity.class,
                          eq("orderId", orderId),
                          RecordStore.Sort.asc("createdAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::refundAttempt)
                      .toList()));
        });
  }

  public UseCaseResult<Views.RefundResult> requestRefund(
      RequestContext c, UUID orderId, Requests.Refund r) {
    // T1 commits the original legal grant before T2. Never surround these calls with an outer
    // transaction.
    var accepted =
        b.write(
            () -> {
              var o = owned(c, orderId, true);
              var durable =
                  b.db()
                      .one(
                          RefundRequestEntity.class,
                          and(
                              eq("acceptedBy", c.actor().userId()),
                              eq("idempotencyKey", c.idempotencyKey())),
                          true)
                      .orElse(null);
              if (durable != null) {
                require(
                    durable.orderId().equals(orderId)
                        && durable.requestFingerprint().equals(b.fingerprint(r)),
                    "IDEMPOTENCY_CONFLICT",
                    "原退款受理键参数不同");
                return new BusinessSupport.Change<>(durable.acceptedOperationId(), true, durable);
              }
              var result =
                  b.change(
                      c,
                      "ME-15",
                      orderId,
                      r,
                      "REFUND_REQUEST",
                      i -> b.need(RefundRequestEntity.class, i, false),
                      () -> {
                        var s = b.need(EventSessionEntity.class, o.sessionId(), false);
                        open(b.need(EventEntity.class, o.eventId(), false), s, null);
                        require(
                            o.status().equals("REGISTERED"),
                            "REFUND_NOT_ALLOWED",
                            "仅截止前未签到已出票订单可新申请退款");
                        require(
                            b.db()
                                    .count(
                                        RefundRequestEntity.class,
                                        and(
                                            eq("orderId", orderId),
                                            eq("source", "USER"),
                                            in("status", List.of("ACCEPTED", "FAILED"))))
                                == 0,
                            "REFUND_REQUEST_EXISTS",
                            "本单已有未关闭退款依据，请查询原依据");
                        var pay =
                            b.db()
                                .one(
                                    PaymentAttemptEntity.class,
                                    and(eq("orderId", orderId), eq("result", "SUCCESS")),
                                    false)
                                .orElseThrow(() -> error(409, "REFUND_NOT_ALLOWED", "缺少原成功支付"));
                        UUID grant = id();
                        b.db()
                            .insert(
                                new RefundRequestEntity(
                                    grant,
                                    orderId,
                                    pay.id(),
                                    "USER",
                                    "ACCEPTED",
                                    r.attemptId(),
                                    c.actor().userId(),
                                    b.now(),
                                    trim(r.reason()),
                                    s.registrationClosesAtMs(),
                                    o.status(),
                                    b.encode(
                                        Map.of(
                                            "orderId",
                                            orderId,
                                            "eventId",
                                            o.eventId(),
                                            "sessionId",
                                            s.id(),
                                            "originalPaymentId",
                                            pay.id(),
                                            "amountJiao",
                                            o.amountJiao(),
                                            "registrationClosesAtMs",
                                            s.registrationClosesAtMs(),
                                            "status",
                                            o.status())),
                                    b.fingerprint(r),
                                    c.idempotencyKey(),
                                    id(),
                                    null,
                                    null,
                                    null));
                        return grant;
                      });
              var grant = b.need(RefundRequestEntity.class, result.current().id(), true);
              b.db().save(copy(grant, "acceptedOperationId", result.operationId()));
              var op = b.need(OperationRecordEntity.class, result.operationId(), true);
              b.db().save(copy(op, "phase", "ACCEPTED"));
              return new BusinessSupport.Change<>(
                  result.operationId(),
                  result.replayed(),
                  b.need(RefundRequestEntity.class, grant.id(), false));
            });
    return b.write(
        () -> {
          owned(c, orderId, true);
          var current = b.need(RefundRequestEntity.class, accepted.current().id(), true);
          var a = executeRefund(c, current, current.initialAttemptId(), false);
          b.db()
              .get(OperationRecordEntity.class, accepted.operationId(), true)
              .ifPresent(op -> b.db().save(copy(op, "phase", "COMPLETED")));
          return new UseCaseResult<>(
              accepted.replayed() ? 200 : 201,
              refundResult(current.id(), a, accepted.replayed()),
              List.of());
        });
  }

  public RefundAttemptEntity executeRefund(
      RequestContext c, RefundRequestEntity grant, UUID attemptId, boolean admin) {
    var previous =
        b.db()
            .one(
                RefundAttemptEntity.class,
                and(eq("requestId", grant.id()), eq("attemptId", attemptId)),
                false)
            .orElse(null);
    if (previous != null) return previous;
    var success =
        b.db()
            .one(
                RefundAttemptEntity.class,
                and(eq("orderId", grant.orderId()), eq("result", "SUCCESS")),
                false)
            .orElse(null);
    if (success != null) {
      require(
          success.requestId().equals(grant.id()),
          "REFUND_BASIS_INVALID",
          "本单已由另一退款依据成功处理，当前依据不可执行");
      return success;
    }
    require(
        !List.of("REVOKED", "SUCCEEDED").contains(grant.status()),
        "REFUND_BASIS_INVALID",
        "原退款依据已关闭");
    require(
        !grant.status().equals("ACCEPTED") || grant.initialAttemptId().equals(attemptId),
        "REFUND_BASIS_INVALID",
        "尚未得到初次尝试结果，只能恢复初次attemptId");
    if (admin)
      require(grant.status().equals("FAILED"), "REFUND_BASIS_INVALID", "管理员只可重试已有持久失败的合法依据");
    var o = b.need(OrderEntity.class, grant.orderId(), true);
    require(o.status().equals("REGISTERED"), "REFUND_BASIS_INVALID", "订单已签到或终态，依据不可执行");
    var currentTicket =
        b.db()
            .one(TicketEntity.class, eq("orderId", o.id()), true)
            .orElseThrow(() -> error(409, "REFUND_BASIS_INVALID", "原电子票缺失"));
    require(
        currentTicket.status().equals("VALID")
            && b.db().count(CheckInEntity.class, eq("ticketId", currentTicket.id())) == 0,
        "REFUND_BASIS_INVALID",
        "原电子票已经使用或失效");
    var s = b.need(EventSessionEntity.class, o.sessionId(), false);
    require(
        b.now() < s.endsAtMs() || grant.source().equals("PLATFORM_TERMINATION"),
        "REFUND_BASIS_INVALID",
        "场次已自然结束");
    var pay = b.need(PaymentAttemptEntity.class, grant.originalPaymentId(), false);
    require(
        pay.orderId().equals(o.id())
            && pay.result().equals("SUCCESS")
            && pay.amountJiao().equals(o.amountJiao()),
        "REFUND_BASIS_INVALID",
        "原成功支付关联不一致");
    var outcome = simulator.refund(pay.amountJiao());
    var attempt =
        new RefundAttemptEntity(
            id(),
            grant.id(),
            o.id(),
            pay.id(),
            attemptId,
            id(),
            outcome.success() ? "SUCCESS" : "FAILURE",
            pay.amountJiao(),
            outcome.reason(),
            b.now());
    b.db().insert(attempt);
    b.db().save(copy(grant, "status", outcome.success() ? "SUCCEEDED" : "FAILED"));
    if (outcome.success()) {
      status(o, "REFUNDED", grant.reason());
      var t = b.db().one(TicketEntity.class, eq("orderId", o.id()), true).orElseThrow();
      b.db().save(copy(t, "status", "INVALID", "invalidatedAtMs", b.now(), "invalidReason", "已退款"));
      closeRefunds(o.id(), grant.id(), "本单已成功退款");
      if (grant.source().equals("USER")
          && b.db()
                  .count(
                      BehaviorFactEntity.class,
                      and(eq("orderId", o.id()), eq("type", "VOLUNTARY_REFUND")))
              == 0)
        b.db()
            .insert(
                new BehaviorFactEntity(
                    id(),
                    o.phoneIdentityId(),
                    "VOLUNTARY_REFUND",
                    o.id(),
                    null,
                    b.now(),
                    grant.reason()));
      b.notify(
          o.userId(),
          "REFUND:" + attempt.id(),
          "REFUND_SUCCESS",
          "退款成功",
          grant.reason(),
          "ORDER",
          o.id(),
          null);
    }
    b.audit(
        c,
        "REFUND_ATTEMPT",
        "REFUND",
        attempt.id(),
        attempt.result(),
        attempt.failureReason(),
        grant.source().equals("PLATFORM_TERMINATION") ? "JOB-04" : null);
    return attempt;
  }

  public void closeRefunds(UUID orderId, UUID exclude, String reason) {
    for (var r :
        b.list(
            RefundRequestEntity.class,
            and(eq("orderId", orderId), in("status", List.of("ACCEPTED", "FAILED")))))
      if (!Objects.equals(r.id(), exclude))
        b.db().save(copy(r, "status", "REVOKED", "revokedAtMs", b.now(), "closureReason", reason));
  }

  private Views.RefundResult refundResult(UUID request, RefundAttemptEntity a, boolean replayed) {
    return new Views.RefundResult(
        a.operationId(),
        refundRequest(b.need(RefundRequestEntity.class, request, false)),
        refundAttempt(a),
        replayed,
        order(b.need(OrderEntity.class, a.orderId(), false)));
  }

  public UseCaseResult<Views.MyRefundDetail> refundDetail(
      RequestContext c, UUID orderId, UUID requestId) {
    return b.read(
        () -> {
          var o = owned(c, orderId, false);
          var r = b.need(RefundRequestEntity.class, requestId, false);
          valid(r.orderId().equals(orderId), "退款依据不属于此订单");
          return ok(
              new Views.MyRefundDetail(
                  refundRequest(r),
                  b
                      .ordered(
                          RefundAttemptEntity.class,
                          eq("requestId", requestId),
                          RecordStore.Sort.asc("createdAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::refundAttempt)
                      .toList(),
                  order(o)));
        });
  }

  public UseCaseResult<Views.RefundResult> retryRefund(
      RequestContext c, UUID orderId, UUID requestId, Requests.Attempt r) {
    return b.write(
        () -> {
          owned(c, orderId, true);
          var grant = b.need(RefundRequestEntity.class, requestId, true);
          valid(grant.orderId().equals(orderId) && grant.source().equals("USER"), "退款依据不属于该单本人申请");
          var x =
              b.change(
                  c,
                  "ME-17",
                  requestId,
                  r,
                  "REFUND",
                  i -> b.need(RefundAttemptEntity.class, i, false),
                  () -> executeRefund(c, grant, r.attemptId(), false).id());
          return created(refundResult(requestId, x.current(), x.replayed()), x.replayed());
        });
  }

  public UseCaseResult<Mutation<Views.RefundRequest>> revokeRequest(
      RequestContext c, UUID orderId, UUID requestId, Requests.Empty r) {
    return b.write(
        () -> {
          owned(c, orderId, true);
          var grant = b.need(RefundRequestEntity.class, requestId, true);
          valid(grant.orderId().equals(orderId) && grant.source().equals("USER"), "只能撤销本人该单申请");
          var x =
              b.change(
                  c,
                  "ME-18",
                  requestId,
                  r,
                  "REFUND_REQUEST",
                  i -> refundRequest(b.need(RefundRequestEntity.class, i, false)),
                  () -> {
                    require(
                        List.of("ACCEPTED", "FAILED").contains(grant.status()),
                        "REFUND_BASIS_INVALID",
                        "依据已成功或撤销");
                    b.db()
                        .save(
                            copy(
                                grant,
                                "status",
                                "REVOKED",
                                "revokedAtMs",
                                b.now(),
                                "closureReason",
                                "本人撤销"));
                    return requestId;
                  });
          return ok(x.mutation());
        });
  }

  public UseCaseResult<Views.TicketView> ticketDetail(RequestContext c, UUID ticketId) {
    return b.read(
        () -> {
          var t = b.need(TicketEntity.class, ticketId, false);
          owned(c, t.orderId(), false);
          return ok(ticket(t));
        });
  }

  public DownloadResult downloadText(RequestContext c, UUID ticketId) {
    return b.read(
        () -> {
          var t = b.need(TicketEntity.class, ticketId, false);
          owned(c, t.orderId(), false);
          var v = ticket(t);
          var h = v.event();
          String content =
              "电子票\r\n姓名："
                  + v.attendeeName()
                  + "\r\n票号："
                  + v.id()
                  + "\r\n活动："
                  + h.eventName()
                  + "\r\n场次："
                  + h.sessionName()
                  + "\r\n起止："
                  + h.startsAt()
                  + " 至 "
                  + h.endsAt()
                  + "\r\n地点："
                  + h.location()
                  + "\r\n票种："
                  + v.ticketTypeName()
                  + "\r\n实付："
                  + java.math.BigDecimal.valueOf(v.paidAmountJiao(), 1).toPlainString()
                  + "元\r\n状态："
                  + v.status()
                  + "\r\n"
                  + (v.usable() ? "请在验票时间内使用" : "不可再次验票：" + v.invalidReason())
                  + "\r\n";
          return new DownloadResult(
              new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)),
              new MediaType("text", "plain", StandardCharsets.UTF_8),
              "ticket-" + ticketId + ".txt",
              true);
        });
  }
}
