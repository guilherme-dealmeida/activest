package com.primevest;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Configuracao dos testes do primevest-core, que nao tem aplicacao propria: os testes de
 * repositorio ({@code @DataJpaTest}) procuram uma {@code @SpringBootConfiguration} subindo
 * pelos pacotes e param aqui.
 *
 * <p>Fica fora do test-jar (pom do core): nos modulos que usam o suporte de testes do core,
 * a configuracao encontrada tem que ser a aplicacao deles.</p>
 */
@SpringBootApplication
public class CoreTestApplication {
}
