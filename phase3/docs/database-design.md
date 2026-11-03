> 2026-11-03 用户最新确认：同手机号可分别拥有参与者/组织者账号，同角色内唯一；新增本地验证码注册/登录，管理员账号分类与检票凭据交接见[账号与登录调整](account-auth-update.md)，该调整优先于旧版相关口径。

# MySQL 数据库设计与建表命令（审阅稿）

版本：v0.1 · 2026-11-02 · **未连接、未执行、未验证真实 MySQL DDL**。

SQL 使用 MySQL 8.4 / InnoDB。当前工程关闭 Flyway、SQL 初始化及自动建库；本文件与 [独立 SQL](mysql-schema.sql) 仅供审阅。不要放入 resources/schema.sql 或启用迁移。后续确认连接与独立开发库后，另行授权执行；已存在的库应先核对结构，不能直接重跑建库语句。

## 统一约束

- ID：CHAR(36) / ascii_bin；JSON DTO 使用 UUID。时间：UTC epoch 毫秒 BIGINT，接口统一 +08:00；北京时间自然日额度使用 DATE。金额：BIGINT 整数角。
- 字符串使用 utf8mb4_0900_bin 精确比较；用户名/邮箱/类别另外保存应用规范值，只折叠 ASCII 大小写。Unicode 码点长度、trim、时间粒度和跨行完整性由 Service 校验。
- 所有外键 RESTRICT，不级联删历史；注销/授权撤销/草稿子对象删除用逻辑标记。历史订单、票、支付、退款、签到、手机号事实持续保留。审计180天物理清理，消息180天逻辑可读；导出/名单/正式统计的可读期限由活动实际终结+7天实时判定，缓存不绕过。
- 每表显式字段，无自动时间默认值；所有写入使用注入 Clock 和服务器ID。生成列只供数据库约束，不是可写实体属性。BOOLEAN 在 MySQL 为 TINYINT(1)，JDBC 映射 Boolean。
- 各状态枚举详见 api.md 与 Java enums；DDL关键唯一/状态 CHECK 是第二防线，不能替代 Service 状态机。草稿允许空值；提交、发布和已发布编辑须补全并校验。
- 概念上相关的 event/session/type/order/payment 组合必须在同一事务校验归属；单列外键仅保证对象存在，不能证明其属于同一聚合。统计、导出必须再次核对权限/期限。
- 占位从 OFFERED/UNPAID/REGISTERED/CHECKED_IN/COMPLETED/ABSENT 订单派生；WAITLISTED/CANCELLED/REFUNDED不占位。锁票种/场次后再查计数，不存容易漂移的第二份库存。
- 单会话唯一索引按未撤销表达；新登录必须在锁账号后撤销旧行（包括已到期未撤销行）再插入。到期判定始终实时检查 expires_at_ms。
- 通用幂等仅保存结论/资源引用，读取时重投影和复核期限；退款T1还在 refund_requests 持久保存受理键及完整资格，通用幂等清理不能破坏其恢复。

## 33 张表与字段字典

| 表 | Java 实体 | 用途 |
| --- | --- | --- |
| `phone_identities` | `PhoneIdentityEntity` | 长期手机号身份；注销不删除，跨账号继承黑名单和限制 |
| `users` | `UserEntity` | 账号；逻辑删除释放登录标识，停用不释放 |
| `login_sessions` | `LoginSessionEntity` | 固定七天、不滑动续期；历史撤销记录保留 |
| `recoveries` | `RecoveryEntity` | 人工找回；受理时固定目标账号，防同名重注册接管 |
| `categories` | `CategoryEntity` | 类别；停用不释放名称 |
| `stored_files` | `StoredFileEntity` | 受控海报/CSV元数据；文件不直接静态开放 |
| `events` | `EventEntity` | 活动配置；不在管理DTO泄漏过期订单计数 |
| `event_sessions` | `EventSessionEntity` | 场次；草稿删除使用逻辑删除保留检票授权审计 |
| `ticket_types` | `TicketTypeEntity` | 票种配置；余票按权威订单派生，无Redis库存 |
| `checker_assignments` | `CheckerAssignmentEntity` | 当前授权及不可变授权历史；一个账号至多一个当前场次 |
| `event_reviews` | `EventReviewEntity` | 每次审核结论及退回理由 |
| `publication_quotas` | `PublicationQuotaEntity` | 全站北京时间每日100次成功发布额度；非组织者单独100次 |
| `orders` | `OrderEntity` | 单人单票订单；排队/占位/历史快照 |
| `registration_requests` | `RegistrationRequestEntity` | 每个报名键永久指向原订单，包括返回已有有效单的新键 |
| `payment_attempts` | `PaymentAttemptEntity` | 模拟支付尝试；每单最多一笔成功 |
| `refund_requests` | `RefundRequestEntity` | T1合法退款依据；先受理后执行，失败仍可恢复 |
| `refund_attempts` | `RefundAttemptEntity` | T2执行记录；失败不使原单/票/库存改变 |
| `tickets` | `TicketEntity` | 全局唯一电子票；每订单只有一票 |
| `checkins` | `CheckInEntity` | 不可变首次签到事实；保留当时检票员与场次 |
| `reports` | `ReportEntity` | 用户举报及额度计数原始事实；驳回不返还额度 |
| `investigations` | `InvestigationEntity` | 管理员核查，同活动同事实去重 |
| `investigation_reports` | `InvestigationReportEntity` | 合并举报关联；不把关系数组塞进活动行 |
| `behavior_facts` | `BehaviorFactEntity` | 长期处罚依据；正常缺席/主动成功退款/组织者违规 |
| `restrictions` | `RestrictionEntity` | 15天限制历史；到期实时判断，无人工任意解除 |
| `restriction_facts` | `RestrictionFactEntity` | 已消费事实；同一事实不能再次用于处罚 |
| `operation_records` | `OperationRecordEntity` | 通用持久幂等；保存原结论和资源引用，不存令牌/私有报表响应 |
| `messages` | `MessageEntity` | 持久站内消息；180天逻辑可读，首次已读不延长动作时限 |
| `audit_logs` | `AuditLogEntity` | 180天物理清理审计；成功事实与业务同事务 |
| `jobs` | `JobEntity` | JOB-01至07持久任务；只查看，无HTTP任意执行入口 |
| `job_items` | `JobItemEntity` | 逐单/逐接收人待办；无订单的组织者/检票员也必须收通知 |
| `termination_facts` | `TerminationFactEntity` | 终止最初生效时逐订单结算依据；不按恢复时刻重新判定退款 |
| `exports` | `ExportEntity` | 异步CSV；元数据受活动终结加七天动态边界保护 |
| `statistics_snapshots` | `StatisticsSnapshotEntity` | 结算完成正式快照；比率从整数分子分母推导，不保存平均百分数 |

### phone_identities

长期手机号身份；注销不删除，跨账号继承黑名单和限制

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 服务端身份UUID |
| `phone` | `VARCHAR(11)` | N | 规范化11位手机号，敏感字段 |
| `cancellation_count` | `INT` | N | 累计注销次数0至3 |
| `last_cancelled_at_ms` | `BIGINT` | Y | 上次注销时刻 |
| `blacklisted_at_ms` | `BIGINT` | Y | 第三次注销永久禁止重新注册 |
| `created_at_ms` | `BIGINT` | N | 创建UTC毫秒 |
| `version` | `BIGINT` | N | 乐观锁版本 |

约束/索引：

- `UNIQUE KEY uk_phone (phone)`
- `CHECK (cancellation_count BETWEEN 0 AND 3)`

### users

账号；逻辑删除释放登录标识，停用不释放

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 账号UUID；不因重注册复用 |
| `username` | `VARCHAR(60)` | N | 用户名展示原值 |
| `username_normalized` | `VARCHAR(60)` | N | trim并只折叠ASCII大小写 |
| `email` | `VARCHAR(254)` | Y | 所有角色可空；填写时通用邮箱校验，不设后缀白名单 |
| `email_normalized` | `VARCHAR(254)` | Y | 邮箱规范值 |
| `phone_identity_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | P/O必有长期手机号身份 |
| `login_phone_identity_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 当前登录号码；空时沿用原phone_identity_id，原字段保留业务身份 |
| `password_hash` | `VARCHAR(100)` | N | BCrypt，不存原文 |
| `role` | `VARCHAR(20)` | N | PARTICIPANT/ORGANIZER/CHECKER/ADMIN |
| `display_name` | `VARCHAR(60)` | Y | 本人姓名，可重名 |
| `enabled` | `BOOLEAN` | N | 账号启用状态 |
| `must_change_password` | `BOOLEAN` | N | 强制改密 |
| `deleted_at_ms` | `BIGINT` | Y | 逻辑删除时刻 |
| `unavailable_reason` | `VARCHAR(200)` | Y | 活动终止等凭据失效原因 |
| `created_at_ms` | `BIGINT` | N | 创建时刻 |
| `updated_at_ms` | `BIGINT` | N | 最后变更时刻 |
| `version` | `BIGINT` | N | 管理编辑版本 |

