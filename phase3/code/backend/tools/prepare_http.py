"""Generate explicit review-stage Controller -> typed Service -> 501 Impl.
Reads canonical HTTP paths from docs/api.md; no dynamic runtime route dispatcher.
"""
raise SystemExit("Preparation generator is frozen after business implementation; maintain production source and docs explicitly. It must not overwrite Service implementations.")

from pathlib import Path
import re

B=Path(__file__).resolve().parents[1]; J=B/'src/main/java/com/campus/ticketing'; D=B.parents[1]/'docs'
def write(path,text):
 p=J/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8')
def record_group(kind,records):
 imports='import java.util.*;\nimport java.time.OffsetDateTime;\nimport jakarta.validation.Valid;\nimport jakarta.validation.constraints.*;\nimport com.fasterxml.jackson.annotation.JsonProperty;\nimport com.campus.ticketing.enums.*;\nimport com.campus.ticketing.validation.*;\nimport com.campus.ticketing.common.*;\n'
 write(f'dto/{kind.lower()}/{kind}.java','package com.campus.ticketing.dto.'+kind.lower()+';\n\n'+imports+'\npublic final class '+kind+' {\n private '+kind+'() {}\n'+''.join(' public record '+name+'('+fields+') {}\n' for name,fields in records.items())+'}\n')

enums={
'Role':'PARTICIPANT ORGANIZER CHECKER ADMIN','EventStatus':'DRAFT PENDING_REVIEW PUBLISHED CANCELLED TAKEN_DOWN ENDED',
'OrderStatus':'WAITLISTED OFFERED UNPAID REGISTERED CHECKED_IN COMPLETED CANCELLED REFUNDED ABSENT',
'TicketStatus':'VALID CHECKED INVALID','AttemptResult':'SUCCESS FAILURE','RefundSource':'USER PLATFORM_TERMINATION',
'RefundRequestStatus':'ACCEPTED FAILED SUCCEEDED REVOKED','ReportStatus':'PENDING SUBSTANTIATED UNSUBSTANTIATED',
'RestrictionKind':'REGISTER PUBLISH','JobStatus':'PENDING RUNNING SUCCEEDED FAILED_RETRYABLE',
'ExportStatus':'PENDING RUNNING SUCCEEDED FAILED_RETRYABLE EXPIRED','SettlementStatus':'NOT_DUE PROCESSING COMPLETED',
'SalesStatus':'NOT_OPEN ON_SALE SOLD_OUT STOPPED CLOSED ENDED TERMINATED','RecoveryStatus':'PENDING VERIFIED RESOLVED REJECTED',
'RegisterRole':'PARTICIPANT ORGANIZER','ManagedRole':'ADMIN CHECKER','RecoveryDecision':'VERIFIED REJECTED',
'Conclusion':'SUBSTANTIATED UNSUBSTANTIATED','CheckinStatus':'NOT_CHECKED CHECKED','CategoryScope':'FILTER EDIT',
'SortBy':'startsAt remaining','SortDirection':'asc desc','AuditObjectType':'ORDER PAYMENT REFUND TICKET CHECKIN',
'AuditResult':'SUCCESS FAILURE','ActorRole':'PARTICIPANT ORGANIZER CHECKER ADMIN SYSTEM','StatisticsReason':'NATURAL CANCEL TAKEDOWN'}
for name,values in enums.items(): write('enums/'+name+'.java','package com.campus.ticketing.enums;\npublic enum '+name+' { '+', '.join(values.split())+' }\n')

