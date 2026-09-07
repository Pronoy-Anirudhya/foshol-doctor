-- TODO(content-owner: C2) name_bn and icon_key await human sign-off.
-- Codes and English names are taken from 00-common.ears.md §2.5.
INSERT INTO crop (id, code, name_bn, name_en, icon_key, display_order) VALUES
    ('01800000-0000-7000-8000-000000000001',   'rice',   'rice',   'Rice',   'crop-rice',   1),
    ('01800000-0000-7000-8000-000000000002', 'tomato', 'tomato', 'Tomato', 'crop-tomato', 2),
    ('01800000-0000-7000-8000-000000000003', 'potato', 'potato', 'Potato', 'crop-potato', 3);
