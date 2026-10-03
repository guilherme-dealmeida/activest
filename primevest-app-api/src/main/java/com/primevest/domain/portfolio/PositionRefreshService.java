package com.primevest.domain.portfolio;

import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.dividend.DividendRecalculator;
import com.primevest.domain.dividend.DividendTaxVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Supplier;

/**
 * A carteira reage ao catalogo (PLANO_ADM_API.md, 10): quando a adm-api avisa que o catalogo
 * mudou, refaz os proventos e a valorizacao das posicoes dos ativos envolvidos. So a app-api
 * grava dado de usuario — a adm-api grava eventos e precos e publica {@link CatalogChange}.
 *
 * <ul>
 *   <li>PROVENTOS: eventos novos ou completados — proventos ({@link DividendRecalculator}) e
 *   valorizacao de cada posicao;</li>
 *   <li>FECHAMENTO: preco novo — so a valorizacao ({@link AppreciationUpdater}).</li>
 * </ul>
 *
 * <p>Tambem roda {@link #refreshAll} — uma vez por dia ({@code PositionRefreshScheduler}), que
 * cobre mensagem perdida, e na subida (antes: {@code AppreciationUpdater} e
 * {@code DividendTaxVerifier} na subida, agora so aqui).</p>
 */
@Slf4j
@Service
public class PositionRefreshService {

    private final PortfolioAssetRepository portfolioAssetRepository;
    private final DividendRecalculator dividendRecalculator;
    private final AppreciationUpdater appreciationUpdater;
    private final DividendTaxVerifier dividendTaxVerifier;
    private final TransactionTemplate tx;

    public PositionRefreshService(PortfolioAssetRepository portfolioAssetRepository,
                                  DividendRecalculator dividendRecalculator,
                                  AppreciationUpdater appreciationUpdater,
                                  DividendTaxVerifier dividendTaxVerifier,
                                  PlatformTransactionManager transactionManager) {
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.dividendRecalculator = dividendRecalculator;
        this.appreciationUpdater = appreciationUpdater;
        this.dividendTaxVerifier = dividendTaxVerifier;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Resultado: posicoes visitadas e proventos novos (so em PROVENTOS). */
    public record Result(int positions, int newDividends) {
    }

    /** "Catalogo mudou" vindo da adm-api. */
    public synchronized Result refresh(CatalogChange change) {
        if (change.assetIds().isEmpty()) {
            return new Result(0, 0);
        }
        Result r = switch (change.origin()) {
            case FECHAMENTO -> new Result(appreciationUpdater.refreshAssets(change.assetIds()), 0);
            case PROVENTOS -> recalculate(() -> portfolioAssetRepository.findAllByAssetIdIn(change.assetIds()));
        };
        log.info("Carteira: catalogo mudou ({}, {} ativos) — {} posicoes, {} proventos novos",
                change.origin(), change.assetIds().size(), r.positions(), r.newDividends());
        return r;
    }

    /** Todas as posicoes: proventos e valorizacao. Rede de seguranca diaria. */
    public synchronized Result refreshAll(String reason) {
        Result r = recalculate(portfolioAssetRepository::findAllWithAsset);
        log.info("Carteira ({}): {} posicoes recalculadas, {} proventos novos", reason, r.positions(), r.newDividends());
        return r;
    }

    /** Carrega e recalcula na mesma transacao: o recalculo navega ativo, tipo e transacoes. */
    private Result recalculate(Supplier<List<PortfolioAsset>> positions) {
        return tx.execute(s -> {
            int dividends = 0;
            List<PortfolioAsset> all = positions.get();
            for (PortfolioAsset pa : all) {
                dividends += dividendRecalculator.recalculate(pa, dividendRecalculator.chronologicalTransactions(pa));
                appreciationUpdater.refresh(pa);
            }
            return new Result(all.size(), dividends);
        });
    }

    /** Na subida: IR pendente dos proventos e valorizacao de todas as posicoes com cotacao. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        dividendTaxVerifier.onStartup();
        int updated = appreciationUpdater.refreshAll();
        log.info("Valorizacao regravada na subida: {} posicoes alteradas", updated);
    }
}
