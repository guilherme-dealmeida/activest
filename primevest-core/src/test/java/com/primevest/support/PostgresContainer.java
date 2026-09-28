package com.primevest.support;

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
            .withDatabaseName("db_primevest_test")
            .withUsername("postgres")
            .withPassword("postgres");

    static {
        POSTGRES.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }
}
