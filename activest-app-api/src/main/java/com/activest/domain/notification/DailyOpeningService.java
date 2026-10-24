package com.activest.domain.notification;

import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.corporateaction.AssetStockEvent;
import com.activest.domain.corporateaction.AssetStockEventRepository;
import com.activest.domain.corporateaction.CorporateActionApplier;
import com.activest.domain.corporateaction.StockEventCostRepository;
import com.activest.domain.corporateaction.StockEventType;
import com.activest.domain.dividend.Dividend;
import com.activest.domain.dividend.DividendRepository;
import com.activest.domain.dividend.DividendType;
import com.activest.domain.portfolio.BonusCostService;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.PortfolioAssetRepository;
import com.activest.domain.portfolio.PositionCalculator;
import com.activest.domain.transaction.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/**
 * Rotina de abertura do dia — [B7] (docs/planos/PLANO_NOTIFICACOES.md, seção 7). Roda à meia-noite e na subida da
 * app-api ("meia-noite ou o primeiro horário do dia com o sistema no ar"), em ordem:
 * <ol>
 *   <li>liquidação dos títulos vencidos (seção 6);</li>
 *   <li>proventos pagos hoje — uma mensagem por ativo (5.3);</li>
 *   <li>desdobramento, grupamento e bonificação com custo com data-ex hoje (5.4);</li>
 *   <li>conferências: bonificação sem custo, aviso de catálogo para quem tem a posição, alertas de preço.</li>
 * </ol>
 * <b>Sem coluna de controle:</b> tudo é idempotente pelas chaves das mensagens (R10) — rodar duas vezes no dia não
 * duplica nada. Mensagem de D0 nunca é retroativa (R11): se o sistema não subir no dia, ela não existe.
 */
@Slf4j
@Service
public class DailyOpeningService {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final DividendRepository dividends;
    private final AssetStockEventRepository stockEvents;
    private final StockEventCostRepository stockEventCosts;
    private final PortfolioAssetRepository positions;
    private final PositionCalculator calculator;
    private final NotificationService notifications;
    private final BonusCostService bonusCosts;
    private final CatalogNoticeFanout fanout;
    private final TransactionTemplate readOnly;

    /** Etapas de outros escopos que entram na abertura (liquidação, alerta de preço). */
    @Autowired(required = false)
    List<DailyOpeningStep> extraSteps = List.of();

