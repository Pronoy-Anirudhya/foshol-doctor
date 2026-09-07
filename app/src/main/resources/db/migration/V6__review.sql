CREATE TABLE review_task (
    id                    uuid          PRIMARY KEY,
    case_id               uuid          NOT NULL UNIQUE REFERENCES diagnosis_case(id),
    officer_id            uuid          REFERENCES field_officer(id),
    state                 varchar(12)   NOT NULL,
    priority_confidence   numeric(5,4),
    claimed_at            timestamptz,
    sla_due_at            timestamptz   NOT NULL,
    requeue_count         smallint      NOT NULL DEFAULT 0,
    version               integer       NOT NULL DEFAULT 0,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    created_by            varchar(60)   NOT NULL DEFAULT 'system',
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    updated_by            varchar(60)   NOT NULL DEFAULT 'system',
    CONSTRAINT ck_review_state CHECK (state IN ('PENDING','CLAIMED','DONE','REJECTED')),
    CONSTRAINT ck_review_claim CHECK ((state = 'CLAIMED') = (officer_id IS NOT NULL AND claimed_at IS NOT NULL)
                                       OR state IN ('DONE','REJECTED'))
);

CREATE TABLE advisory (
    id              uuid         PRIMARY KEY,
    case_id         uuid         NOT NULL REFERENCES diagnosis_case(id),
    disease_id      uuid         REFERENCES disease(id),
    officer_id      uuid         NOT NULL REFERENCES field_officer(id),
    action          varchar(12)  NOT NULL,
    officer_note_bn text,
    version         smallint     NOT NULL DEFAULT 1,
    supersedes_id   uuid         REFERENCES advisory(id),
    published_at    timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(60)  NOT NULL,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(60)  NOT NULL,
    CONSTRAINT uq_advisory_case_version UNIQUE (case_id, version),
    CONSTRAINT ck_advisory_action CHECK (action IN ('APPROVED','EDITED','REPLACED')),
    CONSTRAINT ck_advisory_version CHECK (version >= 1),
    CONSTRAINT ck_advisory_supersedes CHECK ((version = 1) = (supersedes_id IS NULL))
);

CREATE TABLE advisory_remedy (
    advisory_id    uuid     NOT NULL REFERENCES advisory(id),
    remedy_id      uuid     NOT NULL REFERENCES remedy(id),
    display_order  smallint NOT NULL DEFAULT 0,
    PRIMARY KEY (advisory_id, remedy_id)
);

CREATE TABLE case_rejection (
    id           uuid         PRIMARY KEY,
    case_id      uuid         NOT NULL UNIQUE REFERENCES diagnosis_case(id),
    officer_id   uuid         NOT NULL REFERENCES field_officer(id),
    reason_code  varchar(32)  NOT NULL,
    message_bn   text         NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_rejection_reason CHECK (reason_code IN
        ('BLURRY_IMAGE','NOT_A_CROP','WRONG_CROP','INSUFFICIENT_DETAIL','INAUDIBLE_AUDIO','OTHER'))
);
