-- Case field metrics for FO dose context (COMMON additive).
-- Existing rows (if any) backfilled with a placeholder 1 DECIMAL so NOT NULL can apply;
-- greenfield demos typically wipe .data and re-submit with real values.

ALTER TABLE diagnosis_case ADD COLUMN IF NOT EXISTS field_area numeric(12, 3);
ALTER TABLE diagnosis_case ADD COLUMN IF NOT EXISTS field_area_unit varchar(16);
ALTER TABLE diagnosis_case ADD COLUMN IF NOT EXISTS crop_quantity numeric(12, 3);
ALTER TABLE diagnosis_case ADD COLUMN IF NOT EXISTS crop_quantity_unit varchar(16);
ALTER TABLE diagnosis_case ADD COLUMN IF NOT EXISTS metrics_source varchar(24);

UPDATE diagnosis_case
SET field_area = 1,
    field_area_unit = 'DECIMAL',
    metrics_source = COALESCE(metrics_source, 'FORM')
WHERE field_area IS NULL;

ALTER TABLE diagnosis_case ALTER COLUMN field_area SET NOT NULL;
ALTER TABLE diagnosis_case ALTER COLUMN field_area_unit SET NOT NULL;
ALTER TABLE diagnosis_case ALTER COLUMN metrics_source SET NOT NULL;
ALTER TABLE diagnosis_case ALTER COLUMN metrics_source SET DEFAULT 'FORM';

ALTER TABLE diagnosis_case DROP CONSTRAINT IF EXISTS ck_case_field_area_unit;
ALTER TABLE diagnosis_case ADD CONSTRAINT ck_case_field_area_unit
    CHECK (field_area_unit IN ('DECIMAL', 'SQ_M', 'SQ_FT', 'HECTARE', 'ACRE'));

ALTER TABLE diagnosis_case DROP CONSTRAINT IF EXISTS ck_case_crop_quantity_unit;
ALTER TABLE diagnosis_case ADD CONSTRAINT ck_case_crop_quantity_unit
    CHECK (
        (crop_quantity IS NULL AND crop_quantity_unit IS NULL)
        OR (crop_quantity IS NOT NULL AND crop_quantity_unit IN ('KG', 'TON', 'PLANTS', 'BIGHAS_EQUIV'))
    );

ALTER TABLE diagnosis_case DROP CONSTRAINT IF EXISTS ck_case_metrics_source;
ALTER TABLE diagnosis_case ADD CONSTRAINT ck_case_metrics_source
    CHECK (metrics_source IN ('FORM', 'SPEECH', 'FORM_AND_SPEECH'));

ALTER TABLE diagnosis_case DROP CONSTRAINT IF EXISTS ck_case_field_area_positive;
ALTER TABLE diagnosis_case ADD CONSTRAINT ck_case_field_area_positive
    CHECK (field_area > 0);
