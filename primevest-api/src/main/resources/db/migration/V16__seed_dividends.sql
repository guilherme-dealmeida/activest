INSERT INTO dividends (asset_id, portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, type, created_at)
VALUES ((SELECT id FROM assets WHERE ticker = 'PETR4'), 'c0000001-0000-0000-0000-000000000025', 0.85, 80.75, '2026-08-20', '2026-09-05', 'DIVIDENDO', NOW());
INSERT INTO dividends (asset_id, portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, type, created_at)
VALUES ((SELECT id FROM assets WHERE ticker = 'BBAS3'), 'c0000001-0000-0000-0000-000000000018', 0.42, 73.5, '2026-08-15', '2026-09-01', 'DIVIDENDO', NOW());
INSERT INTO dividends (asset_id, portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, type, created_at)
VALUES ((SELECT id FROM assets WHERE ticker = 'HGLG11'), 'c0000001-0000-0000-0000-000000000013', 1.1, 27.5, '2026-08-10', '2026-08-20', 'RENDIMENTO', NOW());
INSERT INTO dividends (asset_id, portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, type, created_at)
VALUES ((SELECT id FROM assets WHERE ticker = 'BTLG11'), 'c0000001-0000-0000-0000-000000000010', 0.78, 19.5, '2026-08-10', '2026-08-20', 'RENDIMENTO', NOW());
INSERT INTO dividends (asset_id, portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, type, created_at)
VALUES ((SELECT id FROM assets WHERE ticker = 'KLBN4'), 'c0000001-0000-0000-0000-000000000024', 0.08, 48, '2026-07-25', '2026-08-10', 'JCP', NOW());
INSERT INTO dividends (asset_id, portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, type, created_at)
VALUES ((SELECT id FROM assets WHERE ticker = 'ITSA4'), 'c0000001-0000-0000-0000-000000000023', 0.15, 22.5, '2026-07-20', '2026-08-05', 'JCP', NOW());
