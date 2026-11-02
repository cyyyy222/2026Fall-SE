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
import org.springframework.stereotype.Service;

@Service
public class ReportingUseCases {
  private final BusinessSupport b;
  private final OrderUseCases orders;
  private final DurableWorkflows workflows;
  private final ControlledFiles files;

  public ReportingUseCases(
      BusinessSupport b, OrderUseCases orders, DurableWorkflows workflows, ControlledFiles files) {
    this.b = b;
    this.orders = orders;
    this.workflows = workflows;
    this.files = files;
  }

  private EventEntity access(RequestContext c, UUID eventId, UUID sessionId, boolean checker) {
    var e = b.eventAccess(c, eventId, false, checker);
    b.retention(e);
    var u = b.actor(c);
    if (u.role().equals("CHECKER")) {
      var assigned =
          b.db()
              .one(
                  CheckerAssignmentEntity.class,
                  and(eq("checkerId", u.id()), nil("revokedAtMs")),
                  false)
              .orElseThrow(() -> error(403, "ACCOUNT_UNAVAILABLE", "授权失效"));
      if (sessionId == null || !assigned.sessionId().equals(sessionId))
        throw error(403, "OBJECT_FORBIDDEN", "检票员必须且只能指定当前授权场次");
    }
    if (sessionId != null) {
      var s = b.need(EventSessionEntity.class, sessionId, false);
      valid(s.eventId().equals(eventId) && s.deletedAtMs() == null, "场次不属于活动");
    }
    return e;
  }

  private void typeFilter(UUID eventId, UUID sessionId, UUID typeId) {
    if (typeId == null) return;
    var t = b.need(TicketTypeEntity.class, typeId, false);
    var s = b.need(EventSessionEntity.class, t.sessionId(), false);
    valid(
        t.deletedAtMs() == null
            && s.eventId().equals(eventId)
            && (sessionId == null || s.id().equals(sessionId)),
        "票种不属于目标活动/场次");
  }

  private List<Views.AttendeeRow> attendees(
      UUID eventId, UUID sessionId, UUID typeId, OrderStatus status, CheckinStatus checked) {
    typeFilter(eventId, sessionId, typeId);
    var all =
        b.ordered(
            OrderEntity.class,
            and(
                eq("eventId", eventId),
                sessionId == null ? null : eq("sessionId", sessionId),
                typeId == null ? null : eq("ticketTypeId", typeId),
                status == null ? null : eq("status", status.name())),
            RecordStore.Sort.asc("sessionId"),
            RecordStore.Sort.asc("id"));
    List<Views.AttendeeRow> rows = new ArrayList<>();
    for (var o : all) {
      var t = b.db().one(TicketEntity.class, eq("orderId", o.id()), false).orElse(null);
      boolean done = t != null && b.db().count(CheckInEntity.class, eq("ticketId", t.id())) > 0;
      CheckinStatus state = done ? CheckinStatus.CHECKED : CheckinStatus.NOT_CHECKED;
      if (checked != null && checked != state) continue;
      rows.add(
          new Views.AttendeeRow(
              o.attendeeNameSnapshot(),
              o.sessionId(),
              o.sessionNameSnapshot(),
              o.ticketTypeId(),
              o.ticketTypeNameSnapshot(),
              OrderStatus.valueOf(o.status()),
              state,
              t == null ? null : t.id()));
    }
    return rows;
  }

  public UseCaseResult<PageResult<Views.AttendeeRow>> listAttendees(
      RequestContext c, UUID eventId, Queries.Attendees q) {
    return b.read(
        () -> {
          access(c, eventId, q.sessionId(), true);
          return ok(
              page(
                  attendees(
                      eventId, q.sessionId(), q.ticketTypeId(), q.orderStatus(), q.checkinStatus()),
                  q.page(),
                  q.pageSize()));
        });
  }

  private Views.ExportView export(ExportEntity x) {
    var e = b.need(EventEntity.class, x.eventId(), false);
    Long end = b.effectiveEnd(e);
    return new Views.ExportView(
        x.id(),
        e.id(),
        ExportStatus.valueOf(x.status()),
        x.rowCount(),
        b.time(x.createdAtMs()),
        end == null ? null : b.time(end + SHORT_RETENTION),
        x.status().equals("SUCCEEDED") ? "/api/management/exports/" + x.id() + "/download" : null,
        x.failureReason());
  }

