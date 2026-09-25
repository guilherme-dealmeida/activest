package com.primevest.domain.dividend;

import com.primevest.domain.portfolio.PortfolioAssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Na subida da API, recalcula as posicoes que tem provento AUTO com o IR ainda
 * nao calculado ({@code tax_checked_at} nulo) — PLANO_IR_PROVENTOS.md, 4.5.
 *
 * <p>Cobre dois casos sem passo manual:</p>
 * <ul>
 *   <li>a carga inicial: os proventos anteriores a V23 nasceram sem IR;</li>
 *   <li>mudanca de lei: a migration que troca a aliquota zera
 *   {@code tax_checked_at} dos proventos afetados, e a subida seguinte os
 *   recalcula.</li>
 * </ul>
 * <p>Proventos novos nao dependem daqui: nascem com o IR no
 * {@link DividendRecalculator}. Isto e a rede de seguranca.</p>
 *
 * <p>Idempotente: a segunda execucao nao encontra nada. Falha numa posicao e
 * logada e nao impede as outras (cada uma na sua transacao).</p>
 */
@Slf4j
@Component
public class DividendTaxVerifier {

    private final DividendRepository dividendRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final DividendRecalculator dividendRecalculator;
    private final TransactionTemplate tx;

    public DividendTaxVerifier(DividendRepository dividendRepository,
                               PortfolioAssetRepository portfolioAssetRepository,
                               DividendRecalculator dividendRecalculator,
                               PlatformTransactionManager transactionManager) {
        this.dividendRepository = dividendRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.dividendRecalculator = dividendRecalculator;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            verify();
        } catch (RuntimeException e) {
            log.error("IR dos proventos: verificacao na subida falhou: {}", e.getMessage(), e);
        }
    }

    /** @return quantas posicoes foram recalculadas */
    public int verify() {
        List<UUID> pending = dividendRepository.findPositionIdsWithTaxPending();
        if (pending.isEmpty()) {
            log.debug("IR dos proventos: nada pendente");
            return 0;
        }
        int done = 0;
        int failed = 0;
        for (UUID id : pending) {
            try {
                tx.executeWithoutResult(s -> portfolioAssetRepository.findById(id).ifPresent(pa ->
                        dividendRecalculator.recalculate(pa, dividendRecalculator.chronologicalTransactions(pa))));
                done++;
            } catch (RuntimeException e) {
                failed++;
                log.error("IR dos proventos: posicao {} nao foi recalculada: {}", id, e.getMessage(), e);
            }
        }
        log.info("IR dos proventos: {} posicoes recalculadas na subida{}", done,
                failed > 0 ? ", " + failed + " com falha (nova tentativa na proxima subida)" : "");
        return done;
    }
}
