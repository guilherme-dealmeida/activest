package com.primevest.adm.integration.coingecko;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente da CoinGecko (API publica, sem chave) — criptomoedas.
 *
 * <p>O ativo e identificado pelo id da CoinGecko ({@code bitcoin},
 * {@code ethereum}), que mora em {@code assets.price_source_ref} (V19). Antes
 * havia aqui um mapa fixo de 14 moedas, com um id vencido
 * (MATIC → {@code matic-network}, parado desde 02/2026); moeda fora do mapa
 * virava o ticker em minusculas e nunca tinha preco.</p>
 *
 * <p>Erro sobe como {@link CoinGeckoException} — o {@code catch} que devolvia
 * mapa vazio escondia a falha do log (PLANO_BRAPI.md, 5.4).</p>
 */
@Slf4j
@Component
public class CoinGeckoClient {

    /** A CoinGecko aceita muitos ids por chamada; 100 mantem a URL curta. */
    private static final int IDS_PER_REQUEST = 100;

    private final WebClient webClient;
    private final Duration timeout;

    @Autowired
    public CoinGeckoClient(WebClient.Builder builder,
                           @Value("${coingecko.base-url}") String baseUrl,
                           @Value("${coingecko.timeout-seconds:20}") long timeoutSeconds) {
        this.webClient = builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Preco em reais de cada moeda.
     *
     * @param coinIds ids da CoinGecko
     * @return id → preco em BRL; moeda sem preco na resposta fica de fora
     */
    public Map<String, BigDecimal> fetchPricesInBrl(Collection<String> coinIds) {
        Map<String, BigDecimal> prices = new HashMap<>();
        for (List<String> batch : partition(coinIds)) {
            Map<String, Map<String, Object>> response = call(() -> webClient.get()
                    .uri(uri -> uri.path("/simple/price")
                            .queryParam("ids", String.join(",", batch))
                            .queryParam("vs_currencies", "brl")
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {})
                    .timeout(timeout)
                    .block());
            if (response == null) {
                continue;
            }
            response.forEach((id, data) -> {
                Object brl = data != null ? data.get("brl") : null;
                if (brl != null) {
                    prices.put(id, new BigDecimal(brl.toString()));
                }
            });
        }
        return prices;
    }

    /**
     * Endereco da imagem (logo, tamanho "large") de cada moeda, para a rotina de
     * logos baixar e guardar do nosso lado.
     */
    public Map<String, String> fetchImageUrls(Collection<String> coinIds) {
        Map<String, String> images = new HashMap<>();
        for (List<String> batch : partition(coinIds)) {
            List<Market> markets = call(() -> webClient.get()
                    .uri(uri -> uri.path("/coins/markets")
                            .queryParam("vs_currency", "brl")
                            .queryParam("ids", String.join(",", batch))
                            .queryParam("per_page", 250)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Market>>() {})
                    .timeout(timeout)
                    .block());
            if (markets != null) {
                markets.stream()
                        .filter(m -> m.id() != null && m.image() != null)
                        .forEach(m -> images.put(m.id(), m.image()));
            }
        }
        return images;
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (WebClientResponseException e) {
            throw new CoinGeckoException(e.getStatusCode().value(),
                    "CoinGecko " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new CoinGeckoException(0, "Falha de rede na CoinGecko: " + cause);
        }
    }

    private static List<List<String>> partition(Collection<String> ids) {
        List<String> list = ids.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        List<List<String>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += IDS_PER_REQUEST) {
            parts.add(list.subList(i, Math.min(i + IDS_PER_REQUEST, list.size())));
        }
        return parts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Market(String id, String symbol, String image) {
    }
}
