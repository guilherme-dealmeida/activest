package com.activest.domain.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Autentica pelo header {@code Authorization: Bearer}. Só token de acesso ({@code typ=access}) de um
 * usuário que ainda existe e emitido depois da última troca de senha (D15). Qualquer outro caso segue
 * sem autenticação, e a rota protegida responde 401.
 *
 * <p>O estado da sessão fica no atributo {@link #SESSION_ATTRIBUTE} da requisição: o filtro dos termos
 * da app-api (D19) o lê sem consultar o banco de novo.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = "activest.session";

    private final JwtTokenProvider tokenProvider;
    private final SessionValidator sessionValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = getJwtFromRequest(request);

        if (StringUtils.hasText(token)) {
            tokenProvider.parseAccess(token)
                .flatMap(claims -> sessionValidator.load(claims.userId())
                    .filter(state -> state.accepts(claims.issuedAt())))
                .ifPresent(state -> {
                    request.setAttribute(SESSION_ATTRIBUTE, state);
                    SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(state.userId(), null, new ArrayList<>()));
                });
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/");
    }
}
