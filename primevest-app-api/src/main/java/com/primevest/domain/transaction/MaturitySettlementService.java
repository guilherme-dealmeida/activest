package com.primevest.domain.transaction;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.fixedincome.FixedIncomeCurve;
import com.primevest.domain.fixedincome.FixedIncomeLots;
import com.primevest.domain.fixedincome.FixedIncomeNote;
import com.primevest.domain.fixedincome.FixedIncomeValuation;
import com.primevest.domain.notification.CatalogReaction;
import com.primevest.domain.notification.DailyOpeningService;
import com.primevest.domain.notification.Notification;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Título vencido: o PrimeVest AVISA, não liquida (docs/planos/PLANO_VENDA_RENDA_FIXA.md, seção 6 — revoga a seção 6
 * do PLANO_NOTIFICACOES, que lançava a venda sozinho). O valor que cai na conta é o líquido, e só o usuário o sabe.
 *
 * <ul>
 *   <li><b>Quais:</b> posições abertas de Renda Fixa privada e Tesouro Direto com vencimento até hoje.</li>
 *   <li><b>Quando:</b> na rotina de abertura do dia, quando um índice ou preço novo chega e depois de o usuário gravar
 *   uma transação do ativo (a pendência nasce ou se resolve na hora).</li>
 *   <li><b>Mensagem:</b> {@link NotificationKind#RF_VENCIDO}, uma pendência por título (RF) ou por ativo da carteira
 *   (TD), com o líquido estimado (RF) ou cotas × PU oficial (TD) e o link que abre o formulário preenchido (D6).</li>
 *   <li><b>Resolução:</b> pelo sistema, quando a posição fica liquidada (RF: venda de liquidação total; TD: sem cotas
 *   em aberto).</li>
 *   <li><b>D7:</b> as pendências {@code RF_LIQUIDACAO_DIVERGENTE} e {@code RF_LIQUIDACAO_SEM_VALOR} abertas são
 *   resolvidas: o assunto delas deixou de existir (a segunda é absorvida pela {@code RF_VENCIDO}).</li>
 * </ul>
 *
 * <p>As vendas automáticas (origem SISTEMA) gravadas antes ficam como estão: não são refeitas nem apagadas.</p>
 */
@Slf4j
@Service
public class MaturitySettlementService implements DailyOpeningService.DailyOpeningStep, CatalogReaction {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final PortfolioAssetRepository positions;
    private final PortfolioRepository portfolios;
    private final PositionCalculator calculator;
    private final FixedIncomeValuation valuation;
    private final AssetPriceHistoryRepository prices;
    private final NotificationService notifications;
    private final TransactionTemplate readOnly;
    private final TransactionTemplate tx;

    public MaturitySettlementService(PortfolioAssetRepository positions, PortfolioRepository portfolios,
                                     PositionCalculator calculator, FixedIncomeValuation valuation,
                                     AssetPriceHistoryRepository prices, NotificationService notifications,
                                     PlatformTransactionManager transactionManager) {
        this.positions = positions;
        this.portfolios = portfolios;
        this.calculator = calculator;
        this.valuation = valuation;
        this.prices = prices;
        this.notifications = notifications;
        // Roda tambem depois do commit de outra transacao (ouvinte): sempre numa propria.
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.readOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Depois do commit (ouvinte) nao ha transacao ativa: a resolucao grava numa propria.
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public String name() {
        return "aviso dos vencidos";
    }

    /**
     * Um título vencido ainda em aberto, pronto para a mensagem.
     *
     * @param key    chave da pendência: o id da posição (RF) ou "td:carteira:ativo" (TD)
     * @param title  título da mensagem
     * @param body   corpo da mensagem
     * @param link   o formulário preenchido (D6)
     */
    private record Due(UUID userId, String key, String refType, Object refId, String title, String body,
                       String link) {
    }

    /** Rotina de abertura: avisa todos os vencidos em aberto e resolve os que já foram liquidados. */
    @Override
    public synchronized int run(LocalDate today) {
        resolveRetired();
        List<Due> due = readOnly.execute(s -> due(positions.findAllWithAsset(), today));
        due = due == null ? List.of() : due;
        int created = 0;
        for (Due d : due) {
            if (notifyDue(d)) {
                created++;
            }
        }
        // Resolucao (R6): pendencia aberta cuja posicao nao esta mais vencida em aberto.
        Set<String> open = new HashSet<>();
        due.forEach(d -> open.add(d.userId() + "|" + d.key()));
        List<UUID> done = notifications.openOf(NotificationKind.RF_VENCIDO).stream()
            .filter(n -> !open.contains(n.getUserId() + "|" + NotificationService.keyOf(n)))
            .map(Notification::getId).toList();
        tx.executeWithoutResult(s -> notifications.resolveIds(done));
        return created;
    }

    /** Índice novo (a curva chega ao vencimento) ou preço novo (o PU do Tesouro): confere de novo. */
    @Override
    public void after(CatalogChange change) {
        if (change.origin() == CatalogChange.Origin.INDICES || change.origin() == CatalogChange.Origin.FECHAMENTO) {
            run(NotificationService.today());
        }
    }

    /** Transação gravada pelo usuário: a pendência do ativo nasce (compra de título já vencido) ou se resolve. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWrite(PositionWrittenEvent e) {
        try {
            LocalDate today = NotificationService.today();
            record Check(List<Due> due, Set<String> keys) {
            }
            Check c = readOnly.execute(s -> portfolios.findFirstByUserId(e.userId()).map(p -> {
                List<PortfolioAsset> all = positions.findAllByPortfolioIdAndAssetId(p.getId(), e.assetId());
                Set<String> keys = new HashSet<>();
                for (PortfolioAsset pa : all) {
                    keys.add(pa.getId().toString());
                    keys.add(treasuryKey(p.getId(), e.assetId()));
                }
                return new Check(due(all, today), keys);
            }).orElse(null));
            if (c == null) {
                return;
            }
            Set<String> stillDue = new HashSet<>();
            for (Due d : c.due()) {
                notifyDue(d);
                stillDue.add(d.key());
            }
            tx.executeWithoutResult(s -> {
                for (String key : c.keys()) {
                    if (!stillDue.contains(key)) {
                        notifications.resolve(NotificationKind.RF_VENCIDO, e.userId(), key);
                    }
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Aviso de vencido depois da gravacao ({}): {}", e.assetId(), ex.getMessage());
        }
    }

    /** D7: as pendencias da liquidacao automatica perderam o assunto. */
    private void resolveRetired() {
        for (NotificationKind kind : List.of(NotificationKind.RF_LIQUIDACAO_DIVERGENTE,
                NotificationKind.RF_LIQUIDACAO_SEM_VALOR)) {
            List<UUID> ids = notifications.openOf(kind).stream().map(Notification::getId).toList();
            if (!ids.isEmpty()) {
                tx.executeWithoutResult(s -> notifications.resolveIds(ids));
                log.info("Vencidos: {} pendencias {} resolvidas (a liquidacao automatica saiu)", ids.size(), kind);
            }
        }
    }

    private boolean notifyDue(Due d) {
        return notifications.notify(NotificationDraft.of(NotificationKind.RF_VENCIDO).user(d.userId())
            .title(d.title()).body(d.body())
            .ref(d.refType(), d.refId()).key(d.key())
            .link("Lançar liquidação", d.link())
            .build()).isPresent();
    }

    private List<Due> due(Collection<PortfolioAsset> all, LocalDate today) {
        List<Due> out = new ArrayList<>();
        Map<String, List<PortfolioAsset>> treasuries = new LinkedHashMap<>();
        for (PortfolioAsset pa : all) {
            Asset asset = pa.getAsset();
            if (asset == null || asset.getAssetType() == null || pa.isSettled()) {
                continue;
            }
            if (asset.getAssetType().isTreasury()) {
                LocalDate maturity = treasuryMaturity(pa);
                if (maturity != null && !maturity.isAfter(today)) {
                    treasuries.computeIfAbsent(treasuryKey(pa.getPortfolio().getId(), asset.getId()),
                        k -> new ArrayList<>()).add(pa);
                }
            } else if (FixedIncomeValuation.usesCurve(pa)) {
                LocalDate maturity = pa.getNote().getMaturityDate();
                if (maturity != null && !maturity.isAfter(today)) {
                    Due d = fixedIncome(pa, maturity);
                    if (d != null) {
                        out.add(d);
                    }
                }
            }
        }
        for (Map.Entry<String, List<PortfolioAsset>> e : treasuries.entrySet()) {
            Due d = treasury(e.getKey(), e.getValue());
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ renda fixa privada

    private Due fixedIncome(PortfolioAsset pa, LocalDate maturity) {
        List<Transaction> txs = calculator.chronologicalTransactions(pa);
        if (txs.isEmpty()) {
            return null;
        }
        FixedIncomeNote note = pa.getNote();
        FixedIncomeLots.Result r = valuation.run(pa, FixedIncomeValuation.moves(txs), maturity, valuation.series());
        boolean noCurve = r == null || r.status() == FixedIncomeCurve.Status.UNSUPPORTED;
        BigDecimal net = noCurve || r.status() != FixedIncomeCurve.Status.MATURED ? null : r.estimatedNet();
        String title = label(pa) + " — vencido em " + maturity.format(BR) + ", lance a liquidação";
        String body;
        if (net != null && net.signum() > 0) {
            body = "O título venceu em " + maturity.format(BR) + ". Pela curva, o saldo bruto no vencimento é "
                + money(r.balance()) + " e o líquido estimado (descontados IR e IOF) é " + money(net) + ". Lance a "
                + "liquidação com o valor que caiu na sua conta; o PrimeVest calcula o bruto, o IR e o IOF.";
        } else if (noCurve) {
            body = "O título venceu em " + maturity.format(BR) + ", mas o PrimeVest não tem como calcular o saldo "
                + "dele (" + (r == null ? "sem curva" : r.reason()) + "). Lance a liquidação com o valor que você "
                + "recebeu.";
        } else {
            body = "O título venceu em " + maturity.format(BR) + ". O índice até o vencimento ainda não foi "
                + "publicado; lance a liquidação com o valor que caiu na sua conta.";
        }
        StringBuilder link = new StringBuilder("/transactions?settle=").append(pa.getId())
            .append("&date=").append(maturity);
        if (net != null && net.signum() > 0) {
            link.append("&amount=").append(net.toPlainString());
        }
        return new Due(pa.getPortfolio().getUser().getId(), pa.getId().toString(), "POSITION", pa.getId(), title,
            body, link.toString());
    }

    // ------------------------------------------------------------------ Tesouro Direto

    private Due treasury(String key, List<PortfolioAsset> titles) {
        PortfolioAsset first = titles.get(0);
        Asset asset = first.getAsset();
        LocalDate maturity = treasuryMaturity(first);
        BigDecimal open = BigDecimal.ZERO;
        for (PortfolioAsset pa : titles) {
            Position p = calculator.calculate(false, calculator.chronologicalTransactions(pa));
            if (p.totalShares() != null && p.totalShares().signum() > 0) {
                open = open.add(p.totalShares());
            }
        }
        if (open.signum() <= 0) {
            return null;
        }
        Optional<AssetPriceHistory> pu = prices.findByAssetIdAndReferenceDate(asset.getId(), maturity)
            .or(() -> {
                List<AssetPriceHistory> before = prices.findByAssetIdAndReferenceDateBetweenOrderByReferenceDateAsc(
                    asset.getId(), maturity.minusDays(30), maturity);
                return before.isEmpty() ? Optional.empty() : Optional.of(before.get(before.size() - 1));
            });
        BigDecimal price = pu.map(AssetPriceHistory::getClosePrice).filter(v -> v.signum() > 0).orElse(null);
        String title = asset.getTicker() + " — vencido em " + maturity.format(BR) + ", lance a liquidação";
        String body;
        StringBuilder link = new StringBuilder("/transactions?settle=").append(first.getId())
            .append("&date=").append(maturity).append("&shares=").append(open.stripTrailingZeros().toPlainString());
        if (price != null) {
            body = "O título venceu em " + maturity.format(BR) + ". Ainda há " + plain(open) + " cotas em aberto; "
                + "pelo preço oficial do Tesouro de " + pu.get().getReferenceDate().format(BR) + " (" + money(price)
                + "), o resgate bruto é " + money(open.multiply(price).setScale(2, RoundingMode.HALF_UP))
                + ". Lance a liquidação; o PrimeVest calcula IR e IOF por título.";
            link.append("&price=").append(price.toPlainString());
        } else {
            body = "O título venceu em " + maturity.format(BR) + " e ainda há " + plain(open) + " cotas em aberto, "
                + "mas o preço oficial do Tesouro desse dia não está no PrimeVest. Lance a liquidação com o valor "
                + "que você recebeu.";
        }
        return new Due(first.getPortfolio().getUser().getId(), key, "ASSET", asset.getId(), title, body,
            link.toString());
    }

    // ------------------------------------------------------------------ apoio

    private static String treasuryKey(UUID portfolioId, Long assetId) {
        return "td:" + portfolioId + ":" + assetId;
    }

    private static LocalDate treasuryMaturity(PortfolioAsset pa) {
        if (pa.getAsset().getTerms() != null && pa.getAsset().getTerms().getMaturityDate() != null) {
            return pa.getAsset().getTerms().getMaturityDate();
        }
        return pa.getNote() != null ? pa.getNote().getMaturityDate() : null;
    }

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
        return f.format(v).replace(' ', ' ');
    }

    private static String plain(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(8);
        return f.format(v);
    }
}
