package com.primevest.domain.dividend;

import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verificacao do IR na subida (docs/planos/PLANO_IR_PROVENTOS.md, 4.5, trava 1).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DividendTaxVerifierTest {

    @Mock private DividendRepository dividendRepository;
    @Mock private PortfolioAssetRepository portfolioAssetRepository;
    @Mock private DividendRecalculator dividendRecalculator;
    @Mock private PlatformTransactionManager transactionManager;

    private DividendTaxVerifier verifier;

    @BeforeEach
    void cenario() {
        verifier = new DividendTaxVerifier(dividendRepository, portfolioAssetRepository, dividendRecalculator,
            transactionManager);
    }

    private PortfolioAsset posicao(UUID id) {
        PortfolioAsset pa = PortfolioAsset.builder().build();
        pa.setId(id);
        when(portfolioAssetRepository.findById(id)).thenReturn(Optional.of(pa));
        when(dividendRecalculator.chronologicalTransactions(pa)).thenReturn(List.of());
        return pa;
    }

    @Test
    @DisplayName("Nada pendente: nenhuma posicao e recalculada")
    void nadaPendente() {
        when(dividendRepository.findPositionIdsWithTaxPending()).thenReturn(List.of());

        assertThat(verifier.verify()).isZero();
        verify(dividendRecalculator, never()).recalculate(any(), any());
    }

    @Test
    @DisplayName("Recalcula so as posicoes com IR pendente")
    void recalculaPendentes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PortfolioAsset pa = posicao(a);
        PortfolioAsset pb = posicao(b);
        when(dividendRepository.findPositionIdsWithTaxPending()).thenReturn(List.of(a, b));

        assertThat(verifier.verify()).isEqualTo(2);
        verify(dividendRecalculator).recalculate(eq(pa), any());
        verify(dividendRecalculator).recalculate(eq(pb), any());
    }

    @Test
    @DisplayName("Idempotente: depois de recalcular, a segunda execucao nao encontra nada")
    void idempotente() {
        UUID a = UUID.randomUUID();
        posicao(a);
        when(dividendRepository.findPositionIdsWithTaxPending()).thenReturn(List.of(a), List.of());

        assertThat(verifier.verify()).isEqualTo(1);
        assertThat(verifier.verify()).isZero();
    }

    @Test
    @DisplayName("Falha numa posicao nao impede as outras")
    void falhaNaoImpedeAsOutras() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PortfolioAsset pa = posicao(a);
        PortfolioAsset pb = posicao(b);
        when(dividendRepository.findPositionIdsWithTaxPending()).thenReturn(List.of(a, b));
        when(dividendRecalculator.recalculate(eq(pa), any())).thenThrow(new IllegalStateException("falhou"));

        assertThat(verifier.verify()).isEqualTo(1);
        verify(dividendRecalculator).recalculate(eq(pb), any());
    }
}
