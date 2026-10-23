package com.activest.adm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Rotinas de administrador — o catalogo (docs/planos/PLANO_ADM_API.md, 10). Porta 8081.
 *
 * <p>Varre {@code com.activest}: as proprias classes ({@code com.activest.adm}) e o
 * activest-core (entidades, repositorios, JWT, tratamento de erro). A app-api nao esta no
 * classpath daqui.</p>
 */
@SpringBootApplication(scanBasePackages = "com.activest")
@EntityScan("com.activest")
@EnableJpaRepositories("com.activest")
public class ActivestAdmApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivestAdmApplication.class, args);
    }
}
