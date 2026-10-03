package com.primevest.domain.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetTermsRepository extends JpaRepository<AssetTerms, Long> {
}
