-- Fictional demo personas (CONTENT-OWNERS clarification 23). Demo profile only.
-- Officer password is the bcrypt of `password` (documented in README.md). Not a production secret.

INSERT INTO farmer (id, name, phone_hash, phone_enc, district_code, preferred_language)
VALUES (
    '01800000-0000-7000-8000-000000000201',
    'Demo Farmer',
    '32b85d49f70073a0b2523cd72f5c68f0add4da3e7be86c18b227ca4c5fab1592',
    decode('8280c52515573873ad1a4fee67462dbae0e06d955f653640b24e7536', 'hex'),
    'DHA',
    'bn'
);

INSERT INTO field_officer (id, name, username, password_hash, phone_hash, phone_enc, district_code, role, active)
VALUES (
    '01800000-0000-7000-8000-000000000202',
    'Demo Officer',
    'officer',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    '688ac7a2d39337b6f97a6f69691b78edc6d27bd3bf365d74715977ad8dc28c87',
    decode('8280c52515573873ad1a4fee67462dbae0e06d955f653640b24e7536', 'hex'),
    'DHA',
    'OFFICER',
    true
);

INSERT INTO field_officer (id, name, username, password_hash, phone_hash, phone_enc, district_code, role, active)
VALUES (
    '01800000-0000-7000-8000-000000000203',
    'Demo Admin',
    'admin',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    '688ac7a2d39337b6f97a6f69691b78edc6d27bd3bf365d74715977ad8dc28c87',
    decode('8280c52515573873ad1a4fee67462dbae0e06d955f653640b24e7536', 'hex'),
    'DHA',
    'ADMIN',
    true
);
