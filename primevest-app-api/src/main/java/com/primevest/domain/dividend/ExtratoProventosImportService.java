package com.primevest.domain.dividend;


import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.portfolio.PositionCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Importa o extrato de proventos da Area do Investidor da B3 (V24): preenche
 * as lacunas do catalogo que nenhuma fonte automatica cobre — FII alem de 12
 * meses quando o FNET falha, datas de pagamento que o historico longo da B3
 * nao traz, a atualizacao pela Selic que a B3 nao publica.
 *
 * <p>Cada linha do extrato (um pagamento recebido pelo usuario) cai em um de
 * tres casos, nesta ordem:</p>
 * <ol>
 *   <li><b>ja existe</b> — um provento do usuario com o mesmo ativo, tipo,
 *   pagamento, cotas e liquido. Nada muda (e o que torna a importacao
 *   repetivel);</li>
 *   <li><b>completa a data</b> — um provento do usuario sem data de pagamento,
 *   com o mesmo ativo, tipo, cotas e liquido. A data vai para o evento do
 *   catalogo, marcada como {@link PaymentDateSource#EXTRATO_B3}. Parcelas
 *   iguais (mesma data-com e valor) sao preenchidas pela menor parcela
 *   primeiro, na ordem dos pagamentos;</li>
 *   <li><b>evento novo</b> — nada no PrimeVest. Exige data-com (deduzida pelo
 *   script de conversao) e bruto; cria o evento com fonte
 *   {@link CashEventSource#EXTRATO_B3} depois de conferir que as cotas na
 *   data-com e o liquido calculado batem com o extrato.</li>
 * </ol>
 * <p>Pagamentos deduzidos (sem linha no extrato) completam a data de um
 * provento sem data, marcada como {@link PaymentDateSource#DEDUZIDA}.</p>
 *
 * <p>Tudo numa transacao; {@code dryRun} desfaz no fim e so relata. Grava pelo
 * mesmo {@link CashEventWriter} da rotina (mesma unicidade) e recalcula as
 * posicoes dos ativos tocados.</p>
 */
@Slf4j
@Service
public class ExtratoProventosImportService {

    static final int VALUE_SCALE = 11;
    private static final int MAX_SEQ = 20;

    /** Uma linha do extrato: um pagamento recebido. */
    public record Line(String ref, String ticker, DividendType type, LocalDate paymentDate, BigDecimal shares,
                       BigDecimal netAmount, BigDecimal grossAmount, LocalDate dataCom) {
    }

    /** Pagamento deduzido (sem linha no extrato) para um provento sem data. */
    public record DeducedPayment(String ref, String ticker, DividendType type, LocalDate dataCom, BigDecimal shares,
                                 BigDecimal netAmount, LocalDate paymentDate) {
    }

    public record Request(boolean dryRun, List<Line> lines, List<DeducedPayment> deducedPayments) {
    }

    public enum Status { JA_EXISTE, DATA_COMPLETADA, EVENTO_CRIADO, DATA_DEDUZIDA, RECUSADO }

    public record ItemResult(String ref, String ticker, DividendType type, LocalDate dataCom, LocalDate paymentDate,
                             BigDecimal netAmount, Status status, String message) {
    }

    public record Result(boolean dryRun, Map<Status, Long> totals, int positionsRecalculated, List<ItemResult> items) {
    }

    private final CashEventWriter cashEventWriter;
    private final DividendRepository dividendRepository;
    private final AssetCashEventRepository cashEventRepository;
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PositionCalculator positionCalculator;
    private final DividendRecalculator dividendRecalculator;
    private final WithholdingTaxRuleRepository ruleRepository;
    private final TransactionTemplate tx;

    public ExtratoProventosImportService(CashEventWriter cashEventWriter,
                                         DividendRepository dividendRepository,
                                         AssetCashEventRepository cashEventRepository,
                                         AssetRepository assetRepository,
                                         PortfolioRepository portfolioRepository,
                                         PortfolioAssetRepository portfolioAssetRepository,
                                         PositionCalculator positionCalculator,
                                         DividendRecalculator dividendRecalculator,
                                         WithholdingTaxRuleRepository ruleRepository,
                                         PlatformTransactionManager transactionManager) {
        this.cashEventWriter = cashEventWriter;
        this.dividendRepository = dividendRepository;
        this.cashEventRepository = cashEventRepository;
        this.assetRepository = assetRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.positionCalculator = positionCalculator;
        this.dividendRecalculator = dividendRecalculator;
        this.ruleRepository = ruleRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Estado de uma rodada: o que ja foi casado ou preenchido nela. */
    private static final class Run {
        final List<Dividend> mine;
        final Portfolio portfolio;
        final List<WithholdingTaxRule> rules;
        final Set<Long> usedDividends = new HashSet<>();
        final Set<Long> filledEvents = new HashSet<>();
        final Set<Long> touchedAssets = new HashSet<>();

        Run(Portfolio portfolio, List<Dividend> mine, List<WithholdingTaxRule> rules) {
            this.portfolio = portfolio;
            this.mine = mine;
            this.rules = rules;
        }
    }

    public Result importFor(UUID userId, Request request) {
        List<Line> lines = request.lines() != null ? request.lines() : List.of();
        List<DeducedPayment> deduced = request.deducedPayments() != null ? request.deducedPayments() : List.of();
        return tx.execute(status -> {
            Portfolio portfolio = portfolioRepository.findFirstByUserId(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario sem carteira."));
            Run run = new Run(portfolio, new ArrayList<>(dividendRepository.findByPortfolioIdWithAsset(portfolio.getId())),
                    ruleRepository.findAll());
            List<ItemResult> items = new ArrayList<>();

            // Ordem dos pagamentos: a parcela paga antes preenche a parcela 1.
            lines.stream()
                    .sorted(Comparator.comparing(Line::paymentDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(l -> items.add(line(run, l)));
            deduced.stream()
                    .sorted(Comparator.comparing(DeducedPayment::paymentDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(d -> items.add(deduced(run, d)));

            int recalculated = 0;
            if (!run.touchedAssets.isEmpty()) {
                for (PortfolioAsset pa : portfolioAssetRepository.findAllByAssetIdIn(run.touchedAssets)) {
                    dividendRecalculator.recalculate(pa, dividendRecalculator.chronologicalTransactions(pa));
                    recalculated++;
                }
            }
            if (request.dryRun()) {
                status.setRollbackOnly();
            }
            Map<Status, Long> totals = new EnumMap<>(Status.class);
            for (Status s : Status.values()) {
                totals.put(s, items.stream().filter(i -> i.status() == s).count());
            }
            log.info("Extrato de proventos{}: {} linhas, {} deduzidos — {} | {} posicoes recalculadas",
                    request.dryRun() ? " (simulacao)" : "", lines.size(), deduced.size(), totals, recalculated);
            return new Result(request.dryRun(), totals, recalculated, items);
        });
    }

    // ------------------------------------------------------------------
    // Linha do extrato
    // ------------------------------------------------------------------

    private ItemResult line(Run run, Line l) {
        if (l.ticker() == null || l.type() == null || l.paymentDate() == null || l.shares() == null
                || l.netAmount() == null) {
            return result(l, null, Status.RECUSADO, "Linha incompleta: ticker, tipo, pagamento, cotas e liquido sao obrigatorios.");
        }
        Asset asset = assetRepository.findByTicker(l.ticker()).orElse(null);
        if (asset == null) {
            return result(l, null, Status.RECUSADO, "Ativo fora do catalogo.");
        }

        // 1. Ja existe: mesmo pagamento, cotas e liquido.
        Dividend existing = run.mine.stream()
                .filter(d -> !run.usedDividends.contains(d.getId()))
                .filter(d -> sameAsset(d, asset) && d.getType() == l.type())
                .filter(d -> l.paymentDate().equals(paymentDateOf(d)))
                .filter(d -> sameShares(d, l.shares()) && sameMoney(d.netOrGross(), l.netAmount()))
                .findFirst().orElse(null);
        if (existing != null) {
            run.usedDividends.add(existing.getId());
            return result(l, existing.getExDate(), Status.JA_EXISTE, null);
        }

        // 2. Completa a data de um provento sem pagamento.
        List<Dividend> candidates = withoutPayment(run, asset, l.type(), l.shares(), l.netAmount(), l.dataCom());
        if (!candidates.isEmpty()) {
            if (candidates.stream().map(Dividend::getExDate).distinct().count() > 1) {
                return result(l, null, Status.RECUSADO, "Mais de um provento sem data se encaixa (datas-com "
                        + candidates.stream().map(d -> d.getExDate().toString()).distinct().sorted().toList() + ").");
            }
            Dividend target = lowestSeq(candidates);
            fillPayment(run, target, l.paymentDate(), PaymentDateSource.EXTRATO_B3);
            return result(l, target.getExDate(), Status.DATA_COMPLETADA, null);
        }

        // 3. Evento novo.
        return create(run, asset, l);
    }

    private ItemResult create(Run run, Asset asset, Line l) {
        if (l.dataCom() == null || l.grossAmount() == null) {
            return result(l, null, Status.RECUSADO,
                    "Provento nao encontrado no PrimeVest, e a linha nao traz data-com e bruto para lanca-lo.");
        }
        PortfolioAsset pa = portfolioAssetRepository
                .findByPortfolioIdAndAssetIdAndNoteIsNull(run.portfolio.getId(), asset.getId()).orElse(null);
        if (pa == null) {
            return result(l, l.dataCom(), Status.RECUSADO, "Sem posicao no ativo.");
        }
        BigDecimal shares = positionCalculator.sharesOn(positionCalculator.chronologicalTransactions(pa),
                positionCalculator.stockEventsOf(asset), l.dataCom());
        if (shares.compareTo(l.shares()) != 0) {
            return result(l, l.dataCom(), Status.RECUSADO, "Na data-com " + l.dataCom() + " o PrimeVest tem "
                    + shares.stripTrailingZeros().toPlainString() + " cotas; o extrato, "
                    + l.shares().stripTrailingZeros().toPlainString() + ".");
        }
        BigDecimal value = l.grossAmount().divide(l.shares(), VALUE_SCALE, RoundingMode.HALF_UP);
        if (WithholdingTaxCalculator.gross(value, l.shares()).compareTo(l.grossAmount()) != 0) {
            return result(l, l.dataCom(), Status.RECUSADO, "O bruto " + l.grossAmount()
                    + " nao sai de um valor por cota com " + VALUE_SCALE + " casas.");
        }
        Long assetTypeId = asset.getAssetType() != null ? asset.getAssetType().getId() : null;
        WithholdingTaxCalculator.Result tax = WithholdingTaxCalculator.calculate(run.rules,
                new WithholdingTaxCalculator.Input(l.type(), assetTypeId, null, l.dataCom(), l.paymentDate(),
                        value, l.shares()));
        BigDecimal net = tax.net() != null ? tax.net() : tax.gross();
        if (net.compareTo(l.netAmount()) != 0) {
            return result(l, l.dataCom(), Status.RECUSADO, "Liquido calculado " + net + " difere do extrato "
                    + l.netAmount() + " (bruto " + l.grossAmount() + ").");
        }

        // Parcela: a primeira livre com a mesma data-com e valor.
        short seq = 0;
        for (short s = 1; s <= MAX_SEQ; s++) {
            AssetCashEvent same = cashEventRepository.findSame(asset.getId(), l.type(), l.dataCom(), value, s).orElse(null);
            if (same == null) {
                seq = s;
                break;
            }
            if (l.paymentDate().equals(same.getPaymentDate())) {
                run.touchedAssets.add(asset.getId());
                return result(l, l.dataCom(), Status.JA_EXISTE, "Evento ja no catalogo (parcela " + s + ").");
            }
        }
        if (seq == 0) {
            return result(l, l.dataCom(), Status.RECUSADO, "Parcelas demais com a mesma data-com e valor.");
        }
        cashEventWriter.save(asset, new CashEventWriter.CashEventData(l.type(), l.type().name(),
                l.dataCom(), l.paymentDate(), value, null, null, asset.getIsin(), null, CashEventSource.EXTRATO_B3,
                truncate(l.ref(), 100), seq));
        run.touchedAssets.add(asset.getId());
        return result(l, l.dataCom(), Status.EVENTO_CRIADO, seq > 1 ? "Parcela " + seq + "." : null);
    }

    // ------------------------------------------------------------------
    // Pagamento deduzido
    // ------------------------------------------------------------------

    private ItemResult deduced(Run run, DeducedPayment p) {
        if (p.ticker() == null || p.type() == null || p.dataCom() == null || p.paymentDate() == null
                || p.shares() == null || p.netAmount() == null) {
            return result(p, Status.RECUSADO, "Pagamento deduzido incompleto.");
        }
        Asset asset = assetRepository.findByTicker(p.ticker()).orElse(null);
        if (asset == null) {
            return result(p, Status.RECUSADO, "Ativo fora do catalogo.");
        }
        List<Dividend> candidates = withoutPayment(run, asset, p.type(), p.shares(), p.netAmount(), p.dataCom());
        if (candidates.isEmpty()) {
            boolean done = run.mine.stream()
                    .anyMatch(d -> sameAsset(d, asset) && d.getType() == p.type() && d.getExDate().equals(p.dataCom())
                            && p.paymentDate().equals(paymentDateOf(d))
                            && sameShares(d, p.shares()) && sameMoney(d.netOrGross(), p.netAmount()));
            return done ? result(p, Status.JA_EXISTE, null)
                    : result(p, Status.RECUSADO, "Nenhum provento sem data de pagamento se encaixa.");
        }
        fillPayment(run, lowestSeq(candidates), p.paymentDate(), PaymentDateSource.DEDUZIDA);
        return result(p, Status.DATA_DEDUZIDA, null);
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Proventos do usuario sem data de pagamento (nem preenchida nesta rodada) que se encaixam. */
    private List<Dividend> withoutPayment(Run run, Asset asset, DividendType type, BigDecimal shares, BigDecimal net,
                                          LocalDate dataCom) {
        return run.mine.stream()
                .filter(d -> !run.usedDividends.contains(d.getId()))
                .filter(d -> d.getCashEvent() != null && !run.filledEvents.contains(d.getCashEvent().getId()))
                .filter(d -> paymentDateOf(d) == null)
                .filter(d -> sameAsset(d, asset) && d.getType() == type)
                .filter(d -> dataCom == null || dataCom.equals(d.getExDate()))
                .filter(d -> sameShares(d, shares) && sameMoney(d.netOrGross(), net))
                .toList();
    }

    private static Dividend lowestSeq(List<Dividend> candidates) {
        return candidates.stream()
                .min(Comparator.comparing((Dividend d) -> d.getCashEvent().getSeq() != null ? d.getCashEvent().getSeq() : (short) 1)
                        .thenComparing(Dividend::getId))
                .orElseThrow();
    }

    private void fillPayment(Run run, Dividend d, LocalDate paymentDate, PaymentDateSource source) {
        AssetCashEvent event = d.getCashEvent();
        event.setPaymentDate(paymentDate);
        event.setPaymentDateSource(source);
        cashEventRepository.save(event);
        run.filledEvents.add(event.getId());
        run.usedDividends.add(d.getId());
        run.touchedAssets.add(event.getAsset().getId());
    }

    /** Data de pagamento do provento; a do evento, se ja foi preenchida nesta rodada. */
    private static LocalDate paymentDateOf(Dividend d) {
        if (d.getPaymentDate() != null) {
            return d.getPaymentDate();
        }
        return d.getCashEvent() != null ? d.getCashEvent().getPaymentDate() : null;
    }

    private static boolean sameAsset(Dividend d, Asset asset) {
        return d.getPortfolioAsset() != null && d.getPortfolioAsset().getAsset() != null
                && Objects.equals(d.getPortfolioAsset().getAsset().getId(), asset.getId());
    }

    private static boolean sameShares(Dividend d, BigDecimal shares) {
        return d.getSharesOnDate() != null && d.getSharesOnDate().compareTo(shares) == 0;
    }

    private static boolean sameMoney(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static ItemResult result(Line l, LocalDate dataCom, Status status, String message) {
        return new ItemResult(l.ref(), l.ticker(), l.type(), dataCom != null ? dataCom : l.dataCom(), l.paymentDate(),
                l.netAmount(), status, message);
    }

    private static ItemResult result(DeducedPayment p, Status status, String message) {
        return new ItemResult(p.ref(), p.ticker(), p.type(), p.dataCom(), p.paymentDate(), p.netAmount(), status, message);
    }
}
