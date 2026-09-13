package com.primevest.domain.transaction;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.transaction.dto.TransactionCreateDTO;
import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        Transaction tx = transactionRepository.findById(txId).orElse(null);
        if (tx == null) return null;
        return toResponseDTO(tx);
    }

    /**
     * Creates a transaction.
     */
    @Transactional
    public TransactionResponseDTO createTransaction(UUID userId, TransactionCreateDTO dto) {
        Asset asset = assetRepository.findByTicker(dto.ticker())
            .orElseThrow(() -> new RuntimeException("Asset not found: " + dto.ticker()));

        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId)
            .orElseThrow(() -> new RuntimeException("Portfolio not found"));

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
        return toResponseDTO(tx);
    }

    /**
     * Deletes a transaction.
     */
    @Transactional
    public void deleteTransaction(UUID userId, UUID txId) {
        transactionRepository.deleteById(txId);
    }

    private TransactionResponseDTO toResponseDTO(Transaction tx) {
        PortfolioAsset pa = tx.getPortfolioAsset();
        String ticker = pa != null && pa.getAsset() != null ? pa.getAsset().getTicker() : "";
        String assetName = pa != null && pa.getAsset() != null ? pa.getAsset().getName() : "";
        return new TransactionResponseDTO(
            tx.getId(), ticker, assetName, tx.getType(),
            tx.getShares(), tx.getPricePerShare(), tx.getTotalAmount(),
            tx.getBrokerageFee(), tx.getTransactionDate(), tx.getCreatedAt()
        );
    }
}
