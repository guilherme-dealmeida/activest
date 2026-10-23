package com.activest.domain.fixedincome;

import com.activest.domain.asset.AssetType;
import com.activest.domain.asset.Indexer;
import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.marketindex.MarketIndex;
import com.activest.domain.marketindex.MarketIndexRepository;
import com.activest.domain.marketindex.MarketIndexValueRepository;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.PortfolioAssetRepository;
import com.activest.domain.portfolio.PositionCalculator;
import com.activest.domain.transaction.Transaction;
import com.activest.domain.transaction.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A curva da renda fixa privada aplicada a carteira (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R4), aporte a aporte
 * (docs/planos/PLANO_VENDA_RENDA_FIXA.md, V2/V3): junta o titulo (nota), as transacoes, as series dos indices e a
 * tabela de IR e IOF, roda os {@link FixedIncomeLots} e grava a valorizacao pelo {@link FixedIncomeSettlement#applyCurve}.
 * So a app-api chama (e dado do usuario). Tesouro nao passa aqui: e valorizado pelo preco oficial, como cotacao.
 *
 * <p>Venda de renda fixa e lancada pelo LIQUIDO (R3): o bruto, o IR e o IOF de cada venda sao derivados dos lotes e
 * regravados por {@link #syncSales} a cada escrita no titulo e a cada rodada (R10) — editar uma compra antiga refaz
 * todas as vendas seguintes.</p>
 *
 * <p>As series e a tabela de impostos sao carregadas uma vez por rodada ({@link Series}); a data de referencia e o
 * ultimo CDI publicado (D14) — sem CDI carregado, o ultimo dia util antes de hoje.</p>
 */
@Slf4j
@Component
public class FixedIncomeValuation {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate FIRST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LAST = LocalDate.of(2999, 12, 31);

    private final MarketIndexRepository indices;
    private final MarketIndexValueRepository values;
    private final PositionCalculator positionCalculator;
    private final FixedIncomeSettlement settlement;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final FixedIncomeTaxRuleRepository taxRules;

    public FixedIncomeValuation(MarketIndexRepository indices, MarketIndexValueRepository values,
                                PositionCalculator positionCalculator, FixedIncomeSettlement settlement,
                                PortfolioAssetRepository portfolioAssetRepository,
                                FixedIncomeTaxRuleRepository taxRules) {
        this.indices = indices;
        this.values = values;
        this.positionCalculator = positionCalculator;
        this.settlement = settlement;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.taxRules = taxRules;
    }

    /** As series e a tabela de impostos de uma rodada, carregadas sob demanda e reaproveitadas entre os titulos. */
    public final class Series {
        private final Map<Indexer, NavigableMap<LocalDate, BigDecimal>> cache = new EnumMap<>(Indexer.class);
        private final LocalDate reference;
        private FixedIncomeTaxTable taxes;

        private Series() {
            NavigableMap<LocalDate, BigDecimal> cdi = of(Indexer.CDI);
            if (!cdi.isEmpty()) {
                reference = cdi.lastKey();
            } else {
                LocalDate d = LocalDate.now(ZONE).minusDays(1);
                while (!BusinessCalendar.isBusinessDay(d)) {
                    d = d.minusDays(1);
                }
                reference = d;
            }
        }

        /** Data de referencia da rodada (o ultimo CDI publicado). */
        public LocalDate reference() {
            return reference;
        }

        /** As regras de IR e IOF (V52), uma vez por rodada. */
        public FixedIncomeTaxTable taxes() {
            if (taxes == null) {
                taxes = new FixedIncomeTaxTable(taxRules.findAll());
            }
            return taxes;
        }

        NavigableMap<LocalDate, BigDecimal> of(Indexer indexer) {
            if (indexer == null) {
                return new TreeMap<>();
            }
            return cache.computeIfAbsent(indexer, i -> {
                NavigableMap<LocalDate, BigDecimal> m = new TreeMap<>();
                indices.findByCode(i.name()).map(MarketIndex::getId).ifPresent(id ->
                        values.findByIndexIdAndReferenceDateBetweenOrderByReferenceDate(id, FIRST, LAST)
                                .forEach(v -> m.put(v.getReferenceDate(), v.getValue())));
                return m;
            });
        }
    }

    public Series series() {
        return new Series();
    }

    /** Titulo de renda fixa privada (valorizado pela curva). */
    public static boolean usesCurve(PortfolioAsset pa) {
        AssetType type = pa.getAsset() != null ? pa.getAsset().getAssetType() : null;
        return type != null && type.isValuedByTotal() && pa.getNote() != null;
    }

    /**
     * A regra de saida dos lotes do titulo: a da DISTRIBUIDORA dele (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, R12;
     * PLANO_VENDA_RENDA_FIXA D1). Padrao PROPORCIONAL; Nu Financeira UEPS.
     */
    public static SaleRule saleRule(PortfolioAsset pa) {
        FixedIncomeNote note = pa != null ? pa.getNote() : null;
        FixedIncomeIssuer distributor = note != null ? note.getDistributor() : null;
        return distributor != null && distributor.getSaleRule() != null ? distributor.getSaleRule() : SaleRule.DEFAULT;
    }

    /** Os movimentos do titulo para os lotes: compra pelo aplicado, venda pelo liquido (ou pelo bruto, se nao houver). */
    public static List<FixedIncomeLots.Move> moves(List<Transaction> chronological) {
        List<FixedIncomeLots.Move> moves = new ArrayList<>();
        for (Transaction tx : chronological) {
            BigDecimal v = tx.getTotalAmount() != null ? tx.getTotalAmount() : BigDecimal.ZERO;
            if (tx.getType() == TransactionType.BUY) {
                moves.add(FixedIncomeLots.Move.buy(tx.getId(), tx.getTransactionDate(), v));
            } else if (tx.getNetAmount() != null) {
                moves.add(FixedIncomeLots.Move.sellNet(tx.getId(), tx.getTransactionDate(), tx.getNetAmount(),
                        tx.isFullSettlement()));
            } else {
                moves.add(FixedIncomeLots.Move.sellGross(tx.getId(), tx.getTransactionDate(), v,
                        tx.isFullSettlement()));
            }
        }
        return moves;
    }

    /** O titulo aporte a aporte na data de referencia da rodada (nulo se ele nao e de renda fixa privada). */
    public FixedIncomeLots.Result lots(PortfolioAsset pa, List<Transaction> chronological, Series series) {
        return usesCurve(pa) ? run(pa, moves(chronological), series.reference(), series) : null;
    }

    /**
     * Os lotes com movimentos quaisquer — a previa de uma venda ainda nao gravada (R9) usa isto com a venda
     * candidata no fim.
     */
    public FixedIncomeLots.Result run(PortfolioAsset pa, List<FixedIncomeLots.Move> moves, LocalDate reference,
                                      Series series) {
        FixedIncomeNote note = pa.getNote();
        Indexer idx = note.getIndexer();
        return FixedIncomeLots.run(
                new FixedIncomeCurve.Terms(note.getRateType(), idx, note.getIndexerRate(), note.getMaturityDate()),
                moves, reference,
                FixedIncomeCurve.isDaily(idx) ? series.of(idx) : null,
                FixedIncomeCurve.isMonthly(idx) ? series.of(idx) : null,
                series.taxes(), pa.getAsset().getId(), saleRule(pa));
    }

    /**
     * O titulo dia a dia de {@code from} a {@code to} (docs/planos/PLANO_RENTABILIDADE.md, 4.2): bruto e liquido estimado
     * de cada dia, numa passada so. Nulo se ele nao e de renda fixa privada.
     */
    public FixedIncomeLots.Path path(PortfolioAsset pa, List<Transaction> chronological, LocalDate from, LocalDate to,
                                     Series series) {
        if (!usesCurve(pa)) {
            return null;
        }
        FixedIncomeNote note = pa.getNote();
        Indexer idx = note.getIndexer();
        return FixedIncomeLots.path(
                new FixedIncomeCurve.Terms(note.getRateType(), idx, note.getIndexerRate(), note.getMaturityDate()),
                moves(chronological), from, to,
                FixedIncomeCurve.isDaily(idx) ? series.of(idx) : null,
                FixedIncomeCurve.isMonthly(idx) ? series.of(idx) : null,
                series.taxes(), pa.getAsset().getId(), saleRule(pa));
    }

    /** O saldo na curva do titulo (nulo se ele nao e de renda fixa privada). */
    public FixedIncomeCurve.Result value(PortfolioAsset pa, List<Transaction> chronological, Series series) {
        return curveOf(lots(pa, chronological, series));
    }

    static FixedIncomeCurve.Result curveOf(FixedIncomeLots.Result lots) {
        if (lots == null) {
            return null;
        }
        if (lots.status() == FixedIncomeCurve.Status.UNSUPPORTED) {
            return FixedIncomeCurve.Result.unsupported(lots.reason());
        }
        return new FixedIncomeCurve.Result(lots.status(), lots.balance(), lots.referenceDate(), lots.estimated(), null);
    }

    /**
     * Regrava bruto, IR, IOF e ajuste das vendas de renda fixa a partir do liquido (R3, R8, R10). As entidades sao
     * as gerenciadas da transacao em curso: a alteracao vai ao banco no commit.
     *
     * @return se alguma venda mudou
     */
    public boolean syncSales(PortfolioAsset pa, List<Transaction> chronological, Series series) {
        FixedIncomeLots.Result r = lots(pa, chronological, series);
        return r != null && syncSales(chronological, r);
    }

    static boolean syncSales(List<Transaction> chronological, FixedIncomeLots.Result r) {
        boolean changed = false;
        for (Transaction tx : chronological) {
            if (tx.getType() != TransactionType.SELL) {
                continue;
            }
            FixedIncomeLots.SaleOutcome out = r.sale(tx.getId());
            if (out == null) {
                continue;
            }
            BigDecimal adjustment = tx.isFullSettlement() ? out.adjustment() : null;
            if (!same(tx.getTotalAmount(), out.gross()) || !same(tx.getIncomeTax(), out.ir())
                    || !same(tx.getIofTax(), out.iof()) || !same(tx.getNetAmount(), out.net())
                    || !same(tx.getSettlementAdjustment(), adjustment)) {
                tx.setTotalAmount(out.gross());
                tx.setNetAmount(out.net());
                tx.setIncomeTax(out.ir());
                tx.setIofTax(out.iof());
                tx.setSettlementAdjustment(adjustment);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** Aplica a curva numa posicao ja carregada (sem gravar). Depois de uma transacao e nas rodadas. */
    public boolean apply(PortfolioAsset pa, List<Transaction> chronological, Series series) {
        if (!usesCurve(pa)) {
            return false;
        }
        FixedIncomeLots.Result r = lots(pa, chronological, series);
        boolean sales = syncSales(chronological, r);
        return settlement.applyCurve(pa, chronological, curveOf(r)) || sales;
    }

    /**
     * Rodada: regrava as vendas e a curva nas posicoes de renda fixa privada. Erro numa posicao e registrado e nao
     * para as outras. Chamar dentro de uma transacao (navega nota, ativo e transacoes).
     *
     * <p>A posicao liquidada tambem passa aqui: as vendas dela podem ter mudado de bruto (lancadas pelo liquido, D2),
     * e a valorizacao da liquidada e a do resgate (regra ja existente), refeita pelo {@code recompute}.</p>
     *
     * @return posicoes cuja valorizacao ou vendas mudaram
     */
    public int refresh(Collection<PortfolioAsset> positions) {
        Series series = null;
        int changed = 0;
        for (PortfolioAsset pa : positions) {
            if (!usesCurve(pa)) {
                continue;
            }
            try {
                if (series == null) {
                    series = series();
                }
                List<Transaction> chronological = positionCalculator.chronologicalTransactions(pa);
                boolean anyChanged;
                if (pa.isSettled()) {
                    anyChanged = syncSales(pa, chronological, series);
                    if (anyChanged) {
                        settlement.recompute(pa, chronological, false);
                    }
                } else {
                    anyChanged = apply(pa, chronological, series);
                }
                if (anyChanged) {
                    portfolioAssetRepository.save(pa);
                    changed++;
                }
            } catch (RuntimeException e) {
                log.error("Curva da renda fixa: posicao {} nao recalculada: {}", pa.getId(), e.getMessage(), e);
            }
        }
        return changed;
    }

    /** Todas as posicoes de renda fixa privada, de todas as carteiras. */
    public int refreshAll() {
        return refresh(portfolioAssetRepository.findAllWithAsset());
    }
}
