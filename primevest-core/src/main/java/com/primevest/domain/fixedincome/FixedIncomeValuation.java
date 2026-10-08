package com.primevest.domain.fixedincome;

import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.calendar.BusinessCalendar;
import com.primevest.domain.marketindex.MarketIndex;
import com.primevest.domain.marketindex.MarketIndexRepository;
import com.primevest.domain.marketindex.MarketIndexValueRepository;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PositionCalculator;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
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
 * A curva da renda fixa privada aplicada a carteira (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R4): junta o titulo
 * (nota), as transacoes e as series dos indices e grava a valorizacao pelo {@link FixedIncomeSettlement#applyCurve}.
 * So a app-api chama (e dado do usuario). Tesouro nao passa aqui: e valorizado pelo preco oficial, como cotacao.
 *
 * <p>As series sao carregadas uma vez por rodada ({@link Series}); a data de referencia e o ultimo CDI publicado (D14)
 * — sem CDI carregado, o ultimo dia util antes de hoje.</p>
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

    public FixedIncomeValuation(MarketIndexRepository indices, MarketIndexValueRepository values,
                                PositionCalculator positionCalculator, FixedIncomeSettlement settlement,
                                PortfolioAssetRepository portfolioAssetRepository) {
        this.indices = indices;
        this.values = values;
        this.positionCalculator = positionCalculator;
        this.settlement = settlement;
        this.portfolioAssetRepository = portfolioAssetRepository;
    }

    /** As series de uma rodada, carregadas sob demanda e reaproveitadas entre os titulos. */
    public final class Series {
        private final Map<Indexer, NavigableMap<LocalDate, BigDecimal>> cache = new EnumMap<>(Indexer.class);
        private final LocalDate reference;

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

    /** O saldo na curva do titulo (nulo se ele nao e de renda fixa privada). */
    public FixedIncomeCurve.Result value(PortfolioAsset pa, List<Transaction> chronological, Series series) {
        if (!usesCurve(pa)) {
            return null;
        }
        FixedIncomeNote note = pa.getNote();
        List<FixedIncomeCurve.Flow> flows = new ArrayList<>();
        for (Transaction tx : chronological) {
            BigDecimal v = tx.getTotalAmount() != null ? tx.getTotalAmount() : BigDecimal.ZERO;
            flows.add(new FixedIncomeCurve.Flow(tx.getTransactionDate(), tx.getType() == TransactionType.BUY ? v : v.negate()));
        }
        Indexer idx = note.getIndexer();
        return FixedIncomeCurve.value(
                new FixedIncomeCurve.Terms(note.getRateType(), idx, note.getIndexerRate(), note.getMaturityDate()),
                flows, series.reference(),
                FixedIncomeCurve.isDaily(idx) ? series.of(idx) : null,
                FixedIncomeCurve.isMonthly(idx) ? series.of(idx) : null);
    }

    /** Aplica a curva numa posicao ja carregada (sem gravar). Depois de uma transacao e nas rodadas. */
    public boolean apply(PortfolioAsset pa, List<Transaction> chronological, Series series) {
        if (!usesCurve(pa)) {
            return false;
        }
        return settlement.applyCurve(pa, chronological, value(pa, chronological, series));
    }

    /**
     * Rodada: aplica e grava a curva nas posicoes de renda fixa privada em aberto. Erro numa posicao e registrado e
     * nao para as outras. Chamar dentro de uma transacao (navega nota, ativo e transacoes).
     *
     * @return posicoes cuja valorizacao mudou
     */
    public int refresh(Collection<PortfolioAsset> positions) {
        Series series = null;
        int changed = 0;
        for (PortfolioAsset pa : positions) {
            if (!usesCurve(pa) || pa.isSettled()) {
                continue;
            }
            try {
                if (series == null) {
                    series = series();
                }
                if (apply(pa, positionCalculator.chronologicalTransactions(pa), series)) {
                    portfolioAssetRepository.save(pa);
                    changed++;
                }
            } catch (RuntimeException e) {
                log.error("Curva da renda fixa: posicao {} nao recalculada: {}", pa.getId(), e.getMessage(), e);
            }
        }
        return changed;
    }

    /** Todas as posicoes de renda fixa privada em aberto, de todas as carteiras. */
    public int refreshAll() {
        return refresh(portfolioAssetRepository.findAllWithAsset());
    }
}
