# 前后端接口约定

版本：v0.6（可选邮箱、验证码找回、详细申诉与手机号换绑） · 日期：2026-11-03 · Base URL：`/api`。

本文是开发契约。当前93项Java业务路由均已接入具体Service用例；本机已连接MySQL/Redis，初始化真实演示数据并将前端切为HTTP，后台任务已获授权启用。真实垂直联调与账号见[HTTP演示说明](http-demo.md)，完整业务与性能验收尚未完成。默认无profile仍离线，依赖存储的请求返回503，不返回原501占位。业务来源见 [项目总纲](project.md) 与 [53条需求追踪](requirements-traceability.md)。接口编号稳定，前端页面、后端模块和验收通过编号引用；JOB-* 是内部操作，不是 HTTP 接口。后端为Java三层+MySQL+Redis；Java落点及默认离线边界见[后端方案](backend-plan.md)和[接口实施表](backend-endpoints.md)；初版实现见[业务交付](backend-implementation.md)。

> 2026-11-03最新可选邮箱、验证码找回、人工申诉和手机号换绑见[账号恢复调整](account-recovery-update.md)；此前验证码/账号分类流程见[账号与登录调整](account-auth-update.md)，替代phase1旧版“手机号全局唯一/不接验证码”的口径。

## 1. 通用协议

### 1.1 请求与类型

- JSON 请求/响应使用 UTF-8、camelCase；除上传/下载外 Content-Type 为 `application/json`。
- ID 为 UUID 字符串，票id也是 UUID 字符串；例子中的 `eventId` 等占位符不代表实际 UUID。
- 所有时间输入须为带有效时区偏移的 ISO 8601 字符串（推荐 `+08:00`；服务端按绝对时刻处理并统一返回 `+08:00`），秒和毫秒必须为 0。服务端内部使用可注入 Clock；前端不能提交 now/付款金额/库存/状态强行覆盖服务器事实。
- 场次开始、结束、放票、报名截止均精确到分钟；业务处理时间保留秒及可选毫秒；倒计时不向整分钟取整。
- 金额统一整数角：单票`priceJiao`及单订单`amountJiao`范围0～9999990；汇总金额不套用单价上限，后端使用64位整数，返回JSON数值须在JavaScript安全整数范围内；前端展示元最多1位小数，不提交浮点元。
- 字段语法分开定义：`field:T?`表示**字段必须出现但值可为null**；`field?:T`表示**请求可以省略，出现时不得为null**；`field?:T?`表示可省略也可显式清空。未标问号的字段必须出现且不得null。查询参数清单中的`name?`仅表示可省略，不接受字符串`null`。响应可空字段始终出现，不随机省略。
- JSON对象只返回DTO声明字段，不返回密码、tokenHash、文件系统绝对路径。特殊最小用户投影使用独立的ForcePasswordUser模型，不用省略UserView必填字段表达。
- 写请求拒绝未声明字段；防止 userId/role/status/price 等注入。查询参数不支持的名称或枚举返回422，不静默忽略拼写错误。
- `PATCH` 使用显式字段更新：省略=不变，只有声明可清空的字段才能用 null 清空。不允许修改的字段即使值没变也拒绝。
- 列表默认 `page=1,pageSize=20`，pageSize≤100，返回 Page<T>。公开查询额外要求 `(page-1)*pageSize+pageSize≤10000`；导出不受此分页上限限制。
- 文本长度按 Unicode code point；trim 后校验。GET 不改变业务状态，已读使用独立写接口。

### 1.2 响应信封

所有 JSON 成功结果：

```json
{
  "code": "OK",
  "message": "操作成功",
  "data": {},
  "traceId": "server-generated-id",
  "serverTime": "2026-11-05T10:00:00+08:00"
}
```

失败：

```json
{
  "code": "REGISTRATION_CLOSED",
  "message": "报名已截止",
  "data": null,
  "errors": [],
  "context": {"orderId": null, "eventId": "...", "deadline": "2026-11-05T10:00:00+08:00"},
  "traceId": "server-generated-id",
  "serverTime": "2026-11-05T10:00:00+08:00"
}
```

`errors` 为 `[{field,code,message}]`，非字段错误为空数组。`context` 仅返回当前账号有权知道的对象和时间；可为 `{}`。前端按 code 分支，不解析 message 文本判断业务。

Page<T>：`{items:T[],page:int,pageSize:int,total:int}`。空查询200且items为空；合法分页无结果不返回404。

二进制下载成功直接返回文件；失败仍用上述 JSON 与对应 HTTP 状态。私有 JSON/下载使用 `Cache-Control: no-store`；错误不得先发部分文件再发JSON。下载前确认响应类型，避免把错误JSON保存成票或CSV。

### 1.3 身份、Cookie、CSRF

| 项 | 约定 |
| --- | --- |
| 登录 | 账密登录由用户名确定角色；手机验证码登录仅P/O且必须选择角色，服务端按手机号+角色查找已存在账号，不能提权 |
| 会话 | Cookie `TICKET_SESSION`，不透明随机值、HttpOnly、SameSite=Lax、Path=/api；HTTPS时Secure，本机HTTP开发例外 |
| 存储与期限 | 只存token哈希；成功登录起固定7天；每个账号仅一个有效会话，第二端登录撤销旧会话 |
| 变更请求 | POST/PUT/PATCH/DELETE均带 `X-CSRF-TOKEN`；AUTH-01先取CSRF，登录后与退出后重取；Cookie随同源请求发送 |
| 登录状态 | 401清本地身份并引导登录；403 FORBIDDEN/OBJECT_FORBIDDEN不清正常会话，ACCOUNT_UNAVAILABLE清本地身份并显示账号不可用，PASSWORD_CHANGE_REQUIRED跳强制改密，CSRF_INVALID重取token；均不伪装404 |
| 强制改密 | 仅AUTH-01、AUTH-05、ME-01最小信息、ME-03可用；其他私有接口403 PASSWORD_CHANGE_REQUIRED |
| 对象授权 | currentUser来自会话；请求不可选择操作者；管理员仅具有逐接口列出的能力 |

开发时Vite代理 `/api` 到Java，交付同源；不依赖跨域通配符。防CSRF和随机会话实现需参考 [Spring Security 官方说明](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)。Cookie名称和安全策略为本项目设计决定。

当前Java从MySQL恢复Cookie会话，不使用HttpSession保存认证上下文。普通连续请求保持当前CSRF有效；登录/退出后重取。已验证同一会话连续提交及支付/退款幂等重放；没有通过关闭CSRF解决连续提交问题。

前端仅对明确的403 CSRF_INVALID重新取令牌后最多重试一次，保留原请求体、requestId/attemptId和Idempotency-Key；不自动重试网络失败、401或其他403。并发获取令牌合并处理，获取请求禁止缓存。过期/撤销/不可用账号的旧会话Cookie会被清理；公开登录、注册、找回可继续各自的校验，私有请求仍按原错误拒绝。数据库故障不会降级为匿名认证。

### 1.4 HTTP与错误码

| HTTP | code | 含义/前端处理 |
| --- | --- | --- |
| 400 | MALFORMED_REQUEST | JSON/请求格式无法解析 |
| 401 | AUTH_REQUIRED / INVALID_CREDENTIALS / SESSION_EXPIRED / SESSION_REVOKED | 未登录、密码错误、固定到期、第二端等撤销；引导重新登录 |
| 403 | FORBIDDEN / OBJECT_FORBIDDEN / ACCOUNT_UNAVAILABLE / PASSWORD_CHANGE_REQUIRED / CSRF_INVALID | 权限、账号、强改密或CSRF；不返回他人业务字段 |
| 404 | RESOURCE_NOT_FOUND | 仅对象确实不存在 |
| 409 | STATE_CONFLICT / VERSION_CONFLICT / IDEMPOTENCY_CONFLICT | 状态改变/编辑覆盖/同键异参；刷新当前对象，不能盲目新键重做 |
| 409 | EVENT_NOT_PUBLIC / EVENT_TERMINATED / SALES_NOT_OPEN / SALE_STOPPED / REGISTRATION_CLOSED / PAYMENT_EXPIRED / PROMOTION_EXPIRED / CHECKIN_WINDOW_CLOSED | 对象存在但不可进行当前业务，显示真实原因 |
| 409 | TICKET_INVALID / CHECKIN_NOT_OPEN / CHECKIN_ENDED | 无效票、尚未到验票窗口、场次已结束；返回不同原因，不能全部显示“票号不存在” |
| 409 | ACTIVE_ORDER_EXISTS / SESSION_TIME_CONFLICT / REGISTRATION_RESTRICTED / PUBLISH_RESTRICTED / CAPACITY_BELOW_OCCUPIED | 资格、冲突、容量不符；不伪造候补成功 |
| 409 | REFUND_NOT_ALLOWED / REFUND_BASIS_INVALID / LAST_CHECKER_REQUIRED / ACCOUNT_CLOSE_BLOCKED | 退款、账号或末位授权限制 |
| 409 | REFUND_REQUEST_EXISTS | 同单存在未关闭的合法USER退款申请，返回本人原依据编号，先查询或处理原申请 |
| 409 | DUPLICATE_REPORT / REPORT_LIMIT_REACHED / RESTRICTION_BASIS_CONSUMED | 举报或处罚事实不能重复使用 |
| 410 | DATA_EXPIRED | 名单/统计/CSV/消息到读取期限；不尝试换管理员入口重建 |
| 413 | FILE_TOO_LARGE | 海报超5,000,000字节 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 非JSON/不支持的图片真实格式 |
| 422 | VALIDATION_FAILED / QUERY_RANGE_EXCEEDED | 字段、时间/数量/金额/深分页非法，显示errors |
| 429 | TOO_MANY_REQUESTS | 仅技术请求限流；不代替业务举报次数限制 |
| 500 | INTERNAL_ERROR | 未预期服务错误，显示traceId并查询既有操作结果 |
| 503 | DATABASE_UNAVAILABLE / DATABASE_BUSY / SERVICE_UNAVAILABLE | 数据库不可用或锁等待耗尽；不能声称成功或失败日志必然已写 |

