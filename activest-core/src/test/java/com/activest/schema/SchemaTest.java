package com.activest.schema;

import com.activest.support.JpaTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste de fundacao: o banco sobe, as migrations rodam e as entidades batem
 * com o schema (ddl-auto: validate falharia o contexto antes de chegar aqui).
 *
 * <p>As contagens de seed sao baratas e pegam migration quebrada antes que
 * qualquer outro teste quebre por um motivo que parece outro.</p>
 */
class SchemaTest extends JpaTestBase {

    @Autowired
    private EntityManager em;

    private long count(String table) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("Flyway roda ate a V17 (V1-V6 consolidadas + contas novas da tela de Ativos + metas + moedas, proventos do exterior, euro, fundos de investimento, ticker por tipo, origem da inativacao, estrutura do exterior e tarefas do catalogo do exterior e dos deslistados) e o schema valida contra as entidades")
    void appliedMigrations() {
        Number version = (Number) em.createNativeQuery(
            "SELECT MAX(CAST(version AS NUMERIC)) FROM flyway_schema_history WHERE success").getSingleResult();
        assertThat(version.intValue()).isEqualTo(17);
        // V17 (F5): FOREIGN_DELISTED so manual, de 2010, WRITE nasce falso
        assertThat(count("tasks t JOIN task_types y ON y.id = t.task_type_id WHERE y.type = 'FOREIGN_DELISTED' "
            + "AND t.cron IS NULL AND NOT t.run_on_startup")).isEqualTo(1);
        // V16 (PLANO_ATIVOS_INTERNACIONAIS, F4): FOREIGN_CATALOG as segundas 06:30, WRITE nasce falso (portao da D12)
        assertThat(count("tasks t JOIN task_attributes ta ON ta.task_id = t.id JOIN task_type_attributes a "
            + "ON a.id = ta.task_type_attribute_id WHERE t.name = 'FOREIGN_CATALOG' AND t.cron = '0 30 6 * * MON' "
            + "AND a.att_name = 'WRITE' AND ta.att_value = 'false'")).isEqualTo(1);
        // V15 (PLANO_ATIVOS_INTERNACIONAIS, F3): foreign_listings 1:1 e as categorias dos tres tipos (D6-D8)
        assertThat(count("information_schema.tables WHERE table_name = 'foreign_listings'")).isEqualTo(1);
        assertThat(count("asset_categories c JOIN asset_types t ON t.id = c.asset_type_id WHERE t.name = 'STOCK'")).isEqualTo(19);
        assertThat(count("asset_categories c JOIN asset_types t ON t.id = c.asset_type_id WHERE t.name = 'REIT'")).isEqualTo(12);
        assertThat(count("asset_categories c JOIN asset_types t ON t.id = c.asset_type_id WHERE t.name = 'ETF_INTERNACIONAL'")).isEqualTo(9);
        // V14 (PLANO_ATIVOS_INTERNACIONAIS, I5/C7): a prova da inativacao, e a conferencia descrita como quem inativa
        assertThat(em.createNativeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'ck_assets_delisting_source'").getSingleResult().toString()).contains("LIST_GONE");
        assertThat(count("task_types WHERE type = 'CATALOG_AUDIT' AND description LIKE '%Inativa o papel%'")).isEqualTo(1);
        // V13 (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, D3 e P2): ticker unico por (ticker, tipo, data de saida)
        assertThat(count("pg_constraint WHERE conname = 'uk_assets_ticker'")).isZero();
        assertThat(em.createNativeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'uk_assets_ticker_type'").getSingleResult().toString())
            .contains("NULLS NOT DISTINCT").contains("ticker, asset_type_id, delisted_on");
        assertThat(count("pg_constraint WHERE conname = 'ck_assets_delisted_inactive'")).isEqualTo(1);
        assertThat(count("goals WHERE params ->> 'ticker' IS NOT NULL AND params ->> 'assetId' IS NULL")).isZero();
        // V12 (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, F1): fonte CVM, tabelas do fundo, indices em copia apagados
        assertThat(count("asset_types WHERE name = 'FUNDO_INVESTIMENTO' AND price_source = 'CVM'")).isEqualTo(1);
        Number fundTables = (Number) em.createNativeQuery("""
            SELECT COUNT(*) FROM information_schema.tables
             WHERE table_name IN ('investment_funds', 'investment_fund_names', 'fund_tax_events')""").getSingleResult();
        assertThat(fundTables.intValue()).isEqualTo(3);
        assertThat(count("investment_funds")).isZero();
        assertThat(count("pg_indexes WHERE indexname IN ('idx_price_history_asset_date', 'idx_assets_ticker')")).isZero();
        assertThat(count("pg_indexes WHERE indexname = 'idx_investment_funds_search'")).isEqualTo(1);
        assertThat(em.createNativeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'ck_price_history_source'").getSingleResult().toString()).contains("CVM");
        // V11: euro cadastrado e inativo
        assertThat(count("currencies WHERE code = 'EUR' AND NOT active")).isEqualTo(1);

        // V9 (docs/planos/PLANO_CADASTRO_MOEDAS.md, M1): moedas, cambio, regras do exterior, operacoes de cambio
        Number currencyTables = (Number) em.createNativeQuery("""
            SELECT COUNT(*) FROM information_schema.tables
             WHERE table_name IN ('currencies', 'exchange_rates', 'foreign_tax_rules', 'fx_operations')""").getSingleResult();
        assertThat(currencyTables.intValue()).isEqualTo(4);
        assertThat(count("foreign_tax_rules")).isEqualTo(10);
        assertThat(count("pg_constraint WHERE conname = 'ck_ceiling_price_quarters_currency'")).isZero();
        // V10: IR brasileiro estimado do provento do exterior
        assertThat(count("information_schema.columns WHERE table_name = 'dividends' AND column_name = 'foreign_income_tax'"))
            .isEqualTo(1);

        // V8 (docs/planos/PLANO_METAS.md, M1): goals recriada, goal_events e goal_snapshots
        Number goalTables = (Number) em.createNativeQuery("""
            SELECT COUNT(*) FROM information_schema.tables
             WHERE table_name IN ('goals', 'goal_events', 'goal_snapshots')""").getSingleResult();
        assertThat(goalTables.intValue()).isEqualTo(3);
        Number oldColumns = (Number) em.createNativeQuery("""
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_name = 'goals' AND column_name IN ('target_amount', 'current_amount', 'status')""").getSingleResult();
        assertThat(oldColumns.intValue()).isZero();

        // V7 (docs/planos/PLANO_TELA_ATIVOS.md, 2.5): 10 colunas novas em company_quarters, nulas
        Number newColumns = (Number) em.createNativeQuery("""
            SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'company_quarters' AND is_nullable = 'YES'
              AND column_name IN ('gross_profit_q', 'gross_profit_ttm', 'depreciation_amortization_q',
                'depreciation_amortization_ttm', 'pretax_income_q', 'pretax_income_ttm', 'income_tax_q',
                'income_tax_ttm', 'current_assets', 'current_liabilities')""").getSingleResult();
        assertThat(newColumns.intValue()).isEqualTo(10);

        // Constraints com nome em ingles (PLANO_NOMES_EM_INGLES, E2), mantidas na consolidacao (PLANO_RENOMEACAO_ACTIVEST, 4.2)
        Number constraint = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM pg_constraint WHERE conname IN ('ck_user_avatars_content_type', 'ck_user_avatars_size', 'ck_users_cpf_complete')").getSingleResult();
        assertThat(constraint.intValue()).isEqualTo(3);

        Number failures = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success").getSingleResult();
        assertThat(failures.intValue()).isZero();
    }

    @Test
    @DisplayName("Tarefas (V61, V62; V8 do Activest: GOALS_SNAPSHOT; V9: EXCHANGE_RATES; V12: FUND_QUOTES; V16: FOREIGN_CATALOG; V17: FOREIGN_DELISTED): 31 tipos (27 da adm-api, 4 da app-api), uma tarefa por tipo, atributos espelhados")
    void seededTasks() {
        assertThat(count("task_types")).isEqualTo(31);
        assertThat(count("task_types WHERE service = 'APP_API'")).isEqualTo(4);
        assertThat(count("tasks")).isEqualTo(31);
        assertThat(count("tasks t JOIN task_types y ON y.id = t.task_type_id WHERE t.name <> y.type")).isZero();
        assertThat(count("tasks WHERE cron IS NOT NULL")).isEqualTo(20);
        assertThat(count("task_type_attributes")).isEqualTo(40);
        assertThat(count("task_attributes")).isEqualTo(40);
        assertThat(count("task_type_attributes WHERE position = 0")).isZero();
        assertThat(count("tasks WHERE run_on_startup")).isEqualTo(8);
        assertThat(count("task_attributes ta JOIN task_type_attributes a ON a.id = ta.task_type_attribute_id "
            + "WHERE ta.att_value IS DISTINCT FROM a.att_value")).isZero();
    }

    @Test
    @DisplayName("Benchmarks (V57): 6 indices e 2 ativos (IVVB11, BTC), so o CDI pre-selecionado; V58 aceita BINANCE")
    void benchmarks() {
        assertThat(count("benchmarks WHERE kind = 'INDICE'")).isEqualTo(6);
        assertThat(count("benchmarks WHERE kind = 'ATIVO'")).isEqualTo(2);
        assertThat(count("benchmarks WHERE default_selected")).isEqualTo(1);
        assertThat(count("benchmarks WHERE accepts_spread")).isEqualTo(1);
        assertThat(em.createNativeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'ck_price_history_source'").getSingleResult().toString()).contains("BINANCE");
    }

    @Test
    @DisplayName("Oferta do Tesouro (V59): tabela vazia, uma linha por titulo, apagada com o ativo")
    void treasuryOffer() {
        assertThat(count("treasury_offers")).isZero();
        assertThat(em.createNativeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'fk_treasury_offers_asset'").getSingleResult().toString()).contains("CASCADE");
    }

    @Test
    @DisplayName("Emissoras de renda fixa (V54): as duas linhas fixas e os tipos aceitos por produto")
    void fixedIncomeIssuers() {
        assertThat(count("fixed_income_issuers WHERE kind = 'TESOURO'")).isEqualTo(1);
        assertThat(count("fixed_income_issuers WHERE kind = 'NAO_LISTADA'")).isEqualTo(1);
        // 16 produtos de renda fixa com tipos aceitos; as 6 categorias do Tesouro aceitam so TESOURO
        assertThat(count("asset_categories WHERE issuer_kinds IS NOT NULL")).isEqualTo(22);
        assertThat(count("asset_categories WHERE issuer_kinds = 'TESOURO'")).isEqualTo(6);
        assertThat(count("asset_categories WHERE guaranteed")).isEqualTo(8);
        assertThat(em.createNativeQuery("SELECT issuer_kinds FROM asset_categories WHERE name = 'CDB'")
            .getSingleResult()).isEqualTo("BANCO");
    }

    @Test
    @DisplayName("Seed das emissoras de renda fixa (V55): empresas, grupos e a curadoria, sem rede")
    void issuersSeed() {
        // 6920 empresas do BCB e da CVM + as 2 linhas fixas da V54
        assertThat(count("fixed_income_issuers")).isEqualTo(6922);
        assertThat(count("fixed_income_groups")).isEqualTo(292);
        assertThat(count("fixed_income_groups WHERE name_source = 'MANUAL'")).isEqualTo(98);
        assertThat(count("fixed_income_groups WHERE extinct")).isEqualTo(2);
        assertThat(count("fixed_income_groups WHERE logo_ticker IS NOT NULL")).isEqualTo(17);
        // inativas ligadas ao grupo que tinham (planilha de curadoria, aba "Inativas — grupo antigo")
        assertThat(count("fixed_income_issuers WHERE group_source = 'MANUAL'")).isEqualTo(32);
        assertThat(em.createNativeQuery("SELECT g.name || '|' || i.sale_rule FROM fixed_income_issuers i "
            + "JOIN fixed_income_groups g ON g.id = i.group_id WHERE i.cnpj_root = '30680829'")
            .getSingleResult()).isEqualTo("Nubank|UEPS");
    }

    @Test
    @DisplayName("O catalogo semeado tem os numeros esperados de uma subida limpa")
    void seededCatalog() {
        assertThat(count("asset_types")).isEqualTo(12);
        // 165 linhas (name, segment) consolidadas em uma por (tipo, name) na V31:
        // ACAO 11, BDR 20, CRIPTO 14, ETF 9, FII 5, RENDA_FIXA 15 (+ RDC na V54), TESOURO_DIRETO 6
        // + 40 dos tipos internacionais (V15): STOCK 19, REIT 12, ETF_INTERNACIONAL 9
        assertThat(count("asset_categories")).isEqualTo(127);
        // V12: as 6 categorias dos fundos de investimento (1o nivel ANBIMA, D18)
        assertThat(count("asset_categories c JOIN asset_types t ON t.id = c.asset_type_id "
            + "WHERE t.name = 'FUNDO_INVESTIMENTO'")).isEqualTo(6);
        // nenhum ativo perdeu a categoria na consolidacao
        assertThat(count("assets WHERE asset_category_id IS NULL")).isZero();
        // 1264 + 23 novos da B3 (V15) + 219 ETFs (V17) + 892 BDRs (V18) + 227 criptos (V19) + RDC (V54)
        assertThat(count("assets")).isEqualTo(2626);
        assertThat(count("asset_terms")).isEqualTo(130);
        // Regras de IR retido nos proventos (V23)
        assertThat(count("withholding_tax_rules")).isEqualTo(7);
        // IR e IOF da renda fixa (V52): 4 faixas de IR, 30 de IOF, 14 excecoes por produto
        assertThat(count("fixed_income_tax_rules")).isEqualTo(57);
        // V12: 9 regras de fundo (IR e come-cotas por regime); as 48 da V52 ficam GENERAL
        assertThat(count("fixed_income_tax_rules WHERE regime = 'GENERAL'")).isEqualTo(48);
        assertThat(count("fixed_income_tax_rules WHERE tax = 'CC'")).isEqualTo(2);
        // Indices de mercado (V43, D1): 6 indexadores de renda fixa + meta Selic, PTAX, Ibovespa e IFIX; serie vazia
        assertThat(count("market_indices")).isEqualTo(10);
        assertThat(count("market_indices WHERE code IN ('CDI','SELIC','IPCA','IGPM','INPC','TR')")).isEqualTo(6);
        assertThat(count("market_index_values")).isZero();
    }

    @Test
    @DisplayName("Emissores semeados (V30): companhias, FIIs com segmento curado, ligacoes e units")
    void seededIssuers() {
        assertThat(count("issuers WHERE kind = 'COMPANY'")).isEqualTo(369);
        assertThat(count("issuers WHERE kind = 'FII'")).isEqualTo(346);
        assertThat(count("issuers WHERE kind = 'FII' AND segment IS NULL")).isZero();
        // 792 acoes (795 menos FSPE11F, FSRF11F e FSTU11F) + 347 FIIs
        assertThat(count("assets WHERE issuer_id IS NOT NULL")).isEqualTo(1139);
        assertThat(count("unit_compositions")).isEqualTo(36);

        Object[] petr4 = (Object[]) em.createNativeQuery("""
            SELECT i.cnpj, a.share_class, a.isin FROM assets a JOIN issuers i ON i.id = a.issuer_id
            WHERE a.ticker = 'PETR4'""").getSingleResult();
        assertThat(petr4).containsExactly("33000167000101", "PN", "BRPETRACNPR6");
        assertThat(em.createNativeQuery("""
            SELECT i.segment FROM assets a JOIN issuers i ON i.id = a.issuer_id WHERE a.ticker = 'HGLG11'""")
            .getSingleResult()).isEqualTo("Imóveis Logísticos");
    }

    @Test
    @DisplayName("Nao ha seed de posicao, transacao ou estrategia de tipo")
    void withoutMovementData() {
        assertThat(count("portfolio_assets")).isZero();
        assertThat(count("transactions")).isZero();
        assertThat(count("strategies")).isZero();
    }

    @Test
    @DisplayName("Integracoes (V25) nascem vazias: nenhuma conexao, credencial ou exclusao semeada")
    void emptyIntegrations() {
        assertThat(count("integration_connections")).isZero();
        assertThat(count("integration_credentials")).isZero();
        assertThat(count("integration_cursors")).isZero();
        assertThat(count("integration_runs")).isZero();
        assertThat(count("integration_run_items")).isZero();
        assertThat(count("integration_crypto_exclusions")).isZero();
    }
}
