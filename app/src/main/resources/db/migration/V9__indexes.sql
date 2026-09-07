CREATE INDEX ix_symptom_phrase_embedding ON symptom_phrase
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX ix_symptom_embedding ON symptom
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

CREATE INDEX ix_officer_queue_priority ON p_officer_queue (state, top_confidence ASC NULLS FIRST, submitted_at ASC);
CREATE INDEX ix_officer_queue_officer  ON p_officer_queue (officer_id) WHERE officer_id IS NOT NULL;

CREATE INDEX ix_farmer_history          ON p_farmer_case_history (farmer_id, submitted_at DESC);
CREATE INDEX ix_case_farmer             ON diagnosis_case (farmer_id, created_at DESC);
CREATE INDEX ix_case_status             ON diagnosis_case (status);
CREATE INDEX ix_case_parent             ON diagnosis_case (parent_case_id) WHERE parent_case_id IS NOT NULL;
CREATE INDEX ix_case_image_case         ON case_image (case_id);
CREATE INDEX ix_case_image_sha          ON case_image (sha256);
CREATE INDEX ix_case_symptom_case       ON case_symptom (case_id);
CREATE INDEX ix_case_candidate_case     ON case_candidate (case_id, source, rank);
CREATE INDEX ix_analysis_run_case       ON analysis_run (case_id, created_at DESC);
CREATE INDEX ix_review_task_state       ON review_task (state, sla_due_at);
CREATE INDEX ix_review_task_claimed     ON review_task (claimed_at) WHERE state = 'CLAIMED';
CREATE INDEX ix_advisory_case           ON advisory (case_id, version DESC);
CREATE INDEX ix_disease_symptom_symptom ON disease_symptom (symptom_id);
CREATE INDEX ix_symptom_phrase_symptom  ON symptom_phrase (symptom_id);
CREATE INDEX ix_remedy_disease          ON remedy (disease_id) WHERE active AND deleted_at IS NULL;
CREATE INDEX ix_notification_farmer     ON notification (farmer_id, created_at DESC);
CREATE INDEX ix_idempotency_expiry      ON idempotency_key (expires_at);
CREATE INDEX ix_otp_phone               ON otp_challenge (phone_hash, created_at DESC);
