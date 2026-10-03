package com.primevest.domain.pricehistory;

import com.primevest.domain.corporateaction.StockEventType;
import com.primevest.domain.pricehistory.PriceSeries.Event;
import com.primevest.domain.pricehistory.PriceSeries.Interval;
import com.primevest.domain.pricehistory.PriceSeries.Range;
import com.primevest.domain.pricehistory.dto.PriceHistoryDTO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Cotacao historica para o grafico da tela de emissoras (PLANO_TELA_EMISSORES.md, 2.3, B5, D9).
 *
 * <p>Le {@code asset_price_history} (COTAHIST desde 2010 + fechamento diario da BRAPI) e
 * {@code asset_stock_events}; ajusta por desdobramento, grupamento e bonificacao e entrega os
 * pontos na densidade da janela (diario ate ~1 ano, semanal ate ~5, mensal acima). Fracionario
 * (PETR4F) usa o lote padrao. Hoje so ha fechamento: {@code closeOnly = true} e o "1D" traz o
 * fechamento anterior e o ultimo; o {@link Interval#INTRADAY} fica para quando houver preco
 * durante o pregao.</p>
 */
@Service
@Transactional(readOnly = true)
public class AssetPriceHistoryService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final JdbcTemplate jdbc;

    public AssetPriceHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Serie do ticker. {@code range} (1D, 7D, 1M, 6M, YTD, 1A, 5A, MAX) ou o periodo especifico
     * {@code from}/{@code to}; sem nenhum dos dois, 1A.
     */
    public PriceHistoryDTO history(String ticker, String range, LocalDate from, LocalDate to) {
        String t = ticker == null ? "" : ticker.trim().toUpperCase();
        List<Long> ids = jdbc.queryForList("SELECT id FROM assets WHERE upper(ticker) = ?", Long.class, t);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo nao encontrado: " + t);
        }
        String priceTicker = t;
        long assetId = ids.get(0);
        String base = baseTicker(t);
        if (!base.equals(t)) {
            List<Long> baseIds = jdbc.queryForList("SELECT id FROM assets WHERE upper(ticker) = ?", Long.class, base);
            if (!baseIds.isEmpty()) {
                assetId = baseIds.get(0);
                priceTicker = base;
            }
        }
        boolean custom = from != null || to != null;
        Range r = null;
        if (!custom) {
            try {
                r = range == null || range.isBlank() ? Range.Y1 : Range.parse(range);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + range);
            }
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data inicial depois da final");
        }
        String rangeLabel = custom ? "CUSTOM" : r.label();

        NavigableMap<LocalDate, BigDecimal> raw = closes(assetId);
        if (raw.isEmpty()) {
            return new PriceHistoryDTO(t, priceTicker, null, null, null, null, rangeLabel, Interval.DAY.name(),
                    false, true, null, null, null, null, List.of());
        }
        List<Event> events = events(assetId);
        NavigableMap<LocalDate, BigDecimal> adjusted = PriceSeries.adjust(raw, events);
        LocalDate first = raw.firstKey();
        LocalDate last = raw.lastKey();

        LocalDate start;
        LocalDate end;
        if (custom) {
            start = from == null || from.isBefore(first) ? first : from;
            end = to == null || to.isAfter(last) ? last : to;
        } else {
            start = PriceSeries.start(r, raw);
            end = last;
        }
        NavigableMap<LocalDate, BigDecimal> window = start.isAfter(end)
                ? new TreeMap<>() : adjusted.subMap(start, true, end, true);
        Interval interval = r == Range.D1 || r == Range.D7 ? Interval.DAY : PriceSeries.interval(start, end);
        List<PriceHistoryDTO.Point> points = PriceSeries.points(window, interval).stream()
                .map(e -> new PriceHistoryDTO.Point(e.getKey().toString(), e.getValue())).toList();

        BigDecimal change = null;
        BigDecimal changePct = null;
        BigDecimal min = null;
        BigDecimal max = null;
        if (!window.isEmpty()) {
            BigDecimal a = window.firstEntry().getValue();
            BigDecimal b = window.lastEntry().getValue();
            change = b.subtract(a).setScale(4, RoundingMode.HALF_UP);
            changePct = a.signum() == 0 ? null
                    : b.divide(a, MC).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            for (BigDecimal v : window.values()) {
                min = min == null || v.compareTo(min) < 0 ? v : min;
                max = max == null || v.compareTo(max) > 0 ? v : max;
            }
        }
        boolean adj = !window.isEmpty() && PriceSeries.adjustedIn(window.firstKey(), window.lastKey(), last, events);
        return new PriceHistoryDTO(t, priceTicker, first, last, window.isEmpty() ? start : window.firstKey(),
                window.isEmpty() ? end : window.lastKey(), rangeLabel, interval.name(), adj, true, change, changePct,
                min, max, points);
    }

    /** PETR4F -> PETR4: o fracionario e o mesmo papel. */
    static String baseTicker(String ticker) {
        String t = ticker.trim().toUpperCase();
        return t.endsWith("F") && t.length() > 5 && Character.isDigit(t.charAt(t.length() - 2))
                ? t.substring(0, t.length() - 1) : t;
    }

    private NavigableMap<LocalDate, BigDecimal> closes(long assetId) {
        NavigableMap<LocalDate, BigDecimal> out = new TreeMap<>();
        jdbc.query("""
                SELECT reference_date, close_price FROM asset_price_history
                WHERE asset_id = ? AND close_price IS NOT NULL AND close_price > 0 ORDER BY reference_date""",
                rs -> { out.put(rs.getDate("reference_date").toLocalDate(), rs.getBigDecimal("close_price")); },
                assetId);
        return out;
    }

    private List<Event> events(long assetId) {
        return jdbc.query("SELECT data_com, type, factor FROM asset_stock_events WHERE asset_id = ? ORDER BY data_com",
                (rs, i) -> new Event(rs.getDate("data_com").toLocalDate(), type(rs.getString("type")),
                        rs.getBigDecimal("factor")), assetId);
    }

    private static StockEventType type(String name) {
        try {
            return name == null ? null : StockEventType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