  public UseCaseResult<Views.ExportView> createExport(
      RequestContext c, UUID eventId, Requests.Export r) {
    return b.write(
        () -> {
          b.actor(c, Role.ORGANIZER, Role.ADMIN);
          access(c, eventId, r.sessionId(), false);
          b.need(EventEntity.class, eventId, true);
          typeFilter(eventId, r.sessionId(), r.ticketTypeId());
          var x =
              b.change(
                  c,
                  "MGT-02",
                  eventId,
                  r,
                  "EXPORT",
                  i -> export(b.need(ExportEntity.class, i, false)),
                  () -> {
                    UUID export = id();
                    var job =
                        b.enqueue(
                            "JOB-07",
                            "EXPORT:" + export,
                            eventId,
                            b.now(),
                            b.now() + 60_000,
                            List.of(
                                b.item(
                                    "EXPORT:" + export,
                                    "EXPORT",
                                    null,
                                    null,
                                    Map.of("exportId", export),
                                    b.now())));
                    b.db()
                        .insert(
                            new ExportEntity(
                                export,
                                eventId,
                                c.actor().userId(),
                                job.id(),
                                "PENDING",
                                r.sessionId(),
                                r.ticketTypeId(),
                                r.orderStatus() == null ? null : r.orderStatus().name(),
                                r.checkinStatus() == null ? null : r.checkinStatus().name(),
                                null,
                                null,
                                b.now(),
                                null,
                                null));
                    return export;
                  });
          return new UseCaseResult<>(202, x.current(), List.of());
        });
  }

  private ExportEntity allowedExport(RequestContext c, UUID exportId) {
    b.actor(c, Role.ADMIN, Role.ORGANIZER);
    var x = b.need(ExportEntity.class, exportId, false);
    access(c, x.eventId(), x.sessionId(), false);
    return x;
  }

  public UseCaseResult<Views.ExportView> exportStatus(RequestContext c, UUID exportId) {
    return b.read(() -> ok(export(allowedExport(c, exportId))));
  }

  public DownloadResult downloadCsv(RequestContext c, UUID exportId) {
    return b.write(
        () -> {
          var x = allowedExport(c, exportId);
          require(
              x.status().equals("SUCCEEDED") && x.fileId() != null, "STATE_CONFLICT", "CSV尚未生成完成");
          var file = b.need(StoredFileEntity.class, x.fileId(), false);
          b.audit(c, "MGT-04", "EXPORT", exportId, "SUCCESS", null, null);
          return files.download(file, "attendees-" + exportId + ".csv", true);
        });
  }

