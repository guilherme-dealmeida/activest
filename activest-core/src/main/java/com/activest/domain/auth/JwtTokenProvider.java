package com.activest.domain.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Emissão e leitura dos tokens da API.
 *
 * <p>Dois tipos, marcados na claim {@code typ}: {@code access} (24 h, vai no header das chamadas) e
 * {@code refresh} (7 dias, só serve para {@code POST /auth/refresh}). Até 05/10/2026 nada os
 * distinguia: assinados com a mesma chave, o refresh entrava no filtro como token de acesso de 7 dias,
 * e o {@code /auth/refresh} aceitava um token de acesso (docs/planos/PLANO_CADASTRO_USUARIO.md, U1).
 * Token sem {@code typ} (emitido antes disso) não vale para nenhum dos dois: o usuário entra de novo.</p>
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long jwtExpiration;
    private final long refreshExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret:defaultSecretKeyWithAtLeast32CharactersForHmacSha256!}") String secret,
            @Value("${jwt.expiration:86400000}") long jwtExpiration,
            @Value("${jwt.refresh-expiration:604800000}") long refreshExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpiration = jwtExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    /** O que um token válido diz: de quem é e quando foi emitido (para o corte de {@code password_changed_at}). */
    public record TokenClaims(UUID userId, Instant issuedAt) {}

    public String generateToken(UUID userId, String email) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /** Token de acesso válido (assinatura, validade e {@code typ=access}); vazio em qualquer outro caso. */
    public Optional<TokenClaims> parseAccess(String token) {
        return parse(token, TYPE_ACCESS);
    }

    /** Token de renovação válido ({@code typ=refresh}); vazio em qualquer outro caso. */
    public Optional<TokenClaims> parseRefresh(String token) {
        return parse(token, TYPE_REFRESH);
    }

    private Optional<TokenClaims> parse(String token, String type) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!type.equals(claims.get(CLAIM_TYPE, String.class)) || claims.getIssuedAt() == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(UUID.fromString(claims.getSubject()), claims.getIssuedAt().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessExpirationMillis() {
        return jwtExpiration;
    }
}
