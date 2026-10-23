package com.activest.domain.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetTypeRepository extends JpaRepository<AssetType, Long> {

    /**
     * Os tipos na ordem do cadastro (V2__seed_asset_types). O findAll() sem
     * ORDER BY devolve a ordem fisica da tabela, e no PostgreSQL um UPDATE
     * regrava a linha no fim: depois da V44, que atualiza o Tesouro Direto, ele
     * passou a vir por ultimo nas telas.
     */
    List<AssetType> findAllByOrderByIdAsc();
}
