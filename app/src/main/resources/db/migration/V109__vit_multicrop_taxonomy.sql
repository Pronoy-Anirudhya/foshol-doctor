-- Append corn/wheat taxonomy and ViT label map for wambugu71/crop_leaf_diseases_vit.
-- Existing rice/tomato/potato rows and V18 mappings are kept (replay fixtures still use them).
-- TODO(content-owner: C1, C2, C4, C6) Bangla names, phrases and remedies await human sign-off.
-- Invalid is deliberately unmapped (COMMON-DATA-012 / COMMON-DATA-013).

INSERT INTO crop (id, code, name_bn, name_en, icon_key, display_order) VALUES
    ('01800000-0000-7000-8000-000000000004', 'corn',  'ভুট্টা', 'Corn',  'crop-corn',  4),
    ('01800000-0000-7000-8000-000000000005', 'wheat', 'গম',     'Wheat', 'crop-wheat', 5);

INSERT INTO disease (id, crop_id, code, name_bn, name_en, description_bn, severity, is_healthy) VALUES
    ('01800000-0000-7000-8000-000000000115', '01800000-0000-7000-8000-000000000004', 'common_rust',    'Common rust',    'Common rust',    NULL, 'MODERATE', false),
    ('01800000-0000-7000-8000-000000000116', '01800000-0000-7000-8000-000000000004', 'gray_leaf_spot', 'Gray leaf spot', 'Gray leaf spot', NULL, 'MODERATE', false),
    ('01800000-0000-7000-8000-000000000117', '01800000-0000-7000-8000-000000000004', 'healthy',        'Healthy',        'Healthy',        NULL, 'NONE',     true),
    ('01800000-0000-7000-8000-000000000118', '01800000-0000-7000-8000-000000000005', 'brown_rust',     'Brown rust',     'Brown rust',     NULL, 'MODERATE', false),
    ('01800000-0000-7000-8000-000000000119', '01800000-0000-7000-8000-000000000005', 'yellow_rust',    'Yellow rust',    'Yellow rust',    NULL, 'MODERATE', false),
    ('01800000-0000-7000-8000-000000000120', '01800000-0000-7000-8000-000000000005', 'healthy',        'Healthy',        'Healthy',        NULL, 'NONE',     true);

INSERT INTO symptom (id, code, name_bn, name_en, organ, created_by, updated_by) VALUES
    ('01800000-0000-7000-8000-000000000316', 'todo_corn_common_rust',    'TODO(content-owner: C3)', 'TODO(content-owner: C3)', 'LEAF', 'system', 'system'),
    ('01800000-0000-7000-8000-000000000317', 'todo_corn_gray_leaf_spot', 'TODO(content-owner: C3)', 'TODO(content-owner: C3)', 'LEAF', 'system', 'system'),
    ('01800000-0000-7000-8000-000000000318', 'todo_wheat_brown_rust',    'TODO(content-owner: C3)', 'TODO(content-owner: C3)', 'LEAF', 'system', 'system'),
    ('01800000-0000-7000-8000-000000000319', 'todo_wheat_yellow_rust',   'TODO(content-owner: C3)', 'TODO(content-owner: C3)', 'LEAF', 'system', 'system');

INSERT INTO symptom_phrase (id, symptom_id, phrase_bn, normalised_bn, created_by, updated_by) VALUES
    ('01800000-0000-7000-8000-000000000491', '01800000-0000-7000-8000-000000000316', 'TODO(content-owner: C4)', 'TODO(content-owner: C4)', 'system', 'system'),
    ('01800000-0000-7000-8000-000000000492', '01800000-0000-7000-8000-000000000317', 'TODO(content-owner: C4)', 'TODO(content-owner: C4)', 'system', 'system'),
    ('01800000-0000-7000-8000-000000000493', '01800000-0000-7000-8000-000000000318', 'TODO(content-owner: C4)', 'TODO(content-owner: C4)', 'system', 'system'),
    ('01800000-0000-7000-8000-000000000494', '01800000-0000-7000-8000-000000000319', 'TODO(content-owner: C4)', 'TODO(content-owner: C4)', 'system', 'system');

INSERT INTO disease_symptom (disease_id, symptom_id, weight) VALUES
    ('01800000-0000-7000-8000-000000000115', '01800000-0000-7000-8000-000000000316', 1.000),
    ('01800000-0000-7000-8000-000000000116', '01800000-0000-7000-8000-000000000317', 1.000),
    ('01800000-0000-7000-8000-000000000118', '01800000-0000-7000-8000-000000000318', 1.000),
    ('01800000-0000-7000-8000-000000000119', '01800000-0000-7000-8000-000000000319', 1.000);

