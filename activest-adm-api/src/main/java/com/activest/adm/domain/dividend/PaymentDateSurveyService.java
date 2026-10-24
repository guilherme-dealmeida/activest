package com.activest.adm.domain.dividend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Datas de pagamento levantadas (docs/planos/PLANO_CARGA_INICIAL.md, secao 11 — D14): o arquivo
 * {@code carga/data/levantamento/datas-pagamento.json}, gerado da planilha do levantamento
 * ({@code carga/scripts/levantamento/xlsx_para_json.py}), completa a data dos proventos de acao que a B3,
 * o FNET e a CVM nao trazem.
 *
 * <p>Casa cada linha pela chave natural — ticker (e o fracionario; sem ele, o ticker + "F"), tipo,
 * data-com, parcela ({@code seq}) e valor por acao (exato primeiro, senao o mais proximo dentro de
 * {@link #TOLERANCE}) — e grava so em evento <b>sem data</b>, com
 * {@code payment_date_source = 'LEVANTAMENTO'}. A data que vier depois da propria fonte do evento
 * substitui esta ({@code CashEventWriter}); a CVM roda antes no bootstrap e tambem tem precedencia.
 * Linha {@code A_DEFINIR} nao tem data e nao grava nada (D16/D17). Nada e apagado; rodar de novo so
 * completa o que falta.</p>
 */
@Slf4j
@Service
public class PaymentDateSurveyService {

    static final String FILE = "levantamento/datas-pagamento.json";
    static final BigDecimal TOLERANCE = new BigDecimal("0.000001");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CatalogChangePublisher catalogPublisher;
    private final Path file;
    private final ReentrantLock running = new ReentrantLock();

    @Autowired
    public PaymentDateSurveyService(JdbcTemplate jdbc, ObjectMapper mapper, CatalogChangePublisher catalogPublisher,
                                    @Value("${activest.data.dir:../carga/data}") String dataDir) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.catalogPublisher = catalogPublisher;
        this.file = Path.of(dataDir).resolve(FILE);
    }

    /** Uma linha do arquivo. {@code paymentDate} nula = A DEFINIR. */
    public record Line(String ticker, String fractional, String type, LocalDate dataCom, BigDecimal value,
                       short seq, LocalDate paymentDate) {
        /** Tickers onde o provento pode estar: o principal e o fracionario (ou o principal + "F"). */
        List<String> tickers() {
            String f = fractional != null && !fractional.isBlank() ? fractional : ticker + "F";
            return f.equals(ticker) ? List.of(ticker) : List.of(ticker, f);
        }
    }

    /** Um evento do catalogo candidato. */
    public record Event(long id, long assetId, String ticker, String type, LocalDate dataCom, short seq,
                        BigDecimal value, LocalDate paymentDate) {
    }

    /** O que casou: a linha e o evento. */
    public record Match(Line line, Event event) {
    }

    /** Casamento: pares linha-evento e as linhas sem evento correspondente. */
    public record Matching(List<Match> matches, List<Line> notFound) {
    }

    public record Result(int lines, int events, int filled, int alreadySame, int alreadyDifferent, int toBeDefined,
                         int notFound, Set<Long> assets) {
        static Result empty() {
            return new Result(0, 0, 0, 0, 0, 0, 0, Set.of());
        }

        @Override
        public String toString() {
            return lines + " linhas, " + events + " eventos casados: " + filled + " datas gravadas, "
                    + alreadySame + " ja tinham a mesma data, " + alreadyDifferent + " ja tinham outra data (mantida), "
                    + toBeDefined + " a definir, " + notFound + " linhas sem evento no catalogo";
        }
    }

    /** Aplica o arquivo. Sem arquivo: nada a fazer. Se outra rodada estiver em andamento, devolve {@code null}. */
    public Result run(String reason) {
        if (!running.tryLock()) {
            log.warn("Datas de pagamento do levantamento ({}): ja existe uma rodada em andamento; nada feito", reason);
            return null;
        }
        try {
            if (!Files.isRegularFile(file)) {
                log.warn("Datas de pagamento do levantamento ({}): {} nao existe (data pull?); nada feito", reason, file);
                return Result.empty();
            }
            List<Line> lines = read(file);
            Matching m = match(lines, load(lines));
            int same = 0;
            int different = 0;
            int toBeDefined = 0;
            List<Object[]> updates = new ArrayList<>();
            Map<Long, Long> assetOf = new HashMap<>();
            for (Match x : m.matches()) {
                LocalDate date = x.line().paymentDate();
                LocalDate current = x.event().paymentDate();
                if (date == null) {
                    toBeDefined++;
                } else if (current == null) {
                    updates.add(new Object[]{java.sql.Date.valueOf(date), x.event().id()});
                    assetOf.put(x.event().id(), x.event().assetId());
                } else if (current.equals(date)) {
                    same++;
                } else {
                    different++;
                    if (different <= 20) {
                        log.info("Datas de pagamento do levantamento: {} {} {} ja tem {} (levantamento {}); mantida",
                                x.event().ticker(), x.event().type(), x.event().dataCom(), current, date);
                    }
                }
            }
            int filled = 0;
            Set<Long> assets = new HashSet<>();
            if (!updates.isEmpty()) {
                int[][] r = jdbc.batchUpdate("""
                        UPDATE asset_cash_events SET payment_date = ?, payment_date_source = 'LEVANTAMENTO'
                        WHERE id = ? AND payment_date IS NULL""", updates, 500,
                        (ps, row) -> {
                            ps.setDate(1, (java.sql.Date) row[0]);
                            ps.setLong(2, (Long) row[1]);
                        });
                int i = 0;
                for (int[] batch : r) {
                    for (int n : batch) {
                        Long id = (Long) updates.get(i++)[1];
                        if (n > 0) {
                            filled++;
                            assets.add(assetOf.get(id));
                        }
                    }
                }
            }
            Result res = new Result(lines.size(), m.matches().size(), filled, same, different, toBeDefined,
                    m.notFound().size(), assets);
            log.info("Datas de pagamento do levantamento ({}) concluido: {}", reason, res);
            m.notFound().stream().limit(20).forEach(l -> log.info(
                    "Datas de pagamento do levantamento: sem evento para {} {} {} {}", l.ticker(), l.type(), l.dataCom(),
                    l.value().toPlainString()));
            if (!assets.isEmpty()) {
                catalogPublisher.publish(new CatalogChange(CatalogChange.Origin.PROVENTOS, assets));
            }
            return res;
        } finally {
            running.unlock();
        }
    }

    /** Le o arquivo ({@code {"linhas": [...]}}). */
    List<Line> read(Path path) {
        try {
            JsonNode root = mapper.readTree(path.toFile());
            List<Line> out = new ArrayList<>();
            for (JsonNode n : root.path("linhas")) {
                out.add(new Line(n.path("ticker").asText(), text(n, "ticker_fracionario"), n.path("tipo").asText(),
                        LocalDate.parse(n.path("data_com").asText()), new BigDecimal(n.path("valor_por_acao").asText()),
                        (short) n.path("parcela").asInt(1),
                        text(n, "data_pagamento") == null ? null : LocalDate.parse(n.path("data_pagamento").asText())));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Levantamento ilegivel: " + path + " (" + e.getMessage() + ")", e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    /** Eventos do catalogo nos tickers do arquivo. */
    private List<Event> load(List<Line> lines) {
        Set<String> tickers = new LinkedHashSet<>();
        lines.forEach(l -> tickers.addAll(l.tickers()));
        if (tickers.isEmpty()) {
            return List.of();
        }
        return jdbc.query(con -> {
            var ps = con.prepareStatement("""
                    SELECT e.id, e.asset_id, a.ticker, e.type, e.data_com, e.seq, e.value_per_share, e.payment_date
                    FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id
                    WHERE a.ticker = ANY (?) AND e.type IS NOT NULL""");
            ps.setArray(1, con.createArrayOf("text", tickers.toArray()));
            return ps;
        }, (rs, i) -> new Event(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getDate(5).toLocalDate(), rs.getShort(6), rs.getBigDecimal(7),
                rs.getDate(8) == null ? null : rs.getDate(8).toLocalDate()));
    }

    /**
     * Casa cada linha com o evento de cada ticker dela (principal e fracionario): mesma chave
     * (ticker, tipo, data-com, seq), valor exato ou o mais proximo dentro da tolerancia; cada evento
     * e usado uma vez.
     */
    static Matching match(List<Line> lines, List<Event> events) {
        Map<String, List<Event>> byKey = new HashMap<>();
        for (Event e : events) {
            byKey.computeIfAbsent(key(e.ticker(), e.type(), e.dataCom(), e.seq()), k -> new ArrayList<>()).add(e);
        }
        Set<Long> used = new HashSet<>();
        List<Match> matches = new ArrayList<>();
        List<Line> notFound = new ArrayList<>();
        for (Line l : lines) {
            boolean found = false;
            for (String t : l.tickers()) {
                Optional<Event> e = byKey.getOrDefault(key(t, l.type(), l.dataCom(), l.seq()), List.of()).stream()
                        .filter(x -> !used.contains(x.id()))
                        .filter(x -> x.value().subtract(l.value()).abs().compareTo(TOLERANCE) <= 0)
                        .min(Comparator.comparing((Event x) -> x.value().subtract(l.value()).abs())
                                .thenComparing(Event::id));
                if (e.isPresent()) {
                    used.add(e.get().id());
                    matches.add(new Match(l, e.get()));
                    found = true;
                }
            }
            if (!found) {
                notFound.add(l);
            }
        }
        return new Matching(matches, notFound);
    }

    private static String key(String ticker, String type, LocalDate dataCom, short seq) {
        return ticker + "|" + type + "|" + dataCom + "|" + seq;
    }
}
