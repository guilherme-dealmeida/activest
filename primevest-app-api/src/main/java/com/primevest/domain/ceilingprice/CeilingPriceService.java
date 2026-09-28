package com.primevest.domain.ceilingprice;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.exception.ResourceNotFoundException;
import com.primevest.domain.ceilingprice.dto.CeilingPriceCreateDTO;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTO;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CeilingPriceService {

    private final CeilingPriceRepository ceilingPriceRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    /**
     * Gets ceiling prices for the user.
     */
    public List<CeilingPriceDTO> getCeilingPrices(UUID userId) {
        return ceilingPriceRepository.findByUserId(userId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Creates ceiling price.
     */
    @Transactional
    public CeilingPriceDTO createCeilingPrice(UUID userId, CeilingPriceCreateDTO dto) {
        Asset asset = assetRepository.findByTicker(dto.ticker())
            .orElseThrow(() -> new RuntimeException("Asset not found: " + dto.ticker()));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        CeilingPrice cp = CeilingPrice.builder()
            .user(user)
            .asset(asset)
            .maxPrice(dto.maxPrice())
            .alertEnabled(dto.alertEnabled())
            .build();

        cp = ceilingPriceRepository.save(cp);
        return toDTO(cp);
    }

    /**
     * Updates ceiling price.
     */
    @Transactional
    public CeilingPriceDTO updateCeilingPrice(UUID userId, UUID id, CeilingPriceCreateDTO dto) {
        CeilingPrice cp = findOwnedCeilingPrice(userId, id);

        if (dto.ticker() != null) {
            Asset asset = assetRepository.findByTicker(dto.ticker())
                .orElseThrow(() -> new RuntimeException("Asset not found: " + dto.ticker()));
            cp.setAsset(asset);
        }
        cp.setMaxPrice(dto.maxPrice());
        cp.setAlertEnabled(dto.alertEnabled());
        cp = ceilingPriceRepository.save(cp);
        return toDTO(cp);
    }

    /**
     * Deletes ceiling price.
     */
    @Transactional
    public void deleteCeilingPrice(UUID userId, UUID id) {
        ceilingPriceRepository.delete(findOwnedCeilingPrice(userId, id));
    }

    /**
     * Carrega o preco teto garantindo que ele e do usuario.
     *
     * Registro de outro dono responde "nao encontrado" de proposito: dizer "sem
     * permissao" confirmaria que o id existe. Mesmo criterio de
     * GoalService.findOwnedGoal.
     *
     * A listagem ja era escopada por findByUserId; eram as escritas que
     * aceitavam qualquer id — e o delete era um deleteById solto.
     */
    private CeilingPrice findOwnedCeilingPrice(UUID userId, UUID id) {
        return ceilingPriceRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Preço teto não encontrado"));
    }

    private CeilingPriceDTO toDTO(CeilingPrice cp) {
        BigDecimal currentPrice = cp.getAsset().getCurrentPrice() != null
            ? cp.getAsset().getCurrentPrice() : BigDecimal.ZERO;
        BigDecimal maxPrice = cp.getMaxPrice();

        BigDecimal diffPct = BigDecimal.ZERO;
        if (maxPrice.compareTo(BigDecimal.ZERO) > 0) {
            diffPct = currentPrice.subtract(maxPrice)
                .divide(maxPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        boolean belowCeiling = currentPrice.compareTo(maxPrice) <= 0;

        // Descrição amigável do tipo (convenção do projeto: getDescription para
        // exibição, getName só para lógica interna), com fallback para o name.
        var assetType = cp.getAsset().getAssetType();
        String assetTypeName = assetType == null ? ""
            : (assetType.getDescription() != null ? assetType.getDescription() : assetType.getName());

        return new CeilingPriceDTO(
            cp.getId(),
            cp.getAsset().getTicker(),
            cp.getAsset().getName(),
            assetTypeName,
            maxPrice,
            currentPrice,
            diffPct,
            cp.isAlertEnabled(),
            belowCeiling
        );
    }
}
