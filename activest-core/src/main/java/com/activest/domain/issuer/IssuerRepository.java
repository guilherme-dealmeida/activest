package com.activest.domain.issuer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssuerRepository extends JpaRepository<Issuer, Long> {

    Optional<Issuer> findByCnpj(String cnpj);

    Optional<Issuer> findByCvmCode(Integer cvmCode);

    List<Issuer> findByKind(IssuerKind kind);

    long countByKind(IssuerKind kind);
}