nn='@NotNull '; name='@NotNull @CodePointLength(min=1,max=60) String '; text='@NotNull @CodePointLength(min=1,max=200) String '
pw='@NotNull @Pattern(regexp="(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}") String '
ver='@NotNull @Min(0) Long expectedVersion'
req={
'Empty':'', 'Login':name+'username, @NotNull @Size(max=72) String password',
'Register':name+'username, '+pw+'password, @NotNull @Email @Size(max=254) String email, @NotNull @Pattern(regexp="[0-9]{11}") String phone, @NotNull RegisterRole role',
'Recovery':name+'account, @NotNull @Pattern(regexp="[0-9]{11}") String phone',
'Profile':name+'displayName','Password':'@NotNull String oldPassword, '+pw+'newPassword',
'Closure':'@NotNull String password, @NotNull @Pattern(regexp="CLOSE") String confirmation',
'Registration':'@NotNull UUID requestId, @NotNull UUID sessionId, @NotNull UUID ticketTypeId, '+name+'attendeeName',
'Cancel':'@CodePointLength(max=200) String reason', 'Attempt':'@NotNull UUID attemptId',
'Refund':'@NotNull UUID attemptId, '+text+'reason','Ticket':'@NotNull UUID ticketId',
'Version':ver, 'VersionReason':ver+', '+text+'reason',
'Draft':ver+', @CodePointLength(min=1,max=60) String name, UUID categoryId, @CodePointLength(max=500) String description, @NotNull @Size(max=20) List<@NotNull @Valid SessionDraft> sessions',
'SessionDraft':'@NotNull UUID id, @CodePointLength(min=1,max=60) String name, @BeijingTime(granularity=3600) OffsetDateTime startsAt, @BeijingTime(granularity=3600) OffsetDateTime endsAt, @CodePointLength(min=1,max=60) String location, @Min(1) @Max(9999) Integer capacity, @BeijingTime(granularity=60) OffsetDateTime salesOpensAt, @BeijingTime(granularity=60) OffsetDateTime registrationClosesAt, @NotNull @Size(max=10) List<@NotNull @Valid TicketTypeDraft> ticketTypes',
'TicketTypeDraft':'@NotNull UUID id, @CodePointLength(min=1,max=60) String name, @Min(1) @Max(9999) Integer capacity, @Min(0) @Max(9999990) Long priceJiao, @NotNull Boolean saleStopped',
'PublishedEdit':ver+', @Valid PatchField<@CodePointLength(max=500) String> description, @PatchNonNull @Valid PatchField<@NotNull @Size(max=20) List<@NotNull @Valid SessionEdit>> sessions',
'SessionEdit':'@NotNull UUID id, @PatchNonNull @Valid PatchField<@CodePointLength(min=1,max=60) String> location, @PatchNonNull @Valid PatchField<@Min(1) @Max(9999) Integer> capacity, @PatchNonNull @Valid PatchField<@NotNull @Size(max=10) List<@NotNull @Valid TypeEdit>> ticketTypes',
'TypeEdit':'@NotNull UUID id, @PatchNonNull @Valid PatchField<@Min(1) @Max(9999) Integer> capacity, @PatchNonNull PatchField<Boolean> saleStopped',
'Export':'UUID sessionId, UUID ticketTypeId, OrderStatus orderStatus, CheckinStatus checkinStatus',
'Report':'@NotNull UUID sessionId, '+text+'reason, @NotNull @CodePointLength(min=1,max=2000) String description, @NotNull @CodePointLength(min=1,max=2000) String evidenceText',
'CreateAccount':name+'username, '+pw+'initialPassword, @NotNull ManagedRole role, UUID sessionId',
'UpdateAccount':ver+', @PatchNonNull PatchField<Boolean> enabled, @PatchNonNull PatchField<UUID> assignedSessionId',
'ResetPassword':pw+'initialPassword, '+text+'reason, UUID recoveryRequestId',
'ResolveRecovery':'@NotNull RecoveryDecision decision, @NotNull @CodePointLength(min=1,max=2000) String verificationNote',
'CreateCategory':name+'name','UpdateCategory':ver+', @PatchNonNull @Valid PatchField<@CodePointLength(min=1,max=60) String> name, @PatchNonNull PatchField<Boolean> enabled',
'Takedown':ver+', @NotNull UUID investigationId',
'Investigation':'@NotNull UUID eventId, @NotNull List<@NotNull UUID> reportIds, @NotNull @CodePointLength(min=1,max=120) String factKey, @NotNull Conclusion conclusion, '+text+'reason, @NotNull @CodePointLength(min=1,max=2000) String evidenceText',
'Restriction':'@NotNull UUID phoneIdentityId, @NotNull @Size(min=1) List<@NotNull UUID> basisFactIds, '+text+'reason',
'PosterUpload':ver+', @NotNull byte[] bytes, @NotNull String contentType'
}
record_group('Requests',req)
# Nullable mandatory fields must be present in JSON even when null.
p=J/'dto/requests/Requests.java';s=p.read_text(encoding='utf8')
for cls,fields in {'Cancel':['reason'],'Draft':['name','categoryId','description'], 'SessionDraft':['name','startsAt','endsAt','location','capacity','salesOpensAt','registrationClosesAt'], 'TicketTypeDraft':['name','capacity','priceJiao']}.items():
 lines=s.splitlines()
 for i,line in enumerate(lines):
  if 'public record '+cls+'(' in line:
   for field in fields:
    line=re.sub(r'(?<![\w])('+field+r')(?=[,)])',r'\1',line)
    # Place property annotation immediately before the Java type.
    line=re.sub(r'(String|UUID|Integer|Long|OffsetDateTime) '+field+r'(?=[,)])',r'@JsonProperty(required=true) \1 '+field,line)
   lines[i]=line
 s='\n'.join(lines)+'\n'
p.write_text(s,encoding='utf8')
# Optional but non-nullable fields: missing is allowed, explicit null is malformed.
s=p.read_text(encoding='utf8')
for cls,fields in {'Export':['sessionId','ticketTypeId','orderStatus','checkinStatus'],'CreateAccount':['sessionId'],'ResetPassword':['recoveryRequestId']}.items():
 lines=s.splitlines()
 for i,line in enumerate(lines):
  if 'public record '+cls+'(' in line:
   for field in fields:
    line=re.sub(r'(UUID|OrderStatus|CheckinStatus) '+field+r'(?=[,)])',r'@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=OmittedNonNullDeserializer.class) \1 '+field,line)
   lines[i]=line
 s='\n'.join(lines)+'\n'
