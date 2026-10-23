package com.primevest.domain.dividend;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.corporateaction.AssetStockEvent;
import com.primevest.domain.corporateaction.AssetStockEventRepository;
import com.primevest.domain.corporateaction.StockEventType;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PositionCalculator;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proventos AUTO a partir dos eventos do catalogo (docs/planos/PLANO_BRAPI.md, F5): valor
 * por cota x cotas na data-com, com os desdobramentos no meio — e o IR retido
 * calculado junto (docs/planos/PLANO_IR_PROVENTOS.md, 4.4).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DividendRecalculatorTest {

    @Mock private DividendRepository dividendRepository;
    @Mock private AssetCashEventRepository cashEventRepository;
    @Mock private AssetStockEventRepository stockEventRepository;
    @Mock private WithholdingTaxRuleRepository ruleRepository;

    private static final long STOCK = 1L;
    private static final WithholdingTaxRule JCP_2025 = rule(1L, DividendType.JCP, "0.1500", null, "2025-12-31");
    private static final WithholdingTaxRule JCP_2026 = rule(2L, DividendType.JCP, "0.1750", "2026-01-01", null);
    private static final WithholdingTaxRule EXEMPT_DIVIDEND = rule(3L, DividendType.DIVIDENDO, "0", null, null);

    private DividendRecalculator recalculator;
    private PortfolioAsset position;
    private Asset petr4;

    @BeforeEach
    void scenario() {
        PositionCalculator calculator = new PositionCalculator(null, stockEventRepository, dividendRepository);
        recalculator = new DividendRecalculator(calculator, dividendRepository, cashEventRepository, ruleRepository);
        petr4 = Asset.builder().id(1L).ticker("PETR4").name("PETROBRAS")
            .assetType(AssetType.builder().id(STOCK).name("ACAO").build()).build();
        position = PortfolioAsset.builder().asset(petr4).build();
        position.setId(UUID.randomUUID());
        when(dividendRepository.findAutoByPortfolioAssetId(any())).thenReturn(List.of());
        when(stockEventRepository.findByAssetIdOrderByDataComAsc(anyLong())).thenReturn(List.of());
        when(ruleRepository.findAll()).thenReturn(List.of(JCP_2025, JCP_2026, EXEMPT_DIVIDEND));
    }

    private static WithholdingTaxRule rule(long id, DividendType typeValue, String aliquota, String de, String ate) {
        return WithholdingTaxRule.builder().id(id).dividendType(typeValue).assetTypeId(STOCK)
            .rate(new BigDecimal(aliquota)).baseDate(TaxBaseDate.CREDIT)
            .validFrom(de == null ? null : LocalDate.parse(de)).validTo(ate == null ? null : LocalDate.parse(ate))
            .legalBasis("teste").build();
    }

    private Dividend saved() {
        ArgumentCaptor<Dividend> saved = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(saved.capture());
        return saved.getValue();
    }

    private static Transaction buy(String data, String shareCount) {
        return Transaction.builder().type(TransactionType.BUY).shares(new BigDecimal(shareCount))
            .pricePerShare(new BigDecimal("30")).transactionDate(LocalDate.parse(data))
            .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
    }

    private AssetCashEvent event(long id, DividendType typeValue, String dataCom, String value) {
        return AssetCashEvent.builder().id(id).asset(petr4).type(typeValue).label(typeValue == null ? "XPTO" : typeValue.name())
            .dataCom(LocalDate.parse(dataCom)).paymentDate(LocalDate.parse(dataCom).plusMonths(2))
            .valuePerShare(new BigDecimal(value)).source(CashEventSource.B3).build();
    }

    @Test
    @DisplayName("Evento com cotas na data-com gera provento AUTO com cotas, total bruto truncado e referencia ao evento")
    void generatesAutoDividend() {
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L))
            .thenReturn(List.of(event(10L, DividendType.DIVIDENDO, "2026-08-21", "0.47156696000")));

        int created = recalculator.recalculate(position, List.of(buy("2026-08-01", "100")));

        ArgumentCaptor<Dividend> saved = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(saved.capture());
        Dividend d = saved.getValue();
        assertThat(created).isEqualTo(1);
        assertThat(d.getOrigin()).isEqualTo(DividendOrigin.AUTO);
        assertThat(d.getCashEvent().getId()).isEqualTo(10L);
        assertThat(d.getSharesOnDate()).isEqualByComparingTo("100");
        // 0,47156696 x 100 = 47,156696: truncado como a B3 (antes, arredondado para 47,16).
        assertThat(d.getTotalAmount()).isEqualByComparingTo("47.15");
        assertThat(d.getExDate()).isEqualTo(LocalDate.parse("2026-08-21"));
    }

    @Test
    @DisplayName("Compra depois da data-com nao tem direito: nada e criado")
    void buyAfterDataCom() {
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L))
            .thenReturn(List.of(event(10L, DividendType.JCP, "2026-08-21", "0.67")));

        recalculator.recalculate(position, List.of(buy("2026-08-22", "100")));

        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("Provento pago depois de um desdobramento usa as cotas novas; antes, as antigas")
    void dividendBeforeAndAfterSplit() {
        when(stockEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(AssetStockEvent.builder()
            .asset(petr4).type(StockEventType.DESDOBRAMENTO).dataCom(LocalDate.parse("2026-04-15"))
            .factor(new BigDecimal("100")).source("B3").build()));
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(
            event(1L, DividendType.DIVIDENDO, "2026-03-31", "1.00"),
            event(2L, DividendType.DIVIDENDO, "2026-06-30", "1.00")));

        recalculator.recalculate(position, List.of(buy("2026-03-02", "100")));

        ArgumentCaptor<Dividend> saved = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Dividend::getTotalAmount)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("100.00"), new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("Rotulo desconhecido (tipo nulo) nao gera provento")
    void unknownLabel() {
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L))
            .thenReturn(List.of(event(10L, null, "2026-08-21", "0.50")));

        recalculator.recalculate(position, List.of(buy("2026-08-01", "100")));

        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("AUTO que perdeu o direito (a compra mudou de data) e apagado; MANUAL nao e tocado")
    void deletesAutoWithoutRight() {
        AssetCashEvent e = event(10L, DividendType.DIVIDENDO, "2026-08-21", "0.50");
        Dividend auto = Dividend.builder().id(5L).portfolioAsset(position).cashEvent(e).origin(DividendOrigin.AUTO)
            .type(DividendType.DIVIDENDO).amountPerShare(new BigDecimal("0.5")).exDate(e.getDataCom())
            .totalAmount(new BigDecimal("50.00")).sharesOnDate(new BigDecimal("100")).build();
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(e));
        when(dividendRepository.findAutoByPortfolioAssetId(position.getId())).thenReturn(List.of(auto));

        recalculator.recalculate(position, List.of(buy("2026-09-01", "100")));

        verify(dividendRepository).delete(auto);
        verify(dividendRepository, never()).save(any());
    }
    @Test
    @DisplayName("Provento criado sai com regra, aliquota, IR, liquidos e tax_checked_at (JCP 2026: 17,5%)")
    void dividendBornWithTax() {
        AssetCashEvent jcp = event(10L, DividendType.JCP, "2026-04-22", "0.31311454");
        jcp.setApprovedOn(LocalDate.parse("2026-04-16"));
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(jcp));

        recalculator.recalculate(position, List.of(buy("2026-04-01", "50")));

        Dividend d = saved();
        assertThat(d.getWithholdingRule()).isSameAs(JCP_2026);
        assertThat(d.getWithholdingRate()).isEqualByComparingTo("0.175");
        assertThat(d.getTotalAmount()).isEqualByComparingTo("15.65");
        assertThat(d.getWithholdingTax()).isEqualByComparingTo("2.73");
        assertThat(d.getNetAmount()).isEqualByComparingTo("12.92");
        assertThat(d.getNetAmountPerShare()).isEqualByComparingTo("0.25831950");
        assertThat(d.getTaxCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("Isento: aliquota zero e liquido igual ao bruto")
    void exemptDividend() {
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L))
            .thenReturn(List.of(event(10L, DividendType.DIVIDENDO, "2026-08-21", "0.47156696000")));

        recalculator.recalculate(position, List.of(buy("2026-08-01", "100")));

        Dividend d = saved();
        assertThat(d.getWithholdingRule()).isSameAs(EXEMPT_DIVIDEND);
        assertThat(d.getWithholdingRate()).isEqualByComparingTo("0");
        assertThat(d.getNetAmount()).isEqualByComparingTo(d.getTotalAmount());
    }

    @Test
    @DisplayName("Tipo sem regra: aliquota e liquidos nulos, mas o provento sai conferido (tax_checked_at)")
    void typeWithoutRule() {
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L))
            .thenReturn(List.of(event(10L, DividendType.RENDIMENTO, "2026-08-21", "0.50")));

        recalculator.recalculate(position, List.of(buy("2026-08-01", "10")));

        Dividend d = saved();
        assertThat(d.getWithholdingRule()).isNull();
        assertThat(d.getWithholdingRate()).isNull();
        assertThat(d.getNetAmount()).isNull();
        assertThat(d.getTotalAmount()).isEqualByComparingTo("5.00");
        assertThat(d.getTaxCheckedAt()).isNotNull();
        assertThat(d.netOrGross()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("Provento existente sem IR (tax_checked_at nulo) e recalculado")
    void existingWithoutTaxIsRecalculated() {
        AssetCashEvent e = event(10L, DividendType.DIVIDENDO, "2026-08-21", "0.50");
        Dividend previous = Dividend.builder().id(5L).portfolioAsset(position).cashEvent(e).origin(DividendOrigin.AUTO)
            .type(DividendType.DIVIDENDO).amountPerShare(new BigDecimal("0.50000000")).exDate(e.getDataCom())
            .paymentDate(e.getPaymentDate()).totalAmount(new BigDecimal("50.00"))
            .sharesOnDate(new BigDecimal("100")).build();
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(e));
        when(dividendRepository.findAutoByPortfolioAssetId(position.getId())).thenReturn(List.of(previous));

        recalculator.recalculate(position, List.of(buy("2026-08-01", "100")));

        verify(dividendRepository).save(previous);
        assertThat(previous.getTaxCheckedAt()).isNotNull();
        assertThat(previous.getNetAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Provento ja calculado e igual nao e regravado")
    void existingEqualNotRewritten() {
        AssetCashEvent e = event(10L, DividendType.DIVIDENDO, "2026-08-21", "0.50");
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(e));
        recalculator.recalculate(position, List.of(buy("2026-08-01", "100")));
        Dividend calculated = saved();
        org.mockito.Mockito.clearInvocations(dividendRepository);
        when(dividendRepository.findAutoByPortfolioAssetId(position.getId())).thenReturn(List.of(calculated));

        recalculator.recalculate(position, List.of(buy("2026-08-01", "100")));

        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("Regra mudou (lei nova): o provento ja calculado e regravado com a aliquota nova")
    void ruleChangedRewrites() {
        AssetCashEvent jcp = event(10L, DividendType.JCP, "2026-04-22", "1.00");
        jcp.setApprovedOn(LocalDate.parse("2026-04-16"));
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(jcp));
        recalculator.recalculate(position, List.of(buy("2026-04-01", "100")));
        Dividend calculated = saved();
        assertThat(calculated.getWithholdingRate()).isEqualByComparingTo("0.175");
        org.mockito.Mockito.clearInvocations(dividendRepository);
        when(dividendRepository.findAutoByPortfolioAssetId(position.getId())).thenReturn(List.of(calculated));
        WithholdingTaxRule jcp20 = rule(9L, DividendType.JCP, "0.2000", "2026-01-01", null);
        when(ruleRepository.findAll()).thenReturn(List.of(JCP_2025, jcp20));

        recalculator.recalculate(position, List.of(buy("2026-04-01", "100")));

        verify(dividendRepository).save(calculated);
        assertThat(calculated.getWithholdingRule()).isSameAs(jcp20);
        assertThat(calculated.getWithholdingTax()).isEqualByComparingTo("20.00");
        assertThat(calculated.getNetAmount()).isEqualByComparingTo("80.00");
    }
}
