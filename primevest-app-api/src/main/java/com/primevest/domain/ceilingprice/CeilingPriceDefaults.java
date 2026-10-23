package com.primevest.domain.ceilingprice;

import com.primevest.domain.ceilingprice.dto.CeilingPriceDTOs.DefaultQuarter;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTOs.DefaultValue;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTOs.Defaults;
import com.primevest.domain.indicator.IndicatorService;
import com.primevest.domain.indicator.IndicatorService.CompanyFundamentals;
import com.primevest.domain.indicator.dto.IndicatorPointDTO;
import com.primevest.domain.issuer.IssuerKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Os padrões do Preço Teto (docs/planos/PLANO_PRECO_TETO.md, 5.2) — só leitura do catálogo. Servem para abrir um
 * cálculo novo, para o ↻ de cada campo e para o aviso "o padrão mudou". Nunca alteram o que está gravado.
 *
 * <ul>
 *   <li>Lucros: os 4 últimos trimestres consecutivos de {@code company_quarters.net_income_parent_q}; o trimestre
 *   que falta vem nulo, com o motivo.</li>
 *   <li>N, preço, ROE e payout: pelas mesmas regras da tela de Emissoras ({@link IndicatorService}). ROE e payout =
 *   média dos 3 últimos exercícios fechados (D3).</li>
 *   <li>Desconto: Selic meta vigente (D5). Perpetuidade 3% e margem 15%: {@code primevest.ceiling-price.*}.</li>
 * </ul>
 */
@Service
public class CeilingPriceDefaults {

    static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    static final int YEARS = 3;

    private final JdbcTemplate jdbc;
    private final IndicatorService indicators;
    private final com.primevest.domain.asset.AssetLogos logos;
    private final BigDecimal perpetualGrowth;
    private final BigDecimal safetyMargin;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CeilingPriceDefaults(JdbcTemplate jdbc, IndicatorService indicators,
                                com.primevest.domain.asset.AssetLogos logos,
                                @Value("${primevest.ceiling-price.perpetual-growth:3}") BigDecimal perpetualGrowth,
                                @Value("${primevest.ceiling-price.safety-margin:15}") BigDecimal safetyMargin) {
        this(jdbc, indicators, logos, perpetualGrowth, safetyMargin, Clock.system(ZONE));
    }

    CeilingPriceDefaults(JdbcTemplate jdbc, IndicatorService indicators, com.primevest.domain.asset.AssetLogos logos,
                         BigDecimal perpetualGrowth, BigDecimal safetyMargin, Clock clock) {
        this.jdbc = jdbc;
        this.indicators = indicators;
        this.logos = logos;
        this.perpetualGrowth = perpetualGrowth;
        this.safetyMargin = safetyMargin;
        this.clock = clock;
    }

    /** Ativo aceito pelo modelo: ação (D1) ligada a uma companhia com balanço na CVM. */
    record AssetInfo(long id, String ticker, String name, Long issuerId, String type, String kind) {
    }

