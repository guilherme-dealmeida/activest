package com.activest.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {

    /** Só a data, sem a imagem: é o que o perfil precisa para montar a versão da foto. */
    @Query("SELECT a.updatedAt FROM UserAvatar a WHERE a.userId = :userId")
    Optional<LocalDateTime> findUpdatedAt(@Param("userId") UUID userId);
}