约束/索引：

- `FOREIGN KEY (phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `active_username VARCHAR(60) GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN username_normalized ELSE NULL END) STORED`
- `active_email VARCHAR(254) GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN email_normalized ELSE NULL END) STORED`
- `active_phone_identity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN COALESCE(login_phone_identity_id,phone_identity_id) ELSE NULL END) STORED`
- `UNIQUE KEY uk_user_name (active_username)`
- `UNIQUE KEY uk_user_email (active_email)`
- `UNIQUE KEY uk_user_phone_role (active_phone_identity_id,role)`
- `CHECK (role IN ('PARTICIPANT','ORGANIZER','CHECKER','ADMIN'))`
- `CHECK (role NOT IN ('PARTICIPANT','ORGANIZER') OR phone_identity_id IS NOT NULL)`
- `KEY ix_users_admin (role,created_at_ms,id)`

### login_sessions

固定七天、不滑动续期；历史撤销记录保留

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 会话记录UUID |
| `user_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 当前登录账号 |
| `token_hash` | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin` | N | 高熵token的SHA256 |
| `created_at_ms` | `BIGINT` | N | 登录时刻 |
| `expires_at_ms` | `BIGINT` | N | 登录时刻加七天 |
| `revoked_at_ms` | `BIGINT` | Y | 撤销时刻 |
| `revocation_reason` | `VARCHAR(60)` | Y | 撤销原因 |

约束/索引：

- `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_session_hash (token_hash)`
- `active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN revoked_at_ms IS NULL THEN 1 ELSE NULL END) STORED`
- `UNIQUE KEY uk_session_active (user_id,active_guard)`
- `KEY ix_session_expiry (expires_at_ms)`
- `CHECK (expires_at_ms = created_at_ms + 604800000)`

### recoveries

人工找回；受理时固定目标账号，防同名重注册接管

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 找回请求UUID |
| `account` | `VARCHAR(60)` | N | 请求填写账号 |
| `account_normalized` | `VARCHAR(60)` | N | 规范化账号 |
| `phone` | `VARCHAR(11)` | N | 丢失访问时原账号实际绑定手机号 |
| `current_phone` | `VARCHAR(11)` | Y | 申诉当前可用手机号；新申请必填 |
| `role` | `VARCHAR(20)` | Y | 原账号P/O角色；新申请必填 |
| `statement` | `VARCHAR(2000)` | Y | 申诉陈述20～2000字；新申请必填 |
| `past_behavior` | `VARCHAR(2000)` | Y | 本人以往行为20～2000字；新申请必填 |
| `contact_verified_at_ms` | `BIGINT` | Y | 当前手机号验证码通过时间；旧缺失申请不可直接改密 |
| `target_account_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 受理时匹配账号，无匹配为NULL |
| `status` | `VARCHAR(20)` | N | PENDING/VERIFIED/RESOLVED/REJECTED |
| `verification_note` | `VARCHAR(2000)` | Y | 人工核验说明 |
| `verified_by` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 核验管理员 |
| `verified_at_ms` | `BIGINT` | Y | 核验时间 |
| `created_at_ms` | `BIGINT` | N | 受理时刻 |
| `resolved_at_ms` | `BIGINT` | Y | 实际完成或拒绝时刻 |

约束/索引：

- `FOREIGN KEY (target_account_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `open_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','VERIFIED') THEN 1 ELSE NULL END) STORED`
- `UNIQUE KEY uk_recovery_pending (account_normalized,phone,open_guard)`
- `KEY ix_recovery_queue (status,created_at_ms,id)`
- `CHECK (status IN ('PENDING','VERIFIED','RESOLVED','REJECTED'))`

### categories

类别；停用不释放名称

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 类别UUID |
| `name` | `VARCHAR(60)` | N | 展示名称 |
| `name_normalized` | `VARCHAR(60)` | N | trim和ASCII大小写归一 |
| `enabled` | `BOOLEAN` | N | 可供新提交选择 |
| `version` | `BIGINT` | N | 乐观锁版本 |
| `created_at_ms` | `BIGINT` | N | 创建时刻 |
| `updated_at_ms` | `BIGINT` | N | 更新时刻 |

约束/索引：

- `UNIQUE KEY uk_category_name (name_normalized)`

### stored_files

受控海报/CSV元数据；文件不直接静态开放

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 文件UUID |
| `owner_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 创建账号 |
| `kind` | `VARCHAR(20)` | N | POSTER/EXPORT |
| `storage_key` | `VARCHAR(255)` | N | 受控相对存储键，不接收客户端路径 |
| `content_type` | `VARCHAR(100)` | N | 服务端识别MIME |
| `byte_size` | `BIGINT` | N | 实际字节数 |
| `sha256` | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin` | N | 内容摘要 |
| `created_at_ms` | `BIGINT` | N | 创建时刻 |
| `deleted_at_ms` | `BIGINT` | Y | 清理标记 |

约束/索引：

- `FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_file_key (storage_key)`
- `CHECK (byte_size >= 0)`
- `CHECK (kind IN ('POSTER','EXPORT'))`

### events

活动配置；不在管理DTO泄漏过期订单计数

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 活动UUID |
| `organizer_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 组织者账号 |
| `status` | `VARCHAR(24)` | N | 活动六态 |
| `name` | `VARCHAR(60)` | Y | 草稿可空 |
| `category_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 草稿可空 |
| `category_name_snapshot` | `VARCHAR(60)` | Y | 提交/发布时类别名称 |
| `proposed_category_name` | `VARCHAR(60)` | Y | 组织者随草稿提出的新类别名称；批准时创建或复用 |
| `description` | `VARCHAR(500)` | Y | 简介 |
| `poster_file_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 当前海报 |
| `submitted_at_ms` | `BIGINT` | Y | 本次提交时刻 |
| `published_at_ms` | `BIGINT` | Y | 发布时刻 |
| `terminated_at_ms` | `BIGINT` | Y | 整活动终结基准：自然取末场计划结束，取消下架取原生效时间，不随任务延迟 |
| `termination_reason` | `VARCHAR(200)` | Y | 取消/下架原因 |
| `last_review_reason` | `VARCHAR(200)` | Y | 最近退回原因 |
| `settlement_status` | `VARCHAR(20)` | N | NOT_DUE/PROCESSING/COMPLETED |
| `created_at_ms` | `BIGINT` | N | 创建时刻 |
| `updated_at_ms` | `BIGINT` | N | 更新时刻 |
| `version` | `BIGINT` | N | 管理配置版本；不随支付计数更新 |

约束/索引：

- `FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (poster_file_id) REFERENCES stored_files(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `CHECK (status IN ('DRAFT','PENDING_REVIEW','PUBLISHED','CANCELLED','TAKEN_DOWN','ENDED'))`
- `KEY ix_event_owner (organizer_id,updated_at_ms,id)`
- `KEY ix_event_review (status,submitted_at_ms,id)`

### event_sessions

场次；草稿删除使用逻辑删除保留检票授权审计

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 前端稳定UUID |
| `event_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 活动 |
| `name` | `VARCHAR(60)` | Y | 草稿可空，trim后精确比较 |
| `starts_at_ms` | `BIGINT` | Y | 按分钟精确开始 |
| `ends_at_ms` | `BIGINT` | Y | 按分钟精确结束 |
| `location` | `VARCHAR(60)` | Y | 当前地点，历史票也读此值 |
| `capacity` | `INT` | Y | 场次容量 |
| `sales_opens_at_ms` | `BIGINT` | Y | 精确分钟 |
| `registration_closes_at_ms` | `BIGINT` | Y | 报名截止 |
| `settlement_status` | `VARCHAR(20)` | N | 场次结算进度 |
| `deleted_at_ms` | `BIGINT` | Y | 仅草稿聚合删除 |
| `version` | `BIGINT` | N | 内部配置版本 |

约束/索引：

- `FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN 1 ELSE NULL END) STORED`
- `UNIQUE KEY uk_session_name (event_id,name,active_guard)`
- `KEY ix_session_time (starts_at_ms,id)`
- `KEY ix_session_end (ends_at_ms,id)`
- `CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 9999)`
- `CHECK (starts_at_ms IS NULL OR ends_at_ms IS NULL OR starts_at_ms < ends_at_ms)`

### ticket_types

票种配置；余票按权威订单派生，无Redis库存

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 前端稳定UUID |
| `session_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 所属场次 |
| `name` | `VARCHAR(60)` | Y | 草稿可空，trim后精确比较 |
| `capacity` | `INT` | Y | 票种容量 |
| `price_jiao` | `BIGINT` | Y | 整数角，草稿可空 |
| `sale_stopped` | `BOOLEAN` | N | 主动停售 |
| `deleted_at_ms` | `BIGINT` | Y | 草稿删除 |
| `version` | `BIGINT` | N | 内部配置版本 |

