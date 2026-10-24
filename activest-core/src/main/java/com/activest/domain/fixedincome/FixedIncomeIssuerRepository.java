package com.activest.domain.fixedincome;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FixedIncomeIssuerRepository extends JpaRepository<FixedIncomeIssuer, Long> {

    Optional<FixedIncomeIssuer> findByCnpjRoot(String cnpjRoot);

    /** Todas as raizes do cadastro (as duas linhas fixas nao tem). */
    @Query("select i.cnpjRoot from FixedIncomeIssuer i where i.cnpjRoot is not null")
    List<String> findAllCnpjRoots();

    /** Raizes das empresas de um grupo, as ativas primeiro: o logo do grupo quando a emissora e a lider nao tem. */
    @Query("select i.cnpjRoot from FixedIncomeIssuer i join i.group g where g.id = :groupId and i.cnpjRoot is not null "
        + "order by case when i.status = 'ATIVA' then 0 else 1 end, i.cnpjRoot")
    List<String> findCnpjRootsByGroupId(@Param("groupId") Long groupId);

    /** As linhas fixas (TESOURO, NAO_LISTADA): uma de cada, pelo indice parcial da V54. */
    Optional<FixedIncomeIssuer> findFirstByKind(FixedIncomeIssuerKind kind);

    long countByKind(FixedIncomeIssuerKind kind);

    /**
     * Busca dos seletores (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 6): pelos tipos aceitos, por nome do
     * grupo, razao social, nome comercial ou raiz do CNPJ. Vivas antes das mortas; "nao listada" sempre por ultimo
     * (quem chama a acrescenta).
     */
    @Query("""
        SELECT i FROM FixedIncomeIssuer i LEFT JOIN FETCH i.group g
        WHERE i.kind IN :kinds
          AND (:q = ''
               OR LOWER(i.legalName) LIKE CONCAT('%', :q, '%')
               OR LOWER(COALESCE(i.tradeName, '')) LIKE CONCAT('%', :q, '%')
               OR LOWER(COALESCE(g.name, '')) LIKE CONCAT('%', :q, '%')
               OR COALESCE(i.cnpjRoot, '') LIKE CONCAT(:q, '%'))
        ORDER BY CASE WHEN i.status = 'ATIVA' THEN 0 ELSE 1 END,
                 LOWER(COALESCE(g.name, COALESCE(i.tradeName, i.legalName))), LOWER(i.legalName)
        """)
    List<FixedIncomeIssuer> search(@Param("kinds") Collection<FixedIncomeIssuerKind> kinds, @Param("q") String q,
                                   Pageable page);
}
