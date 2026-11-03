package com.campus.ticketing.service.core;

import static com.campus.ticketing.common.EntityRecords.*;
import static com.campus.ticketing.dao.RecordStore.Filter.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dao.RecordStore;
import com.campus.ticketing.entity.*;
import com.campus.ticketing.enums.*;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class BusinessSupport {
  public static final long DAY = 86_400_000L,
      SESSION_TTL = 7 * DAY,
      SHORT_RETENTION = 7 * DAY,
      LONG_RETENTION = 180 * DAY;
  private final ObjectProvider<RecordStore> stores;
  private final ObjectProvider<PlatformTransactionManager> managers;
  public final Clock clock;
  public final ObjectMapper json;

  public BusinessSupport(
      ObjectProvider<RecordStore> stores,
      ObjectProvider<PlatformTransactionManager> managers,
      Clock clock,
      ObjectMapper json) {
    this.stores = stores;
    this.managers = managers;
    this.clock = clock;
    this.json = json;
  }

  public RecordStore db() {
    var d = stores.getIfAvailable();
    if (d == null) throw error(503, "DATABASE_UNAVAILABLE", "当前未连接MySQL；业务代码已就绪，不能执行持久化操作");
    return d;
  }

  public long now() {
    return clock.millis();
  }

  public OffsetDateTime time(Long ms) {
    return ms == null
        ? null
        : OffsetDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.ofHours(8));
  }

  public static Long millis(OffsetDateTime t) {
    return t == null ? null : t.toInstant().toEpochMilli();
  }

  public static UUID id() {
    return UUID.randomUUID();
  }

  public static String trim(String s) {
    return s == null ? null : s.strip();
  }

  public static String normalize(String s) {
    if (s == null) return null;
    var b = new StringBuilder(s.strip());
    for (int i = 0; i < b.length(); i++) {
      char c = b.charAt(i);
      if (c >= 'A' && c <= 'Z') b.setCharAt(i, (char) (c + 32));
    }
    return b.toString();
  }

  public static boolean contains(String value, String query) {
    return query == null || normalize(value == null ? "" : value).contains(normalize(query));
  }

  public static ApiException error(int status, String code, String message) {
    return new ApiException(status, code, message);
  }

  public static void require(boolean condition, String code, String message) {
    if (!condition) throw error(409, code, message);
  }

  public static void valid(boolean condition, String message) {
    if (!condition) throw error(422, "VALIDATION_FAILED", message);
  }

  public <T> T need(Class<T> type, UUID id, boolean lock) {
    return db().get(type, id, lock).orElseThrow(() -> error(404, "RESOURCE_NOT_FOUND", "对象不存在"));
  }

  public <T> List<T> list(Class<T> type, RecordStore.Filter filter) {
    return db().select(
            type, filter, List.of(RecordStore.Sort.asc("id")), Integer.MAX_VALUE, 0, false);
  }

  public <T> List<T> ordered(Class<T> type, RecordStore.Filter filter, RecordStore.Sort... sort) {
    return db().select(type, filter, List.of(sort), Integer.MAX_VALUE, 0, false);
  }

  public <T> T write(Supplier<T> work) {
    db();
    var manager = managers.getIfAvailable();
    if (manager == null) throw error(503, "DATABASE_UNAVAILABLE", "事务管理器不可用");
    var tx = new TransactionTemplate(manager);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    return tx.execute(s -> work.get());
  }

  public <T> T read(Supplier<T> work) {
    db();
    var manager = managers.getIfAvailable();
    if (manager == null) throw error(503, "DATABASE_UNAVAILABLE", "事务管理器不可用");
    var tx = new TransactionTemplate(manager);
    tx.setReadOnly(true);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return tx.execute(s -> work.get());
  }

  public UserEntity actor(RequestContext context, Role... roles) {
    if (context == null || context.actor() == null) throw error(401, "AUTH_REQUIRED", "请先登录");
    UserEntity u = need(UserEntity.class, context.actor().userId(), false);
    if (u.deletedAtMs() != null || !Boolean.TRUE.equals(u.enabled()))
      throw error(403, "ACCOUNT_UNAVAILABLE", "账号不可用");
    if (context.actor().expiresAt() != null
        && !clock.instant().isBefore(context.actor().expiresAt()))
      throw error(401, "SESSION_EXPIRED", "会话已到期");
    if (context.actor().sessionId() != null) {
      var session = need(LoginSessionEntity.class, context.actor().sessionId(), false);
      if (!session.userId().equals(u.id()) || session.revokedAtMs() != null)
        throw error(401, "SESSION_REVOKED", "会话已撤销");
      if (now() >= session.expiresAtMs()) throw error(401, "SESSION_EXPIRED", "会话已到期");
    }
    if (roles.length > 0 && Arrays.stream(roles).noneMatch(r -> r.name().equals(u.role())))
      throw error(403, "FORBIDDEN", "无权执行此操作");
    return u;
  }

  public EventEntity eventAccess(
      RequestContext c, UUID eventId, boolean lock, boolean checkerAllowed) {
    UserEntity u = actor(c);
    EventEntity e = need(EventEntity.class, eventId, lock);
    if (e.status().equals("DRAFT")
        && need(UserEntity.class, e.organizerId(), false).deletedAtMs() != null)
      throw error(404, "RESOURCE_NOT_FOUND", "组织者注销后草稿已逻辑删除");
    if (u.role().equals("ADMIN") || u.role().equals("ORGANIZER") && u.id().equals(e.organizerId()))
      return e;
    if (checkerAllowed && u.role().equals("CHECKER")) {
      var a =
          db().one(
                  CheckerAssignmentEntity.class,
                  and(eq("checkerId", u.id()), nil("revokedAtMs")),
                  false);
      if (a.isPresent()
          && need(EventSessionEntity.class, a.get().sessionId(), false).eventId().equals(eventId))
        return e;
    }
    throw error(403, "OBJECT_FORBIDDEN", "无权访问此活动");
  }

  public List<EventSessionEntity> sessions(UUID eventId) {
    return ordered(
        EventSessionEntity.class,
        and(eq("eventId", eventId), nil("deletedAtMs")),
        RecordStore.Sort.asc("startsAtMs"),
        RecordStore.Sort.asc("id"));
  }

  public List<TicketTypeEntity> types(UUID sessionId) {
    return list(TicketTypeEntity.class, and(eq("sessionId", sessionId), nil("deletedAtMs")));
  }

  public Long effectiveEnd(EventEntity e) {
    if (e.terminatedAtMs() != null) return e.terminatedAtMs();
    if (!e.status().equals("PUBLISHED") && !e.status().equals("ENDED")) return null;
    var times =
        sessions(e.id()).stream()
            .map(EventSessionEntity::endsAtMs)
            .filter(Objects::nonNull)
            .toList();
    if (times.isEmpty()) return null;
    long end = Collections.max(times);
    return now() >= end ? end : null;
  }

  public void retention(EventEntity e) {
    Long end = effectiveEnd(e);
    if (end != null && now() >= end + SHORT_RETENTION)
      throw error(410, "DATA_EXPIRED", "活动名单/统计/导出已过保留期");
  }

  public void publicEvent(EventEntity e) {
    if (!e.status().equals("PUBLISHED") || effectiveEnd(e) != null)
      throw error(
          409,
          "EVENT_NOT_PUBLIC",
          e.terminationReason() == null ? "活动当前不可公开访问" : e.terminationReason());
  }

  public boolean restricted(UUID identityId, String kind) {
    return identityId != null
        && db().count(
                    RestrictionEntity.class,
                    and(
                        in("phoneIdentityId", governancePhoneIds(identityId, kind)),
                        eq("kind", kind),
                        le("startsAtMs", now()),
                        gt("endsAtMs", now())))
            > 0;
  }

  public static UUID loginPhoneId(UserEntity u) {
    return u.loginPhoneIdentityId() == null ? u.phoneIdentityId() : u.loginPhoneIdentityId();
  }

  public static RecordStore.Filter currentPhoneFilter(UUID phoneId) {
    return or(
        eq("loginPhoneIdentityId", phoneId),
        and(nil("loginPhoneIdentityId"), eq("phoneIdentityId", phoneId)));
  }

  public Set<UUID> governancePhoneIds(UUID identityId, String kind) {
    Set<UUID> ids = new HashSet<>();
    ids.add(identityId);
    for (var u :
        list(
            UserEntity.class,
            and(
                eq("phoneIdentityId", identityId),
                eq("role", kind.equals("PUBLISH") ? "ORGANIZER" : "PARTICIPANT"),
                nil("deletedAtMs")))) {
      if (loginPhoneId(u) != null) ids.add(loginPhoneId(u));
    }
    return ids;
  }

  public void afterCommit(Runnable work) {
    if (org.springframework.transaction.support.TransactionSynchronizationManager
        .isSynchronizationActive())
      org.springframework.transaction.support.TransactionSynchronizationManager
          .registerSynchronization(
              new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                  work.run();
                }
              });
    else work.run();
  }

  public long occupied(UUID typeId) {
    return db().count(
            OrderEntity.class,
            and(
                eq("ticketTypeId", typeId),
                in(
                    "status",
                    List.of(
                        "OFFERED", "UNPAID", "REGISTERED", "CHECKED_IN", "COMPLETED", "ABSENT"))));
  }

  public void audit(
      RequestContext c,
      String action,
      String type,
      UUID objectId,
      String result,
      String reason,
      String job) {
    db().insert(
            new AuditLogEntity(
                id(),
                c == null || c.actor() == null ? null : c.actor().userId(),
                c == null || c.actor() == null ? "SYSTEM" : c.actor().role().name(),
                action,
                type,
                objectId,
                result,
                reason,
                job,
                c == null ? "system" : Objects.requireNonNullElse(c.traceId(), "system"),
                now()));
  }

  public void notify(
      UUID recipient,
      String key,
      String type,
      String title,
      String body,
      String resourceType,
      UUID resourceId,
      Long deadline) {
    if (db().one(MessageEntity.class, and(eq("recipientId", recipient), eq("eventKey", key)), false)
        .isPresent()) return;
    long createdAt = now();
    db().insert(
            new MessageEntity(
                id(),
                recipient,
                key,
                type,
                title,
                body,
                resourceType,
                resourceId,
                createdAt,
                createdAt + LONG_RETENTION,
                null,
                deadline));
  }

  public String fingerprint(Object value) {
    try {
      String digest = hash(json.writeValueAsBytes(value));
      return containsPassword(value)
          ? new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(digest)
          : digest;
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot fingerprint typed request", e);
    }
  }

  private boolean containsPassword(Object value) {
    return value instanceof com.campus.ticketing.dto.requests.Requests.Password
        || value instanceof com.campus.ticketing.dto.requests.Requests.ChangePhone
        || value instanceof com.campus.ticketing.dto.requests.Requests.PhonePasswordReset
        || value instanceof com.campus.ticketing.dto.requests.Requests.ResolveRecovery
        || value instanceof com.campus.ticketing.dto.requests.Requests.CreateAccount
        || value instanceof com.campus.ticketing.dto.requests.Requests.ResetPassword
        || value instanceof com.campus.ticketing.dto.requests.Requests.Closure
        || value instanceof com.campus.ticketing.dto.requests.Requests.Login
        || value instanceof com.campus.ticketing.dto.requests.Requests.Register;
  }

  public boolean matchesFingerprint(String stored, Object request) {
    if (!containsPassword(request)) return stored.equals(fingerprint(request));
    try {
      return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
          .matches(hash(json.writeValueAsBytes(request)), stored);
    } catch (Exception invalid) {
      return false;
    }
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot encode internal metadata", e);
    }
  }

  public JobEntity enqueue(
      String type, String key, UUID eventId, long dueAt, long deadline, List<JobItemEntity> items) {
    var existing =
        db().one(JobEntity.class, and(eq("type", type), eq("dedupKey", key)), false).orElse(null);
    if (existing != null) return existing;
    JobEntity job =
        new JobEntity(
            id(),
            type,
            key,
            eventId,
            "PENDING",
            dueAt,
            deadline,
            null,
            items.size(),
            0,
            0,
            dueAt,
            null,
            null,
            null,
            now());
    db().insert(job);
    for (var item : items) db().insert(copy(item, "jobId", job.id()));
    return job;
  }

  public JobItemEntity item(
      String key, String kind, UUID order, UUID recipient, Object payload, long due) {
    return new JobItemEntity(
        id(), null, key, kind, order, recipient, "PENDING", encode(payload), 0, due, null, null);
  }

  public static String hash(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String hash(String value) {
    return hash(value.getBytes(StandardCharsets.UTF_8));
  }

  public record Change<T>(UUID operationId, boolean replayed, T current) {
    public Mutation<T> mutation() {
      return new Mutation<>(operationId, "APPLIED", replayed, current);
    }
  }

  public <T> Change<T> change(
      RequestContext c,
      String api,
      UUID target,
      Object request,
      String resourceType,
      Function<UUID, T> project,
      Supplier<UUID> execute) {
    valid(c.idempotencyKey() != null, "缺少Idempotency-Key");
    actor(c);
    // Serialize keys for the same actor across resources, including empty collection creates.
    need(UserEntity.class, c.actor().userId(), true);
    String targetKey = target == null ? "ROOT" : target.toString(), fp = fingerprint(request);
    var existing =
        db().one(
                OperationRecordEntity.class,
                and(
                    eq("actorId", c.actor().userId()),
                    eq("apiId", api),
                    eq("targetKey", targetKey),
                    eq("idempotencyKey", c.idempotencyKey())),
                true);
    if (existing.isPresent()) {
      var op = existing.get();
      require(matchesFingerprint(op.fingerprint(), request), "IDEMPOTENCY_CONFLICT", "相同幂等键参数不一致");
      return new Change<>(op.id(), true, project.apply(op.resourceId()));
    }
    UUID resource = execute.get(), operation = id();
    db().insert(
            new OperationRecordEntity(
                operation,
                c.actor().userId(),
                api,
                targetKey,
                c.idempotencyKey(),
                fp,
                "COMPLETED",
                "APPLIED",
                resourceType,
                resource,
                "{}",
                now(),
                now() + LONG_RETENTION));
    audit(c, api, resourceType, resource, "SUCCESS", null, null);
    return new Change<>(operation, false, project.apply(resource));
  }

  public static <T> UseCaseResult<T> ok(T value) {
    return new UseCaseResult<>(200, value, List.of());
  }

  public static <T> UseCaseResult<T> created(T value, boolean replayed) {
    return new UseCaseResult<>(replayed ? 200 : 201, value, List.of());
  }

  public static <T> PageResult<T> page(List<T> rows, Integer page, Integer size) {
    int p = page == null ? 1 : page, s = size == null ? 20 : size;
    valid(p >= 1 && s >= 1 && s <= 100, "分页范围不合法");
    long from = (long) (p - 1) * s;
    int begin = (int) Math.min(rows.size(), from);
    return new PageResult<>(
        rows.subList(begin, (int) Math.min(rows.size(), from + s)), p, s, rows.size());
  }
}
