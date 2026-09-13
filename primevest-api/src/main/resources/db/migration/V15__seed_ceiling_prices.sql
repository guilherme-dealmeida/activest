INSERT INTO ceiling_prices (id, user_id, asset_id, max_price, alert_enabled, created_at)
VALUES (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'PETR4'), 32, true, NOW());
INSERT INTO ceiling_prices (id, user_id, asset_id, max_price, alert_enabled, created_at)
VALUES (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'BBAS3'), 25, true, NOW());
INSERT INTO ceiling_prices (id, user_id, asset_id, max_price, alert_enabled, created_at)
VALUES (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'ITSA4'), 12, true, NOW());
INSERT INTO ceiling_prices (id, user_id, asset_id, max_price, alert_enabled, created_at)
VALUES (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'HGLG11'), 160, true, NOW());
INSERT INTO ceiling_prices (id, user_id, asset_id, max_price, alert_enabled, created_at)
VALUES (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'BTLG11'), 105, false, NOW());
INSERT INTO ceiling_prices (id, user_id, asset_id, max_price, alert_enabled, created_at)
VALUES (gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'KLBN4'), 4.5, true, NOW());
