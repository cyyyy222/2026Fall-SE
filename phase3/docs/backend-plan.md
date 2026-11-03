> 2026-11-03 用户最新确认：同手机号可分别拥有参与者/组织者账号，同角色内唯一；新增本地验证码注册/登录，管理员账号分类与检票凭据交接见[账号与登录调整](account-auth-update.md)，该调整优先于旧版相关口径。

# Java 后端框架实施方案（供审阅）

> 当前进展：MySQL/Redis已接通，开发数据显式初始化完成，前端已切HTTP，开发后台任务按用户授权启用。最新账号、命令和验证范围见[HTTP演示说明](http-demo.md)。以下v0.3保留方案形成时的默认离线边界；启动不指定profile时仍离线，不自动执行DDL或导入。

版本：v0.3 · 日期：2026-11-02 · 状态：按最新授权已实现业务初版，默认离线；真实MySQL/Redis接入及验收未执行。

本方案按用户本次要求采用 **Java + Spring Boot + Controller / Service / DAO 三层 + MySQL + Redis**。按最新授权，本轮完成文档自审、33张表与DDL、Redis分类、DTO/实体/DAO端口和三层骨架；准备工作已获用户审阅通过，本轮继续可独立编写的业务代码；**不接入个人配置、不执行建表**。前端继续使用mock。

## 1. 与现有需求的关系

业务规则继续使用[phase1 version5正文及附录](../../phase1/my_exp_cont/requirements.md)、[项目文档](project.md)、[API契约](api.md)和[需求追踪表](requirements-traceability.md)。已有桌面前端和莫兰迪风格保留。

本次明确替代的技术选择：原SQLite及`BEGIN IMMEDIATE`方案改为MySQL InnoDB事务、行锁和唯一约束；Redis承担短期辅助数据与技术限流。原SQL不能仅修改连接字符串就复用，迁移、条件唯一、锁和时间字段均须按MySQL重新实现。

REQ-023中的SQLite是原测试参照，不是当前目标数据库。现改MySQL后，仍以相同数据规模、50并发/100查询和20账号抢最后一票的目标重新验收，记录数据库、Redis、硬件、配置与提交版本，不将旧参照写成MySQL已经达标。

所有89项业务HTTP接口的路径、方法、字段、状态和错误约定以api.md为主。本文只规定Java落点和实施顺序，不另创一套前端协议。[backend-endpoints.md](backend-endpoints.md)逐项对应Controller方法、Service和框架阶段状态。

## 2. 技术与环境基线

| 项目 | 选择 | 实施约束 |
| --- | --- | --- |
| Java | JDK 17，UTF-8 | 使用Jakarta包；Java records用于不可变DTO。先确认真实JDK路径，不把Oracle启动器路径当成版本验证 |
| Web框架 | Spring Boot 3.5系，计划3.5.16 | Maven构建时验证制品可用性并固定精确版本；Spring Framework/Security/Jackson等遵循Boot BOM |
| 构建 | Maven + Maven Wrapper | 项目附mvnw/mvnw.cmd；可用本机Maven，也可用Wrapper重复构建 |
| 数据库 | MySQL 8.4 LTS / InnoDB / utf8mb4 | 使用独立开发库；实际已有实例版本和连接权限在搭建时核实 |
| Redis | 计划Redis 7.4兼容基线 | 初期只使用基础字符串、TTL、计数及Lua，不要求Redis模块；已有实例先核实版本与认证 |
| 数据操作 | Spring JDBC，NamedParameterJdbcTemplate，DAO接口+Jdbc实现 | 参数化SQL；SELECT/INSERT/UPDATE语义明确；事务由Service控制 |
| MySQL驱动 | mysql-connector-j | 使用Boot管理的相容版本，不保留SQLite驱动 |
| Redis客户端 | spring-boot-starter-data-redis / Lettuce | StringRedisTemplate，显式序列化；不使用Java对象原生序列化 |
| 迁移 | Flyway Core + flyway-mysql | DDL版本化；启动校验，禁止以自动删表/重建方式启动 |
| 安全 | Spring Security、随机会话Cookie、CSRF、BCrypt | 延续固定7天、单有效会话、强制改密；不新增JWT协议 |
| 测试 | JUnit 5、Spring Boot Test、Security Test、真实MySQL/Redis集成测试 | 不用H2/内存Map替代MySQL约束和Redis行为验收 |