p.write_text(s,encoding='utf8')

page='@Min(1) Integer page, @Min(1) @Max(100) Integer pageSize'
query={
'None':'', 'Categories':'CategoryScope scope',
'PublicSessions':'String eventName, UUID categoryId, @BeijingTime OffsetDateTime startsFrom, @BeijingTime OffsetDateTime startsTo, String location, String ticketTypeName, @Min(0) @Max(9999990) Long minPriceJiao, @Min(0) @Max(9999990) Long maxPriceJiao, SalesStatus salesStatus, SortBy sortBy, SortDirection sortDirection, '+page,
'Orders':'OrderStatus status, UUID eventId, '+page,'Events':'EventStatus status, '+page,
'Version':ver,'Attendees':'UUID sessionId, UUID ticketTypeId, OrderStatus orderStatus, CheckinStatus checkinStatus, '+page,
'Session':'UUID sessionId', 'Reports':'ReportStatus status, '+page,'Messages':'Boolean unreadOnly, '+page,
'Accounts':'Role role, String username, UUID eventId, UUID sessionId, Boolean enabled, '+page,
'Recoveries':'RecoveryStatus status, '+page,'Reviews':'Boolean overdueOnly, '+page,'CategoryAdmin':'Boolean enabled, '+page,
'Payments':'UUID orderId, AttemptResult result, @BeijingTime OffsetDateTime from, @BeijingTime OffsetDateTime to, '+page,
'AdminReports':'UUID eventId, ReportStatus status, Boolean needsAttention, '+page,
'Candidates':'RestrictionKind kind, '+page, 'Candidate':'@NotNull RestrictionKind kind',
'Audit':'@BeijingTime OffsetDateTime from, @BeijingTime OffsetDateTime to, String action, String objectType, UUID objectId, AuditResult result, '+page,
'Jobs':'UUID eventId, @Pattern(regexp="JOB-0[1-7]") String type, JobStatus status, Boolean overdue, '+page,
'AdminEvents':'EventStatus status, String name, UUID organizerId, '+page,
'Investigations':'UUID eventId, Conclusion conclusion, '+page,
'Refunds':'RefundRequestStatus status, RefundSource source, UUID orderId, '+page}
record_group('Queries',query)

