package com.primevest.domain.portfolio;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.PriceSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Quais ativos estao em carteira hoje — de qualquer usuario.
 *
 * <p>As rotinas externas trabalham so com eles (PLANO_BRAPI.md, F2): o plano
 * gratuito da BRAPI da 15.000 requisicoes por mes, 1 ativo por chamada, e
 * varrer o catalogo inteiro (mais de 2.000 ativos) todo dia nao cabe. Com ~23
 * ativos em carteira a rotina de fechamento gasta ~506 requisicoes por mes.</p>
 *
 * <p>"Em carteira" e ter cotas hoje, pela mesma regra da posicao
 * ({@link PositionCalculator}) — e nao so existir a linha em portfolio_assets,
 * que continua la depois da venda total.</p>
 */
@Component
@RequiredArgsConstructor
public class OpenPositions {

    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PositionCalculator positionCalculator;

    /** Ativos com cotas hoje e cotacao pelo provedor informado, em ordem de ticker. */
    @Transactional(readOnly = true)
    public List<Asset> assetsWithShares(PriceSource source) {
        Map<UUID, List<PortfolioAsset>> byPortfolio = portfolioAssetRepository.findAllWithAsset().stream()
                .filter(pa -> pa.getAsset().getAssetType() != null
                        && pa.getAsset().getAssetType().getPriceSource() == source)
                .collect(Collectors.groupingBy(pa -> pa.getPortfolio().getId()));

        Map<Long, Asset> assets = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<PortfolioAsset>> entry : byPortfolio.entrySet()) {
            Map<UUID, Position> positions = positionCalculator.positionsOf(entry.getKey(), entry.getValue());
            for (PortfolioAsset pa : entry.getValue()) {
                Position p = positions.get(pa.getId());
                if (p != null && p.shares().signum() > 0) {
                    assets.putIfAbsent(pa.getAsset().getId(), pa.getAsset());
                }
            }
        }
        return assets.values().stream().sorted(Comparator.comparing(Asset::getTicker)).toList();
    }

    /** Ativos que tem ou ja tiveram posicao (qualquer transacao), de qualquer usuario. */
    @Transactional(readOnly = true)
    public List<Asset> assetsEverHeld() {
        Map<Long, Asset> assets = new LinkedHashMap<>();
        portfolioAssetRepository.findAllWithAsset().forEach(pa -> {
            // O tipo e lido depois, fora desta transacao: carrega agora.
            if (pa.getAsset().getAssetType() != null) {
                pa.getAsset().getAssetType().getName();
            }
            assets.putIfAbsent(pa.getAsset().getId(), pa.getAsset());
        });
        return assets.values().stream().sorted(Comparator.comparing(Asset::getTicker)).toList();
    }
}
