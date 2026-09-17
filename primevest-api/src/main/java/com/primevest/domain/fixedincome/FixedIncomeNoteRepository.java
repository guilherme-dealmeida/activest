package com.primevest.domain.fixedincome;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FixedIncomeNoteRepository extends JpaRepository<FixedIncomeNote, UUID> {

    List<FixedIncomeNote> findByUserIdOrderByMaturityDateAsc(UUID userId);

    List<FixedIncomeNote> findByUserIdAndAssetId(UUID userId, Long assetId);

    /**
     * Busca escopada pelo dono. Carregar a nota só pelo id abriria a porta para
     * um usuário lançar transação sobre o papel de outro — o id vem do cliente.
     */
    Optional<FixedIncomeNote> findByIdAndUserId(UUID id, UUID userId);
}
