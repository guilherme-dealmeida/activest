package com.activest.domain.strategy;

import com.activest.domain.asset.AssetType;
import com.activest.domain.asset.AssetTypeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRepositoryTest extends JpaTestBase {

    @Autowired private StrategyRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private AssetTypeRepository assetTypeRepository;

    private User owner;
    private User other;
    private AssetType stock;
    private AssetType fii;

    @BeforeEach
    void scenario() {
        owner = userRepository.save(Fixtures.user("dono@activest.test"));
        other = userRepository.save(Fixtures.user("outro@activest.test"));
        stock = CatalogIds.type(assetTypeRepository, "ACAO");
        fii = CatalogIds.type(assetTypeRepository, "FII");
    }

    @Test
    @DisplayName("findByUserId traz so a estrategia do usuario")
    void scopeByUser() {
        repository.save(Fixtures.strategy(owner, stock, "60.00"));
        repository.save(Fixtures.strategy(owner, fii, "40.00"));
        repository.save(Fixtures.strategy(other, stock, "100.00"));

        assertThat(repository.findByUserId(owner.getId())).hasSize(2);
        assertThat(repository.findByUserId(other.getId())).hasSize(1);
    }

    @Test
    @DisplayName("unique(user_id, asset_type_id): o mesmo tipo duas vezes para o mesmo usuario viola a unicidade")
    void repeatedTypeViolatesUniqueness() {
        repository.saveAndFlush(Fixtures.strategy(owner, stock, "60.00"));

        assertThatThrownBy(() -> repository.saveAndFlush(Fixtures.strategy(owner, stock, "40.00")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("O mesmo tipo em usuarios diferentes e permitido")
    void sameTypeForDifferentUsers() {
        repository.saveAndFlush(Fixtures.strategy(owner, stock, "60.00"));
        repository.saveAndFlush(Fixtures.strategy(other, stock, "70.00"));

        assertThat(repository.findByUserIdAndAssetTypeId(owner.getId(), stock.getId()))
            .get().extracting(Strategy::getTargetPercentage)
            .satisfies(p -> assertThat(p).isEqualByComparingTo("60.00"));
        assertThat(repository.findByUserIdAndAssetTypeId(other.getId(), stock.getId()))
            .get().extracting(Strategy::getTargetPercentage)
            .satisfies(p -> assertThat(p).isEqualByComparingTo("70.00"));
    }

    @Test
    @DisplayName("findByUserIdAndAssetTypeId nao acha a estrategia de outro dono")
    void searchByTypeIsScoped() {
        repository.saveAndFlush(Fixtures.strategy(other, fii, "40.00"));

        assertThat(repository.findByUserIdAndAssetTypeId(owner.getId(), fii.getId())).isEmpty();
    }
}
