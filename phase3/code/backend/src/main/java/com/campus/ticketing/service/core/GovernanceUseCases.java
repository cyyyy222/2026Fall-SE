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
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GovernanceUseCases {
  private final BusinessSupport b;
  private final IdentityUseCases identity;
  private final DurableWorkflows workflows;

  public GovernanceUseCases(
      BusinessSupport b, IdentityUseCases identity, DurableWorkflows workflows) {
    this.b = b;
    this.identity = identity;
    this.workflows = workflows;
  }

  public Views.ReportView report(ReportEntity r) {
    return new Views.ReportView(
        r.id(),
        r.sessionId(),
        r.eventId(),
        r.reason(),
        r.description(),
        r.evidenceText(),
        ReportStatus.valueOf(r.status()),
        b.time(r.createdAtMs()),
        r.conclusion(),
        b.time(r.resolvedAtMs()));
  }

  public UseCaseResult<Views.ReportView> createReport(RequestContext c, Requests.Report r) {
    return b.write(
        () -> {
          var u = b.actor(c, Role.PARTICIPANT);
          var s = b.need(EventSessionEntity.class, r.sessionId(), false);
          var e = b.need(EventEntity.class, s.eventId(), true);
          b.need(UserEntity.class, u.id(), true);
          var x =
              b.change(
                  c,
                  "ME-21",
                  s.id(),
                  r,
                  "REPORT",
                  i -> report(b.need(ReportEntity.class, i, false)),
                  () -> {
                    require(
                        e.status().equals("PUBLISHED") && b.effectiveEnd(e) == null
                            || b.db()
                                    .count(
                                        OrderEntity.class,
                                        and(eq("userId", u.id()), eq("sessionId", s.id())))
                                > 0,
                        "EVENT_NOT_PUBLIC",
                        "只能举报公开场次或本人有关联的历史场次");
                    require(
                        b.db()
                                .count(
                                    ReportEntity.class,
                                    and(eq("reporterId", u.id()), eq("sessionId", s.id())))
                            == 0,
                        "DUPLICATE_REPORT",
                        "本人已举报过本场");
                    require(
                        b.db()
                                .count(
                                    ReportEntity.class,
                                    and(
                                        eq("reporterId", u.id()),
                                        gt("createdAtMs", b.now() - 30 * DAY),
                                        le("createdAtMs", b.now())))
                            < 5,
                        "REPORT_LIMIT_REACHED",
                        "滚动30天最多受理5次举报");
                    UUID report = id();
                    b.db()
                        .insert(
                            new ReportEntity(
                                report,
                                u.id(),
                                e.id(),
                                s.id(),
                                trim(r.reason()),
                                trim(r.description()),
                                trim(r.evidenceText()),
                                "PENDING",
                                b.now(),
                                null,
                                null));
                    if (b.db().count(ReportEntity.class, eq("eventId", e.id())) >= 11)
                      for (var admin :
                          b.list(
                              UserEntity.class,
                              and(eq("role", "ADMIN"), eq("enabled", true), nil("deletedAtMs"))))
                        b.notify(
                            admin.id(),
                            "REPORT_ALERT:" + e.id(),
                            "VIOLATION",
                            "活动举报数量提醒",
                            "同活动累计已受理至少11条举报，请依据证据核查",
                            "EVENT",
                            e.id(),
                            null);
                    return report;
                  });
          return created(x.current(), x.replayed());
        });
  }

  public UseCaseResult<PageResult<Views.ReportView>> listMyReports(
      RequestContext c, Queries.Reports q) {
    return b.read(
        () -> {
          var u = b.actor(c, Role.PARTICIPANT);
          return ok(
              page(
                  b
                      .ordered(
                          ReportEntity.class,
                          and(
                              eq("reporterId", u.id()),
                              q.status() == null ? null : eq("status", q.status().name())),
                          RecordStore.Sort.desc("createdAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::report)
                      .toList(),
                  q.page(),
                  q.pageSize()));
        });
  }

  public UseCaseResult<Views.ReportView> reportDetail(RequestContext c, UUID reportId) {
    return b.read(
        () -> {
          var u = b.actor(c, Role.PARTICIPANT);
          var r = b.need(ReportEntity.class, reportId, false);
          if (!r.reporterId().equals(u.id())) throw error(403, "OBJECT_FORBIDDEN", "不能查看他人举报");
          return ok(report(r));
        });
  }

  public UseCaseResult<PageResult<Views.ReportAdminRow>> listReports(
      RequestContext c, Queries.AdminReports q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          var rows =
              b
                  .ordered(
                      ReportEntity.class,
                      and(
                          q.eventId() == null ? null : eq("eventId", q.eventId()),
                          q.status() == null ? null : eq("status", q.status().name())),
                      RecordStore.Sort.asc("createdAtMs"),
                      RecordStore.Sort.asc("id"))
                  .stream()
                  .map(
                      r -> {
                        int n = (int) b.db().count(ReportEntity.class, eq("eventId", r.eventId()));
                        return new Views.ReportAdminRow(
                            r.id(),
                            r.sessionId(),
                            r.eventId(),
                            r.reason(),
                            r.description(),
                            r.evidenceText(),
                            ReportStatus.valueOf(r.status()),
                            b.time(r.createdAtMs()),
                            r.conclusion(),
                            b.time(r.resolvedAtMs()),
                            r.reporterId(),
                            n,
                            n >= 11);
                      })
                  .filter(
                      r -> q.needsAttention() == null || r.needsAttention() == q.needsAttention())
                  .toList();
          return ok(page(rows, q.page(), q.pageSize()));
        });
  }

  public Views.InvestigationResult investigation(InvestigationEntity i) {
    UUID fact =
        b.db()
            .one(BehaviorFactEntity.class, eq("investigationId", i.id()), false)
            .map(BehaviorFactEntity::id)
            .orElse(null);
    return new Views.InvestigationResult(
        i.id(),
        fact,
        i.eventId(),
        Conclusion.valueOf(i.conclusion()),
        b.list(InvestigationReportEntity.class, eq("investigationId", i.id())).stream()
            .map(InvestigationReportEntity::reportId)
            .sorted(Comparator.comparing(UUID::toString))
            .toList(),
        fact != null,
        EventStatus.valueOf(b.need(EventEntity.class, i.eventId(), false).status()),
        i.terminationJobId());
  }

  public UseCaseResult<Views.InvestigationResult> investigate(
      RequestContext c, Requests.Investigation r) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          var e = b.need(EventEntity.class, r.eventId(), true);
          var users = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
          users.add(c.actor().userId());
          users.add(e.organizerId());
          for (UUID uid : users) b.need(UserEntity.class, uid, true);
          var x =
              b.change(
                  c,
                  "ADM-20",
                  e.id(),
                  r,
                  "INVESTIGATION",
                  i -> investigation(b.need(InvestigationEntity.class, i, false)),
                  () -> {
                    valid(new HashSet<>(r.reportIds()).size() == r.reportIds().size(), "举报ID重复");
                    var old =
                        b.db()
                            .one(
                                InvestigationEntity.class,
                                and(eq("eventId", e.id()), eq("factKey", trim(r.factKey()))),
                                true)
                            .orElse(null);
                    if (old != null)
                      require(
                          old.conclusion().equals(r.conclusion().name()),
                          "STATE_CONFLICT",
                          "同一事实已有相反核查结论");
                    UUID investigation = old == null ? id() : old.id();
                    for (UUID reportId : r.reportIds()) {
                      var report = b.need(ReportEntity.class, reportId, true);
                      valid(report.eventId().equals(e.id()), "举报不能跨活动合并");
                      var link =
                          b.db()
                              .one(InvestigationReportEntity.class, eq("reportId", reportId), false)
                              .orElse(null);
                      require(
                          link == null || link.investigationId().equals(investigation),
                          "STATE_CONFLICT",
                          "举报已由其他事实处理");
                    }
                    if (old == null)
                      b.db()
                          .insert(
                              new InvestigationEntity(
                                  investigation,
                                  e.id(),
                                  c.actor().userId(),
                                  trim(r.factKey()),
                                  r.conclusion().name(),
                                  trim(r.reason()),
                                  trim(r.evidenceText()),
                                  b.now(),
                                  null));
                    for (UUID reportId : r.reportIds())
                      if (b.db().count(InvestigationReportEntity.class, eq("reportId", reportId))
                          == 0) {
                        var report = b.need(ReportEntity.class, reportId, true);
                        b.db().insert(new InvestigationReportEntity(id(), investigation, reportId));
                        b.db()
                            .save(
                                copy(
                                    report,
                                    "status",
                                    r.conclusion().name(),
                                    "conclusion",
                                    trim(r.reason()),
                                    "resolvedAtMs",
                                    b.now()));
                      }
                    if (r.conclusion() == Conclusion.SUBSTANTIATED
                        && b.db()
                                .count(
                                    BehaviorFactEntity.class, eq("investigationId", investigation))
                            == 0) {
                      var owner = b.need(UserEntity.class, e.organizerId(), true);
                      var phone = b.need(PhoneIdentityEntity.class, owner.phoneIdentityId(), true);
                      b.db()
                          .insert(
                              new BehaviorFactEntity(
                                  id(),
                                  phone.id(),
                                  "ORGANIZER_VIOLATION",
                                  null,
                                  investigation,
                                  b.now(),
                                  trim(r.reason())));
                      autoPublishRestriction(phone.id());
                      b.notify(
                          owner.id(),
                          "VIOLATION:" + investigation,
                          "VIOLATION",
                          "活动违规核查属实",
                          trim(r.reason()),
                          "EVENT",
                          e.id(),
                          null);
                    }
                    if (r.conclusion() == Conclusion.SUBSTANTIATED
                        && e.status().equals("PUBLISHED")
                        && b.effectiveEnd(e) == null) {
                      var job =
                          workflows.beginTermination(
                              c,
                              b.need(EventEntity.class, e.id(), true),
                              "TAKEN_DOWN",
                              trim(r.reason()));
                      var stored = b.need(InvestigationEntity.class, investigation, false);
                      b.db().save(copy(stored, "terminationJobId", job.id()));
                    }
                    return investigation;
                  });
          return created(x.current(), x.replayed());
        });
  }

  public UseCaseResult<Views.InvestigationDetail> investigationDetail(
      RequestContext c, UUID investigationId) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          var i = b.need(InvestigationEntity.class, investigationId, false);
          var v = investigation(i);
          return ok(
              new Views.InvestigationDetail(
                  v.id(),
                  v.factId(),
                  v.eventId(),
                  v.conclusion(),
                  v.mergedReportIds(),
                  v.violationRecorded(),
                  v.eventStatus(),
                  v.terminationJobId(),
                  i.reason(),
                  i.evidenceText(),
                  i.factKey(),
                  b.time(i.createdAtMs())));
        });
  }

  public UseCaseResult<PageResult<Views.InvestigationResult>> listInvestigations(
      RequestContext c, Queries.Investigations q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          return ok(
              page(
                  b
                      .ordered(
                          InvestigationEntity.class,
                          and(
                              q.eventId() == null ? null : eq("eventId", q.eventId()),
                              q.conclusion() == null
                                  ? null
                                  : eq("conclusion", q.conclusion().name())),
                          RecordStore.Sort.desc("createdAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::investigation)
                      .toList(),
                  q.page(),
                  q.pageSize()));
        });
  }

  private Long previous(UUID identity, String kind) {
    return b
        .ordered(
            RestrictionEntity.class,
            and(eq("phoneIdentityId", identity), eq("kind", kind)),
            RecordStore.Sort.desc("startsAtMs"),
            RecordStore.Sort.asc("id"))
        .stream()
        .findFirst()
        .map(RestrictionEntity::startsAtMs)
        .orElse(null);
  }

  public List<BehaviorFactEntity> eligible(UUID identity, String kind) {
    Long last = previous(identity, kind);
    var rows =
        b.ordered(
            BehaviorFactEntity.class,
            and(
                eq("phoneIdentityId", identity),
                gt(
                    "occurredAtMs",
                    Math.max(b.now() - 30 * DAY, last == null ? Long.MIN_VALUE : last)),
                le("occurredAtMs", b.now()),
                in(
                    "type",
                    kind.equals("PUBLISH")
                        ? List.of("ORGANIZER_VIOLATION")
                        : List.of("NATURAL_ABSENCE", "VOLUNTARY_REFUND"))),
            RecordStore.Sort.asc("occurredAtMs"),
            RecordStore.Sort.asc("id"));
    return rows.stream()
        .filter(f -> b.db().count(RestrictionFactEntity.class, eq("factId", f.id())) == 0)
        .toList();
  }

  private Views.RestrictionCandidate candidate(PhoneIdentityEntity phone, String kind) {
    var facts = eligible(phone.id(), kind);
    var active =
        b.db()
            .one(
                RestrictionEntity.class,
                and(
                    eq("phoneIdentityId", phone.id()),
                    eq("kind", kind),
                    le("startsAtMs", b.now()),
                    gt("endsAtMs", b.now())),
                false)
            .orElse(null);
    return new Views.RestrictionCandidate(
        phone.id(),
        IdentityUseCases.mask(phone.phone()),
        RestrictionKind.valueOf(kind),
        (int) facts.stream().filter(f -> f.type().equals("NATURAL_ABSENCE")).count(),
        (int) facts.stream().filter(f -> f.type().equals("VOLUNTARY_REFUND")).count(),
        (int) facts.stream().filter(f -> f.type().equals("ORGANIZER_VIOLATION")).count(),
        facts.stream().map(BehaviorFactEntity::id).toList(),
        active == null ? null : identity.restriction(active),
        b.time(previous(phone.id(), kind)));
  }

  public UseCaseResult<PageResult<Views.RestrictionCandidate>> candidates(
      RequestContext c, Queries.Candidates q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          List<Views.RestrictionCandidate> rows = new ArrayList<>();
          for (var p : b.ordered(PhoneIdentityEntity.class, null, RecordStore.Sort.asc("id")))
            for (String kind :
                q.kind() == null ? List.of("REGISTER", "PUBLISH") : List.of(q.kind().name())) {
              var candidate = candidate(p, kind);
              if (!candidate.eligibleFactIds().isEmpty() || candidate.activeRestriction() != null)
                rows.add(candidate);
            }
          return ok(page(rows, q.page(), q.pageSize()));
        });
  }

  public UseCaseResult<Views.RestrictionEvidence> candidateEvidence(
      RequestContext c, UUID phoneIdentityId, Queries.Candidate q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          var p = b.need(PhoneIdentityEntity.class, phoneIdentityId, false);
          var candidate = candidate(p, q.kind().name());
          var facts =
              b
                  .ordered(
                      BehaviorFactEntity.class,
                      and(
                          eq("phoneIdentityId", p.id()),
                          gt("occurredAtMs", b.now() - 30 * DAY),
                          le("occurredAtMs", b.now()),
                          in(
                              "type",
                              q.kind() == RestrictionKind.PUBLISH
                                  ? List.of("ORGANIZER_VIOLATION")
                                  : List.of("NATURAL_ABSENCE", "VOLUNTARY_REFUND"))),
                      RecordStore.Sort.asc("occurredAtMs"),
                      RecordStore.Sort.asc("id"))
                  .stream()
                  .map(
                      f ->
                          new Views.EvidenceFact(
                              f.id(),
                              f.type(),
                              b.time(f.occurredAtMs()),
                              f.orderId(),
                              f.investigationId(),
                              f.reason(),
                              b.db().count(RestrictionFactEntity.class, eq("factId", f.id())) > 0))
                  .toList();
          return ok(new Views.RestrictionEvidence(candidate, facts));
        });
  }

  private RestrictionEntity restrict(
      RequestContext c,
      UUID phone,
      String kind,
      List<BehaviorFactEntity> facts,
      String reason,
      long startsAt) {
    require(!b.restricted(phone, kind), "STATE_CONFLICT", "当前限制尚未到期，不延长或重新起算");
    var restriction =
        new RestrictionEntity(
            id(),
            phone,
            kind,
            startsAt,
            startsAt + 15 * DAY,
            reason,
            c == null ? null : c.actor().userId(),
            null);
    b.db().insert(restriction);
    for (var fact : facts)
      b.db().insert(new RestrictionFactEntity(id(), restriction.id(), fact.id()));
    for (var user :
        b.list(
            UserEntity.class,
            and(
                eq("phoneIdentityId", phone),
                eq("role", kind.equals("PUBLISH") ? "ORGANIZER" : "PARTICIPANT"),
                nil("deletedAtMs"))))
      b.notify(
          user.id(),
          "RESTRICTION:" + restriction.id(),
          "RESTRICTION_STARTED",
          "业务资格受到15天限制",
          reason,
          "ACCOUNT",
          user.id(),
          restriction.endsAtMs());
    b.audit(
        c,
        "RESTRICTION_STARTED",
        "RESTRICTION",
        restriction.id(),
        "SUCCESS",
        null,
        c == null ? "JOB-06" : null);
    return restriction;
  }

  public void autoPublishRestriction(UUID phone) {
    if (b.restricted(phone, "PUBLISH")) return;
    var facts = eligible(phone, "PUBLISH");
    if (facts.size() >= 3)
      restrict(
          null,
          phone,
          "PUBLISH",
          facts.subList(0, 3),
          "滚动30天内三次组织者违规",
          facts.get(2).occurredAtMs());
  }

  public void reconcileDue(UUID jobId) {
    for (var phone : b.read(() -> b.list(PhoneIdentityEntity.class, null)))
      b.write(
          () -> {
            for (var u :
                b.ordered(
                    UserEntity.class,
                    and(eq("phoneIdentityId", phone.id()), nil("deletedAtMs")),
                    RecordStore.Sort.asc("id"))) b.need(UserEntity.class, u.id(), true);
            b.need(PhoneIdentityEntity.class, phone.id(), true);
            autoPublishRestriction(phone.id());
            return null;
          });
    workflows.reconcileRestrictionMessages();
  }

  public UseCaseResult<Views.RestrictionView> restrictRegistration(
      RequestContext c, Requests.Restriction r) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          var users = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
          users.add(c.actor().userId());
          for (var u :
              b.list(
                  UserEntity.class,
                  and(eq("phoneIdentityId", r.phoneIdentityId()), nil("deletedAtMs"))))
            users.add(u.id());
          for (UUID uid : users) b.need(UserEntity.class, uid, true);
          b.need(PhoneIdentityEntity.class, r.phoneIdentityId(), true);
          var x =
              b.change(
                  c,
                  "ADM-23",
                  r.phoneIdentityId(),
                  r,
                  "RESTRICTION",
                  i -> identity.restriction(b.need(RestrictionEntity.class, i, false)),
                  () -> {
                    valid(
                        new HashSet<>(r.basisFactIds()).size() == r.basisFactIds().size(),
                        "处罚依据不能重复");
                    var eligible = eligible(r.phoneIdentityId(), "REGISTER");
                    Map<UUID, BehaviorFactEntity> byId = new HashMap<>();
                    eligible.forEach(f -> byId.put(f.id(), f));
                    List<BehaviorFactEntity> selected = new ArrayList<>();
                    for (UUID id : r.basisFactIds()) {
                      require(
                          byId.containsKey(id), "RESTRICTION_BASIS_CONSUMED", "依据已消费、过窗口或不属于此身份");
                      selected.add(byId.get(id));
                    }
                    long
                        absent =
                            selected.stream()
                                .filter(f -> f.type().equals("NATURAL_ABSENCE"))
                                .count(),
                        refund =
                            selected.stream()
                                .filter(f -> f.type().equals("VOLUNTARY_REFUND"))
                                .count();
                    require(
                        absent >= 3 || refund >= 5, "STATE_CONFLICT", "需至少3次正常缺席或5次主动成功退款，不能混加阈值");
                    return restrict(
                            c, r.phoneIdentityId(), "REGISTER", selected, trim(r.reason()), b.now())
                        .id();
                  });
          return created(x.current(), x.replayed());
        });
  }

  public Views.MessageView message(MessageEntity m) {
    return new Views.MessageView(
        m.id(),
        m.type(),
        m.title(),
        m.body(),
        b.time(m.createdAtMs()),
        b.time(m.expiresAtMs()),
        b.time(m.readAtMs()),
        m.resourceType() == null ? null : new Views.MessageTarget(m.resourceType(), m.resourceId()),
        b.time(m.actionDeadlineMs()));
  }

  private MessageEntity ownMessage(RequestContext c, UUID id, boolean lock) {
    var u = b.actor(c);
    var m = b.need(MessageEntity.class, id, lock);
    if (!m.recipientId().equals(u.id())) throw error(403, "OBJECT_FORBIDDEN", "不能查看他人消息");
    if (b.now() >= m.expiresAtMs()) throw error(410, "DATA_EXPIRED", "消息已满180天");
    return m;
  }

  public UseCaseResult<PageResult<Views.MessageView>> listMessages(
      RequestContext c, Queries.Messages q) {
    return b.read(
        () -> {
          var u = b.actor(c);
          return ok(
              page(
                  b
                      .ordered(
                          MessageEntity.class,
                          and(
                              eq("recipientId", u.id()),
                              gt("expiresAtMs", b.now()),
                              Boolean.TRUE.equals(q.unreadOnly()) ? nil("readAtMs") : null),
                          RecordStore.Sort.desc("createdAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::message)
                      .toList(),
                  q.page(),
                  q.pageSize()));
        });
  }

  public UseCaseResult<Views.MessageView> messageDetail(RequestContext c, UUID messageId) {
    return b.read(() -> ok(message(ownMessage(c, messageId, false))));
  }

  public UseCaseResult<Mutation<Views.MessageView>> markRead(
      RequestContext c, UUID messageId, Requests.Empty r) {
    return b.write(
        () -> {
          ownMessage(c, messageId, false);
          var x =
              b.change(
                  c,
                  "ME-26",
                  messageId,
                  r,
                  "MESSAGE",
                  i -> message(ownMessage(c, i, false)),
                  () -> {
                    var m = ownMessage(c, messageId, true);
                    if (m.readAtMs() == null) b.db().save(copy(m, "readAtMs", b.now()));
                    return messageId;
                  });
          return ok(x.mutation());
        });
  }
}
