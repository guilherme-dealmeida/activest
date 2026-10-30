package com.activest.adm.domain.asset;

import com.activest.domain.user.User;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Curadoria da inativação (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C7 e 11.5): o administrador desfaz (ou faz) a
 * inativação; grava MANUAL, que a conferência não refaz; reativar não pode repetir o ticker de um papel listado.
 */
class AssetActivationIT extends IntegrationTestBase {

    private User admin;
    private User common;

    @BeforeEach
    void scenario() {
        cleanup();
        admin = userWithPortfolio("admin-ativo-" + System.nanoTime() + "@activest.test");
        jdbc.update("UPDATE users SET is_admin = TRUE WHERE id = ?", admin.getId());
        common = userWithPortfolio("comum-ativo-" + System.nanoTime() + "@activest.test");
        jdbc.update("""
            INSERT INTO assets (ticker, name, asset_type_id, currency_code, active, delisted_on, delisting_source)
            VALUES ('ZZACT3', 'Teste', (SELECT id FROM asset_types WHERE name = 'ACAO'), 'BRL', false, '2024-05-10',
                    'LIST_GONE')""");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM assets WHERE ticker = 'ZZACT3'");
    }

    private long id(boolean active) {
        return jdbc.queryForObject("SELECT id FROM assets WHERE ticker = 'ZZACT3' AND active = ? ORDER BY id LIMIT 1",
            Long.class, active);
    }

    private String url(long id) {
        return "/api/v1/admin/assets/" + id + "/activation";
    }

    @Test
    @DisplayName("Desfazer a inativação: só administrador; volta listado, sem data, com origem MANUAL")
    void undo() {
        long id = id(false);
        Map<String, Object> body = new HashMap<>();
        body.put("active", true);

        assertThat(put(common, url(id), body, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(put(admin, url(id), body, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForMap("SELECT active, delisted_on, delisting_source FROM assets WHERE id = ?", id))
            .containsEntry("active", true).containsEntry("delisted_on", null).containsEntry("delisting_source", "MANUAL");
    }

    @Test
    @DisplayName("Reativar o deslistado quando já há um listado com o mesmo ticker no tipo responde 409")
    void conflict() {
        long old = id(false);
        jdbc.update("""
            INSERT INTO assets (ticker, name, asset_type_id, currency_code, active)
            VALUES ('ZZACT3', 'Novo', (SELECT id FROM asset_types WHERE name = 'ACAO'), 'BRL', true)""");
        Map<String, Object> body = new HashMap<>();
        body.put("active", true);

        assertThat(put(admin, url(old), body, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(put(admin, "/api/v1/admin/assets/999999999/activation", body, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> wrong = new HashMap<>();
        wrong.put("active", true);
        wrong.put("delistedOn", "2024-01-01");
        assertThat(put(admin, url(old), wrong, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