业务失败但**有意提交状态变更**的情况不使用错误响应掩盖已提交结果：

- 审核通过请求复核失败并退回草稿：200，ReviewResult.outcome=RETURNED_TO_DRAFT。
- 模拟支付执行得到失败：新尝试201/重放200，PaymentResult.attempt.result=FAILURE，保留本次尝试及当前订单；不是接口崩溃。
- 合法退款申请执行失败：新申请/尝试201、重放200，RefundResult.attempt.result=FAILURE，返回已保存的依据与失败尝试；票/订单/库存仍原状态。
- 终止受理：202，返回已生效状态与jobId；结算尚未完成，不展示“全部退款完成”。

所有写接口均可出现上述通用错误；接口表“特有规则”补充特有校验。无资格不产生订单或候补；纯技术异常未受理时按5xx处理。

模拟支付/退款的FAILURE是一个已持久化的业务尝试结果，日志result=FAILURE与该尝试同事务保存；不要因为HTTP为2xx而把它记为成功，也不要再按“事务回滚失败”重复记录一条。真正导致业务事务回滚的错误才使用独立失败日志事务；数据库不可写不承诺该日志可保存。

### 1.5 幂等、版本与重放

| 操作 | 业务键 | 约束 |
| --- | --- | --- |
| 报名 | 当前参与者+body.requestId | 永久映射原订单；同键异参409；旧单取消后重放仍返回旧单，新意图用新requestId |
| 支付 | orderId+body.attemptId | 同次重放不加失败次数；新失败重试用新attemptId；每单最多一次成功 |
| 退款 | 申请创建使用Idempotency-Key；执行使用requestId+attemptId | 每单最多一次成功；同失败尝试重放不重复执行，新尝试保留原合法依据 |
| 签到 | ticketId | 至多一条签到；当前授权有效时重放返回首次时间 |
| 其他写 | 当前账号+稳定接口编号+目标资源ID+Idempotency-Key | 同键异参409；返回该接口规定的原结果与最新可读对象；operationId是服务端生成的操作记录ID，不是接口编号 |
| 消息/内部任务 | eventKey+recipient；taskId+itemId | 同事务唯一约束，重启或任务重放不重复副作用 |

除AUTH-02～08及ME-06/13、CHK-02/03外，其余POST/PUT/PATCH/DELETE要求 `Idempotency-Key: UUID`；ME-06/13用body键，CHK-03用票唯一性，CHK-02只核对不改变业务。AUTH-02～04不提供通用响应重放缓存：登录依会话撤销规则，注册依有效账号唯一约束，找回依待处理请求合并；AUTH-05依会话撤销规则；AUTH-06～08使用验证码挑战、冷却和一次消费，不缓存含会话的响应。匿名注册不同时规定“幂等键必填”和“可选”。会话令牌不得存入可重放响应。强制改密等撤销会话操作仍先鉴权，原会话已撤销则401，前端通过重新登录验证结果。

新增设计：通用写幂等记录最少保留180天；报名映射、支付/退款尝试、签到唯一事实随底层业务持续保留。过期通用幂等记录重放仍受当前状态/唯一/版本限制。新业务意图必须生成新键；通用幂等不是替代所有业务唯一性的机制。

管理修改使用expectedVersion整数（JSON body、multipart字段或DELETE query，以接口表为准，从详情version取得）。服务端写事务内比较，成功version+1；冲突409，前端展示差异后再提交。报名、支付、退款、签到不要求用户提交管理version，统一读取最新事实；避免库存变化导致普通支付误报编辑冲突。检票账号配置另有account.version，发布时实时读授权，不因用户不知道的新授权version而错误覆盖活动内容。

`Mutation<T>`：`{operationId:uuid,operationResult:string,replayed:boolean,current:T}`；operationResult记录原结论，current为当前有权读取的对象。PaymentResult/RefundResult以attempt.result记录原结论，ReviewResult以outcome记录原结论，不额外假设它们有operationResult字段。创建类返回DTO的通用重放仍返回原创建对象ID并投影当前对象，不统一强加Mutation信封；HTTP重放为200，首次创建为201，除明确202的接口外。

幂等查重在最新会话/角色/对象权限校验后、业务时间和状态检查前；**只有确有持久受理记录才是重放**。不同参数、用户或对象不能借旧键绕过条件。业务数据、成功日志、必发消息、操作结果和幂等映射必须同事务提交；不得先提交业务后再写映射。缓存不能绕过7/180天读取期限；到期返回410，不返回旧缓存名单/统计/消息。注销/改密/自停用等撤销自身会话后不绕过鉴权重放。

## 2. 枚举与状态转换

| 字段 | 允许值 |
| --- | --- |
| Role | PARTICIPANT、ORGANIZER、CHECKER、ADMIN |
| EventStatus | DRAFT、PENDING_REVIEW、PUBLISHED、CANCELLED、TAKEN_DOWN、ENDED |
| OrderStatus | WAITLISTED、OFFERED、UNPAID、REGISTERED、CHECKED_IN、COMPLETED、CANCELLED、REFUNDED、ABSENT |
| TicketStatus | VALID、CHECKED、INVALID |
| AttemptResult | SUCCESS、FAILURE |
| RefundSource | USER、PLATFORM_TERMINATION |
| RefundRequestStatus | ACCEPTED、FAILED、SUCCEEDED、REVOKED；此为依据处理状态，不是订单状态 |
| ReportStatus | PENDING、SUBSTANTIATED、UNSUBSTANTIATED |
| RestrictionKind | REGISTER、PUBLISH |
| JobStatus / ExportStatus | PENDING、RUNNING、SUCCEEDED、FAILED_RETRYABLE；导出另有EXPIRED |
| SettlementStatus | NOT_DUE、PROCESSING、COMPLETED |
| SalesStatus（只读） | NOT_OPEN、ON_SALE、SOLD_OUT、STOPPED、CLOSED、ENDED、TERMINATED |

票种SalesStatus派生优先级：TERMINATED > ENDED > CLOSED > NOT_OPEN > STOPPED > SOLD_OUT > ON_SALE；多个事实仍分别返回saleStopped/remaining等字段。列表`sessionSalesStatus`在场次级先判断终止/结束/截止/未开售；开放时有可售票种则ON_SALE，否则有未停售票种则SOLD_OUT，否则STOPPED。筛选状态以此定义，不能混用活动状态。

订单完整转换表（未列出的一律拒绝）：

| 起点 | 终点 | 触发 |
| --- | --- | --- |
| 无 | UNPAID / WAITLISTED | 合格新报名，是否可公平取得占位 |
| WAITLISTED | OFFERED | 系统分配、占位、通知同事务 |
| WAITLISTED | CANCELLED | 主动取消/截止/终止 |
| OFFERED | UNPAID | 本人在确认截止前确认，沿用占位 |
| OFFERED | CANCELLED | 拒绝/到期/主动取消/截止/终止 |
| UNPAID | REGISTERED | 成功支付及出票同事务 |
| UNPAID | CANCELLED | 主动取消/第三次不同失败/到期/截止/终止 |
| REGISTERED | CHECKED_IN | 授权检票窗口内确认 |
| REGISTERED | REFUNDED | 合规主动退款或平台终止退款 |
| REGISTERED | ABSENT | 正常结束未签到；平台终止不新增缺席 |
| CHECKED_IN | COMPLETED | 正常结束或平台提前终止结算 |

支付失败不改变UNPAID（第三次失败除外），退款失败不改变REGISTERED。COMPLETED/CANCELLED/REFUNDED/ABSENT不可恢复。CHECKED票在完成后仍CHECKED；未用票退款/缺席转INVALID。

## 3. 共享数据模型

下表采用紧凑字段表达；字段名、类型和空值是约定，后续Java/TS类型需逐项对应。

### 3.1 身份与账号

