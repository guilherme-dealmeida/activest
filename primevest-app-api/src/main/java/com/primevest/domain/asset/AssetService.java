package com.primevest.domain.asset;

import com.primevest.domain.asset.dto.AssetCategoryResponseDTO;
import com.primevest.domain.asset.dto.AssetResponseDTO;
import com.primevest.domain.asset.dto.AssetTypeResponseDTO;
import com.primevest.domain.portfolio.AppreciationUpdater;
import com.primevest.exception.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final AppreciationUpdater appreciationUpdater;

    public AssetService(AssetRepository assetRepository, AssetTypeRepository assetTypeRepository,
                        AssetCategoryRepository assetCategoryRepository, AppreciationUpdater appreciationUpdater) {
        this.assetRepository = assetRepository;
        this.assetTypeRepository = assetTypeRepository;
        this.assetCategoryRepository = assetCategoryRepository;
        this.appreciationUpdater = appreciationUpdater;
    }

    /**
     * Gets all assets.
     */
    public List<AssetResponseDTO> getAllAssets() {
        return assetRepository.findAllWithTerms().stream().map(this::toDTO).collect(Collectors.toList());
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
        Asset asset = assetRepository.findByTickerWithTerms(ticker)
            .orElseThrow(() -> new ResourceNotFoundException("Ativo não encontrado"));
        return toDTO(asset);
    }

    /**
     * Gets all asset types.
     */
    public List<AssetTypeResponseDTO> getAllTypes() {
        return assetTypeRepository.findAllByOrderByIdAsc().stream()
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
     * Teto de sugestões devolvidas pela busca.
     *
     * O corte precisa ficar aqui, e não na tela: o front não tem como escolher
     * quais linhas descartar, e era exatamente isso que ele fazia — ficava com
     * as 15 primeiras de uma lista sem ordem nenhuma. Com o limite no
     * repositório, quem decide o que sobra é o ORDER BY da query.
     *
     * 50 cobre com folga qualquer busca útil (a maior família do catálogo, o
     * Tesouro, tem 130 títulos) sem despejar centenas de linhas num dropdown.
     */
    private static final int LIMITE_BUSCA = 50;

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
        // Pageable sem Sort: a ordenação vem do próprio @Query. Passar um Sort
        // aqui faria o Spring Data concatenar um segundo ORDER BY e quebrar.
        Pageable limite = PageRequest.of(0, LIMITE_BUSCA);
        List<Asset> found = typeId != null
            ? assetRepository.searchByTypeAndTickerOrName(typeId, query, limite)
            : assetRepository.searchByTickerOrName(query, limite);
        return found.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** Busca so entre ativos ligados a emissor (tela de emissoras, B3). */
    @Transactional(readOnly = true)
    public List<AssetResponseDTO> searchAssetsWithIssuer(String query) {
        return assetRepository.searchWithIssuerByTickerOrName(query, PageRequest.of(0, LIMITE_BUSCA))
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Updates asset price.
     */
    @Transactional
    public void updateAssetPrice(Long assetId, BigDecimal newPrice) {
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new ResourceNotFoundException("Ativo não encontrado"));
        asset.setCurrentPrice(newPrice);
        asset.setPriceUpdatedAt(LocalDateTime.now());
        assetRepository.save(asset);
        // A valorizacao gravada das posicoes deste ativo depende da cotacao.
        appreciationUpdater.refreshAssets(List.of(assetId));
    }

    private AssetResponseDTO toDTO(Asset asset) {
        // Ativo sem parametros publicos nao tem linha em asset_terms: a
        // ausencia da linha JA e a informacao "nao tem prazo nem indexador".
        AssetTerms terms = asset.getTerms();
        return new AssetResponseDTO(
            asset.getId(),
            asset.getTicker(),
            asset.getName(),
            asset.getAssetType() != null ? asset.getAssetType().getId() : null,
            asset.getAssetType() != null ? (asset.getAssetType().getDescription() != null ? asset.getAssetType().getDescription() : asset.getAssetType().getName()) : null,
            asset.getAssetCategory() != null ? asset.getAssetCategory().getId() : null,
            asset.getAssetCategory() != null ? asset.getAssetCategory().getDisplayName() : null,
            asset.getCurrentPrice(),
            asset.getPriceUpdatedAt(),
            terms != null ? terms.getMaturityDate() : null,
            terms != null ? terms.getRateType()     : null,
            terms != null ? terms.getIndexer()      : null,
            asset.isActive()
        );
    }
}
