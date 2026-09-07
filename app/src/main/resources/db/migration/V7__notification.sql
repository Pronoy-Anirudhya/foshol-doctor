CREATE TABLE notification (
    id            uuid         PRIMARY KEY,
    farmer_id     uuid         NOT NULL REFERENCES farmer(id),
    case_id       uuid         NOT NULL REFERENCES diagnosis_case(id),
    advisory_id   uuid         REFERENCES advisory(id),
    channel       varchar(16)  NOT NULL,
    type          varchar(32)  NOT NULL,
    title_bn      varchar(200) NOT NULL,
    body_bn       text         NOT NULL,
    payload       jsonb        NOT NULL DEFAULT '{}'::jsonb,
    state         varchar(12)  NOT NULL,
    attempts      smallint     NOT NULL DEFAULT 0,
    delivered_at  timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('SSE','WEB_PUSH','SMS')),
    CONSTRAINT ck_notification_state   CHECK (state IN ('PENDING','SENT','FAILED','SKIPPED')),
    CONSTRAINT ck_notification_type    CHECK (type IN
        ('ADVISORY_PUBLISHED','ADVISORY_REVISED','CASE_REJECTED','CASE_STATUS_CHANGED'))
);
