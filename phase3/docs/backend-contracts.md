# Java 请求、响应及接口落点

与 api.md 89项HTTP接口逐一对照。AUTH-01为真实CSRF基础设施，其余88项已接入业务用例及MySQL DAO。数据库会话代码已实现，默认未连接；无存储时503，完整HTTP业务尚未联调。

Requests（写入）、Queries（查询）、Views（响应）使用独立类型；RequestContext包含服务端SessionPrincipal、幂等键、trace和可信TCP远端地址。UUID路径参数强类型；无任意Map业务请求。以下类型位于对应dto包。`-`表示无参数。

| 接口 | 方法 | Requests内部类型 | Queries内部类型 | 返回data类型 |
| --- | --- | --- | --- | --- |
| AUTH-01 | `AuthController.csrf` | `-` | `-` | `Views.Csrf` |
| AUTH-02 | `AuthController.login` | `Login` | `-` | `Views.Identity` |
| AUTH-03 | `AuthController.register` | `Register` | `-` | `Views.Registered` |
| AUTH-04 | `AuthController.requestRecovery` | `Recovery` | `-` | `Views.RecoveryReceipt` |
| AUTH-05 | `AuthController.logout` | `Empty` | `-` | `Views.LoggedOut` |
| ME-01 | `MeAccountController.currentUser` | `-` | `-` | `Views.Identity` |
| ME-02 | `MeAccountController.updateProfile` | `Profile` | `-` | `Mutation<Views.UserView>` |
| ME-03 | `MeAccountController.changePassword` | `Password` | `-` | `Views.ChangedPassword` |
| ME-04 | `MeAccountController.closureEligibility` | `-` | `-` | `Views.ClosureEligibility` |
| ME-05 | `MeAccountController.closeAccount` | `Closure` | `-` | `Views.ClosedAccount` |
| PUB-01 | `PublicCatalogController.listCategories` | `-` | `Categories` | `List<Views.Category>` |
| PUB-02 | `PublicEventController.listSessions` | `-` | `PublicSessions` | `PageResult<Views.SessionSearchRow>` |
| PUB-03 | `PublicEventController.eventDetail` | `-` | `-` | `Views.EventPublic` |
| PUB-04 | `PublicEventController.publicPoster` | `-` | `-` | `Resource` |
| ME-06 | `RegistrationController.register` | `Registration` | `-` | `Views.RegistrationResult` |
| ME-07 | `OrderController.listMyOrders` | `-` | `Orders` | `PageResult<Views.OrderView>` |
| ME-08 | `OrderController.orderDetail` | `-` | `-` | `Views.OrderView` |
| ME-09 | `RegistrationController.findByRequest` | `-` | `-` | `Views.RegistrationResult` |
| ME-10 | `OrderController.cancel` | `Cancel` | `-` | `Mutation<Views.OrderView>` |
| ME-11 | `OrderController.queuePosition` | `-` | `-` | `Views.QueueView` |
| ME-12 | `OrderController.confirmPromotion` | `Empty` | `-` | `Mutation<Views.OrderView>` |
| ME-13 | `PaymentController.pay` | `Attempt` | `-` | `Views.PaymentResult` |
| ME-14 | `PaymentController.paymentHistory` | `-` | `-` | `Views.PaymentAudit` |
| ME-15 | `RefundController.requestRefund` | `Refund` | `-` | `Views.RefundResult` |
| ME-16 | `RefundController.refundDetail` | `-` | `-` | `Views.MyRefundDetail` |
| ME-17 | `RefundController.retryRefund` | `Attempt` | `-` | `Views.RefundResult` |
| ME-18 | `RefundController.revokeRequest` | `Empty` | `-` | `Mutation<Views.RefundRequest>` |
| ME-19 | `TicketController.ticketDetail` | `-` | `-` | `Views.TicketView` |
| ME-20 | `TicketController.downloadText` | `-` | `-` | `Resource` |
| ORG-01 | `OrganizerEventController.listMine` | `-` | `Events` | `PageResult<Views.EventManage>` |
| ORG-02 | `OrganizerEventController.createDraft` | `Empty` | `-` | `Views.EventManage` |
| ORG-03 | `OrganizerEventController.managementDetail` | `-` | `-` | `Views.EventManage` |
| ORG-04 | `OrganizerEventController.saveDraft` | `Draft` | `-` | `Mutation<Views.EventManage>` |
| ORG-05 | `OrganizerEventController.replacePoster` | `PosterUpload` | `-` | `Mutation<Views.EventManage>` |
| ORG-06 | `OrganizerEventController.removePoster` | `-` | `Version` | `Mutation<Views.EventManage>` |
| ORG-07 | `OrganizerEventController.submitReview` | `Version` | `-` | `Mutation<Views.EventManage>` |
| ORG-08 | `OrganizerEventController.withdrawReview` | `Version` | `-` | `Mutation<Views.EventManage>` |
| ORG-09 | `OrganizerEventController.updatePublishedSettings` | `PublishedEdit` | `-` | `Mutation<Views.EventManage>` |
| ORG-10 | `OrganizerEventController.cancelEvent` | `VersionReason` | `-` | `Views.TerminationResult` |
| ORG-11 | `OrganizerEventController.settlementProgress` | `-` | `-` | `Views.SettlementProgress` |
| CHK-01 | `CheckerController.context` | `-` | `-` | `Views.CheckerContext` |
| CHK-02 | `CheckerController.previewTicket` | `Ticket` | `-` | `Views.TicketPreview` |
| CHK-03 | `CheckerController.confirmCheckIn` | `Ticket` | `-` | `Views.CheckInResult` |
| CHK-04 | `CheckerController.reconcileAbsence` | `Empty` | `-` | `Views.Reconciliation` |
| MGT-01 | `AttendeeController.listAttendees` | `-` | `Attendees` | `PageResult<Views.AttendeeRow>` |
| MGT-02 | `ExportController.createExport` | `Export` | `-` | `Views.ExportView` |
| MGT-03 | `ExportController.exportStatus` | `-` | `-` | `Views.ExportView` |
| MGT-04 | `ExportController.downloadCsv` | `-` | `-` | `Resource` |
| MGT-05 | `StatisticsController.liveCounts` | `-` | `Session` | `Views.Counts` |
| MGT-06 | `StatisticsController.statistics` | `-` | `Session` | `Views.StatisticsProjection` |
| MGT-07 | `StatisticsController.paymentSummary` | `-` | `Session` | `Views.PaymentSummary` |
| MGT-08 | `PosterController.managementPoster` | `-` | `-` | `Resource` |
| ME-21 | `ReportController.createReport` | `Report` | `-` | `Views.ReportView` |
| ME-22 | `ReportController.listMine` | `-` | `Reports` | `PageResult<Views.ReportView>` |
| ME-23 | `ReportController.reportDetail` | `-` | `-` | `Views.ReportView` |
| ME-24 | `MessageController.listMine` | `-` | `Messages` | `PageResult<Views.MessageView>` |
| ME-25 | `MessageController.messageDetail` | `-` | `-` | `Views.MessageView` |
| ME-26 | `MessageController.markRead` | `Empty` | `-` | `Mutation<Views.MessageView>` |
| ADM-01 | `AdminAccountController.listAccounts` | `-` | `Accounts` | `PageResult<Views.ManagedAccount>` |
| ADM-02 | `AdminAccountController.createAccount` | `CreateAccount` | `-` | `Views.ManagedAccount` |
| ADM-03 | `AdminAccountController.updateAccount` | `UpdateAccount` | `-` | `Mutation<Views.ManagedAccount>` |
| ADM-04 | `AdminAccountController.deleteAccount` | `-` | `Version` | `Mutation<Views.ManagedAccount>` |
| ADM-05 | `AdminAccountController.resetPassword` | `ResetPassword` | `-` | `Views.ResetPassword` |
| ADM-06 | `AdminAccountController.listRecoveries` | `-` | `Recoveries` | `PageResult<Views.RecoveryView>` |
| ADM-07 | `AdminAccountController.resolveRecovery` | `ResolveRecovery` | `-` | `Views.RecoveryView` |
| ADM-08 | `AdminReviewController.reviewQueue` | `-` | `Reviews` | `Views.ReviewQueue` |
| ADM-09 | `AdminReviewController.eventDetail` | `-` | `-` | `Views.EventManage` |
| ADM-10 | `AdminReviewController.approve` | `Version` | `-` | `Views.ReviewResult` |
| ADM-11 | `AdminReviewController.reject` | `VersionReason` | `-` | `Views.ReviewResult` |
| ADM-12 | `AdminCategoryController.listCategories` | `-` | `CategoryAdmin` | `PageResult<Views.Category>` |
| ADM-13 | `AdminCategoryController.createCategory` | `CreateCategory` | `-` | `Views.Category` |
| ADM-14 | `AdminCategoryController.updateCategory` | `UpdateCategory` | `-` | `Mutation<Views.Category>` |
| ADM-15 | `AdminTerminationController.takedown` | `Takedown` | `-` | `Views.TerminationResult` |
| ADM-16 | `AdminFinanceController.listPayments` | `-` | `Payments` | `PageResult<Views.PaymentAttempt>` |
| ADM-17 | `AdminFinanceController.refundRequestDetail` | `-` | `-` | `Views.AdminRefundDetail` |
| ADM-18 | `AdminFinanceController.retryAuthorizedRefund` | `Refund` | `-` | `Views.RefundResult` |
| ADM-19 | `AdminReportController.listReports` | `-` | `AdminReports` | `PageResult<Views.ReportAdminRow>` |
| ADM-20 | `AdminReportController.investigate` | `Investigation` | `-` | `Views.InvestigationResult` |
| ADM-21 | `AdminReportController.investigationDetail` | `-` | `-` | `Views.InvestigationDetail` |
| ADM-22 | `AdminRestrictionController.candidates` | `-` | `Candidates` | `PageResult<Views.RestrictionCandidate>` |
| ADM-23 | `AdminRestrictionController.restrictRegistration` | `Restriction` | `-` | `Views.RestrictionView` |
| ADM-24 | `AdminAuditController.logs` | `-` | `Audit` | `PageResult<Views.AuditLog>` |
| ADM-25 | `AdminAuditController.singleObject` | `-` | `-` | `Views.AuditObject` |
| ADM-26 | `AdminJobController.listJobs` | `-` | `Jobs` | `PageResult<Views.JobView>` |
| ADM-27 | `AdminJobController.jobDetail` | `-` | `-` | `Views.JobView` |
| ADM-28 | `AdminReviewController.listEvents` | `-` | `AdminEvents` | `PageResult<Views.EventManage>` |
| ADM-29 | `AdminRestrictionController.candidateEvidence` | `-` | `Candidate` | `Views.RestrictionEvidence` |
| ADM-30 | `AdminReportController.listInvestigations` | `-` | `Investigations` | `PageResult<Views.InvestigationResult>` |
| ADM-31 | `AdminFinanceController.listRefundRequests` | `-` | `Refunds` | `PageResult<Views.RefundRequest>` |

## 校验分工

当前实施：必填/类型/UUID、未知JSON字段、查询白名单、分页界限、整数角、Unicode码点长度、时区/小时分钟粒度、PATCH缺失/null区分及通用幂等键格式。

已在service/core中编写对象归属、版本比较、数组ID/名称、时序/容量、发布完整性、深分页/闭区间、跨表关联、状态/资格和持久幂等初版；其真实SQL事务、并发与异常恢复须接库验收。

DTO中的nullable和可省略规则以api.md为准；强改密/检票员投影使用sealed接口限制可返回类型。持久实体不作为API响应。所有时间输出+08:00，所有金额Long整数角。创建/重放/异步受理的201/200/202由核心Service结果元数据驱动，当前未接库时不会返回虚构成功。
