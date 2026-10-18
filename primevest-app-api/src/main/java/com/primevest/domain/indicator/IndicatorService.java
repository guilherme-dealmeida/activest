package com.primevest.domain.indicator;

import com.primevest.domain.indicator.IndicatorRules.CompanyBasis;
import com.primevest.domain.indicator.IndicatorRules.FundBasis;
import com.primevest.domain.indicator.IndicatorRules.Value;
import com.primevest.domain.indicator.dto.AssetIndicatorsDTO;
import com.primevest.domain.indicator.dto.IndicatorHistoryDTO;
import com.primevest.domain.indicator.dto.IndicatorPointDTO;
import com.primevest.domain.corporateaction.StockEventType;
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
 * Indicadores de acoes e FIIs (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5 e F9). <b>Nada e gravado:</b> le
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
        boolean company = ctx.asset.kind == IssuerKind.COMPANY;
        return currentOf(ctx, company ? quarters(ctx.asset.issuerId) : List.of(),
                company ? List.of() : months(ctx.asset.issuerId)).indicators();
    }

    /**
     * O "Atual" de um ticker com o que vai junto na listagem de emissoras
     * (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 4.1).
     *
     * @param priceDate data do preco atual ({@code price_updated_at} da BRAPI ou o dia do ultimo fechamento)
     * @param equity    patrimonio liquido da base: PL da controladora do ultimo balanco (acao) ou PL do ultimo
     *                  informe (FII); nulo sem base
     */
    public record CurrentSnapshot(long assetId, AssetIndicatorsDTO indicators, LocalDate priceDate,
                                  BigDecimal equity) {
    }

    private CurrentSnapshot currentOf(Context ctx, List<QuarterRow> quarters, List<MonthRow> months) {
        List<AssetIndicatorsDTO.Item> items = new ArrayList<>();
        LocalDate base;
        BigDecimal equity;
        if (ctx.asset.kind == IssuerKind.COMPANY) {
            CurrentCompany cc = currentCompany(ctx, quarters);
            base = cc.basis().periodEnd();
            equity = quarters.isEmpty() ? null : quarters.get(quarters.size() - 1).equityParent;
            for (Indicator i : Indicator.values()) {
                if (i.appliesTo(IssuerKind.COMPANY)) {
                    Value v = cc.value(i);
                    items.add(new AssetIndicatorsDTO.Item(i, i.unit(), v.value(), v.note()));
                }
            }
        } else {
            FundBasis basis = currentFund(ctx, months);
            base = basis.month() == null ? null : basis.month().plusMonths(1).minusDays(1);
            equity = months.isEmpty() ? null : months.get(months.size() - 1).netAssets;
            for (Indicator i : Indicator.values()) {
                if (i.appliesTo(IssuerKind.FII)) {
                    Value v = IndicatorRules.fund(i, basis);
                    items.add(new AssetIndicatorsDTO.Item(i, i.unit(), v.value(), v.note()));
                }
            }
        }
        return new CurrentSnapshot(ctx.asset.id, new AssetIndicatorsDTO(ctx.asset.ticker, ctx.asset.issuerId,
                ctx.asset.kind, ctx.currentPrice, base, items), ctx.currentPriceDate, equity);
    }

    // ------------------------------------------------------------------ listagem (lote)

    /** Os ativos da listagem de uma aba: ativos, ligados a emissor do tipo pedido. */
    private static final String LISTED_ASSETS = """
            SELECT a.id FROM assets a JOIN issuers i ON i.id = a.issuer_id WHERE a.active AND i.kind = ?""";

    /**
     * O "Atual" de todos os tickers de uma aba da listagem de emissoras (docs/planos/PLANO_LISTAGEM_EMISSORAS.md,
     * 4.1): ativos ligados a emissor do tipo, <b>sem os fracionarios</b> cujo lote padrao esta na mesma lista
     * (D9 — PETR4F e PETR4 dariam a mesma linha). Le tudo em poucas consultas por conjunto e calcula com
     * <b>o mesmo codigo</b> de {@link #current(String)}: so a origem dos dados muda. Ordem: a do ticker.
     */
    public List<CurrentSnapshot> currentAll(IssuerKind kind) {
        record Row(long id, String ticker, long issuerId, String shareClass, BigDecimal currentPrice,
                   LocalDate updatedAt, LocalDate lastDate, BigDecimal lastClose) {
        }
        List<Row> assets = jdbc.query("""
                SELECT a.id, a.ticker, a.issuer_id, a.share_class, a.current_price, a.price_updated_at,
                       lc.reference_date AS last_date, lc.close_price AS last_close
                FROM assets a JOIN issuers i ON i.id = a.issuer_id
                LEFT JOIN LATERAL (SELECT h.reference_date, h.close_price FROM asset_price_history h
                                   WHERE h.asset_id = a.id AND h.close_price IS NOT NULL
                                   ORDER BY h.reference_date DESC LIMIT 1) lc ON true
                WHERE a.active AND i.kind = ?
                ORDER BY upper(a.ticker)""",
                (rs, i) -> {
                    java.sql.Timestamp at = rs.getTimestamp("price_updated_at");
                    java.sql.Date last = rs.getDate("last_date");
                    return new Row(rs.getLong("id"), rs.getString("ticker"), rs.getLong("issuer_id"),
                            rs.getString("share_class"), rs.getBigDecimal("current_price"),
                            at == null ? null : at.toLocalDateTime().toLocalDate(),
                            last == null ? null : last.toLocalDate(), rs.getBigDecimal("last_close"));
                }, kind.name());
        java.util.Set<String> tickers = new java.util.HashSet<>();
        assets.forEach(a -> tickers.add(a.ticker.trim().toUpperCase()));

        Map<Long, Integer> units = new java.util.HashMap<>();
        jdbc.query("SELECT asset_id, COALESCE(SUM(quantity), 0) AS q FROM unit_compositions GROUP BY asset_id",
                rs -> { units.put(rs.getLong("asset_id"), rs.getInt("q")); });
        Map<Long, List<StockEvent>> stock = new java.util.HashMap<>();
        jdbc.query("SELECT asset_id, data_com, type, factor FROM asset_stock_events WHERE asset_id IN ("
                        + LISTED_ASSETS + ") ORDER BY asset_id, data_com",
                rs -> {
                    stock.computeIfAbsent(rs.getLong("asset_id"), k -> new ArrayList<>()).add(new StockEvent(
                            rs.getDate("data_com").toLocalDate(), rs.getString("type"), rs.getBigDecimal("factor")));
                }, kind.name());
        // so o que cabe nos 12 meses de hoje: e o unico uso do provento no "Atual"
        Map<Long, List<CashEvent>> cash = new java.util.HashMap<>();
        jdbc.query("SELECT asset_id, data_com, value_per_share FROM asset_cash_events WHERE asset_id IN ("
                        + LISTED_ASSETS + ") AND type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO') AND data_com >= ?"
                        + " ORDER BY asset_id, data_com",
                rs -> {
                    cash.computeIfAbsent(rs.getLong("asset_id"), k -> new ArrayList<>()).add(new CashEvent(
                            rs.getDate("data_com").toLocalDate(), rs.getBigDecimal("value_per_share")));
                }, kind.name(), java.sql.Date.valueOf(LocalDate.now(clock).minusYears(1)));
        Map<Long, List<QuarterRow>> quarters = new java.util.HashMap<>();
        Map<Long, List<MonthRow>> months = new java.util.HashMap<>();
        String issuers = "SELECT a.issuer_id FROM assets a JOIN issuers i ON i.id = a.issuer_id"
                + " WHERE a.active AND i.kind = ?";
        if (kind == IssuerKind.COMPANY) {
            jdbc.query(QUARTERS_SELECT + " WHERE q.issuer_id IN (" + issuers + ") ORDER BY q.issuer_id, q.period_end",
                    rs -> {
                        quarters.computeIfAbsent(rs.getLong("issuer_id"), k -> new ArrayList<>()).add(quarterRow(rs));
                    }, kind.name());
        } else {
            // so o ultimo informe de cada fundo: e o unico que o "Atual" le
            jdbc.query("SELECT DISTINCT ON (issuer_id) issuer_id, " + MONTH_COLUMNS
                            + " FROM fii_monthly WHERE issuer_id IN (" + issuers + ")"
                            + " ORDER BY issuer_id, reference_month DESC",
                    rs -> {
                        months.computeIfAbsent(rs.getLong("issuer_id"), k -> new ArrayList<>()).add(monthRow(rs));
                    }, kind.name());
        }

        List<CurrentSnapshot> out = new ArrayList<>();
        for (Row a : assets) {
            String base = baseTicker(a.ticker);
            if (!base.equals(a.ticker.trim().toUpperCase())) {
                if (tickers.contains(base)) {
                    continue; // fracionario: a linha do lote padrao ja esta na lista
                }
                // fracionario sem o lote padrao na lista (raro): o caminho de um ticker, que resolve o lote
                out.add(currentOf(context(a.ticker), quarters.getOrDefault(a.issuerId, List.of()),
                        months.getOrDefault(a.issuerId, List.of())));
                continue;
            }
            int unitShares = "UNIT".equals(a.shareClass) ? units.getOrDefault(a.id, 0) : 1;
            BigDecimal price;
            LocalDate priceDate;
            if (a.currentPrice != null) {
                price = a.currentPrice;
                priceDate = a.updatedAt != null ? a.updatedAt : a.lastDate;
            } else {
                price = a.lastClose;
                priceDate = a.lastClose == null ? null : a.lastDate;
            }
            Context ctx = new Context(new AssetRow(a.id, a.ticker, a.issuerId, kind, a.shareClass, a.id, unitShares),
                    price, priceDate, stock.getOrDefault(a.id, List.of()), cash.getOrDefault(a.id, List.of()));
            out.add(currentOf(ctx, quarters.getOrDefault(a.issuerId, List.of()),
                    months.getOrDefault(a.issuerId, List.of())));
        }
        return out;
    }

    public IndicatorHistoryDTO history(String ticker, Indicator indicator, Period period) {
        Context ctx = context(ticker);
        if (!indicator.appliesTo(ctx.asset.kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    indicator + " nao se aplica a " + (ctx.asset.kind == IssuerKind.FII ? "FII" : "acao"));
        }
        return history(ctx, new Rows(ctx), indicator, period);
    }

    /**
     * Historico de todos os indicadores do ticker, na ordem do {@link Indicator}
     * (docs/planos/PLANO_TELA_EMISSORES.md, B4 — sparklines). Le trimestres/informes, precos e proventos
     * uma vez so; cada item e igual ao de {@link #history(String, Indicator, Period)}.
     */
    public List<IndicatorHistoryDTO> historyAll(String ticker, Period period) {
        Context ctx = context(ticker);
        Rows rows = new Rows(ctx);
        List<IndicatorHistoryDTO> out = new ArrayList<>();
        for (Indicator i : Indicator.values()) {
            if (i.appliesTo(ctx.asset.kind)) {
                out.add(history(ctx, rows, i, period));
            }
        }
        return out;
    }

    /** Serie do emissor lida uma vez por requisicao: trimestres (companhia) ou informes (FII). */
    private final class Rows {
        final List<QuarterRow> quarters;
        final List<MonthRow> months;

        Rows(Context ctx) {
            boolean company = ctx.asset.kind == IssuerKind.COMPANY;
            this.quarters = company ? quarters(ctx.asset.issuerId) : List.of();
            this.months = company ? List.of() : months(ctx.asset.issuerId);
        }
    }

    private IndicatorHistoryDTO history(Context ctx, Rows rows, Indicator indicator, Period period) {
        IndicatorPointDTO current;
        List<IndicatorPointDTO> annual;
        List<IndicatorPointDTO> points;
        if (ctx.asset.kind == IssuerKind.COMPANY) {
            List<QuarterRow> quarters = rows.quarters;
            current = point("Atual", currentCompany(ctx, quarters).value(indicator),
                    quarters.isEmpty() ? null : quarters.get(quarters.size() - 1).periodEnd);
            annual = companyPoints(ctx, quarters, indicator, Period.YEAR);
            points = period == Period.YEAR ? annual : companyPoints(ctx, quarters, indicator, Period.QUARTER);
        } else {
            List<MonthRow> months = rows.months;
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

    // ------------------------------------------------------------------ base do Preco Teto

    /**
     * O que o Preco Teto usa de padrao (docs/planos/PLANO_PRECO_TETO.md, 5.2), lido pelas <b>mesmas regras</b> da
     * tela de Emissoras: quantidade de acoes do ultimo balanco (ou do ultimo conferido, com o motivo) ajustada pelos
     * eventos societarios posteriores, preco atual com a data, e o ROE e o payout anuais (DFP).
     *
     * @param sharesAdjusted acoes sem tesouraria da companhia ajustadas ate hoje; nulo sem quantidade conferida
     * @param unitShares     acoes por papel (1, ou a composicao da unit)
     */
    public record CompanyFundamentals(String ticker, Long issuerId, IssuerKind kind, int unitShares,
                                      LocalDate lastPeriodEnd, LocalDate sharesPeriodEnd, Long sharesAdjusted,
                                      String sharesNote, BigDecimal equityParent, BigDecimal price,
                                      LocalDate priceDate, List<IndicatorPointDTO> roeYears,
                                      List<IndicatorPointDTO> payoutYears) {
    }

    public CompanyFundamentals companyFundamentals(String ticker) {
        Context ctx = context(ticker);
        if (ctx.asset.kind != IssuerKind.COMPANY) {
            return new CompanyFundamentals(ctx.asset.ticker, ctx.asset.issuerId, ctx.asset.kind, ctx.asset.unitShares,
                    null, null, null, null, null, ctx.currentPrice, ctx.currentPriceDate, List.of(), List.of());
        }
        List<QuarterRow> quarters = quarters(ctx.asset.issuerId);
        Long shares = null;
        LocalDate sharesDate = null;
        String note = null;
        LocalDate last = null;
        BigDecimal equity = null;
        if (!quarters.isEmpty()) {
            CurrentCompany cc = currentCompany(ctx, quarters);
            QuarterRow lastRow = quarters.get(quarters.size() - 1);
            last = lastRow.periodEnd;
            equity = lastRow.equityParent;
            note = cc.sharesNote();
            sharesDate = last;
            if (note != null) {
                // o fallback do balanco em revisao: a quantidade e a do ultimo conferido
                for (int k = quarters.size() - 2; k >= 0; k--) {
                    if (cc.basis().sharesOutstanding() != null
                            && cc.basis().sharesOutstanding().equals(quarters.get(k).sharesOutstanding)) {
                        sharesDate = quarters.get(k).periodEnd;
                        break;
                    }
                }
            }
            if (cc.basis().sharesOutstanding() != null && cc.basis().sharesOutstanding() > 0) {
                BigDecimal ratio = cc.basis().sharesRatio() == null ? BigDecimal.ONE : cc.basis().sharesRatio();
                shares = BigDecimal.valueOf(cc.basis().sharesOutstanding()).multiply(ratio)
                        .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
            } else {
                note = "sem quantidade de acoes conferida no balanco (escala em revisao)";
            }
        }
        Rows rows = new Rows(ctx);
        List<IndicatorPointDTO> roe = history(ctx, rows, Indicator.ROE, Period.YEAR).points();
        List<IndicatorPointDTO> payout = history(ctx, rows, Indicator.PAYOUT, Period.YEAR).points();
        return new CompanyFundamentals(ctx.asset.ticker, ctx.asset.issuerId, ctx.asset.kind, ctx.asset.unitShares,
                last, sharesDate, shares, note, equity, ctx.currentPrice, ctx.currentPriceDate, roe, payout);
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
                    b.dividendsPaidTtm(), b.price(), b.proventos12m());
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
                q.dividendsPaidTtm, price, proventos);
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
                              BigDecimal equityParent, Long sharesOutstanding, BigDecimal dividendsPaidTtm) {
    }

    private record MonthRow(LocalDate month, BigDecimal navPerShare, Integer shareholders, BigDecimal realEstate,
                            BigDecimal realEstateCompanies, BigDecimal receivables, BigDecimal totalInvested,
                            BigDecimal liabilities, BigDecimal totalAssets, BigDecimal netAssets) {
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
        /** Data do preco atual: {@code price_updated_at} da BRAPI ou o dia do ultimo fechamento. */
        final LocalDate currentPriceDate;
        final List<StockEvent> stockEvents;
        final List<CashEvent> cashEvents;

        /**
         * Com os dados ja lidos (listagem em lote): so o "Atual", que nao usa a serie de fechamentos —
         * {@link #priceAt} fica sem preco.
         */
        Context(AssetRow asset, BigDecimal currentPrice, LocalDate currentPriceDate, List<StockEvent> stockEvents,
                List<CashEvent> cashEvents) {
            this.asset = asset;
            this.closes = new TreeMap<>();
            this.currentPrice = currentPrice;
            this.currentPriceDate = currentPriceDate;
            this.stockEvents = stockEvents;
            this.cashEvents = cashEvents;
        }

        Context(AssetRow asset) {
            this.asset = asset;
            this.closes = closes(asset.priceAssetId);
            Map<String, Object> cur = jdbc.queryForMap(
                    "SELECT current_price, price_updated_at FROM assets WHERE id = ?", asset.priceAssetId);
            BigDecimal current = (BigDecimal) cur.get("current_price");
            if (current != null) {
                this.currentPrice = current;
                Object at = cur.get("price_updated_at");
                this.currentPriceDate = at instanceof java.sql.Timestamp ts ? ts.toLocalDateTime().toLocalDate()
                        : closes.isEmpty() ? null : closes.lastKey();
            } else {
                this.currentPrice = closes.isEmpty() ? null : closes.lastEntry().getValue();
                this.currentPriceDate = closes.isEmpty() ? null : closes.lastKey();
            }
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
                    BigDecimal q = e.dataCom.isAfter(from) && !e.dataCom.isAfter(to)
                        ? StockEventType.valueOf(e.type).quantityRatio(e.factor) : null;
                if (q != null) {
                    r = r.multiply(q, MC);
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

    private static final String QUARTERS_SELECT = """
            SELECT q.issuer_id, q.period_end, d.doc_type, q.net_income_parent_ttm, q.equity_parent,
                   q.shares_outstanding, q.dividends_paid_ttm
            FROM company_quarters q JOIN cvm_documents d ON d.id = q.source_doc_id""";

    private static final String MONTH_COLUMNS = """
            reference_month, nav_per_share, shareholders, real_estate_total, real_estate_companies,
            receivables_total, total_invested, liabilities_total, total_assets, net_assets""";

    private static QuarterRow quarterRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new QuarterRow(rs.getDate("period_end").toLocalDate(), "DFP".equals(rs.getString("doc_type")),
                rs.getBigDecimal("net_income_parent_ttm"), rs.getBigDecimal("equity_parent"),
                (Long) rs.getObject("shares_outstanding", Long.class), rs.getBigDecimal("dividends_paid_ttm"));
    }

    private static MonthRow monthRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MonthRow(rs.getDate("reference_month").toLocalDate(), rs.getBigDecimal("nav_per_share"),
                (Integer) rs.getObject("shareholders", Integer.class), rs.getBigDecimal("real_estate_total"),
                rs.getBigDecimal("real_estate_companies"), rs.getBigDecimal("receivables_total"),
                rs.getBigDecimal("total_invested"), rs.getBigDecimal("liabilities_total"),
                rs.getBigDecimal("total_assets"), rs.getBigDecimal("net_assets"));
    }

    private List<QuarterRow> quarters(long issuerId) {
        return jdbc.query(QUARTERS_SELECT + " WHERE q.issuer_id = ? ORDER BY q.period_end",
                (rs, i) -> quarterRow(rs), issuerId);
    }

    private List<MonthRow> months(long issuerId) {
        return jdbc.query("SELECT " + MONTH_COLUMNS + " FROM fii_monthly WHERE issuer_id = ? ORDER BY reference_month",
                (rs, i) -> monthRow(rs), issuerId);
    }

    private int unitShares(long assetId) {
        Integer sum = jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM unit_compositions WHERE asset_id = ?",
                Integer.class, assetId);
        return sum == null ? 0 : sum;
    }
}
