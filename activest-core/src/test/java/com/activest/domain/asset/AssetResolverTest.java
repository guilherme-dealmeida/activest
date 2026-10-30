package com.activest.domain.asset;

import com.activest.exception.BusinessException;
import com.activest.support.CatalogIds;
import com.activest.support.JpaTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V13 (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, D3 e P2): o ticker é único por (ticker, tipo, data de saída). ZZUSD
 * é um ticker fictício cadastrado aqui como ação do exterior e como cripto — o mesmo caso de META.
 */
class AssetResolverTest extends JpaTestBase {

    private static final String TICKER = "ZZUSD";

    @Autowired private AssetRepository repository;
    @Autowired private AssetTypeRepository types;

    private AssetResolver resolver;
    private Asset stock;
    private Asset crypto;

    @BeforeEach
    void scenario() {
        resolver = new AssetResolver(repository);
        stock = repository.saveAndFlush(asset(AssetTypeNames.STOCK, null));
        crypto = repository.saveAndFlush(asset(AssetTypeNames.CRIPTO, null));
    }

    private Asset asset(String type, LocalDate delistedOn) {
        return Asset.builder().ticker(TICKER).name(TICKER + " " + type).assetType(CatalogIds.type(types, type))
            .currencyCode("USD").active(delistedOn == null).delistedOn(delistedOn).build();
    }

    @Test
    @DisplayName("Com o tipo, cada homônimo é achado; sem o tipo, o ticker é ambíguo")
    void homonymsByType() {
        assertThat(resolver.find(TICKER, AssetTypeNames.STOCK)).get().extracting(Asset::getId).isEqualTo(stock.getId());
        assertThat(resolver.find(TICKER, AssetTypeNames.CRIPTO)).get().extracting(Asset::getId).isEqualTo(crypto.getId());
        assertThat(resolver.findUnique(TICKER)).isEmpty();
        assertThat(resolver.isAmbiguous(TICKER)).isTrue();
    }

    @Test
    @DisplayName("A fonte procura só nos tipos dela: a Binance acha a cripto, a B3 não acha nada")
    void bySourceTypes() {
        assertThat(resolver.findInTypes(TICKER, Set.of(AssetTypeNames.CRIPTO))).get()
            .extracting(Asset::getId).isEqualTo(crypto.getId());
        assertThat(resolver.findInTypes(TICKER, AssetTypeNames.B3_LISTED)).isEmpty();
        assertThat(resolver.findInTypes(TICKER, Set.of(AssetTypeNames.CRIPTO, AssetTypeNames.STOCK))).isEmpty();
    }

    @Test
    @DisplayName("O pedido sem o tipo pede o tipo; o ticker em minúsculas é achado em maiúsculas")
    void requireMessages() {
        assertThatThrownBy(() -> resolver.require(TICKER, null, null))
            .isInstanceOf(BusinessException.class).hasMessageContaining("informe o tipo");
        assertThatThrownBy(() -> resolver.require("ZZNADA", AssetTypeNames.STOCK, null))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Ativo não encontrado no catálogo: ZZNADA");
        assertThat(resolver.require("zzusd", "cripto", null).getId()).isEqualTo(crypto.getId());
    }

    @Test
    @DisplayName("Ticker reaproveitado (P2): a data da transação escolhe o papel que estava listado naquele dia")
    void delistedByDate() {
        Asset old = repository.saveAndFlush(asset(AssetTypeNames.STOCK, LocalDate.of(2015, 6, 30)));

        assertThat(resolver.find(TICKER, AssetTypeNames.STOCK, LocalDate.of(2014, 1, 2))).get()
            .extracting(Asset::getId).isEqualTo(old.getId());
        assertThat(resolver.find(TICKER, AssetTypeNames.STOCK, LocalDate.of(2015, 6, 30))).get()
            .extracting(Asset::getId).isEqualTo(old.getId());
        assertThat(resolver.find(TICKER, AssetTypeNames.STOCK, LocalDate.of(2020, 1, 2))).get()
            .extracting(Asset::getId).isEqualTo(stock.getId());
        assertThat(resolver.find(TICKER, AssetTypeNames.STOCK)).get().extracting(Asset::getId).isEqualTo(stock.getId());
    }

    @Test
    @DisplayName("O mesmo (ticker, tipo) listado duas vezes é recusado pelo banco (uk_assets_ticker_type)")
    void uniqueByType() {
        assertThatThrownBy(() -> repository.saveAndFlush(asset(AssetTypeNames.STOCK, null)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Papel com data de saída tem de estar inativo (ck_assets_delisted_inactive)")
    void delistedIsInactive() {
        Asset a = asset(AssetTypeNames.REIT, LocalDate.of(2015, 6, 30));
        a.setActive(true);

        assertThatThrownBy(() -> repository.saveAndFlush(a)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