views={
'Csrf':'String token, String headerName',
'UserView':'UUID id, String username, Role role, String email, String maskedPhone, String displayName, boolean mustChangePassword, OffsetDateTime sessionExpiresAt, List<RestrictionView> restrictions, UUID checkerSessionId',
'ForcePasswordUser':'UUID id, String username, Role role, boolean mustChangePassword, OffsetDateTime sessionExpiresAt',
'RestrictionView':'UUID id, RestrictionKind kind, OffsetDateTime startsAt, OffsetDateTime endsAt, String reason',
'Registered':'UUID id, String username, Role role','RecoveryReceipt':'UUID requestId, String status, String message',
'RecoveryView':'UUID id, String account, String phone, UUID targetAccountId, RecoveryStatus status, OffsetDateTime createdAt, String verificationNote, OffsetDateTime resolvedAt',
'ClosureBlocker':'String code, String message, String objectType, UUID objectId',
'ClosureEligibility':'boolean eligible, List<ClosureBlocker> blockers, int cancellationCount, OffsetDateTime lastCancelledAt, OffsetDateTime nextEligibleAt, boolean willBlacklist',
'ManagedAccount':'UUID id, String username, Role role, boolean enabled, OffsetDateTime deletedAt, boolean mustChangePassword, UUID assignedSessionId, UUID eventId, long version',
'Category':'UUID id, String name, boolean enabled, long version',
'TicketTypeDraft':'UUID id, String name, Integer capacity, Long priceJiao, boolean saleStopped',
'SessionManage':'UUID id, String name, OffsetDateTime startsAt, OffsetDateTime endsAt, String location, Integer capacity, OffsetDateTime salesOpensAt, OffsetDateTime registrationClosesAt, List<TicketTypeDraft> ticketTypes, SalesStatus sessionSalesStatus, int checkerCount',
'EventManage':'UUID id, UUID organizerId, EventStatus status, long version, String name, UUID categoryId, String categoryNameSnapshot, String description, String posterUrl, List<SessionManage> sessions, OffsetDateTime submittedAt, OffsetDateTime publishedAt, OffsetDateTime terminatedAt, String terminationReason, String lastReviewReason, boolean reviewOverdue, SettlementStatus settlementStatus, OffsetDateTime expiresAt, List<String> allowedActions, OffsetDateTime plannedStartsAt, OffsetDateTime plannedEndsAt, Integer totalCapacity, Long minPriceJiao, Long maxPriceJiao',
'TicketTypeView':'UUID id, String name, int capacity, long priceJiao, boolean saleStopped, long occupied, long remaining, SalesStatus salesStatus',
'SessionPublic':'UUID id, UUID eventId, String eventName, UUID categoryId, String categoryNameSnapshot, String name, OffsetDateTime startsAt, OffsetDateTime endsAt, String location, int capacity, OffsetDateTime salesOpensAt, OffsetDateTime registrationClosesAt, long remaining, SalesStatus sessionSalesStatus, List<TicketTypeView> ticketTypes, String posterUrl',
'SessionSearchRow':'UUID id, UUID eventId, String eventName, UUID categoryId, String categoryNameSnapshot, String name, OffsetDateTime startsAt, OffsetDateTime endsAt, String location, int capacity, OffsetDateTime salesOpensAt, OffsetDateTime registrationClosesAt, long remaining, SalesStatus sessionSalesStatus, List<TicketTypeView> ticketTypes, String posterUrl, List<UUID> matchedTicketTypeIds',
'EventPublic':'UUID id, String name, UUID categoryId, String categoryNameSnapshot, String description, String posterUrl, List<SessionPublic> sessions',
'ReviewResult':'UUID operationId, String outcome, String reasonCode, String reason, boolean replayed, EventManage current, int dailyPublishedCount',
'TerminationResult':'UUID operationId, boolean replayed, EventManage current, OffsetDateTime effectiveAt, UUID jobId, SettlementStatus settlementStatus',
'ReviewQueue':'List<EventManage> items, int page, int pageSize, long total, int dailyPublishedCount, int dailyLimit, String day',
'SettlementProgress':'UUID eventId, EventStatus eventStatus, OffsetDateTime effectiveAt, SettlementStatus settlementStatus, JobView job',
'EventHistorySummary':'UUID eventId, String eventName, String categoryNameSnapshot, EventStatus eventStatus, UUID sessionId, String sessionName, OffsetDateTime startsAt, OffsetDateTime endsAt, String location, String terminationReason, OffsetDateTime terminatedAt',
'OrderView':'UUID id, UUID requestId, OrderStatus status, String statusReason, OffsetDateTime createdAt, String attendeeNameSnapshot, long amountJiao, EventHistorySummary event, UUID ticketTypeId, String ticketTypeName, OffsetDateTime payDeadline, OffsetDateTime confirmDeadline, Integer queuePosition, int consecutivePaymentFailures, UUID ticketId, UUID activeRefundRequestId, List<String> allowedActions',
'RegistrationResult':'UUID operationId, String operationResult, boolean replayed, OrderView current',
'QueueView':'UUID orderId, OrderStatus status, Integer position, OffsetDateTime confirmDeadline, OffsetDateTime payDeadline, boolean temporarilySkipped, String skipReason',
'PaymentAttempt':'UUID id, UUID attemptId, UUID orderId, AttemptResult result, long amountJiao, OffsetDateTime createdAt, String failureReason',
'PaymentResult':'UUID operationId, PaymentAttempt attempt, boolean replayed, OrderView current, TicketView ticket',
'RefundEligibility':'OffsetDateTime registrationClosesAt, OrderStatus orderStatus',
'RefundRequest':'UUID id, UUID orderId, RefundSource source, RefundRequestStatus status, OffsetDateTime acceptedAt, UUID initialAttemptId, String reason, RefundEligibility eligibilityAtAcceptance, OffsetDateTime revokedAt, String closureReason',
'RefundAttempt':'UUID id, UUID attemptId, UUID requestId, UUID orderId, UUID originalPaymentId, AttemptResult result, long amountJiao, OffsetDateTime createdAt, String failureReason',
'RefundResult':'UUID operationId, RefundRequest request, RefundAttempt attempt, boolean replayed, OrderView current',
'MyRefundDetail':'RefundRequest request, List<RefundAttempt> attempts, OrderView current',
'AdminRefundDetail':'RefundRequest request, List<RefundAttempt> attempts, OrderView order',
'TicketView':'UUID id, UUID orderId, TicketStatus status, String attendeeName, EventHistorySummary event, String ticketTypeName, long paidAmountJiao, OffsetDateTime checkedAt, boolean usable, String invalidReason',
'PaymentAudit':'List<PaymentAttempt> paymentAttempts, List<RefundRequest> refundRequests, List<RefundAttempt> refundAttempts',
'CheckerContext':'SessionPublic session, EventStatus eventStatus, OffsetDateTime checkinOpensAt, OffsetDateTime checkinClosesAt, List<String> allowedActions',
'TicketPreview':'UUID ticketId, String attendeeName, UUID sessionId, String sessionName, String ticketTypeName, TicketStatus ticketStatus, boolean alreadyChecked, OffsetDateTime checkedAt, boolean canConfirm',
'CheckInResult':'UUID ticketId, UUID orderId, OffsetDateTime checkedAt, UUID checkerId, UUID sessionId, boolean replayed, OrderStatus orderStatus, TicketStatus ticketStatus',
'Reconciliation':'UUID sessionId, int processedCount, int alreadyProcessedCount, SettlementStatus settlementStatus',
'AttendeeRow':'String attendeeName, UUID sessionId, String sessionName, UUID ticketTypeId, String ticketTypeName, OrderStatus orderStatus, CheckinStatus checkinStatus, UUID ticketId',
'ExportView':'UUID id, UUID eventId, ExportStatus status, Long rowCount, OffsetDateTime createdAt, OffsetDateTime expiresAt, String downloadUrl, String failureReason',
'TicketTypeCount':'UUID sessionId, UUID ticketTypeId, long capacity, long occupied, long remaining',
'LiveCounts':'UUID eventId, UUID sessionId, OffsetDateTime asOf, Map<OrderStatus,Long> orderCounts, long currentWaitlist, long cumulativeWaitlistApplications, long occupied, long remaining, long checkedIn, List<TicketTypeCount> ticketTypeCounts, SettlementStatus settlementStatus',
'CheckerCounts':'UUID sessionId, OffsetDateTime asOf, long checkedIn, SettlementStatus settlementStatus',
'Statistics':'UUID eventId, UUID sessionId, StatisticsReason reason, SettlementStatus settlementStatus, OffsetDateTime generatedAt, OffsetDateTime expiresAt, Long issuedCount, Long refundCount, Long nonRefundedIssuedCount, Long checkedInCount, Long absentCount, Long capacityAtEnd, Long currentWaitlistCount, Long cumulativeWaitlistApplications, Double attendanceRate, Double absenceRate, Double capacityUtilizationRate',
'CheckerStatistics':'UUID sessionId, SettlementStatus settlementStatus, OffsetDateTime generatedAt, OffsetDateTime expiresAt, Long checkedInCount, Double attendanceRate',
'PaymentSummary':'UUID eventId, UUID sessionId, long successfulPaymentCount, long paidAmountJiao, long successfulRefundCount, long refundedAmountJiao, long netAmountJiao, OffsetDateTime asOf',
'ReportView':'UUID id, UUID sessionId, UUID eventId, String reason, String description, String evidenceText, ReportStatus status, OffsetDateTime createdAt, String conclusion, OffsetDateTime resolvedAt',
'ReportAdminRow':'UUID id, UUID sessionId, UUID eventId, String reason, String description, String evidenceText, ReportStatus status, OffsetDateTime createdAt, String conclusion, OffsetDateTime resolvedAt, UUID reporterId, int eventAcceptedReportCount, boolean needsAttention',
'InvestigationResult':'UUID id, UUID factId, UUID eventId, Conclusion conclusion, List<UUID> mergedReportIds, boolean violationRecorded, EventStatus eventStatus, UUID terminationJobId',
'InvestigationDetail':'UUID id, UUID factId, UUID eventId, Conclusion conclusion, List<UUID> mergedReportIds, boolean violationRecorded, EventStatus eventStatus, UUID terminationJobId, String reason, String evidenceText, String factKey, OffsetDateTime createdAt',
'RestrictionCandidate':'UUID phoneIdentityId, String maskedPhone, RestrictionKind kind, int absenceCount, int voluntaryRefundCount, int violationCount, List<UUID> eligibleFactIds, RestrictionView activeRestriction, OffsetDateTime previousEffectiveAt',
'EvidenceFact':'UUID id, String type, OffsetDateTime occurredAt, UUID orderId, UUID investigationId, String reason, boolean consumed',
'RestrictionEvidence':'RestrictionCandidate candidate, List<EvidenceFact> facts',
'MessageTarget':'String resourceType, UUID resourceId',
'MessageView':'UUID id, String type, String title, String body, OffsetDateTime createdAt, OffsetDateTime expiresAt, OffsetDateTime readAt, MessageTarget target, OffsetDateTime actionDeadline',
'AuditLog':'UUID id, UUID actorId, ActorRole actorRole, String action, String objectType, UUID objectId, AuditResult result, String failureReason, String jobType, OffsetDateTime createdAt, String traceId',
'RelatedIds':'List<UUID> orders, List<UUID> payments, List<UUID> refunds, List<UUID> tickets, List<UUID> checkins',
'AuditObject':'AuditObjectType objectType, UUID id, AuditRecord record, RelatedIds relatedIds',
'JobView':'UUID id, String type, UUID eventId, JobStatus status, OffsetDateTime dueAt, OffsetDateTime deadlineAt, OffsetDateTime completedAt, int total, int processed, int retryCount, boolean overdue, String lastError',
'LoggedOut':'boolean loggedOut','ChangedPassword':'boolean changed, boolean requiresLogin','ClosedAccount':'boolean closed, boolean requiresLogin',
'ResetPassword':'UUID accountId, boolean reset, boolean mustChangePassword',
'HealthComponents':'String mysql, String redis','Health':'String status, HealthComponents components'
}
record_group('Views',views)
# Explicit union types prevent accidental full-data serialization for CHECKER/forced-password.
p=J/'dto/views/Views.java';s=p.read_text(encoding='utf8').replace('public final class Views {','public final class Views {\n public sealed interface Identity permits UserView, ForcePasswordUser {}\n public sealed interface Counts permits LiveCounts, CheckerCounts {}\n public sealed interface StatisticsProjection permits Statistics, CheckerStatistics {}\n public sealed interface AuditRecord permits OrderAuditRecord, PaymentAuditRecord, RefundAuditRecord, TicketAuditRecord, CheckInAuditRecord {}\n public record OrderAuditRecord(UUID id, OrderStatus status, long amountJiao, OffsetDateTime createdAt) implements AuditRecord {}\n public record PaymentAuditRecord(UUID id, UUID orderId, AttemptResult result, long amountJiao, OffsetDateTime createdAt) implements AuditRecord {}\n public record RefundAuditRecord(UUID id, UUID orderId, UUID requestId, AttemptResult result, long amountJiao, OffsetDateTime createdAt) implements AuditRecord {}\n public record TicketAuditRecord(UUID id, UUID orderId, TicketStatus status) implements AuditRecord {}\n public record CheckInAuditRecord(UUID id, UUID ticketId, UUID orderId, UUID sessionId, UUID checkerId, OffsetDateTime checkedAt) implements AuditRecord {}')
for cls,interface in [('UserView','Identity'),('ForcePasswordUser','Identity'),('LiveCounts','Counts'),('CheckerCounts','Counts'),('Statistics','StatisticsProjection'),('CheckerStatistics','StatisticsProjection')]:
 s=re.sub(r'(public record '+cls+r'\([^\n]+\)) \{\}',r'\1 implements '+interface+' {}',s)