    AssetInfo asset(String ticker) {
        String t = ticker == null ? "" : ticker.trim().toUpperCase();
        List<AssetInfo> found = jdbc.query("""
                SELECT a.id, a.ticker, a.name, a.issuer_id, t.name AS type, i.kind
                FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                LEFT JOIN issuers i ON i.id = a.issuer_id
                WHERE upper(a.ticker) = ?""",
                (rs, i) -> new AssetInfo(rs.getLong("id"), rs.getString("ticker"), rs.getString("name"),
                        (Long) rs.getObject("issuer_id", Long.class), rs.getString("type"), rs.getString("kind")), t);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo não encontrado: " + t);
        }
        AssetInfo a = found.get(0);
        if (!"ACAO".equals(a.type()) || a.issuerId() == null || !IssuerKind.COMPANY.name().equals(a.kind())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O Preço Teto é só para ações ligadas a uma companhia com balanço na CVM (" + a.ticker() + ").");
        }
        return a;
    }

    public Defaults of(String ticker, UUID existingId) {
        AssetInfo a = asset(ticker);
        CompanyFundamentals f = indicators.companyFundamentals(a.ticker());
        List<QuarterRow> rows = quarterRows(a.issuerId());
        List<DefaultQuarter> quarters = window(rows);

        // N: ações sem tesouraria ajustadas; a unit vale um grupo de ações
        DefaultValue shares;
        if (f.sharesAdjusted() == null) {
            shares = new DefaultValue(null, null, f.sharesNote() != null ? f.sharesNote()
                    : "sem quantidade de ações no balanço");
        } else {
            long n = f.unitShares() > 1 ? Math.round((double) f.sharesAdjusted() / f.unitShares()) : f.sharesAdjusted();
            String ref = "ações sem tesouraria, balanço de " + fmt(f.sharesPeriodEnd())
                    + (f.unitShares() > 1 ? " ÷ " + f.unitShares() + " (ações por unit)" : "")
                    + (f.sharesNote() != null ? " — " + f.sharesNote() : "");
            shares = new DefaultValue(BigDecimal.valueOf(n), ref, null);
        }

        List<IndicatorPointDTO> roeYears = lastYears(f.roeYears());
        List<IndicatorPointDTO> payoutYears = lastYears(f.payoutYears());
        DefaultValue roe = average(roeYears, "ROE");
        DefaultValue payout = average(payoutYears, "payout (dividendos + JCP pagos ÷ lucro)");

        DefaultValue discount = selicTarget();
        DefaultValue growth = new DefaultValue(perpetualGrowth, "definido no sistema (crescimento médio do PIB do Brasil)", null);
        DefaultValue margin = new DefaultValue(safetyMargin, "definido no sistema", null);
        DefaultValue price = f.price() == null
                ? new DefaultValue(null, null, "sem cotação no catálogo")
                : new DefaultValue(f.price(), "fechamento de " + fmt(f.priceDate()), null);

        BigDecimal catalogTtm = rows.isEmpty() ? null : rows.get(rows.size() - 1).ttm();
        return new Defaults(a.ticker(), a.name(), a.issuerId(), logos.hasLogo(a.ticker()), existingId, quarters,
                shares, roe, payout, discount, growth, margin, price, f.priceDate(),
                rows.isEmpty() ? null : rows.get(rows.size() - 1).periodEnd(), f.equityParent(), catalogTtm,
                roeYears.stream().map(this::yearLabel).toList(), payoutYears.stream().map(this::yearLabel).toList());
    }

    // ------------------------------------------------------------------ trimestres

    record QuarterRow(LocalDate periodEnd, BigDecimal value, BigDecimal ttm, String docType, int version,
                      LocalDate receivedAt, boolean consolidated) {
    }

    List<QuarterRow> quarterRows(long issuerId) {
        return jdbc.query("""
                SELECT q.period_end, q.net_income_parent_q, q.net_income_parent_ttm, d.doc_type, d.version,
                       d.received_at, d.consolidated
                FROM company_quarters q JOIN cvm_documents d ON d.id = q.source_doc_id
                WHERE q.issuer_id = ? ORDER BY q.period_end""",
                (rs, i) -> new QuarterRow(rs.getDate("period_end").toLocalDate(),
                        rs.getBigDecimal("net_income_parent_q"), rs.getBigDecimal("net_income_parent_ttm"),
                        rs.getString("doc_type"), rs.getInt("version"),
                        rs.getDate("received_at") == null ? null : rs.getDate("received_at").toLocalDate(),
                        rs.getBoolean("consolidated")), issuerId);
    }

    /**
     * Os 4 trimestres que terminam no último balanço, consecutivos: cada um é o fim de trimestre anterior ao
     * seguinte (±10 dias, como o exercício fora do calendário). O que falta vem nulo com o motivo.
     */
    static List<DefaultQuarter> window(List<QuarterRow> rows) {
        List<DefaultQuarter> out = new ArrayList<>();
        if (rows.isEmpty()) {
            return out;
        }
        LocalDate end = rows.get(rows.size() - 1).periodEnd();
        LocalDate[] dates = new LocalDate[4];
        dates[3] = end;
        for (int k = 2; k >= 0; k--) {
            dates[k] = dates[k + 1].minusMonths(3).withDayOfMonth(1).plusMonths(1).minusDays(1);
        }
        for (int k = 0; k < 4; k++) {
            LocalDate target = dates[k];
            QuarterRow row = rows.stream()
                    .filter(r -> !r.periodEnd().isBefore(target.minusDays(10)) && !r.periodEnd().isAfter(target.plusDays(10)))
                    .findFirst().orElse(null);
            LocalDate date = row != null ? row.periodEnd() : target;
            String label = label(date);
            if (row == null) {
                out.add(new DefaultQuarter(date, label, null, null,
                        "a CVM não tem o balanço do " + label + " desta companhia"));
            } else if (row.value() == null) {
                out.add(new DefaultQuarter(date, label, null, docRef(row),
                        "o lucro isolado do " + label + " não pôde ser calculado (falta o documento anterior)"));
            } else {
                out.add(new DefaultQuarter(date, label, row.value(), docRef(row), null));
            }
        }
        return out;
    }

    static String docRef(QuarterRow r) {
        return "CVM " + r.docType() + " " + fmt(r.periodEnd()) + " v" + r.version()
                + (r.receivedAt() != null ? ", entregue em " + fmt(r.receivedAt()) : "")
                + (r.consolidated() ? "" : " (individual)")
                + ("DFP".equals(r.docType()) ? " — 4º trimestre = ano − 9 meses" : "");
    }

    static String label(LocalDate d) {
        return ((d.getMonthValue() - 1) / 3 + 1) + "T" + String.format("%02d", d.getYear() % 100);
    }

    // ------------------------------------------------------------------ ROE e payout

    /** Os {@value #YEARS} exercícios fechados mais recentes (DFP), do mais antigo ao mais novo. */
    static List<IndicatorPointDTO> lastYears(List<IndicatorPointDTO> years) {
        int from = Math.max(0, years.size() - YEARS);
        return years.subList(from, years.size());
    }

    static DefaultValue average(List<IndicatorPointDTO> years, String name) {
        List<IndicatorPointDTO> present = years.stream().filter(p -> p.value() != null).toList();
        if (present.isEmpty()) {
            return new DefaultValue(null, null, "sem " + name + " nos últimos exercícios fechados"
                    + (years.isEmpty() ? "" : " (" + years.stream().map(p -> p.label() + ": " + p.note())
                    .reduce((x, y) -> x + "; " + y).orElse("") + ")"));
        }
        BigDecimal sum = present.stream().map(IndicatorPointDTO::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
        String parts = present.stream().map(p -> p.label() + ": " + p.value().stripTrailingZeros().toPlainString()
                .replace('.', ',') + "%").reduce((x, y) -> x + "; " + y).orElse("");
        String ref = "média " + present.get(0).label() + "–" + present.get(present.size() - 1).label()
                + " (" + parts + ")";
        String note = present.size() < YEARS ? "média de " + present.size() + " exercício(s): a companhia não tem "
                + YEARS + " anos com o indicador" : null;
        return new DefaultValue(avg, ref, note);
    }

    private String yearLabel(IndicatorPointDTO p) {
        return p.label() + "=" + (p.value() == null ? "—" : p.value().toPlainString());
    }

    // ------------------------------------------------------------------ índices

    DefaultValue selicTarget() {
        List<Map<String, Object>> r = jdbc.queryForList("""
                SELECT v.reference_date, v.value FROM market_index_values v
                JOIN market_indices i ON i.id = v.index_id
                WHERE i.code = 'SELIC_META' AND v.reference_date <= ?
                ORDER BY v.reference_date DESC LIMIT 1""", java.sql.Date.valueOf(LocalDate.now(clock)));
        if (r.isEmpty()) {
            return new DefaultValue(null, null, "Selic meta não carregada (índices de mercado)");
        }
        LocalDate d = ((java.sql.Date) r.get(0).get("reference_date")).toLocalDate();
        BigDecimal v = (BigDecimal) r.get(0).get("value");
        return new DefaultValue(v.stripTrailingZeros().setScale(Math.max(2, v.stripTrailingZeros().scale()),
                RoundingMode.UNNECESSARY), "Selic meta vigente em " + fmt(d), null);
    }

    /** PTAX de venda do dia (ou do último dia útil anterior); nulo sem série. */
    public BigDecimal ptax(LocalDate date) {
        List<BigDecimal> r = jdbc.queryForList("""
                SELECT v.value FROM market_index_values v JOIN market_indices i ON i.id = v.index_id
                WHERE i.code = 'USD_PTAX' AND v.reference_date <= ? ORDER BY v.reference_date DESC LIMIT 1""",
                BigDecimal.class, java.sql.Date.valueOf(date));
        return r.isEmpty() ? null : r.get(0);
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    static String fmt(LocalDate d) {
        return d == null ? "—" : d.format(BR);
    }

    static boolean same(BigDecimal a, BigDecimal b) {
        return a != null && b != null ? a.compareTo(b) == 0 : Objects.equals(a, b);
    }
}
