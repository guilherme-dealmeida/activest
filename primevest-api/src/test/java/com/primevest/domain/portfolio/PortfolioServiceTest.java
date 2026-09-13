package com.primevest.domain.portfolio;

import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioAssetRepository portfolioAssetRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PortfolioService portfolioService;

    @Test
    @DisplayName("Deve retornar resumo do portfólio com ativos, calculando retorno e percentual corretamente")
    void testGetPortfolioSummary_withAssets() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        portfolio.setUserId(userId);

        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        PortfolioAsset petr4 = new PortfolioAsset();
        petr4.setAveragePrice(new BigDecimal("35.00"));
        petr4.setShares(new BigDecimal("100"));
        petr4.setCurrentPrice(new BigDecimal("38.50"));
        petr4.setDividends(BigDecimal.ZERO);

        PortfolioAsset mxrf11 = new PortfolioAsset();
        mxrf11.setAveragePrice(new BigDecimal("10.00"));
        mxrf11.setShares(new BigDecimal("200"));
        mxrf11.setCurrentPrice(new BigDecimal("10.50"));
        mxrf11.setDividends(new BigDecimal("50.00"));

        when(portfolioAssetRepository.findByPortfolioId(portfolio.getId()))
                .thenReturn(List.of(petr4, mxrf11));

        // Act
        PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary(userId);

        // Assert
        // Total Invested = (35 * 100) + (10 * 200) = 3500 + 2000 = 5500
        // Current Value = (38.50 * 100) + (10.50 * 200) = 3850 + 2100 = 5950
        // Dividends = 0 + 50 = 50
        // Total Return = (5950 + 50) - 5500 = 500
        // Return % = (500 / 5500) * 100 = 9.09%

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalInvested()).isEqualByComparingTo("5500.00");
        assertThat(summary.getCurrentValue()).isEqualByComparingTo("5950.00");
        assertThat(summary.getTotalDividends()).isEqualByComparingTo("50.00");
        assertThat(summary.getTotalReturn()).isEqualByComparingTo("500.00");
        
        // Use a reasonable tolerance for percentages
        assertThat(summary.getReturnPercentage()).isCloseTo(new BigDecimal("9.09"), org.assertj.core.data.Percentage.withPercentage(1));
    }

    @Test
    @DisplayName("Deve retornar valores zerados para portfólio vazio")
    void testGetPortfolioSummary_emptyPortfolio() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        portfolio.setUserId(userId);

        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));
        when(portfolioAssetRepository.findByPortfolioId(portfolio.getId())).thenReturn(List.of());

        // Act
        PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary(userId);

        // Assert
        assertThat(summary).isNotNull();
        assertThat(summary.getTotalInvested()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalDividends()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getReturnPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Deve incluir dividendos no cálculo de retorno do portfólio")
    void testGetPortfolioSummary_withDividends() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        portfolio.setUserId(userId);

        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        PortfolioAsset mxrf11 = new PortfolioAsset();
        mxrf11.setAveragePrice(new BigDecimal("10.00"));
        mxrf11.setShares(new BigDecimal("100"));
        mxrf11.setCurrentPrice(new BigDecimal("10.00")); // No capital gain
        mxrf11.setDividends(new BigDecimal("100.00")); // Only dividends

        when(portfolioAssetRepository.findByPortfolioId(portfolio.getId())).thenReturn(List.of(mxrf11));

        // Act
        PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary(userId);

        // Assert
        // Invested: 1000, Current: 1000, Div: 100
        // Return: 100, Return %: 10%
        assertThat(summary.getTotalReturn()).isEqualByComparingTo("100.00");
        assertThat(summary.getReturnPercentage()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Deve listar ativos do portfólio calculando valorização corretamente")
    void testGetPortfolioAssets_calculatesValorizationCorrectly() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        PortfolioAsset petr4 = new PortfolioAsset();
        petr4.setTicker("PETR4");
        petr4.setAveragePrice(new BigDecimal("35.00"));
        petr4.setShares(new BigDecimal("100"));
        petr4.setCurrentPrice(new BigDecimal("38.50"));

        when(portfolioAssetRepository.findByPortfolioId(portfolio.getId())).thenReturn(List.of(petr4));

        // Act
        List<PortfolioAssetDetailDTO> assets = portfolioService.getPortfolioAssets(userId);

        // Assert
        assertThat(assets).hasSize(1);
        PortfolioAssetDetailDTO dto = assets.get(0);
        // Valorization = (38.5 - 35) * 100 = 350
        // Return % = (38.5 / 35 - 1) * 100 = 10%
        assertThat(dto.getValorization()).isEqualByComparingTo("350.00");
        assertThat(dto.getReturnPercentage()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Deve lidar com ativo com retorno negativo corretamente")
    void testGetPortfolioAssets_negativeReturn() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        PortfolioAsset vale3 = new PortfolioAsset();
        vale3.setTicker("VALE3");
        vale3.setAveragePrice(new BigDecimal("72.00"));
        vale3.setShares(new BigDecimal("50"));
        vale3.setCurrentPrice(new BigDecimal("68.20"));

        when(portfolioAssetRepository.findByPortfolioId(portfolio.getId())).thenReturn(List.of(vale3));

        // Act
        List<PortfolioAssetDetailDTO> assets = portfolioService.getPortfolioAssets(userId);

        // Assert
        assertThat(assets).hasSize(1);
        PortfolioAssetDetailDTO dto = assets.get(0);
        // Valorization = (68.2 - 72) * 50 = -3.8 * 50 = -190
        // Return % = (68.2 / 72 - 1) * 100 = -5.277...
        assertThat(dto.getValorization()).isEqualByComparingTo("-190.00");
        assertThat(dto.getReturnPercentage()).isCloseTo(new BigDecimal("-5.28"), org.assertj.core.data.Percentage.withPercentage(1));
    }

    @Test
    @DisplayName("Deve calcular alocação do portfólio agrupada por tipo de ativo")
    void testGetAllocation_groupsByType() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());

        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        PortfolioAsset rv1 = new PortfolioAsset();
        rv1.setAssetType("RENDA_VARIAVEL");
        rv1.setCurrentPrice(new BigDecimal("100")); // Total 1000
        rv1.setShares(new BigDecimal("10"));

        PortfolioAsset rv2 = new PortfolioAsset();
        rv2.setAssetType("RENDA_VARIAVEL");
        rv2.setCurrentPrice(new BigDecimal("50"));  // Total 1000
        rv2.setShares(new BigDecimal("20"));

        PortfolioAsset fii = new PortfolioAsset();
        fii.setAssetType("FII");
        fii.setCurrentPrice(new BigDecimal("10"));  // Total 2000
        fii.setShares(new BigDecimal("200"));

        when(portfolioAssetRepository.findByPortfolioId(portfolio.getId()))
                .thenReturn(List.of(rv1, rv2, fii));

        // Act
        List<PortfolioAllocationDTO> allocation = portfolioService.getAllocation(userId);

        // Assert
        // Total Value = 1000 + 1000 + 2000 = 4000
        // RENDA_VARIAVEL = 2000 (50%)
        // FII = 2000 (50%)
        assertThat(allocation).hasSize(2);
        
        Optional<PortfolioAllocationDTO> rv = allocation.stream().filter(a -> a.getAssetType().equals("RENDA_VARIAVEL")).findFirst();
        assertThat(rv).isPresent();
        assertThat(rv.get().getPercentage()).isEqualByComparingTo("50.00");

        Optional<PortfolioAllocationDTO> fiis = allocation.stream().filter(a -> a.getAssetType().equals("FII")).findFirst();
        assertThat(fiis).isPresent();
        assertThat(fiis.get().getPercentage()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Deve calcular alocação com 100% em um único tipo")
    void testGetAllocation_singleType() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());

        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        PortfolioAsset fii = new PortfolioAsset();
        fii.setAssetType("FII");
        fii.setCurrentPrice(new BigDecimal("10.50"));
        fii.setShares(new BigDecimal("100")); // Total 1050

        when(portfolioAssetRepository.findByPortfolioId(portfolio.getId()))
                .thenReturn(List.of(fii));

        // Act
        List<PortfolioAllocationDTO> allocation = portfolioService.getAllocation(userId);

        // Assert
        assertThat(allocation).hasSize(1);
        assertThat(allocation.get(0).getAssetType()).isEqualTo("FII");
        assertThat(allocation.get(0).getPercentage()).isEqualByComparingTo("100.00");
    }
}
