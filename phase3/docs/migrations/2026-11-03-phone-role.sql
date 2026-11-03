-- Existing campus_ticketing_dev only. No table/data deletion.
-- Replace the previous global active-phone uniqueness with per-role uniqueness.
-- Check SHOW INDEX FROM users before applying. Run once, not on every startup.
ALTER TABLE users
  DROP INDEX uk_user_phone,
  ADD UNIQUE KEY uk_user_phone_role (active_phone_identity_id, role);
