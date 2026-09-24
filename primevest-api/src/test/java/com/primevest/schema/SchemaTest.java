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
    @DisplayName("Flyway roda ate a V22 e o schema valida contra as entidades")
    void migrationsAplicadas() {
        Number versao = (Number) em.createNativeQuery(
            "SELECT MAX(CAST(version AS NUMERIC)) FROM flyway_schema_history WHERE success").getSingleResult();
        assertThat(versao.intValue()).isEqualTo(22);

        Number falhas = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success").getSingleResult();
        assertThat(falhas.intValue()).isZero();
    }

    @Test
    @DisplayName("O catalogo semeado tem os numeros esperados de uma subida limpa")
    void catalogoSemeado() {
        assertThat(count("asset_types")).isEqualTo(12);
        // 116 + 12 de ETF (V16) + 22 de BDR (V18) + 15 de cripto (V19)
        assertThat(count("asset_categories")).isEqualTo(165);
        // 1264 + 23 novos da B3 (V15) + 219 ETFs (V17) + 892 BDRs (V18) + 227 criptos (V19)
        assertThat(count("assets")).isEqualTo(2625);
        assertThat(count("asset_terms")).isEqualTo(130);
    }

    @Test
    @DisplayName("Nao ha seed de posicao, transacao ou estrategia de tipo")
    void semDadosDeMovimento() {
        assertThat(count("portfolio_assets")).isZero();
        assertThat(count("transactions")).isZero();
        assertThat(count("strategies")).isZero();
    }
}
