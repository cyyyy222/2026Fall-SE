package com.campus.ticketing.service.core;

import static com.campus.ticketing.common.EntityRecords.*;
import static com.campus.ticketing.dao.RecordStore.Filter.*;
import static com.campus.ticketing.service.core.BusinessSupport.*;

import com.campus.ticketing.entity.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Explicit local demo import. Never runs merely because a database is connected. */
@Service
public class DemoDatasetService {
  public static final String USER_PASSWORD = "DemoPass123";
  public static final String ADMIN_PASSWORD = "AdminDemo123";
  private final BusinessSupport b;
  private final IdentityUseCases identity;
  private final DurableWorkflows workflows;

  public DemoDatasetService(
      BusinessSupport b, IdentityUseCases identity, DurableWorkflows workflows) {
    this.b = b;
    this.identity = identity;
    this.workflows = workflows;
  }

  public static UUID key(String name) {
    return UUID.nameUUIDFromBytes(
        ("campus-ticketing:demo:v1:" + name).getBytes(StandardCharsets.UTF_8));
  }

  public record Summary(boolean inserted, long users, long events, long orders, long categories) {}

  private long now;
  private OffsetDateTime today;

  private long at(int days, int hour) {
    return today.plusDays(days).withHour(hour).toInstant().toEpochMilli();
  }

  private long before(long value, long minutes) {
    return value - minutes * 60_000;
  }

