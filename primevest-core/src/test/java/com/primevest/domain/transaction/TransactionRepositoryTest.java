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

    private User dono;
    private User outro;
    private Portfolio carteira;
    private Asset acao;
    private Asset tesouro;
    private PortfolioAsset posicaoDaAcao;

    @BeforeEach
    void cenario() {
        dono = userRepository.save(Fixtures.user("dono@primevest.test"));
        outro = userRepository.save(Fixtures.user("outro@primevest.test"));
        carteira = portfolioRepository.save(Fixtures.portfolio(dono));
        acao = CatalogIds.asset(assetRepository, CatalogIds.ACAO);
        tesouro = CatalogIds.asset(assetRepository, CatalogIds.TESOURO_IPCA_2050);
        posicaoDaAcao = positionRepository.save(Fixtures.position(carteira, acao));
    }

    private PortfolioAsset tituloDoTesouro(LocalDate compra) {
        FixedIncomeNote note = noteRepository.save(Fixtures.note(dono, tesouro, Fixtures.issuer(issuerRepository, "Tesouro Nacional"),
            LocalDate.of(2050, 8, 15), RateType.HIBRIDO, Indexer.IPCA, new BigDecimal("7.0900")));
        note.setPurchaseDate(compra);
        return positionRepository.save(Fixtures.position(carteira, tesouro, note));
    }

    @Test
    @DisplayName("findOwnedById devolve a transacao ao dono, com ativo, tipo e categoria carregados")
    void transacaoEscopadaPeloDono() {
        Transaction compra = repository.save(Fixtures.buy(posicaoDaAcao, LocalDate.of(2026, 9, 1), "10", "30.00"));
        em.flush();
        em.clear();

        Transaction achada = repository.findOwnedById(dono.getId(), compra.getId()).orElseThrow();

        assertThat(achada.getPortfolioAsset().getAsset().getTicker()).isEqualTo(CatalogIds.ACAO);
        assertThat(achada.getPortfolioAsset().getAsset().getAssetType().getName()).isEqualTo("ACAO");
    }

    @Test
    @DisplayName("findOwnedById nao devolve transacao de outro usuario — 'nao encontrada', nunca 'sem permissao'")
    void transacaoDeOutroDonoNaoSai() {
        Transaction compra = repository.saveAndFlush(
            Fixtures.buy(posicaoDaAcao, LocalDate.of(2026, 9, 1), "10", "30.00"));

        assertThat(repository.findOwnedById(outro.getId(), compra.getId())).isEmpty();
        assertThat(repository.findOwnedById(dono.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findAllTransactionsByUserId isola usuarios e nao perde renda variavel no LEFT JOIN do titulo")
    void listagemPorUsuario() {
        repository.save(Fixtures.buy(posicaoDaAcao, LocalDate.of(2026, 9, 1), "10", "30.00"));
        PortfolioAsset titulo = tituloDoTesouro(LocalDate.of(2026, 9, 1));
        repository.save(Fixtures.buy(titulo, LocalDate.of(2026, 9, 1), "3", "830.07"));

        Portfolio carteiraDoOutro = portfolioRepository.save(Fixtures.portfolio(outro));
        PortfolioAsset posicaoDoOutro = positionRepository.save(Fixtures.position(carteiraDoOutro, acao));
        repository.save(Fixtures.buy(posicaoDoOutro, LocalDate.of(2026, 9, 2), "5", "31.00"));
        em.flush();
        em.clear();

        List<Transaction> minhas = repository.findAllTransactionsByUserId(dono.getId());

        assertThat(minhas).hasSize(2);
        assertThat(minhas).anySatisfy(tx -> assertThat(tx.getNote()).isNull());
        assertThat(minhas).anySatisfy(tx -> assertThat(tx.getNote()).isNotNull());
        assertThat(repository.findAllTransactionsByUserId(outro.getId())).hasSize(1);
    }

    @Test
    @DisplayName("findByPortfolioIdAndAssetId traz as transacoes de TODOS os titulos do ativo")
    void transacoesDoAtivoInteiro() {
        PortfolioAsset titulo1 = tituloDoTesouro(LocalDate.of(2026, 9, 1));
        PortfolioAsset titulo2 = tituloDoTesouro(LocalDate.of(2026, 9, 15));
        repository.save(Fixtures.buy(titulo1, LocalDate.of(2026, 9, 1), "3", "830.07"));
        repository.save(Fixtures.buy(titulo2, LocalDate.of(2026, 9, 15), "4", "830.10"));
        repository.save(Fixtures.buy(posicaoDaAcao, LocalDate.of(2026, 9, 1), "10", "30.00"));
        em.flush();
        em.clear();

        List<Transaction> doAtivo = repository.findByPortfolioIdAndAssetId(carteira.getId(), tesouro.getId());

        assertThat(doAtivo).hasSize(2);
        assertThat(doAtivo).allSatisfy(tx ->
            assertThat(tx.getPortfolioAsset().getAsset().getId()).isEqualTo(tesouro.getId()));
    }

    @Test
    @DisplayName("findBySaleGroupId junta as partes da mesma venda")
    void partesDaMesmaVenda() {
        PortfolioAsset titulo1 = tituloDoTesouro(LocalDate.of(2026, 9, 1));
        PortfolioAsset titulo2 = tituloDoTesouro(LocalDate.of(2026, 9, 15));
        UUID grupo = UUID.randomUUID();

        Transaction parte1 = Fixtures.sell(titulo1, LocalDate.of(2026, 9, 17), "3", "850.00");
        parte1.setSaleGroupId(grupo);
        Transaction parte2 = Fixtures.sell(titulo2, LocalDate.of(2026, 9, 17), "2", "850.00");
        parte2.setSaleGroupId(grupo);
        repository.saveAll(List.of(parte1, parte2));
        repository.save(Fixtures.sell(posicaoDaAcao, LocalDate.of(2026, 9, 17), "1", "30.00"));
        em.flush();

        assertThat(repository.findBySaleGroupId(grupo)).hasSize(2);
    }

    @Test
    @DisplayName("findByPortfolioAssetIdOrderByTransactionDateDesc ordena da mais nova para a mais antiga")
    void ordenacaoPorData() {
        repository.save(Fixtures.buy(posicaoDaAcao, LocalDate.of(2026, 9, 1), "10", "30.00"));
        repository.save(Fixtures.buy(posicaoDaAcao, LocalDate.of(2026, 9, 10), "5", "31.00"));
        em.flush();

        List<Transaction> lista =
            repository.findByPortfolioAssetIdOrderByTransactionDateDesc(posicaoDaAcao.getId());

        assertThat(lista).extracting(Transaction::getTransactionDate)
            .containsExactly(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1));
    }
}
