package com.primevest.domain.indicator;

import com.primevest.domain.indicator.IndicatorRules.CompanyBasis;
import com.primevest.domain.indicator.IndicatorRules.FundBasis;
import com.primevest.domain.indicator.IndicatorRules.Value;
import com.primevest.domain.indicator.dto.AssetIndicatorsDTO;
import com.primevest.domain.indicator.dto.IndicatorHistoryDTO;
import com.primevest.domain.indicator.dto.IndicatorPointDTO;
import com.primevest.domain.issuer.IssuerKind;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Indicadores de acoes e FIIs (PLANO_EMPRESAS_BALANCOS.md, 5 e F9). <b>Nada e gravado:</b> le
 * {@code company_quarters}, {@code fii_monthly}, proventos e precos do catalogo e calcula na
 * leitura; as formulas estao em {@link IndicatorRules}.
 *
 * <ul>
 *   <li><b>Atual:</b> ultimo balanco (acao) ou ultimo informe (FII) com o preco atual
 *       ({@code assets.current_price}, senao o ultimo fechamento). A quantidade de acoes do
 *       balanco e ajustada pelos desdobramentos, grupamentos e bonificacoes posteriores.</li>
 *   <li><b>Ano:</b> acao — balanco do DFP com o fechamento do ultimo pregao ate a data do
 *       balanco; FII — informe de dezembro com o ultimo pregao do ano (a partir de 2017, o
 *       primeiro ano inteiro de avisos do FNET e de informe). Preco e quantidade sem ajuste:
 *       os dois sao da mesma data.</li>
 *   <li><b>Trimestre:</b> cada DFP/ITR (acao) ou os informes de marco, junho, setembro e
 *       dezembro (FII).</li>
 *   <li><b>DY:</b> proventos do proprio ticker (D5) — dividendo, JCP e rendimento, sem
 *       amortizacao nem restituicao de capital — com data-com nos 12 meses ate a data do
 *       ponto; provento de antes de um desdobramento e ajustado para a quantidade da data.
 *       Sem provento, 0.</li>
 *   <li><b>Fracionario</b> (PETR4F) usa o preco, os eventos e os proventos do lote padrao
 *       (D8); <b>unit</b> multiplica LPA e VPA pelas acoes da unit e usa os proprios proventos.</li>
 * </ul>
 */
