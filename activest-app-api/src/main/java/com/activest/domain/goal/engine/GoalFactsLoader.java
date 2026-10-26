package com.activest.domain.goal.engine;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.asset.PriceSource;
import com.activest.domain.dividend.Dividend;
import com.activest.domain.dividend.DividendCalendar;
import com.activest.domain.dividend.DividendRepository;
import com.activest.domain.fixedincome.FixedIncomeNote;
import com.activest.domain.marketindex.IndexPeriodicity;
import com.activest.domain.marketindex.IndexPoint;
import com.activest.domain.marketindex.MarketIndex;
import com.activest.domain.marketindex.MarketIndexCalculator;
import com.activest.domain.marketindex.MarketIndexRepository;
import com.activest.domain.marketindex.MarketIndexValueRepository;
import com.activest.domain.performance.BenchmarkData;
import com.activest.domain.performance.PortfolioTimeline;
import com.activest.domain.performance.PortfolioTimelineBuilder;
import com.activest.domain.portfolio.Portfolio;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.PortfolioAssetRepository;
import com.activest.domain.portfolio.PortfolioRepository;
import com.activest.domain.portfolio.PortfolioService;
import com.activest.domain.portfolio.dto.PortfolioAssetDetailDTO;
import com.activest.domain.strategy.StrategyService;
import com.activest.domain.strategy.dto.RebalanceDTO;
import com.activest.domain.transaction.Transaction;
import com.activest.domain.transaction.TransactionRepository;
import com.activest.domain.transaction.TransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * Carrega os {@link GoalFacts} de um usuario (docs/planos/PLANO_METAS.md, 5.4). So le: as telas que ja existem sao a
 * fonte de cada numero (G7) — a Carteira para o valor atual, a regra da tela Dividendos para os proventos, o
 * Rebalanceamento para a estrategia e a linha do tempo da Rentabilidade (em cache por usuario) para a serie.
 */
@Component
public class GoalFactsLoader {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PortfolioService portfolioService;
    private final TransactionRepository transactionRepository;
    private final DividendRepository dividendRepository;
    private final DividendCalendar dividendCalendar;
    private final StrategyService strategyService;
    private final PortfolioTimelineBuilder timelineBuilder;
    private final BenchmarkData benchmarkData;
    private final JdbcTemplate jdbc;
    private final MarketIndexRepository marketIndices;
    private final MarketIndexValueRepository marketIndexValues;
    private final AssetRepository assetRepository;

    public GoalFactsLoader(PortfolioRepository portfolioRepository, PortfolioAssetRepository portfolioAssetRepository,
                           PortfolioService portfolioService, TransactionRepository transactionRepository,
                           DividendRepository dividendRepository, DividendCalendar dividendCalendar,
                           StrategyService strategyService, PortfolioTimelineBuilder timelineBuilder,
                           BenchmarkData benchmarkData, JdbcTemplate jdbc, MarketIndexRepository marketIndices,
                           MarketIndexValueRepository marketIndexValues, AssetRepository assetRepository) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.portfolioService = portfolioService;
        this.transactionRepository = transactionRepository;
        this.dividendRepository = dividendRepository;
        this.dividendCalendar = dividendCalendar;
        this.strategyService = strategyService;
        this.timelineBuilder = timelineBuilder;
        this.benchmarkData = benchmarkData;
        this.jdbc = jdbc;
        this.marketIndices = marketIndices;
        this.marketIndexValues = marketIndexValues;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public GoalFacts load(UUID userId) {
        LocalDate today = dividendCalendar.today();
        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        Supplier<PortfolioTimeline> timeline = memo(() -> timelineBuilder.timeline(userId));
        Supplier<RebalanceDTO> rebalance = memo(() -> strategyService.getRebalanceSuggestion(userId));
        if (portfolio == null) {
            return new GoalFacts(today, Map.of(), List.of(), List.of(), List.of(), rebalance, timeline, benchmarkData,
                    indices(), 0, Map.of(), this::catalogPrice);
        }
        UUID pid = portfolio.getId();

        Map<UUID, PositionInfo> positions = new LinkedHashMap<>();
        Map<UUID, Asset> assets = new HashMap<>();
        for (PortfolioAsset pa : portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(pid)) {
            assets.put(pa.getId(), pa.getAsset());
            positions.put(pa.getId(), info(pa));
        }

        List<GoalFacts.Holding> holdings = new ArrayList<>();
        for (PortfolioAssetDetailDTO d : portfolioService.getPortfolioAssets(userId)) {
            PositionInfo info = positions.get(d.id());
            if (info == null) {
                continue;
            }
            Asset a = assets.get(d.id());
            boolean rf = "RENDA_FIXA".equals(info.typeName());
            BigDecimal cost = rf ? nz(d.totalInvested()).subtract(nz(d.totalSold())) : nz(d.totalInvested());
            boolean noPrice = !info.fixedIncome() && a.getAssetType() != null
                    && a.getAssetType().getPriceSource() == PriceSource.MANUAL;
            LocalDate priceDate = info.fixedIncome() ? d.valuationDate()
                    : a.getPriceUpdatedAt() != null ? a.getPriceUpdatedAt().toLocalDate() : null;
            holdings.add(new GoalFacts.Holding(info, nz(d.currentValue()), cost, d.totalShares(), d.currentPrice(),
                    noPrice ? null : priceDate, noPrice, "SEM_CURVA".equals(d.valuationBasis()),
                    d.pendingBonusCosts() != null ? d.pendingBonusCosts().size() : 0));
        }

        List<GoalFacts.Flow> flows = new ArrayList<>();
        for (Transaction t : transactionRepository.findByPortfolioAssetPortfolioIdOrderByTransactionDateDesc(pid)) {
            BigDecimal amount = t.getType() == TransactionType.SELL && t.getNetAmount() != null
                    ? t.getNetAmount() : nz(t.getTotalAmount());
            flows.add(new GoalFacts.Flow(t.getPortfolioAsset().getId(), t.getTransactionDate(), t.getType(), amount,
                    t.getShares()));
        }
        flows.sort(Comparator.comparing(GoalFacts.Flow::date));

        List<GoalFacts.Income> incomes = new ArrayList<>();
        for (Dividend d : dividendRepository.findByPortfolioIdWithAsset(pid)) {
            incomes.add(new GoalFacts.Income(d.getPortfolioAsset().getId(), d.getExDate(), d.getPaymentDate(),
                    d.netOrGross(), d.getTotalAmount(),
                    d.getNetAmountPerShare() != null ? d.getNetAmountPerShare() : d.getAmountPerShare(), d.getType(),
                    d.isReceived(today)));
        }

        Integer pending = jdbc.queryForObject("""
                SELECT count(*) FROM notifications
                 WHERE user_id = ? AND kind = 'INTEGRACAO_AGUARDANDO' AND status = 'NAO_RESOLVIDA'""",
                Integer.class, userId);

        Map<String, LocalDate> sources = new LinkedHashMap<>();
        holdings.stream().map(GoalFacts.Holding::priceDate).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .ifPresent(d -> sources.put("Cotações e valorização", d));
        flows.stream().map(GoalFacts.Flow::date).max(Comparator.naturalOrder())
                .ifPresent(d -> sources.put("Última transação", d));
        incomes.stream().filter(GoalFacts.Income::received).map(GoalFacts.Income::refDate).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).ifPresent(d -> sources.put("Último provento recebido", d));

