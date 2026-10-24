package com.activest.domain.auth;

import com.activest.domain.auth.dto.CodeConfirmDTO;
import com.activest.domain.auth.dto.CodeSentDTO;
import com.activest.domain.auth.dto.SignupStartDTO;
import com.activest.domain.auth.dto.TokenResponseDTO;
import com.activest.domain.auth.mail.AccountMailer;
import com.activest.domain.auth.mail.AccountNotice;
import com.activest.domain.auth.verification.PendingCode;
import com.activest.domain.auth.verification.RateLimiter;
import com.activest.domain.auth.verification.VerificationCodeService;
import com.activest.domain.auth.verification.VerificationPurpose;
import com.activest.domain.user.User;
import com.activest.domain.user.UserCpfService;
import com.activest.domain.user.UserRepository;
import com.activest.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Cadastro com código por e-mail (docs/planos/PLANO_CADASTRO_USUARIO.md, 3.1–3.3).
 *
 * <p>O cadastro pendente fica no Redis até o código ser confirmado (D3): nome, e-mail, celular, o hash
 * bcrypt da senha e o CPF já cifrado. O usuário só nasce no {@link #confirm}. Quem desiste some sozinho
 * quando o pedido vence, e ninguém consegue "prender" o e-mail de outra pessoa.</p>
 *
 * <p>Anti-enumeração (D10): com um e-mail que já tem conta, a resposta é a mesma; o pedido vira uma
 * fachada que nenhum código confirma, e o dono do e-mail recebe o aviso "você já tem conta". Celular e
 * CPF repetidos respondem 422 na hora: não são o login, e esconder o erro só deixaria a pessoa sem saber
 * por que o cadastro não anda.</p>
 */
@Service
public class SignupService {

    static final int MAX_SIGNUPS_PER_IP = 20;

    private final AuthSettings settings;
    private final VerificationCodeService codes;
    private final AccountMailer mailer;
    private final RateLimiter rateLimiter;
    private final UserRepository users;
    private final UserCpfService cpfService;
    private final PasswordEncoder passwordEncoder;
    private final AccountFactory accountFactory;
    private final AuthService authService;

    public SignupService(AuthSettings settings, VerificationCodeService codes, AccountMailer mailer,
                         RateLimiter rateLimiter, UserRepository users, UserCpfService cpfService,
                         PasswordEncoder passwordEncoder, AccountFactory accountFactory, AuthService authService) {
        this.settings = settings;
        this.codes = codes;
        this.mailer = mailer;
        this.rateLimiter = rateLimiter;
        this.users = users;
        this.cpfService = cpfService;
        this.passwordEncoder = passwordEncoder;
        this.accountFactory = accountFactory;
        this.authService = authService;
    }

    public CodeSentDTO start(SignupStartDTO dto, String ip) {
        requireSignupEnabled();
        rateLimiter.hit("signup:ip:" + ip, MAX_SIGNUPS_PER_IP, Duration.ofHours(1),
            "Muitos cadastros a partir deste endereço. Tente de novo mais tarde.");

        String email = Emails.normalize(dto.email());
        String name = dto.name().strip();
        String phone = Phones.normalize(dto.phone());
        String cpf = dto.cpf() == null || dto.cpf().isBlank() ? null : cpfService.normalize(dto.cpf());
        validateUnique(phone, cpf);

        String requestId = VerificationCodeService.newRequestId();
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            codes.issue(requestId, VerificationPurpose.SIGNUP, email, Map.of(), true);
            mailer.sendNotice(AccountNotice.ACCOUNT_EXISTS, email, existing.get().getName());
            return sent(requestId, email);
        }

        Map<String, String> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("email", email);
        if (phone != null) {
            payload.put("phone", phone);
        }
        payload.put("passwordHash", passwordEncoder.encode(dto.password()));
        if (cpf != null) {
            payload.putAll(cpfService.sealPending(requestId, cpf));
        }
        VerificationCodeService.Issued issued = codes.issue(requestId, VerificationPurpose.SIGNUP, email, payload, false);
        mailer.sendCode(VerificationPurpose.SIGNUP, email, name, issued.code(), codes.expiresInSeconds() / 60);
        return sent(requestId, email);
    }

    public CodeSentDTO resend(String requestId) {
        requireSignupEnabled();
        VerificationCodeService.Issued issued = codes.resend(requestId, VerificationPurpose.SIGNUP);
        PendingCode p = issued.pending();
        if (p.decoy()) {
            mailer.sendNotice(AccountNotice.ACCOUNT_EXISTS, p.email(),
                users.findByEmail(p.email()).map(User::getName).orElse(null));
        } else {
            mailer.sendCode(VerificationPurpose.SIGNUP, p.email(), p.get("name"), issued.code(),
                codes.expiresInSeconds() / 60);
        }
        return sent(requestId, p.email());
    }

    /** Código certo: nasce a conta (com carteira e reserva), já com e-mail verificado e termos aceitos. */
    @Transactional
    public TokenResponseDTO confirm(CodeConfirmDTO dto) {
        requireSignupEnabled();
        PendingCode p = codes.verify(dto.requestId(), VerificationPurpose.SIGNUP, dto.code());
        String email = p.get("email");
        String phone = p.get("phone");
        String cpf = cpfService.openPending(p.requestId(), p.payload()).orElse(null);
        // Entre o passo 1 e o código, outra conta pode ter levado o e-mail, o celular ou o CPF.
        if (users.existsByEmail(email)) {
            throw new BusinessException("Este e-mail acabou de ser cadastrado. Entre pela tela de login.");
        }
        validateUnique(phone, cpf);

        LocalDateTime now = LocalDateTime.now();
        User created = User.builder()
            .name(p.get("name"))
            .email(email)
            .phone(phone)
            .passwordHash(p.get("passwordHash"))
            .emailVerifiedAt(now)
            .build();
        created.acceptTerms(now);
        User user = accountFactory.create(created);
        if (cpf != null) {
            cpfService.apply(user, cpf);
            users.save(user);
        }
        return authService.tokensFor(user);
    }

    private void validateUnique(String phone, String cpf) {
        if (phone != null && users.existsByPhone(phone)) {
            throw new BusinessException("Este celular já está cadastrado em outra conta.");
        }
        if (cpf != null && users.existsByCpfHash(cpfService.hash(cpf))) {
            throw new BusinessException("Este CPF já está cadastrado em outra conta.");
        }
    }

    private void requireSignupEnabled() {
        if (!settings.isSignupEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "O cadastro de novas contas está desativado. Peça acesso a quem administra o Activest.");
        }
    }

    private CodeSentDTO sent(String requestId, String email) {
        return new CodeSentDTO(requestId, Emails.mask(email), codes.resendInSeconds(), codes.expiresInSeconds());
    }
}
