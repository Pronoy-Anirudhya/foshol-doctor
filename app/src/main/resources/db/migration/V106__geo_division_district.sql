-- Bangladesh 8 divisions and 64 districts (identity routing). Not agronomy.
-- Demo district code DHA remains Dhaka district. Wipe .data or migrate in place.

CREATE TABLE geo_division (
    code    varchar(8)   PRIMARY KEY,
    name_en varchar(80)  NOT NULL,
    name_bn varchar(80)  NOT NULL
);

CREATE TABLE geo_district (
    code          varchar(16) PRIMARY KEY,
    division_code varchar(8)  NOT NULL REFERENCES geo_division (code),
    name_en       varchar(80)  NOT NULL,
    name_bn       varchar(80)  NOT NULL
);

INSERT INTO geo_division (code, name_en, name_bn) VALUES
    ('BAR', 'Barishal', 'বরিশাল'),
    ('CTG', 'Chattogram', 'চট্টগ্রাম'),
    ('DHK', 'Dhaka', 'ঢাকা'),
    ('KHL', 'Khulna', 'খুলনা'),
    ('MYM', 'Mymensingh', 'ময়মনসিংহ'),
    ('RAJ', 'Rajshahi', 'রাজশাহী'),
    ('RAN', 'Rangpur', 'রংপুর'),
    ('SYL', 'Sylhet', 'সিলেট');

INSERT INTO geo_district (code, division_code, name_en, name_bn) VALUES
    ('BARGUNA', 'BAR', 'Barguna', 'বরগুনা'),
    ('BARISHAL', 'BAR', 'Barishal', 'বরিশাল'),
    ('BHOLA', 'BAR', 'Bhola', 'ভোলা'),
    ('JHALOKATI', 'BAR', 'Jhalokati', 'ঝালকাঠি'),
    ('PATUAKHALI', 'BAR', 'Patuakhali', 'পটুয়াখালী'),
    ('PIROJPUR', 'BAR', 'Pirojpur', 'পিরোজপুর'),
    ('BANDARBAN', 'CTG', 'Bandarban', 'বান্দরবান'),
    ('BRAHMANBARIA', 'CTG', 'Brahmanbaria', 'ব্রাহ্মণবাড়িয়া'),
    ('CHANDPUR', 'CTG', 'Chandpur', 'চাঁদপুর'),
    ('CTG', 'CTG', 'Chattogram', 'চট্টগ্রাম'),
    ('CUMILLA', 'CTG', 'Cumilla', 'কুমিল্লা'),
    ('COXBAZAR', 'CTG', 'Cox''s Bazar', 'কক্সবাজার'),
    ('FENI', 'CTG', 'Feni', 'ফেনী'),
    ('KHAGRACHHARI', 'CTG', 'Khagrachhari', 'খাগড়াছড়ি'),
    ('LAKSHMIPUR', 'CTG', 'Lakshmipur', 'লক্ষ্মীপুর'),
    ('NOAKHALI', 'CTG', 'Noakhali', 'নোয়াখালী'),
    ('RANGAMATI', 'CTG', 'Rangamati', 'রাঙামাটি'),
    ('DHA', 'DHK', 'Dhaka', 'ঢাকা'),
    ('FARIDPUR', 'DHK', 'Faridpur', 'ফরিদপুর'),
    ('GAZIPUR', 'DHK', 'Gazipur', 'গাজীপুর'),
    ('GOPALGANJ', 'DHK', 'Gopalganj', 'গোপালগঞ্জ'),
    ('KISHOREGANJ', 'DHK', 'Kishoreganj', 'কিশোরগঞ্জ'),
    ('MADARIPUR', 'DHK', 'Madaripur', 'মাদারীপুর'),
    ('MANIKGANJ', 'DHK', 'Manikganj', 'মানিকগঞ্জ'),
    ('MUNSHIGANJ', 'DHK', 'Munshiganj', 'মুন্সিগঞ্জ'),
    ('NARAYANGANJ', 'DHK', 'Narayanganj', 'নারায়ণগঞ্জ'),
    ('NARSINGDI', 'DHK', 'Narsingdi', 'নরসিংদী'),
    ('RAJBARI', 'DHK', 'Rajbari', 'রাজবাড়ী'),
    ('SHARIATPUR', 'DHK', 'Shariatpur', 'শরীয়তপুর'),
    ('TANGAIL', 'DHK', 'Tangail', 'টাঙ্গাইল'),
    ('BAGERHAT', 'KHL', 'Bagerhat', 'বাগেরহাট'),
    ('CHUADANGA', 'KHL', 'Chuadanga', 'চুয়াডাঙ্গা'),
    ('JASHORE', 'KHL', 'Jashore', 'যশোর'),
    ('JHENAIDAH', 'KHL', 'Jhenaidah', 'ঝিনাইদহ'),
    ('KHULNA', 'KHL', 'Khulna', 'খুলনা'),
    ('KUSHTIA', 'KHL', 'Kushtia', 'কুষ্টিয়া'),
    ('MAGURA', 'KHL', 'Magura', 'মাগুরা'),
    ('MEHERPUR', 'KHL', 'Meherpur', 'মেহেরপুর'),
    ('NARAIL', 'KHL', 'Narail', 'নড়াইল'),
    ('SATKHIRA', 'KHL', 'Satkhira', 'সাতক্ষীরা'),
    ('JAMALPUR', 'MYM', 'Jamalpur', 'জামালপুর'),
    ('MYMENSINGH', 'MYM', 'Mymensingh', 'ময়মনসিংহ'),
    ('NETROKONA', 'MYM', 'Netrokona', 'নেত্রকোণা'),
    ('SHERPUR', 'MYM', 'Sherpur', 'শেরপুর'),
    ('BOGURA', 'RAJ', 'Bogura', 'বগুড়া'),
    ('CHAPAINAWABGANJ', 'RAJ', 'Chapainawabganj', 'চাঁপাইনবাবগঞ্জ'),
    ('JOYPURHAT', 'RAJ', 'Joypurhat', 'জয়পুরহাট'),
    ('NAOGAON', 'RAJ', 'Naogaon', 'নওগাঁ'),
    ('NATORE', 'RAJ', 'Natore', 'নাটোর'),
    ('PABNA', 'RAJ', 'Pabna', 'পাবনা'),
    ('RAJSHAHI', 'RAJ', 'Rajshahi', 'রাজশাহী'),
    ('SIRAJGANJ', 'RAJ', 'Sirajganj', 'সিরাজগঞ্জ'),
    ('DINAJPUR', 'RAN', 'Dinajpur', 'দিনাজপুর'),
    ('GAIBANDHA', 'RAN', 'Gaibandha', 'গাইবান্ধা'),
    ('KURIGRAM', 'RAN', 'Kurigram', 'কুড়িগ্রাম'),
    ('LALMONIRHAT', 'RAN', 'Lalmonirhat', 'লালমনিরহাট'),
    ('NILPHAMARI', 'RAN', 'Nilphamari', 'নীলফামারী'),
    ('PANCHAGARH', 'RAN', 'Panchagarh', 'পঞ্চগড়'),
    ('RANGPUR', 'RAN', 'Rangpur', 'রংপুর'),
    ('THAKURGAON', 'RAN', 'Thakurgaon', 'ঠাকুরগাঁও'),
    ('HABIGANJ', 'SYL', 'Habiganj', 'হবিগঞ্জ'),
    ('MOULVIBAZAR', 'SYL', 'Moulvibazar', 'মৌলভীবাজার'),
    ('SUNAMGANJ', 'SYL', 'Sunamganj', 'সুনামগঞ্জ'),
    ('SYLHET', 'SYL', 'Sylhet', 'সিলেট');

