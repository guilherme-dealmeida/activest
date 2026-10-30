package com.activest.adm.domain.asset;

import com.activest.adm.domain.notice.AdminNotices;
import com.activest.adm.integration.cvm.CvmOpenDataClient;
import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.notification.NotificationService;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Inativação automática do nacional (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, I5 e C7 — F2): só com prova (CVM
 * cancelada, ou fora da lista oficial + 30 pregões sem negócio, sem renomeação); suspensão e decisão do administrador não
 * são mexidas. Os papéis do teste (ZZ...) têm ticker próprio e o pregão de 45 dias úteis até hoje é gravado aqui.
 */
class CatalogDelistingIT extends IntegrationTestBase {

    private static final List<String> TICKERS = List.of("ZZLIV3", "ZZOLD3", "ZZSUS3", "ZZREN3", "ZZMAN3", "ZZCVM3",
            "ZZMIG3");

    @Autowired AssetRepository assets;
    @Autowired AdminNotices notices;
    @TempDir Path dir;

    private final List<LocalDate> sessions = new ArrayList<>();
    private CatalogDelisting delisting;

    @BeforeEach
    void scenario() throws Exception {
        cleanup();
        LocalDate d = NotificationService.today();
        while (sessions.size() < 45) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                sessions.add(0, d);
            }
            d = d.minusDays(1);
        }
        Long cvm = issuer("11222333000181");
        Long mig = issuer("44555666000172");
        for (String t : TICKERS) {
            jdbc.update("""
                INSERT INTO assets (ticker, name, asset_type_id, currency_code, active, issuer_id)
                VALUES (?, ?, (SELECT id FROM asset_types WHERE name = 'ACAO'), 'BRL', true, ?)""",
                t, "Teste " + t, t.equals("ZZCVM3") ? cvm : t.equals("ZZMIG3") ? mig : null);
        }
        jdbc.update("UPDATE assets SET delisting_source = 'MANUAL' WHERE ticker = 'ZZMAN3'");
        // ZZLIV3 negocia todo dia (e da os 45 pregoes ao COTAHIST); os outros, so nos 5 primeiros
        sessions.forEach(s -> trade("ZZLIV3", s));
        for (String t : List.of("ZZOLD3", "ZZSUS3", "ZZREN3", "ZZMAN3", "ZZCVM3", "ZZMIG3")) {
            sessions.subList(0, 5).forEach(s -> trade(t, s));
        }
        Path csv = dir.resolve("cad_cia_aberta.csv");
        Files.writeString(csv, """
            CNPJ_CIA;DENOM_SOCIAL;SIT;DT_CANCEL;MOTIVO_CANCEL;CD_CVM;TP_MERC
            11.222.333/0001-81;ZZ CANCELADA SA;CANCELADA;2025-01-10;Cancelamento Voluntário;99991;BOLSA
            44.555.666/0001-72;ZZ MIGRADA SA;CANCELADA;2025-02-10;Migração de Registro;99992;BOLSA
            """, java.nio.charset.Charset.forName("ISO-8859-1"));
        CvmOpenDataClient client = mock(CvmOpenDataClient.class);
        when(client.companyRegistry()).thenReturn(csv);
        delisting = new CatalogDelisting(jdbc, client, notices);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM notifications WHERE kind = 'CATALOGO_TICKER_INATIVADO'");
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker LIKE 'ZZ%3')");
        jdbc.update("DELETE FROM assets WHERE ticker IN (?, ?, ?, ?, ?, ?, ?)", TICKERS.toArray());
        jdbc.update("DELETE FROM issuers WHERE cnpj IN ('11222333000181', '44555666000172')");
    }

    private Long issuer(String cnpj) {
        return jdbc.queryForObject("""
            INSERT INTO issuers (kind, cnpj, legal_name, registration_status) VALUES ('COMPANY', ?, ?, 'ATIVO')
            RETURNING id""", Long.class, cnpj, "ZZ " + cnpj);
    }

    private void trade(String ticker, LocalDate day) {
        jdbc.update("""
            INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
            VALUES ((SELECT id FROM assets WHERE ticker = ?), ?, 10, 'B3_COTAHIST')""", ticker, java.sql.Date.valueOf(day));
    }

    private Map<String, Object> row(String ticker) {
        return jdbc.queryForMap("SELECT active, delisted_on, delisting_source FROM assets WHERE ticker = ?", ticker);
    }

    private List<Asset> catalog() {
        return assets.findActiveWithType().stream().filter(a -> TICKERS.contains(a.getTicker())).toList();
    }

    @Test
    @DisplayName("Fora da lista + 30 pregões sem negócio inativa com a data do último pregão; suspenso, renomeado e MANUAL não")
    void listGone() {
        // ZZSUS3 suspenso: continua na lista oficial; ZZREN3: renomeacao da BRAPI
        CatalogDelisting.Result r = delisting.run(catalog(), Map.of("ZZLIV3", "ACAO", "ZZSUS3", "ACAO",
                "ZZCVM3", "ACAO", "ZZMIG3", "ACAO"), Set.of("ZZREN3"), Set.of(), Set.of());

        LocalDate last = sessions.get(4);
        assertThat(row("ZZOLD3")).containsEntry("active", false).containsEntry("delisted_on", java.sql.Date.valueOf(last))
                .containsEntry("delisting_source", "LIST_GONE");
        for (String kept : List.of("ZZLIV3", "ZZSUS3", "ZZREN3", "ZZMAN3", "ZZMIG3")) {
            assertThat(row(kept)).as(kept).containsEntry("active", true);
        }
        assertThat(r.byListGone()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'CATALOGO_TICKER_INATIVADO'",
                Long.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("Registro cancelado na CVM inativa a companhia; migração de registro não é saída de bolsa")
    void cvm() {
        CatalogDelisting.Result r = delisting.run(catalog(), Map.of(), null, Set.of(), Set.of());

        assertThat(row("ZZCVM3")).containsEntry("active", false).containsEntry("delisting_source", "CVM")
                .containsEntry("delisted_on", java.sql.Date.valueOf(sessions.get(4)));
        assertThat(row("ZZMIG3")).containsEntry("active", true);
        // sem a lista oficial, a regra do sumico nao roda
        assertThat(row("ZZOLD3")).containsEntry("active", true);
        assertThat(r.byCvm()).isEqualTo(1);
        assertThat(r.byListGone()).isZero();
    }

    @Test
    @DisplayName("Inativo sem data de saída ganha o último pregão (I5, 1ª parte); o MANUAL fica como está")
    void fillsDates() {
        jdbc.update("UPDATE assets SET active = false WHERE ticker IN ('ZZOLD3', 'ZZMAN3')");

        delisting.run(List.of(), Map.of(), null, Set.of(), Set.of());

        assertThat(row("ZZOLD3")).containsEntry("delisted_on", java.sql.Date.valueOf(sessions.get(4)))
                .containsEntry("delisting_source", null);
        assertThat(row("ZZMAN3")).containsEntry("delisted_on", null);
    }
}
