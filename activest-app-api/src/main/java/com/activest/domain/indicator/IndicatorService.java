package com.activest.domain.indicator;

import com.activest.domain.indicator.IndicatorRules.Accounts;
import com.activest.domain.indicator.IndicatorRules.CompanyBasis;
import com.activest.domain.indicator.IndicatorRules.FundBasis;
import com.activest.domain.indicator.IndicatorRules.Value;
import com.activest.domain.indicator.dto.AssetIndicatorsDTO;
import com.activest.domain.indicator.dto.IndicatorHistoryDTO;
import com.activest.domain.indicator.dto.IndicatorPointDTO;
import com.activest.domain.corporateaction.StockEventType;
import com.activest.domain.issuer.IssuerKind;
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
    /** Janela da liquidez media (D3: serie PN de maior liquidez; D4: liquidez do card), em meses. */
    private final int liquidityMonths;

    @org.springframework.beans.factory.annotation.Autowired
    public IndicatorService(JdbcTemplate jdbc,
                            @org.springframework.beans.factory.annotation.Value(
                                    "${activest.issuer-page.liquidity-months:2}") int liquidityMonths) {
        this(jdbc, Clock.system(ZONE), liquidityMonths);
    }

    IndicatorService(JdbcTemplate jdbc, Clock clock, int liquidityMonths) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.liquidityMonths = liquidityMonths;
    }

    // ------------------------------------------------------------------ entradas

    public AssetIndicatorsDTO current(String ticker) {
        Context ctx = context(ticker);
        boolean company = ctx.asset.kind == IssuerKind.COMPANY;
        return currentOf(ctx, company ? quarters(ctx.asset.issuerId) : List.of(),
                company ? List.of() : months(ctx.asset.issuerId), false).indicators();
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

    /**
     * @param listedOnly so os indicadores das colunas da listagem (T11, docs/planos/PLANO_TELA_ATIVOS.md): a linha
     *                   da listagem nao leva os demais
     */
    private CurrentSnapshot currentOf(Context ctx, List<QuarterRow> quarters, List<MonthRow> months,
                                      boolean listedOnly) {
        List<AssetIndicatorsDTO.Item> items = new ArrayList<>();
        LocalDate base;
        BigDecimal equity;
        if (ctx.asset.kind == IssuerKind.COMPANY) {
            CurrentCompany cc = currentCompany(ctx, quarters);
            base = cc.basis().periodEnd();
            equity = quarters.isEmpty() ? null : quarters.get(quarters.size() - 1).equityParent;
            for (Indicator i : Indicator.values()) {
                if (i.appliesTo(IssuerKind.COMPANY, ctx.asset.statementLayout) && (!listedOnly || i.listed())) {
                    Value v = cc.value(i);
                    items.add(new AssetIndicatorsDTO.Item(i, i.unit(), v.value(), v.note()));
                }
            }
        } else {
            FundBasis basis = currentFund(ctx, months);
            base = basis.month() == null ? null : basis.month().plusMonths(1).minusDays(1);
            equity = months.isEmpty() ? null : months.get(months.size() - 1).netAssets;
            for (Indicator i : Indicator.values()) {
                if (i.appliesTo(IssuerKind.FII) && (!listedOnly || i.listed())) {
                    Value v = IndicatorRules.fund(i, basis);
                    items.add(new AssetIndicatorsDTO.Item(i, i.unit(), v.value(), v.note()));
                }
            }
        }
        return new CurrentSnapshot(ctx.asset.id, new AssetIndicatorsDTO(ctx.asset.ticker, ctx.asset.issuerId,
                ctx.asset.kind, ctx.currentPrice, base, ctx.asset.statementLayout, items), ctx.currentPriceDate,
                equity);
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
     * <p>
     * O fracionario que existe sozinho no catalogo (EKTR3F, CEDO3F... — o lote padrao nao existe) e calculado no
     * lote com os proprios dados, como o caminho de um ticker faria (docs/planos/PLANO_PERFORMANCE.md, P2): ele
     * custava duas leituras de {@code assets} e a serie inteira de fechamentos por linha.
     */
    public List<CurrentSnapshot> currentAll(IssuerKind kind) {
        record Row(long id, String ticker, long issuerId, String shareClass, String layout, BigDecimal currentPrice,
                   LocalDate updatedAt, LocalDate lastDate, BigDecimal lastClose) {
        }
        List<Row> assets = jdbc.query("""
                SELECT a.id, a.ticker, a.issuer_id, a.share_class, i.statement_layout, a.current_price,
                       a.price_updated_at,
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
                            rs.getString("share_class"), rs.getString("statement_layout"),
                            rs.getBigDecimal("current_price"),
                            at == null ? null : at.toLocalDateTime().toLocalDate(),
                            last == null ? null : last.toLocalDate(), rs.getBigDecimal("last_close"));
                }, kind.name());
        java.util.Set<String> tickers = new java.util.HashSet<>();
        assets.forEach(a -> tickers.add(a.ticker.trim().toUpperCase()));
        // fracionarios sem o lote padrao na lista: quais lotes padrao existem no catalogo (fora da lista)
        List<String> missingBases = assets.stream().map(a -> baseTicker(a.ticker))
                .filter(b -> !tickers.contains(b)).distinct().toList();
        java.util.Set<String> basesInCatalog = new java.util.HashSet<>();
        if (!missingBases.isEmpty()) {
            basesInCatalog.addAll(jdbc.queryForList("SELECT DISTINCT upper(ticker) FROM assets WHERE upper(ticker) IN ("
                    + String.join(", ", java.util.Collections.nCopies(missingBases.size(), "?")) + ")",
                    String.class, missingBases.toArray()));
        }

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
            // so as colunas dos indicadores da listagem (T11): sem as contas da tela de Ativos nem a quantidade por classe
            jdbc.query(LISTING_QUARTERS_SELECT + " WHERE q.issuer_id IN (" + issuers + ") ORDER BY q.issuer_id, q.period_end",
                    rs -> {
                        quarters.computeIfAbsent(rs.getLong("issuer_id"), k -> new ArrayList<>()).add(listingQuarterRow(rs));
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
                if (basesInCatalog.contains(base)) {
                    // lote padrao no catalogo, fora da lista (raro): o caminho de um ticker, que usa o preco dele
                    out.add(currentOf(context(a.ticker), quarters.getOrDefault(a.issuerId, List.of()),
                            months.getOrDefault(a.issuerId, List.of()), true));
                    continue;
                }
                // so existe o fracionario: segue no lote com os proprios dados (o lote padrao do context() e ele)
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
            Context ctx = new Context(new AssetRow(a.id, a.ticker, a.issuerId, kind, a.shareClass, a.id, unitShares,
                    a.layout), price, priceDate, stock.getOrDefault(a.id, List.of()), cash.getOrDefault(a.id, List.of()));
            out.add(currentOf(ctx, quarters.getOrDefault(a.issuerId, List.of()),
                    months.getOrDefault(a.issuerId, List.of()), true));
        }
        return out;
    }

    public IndicatorHistoryDTO history(String ticker, Indicator indicator, Period period) {
        Context ctx = context(ticker);
        if (!indicator.appliesTo(ctx.asset.kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    indicator + " nao se aplica a " + (ctx.asset.kind == IssuerKind.FII ? "FII" : "acao"));
        }
        if (!indicator.appliesTo(ctx.asset.kind, ctx.asset.statementLayout)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    indicator + " nao se aplica a bancos e seguradoras");
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
            if (i.appliesTo(ctx.asset.kind, ctx.asset.statementLayout)) {
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
        List<IndicatorHistoryDTO.WindowAverage> averages = new ArrayList<>();
        for (IndicatorWindow w : IndicatorWindow.values()) {
            averages.add(windowAverage(w, annual, current, indicator.unit()));
        }
        return new IndicatorHistoryDTO(ctx.asset.ticker, indicator, indicator.unit(), period.name(), current,
                averages.get(averages.size() - 1).value(), averages, points);
    }

    /**
     * Media de uma janela (D14): os N ultimos pontos anuais (exercicios fechados) + Atual, com a regra de
     * {@link IndicatorRules#average} (nulos fora, zeros dentro). MAX = todos os anuais.
     */
    static IndicatorHistoryDTO.WindowAverage windowAverage(IndicatorWindow w, List<IndicatorPointDTO> annual,
                                                          IndicatorPointDTO current, Indicator.Unit unit) {
        List<IndicatorPointDTO> inWindow = w.years() == 0 || annual.size() <= w.years() ? annual
                : annual.subList(annual.size() - w.years(), annual.size());
        List<BigDecimal> values = new ArrayList<>(inWindow.stream().map(IndicatorPointDTO::value).toList());
        values.add(current.value());
        Integer from = inWindow.isEmpty() || inWindow.get(0).periodEnd() == null ? null
                : inWindow.get(0).periodEnd().getYear();
        return new IndicatorHistoryDTO.WindowAverage(w.label(), from, IndicatorRules.average(values, unit));
    }

    private static IndicatorPointDTO point(String label, Value v, LocalDate periodEnd) {
        return new IndicatorPointDTO(label, periodEnd, v.value(), v.note());
    }

    // ------------------------------------------------------------------ Dados da empresa (A3)

    /**
     * Base do card "Dados da empresa" / "Dados do fundo" (docs/planos/PLANO_TELA_ATIVOS.md, D2, D3 e D6), com as
     * mesmas regras dos indicadores: preco atual do ticker; acao — ultimo balanco, valor de mercado da companhia
     * (D3, com a nota) e a quantidade de acoes do balanco ajustada pelos eventos posteriores; FII — ultimo informe.
     *
     * @param sharesTotal acao: acoes da companhia com tesouraria ("nº total de papeis"); FII: cotas emitidas
     * @param equity      acao: PL da controladora; FII: PL do informe
     */
    public record MarketData(long assetId, String ticker, Long issuerId, IssuerKind kind, String statementLayout,
                             BigDecimal price, LocalDate priceDate, LocalDate basePeriodEnd, BigDecimal marketValue,
                             String marketValueNote, BigDecimal equity, BigDecimal sharesTotal, BigDecimal totalAssets,
                             BigDecimal currentAssets, BigDecimal grossDebt, BigDecimal cash) {
    }

    public MarketData marketData(String ticker) {
        Context ctx = context(ticker);
        if (ctx.asset.kind == IssuerKind.FII) {
            List<MonthRow> months = months(ctx.asset.issuerId);
            MonthRow last = months.isEmpty() ? null : months.get(months.size() - 1);
            BigDecimal shares = last == null ? null : fundShares(ctx.asset.issuerId, last.month);
            BigDecimal value = shares == null || ctx.currentPrice == null ? null : shares.multiply(ctx.currentPrice);
            String note = last == null ? "sem informe mensal" : shares == null ? "cotas emitidas nao informadas"
                    : ctx.currentPrice == null ? "sem cotacao" : null;
            return new MarketData(ctx.asset.id, ctx.asset.ticker, ctx.asset.issuerId, ctx.asset.kind, null,
                    ctx.currentPrice, ctx.currentPriceDate,
                    last == null ? null : last.month.plusMonths(1).minusDays(1), value, note,
                    last == null ? null : last.netAssets, shares, last == null ? null : last.totalAssets,
                    null, null, null);
        }
        List<QuarterRow> quarters = quarters(ctx.asset.issuerId);
        if (quarters.isEmpty()) {
            return new MarketData(ctx.asset.id, ctx.asset.ticker, ctx.asset.issuerId, ctx.asset.kind,
                    ctx.asset.statementLayout, ctx.currentPrice, ctx.currentPriceDate, null, null, "sem balanco",
                    null, null, null, null, null, null);
        }
        QuarterRow last = quarters.get(quarters.size() - 1);
        Accounts a = currentCompany(ctx, quarters).basis().accounts();
        Long total = sharesTotal(ctx.asset.issuerId, last.periodEnd);
        BigDecimal ratio = ctx.ratio(last.periodEnd, LocalDate.now(clock));
        return new MarketData(ctx.asset.id, ctx.asset.ticker, ctx.asset.issuerId, ctx.asset.kind,
                ctx.asset.statementLayout, ctx.currentPrice, ctx.currentPriceDate, last.periodEnd, a.marketValue(),
                a.marketValueNote(), last.equityParent,
                total == null ? null : BigDecimal.valueOf(total).multiply(ratio, MC).setScale(0, java.math.RoundingMode.HALF_UP),
                last.totalAssets, last.currentAssets, last.grossDebt, last.cash);
    }

    private Long sharesTotal(long issuerId, LocalDate periodEnd) {
        List<Long> v = jdbc.queryForList("SELECT shares_total FROM company_quarters WHERE issuer_id = ? AND period_end = ?",
                Long.class, issuerId, java.sql.Date.valueOf(periodEnd));
        return v.isEmpty() ? null : v.get(0);
    }

    private BigDecimal fundShares(long issuerId, LocalDate month) {
        List<BigDecimal> v = jdbc.queryForList("""
                SELECT shares_issued FROM fii_monthly WHERE issuer_id = ? AND reference_month = ?
                ORDER BY version DESC LIMIT 1""", BigDecimal.class, issuerId, java.sql.Date.valueOf(month));
        return v.isEmpty() ? null : v.get(0);
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
            return sharesNote != null && i.usesShares() && v.value() != null ? new Value(v.value(), sharesNote) : v;
        }
    }

    private CurrentCompany currentCompany(Context ctx, List<QuarterRow> quarters) {
        LocalDate today = LocalDate.now(clock);
        BigDecimal dividends = ctx.dividends12m(today);
        if (quarters.isEmpty()) {
            return new CurrentCompany(new CompanyBasis(null, null, null, null, null, BigDecimal.ONE,
                    ctx.asset.unitShares, null, ctx.currentPrice, dividends), null);
        }
        QuarterRow last = quarters.get(quarters.size() - 1);
        BigDecimal ratio = ctx.ratio(last.periodEnd, today);
        CompanyBasis b = basis(last, quarters, ratio, ctx.asset.unitShares, ctx.currentPrice, dividends);
        b = b.withShares(b.sharesOutstanding(), withMarketValue(ctx, b.accounts(), last, ratio, null));
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
            CompanyBasis fallback = b.withShares(prev.sharesOutstanding,
                    withMarketValue(ctx, b.accounts(), prev, ratio, null));
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
                    ctx.dividends12m(q.periodEnd));
            if (indicator == Indicator.EV_EBIT || indicator == Indicator.EV_EBITDA) {
                b = b.withShares(b.sharesOutstanding(),
                        withMarketValue(ctx, b.accounts(), q, BigDecimal.ONE, q.periodEnd));
            }
            out.add(point(period == Period.YEAR ? String.valueOf(q.periodEnd.getYear()) : quarterLabel(q.periodEnd),
                    IndicatorRules.company(indicator, b), q.periodEnd));
        }
        return out;
    }

    private static CompanyBasis basis(QuarterRow q, List<QuarterRow> quarters, BigDecimal ratio, int unitShares,
                                      BigDecimal price, BigDecimal dividends) {
        QuarterRow start = yearBefore(q, quarters);
        QuarterRow five = yearsBefore(q, quarters, 5);
        Accounts a = new Accounts(q.revenueTtm, q.ebitTtm, q.depreciationTtm, q.grossProfitTtm, q.netIncomeTtm,
                q.pretaxIncomeTtm, q.incomeTaxTtm, q.equity, q.totalAssets, q.cash, q.grossDebt, q.currentAssets,
                q.currentLiabilities, start == null ? null : start.equity, start == null ? null : start.totalAssets,
                start == null ? null : start.cash, start == null ? null : start.grossDebt,
                five == null ? null : five.revenueTtm, five == null ? null : five.netIncomeTtm, null,
                "sem valor de mercado");
        return new CompanyBasis(q.periodEnd, q.netIncomeParentTtm, q.equityParent,
                start == null ? null : start.equityParent, q.sharesOutstanding, ratio, unitShares,
                q.dividendsPaidTtm, price, dividends, a);
    }

    /** O balanco de 12 meses antes (inicio do periodo do ROE), com folga de 20 dias. */
    private static QuarterRow yearBefore(QuarterRow q, List<QuarterRow> quarters) {
        return yearsBefore(q, quarters, 1);
    }

    /** O balanco de N anos antes, com folga de 20 dias (CAGR: 5). */
    private static QuarterRow yearsBefore(QuarterRow q, List<QuarterRow> quarters, int years) {
        LocalDate target = q.periodEnd.minusYears(years);
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

    // ------------------------------------------------------------------ valor de mercado (D3)

    /** Cotacao de uma classe: o papel escolhido, o preco atual e os fechamentos. */
    private record ClassQuote(String ticker, BigDecimal current, NavigableMap<LocalDate, BigDecimal> closes) {

        /** {@code at} nulo = preco atual; senao o fechamento do ultimo pregao ate a data, se nao for antigo. */
        BigDecimal price(LocalDate at) {
            if (at == null) {
                return current;
            }
            Map.Entry<LocalDate, BigDecimal> e = closes.floorEntry(at);
            return e == null || e.getKey().isBefore(at.minusDays(MAX_PRICE_GAP_DAYS)) ? null : e.getValue();
        }
    }

    /** ON e PN do emissor; nulo a classe sem papel ativo no catalogo. */
    private record ClassQuotes(ClassQuote on, ClassQuote pn) {
    }

    /**
     * Os papeis ativos do emissor por classe (sem fracionario nem unit): ON e as series PN (PN, PNA, PNB...). Com mais
     * de um na classe, o de maior liquidez media (quantidade x fechamento) nos ultimos {@code liquidityMonths} meses
     * (D3: "PN em series usa a serie de maior liquidez").
     */
    private ClassQuotes loadClassQuotes(Context ctx) {
        record Candidate(long id, String ticker, boolean ordinary, BigDecimal current, BigDecimal liquidity) {
        }
        LocalDate since = LocalDate.now(clock).minusMonths(liquidityMonths);
        List<Candidate> all = jdbc.query("""
                SELECT a.id, a.ticker, a.share_class, a.current_price,
                       (SELECT AVG(h.volume * h.close_price) FROM asset_price_history h
                        WHERE h.asset_id = a.id AND h.reference_date > ? AND h.volume IS NOT NULL) AS liquidity
                FROM assets a
                WHERE a.issuer_id = ? AND a.active AND (a.share_class = 'ON' OR a.share_class LIKE 'PN%')""",
                (rs, i) -> new Candidate(rs.getLong("id"), rs.getString("ticker"), "ON".equals(rs.getString("share_class")),
                        rs.getBigDecimal("current_price"), rs.getBigDecimal("liquidity")),
                java.sql.Date.valueOf(since), ctx.asset.issuerId);
        java.util.Comparator<Candidate> best = java.util.Comparator
                .comparing((Candidate c) -> c.liquidity() == null ? BigDecimal.ZERO : c.liquidity()).reversed()
                .thenComparing(Candidate::ticker);
        ClassQuote[] chosen = new ClassQuote[2];
        for (boolean ordinary : new boolean[]{true, false}) {
            Candidate c = all.stream()
                    .filter(x -> x.ordinary() == ordinary && baseTicker(x.ticker()).equals(x.ticker().trim().toUpperCase()))
                    .sorted(best).findFirst().orElse(null);
            if (c != null) {
                NavigableMap<LocalDate, BigDecimal> closes = c.id() == ctx.asset.priceAssetId ? ctx.closes : closes(c.id());
                BigDecimal current = c.current() != null ? c.current()
                        : closes.isEmpty() ? null : closes.lastEntry().getValue();
                chosen[ordinary ? 0 : 1] = new ClassQuote(c.ticker(), current, closes);
            }
        }
        return new ClassQuotes(chosen[0], chosen[1]);
    }

    /**
     * Valor de mercado da companhia no balanco {@code q} (docs/planos/PLANO_TELA_ATIVOS.md, 2.5 e D3): acoes ON e PN
     * sem tesouraria (x {@code ratio}, os eventos depois do balanco) x o preco de cada classe — atual com
     * {@code at} nulo, o fechamento na data no historico. Classe sem cotacao propria usa o preco da outra, com a nota.
     * Unit nao entra: as classes que a compoem ja entram.
     */
    private Accounts withMarketValue(Context ctx, Accounts a, QuarterRow q, BigDecimal ratio, LocalDate at) {
        ClassQuotes c = ctx.classQuotes();
        if (c == null) {
            return a;
        }
        if (q.ordinaryOutstanding == null && q.preferredOutstanding == null) {
            return a.withMarketValue(null, "sem quantidade de acoes por classe (escala em revisao)");
        }
        long ordinary = q.ordinaryOutstanding == null ? 0 : q.ordinaryOutstanding;
        long preferred = q.preferredOutstanding == null ? 0 : q.preferredOutstanding;
        if (ordinary <= 0 && preferred <= 0) {
            return a.withMarketValue(null, "sem acoes em circulacao");
        }
        BigDecimal on = c.on() == null ? null : c.on().price(at);
        BigDecimal pn = c.pn() == null ? null : c.pn().price(at);
        BigDecimal total = BigDecimal.ZERO;
        String note = null;
        if (ordinary > 0) {
            BigDecimal price = on;
            if (price == null && pn != null) {
                price = pn;
                note = "ON sem negociacao: avaliada pelo preco de " + c.pn().ticker();
            }
            if (price == null) {
                return a.withMarketValue(null, "sem cotacao das acoes ON");
            }
            total = total.add(price.multiply(BigDecimal.valueOf(ordinary)));
        }
        if (preferred > 0) {
            BigDecimal price = pn;
            if (price == null && on != null) {
                price = on;
                note = "PN sem negociacao: avaliada pelo preco de " + c.on().ticker();
            }
            if (price == null) {
                return a.withMarketValue(null, "sem cotacao das acoes PN");
            }
            total = total.add(price.multiply(BigDecimal.valueOf(preferred)));
        }
        return a.withMarketValue(total.multiply(ratio == null ? BigDecimal.ONE : ratio, MC), note);
    }

    // ------------------------------------------------------------------ FII

    private FundBasis currentFund(Context ctx, List<MonthRow> months) {
        BigDecimal dividends = ctx.dividends12m(LocalDate.now(clock));
        MonthRow last = months.isEmpty() ? null : months.get(months.size() - 1);
        return fundBasis(last, ctx.currentPrice, dividends);
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
            FundBasis b = fundBasis(m, ctx.priceAt(end), ctx.dividends12m(end));
            out.add(point(period == Period.YEAR ? String.valueOf(m.month.getYear()) : quarterLabel(end),
                    IndicatorRules.fund(indicator, b), end));
        }
        return out;
    }

    private static FundBasis fundBasis(MonthRow m, BigDecimal price, BigDecimal dividends) {
        if (m == null) {
            return new FundBasis(null, null, null, null, null, null, null, null, null, price, dividends);
        }
        return new FundBasis(m.month, m.navPerShare, m.shareholders, m.realEstate, m.realEstateCompanies,
                m.receivables, m.totalInvested, m.liabilities, m.totalAssets, price, dividends);
    }

    // ------------------------------------------------------------------ dados

    private record AssetRow(long id, String ticker, Long issuerId, IssuerKind kind, String shareClass,
                            long priceAssetId, int unitShares, String statementLayout) {
    }

    /**
     * Uma linha de {@code company_quarters} com a quantidade por classe do documento ({@code share_counts}, sem
     * tesouraria, em unidades; nulas sem escala conferida) — base do valor de mercado (D3).
     */
    private record QuarterRow(LocalDate periodEnd, boolean annual, BigDecimal netIncomeParentTtm,
                              BigDecimal equityParent, Long sharesOutstanding, BigDecimal dividendsPaidTtm,
                              BigDecimal revenueTtm, BigDecimal ebitTtm, BigDecimal depreciationTtm,
                              BigDecimal grossProfitTtm, BigDecimal netIncomeTtm, BigDecimal pretaxIncomeTtm,
                              BigDecimal incomeTaxTtm, BigDecimal equity, BigDecimal totalAssets, BigDecimal cash,
                              BigDecimal grossDebt, BigDecimal currentAssets, BigDecimal currentLiabilities,
                              Long ordinaryOutstanding, Long preferredOutstanding) {
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
        /** Listagem em lote: so os indicadores das colunas (T11), sem as cotacoes das outras classes. */
        final boolean batch;
        private ClassQuotes classes;
        private boolean classesLoaded;

        /** Cotacoes das classes ON e PN do emissor (D3), lidas na primeira vez; nulo na listagem em lote. */
        ClassQuotes classQuotes() {
            if (batch) {
                return null;
            }
            if (!classesLoaded) {
                classes = loadClassQuotes(this);
                classesLoaded = true;
            }
            return classes;
        }

        /**
         * Com os dados ja lidos (listagem em lote): so o "Atual", que nao usa a serie de fechamentos —
         * {@link #priceAt} fica sem preco.
         */
        Context(AssetRow asset, BigDecimal currentPrice, LocalDate currentPriceDate, List<StockEvent> stockEvents,
                List<CashEvent> cashEvents) {
            this.asset = asset;
            this.batch = true;
            this.closes = new TreeMap<>();
            this.currentPrice = currentPrice;
            this.currentPriceDate = currentPriceDate;
            this.stockEvents = stockEvents;
            this.cashEvents = cashEvents;
        }

        Context(AssetRow asset) {
            this.asset = asset;
            this.batch = false;
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
        BigDecimal dividends12m(LocalDate ref) {
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
                SELECT a.id, a.ticker, a.issuer_id, i.kind, a.share_class, i.statement_layout
                FROM assets a LEFT JOIN issuers i ON i.id = a.issuer_id
                WHERE upper(a.ticker) = ?
                ORDER BY (a.issuer_id IS NULL), a.delisted_on DESC NULLS FIRST""",
                (rs, i) -> new AssetRow(rs.getLong("id"), rs.getString("ticker"),
                        (Long) rs.getObject("issuer_id", Long.class),
                        rs.getString("kind") == null ? null : IssuerKind.valueOf(rs.getString("kind")),
                        rs.getString("share_class"), 0, 0, rs.getString("statement_layout")), t);
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
        return new Context(new AssetRow(a.id, a.ticker, a.issuerId, a.kind, a.shareClass, priceAsset, unitShares,
                a.statementLayout));
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
                   q.shares_outstanding, q.dividends_paid_ttm, q.revenue_ttm, q.ebit_ttm,
                   q.depreciation_amortization_ttm, q.gross_profit_ttm, q.net_income_ttm, q.pretax_income_ttm,
                   q.income_tax_ttm, q.equity, q.total_assets, q.cash, q.gross_debt, q.current_assets,
                   q.current_liabilities,
                   (s.ordinary_shares - COALESCE(s.ordinary_treasury, 0)) * s.scale_factor AS ordinary_outstanding,
                   (s.preferred_shares - COALESCE(s.preferred_treasury, 0)) * s.scale_factor AS preferred_outstanding
            FROM company_quarters q JOIN cvm_documents d ON d.id = q.source_doc_id
            LEFT JOIN share_counts s ON s.document_id = q.source_doc_id""";

    /** O que os indicadores da listagem leem (T11): as colunas da F9. */
    private static final String LISTING_QUARTERS_SELECT = """
            SELECT q.issuer_id, q.period_end, d.doc_type, q.net_income_parent_ttm, q.equity_parent,
                   q.shares_outstanding, q.dividends_paid_ttm
            FROM company_quarters q JOIN cvm_documents d ON d.id = q.source_doc_id""";

    private static final String MONTH_COLUMNS = """
            reference_month, nav_per_share, shareholders, real_estate_total, real_estate_companies,
            receivables_total, total_invested, liabilities_total, total_assets, net_assets""";

    private static QuarterRow quarterRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new QuarterRow(rs.getDate("period_end").toLocalDate(), "DFP".equals(rs.getString("doc_type")),
                rs.getBigDecimal("net_income_parent_ttm"), rs.getBigDecimal("equity_parent"),
                (Long) rs.getObject("shares_outstanding", Long.class), rs.getBigDecimal("dividends_paid_ttm"),
                rs.getBigDecimal("revenue_ttm"), rs.getBigDecimal("ebit_ttm"),
                rs.getBigDecimal("depreciation_amortization_ttm"), rs.getBigDecimal("gross_profit_ttm"),
                rs.getBigDecimal("net_income_ttm"), rs.getBigDecimal("pretax_income_ttm"),
                rs.getBigDecimal("income_tax_ttm"), rs.getBigDecimal("equity"), rs.getBigDecimal("total_assets"),
                rs.getBigDecimal("cash"), rs.getBigDecimal("gross_debt"), rs.getBigDecimal("current_assets"),
                rs.getBigDecimal("current_liabilities"), (Long) rs.getObject("ordinary_outstanding", Long.class),
                (Long) rs.getObject("preferred_outstanding", Long.class));
    }

    private static QuarterRow listingQuarterRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new QuarterRow(rs.getDate("period_end").toLocalDate(), "DFP".equals(rs.getString("doc_type")),
                rs.getBigDecimal("net_income_parent_ttm"), rs.getBigDecimal("equity_parent"),
                (Long) rs.getObject("shares_outstanding", Long.class), rs.getBigDecimal("dividends_paid_ttm"),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
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
