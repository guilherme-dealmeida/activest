package com.activest.integration.b3investidor;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Configuracao da API da Area do Investidor da B3 ({@code b3i.*}; docs/planos/PLANO_INTEGRACAO_B3.md, 6.13).
 *
 * <p>Vem do {@code .env} pelo {@code docker-compose.yml}. Trocar o simulador pela B3 real e trocar
 * estes valores — URLs, credenciais do Pacote de Acesso e certificado — sem mudar codigo. As rotas
 * ficam aqui porque tres delas sao inferidas (6.3): confirmar o contrato muda configuracao.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "b3i")
public class B3InvestidorProperties {

    /** Sem {@code true}, a B3 nao aparece entre as fontes e nada dela sobe. */
    private boolean enabled;

    /** {@code https://investidor.b3.com.br:2443}, {@code https://apib3i-cert.b3.com.br:2443} ou o simulador. */
    private String baseUrl;

    /** {@code https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token} ou o do simulador. */
    private String tokenUrl;

    private String clientId;
    private String clientSecret;
    private String scope;

    /** Certificado do Pacote de Acesso (.p12), para o mTLS; vazio = sem certificado de cliente. */
    private String certP12Path;
    private String certP12Password;

    /** Onde o investidor autoriza o Activest (o {@code Link_optin.txt} do Pacote de Acesso). */
    private String optinUrl;

    private long timeoutSeconds = 30;

    /** Inicio do historico da B3 (A5): a primeira busca pede desde aqui; as seguintes, do cursor. */
    private LocalDate historyStart = LocalDate.of(2019, 11, 1);

    /** "Consultar cada CPF uma vez por dia" (A6, G12). Desligado so no simulador, para o teste C4. */
    private boolean oncePerDay = true;

    /** A B3 publica o dia anterior (D-1) a partir deste horario de Brasilia (A6). */
    private LocalTime availableAfter = LocalTime.of(8, 0);

    /** Renovar o token este tanto antes de vencer (A7). */
    private long tokenRenewSeconds = 300;

    private Paths paths = new Paths();

    /** Rotas da API; {@code {documentNumber}} e {@code {product}} sao trocados pelo cliente. */
    @Getter
    @Setter
    public static class Paths {
        private String authorization = "/api/authorization/v1/investors/{documentNumber}";
        private String assetTrading = "/api/asset-trading/v2/investors/{documentNumber}";
        private String movement = "/api/movement/v2/{product}/investors/{documentNumber}";
        private String position = "/api/position/v3/{product}/investors/{documentNumber}";
        private String provisionedEvents = "/api/provisioned-events/v2/investors/{documentNumber}";
    }
}
