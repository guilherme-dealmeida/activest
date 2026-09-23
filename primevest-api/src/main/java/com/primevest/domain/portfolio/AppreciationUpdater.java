package com.primevest.domain.portfolio;

import com.primevest.domain.asset.Asset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Grava a valorizacao das posicoes com cotacao (Acoes, FIIs, ETFs, BDRs,
 * Cripto...) em portfolio_assets.appreciation.
 *
 * <p>A tela le a valorizacao gravada; nao calcula. Por isso o valor precisa
 * ser reescrito sempre que um dos dois lados da conta muda:</p>
 * <ul>
 *   <li><b>a cotacao</b> — PriceUpdateScheduler e AssetService.updateAssetPrice;</li>
 *   <li><b>as cotas ou o custo</b> — criacao, edicao e exclusao de transacao.</li>
 * </ul>
 * <p>Na subida da aplicacao todas as posicoes com cotacao sao regravadas, para
 * cobrir o que mudou enquanto ela estava fora (e as posicoes anteriores a esta
 * regra, que nasceram com zero).</p>
 *
 * <p>Valorizacao = cotas x cotacao atual - custo, com 2 casas. Cotas e custo
 * saem de {@link PositionCalculator}.</p>
 *
 * <p>Posicao sem cotacao (Renda Fixa, Tesouro, tipos MANUAL) nao e tocada:
 * nela appreciation e apurada por fora (a curva da renda fixa, quando
 * existir), e sobrescreve-la com zero apagaria esse valor.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppreciationUpdater {

    private static final int MONEY_SCALE = 2;

    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PositionCalculator positionCalculator;

    /** Uma posicao: depois de criar, editar ou excluir transacao dela. */
    @Transactional
    public void refresh(PortfolioAsset pa) {
        if (!hasMarketPrice(pa.getAsset())) {
            return;
        }
        apply(pa, positionCalculator.positionOf(pa));
    }

    /** Todas as posicoes dos ativos informados: depois de atualizar cotacao. */
    @Transactional
    public int refreshAssets(Collection<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return 0;
        }
        return refreshPositions(portfolioAssetRepository.findAllByAssetIdIn(assetIds));
    }

    /** Todas as posicoes com cotacao, de todas as carteiras. Roda na subida. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void refreshAllOnStartup() {
        int updated = refreshPositions(portfolioAssetRepository.findAllWithAsset());
        log.info("Valorizacao regravada na subida: {} posicoes alteradas", updated);
    }

    /**
     * Agrupa por carteira para buscar as transacoes de cada uma numa consulta
     * so, em vez de uma por posicao.
     */
    private int refreshPositions(List<PortfolioAsset> positions) {
        Map<UUID, List<PortfolioAsset>> byPortfolio = positions.stream()
                .filter(pa -> hasMarketPrice(pa.getAsset()))
                .collect(Collectors.groupingBy(pa -> pa.getPortfolio().getId()));

        int updated = 0;
        for (Map.Entry<UUID, List<PortfolioAsset>> entry : byPortfolio.entrySet()) {
            Map<UUID, Position> calculated = positionCalculator.positionsOf(entry.getKey(), entry.getValue());
            for (PortfolioAsset pa : entry.getValue()) {
                if (apply(pa, calculated.get(pa.getId()))) {
                    updated++;
                }
            }
        }
        return updated;
    }

    /** Grava so se mudou. Devolve se gravou. */
    private boolean apply(PortfolioAsset pa, Position position) {
        BigDecimal shares = position.totalShares() != null ? position.totalShares() : BigDecimal.ZERO;
        BigDecimal cost = position.totalInvested() != null ? position.totalInvested() : BigDecimal.ZERO;

        BigDecimal appreciation = pa.getAsset().getCurrentPrice()
                .multiply(shares)
                .subtract(cost)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (pa.getAppreciation() != null && pa.getAppreciation().compareTo(appreciation) == 0) {
            return false;
        }
        pa.setAppreciation(appreciation);
        portfolioAssetRepository.save(pa);
        return true;
    }

    /** Ativo com cotacao de mercado. Mesmo criterio que a tela usa para o preco atual. */
    public static boolean hasMarketPrice(Asset asset) {
        BigDecimal price = asset != null ? asset.getCurrentPrice() : null;
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
}
