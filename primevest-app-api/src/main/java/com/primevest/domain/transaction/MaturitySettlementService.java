package com.primevest.domain.transaction;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.fixedincome.FixedIncomeCurve;
import com.primevest.domain.fixedincome.FixedIncomeNote;
import com.primevest.domain.fixedincome.FixedIncomeValuation;
import com.primevest.domain.notification.CatalogReaction;
import com.primevest.domain.notification.DailyOpeningService;
import com.primevest.domain.notification.NotificationDraft;
import com.primevest.domain.notification.NotificationKind;
import com.primevest.domain.notification.NotificationService;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.portfolio.Position;
import com.primevest.domain.portfolio.PositionCalculator;
import com.primevest.domain.pricehistory.AssetPriceHistory;
import com.primevest.domain.pricehistory.AssetPriceHistoryRepository;
import com.primevest.domain.transaction.dto.TransactionCreateDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Liquidação automática do título vencido (docs/planos/PLANO_NOTIFICACOES.md, seção 6 — revoga a D8 do plano de
 * valorização da renda fixa: título vencido não continua rendendo, o sistema liquida).
 *
 * <ul>
 *   <li><b>Quais:</b> posições abertas de Renda Fixa privada e Tesouro Direto com vencimento até hoje.</li>
 *   <li><b>Quando:</b> na rotina de abertura do dia (a primeira rodada liquida todos os já vencidos), quando um
 *   índice ou preço novo chega, e logo depois de o usuário gravar uma compra — a compra retroativa de título já
 *   vencido nasce liquidada.</li>
 *   <li><b>Como:</b> uma venda com origem SISTEMA (selo "Automática"), na data do vencimento, pelas mesmas regras da
 *   venda digitada. RF privada: o saldo da curva no vencimento, com liquidação total. Tesouro: o PU oficial do dia
 *   do vencimento (ou o último antes dele) × as cotas de todos os títulos do ativo.</li>
 *   <li><b>Sem valor</b> (curva não suportada, PU ausente): nenhuma transação; pendência "informe o valor do
 *   resgate".</li>
 *   <li><b>Editada pelo usuário:</b> a rotina não a refaz; se a conta deixar de bater (compra editada depois), abre
 *   a pendência de liquidação divergente. Não editada: é refeita. Excluída: volta na rodada seguinte.</li>
 * </ul>
 */
@Slf4j
@Service
public class MaturitySettlementService implements DailyOpeningService.DailyOpeningStep, CatalogReaction {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final PortfolioAssetRepository positions;
    private final PortfolioRepository portfolios;
    private final TransactionRepository transactions;
    private final PositionCalculator calculator;
    private final FixedIncomeValuation valuation;
    private final AssetPriceHistoryRepository prices;
    private final TransactionService transactionService;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final TransactionTemplate readOnly;

    public MaturitySettlementService(PortfolioAssetRepository positions, PortfolioRepository portfolios,
                                     TransactionRepository transactions, PositionCalculator calculator,
                                     FixedIncomeValuation valuation, AssetPriceHistoryRepository prices,
                                     TransactionService transactionService, NotificationService notifications,
                                     PlatformTransactionManager transactionManager) {
        this.positions = positions;
        this.portfolios = portfolios;
        this.transactions = transactions;
        this.calculator = calculator;
        this.valuation = valuation;
        this.prices = prices;
        this.transactionService = transactionService;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(transactionManager);
        // Roda tambem depois do commit de outra transacao (ouvinte): sempre numa propria.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.readOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public String name() {
        return "liquidação dos vencidos";
    }

    /** Um alvo: um título de RF privada, ou um ativo do Tesouro de uma carteira (todos os títulos dele). */
    private record Target(UUID userId, UUID portfolioId, Long assetId, UUID positionId, boolean treasury) {
    }

    /** Rotina de abertura: todos os vencidos de todas as carteiras, com mensagem. */
    @Override
    public synchronized int run(LocalDate today) {
        List<Target> targets = readOnly.execute(s -> targets(positions.findAllWithAsset(), today));
        int done = 0;
        for (Target t : targets == null ? List.<Target>of() : targets) {
            if (settle(t, today, true)) {
                done++;
            }
        }
        return done;
    }

    /** Índice novo (a curva chega ao vencimento) ou preço novo (o PU do Tesouro): tenta de novo os que esperavam. */
    @Override
    public void after(CatalogChange change) {
        if (change.origin() == CatalogChange.Origin.INDICES || change.origin() == CatalogChange.Origin.FECHAMENTO) {
            run(NotificationService.today());
        }
    }

