package com.campus.ticketing.dto.views;

import com.campus.ticketing.common.*;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.validation.*;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class Views {
  public sealed interface Identity permits UserView, ForcePasswordUser {}

  public sealed interface Counts permits LiveCounts, CheckerCounts {}

  public sealed interface StatisticsProjection permits Statistics, CheckerStatistics {}

  public sealed interface AuditRecord
      permits OrderAuditRecord,
          PaymentAuditRecord,
          RefundAuditRecord,
          TicketAuditRecord,
          CheckInAuditRecord {}

  public record OrderAuditRecord(
      UUID id, OrderStatus status, long amountJiao, OffsetDateTime createdAt)
      implements AuditRecord {}

  public record PaymentAuditRecord(
      UUID id, UUID orderId, AttemptResult result, long amountJiao, OffsetDateTime createdAt)
      implements AuditRecord {}

  public record RefundAuditRecord(
      UUID id,
      UUID orderId,
      UUID requestId,
      AttemptResult result,
      long amountJiao,
      OffsetDateTime createdAt)
      implements AuditRecord {}

  public record TicketAuditRecord(UUID id, UUID orderId, TicketStatus status)
      implements AuditRecord {}

  public record CheckInAuditRecord(
      UUID id,
      UUID ticketId,
      UUID orderId,
      UUID sessionId,
      UUID checkerId,
      OffsetDateTime checkedAt)
      implements AuditRecord {}

  private Views() {}

  public record Csrf(String token, String headerName) {}

  public record UserView(
      UUID id,
      String username,
      Role role,
      String email,
      String maskedPhone,
      String displayName,
      boolean mustChangePassword,
      OffsetDateTime sessionExpiresAt,
      List<RestrictionView> restrictions,
      UUID checkerSessionId)
      implements Identity {}

  public record ForcePasswordUser(
      UUID id,
      String username,
      Role role,
      boolean mustChangePassword,
      OffsetDateTime sessionExpiresAt)
      implements Identity {}

  public record RestrictionView(
      UUID id,
      RestrictionKind kind,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String reason) {}

  public record Registered(UUID id, String username, Role role) {}

  public record RecoveryReceipt(UUID requestId, String status, String message) {}

  public record RecoveryView(
      UUID id,
      String account,
      String phone,
      UUID targetAccountId,
      RecoveryStatus status,
      OffsetDateTime createdAt,
      String verificationNote,
      OffsetDateTime resolvedAt) {}

  public record ClosureBlocker(String code, String message, String objectType, UUID objectId) {}

  public record ClosureEligibility(
      boolean eligible,
      List<ClosureBlocker> blockers,
      int cancellationCount,
      OffsetDateTime lastCancelledAt,
      OffsetDateTime nextEligibleAt,
      boolean willBlacklist) {}

  public record ManagedAccount(
      UUID id,
      String username,
      Role role,
      boolean enabled,
      OffsetDateTime deletedAt,
      boolean mustChangePassword,
      UUID assignedSessionId,
      UUID eventId,
      long version) {}

  public record Category(UUID id, String name, boolean enabled, long version) {}

  public record TicketTypeDraft(
      UUID id, String name, Integer capacity, Long priceJiao, boolean saleStopped) {}

  public record SessionManage(
      UUID id,
      String name,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String location,
      Integer capacity,
      OffsetDateTime salesOpensAt,
      OffsetDateTime registrationClosesAt,
      List<TicketTypeDraft> ticketTypes,
      SalesStatus sessionSalesStatus,
      int checkerCount) {}

  public record EventManage(
      UUID id,
      UUID organizerId,
      EventStatus status,
      long version,
      String name,
      UUID categoryId,
      String categoryNameSnapshot,
      String description,
      String posterUrl,
      List<SessionManage> sessions,
      OffsetDateTime submittedAt,
      OffsetDateTime publishedAt,
      OffsetDateTime terminatedAt,
      String terminationReason,
      String lastReviewReason,
      boolean reviewOverdue,
      SettlementStatus settlementStatus,
      OffsetDateTime expiresAt,
      List<String> allowedActions,
      OffsetDateTime plannedStartsAt,
      OffsetDateTime plannedEndsAt,
      Integer totalCapacity,
      Long minPriceJiao,
      Long maxPriceJiao) {}

  public record TicketTypeView(
      UUID id,
      String name,
      int capacity,
      long priceJiao,
      boolean saleStopped,
      long occupied,
      long remaining,
      SalesStatus salesStatus) {}

  public record SessionPublic(
      UUID id,
      UUID eventId,
      String eventName,
      UUID categoryId,
      String categoryNameSnapshot,
      String name,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String location,
      int capacity,
      OffsetDateTime salesOpensAt,
      OffsetDateTime registrationClosesAt,
      long remaining,
      SalesStatus sessionSalesStatus,
      List<TicketTypeView> ticketTypes,
      String posterUrl) {}

  public record SessionSearchRow(
      UUID id,
      UUID eventId,
      String eventName,
      UUID categoryId,
      String categoryNameSnapshot,
      String name,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String location,
      int capacity,
      OffsetDateTime salesOpensAt,
      OffsetDateTime registrationClosesAt,
      long remaining,
      SalesStatus sessionSalesStatus,
      List<TicketTypeView> ticketTypes,
      String posterUrl,
      List<UUID> matchedTicketTypeIds) {}

  public record EventPublic(
      UUID id,
      String name,
      UUID categoryId,
      String categoryNameSnapshot,
      String description,
      String posterUrl,
      List<SessionPublic> sessions) {}

  public record ReviewResult(
      UUID operationId,
      String outcome,
      String reasonCode,
      String reason,
      boolean replayed,
      EventManage current,
      int dailyPublishedCount) {}

  public record TerminationResult(
      UUID operationId,
      boolean replayed,
      EventManage current,
      OffsetDateTime effectiveAt,
      UUID jobId,
      SettlementStatus settlementStatus) {}

  public record ReviewQueue(
      List<EventManage> items,
      int page,
      int pageSize,
      long total,
      int dailyPublishedCount,
      int dailyLimit,
      String day) {}

  public record SettlementProgress(
      UUID eventId,
      EventStatus eventStatus,
      OffsetDateTime effectiveAt,
      SettlementStatus settlementStatus,
      JobView job) {}

  public record EventHistorySummary(
      UUID eventId,
      String eventName,
      String categoryNameSnapshot,
      EventStatus eventStatus,
      UUID sessionId,
      String sessionName,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String location,
      String terminationReason,
      OffsetDateTime terminatedAt) {}

  public record OrderView(
      UUID id,
      UUID requestId,
      OrderStatus status,
      String statusReason,
      OffsetDateTime createdAt,
      String attendeeNameSnapshot,
      long amountJiao,
      EventHistorySummary event,
      UUID ticketTypeId,
      String ticketTypeName,
      OffsetDateTime payDeadline,
      OffsetDateTime confirmDeadline,
      Integer queuePosition,
      int consecutivePaymentFailures,
      UUID ticketId,
      UUID activeRefundRequestId,
      List<String> allowedActions) {}

  public record RegistrationResult(
      UUID operationId, String operationResult, boolean replayed, OrderView current) {}

  public record QueueView(
      UUID orderId,
      OrderStatus status,
      Integer position,
      OffsetDateTime confirmDeadline,
      OffsetDateTime payDeadline,
      boolean temporarilySkipped,
      String skipReason) {}

  public record PaymentAttempt(
      UUID id,
      UUID attemptId,
      UUID orderId,
      AttemptResult result,
      long amountJiao,
      OffsetDateTime createdAt,
      String failureReason) {}

  public record PaymentResult(
      UUID operationId,
      PaymentAttempt attempt,
      boolean replayed,
      OrderView current,
      TicketView ticket) {}

  public record RefundEligibility(OffsetDateTime registrationClosesAt, OrderStatus orderStatus) {}

  public record RefundRequest(
      UUID id,
      UUID orderId,
      RefundSource source,
      RefundRequestStatus status,
      OffsetDateTime acceptedAt,
      UUID initialAttemptId,
      String reason,
      RefundEligibility eligibilityAtAcceptance,
      OffsetDateTime revokedAt,
      String closureReason) {}

  public record RefundAttempt(
      UUID id,
      UUID attemptId,
      UUID requestId,
      UUID orderId,
      UUID originalPaymentId,
      AttemptResult result,
      long amountJiao,
      OffsetDateTime createdAt,
      String failureReason) {}

  public record RefundResult(
      UUID operationId,
      RefundRequest request,
      RefundAttempt attempt,
      boolean replayed,
      OrderView current) {}

  public record MyRefundDetail(
      RefundRequest request, List<RefundAttempt> attempts, OrderView current) {}

  public record AdminRefundDetail(
      RefundRequest request, List<RefundAttempt> attempts, OrderView order) {}

  public record TicketView(
      UUID id,
      UUID orderId,
      TicketStatus status,
      String attendeeName,
      EventHistorySummary event,
      String ticketTypeName,
      long paidAmountJiao,
      OffsetDateTime checkedAt,
      boolean usable,
      String invalidReason) {}

  public record PaymentAudit(
      List<PaymentAttempt> paymentAttempts,
      List<RefundRequest> refundRequests,
      List<RefundAttempt> refundAttempts) {}

  public record CheckerContext(
      SessionPublic session,
      EventStatus eventStatus,
      OffsetDateTime checkinOpensAt,
      OffsetDateTime checkinClosesAt,
      List<String> allowedActions) {}

  public record TicketPreview(
      UUID ticketId,
      String attendeeName,
      UUID sessionId,
      String sessionName,
      String ticketTypeName,
      TicketStatus ticketStatus,
      boolean alreadyChecked,
      OffsetDateTime checkedAt,
      boolean canConfirm) {}

  public record CheckInResult(
      UUID ticketId,
      UUID orderId,
      OffsetDateTime checkedAt,
      UUID checkerId,
      UUID sessionId,
      boolean replayed,
      OrderStatus orderStatus,
      TicketStatus ticketStatus) {}

  public record Reconciliation(
      UUID sessionId,
      int processedCount,
      int alreadyProcessedCount,
      SettlementStatus settlementStatus) {}

  public record AttendeeRow(
      String attendeeName,
      UUID sessionId,
      String sessionName,
      UUID ticketTypeId,
      String ticketTypeName,
      OrderStatus orderStatus,
      CheckinStatus checkinStatus,
      UUID ticketId) {}

  public record ExportView(
      UUID id,
      UUID eventId,
      ExportStatus status,
      Long rowCount,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt,
      String downloadUrl,
      String failureReason) {}

  public record TicketTypeCount(
      UUID sessionId, UUID ticketTypeId, long capacity, long occupied, long remaining) {}

  public record LiveCounts(
      UUID eventId,
      UUID sessionId,
      OffsetDateTime asOf,
      Map<OrderStatus, Long> orderCounts,
      long currentWaitlist,
      long cumulativeWaitlistApplications,
      long occupied,
      long remaining,
      long checkedIn,
      List<TicketTypeCount> ticketTypeCounts,
      SettlementStatus settlementStatus)
      implements Counts {}

  public record CheckerCounts(
      UUID sessionId, OffsetDateTime asOf, long checkedIn, SettlementStatus settlementStatus)
      implements Counts {}

  public record Statistics(
      UUID eventId,
      UUID sessionId,
      StatisticsReason reason,
      SettlementStatus settlementStatus,
      OffsetDateTime generatedAt,
      OffsetDateTime expiresAt,
      Long issuedCount,
      Long refundCount,
      Long nonRefundedIssuedCount,
      Long checkedInCount,
      Long absentCount,
      Long capacityAtEnd,
      Long currentWaitlistCount,
      Long cumulativeWaitlistApplications,
      Double attendanceRate,
      Double absenceRate,
      Double capacityUtilizationRate)
      implements StatisticsProjection {}

  public record CheckerStatistics(
      UUID sessionId,
      SettlementStatus settlementStatus,
      OffsetDateTime generatedAt,
      OffsetDateTime expiresAt,
      Long checkedInCount,
      Double attendanceRate)
      implements StatisticsProjection {}

  public record PaymentSummary(
      UUID eventId,
      UUID sessionId,
      long successfulPaymentCount,
      long paidAmountJiao,
      long successfulRefundCount,
      long refundedAmountJiao,
      long netAmountJiao,
      OffsetDateTime asOf) {}

  public record ReportView(
      UUID id,
      UUID sessionId,
      UUID eventId,
      String reason,
      String description,
      String evidenceText,
      ReportStatus status,
      OffsetDateTime createdAt,
      String conclusion,
      OffsetDateTime resolvedAt) {}

  public record ReportAdminRow(
      UUID id,
      UUID sessionId,
      UUID eventId,
      String reason,
      String description,
      String evidenceText,
      ReportStatus status,
      OffsetDateTime createdAt,
      String conclusion,
      OffsetDateTime resolvedAt,
      UUID reporterId,
      int eventAcceptedReportCount,
      boolean needsAttention) {}

  public record InvestigationResult(
      UUID id,
      UUID factId,
      UUID eventId,
      Conclusion conclusion,
      List<UUID> mergedReportIds,
      boolean violationRecorded,
      EventStatus eventStatus,
      UUID terminationJobId) {}

  public record InvestigationDetail(
      UUID id,
      UUID factId,
      UUID eventId,
      Conclusion conclusion,
      List<UUID> mergedReportIds,
      boolean violationRecorded,
      EventStatus eventStatus,
      UUID terminationJobId,
      String reason,
      String evidenceText,
      String factKey,
      OffsetDateTime createdAt) {}

  public record RestrictionCandidate(
      UUID phoneIdentityId,
      String maskedPhone,
      RestrictionKind kind,
      int absenceCount,
      int voluntaryRefundCount,
      int violationCount,
      List<UUID> eligibleFactIds,
      RestrictionView activeRestriction,
      OffsetDateTime previousEffectiveAt) {}

  public record EvidenceFact(
      UUID id,
      String type,
      OffsetDateTime occurredAt,
      UUID orderId,
      UUID investigationId,
      String reason,
      boolean consumed) {}

  public record RestrictionEvidence(RestrictionCandidate candidate, List<EvidenceFact> facts) {}

  public record MessageTarget(String resourceType, UUID resourceId) {}

  public record MessageView(
      UUID id,
      String type,
      String title,
      String body,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt,
      OffsetDateTime readAt,
      MessageTarget target,
      OffsetDateTime actionDeadline) {}

  public record AuditLog(
      UUID id,
      UUID actorId,
      ActorRole actorRole,
      String action,
      String objectType,
      UUID objectId,
      AuditResult result,
      String failureReason,
      String jobType,
      OffsetDateTime createdAt,
      String traceId) {}

  public record RelatedIds(
      List<UUID> orders,
      List<UUID> payments,
      List<UUID> refunds,
      List<UUID> tickets,
      List<UUID> checkins) {}

  public record AuditObject(
      AuditObjectType objectType, UUID id, AuditRecord record, RelatedIds relatedIds) {}

  public record JobView(
      UUID id,
      String type,
      UUID eventId,
      JobStatus status,
      OffsetDateTime dueAt,
      OffsetDateTime deadlineAt,
      OffsetDateTime completedAt,
      int total,
      int processed,
      int retryCount,
      boolean overdue,
      String lastError) {}

  public record LoggedOut(boolean loggedOut) {}

  public record ChangedPassword(boolean changed, boolean requiresLogin) {}

  public record ClosedAccount(boolean closed, boolean requiresLogin) {}

  public record ResetPassword(UUID accountId, boolean reset, boolean mustChangePassword) {}

  public record HealthComponents(String mysql, String redis) {}

  public record Health(String status, HealthComponents components) {}
}