ALTER TABLE farmer ALTER COLUMN district_code TYPE varchar(16);
ALTER TABLE field_officer ALTER COLUMN district_code TYPE varchar(16);
ALTER TABLE diagnosis_case ALTER COLUMN district_code TYPE varchar(16);
ALTER TABLE p_officer_queue ALTER COLUMN district_code TYPE varchar(16);

ALTER TABLE farmer ADD COLUMN IF NOT EXISTS division_code varchar(8);
ALTER TABLE field_officer ADD COLUMN IF NOT EXISTS division_code varchar(8);
ALTER TABLE diagnosis_case ADD COLUMN IF NOT EXISTS division_code varchar(8);
ALTER TABLE p_officer_queue ADD COLUMN IF NOT EXISTS division_code varchar(8);

UPDATE farmer f
SET division_code = d.division_code
FROM geo_district d
WHERE d.code = f.district_code AND f.division_code IS NULL;

UPDATE field_officer o
SET division_code = d.division_code
FROM geo_district d
WHERE d.code = o.district_code AND o.division_code IS NULL;

UPDATE diagnosis_case c
SET division_code = d.division_code
FROM geo_district d
WHERE d.code = c.district_code AND c.division_code IS NULL;

UPDATE diagnosis_case SET division_code = 'DHK' WHERE division_code IS NULL;
UPDATE farmer SET division_code = 'DHK' WHERE division_code IS NULL;
UPDATE field_officer SET division_code = 'DHK' WHERE division_code IS NULL;

UPDATE p_officer_queue q
SET division_code = c.division_code
FROM diagnosis_case c
WHERE c.id = q.case_id AND q.division_code IS NULL;

UPDATE p_officer_queue SET division_code = 'DHK' WHERE division_code IS NULL;

ALTER TABLE farmer ALTER COLUMN division_code SET NOT NULL;
ALTER TABLE field_officer ALTER COLUMN division_code SET NOT NULL;
ALTER TABLE diagnosis_case ALTER COLUMN division_code SET NOT NULL;
ALTER TABLE p_officer_queue ALTER COLUMN division_code SET NOT NULL;

ALTER TABLE farmer DROP CONSTRAINT IF EXISTS fk_farmer_district;
ALTER TABLE farmer ADD CONSTRAINT fk_farmer_district
    FOREIGN KEY (district_code) REFERENCES geo_district (code);
ALTER TABLE farmer DROP CONSTRAINT IF EXISTS fk_farmer_division;
ALTER TABLE farmer ADD CONSTRAINT fk_farmer_division
    FOREIGN KEY (division_code) REFERENCES geo_division (code);

ALTER TABLE field_officer DROP CONSTRAINT IF EXISTS fk_officer_district;
ALTER TABLE field_officer ADD CONSTRAINT fk_officer_district
    FOREIGN KEY (district_code) REFERENCES geo_district (code);
ALTER TABLE field_officer DROP CONSTRAINT IF EXISTS fk_officer_division;
ALTER TABLE field_officer ADD CONSTRAINT fk_officer_division
    FOREIGN KEY (division_code) REFERENCES geo_division (code);

ALTER TABLE diagnosis_case DROP CONSTRAINT IF EXISTS fk_case_district;
ALTER TABLE diagnosis_case ADD CONSTRAINT fk_case_district
    FOREIGN KEY (district_code) REFERENCES geo_district (code);
ALTER TABLE diagnosis_case DROP CONSTRAINT IF EXISTS fk_case_division;
ALTER TABLE diagnosis_case ADD CONSTRAINT fk_case_division
    FOREIGN KEY (division_code) REFERENCES geo_division (code);