| DTO | 字段 |
| --- | --- |
| Csrf | `token:string,headerName:"X-CSRF-TOKEN"` |
| UserView | `id:uuid,username:string,role:Role,email:string?,maskedPhone:string?,displayName:string?,mustChangePassword:false,sessionExpiresAt:time,restrictions:RestrictionView[],checkerSessionId:uuid?`；管理员/检票员邮箱手机号可空；强改密另用ForcePasswordUser |
| ForcePasswordUser | `id:uuid,username:string,role:Role,mustChangePassword:true,sessionExpiresAt:time`；AUTH-02/ME-01响应为UserView或本模型，按mustChangePassword区分 |
| RestrictionView | `id:uuid,kind:RestrictionKind,startsAt:time,endsAt:time,reason:string,source:string?,active:boolean` |
| RestrictionAppealView | `id:uuid,restrictionId:uuid,userId:uuid,username:string,reason:string,evidenceText:string?,status:APPROVED或REJECTED或PENDING,createdAt:time,reviewedAt:time?,decisionNote:string?,restriction:RestrictionView` |
| RegisterInput | `username:string,password:string,email?:string?,phone:string,role:PARTICIPANT或ORGANIZER,verificationId:uuid,verificationCode:string`；六位数字验证码须匹配REGISTER用途、手机号及角色 |
| RecoveryInput | `account:string,phone:string,role:PARTICIPANT或ORGANIZER,currentPhone:string,statement:string,pastBehavior:string,verificationId:uuid,verificationCode:string`；phone为原号码；陈述/行为各20～2000字；验证当前号码RECOVERY_CONTACT用途；提交不直接重置 |
| RecoveryReceipt | `requestId:uuid,status:"RECEIVED",message:string`；不透露账号是否存在 |
| RecoveryView（ADMIN） | `id:uuid,account:string,phone:string,targetAccountId:uuid?,status:PENDING或VERIFIED或RESOLVED或REJECTED,createdAt:time,verificationNote:string?,resolvedAt:time?,currentPhone:string?,role:string?,statement:string?,pastBehavior:string?,contactVerifiedAt:time?,historicalHints:string[]`；受理时固定匹配目标账号ID，不随同名重注册改绑；无匹配为null，仅管理员可读 |
| ClosureEligibility | `eligible:boolean,blockers:[{code,message,objectType,objectId}],cancellationCount:int,lastCancelledAt:time?,nextEligibleAt:time?,willBlacklist:boolean` |
| ManagedAccount | `id:uuid,username:string,role:Role,maskedPhone:string?,enabled:boolean,deletedAt:time?,mustChangePassword:boolean,assignedSessionId:uuid?,eventId:uuid?,version:int`；不返回密码 |

格式约定：用户名1～60字，邮箱选填，空白按null保存；填写时≤254字符并校验通用邮箱格式（含qq.com、163.com、edu.cn等，无后缀白名单），手机号11位大陆数字；密码8～72位ASCII字母数字且两类均有；填写的邮箱/用户名去首尾空白，ASCII英文字母不区分大小写唯一；有效账号手机号在同角色内唯一，参与者与组织者可共用同一手机号；用户名与邮箱仍全局唯一。额外上限为实现设计选择。本人姓名1～60字，允许不同账号重名；非实名认证。

### 3.2 活动、草稿与公开查询

| DTO | 字段 |
| --- | --- |
| Category | `id:uuid,name:string,enabled:boolean,version:int`；名称trim后1～60字、归一唯一，停用也占名 |
| TicketTypeDraft | `id:uuid,name:string?,capacity:int?,priceJiao:int?,saleStopped:boolean` |
| SessionDraft | `id:uuid,name:string?,startsAt:time?,endsAt:time?,location:string?,capacity:int?,salesOpensAt:time?,registrationClosesAt:time?,ticketTypes:TicketTypeDraft[]` |
| DraftInput | `expectedVersion:int,name:string?,categoryId:uuid?,proposedCategoryName:string?,description:string?,sessions:SessionDraft[]`；`categoryId`与`proposedCategoryName`二选一，提出新类别时必须留空`categoryId`；聚合整体替换，缺失子对象即从草稿移除；不含poster文件路径 |
| EventManage | `id:uuid,organizerId:uuid,status:EventStatus,version:int,name:string?,categoryId:uuid?,categoryNameSnapshot:string?,proposedCategoryName:string?,description:string?,posterUrl:string?,sessions:SessionManage[],submittedAt:time?,publishedAt:time?,terminatedAt:time?,terminationReason:string?,lastReviewReason:string?,reviewOverdue:boolean,settlementStatus:SettlementStatus,expiresAt:time?,allowedActions:string[]` |
| SessionManage | SessionDraft字段加 `sessionSalesStatus:SalesStatus?,checkerCount:int`；ticketTypes仍为TicketTypeDraft[]，草稿未填字段保留null。此模型只含活动配置，不含occupied/remaining等订单计数；计数必须通过受保留期保护的MGT-05/06读取 |
| TicketTypeView | `id:uuid,name:string,capacity:int,priceJiao:int,saleStopped:boolean,occupied:int,remaining:int,salesStatus:SalesStatus` |
| SessionPublic | `id:uuid,eventId:uuid,eventName:string,categoryId:uuid,categoryNameSnapshot:string,name:string,startsAt:time,endsAt:time,location:string,capacity:int,salesOpensAt:time,registrationClosesAt:time,remaining:int,sessionSalesStatus:SalesStatus,ticketTypes:TicketTypeView[],posterUrl:string?` |
| EventPublic | `id:uuid,name:string,categoryId:uuid,categoryNameSnapshot:string,description:string,posterUrl:string?,sessions:SessionPublic[]`；无organizer私有账号、名单、支付记录 |
| PublishedEdit | `expectedVersion:int,description?:string?,sessions?:[{id:uuid,location?:string,capacity?:int,ticketTypes?:[{id:uuid,capacity?:int,saleStopped?:boolean}]}]`；仅description可null清空，其他字段不接受null；至少一项实际修改，数组重复ID422，省略子项不删除 |
| ReviewResult | `operationId:uuid,outcome:PUBLISHED或REJECTED或RETURNED_TO_DRAFT,reasonCode:string?,reason:string?,replayed:boolean,current:EventManage,dailyPublishedCount:int` |
| TerminationResult | `operationId:uuid,replayed:boolean,current:EventManage,effectiveAt:time,jobId:uuid,settlementStatus:SettlementStatus`；首次为PROCESSING，重放读取最新进度，不把已完成显示回处理中 |
| ReviewQueue | Page<EventManage>加 `dailyPublishedCount:int,dailyLimit:100,day:string`，day为北京时间自然日 |

草稿允许未填字段为null但已填写必须合法。SessionDraft/TicketTypeDraft新增ID由前端生成UUID，保存后不更换；服务端校验全局未占用或属于本聚合，不能以ID接管其他活动子对象。待审期检票账号已绑定的场次被撤回后从草稿删除时，同事务撤销/逻辑删除相关检票账号，保留审计，不留下悬空授权；新增场次须重新配置。这是聚合保存的完整性设计补充。

名称/地点1～60字、简介≤500字、活动1～20场、每场1～10票种、容量1～9999且票种总和≤场次。草稿不要求结构完整，容量总和校验可延后；提交/发布/已发布编辑必须完整合法。价格0～9999990角。

场次名在本活动内唯一、票种名在本场内唯一；比较以trim后的Unicode文本精确相等为准（不擅自套用类别的英文大小写归一规则）。已有有效值在草稿保存时即校验名称、ID唯一性、字段类型/范围和已齐备的起止顺序；仅缺失必填项与容量总和可延至提交，不允许先保存非法价格/重复名称。已发布编辑只允许未结束且未终止场次，已结束场次即使整活动尚未结束也只读。

EventManage补充只读汇总`plannedStartsAt:time?,plannedEndsAt:time?,totalCapacity:int?,minPriceJiao:int?,maxPriceJiao:int?`，从配置推导，不是可写字段；不完整草稿不能计算时null。管理元数据详情/列表不得夹带订单数、候补数、已占/余票、出票/退款/签到数和统计快照，防止过期读取绕过。

PUB-02筛选参数：`eventName?,categoryId?,startsFrom?,startsTo?,location?,ticketTypeName?,minPriceJiao?,maxPriceJiao?,salesStatus?,sortBy=startsAt|remaining,sortDirection=asc|desc,page?,pageSize?`。同一票种须同时满足ticketTypeName与价格条件；所有过滤AND；活动名/地点/票种名为trim后包含匹配（ASCII大小写不敏感）。时间和价格闭区间，非法逆区间422。默认startsAt asc，同值sessionId asc；sortDirection不改变同值ID排序。只返回已发布且未整体结束活动下未结束场次。

PUB-02的`total`为符合条件的场次数，不是活动数；每行ticketTypes返回该公开场次全部票种，另有`matchedTicketTypeIds:uuid[]`标示满足票种/价格条件的票种，避免前端误解匹配范围。无票种筛选时全部票种都在matched集合。

total返回真实命中数，不截成10000；超过可查询范围时前端提示缩小筛选，禁止翻到越界页。所有列表page必须为≥1整数，pageSize为1～100整数；from/to、startsFrom/startsTo为time，price为整数角，所有boolean query只接受true/false，id为UUID，枚举值严格匹配。仅公开查询有10000偏移上限。

### 3.3 订单、支付、退款与票

