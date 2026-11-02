package com.campus.ticketing.dao.jdbc;

import com.campus.ticketing.entity.*;
import java.util.Map;

public final class SchemaRegistry {
  private SchemaRegistry() {}

  public static final Map<Class<?>, String> TABLES =
      Map.ofEntries(
          Map.entry(PhoneIdentityEntity.class, "phone_identities"),
          Map.entry(UserEntity.class, "users"),
          Map.entry(LoginSessionEntity.class, "login_sessions"),
          Map.entry(RecoveryEntity.class, "recoveries"),
          Map.entry(CategoryEntity.class, "categories"),
          Map.entry(StoredFileEntity.class, "stored_files"),
          Map.entry(EventEntity.class, "events"),
          Map.entry(EventSessionEntity.class, "event_sessions"),
          Map.entry(TicketTypeEntity.class, "ticket_types"),
          Map.entry(CheckerAssignmentEntity.class, "checker_assignments"),
          Map.entry(EventReviewEntity.class, "event_reviews"),
          Map.entry(PublicationQuotaEntity.class, "publication_quotas"),
          Map.entry(OrderEntity.class, "orders"),
          Map.entry(RegistrationRequestEntity.class, "registration_requests"),
          Map.entry(PaymentAttemptEntity.class, "payment_attempts"),
          Map.entry(RefundRequestEntity.class, "refund_requests"),
          Map.entry(RefundAttemptEntity.class, "refund_attempts"),
          Map.entry(TicketEntity.class, "tickets"),
          Map.entry(CheckInEntity.class, "checkins"),
          Map.entry(ReportEntity.class, "reports"),
          Map.entry(InvestigationEntity.class, "investigations"),
          Map.entry(InvestigationReportEntity.class, "investigation_reports"),
          Map.entry(BehaviorFactEntity.class, "behavior_facts"),
          Map.entry(RestrictionEntity.class, "restrictions"),
          Map.entry(RestrictionFactEntity.class, "restriction_facts"),
          Map.entry(OperationRecordEntity.class, "operation_records"),
          Map.entry(MessageEntity.class, "messages"),
          Map.entry(AuditLogEntity.class, "audit_logs"),
          Map.entry(JobEntity.class, "jobs"),
          Map.entry(JobItemEntity.class, "job_items"),
          Map.entry(TerminationFactEntity.class, "termination_facts"),
          Map.entry(ExportEntity.class, "exports"),
          Map.entry(StatisticsSnapshotEntity.class, "statistics_snapshots"));
}
