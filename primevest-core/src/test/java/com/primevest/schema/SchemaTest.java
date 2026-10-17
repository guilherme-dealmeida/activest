package com.primevest.schema;

import com.primevest.support.JpaTestBase;
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
    @DisplayName("Flyway roda ate a V59 e o schema valida contra as entidades")
    void migrationsAplicadas() {
        Number versao = (Number) em.createNativeQuery(
            "SELECT MAX(CAST(version AS NUMERIC)) FROM flyway_schema_history WHERE success").getSingleResult();
        assertThat(versao.intValue()).isEqualTo(59);

        Number falhas = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success").getSingleResult();
        assertThat(falhas.intValue()).isZero();
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
    void ofertaDoTesouro() {
        assertThat(count("treasury_offers")).isZero();
        assertThat(em.createNativeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'fk_treasury_offers_asset'").getSingleResult().toString()).contains("CASCADE");
    }

    @Test
    @DisplayName("Emissoras de renda fixa (V54): as duas linhas fixas e os tipos aceitos por produto")
    void emissorasDeRendaFixa() {
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
    void seedDasEmissoras() {
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
    void catalogoSemeado() {
        assertThat(count("asset_types")).isEqualTo(12);
        // 165 linhas (name, segment) consolidadas em uma por (tipo, name) na V31:
        // ACAO 11, BDR 20, CRIPTO 14, ETF 9, FII 5, RENDA_FIXA 15 (+ RDC na V54), TESOURO_DIRETO 6
        assertThat(count("asset_categories")).isEqualTo(81);
        // nenhum ativo perdeu a categoria na consolidacao
        assertThat(count("assets WHERE asset_category_id IS NULL")).isZero();
        // 1264 + 23 novos da B3 (V15) + 219 ETFs (V17) + 892 BDRs (V18) + 227 criptos (V19) + RDC (V54)
        assertThat(count("assets")).isEqualTo(2626);
        assertThat(count("asset_terms")).isEqualTo(130);
        // Regras de IR retido nos proventos (V23)
        assertThat(count("withholding_tax_rules")).isEqualTo(7);
        // IR e IOF da renda fixa (V52): 4 faixas de IR, 30 de IOF, 14 excecoes por produto
        assertThat(count("fixed_income_tax_rules")).isEqualTo(48);
        // Indices de mercado (V43, D1): 6 indexadores de renda fixa + meta Selic, PTAX, Ibovespa e IFIX; serie vazia
        assertThat(count("market_indices")).isEqualTo(10);
        assertThat(count("market_indices WHERE code IN ('CDI','SELIC','IPCA','IGPM','INPC','TR')")).isEqualTo(6);
        assertThat(count("market_index_values")).isZero();
    }

    @Test
    @DisplayName("Emissores semeados (V30): companhias, FIIs com segmento curado, ligacoes e units")
    void emissoresSemeados() {
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
    void semDadosDeMovimento() {
        assertThat(count("portfolio_assets")).isZero();
        assertThat(count("transactions")).isZero();
        assertThat(count("strategies")).isZero();
    }

    @Test
    @DisplayName("Integracoes (V25) nascem vazias: nenhuma conexao, credencial ou exclusao semeada")
    void integracoesVazias() {
        assertThat(count("integration_connections")).isZero();
        assertThat(count("integration_credentials")).isZero();
        assertThat(count("integration_cursors")).isZero();
        assertThat(count("integration_runs")).isZero();
        assertThat(count("integration_run_items")).isZero();
        assertThat(count("integration_crypto_exclusions")).isZero();
    }
}
