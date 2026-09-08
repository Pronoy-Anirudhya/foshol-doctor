-- HACKATHON DEMO rates and extra steps for V18 remedies (ids …501–…533).
-- Not C15 / not production agronomy. Officer approval still required before farmer delivery.
-- Do not treat these numbers as DAE/BRRI/BARI labels. Wipe .data or migrate in place.

-- Extra DEMO step on every seeded remedy (warning row already exists as steps_bn[0]).
UPDATE remedy
SET steps_bn = steps_bn || '["ডেমো: মাঠ কর্মকর্তা কৃষককে মাত্রা ও অপেক্ষাকাল বুঝিয়ে দিন।"]'::jsonb,
    updated_by = 'demo-seed',
    updated_at = now()
WHERE id BETWEEN '01800000-0000-7000-8000-000000000501' AND '01800000-0000-7000-8000-000000000533'
  AND created_by = 'demo-seed';

-- Chemical + dosed organic/biological only. Cultural / no-quantity rows stay rate-null.

UPDATE remedy SET rate_amount = 40, rate_unit = 'ML', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000503';

UPDATE remedy SET rate_amount = 4, rate_unit = 'G', rate_basis = 'FIXED',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়; প্রতি কেজি বীজ', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000505';

UPDATE remedy SET rate_amount = 40, rate_unit = 'ML', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000506';

UPDATE remedy SET rate_amount = 3, rate_unit = 'ML', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000508';

UPDATE remedy SET rate_amount = 80, rate_unit = 'G', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000509';

UPDATE remedy SET rate_amount = 50, rate_unit = 'ML', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000512';

UPDATE remedy SET rate_amount = 2.5, rate_unit = 'KG', rate_basis = 'PER_HECTARE',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000514';

UPDATE remedy SET rate_amount = 1, rate_unit = 'L', rate_basis = 'PER_HECTARE',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000515';

UPDATE remedy SET rate_amount = 40, rate_unit = 'ML', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000517';

UPDATE remedy SET rate_amount = 50, rate_unit = 'G', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000518';

UPDATE remedy SET rate_amount = 40, rate_unit = 'G', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000520';

UPDATE remedy SET rate_amount = 50, rate_unit = 'G', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000521';

UPDATE remedy SET rate_amount = 3, rate_unit = 'ML', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000523';

UPDATE remedy SET rate_amount = 50, rate_unit = 'ML', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000524';

UPDATE remedy SET rate_amount = 50, rate_unit = 'G', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000527';

UPDATE remedy SET rate_amount = 40, rate_unit = 'G', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000529';

UPDATE remedy SET rate_amount = 50, rate_unit = 'G', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000530';

UPDATE remedy SET rate_amount = 40, rate_unit = 'G', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000532';

UPDATE remedy SET rate_amount = 50, rate_unit = 'G', rate_basis = 'PER_DECIMAL',
    rate_notes_bn = 'ডেমো মাত্রা — লেবেল নয়', updated_by = 'demo-seed', updated_at = now()
WHERE id = '01800000-0000-7000-8000-000000000533';
