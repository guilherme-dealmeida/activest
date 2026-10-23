package com.activest.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.config.SecurityConfig;
import com.activest.domain.auth.JwtAuthFilter;
import com.activest.domain.auth.JwtTokenProvider;
import com.activest.exception.GlobalExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos testes de controller: so a camada web, com a seguranca de verdade.
 *
 * <p>{@code SecurityConfig} e {@code JwtAuthFilter} entram no contexto porque
 * o que interessa aqui inclui o 401 de quem nao esta autenticado — testar o
 * controller com a seguranca desligada esconderia exatamente isso.</p>
 *
 * <p>O usuario e simulado com {@code @WithMockUser(username = "<uuid>")}:
 * {@code SecurityUtils} le o nome do principal como UUID.</p>
 */
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
public abstract class MvcTestBase {

    protected static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @MockBean
    protected JwtTokenProvider jwtTokenProvider;

    /** O filtro JWT consulta o estado da sessao no banco (D15); aqui nao ha banco. */
    @MockBean
    protected com.activest.domain.auth.SessionValidator sessionValidator;
}