p.write_text(s,encoding='utf8')

# id -> request type, query type, response type. N/A request or query is '-'.
spec={}
def group(ids,lines):
 for id,line in zip(ids,lines.strip().splitlines(),strict=True): spec[id]=line.split()
group([f'AUTH-{i:02}' for i in range(1,6)],'''
- - Views.Csrf
Login - Views.Identity
Register - Views.Registered
Recovery - Views.RecoveryReceipt
Empty - Views.LoggedOut
''')
group([f'ME-{i:02}' for i in range(1,27)],'''
- - Views.Identity
Profile - Mutation<Views.UserView>
Password - Views.ChangedPassword
- - Views.ClosureEligibility
Closure - Views.ClosedAccount
Registration - Views.RegistrationResult
- Orders PageResult<Views.OrderView>
- - Views.OrderView
- - Views.RegistrationResult
Cancel - Mutation<Views.OrderView>
- - Views.QueueView
Empty - Mutation<Views.OrderView>
Attempt - Views.PaymentResult
- - Views.PaymentAudit
Refund - Views.RefundResult
- - Views.MyRefundDetail
Attempt - Views.RefundResult
Empty - Mutation<Views.RefundRequest>
- - Views.TicketView
- - Resource
Report - Views.ReportView
- Reports PageResult<Views.ReportView>
- - Views.ReportView
- Messages PageResult<Views.MessageView>
- - Views.MessageView
Empty - Mutation<Views.MessageView>
''')
group([f'PUB-{i:02}' for i in range(1,5)],'''
- Categories List<Views.Category>
- PublicSessions PageResult<Views.SessionSearchRow>
- - Views.EventPublic
- - Resource
''')
group([f'ORG-{i:02}' for i in range(1,12)],'''
- Events PageResult<Views.EventManage>
Empty - Views.EventManage
- - Views.EventManage
Draft - Mutation<Views.EventManage>
PosterUpload - Mutation<Views.EventManage>
- Version Mutation<Views.EventManage>
Version - Mutation<Views.EventManage>
Version - Mutation<Views.EventManage>
PublishedEdit - Mutation<Views.EventManage>
VersionReason - Views.TerminationResult
- - Views.SettlementProgress
''')
group([f'CHK-{i:02}' for i in range(1,5)],'''
- - Views.CheckerContext
Ticket - Views.TicketPreview
Ticket - Views.CheckInResult
Empty - Views.Reconciliation
''')
group([f'MGT-{i:02}' for i in range(1,9)],'''
- Attendees PageResult<Views.AttendeeRow>
Export - Views.ExportView
- - Views.ExportView
- - Resource
- Session Views.Counts
- Session Views.StatisticsProjection
- Session Views.PaymentSummary
- - Resource
''')
group([f'ADM-{i:02}' for i in range(1,32)],'''
- Accounts PageResult<Views.ManagedAccount>
CreateAccount - Views.ManagedAccount
UpdateAccount - Mutation<Views.ManagedAccount>
- Version Mutation<Views.ManagedAccount>
ResetPassword - Views.ResetPassword
- Recoveries PageResult<Views.RecoveryView>
ResolveRecovery - Views.RecoveryView
- Reviews Views.ReviewQueue
- - Views.EventManage
Version - Views.ReviewResult
VersionReason - Views.ReviewResult
- CategoryAdmin PageResult<Views.Category>
CreateCategory - Views.Category
UpdateCategory - Mutation<Views.Category>
Takedown - Views.TerminationResult
- Payments PageResult<Views.PaymentAttempt>
- - Views.AdminRefundDetail
Refund - Views.RefundResult
- AdminReports PageResult<Views.ReportAdminRow>
Investigation - Views.InvestigationResult
- - Views.InvestigationDetail
- Candidates PageResult<Views.RestrictionCandidate>
Restriction - Views.RestrictionView
- Audit PageResult<Views.AuditLog>
- - Views.AuditObject
- Jobs PageResult<Views.JobView>
- - Views.JobView
- AdminEvents PageResult<Views.EventManage>
- Candidate Views.RestrictionEvidence
- Investigations PageResult<Views.InvestigationResult>
- Refunds PageResult<Views.RefundRequest>
''')

