-- LOCAL ONLY helper (mirrors db/seed/V103__seed_wambugu71_label_map.sql).
-- Prefer restarting the app with profile `local` so Flyway applies V103.
-- If you already have a running DB without V103, you can apply this file with psql.
-- Requires: foshol.knowledge.expected-disease-count=20
-- Invalid stays unmapped. Stub remedies/symptoms are NOT farmer-facing advice.

BEGIN;

INSERT INTO crop (id, code, name_bn, name_en, icon_key, display_order, created_by, updated_by)
VALUES
    ('01800000-0000-7000-8000-000000000004', 'wheat', 'wheat', 'Wheat', 'crop-wheat', 4, 'local-seed', 'local-seed'),
    ('01800000-0000-7000-8000-000000000005', 'corn', 'corn', 'Corn', 'crop-corn', 5, 'local-seed', 'local-seed')
ON CONFLICT (id) DO NOTHING;

INSERT INTO disease (id, crop_id, code, name_bn, name_en, description_bn, severity, is_healthy, created_by, updated_by)
VALUES
    ('01800000-0000-7000-8000-000000000121', '01800000-0000-7000-8000-000000000004', 'brown_rust', 'Brown rust', 'Brown rust', NULL, 'LOW', false, 'local-seed', 'local-seed'),
    ('01800000-0000-7000-8000-000000000122', '01800000-0000-7000-8000-000000000004', 'yellow_rust', 'Yellow rust', 'Yellow rust', NULL, 'LOW', false, 'local-seed', 'local-seed'),
    ('01800000-0000-7000-8000-000000000123', '01800000-0000-7000-8000-000000000004', 'healthy', 'Healthy', 'Healthy', NULL, 'NONE', true, 'local-seed', 'local-seed'),
    ('01800000-0000-7000-8000-000000000124', '01800000-0000-7000-8000-000000000005', 'common_rust', 'Common rust', 'Common rust', NULL, 'LOW', false, 'local-seed', 'local-seed'),
    ('01800000-0000-7000-8000-000000000125', '01800000-0000-7000-8000-000000000005', 'gray_leaf_spot', 'Gray leaf spot', 'Gray leaf spot', NULL, 'LOW', false, 'local-seed', 'local-seed'),
    ('01800000-0000-7000-8000-000000000126', '01800000-0000-7000-8000-000000000005', 'healthy', 'Healthy', 'Healthy', NULL, 'NONE', true, 'local-seed', 'local-seed')
ON CONFLICT (id) DO NOTHING;

INSERT INTO disease_symptom (disease_id, symptom_id, weight) VALUES
    ('01800000-0000-7000-8000-000000000121', '01800000-0000-7000-8000-000000000301', 0.900),
    ('01800000-0000-7000-8000-000000000121', '01800000-0000-7000-8000-000000000304', 0.400),
    ('01800000-0000-7000-8000-000000000121', '01800000-0000-7000-8000-000000000307', 0.200),
    ('01800000-0000-7000-8000-000000000122', '01800000-0000-7000-8000-000000000304', 0.900),
    ('01800000-0000-7000-8000-000000000122', '01800000-0000-7000-8000-000000000301', 0.500),
    ('01800000-0000-7000-8000-000000000122', '01800000-0000-7000-8000-000000000307', 0.300),
    ('01800000-0000-7000-8000-000000000124', '01800000-0000-7000-8000-000000000301', 0.900),
    ('01800000-0000-7000-8000-000000000124', '01800000-0000-7000-8000-000000000304', 0.400),
    ('01800000-0000-7000-8000-000000000124', '01800000-0000-7000-8000-000000000307', 0.200),
    ('01800000-0000-7000-8000-000000000125', '01800000-0000-7000-8000-000000000312', 0.900),
    ('01800000-0000-7000-8000-000000000125', '01800000-0000-7000-8000-000000000301', 0.500),
    ('01800000-0000-7000-8000-000000000125', '01800000-0000-7000-8000-000000000307', 0.200)
ON CONFLICT (disease_id, symptom_id) DO NOTHING;

