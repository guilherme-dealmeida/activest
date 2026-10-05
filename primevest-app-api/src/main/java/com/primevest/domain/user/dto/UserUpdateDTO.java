package com.primevest.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /users/me}: o perfil.
 *
 * <ul>
 *   <li>{@code phone} — o valor completo do formulário: nulo ou vazio tira o celular;</li>
 *   <li>{@code cpf} — a API só devolve o CPF mascarado, então a tela só o manda quando o usuário o
 *       digita: <b>nulo = não mexe</b>; vazio = tira; número = troca.</li>
 * </ul>
 * O e-mail não está aqui: troca com código ({@code POST /users/me/email}).
 */
public record UserUpdateDTO(
    @NotBlank(message = "Informe o nome.") @Size(max = 255, message = "Nome muito longo.") String name,
    @Size(max = 20, message = "Celular inválido.") String phone,
    @Size(max = 14, message = "CPF inválido.") String cpf
) {
    @Override
    public String toString() {
        return "UserUpdateDTO[name=" + name + ", phone=" + phone + ", cpf=****]";
    }
}
