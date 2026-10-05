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
    @DisplayName("Flyway roda ate a V42 e o schema valida contra as entidades")
    void migrationsAplicadas() {
        Number versao = (Number) em.createNativeQuery(
            "SELECT MAX(CAST(version AS NUMERIC)) FROM flyway_schema_history WHERE success").getSingleResult();
        assertThat(versao.intValue()).isEqualTo(42);

        Number falhas = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success").getSingleResult();
        assertThat(falhas.intValue()).isZero();
    }

    @Test
    @DisplayName("O catalogo semeado tem os numeros esperados de uma subida limpa")
    void catalogoSemeado() {
        assertThat(count("asset_types")).isEqualTo(12);
        // 165 linhas (name, segment) consolidadas em uma por (tipo, name) na V31:
        // ACAO 11, BDR 20, CRIPTO 14, ETF 9, FII 5, RENDA_FIXA 15, TESOURO_DIRETO 6
        assertThat(count("asset_categories")).isEqualTo(80);
        // nenhum ativo perdeu a categoria na consolidacao
        assertThat(count("assets WHERE asset_category_id IS NULL")).isZero();
        // 1264 + 23 novos da B3 (V15) + 219 ETFs (V17) + 892 BDRs (V18) + 227 criptos (V19)
        assertThat(count("assets")).isEqualTo(2625);
        assertThat(count("asset_terms")).isEqualTo(130);
        // Regras de IR retido nos proventos (V23)
        assertThat(count("withholding_tax_rules")).isEqualTo(7);
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
