package com.campus.ticketing.service.core;

import static com.campus.ticketing.service.core.BusinessSupport.*;

import com.campus.ticketing.dto.requests.Requests;
import java.math.*;
import java.time.*;
import java.util.*;

/** Pure, deterministic rules shared by HTTP use cases and durable workers. */
public final class DomainRules {
  private DomainRules() {}

  public static boolean overlaps(long a, long b, long c, long d) {
    return a < d && c < b;
  }

  public static long payDeadline(long accepted, long close) {
    return Math.min(Math.addExact(accepted, 15 * 60_000L), close);
  }

  public static long promotionDeadline(long offered, long close) {
    return Math.min(Math.addExact(offered, 12 * 3_600_000L), close);
  }

  public static boolean checkinWindow(long now, long start, long end) {
    return now >= checkinOpen(start) && now < checkinClose(start, end);
  }

  /** 验票在活动开始前 2 小时开放。 */
  public static long checkinOpen(long start) {
    return Math.subtractExact(start, 2 * 3_600_000L);
  }

  /** 验票在活动进行到一半时结束（结束时刻不含）。 */
  public static long checkinClose(long start, long end) {
    return start + Math.max(0, end - start) / 2;
  }

  public static double ratio(long numerator, long denominator) {
    return denominator == 0
        ? 0
        : BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
            .doubleValue();
  }

  public static void draft(Requests.Draft draft, boolean complete, long now) {
    valid(draft.sessions().size() <= 20, "最多20场");
    Set<UUID> ids = new HashSet<>();
    Set<String> names = new HashSet<>();
    if (complete) {
      valid(
          draft.name() != null
              && (draft.categoryId() != null
                  || (draft.proposedCategoryName() != null
                      && !trim(draft.proposedCategoryName()).isEmpty()))
              && !draft.sessions().isEmpty(),
          "活动信息未完整填写");
    }
    Long earliest = null;
    for (var s : draft.sessions()) {
      valid(ids.add(s.id()), "重复场次ID");
      if (s.name() != null) valid(names.add(trim(s.name())), "同活动场次名重复");
      if (s.startsAt() != null && s.endsAt() != null)
        valid(s.startsAt().isBefore(s.endsAt()), "开始必须早于结束");
      if (s.salesOpensAt() != null && s.registrationClosesAt() != null)
        valid(s.salesOpensAt().isBefore(s.registrationClosesAt()), "放票必须早于报名截止");
      if (s.registrationClosesAt() != null && s.startsAt() != null)
        valid(!s.registrationClosesAt().isAfter(s.startsAt()), "报名截止不能晚于开始");
      Set<String> typeNames = new HashSet<>();
      long sum = 0;
      for (var t : s.ticketTypes()) {
        valid(ids.add(t.id()), "子对象ID重复");
        if (t.name() != null) valid(typeNames.add(trim(t.name())), "同场票种名重复");
        if (t.capacity() != null) sum += t.capacity();
        if (complete)
          valid(t.name() != null && t.capacity() != null && t.priceJiao() != null, "票种信息未完整填写");
      }
      if (complete) {
        valid(
            s.name() != null
                && s.startsAt() != null
                && s.endsAt() != null
                && s.location() != null
                && s.capacity() != null
                && s.salesOpensAt() != null
                && s.registrationClosesAt() != null
                && !s.ticketTypes().isEmpty(),
            "场次信息未完整填写");
        valid(sum <= s.capacity(), "票种容量总和超过场次容量");
        require(now <= millis(s.salesOpensAt()), "STATE_CONFLICT", "放票时刻已过，不能提交/发布");
        earliest =
            earliest == null ? millis(s.startsAt()) : Math.min(earliest, millis(s.startsAt()));
      }
    }
    if (complete) valid(earliest > now && earliest <= now + 7 * DAY, "首场必须在未来7天内开始");
  }

  public static String csv(String value) {
    String s = value == null ? "" : value;
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}
