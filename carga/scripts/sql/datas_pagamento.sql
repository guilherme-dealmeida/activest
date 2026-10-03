-- Cobertura da data de pagamento dos proventos do catalogo, por ano da data-com e tipo de ativo
-- (docs/planos/PLANO_CARGA_INICIAL.md, 5.4 e D14). "sem_data" de acao antes de 2022 e o que a D14 decide.
-- Uso: docker exec -i primevest-db psql -U postgres -d db_primevest < carga/scripts/sql/datas_pagamento.sql
SELECT extract(year FROM e.data_com)::int                                        AS ano,
       CASE WHEN t.name = 'FII' THEN 'FII' ELSE 'acao/outros' END                 AS tipo,
       count(*)                                                                    AS eventos,
       count(*) FILTER (WHERE e.payment_date IS NULL)                              AS sem_data,
       count(*) FILTER (WHERE e.payment_date_source = 'CVM')                       AS data_cvm,
       round(100.0 * count(*) FILTER (WHERE e.payment_date IS NOT NULL) / count(*), 1) AS pct_com_data
FROM asset_cash_events e
JOIN assets a ON a.id = e.asset_id
JOIN asset_types t ON t.id = a.asset_type_id
WHERE e.data_com >= DATE '2010-01-01' AND e.type IS NOT NULL
GROUP BY 1, 2
ORDER BY 1 DESC, 2;
