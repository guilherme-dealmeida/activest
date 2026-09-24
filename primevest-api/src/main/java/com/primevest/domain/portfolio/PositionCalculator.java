package com.primevest.domain.portfolio;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.corporateaction.AssetStockEvent;
import com.primevest.domain.corporateaction.AssetStockEventRepository;
import com.primevest.domain.corporateaction.CorporateActionApplier;
import com.primevest.domain.corporateaction.StockEventStep;
import com.primevest.domain.dividend.Dividend;
import com.primevest.domain.dividend.DividendRepository;
import com.primevest.domain.dividend.DividendType;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionRepository;
import com.primevest.domain.transaction.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Regra unica da posicao: cotas, preco medio e custo saem das transacoes.
 *
 * <p>Antes esses numeros eram gravados em portfolio_assets e mantidos por um
 * recalculo disparado a cada mudanca de transacao. Agora sao apurados na
 * leitura, e as transacoes voltam a ser a unica fonte (V28).</p>
 *
 * <p>O calculo percorre as transacoes em ordem cronologica, porque a venda
 * depende do que veio antes:</p>
 * <ul>
 *   <li><b>Compra</b>: soma cotas e custo (cotas x preco).</li>
 *   <li><b>Venda</b>: tira cotas e tira custo pelo preco medio do momento
 *   (cotas vendidas x custo / cotas). O preco medio nao muda na venda.</li>
 *   <li><b>Preco medio</b> = custo / cotas.</li>
 * </ul>
 *
 * <p>Por isso nao e um SUM nem um AVG: compra 100 a R$ 10, compra 100 a
 * R$ 20 e venda 100 a R$ 30 da preco medio R$ 15 e custo R$ 1.500 — a media
 * simples dos precos daria R$ 20 e a soma de quantidade x preco, R$ 6.000.</p>
 *
 * <p><b>Eventos do papel</b> (PLANO_BRAPI.md, F5 e F6) entram como passos a
 * mais na mesma linha do tempo, sem reescrever nenhuma transacao:</p>
 * <ul>
 *   <li><b>desdobramento, grupamento e bonificacao</b> — logo depois das
 *   transacoes da data-com: quem comprou na data-com tem direito; quem comprou
 *   na data ex ja compra na quantidade nova (e o que a nota mostra). A regra de
 *   cada um mora em {@link CorporateActionApplier};</li>
 *   <li><b>amortizacao e restituicao de capital</b> — na data de pagamento, o
 *   valor recebido sai do custo (nunca abaixo de zero). Cotas nao mudam.</li>
 * </ul>
 * <p>Evento com data-com no futuro ainda nao vale.</p>
 *
 * <p>Venda sem cotas em carteira nao e mais possivel: a regra B a recusa antes
 * de gravar, em qualquer data. O calculo ainda tolera o caso (zera a posicao)
 * porque ele tambem roda sobre dados anteriores a essa regra.</p>
 *
 * <p>Tipos lancados por valor total ({@link com.primevest.domain.asset.AssetType#isValuedByTotal()})
 * nao tem quantidade: cotas e preco medio ficam nulos, o custo e o valor
 * APLICADO (so as compras) e o resgatado fica em {@link Position#totalSold()}.
 * As duas pontas ficam separadas porque as regras E e F comparam uma com a
 * outra. Eventos do papel nao se aplicam a eles.</p>
 *
 * <p>Os proventos usam a mesma contagem de cotas ({@link #sharesOn}), para
 * que posicao e provento nunca divirjam.</p>
 */
@Component
public class PositionCalculator {

    private static final int AVERAGE_PRICE_SCALE = 4;
    private static final int INTERMEDIATE_SCALE = 10;
    private static final int MONEY_SCALE = 2;
    private static final ZoneId B3_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Set<DividendType> CAPITAL_RETURN_TYPES =
            Set.of(DividendType.AMORTIZACAO, DividendType.RESTITUICAO_CAPITAL);

    private final TransactionRepository transactionRepository;
    private final AssetStockEventRepository stockEventRepository;
    private final DividendRepository dividendRepository;

    public PositionCalculator(TransactionRepository transactionRepository,
                              AssetStockEventRepository stockEventRepository,
                              DividendRepository dividendRepository) {
        this.transactionRepository = transactionRepository;
        this.stockEventRepository = stockEventRepository;
        this.dividendRepository = dividendRepository;
    }

    /** Ordem cronologica: data, depois criacao. */
    public static List<Transaction> chronological(Collection<Transaction> transactions) {
        return transactions.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate)
                        .thenComparing(Transaction::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Transacoes de uma posicao em ordem cronologica. */
    public List<Transaction> chronologicalTransactions(PortfolioAsset pa) {
        return chronological(transactionRepository.findByPortfolioAssetIdOrderByTransactionDateDesc(pa.getId()));
    }

    /** Eventos societarios do ativo, como passos do calculo. */
    public List<StockEventStep> stockEventsOf(Asset asset) {
        if (asset == null || asset.getId() == null || stockEventRepository == null) {
            return List.of();
        }
        return stockEventRepository.findByAssetIdOrderByDataComAsc(asset.getId()).stream()
                .map(AssetStockEvent::toStep)
                .toList();
    }

    /** Eventos societarios do ativo e devolucoes de capital recebidas pela posicao. */
    public PositionEvents eventsOf(PortfolioAsset pa) {
        if (dividendRepository == null) {
            return PositionEvents.ofStockEvents(stockEventsOf(pa.getAsset()));
        }
        List<CapitalReturn> returns = dividendRepository
                .findByPortfolioAssetIdAndTypeIn(pa.getId(), CAPITAL_RETURN_TYPES).stream()
                .map(PositionCalculator::toCapitalReturn)
                .toList();
        return new PositionEvents(stockEventsOf(pa.getAsset()), returns);
    }

    /** Posicao de uma unica linha da carteira, com os eventos do papel. */
    public Position positionOf(PortfolioAsset pa) {
        return calculate(isValuedByTotal(pa.getAsset()), chronologicalTransactions(pa), eventsOf(pa));
    }

    /**
     * Posicao de cada linha de uma carteira, com uma consulta so para todas as
     * transacoes dela — e nao uma por posicao. Os eventos do papel e as
     * devolucoes de capital tambem vem numa consulta cada.
     *
     * <p>Posicao sem transacao aparece com {@link Position#EMPTY}.</p>
     */
    public Map<UUID, Position> positionsOf(UUID portfolioId, Collection<PortfolioAsset> positions) {
        Map<UUID, List<Transaction>> txByPosition = transactionRepository
                .findByPortfolioAssetPortfolioIdOrderByTransactionDateDesc(portfolioId)
                .stream()
                .collect(Collectors.groupingBy(tx -> tx.getPortfolioAsset().getId()));

        Map<Long, List<StockEventStep>> stockByAsset = new HashMap<>();
        if (stockEventRepository != null) {
            Set<Long> assetIds = positions.stream()
                    .map(pa -> pa.getAsset() != null ? pa.getAsset().getId() : null)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!assetIds.isEmpty()) {
                for (AssetStockEvent e : stockEventRepository.findByAssetIds(assetIds)) {
                    stockByAsset.computeIfAbsent(e.getAsset().getId(), k -> new ArrayList<>()).add(e.toStep());
                }
            }
        }
        Map<UUID, List<CapitalReturn>> returnsByPosition = new HashMap<>();
        if (dividendRepository != null) {
            for (Dividend d : dividendRepository.findByPortfolioIdAndTypeIn(portfolioId, CAPITAL_RETURN_TYPES)) {
                returnsByPosition.computeIfAbsent(d.getPortfolioAsset().getId(), k -> new ArrayList<>())
                        .add(toCapitalReturn(d));
            }
        }

        Map<UUID, Position> result = new HashMap<>();
        for (PortfolioAsset pa : positions) {
            List<Transaction> txs = txByPosition.get(pa.getId());
            if (txs == null) {
                result.put(pa.getId(), Position.EMPTY);
                continue;
            }
            Long assetId = pa.getAsset() != null ? pa.getAsset().getId() : null;
            PositionEvents events = new PositionEvents(
                    stockByAsset.getOrDefault(assetId, List.of()),
                    returnsByPosition.getOrDefault(pa.getId(), List.of()));
            result.put(pa.getId(), calculate(isValuedByTotal(pa.getAsset()), chronological(txs), events));
        }
        return result;
    }

    /**
     * Apura a posicao so pelas transacoes, sem eventos do papel.
     *
     * <p>E o que as regras de lancamento usam para conferir o que o usuario
     * digitou (regra I: posicao em aberto tem compra): a conferencia e sobre as
     * transacoes, e uma amortizacao que zere o custo nao pode fazer uma edicao
     * valida ser recusada.</p>
     *
     * @param valuedByTotal o tipo do ativo e lancado por valor total
     * @param chronological transacoes em ordem cronologica
     */
    public Position calculate(boolean valuedByTotal, List<Transaction> chronological) {
        return calculate(valuedByTotal, chronological, PositionEvents.NONE);
    }

    /** Apura a posicao com os eventos do papel, ate hoje. */
    public Position calculate(boolean valuedByTotal, List<Transaction> chronological, PositionEvents events) {
        return calculate(valuedByTotal, chronological, events, LocalDate.now(B3_ZONE));
    }

    /**
     * Apura a posicao com os eventos do papel ate uma data.
     *
     * @param asOf eventos com data-com (societarios) ou pagamento (capital)
     *             depois dela ainda nao valem
     */
    public Position calculate(boolean valuedByTotal, List<Transaction> chronological, PositionEvents events,
                              LocalDate asOf) {
        if (valuedByTotal) {
            return calculateByTotal(chronological);
        }

        Walk walk = new Walk(events);
        for (Transaction tx : chronological) {
            walk.advanceBefore(tx.getTransactionDate());
            walk.holding = walk.holding.apply(tx);
        }
        walk.advanceUntil(asOf);
        Holding holding = walk.holding;

        BigDecimal averagePrice = holding.shares().compareTo(BigDecimal.ZERO) > 0
                ? holding.cost().divide(holding.shares(), AVERAGE_PRICE_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new Position(holding.shares(), averagePrice,
                holding.cost().setScale(MONEY_SCALE, RoundingMode.HALF_UP), BigDecimal.ZERO);
    }

    /**
     * Cotas ao fim de uma data (a data-com, para proventos), so pelas
     * transacoes. Compra feita na propria data conta; a partir do dia seguinte
     * nao. Transacoes sem quantidade (Renda Fixa) nao somam nada.
     *
     * @param chronological transacoes em ordem cronologica
     */
    public BigDecimal sharesOn(List<Transaction> chronological, LocalDate date) {
        return sharesOn(chronological, List.of(), date);
    }

    /**
     * Cotas ao fim de uma data, com os eventos societarios do papel. Evento com
     * data-com ANTERIOR a data ja vale; com data-com na propria data, ainda nao
     * (o provento dessa data-com e sobre a quantidade de antes do evento).
     */
    public BigDecimal sharesOn(List<Transaction> chronological, List<StockEventStep> stockEvents, LocalDate date) {
        Walk walk = new Walk(PositionEvents.ofStockEvents(stockEvents));
        for (Transaction tx : chronological) {
            if (tx.getTransactionDate().isAfter(date)) break;
            walk.advanceBefore(tx.getTransactionDate());
            walk.holding = walk.holding.apply(tx);
        }
        walk.advanceBefore(date);
        return walk.holding.shares();
    }

    /**
     * Percorre os eventos em paralelo as transacoes. Cada evento e aplicado uma
     * vez, na ordem de data.
     */
    private static final class Walk {
        private final List<StockEventStep> stock;
        private final List<CapitalReturn> capital;
        private int nextStock;
        private int nextCapital;
        Holding holding = Holding.NONE;

        Walk(PositionEvents events) {
            this.stock = events.stockEvents();
            this.capital = events.capitalReturns();
        }

        /**
         * Tudo o que vale antes de uma transacao na data informada: eventos
         * societarios com data-com anterior (a transacao ja e da data ex em
         * diante) e devolucoes de capital pagas ate a data.
         */
        void advanceBefore(LocalDate txDate) {
            apply(txDate, false);
        }

        /** Fim do calculo: tudo o que ja vale ate {@code asOf}. */
        void advanceUntil(LocalDate asOf) {
            apply(asOf, true);
        }

        private void apply(LocalDate limit, boolean end) {
            while (true) {
                StockEventStep s = nextStock < stock.size() ? stock.get(nextStock) : null;
                CapitalReturn c = nextCapital < capital.size() ? capital.get(nextCapital) : null;
                boolean stockDue = s != null && s.dataCom().isBefore(limit);
                boolean capitalDue = c != null && !c.date().isAfter(limit);
                if (!stockDue && !capitalDue) {
                    return;
                }
                // O que vem primeiro na data. O evento societario vale a partir
                // do dia seguinte a data-com; o pagamento, no proprio dia.
                boolean stockFirst = stockDue && (!capitalDue || !s.dataCom().plusDays(1).isAfter(c.date()));
                if (stockFirst) {
                    holding = holding.after(s);
                    nextStock++;
                } else {
                    holding = holding.minusCapital(c.amount());
                    nextCapital++;
                }
            }
        }
    }

    /** Cotas e custo acumulados ate um ponto da historia da posicao. */
    private record Holding(BigDecimal shares, BigDecimal cost) {

        static final Holding NONE = new Holding(BigDecimal.ZERO, BigDecimal.ZERO);

        /** Um passo do calculo por quantidade. Transacao sem quantidade nao altera nada. */
        Holding apply(Transaction tx) {
            BigDecimal txShares = tx.getShares();
            if (txShares == null) {
                return this;
            }
            BigDecimal txPrice = tx.getPricePerShare() != null ? tx.getPricePerShare() : BigDecimal.ZERO;

            if (tx.getType() == TransactionType.BUY) {
                return new Holding(shares.add(txShares), cost.add(txShares.multiply(txPrice)));
            }

            if (tx.getType() == TransactionType.SELL && shares.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remaining = shares.subtract(txShares);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    return NONE;
                }
                BigDecimal average = cost.divide(shares, INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
                return new Holding(remaining, cost.subtract(txShares.multiply(average)));
            }

            return this;
        }

        /** Desdobramento, grupamento ou bonificacao. */
        Holding after(StockEventStep event) {
            CorporateActionApplier.Holding next =
                    CorporateActionApplier.apply(new CorporateActionApplier.Holding(shares, cost), event);
            return new Holding(next.shares(), next.cost());
        }

        /** Amortizacao ou restituicao: sai do custo, sem passar de zero. */
        Holding minusCapital(BigDecimal amount) {
            if (shares.signum() <= 0 || amount == null || amount.signum() <= 0) {
                return this;
            }
            BigDecimal left = cost.subtract(amount);
            return new Holding(shares, left.signum() < 0 ? BigDecimal.ZERO : left);
        }
    }

    /**
     * Tipos lancados por valor: o aplicado e o resgatado ficam separados.
     *
     * <p>Abater a venda do aplicado, como se fazia antes, misturava principal e
     * ganho num numero so e tornava as regras E e F indecidiveis.</p>
     */
    private Position calculateByTotal(List<Transaction> chronological) {
        BigDecimal applied = BigDecimal.ZERO;
        BigDecimal sold = BigDecimal.ZERO;
        for (Transaction tx : chronological) {
            BigDecimal amount = tx.getTotalAmount() != null ? tx.getTotalAmount() : BigDecimal.ZERO;
            if (tx.getType() == TransactionType.BUY) {
                applied = applied.add(amount);
            } else if (tx.getType() == TransactionType.SELL) {
                sold = sold.add(amount);
            }
        }
        return new Position(null, null,
                applied.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                sold.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
    }

    private static CapitalReturn toCapitalReturn(Dividend d) {
        LocalDate date = d.getPaymentDate() != null ? d.getPaymentDate() : d.getExDate();
        return new CapitalReturn(date, d.getTotalAmount() != null ? d.getTotalAmount() : BigDecimal.ZERO);
    }

    private boolean isValuedByTotal(Asset asset) {
        return asset != null && asset.getAssetType() != null && asset.getAssetType().isValuedByTotal();
    }
}
