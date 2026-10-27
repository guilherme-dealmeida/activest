package com.activest.domain.fund;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.asset.PriceSource;
import com.activest.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cota de um fundo numa data, para pré-preencher o Preço da Nova transação (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md,
 * C6 e D23): a série gravada, quando já existe; senão o informe da CVM do mês da data, lido na hora (pasta
 * {@code cvm/fi} montada só leitura — quem baixa é a adm-api). Sem cota na data (fim de semana, feriado, atraso), a
 * última anterior, com a data dela.
 */
@Slf4j
@Service
public class FundQuoteService {

    /** A cota e a data a que ela se refere. */
    public record Quote(LocalDate date, BigDecimal quote) {
    }

    private static final YearMonth FIRST_MONTHLY = YearMonth.of(2021, 1);

    private final AssetRepository assets;
    private final JdbcTemplate jdbc;
    private final Path dir;

    public FundQuoteService(AssetRepository assets, JdbcTemplate jdbc,
                            @Value("${activest.funds.dir:../carga/data/cvm/fi}") String dir) {
        this.assets = assets;
        this.jdbc = jdbc;
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    public Quote quote(String ticker, LocalDate date) {
        Asset asset = assets.findByTicker(ticker)
            .filter(a -> a.getAssetType() != null && a.getAssetType().getPriceSource() == PriceSource.CVM)
            .orElseThrow(() -> new ResourceNotFoundException("Fundo não encontrado"));
        Quote stored = jdbc.query("""
                SELECT reference_date, close_price FROM asset_price_history
                 WHERE asset_id = ? AND reference_date <= ? ORDER BY reference_date DESC LIMIT 1""",
                rs -> rs.next() ? new Quote(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)) : null,
                asset.getId(), java.sql.Date.valueOf(date));
        if (stored != null && stored.date().equals(date)) {
            return stored;
        }
        Quote fromFile = fromFiles(ticker, date);
        Quote best = latest(stored, fromFile);
        if (best == null && asset.getCurrentPrice() != null && asset.getPriceUpdatedAt() != null
                && !asset.getPriceUpdatedAt().toLocalDate().isAfter(date)) {
            best = new Quote(asset.getPriceUpdatedAt().toLocalDate(), asset.getCurrentPrice());
        }
        if (best == null) {
            throw new ResourceNotFoundException("Sem cota do fundo até " + date);
        }
        return best;
    }

    /** O arquivo do mês da data e, se ele não tiver cota até a data, o anterior. */
    private Quote fromFiles(String key, LocalDate date) {
        YearMonth month = YearMonth.from(date);
        for (int i = 0; i < 2; i++) {
            Path zip = file(month);
            if (zip != null) {
                Quote q = scan(zip, key, date);
                if (q != null) {
                    return q;
                }
            }
            month = month.isBefore(FIRST_MONTHLY) ? YearMonth.of(month.getYear() - 1, 12) : month.minusMonths(1);
        }
        return null;
    }

    private Path file(YearMonth month) {
        Path p = month.isBefore(FIRST_MONTHLY)
            ? dir.resolve("inf_diario").resolve("hist").resolve("inf_diario_fi_" + month.getYear() + ".zip")
            : dir.resolve("inf_diario").resolve("inf_diario_fi_" + month.getYear()
                + String.format("%02d", month.getMonthValue()) + ".zip");
        return Files.exists(p) ? p : null;
    }

    private static Quote scan(Path zip, String key, LocalDate date) {
        Quote[] best = {null};
        FundDailyReportReader.read(zip, key::equals, row -> {
            if (!FundDailyReportReader.usableQuote(row.quote()) || row.date().isAfter(date)) {
                return;
            }
            if (best[0] == null || row.date().isAfter(best[0].date())) {
                best[0] = new Quote(row.date(), row.quote().setScale(8, RoundingMode.HALF_EVEN));
            }
        });
        return best[0];
    }

    private static Quote latest(Quote a, Quote b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return b.date().isAfter(a.date()) ? b : a;
    }

    /**
     * Nome curto dos fundos que o usuário já lançou: ticker → rótulo. As telas mostram o rótulo onde os demais tipos
     * mostram o ticker (D3) — o ticker do fundo é o CNPJ.
     */
    public Map<String, String> labels(UUID userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT DISTINCT a.ticker, f.short_name
                  FROM portfolios p
                  JOIN portfolio_assets pa ON pa.portfolio_id = p.id
                  JOIN assets a ON a.id = pa.asset_id
                  JOIN investment_funds f ON f.asset_id = a.id
                 WHERE p.user_id = ?""", userId);
        return rows.stream().collect(Collectors.toMap(r -> (String) r.get("ticker"), r -> (String) r.get("short_name"),
            (x, y) -> x));
    }
}
