package com.primevest.domain.portfolio;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.fixedincome.FixedIncomeNote;
import com.primevest.domain.fixedincome.FixedIncomeNoteRepository;
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
    @Autowired private com.primevest.domain.fixedincome.FixedIncomeIssuerRepository issuerRepository;
    @Autowired private EntityManager em;

    private User dono;
    private User outro;
    private Portfolio carteira;
    private Portfolio carteiraDoOutro;
    private Asset acao;
    private Asset cdb;

    @BeforeEach
    void cenario() {
        dono = userRepository.save(Fixtures.user("dono@primevest.test"));
        outro = userRepository.save(Fixtures.user("outro@primevest.test"));
        carteira = portfolioRepository.save(Fixtures.portfolio(dono));
        carteiraDoOutro = portfolioRepository.save(Fixtures.portfolio(outro));
        acao = CatalogIds.asset(assetRepository, CatalogIds.ACAO);
        cdb = CatalogIds.asset(assetRepository, CatalogIds.CDB);
    }

    private FixedIncomeNote nota(User user, String emissor) {
        return noteRepository.save(Fixtures.note(user, cdb, Fixtures.issuer(issuerRepository, emissor), LocalDate.of(2030, 1, 15),
            RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("110.0000")));
    }

    @Test
    @DisplayName("findByPortfolioIdAndAssetIdAndNoteIsNull acha a posicao de renda variavel e ignora a que tem titulo")
    void posicaoSemTitulo() {
        PortfolioAsset semNota = repository.save(Fixtures.position(carteira, acao));
        repository.save(Fixtures.position(carteira, cdb, nota(dono, "BTG")));

        Optional<PortfolioAsset> achada =
            repository.findByPortfolioIdAndAssetIdAndNoteIsNull(carteira.getId(), acao.getId());

        assertThat(achada).get().extracting(PortfolioAsset::getId).isEqualTo(semNota.getId());
        assertThat(repository.findByPortfolioIdAndAssetIdAndNoteIsNull(carteira.getId(), cdb.getId())).isEmpty();
    }

    @Test
    @DisplayName("V56: posicao e titulo no mesmo produto — trocar so a posicao e recusado; trocar o titulo leva a posicao")
    void tituloEPosicaoNoMesmoProduto() {
        PortfolioAsset posicao = repository.saveAndFlush(Fixtures.position(carteira, cdb, nota(dono, "BTG")));
        Asset rdb = CatalogIds.asset(assetRepository, "RDB");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> em.createNativeQuery(
                "UPDATE portfolio_assets SET asset_id = :a WHERE id = :id")
                .setParameter("a", rdb.getId()).setParameter("id", posicao.getId()).executeUpdate())
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("V56: trocar o produto do titulo leva a posicao junto (ON UPDATE CASCADE)")
    void trocaDoProdutoNoTitulo() {
        FixedIncomeNote nota = nota(dono, "Nu");
        PortfolioAsset posicao = repository.saveAndFlush(Fixtures.position(carteira, cdb, nota));
        Asset rdb = CatalogIds.asset(assetRepository, "RDB");

        em.createNativeQuery("UPDATE fixed_income_notes SET asset_id = :a WHERE id = :id")
            .setParameter("a", rdb.getId()).setParameter("id", nota.getId()).executeUpdate();

        Object ativo = em.createNativeQuery("SELECT asset_id FROM portfolio_assets WHERE id = :id")
            .setParameter("id", posicao.getId()).getSingleResult();
        assertThat(((Number) ativo).longValue()).isEqualTo(rdb.getId());
    }

    @Test
    @DisplayName("Dois CDBs do mesmo ativo sao duas posicoes, cada uma achada pelo seu titulo")
    void duasPosicoesDoMesmoAtivoComTitulosDiferentes() {
        FixedIncomeNote btg = nota(dono, "BTG");
        FixedIncomeNote inter = nota(dono, "Inter");
        PortfolioAsset posicaoBtg = repository.save(Fixtures.position(carteira, cdb, btg));
        PortfolioAsset posicaoInter = repository.save(Fixtures.position(carteira, cdb, inter));

        assertThat(posicaoBtg.getId()).isNotEqualTo(posicaoInter.getId());
        assertThat(repository.findByPortfolioIdAndNoteId(carteira.getId(), btg.getId()))
            .get().extracting(PortfolioAsset::getId).isEqualTo(posicaoBtg.getId());
        assertThat(repository.findByPortfolioIdAndNoteId(carteira.getId(), inter.getId()))
            .get().extracting(PortfolioAsset::getId).isEqualTo(posicaoInter.getId());
    }

    @Test
    @DisplayName("NULLS NOT DISTINCT: duas posicoes do mesmo ativo sem titulo violam a unicidade")
    void unicidadeComNoteIdNulo() {
        repository.saveAndFlush(Fixtures.position(carteira, acao));

        assertThatThrownBy(() -> repository.saveAndFlush(Fixtures.position(carteira, acao)))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_portfolio_assets_position");
    }

    @Test
    @DisplayName("A mesma posicao em carteiras diferentes e permitida")
    void unicidadeEPorCarteira() {
        repository.saveAndFlush(Fixtures.position(carteira, acao));
        repository.saveAndFlush(Fixtures.position(carteiraDoOutro, acao));

        assertThat(repository.findByPortfolioId(carteira.getId())).hasSize(1);
        assertThat(repository.findByPortfolioId(carteiraDoOutro.getId())).hasSize(1);
    }

    @Test
    @DisplayName("findAllByPortfolioIdAndAssetId traz todos os titulos do ativo, com a nota carregada")
    void titulosDoAtivo() {
        repository.save(Fixtures.position(carteira, cdb, nota(dono, "BTG")));
        repository.save(Fixtures.position(carteira, cdb, nota(dono, "Inter")));
        repository.save(Fixtures.position(carteira, acao));
        em.flush();
        em.clear();

        List<PortfolioAsset> titulos = repository.findAllByPortfolioIdAndAssetId(carteira.getId(), cdb.getId());

        assertThat(titulos).hasSize(2);
        assertThat(titulos).allSatisfy(pa -> assertThat(pa.getNote().getIssuer().getLegalName()).isNotBlank());
    }

    @Test
    @DisplayName("findAllByPortfolioIdWithAssetEagerly traz renda variavel (sem nota) e renda fixa juntas")
    void listagemDaCarteiraNaoPerdeRendaVariavel() {
        repository.save(Fixtures.position(carteira, acao));
        repository.save(Fixtures.position(carteira, cdb, nota(dono, "BTG")));
        em.flush();
        em.clear();

        List<PortfolioAsset> posicoes = repository.findAllByPortfolioIdWithAssetEagerly(carteira.getId());

        assertThat(posicoes).hasSize(2);
        assertThat(posicoes).anySatisfy(pa -> assertThat(pa.getNote()).isNull());
        assertThat(posicoes).allSatisfy(pa -> assertThat(pa.getAsset().getTicker()).isNotBlank());
    }

    @Test
    @DisplayName("findOwnedById devolve a posicao ao dono, com o tipo do ativo carregado")
    void posicaoEscopadaPeloDono() {
        PortfolioAsset posicao = repository.save(Fixtures.position(carteira, acao));
        em.flush();
        em.clear();

        Optional<PortfolioAsset> achada = repository.findOwnedById(dono.getId(), posicao.getId());

        assertThat(achada).isPresent();
        assertThat(achada.get().getAsset().getAssetType().getName()).isEqualTo("ACAO");
    }

    @Test
    @DisplayName("findOwnedById nao devolve a posicao de outro usuario")
    void posicaoDeOutroDonoNaoSai() {
        PortfolioAsset posicao = repository.saveAndFlush(Fixtures.position(carteira, acao));

        assertThat(repository.findOwnedById(outro.getId(), posicao.getId())).isEmpty();
        assertThat(repository.findOwnedById(dono.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findAllByAssetIdIn atravessa carteiras — e o que a atualizacao de cotacao usa")
    void posicoesPorAtivoEmTodasAsCarteiras() {
        repository.save(Fixtures.position(carteira, acao));
        repository.save(Fixtures.position(carteiraDoOutro, acao));
        em.flush();
        em.clear();

        assertThat(repository.findAllByAssetIdIn(List.of(acao.getId()))).hasSize(2);
    }
}
