-- Alerta de preço do Preço Teto (docs/planos/PLANO_NOTIFICACOES.md, 3.2 e 5.5): "avise quando o preço atual ficar
-- abaixo do Preço Ideal". Colunas novas em tabela existente nascem com o default (REGRAS 8.1).
--   alert_enabled     — o interruptor da coluna "Alerta" da lista (desligado por padrão);
--   alert_below_ideal — o preço estava abaixo do ideal na última conferência: só avisa de novo depois de voltar acima;
--   alert_last_on     — último dia avisado: no máximo um aviso por ativo por dia.
ALTER TABLE ceiling_prices ADD COLUMN alert_enabled     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ceiling_prices ADD COLUMN alert_below_ideal BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ceiling_prices ADD COLUMN alert_last_on     DATE;
