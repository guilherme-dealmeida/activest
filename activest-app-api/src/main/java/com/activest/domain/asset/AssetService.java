package com.activest.domain.asset;

import com.activest.domain.asset.dto.AssetCategoryResponseDTO;
import com.activest.domain.asset.dto.AssetResponseDTO;
import com.activest.domain.asset.dto.AssetTypeResponseDTO;
import com.activest.domain.fund.FundCategory;
import com.activest.domain.fund.FundKeys;
import com.activest.domain.fund.InvestmentFund;
import com.activest.domain.fund.InvestmentFundRepository;
import com.activest.domain.portfolio.AppreciationUpdater;
import com.activest.exception.ResourceNotFoundException;
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
    private final InvestmentFundRepository fundRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public AssetService(AssetRepository assetRepository, AssetTypeRepository assetTypeRepository,
                        AssetCategoryRepository assetCategoryRepository, AppreciationUpdater appreciationUpdater,
                        InvestmentFundRepository fundRepository, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.assetRepository = assetRepository;
        this.assetTypeRepository = assetTypeRepository;
        this.assetCategoryRepository = assetCategoryRepository;
        this.appreciationUpdater = appreciationUpdater;
        this.fundRepository = fundRepository;
        this.jdbc = jdbc;
    }

    /** Resultado da busca de fundos: a lista e quantos encerrados também casam (o "Mostrar N encerrados", D11). */
    public record FundSearch(List<AssetResponseDTO> assets, int inactiveMatches) {
    }

    /**
     * Busca de fundo de investimento (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, C11/D11/D25): CNPJ com ou sem máscara,
     * nome curto, social, comercial e anteriores, sem acento, pelo índice pg_trgm de {@code investment_funds.search_text}.
     * Vivos primeiro (todos, sem filtro por exclusivo ou público-alvo — I1); encerrados só com {@code includeInactive}.
     */
    public FundSearch searchFunds(String query, boolean includeInactive) {
        String q = FundKeys.normalize(query);
        if (q.length() < 2) {
            return new FundSearch(List.of(), 0);
        }
        String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String cnpj = FundKeys.cnpj(query);
        List<Long> ids = jdbc.queryForList("""
                SELECT a.id FROM investment_funds f JOIN assets a ON a.id = f.asset_id
                 WHERE f.search_text LIKE ? AND (? OR a.active)
                 ORDER BY a.active DESC,
                          CASE WHEN f.cnpj = ? THEN 0 WHEN f.short_name LIKE ? THEN 1 ELSE 2 END,
                          f.net_worth DESC NULLS LAST, f.short_name
                 LIMIT ?""", Long.class, like, includeInactive, cnpj == null ? "" : cnpj, q + "%", SEARCH_LIMIT);
        Integer inactive = includeInactive ? 0 : jdbc.queryForObject("""
                SELECT COUNT(*) FROM investment_funds f JOIN assets a ON a.id = f.asset_id
                 WHERE f.search_text LIKE ? AND NOT a.active""", Integer.class, like);
        java.util.Map<Long, Asset> byId = assetRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Asset::getId, a -> a));
        java.util.Map<Long, InvestmentFund> funds = fundRepository.findByAssetIdIn(ids).stream()
            .collect(Collectors.toMap(InvestmentFund::getAssetId, f -> f));
        List<AssetResponseDTO> out = ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
            .map(a -> toDTO(a, funds.get(a.getId()))).toList();
        return new FundSearch(out, inactive == null ? 0 : inactive);
    }

    /** O tipo é o dos fundos de investimento (cota da CVM)? */
    public boolean isFundType(Long typeId) {
        return typeId != null && assetTypeRepository.findById(typeId)
            .map(t -> t.getPriceSource() == PriceSource.CVM).orElse(false);
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
            .map(t -> new AssetTypeResponseDTO(t.getId(), t.getName(), t.getDescription(), t.getColorHex(), t.isFixedIncome(),
                t.getDefaultCurrencyCode(), t.getRegion().name()))
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
    private static final int SEARCH_LIMIT = 50;

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
        Pageable limit = PageRequest.of(0, SEARCH_LIMIT);
        List<Asset> found = typeId != null
            ? assetRepository.searchByTypeAndTickerOrName(typeId, query, limit)
            : assetRepository.searchByTickerOrName(query, limit);
        // Sem tipo, fundo de investimento fica de fora: ele tem busca própria (searchFunds) e não tem página (D20).
        return found.stream()
            .filter(a -> typeId != null || a.getAssetType() == null || a.getAssetType().getPriceSource() != PriceSource.CVM)
            .map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Busca so entre ativos ligados a emissor (tela de emissoras, B3). {@code kind} restringe ao tipo de emissor
     * (COMPANY = acoes, no Preco Teto — 18/10/2026); nulo, acoes e FIIs.
     */
    @Transactional(readOnly = true)
    public List<AssetResponseDTO> searchAssetsWithIssuer(String query, com.activest.domain.issuer.IssuerKind kind) {
        java.util.List<com.activest.domain.issuer.IssuerKind> kinds = kind == null
            ? java.util.List.of(com.activest.domain.issuer.IssuerKind.values()) : java.util.List.of(kind);
        return assetRepository.searchWithIssuerByTickerOrName(query, kinds, PageRequest.of(0, SEARCH_LIMIT))
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
        InvestmentFund fund = asset.getAssetType() != null && asset.getAssetType().getPriceSource() == PriceSource.CVM
            ? fundRepository.findById(asset.getId()).orElse(null) : null;
        return toDTO(asset, fund);
    }

    private AssetResponseDTO toDTO(Asset asset, InvestmentFund fund) {
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
            asset.isActive(),
            asset.getCurrencyCode(),
            fund != null ? fund.getShortName() : asset.getTicker(),
            fund == null ? null : new AssetResponseDTO.FundInfo(FundKeys.format(fund.getCnpj()), fund.getSubclassId(),
                fund.getAnbimaClassification(), fund.getTargetAudience(), fund.getExclusive(),
                asset.getAssetCategory() != null && FundCategory.PREVIDENCIA.equals(asset.getAssetCategory().getName()),
                fund.getCvmStatus(), fund.getLastQuoteOn())
        );
    }
}
