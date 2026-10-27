package com.activest.domain.transaction;

import com.activest.domain.asset.Asset;
import com.activest.domain.corporateaction.StockEventStep;
import com.activest.domain.currency.CurrencyRepository;
import com.activest.domain.currency.ExchangeRateService;
import com.activest.domain.currency.ForeignTax;
import com.activest.domain.currency.ForeignTaxCalculator;
import com.activest.domain.currency.ForeignTaxEvent;
import com.activest.domain.currency.ForeignTaxRule;
import com.activest.domain.currency.ForeignTaxRuleService;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.Position;
import com.activest.domain.portfolio.PositionCalculator;
import com.activest.domain.portfolio.PositionEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * IR estimado na venda de ativo do exterior (docs/planos/PLANO_CADASTRO_MOEDAS.md, C5, D20, D21).
 *
 * <p>Por venda, na ordem da posição: custo = preço médio em reais antes da venda × cotas vendidas (o preço médio já
 * está em reais, com o câmbio de cada compra); ganho = total da venda em reais − custo — a variação cambial entra
 * sozinha. IR = regra {@code IR_FOREIGN}/{@code SALE} vigente na data: 15% desde 2024 (Lei 14.754/2023); antes, ganho
 * de capital com isenção das vendas do mês até R$ 35 mil e faixas progressivas. Grava {@code income_tax},
 * {@code net_amount} (total − IR) e {@code iof_tax} = 0 — os mesmos campos que a venda de renda fixa usa, então a
 * Rentabilidade líquida e as telas já os leem. Perda = IR 0.</p>
 *
 * <p>É estimativa por operação: a apuração oficial é anual e compensa perdas do ano (D20). Taxas de corretagem não
 * entram no ganho (regra do projeto).</p>
 */
@Component
@RequiredArgsConstructor
public class ForeignSaleTax {

    private final PositionCalculator positionCalculator;
    private final ForeignTaxRuleService taxRules;
    private final ExchangeRateService exchangeRates;
    private final CurrencyRepository currencies;
    private final TransactionRepository transactions;
    private final JdbcTemplate jdbc;

    public boolean applies(Asset asset) {
        return asset != null && !exchangeRates.isBase(asset.getCurrencyCode());
    }

    /** Refaz o IR estimado de todas as vendas da posição. Devolve as vendas alteradas. */
    public int recompute(PortfolioAsset pa, List<Transaction> chronological, List<StockEventStep> stockEvents) {
        Asset asset = pa.getAsset();
        List<ForeignTaxRule> rules = taxRules.all();
        String country = asset.getCountryCode() != null ? asset.getCountryCode()
            : currencies.findById(asset.getCurrencyCode()).map(c -> c.getCountryCode()).orElse(null);
        Long typeId = asset.getAssetType() != null ? asset.getAssetType().getId() : null;
        PositionEvents events = PositionEvents.ofStockEvents(stockEvents);
        List<Transaction> before = new ArrayList<>();
        int changed = 0;
        for (Transaction tx : chronological) {
            if (tx.getType() == TransactionType.SELL && tx.getShares() != null) {
                Position p = positionCalculator.calculate(false, before, events, tx.getTransactionDate());
                BigDecimal cost = p.averagePrice().multiply(tx.getShares()).setScale(2, RoundingMode.HALF_EVEN);
                BigDecimal gain = tx.getTotalAmount().subtract(cost);
                ForeignTaxRule rule = ForeignTaxRuleService.select(rules, ForeignTax.IR_FOREIGN, ForeignTaxEvent.SALE,
                    country, typeId, tx.getTransactionDate()).orElse(null);
                BigDecimal ir = rule == null ? BigDecimal.ZERO.setScale(2)
                    : ForeignTaxCalculator.saleIncomeTax(gain, monthSales(pa, tx.getTransactionDate()), rule);
                BigDecimal net = tx.getTotalAmount().subtract(ir);
                if (differs(tx.getIncomeTax(), ir) || differs(tx.getNetAmount(), net) || tx.getIofTax() == null) {
                    tx.setIncomeTax(ir);
                    tx.setNetAmount(net);
                    tx.setIofTax(BigDecimal.ZERO);
                    transactions.save(tx);
                    changed++;
                }
            }
            before.add(tx);
        }
        return changed;
    }

    /** Vendas de ativos do exterior da carteira no mês da data, em reais (isenção mensal do regime anterior). */
    private BigDecimal monthSales(PortfolioAsset pa, LocalDate date) {
        LocalDate first = date.withDayOfMonth(1);
        BigDecimal sum = jdbc.queryForObject("""
            SELECT COALESCE(SUM(t.total_amount), 0) FROM transactions t
              JOIN portfolio_assets p ON p.id = t.portfolio_asset_id
              JOIN assets a ON a.id = p.asset_id
             WHERE p.portfolio_id = ? AND t.type = 'SELL' AND a.currency_code <> ?
               AND t.transaction_date BETWEEN ? AND ?""", BigDecimal.class,
            pa.getPortfolio().getId(), exchangeRates.baseCode(), Date.valueOf(first),
            Date.valueOf(first.withDayOfMonth(first.lengthOfMonth())));
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a != b : a.compareTo(b) != 0;
    }
}
