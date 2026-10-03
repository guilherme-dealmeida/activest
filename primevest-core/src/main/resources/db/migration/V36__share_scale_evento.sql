-- scale_check EVENTO: a quantidade que nao fechou pelo FRE, pelo LPA nem pelo vizinho e
-- conferida contra a do balanco validado anterior ajustada pelos grupamentos, desdobramentos
-- e bonificacoes registrados entre as duas datas (FinancialStatementImportService.resolveByStockEvents).
ALTER TABLE share_counts DROP CONSTRAINT ck_share_counts_check;
ALTER TABLE share_counts ADD CONSTRAINT ck_share_counts_check
    CHECK (scale_check IS NULL OR scale_check IN ('FRE', 'LPA', 'VIZINHO', 'EVENTO'));
