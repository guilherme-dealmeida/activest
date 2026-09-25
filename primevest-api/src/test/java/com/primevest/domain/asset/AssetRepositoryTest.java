package com.primevest.domain.asset;

import com.primevest.support.CatalogIds;
import com.primevest.support.JpaTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssetRepositoryTest extends JpaTestBase {

    @Autowired private AssetRepository repository;
    @Autowired private AssetTypeRepository assetTypeRepository;

    /** O teto do autocomplete e do servidor: 50, sem Sort (o @Query ja ordena). */
    private static final PageRequest TETO = PageRequest.of(0, 50);

    @Test
    @DisplayName("A busca corta em 50 no servidor, e Pageable sem Sort nao quebra o @Query que ja tem ORDER BY")
    void tetoDeCinquenta() {
        List<Asset> resultado = repository.searchByTickerOrName("a", TETO);

        assertThat(resultado).hasSize(50);
    }

    @Test
    @DisplayName("Ticker exato vem primeiro, na frente dos que so comecam igual")
    void tickerExatoPrimeiro() {
        List<Asset> resultado = repository.searchByTickerOrName("PETR4", TETO);

        assertThat(resultado.get(0).getTicker()).isEqualTo("PETR4");
    }

    @Test
    @DisplayName("Titulo ja vencido fica por ultimo — quem digita SELIC quer o que ainda existe")
    void vencidosPorUltimo() {
        List<Asset> resultado = repository.searchByTickerOrName("SELIC", TETO);

        List<String> tickers = resultado.stream().map(Asset::getTicker).toList();
        assertThat(tickers).contains("Tesouro Selic 2031");

        int primeiroVencido = -1;
        int ultimoEmAberto = -1;
        for (int i = 0; i < resultado.size(); i++) {
            AssetTerms terms = resultado.get(i).getTerms();
            boolean vencido = terms != null && terms.getMaturityDate() != null
                && terms.getMaturityDate().isBefore(LocalDate.now());
            if (vencido && primeiroVencido < 0) {
                primeiroVencido = i;
            }
            if (!vencido) {
                ultimoEmAberto = i;
            }
        }
        assertThat(primeiroVencido).isGreaterThan(-1);
        assertThat(ultimoEmAberto).isLessThan(primeiroVencido);
    }

    @Test
    @DisplayName("A busca por tipo restringe o autocomplete ao tipo escolhido")
    void buscaRestritaAoTipo() {
        AssetType fii = CatalogIds.type(assetTypeRepository, "FII");

        List<Asset> resultado = repository.searchByTypeAndTickerOrName(fii.getId(), "HG", TETO);

        assertThat(resultado).isNotEmpty();
        assertThat(resultado).allSatisfy(a -> assertThat(a.getAssetType().getId()).isEqualTo(fii.getId()));
    }

    @Test
    @DisplayName("findByTickerWithTerms traz os parametros do papel no Tesouro e nulo na acao")
    void termsNoMesmoSelect() {
        Asset tesouro = repository.findByTickerWithTerms(CatalogIds.TESOURO_IPCA_2050).orElseThrow();
        Asset acao = repository.findByTickerWithTerms(CatalogIds.ACAO).orElseThrow();

        assertThat(tesouro.getTerms()).isNotNull();
        assertThat(tesouro.getTerms().getMaturityDate()).isEqualTo(LocalDate.of(2050, 8, 15));
        assertThat(acao.getTerms()).isNull();
    }

    @Test
    @DisplayName("Busca por nome tambem encontra — o catalogo de renda fixa nao tem ticker reconhecivel")
    void buscaPorNome() {
        List<Asset> resultado = repository.searchByTickerOrName("Certificado", TETO);

        assertThat(resultado).extracting(Asset::getTicker).contains(CatalogIds.CDB);
    }

    @Test
    @DisplayName("Ativo inativo (deslistado, V14) nao aparece na busca; o novo da V15 aparece com nome da BRAPI")
    void inativoForaDaBusca() {
        assertThat(repository.searchByTickerOrName("AHEB3F", TETO)).isEmpty();
        assertThat(repository.findByTicker("AHEB3F")).get().extracting(Asset::isActive).isEqualTo(false);
        assertThat(repository.searchByTickerOrName("CEEB5", TETO)).extracting(Asset::getTicker).contains("CEEB5");
    }

    @Test
    @DisplayName("V13: nome das acoes com acento, vindo da BRAPI (AALR3)")
    void nomeComAcento() {
        assertThat(repository.findByTicker("AALR3")).get().extracting(Asset::getName)
            .isEqualTo("ALLIANÇA SAÚDE E PARTICIPAÇÕES S.A.");
    }

    @Test
    @DisplayName("V15: INHF11 e FII hibrido; V17/V18/V19: ETF, BDR e cripto no catalogo")
    void catalogoNovo() {
        assertThat(repository.findByTicker("INHF11")).get().extracting(a -> a.getAssetType().getName()).isEqualTo("FII");
        assertThat(repository.findByTicker("BOVA11")).get().extracting(a -> a.getAssetType().getName()).isEqualTo("ETF");
        assertThat(repository.findByTicker("BOVA11").orElseThrow().getCurrentPrice()).isPositive();
        assertThat(repository.findByTicker("AAPL34")).get().extracting(a -> a.getAssetType().getName()).isEqualTo("BDR");
        assertThat(repository.findByTicker("BTC")).get().extracting(Asset::getPriceSourceRef).isEqualTo("bitcoin");
    }
}
