package com.activest.config;

import com.activest.domain.auth.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/api-docs/**", "/api-docs", "/v3/api-docs/**", "/v3/api-docs", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**").permitAll()
                // Logo do ativo: a tela carrega por <img src>, que nao manda o
                // token. E a marca da empresa, dado publico do catalogo. Pelo ticker e pelo par (tipo, ticker) — V13.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/assets/*/logo",
                    "/api/v1/assets/*/*/logo").permitAll()
                // Logo de emissora de renda fixa por raiz de CNPJ (plano de emissoras, Adendo A): mesmo motivo
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/fixed-income-issuers/logos/*")
                .permitAll()
                .anyRequest().authenticated()
            )
            // Sem token, ou com token vencido, a resposta e 401 — nao o 403 que o
            // Spring usa por padrao quando nao ha entry point. O front so limpa o
            // token e manda para /login no 401 (api/client.ts); com 403 o usuario
            // ficava na tela recebendo erro, sem ser levado ao login.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Termos atualizados pedem novo aceite (D19): depois do JWT, de quem le o estado da sessao.
            .addFilterAfter(new TermsAcceptanceFilter(objectMapper), JwtAuthFilter.class);

        // Sem oauth2Login: o login pelo Google não usa redirect de servidor. O
        // navegador obtém o ID token direto do Google e o envia para
        // POST /api/v1/auth/google, que já está em permitAll acima. Assim a API
        // continua STATELESS de verdade — nenhuma sessão, nenhum callback.
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}
