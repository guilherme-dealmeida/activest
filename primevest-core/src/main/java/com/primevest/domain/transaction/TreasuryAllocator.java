package com.primevest.domain.transaction;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A venda de Tesouro Direto: do ativo, dos titulos mais antigos primeiro.
 *
 * <p>No Tesouro o saldo e do ATIVO e a posicao e do TITULO. O usuario vende
 * "5 cotas de Tesouro IPCA+ 2050" sem escolher de qual compra elas saem — a
 * resposta e o FIFO: saem do titulo mais antigo, e o que faltar vem do
 * seguinte. Uma venda pode portanto consumir dois titulos, e cada parte vira
 * uma transacao presa ao titulo que ela liquida, todas com o mesmo
 * {@code sale_group_id}.</p>
 *
 * <p>A reparticao e recalculada por inteiro a cada escrita no ativo, e nao
 * ajustada aos pedacos. E o que o Exemplo 6 do escopo exige: uma compra lancada
 * com data retroativa passa a ser o titulo mais antigo e leva as cotas que
 * antes saiam de outro, inclusive reabrindo titulos que estavam liquidados.
 * Reparticao e dado derivado; aqui ela fica gravada porque a venda precisa
 * apontar para um titulo real — a divida esta registrada em PENDENCIAS.txt.</p>
 *
 * <p>A regra H — um titulo por ativo e data — e o que torna o FIFO
 * deterministico: sem ela, dois titulos comprados no mesmo dia nao teriam
 * ordem definida entre si.</p>
 */
@Component
@RequiredArgsConstructor
public class TreasuryAllocator {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MONEY_SCALE = 2;

    private final TransactionRepository transactionRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;

    /**
     * Uma venda como o usuario a lancou, antes de ser repartida entre titulos.
     *
     * @param groupId identificador do grupo; e ele que sobrevive a reparticao
     */
    public record SaleIntent(UUID groupId, LocalDate date, BigDecimal shares,
                             BigDecimal pricePerShare, BigDecimal fee, LocalDateTime createdAt) {}

    /** Uma parte da venda: as cotas que saem de um titulo. */
    private record Allocation(PortfolioAsset position, BigDecimal shares) {}

    /**
     * Reconstitui as vendas do usuario a partir das linhas ja gravadas.
     *
     * <p>Quantidade e taxas do grupo sao a soma das partes, que e exatamente o
     * que ele digitou: a reparticao preserva os totais.</p>
     */
    public List<SaleIntent> salesOf(List<Transaction> transactionsOfAsset) {
        Map<UUID, List<Transaction>> byGroup = new LinkedHashMap<>();
        for (Transaction tx : transactionsOfAsset) {
            if (tx.getType() == TransactionType.SELL) {
                byGroup.computeIfAbsent(groupKey(tx), k -> new ArrayList<>()).add(tx);
            }
        }

        List<SaleIntent> sales = new ArrayList<>();
        for (Map.Entry<UUID, List<Transaction>> entry : byGroup.entrySet()) {
            List<Transaction> rows = entry.getValue();
            Transaction first = rows.get(0);
            BigDecimal shares = BigDecimal.ZERO;
            BigDecimal fee = BigDecimal.ZERO;
            LocalDateTime createdAt = null;
            for (Transaction row : rows) {
                shares = shares.add(safe(row.getShares()));
                fee = fee.add(safe(row.getBrokerageFee()));
                if (createdAt == null
                        || (row.getCreatedAt() != null && row.getCreatedAt().isBefore(createdAt))) {
                    createdAt = row.getCreatedAt();
                }
            }
            sales.add(new SaleIntent(entry.getKey(), first.getTransactionDate(), shares,
                    safe(first.getPricePerShare()), fee, createdAt));
        }
        return sales;
    }

