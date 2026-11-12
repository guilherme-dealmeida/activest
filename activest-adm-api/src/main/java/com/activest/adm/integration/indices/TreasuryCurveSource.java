package com.activest.adm.integration.indices;

import com.activest.adm.integration.treasury.TreasuryPriceClient;
import com.activest.domain.marketindex.IndexPoint;
import com.activest.domain.marketindex.YieldCurve;
import com.activest.domain.marketindex.YieldCurve.Bond;
import com.activest.domain.marketindex.YieldCurve.Curve;
import com.activest.domain.marketindex.YieldCurve.Vertex;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Curva de juros do Tesouro por prazo (docs/planos/PLANO_JUROS_FUTUROS.md, J2): a "fonte" dos indices {@code TESOURO}.
 * Nao baixa nada — le o arquivo de precos e taxas que a rotina {@code TREASURY_PRICES} ja guarda em
 * {@code carga/data/tesouro} e calcula a taxa de cada prazo com {@link YieldCurve}.
 *
 * <p>O arquivo (~14 MB) e lido uma vez e fica em memoria enquanto nao muda (data de modificacao e tamanho): os 9
 * prazos x 17 anos de uma carga historica usam a mesma leitura. O CDI do dia (ancora da prefixada, I4) vem da serie
 * ja gravada — por isso a rodada processa o CDI antes (ordem por id).</p>
 */
@Slf4j
@Component
public class TreasuryCurveSource {

    private final TreasuryPriceClient treasury;
    private final JdbcTemplate jdbc;
    private Snapshot snapshot;

    /** Os titulos das duas curvas por dia, e de qual versao do arquivo vieram. */
    private record Snapshot(long modified, long size, NavigableMap<LocalDate, List<Bond>> days) {
    }

    public TreasuryCurveSource(TreasuryPriceClient treasury, JdbcTemplate jdbc) {
        this.treasury = treasury;
        this.jdbc = jdbc;
    }

    /**
     * Taxas do prazo {@code sourceRef} ({@code PRE:5}) em cada dia de {@code year} com titulos no arquivo; dia em que o
     * prazo fica fora dos titulos nao entra (sem extrapolacao).
     *
     * @throws IndexSourceException arquivo do Tesouro ausente ou ilegivel
     */
    public List<IndexPoint> year(String sourceRef, int year) {
        Vertex vertex = Vertex.parse(sourceRef);
        NavigableMap<LocalDate, List<Bond>> days = days().subMap(LocalDate.of(year, 1, 1), true,
                LocalDate.of(year, 12, 31), true);
        if (days.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, BigDecimal> cdi = vertex.curve() == Curve.PRE ? cdi(days.firstKey(), days.lastKey()) : Map.of();
        List<IndexPoint> out = new ArrayList<>();
        days.forEach((date, bonds) -> {
            BigDecimal daily = cdi.get(date);
            Optional<BigDecimal> rate = YieldCurve.rate(vertex, date, bonds,
                    daily == null ? null : YieldCurve.annualize(daily));
            rate.ifPresent(r -> out.add(new IndexPoint(date, r)));
        });
        return out;
    }

    private synchronized NavigableMap<LocalDate, List<Bond>> days() {
        Path file = treasury.file();
        if (!Files.isRegularFile(file)) {
            throw new IndexSourceException("arquivo do Tesouro ainda nao baixado (" + file
                    + "): rode a tarefa TREASURY_PRICES");
        }
        long modified;
        long size;
        try {
            modified = Files.getLastModifiedTime(file).toMillis();
            size = Files.size(file);
        } catch (IOException e) {
            throw new IndexSourceException("arquivo do Tesouro ilegivel: " + e.getMessage(), e);
        }
        if (snapshot != null && snapshot.modified() == modified && snapshot.size() == size) {
            return snapshot.days();
        }
        NavigableMap<LocalDate, List<Bond>> days = new TreeMap<>();
        try {
            TreasuryPriceClient.read(file, r -> {
                if (Curve.PRE.includes(r.type()) || Curve.REAL.includes(r.type())) {
                    days.computeIfAbsent(r.date(), d -> new ArrayList<>())
                            .add(new Bond(r.type(), r.maturity(), r.buyRate(), r.sellRate()));
                }
            });
        } catch (UncheckedIOException e) {
            throw new IndexSourceException(e.getMessage(), e);
        }
        log.info("Curva do Tesouro: {} dias lidos de {} ({} a {})", days.size(), file.getFileName(),
                days.isEmpty() ? "-" : days.firstKey(), days.isEmpty() ? "-" : days.lastKey());
        snapshot = new Snapshot(modified, size, days);
        return days;
    }

    /** CDI diario gravado (% ao dia) entre as datas. */
    private Map<LocalDate, BigDecimal> cdi(LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> out = new HashMap<>();
        jdbc.query("SELECT v.reference_date, v.value FROM market_index_values v JOIN market_indices i ON i.id = v.index_id "
                        + "WHERE i.code = 'CDI' AND v.reference_date BETWEEN ? AND ?",
                rs -> { out.put(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)); },
                Date.valueOf(from), Date.valueOf(to));
        return out;
    }
}
