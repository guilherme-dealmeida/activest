package com.primevest.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Passo 1 do cadastro (docs/planos/PLANO_CADASTRO_USUARIO.md, 3.1). Celular e CPF opcionais (D6, D18).
 * Senha de 8 a 72 caracteres: o bcrypt ignora o que passa de 72 bytes.
 */
public record SignupStartDTO(
    @NotBlank(message = "Informe o nome.") @Size(max = 255, message = "Nome muito longo.") String name,
    @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.")
    @Size(max = 255, message = "E-mail muito longo.") String email,
    @Size(max = 20, message = "Celular inválido.") String phone,
    @Size(max = 14, message = "CPF inválido.") String cpf,
    @NotBlank(message = "Informe a senha.") @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres.")
    String password,
    @NotNull(message = "É preciso aceitar os Termos de Uso e a Política de Privacidade.")
    @AssertTrue(message = "É preciso aceitar os Termos de Uso e a Política de Privacidade.") Boolean acceptTerms
) {
    @Override
    public String toString() {
        return "SignupStartDTO[email=" + email + ", password=****, cpf=****]";
    }
}
