package com.primevest.domain.auth.mail;

import com.primevest.domain.auth.verification.VerificationPurpose;
import com.primevest.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * {@link AccountMailer} por SMTP ({@code spring.mail.*}, do {@code .env}). Sem configuração, aponta para o
 * Mailpit do compose ({@code http://localhost:8025} mostra o que foi enviado).
 */
@Slf4j
@Component
public class SmtpAccountMailer implements AccountMailer {

    private final JavaMailSender sender;
    private final String from;
    private final String appUrl;

    public SmtpAccountMailer(JavaMailSender sender,
                             @Value("${primevest.auth.mail-from:PrimeVest <nao-responda@primevest.local>}") String from,
                             @Value("${primevest.auth.app-url:http://localhost:5173}") String appUrl) {
        this.sender = sender;
        this.from = from;
        this.appUrl = appUrl;
    }

    @Override
    public void sendCode(VerificationPurpose purpose, String to, String name, String code, long validMinutes) {
        try {
            enviar(to, MailTemplates.code(purpose, name, code, validMinutes, appUrl));
        } catch (MailException | MessagingException e) {
            log.warn("Falha ao enviar o código {} para {}: {}", purpose, mascarar(to), e.getMessage());
            throw new BusinessException("Não foi possível enviar o e-mail agora. Tente novamente em instantes.");
        }
    }

    @Override
    public void sendNotice(AccountNotice notice, String to, String name) {
        try {
            enviar(to, MailTemplates.notice(notice, name, appUrl));
        } catch (MailException | MessagingException e) {
            log.warn("Falha ao enviar o aviso {} para {}: {}", notice, mascarar(to), e.getMessage());
        }
    }

    private void enviar(String to, MailTemplates.Mail mail) throws MessagingException {
        MimeMessage mensagem = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(mail.subject());
        helper.setText(mail.text(), mail.html());
        sender.send(mensagem);
    }

    private static String mascarar(String email) {
        return com.primevest.domain.auth.Emails.mask(email);
    }
}
