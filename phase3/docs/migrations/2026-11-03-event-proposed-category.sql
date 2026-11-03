-- Existing campus_ticketing_dev only. No table/data deletion.
-- Organizer may submit a proposed category name together with an event draft;
-- an admin creates/reuses that category when approving the event.
ALTER TABLE events
  ADD COLUMN proposed_category_name VARCHAR(60) NULL AFTER category_name_snapshot;
