-- Label map for VisionaryQuant/5_Crop_Disease_Detection (EfficientNet-B3).
-- Twelve native strings are character-identical to V109 ViT labels and map to the
-- same disease_id values. Five native classes have no taxonomy equivalent and are
-- left unmapped (COMMON-DATA-012 / COMMON-DATA-013):
--   Corn___Northern_Leaf_Blight
--   Rice___Neck_Blast
--   Sugarcane__Bacterial_Blight
--   Sugarcane__Healthy
--   Sugarcane__Red_Rot

INSERT INTO model_label_map (id, model_id, model_version, raw_label, disease_id) VALUES
    ('01800000-0000-7000-8000-000000000663', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Corn___Common_Rust',    '01800000-0000-7000-8000-000000000115'),
    ('01800000-0000-7000-8000-000000000664', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Corn___Gray_Leaf_Spot', '01800000-0000-7000-8000-000000000116'),
    ('01800000-0000-7000-8000-000000000665', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Corn___Healthy',        '01800000-0000-7000-8000-000000000117'),
    ('01800000-0000-7000-8000-000000000666', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Potato___Early_Blight', '01800000-0000-7000-8000-000000000112'),
    ('01800000-0000-7000-8000-000000000667', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Potato___Healthy',      '01800000-0000-7000-8000-000000000114'),
    ('01800000-0000-7000-8000-000000000668', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Potato___Late_Blight',  '01800000-0000-7000-8000-000000000113'),
    ('01800000-0000-7000-8000-000000000669', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Rice___Brown_Spot',     '01800000-0000-7000-8000-000000000101'),
    ('01800000-0000-7000-8000-000000000670', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Rice___Healthy',        '01800000-0000-7000-8000-000000000106'),
    ('01800000-0000-7000-8000-000000000671', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Rice___Leaf_Blast',     '01800000-0000-7000-8000-000000000103'),
    ('01800000-0000-7000-8000-000000000672', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Wheat___Brown_Rust',    '01800000-0000-7000-8000-000000000118'),
    ('01800000-0000-7000-8000-000000000673', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Wheat___Healthy',       '01800000-0000-7000-8000-000000000120'),
    ('01800000-0000-7000-8000-000000000674', 'VisionaryQuant/5_Crop_Disease_Detection', '63080391f7d2bdb331ab356b0d1d9b4b603b3946', 'Wheat___Yellow_Rust',   '01800000-0000-7000-8000-000000000119');
