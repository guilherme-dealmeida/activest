package com.activest.domain.integration;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.dividend.Dividend;
import com.activest.domain.dividend.DividendRepository;
import com.activest.domain.dividend.DividendType;
import com.activest.domain.integration.source.ImportedMovement;
import com.activest.domain.integration.source.MovementKind;
import com.activest.domain.portfolio.PortfolioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Proventos que a fonte informa pela API (B3, Area do Investidor: Movimentacao e Eventos Provisionados)
 * conferidos com os proventos da carteira (docs/planos/PLANO_INTEGRACAO_B3.md, 6.8 e C3). <b>So le</b>:
 * nada vai para {@code dividends} nem para o catalogo (P3) — provento da carteira vem do catalogo, e o
 * catalogo vem das fontes de catalogo (B3, FNET, CVM). O resultado vira o motivo do item nao importado —
 * {@code integration_run_items.reason}, sem coluna nova (P2).
 *
 * <p>Cada provento informado cai em um caso ({@link Outcome}): ja esta na carteira (mesmo ativo, tipo,
 * pagamento, cotas e liquido); esta na carteira, mas o evento do catalogo ainda nao tem a data de
 * pagamento; nao esta na carteira (o catalogo nao tem o evento); ou nao da para conferir.</p>
 *
 * <p>Roda na pre-visualizacao e de novo na confirmacao: na primeira importacao a carteira ainda nao
 * tem as posicoes, e os proventos dela so existem depois que as negociacoes entram.</p>
 */
@Slf4j
@Component
public class DividendReconciliation {

    public enum Outcome { JA_EXISTE, SEM_DATA, SEM_EVENTO, RECUSADO }

    /** Um provento informado pela fonte: um pagamento recebido (ou a receber). */
    record Payment(String ref, String ticker, DividendType type, LocalDate paymentDate, BigDecimal shares,
                     BigDecimal net) {
    }

    record Result(String ref, Outcome outcome, String message) {
    }

    private final DividendRepository dividendRepository;
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;

    public DividendReconciliation(DividendRepository dividendRepository, AssetRepository assetRepository,
                                PortfolioRepository portfolioRepository) {
        this.dividendRepository = dividendRepository;
        this.assetRepository = assetRepository;
        this.portfolioRepository = portfolioRepository;
    }

    /** externalId -> motivo com o resultado da conferencia, para os proventos (CASH_EVENT) entre os movimentos. */
    @Transactional(readOnly = true)
    public Map<String, String> reconcile(UUID userId, List<ImportedMovement> movements) {
        Map<String, ImportedMovement> cashEvents = new HashMap<>();
        List<Payment> payments = new ArrayList<>();
        for (ImportedMovement m : movements) {
            if (m.kind() != MovementKind.CASH_EVENT) {
                continue;
            }
            cashEvents.put(m.externalId(), m);
            payments.add(new Payment(m.externalId(), m.asset(), type(m.market()), m.tradeDate(), m.grossQuantity(),
                m.quoteAmount()));
        }
        if (payments.isEmpty()) {
            return Map.of();
        }
        List<Result> results;
        try {
            results = reconcilePayments(userId, payments);
        } catch (RuntimeException e) {
            log.warn("Conferencia de proventos da integracao nao rodou: {}", e.getMessage());
            return Map.of();
        }
        Map<String, String> reasons = new HashMap<>();
        for (Result r : results) {
            ImportedMovement m = cashEvents.get(r.ref());
            if (m != null) {
                reasons.put(r.ref(), reason(m, r));
            }
        }
        return reasons;
    }

