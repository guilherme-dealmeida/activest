package com.activest.adm.integration.b3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Cliente do site da B3 ({@code sistemaswebb3-listados.b3.com.br}) — proventos
 * e eventos societarios, sem token (docs/planos/PLANO_BRAPI.md, F5).
 *
 * <p>O parametro de cada chamada e um JSON em base64 no fim da URL, como o
 * proprio site da B3 faz. Quatro endpoints, testados em 24/09/2026:</p>
 * <ul>
 *   <li>{@code GetListedSupplementCompany} — empresa (acao, unit, BDR): ~12
 *   meses de proventos + eventos societarios, por emissor (PETR);</li>
 *   <li>{@code GetListedSupplementFunds} — FII, ETF e Fiagro, pelo codigo (MXRF);</li>
 *   <li>{@code GetDetail} — ticker ↔ ISIN de uma empresa, pelo codigo CVM;</li>
 *   <li>{@code GetListedCashDividends} — historico longo de empresa, paginado.</li>
 * </ul>
 *
 * <p><b>Risco:</b> sao os endpoints que o site usa, sem documentacao — podem
 * mudar sem aviso. Por isso o cliente e isolado aqui e os testes usam respostas
 * reais gravadas: se o formato mudar, o teste quebra antes de gravar dado
 * errado. Resposta vazia (e o que a B3 devolve para parametro invalido) vira
 * {@link B3Exception}.</p>
 */
@Component
public class B3Client {

    private final WebClient webClient;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    @Autowired
    public B3Client(WebClient.Builder builder,
                    @Value("${b3.base-url:https://sistemaswebb3-listados.b3.com.br}") String baseUrl,
                    @Value("${b3.timeout-seconds:40}") long timeoutSeconds,
                    ObjectMapper objectMapper) {
        this.webClient = builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.objectMapper = objectMapper;
    }

    /** Proventos recentes e eventos de uma empresa. Nulo = a B3 nao conhece o emissor. */
    public B3Supplement companySupplement(String issuer) {
        JsonNode node = call("/listedCompaniesProxy/CompanyCall/GetListedSupplementCompany/",
                "{\"issuingCompany\":\"" + issuer + "\",\"language\":\"pt-br\"}");
        JsonNode first = node.isArray() ? (node.isEmpty() ? null : node.get(0)) : node;
        return first == null ? null : convert(first, B3Supplement.class);
    }

    /** Proventos recentes e eventos de um fundo listado (FII, ETF, Fiagro). */
    public B3Supplement fundSupplement(String code) {
        JsonNode node = call("/fundsProxy/fundsCall/GetListedSupplementFunds/",
                "{\"cnpj\":\"0\",\"identifierFund\":\"" + code + "\",\"typeFund\":7}");
        return node.isObject() ? convert(node, B3Supplement.class) : null;
    }

    /** Cadastro da empresa, com o ISIN de cada ticker. */
    public B3CompanyDetail companyDetail(String codeCvm) {
        JsonNode node = call("/listedCompaniesProxy/CompanyCall/GetDetail/",
                "{\"codeCVM\":\"" + codeCvm + "\",\"language\":\"pt-br\"}");
        return convert(node, B3CompanyDetail.class);
    }

    /** Uma pagina do historico longo de proventos de uma empresa. */
    public B3HistoryPage cashDividendsHistory(String tradingName, int page, int pageSize) {
        JsonNode node = call("/listedCompaniesProxy/CompanyCall/GetListedCashDividends/",
                "{\"language\":\"pt-br\",\"pageNumber\":" + page + ",\"pageSize\":" + pageSize
                        + ",\"tradingName\":\"" + tradingName.trim() + "\"}");
        return convert(node, B3HistoryPage.class);
    }

    private JsonNode call(String path, String json) {
        String param = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String body;
        try {
            body = webClient.get()
                    .uri(path + param)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();
        } catch (WebClientResponseException e) {
            throw new B3Exception("B3 " + e.getStatusCode().value() + " em " + path + " " + json);
        } catch (Exception e) {
            throw new B3Exception("Falha de rede na B3 em " + path + " " + json + ": " + e.getMessage(), e);
        }
        if (body == null || body.isBlank()) {
            throw new B3Exception("B3 respondeu vazio em " + path + " " + json);
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            // Com Accept: application/json a B3 devolve o JSON embrulhado numa
            // string JSON ("[{\"stockCapital\"...}]"). Desembrulha uma vez.
            if (node != null && node.isTextual()) {
                String inner = node.asText();
                if (inner.isBlank()) {
                    throw new B3Exception("B3 respondeu vazio em " + path + " " + json);
                }
                node = objectMapper.readTree(inner);
            }
            return node;
        } catch (B3Exception e) {
            throw e;
        } catch (Exception e) {
            throw new B3Exception("B3 respondeu fora do formato esperado em " + path + ": " + e.getMessage(), e);
        }
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (Exception e) {
            throw new B3Exception("Resposta da B3 fora do formato de " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
