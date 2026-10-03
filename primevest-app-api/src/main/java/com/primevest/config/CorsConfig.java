package com.primevest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Origens do front em desenvolvimento, pelos dois nomes da maquina.
     *
     * <p>Para o navegador, localhost e 127.0.0.1 sao origens diferentes. So com
     * localhost na lista, abrir a tela por 127.0.0.1 fazia o Spring recusar a
     * requisicao com 403 antes de chegar ao controller — e o login mostrava
     * "Email ou senha invalidos" sem ter conferido senha nenhuma.</p>
     */
    static final String[] ALLOWED_ORIGINS = {
        "http://localhost:5173", "http://127.0.0.1:5173",
        "http://localhost:3000", "http://127.0.0.1:3000",
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(ALLOWED_ORIGINS)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*", "Authorization", "Content-Type")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