| DTO | 字段 |
| --- | --- |
| RegistrationInput | `requestId:uuid,sessionId:uuid,ticketTypeId:uuid,attendeeName:string`；无userId、quantity、price或多人名单 |
| EventHistorySummary | `eventId:uuid,eventName:string,categoryNameSnapshot:string,eventStatus:EventStatus,sessionId:uuid,sessionName:string,startsAt:time,endsAt:time,location:string,terminationReason:string?,terminatedAt:time?` |
| OrderView | `id:uuid,requestId:uuid,status:OrderStatus,statusReason:string?,createdAt:time,attendeeNameSnapshot:string,amountJiao:int,event:EventHistorySummary,ticketTypeId:uuid,ticketTypeName:string,payDeadline:time?,confirmDeadline:time?,queuePosition:int?,consecutivePaymentFailures:int,ticketId:uuid?,activeRefundRequestId:uuid?,allowedActions:string[]` |
| RegistrationResult | `operationId:uuid,operationResult:CREATED或EXISTING_ACTIVE_ORDER,replayed:boolean,current:OrderView` |
| QueueView | `orderId:uuid,status:OrderStatus,position:int?,confirmDeadline:time?,payDeadline:time?,temporarilySkipped:boolean,skipReason:string?`；仅本人位次，不提供他人账号/队列名单 |
| PaymentAttempt | `id:uuid,attemptId:uuid,orderId:uuid,result:AttemptResult,amountJiao:int,createdAt:time,failureReason:string?` |
| PaymentResult | `operationId:uuid,attempt:PaymentAttempt,replayed:boolean,current:OrderView,ticket:TicketView?`；重复成功不新增尝试/票，不续期 |
| RefundRequest | `id:uuid,orderId:uuid,source:RefundSource,status:RefundRequestStatus,acceptedAt:time,initialAttemptId:uuid,reason:string,eligibilityAtAcceptance:{registrationClosesAt:time,orderStatus:OrderStatus},revokedAt:time?,closureReason:string?`；内部完整资格快照、原请求指纹及受理幂等映射由服务端保存 |
| RefundAttempt | `id:uuid,attemptId:uuid,requestId:uuid,orderId:uuid,originalPaymentId:uuid,result:AttemptResult,amountJiao:int,createdAt:time,failureReason:string?` |
| RefundResult | `operationId:uuid,request:RefundRequest,attempt:RefundAttempt,replayed:boolean,current:OrderView` |
| TicketView | `id:uuid,orderId:uuid,status:TicketStatus,attendeeName:string,event:EventHistorySummary,ticketTypeName:string,paidAmountJiao:int,checkedAt:time?,usable:boolean,invalidReason:string?`；姓名/价快照，地点最新 |
| PaymentAudit | `paymentAttempts:PaymentAttempt[],refundRequests:RefundRequest[],refundAttempts:RefundAttempt[]`；涵盖多次失败和最多一次成功支付，尚未支付时paymentAttempts=[]；各数组createdAt asc,id asc |

订单allowedActions枚举：PAY、CONFIRM_PROMOTION、CANCEL、REQUEST_REFUND、RETRY_REFUND、REVOKE_REFUND、VIEW_TICKET、DOWNLOAD_TICKET、REPORT。它是展示提示，执行时仍实时校验。登录角色不满足的动作不返回。因网络延迟产生旧按钮时后端真实判断优先。

### 3.4 验票、名单、统计和导出

| DTO | 字段 |
| --- | --- |
| CheckerContext | `session:SessionPublic,eventStatus:EventStatus,checkinOpensAt:time,checkinClosesAt:time,allowedActions:string[]` |
| TicketPreview | `ticketId:uuid,userId:uuid,username:string,attendeeName:string,phone:string?,sessionId:uuid,sessionName:string,ticketTypeName:string,ticketStatus:TicketStatus,alreadyChecked:boolean,checkedAt:time?,canConfirm:boolean` |
| CheckInResult | `ticketId:uuid,orderId:uuid,checkedAt:time,checkerId:uuid,sessionId:uuid,replayed:boolean,orderStatus:OrderStatus,ticketStatus:TicketStatus`；只返回当前授权范围内数据 |
| AttendeeRow | `attendeeName:string,sessionId:uuid,sessionName:string,ticketTypeId:uuid,ticketTypeName:string,orderStatus:OrderStatus,checkinStatus:NOT_CHECKED或CHECKED,ticketId:uuid?`；无手机号/邮箱/支付记录 |
| ExportView | `id:uuid,eventId:uuid,status:ExportStatus,rowCount:int?,createdAt:time,expiresAt:time?,downloadUrl:string?,failureReason:string?`；活动未终结时expiresAt=null，终结后按统一边界动态计算，下载仍复核 |
| LiveCounts | `eventId:uuid,sessionId:uuid?,asOf:time,orderCounts:Record<OrderStatus,int>,currentWaitlist:int,cumulativeWaitlistApplications:int,occupied:int,remaining:int,checkedIn:int,ticketTypeCounts:[{sessionId:uuid,ticketTypeId:uuid,capacity:int,occupied:int,remaining:int}],settlementStatus:SettlementStatus`；分票种计数供扩缩容显示，仍受同样期限 |
| CheckerCounts | `sessionId:uuid,asOf:time,checkedIn:int,settlementStatus:SettlementStatus`；不返回候补数、订单分布、支付汇总 |
| Statistics | `eventId:uuid,sessionId:uuid?,reason:NATURAL或CANCEL或TAKEDOWN,settlementStatus:SettlementStatus,generatedAt:time?,expiresAt:time?,issuedCount:int?,refundCount:int?,nonRefundedIssuedCount:int?,checkedInCount:int?,absentCount:int?,capacityAtEnd:int?,currentWaitlistCount:int?,cumulativeWaitlistApplications:int?,attendanceRate:number?,absenceRate:number?,capacityUtilizationRate:number?` |
| CheckerStatistics | `sessionId:uuid,settlementStatus:SettlementStatus,generatedAt:time?,expiresAt:time?,checkedInCount:int?,attendanceRate:number?`；只限授权场次签到统计，无金额、其他场次 |
| PaymentSummary | `eventId:uuid,sessionId:uuid?,successfulPaymentCount:int,paidAmountJiao:int,successfulRefundCount:int,refundedAmountJiao:int,netAmountJiao:int,asOf:time`；非长期财务报表，受7天期限 |

统计处理中以200返回settlementStatus=PROCESSING、正式数值null，不输出假零；尚未结束返回NOT_DUE且正式值null。比率为0～1数值，6位小数，页面转百分数；分母0返回0。报名人数=曾成功出票数；分母=未退款出票数；签到/缺席分别除该分母；利用率=签到/结束容量；活动汇总先加分子分母再算，不平均百分比。平台终止统计以实际结果和reason解释，不能制造缺席使比例凑成1。

结算完成快照同时保存累计候补申请数和结算时当前队列数（清理全部完成后通常为0）；不能只返回累计数。活动进行中，已正常结束场次可读该场快照；活动汇总仍NOT_DUE。若自然结束任务尚未落库，按计划结束时点判断PROCESSING，不能继续返回NOT_DUE。实时计数对已结束场次返回409引导快照，其他未结束场次仍可读；7天期限优先于这些状态分支，过期统一410。

名单过滤：`sessionId?,ticketTypeId?,orderStatus?,checkinStatus?,page?,pageSize?`，AND组合，默认sessionId asc、orderId asc内部稳定排序（响应不额外暴露orderId列）。ticketType必须属于指定活动/场次，非法关联422。

### 3.5 治理、消息、审计与任务

| DTO | 字段 |
| --- | --- |
| ReportInput | `sessionId:uuid,reason:string,description:string,evidenceText:string`；reason1～200字，description/evidenceText各1～2000字（新增实现上限），不上传附件/抓取链接 |
| ReportView | `id:uuid,sessionId:uuid,eventId:uuid,reason:string,description:string,evidenceText:string,status:ReportStatus,createdAt:time,conclusion:string?,resolvedAt:time?`；本人或管理员可读，举报人看不到其他举报人的身份/内容 |
| ReportAdminRow | ReportView加 `reporterId:uuid,eventAcceptedReportCount:int,needsAttention:boolean`；同活动累计≥11提醒，提醒去重 |
| InvestigationInput | `eventId:uuid,reportIds:uuid[],factKey:string,conclusion:SUBSTANTIATED或UNSUBSTANTIATED,reason:string,evidenceText:string`；factKey1～120字，reason1～200字，evidenceText1～2000字；无举报时可空reportIds但仍必需证据 |
| InvestigationResult | `id:uuid,factId:uuid?,eventId:uuid,conclusion:string,mergedReportIds:uuid[],violationRecorded:boolean,eventStatus:EventStatus,terminationJobId:uuid?` |
| RestrictionCandidate | `phoneIdentityId:uuid,maskedPhone:string,kind:RestrictionKind,absenceCount:int,voluntaryRefundCount:int,violationCount:int,eligibleFactIds:uuid[],activeRestriction:RestrictionView?,previousEffectiveAt:time?`；服务端重算，客户端计数无效 |
| RestrictionEvidence | `candidate:RestrictionCandidate,facts:[{id:uuid,type:NATURAL_ABSENCE或VOLUNTARY_REFUND或ORGANIZER_VIOLATION,occurredAt:time,orderId:uuid?,investigationId:uuid?,reason:string,consumed:boolean}]`；仅单个手机号身份的核查事实，不提供活动名单重建 |
| MessageView | `id:uuid,type:string,title:string,body:string,createdAt:time,expiresAt:time,readAt:time?,target:{resourceType:string,resourceId:uuid}?,actionDeadline:time?`；过期动作不可因阅读恢复 |
| AuditLog | `id:uuid,actorId:uuid?,actorRole:Role或SYSTEM,action:string,objectType:string,objectId:uuid?,result:SUCCESS或FAILURE,failureReason:string?,jobType:string?,createdAt:time,traceId:string` |
| AuditObject | `objectType:ORDER或PAYMENT或REFUND或TICKET或CHECKIN,id:uuid,record:单条脱敏业务记录,relatedIds:{orders:uuid[],payments:uuid[],refunds:uuid[],tickets:uuid[],checkins:uuid[]}}`；只沿该对象链路，不提供活动级批量数据 |
| JobView | `id:uuid,type:string,eventId:uuid?,status:JobStatus,dueAt:time,deadlineAt:time,completedAt:time?,total:int,processed:int,retryCount:int,overdue:boolean,lastError:string?` |

消息类型至少覆盖 PROMOTION_OFFERED、PAYMENT_SUCCESS、REFUND_SUCCESS、ORDER_CANCELLED、REVIEW_RESULT、EVENT_CHANGED、EVENT_TERMINATED、VIOLATION、RESTRICTION_STARTED、RESTRICTION_ENDED。事件键由服务端产生，客户端不可传接收人和eventKey。

