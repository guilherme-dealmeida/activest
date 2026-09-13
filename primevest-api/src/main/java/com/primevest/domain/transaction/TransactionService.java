package com.primevest.domain.transaction;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.transaction.dto.TransactionCreateDTO;
import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final AssetRepository assetRepository;

    /**
     * Gets user transactions.
     */
    public List<TransactionResponseDTO> getTransactions(UUID userId) {
        List<Transaction> transactions = transactionRepository.findAllTransactionsByUserId(userId);
        return transactions.stream()
            .sorted(Comparator.comparing(Transaction::getTransactionDate).reversed()
                .thenComparing(Comparator.comparing(Transaction::getCreatedAt).reversed()))
            .map(this::toResponseDTO)
            .collect(Collectors.toList());
    }

    /**
     * Gets transaction by ID.
     */
    public TransactionResponseDTO getTransactionById(UUID userId, UUID txId) {
        Transaction tx = transactionRepository.findById(txId)
            .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));
        return toResponseDTO(tx);
    }

    /**
     * Creates a transaction and updates portfolio totals.
     */
    @Transactional
    public TransactionResponseDTO createTransaction(UUID userId, TransactionCreateDTO dto) {
        Asset asset = assetRepository.findByTicker(dto.ticker())
            .orElseThrow(() -> new BusinessException("Ativo não encontrado: " + dto.ticker()));

        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId)
            .orElseThrow(() -> new BusinessException("Portfolio não encontrado"));

        PortfolioAsset pa = portfolioAssetRepository.findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
            .orElseGet(() -> {
                PortfolioAsset newPa = PortfolioAsset.builder()
                    .portfolio(portfolio)
                    .asset(asset)
                    .totalShares(BigDecimal.ZERO)
                    .averagePrice(BigDecimal.ZERO)
                    .totalInvested(BigDecimal.ZERO)
                    .currentValue(BigDecimal.ZERO)
                    .totalDividends(BigDecimal.ZERO)
                    .isReservation(dto.isReservation() != null && dto.isReservation())
                    .build();
                return portfolioAssetRepository.save(newPa);
            });

        BigDecimal totalAmount = dto.shares().multiply(dto.pricePerShare());
        Transaction tx = Transaction.builder()
            .portfolioAsset(pa)
            .type(dto.type())
            .shares(dto.shares())
            .pricePerShare(dto.pricePerShare())
            .totalAmount(totalAmount)
            .brokerageFee(dto.brokerageFee() != null ? dto.brokerageFee() : BigDecimal.ZERO)
            .transactionDate(dto.transactionDate())
            .build();

        tx = transactionRepository.save(tx);
        recalculatePortfolioAsset(pa);
        return toResponseDTO(tx);
    }

    /**
     * Updates an existing transaction and recalculates portfolio totals.
     * Only allows editing: shares, pricePerShare, brokerageFee, transactionDate.
     * Does NOT allow changing: type, ticker/asset.
     */
    @Transactional
    public TransactionResponseDTO updateTransaction(UUID userId, UUID txId, TransactionCreateDTO dto) {
        Transaction tx = transactionRepository.findById(txId)
            .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        // Only update allowed fields (quantity, price, fee, date)
        tx.setShares(dto.shares());
        tx.setPricePerShare(dto.pricePerShare());
        tx.setTotalAmount(dto.shares().multiply(dto.pricePerShare()));
        tx.setBrokerageFee(dto.brokerageFee() != null ? dto.brokerageFee() : BigDecimal.ZERO);
        tx.setTransactionDate(dto.transactionDate());

        tx = transactionRepository.save(tx);
        recalculatePortfolioAsset(tx.getPortfolioAsset());
        return toResponseDTO(tx);
    }

    /**
     * Deletes a transaction and recalculates portfolio totals.
     */
    @Transactional
    public void deleteTransaction(UUID userId, UUID txId) {
        Transaction tx = transactionRepository.findById(txId)
            .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));
        PortfolioAsset pa = tx.getPortfolioAsset();
        transactionRepository.delete(tx);
        transactionRepository.flush();
        recalculatePortfolioAsset(pa);
    }

    /**
     * Recalculates ALL portfolio assets for a user.
     * Useful to fix historical data that wasn't recalculated.
     */
    @Transactional
    public void recalculateAllPortfolioAssets(UUID userId) {
        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId)
            .orElseThrow(() -> new BusinessException("Portfolio não encontrado"));
        List<PortfolioAsset> assets = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());
        for (PortfolioAsset pa : assets) {
            recalculatePortfolioAsset(pa);
        }
    }

    /**
     * Recalculates PortfolioAsset totals from ALL its transactions.
     * 
     * Logic:
     * - BUY: adds shares, recalculates weighted average price, adds to totalInvested
     * - SELL: subtracts shares, keeps averagePrice unchanged, subtracts (shares * avgPrice) from totalInvested
     * - currentValue = totalShares * asset.currentPrice (or averagePrice if no market price)
     */
    private void recalculatePortfolioAsset(PortfolioAsset pa) {
        List<Transaction> txs = transactionRepository
            .findByPortfolioAssetIdOrderByTransactionDateDesc(pa.getId());

        // Process chronologically (oldest first)
        txs.sort(Comparator.comparing(Transaction::getTransactionDate)
            .thenComparing(Transaction::getCreatedAt));

        BigDecimal totalShares = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO; // accumulated cost for average price calculation

        for (Transaction tx : txs) {
            BigDecimal txShares = tx.getShares();
            BigDecimal txPrice = tx.getPricePerShare();

            if (tx.getType() == TransactionType.BUY) {
                // Weighted average: newAvg = (oldCost + newCost) / (oldShares + newShares)
                BigDecimal txCost = txShares.multiply(txPrice);
                totalCost = totalCost.add(txCost);
                totalShares = totalShares.add(txShares);
            } else if (tx.getType() == TransactionType.SELL) {
                if (totalShares.compareTo(BigDecimal.ZERO) > 0) {
                    // Remove proportional cost: cost reduction = (sellShares / totalShares) * totalCost
                    BigDecimal avgPrice = totalCost.divide(totalShares, 10, RoundingMode.HALF_UP);
                    BigDecimal costReduction = txShares.multiply(avgPrice);
                    totalCost = totalCost.subtract(costReduction);
                    totalShares = totalShares.subtract(txShares);
                    // Prevent negatives
                    if (totalShares.compareTo(BigDecimal.ZERO) < 0) {
                        totalShares = BigDecimal.ZERO;
                        totalCost = BigDecimal.ZERO;
                    }
                }
            }
        }

        // Calculate average price
        BigDecimal averagePrice = BigDecimal.ZERO;
        if (totalShares.compareTo(BigDecimal.ZERO) > 0) {
            averagePrice = totalCost.divide(totalShares, 4, RoundingMode.HALF_UP);
        }

        // Calculate current value using market price if available, otherwise use average price
        BigDecimal marketPrice = pa.getAsset() != null ? pa.getAsset().getCurrentPrice() : null;
        BigDecimal priceForValue = (marketPrice != null && marketPrice.compareTo(BigDecimal.ZERO) > 0)
            ? marketPrice : averagePrice;
        BigDecimal currentValue = totalShares.multiply(priceForValue);

        // Update portfolio asset
        pa.setTotalShares(totalShares);
        pa.setAveragePrice(averagePrice);
        pa.setTotalInvested(totalCost);
        pa.setCurrentValue(currentValue);
        portfolioAssetRepository.save(pa);
    }

    private TransactionResponseDTO toResponseDTO(Transaction tx) {
        PortfolioAsset pa = tx.getPortfolioAsset();
        String ticker = pa != null && pa.getAsset() != null ? pa.getAsset().getTicker() : "";
        String assetName = pa != null && pa.getAsset() != null ? pa.getAsset().getName() : "";
        String assetTypeName = pa != null && pa.getAsset() != null && pa.getAsset().getAssetType() != null
                ? (pa.getAsset().getAssetType().getDescription() != null ? pa.getAsset().getAssetType().getDescription() : pa.getAsset().getAssetType().getName()) : "";
        return new TransactionResponseDTO(
            tx.getId(), ticker, assetName, assetTypeName, tx.getType(),
            tx.getShares(), tx.getPricePerShare(), tx.getTotalAmount(),
            tx.getBrokerageFee(), tx.getTransactionDate(), tx.getCreatedAt()
        );
    }
}
