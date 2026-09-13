package com.primevest.integration.coingecko;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO para a resposta de cotação da CoinGecko API.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinGeckoQuoteDTO {

    /**
     * Map de coinId -> Map de currency -> price.
     * Ex: {"bitcoin": {"brl": 350000.00, "usd": 65000.00}}
     */
    private Map<String, CurrencyPrice> prices;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrencyPrice {
        @JsonProperty("brl")
        private BigDecimal brl;

        @JsonProperty("usd")
        private BigDecimal usd;

        @JsonProperty("brl_24h_change")
        private BigDecimal brl24hChange;

        @JsonProperty("usd_24h_change")
        private BigDecimal usd24hChange;

        @JsonProperty("brl_market_cap")
        private BigDecimal brlMarketCap;

        @JsonProperty("last_updated_at")
        private Long lastUpdatedAt;
    }
}