## 4. 接口目录

表中路径均需加 `/api`。P=参与者，O=组织者，C=检票员，A=管理员，U=可用且已完成必要改密的已登录用户，G=访客/公共读取。所有“本人/自己/授权”在服务端校验。箭头右侧为data类型，外层仍用统一信封。未标状态时GET/修改200、创建201；特殊202或二进制见行内。

### 4.1 身份与账号入口（auth/account）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| AUTH-01 | GET `/auth/csrf` | G/U | 无 → Csrf | 取匿名/登录上下文token；不续业务会话期限 |
| AUTH-02 | POST `/auth/login` | G | `{username,password}` → UserView或ForcePasswordUser | 200设置Cookie；旧会话撤销；固定7天；失败不设置登录态 |
| AUTH-03 | POST `/auth/register` | G | RegisterInput → `{id,username,role}` | 201；必须验证手机，不自动登录；仅P/O，同角色手机号重复且验证码有效时409 PHONE_ACCOUNT_EXISTS，保留该验证码供本人确认登录；继承手机号历史 |
| AUTH-04 | POST `/auth/recoveries` | G | RecoveryInput → RecoveryReceipt | 202；当前号码先验证；冻结原账号匹配，保存详细陈述供人工审核；不自动改密；同资料合并，不同资料需联系核对 |
| AUTH-05 | POST `/auth/logout` | 登录含强改密 | `{}` → `{loggedOut:true}` | 撤销会话并清Cookie；不依赖前端删除状态 |
| AUTH-06 | POST `/auth/phone-codes` | G | `{phone:string,role:PARTICIPANT或ORGANIZER,purpose:LOGIN或REGISTER或RESET_PASSWORD或RECOVERY_CONTACT或CHANGE_PHONE}` → `{verificationId:uuid,expiresAt:time,resendAfterSeconds:60,delivery:LOCAL_CONSOLE}` | 200；CHANGE_PHONE须P/O登录且角色匹配、校验新号同角色未占用；本地终端输出六位码，5分钟有效；同手机同角色60秒冷却；不返回验证码或账号是否存在 |
| AUTH-07 | POST `/auth/phone-login` | G | `{phone:string,role:PARTICIPANT或ORGANIZER,verificationId:uuid,verificationCode:string,purpose:LOGIN或REGISTER}` → UserView或ForcePasswordUser | 200设置7天Cookie，撤销旧会话；验证码一次性；REGISTER用途用于用户确认登录同角色原账号；验证后未注册404 PHONE_NOT_REGISTERED |
| AUTH-08 | POST `/auth/password-reset` | G | `{phone,role:PARTICIPANT或ORGANIZER,verificationId,verificationCode,newPassword}` → `{changed:true,requiresLogin:true}` | RESET_PASSWORD验证码一次消费；不要求原密码；设置本人新密码、撤销旧会话、清Cookie；不得用其他用途验证码 |
| ME-01 | GET `/me` | 登录含强改密 | 无 → UserView或ForcePasswordUser | 强改密使用独立最小模型；不返回完整手机号给展示页 |
| ME-02 | PATCH `/me/profile` | P/O | `{displayName:string}` → Mutation<UserView> | 本人姓名更改，不改历史姓名；不接受角色/邮箱修改；手机号通过ME-27换绑 |
| ME-03 | POST `/me/password` | 登录含强改密 | `{oldPassword,newPassword}` → `{changed:true,requiresLogin:true}` | 校验原密码；重置后用初始/临时密码作为oldPassword；撤销全部会话并清Cookie |
| ME-04 | GET `/me/closure-eligibility` | P/O | 无 → ClosureEligibility | 查询提示不构成注销授权；写时重查 |
| ME-05 | POST `/me/closure` | P/O | `{password,confirmation:"CLOSE"}` → `{closed:true,requiresLogin:true}` | 未结业务阻止；O另查待审/未结束发布；365天/第三次黑名单；立即撤销 |
| ME-27 | PATCH `/me/phone` | P/O本人 | `{newPhone:string,verificationId:uuid,verificationCode:string}` → `{maskedPhone:string,requiresLogin:true}` | CHANGE_PHONE新号验证码即可；不收原号验证码/密码；校验同角色占用、更新登录联系方式并撤销会话清Cookie，保留原业务身份与订单事实；Idempotency-Key必需 |

### 4.2 公开读取（catalog/search）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| PUB-01 | GET `/public/categories` | G | `scope=FILTER或EDIT` → Category[] | 默认FILTER：有效类别及仍有关联公开活动的停用类别；EDIT仅enabled，供草稿选择；按name归一asc,id asc |
| PUB-02 | GET `/public/sessions` | G | 第3.2节筛选 → Page<SessionPublic+matchedTicketTypeIds> | 场次结果、AND、同票种匹配、稳定排序、深分页≤10000 |
| PUB-03 | GET `/public/events/{eventId}` | G | path → EventPublic | 存在但非公开409 EVENT_NOT_PUBLIC；已终止附实际原因且不泄露私有字段 |
| PUB-04 | GET `/public/events/{eventId}/poster` | G | path → image/png或image/jpeg | 与公开详情相同可见性；无海报404；禁止直链文件路径绕过状态 |

### 4.3 本人报名、订单和候补（order）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| ME-06 | POST `/me/registrations` | P | RegistrationInput → RegistrationResult | 201新单，200重放/同票种已有有效单；异票种409 ACTIVE_ORDER_EXISTS；映射新requestId到返回原单，重放不误指向后来的单 |
| ME-07 | GET `/me/orders` | P | `status?,eventId?,page?,pageSize?` → Page<OrderView> | createdAt desc,id asc；包括终态及已非公开活动本人摘要，不提供别人名单 |
| ME-08 | GET `/me/orders/{orderId}` | P本人 | path → OrderView | 最新状态/期限/可用动作；用于响应丢失恢复 |
| ME-09 | GET `/me/registration-requests/{requestId}` | P本人 | path → RegistrationResult | 查原报名键；无持久受理记录404；未知结果时也可用原键重放ME-06 |
| ME-10 | POST `/me/orders/{orderId}/cancel` | P本人 | `{reason:string?}` → Mutation<OrderView> | 只WAITLISTED/OFFERED/UNPAID且验票结束前；释放与消息同事务；REGISTERED必须走退款 |
| ME-11 | GET `/me/orders/{orderId}/queue` | P本人 | path → QueueView | 当前位次仅WAITLISTED；其他状态position=null；不返回其他人数据 |
| ME-12 | POST `/me/orders/{orderId}/promotion-confirmation` | P本人 | `{}` → Mutation<OrderView> | OFFERED且now<确认截止；确认截止=min(通知+12小时,验票结束)，确认后支付截止=min(确认+15分钟,验票结束)；拒绝递补用ME-10 |

普通支付截止=min(新单提交时间+15分钟,报名截止)；候补递补确认截止=min(通知持久化+12小时,验票结束)，确认后支付截止=min(确认时刻+15分钟,验票结束)。候补未递补、已递补未确认或已确认未支付，均在验票结束时失效。被报名锁定者不得新报名/候补，但既有OFFERED/UNPAID可继续履约。无合格候补时才允许新用户取得名额。

### 4.4 支付与退款（payment）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| ME-13 | POST `/me/orders/{orderId}/payment-attempts` | P本人 | `{attemptId:uuid}` → PaymentResult | 201新尝试，200重放/已有成功；普通模式默认成功，0元也确认；不接收simulateFailure/金额 |
| ME-14 | GET `/me/orders/{orderId}/payments` | P本人 | 无 → PaymentAudit | 仅本人；支付尝试与退款记录，含失败；按createdAt asc,id asc |
| ME-15 | POST `/me/orders/{orderId}/refund-requests` | P本人 | `{attemptId:uuid,reason:string}` → RefundResult | 201依据受理后同步尝试；reason1～200字；报名截止前且REGISTERED；重复键返回原结果/当前单 |
| ME-16 | GET `/me/orders/{orderId}/refund-requests/{requestId}` | P本人 | path → `{request:RefundRequest,attempts:RefundAttempt[],current:OrderView}` | 查看已保存合法申请及尝试；request必须属于path订单 |
| ME-17 | POST `/me/orders/{orderId}/refund-requests/{requestId}/attempts` | P本人 | `{attemptId:uuid}` → RefundResult | 201新尝试/200重放；FAILED原依据可新尝试，ACCEPTED断点仅可恢复initialAttemptId；始终重查当前资格 |
| ME-18 | POST `/me/orders/{orderId}/refund-requests/{requestId}/revocation` | P本人 | `{}` → Mutation<RefundRequest> | 仅未成功USER依据，撤销后禁止该依据重试；平台依据不可撤销 |

ME-13同order+attempt重放返回原attempt和最新order；不同attempt但已有成功时返回唯一成功attempt，operationId指原成功，replayed=true。不能新增虚构第二笔成功。失败尝试少于3仍UNPAID，第三个不同连续失败取消释放。请求在截止后且未有可重放结果时即时拒绝；清理任务最多1分钟处理库存。

退款使用两个事务边界：T1鉴权/资格/截止/幂等→原子保存合法RefundRequest、initialAttemptId、原参数指纹、受理操作记录和受理日志；T2重新校验当前状态→全额退款+票失效+状态+释放+成功日志消息+尝试结果一起提交。T2失败回滚业务，再单独保存失败尝试与FAILED（数据库可用时）；不回滚先前T1依据。技术故障未能保存attempt时返回5xx，查询依据后用原attemptId恢复，不能猜测成功。禁止将订单永久置“已处理”阻止恢复。

