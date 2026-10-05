package com.primevest.domain.auth.mail;

import com.primevest.domain.auth.verification.VerificationPurpose;

/**
 * E-mails da conta (docs/planos/PLANO_CADASTRO_USUARIO.md, 3.4). Uma interface e a implementação
 * SMTP ({@link SmtpAccountMailer}): no DEV o SMTP é o Mailpit, que guarda tudo e não envia para fora.
 */
public interface AccountMailer {

    /** Código de verificação. Falha no envio vira erro para quem pediu — sem o e-mail o código não serve. */
    void sendCode(VerificationPurpose purpose, String to, String name, String code, long validMinutes);

    /** Aviso sem código. Falha no envio só vai para o log: o que o motivou já aconteceu. */
    void sendNotice(AccountNotice notice, String to, String name);
}
