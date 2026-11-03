-- Existing campus_ticketing_dev only. Additive changes for weekly absence bans and appeals.
ALTER TABLE restrictions
  ADD COLUMN source VARCHAR(30) NULL AFTER ended_message_at_ms,
  ADD COLUMN source_ref VARCHAR(40) NULL AFTER source,
  ADD COLUMN revoked_at_ms BIGINT NULL AFTER source_ref;

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
