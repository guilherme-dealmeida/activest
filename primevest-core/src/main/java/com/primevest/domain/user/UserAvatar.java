package com.primevest.domain.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Foto de perfil enviada pelo usuário (V42): JPEG 512×512 já reduzido e sem metadados. 1:1 com
 * {@link User}, em tabela própria para a imagem não vir em toda leitura do usuário.
 */
@Entity
@Table(name = "user_avatars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private byte[] image;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
