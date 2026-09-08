ALTER TABLE review_task ADD COLUMN IF NOT EXISTS assignment_opened_at timestamptz;
ALTER TABLE review_task ADD COLUMN IF NOT EXISTS assignment_due_at timestamptz;
ALTER TABLE review_task ADD COLUMN IF NOT EXISTS resolution_due_at timestamptz;
ALTER TABLE review_task ADD COLUMN IF NOT EXISTS kpi_warn_emitted_at timestamptz;

UPDATE review_task
SET assignment_opened_at = created_at
WHERE assignment_opened_at IS NULL;

UPDATE review_task
SET assignment_due_at = created_at + interval '1 hour'
WHERE assignment_due_at IS NULL;

UPDATE review_task
SET resolution_due_at = claimed_at + interval '2 hours'
WHERE resolution_due_at IS NULL AND claimed_at IS NOT NULL AND state = 'CLAIMED';

ALTER TABLE review_task ALTER COLUMN assignment_opened_at SET NOT NULL;
ALTER TABLE review_task ALTER COLUMN assignment_due_at SET NOT NULL;

ALTER TABLE p_officer_queue ADD COLUMN IF NOT EXISTS assignment_due_at timestamptz;
ALTER TABLE p_officer_queue ADD COLUMN IF NOT EXISTS resolution_due_at timestamptz;

UPDATE p_officer_queue q
SET assignment_due_at = t.assignment_due_at,
    resolution_due_at = t.resolution_due_at
FROM review_task t
WHERE t.id = q.review_task_id;

CREATE TABLE IF NOT EXISTS kpi_breach (
    id                  uuid          PRIMARY KEY,
    review_task_id      uuid          NOT NULL REFERENCES review_task(id),
    case_id             uuid          NOT NULL REFERENCES diagnosis_case(id),
    district_code       varchar(16)  NOT NULL,
    kind                varchar(16)  NOT NULL,
    officer_id          uuid          REFERENCES field_officer(id),
    window_started_at   timestamptz   NOT NULL,
    due_at              timestamptz   NOT NULL,
    breached_at         timestamptz   NOT NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_kpi_breach_kind CHECK (kind IN ('ASSIGNMENT', 'RESOLUTION')),
    CONSTRAINT uq_kpi_breach_window UNIQUE (review_task_id, kind, window_started_at)
);

CREATE INDEX IF NOT EXISTS ix_kpi_breach_district ON kpi_breach (district_code, breached_at DESC);
CREATE INDEX IF NOT EXISTS ix_kpi_breach_officer ON kpi_breach (officer_id, breached_at DESC)
    WHERE officer_id IS NOT NULL;
