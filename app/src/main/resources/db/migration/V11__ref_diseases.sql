-- TODO(content-owner: C1) name_bn, description_bn and severity await human sign-off.
-- English names and the 14-class list are from 00-common.ears.md §2.5.
-- is_healthy and severity=NONE on the three healthy classes are specified there.
-- Non-healthy severity is LOW as a NOT NULL scaffold only, not a clinical judgement.
INSERT INTO disease (id, crop_id, code, name_bn, name_en, description_bn, severity, is_healthy) VALUES
    ('01800000-0000-7000-8000-000000000101', '01800000-0000-7000-8000-000000000001', 'brown_spot', 'Brown spot', 'Brown spot', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000102', '01800000-0000-7000-8000-000000000001', 'leaf_scald', 'Leaf scald', 'Leaf scald', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000103', '01800000-0000-7000-8000-000000000001', 'blast', 'Blast', 'Blast', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000104', '01800000-0000-7000-8000-000000000001', 'tungro', 'Tungro', 'Tungro', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000105', '01800000-0000-7000-8000-000000000001', 'sheath_blight', 'Sheath blight', 'Sheath blight', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000106', '01800000-0000-7000-8000-000000000001', 'healthy', 'Healthy', 'Healthy', NULL, 'NONE', true),
    ('01800000-0000-7000-8000-000000000107', '01800000-0000-7000-8000-000000000002', 'early_blight', 'Early blight', 'Early blight', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000108', '01800000-0000-7000-8000-000000000002', 'late_blight', 'Late blight', 'Late blight', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000109', '01800000-0000-7000-8000-000000000002', 'leaf_curl_virus', 'Leaf curl virus', 'Leaf curl virus', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000110', '01800000-0000-7000-8000-000000000002', 'septoria_leaf_spot', 'Septoria leaf spot', 'Septoria leaf spot', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000111', '01800000-0000-7000-8000-000000000002', 'healthy', 'Healthy', 'Healthy', NULL, 'NONE', true),
    ('01800000-0000-7000-8000-000000000112', '01800000-0000-7000-8000-000000000003', 'early_blight', 'Early blight', 'Early blight', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000113', '01800000-0000-7000-8000-000000000003', 'late_blight', 'Late blight', 'Late blight', NULL, 'LOW', false),
    ('01800000-0000-7000-8000-000000000114', '01800000-0000-7000-8000-000000000003', 'healthy', 'Healthy', 'Healthy', NULL, 'NONE', true);
