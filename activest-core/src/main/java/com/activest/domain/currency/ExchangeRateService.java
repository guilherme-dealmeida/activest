package com.activest.domain.currency;

import com.activest.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Valor na moeda base na data D" — a peça única de conversão (docs/planos/PLANO_CADASTRO_MOEDAS.md, R9 e C1).
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Moeda base → câmbio 1.</li>
 *   <li>Outra moeda → boletim de fechamento do dia; sem boletim no dia, o <b>último boletim anterior carregado</b>,
 *       sem limite de dias (D27: o câmbio nunca fica indisponível; o atraso da fonte vira aviso ao administrador na
 *       adm-api, C1a). {@link Quote#rateDate()} diz qual boletim foi usado, para a tela marcar "câmbio de dd/mm".</li>
 *   <li>Data anterior ao primeiro boletim da série → sem câmbio ({@link Optional#empty()}); nunca zero, nunca
 *       estimado.</li>
 * </ul>
 *
 * <p>A série inteira de cada moeda fica em memória (o dólar desde 2010 são ~4.200 linhas) e é relida depois de
 * {@link #TTL} ou quando a adm-api avisa que o catálogo mudou ({@link #evict()}). Para séries diárias longas
 * (Rentabilidade, Metas) use {@link #series(String, RateSide)} e consulte em memória.</p>
 */
@Service
public class ExchangeRateService {

    static final Duration TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExchangeRateRepository rates;
    private final CurrencyRepository currencies;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private volatile String baseCode;

    public ExchangeRateService(ExchangeRateRepository rates, CurrencyRepository currencies) {
        this.rates = rates;
        this.currencies = currencies;
    }

    /** O câmbio usado: valor, a data pedida e a data do boletim (diferentes quando o câmbio foi carregado). */
    public record Quote(String currencyCode, BigDecimal rate, LocalDate requestedDate, LocalDate rateDate) {
        /** Sem boletim na data pedida: usou o último anterior. Moeda base nunca é "carregada". */
        public boolean carried() {
            return !rateDate.equals(requestedDate);
        }
    }

    /** Série de uma moeda num lado, para consultas repetidas em memória (dia a dia da Rentabilidade). */
    public static final class Series {
        private final String currencyCode;
        private final NavigableMap<LocalDate, BigDecimal> points;
        private final boolean base;

        Series(String currencyCode, NavigableMap<LocalDate, BigDecimal> points, boolean base) {
            this.currencyCode = currencyCode;
            this.points = points;
            this.base = base;
        }

        public Optional<Quote> on(LocalDate date) {
            if (base) {
                return Optional.of(new Quote(currencyCode, BigDecimal.ONE, date, date));
            }
            Map.Entry<LocalDate, BigDecimal> e = points.floorEntry(date);
            return e == null ? Optional.empty() : Optional.of(new Quote(currencyCode, e.getValue(), date, e.getKey()));
        }

        /** Câmbio da data, ou nulo antes do primeiro boletim. */
        public BigDecimal rateOn(LocalDate date) {
            return on(date).map(Quote::rate).orElse(null);
        }

        public boolean isBase() {
            return base;
        }
    }

    public String baseCode() {
        String b = baseCode;
        if (b == null) {
            b = currencies.findByBaseTrue().map(Currency::getCode).orElse(Currency.BRL);
            baseCode = b;
        }
        return b;
    }

    public boolean isBase(String currencyCode) {
        return currencyCode == null || baseCode().equals(currencyCode);
    }

    /** Câmbio da data (C1). Vazio só antes do primeiro boletim da moeda. */
    public Optional<Quote> rateOn(String currencyCode, LocalDate date, RateSide side) {
        return series(currencyCode, side).on(date);
    }

    /** Como {@link #rateOn}, mas sem câmbio é erro de negócio (422) com a data na mensagem. */
    public Quote requireRateOn(String currencyCode, LocalDate date, RateSide side) {
        return rateOn(currencyCode, date, side).orElseThrow(() -> new BusinessException(
            "Câmbio de " + currencyCode + " não encontrado para " + date.format(BR) + "; informe a taxa."));
    }

    /** {@code amount} (na moeda) × câmbio da data, arredondado (HALF_EVEN, D26). Moeda base devolve o próprio valor. */
    public BigDecimal toBase(BigDecimal amount, String currencyCode, LocalDate date, RateSide side, int scale) {
        if (amount == null) {
            return null;
        }
        if (isBase(currencyCode)) {
            return amount.setScale(scale, RoundingMode.HALF_EVEN);
        }
        return amount.multiply(requireRateOn(currencyCode, date, side).rate()).setScale(scale, RoundingMode.HALF_EVEN);
    }

    /** Série inteira em memória (com cache). Moeda base = série "1" sem pontos. */
    public Series series(String currencyCode, RateSide side) {
        if (isBase(currencyCode)) {
            return new Series(baseCode(), new TreeMap<>(), true);
        }
        Cached c = cache.compute(currencyCode, (code, old) ->
            old != null && old.fresh() ? old : load(code));
        return new Series(currencyCode, side == RateSide.BUY ? c.buy : c.sell, false);
    }

    /** Último boletim gravado da moeda (tela de administrador, aviso de atraso). */
    public Optional<ExchangeRate> latest(String currencyCode) {
        return rates.findFirstByCurrencyCodeOrderByReferenceDateDesc(currencyCode);
    }

    /** Descarta o cache: chamado quando a adm-api grava câmbio novo ou o cadastro muda. */
    public void evict() {
        cache.clear();
        baseCode = null;
    }

    private Cached load(String code) {
        NavigableMap<LocalDate, BigDecimal> buy = new TreeMap<>();
        NavigableMap<LocalDate, BigDecimal> sell = new TreeMap<>();
        for (ExchangeRate r : rates.findByCurrencyCodeOrderByReferenceDate(code)) {
            buy.put(r.getReferenceDate(), r.getBuyRate());
            sell.put(r.getReferenceDate(), r.getSellRate());
        }
        return new Cached(java.util.Collections.unmodifiableNavigableMap(buy),
            java.util.Collections.unmodifiableNavigableMap(sell), Instant.now());
    }

    private record Cached(NavigableMap<LocalDate, BigDecimal> buy, NavigableMap<LocalDate, BigDecimal> sell,
                          Instant loadedAt) {
        boolean fresh() {
            return loadedAt.plus(TTL).isAfter(Instant.now());
        }
    }
}