@Service
public class IndicatorService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final MathContext MC = MathContext.DECIMAL64;
    /** Fechamento mais antigo que isso antes da data do ponto nao vale como preco da data. */
    private static final int MAX_PRICE_GAP_DAYS = 15;
    private static final int FIRST_FUND_YEAR = 2017;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final java.time.format.DateTimeFormatter BR = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public enum Period { YEAR, QUARTER }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public IndicatorService(JdbcTemplate jdbc) {
        this(jdbc, Clock.system(ZONE));
    }

    IndicatorService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ entradas

    public AssetIndicatorsDTO current(String ticker) {
        Context ctx = context(ticker);
        List<AssetIndicatorsDTO.Item> items = new ArrayList<>();
        LocalDate base;
        if (ctx.asset.kind == IssuerKind.COMPANY) {
            List<QuarterRow> quarters = quarters(ctx.asset.issuerId);
            CurrentCompany cc = currentCompany(ctx, quarters);
            base = cc.basis().periodEnd();
            for (Indicator i : Indicator.values()) {
                if (i.appliesTo(IssuerKind.COMPANY)) {
                    Value v = cc.value(i);
                    items.add(new AssetIndicatorsDTO.Item(i, i.unit(), v.value(), v.note()));
                }
            }
        } else {
            List<MonthRow> months = months(ctx.asset.issuerId);
            FundBasis basis = currentFund(ctx, months);
            base = basis.month() == null ? null : basis.month().plusMonths(1).minusDays(1);
            for (Indicator i : Indicator.values()) {
                if (i.appliesTo(IssuerKind.FII)) {
                    Value v = IndicatorRules.fund(i, basis);
                    items.add(new AssetIndicatorsDTO.Item(i, i.unit(), v.value(), v.note()));
                }
            }
        }
        return new AssetIndicatorsDTO(ctx.asset.ticker, ctx.asset.issuerId, ctx.asset.kind, ctx.currentPrice, base,
                items);
    }

    public IndicatorHistoryDTO history(String ticker, Indicator indicator, Period period) {
        Context ctx = context(ticker);
        if (!indicator.appliesTo(ctx.asset.kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    indicator + " nao se aplica a " + (ctx.asset.kind == IssuerKind.FII ? "FII" : "acao"));
        }
        IndicatorPointDTO current;
        List<IndicatorPointDTO> annual;
        List<IndicatorPointDTO> points;
        if (ctx.asset.kind == IssuerKind.COMPANY) {
            List<QuarterRow> quarters = quarters(ctx.asset.issuerId);
            current = point("Atual", currentCompany(ctx, quarters).value(indicator),
                    quarters.isEmpty() ? null : quarters.get(quarters.size() - 1).periodEnd);
            annual = companyPoints(ctx, quarters, indicator, Period.YEAR);
            points = period == Period.YEAR ? annual : companyPoints(ctx, quarters, indicator, Period.QUARTER);
        } else {
            List<MonthRow> months = months(ctx.asset.issuerId);
            FundBasis basis = currentFund(ctx, months);
            current = point("Atual", IndicatorRules.fund(indicator, basis),
                    basis.month() == null ? null : basis.month().plusMonths(1).minusDays(1));
            annual = fundPoints(ctx, months, indicator, Period.YEAR);
            points = period == Period.YEAR ? annual : fundPoints(ctx, months, indicator, Period.QUARTER);
        }
        List<BigDecimal> forAverage = new ArrayList<>(annual.stream().map(IndicatorPointDTO::value).toList());
        forAverage.add(current.value());
        return new IndicatorHistoryDTO(ctx.asset.ticker, indicator, indicator.unit(), period.name(), current,
                IndicatorRules.average(forAverage, indicator.unit()), points);
    }

    private static IndicatorPointDTO point(String label, Value v, LocalDate periodEnd) {
        return new IndicatorPointDTO(label, periodEnd, v.value(), v.note());
    }

    // ------------------------------------------------------------------ acoes

    /**
     * Base do "Atual". {@code sharesNote}: a quantidade de acoes veio de um balanco anterior
     * ao ultimo — o motivo vai junto dos indicadores que dependem dela.
     */
    record CurrentCompany(CompanyBasis basis, String sharesNote) {
        Value value(Indicator i) {
            Value v = IndicatorRules.company(i, basis);
            boolean usesShares = i == Indicator.LPA || i == Indicator.VPA || i == Indicator.PL || i == Indicator.PVP;
            return sharesNote != null && usesShares && v.value() != null ? new Value(v.value(), sharesNote) : v;
        }
    }

    private CurrentCompany currentCompany(Context ctx, List<QuarterRow> quarters) {
        LocalDate today = LocalDate.now(clock);
        BigDecimal proventos = ctx.proventos12m(today);
        if (quarters.isEmpty()) {
            return new CurrentCompany(new CompanyBasis(null, null, null, null, null, BigDecimal.ONE,
                    ctx.asset.unitShares, null, ctx.currentPrice, proventos), null);
        }
        QuarterRow last = quarters.get(quarters.size() - 1);
        CompanyBasis b = basis(last, quarters, ctx.ratio(last.periodEnd, today), ctx.asset.unitShares,
                ctx.currentPrice, proventos);
        if (last.sharesOutstanding != null && last.sharesOutstanding > 0) {
            return new CurrentCompany(b, null);
        }
        // Quantidade do ultimo balanco em revisao (erro na fonte, PENDENCIAS 18.11): vale a do
        // ultimo balanco conferido, desde que nenhum desdobramento, grupamento ou bonificacao
        // registrado entre os dois a tenha mudado — com evento no meio, fica nula.
        for (int k = quarters.size() - 2; k >= 0; k--) {
            QuarterRow prev = quarters.get(k);
            if (prev.sharesOutstanding == null || prev.sharesOutstanding <= 0) {
                continue;
            }
            if (ctx.ratio(prev.periodEnd, last.periodEnd).compareTo(BigDecimal.ONE) != 0) {
                break;
            }
            CompanyBasis fallback = new CompanyBasis(b.periodEnd(), b.netIncomeParentTtm(), b.equityParent(),
                    b.equityParentStart(), prev.sharesOutstanding, b.sharesRatio(), b.unitShares(),
                    b.dividendsDeclaredTtm(), b.price(), b.proventos12m());
            return new CurrentCompany(fallback, "quantidade de acoes do balanco de "
                    + prev.periodEnd.format(BR) + " (a do ultimo esta em revisao)");
        }
        return new CurrentCompany(b, null);
    }

    private List<IndicatorPointDTO> companyPoints(Context ctx, List<QuarterRow> quarters, Indicator indicator,
                                                  Period period) {
        List<IndicatorPointDTO> out = new ArrayList<>();
        for (QuarterRow q : quarters) {
            if (period == Period.YEAR && !q.annual) {
                continue;
            }
            CompanyBasis b = basis(q, quarters, BigDecimal.ONE, ctx.asset.unitShares, ctx.priceAt(q.periodEnd),
                    ctx.proventos12m(q.periodEnd));
            out.add(point(period == Period.YEAR ? String.valueOf(q.periodEnd.getYear()) : quarterLabel(q.periodEnd),
                    IndicatorRules.company(indicator, b), q.periodEnd));
        }
        return out;
    }

    private static CompanyBasis basis(QuarterRow q, List<QuarterRow> quarters, BigDecimal ratio, int unitShares,
                                      BigDecimal price, BigDecimal proventos) {
        QuarterRow start = yearBefore(q, quarters);
        return new CompanyBasis(q.periodEnd, q.netIncomeParentTtm, q.equityParent,
                start == null ? null : start.equityParent, q.sharesOutstanding, ratio, unitShares,
                q.dividendsDeclaredTtm, price, proventos);
    }

    /** O balanco de 12 meses antes (inicio do periodo do ROE), com folga de 20 dias. */
    private static QuarterRow yearBefore(QuarterRow q, List<QuarterRow> quarters) {
        LocalDate target = q.periodEnd.minusYears(1);
        for (QuarterRow r : quarters) {
            if (!r.periodEnd.isBefore(target.minusDays(20)) && !r.periodEnd.isAfter(target.plusDays(20))) {
                return r;
            }
        }
        return null;
    }

    static String quarterLabel(LocalDate d) {
        return ((d.getMonthValue() - 1) / 3 + 1) + "T" + String.format("%02d", d.getYear() % 100);
    }

    // ------------------------------------------------------------------ FII

    private FundBasis currentFund(Context ctx, List<MonthRow> months) {
        BigDecimal proventos = ctx.proventos12m(LocalDate.now(clock));
        MonthRow last = months.isEmpty() ? null : months.get(months.size() - 1);
        return fundBasis(last, ctx.currentPrice, proventos);
    }

    private List<IndicatorPointDTO> fundPoints(Context ctx, List<MonthRow> months, Indicator indicator,
                                               Period period) {
        List<IndicatorPointDTO> out = new ArrayList<>();
        for (MonthRow m : months) {
            int month = m.month.getMonthValue();
            if (m.month.getYear() < FIRST_FUND_YEAR || (period == Period.YEAR ? month != 12 : month % 3 != 0)) {
                continue;
            }
            LocalDate end = m.month.plusMonths(1).minusDays(1);
            FundBasis b = fundBasis(m, ctx.priceAt(end), ctx.proventos12m(end));
            out.add(point(period == Period.YEAR ? String.valueOf(m.month.getYear()) : quarterLabel(end),
                    IndicatorRules.fund(indicator, b), end));
        }
        return out;
    }

    private static FundBasis fundBasis(MonthRow m, BigDecimal price, BigDecimal proventos) {
        if (m == null) {
            return new FundBasis(null, null, null, null, null, null, null, null, null, price, proventos);
        }
        return new FundBasis(m.month, m.navPerShare, m.shareholders, m.realEstate, m.realEstateCompanies,
                m.receivables, m.totalInvested, m.liabilities, m.totalAssets, price, proventos);
    }

    // ------------------------------------------------------------------ dados

    private record AssetRow(long id, String ticker, Long issuerId, IssuerKind kind, String shareClass,
                            long priceAssetId, int unitShares) {
    }

    private record QuarterRow(LocalDate periodEnd, boolean annual, BigDecimal netIncomeParentTtm,
                              BigDecimal equityParent, Long sharesOutstanding, BigDecimal dividendsDeclaredTtm) {
    }

    private record MonthRow(LocalDate month, BigDecimal navPerShare, Integer shareholders, BigDecimal realEstate,
                            BigDecimal realEstateCompanies, BigDecimal receivables, BigDecimal totalInvested,
                            BigDecimal liabilities, BigDecimal totalAssets) {
    }

    private record StockEvent(LocalDate dataCom, String type, BigDecimal factor) {
    }

    private record CashEvent(LocalDate dataCom, BigDecimal value) {
    }

    /** O ativo e o que se le dele, uma vez por requisicao. */
    private final class Context {
        final AssetRow asset;
        final NavigableMap<LocalDate, BigDecimal> closes;
        final BigDecimal currentPrice;
        final List<StockEvent> stockEvents;
        final List<CashEvent> cashEvents;

        Context(AssetRow asset) {
            this.asset = asset;
            this.closes = closes(asset.priceAssetId);
            BigDecimal current = jdbc.queryForObject("SELECT current_price FROM assets WHERE id = ?",
                    BigDecimal.class, asset.priceAssetId);
            this.currentPrice = current != null ? current : closes.isEmpty() ? null : closes.lastEntry().getValue();
            this.stockEvents = jdbc.query("""
                    SELECT data_com, type, factor FROM asset_stock_events WHERE asset_id = ? ORDER BY data_com""",
                    (rs, i) -> new StockEvent(rs.getDate("data_com").toLocalDate(), rs.getString("type"),
                            rs.getBigDecimal("factor")), asset.priceAssetId);
            // proventos do proprio ticker (D5); o fracionario usa os do lote padrao
            long cashAsset = asset.ticker.equals(baseTicker(asset.ticker)) ? asset.id : asset.priceAssetId;
            this.cashEvents = jdbc.query("""
                    SELECT data_com, value_per_share FROM asset_cash_events
                    WHERE asset_id = ? AND type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO') ORDER BY data_com""",
                    (rs, i) -> new CashEvent(rs.getDate("data_com").toLocalDate(), rs.getBigDecimal("value_per_share")),
                    cashAsset);
        }

        /** Fechamento do ultimo pregao ate a data, se nao for antigo demais. */
        BigDecimal priceAt(LocalDate date) {
            Map.Entry<LocalDate, BigDecimal> e = closes.floorEntry(date);
            if (e == null || e.getKey().isBefore(date.minusDays(MAX_PRICE_GAP_DAYS))) {
                return null;
            }
            return e.getValue();
        }

        /** Razao da quantidade de acoes pelos eventos com data-com em (from, to]. */
        BigDecimal ratio(LocalDate from, LocalDate to) {
            BigDecimal r = BigDecimal.ONE;
            for (StockEvent e : stockEvents) {
                if (e.dataCom.isAfter(from) && !e.dataCom.isAfter(to) && e.factor != null && e.factor.signum() > 0) {
                    r = r.multiply(switch (e.type) {
                        case "GRUPAMENTO" -> e.factor;
                        default -> BigDecimal.ONE.add(e.factor.divide(HUNDRED, MC)); // desdobramento, bonificacao
                    }, MC);
                }
            }
            return r;
        }

        /** Proventos por papel com data-com em (ref - 12 meses, ref], na quantidade da data ref. */
        BigDecimal proventos12m(LocalDate ref) {
            LocalDate from = ref.minusYears(1);
            BigDecimal sum = BigDecimal.ZERO;
            for (CashEvent c : cashEvents) {
                if (c.dataCom.isAfter(from) && !c.dataCom.isAfter(ref)) {
                    sum = sum.add(c.value.divide(ratio(c.dataCom, ref), MC));
                }
            }
            return sum;
        }
    }

    private Context context(String ticker) {
        String t = ticker == null ? "" : ticker.trim().toUpperCase();
        List<AssetRow> found = jdbc.query("""
                SELECT a.id, a.ticker, a.issuer_id, i.kind, a.share_class
                FROM assets a LEFT JOIN issuers i ON i.id = a.issuer_id
                WHERE upper(a.ticker) = ?""",
                (rs, i) -> new AssetRow(rs.getLong("id"), rs.getString("ticker"),
                        (Long) rs.getObject("issuer_id", Long.class),
                        rs.getString("kind") == null ? null : IssuerKind.valueOf(rs.getString("kind")),
                        rs.getString("share_class"), 0, 0), t);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo nao encontrado: " + t);
        }
        AssetRow a = found.get(0);
        if (a.issuerId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ativo sem emissor: indicadores so para acoes e FIIs ligados a um emissor (" + t + ")");
        }
        String base = baseTicker(a.ticker);
        long priceAsset = a.id;
        if (!base.equals(a.ticker)) {
            List<Long> ids = jdbc.queryForList("SELECT id FROM assets WHERE upper(ticker) = ?", Long.class, base);
            if (!ids.isEmpty()) {
                priceAsset = ids.get(0);
            }
        }
        int unitShares = 1;
        if ("UNIT".equals(a.shareClass)) {
            // composicao do lote padrao; o fracionario sem composicao propria usa a dele
            unitShares = unitShares(priceAsset);
            if (unitShares == 0) {
                unitShares = unitShares(a.id);
            }
        }
        return new Context(new AssetRow(a.id, a.ticker, a.issuerId, a.kind, a.shareClass, priceAsset, unitShares));
    }

    /** PETR4F -> PETR4: o fracionario e o mesmo papel (D8). */
    static String baseTicker(String ticker) {
        String t = ticker.trim().toUpperCase();
        return t.endsWith("F") && t.length() > 5 && Character.isDigit(t.charAt(t.length() - 2))
                ? t.substring(0, t.length() - 1) : t;
    }

    private NavigableMap<LocalDate, BigDecimal> closes(long assetId) {
        NavigableMap<LocalDate, BigDecimal> out = new TreeMap<>();
        jdbc.query("""
                SELECT reference_date, close_price FROM asset_price_history
                WHERE asset_id = ? AND close_price IS NOT NULL ORDER BY reference_date""",
                rs -> { out.put(rs.getDate("reference_date").toLocalDate(), rs.getBigDecimal("close_price")); },
                assetId);
        return out;
    }

    private List<QuarterRow> quarters(long issuerId) {
        return jdbc.query("""
                SELECT q.period_end, d.doc_type, q.net_income_parent_ttm, q.equity_parent, q.shares_outstanding,
                       q.dividends_declared_ttm
                FROM company_quarters q JOIN cvm_documents d ON d.id = q.source_doc_id
                WHERE q.issuer_id = ? ORDER BY q.period_end""",
                (rs, i) -> new QuarterRow(rs.getDate("period_end").toLocalDate(), "DFP".equals(rs.getString("doc_type")),
                        rs.getBigDecimal("net_income_parent_ttm"), rs.getBigDecimal("equity_parent"),
                        (Long) rs.getObject("shares_outstanding", Long.class), rs.getBigDecimal("dividends_declared_ttm")),
                issuerId);
    }

    private List<MonthRow> months(long issuerId) {
        return jdbc.query("""
                SELECT reference_month, nav_per_share, shareholders, real_estate_total, real_estate_companies,
                       receivables_total, total_invested, liabilities_total, total_assets
                FROM fii_monthly WHERE issuer_id = ? ORDER BY reference_month""",
                (rs, i) -> new MonthRow(rs.getDate("reference_month").toLocalDate(), rs.getBigDecimal("nav_per_share"),
                        (Integer) rs.getObject("shareholders", Integer.class), rs.getBigDecimal("real_estate_total"),
                        rs.getBigDecimal("real_estate_companies"), rs.getBigDecimal("receivables_total"),
                        rs.getBigDecimal("total_invested"), rs.getBigDecimal("liabilities_total"),
                        rs.getBigDecimal("total_assets")),
                issuerId);
    }

    private int unitShares(long assetId) {
        Integer sum = jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM unit_compositions WHERE asset_id = ?",
                Integer.class, assetId);
        return sum == null ? 0 : sum;
    }
}
