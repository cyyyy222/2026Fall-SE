> 2026-11-03当前实现：可选邮箱、验证码找回和个人资料新号验证码换绑见[最新交付](account-recovery-update.md)。本机MySQL/Redis已连接；下表原“待接库验证”标签保留为早期记录，不代表当前未接库；完整并发/性能验收仍待完成。

# 后端接口三层实施表（供审阅）

版本：v0.6 · 日期：2026-11-03 · 状态：93项业务路由已注册；本机MySQL/Redis接通，账号与验证码调整见[最新说明](account-recovery-update.md)，全量性能与恢复尚未验收。

本表将[api.md](api.md)的93项业务HTTP接口逐项分配给Controller方法、Service接口和DAO。路径均需加`/api`；请求/响应/权限仍以api.md为准，不在此复制成另一套协议。框架边界见[backend-plan.md](backend-plan.md)。

当前AUTH-01为CSRF基础设施，其余92项已实现业务初版并接入参数化DAO；本机依赖已连接，依赖不可用时503。表中“已实现/待接库验证”不等于真实业务或并发验收通过。见[业务交付](backend-implementation.md)。

Controller方法名对应已创建代码，可在不改变HTTP契约的前提下按Java规范调整。表中的Service是Controller的门面；例如EventService的终止/海报方法会委托EventTerminationService/PosterService，FinanceAuditService重试委托RefundService，不在Controller跨层执行。所有写用例另依赖共用审计、消息、幂等及事务策略，本表DAO列是主业务数据，不是完整依赖列表。