约束/索引：

- `FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN 1 ELSE NULL END) STORED`
- `UNIQUE KEY uk_type_name (session_id,name,active_guard)`
- `CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 9999)`
- `CHECK (price_jiao IS NULL OR price_jiao BETWEEN 0 AND 9999990)`

### checker_assignments

当前授权及不可变授权历史；一个账号至多一个当前场次

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 授权UUID |
| `checker_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 检票账号 |
| `session_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 授权场次 |
| `assigned_by` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 操作管理员 |
| `assigned_at_ms` | `BIGINT` | N | 授权时刻 |
| `revoked_at_ms` | `BIGINT` | Y | 改绑/终止撤销时刻 |
| `revocation_reason` | `VARCHAR(200)` | Y | 撤销原因 |

约束/索引：

- `FOREIGN KEY (checker_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (assigned_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN revoked_at_ms IS NULL THEN 1 ELSE NULL END) STORED`
- `UNIQUE KEY uk_checker_current (checker_id,active_guard)`
- `KEY ix_assignment_session (session_id,revoked_at_ms,checker_id)`

### event_reviews

每次审核结论及退回理由

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 审核UUID |
| `event_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 活动 |
| `reviewer_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 管理员 |
| `submitted_version` | `BIGINT` | N | 被审核配置版本 |
| `outcome` | `VARCHAR(24)` | N | PUBLISHED/REJECTED/RETURNED_TO_DRAFT |
| `reason_code` | `VARCHAR(80)` | Y | 退回机器原因 |
| `reason` | `VARCHAR(200)` | Y | 审核意见 |
| `created_at_ms` | `BIGINT` | N | 审核时刻 |

约束/索引：

- `FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `KEY ix_review_event (event_id,created_at_ms,id)`

### publication_quotas

全站北京时间每日100次成功发布额度；非组织者单独100次

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 额度行UUID |
| `day` | `DATE` | N | 北京时间自然日 |
| `published_count` | `INT` | N | 成功发布计数，不因终止返还 |
| `version` | `BIGINT` | N | 条件更新版本 |

约束/索引：

- `UNIQUE KEY uk_publish_day (day)`
- `CHECK (published_count BETWEEN 0 AND 100)`

### orders

单人单票订单；排队/占位/历史快照

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 订单UUID |
| `request_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 最初报名请求UUID；别名映射另表 |
| `user_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 参与者账号 |
| `phone_identity_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 事实归属长期身份 |
| `event_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 活动 |
| `session_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 场次 |
| `ticket_type_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 票种 |
| `status` | `VARCHAR(20)` | N | 订单九态 |
| `status_reason` | `VARCHAR(200)` | Y | 取消/失效原因 |
| `attendee_name_snapshot` | `VARCHAR(60)` | N | 报名时姓名 |
| `event_name_snapshot` | `VARCHAR(60)` | N | 活动名称快照 |
| `session_name_snapshot` | `VARCHAR(60)` | N | 场次名称快照 |
| `category_name_snapshot` | `VARCHAR(60)` | N | 类别快照 |
| `ticket_type_name_snapshot` | `VARCHAR(60)` | N | 票种名称快照 |
| `amount_jiao` | `BIGINT` | N | 下单单价快照，服务端确定 |
| `created_at_ms` | `BIGINT` | N | 报名持久受理时间 |
| `waitlisted_at_ms` | `BIGINT` | Y | 曾候补时刻；累计申请依据 |
| `offered_at_ms` | `BIGINT` | Y | 持久递补通知时刻 |
| `confirm_deadline_ms` | `BIGINT` | Y | 递补确认截止 |
| `pay_deadline_ms` | `BIGINT` | Y | 支付截止 |
| `consecutive_payment_failures` | `INT` | N | 不同失败尝试计数0至3 |
| `updated_at_ms` | `BIGINT` | N | 状态变更时刻 |
| `version` | `BIGINT` | N | 内部状态版本 |

约束/索引：

- `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('WAITLISTED','OFFERED','UNPAID','REGISTERED','CHECKED_IN') THEN 1 ELSE NULL END) STORED`
- `UNIQUE KEY uk_order_active (user_id,session_id,active_guard)`
- `CHECK (status IN ('WAITLISTED','OFFERED','UNPAID','REGISTERED','CHECKED_IN','COMPLETED','CANCELLED','REFUNDED','ABSENT'))`
- `CHECK (amount_jiao BETWEEN 0 AND 9999990)`
- `CHECK (consecutive_payment_failures BETWEEN 0 AND 3)`
- `KEY ix_order_owner (user_id,created_at_ms,id)`
- `KEY ix_order_occupancy (ticket_type_id,status,id)`
- `KEY ix_order_waitlist (session_id,ticket_type_id,status,waitlisted_at_ms,id)`
- `KEY ix_order_pay_deadline (status,pay_deadline_ms,id)`
- `KEY ix_order_confirm_deadline (status,confirm_deadline_ms,id)`
- `KEY ix_order_event (event_id,session_id,id)`

### registration_requests

每个报名键永久指向原订单，包括返回已有有效单的新键

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 映射UUID |
| `user_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 参与者 |
| `request_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 客户端业务请求键 |
| `fingerprint` | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin` | N | 规范请求SHA256 |
| `order_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 原订单，不跟随后续订单改变 |
| `operation_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 原受理操作UUID |
| `operation_result` | `VARCHAR(30)` | N | CREATED/EXISTING_ACTIVE_ORDER |
| `created_at_ms` | `BIGINT` | N | 受理时刻 |

约束/索引：

- `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_registration_key (user_id,request_id)`

### payment_attempts

