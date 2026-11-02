package com.activest.domain.portfolio;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetType;
import com.activest.domain.currency.ExchangeRateService;
import com.activest.domain.currency.RateSide;
import com.activest.domain.portfolio.dto.PortfolioAssetDetailDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Segunda linha em outra moeda (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md, R2 e R4), com os números do IAU da
 * conferência de 01/11/2026: compra a US$ 78,268 com PTAX 5,0096; hoje US$ 72,92 com PTAX 5,4212.
 */
class ForeignValuesServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    private final ExchangeRateService rates = mock(ExchangeRateService.class);
    private final ForeignValuesService service = new ForeignValuesService(mock(JdbcTemplate.class), rates);

    private static PortfolioAsset position(String type, String currency, String price) {
        Asset asset = Asset.builder().ticker("X").name("X").currencyCode(currency)
                .assetType(AssetType.builder().name(type).build()).currentPrice(new BigDecimal(price)).build();
        return PortfolioAsset.builder().id(UUID.randomUUID()).asset(asset).build();
    }

    private void ptax(String rate) {
        when(rates.isBase("BRL")).thenReturn(true);
        when(rates.rateOn(eq("USD"), any(), eq(RateSide.SELL))).thenReturn(Optional.of(
                new ExchangeRateService.Quote("USD", new BigDecimal(rate), TODAY, LocalDate.of(2026, 10, 30))));
    }

    @Test
    @DisplayName("ETF do exterior: preço médio e valor inicial pelo câmbio das compras, atual pela cotação; valorização em dólar negativa com a de reais positiva")
    void foreignAsset() {
        ptax("5.4212");
        PortfolioAsset iau = position("ETF_INTERNACIONAL", "USD", "72.92");

        PortfolioAssetDetailDTO.ForeignValues v = service.of(iau, new BigDecimal("5.0096"), TODAY,
                new BigDecimal("1.55913017"), new BigDecimal("392.09137235"), new BigDecimal("611.32"),
                new BigDecimal("395.31"), BigDecimal.ZERO);

        assertThat(v.currencyCode()).isEqualTo("USD");
        assertThat(v.price()).isEqualByComparingTo("72.92");
        assertThat(v.averagePrice()).isEqualByComparingTo("78.26799991");
        assertThat(v.totalInvested()).isEqualByComparingTo("122.03");
        assertThat(v.currentValue()).isEqualByComparingTo("113.69");
        assertThat(v.valorization()).isEqualByComparingTo("-8.34");
        assertThat(v.returnPercentage()).isEqualByComparingTo("-6.83");
        assertThat(v.totalDividends()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Cripto em reais: preço atual em dólar pela PTAX de hoje")
    void crypto() {
        ptax("5.4212");
        PortfolioAsset btc = position("CRIPTO", "BRL", "421583.00");

        PortfolioAssetDetailDTO.ForeignValues v = service.of(btc, new BigDecimal("5.30"), TODAY,
                new BigDecimal("0.00831073"), new BigDecimal("432620.49264144"), new BigDecimal("3595.39"),
                new BigDecimal("421583.00"), BigDecimal.ZERO);

        assertThat(v.currencyCode()).isEqualTo("USD");
        assertThat(v.price()).isEqualByComparingTo("77765.62384712");
        assertThat(v.totalInvested()).isEqualByComparingTo("678.38");
    }

    @Test
    @DisplayName("Ativo em reais que não é cripto: sem segunda linha")
    void brl() {
        ptax("5.4212");
        assertThat(service.of(position("ACAO", "BRL", "30"), null, TODAY, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO)).isNull();
    }
}
