package com.activest.domain.portfolio;

import com.activest.domain.corporateaction.AssetStockEvent;
import com.activest.domain.corporateaction.AssetStockEventRepository;
import com.activest.domain.corporateaction.StockEventCost;
import com.activest.domain.corporateaction.StockEventCostRepository;
import com.activest.domain.corporateaction.StockEventType;
import com.activest.domain.notification.Notification;
import com.activest.domain.notification.NotificationDraft;
import com.activest.domain.notification.NotificationKind;
import com.activest.domain.notification.NotificationService;
import com.activest.domain.transaction.Transaction;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Bonificação sem custo atribuído pela B3 (docs/planos/PLANO_NOTIFICACOES.md, 5.4 — PENDENCIAS 13.2). As ações novas
 * entram com custo zero provisório (o preço médio cai); o usuário informa o valor por ação do fato relevante e ele
 * vale só para a posição dele ({@code stock_event_costs}, V51). Enquanto não houver custo:
 * <ul>
 *   <li>a Carteira mostra o ícone de atenção ao lado do preço médio ({@link #pendingFor});</li>
 *   <li>a caixa tem a pendência {@link NotificationKind#BONIFICACAO_SEM_CUSTO}, com a ação "Informar custo"
 *   ({@link #check}) — criada para as bonificações novas e, na primeira rodada, para as que já existiam (D3).</li>
 * </ul>
 * Informar o custo recalcula o preço médio e resolve a pendência.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BonusCostService {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AssetStockEventRepository stockEvents;
    private final StockEventCostRepository costs;
    private final PortfolioAssetRepository positions;
    private final PositionCalculator calculator;
    private final AppreciationUpdater appreciationUpdater;
    private final NotificationService notifications;

    /** Uma bonificação sem custo que pega a posição. */
    public record Pending(Long stockEventId, LocalDate dataCom, BigDecimal factor, BigDecimal newShares, String label) {
    }

    /** As bonificações sem custo de cada posição (a posição tinha cotas na data-com e ninguém informou o custo). */
    @Transactional(readOnly = true)
    public Map<UUID, List<Pending>> pendingFor(Collection<PortfolioAsset> pas) {
        Map<Long, List<AssetStockEvent>> byAsset = withoutCostByAsset();
        if (byAsset.isEmpty() || pas.isEmpty()) {
            return Map.of();
        }
        List<PortfolioAsset> relevant = pas.stream()
            .filter(pa -> pa.getAsset() != null && byAsset.containsKey(pa.getAsset().getId())).toList();
        if (relevant.isEmpty()) {
            return Map.of();
        }
        Set<StockEventCost.Key> informed = new HashSet<>();
        costs.findByPortfolioAssetIdIn(relevant.stream().map(PortfolioAsset::getId).toList())
            .forEach(c -> informed.add(new StockEventCost.Key(c.getPortfolioAssetId(), c.getStockEventId())));

        Map<UUID, List<Pending>> out = new HashMap<>();
        for (PortfolioAsset pa : relevant) {
            List<Transaction> txs = calculator.chronologicalTransactions(pa);
            if (txs.isEmpty()) {
                continue;
            }
            var steps = calculator.stockEventsOf(pa);
            for (AssetStockEvent e : byAsset.get(pa.getAsset().getId())) {
                if (informed.contains(new StockEventCost.Key(pa.getId(), e.getId()))) {
                    continue;
                }
                BigDecimal shares = calculator.sharesOn(txs, steps, e.getDataCom());
                if (shares == null || shares.signum() <= 0 || e.getFactor() == null || e.getFactor().signum() <= 0) {
                    continue;
                }
                BigDecimal added = shares.multiply(e.getFactor()).divide(HUNDRED, 10, RoundingMode.HALF_UP)
                    .setScale(0, RoundingMode.DOWN);
                if (added.signum() <= 0) {
                    continue;
                }
                out.computeIfAbsent(pa.getId(), k -> new ArrayList<>())
                    .add(new Pending(e.getId(), e.getDataCom(), e.getFactor(), added, label(pa, e)));
            }
        }
        return out;
    }

    /**
     * Cria as pendências que faltam e resolve as que não valem mais (custo informado, ou a B3 passou a informar).
     * Idempotente pela chave posição + evento: roda no "catálogo mudou" de proventos e na rotina de abertura.
     */
    @Transactional
    public int check() {
        List<PortfolioAsset> all = positions.findAllByAssetIdIn(withoutCostByAsset().keySet());
        Map<UUID, PortfolioAsset> byId = new HashMap<>();
        all.forEach(pa -> byId.put(pa.getId(), pa));
        Map<UUID, List<Pending>> pending = pendingFor(all);

        Set<String> keep = new HashSet<>();
        int created = 0;
        for (Map.Entry<UUID, List<Pending>> entry : pending.entrySet()) {
            PortfolioAsset pa = byId.get(entry.getKey());
            UUID userId = pa.getPortfolio().getUser().getId();
            String ticker = pa.getAsset().getTicker();
            for (Pending p : entry.getValue()) {
                String key = pa.getId() + ":" + p.stockEventId();
                keep.add(key);
                boolean isNew = notifications.notify(NotificationDraft.of(NotificationKind.BONIFICACAO_SEM_CUSTO)
                    .user(userId)
                    .title(p.label() + " sem custo atribuído")
                    .body("A empresa bonificou " + pct(p.factor()) + " em ações (data-com " + p.dataCom().format(BR)
                        + "). A B3 não informou o custo atribuído, e as ações novas entraram na sua posição com custo "
                        + "zero provisório — o preço médio está mais baixo do que deveria. Informe o valor por ação: "
                        + "ele está no fato relevante da empresa.")
                    .ref("POSITION", pa.getId()).key(key)
                    .line("Ações novas na sua posição: " + p.newShares().toPlainString())
                    .action(new NotificationDraft.Action("INFORMAR_CUSTO", "Informar custo", null, Map.of(
                        "positionId", pa.getId().toString(), "stockEventId", p.stockEventId(), "ticker", ticker,
                        "label", p.label())))
                    .link("Ver carteira", "/portfolio")
                    .build()).isPresent();
                if (isNew) {
                    created++;
                }
            }
        }
        List<UUID> toResolve = new ArrayList<>();
        for (Notification n : notifications.openOf(NotificationKind.BONIFICACAO_SEM_CUSTO)) {
            if (!keep.contains(NotificationService.keyOf(n))) {
                toResolve.add(n.getId());
            }
        }
        notifications.resolveIds(toResolve);
        return created;
    }

    /** "Informar custo": grava o valor por ação para a posição, recalcula o preço médio e resolve a pendência. */
    @Transactional
    public void setCost(UUID userId, UUID positionId, Long stockEventId, BigDecimal costPerShare) {
        PortfolioAsset pa = positions.findOwnedById(userId, positionId)
            .orElseThrow(() -> new ResourceNotFoundException("Posição não encontrada"));
        AssetStockEvent e = stockEvents.findById(stockEventId)
            .filter(ev -> ev.getAsset().getId().equals(pa.getAsset().getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado"));
        if (e.getType() != StockEventType.BONIFICACAO) {
            throw new BusinessException("Só bonificação tem custo atribuído.");
        }
        if (e.getAssignedCost() != null) {
            throw new BusinessException("A B3 já informou o custo desta bonificação.");
        }
        if (costPerShare == null || costPerShare.signum() <= 0) {
            throw new BusinessException("Informe um custo por ação maior que zero.");
        }
        StockEventCost cost = costs.findById(new StockEventCost.Key(pa.getId(), stockEventId))
            .orElseGet(() -> new StockEventCost(pa.getId(), stockEventId, costPerShare));
        cost.setCostPerShare(costPerShare);
        costs.saveAndFlush(cost);
        appreciationUpdater.refresh(pa);
        notifications.resolve(NotificationKind.BONIFICACAO_SEM_CUSTO, userId, pa.getId() + ":" + stockEventId);
        log.info("Custo da bonificacao {} informado para a posicao {}: {}", stockEventId, pa.getId(), costPerShare);
    }

    private Map<Long, List<AssetStockEvent>> withoutCostByAsset() {
        Map<Long, List<AssetStockEvent>> byAsset = new HashMap<>();
        for (AssetStockEvent e : stockEvents.findWithoutCost(StockEventType.BONIFICACAO)) {
            byAsset.computeIfAbsent(e.getAsset().getId(), k -> new ArrayList<>()).add(e);
        }
        return byAsset;
    }

    private static String label(PortfolioAsset pa, AssetStockEvent e) {
        return pa.getAsset().getTicker() + " — bonificação de " + pct(e.getFactor()) + " em " + e.getDataCom().format(BR);
    }

    private static String pct(BigDecimal factor) {
        return factor.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }
}