| API | 方法与路径 | Controller方法 | Service接口 | 主要DAO/基础组件 | 框架阶段 |
| --- | --- | --- | --- | --- | --- |
| AUTH-01 | GET `/auth/csrf` | `AuthController.csrf` | `CsrfTokenService` | Spring CSRF TokenRepository | CSRF可离线使用 |
| AUTH-02 | POST `/auth/login` | `AuthController.login` | `AuthService` | UserDao / LoginSessionDao / RecoveryDao | 已实现/待接库验证 |
| AUTH-03 | POST `/auth/register` | `AuthController.register` | `AuthService` | UserDao / LoginSessionDao / RecoveryDao | 已实现/待接库验证 |
| AUTH-04 | POST `/auth/recoveries` | `AuthController.requestRecovery` | `AuthService` | UserDao / LoginSessionDao / RecoveryDao | 已实现/待接库验证 |
| AUTH-05 | POST `/auth/logout` | `AuthController.logout` | `AuthService` | UserDao / LoginSessionDao / RecoveryDao | 已实现/待接库验证 |
| AUTH-06 | POST `/auth/phone-codes` | `AuthController.phoneCode` | `AuthService` → `PhoneCodeUseCases` | RedisTransientDao / RedisKeyFactory / RateLimitService | 本地控制台交付，真实联调通过 |
| AUTH-07 | POST `/auth/phone-login` | `AuthController.phoneLogin` | `AuthService` → `PhoneCodeUseCases` / `IdentityUseCases` | Redis挑战 / JdbcRecordStore用户与会话 | 真实联调通过 |
| AUTH-08 | POST `/auth/password-reset` | `AuthController.phonePasswordReset` | `AuthService` → `PhoneCodeUseCases` / `IdentityUseCases` | Redis挑战 / 用户与会话DAO | 真实HTTP通过 |
| ME-27 | PATCH `/me/phone` | `MeAccountController.changePhone` | `AccountService` → `IdentityUseCases` | 新号验证 / 用户、会话与幂等DAO | 真实HTTP通过，仅新号验证 |
| ME-01 | GET `/me` | `MeAccountController.currentUser` | `AccountService` | UserDao / LoginSessionDao / PhoneIdentityDao | 已实现/待接库验证 |
| ME-02 | PATCH `/me/profile` | `MeAccountController.updateProfile` | `AccountService` | UserDao / LoginSessionDao / PhoneIdentityDao | 已实现/待接库验证 |
| ME-03 | POST `/me/password` | `MeAccountController.changePassword` | `AccountService` | UserDao / LoginSessionDao / PhoneIdentityDao | 已实现/待接库验证 |
| ME-04 | GET `/me/closure-eligibility` | `MeAccountController.closureEligibility` | `AccountService` | UserDao / LoginSessionDao / PhoneIdentityDao | 已实现/待接库验证 |
| ME-05 | POST `/me/closure` | `MeAccountController.closeAccount` | `AccountService` | UserDao / LoginSessionDao / PhoneIdentityDao | 已实现/待接库验证 |
| PUB-01 | GET `/public/categories` | `PublicCatalogController.listCategories` | `CategoryQueryService` | CategoryDao | 已实现/待接库验证 |
| PUB-02 | GET `/public/sessions` | `PublicEventController.listSessions` | `PublicEventService` | EventDao / EventSessionDao / TicketTypeDao | 已实现/待接库验证 |
| PUB-03 | GET `/public/events/{eventId}` | `PublicEventController.eventDetail` | `PublicEventService` | EventDao / EventSessionDao / TicketTypeDao | 已实现/待接库验证 |
| PUB-04 | GET `/public/events/{eventId}/poster` | `PublicEventController.publicPoster` | `PublicEventService` | EventDao / EventSessionDao / TicketTypeDao | 已实现/待接库验证 |
| ME-06 | POST `/me/registrations` | `RegistrationController.register` | `RegistrationService` | RegistrationRequestDao / OrderDao / TicketTypeDao | 已实现/待接库验证 |
| ME-07 | GET `/me/orders` | `OrderController.listMyOrders` | `OrderService` | OrderDao / TicketTypeDao / MessageDao | 已实现/待接库验证 |
| ME-08 | GET `/me/orders/{orderId}` | `OrderController.orderDetail` | `OrderService` | OrderDao / TicketTypeDao / MessageDao | 已实现/待接库验证 |
| ME-09 | GET `/me/registration-requests/{requestId}` | `RegistrationController.findByRequest` | `RegistrationService` | RegistrationRequestDao / OrderDao / TicketTypeDao | 已实现/待接库验证 |
| ME-10 | POST `/me/orders/{orderId}/cancel` | `OrderController.cancel` | `OrderService` | OrderDao / TicketTypeDao / MessageDao | 已实现/待接库验证 |
| ME-11 | GET `/me/orders/{orderId}/queue` | `OrderController.queuePosition` | `OrderService` | OrderDao / TicketTypeDao / MessageDao | 已实现/待接库验证 |
| ME-12 | POST `/me/orders/{orderId}/promotion-confirmation` | `OrderController.confirmPromotion` | `OrderService` | OrderDao / TicketTypeDao / MessageDao | 已实现/待接库验证 |
| ME-13 | POST `/me/orders/{orderId}/payment-attempts` | `PaymentController.pay` | `PaymentService` | PaymentAttemptDao / OrderDao / TicketDao | 已实现/待接库验证 |
| ME-14 | GET `/me/orders/{orderId}/payments` | `PaymentController.paymentHistory` | `PaymentService` | PaymentAttemptDao / OrderDao / TicketDao | 已实现/待接库验证 |
| ME-15 | POST `/me/orders/{orderId}/refund-requests` | `RefundController.requestRefund` | `RefundService` | RefundRequestDao / RefundAttemptDao / OrderDao | 已实现/待接库验证 |
| ME-16 | GET `/me/orders/{orderId}/refund-requests/{requestId}` | `RefundController.refundDetail` | `RefundService` | RefundRequestDao / RefundAttemptDao / OrderDao | 已实现/待接库验证 |
| ME-17 | POST `/me/orders/{orderId}/refund-requests/{requestId}/attempts` | `RefundController.retryRefund` | `RefundService` | RefundRequestDao / RefundAttemptDao / OrderDao | 已实现/待接库验证 |
| ME-18 | POST `/me/orders/{orderId}/refund-requests/{requestId}/revocation` | `RefundController.revokeRequest` | `RefundService` | RefundRequestDao / RefundAttemptDao / OrderDao | 已实现/待接库验证 |
| ME-19 | GET `/me/tickets/{ticketId}` | `TicketController.ticketDetail` | `TicketService` | TicketDao / OrderDao / EventSessionDao | 已实现/待接库验证 |
| ME-20 | GET `/me/tickets/{ticketId}/download` | `TicketController.downloadText` | `TicketService` | TicketDao / OrderDao / EventSessionDao | 已实现/待接库验证 |
| ORG-01 | GET `/organizer/events` | `OrganizerEventController.listMine` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| ORG-02 | POST `/organizer/events` | `OrganizerEventController.createDraft` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| ORG-03 | GET `/organizer/events/{eventId}` | `OrganizerEventController.managementDetail` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| ORG-04 | PUT `/organizer/events/{eventId}/draft` | `OrganizerEventController.saveDraft` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| ORG-05 | PUT `/organizer/events/{eventId}/poster` | `OrganizerEventController.replacePoster` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| ORG-06 | DELETE `/organizer/events/{eventId}/poster` | `OrganizerEventController.removePoster` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| ORG-07 | POST `/organizer/events/{eventId}/submission` | `OrganizerEventController.submitReview` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| ORG-08 | POST `/organizer/events/{eventId}/withdrawal` | `OrganizerEventController.withdrawReview` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| ORG-09 | PATCH `/organizer/events/{eventId}/published-settings` | `OrganizerEventController.updatePublishedSettings` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| ORG-10 | POST `/organizer/events/{eventId}/cancellation` | `OrganizerEventController.cancelEvent` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| ORG-11 | GET `/organizer/events/{eventId}/settlement` | `OrganizerEventController.settlementProgress` | `EventService` | EventDao / EventSessionDao / TicketTypeDao / JobDao | 已实现/待接库验证 |
| CHK-01 | GET `/checker/context` | `CheckerController.context` | `CheckInService` | CheckerAssignmentDao / TicketDao / CheckInDao | 已实现/待接库验证 |
| CHK-02 | POST `/checker/ticket-previews` | `CheckerController.previewTicket` | `CheckInService` | CheckerAssignmentDao / TicketDao / CheckInDao | 已实现/待接库验证 |
| CHK-03 | POST `/checker/checkins` | `CheckerController.confirmCheckIn` | `CheckInService` | CheckerAssignmentDao / TicketDao / CheckInDao | 已实现/待接库验证 |
| CHK-04 | POST `/checker/sessions/{sessionId}/absence-reconciliation` | `CheckerController.reconcileAbsence` | `CheckInService` | CheckerAssignmentDao / TicketDao / CheckInDao | 已实现/待接库验证 |
| MGT-01 | GET `/management/events/{eventId}/attendees` | `AttendeeController.listAttendees` | `AttendeeService` | AttendeeQueryDao | 已实现/待接库验证 |
| MGT-02 | POST `/management/events/{eventId}/exports` | `ExportController.createExport` | `ExportService` | ExportDao / AttendeeQueryDao / JobDao | 已实现/待接库验证 |
| MGT-03 | GET `/management/exports/{exportId}` | `ExportController.exportStatus` | `ExportService` | ExportDao / AttendeeQueryDao / JobDao | 已实现/待接库验证 |
| MGT-04 | GET `/management/exports/{exportId}/download` | `ExportController.downloadCsv` | `ExportService` | ExportDao / AttendeeQueryDao / JobDao | 已实现/待接库验证 |
| MGT-05 | GET `/management/events/{eventId}/live-counts` | `StatisticsController.liveCounts` | `StatisticsService` | StatisticsSnapshotDao / OrderDao / PaymentAttemptDao | 已实现/待接库验证 |
| MGT-06 | GET `/management/events/{eventId}/statistics` | `StatisticsController.statistics` | `StatisticsService` | StatisticsSnapshotDao / OrderDao / PaymentAttemptDao | 已实现/待接库验证 |
| MGT-07 | GET `/management/events/{eventId}/payment-summary` | `StatisticsController.paymentSummary` | `StatisticsService` | StatisticsSnapshotDao / OrderDao / PaymentAttemptDao | 已实现/待接库验证 |
| MGT-08 | GET `/management/events/{eventId}/poster` | `PosterController.managementPoster` | `PosterService` | EventDao / FileMetadataDao | 已实现/待接库验证 |
| ME-21 | POST `/me/reports` | `ReportController.createReport` | `ReportService` | ReportDao / EventDao / OrderDao | 已实现/待接库验证 |
| ME-22 | GET `/me/reports` | `ReportController.listMine` | `ReportService` | ReportDao / EventDao / OrderDao | 已实现/待接库验证 |
| ME-23 | GET `/me/reports/{reportId}` | `ReportController.reportDetail` | `ReportService` | ReportDao / EventDao / OrderDao | 已实现/待接库验证 |
| ME-24 | GET `/me/messages` | `MessageController.listMine` | `MessageService` | MessageDao | 已实现/待接库验证 |
| ME-25 | GET `/me/messages/{messageId}` | `MessageController.messageDetail` | `MessageService` | MessageDao | 已实现/待接库验证 |
| ME-26 | POST `/me/messages/{messageId}/read` | `MessageController.markRead` | `MessageService` | MessageDao | 已实现/待接库验证 |
| ADM-01 | GET `/admin/accounts` | `AdminAccountController.listAccounts` | `AccountAdminService` | UserDao / CheckerAssignmentDao / RecoveryDao | 已实现/待接库验证 |
| ADM-02 | POST `/admin/accounts` | `AdminAccountController.createAccount` | `AccountAdminService` | UserDao / CheckerAssignmentDao / RecoveryDao | 已实现/待接库验证 |
| ADM-03 | PATCH `/admin/accounts/{accountId}` | `AdminAccountController.updateAccount` | `AccountAdminService` | UserDao / CheckerAssignmentDao / RecoveryDao | 已实现/待接库验证 |
| ADM-04 | DELETE `/admin/accounts/{accountId}` | `AdminAccountController.deleteAccount` | `AccountAdminService` | UserDao / CheckerAssignmentDao / RecoveryDao | 已实现/待接库验证 |
| ADM-05 | POST `/admin/accounts/{accountId}/password-reset` | `AdminAccountController.resetPassword` | `AccountAdminService` | UserDao / CheckerAssignmentDao / RecoveryDao | 已实现/待接库验证 |
| ADM-06 | GET `/admin/recoveries` | `AdminAccountController.listRecoveries` | `AccountAdminService` | UserDao / CheckerAssignmentDao / RecoveryDao | 已实现/待接库验证 |
| ADM-07 | POST `/admin/recoveries/{requestId}/resolution` | `AdminAccountController.resolveRecovery` | `AccountAdminService` | UserDao / CheckerAssignmentDao / RecoveryDao | 已实现/待接库验证 |
| ADM-08 | GET `/admin/reviews` | `AdminReviewController.reviewQueue` | `EventReviewService` | EventReviewDao / EventDao / PublicationQuotaDao | 已实现/待接库验证 |
| ADM-09 | GET `/admin/events/{eventId}` | `AdminReviewController.eventDetail` | `EventReviewService` | EventReviewDao / EventDao / PublicationQuotaDao | 已实现/待接库验证 |
| ADM-10 | POST `/admin/events/{eventId}/approval` | `AdminReviewController.approve` | `EventReviewService` | EventReviewDao / EventDao / PublicationQuotaDao | 已实现/待接库验证 |
| ADM-11 | POST `/admin/events/{eventId}/rejection` | `AdminReviewController.reject` | `EventReviewService` | EventReviewDao / EventDao / PublicationQuotaDao | 已实现/待接库验证 |
| ADM-12 | GET `/admin/categories` | `AdminCategoryController.listCategories` | `CategoryAdminService` | CategoryDao | 已实现/待接库验证 |
| ADM-13 | POST `/admin/categories` | `AdminCategoryController.createCategory` | `CategoryAdminService` | CategoryDao | 已实现/待接库验证 |
| ADM-14 | PATCH `/admin/categories/{categoryId}` | `AdminCategoryController.updateCategory` | `CategoryAdminService` | CategoryDao | 已实现/待接库验证 |
| ADM-15 | POST `/admin/events/{eventId}/takedown` | `AdminTerminationController.takedown` | `EventTerminationService` | EventDao / InvestigationDao / JobDao | 已实现/待接库验证 |
| ADM-16 | GET `/admin/payments` | `AdminFinanceController.listPayments` | `FinanceAuditService` | PaymentAttemptDao / RefundRequestDao / RefundAttemptDao | 已实现/待接库验证 |
| ADM-17 | GET `/admin/refund-requests/{requestId}` | `AdminFinanceController.refundRequestDetail` | `FinanceAuditService` | PaymentAttemptDao / RefundRequestDao / RefundAttemptDao | 已实现/待接库验证 |
| ADM-18 | POST `/admin/refund-requests/{requestId}/attempts` | `AdminFinanceController.retryAuthorizedRefund` | `FinanceAuditService` | PaymentAttemptDao / RefundRequestDao / RefundAttemptDao | 已实现/待接库验证 |
| ADM-19 | GET `/admin/reports` | `AdminReportController.listReports` | `InvestigationService` | ReportDao / InvestigationDao / BehaviorFactDao | 已实现/待接库验证 |
| ADM-20 | POST `/admin/investigations` | `AdminReportController.investigate` | `InvestigationService` | ReportDao / InvestigationDao / BehaviorFactDao | 已实现/待接库验证 |
| ADM-21 | GET `/admin/investigations/{investigationId}` | `AdminReportController.investigationDetail` | `InvestigationService` | ReportDao / InvestigationDao / BehaviorFactDao | 已实现/待接库验证 |
| ADM-22 | GET `/admin/restriction-candidates` | `AdminRestrictionController.candidates` | `RestrictionService` | RestrictionDao / RestrictionFactDao / PhoneIdentityDao | 已实现/待接库验证 |
| ADM-23 | POST `/admin/registration-restrictions` | `AdminRestrictionController.restrictRegistration` | `RestrictionService` | RestrictionDao / RestrictionFactDao / PhoneIdentityDao | 已实现/待接库验证 |
| ADM-24 | GET `/admin/audit-logs` | `AdminAuditController.logs` | `AuditQueryService` | AuditDao / BusinessAuditQueryDao | 已实现/待接库验证 |
| ADM-25 | GET `/admin/audit-objects/{objectType}/{objectId}` | `AdminAuditController.singleObject` | `AuditQueryService` | AuditDao / BusinessAuditQueryDao | 已实现/待接库验证 |
| ADM-26 | GET `/admin/jobs` | `AdminJobController.listJobs` | `JobQueryService` | JobDao | 已实现/待接库验证 |
| ADM-27 | GET `/admin/jobs/{jobId}` | `AdminJobController.jobDetail` | `JobQueryService` | JobDao | 已实现/待接库验证 |
| ADM-28 | GET `/admin/events` | `AdminReviewController.listEvents` | `EventReviewService` | EventReviewDao / EventDao / PublicationQuotaDao | 已实现/待接库验证 |
| ADM-29 | GET `/admin/restriction-candidates/{phoneIdentityId}` | `AdminRestrictionController.candidateEvidence` | `RestrictionService` | RestrictionDao / RestrictionFactDao / PhoneIdentityDao | 已实现/待接库验证 |
| ADM-30 | GET `/admin/investigations` | `AdminReportController.listInvestigations` | `InvestigationService` | ReportDao / InvestigationDao / BehaviorFactDao | 已实现/待接库验证 |
| ADM-31 | GET `/admin/refund-requests` | `AdminFinanceController.listRefundRequests` | `FinanceAuditService` | PaymentAttemptDao / RefundRequestDao / RefundAttemptDao | 已实现/待接库验证 |