api={m[1]:(m[2],m[3],m[4]) for m in re.finditer(r'^\| ([A-Z]+-\d+) \| (GET|POST|PUT|PATCH|DELETE) `([^`]+)` \| ([^|]+)\|', (D/'api.md').read_text(encoding='utf8'),re.M)}
mapping=[]
for line in (D/'backend-endpoints.md').read_text(encoding='utf8').splitlines():
 m=re.match(r'\| ([A-Z]+-\d+) \| .*? \| `([\w]+)\.([\w]+)` \| `([\w]+)`',line)
 if m: mapping.append(m.groups())
assert len(mapping)==len(api)==len(spec)==89
imports='''import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;
import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.queries.Queries;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.security.SessionPrincipal;
import com.campus.ticketing.enums.*;
'''
controllers={};services={};rows=[]
for id,ctrl,method,svc in mapping:
 if id=='AUTH-01': continue # handwritten CSRF service adapter
 verb,path,permission=api[id]; body,q,result=spec[id]
 args=['RequestContext context']; cargs=['@CurrentRequest RequestContext context']; call=['context']
 for name in re.findall(r'\{(\w+)\}',path):
  typ='AuditObjectType' if name=='objectType' else 'UUID'
  args.append(typ+' '+name);cargs.append('@PathVariable '+typ+' '+name);call.append(name)
 if q!='-': args.append('Queries.'+q+' query');cargs.append('@Valid @ModelAttribute Queries.'+q+' query');call.append('query')
 if body!='-':
  args.append('Requests.'+body+' request'); call.append('request')
  if body=='PosterUpload':
   cargs+=['@RequestPart("file") MultipartFile file','@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) @RequestParam("expectedVersion") Long expectedVersion']
  else: cargs.append('@Valid @RequestBody Requests.'+body+' request')
 sig=('DownloadResult' if result=='Resource' else 'UseCaseResult<'+result+'>')+' '+method+'('+', '.join(args)+')'
 services.setdefault(svc,[]).append((sig,id))
 roles=[]
 if permission.strip() not in ('G','G/U'):
  if '登录' in permission or permission.strip().startswith('U'): auth='isAuthenticated()'
  else:
   for role,marker in [('PARTICIPANT','P'),('ORGANIZER','O'),('CHECKER','C'),('ADMIN','A')]:
    if re.search(r'(?<![A-Z])'+marker+r'(?![A-Z])',permission): roles.append(role)
   auth="hasAnyRole("+','.join("'"+r+"'" for r in roles)+")"
 else: auth='permitAll()'
 whitelist=[] if q=='-' else re.findall(r'(\w+)(?:,|$)',query[q])
 # Use record field names, annotations may contain commas but not trailing identifier patterns.
 if body=='PosterUpload': whitelist=['expectedVersion']
 requirekey=verb!='GET' and id not in {'AUTH-02','AUTH-03','AUTH-04','AUTH-05','ME-06','ME-13','CHK-02','CHK-03'}
 annotation=' @ApiContract(id="'+id+'", idempotency='+str(requirekey).lower()+', query={'+','.join('"'+v+'"' for v in whitelist)+'})\n'
 annotation+=' @PreAuthorize("'+auth+'")\n @'+verb.title()+'Mapping(value="'+path+'"'+(', consumes=MediaType.MULTIPART_FORM_DATA_VALUE' if body=='PosterUpload' else '')+')\n'
 ret='ResponseEntity<Resource>' if result=='Resource' else 'ResponseEntity<ApiResponse<'+result+'>>'
 code=''
 if body=='PosterUpload':code='  if (file.getSize() > 5_000_000) throw new ApiException(413,"FILE_TOO_LARGE","海报超过大小限制");\n  Requests.PosterUpload request = new Requests.PosterUpload(expectedVersion, file.getBytes(), file.getContentType());\n'
 invocation=svc[0].lower()+svc[1:]+'.'+method+'('+', '.join(call)+')'
 # Real successful status/replay headers will be implemented per use case after review.
 value='responses.download('+invocation+')' if result=='Resource' else 'responses.toResponse('+invocation+')'
 code+= '  return '+value+';'
 controllers.setdefault(ctrl,{'services':set(),'methods':[]})['services'].add(svc)
 controllers[ctrl]['methods'].append(annotation+' public '+ret+' '+method+'('+', '.join(cargs)+')'+(' throws java.io.IOException' if body=='PosterUpload' else '')+' {\n'+code+'\n }\n')
 rows.append(f'| {id} | `{ctrl}.{method}` | `{body}` | `{q}` | `{result}` |')
