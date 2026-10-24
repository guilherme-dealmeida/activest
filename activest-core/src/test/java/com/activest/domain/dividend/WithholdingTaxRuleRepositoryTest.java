package com.activest.domain.dividend;

import com.activest.domain.asset.AssetTypeRepository;
import com.activest.support.CatalogIds;
import com.activest.support.JpaTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras de IR no banco (V23, docs/planos/PLANO_IR_PROVENTOS.md, 4.1). Protege toda
 * migration futura de lei: nenhuma vigencia pode se sobrepor a outra.
 */
class WithholdingTaxRuleRepositoryTest extends JpaTestBase {

    @Autowired private WithholdingTaxRuleRepository repository;
    @Autowired private AssetTypeRepository assetTypeRepository;

    private Long typeValue(String name) {
        return CatalogIds.type(assetTypeRepository, name).getId();
    }

    private WithholdingTaxRule rule(DividendType typeValue, String assetEntity, LocalDate data) {
        List<WithholdingTaxRule> found = repository.findAll().stream()
            .filter(r -> r.getDividendType() == typeValue && r.getAssetTypeId().equals(typeValue(assetEntity)) && r.covers(data))
            .toList();
        assertThat(found).as("%s de %s em %s", typeValue, assetEntity, data).hasSize(1);
        return found.get(0);
    }

    @Test
    @DisplayName("A carga da V23 existe, linha a linha")
    void initialLoad() {
        assertThat(repository.count()).isEqualTo(7);

        WithholdingTaxRule jcp2025 = rule(DividendType.JCP, "ACAO", LocalDate.of(2025, 12, 31));
        assertThat(jcp2025.getRate()).isEqualByComparingTo("0.15");
        assertThat(jcp2025.getBaseDate()).isEqualTo(TaxBaseDate.CREDIT);
        assertThat(jcp2025.getValidTo()).isEqualTo(LocalDate.of(2025, 12, 31));

        WithholdingTaxRule jcp2026 = rule(DividendType.JCP, "ACAO", LocalDate.of(2026, 1, 1));
        assertThat(jcp2026.getRate()).isEqualByComparingTo("0.175");
        assertThat(jcp2026.getValidFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(jcp2026.getValidTo()).isNull();
        assertThat(jcp2026.getLegalBasis()).contains("LC 224/2025");

        LocalDate today = LocalDate.of(2026, 9, 24);
        assertThat(rule(DividendType.DIVIDENDO, "ACAO", today).getRate()).isEqualByComparingTo("0");
        assertThat(rule(DividendType.RENDIMENTO, "ACAO", today).getRate()).isEqualByComparingTo("0.225");
        assertThat(rule(DividendType.RESTITUICAO_CAPITAL, "ACAO", today).getRate()).isEqualByComparingTo("0");
        assertThat(rule(DividendType.RENDIMENTO, "FII", today).getRate()).isEqualByComparingTo("0");
        assertThat(rule(DividendType.AMORTIZACAO, "FII", today).getRate()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Nenhuma vigencia se sobrepoe a outra do mesmo tipo de provento e de ativo")
    void withoutOverlap() {
        List<WithholdingTaxRule> rules = repository.findAll();
        for (WithholdingTaxRule a : rules) {
            for (WithholdingTaxRule b : rules) {
                if (a.getId().equals(b.getId()) || a.getDividendType() != b.getDividendType()
                        || !a.getAssetTypeId().equals(b.getAssetTypeId())) {
                    continue;
                }
                LocalDate startA = a.getValidFrom() != null ? a.getValidFrom() : LocalDate.MIN;
                LocalDate endA = a.getValidTo() != null ? a.getValidTo() : LocalDate.MAX;
                LocalDate startB = b.getValidFrom() != null ? b.getValidFrom() : LocalDate.MIN;
                LocalDate endB = b.getValidTo() != null ? b.getValidTo() : LocalDate.MAX;
                boolean sobrepoe = !startA.isAfter(endB) && !startB.isAfter(endA);
                assertThat(sobrepoe).as("regras %d e %d (%s) se sobrepoem", a.getId(), b.getId(), a.getDividendType())
                    .isFalse();
            }
        }
    }
}
