package com.primevest.domain.dividend;

import com.primevest.domain.asset.AssetTypeRepository;
import com.primevest.support.CatalogIds;
import com.primevest.support.JpaTestBase;
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

    private Long tipo(String nome) {
        return CatalogIds.type(assetTypeRepository, nome).getId();
    }

    private WithholdingTaxRule regra(DividendType tipo, String ativo, LocalDate data) {
        List<WithholdingTaxRule> achadas = repository.findAll().stream()
            .filter(r -> r.getDividendType() == tipo && r.getAssetTypeId().equals(tipo(ativo)) && r.covers(data))
            .toList();
        assertThat(achadas).as("%s de %s em %s", tipo, ativo, data).hasSize(1);
        return achadas.get(0);
    }

    @Test
    @DisplayName("A carga da V23 existe, linha a linha")
    void cargaInicial() {
        assertThat(repository.count()).isEqualTo(7);

        WithholdingTaxRule jcp2025 = regra(DividendType.JCP, "ACAO", LocalDate.of(2025, 12, 31));
        assertThat(jcp2025.getRate()).isEqualByComparingTo("0.15");
        assertThat(jcp2025.getBaseDate()).isEqualTo(TaxBaseDate.CREDIT);
        assertThat(jcp2025.getValidTo()).isEqualTo(LocalDate.of(2025, 12, 31));

        WithholdingTaxRule jcp2026 = regra(DividendType.JCP, "ACAO", LocalDate.of(2026, 1, 1));
        assertThat(jcp2026.getRate()).isEqualByComparingTo("0.175");
        assertThat(jcp2026.getValidFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(jcp2026.getValidTo()).isNull();
        assertThat(jcp2026.getLegalBasis()).contains("LC 224/2025");

        LocalDate hoje = LocalDate.of(2026, 9, 24);
        assertThat(regra(DividendType.DIVIDENDO, "ACAO", hoje).getRate()).isEqualByComparingTo("0");
        assertThat(regra(DividendType.RENDIMENTO, "ACAO", hoje).getRate()).isEqualByComparingTo("0.225");
        assertThat(regra(DividendType.RESTITUICAO_CAPITAL, "ACAO", hoje).getRate()).isEqualByComparingTo("0");
        assertThat(regra(DividendType.RENDIMENTO, "FII", hoje).getRate()).isEqualByComparingTo("0");
        assertThat(regra(DividendType.AMORTIZACAO, "FII", hoje).getRate()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Nenhuma vigencia se sobrepoe a outra do mesmo tipo de provento e de ativo")
    void semSobreposicao() {
        List<WithholdingTaxRule> regras = repository.findAll();
        for (WithholdingTaxRule a : regras) {
            for (WithholdingTaxRule b : regras) {
                if (a.getId().equals(b.getId()) || a.getDividendType() != b.getDividendType()
                        || !a.getAssetTypeId().equals(b.getAssetTypeId())) {
                    continue;
                }
                LocalDate inicioA = a.getValidFrom() != null ? a.getValidFrom() : LocalDate.MIN;
                LocalDate fimA = a.getValidTo() != null ? a.getValidTo() : LocalDate.MAX;
                LocalDate inicioB = b.getValidFrom() != null ? b.getValidFrom() : LocalDate.MIN;
                LocalDate fimB = b.getValidTo() != null ? b.getValidTo() : LocalDate.MAX;
                boolean sobrepoe = !inicioA.isAfter(fimB) && !inicioB.isAfter(fimA);
                assertThat(sobrepoe).as("regras %d e %d (%s) se sobrepoem", a.getId(), b.getId(), a.getDividendType())
                    .isFalse();
            }
        }
    }
}