退款断点恢复协议：ME-15相同Idempotency-Key与原参数重放，如果T1已提交而T2无持久结果，恢复原request和initialAttemptId，不重新检查“新申请截止”；T2仍重查当前状态/原依据。ME-16可读ACCEPTED及initialAttemptId，此时ME-17只接受该initialAttemptId恢复；新的attemptId仅在已有持久失败后受理。若响应未完成，前端显示“结果待确认”，订单仍REGISTERED，不谎称退款失败或成功。受理记录不能作为完整RefundResult缓存返回，必须恢复执行或5xx并指向原依据。

单订单同时最多一个未撤销、未成功的USER退款依据；另一新意图返回409 REFUND_REQUEST_EXISTS及本人原依据ID，避免两个T1并发创建悬空申请。T2、撤销、验票、平台终止串行校验；成功退款后其余失效依据标记REVOKED并保存原因，避免永久阻止注销。签到/完成/缺席使未成功USER依据失效并关闭（REVOKED，原因保存），不得据此再退款。平台终止可依据其独立事实执行，不被失败USER申请阻塞，也不得把平台退款计作主动退款。

正常无失败调用ME-15即同步成功，不额外要求用户再点执行；失败再调用ME-17。没有处理中订单状态，依据可以FAILED；后台平台退款和人工重试遵循同样每单成功唯一、当前状态限制。未撤销合法失败申请可在截止后重试，但已签到/已终态仍拒绝。撤销申请不回滚已成功退款。新报名退款依据复用须匹配原订单，不能跨单。

### 4.5 电子票（ticket）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| ME-19 | GET `/me/tickets/{ticketId}` | P本人 | path → TicketView | 全局唯一id不代替归属校验；失效票/历史仍可读 |
| ME-20 | GET `/me/tickets/{ticketId}/download` | P本人 | path → `text/plain;charset=UTF-8` | Content-Disposition attachment；内容含姓名、id、活动场次时间地点、票种实付价状态；无二维码/PDF |

下载失效/已验票明确“不可再次验票”；不改变票状态；不受名单7天期限影响。本人历史图片若需展示，通过订单摘要的授权读取，不将草稿或他人活动文件开放；本版历史摘要不承诺海报字段。

### 4.6 组织者活动（event）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| ORG-01 | GET `/organizer/events` | O | `status?,page?,pageSize?` → Page<EventManage> | 自己活动；updatedAt desc,id asc |
| ORG-02 | POST `/organizer/events` | O | `{}` → EventManage | 201创建空草稿，category/name可空 |
| ORG-03 | GET `/organizer/events/{eventId}` | O归属 | path → EventManage | 管理视图含待审/终态；字段按角色投影 |
| ORG-04 | PUT `/organizer/events/{eventId}/draft` | O归属 | DraftInput → Mutation<EventManage> | DRAFT整体保存；允许不完整；稳定子ID；拒绝非整点；场次删除处理授权见3.2 |
| ORG-05 | PUT `/organizer/events/{eventId}/poster` | O归属 | multipart `file,expectedVersion` → Mutation<EventManage> | DRAFT/PUBLISHED未整体结束；JPEG/PNG≤5,000,000字节；服务端识别真实格式并限制解码像素≤40,000,000（本轮实现上限），失败保留旧图 |
| ORG-06 | DELETE `/organizer/events/{eventId}/poster` | O归属 | query `expectedVersion` → Mutation<EventManage> | 同可编辑条件；删除关联，不暴露文件系统地址 |
| ORG-07 | POST `/organizer/events/{eventId}/submission` | O归属 | `{expectedVersion}` → Mutation<EventManage> | 校验完整性/时间/排期/有效类别或建议新类别，更新提交类别快照；不要求草稿编辑时就完整 |
| ORG-08 | POST `/organizer/events/{eventId}/withdrawal` | O归属 | `{expectedVersion}` → Mutation<EventManage> | 仅PENDING_REVIEW；与审核并发仅一方成功 |
| ORG-09 | PATCH `/organizer/events/{eventId}/published-settings` | O归属 | PublishedEdit → Mutation<EventManage> | 只允许未结束场次白名单字段、活动简介；整体容量校验；地点/容量变更给受影响有效单持久通知；不得修改已结束场次 |
| ORG-10 | POST `/organizer/events/{eventId}/cancellation` | O归属 | `{expectedVersion,reason:string}` → TerminationResult | 202；reason1～200字；首场前且全活动无签到；终止立即生效 |
| ORG-11 | GET `/organizer/events/{eventId}/settlement` | O归属 | path → `{eventId,eventStatus,effectiveAt:time?,settlementStatus,job:JobView?}` | 可查自己批处理进度，不能改变job；不输出其他活动信息 |

组织者管理海报预览使用 EventManage.posterUrl，值为下一节MGT-08受控URL，不能用PUB-04读取尚未发布海报。

### 4.7 检票员（ticket）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| CHK-01 | GET `/checker/context` | C | 无 → CheckerContext | 从会话授权取场次；客户端不能通过参数选择另一场；验票窗口为活动开始前2小时至活动进行到一半 |
| CHK-02 | POST `/checker/ticket-previews` | C | `{ticketId:uuid}` → TicketPreview | 200只核对；先鉴权与当前授权再返回用户名、姓名、手机号、用户ID与票号；未签到须票有效/窗口/活动有效 |
| CHK-03 | POST `/checker/checkins` | C | `{ticketId:uuid}` → CheckInResult | 201首次，200重复；确认时重查，不信preview；票唯一签到 |
| CHK-04 | POST `/checker/sessions/{sessionId}/absence-reconciliation` | C当前授权 | `{}` → `{sessionId,processedCount:int,alreadyProcessedCount:int,settlementStatus}` | 200；只在正常结束后且账号仍有效；幂等补漏，不改已退款/取消/签到，不提前标缺席 |

重复签到先校验账号/授权；有效授权下即使过验票窗口也可返回首次结果。末场结束导致账号实际失效后先403，不为查旧结果恢复凭据。活动取消/下架立即拒绝所有新验票，已签到事实不会被撤销。

### 4.8 名单、统计与文件（reporting/storage）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| MGT-01 | GET `/management/events/{eventId}/attendees` | O归属/A/C当前授权 | 第3.4节筛选 → Page<AttendeeRow> | C必须指定且仅当前sessionId；所有角色检查整活动终结+7天；P拒绝 |
| MGT-02 | POST `/management/events/{eventId}/exports` | O归属/A | `{sessionId?,ticketTypeId?,orderStatus?,checkinStatus?}` → ExportView | 202异步生成；无分页限制；0条仍BOM+表头；幂等重放不重复文件 |
| MGT-03 | GET `/management/exports/{exportId}` | O当前归属/A | path → ExportView | 生成者/活动归属复核；过期410；无对外物理路径 |
| MGT-04 | GET `/management/exports/{exportId}/download` | O当前归属/A | path → `text/csv;charset=UTF-8` | 下载再次鉴权与期限；未完成409；不发布半文件；C/P禁止 |
| MGT-05 | GET `/management/events/{eventId}/live-counts` | O归属/A/C当前授权 | `sessionId?` → LiveCounts或CheckerCounts | C必填授权sessionId且仅签到数；目标场次或整活动已终结409转正式统计；自然结束按时间判定，过期优先410 |
| MGT-06 | GET `/management/events/{eventId}/statistics` | O归属/A/C当前授权 | `sessionId?` → Statistics或CheckerStatistics | C必填授权sessionId；自然/终止结算未完返回PROCESSING；未结束NOT_DUE；过期410 |
| MGT-07 | GET `/management/events/{eventId}/payment-summary` | O归属/A | `sessionId?` → PaymentSummary | 聚合金额，不给组织者个人支付详情；到期410 |
| MGT-08 | GET `/management/events/{eventId}/poster` | O归属/A | path → image/png或image/jpeg | 管理海报；草稿/待审/终态按归属授权，无图404；不提供私有数据文件静态目录 |

MGT-03/04允许管理员按明确exportId查看/下载，O必须当前仍归属且有权查看活动；导出创建者变化不赋予额外权限。C不能借URL下载，admin也不能绕过7天。CSV正确转义逗号/引号/换行，文本公式起始符安全处理，票码作为文本，完整临时文件完成后才发布可下载状态；生成与下载分别记日志。

### 4.9 本人举报与消息（governance/message）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| ME-21 | POST `/me/reports` | P | ReportInput → ReportView | 201；公开活动可无票举报；结束后7天内且本人有该活动订单；同一参与者同一活动唯一；不设月度业务上限，保留24小时技术频率保护 |
| ME-22 | GET `/me/reports` | P | `status?,page?,pageSize?` → Page<ReportView> | 仅本人；createdAt desc,id asc；同一活动不重复受理 |
| ME-23 | GET `/me/reports/{reportId}` | P本人 | path → ReportView | 不泄露其他举报人的身份或内容 |
| ME-24 | GET `/me/messages` | U | `unreadOnly=false,page?,pageSize?` → Page<MessageView> | 本人且未满180天；createdAt desc,id asc；逻辑过期过滤 |
| ME-25 | GET `/me/messages/{messageId}` | U本人 | path → MessageView | 详情不自动已读；已过期410 |
| ME-26 | POST `/me/messages/{messageId}/read` | U本人 | `{}` → Mutation<MessageView> | 已读幂等，保留首次readAt，不延长确认期限 |
| ME-28 | GET `/me/restrictions` | P | 无 → RestrictionView[] | 本人手机号身份下的报名限制，含来源、起止与是否生效 |
| ME-29 | GET `/me/restriction-appeals` | P | 无 → RestrictionAppealView[] | 本人申诉记录，createdAt desc,id asc |
| ME-30 | POST `/me/restriction-appeals` | P本人 | `{restrictionId,reason,evidenceText?}` → RestrictionAppealView | 201；仅本人有效报名限制；同一限制同时只允许一条待审，可补充证据再次申诉 |
| ME-31 | GET `/me/messages/unread-count` | U | 无 → `{count:int}` | 仅统计未过期且未读消息 |

