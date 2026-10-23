package com.primevest.domain.marketindex;

import com.primevest.support.JpaTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V43: cadastro semeado e consultas da serie (docs/planos/PLANO_INDICES_MERCADO.md, X1). */
class MarketIndexRepositoryTest extends JpaTestBase {

    @Autowired private MarketIndexRepository indices;
    @Autowired private MarketIndexValueRepository values;
    @Autowired private JdbcTemplate jdbc;

    private void insert(long indexId, LocalDate d, String v) {
        jdbc.update("INSERT INTO market_index_values (index_id, reference_date, value) VALUES (?, ?, ?)",
                indexId, Date.valueOf(d), new java.math.BigDecimal(v));
    }

    @Test
    @DisplayName("Os seis indexadores da renda fixa existem com o mesmo nome do enum Indexer")
    void enumIndexers() {
        for (com.primevest.domain.asset.Indexer i : com.primevest.domain.asset.Indexer.values()) {
            assertThat(indices.findByCode(i.name())).as(i.name()).isPresent();
        }
        MarketIndex cdi = indices.findByCode("CDI").orElseThrow();
        assertThat(cdi.getSource()).isEqualTo(IndexSource.BCB_SGS);
        assertThat(cdi.getSourceRef()).isEqualTo("12");
        assertThat(cdi.getUnit()).isEqualTo(IndexUnit.PCT_DIA);
        assertThat(cdi.getPeriodicity()).isEqualTo(IndexPeriodicity.DIARIA);
        assertThat(indices.findByCode("IPCA").orElseThrow().getPeriodicity()).isEqualTo(IndexPeriodicity.MENSAL);
        assertThat(indices.findByCode("IBOV").orElseThrow().getSource()).isEqualTo(IndexSource.B3);
        assertThat(indices.findByActiveTrueOrderByIdAsc()).hasSize(10);
    }

    @Test
    @DisplayName("Serie: intervalo ordenado, ultimo ate a data, primeiro e ultimo")
    void queries() {
        long cdi = indices.findByCode("CDI").orElseThrow().getId();
        insert(cdi, LocalDate.of(2024, 1, 3), "0.043739");
        insert(cdi, LocalDate.of(2024, 1, 2), "0.043739");
        insert(cdi, LocalDate.of(2024, 1, 5), "0.043739");

        assertThat(values.findByIndexIdAndReferenceDateBetweenOrderByReferenceDate(cdi,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 4)))
                .extracting(MarketIndexValue::getReferenceDate)
                .containsExactly(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3));
        assertThat(values.findFirstByIndexIdAndReferenceDateLessThanEqualOrderByReferenceDateDesc(cdi,
                LocalDate.of(2024, 1, 4)).orElseThrow().getReferenceDate()).isEqualTo(LocalDate.of(2024, 1, 3));
        assertThat(values.findFirstByIndexIdOrderByReferenceDateDesc(cdi).orElseThrow().getReferenceDate())
                .isEqualTo(LocalDate.of(2024, 1, 5));
        assertThat(values.findFirstByIndexIdOrderByReferenceDateAsc(cdi).orElseThrow().getValue())
                .isEqualByComparingTo("0.043739");
        assertThat(values.countByIndexId(cdi)).isEqualTo(3);
    }

    @Test
    @DisplayName("Um valor por indice por data: a mesma data duas vezes viola a chave")
    void key() {
        long ipca = indices.findByCode("IPCA").orElseThrow().getId();
        insert(ipca, LocalDate.of(2024, 1, 1), "0.42");

        assertThatThrownBy(() -> insert(ipca, LocalDate.of(2024, 1, 1), "0.43"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
