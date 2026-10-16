package com.primevest.domain.fixedincome;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.support.CatalogIds;
import com.primevest.support.Fixtures;
import com.primevest.support.JpaTestBase;
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

    private User dono;
    private User outro;
    private Asset cdb;
    private Asset tesouro;

    @BeforeEach
    void cenario() {
        dono = userRepository.save(Fixtures.user("dono@primevest.test"));
        outro = userRepository.save(Fixtures.user("outro@primevest.test"));
        cdb = CatalogIds.asset(assetRepository, CatalogIds.CDB);
        tesouro = CatalogIds.asset(assetRepository, CatalogIds.TESOURO_IPCA_2050);
    }

    private FixedIncomeNote cdbDe(User user, String emissor) {
        return Fixtures.note(user, cdb, Fixtures.issuer(issuerRepository, emissor), LocalDate.of(2030, 1, 15),
            RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("110.0000"));
    }

    @Test
    @DisplayName("findByIdAndUserId nao devolve o titulo de outro usuario — o id vem do cliente")
    void tituloEscopadoPeloDono() {
        FixedIncomeNote minha = repository.saveAndFlush(cdbDe(dono, "BTG"));

        assertThat(repository.findByIdAndUserId(minha.getId(), dono.getId())).isPresent();
        assertThat(repository.findByIdAndUserId(minha.getId(), outro.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdOrderByMaturityDateAsc ordena pelo vencimento e isola usuarios")
    void ordenadoPorVencimento() {
        FixedIncomeNote longa = cdbDe(dono, "BTG");
        longa.setMaturityDate(LocalDate.of(2032, 5, 10));
        FixedIncomeNote curta = cdbDe(dono, "Inter");
        curta.setMaturityDate(LocalDate.of(2028, 3, 1));
        repository.saveAll(java.util.List.of(longa, curta));
        repository.saveAndFlush(cdbDe(outro, "Nubank"));

        assertThat(repository.findByUserIdOrderByMaturityDateAsc(dono.getId()))
            .extracting(n -> n.getIssuer().getLegalName())
            .containsExactly("Inter", "BTG");
    }

    @Test
    @DisplayName("findByUserIdAndAssetId separa os titulos por ativo")
    void titulosPorAtivo() {
        repository.save(cdbDe(dono, "BTG"));
        repository.saveAndFlush(Fixtures.note(dono, tesouro, Fixtures.issuer(issuerRepository, "Tesouro Nacional"), LocalDate.of(2050, 8, 15),
            RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.0900")));

        assertThat(repository.findByUserIdAndAssetId(dono.getId(), cdb.getId())).hasSize(1);
        assertThat(repository.findByUserIdAndAssetId(dono.getId(), tesouro.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Regra H no banco: dois titulos do mesmo ativo e distribuidora comprados na mesma data violam o indice unico")
    void doisTitulosNaMesmaDataDeCompra() {
        FixedIncomeNote primeiro = Fixtures.note(dono, tesouro, Fixtures.issuer(issuerRepository, "Tesouro Nacional"), LocalDate.of(2050, 8, 15),
            RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.0900"));
        primeiro.setPurchaseDate(LocalDate.of(2026, 9, 1));
        repository.saveAndFlush(primeiro);

        FixedIncomeNote segundo = Fixtures.note(dono, tesouro, Fixtures.issuer(issuerRepository, "Tesouro Nacional"), LocalDate.of(2050, 8, 15),
            RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.5000"));
        segundo.setPurchaseDate(LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> repository.saveAndFlush(segundo))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_fixed_income_notes_purchase");
    }

    @Test
    @DisplayName("O indice unico e parcial: varios titulos sem data de compra convivem")
    void semDataDeCompraNaoConflita() {
        repository.saveAndFlush(cdbDe(dono, "BTG"));
        repository.saveAndFlush(cdbDe(dono, "Inter"));

        assertThat(repository.findByUserIdAndAssetId(dono.getId(), cdb.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Regra H por distribuidora (D3): o mesmo titulo comprado no mesmo dia em duas corretoras convive")
    void mesmaDataEmDistribuidorasDiferentes() {
        FixedIncomeNote naNu = Fixtures.note(dono, tesouro, Fixtures.issuer(issuerRepository, "Tesouro Nacional"),
            LocalDate.of(2050, 8, 15), RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.0900"));
        naNu.setDistributor(Fixtures.issuer(issuerRepository, "Corretora A"));
        naNu.setPurchaseDate(LocalDate.of(2026, 9, 1));
        repository.saveAndFlush(naNu);

        FixedIncomeNote naXp = Fixtures.note(dono, tesouro, Fixtures.issuer(issuerRepository, "Tesouro Nacional"),
            LocalDate.of(2050, 8, 15), RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.1000"));
        naXp.setDistributor(Fixtures.issuer(issuerRepository, "Corretora B"));
        naXp.setPurchaseDate(LocalDate.of(2026, 9, 1));
        repository.saveAndFlush(naXp);

        assertThat(repository.findByUserIdAndAssetId(dono.getId(), tesouro.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Codigo do papel (D4): unico por usuario; outro usuario pode ter o mesmo papel")
    void codigoDoPapelUnicoPorUsuario() {
        FixedIncomeNote minha = cdbDe(dono, "BTG");
        minha.setPaperCode("CDBA2478FYE");
        repository.saveAndFlush(minha);

        FixedIncomeNote doOutro = cdbDe(outro, "BTG");
        doOutro.setPaperCode("CDBA2478FYE");
        repository.saveAndFlush(doOutro);

        FixedIncomeNote repetida = cdbDe(dono, "Inter");
        repetida.setPaperCode("CDBA2478FYE");
        assertThatThrownBy(() -> repository.saveAndFlush(repetida))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_fixed_income_notes_paper_code");
    }

    @Test
    @DisplayName("ck_fixed_income_notes_rate_coherence: pos-fixado sem indice e recusado pelo banco")
    void coerenciaDeTaxa() {
        FixedIncomeNote semIndice = Fixtures.note(dono, cdb, Fixtures.issuer(issuerRepository, "BTG"), LocalDate.of(2030, 1, 15),
            RateType.POS_FIXADO, null, new BigDecimal("110.0000"));

        assertThatThrownBy(() -> repository.saveAndFlush(semIndice))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_fixed_income_notes_rate_coherence");
    }
}
