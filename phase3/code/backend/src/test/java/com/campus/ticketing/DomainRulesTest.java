package com.campus.ticketing;

import static org.assertj.core.api.Assertions.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.service.core.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DomainRulesTest {
  @Test
  void intervalsAreHalfOpenAndDeadlinesAreTruncated() {
    assertThat(DomainRules.overlaps(10, 20, 20, 30)).isFalse();
    assertThat(DomainRules.overlaps(10, 21, 20, 30)).isTrue();
    assertThat(DomainRules.payDeadline(1000, 2000)).isEqualTo(2000);
    assertThat(DomainRules.promotionDeadline(1000, 2000)).isEqualTo(2000);
    assertThat(DomainRules.checkinWindow(700, 1000, 2000)).isTrue();
    assertThat(DomainRules.checkinWindow(2000, 1000, 2000)).isFalse();
  }

  @Test
  void zeroDenominatorsRoundingAndCsvAreDeterministic() {
    assertThat(DomainRules.ratio(0, 0)).isZero();
    assertThat(DomainRules.ratio(1, 3)).isEqualTo(0.333333);
    assertThat(DomainRules.csv("=SUM(A1)")).isEqualTo("\"'=SUM(A1)\"");
    assertThat(DomainRules.csv("x,\"y\"\nz")).isEqualTo("\"x,\"\"y\"\"\nz\"");
  }

  @Test
  void asciiNormalizationDoesNotFoldNonAsciiText() {
    assertThat(BusinessSupport.normalize(" AbCÄ ")).isEqualTo("abcÄ");
  }

  @Test
  void hmacSeparatesEnvironmentIndependentDimensionsWithoutRawPhone() {
    byte[] secret = "a".repeat(32).getBytes();
    String a = RateLimitService.fingerprint(secret, "login", "account", "13800000000");
    assertThat(a).matches("[0-9a-f]{64}").doesNotContain("13800000000");
    assertThat(a)
        .isNotEqualTo(RateLimitService.fingerprint(secret, "login", "client", "13800000000"));
  }

  @Test
  void draftAllowsMissingIntroductionAndRejectsDuplicateIdsAndBadSequence() {
    var now = OffsetDateTime.parse("2026-11-05T10:00:00+08:00");
    UUID id = UUID.randomUUID();
    var type = new Requests.TicketTypeDraft(UUID.randomUUID(), "票种", 1, 0L, false);
    var session =
        new Requests.SessionDraft(
            id,
            "场次",
            now.plusHours(8),
            now.plusHours(10),
            "教室",
            1,
            now.plusHours(1),
            now.plusHours(7),
            List.of(type));
    var valid = new Requests.Draft(0L, "名称", UUID.randomUUID(), null, List.of(session));
    assertThatCode(() -> DomainRules.draft(valid, true, now.toInstant().toEpochMilli()))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                DomainRules.draft(
                    new Requests.Draft(
                        0L, "名称", UUID.randomUUID(), null, List.of(session, session)),
                    false,
                    now.toInstant().toEpochMilli()))
        .isInstanceOf(ApiException.class);
  }
}
