-- Taxa e oferta atuais dos titulos do Tesouro (docs/planos/PLANO_RENTABILIDADE.md, andamento 17/10/2026).
--
-- Uma linha por titulo do catalogo, so o estado mais recente (sem historico): a carga do Tesouro (adm-api) grava
-- a taxa do ultimo dia do arquivo de precos e se o titulo esta a venda pelo arquivo de vendas.
--   rate_date   data base da taxa (ultimo dia do arquivo PrecoTaxaTesouroDireto)
--   buy_rate    taxa de compra da manha, % a.a. (no IPCA+, a taxa real acima do IPCA)
--   sell_rate   taxa de venda (resgate) da manha, % a.a.
--   on_sale     teve venda no ultimo dia do arquivo VendasTesouroDireto (o Tesouro oferece o titulo hoje)
--   sale_date   esse ultimo dia de vendas (liquidacao)
-- Uso: benchmark padrao do Tesouro na Rentabilidade = IPCA + a taxa de compra do Tesouro IPCA+ a venda mais curto.

CREATE TABLE treasury_offers (
    asset_id    BIGINT        PRIMARY KEY,
    rate_date   DATE          NOT NULL,
    buy_rate    NUMERIC(8,4),
    sell_rate   NUMERIC(8,4),
    on_sale     BOOLEAN       NOT NULL DEFAULT FALSE,
    sale_date   DATE,
    updated_at  TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT fk_treasury_offers_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);