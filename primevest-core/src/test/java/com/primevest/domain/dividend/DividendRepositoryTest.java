package com.primevest.domain.dividend;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.support.CatalogIds;
import com.primevest.support.Fixtures;
import com.primevest.support.JpaTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Somas da carteira em liquido (docs/planos/PLANO_IR_PROVENTOS.md, D1) e so do que ja foi
 * recebido; a fila do verificador de IR (4.5).
 */
class DividendRepositoryTest extends JpaTestBase {

    @Autowired private DividendRepository repository;
    @Autowired private WithholdingTaxRuleRepository ruleRepository;
    @Autowired private PortfolioAssetRepository portfolioAssetRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetCashEventRepository cashEventRepository;

    private Portfolio portfolioEntity;
    private PortfolioAsset positionEntity;
    private Asset stock;

    @BeforeEach
    void scenario() {
        User owner = userRepository.save(Fixtures.user("ir@primevest.test"));
        portfolioEntity = portfolioRepository.save(Fixtures.portfolio(owner));
        stock = CatalogIds.asset(assetRepository, CatalogIds.STOCK);
        positionEntity = portfolioAssetRepository.save(Fixtures.position(portfolioEntity, stock));
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    private Dividend dividendEvent(String gross, String ir, String net, boolean reconciled) {
        return dividendEvent(gross, ir, net, reconciled, "2026-09-10", CashEventSource.B3);
    }

    private Dividend dividendEvent(String gross, String ir, String net, boolean reconciled, String payment,
                              CashEventSource sourceName) {
        WithholdingTaxRule rule = ir == null ? null : ruleRepository.findAll().stream()
            .filter(r -> r.getDividendType() == DividendType.JCP && r.getValidFrom() != null).findFirst().orElseThrow();
        // AUTO sempre tem o evento do catalogo que o gerou (ck_dividends_auto_has_event).
        AssetCashEvent event = cashEventRepository.save(AssetCashEvent.builder().asset(stock).type(DividendType.JCP)
            .label("JRS CAP PROPRIO").dataCom(LocalDate.of(2026, 6, 1)).valuePerShare(new BigDecimal(gross))
            .source(sourceName).build());
        return repository.save(Dividend.builder().portfolioAsset(positionEntity).origin(DividendOrigin.AUTO).cashEvent(event)
            .type(DividendType.JCP).amountPerShare(new BigDecimal("0.1")).exDate(LocalDate.of(2026, 6, 1))
            .paymentDate(payment == null ? null : LocalDate.parse(payment))
            .totalAmount(new BigDecimal(gross))
            .withholdingRule(rule)
            .withholdingRate(rule != null ? rule.getRate() : null)
            .withholdingTax(ir == null ? null : new BigDecimal(ir))
            .netAmountPerShare(net == null ? null : new BigDecimal("0.08250000"))
            .netAmount(net == null ? null : new BigDecimal(net))
            .taxCheckedAt(reconciled ? LocalDateTime.now() : null)
            .build());
    }

    @Test
    @DisplayName("Soma por posicao usa o liquido; tipo sem regra entra pelo bruto")
    void netSum() {
        dividendEvent("10.19", "1.78", "8.41", true);
        dividendEvent("7.98", null, null, true);

        List<PositionDividendTotal> totals = repository.sumTotalAmountByPosition(portfolioEntity.getId(), TODAY);

        assertThat(totals).singleElement().satisfies(t -> assertThat(t.total()).isEqualByComparingTo("16.39"));
        assertThat(repository.sumTotalDividendsByPortfolioId(portfolioEntity.getId(), TODAY)).isEqualByComparingTo("16.39");
    }

    @Test
    @DisplayName("Somas so do recebido — e a regra do banco bate com Dividend.isReceived, caso a caso")
    void sumOnlyReceived() {
        List<Dividend> cases = List.of(
            dividendEvent("1.00", null, null, true, "2026-09-23", CashEventSource.B3),            // ontem
            dividendEvent("2.00", null, null, true, "2026-09-24", CashEventSource.B3),            // hoje (D0)
            dividendEvent("4.00", null, null, true, "2026-09-25", CashEventSource.B3),            // amanha
            dividendEvent("8.00", null, null, true, null, CashEventSource.B3_HISTORICO),          // sem data, historico
            dividendEvent("16.00", null, null, true, null, CashEventSource.B3),                   // sem data, a definir
            dividendEvent("32.00", null, null, true, "2027-01-22", CashEventSource.FNET));        // futuro
        BigDecimal expected = cases.stream().filter(d -> d.isReceived(TODAY))
            .map(Dividend::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(expected).isEqualByComparingTo("11.00");

        assertThat(repository.sumTotalAmountByPosition(portfolioEntity.getId(), TODAY))
            .singleElement().satisfies(t -> assertThat(t.total()).isEqualByComparingTo(expected));
        assertThat(repository.sumTotalDividendsByPortfolioId(portfolioEntity.getId(), TODAY)).isEqualByComparingTo(expected);
        // Um dia depois, o de amanha entra.
        assertThat(repository.sumTotalDividendsByPortfolioId(portfolioEntity.getId(), TODAY.plusDays(1)))
            .isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("Fila do verificador: so posicoes com provento AUTO sem IR calculado")
    void verifierQueue() {
        dividendEvent("10.19", "1.78", "8.41", true);
        assertThat(repository.findPositionIdsWithTaxPending()).isEmpty();

        dividendEvent("5.00", null, null, false);
        assertThat(repository.findPositionIdsWithTaxPending()).containsExactly(positionEntity.getId());
    }
}
