-- Explicit campus_ticketing_dev upgrade. Preserve users, orders, and historical identities.
-- Verify old CHECK/index names before running; do not run this on every startup.
ALTER TABLE users
  DROP CHECK users_chk_2,
  DROP INDEX uk_user_phone_role,
  ADD COLUMN login_phone_identity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD CONSTRAINT fk_user_login_phone FOREIGN KEY (login_phone_identity_id) REFERENCES phone_identities(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  MODIFY COLUMN active_phone_identity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN COALESCE(login_phone_identity_id,phone_identity_id) ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_user_phone_role (active_phone_identity_id,role),
  ADD CONSTRAINT ck_user_phone_required CHECK (role NOT IN ('PARTICIPANT','ORGANIZER') OR phone_identity_id IS NOT NULL);

ALTER TABLE recoveries
  ADD COLUMN `current_phone` VARCHAR(11) NULL,
  ADD COLUMN `role` VARCHAR(20) NULL,
  ADD COLUMN `statement` VARCHAR(2000) NULL,
  ADD COLUMN `past_behavior` VARCHAR(2000) NULL,
  ADD COLUMN `contact_verified_at_ms` BIGINT NULL;
