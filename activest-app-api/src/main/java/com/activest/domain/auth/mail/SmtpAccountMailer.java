package com.activest.domain.auth.mail;

import com.activest.domain.auth.verification.VerificationPurpose;
import com.activest.exception.BusinessException;
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
                             @Value("${activest.auth.mail-from:Activest <contato@activest.com.br>}") String from,
                             @Value("${activest.auth.app-url:http://localhost:5173}") String appUrl) {
        this.sender = sender;
        this.from = from;
        this.appUrl = appUrl;
    }

    @Override
    public void sendCode(VerificationPurpose purpose, String to, String name, String code, long validMinutes) {
        try {
            sendMessage(to, MailTemplates.code(purpose, name, code, validMinutes, appUrl));
        } catch (MailException | MessagingException e) {
            log.warn("Falha ao enviar o código {} para {}: {}", purpose, maskEmail(to), e.getMessage());
            throw new BusinessException("Não foi possível enviar o e-mail agora. Tente novamente em instantes.");
        }
    }

    @Override
    public void sendNotice(AccountNotice notice, String to, String name) {
        try {
            sendMessage(to, MailTemplates.notice(notice, name, appUrl));
        } catch (MailException | MessagingException e) {
            log.warn("Falha ao enviar o aviso {} para {}: {}", notice, maskEmail(to), e.getMessage());
        }
    }

    private void sendMessage(String to, MailTemplates.Mail mail) throws MessagingException {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(mail.subject());
        helper.setText(mail.text(), mail.html());
        sender.send(message);
    }

    private static String maskEmail(String email) {
        return com.activest.domain.auth.Emails.mask(email);
    }
}