  public void generatePending(UUID exportId) {
    record Prepared(ExportEntity export, List<Views.AttendeeRow> rows) {}
    Prepared prepared =
        b.read(
            () -> {
              var x = b.need(ExportEntity.class, exportId, false);
              var e = b.need(EventEntity.class, x.eventId(), false);
              b.retention(e);
              var creator = b.need(UserEntity.class, x.createdBy(), false);
              require(
                  creator.enabled()
                      && creator.deletedAtMs() == null
                      && (creator.role().equals("ADMIN")
                          || creator.role().equals("ORGANIZER")
                              && e.organizerId().equals(creator.id())),
                  "OBJECT_FORBIDDEN",
                  "导出创建者已无权限");
              return new Prepared(
                  x,
                  attendees(
                      x.eventId(),
                      x.sessionId(),
                      x.ticketTypeId(),
                      x.orderStatusFilter() == null
                          ? null
                          : OrderStatus.valueOf(x.orderStatusFilter()),
                      x.checkinStatusFilter() == null
                          ? null
                          : CheckinStatus.valueOf(x.checkinStatusFilter())));
            });
    if (prepared.export().status().equals("SUCCEEDED")) return;
    StringBuilder csv = new StringBuilder("\uFEFF姓名,场次,票种,报名状态,签到状态,票码\r\n");
    for (var row : prepared.rows())
      csv.append(
              String.join(
                  ",",
                  List.of(
                      DomainRules.csv(row.attendeeName()),
                      DomainRules.csv(row.sessionName()),
                      DomainRules.csv(row.ticketTypeName()),
                      DomainRules.csv(row.orderStatus().name()),
                      DomainRules.csv(row.checkinStatus().name()),
                      DomainRules.csv(row.ticketId() == null ? "" : "'" + row.ticketId()))))
          .append("\r\n");
    b.write(
        () -> {
          var x = b.need(ExportEntity.class, exportId, true);
          b.retention(b.need(EventEntity.class, x.eventId(), false));
          if (x.status().equals("SUCCEEDED")) return null;
          var file =
              files.store(
                  x.createdBy(),
                  "EXPORT",
                  csv.toString().getBytes(StandardCharsets.UTF_8),
                  "text/csv;charset=UTF-8");
          b.db().insert(file);
          b.db()
              .save(
                  copy(
                      x,
                      "status",
                      "SUCCEEDED",
                      "rowCount",
                      (long) prepared.rows().size(),
                      "fileId",
                      file.id(),
                      "completedAtMs",
                      b.now(),
                      "failureReason",
                      null));
          b.audit(null, "EXPORT_GENERATED", "EXPORT", x.id(), "SUCCESS", null, "JOB-07");
          return null;
        });
  }

  public UseCaseResult<Views.Counts> liveCounts(RequestContext c, UUID eventId, Queries.Session q) {
    return b.read(
        () -> {
          var e = access(c, eventId, q.sessionId(), true);
          EventSessionEntity s =
              q.sessionId() == null ? null : b.need(EventSessionEntity.class, q.sessionId(), false);
          require(
              b.effectiveEnd(e) == null && (s == null || b.now() < s.endsAtMs()),
              "STATE_CONFLICT",
              "活动/场次已结束，请读取正式统计");
          var all =
              b.list(
                  OrderEntity.class,
                  and(
                      eq("eventId", eventId),
                      q.sessionId() == null ? null : eq("sessionId", q.sessionId())));
          Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
          for (var value : OrderStatus.values()) counts.put(value, 0L);
          long wait = 0, cumulative = 0, checked = 0, occupied = 0, remaining = 0;
          for (var o : all) {
            var status = OrderStatus.valueOf(o.status());
            counts.merge(status, 1L, Long::sum);
            if (status == OrderStatus.WAITLISTED) wait++;
            if (o.waitlistedAtMs() != null) cumulative++;
            if (status == OrderStatus.CHECKED_IN || status == OrderStatus.COMPLETED) checked++;
          }
          if (b.actor(c).role().equals("CHECKER"))
            return ok(
                new Views.CheckerCounts(
                    q.sessionId(),
                    b.time(b.now()),
                    checked,
                    SettlementStatus.valueOf(e.settlementStatus())));
          var perType = new ArrayList<Views.TicketTypeCount>();
          for (var session : s == null ? b.sessions(e.id()) : List.of(s))
            for (var t : b.types(session.id())) {
              long used = b.occupied(t.id());
              occupied += used;
              remaining += Math.max(0, t.capacity() - used);
              perType.add(
                  new Views.TicketTypeCount(
                      session.id(), t.id(), t.capacity(), used, Math.max(0, t.capacity() - used)));
            }
          return ok(
              new Views.LiveCounts(
                  eventId,
                  q.sessionId(),
                  b.time(b.now()),
                  counts,
                  wait,
                  cumulative,
                  occupied,
                  remaining,
                  checked,
                  perType,
                  SettlementStatus.valueOf(e.settlementStatus())));
        });
  }

