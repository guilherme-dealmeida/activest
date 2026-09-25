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
 * Somas da carteira em liquido (PLANO_IR_PROVENTOS.md, D1) e so do que ja foi
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

    private Portfolio carteira;
    private PortfolioAsset posicao;
    private Asset acao;

    @BeforeEach
    void cenario() {
        User dono = userRepository.save(Fixtures.user("ir@primevest.test"));
        carteira = portfolioRepository.save(Fixtures.portfolio(dono));
        acao = CatalogIds.asset(assetRepository, CatalogIds.ACAO);
        posicao = portfolioAssetRepository.save(Fixtures.position(carteira, acao));
    }

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 24);

    private Dividend provento(String bruto, String ir, String liquido, boolean conferido) {
        return provento(bruto, ir, liquido, conferido, "2026-09-10", CashEventSource.B3);
    }

    private Dividend provento(String bruto, String ir, String liquido, boolean conferido, String pagamento,
                              CashEventSource fonte) {
        WithholdingTaxRule regra = ir == null ? null : ruleRepository.findAll().stream()
            .filter(r -> r.getDividendType() == DividendType.JCP && r.getValidFrom() != null).findFirst().orElseThrow();
        // AUTO sempre tem o evento do catalogo que o gerou (ck_dividends_auto_has_event).
        AssetCashEvent evento = cashEventRepository.save(AssetCashEvent.builder().asset(acao).type(DividendType.JCP)
            .label("JRS CAP PROPRIO").dataCom(LocalDate.of(2026, 6, 1)).valuePerShare(new BigDecimal(bruto))
            .source(fonte).build());
        return repository.save(Dividend.builder().portfolioAsset(posicao).origin(DividendOrigin.AUTO).cashEvent(evento)
            .type(DividendType.JCP).amountPerShare(new BigDecimal("0.1")).exDate(LocalDate.of(2026, 6, 1))
            .paymentDate(pagamento == null ? null : LocalDate.parse(pagamento))
            .totalAmount(new BigDecimal(bruto))
            .withholdingRule(regra)
            .withholdingRate(regra != null ? regra.getRate() : null)
            .withholdingTax(ir == null ? null : new BigDecimal(ir))
            .netAmountPerShare(liquido == null ? null : new BigDecimal("0.08250000"))
            .netAmount(liquido == null ? null : new BigDecimal(liquido))
            .taxCheckedAt(conferido ? LocalDateTime.now() : null)
            .build());
    }

    @Test
    @DisplayName("Soma por posicao usa o liquido; tipo sem regra entra pelo bruto")
    void somaLiquida() {
        provento("10.19", "1.78", "8.41", true);
        provento("7.98", null, null, true);

        List<PositionDividendTotal> totais = repository.sumTotalAmountByPosition(carteira.getId(), HOJE);

        assertThat(totais).singleElement().satisfies(t -> assertThat(t.total()).isEqualByComparingTo("16.39"));
        assertThat(repository.sumTotalDividendsByPortfolioId(carteira.getId(), HOJE)).isEqualByComparingTo("16.39");
    }

    @Test
    @DisplayName("Somas so do recebido — e a regra do banco bate com Dividend.isReceived, caso a caso")
    void somaSoDoRecebido() {
        List<Dividend> casos = List.of(
            provento("1.00", null, null, true, "2026-09-23", CashEventSource.B3),            // ontem
            provento("2.00", null, null, true, "2026-09-24", CashEventSource.B3),            // hoje (D0)
            provento("4.00", null, null, true, "2026-09-25", CashEventSource.B3),            // amanha
            provento("8.00", null, null, true, null, CashEventSource.B3_HISTORICO),          // sem data, historico
            provento("16.00", null, null, true, null, CashEventSource.B3),                   // sem data, a definir
            provento("32.00", null, null, true, "2027-01-22", CashEventSource.FNET));        // futuro
        BigDecimal esperado = casos.stream().filter(d -> d.isReceived(HOJE))
            .map(Dividend::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(esperado).isEqualByComparingTo("11.00");

        assertThat(repository.sumTotalAmountByPosition(carteira.getId(), HOJE))
            .singleElement().satisfies(t -> assertThat(t.total()).isEqualByComparingTo(esperado));
        assertThat(repository.sumTotalDividendsByPortfolioId(carteira.getId(), HOJE)).isEqualByComparingTo(esperado);
        // Um dia depois, o de amanha entra.
        assertThat(repository.sumTotalDividendsByPortfolioId(carteira.getId(), HOJE.plusDays(1)))
            .isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("Fila do verificador: so posicoes com provento AUTO sem IR calculado")
    void filaDoVerificador() {
        provento("10.19", "1.78", "8.41", true);
        assertThat(repository.findPositionIdsWithTaxPending()).isEmpty();

        provento("5.00", null, null, false);
        assertThat(repository.findPositionIdsWithTaxPending()).containsExactly(posicao.getId());
    }
}
