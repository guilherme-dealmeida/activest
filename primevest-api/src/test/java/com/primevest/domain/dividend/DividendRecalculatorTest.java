package com.primevest.domain.dividend;

import com.primevest.domain.asset.Asset;
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
 * por cota x cotas na data-com, com os desdobramentos no meio.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DividendRecalculatorTest {

    @Mock private DividendRepository dividendRepository;
    @Mock private AssetCashEventRepository cashEventRepository;
    @Mock private AssetStockEventRepository stockEventRepository;

    private DividendRecalculator recalculator;
    private PortfolioAsset posicao;
    private Asset petr4;

    @BeforeEach
    void cenario() {
        PositionCalculator calculator = new PositionCalculator(null, stockEventRepository, dividendRepository);
        recalculator = new DividendRecalculator(calculator, dividendRepository, cashEventRepository);
        petr4 = Asset.builder().id(1L).ticker("PETR4").name("PETROBRAS").build();
        posicao = PortfolioAsset.builder().asset(petr4).build();
        posicao.setId(UUID.randomUUID());
        when(dividendRepository.findAutoByPortfolioAssetId(any())).thenReturn(List.of());
        when(stockEventRepository.findByAssetIdOrderByDataComAsc(anyLong())).thenReturn(List.of());
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
    @DisplayName("Evento com cotas na data-com gera provento AUTO com cotas, total e referencia ao evento")
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
        assertThat(d.getTotalAmount()).isEqualByComparingTo("47.16");
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
}
