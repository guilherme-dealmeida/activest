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
 * O e-mail e o mock {@code accountMailer} do {@link FunctionalBase}: o codigo e lido dele.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class SignupIT extends FunctionalBase {

    private static final String PASSWORD = "senha-forte-123";

    // ---------------------------------------------------------------- apoio

    private ResponseEntity<Map> publicResult(HttpMethod method, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, h), Map.class);
    }

    private ResponseEntity<Map> comToken(String token, HttpMethod method, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, h), Map.class);
    }

    private Map<String, Object> registryData(String email) {
        Map<String, Object> c = new HashMap<>();
        c.put("name", "Maria Teste");
        c.put("email", email);
        c.put("password", PASSWORD);
        c.put("acceptTerms", true);
        return c;
    }

    private String lastCode(VerificationPurpose purpose, String email) {
        ArgumentCaptor<String> codeValue = ArgumentCaptor.forClass(String.class);
        verify(accountMailer, atLeastOnce()).sendCode(eq(purpose), eq(email), any(), codeValue.capture(), anyLong());
        return codeValue.getValue();
    }

    /** Cadastro completo pela API; devolve o token de acesso. */
    private String register(Map<String, Object> data) {
        ResponseEntity<Map> start = publicResult(HttpMethod.POST, "/api/v1/auth/signup/start", data);
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String id = (String) start.getBody().get("requestId");
        String code = lastCode(VerificationPurpose.SIGNUP, ((String) data.get("email")).toLowerCase());
        ResponseEntity<Map> end = publicResult(HttpMethod.POST, "/api/v1/auth/signup/confirm",
            Map.of("requestId", id, "code", code));
        assertThat(end.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) end.getBody().get("accessToken");
    }

    private ResponseEntity<Map> login(String email, String password) {
        return publicResult(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    // ---------------------------------------------------------------- cadastro

    @Test
    @DisplayName("C4: cadastro com codigo cria a conta com carteira, e-mail verificado e termos aceitos")
    void completeSignup() {
        Map<String, Object> st = publicResult(HttpMethod.GET, "/api/v1/auth/signup/enabled", null).getBody();
        assertThat(st).containsEntry("enabled", true).containsEntry("termsVersion", "2026-10-provisorio");

        Map<String, Object> data = registryData("Maria@PrimeVest.test");
        data.put("phone", "(41) 99876-5432");
        data.put("cpf", "529.982.247-25");
        String token = register(data);

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
        // CPF nunca em claro no banco. A busca e por bytes (position em bytea): converter o
        // ciphertext para texto falhava sempre que o AES gerava um byte 0x00 — teste instavel.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users "
            + "WHERE position(convert_to('52998224725', 'UTF8') IN cpf_ciphertext) > 0",
            Integer.class)).isZero();

        // C11: maiusculas no login entram na mesma conta
        assertThat(login("MARIA@primevest.TEST", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("C8: e-mail ja cadastrado responde igual, avisa por e-mail e nenhum codigo confirma")
    void existingEmail() {
        userWithPortfolio("ja@primevest.test");
        ResponseEntity<Map> r = publicResult(HttpMethod.POST, "/api/v1/auth/signup/start", registryData("ja@primevest.test"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(r.getBody()).containsKeys("requestId", "maskedEmail", "resendInSeconds", "expiresInSeconds");
        verify(accountMailer).sendNotice(eq(AccountNotice.ACCOUNT_EXISTS), eq("ja@primevest.test"), any());
        verify(accountMailer, never()).sendCode(any(), eq("ja@primevest.test"), any(), any(), anyLong());

        ResponseEntity<Map> c = publicResult(HttpMethod.POST, "/api/v1/auth/signup/confirm",
            Map.of("requestId", r.getBody().get("requestId"), "code", "123456"));
        assertThat(c.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("C5/C7: codigo errado conta tentativas; reenvio antes de 60 s e recusado")
    void attemptsAndResend() {
        ResponseEntity<Map> r = publicResult(HttpMethod.POST, "/api/v1/auth/signup/start", registryData("ana@primevest.test"));
        String id = (String) r.getBody().get("requestId");

        ResponseEntity<Map> resend = publicResult(HttpMethod.POST, "/api/v1/auth/signup/resend", Map.of("requestId", id));
        assertThat(resend.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        String valid = lastCode(VerificationPurpose.SIGNUP, "ana@primevest.test");
        String wrong = valid.equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 4; i++) {
            ResponseEntity<Map> e = publicResult(HttpMethod.POST, "/api/v1/auth/signup/confirm", Map.of("requestId", id, "code", wrong));
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat((String) e.getBody().get("message")).startsWith("Código incorreto.");
        }
        publicResult(HttpMethod.POST, "/api/v1/auth/signup/confirm", Map.of("requestId", id, "code", wrong));
        // a quinta errada apaga o pedido: nem o certo vale mais
        ResponseEntity<Map> after = publicResult(HttpMethod.POST, "/api/v1/auth/signup/confirm", Map.of("requestId", id, "code", valid));
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) after.getBody().get("message")).contains("expirou");
        assertThat(userRepository.findByEmail("ana@primevest.test")).isEmpty();
    }

    @Test
    @DisplayName("C9/C16/C17/C25: sem aceite 400; CPF invalido, CPF e celular repetidos 422")
    void validations() {
        Map<String, Object> withoutAcceptance = registryData("b@primevest.test");
        withoutAcceptance.put("acceptTerms", false);
        ResponseEntity<Map> r = publicResult(HttpMethod.POST, "/api/v1/auth/signup/start", withoutAcceptance);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map<String, String>) r.getBody().get("errors")).get("acceptTerms")).contains("Termos de Uso");

        Map<String, Object> shortPassword = registryData("b@primevest.test");
        shortPassword.put("password", "123");
        assertThat(((Map<String, String>) publicResult(HttpMethod.POST, "/api/v1/auth/signup/start", shortPassword)
            .getBody().get("errors")).get("password")).isEqualTo("A senha precisa ter de 8 a 72 caracteres.");

        Map<String, Object> badCpf = registryData("b@primevest.test");
        badCpf.put("cpf", "111.111.111-11");
        ResponseEntity<Map> rc = publicResult(HttpMethod.POST, "/api/v1/auth/signup/start", badCpf);
        assertThat(rc.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rc.getBody()).containsEntry("message", "Informe um CPF válido.");

        Map<String, Object> first = registryData("c@primevest.test");
        first.put("cpf", "52998224725");
        first.put("phone", "41999990000");
        register(first);

        Map<String, Object> sameCpf = registryData("d@primevest.test");
        sameCpf.put("cpf", "529.982.247-25");
        assertThat(publicResult(HttpMethod.POST, "/api/v1/auth/signup/start", sameCpf).getBody())
            .containsEntry("message", "Este CPF já está cadastrado em outra conta.");

        Map<String, Object> samePhone = registryData("d@primevest.test");
        samePhone.put("phone", "+55 41 99999-0000");
        assertThat(publicResult(HttpMethod.POST, "/api/v1/auth/signup/start", samePhone).getBody())
            .containsEntry("message", "Este celular já está cadastrado em outra conta.");
    }

    // ---------------------------------------------------------------- tokens e sessao

    @Test
    @DisplayName("C12/C13: refresh nao serve de acesso, acesso nao serve de refresh; a renovacao funciona")
    void tokenTypes() {
        register(registryData("t@primevest.test"));
        Map<String, Object> tokens = login("t@primevest.test", PASSWORD).getBody();
        String access = (String) tokens.get("accessToken");
        String refresh = (String) tokens.get("refreshToken");

        assertThat(comToken(refresh, HttpMethod.GET, "/api/v1/users/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(publicResult(HttpMethod.POST, "/api/v1/auth/refresh", Map.of("refreshToken", access)).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> renewed = publicResult(HttpMethod.POST, "/api/v1/auth/refresh", Map.of("refreshToken", refresh));
        assertThat(renewed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(comToken((String) renewed.getBody().get("accessToken"), HttpMethod.GET, "/api/v1/users/me", null)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Login: senha errada 401; depois de 10 falhas o e-mail fica bloqueado (429)")
    void loginLimit() {
        register(registryData("l@primevest.test"));
        for (int i = 0; i < 10; i++) {
            assertThat(login("l@primevest.test", "errada-123").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(login("l@primevest.test", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("C14: esqueci a senha — codigo no e-mail, nova senha vale e as sessoes antigas caem")
    void forgotPassword() throws Exception {
        String previous = register(registryData("e@primevest.test"));
        Thread.sleep(1100); // o iat do JWT e em segundos: a troca precisa cair no segundo seguinte

        ResponseEntity<Map> request = publicResult(HttpMethod.POST, "/api/v1/auth/password/forgot", Map.of("email", "E@primevest.test"));
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String code = lastCode(VerificationPurpose.RESET_PASSWORD, "e@primevest.test");
        ResponseEntity<Map> reset = publicResult(HttpMethod.POST, "/api/v1/auth/password/reset",
            Map.of("requestId", request.getBody().get("requestId"), "code", code, "newPassword", "outra-senha-456"));
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(comToken(previous, HttpMethod.GET, "/api/v1/users/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("e@primevest.test", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("e@primevest.test", "outra-senha-456").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(accountMailer).sendNotice(eq(AccountNotice.PASSWORD_CHANGED), eq("e@primevest.test"), any());

        // e-mail sem conta: mesma resposta, nenhum e-mail
        ResponseEntity<Map> ghost = publicResult(HttpMethod.POST, "/api/v1/auth/password/forgot", Map.of("email", "ninguem@primevest.test"));
        assertThat(ghost.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(accountMailer, never()).sendCode(any(), eq("ninguem@primevest.test"), any(), any(), anyLong());
    }

    // ---------------------------------------------------------------- configuracoes

    @Test
    @DisplayName("C21: trocar a senha devolve token novo e derruba o antigo")
    void changePassword() throws Exception {
        String previous = register(registryData("s@primevest.test"));
        Thread.sleep(1100);
        ResponseEntity<Map> wrong = comToken(previous, HttpMethod.PUT, "/api/v1/users/me/password",
            Map.of("currentPassword", "nao-e-essa", "newPassword", "nova-senha-789"));
        assertThat(wrong.getBody()).containsEntry("message", "Senha atual incorreta.");

        ResponseEntity<Map> ok = comToken(previous, HttpMethod.PUT, "/api/v1/users/me/password",
            Map.of("currentPassword", PASSWORD, "newPassword", "nova-senha-789"));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        String created = (String) ok.getBody().get("accessToken");

        assertThat(comToken(previous, HttpMethod.GET, "/api/v1/users/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(comToken(created, HttpMethod.GET, "/api/v1/users/me", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("C15: trocar o e-mail so com o codigo enviado ao novo; o antigo e avisado")
    void changeEmail() {
        String token = register(registryData("velho@primevest.test"));
        ResponseEntity<Map> request = comToken(token, HttpMethod.POST, "/api/v1/users/me/email", Map.of("newEmail", "Novo@PrimeVest.test"));
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(userRepository.findByEmail("velho@primevest.test")).isPresent();

        String code = lastCode(VerificationPurpose.CHANGE_EMAIL, "novo@primevest.test");
        ResponseEntity<Map> ok = comToken(token, HttpMethod.POST, "/api/v1/users/me/email/confirm",
            Map.of("requestId", request.getBody().get("requestId"), "code", code));
        assertThat(ok.getBody()).containsEntry("email", "novo@primevest.test");
        verify(accountMailer).sendNotice(eq(AccountNotice.EMAIL_CHANGED), eq("velho@primevest.test"), any());
        assertThat(login("novo@primevest.test", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Perfil: nome, celular, CPF (nulo nao mexe, vazio tira) e tema")
    void profile() {
        String token = register(registryData("p@primevest.test"));
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Paula Nova");
        body.put("phone", "41988887777");
        body.put("cpf", "529.982.247-25");
        Map<String, Object> me = comToken(token, HttpMethod.PUT, "/api/v1/users/me", body).getBody();
        assertThat(me).containsEntry("name", "Paula Nova").containsEntry("phone", "+5541988887777")
            .containsEntry("cpfMasked", "***.982.247-**");

        body.put("cpf", null);
        body.put("phone", "");
        me = comToken(token, HttpMethod.PUT, "/api/v1/users/me", body).getBody();
        assertThat(me).containsEntry("cpfMasked", "***.982.247-**").doesNotContainKey("phone");

        body.put("cpf", "");
        me = comToken(token, HttpMethod.PUT, "/api/v1/users/me", body).getBody();
        assertThat(me).doesNotContainKey("cpfMasked");

        me = comToken(token, HttpMethod.PUT, "/api/v1/users/me/preferences", Map.of("theme", "LIGHT")).getBody();
        assertThat(me).containsEntry("theme", "LIGHT");
    }

    @Test
    @DisplayName("C20: desvincular o Google sem senha e recusado")
    void unlinkGoogleWithoutPassword() {
        User u = userWithPortfolio("g@primevest.test");
        jdbc.update("UPDATE users SET google_id = 'sub-123', password_hash = NULL WHERE id = ?", u.getId());
        ResponseEntity<Map> r = rest.exchange("/api/v1/users/me/google", HttpMethod.DELETE, new HttpEntity<>(headers(u)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) r.getBody().get("message")).startsWith("Defina uma senha");

        // C19: quem so tem Google define a primeira senha sem informar a atual
        ResponseEntity<String> defined = put(u, "/api/v1/users/me/password", Map.of("newPassword", "primeira-senha-1"), String.class);
        assertThat(defined.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login("g@primevest.test", "primeira-senha-1").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("C26: termos desatualizados — 403 TERMS_REQUIRED ate aceitar; /users/me continua liberado")
    void newTermsAcceptance() {
        User u = userWithPortfolio("termos@primevest.test");
        jdbc.update("UPDATE users SET terms_version = 'versao-antiga' WHERE id = ?", u.getId());

        ResponseEntity<Map> blocked = get(u, "/api/v1/portfolio/", Map.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((Map<String, String>) blocked.getBody().get("errors")).containsEntry("code", "TERMS_REQUIRED");
        assertThat(get(u, "/api/v1/users/me", Map.class).getBody()).containsEntry("termsPending", true);

        assertThat(post(u, "/api/v1/users/me/terms", null, Map.class).getBody()).containsEntry("termsPending", false);
        assertThat(get(u, "/api/v1/portfolio/", Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("C23: excluir a conta com o codigo apaga o usuario e os dados; o token deixa de valer")
    void deleteAccount() {
        String token = register(registryData("x@primevest.test"));
        User u = userRepository.findByEmail("x@primevest.test").orElseThrow();
        recordSuccessfully(u, buy("PETR4", "2024-01-10", "10", "30.00"));

        ResponseEntity<Map> request = comToken(token, HttpMethod.POST, "/api/v1/users/me/delete-request", null);
        String code = lastCode(VerificationPurpose.DELETE_ACCOUNT, "x@primevest.test");
        ResponseEntity<Map> r = comToken(token, HttpMethod.DELETE, "/api/v1/users/me",
            Map.of("requestId", request.getBody().get("requestId"), "code", code));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(userRepository.findByEmail("x@primevest.test")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isZero();
        assertThat(comToken(token, HttpMethod.GET, "/api/v1/users/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(accountMailer).sendNotice(eq(AccountNotice.ACCOUNT_DELETED), eq("x@primevest.test"), any());
    }

    // ---------------------------------------------------------------- foto de perfil

    private ResponseEntity<Map> sendPhoto(User u, byte[] bytes, String name) {
        org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override public String getFilename() { return name; }
        });
        HttpHeaders h = headers(u);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/v1/users/me/avatar", HttpMethod.PUT, new HttpEntity<>(body, h), Map.class);
    }

    @Test
    @DisplayName("Foto de perfil: envio vira JPEG quadrado, sai com a versao no perfil, recusa invalida e remove")
    void profilePhoto() throws Exception {
        User u = userWithPortfolio("foto@primevest.test");

        ResponseEntity<Map> ok = sendPhoto(u, com.primevest.domain.user.UserAvatarServiceTest.imageBytes("png", 900, 600), "eu.png");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("avatarVersion")).isNotNull();

        ResponseEntity<byte[]> img = rest.exchange("/api/v1/users/me/avatar", HttpMethod.GET, new HttpEntity<>(headers(u)), byte[].class);
        assertThat(img.getHeaders().getContentType()).hasToString("image/jpeg");
        java.awt.image.BufferedImage readBack = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(img.getBody()));
        assertThat(readBack.getWidth()).isEqualTo(512);
        assertThat(readBack.getHeight()).isEqualTo(512);

        ResponseEntity<Map> bad = sendPhoto(u, "nao sou imagem".getBytes(), "x.jpg");
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) bad.getBody().get("message")).startsWith("Formato não aceito");

        ResponseEntity<Map> none = rest.exchange("/api/v1/users/me/avatar", HttpMethod.DELETE, new HttpEntity<>(headers(u)), Map.class);
        assertThat(none.getBody()).doesNotContainKey("avatarVersion");
        assertThat(rest.exchange("/api/v1/users/me/avatar", HttpMethod.GET, new HttpEntity<>(headers(u)), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
