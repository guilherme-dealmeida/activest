package com.primevest.domain.strategy;

import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.AssetTypeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRepositoryTest extends JpaTestBase {

    @Autowired private StrategyRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private AssetTypeRepository assetTypeRepository;

    private User dono;
    private User outro;
    private AssetType acao;
    private AssetType fii;

    @BeforeEach
    void cenario() {
        dono = userRepository.save(Fixtures.user("dono@primevest.test"));
        outro = userRepository.save(Fixtures.user("outro@primevest.test"));
        acao = CatalogIds.type(assetTypeRepository, "ACAO");
        fii = CatalogIds.type(assetTypeRepository, "FII");
    }

    @Test
    @DisplayName("findByUserId traz so a estrategia do usuario")
    void escopoPorUsuario() {
        repository.save(Fixtures.strategy(dono, acao, "60.00"));
        repository.save(Fixtures.strategy(dono, fii, "40.00"));
        repository.save(Fixtures.strategy(outro, acao, "100.00"));

        assertThat(repository.findByUserId(dono.getId())).hasSize(2);
        assertThat(repository.findByUserId(outro.getId())).hasSize(1);
    }

    @Test
    @DisplayName("unique(user_id, asset_type_id): o mesmo tipo duas vezes para o mesmo usuario viola a unicidade")
    void tipoRepetidoViolaUnicidade() {
        repository.saveAndFlush(Fixtures.strategy(dono, acao, "60.00"));

        assertThatThrownBy(() -> repository.saveAndFlush(Fixtures.strategy(dono, acao, "40.00")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("O mesmo tipo em usuarios diferentes e permitido")
    void mesmoTipoEmUsuariosDiferentes() {
        repository.saveAndFlush(Fixtures.strategy(dono, acao, "60.00"));
        repository.saveAndFlush(Fixtures.strategy(outro, acao, "70.00"));

        assertThat(repository.findByUserIdAndAssetTypeId(dono.getId(), acao.getId()))
            .get().extracting(Strategy::getTargetPercentage)
            .satisfies(p -> assertThat(p).isEqualByComparingTo("60.00"));
        assertThat(repository.findByUserIdAndAssetTypeId(outro.getId(), acao.getId()))
            .get().extracting(Strategy::getTargetPercentage)
            .satisfies(p -> assertThat(p).isEqualByComparingTo("70.00"));
    }

    @Test
    @DisplayName("findByUserIdAndAssetTypeId nao acha a estrategia de outro dono")
    void buscaPorTipoEEscopada() {
        repository.saveAndFlush(Fixtures.strategy(outro, fii, "40.00"));

        assertThat(repository.findByUserIdAndAssetTypeId(dono.getId(), fii.getId())).isEmpty();
    }
}
