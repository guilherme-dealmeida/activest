package com.primevest.support;

import com.primevest.domain.auth.JwtTokenProvider;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base dos testes funcionais: aplicacao inteira, PostgreSQL de verdade, HTTP de
 * verdade.
 *
 * <p>Integracoes externas sao mockadas — a rede esta bloqueada por politica e um
 * teste nao pode depender de cotacao de terceiro. Cada modulo mocka as suas
 * (PLANO_ADM_API.md): a app-api a Binance ({@code FuncionalBase}), a adm-api e os
 * testes de ponta a ponta as fontes do catalogo. Aqui, no primevest-core, so o que
 * e comum; a aplicacao do contexto e a do modulo do teste.</p>
 *
 * <p>A limpeza apaga so o que e do usuario. O catalogo (assets, asset_types,
 * asset_categories, asset_terms) fica: ele vem das migrations e e o mesmo da
 * aplicacao.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresContainer.class)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    private static final String[] TABELAS_DO_USUARIO = {
        "dividends", "transactions", "portfolio_assets", "fixed_income_notes",
        "strategies", "strategy_reservations", "ceiling_prices", "goals",
        "portfolios", "users",
        // Eventos do papel (V20): sao catalogo, mas nenhuma migration os semeia
        // — so os testes gravam, e um cenario nao pode herdar o do outro.
        "asset_cash_events", "asset_stock_events",
        // Balancos da CVM (V32) e serie trimestral (V33): volume de arquivo e derivado,
        // nenhuma migration semeia. O CASCADE leva cvm_statement_values, share_counts
        // e company_quarters, e nao toca issuers.
        "cvm_documents",
        // Informe mensal de FII (V34): idem; sai antes dos testes que apagam issuers.
        "fii_monthly"
    };

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PortfolioRepository portfolioRepository;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    /** Usuario com carteira, pronto para receber transacoes. */
    protected User usuarioComCarteira(String email) {
        User user = userRepository.save(Fixtures.user(email));
        portfolioRepository.save(Fixtures.portfolio(user));
        return user;
    }

    protected HttpHeaders headers(User user) {
        return Tokens.bearer(jwtTokenProvider, user);
    }

    protected <T> ResponseEntity<T> get(User user, String path, Class<T> tipo) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(user)), tipo);
    }

    protected <T> ResponseEntity<T> post(User user, String path, Object corpo, Class<T> tipo) {
        return rest.exchange(path, HttpMethod.POST, entidade(user, corpo), tipo);
    }

    protected <T> ResponseEntity<T> put(User user, String path, Object corpo, Class<T> tipo) {
        return rest.exchange(path, HttpMethod.PUT, entidade(user, corpo), tipo);
    }

    protected ResponseEntity<String> delete(User user, String path) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(user)), String.class);
    }

    private HttpEntity<Object> entidade(User user, Object corpo) {
        HttpHeaders headers = headers(user);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new HttpEntity<>(corpo, headers);
    }

    /**
     * PATCH nao existe na fabrica padrao do TestRestTemplate (a do JDK antigo
     * recusa o metodo). A do java.net.http aceita — e o switch da reserva e
     * PATCH.
     */
    @BeforeEach
    void suportarPatch() {
        rest.getRestTemplate().setRequestFactory(
            new org.springframework.http.client.JdkClientHttpRequestFactory());
    }

    @org.springframework.beans.factory.annotation.Value("${primevest.data.dir}")
    private String dataDir;

    /**
     * Historico de proventos da B3 em disco (F10: o arquivo e o estado): cada cenario comeca
     * sem ele, senao a resposta mockada de um teste vira o historico do seguinte.
     */
    @BeforeEach
    void limparHistoricoEmDisco() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Path.of(dataDir, "b3", "proventos");
        if (java.nio.file.Files.isDirectory(dir)) {
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
                for (java.nio.file.Path f : files.toList()) {
                    java.nio.file.Files.deleteIfExists(f);
                }
            }
        }
    }

    @BeforeEach
    void limparDadosDoUsuario() {
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", TABELAS_DO_USUARIO) + " RESTART IDENTITY CASCADE");
    }
}
