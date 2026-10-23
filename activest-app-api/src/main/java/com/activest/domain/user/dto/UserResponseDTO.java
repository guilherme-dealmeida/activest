package com.activest.domain.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code GET /users/me} (docs/planos/PLANO_CADASTRO_USUARIO.md, 4.4). O CPF só sai mascarado.
 *
 * @param termsPending a versão aceita não é a atual: a tela abre o modal de novo aceite (D19)
 */
public record UserResponseDTO(
    UUID id,
    String name,
    String email,
    String avatarUrl,
    Long avatarVersion,
    String phone,
    String cpfMasked,
    boolean emailVerified,
    boolean hasPassword,
    boolean googleLinked,
    boolean admin,
    String theme,
    String termsVersion,
    LocalDateTime termsAcceptedAt,
    String currentTermsVersion,
    boolean termsPending,
    LocalDateTime createdAt
) {}
