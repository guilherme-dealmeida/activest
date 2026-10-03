package com.primevest.adm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Rotinas de administrador — o catalogo (docs/planos/PLANO_ADM_API.md, 10). Porta 8081.
 *
 * <p>Varre {@code com.primevest}: as proprias classes ({@code com.primevest.adm}) e o
 * primevest-core (entidades, repositorios, JWT, tratamento de erro). A app-api nao esta no
 * classpath daqui.</p>
 */
@SpringBootApplication(scanBasePackages = "com.primevest")
@EntityScan("com.primevest")
@EnableJpaRepositories("com.primevest")
public class PrimevestAdmApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrimevestAdmApplication.class, args);
    }
}
