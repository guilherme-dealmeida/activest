package com.activest.adm.domain.foreign;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Os ativos internacionais por ticker, para casar o que vem de uma fonte por data (preço, provento, desdobramento):
 * o vivo e os deslistados que ainda estavam na bolsa a partir de uma data. Ticker reaproveitado (V13): na data, vale o
 * deslistado de saída mais próxima ainda não passada; sem ele, o vivo (docs/planos/PLANO_PRECOS_HISTORICOS_INTERNACIONAIS.md
 * e PLANO_PROVENTOS_INTERNACIONAIS.md).
 */
public final class ForeignListings {

    private static final String QUERY = """
            SELECT a.id, a.ticker, t.name AS type_name, a.delisted_on FROM assets a
            JOIN asset_types t ON t.id = a.asset_type_id
            WHERE t.price_source = 'MASSIVE'
              AND ((a.active AND a.delisted_on IS NULL) OR a.delisted_on >= ?)
            """;

    private ForeignListings() {
    }

    /** Um papel do catálogo; {@code delistedOn} nulo = vivo. */
    public record Listed(long id, String ticker, String type, LocalDate delistedOn) {
    }

    /** Candidatos por ticker (maiúsculo): o vivo e os deslistados com saída a partir de {@code from}. */
    public static Map<String, List<Listed>> load(JdbcTemplate jdbc, LocalDate from) {
        Map<String, List<Listed>> out = new HashMap<>();
        jdbc.query(QUERY, rs -> {
            Date delisted = rs.getDate("delisted_on");
            Listed a = new Listed(rs.getLong("id"), rs.getString("ticker"), rs.getString("type_name"),
                    delisted == null ? null : delisted.toLocalDate());
            out.computeIfAbsent(a.ticker().toUpperCase(), k -> new ArrayList<>()).add(a);
        }, Date.valueOf(from));
        return out;
    }

    /** O papel listado em {@code d} entre os candidatos de um ticker; nulo se nenhum. */
    public static Listed pick(List<Listed> candidates, LocalDate d) {
        if (candidates == null) {
            return null;
        }
        Listed pick = null;
        for (Listed a : candidates) {
            if (a.delistedOn() == null) {
                if (pick == null) {
                    pick = a;
                }
            } else if (!a.delistedOn().isBefore(d)
                    && (pick == null || pick.delistedOn() == null || a.delistedOn().isBefore(pick.delistedOn()))) {
                pick = a;
            }
        }
        return pick;
    }

    /** O papel listado em {@code d} para cada ticker. */
    public static Map<String, Listed> listedOn(Map<String, List<Listed>> catalog, LocalDate d) {
        Map<String, Listed> out = new HashMap<>(catalog.size() * 2);
        catalog.forEach((ticker, candidates) -> {
            Listed pick = pick(candidates, d);
            if (pick != null) {
                out.put(ticker, pick);
            }
        });
        return out;
    }
}
