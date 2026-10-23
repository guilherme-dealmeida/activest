package com.activest.domain.issuer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnitCompositionRepository extends JpaRepository<UnitComposition, UnitComposition.Key> {

    List<UnitComposition> findByAssetId(Long assetId);
}
