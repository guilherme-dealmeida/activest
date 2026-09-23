package com.primevest.domain.portfolio;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionRepository;
import com.primevest.domain.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p>Venda sem cotas em carteira nao e mais possivel: a regra B a recusa antes
 * de gravar, em qualquer data. O calculo ainda tolera o caso (zera a posicao)
 * porque ele tambem roda sobre dados anteriores a essa regra.</p>
 *
 * <p>Tipos lancados por valor total ({@link com.primevest.domain.asset.AssetType#isValuedByTotal()})
 * nao tem quantidade: cotas e preco medio ficam nulos, o custo e o valor
 * APLICADO (so as compras) e o resgatado fica em {@link Position#totalSold()}.
 * As duas pontas ficam separadas porque as regras E e F comparam uma com a
 * outra.</p>
 *
 * <p>Os proventos usam a mesma contagem de cotas ({@link #sharesOn}), para
 * que posicao e provento nunca divirjam.</p>
 */
@Component
@RequiredArgsConstructor
public class PositionCalculator {

    private static final int AVERAGE_PRICE_SCALE = 4;
    private static final int INTERMEDIATE_SCALE = 10;
    private static final int MONEY_SCALE = 2;

    private final TransactionRepository transactionRepository;

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

    /** Posicao de uma unica linha da carteira. */
    public Position positionOf(PortfolioAsset pa) {
        return calculate(isValuedByTotal(pa.getAsset()), chronologicalTransactions(pa));
    }

    /**
     * Posicao de cada linha de uma carteira, com uma consulta so para todas as
     * transacoes dela — e nao uma por posicao.
     *
     * <p>Posicao sem transacao aparece com {@link Position#EMPTY}.</p>
     */
    public Map<UUID, Position> positionsOf(UUID portfolioId, Collection<PortfolioAsset> positions) {
        Map<UUID, List<Transaction>> txByPosition = transactionRepository
                .findByPortfolioAssetPortfolioIdOrderByTransactionDateDesc(portfolioId)
                .stream()
                .collect(Collectors.groupingBy(tx -> tx.getPortfolioAsset().getId()));

        Map<UUID, Position> result = new HashMap<>();
        for (PortfolioAsset pa : positions) {
            List<Transaction> txs = txByPosition.get(pa.getId());
            result.put(pa.getId(), txs == null
                    ? Position.EMPTY
                    : calculate(isValuedByTotal(pa.getAsset()), chronological(txs)));
        }
        return result;
    }

    /**
     * Apura a posicao.
     *
     * @param valuedByTotal o tipo do ativo e lancado por valor total
     * @param chronological transacoes em ordem cronologica
     */
    public Position calculate(boolean valuedByTotal, List<Transaction> chronological) {
        if (valuedByTotal) {
            return calculateByTotal(chronological);
        }

        Holding holding = Holding.NONE;
        for (Transaction tx : chronological) {
            holding = holding.apply(tx);
        }

        BigDecimal averagePrice = holding.shares().compareTo(BigDecimal.ZERO) > 0
                ? holding.cost().divide(holding.shares(), AVERAGE_PRICE_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new Position(holding.shares(), averagePrice,
                holding.cost().setScale(MONEY_SCALE, RoundingMode.HALF_UP), BigDecimal.ZERO);
    }

    /**
     * Cotas ao fim de uma data (a data-com, para proventos). Compra feita na
     * propria data conta; a partir do dia seguinte nao. Transacoes sem
     * quantidade (Renda Fixa) nao somam nada.
     *
     * @param chronological transacoes em ordem cronologica
     */
    public BigDecimal sharesOn(List<Transaction> chronological, LocalDate date) {
        Holding holding = Holding.NONE;
        for (Transaction tx : chronological) {
            if (tx.getTransactionDate().isAfter(date)) break;
            holding = holding.apply(tx);
        }
        return holding.shares();
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

    private boolean isValuedByTotal(Asset asset) {
        return asset != null && asset.getAssetType() != null && asset.getAssetType().isValuedByTotal();
    }
}
