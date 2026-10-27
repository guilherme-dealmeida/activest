package com.activest.domain.fund;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.PriceSource;
import com.activest.domain.fixedincome.FixedIncomeTaxRuleRepository;
import com.activest.domain.fixedincome.TaxRegime;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.transaction.Transaction;
import com.activest.domain.transaction.TransactionRepository;
import com.activest.domain.transaction.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Come-cotas e imposto dos resgates de uma posição de fundo (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, F7, D22): regera
 * {@code fund_tax_events} dos lotes da posição e grava IR, IOF e líquido em cada resgate. Chamado depois de toda escrita
 * na posição e quando a série de cotas muda (catálogo), antes da quantidade ser recalculada — a quantidade lê as cotas
 * retidas ({@code PositionCalculator.withholdingsOf}).
 */
@Slf4j
@Service
public class FundTaxService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    /** Cota do come-cotas: a do dia, ou a última até 7 dias antes (atraso do informe). */
    private static final int QUOTE_LOOKBACK_DAYS = 7;

    private final InvestmentFundRepository funds;
    private final FundTaxEventRepository events;
    private final FixedIncomeTaxRuleRepository rules;
    private final TransactionRepository transactions;
    private final JdbcTemplate jdbc;

    public FundTaxService(InvestmentFundRepository funds, FundTaxEventRepository events,
                          FixedIncomeTaxRuleRepository rules, TransactionRepository transactions, JdbcTemplate jdbc) {
        this.funds = funds;
        this.events = events;
        this.rules = rules;
        this.transactions = transactions;
        this.jdbc = jdbc;
    }

    public static boolean applies(Asset asset) {
        return asset != null && asset.getAssetType() != null && asset.getAssetType().getPriceSource() == PriceSource.CVM;
    }

    /** Refaz o come-cotas e o imposto dos resgates da posição. */
    public void recompute(PortfolioAsset pa, List<Transaction> chronological) {
        Asset asset = pa.getAsset();
        if (!applies(asset) || pa.getId() == null) {
            return;
        }
        TaxRegime regime = regimeOf(asset);
        List<FundTax.Tx> txs = txs(chronological);
        FundTax.Result result = FundTax.calculate(regime, rules.findAll(), txs, d -> quoteOn(asset.getId(), d),
            LocalDate.now(ZONE));

        List<UUID> lots = chronological.stream().map(Transaction::getId).toList();
        if (!lots.isEmpty()) {
            events.deleteByLotTransactionIdIn(lots);
        }
        UUID userId = pa.getPortfolio().getUser().getId();
        for (FundTax.ComeCotas c : result.comeCotas()) {
            events.save(FundTaxEvent.builder().userId(userId).assetId(asset.getId()).lotTransactionId(c.lot())
                .eventDate(c.date()).quote(c.quote()).baseQuote(c.baseQuote()).sharesBefore(c.sharesBefore())
                .sharesWithheld(c.sharesWithheld()).taxAmount(c.tax()).rate(c.rate()).build());
        }
        Map<UUID, FundTax.SaleTax> sales = result.sales();
        for (Transaction t : chronological) {
            FundTax.SaleTax s = sales.get(t.getId());
            if (t.getType() != TransactionType.SELL || s == null) {
                continue;
            }
            if (!same(t.getIncomeTax(), s.incomeTax()) || !same(t.getIofTax(), s.iof())
                    || !same(t.getNetAmount(), s.net())) {
                t.setIncomeTax(s.incomeTax());
                t.setIofTax(s.iof());
                t.setNetAmount(s.net());
                transactions.save(t);
            }
        }
        if (!result.comeCotas().isEmpty()) {
            log.debug("Fundo {}: {} come-cotas na posição {}", asset.getTicker(), result.comeCotas().size(), pa.getId());
        }
    }

    /**
     * Líquido estimado de um resgate total hoje à cota atual (C10), sem gravar nada; nulo sem cota ou sem cotas.
     * Previdência: o bruto (IR do plano não calculado — D21).
     */
    public FundTax.SaleTax estimate(PortfolioAsset pa, List<Transaction> chronological) {
        Asset asset = pa.getAsset();
        if (!applies(asset) || asset.getCurrentPrice() == null || asset.getCurrentPrice().signum() <= 0) {
            return null;
        }
        TaxRegime regime = regimeOf(asset);
        List<com.activest.domain.fixedincome.FixedIncomeTaxRule> all = rules.findAll();
        LocalDate today = LocalDate.now(ZONE);
        FundTax.Result result = FundTax.calculate(regime, all, txs(chronological), d -> quoteOn(asset.getId(), d), today);
        if (result.openLots().isEmpty()) {
            return null;
        }
        return FundTax.estimate(regime, all, result.openLots(), asset.getCurrentPrice(), today);
    }

    /**
     * Prévia do resgate (C7, D24), sem gravar: a candidata entra no fim da linha do tempo da posição (cotas = bruto ÷
     * cota) e sai com IR, IOF e líquido pelo PEPS, já com o come-cotas. {@code chronological} sem a transação em edição.
     */
    public FundTax.SaleTax previewSale(PortfolioAsset pa, List<Transaction> chronological, LocalDate date,
                                       BigDecimal gross, BigDecimal quote) {
        Asset asset = pa.getAsset();
        TaxRegime regime = regimeOf(asset);
        java.util.ArrayList<FundTax.Tx> txs = new java.util.ArrayList<>(txs(chronological));
        UUID candidate = UUID.randomUUID();
        BigDecimal shares = gross.divide(quote, 8, java.math.RoundingMode.HALF_EVEN);
        txs.add(new FundTax.Tx(candidate, false, date, shares, quote, gross));
        FundTax.Result result = FundTax.calculate(regime, rules.findAll(), txs, d -> quoteOn(asset.getId(), d),
            LocalDate.now(ZONE));
        return result.sales().get(candidate);
    }

    /** Regime de imposto do fundo (C8): nulo = Previdência, sem imposto. */
    public TaxRegime regimeOf(Asset asset) {
        InvestmentFund fund = funds.findById(asset.getId()).orElse(null);
        String category = asset.getAssetCategory() != null ? asset.getAssetCategory().getName() : null;
        return FundTax.regime(category, fund == null ? null : fund.getLongTermTax());
    }

    private static List<FundTax.Tx> txs(List<Transaction> chronological) {
        return chronological.stream()
            .filter(t -> t.getShares() != null && t.getPricePerShare() != null)
            .map(t -> new FundTax.Tx(t.getId(), t.getType() == TransactionType.BUY, t.getTransactionDate(),
                t.getShares(), t.getPricePerShare(), t.getTotalAmount()))
            .toList();
    }

    private BigDecimal quoteOn(Long assetId, LocalDate date) {
        return jdbc.query("""
                SELECT close_price FROM asset_price_history
                 WHERE asset_id = ? AND reference_date <= ? AND reference_date >= ?
                 ORDER BY reference_date DESC LIMIT 1""",
            rs -> rs.next() ? rs.getBigDecimal(1) : null, assetId, java.sql.Date.valueOf(date),
            java.sql.Date.valueOf(date.minusDays(QUOTE_LOOKBACK_DAYS)));
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