  public synchronized Summary seed() {
    return b.write(
        () -> {
          // A stable marker and all fixture rows are committed in the same transaction.
          var marker = b.db().get(UserEntity.class, key("user:admin_demo"), true).orElse(null);
          if (marker != null) {
            valid(
                marker.username().equals("admin_demo") && marker.role().equals("ADMIN"),
                "演示数据编号已被其他数据占用");
            return summary(false);
          }
          now = b.now();
          today = b.time(now).toLocalDate().atStartOfDay().atOffset(ZoneOffset.ofHours(8));
          var users = new LinkedHashMap<String, String>();
          users.put("admin_demo", "ADMIN");
          users.put("organizer_demo", "ORGANIZER");
          for (String name :
              List.of(
                  "participant_demo",
                  "participant_ticket_demo",
                  "participant_wait_demo",
                  "participant_pending_demo",
                  "participant_history_demo",
                  "participant_test_demo")) users.put(name, "PARTICIPANT");
          for (String suffix :
              List.of(
                  "", "_workshop", "_free", "_full", "_live", "_review", "_history", "_cancelled"))
            users.put("checker" + suffix + "_demo", "CHECKER");
          int phone = 1;
          for (var user : users.entrySet()) {
            valid(
                b.db()
                        .count(
                            UserEntity.class,
                            and(eq("usernameNormalized", user.getKey()), nil("deletedAtMs")))
                    == 0,
                "已有同名账号，未覆盖：" + user.getKey());
            UUID phoneId = null;
            String email = null;
            if (List.of("PARTICIPANT", "ORGANIZER").contains(user.getValue())) {
              String number = String.format(Locale.ROOT, "13900001%03d", phone++);
              valid(
                  b.db().count(PhoneIdentityEntity.class, eq("phone", number)) == 0,
                  "演示手机号已被占用，未覆盖");
              phoneId = key("phone:" + user.getKey());
              email = user.getKey() + "@example.test";
              b.db()
                  .insert(
                      new PhoneIdentityEntity(phoneId, number, 0, null, null, now - 30 * DAY, 0L));
            }
            boolean historical =
                user.getKey().equals("checker_history_demo")
                    || user.getKey().equals("checker_cancelled_demo");
            b.db()
                .insert(
                    new UserEntity(
                        key("user:" + user.getKey()),
                        user.getKey(),
                        user.getKey(),
                        email,
                        email,
                        phoneId,
                        identity
                            .passwordEncoder()
                            .encode(
                                user.getValue().equals("ADMIN") ? ADMIN_PASSWORD : USER_PASSWORD),
                        user.getValue(),
                        display(user.getKey()),
                        !historical,
                        user.getValue().equals("ADMIN"),
                        historical
                            ? (user.getKey().equals("checker_cancelled_demo")
                                ? at(-1, 12)
                                : at(-1, 17))
                            : null,
                        historical ? "DEMO_HISTORICAL_EVENT_ENDED" : null,
                        now - 30 * DAY,
                        now,
                        0L));
          }
          category("music", "音乐与演出", true);
          category("craft", "创意与手作", true);
          category("sport", "运动与公益", true);
          category("talk", "讲座与分享", true);
          category("archive", "历史类别", false);

          var concert =
              event(
                  "concert",
                  "秋日校园音乐会",
                  "music",
                  "PUBLISHED",
                  at(2, 19),
                  at(2, 21),
                  at(2, 18),
                  200,
                  "学生活动中心·音乐厅",
                  "在温柔的灯光与旋律中，度过一个属于校园的夜晚。");
          ticketType("concert:standard", concert, 120, 390);
          ticketType("concert:student", concert, 80, 190);
          assign("checker_demo", concert, null);
          var workshop =
              event(
                  "workshop",
                  "手作与香气·周末工坊",
                  "craft",
                  "PUBLISHED",
                  at(3, 14),
                  at(3, 16),
                  at(3, 13),
                  20,
                  "创客空间·203室",
                  "从选材到完成作品，体验一段安静专注的手作时光。");
          ticketType("workshop:standard", workshop, 12, 680);
          ticketType("workshop:student", workshop, 8, 480);
          assign("checker_workshop_demo", workshop, null);
          var run =
              event(
                  "run",
                  "校园公益跑·一起出发",
                  "sport",
                  "PUBLISHED",
                  at(4, 9),
                  at(4, 11),
                  at(4, 8),
                  80,
                  "学校东操场",
                  "免费报名，完成支付确认后生成电子票。请提前做好热身。");
          ticketType("run:free", run, 80, 0);
          assign("checker_free_demo", run, null);
          var full =
              event(
                  "full",
                  "小剧场·满座与候补",
                  "music",
                  "PUBLISHED",
                  at(1, 19),
                  at(1, 21),
                  at(1, 18),
                  1,
                  "艺术楼·黑匣子剧场",
                  "仅有一个演示名额，已售出；可体验候补与递补流程。");
          ticketType("full:standard", full, 1, 250);
          assign("checker_full_demo", full, null);
          long liveStart =
              b.time(now).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
          var live =
              event(
                  "live",
                  "城市影像分享会·正在进行",
                  "talk",
                  "PUBLISHED",
                  liveStart,
                  liveStart + 2 * 3_600_000,
                  liveStart - 60_000,
                  30,
                  "图书馆·影像放映室",
                  "本场已有演示电子票，可用检票账号体验核对与确认签到。");
          ticketType("live:standard", live, 30, 0);
          assign("checker_live_demo", live, null);
          var review =
              event(
                  "review",
                  "校园策展人·主题交流会",
                  "talk",
                  "PENDING_REVIEW",
                  at(5, 15),
                  at(5, 17),
                  at(5, 14),
                  40,
                  "创新中心·报告厅",
                  "资料完整并已绑定检票员，供管理员体验审核发布。");
          ticketType("review:standard", review, 40, 120);
          assign("checker_review_demo", review, null);
          b.db()
              .insert(
                  create(
                      EventEntity.class,
                      "id",
                      key("event:draft"),
                      "organizerId",
                      key("user:organizer_demo"),
                      "status",
                      "DRAFT",
                      "name",
                      "我的下一场活动·草稿",
                      "description",
                      "这是可以继续完善的未提交草稿。",
                      "settlementStatus",
                      "NOT_DUE",
                      "createdAtMs",
                      now - 3_600_000,
                      "updatedAtMs",
                      now,
                      "version",
                      0L));

          var ended =
              event(
                  "ended",
                  "读书与表达·往期分享",
                  "archive",
                  "ENDED",
                  at(-1, 15),
                  at(-1, 17),
                  at(-1, 14),
                  60,
                  "人文楼·201室",
                  "活动已自然结束，可查看历史电子票、签到和正式报表。");
          ticketType("ended:standard", ended, 60, 100);
          UUID pastAssignment = assign("checker_history_demo", ended, ended.endsAtMs());
          var cancelled =
              event(
                  "cancelled",
                  "纸上花园·已取消示例",
                  "craft",
                  "CANCELLED",
                  at(-1, 10),
                  at(-1, 12),
                  at(-1, 9),
                  20,
                  "创意工坊·102室",
                  "活动已取消，保留原订单、退款依据和结算结果供查看。");
          ticketType("cancelled:standard", cancelled, 20, 150);
          assign("checker_cancelled_demo", cancelled, at(-2, 12));

          order(
              "full:paid",
              "participant_ticket_demo",
              full,
              "full:standard",
              "REGISTERED",
              now - 3_600_000,
              null);
          order(
              "full:waiting",
              "participant_wait_demo",
              full,
              "full:standard",
              "WAITLISTED",
              now - 30 * 60_000,
              null);
          order(
              "workshop:pending",
              "participant_pending_demo",
              workshop,
              "workshop:standard",
              "UNPAID",
              now - 60_000,
              null);
          var liveOrder =
              order(
                  "live:ticket",
                  "participant_ticket_demo",
                  live,
                  "live:standard",
                  "REGISTERED",
                  before(live.registrationClosesAtMs(), 30),
                  null);
          var absent =
              order(
                  "ended:absent",
                  "participant_history_demo",
                  ended,
                  "ended:standard",
                  "ABSENT",
                  before(ended.registrationClosesAtMs(), 60),
                  null);
          order(
              "ended:checked",
              "participant_test_demo",
              ended,
              "ended:standard",
              "COMPLETED",
              before(ended.registrationClosesAtMs(), 45),
              pastAssignment);
          b.db()
              .insert(
                  new BehaviorFactEntity(
                      key("fact:absence"),
                      absent.phoneIdentityId(),
                      "NATURAL_ABSENCE",
                      absent.id(),
                      null,
                      ended.endsAtMs(),
                      "正常结束未签到（演示历史）"));
          cancelledRefund(cancelled);
          workflows.snapshot(b.need(EventEntity.class, ended.eventId(), false), ended, "NATURAL");
          workflows.snapshot(b.need(EventEntity.class, ended.eventId(), false), null, "NATURAL");

          UUID report = key("report:concert");
          b.db()
              .insert(
                  new ReportEntity(
                      report,
                      key("user:participant_wait_demo"),
                      concert.eventId(),
                      concert.id(),
                      "页面信息需要核实",
                      "演示用待处理举报：希望进一步明确入场须知。",
                      "仅为文本核查示例，不代表真实违规。",
                      "PENDING",
                      now - 10 * 60_000,
                      null,
                      null));
          UUID oldReport = key("report:ended"), investigation = key("investigation:ended");
          b.db()
              .insert(
                  new ReportEntity(
                      oldReport,
                      key("user:participant_history_demo"),
                      ended.eventId(),
                      ended.id(),
                      "活动结束时间咨询",
                      "演示已处理举报流程。",
                      "经人工核对，活动按计划结束。",
                      "UNSUBSTANTIATED",
                      ended.endsAtMs() + 60_000,
                      "已核实，未发现违规",
                      ended.endsAtMs() + 20 * 60_000));
          b.db()
              .insert(
                  new InvestigationEntity(
                      investigation,
                      ended.eventId(),
                      key("user:admin_demo"),
                      "demo:v1:ended-time",
                      "UNSUBSTANTIATED",
                      "已核实，未发现违规",
                      "演示核查记录；不生成组织者违规事实。",
                      ended.endsAtMs() + 20 * 60_000,
                      null));
          b.db()
              .insert(
                  new InvestigationReportEntity(
                      key("investigation-report:ended"), investigation, oldReport));
          for (String name : users.keySet())
            if (!name.contains("history") && !name.contains("cancelled"))
              b.notify(
                  key("user:" + name),
                  "DEMO_WELCOME:" + name,
                  "EVENT_CHANGED",
                  "欢迎使用真实接口演示",
                  "当前数据来自Java后端与MySQL；本地模拟支付不会实际扣款。",
                  "ACCOUNT",
                  key("user:" + name),
                  null);
          b.notify(
              key("user:participant_ticket_demo"),
              "DEMO_LIVE_TICKET",
              "PAYMENT_SUCCESS",
              "现场电子票已准备好",
              "可在我的订单中查看城市影像分享会电子票。",
              "ORDER",
              liveOrder.id(),
              null);
          b.audit(null, "DEMO_DATASET_IMPORTED", "DATASET", key("dataset"), "SUCCESS", null, null);
          return summary(true);
        });
  }

