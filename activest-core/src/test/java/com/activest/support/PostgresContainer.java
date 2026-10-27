package com.activest.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * O PostgreSQL dos testes — um container para a execucao inteira.
 *
 * <p>A instancia e estatica e sobe uma unica vez: o Testcontainers so a derruba
 * no fim da JVM (sem {@code stop()}, o JVM shutdown hook cuida). Subir um banco
 * por classe custaria dezenas de segundos e as migrations rodariam de novo a
 * cada vez.</p>
 *
 * <p>Versao 17, igual a do docker-compose: o teste nao tem valor se o banco nao
 * for o de producao.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainer {

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("db_activest_test")
            .withUsername("postgres")
            .withPassword("postgres")
            // Cada combinacao de @MockBean e um contexto Spring proprio, guardado em cache com o seu pool (10
            // conexoes): com a FundCatalogIT a adm-api passou das 100 conexoes padrao ("too many clients already",
            // 27/10/2026). fsync=off e o padrao do Testcontainers, repetido porque withCommand o substitui.
            .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=300");

    static {
        POSTGRES.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }
}
