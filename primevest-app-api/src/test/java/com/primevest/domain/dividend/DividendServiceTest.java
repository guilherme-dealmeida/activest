package com.primevest.domain.dividend;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.dividend.dto.DividendResponseDTO;
import com.primevest.domain.dividend.dto.DividendSummaryDTO;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Resumo dos proventos em liquido (docs/planos/PLANO_IR_PROVENTOS.md, D3).
 */
@ExtendWith(MockitoExtension.class)
class DividendServiceTest {

    @Mock private DividendRepository dividendRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private DividendCalendar dividendCalendar;
    @Mock private com.primevest.domain.asset.AssetLogos assetLogos;
    @InjectMocks private DividendService service;

    private static final WithholdingTaxRule JCP = WithholdingTaxRule.builder().id(2L).dividendType(DividendType.JCP)
        .assetTypeId(1L).rate(new BigDecimal("0.1750")).baseDate(TaxBaseDate.CREDIT).legalBasis("LC 224/2025, art. 8º")
        .build();

    private static Dividend provento(DividendType tipo, String bruto, String ir, String liquido, WithholdingTaxRule regra) {
        Asset ativo = Asset.builder().id(1L).ticker("BBAS3").name("BANCO DO BRASIL")
            .assetType(AssetType.builder().id(1L).name("ACAO").description("Ações").build()).build();
        PortfolioAsset pa = PortfolioAsset.builder().asset(ativo).build();
        return Dividend.builder().id((long) bruto.hashCode()).portfolioAsset(pa).type(tipo)
            .amountPerShare(new BigDecimal("0.08157785")).exDate(LocalDate.of(2026, 6, 1))
            .totalAmount(new BigDecimal(bruto))
            .withholdingRule(regra)
            .withholdingRate(regra != null ? regra.getRate() : null)
            .withholdingTax(ir == null ? null : new BigDecimal(ir))
            .netAmount(liquido == null ? null : new BigDecimal(liquido))
            .netAmountPerShare(liquido == null ? null : new BigDecimal("0.06730173"))
            .build();
    }

    @Test
    @DisplayName("Totais e byType em liquido; bruto e IR a parte; tipo sem regra entra pelo bruto")
    void resumoEmLiquido() {
        UUID usuario = UUID.randomUUID();
        Portfolio carteira = Portfolio.builder().build();
        carteira.setId(UUID.randomUUID());
        when(portfolioRepository.findFirstByUserId(usuario)).thenReturn(Optional.of(carteira));
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 24));
        when(dividendRepository.findByPortfolioIdWithAsset(carteira.getId())).thenReturn(List.of(
            provento(DividendType.JCP, "10.19", "1.78", "8.41", JCP),
            provento(DividendType.JCP, "7.46", "1.30", "6.16", JCP),
            provento(DividendType.RENDIMENTO, "7.98", null, null, null)));

        DividendSummaryDTO resumo = service.getDividendSummary(usuario);

        assertThat(resumo.totalDividends()).isEqualByComparingTo("22.55");   // 8,41 + 6,16 + 7,98
        assertThat(resumo.totalGross()).isEqualByComparingTo("25.63");
        assertThat(resumo.totalTax()).isEqualByComparingTo("3.08");
        assertThat(resumo.byType().get("JCP")).isEqualByComparingTo("14.57");
        assertThat(resumo.byType().get("RENDIMENTO")).isEqualByComparingTo("7.98");

        DividendResponseDTO jcp = resumo.recent().get(0);
        assertThat(jcp.withholdingRate()).isEqualByComparingTo("0.175");
        assertThat(jcp.withholdingTax()).isEqualByComparingTo("1.78");
        assertThat(jcp.netAmount()).isEqualByComparingTo("8.41");
        assertThat(jcp.netAmountPerShare()).isEqualByComparingTo("0.06730173");
        assertThat(jcp.withholdingLegalBasis()).isEqualTo("LC 224/2025, art. 8º");
        DividendResponseDTO semRegra = resumo.recent().get(2);
        assertThat(semRegra.withholdingRate()).isNull();
        assertThat(semRegra.netAmount()).isNull();
        assertThat(semRegra.withholdingLegalBasis()).isNull();
    }

    @Test
    @DisplayName("Recebido x a receber: totais e byType so do recebido; a receber a parte; cada provento com a situacao")
    void recebidoEAReceber() {
        UUID usuario = UUID.randomUUID();
        Portfolio carteira = Portfolio.builder().build();
        carteira.setId(UUID.randomUUID());
        when(portfolioRepository.findFirstByUserId(usuario)).thenReturn(Optional.of(carteira));
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 24));
        Dividend pago = provento(DividendType.JCP, "10.19", "1.78", "8.41", JCP);
        pago.setPaymentDate(LocalDate.of(2026, 9, 24));
        Dividend futuro = provento(DividendType.JCP, "16.25", "2.84", "13.41", JCP);
        futuro.setPaymentDate(LocalDate.of(2027, 1, 22));
        when(dividendRepository.findByPortfolioIdWithAsset(carteira.getId())).thenReturn(List.of(pago, futuro));

        DividendSummaryDTO resumo = service.getDividendSummary(usuario);

        assertThat(resumo.totalDividends()).isEqualByComparingTo("8.41");
        assertThat(resumo.totalGross()).isEqualByComparingTo("10.19");
        assertThat(resumo.totalTax()).isEqualByComparingTo("1.78");
        assertThat(resumo.byType().get("JCP")).isEqualByComparingTo("8.41");
        assertThat(resumo.totalToReceive()).isEqualByComparingTo("13.41");
        assertThat(resumo.toReceiveGross()).isEqualByComparingTo("16.25");
        assertThat(resumo.toReceiveTax()).isEqualByComparingTo("2.84");
        assertThat(resumo.recent()).extracting(DividendResponseDTO::received).containsExactly(true, false);
    }
}
