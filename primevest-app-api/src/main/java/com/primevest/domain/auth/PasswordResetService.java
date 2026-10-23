package com.primevest.domain.auth;

import com.primevest.domain.auth.dto.CodeSentDTO;
import com.primevest.domain.auth.dto.PasswordResetDTO;
import com.primevest.domain.auth.mail.AccountMailer;
import com.primevest.domain.auth.mail.AccountNotice;
import com.primevest.domain.auth.verification.PendingCode;
import com.primevest.domain.auth.verification.RateLimiter;
import com.primevest.domain.auth.verification.VerificationCodeService;
import com.primevest.domain.auth.verification.VerificationPurpose;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * "Esqueci minha senha" (docs/planos/PLANO_CADASTRO_USUARIO.md, 3.5; D4). Código por e-mail; a nova
 * senha derruba as outras sessões (D15). Serve também a quem só tinha Google e quer uma senha.
 *
 * <p>Anti-enumeração (D10): e-mail sem conta recebe a mesma resposta, e o pedido é uma fachada sem
 * e-mail enviado.</p>
 */
@Service
public class PasswordResetService {

    static final int MAX_REQUESTS_PER_IP = 20;

    private final VerificationCodeService codes;
    private final AccountMailer mailer;
    private final RateLimiter rateLimiter;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(VerificationCodeService codes, AccountMailer mailer, RateLimiter rateLimiter,
                                UserRepository users, PasswordEncoder passwordEncoder) {
        this.codes = codes;
        this.mailer = mailer;
        this.rateLimiter = rateLimiter;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public CodeSentDTO forgot(String rawEmail, String ip) {
        rateLimiter.hit("forgot:ip:" + ip, MAX_REQUESTS_PER_IP, Duration.ofHours(1),
            "Muitos pedidos a partir deste endereço. Tente de novo mais tarde.");
        String email = Emails.normalize(rawEmail);
        String requestId = VerificationCodeService.newRequestId();
        var user = users.findByEmail(email);
        if (user.isEmpty()) {
            codes.issue(requestId, VerificationPurpose.RESET_PASSWORD, email, Map.of(), true);
        } else {
            VerificationCodeService.Issued issued = codes.issue(requestId, VerificationPurpose.RESET_PASSWORD, email,
                Map.of("userId", user.get().getId().toString()), false);
            mailer.sendCode(VerificationPurpose.RESET_PASSWORD, email, user.get().getName(), issued.code(),
                codes.expiresInSeconds() / 60);
        }
        return new CodeSentDTO(requestId, Emails.mask(email), codes.resendInSeconds(), codes.expiresInSeconds());
    }

    public CodeSentDTO resend(String requestId) {
        VerificationCodeService.Issued issued = codes.resend(requestId, VerificationPurpose.RESET_PASSWORD);
        PendingCode p = issued.pending();
        if (!p.decoy()) {
            User user = users.findById(UUID.fromString(p.get("userId")))
                .orElseThrow(() -> new BusinessException("O código expirou ou já foi usado. Peça um novo código."));
            mailer.sendCode(VerificationPurpose.RESET_PASSWORD, p.email(), user.getName(), issued.code(),
                codes.expiresInSeconds() / 60);
        }
        return new CodeSentDTO(requestId, Emails.mask(p.email()), codes.resendInSeconds(), codes.expiresInSeconds());
    }

    @Transactional
    public void reset(PasswordResetDTO dto) {
        PendingCode p = codes.verify(dto.requestId(), VerificationPurpose.RESET_PASSWORD, dto.code());
        User user = users.findById(UUID.fromString(p.get("userId")))
            .orElseThrow(() -> new BusinessException("O código expirou ou já foi usado. Peça um novo código."));
        setPassword(user, passwordEncoder.encode(dto.newPassword()));
        // Quem recebeu o código no e-mail provou que é dono dele.
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(LocalDateTime.now());
        }
        users.save(user);
        mailer.sendNotice(AccountNotice.PASSWORD_CHANGED, user.getEmail(), user.getName());
    }

    /**
     * Troca o hash e marca {@code password_changed_at} truncado no segundo: o {@code iat} do JWT tem
     * resolução de segundos, e o token emitido logo depois da troca precisa continuar valendo.
     */
    public static void setPassword(User user, String hash) {
        user.setPasswordHash(hash);
        user.setPasswordChangedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
