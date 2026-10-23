package com.activest.support;

import com.activest.domain.auth.JwtTokenProvider;
import com.activest.domain.portfolio.Portfolio;
import com.activest.domain.portfolio.PortfolioRepository;
import com.activest.domain.user.User;
import com.activest.domain.user.UserRepository;
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
 * (docs/planos/PLANO_ADM_API.md): a app-api a Binance ({@code FuncionalBase}), a adm-api e os
 * testes de ponta a ponta as fontes do catalogo. Aqui, no activest-core, so o que
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

    private static final String[] USER_TABLES = {
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
        "fii_monthly",
        // Serie dos indices de mercado (V43): o cadastro (market_indices) e seed e fica; a serie vem da carga.
        "market_index_values",
        // Notificacoes (V48): as de administrador nao tem dono e nao saem com users.
        "notifications"
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
    protected User userWithPortfolio(String email) {
        User user = userRepository.save(Fixtures.user(email));
        portfolioRepository.save(Fixtures.portfolio(user));
        return user;
    }

    protected HttpHeaders headers(User user) {
        return Tokens.bearer(jwtTokenProvider, user);
    }

    protected <T> ResponseEntity<T> get(User user, String path, Class<T> type) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(user)), type);
    }

    protected <T> ResponseEntity<T> post(User user, String path, Object body, Class<T> type) {
        return rest.exchange(path, HttpMethod.POST, entity(user, body), type);
    }

    protected <T> ResponseEntity<T> put(User user, String path, Object body, Class<T> type) {
        return rest.exchange(path, HttpMethod.PUT, entity(user, body), type);
    }

    protected ResponseEntity<String> delete(User user, String path) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(user)), String.class);
    }

    private HttpEntity<Object> entity(User user, Object body) {
        HttpHeaders headers = headers(user);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /**
     * PATCH nao existe na fabrica padrao do TestRestTemplate (a do JDK antigo
     * recusa o metodo). A do java.net.http aceita — e o switch da reserva e
     * PATCH.
     */
    @BeforeEach
    void supportPatch() {
        rest.getRestTemplate().setRequestFactory(
            new org.springframework.http.client.JdkClientHttpRequestFactory());
    }

    @org.springframework.beans.factory.annotation.Value("${activest.data.dir}")
    private String dataDir;

    /**
     * Historico de proventos da B3 em disco (F10: o arquivo e o estado): cada cenario comeca
     * sem ele, senao a resposta mockada de um teste vira o historico do seguinte.
     */
    @BeforeEach
    void clearHistoryOnDisk() throws java.io.IOException {
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
    void clearUserData() {
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", USER_TABLES) + " RESTART IDENTITY CASCADE");
    }
}
