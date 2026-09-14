package com.primevest.domain.asset;

import com.primevest.domain.asset.dto.AssetCategoryResponseDTO;
import com.primevest.domain.asset.dto.AssetResponseDTO;
import com.primevest.domain.asset.dto.AssetTypeResponseDTO;
import com.primevest.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final AssetCategoryRepository assetCategoryRepository;

    public AssetService(AssetRepository assetRepository, AssetTypeRepository assetTypeRepository, AssetCategoryRepository assetCategoryRepository) {
        this.assetRepository = assetRepository;
        this.assetTypeRepository = assetTypeRepository;
        this.assetCategoryRepository = assetCategoryRepository;
    }

    /**
     * Gets all assets.
     */
    public List<AssetResponseDTO> getAllAssets() {
        return assetRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Gets assets by type.
     */
    public List<AssetResponseDTO> getAssetsByType(Long typeId) {
        return assetRepository.findByAssetTypeId(typeId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Gets asset by ticker.
     */
    public AssetResponseDTO getAssetByTicker(String ticker) {
        Asset asset = assetRepository.findByTicker(ticker)
            .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        return toDTO(asset);
    }

    /**
     * Gets all asset types.
     */
    public List<AssetTypeResponseDTO> getAllTypes() {
        return assetTypeRepository.findAll().stream()
            .map(t -> new AssetTypeResponseDTO(t.getId(), t.getName(), t.getDescription(), t.getColorHex(), t.isFixedIncome()))
            .collect(Collectors.toList());
    }

    /**
     * Gets categories by type.
     */
    public List<AssetCategoryResponseDTO> getCategoriesByType(Long typeId) {
        return assetCategoryRepository.findByAssetTypeId(typeId).stream()
            .map(c -> new AssetCategoryResponseDTO(c.getId(), c.getName(), c.getDescription(), c.getAssetType().getId(), c.getAssetType().getDescription() != null ? c.getAssetType().getDescription() : c.getAssetType().getName()))
            .collect(Collectors.toList());
    }

    /**
     * Searches assets by query.
     */
    public List<AssetResponseDTO> searchAssets(String query) {
        return searchAssets(query, null);
    }

    /**
     * Busca por texto, opcionalmente restrita a um tipo de ativo.
     * O autocomplete do cadastro de transação usa a forma com typeId: o tipo
     * escolhido determina a lista, e não o contrário.
     */
    public List<AssetResponseDTO> searchAssets(String query, Long typeId) {
        List<Asset> found = typeId != null
            ? assetRepository.searchByTypeAndTickerOrName(typeId, query)
            : assetRepository.searchByTickerOrName(query);
        return found.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Updates asset price.
     */
    @Transactional
    public void updateAssetPrice(Long assetId, BigDecimal newPrice) {
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        asset.setCurrentPrice(newPrice);
        asset.setPriceUpdatedAt(LocalDateTime.now());
        assetRepository.save(asset);
    }

    private AssetResponseDTO toDTO(Asset asset) {
        return new AssetResponseDTO(
            asset.getId(),
            asset.getTicker(),
            asset.getName(),
            asset.getAssetType() != null ? asset.getAssetType().getId() : null,
            asset.getAssetType() != null ? (asset.getAssetType().getDescription() != null ? asset.getAssetType().getDescription() : asset.getAssetType().getName()) : null,
            asset.getAssetCategory() != null ? asset.getAssetCategory().getId() : null,
            asset.getAssetCategory() != null ? asset.getAssetCategory().getName() : null,
            asset.getSector(),
            asset.getCurrentPrice(),
            asset.getPriceUpdatedAt(),
            asset.getMaturityDate(),
            asset.getRateType(),
            asset.getIndexer(),
            asset.getIndexerRate(),
            asset.isActive()
        );
    }
}
