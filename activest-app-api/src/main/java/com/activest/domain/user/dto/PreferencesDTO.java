package com.activest.domain.user.dto;

import com.activest.domain.user.UserFontSize;
import com.activest.domain.user.UserTheme;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

/**
 * {@code PUT /users/me/preferences}: o tema (docs/planos/PLANO_CADASTRO_USUARIO.md, D13) e o tamanho da tela
 * (docs/planos/PLANO_CUSTOMIZAR_TAMANHOS.md, D4). Gravacao parcial: o que vier nulo nao muda; pelo menos um.
 */
public record PreferencesDTO(UserTheme theme, UserFontSize fontSize) {

    @JsonIgnore
    @AssertTrue(message = "Informe o tema ou o tamanho.")
    public boolean isAnyPreference() {
        return theme != null || fontSize != null;
    }
}
