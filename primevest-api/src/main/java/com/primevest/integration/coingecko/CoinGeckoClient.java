package com.primevest.integration.coingecko;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client para integração com a CoinGecko API – cotações de criptomoedas.
 *
 * <p>A CoinGecko fornece cotações gratuitas de criptomoedas em múltiplas moedas.
 * Endpoint principal: GET /simple/price</p>
 *
 * <p>Mapeamento de tickers do PrimeVest para CoinGecko IDs:</p>
 * <ul>
 *   <li>BTC → bitcoin</li>
 *   <li>ETH → ethereum</li>
 *   <li>SOL → solana</li>
 *   <li>ADA → cardano</li>
 *   <li>DOT → polkadot</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoinGeckoClient {

    @Value("${coingecko.base-url}")
    private String baseUrl;

    private WebClient webClient;

    /**
     * Mapeamento de ticker interno → CoinGecko ID.
     */
    private static final Map<String, String> TICKER_TO_COINGECKO_ID = Map.ofEntries(
            Map.entry("BTC", "bitcoin"),
            Map.entry("ETH", "ethereum"),
            Map.entry("SOL", "solana"),
            Map.entry("ADA", "cardano"),
            Map.entry("DOT", "polkadot"),
            Map.entry("AVAX", "avalanche-2"),
            Map.entry("MATIC", "matic-network"),
            Map.entry("LINK", "chainlink"),
            Map.entry("UNI", "uniswap"),
            Map.entry("ATOM", "cosmos"),
            Map.entry("XRP", "ripple"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("LTC", "litecoin"),
            Map.entry("BNB", "binancecoin")
    );

    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Accept", "application/json")
                    .build();
        }
        return webClient;
    }

    /**
     * Converte ticker interno para CoinGecko ID.
     *
     * @param ticker ticker do ativo (ex: "BTC")
     * @return CoinGecko ID (ex: "bitcoin")
     */
    public String toCoingeckoId(String ticker) {
        return TICKER_TO_COINGECKO_ID.getOrDefault(ticker.toUpperCase(), ticker.toLowerCase());
    }

    /**
     * Busca cotação de criptomoedas em BRL.
     *
     * @param coinIds lista de CoinGecko IDs separados por vírgula (ex: "bitcoin,ethereum")
     * @return mapa de coinId → preço em BRL
     */
    public Map<String, BigDecimal> getPricesInBrl(String coinIds) {
        try {
            log.debug("Fetching crypto prices from CoinGecko for: {}", coinIds);

            Map<String, Map<String, Object>> response = getWebClient()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/simple/price")
                            .queryParam("ids", coinIds)
                            .queryParam("vs_currencies", "brl")
                            .queryParam("include_24hr_change", "true")
                            .queryParam("include_market_cap", "true")
                            .queryParam("include_last_updated_at", "true")
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {})
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null) {
                return Collections.emptyMap();
            }

            Map<String, BigDecimal> prices = new HashMap<>();
            response.forEach((coinId, data) -> {
                Object brlPrice = data.get("brl");
                if (brlPrice != null) {
                    prices.put(coinId, new BigDecimal(brlPrice.toString()));
                }
            });

            log.info("Successfully fetched {} crypto prices from CoinGecko", prices.size());
            return prices;
        } catch (Exception e) {
            log.error("Error fetching crypto prices from CoinGecko: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Busca cotação de uma criptomoeda pelo ticker interno do PrimeVest.
     *
     * @param ticker ticker interno (ex: "BTC")
     * @return preço em BRL, se encontrado
     */
    @Cacheable(value = "quotes", key = "'crypto_' + #ticker")
    public Optional<BigDecimal> getPriceByTicker(String ticker) {
        String coinId = toCoingeckoId(ticker);
        Map<String, BigDecimal> prices = getPricesInBrl(coinId);
        return Optional.ofNullable(prices.get(coinId));
    }

    /**
     * Busca cotações de múltiplas criptomoedas por tickers internos.
     *
     * @param tickers lista de tickers internos
     * @return mapa de ticker → preço em BRL
     */
    public Map<String, BigDecimal> getBatchPricesByTicker(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> coinIds = tickers.stream()
                .map(this::toCoingeckoId)
                .toList();

        String coinIdString = String.join(",", coinIds);
        Map<String, BigDecimal> coinIdPrices = getPricesInBrl(coinIdString);

        // Mapear de volta de CoinGecko ID para ticker interno
        Map<String, BigDecimal> tickerPrices = new HashMap<>();
        for (String ticker : tickers) {
            String coinId = toCoingeckoId(ticker);
            BigDecimal price = coinIdPrices.get(coinId);
            if (price != null) {
                tickerPrices.put(ticker, price);
            }
        }

        return tickerPrices;
    }
}