  public UseCaseResult<Views.StatisticsProjection> statistics(
      RequestContext c, UUID eventId, Queries.Session q) {
    return b.read(
        () -> {
          var e = access(c, eventId, q.sessionId(), true);
          var s =
              q.sessionId() == null ? null : b.need(EventSessionEntity.class, q.sessionId(), false);
          var snapshot =
              b.db()
                  .one(
                      StatisticsSnapshotEntity.class,
                      and(eq("eventId", eventId), eq("sessionId", q.sessionId())),
                      false)
                  .orElse(null);
          boolean due = b.effectiveEnd(e) != null || s != null && b.now() >= s.endsAtMs();
          SettlementStatus state =
              snapshot != null
                  ? SettlementStatus.COMPLETED
                  : due ? SettlementStatus.PROCESSING : SettlementStatus.NOT_DUE;
          Long end = b.effectiveEnd(e);
          Double attendance =
              snapshot == null
                  ? null
                  : DomainRules.ratio(snapshot.checkedInCount(), snapshot.nonRefundedIssuedCount());
          if (b.actor(c).role().equals("CHECKER"))
            return ok(
                new Views.CheckerStatistics(
                    s.id(),
                    state,
                    snapshot == null ? null : b.time(snapshot.generatedAtMs()),
                    end == null ? null : b.time(end + SHORT_RETENTION),
                    snapshot == null ? null : snapshot.checkedInCount(),
                    attendance));
          StatisticsReason reason =
              snapshot != null
                  ? StatisticsReason.valueOf(snapshot.reason())
                  : e.status().equals("CANCELLED")
                      ? StatisticsReason.CANCEL
                      : e.status().equals("TAKEN_DOWN")
                          ? StatisticsReason.TAKEDOWN
                          : StatisticsReason.NATURAL;
          return ok(
              new Views.Statistics(
                  e.id(),
                  q.sessionId(),
                  reason,
                  state,
                  snapshot == null ? null : b.time(snapshot.generatedAtMs()),
                  end == null ? null : b.time(end + SHORT_RETENTION),
                  snapshot == null ? null : snapshot.issuedCount(),
                  snapshot == null ? null : snapshot.refundCount(),
                  snapshot == null ? null : snapshot.nonRefundedIssuedCount(),
                  snapshot == null ? null : snapshot.checkedInCount(),
                  snapshot == null ? null : snapshot.absentCount(),
                  snapshot == null ? null : snapshot.capacityAtEnd(),
                  snapshot == null ? null : snapshot.currentWaitlistCount(),
                  snapshot == null ? null : snapshot.cumulativeWaitlistApplications(),
                  attendance,
                  snapshot == null
                      ? null
                      : DomainRules.ratio(
                          snapshot.absentCount(), snapshot.nonRefundedIssuedCount()),
                  snapshot == null
                      ? null
                      : DomainRules.ratio(snapshot.checkedInCount(), snapshot.capacityAtEnd())));
        });
  }

  public UseCaseResult<Views.PaymentSummary> paymentSummary(
      RequestContext c, UUID eventId, Queries.Session q) {
    return b.read(
        () -> {
          b.actor(c, Role.ORGANIZER, Role.ADMIN);
          access(c, eventId, q.sessionId(), false);
          long paid = 0, refunded = 0, pc = 0, rc = 0;
          for (var o :
              b.list(
                  OrderEntity.class,
                  and(
                      eq("eventId", eventId),
                      q.sessionId() == null ? null : eq("sessionId", q.sessionId())))) {
            for (var p :
                b.list(
                    PaymentAttemptEntity.class,
                    and(eq("orderId", o.id()), eq("result", "SUCCESS")))) {
              paid = Math.addExact(paid, p.amountJiao());
              pc++;
            }
            for (var r :
                b.list(
                    RefundAttemptEntity.class,
                    and(eq("orderId", o.id()), eq("result", "SUCCESS")))) {
              refunded = Math.addExact(refunded, r.amountJiao());
              rc++;
            }
          }
          valid(
              paid <= 9_007_199_254_740_991L && refunded <= 9_007_199_254_740_991L,
              "汇总超过JSON安全整数范围");
          return ok(
              new Views.PaymentSummary(
                  eventId,
                  q.sessionId(),
                  pc,
                  paid,
                  rc,
                  refunded,
                  paid - refunded,
                  b.time(b.now())));
        });
  }

  public boolean bulkAllowed(OrderEntity o) {
    Long end = b.effectiveEnd(b.need(EventEntity.class, o.eventId(), false));
    return end == null || b.now() < end + SHORT_RETENTION;
  }

