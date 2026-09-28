-- 0024_user_deletion_fks: account deletion (C6).
-- notification_log.user_uid (0005) and harvest_events.recorder_uid (0007)
-- previously had no foreign key, so deleting a users row orphaned them.
-- Add the missing FKs so DELETE /v1/users/me cascades like every other
-- user-owned table. Pre-launch: migrations run once on empty DBs, so a
-- plain ADD CONSTRAINT is sufficient.
ALTER TABLE notification_log
    ADD CONSTRAINT notification_log_user_uid_fkey
    FOREIGN KEY (user_uid) REFERENCES users(uid) ON DELETE CASCADE;
ALTER TABLE harvest_events
    ADD CONSTRAINT harvest_events_recorder_uid_fkey
    FOREIGN KEY (recorder_uid) REFERENCES users(uid) ON DELETE CASCADE;