实际构建使用 `D:\javajdk17` 的 JDK 17.0.17 和 Maven 3.9.4，Boot 3.5.16父POM已成功解析。默认不创建数据库/Redis客户端，未查询已有服务版本、账号或业务数据。

Spring Boot 3.5官方文档说明该系列支持Java 17及Maven 3.6.3以上；本项目选择该版本线是实现决定，不宣称它是最新主版本。[Spring Boot系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)

## 3. 传统三层及目录

请求方向：

```text
浏览器 /api 请求
    ↓
TraceId过滤器 → Spring Security过滤链 → MVC请求约定拦截器
    ↓
Controller（HTTP与DTO）
    ↓
Service接口 → ServiceImpl（规则、授权、事务、用例编排）
    ↓
DAO接口 → JdbcXxxDao（SQL） / RedisTransientDao（短期辅助）
    ↓
MySQL（可靠业务数据） / Redis（临时技术数据）
```

计划目录：

```text
phase3/code/backend/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/
├── README.md
├── requests/                         # 可复现的基础接口调用说明
├── src/main/java/com/campus/ticketing/
│   ├── TicketingApplication.java
│   ├── controller/                   # 按auth/account/event/order等子包分组
│   ├── service/                      # XxxService接口
│   │   └── impl/                     # XxxServiceImpl
│   ├── dao/                          # XxxDao接口
│   │   ├── jdbc/                     # JdbcXxxDao，MySQL SQL
│   │   └── redis/                    # RedisTransientDao
│   ├── entity/                       # 内部持久化对象
│   ├── dto/request/                  # 请求DTO、分页/筛选/更新DTO
│   ├── dto/response/                 # 对应api.md的响应DTO
│   ├── enums/                        # 固定角色、状态、错误码
│   ├── config/                       # Web、Security、Jackson、Data、Clock配置
│   ├── filter/                       # trace、会话认证、技术限流
│   ├── interceptor/                  # 接口编号、请求键、查询白名单
│   ├── security/                     # principal、当前用户、授权策略、错误处理
│   ├── exception/                    # BusinessException、全局异常处理
│   ├── scheduler/                    # 调度入口，只调用Service
│   └── common/                       # ApiResponse、PageResult、时间、请求指纹等
├── src/main/resources/
│   ├── application.yml
│   ├── application-connected.yml
│   ├── application-test.yml
│   ├── META-INF/services/             # 校验器值提取器；DDL不放在资源目录
│   └── static/                       # 后续前端产物，开发时仍用Vite
└── src/test/                         # 分层、契约、安全、MySQL/Redis集成测试
```

DAO就是原文档所说的Repository/数据访问职责，本次统一命名为DAO；不再创建一套重复的Repository层。实体不直接作为HTTP响应，业务规则不放在DAO中。

### 3.1 Controller职责

`@RestController`声明方法与路径，接收`@Valid @RequestBody`、路径/查询参数、multipart文件；用`@CurrentRequest`取得服务端RequestContext（身份、幂等键及trace）；调用Service并返回约定状态及DTO。只做协议转换，不直接调用DAO，不在此计算库存、判断退款、执行SQL或操作Redis。

### 3.2 Service职责

接口表达用例，例如`RegistrationService.register`、`PaymentService.pay`、`RefundService.request/retry`、`CheckInService.preview/confirm`。实现类用构造器注入DAO和共用策略，完成对象归属、时限、状态、幂等、事务、日志消息及任务创建。

`@Transactional`放在经Spring代理调用的ServiceImpl公开事务方法上，写操作明确`rollbackFor = Exception.class`。T1/T2、批量逐项和失败日志用独立事务Bean或TransactionTemplate组织，避免同类自调用使事务注解失效。Service之间可调用用例，禁止互相经HTTP调用，禁止循环依赖。

### 3.3 DAO职责