    /** Compra gravada pelo usuário: a retroativa de título vencido nasce liquidada; a editada refaz a venda. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWrite(PositionWrittenEvent e) {
        try {
            LocalDate today = NotificationService.today();
            List<Target> targets = readOnly.execute(s -> portfolios.findFirstByUserId(e.userId())
                .map(p -> targets(positions.findAllByPortfolioIdAndAssetId(p.getId(), e.assetId()), today))
                .orElse(List.of()));
            for (Target t : targets == null ? List.<Target>of() : targets) {
                settle(t, today, false);
            }
        } catch (RuntimeException ex) {
            log.warn("Liquidacao automatica depois da gravacao ({}): {}", e.assetId(), ex.getMessage());
        }
    }

    private List<Target> targets(Collection<PortfolioAsset> all, LocalDate today) {
        List<Target> out = new ArrayList<>();
        Set<String> treasuries = new HashSet<>();
        for (PortfolioAsset pa : all) {
            Asset asset = pa.getAsset();
            if (asset == null || asset.getAssetType() == null) {
                continue;
            }
            UUID userId = pa.getPortfolio().getUser().getId();
            if (asset.getAssetType().isTreasury()) {
                LocalDate maturity = treasuryMaturity(pa);
                if (maturity != null && !maturity.isAfter(today)
                        && treasuries.add(pa.getPortfolio().getId() + ":" + asset.getId())) {
                    out.add(new Target(userId, pa.getPortfolio().getId(), asset.getId(), null, true));
                }
            } else if (FixedIncomeValuation.usesCurve(pa)) {
                LocalDate maturity = pa.getNote().getMaturityDate();
                if (maturity != null && !maturity.isAfter(today)) {
                    out.add(new Target(userId, pa.getPortfolio().getId(), asset.getId(), pa.getId(), false));
                }
            }
        }
        return out;
    }

    private static LocalDate treasuryMaturity(PortfolioAsset pa) {
        if (pa.getAsset().getTerms() != null && pa.getAsset().getTerms().getMaturityDate() != null) {
            return pa.getAsset().getTerms().getMaturityDate();
        }
        return pa.getNote() != null ? pa.getNote().getMaturityDate() : null;
    }

    private boolean settle(Target t, LocalDate today, boolean notify) {
        try {
            Boolean r = t.treasury() ? settleTreasury(t, notify) : settleFixedIncome(t, notify);
            return Boolean.TRUE.equals(r);
        } catch (RuntimeException e) {
            log.error("Liquidacao automatica de {} falhou: {}", t, e.getMessage(), e);
            return false;
        }
    }

    // ------------------------------------------------------------------ renda fixa privada

    private Boolean settleFixedIncome(Target t, boolean notify) {
        record Plan(String ticker, String label, UUID noteId, LocalDate maturity, BigDecimal value, UUID saleId,
                    boolean saleEdited, BigDecimal saleAmount, boolean settled, String unsupported) {
        }
        Plan plan = readOnly.execute(s -> {
            PortfolioAsset pa = positions.findById(t.positionId()).orElse(null);
            if (pa == null) {
                return null;
            }
            FixedIncomeNote note = pa.getNote();
            List<Transaction> txs = calculator.chronologicalTransactions(pa);
            if (txs.isEmpty()) {
                return null;
            }
            List<Transaction> sistema = txs.stream()
                .filter(x -> x.getType() == TransactionType.SELL && x.getOrigin() == TransactionOrigin.SISTEMA).toList();
            List<Transaction> semSistema = txs.stream().filter(x -> !sistema.contains(x)).toList();
            if (pa.isSettled() && sistema.isEmpty()) {
                return null; // liquidada pelo usuario
            }
            FixedIncomeCurve.Result r = valuation.value(pa, semSistema, valuation.series());
            String unsupported = r == null || r.status() == FixedIncomeCurve.Status.UNSUPPORTED
                ? (r == null ? "sem curva" : r.reason()) : null;
            BigDecimal value = r != null && r.status() == FixedIncomeCurve.Status.MATURED ? r.balance() : null;
            Transaction sale = sistema.isEmpty() ? null : sistema.get(0);
            return new Plan(pa.getAsset().getTicker(), label(pa), note.getId(), note.getMaturityDate(), value,
                sale == null ? null : sale.getId(), sale != null && sale.getEditedAt() != null,
                sale == null ? null : sale.getTotalAmount(), pa.isSettled(), unsupported);
        });
        if (plan == null) {
            return false;
        }
        String key = t.positionId().toString();
        if (plan.unsupported() != null && plan.saleId() == null) {
            notifications.notify(NotificationDraft.of(NotificationKind.RF_LIQUIDACAO_SEM_VALOR).user(t.userId())
                .title(plan.label() + " — vencido, informe o valor do resgate")
                .body("O título venceu em " + plan.maturity().format(BR) + ", mas o PrimeVest não tem como calcular "
                    + "o saldo dele (" + plan.unsupported() + "). Lance o resgate pela tela de transações com o valor "
                    + "que você recebeu.")
                .ref("POSITION", t.positionId()).key(key)
                .link("Lançar resgate", "/transactions")
                .build());
            return false;
        }
        if (plan.value() == null || plan.value().signum() <= 0) {
            return false; // a curva ainda nao chegou ao vencimento (indice do dia nao saiu): tenta de novo depois
        }
        notifications.resolve(NotificationKind.RF_LIQUIDACAO_SEM_VALOR, t.userId(), key);

        if (plan.saleId() == null) {
            TransactionCreateDTO dto = new TransactionCreateDTO(plan.ticker(), TransactionType.SELL, null, null,
                plan.value(), BigDecimal.ZERO, plan.maturity(), null, true, plan.noteId(), null, null, null, null, null);
            tx.executeWithoutResult(s -> SystemWrites.run(() -> transactionService.createTransaction(t.userId(), dto)));
            log.info("Liquidacao automatica: {} ({}) liquidado em {} por {}", plan.label(), t.positionId(),
                plan.maturity(), plan.value());
            if (notify) {
                notifications.notify(NotificationDraft.of(NotificationKind.RF_LIQUIDADA).user(t.userId())
                    .title(plan.label() + " — vencido e liquidado")
                    .body("O título venceu em " + plan.maturity().format(BR) + " e o PrimeVest lançou o resgate de "
                        + money(plan.value()) + " (o saldo na curva no vencimento, valor bruto). A venda tem o selo "
                        + "\"Automática\"; se o valor que você recebeu foi outro, edite a venda.")
                    .ref("POSITION", t.positionId()).key(key)
                    .link("Ver transações", "/transactions")
                    .build());
            }
            return true;
        }
        // Ja liquidada pelo sistema: a conta ainda bate?
        boolean bate = plan.saleAmount() != null && plan.saleAmount().compareTo(plan.value()) == 0;
        if (bate) {
            notifications.resolve(NotificationKind.RF_LIQUIDACAO_DIVERGENTE, t.userId(), key);
            return false;
        }
        if (!plan.saleEdited()) {
            TransactionCreateDTO dto = new TransactionCreateDTO(plan.ticker(), TransactionType.SELL, null, null,
                plan.value(), BigDecimal.ZERO, plan.maturity(), null, true, plan.noteId(), null, null, null, null, null);
            tx.executeWithoutResult(s -> SystemWrites.run(() ->
                transactionService.updateTransaction(t.userId(), plan.saleId(), dto)));
            log.info("Liquidacao automatica refeita: {} agora {}", plan.label(), plan.value());
            return true;
        }
        notifications.notify(NotificationDraft.of(NotificationKind.RF_LIQUIDACAO_DIVERGENTE).user(t.userId())
            .title(plan.label() + " — a liquidação não bate com a curva")
            .body("Você editou a venda automática (" + money(plan.saleAmount()) + "), e depois disso as compras do "
                + "título mudaram: pela curva, o saldo no vencimento é " + money(plan.value()) + ". Confira a venda.")
            .ref("POSITION", t.positionId()).key(key)
            .link("Ver transações", "/transactions")
            .build());
        return false;
    }

    // ------------------------------------------------------------------ Tesouro Direto

    private Boolean settleTreasury(Target t, boolean notify) {
        record Plan(String ticker, LocalDate maturity, BigDecimal open, BigDecimal pu, LocalDate puDate,
                    UUID saleId, boolean saleEdited, BigDecimal saleShares) {
        }
        Plan plan = readOnly.execute(s -> {
            List<PortfolioAsset> titles = positions.findAllByPortfolioIdAndAssetId(t.portfolioId(), t.assetId());
            if (titles.isEmpty()) {
                return null;
            }
            Asset asset = titles.get(0).getAsset();
            LocalDate maturity = treasuryMaturity(titles.get(0));
            BigDecimal open = BigDecimal.ZERO;
            for (PortfolioAsset pa : titles) {
                Position p = calculator.calculate(false, calculator.chronologicalTransactions(pa));
                if (p.totalShares() != null && p.totalShares().signum() > 0) {
                    open = open.add(p.totalShares());
                }
            }
            List<Transaction> sistema = transactions.findByPortfolioIdAndAssetId(t.portfolioId(), t.assetId())
                .stream().filter(x -> x.getType() == TransactionType.SELL && x.getOrigin() == TransactionOrigin.SISTEMA)
                .toList();
            BigDecimal saleShares = sistema.stream().map(Transaction::getShares).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            Optional<AssetPriceHistory> pu = prices.findByAssetIdAndReferenceDate(asset.getId(), maturity)
                .or(() -> {
                    List<AssetPriceHistory> before = prices.findByAssetIdAndReferenceDateBetweenOrderByReferenceDateAsc(
                        asset.getId(), maturity.minusDays(30), maturity);
                    return before.isEmpty() ? Optional.empty() : Optional.of(before.get(before.size() - 1));
                });
            return new Plan(asset.getTicker(), maturity, open, pu.map(AssetPriceHistory::getClosePrice).orElse(null),
                pu.map(AssetPriceHistory::getReferenceDate).orElse(null),
                sistema.isEmpty() ? null : sistema.get(0).getId(),
                sistema.stream().anyMatch(x -> x.getEditedAt() != null), saleShares);
        });
        if (plan == null || plan.open().signum() <= 0) {
            return false;
        }
        String key = "td:" + t.portfolioId() + ":" + t.assetId();
        if (plan.pu() == null || plan.pu().signum() <= 0) {
            notifications.notify(NotificationDraft.of(NotificationKind.RF_LIQUIDACAO_SEM_VALOR).user(t.userId())
                .title(plan.ticker() + " — vencido, informe o valor do resgate")
                .body("O título venceu em " + plan.maturity().format(BR) + ", mas o preço oficial do Tesouro desse dia "
                    + "não está no PrimeVest. Lance o resgate pela tela de transações com o valor que você recebeu.")
                .ref("ASSET", t.assetId()).key(key)
                .link("Lançar resgate", "/transactions")
                .build());
            return false;
        }
        notifications.resolve(NotificationKind.RF_LIQUIDACAO_SEM_VALOR, t.userId(), key);

        if (plan.saleId() == null) {
            TransactionCreateDTO dto = new TransactionCreateDTO(plan.ticker(), TransactionType.SELL, plan.open(),
                plan.pu(), null, BigDecimal.ZERO, plan.maturity(), null, null, null, null, null, null, null, null);
            tx.executeWithoutResult(s -> SystemWrites.run(() -> transactionService.createTransaction(t.userId(), dto)));
            log.info("Liquidacao automatica: {} ({} cotas) a {} em {}", plan.ticker(), plan.open(), plan.pu(),
                plan.maturity());
            if (notify) {
                BigDecimal total = plan.open().multiply(plan.pu());
                notifications.notify(NotificationDraft.of(NotificationKind.RF_LIQUIDADA).user(t.userId())
                    .title(plan.ticker() + " — vencido e liquidado")
                    .body("O título venceu em " + plan.maturity().format(BR) + " e o PrimeVest lançou o resgate: "
                        + plain(plan.open()) + " cotas a " + money(plan.pu()) + " (preço oficial do Tesouro de "
                        + plan.puDate().format(BR) + "), " + money(total) + " brutos. A venda tem o selo "
                        + "\"Automática\"; se o valor que você recebeu foi outro, edite a venda.")
                    .ref("ASSET", t.assetId()).key(key)
                    .link("Ver transações", "/transactions")
                    .build());
            }
            return true;
        }
        // Sobrou cota depois da venda automatica (compra retroativa/editada): refaz a venda, se nao foi editada.
        if (!plan.saleEdited()) {
            TransactionCreateDTO dto = new TransactionCreateDTO(plan.ticker(), TransactionType.SELL,
                plan.saleShares().add(plan.open()), plan.pu(), null, BigDecimal.ZERO, plan.maturity(), null, null,
                null, null, null, null, null, null);
            tx.executeWithoutResult(s -> SystemWrites.run(() ->
                transactionService.updateTransaction(t.userId(), plan.saleId(), dto)));
            return true;
        }
        notifications.notify(NotificationDraft.of(NotificationKind.RF_LIQUIDACAO_DIVERGENTE).user(t.userId())
            .title(plan.ticker() + " — a liquidação não cobre todas as cotas")
            .body("Você editou a venda automática, e ainda sobram " + plain(plan.open()) + " cotas do título vencido. "
                + "Confira a venda.")
            .ref("ASSET", t.assetId()).key(key)
            .link("Ver transações", "/transactions")
            .build());
        return false;
    }

    // ------------------------------------------------------------------ apoio

    private static String label(PortfolioAsset pa) {
        FixedIncomeNote note = pa.getNote();
        String issuer = note != null && note.getIssuer() != null ? " " + note.getIssuer() : "";
        return pa.getAsset().getTicker() + issuer;
    }

    private static String money(BigDecimal v) {
        if (v == null) {
            return "—";
        }
        NumberFormat f = NumberFormat.getCurrencyInstance(PT_BR);
        return f.format(v).replace(' ', ' ');
    }

    private static String plain(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(8);
        return f.format(v);
    }
}