  public UseCaseResult<PageResult<Views.PaymentAttempt>> listPayments(
      RequestContext c, Queries.Payments q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          valid(q.from() == null || q.to() == null || !q.from().isAfter(q.to()), "时间区间逆序");
          if (q.orderId() != null) b.need(OrderEntity.class, q.orderId(), false);
          var rows =
              b
                  .ordered(
                      PaymentAttemptEntity.class,
                      and(
                          q.orderId() == null ? null : eq("orderId", q.orderId()),
                          q.result() == null ? null : eq("result", q.result().name()),
                          q.from() == null ? null : ge("createdAtMs", millis(q.from())),
                          q.to() == null ? null : le("createdAtMs", millis(q.to()))),
                      RecordStore.Sort.desc("createdAtMs"),
                      RecordStore.Sort.asc("id"))
                  .stream()
                  .filter(
                      p ->
                          q.orderId() != null
                              || bulkAllowed(b.need(OrderEntity.class, p.orderId(), false)))
                  .map(orders::payment)
                  .toList();
          return ok(page(rows, q.page(), q.pageSize()));
        });
  }

  public UseCaseResult<Views.AdminRefundDetail> refundRequestDetail(
      RequestContext c, UUID requestId) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          var r = b.need(RefundRequestEntity.class, requestId, false);
          return ok(
              new Views.AdminRefundDetail(
                  orders.refundRequest(r),
                  b
                      .ordered(
                          RefundAttemptEntity.class,
                          eq("requestId", requestId),
                          RecordStore.Sort.asc("createdAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(orders::refundAttempt)
                      .toList(),
                  orders.auditOrder(b.need(OrderEntity.class, r.orderId(), false))));
        });
  }

  public UseCaseResult<PageResult<Views.RefundRequest>> listRefundRequests(
      RequestContext c, Queries.Refunds q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          if (q.orderId() != null) b.need(OrderEntity.class, q.orderId(), false);
          var rows =
              b
                  .ordered(
                      RefundRequestEntity.class,
                      and(
                          q.orderId() == null ? null : eq("orderId", q.orderId()),
                          q.status() == null ? null : eq("status", q.status().name()),
                          q.source() == null ? null : eq("source", q.source().name())),
                      RecordStore.Sort.asc("acceptedAtMs"),
                      RecordStore.Sort.asc("id"))
                  .stream()
                  .filter(
                      r ->
                          q.orderId() != null
                              || bulkAllowed(b.need(OrderEntity.class, r.orderId(), false)))
                  .map(orders::refundRequest)
                  .toList();
          return ok(page(rows, q.page(), q.pageSize()));
        });
  }

  public UseCaseResult<Views.RefundResult> retryAuthorizedRefund(
      RequestContext c, UUID requestId, Requests.Refund r) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          var initial = b.need(RefundRequestEntity.class, requestId, false);
          var o = b.need(OrderEntity.class, initial.orderId(), false);
          b.need(EventEntity.class, o.eventId(), true);
          var users = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
          users.add(c.actor().userId());
          users.add(o.userId());
          for (UUID id : users) b.need(UserEntity.class, id, true);
          b.need(EventSessionEntity.class, o.sessionId(), true);
          b.need(TicketTypeEntity.class, o.ticketTypeId(), true);
          b.need(OrderEntity.class, o.id(), true);
          var x =
              b.change(
                  c,
                  "ADM-18",
                  requestId,
                  r,
                  "REFUND",
                  i -> b.need(RefundAttemptEntity.class, i, false),
                  () ->
                      orders
                          .executeRefund(
                              c,
                              b.need(RefundRequestEntity.class, requestId, true),
                              r.attemptId(),
                              true)
                          .id());
          var a = x.current();
          return created(
              new Views.RefundResult(
                  x.operationId(),
                  orders.refundRequest(b.need(RefundRequestEntity.class, a.requestId(), false)),
                  orders.refundAttempt(a),
                  x.replayed(),
                  orders.auditOrder(b.need(OrderEntity.class, a.orderId(), false))),
              x.replayed());
        });
  }
}
