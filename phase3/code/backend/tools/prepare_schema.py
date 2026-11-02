"""Generate review-only MySQL DDL, field dictionary and immutable Java entities.

Does not connect to a database. DDL is intentionally outside resources/db/migration.
"""
raise SystemExit("Preparation generator is frozen after business implementation; maintain production source and docs explicitly. It must not overwrite Service implementations.")

from pathlib import Path
import re

BASE = Path(__file__).resolve().parents[1]
DOC = BASE.parents[1] / 'docs'
JAVA = BASE / 'src/main/java/com/campus/ticketing'
tables = []

def table(name, entity, purpose, fields, constraints=()):
    # field spec: name|SQL type|nullable|required description
    rows = [tuple(line.split('|', 3)) for line in fields.strip().splitlines()]
    tables.append((name, entity, purpose, rows, list(constraints)))

ID = 'CHAR(36) CHARACTER SET ascii COLLATE ascii_bin'
def fk(col, target):
    return f'FOREIGN KEY ({col}) REFERENCES {target}(id) ON DELETE RESTRICT ON UPDATE RESTRICT'

table('phone_identities','PhoneIdentityEntity','长期手机号身份；注销不删除，跨账号继承黑名单和限制', '''
id|ID|N|服务端身份UUID
phone|VARCHAR(11)|N|规范化11位手机号，敏感字段
cancellation_count|INT|N|累计注销次数0至3
last_cancelled_at_ms|BIGINT|Y|上次注销时刻
blacklisted_at_ms|BIGINT|Y|第三次注销永久禁止重新注册
created_at_ms|BIGINT|N|创建UTC毫秒
version|BIGINT|N|乐观锁版本
''', ['UNIQUE KEY uk_phone (phone)', 'CHECK (cancellation_count BETWEEN 0 AND 3)'])
table('users','UserEntity','账号；逻辑删除释放登录标识，停用不释放', '''
id|ID|N|账号UUID；不因重注册复用
username|VARCHAR(60)|N|用户名展示原值
username_normalized|VARCHAR(60)|N|trim并只折叠ASCII大小写
email|VARCHAR(254)|Y|P/O必填，A/C可空
email_normalized|VARCHAR(254)|Y|邮箱规范值
phone_identity_id|ID|Y|P/O必有长期手机号身份
password_hash|VARCHAR(100)|N|BCrypt，不存原文
role|VARCHAR(20)|N|PARTICIPANT/ORGANIZER/CHECKER/ADMIN
display_name|VARCHAR(60)|Y|本人姓名，可重名
enabled|BOOLEAN|N|账号启用状态
must_change_password|BOOLEAN|N|强制改密
deleted_at_ms|BIGINT|Y|逻辑删除时刻
unavailable_reason|VARCHAR(200)|Y|活动终止等凭据失效原因
created_at_ms|BIGINT|N|创建时刻
updated_at_ms|BIGINT|N|最后变更时刻
version|BIGINT|N|管理编辑版本
''', [fk('phone_identity_id','phone_identities'),
"active_username VARCHAR(60) GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN username_normalized ELSE NULL END) STORED",
"active_email VARCHAR(254) GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN email_normalized ELSE NULL END) STORED",
f"active_phone_identity_id {ID} GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN phone_identity_id ELSE NULL END) STORED",
'UNIQUE KEY uk_user_name (active_username)','UNIQUE KEY uk_user_email (active_email)','UNIQUE KEY uk_user_phone (active_phone_identity_id)',
"CHECK (role IN ('PARTICIPANT','ORGANIZER','CHECKER','ADMIN'))", "CHECK (role NOT IN ('PARTICIPANT','ORGANIZER') OR (email IS NOT NULL AND email_normalized IS NOT NULL AND phone_identity_id IS NOT NULL))", 'KEY ix_users_admin (role,created_at_ms,id)'])
table('login_sessions','LoginSessionEntity','固定七天、不滑动续期；历史撤销记录保留', '''
id|ID|N|会话记录UUID
user_id|ID|N|当前登录账号
token_hash|CHAR(64) CHARACTER SET ascii COLLATE ascii_bin|N|高熵token的SHA256
created_at_ms|BIGINT|N|登录时刻
expires_at_ms|BIGINT|N|登录时刻加七天
revoked_at_ms|BIGINT|Y|撤销时刻
revocation_reason|VARCHAR(60)|Y|撤销原因
''',[fk('user_id','users'),'UNIQUE KEY uk_session_hash (token_hash)', 'active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN revoked_at_ms IS NULL THEN 1 ELSE NULL END) STORED','UNIQUE KEY uk_session_active (user_id,active_guard)','KEY ix_session_expiry (expires_at_ms)', 'CHECK (expires_at_ms = created_at_ms + 604800000)'])
table('recoveries','RecoveryEntity','人工找回；受理时固定目标账号，防同名重注册接管', '''
id|ID|N|找回请求UUID
account|VARCHAR(60)|N|请求填写账号
account_normalized|VARCHAR(60)|N|规范化账号
phone|VARCHAR(11)|N|受理时填写手机号
target_account_id|ID|Y|受理时匹配账号，无匹配为NULL
status|VARCHAR(20)|N|PENDING/VERIFIED/RESOLVED/REJECTED
verification_note|VARCHAR(2000)|Y|人工核验说明
verified_by|ID|Y|核验管理员
verified_at_ms|BIGINT|Y|核验时间
created_at_ms|BIGINT|N|受理时刻
resolved_at_ms|BIGINT|Y|实际完成或拒绝时刻
''',[fk('target_account_id','users'),fk('verified_by','users'),"open_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','VERIFIED') THEN 1 ELSE NULL END) STORED",'UNIQUE KEY uk_recovery_pending (account_normalized,phone,open_guard)','KEY ix_recovery_queue (status,created_at_ms,id)',"CHECK (status IN ('PENDING','VERIFIED','RESOLVED','REJECTED'))"])
table('categories','CategoryEntity','类别；停用不释放名称', '''
id|ID|N|类别UUID
name|VARCHAR(60)|N|展示名称
name_normalized|VARCHAR(60)|N|trim和ASCII大小写归一
enabled|BOOLEAN|N|可供新提交选择
version|BIGINT|N|乐观锁版本
created_at_ms|BIGINT|N|创建时刻
updated_at_ms|BIGINT|N|更新时刻
''',['UNIQUE KEY uk_category_name (name_normalized)'])
table('stored_files','StoredFileEntity','受控海报/CSV元数据；文件不直接静态开放', '''
id|ID|N|文件UUID
owner_id|ID|N|创建账号
kind|VARCHAR(20)|N|POSTER/EXPORT
storage_key|VARCHAR(255)|N|受控相对存储键，不接收客户端路径
content_type|VARCHAR(100)|N|服务端识别MIME
byte_size|BIGINT|N|实际字节数
sha256|CHAR(64) CHARACTER SET ascii COLLATE ascii_bin|N|内容摘要
created_at_ms|BIGINT|N|创建时刻
deleted_at_ms|BIGINT|Y|清理标记
''',[fk('owner_id','users'),'UNIQUE KEY uk_file_key (storage_key)','CHECK (byte_size >= 0)',"CHECK (kind IN ('POSTER','EXPORT'))"])
table('events','EventEntity','活动配置；不在管理DTO泄漏过期订单计数', '''
id|ID|N|活动UUID
organizer_id|ID|N|组织者账号
status|VARCHAR(24)|N|活动六态
name|VARCHAR(60)|Y|草稿可空
category_id|ID|Y|草稿可空
category_name_snapshot|VARCHAR(60)|Y|提交/发布时类别名称
description|VARCHAR(500)|Y|简介
poster_file_id|ID|Y|当前海报
submitted_at_ms|BIGINT|Y|本次提交时刻
published_at_ms|BIGINT|Y|发布时刻
terminated_at_ms|BIGINT|Y|整活动终结基准：自然取末场计划结束，取消下架取原生效时间，不随任务延迟
termination_reason|VARCHAR(200)|Y|取消/下架原因
last_review_reason|VARCHAR(200)|Y|最近退回原因
settlement_status|VARCHAR(20)|N|NOT_DUE/PROCESSING/COMPLETED
created_at_ms|BIGINT|N|创建时刻
updated_at_ms|BIGINT|N|更新时刻
version|BIGINT|N|管理配置版本；不随支付计数更新
''',[fk('organizer_id','users'),fk('category_id','categories'),fk('poster_file_id','stored_files'),"CHECK (status IN ('DRAFT','PENDING_REVIEW','PUBLISHED','CANCELLED','TAKEN_DOWN','ENDED'))",'KEY ix_event_owner (organizer_id,updated_at_ms,id)','KEY ix_event_review (status,submitted_at_ms,id)'])
table('event_sessions','EventSessionEntity','场次；草稿删除使用逻辑删除保留检票授权审计', '''
id|ID|N|前端稳定UUID
event_id|ID|N|活动
name|VARCHAR(60)|Y|草稿可空，trim后精确比较
starts_at_ms|BIGINT|Y|整小时开始
ends_at_ms|BIGINT|Y|整小时结束
location|VARCHAR(60)|Y|当前地点，历史票也读此值
capacity|INT|Y|场次容量
sales_opens_at_ms|BIGINT|Y|精确分钟
registration_closes_at_ms|BIGINT|Y|报名截止
settlement_status|VARCHAR(20)|N|场次结算进度
deleted_at_ms|BIGINT|Y|仅草稿聚合删除
version|BIGINT|N|内部配置版本
''',[fk('event_id','events'),"active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN 1 ELSE NULL END) STORED",'UNIQUE KEY uk_session_name (event_id,name,active_guard)','KEY ix_session_time (starts_at_ms,id)','KEY ix_session_end (ends_at_ms,id)','CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 9999)','CHECK (starts_at_ms IS NULL OR ends_at_ms IS NULL OR starts_at_ms < ends_at_ms)'])
table('ticket_types','TicketTypeEntity','票种配置；余票按权威订单派生，无Redis库存', '''
id|ID|N|前端稳定UUID
session_id|ID|N|所属场次
name|VARCHAR(60)|Y|草稿可空，trim后精确比较
capacity|INT|Y|票种容量
price_jiao|BIGINT|Y|整数角，草稿可空
sale_stopped|BOOLEAN|N|主动停售
deleted_at_ms|BIGINT|Y|草稿删除
version|BIGINT|N|内部配置版本
''',[fk('session_id','event_sessions'),"active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN 1 ELSE NULL END) STORED",'UNIQUE KEY uk_type_name (session_id,name,active_guard)','CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 9999)','CHECK (price_jiao IS NULL OR price_jiao BETWEEN 0 AND 9999990)'])
table('checker_assignments','CheckerAssignmentEntity','当前授权及不可变授权历史；一个账号至多一个当前场次', '''
id|ID|N|授权UUID
checker_id|ID|N|检票账号
session_id|ID|N|授权场次
assigned_by|ID|N|操作管理员
assigned_at_ms|BIGINT|N|授权时刻
revoked_at_ms|BIGINT|Y|改绑/终止撤销时刻
revocation_reason|VARCHAR(200)|Y|撤销原因
''',[fk('checker_id','users'),fk('session_id','event_sessions'),fk('assigned_by','users'),'active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN revoked_at_ms IS NULL THEN 1 ELSE NULL END) STORED','UNIQUE KEY uk_checker_current (checker_id,active_guard)','KEY ix_assignment_session (session_id,revoked_at_ms,checker_id)'])
table('event_reviews','EventReviewEntity','每次审核结论及退回理由', '''
id|ID|N|审核UUID
event_id|ID|N|活动
reviewer_id|ID|N|管理员
submitted_version|BIGINT|N|被审核配置版本
outcome|VARCHAR(24)|N|PUBLISHED/REJECTED/RETURNED_TO_DRAFT
reason_code|VARCHAR(80)|Y|退回机器原因
reason|VARCHAR(200)|Y|审核意见
created_at_ms|BIGINT|N|审核时刻
''',[fk('event_id','events'),fk('reviewer_id','users'),'KEY ix_review_event (event_id,created_at_ms,id)'])
table('publication_quotas','PublicationQuotaEntity','全站北京时间每日100次成功发布额度；非组织者单独100次', '''
id|ID|N|额度行UUID
day|DATE|N|北京时间自然日
published_count|INT|N|成功发布计数，不因终止返还
version|BIGINT|N|条件更新版本
''',['UNIQUE KEY uk_publish_day (day)','CHECK (published_count BETWEEN 0 AND 100)'])
table('orders','OrderEntity','单人单票订单；排队/占位/历史快照', '''
id|ID|N|订单UUID
request_id|ID|N|最初报名请求UUID；别名映射另表
user_id|ID|N|参与者账号
phone_identity_id|ID|N|事实归属长期身份
event_id|ID|N|活动
session_id|ID|N|场次
ticket_type_id|ID|N|票种
status|VARCHAR(20)|N|订单九态
status_reason|VARCHAR(200)|Y|取消/失效原因
attendee_name_snapshot|VARCHAR(60)|N|报名时姓名
event_name_snapshot|VARCHAR(60)|N|活动名称快照
session_name_snapshot|VARCHAR(60)|N|场次名称快照
category_name_snapshot|VARCHAR(60)|N|类别快照
ticket_type_name_snapshot|VARCHAR(60)|N|票种名称快照
amount_jiao|BIGINT|N|下单单价快照，服务端确定
created_at_ms|BIGINT|N|报名持久受理时间
waitlisted_at_ms|BIGINT|Y|曾候补时刻；累计申请依据
offered_at_ms|BIGINT|Y|持久递补通知时刻
confirm_deadline_ms|BIGINT|Y|递补确认截止
pay_deadline_ms|BIGINT|Y|支付截止
consecutive_payment_failures|INT|N|不同失败尝试计数0至3
updated_at_ms|BIGINT|N|状态变更时刻
version|BIGINT|N|内部状态版本
''',[fk('user_id','users'),fk('phone_identity_id','phone_identities'),fk('event_id','events'),fk('session_id','event_sessions'),fk('ticket_type_id','ticket_types'),"active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('WAITLISTED','OFFERED','UNPAID','REGISTERED','CHECKED_IN') THEN 1 ELSE NULL END) STORED",'UNIQUE KEY uk_order_active (user_id,session_id,active_guard)',"CHECK (status IN ('WAITLISTED','OFFERED','UNPAID','REGISTERED','CHECKED_IN','COMPLETED','CANCELLED','REFUNDED','ABSENT'))",'CHECK (amount_jiao BETWEEN 0 AND 9999990)','CHECK (consecutive_payment_failures BETWEEN 0 AND 3)','KEY ix_order_owner (user_id,created_at_ms,id)','KEY ix_order_occupancy (ticket_type_id,status,id)','KEY ix_order_waitlist (session_id,ticket_type_id,status,waitlisted_at_ms,id)','KEY ix_order_pay_deadline (status,pay_deadline_ms,id)','KEY ix_order_confirm_deadline (status,confirm_deadline_ms,id)','KEY ix_order_event (event_id,session_id,id)'])
table('registration_requests','RegistrationRequestEntity','每个报名键永久指向原订单，包括返回已有有效单的新键', '''
id|ID|N|映射UUID
user_id|ID|N|参与者
request_id|ID|N|客户端业务请求键
fingerprint|CHAR(64) CHARACTER SET ascii COLLATE ascii_bin|N|规范请求SHA256
order_id|ID|N|原订单，不跟随后续订单改变
operation_id|ID|N|原受理操作UUID
operation_result|VARCHAR(30)|N|CREATED/EXISTING_ACTIVE_ORDER
created_at_ms|BIGINT|N|受理时刻
''',[fk('user_id','users'),fk('order_id','orders'),'UNIQUE KEY uk_registration_key (user_id,request_id)'])
table('payment_attempts','PaymentAttemptEntity','模拟支付尝试；每单最多一笔成功', '''
id|ID|N|支付记录UUID
order_id|ID|N|订单
attempt_id|ID|N|客户端尝试键
operation_id|ID|N|原操作UUID
result|VARCHAR(10)|N|SUCCESS/FAILURE
amount_jiao|BIGINT|N|服务端订单全额
failure_reason|VARCHAR(200)|Y|失败说明
created_at_ms|BIGINT|N|执行时刻
''',[fk('order_id','orders'),'UNIQUE KEY uk_payment_attempt (order_id,attempt_id)',"success_guard TINYINT GENERATED ALWAYS AS (CASE WHEN result='SUCCESS' THEN 1 ELSE NULL END) STORED",'UNIQUE KEY uk_payment_success (order_id,success_guard)',"CHECK (result IN ('SUCCESS','FAILURE'))",'CHECK (amount_jiao BETWEEN 0 AND 9999990)','KEY ix_payment_audit (created_at_ms,id)'])
table('refund_requests','RefundRequestEntity','T1合法退款依据；先受理后执行，失败仍可恢复', '''
id|ID|N|退款依据UUID
order_id|ID|N|原订单
original_payment_id|ID|N|原成功支付
source|VARCHAR(24)|N|USER/PLATFORM_TERMINATION
status|VARCHAR(16)|N|ACCEPTED/FAILED/SUCCEEDED/REVOKED
initial_attempt_id|ID|N|T1固定初次尝试键
accepted_by|ID|Y|本人/管理员；系统为NULL
accepted_at_ms|BIGINT|N|原合法受理时间
reason|VARCHAR(200)|N|申请理由
registration_closes_at_ms|BIGINT|N|受理时截止快照
order_status_at_acceptance|VARCHAR(20)|N|受理时订单状态
eligibility_snapshot_json|JSON|N|资格完整快照，内部使用不直接返回
request_fingerprint|CHAR(64) CHARACTER SET ascii COLLATE ascii_bin|N|原受理请求摘要
idempotency_key|ID|Y|USER初次受理键；平台任务可空
accepted_operation_id|ID|N|原受理操作UUID
termination_fact_id|ID|Y|平台终止依据，后置外键
revoked_at_ms|BIGINT|Y|关闭时刻
closure_reason|VARCHAR(200)|Y|关闭原因
''',[fk('order_id','orders'),fk('original_payment_id','payment_attempts'),fk('accepted_by','users'),"open_user_guard TINYINT GENERATED ALWAYS AS (CASE WHEN source='USER' AND status IN ('ACCEPTED','FAILED') THEN 1 ELSE NULL END) STORED",'UNIQUE KEY uk_refund_open (order_id,open_user_guard)','UNIQUE KEY uk_refund_acceptance (accepted_by,idempotency_key)','UNIQUE KEY uk_refund_platform (termination_fact_id)',"CHECK (source IN ('USER','PLATFORM_TERMINATION'))","CHECK (status IN ('ACCEPTED','FAILED','SUCCEEDED','REVOKED'))",'KEY ix_refund_queue (status,accepted_at_ms,id)'])
table('refund_attempts','RefundAttemptEntity','T2执行记录；失败不使原单/票/库存改变', '''
id|ID|N|退款执行UUID
request_id|ID|N|合法退款依据
order_id|ID|N|订单
original_payment_id|ID|N|原成功支付，须核对同订单
attempt_id|ID|N|客户端或内部尝试键
operation_id|ID|N|操作UUID
result|VARCHAR(10)|N|SUCCESS/FAILURE
amount_jiao|BIGINT|N|原支付全额
failure_reason|VARCHAR(200)|Y|失败说明
created_at_ms|BIGINT|N|执行时刻
''',[fk('request_id','refund_requests'),fk('order_id','orders'),fk('original_payment_id','payment_attempts'),'UNIQUE KEY uk_refund_attempt (request_id,attempt_id)',"success_guard TINYINT GENERATED ALWAYS AS (CASE WHEN result='SUCCESS' THEN 1 ELSE NULL END) STORED",'UNIQUE KEY uk_refund_success (order_id,success_guard)',"CHECK (result IN ('SUCCESS','FAILURE'))",'CHECK (amount_jiao BETWEEN 0 AND 9999990)'])
table('tickets','TicketEntity','全局唯一电子票；每订单只有一票', '''
id|ID|N|票UUID，非二维码
order_id|ID|N|唯一订单
status|VARCHAR(12)|N|VALID/CHECKED/INVALID
issued_at_ms|BIGINT|N|出票时刻
invalidated_at_ms|BIGINT|Y|失效时刻
invalid_reason|VARCHAR(200)|Y|退款/自然缺席等原因
''',[fk('order_id','orders'),'UNIQUE KEY uk_ticket_order (order_id)',"CHECK (status IN ('VALID','CHECKED','INVALID'))"])
table('checkins','CheckInEntity','不可变首次签到事实；保留当时检票员与场次', '''
id|ID|N|签到UUID
ticket_id|ID|N|唯一票
order_id|ID|N|订单
checker_id|ID|N|当时检票账号
session_id|ID|N|当时场次
assignment_id|ID|N|当时授权历史
checked_at_ms|BIGINT|N|首次签到时刻
''',[fk('ticket_id','tickets'),fk('order_id','orders'),fk('checker_id','users'),fk('session_id','event_sessions'),fk('assignment_id','checker_assignments'),'UNIQUE KEY uk_checkin_ticket (ticket_id)','UNIQUE KEY uk_checkin_order (order_id)','KEY ix_checkin_session (session_id,checked_at_ms,id)'])
table('reports','ReportEntity','用户举报及额度计数原始事实；驳回不返还额度', '''
id|ID|N|举报UUID
reporter_id|ID|N|参与者
event_id|ID|N|活动
session_id|ID|N|场次
reason|VARCHAR(200)|N|理由
description|VARCHAR(2000)|N|情况说明
evidence_text|VARCHAR(2000)|N|文本证据，不抓取URL
status|VARCHAR(20)|N|PENDING/SUBSTANTIATED/UNSUBSTANTIATED
created_at_ms|BIGINT|N|受理时刻，30天窗口依据
conclusion|VARCHAR(200)|Y|结论说明
resolved_at_ms|BIGINT|Y|处理时刻
''',[fk('reporter_id','users'),fk('event_id','events'),fk('session_id','event_sessions'),'UNIQUE KEY uk_report_once (reporter_id,session_id)','KEY ix_report_quota (reporter_id,created_at_ms,id)','KEY ix_report_event (event_id,status,created_at_ms,id)'])
table('investigations','InvestigationEntity','管理员核查，同活动同事实去重', '''
id|ID|N|核查UUID
event_id|ID|N|活动
investigator_id|ID|N|管理员
fact_key|VARCHAR(120)|N|trim后精确比较的事实键
conclusion|VARCHAR(20)|N|SUBSTANTIATED/UNSUBSTANTIATED
reason|VARCHAR(200)|N|结论理由
evidence_text|VARCHAR(2000)|N|核查证据
created_at_ms|BIGINT|N|核查时刻
termination_job_id|ID|Y|本次下架任务，后置外键
''',[fk('event_id','events'),fk('investigator_id','users'),'UNIQUE KEY uk_investigation_fact (event_id,fact_key)','KEY ix_investigation_list (created_at_ms,id)'])
table('investigation_reports','InvestigationReportEntity','合并举报关联；不把关系数组塞进活动行', '''
id|ID|N|关联UUID
investigation_id|ID|N|核查
report_id|ID|N|被处理举报
''',[fk('investigation_id','investigations'),fk('report_id','reports'),'UNIQUE KEY uk_investigation_report (report_id)'])
table('behavior_facts','BehaviorFactEntity','长期处罚依据；正常缺席/主动成功退款/组织者违规', '''
id|ID|N|统一事实UUID
phone_identity_id|ID|N|长期身份
type|VARCHAR(24)|N|NATURAL_ABSENCE/VOLUNTARY_REFUND/ORGANIZER_VIOLATION
order_id|ID|Y|缺席/退款来源订单
investigation_id|ID|Y|组织者违规来源
occurred_at_ms|BIGINT|N|真实事实时刻，不能由恢复任务重算
reason|VARCHAR(200)|N|事实说明
''',[fk('phone_identity_id','phone_identities'),fk('order_id','orders'),fk('investigation_id','investigations'),'UNIQUE KEY uk_fact_order_type (order_id,type)','UNIQUE KEY uk_fact_investigation (investigation_id)','KEY ix_fact_window (phone_identity_id,type,occurred_at_ms,id)',"CHECK (type IN ('NATURAL_ABSENCE','VOLUNTARY_REFUND','ORGANIZER_VIOLATION'))"])
table('restrictions','RestrictionEntity','15天限制历史；到期实时判断，无人工任意解除', '''
id|ID|N|限制UUID
phone_identity_id|ID|N|长期身份
kind|VARCHAR(12)|N|REGISTER/PUBLISH
starts_at_ms|BIGINT|N|最初生效时刻
ends_at_ms|BIGINT|N|固定15天截止
reason|VARCHAR(200)|N|原因
created_by|ID|Y|核实管理员，系统自动为空
ended_message_at_ms|BIGINT|Y|到期通知补齐标记
''',[fk('phone_identity_id','phone_identities'),fk('created_by','users'),'CHECK (ends_at_ms = starts_at_ms + 1296000000)','KEY ix_restriction_active (phone_identity_id,kind,ends_at_ms,id)'])
table('restriction_facts','RestrictionFactEntity','已消费事实；同一事实不能再次用于处罚', '''
id|ID|N|消费关系UUID
restriction_id|ID|N|生效限制
fact_id|ID|N|消耗事实
''',[fk('restriction_id','restrictions'),fk('fact_id','behavior_facts'),'UNIQUE KEY uk_fact_consumed (fact_id)'])
table('operation_records','OperationRecordEntity','通用持久幂等；保存原结论和资源引用，不存令牌/私有报表响应', '''
id|ID|N|operationId
actor_id|ID|N|当前账号
api_id|VARCHAR(16)|N|稳定接口编号
target_key|VARCHAR(80)|N|无目标用ROOT，复合目标按规范编码
idempotency_key|ID|N|请求header键
fingerprint|CHAR(64) CHARACTER SET ascii COLLATE ascii_bin|N|规范化参数摘要
phase|VARCHAR(16)|N|ACCEPTED/COMPLETED，退款T1可ACCEPTED
result_code|VARCHAR(80)|N|原操作结论
resource_type|VARCHAR(40)|N|可重新投影资源类型
resource_id|ID|Y|原资源UUID
result_metadata_json|JSON|N|最小原结果元数据，禁止敏感体/缓存名单
created_at_ms|BIGINT|N|原受理时刻
expires_at_ms|BIGINT|N|通用记录至少180天
''',[fk('actor_id','users'),'UNIQUE KEY uk_operation_key (actor_id,api_id,target_key,idempotency_key)','KEY ix_operation_expiry (expires_at_ms,id)','CHECK (expires_at_ms >= created_at_ms + 15552000000)'])
table('messages','MessageEntity','持久站内消息；180天逻辑可读，首次已读不延长动作时限', '''
id|ID|N|消息UUID
recipient_id|ID|N|接收账号
event_key|VARCHAR(180)|N|服务端去重事件键
type|VARCHAR(40)|N|消息类型
title|VARCHAR(120)|N|标题
body|TEXT|N|安全纯文本正文
resource_type|VARCHAR(40)|Y|目标类型
resource_id|ID|Y|目标UUID
created_at_ms|BIGINT|N|持久化时刻
expires_at_ms|BIGINT|N|创建加180天
read_at_ms|BIGINT|Y|首次已读
action_deadline_ms|BIGINT|Y|动作实际截止
''',[fk('recipient_id','users'),'UNIQUE KEY uk_message_once (event_key,recipient_id)','KEY ix_message_inbox (recipient_id,created_at_ms,id)','CHECK (expires_at_ms = created_at_ms + 15552000000)'])
table('audit_logs','AuditLogEntity','180天物理清理审计；成功事实与业务同事务', '''
id|ID|N|日志UUID
actor_id|ID|Y|系统为NULL
actor_role|VARCHAR(20)|N|四角色或SYSTEM
action|VARCHAR(80)|N|操作名
object_type|VARCHAR(40)|N|对象类型
object_id|ID|Y|单对象UUID
result|VARCHAR(10)|N|SUCCESS/FAILURE
failure_reason|VARCHAR(200)|Y|脱敏失败原因
job_type|VARCHAR(20)|Y|系统任务类型
trace_id|VARCHAR(64)|N|服务端追踪号
created_at_ms|BIGINT|N|发生时间
''',[fk('actor_id','users'),'KEY ix_audit_time (created_at_ms,id)','KEY ix_audit_object (object_type,object_id,created_at_ms,id)'])
table('jobs','JobEntity','JOB-01至07持久任务；只查看，无HTTP任意执行入口', '''
id|ID|N|任务UUID
type|VARCHAR(20)|N|JOB-01至JOB-07
dedup_key|VARCHAR(180)|N|稳定触发事实键
event_id|ID|Y|关联活动
status|VARCHAR(24)|N|PENDING/RUNNING/SUCCEEDED/FAILED_RETRYABLE
due_at_ms|BIGINT|N|原应处理时刻
deadline_at_ms|BIGINT|N|原1/5分钟处理截止，不随重试延长
completed_at_ms|BIGINT|Y|完成时刻
total|INT|N|总项目数
processed|INT|N|已处理项目数
retry_count|INT|N|重试次数
next_attempt_at_ms|BIGINT|N|下一尝试时间
lease_owner|VARCHAR(100)|Y|执行器租约标识
lease_until_ms|BIGINT|Y|宕机后可恢复
last_error|VARCHAR(200)|Y|脱敏错误
created_at_ms|BIGINT|N|最初创建时刻
''',[fk('event_id','events'),'UNIQUE KEY uk_job_trigger (type,dedup_key)','KEY ix_job_poll (status,next_attempt_at_ms,lease_until_ms,id)','CHECK (total >= 0 AND processed BETWEEN 0 AND total AND retry_count >= 0)'])
table('job_items','JobItemEntity','逐单/逐接收人待办；无订单的组织者/检票员也必须收通知', '''
id|ID|N|项目UUID
job_id|ID|N|任务
item_key|VARCHAR(180)|N|如ORDER:UUID/NOTICE:账号UUID
kind|VARCHAR(40)|N|ORDER_SETTLEMENT/RECIPIENT_NOTICE/EXPORT等
order_id|ID|Y|关联订单
recipient_id|ID|Y|通知接收人
status|VARCHAR(24)|N|项目状态
payload_json|JSON|N|最初处理事实最小快照，禁止密码
retry_count|INT|N|单项重试次数
next_attempt_at_ms|BIGINT|N|重试时刻
completed_at_ms|BIGINT|Y|完成时间
last_error|VARCHAR(200)|Y|脱敏错误
''',[fk('job_id','jobs'),fk('order_id','orders'),fk('recipient_id','users'),'UNIQUE KEY uk_job_item (job_id,item_key)','KEY ix_job_item_poll (job_id,status,next_attempt_at_ms,id)'])
table('termination_facts','TerminationFactEntity','终止最初生效时逐订单结算依据；不按恢复时刻重新判定退款', '''
id|ID|N|事实UUID
event_id|ID|N|活动
job_id|ID|N|终止结算任务
order_id|ID|N|订单
effective_at_ms|BIGINT|N|原终止生效时间
status_at_effective|VARCHAR(20)|N|原订单状态
session_ends_at_ms|BIGINT|N|原场次结束时点
settlement_action|VARCHAR(30)|N|CANCEL/REFUND/COMPLETE/NATURAL_SETTLE/KEEP
reason|VARCHAR(200)|N|平台终止依据
''',[fk('event_id','events'),fk('job_id','jobs'),fk('order_id','orders'),'UNIQUE KEY uk_termination_order (event_id,order_id)'])
table('exports','ExportEntity','异步CSV；元数据受活动终结加七天动态边界保护', '''
id|ID|N|导出UUID
event_id|ID|N|活动
created_by|ID|N|创建账号
job_id|ID|N|JOB-07
status|VARCHAR(24)|N|导出状态
session_id|ID|Y|筛选场次
ticket_type_id|ID|Y|筛选票种
order_status_filter|VARCHAR(20)|Y|筛选订单状态
checkin_status_filter|VARCHAR(20)|Y|NOT_CHECKED/CHECKED
row_count|BIGINT|Y|完成后行数，0也有表头
file_id|ID|Y|完整生成后原子发布
created_at_ms|BIGINT|N|创建时刻
completed_at_ms|BIGINT|Y|生成完成
failure_reason|VARCHAR(200)|Y|脱敏原因
''',[fk('event_id','events'),fk('created_by','users'),fk('job_id','jobs'),fk('session_id','event_sessions'),fk('ticket_type_id','ticket_types'),fk('file_id','stored_files'),'UNIQUE KEY uk_export_job (job_id)'])
table('statistics_snapshots','StatisticsSnapshotEntity','结算完成正式快照；比率从整数分子分母推导，不保存平均百分数', '''
id|ID|N|快照UUID
event_id|ID|N|活动
session_id|ID|Y|NULL为活动汇总
reason|VARCHAR(12)|N|NATURAL/CANCEL/TAKEDOWN
generated_at_ms|BIGINT|N|实际结算完成时刻
issued_count|BIGINT|N|曾出票
refund_count|BIGINT|N|成功退款
non_refunded_issued_count|BIGINT|N|未退款出票
checked_in_count|BIGINT|N|签到
absent_count|BIGINT|N|正常缺席
capacity_at_end|BIGINT|N|原结束容量
current_waitlist_count|BIGINT|N|结算完成当前候补
cumulative_waitlist_applications|BIGINT|N|曾候补累计
''',[fk('event_id','events'),fk('session_id','event_sessions'),"scope_key VARCHAR(44) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN session_id IS NULL THEN 'EVENT' ELSE CONCAT('SESSION:',session_id) END) STORED",'UNIQUE KEY uk_statistics_scope (event_id,scope_key)','CHECK (issued_count >= 0 AND refund_count >= 0 AND non_refunded_issued_count >= 0 AND checked_in_count >= 0 AND absent_count >= 0 AND capacity_at_end >= 0 AND current_waitlist_count >= 0 AND cumulative_waitlist_applications >= 0)'])

