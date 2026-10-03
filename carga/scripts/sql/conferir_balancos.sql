-- Conferencia da carga de balancos (F5): quantidade de acoes, LPA calculado x informado, casos-referencia.
\echo === quantidade de acoes por fonte e situacao
SELECT s.source, s.scale_status, coalesce(s.scale_check, '-') AS conferencia, count(*) AS docs,
       count(DISTINCT d.issuer_id) AS emissores
FROM share_counts s JOIN cvm_documents d ON d.id = s.document_id GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;

\echo === LPA calculado / LPA informado nos DFP (quantidade validada, lucro e LPA nao nulos)
WITH x AS (
  SELECT d.id,
    (SELECT value FROM cvm_statement_values v WHERE v.document_id = d.id AND v.metric = 'LUCRO_CONTROLADORA'
      ORDER BY period_start LIMIT 1) AS lucro,
    (SELECT value FROM cvm_statement_values v WHERE v.document_id = d.id AND v.metric IN ('LPA_ON', 'LPA_PN')
      ORDER BY metric, period_start LIMIT 1) AS lpa,
    (s.total_shares - coalesce(s.total_treasury, 0))::numeric * s.scale_factor AS acoes
  FROM cvm_documents d JOIN share_counts s ON s.document_id = d.id
  WHERE d.doc_type = 'DFP' AND s.scale_factor IS NOT NULL),
r AS (SELECT lucro / acoes / lpa AS razao FROM x WHERE lucro <> 0 AND lpa <> 0 AND acoes > 0)
SELECT count(*) AS dfps,
  count(*) FILTER (WHERE razao BETWEEN 0.9 AND 1.1) AS ate_10pct,
  count(*) FILTER (WHERE razao BETWEEN 0.5 AND 2) AS ate_2x,
  count(*) FILTER (WHERE razao NOT BETWEEN 0.5 AND 2) AS fora
FROM r;

\echo === Petrobras DFP 2025 (plano, 1.1: LPA 8,54; VPA 32,26; payout 37,4%)
SELECT d.reference_date,
  round(l.value / ((s.total_shares - coalesce(s.total_treasury, 0))::numeric * s.scale_factor), 2) AS lpa,
  round((pl.value - coalesce(nc.value, 0)) / ((s.total_shares - coalesce(s.total_treasury, 0))::numeric * s.scale_factor), 2) AS vpa,
  round(100 * (coalesce(j.value, 0) + coalesce(dv.value, 0)) / l.value, 1) AS payout
FROM cvm_documents d JOIN issuers i ON i.id = d.issuer_id JOIN share_counts s ON s.document_id = d.id
JOIN cvm_statement_values l ON l.document_id = d.id AND l.metric = 'LUCRO_CONTROLADORA'
JOIN cvm_statement_values pl ON pl.document_id = d.id AND pl.metric = 'PL'
LEFT JOIN cvm_statement_values nc ON nc.document_id = d.id AND nc.metric = 'PL_NAO_CONTROLADORES'
LEFT JOIN cvm_statement_values j ON j.document_id = d.id AND j.metric = 'JCP_DECLARADO'
LEFT JOIN cvm_statement_values dv ON dv.document_id = d.id AND dv.metric = 'DIVIDENDOS_DECLARADOS'
WHERE i.cnpj = '33000167000101' AND d.doc_type = 'DFP' AND d.reference_date >= '2020-01-01'
ORDER BY 1;

\echo === ativos do catalogo (ACAO) sem nenhuma quantidade validada no ultimo DFP do emissor
SELECT count(DISTINCT i.id) AS emissores FROM issuers i
WHERE EXISTS (SELECT 1 FROM assets a JOIN asset_types t ON t.id = a.asset_type_id WHERE a.issuer_id = i.id AND t.name = 'ACAO' AND a.active)
  AND NOT EXISTS (SELECT 1 FROM cvm_documents d JOIN share_counts s ON s.document_id = d.id
                  WHERE d.issuer_id = i.id AND s.scale_factor IS NOT NULL AND d.reference_date >= '2025-01-01');
