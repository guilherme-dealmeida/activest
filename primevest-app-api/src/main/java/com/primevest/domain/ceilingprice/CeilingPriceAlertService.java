package com.primevest.domain.ceilingprice;

import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.notification.CatalogReaction;
import com.primevest.domain.notification.DailyOpeningService;
import com.primevest.domain.notification.Notification;
import com.primevest.domain.notification.NotificationDraft;
import com.primevest.domain.notification.NotificationKind;
import com.primevest.domain.notification.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Mensagens do Preço Teto (docs/planos/PLANO_NOTIFICACOES.md, 5.5 e 5.6).
 *
 * <p><b>Alerta de preço</b> — com o alerta ligado no cálculo, avisa quando o <b>preço atual</b> (REGRAS 2.6) fica
 * igual ou abaixo do Preço Ideal: só ao cruzar para baixo (volta a valer depois que o preço sobe acima do ideal) e no
 * máximo uma vez por ativo por dia. Roda a cada preço novo ("catálogo mudou" de fechamento: BRAPI e COTAHIST) e na
 * rotina de abertura.</p>
 *
 * <p><b>Padrão mudou</b> — balanço novo da CVM que muda um padrão de balanço do cálculo salvo (trimestres, ROE,
 * payout, ações; D5): pendência até o cálculo ser salvo de novo. Roda no "catálogo mudou" de balanços e na
 * abertura.</p>
 */