dao_methods={
'PhoneIdentity': ['Optional<PhoneIdentityEntity> findByPhone(String normalizedPhone);', 'Optional<PhoneIdentityEntity> lockByPhone(String normalizedPhone);'],
'User': ['Optional<UserEntity> findActiveByUsername(String normalizedUsername);', 'List<UserEntity> lockEnabledAdministrators();', 'boolean updateWithVersion(UserEntity user, long expectedVersion);'],
'LoginSession': ['Optional<LoginSessionEntity> findByTokenHash(String sha256);', 'int revokeAllForUser(UUID userId, long revokedAtMs, String reason);'],
'Recovery': ['Optional<RecoveryEntity> findOpen(String normalizedAccount, String phone);', 'boolean updateWithStatus(RecoveryEntity value, String expectedStatus);'],
'Category': ['List<CategoryEntity> findEnabled();', 'boolean updateWithVersion(CategoryEntity category, long expectedVersion);'],
'Event': ['boolean updateWithVersion(EventEntity event, long expectedVersion);', 'List<EventEntity> findPendingReviews(int limit, long offset);'],
'EventSession': ['List<EventSessionEntity> findActiveByEvent(UUID eventId);', 'boolean updateWithVersion(EventSessionEntity session, long expectedVersion);'],
'TicketType': ['List<TicketTypeEntity> findActiveBySession(UUID sessionId);', 'boolean updateWithVersion(TicketTypeEntity type, long expectedVersion);'],
'CheckerAssignment': ['Optional<CheckerAssignmentEntity> findCurrentByChecker(UUID checkerId);', 'List<CheckerAssignmentEntity> findCurrentBySession(UUID sessionId);'],
'PublicationQuota': ['Optional<PublicationQuotaEntity> lockByDay(java.time.LocalDate beijingDay);', 'boolean incrementIfBelowLimit(java.time.LocalDate day);'],
'Order': ['Optional<OrderEntity> findActiveByUserAndSession(UUID userId, UUID sessionId);', 'List<OrderEntity> findWaitingInOrder(UUID ticketTypeId, int limit);', 'long countOccupyingByType(UUID ticketTypeId);', 'boolean updateWithVersion(OrderEntity order, long expectedVersion);'],
'RegistrationRequest': ['Optional<RegistrationRequestEntity> findByUserAndRequest(UUID userId, UUID requestId);'],
'PaymentAttempt': ['Optional<PaymentAttemptEntity> findByOrderAndAttempt(UUID orderId, UUID attemptId);', 'Optional<PaymentAttemptEntity> findSuccessByOrder(UUID orderId);', 'List<PaymentAttemptEntity> findByOrder(UUID orderId);'],
'RefundRequest': ['Optional<RefundRequestEntity> findOpenUserRequest(UUID orderId);', 'Optional<RefundRequestEntity> findByAcceptanceKey(UUID acceptedBy, UUID idempotencyKey);', 'Optional<RefundRequestEntity> findByTerminationFact(UUID factId);'],
'RefundAttempt': ['Optional<RefundAttemptEntity> findByRequestAndAttempt(UUID requestId, UUID attemptId);', 'Optional<RefundAttemptEntity> findSuccessByOrder(UUID orderId);', 'List<RefundAttemptEntity> findByRequest(UUID requestId);'],
'Ticket': ['Optional<TicketEntity> findByOrder(UUID orderId);'],
'CheckIn': ['Optional<CheckInEntity> findByTicket(UUID ticketId);'],
'Report': ['long countAcceptedSince(UUID reporterId, long exclusiveFromMs, long inclusiveToMs);', 'long countByEvent(UUID eventId);'],
'Investigation': ['Optional<InvestigationEntity> findByEventAndFactKey(UUID eventId, String factKey);'],
'BehaviorFact': ['List<BehaviorFactEntity> findByIdentityAndWindow(UUID identityId, long exclusiveFromMs, long inclusiveToMs);'],
'Restriction': ['List<RestrictionEntity> findEffective(UUID identityId, String kind, long nowMs);'],
'RestrictionFact': ['boolean isConsumed(UUID factId);'],
'OperationRecord': ['Optional<OperationRecordEntity> findByKey(UUID actorId, String apiId, String targetKey, UUID idempotencyKey);'],
'Message': ['Optional<MessageEntity> findByEventAndRecipient(String eventKey, UUID recipientId);', 'boolean markReadIfUnread(UUID messageId, long readAtMs);'],
'Job': ['Optional<JobEntity> findByTrigger(String type, String dedupKey);', 'List<JobEntity> findDue(long nowMs, int limit);'],
'JobItem': ['Optional<JobItemEntity> findByJobAndKey(UUID jobId, String itemKey);', 'List<JobItemEntity> findPending(UUID jobId, long nowMs, int limit);'],
'TerminationFact': ['Optional<TerminationFactEntity> findByEventAndOrder(UUID eventId, UUID orderId);'],
'StatisticsSnapshot': ['Optional<StatisticsSnapshotEntity> findByScope(UUID eventId, UUID nullableSessionId);']
}
def camel(s): return re.sub(r'_([a-z])', lambda m:m[1].upper(),s)
sql = ['-- REVIEW ONLY: do not execute before schema approval and explicit connection stage.',
       '-- MySQL 8.4 / InnoDB. No DROP, seed data, passwords, or automatic startup migration.',
       "CREATE DATABASE campus_ticketing_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;",
       'USE campus_ticketing_dev;']
