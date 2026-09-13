package com.primevest.integration.brapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Client para integração com a BrAPI (brapi.dev) – cotações de ativos da B3.
 *
 * <p>A BrAPI fornece cotações em tempo real de ações, FIIs, ETFs, BDRs e outros
 * ativos listados na B3.</p>
 *
 * <p>Endpoints utilizados:</p>
 * <ul>
 *   <li>GET /quote/{tickers} – cotação atual de um ou mais ativos</li>
 *   <li>GET /quote/{ticker}/dividends – histórico de dividendos</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrApiClient {

    @Value("${brapi.base-url}")
    private String baseUrl;

    @Value("${brapi.token:}")
    private String token;

    private WebClient webClient;

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
     * Busca cotação atual de um ou mais tickers da B3.
     *
     * @param tickers lista de tickers separados por vírgula (ex: "PETR4,VALE3,ITUB4")
     * @return lista de cotações
     */
    public List<BrApiQuoteResponse.BrApiQuote> getQuotes(String tickers) {
        try {
            log.debug("Fetching quotes from BrAPI for: {}", tickers);

            BrApiQuoteResponse response = getWebClient()
                    .get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/quote/{tickers}");
                        if (token != null && !token.isBlank()) {
                            builder.queryParam("token", token);
                        }
                        return builder.build(tickers);
                    })
                    .retrieve()
                    .bodyToMono(BrApiQuoteResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.getResults() != null) {
                log.info("Successfully fetched {} quotes from BrAPI", response.getResults().size());
                return response.getResults();
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching quotes from BrAPI for {}: {}", tickers, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Busca cotação de um único ticker.
     *
     * @param ticker ticker do ativo (ex: "PETR4")
     * @return cotação se encontrada
     */
    @Cacheable(value = "quotes", key = "#ticker")
    public Optional<BrApiQuoteResponse.BrApiQuote> getQuote(String ticker) {
        List<BrApiQuoteResponse.BrApiQuote> quotes = getQuotes(ticker);
        return quotes.stream().findFirst();
    }

    /**
     * Busca o preço atual de um ativo.
     *
     * @param ticker ticker do ativo
     * @return preço atual ou Optional.empty() se não encontrado
     */
    public Optional<BigDecimal> getCurrentPrice(String ticker) {
        return getQuote(ticker).map(BrApiQuoteResponse.BrApiQuote::getRegularMarketPrice);
    }

    /**
     * Busca cotações de múltiplos tickers de uma vez (batch).
     * A BrAPI suporta até ~20 tickers por request.
     *
     * @param tickers lista de tickers
     * @return lista de cotações
     */
    public List<BrApiQuoteResponse.BrApiQuote> getBatchQuotes(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return Collections.emptyList();
        }

        // BrAPI aceita tickers separados por vírgula
        String tickerString = String.join(",", tickers);
        return getQuotes(tickerString);
    }

    /**
     * Busca histórico de dividendos de um ativo.
     *
     * @param ticker ticker do ativo
     * @return resposta com lista de dividendos
     */
    public Optional<BrApiDividendResponse> getDividends(String ticker) {
        try {
            log.debug("Fetching dividends from BrAPI for: {}", ticker);

            BrApiDividendResponse response = getWebClient()
                    .get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/quote/{ticker}")
                                .queryParam("dividends", "true")
                                .queryParam("range", "1y");
                        if (token != null && !token.isBlank()) {
                            builder.queryParam("token", token);
                        }
                        return builder.build(ticker);
                    })
                    .retrieve()
                    .bodyToMono(BrApiDividendResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Error fetching dividends from BrAPI for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }
}
