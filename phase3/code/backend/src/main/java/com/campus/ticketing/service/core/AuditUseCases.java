package com.campus.ticketing.service.core;

import static com.campus.ticketing.dao.RecordStore.Filter.*;
import static com.campus.ticketing.service.core.BusinessSupport.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dao.RecordStore;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.entity.*;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AuditUseCases {
  private final BusinessSupport b;

  public AuditUseCases(BusinessSupport b) {
    this.b = b;
  }

  public UseCaseResult<PageResult<Views.AuditLog>> logs(RequestContext c, Queries.Audit q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          valid(q.from() == null || q.to() == null || !q.from().isAfter(q.to()), "日志时间区间逆序");
          var all =
              b
                  .ordered(
                      AuditLogEntity.class,
                      and(
                          gt("createdAtMs", b.now() - LONG_RETENTION),
                          q.from() == null ? null : ge("createdAtMs", millis(q.from())),
                          q.to() == null ? null : le("createdAtMs", millis(q.to())),
                          q.action() == null ? null : eq("action", q.action()),
                          q.objectType() == null ? null : eq("objectType", q.objectType()),
                          q.objectId() == null ? null : eq("objectId", q.objectId()),
                          q.result() == null ? null : eq("result", q.result().name())),
                      RecordStore.Sort.desc("createdAtMs"),
                      RecordStore.Sort.asc("id"))
                  .stream()
                  .map(
                      a ->
                          new Views.AuditLog(
                              a.id(),
                              a.actorId(),
                              ActorRole.valueOf(a.actorRole()),
                              a.action(),
                              a.objectType(),
                              a.objectId(),
                              AuditResult.valueOf(a.result()),
                              a.failureReason(),
                              a.jobType(),
                              b.time(a.createdAtMs()),
                              a.traceId()))
                  .toList();
          return ok(page(all, q.page(), q.pageSize()));
        });
  }

  public UseCaseResult<Views.AuditObject> singleObject(
      RequestContext c, AuditObjectType objectType, UUID objectId) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          UUID orderId;
          Views.AuditRecord record;
          switch (objectType) {
            case ORDER -> {
              var o = b.need(OrderEntity.class, objectId, false);
              orderId = o.id();
              record =
                  new Views.OrderAuditRecord(
                      o.id(),
                      OrderStatus.valueOf(o.status()),
                      o.amountJiao(),
                      b.time(o.createdAtMs()));
            }
            case PAYMENT -> {
              var p = b.need(PaymentAttemptEntity.class, objectId, false);
              orderId = p.orderId();
              record =
                  new Views.PaymentAuditRecord(
                      p.id(),
                      p.orderId(),
                      AttemptResult.valueOf(p.result()),
                      p.amountJiao(),
                      b.time(p.createdAtMs()));
            }
            case REFUND -> {
              var r = b.need(RefundAttemptEntity.class, objectId, false);
              orderId = r.orderId();
              record =
                  new Views.RefundAuditRecord(
                      r.id(),
                      r.orderId(),
                      r.requestId(),
                      AttemptResult.valueOf(r.result()),
                      r.amountJiao(),
                      b.time(r.createdAtMs()));
            }
            case TICKET -> {
              var t = b.need(TicketEntity.class, objectId, false);
              orderId = t.orderId();
              record =
                  new Views.TicketAuditRecord(
                      t.id(), t.orderId(), TicketStatus.valueOf(t.status()));
            }
            case CHECKIN -> {
              var x = b.need(CheckInEntity.class, objectId, false);
              orderId = x.orderId();
              record =
                  new Views.CheckInAuditRecord(
                      x.id(),
                      x.ticketId(),
                      x.orderId(),
                      x.sessionId(),
                      x.checkerId(),
                      b.time(x.checkedAtMs()));
            }
            default -> throw error(422, "VALIDATION_FAILED", "不支持的审计对象类型");
          }
          var related =
              new Views.RelatedIds(
                  List.of(orderId),
                  b.list(PaymentAttemptEntity.class, eq("orderId", orderId)).stream()
                      .map(PaymentAttemptEntity::id)
                      .toList(),
                  b.list(RefundAttemptEntity.class, eq("orderId", orderId)).stream()
                      .map(RefundAttemptEntity::id)
                      .toList(),
                  b.list(TicketEntity.class, eq("orderId", orderId)).stream()
                      .map(TicketEntity::id)
                      .toList(),
                  b.list(CheckInEntity.class, eq("orderId", orderId)).stream()
                      .map(CheckInEntity::id)
                      .toList());
          return ok(new Views.AuditObject(objectType, objectId, record, related));
        });
  }
}
