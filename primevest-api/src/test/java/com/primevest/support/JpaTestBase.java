package com.primevest.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base dos testes de repositorio.
 *
 * <p>{@code replace = NONE} e o que impede o Spring de trocar o datasource por
 * um banco em memoria — que e justamente o que estes testes nao querem.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainer.class)
@ActiveProfiles("test")
public abstract class JpaTestBase {
}
