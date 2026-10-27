-- Euro no cadastro de moedas, inativo (docs/planos/PLANO_CADASTRO_MOEDAS.md, 26/10/2026, pedido do Guilherme).
--
-- A conferência das integrações trata como dinheiro (e não como posição) as moedas do cadastro. Antes do cadastro a
-- lista fixa era BRL/USD/EUR; sem o EUR, um saldo em euro na Binance apareceria como divergência. Inativo: não tem
-- série de câmbio carregada pela tarefa nem aparece para o usuário; ativar na tela Moedas quando houver ativo em euro.
INSERT INTO currencies (code, name, symbol, decimals, is_base, active, rate_source, country_code) VALUES
    ('EUR', 'Euro', '€', 2, false, false, 'BCB_PTAX', NULL)
ON CONFLICT (code) DO NOTHING;
