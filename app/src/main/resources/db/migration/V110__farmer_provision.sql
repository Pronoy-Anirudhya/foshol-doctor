ALTER TABLE farmer
    ADD COLUMN registered_by uuid NULL REFERENCES field_officer (id),
    ADD COLUMN registration_source varchar(16) NOT NULL DEFAULT 'MIGRATION';

ALTER TABLE farmer
    ADD CONSTRAINT ck_farmer_registration_source
        CHECK (registration_source IN ('MIGRATION', 'MANUAL', 'CSV'));

CREATE INDEX ix_farmer_district_created ON farmer (district_code, created_at DESC);

CREATE TABLE farmer_provision_idempotency (
    key          uuid         PRIMARY KEY,
    officer_id   uuid         NOT NULL REFERENCES field_officer (id),
    request_hash char(64)     NOT NULL,
    farmer_id    uuid         NOT NULL REFERENCES farmer (id),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    expires_at   timestamptz  NOT NULL
);

CREATE INDEX ix_farmer_provision_idempotency_expiry ON farmer_provision_idempotency (expires_at);
