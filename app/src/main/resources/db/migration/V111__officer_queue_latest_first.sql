-- Officer queue is served newest submitted_at first (REVIEW-FR-030).
CREATE INDEX IF NOT EXISTS ix_officer_queue_latest ON p_officer_queue (submitted_at DESC);
