package com.activest.domain.asset;

import com.activest.support.CatalogIds;
import com.activest.support.JpaTestBase;
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
    private static final PageRequest CEILING = PageRequest.of(0, 50);

    @Test
    @DisplayName("A busca corta em 50 no servidor, e Pageable sem Sort nao quebra o @Query que ja tem ORDER BY")
    void ceilingOfFifty() {
        List<Asset> result = repository.searchByTickerOrName("a", CEILING);

        assertThat(result).hasSize(50);
    }

    @Test
    @DisplayName("Ticker exato vem primeiro, na frente dos que so comecam igual")
    void exactTickerFirst() {
        List<Asset> result = repository.searchByTickerOrName("PETR4", CEILING);

        assertThat(result.get(0).getTicker()).isEqualTo("PETR4");
    }

    @Test
    @DisplayName("Titulo ja vencido fica por ultimo — quem digita SELIC quer o que ainda existe")
    void maturedLast() {
        List<Asset> result = repository.searchByTickerOrName("SELIC", CEILING);

        List<String> tickers = result.stream().map(Asset::getTicker).toList();
        assertThat(tickers).contains("Tesouro Selic 2031");

        int firstMatured = -1;
        int lastOpen = -1;
        for (int i = 0; i < result.size(); i++) {
            AssetTerms terms = result.get(i).getTerms();
            boolean matured = terms != null && terms.getMaturityDate() != null
                && terms.getMaturityDate().isBefore(LocalDate.now());
            if (matured && firstMatured < 0) {
                firstMatured = i;
            }
            if (!matured) {
                lastOpen = i;
            }
        }
        assertThat(firstMatured).isGreaterThan(-1);
        assertThat(lastOpen).isLessThan(firstMatured);
    }

    @Test
    @DisplayName("A busca por tipo restringe o autocomplete ao tipo escolhido")
    void searchRestrictedToType() {
        AssetType fii = CatalogIds.type(assetTypeRepository, "FII");

        List<Asset> result = repository.searchByTypeAndTickerOrName(fii.getId(), "HG", CEILING);

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(a -> assertThat(a.getAssetType().getId()).isEqualTo(fii.getId()));
    }

    @Test
    @DisplayName("findByTickerWithTerms traz os parametros do papel no Tesouro e nulo na acao")
    void termsInSameSelect() {
        Asset treasury = repository.findByTickerWithTerms(CatalogIds.TREASURY_IPCA_2050).orElseThrow();
        Asset stock = repository.findByTickerWithTerms(CatalogIds.STOCK).orElseThrow();

        assertThat(treasury.getTerms()).isNotNull();
        assertThat(treasury.getTerms().getMaturityDate()).isEqualTo(LocalDate.of(2050, 8, 15));
        assertThat(stock.getTerms()).isNull();
    }

    @Test
    @DisplayName("Busca por nome tambem encontra — o catalogo de renda fixa nao tem ticker reconhecivel")
    void searchByName() {
        List<Asset> result = repository.searchByTickerOrName("Certificado", CEILING);

        assertThat(result).extracting(Asset::getTicker).contains(CatalogIds.CDB);
    }

    @Test
    @DisplayName("Ativo inativo (deslistado, V14) nao aparece na busca; o novo da V15 aparece com nome da BRAPI")
    void inactiveOutOfSearch() {
        assertThat(repository.searchByTickerOrName("AHEB3F", CEILING)).isEmpty();
        assertThat(repository.findByTicker("AHEB3F")).get().extracting(Asset::isActive).isEqualTo(false);
        assertThat(repository.searchByTickerOrName("CEEB5", CEILING)).extracting(Asset::getTicker).contains("CEEB5");
    }

    @Test
    @DisplayName("V13: nome das acoes com acento, vindo da BRAPI (AALR3)")
    void nameWithAccent() {
        assertThat(repository.findByTicker("AALR3")).get().extracting(Asset::getName)
            .isEqualTo("ALLIANÇA SAÚDE E PARTICIPAÇÕES S.A.");
    }

    @Test
    @DisplayName("V15: INHF11 e FII hibrido; V17/V18/V19: ETF, BDR e cripto no catalogo")
    void newCatalog() {
        assertThat(repository.findByTicker("INHF11")).get().extracting(a -> a.getAssetType().getName()).isEqualTo("FII");
        assertThat(repository.findByTicker("BOVA11")).get().extracting(a -> a.getAssetType().getName()).isEqualTo("ETF");
        assertThat(repository.findByTicker("BOVA11").orElseThrow().getCurrentPrice()).isPositive();
        assertThat(repository.findByTicker("AAPL34")).get().extracting(a -> a.getAssetType().getName()).isEqualTo("BDR");
        assertThat(repository.findByTicker("BTC")).get().extracting(Asset::getPriceSourceRef).isEqualTo("bitcoin");
    }
}