## 新增工程接口（不计入89项业务接口）

| API | 方法与路径 | 三层落点 | 框架阶段 |
| --- | --- | --- | --- |
| SYS-01 | GET `/api/system/health` | SystemController.health → SystemStatusService → MySQL/Redis轻量连接检查 | 已实现诊断；默认503 NOT_READY，不创建连接 |

## 调度入口（不是HTTP接口）

| 内部操作 | 调度器调用的Service | 持久化主责 |
| --- | --- | --- |
| JOB-01 | OrderExpiryService.expireDueOrders | OrderDao / JobDao |
| JOB-02 | WaitlistService.promoteEligible | OrderDao / TicketTypeDao / MessageDao |
| JOB-03 | NaturalSettlementService.settleDueSessions | OrderDao / TicketDao / StatisticsSnapshotDao / JobDao |
| JOB-04 | TerminationSettlementService.processPending | TerminationFactDao / JobItemDao / RefundRequestDao / JobDao |
| JOB-05 | DataRetentionService.cleanExpired | ExportDao / MessageDao / AuditDao / UserDao |
| JOB-06 | RestrictionService.reconcileDue | RestrictionDao / RestrictionFactDao / MessageDao |
| JOB-07 | ExportService.generatePending | ExportDao / AttendeeQueryDao / JobDao |

已实现调度Service和持久执行器，默认jobs.enabled=false，不用Redis锁/内存计时器替代MySQL持久待办，也不启动空执行器把任务标成成功。接库并完成结构/任务验收后再显式启用调度。

计数校验：89项业务HTTP映射，其中1项CSRF可离线使用、88项业务初版已接入用例与DAO，待接库验证；另有1项工程健康接口与7项非HTTP内部任务。此计数不表示业务验收通过；默认健康503 NOT_READY。

SYS-01为工程诊断特例：依赖未就绪时503 SERVICE_UNAVAILABLE仍在data中保留最小组件状态；业务失败接口仍按api.md返回data=null。
