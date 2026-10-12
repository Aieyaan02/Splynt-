-- Older inquiries are not silently emailed when this feature is deployed.
ALTER TABLE contact_inquiries ADD COLUMN notification_state varchar(16) NOT NULL DEFAULT 'SKIPPED';
ALTER TABLE contact_inquiries ADD COLUMN notification_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE contact_inquiries ADD COLUMN notification_available_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE contact_inquiries ALTER COLUMN notification_state SET DEFAULT 'PENDING';
CREATE INDEX idx_contact_notification_pending ON contact_inquiries (notification_state, notification_available_at, id);
