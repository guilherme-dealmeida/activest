package com.primevest.domain.portfolio;

import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.dividend.DividendRecalculator;
import com.primevest.domain.dividend.DividendTaxVerifier;
import com.primevest.domain.fixedincome.FixedIncomeValuation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Supplier;

/**
 * A carteira reage ao catalogo (docs/planos/PLANO_ADM_API.md, 10): quando a adm-api avisa que o catalogo
 * mudou, refaz os proventos e a valorizacao das posicoes dos ativos envolvidos. So a app-api
 * grava dado de usuario — a adm-api grava eventos e precos e publica {@link CatalogChange}.
 *
 * <ul>
 *   <li>PROVENTOS: eventos novos ou completados — proventos ({@link DividendRecalculator}) e
 *   valorizacao de cada posicao;</li>
 *   <li>FECHAMENTO: preco novo (acoes, cripto, Tesouro) — so a valorizacao ({@link AppreciationUpdater});</li>
 *   <li>INDICES: indice de mercado novo ou corrigido — a curva da renda fixa privada ({@link FixedIncomeValuation},
 *   docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R5). Todos os titulos abertos: a data de referencia (o ultimo CDI)
 *   muda para todos, inclusive o prefixado.</li>
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
    private final FixedIncomeValuation fixedIncomeValuation;
    private final TransactionTemplate tx;

    public PositionRefreshService(PortfolioAssetRepository portfolioAssetRepository,
                                  DividendRecalculator dividendRecalculator,
                                  AppreciationUpdater appreciationUpdater,
                                  DividendTaxVerifier dividendTaxVerifier,
                                  FixedIncomeValuation fixedIncomeValuation,
                                  PlatformTransactionManager transactionManager) {
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.dividendRecalculator = dividendRecalculator;
        this.appreciationUpdater = appreciationUpdater;
        this.dividendTaxVerifier = dividendTaxVerifier;
        this.fixedIncomeValuation = fixedIncomeValuation;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Resultado: posicoes visitadas e proventos novos (so em PROVENTOS). */
    public record Result(int positions, int newDividends) {
    }

    /**
     * O que mais reage ao catalogo depois do recalculo: mensagens da caixa (docs/planos/PLANO_NOTIFICACOES.md) —
     * bonificacao sem custo, aviso de catalogo para quem tem a posicao, alerta de preco, "padrao mudou".
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    List<com.primevest.domain.notification.CatalogReaction> reactions = List.of();

    /** "Catalogo mudou" vindo da adm-api. */
    public Result refresh(CatalogChange change) {
        Result r = recalculateFor(change);
        for (com.primevest.domain.notification.CatalogReaction reaction : reactions) {
            try {
                reaction.after(change);
            } catch (RuntimeException e) {
                log.warn("Carteira: reacao {} ao catalogo ({}) falhou: {}", reaction.getClass().getSimpleName(),
                        change.origin(), e.getMessage());
            }
        }
        return r;
    }

    private synchronized Result recalculateFor(CatalogChange change) {
        if (change.origin() == CatalogChange.Origin.BALANCOS) {
            return new Result(0, 0);
        }
        if (change.origin() == CatalogChange.Origin.INDICES) {
            int changed = refreshFixedIncome();
            log.info("Carteira: indices mudaram ({}) — {} titulos de renda fixa revalorizados", change.indexCodes(), changed);
            return new Result(changed, 0);
        }
        if (change.assetIds().isEmpty()) {
            return new Result(0, 0);
        }
        Result r = switch (change.origin()) {
            case FECHAMENTO -> new Result(appreciationUpdater.refreshAssets(change.assetIds()), 0);
            case PROVENTOS -> recalculate(() -> portfolioAssetRepository.findAllByAssetIdIn(change.assetIds()));
            case INDICES, BALANCOS -> new Result(0, 0);
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
            fixedIncomeValuation.refresh(all);
            return new Result(all.size(), dividends);
        });
    }

    /** A curva de todos os titulos de renda fixa privada em aberto. */
    public int refreshFixedIncome() {
        Integer n = tx.execute(s -> fixedIncomeValuation.refreshAll());
        return n == null ? 0 : n;
    }

    /** Na subida: IR pendente dos proventos, valorizacao das posicoes com cotacao e a curva da renda fixa. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        dividendTaxVerifier.onStartup();
        int updated = appreciationUpdater.refreshAll();
        int curve = refreshFixedIncome();
        log.info("Valorizacao regravada na subida: {} posicoes com cotacao e {} titulos de renda fixa alterados",
                updated, curve);
    }
}
