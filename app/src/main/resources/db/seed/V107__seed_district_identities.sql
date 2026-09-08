-- One farmer, officer, and admin per district. V100 keeps Demo Farmer / officer / admin on DHA.
-- Same bcrypt as V100 (`password`). Dummy phone_enc. Not production identities.

INSERT INTO farmer (id, name, phone_hash, phone_enc, district_code, division_code, preferred_language)
SELECT
    ('01800000-0000-7000-8000-' || lpad(to_hex(3000 + n), 12, '0'))::uuid,
    'Demo Farmer ' || name_en,
    encode(digest('+8801712' || lpad(n::text, 6, '0'), 'sha256'), 'hex'),
    decode('8280c52515573873ad1a4fee67462dbae0e06d955f653640b24e7536', 'hex'),
    code,
    division_code,
    'bn'
FROM (
    SELECT code, division_code, name_en, row_number() OVER (ORDER BY code) AS n
    FROM geo_district
    WHERE code <> 'DHA'
) d;

INSERT INTO field_officer (id, name, username, password_hash, phone_hash, phone_enc, district_code, division_code, role, active)
SELECT
    ('01800000-0000-7000-8000-' || lpad(to_hex(4000 + n), 12, '0'))::uuid,
    'Officer ' || name_en,
    'officer-' || lower(code),
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    encode(digest('officer-phone-' || code, 'sha256'), 'hex'),
    decode('8280c52515573873ad1a4fee67462dbae0e06d955f653640b24e7536', 'hex'),
    code,
    division_code,
    'OFFICER',
    true
FROM (
    SELECT code, division_code, name_en, row_number() OVER (ORDER BY code) AS n
    FROM geo_district
) d;

INSERT INTO field_officer (id, name, username, password_hash, phone_hash, phone_enc, district_code, division_code, role, active)
SELECT
    ('01800000-0000-7000-8000-' || lpad(to_hex(5000 + n), 12, '0'))::uuid,
    'Admin ' || name_en,
    'admin-' || lower(code),
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    encode(digest('admin-phone-' || code, 'sha256'), 'hex'),
    decode('8280c52515573873ad1a4fee67462dbae0e06d955f653640b24e7536', 'hex'),
    code,
    division_code,
    'ADMIN',
    true
FROM (
    SELECT code, division_code, name_en, row_number() OVER (ORDER BY code) AS n
    FROM geo_district
) d;