  private Summary summary(boolean inserted) {
    return new Summary(
        inserted,
        b.db().count(UserEntity.class, null),
        b.db().count(EventEntity.class, null),
        b.db().count(OrderEntity.class, null),
        b.db().count(CategoryEntity.class, null));
  }

  private String display(String name) {
    if (name.equals("organizer_demo")) return "校园活动工作室";
    if (name.startsWith("checker")) return "演示检票员";
    if (name.equals("admin_demo")) return "演示管理员";
    return "演示参与者";
  }

  private void category(String slug, String name, boolean enabled) {
    valid(
        b.db().count(CategoryEntity.class, eq("nameNormalized", name)) == 0, "已有同名类别，未覆盖：" + name);
    b.db()
        .insert(
            new CategoryEntity(
                key("category:" + slug), name, name, enabled, 0L, now - 30 * DAY, now));
  }

  private EventSessionEntity event(
      String slug,
      String name,
      String category,
      String status,
      long starts,
      long ends,
      long close,
      int capacity,
      String location,
      String description) {
    long published = Math.min(now - 2 * DAY, starts - 2 * DAY), created = published - DAY;
    Long terminal = null;
    if (status.equals("ENDED")) terminal = ends;
    else if (status.equals("CANCELLED")) terminal = at(-2, 12);
    var e =
        new EventEntity(
            key("event:" + slug),
            key("user:organizer_demo"),
            status,
            name,
            key("category:" + category),
            b.need(CategoryEntity.class, key("category:" + category), false).name(),
            description,
            null,
            status.equals("PENDING_REVIEW") ? now - 60 * 60_000 : published - 3_600_000,
            status.equals("PENDING_REVIEW") ? null : published,
            terminal,
            status.equals("CANCELLED") ? "因场地安排调整主动取消（演示）" : null,
            null,
            terminal == null ? "NOT_DUE" : "COMPLETED",
            created,
            now,
            1L);
    b.db().insert(e);
    var s =
        new EventSessionEntity(
            key("session:" + slug),
            e.id(),
            name + "·主场",
            starts,
            ends,
            location,
            capacity,
            status.equals("PENDING_REVIEW") ? starts - 6 * 3_600_000 : published + 60_000,
            close,
            terminal == null ? "NOT_DUE" : "COMPLETED",
            null,
            1L);
    b.db().insert(s);
    if (!status.equals("PENDING_REVIEW"))
      b.db()
          .insert(
              new EventReviewEntity(
                  key("review:" + slug),
                  e.id(),
                  key("user:admin_demo"),
                  0L,
                  "PUBLISHED",
                  null,
                  null,
                  published));
    return s;
  }

