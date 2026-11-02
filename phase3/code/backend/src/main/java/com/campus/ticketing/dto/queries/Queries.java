package com.campus.ticketing.dto.queries;

import com.campus.ticketing.common.*;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.validation.*;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class Queries {
  private Queries() {}

  public record None() {}

  public record Categories(CategoryScope scope) {}

  public record PublicSessions(
      String eventName,
      UUID categoryId,
      @BeijingTime OffsetDateTime startsFrom,
      @BeijingTime OffsetDateTime startsTo,
      String location,
      String ticketTypeName,
      @Min(0) @Max(9999990) Long minPriceJiao,
      @Min(0) @Max(9999990) Long maxPriceJiao,
      SalesStatus salesStatus,
      SortBy sortBy,
      SortDirection sortDirection,
      @Min(1) Integer page,
      @Min(1) @Max(100) Integer pageSize) {}

  public record Orders(
      OrderStatus status, UUID eventId, @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {}

  public record Events(
      EventStatus status, @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {}

  public record Version(@NotNull @Min(0) Long expectedVersion) {}

  public record Attendees(
      UUID sessionId,
      UUID ticketTypeId,
      OrderStatus orderStatus,
      CheckinStatus checkinStatus,
      @Min(1) Integer page,
      @Min(1) @Max(100) Integer pageSize) {}

  public record Session(UUID sessionId) {}

  public record Reports(
      ReportStatus status, @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {}

  public record Messages(
      Boolean unreadOnly, @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {}

  public record Accounts(
      Role role,
      String username,
      UUID eventId,
      UUID sessionId,
      Boolean enabled,
      @Min(1) Integer page,
      @Min(1) @Max(100) Integer pageSize) {}

  public record Recoveries(
      RecoveryStatus status, @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {}

  public record Reviews(
      Boolean overdueOnly, @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {}

  public record CategoryAdmin(
      Boolean enabled, @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {}

  public record Payments(
      UUID orderId,
      AttemptResult result,
      @BeijingTime OffsetDateTime from,
      @BeijingTime OffsetDateTime to,
      @Min(1) Integer page,
      @Min(1) @Max(100) Integer pageSize) {}

  public record AdminReports(
      UUID eventId,
      ReportStatus status,
      Boolean needsAttention,
      @Min(1) Integer page,
      @Min(1) @Max(100) Integer pageSize) {}

  public record Candidates(
      RestrictionKind kind, @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {}

  public record Candidate(@NotNull RestrictionKind kind) {}

  public record Audit(
      @BeijingTime OffsetDateTime from,
      @BeijingTime OffsetDateTime to,
      String action,
      String objectType,
      UUID objectId,
      AuditResult result,
      @Min(1) Integer page,
      @Min(1) @Max(100) Integer pageSize) {}

  public record Jobs(
      UUID eventId,
      @Pattern(regexp = "JOB-0[1-7]") String type,
      JobStatus status,
      Boolean overdue,
      @Min(1) Integer page,
      @Min(1) @Max(100) Integer pageSize) {}

  public record AdminEvents(
      EventStatus status,
      String name,
      UUID organizerId,
      @Min(1) Integer page,
      @Min(1) @Max(100) Integer pageSize) {}

  public record Investigations(
      UUID eventId,
      Conclusion conclusion,
      @Min(1) Integer page,
      @Min(1) @Max(100) Integer pageSize) {}

  public record Refunds(
      RefundRequestStatus status,
      RefundSource source,
      UUID orderId,
      @Min(1) Integer page,
      @Min(1) @Max(100) Integer pageSize) {}
}
