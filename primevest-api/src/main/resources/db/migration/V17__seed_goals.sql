
INSERT INTO goals (id, user_id, name, target_amount, current_amount, target_date, status, created_at) VALUES 
(gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Reserva de Emergência 6 meses', 30000, 25192.94, '2027-01-01', 'ACTIVE', NOW()),
(gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Carteira R$ 100.000', 100000, 64256.33, '2028-01-01', 'ACTIVE', NOW()),
(gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Renda Passiva R$ 500/mês', 6000, 2711.17, '2029-01-01', 'ACTIVE', NOW()),
(gen_random_uuid(), 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Viagem Europa', 15000, 15000, '2026-06-01', 'COMPLETED', NOW());
