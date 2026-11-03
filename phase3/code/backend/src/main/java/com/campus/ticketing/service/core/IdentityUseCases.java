package com.campus.ticketing.service.core;

import static com.campus.ticketing.common.EntityRecords.*;
import static com.campus.ticketing.dao.RecordStore.Filter.*;
import static com.campus.ticketing.service.core.BusinessSupport.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.entity.*;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.security.SessionPrincipal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class IdentityUseCases {
  private final BusinessSupport b;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
  private final SecureRandom random = new SecureRandom();
  private final boolean secure;

  public IdentityUseCases(BusinessSupport b, Environment env) {
    this.b = b;
    secure = env.getProperty("app.cookies.secure", Boolean.class, false);
  }

  public BCryptPasswordEncoder passwordEncoder() {
    return passwords;
  }

  private ResponseCookie cookie(String token, long ttl) {
    return ResponseCookie.from("TICKET_SESSION", token)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/api")
        .maxAge(Duration.ofMillis(ttl))
        .build();
  }

  private List<ResponseCookie> clearedCookies() {
    return List.of(
        cookie("", 0),
        ResponseCookie.from("XSRF-TOKEN", "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api")
            .maxAge(Duration.ZERO)
            .build());
  }

  public UseCaseResult<Views.Identity> login(RequestContext c, Requests.Login request) {
    return b.write(
        () -> {
          UserEntity u =
              b.db()
                  .one(
                      UserEntity.class,
                      and(
                          eq("usernameNormalized", normalize(request.username())),
                          nil("deletedAtMs")),
                      true)
                  .orElse(null);
          // Same BCrypt work for an unknown user avoids a fast username-existence branch.
          String encoded = u == null ? DUMMY_HASH : u.passwordHash();
          boolean matches = passwords.matches(request.password(), encoded);
          if (u == null || !matches) throw error(401, "INVALID_CREDENTIALS", "用户名或密码错误");
          available(u);
          return issueSession(c, u, "AUTH-02");
        });
  }

  private UseCaseResult<Views.Identity> issueSession(RequestContext c, UserEntity u, String api) {
    revoke(u.id(), "SECOND_LOGIN");
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    long createdAt = b.now(), expires = createdAt + SESSION_TTL;
    b.db()
        .insert(new LoginSessionEntity(id(), u.id(), hash(token), createdAt, expires, null, null));
    b.audit(
        new RequestContext(
            new SessionPrincipal(
                u.id(),
                Role.valueOf(u.role()),
                u.mustChangePassword(),
                Instant.ofEpochMilli(expires)),
            null,
            c.traceId()),
        api,
        "ACCOUNT",
        u.id(),
        "SUCCESS",
        null,
        null);
    var cookies = new ArrayList<ResponseCookie>();
    cookies.add(cookie(token, SESSION_TTL));
    cookies.add(clearedCookies().get(1));
    return new UseCaseResult<Views.Identity>(200, identity(u, expires), cookies);
  }

  public boolean hasPhoneAccount(String phone, RegisterRole role) {
    return b.read(() -> phoneAccount(phone, role, false) != null);
  }

  private UserEntity phoneAccount(String phone, RegisterRole role, boolean lock) {
    var p = b.db().one(PhoneIdentityEntity.class, eq("phone", phone), false).orElse(null);
    return p == null
        ? null
        : b.db()
            .one(
                UserEntity.class,
                and(currentPhoneFilter(p.id()), eq("role", role.name()), nil("deletedAtMs")),
                lock)
            .orElse(null);
  }

  public UseCaseResult<Views.Identity> loginByPhone(RequestContext c, Requests.PhoneLogin r) {
    return b.write(
        () -> {
          var u = phoneAccount(r.phone(), r.role(), true);
          if (u == null) throw error(404, "PHONE_NOT_REGISTERED", "该手机号尚未注册当前角色，请先注册");
          available(u);
          return issueSession(c, u, "AUTH-07");
        });
  }

  // A valid BCrypt hash, no usable seeded account or password is created.
  private static final String DUMMY_HASH =
      "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

  public UseCaseResult<Views.Registered> register(RequestContext c, Requests.Register r) {
    return b.write(
        () -> {
          var phone =
              b.db().one(PhoneIdentityEntity.class, eq("phone", r.phone()), true).orElse(null);
          if (phone == null) {
            phone = new PhoneIdentityEntity(id(), r.phone(), 0, null, null, b.now(), 0L);
            b.db().insert(phone);
          }
          require(phone.blacklistedAtMs() == null, "ACCOUNT_CLOSE_BLOCKED", "该手机号已被永久禁止重新注册");
          valid(
              b.db()
                      .count(
                          UserEntity.class,
                          and(
                              nil("deletedAtMs"),
                              eq("usernameNormalized", normalize(r.username()))))
                  == 0,
              "用户名已存在，请换一个用户名");
          valid(
              r.email() == null
                  || r.email().isBlank()
                  || b.db()
                          .count(
                              UserEntity.class,
                              and(
                                  nil("deletedAtMs"),
                                  eq("emailNormalized", normalize(r.email()))))
                      == 0,
              "这个邮箱已被其他账号使用");
          valid(
              b.db()
                      .count(
                          UserEntity.class,
                          and(
                              nil("deletedAtMs"),
                              currentPhoneFilter(phone.id()),
                              eq("role", r.role().name())))
                  == 0,
              "该手机号已经注册过当前角色，请直接登录或找回密码");
          UserEntity u =
              new UserEntity(
                  id(),
                  trim(r.username()),
                  normalize(r.username()),
                  r.email() == null || r.email().isBlank() ? null : trim(r.email()),
                  r.email() == null || r.email().isBlank() ? null : normalize(r.email()),
                  phone.id(),
                  passwords.encode(r.password()),
                  r.role().name(),
                  null,
                  true,
                  false,
                  null,
                  null,
                  b.now(),
                  b.now(),
                  0L);
          b.db().insert(u);
          b.audit(c, "AUTH-03", "ACCOUNT", u.id(), "SUCCESS", null, null);
          return created(new Views.Registered(u.id(), u.username(), Role.valueOf(u.role())), false);
        });
  }

  public UseCaseResult<Views.RecoveryReceipt> requestRecovery(
      RequestContext c, Requests.Recovery r) {
    return b.write(
        () -> {
          var open =
              b.db()
                  .one(
                      RecoveryEntity.class,
                      and(
                          eq("accountNormalized", normalize(r.account())),
                          eq("phone", r.phone()),
                          in("status", List.of("PENDING", "VERIFIED"))),
                      true);
          if (open.isPresent()) {
            var old = open.get();
            require(
                Objects.equals(old.currentPhone(), r.currentPhone())
                    && Objects.equals(old.role(), r.role().name())
                    && Objects.equals(old.statement(), trim(r.statement()))
                    && Objects.equals(old.pastBehavior(), trim(r.pastBehavior())),
                "RECOVERY_PENDING_EXISTS",
                "该账号已有待处理申诉，资料不同请联系管理员核对原申请");
            return new UseCaseResult<>(
                202, new Views.RecoveryReceipt(old.id(), "RECEIVED", "已受理，请等待管理员审核"), List.of());
          }
          var target =
              b.db()
                  .one(
                      UserEntity.class,
                      and(
                          eq("usernameNormalized", normalize(r.account())),
                          eq("role", r.role().name()),
                          nil("deletedAtMs")),
                      false)
                  .orElse(null);
          if (target != null
              && (loginPhoneId(target) == null
                  || !b.need(PhoneIdentityEntity.class, loginPhoneId(target), false)
                      .phone()
                      .equals(r.phone()))) target = null;
          UUID requestId = id();
          b.db()
              .insert(
                  new RecoveryEntity(
                      requestId,
                      trim(r.account()),
                      normalize(r.account()),
                      r.phone(),
                      target == null ? null : target.id(),
                      "PENDING",
                      null,
                      null,
                      null,
                      b.now(),
                      null,
                      r.currentPhone(),
                      r.role().name(),
                      trim(r.statement()),
                      trim(r.pastBehavior()),
                      b.now()));
          b.audit(c, "AUTH-04", "RECOVERY", requestId, "SUCCESS", null, null);
          return new UseCaseResult<>(
              202, new Views.RecoveryReceipt(requestId, "RECEIVED", "已受理，请等待管理员审核"), List.of());
        });
  }

  public UseCaseResult<Views.ChangedPassword> resetPasswordByPhone(
      RequestContext c, Requests.PhonePasswordReset r) {
    return b.write(
        () -> {
          var u = phoneAccount(r.phone(), r.role(), true);
          if (u == null) throw error(404, "PHONE_NOT_REGISTERED", "该手机号尚未绑定当前角色的账号");
          available(u);
          b.db()
              .save(
                  copy(
                      u,
                      "passwordHash",
                      passwords.encode(r.newPassword()),
                      "mustChangePassword",
                      false,
                      "updatedAtMs",
                      b.now(),
                      "version",
                      u.version() + 1));
          revoke(u.id(), "PHONE_PASSWORD_RESET");
          b.audit(
              new RequestContext(
                  new SessionPrincipal(
                      u.id(), Role.valueOf(u.role()), false, b.clock.instant().plusSeconds(60)),
                  null,
                  c.traceId()),
              "AUTH-08",
              "ACCOUNT",
              u.id(),
              "SUCCESS",
              null,
              null);
          return new UseCaseResult<>(200, new Views.ChangedPassword(true, true), clearedCookies());
        });
  }

  /** Login contact changes; the original business identity and its history remain attached. */
  public UserEntity bindPhone(UserEntity u, String phone) {
    valid(List.of("PARTICIPANT", "ORGANIZER").contains(u.role()), "该角色不绑定手机号");
    var existing = phoneAccount(phone, RegisterRole.valueOf(u.role()), false);
    require(
        existing == null || existing.id().equals(u.id()),
        "PHONE_ALREADY_BOUND",
        "新手机号已被同角色的其他账号绑定");
    var target = b.db().one(PhoneIdentityEntity.class, eq("phone", phone), true).orElse(null);
    if (target == null) {
      target = new PhoneIdentityEntity(id(), phone, 0, null, null, b.now(), 0L);
      b.db().insert(target);
    }
    require(target.blacklistedAtMs() == null, "PHONE_UNAVAILABLE", "该手机号已被限制绑定，请联系管理员");
    var changed =
        copy(
            u,
            "loginPhoneIdentityId",
            target.id(),
            "updatedAtMs",
            b.now(),
            "version",
            u.version() + 1);
    b.db().save(changed);
    return changed;
  }

  public UseCaseResult<Views.ChangedPhone> changePhone(
      RequestContext c, Requests.ChangePhone r, Runnable verify) {
    return b.write(
        () -> {
          var actor = b.actor(c, Role.PARTICIPANT, Role.ORGANIZER);
          var x =
              b.change(
                  c,
                  "ME-27",
                  actor.id(),
                  r,
                  "ACCOUNT",
                  userId -> {
                    var u = b.need(UserEntity.class, userId, false);
                    return new Views.ChangedPhone(
                        mask(b.need(PhoneIdentityEntity.class, loginPhoneId(u), false).phone()),
                        true);
                  },
                  () -> {
                    var u = b.need(UserEntity.class, actor.id(), true);
                    require(
                        !b.need(PhoneIdentityEntity.class, loginPhoneId(u), false)
                            .phone()
                            .equals(r.newPhone()),
                        "PHONE_UNCHANGED",
                        "新手机号与当前手机号相同");
                    verify.run();
                    bindPhone(u, r.newPhone());
                    revoke(u.id(), "PHONE_CHANGED");
                    b.notify(
                        u.id(),
                        "PHONE_CHANGED:" + u.id() + ":" + (u.version() + 1),
                        "ACCOUNT_CHANGED",
                        "绑定手机号已更新",
                        "新手机号：" + mask(r.newPhone()) + "。请重新登录；历史订单和业务限制保留。",
                        "ACCOUNT",
                        u.id(),
                        null);
                    return u.id();
                  });
          return new UseCaseResult<>(200, x.current(), clearedCookies());
        });
  }

  public UseCaseResult<Views.LoggedOut> logout(RequestContext c, Requests.Empty r) {
    return b.write(
        () -> {
          UserEntity u = b.actor(c);
          b.need(UserEntity.class, u.id(), true);
          revoke(u.id(), "LOGOUT");
          b.audit(c, "AUTH-05", "ACCOUNT", u.id(), "SUCCESS", null, null);
          return new UseCaseResult<>(200, new Views.LoggedOut(true), clearedCookies());
        });
  }

  public void revoke(UUID userId, String reason) {
    for (var s : b.list(LoginSessionEntity.class, and(eq("userId", userId), nil("revokedAtMs"))))
      b.db().save(copy(s, "revokedAtMs", b.now(), "revocationReason", reason));
  }

  public SessionPrincipal authenticate(String token) {
    return b.read(
        () -> {
          if (token.length() > 128) throw error(401, "SESSION_REVOKED", "会话无效");
          var s =
              b.db()
                  .one(LoginSessionEntity.class, eq("tokenHash", hash(token)), false)
                  .orElseThrow(() -> error(401, "SESSION_REVOKED", "会话已失效"));
          if (s.revokedAtMs() != null) throw error(401, "SESSION_REVOKED", "会话已撤销");
          if (b.now() >= s.expiresAtMs()) throw error(401, "SESSION_EXPIRED", "会话已到期");
          UserEntity u = b.need(UserEntity.class, s.userId(), false);
          available(u);
          return new SessionPrincipal(
              u.id(),
              Role.valueOf(u.role()),
              u.mustChangePassword(),
              Instant.ofEpochMilli(s.expiresAtMs()),
              s.id());
        });
  }

  public void available(UserEntity u) {
    if (u.deletedAtMs() != null || !u.enabled()) throw error(403, "ACCOUNT_UNAVAILABLE", "账号不可用");
    if (u.role().equals("CHECKER")) {
      var a =
          b.db()
              .one(
                  CheckerAssignmentEntity.class,
                  and(eq("checkerId", u.id()), nil("revokedAtMs")),
                  false)
              .orElseThrow(() -> error(403, "ACCOUNT_UNAVAILABLE", "检票授权已撤销"));
      var s = b.need(EventSessionEntity.class, a.sessionId(), false);
      var e = b.need(EventEntity.class, s.eventId(), false);
      if (s.deletedAtMs() != null
          || Set.of("CANCELLED", "TAKEN_DOWN", "ENDED").contains(e.status())
          || b.effectiveEnd(e) != null) throw error(403, "ACCOUNT_UNAVAILABLE", "活动已终结，检票凭据失效");
    }
  }

  public Views.Identity identity(UserEntity u, long expires) {
    if (u.mustChangePassword())
      return new Views.ForcePasswordUser(
          u.id(), u.username(), Role.valueOf(u.role()), true, b.time(expires));
    String phone =
        u.phoneIdentityId() == null
            ? null
            : b.need(PhoneIdentityEntity.class, loginPhoneId(u), false).phone();
    var restrictions =
        u.phoneIdentityId() == null
            ? List.<Views.RestrictionView>of()
            : b
                .list(
                    RestrictionEntity.class,
                    and(
                        in(
                            "phoneIdentityId",
                            b.governancePhoneIds(
                                u.phoneIdentityId(),
                                u.role().equals("ORGANIZER") ? "PUBLISH" : "REGISTER")),
                        eq("kind", u.role().equals("ORGANIZER") ? "PUBLISH" : "REGISTER"),
                        le("startsAtMs", b.now()),
                        gt("endsAtMs", b.now())))
                .stream()
                .map(this::restriction)
                .toList();
    UUID assigned =
        b.db()
            .one(
                CheckerAssignmentEntity.class,
                and(eq("checkerId", u.id()), nil("revokedAtMs")),
                false)
            .map(CheckerAssignmentEntity::sessionId)
            .orElse(null);
    return new Views.UserView(
        u.id(),
        u.username(),
        Role.valueOf(u.role()),
        u.email(),
        mask(phone),
        u.displayName(),
        false,
        b.time(expires),
        restrictions,
        assigned);
  }

  public static String mask(String phone) {
    return phone == null ? null : phone.substring(0, 3) + "****" + phone.substring(7);
  }

  public Views.RestrictionView restriction(RestrictionEntity r) {
    return new Views.RestrictionView(
        r.id(),
        RestrictionKind.valueOf(r.kind()),
        b.time(r.startsAtMs()),
        b.time(r.endsAtMs()),
        r.reason(),
        r.source(),
        b.now() >= r.startsAtMs() && b.now() < r.endsAtMs() && r.revokedAtMs() == null);
  }

  public UseCaseResult<Views.Identity> currentUser(RequestContext c) {
    return b.read(() -> ok(identity(b.actor(c), c.actor().expiresAt().toEpochMilli())));
  }

  public UseCaseResult<Mutation<Views.UserView>> updateProfile(
      RequestContext c, Requests.Profile r) {
    return b.write(
        () -> {
          b.actor(c, Role.PARTICIPANT, Role.ORGANIZER);
          var result =
              b.change(
                  c,
                  "ME-02",
                  c.actor().userId(),
                  r,
                  "ACCOUNT",
                  i ->
                      (Views.UserView)
                          identity(
                              b.need(UserEntity.class, i, false),
                              c.actor().expiresAt().toEpochMilli()),
                  () -> {
                    var u = b.need(UserEntity.class, c.actor().userId(), true);
                    b.db()
                        .save(
                            copy(
                                u,
                                "displayName",
                                trim(r.displayName()),
                                "updatedAtMs",
                                b.now(),
                                "version",
                                u.version() + 1));
                    return u.id();
                  });
          return ok(result.mutation());
        });
  }

  public UseCaseResult<Views.ChangedPassword> changePassword(
      RequestContext c, Requests.Password r) {
    return b.write(
        () -> {
          var u = b.actor(c);
          u = b.need(UserEntity.class, u.id(), true);
          require(passwords.matches(r.oldPassword(), u.passwordHash()), "STATE_CONFLICT", "原密码不正确");
          b.change(
              c,
              "ME-03",
              u.id(),
              r,
              "ACCOUNT",
              i -> i,
              () -> {
                var current = b.need(UserEntity.class, c.actor().userId(), true);
                b.db()
                    .save(
                        copy(
                            current,
                            "passwordHash",
                            passwords.encode(r.newPassword()),
                            "mustChangePassword",
                            false,
                            "updatedAtMs",
                            b.now(),
                            "version",
                            current.version() + 1));
                revoke(current.id(), "PASSWORD_CHANGED");
                return current.id();
              });
          return new UseCaseResult<>(200, new Views.ChangedPassword(true, true), clearedCookies());
        });
  }

  public Views.ClosureEligibility eligibility(UserEntity u) {
    var p = b.need(PhoneIdentityEntity.class, u.phoneIdentityId(), false);
    List<Views.ClosureBlocker> blockers = new ArrayList<>();
    for (var o :
        b.list(
            OrderEntity.class,
            and(
                eq("userId", u.id()),
                in(
                    "status",
                    List.of("WAITLISTED", "OFFERED", "UNPAID", "REGISTERED", "CHECKED_IN")))))
      blockers.add(new Views.ClosureBlocker("OPEN_ORDER", "尚有未结订单", "ORDER", o.id()));
    for (var r :
        b.list(
            RefundRequestEntity.class,
            and(eq("acceptedBy", u.id()), in("status", List.of("ACCEPTED", "FAILED")))))
      blockers.add(new Views.ClosureBlocker("OPEN_REFUND", "尚有未关闭退款依据", "REFUND", r.id()));
    if (u.role().equals("ORGANIZER"))
      for (var e :
          b.list(
              EventEntity.class,
              and(eq("organizerId", u.id()), in("status", List.of("PENDING_REVIEW", "PUBLISHED")))))
        if (e.status().equals("PENDING_REVIEW") || b.effectiveEnd(e) == null)
          blockers.add(new Views.ClosureBlocker("OPEN_EVENT", "尚有待审/未结束活动", "EVENT", e.id()));
    Long next = p.lastCancelledAtMs() == null ? null : p.lastCancelledAtMs() + 365 * DAY;
    if (next != null && b.now() < next)
      blockers.add(
          new Views.ClosureBlocker("CLOSURE_INTERVAL", "距离上次注销未满365天", "PHONE_IDENTITY", p.id()));
    return new Views.ClosureEligibility(
        blockers.isEmpty(),
        blockers,
        p.cancellationCount(),
        b.time(p.lastCancelledAtMs()),
        b.time(next),
        p.cancellationCount() >= 2);
  }

  public UseCaseResult<Views.ClosureEligibility> closureEligibility(RequestContext c) {
    return b.read(() -> ok(eligibility(b.actor(c, Role.PARTICIPANT, Role.ORGANIZER))));
  }

  public UseCaseResult<Views.ClosedAccount> closeAccount(RequestContext c, Requests.Closure r) {
    return b.write(
        () -> {
          var u = b.actor(c, Role.PARTICIPANT, Role.ORGANIZER);
          b.need(UserEntity.class, u.id(), true);
          var p = b.need(PhoneIdentityEntity.class, u.phoneIdentityId(), true);
          require(passwords.matches(r.password(), u.passwordHash()), "STATE_CONFLICT", "密码不正确");
          require("CLOSE".equals(r.confirmation()), "STATE_CONFLICT", "注销确认不正确");
          require(eligibility(u).eligible(), "ACCOUNT_CLOSE_BLOCKED", "仍有未结业务或注销间隔未到");
          b.change(
              c,
              "ME-05",
              u.id(),
              r,
              "ACCOUNT",
              i -> i,
              () -> {
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
                int count = p.cancellationCount() + 1;
                b.db()
                    .save(
                        copy(
                            p,
                            "cancellationCount",
                            count,
                            "lastCancelledAtMs",
                            b.now(),
                            "blacklistedAtMs",
                            count >= 3 ? b.now() : null,
                            "version",
                            p.version() + 1));
                revoke(u.id(), "ACCOUNT_CLOSED");
                for (var recovery :
                    b.list(
                        RecoveryEntity.class,
                        and(
                            eq("targetAccountId", u.id()),
                            in("status", List.of("PENDING", "VERIFIED")))))
                  b.db()
                      .save(
                          copy(
                              recovery,
                              "status",
                              "REJECTED",
                              "verificationNote",
                              "原账号已注销",
                              "resolvedAtMs",
                              b.now()));
                return u.id();
              });
          return new UseCaseResult<>(200, new Views.ClosedAccount(true, true), clearedCookies());
        });
  }
}
