import os

base_dir = r"C:\Users\guilh\.gemini\antigravity\scratch\primevest\primevest-api\src\main\java\com\primevest"

files = {
    "config/SecurityUtils.java": """package com.primevest.config;

import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

public class SecurityUtils {
    public static UUID getCurrentUserId() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return UUID.fromString(name);
    }
}
""",
    "domain/user/dto/UserResponseDTO.java": """package com.primevest.domain.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDTO(
    UUID id,
    String name,
    String email,
    String avatarUrl,
    LocalDateTime createdAt
) {}
""",
    "domain/user/dto/UserUpdateDTO.java": """package com.primevest.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserUpdateDTO(
    @NotBlank String name,
    String avatarUrl
) {}
""",
    "domain/user/UserService.java": """package com.primevest.domain.user;

import com.primevest.domain.user.dto.UserResponseDTO;
import com.primevest.domain.user.dto.UserUpdateDTO;
import com.primevest.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Gets user by ID.
     */
    public UserResponseDTO getUserById(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toDTO(user);
    }

    /**
     * Updates a user.
     */
    @Transactional
    public UserResponseDTO updateUser(UUID userId, UserUpdateDTO dto) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setName(dto.name());
        user.setAvatarUrl(dto.avatarUrl());
        
        return toDTO(userRepository.save(user));
    }

    private UserResponseDTO toDTO(User user) {
        return new UserResponseDTO(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.getCreatedAt()
        );
    }
}
""",
    "domain/user/UserController.java": """package com.primevest.domain.user;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.user.dto.UserResponseDTO;
import com.primevest.domain.user.dto.UserUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser() {
        return ResponseEntity.ok(userService.getUserById(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateCurrentUser(@Valid @RequestBody UserUpdateDTO dto) {
        return ResponseEntity.ok(userService.updateUser(SecurityUtils.getCurrentUserId(), dto));
    }
}
""",
    "domain/asset/dto/AssetResponseDTO.java": """package com.primevest.domain.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssetResponseDTO(
    Long id,
    String ticker,
    String name,
    String typeName,
    String categoryName,
    String sector,
    BigDecimal currentPrice,
    LocalDateTime priceUpdatedAt,
    LocalDate maturityDate,
    String indexer,
    BigDecimal indexerRate,
    boolean active
) {}
""",
    "domain/asset/dto/AssetTypeResponseDTO.java": """package com.primevest.domain.asset.dto;

public record AssetTypeResponseDTO(
    Long id,
    String name,
    String description,
    String colorHex
) {}
""",
    "domain/asset/dto/AssetCategoryResponseDTO.java": """package com.primevest.domain.asset.dto;

public record AssetCategoryResponseDTO(
    Long id,
    String name,
    String description,
    Long assetTypeId,
    String assetTypeName
) {}
""",
    "domain/asset/AssetService.java": """package com.primevest.domain.asset;

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
            .map(t -> new AssetTypeResponseDTO(t.getId(), t.getName(), t.getDescription(), t.getColorHex()))
            .collect(Collectors.toList());
    }

    /**
     * Gets categories by type.
     */
    public List<AssetCategoryResponseDTO> getCategoriesByType(Long typeId) {
        return assetCategoryRepository.findByAssetTypeId(typeId).stream()
            .map(c -> new AssetCategoryResponseDTO(c.getId(), c.getName(), c.getDescription(), c.getAssetType().getId(), c.getAssetType().getName()))
            .collect(Collectors.toList());
    }

    /**
     * Searches assets by query.
     */
    public List<AssetResponseDTO> searchAssets(String query) {
        return assetRepository.search(query).stream().map(this::toDTO).collect(Collectors.toList());
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
            asset.getAssetType() != null ? asset.getAssetType().getName() : null,
            asset.getAssetCategory() != null ? asset.getAssetCategory().getName() : null,
            asset.getSector(),
            asset.getCurrentPrice(),
            asset.getPriceUpdatedAt(),
            asset.getMaturityDate(),
            asset.getIndexer(),
            asset.getIndexerRate(),
            asset.isActive()
        );
    }
}
""",
    "domain/asset/AssetController.java": """package com.primevest.domain.asset;

import com.primevest.domain.asset.dto.AssetCategoryResponseDTO;
import com.primevest.domain.asset.dto.AssetResponseDTO;
import com.primevest.domain.asset.dto.AssetTypeResponseDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/")
    public ResponseEntity<List<AssetResponseDTO>> listAssets(
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) String query) {
        if (query != null && !query.isEmpty()) {
            return ResponseEntity.ok(assetService.searchAssets(query));
        } else if (typeId != null) {
            return ResponseEntity.ok(assetService.getAssetsByType(typeId));
        }
        return ResponseEntity.ok(assetService.getAllAssets());
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<AssetResponseDTO> getByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(assetService.getAssetByTicker(ticker));
    }

    @GetMapping("/types")
    public ResponseEntity<List<AssetTypeResponseDTO>> listTypes() {
        return ResponseEntity.ok(assetService.getAllTypes());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<AssetCategoryResponseDTO>> listCategories(@RequestParam(required = false) Long typeId) {
        if (typeId != null) {
            return ResponseEntity.ok(assetService.getCategoriesByType(typeId));
        }
        return ResponseEntity.ok(List.of()); // Or all categories if supported
    }

    @GetMapping("/{ticker}/history")
    public ResponseEntity<Object> getPriceHistory(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        // Handled by pricehistory module, would call AssetPriceHistoryService here or redirect
        return ResponseEntity.ok().build();
    }
}
""",
    "domain/portfolio/dto/PortfolioSummaryDTO.java": """package com.primevest.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioSummaryDTO(
    UUID id,
    String name,
    BigDecimal totalInvested,
    BigDecimal currentValue,
    BigDecimal totalDividends,
    BigDecimal totalReturn,
    BigDecimal returnPercentage
) {}
""",
    "domain/portfolio/dto/PortfolioAssetDetailDTO.java": """package com.primevest.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioAssetDetailDTO(
    UUID id,
    String ticker,
    String assetName,
    String typeName,
    String categoryName,
    BigDecimal totalShares,
    BigDecimal averagePrice,
    BigDecimal totalInvested,
    BigDecimal currentValue,
    BigDecimal currentPrice,
    BigDecimal totalDividends,
    BigDecimal valorization,
    BigDecimal returnPercentage,
    boolean isReservation
) {}
""",
    "domain/portfolio/dto/PortfolioAllocationDTO.java": """package com.primevest.domain.portfolio.dto;

import java.math.BigDecimal;

public record PortfolioAllocationDTO(
    String typeName,
    String colorHex,
    BigDecimal value,
    BigDecimal percentage
) {}
""",
    "domain/portfolio/dto/PortfolioAllocationDetailedDTO.java": """package com.primevest.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioAllocationDetailedDTO(
    String typeName,
    String colorHex,
    BigDecimal totalValue,
    BigDecimal percentage,
    List<CategoryAllocation> categories
) {
    public record CategoryAllocation(
        String categoryName,
        BigDecimal value,
        BigDecimal percentage
    ) {}
}
""",
    "domain/portfolio/dto/PortfolioPerformanceDTO.java": """package com.primevest.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioPerformanceDTO(
    BigDecimal totalInvested,
    BigDecimal currentValue,
    BigDecimal totalDividends,
    BigDecimal totalReturn,
    BigDecimal returnPercentage,
    List<AssetPerformance> assets
) {
    public record AssetPerformance(
        String ticker,
        BigDecimal invested,
        BigDecimal currentValue,
        BigDecimal returnPct
    ) {}
}
""",
    "domain/portfolio/PortfolioService.java": """package com.primevest.domain.portfolio;

import com.primevest.domain.portfolio.dto.*;
import com.primevest.domain.portfolio.dto.PortfolioAllocationDetailedDTO.CategoryAllocation;
import com.primevest.domain.portfolio.dto.PortfolioPerformanceDTO.AssetPerformance;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final UserRepository userRepository;

    public PortfolioService(PortfolioRepository portfolioRepository, PortfolioAssetRepository portfolioAssetRepository, UserRepository userRepository) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.userRepository = userRepository;
    }

    /**
     * Gets portfolio summary.
     */
    public PortfolioSummaryDTO getPortfolioSummary(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        
        BigDecimal totalReturn = portfolio.getCurrentValue().add(portfolio.getTotalDividends()).subtract(portfolio.getTotalInvested());
        BigDecimal returnPercentage = BigDecimal.ZERO;
        
        if (portfolio.getTotalInvested().compareTo(BigDecimal.ZERO) > 0) {
            returnPercentage = totalReturn.divide(portfolio.getTotalInvested(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }

        return new PortfolioSummaryDTO(
            portfolio.getId(),
            portfolio.getName(),
            portfolio.getTotalInvested(),
            portfolio.getCurrentValue(),
            portfolio.getTotalDividends(),
            totalReturn,
            returnPercentage
        );
    }

    /**
     * Gets portfolio assets.
     */
    public List<PortfolioAssetDetailDTO> getPortfolioAssets(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        return portfolioAssetRepository.findByPortfolioId(portfolio.getId()).stream().map(pa -> {
            BigDecimal currentPrice = pa.getAsset().getCurrentPrice() != null ? pa.getAsset().getCurrentPrice() : BigDecimal.ZERO;
            BigDecimal currentValue = currentPrice.multiply(pa.getTotalShares());
            BigDecimal valorization = currentValue.subtract(pa.getAveragePrice().multiply(pa.getTotalShares()));
            
            BigDecimal returnPct = BigDecimal.ZERO;
            if (pa.getAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
                returnPct = currentPrice.divide(pa.getAveragePrice(), 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(new BigDecimal("100"));
            }

            return new PortfolioAssetDetailDTO(
                pa.getId(),
                pa.getAsset().getTicker(),
                pa.getAsset().getName(),
                pa.getAsset().getAssetType().getName(),
                pa.getAsset().getAssetCategory() != null ? pa.getAsset().getAssetCategory().getName() : null,
                pa.getTotalShares(),
                pa.getAveragePrice(),
                pa.getTotalInvested(),
                currentValue,
                currentPrice,
                pa.getTotalDividends(),
                valorization,
                returnPct,
                pa.isReservation()
            );
        }).collect(Collectors.toList());
    }

    /**
     * Gets portfolio allocation.
     */
    public List<PortfolioAllocationDTO> getAllocation(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        List<PortfolioAsset> assets = portfolioAssetRepository.findByPortfolioId(portfolio.getId());
        
        BigDecimal totalVal = portfolio.getCurrentValue();
        if (totalVal.compareTo(BigDecimal.ZERO) == 0) return List.of();

        Map<String, BigDecimal> typeValues = new HashMap<>();
        Map<String, String> typeColors = new HashMap<>();

        for (PortfolioAsset pa : assets) {
            String typeName = pa.getAsset().getAssetType().getName();
            typeColors.put(typeName, pa.getAsset().getAssetType().getColorHex());
            BigDecimal val = pa.getAsset().getCurrentPrice().multiply(pa.getTotalShares());
            typeValues.merge(typeName, val, BigDecimal::add);
        }

        return typeValues.entrySet().stream().map(e -> {
            BigDecimal pct = e.getValue().divide(totalVal, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            return new PortfolioAllocationDTO(e.getKey(), typeColors.get(e.getKey()), e.getValue(), pct);
        }).collect(Collectors.toList());
    }

    /**
     * Gets detailed allocation.
     */
    public List<PortfolioAllocationDetailedDTO> getAllocationDetailed(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        List<PortfolioAsset> assets = portfolioAssetRepository.findByPortfolioId(portfolio.getId());
        
        BigDecimal totalVal = portfolio.getCurrentValue();
        if (totalVal.compareTo(BigDecimal.ZERO) == 0) return List.of();

        // Complex grouping omitted for brevity, simple return for now
        return List.of();
    }

    /**
     * Gets performance.
     */
    public PortfolioPerformanceDTO getPerformance(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        PortfolioSummaryDTO summary = getPortfolioSummary(userId);
        
        List<AssetPerformance> assetPerfs = portfolioAssetRepository.findByPortfolioId(portfolio.getId()).stream().map(pa -> {
            BigDecimal currentPrice = pa.getAsset().getCurrentPrice() != null ? pa.getAsset().getCurrentPrice() : BigDecimal.ZERO;
            BigDecimal currentValue = currentPrice.multiply(pa.getTotalShares());
            BigDecimal returnPct = BigDecimal.ZERO;
            if (pa.getAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
                returnPct = currentPrice.divide(pa.getAveragePrice(), 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(new BigDecimal("100"));
            }
            return new AssetPerformance(pa.getAsset().getTicker(), pa.getTotalInvested(), currentValue, returnPct);
        }).collect(Collectors.toList());

        return new PortfolioPerformanceDTO(
            summary.totalInvested(), summary.currentValue(), summary.totalDividends(),
            summary.totalReturn(), summary.returnPercentage(), assetPerfs
        );
    }

    /**
     * Helper to get or create portfolio.
     */
    @Transactional
    public Portfolio getOrCreatePortfolio(UUID userId) {
        return portfolioRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId).orElseThrow();
            Portfolio p = new Portfolio();
            p.setUser(user);
            p.setName("Main Portfolio");
            p.setTotalInvested(BigDecimal.ZERO);
            p.setCurrentValue(BigDecimal.ZERO);
            p.setTotalDividends(BigDecimal.ZERO);
            return portfolioRepository.save(p);
        });
    }

    /**
     * Recalculates portfolio.
     */
    @Transactional
    public void recalculatePortfolio(UUID portfolioId) {
        Portfolio p = portfolioRepository.findById(portfolioId).orElseThrow();
        // Recalculation logic based on transactions
    }
}
""",
    "domain/portfolio/PortfolioController.java": """package com.primevest.domain.portfolio;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.portfolio.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/")
    public ResponseEntity<PortfolioSummaryDTO> getSummary() {
        return ResponseEntity.ok(portfolioService.getPortfolioSummary(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/assets")
    public ResponseEntity<List<PortfolioAssetDetailDTO>> getAssets() {
        return ResponseEntity.ok(portfolioService.getPortfolioAssets(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/allocation")
    public ResponseEntity<List<PortfolioAllocationDTO>> getAllocation() {
        return ResponseEntity.ok(portfolioService.getAllocation(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/allocation/detailed")
    public ResponseEntity<List<PortfolioAllocationDetailedDTO>> getAllocationDetailed() {
        return ResponseEntity.ok(portfolioService.getAllocationDetailed(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/performance")
    public ResponseEntity<PortfolioPerformanceDTO> getPerformance() {
        return ResponseEntity.ok(portfolioService.getPerformance(SecurityUtils.getCurrentUserId()));
    }
    
    @GetMapping("/dividends")
    public ResponseEntity<Object> getDividends() {
        return ResponseEntity.ok().build();
    }
}
""",
    "domain/transaction/dto/TransactionCreateDTO.java": """package com.primevest.domain.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.primevest.domain.transaction.TransactionType;

public record TransactionCreateDTO(
    @NotBlank String ticker,
    @NotNull TransactionType type,
    @NotNull @Positive BigDecimal shares,
    @NotNull @Positive BigDecimal pricePerShare,
    BigDecimal brokerageFee,
    @NotNull LocalDate transactionDate,
    Boolean isReservation
) {}
""",
    "domain/transaction/dto/TransactionResponseDTO.java": """package com.primevest.domain.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.primevest.domain.transaction.TransactionType;

public record TransactionResponseDTO(
    UUID id,
    String ticker,
    String assetName,
    TransactionType type,
    BigDecimal shares,
    BigDecimal pricePerShare,
    BigDecimal totalAmount,
    BigDecimal brokerageFee,
    LocalDate transactionDate,
    LocalDateTime createdAt
) {}
""",
    "domain/transaction/TransactionService.java": """package com.primevest.domain.transaction;

import com.primevest.domain.transaction.dto.TransactionCreateDTO;
import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import com.primevest.domain.portfolio.PortfolioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PortfolioService portfolioService;

    public TransactionService(TransactionRepository transactionRepository, PortfolioService portfolioService) {
        this.transactionRepository = transactionRepository;
        this.portfolioService = portfolioService;
    }

    /**
     * Creates a transaction.
     */
    @Transactional
    public TransactionResponseDTO createTransaction(UUID userId, TransactionCreateDTO dto) {
        // Logic to create transaction
        return null;
    }

    /**
     * Gets user transactions.
     */
    public List<TransactionResponseDTO> getTransactions(UUID userId) {
        return List.of();
    }

    /**
     * Gets transaction by ID.
     */
    public TransactionResponseDTO getTransactionById(UUID userId, UUID txId) {
        return null;
    }

    /**
     * Deletes a transaction.
     */
    @Transactional
    public void deleteTransaction(UUID userId, UUID txId) {
    }
}
""",
    "domain/transaction/TransactionController.java": """package com.primevest.domain.transaction;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.transaction.dto.TransactionCreateDTO;
import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/")
    public ResponseEntity<TransactionResponseDTO> createTransaction(@Valid @RequestBody TransactionCreateDTO dto) {
        return ResponseEntity.ok(transactionService.createTransaction(SecurityUtils.getCurrentUserId(), dto));
    }

    @GetMapping("/")
    public ResponseEntity<List<TransactionResponseDTO>> getTransactions() {
        return ResponseEntity.ok(transactionService.getTransactions(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDTO> getTransactionById(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getTransactionById(SecurityUtils.getCurrentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {
        transactionService.deleteTransaction(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
""",
    "domain/strategy/dto/StrategyDTO.java": """package com.primevest.domain.strategy.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StrategyDTO(
    UUID id,
    Long assetTypeId,
    String assetTypeName,
    BigDecimal targetPercentage,
    boolean includeReservation
) {}
""",
    "domain/strategy/dto/StrategyUpdateDTO.java": """package com.primevest.domain.strategy.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.List;

public record StrategyUpdateDTO(
    List<StrategyItemDTO> items
) {
    public record StrategyItemDTO(
        Long assetTypeId,
        @DecimalMin("0") @DecimalMax("100") BigDecimal targetPercentage,
        boolean includeReservation
    ) {}
}
""",
    "domain/strategy/dto/RebalanceDTO.java": """package com.primevest.domain.strategy.dto;

import java.math.BigDecimal;
import java.util.List;

public record RebalanceDTO(
    BigDecimal totalPortfolioValue,
    List<RebalanceItem> items
) {
    public record RebalanceItem(
        String typeName,
        BigDecimal idealPercentage,
        BigDecimal currentPercentage,
        BigDecimal diffPercentage,
        BigDecimal currentValue,
        BigDecimal idealValue,
        BigDecimal amountToRebalance
    ) {}
}
""",
    "domain/strategy/StrategyService.java": """package com.primevest.domain.strategy;

import com.primevest.domain.strategy.dto.RebalanceDTO;
import com.primevest.domain.strategy.dto.StrategyDTO;
import com.primevest.domain.strategy.dto.StrategyUpdateDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StrategyService {

    /**
     * Gets user strategy.
     */
    public List<StrategyDTO> getUserStrategy(UUID userId) {
        return List.of();
    }

    /**
     * Updates user strategy.
     */
    @Transactional
    public List<StrategyDTO> updateStrategy(UUID userId, StrategyUpdateDTO dto) {
        return List.of();
    }

    /**
     * Gets rebalance suggestion.
     */
    public RebalanceDTO getRebalanceSuggestion(UUID userId) {
        return null;
    }
}
""",
    "domain/strategy/StrategyController.java": """package com.primevest.domain.strategy;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.strategy.dto.RebalanceDTO;
import com.primevest.domain.strategy.dto.StrategyDTO;
import com.primevest.domain.strategy.dto.StrategyUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/strategy")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping("/")
    public ResponseEntity<List<StrategyDTO>> getUserStrategy() {
        return ResponseEntity.ok(strategyService.getUserStrategy(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/")
    public ResponseEntity<List<StrategyDTO>> updateStrategy(@Valid @RequestBody StrategyUpdateDTO dto) {
        return ResponseEntity.ok(strategyService.updateStrategy(SecurityUtils.getCurrentUserId(), dto));
    }

    @GetMapping("/rebalance")
    public ResponseEntity<RebalanceDTO> getRebalanceSuggestion() {
        return ResponseEntity.ok(strategyService.getRebalanceSuggestion(SecurityUtils.getCurrentUserId()));
    }
}
""",
    "domain/ceilingprice/dto/CeilingPriceDTO.java": """package com.primevest.domain.ceilingprice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CeilingPriceDTO(
    UUID id,
    String ticker,
    String assetName,
    BigDecimal maxPrice,
    BigDecimal currentPrice,
    BigDecimal diffPercentage,
    boolean alertEnabled,
    boolean belowCeiling
) {}
""",
    "domain/ceilingprice/dto/CeilingPriceCreateDTO.java": """package com.primevest.domain.ceilingprice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CeilingPriceCreateDTO(
    @NotBlank String ticker,
    @NotNull @Positive BigDecimal maxPrice,
    boolean alertEnabled
) {}
""",
    "domain/ceilingprice/CeilingPriceService.java": """package com.primevest.domain.ceilingprice;

import com.primevest.domain.ceilingprice.dto.CeilingPriceCreateDTO;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CeilingPriceService {

    /**
     * Gets ceiling prices.
     */
    public List<CeilingPriceDTO> getCeilingPrices(UUID userId) {
        return List.of();
    }

    /**
     * Creates ceiling price.
     */
    @Transactional
    public CeilingPriceDTO createCeilingPrice(UUID userId, CeilingPriceCreateDTO dto) {
        return null;
    }

    /**
     * Updates ceiling price.
     */
    @Transactional
    public CeilingPriceDTO updateCeilingPrice(UUID userId, UUID id, CeilingPriceCreateDTO dto) {
        return null;
    }

    /**
     * Deletes ceiling price.
     */
    @Transactional
    public void deleteCeilingPrice(UUID userId, UUID id) {
    }
}
""",
    "domain/ceilingprice/CeilingPriceController.java": """package com.primevest.domain.ceilingprice;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.ceilingprice.dto.CeilingPriceCreateDTO;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ceiling-prices")
public class CeilingPriceController {

    private final CeilingPriceService ceilingPriceService;

    public CeilingPriceController(CeilingPriceService ceilingPriceService) {
        this.ceilingPriceService = ceilingPriceService;
    }

    @GetMapping("/")
    public ResponseEntity<List<CeilingPriceDTO>> list() {
        return ResponseEntity.ok(ceilingPriceService.getCeilingPrices(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/")
    public ResponseEntity<CeilingPriceDTO> create(@Valid @RequestBody CeilingPriceCreateDTO dto) {
        return ResponseEntity.ok(ceilingPriceService.createCeilingPrice(SecurityUtils.getCurrentUserId(), dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CeilingPriceDTO> update(@PathVariable UUID id, @Valid @RequestBody CeilingPriceCreateDTO dto) {
        return ResponseEntity.ok(ceilingPriceService.updateCeilingPrice(SecurityUtils.getCurrentUserId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ceilingPriceService.deleteCeilingPrice(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
""",
    "domain/dividend/dto/DividendResponseDTO.java": """package com.primevest.domain.dividend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.primevest.domain.dividend.DividendType;

public record DividendResponseDTO(
    Long id,
    String ticker,
    String assetName,
    BigDecimal amountPerShare,
    BigDecimal totalAmount,
    LocalDate exDate,
    LocalDate paymentDate,
    DividendType type
) {}
""",
    "domain/dividend/dto/DividendCreateDTO.java": """package com.primevest.domain.dividend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.primevest.domain.dividend.DividendType;

public record DividendCreateDTO(
    @NotBlank String ticker,
    @NotNull BigDecimal amountPerShare,
    @NotNull LocalDate exDate,
    LocalDate paymentDate,
    @NotNull DividendType type
) {}
""",
    "domain/dividend/dto/DividendSummaryDTO.java": """package com.primevest.domain.dividend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DividendSummaryDTO(
    BigDecimal totalDividends,
    Map<String, BigDecimal> byType,
    List<DividendResponseDTO> recent
) {}
""",
    "domain/dividend/DividendService.java": """package com.primevest.domain.dividend;

import com.primevest.domain.dividend.dto.DividendCreateDTO;
import com.primevest.domain.dividend.dto.DividendResponseDTO;
import com.primevest.domain.dividend.dto.DividendSummaryDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DividendService {

    /**
     * Registers a dividend.
     */
    @Transactional
    public DividendResponseDTO registerDividend(UUID userId, DividendCreateDTO dto) {
        return null;
    }

    /**
     * Gets dividend summary.
     */
    public DividendSummaryDTO getDividendSummary(UUID userId) {
        return null;
    }

    /**
     * Gets dividends by asset.
     */
    public List<DividendResponseDTO> getDividendsByAsset(UUID userId, String ticker) {
        return List.of();
    }
}
""",
    "domain/dividend/DividendController.java": """package com.primevest.domain.dividend;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.dividend.dto.DividendCreateDTO;
import com.primevest.domain.dividend.dto.DividendResponseDTO;
import com.primevest.domain.dividend.dto.DividendSummaryDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dividends")
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    @PostMapping("/")
    public ResponseEntity<DividendResponseDTO> registerDividend(@Valid @RequestBody DividendCreateDTO dto) {
        return ResponseEntity.ok(dividendService.registerDividend(SecurityUtils.getCurrentUserId(), dto));
    }

    @GetMapping("/")
    public ResponseEntity<DividendSummaryDTO> summary() {
        return ResponseEntity.ok(dividendService.getDividendSummary(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/asset/{ticker}")
    public ResponseEntity<List<DividendResponseDTO>> dividendsByAsset(@PathVariable String ticker) {
        return ResponseEntity.ok(dividendService.getDividendsByAsset(SecurityUtils.getCurrentUserId(), ticker));
    }
}
""",
    "domain/goal/dto/GoalResponseDTO.java": """package com.primevest.domain.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.primevest.domain.goal.GoalStatus;

public record GoalResponseDTO(
    UUID id,
    String name,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    BigDecimal progressPercentage,
    LocalDate targetDate,
    GoalStatus status,
    LocalDateTime createdAt
) {}
""",
    "domain/goal/dto/GoalCreateDTO.java": """package com.primevest.domain.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalCreateDTO(
    @NotBlank String name,
    @NotNull BigDecimal targetAmount,
    LocalDate targetDate
) {}
""",
    "domain/goal/dto/GoalUpdateDTO.java": """package com.primevest.domain.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.primevest.domain.goal.GoalStatus;

public record GoalUpdateDTO(
    String name,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    LocalDate targetDate,
    GoalStatus status
) {}
""",
    "domain/goal/GoalService.java": """package com.primevest.domain.goal;

import com.primevest.domain.goal.dto.GoalCreateDTO;
import com.primevest.domain.goal.dto.GoalResponseDTO;
import com.primevest.domain.goal.dto.GoalUpdateDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GoalService {

    /**
     * Creates a goal.
     */
    @Transactional
    public GoalResponseDTO createGoal(UUID userId, GoalCreateDTO dto) {
        return null;
    }

    /**
     * Gets goals.
     */
    public List<GoalResponseDTO> getGoals(UUID userId) {
        return List.of();
    }

    /**
     * Updates a goal.
     */
    @Transactional
    public GoalResponseDTO updateGoal(UUID userId, UUID goalId, GoalUpdateDTO dto) {
        return null;
    }

    /**
     * Deletes a goal.
     */
    @Transactional
    public void deleteGoal(UUID userId, UUID goalId) {
    }
}
""",
    "domain/goal/GoalController.java": """package com.primevest.domain.goal;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.goal.dto.GoalCreateDTO;
import com.primevest.domain.goal.dto.GoalResponseDTO;
import com.primevest.domain.goal.dto.GoalUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping("/")
    public ResponseEntity<GoalResponseDTO> create(@Valid @RequestBody GoalCreateDTO dto) {
        return ResponseEntity.ok(goalService.createGoal(SecurityUtils.getCurrentUserId(), dto));
    }

    @GetMapping("/")
    public ResponseEntity<List<GoalResponseDTO>> list() {
        return ResponseEntity.ok(goalService.getGoals(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponseDTO> update(@PathVariable UUID id, @Valid @RequestBody GoalUpdateDTO dto) {
        return ResponseEntity.ok(goalService.updateGoal(SecurityUtils.getCurrentUserId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        goalService.deleteGoal(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
""",
    "domain/pricehistory/dto/PriceHistoryDTO.java": """package com.primevest.domain.pricehistory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceHistoryDTO(
    LocalDate date,
    BigDecimal open,
    BigDecimal close,
    BigDecimal high,
    BigDecimal low,
    Long volume
) {}
""",
    "domain/pricehistory/AssetPriceHistoryService.java": """package com.primevest.domain.pricehistory;

import com.primevest.domain.pricehistory.dto.PriceHistoryDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssetPriceHistoryService {

    /**
     * Gets price history.
     */
    public List<PriceHistoryDTO> getHistory(String ticker, LocalDate start, LocalDate end) {
        return List.of();
    }

    /**
     * Saves price history.
     */
    @Transactional
    public void saveHistory(Long assetId, PriceHistoryDTO dto) {
    }
}
"""
}

for filepath, content in files.items():
    full_path = os.path.join(base_dir, filepath)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)

print("Files generated successfully.")