  private void ticketType(String slug, EventSessionEntity s, int capacity, long price) {
    b.db()
        .insert(
            new TicketTypeEntity(
                key("type:" + slug),
                s.id(),
                slug.endsWith("student") ? "学生票" : price == 0 ? "免费参与票" : "标准票",
                capacity,
                price,
                false,
                null,
                1L));
  }

  private UUID assign(String name, EventSessionEntity s, Long revoked) {
    UUID id = key("assignment:" + s.id());
    b.db()
        .insert(
            new CheckerAssignmentEntity(
                id,
                key("user:" + name),
                s.id(),
                key("user:admin_demo"),
                before(s.salesOpensAtMs(), 1),
                revoked,
                revoked == null ? null : "HISTORICAL_EVENT_TERMINATED"));
    return id;
  }

  private OrderEntity order(
      String slug,
      String username,
      EventSessionEntity s,
      String type,
      String state,
      long created,
      UUID checkedAssignment) {
    var u = b.need(UserEntity.class, key("user:" + username), false);
    var e = b.need(EventEntity.class, s.eventId(), false);
    var t = b.need(TicketTypeEntity.class, key("type:" + type), false);
    UUID id = key("order:" + slug),
        request = key("registration:" + slug),
        op = key("operation:" + slug);
    boolean waiting = state.equals("WAITLISTED"), unpaid = state.equals("UNPAID");
    long updated = state.equals("ABSENT") || state.equals("COMPLETED") ? s.endsAtMs() : created;
    var o =
        new OrderEntity(
            id,
            request,
            u.id(),
            u.phoneIdentityId(),
            e.id(),
            s.id(),
            t.id(),
            state,
            state.equals("ABSENT") ? "正常结束未签到" : null,
            u.displayName(),
            e.name(),
            s.name(),
            e.categoryNameSnapshot(),
            t.name(),
            t.priceJiao(),
            created,
            waiting ? created : null,
            null,
            null,
            unpaid ? DomainRules.payDeadline(created, s.registrationClosesAtMs()) : null,
            0,
            updated,
            0L);
    b.db().insert(o);
    var input =
        new com.campus.ticketing.dto.requests.Requests.Registration(
            request, s.id(), t.id(), u.displayName());
    b.db()
        .insert(
            new RegistrationRequestEntity(
                key("registration-map:" + slug),
                u.id(),
                request,
                b.fingerprint(input),
                o.id(),
                op,
                "CREATED",
                created));
    if (!waiting && !unpaid) {
      b.db()
          .insert(
              new PaymentAttemptEntity(
                  key("payment:" + slug),
                  o.id(),
                  key("payment-attempt:" + slug),
                  op,
                  "SUCCESS",
                  o.amountJiao(),
                  null,
                  created + 1000));
      String ticketState =
          state.equals("COMPLETED") ? "CHECKED" : state.equals("ABSENT") ? "INVALID" : "VALID";
      b.db()
          .insert(
              new TicketEntity(
                  key("ticket:" + slug),
                  o.id(),
                  ticketState,
                  created + 1000,
                  state.equals("ABSENT") ? s.endsAtMs() : null,
                  state.equals("ABSENT") ? "正常结束未签到" : null));
      if (checkedAssignment != null)
        b.db()
            .insert(
                new CheckInEntity(
                    key("checkin:" + slug),
                    key("ticket:" + slug),
                    o.id(),
                    key("user:checker_history_demo"),
                    s.id(),
                    checkedAssignment,
                    s.startsAtMs() + 10 * 60_000));
    }
    return o;
  }

