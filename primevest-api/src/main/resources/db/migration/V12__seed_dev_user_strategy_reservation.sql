-- Estrategia de reserva do usuario de desenvolvimento.
--
-- Usuario criado pela API ganha a sua no cadastro (AuthService); o semeado
-- pela V9 nao passa por esse caminho. O padrao e o mesmo do cadastro: por
-- percentual, alvo 0,00%.
--
-- Usuario resolvido por email, nunca por id literal. ON CONFLICT porque a
-- linha e unica por usuario e pode ja existir num banco que subiu antes.
INSERT INTO strategy_reservations (user_id, type_reservation, target_percentage, target_value)
SELECT u.id, 1, 0.00, NULL
FROM users u
WHERE u.email = 'guilhermeunatec@gmail.com'
ON CONFLICT (user_id) DO NOTHING;