services['RestrictionService'].append(('void reconcileDue(UUID jobId)','JOB-06'))
services['ExportService'].append(('void generatePending(UUID jobId)','JOB-07'))
for svc,methods in services.items():
 write('service/'+svc+'.java','package com.campus.ticketing.service;\n'+imports+'\npublic interface '+svc+' {\n'+''.join(' '+sig+';\n' for sig,id in methods)+'}\n')
 write('service/impl/'+svc+'Impl.java','package com.campus.ticketing.service.impl;\n'+imports+'import org.springframework.stereotype.Service;\nimport com.campus.ticketing.service.'+svc+';\n\n/** Review stage: no business transaction or database writes. */\n@Service\npublic class '+svc+'Impl implements '+svc+' {\n'+''.join(' @Override public '+sig+' { throw ApiException.notImplemented("'+id+'"); }\n' for sig,id in methods)+'}\n')
for ctrl,c in controllers.items():
 fields=' private final ApiResponses responses;\n'+''.join(' private final '+svc+' '+svc[0].lower()+svc[1:]+';\n' for svc in sorted(c['services']))
 ctor=' public '+ctrl+'(ApiResponses responses, '+', '.join(svc+' '+svc[0].lower()+svc[1:] for svc in sorted(c['services']))+') {\n  this.responses=responses;\n'+''.join('  this.'+svc[0].lower()+svc[1:]+'='+svc[0].lower()+svc[1:]+';\n' for svc in sorted(c['services']))+' }\n'
 # AuthController also declares AUTH-01 below with its dedicated service.
 extra=''
 if ctrl=='AuthController':
  extra=' @ApiContract(id="AUTH-01")\n @GetMapping("/auth/csrf")\n public ApiResponse<Views.Csrf> csrf(org.springframework.security.web.csrf.CsrfToken token) { return responses.success(new Views.Csrf(token.getToken(),token.getHeaderName())); }\n'
 write('controller/'+ctrl+'.java','package com.campus.ticketing.controller;\n'+imports+'import com.campus.ticketing.service.*;\n\n@RestController\n@RequestMapping("/api")\n@Validated\npublic class '+ctrl+' {\n'+fields+ctor+extra+''.join(c['methods'])+'}\n')
