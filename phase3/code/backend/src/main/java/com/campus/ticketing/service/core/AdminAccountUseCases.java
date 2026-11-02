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
public class AdminAccountUseCases {
  private final BusinessSupport b;
  private final IdentityUseCases identity;
  private final EventUseCases events;

  public AdminAccountUseCases(BusinessSupport b, IdentityUseCases identity, EventUseCases events) {
    this.b = b;
    this.identity = identity;
    this.events = events;
  }

  private CheckerAssignmentEntity assignment(UUID user) {
    return b.db()
        .one(CheckerAssignmentEntity.class, and(eq("checkerId", user), nil("revokedAtMs")), false)
        .orElse(null);
  }

  public Views.ManagedAccount managed(UserEntity u) {
    var a = assignment(u.id());
    UUID session = a == null ? null : a.sessionId();
    return new Views.ManagedAccount(
        u.id(),
        u.username(),
        Role.valueOf(u.role()),
        u.enabled(),
        b.time(u.deletedAtMs()),
        u.mustChangePassword(),
        session,
        session == null ? null : b.need(EventSessionEntity.class, session, false).eventId(),
        u.version());
  }

  private void lockAccounts(RequestContext c, UUID target) {
    var ids = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
    for (var u : b.list(UserEntity.class, and(eq("role", "ADMIN"), nil("deletedAtMs"))))
      ids.add(u.id());
    ids.add(c.actor().userId());
    if (target != null) ids.add(target);
    for (UUID id : ids) b.need(UserEntity.class, id, true);
  }

  private EventSessionEntity configurable(UUID sessionId) {
    var s = b.need(EventSessionEntity.class, sessionId, false);
    var e = b.need(EventEntity.class, s.eventId(), true);
    require(
        s.deletedAtMs() == null
            && s.endsAtMs() != null
            && b.now() < s.endsAtMs()
            && List.of("PENDING_REVIEW", "PUBLISHED").contains(e.status())
            && b.effectiveEnd(e) == null,
        "STATE_CONFLICT",
        "检票员仅可配置待审/发布未结束场次");
    return s;
  }

  private void protect(UserEntity target, boolean losing) {
    if (!losing || target.deletedAtMs() != null || !target.enabled()) return;
    if (target.role().equals("ADMIN"))
      require(
          b.db()
                  .count(
                      UserEntity.class,
                      and(eq("role", "ADMIN"), eq("enabled", true), nil("deletedAtMs")))
              > 1,
          "LAST_CHECKER_REQUIRED",
          "不能删除/停用最后一个可用管理员");
    if (target.role().equals("CHECKER")) {
      var a = assignment(target.id());
      if (a != null) {
        var s = b.need(EventSessionEntity.class, a.sessionId(), false);
        var e = b.need(EventEntity.class, s.eventId(), false);
        if (e.status().equals("PUBLISHED") && b.effectiveEnd(e) == null && b.now() < s.endsAtMs())
          require(events.checkers(s.id()).size() > 1, "LAST_CHECKER_REQUIRED", "不能移除本场最后一个可用检票员");
      }
    }
  }

