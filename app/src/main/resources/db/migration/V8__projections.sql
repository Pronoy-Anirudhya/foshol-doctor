CREATE TABLE p_officer_queue (
    case_id               uuid          PRIMARY KEY REFERENCES diagnosis_case(id),
    review_task_id        uuid          NOT NULL,
    farmer_name           varchar(120)  NOT NULL,
    crop_code             varchar(24)   NOT NULL,
    crop_name_bn          varchar(120)  NOT NULL,
    district_code         varchar(8)    NOT NULL,
    decision_path         varchar(16),
    top_disease_id        uuid,
    top_disease_name_bn   varchar(120),
    top_confidence        numeric(5,4),
    image_count           smallint      NOT NULL DEFAULT 0,
    has_audio             boolean       NOT NULL DEFAULT false,
    analysis_mode         varchar(8),
    state                 varchar(12)   NOT NULL,
    officer_id            uuid,
    is_resubmission       boolean       NOT NULL DEFAULT false,
    submitted_at          timestamptz   NOT NULL,
    sla_due_at            timestamptz   NOT NULL,
    updated_at            timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE p_farmer_case_history (
    case_id                 uuid          PRIMARY KEY REFERENCES diagnosis_case(id),
    farmer_id               uuid          NOT NULL,
    crop_name_bn            varchar(120)  NOT NULL,
    status                  varchar(16)   NOT NULL,
    decision_path           varchar(16),
    advisory_id             uuid,
    advisory_version        smallint,
    disease_name_bn         varchar(120),
    officer_name            varchar(120),
    rejection_message_bn    text,
    thumbnail_object_key    varchar(200),
    submitted_at            timestamptz   NOT NULL,
    published_at            timestamptz,
    updated_at              timestamptz   NOT NULL DEFAULT now()
);