        return new GoalFacts(today, positions, holdings, flows, incomes, rebalance, timeline, benchmarkData,
                indices(), pending != null ? pending : 0, sources, this::catalogPrice);
    }

    /**
     * Acumulado de um indice de taxa (CDI, IPCA) pela mesma regra do {@code /indices/{code}/accumulated}
     * ({@link MarketIndexCalculator}). Le direto, sem excecao: um erro dentro da transacao a marcaria para rollback.
     */
    private GoalFacts.IndexReader indices() {
        Map<String, Optional<GoalFacts.Accumulated>> cache = new HashMap<>();
        return (code, from, to) -> cache.computeIfAbsent(code + from + to, k -> {
            MarketIndex index = marketIndices.findByCode(code).filter(MarketIndex::isActive).orElse(null);
            if (index == null || !index.getUnit().isCompounding() || to.isBefore(from)) {
                return Optional.empty();
            }
            LocalDate start = index.getPeriodicity() == IndexPeriodicity.MENSAL ? from.withDayOfMonth(1) : from;
            List<IndexPoint> rates = marketIndexValues
                    .findByIndexIdAndReferenceDateBetweenOrderByReferenceDate(index.getId(), start, to)
                    .stream().map(IndexPoint::of).toList();
            if (rates.isEmpty()) {
                return Optional.empty();
            }
            try {
                MarketIndexCalculator.Accumulated a = MarketIndexCalculator.compound(index.getUnit(), rates, null, null);
                return Optional.of(new GoalFacts.Accumulated(a.percent().doubleValue() / 100, a.lastDate()));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    /** Cotacao atual de um ticker do catalogo (meta por ativo que ainda nao esta na carteira). */
    private BigDecimal catalogPrice(String ticker) {
        return assetRepository.findByTicker(ticker).map(Asset::getCurrentPrice).orElse(null);
    }

    private static PositionInfo info(PortfolioAsset pa) {
        Asset a = pa.getAsset();
        boolean fixedIncome = a.getAssetType() != null && a.getAssetType().isFixedIncome();
        FixedIncomeNote n = pa.getNote();
        String indexer;
        String issuer;
        if (fixedIncome && n != null) {
            indexer = n.getIndexer() != null ? n.getIndexer().name()
                    : n.getRateType() != null && n.getRateType().name().equals("PREFIXADO") ? PositionInfo.PREFIXED : null;
            issuer = n.getIssuer() != null ? n.getIssuer().getDisplayName() : null;
        } else {
            indexer = PositionInfo.VARIABLE_INCOME;
            issuer = a.getIssuer() != null
                    ? (a.getIssuer().getTradeName() != null && !a.getIssuer().getTradeName().isBlank()
                        ? a.getIssuer().getTradeName() : a.getIssuer().getLegalName())
                    : null;
        }
        return new PositionInfo(pa.getId(), a.getId(), a.getTicker(),
                a.getAssetType() != null ? a.getAssetType().getName() : null,
                a.getAssetType() != null ? a.getAssetType().getDescription() : null,
                a.getAssetCategory() != null ? a.getAssetCategory().getDisplayName() : null,
                indexer, issuer, pa.isReservation(), fixedIncome, pa.isSettled());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static <T> Supplier<T> memo(Supplier<T> s) {
        return new Supplier<>() {
            private T value;
            private boolean done;

            @Override
            public synchronized T get() {
                if (!done) {
                    value = s.get();
                    done = true;
                }
                return value;
            }
        };
    }
}