### 4.10 管理员账号与人工找回（account）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| ADM-01 | GET `/admin/accounts` | A | `role?,username?,eventId?,sessionId?,enabled?,page?,pageSize?` → Page<ManagedAccount> | username规范化包含匹配；createdAt desc,id asc；不返回密码/会话token |
| ADM-02 | POST `/admin/accounts` | A | `{username,initialPassword,role:ADMIN或CHECKER,sessionId?:uuid}` → ManagedAccount | 201；C必绑已存在场次，A不得绑场次；C仅可配置待审/已发布未结束场次；所有新A强改密 |
| ADM-03 | PATCH `/admin/accounts/{accountId}` | A | `{expectedVersion,enabled?:boolean,assignedSessionId?:uuid}` → Mutation<ManagedAccount> | 仅管理A/C停用/启用，只有C可改绑到待审/已发布未结束场次（可跨活动）；末个可用账号保护；停用/改绑撤销旧会话 |
| ADM-04 | DELETE `/admin/accounts/{accountId}` | A | query `expectedVersion` → Mutation<ManagedAccount> | 仅逻辑删除管理员/检票账号；末个检票保护；不删历史；为可运维性拒绝删除/停用最后一个可用管理员 |
| ADM-05 | POST `/admin/accounts/{accountId}/password-reset` | A | `{initialPassword,reason,recoveryRequestId?:uuid}` → `{accountId,reset:true,mustChangePassword:true}` | 所有重置撤销会话；P/O必须有VERIFIED且targetAccountId匹配、当前号码已验证码验证的完整申诉；同事务换绑、重置及提交后本地短信通知；禁止重置已删除或因活动终止失效的C；不恢复账号 |
| ADM-06 | GET `/admin/recoveries` | A | `status?,page?,pageSize?` → Page<RecoveryView> | 人工核验请求列表；createdAt asc,id asc |
| ADM-07 | POST `/admin/recoveries/{requestId}/resolution` | A | `{decision:VERIFIED或REJECTED,verificationNote:string,initialPassword?:string,reason?:string}` → RecoveryView | note10～2000字；VERIFIED并提供initialPassword时，同事务完成审核、换绑、改密、撤会话和提交后通知；不提供密码则等待ADM-05 |

找回状态：PENDING → VERIFIED或REJECTED；VERIFIED经ADM-05成功重置后变RESOLVED；ADM-07携带临时密码通过审核时，同一事务直接到RESOLVED。ADM-07仅能处理PENDING；ADM-05消费已核验依据一次，记录关联账号及操作，不凭手机号直接重置。管理员重置P/O不等于允许任意修改这些用户角色/业务限制。最后管理员保护为本次设计新增运维约束。

VERIFIED之前必须确认targetAccountId存在、未删除，且登记手机号与该账号当前身份匹配；重置时再次核对。旧账号注销/删除使其未完成找回依据失效，不得转用于占用了相同用户名的新账号。resolvedAt用于RESOLVED/REJECTED完成时刻，VERIFIED尚未实际重置时仍null。

### 4.11 管理员审核与类别（event/catalog）

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| ADM-08 | GET `/admin/reviews` | A | `overdueOnly=false,page?,pageSize?` → ReviewQueue | 仅待审；submittedAt asc,id asc；24小时超期标记，不自动通过 |
| ADM-09 | GET `/admin/events/{eventId}` | A | path → EventManage | 审核/治理所需元数据；不能从中获取过期名单或报表 |
| ADM-10 | POST `/admin/events/{eventId}/approval` | A | `{expectedVersion}` → ReviewResult | 全条件重查；批准时若活动只有建议新类别则自动创建或复用同名类别并归入本活动；成功PUBLISHED，额度原子扣一次；条件失效提交RETURNED_TO_DRAFT原因 |
| ADM-11 | POST `/admin/events/{eventId}/rejection` | A | `{expectedVersion,reason:string}` → ReviewResult | reason1～200字必填；退草稿并通知组织者 |
| ADM-12 | GET `/admin/categories` | A | `enabled?,page?,pageSize?` → Page<Category> | 包括停用；name规范化asc,id asc |
| ADM-13 | POST `/admin/categories` | A | `{name:string}` → Category | 201；同规范化名称唯一，停用也占名 |
| ADM-14 | PATCH `/admin/categories/{categoryId}` | A | `{expectedVersion,name?:string,enabled?:boolean}` → Mutation<Category> | 不回改发布快照；关联草稿/审核发布用当前有效性 |
| ADM-15 | POST `/admin/events/{eventId}/takedown` | A | `{expectedVersion,investigationId:uuid}` → TerminationResult | 202；必须属实且属于该活动的持久核查依据；已终态409，不恢复、不重复退款 |

ADM-10不因缺检票账号而自动创建；前端引导先ADM-02配置。发布条件无效（类别、时间、排期、限制、额度、账号等）一律记录具体原因退草稿；非法版本/已不待审则409且不改状态。

### 4.12 管理员支付、治理、审计及任务

| 编号 | 方法与路径 | 权限 | 输入 → 输出 | 特有规则 |
| --- | --- | --- | --- | --- |
| ADM-16 | GET `/admin/payments` | A | `orderId?,result?,from?,to?,page?,pageSize?` → Page<PaymentAttempt> | createdAt desc,id asc；无orderId时仅列未过短期保留期活动记录；明确orderId时只查该单历史链，不跨单重建名单 |
| ADM-17 | GET `/admin/refund-requests/{requestId}` | A | path → `{request:RefundRequest,attempts:RefundAttempt[],order:OrderView}` | 单条合法退款核查；含原资格、撤销、当前状态，不是批量名单 |
| ADM-18 | POST `/admin/refund-requests/{requestId}/attempts` | A | `{attemptId:uuid,reason:string}` → RefundResult | 201/200；必须有该依据的失败尝试、未撤销且仍合法；每单至多一成功；不代支付 |
| ADM-19 | GET `/admin/reports` | A | `eventId?,status?,needsAttention?,page?,pageSize?` → Page<ReportAdminRow> | createdAt asc,id asc；跨场总受理11条提醒，不以阈值代替证据 |
| ADM-20 | POST `/admin/investigations` | A | InvestigationInput → InvestigationResult | 201；合并同活动同事实，属实记违规；仍发布活动同事务下架并建任务，终态只记核查违规 |
| ADM-21 | GET `/admin/investigations/{investigationId}` | A | path → InvestigationResult加`reason,evidenceText,factKey,createdAt` | 可追溯依据，举报关联不可跨活动 |
| ADM-22 | GET `/admin/restriction-candidates` | A | `kind?,page?,pageSize?` → Page<RestrictionCandidate> | 根据当前窗口事实，排除已消费依据，phoneIdentityId asc；明文手机号不批量暴露 |
| ADM-23 | POST `/admin/registration-restrictions` | A | `{phoneIdentityId:uuid,basisFactIds:uuid[],reason:string}` → RestrictionView | 201；只参与者REGISTER限制，重查3次正常缺席或5次主动退款，15天；组织者PUBLISH限制在违规事实提交事务中自动生效 |
| ADM-24 | GET `/admin/audit-logs` | A | `from?,to?,action?,objectType?,objectId?,result?,page?,pageSize?` → Page<AuditLog> | createdAt desc,id asc；180天内；无改删/导出，日志不含密码/令牌 |
| ADM-25 | GET `/admin/audit-objects/{objectType}/{objectId}` | A | ORDER/PAYMENT/REFUND/TICKET/CHECKIN → AuditObject | 单条业务编号追踪，不支持按活动批量/聚合/导出；不受短期7天影响 |
| ADM-26 | GET `/admin/jobs` | A | `eventId?,type?,status?,overdue?,page?,pageSize?` → Page<JobView> | dueAt asc,id asc；查看最初触发/截止及进度 |
| ADM-27 | GET `/admin/jobs/{jobId}` | A | path → JobView | 不暴露任意任务执行/直接改状态入口，重试由内部持久执行器完成 |
| ADM-28 | GET `/admin/events` | A | `status?,name?,organizerId?,page?,pageSize?` → Page<EventManage> | 所有活动元数据检索，updatedAt desc,id asc；name包含匹配；不含参与者名单/支付明细/聚合统计，历史活动仍可定位但短期数据到期拒绝 |
| ADM-29 | GET `/admin/restriction-candidates/{phoneIdentityId}` | A | query `kind=REGISTER或PUBLISH`必填 → RestrictionEvidence | 单身份可跨注销关联不同角色历史，必须明确kind；仅当前窗口及相关边界的核实依据，ADM-23写时重算 |
| ADM-32 | GET `/admin/restriction-appeals` | A | `status?,page?,pageSize?` → Page<RestrictionAppealView> | createdAt desc,id asc；含账号、限制依据、申诉理由与证据 |
| ADM-33 | POST `/admin/restriction-appeals/{appealId}/resolution` | A | `{decision:APPROVED或REJECTED,note:string}` → RestrictionAppealView | 仅PENDING；APPROVED立即撤销限制并通知；保存审核人、时间与说明；不删除历史 |
| ADM-30 | GET `/admin/investigations` | A | `eventId?,conclusion?,page?,pageSize?` → Page<InvestigationResult> | createdAt desc,id asc；供核查历史列表定位ADM-21详情，不能按其重建参与者名单 |
| ADM-31 | GET `/admin/refund-requests` | A | `status?,source?,orderId?,page?,pageSize?` → Page<RefundRequest> | acceptedAt asc,id asc；仅有合法依据的退款排障队列；批量限未过短期保留期活动，明确orderId仅该单历史；已知requestId走ADM-17 |