contract=['# Java 请求、响应及接口落点', '', '与 api.md 89项HTTP接口逐一对照。AUTH-01为真实CSRF基础设施，其余88项通过安全与语法检查后由Service明确501；合法认证目前仅测试注入，未实现数据库会话。不得切换前端HTTP后声称可完成业务。', '',
'Requests（写入）、Queries（查询）、Views（响应）使用独立类型；SessionPrincipal来自服务端身份。UUID路径参数强类型；无任意Map业务请求。以下类型位于对应dto包。`-`表示无参数。', '',
'| 接口 | 方法 | Requests内部类型 | Queries内部类型 | 返回data类型 |','| --- | --- | --- | --- | --- |',
'| AUTH-01 | `AuthController.csrf` | `-` | `-` | `Views.Csrf` |']+rows+['','## 校验分工','','当前实施：必填/类型/UUID、未知JSON字段、查询白名单、分页界限、整数角、Unicode码点长度、时区/小时分钟粒度、PATCH缺失/null区分及通用幂等键格式。', '',
'待核心阶段实施：对象归属、版本比较、数组ID/名称去重、跨字段时序/容量、非空PATCH实际修改、发布完整性、深分页和闭区间组合、跨表关联、状态和资格、请求指纹与持久幂等。这些不能只靠DTO注解验证，不宣称已完成。', '',
'DTO中的nullable和可省略规则以api.md为准；强改密/检票员投影使用sealed接口限制可返回类型。持久实体不作为API响应。所有时间输出+08:00，所有金额Long整数角。创建/重放/异步受理的201/200/202由核心Service结果元数据驱动，当前501阶段不会返回虚构成功。']
(D/'backend-contracts.md').write_text('\n'.join(contract)+'\n',encoding='utf8')
print('Prepared 89 explicit business routes, request/query/response types and typed service stubs.')
