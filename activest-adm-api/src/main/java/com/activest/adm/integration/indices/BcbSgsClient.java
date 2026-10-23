package com.activest.adm.integration.indices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.domain.marketindex.IndexPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * API SGS do Banco Central ({@code api.bcb.gov.br/dados/serie/bcdata.sgs.{codigo}/dados}) — CDI, Selic, IPCA,
 * IGP-M, INPC, TR, meta Selic e dolar PTAX (docs/planos/PLANO_INDICES_MERCADO.md, 2.3). Sem token.
 *
 * <p>Uma consulta por ano civil, sempre com {@code dataInicial}/{@code dataFinal}: serie diaria recusa janela
 * de mais de 10 anos (406) e a consulta de 10 anos do CDI chegou a passar de 15 s. Resposta:
 * {@code [{"data":"02/01/2024","valor":"0.043739"}]} (a TR traz tambem {@code dataFim} e repete o inicio com
 * fins diferentes: fica o periodo de um mes cheio); mensal vem datada no
 * dia 1o; 404 = nenhum valor no periodo. Cache por ano em {@code carga/data/bcb/sgs/{codigo}/{ano}.json}
 * ({@link IndexYearCache}).</p>
 */
@Component
public class BcbSgsClient {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final IndexYearCache cache;
    private final String baseUrl;
    private final Path dir;
    private final Clock clock;

    @Autowired
    public BcbSgsClient(@Value("${bcb.sgs-base-url:https://api.bcb.gov.br/dados/serie}") String baseUrl,
                        @Value("${activest.data.dir:../carga/data}") String dataDir,
                        @Value("${indices.timeout-seconds:20}") long timeoutSeconds,
                        @Value("${indices.attempts:4}") int attempts,
                        @Value("${indices.retry-base-millis:1000}") long retryBaseMillis) {
        this(baseUrl, Path.of(dataDir, "bcb", "sgs"),
                new IndexYearCache(Duration.ofSeconds(timeoutSeconds), attempts, retryBaseMillis), Clock.system(ZONE));
    }

    BcbSgsClient(String baseUrl, Path dir, IndexYearCache cache, Clock clock) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.dir = dir.toAbsolutePath().normalize();
        this.cache = cache;
        this.clock = clock;
    }

    /** Os valores da serie {@code code} no ano, em ordem de data. Ano corrente e anterior sempre vao a fonte. */
    public List<IndexPoint> year(String code, int year) {
        int current = LocalDate.now(clock).getYear();
        URI url = URI.create(baseUrl + "/bcdata.sgs." + code + "/dados?formato=json&dataInicial=01/01/" + year
                + "&dataFinal=31/12/" + year);
        String body = cache.fetch(url, dir.resolve(code).resolve(year + ".json"), year >= current - 1, "[]",
                BcbSgsClient::isArray);
        return parse(body);
    }

    static boolean isArray(String body) {
        try {
            return JSON.readTree(body).isArray();
        } catch (IOException e) {
            return false;
        }
    }

    static List<IndexPoint> parse(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            Map<LocalDate, IndexPoint> byDate = new TreeMap<>();
            Set<LocalDate> fullMonth = new HashSet<>();
            for (JsonNode n : root) {
                String date = n.path("data").asText("");
                String value = n.path("valor").asText("");
                if (date.isBlank() || value.isBlank()) {
                    continue;
                }
                LocalDate d = LocalDate.parse(date, BR);
                String end = n.path("dataFim").asText("");
                // TR: o mesmo inicio aparece com mais de um fim (01/05 a 31/05 e 01/05 a 01/06). Vale o periodo
                // de um mes cheio, o da poupanca com aniversario no dia; sem ele, o ultimo da lista.
                boolean isFullMonth = !end.isBlank() && LocalDate.parse(end, BR).equals(d.plusMonths(1));
                if (fullMonth.contains(d) && !isFullMonth) {
                    continue;
                }
                if (isFullMonth) {
                    fullMonth.add(d);
                }
                byDate.put(d, new IndexPoint(d, new BigDecimal(value.trim())));
            }
            return new ArrayList<>(byDate.values());
        } catch (IOException | RuntimeException e) {
            throw new IndexSourceException("Resposta do SGS ilegivel: " + e.getMessage(), e);
        }
    }
}