@Slf4j
@Service
public class CeilingPriceAlertService implements CatalogReaction, DailyOpeningService.DailyOpeningStep {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final CeilingPriceRepository repository;
    private final CeilingPriceService service;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public CeilingPriceAlertService(CeilingPriceRepository repository, CeilingPriceService service,
                                    NotificationService notifications, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.service = service;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void after(CatalogChange change) {
        if (change.origin() == CatalogChange.Origin.FECHAMENTO) {
            checkPrices(change.assetIds(), NotificationService.today());
        } else if (change.origin() == CatalogChange.Origin.BALANCOS) {
            checkDefaults(change.assetIds().isEmpty() ? null : change.assetIds());
        }
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public String name() {
        return "preço teto";
    }

    @Override
    public int run(LocalDate today) {
        return checkPrices(null, today) + checkDefaults(null);
    }

    /** O alerta de preço dos cálculos ligados ({@code assetIds} nulo = todos). Devolve quantos avisos saíram. */
    public synchronized int checkPrices(Collection<Long> assetIds, LocalDate today) {
        Integer n = tx.execute(s -> {
            int sent = 0;
            for (CeilingPrice cp : repository.findByAlertEnabledTrue()) {
                if (assetIds != null && !assetIds.contains(cp.getAsset().getId())) {
                    continue;
                }
                try {
                    if (check(cp, today)) {
                        sent++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Alerta de preco de {}: {}", cp.getAsset().getTicker(), e.getMessage());
                }
            }
            return sent;
        });
        return n == null ? 0 : n;
    }

    private boolean check(CeilingPrice cp, LocalDate today) {
        CeilingPriceService.PriceCheck pc = service.priceCheck(cp);
        if (pc.price() == null || pc.ideal() == null) {
            return false;
        }
        boolean below = pc.price().compareTo(pc.ideal()) <= 0;
        if (!below) {
            if (cp.isAlertBelowIdeal()) {
                cp.setAlertBelowIdeal(false); // rearma
                repository.save(cp);
            }
            return false;
        }
        if (cp.isAlertBelowIdeal()) {
            return false; // ja avisado nesta descida
        }
        cp.setAlertBelowIdeal(true);
        boolean shouldNotify = !today.equals(cp.getAlertLastOn());
        if (shouldNotify) {
            cp.setAlertLastOn(today);
        }
        repository.save(cp);
        if (!shouldNotify) {
            return false; // uma por ativo por dia
        }
        String ticker = cp.getAsset().getTicker();
        BigDecimal pct = pc.ideal().signum() > 0
            ? pc.price().divide(pc.ideal(), 6, java.math.RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
            : null;
        notifications.notify(NotificationDraft.of(NotificationKind.PRECO_ABAIXO_IDEAL).user(cp.getUser().getId())
            .title(ticker + " abaixo do Preço Ideal")
            .body(ticker + " está a " + money(pc.price()) + (pc.priceDate() == null ? "" : " (" + pc.priceDate()
                .format(BR) + ")") + ", abaixo do Preço Ideal de " + money(pc.ideal()) + " do seu cálculo"
                + (pct == null ? "" : " (" + plain(pct.abs()) + "% abaixo)") + ". O próximo aviso só vem depois que o "
                + "preço voltar acima do ideal.")
            .ref("CEILING_PRICE", cp.getId()).key(cp.getId() + ":" + today)
            .link("Ver cálculo", "/ceiling-prices/" + ticker)
            .build());
        return true;
    }

    /** "Padrão mudou" dos cálculos dos ativos ({@code assetIds} nulo = todos). */
    public synchronized int checkDefaults(Collection<Long> assetIds) {
        Integer n = tx.execute(s -> defaultsInTx(assetIds));
        return n == null ? 0 : n;
    }

    private int defaultsInTx(Collection<Long> assetIds) {
        List<CeilingPrice> all = assetIds == null ? repository.findAll() : repository.findByAssetIdIn(assetIds);
        int sent = 0;
        for (CeilingPrice cp : all) {
            try {
                List<String> changed = service.changedBalanceDefaults(cp);
                if (changed.isEmpty()) {
                    resolveDefaultsChanged(cp);
                    continue;
                }
                LocalDate last = service.latestQuarterEnd(cp);
                String ticker = cp.getAsset().getTicker();
                boolean created = notifications.notify(NotificationDraft.of(NotificationKind.PRECO_TETO_PADRAO_MUDOU)
                    .user(cp.getUser().getId())
                    .title(ticker + " — saiu balanço novo para o seu Preço Teto")
                    .body("Os padrões de balanço do seu cálculo de " + ticker + " mudaram desde que você o salvou"
                        + (last == null ? "" : " (último trimestre no catálogo: " + last.format(BR) + ")")
                        + ". O cálculo salvo continua como estava; abra-o e use ↻ nos campos destacados para trazer "
                        + "os valores novos, se quiser.")
                    .ref("CEILING_PRICE", cp.getId()).key(cp.getId() + ":" + (last == null ? "?" : last))
                    .lines(changed.stream().map(CeilingPriceAlertService::fieldLabel).toList())
                    .link("Ver cálculo", "/ceiling-prices/" + ticker)
                    .build()).isPresent();
                if (created) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.warn("Padrao mudou de {}: {}", cp.getAsset().getTicker(), e.getMessage());
            }
        }
        return sent;
    }

    /** O cálculo foi salvo de novo (ou o padrão voltou): o "padrão mudou" dele acabou. */
    public void resolveDefaultsChanged(CeilingPrice cp) {
        String prefix = cp.getId() + ":";
        List<UUID> ids = new ArrayList<>();
        for (Notification n : notifications.openOf(NotificationKind.PRECO_TETO_PADRAO_MUDOU)) {
            String key = NotificationService.keyOf(n);
            if (key != null && key.startsWith(prefix)) {
                ids.add(n.getId());
            }
        }
        notifications.resolveIds(ids);
    }

    private static String fieldLabel(String f) {
        return switch (f) {
            case "quarters" -> "Lucros trimestrais";
            case "shares" -> "Quantidade de ações";
            case "roePct" -> "ROE médio";
            case "payoutPct" -> "Payout médio";
            default -> f;
        };
    }

    private static String money(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(PT_BR);
        return f.format(v).replace(' ', ' ');
    }

    private static String plain(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(2);
        return f.format(v);
    }
}
