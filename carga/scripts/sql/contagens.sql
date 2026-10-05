-- Contagens do catalogo, para comparar um banco novo (bootstrap) com o de desenvolvimento.
-- Uso: docker exec -i primevest-db psql -U postgres -d db_primevest < carga/scripts/sql/contagens.sql
-- Referencia do ensaio de 28/09 (docs/planos/PLANO_EMPRESAS_BALANCOS.md, F11) e aceite do docs/planos/PLANO_CARGA_INICIAL.md, secao 4.
select 'assets' as tabela, count(*) from assets union all
select 'issuers', count(*) from issuers union all
select 'ligados', count(*) from assets where issuer_id is not null union all
select 'unit_compositions', count(*) from unit_compositions union all
select 'asset_price_history', count(*) from asset_price_history union all
select 'asset_cash_events', count(*) from asset_cash_events union all
select 'asset_cash_events ' || source, count(*) from asset_cash_events group by source union all
select 'asset_stock_events', count(*) from asset_stock_events union all
select 'cvm_documents', count(*) from cvm_documents union all
select 'share_counts REVISAR', count(*) from share_counts where scale_status = 'REVISAR' union all
select 'company_quarters', count(*) from company_quarters union all
select 'fii_monthly', count(*) from fii_monthly union all
select 'market_index_values', count(*) from market_index_values union all
select 'indice ' || i.code, count(*) from market_index_values v join market_indices i on i.id = v.index_id group by i.code union all
select 'cash sem tipo', count(*) from asset_cash_events where type is null
order by 1;