md = ['# MySQL 数据库设计与建表命令（审阅稿）', '', '版本：v0.1 · 2026-11-02 · **未连接、未执行、未验证真实 MySQL DDL**。', '',
      'SQL 使用 MySQL 8.4 / InnoDB。当前工程关闭 Flyway、SQL 初始化及自动建库；本文件与 [独立 SQL](mysql-schema.sql) 仅供审阅。不要放入 resources/schema.sql 或启用迁移。后续确认连接与独立开发库后，另行授权执行；已存在的库应先核对结构，不能直接重跑建库语句。', '',
      '## 统一约束', '',
      '- ID：CHAR(36) / ascii_bin；JSON DTO 使用 UUID。时间：UTC epoch 毫秒 BIGINT，接口统一 +08:00；北京时间自然日额度使用 DATE。金额：BIGINT 整数角。',
      '- 字符串使用 utf8mb4_0900_bin 精确比较；用户名/邮箱/类别另外保存应用规范值，只折叠 ASCII 大小写。Unicode 码点长度、trim、时间粒度和跨行完整性由 Service 校验。',
      '- 所有外键 RESTRICT，不级联删历史；注销/授权撤销/草稿子对象删除用逻辑标记。历史订单、票、支付、退款、签到、手机号事实持续保留。审计180天物理清理，消息180天逻辑可读；导出/名单/正式统计的可读期限由活动实际终结+7天实时判定，缓存不绕过。',
      '- 每表显式字段，无自动时间默认值；所有写入使用注入 Clock 和服务器ID。生成列只供数据库约束，不是可写实体属性。BOOLEAN 在 MySQL 为 TINYINT(1)，JDBC 映射 Boolean。',
      '- 各状态枚举详见 api.md 与 Java enums；DDL关键唯一/状态 CHECK 是第二防线，不能替代 Service 状态机。草稿允许空值；提交、发布和已发布编辑须补全并校验。',
      '- 概念上相关的 event/session/type/order/payment 组合必须在同一事务校验归属；单列外键仅保证对象存在，不能证明其属于同一聚合。统计、导出必须再次核对权限/期限。',
      '- 占位从 OFFERED/UNPAID/REGISTERED/CHECKED_IN/COMPLETED/ABSENT 订单派生；WAITLISTED/CANCELLED/REFUNDED不占位。锁票种/场次后再查计数，不存容易漂移的第二份库存。',
      '- 单会话唯一索引按未撤销表达；新登录必须在锁账号后撤销旧行（包括已到期未撤销行）再插入。到期判定始终实时检查 expires_at_ms。',
      '- 通用幂等仅保存结论/资源引用，读取时重投影和复核期限；退款T1还在 refund_requests 持久保存受理键及完整资格，通用幂等清理不能破坏其恢复。', '',
      f'## {len(tables)} 张表与字段字典', '', '| 表 | Java 实体 | 用途 |', '| --- | --- | --- |']
