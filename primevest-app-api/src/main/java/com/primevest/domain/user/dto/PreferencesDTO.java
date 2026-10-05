package com.primevest.domain.user.dto;

import com.primevest.domain.user.UserTheme;
import jakarta.validation.constraints.NotNull;

/** {@code PUT /users/me/preferences}: o tema (D13). */
public record PreferencesDTO(@NotNull(message = "Informe o tema.") UserTheme theme) {}
