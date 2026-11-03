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
import org.springframework.stereotype.Service;

@Service
public class EventUseCases {
  private final BusinessSupport b;
  private final IdentityUseCases identity;

  public EventUseCases(BusinessSupport b, IdentityUseCases identity) {
    this.b = b;
    this.identity = identity;
  }

  public Views.Category category(CategoryEntity c) {
    return new Views.Category(c.id(), c.name(), c.enabled(), c.version());
  }

  public UseCaseResult<List<Views.Category>> listCategories(
      RequestContext c, Queries.Categories q) {
    return b.read(
        () -> {
          var all =
              b.ordered(
                  CategoryEntity.class,
                  null,
                  RecordStore.Sort.asc("nameNormalized"),
                  RecordStore.Sort.asc("id"));
          var publicIds = new HashSet<UUID>();
          if (q.scope() != CategoryScope.EDIT)
            for (var e : b.list(EventEntity.class, eq("status", "PUBLISHED")))
              if (b.effectiveEnd(e) == null) publicIds.add(e.categoryId());
          return ok(
              all.stream()
                  .filter(x -> x.enabled() || publicIds.contains(x.id()))
                  .map(this::category)
                  .toList());
        });
  }

  public UseCaseResult<PageResult<Views.Category>> listCategoriesAdmin(
      RequestContext c, Queries.CategoryAdmin q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          return ok(
              page(
                  b
                      .ordered(
                          CategoryEntity.class,
                          q.enabled() == null ? null : eq("enabled", q.enabled()),
                          RecordStore.Sort.asc("nameNormalized"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::category)
                      .toList(),
                  q.page(),
                  q.pageSize()));
        });
  }

  public UseCaseResult<Views.Category> createCategory(RequestContext c, Requests.CreateCategory r) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          var x =
              b.change(
                  c,
                  "ADM-13",
                  null,
                  r,
                  "CATEGORY",
                  i -> category(b.need(CategoryEntity.class, i, false)),
                  () -> {
                    valid(
                        b.db()
                                .count(
                                    CategoryEntity.class, eq("nameNormalized", normalize(r.name())))
                            == 0,
                        "类别名称已存在");
                    UUID id = id();
                    b.db()
                        .insert(
                            new CategoryEntity(
                                id,
                                trim(r.name()),
                                normalize(r.name()),
                                true,
                                0L,
                                b.now(),
                                b.now()));
                    return id;
                  });
          return created(x.current(), x.replayed());
        });
  }

  public UseCaseResult<Mutation<Views.Category>> updateCategory(
      RequestContext c, UUID categoryId, Requests.UpdateCategory r) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          b.need(CategoryEntity.class, categoryId, true);
          var x =
              b.change(
                  c,
                  "ADM-14",
                  categoryId,
                  r,
                  "CATEGORY",
                  i -> category(b.need(CategoryEntity.class, i, false)),
                  () -> {
                    var old = b.need(CategoryEntity.class, categoryId, true);
                    version(old.version(), r.expectedVersion());
                    valid(present(r.name()) || present(r.enabled()), "必须修改至少一个字段");
                    String name = present(r.name()) ? trim(r.name().value()) : old.name();
                    valid(
                        b.db()
                                .count(
                                    CategoryEntity.class,
                                    and(
                                        eq("nameNormalized", normalize(name)),
                                        ne("id", categoryId)))
                            == 0,
                        "类别名称已存在");
                    b.db()
                        .save(
                            copy(
                                old,
                                "name",
                                name,
                                "nameNormalized",
                                normalize(name),
                                "enabled",
                                present(r.enabled()) ? r.enabled().value() : old.enabled(),
                                "version",
                                old.version() + 1,
                                "updatedAtMs",
                                b.now()));
                    return categoryId;
                  });
          return ok(x.mutation());
        });
  }

  public static boolean present(PatchField<?> field) {
    return field != null && field.present();
  }

  public static void version(long actual, long expected) {
    require(actual == expected, "VERSION_CONFLICT", "配置已被修改，请刷新后重试");
  }

  public SalesStatus sales(
      EventEntity e, EventSessionEntity s, TicketTypeEntity type, long remaining) {
    if (Set.of("CANCELLED", "TAKEN_DOWN").contains(e.status())) return SalesStatus.TERMINATED;
    if (s.endsAtMs() != null && b.now() >= s.endsAtMs()) return SalesStatus.ENDED;
    if (s.registrationClosesAtMs() == null || s.salesOpensAtMs() == null) return null;
    if (b.now() >= s.registrationClosesAtMs()) return SalesStatus.CLOSED;
    if (b.now() < s.salesOpensAtMs()) return SalesStatus.NOT_OPEN;
    if (type != null && type.saleStopped()) return SalesStatus.STOPPED;
    return remaining == 0 ? SalesStatus.SOLD_OUT : SalesStatus.ON_SALE;
  }

  public Views.SessionPublic publicSession(EventEntity e, EventSessionEntity s) {
    var types =
        b.types(s.id()).stream()
            .map(
                t -> {
                  long occupied = b.occupied(t.id());
                  long remaining = Math.max(0, t.capacity() - occupied);
                  return new Views.TicketTypeView(
                      t.id(),
                      t.name(),
                      t.capacity(),
                      t.priceJiao(),
                      t.saleStopped(),
                      occupied,
                      remaining,
                      sales(e, s, t, remaining));
                })
            .toList();
    long remaining = types.stream().mapToLong(Views.TicketTypeView::remaining).sum();
    SalesStatus status = sales(e, s, null, remaining);
    if (status == SalesStatus.ON_SALE || status == SalesStatus.SOLD_OUT) {
      if (types.stream().anyMatch(t -> t.salesStatus() == SalesStatus.ON_SALE))
        status = SalesStatus.ON_SALE;
      else if (types.stream().anyMatch(t -> !t.saleStopped())) status = SalesStatus.SOLD_OUT;
      else status = SalesStatus.STOPPED;
    }
    return new Views.SessionPublic(
        s.id(),
        e.id(),
        e.name(),
        e.categoryId(),
        e.categoryNameSnapshot(),
        s.name(),
        b.time(s.startsAtMs()),
        b.time(s.endsAtMs()),
        s.location(),
        s.capacity(),
        b.time(s.salesOpensAtMs()),
        b.time(s.registrationClosesAtMs()),
        remaining,
        status,
        types,
        e.posterFileId() == null ? null : "/api/public/events/" + e.id() + "/poster");
  }

  public Views.EventManage manage(EventEntity e) {
    var sessions = b.sessions(e.id());
    var views =
        sessions.stream()
            .map(
                s ->
                    new Views.SessionManage(
                        s.id(),
                        s.name(),
                        b.time(s.startsAtMs()),
                        b.time(s.endsAtMs()),
                        s.location(),
                        s.capacity(),
                        b.time(s.salesOpensAtMs()),
                        b.time(s.registrationClosesAtMs()),
                        b.types(s.id()).stream()
                            .map(
                                t ->
                                    new Views.TicketTypeDraft(
                                        t.id(),
                                        t.name(),
                                        t.capacity(),
                                        t.priceJiao(),
                                        t.saleStopped()))
                            .toList(),
                        sales(e, s, null, 0),
                        checkers(s.id()).size()))
            .toList();
    var times =
        sessions.stream().map(EventSessionEntity::startsAtMs).filter(Objects::nonNull).toList();
    var ends =
        sessions.stream().map(EventSessionEntity::endsAtMs).filter(Objects::nonNull).toList();
    var types = sessions.stream().flatMap(s -> b.types(s.id()).stream()).toList();
    var prices = types.stream().map(TicketTypeEntity::priceJiao).filter(Objects::nonNull).toList();
    boolean complete = sessions.stream().allMatch(s -> s.capacity() != null);
    List<String> actions =
        switch (e.status()) {
          case "DRAFT" -> List.of("EDIT", "SUBMIT");
          case "PENDING_REVIEW" -> List.of("WITHDRAW");
          case "PUBLISHED" ->
              b.effectiveEnd(e) == null
                  ? List.of("EDIT_SETTINGS", "CANCEL", "ATTENDEES", "STATISTICS")
                  : List.of("STATISTICS");
          default -> List.of("STATISTICS");
        };
    Long end = b.effectiveEnd(e);
    return new Views.EventManage(
        e.id(),
        e.organizerId(),
        EventStatus.valueOf(e.status()),
        e.version(),
        e.name(),
        e.categoryId(),
        e.categoryNameSnapshot(),
        e.proposedCategoryName(),
        e.description(),
        e.posterFileId() == null ? null : "/api/management/events/" + e.id() + "/poster",
        views,
        b.time(e.submittedAtMs()),
        b.time(e.publishedAtMs()),
        b.time(e.terminatedAtMs()),
        e.terminationReason(),
        e.lastReviewReason(),
        e.status().equals("PENDING_REVIEW")
            && e.submittedAtMs() != null
            && b.now() >= e.submittedAtMs() + DAY,
        SettlementStatus.valueOf(e.settlementStatus()),
        end == null ? null : b.time(end + SHORT_RETENTION),
        actions,
        times.isEmpty() ? null : b.time(Collections.min(times)),
        ends.isEmpty() ? null : b.time(Collections.max(ends)),
        !complete || sessions.isEmpty()
            ? null
            : sessions.stream().mapToInt(EventSessionEntity::capacity).sum(),
        prices.isEmpty() ? null : Collections.min(prices),
        prices.isEmpty() ? null : Collections.max(prices));
  }

  public List<UserEntity> checkers(UUID session) {
    return b
        .list(CheckerAssignmentEntity.class, and(eq("sessionId", session), nil("revokedAtMs")))
        .stream()
        .map(a -> b.need(UserEntity.class, a.checkerId(), false))
        .filter(u -> u.deletedAtMs() == null && u.enabled())
        .toList();
  }

  public UseCaseResult<PageResult<Views.SessionSearchRow>> listSessions(
      RequestContext c, Queries.PublicSessions q) {
    return b.read(
        () -> {
          int page = q.page() == null ? 1 : q.page(),
              size = q.pageSize() == null ? 20 : q.pageSize();
          valid((long) page * size <= 10000, "公开查询最多查询前10000条，请缩小筛选");
          valid(
              q.startsFrom() == null
                  || q.startsTo() == null
                  || !q.startsFrom().isAfter(q.startsTo()),
              "时间区间逆序");
          valid(
              q.minPriceJiao() == null
                  || q.maxPriceJiao() == null
                  || q.minPriceJiao() <= q.maxPriceJiao(),
              "价格区间逆序");
          Map<UUID, EventEntity> events = new HashMap<>();
          for (var e :
              b.list(
                  EventEntity.class,
                  and(
                      eq("status", "PUBLISHED"),
                      q.categoryId() == null ? null : eq("categoryId", q.categoryId()))))
            if (b.effectiveEnd(e) == null && contains(e.name(), q.eventName()))
              events.put(e.id(), e);
          var sessions =
              b.list(
                  EventSessionEntity.class,
                  and(
                      in("eventId", events.keySet()),
                      nil("deletedAtMs"),
                      gt("endsAtMs", b.now()),
                      q.startsFrom() == null ? null : ge("startsAtMs", millis(q.startsFrom())),
                      q.startsTo() == null ? null : le("startsAtMs", millis(q.startsTo()))));
          List<Views.SessionSearchRow> matches = new ArrayList<>();
          for (var s : sessions) {
            if (!contains(s.location(), q.location())) continue;
            var v = publicSession(events.get(s.eventId()), s);
            if (q.salesStatus() != null && v.sessionSalesStatus() != q.salesStatus()) continue;
            var ids =
                v.ticketTypes().stream()
                    .filter(
                        t ->
                            contains(t.name(), q.ticketTypeName())
                                && (q.minPriceJiao() == null || t.priceJiao() >= q.minPriceJiao())
                                && (q.maxPriceJiao() == null || t.priceJiao() <= q.maxPriceJiao()))
                    .map(Views.TicketTypeView::id)
                    .toList();
            if (ids.isEmpty()) continue;
            matches.add(
                new Views.SessionSearchRow(
                    v.id(),
                    v.eventId(),
                    v.eventName(),
                    v.categoryId(),
                    v.categoryNameSnapshot(),
                    v.name(),
                    v.startsAt(),
                    v.endsAt(),
                    v.location(),
                    v.capacity(),
                    v.salesOpensAt(),
                    v.registrationClosesAt(),
                    v.remaining(),
                    v.sessionSalesStatus(),
                    v.ticketTypes(),
                    v.posterUrl(),
                    ids));
          }
          Comparator<Views.SessionSearchRow> sort =
              q.sortBy() == SortBy.remaining
                  ? Comparator.comparingLong(Views.SessionSearchRow::remaining)
                  : Comparator.comparing(Views.SessionSearchRow::startsAt);
          if (q.sortDirection() == SortDirection.desc) sort = sort.reversed();
          matches.sort(sort.thenComparing(v -> v.id().toString()));
          return ok(page(matches, page, size));
        });
  }

  public UseCaseResult<Views.EventPublic> eventDetail(RequestContext c, UUID eventId) {
    return b.read(
        () -> {
          var e = b.need(EventEntity.class, eventId, false);
          b.publicEvent(e);
          return ok(
              new Views.EventPublic(
                  e.id(),
                  e.name(),
                  e.categoryId(),
                  e.categoryNameSnapshot(),
                  Objects.requireNonNullElse(e.description(), ""),
                  e.posterFileId() == null ? null : "/api/public/events/" + e.id() + "/poster",
                  b.sessions(eventId).stream().map(s -> publicSession(e, s)).toList()));
        });
  }

  public UseCaseResult<PageResult<Views.EventManage>> listMine(RequestContext c, Queries.Events q) {
    return b.read(
        () -> {
          var u = b.actor(c, Role.ORGANIZER);
          return ok(
              page(
                  b
                      .ordered(
                          EventEntity.class,
                          and(
                              eq("organizerId", u.id()),
                              q.status() == null ? null : eq("status", q.status().name())),
                          RecordStore.Sort.desc("updatedAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::manage)
                      .toList(),
                  q.page(),
                  q.pageSize()));
        });
  }

  public UseCaseResult<Views.EventManage> createDraft(RequestContext c, Requests.Empty r) {
    return b.write(
        () -> {
          var u = b.actor(c, Role.ORGANIZER);
          var x =
              b.change(
                  c,
                  "ORG-02",
                  null,
                  r,
                  "EVENT",
                  i -> manage(b.need(EventEntity.class, i, false)),
                  () -> {
                    UUID event = id();
                    b.db()
                        .insert(
                            create(
                                EventEntity.class,
                                "id",
                                event,
                                "organizerId",
                                u.id(),
                                "status",
                                "DRAFT",
                                "settlementStatus",
                                "NOT_DUE",
                                "createdAtMs",
                                b.now(),
                                "updatedAtMs",
                                b.now(),
                                "version",
                                0L));
                    return event;
                  });
          return created(x.current(), x.replayed());
        });
  }

  public UseCaseResult<Views.EventManage> managementDetail(RequestContext c, UUID eventId) {
    return b.read(() -> ok(manage(b.eventAccess(c, eventId, false, false))));
  }

  public UseCaseResult<Mutation<Views.EventManage>> saveDraft(
      RequestContext c, UUID eventId, Requests.Draft r) {
    return b.write(
        () -> {
          b.actor(c, Role.ORGANIZER);
          b.eventAccess(c, eventId, true, false);
          var x =
              b.change(
                  c,
                  "ORG-04",
                  eventId,
                  r,
                  "EVENT",
                  i -> manage(b.need(EventEntity.class, i, false)),
                  () -> {
                    var e = b.need(EventEntity.class, eventId, true);
                    version(e.version(), r.expectedVersion());
                    require(e.status().equals("DRAFT"), "STATE_CONFLICT", "仅草稿可保存");
                    DomainRules.draft(r, false, b.now());
                    if (r.categoryId() != null) b.need(CategoryEntity.class, r.categoryId(), false);
                    Set<UUID> requestedSessions = new HashSet<>(), requestedTypes = new HashSet<>();
                    for (var s : r.sessions()) {
                      requestedSessions.add(s.id());
                      for (var t : s.ticketTypes()) requestedTypes.add(t.id());
                    }
                    // Release names inside this transaction before a rename swap / replacement with
                    // a new stable ID.
                    for (var oldSession : b.sessions(eventId)) {
                      if (!requestedSessions.contains(oldSession.id())) {
                        for (var checker : checkers(oldSession.id()))
                          disableChecker(checker, "DRAFT_SESSION_REMOVED");
                        for (var a :
                            b.list(
                                CheckerAssignmentEntity.class,
                                and(eq("sessionId", oldSession.id()), nil("revokedAtMs"))))
                          b.db()
                              .save(
                                  copy(
                                      a,
                                      "revokedAtMs",
                                      b.now(),
                                      "revocationReason",
                                      "DRAFT_SESSION_REMOVED"));
                        b.db()
                            .save(
                                copy(
                                    oldSession,
                                    "deletedAtMs",
                                    b.now(),
                                    "version",
                                    oldSession.version() + 1));
                      } else b.db().save(copy(oldSession, "name", null));
                      for (var oldType : b.types(oldSession.id())) {
                        if (!requestedSessions.contains(oldSession.id())
                            || !requestedTypes.contains(oldType.id()))
                          b.db()
                              .save(
                                  copy(
                                      oldType,
                                      "deletedAtMs",
                                      b.now(),
                                      "version",
                                      oldType.version() + 1));
                        else b.db().save(copy(oldType, "name", null));
                      }
                    }
                    Set<UUID> sessions = new HashSet<>(), types = new HashSet<>();
                    for (var s : r.sessions()) {
                      sessions.add(s.id());
                      var old = b.db().get(EventSessionEntity.class, s.id(), false).orElse(null);
                      valid(
                          old == null || old.eventId().equals(eventId) && old.deletedAtMs() == null,
                          "场次ID已占用或已删除");
                      var row =
                          new EventSessionEntity(
                              s.id(),
                              eventId,
                              trim(s.name()),
                              millis(s.startsAt()),
                              millis(s.endsAt()),
                              trim(s.location()),
                              s.capacity(),
                              millis(s.salesOpensAt()),
                              millis(s.registrationClosesAt()),
                              "NOT_DUE",
                              null,
                              old == null ? 0L : old.version() + 1);
                      if (old == null) b.db().insert(row);
                      else b.db().save(row);
                      for (var t : s.ticketTypes()) {
                        types.add(t.id());
                        var previous =
                            b.db().get(TicketTypeEntity.class, t.id(), false).orElse(null);
                        valid(
                            previous == null
                                || previous.sessionId().equals(s.id())
                                    && previous.deletedAtMs() == null,
                            "票种ID已占用或已删除");
                        var rowType =
                            new TicketTypeEntity(
                                t.id(),
                                s.id(),
                                trim(t.name()),
                                t.capacity(),
                                t.priceJiao(),
                                t.saleStopped(),
                                null,
                                previous == null ? 0L : previous.version() + 1);
                        if (previous == null) b.db().insert(rowType);
                        else b.db().save(rowType);
                      }
                      for (var t : b.types(s.id()))
                        if (!types.contains(t.id()))
                          b.db().save(copy(t, "deletedAtMs", b.now(), "version", t.version() + 1));
                    }
                    for (var s : b.sessions(eventId))
                      if (!sessions.contains(s.id())) {
                        for (var u : checkers(s.id())) disableChecker(u, "DRAFT_SESSION_REMOVED");
                        for (var a :
                            b.list(
                                CheckerAssignmentEntity.class,
                                and(eq("sessionId", s.id()), nil("revokedAtMs"))))
                          b.db()
                              .save(
                                  copy(
                                      a,
                                      "revokedAtMs",
                                      b.now(),
                                      "revocationReason",
                                      "DRAFT_SESSION_REMOVED"));
                        for (var t : b.types(s.id()))
                          b.db().save(copy(t, "deletedAtMs", b.now(), "version", t.version() + 1));
                        b.db().save(copy(s, "deletedAtMs", b.now(), "version", s.version() + 1));
                      }
                    b.db()
                        .save(
                            copy(
                                e,
                                "name",
                                trim(r.name()),
                                "categoryId",
                                r.categoryId(),
                                "proposedCategoryName",
                                trim(r.proposedCategoryName()),
                                "description",
                                trim(r.description()),
                                "version",
                                e.version() + 1,
                                "updatedAtMs",
                                b.now()));
                    return eventId;
                  });
          return ok(x.mutation());
        });
  }

  public void disableChecker(UserEntity user, String reason) {
    b.db()
        .save(
            copy(
                user,
                "enabled",
                false,
                "deletedAtMs",
                b.now(),
                "unavailableReason",
                reason,
                "updatedAtMs",
                b.now(),
                "version",
                user.version() + 1));
    identity.revoke(user.id(), reason);
  }

  public Requests.Draft draftOf(EventEntity e) {
    return new Requests.Draft(
        e.version(),
        e.name(),
        e.categoryId(),
        e.description(),
        b.sessions(e.id()).stream()
            .map(
                s ->
                    new Requests.SessionDraft(
                        s.id(),
                        s.name(),
                        b.time(s.startsAtMs()),
                        b.time(s.endsAtMs()),
                        s.location(),
                        s.capacity(),
                        b.time(s.salesOpensAtMs()),
                        b.time(s.registrationClosesAtMs()),
                        b.types(s.id()).stream()
                            .map(
                                t ->
                                    new Requests.TicketTypeDraft(
                                        t.id(),
                                        t.name(),
                                        t.capacity(),
                                        t.priceJiao(),
                                        t.saleStopped()))
                            .toList()))
            .toList(),
        e.proposedCategoryName());
  }

  public void publicationRules(EventEntity e) {
    publicationRules(e, false);
  }

  public void publicationRules(EventEntity e, boolean allowProposedCategory) {
    DomainRules.draft(draftOf(e), true, b.now());
    if (e.categoryId() == null) {
      require(
          allowProposedCategory
              && e.proposedCategoryName() != null
              && !trim(e.proposedCategoryName()).isEmpty(),
          "STATE_CONFLICT",
          "请选择类别或提出新的类别名称");
    } else {
      var category = b.need(CategoryEntity.class, e.categoryId(), false);
      require(category.enabled(), "STATE_CONFLICT", "类别已停用");
    }
    var owner = b.need(UserEntity.class, e.organizerId(), true);
    require(owner.enabled() && owner.deletedAtMs() == null, "ACCOUNT_UNAVAILABLE", "组织者不可用");
    require(!b.restricted(owner.phoneIdentityId(), "PUBLISH"), "PUBLISH_RESTRICTED", "组织者处于发布限制期");
    var own = b.sessions(e.id());
    for (var other :
        b.list(
            EventEntity.class,
            and(
                eq("organizerId", e.organizerId()),
                ne("id", e.id()),
                in("status", List.of("PENDING_REVIEW", "PUBLISHED")))))
      for (var a : own)
        for (var s : b.sessions(other.id()))
          if (s.startsAtMs() != null && s.endsAtMs() != null)
            require(
                !DomainRules.overlaps(a.startsAtMs(), a.endsAtMs(), s.startsAtMs(), s.endsAtMs()),
                "SESSION_TIME_CONFLICT",
                "与本人其他待审/已发布活动冲突");
  }

  private UseCaseResult<Mutation<Views.EventManage>> transition(
      RequestContext c, UUID eventId, Requests.Version r, boolean submit) {
    return b.write(
        () -> {
          b.actor(c, Role.ORGANIZER);
          b.eventAccess(c, eventId, true, false);
          String api = submit ? "ORG-07" : "ORG-08";
          var x =
              b.change(
                  c,
                  api,
                  eventId,
                  r,
                  "EVENT",
                  i -> manage(b.need(EventEntity.class, i, false)),
                  () -> {
                    var e = b.need(EventEntity.class, eventId, true);
                    version(e.version(), r.expectedVersion());
                    require(
                        e.status().equals(submit ? "DRAFT" : "PENDING_REVIEW"),
                        "STATE_CONFLICT",
                        "活动状态不允许此操作");
                    if (submit) publicationRules(e, true);
                    b.db()
                        .save(
                            copy(
                                e,
                                "status",
                                submit ? "PENDING_REVIEW" : "DRAFT",
                                "submittedAtMs",
                                submit ? b.now() : null,
                                "categoryNameSnapshot",
                                submit
                                    ? (e.categoryId() == null
                                        ? trim(e.proposedCategoryName())
                                        : b.need(CategoryEntity.class, e.categoryId(), false).name())
                                    : e.categoryNameSnapshot(),
                                "updatedAtMs",
                                b.now(),
                                "version",
                                e.version() + 1));
                    return eventId;
                  });
          return ok(x.mutation());
        });
  }

  public UseCaseResult<Mutation<Views.EventManage>> submitReview(
      RequestContext c, UUID eventId, Requests.Version r) {
    return transition(c, eventId, r, true);
  }

  public UseCaseResult<Mutation<Views.EventManage>> withdrawReview(
      RequestContext c, UUID eventId, Requests.Version r) {
    return transition(c, eventId, r, false);
  }

  public UseCaseResult<Mutation<Views.EventManage>> updatePublishedSettings(
      RequestContext c, UUID eventId, Requests.PublishedEdit r) {
    return b.write(
        () -> {
          b.actor(c, Role.ORGANIZER);
          b.eventAccess(c, eventId, true, false);
          var x =
              b.change(
                  c,
                  "ORG-09",
                  eventId,
                  r,
                  "EVENT",
                  i -> manage(b.need(EventEntity.class, i, false)),
                  () -> {
                    var e = b.need(EventEntity.class, eventId, true);
                    version(e.version(), r.expectedVersion());
                    require(
                        e.status().equals("PUBLISHED") && b.effectiveEnd(e) == null,
                        "STATE_CONFLICT",
                        "活动已不可编辑");
                    valid(present(r.description()) || present(r.sessions()), "没有修改字段");
                    Set<UUID> ids = new HashSet<>();
                    if (present(r.sessions()))
                      for (var edit : r.sessions().value()) {
                        valid(ids.add(edit.id()), "场次ID重复");
                        var s = b.need(EventSessionEntity.class, edit.id(), true);
                        valid(s.eventId().equals(eventId) && s.deletedAtMs() == null, "场次不属于本活动");
                        require(b.now() < s.endsAtMs(), "STATE_CONFLICT", "已结束场次不可修改");
                        int capacity =
                            present(edit.capacity()) ? edit.capacity().value() : s.capacity();
                        String location =
                            present(edit.location()) ? trim(edit.location().value()) : s.location();
                        Set<UUID> typeIds = new HashSet<>();
                        if (present(edit.ticketTypes()))
                          for (var t : edit.ticketTypes().value()) {
                            valid(typeIds.add(t.id()), "票种ID重复");
                            var type = b.need(TicketTypeEntity.class, t.id(), true);
                            valid(
                                type.sessionId().equals(s.id()) && type.deletedAtMs() == null,
                                "票种不属于本场");
                            int cap =
                                present(t.capacity()) ? t.capacity().value() : type.capacity();
                            require(
                                cap >= b.occupied(type.id()),
                                "CAPACITY_BELOW_OCCUPIED",
                                "票种容量不能低于占位数");
                            b.db()
                                .save(
                                    copy(
                                        type,
                                        "capacity",
                                        cap,
                                        "saleStopped",
                                        present(t.saleStopped())
                                            ? t.saleStopped().value()
                                            : type.saleStopped(),
                                        "version",
                                        type.version() + 1));
                          }
                        valid(
                            b.types(s.id()).stream().mapToInt(TicketTypeEntity::capacity).sum()
                                <= capacity,
                            "票种总容量超过场次");
                        b.db()
                            .save(
                                copy(
                                    s,
                                    "location",
                                    location,
                                    "capacity",
                                    capacity,
                                    "version",
                                    s.version() + 1));
                        if (!Objects.equals(location, s.location()) || capacity != s.capacity())
                          for (var o :
                              b.list(
                                  OrderEntity.class,
                                  and(
                                      eq("sessionId", s.id()),
                                      in(
                                          "status",
                                          List.of(
                                              "WAITLISTED",
                                              "OFFERED",
                                              "UNPAID",
                                              "REGISTERED",
                                              "CHECKED_IN")))))
                            b.notify(
                                o.userId(),
                                "CHANGE:" + e.id() + ":" + (e.version() + 1) + ":" + s.id(),
                                "EVENT_CHANGED",
                                "活动信息更新",
                                "地点或容量已更新，请查看最新活动信息",
                                "ORDER",
                                o.id(),
                                null);
                      }
                    b.db()
                        .save(
                            copy(
                                e,
                                "description",
                                present(r.description())
                                    ? trim(r.description().value())
                                    : e.description(),
                                "version",
                                e.version() + 1,
                                "updatedAtMs",
                                b.now()));
                    return eventId;
                  });
          return ok(x.mutation());
        });
  }

  public UseCaseResult<PageResult<Views.EventManage>> listAdminEvents(
      RequestContext c, Queries.AdminEvents q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          var rows =
              b
                  .ordered(
                      EventEntity.class,
                      and(
                          q.status() == null ? null : eq("status", q.status().name()),
                          q.organizerId() == null ? null : eq("organizerId", q.organizerId())),
                      RecordStore.Sort.desc("updatedAtMs"),
                      RecordStore.Sort.asc("id"))
                  .stream()
                  .filter(e -> contains(e.name(), q.name()))
                  .filter(
                      e ->
                          !e.status().equals("DRAFT")
                              || b.need(UserEntity.class, e.organizerId(), false).deletedAtMs()
                                  == null)
                  .map(this::manage)
                  .toList();
          return ok(page(rows, q.page(), q.pageSize()));
        });
  }

  public UseCaseResult<Views.ReviewQueue> reviewQueue(RequestContext c, Queries.Reviews q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          var rows =
              b
                  .ordered(
                      EventEntity.class,
                      eq("status", "PENDING_REVIEW"),
                      RecordStore.Sort.asc("submittedAtMs"),
                      RecordStore.Sort.asc("id"))
                  .stream()
                  .filter(
                      e ->
                          !Boolean.TRUE.equals(q.overdueOnly())
                              || e.submittedAtMs() + DAY <= b.now())
                  .map(this::manage)
                  .toList();
          var p = page(rows, q.page(), q.pageSize());
          return ok(
              new Views.ReviewQueue(
                  p.items(),
                  p.page(),
                  p.pageSize(),
                  p.total(),
                  dailyCount(),
                  100,
                  b.time(b.now()).toLocalDate().toString()));
        });
  }

  public int dailyCount() {
    return b.db()
        .one(PublicationQuotaEntity.class, eq("day", b.time(b.now()).toLocalDate()), false)
        .map(PublicationQuotaEntity::publishedCount)
        .orElse(0);
  }

  public UseCaseResult<Views.ReviewResult> approve(
      RequestContext c, UUID eventId, Requests.Version r) {
    return review(c, eventId, r.expectedVersion(), null, r);
  }

  public UseCaseResult<Views.ReviewResult> reject(
      RequestContext c, UUID eventId, Requests.VersionReason r) {
    return review(c, eventId, r.expectedVersion(), trim(r.reason()), r);
  }

  private UseCaseResult<Views.ReviewResult> review(
      RequestContext c, UUID eventId, long expected, String reason, Object request) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          var initial = b.eventAccess(c, eventId, true, false);
          String api = reason == null ? "ADM-10" : "ADM-11";
          var x =
              b.change(
                  c,
                  api,
                  eventId,
                  request,
                  "REVIEW",
                  i -> {
                    var rev = b.need(EventReviewEntity.class, i, false);
                    return new Views.ReviewResult(
                        rev.id(),
                        rev.outcome(),
                        rev.reasonCode(),
                        rev.reason(),
                        true,
                        manage(b.need(EventEntity.class, eventId, false)),
                        dailyCount());
                  },
                  () -> {
                    var e = b.need(EventEntity.class, eventId, true);
                    version(e.version(), expected);
                    require(e.status().equals("PENDING_REVIEW"), "STATE_CONFLICT", "活动已不在待审状态");
                    String outcome = reason == null ? "PUBLISHED" : "REJECTED",
                        why = reason,
                        reasonCode = null;
                    if (reason == null) {
                      try {
                        if (e.categoryId() == null) {
                          String proposed = trim(e.proposedCategoryName());
                          if (proposed != null && !proposed.isEmpty()) {
                            var existing =
                                b.db()
                                    .one(
                                        CategoryEntity.class,
                                        eq("nameNormalized", normalize(proposed)),
                                        false)
                                    .orElse(null);
                            UUID categoryId;
                            if (existing == null) {
                              categoryId = id();
                              b.db()
                                  .insert(
                                      new CategoryEntity(
                                          categoryId,
                                          proposed,
                                          normalize(proposed),
                                          true,
                                          0L,
                                          b.now(),
                                          b.now()));
                            } else {
                              require(existing.enabled(), "STATE_CONFLICT", "类别已停用");
                              categoryId = existing.id();
                            }
                            b.db()
                                .save(
                                    copy(
                                        e,
                                        "categoryId",
                                        categoryId,
                                        "categoryNameSnapshot",
                                        proposed));
                            e = b.need(EventEntity.class, eventId, true);
                          }
                        }
                        publicationRules(e);
                        for (var s : b.sessions(eventId))
                          require(
                              !checkers(s.id()).isEmpty(),
                              "LAST_CHECKER_REQUIRED",
                              "每场至少需要一个可用检票账号");
                        LocalDate day = b.time(b.now()).toLocalDate();
                        b.db().ensurePublicationDay(day);
                        var quota =
                            b.db()
                                .one(PublicationQuotaEntity.class, eq("day", day), true)
                                .orElseThrow();
                        require(quota.publishedCount() < 100, "STATE_CONFLICT", "今日全站发布额度已满");
                        b.db()
                            .save(
                                copy(
                                    quota,
                                    "publishedCount",
                                    quota.publishedCount() + 1,
                                    "version",
                                    quota.version() + 1));
                      } catch (ApiException failure) {
                        if (failure.status() >= 500) throw failure;
                        outcome = "RETURNED_TO_DRAFT";
                        why = failure.getMessage();
                        reasonCode = failure.code();
                      }
                    }
                    UUID review = id();
                    b.db()
                        .insert(
                            new EventReviewEntity(
                                review,
                                eventId,
                                c.actor().userId(),
                                e.version(),
                                outcome,
                                reasonCode,
                                why,
                                b.now()));
                    boolean publish = outcome.equals("PUBLISHED");
                    b.db()
                        .save(
                            copy(
                                e,
                                "status",
                                publish ? "PUBLISHED" : "DRAFT",
                                "publishedAtMs",
                                publish ? b.now() : e.publishedAtMs(),
                                "categoryNameSnapshot",
                                publish
                                    ? b.need(CategoryEntity.class, e.categoryId(), false).name()
                                    : e.categoryNameSnapshot(),
                                "lastReviewReason",
                                why,
                                "version",
                                e.version() + 1,
                                "updatedAtMs",
                                b.now()));
                    b.notify(
                        e.organizerId(),
                        "REVIEW:" + review,
                        "REVIEW_RESULT",
                        "审核结果",
                        publish ? "活动已发布" : Objects.requireNonNullElse(why, "审核未通过"),
                        "EVENT",
                        eventId,
                        null);
                    return review;
                  });
          var v = x.current();
          return ok(
              new Views.ReviewResult(
                  x.operationId(),
                  v.outcome(),
                  v.reasonCode(),
                  v.reason(),
                  x.replayed(),
                  v.current(),
                  v.dailyPublishedCount()));
        });
  }
}
