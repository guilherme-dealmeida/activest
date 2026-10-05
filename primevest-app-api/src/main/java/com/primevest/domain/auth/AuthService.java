package com.primevest.domain.auth;

import com.primevest.domain.auth.dto.LoginRequestDTO;
import com.primevest.domain.auth.dto.TokenResponseDTO;
import com.primevest.domain.auth.verification.RateLimiter;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Entrada na conta: e-mail/senha, Google e renovação do token. O cadastro com código está no
 * {@link SignupService}; a redefinição de senha no {@link PasswordResetService}.
 */
@Service
public class AuthService {

    /** Falhas de login antes do bloqueio temporário, por e-mail e por IP (D16 / 3.2). */
    static final int MAX_FALHAS_POR_EMAIL = 10;
    static final int MAX_FALHAS_POR_IP = 50;
    static final Duration JANELA_FALHAS = Duration.ofMinutes(15);
    static final String MUITAS_TENTATIVAS = "Muitas tentativas de entrada. Aguarde 15 minutos ou use "
        + "\"Esqueci a senha\".";

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AccountFactory accountFactory;
    private final SessionValidator sessionValidator;
    private final RateLimiter rateLimiter;
    private final AuthSettings settings;

    public AuthService(JwtTokenProvider jwtTokenProvider,
                       @Lazy AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       GoogleTokenVerifier googleTokenVerifier,
                       AccountFactory accountFactory,
                       SessionValidator sessionValidator,
                       RateLimiter rateLimiter,
                       AuthSettings settings) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.accountFactory = accountFactory;
        this.sessionValidator = sessionValidator;
        this.rateLimiter = rateLimiter;
        this.settings = settings;
    }

    public TokenResponseDTO loginWithEmail(LoginRequestDTO request, String ip) {
        String email = Emails.normalize(request.email());
        String chaveEmail = "login:email:" + email;
        String chaveIp = "login:ip:" + ip;
        rateLimiter.check(chaveEmail, MAX_FALHAS_POR_EMAIL, MUITAS_TENTATIVAS);
        rateLimiter.check(chaveIp, MAX_FALHAS_POR_IP, MUITAS_TENTATIVAS);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException e) {
            rateLimiter.record(chaveEmail, JANELA_FALHAS);
            rateLimiter.record(chaveIp, JANELA_FALHAS);
            throw new BadCredentialsException("Credenciais recusadas");
        }
        rateLimiter.reset(chaveEmail);

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("Credenciais recusadas"));
        return tokensFor(user);
    }

    /**
     * Login pelo Google a partir do ID token emitido para o navegador.
     *
     * Três caminhos, nesta ordem:
     *   1. já existe usuário com este google_id  -> entra
     *   2. existe usuário com este email         -> vincula o google_id à conta
     *   3. ninguém                               -> cria a conta (se o cadastro estiver ligado, D16)
     *
     * O passo 2 só é seguro porque o GoogleTokenVerifier exige email_verified: sem isso, bastaria criar
     * uma conta Google com o email alheio para assumir a conta existente.
     *
     * Nome e foto vêm do Google só na criação (D5, G5): até 05/10/2026 eram regravados em todo login,
     * e o nome editado em Configurações sumia no login seguinte.
     */
    @Transactional
    public TokenResponseDTO processGoogleToken(String credential) {
        GoogleTokenVerifier.GoogleProfile profile = googleTokenVerifier.verify(credential);
        String email = Emails.normalize(profile.email());

        User user = userRepository.findByGoogleId(profile.subject())
            .map(existing -> marcarEmailVerificado(existing, email))
            .orElseGet(() -> userRepository.findByEmail(email)
                .map(existing -> {
                    existing.setGoogleId(profile.subject());
                    return marcarEmailVerificado(existing, email);
                })
                .orElseGet(() -> createFromGoogle(profile, email)));

        return tokensFor(user);
    }

    /** O Google garantiu o e-mail (email_verified): se é o mesmo da conta, ele está verificado (G8). */
    private User marcarEmailVerificado(User user, String googleEmail) {
        if (user.getEmailVerifiedAt() == null && googleEmail.equals(user.getEmail())) {
            user.setEmailVerifiedAt(LocalDateTime.now());
        }
        return userRepository.save(user);
    }

    /**
     * Conta nova pelo Google: sem password_hash (a coluna é nullable por isso), e-mail já verificado
     * (G8) e o aceite dos termos pela frase sob o botão (D17), registrado aqui. Com o cadastro desligado
     * (G10), o Google não cria conta — senão a chave do D16 teria uma porta lateral.
     */
    private User createFromGoogle(GoogleTokenVerifier.GoogleProfile profile, String email) {
        if (!settings.isSignupEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "O cadastro de novas contas está desativado. Peça acesso a quem administra o PrimeVest.");
        }
        LocalDateTime agora = LocalDateTime.now();
        User novo = User.builder()
            .name(profile.name() != null && !profile.name().isBlank() ? profile.name() : email)
            .email(email)
            .googleId(profile.subject())
            .avatarUrl(profile.pictureUrl())
            .emailVerifiedAt(agora)
            .build();
        novo.acceptTerms(agora);
        return accountFactory.create(novo);
    }

    /**
     * Renovação: só token {@code typ=refresh}, de usuário que existe e emitido depois da última troca
     * de senha (D15) — trocar a senha derruba também as renovações.
     */
    public TokenResponseDTO refreshToken(String refreshToken) {
        JwtTokenProvider.TokenClaims claims = jwtTokenProvider.parseRefresh(refreshToken)
            .orElseThrow(() -> new BadCredentialsException("Token de renovação inválido"));
        sessionValidator.load(claims.userId())
            .filter(state -> state.accepts(claims.issuedAt()))
            .orElseThrow(() -> new BadCredentialsException("Sessão encerrada"));
        User user = userRepository.findById(claims.userId())
            .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
        return tokensFor(user);
    }

    public TokenResponseDTO tokensFor(User user) {
        return new TokenResponseDTO(
            jwtTokenProvider.generateToken(user.getId(), user.getEmail()),
            jwtTokenProvider.generateRefreshToken(user.getId()),
            "Bearer",
            jwtTokenProvider.accessExpirationMillis());
    }
}
