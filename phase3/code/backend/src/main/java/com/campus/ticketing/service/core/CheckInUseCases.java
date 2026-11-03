package com.campus.ticketing.service.core;

import static com.campus.ticketing.common.EntityRecords.*;
import static com.campus.ticketing.dao.RecordStore.Filter.*;
import static com.campus.ticketing.service.core.BusinessSupport.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.entity.*;
import com.campus.ticketing.enums.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CheckInUseCases {
  private final BusinessSupport b;
  private final IdentityUseCases identities;
  private final EventUseCases events;
  private final OrderUseCases orders;

  public CheckInUseCases(
      BusinessSupport b, IdentityUseCases identities, EventUseCases events, OrderUseCases orders) {
    this.b = b;
    this.identities = identities;
    this.events = events;
    this.orders = orders;
  }

  public CheckerAssignmentEntity current(RequestContext c) {
    var u = b.actor(c, Role.CHECKER);
    identities.available(u);
    return b.db()
        .one(CheckerAssignmentEntity.class, and(eq("checkerId", u.id()), nil("revokedAtMs")), false)
        .orElseThrow(() -> error(403, "ACCOUNT_UNAVAILABLE", "无有效授权"));
  }

  public UseCaseResult<Views.CheckerContext> context(RequestContext c) {
    return b.read(
        () -> {
          var a = current(c);
          var s = b.need(EventSessionEntity.class, a.sessionId(), false);
          var e = b.need(EventEntity.class, s.eventId(), false);
          return ok(
              new Views.CheckerContext(
                  events.publicSession(e, s),
                  EventStatus.valueOf(e.status()),
                  b.time(DomainRules.checkinOpen(s.startsAtMs())),
                  b.time(DomainRules.checkinClose(s.startsAtMs(), s.endsAtMs())),
                  List.of("PREVIEW", "CHECK_IN")));
        });
  }

  private void allowed(TicketEntity t, OrderEntity o, CheckerAssignmentEntity a) {
    if (!o.sessionId().equals(a.sessionId())) throw error(403, "OBJECT_FORBIDDEN", "票不属于当前授权场次");
  }

  private void usable(TicketEntity t, OrderEntity o) {
    var s = b.need(EventSessionEntity.class, o.sessionId(), false);
    var e = b.need(EventEntity.class, o.eventId(), false);
    require(
        e.status().equals("PUBLISHED") && b.effectiveEnd(e) == null, "EVENT_TERMINATED", "活动已终止");
    require(
        t.status().equals("VALID") && o.status().equals("REGISTERED"),
        "TICKET_INVALID",
        "票已失效或不可验票");
    require(
        b.now() >= DomainRules.checkinOpen(s.startsAtMs()), "CHECKIN_NOT_OPEN", "尚未到验票窗口");
    require(
        b.now() < DomainRules.checkinClose(s.startsAtMs(), s.endsAtMs()),
        "CHECKIN_ENDED",
        "验票窗口已结束");
  }

  public UseCaseResult<Views.TicketPreview> previewTicket(RequestContext c, Requests.Ticket r) {
    return b.read(
        () -> {
          var a = current(c);
          var t = b.need(TicketEntity.class, r.ticketId(), false);
          var o = b.need(OrderEntity.class, t.orderId(), false);
          allowed(t, o, a);
          var s = b.need(EventSessionEntity.class, o.sessionId(), false);
          var check = b.db().one(CheckInEntity.class, eq("ticketId", t.id()), false).orElse(null);
          if (check == null) usable(t, o);
          var owner = b.need(UserEntity.class, o.userId(), false);
          var phoneId = loginPhoneId(owner);
          var phone = phoneId == null ? null : b.need(PhoneIdentityEntity.class, phoneId, false).phone();
          return ok(
              new Views.TicketPreview(
                  t.id(),
                  owner.id(),
                  owner.username(),
                  o.attendeeNameSnapshot(),
                  phone,
                  s.id(),
                  o.sessionNameSnapshot(),
                  o.ticketTypeNameSnapshot(),
                  TicketStatus.valueOf(t.status()),
                  check != null,
                  check == null ? null : b.time(check.checkedAtMs()),
                  check == null));
        });
  }

  public UseCaseResult<Views.CheckInResult> confirmCheckIn(RequestContext c, Requests.Ticket r) {
    return b.write(
        () -> {
          b.actor(c, Role.CHECKER);
          var t = b.need(TicketEntity.class, r.ticketId(), false);
          var o = b.need(OrderEntity.class, t.orderId(), false);
          b.need(EventEntity.class, o.eventId(), true);
          var userIds = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
          userIds.add(o.userId());
          userIds.add(c.actor().userId());
          for (UUID id : userIds) b.need(UserEntity.class, id, true);
          b.need(EventSessionEntity.class, o.sessionId(), true);
          b.need(TicketTypeEntity.class, o.ticketTypeId(), true);
          o = b.need(OrderEntity.class, o.id(), true);
          t = b.need(TicketEntity.class, t.id(), true);
          var a = current(c);
          allowed(t, o, a);
          var check = b.db().one(CheckInEntity.class, eq("ticketId", t.id()), true).orElse(null);
          if (check != null) return ok(result(check, true));
          usable(t, o);
          check =
              new CheckInEntity(
                  id(), t.id(), o.id(), c.actor().userId(), a.sessionId(), a.id(), b.now());
          b.db().insert(check);
          b.db().save(copy(t, "status", "CHECKED"));
          orders.status(o, "CHECKED_IN", null);
          orders.closeRefunds(o.id(), null, "已签到，原未成功退款依据失效");
          b.audit(c, "CHK-03", "CHECKIN", check.id(), "SUCCESS", null, null);
          return created(result(check, false), false);
        });
  }

  private Views.CheckInResult result(CheckInEntity check, boolean replay) {
    return new Views.CheckInResult(
        check.ticketId(),
        check.orderId(),
        b.time(check.checkedAtMs()),
        check.checkerId(),
        check.sessionId(),
        replay,
        OrderStatus.valueOf(b.need(OrderEntity.class, check.orderId(), false).status()),
        TicketStatus.valueOf(b.need(TicketEntity.class, check.ticketId(), false).status()));
  }
}