  public UseCaseResult<PageResult<Views.ManagedAccount>> listAccounts(
      RequestContext c, Queries.Accounts q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          var rows =
              b
                  .ordered(
                      UserEntity.class,
                      and(
                          q.role() == null ? null : eq("role", q.role().name()),
                          q.enabled() == null ? null : eq("enabled", q.enabled())),
                      RecordStore.Sort.desc("createdAtMs"),
                      RecordStore.Sort.asc("id"))
                  .stream()
                  .filter(u -> contains(u.username(), q.username()))
                  .map(this::managed)
                  .filter(
                      v ->
                          (q.eventId() == null || q.eventId().equals(v.eventId()))
                              && (q.sessionId() == null
                                  || q.sessionId().equals(v.assignedSessionId())))
                  .toList();
          return ok(page(rows, q.page(), q.pageSize()));
        });
  }

  public UseCaseResult<Views.ManagedAccount> createAccount(
      RequestContext c, Requests.CreateAccount r) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          valid(
              r.role() == ManagedRole.CHECKER ? r.sessionId() != null : r.sessionId() == null,
              "检票员必须绑定场次，管理员不能绑定");
          if (r.sessionId() != null) configurable(r.sessionId());
          lockAccounts(c, null);
          var x =
              b.change(
                  c,
                  "ADM-02",
                  null,
                  r,
                  "ACCOUNT",
                  i -> managed(b.need(UserEntity.class, i, false)),
                  () -> {
                    valid(
                        b.db()
                                .count(
                                    UserEntity.class,
                                    and(
                                        eq("usernameNormalized", normalize(r.username())),
                                        nil("deletedAtMs")))
                            == 0,
                        "用户名已存在");
                    UUID uid = id();
                    b.db()
                        .insert(
                            new UserEntity(
                                uid,
                                trim(r.username()),
                                normalize(r.username()),
                                null,
                                null,
                                null,
                                identity.passwordEncoder().encode(r.initialPassword()),
                                r.role().name(),
                                null,
                                true,
                                r.role() == ManagedRole.ADMIN,
                                null,
                                null,
                                b.now(),
                                b.now(),
                                0L));
                    if (r.sessionId() != null)
                      b.db()
                          .insert(
                              new CheckerAssignmentEntity(
                                  id(),
                                  uid,
                                  r.sessionId(),
                                  c.actor().userId(),
                                  b.now(),
                                  null,
                                  null));
                    return uid;
                  });
          return created(x.current(), x.replayed());
        });
  }

  public UseCaseResult<Mutation<Views.ManagedAccount>> updateAccount(
      RequestContext c, UUID accountId, Requests.UpdateAccount r) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          var target = b.need(UserEntity.class, accountId, false);
          var old = assignment(accountId);
          if (old != null && EventUseCases.present(r.assignedSessionId()))
            valid(
                b.need(EventSessionEntity.class, old.sessionId(), false)
                    .eventId()
                    .equals(
                        b.need(EventSessionEntity.class, r.assignedSessionId().value(), false)
                            .eventId()),
                "检票账号不得跨活动改绑");
          if (old != null) configurableForRemoval(old.sessionId());
          if (EventUseCases.present(r.assignedSessionId()))
            configurable(r.assignedSessionId().value());
          lockAccounts(c, accountId);
          var x =
              b.change(
                  c,
                  "ADM-03",
                  accountId,
                  r,
                  "ACCOUNT",
                  i -> managed(b.need(UserEntity.class, i, false)),
                  () -> {
                    var u = b.need(UserEntity.class, accountId, true);
                    EventUseCases.version(u.version(), r.expectedVersion());
                    require(
                        u.deletedAtMs() == null && List.of("ADMIN", "CHECKER").contains(u.role()),
                        "STATE_CONFLICT",
                        "仅能管理未删除的管理员/检票账号");
                    valid(
                        EventUseCases.present(r.enabled())
                            || EventUseCases.present(r.assignedSessionId()),
                        "没有修改字段");
                    boolean enabled =
                        EventUseCases.present(r.enabled()) ? r.enabled().value() : u.enabled();
                    boolean moving =
                        EventUseCases.present(r.assignedSessionId())
                            && (old == null
                                || !old.sessionId().equals(r.assignedSessionId().value()));
                    if (moving) {
                      valid(u.role().equals("CHECKER") && old != null, "只有当前检票账号可以同活动改绑");
                      var a = b.need(EventSessionEntity.class, old.sessionId(), false);
                      var z =
                          b.need(EventSessionEntity.class, r.assignedSessionId().value(), false);
                      valid(a.eventId().equals(z.eventId()), "检票账号不得跨活动改绑");
                    }
                    protect(u, !enabled || moving);
                    if (moving) {
                      b.db()
                          .save(
                              copy(old, "revokedAtMs", b.now(), "revocationReason", "REASSIGNED"));
                      b.db()
                          .insert(
                              new CheckerAssignmentEntity(
                                  id(),
                                  accountId,
                                  r.assignedSessionId().value(),
                                  c.actor().userId(),
                                  b.now(),
                                  null,
                                  null));
                    }
                    b.db()
                        .save(
                            copy(
                                u,
                                "enabled",
                                enabled,
                                "updatedAtMs",
                                b.now(),
                                "version",
                                u.version() + 1));
                    identity.revoke(accountId, "ACCOUNT_UPDATED");
                    return accountId;
                  });
          return ok(x.mutation());
        });
  }

  private void configurableForRemoval(UUID sessionId) {
    var s = b.need(EventSessionEntity.class, sessionId, false);
    b.need(EventEntity.class, s.eventId(), true);
  }

  public UseCaseResult<Mutation<Views.ManagedAccount>> deleteAccount(
      RequestContext c, UUID accountId, Queries.Version q) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          var assignment = assignment(accountId);
          if (assignment != null) configurableForRemoval(assignment.sessionId());
          lockAccounts(c, accountId);
          var x =
              b.change(
                  c,
                  "ADM-04",
                  accountId,
                  q,
                  "ACCOUNT",
                  i -> managed(b.need(UserEntity.class, i, false)),
                  () -> {
                    var u = b.need(UserEntity.class, accountId, true);
                    EventUseCases.version(u.version(), q.expectedVersion());
                    require(
                        u.deletedAtMs() == null && List.of("ADMIN", "CHECKER").contains(u.role()),
                        "STATE_CONFLICT",
                        "账号不可删除");
                    protect(u, true);
                    b.db()
                        .save(
                            copy(
                                u,
                                "deletedAtMs",
                                b.now(),
                                "enabled",
                                false,
                                "updatedAtMs",
                                b.now(),
                                "version",
                                u.version() + 1));
                    identity.revoke(accountId, "ACCOUNT_DELETED");
                    if (assignment != null)
                      b.db()
                          .save(
                              copy(
                                  assignment,
                                  "revokedAtMs",
                                  b.now(),
                                  "revocationReason",
                                  "ACCOUNT_DELETED"));
                    return accountId;
                  });
          return ok(x.mutation());
        });
  }

  public Views.RecoveryView recovery(RecoveryEntity r) {
    return new Views.RecoveryView(
        r.id(),
        r.account(),
        r.phone(),
        r.targetAccountId(),
        RecoveryStatus.valueOf(r.status()),
        b.time(r.createdAtMs()),
        r.verificationNote(),
        b.time(r.resolvedAtMs()));
  }

  public UseCaseResult<PageResult<Views.RecoveryView>> listRecoveries(
      RequestContext c, Queries.Recoveries q) {
    return b.read(
        () -> {
          b.actor(c, Role.ADMIN);
          return ok(
              page(
                  b
                      .ordered(
                          RecoveryEntity.class,
                          q.status() == null ? null : eq("status", q.status().name()),
                          RecordStore.Sort.asc("createdAtMs"),
                          RecordStore.Sort.asc("id"))
                      .stream()
                      .map(this::recovery)
                      .toList(),
                  q.page(),
                  q.pageSize()));
        });
  }

  private void matchingRecovery(RecoveryEntity r, UserEntity u) {
    require(
        u.deletedAtMs() == null
            && r.targetAccountId() != null
            && r.targetAccountId().equals(u.id())
            && u.phoneIdentityId() != null
            && b.need(PhoneIdentityEntity.class, u.phoneIdentityId(), false)
                .phone()
                .equals(r.phone()),
        "STATE_CONFLICT",
        "找回依据与原账号身份不符或已失效");
  }

  public UseCaseResult<Views.RecoveryView> resolveRecovery(
      RequestContext c, UUID requestId, Requests.ResolveRecovery r) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          var x =
              b.change(
                  c,
                  "ADM-07",
                  requestId,
                  r,
                  "RECOVERY",
                  i -> recovery(b.need(RecoveryEntity.class, i, false)),
                  () -> {
                    var old = b.need(RecoveryEntity.class, requestId, true);
                    require(old.status().equals("PENDING"), "STATE_CONFLICT", "找回申请已处理");
                    if (r.decision() == RecoveryDecision.VERIFIED) {
                      require(old.targetAccountId() != null, "STATE_CONFLICT", "原受理未匹配账号，不允许核验通过");
                      matchingRecovery(old, b.need(UserEntity.class, old.targetAccountId(), true));
                    }
                    b.db()
                        .save(
                            copy(
                                old,
                                "status",
                                r.decision().name(),
                                "verificationNote",
                                trim(r.verificationNote()),
                                "verifiedBy",
                                c.actor().userId(),
                                "verifiedAtMs",
                                b.now(),
                                "resolvedAtMs",
                                r.decision() == RecoveryDecision.REJECTED ? b.now() : null));
                    return requestId;
                  });
          return ok(x.current());
        });
  }

  public UseCaseResult<Views.ResetPassword> resetPassword(
      RequestContext c, UUID accountId, Requests.ResetPassword r) {
    return b.write(
        () -> {
          b.actor(c, Role.ADMIN);
          lockAccounts(c, accountId);
          var x =
              b.change(
                  c,
                  "ADM-05",
                  accountId,
                  r,
                  "ACCOUNT",
                  i -> new Views.ResetPassword(i, true, true),
                  () -> {
                    var u = b.need(UserEntity.class, accountId, true);
                    require(u.deletedAtMs() == null, "ACCOUNT_UNAVAILABLE", "已删除账号不可重置");
                    if (u.role().equals("CHECKER")) {
                      var assigned = assignment(u.id());
                      require(assigned != null, "ACCOUNT_UNAVAILABLE", "检票授权已撤销");
                      var event =
                          b.need(
                              EventEntity.class,
                              b.need(EventSessionEntity.class, assigned.sessionId(), false)
                                  .eventId(),
                              false);
                      require(
                          List.of("PENDING_REVIEW", "PUBLISHED").contains(event.status())
                              && b.effectiveEnd(event) == null,
                          "ACCOUNT_UNAVAILABLE",
                          "活动已终结，检票凭据不可重置");
                    }
                    if (List.of("PARTICIPANT", "ORGANIZER").contains(u.role())) {
                      require(r.recoveryRequestId() != null, "STATE_CONFLICT", "普通用户重置必须有人工核验依据");
                      var recovery = b.need(RecoveryEntity.class, r.recoveryRequestId(), true);
                      require(
                          recovery.status().equals("VERIFIED"), "STATE_CONFLICT", "找回依据尚未通过或已经消费");
                      matchingRecovery(recovery, u);
                      b.db().save(copy(recovery, "status", "RESOLVED", "resolvedAtMs", b.now()));
                    }
                    b.db()
                        .save(
                            copy(
                                u,
                                "passwordHash",
                                identity.passwordEncoder().encode(r.initialPassword()),
                                "mustChangePassword",
                                true,
                                "updatedAtMs",
                                b.now(),
                                "version",
                                u.version() + 1));
                    identity.revoke(accountId, "PASSWORD_RESET");
                    return accountId;
                  });
          return ok(x.current());
        });
  }
}
