package com.activest.domain.transaction;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetLogos;
import com.activest.domain.corporateaction.AssetStockEvent;
import com.activest.domain.corporateaction.AssetStockEventRepository;
import com.activest.domain.corporateaction.StockEventType;
import com.activest.domain.portfolio.AppliedStockEvent;
import com.activest.domain.portfolio.Portfolio;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.PortfolioAssetRepository;
import com.activest.domain.portfolio.PortfolioRepository;
import com.activest.domain.portfolio.PositionCalculator;
import com.activest.domain.transaction.dto.StockEventEntryDTO;
import com.activest.domain.transaction.dto.StockEventEntryDTO.TaxCostOrigin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Os eventos do papel que mexeram nas posições do usuário, como linhas do extrato de Transações
 * (docs/planos/PLANO_EVENTOS_TRANSACOES.md).
 *
 * <p>Nada é gravado (D1): as linhas saem de {@link PositionCalculator#appliedStockEventsOf}, o mesmo percurso que
 * calcula a Carteira, com as mesmas consultas em lote. Compra retroativa, exclusão, correção do catálogo e custo
 * informado aparecem sozinhos.</p>
 */
@Service
@Transactional(readOnly = true)
public class StockEventEntryService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final AssetStockEventRepository stockEventRepository;
    private final PositionCalculator positionCalculator;
    private final AssetLogos assetLogos;

    public StockEventEntryService(PortfolioRepository portfolioRepository,
                                  PortfolioAssetRepository portfolioAssetRepository,
                                  AssetStockEventRepository stockEventRepository,
                                  PositionCalculator positionCalculator,
                                  AssetLogos assetLogos) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.stockEventRepository = stockEventRepository;
        this.positionCalculator = positionCalculator;
        this.assetLogos = assetLogos;
    }

    /** Linhas de evento do usuário, da data ex mais recente para a mais antiga. */
    public List<StockEventEntryDTO> list(UUID userId) {
        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        if (portfolio == null) {
            return List.of();
        }
        List<PortfolioAsset> positions = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());
        Map<UUID, List<AppliedStockEvent>> applied = positionCalculator.appliedStockEventsOf(portfolio.getId(), positions);
        if (applied.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = applied.values().stream().flatMap(List::stream)
            .map(AppliedStockEvent::eventId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, AssetStockEvent> events = stockEventRepository.findAllById(eventIds).stream()
            .collect(Collectors.toMap(AssetStockEvent::getId, Function.identity()));
        Map<UUID, PortfolioAsset> byId = positions.stream()
            .collect(Collectors.toMap(PortfolioAsset::getId, Function.identity()));

        List<StockEventEntryDTO> out = new ArrayList<>();
        applied.forEach((positionId, list) -> {
            PortfolioAsset pa = byId.get(positionId);
            for (AppliedStockEvent a : list) {
                out.add(toDTO(pa, a, a.eventId() != null ? events.get(a.eventId()) : null));
            }
        });
        out.sort(Comparator.comparing(StockEventEntryDTO::exDate).reversed()
            .thenComparing(StockEventEntryDTO::ticker));
        return out;
    }

    private StockEventEntryDTO toDTO(PortfolioAsset pa, AppliedStockEvent a, AssetStockEvent event) {
        Asset asset = pa.getAsset();
        var assetType = asset.getAssetType();
        var category = asset.getAssetCategory();
        TaxCostOrigin origin = null;
        if (a.type() == StockEventType.BONIFICACAO) {
            if (event != null && event.getAssignedCost() != null) {
                origin = TaxCostOrigin.CATALOG;
            } else if (a.taxUnitCost() != null) {
                origin = TaxCostOrigin.USER;
            } else {
                origin = TaxCostOrigin.MISSING;
            }
        }
        return new StockEventEntryDTO(
            pa.getId(),
            a.eventId(),
            asset.getTicker(),
            asset.getName(),
            assetType != null ? assetType.getId() : null,
            assetType != null ? (assetType.getDescription() != null ? assetType.getDescription() : assetType.getName()) : "",
            category != null ? category.getId() : null,
            category != null ? category.getDisplayName() : "",
            assetLogos.logoFlag(asset),
            asset.getIssuer() != null ? asset.getIssuer().getId() : null,
            a.type(),
            a.factor(),
            a.type().quantityRatio(a.factor()),
            a.dataCom(),
            a.exDate(),
            event != null ? event.getSource() : null,
            a.sharesBefore(),
            a.sharesAfter(),
            a.sharesDelta(),
            a.fractionShares(),
            a.costDelta().setScale(2, RoundingMode.HALF_UP),
            a.taxUnitCost(),
            a.taxCostDelta() != null ? a.taxCostDelta().setScale(2, RoundingMode.HALF_UP) : null,
            origin);
    }
}
