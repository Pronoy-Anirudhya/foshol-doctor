-- TODO(content-owner: C15) rate_amount / rate_unit / rate_basis / rate_notes_bn must be human-supplied.
-- Schema only — no agent-authored dosage rates (COMMON-CON-003).

ALTER TABLE remedy ADD COLUMN IF NOT EXISTS rate_amount numeric(12, 4);
ALTER TABLE remedy ADD COLUMN IF NOT EXISTS rate_unit varchar(8);
ALTER TABLE remedy ADD COLUMN IF NOT EXISTS rate_basis varchar(16);
ALTER TABLE remedy ADD COLUMN IF NOT EXISTS rate_notes_bn text;

ALTER TABLE remedy DROP CONSTRAINT IF EXISTS ck_remedy_rate_unit;
ALTER TABLE remedy ADD CONSTRAINT ck_remedy_rate_unit
    CHECK (rate_unit IS NULL OR rate_unit IN ('ML', 'G', 'KG', 'L'));

ALTER TABLE remedy DROP CONSTRAINT IF EXISTS ck_remedy_rate_basis;
ALTER TABLE remedy ADD CONSTRAINT ck_remedy_rate_basis
    CHECK (
        rate_basis IS NULL
        OR rate_basis IN ('PER_DECIMAL', 'PER_SQ_M', 'PER_HECTARE', 'PER_ACRE', 'PER_SQ_FT', 'FIXED')
    );

ALTER TABLE remedy DROP CONSTRAINT IF EXISTS ck_remedy_rate_complete;
ALTER TABLE remedy ADD CONSTRAINT ck_remedy_rate_complete
    CHECK (
        (rate_amount IS NULL AND rate_unit IS NULL AND rate_basis IS NULL)
        OR (rate_amount IS NOT NULL AND rate_unit IS NOT NULL AND rate_basis IS NOT NULL AND rate_amount > 0)
    );
