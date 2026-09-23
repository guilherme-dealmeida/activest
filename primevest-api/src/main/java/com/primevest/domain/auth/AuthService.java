package com.primevest.domain.auth;

import com.primevest.domain.auth.dto.LoginRequestDTO;
import com.primevest.domain.auth.dto.RegisterRequestDTO;
import com.primevest.domain.auth.dto.TokenResponseDTO;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.strategy.StrategyReservation;
import com.primevest.domain.strategy.StrategyReservationRepository;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final StrategyReservationRepository strategyReservationRepository;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthService(JwtTokenProvider jwtTokenProvider,
                       @Lazy AuthenticationManager authenticationManager,
                       PasswordEncoder passwordEncoder,
                       UserRepository userRepository,
                       PortfolioRepository portfolioRepository,
                       StrategyReservationRepository strategyReservationRepository,
                       GoogleTokenVerifier googleTokenVerifier) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.strategyReservationRepository = strategyReservationRepository;
        this.googleTokenVerifier = googleTokenVerifier;
    }
    
    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @Transactional
    public void registerWithEmail(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email already registered");
        }
        User user = User.builder()
            .name(request.name())
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .build();
        user = userRepository.save(user);
        
        createDefaults(user);
    }

    public TokenResponseDTO loginWithEmail(LoginRequestDTO request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BusinessException("User not found"));
        
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        
        return new TokenResponseDTO(token, refreshToken, "Bearer", jwtExpiration);
    }

    /**
     * Login pelo Google a partir do ID token emitido para o navegador.
     *
     * Três caminhos, nesta ordem:
     *   1. já existe usuário com este google_id  -> entra
     *   2. existe usuário com este email         -> vincula o google_id à conta
     *   3. ninguém                               -> cria usuário e carteira padrão
     *
     * O passo 2 é o que permite a alguém que se cadastrou por email/senha passar
     * a entrar pelo Google sem virar um segundo cadastro. Ele só é seguro porque
     * o GoogleTokenVerifier exige email_verified: sem isso, bastaria criar uma
     * conta Google com o email alheio para assumir a conta existente.
     */
    @Transactional
    public TokenResponseDTO processGoogleToken(String credential) {
        GoogleTokenVerifier.GoogleProfile profile = googleTokenVerifier.verify(credential);

        User user = userRepository.findByGoogleId(profile.subject())
            .map(existing -> refreshProfile(existing, profile))
            .orElseGet(() -> userRepository.findByEmail(profile.email())
                .map(existing -> {
                    existing.setGoogleId(profile.subject());
                    return refreshProfile(existing, profile);
                })
                .orElseGet(() -> createFromGoogle(profile)));

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return new TokenResponseDTO(token, refreshToken, "Bearer", jwtExpiration);
    }

    /** O nome e a foto podem mudar no Google; o email e o google_id ancoram a identidade. */
    private User refreshProfile(User user, GoogleTokenVerifier.GoogleProfile profile) {
        if (profile.pictureUrl() != null) user.setAvatarUrl(profile.pictureUrl());
        if (profile.name() != null && !profile.name().isBlank()) user.setName(profile.name());
        return userRepository.save(user);
    }

    /**
     * Usuário criado pelo Google fica sem password_hash — a coluna é nullable
     * justamente por isso. Ele não consegue entrar por email/senha enquanto não
     * definir uma, o que é o comportamento correto: não existe senha para conferir.
     */
    private User createFromGoogle(GoogleTokenVerifier.GoogleProfile profile) {
        User user = userRepository.save(User.builder()
            .name(profile.name() != null && !profile.name().isBlank() ? profile.name() : profile.email())
            .email(profile.email())
            .googleId(profile.subject())
            .avatarUrl(profile.pictureUrl())
            .build());

        createDefaults(user);
        return user;
    }

    /**
     * O que todo usuario tem desde o nascimento: a carteira padrao e a
     * estrategia de reserva de emergencia (por percentual, alvo 0,00%).
     *
     * <p>Um metodo so para os dois caminhos que criam usuario — email e
     * Google —, para que o proximo item do nascimento nao seja lembrado em um
     * e esquecido no outro. O usuario semeado pela V9 nao passa por aqui: a
     * carteira dele vem da propria V9, e a estrategia de reserva da V12.</p>
     */
    private void createDefaults(User user) {
        Portfolio portfolio = new Portfolio();
        portfolio.setName("Minha Carteira");
        portfolio.setUser(user);
        portfolioRepository.save(portfolio);

        strategyReservationRepository.save(StrategyReservation.defaultFor(user));
    }

    public TokenResponseDTO refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("Invalid refresh token");
        }
        
        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found"));
        
        String newToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        
        return new TokenResponseDTO(newToken, newRefreshToken, "Bearer", jwtExpiration);
    }
}
