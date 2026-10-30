package com.activest.domain.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Dados do exterior por ativo (V15). */
@Repository
public interface ForeignListingRepository extends JpaRepository<ForeignListing, Long> {

    Optional<ForeignListing> findByCompositeFigi(String compositeFigi);

    Optional<ForeignListing> findBySecSeriesId(String secSeriesId);
}
