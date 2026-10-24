package com.activest.domain.auth.verification;

/** Para que serve um código (3.2): um código vale só para o propósito com que foi emitido. */
public enum VerificationPurpose {
    SIGNUP,
    RESET_PASSWORD,
    CHANGE_EMAIL,
    DELETE_ACCOUNT
}
