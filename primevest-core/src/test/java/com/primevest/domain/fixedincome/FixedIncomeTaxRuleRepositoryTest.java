package com.primevest.domain.fixedincome;

import com.primevest.domain.asset.AssetRepository;
import com.primevest.support.CatalogIds;
import com.primevest.support.JpaTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras de IR e IOF da renda fixa no banco (V52, docs/planos/PLANO_VENDA_RENDA_FIXA.md, secao 3.1). Confere a
 * carga, as faixas nos limites pelo {@link FixedIncomeTaxTable} e protege toda migration futura de lei: nenhuma
 * faixa se sobrepoe a outra do mesmo imposto e produto na mesma vigencia.
 */
class FixedIncomeTaxRuleRepositoryTest extends JpaTestBase {

    private static final LocalDate INVESTMENT = LocalDate.of(2026, 1, 1);

    @Autowired private FixedIncomeTaxRuleRepository repository;
    @Autowired private AssetRepository assetRepository;

    private FixedIncomeTaxTable table() {
        return new FixedIncomeTaxTable(repository.findAll());
    }

    private Long product(String ticker) {
        return CatalogIds.asset(assetRepository, ticker).getId();
    }

    private FixedIncomeTaxTable.Rates aos(long days, String ticker) {
        return table().rates(ticker == null ? null : product(ticker), INVESTMENT, INVESTMENT.plusDays(days));
    }

    @Test
    @DisplayName("Carga da V52: 4 faixas de IR, 30 de IOF e 14 excecoes por produto")
    void load() {
        List<FixedIncomeTaxRule> rules = repository.findAll();
        assertThat(rules).hasSize(4 + 30 + 14);
        assertThat(rules).allSatisfy(r -> assertThat(r.getLegalBasis()).isNotBlank());
    }

    @Test
    @DisplayName("IR regressivo nos limites das faixas: 180/181, 360/361, 720/721")
    void irAtBoundaries() {
        assertThat(aos(0, "CDB").ir()).isEqualByComparingTo("0.225");
        assertThat(aos(180, "CDB").ir()).isEqualByComparingTo("0.225");
        assertThat(aos(181, "CDB").ir()).isEqualByComparingTo("0.20");
        assertThat(aos(360, "CDB").ir()).isEqualByComparingTo("0.20");
        assertThat(aos(361, "CDB").ir()).isEqualByComparingTo("0.175");
        assertThat(aos(720, "CDB").ir()).isEqualByComparingTo("0.175");
        assertThat(aos(721, "CDB").ir()).isEqualByComparingTo("0.15");
        assertThat(aos(5000, "CDB").ir()).isEqualByComparingTo("0.15");
    }

    @Test
    @DisplayName("IOF regressivo: dia 0 e 1 a 96%, dia 17 a 43%, dia 29 a 3%, dia 30 em diante zero")
    void iofAtBoundaries() {
        assertThat(aos(0, "CDB").iof()).isEqualByComparingTo("0.96");
        assertThat(aos(1, "CDB").iof()).isEqualByComparingTo("0.96");
        assertThat(aos(2, "CDB").iof()).isEqualByComparingTo("0.93");
        assertThat(aos(17, "CDB").iof()).isEqualByComparingTo("0.43");
        assertThat(aos(29, "CDB").iof()).isEqualByComparingTo("0.03");
        assertThat(aos(30, "CDB").iof()).isEqualByComparingTo("0");
        assertThat(aos(400, "CDB").iof()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Isencoes de IR: LCI, LCA, CRI, CRA, debenture incentivada, LIG e poupanca")
    void taxExemptions() {
        for (String ticker : List.of("LCI", "LCA", "CRI", "CRA", "DEBENTURE_INCENTIVADA", "LIG", "POUPANCA")) {
            assertThat(aos(10, ticker).ir()).as(ticker).isEqualByComparingTo("0");
            assertThat(aos(800, ticker).ir()).as(ticker).isEqualByComparingTo("0");
        }
        assertThat(aos(10, "DEBENTURE").ir()).isEqualByComparingTo("0.225");
        assertThat(aos(10, "LF").ir()).isEqualByComparingTo("0.225");
    }

    @Test
    @DisplayName("IOF zero: LCA, CRA, CRI, debentures, LF e poupanca; LCI e LIG pagam IOF antes de 30 dias")
    void iofZero() {
        for (String ticker : List.of("LCA", "CRA", "CRI", "DEBENTURE", "DEBENTURE_INCENTIVADA", "LF", "POUPANCA")) {
            assertThat(aos(5, ticker).iof()).as(ticker).isEqualByComparingTo("0");
        }
        assertThat(aos(5, "LCI").iof()).isEqualByComparingTo("0.83");
        assertThat(aos(5, "LIG").iof()).isEqualByComparingTo("0.83");
        assertThat(aos(5, null).iof()).as("Tesouro e demais: regra geral").isEqualByComparingTo("0.83");
    }

    @Test
    @DisplayName("Nenhuma faixa se sobrepoe a outra do mesmo imposto e produto na mesma vigencia")
    void withoutOverlap() {
        List<FixedIncomeTaxRule> rules = repository.findAll();
        for (FixedIncomeTaxRule a : rules) {
            for (FixedIncomeTaxRule b : rules) {
                if (a.getId().equals(b.getId()) || a.getTax() != b.getTax()
                        || !Objects.equals(a.getAssetId(), b.getAssetId())) {
                    continue;
                }
                LocalDate startA = a.getValidFrom() != null ? a.getValidFrom() : LocalDate.MIN;
                LocalDate endA = a.getValidTo() != null ? a.getValidTo() : LocalDate.MAX;
                LocalDate startB = b.getValidFrom() != null ? b.getValidFrom() : LocalDate.MIN;
                LocalDate endB = b.getValidTo() != null ? b.getValidTo() : LocalDate.MAX;
                boolean validitiesTouch = !startA.isAfter(endB) && !startB.isAfter(endA);
                long endDaysA = a.getDaysTo() != null ? a.getDaysTo() : Long.MAX_VALUE;
                long endDaysB = b.getDaysTo() != null ? b.getDaysTo() : Long.MAX_VALUE;
                boolean bandsTouch = a.getDaysFrom() <= endDaysB && b.getDaysFrom() <= endDaysA;
                assertThat(validitiesTouch && bandsTouch)
                    .as("regras %d e %d (%s) se sobrepoem", a.getId(), b.getId(), a.getTax()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("As faixas gerais cobrem todo prazo, sem buraco")
    void withoutGap() {
        FixedIncomeTaxTable t = table();
        for (long days = 0; days <= 800; days++) {
            BigDecimal ir = t.rate(FixedIncomeTax.IR, null, days, INVESTMENT);
            assertThat(ir.signum()).as("IR no dia %d", days).isPositive();
        }
    }
}
