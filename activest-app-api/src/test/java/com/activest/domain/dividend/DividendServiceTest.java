package com.activest.domain.dividend;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.asset.AssetType;
import com.activest.domain.dividend.dto.DividendResponseDTO;
import com.activest.domain.dividend.dto.DividendSummaryDTO;
import com.activest.domain.portfolio.Portfolio;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.PortfolioRepository;
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
    @Mock private com.activest.domain.asset.AssetLogos assetLogos;
    @InjectMocks private DividendService service;

    private static final WithholdingTaxRule JCP = WithholdingTaxRule.builder().id(2L).dividendType(DividendType.JCP)
        .assetTypeId(1L).rate(new BigDecimal("0.1750")).baseDate(TaxBaseDate.CREDIT).legalBasis("LC 224/2025, art. 8º")
        .build();

    private static Dividend dividendEvent(DividendType typeValue, String gross, String ir, String net, WithholdingTaxRule rule) {
        Asset assetEntity = Asset.builder().id(1L).ticker("BBAS3").name("BANCO DO BRASIL")
            .assetType(AssetType.builder().id(1L).name("ACAO").description("Ações").build()).build();
        PortfolioAsset pa = PortfolioAsset.builder().asset(assetEntity).build();
        return Dividend.builder().id((long) gross.hashCode()).portfolioAsset(pa).type(typeValue)
            .amountPerShare(new BigDecimal("0.08157785")).exDate(LocalDate.of(2026, 6, 1))
            .totalAmount(new BigDecimal(gross))
            .withholdingRule(rule)
            .withholdingRate(rule != null ? rule.getRate() : null)
            .withholdingTax(ir == null ? null : new BigDecimal(ir))
            .netAmount(net == null ? null : new BigDecimal(net))
            .netAmountPerShare(net == null ? null : new BigDecimal("0.06730173"))
            .build();
    }

    @Test
    @DisplayName("Totais e byType em liquido; bruto e IR a parte; tipo sem regra entra pelo bruto")
    void netSummary() {
        UUID user = UUID.randomUUID();
        Portfolio portfolioEntity = Portfolio.builder().build();
        portfolioEntity.setId(UUID.randomUUID());
        when(portfolioRepository.findFirstByUserId(user)).thenReturn(Optional.of(portfolioEntity));
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 24));
        when(dividendRepository.findByPortfolioIdWithAsset(portfolioEntity.getId())).thenReturn(List.of(
            dividendEvent(DividendType.JCP, "10.19", "1.78", "8.41", JCP),
            dividendEvent(DividendType.JCP, "7.46", "1.30", "6.16", JCP),
            dividendEvent(DividendType.RENDIMENTO, "7.98", null, null, null)));

        DividendSummaryDTO summary = service.getDividendSummary(user);

        assertThat(summary.totalDividends()).isEqualByComparingTo("22.55");   // 8,41 + 6,16 + 7,98
        assertThat(summary.totalGross()).isEqualByComparingTo("25.63");
        assertThat(summary.totalTax()).isEqualByComparingTo("3.08");
        assertThat(summary.byType().get("JCP")).isEqualByComparingTo("14.57");
        assertThat(summary.byType().get("RENDIMENTO")).isEqualByComparingTo("7.98");

        DividendResponseDTO jcp = summary.recent().get(0);
        assertThat(jcp.withholdingRate()).isEqualByComparingTo("0.175");
        assertThat(jcp.withholdingTax()).isEqualByComparingTo("1.78");
        assertThat(jcp.netAmount()).isEqualByComparingTo("8.41");
        assertThat(jcp.netAmountPerShare()).isEqualByComparingTo("0.06730173");
        assertThat(jcp.withholdingLegalBasis()).isEqualTo("LC 224/2025, art. 8º");
        DividendResponseDTO withoutRule = summary.recent().get(2);
        assertThat(withoutRule.withholdingRate()).isNull();
        assertThat(withoutRule.netAmount()).isNull();
        assertThat(withoutRule.withholdingLegalBasis()).isNull();
    }

    @Test
    @DisplayName("Recebido x a receber: totais e byType so do recebido; a receber a parte; cada provento com a situacao")
    void receivedAndToReceive() {
        UUID user = UUID.randomUUID();
        Portfolio portfolioEntity = Portfolio.builder().build();
        portfolioEntity.setId(UUID.randomUUID());
        when(portfolioRepository.findFirstByUserId(user)).thenReturn(Optional.of(portfolioEntity));
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 24));
        Dividend paid = dividendEvent(DividendType.JCP, "10.19", "1.78", "8.41", JCP);
        paid.setPaymentDate(LocalDate.of(2026, 9, 24));
        Dividend future = dividendEvent(DividendType.JCP, "16.25", "2.84", "13.41", JCP);
        future.setPaymentDate(LocalDate.of(2027, 1, 22));
        when(dividendRepository.findByPortfolioIdWithAsset(portfolioEntity.getId())).thenReturn(List.of(paid, future));

        DividendSummaryDTO summary = service.getDividendSummary(user);

        assertThat(summary.totalDividends()).isEqualByComparingTo("8.41");
        assertThat(summary.totalGross()).isEqualByComparingTo("10.19");
        assertThat(summary.totalTax()).isEqualByComparingTo("1.78");
        assertThat(summary.byType().get("JCP")).isEqualByComparingTo("8.41");
        assertThat(summary.totalToReceive()).isEqualByComparingTo("13.41");
        assertThat(summary.toReceiveGross()).isEqualByComparingTo("16.25");
        assertThat(summary.toReceiveTax()).isEqualByComparingTo("2.84");
        assertThat(summary.recent()).extracting(DividendResponseDTO::received).containsExactly(true, false);
    }
}
