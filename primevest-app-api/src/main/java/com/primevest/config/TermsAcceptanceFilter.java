package com.primevest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.domain.auth.JwtAuthFilter;
import com.primevest.domain.auth.SessionValidator;
import com.primevest.domain.user.LegalTerms;
import com.primevest.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Termos novos pedem novo aceite (docs/planos/PLANO_CADASTRO_USUARIO.md, D19): usuário autenticado que
 * não aceitou a versão atual recebe 403 com {@code errors.code = TERMS_REQUIRED} em tudo, menos no que
 * a tela precisa para mostrar o modal e decidir — ler o próprio usuário, aceitar, ou excluir a conta.
 *
 * <p>Não é {@code @Component} de propósito: o Spring Boot registraria o filtro também fora da cadeia de
 * segurança. Quem o cria é o {@link SecurityConfig}, depois do {@link JwtAuthFilter}, de quem lê o estado
 * da sessão (sem nova consulta ao banco).</p>
 */
public class TermsAcceptanceFilter extends OncePerRequestFilter {

    public static final String CODE = "TERMS_REQUIRED";
    private static final String ME = "/api/v1/users/me";

    private final ObjectMapper json;

    public TermsAcceptanceFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object state = request.getAttribute(JwtAuthFilter.SESSION_ATTRIBUTE);
        if (state instanceof SessionValidator.SessionState sessao
                && !LegalTerms.CURRENT_VERSION.equals(sessao.termsVersion())
                && !liberado(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            json.writeValue(response.getOutputStream(), new ErrorResponse(HttpStatus.FORBIDDEN.value(),
                "Os Termos de Uso e a Política de Privacidade foram atualizados. Aceite a nova versão para continuar.",
                LocalDateTime.now(), Map.of("code", CODE)));
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean liberado(HttpServletRequest request) {
        String path = request.getRequestURI();
        String metodo = request.getMethod();
        return (ME.equals(path) && ("GET".equals(metodo) || "DELETE".equals(metodo)))
            || (ME + "/terms").equals(path)
            || path.startsWith(ME + "/delete-request");
    }
}