  private void cancelledRefund(EventSessionEntity s) {
    long effective = at(-2, 12);
    var o =
        order(
            "cancelled:refunded",
            "participant_history_demo",
            s,
            "cancelled:standard",
            "REGISTERED",
            effective - 3_600_000,
            null);
    UUID jobId = key("job:cancelled"),
        factId = key("termination:cancelled"),
        grantId = key("refund:cancelled"),
        attemptId = key("refund-attempt:cancelled");
    b.db()
        .insert(
            new JobEntity(
                jobId,
                "JOB-04",
                "TERMINATE:" + s.eventId(),
                s.eventId(),
                "SUCCEEDED",
                effective,
                effective + 300_000,
                effective + 2000,
                4,
                4,
                0,
                effective,
                null,
                null,
                null,
                effective));
    b.db()
        .insert(
            new TerminationFactEntity(
                factId,
                s.eventId(),
                jobId,
                o.id(),
                effective,
                "REGISTERED",
                s.endsAtMs(),
                "REFUND",
                "场地安排调整"));
    b.db()
        .insert(
            new JobItemEntity(
                key("job-item:cancelled:order"),
                jobId,
                "ORDER:" + o.id(),
                "ORDER_SETTLEMENT",
                o.id(),
                null,
                "SUCCEEDED",
                "{}",
                0,
                effective,
                effective + 1000,
                null));
    for (UUID recipient :
        List.of(o.userId(), key("user:organizer_demo"), key("user:checker_cancelled_demo"))) {
      b.db()
          .insert(
              new JobItemEntity(
                  key("job-item:cancelled:notice:" + recipient),
                  jobId,
                  "NOTICE:" + recipient,
                  "RECIPIENT_NOTICE",
                  null,
                  recipient,
                  "SUCCEEDED",
                  "{}",
                  0,
                  effective,
                  effective + 2000,
                  null));
      b.db()
          .insert(
              new MessageEntity(
                  key("message:cancelled:" + recipient),
                  recipient,
                  "TERMINATE:" + s.eventId(),
                  "EVENT_TERMINATED",
                  "活动已取消",
                  "场地安排调整，已完成退款。",
                  "EVENT",
                  s.eventId(),
                  effective + 2000,
                  effective + 2000 + LONG_RETENTION,
                  null,
                  null));
    }
    b.db()
        .insert(
            new RefundRequestEntity(
                grantId,
                o.id(),
                key("payment:cancelled:refunded"),
                "PLATFORM_TERMINATION",
                "SUCCEEDED",
                attemptId,
                null,
                effective,
                "场地安排调整",
                s.registrationClosesAtMs(),
                "REGISTERED",
                b.encode(Map.of("orderId", o.id(), "effectiveAtMs", effective)),
                b.fingerprint(Map.of("terminationFact", factId)),
                null,
                key("operation:refund"),
                factId,
                null,
                null));
    b.db()
        .insert(
            new RefundAttemptEntity(
                key("refund-result:cancelled"),
                grantId,
                o.id(),
                key("payment:cancelled:refunded"),
                attemptId,
                key("operation:refund"),
                "SUCCESS",
                o.amountJiao(),
                null,
                effective + 1000));
    b.db()
        .save(
            copy(
                o,
                "status",
                "REFUNDED",
                "statusReason",
                "平台取消，全额退款",
                "updatedAtMs",
                effective + 1000,
                "version",
                1L));
    var ticket = b.need(TicketEntity.class, key("ticket:cancelled:refunded"), false);
    b.db()
        .save(
            copy(
                ticket,
                "status",
                "INVALID",
                "invalidatedAtMs",
                effective + 1000,
                "invalidReason",
                "平台取消已退款"));
    workflows.snapshot(b.need(EventEntity.class, s.eventId(), false), s, "CANCEL");
    workflows.snapshot(b.need(EventEntity.class, s.eventId(), false), null, "CANCEL");
  }
}
