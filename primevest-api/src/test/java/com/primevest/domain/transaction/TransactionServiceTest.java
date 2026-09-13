package com.primevest.domain.transaction;

import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PortfolioAssetRepository portfolioAssetRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    @DisplayName("Deve criar transação de COMPRA e atualizar posição no portfólio")
    void testCreateBuyTransaction() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        TransactionCreateDTO dto = new TransactionCreateDTO();
        dto.setTicker("PETR4");
        dto.setType(TransactionType.BUY);
        dto.setShares(new BigDecimal("100"));
        dto.setPrice(new BigDecimal("35.00"));

        PortfolioAsset asset = new PortfolioAsset();
        asset.setTicker("PETR4");
        asset.setShares(BigDecimal.ZERO);
        asset.setAveragePrice(BigDecimal.ZERO);

        when(portfolioAssetRepository.findByPortfolioIdAndTicker(portfolio.getId(), "PETR4"))
                .thenReturn(Optional.of(asset));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        // Act
        TransactionResponseDTO response = transactionService.createTransaction(userId, dto);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getTicker()).isEqualTo("PETR4");
        assertThat(asset.getShares()).isEqualByComparingTo("100");
        assertThat(asset.getAveragePrice()).isEqualByComparingTo("35.00");
        verify(portfolioAssetRepository, times(1)).save(asset);
    }

    @Test
    @DisplayName("Deve atualizar o preço médio corretamente em nova compra (média ponderada)")
    void testCreateBuyTransaction_updatesAveragePrice() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        TransactionCreateDTO dto = new TransactionCreateDTO();
        dto.setTicker("PETR4");
        dto.setType(TransactionType.BUY);
        dto.setShares(new BigDecimal("50"));
        dto.setPrice(new BigDecimal("38.00"));

        PortfolioAsset asset = new PortfolioAsset();
        asset.setTicker("PETR4");
        // Existing: 100 shares at 35.00
        asset.setShares(new BigDecimal("100"));
        asset.setAveragePrice(new BigDecimal("35.00"));

        when(portfolioAssetRepository.findByPortfolioIdAndTicker(portfolio.getId(), "PETR4"))
                .thenReturn(Optional.of(asset));
        when(transactionRepository.save(any())).thenReturn(new Transaction());

        // Act
        transactionService.createTransaction(userId, dto);

        // Assert
        // Old Total: 100 * 35 = 3500
        // New Amount: 50 * 38 = 1900
        // Total Invested = 5400
        // Total Shares = 150
        // New Avg Price = 5400 / 150 = 36.00
        assertThat(asset.getShares()).isEqualByComparingTo("150");
        assertThat(asset.getAveragePrice()).isEqualByComparingTo("36.00");
    }

    @Test
    @DisplayName("Deve criar transação de VENDA e abater quantidade de ações")
    void testCreateSellTransaction() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        TransactionCreateDTO dto = new TransactionCreateDTO();
        dto.setTicker("PETR4");
        dto.setType(TransactionType.SELL);
        dto.setShares(new BigDecimal("50"));
        dto.setPrice(new BigDecimal("38.00")); // Selling with profit

        PortfolioAsset asset = new PortfolioAsset();
        asset.setTicker("PETR4");
        asset.setShares(new BigDecimal("100"));
        asset.setAveragePrice(new BigDecimal("35.00"));

        when(portfolioAssetRepository.findByPortfolioIdAndTicker(portfolio.getId(), "PETR4"))
                .thenReturn(Optional.of(asset));
        when(transactionRepository.save(any())).thenReturn(new Transaction());

        // Act
        transactionService.createTransaction(userId, dto);

        // Assert
        // Sell 50 shares -> Remaining 50 shares. Avg price doesn't change on selling.
        assertThat(asset.getShares()).isEqualByComparingTo("50");
        assertThat(asset.getAveragePrice()).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("Deve lançar BusinessException ao tentar vender mais ações do que possui")
    void testCreateSellTransaction_insufficientShares() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        TransactionCreateDTO dto = new TransactionCreateDTO();
        dto.setTicker("VALE3");
        dto.setType(TransactionType.SELL);
        dto.setShares(new BigDecimal("100"));
        dto.setPrice(new BigDecimal("70.00"));

        PortfolioAsset asset = new PortfolioAsset();
        asset.setTicker("VALE3");
        asset.setShares(new BigDecimal("50")); // Only has 50
        asset.setAveragePrice(new BigDecimal("72.00"));

        when(portfolioAssetRepository.findByPortfolioIdAndTicker(portfolio.getId(), "VALE3"))
                .thenReturn(Optional.of(asset));

        // Act & Assert
        assertThrows(BusinessException.class, () -> transactionService.createTransaction(userId, dto));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve criar novo PortfolioAsset caso não exista na primeira compra")
    void testCreateTransaction_newAsset() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));

        TransactionCreateDTO dto = new TransactionCreateDTO();
        dto.setTicker("MXRF11");
        dto.setType(TransactionType.BUY);
        dto.setShares(new BigDecimal("200"));
        dto.setPrice(new BigDecimal("10.00"));

        when(portfolioAssetRepository.findByPortfolioIdAndTicker(portfolio.getId(), "MXRF11"))
                .thenReturn(Optional.empty()); // Does not exist
        when(transactionRepository.save(any())).thenReturn(new Transaction());

        // Act
        transactionService.createTransaction(userId, dto);

        // Assert
        verify(portfolioAssetRepository, times(1)).save(argThat(asset -> 
            asset.getTicker().equals("MXRF11") &&
            asset.getShares().compareTo(new BigDecimal("200")) == 0 &&
            asset.getAveragePrice().compareTo(new BigDecimal("10.00")) == 0
        ));
    }

    @Test
    @DisplayName("Deve deletar transação e reverter efeito na posição (desfazer compra)")
    void testDeleteTransaction_reversesBuy() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        
        Portfolio portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());

        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setTicker("VALE3");
        tx.setType(TransactionType.BUY);
        tx.setShares(new BigDecimal("50"));
        tx.setPrice(new BigDecimal("70.00"));
        tx.setPortfolioId(portfolio.getId());

        PortfolioAsset asset = new PortfolioAsset();
        asset.setTicker("VALE3");
        asset.setShares(new BigDecimal("100")); // Had 100
        // Let's assume prior avg price was 60.00 for 50 shares
        // So total was 3000 (old) + 3500 (new) = 6500. Avg price now = 65.00
        asset.setAveragePrice(new BigDecimal("65.00"));

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));
        when(portfolioAssetRepository.findByPortfolioIdAndTicker(portfolio.getId(), "VALE3"))
                .thenReturn(Optional.of(asset));

        // Act
        transactionService.deleteTransaction(userId, txId);

        // Assert
        // Reverting the buy of 50 shares at 70.00
        // New total shares = 100 - 50 = 50
        // Old Invested = (100 * 65.00) - (50 * 70.00) = 6500 - 3500 = 3000
        // Reverted Avg Price = 3000 / 50 = 60.00
        assertThat(asset.getShares()).isEqualByComparingTo("50");
        assertThat(asset.getAveragePrice()).isEqualByComparingTo("60.00");
        verify(transactionRepository, times(1)).delete(tx);
    }
}
