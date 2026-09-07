CREATE TABLE case_symptom (
    id          uuid         PRIMARY KEY,
    case_id     uuid         NOT NULL REFERENCES diagnosis_case(id),
    symptom_id  uuid         NOT NULL REFERENCES symptom(id),
    score       numeric(4,3) NOT NULL,
    source      varchar(8)   NOT NULL,
    matcher     varchar(8),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_symptom UNIQUE (case_id, symptom_id, source),
    CONSTRAINT ck_case_symptom_source  CHECK (source IN ('SPEECH','VISION','OFFICER')),
    CONSTRAINT ck_case_symptom_matcher CHECK (matcher IS NULL OR matcher IN ('VECTOR','FUZZY','MANUAL'))
);

CREATE TABLE case_candidate (
    id          uuid          PRIMARY KEY,
    case_id     uuid          NOT NULL REFERENCES diagnosis_case(id),
    disease_id  uuid          NOT NULL REFERENCES disease(id),
    confidence  numeric(5,4)  NOT NULL,
    rank        smallint      NOT NULL,
    source      varchar(8)    NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_candidate UNIQUE (case_id, disease_id, source),
    CONSTRAINT ck_case_candidate_source CHECK (source IN ('MODEL','KB','MERGED')),
    CONSTRAINT ck_case_candidate_conf   CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE TABLE analysis_run (
    id                     uuid          PRIMARY KEY,
    case_id                uuid          NOT NULL REFERENCES diagnosis_case(id),
    mode                   varchar(8)    NOT NULL,
    vision_model_id        varchar(160),
    vision_model_version   varchar(64),
    asr_model_id           varchar(160),
    embed_model_id         varchar(160),
    top1_confidence        numeric(5,4),
    top2_confidence        numeric(5,4),
    margin                 numeric(5,4),
    decision_path          varchar(16),
    latency_ms             integer       NOT NULL,
    gradcam_object_key     varchar(200),
    unmapped_labels        jsonb         NOT NULL DEFAULT '[]'::jsonb,
    raw_output             jsonb,
    error_code             varchar(32),
    created_at             timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_analysis_mode CHECK (mode IN ('REPLAY','LIVE')),
    CONSTRAINT ck_analysis_path CHECK (decision_path IS NULL OR decision_path IN
        ('PRIMARY','SECONDARY','UNDETERMINED')),
    CONSTRAINT ck_analysis_unmapped CHECK (jsonb_typeof(unmapped_labels) = 'array')
);
