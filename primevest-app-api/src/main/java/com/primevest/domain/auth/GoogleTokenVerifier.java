package com.primevest.domain.auth;

import com.primevest.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Valida o ID token que o Google Identity Services devolve ao navegador.
 *
 * O token é um JWT assinado pelo Google. Validar quer dizer conferir quatro
 * coisas, e as quatro importam:
 *
 *   assinatura  contra as chaves públicas do Google, que rotacionam — por isso
 *               o JWKS é buscado pela URI e não embutido no código
 *   audience    o token foi emitido PARA esta aplicação. Sem isso, um ID token
 *               legítimo obtido por qualquer outro site seria aceito aqui, e
 *               qualquer dono de app Google conseguiria entrar como outra pessoa
 *   issuer      foi o Google que emitiu
 *   expiração   ainda vale (o Google emite com validade de uma hora)
 *
 * Decodificar o JWT sem verificar a assinatura — só ler o payload em base64 —
 * seria o mesmo que não ter login nenhum: qualquer um forja um token com o
 * email que quiser.
 */
@Component
public class GoogleTokenVerifier {

    /** Chaves públicas do Google. O NimbusJwtDecoder cacheia e revalida sozinho. */
    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    /** O Google emite com as duas formas; ambas são legítimas. */
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder;
    private final boolean configured;

    public GoogleTokenVerifier(@Value("${google.client-id:}") String clientId) {
        this.configured = clientId != null && !clientId.isBlank() && !"dummy".equals(clientId);
        this.decoder = configured ? build(clientId) : null;
    }

    private static JwtDecoder build(String clientId) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();

        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(clientId));

        OAuth2TokenValidator<Jwt> issuer = new JwtClaimValidator<String>(
            JwtClaimNames.ISS, ISSUERS::contains);

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(), audience, issuer));

        return jwtDecoder;
    }

    public GoogleProfile verify(String idToken) {
        if (!configured) {
            throw new BusinessException(
                "Login com Google não está configurado neste ambiente. Defina GOOGLE_CLIENT_ID.");
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            // A mensagem do Nimbus nomeia a claim que falhou; vale no log, não na resposta.
            throw new BusinessException("Token do Google inválido ou expirado");
        }

        // O Google só garante o email quando email_verified é true. Sem essa
        // checagem, uma conta Google com email não verificado igual ao de um
        // usuário existente seria vinculada a ele — sequestro de conta pela
        // porta da frente, já que o vínculo abaixo procura por email.
        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            throw new BusinessException("A conta Google precisa ter o email verificado");
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new BusinessException("O token do Google não trouxe email");
        }

        return new GoogleProfile(
            jwt.getSubject(),
            email,
            jwt.getClaimAsString("name"),
            jwt.getClaimAsString("picture")
        );
    }

    /** subject é o id estável do usuário no Google — o que vai para users.google_id. */
    public record GoogleProfile(String subject, String email, String name, String pictureUrl) {}
}
