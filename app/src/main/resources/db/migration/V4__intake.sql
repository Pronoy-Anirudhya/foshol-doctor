CREATE TABLE diagnosis_case (
    id              uuid         PRIMARY KEY,
    farmer_id       uuid         NOT NULL REFERENCES farmer(id),
    crop_id         uuid         NOT NULL REFERENCES crop(id),
    parent_case_id  uuid         REFERENCES diagnosis_case(id),
    status          varchar(16)  NOT NULL,
    decision_path   varchar(16),
    note_bn         text,
    district_code   varchar(8)   NOT NULL,
    correlation_id  varchar(36)  NOT NULL,
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_case_status CHECK (status IN
        ('SUBMITTED','ANALYSING','ANALYSED','IN_REVIEW','ADVISED','REJECTED','FAILED')),
    CONSTRAINT ck_case_path CHECK (decision_path IS NULL OR decision_path IN
        ('PRIMARY','SECONDARY','UNDETERMINED'))
);

CREATE TABLE case_image (
    id                     uuid         PRIMARY KEY,
    case_id                uuid         NOT NULL REFERENCES diagnosis_case(id),
    object_key             varchar(200) NOT NULL,
    derivative_object_key  varchar(200),
    content_type           varchar(60)  NOT NULL,
    byte_size              integer      NOT NULL,
    width                  integer,
    height                 integer,
    sha256                 char(64)     NOT NULL,
    quality_score          numeric(4,3),
    blur_variance          numeric(10,3),
    exposure_score         numeric(4,3),
    rejected_reason        varchar(32),
    is_primary             boolean      NOT NULL DEFAULT false,
    position               smallint     NOT NULL,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_image_position UNIQUE (case_id, position)
);

CREATE TABLE case_audio (
    id              uuid         PRIMARY KEY,
    case_id         uuid         NOT NULL UNIQUE REFERENCES diagnosis_case(id),
    object_key      varchar(200) NOT NULL,
    duration_ms     integer      NOT NULL,
    sample_rate_hz  integer      NOT NULL,
    byte_size       integer      NOT NULL,
    transcript_bn   text,
    asr_confidence  numeric(4,3),
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_key (
    key              uuid         PRIMARY KEY,
    farmer_id        uuid         NOT NULL REFERENCES farmer(id),
    endpoint         varchar(80)  NOT NULL,
    request_hash     char(64)     NOT NULL,
    response_status  smallint     NOT NULL,
    response_body    jsonb        NOT NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    expires_at       timestamptz  NOT NULL
);