如`UserDao.findActiveByUsername/lockById`、`OrderDao.findByRequest/insert/lockById`、`TicketTypeDao.lockById/updateCapacity`。实现类使用NamedParameterJdbcTemplate与RowMapper，返回Entity/查询投影，不返回ApiResponse；不读取HttpServletRequest、SecurityContext或前端Cookie。

RedisDao仅封装临时技术存取和原子计数。MySQL与Redis不拼成“一个跨库@Transactional事务”。

## 4. MySQL与Redis的明确分工

| 数据/行为 | 可靠来源 | Redis使用边界 |
| --- | --- | --- |
| 用户、密码哈希、会话tokenHash及撤销/到期 | MySQL | 不以Redis作为会话唯一来源，避免缓存重建或丢失改变权限 |
| 活动、场次、票种、占位、订单、支付退款、票、签到 | MySQL | 不在Redis维护另一份权威库存、订单状态或支付成功标记 |
| 请求幂等映射、合法退款依据、成功唯一约束 | MySQL | Redis SET NX不能代替可靠幂等结果 |
| 消息、成功日志、任务待办、终止事实、限制依据、正式统计 | MySQL | 不只保存在缓存或内存定时器中 |
| 登录/注册/找回的技术请求频率 | Redis计数+TTL | 只限制技术请求，不代替30天举报额度、每日发布100额度、处罚或库存规则 |
| 未来短期辅助缓存 | Redis | 本阶段预留读写/TTL工具；不缓存要求“下一次查询反映最新值”的实时计数、权限和余票 |

Redis后续接库阶段用于登录等入口限流与连接健康检查。本轮已编写适配器与限流策略并接到认证Service，默认尚未连接。缓存基础设施可以复用，但不为了“用到Redis”复制一套业务事实或修改API。

