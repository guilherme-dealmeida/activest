package com.primevest.domain.transaction;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.fixedincome.FixedIncomeNote;
import com.primevest.domain.fixedincome.FixedIncomeNoteRepository;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.support.CatalogIds;
import com.primevest.support.Fixtures;
import com.primevest.support.JpaTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionRepositoryTest extends JpaTestBase {

    @Autowired private TransactionRepository repository;
    @Autowired private PortfolioAssetRepository positionRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private FixedIncomeNoteRepository noteRepository;
    @Autowired private com.primevest.domain.fixedincome.FixedIncomeIssuerRepository issuerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private EntityManager em;

    private User owner;
    private User other;
    private Portfolio portfolioEntity;
    private Asset stock;
    private Asset treasury;
    private PortfolioAsset stockPosition;

    @BeforeEach
    void scenario() {
        owner = userRepository.save(Fixtures.user("dono@primevest.test"));
        other = userRepository.save(Fixtures.user("outro@primevest.test"));
        portfolioEntity = portfolioRepository.save(Fixtures.portfolio(owner));
        stock = CatalogIds.asset(assetRepository, CatalogIds.STOCK);
        treasury = CatalogIds.asset(assetRepository, CatalogIds.TREASURY_IPCA_2050);
        stockPosition = positionRepository.save(Fixtures.position(portfolioEntity, stock));
    }

    private PortfolioAsset treasuryNote(LocalDate buyTx) {
        FixedIncomeNote note = noteRepository.save(Fixtures.note(owner, treasury, Fixtures.issuer(issuerRepository, "Tesouro Nacional"),
            LocalDate.of(2050, 8, 15), RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.0900")));
        note.setPurchaseDate(buyTx);
        return positionRepository.save(Fixtures.position(portfolioEntity, treasury, note));
    }

    @Test
    @DisplayName("findOwnedById devolve a transacao ao dono, com ativo, tipo e categoria carregados")
    void transactionScopedByOwner() {
        Transaction buyTx = repository.save(Fixtures.buy(stockPosition, LocalDate.of(2026, 9, 1), "10", "30.00"));
        em.flush();
        em.clear();

        Transaction found = repository.findOwnedById(owner.getId(), buyTx.getId()).orElseThrow();

        assertThat(found.getPortfolioAsset().getAsset().getTicker()).isEqualTo(CatalogIds.STOCK);
        assertThat(found.getPortfolioAsset().getAsset().getAssetType().getName()).isEqualTo("ACAO");
    }

    @Test
    @DisplayName("findOwnedById nao devolve transacao de outro usuario — 'nao encontrada', nunca 'sem permissao'")
    void otherOwnerTransactionNotReturned() {
        Transaction buyTx = repository.saveAndFlush(
            Fixtures.buy(stockPosition, LocalDate.of(2026, 9, 1), "10", "30.00"));

        assertThat(repository.findOwnedById(other.getId(), buyTx.getId())).isEmpty();
        assertThat(repository.findOwnedById(owner.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findAllTransactionsByUserId isola usuarios e nao perde renda variavel no LEFT JOIN do titulo")
    void listingByUser() {
        repository.save(Fixtures.buy(stockPosition, LocalDate.of(2026, 9, 1), "10", "30.00"));
        PortfolioAsset noteEntity = treasuryNote(LocalDate.of(2026, 9, 1));
        repository.save(Fixtures.buy(noteEntity, LocalDate.of(2026, 9, 1), "3", "830.07"));

        Portfolio otherUsersPortfolio = portfolioRepository.save(Fixtures.portfolio(other));
        PortfolioAsset othersPosition = positionRepository.save(Fixtures.position(otherUsersPortfolio, stock));
        repository.save(Fixtures.buy(othersPosition, LocalDate.of(2026, 9, 2), "5", "31.00"));
        em.flush();
        em.clear();

        List<Transaction> mine = repository.findAllTransactionsByUserId(owner.getId());

        assertThat(mine).hasSize(2);
        assertThat(mine).anySatisfy(tx -> assertThat(tx.getNote()).isNull());
        assertThat(mine).anySatisfy(tx -> assertThat(tx.getNote()).isNotNull());
        assertThat(repository.findAllTransactionsByUserId(other.getId())).hasSize(1);
    }

    @Test
    @DisplayName("findByPortfolioIdAndAssetId traz as transacoes de TODOS os titulos do ativo")
    void wholeAssetTransactions() {
        PortfolioAsset note1 = treasuryNote(LocalDate.of(2026, 9, 1));
        PortfolioAsset note2 = treasuryNote(LocalDate.of(2026, 9, 15));
        repository.save(Fixtures.buy(note1, LocalDate.of(2026, 9, 1), "3", "830.07"));
        repository.save(Fixtures.buy(note2, LocalDate.of(2026, 9, 15), "4", "830.10"));
        repository.save(Fixtures.buy(stockPosition, LocalDate.of(2026, 9, 1), "10", "30.00"));
        em.flush();
        em.clear();

        List<Transaction> ofAsset = repository.findByPortfolioIdAndAssetId(portfolioEntity.getId(), treasury.getId());

        assertThat(ofAsset).hasSize(2);
        assertThat(ofAsset).allSatisfy(tx ->
            assertThat(tx.getPortfolioAsset().getAsset().getId()).isEqualTo(treasury.getId()));
    }

    @Test
    @DisplayName("findBySaleGroupId junta as partes da mesma venda")
    void partsOfSameSale() {
        PortfolioAsset note1 = treasuryNote(LocalDate.of(2026, 9, 1));
        PortfolioAsset note2 = treasuryNote(LocalDate.of(2026, 9, 15));
        UUID group = UUID.randomUUID();

        Transaction part1 = Fixtures.sell(note1, LocalDate.of(2026, 9, 17), "3", "850.00");
        part1.setSaleGroupId(group);
        Transaction part2 = Fixtures.sell(note2, LocalDate.of(2026, 9, 17), "2", "850.00");
        part2.setSaleGroupId(group);
        repository.saveAll(List.of(part1, part2));
        repository.save(Fixtures.sell(stockPosition, LocalDate.of(2026, 9, 17), "1", "30.00"));
        em.flush();

        assertThat(repository.findBySaleGroupId(group)).hasSize(2);
    }

    @Test
    @DisplayName("findByPortfolioAssetIdOrderByTransactionDateDesc ordena da mais nova para a mais antiga")
    void sortByDate() {
        repository.save(Fixtures.buy(stockPosition, LocalDate.of(2026, 9, 1), "10", "30.00"));
        repository.save(Fixtures.buy(stockPosition, LocalDate.of(2026, 9, 10), "5", "31.00"));
        em.flush();

        List<Transaction> list =
            repository.findByPortfolioAssetIdOrderByTransactionDateDesc(stockPosition.getId());

        assertThat(list).extracting(Transaction::getTransactionDate)
            .containsExactly(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1));
    }
}
