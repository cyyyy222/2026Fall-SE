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
  source VARCHAR(30) NULL,
  source_ref VARCHAR(40) NULL,
  revoked_at_ms BIGINT NULL,
  FOREIGN KEY (phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CHECK (ends_at_ms = starts_at_ms + 1296000000),
  KEY ix_restriction_active (phone_identity_id,kind,ends_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


-- 参与者对15天限制的申诉；同一限制可补充证据再次申诉，但同时只允许一条待审。
CREATE TABLE restriction_appeals (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  restriction_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  evidence_text VARCHAR(2000) NULL,
  status VARCHAR(20) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  reviewed_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  reviewed_at_ms BIGINT NULL,
  decision_note VARCHAR(1000) NULL,
  version BIGINT NOT NULL,
  FOREIGN KEY (restriction_id) REFERENCES restrictions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  KEY ix_appeal_status (status,created_at_ms,id),
  KEY ix_appeal_user (user_id,created_at_ms,id)
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
