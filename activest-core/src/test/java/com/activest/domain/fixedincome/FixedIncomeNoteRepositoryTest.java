package com.activest.domain.fixedincome;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.asset.Indexer;
import com.activest.domain.asset.RateType;
import com.activest.domain.user.User;
import com.activest.domain.user.UserRepository;
import com.activest.support.CatalogIds;
import com.activest.support.Fixtures;
import com.activest.support.JpaTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedIncomeNoteRepositoryTest extends JpaTestBase {

    @Autowired private FixedIncomeNoteRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private FixedIncomeIssuerRepository issuerRepository;

    private User owner;
    private User other;
    private Asset cdb;
    private Asset treasury;

    @BeforeEach
    void scenario() {
        owner = userRepository.save(Fixtures.user("dono@activest.test"));
        other = userRepository.save(Fixtures.user("outro@activest.test"));
        cdb = CatalogIds.asset(assetRepository, CatalogIds.CDB);
        treasury = CatalogIds.asset(assetRepository, CatalogIds.TREASURY_IPCA_2050);
    }

    private FixedIncomeNote cdbDe(User user, String issuerEntity) {
        return Fixtures.note(user, cdb, Fixtures.issuer(issuerRepository, issuerEntity), LocalDate.of(2030, 1, 15),
            RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("110.0000"));
    }

    @Test
    @DisplayName("findByIdAndUserId nao devolve o titulo de outro usuario — o id vem do cliente")
    void noteScopedByOwner() {
        FixedIncomeNote mine = repository.saveAndFlush(cdbDe(owner, "BTG"));

        assertThat(repository.findByIdAndUserId(mine.getId(), owner.getId())).isPresent();
        assertThat(repository.findByIdAndUserId(mine.getId(), other.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdOrderByMaturityDateAsc ordena pelo vencimento e isola usuarios")
    void sortedByMaturity() {
        FixedIncomeNote longNote = cdbDe(owner, "BTG");
        longNote.setMaturityDate(LocalDate.of(2032, 5, 10));
        FixedIncomeNote shortNote = cdbDe(owner, "Inter");
        shortNote.setMaturityDate(LocalDate.of(2028, 3, 1));
        repository.saveAll(java.util.List.of(longNote, shortNote));
        repository.saveAndFlush(cdbDe(other, "Nubank"));

        assertThat(repository.findByUserIdOrderByMaturityDateAsc(owner.getId()))
            .extracting(n -> n.getIssuer().getLegalName())
            .containsExactly("Inter", "BTG");
    }

    @Test
    @DisplayName("findByUserIdAndAssetId separa os titulos por ativo")
    void notesByAsset() {
        repository.save(cdbDe(owner, "BTG"));
        repository.saveAndFlush(Fixtures.note(owner, treasury, Fixtures.issuer(issuerRepository, "Tesouro Nacional"), LocalDate.of(2050, 8, 15),
            RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.0900")));

        assertThat(repository.findByUserIdAndAssetId(owner.getId(), cdb.getId())).hasSize(1);
        assertThat(repository.findByUserIdAndAssetId(owner.getId(), treasury.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Regra H no banco: dois titulos do mesmo ativo e distribuidora comprados na mesma data violam o indice unico")
    void twoNotesOnSameBuyDate() {
        FixedIncomeNote first = Fixtures.note(owner, treasury, Fixtures.issuer(issuerRepository, "Tesouro Nacional"), LocalDate.of(2050, 8, 15),
            RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.0900"));
        first.setPurchaseDate(LocalDate.of(2026, 9, 1));
        repository.saveAndFlush(first);

        FixedIncomeNote second = Fixtures.note(owner, treasury, Fixtures.issuer(issuerRepository, "Tesouro Nacional"), LocalDate.of(2050, 8, 15),
            RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.5000"));
        second.setPurchaseDate(LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> repository.saveAndFlush(second))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_fixed_income_notes_purchase");
    }

    @Test
    @DisplayName("O indice unico e parcial: varios titulos sem data de compra convivem")
    void withoutBuyDateDoesNotConflict() {
        repository.saveAndFlush(cdbDe(owner, "BTG"));
        repository.saveAndFlush(cdbDe(owner, "Inter"));

        assertThat(repository.findByUserIdAndAssetId(owner.getId(), cdb.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Regra H por distribuidora (D3): o mesmo titulo comprado no mesmo dia em duas corretoras convive")
    void sameDateAtDifferentDistributors() {
        FixedIncomeNote naNu = Fixtures.note(owner, treasury, Fixtures.issuer(issuerRepository, "Tesouro Nacional"),
            LocalDate.of(2050, 8, 15), RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.0900"));
        naNu.setDistributor(Fixtures.issuer(issuerRepository, "Corretora A"));
        naNu.setPurchaseDate(LocalDate.of(2026, 9, 1));
        repository.saveAndFlush(naNu);

        FixedIncomeNote naXp = Fixtures.note(owner, treasury, Fixtures.issuer(issuerRepository, "Tesouro Nacional"),
            LocalDate.of(2050, 8, 15), RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.1000"));
        naXp.setDistributor(Fixtures.issuer(issuerRepository, "Corretora B"));
        naXp.setPurchaseDate(LocalDate.of(2026, 9, 1));
        repository.saveAndFlush(naXp);

        assertThat(repository.findByUserIdAndAssetId(owner.getId(), treasury.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Codigo do papel (D4): unico por usuario; outro usuario pode ter o mesmo papel")
    void securityCodeUniquePerUser() {
        FixedIncomeNote mine = cdbDe(owner, "BTG");
        mine.setPaperCode("CDBA2478FYE");
        repository.saveAndFlush(mine);

        FixedIncomeNote ofOther = cdbDe(other, "BTG");
        ofOther.setPaperCode("CDBA2478FYE");
        repository.saveAndFlush(ofOther);

        FixedIncomeNote repeated = cdbDe(owner, "Inter");
        repeated.setPaperCode("CDBA2478FYE");
        assertThatThrownBy(() -> repository.saveAndFlush(repeated))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_fixed_income_notes_paper_code");
    }

    @Test
    @DisplayName("ck_fixed_income_notes_rate_coherence: pos-fixado sem indice e recusado pelo banco")
    void rateCoherence() {
        FixedIncomeNote withoutIndex = Fixtures.note(owner, cdb, Fixtures.issuer(issuerRepository, "BTG"), LocalDate.of(2030, 1, 15),
            RateType.POS_FIXADO, null, new BigDecimal("110.0000"));

        assertThatThrownBy(() -> repository.saveAndFlush(withoutIndex))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_fixed_income_notes_rate_coherence");
    }
}
