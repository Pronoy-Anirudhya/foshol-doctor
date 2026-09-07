CREATE TABLE farmer (
    id                  uuid         PRIMARY KEY,
    name                varchar(120) NOT NULL,
    phone_hash          char(64)     NOT NULL UNIQUE,
    phone_enc           bytea        NOT NULL,
    district_code       varchar(8)   NOT NULL,
    preferred_language  char(2)      NOT NULL DEFAULT 'bn',
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_farmer_language CHECK (preferred_language IN ('bn','en'))
);

CREATE TABLE field_officer (
    id                  uuid         PRIMARY KEY,
    name                varchar(120) NOT NULL,
    username            varchar(60)  NOT NULL UNIQUE,
    password_hash       varchar(72)  NOT NULL,
    phone_hash          char(64)     NOT NULL,
    phone_enc           bytea        NOT NULL,
    district_code       varchar(8)   NOT NULL,
    role                varchar(16)  NOT NULL,
    active              boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_officer_role CHECK (role IN ('OFFICER','ADMIN'))
);

CREATE TABLE otp_challenge (
    id                  uuid         PRIMARY KEY,
    phone_hash          char(64)     NOT NULL,
    code_hash           char(64)     NOT NULL,
    attempts            smallint     NOT NULL DEFAULT 0,
    expires_at          timestamptz  NOT NULL,
    consumed_at         timestamptz,
    created_at          timestamptz  NOT NULL DEFAULT now()
);
