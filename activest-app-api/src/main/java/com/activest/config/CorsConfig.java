package com.activest.config;

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
     *
     * <p>5174 e o front da pilha activest-teste (docs/planos/PLANO_PRIMEVEST_TESTE.md). O proxy do Vite
     * repassa a origem do navegador, e sem ela na lista todo POST, PUT e DELETE da tela de teste voltava
     * 403 (os GET passam: o navegador nao manda Origin no GET da mesma origem). Achado na F6 do
     * docs/planos/PLANO_INTEGRACAO_B3.md.</p>
     */
    static final String[] ALLOWED_ORIGINS = {
        "http://localhost:5173", "http://127.0.0.1:5173",
        "http://localhost:5174", "http://127.0.0.1:5174",
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
