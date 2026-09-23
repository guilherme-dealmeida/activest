package com.primevest.support;

import com.primevest.domain.auth.JwtTokenProvider;
import com.primevest.domain.user.User;
import org.springframework.http.HttpHeaders;

/**
 * Token dos testes funcionais, gerado direto pelo JwtTokenProvider.
 *
 * <p>De proposito nao passa por {@code POST /auth/login}: Autenticacao ainda
 * esta em aberto, e um teste de Carteira nao deve quebrar porque o login mudou.
 * O que se exercita aqui e o filtro JWT, que e o mesmo da aplicacao.</p>
 */
public final class Tokens {

    private Tokens() {
    }

    public static HttpHeaders bearer(JwtTokenProvider provider, User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(provider.generateToken(user.getId(), user.getEmail()));
        return headers;
    }
}