    /**
     * Refaz a reparticao das vendas do ativo e grava.
     *
     * @param portfolio  a carteira
     * @param asset      o ativo do Tesouro
     * @param current    todas as transacoes do ativo nessa carteira
     * @param sales      as vendas que devem existir ao fim (com as alteracoes
     *                   ja aplicadas: a nova incluida, a excluida de fora)
     * @return as linhas gravadas, por grupo
     */
    public Map<UUID, List<Transaction>> apply(Portfolio portfolio, Asset asset,
                                              List<Transaction> current, List<SaleIntent> sales) {
        List<PortfolioAsset> titles = titlesOf(portfolio, asset);
        Map<UUID, BigDecimal> remaining = boughtByTitle(titles, current);

        List<SaleIntent> chronological = new ArrayList<>(sales);
        chronological.sort(Comparator.comparing(SaleIntent::date)
                .thenComparing(SaleIntent::createdAt, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<UUID, List<Allocation>> allocations = new LinkedHashMap<>();
        for (SaleIntent sale : chronological) {
            allocations.put(sale.groupId(), allocate(asset, titles, remaining, sale));
        }

        Map<UUID, List<Transaction>> rowsByGroup = persist(current, chronological, allocations);
        settleTitles(titles, remaining);
        return rowsByGroup;
    }

    /**
     * FIFO: consome o titulo mais antigo comprado ate a data da venda.
     *
     * <p>Um titulo comprado depois da venda nao conta — e o Exemplo 5 do
     * escopo: vender em 14/09 nao alcanca cotas que so entraram em 15/09,
     * mesmo que o saldo de hoje comporte.</p>
     */
    private List<Allocation> allocate(Asset asset, List<PortfolioAsset> titles,
                                      Map<UUID, BigDecimal> remaining, SaleIntent sale) {
        List<Allocation> allocations = new ArrayList<>();
        BigDecimal pending = sale.shares();

        for (PortfolioAsset title : titles) {
            if (pending.signum() <= 0) {
                break;
            }
            LocalDate purchase = purchaseDate(title);
            if (purchase != null && purchase.isAfter(sale.date())) {
                continue;
            }
            BigDecimal available = remaining.getOrDefault(title.getId(), BigDecimal.ZERO);
            if (available.signum() <= 0) {
                continue;
            }
            BigDecimal take = available.min(pending);
            remaining.put(title.getId(), available.subtract(take));
            allocations.add(new Allocation(title, take));
            pending = pending.subtract(take);
        }

        if (pending.signum() > 0) {
            throw new BusinessException(
                "Saldo insuficiente de " + asset.getTicker() + " em " + sale.date().format(DATA)
                + ": faltam " + pending.stripTrailingZeros().toPlainString()
                + " cotas compradas até essa data.");
        }
        return allocations;
    }

    /**
     * Grava a reparticao: aproveita as linhas que ja existiam no grupo, cria o
     * que falta e apaga o que sobrou.
     *
     * <p>Reaproveitar em vez de apagar e recriar mantem o id e a data de
     * criacao das linhas — o extrato nao se remonta do zero a cada lancamento
     * retroativo em outro titulo.</p>
     */
    private Map<UUID, List<Transaction>> persist(List<Transaction> current, List<SaleIntent> sales,
                                                 Map<UUID, List<Allocation>> allocations) {
        Map<UUID, List<Transaction>> existing = new HashMap<>();
        for (Transaction tx : current) {
            if (tx.getType() == TransactionType.SELL) {
                existing.computeIfAbsent(groupKey(tx), k -> new ArrayList<>()).add(tx);
            }
        }

        Map<UUID, List<Transaction>> rowsByGroup = new LinkedHashMap<>();

        for (SaleIntent sale : sales) {
            List<Allocation> parts = allocations.getOrDefault(sale.groupId(), List.of());
            List<Transaction> previous = existing.remove(sale.groupId());
            List<Transaction> reusable = previous != null ? new ArrayList<>(previous) : new ArrayList<>();
            List<Transaction> saved = new ArrayList<>();
            List<BigDecimal> fees = prorate(sale.fee(), parts, sale.shares());

            for (int i = 0; i < parts.size(); i++) {
                Allocation part = parts.get(i);
                Transaction row = take(reusable, part.position().getId());
                if (row == null) {
                    row = Transaction.builder()
                            .portfolioAsset(part.position())
                            .type(TransactionType.SELL)
                            .build();
                }
                row.setPortfolioAsset(part.position());
                row.setNote(part.position().getNote());
                row.setType(TransactionType.SELL);
                row.setSaleGroupId(sale.groupId());
                row.setFullSettlement(false);
                row.setShares(part.shares());
                row.setPricePerShare(sale.pricePerShare());
                row.setTotalAmount(part.shares().multiply(sale.pricePerShare())
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                row.setBrokerageFee(fees.get(i));
                row.setTransactionDate(sale.date());
                saved.add(transactionRepository.save(row));
            }

            reusable.forEach(transactionRepository::delete);
            rowsByGroup.put(sale.groupId(), saved);
        }

        // Grupos que deixaram de existir (venda excluida).
        existing.values().forEach(rows -> rows.forEach(transactionRepository::delete));
        transactionRepository.flush();
        return rowsByGroup;
    }

    /**
     * Rateia as taxas da venda entre as partes, proporcionalmente as cotas, com
     * o centavo de sobra na ultima. Assim o custo de cada titulo continua certo
     * quando se olha o resultado titulo a titulo.
     */
    private List<BigDecimal> prorate(BigDecimal fee, List<Allocation> parts, BigDecimal totalShares) {
        List<BigDecimal> result = new ArrayList<>();
        BigDecimal total = safe(fee).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal accumulated = BigDecimal.ZERO;

        for (int i = 0; i < parts.size(); i++) {
            if (i == parts.size() - 1) {
                result.add(total.subtract(accumulated));
                break;
            }
            BigDecimal share = totalShares.signum() > 0
                    ? total.multiply(parts.get(i).shares())
                        .divide(totalShares, MONEY_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            accumulated = accumulated.add(share);
            result.add(share);
        }
        return result;
    }

    /** Marca como liquidado o titulo que ficou sem cotas, e so ele (regras 3.2 e J). */
    private void settleTitles(List<PortfolioAsset> titles, Map<UUID, BigDecimal> remaining) {
        for (PortfolioAsset title : titles) {
            boolean settled = remaining.getOrDefault(title.getId(), BigDecimal.ZERO).signum() <= 0;
            if (title.isSettled() != settled) {
                title.setSettled(settled);
                portfolioAssetRepository.save(title);
            }
        }
    }

    /** Titulos do ativo na carteira, do mais antigo para o mais novo. */
    private List<PortfolioAsset> titlesOf(Portfolio portfolio, Asset asset) {
        List<PortfolioAsset> titles = new ArrayList<>(
                portfolioAssetRepository.findAllByPortfolioIdAndAssetId(portfolio.getId(), asset.getId()));
        titles.removeIf(pa -> pa.getNote() == null);
        titles.sort(Comparator
                .comparing((PortfolioAsset pa) -> purchaseDate(pa),
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(pa -> pa.getNote().getCreatedAt(),
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return titles;
    }

    /** Cotas compradas de cada titulo. */
    private Map<UUID, BigDecimal> boughtByTitle(List<PortfolioAsset> titles, List<Transaction> current) {
        Map<UUID, BigDecimal> bought = new HashMap<>();
        titles.forEach(pa -> bought.put(pa.getId(), BigDecimal.ZERO));
        for (Transaction tx : current) {
            if (tx.getType() != TransactionType.BUY) {
                continue;
            }
            UUID positionId = tx.getPortfolioAsset().getId();
            bought.merge(positionId, safe(tx.getShares()), BigDecimal::add);
        }
        return bought;
    }

    private Transaction take(List<Transaction> rows, UUID positionId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getPortfolioAsset().getId().equals(positionId)) {
                return rows.remove(i);
            }
        }
        return null;
    }

    private LocalDate purchaseDate(PortfolioAsset title) {
        return title.getNote() != null ? title.getNote().getPurchaseDate() : null;
    }

    /** A chave do grupo. Linha anterior a V29 nao tem grupo: ela e o proprio. */
    private UUID groupKey(Transaction tx) {
        return tx.getSaleGroupId() != null ? tx.getSaleGroupId() : tx.getId();
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
