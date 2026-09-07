CREATE TABLE crop (
    id             uuid         PRIMARY KEY,
    code           varchar(24)  NOT NULL UNIQUE,
    name_bn        varchar(120) NOT NULL,
    name_en        varchar(120),
    icon_key       varchar(64)  NOT NULL,
    display_order  smallint     NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     varchar(60)  NOT NULL DEFAULT 'system',
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     varchar(60)  NOT NULL DEFAULT 'system',
    deleted_at     timestamptz
);

CREATE TABLE disease (
    id              uuid         PRIMARY KEY,
    crop_id         uuid         NOT NULL REFERENCES crop(id),
    code            varchar(48)  NOT NULL,
    name_bn         varchar(120) NOT NULL,
    name_en         varchar(120),
    description_bn  text,
    severity        varchar(12)  NOT NULL,
    is_healthy      boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(60)  NOT NULL DEFAULT 'system',
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(60)  NOT NULL DEFAULT 'system',
    deleted_at      timestamptz,
    CONSTRAINT uq_disease_crop_code UNIQUE (crop_id, code),
    CONSTRAINT ck_disease_severity CHECK (severity IN ('LOW','MODERATE','HIGH','CRITICAL','NONE'))
);

CREATE TABLE symptom (
    id          uuid         PRIMARY KEY,
    code        varchar(48)  NOT NULL UNIQUE,
    name_bn     varchar(120) NOT NULL,
    name_en     varchar(120),
    organ       varchar(16)  NOT NULL,
    embedding   vector(768),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  varchar(60)  NOT NULL DEFAULT 'system',
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  varchar(60)  NOT NULL DEFAULT 'system',
    deleted_at  timestamptz,
    CONSTRAINT ck_symptom_organ CHECK (organ IN ('LEAF','STEM','ROOT','PANICLE','FRUIT','TUBER','WHOLE'))
);

CREATE TABLE symptom_phrase (
    id             uuid        PRIMARY KEY,
    symptom_id     uuid        NOT NULL REFERENCES symptom(id),
    phrase_bn      text        NOT NULL,
    normalised_bn  text        NOT NULL,
    embedding      vector(768),
    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     varchar(60) NOT NULL DEFAULT 'system',
    updated_at     timestamptz NOT NULL DEFAULT now(),
    updated_by     varchar(60) NOT NULL DEFAULT 'system',
    deleted_at     timestamptz,
    CONSTRAINT uq_symptom_phrase UNIQUE (symptom_id, normalised_bn)
);

CREATE TABLE disease_symptom (
    disease_id  uuid          NOT NULL REFERENCES disease(id),
    symptom_id  uuid          NOT NULL REFERENCES symptom(id),
    weight      numeric(4,3)  NOT NULL,
    PRIMARY KEY (disease_id, symptom_id),
    CONSTRAINT ck_disease_symptom_weight CHECK (weight > 0 AND weight <= 1)
);

CREATE TABLE remedy (
    id             uuid         PRIMARY KEY,
    disease_id     uuid         NOT NULL REFERENCES disease(id),
    type           varchar(12)  NOT NULL,
    title_bn       varchar(200) NOT NULL,
    steps_bn       jsonb        NOT NULL,
    dosage_bn      text,
    phi_days       smallint,
    cost_tier      varchar(8)   NOT NULL,
    efficacy       varchar(8)   NOT NULL,
    source_ref     text         NOT NULL,
    display_order  smallint     NOT NULL DEFAULT 0,
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     varchar(60)  NOT NULL DEFAULT 'system',
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     varchar(60)  NOT NULL DEFAULT 'system',
    deleted_at     timestamptz,
    CONSTRAINT ck_remedy_type     CHECK (type IN ('CULTURAL','ORGANIC','BIOLOGICAL','CHEMICAL')),
    CONSTRAINT ck_remedy_cost     CHECK (cost_tier IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_remedy_efficacy CHECK (efficacy IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_remedy_steps    CHECK (jsonb_typeof(steps_bn) = 'array'),
    CONSTRAINT ck_remedy_phi      CHECK (type <> 'CHEMICAL' OR phi_days IS NOT NULL)
);

CREATE TABLE model_label_map (
    id             uuid         PRIMARY KEY,
    model_id       varchar(160) NOT NULL,
    model_version  varchar(64)  NOT NULL,
    raw_label      varchar(160) NOT NULL,
    disease_id     uuid         NOT NULL REFERENCES disease(id),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_model_label UNIQUE (model_id, model_version, raw_label)
);