for name, entity, purpose, fields, constraints in tables:
    md.append(f'| `{name}` | `{entity}` | {purpose} |')
for name, entity, purpose, fields, constraints in tables:
    columns=[]; javafields=[]
    md += ['', f'### {name}', '', purpose, '', '| 字段 | MySQL 类型 | 可空 | 含义 |', '| --- | --- | --- | --- |']
    for field,typ,nullable,desc in fields:
        typ = typ.replace('ID',ID) if typ == 'ID' else typ
        columns.append(f'  {field} {typ} '+('NULL' if nullable=='Y' else 'NOT NULL')+(" PRIMARY KEY" if field=='id' else ''))
        md.append(f'| `{field}` | `{typ}` | {nullable} | {desc} |')
        jtype = 'UUID' if typ==ID else ('Long' if typ=='BIGINT' else 'Integer' if typ=='INT' else 'Boolean' if typ=='BOOLEAN' else 'LocalDate' if typ=='DATE' else 'String')
        javafields.append(f'    {jtype} {camel(field)}')
    columns += ['  '+c for c in constraints]
    sql.append(f'\n-- {purpose}\nCREATE TABLE {name} (\n'+',\n'.join(columns)+'\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;')
    md += ['', '约束/索引：', ''] + ['- `'+c+'`' for c in constraints]
    dest=JAVA/'entity'/f'{entity}.java'; dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text('package com.campus.ticketing.entity;\n\nimport java.util.UUID;\nimport java.time.LocalDate;\n\n/** '+purpose+'。仅内部持久化使用；不得直接序列化为API响应。 */\npublic record '+entity+'(\n'+',\n'.join(javafields)+'\n) {}\n',encoding='utf8')
    dao=JAVA/'dao'/f'{entity.removesuffix("Entity")}Dao.java'; dao.parent.mkdir(parents=True,exist_ok=True)
    methods=dao_methods.get(entity.removesuffix('Entity'),[])
    dao.write_text('package com.campus.ticketing.dao;\n\nimport java.util.*;\nimport com.campus.ticketing.entity.'+entity+';\n\n/** 持久层端口，事务由Service控制；尚未实现业务SQL。 */\npublic interface '+entity.removesuffix('Entity')+'Dao extends EntityDao<'+entity+'> {\n'+''.join(' '+method+'\n' for method in methods)+'}\n',encoding='utf8')
