package com.primevest.functional;

import com.primevest.domain.auth.mail.AccountNotice;
import com.primevest.domain.auth.verification.VerificationPurpose;
import com.primevest.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Cadastro, entrada e conta de ponta a ponta na API (docs/planos/PLANO_CADASTRO_USUARIO.md, secao 7).
 * O e-mail e o mock {@code accountMailer} do {@link FuncionalBase}: o codigo e lido dele.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class CadastroUsuarioIT extends FuncionalBase {

    private static final String SENHA = "senha-forte-123";

    // ---------------------------------------------------------------- apoio

    private ResponseEntity<Map> publico(HttpMethod metodo, String path, Object corpo) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, metodo, new HttpEntity<>(corpo, h), Map.class);
    }

    private ResponseEntity<Map> comToken(String token, HttpMethod metodo, String path, Object corpo) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return rest.exchange(path, metodo, new HttpEntity<>(corpo, h), Map.class);
    }

    private Map<String, Object> dadosCadastro(String email) {
        Map<String, Object> c = new HashMap<>();
        c.put("name", "Maria Teste");
        c.put("email", email);
        c.put("password", SENHA);
        c.put("acceptTerms", true);
        return c;
    }

    private String ultimoCodigo(VerificationPurpose purpose, String email) {
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(accountMailer, atLeastOnce()).sendCode(eq(purpose), eq(email), any(), codigo.capture(), anyLong());
        return codigo.getValue();
    }

    /** Cadastro completo pela API; devolve o token de acesso. */
    private String cadastrar(Map<String, Object> dados) {
        ResponseEntity<Map> inicio = publico(HttpMethod.POST, "/api/v1/auth/signup/start", dados);
        assertThat(inicio.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String id = (String) inicio.getBody().get("requestId");
        String code = ultimoCodigo(VerificationPurpose.SIGNUP, ((String) dados.get("email")).toLowerCase());
        ResponseEntity<Map> fim = publico(HttpMethod.POST, "/api/v1/auth/signup/confirm",
            Map.of("requestId", id, "code", code));
        assertThat(fim.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) fim.getBody().get("accessToken");
    }

    private ResponseEntity<Map> login(String email, String senha) {
        return publico(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", senha));
    }

    // ---------------------------------------------------------------- cadastro

    @Test
    @DisplayName("C4: cadastro com codigo cria a conta com carteira, e-mail verificado e termos aceitos")
    void cadastroCompleto() {
        Map<String, Object> st = publico(HttpMethod.GET, "/api/v1/auth/signup/enabled", null).getBody();
        assertThat(st).containsEntry("enabled", true).containsEntry("termsVersion", "2026-10-provisorio");

        Map<String, Object> dados = dadosCadastro("Maria@PrimeVest.test");
        dados.put("phone", "(41) 99876-5432");
        dados.put("cpf", "529.982.247-25");
        String token = cadastrar(dados);

        Map<String, Object> me = comToken(token, HttpMethod.GET, "/api/v1/users/me", null).getBody();
        assertThat(me).containsEntry("email", "maria@primevest.test")
            .containsEntry("emailVerified", true)
            .containsEntry("hasPassword", true)
            .containsEntry("googleLinked", false)
            .containsEntry("phone", "+5541998765432")
            .containsEntry("cpfMasked", "***.982.247-**")
            .containsEntry("termsPending", false)
            .containsEntry("theme", "DARK");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolios p JOIN users u ON u.id = p.user_id "
            + "WHERE u.email = 'maria@primevest.test'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM strategy_reservations r JOIN users u ON u.id = r.user_id "
            + "WHERE u.email = 'maria@primevest.test'", Integer.class)).isEqualTo(1);
        // CPF nunca em claro no banco
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE convert_from(cpf_ciphertext, 'LATIN1') LIKE '%52998224725%'",
            Integer.class)).isZero();

        // C11: maiusculas no login entram na mesma conta
        assertThat(login("MARIA@primevest.TEST", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("C8: e-mail ja cadastrado responde igual, avisa por e-mail e nenhum codigo confirma")
    void emailExistente() {
        usuarioComCarteira("ja@primevest.test");
        ResponseEntity<Map> r = publico(HttpMethod.POST, "/api/v1/auth/signup/start", dadosCadastro("ja@primevest.test"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(r.getBody()).containsKeys("requestId", "maskedEmail", "resendInSeconds", "expiresInSeconds");
        verify(accountMailer).sendNotice(eq(AccountNotice.ACCOUNT_EXISTS), eq("ja@primevest.test"), any());
        verify(accountMailer, never()).sendCode(any(), eq("ja@primevest.test"), any(), any(), anyLong());

        ResponseEntity<Map> c = publico(HttpMethod.POST, "/api/v1/auth/signup/confirm",
            Map.of("requestId", r.getBody().get("requestId"), "code", "123456"));
        assertThat(c.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("C5/C7: codigo errado conta tentativas; reenvio antes de 60 s e recusado")
    void tentativasEReenvio() {
        ResponseEntity<Map> r = publico(HttpMethod.POST, "/api/v1/auth/signup/start", dadosCadastro("ana@primevest.test"));
        String id = (String) r.getBody().get("requestId");

        ResponseEntity<Map> reenvio = publico(HttpMethod.POST, "/api/v1/auth/signup/resend", Map.of("requestId", id));
        assertThat(reenvio.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        String certo = ultimoCodigo(VerificationPurpose.SIGNUP, "ana@primevest.test");
        String errado = certo.equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 4; i++) {
            ResponseEntity<Map> e = publico(HttpMethod.POST, "/api/v1/auth/signup/confirm", Map.of("requestId", id, "code", errado));
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat((String) e.getBody().get("message")).startsWith("Código incorreto.");
        }
        publico(HttpMethod.POST, "/api/v1/auth/signup/confirm", Map.of("requestId", id, "code", errado));
        // a quinta errada apaga o pedido: nem o certo vale mais
        ResponseEntity<Map> depois = publico(HttpMethod.POST, "/api/v1/auth/signup/confirm", Map.of("requestId", id, "code", certo));
        assertThat(depois.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) depois.getBody().get("message")).contains("expirou");
        assertThat(userRepository.findByEmail("ana@primevest.test")).isEmpty();
    }

    @Test
    @DisplayName("C9/C16/C17/C25: sem aceite 400; CPF invalido, CPF e celular repetidos 422")
    void validacoes() {
        Map<String, Object> semAceite = dadosCadastro("b@primevest.test");
        semAceite.put("acceptTerms", false);
        ResponseEntity<Map> r = publico(HttpMethod.POST, "/api/v1/auth/signup/start", semAceite);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map<String, String>) r.getBody().get("errors")).get("acceptTerms")).contains("Termos de Uso");

        Map<String, Object> senhaCurta = dadosCadastro("b@primevest.test");
        senhaCurta.put("password", "123");
        assertThat(((Map<String, String>) publico(HttpMethod.POST, "/api/v1/auth/signup/start", senhaCurta)
            .getBody().get("errors")).get("password")).isEqualTo("A senha precisa ter de 8 a 72 caracteres.");

        Map<String, Object> cpfRuim = dadosCadastro("b@primevest.test");
        cpfRuim.put("cpf", "111.111.111-11");
        ResponseEntity<Map> rc = publico(HttpMethod.POST, "/api/v1/auth/signup/start", cpfRuim);
        assertThat(rc.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rc.getBody()).containsEntry("message", "Informe um CPF válido.");

        Map<String, Object> primeiro = dadosCadastro("c@primevest.test");
        primeiro.put("cpf", "52998224725");
        primeiro.put("phone", "41999990000");
        cadastrar(primeiro);

        Map<String, Object> mesmoCpf = dadosCadastro("d@primevest.test");
        mesmoCpf.put("cpf", "529.982.247-25");
        assertThat(publico(HttpMethod.POST, "/api/v1/auth/signup/start", mesmoCpf).getBody())
            .containsEntry("message", "Este CPF já está cadastrado em outra conta.");

        Map<String, Object> mesmoCelular = dadosCadastro("d@primevest.test");
        mesmoCelular.put("phone", "+55 41 99999-0000");
        assertThat(publico(HttpMethod.POST, "/api/v1/auth/signup/start", mesmoCelular).getBody())
            .containsEntry("message", "Este celular já está cadastrado em outra conta.");
    }

    // ---------------------------------------------------------------- tokens e sessao

    @Test
    @DisplayName("C12/C13: refresh nao serve de acesso, acesso nao serve de refresh; a renovacao funciona")
    void tiposDeToken() {
        cadastrar(dadosCadastro("t@primevest.test"));
        Map<String, Object> tokens = login("t@primevest.test", SENHA).getBody();
        String acesso = (String) tokens.get("accessToken");
        String refresh = (String) tokens.get("refreshToken");

        assertThat(comToken(refresh, HttpMethod.GET, "/api/v1/users/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(publico(HttpMethod.POST, "/api/v1/auth/refresh", Map.of("refreshToken", acesso)).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> renovado = publico(HttpMethod.POST, "/api/v1/auth/refresh", Map.of("refreshToken", refresh));
        assertThat(renovado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(comToken((String) renovado.getBody().get("accessToken"), HttpMethod.GET, "/api/v1/users/me", null)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Login: senha errada 401; depois de 10 falhas o e-mail fica bloqueado (429)")
    void limiteDeLogin() {
        cadastrar(dadosCadastro("l@primevest.test"));
        for (int i = 0; i < 10; i++) {
            assertThat(login("l@primevest.test", "errada-123").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(login("l@primevest.test", SENHA).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("C14: esqueci a senha — codigo no e-mail, nova senha vale e as sessoes antigas caem")
    void esqueciASenha() throws Exception {
        String antigo = cadastrar(dadosCadastro("e@primevest.test"));
        Thread.sleep(1100); // o iat do JWT e em segundos: a troca precisa cair no segundo seguinte

        ResponseEntity<Map> pedido = publico(HttpMethod.POST, "/api/v1/auth/password/forgot", Map.of("email", "E@primevest.test"));
        assertThat(pedido.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String code = ultimoCodigo(VerificationPurpose.RESET_PASSWORD, "e@primevest.test");
        ResponseEntity<Map> reset = publico(HttpMethod.POST, "/api/v1/auth/password/reset",
            Map.of("requestId", pedido.getBody().get("requestId"), "code", code, "newPassword", "outra-senha-456"));
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(comToken(antigo, HttpMethod.GET, "/api/v1/users/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("e@primevest.test", SENHA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("e@primevest.test", "outra-senha-456").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(accountMailer).sendNotice(eq(AccountNotice.PASSWORD_CHANGED), eq("e@primevest.test"), any());

        // e-mail sem conta: mesma resposta, nenhum e-mail
        ResponseEntity<Map> fantasma = publico(HttpMethod.POST, "/api/v1/auth/password/forgot", Map.of("email", "ninguem@primevest.test"));
        assertThat(fantasma.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(accountMailer, never()).sendCode(any(), eq("ninguem@primevest.test"), any(), any(), anyLong());
    }

    // ---------------------------------------------------------------- configuracoes

    @Test
    @DisplayName("C21: trocar a senha devolve token novo e derruba o antigo")
    void trocarSenha() throws Exception {
        String antigo = cadastrar(dadosCadastro("s@primevest.test"));
        Thread.sleep(1100);
        ResponseEntity<Map> errada = comToken(antigo, HttpMethod.PUT, "/api/v1/users/me/password",
            Map.of("currentPassword", "nao-e-essa", "newPassword", "nova-senha-789"));
        assertThat(errada.getBody()).containsEntry("message", "Senha atual incorreta.");

        ResponseEntity<Map> ok = comToken(antigo, HttpMethod.PUT, "/api/v1/users/me/password",
            Map.of("currentPassword", SENHA, "newPassword", "nova-senha-789"));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        String novo = (String) ok.getBody().get("accessToken");

        assertThat(comToken(antigo, HttpMethod.GET, "/api/v1/users/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(comToken(novo, HttpMethod.GET, "/api/v1/users/me", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("C15: trocar o e-mail so com o codigo enviado ao novo; o antigo e avisado")
    void trocarEmail() {
        String token = cadastrar(dadosCadastro("velho@primevest.test"));
        ResponseEntity<Map> pedido = comToken(token, HttpMethod.POST, "/api/v1/users/me/email", Map.of("newEmail", "Novo@PrimeVest.test"));
        assertThat(pedido.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(userRepository.findByEmail("velho@primevest.test")).isPresent();

        String code = ultimoCodigo(VerificationPurpose.CHANGE_EMAIL, "novo@primevest.test");
        ResponseEntity<Map> ok = comToken(token, HttpMethod.POST, "/api/v1/users/me/email/confirm",
            Map.of("requestId", pedido.getBody().get("requestId"), "code", code));
        assertThat(ok.getBody()).containsEntry("email", "novo@primevest.test");
        verify(accountMailer).sendNotice(eq(AccountNotice.EMAIL_CHANGED), eq("velho@primevest.test"), any());
        assertThat(login("novo@primevest.test", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Perfil: nome, celular, CPF (nulo nao mexe, vazio tira) e tema")
    void perfil() {
        String token = cadastrar(dadosCadastro("p@primevest.test"));
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("name", "Paula Nova");
        corpo.put("phone", "41988887777");
        corpo.put("cpf", "529.982.247-25");
        Map<String, Object> me = comToken(token, HttpMethod.PUT, "/api/v1/users/me", corpo).getBody();
        assertThat(me).containsEntry("name", "Paula Nova").containsEntry("phone", "+5541988887777")
            .containsEntry("cpfMasked", "***.982.247-**");

        corpo.put("cpf", null);
        corpo.put("phone", "");
        me = comToken(token, HttpMethod.PUT, "/api/v1/users/me", corpo).getBody();
        assertThat(me).containsEntry("cpfMasked", "***.982.247-**").doesNotContainKey("phone");

        corpo.put("cpf", "");
        me = comToken(token, HttpMethod.PUT, "/api/v1/users/me", corpo).getBody();
        assertThat(me).doesNotContainKey("cpfMasked");

        me = comToken(token, HttpMethod.PUT, "/api/v1/users/me/preferences", Map.of("theme", "LIGHT")).getBody();
        assertThat(me).containsEntry("theme", "LIGHT");
    }

    @Test
    @DisplayName("C20: desvincular o Google sem senha e recusado")
    void desvincularGoogleSemSenha() {
        User u = usuarioComCarteira("g@primevest.test");
        jdbc.update("UPDATE users SET google_id = 'sub-123', password_hash = NULL WHERE id = ?", u.getId());
        ResponseEntity<Map> r = rest.exchange("/api/v1/users/me/google", HttpMethod.DELETE, new HttpEntity<>(headers(u)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) r.getBody().get("message")).startsWith("Defina uma senha");

        // C19: quem so tem Google define a primeira senha sem informar a atual
        ResponseEntity<String> definida = put(u, "/api/v1/users/me/password", Map.of("newPassword", "primeira-senha-1"), String.class);
        assertThat(definida.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login("g@primevest.test", "primeira-senha-1").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("C26: termos desatualizados — 403 TERMS_REQUIRED ate aceitar; /users/me continua liberado")
    void novoAceiteDosTermos() {
        User u = usuarioComCarteira("termos@primevest.test");
        jdbc.update("UPDATE users SET terms_version = 'versao-antiga' WHERE id = ?", u.getId());

        ResponseEntity<Map> bloqueado = get(u, "/api/v1/portfolio/", Map.class);
        assertThat(bloqueado.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((Map<String, String>) bloqueado.getBody().get("errors")).containsEntry("code", "TERMS_REQUIRED");
        assertThat(get(u, "/api/v1/users/me", Map.class).getBody()).containsEntry("termsPending", true);

        assertThat(post(u, "/api/v1/users/me/terms", null, Map.class).getBody()).containsEntry("termsPending", false);
        assertThat(get(u, "/api/v1/portfolio/", Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("C23: excluir a conta com o codigo apaga o usuario e os dados; o token deixa de valer")
    void excluirConta() {
        String token = cadastrar(dadosCadastro("x@primevest.test"));
        User u = userRepository.findByEmail("x@primevest.test").orElseThrow();
        lancarComSucesso(u, compra("PETR4", "2024-01-10", "10", "30.00"));

        ResponseEntity<Map> pedido = comToken(token, HttpMethod.POST, "/api/v1/users/me/delete-request", null);
        String code = ultimoCodigo(VerificationPurpose.DELETE_ACCOUNT, "x@primevest.test");
        ResponseEntity<Map> r = comToken(token, HttpMethod.DELETE, "/api/v1/users/me",
            Map.of("requestId", pedido.getBody().get("requestId"), "code", code));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(userRepository.findByEmail("x@primevest.test")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isZero();
        assertThat(comToken(token, HttpMethod.GET, "/api/v1/users/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(accountMailer).sendNotice(eq(AccountNotice.ACCOUNT_DELETED), eq("x@primevest.test"), any());
    }

    // ---------------------------------------------------------------- foto de perfil

    private ResponseEntity<Map> enviarFoto(User u, byte[] bytes, String nome) {
        org.springframework.util.LinkedMultiValueMap<String, Object> corpo = new org.springframework.util.LinkedMultiValueMap<>();
        corpo.add("file", new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override public String getFilename() { return nome; }
        });
        HttpHeaders h = headers(u);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/v1/users/me/avatar", HttpMethod.PUT, new HttpEntity<>(corpo, h), Map.class);
    }

    @Test
    @DisplayName("Foto de perfil: envio vira JPEG quadrado, sai com a versao no perfil, recusa invalida e remove")
    void fotoDePerfil() throws Exception {
        User u = usuarioComCarteira("foto@primevest.test");

        ResponseEntity<Map> ok = enviarFoto(u, com.primevest.domain.user.UserAvatarServiceTest.imagem("png", 900, 600), "eu.png");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("avatarVersion")).isNotNull();

        ResponseEntity<byte[]> img = rest.exchange("/api/v1/users/me/avatar", HttpMethod.GET, new HttpEntity<>(headers(u)), byte[].class);
        assertThat(img.getHeaders().getContentType()).hasToString("image/jpeg");
        java.awt.image.BufferedImage lida = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(img.getBody()));
        assertThat(lida.getWidth()).isEqualTo(512);
        assertThat(lida.getHeight()).isEqualTo(512);

        ResponseEntity<Map> ruim = enviarFoto(u, "nao sou imagem".getBytes(), "x.jpg");
        assertThat(ruim.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) ruim.getBody().get("message")).startsWith("Formato não aceito");

        ResponseEntity<Map> sem = rest.exchange("/api/v1/users/me/avatar", HttpMethod.DELETE, new HttpEntity<>(headers(u)), Map.class);
        assertThat(sem.getBody()).doesNotContainKey("avatarVersion");
        assertThat(rest.exchange("/api/v1/users/me/avatar", HttpMethod.GET, new HttpEntity<>(headers(u)), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