INSERT INTO remedy (id, disease_id, type, title_bn, steps_bn, dosage_bn, phi_days, cost_tier, efficacy, source_ref, display_order, active, created_by, updated_by) VALUES
    ('01800000-0000-7000-8000-000000000534', '01800000-0000-7000-8000-000000000115', 'CULTURAL', 'TODO(content-owner: C6)', '["TODO(content-owner: C6)"]'::jsonb, NULL, NULL, 'LOW', 'LOW', 'TODO(content-owner: C6)', 1, true, 'system', 'system'),
    ('01800000-0000-7000-8000-000000000535', '01800000-0000-7000-8000-000000000116', 'CULTURAL', 'TODO(content-owner: C6)', '["TODO(content-owner: C6)"]'::jsonb, NULL, NULL, 'LOW', 'LOW', 'TODO(content-owner: C6)', 1, true, 'system', 'system'),
    ('01800000-0000-7000-8000-000000000536', '01800000-0000-7000-8000-000000000118', 'CULTURAL', 'TODO(content-owner: C6)', '["TODO(content-owner: C6)"]'::jsonb, NULL, NULL, 'LOW', 'LOW', 'TODO(content-owner: C6)', 1, true, 'system', 'system'),
    ('01800000-0000-7000-8000-000000000537', '01800000-0000-7000-8000-000000000119', 'CULTURAL', 'TODO(content-owner: C6)', '["TODO(content-owner: C6)"]'::jsonb, NULL, NULL, 'LOW', 'LOW', 'TODO(content-owner: C6)', 1, true, 'system', 'system');

CREATE OR REPLACE FUNCTION foshol_vit_placeholder_embedding(input text) RETURNS vector(768)
LANGUAGE plpgsql AS $$
DECLARE
  dims float8[] := ARRAY[]::float8[];
  i int;
  h bytea;
  n float8 := 0;
  v float8;
BEGIN
  FOR i IN 0..191 LOOP
    h := decode(md5(coalesce(input, '') || ':' || i::text), 'hex');
    dims := dims || ARRAY[
      (get_byte(h, 0)::float8 / 127.5) - 1.0,
      (get_byte(h, 1)::float8 / 127.5) - 1.0,
      (get_byte(h, 2)::float8 / 127.5) - 1.0,
      (get_byte(h, 3)::float8 / 127.5) - 1.0
    ];
  END LOOP;
  FOREACH v IN ARRAY dims LOOP
    n := n + v * v;
  END LOOP;
  n := sqrt(GREATEST(n, 1e-12));
  FOR i IN 1..768 LOOP
    dims[i] := dims[i] / n;
  END LOOP;
  RETURN dims::vector(768);
END;
$$;

UPDATE symptom_phrase
SET embedding = foshol_vit_placeholder_embedding(id::text)
WHERE id IN (
    '01800000-0000-7000-8000-000000000491',
    '01800000-0000-7000-8000-000000000492',
    '01800000-0000-7000-8000-000000000493',
    '01800000-0000-7000-8000-000000000494'
);
UPDATE symptom
SET embedding = foshol_vit_placeholder_embedding(id::text)
WHERE id IN (
    '01800000-0000-7000-8000-000000000316',
    '01800000-0000-7000-8000-000000000317',
    '01800000-0000-7000-8000-000000000318',
    '01800000-0000-7000-8000-000000000319'
);
DROP FUNCTION foshol_vit_placeholder_embedding(text);

INSERT INTO model_label_map (id, model_id, model_version, raw_label, disease_id) VALUES
    ('01800000-0000-7000-8000-000000000651', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Corn___Common_Rust',    '01800000-0000-7000-8000-000000000115'),
    ('01800000-0000-7000-8000-000000000652', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Corn___Gray_Leaf_Spot', '01800000-0000-7000-8000-000000000116'),
    ('01800000-0000-7000-8000-000000000653', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Corn___Healthy',        '01800000-0000-7000-8000-000000000117'),
    ('01800000-0000-7000-8000-000000000654', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Potato___Early_Blight', '01800000-0000-7000-8000-000000000112'),
    ('01800000-0000-7000-8000-000000000655', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Potato___Healthy',      '01800000-0000-7000-8000-000000000114'),
    ('01800000-0000-7000-8000-000000000656', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Potato___Late_Blight',  '01800000-0000-7000-8000-000000000113'),
    ('01800000-0000-7000-8000-000000000657', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Rice___Brown_Spot',     '01800000-0000-7000-8000-000000000101'),
    ('01800000-0000-7000-8000-000000000658', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Rice___Healthy',        '01800000-0000-7000-8000-000000000106'),
    ('01800000-0000-7000-8000-000000000659', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Rice___Leaf_Blast',     '01800000-0000-7000-8000-000000000103'),
    ('01800000-0000-7000-8000-000000000660', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Wheat___Brown_Rust',    '01800000-0000-7000-8000-000000000118'),
    ('01800000-0000-7000-8000-000000000661', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Wheat___Healthy',       '01800000-0000-7000-8000-000000000120'),
    ('01800000-0000-7000-8000-000000000662', 'wambugu71/crop_leaf_diseases_vit', '7d5b32bcd6f83a2f57e7e0346358fad276296877', 'Wheat___Yellow_Rust',   '01800000-0000-7000-8000-000000000119');
