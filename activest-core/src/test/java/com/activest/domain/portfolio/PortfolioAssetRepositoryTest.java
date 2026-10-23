package com.activest.domain.portfolio;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.asset.Indexer;
import com.activest.domain.asset.RateType;
import com.activest.domain.fixedincome.FixedIncomeNote;
import com.activest.domain.fixedincome.FixedIncomeNoteRepository;
import com.activest.domain.user.User;
import com.activest.domain.user.UserRepository;
import com.activest.support.CatalogIds;
import com.activest.support.Fixtures;
import com.activest.support.JpaTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioAssetRepositoryTest extends JpaTestBase {

    @Autowired private PortfolioAssetRepository repository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private FixedIncomeNoteRepository noteRepository;
    @Autowired private com.activest.domain.fixedincome.FixedIncomeIssuerRepository issuerRepository;
    @Autowired private EntityManager em;

    private User owner;
    private User other;
    private Portfolio portfolioEntity;
    private Portfolio otherUsersPortfolio;
    private Asset stock;
    private Asset cdb;

    @BeforeEach
    void scenario() {
        owner = userRepository.save(Fixtures.user("dono@activest.test"));
        other = userRepository.save(Fixtures.user("outro@activest.test"));
        portfolioEntity = portfolioRepository.save(Fixtures.portfolio(owner));
        otherUsersPortfolio = portfolioRepository.save(Fixtures.portfolio(other));
        stock = CatalogIds.asset(assetRepository, CatalogIds.STOCK);
        cdb = CatalogIds.asset(assetRepository, CatalogIds.CDB);
    }

    private FixedIncomeNote noteEntity(User user, String issuerEntity) {
        return noteRepository.save(Fixtures.note(user, cdb, Fixtures.issuer(issuerRepository, issuerEntity), LocalDate.of(2030, 1, 15),
            RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("110.0000")));
    }

    @Test
    @DisplayName("findByPortfolioIdAndAssetIdAndNoteIsNull acha a posicao de renda variavel e ignora a que tem titulo")
    void positionWithoutNote() {
        PortfolioAsset withoutNote = repository.save(Fixtures.position(portfolioEntity, stock));
        repository.save(Fixtures.position(portfolioEntity, cdb, noteEntity(owner, "BTG")));

        Optional<PortfolioAsset> found =
            repository.findByPortfolioIdAndAssetIdAndNoteIsNull(portfolioEntity.getId(), stock.getId());

        assertThat(found).get().extracting(PortfolioAsset::getId).isEqualTo(withoutNote.getId());
        assertThat(repository.findByPortfolioIdAndAssetIdAndNoteIsNull(portfolioEntity.getId(), cdb.getId())).isEmpty();
    }

    @Test
    @DisplayName("V56: posicao e titulo no mesmo produto — trocar so a posicao e recusado; trocar o titulo leva a posicao")
    void noteAndPositionOnSameProduct() {
        PortfolioAsset positionEntity = repository.saveAndFlush(Fixtures.position(portfolioEntity, cdb, noteEntity(owner, "BTG")));
        Asset rdb = CatalogIds.asset(assetRepository, "RDB");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> em.createNativeQuery(
                "UPDATE portfolio_assets SET asset_id = :a WHERE id = :id")
                .setParameter("a", rdb.getId()).setParameter("id", positionEntity.getId()).executeUpdate())
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("V56: trocar o produto do titulo leva a posicao junto (ON UPDATE CASCADE)")
    void productChangeOnNote() {
        FixedIncomeNote noteEntity = noteEntity(owner, "Nu");
        PortfolioAsset positionEntity = repository.saveAndFlush(Fixtures.position(portfolioEntity, cdb, noteEntity));
        Asset rdb = CatalogIds.asset(assetRepository, "RDB");

        em.createNativeQuery("UPDATE fixed_income_notes SET asset_id = :a WHERE id = :id")
            .setParameter("a", rdb.getId()).setParameter("id", noteEntity.getId()).executeUpdate();

        Object assetEntity = em.createNativeQuery("SELECT asset_id FROM portfolio_assets WHERE id = :id")
            .setParameter("id", positionEntity.getId()).getSingleResult();
        assertThat(((Number) assetEntity).longValue()).isEqualTo(rdb.getId());
    }

    @Test
    @DisplayName("Dois CDBs do mesmo ativo sao duas posicoes, cada uma achada pelo seu titulo")
    void twoPositionsOfSameAssetWithDifferentNotes() {
        FixedIncomeNote btg = noteEntity(owner, "BTG");
        FixedIncomeNote inter = noteEntity(owner, "Inter");
        PortfolioAsset btgPosition = repository.save(Fixtures.position(portfolioEntity, cdb, btg));
        PortfolioAsset interPosition = repository.save(Fixtures.position(portfolioEntity, cdb, inter));

        assertThat(btgPosition.getId()).isNotEqualTo(interPosition.getId());
        assertThat(repository.findByPortfolioIdAndNoteId(portfolioEntity.getId(), btg.getId()))
            .get().extracting(PortfolioAsset::getId).isEqualTo(btgPosition.getId());
        assertThat(repository.findByPortfolioIdAndNoteId(portfolioEntity.getId(), inter.getId()))
            .get().extracting(PortfolioAsset::getId).isEqualTo(interPosition.getId());
    }

    @Test
    @DisplayName("NULLS NOT DISTINCT: duas posicoes do mesmo ativo sem titulo violam a unicidade")
    void uniquenessWithNullNoteId() {
        repository.saveAndFlush(Fixtures.position(portfolioEntity, stock));

        assertThatThrownBy(() -> repository.saveAndFlush(Fixtures.position(portfolioEntity, stock)))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_portfolio_assets_position");
    }

    @Test
    @DisplayName("A mesma posicao em carteiras diferentes e permitida")
    void uniquenessIsPerPortfolio() {
        repository.saveAndFlush(Fixtures.position(portfolioEntity, stock));
        repository.saveAndFlush(Fixtures.position(otherUsersPortfolio, stock));

        assertThat(repository.findByPortfolioId(portfolioEntity.getId())).hasSize(1);
        assertThat(repository.findByPortfolioId(otherUsersPortfolio.getId())).hasSize(1);
    }

    @Test
    @DisplayName("findAllByPortfolioIdAndAssetId traz todos os titulos do ativo, com a nota carregada")
    void assetNotes() {
        repository.save(Fixtures.position(portfolioEntity, cdb, noteEntity(owner, "BTG")));
        repository.save(Fixtures.position(portfolioEntity, cdb, noteEntity(owner, "Inter")));
        repository.save(Fixtures.position(portfolioEntity, stock));
        em.flush();
        em.clear();

        List<PortfolioAsset> notes = repository.findAllByPortfolioIdAndAssetId(portfolioEntity.getId(), cdb.getId());

        assertThat(notes).hasSize(2);
        assertThat(notes).allSatisfy(pa -> assertThat(pa.getNote().getIssuer().getLegalName()).isNotBlank());
    }

    @Test
    @DisplayName("findAllByPortfolioIdWithAssetEagerly traz renda variavel (sem nota) e renda fixa juntas")
    void portfolioListingKeepsEquities() {
        repository.save(Fixtures.position(portfolioEntity, stock));
        repository.save(Fixtures.position(portfolioEntity, cdb, noteEntity(owner, "BTG")));
        em.flush();
        em.clear();

        List<PortfolioAsset> positions = repository.findAllByPortfolioIdWithAssetEagerly(portfolioEntity.getId());

        assertThat(positions).hasSize(2);
        assertThat(positions).anySatisfy(pa -> assertThat(pa.getNote()).isNull());
        assertThat(positions).allSatisfy(pa -> assertThat(pa.getAsset().getTicker()).isNotBlank());
    }

    @Test
    @DisplayName("findOwnedById devolve a posicao ao dono, com o tipo do ativo carregado")
    void positionScopedByOwner() {
        PortfolioAsset positionEntity = repository.save(Fixtures.position(portfolioEntity, stock));
        em.flush();
        em.clear();

        Optional<PortfolioAsset> found = repository.findOwnedById(owner.getId(), positionEntity.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getAsset().getAssetType().getName()).isEqualTo("ACAO");
    }

    @Test
    @DisplayName("findOwnedById nao devolve a posicao de outro usuario")
    void otherOwnerPositionNotReturned() {
        PortfolioAsset positionEntity = repository.saveAndFlush(Fixtures.position(portfolioEntity, stock));

        assertThat(repository.findOwnedById(other.getId(), positionEntity.getId())).isEmpty();
        assertThat(repository.findOwnedById(owner.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findAllByAssetIdIn atravessa carteiras — e o que a atualizacao de cotacao usa")
    void positionsByAssetAcrossPortfolios() {
        repository.save(Fixtures.position(portfolioEntity, stock));
        repository.save(Fixtures.position(otherUsersPortfolio, stock));
        em.flush();
        em.clear();

        assertThat(repository.findAllByAssetIdIn(List.of(stock.getId()))).hasSize(2);
    }
}