模拟支付尝试；每单最多一笔成功

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 支付记录UUID |
| `order_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 订单 |
| `attempt_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 客户端尝试键 |
| `operation_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 原操作UUID |
| `result` | `VARCHAR(10)` | N | SUCCESS/FAILURE |
| `amount_jiao` | `BIGINT` | N | 服务端订单全额 |
| `failure_reason` | `VARCHAR(200)` | Y | 失败说明 |
| `created_at_ms` | `BIGINT` | N | 执行时刻 |

约束/索引：

- `FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_payment_attempt (order_id,attempt_id)`
- `success_guard TINYINT GENERATED ALWAYS AS (CASE WHEN result='SUCCESS' THEN 1 ELSE NULL END) STORED`
- `UNIQUE KEY uk_payment_success (order_id,success_guard)`
- `CHECK (result IN ('SUCCESS','FAILURE'))`
- `CHECK (amount_jiao BETWEEN 0 AND 9999990)`
- `KEY ix_payment_audit (created_at_ms,id)`

### refund_requests

T1合法退款依据；先受理后执行，失败仍可恢复

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 退款依据UUID |
| `order_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 原订单 |
| `original_payment_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 原成功支付 |
| `source` | `VARCHAR(24)` | N | USER/PLATFORM_TERMINATION |
| `status` | `VARCHAR(16)` | N | ACCEPTED/FAILED/SUCCEEDED/REVOKED |
| `initial_attempt_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | T1固定初次尝试键 |
| `accepted_by` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 本人/管理员；系统为NULL |
| `accepted_at_ms` | `BIGINT` | N | 原合法受理时间 |
| `reason` | `VARCHAR(200)` | N | 申请理由 |
| `registration_closes_at_ms` | `BIGINT` | N | 受理时截止快照 |
| `order_status_at_acceptance` | `VARCHAR(20)` | N | 受理时订单状态 |
| `eligibility_snapshot_json` | `JSON` | N | 资格完整快照，内部使用不直接返回 |
| `request_fingerprint` | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin` | N | 原受理请求摘要 |
| `idempotency_key` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | USER初次受理键；平台任务可空 |
| `accepted_operation_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 原受理操作UUID |
| `termination_fact_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 平台终止依据，后置外键 |
| `revoked_at_ms` | `BIGINT` | Y | 关闭时刻 |
| `closure_reason` | `VARCHAR(200)` | Y | 关闭原因 |

约束/索引：

- `FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (original_payment_id) REFERENCES payment_attempts(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (accepted_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `open_user_guard TINYINT GENERATED ALWAYS AS (CASE WHEN source='USER' AND status IN ('ACCEPTED','FAILED') THEN 1 ELSE NULL END) STORED`
- `UNIQUE KEY uk_refund_open (order_id,open_user_guard)`
- `UNIQUE KEY uk_refund_acceptance (accepted_by,idempotency_key)`
- `UNIQUE KEY uk_refund_platform (termination_fact_id)`
- `CHECK (source IN ('USER','PLATFORM_TERMINATION'))`
- `CHECK (status IN ('ACCEPTED','FAILED','SUCCEEDED','REVOKED'))`
- `KEY ix_refund_queue (status,accepted_at_ms,id)`

### refund_attempts

T2执行记录；失败不使原单/票/库存改变

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 退款执行UUID |
| `request_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 合法退款依据 |
| `order_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 订单 |
| `original_payment_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 原成功支付，须核对同订单 |
| `attempt_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 客户端或内部尝试键 |
| `operation_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 操作UUID |
| `result` | `VARCHAR(10)` | N | SUCCESS/FAILURE |
| `amount_jiao` | `BIGINT` | N | 原支付全额 |
| `failure_reason` | `VARCHAR(200)` | Y | 失败说明 |
| `created_at_ms` | `BIGINT` | N | 执行时刻 |

约束/索引：

- `FOREIGN KEY (request_id) REFERENCES refund_requests(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (original_payment_id) REFERENCES payment_attempts(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_refund_attempt (request_id,attempt_id)`
- `success_guard TINYINT GENERATED ALWAYS AS (CASE WHEN result='SUCCESS' THEN 1 ELSE NULL END) STORED`
- `UNIQUE KEY uk_refund_success (order_id,success_guard)`
- `CHECK (result IN ('SUCCESS','FAILURE'))`
- `CHECK (amount_jiao BETWEEN 0 AND 9999990)`

### tickets

全局唯一电子票；每订单只有一票

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 票UUID，非二维码 |
| `order_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 唯一订单 |
| `status` | `VARCHAR(12)` | N | VALID/CHECKED/INVALID |
| `issued_at_ms` | `BIGINT` | N | 出票时刻 |
| `invalidated_at_ms` | `BIGINT` | Y | 失效时刻 |
| `invalid_reason` | `VARCHAR(200)` | Y | 退款/自然缺席等原因 |

约束/索引：

- `FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_ticket_order (order_id)`
- `CHECK (status IN ('VALID','CHECKED','INVALID'))`

### checkins

不可变首次签到事实；保留当时检票员与场次

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 签到UUID |
| `ticket_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 唯一票 |
| `order_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 订单 |
| `checker_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 当时检票账号 |
| `session_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 当时场次 |
| `assignment_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 当时授权历史 |
| `checked_at_ms` | `BIGINT` | N | 首次签到时刻 |

约束/索引：

- `FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (checker_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (assignment_id) REFERENCES checker_assignments(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_checkin_ticket (ticket_id)`
- `UNIQUE KEY uk_checkin_order (order_id)`
- `KEY ix_checkin_session (session_id,checked_at_ms,id)`

### reports

用户举报及额度计数原始事实；驳回不返还额度

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 举报UUID |
| `reporter_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 参与者 |
| `event_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 活动 |
| `session_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 场次 |
| `reason` | `VARCHAR(200)` | N | 理由 |
| `description` | `VARCHAR(2000)` | N | 情况说明 |
| `evidence_text` | `VARCHAR(2000)` | N | 文本证据，不抓取URL |
| `status` | `VARCHAR(20)` | N | PENDING/SUBSTANTIATED/UNSUBSTANTIATED |
| `created_at_ms` | `BIGINT` | N | 受理时刻，30天窗口依据 |
| `conclusion` | `VARCHAR(200)` | Y | 结论说明 |
| `resolved_at_ms` | `BIGINT` | Y | 处理时刻 |

约束/索引：

- `FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_report_once (reporter_id,session_id)`
- `KEY ix_report_quota (reporter_id,created_at_ms,id)`
- `KEY ix_report_event (event_id,status,created_at_ms,id)`

### investigations

管理员核查，同活动同事实去重

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 核查UUID |
| `event_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 活动 |
| `investigator_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 管理员 |
| `fact_key` | `VARCHAR(120)` | N | trim后精确比较的事实键 |
| `conclusion` | `VARCHAR(20)` | N | SUBSTANTIATED/UNSUBSTANTIATED |
| `reason` | `VARCHAR(200)` | N | 结论理由 |
| `evidence_text` | `VARCHAR(2000)` | N | 核查证据 |
| `created_at_ms` | `BIGINT` | N | 核查时刻 |
| `termination_job_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 本次下架任务，后置外键 |

约束/索引：

- `FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (investigator_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_investigation_fact (event_id,fact_key)`
- `KEY ix_investigation_list (created_at_ms,id)`

### investigation_reports

合并举报关联；不把关系数组塞进活动行

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 关联UUID |
| `investigation_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 核查 |
| `report_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 被处理举报 |

约束/索引：

- `FOREIGN KEY (investigation_id) REFERENCES investigations(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_investigation_report (report_id)`

### behavior_facts

长期处罚依据；正常缺席/主动成功退款/组织者违规

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 统一事实UUID |
| `phone_identity_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 长期身份 |
| `type` | `VARCHAR(24)` | N | NATURAL_ABSENCE/VOLUNTARY_REFUND/ORGANIZER_VIOLATION |
| `order_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 缺席/退款来源订单 |
| `investigation_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 组织者违规来源 |
| `occurred_at_ms` | `BIGINT` | N | 真实事实时刻，不能由恢复任务重算 |
| `reason` | `VARCHAR(200)` | N | 事实说明 |

约束/索引：

- `FOREIGN KEY (phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (investigation_id) REFERENCES investigations(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_fact_order_type (order_id,type)`
- `UNIQUE KEY uk_fact_investigation (investigation_id)`
- `KEY ix_fact_window (phone_identity_id,type,occurred_at_ms,id)`
- `CHECK (type IN ('NATURAL_ABSENCE','VOLUNTARY_REFUND','ORGANIZER_VIOLATION'))`

### restrictions

15天限制历史；到期实时判断，无人工任意解除

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 限制UUID |
| `phone_identity_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 长期身份 |
| `kind` | `VARCHAR(12)` | N | REGISTER/PUBLISH |
| `starts_at_ms` | `BIGINT` | N | 最初生效时刻 |
| `ends_at_ms` | `BIGINT` | N | 固定15天截止 |
| `reason` | `VARCHAR(200)` | N | 原因 |
| `created_by` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 核实管理员，系统自动为空 |
| `ended_message_at_ms` | `BIGINT` | Y | 到期通知补齐标记 |
| `source` | `VARCHAR(30)` | Y | ADMIN/WEEKLY_ABSENCE/ORGANIZER_AUTO |
| `source_ref` | `VARCHAR(40)` | Y | 周任务标识等，用于幂等 |
| `revoked_at_ms` | `BIGINT` | Y | 申诉通过提前撤销时刻；到期仍按ends_at_ms |

约束/索引：

- `FOREIGN KEY (phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `CHECK (ends_at_ms = starts_at_ms + 1296000000)`
- `KEY ix_restriction_active (phone_identity_id,kind,ends_at_ms,id)`

### restriction_appeals

参与者对15天限制的申诉；同一限制可补充证据再次申诉，但同时只允许一条待审。

| 字段 | MySQL 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 申诉UUID |
| `restriction_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 被申诉限制 |
| `user_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 参与者 |
| `reason` | `VARCHAR(1000)` | N | 申诉理由 |
| `evidence_text` | `VARCHAR(2000)` | Y | 文字证据 |
| `status` | `VARCHAR(20)` | N | PENDING/APPROVED/REJECTED |
| `created_at_ms` | `BIGINT` | N | 提交时刻 |
| `reviewed_by` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 审核管理员 |
| `reviewed_at_ms` | `BIGINT` | Y | 审核时刻 |
| `decision_note` | `VARCHAR(1000)` | Y | 审核说明 |
| `version` | `BIGINT` | N | 版本 |

约束/索引：限制/用户/审核人外键；`CHECK (status IN ('PENDING','APPROVED','REJECTED'))`；`KEY ix_appeal_status (status,created_at_ms,id)`、`KEY ix_appeal_user (user_id,created_at_ms,id)`。

### restriction_facts

已消费事实；同一事实不能再次用于处罚

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 消费关系UUID |
| `restriction_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 生效限制 |
| `fact_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 消耗事实 |

约束/索引：

- `FOREIGN KEY (restriction_id) REFERENCES restrictions(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (fact_id) REFERENCES behavior_facts(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_fact_consumed (fact_id)`

### operation_records

通用持久幂等；保存原结论和资源引用，不存令牌/私有报表响应

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | operationId |
| `actor_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 当前账号 |
| `api_id` | `VARCHAR(16)` | N | 稳定接口编号 |
| `target_key` | `VARCHAR(80)` | N | 无目标用ROOT，复合目标按规范编码 |
| `idempotency_key` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 请求header键 |
| `fingerprint` | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin` | N | 规范化参数摘要；含口令请求使用SHA256后BCrypt，普通请求SHA256 |
| `phase` | `VARCHAR(16)` | N | ACCEPTED/COMPLETED，退款T1可ACCEPTED |
| `result_code` | `VARCHAR(80)` | N | 原操作结论 |
| `resource_type` | `VARCHAR(40)` | N | 可重新投影资源类型 |
| `resource_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 原资源UUID |
| `result_metadata_json` | `JSON` | N | 最小原结果元数据，禁止敏感体/缓存名单 |
| `created_at_ms` | `BIGINT` | N | 原受理时刻 |
| `expires_at_ms` | `BIGINT` | N | 通用记录至少180天 |

约束/索引：

- `FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_operation_key (actor_id,api_id,target_key,idempotency_key)`
- `KEY ix_operation_expiry (expires_at_ms,id)`
- `CHECK (expires_at_ms >= created_at_ms + 15552000000)`

### messages

持久站内消息；180天逻辑可读，首次已读不延长动作时限

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 消息UUID |
| `recipient_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 接收账号 |
| `event_key` | `VARCHAR(180)` | N | 服务端去重事件键 |
| `type` | `VARCHAR(40)` | N | 消息类型 |
| `title` | `VARCHAR(120)` | N | 标题 |
| `body` | `TEXT` | N | 安全纯文本正文 |
| `resource_type` | `VARCHAR(40)` | Y | 目标类型 |
| `resource_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 目标UUID |
| `created_at_ms` | `BIGINT` | N | 持久化时刻 |
| `expires_at_ms` | `BIGINT` | N | 创建加180天 |
| `read_at_ms` | `BIGINT` | Y | 首次已读 |
| `action_deadline_ms` | `BIGINT` | Y | 动作实际截止 |

约束/索引：

- `FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_message_once (event_key,recipient_id)`
- `KEY ix_message_inbox (recipient_id,created_at_ms,id)`
- `CHECK (expires_at_ms = created_at_ms + 15552000000)`

### audit_logs

180天物理清理审计；成功事实与业务同事务

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 日志UUID |
| `actor_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 系统为NULL |
| `actor_role` | `VARCHAR(20)` | N | 四角色或SYSTEM |
| `action` | `VARCHAR(80)` | N | 操作名 |
| `object_type` | `VARCHAR(40)` | N | 对象类型 |
| `object_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 单对象UUID |
| `result` | `VARCHAR(10)` | N | SUCCESS/FAILURE |
| `failure_reason` | `VARCHAR(200)` | Y | 脱敏失败原因 |
| `job_type` | `VARCHAR(20)` | Y | 系统任务类型 |
| `trace_id` | `VARCHAR(64)` | N | 服务端追踪号 |
| `created_at_ms` | `BIGINT` | N | 发生时间 |

约束/索引：

- `FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `KEY ix_audit_time (created_at_ms,id)`
- `KEY ix_audit_object (object_type,object_id,created_at_ms,id)`

### jobs

JOB-01至07持久任务；只查看，无HTTP任意执行入口

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 任务UUID |
| `type` | `VARCHAR(20)` | N | JOB-01至JOB-07 |
| `dedup_key` | `VARCHAR(180)` | N | 稳定触发事实键 |
| `event_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 关联活动 |
| `status` | `VARCHAR(24)` | N | PENDING/RUNNING/SUCCEEDED/FAILED_RETRYABLE |
| `due_at_ms` | `BIGINT` | N | 原应处理时刻 |
| `deadline_at_ms` | `BIGINT` | N | 原1/5分钟处理截止，不随重试延长 |
| `completed_at_ms` | `BIGINT` | Y | 完成时刻 |
| `total` | `INT` | N | 总项目数 |
| `processed` | `INT` | N | 已处理项目数 |
| `retry_count` | `INT` | N | 重试次数 |
| `next_attempt_at_ms` | `BIGINT` | N | 下一尝试时间 |
| `lease_owner` | `VARCHAR(100)` | Y | 执行器租约标识 |
| `lease_until_ms` | `BIGINT` | Y | 宕机后可恢复 |
| `last_error` | `VARCHAR(200)` | Y | 脱敏错误 |
| `created_at_ms` | `BIGINT` | N | 最初创建时刻 |

约束/索引：

- `FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_job_trigger (type,dedup_key)`
- `KEY ix_job_poll (status,next_attempt_at_ms,lease_until_ms,id)`
- `CHECK (total >= 0 AND processed BETWEEN 0 AND total AND retry_count >= 0)`

### job_items

逐单/逐接收人待办；无订单的组织者/检票员也必须收通知

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 项目UUID |
| `job_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 任务 |
| `item_key` | `VARCHAR(180)` | N | 如ORDER:UUID/NOTICE:账号UUID |
| `kind` | `VARCHAR(40)` | N | ORDER_SETTLEMENT/RECIPIENT_NOTICE/EXPORT等 |
| `order_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 关联订单 |
| `recipient_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 通知接收人 |
| `status` | `VARCHAR(24)` | N | 项目状态 |
| `payload_json` | `JSON` | N | 最初处理事实最小快照，禁止密码 |
| `retry_count` | `INT` | N | 单项重试次数 |
| `next_attempt_at_ms` | `BIGINT` | N | 重试时刻 |
| `completed_at_ms` | `BIGINT` | Y | 完成时间 |
| `last_error` | `VARCHAR(200)` | Y | 脱敏错误 |

约束/索引：

- `FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_job_item (job_id,item_key)`
- `KEY ix_job_item_poll (job_id,status,next_attempt_at_ms,id)`

### termination_facts

终止最初生效时逐订单结算依据；不按恢复时刻重新判定退款

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 事实UUID |
| `event_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 活动 |
| `job_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 终止结算任务 |
| `order_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 订单 |
| `effective_at_ms` | `BIGINT` | N | 原终止生效时间 |
| `status_at_effective` | `VARCHAR(20)` | N | 原订单状态 |
| `session_ends_at_ms` | `BIGINT` | N | 原场次结束时点 |
| `settlement_action` | `VARCHAR(30)` | N | CANCEL/REFUND/COMPLETE/NATURAL_SETTLE/KEEP |
| `reason` | `VARCHAR(200)` | N | 平台终止依据 |

约束/索引：

- `FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_termination_order (event_id,order_id)`

### exports

异步CSV；元数据受活动终结加七天动态边界保护

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 导出UUID |
| `event_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 活动 |
| `created_by` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 创建账号 |
| `job_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | JOB-07 |
| `status` | `VARCHAR(24)` | N | 导出状态 |
| `session_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 筛选场次 |
| `ticket_type_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 筛选票种 |
| `order_status_filter` | `VARCHAR(20)` | Y | 筛选订单状态 |
| `checkin_status_filter` | `VARCHAR(20)` | Y | NOT_CHECKED/CHECKED |
| `row_count` | `BIGINT` | Y | 完成后行数，0也有表头 |
| `file_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | 完整生成后原子发布 |
| `created_at_ms` | `BIGINT` | N | 创建时刻 |
| `completed_at_ms` | `BIGINT` | Y | 生成完成 |
| `failure_reason` | `VARCHAR(200)` | Y | 脱敏原因 |

约束/索引：

- `FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (file_id) REFERENCES stored_files(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `UNIQUE KEY uk_export_job (job_id)`

### statistics_snapshots

结算完成正式快照；比率从整数分子分母推导，不保存平均百分数

| 字段 | MySQL 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 快照UUID |
| `event_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | N | 活动 |
| `session_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | Y | NULL为活动汇总 |
| `reason` | `VARCHAR(12)` | N | NATURAL/CANCEL/TAKEDOWN |
| `generated_at_ms` | `BIGINT` | N | 实际结算完成时刻 |
| `issued_count` | `BIGINT` | N | 曾出票 |
| `refund_count` | `BIGINT` | N | 成功退款 |
| `non_refunded_issued_count` | `BIGINT` | N | 未退款出票 |
| `checked_in_count` | `BIGINT` | N | 签到 |
| `absent_count` | `BIGINT` | N | 正常缺席 |
| `capacity_at_end` | `BIGINT` | N | 原结束容量 |
| `current_waitlist_count` | `BIGINT` | N | 结算完成当前候补 |
| `cumulative_waitlist_applications` | `BIGINT` | N | 曾候补累计 |

约束/索引：

- `FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
- `scope_key VARCHAR(44) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN session_id IS NULL THEN 'EVENT' ELSE CONCAT('SESSION:',session_id) END) STORED`
- `UNIQUE KEY uk_statistics_scope (event_id,scope_key)`
- `CHECK (issued_count >= 0 AND refund_count >= 0 AND non_refunded_issued_count >= 0 AND checked_in_count >= 0 AND absent_count >= 0 AND capacity_at_end >= 0 AND current_waitlist_count >= 0 AND cumulative_waitlist_applications >= 0)`

## 应用层仍必须保证的跨行约束

同活动1～20场/每场1～10票种、完整时序、票种容量之和、容量不小于占位、跨场时间冲突、末位管理员/检票员保护、角色与授权匹配、每单成功金额与原支付一致、活动取消与签到竞争、审核每日额度和处罚窗口，均需在 Service 事务内按 backend-plan.md 锁顺序保证。MySQL CHECK 不能跨表查询，不能将外键存在当成这些规则已实现。

索引先服务当前查询和任务扫描；包含模糊检索的名称/地点不能依靠普通 B-tree 达到性能目标。后续以真实规模 EXPLAIN 和压测调整，不在此宣称达标。

## 创建命令（全文，暂不执行）

```sql
-- REVIEW ONLY: do not execute before schema approval and explicit connection stage.

-- MySQL 8.4 / InnoDB. No DROP, seed data, passwords, or automatic startup migration.

CREATE DATABASE campus_ticketing_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

USE campus_ticketing_dev;


-- 长期手机号身份；注销不删除，跨账号继承黑名单和限制
CREATE TABLE phone_identities (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  phone VARCHAR(11) NOT NULL,
  cancellation_count INT NOT NULL,
  last_cancelled_at_ms BIGINT NULL,
  blacklisted_at_ms BIGINT NULL,
  created_at_ms BIGINT NOT NULL,
  version BIGINT NOT NULL,
  UNIQUE KEY uk_phone (phone),
  CHECK (cancellation_count BETWEEN 0 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 账号；逻辑删除释放登录标识，停用不释放
CREATE TABLE users (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  username VARCHAR(60) NOT NULL,
  username_normalized VARCHAR(60) NOT NULL,
  email VARCHAR(254) NULL,
  email_normalized VARCHAR(254) NULL,
  phone_identity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  login_phone_identity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL,
  display_name VARCHAR(60) NULL,
  enabled BOOLEAN NOT NULL,
  must_change_password BOOLEAN NOT NULL,
  deleted_at_ms BIGINT NULL,
  unavailable_reason VARCHAR(200) NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  version BIGINT NOT NULL,
  FOREIGN KEY (phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_user_login_phone FOREIGN KEY (login_phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  active_username VARCHAR(60) GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN username_normalized ELSE NULL END) STORED,
  active_email VARCHAR(254) GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN email_normalized ELSE NULL END) STORED,
  active_phone_identity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN COALESCE(login_phone_identity_id,phone_identity_id) ELSE NULL END) STORED,
  UNIQUE KEY uk_user_name (active_username),
  UNIQUE KEY uk_user_email (active_email),
  UNIQUE KEY uk_user_phone_role (active_phone_identity_id,role),
  CHECK (role IN ('PARTICIPANT','ORGANIZER','CHECKER','ADMIN')),
  CONSTRAINT ck_user_phone_required CHECK (role NOT IN ('PARTICIPANT','ORGANIZER') OR phone_identity_id IS NOT NULL),
  KEY ix_users_admin (role,created_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 固定七天、不滑动续期；历史撤销记录保留
CREATE TABLE login_sessions (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at_ms BIGINT NOT NULL,
  expires_at_ms BIGINT NOT NULL,
  revoked_at_ms BIGINT NULL,
  revocation_reason VARCHAR(60) NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_session_hash (token_hash),
  active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN revoked_at_ms IS NULL THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uk_session_active (user_id,active_guard),
  KEY ix_session_expiry (expires_at_ms),
  CHECK (expires_at_ms = created_at_ms + 604800000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 人工找回；受理时固定目标账号，防同名重注册接管
CREATE TABLE recoveries (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  account VARCHAR(60) NOT NULL,
  account_normalized VARCHAR(60) NOT NULL,
  phone VARCHAR(11) NOT NULL,
  current_phone VARCHAR(11) NULL,
  role VARCHAR(20) NULL,
  `statement` VARCHAR(2000) NULL,
  past_behavior VARCHAR(2000) NULL,
  contact_verified_at_ms BIGINT NULL,
  target_account_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  status VARCHAR(20) NOT NULL,
  verification_note VARCHAR(2000) NULL,
  verified_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  verified_at_ms BIGINT NULL,
  created_at_ms BIGINT NOT NULL,
  resolved_at_ms BIGINT NULL,
  FOREIGN KEY (target_account_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  open_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','VERIFIED') THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uk_recovery_pending (account_normalized,phone,open_guard),
  KEY ix_recovery_queue (status,created_at_ms,id),
  CHECK (status IN ('PENDING','VERIFIED','RESOLVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 类别；停用不释放名称
CREATE TABLE categories (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  name VARCHAR(60) NOT NULL,
  name_normalized VARCHAR(60) NOT NULL,
  enabled BOOLEAN NOT NULL,
  version BIGINT NOT NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  UNIQUE KEY uk_category_name (name_normalized)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 受控海报/CSV元数据；文件不直接静态开放
CREATE TABLE stored_files (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  kind VARCHAR(20) NOT NULL,
  storage_key VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  byte_size BIGINT NOT NULL,
  sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at_ms BIGINT NOT NULL,
  deleted_at_ms BIGINT NULL,
  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_file_key (storage_key),
  CHECK (byte_size >= 0),
  CHECK (kind IN ('POSTER','EXPORT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 活动配置；不在管理DTO泄漏过期订单计数
CREATE TABLE events (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  organizer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(24) NOT NULL,
  name VARCHAR(60) NULL,
  category_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  category_name_snapshot VARCHAR(60) NULL,
  proposed_category_name VARCHAR(60) NULL,
  description VARCHAR(500) NULL,
  poster_file_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  submitted_at_ms BIGINT NULL,
  published_at_ms BIGINT NULL,
  terminated_at_ms BIGINT NULL,
  termination_reason VARCHAR(200) NULL,
  last_review_reason VARCHAR(200) NULL,
  settlement_status VARCHAR(20) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  version BIGINT NOT NULL,
  FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (poster_file_id) REFERENCES stored_files(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CHECK (status IN ('DRAFT','PENDING_REVIEW','PUBLISHED','CANCELLED','TAKEN_DOWN','ENDED')),
  KEY ix_event_owner (organizer_id,updated_at_ms,id),
  KEY ix_event_review (status,submitted_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 场次；草稿删除使用逻辑删除保留检票授权审计
CREATE TABLE event_sessions (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  name VARCHAR(60) NULL,
  starts_at_ms BIGINT NULL,
  ends_at_ms BIGINT NULL,
  location VARCHAR(60) NULL,
  capacity INT NULL,
  sales_opens_at_ms BIGINT NULL,
  registration_closes_at_ms BIGINT NULL,
  settlement_status VARCHAR(20) NOT NULL,
  deleted_at_ms BIGINT NULL,
  version BIGINT NOT NULL,
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uk_session_name (event_id,name,active_guard),
  KEY ix_session_time (starts_at_ms,id),
  KEY ix_session_end (ends_at_ms,id),
  CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 9999),
  CHECK (starts_at_ms IS NULL OR ends_at_ms IS NULL OR starts_at_ms < ends_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 票种配置；余票按权威订单派生，无Redis库存
CREATE TABLE ticket_types (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  name VARCHAR(60) NULL,
  capacity INT NULL,
  price_jiao BIGINT NULL,
  sale_stopped BOOLEAN NOT NULL,
  deleted_at_ms BIGINT NULL,
  version BIGINT NOT NULL,
  FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uk_type_name (session_id,name,active_guard),
  CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 9999),
  CHECK (price_jiao IS NULL OR price_jiao BETWEEN 0 AND 9999990)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 当前授权及不可变授权历史；一个账号至多一个当前场次
CREATE TABLE checker_assignments (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  checker_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  assigned_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  assigned_at_ms BIGINT NOT NULL,
  revoked_at_ms BIGINT NULL,
  revocation_reason VARCHAR(200) NULL,
  FOREIGN KEY (checker_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (assigned_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN revoked_at_ms IS NULL THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uk_checker_current (checker_id,active_guard),
  KEY ix_assignment_session (session_id,revoked_at_ms,checker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 每次审核结论及退回理由
CREATE TABLE event_reviews (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reviewer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  submitted_version BIGINT NOT NULL,
  outcome VARCHAR(24) NOT NULL,
  reason_code VARCHAR(80) NULL,
  reason VARCHAR(200) NULL,
  created_at_ms BIGINT NOT NULL,
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY ix_review_event (event_id,created_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 全站北京时间每日100次成功发布额度；非组织者单独100次
CREATE TABLE publication_quotas (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  day DATE NOT NULL,
  published_count INT NOT NULL,
  version BIGINT NOT NULL,
  UNIQUE KEY uk_publish_day (day),
  CHECK (published_count BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 单人单票订单；排队/占位/历史快照
CREATE TABLE orders (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  phone_identity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  ticket_type_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(20) NOT NULL,
  status_reason VARCHAR(200) NULL,
  attendee_name_snapshot VARCHAR(60) NOT NULL,
  event_name_snapshot VARCHAR(60) NOT NULL,
  session_name_snapshot VARCHAR(60) NOT NULL,
  category_name_snapshot VARCHAR(60) NOT NULL,
  ticket_type_name_snapshot VARCHAR(60) NOT NULL,
  amount_jiao BIGINT NOT NULL,
  created_at_ms BIGINT NOT NULL,
  waitlisted_at_ms BIGINT NULL,
  offered_at_ms BIGINT NULL,
  confirm_deadline_ms BIGINT NULL,
  pay_deadline_ms BIGINT NULL,
  consecutive_payment_failures INT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  version BIGINT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('WAITLISTED','OFFERED','UNPAID','REGISTERED','CHECKED_IN') THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uk_order_active (user_id,session_id,active_guard),
  CHECK (status IN ('WAITLISTED','OFFERED','UNPAID','REGISTERED','CHECKED_IN','COMPLETED','CANCELLED','REFUNDED','ABSENT')),
  CHECK (amount_jiao BETWEEN 0 AND 9999990),
  CHECK (consecutive_payment_failures BETWEEN 0 AND 3),
  KEY ix_order_owner (user_id,created_at_ms,id),
  KEY ix_order_occupancy (ticket_type_id,status,id),
  KEY ix_order_waitlist (session_id,ticket_type_id,status,waitlisted_at_ms,id),
  KEY ix_order_pay_deadline (status,pay_deadline_ms,id),
  KEY ix_order_confirm_deadline (status,confirm_deadline_ms,id),
  KEY ix_order_event (event_id,session_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 每个报名键永久指向原订单，包括返回已有有效单的新键
CREATE TABLE registration_requests (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_result VARCHAR(30) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_registration_key (user_id,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 模拟支付尝试；每单最多一笔成功
CREATE TABLE payment_attempts (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  attempt_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  result VARCHAR(10) NOT NULL,
  amount_jiao BIGINT NOT NULL,
  failure_reason VARCHAR(200) NULL,
  created_at_ms BIGINT NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_payment_attempt (order_id,attempt_id),
  success_guard TINYINT GENERATED ALWAYS AS (CASE WHEN result='SUCCESS' THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uk_payment_success (order_id,success_guard),
  CHECK (result IN ('SUCCESS','FAILURE')),
  CHECK (amount_jiao BETWEEN 0 AND 9999990),
  KEY ix_payment_audit (created_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- T1合法退款依据；先受理后执行，失败仍可恢复
CREATE TABLE refund_requests (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  original_payment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source VARCHAR(24) NOT NULL,
  status VARCHAR(16) NOT NULL,
  initial_attempt_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  accepted_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  accepted_at_ms BIGINT NOT NULL,
  reason VARCHAR(200) NOT NULL,
  registration_closes_at_ms BIGINT NOT NULL,
  order_status_at_acceptance VARCHAR(20) NOT NULL,
  eligibility_snapshot_json JSON NOT NULL,
  request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  idempotency_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  accepted_operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  termination_fact_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  revoked_at_ms BIGINT NULL,
  closure_reason VARCHAR(200) NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (original_payment_id) REFERENCES payment_attempts(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (accepted_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  open_user_guard TINYINT GENERATED ALWAYS AS (CASE WHEN source='USER' AND status IN ('ACCEPTED','FAILED') THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uk_refund_open (order_id,open_user_guard),
  UNIQUE KEY uk_refund_acceptance (accepted_by,idempotency_key),
  UNIQUE KEY uk_refund_platform (termination_fact_id),
  CHECK (source IN ('USER','PLATFORM_TERMINATION')),
  CHECK (status IN ('ACCEPTED','FAILED','SUCCEEDED','REVOKED')),
  KEY ix_refund_queue (status,accepted_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- T2执行记录；失败不使原单/票/库存改变
CREATE TABLE refund_attempts (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  original_payment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  attempt_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  result VARCHAR(10) NOT NULL,
  amount_jiao BIGINT NOT NULL,
  failure_reason VARCHAR(200) NULL,
  created_at_ms BIGINT NOT NULL,
  FOREIGN KEY (request_id) REFERENCES refund_requests(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (original_payment_id) REFERENCES payment_attempts(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_refund_attempt (request_id,attempt_id),
  success_guard TINYINT GENERATED ALWAYS AS (CASE WHEN result='SUCCESS' THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uk_refund_success (order_id,success_guard),
  CHECK (result IN ('SUCCESS','FAILURE')),
  CHECK (amount_jiao BETWEEN 0 AND 9999990)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 全局唯一电子票；每订单只有一票
CREATE TABLE tickets (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(12) NOT NULL,
  issued_at_ms BIGINT NOT NULL,
  invalidated_at_ms BIGINT NULL,
  invalid_reason VARCHAR(200) NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_ticket_order (order_id),
  CHECK (status IN ('VALID','CHECKED','INVALID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 不可变首次签到事实；保留当时检票员与场次
CREATE TABLE checkins (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  ticket_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  checker_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  assignment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  checked_at_ms BIGINT NOT NULL,
  FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (checker_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (assignment_id) REFERENCES checker_assignments(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_checkin_ticket (ticket_id),
  UNIQUE KEY uk_checkin_order (order_id),
  KEY ix_checkin_session (session_id,checked_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 用户举报及额度计数原始事实；驳回不返还额度
CREATE TABLE reports (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  reporter_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reason VARCHAR(200) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  evidence_text VARCHAR(2000) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  conclusion VARCHAR(200) NULL,
  resolved_at_ms BIGINT NULL,
  FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_report_once (reporter_id,session_id),
  KEY ix_report_quota (reporter_id,created_at_ms,id),
  KEY ix_report_event (event_id,status,created_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 管理员核查，同活动同事实去重
CREATE TABLE investigations (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  investigator_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  fact_key VARCHAR(120) NOT NULL,
  conclusion VARCHAR(20) NOT NULL,
  reason VARCHAR(200) NOT NULL,
  evidence_text VARCHAR(2000) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  termination_job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (investigator_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_investigation_fact (event_id,fact_key),
  KEY ix_investigation_list (created_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 合并举报关联；不把关系数组塞进活动行
CREATE TABLE investigation_reports (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  investigation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  report_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  FOREIGN KEY (investigation_id) REFERENCES investigations(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_investigation_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 长期处罚依据；正常缺席/主动成功退款/组织者违规
CREATE TABLE behavior_facts (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  phone_identity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  type VARCHAR(24) NOT NULL,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  investigation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  occurred_at_ms BIGINT NOT NULL,
  reason VARCHAR(200) NOT NULL,
  FOREIGN KEY (phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (investigation_id) REFERENCES investigations(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_fact_order_type (order_id,type),
  UNIQUE KEY uk_fact_investigation (investigation_id),
  KEY ix_fact_window (phone_identity_id,type,occurred_at_ms,id),
  CHECK (type IN ('NATURAL_ABSENCE','VOLUNTARY_REFUND','ORGANIZER_VIOLATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 15天限制历史；到期实时判断，无人工任意解除
CREATE TABLE restrictions (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  phone_identity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  kind VARCHAR(12) NOT NULL,
  starts_at_ms BIGINT NOT NULL,
  ends_at_ms BIGINT NOT NULL,
  reason VARCHAR(200) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ended_message_at_ms BIGINT NULL,
  FOREIGN KEY (phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CHECK (ends_at_ms = starts_at_ms + 1296000000),
  KEY ix_restriction_active (phone_identity_id,kind,ends_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 已消费事实；同一事实不能再次用于处罚
CREATE TABLE restriction_facts (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  restriction_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  fact_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  FOREIGN KEY (restriction_id) REFERENCES restrictions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (fact_id) REFERENCES behavior_facts(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_fact_consumed (fact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 通用持久幂等；保存原结论和资源引用，不存令牌/私有报表响应
CREATE TABLE operation_records (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  actor_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  api_id VARCHAR(16) NOT NULL,
  target_key VARCHAR(80) NOT NULL,
  idempotency_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  phase VARCHAR(16) NOT NULL,
  result_code VARCHAR(80) NOT NULL,
  resource_type VARCHAR(40) NOT NULL,
  resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  result_metadata_json JSON NOT NULL,
  created_at_ms BIGINT NOT NULL,
  expires_at_ms BIGINT NOT NULL,
  FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_operation_key (actor_id,api_id,target_key,idempotency_key),
  KEY ix_operation_expiry (expires_at_ms,id),
  CHECK (expires_at_ms >= created_at_ms + 15552000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 持久站内消息；180天逻辑可读，首次已读不延长动作时限
CREATE TABLE messages (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  recipient_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  event_key VARCHAR(180) NOT NULL,
  type VARCHAR(40) NOT NULL,
  title VARCHAR(120) NOT NULL,
  body TEXT NOT NULL,
  resource_type VARCHAR(40) NULL,
  resource_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  created_at_ms BIGINT NOT NULL,
  expires_at_ms BIGINT NOT NULL,
  read_at_ms BIGINT NULL,
  action_deadline_ms BIGINT NULL,
  FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_message_once (event_key,recipient_id),
  KEY ix_message_inbox (recipient_id,created_at_ms,id),
  CHECK (expires_at_ms = created_at_ms + 15552000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 180天物理清理审计；成功事实与业务同事务
CREATE TABLE audit_logs (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  actor_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  actor_role VARCHAR(20) NOT NULL,
  action VARCHAR(80) NOT NULL,
  object_type VARCHAR(40) NOT NULL,
  object_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  result VARCHAR(10) NOT NULL,
  failure_reason VARCHAR(200) NULL,
  job_type VARCHAR(20) NULL,
  trace_id VARCHAR(64) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY ix_audit_time (created_at_ms,id),
  KEY ix_audit_object (object_type,object_id,created_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- JOB-01至07持久任务；只查看，无HTTP任意执行入口
CREATE TABLE jobs (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  type VARCHAR(20) NOT NULL,
  dedup_key VARCHAR(180) NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  status VARCHAR(24) NOT NULL,
  due_at_ms BIGINT NOT NULL,
  deadline_at_ms BIGINT NOT NULL,
  completed_at_ms BIGINT NULL,
  total INT NOT NULL,
  processed INT NOT NULL,
  retry_count INT NOT NULL,
  next_attempt_at_ms BIGINT NOT NULL,
  lease_owner VARCHAR(100) NULL,
  lease_until_ms BIGINT NULL,
  last_error VARCHAR(200) NULL,
  created_at_ms BIGINT NOT NULL,
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_job_trigger (type,dedup_key),
  KEY ix_job_poll (status,next_attempt_at_ms,lease_until_ms,id),
  CHECK (total >= 0 AND processed BETWEEN 0 AND total AND retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 逐单/逐接收人待办；无订单的组织者/检票员也必须收通知
CREATE TABLE job_items (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  item_key VARCHAR(180) NOT NULL,
  kind VARCHAR(40) NOT NULL,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  recipient_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  status VARCHAR(24) NOT NULL,
  payload_json JSON NOT NULL,
  retry_count INT NOT NULL,
  next_attempt_at_ms BIGINT NOT NULL,
  completed_at_ms BIGINT NULL,
  last_error VARCHAR(200) NULL,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_job_item (job_id,item_key),
  KEY ix_job_item_poll (job_id,status,next_attempt_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 终止最初生效时逐订单结算依据；不按恢复时刻重新判定退款
CREATE TABLE termination_facts (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  effective_at_ms BIGINT NOT NULL,
  status_at_effective VARCHAR(20) NOT NULL,
  session_ends_at_ms BIGINT NOT NULL,
  settlement_action VARCHAR(30) NOT NULL,
  reason VARCHAR(200) NOT NULL,
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_termination_order (event_id,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 异步CSV；元数据受活动终结加七天动态边界保护
CREATE TABLE exports (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(24) NOT NULL,
  session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ticket_type_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  order_status_filter VARCHAR(20) NULL,
  checkin_status_filter VARCHAR(20) NULL,
  row_count BIGINT NULL,
  file_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  created_at_ms BIGINT NOT NULL,
  completed_at_ms BIGINT NULL,
  failure_reason VARCHAR(200) NULL,
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (file_id) REFERENCES stored_files(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY uk_export_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 结算完成正式快照；比率从整数分子分母推导，不保存平均百分数
CREATE TABLE statistics_snapshots (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  reason VARCHAR(12) NOT NULL,
  generated_at_ms BIGINT NOT NULL,
  issued_count BIGINT NOT NULL,
  refund_count BIGINT NOT NULL,
  non_refunded_issued_count BIGINT NOT NULL,
  checked_in_count BIGINT NOT NULL,
  absent_count BIGINT NOT NULL,
  capacity_at_end BIGINT NOT NULL,
  current_waitlist_count BIGINT NOT NULL,
  cumulative_waitlist_applications BIGINT NOT NULL,
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (session_id) REFERENCES event_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  scope_key VARCHAR(44) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN session_id IS NULL THEN 'EVENT' ELSE CONCAT('SESSION:',session_id) END) STORED,
  UNIQUE KEY uk_statistics_scope (event_id,scope_key),
  CHECK (issued_count >= 0 AND refund_count >= 0 AND non_refunded_issued_count >= 0 AND checked_in_count >= 0 AND absent_count >= 0 AND capacity_at_end >= 0 AND current_waitlist_count >= 0 AND cumulative_waitlist_applications >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


ALTER TABLE refund_requests ADD FOREIGN KEY (termination_fact_id) REFERENCES termination_facts(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE investigations ADD FOREIGN KEY (termination_job_id) REFERENCES jobs(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

```
