package com.primevest.domain.auth;

import com.primevest.domain.auth.dto.LoginRequestDTO;
import com.primevest.domain.auth.dto.RegisterRequestDTO;
import com.primevest.domain.auth.dto.TokenResponseDTO;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;

    public AuthService(JwtTokenProvider jwtTokenProvider,
                       @Lazy AuthenticationManager authenticationManager,
                       PasswordEncoder passwordEncoder,
                       UserRepository userRepository,
                       PortfolioRepository portfolioRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
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
        
        // Create default portfolio
        Portfolio portfolio = new Portfolio();
        portfolio.setName("Minha Carteira");
        portfolio.setUser(user);
        portfolioRepository.save(portfolio);
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

    @Transactional
    public String processGoogleLogin(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String googleId = (String) attributes.get("sub");
        String avatarUrl = (String) attributes.get("picture");
        
        User user = userRepository.findByGoogleId(googleId)
            .orElseGet(() -> userRepository.findByEmail(email)
                .map(existingUser -> {
                    existingUser.setGoogleId(googleId);
                    if (avatarUrl != null) existingUser.setAvatarUrl(avatarUrl);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                        .name(name)
                        .email(email)
                        .googleId(googleId)
                        .avatarUrl(avatarUrl)
                        .build();
                    newUser = userRepository.save(newUser);
                    Portfolio portfolio = new Portfolio();
                    portfolio.setName("Minha Carteira");
                    portfolio.setUser(newUser);
                    portfolioRepository.save(portfolio);
                    return newUser;
                })
            );
        
        return jwtTokenProvider.generateToken(user.getId(), user.getEmail());
    }
    
    @Transactional
    public TokenResponseDTO processGoogleToken(String credential) {
        // For now, just a placeholder for Google ID token validation
        throw new BusinessException("Google token login not yet implemented");
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