ADM-20属实处理中的“记录事实、关联举报结论、组织者计次、需要时下架生效及任务依据”在同一业务写事务提交；再次用同factKey不重复记违规。若事务成功下架，返回201的InvestigationResult含terminationJobId及TAKEN_DOWN，前端显示“下架已生效，结算中”；ADM-15用于对已有合法属实事实补发下架命令，不能造成第二次下架。处理终态活动不能回改旧终态或对旧订单退款。

参与者缺席/主动成功退款事实、组织者违规事实的ID由后端统一生成，限制不能信任客户端自行编造的计数。滚动窗口为 `(now-30×24h,now]`，限制期为 `[startsAt,startsAt+15×24h)`；再次限制仅使用上次生效后新增且未消费的同类事实，0元主动退款计数，平台退款不计。限制解除由时间判断与JOB-06消息补齐，无人工任意延长/提前解除接口。

组织者第三条合格违规事实与自动PUBLISH限制、依据消费、日志及通知必须**同一写事务**，发布接口也实时核对未消费事实，不能留下等JOB-06扫描期间继续发布的空档。限制有效期间不重新起算或延长15天；新增事实保留供符合再限制规则时使用。正常路径不由定时器决定最初生效时刻；JOB-06只做恢复核对和到期处理，断点恢复保留既有生效/截止。

所有账号变更只允许对未逻辑删除且生命周期仍有效的账号执行；终态检票账号不能启用、重置或改绑后恢复。自停用/自删除成功后清前端身份并退出；最后一个可用管理员保护同时适用于ADM-03和ADM-04。

## 5. 典型请求与响应

### 5.1 有票报名

```http
POST /api/me/registrations
Content-Type: application/json
X-CSRF-TOKEN: <token>
Cookie: TICKET_SESSION=<opaque>
```

```json
{
  "requestId": "10000000-0000-4000-8000-000000000001",
  "sessionId": "20000000-0000-4000-8000-000000000001",
  "ticketTypeId": "30000000-0000-4000-8000-000000000001",
  "attendeeName": "张同学"
}
```

201信封data.current为完整OrderView，核心字段示意（以下片段不是省略其他必需字段的实际响应）：

```json
{
  "status": "UNPAID",
  "amountJiao": 120,
  "payDeadline": "2026-11-05T10:15:00+08:00",
  "ticketId": null,
  "allowedActions": ["PAY", "CANCEL"]
}
```

前端仅根据current.status跳支付页或候补页；同票种重复请求返回原有效单时可能已经REGISTERED，应按当前状态跳票/订单页，不强制回支付页。

### 5.2 确认支付与未知结果恢复

`POST /me/orders/{orderId}/payment-attempts` body=`{"attemptId":"40000000-0000-4000-8000-000000000001"}`。成功data.attempt.result=SUCCESS、current.status=REGISTERED、ticket非null。响应丢失时禁用新的付款意图，先GET ME-08/ME-14，或用原attemptId重放ME-13；不得自动换attemptId。用户确认上次确实失败后，新的支付尝试才生成新ID。

### 5.3 管理编辑冲突

`PATCH /organizer/events/{eventId}/published-settings`：

```json
{
  "expectedVersion": 7,
  "sessions": [{
    "id": "20000000-0000-4000-8000-000000000001",
    "capacity": 120,
    "ticketTypes": [{
      "id": "30000000-0000-4000-8000-000000000001",
      "capacity": 120
    }]
  }]
}
```

带Idempotency-Key，版本冲突409 VERSION_CONFLICT；容量小于占用409 CAPACITY_BELOW_OCCUPIED且全部回滚。成功后下一次计数应反映新容量，候补优先分配最多1分钟，不能前端直接加一张“已递补”票。

## 6. 内部任务契约与外部响应关系

内部JOB编号见project.md第13节；没有 `/api/jobs/run` 或客户端可传“现在时间”的正式接口。

| 内部操作 | 可观察的HTTP结果 | 原子性与恢复约定 |
| --- | --- | --- |
| JOB-01 超时/截止清理 | ME-08当前状态、ME-24消息、ADM-27进度 | 即时请求已拒绝；任务≤1分钟取消释放一次，不提前复用未释放库存 |
| JOB-02 公平递补 | ME-11位次、ME-08 OFFERED、消息 | 分配+占位+12小时截断期限+消息同事务；≤1分钟 |
| JOB-03 自然结束 | MGT-06报表、账号不可用 | 场次完成/缺席/票失效与快照≤1分钟；汇总先加总；末场账号失效即时 |
| JOB-04 终止结算 | ORG-11、ADM-27、MGT-06 | 202生效后≤5分钟；按原生效事实逐单、消息与进度同事务 |
| JOB-05 清理 | 到期HTTP410或登录403 | 到点读取拒绝，清理不决定权限；不删本人底层历史和手机号长期事实 |
| JOB-06 限制恢复核对/解除 | /me限制、ADM-22、站内消息 | 正常组织者限制已随违规事务提交；恢复核对不改既有期限，到点解禁实时判断，消息去重 |

任务失败保留未完成项和重试次数，恢复从数据库读取最初期限；重复执行不延后截止/保留期、不重复日志业务成功事实、不重复通知。管理员查看任务不能触发后台按“当前时刻”重算终止退款资格。

### 6.1 终止与自然结束的交叉判定

以终止事务内的effectiveAt、场次计划endsAt和签到事实固定分类：

- `endsAt > effectiveAt`的未结束场次：WAITLISTED/OFFERED/UNPAID取消；REGISTERED退款；CHECKED_IN完成；旧终态不动。
- `endsAt ≤ effectiveAt`的已结束场次：已结算终态不动；若JOB-03积压仍REGISTERED/CHECKED_IN，则按**原自然结束时点**续办缺席/完成，不能退款或永久遗漏；超时记录仍按原结束时刻计算。
- 先前已正常结算场次保留NATURAL快照及结束容量；未结束场次以终止时刻和容量结算。活动终止汇总等待所有场次结清，以活动原因CANCEL/TAKEDOWN标记，不覆盖已结算场次原因。
- 整活动计划最晚结束已到，即使events.status尚未由任务更新，也按实际ENDED限制公开访问、编辑、下架、检票与新交易；JOB-03补记持久ENDED，不允许在积压期间改写为TAKEN_DOWN。

### 6.2 批量消息与导出任务

JOB-04不仅含订单结算项，还必须含终止通知项：终止时固化接收人集合（组织者、相关参与者、关联检票账号），含没有订单的组织者/检票员。每个接收人消息与其处理项进度同事务，事件+接收人唯一；同一参与者多订单不重复收到同一终止消息。订单取消/退款消息按各自订单事件独立去重。整体5分钟完成目标包含订单、全部必发通知和报表；账号已销毁仍保存其通知记录，不能因此恢复登录。地点/容量编辑通知只给该次实际变更影响的有效订单用户。

新增内部操作JOB-07：异步CSV生成。MGT-02在同事务写导出记录、筛选条件与持久任务；重启后可续办/从头重建未发布临时文件，发布前再次校验当前活动和保留期限。到期转EXPIRED，不再重试重建。筛选数据以生成执行时的一致读快照为准，ExportView补充`dataAsOf:time?`，避免名单变化后误解导出时间；完成才提供downloadUrl。导出任务可在ADM-26/27查看，不新增公开任意执行入口。

所有聚合/枚举入口共用DataRetentionPolicy：EventManage仅配置元数据；MGT-*的受限业务数据、ADM-16/31批量列表及相关幂等缓存不能绕过7天。已知单条ORDER/PAYMENT/REFUND/TICKET/CHECKIN可由ADM-25追踪，合法原退款依据按ADM-17/18处理；不得在返回中附带同活动其他订单。

## 7. 接口验收与变更规则

- 逐接口检查角色、本人/他人、授权改绑、匿名、强制改密、已停用及实际活动到期；文件下载同样检查。
- 逐写接口检查相同键同参、同键异参、响应丢失、过期后重放与当前状态返回；验证服务端不能被未知字段注入。
- 对照状态表、时间边界、库存公式、退款合法依据和消息/日志事务做集成测试；真实数据库夹具不能由页面mock替代。
- 固定 DTO 与错误code；改名/新增必填字段必须先改本文和双方类型，追踪表同步更新。新增可选字段也要更新mock，禁止前端临时猜字段。
- 当前没有OpenAPI生成文件或自动契约校验服务；实现骨架阶段应将本文字段落为Java/TS类型并以代表性请求响应夹具校验。本次文档检查不等于接口已运行。

后端设计材料：[字段/建表SQL](database-design.md)、[Redis键字典](redis-design.md)、[Java参数落点](backend-contracts.md)、[自审记录](backend-review.md)。原HTTP业务字段保持不变。

本轮实现及待接入项见[backend-implementation.md](backend-implementation.md)。SYS-01健康诊断未就绪时503仍保留最小组件状态，普通业务失败仍data=null。