    public DailyOpeningService(DividendRepository dividends, AssetStockEventRepository stockEvents,
                               StockEventCostRepository stockEventCosts, PortfolioAssetRepository positions,
                               PositionCalculator calculator, NotificationService notifications,
                               BonusCostService bonusCosts, CatalogNoticeFanout fanout,
                               PlatformTransactionManager transactionManager) {
        this.dividends = dividends;
        this.stockEvents = stockEvents;
        this.stockEventCosts = stockEventCosts;
        this.positions = positions;
        this.calculator = calculator;
        this.notifications = notifications;
        this.bonusCosts = bonusCosts;
        this.fanout = fanout;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** Uma etapa da abertura vinda de outro escopo, na ordem de {@link #order()}. */
    public interface DailyOpeningStep {
        /** Menor primeiro: a liquidação (0) vem antes das mensagens; a conferência de preço (100) depois. */
        int order();

        String name();

        int run(LocalDate today);
    }

    public record Result(Map<String, Integer> steps) {
    }

    public synchronized Result run(String reason) {
        return run(NotificationService.today(), reason);
    }

    /** {@code today} explícito para os testes. */
    public synchronized Result run(LocalDate today, String reason) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<DailyOpeningStep> extras = extraSteps.stream().sorted(Comparator.comparingInt(DailyOpeningStep::order))
            .toList();
        extras.stream().filter(s -> s.order() < 50).forEach(s -> out.put(s.name(), step(s.name(), () -> s.run(today))));
        out.put("proventos pagos", step("proventos pagos", () -> paidDividends(today)));
        out.put("eventos aplicados", step("eventos aplicados", () -> appliedEvents(today)));
        out.put("bonificação sem custo", step("bonificação sem custo", bonusCosts::check));
        out.put("avisos de catálogo", step("avisos de catálogo", () -> {
            fanout.run();
            return 0;
        }));
        extras.stream().filter(s -> s.order() >= 50).forEach(s -> out.put(s.name(), step(s.name(), () -> s.run(today))));
        log.info("Abertura do dia {} ({}): {}", today.format(BR), reason, out);
        return new Result(out);
    }

    private int step(String name, Supplier<Integer> work) {
        try {
            Integer n = work.get();
            return n == null ? 0 : n;
        } catch (RuntimeException e) {
            log.error("Abertura do dia: {} falhou: {}", name, e.getMessage(), e);
            return -1;
        }
    }

    // ------------------------------------------------------------------ proventos pagos hoje (5.3)

    /** Uma mensagem por ativo e dia, com uma linha por provento. Sem data de pagamento: nunca entra. */
    int paidDividends(LocalDate today) {
        Map<UUID, List<Dividend>> byPosition = new LinkedHashMap<>();
        Map<UUID, UUID> owner = new HashMap<>();
        Map<UUID, String> ticker = new HashMap<>();
        readOnly.executeWithoutResult(s -> dividends.findPaidOnWithOwner(today).forEach(d -> {
            PortfolioAsset pa = d.getPortfolioAsset();
            byPosition.computeIfAbsent(pa.getId(), k -> new ArrayList<>()).add(d);
            owner.put(pa.getId(), pa.getPortfolio().getUser().getId());
            ticker.put(pa.getId(), pa.getAsset().getTicker());
        }));
        int created = 0;
        for (Map.Entry<UUID, List<Dividend>> e : byPosition.entrySet()) {
            List<Dividend> list = e.getValue();
            BigDecimal gross = list.stream().map(Dividend::getTotalAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal net = list.stream().map(d -> d.getNetAmount() != null ? d.getNetAmount() : d.getTotalAmount())
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            String t = ticker.get(e.getKey());
            NotificationDraft.Builder b = NotificationDraft.of(NotificationKind.PROVENTO_PAGO).user(owner.get(e.getKey()))
                .title(t + " — " + money(net) + (list.size() == 1 ? " de provento pago hoje" : " de proventos pagos hoje"))
                .body(t + " paga hoje (" + today.format(BR) + ") " + (list.size() == 1 ? "um provento" : list.size()
                    + " proventos") + " sobre as cotas que você tinha na data-com: " + money(gross) + " bruto, "
                    + money(net) + " líquido.")
                .ref("POSITION", e.getKey()).key(e.getKey() + ":" + today)
                .link("Ver proventos", "/dividends");
            for (Dividend d : list) {
                b.line(typeLabel(d.getType()) + " · data-com " + d.getExDate().format(BR) + " · "
                    + money(d.getAmountPerShare(), 8) + " por cota × " + plain(d.getSharesOnDate()) + " = "
                    + money(d.getTotalAmount()) + (d.getNetAmount() != null
                        && d.getTotalAmount() != null && d.getNetAmount().compareTo(d.getTotalAmount()) != 0
                        ? " (líquido " + money(d.getNetAmount()) + ")" : ""));
            }
            if (notifications.notify(b.build()).isPresent()) {
                created++;
            }
        }
        return created;
    }

    // ------------------------------------------------------------------ eventos com data-ex hoje (5.4)

    /** A data-ex de um evento: o pregão seguinte à data-com. */
    static LocalDate dataEx(LocalDate dataCom) {
        LocalDate d = dataCom.plusDays(1);
        while (!BusinessCalendar.isBusinessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    int appliedEvents(LocalDate today) {
        record Msg(UUID userId, UUID positionId, String ticker, AssetStockEvent event, BigDecimal before,
                   BigDecimal after, BigDecimal cost) {
        }
        List<Msg> msgs = new ArrayList<>();
        readOnly.executeWithoutResult(s -> {
            List<AssetStockEvent> events = stockEvents.findByDataComBetween(today.minusDays(15), today.minusDays(1))
                .stream().filter(e -> dataEx(e.getDataCom()).equals(today)).toList();
            if (events.isEmpty()) {
                return;
            }
            Map<Long, List<AssetStockEvent>> byAsset = new HashMap<>();
            events.forEach(e -> byAsset.computeIfAbsent(e.getAsset().getId(), k -> new ArrayList<>()).add(e));
            for (PortfolioAsset pa : positions.findAllByAssetIdIn(byAsset.keySet())) {
                List<Transaction> txs = calculator.chronologicalTransactions(pa);
                if (txs.isEmpty()) {
                    continue;
                }
                var steps = calculator.stockEventsOf(pa);
                Map<Long, BigDecimal> userCosts = new HashMap<>();
                stockEventCosts.findByPortfolioAssetId(pa.getId())
                    .forEach(c -> userCosts.put(c.getStockEventId(), c.getCostPerShare()));
                for (AssetStockEvent e : byAsset.get(pa.getAsset().getId())) {
                    BigDecimal cost = e.getAssignedCost() != null ? e.getAssignedCost() : userCosts.get(e.getId());
                    if (e.getType() == StockEventType.BONIFICACAO && cost == null) {
                        continue; // sem custo: é a pendência "Informar custo", não esta mensagem
                    }
                    BigDecimal before = calculator.sharesOn(txs, steps, e.getDataCom());
                    if (before == null || before.signum() <= 0) {
                        continue;
                    }
                    BigDecimal after = CorporateActionApplier.apply(new CorporateActionApplier.Holding(before,
                        BigDecimal.ZERO), e.toStep(cost)).shares();
                    msgs.add(new Msg(pa.getPortfolio().getUser().getId(), pa.getId(), pa.getAsset().getTicker(), e,
                        before, after, cost));
                }
            }
        });
        int created = 0;
        for (Msg m : msgs) {
            AssetStockEvent e = m.event();
            String what = switch (e.getType()) {
                case DESDOBRAMENTO -> "desdobramento de " + ratio(e) ;
                case GRUPAMENTO -> "grupamento de " + ratio(e);
                case BONIFICACAO -> "bonificação de " + pct(e.getFactor());
            };
            String body = switch (e.getType()) {
                case DESDOBRAMENTO, GRUPAMENTO -> "O " + what + " (data-com " + e.getDataCom().format(BR)
                    + ") entrou na sua posição hoje: as cotas passaram de " + plain(m.before()) + " para "
                    + plain(m.after()) + " e o preço médio foi ajustado na mesma proporção (o custo total não muda)."
                    + (fraction(e, m.before()) ? " A fração que sobrou é vendida em leilão pela B3 e paga em dinheiro." : "");
                case BONIFICACAO -> "A " + what + " (data-com " + e.getDataCom().format(BR) + ") entrou na sua posição "
                    + "hoje: " + plain(m.after().subtract(m.before())) + " ações novas a " + money(m.cost(), 4)
                    + " cada; as cotas passaram de " + plain(m.before()) + " para " + plain(m.after()) + ".";
            };
            if (notifications.notify(NotificationDraft.of(NotificationKind.EVENTO_APLICADO).user(m.userId())
                    .title(m.ticker() + " — " + what + " aplicado")
                    .body(body)
                    .ref("POSITION", m.positionId()).key(m.positionId() + ":" + e.getId())
                    .link("Ver carteira", "/portfolio")
                    .build()).isPresent()) {
                created++;
            }
        }
        return created;
    }

    private static boolean fraction(AssetStockEvent e, BigDecimal before) {
        BigDecimal r = e.getType().quantityRatio(e.getFactor());
        return r != null && before.multiply(r).stripTrailingZeros().scale() > 0;
    }

    /** "1 para 4" (desdobramento 300) ou "10 para 1" (grupamento 0,1). */
    private static String ratio(AssetStockEvent e) {
        BigDecimal r = e.getType().quantityRatio(e.getFactor());
        if (r == null) {
            return "fator " + plain(e.getFactor());
        }
        if (r.compareTo(BigDecimal.ONE) >= 0) {
            return "1 para " + plain(r);
        }
        return plain(BigDecimal.ONE.divide(r, 6, RoundingMode.HALF_UP)) + " para 1";
    }

    private static String typeLabel(DividendType t) {
        if (t == null) {
            return "Provento";
        }
        return switch (t) {
            case DIVIDENDO -> "Dividendo";
            case JCP -> "JCP";
            case RENDIMENTO -> "Rendimento";
            case AMORTIZACAO -> "Amortização";
            case RESTITUICAO_CAPITAL -> "Restituição de capital";
        };
    }

    static String money(BigDecimal v) {
        return money(v, 2);
    }

    static String money(BigDecimal v, int maxDecimals) {
        if (v == null) {
            return "—";
        }
        NumberFormat f = NumberFormat.getCurrencyInstance(PT_BR);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(maxDecimals);
        return f.format(v).replace(' ', ' ');
    }

    static String plain(BigDecimal v) {
        if (v == null) {
            return "—";
        }
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(8);
        return f.format(v);
    }

    private static String pct(BigDecimal v) {
        return plain(v) + "%";
    }
}
