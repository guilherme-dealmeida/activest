package com.primevest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * API do usuario — a carteira (docs/planos/PLANO_ADM_API.md).
 *
 * <p>Equivale a {@code @SpringBootApplication}, com um filtro a mais na varredura:
 * {@code com.primevest.adm} fica de fora. A adm-api so aparece no classpath dos testes de
 * ponta a ponta de proventos e fechamento, que importam o que usam; varre-la subiria o
 * controller, os agendamentos e a seguranca dela junto com os daqui.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = {
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.primevest\\.adm\\..*")
})
@EnableCaching
public class PrimevestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrimevestApplication.class, args);
    }
}