key使用环境命名空间，例如`ticketing:local:rate:login:<fingerprint>`，计数与首次TTL用Lua原子执行，避免INCR成功但EXPIRE失败留下长期key。初始限流值见[Redis设计](redis-design.md)，登录另有客户端60次/分钟、账号30次/分钟的组合保护；组合10次/分钟，注册、找回客户端5次/10分钟。使用受控指纹，不将密码、Cookie或手机号原文放进key。[Redis官方限流模式](https://redis.io/docs/latest/commands/incr/)

Redis不可用时：受其保护的认证入口返回503 SERVICE_UNAVAILABLE，不静默停用限流；已提交的MySQL支付/退款不因事后Redis辅助操作失败而假装回滚。业务接口不得在服务方法提交后再执行必须成功的Redis写，造成前端误判交易结果。

## 5. 拦截与安全链

### 5.1 组件和责任

| 组件 | 责任 | 不在此执行的工作 |
| --- | --- | --- |
| TraceIdFilter | 服务端生成traceId，响应头/信封/MDC关联，finally清理线程上下文 | 不记录请求密码、Cookie、token、完整请求体或证据正文 |
| SessionAuthenticationFilter | 读取TICKET_SESSION，调用认证Service校验MySQL会话/账号，建立SecurityContext | 不相信客户端role/userId，不决定库存或退款资格 |
| SecurityFilterChain | 按HTTP方法和路径区分公开、登录、固定角色；拒绝越权、强制改密未完成的业务请求 | 不能只靠URL前缀就认为对象归属正确 |
| Spring CsrfFilter及TokenRepository | 对变更请求校验X-CSRF-TOKEN；匿名登录/注册/找回也保护 | permitAll不等于关闭CSRF |
| RateLimitService（由认证Service门面调用） | DTO语法校验后，仅明确认证入口调用Redis原子限流 | 不把技术请求数当成业务受理次数 |
| ApiContractInterceptor | 从Controller注解取得稳定接口编号，验证通用幂等键格式、声明的查询参数白名单 | 不保存幂等成功结果、不占库存、不读取/缓存密码体 |
| RequestContextResolver | 把已认证principal注入Controller | 不新建一套并行的登录态ThreadLocal |
| AuthorizationService及DataRetentionPolicy | Service内核对归属、当前授权、生命周期和7/180天期限；写事务再核验 | 不以先前页面按钮、过滤器快照或Redis缓存替代最新业务事实 |

Spring官方明确不建议把MVC Interceptor当成唯一安全层，建议尽早在过滤链使用Spring Security。因此这里保留传统拦截器，但登录/安全由Security链负责。[Spring MVC拦截器说明](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/interceptors.html)

### 5.2 会话与CSRF流程

1. 前端GET `/api/auth/csrf`取token，变更请求发送X-CSRF-TOKEN；采用Spring提供的Cookie token repository和与6.5兼容的请求处理器，明确header名称与`/api` cookie path。
2. 登录成功生成高熵不透明会话token，仅tokenHash入MySQL；同账号旧会话在该MySQL事务中撤销，期限从登录起固定7天。
3. Controller把原token写入HttpOnly、SameSite=Lax的TICKET_SESSION，不把它装进返回DTO；HTTPS时Secure，本机HTTP local配置例外。
4. 使用数据库会话加Spring Security的请求级身份，不同时依赖默认JSESSIONID或JWT。不让访问延长会话期限。
5. 登录、退出、改密、重置、停用、注销后按契约撤销会话；登录/退出后前端重取CSRF。Spring单页应用的token刷新需要专门配置与集成测试，不简单删除CSRF过滤器。[Spring Security CSRF说明](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)
6. 强制改密状态仅开放AUTH-01、AUTH-05、ME-01最小投影、ME-03；其他私有接口403。后台任务和业务写入仍需复核最新账号/授权状态，防止请求通过过滤器后权限已经被撤销。

### 5.3 权限及异常顺序

公开读取只允许声明的GET路径；登录、注册、找回是公开POST但仍须CSRF及技术限流。P/O/C/A没有继承关系，使用固定Role枚举与显式角色集合。所有私有下载也进过滤链及对象授权。

无效旧会话不能绕过私有检查；普通公开读取可清理无效Cookie后按匿名访问。未注册路径进入统一不存在处理；已有路由无权限返回403，不能伪装404。请求同时缺少多个条件时先返回过滤链实际识别的错误（如无CSRF的写请求可能先403），业务验收分别提供其他有效条件来检查401/403/409。

Filter/Security错误不保证经过`@RestControllerAdvice`。需要JsonAuthenticationEntryPoint、JsonAccessDeniedHandler及ApiResponseWriter复用相同错误模型，避免返回HTML登录页或堆栈。MVC层则由GlobalExceptionHandler统一处理。

## 6. MySQL数据访问、锁与事务

### 6.1 数据模型与迁移

使用InnoDB外键、唯一约束和检查约束，持久表按项目文档分批迁移。UUID建议CHAR(36)并使用一致的ASCII精确比较规则；名称、邮箱等按各自规则建立规范化字段，不依赖数据库默认大小写规则来猜业务规则。时间保存UTC epoch毫秒BIGINT，DTO输出`+08:00` ISO时间；金额用BIGINT整数角。

MySQL不能直接照搬SQLite的部分唯一索引。计划采用**可空生成列+唯一索引**表达条件唯一，具体DDL须在真实MySQL上测试：

| 约束 | MySQL落地 |
| --- | --- |
| 未注销用户名/邮箱/手机号唯一 | `deleted_at_ms IS NULL`时生成规范化唯一字段，逻辑删除后生成NULL；停用不释放用户名占用 |
| 同用户同场最多一个有效订单 | 有效状态生成active_guard=1，其余NULL，UNIQUE(user_id,session_id,active_guard) |
| 每单一次成功支付/退款 | SUCCESS时生成success_guard=1，否则NULL，分别UNIQUE(order_id,success_guard) |
| 每单一个未关闭USER退款依据 | USER且ACCEPTED/FAILED时生成open_user_guard=1，否则NULL，UNIQUE(order_id,open_user_guard) |
| 业务幂等/票/签到/消息/事实 | 报名用户+请求键、支付单+尝试键、order_id票唯一、ticket_id签到唯一、event_key+receiver、event_id+fact_key等普通唯一索引 |

不通过修改历史订单到另一状态来回避唯一约束；同用户名已注销后重注册仍保留原内部编号及手机号身份关联。

后续审阅通过并接入独立数据库后，才把[建表SQL审阅稿](mysql-schema.sql)拆为Flyway版本迁移。当前关闭Flyway和SQL初始化；DDL仅存docs，启动不会执行创建。计划按身份/活动/交易/治理任务分批迁移；迁移脚本入Git，数据和密码不入Git。Flyway MySQL支持需要相应模块，除core外明确加入`org.flywaydb:flyway-mysql`。[Flyway MySQL说明](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)

### 6.2 事务策略

业务写入由Service事务管理，计划采用READ_COMMITTED和显式`SELECT ... FOR UPDATE`；关键校验与写入在同一连接、同一事务中进行。JdbcTemplate通过Spring事务管理取得连接，DAO不自己开启第二个连接、COMMIT或ROLLBACK。[Spring JDBC连接与事务](https://docs.spring.io/spring-framework/reference/data-access/jdbc/connections.html)

锁定读取应在事务中执行，锁在提交/回滚释放；不能先锁定、结束事务，再去扣库存。[Oracle官方MySQL 8.4锁定读取说明](https://docs.oracle.com/cd/E17952_01/mysql-8.4-en/innodb-locking-reads.html)

| 用例 | 需要串行保护的事实 |
| --- | --- |
| 报名/候补/递补/支付/退款 | 活动当前状态、用户资格与跨场有效订单、场次/票种容量、在先合格候补、订单与尝试唯一性 |
| 取消与签到竞争 | 以同活动行作为共同锁点，再读最新签到/订单；不能只锁不同的订单而放过整活动竞争 |
| 同用户跨活动时间冲突 | 对参与者行串行保护其新报名，再读取有效订单比较半开区间；仅用户+场次唯一索引不够 |
| 审核发布 | 活动状态、组织者排期/限制、每场当前检票授权、当日发布额度记录；额度条件更新不超过100 |
| 末位检票账号保护 | 活动/场次与当前可用授权集合；不能并发移走最后两个可用账号 |

事件相关写操作统一锁顺序：活动行 → 涉及账号/手机号身份（ID排序）→ 场次 → 票种 → 订单 → 票/尝试/依据；同类多对象按ID升序。跨活动有效订单的检查在参与者锁下使用当前已提交读取，不额外以相反顺序锁遍其他活动；跨活动批处理分成单活动事务。实现时逐用例审查锁集合，不能凭“用了FOR UPDATE”宣称并发正确。

普通公开查询不加写锁；名单/导出等多SQL一致读取使用明确的只读REPEATABLE_READ快照，不锁全站，和写事务READ_COMMITTED分开配置。数据分页、稳定排序、索引和深分页限制按契约。遇到可识别死锁/锁等待失败，回滚完整单项事务、有限重试；提交结果未知时按持久幂等记录查询，不用新请求盲目重做。

### 6.3 关键事务边界保留

- 支付成功、唯一票、订单REGISTERED、成功日志、必发消息、幂等结果同事务；确认及支付沿用原占位。
- 退款T1固化原合法依据/initialAttemptId/受理键，T2提交退款/票失效/订单/释放/日志消息。T2失败保留T1，按原键恢复；不把两个阶段包进一个会一起回滚的外层事务。
- 审核退回草稿属于应提交的业务结果，不能抛异常让退回也回滚。
- 终止先原子保存状态、生效事实、逐单与通知待办，再由持久任务分项处理；已结束场次按原自然事实补结算。
- 业务失败日志只在原事务完成回滚后另起事务尝试；不能在持有同一批MySQL锁时误用嵌套REQUIRES_NEW造成等待，也不能声称数据库不可写时日志仍已保存。

Redis不承担这些跨表原子性。框架阶段仅搭出共用事务、DAO与测试基础，完整业务规则在后续模块阶段实现。

## 7. 接口、DTO、异常与请求约定

沿用ApiResponse<T>、ApiErrorResponse、PageResult<T>、Mutation和专用结果DTO。Controller显式返回200/201/202等状态，不以HTTP200包装所有错误；下载成功是文件，失败仍是约定JSON。错误处理覆盖MVC、Security、未知路由、参数绑定、multipart超限和数据库不可用。

DTO分别放在dto/requests、dto/queries、dto/views，持久对象放entity。DTO使用盒装类型表达缺失/空值，避免缺少capacity自动变0。PATCH采用可识别absent/null/value的PatchField，防止只改简介时清空场次；不向业务层传任意Map或原始HTTP对象。Jackson拒绝未知写字段，查询白名单拒绝未声明参数；CodePointLength校验与前端Unicode长度规则一致。枚举、UUID、整数角和时区转换统一配置。

幂等拦截器仅检查是否需要header、格式及接口编号。真正的参数指纹、原结果、最新对象投影、业务唯一性和MySQL事务由Service/DAO负责；Redis限流不能抢先把一个未提交业务标成“已成功”。鉴权优先于读取原结果，重放也受数据保留期约束。

原501占位已替换为业务初版；默认无数据库的请求经鉴权及语法校验后返回503，不用内存存储或空结果伪装成功。

## 8. 模块与三层落点

| 模块 | 主要Controller | Service接口 | 主要DAO |
| --- | --- | --- | --- |
| 认证/本人账号 | AuthController、MeAccountController | AuthService、AccountService | UserDao、LoginSessionDao、PhoneIdentityDao |
| 类别/公开活动 | PublicCatalogController、PublicEventController | CategoryQueryService、PublicEventService | CategoryDao、EventDao、EventSessionDao、TicketTypeDao |
| 组织者活动/审核 | OrganizerEventController、AdminReviewController | EventService、EventReviewService、EventTerminationService | EventDao、EventReviewDao、CheckerAssignmentDao、PublicationQuotaDao |
| 报名/订单/候补 | RegistrationController、OrderController | RegistrationService、OrderService、WaitlistService | RegistrationRequestDao、OrderDao、TicketTypeDao |
| 支付/退款/票 | PaymentController、RefundController、TicketController | PaymentService、RefundService、TicketService | PaymentAttemptDao、RefundRequestDao、RefundAttemptDao、TicketDao |
| 检票 | CheckerController | CheckInService | CheckerAssignmentDao、TicketDao、CheckInDao、OrderDao |
| 名单/报表/文件 | AttendeeController、ExportController、StatisticsController、PosterController | AttendeeService、ExportService、StatisticsService、PosterService | ExportDao、StatisticsSnapshotDao及只读投影DAO |
| 举报/治理/限制 | ReportController、AdminReportController、AdminRestrictionController | ReportService、InvestigationService、RestrictionService | ReportDao、BehaviorFactDao、RestrictionDao、PhoneIdentityDao |
| 消息/审计/运维 | MessageController、AdminAuditController、AdminJobController | MessageService、AuditQueryService、JobQueryService | MessageDao、AuditDao、JobDao |
| 管理员账号/财务 | AdminAccountController、AdminCategoryController、AdminFinanceController、AdminTerminationController | AccountAdminService、CategoryAdminService、FinanceAuditService、RefundService | 对应账号/类别/支付/退款DAO |
| 系统基础 | SystemController | SystemStatusService、RateLimitService | 轻量MySQL探测、RedisTransientDao |

每项业务HTTP接口的具体方法名和框架阶段是否可用见[逐接口实施表](backend-endpoints.md)，不以模块表取代89项接口契约。

## 9. 当前业务初版交付边界

用户已审阅准备材料并授权实现无需个人配置的业务代码。当前89项业务HTTP接口已接具体Service用例；实际数据访问采用RecordStore/JdbcRecordStore和33实体注册表，SQL只在DAO。复杂规则按Identity/Event/Order/CheckIn/Governance/Reporting/Durable等用例组织，仍属于Service层。

MySQL会话、Redis HMAC多维限流、报名支付退款、检票与治理、持久任务/文件/统计等初版已编写；AUTH-01可离线使用，其他请求无库时503。默认连接、Flyway、SQL初始化、任务均关闭；不创建管理员。详细实现、测试和信息缺口见[业务交付](backend-implementation.md)。

原1～8节仍是分层、规则和事务设计依据；代码已实现不等于数据库中的CHECK/唯一/锁/回滚、Redis Lua或性能已通过。待提供独立连接信息后，先核验DDL与基础身份链，再按活动→交易→结算治理进行真实垂直联调。前端暂保持mock。