    /** A conferencia em si, pagamento a pagamento (em ordem de pagamento); um provento da carteira casa uma vez so. */
    List<Result> reconcilePayments(UUID userId, List<Payment> payments) {
        var portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        if (portfolio == null) {
            return payments.stream().map(p -> new Result(p.ref(), Outcome.RECUSADO, "usuario sem carteira.")).toList();
        }
        List<Dividend> mine = new ArrayList<>(dividendRepository.findByPortfolioIdWithAsset(portfolio.getId()));
        Set<Long> used = new HashSet<>();
        return payments.stream()
                .sorted(Comparator.comparing(Payment::paymentDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> reconcile(mine, used, p))
                .toList();
    }

    private Result reconcile(List<Dividend> mine, Set<Long> used, Payment p) {
        if (p.ticker() == null || p.type() == null || p.paymentDate() == null || p.shares() == null || p.net() == null) {
            return new Result(p.ref(), Outcome.RECUSADO, "dados incompletos (ativo, tipo, pagamento, cotas e liquido).");
        }
        Asset asset = assetRepository.findByTicker(p.ticker()).orElse(null);
        if (asset == null) {
            return new Result(p.ref(), Outcome.RECUSADO, "ativo fora do catalogo.");
        }
        Dividend match = mine.stream()
                .filter(d -> !used.contains(d.getId()))
                .filter(d -> sameAsset(d, asset) && d.getType() == p.type())
                .filter(d -> p.paymentDate().equals(d.getPaymentDate()))
                .filter(d -> sameShares(d, p.shares()) && sameAmount(d.netOrGross(), p.net()))
                .findFirst().orElse(null);
        if (match != null) {
            used.add(match.getId());
            return new Result(p.ref(), Outcome.JA_EXISTE, null);
        }
        Dividend withoutDate = mine.stream()
                .filter(d -> !used.contains(d.getId()))
                .filter(d -> d.getPaymentDate() == null)
                .filter(d -> sameAsset(d, asset) && d.getType() == p.type())
                .filter(d -> sameShares(d, p.shares()) && sameAmount(d.netOrGross(), p.net()))
                .min(Comparator.comparing(Dividend::getExDate).thenComparing(Dividend::getId))
                .orElse(null);
        if (withoutDate != null) {
            used.add(withoutDate.getId());
            return new Result(p.ref(), Outcome.SEM_DATA, null);
        }
        return new Result(p.ref(), Outcome.SEM_EVENTO, null);
    }

    private static boolean sameAsset(Dividend d, Asset asset) {
        return d.getPortfolioAsset() != null && d.getPortfolioAsset().getAsset() != null
                && Objects.equals(d.getPortfolioAsset().getAsset().getId(), asset.getId());
    }

    private static boolean sameShares(Dividend d, BigDecimal shares) {
        return d.getSharesOnDate() != null && d.getSharesOnDate().compareTo(shares) == 0;
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    static String reason(ImportedMovement m, Result r) {
        String p = "Provento (" + (m.market() != null ? m.market() : "provento")
            + (m.asset() != null ? ", " + m.asset() : "") + ")";
        return switch (r.outcome()) {
            case JA_EXISTE -> p + " conferido: já está na carteira.";
            case SEM_DATA -> p + " está na carteira, mas o catálogo ainda não tem a data de pagamento.";
            case SEM_EVENTO -> p + " não está na carteira: o catálogo não tem este evento (pendência do catálogo).";
            case RECUSADO -> p + " não conferido: " + r.message();
        };
    }

    /** Tipo da B3 ('Dividendo', 'Juros Sobre Capital Próprio', 'Rendimento', ... 'a receber') no tipo da carteira. */
    static DividendType type(String description) {
        if (description == null) {
            return null;
        }
        String t = Normalizer.normalize(description.toUpperCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (t.contains("JUROS")) {
            return DividendType.JCP;
        }
        if (t.contains("DIVIDENDO")) {
            return DividendType.DIVIDENDO;
        }
        if (t.contains("RENDIMENTO")) {
            return DividendType.RENDIMENTO;
        }
        if (t.contains("AMORTIZACAO")) {
            return DividendType.AMORTIZACAO;
        }
        if (t.contains("RESTITUICAO")) {
            return DividendType.RESTITUICAO_CAPITAL;
        }
        return null;
    }
}