sql += ['\nALTER TABLE refund_requests ADD FOREIGN KEY (termination_fact_id) REFERENCES termination_facts(id) ON DELETE RESTRICT ON UPDATE RESTRICT;', 'ALTER TABLE investigations ADD FOREIGN KEY (termination_job_id) REFERENCES jobs(id) ON DELETE RESTRICT ON UPDATE RESTRICT;']
ddl='\n\n'.join(sql)+'\n'
(DOC/'mysql-schema.sql').write_text(ddl,encoding='utf8')
md += ['', '## 应用层仍必须保证的跨行约束', '',
       '同活动1～20场/每场1～10票种、完整时序、票种容量之和、容量不小于占位、跨场时间冲突、末位管理员/检票员保护、角色与授权匹配、每单成功金额与原支付一致、活动取消与签到竞争、审核每日额度和处罚窗口，均需在 Service 事务内按 backend-plan.md 锁顺序保证。MySQL CHECK 不能跨表查询，不能将外键存在当成这些规则已实现。', '',
       '索引先服务当前查询和任务扫描；包含模糊检索的名称/地点不能依靠普通 B-tree 达到性能目标。后续以真实规模 EXPLAIN 和压测调整，不在此宣称达标。', '',
       '## 创建命令（全文，暂不执行）', '', '```sql', ddl, '```']
(DOC/'database-design.md').write_text('\n'.join(md)+'\n',encoding='utf8')
print(f'Prepared {len(tables)} tables/entities/DAO ports; NO database operation performed.')