INSERT INTO remedy (
    id, disease_id, type, title_bn, steps_bn, dosage_bn, phi_days,
    cost_tier, efficacy, source_ref, display_order, active, created_by, updated_by
) VALUES
    ('01800000-0000-7000-8000-000000000541', '01800000-0000-7000-8000-000000000121', 'CULTURAL',
     'TODO(content-owner: C6)', '["TODO(content-owner: C6)"]'::jsonb, NULL, NULL,
     'LOW', 'LOW', 'DEMO ONLY — local test stub for wambugu71 map', 1, true, 'local-seed', 'local-seed'),
    ('01800000-0000-7000-8000-000000000542', '01800000-0000-7000-8000-000000000122', 'CULTURAL',
     'TODO(content-owner: C6)', '["TODO(content-owner: C6)"]'::jsonb, NULL, NULL,
     'LOW', 'LOW', 'DEMO ONLY — local test stub for wambugu71 map', 1, true, 'local-seed', 'local-seed'),
    ('01800000-0000-7000-8000-000000000543', '01800000-0000-7000-8000-000000000124', 'CULTURAL',
     'TODO(content-owner: C6)', '["TODO(content-owner: C6)"]'::jsonb, NULL, NULL,
     'LOW', 'LOW', 'DEMO ONLY — local test stub for wambugu71 map', 1, true, 'local-seed', 'local-seed'),
    ('01800000-0000-7000-8000-000000000544', '01800000-0000-7000-8000-000000000125', 'CULTURAL',
     'TODO(content-owner: C6)', '["TODO(content-owner: C6)"]'::jsonb, NULL, NULL,
     'LOW', 'LOW', 'DEMO ONLY — local test stub for wambugu71 map', 1, true, 'local-seed', 'local-seed')
ON CONFLICT (id) DO NOTHING;

INSERT INTO model_label_map (id, model_id, model_version, raw_label, disease_id) VALUES
    ('01800000-0000-7000-8000-000000000651', 'wambugu71/crop_leaf_diseases_vit', '1', 'Rice___Brown_Spot', '01800000-0000-7000-8000-000000000101'),
    ('01800000-0000-7000-8000-000000000652', 'wambugu71/crop_leaf_diseases_vit', '1', 'Rice___Leaf_Blast', '01800000-0000-7000-8000-000000000103'),
    ('01800000-0000-7000-8000-000000000653', 'wambugu71/crop_leaf_diseases_vit', '1', 'Rice___Healthy', '01800000-0000-7000-8000-000000000106'),
    ('01800000-0000-7000-8000-000000000654', 'wambugu71/crop_leaf_diseases_vit', '1', 'Potato___Early_Blight', '01800000-0000-7000-8000-000000000112'),
    ('01800000-0000-7000-8000-000000000655', 'wambugu71/crop_leaf_diseases_vit', '1', 'Potato___Late_Blight', '01800000-0000-7000-8000-000000000113'),
    ('01800000-0000-7000-8000-000000000656', 'wambugu71/crop_leaf_diseases_vit', '1', 'Potato___Healthy', '01800000-0000-7000-8000-000000000114'),
    ('01800000-0000-7000-8000-000000000657', 'wambugu71/crop_leaf_diseases_vit', '1', 'Wheat___Brown_Rust', '01800000-0000-7000-8000-000000000121'),
    ('01800000-0000-7000-8000-000000000658', 'wambugu71/crop_leaf_diseases_vit', '1', 'Wheat___Yellow_Rust', '01800000-0000-7000-8000-000000000122'),
    ('01800000-0000-7000-8000-000000000659', 'wambugu71/crop_leaf_diseases_vit', '1', 'Wheat___Healthy', '01800000-0000-7000-8000-000000000123'),
    ('01800000-0000-7000-8000-000000000660', 'wambugu71/crop_leaf_diseases_vit', '1', 'Corn___Common_Rust', '01800000-0000-7000-8000-000000000124'),
    ('01800000-0000-7000-8000-000000000661', 'wambugu71/crop_leaf_diseases_vit', '1', 'Corn___Gray_Leaf_Spot', '01800000-0000-7000-8000-000000000125'),
    ('01800000-0000-7000-8000-000000000662', 'wambugu71/crop_leaf_diseases_vit', '1', 'Corn___Healthy', '01800000-0000-7000-8000-000000000126')
ON CONFLICT (model_id, model_version, raw_label) DO UPDATE
SET disease_id = EXCLUDED.disease_id;

COMMIT;
