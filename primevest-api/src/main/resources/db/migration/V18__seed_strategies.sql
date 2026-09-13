
INSERT INTO strategies (id, user_id, asset_type_id, target_percentage, include_reservation, created_at) VALUES
(gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 1, 30.00, true, NOW()),
(gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 2, 30.00, false, NOW()),
(gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 3, 30.00, false, NOW()),
(gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 4, 30.00, false, NOW()),
(gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 5, 5.00, false, NOW()),
(gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 6, 5.00, false, NOW());
