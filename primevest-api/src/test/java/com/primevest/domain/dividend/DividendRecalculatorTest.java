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
 * Proventos AUTO a partir dos eventos do catalogo (PLANO_BRAPI.md, F5): valor
 * por cota x cotas na data-com, com os desdobramentos no meio — e o IR retido
 * calculado junto (PLANO_IR_PROVENTOS.md, 4.4).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DividendRecalculatorTest {

    @Mock private DividendRepository dividendRepository;
    @Mock private AssetCashEventRepository cashEventRepository;
    @Mock private AssetStockEventRepository stockEventRepository;
    @Mock private WithholdingTaxRuleRepository ruleRepository;

    private static final long ACAO = 1L;
    private static final WithholdingTaxRule JCP_2025 = regra(1L, DividendType.JCP, "0.1500", null, "2025-12-31");
    private static final WithholdingTaxRule JCP_2026 = regra(2L, DividendType.JCP, "0.1750", "2026-01-01", null);
    private static final WithholdingTaxRule DIVIDENDO_ISENTO = regra(3L, DividendType.DIVIDENDO, "0", null, null);

    private DividendRecalculator recalculator;
    private PortfolioAsset posicao;
    private Asset petr4;

    @BeforeEach
    void cenario() {
        PositionCalculator calculator = new PositionCalculator(null, stockEventRepository, dividendRepository);
        recalculator = new DividendRecalculator(calculator, dividendRepository, cashEventRepository, ruleRepository);
        petr4 = Asset.builder().id(1L).ticker("PETR4").name("PETROBRAS")
            .assetType(AssetType.builder().id(ACAO).name("ACAO").build()).build();
        posicao = PortfolioAsset.builder().asset(petr4).build();
        posicao.setId(UUID.randomUUID());
        when(dividendRepository.findAutoByPortfolioAssetId(any())).thenReturn(List.of());
        when(stockEventRepository.findByAssetIdOrderByDataComAsc(anyLong())).thenReturn(List.of());
        when(ruleRepository.findAll()).thenReturn(List.of(JCP_2025, JCP_2026, DIVIDENDO_ISENTO));
    }

    private static WithholdingTaxRule regra(long id, DividendType tipo, String aliquota, String de, String ate) {
        return WithholdingTaxRule.builder().id(id).dividendType(tipo).assetTypeId(ACAO)
            .rate(new BigDecimal(aliquota)).baseDate(TaxBaseDate.CREDIT)
            .validFrom(de == null ? null : LocalDate.parse(de)).validTo(ate == null ? null : LocalDate.parse(ate))
            .legalBasis("teste").build();
    }

    private Dividend salvo() {
        ArgumentCaptor<Dividend> salvo = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(salvo.capture());
        return salvo.getValue();
    }

    private static Transaction compra(String data, String cotas) {
        return Transaction.builder().type(TransactionType.BUY).shares(new BigDecimal(cotas))
            .pricePerShare(new BigDecimal("30")).transactionDate(LocalDate.parse(data))
            .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
    }

    private AssetCashEvent evento(long id, DividendType tipo, String dataCom, String valor) {
        return AssetCashEvent.builder().id(id).asset(petr4).type(tipo).label(tipo == null ? "XPTO" : tipo.name())
            .dataCom(LocalDate.parse(dataCom)).paymentDate(LocalDate.parse(dataCom).plusMonths(2))
            .valuePerShare(new BigDecimal(valor)).source(CashEventSource.B3).build();
    }

    @Test
    @DisplayName("Evento com cotas na data-com gera provento AUTO com cotas, total bruto truncado e referencia ao evento")
    void geraProventoAuto() {
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L))
            .thenReturn(List.of(evento(10L, DividendType.DIVIDENDO, "2026-08-21", "0.47156696000")));

        int criados = recalculator.recalculate(posicao, List.of(compra("2026-08-01", "100")));

        ArgumentCaptor<Dividend> salvo = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(salvo.capture());
        Dividend d = salvo.getValue();
        assertThat(criados).isEqualTo(1);
        assertThat(d.getOrigin()).isEqualTo(DividendOrigin.AUTO);
        assertThat(d.getCashEvent().getId()).isEqualTo(10L);
        assertThat(d.getSharesOnDate()).isEqualByComparingTo("100");
        // 0,47156696 x 100 = 47,156696: truncado como a B3 (antes, arredondado para 47,16).
        assertThat(d.getTotalAmount()).isEqualByComparingTo("47.15");
        assertThat(d.getExDate()).isEqualTo(LocalDate.parse("2026-08-21"));
    }

    @Test
    @DisplayName("Compra depois da data-com nao tem direito: nada e criado")
    void compraDepoisDaDataCom() {
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L))
            .thenReturn(List.of(evento(10L, DividendType.JCP, "2026-08-21", "0.67")));

        recalculator.recalculate(posicao, List.of(compra("2026-08-22", "100")));

        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("Provento pago depois de um desdobramento usa as cotas novas; antes, as antigas")
    void proventoAntesEDepoisDoDesdobramento() {
        when(stockEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(AssetStockEvent.builder()
            .asset(petr4).type(StockEventType.DESDOBRAMENTO).dataCom(LocalDate.parse("2026-04-15"))
            .factor(new BigDecimal("100")).source("B3").build()));
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(
            evento(1L, DividendType.DIVIDENDO, "2026-03-31", "1.00"),
            evento(2L, DividendType.DIVIDENDO, "2026-06-30", "1.00")));

        recalculator.recalculate(posicao, List.of(compra("2026-03-02", "100")));

        ArgumentCaptor<Dividend> salvos = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository, org.mockito.Mockito.times(2)).save(salvos.capture());
        assertThat(salvos.getAllValues()).extracting(Dividend::getTotalAmount)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("100.00"), new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("Rotulo desconhecido (tipo nulo) nao gera provento")
    void rotuloDesconhecido() {
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L))
            .thenReturn(List.of(evento(10L, null, "2026-08-21", "0.50")));

        recalculator.recalculate(posicao, List.of(compra("2026-08-01", "100")));

        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("AUTO que perdeu o direito (a compra mudou de data) e apagado; MANUAL nao e tocado")
    void apagaAutoSemDireito() {
        AssetCashEvent e = evento(10L, DividendType.DIVIDENDO, "2026-08-21", "0.50");
        Dividend auto = Dividend.builder().id(5L).portfolioAsset(posicao).cashEvent(e).origin(DividendOrigin.AUTO)
            .type(DividendType.DIVIDENDO).amountPerShare(new BigDecimal("0.5")).exDate(e.getDataCom())
            .totalAmount(new BigDecimal("50.00")).sharesOnDate(new BigDecimal("100")).build();
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(e));
        when(dividendRepository.findAutoByPortfolioAssetId(posicao.getId())).thenReturn(List.of(auto));

        recalculator.recalculate(posicao, List.of(compra("2026-09-01", "100")));

        verify(dividendRepository).delete(auto);
        verify(dividendRepository, never()).save(any());
    }
    @Test
    @DisplayName("Provento criado sai com regra, aliquota, IR, liquidos e tax_checked_at (JCP 2026: 17,5%)")
    void proventoNasceComIr() {
        AssetCashEvent jcp = evento(10L, DividendType.JCP, "2026-04-22", "0.31311454");
        jcp.setApprovedOn(LocalDate.parse("2026-04-16"));
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(jcp));

        recalculator.recalculate(posicao, List.of(compra("2026-04-01", "50")));

        Dividend d = salvo();
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
    void proventoIsento() {
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L))
            .thenReturn(List.of(evento(10L, DividendType.DIVIDENDO, "2026-08-21", "0.47156696000")));

        recalculator.recalculate(posicao, List.of(compra("2026-08-01", "100")));

        Dividend d = salvo();
        assertThat(d.getWithholdingRule()).isSameAs(DIVIDENDO_ISENTO);
        assertThat(d.getWithholdingRate()).isEqualByComparingTo("0");
        assertThat(d.getNetAmount()).isEqualByComparingTo(d.getTotalAmount());
    }

    @Test
    @DisplayName("Tipo sem regra: aliquota e liquidos nulos, mas o provento sai conferido (tax_checked_at)")
    void tipoSemRegra() {
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L))
            .thenReturn(List.of(evento(10L, DividendType.RENDIMENTO, "2026-08-21", "0.50")));

        recalculator.recalculate(posicao, List.of(compra("2026-08-01", "10")));

        Dividend d = salvo();
        assertThat(d.getWithholdingRule()).isNull();
        assertThat(d.getWithholdingRate()).isNull();
        assertThat(d.getNetAmount()).isNull();
        assertThat(d.getTotalAmount()).isEqualByComparingTo("5.00");
        assertThat(d.getTaxCheckedAt()).isNotNull();
        assertThat(d.netOrGross()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("Provento existente sem IR (tax_checked_at nulo) e recalculado")
    void existenteSemIrERecalculado() {
        AssetCashEvent e = evento(10L, DividendType.DIVIDENDO, "2026-08-21", "0.50");
        Dividend antigo = Dividend.builder().id(5L).portfolioAsset(posicao).cashEvent(e).origin(DividendOrigin.AUTO)
            .type(DividendType.DIVIDENDO).amountPerShare(new BigDecimal("0.50000000")).exDate(e.getDataCom())
            .paymentDate(e.getPaymentDate()).totalAmount(new BigDecimal("50.00"))
            .sharesOnDate(new BigDecimal("100")).build();
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(e));
        when(dividendRepository.findAutoByPortfolioAssetId(posicao.getId())).thenReturn(List.of(antigo));

        recalculator.recalculate(posicao, List.of(compra("2026-08-01", "100")));

        verify(dividendRepository).save(antigo);
        assertThat(antigo.getTaxCheckedAt()).isNotNull();
        assertThat(antigo.getNetAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Provento ja calculado e igual nao e regravado")
    void existenteIgualNaoRegrava() {
        AssetCashEvent e = evento(10L, DividendType.DIVIDENDO, "2026-08-21", "0.50");
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(e));
        recalculator.recalculate(posicao, List.of(compra("2026-08-01", "100")));
        Dividend calculado = salvo();
        org.mockito.Mockito.clearInvocations(dividendRepository);
        when(dividendRepository.findAutoByPortfolioAssetId(posicao.getId())).thenReturn(List.of(calculado));

        recalculator.recalculate(posicao, List.of(compra("2026-08-01", "100")));

        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("Regra mudou (lei nova): o provento ja calculado e regravado com a aliquota nova")
    void regraMudouRegrava() {
        AssetCashEvent jcp = evento(10L, DividendType.JCP, "2026-04-22", "1.00");
        jcp.setApprovedOn(LocalDate.parse("2026-04-16"));
        when(cashEventRepository.findByAssetIdOrderByDataComAsc(1L)).thenReturn(List.of(jcp));
        recalculator.recalculate(posicao, List.of(compra("2026-04-01", "100")));
        Dividend calculado = salvo();
        assertThat(calculado.getWithholdingRate()).isEqualByComparingTo("0.175");
        org.mockito.Mockito.clearInvocations(dividendRepository);
        when(dividendRepository.findAutoByPortfolioAssetId(posicao.getId())).thenReturn(List.of(calculado));
        WithholdingTaxRule jcp20 = regra(9L, DividendType.JCP, "0.2000", "2026-01-01", null);
        when(ruleRepository.findAll()).thenReturn(List.of(JCP_2025, jcp20));

        recalculator.recalculate(posicao, List.of(compra("2026-04-01", "100")));

        verify(dividendRepository).save(calculado);
        assertThat(calculado.getWithholdingRule()).isSameAs(jcp20);
        assertThat(calculado.getWithholdingTax()).isEqualByComparingTo("20.00");
        assertThat(calculado.getNetAmount()).isEqualByComparingTo("80.00");
    }
}
