ALTER TABLE project
    ADD COLUMN last_notify_error VARCHAR(255) NULL;

ALTER TABLE finding
    ADD COLUMN notified_at DATETIME(6) NULL;
